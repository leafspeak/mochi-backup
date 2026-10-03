package com.mochi.backup.core.restore.decompressors;

import com.mochi.backup.MochiClient;
import com.mochi.backup.MochiLogger;
import com.mochi.backup.Utilities;
import com.mochi.backup.core.digest.FileTreeHashBuilder;
import com.mochi.backup.core.digest.HashingOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.IOUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;

public class ZipDecompressor {
    private final static MochiLogger log = new MochiLogger(MochiClient.MOD_ID);

    public static long decompress(Path inputFile, Path target) throws IOException {
        Instant start = Instant.now();
        FileTreeHashBuilder hashBuilder = new FileTreeHashBuilder(0);

        try (ZipFile zipFile = new ZipFile(inputFile.toFile())) {
            for (Iterator<ZipArchiveEntry> it = zipFile.getEntries().asIterator(); it.hasNext();) {
                ZipArchiveEntry entry = it.next();
                Path file = target.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(file);
                } else {
                    Files.createDirectories(file.getParent());
                    try (OutputStream os = Files.newOutputStream(file);
                         HashingOutputStream out = new HashingOutputStream(os, file, hashBuilder);
                         InputStream in = zipFile.getInputStream(entry)) {
                        IOUtils.copy(in, out);
                    }
                }
            }
        }

        log.info("Decompression took: {}", Utilities.formatDuration(Duration.between(start, Instant.now())));
        try { return hashBuilder.getValue(false); }
        catch (InterruptedException ignored) { return 0; }
    }
}
