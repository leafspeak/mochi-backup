package com.mochi.backup.core.create.compressors;

import com.mochi.backup.MochiClient;
import com.mochi.backup.core.NoSpaceLeftOnDeviceException;
import com.mochi.backup.core.create.ExecutableBackup;
import com.mochi.backup.core.create.InputSupplier;
import org.apache.commons.compress.archivers.zip.ParallelScatterZipCreator;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;

public class ParallelZipCompressor extends ZipCompressor {
    private ParallelScatterZipCreator scatterZipCreator;

    public static ParallelZipCompressor getInstance() { return new ParallelZipCompressor(); }

    @Override
    protected OutputStream createArchiveOutputStream(OutputStream stream, ExecutableBackup ctx, int coreLimit) {
        scatterZipCreator = new ParallelScatterZipCreator(Executors.newFixedThreadPool(coreLimit));
        return super.createArchiveOutputStream(stream, ctx, coreLimit);
    }

    @Override
    protected void addEntry(InputSupplier input, OutputStream arc) throws IOException {
        ZipArchiveEntry entry;
        if (input.getPath().isEmpty()) {
            entry = new ZipArchiveEntry(input.getName());
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(input.size());
        } else {
            Path file = input.getPath().get();
            // Skip files that disappeared mid-backup (player disconnect cleanup, etc.)
            try {
                entry = (ZipArchiveEntry) ((ZipArchiveOutputStream) arc).createArchiveEntry(file, input.getName());
            } catch (java.nio.file.NoSuchFileException e) {
                // File was deleted between listing and reading — skip it silently
                return;
            }
            if (ZipCompressor.isDotDat(file.toString())) {
                long size;
                try { size = Files.size(file); }
                catch (java.nio.file.NoSuchFileException ignored) { return; }
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(size);
                entry.setCompressedSize(size);
                entry.setCrc(getCRC(file));
            } else entry.setMethod(ZipEntry.DEFLATED);
        }
        entry.setTime(System.currentTimeMillis());
        scatterZipCreator.addArchiveEntry(entry, input);
    }

    @Override
    protected void finish(OutputStream arc) throws InterruptedException, IOException, ExecutionException {
        try {
            scatterZipCreator.writeTo((ZipArchiveOutputStream) arc);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            MochiClient.LOGGER.error("Parallel ZIP write failed (possible out of space)", cause);
            throw new NoSpaceLeftOnDeviceException(cause != null ? cause : e);
        }
    }
}
