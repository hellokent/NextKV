# FastKV: 纯 Java Mmap 挑战者深度剖析

## 1. 核心架构与设计哲学

FastKV 是一款专为 Android 平台设计的高性能键值对（Key-Value）存储库。与严重依赖 C++ 和 JNI（Java Native Interface）的 MMKV 不同，FastKV 的核心是完全由 Java/Kotlin 编写的。

它的基础设计哲学非常明确：**“在完全避免 JNI 带来的上下文切换开销与复杂性的前提下，实现比肩甚至超越 C++ 的性能。”**

### 核心技术点：
*   **内存映射文件 (`mmap`) 与 `MappedByteBuffer`：** FastKV 利用了 Java 原生 NIO（New I/O）中的 `MappedByteBuffer` 将文件直接映射到内存中。这意味着在 FastKV 中读取数据本质上就是直接读取 RAM，从而获得了极高的速度。
*   **顺序追加与数据压缩 (Sequential Append & Compaction)：** 它的写入策略是“追加写（Append-Only）”。当一个 Value 被更新时，它只是将新值追加到文件末尾，并废弃旧值。为了防止文件无限膨胀，它会在后台触发 Compaction（垃圾回收/碎片整理）来移除这些失效的“死数据”。
*   **零 JNI 切换开销 (Zero JNI Context Switching)：** 每次 Java 代码调用 C++ 方法（如 MMKV 或我们的 NextKV）时，Android 虚拟机 (ART) 都必须执行一次极其微小的上下文切换。虽然单次开销极低，但在密集的循环中会聚沙成塔。FastKV 完全留在 JVM 内部，彻底消灭了这一微秒级惩罚。

---

## 2. 存储结构布局

FastKV 的数据主要存储在两个部分：
1.  **内存字典 (`ConcurrentHashMap`)：** 一张极速的内存哈希表，用于存储 Key 以及该 Key 对应的 Value 在 Mmap 映射文件中的**绝对偏移量 (Offset)**。
2.  **Mmap 文件 (`MappedByteBuffer`)：** 实际持久化在磁盘上的二进制数据。

当你调用 `putInt("key", 10)` 时，发生了什么：
1.  FastKV 将 Key 和 Value 序列化为字节数组。
2.  将这个字节数组直接追加到 `MappedByteBuffer` 的当前末尾偏移量处。
3.  更新内存中的 `ConcurrentHashMap`，让这个 Key 指向刚刚写入的新偏移量。

---

## 3. 为什么 FastKV 能在单线程写入 Benchmark 中取胜？

在我们之前进行的 50,000 次笛卡尔积极限压测（Android 10 和 Android 16 双端）中，FastKV 展现出了一个非常反直觉的优势：它在 **单线程连续顺序写入 (ST PUT)** 这个单一维度上，击败了用 C++ 武装到牙齿的 NextKV 和老牌霸主 MMKV。

*   **MMKV / NextKV 的必然惩罚：** 即使 NextKV 拥有极端的 C++ 优化（比如泛型原位覆写、零装箱），它依然必须为*每一次写入*跨越一次 JNI 边界。在 50,000 次死循环密集写入中，这 50,000 次 JNI 切换堆叠成了一座大山。
*   **FastKV 的主场优势：** FastKV 完全运行在 JVM 的内存空间内。在 Java 中对 `MappedByteBuffer` 进行写入，其实就是一条最底层的内存指针赋值指令（由 JVM 编译为机器码）。这段死循环行云流水，ART 虚拟机完全不需要准备 Native 栈或者封送参数。

---

## 4. 阿喀琉斯之踵：为什么 FastKV 在高并发下全面崩溃？

虽然 FastKV 是单线程环境下的竞速狂魔，但我们的 Benchmark 数据无情地揭露了它在面对 **多线程混合操作 (MT MIXED - 读/写/删 混合轰炸)** 时的灾难性性能滑坡。在这个真实且恶劣的业务场景下，NextKV 的性能直接碾压了 FastKV 足足 **8倍 到 25倍**。

### 溃败原因深度解析：

1.  **沉重的 Java 层同步锁 (Heavy Java Synchronization)：** 因为 FastKV 必须在 JVM 内部保证线程安全，它高度依赖 Java 层的锁机制（如 `synchronized` 代码块和 `ReentrantReadWriteLock`）。在极端竞争下（4条线程同时疯狂读写删），这些高级锁会导致严重的线程阻塞和操作系统级的上下文切换。
2.  **Compaction 陷阱 (The Compaction Trap)：** 如前文所述，FastKV 使用的是“追加写”策略。如果 4 条线程不断地更新和删除 Key，Mmap 文件会以前所未有的速度被“死数据”填满。FastKV 被迫频繁暂停工作，触发内部的 Compaction 算法（将存活的数据拷贝到一个新文件，然后删掉旧文件）。在高并发期间进行这种大规模的 I/O 拷贝，会导致极度夸张的延迟毛刺。
3.  **多进程的复杂性 (`MPFastKV`)：** 为了支持跨进程，FastKV 必须使用 Android 系统的 `FileLock` (底层就是 POSIX 的 `fcntl`)。然而，使用文件锁来协调不同虚拟机进程之间的 Java 对象序列化和巨型文件的 Compaction 回收，其效率极其低下。相比之下，NextKV 直接在共享内存中使用极轻量的 C++ `std::atomic` 自旋锁进行指令级调度，自然是降维打击。

---

## 5. 总结建议：FastKV vs. NextKV

基于以上深度的架构剖析和实打实的 Benchmark 数据，我们得出以下技术选型建议：

*   **选择 FastKV 的场景：** 如果你开发的是一个**纯单进程 App**，并且核心需求是做**单线程、超高频的日志记录或数据打点**。在这种场景下，JNI 的边界开销是最大的瓶颈，纯 Java 且基于 Mmap 的 FastKV 是你最好的神兵利器。
*   **选择 NextKV 的场景：** 如果你正在构建一个健壮的、国民级的企业架构，它要求**极端的跨进程状态共享（如主进程与小程序/推送进程的通讯）**、**免疫进程强杀的数据防丢容灾能力**、以及**扛住多线程并发轰炸的 0 GC 极低 CPU 消耗**。NextKV 坚如磐石的 C++ 底座、就地内存覆写机制以及硬件加速的 CRC16 校验，是纯 Java 实现的 FastKV 永远无法企及的高度。