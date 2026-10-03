package com.mochi.backup.core;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class BrokenFileHandler {
    private final Map<String, Exception> store = new HashMap<>();

    public void handle(Path file, Exception e) { store.put(file.toString(), e); }
    public boolean valid() { return store.isEmpty(); }
    public Map<String, Exception> get() { return store; }
}
