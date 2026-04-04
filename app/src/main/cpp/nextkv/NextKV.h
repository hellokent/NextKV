#ifndef NEXTKV_H
#define NEXTKV_H

#include <string>
#include <string_view>
#include <unordered_map>
#include <list>
#include <vector>
#include <atomic>
#include <cstdint>

class NextKV {
public:
    NextKV(const std::string& path);
    ~NextKV();

    void putString(std::string_view key, std::u16string_view value);
    std::u16string_view getStringView(std::string_view key);

    void putInt(std::string_view key, int32_t value);
    int32_t getInt(std::string_view key, int32_t defaultValue = 0);

    void putBool(std::string_view key, bool value);
    bool getBool(std::string_view key, bool defaultValue = false);

    void putFloat(std::string_view key, float value);
    float getFloat(std::string_view key, float defaultValue = 0.0f);

    void putLong(std::string_view key, int64_t value);
    int64_t getLong(std::string_view key, int64_t defaultValue = 0);

    void putDouble(std::string_view key, double value);
    double getDouble(std::string_view key, double defaultValue = 0.0);

    void putByteArray(std::string_view key, const uint8_t* value, size_t length);
    struct ByteArrayView {
        const uint8_t* data;
        size_t size;
    };
    ByteArrayView getByteArray(std::string_view key);

    bool contains(std::string_view key);
    void remove(std::string_view key);
    void clearAll();

private:
    struct DataPointer {
        uint32_t offset;
        uint32_t size;
    };

    std::string m_path;
    int m_fd;
    uint8_t* m_mmapPtr;
    size_t m_capacity;
    size_t m_currentOffset;
    
    std::list<std::string> m_keyStore;
    std::unordered_map<std::string_view, uint16_t> m_keyDict;
    uint16_t m_nextKeyId = 1;
    
    std::vector<DataPointer> m_memTable;

    std::atomic_flag m_lock = ATOMIC_FLAG_INIT;
    inline void lock() {
        while (m_lock.test_and_set(std::memory_order_acquire)) {}
    }
    inline void unlock() {
        m_lock.clear(std::memory_order_release);
    }

    uint16_t getOrCreateKeyId(std::string_view key);
    void ensureCapacity(size_t sizeNeeded);
    void recover();
};

#endif // NEXTKV_H