#include "NextKV.h"
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <cstring>
#include <android/log.h>

#define LOG_TAG "NextKV_Native"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

const size_t INITIAL_CAPACITY = 64 * 1024 * 1024; // 64MB for UTF-16 benchmarks
const uint32_t TOMBSTONE_MAGIC = 0xffffffff;

NextKV::NextKV(const std::string& path) : m_path(path), m_fd(-1), m_mmapPtr(nullptr), m_capacity(0), m_currentOffset(0) {
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
    recover();
}

NextKV::~NextKV() {
    if (m_mmapPtr && m_mmapPtr != MAP_FAILED) munmap(m_mmapPtr, m_capacity);
    if (m_fd >= 0) close(m_fd);
}

void NextKV::recover() {
    size_t offset = 0;
    while (offset + 6 <= m_capacity) {
        uint16_t keyId;
        memcpy(&keyId, m_mmapPtr + offset, 2);
        if (keyId == 0) break;
        
        uint32_t size;
        memcpy(&size, m_mmapPtr + offset + 2, 4);
        
        if (size == TOMBSTONE_MAGIC) {
            if (keyId >= m_memTable.size()) m_memTable.resize(keyId + 1024);
            m_memTable[keyId] = { 0, TOMBSTONE_MAGIC };
            offset += 6;
        } else {
            if (offset + 6 + size > m_capacity) break; 
            if (keyId >= m_memTable.size()) m_memTable.resize(keyId + 1024);
            m_memTable[keyId] = { (uint32_t)(offset + 6), size };
            offset += 6 + size;
        }
        if (keyId >= m_nextKeyId) m_nextKeyId = keyId + 1;
    }
    m_currentOffset = offset;
}

uint16_t NextKV::getOrCreateKeyId(std::string_view key) {
    auto it = m_keyDict.find(key);
    if (it != m_keyDict.end()) return it->second;
    uint16_t newId = m_nextKeyId++;
    m_keyStore.emplace_back(std::string(key));
    m_keyDict[m_keyStore.back()] = newId; 
    return newId;
}

void NextKV::ensureCapacity(size_t sizeNeeded) {
    if (m_currentOffset + sizeNeeded <= m_capacity) return;
    size_t newCapacity = m_capacity * 2;
    while (m_currentOffset + sizeNeeded > newCapacity) newCapacity *= 2;
    munmap(m_mmapPtr, m_capacity);
    ftruncate(m_fd, newCapacity);
    m_mmapPtr = (uint8_t*)mmap(nullptr, newCapacity, PROT_READ | PROT_WRITE, MAP_SHARED, m_fd, 0);
    m_capacity = newCapacity;
}

void NextKV::putString(std::string_view key, std::u16string_view value) {
    lock();
    if (!m_mmapPtr) { unlock(); return; }

    uint16_t keyId = getOrCreateKeyId(key);
    uint32_t size = value.size() * 2; // bytes
    
    size_t recordSize = 6 + size;
    ensureCapacity(recordSize);
    
    memcpy(m_mmapPtr + m_currentOffset, &keyId, 2);
    memcpy(m_mmapPtr + m_currentOffset + 2, &size, 4);
    memcpy(m_mmapPtr + m_currentOffset + 6, value.data(), size);
    
    if (keyId >= m_memTable.size()) m_memTable.resize(keyId + 1024);
    m_memTable[keyId] = { (uint32_t)(m_currentOffset + 6), size };
    m_currentOffset += recordSize;
    unlock();
}

std::u16string_view NextKV::getStringView(std::string_view key) {
    lock();
    auto it = m_keyDict.find(key);
    if (it == m_keyDict.end()) { unlock(); return std::u16string_view(nullptr, 0); }
    
    DataPointer dp = m_memTable[it->second];
    if (dp.size == 0 || dp.size == TOMBSTONE_MAGIC) { unlock(); return std::u16string_view(nullptr, 0); }
    
    std::u16string_view result((char16_t*)(m_mmapPtr + dp.offset), dp.size / 2);
    unlock();
    return result;
}

