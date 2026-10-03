package com.mochi.backup.core.create.compressors;

import com.mochi.backup.MochiClient;
import com.mochi.backup.MochiConfigHelper;
import com.mochi.backup.ActionInitiator;
import com.mochi.backup.Globals;
import com.mochi.backup.Utilities;
import com.mochi.backup.core.BrokenFileHandler;
import com.mochi.backup.core.CompressionStatus;
import com.mochi.backup.core.create.ExecutableBackup;
import com.mochi.backup.core.create.FileInputStreamSupplier;
import com.mochi.backup.core.create.InputSupplier;
import com.mochi.backup.core.digest.FileTreeHashBuilder;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

public abstract class AbstractCompressor {
    protected abstract OutputStream createArchiveOutputStream(OutputStream stream, ExecutableBackup ctx, int coreLimit) throws IOException;
    protected abstract void addEntry(InputSupplier inputSupplier, OutputStream arc) throws IOException;
    protected void finish(OutputStream arc) throws InterruptedException, ExecutionException, IOException {}
    protected void close() {}

    public void createArchive(Path inputFile, Path outputFile, ExecutableBackup ctx, int coreLimit) throws IOException, ExecutionException, InterruptedException {
        Instant start = Instant.now();
        BrokenFileHandler brokenFileHandler = new BrokenFileHandler();

        try (OutputStream outStream = Files.newOutputStream(outputFile);
             BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outStream);
             OutputStream arc = createArchiveOutputStream(bufferedOutputStream, ctx, coreLimit);
             Stream<Path> fileStream = Files.walk(inputFile)) {

            var fileList = fileStream
                    .filter(path -> !Utilities.isBlacklisted(path, MochiConfigHelper.INSTANCE.get()))
                    .filter(Files::isRegularFile)
                    .toList();

            FileTreeHashBuilder fileHashBuilder = new FileTreeHashBuilder(fileList.size());

            for (Path file : fileList) {
                try {
                    addEntry(new FileInputStreamSupplier(file, inputFile.relativize(file).toString().replace('\\', '/'), fileHashBuilder, brokenFileHandler), arc);
                } catch (IOException e) {
                    brokenFileHandler.handle(file, e);
                    fileHashBuilder.update(file, 0, 0);
                    if (MochiConfigHelper.INSTANCE.get().integrityVerificationMode.isStrict()) throw e;
                    else MochiClient.LOGGER.warn("Skipping broken file: {}", file.getFileName());
                }
            }

            arc.flush();
            Instant now = Instant.now();
            long treeHash = fileHashBuilder.getValue(true);
            Map<String, String> brokenFilesMap = new HashMap<>();
            for (Map.Entry<String, Exception> entry : brokenFileHandler.get().entrySet()) brokenFilesMap.put(entry.getKey(), entry.getValue().toString());
            CompressionStatus status = new CompressionStatus(treeHash, brokenFilesMap, ctx.startDate(), start.toEpochMilli(), now.toEpochMilli(), com.mochi.backup.Globals.INSTANCE.getCombinedVersionString());
            addEntry(new StatusInputSupplier(status.serialize()), arc);
            finish(arc);
        } finally { close(); }

        MochiClient.LOGGER.info("Compression took: {}", Utilities.formatDuration(Duration.between(start, Instant.now())));
    }

    private record StatusInputSupplier(byte[] data) implements InputSupplier {
        public InputStream getInputStream() { return new ByteArrayInputStream(data); }
        public Optional<Path> getPath() { return Optional.empty(); }
        public String getName() { return CompressionStatus.DATA_FILENAME; }
        public long size() { return data.length; }
        public InputStream get() { return getInputStream(); }
    }
}
