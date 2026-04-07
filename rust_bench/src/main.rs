use std::time::Instant;
use std::ffi::c_void;
use std::os::raw::{c_char, c_int};
use std::sync::Arc;
use std::thread;
use rand::{Rng, SeedableRng};
use rand::rngs::StdRng;
use std::fs;
use redb::{Database, TableDefinition, ReadableTable};

extern "C" {
    fn nkv_open(path: *const c_char, multi_process: bool) -> *mut c_void;
    fn nkv_close(handle: *mut c_void);
    fn nkv_put_int(handle: *mut c_void, key: *const c_char, key_len: usize, value: i32);
    fn nkv_get_int(handle: *mut c_void, key: *const c_char, key_len: usize, default_value: i32) -> i32;
    fn nkv_put_string(handle: *mut c_void, key: *const c_char, key_len: usize, value: *const c_char, val_len: usize);
    fn nkv_get_string(handle: *mut c_void, key: *const c_char, key_len: usize, out_len: *mut usize) -> *mut c_char;
    fn nkv_free_string(s: *mut c_char);
    fn nkv_remove(handle: *mut c_void, key: *const c_char, key_len: usize);
    fn nkv_contains(handle: *mut c_void, key: *const c_char, key_len: usize) -> bool;
}

const ITERATIONS: usize = 100000;
const TABLE: TableDefinition<&[u8], &[u8]> = TableDefinition::new("table");

struct NextKV(*mut c_void);

unsafe impl Send for NextKV {}
unsafe impl Sync for NextKV {}

impl NextKV {
    fn open(path: &str, multi_process: bool) -> Self {
        let c_path = std::ffi::CString::new(path).unwrap();
        unsafe {
            NextKV(nkv_open(c_path.as_ptr(), multi_process))
        }
    }
    
    fn close(&self) {
        unsafe { nkv_close(self.0) }
    }

    fn put_int(&self, key: &str, val: i32) {
        unsafe {
            nkv_put_int(self.0, key.as_ptr() as *const c_char, key.len(), val);
        }
    }

    fn get_int(&self, key: &str, def: i32) -> i32 {
        unsafe {
            nkv_get_int(self.0, key.as_ptr() as *const c_char, key.len(), def)
        }
    }

    fn put_string(&self, key: &str, val: &str) {
        unsafe {
            nkv_put_string(self.0, key.as_ptr() as *const c_char, key.len(), val.as_ptr() as *const c_char, val.len());
        }
    }

    fn get_string(&self, key: &str) -> Option<String> {
        let mut out_len = 0;
        unsafe {
            let ptr = nkv_get_string(self.0, key.as_ptr() as *const c_char, key.len(), &mut out_len);
            if ptr.is_null() {
                return None;
            }
            let slice = std::slice::from_raw_parts(ptr as *const u8, out_len);
            let s = String::from_utf8_lossy(slice).into_owned();
            nkv_free_string(ptr);
            Some(s)
        }
    }

    fn remove(&self, key: &str) {
        unsafe { nkv_remove(self.0, key.as_ptr() as *const c_char, key.len()) }
    }

    fn contains(&self, key: &str) -> bool {
        unsafe { nkv_contains(self.0, key.as_ptr() as *const c_char, key.len()) }
    }
}