void NextKV::putInt(std::string_view key, int32_t value) {
    lock();
    if (!m_mmapPtr) { unlock(); return; }
    uint16_t keyId = getOrCreateKeyId(key);
    uint32_t size = sizeof(int32_t);
    size_t recordSize = 6 + size;
    ensureCapacity(recordSize);
    memcpy(m_mmapPtr + m_currentOffset, &keyId, 2);
    memcpy(m_mmapPtr + m_currentOffset + 2, &size, 4);
    memcpy(m_mmapPtr + m_currentOffset + 6, &value, size);
    if (keyId >= m_memTable.size()) m_memTable.resize(keyId + 1024);
    m_memTable[keyId] = { (uint32_t)(m_currentOffset + 6), size };
    m_currentOffset += recordSize;
    unlock();
}

int32_t NextKV::getInt(std::string_view key, int32_t defaultValue) {
    lock();
    auto it = m_keyDict.find(key);
    if (it == m_keyDict.end()) { unlock(); return defaultValue; }
    DataPointer dp = m_memTable[it->second];
    if (dp.size != sizeof(int32_t)) { unlock(); return defaultValue; }
    int32_t val;
    memcpy(&val, m_mmapPtr + dp.offset, sizeof(int32_t));
    unlock();
    return val;
}

void NextKV::putBool(std::string_view key, bool value) {
    lock();
    if (!m_mmapPtr) { unlock(); return; }
    uint16_t keyId = getOrCreateKeyId(key);
    uint32_t size = 1;
    uint8_t val = value ? 1 : 0;
    size_t recordSize = 6 + size;
    ensureCapacity(recordSize);
    memcpy(m_mmapPtr + m_currentOffset, &keyId, 2);
    memcpy(m_mmapPtr + m_currentOffset + 2, &size, 4);
    memcpy(m_mmapPtr + m_currentOffset + 6, &val, size);
    if (keyId >= m_memTable.size()) m_memTable.resize(keyId + 1024);
    m_memTable[keyId] = { (uint32_t)(m_currentOffset + 6), size };
    m_currentOffset += recordSize;
    unlock();
}

bool NextKV::getBool(std::string_view key, bool defaultValue) {
    lock();
    auto it = m_keyDict.find(key);
    if (it == m_keyDict.end()) { unlock(); return defaultValue; }
    DataPointer dp = m_memTable[it->second];
    if (dp.size != 1) { unlock(); return defaultValue; }
    bool val = *(m_mmapPtr + dp.offset) != 0;
    unlock();
    return val;
}

void NextKV::putFloat(std::string_view key, float value) {
    lock();
    if (!m_mmapPtr) { unlock(); return; }
    uint16_t keyId = getOrCreateKeyId(key);
    uint32_t size = sizeof(float);
    size_t recordSize = 6 + size;
    ensureCapacity(recordSize);
    memcpy(m_mmapPtr + m_currentOffset, &keyId, 2);
    memcpy(m_mmapPtr + m_currentOffset + 2, &size, 4);
    memcpy(m_mmapPtr + m_currentOffset + 6, &value, size);
    if (keyId >= m_memTable.size()) m_memTable.resize(keyId + 1024);
    m_memTable[keyId] = { (uint32_t)(m_currentOffset + 6), size };
    m_currentOffset += recordSize;
    unlock();
}

float NextKV::getFloat(std::string_view key, float defaultValue) {
    lock();
    auto it = m_keyDict.find(key);
    if (it == m_keyDict.end()) { unlock(); return defaultValue; }
    DataPointer dp = m_memTable[it->second];
    if (dp.size != sizeof(float)) { unlock(); return defaultValue; }
    float val;
    memcpy(&val, m_mmapPtr + dp.offset, sizeof(float));
    unlock();
    return val;
}

void NextKV::putLong(std::string_view key, int64_t value) {
    lock();
    if (!m_mmapPtr) { unlock(); return; }
    uint16_t keyId = getOrCreateKeyId(key);
    uint32_t size = sizeof(int64_t);
    size_t recordSize = 6 + size;
    ensureCapacity(recordSize);
    memcpy(m_mmapPtr + m_currentOffset, &keyId, 2);
    memcpy(m_mmapPtr + m_currentOffset + 2, &size, 4);
    memcpy(m_mmapPtr + m_currentOffset + 6, &value, size);
    if (keyId >= m_memTable.size()) m_memTable.resize(keyId + 1024);
    m_memTable[keyId] = { (uint32_t)(m_currentOffset + 6), size };
    m_currentOffset += recordSize;
    unlock();
}

