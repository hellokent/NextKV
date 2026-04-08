#include <iostream>
#include <vector>
#include <string>
#include <chrono>
#include <thread>
#include <random>
#include <filesystem>

#include "../app/src/main/cpp/nextkv/NextKV.h"
#include <lmdb.h>
#include <rocksdb/db.h>
#include <rocksdb/options.h>
#include <rocksdb/slice.h>

const int ITERATIONS = 100000;

std::vector<std::string> keys;
std::vector<std::string> stringValues;
std::vector<int32_t> intValues;
std::vector<int> opSequence;

void initData() {
    std::mt19937 rng(42);
    for (int i = 0; i < ITERATIONS; i++) {
        keys.push_back("key_long_prefix_to_test_hashing_" + std::to_string(i));
        stringValues.push_back("val_string_payload_with_some_length_" + std::to_string(i) + "_" + std::to_string(rng()));
        intValues.push_back(rng());
        opSequence.push_back(rng() % 4);
    }
}

void runNextKV() {
    std::filesystem::remove("nextkv_cpp_sp.data");
    NextKV* kv = new NextKV("nextkv_cpp_sp.data", false); // SP mode

    auto start = std::chrono::high_resolution_clock::now();
    for (int i = 0; i < ITERATIONS; i++) {
        kv->putInt(std::u16string_view(reinterpret_cast<const char16_t*>(keys[i].data()), keys[i].size()), intValues[i]);
    }
    auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::high_resolution_clock::now() - start).count();
    std::cout << "NextKV(C++)  ST PUT (Int):    " << elapsed << " ms\n";

    start = std::chrono::high_resolution_clock::now();
    for (int i = 0; i < ITERATIONS; i++) {
        kv->getInt(std::u16string_view(reinterpret_cast<const char16_t*>(keys[i].data()), keys[i].size()), 0);
    }
    elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::high_resolution_clock::now() - start).count();
    std::cout << "NextKV(C++)  ST GET (Int):    " << elapsed << " ms\n";

    start = std::chrono::high_resolution_clock::now();
    for (int i = 0; i < ITERATIONS; i++) {
        kv->putByteArray(std::u16string_view(reinterpret_cast<const char16_t*>(keys[i].data()), keys[i].size()), (const uint8_t*)stringValues[i].data(), stringValues[i].size());
    }
    elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::high_resolution_clock::now() - start).count();
    std::cout << "NextKV(C++)  ST PUT (String): " << elapsed << " ms\n";

    start = std::chrono::high_resolution_clock::now();
    std::vector<uint8_t> outBuf;
    for (int i = 0; i < ITERATIONS; i++) {
        kv->getByteArray(std::u16string_view(reinterpret_cast<const char16_t*>(keys[i].data()), keys[i].size()), outBuf);
    }
    elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::high_resolution_clock::now() - start).count();
    std::cout << "NextKV(C++)  ST GET (String): " << elapsed << " ms\n";

    // MT MIXED
    start = std::chrono::high_resolution_clock::now();
    int threads = 4;
    std::vector<std::thread> workers;
    for (int t = 0; t < threads; t++) {
        workers.emplace_back([&kv, t, threads]() {
            int startIdx = t * (ITERATIONS / threads);
            int endIdx = (t + 1) * (ITERATIONS / threads);
            std::vector<uint8_t> tlsBuf;
            for (int i = startIdx; i < endIdx; i++) {
                int op = opSequence[i];
                auto u16k = std::u16string_view(reinterpret_cast<const char16_t*>(keys[i].data()), keys[i].size());
                if (op == 0) {
                    kv->putByteArray(u16k, (const uint8_t*)stringValues[i].data(), stringValues[i].size());
                } else if (op == 1) {
                    kv->getByteArray(u16k, tlsBuf);
                } else if (op == 2) {
                    kv->remove(u16k);
                } else {
                    kv->contains(u16k);
                }
            }
        });
    }
    for (auto& w : workers) w.join();
    elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::high_resolution_clock::now() - start).count();
    std::cout << "NextKV(C++)  MT MIXED (4 Th): " << elapsed << " ms\n";

    delete kv;
}

