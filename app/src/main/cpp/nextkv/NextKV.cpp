#include "NextKV.h"
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <cstring>
#include <android/log.h>

#if defined(__aarch64__) || defined(__arm__)
#include <arm_acle.h>
#endif

#define LOG_TAG "NextKV_Native"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

const size_t INITIAL_CAPACITY = 64 * 1024 * 1024; 
const uint32_t TOMBSTONE_MAGIC = 0xffffffff;
const uint32_t NKV_MAGIC = 0x4E4B5631;

static inline void cpu_relax() {
#if defined(__aarch64__) || defined(__arm__)
    __asm__ __volatile__("yield" ::: "memory");
#elif defined(__x86_64__) || defined(__i386__)
    __asm__ __volatile__("pause" ::: "memory");
#else
    sched_yield();
#endif
}

static inline uint32_t fast_hash(std::u16string_view key) {
    uint32_t hash = 0;
    const char16_t* data = key.data();
    size_t len = key.size();
    
#if defined(__aarch64__)
    while (len >= 4) {
        uint64_t chunk;
        memcpy(&chunk, data, 8);
        hash = __crc32cd(hash, chunk);
        data += 4;
        len -= 4;
    }
    while (len >= 2) {
        uint32_t chunk;
        memcpy(&chunk, data, 4);
        hash = __crc32cw(hash, chunk);
        data += 2;
        len -= 2;
    }
    while (len > 0) {
        hash = __crc32ch(hash, *data);
        data++;
        len--;
    }
#else
    hash = 2166136261u;
    for (size_t i = 0; i < len; ++i) {
        hash ^= data[i];
        hash *= 16777619;
    }
#endif
    return hash;
}

void NextKV::dictPut(std::u16string_view key, uint16_t id) {
    if (m_dictCount * 2 >= m_flatDict.size()) dictResize();
    size_t mask = m_flatDict.size() - 1;
    uint32_t hash = fast_hash(key);
    size_t idx = hash & mask;
    
    Slot currentSlot = {key, id, 0, true};
    
    while (true) {
        if (!m_flatDict[idx].occupied) {
            m_flatDict[idx] = currentSlot;
            m_dictCount++;
            return;
        }
        
        if (m_flatDict[idx].key == currentSlot.key) {
            m_flatDict[idx].id = currentSlot.id; // Update existing
            return;
        }
        
        if (m_flatDict[idx].psl < currentSlot.psl) {
            std::swap(m_flatDict[idx], currentSlot);
        }
        
        currentSlot.psl++;
        idx = (idx + 1) & mask;
    }
}

uint16_t NextKV::dictGet(std::u16string_view key) {
    if (m_dictCount == 0) return 0;
    size_t mask = m_flatDict.size() - 1;
    uint32_t hash = fast_hash(key);
    size_t idx = hash & mask;
    uint16_t dist = 0;
    
    while (m_flatDict[idx].occupied) {
        if (m_flatDict[idx].key == key) return m_flatDict[idx].id;
        if (dist > m_flatDict[idx].psl) return 0; // The item we're looking for would have been swapped here! Early exit!
        dist++;
        idx = (idx + 1) & mask;
    }
    return 0;
}

void NextKV::dictResize() {
    std::vector<Slot> old = std::move(m_flatDict);
    m_flatDict.assign(old.size() * 2, {std::u16string_view(), 0, 0, false});
    m_dictCount = 0;
    for (const auto& s : old) {
        if (s.occupied) dictPut(s.key, s.id);
    }
}

NextKV::NextKV(const std::string& path, bool multiProcess) : m_path(path), m_fd(-1), m_mmapPtr(nullptr), m_capacity(0), m_localOffset(sizeof(FileHeader)), m_localSequence(0), m_multiProcess(multiProcess), m_dictCount(0) {
    m_flatDict.assign(8192, {std::u16string_view(), 0, 0, false});
    m_fd = open(m_path.c_str(), O_RDWR | O_CREAT, 0644);
    if (m_fd < 0) {
        LOGE("Failed to open file: %s", m_path.c_str());
        return;
    }

    struct stat st;
    fstat(m_fd, &st);
    m_capacity = st.st_size;

    if (m_capacity == 0) {
        m_capacity = INITIAL_CAPACITY;
        ftruncate(m_fd, m_capacity);
    }

    m_mmapPtr = (uint8_t*)mmap(nullptr, m_capacity, PROT_READ | PROT_WRITE, MAP_SHARED, m_fd, 0);
    m_memTable.resize(65536); 
    recoverAll();
}

