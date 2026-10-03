package com.mochi.backup.core.digest;

import com.mochi.backup.Globals;
import com.mochi.backup.core.BrokenFileHandler;
import com.mochi.backup.core.Hash;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class HashingInputStream extends FilterInputStream {
    private final Path path;
    private final Hash hash = Globals.CHECKSUM_SUPPLIER.get();
    private final FileTreeHashBuilder hashBuilder;
    private final BrokenFileHandler brokenFileHandler;
    private long bytesWritten = 0;

    public HashingInputStream(InputStream in, Path path, FileTreeHashBuilder hashBuilder, BrokenFileHandler brokenFileHandler) {
        super(in);
        this.path = path;
        this.hashBuilder = hashBuilder;
        this.brokenFileHandler = brokenFileHandler;
    }

    @Override
    public int read(@NotNull byte[] b, int off, int len) throws IOException {
        int i;
        try { i = in.read(b, off, len); }
        catch (IOException e) { throw new IOException("Error accessing: [" + path + "]", e); }
        if (i != -1) { hash.update(b, off, i); bytesWritten += i; }
        return i;
    }

    @Override
    public int read() throws IOException {
        int i;
        try { i = in.read(); }
        catch (IOException e) { throw new IOException("Error accessing: [" + path + "]", e); }
        if (i != -1) { hash.update(i); bytesWritten++; }
        return i;
    }

    @Override
    public void close() throws IOException {
        hash.update(path.getFileName().toString().getBytes(StandardCharsets.UTF_8));
        hashBuilder.update(path, hash.getValue(), bytesWritten);
        if (in.available() != 0) brokenFileHandler.handle(path, new com.mochi.backup.core.DataLeftException(in.available()));
        super.close();
    }
}
