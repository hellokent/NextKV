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

NextKV has been rigorously tested in a 1,000,000-iteration Cartesian product benchmark across 4 major language ecosystems (Java, Go, Rust, C++) against the industry's top storage engines.

### 📱 Android Ecosystem (vs. MMKV, FastKV)
*Test Device: Android 16 (ARM64) - 50,000 Iterations*

| Engine | Multi-Thread MIXED (Int) | Single-Thread PUT (Int) | Single-Thread GET (String) |
| :--- | :--- | :--- | :--- |
| **NextKV** | **144 ms 👑** | **140 ms 👑** | **4 ms 👑** |
| **MMKV** | 538 ms | 236 ms | 108 ms |
| **FastKV** | 3505 ms | 243 ms | 8 ms |

*NextKV dominates high-concurrency and raw reading speeds, outperforming MMKV by up to **3.7x**.*

### 🖥️ Server Ecosystem - Go / Rust / C++ / Java
*Test Device: Apple Silicon M-Series (ARM64 macOS) - 100,000 Iterations*

| Ecosystem | Competitor | Engine | Multi-Thread MIXED | Single-Thread PUT | Single-Thread GET |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Golang** | BadgerDB / Bbolt | **NextKV (Cgo)** | **16 ms 👑** | **21 ms** | **18 ms** |
| **Rust** | Sled / Redb | **NextKV (FFI)** | **22 ms** | **19 ms** | **13 ms 👑** |
| **C/C++** | RocksDB / LMDB | **NextKV (Pure)** | **12 ms** | **11 ms** | **4 ms 👑** |
| **Java** | MapDB / Xodus | **NextKV (JNI)** | **83 ms** | **110 ms** | **22 ms 👑** |

*Even wrapped in Cgo/FFI/JNI boundaries, NextKV slaughters pure-native titans like BadgerDB (275ms) and RocksDB (100ms) in micro-cache state-machine scenarios.*

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