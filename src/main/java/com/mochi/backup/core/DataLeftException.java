package com.mochi.backup.core;

import java.io.IOException;

public class DataLeftException extends IOException {
    public DataLeftException(long n) { super("Input stream closed with " + n + " bytes left!"); }
}
