package com.example.nextkv;

import android.content.Context;
import android.util.Log;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.tencent.mmkv.MMKV;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Random;

@RunWith(AndroidJUnit4.class)
public class MMKVBenchmarkTest {

    private static final String TAG = "MMKVBenchmarkTest";
    private static final int m_loops = 50000;
    
    private String[] m_arrStrings;
    private String[] m_arrKeys;
    private String[] m_arrIntKeys;
    private Context appContext;

    @Before
    public void setup() {
        appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MMKV.initialize(appContext);

        m_arrStrings = new String[m_loops];
        m_arrKeys = new String[m_loops];
        m_arrIntKeys = new String[m_loops];
        
        Random r = new Random(42);
        final String filename = "mmkv/Android/MMKV/mmkvdemo/src/main/java/com/tencent/mmkvdemo/BenchMarkBaseService.java_";
        for (int index = 0; index < m_loops; index++) {
            m_arrStrings[index] = filename + r.nextInt();
            m_arrKeys[index] = "testStr_" + index;
            m_arrIntKeys[index] = "int_" + index;
        }
    }

    private void runMMKVBenchmark() {
        MMKV mmkv = MMKV.mmkvWithID("benchmark_interprocess", MMKV.MULTI_PROCESS_MODE);
        mmkv.clearAll();

        Random r = new Random(42);

        // mmkvBatchWriteInt
        long startTime = System.currentTimeMillis();
        for (int index = 0; index < m_loops; index++) {
            int tmp = r.nextInt();
            mmkv.encode(m_arrIntKeys[index], tmp);
        }
        long endTime = System.currentTimeMillis();
        Log.i(TAG, "MMKV write int: loop[" + m_loops + "]: " + (endTime - startTime) + " ms");

        // mmkvBatchReadInt
        startTime = System.currentTimeMillis();
        for (int index = 0; index < m_loops; index++) {
            int tmp = mmkv.decodeInt(m_arrIntKeys[index]);
        }
        endTime = System.currentTimeMillis();
        Log.i(TAG, "MMKV read int: loop[" + m_loops + "]: " + (endTime - startTime) + " ms");

        // mmkvBatchWriteString
        startTime = System.currentTimeMillis();
        for (int index = 0; index < m_loops; index++) {
            mmkv.encode(m_arrKeys[index], m_arrStrings[index]);
        }
        endTime = System.currentTimeMillis();
        Log.i(TAG, "MMKV write String: loop[" + m_loops + "]: " + (endTime - startTime) + " ms");

        // mmkvBatchReadString
        startTime = System.currentTimeMillis();
        for (int index = 0; index < m_loops; index++) {
            String tmpStr = mmkv.decodeString(m_arrKeys[index]);
        }
        endTime = System.currentTimeMillis();
        Log.i(TAG, "MMKV read String: loop[" + m_loops + "]: " + (endTime - startTime) + " ms");
    }

    private void runNextKVBenchmark() {
        File nextKvMpFile = new File(appContext.getFilesDir(), "benchmark_interprocess_nextkv.data");
        if (nextKvMpFile.exists()) {
            nextKvMpFile.delete();
        }
        NextKV.init(nextKvMpFile.getAbsolutePath(), true);
        NextKV nextkv = new NextKV(true);

        Random r = new Random(42);

        // nextkvBatchWriteInt
        long startTime = System.currentTimeMillis();
        for (int index = 0; index < m_loops; index++) {
            int tmp = r.nextInt();
            nextkv.putInt(m_arrIntKeys[index], tmp);
        }
        long endTime = System.currentTimeMillis();
        Log.i(TAG, "NextKV write int: loop[" + m_loops + "]: " + (endTime - startTime) + " ms");

        // nextkvBatchReadInt
        startTime = System.currentTimeMillis();
        for (int index = 0; index < m_loops; index++) {
            int tmp = nextkv.getInt(m_arrIntKeys[index], 0);
        }
        endTime = System.currentTimeMillis();
        Log.i(TAG, "NextKV read int: loop[" + m_loops + "]: " + (endTime - startTime) + " ms");

        // nextkvBatchWriteString
        startTime = System.currentTimeMillis();
        for (int index = 0; index < m_loops; index++) {
            nextkv.putString(m_arrKeys[index], m_arrStrings[index]);
        }
        endTime = System.currentTimeMillis();
        Log.i(TAG, "NextKV write String: loop[" + m_loops + "]: " + (endTime - startTime) + " ms");

        // nextkvBatchReadString
        startTime = System.currentTimeMillis();
        for (int index = 0; index < m_loops; index++) {
            String tmpStr = nextkv.getString(m_arrKeys[index], null);
        }
        endTime = System.currentTimeMillis();
        Log.i(TAG, "NextKV read String: loop[" + m_loops + "]: " + (endTime - startTime) + " ms");
    }

    @Test
    public void runBenchmarks() {
        Log.i(TAG, "==== Start MMKV Official Benchmark ====");
        runMMKVBenchmark();
        Log.i(TAG, "==== Start NextKV Official Benchmark ====");
        runNextKVBenchmark();
    }
}