void runRocksDB() {
    std::filesystem::remove_all("rocksdb_cpp_data");
    rocksdb::DB* db;
    rocksdb::Options options;
    options.create_if_missing = true;
    rocksdb::Status status = rocksdb::DB::Open(options, "rocksdb_cpp_data", &db);
    
    auto start = std::chrono::high_resolution_clock::now();
    rocksdb::WriteBatch batch;
    for (int i = 0; i < ITERATIONS; i++) {
        batch.Put(keys[i], rocksdb::Slice((const char*)&intValues[i], sizeof(int32_t)));
    }
    db->Write(rocksdb::WriteOptions(), &batch);
    auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::high_resolution_clock::now() - start).count();
    std::cout << "RocksDB(C++) ST PUT (Int):    " << elapsed << " ms\n";

    start = std::chrono::high_resolution_clock::now();
    std::string val;
    for (int i = 0; i < ITERATIONS; i++) {
        db->Get(rocksdb::ReadOptions(), keys[i], &val);
    }
    elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::high_resolution_clock::now() - start).count();
    std::cout << "RocksDB(C++) ST GET (Int):    " << elapsed << " ms\n";

    start = std::chrono::high_resolution_clock::now();
    rocksdb::WriteBatch batch2;
    for (int i = 0; i < ITERATIONS; i++) {
        batch2.Put(keys[i], stringValues[i]);
    }
    db->Write(rocksdb::WriteOptions(), &batch2);
    elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::high_resolution_clock::now() - start).count();
    std::cout << "RocksDB(C++) ST PUT (String): " << elapsed << " ms\n";

    start = std::chrono::high_resolution_clock::now();
    for (int i = 0; i < ITERATIONS; i++) {
        db->Get(rocksdb::ReadOptions(), keys[i], &val);
    }
    elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::high_resolution_clock::now() - start).count();
    std::cout << "RocksDB(C++) ST GET (String): " << elapsed << " ms\n";

    // MT MIXED
    start = std::chrono::high_resolution_clock::now();
    int threads = 4;
    std::vector<std::thread> workers;
    for (int t = 0; t < threads; t++) {
        workers.emplace_back([&db, t, threads]() {
            int startIdx = t * (ITERATIONS / threads);
            int endIdx = (t + 1) * (ITERATIONS / threads);
            std::string tval;
            for (int i = startIdx; i < endIdx; i++) {
                int op = opSequence[i];
                if (op == 0) {
                    db->Put(rocksdb::WriteOptions(), keys[i], stringValues[i]);
                } else if (op == 1 || op == 3) {
                    db->Get(rocksdb::ReadOptions(), keys[i], &tval);
                } else if (op == 2) {
                    db->Delete(rocksdb::WriteOptions(), keys[i]);
                }
            }
        });
    }
    for (auto& w : workers) w.join();
    elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::high_resolution_clock::now() - start).count();
    std::cout << "RocksDB(C++) MT MIXED (4 Th): " << elapsed << " ms\n";

    delete db;
}