NextKV::~NextKV() {
    if (m_mmapPtr && m_mmapPtr != MAP_FAILED) munmap(m_mmapPtr, m_capacity);
    if (m_fd >= 0) close(m_fd);
}

template <bool MP>
inline void NextKV::lockWriteImpl() {
    while (m_threadLock.test_and_set(std::memory_order_acquire)) {
        cpu_relax();
    }
    if constexpr (MP) {
        if (m_mmapPtr) {
            FileHeader* header = reinterpret_cast<FileHeader*>(m_mmapPtr);
            while (__atomic_test_and_set(&header->processLock, __ATOMIC_ACQUIRE)) {
                cpu_relax();
            }
            recoverDelta();
        }
    }
}

template <bool MP>
inline void NextKV::unlockWriteImpl() {
    if constexpr (MP) {
        if (m_mmapPtr) {
            FileHeader* header = reinterpret_cast<FileHeader*>(m_mmapPtr);
            header->currentOffset = m_localOffset;
            header->sequence++;
            m_localSequence = header->sequence;
            __atomic_clear(&header->processLock, __ATOMIC_RELEASE);
        }
    }
    m_threadLock.clear(std::memory_order_release);
}

template <bool MP>
inline void NextKV::lockReadImpl() {
    while (m_threadLock.test_and_set(std::memory_order_acquire)) {
        cpu_relax();
    }
    if constexpr (MP) {
        if (m_mmapPtr) {
            FileHeader* header = reinterpret_cast<FileHeader*>(m_mmapPtr);
            if (header->sequence != m_localSequence) {
                while (__atomic_test_and_set(&header->processLock, __ATOMIC_ACQUIRE)) {
                    cpu_relax();
                }
                recoverDelta();
                m_localSequence = header->sequence;
                __atomic_clear(&header->processLock, __ATOMIC_RELEASE);
            }
        }
    }
}

template <bool MP>
inline void NextKV::unlockReadImpl() {
    m_threadLock.clear(std::memory_order_release);
}

void NextKV::recoverDelta() {
    if (!m_mmapPtr) return;
    FileHeader* header = reinterpret_cast<FileHeader*>(m_mmapPtr);
    if (header->magic != NKV_MAGIC) {
        header->magic = NKV_MAGIC;
        header->currentOffset = sizeof(FileHeader);
        header->sequence = 1;
        header->processLock = 0;
        m_localOffset = sizeof(FileHeader);
        m_localSequence = 1;
        return;
    }
    
    uint32_t targetOffset = header->currentOffset;
    if (targetOffset > m_capacity) {
        size_t oldCapacity = m_capacity;
        munmap(m_mmapPtr, m_capacity);
        struct stat st;
        fstat(m_fd, &st);
        m_capacity = st.st_size;
        m_mmapPtr = (uint8_t*)mmap(nullptr, m_capacity, PROT_READ | PROT_WRITE, MAP_SHARED, m_fd, 0);
        madvise(m_mmapPtr + oldCapacity, m_capacity - oldCapacity, MADV_WILLNEED);
        header = reinterpret_cast<FileHeader*>(m_mmapPtr);
    }
    
    if (targetOffset <= m_localOffset) return;
    
    size_t offset = m_localOffset;
    while (offset + 6 <= targetOffset && offset + 6 <= m_capacity) {
        uint16_t keyId;
        memcpy(&keyId, m_mmapPtr + offset, 2);
        if (keyId == 0) break;
        
        uint32_t size;
        memcpy(&size, m_mmapPtr + offset + 2, 4);
        
        bool isTombstone = (size & 0x80000000) != 0;
        uint32_t payloadSize = size & 0x7FFFFFFF;
        
        if (keyId >= m_memTable.size()) m_memTable.resize(keyId + 1024);
        
        if (isTombstone) {
            if (m_memTable[keyId].offset != 0 && !(m_memTable[keyId].size & 0x80000000)) {
                freeSpace(m_memTable[keyId].offset, m_memTable[keyId].size & 0x7FFFFFFF);
            }
            m_memTable[keyId] = { (uint32_t)(offset + 6), size };
            freeSpace(offset + 6, payloadSize);
        } else {
            if (m_memTable[keyId].offset != 0 && !(m_memTable[keyId].size & 0x80000000)) {
                freeSpace(m_memTable[keyId].offset, m_memTable[keyId].size & 0x7FFFFFFF);
            }
            m_memTable[keyId] = { (uint32_t)(offset + 6), payloadSize };
        }
        offset += 6 + payloadSize;
        if (keyId >= m_nextKeyId) m_nextKeyId = keyId + 1;
    }
    m_localOffset = offset;
    m_localSequence = header->sequence;
}

