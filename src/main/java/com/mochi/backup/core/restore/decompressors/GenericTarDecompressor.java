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
        Path root = target.toAbsolutePath().normalize();

        try (InputStream fis = Files.newInputStream(input);
             BufferedInputStream bis = new BufferedInputStream(fis);
             InputStream cis = getCompressorStream(bis);
             TarArchiveInputStream tar = new TarArchiveInputStream(cis)) {

            TarArchiveEntry entry;
            while ((entry = tar.getNextTarEntry()) != null) {
                if (!tar.canReadEntryData(entry))
                    throw new IOException("Cannot read entry: " + entry.getName());
                // Rejects "../" traversal, absolute paths and drive paths (Tar Slip).
                Path file = Utilities.resolveArchiveEntry(root, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(file);
                } else if (!file.equals(root)) {
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

    /**
     * Detects the compression wrapper, falling back to a raw TAR stream.
     * The stream is marked <em>before</em> the detection attempt so it can be rewound afterwards;
     * marking after a failed attempt would rewind to an already-consumed offset and silently
     * corrupt the extraction.
     */
    private static InputStream getCompressorStream(InputStream is) throws CompressorException, IOException {
        BufferedInputStream buffered = is instanceof BufferedInputStream b ? b : new BufferedInputStream(is);
        buffered.mark(512);
        try {
            return new CompressorStreamFactory().createCompressorInputStream(buffered);
        } catch (CompressorException e) {
            try {
                buffered.reset();
            } catch (IOException rewindFailure) {
                throw new CompressorException("Cannot rewind stream to test for a raw TAR archive", rewindFailure);
            }
            byte[] header = new byte[512];
            int read = 0;
            while (read < header.length) {
                int n = buffered.read(header, read, header.length - read);
                if (n < 0) break;
                read += n;
            }
            try { buffered.reset(); } catch (IOException ignored) {}
            if (read > 0 && TarArchiveInputStream.matches(header, read)) return buffered;
            throw e;
        }
    }
}
