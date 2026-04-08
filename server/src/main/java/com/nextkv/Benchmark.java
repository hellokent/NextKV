package com.nextkv;

import org.rocksdb.RocksDB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import org.lmdbjava.Env;
import org.lmdbjava.Dbi;
import org.lmdbjava.Txn;
import org.lmdbjava.DbiFlags;

import net.openhft.chronicle.map.ChronicleMap;

// Removed HaloDB


import jetbrains.exodus.env.Environment;
import jetbrains.exodus.env.Environments;
import jetbrains.exodus.env.Store;
import jetbrains.exodus.env.StoreConfig;
import jetbrains.exodus.env.Transaction;
import jetbrains.exodus.ArrayByteIterable;

import org.h2.mvstore.MVStore;
import org.h2.mvstore.MVMap;

import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import static org.iq80.leveldb.impl.Iq80DBFactory.factory;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

public class Benchmark {
    private static final int ITERATIONS = 1000000;
    private static String[] keys;
    private static String[] stringValues;
    private static int[] intValues;
    private static int[] opSequence;

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("Starting PC/Server 10-Engine KV Benchmark on ARM64 MacOS...");
        System.out.println("Iterations: " + ITERATIONS);
        System.out.println("==================================================");

        try { RocksDB.loadLibrary(); } catch(Exception e){}
        
        keys = new String[ITERATIONS];
        stringValues = new String[ITERATIONS];
        intValues = new int[ITERATIONS];
        opSequence = new int[ITERATIONS];

        Random r = new Random(42);
        for(int i=0; i<ITERATIONS; i++) {
            keys[i] = "key_long_prefix_to_test_hashing_" + i;
            stringValues[i] = "val_string_payload_with_some_length_" + UUID.randomUUID().toString();
            intValues[i] = r.nextInt();
            opSequence[i] = r.nextInt(4); // 0=put, 1=get, 2=remove, 3=contains
        }

        runNextKV_SP();
        System.out.println("--------------------------------------------------");
        runNextKV_MP();
        System.out.println("--------------------------------------------------");
                        runLMDB();
        System.out.println("--------------------------------------------------");
        runRocksDB();
        System.out.println("--------------------------------------------------");
        runLevelDB();
        System.out.println("--------------------------------------------------");
        runMapDB();
        System.out.println("--------------------------------------------------");
        runMVStore();
        System.out.println("--------------------------------------------------");
        runXodus();
        System.out.println("==================================================");
        System.exit(0);
    }

    private static void deleteDir(File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) deleteDir(child);
        }
        file.delete();
    }

    private static void runNextKV_SP() throws Exception {
        File f = new File("nextkv_server_sp.data");
        if (f.exists()) f.delete();
        
        NextKV.init(f.getAbsolutePath(), false); // SP Mode
        NextKV kv = new NextKV(false);

        long start = System.currentTimeMillis();
        for(int i=0; i<ITERATIONS; i++) {
            kv.putInt(keys[i], intValues[i]);
        }
        System.out.println("NextKV(SP)   ST PUT (Int):    " + (System.currentTimeMillis() - start) + " ms");

        start = System.currentTimeMillis();
        for(int i=0; i<ITERATIONS; i++) {
            kv.getInt(keys[i], 0);
        }
        System.out.println("NextKV(SP)   ST GET (Int):    " + (System.currentTimeMillis() - start) + " ms");

        start = System.currentTimeMillis();
        for(int i=0; i<ITERATIONS; i++) {
            kv.putString(keys[i], stringValues[i]);
        }
        System.out.println("NextKV(SP)   ST PUT (String): " + (System.currentTimeMillis() - start) + " ms");

        start = System.currentTimeMillis();
        for(int i=0; i<ITERATIONS; i++) {
            kv.getString(keys[i], "");
        }
        System.out.println("NextKV(SP)   ST GET (String): " + (System.currentTimeMillis() - start) + " ms");

        int threads = 4;
        CountDownLatch latch = new CountDownLatch(threads);
        start = System.currentTimeMillis();
        for(int t=0; t<threads; t++) {
            final int tIdx = t;
            new Thread(() -> {
                for(int i=tIdx*(ITERATIONS/threads); i<(tIdx+1)*(ITERATIONS/threads); i++) {
                    int op = opSequence[i];
                    if (op == 0) kv.putString(keys[i], stringValues[i]);
                    else if (op == 1) kv.getString(keys[i], "");
                    else if (op == 2) kv.remove(keys[i]);
                    else kv.contains(keys[i]);
                }
                latch.countDown();
            }).start();
        }
        latch.await();
        System.out.println("NextKV(SP)   MT MIXED (4 Th): " + (System.currentTimeMillis() - start) + " ms");
    }