void runLMDB() {
    std::filesystem::remove_all("lmdb_cpp_data");
    std::filesystem::create_directory("lmdb_cpp_data");

    MDB_env *env;
    mdb_env_create(&env);
    mdb_env_set_mapsize(env, 1073741824); // 1GB
    mdb_env_open(env, "lmdb_cpp_data", MDB_NOSYNC, 0664);

    MDB_txn *txn;
    MDB_dbi dbi;
    mdb_txn_begin(env, NULL, 0, &txn);
    mdb_dbi_open(txn, NULL, MDB_CREATE, &dbi);
    mdb_txn_commit(txn);

    auto start = std::chrono::high_resolution_clock::now();
    mdb_txn_begin(env, NULL, 0, &txn);
    for (int i = 0; i < ITERATIONS; i++) {
        MDB_val k = { keys[i].size(), (void*)keys[i].data() };
        MDB_val v = { sizeof(int32_t), &intValues[i] };
        mdb_put(txn, dbi, &k, &v, 0);
    }
    mdb_txn_commit(txn);
    auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::high_resolution_clock::now() - start).count();
    std::cout << "LMDB(C++)    ST PUT (Int):    " << elapsed << " ms\n";

    start = std::chrono::high_resolution_clock::now();
    mdb_txn_begin(env, NULL, MDB_RDONLY, &txn);
    for (int i = 0; i < ITERATIONS; i++) {
        MDB_val k = { keys[i].size(), (void*)keys[i].data() };
        MDB_val v;
        mdb_get(txn, dbi, &k, &v);
    }
    mdb_txn_commit(txn);
    elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::high_resolution_clock::now() - start).count();
    std::cout << "LMDB(C++)    ST GET (Int):    " << elapsed << " ms\n";

    start = std::chrono::high_resolution_clock::now();
    mdb_txn_begin(env, NULL, 0, &txn);
    for (int i = 0; i < ITERATIONS; i++) {
        MDB_val k = { keys[i].size(), (void*)keys[i].data() };
        MDB_val v = { stringValues[i].size(), (void*)stringValues[i].data() };
        mdb_put(txn, dbi, &k, &v, 0);
    }
    mdb_txn_commit(txn);
    elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::high_resolution_clock::now() - start).count();
    std::cout << "LMDB(C++)    ST PUT (String): " << elapsed << " ms\n";

    start = std::chrono::high_resolution_clock::now();
    mdb_txn_begin(env, NULL, MDB_RDONLY, &txn);
    for (int i = 0; i < ITERATIONS; i++) {
        MDB_val k = { keys[i].size(), (void*)keys[i].data() };
        MDB_val v;
        mdb_get(txn, dbi, &k, &v);
    }
    mdb_txn_commit(txn);
    elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::high_resolution_clock::now() - start).count();
    std::cout << "LMDB(C++)    ST GET (String): " << elapsed << " ms\n";

    // MT MIXED
    start = std::chrono::high_resolution_clock::now();
    int threads = 4;
    std::vector<std::thread> workers;
    for (int t = 0; t < threads; t++) {
        workers.emplace_back([&env, dbi, t, threads]() {
            int startIdx = t * (ITERATIONS / threads);
            int endIdx = (t + 1) * (ITERATIONS / threads);
            for (int i = startIdx; i < endIdx; i++) {
                int op = opSequence[i];
                MDB_val k = { keys[i].size(), (void*)keys[i].data() };
                MDB_txn *tx;
                if (op == 0) {
                    MDB_val v = { stringValues[i].size(), (void*)stringValues[i].data() };
                    mdb_txn_begin(env, NULL, 0, &tx);
                    mdb_put(tx, dbi, &k, &v, 0);
                    mdb_txn_commit(tx);
                } else if (op == 1 || op == 3) {
                    MDB_val v;
                    mdb_txn_begin(env, NULL, MDB_RDONLY, &tx);
                    mdb_get(tx, dbi, &k, &v);
                    mdb_txn_commit(tx);
                } else if (op == 2) {
                    mdb_txn_begin(env, NULL, 0, &tx);
                    mdb_del(tx, dbi, &k, NULL);
                    mdb_txn_commit(tx);
                }
            }
        });
    }
    for (auto& w : workers) w.join();
    elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::high_resolution_clock::now() - start).count();
    std::cout << "LMDB(C++)    MT MIXED (4 Th): " << elapsed << " ms\n";

    mdb_env_close(env);
}

int main() {
    std::cout << "==================================================\n";
    std::cout << "Starting C++ 3-Engine KV Benchmark on ARM64 MacOS...\n";
    std::cout << "Iterations: " << ITERATIONS << "\n";
    std::cout << "==================================================\n";

    initData();

    runNextKV();
    std::cout << "--------------------------------------------------\n";
    runRocksDB();
    std::cout << "--------------------------------------------------\n";
    runLMDB();
    std::cout << "==================================================\n";

    return 0;
}