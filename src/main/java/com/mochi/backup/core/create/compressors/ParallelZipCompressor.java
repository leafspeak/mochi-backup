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
import java.util.Locale;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;

public class ParallelZipCompressor extends ZipCompressor {
    private ParallelScatterZipCreator scatterZipCreator;
    /**
     * The creator does not expose its executor, so we keep our own reference and always
     * shut it down: its workers are only stopped by writeTo(), which is skipped whenever
     * a backup fails first - leaking threads that then keep the JVM alive.
     */
    private ExecutorService executorService;

    public static ParallelZipCompressor getInstance() { return new ParallelZipCompressor(); }

    @Override
    protected OutputStream createArchiveOutputStream(OutputStream stream, ExecutableBackup ctx, int coreLimit) {
        executorService = Executors.newFixedThreadPool(coreLimit, r -> {
            Thread t = new Thread(r, "Mochi-Zip-Worker");
            t.setDaemon(true);
            return t;
        });
        scatterZipCreator = new ParallelScatterZipCreator(executorService);
        return super.createArchiveOutputStream(stream, ctx, coreLimit);
    }

    @Override
    protected void close() {
        ExecutorService executor = executorService;
        executorService = null;
        if (executor != null) executor.shutdownNow();
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
                accountSkippedFile(input, file);
                return;
            }
            if (ZipCompressor.isDotDat(file.toString())) {
                long size;
                try {
                    size = Files.size(file);
                } catch (java.nio.file.NoSuchFileException e) {
                    accountSkippedFile(input, file);
                    return;
                }
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(size);
                entry.setCompressedSize(size);
                try {
                    entry.setCrc(getCRC(file));
                } catch (java.nio.file.NoSuchFileException e) {
                    accountSkippedFile(input, file);
                    return;
                }
            } else {
                entry.setMethod(ZipEntry.DEFLATED);
            }
        }
        entry.setTime(System.currentTimeMillis());
        scatterZipCreator.addArchiveEntry(entry, input);
    }

    @Override
    protected void finish(OutputStream arc) throws InterruptedException, IOException, ExecutionException {
        try {
            scatterZipCreator.writeTo((ZipArchiveOutputStream) arc);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            // Only report "out of space" when that is actually what happened; a file that
            // vanished must not be blamed on the disk.
            if (isOutOfSpace(cause)) {
                MochiClient.LOGGER.error("Parallel ZIP write failed: no space left on device", cause);
                throw new NoSpaceLeftOnDeviceException(cause);
            }
            MochiClient.LOGGER.error("Parallel ZIP write failed", cause);
            throw e;
        }
    }

    private static boolean isOutOfSpace(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            String message = c.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("space")) return true;
            if (c instanceof java.nio.file.FileSystemException fse) {
                String reason = fse.getReason();
                if (reason != null && reason.toLowerCase(Locale.ROOT).contains("space")) return true;
            }
        }
        return false;
    }
}
