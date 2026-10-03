package com.mochi.backup.core.create;

import org.apache.commons.compress.parallel.InputStreamSupplier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

public interface InputSupplier extends InputStreamSupplier {
    InputStream getInputStream() throws IOException;
    Optional<Path> getPath();
    String getName();
    long size() throws IOException;
}
