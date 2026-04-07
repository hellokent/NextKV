package main

/*
#cgo CXXFLAGS: -std=c++17 -O3
#cgo LDFLAGS: -L${SRCDIR}/lib -lnextkv -lc++
#include "nextkv_c_api.h"
#include <stdlib.h>
*/
import "C"
import (
	"unsafe"
)

type NextKV struct {
	handle C.NextKVHandle
}

func NewNextKV(path string, multiProcess bool) *NextKV {
	cpath := C.CString(path)
	defer C.free(unsafe.Pointer(cpath))
	mp := C.bool(multiProcess)
	handle := C.nkv_open(cpath, mp)
	return &NextKV{handle: handle}
}

func (n *NextKV) Close() {
	C.nkv_close(n.handle)
}

// stringToPointer returns the underlying pointer of a string without allocation.
// Warning: valid only for the duration of the C call, and does not add a null terminator.
func stringToPointer(s string) *C.char {
	if len(s) == 0 {
		return nil
	}
	return (*C.char)(unsafe.Pointer(unsafe.StringData(s)))
}

func (n *NextKV) PutInt(key string, val int32) {
	C.nkv_put_int(n.handle, stringToPointer(key), C.size_t(len(key)), C.int32_t(val))
}

func (n *NextKV) GetInt(key string, defaultVal int32) int32 {
	return int32(C.nkv_get_int(n.handle, stringToPointer(key), C.size_t(len(key)), C.int32_t(defaultVal)))
}

func (n *NextKV) PutString(key string, val string) {
	C.nkv_put_string(n.handle, stringToPointer(key), C.size_t(len(key)), stringToPointer(val), C.size_t(len(val)))
}

func (n *NextKV) GetString(key string) string {
	var outLen C.size_t
	cval := C.nkv_get_string(n.handle, stringToPointer(key), C.size_t(len(key)), &outLen)
	if cval == nil {
		return ""
	}
	defer C.nkv_free_string(cval)
	return C.GoStringN(cval, C.int(outLen))
}

func (n *NextKV) Remove(key string) {
	C.nkv_remove(n.handle, stringToPointer(key), C.size_t(len(key)))
}

func (n *NextKV) Contains(key string) bool {
	return bool(C.nkv_contains(n.handle, stringToPointer(key), C.size_t(len(key))))
}