private static void runNextKV_MP() throws Exception {
        File f = new File("nextkv_server_mp.data");
        if (f.exists()) f.delete();
        
        NextKV.init(f.getAbsolutePath(), true); // MP Mode to test locks
        NextKV kv = new NextKV(true);

        long start = System.currentTimeMillis();
        for(int i=0; i<ITERATIONS; i++) {
            kv.putInt(keys[i], intValues[i]);
        }
        System.out.println("NextKV(MP)   ST PUT (Int):    " + (System.currentTimeMillis() - start) + " ms");

        start = System.currentTimeMillis();
        for(int i=0; i<ITERATIONS; i++) {
            kv.getInt(keys[i], 0);
        }
        System.out.println("NextKV(MP)   ST GET (Int):    " + (System.currentTimeMillis() - start) + " ms");

        start = System.currentTimeMillis();
        for(int i=0; i<ITERATIONS; i++) {
            kv.putString(keys[i], stringValues[i]);
        }
        System.out.println("NextKV(MP)   ST PUT (String): " + (System.currentTimeMillis() - start) + " ms");

        start = System.currentTimeMillis();
        for(int i=0; i<ITERATIONS; i++) {
            kv.getString(keys[i], "");
        }
        System.out.println("NextKV(MP)   ST GET (String): " + (System.currentTimeMillis() - start) + " ms");

        int threads = 4;
        CountDownLatch latch = new CountDownLatch(threads);
        start = System.currentTimeMillis();
        for(int t=0; t<threads; t++) {
            final int tIdx = t;
            new Thread(() -> {
                for(int i=tIdx*(ITERATIONS/threads); i<(tIdx+1)*(ITERATIONS/threads); i++) {
                    int op = opSequence[i];
                    if (op == 0) kv.putString(keys[i], stringValues[i]);
                    else if (op == 1) kv.getString(keys[i], "");
                    else if (op == 2) kv.remove(keys[i]);
                    else kv.contains(keys[i]);
                }
                latch.countDown();
            }).start();
        }
        latch.await();
        System.out.println("NextKV(MP)   MT MIXED (4 Th): " + (System.currentTimeMillis() - start) + " ms");
    }

    private static void runLMDB() throws Exception {
        File f = new File("lmdb");
        deleteDir(f);
        f.mkdirs();
        try(Env<ByteBuffer> env = Env.create()
            .setMapSize(1073741824) // 1GB
            .setMaxDbs(1)
            .open(f)) {

            Dbi<ByteBuffer> db = env.openDbi("db", DbiFlags.MDB_CREATE);

            long start = System.currentTimeMillis();
            for(int i=0; i<ITERATIONS; i++) {
                ByteBuffer k = ByteBuffer.allocateDirect(keys[i].getBytes().length).put(keys[i].getBytes()).flip();
                ByteBuffer v = ByteBuffer.allocateDirect(4).putInt(intValues[i]).flip();
                db.put(k, v);
            }
            System.out.println("LMDB(JNI)    ST PUT (Int):    " + (System.currentTimeMillis() - start) + " ms");

            start = System.currentTimeMillis();
            try (Txn<ByteBuffer> txn = env.txnRead()) {
                for(int i=0; i<ITERATIONS; i++) {
                    ByteBuffer k = ByteBuffer.allocateDirect(keys[i].getBytes().length).put(keys[i].getBytes()).flip();
                    db.get(txn, k);
                }
            }
            System.out.println("LMDB(JNI)    ST GET (Int):    " + (System.currentTimeMillis() - start) + " ms");

            start = System.currentTimeMillis();
            for(int i=0; i<ITERATIONS; i++) {
                ByteBuffer k = ByteBuffer.allocateDirect(keys[i].getBytes().length).put(keys[i].getBytes()).flip();
                ByteBuffer v = ByteBuffer.allocateDirect(stringValues[i].getBytes().length).put(stringValues[i].getBytes()).flip();
                db.put(k, v);
            }
            System.out.println("LMDB(JNI)    ST PUT (String): " + (System.currentTimeMillis() - start) + " ms");

            start = System.currentTimeMillis();
            try (Txn<ByteBuffer> txn = env.txnRead()) {
                for(int i=0; i<ITERATIONS; i++) {
                    ByteBuffer k = ByteBuffer.allocateDirect(keys[i].getBytes().length).put(keys[i].getBytes()).flip();
                    db.get(txn, k);
                }
            }
            System.out.println("LMDB(JNI)    ST GET (String): " + (System.currentTimeMillis() - start) + " ms");

            int threads = 4;
            CountDownLatch latch = new CountDownLatch(threads);
            start = System.currentTimeMillis();
            for(int t=0; t<threads; t++) {
                final int tIdx = t;
                new Thread(() -> {
                    try {
                        for(int i=tIdx*(ITERATIONS/threads); i<(tIdx+1)*(ITERATIONS/threads); i++) {
                            int op = opSequence[i];
                            ByteBuffer k = ByteBuffer.allocateDirect(keys[i].getBytes().length).put(keys[i].getBytes()).flip();
                            if (op == 0) {
                                ByteBuffer v = ByteBuffer.allocateDirect(stringValues[i].getBytes().length).put(stringValues[i].getBytes()).flip();
                                db.put(k, v);
                            } else if (op == 1 || op == 3) {
                                try (Txn<ByteBuffer> txn = env.txnRead()) {
                                    db.get(txn, k);
                                }
                            } else if (op == 2) {
                                db.delete(k);
                            }
                        }
                    }catch(Exception e){}
                    latch.countDown();
                }).start();
            }
            latch.await();
            System.out.println("LMDB(JNI)    MT MIXED (4 Th): " + (System.currentTimeMillis() - start) + " ms");
        }
    }


    private static void runRocksDB() throws Exception {
        File dir = new File("rocksdb_server_data");
        deleteDir(dir);

        try (final org.rocksdb.Options options = new org.rocksdb.Options().setCreateIfMissing(true)) {
            try (final RocksDB db = RocksDB.open(options, dir.getAbsolutePath())) {
                
                long start = System.currentTimeMillis();
                try(org.rocksdb.WriteBatch batch = new org.rocksdb.WriteBatch()) {
                    for(int i=0; i<ITERATIONS; i++) {
                        batch.put(keys[i].getBytes(), ByteBuffer.allocate(4).putInt(intValues[i]).array());
                    }
                    try(org.rocksdb.WriteOptions opt = new org.rocksdb.WriteOptions()) {
                        db.write(opt, batch);
                    }
                }
                System.out.println("RocksDB(JNI) ST PUT (Int):    " + (System.currentTimeMillis() - start) + " ms");

                start = System.currentTimeMillis();
                for(int i=0; i<ITERATIONS; i++) {
                    db.get(keys[i].getBytes());
                }
                System.out.println("RocksDB(JNI) ST GET (Int):    " + (System.currentTimeMillis() - start) + " ms");

                start = System.currentTimeMillis();
                try(org.rocksdb.WriteBatch batch = new org.rocksdb.WriteBatch()) {
                    for(int i=0; i<ITERATIONS; i++) {
                        batch.put(keys[i].getBytes(), stringValues[i].getBytes());
                    }
                    try(org.rocksdb.WriteOptions opt = new org.rocksdb.WriteOptions()) {
                        db.write(opt, batch);
                    }
                }
                System.out.println("RocksDB(JNI) ST PUT (String): " + (System.currentTimeMillis() - start) + " ms");

                start = System.currentTimeMillis();
                for(int i=0; i<ITERATIONS; i++) {
                    db.get(keys[i].getBytes());
                }
                System.out.println("RocksDB(JNI) ST GET (String): " + (System.currentTimeMillis() - start) + " ms");

                int threads = 4;
                CountDownLatch latch = new CountDownLatch(threads);
                start = System.currentTimeMillis();
                for(int t=0; t<threads; t++) {
                    final int tIdx = t;
                    new Thread(() -> {
                        try {
                            for(int i=tIdx*(ITERATIONS/threads); i<(tIdx+1)*(ITERATIONS/threads); i++) {
                                int op = opSequence[i];
                                if (op == 0) db.put(keys[i].getBytes(), stringValues[i].getBytes());
                                else if (op == 1) db.get(keys[i].getBytes());
                                else if (op == 2) db.delete(keys[i].getBytes());
                                else db.keyMayExist(keys[i].getBytes(), null);
                            }
                        } catch(Exception e){}
                        latch.countDown();
                    }).start();
                }
                latch.await();
                System.out.println("RocksDB(JNI) MT MIXED (4 Th): " + (System.currentTimeMillis() - start) + " ms");
            }
        }
    }

    private static void runLevelDB() throws Exception {
        File dir = new File("leveldb");
        deleteDir(dir);

        Options options = new Options();
        options.createIfMissing(true);
        try(DB db = factory.open(dir, options)) {

            long start = System.currentTimeMillis();
            try(org.iq80.leveldb.WriteBatch batch = db.createWriteBatch()) {
                    for(int i=0; i<ITERATIONS; i++) {
                        batch.put(keys[i].getBytes(), ByteBuffer.allocate(4).putInt(intValues[i]).array());
                    }
                    db.write(batch);
                }
            System.out.println("LevelDB(J)   ST PUT (Int):    " + (System.currentTimeMillis() - start) + " ms");

            start = System.currentTimeMillis();
            for(int i=0; i<ITERATIONS; i++) {
                db.get(keys[i].getBytes());
            }
            System.out.println("LevelDB(J)   ST GET (Int):    " + (System.currentTimeMillis() - start) + " ms");

            start = System.currentTimeMillis();
            try(org.iq80.leveldb.WriteBatch batch = db.createWriteBatch()) {
                    for(int i=0; i<ITERATIONS; i++) {
                        batch.put(keys[i].getBytes(), stringValues[i].getBytes());
                    }
                    db.write(batch);
                }
            System.out.println("LevelDB(J)   ST PUT (String): " + (System.currentTimeMillis() - start) + " ms");

            start = System.currentTimeMillis();
            for(int i=0; i<ITERATIONS; i++) {
                db.get(keys[i].getBytes());
            }
            System.out.println("LevelDB(J)   ST GET (String): " + (System.currentTimeMillis() - start) + " ms");

            int threads = 4;
            CountDownLatch latch = new CountDownLatch(threads);
            start = System.currentTimeMillis();
            for(int t=0; t<threads; t++) {
                final int tIdx = t;
                new Thread(() -> {
                    try {
                        for(int i=tIdx*(ITERATIONS/threads); i<(tIdx+1)*(ITERATIONS/threads); i++) {
                            int op = opSequence[i];
                            if (op == 0) db.put(keys[i].getBytes(), stringValues[i].getBytes());
                            else if (op == 1 || op == 3) db.get(keys[i].getBytes());
                            else if (op == 2) db.delete(keys[i].getBytes());
                        }
                    } catch(Exception e){}
                    latch.countDown();
                }).start();
            }
            latch.await();
            System.out.println("LevelDB(J)   MT MIXED (4 Th): " + (System.currentTimeMillis() - start) + " ms");
        }
    }

    private static void runMapDB() throws Exception {
        File f = new File("mapdb_server.data");
        if (f.exists()) f.delete();

        org.mapdb.DB db = DBMaker.fileDB(f).fileMmapEnable().make();
        HTreeMap<String, Object> map = db.hashMap("map", Serializer.STRING, Serializer.JAVA).createOrOpen();

        long start = System.currentTimeMillis();
        for(int i=0; i<ITERATIONS; i++) {
            map.put(keys[i], intValues[i]);
        }
        System.out.println("MapDB(J)     ST PUT (Int):    " + (System.currentTimeMillis() - start) + " ms");

        start = System.currentTimeMillis();
        for(int i=0; i<ITERATIONS; i++) {
            map.get(keys[i]);
        }
        System.out.println("MapDB(J)     ST GET (Int):    " + (System.currentTimeMillis() - start) + " ms");

        start = System.currentTimeMillis();
        for(int i=0; i<ITERATIONS; i++) {
            map.put(keys[i], stringValues[i]);
        }
        System.out.println("MapDB(J)     ST PUT (String): " + (System.currentTimeMillis() - start) + " ms");

        start = System.currentTimeMillis();
        for(int i=0; i<ITERATIONS; i++) {
            map.get(keys[i]);
        }
        System.out.println("MapDB(J)     ST GET (String): " + (System.currentTimeMillis() - start) + " ms");

        int threads = 4;
        CountDownLatch latch = new CountDownLatch(threads);
        start = System.currentTimeMillis();
        for(int t=0; t<threads; t++) {
            final int tIdx = t;
            new Thread(() -> {
                for(int i=tIdx*(ITERATIONS/threads); i<(tIdx+1)*(ITERATIONS/threads); i++) {
                    int op = opSequence[i];
                    if (op == 0) map.put(keys[i], stringValues[i]);
                    else if (op == 1) map.get(keys[i]);
                    else if (op == 2) map.remove(keys[i]);
                    else map.containsKey(keys[i]);
                }
                latch.countDown();
            }).start();
        }
        latch.await();
        System.out.println("MapDB(J)     MT MIXED (4 Th): " + (System.currentTimeMillis() - start) + " ms");
        db.close();
    }

    private static void runMVStore() throws Exception {
        File f = new File("h2mvstore.data");
        if (f.exists()) f.delete();

        try(MVStore s = new MVStore.Builder().fileName(f.getAbsolutePath()).open()) {
            MVMap<String, String> map = s.openMap("data");
            
            long start = System.currentTimeMillis();
            for(int i=0; i<ITERATIONS; i++) {
                map.put(keys[i], String.valueOf(intValues[i]));
            }
            System.out.println("H2-MVStore   ST PUT (Int):    " + (System.currentTimeMillis() - start) + " ms");

            start = System.currentTimeMillis();
            for(int i=0; i<ITERATIONS; i++) {
                map.get(keys[i]);
            }
            System.out.println("H2-MVStore   ST GET (Int):    " + (System.currentTimeMillis() - start) + " ms");

            start = System.currentTimeMillis();
            for(int i=0; i<ITERATIONS; i++) {
                map.put(keys[i], stringValues[i]);
            }
            System.out.println("H2-MVStore   ST PUT (String): " + (System.currentTimeMillis() - start) + " ms");

            start = System.currentTimeMillis();
            for(int i=0; i<ITERATIONS; i++) {
                map.get(keys[i]);
            }
            System.out.println("H2-MVStore   ST GET (String): " + (System.currentTimeMillis() - start) + " ms");

            int threads = 4;
            CountDownLatch latch = new CountDownLatch(threads);
            start = System.currentTimeMillis();
            for(int t=0; t<threads; t++) {
                final int tIdx = t;
                new Thread(() -> {
                    for(int i=tIdx*(ITERATIONS/threads); i<(tIdx+1)*(ITERATIONS/threads); i++) {
                        int op = opSequence[i];
                        if (op == 0) map.put(keys[i], stringValues[i]);
                        else if (op == 1) map.get(keys[i]);
                        else if (op == 2) map.remove(keys[i]);
                        else map.containsKey(keys[i]);
                    }
                    latch.countDown();
                }).start();
            }
            latch.await();
            System.out.println("H2-MVStore   MT MIXED (4 Th): " + (System.currentTimeMillis() - start) + " ms");
        }
    }

    private static void runXodus() throws Exception {
        File dir = new File("xodus");
        deleteDir(dir);

        try(Environment env = Environments.newInstance(dir)) {
            final Store store = env.computeInTransaction(txn -> 
                env.openStore("store", StoreConfig.WITHOUT_DUPLICATES, txn));
            
            long start = System.currentTimeMillis();
            env.executeInTransaction(txn -> {
                for(int i=0; i<ITERATIONS; i++) {
                    store.put(txn, new ArrayByteIterable(keys[i].getBytes()), new ArrayByteIterable(ByteBuffer.allocate(4).putInt(intValues[i]).array()));
                }
            });
            System.out.println("Xodus(J)     ST PUT (Int):    " + (System.currentTimeMillis() - start) + " ms");

            start = System.currentTimeMillis();
            env.executeInReadonlyTransaction(txn -> {
                for(int i=0; i<ITERATIONS; i++) {
                    store.get(txn, new ArrayByteIterable(keys[i].getBytes()));
                }
            });
            System.out.println("Xodus(J)     ST GET (Int):    " + (System.currentTimeMillis() - start) + " ms");

            start = System.currentTimeMillis();
            env.executeInTransaction(txn -> {
                for(int i=0; i<ITERATIONS; i++) {
                    store.put(txn, new ArrayByteIterable(keys[i].getBytes()), new ArrayByteIterable(stringValues[i].getBytes()));
                }
            });
            System.out.println("Xodus(J)     ST PUT (String): " + (System.currentTimeMillis() - start) + " ms");

            start = System.currentTimeMillis();
            env.executeInReadonlyTransaction(txn -> {
                for(int i=0; i<ITERATIONS; i++) {
                    store.get(txn, new ArrayByteIterable(keys[i].getBytes()));
                }
            });
            System.out.println("Xodus(J)     ST GET (String): " + (System.currentTimeMillis() - start) + " ms");

            int threads = 4;
            CountDownLatch latch = new CountDownLatch(threads);
            start = System.currentTimeMillis();
            for(int t=0; t<threads; t++) {
                final int tIdx = t;
                new Thread(() -> {
                    for(int i=tIdx*(ITERATIONS/threads); i<(tIdx+1)*(ITERATIONS/threads); i++) {
                        int op = opSequence[i];
                        int finalI = i;
                        if (op == 0) {
                            env.executeInTransaction(txn -> {
                                store.put(txn, new ArrayByteIterable(keys[finalI].getBytes()), new ArrayByteIterable(stringValues[finalI].getBytes()));
                            });
                        }
                        else if (op == 1 || op == 3) {
                            env.executeInReadonlyTransaction(txn -> {
                                store.get(txn, new ArrayByteIterable(keys[finalI].getBytes()));
                            });
                        }
                        else if (op == 2) {
                            env.executeInTransaction(txn -> {
                                store.delete(txn, new ArrayByteIterable(keys[finalI].getBytes()));
                            });
                        }
                    }
                    latch.countDown();
                }).start();
            }
            latch.await();
            System.out.println("Xodus(J)     MT MIXED (4 Th): " + (System.currentTimeMillis() - start) + " ms");
        }
    }
}