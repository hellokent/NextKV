package com.example.nextkv;

import android.content.Context;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.tencent.mmkv.MMKV;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.Before;

import java.io.File;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    private static final String TAG = "KV_Benchmark";
    private static final int ITERATIONS = 50000;
    
    private String[] keys;
    private String[] stringValues;
    private int[] intValues;
    private boolean[] boolValues;
    private float[] floatValues;
    private long[] longValues;
    private double[] doubleValues;
    private byte[][] byteValues;
    private int[] opSequence;
    private int[] typeSequence;
    private Context appContext;

    private static final String[] TYPE_NAMES = {
        "String", "Int", "Bool", "Float", "Long", "Double", "Bytes", "AllMixed"
    };

    interface Engine {
        void put(int type, String key, int index);
        void get(int type, String key, int index);
        void remove(String key);
        boolean contains(String key);
        void clearAll();
    }

    class MMKVEngine implements Engine {
        MMKV mmkv;
        MMKVEngine(MMKV m) { this.mmkv = m; }
        
        @Override public void put(int type, String key, int index) {
            if (type == 0) mmkv.encode(key, stringValues[index]);
            else if (type == 1) mmkv.encode(key, intValues[index]);
            else if (type == 2) mmkv.encode(key, boolValues[index]);
            else if (type == 3) mmkv.encode(key, floatValues[index]);
            else if (type == 4) mmkv.encode(key, longValues[index]);
            else if (type == 5) mmkv.encode(key, doubleValues[index]);
            else if (type == 6) mmkv.encode(key, byteValues[index]);
        }
        @Override public void get(int type, String key, int index) {
            if (type == 0) mmkv.decodeString(key, "");
            else if (type == 1) mmkv.decodeInt(key, 0);
            else if (type == 2) mmkv.decodeBool(key, false);
            else if (type == 3) mmkv.decodeFloat(key, 0f);
            else if (type == 4) mmkv.decodeLong(key, 0L);
            else if (type == 5) mmkv.decodeDouble(key, 0.0);
            else if (type == 6) mmkv.decodeBytes(key);
        }
        @Override public void remove(String key) { mmkv.removeValueForKey(key); }
        @Override public boolean contains(String key) { return mmkv.containsKey(key); }
        @Override public void clearAll() { mmkv.clearAll(); }
    }

    class NextKVEngine implements Engine {
        NextKV nextkv;
        NextKVEngine(NextKV n) { this.nextkv = n; }
        
        @Override public void put(int type, String key, int index) {
            if (type == 0) nextkv.putString(key, stringValues[index]);
            else if (type == 1) nextkv.putInt(key, intValues[index]);
            else if (type == 2) nextkv.putBoolean(key, boolValues[index]);
            else if (type == 3) nextkv.putFloat(key, floatValues[index]);
            else if (type == 4) nextkv.putLong(key, longValues[index]);
            else if (type == 5) nextkv.putDouble(key, doubleValues[index]);
            else if (type == 6) nextkv.putByteArray(key, byteValues[index]);
        }
        @Override public void get(int type, String key, int index) {
            if (type == 0) nextkv.getStringFast(key, "");
            else if (type == 1) nextkv.getInt(key, 0);
            else if (type == 2) nextkv.getBoolean(key, false);
            else if (type == 3) nextkv.getFloat(key, 0f);
            else if (type == 4) nextkv.getLong(key, 0L);
            else if (type == 5) nextkv.getDouble(key, 0.0);
            else if (type == 6) nextkv.getByteArray(key);
        }
        @Override public void remove(String key) { nextkv.remove(key); }
        @Override public boolean contains(String key) { return nextkv.contains(key); }
        @Override public void clearAll() { nextkv.clearAll(); }
    }

    @Before
    public void setup() {
        appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MMKV.initialize(appContext);

        keys = new String[ITERATIONS];
        stringValues = new String[ITERATIONS];
        intValues = new int[ITERATIONS];
        boolValues = new boolean[ITERATIONS];
        floatValues = new float[ITERATIONS];
        longValues = new long[ITERATIONS];
        doubleValues = new double[ITERATIONS];
        byteValues = new byte[ITERATIONS][];
        opSequence = new int[ITERATIONS];
        typeSequence = new int[ITERATIONS];

        Random random = new Random(42);
        for (int i = 0; i < ITERATIONS; i++) {
            keys[i] = "key_" + i;
            stringValues[i] = "val_" + UUID.randomUUID().toString();
            intValues[i] = random.nextInt();
            boolValues[i] = random.nextBoolean();
            floatValues[i] = random.nextFloat();
            longValues[i] = random.nextLong();
            doubleValues[i] = random.nextDouble();
            byteValues[i] = new byte[32];
            random.nextBytes(byteValues[i]);
            
            opSequence[i] = random.nextInt(4); // 0=Put, 1=Get, 2=Remove, 3=Contains
            typeSequence[i] = random.nextInt(7); // 0..6
        }
    }

    private void runBenchmarkForType(String engineName, String processMode, Engine engine, int targetType) throws InterruptedException {
        String typeName = TYPE_NAMES[targetType];
        
        // Single Thread PUT
        engine.clearAll();
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            int t = (targetType == 7) ? typeSequence[i] : targetType;
            engine.put(t, keys[i], i);
        }
        long putST = (System.nanoTime() - start) / 1000000;
        Log.i(TAG, String.format("CSV: %s,%s,%s,ST,PUT,%d", engineName, processMode, typeName, putST));

        // Single Thread GET
        start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            int t = (targetType == 7) ? typeSequence[i] : targetType;
            engine.get(t, keys[i], i);
        }
        long getST = (System.nanoTime() - start) / 1000000;
        Log.i(TAG, String.format("CSV: %s,%s,%s,ST,GET,%d", engineName, processMode, typeName, getST));

        // Single Thread MIXED
        start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            int op = opSequence[i];
            int t = (targetType == 7) ? typeSequence[i] : targetType;
            if (op == 0) engine.put(t, keys[i], i);
            else if (op == 1) engine.get(t, keys[i], i);
            else if (op == 2) engine.remove(keys[i]);
            else engine.contains(keys[i]);
        }
        long mixST = (System.nanoTime() - start) / 1000000;
        Log.i(TAG, String.format("CSV: %s,%s,%s,ST,MIXED,%d", engineName, processMode, typeName, mixST));

        // Multi Thread PUT
        engine.clearAll();
        int threads = 4;
        CountDownLatch latch = new CountDownLatch(threads);
        start = System.nanoTime();
        for (int threadId = 0; threadId < threads; threadId++) {
            final int tId = threadId;
            new Thread(() -> {
                for (int i = tId * (ITERATIONS / threads); i < (tId + 1) * (ITERATIONS / threads); i++) {
                    int t = (targetType == 7) ? typeSequence[i] : targetType;
                    engine.put(t, keys[i], i);
                }
                latch.countDown();
            }).start();
        }
        latch.await();
        long putMT = (System.nanoTime() - start) / 1000000;
        Log.i(TAG, String.format("CSV: %s,%s,%s,MT,PUT,%d", engineName, processMode, typeName, putMT));

        // Multi Thread GET
        CountDownLatch latch2 = new CountDownLatch(threads);
        start = System.nanoTime();
        for (int threadId = 0; threadId < threads; threadId++) {
            final int tId = threadId;
            new Thread(() -> {
                for (int i = tId * (ITERATIONS / threads); i < (tId + 1) * (ITERATIONS / threads); i++) {
                    int t = (targetType == 7) ? typeSequence[i] : targetType;
                    engine.get(t, keys[i], i);
                }
                latch2.countDown();
            }).start();
        }
        latch2.await();
        long getMT = (System.nanoTime() - start) / 1000000;
        Log.i(TAG, String.format("CSV: %s,%s,%s,MT,GET,%d", engineName, processMode, typeName, getMT));

        // Multi Thread MIXED
        CountDownLatch latch3 = new CountDownLatch(threads);
        start = System.nanoTime();
        for (int threadId = 0; threadId < threads; threadId++) {
            final int tId = threadId;
            new Thread(() -> {
                for (int i = tId * (ITERATIONS / threads); i < (tId + 1) * (ITERATIONS / threads); i++) {
                    int op = opSequence[i];
                    int t = (targetType == 7) ? typeSequence[i] : targetType;
                    if (op == 0) engine.put(t, keys[i], i);
                    else if (op == 1) engine.get(t, keys[i], i);
                    else if (op == 2) engine.remove(keys[i]);
                    else engine.contains(keys[i]);
                }
                latch3.countDown();
            }).start();
        }
        latch3.await();
        long mixMT = (System.nanoTime() - start) / 1000000;
        Log.i(TAG, String.format("CSV: %s,%s,%s,MT,MIXED,%d", engineName, processMode, typeName, mixMT));
    }

    private void runFullSuite(String engineName, String processMode, Engine engine) throws InterruptedException {
        // Warmup
        engine.put(1, "warmup", 0);
        engine.get(1, "warmup", 0);

        for (int type = 0; type <= 7; type++) {
            runBenchmarkForType(engineName, processMode, engine, type);
        }
    }

    @Test
    public void benchmarkAll() throws InterruptedException {
        // --- Single Process Benchmark ---
        File nextKvSpFile = new File(appContext.getFilesDir(), "nextkv_sp_full.data");
        if (nextKvSpFile.exists()) nextKvSpFile.delete();
        NextKV.init(nextKvSpFile.getAbsolutePath(), false); // Single Process
        
        MMKV mmkvSp = MMKV.mmkvWithID("sp_full", MMKV.SINGLE_PROCESS_MODE);
        NextKV nextkvSp = new NextKV(false);
        
        runFullSuite("MMKV", "SP", new MMKVEngine(mmkvSp));
        runFullSuite("NextKV", "SP", new NextKVEngine(nextkvSp));
        
        // --- Multi Process Benchmark ---
        File nextKvMpFile = new File(appContext.getFilesDir(), "nextkv_mp_full.data");
        if (nextKvMpFile.exists()) nextKvMpFile.delete();
        NextKV.init(nextKvMpFile.getAbsolutePath(), true); // Multi Process
        
        MMKV mmkvMp = MMKV.mmkvWithID("mp_full", MMKV.MULTI_PROCESS_MODE);
        NextKV nextkvMp = new NextKV(true);
        
        runFullSuite("MMKV", "MP", new MMKVEngine(mmkvMp));
        runFullSuite("NextKV", "MP", new NextKVEngine(nextkvMp));
    }
}