fn main() {
    println!("==================================================");
    println!("Starting Rust 3-Engine KV Benchmark on ARM64 MacOS...");
    println!("Iterations: {}", ITERATIONS);
    println!("==================================================");

    let mut keys = Vec::with_capacity(ITERATIONS);
    let mut string_vals = Vec::with_capacity(ITERATIONS);
    let mut int_vals = Vec::with_capacity(ITERATIONS);
    let mut op_seq = Vec::with_capacity(ITERATIONS);

    let mut rng = StdRng::seed_from_u64(42);
    for i in 0..ITERATIONS {
        keys.push(format!("key_long_prefix_to_test_hashing_{}", i));
        let num: u64 = rng.gen();
        string_vals.push(format!("val_string_payload_with_some_length_{}_{}", i, num));
        int_vals.push(rng.gen());
        op_seq.push(rng.gen_range(0..4));
    }

    // Run NextKV
    let _ = fs::remove_file("nextkv_rust_sp.data");
    let nextkv = Arc::new(NextKV::open("nextkv_rust_sp.data", false));

    let mut start = Instant::now();
    for i in 0..ITERATIONS {
        nextkv.put_int(&keys[i], int_vals[i]);
    }
    println!("NextKV(FFI)  ST PUT (Int):    {} ms", start.elapsed().as_millis());

    start = Instant::now();
    for i in 0..ITERATIONS {
        nextkv.get_int(&keys[i], 0);
    }
    println!("NextKV(FFI)  ST GET (Int):    {} ms", start.elapsed().as_millis());

    start = Instant::now();
    for i in 0..ITERATIONS {
        nextkv.put_string(&keys[i], &string_vals[i]);
    }
    println!("NextKV(FFI)  ST PUT (String): {} ms", start.elapsed().as_millis());

    start = Instant::now();
    for i in 0..ITERATIONS {
        nextkv.get_string(&keys[i]);
    }
    println!("NextKV(FFI)  ST GET (String): {} ms", start.elapsed().as_millis());

    let num_threads = 4;
    start = Instant::now();
    let mut handles = vec![];
    for t in 0..num_threads {
        let nkv_clone = nextkv.clone();
        let t_keys = keys.clone();
        let t_s_vals = string_vals.clone();
        let t_op = op_seq.clone();
        
        handles.push(thread::spawn(move || {
            let start_idx = t * (ITERATIONS / num_threads);
            let end_idx = (t + 1) * (ITERATIONS / num_threads);
            for i in start_idx..end_idx {
                let op = t_op[i];
                if op == 0 {
                    nkv_clone.put_string(&t_keys[i], &t_s_vals[i]);
                } else if op == 1 {
                    nkv_clone.get_string(&t_keys[i]);
                } else if op == 2 {
                    nkv_clone.remove(&t_keys[i]);
                } else {
                    nkv_clone.contains(&t_keys[i]);
                }
            }
        }));
    }
    for h in handles {
        h.join().unwrap();
    }
    println!("NextKV(FFI)  MT MIXED (4 Th): {} ms", start.elapsed().as_millis());
    nextkv.close();
    println!("--------------------------------------------------");

    // Run Sled
    let _ = fs::remove_dir_all("sled_db");
    let sled_db = sled::open("sled_db").unwrap();

    start = Instant::now();
    for i in 0..ITERATIONS {
        sled_db.insert(keys[i].as_bytes(), &int_vals[i].to_le_bytes()).unwrap();
    }
    println!("Sled         ST PUT (Int):    {} ms", start.elapsed().as_millis());

    start = Instant::now();
    for i in 0..ITERATIONS {
        sled_db.get(keys[i].as_bytes()).unwrap();
    }
    println!("Sled         ST GET (Int):    {} ms", start.elapsed().as_millis());

    start = Instant::now();
    for i in 0..ITERATIONS {
        sled_db.insert(keys[i].as_bytes(), string_vals[i].as_bytes()).unwrap();
    }
    println!("Sled         ST PUT (String): {} ms", start.elapsed().as_millis());

    start = Instant::now();
    for i in 0..ITERATIONS {
        sled_db.get(keys[i].as_bytes()).unwrap();
    }
    println!("Sled         ST GET (String): {} ms", start.elapsed().as_millis());

    start = Instant::now();
    let mut handles = vec![];
    for t in 0..num_threads {
        let db_clone = sled_db.clone();
        let t_keys = keys.clone();
        let t_s_vals = string_vals.clone();
        let t_op = op_seq.clone();
        
        handles.push(thread::spawn(move || {
            let start_idx = t * (ITERATIONS / num_threads);
            let end_idx = (t + 1) * (ITERATIONS / num_threads);
            for i in start_idx..end_idx {
                let op = t_op[i];
                let k = t_keys[i].as_bytes();
                if op == 0 {
                    db_clone.insert(k, t_s_vals[i].as_bytes()).unwrap();
                } else if op == 1 || op == 3 {
                    db_clone.get(k).unwrap();
                } else if op == 2 {
                    db_clone.remove(k).unwrap();
                }
            }
        }));
    }
    for h in handles {
        h.join().unwrap();
    }
    println!("Sled         MT MIXED (4 Th): {} ms", start.elapsed().as_millis());
    println!("--------------------------------------------------");

    // Run Redb
    let _ = fs::remove_file("redb.dat");
    let redb_db = Arc::new(Database::create("redb.dat").unwrap());
    
    let write_txn = redb_db.begin_write().unwrap();
    {
        write_txn.open_table(TABLE).unwrap();
    }
    write_txn.commit().unwrap();

    start = Instant::now();
    let write_txn = redb_db.begin_write().unwrap();
    {
        let mut table = write_txn.open_table(TABLE).unwrap();
        for i in 0..ITERATIONS {
            table.insert(keys[i].as_bytes(), int_vals[i].to_le_bytes().as_ref()).unwrap();
        }
    }
    write_txn.commit().unwrap();
    println!("Redb         ST PUT (Int):    {} ms", start.elapsed().as_millis());

    start = Instant::now();
    let read_txn = redb_db.begin_read().unwrap();
    {
        let table = read_txn.open_table(TABLE).unwrap();
        for i in 0..ITERATIONS {
            table.get(keys[i].as_bytes()).unwrap();
        }
    }
    println!("Redb         ST GET (Int):    {} ms", start.elapsed().as_millis());

    start = Instant::now();
    let write_txn = redb_db.begin_write().unwrap();
    {
        let mut table = write_txn.open_table(TABLE).unwrap();
        for i in 0..ITERATIONS {
            table.insert(keys[i].as_bytes(), string_vals[i].as_bytes()).unwrap();
        }
    }
    write_txn.commit().unwrap();
    println!("Redb         ST PUT (String): {} ms", start.elapsed().as_millis());

    start = Instant::now();
    let read_txn = redb_db.begin_read().unwrap();
    {
        let table = read_txn.open_table(TABLE).unwrap();
        for i in 0..ITERATIONS {
            table.get(keys[i].as_bytes()).unwrap();
        }
    }
    println!("Redb         ST GET (String): {} ms", start.elapsed().as_millis());

    start = Instant::now();
    let mut handles = vec![];
    for t in 0..num_threads {
        let db_clone = redb_db.clone();
        let t_keys = keys.clone();
        let t_s_vals = string_vals.clone();
        let t_op = op_seq.clone();
        
        handles.push(thread::spawn(move || {
            let start_idx = t * (ITERATIONS / num_threads);
            let end_idx = (t + 1) * (ITERATIONS / num_threads);
            // Redb multi-threading requires concurrent transactions, but writes are serialized
            let write_txn = db_clone.begin_write().unwrap();
            {
                let mut table = write_txn.open_table(TABLE).unwrap();
                for i in start_idx..end_idx {
                    let op = t_op[i];
                    let k = t_keys[i].as_bytes();
                    if op == 0 {
                        table.insert(k, t_s_vals[i].as_bytes()).unwrap();
                    } else if op == 1 || op == 3 {
                        table.get(k).unwrap();
                    } else if op == 2 {
                        table.remove(k).unwrap();
                    }
                }
            }
            write_txn.commit().unwrap();
        }));
    }
    for h in handles {
        h.join().unwrap();
    }
    println!("Redb         MT MIXED (4 Th): {} ms", start.elapsed().as_millis());
    println!("==================================================");
}