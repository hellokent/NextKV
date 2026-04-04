package com.example.nextkv;

import dalvik.annotation.optimization.FastNative;
import java.util.concurrent.ConcurrentHashMap;

public class NextKV {
    static {
        System.loadLibrary("nextkv");
    }

    private final boolean mIsMultiProcess;
    private final ConcurrentHashMap<String, Object> mCache;

    public NextKV(boolean isMultiProcess) {
        this.mIsMultiProcess = isMultiProcess;
        if (!isMultiProcess) {
            mCache = new ConcurrentHashMap<>(8192);
        } else {
            mCache = null;
        }
    }

    public NextKV() {
        this(false); // default SP
    }

    public static native void init(String path, boolean multiProcess);

    @FastNative
    private native void nativePutString(String key, String value);
    @FastNative
    private native String nativeGetString(String key, String defaultValue);

    @FastNative
    private native void nativePutInt(String key, int value);
    @FastNative
    private native int nativeGetInt(String key, int defaultValue);

    @FastNative
    private native void nativePutBoolean(String key, boolean value);
    @FastNative
    private native boolean nativeGetBoolean(String key, boolean defaultValue);

    @FastNative
    private native void nativePutFloat(String key, float value);
    @FastNative
    private native float nativeGetFloat(String key, float defaultValue);

    @FastNative
    private native void nativePutLong(String key, long value);
    @FastNative
    private native long nativeGetLong(String key, long defaultValue);

    @FastNative
    private native void nativePutDouble(String key, double value);
    @FastNative
    private native double nativeGetDouble(String key, double defaultValue);

    @FastNative
    private native void nativePutByteArray(String key, byte[] value);
    @FastNative
    private native byte[] nativeGetByteArray(String key);

    @FastNative
    private native boolean nativeContains(String key);

    @FastNative
    private native void nativeRemove(String key);

    @FastNative
    private native void nativeClearAll();


    // Wrappers with Java-level caching for Single Process mode

    public void putString(String key, String value) {
        if (!mIsMultiProcess && mCache != null) {
            if (value == null) mCache.remove(key);
            else mCache.put(key, value);
        }
        nativePutString(key, value);
    }

    public String getString(String key, String defaultValue) {
        if (!mIsMultiProcess && mCache != null) {
            Object obj = mCache.get(key);
            if (obj != null && obj instanceof String) return (String) obj;
        }
        String result = nativeGetString(key, defaultValue);
        if (!mIsMultiProcess && mCache != null && result != null) {
            mCache.put(key, result);
        }
        return result;
    }

    // For getStringFast, we will just map it to getString since we removed DirectByteBuffer
    public String getStringFast(String key, String defaultValue) {
        return getString(key, defaultValue);
    }

    public void putInt(String key, int value) {
        if (!mIsMultiProcess && mCache != null) mCache.put(key, value);
        nativePutInt(key, value);
    }

    public int getInt(String key, int defaultValue) {
        if (!mIsMultiProcess && mCache != null) {
            Object obj = mCache.get(key);
            if (obj != null && obj instanceof Integer) return (Integer) obj;
        }
        int result = nativeGetInt(key, defaultValue);
        if (!mIsMultiProcess && mCache != null) mCache.put(key, result);
        return result;
    }

    public void putBoolean(String key, boolean value) {
        if (!mIsMultiProcess && mCache != null) mCache.put(key, value);
        nativePutBoolean(key, value);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        if (!mIsMultiProcess && mCache != null) {
            Object obj = mCache.get(key);
            if (obj != null && obj instanceof Boolean) return (Boolean) obj;
        }
        boolean result = nativeGetBoolean(key, defaultValue);
        if (!mIsMultiProcess && mCache != null) mCache.put(key, result);
        return result;
    }

    public void putFloat(String key, float value) {
        if (!mIsMultiProcess && mCache != null) mCache.put(key, value);
        nativePutFloat(key, value);
    }

    public float getFloat(String key, float defaultValue) {
        if (!mIsMultiProcess && mCache != null) {
            Object obj = mCache.get(key);
            if (obj != null && obj instanceof Float) return (Float) obj;
        }
        float result = nativeGetFloat(key, defaultValue);
        if (!mIsMultiProcess && mCache != null) mCache.put(key, result);
        return result;
    }

    public void putLong(String key, long value) {
        if (!mIsMultiProcess && mCache != null) mCache.put(key, value);
        nativePutLong(key, value);
    }

    public long getLong(String key, long defaultValue) {
        if (!mIsMultiProcess && mCache != null) {
            Object obj = mCache.get(key);
            if (obj != null && obj instanceof Long) return (Long) obj;
        }
        long result = nativeGetLong(key, defaultValue);
        if (!mIsMultiProcess && mCache != null) mCache.put(key, result);
        return result;
    }

    public void putDouble(String key, double value) {
        if (!mIsMultiProcess && mCache != null) mCache.put(key, value);
        nativePutDouble(key, value);
    }

    public double getDouble(String key, double defaultValue) {
        if (!mIsMultiProcess && mCache != null) {
            Object obj = mCache.get(key);
            if (obj != null && obj instanceof Double) return (Double) obj;
        }
        double result = nativeGetDouble(key, defaultValue);
        if (!mIsMultiProcess && mCache != null) mCache.put(key, result);
        return result;
    }

    public void putByteArray(String key, byte[] value) {
        if (!mIsMultiProcess && mCache != null) {
            if (value == null) mCache.remove(key);
            else mCache.put(key, value);
        }
        nativePutByteArray(key, value);
    }

    public byte[] getByteArray(String key) {
        if (!mIsMultiProcess && mCache != null) {
            Object obj = mCache.get(key);
            if (obj != null && obj instanceof byte[]) return (byte[]) obj;
        }
        byte[] result = nativeGetByteArray(key);
        if (!mIsMultiProcess && mCache != null && result != null) {
            mCache.put(key, result);
        }
        return result;
    }

    public boolean contains(String key) {
        if (!mIsMultiProcess && mCache != null && mCache.containsKey(key)) return true;
        return nativeContains(key);
    }

    public void remove(String key) {
        if (!mIsMultiProcess && mCache != null) mCache.remove(key);
        nativeRemove(key);
    }

    public void clearAll() {
        if (!mIsMultiProcess && mCache != null) mCache.clear();
        nativeClearAll();
    }
}