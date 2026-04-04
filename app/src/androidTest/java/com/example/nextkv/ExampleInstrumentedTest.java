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

@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    private static final String TAG = "KV_Benchmark";
    private static final int WARMUP_ITERATIONS = 5000;
    private static final int ITERATIONS = 5000; 
    private static final int MIXED_ITERATIONS = 20000; 
    
    private String[] keysS;
    private String[] keysI;
    private String[] keysB;
    private String[] keysF;
    private String[] keysL;
    private String[] keysD;
    private String[] keysBytes;
    private String[] keysMix;

    private String[] stringValues;
    private int[] intValues;
    private boolean[] boolValues;
    private float[] floatValues;
    private long[] longValues;
    private double[] doubleValues;
    private byte[][] byteValues;
    private int[] opSequence;
    private Context appContext;

    @Before
    public void setup() {
        appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        
        String mmkvPath = MMKV.initialize(appContext);
        Log.i(TAG, "MMKV initialized at: " + mmkvPath);

        // Keep the arrays smaller to prevent OOM. We reuse keys for MIXED_ITERATIONS
        int maxLen = ITERATIONS; 
        keysS = new String[maxLen];
        keysI = new String[maxLen];
        keysB = new String[maxLen];
        keysF = new String[maxLen];
        keysL = new String[maxLen];
        keysD = new String[maxLen];
        keysBytes = new String[maxLen];
        keysMix = new String[maxLen];

        stringValues = new String[maxLen];
        intValues = new int[maxLen];
        boolValues = new boolean[maxLen];
        floatValues = new float[maxLen];
        longValues = new long[maxLen];
        doubleValues = new double[maxLen];
        byteValues = new byte[maxLen][];
        
        // Operation sequence can be size of MIXED_ITERATIONS, it's just int array (2MB)
        opSequence = new int[MIXED_ITERATIONS];

        Random random = new Random(42);
        for (int i = 0; i < maxLen; i++) {
            String baseKey = "key_" + i;
            keysS[i] = baseKey + "_s";
            keysI[i] = baseKey + "_i";
            keysB[i] = baseKey + "_b";
            keysF[i] = baseKey + "_f";
            keysL[i] = baseKey + "_l";
            keysD[i] = baseKey + "_d";
            keysBytes[i] = baseKey + "_bytes";
            // For mixed, we just use a pool of keys
            keysMix[i] = "key_" + (i % 5000) + "_mix";

            stringValues[i] = "val_" + UUID.randomUUID().toString();
            intValues[i] = random.nextInt();
            boolValues[i] = random.nextBoolean();
            floatValues[i] = random.nextFloat();
            longValues[i] = random.nextLong();
            doubleValues[i] = random.nextDouble();
            byteValues[i] = new byte[32];
            random.nextBytes(byteValues[i]);
        }
        
        for (int i = 0; i < MIXED_ITERATIONS; i++) {
            opSequence[i] = random.nextInt(4); // 0=Put, 1=Get, 2=Remove, 3=Contains
        }
    }

    private void warmup(MMKV mmkv, NextKV nextkv) {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            String k = "warmup_" + i;
            if (mmkv != null) {
                mmkv.encode(k, 1);
                mmkv.decodeInt(k, 0);
            }
            if (nextkv != null) {
                nextkv.putInt(k, 1);
                nextkv.getInt(k, 0);
            }
        }
    }

    private void runSuite(String modeName, MMKV mmkv, NextKV nextkv) {
        Log.i(TAG, "==== WARMUP START [" + modeName + "] ====");
        warmup(mmkv, nextkv);
        Log.i(TAG, "==== WARMUP DONE [" + modeName + "] ====");

        Log.i(TAG, "==== BENCHMARK START [" + modeName + "] ====");
        
        // 1. Put (All 7 Types)
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            if (mmkv != null) {
                mmkv.encode(keysS[i], stringValues[i]);
                mmkv.encode(keysI[i], intValues[i]);
                mmkv.encode(keysB[i], boolValues[i]);
                mmkv.encode(keysF[i], floatValues[i]);
                mmkv.encode(keysL[i], longValues[i]);
                mmkv.encode(keysD[i], doubleValues[i]);
                mmkv.encode(keysBytes[i], byteValues[i]);
            } else {
                nextkv.putString(keysS[i], stringValues[i]);
                nextkv.putInt(keysI[i], intValues[i]);
                nextkv.putBoolean(keysB[i], boolValues[i]);
                nextkv.putFloat(keysF[i], floatValues[i]);
                nextkv.putLong(keysL[i], longValues[i]);
                nextkv.putDouble(keysD[i], doubleValues[i]);
                nextkv.putByteArray(keysBytes[i], byteValues[i]);
            }
        }
        long putTime = (System.nanoTime() - start) / 1000000;
        Log.i(TAG, String.format("PUT (All 7 Types * %d): %s=%d ms", ITERATIONS, modeName, putTime));

        // 2. Get (All 7 Types)
        start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            if (mmkv != null) {
                mmkv.decodeString(keysS[i], "");
                mmkv.decodeInt(keysI[i], 0);
                mmkv.decodeBool(keysB[i], false);
                mmkv.decodeFloat(keysF[i], 0f);
                mmkv.decodeLong(keysL[i], 0L);
                mmkv.decodeDouble(keysD[i], 0.0);
                mmkv.decodeBytes(keysBytes[i]);
            } else {
                nextkv.getStringFast(keysS[i], "");
                nextkv.getInt(keysI[i], 0);
                nextkv.getBoolean(keysB[i], false);
                nextkv.getFloat(keysF[i], 0f);
                nextkv.getLong(keysL[i], 0L);
                nextkv.getDouble(keysD[i], 0.0);
                nextkv.getByteArray(keysBytes[i]);
            }
        }
        long getTime = (System.nanoTime() - start) / 1000000;
        Log.i(TAG, String.format("GET (All 7 Types * %d): %s=%d ms", ITERATIONS, modeName, getTime));
        
        // 3. Mixed Operations (Put / Get / Remove / Contains randomly)
        start = System.nanoTime();
        for (int i = 0; i < MIXED_ITERATIONS; i++) {
            int op = opSequence[i];
            String k = keysMix[i % ITERATIONS]; 
            if (mmkv != null) {
                if (op == 0) mmkv.encode(k, stringValues[i % ITERATIONS]);
                else if (op == 1) mmkv.decodeString(k, "");
                else if (op == 2) mmkv.removeValueForKey(k);
                else mmkv.containsKey(k);
            } else {
                if (op == 0) nextkv.putString(k, stringValues[i % ITERATIONS]);
                else if (op == 1) nextkv.getStringFast(k, "");
                else if (op == 2) nextkv.remove(k);
                else nextkv.contains(k);
            }
        }
        long mixedTime = (System.nanoTime() - start) / 1000000;
        Log.i(TAG, String.format("MIXED (Put/Get/Upd/Cont/Rem * %d): %s=%d ms", MIXED_ITERATIONS, modeName, mixedTime));
        
        Log.i(TAG, "==== BENCHMARK DONE [" + modeName + "] ====");
    }

    private void runConcurrentSuite(String modeName, MMKV mmkv, NextKV nextkv) throws InterruptedException {
        Log.i(TAG, "==== CONCURRENT BENCHMARK START [" + modeName + "] ====");
        int threadCount = 4;
        int opsPerThread = MIXED_ITERATIONS / threadCount;
        Thread[] threads = new Thread[threadCount];
        
        long start = System.nanoTime();
        for (int t = 0; t < threadCount; t++) {
            final int tIdx = t;
            threads[t] = new Thread(() -> {
                for (int i = tIdx * opsPerThread; i < (tIdx + 1) * opsPerThread; i++) {
                    int op = opSequence[i];
                    String k = keysMix[i % 1000]; // Tighter hot keys for contention
                    if (mmkv != null) {
                        if (op == 0) mmkv.encode(k, stringValues[i % ITERATIONS]);
                        else if (op == 1) mmkv.decodeString(k, "");
                        else if (op == 2) mmkv.removeValueForKey(k);
                        else mmkv.containsKey(k);
                    } else if (nextkv != null) {
                        if (op == 0) nextkv.putString(k, stringValues[i % ITERATIONS]);
                        else if (op == 1) nextkv.getStringFast(k, "");
                        else if (op == 2) nextkv.remove(k);
                        else nextkv.contains(k);
                    }
                }
            });
            threads[t].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        long time = (System.nanoTime() - start) / 1000000;
        Log.i(TAG, String.format("CONCURRENT MIXED (4 Threads, Total %d): %s=%d ms", MIXED_ITERATIONS, modeName, time));
        Log.i(TAG, "==== CONCURRENT BENCHMARK DONE [" + modeName + "] ====");
    }

    private void runMixedProfileLoop(MMKV mmkv, NextKV nextkv, String modeName) {
        warmup(MMKV.defaultMMKV(), new NextKV()); 
        Log.i(TAG, "==== PROFILE LOOP START [" + modeName + "] ====");
        long endTime = System.currentTimeMillis() + 10000; // Run for 10 seconds
        long count = 0;
        while (System.currentTimeMillis() < endTime) {
            int i = (int) (count % MIXED_ITERATIONS);
            int op = opSequence[i];
            String k = keysMix[i % ITERATIONS]; 
            if (mmkv != null) {
                if (op == 0) mmkv.encode(k, stringValues[i % ITERATIONS]);
                else if (op == 1) mmkv.decodeString(k, "");
                else if (op == 2) mmkv.removeValueForKey(k);
                else mmkv.containsKey(k);
            } else if (nextkv != null) {
                if (op == 0) nextkv.putString(k, stringValues[i % ITERATIONS]);
                else if (op == 1) nextkv.getStringFast(k, "");
                else if (op == 2) nextkv.remove(k);
                else nextkv.contains(k);
            }
            count++;
        }
        Log.i(TAG, "==== PROFILE LOOP DONE [" + modeName + "] Total Ops: " + count + " ====");
    }

    @Test
    public void profileMmkvSpMixed() {
        MMKV mmkv = MMKV.mmkvWithID("prof_mmkv_sp", MMKV.SINGLE_PROCESS_MODE);
        runMixedProfileLoop(mmkv, null, "MMKV_SP");
    }

    @Test
    public void profileNextkvSpMixed() {
        File f = new File(appContext.getFilesDir(), "prof_nextkv_sp.data");
        if (f.exists()) f.delete();
        NextKV.init(f.getAbsolutePath(), false);
        NextKV nextkv = new NextKV(false);
        runMixedProfileLoop(null, nextkv, "NextKV_SP");
    }

    @Test
    public void profileMmkvMpMixed() {
        MMKV mmkv = MMKV.mmkvWithID("prof_mmkv_mp", MMKV.MULTI_PROCESS_MODE);
        runMixedProfileLoop(mmkv, null, "MMKV_MP");
    }

    @Test
    public void profileNextkvMpMixed() {
        File f = new File(appContext.getFilesDir(), "prof_nextkv_mp.data");
        if (f.exists()) f.delete();
        NextKV.init(f.getAbsolutePath(), true);
        NextKV nextkv = new NextKV(true);
        runMixedProfileLoop(null, nextkv, "NextKV_MP");
    }

    @Test
    public void benchmarkAll() throws InterruptedException {
        // --- Single Process Benchmark ---
        File nextKvSpFile = new File(appContext.getFilesDir(), "nextkv_sp.data");
        if (nextKvSpFile.exists()) nextKvSpFile.delete();
        NextKV.init(nextKvSpFile.getAbsolutePath(), false); // Single Process
        
        MMKV mmkvSp = MMKV.mmkvWithID("sp", MMKV.SINGLE_PROCESS_MODE);
        NextKV nextkvSp = new NextKV(false);
        
        runSuite("MMKV_SP", mmkvSp, null);
        runSuite("NextKV_SP", null, nextkvSp);
        runConcurrentSuite("MMKV_SP", mmkvSp, null);
        runConcurrentSuite("NextKV_SP", null, nextkvSp);
        
        // --- Multi Process Benchmark ---
        File nextKvMpFile = new File(appContext.getFilesDir(), "nextkv_mp.data");
        if (nextKvMpFile.exists()) nextKvMpFile.delete();
        NextKV.init(nextKvMpFile.getAbsolutePath(), true); // Multi Process
        
        MMKV mmkvMp = MMKV.mmkvWithID("mp", MMKV.MULTI_PROCESS_MODE);
        NextKV nextkvMp = new NextKV(true);
        
        runSuite("MMKV_MP", mmkvMp, null);
        runSuite("NextKV_MP", null, nextkvMp);
        runConcurrentSuite("MMKV_MP", mmkvMp, null);
        runConcurrentSuite("NextKV_MP", null, nextkvMp);
    }
}