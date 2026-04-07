#ifndef NEXTKV_C_API_H
#define NEXTKV_C_API_H

#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef void* NextKVHandle;

NextKVHandle nkv_open(const char* path, bool multiProcess);
void nkv_close(NextKVHandle handle);

void nkv_put_int(NextKVHandle handle, const char* key, size_t key_len, int32_t value);
int32_t nkv_get_int(NextKVHandle handle, const char* key, size_t key_len, int32_t default_value);

void nkv_put_string(NextKVHandle handle, const char* key, size_t key_len, const char* value, size_t val_len);
char* nkv_get_string(NextKVHandle handle, const char* key, size_t key_len, size_t* out_len);

void nkv_free_string(char* str);

void nkv_remove(NextKVHandle handle, const char* key, size_t key_len);
bool nkv_contains(NextKVHandle handle, const char* key, size_t key_len);

#ifdef __cplusplus
}
#endif

#endif