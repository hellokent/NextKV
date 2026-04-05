package com.example.nextkv;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.tencent.mmkv.MMKV;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PersistenceTest {

    @Test
    public void step1_writeAndCrash() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MMKV.initialize(appContext);
        
        // Clean up previous files to ensure a fresh test
        File nextKvFile = new File(appContext.getFilesDir(), "nextkv_persist.data");
        if (nextKvFile.exists()) {
            nextKvFile.delete();
        }
        NextKV.init(nextKvFile.getAbsolutePath(), false);
        NextKV nextkv = new NextKV(false);
        
        MMKV mmkv = MMKV.mmkvWithID("mmkv_persist", MMKV.SINGLE_PROCESS_MODE);
        mmkv.clearAll();
        
        // Write data
        nextkv.putString("crash_test_key", "survived_nextkv_sp");
        nextkv.putInt("crash_test_int", 999);
        
        mmkv.encode("crash_test_key", "survived_mmkv_sp");
        mmkv.encode("crash_test_int", 999);

        // Multi Process mode files
        File nextKvMpFile = new File(appContext.getFilesDir(), "nextkv_persist_mp.data");
        if (nextKvMpFile.exists()) {
            nextKvMpFile.delete();
        }
        NextKV.init(nextKvMpFile.getAbsolutePath(), true);
        NextKV nextkvMp = new NextKV(true);
        
        MMKV mmkvMp = MMKV.mmkvWithID("mmkv_persist_mp", MMKV.MULTI_PROCESS_MODE);
        mmkvMp.clearAll();
        
        nextkvMp.putString("crash_test_key_mp", "survived_nextkv_mp");
        mmkvMp.encode("crash_test_key_mp", "survived_mmkv_mp");

        // Force a brutal native crash (SIGKILL) immediately after writing
        // This prevents any graceful shutdown hooks or file closing
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Test
    public void step2_readAfterCrash() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MMKV.initialize(appContext);
        
        // Read Single Process
        File nextKvFile = new File(appContext.getFilesDir(), "nextkv_persist.data");
        File nextKvMpFile = new File(appContext.getFilesDir(), "nextkv_persist_mp.data");
        android.util.Log.i("KV_Benchmark", "nextkv_persist.data exists: " + nextKvFile.exists());
        android.util.Log.i("KV_Benchmark", "nextkv_persist_mp.data exists: " + nextKvMpFile.exists());
        
        File mmkvFile = new File(appContext.getFilesDir().getParent() + "/mmkv", "mmkv_persist");
        android.util.Log.i("KV_Benchmark", "mmkv_persist exists: " + mmkvFile.exists());
        NextKV.init(nextKvFile.getAbsolutePath(), false);
        NextKV nextkv = new NextKV(false);
        
        MMKV mmkv = MMKV.mmkvWithID("mmkv_persist", MMKV.SINGLE_PROCESS_MODE);
        
        assertEquals("survived_nextkv_sp", nextkv.getString("crash_test_key", ""));
        assertEquals(999, nextkv.getInt("crash_test_int", 0));
        
        assertEquals("survived_mmkv_sp", mmkv.decodeString("crash_test_key", ""));
        assertEquals(999, mmkv.decodeInt("crash_test_int", 0));

        // Read Multi Process
        NextKV.init(nextKvMpFile.getAbsolutePath(), true);
        NextKV nextkvMp = new NextKV(true);
        
        MMKV mmkvMp = MMKV.mmkvWithID("mmkv_persist_mp", MMKV.MULTI_PROCESS_MODE);
        
        assertEquals("survived_nextkv_mp", nextkvMp.getString("crash_test_key_mp", ""));
        assertEquals("survived_mmkv_mp", mmkvMp.decodeString("crash_test_key_mp", ""));
    }
}