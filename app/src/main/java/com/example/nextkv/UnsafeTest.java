package com.example.nextkv;
import sun.misc.Unsafe;
public class UnsafeTest {
    public static void test(Unsafe u) {
        u.arrayBaseOffset(char[].class);
    }
}
