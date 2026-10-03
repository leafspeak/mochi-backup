package com.mochi.backup.core.restore.decompressors;

import com.mochi.backup.MochiClient;
import com.mochi.backup.MochiLogger;
import com.mochi.backup.Utilities;
import com.mochi.backup.core.digest.FileTreeHashBuilder;
import com.mochi.backup.core.digest.HashingOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.compress.utils.IOUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

public class GenericTarDecompressor {
    private final static MochiLogger log = new MochiLogger(MochiClient.MOD_ID);

    public static long decompress(Path input, Path target) throws IOException {
        Instant start = Instant.now();
        FileTreeHashBuilder treeBuilder = new FileTreeHashBuilder(0);

        try (InputStream fis = Files.newInputStream(input);
             BufferedInputStream bis = new BufferedInputStream(fis);
             InputStream cis = getCompressorStream(bis);
             TarArchiveInputStream tar = new TarArchiveInputStream(cis)) {

            TarArchiveEntry entry;
            while ((entry = tar.getNextTarEntry()) != null) {
                if (!tar.canReadEntryData(entry))
                    throw new IOException("Cannot read entry: " + entry.getName());
                Path file = target.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(file);
                } else {
                    Files.createDirectories(file.getParent());
                    try (OutputStream os = Files.newOutputStream(file);
                         HashingOutputStream out = new HashingOutputStream(os, file, treeBuilder)) {
                        IOUtils.copy(tar, out);
                    }
                }
            }
        } catch (CompressorException e) { throw new IOException(e); }

        log.info("Decompression took: {}", Utilities.formatDuration(Duration.between(start, Instant.now())));
        try { return treeBuilder.getValue(false); }
        catch (InterruptedException ignored) { return 0; }
    }

    private static InputStream getCompressorStream(InputStream is) throws CompressorException {
        try {
            return new CompressorStreamFactory().createCompressorInputStream(is);
        } catch (CompressorException e) {
            byte[] header = new byte[512];
            is.mark(header.length);
            int sigLen;
            try { sigLen = IOUtils.readFully(is, header); }
            catch (IOException ex) { throw new CompressorException("Failed to read signature", ex); }
            try { is.reset(); } catch (IOException ignored) {}
            if (TarArchiveInputStream.matches(header, sigLen)) return is;
            throw e;
        }
    }
}
