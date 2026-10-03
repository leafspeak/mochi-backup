package com.mochi.backup.core;

public interface Hash {
    void update(int b);
    void update(long b);
    default void update(byte[] b) { update(b, 0, b.length); }
    void update(byte[] b, int off, int len);
    long getValue();
}
