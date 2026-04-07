#include "nextkv_c_api.h"
#include "../app/src/main/cpp/nextkv/NextKV.h"
#include <cstdlib>
#include <cstring>
#include <vector>

thread_local std::u16string tls_key_buf;

static std::u16string_view make_u16_key(const char* key, size_t key_len) {
    tls_key_buf.clear();
    for (size_t i = 0; i < key_len; i++) {
        tls_key_buf.push_back(key[i]);
    }
    return std::u16string_view(tls_key_buf.data(), tls_key_buf.size());
}

extern "C" {
    NextKVHandle nkv_open(const char* path, bool multiProcess) {
        return new NextKV(path, multiProcess);
    }
    
    void nkv_close(NextKVHandle handle) {
        delete static_cast<NextKV*>(handle);
    }
    
    void nkv_put_int(NextKVHandle handle, const char* key, size_t key_len, int32_t value) {
        static_cast<NextKV*>(handle)->putInt(make_u16_key(key, key_len), value);
    }
    
    int32_t nkv_get_int(NextKVHandle handle, const char* key, size_t key_len, int32_t default_value) {
        return static_cast<NextKV*>(handle)->getInt(make_u16_key(key, key_len), default_value);
    }
    
    void nkv_put_string(NextKVHandle handle, const char* key, size_t key_len, const char* value, size_t val_len) {
        static_cast<NextKV*>(handle)->putByteArray(make_u16_key(key, key_len), (const uint8_t*)value, val_len);
    }
    
    char* nkv_get_string(NextKVHandle handle, const char* key, size_t key_len, size_t* out_len) {
        thread_local std::vector<uint8_t> tls_val_buf;
        if (static_cast<NextKV*>(handle)->getByteArray(make_u16_key(key, key_len), tls_val_buf)) {
            if (out_len) *out_len = tls_val_buf.size();
            char* res = (char*)malloc(tls_val_buf.size());
            memcpy(res, tls_val_buf.data(), tls_val_buf.size());
            return res;
        }
        if (out_len) *out_len = 0;
        return nullptr;
    }
    
    void nkv_free_string(char* str) {
        free(str);
    }
    
    void nkv_remove(NextKVHandle handle, const char* key, size_t key_len) {
        static_cast<NextKV*>(handle)->remove(make_u16_key(key, key_len));
    }
    
    bool nkv_contains(NextKVHandle handle, const char* key, size_t key_len) {
        return static_cast<NextKV*>(handle)->contains(make_u16_key(key, key_len));
    }
}