void NextKV::recoverAll() {
    m_localOffset = sizeof(FileHeader);
    recoverDelta();
}

uint16_t NextKV::getOrCreateKeyId(std::u16string_view key, bool& isNew) {
    uint16_t id = dictGet(key);
    if (id != 0) {
        isNew = false;
        return id;
    }
    isNew = true;
    uint16_t newId = m_nextKeyId++;
    m_keyStore.emplace_back(std::u16string(key));
    dictPut(m_keyStore.back(), newId);
    return newId;
}

void NextKV::ensureCapacity(size_t sizeNeeded) {
    if (m_localOffset + sizeNeeded <= m_capacity) return;
    size_t oldCapacity = m_capacity;
    size_t newCapacity = m_capacity * 2;
    while (m_localOffset + sizeNeeded > newCapacity) newCapacity *= 2;
    munmap(m_mmapPtr, m_capacity);
    ftruncate(m_fd, newCapacity);
    m_mmapPtr = (uint8_t*)mmap(nullptr, newCapacity, PROT_READ | PROT_WRITE, MAP_SHARED, m_fd, 0);
    m_capacity = newCapacity;
    madvise(m_mmapPtr + oldCapacity, newCapacity - oldCapacity, MADV_WILLNEED);
}

void NextKV::freeSpace(uint32_t offset, uint32_t size) {
    if (size > 0 && size != TOMBSTONE_MAGIC && size < 0x80000000) {
        m_freeBlocks[size].push_back(offset - 6);
    }
}

uint32_t NextKV::allocateSpace(uint32_t size) {
    uint32_t requiredTotal = 6 + size;
    auto it = m_freeBlocks.lower_bound(size);
    if (it != m_freeBlocks.end()) {
        std::vector<uint32_t>& offsets = it->second;
        if (!offsets.empty()) {
            uint32_t offset = offsets.back();
            offsets.pop_back();
            if (offsets.empty()) m_freeBlocks.erase(it);
            return offset;
        }
    }
    uint32_t offset = m_localOffset;
    ensureCapacity(requiredTotal);
    m_localOffset += requiredTotal;
    return offset;
}

uint64_t NextKV::getRecordMeta(std::u16string_view key) {
    lockReadImpl<false>();
    uint16_t id = dictGet(key);
    if (id == 0) { unlockReadImpl<false>(); return 0; }
    DataPointer dp = m_memTable[id];
    if (dp.offset == 0 || (dp.size & 0x80000000)) { unlockReadImpl<false>(); return 0; }
    uint64_t meta = ((uint64_t)dp.offset << 32) | (dp.size & 0x7FFFFFFF);
    unlockReadImpl<false>();
    return meta;
}


template <bool MP>
inline void NextKV::putStringCore(std::u16string_view key, std::u16string_view value) {
    lockWriteImpl<MP>();
    if (!m_mmapPtr) { unlockWriteImpl<MP>(); return; }

    bool isNew = false;
    uint16_t keyId = getOrCreateKeyId(key, isNew);
    uint32_t size = value.size() * 2; 
    
    if constexpr (!MP) {
        if (!isNew && keyId < m_memTable.size()) {
            DataPointer oldDp = m_memTable[keyId];
            uint32_t oldCap = oldDp.size & 0x7FFFFFFF;
            if (oldDp.offset != 0 && size == oldCap) {
                uint32_t newSize = size;
                memcpy(m_mmapPtr + oldDp.offset - 4, &newSize, 4);
                memcpy(m_mmapPtr + oldDp.offset, value.data(), size);
                m_memTable[keyId] = { oldDp.offset, size };
                unlockWriteImpl<MP>();
                return;
            } else if (oldDp.offset != 0 && !(oldDp.size & 0x80000000)) {
                freeSpace(oldDp.offset, oldCap);
            }
        }
    }
    
    uint32_t offset = allocateSpace(size);
    memcpy(m_mmapPtr + offset, &keyId, 2);
    memcpy(m_mmapPtr + offset + 2, &size, 4);
    memcpy(m_mmapPtr + offset + 6, value.data(), size);
    
    if (keyId >= m_memTable.size()) m_memTable.resize(keyId + 1024);
    m_memTable[keyId] = { offset + 6, size };
    unlockWriteImpl<MP>();
}

