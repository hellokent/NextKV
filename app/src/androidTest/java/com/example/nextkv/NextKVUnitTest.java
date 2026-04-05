package com.example.nextkv;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NextKVUnitTest {

    private NextKV setupKV(boolean isMultiProcess) {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File nextKvFile = new File(appContext.getFilesDir(), "nextkv_unit_" + isMultiProcess + ".data");
        if (nextKvFile.exists()) {
            nextKvFile.delete();
        }
        NextKV.init(nextKvFile.getAbsolutePath(), isMultiProcess);
        return new NextKV(isMultiProcess);
    }

    private void testAllTypesSingleThread(boolean isMultiProcess) {
        NextKV nextkv = setupKV(isMultiProcess);
        int iterations = 1000;

        for (int i = 0; i < iterations; i++) {
            // PUT
            nextkv.putString("str_" + i, "val_" + i);
            nextkv.putInt("int_" + i, i);
            nextkv.putBoolean("bool_" + i, i % 2 == 0);
            nextkv.putFloat("float_" + i, i + 0.5f);
            nextkv.putLong("long_" + i, i * 100000L);
            nextkv.putDouble("double_" + i, i + 0.0001);
            nextkv.putByteArray("bytes_" + i, new byte[]{(byte)(i % 255)});

            // GET
            assertEquals("val_" + i, nextkv.getString("str_" + i, ""));
            assertEquals(i, nextkv.getInt("int_" + i, -1));
            assertEquals(i % 2 == 0, nextkv.getBoolean("bool_" + i, false));
            assertEquals(i + 0.5f, nextkv.getFloat("float_" + i, -1f), 0.001f);
            assertEquals(i * 100000L, nextkv.getLong("long_" + i, -1L));
            assertEquals(i + 0.0001, nextkv.getDouble("double_" + i, -1.0), 0.0001);
            assertArrayEquals(new byte[]{(byte)(i % 255)}, nextkv.getByteArray("bytes_" + i));

            // UPDATE
            nextkv.putInt("int_" + i, i + 1);
            assertEquals(i + 1, nextkv.getInt("int_" + i, -1));

            // CONTAINS & REMOVE
            assertTrue(nextkv.contains("int_" + i));
            if (i % 10 == 0) {
                nextkv.remove("int_" + i);
                assertFalse(nextkv.contains("int_" + i));
                assertEquals(-1, nextkv.getInt("int_" + i, -1));
            }
        }
    }

    private void testAllTypesMultiThread(boolean isMultiProcess) throws InterruptedException {
        NextKV nextkv = setupKV(isMultiProcess);
        int threadCount = 4;
        int countPerThread = 500;
        CountDownLatch putLatch = new CountDownLatch(threadCount);

        // MT PUT
        for (int t = 0; t < threadCount; t++) {
            final int tIdx = t;
            new Thread(() -> {
                for (int i = 0; i < countPerThread; i++) {
                    String prefix = "mt_" + tIdx + "_" + i + "_";
                    nextkv.putString(prefix + "str", "val_" + i);
                    nextkv.putInt(prefix + "int", i);
                    nextkv.putBoolean(prefix + "bool", i % 2 == 0);
                    nextkv.putFloat(prefix + "float", i + 0.5f);
                    nextkv.putLong(prefix + "long", i * 100000L);
                    nextkv.putDouble(prefix + "double", i + 0.0001);
                    nextkv.putByteArray(prefix + "bytes", new byte[]{(byte)(i % 255)});
                }
                putLatch.countDown();
            }).start();
        }
        putLatch.await();

        // MT GET & UPDATE & REMOVE (MIXED)
        CountDownLatch mixLatch = new CountDownLatch(threadCount);
        for (int t = 0; t < threadCount; t++) {
            final int tIdx = t;
            new Thread(() -> {
                for (int i = 0; i < countPerThread; i++) {
                    String prefix = "mt_" + tIdx + "_" + i + "_";
                    // GET
                    assertEquals("val_" + i, nextkv.getString(prefix + "str", ""));
                    assertEquals(i, nextkv.getInt(prefix + "int", -1));
                    assertEquals(i % 2 == 0, nextkv.getBoolean(prefix + "bool", false));
                    assertEquals(i + 0.5f, nextkv.getFloat(prefix + "float", -1f), 0.001f);
                    assertEquals(i * 100000L, nextkv.getLong(prefix + "long", -1L));
                    assertEquals(i + 0.0001, nextkv.getDouble(prefix + "double", -1.0), 0.0001);
                    assertArrayEquals(new byte[]{(byte)(i % 255)}, nextkv.getByteArray(prefix + "bytes"));

                    // UPDATE
                    nextkv.putInt(prefix + "int", i + 1);
                    assertEquals(i + 1, nextkv.getInt(prefix + "int", -1));

                    // REMOVE
                    if (i % 2 == 0) {
                        nextkv.remove(prefix + "str");
                        assertFalse(nextkv.contains(prefix + "str"));
                    }
                }
                mixLatch.countDown();
            }).start();
        }
        mixLatch.await();
    }

    @Test
    public void testCartesianSP_SingleThread() {
        testAllTypesSingleThread(false);
    }

    @Test
    public void testCartesianMP_SingleThread() {
        testAllTypesSingleThread(true);
    }

    @Test
    public void testCartesianSP_MultiThread() throws InterruptedException {
        testAllTypesMultiThread(false);
    }

    @Test
    public void testCartesianMP_MultiThread() throws InterruptedException {
        testAllTypesMultiThread(true);
    }
}