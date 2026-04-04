package com.example.nextkv;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

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

    private void runBasicTypesTest(boolean isMultiProcess) {
        NextKV nextkv = setupKV(isMultiProcess);
        
        // String
        nextkv.putString("str_key", "hello world");
        assertEquals("hello world", nextkv.getString("str_key", ""));

        // Int
        nextkv.putInt("int_key", 42);
        assertEquals(42, nextkv.getInt("int_key", 0));

        // Boolean
        nextkv.putBoolean("bool_key", true);
        assertTrue(nextkv.getBoolean("bool_key", false));

        // Float
        nextkv.putFloat("float_key", 3.14f);
        assertEquals(3.14f, nextkv.getFloat("float_key", 0f), 0.001f);

        // Long
        nextkv.putLong("long_key", 123456789012345L);
        assertEquals(123456789012345L, nextkv.getLong("long_key", 0L));

        // Double
        nextkv.putDouble("double_key", 2.718281828);
        assertEquals(2.718281828, nextkv.getDouble("double_key", 0.0), 0.0000001);

        // Byte Array
        byte[] bytes = new byte[]{1, 2, 3, 4, 5};
        nextkv.putByteArray("bytes_key", bytes);
        assertArrayEquals(bytes, nextkv.getByteArray("bytes_key"));
    }

    private void runUpdateTest(boolean isMultiProcess) {
        NextKV nextkv = setupKV(isMultiProcess);
        nextkv.putString("update_key", "first");
        assertEquals("first", nextkv.getString("update_key", ""));
        nextkv.putString("update_key", "second");
        assertEquals("second", nextkv.getString("update_key", ""));
    }

    private void runRemoveAndContainsTest(boolean isMultiProcess) {
        NextKV nextkv = setupKV(isMultiProcess);
        nextkv.putString("rem_key", "to_be_removed");
        assertTrue(nextkv.contains("rem_key"));
        
        nextkv.remove("rem_key");
        assertFalse(nextkv.contains("rem_key"));
        assertEquals("default", nextkv.getString("rem_key", "default"));
    }

    private void runClearAllTest(boolean isMultiProcess) {
        NextKV nextkv = setupKV(isMultiProcess);
        nextkv.putString("k1", "v1");
        nextkv.putInt("k2", 2);
        assertTrue(nextkv.contains("k1"));
        
        nextkv.clearAll();
        assertFalse(nextkv.contains("k1"));
        assertFalse(nextkv.contains("k2"));
        assertEquals("def", nextkv.getString("k1", "def"));
    }

    @Test
    public void testBasicTypesSP() { runBasicTypesTest(false); }
    @Test
    public void testBasicTypesMP() { runBasicTypesTest(true); }

    @Test
    public void testUpdateSP() { runUpdateTest(false); }
    @Test
    public void testUpdateMP() { runUpdateTest(true); }

    @Test
    public void testRemoveAndContainsSP() { runRemoveAndContainsTest(false); }
    @Test
    public void testRemoveAndContainsMP() { runRemoveAndContainsTest(true); }

    @Test
    public void testClearAllSP() { runClearAllTest(false); }
    @Test
    public void testClearAllMP() { runClearAllTest(true); }
}