void NextKV::putString(std::u16string_view key, std::u16string_view value) {
    if (m_multiProcess) putStringCore<true>(key, value);
    else putStringCore<false>(key, value);
}

template <bool MP>
inline bool NextKV::getStringCore(std::u16string_view key, std::vector<char16_t>& outBuf) {
    lockReadImpl<MP>();
    uint16_t id = dictGet(key);
    if (id == 0) { unlockReadImpl<MP>(); return false; }

    DataPointer dp = m_memTable[id];
    if (dp.offset == 0 || (dp.size & 0x80000000)) { unlockReadImpl<MP>(); return false; }

    const char16_t* ptr = (const char16_t*)(m_mmapPtr + dp.offset);
    outBuf.assign(ptr, ptr + (dp.size / 2));
    unlockReadImpl<MP>();
    return true;
}
bool NextKV::getString(std::u16string_view key, std::vector<char16_t>& outBuf) {
    if (m_multiProcess) return getStringCore<true>(key, outBuf);
    return getStringCore<false>(key, outBuf);
}

template <bool MP>
inline void NextKV::putIntCore(std::u16string_view key, int32_t value) {
    putStringCore<MP>(key, std::u16string_view((char16_t*)&value, sizeof(int32_t) / 2));
}

void NextKV::putInt(std::u16string_view key, int32_t value) {
    if (m_multiProcess) putIntCore<true>(key, value);
    else putIntCore<false>(key, value);
}

template <bool MP>
inline int32_t NextKV::getIntCore(std::u16string_view key, int32_t defaultValue) {
    lockReadImpl<MP>();
    uint16_t id = dictGet(key);
    if (id == 0) { unlockReadImpl<MP>(); return defaultValue; }
    DataPointer dp = m_memTable[id];
    if (dp.size != sizeof(int32_t)) { unlockReadImpl<MP>(); return defaultValue; }
    int32_t val;
    memcpy(&val, m_mmapPtr + dp.offset, sizeof(int32_t));
    unlockReadImpl<MP>();
    return val;
}

int32_t NextKV::getInt(std::u16string_view key, int32_t defaultValue) {
    if (m_multiProcess) return getIntCore<true>(key, defaultValue);
    return getIntCore<false>(key, defaultValue);
}

template <bool MP>
inline void NextKV::putBoolCore(std::u16string_view key, bool value) {
    uint16_t val = value ? 1 : 0; 
    putStringCore<MP>(key, std::u16string_view((char16_t*)&val, 1));
}

void NextKV::putBool(std::u16string_view key, bool value) {
    if (m_multiProcess) putBoolCore<true>(key, value);
    else putBoolCore<false>(key, value);
}

template <bool MP>
inline bool NextKV::getBoolCore(std::u16string_view key, bool defaultValue) {
    lockReadImpl<MP>();
    uint16_t id = dictGet(key);
    if (id == 0) { unlockReadImpl<MP>(); return defaultValue; }
    DataPointer dp = m_memTable[id];
    if (dp.size != 2) { unlockReadImpl<MP>(); return defaultValue; } 
    uint16_t val;
    memcpy(&val, m_mmapPtr + dp.offset, 2);
    unlockReadImpl<MP>();
    return val != 0;
}

bool NextKV::getBool(std::u16string_view key, bool defaultValue) {
    if (m_multiProcess) return getBoolCore<true>(key, defaultValue);
    return getBoolCore<false>(key, defaultValue);
}

template <bool MP>
inline void NextKV::putFloatCore(std::u16string_view key, float value) {
    putStringCore<MP>(key, std::u16string_view((char16_t*)&value, sizeof(float) / 2));
}

