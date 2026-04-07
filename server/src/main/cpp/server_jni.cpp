#include <jni.h>
#include <string>
#include <string_view>
#include <vector>
#include "../../../../app/src/main/cpp/nextkv/NextKV.h"

static NextKV* g_nextKV = nullptr;
thread_local std::vector<char16_t> tls_string_buf;
thread_local std::vector<uint8_t> tls_byte_buf;

class JStringU16View {
public:
    JStringU16View(JNIEnv* env, jstring str) : m_env(env), m_str(str) {
        if (str) {
            m_chars = env->GetStringChars(str, nullptr);
            m_len = env->GetStringLength(str);
        } else {
            m_chars = nullptr;
            m_len = 0;
        }
    }
    ~JStringU16View() {
        if (m_str && m_chars) {
            m_env->ReleaseStringChars(m_str, m_chars);
        }
    }
    std::u16string_view view() const {
        return std::u16string_view(reinterpret_cast<const char16_t*>(m_chars), m_len);
    }
private:
    JNIEnv* m_env;
    jstring m_str;
    const jchar* m_chars;
    jsize m_len;
};

extern "C" JNIEXPORT void JNICALL
Java_com_nextkv_NextKV_init(JNIEnv* env, jclass clazz, jstring path, jboolean multiProcess) {
    if (g_nextKV) {
        delete g_nextKV;
    }
    const char* pathStr = env->GetStringUTFChars(path, nullptr);
    g_nextKV = new NextKV(pathStr, multiProcess);
    env->ReleaseStringUTFChars(path, pathStr);
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_nextkv_NextKV_nativeGetSharedByteBuffer(JNIEnv* env, jobject thiz) {
    if (!g_nextKV || !g_nextKV->getMmapPtr()) return nullptr;
    return env->NewDirectByteBuffer(g_nextKV->getMmapPtr(), g_nextKV->getCapacity());
}

extern "C" JNIEXPORT void JNICALL
Java_com_nextkv_NextKV_nativePutString(JNIEnv* env, jobject thiz, jstring key, jstring value) {
    if (!g_nextKV) return;
    JStringU16View k(env, key);
    if (!value) {
        g_nextKV->remove(k.view());
        return;
    }
    JStringU16View v(env, value);
    g_nextKV->putString(k.view(), v.view());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nextkv_NextKV_nativeGetString(JNIEnv* env, jobject thiz, jstring key, jstring defaultValue) {
    if (!g_nextKV) return defaultValue;
    JStringU16View k(env, key);
    if (g_nextKV->getString(k.view(), tls_string_buf)) {
        return env->NewString(reinterpret_cast<const jchar*>(tls_string_buf.data()), tls_string_buf.size());
    }
    return defaultValue;
}

extern "C" JNIEXPORT void JNICALL
Java_com_nextkv_NextKV_nativePutInt(JNIEnv* env, jobject thiz, jstring key, jint value) {
    if (!g_nextKV) return;
    JStringU16View k(env, key);
    g_nextKV->putInt(k.view(), value);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_nextkv_NextKV_nativeGetInt(JNIEnv* env, jobject thiz, jstring key, jint defaultValue) {
    if (!g_nextKV) return defaultValue;
    JStringU16View k(env, key);
    return g_nextKV->getInt(k.view(), defaultValue);
}

extern "C" JNIEXPORT void JNICALL
Java_com_nextkv_NextKV_nativePutBoolean(JNIEnv* env, jobject thiz, jstring key, jboolean value) {
    if (!g_nextKV) return;
    JStringU16View k(env, key);
    g_nextKV->putBool(k.view(), value);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nextkv_NextKV_nativeGetBoolean(JNIEnv* env, jobject thiz, jstring key, jboolean defaultValue) {
    if (!g_nextKV) return defaultValue;
    JStringU16View k(env, key);
    return g_nextKV->getBool(k.view(), defaultValue);
}

extern "C" JNIEXPORT void JNICALL
Java_com_nextkv_NextKV_nativePutFloat(JNIEnv* env, jobject thiz, jstring key, jfloat value) {
    if (!g_nextKV) return;
    JStringU16View k(env, key);
    g_nextKV->putFloat(k.view(), value);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_nextkv_NextKV_nativeGetFloat(JNIEnv* env, jobject thiz, jstring key, jfloat defaultValue) {
    if (!g_nextKV) return defaultValue;
    JStringU16View k(env, key);
    return g_nextKV->getFloat(k.view(), defaultValue);
}

extern "C" JNIEXPORT void JNICALL
Java_com_nextkv_NextKV_nativePutLong(JNIEnv* env, jobject thiz, jstring key, jlong value) {
    if (!g_nextKV) return;
    JStringU16View k(env, key);
    g_nextKV->putLong(k.view(), value);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_nextkv_NextKV_nativeGetLong(JNIEnv* env, jobject thiz, jstring key, jlong defaultValue) {
    if (!g_nextKV) return defaultValue;
    JStringU16View k(env, key);
    return g_nextKV->getLong(k.view(), defaultValue);
}

extern "C" JNIEXPORT void JNICALL
Java_com_nextkv_NextKV_nativePutDouble(JNIEnv* env, jobject thiz, jstring key, jdouble value) {
    if (!g_nextKV) return;
    JStringU16View k(env, key);
    g_nextKV->putDouble(k.view(), value);
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_nextkv_NextKV_nativeGetDouble(JNIEnv* env, jobject thiz, jstring key, jdouble defaultValue) {
    if (!g_nextKV) return defaultValue;
    JStringU16View k(env, key);
    return g_nextKV->getDouble(k.view(), defaultValue);
}

extern "C" JNIEXPORT void JNICALL
Java_com_nextkv_NextKV_nativePutByteArray(JNIEnv* env, jobject thiz, jstring key, jbyteArray value) {
    if (!g_nextKV) return;
    JStringU16View k(env, key);
    if (!value) {
        g_nextKV->remove(k.view());
        return;
    }
    jsize len = env->GetArrayLength(value);
    jbyte* bytes = env->GetByteArrayElements(value, nullptr);
    g_nextKV->putByteArray(k.view(), reinterpret_cast<const uint8_t*>(bytes), len);
    env->ReleaseByteArrayElements(value, bytes, JNI_ABORT);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_nextkv_NextKV_nativeGetByteArray(JNIEnv* env, jobject thiz, jstring key) {
    if (!g_nextKV) return nullptr;
    JStringU16View k(env, key);
    if (g_nextKV->getByteArray(k.view(), tls_byte_buf)) {
        jbyteArray arr = env->NewByteArray(tls_byte_buf.size());
        env->SetByteArrayRegion(arr, 0, tls_byte_buf.size(), reinterpret_cast<const jbyte*>(tls_byte_buf.data()));
        return arr;
    }
    return nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nextkv_NextKV_nativeContains(JNIEnv* env, jobject thiz, jstring key) {
    if (!g_nextKV) return false;
    JStringU16View k(env, key);
    return g_nextKV->contains(k.view());
}

extern "C" JNIEXPORT void JNICALL
Java_com_nextkv_NextKV_nativeRemove(JNIEnv* env, jobject thiz, jstring key) {
    if (!g_nextKV) return;
    JStringU16View k(env, key);
    g_nextKV->remove(k.view());
}

extern "C" JNIEXPORT void JNICALL
Java_com_nextkv_NextKV_nativeClearAll(JNIEnv* env, jobject thiz) {
    if (!g_nextKV) return;
    g_nextKV->clearAll();
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_nextkv_NextKV_nativeGetBaseAddress(JNIEnv* env, jobject thiz) {
    if (!g_nextKV) return 0;
    return reinterpret_cast<jlong>(g_nextKV->getMmapPtr());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_nextkv_NextKV_nativeGetSequence(JNIEnv* env, jobject thiz) {
    if (!g_nextKV) return 0;
    return static_cast<jint>(g_nextKV->getSequence());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_nextkv_NextKV_nativeGetRecordMeta(JNIEnv* env, jobject thiz, jstring key) {
    if (!g_nextKV) return 0;
    JStringU16View k(env, key);
    return g_nextKV->getRecordMeta(k.view());
}