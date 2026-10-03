package com.mochi.backup.core.digest;

import com.mochi.backup.Globals;
import com.mochi.backup.core.Hash;
import org.jetbrains.annotations.NotNull;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class HashingOutputStream extends FilterOutputStream {
    private final Path path;
    private final Hash hash = Globals.CHECKSUM_SUPPLIER.get();
    private final FileTreeHashBuilder hashBuilder;
    private long bytesWritten = 0;

    public HashingOutputStream(OutputStream out, Path path, FileTreeHashBuilder hashBuilder) {
        super(out);
        this.path = path;
        this.hashBuilder = hashBuilder;
    }

    @Override
    public void write(int b) throws IOException { out.write(b); hash.update(b); bytesWritten++; }

    @Override
    public void write(@NotNull byte[] b, int off, int len) throws IOException {
        out.write(b, off, len); hash.update(b, off, len); bytesWritten += len;
    }

    @Override
    public void close() throws IOException {
        hash.update(path.getFileName().toString().getBytes(StandardCharsets.UTF_8));
        hashBuilder.update(path, hash.getValue(), bytesWritten);
        super.close();
    }
}