void NextKV::putFloat(std::u16string_view key, float value) {
    if (m_multiProcess) putFloatCore<true>(key, value);
    else putFloatCore<false>(key, value);
}

template <bool MP>
inline float NextKV::getFloatCore(std::u16string_view key, float defaultValue) {
    lockReadImpl<MP>();
    uint16_t id = dictGet(key);
    if (id == 0) { unlockReadImpl<MP>(); return defaultValue; }
    DataPointer dp = m_memTable[id];
    if (dp.size != sizeof(float)) { unlockReadImpl<MP>(); return defaultValue; }
    float val;
    memcpy(&val, m_mmapPtr + dp.offset, sizeof(float));
    unlockReadImpl<MP>();
    return val;
}

float NextKV::getFloat(std::u16string_view key, float defaultValue) {
    if (m_multiProcess) return getFloatCore<true>(key, defaultValue);
    return getFloatCore<false>(key, defaultValue);
}

template <bool MP>
inline void NextKV::putLongCore(std::u16string_view key, int64_t value) {
    putStringCore<MP>(key, std::u16string_view((char16_t*)&value, sizeof(int64_t) / 2));
}

void NextKV::putLong(std::u16string_view key, int64_t value) {
    if (m_multiProcess) putLongCore<true>(key, value);
    else putLongCore<false>(key, value);
}

template <bool MP>
inline int64_t NextKV::getLongCore(std::u16string_view key, int64_t defaultValue) {
    lockReadImpl<MP>();
    uint16_t id = dictGet(key);
    if (id == 0) { unlockReadImpl<MP>(); return defaultValue; }
    DataPointer dp = m_memTable[id];
    if (dp.size != sizeof(int64_t)) { unlockReadImpl<MP>(); return defaultValue; }
    int64_t val;
    memcpy(&val, m_mmapPtr + dp.offset, sizeof(int64_t));
    unlockReadImpl<MP>();
    return val;
}

int64_t NextKV::getLong(std::u16string_view key, int64_t defaultValue) {
    if (m_multiProcess) return getLongCore<true>(key, defaultValue);
    return getLongCore<false>(key, defaultValue);
}

template <bool MP>
inline void NextKV::putDoubleCore(std::u16string_view key, double value) {
    putStringCore<MP>(key, std::u16string_view((char16_t*)&value, sizeof(double) / 2));
}

void NextKV::putDouble(std::u16string_view key, double value) {
    if (m_multiProcess) putDoubleCore<true>(key, value);
    else putDoubleCore<false>(key, value);
}

template <bool MP>
inline double NextKV::getDoubleCore(std::u16string_view key, double defaultValue) {
    lockReadImpl<MP>();
    uint16_t id = dictGet(key);
    if (id == 0) { unlockReadImpl<MP>(); return defaultValue; }
    DataPointer dp = m_memTable[id];
    if (dp.size != sizeof(double)) { unlockReadImpl<MP>(); return defaultValue; }
    double val;
    memcpy(&val, m_mmapPtr + dp.offset, sizeof(double));
    unlockReadImpl<MP>();
    return val;
}

double NextKV::getDouble(std::u16string_view key, double defaultValue) {
    if (m_multiProcess) return getDoubleCore<true>(key, defaultValue);
    return getDoubleCore<false>(key, defaultValue);
}

template <bool MP>
inline void NextKV::putByteArrayCore(std::u16string_view key, const uint8_t* value, size_t length) {
    lockWriteImpl<MP>();
    if (!m_mmapPtr) { unlockWriteImpl<MP>(); return; }
    
    bool isNew = false;
    uint16_t keyId = getOrCreateKeyId(key, isNew);
    uint32_t size = length;
    
    if constexpr (!MP) {
        if (!isNew && keyId < m_memTable.size()) {
            DataPointer oldDp = m_memTable[keyId];
            uint32_t oldCap = oldDp.size & 0x7FFFFFFF;
            if (oldDp.offset != 0 && size == oldCap) {
                uint32_t newSize = size;
                memcpy(m_mmapPtr + oldDp.offset - 4, &newSize, 4);
                if (value && size > 0) {
                    memcpy(m_mmapPtr + oldDp.offset, value, size);
                }
                m_memTable[keyId] = { oldDp.offset, size };
                unlockWriteImpl<MP>();
                return;
            } else if (oldDp.offset != 0 && !(oldDp.size & 0x80000000)) {
                freeSpace(oldDp.offset, oldCap);
            }
        }
    }

    uint32_t offset = allocateSpace(size);
    memcpy(m_mmapPtr + offset, &keyId, 2);
    memcpy(m_mmapPtr + offset + 2, &size, 4);
    if (value && length > 0) {
        memcpy(m_mmapPtr + offset + 6, value, size);
    }
    if (keyId >= m_memTable.size()) m_memTable.resize(keyId + 1024);
    m_memTable[keyId] = { offset + 6, size };
    unlockWriteImpl<MP>();
}

