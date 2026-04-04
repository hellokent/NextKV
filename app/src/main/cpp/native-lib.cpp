#include <jni.h>
#include <string>
#include <string_view>
#include <vector>
#include "nextkv/NextKV.h"

static NextKV* g_nextKV = nullptr;
thread_local std::vector<char16_t> tls_string_buf;
thread_local std::vector<uint8_t> tls_byte_buf;

struct JStringU16View {
    JNIEnv* env;
    jstring jstr;
    const jchar* chars;
    size_t len;
    
    JStringU16View(JNIEnv* e, jstring j) : env(e), jstr(j) {
        if (jstr) {
            len = env->GetStringLength(jstr);
            chars = env->GetStringCritical(jstr, nullptr);
        } else {
            chars = nullptr;
            len = 0;
        }
    }
    
    ~JStringU16View() {
        if (chars) {
            env->ReleaseStringCritical(jstr, chars);
        }
    }
    
    std::u16string_view view() const {
        return chars ? std::u16string_view(reinterpret_cast<const char16_t*>(chars), len) : std::u16string_view();
    }
};

extern "C" JNIEXPORT void JNICALL
Java_com_example_nextkv_NextKV_init(JNIEnv* env, jclass clazz, jstring path, jboolean multiProcess) {
    if (g_nextKV != nullptr) {
        delete g_nextKV;
    }
    const char* pathStr = env->GetStringUTFChars(path, nullptr);
    g_nextKV = new NextKV(std::string(pathStr), multiProcess);
    env->ReleaseStringUTFChars(path, pathStr);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_nextkv_NextKV_nativePutString(JNIEnv* env, jobject thiz, jstring key, jstring value) {
    if (!g_nextKV) return;
    if (!value) {
        JStringU16View k(env, key);
        g_nextKV->remove(k.view());
        return;
    }
    
    jsize len = env->GetStringLength(value);
    const jchar* chars = env->GetStringCritical(value, nullptr);
    if (chars) {
        JStringU16View k(env, key);
        g_nextKV->putString(k.view(), std::u16string_view(reinterpret_cast<const char16_t*>(chars), len));
        env->ReleaseStringCritical(value, chars);
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_nextkv_NextKV_nativeGetString(JNIEnv* env, jobject thiz, jstring key, jstring default_value) {
    if (!g_nextKV) return default_value;
    bool found;
    {
        JStringU16View k(env, key);
        found = g_nextKV->getString(k.view(), tls_string_buf);
    }
    if (!found) {
        return default_value;
    }
    return env->NewString(reinterpret_cast<const jchar*>(tls_string_buf.data()), tls_string_buf.size());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_nextkv_NextKV_nativePutInt(JNIEnv* env, jobject thiz, jstring key, jint value) {
    if (!g_nextKV) return;
    JStringU16View k(env, key);
    g_nextKV->putInt(k.view(), value);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_nextkv_NextKV_nativeGetInt(JNIEnv* env, jobject thiz, jstring key, jint default_value) {
    if (!g_nextKV) return default_value;
    JStringU16View k(env, key);
    return g_nextKV->getInt(k.view(), default_value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_nextkv_NextKV_nativePutBoolean(JNIEnv* env, jobject thiz, jstring key, jboolean value) {
    if (!g_nextKV) return;
    JStringU16View k(env, key);
    g_nextKV->putBool(k.view(), value);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_nextkv_NextKV_nativeGetBoolean(JNIEnv* env, jobject thiz, jstring key, jboolean default_value) {
    if (!g_nextKV) return default_value;
    JStringU16View k(env, key);
    return g_nextKV->getBool(k.view(), default_value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_nextkv_NextKV_nativePutFloat(JNIEnv* env, jobject thiz, jstring key, jfloat value) {
    if (!g_nextKV) return;
    JStringU16View k(env, key);
    g_nextKV->putFloat(k.view(), value);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_example_nextkv_NextKV_nativeGetFloat(JNIEnv* env, jobject thiz, jstring key, jfloat default_value) {
    if (!g_nextKV) return default_value;
    JStringU16View k(env, key);
    return g_nextKV->getFloat(k.view(), default_value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_nextkv_NextKV_nativePutLong(JNIEnv* env, jobject thiz, jstring key, jlong value) {
    if (!g_nextKV) return;
    JStringU16View k(env, key);
    g_nextKV->putLong(k.view(), value);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_nextkv_NextKV_nativeGetLong(JNIEnv* env, jobject thiz, jstring key, jlong default_value) {
    if (!g_nextKV) return default_value;
    JStringU16View k(env, key);
    return g_nextKV->getLong(k.view(), default_value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_nextkv_NextKV_nativePutDouble(JNIEnv* env, jobject thiz, jstring key, jdouble value) {
    if (!g_nextKV) return;
    JStringU16View k(env, key);
    g_nextKV->putDouble(k.view(), value);
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_example_nextkv_NextKV_nativeGetDouble(JNIEnv* env, jobject thiz, jstring key, jdouble default_value) {
    if (!g_nextKV) return default_value;
    JStringU16View k(env, key);
    return g_nextKV->getDouble(k.view(), default_value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_nextkv_NextKV_nativePutByteArray(JNIEnv* env, jobject thiz, jstring key, jbyteArray value) {
    if (!g_nextKV) return;
    if (!value) {
        JStringU16View k(env, key);
        g_nextKV->remove(k.view());
        return;
    }
    jsize len = env->GetArrayLength(value);
    jbyte* bytes = env->GetByteArrayElements(value, nullptr);
    if (bytes) {
        {
            JStringU16View k(env, key);
            g_nextKV->putByteArray(k.view(), reinterpret_cast<const uint8_t*>(bytes), len);
        }
        env->ReleaseByteArrayElements(value, bytes, JNI_ABORT);
    }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_nextkv_NextKV_nativeGetByteArray(JNIEnv* env, jobject thiz, jstring key) {
    if (!g_nextKV) return nullptr;
    bool found;
    {
        JStringU16View k(env, key);
        found = g_nextKV->getByteArray(k.view(), tls_byte_buf);
    }
    if (!found) return nullptr;
    
    jbyteArray result = env->NewByteArray(tls_byte_buf.size());
    if (result) {
        env->SetByteArrayRegion(result, 0, tls_byte_buf.size(), reinterpret_cast<const jbyte*>(tls_byte_buf.data()));
    }
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_nextkv_NextKV_nativeContains(JNIEnv* env, jobject thiz, jstring key) {
    if (!g_nextKV) return false;
    JStringU16View k(env, key);
    return g_nextKV->contains(k.view());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_nextkv_NextKV_nativeRemove(JNIEnv* env, jobject thiz, jstring key) {
    if (!g_nextKV) return;
    JStringU16View k(env, key);
    g_nextKV->remove(k.view());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_nextkv_NextKV_nativeClearAll(JNIEnv* env, jobject thiz) {
    if (!g_nextKV) return;
    g_nextKV->clearAll();
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_example_nextkv_NextKV_nativeGetSharedByteBuffer(JNIEnv* env, jobject thiz) {
    if (!g_nextKV) return nullptr;
    return env->NewDirectByteBuffer(g_nextKV->getMmapPtr(), g_nextKV->getCapacity());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_nextkv_NextKV_nativeGetRecordMeta(JNIEnv* env, jobject thiz, jstring key) {
    if (!g_nextKV) return 0;
    JStringU16View k(env, key);
    return g_nextKV->getRecordMeta(k.view());
}