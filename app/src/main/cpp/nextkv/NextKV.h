#ifndef NEXTKV_H
#define NEXTKV_H

#include <string>
#include <string_view>
#include <list>
#include <vector>
#include <atomic>
#include <cstdint>

#include <map>

class NextKV {
public:
    NextKV(const std::string& path, bool multiProcess);
    ~NextKV();

    void putString(std::u16string_view key, std::u16string_view value);
    bool getString(std::u16string_view key, std::vector<char16_t>& outBuf);

    void putInt(std::u16string_view key, int32_t value);
    int32_t getInt(std::u16string_view key, int32_t defaultValue = 0);

    void putBool(std::u16string_view key, bool value);
    bool getBool(std::u16string_view key, bool defaultValue = false);

    void putFloat(std::u16string_view key, float value);
    float getFloat(std::u16string_view key, float defaultValue = 0.0f);

    void putLong(std::u16string_view key, int64_t value);
    int64_t getLong(std::u16string_view key, int64_t defaultValue = 0);

    void putDouble(std::u16string_view key, double value);
    double getDouble(std::u16string_view key, double defaultValue = 0.0);

    void putByteArray(std::u16string_view key, const uint8_t* value, size_t length);
    bool getByteArray(std::u16string_view key, std::vector<uint8_t>& outBuf);

    bool contains(std::u16string_view key);
    void remove(std::u16string_view key);
    void clearAll();

    // Export memory map for Java DirectByteBuffer (Cross-platform boundary)
    uint8_t* getMmapPtr() const { return m_mmapPtr; }
    size_t getCapacity() const { return m_capacity; }
    uint64_t getRecordMeta(std::u16string_view key); // Returns (offset << 32) | size

private:
    struct DataPointer {
        uint32_t offset;
        uint32_t size;
    };
    
    struct FileHeader {
        uint32_t magic;
        uint32_t currentOffset;
        uint32_t sequence;
        uint32_t processLock;
    };

    std::string m_path;
    int m_fd;
    uint8_t* m_mmapPtr;
    size_t m_capacity;
    uint32_t m_localOffset;
    uint32_t m_localSequence;
    bool m_multiProcess;
    
    std::list<std::u16string> m_keyStore;
    
    struct Slot {
        std::u16string_view key;
        uint16_t id;
        uint16_t psl;
        bool occupied;
    };
    std::vector<Slot> m_flatDict;
    size_t m_dictCount;
    
    uint16_t m_nextKeyId = 1;
    std::vector<DataPointer> m_memTable;
    
    // Free list for memory reuse (Size -> List of Offsets)
    std::map<uint32_t, std::vector<uint32_t>> m_freeBlocks;
    
    std::atomic_flag m_threadLock = ATOMIC_FLAG_INIT;

    template <bool MP> inline void lockReadImpl();
    template <bool MP> inline void unlockReadImpl();
    template <bool MP> inline void lockWriteImpl();
    template <bool MP> inline void unlockWriteImpl();

    void dictPut(std::u16string_view key, uint16_t id);
    uint16_t dictGet(std::u16string_view key);
    void dictResize();

    uint16_t getOrCreateKeyId(std::u16string_view key, bool& isNew);
    void ensureCapacity(size_t sizeNeeded);
    void recoverDelta();
    void recoverAll();
    
    uint32_t allocateSpace(uint32_t size);
    void freeSpace(uint32_t offset, uint32_t size);

    template <bool MP> inline void putStringCore(std::u16string_view key, std::u16string_view value);
    template <bool MP> inline bool getStringCore(std::u16string_view key, std::vector<char16_t>& outBuf);
    template <bool MP> inline void putIntCore(std::u16string_view key, int32_t value);
    template <bool MP> inline int32_t getIntCore(std::u16string_view key, int32_t defaultValue);
    template <bool MP> inline void putBoolCore(std::u16string_view key, bool value);
    template <bool MP> inline bool getBoolCore(std::u16string_view key, bool defaultValue);
    template <bool MP> inline void putFloatCore(std::u16string_view key, float value);
    template <bool MP> inline float getFloatCore(std::u16string_view key, float defaultValue);
    template <bool MP> inline void putLongCore(std::u16string_view key, int64_t value);
    template <bool MP> inline int64_t getLongCore(std::u16string_view key, int64_t defaultValue);
    template <bool MP> inline void putDoubleCore(std::u16string_view key, double value);
    template <bool MP> inline double getDoubleCore(std::u16string_view key, double defaultValue);
    template <bool MP> inline void putByteArrayCore(std::u16string_view key, const uint8_t* value, size_t length);
    template <bool MP> inline bool getByteArrayCore(std::u16string_view key, std::vector<uint8_t>& outBuf);
    template <bool MP> inline bool containsCore(std::u16string_view key);
    template <bool MP> inline void removeCore(std::u16string_view key);
    template <bool MP> inline void clearAllCore();
};

#endif // NEXTKV_H