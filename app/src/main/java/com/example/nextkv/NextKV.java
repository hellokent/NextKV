package com.example.nextkv;

import dalvik.annotation.optimization.FastNative;

public class NextKV {
    static {
        System.loadLibrary("nextkv");
    }

    public static native void init(String path);

    @FastNative
    public native void putString(String key, String value);
    @FastNative
    public native String getString(String key, String defaultValue);

    @FastNative
    public native void putInt(String key, int value);
    @FastNative
    public native int getInt(String key, int defaultValue);

    @FastNative
    public native void putBoolean(String key, boolean value);
    @FastNative
    public native boolean getBoolean(String key, boolean defaultValue);

    @FastNative
    public native void putFloat(String key, float value);
    @FastNative
    public native float getFloat(String key, float defaultValue);

    @FastNative
    public native void putLong(String key, long value);
    @FastNative
    public native long getLong(String key, long defaultValue);

    @FastNative
    public native void putDouble(String key, double value);
    @FastNative
    public native double getDouble(String key, double defaultValue);

    @FastNative
    public native void putByteArray(String key, byte[] value);
    @FastNative
    public native byte[] getByteArray(String key);

    @FastNative
    public native boolean contains(String key);

    @FastNative
    public native void remove(String key);

    @FastNative
    public native void clearAll();
}