void NextKV::putByteArray(std::u16string_view key, const uint8_t* value, size_t length) {
    if (m_multiProcess) putByteArrayCore<true>(key, value, length);
    else putByteArrayCore<false>(key, value, length);
}

template <bool MP>
inline bool NextKV::getByteArrayCore(std::u16string_view key, std::vector<uint8_t>& outBuf) {
    lockReadImpl<MP>();
    uint16_t id = dictGet(key);
    if (id == 0) { unlockReadImpl<MP>(); return false; }
    DataPointer dp = m_memTable[id];
    if (dp.offset == 0 || (dp.size & 0x80000000)) { unlockReadImpl<MP>(); return false; }
    
    const uint8_t* ptr = m_mmapPtr + dp.offset;
    outBuf.assign(ptr, ptr + dp.size);
    unlockReadImpl<MP>();
    return true;
}

bool NextKV::getByteArray(std::u16string_view key, std::vector<uint8_t>& outBuf) {
    if (m_multiProcess) return getByteArrayCore<true>(key, outBuf);
    return getByteArrayCore<false>(key, outBuf);
}

template <bool MP>
inline bool NextKV::containsCore(std::u16string_view key) {
    lockReadImpl<MP>();
    uint16_t id = dictGet(key);
    if (id == 0) { unlockReadImpl<MP>(); return false; }
    DataPointer dp = m_memTable[id];
    bool exists = (dp.offset != 0 && !(dp.size & 0x80000000));
    unlockReadImpl<MP>();
    return exists;
}

bool NextKV::contains(std::u16string_view key) {
    if (m_multiProcess) return containsCore<true>(key);
    return containsCore<false>(key);
}

template <bool MP>
inline void NextKV::removeCore(std::u16string_view key) {
    lockWriteImpl<MP>();
    if (!m_mmapPtr) { unlockWriteImpl<MP>(); return; }
    uint16_t keyId = dictGet(key);
    if (keyId == 0) { unlockWriteImpl<MP>(); return; }
    
    if constexpr (!MP) {
        DataPointer dp = m_memTable[keyId];
        if (dp.offset != 0 && !(dp.size & 0x80000000)) {
            uint32_t newSize = dp.size | 0x80000000;
            memcpy(m_mmapPtr + dp.offset - 4, &newSize, 4);
            m_memTable[keyId] = { dp.offset, newSize };
            freeSpace(dp.offset, dp.size & 0x7FFFFFFF); // Free the payload space
            unlockWriteImpl<MP>();
            return;
        }
    }
    
    uint32_t size = 0x80000000;
    uint32_t offset = allocateSpace(0);
    memcpy(m_mmapPtr + offset, &keyId, 2);
    memcpy(m_mmapPtr + offset + 2, &size, 4);
    
    m_memTable[keyId] = { 0, size };
    unlockWriteImpl<MP>();
}

void NextKV::remove(std::u16string_view key) {
    if (m_multiProcess) removeCore<true>(key);
    else removeCore<false>(key);
}

template <bool MP>
inline void NextKV::clearAllCore() {
    lockWriteImpl<MP>();
    m_localOffset = sizeof(FileHeader);
    m_nextKeyId = 1;
    m_flatDict.assign(8192, {std::u16string_view(), 0, false});
    m_dictCount = 0;
    m_keyStore.clear();
    std::fill(m_memTable.begin(), m_memTable.end(), DataPointer{0, 0});
    unlockWriteImpl<MP>();
}

void NextKV::clearAll() {
    if (m_multiProcess) clearAllCore<true>();
    else clearAllCore<false>();
}