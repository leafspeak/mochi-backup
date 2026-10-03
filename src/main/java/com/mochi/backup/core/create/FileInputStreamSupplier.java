package com.mochi.backup.core.create;

import com.mochi.backup.MochiClient;
import com.mochi.backup.core.BrokenFileHandler;
import com.mochi.backup.core.digest.FileTreeHashBuilder;
import com.mochi.backup.core.digest.HashingInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public record FileInputStreamSupplier(Path path, String name, FileTreeHashBuilder hashTreeBuilder, BrokenFileHandler brokenFileHandler) implements InputSupplier {
    @Override
    public InputStream getInputStream() throws IOException {
        try {
            return new HashingInputStream(Files.newInputStream(path), path, hashTreeBuilder, brokenFileHandler);
        } catch (IOException e) {
            hashTreeBuilder.update(path, 0, 0);
            brokenFileHandler.handle(path, e);
            throw e;
        }
    }

    @Override
    public Optional<Path> getPath() { return Optional.of(path); }
    @Override
    public long size() throws IOException { return Files.size(path); }
    @Override
    public String getName() { return name; }
    @Override
    public InputStream get() {
        try { return getInputStream(); }
        catch (IOException e) { MochiClient.LOGGER.error("Failed to open input stream for: {}", path, e); }
        return null;
    }
}
