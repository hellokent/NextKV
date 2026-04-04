package sun.misc;

public final class Unsafe {
    public void copyMemory(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes) {}
    public int arrayBaseOffset(Class<?> clazz) { return 0; }
}
