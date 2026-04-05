package com.example.nextkv;

import dalvik.annotation.optimization.FastNative;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.lang.reflect.Field;
import sun.misc.Unsafe;

public class NextKV {
    static {
        System.loadLibrary("nextkv");
    }

    private final boolean mIsMultiProcess;
    private final java.util.concurrent.ConcurrentHashMap<String, Object> mCache;
    
    private ByteBuffer mRootBuffer;
    private final ThreadLocal<ByteBuffer> mThreadBuffer = new ThreadLocal<ByteBuffer>() {
        @Override
        protected ByteBuffer initialValue() {
            if (mRootBuffer != null) {
                return mRootBuffer.duplicate().order(ByteOrder.nativeOrder());
            }
            return null;
        }
    };
    
    private long mBaseAddress = 0;
    private Unsafe mUnsafe = null;
    private long mCharBaseOffset = 0;
    private int mLocalSequence = -1;

    public NextKV(boolean isMultiProcess) {
        this.mIsMultiProcess = isMultiProcess;
        mCache = new java.util.concurrent.ConcurrentHashMap<>(8192);
        
        mRootBuffer = nativeGetSharedByteBuffer();
        if (mRootBuffer != null) {
            mRootBuffer.order(ByteOrder.nativeOrder());
            try {
                Field addrField = java.nio.Buffer.class.getDeclaredField("address");
                addrField.setAccessible(true);
                mBaseAddress = addrField.getLong(mRootBuffer);
                
                Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
                unsafeField.setAccessible(true);
                mUnsafe = (Unsafe) unsafeField.get(null);
                mCharBaseOffset = mUnsafe.arrayBaseOffset(char[].class);
            } catch (Exception e) {
                // Silently fallback to ThreadLocal ByteBuffer
            }
        }
        if (isMultiProcess) {
            mLocalSequence = nativeGetSequence();
        }
    }

    public NextKV() {
        this(false); // default SP
    }

    private boolean checkSequence() {
        if (!mIsMultiProcess) return true;
        int seq = nativeGetSequence();
        if (seq == mLocalSequence) {
            return true;
        }
        mLocalSequence = seq;
        if (mCache != null) mCache.clear();
        return false;
    }

    private void updateSequence() {
        if (mIsMultiProcess) {
            mLocalSequence = nativeGetSequence();
        }
    }

    public static native void init(String path, boolean multiProcess);

    @FastNative
    private native ByteBuffer nativeGetSharedByteBuffer();

    @FastNative
    private native long nativeGetBaseAddress();

    @FastNative
    private native int nativeGetSequence();

    @FastNative
    private native long nativeGetRecordMeta(String key);

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
        if (mCache != null) {
            if (value == null) mCache.remove(key);
            else mCache.put(key, value);
        }
        nativePutString(key, value);
        updateSequence();
    }

    public String getString(String key, String defaultValue) {
        if (checkSequence() && mCache != null) {
            Object obj = mCache.get(key);
            if (obj != null && obj instanceof String) return (String) obj;
        }
        String result = nativeGetString(key, defaultValue);
        if (mCache != null && result != null) {
            mCache.put(key, result);
        }
        return result;
    }

    public String getStringFast(String key, String defaultValue) {
        if (checkSequence() && mCache != null) {
            Object obj = mCache.get(key);
            if (obj != null && obj instanceof String) return (String) obj;
        }

        long meta = nativeGetRecordMeta(key);
        if (meta == 0) return defaultValue;
        int offset = (int) (meta >>> 32);
        int size = (int) (meta & 0xFFFFFFFFL);
        if (size == 0) return defaultValue;
        
        String result;
        long baseAddr = nativeGetBaseAddress();
        if (mUnsafe != null && baseAddr != 0) {
            char[] chars = new char[size / 2];
            try {
                mUnsafe.copyMemory(null, baseAddr + offset, chars, mCharBaseOffset, size);
                result = new String(chars);
            } catch (Throwable t) {
                mUnsafe = null;
                result = nativeGetString(key, defaultValue);
            }
        } else {
            result = nativeGetString(key, defaultValue);
        }

        if (mCache != null) {
            mCache.put(key, result);
        }
        return result;
    }

    public void putInt(String key, int value) {
        if (mCache != null) {
            mCache.remove(key);
        }
        nativePutInt(key, value);
        updateSequence();
    }

    public int getInt(String key, int defaultValue) {
        return nativeGetInt(key, defaultValue);
    }

    public void putBoolean(String key, boolean value) {
        if (mCache != null) {
            mCache.remove(key);
        }
        nativePutBoolean(key, value);
        updateSequence();
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return nativeGetBoolean(key, defaultValue);
    }

    public void putFloat(String key, float value) {
        if (mCache != null) {
            mCache.remove(key);
        }
        nativePutFloat(key, value);
        updateSequence();
    }

    public float getFloat(String key, float defaultValue) {
        return nativeGetFloat(key, defaultValue);
    }

    public void putLong(String key, long value) {
        if (mCache != null) {
            mCache.remove(key);
        }
        nativePutLong(key, value);
        updateSequence();
    }

    public long getLong(String key, long defaultValue) {
        return nativeGetLong(key, defaultValue);
    }

    public void putDouble(String key, double value) {
        if (mCache != null) {
            mCache.remove(key);
        }
        nativePutDouble(key, value);
        updateSequence();
    }

    public double getDouble(String key, double defaultValue) {
        return nativeGetDouble(key, defaultValue);
    }

    public void putByteArray(String key, byte[] value) {
        if (mCache != null) {
            if (value == null) mCache.remove(key);
            else mCache.put(key, value);
        }
        nativePutByteArray(key, value);
        updateSequence();
    }

    public byte[] getByteArray(String key) {
        if (checkSequence() && mCache != null) {
            Object obj = mCache.get(key);
            if (obj != null && obj instanceof byte[]) return (byte[]) obj;
        }
        byte[] result = nativeGetByteArray(key);
        if (mCache != null && result != null) {
            mCache.put(key, result);
        }
        return result;
    }

    public boolean contains(String key) {
        if (checkSequence() && mCache != null && mCache.containsKey(key)) return true;
        return nativeContains(key);
    }

    public void remove(String key) {
        if (mCache != null) mCache.remove(key);
        nativeRemove(key);
        updateSequence();
    }

    public void clearAll() {
        if (mCache != null) mCache.clear();
        nativeClearAll();
        updateSequence();
    }
}