int64_t NextKV::getLong(std::string_view key, int64_t defaultValue) {
    lock();
    auto it = m_keyDict.find(key);
    if (it == m_keyDict.end()) { unlock(); return defaultValue; }
    DataPointer dp = m_memTable[it->second];
    if (dp.size != sizeof(int64_t)) { unlock(); return defaultValue; }
    int64_t val;
    memcpy(&val, m_mmapPtr + dp.offset, sizeof(int64_t));
    unlock();
    return val;
}

void NextKV::putDouble(std::string_view key, double value) {
    lock();
    if (!m_mmapPtr) { unlock(); return; }
    uint16_t keyId = getOrCreateKeyId(key);
    uint32_t size = sizeof(double);
    size_t recordSize = 6 + size;
    ensureCapacity(recordSize);
    memcpy(m_mmapPtr + m_currentOffset, &keyId, 2);
    memcpy(m_mmapPtr + m_currentOffset + 2, &size, 4);
    memcpy(m_mmapPtr + m_currentOffset + 6, &value, size);
    if (keyId >= m_memTable.size()) m_memTable.resize(keyId + 1024);
    m_memTable[keyId] = { (uint32_t)(m_currentOffset + 6), size };
    m_currentOffset += recordSize;
    unlock();
}

double NextKV::getDouble(std::string_view key, double defaultValue) {
    lock();
    auto it = m_keyDict.find(key);
    if (it == m_keyDict.end()) { unlock(); return defaultValue; }
    DataPointer dp = m_memTable[it->second];
    if (dp.size != sizeof(double)) { unlock(); return defaultValue; }
    double val;
    memcpy(&val, m_mmapPtr + dp.offset, sizeof(double));
    unlock();
    return val;
}

void NextKV::putByteArray(std::string_view key, const uint8_t* value, size_t length) {
    lock();
    if (!m_mmapPtr) { unlock(); return; }
    uint16_t keyId = getOrCreateKeyId(key);
    uint32_t size = length;
    size_t recordSize = 6 + size;
    ensureCapacity(recordSize);
    memcpy(m_mmapPtr + m_currentOffset, &keyId, 2);
    memcpy(m_mmapPtr + m_currentOffset + 2, &size, 4);
    if (value && length > 0) {
        memcpy(m_mmapPtr + m_currentOffset + 6, value, size);
    }
    if (keyId >= m_memTable.size()) m_memTable.resize(keyId + 1024);
    m_memTable[keyId] = { (uint32_t)(m_currentOffset + 6), size };
    m_currentOffset += recordSize;
    unlock();
}

NextKV::ByteArrayView NextKV::getByteArray(std::string_view key) {
    lock();
    auto it = m_keyDict.find(key);
    if (it == m_keyDict.end()) { unlock(); return {nullptr, 0}; }
    DataPointer dp = m_memTable[it->second];
    if (dp.size == 0 || dp.size == TOMBSTONE_MAGIC) { unlock(); return {nullptr, 0}; }
    ByteArrayView result = { m_mmapPtr + dp.offset, dp.size };
    unlock();
    return result;
}

bool NextKV::contains(std::string_view key) {
    lock();
    auto it = m_keyDict.find(key);
    if (it == m_keyDict.end()) { unlock(); return false; }
    DataPointer dp = m_memTable[it->second];
    bool exists = (dp.size != TOMBSTONE_MAGIC && dp.offset != 0);
    unlock();
    return exists;
}

void NextKV::remove(std::string_view key) {
    lock();
    if (!m_mmapPtr) { unlock(); return; }
    auto it = m_keyDict.find(key);
    if (it == m_keyDict.end()) { unlock(); return; }
    uint16_t keyId = it->second;
    
    uint32_t size = TOMBSTONE_MAGIC;
    size_t recordSize = 6;
    ensureCapacity(recordSize);
    memcpy(m_mmapPtr + m_currentOffset, &keyId, 2);
    memcpy(m_mmapPtr + m_currentOffset + 2, &size, 4);
    
    m_memTable[keyId] = { 0, size };
    m_currentOffset += recordSize;
    unlock();
}

void NextKV::clearAll() {
    lock();
    m_currentOffset = 0;
    m_nextKeyId = 1;
    m_keyDict.clear();
    m_keyStore.clear();
    std::fill(m_memTable.begin(), m_memTable.end(), DataPointer{0, 0});
    unlock();
}