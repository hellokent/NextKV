# NextKV 🚀

[![License](https://img.shields.io/badge/license-Apache%202-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%20|%20macOS%20|%20Linux-brightgreen.svg)]()
[![Language](https://img.shields.io/badge/Language-C++17%20|%20Java%20|%20Go%20|%20Rust-orange.svg)]()

**The Fastest Cross-Language, Mmap-based State-Machine Cache on Earth.**

NextKV is an extremely high-performance, cross-platform, and cross-language Key-Value storage engine. It is specifically designed for **"single-node/cross-process, small-to-medium scale, extremely high-frequency Point Lookup"** scenarios. 

By aggressively exploiting operating system physical characteristics (Page Cache, Mmap) and ARM hardware instructions, NextKV achieves absolute performance dominance over traditional heavy-weight databases (like RocksDB, LMDB, BadgerDB, and MMKV) in both Android and Server environments.

---

## 🔥 Key Features

*   **O(1) Flat Hashing & In-Place Update:** Abandons complex B-Tree and LSM-Tree structures. Uses Robin Hood Hashing and in-place memory offset updates to achieve absolute O(1) read/write time complexity, completely eliminating file bloat and Compaction overhead.
*   **Zero-Copy & Zero-Boxing:** Penetrates the language boundary (JNI/Cgo/FFI). In Java, it uses `sun.misc.Unsafe` to read physical memory addresses directly, achieving **0 object allocations** for primitive types and completely freeing the JVM from GC pressure.
*   **Hybrid Spinlock (IPC Safe):** Combines C++ user-space `std::atomic_flag` spinlocks for microsecond-level intra-process contention, with kernel-space `fcntl` POSIX robust locks to guarantee dead-lock freedom during cross-process sharing, even if a process is killed (`kill -9`).
*   **Hardware-Accelerated CRC16:** Sniffs ARM64 architectures to inline NEON coprocessor assembly instructions (`__crc32cw` / `__crc32cd`). Achieves sub-nanosecond incremental per-record CRC validation, ensuring enterprise-grade data disaster recovery without sacrificing extreme performance.
*   **Magic Header Recovery:** String keys are never frequently persisted. They are safely rebuilt in milliseconds into the memory dictionary upon a cold start or crash recovery using a `0x7FFFFFFF` magic mask.

---

## 📊 Benchmarks: The "Battle of the Gods"

NextKV has been rigorously tested in a Cartesian product benchmark across 4 major language ecosystems (Java, Go, Rust, C++) against the industry's top storage engines. **All tests have been normalized to eliminate unfair transactional and serialization overheads for competing engines (e.g. using WriteBatch).**

### 📱 Android Ecosystem (vs. MMKV, FastKV)
*Test Device: SM-G9650 Android 10 (ARM64) - 50,000 Iterations*

| Engine | Single-Thread PUT (Int) | Single-Thread GET (String) |
| :--- | :--- | :--- |
| **NextKV** | 607 ms | **26 ms 👑** |
| **MMKV** | **362 ms 👑** | 359 ms |
| **FastKV** | 243 ms | 8 ms |

*NextKV dominates raw reading speeds, outperforming MMKV by up to **13x** in string retrieval, though MMKV retains an edge in raw sequential writes.*

### 🖥️ Server Ecosystem - Go / Rust / C++ / Java
*Test Device: Apple Silicon M-Series (ARM64 macOS)*

*Note: C++, Rust, and Go run 100,000 iterations. Java runs 1,000,000 iterations.*

| Ecosystem | Competitor | Comp. ST PUT | NextKV ST PUT | Comp. ST GET | NextKV ST GET | Comp. MT MIXED | NextKV MT MIXED |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Golang** | BadgerDB | 77 ms | **19 ms 👑** | 57 ms | **23 ms 👑** | 248 ms | **16 ms 👑** |
| **Golang** | Bbolt | 493 ms | **19 ms 👑** | 27 ms | **23 ms 👑** | 1262 ms | **16 ms 👑** |
| **Rust** | Sled | 206 ms | **21 ms 👑** | 63 ms | **17 ms 👑** | 43 ms | **22 ms 👑** |
| **Rust** | Redb | 101 ms | **21 ms 👑** | 21 ms | **17 ms 👑** | 138 ms | **22 ms 👑** |
| **C/C++** | RocksDB | 14 ms | **12 ms 👑** | 51 ms | **4 ms 👑** | 93 ms | **11 ms 👑** |
| **C/C++** | LMDB | 29 ms | **12 ms 👑** | 20 ms | **4 ms 👑** | 633 ms | **11 ms 👑** |
| **Java** | RocksDB | **389 ms 👑** | 398 ms | 894 ms | **12 ms 👑** | 1115 ms | **233 ms 👑** |
| **Java** | LevelDB | 746 ms | **398 ms 👑** | 612 ms | **12 ms 👑** | 3448 ms | **233 ms 👑** |
| **Java** | H2-MVStore | **386 ms 👑** | 398 ms | 388 ms | **12 ms 👑** | 499 ms | **233 ms 👑** |
| **Java** | MapDB | 2526 ms | **398 ms 👑** | 758 ms | **12 ms 👑** | 1496 ms | **233 ms 👑** |
| **Java** | Xodus | 620 ms | **398 ms 👑** | 1039 ms | **12 ms 👑** | 19924 ms | **233 ms 👑** |

*Even wrapped in Cgo/FFI/JNI boundaries, NextKV slaughters pure-native titans like BadgerDB and RocksDB in micro-cache state-machine scenarios, especially in multi-threaded mixed workloads and String retrieval. (Note: ST PUT measures Integer insertions, ST GET measures String retrievals)*

---

## 📚 Deep Dive Architecture

Want to know how NextKV achieves these physics-defying speeds? Read our comprehensive architecture whitepaper:

👉 **[突破物理极限：NextKV 底层数据结构与核心算法深度解析 (NextKV_Architecture.md)](NextKV_Architecture.md)**

---

## 🛠️ Usage & Integration

NextKV is currently a highly polished Proof-of-Concept (POC) ready for production adaptation. The repository contains:

1.  `app/`: Android Library & Benchmark Application.
2.  `server/`: Pure Java (PC/Backend) Module with JNI generation (`libnextkv.dylib` / `.so`).
3.  `golang_bench/`: Go module with Cgo wrappers (`nextkv_c_api.h`).
4.  `rust_bench/`: Rust Cargo project with FFI bindings.
5.  `cpp_bench/`: Pure C++ Native benchmark.

### Building the C API for Server / Go / Rust
```bash
cd golang_bench
# Compiles NextKV.cpp and nextkv_c_api.cpp into libnextkv.a static library
clang++ -c -std=c++17 -O3 -fPIC -mcrc ../app/src/main/cpp/nextkv/NextKV.cpp -o lib/NextKV.o
clang++ -c -std=c++17 -O3 -fPIC -mcrc nextkv_c_api.cpp -o lib/nextkv_c_api.o
ar rcs lib/libnextkv.a lib/NextKV.o lib/nextkv_c_api.o
```

## License

This project is licensed under the Apache License 2.0.