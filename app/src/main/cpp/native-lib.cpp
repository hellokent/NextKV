#include <jni.h>
#include <string>
#include <string_view>
#include "nextkv/NextKV.h"

static NextKV* g_nextKV = nullptr;

struct JStringView {
    JNIEnv* env;
    jstring jstr;
    const char* chars;
    size_t len;
    
    JStringView(JNIEnv* e, jstring j) : env(e), jstr(j) {
        if (jstr) {
            chars = env->GetStringUTFChars(jstr, nullptr);
            len = env->GetStringUTFLength(jstr);
        } else {
            chars = nullptr;
            len = 0;
        }
    }
    
    ~JStringView() {
        if (chars) {
            env->ReleaseStringUTFChars(jstr, chars);
        }
    }
    
    std::string_view view() const {
        return chars ? std::string_view(chars, len) : std::string_view();
    }
};

extern "C" JNIEXPORT void JNICALL
Java_com_example_nextkv_NextKV_init(JNIEnv* env, jclass clazz, jstring path) {
    if (g_nextKV != nullptr) {
        delete g_nextKV;
    }
    const char* pathStr = env->GetStringUTFChars(path, nullptr);
    g_nextKV = new NextKV(std::string(pathStr));
    env->ReleaseStringUTFChars(path, pathStr);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_nextkv_NextKV_putString(JNIEnv* env, jobject thiz, jstring key, jstring value) {
    if (!g_nextKV) return;
    JStringView k(env, key);
    if (!value) {
        g_nextKV->remove(k.view());
        return;
    }
    
    jsize len = env->GetStringLength(value);
    const jchar* chars = env->GetStringCritical(value, nullptr);
    if (chars) {
        g_nextKV->putString(k.view(), std::u16string_view(reinterpret_cast<const char16_t*>(chars), len));
        env->ReleaseStringCritical(value, chars);
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_nextkv_NextKV_getString(JNIEnv* env, jobject thiz, jstring key, jstring default_value) {
    if (!g_nextKV) return default_value;
    JStringView k(env, key);
    
    std::u16string_view result = g_nextKV->getStringView(k.view());
    if (result.data() == nullptr) {
        return default_value;
    }
    
    return env->NewString(reinterpret_cast<const jchar*>(result.data()), result.size());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_nextkv_NextKV_putInt(JNIEnv* env, jobject thiz, jstring key, jint value) {
    if (!g_nextKV) return;
    JStringView k(env, key);
    g_nextKV->putInt(k.view(), value);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_nextkv_NextKV_getInt(JNIEnv* env, jobject thiz, jstring key, jint default_value) {
    if (!g_nextKV) return default_value;
    JStringView k(env, key);
    return g_nextKV->getInt(k.view(), default_value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_nextkv_NextKV_putBoolean(JNIEnv* env, jobject thiz, jstring key, jboolean value) {
    if (!g_nextKV) return;
    JStringView k(env, key);
    g_nextKV->putBool(k.view(), value);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_nextkv_NextKV_getBoolean(JNIEnv* env, jobject thiz, jstring key, jboolean default_value) {
    if (!g_nextKV) return default_value;
    JStringView k(env, key);
    return g_nextKV->getBool(k.view(), default_value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_nextkv_NextKV_putFloat(JNIEnv* env, jobject thiz, jstring key, jfloat value) {
    if (!g_nextKV) return;
    JStringView k(env, key);
    g_nextKV->putFloat(k.view(), value);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_example_nextkv_NextKV_getFloat(JNIEnv* env, jobject thiz, jstring key, jfloat default_value) {
    if (!g_nextKV) return default_value;
    JStringView k(env, key);
    return g_nextKV->getFloat(k.view(), default_value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_nextkv_NextKV_putLong(JNIEnv* env, jobject thiz, jstring key, jlong value) {
    if (!g_nextKV) return;
    JStringView k(env, key);
    g_nextKV->putLong(k.view(), value);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_nextkv_NextKV_getLong(JNIEnv* env, jobject thiz, jstring key, jlong default_value) {
    if (!g_nextKV) return default_value;
    JStringView k(env, key);
    return g_nextKV->getLong(k.view(), default_value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_nextkv_NextKV_putDouble(JNIEnv* env, jobject thiz, jstring key, jdouble value) {
    if (!g_nextKV) return;
    JStringView k(env, key);
    g_nextKV->putDouble(k.view(), value);
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_example_nextkv_NextKV_getDouble(JNIEnv* env, jobject thiz, jstring key, jdouble default_value) {
    if (!g_nextKV) return default_value;
    JStringView k(env, key);
    return g_nextKV->getDouble(k.view(), default_value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_nextkv_NextKV_putByteArray(JNIEnv* env, jobject thiz, jstring key, jbyteArray value) {
    if (!g_nextKV) return;
    JStringView k(env, key);
    if (!value) {
        g_nextKV->remove(k.view());
        return;
    }
    jsize len = env->GetArrayLength(value);
    jbyte* bytes = env->GetByteArrayElements(value, nullptr);
    if (bytes) {
        g_nextKV->putByteArray(k.view(), reinterpret_cast<const uint8_t*>(bytes), len);
        env->ReleaseByteArrayElements(value, bytes, JNI_ABORT);
    }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_nextkv_NextKV_getByteArray(JNIEnv* env, jobject thiz, jstring key) {
    if (!g_nextKV) return nullptr;
    JStringView k(env, key);
    auto view = g_nextKV->getByteArray(k.view());
    if (view.data == nullptr) return nullptr;
    
    jbyteArray result = env->NewByteArray(view.size);
    if (result) {
        env->SetByteArrayRegion(result, 0, view.size, reinterpret_cast<const jbyte*>(view.data));
    }
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_nextkv_NextKV_contains(JNIEnv* env, jobject thiz, jstring key) {
    if (!g_nextKV) return false;
    JStringView k(env, key);
    return g_nextKV->contains(k.view());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_nextkv_NextKV_remove(JNIEnv* env, jobject thiz, jstring key) {
    if (!g_nextKV) return;
    JStringView k(env, key);
    g_nextKV->remove(k.view());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_nextkv_NextKV_clearAll(JNIEnv* env, jobject thiz) {
    if (!g_nextKV) return;
    g_nextKV->clearAll();
}