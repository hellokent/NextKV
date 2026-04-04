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
    private static final int WARMUP_ITERATIONS = 10000;
    private static final int ITERATIONS = 100000;
    
    private String[] keys;
    private String[] stringValues;
    private int[] intValues;
    private boolean[] boolValues;
    private float[] floatValues;
    private long[] longValues;
    private double[] doubleValues;
    private byte[][] byteValues;
    private int[] opSequence;

    @Before
    public void setup() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        
        String mmkvPath = MMKV.initialize(appContext);
        Log.i(TAG, "MMKV initialized at: " + mmkvPath);
        
        File nextKvFile = new File(appContext.getFilesDir(), "nextkv.data");
        if (nextKvFile.exists()) {
            nextKvFile.delete();
        }
        NextKV.init(nextKvFile.getAbsolutePath());
        Log.i(TAG, "NextKV initialized at: " + nextKvFile.getAbsolutePath());

        keys = new String[ITERATIONS];
        stringValues = new String[ITERATIONS];
        intValues = new int[ITERATIONS];
        boolValues = new boolean[ITERATIONS];
        floatValues = new float[ITERATIONS];
        longValues = new long[ITERATIONS];
        doubleValues = new double[ITERATIONS];
        byteValues = new byte[ITERATIONS][];
        opSequence = new int[ITERATIONS];

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
            opSequence[i] = random.nextInt(3); // 0=Put, 1=Get, 2=Update
        }
    }

    private void warmup(MMKV mmkv, NextKV nextkv) {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            String k = "warmup_" + i;
            mmkv.encode(k, 1);
            mmkv.decodeInt(k, 0);
            nextkv.putInt(k, 1);
            nextkv.getInt(k, 0);
        }
    }

    @Test
    public void benchmarkAll() {
        MMKV mmkv = MMKV.defaultMMKV();
        NextKV nextkv = new NextKV();
        
        Log.i(TAG, "==== WARMUP START ====");
        warmup(mmkv, nextkv);
        Log.i(TAG, "==== WARMUP DONE ====");

        Log.i(TAG, "==== BENCHMARK START ====");
        
        // 1. Put (All 7 Types)
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            mmkv.encode(keys[i] + "_s", stringValues[i]);
            mmkv.encode(keys[i] + "_i", intValues[i]);
            mmkv.encode(keys[i] + "_b", boolValues[i]);
            mmkv.encode(keys[i] + "_f", floatValues[i]);
            mmkv.encode(keys[i] + "_l", longValues[i]);
            mmkv.encode(keys[i] + "_d", doubleValues[i]);
            mmkv.encode(keys[i] + "_bytes", byteValues[i]);
        }
        long mmkvPutTime = (System.nanoTime() - start) / 1000000;
        
        start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            nextkv.putString(keys[i] + "_s", stringValues[i]);
            nextkv.putInt(keys[i] + "_i", intValues[i]);
            nextkv.putBoolean(keys[i] + "_b", boolValues[i]);
            nextkv.putFloat(keys[i] + "_f", floatValues[i]);
            nextkv.putLong(keys[i] + "_l", longValues[i]);
            nextkv.putDouble(keys[i] + "_d", doubleValues[i]);
            nextkv.putByteArray(keys[i] + "_bytes", byteValues[i]);
        }
        long nextkvPutTime = (System.nanoTime() - start) / 1000000;
        Log.i(TAG, String.format("PUT (All 7 Types * %d): MMKV=%d ms, NextKV=%d ms", ITERATIONS, mmkvPutTime, nextkvPutTime));

        // 2. Get (All 7 Types)
        start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            mmkv.decodeString(keys[i] + "_s", "");
            mmkv.decodeInt(keys[i] + "_i", 0);
            mmkv.decodeBool(keys[i] + "_b", false);
            mmkv.decodeFloat(keys[i] + "_f", 0f);
            mmkv.decodeLong(keys[i] + "_l", 0L);
            mmkv.decodeDouble(keys[i] + "_d", 0.0);
            mmkv.decodeBytes(keys[i] + "_bytes");
        }
        long mmkvGetTime = (System.nanoTime() - start) / 1000000;

        start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            nextkv.getString(keys[i] + "_s", "");
            nextkv.getInt(keys[i] + "_i", 0);
            nextkv.getBoolean(keys[i] + "_b", false);
            nextkv.getFloat(keys[i] + "_f", 0f);
            nextkv.getLong(keys[i] + "_l", 0L);
            nextkv.getDouble(keys[i] + "_d", 0.0);
            nextkv.getByteArray(keys[i] + "_bytes");
        }
        long nextkvGetTime = (System.nanoTime() - start) / 1000000;
        Log.i(TAG, String.format("GET (All 7 Types * %d): MMKV=%d ms, NextKV=%d ms", ITERATIONS, mmkvGetTime, nextkvGetTime));
        
        // 3. Mixed Operations (Put / Get / Update randomly)
        start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            int op = opSequence[i];
            String k = keys[i % 5000]; // simulate hot keys
            if (op == 0 || op == 2) {
                mmkv.encode(k + "_mix", stringValues[i]);
            } else {
                mmkv.decodeString(k + "_mix", "");
            }
        }
        long mmkvMixedTime = (System.nanoTime() - start) / 1000000;

        start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            int op = opSequence[i];
            String k = keys[i % 5000]; // simulate hot keys
            if (op == 0 || op == 2) {
                nextkv.putString(k + "_mix", stringValues[i]);
            } else {
                nextkv.getString(k + "_mix", "");
            }
        }
        long nextkvMixedTime = (System.nanoTime() - start) / 1000000;
        Log.i(TAG, String.format("MIXED (Put/Update/Get * %d): MMKV=%d ms, NextKV=%d ms", ITERATIONS, mmkvMixedTime, nextkvMixedTime));
        
        Log.i(TAG, "==== BENCHMARK DONE ====");
    }
}