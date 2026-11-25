package com.mochi.backup.core.digest;

import com.mochi.backup.Globals;
import com.mochi.backup.MochiClient;
import com.mochi.backup.core.CompressionStatus;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class FileTreeHashBuilder {
    /**
     * Upper bound on how long a backup may wait for the tree hash to settle.
     * Without this, a file that is skipped without being accounted for would block
     * the (single threaded) backup executor forever.
     */
    private static final long AWAIT_TIMEOUT_MINUTES = 5;

    private final Object lock = new Object();
    private long hash = 0, filesProcessed = 0, filesTotalSize = 0;
    private final CountDownLatch latch;
    /** Guards against the same file being accounted for twice (see AbstractCompressor / FileInputStreamSupplier). */
    private final Set<Path> accounted = ConcurrentHashMap.newKeySet();

    public FileTreeHashBuilder(int filesToProcess) {
        latch = new CountDownLatch(filesToProcess);
    }

    public void update(Path path, long newHash, long bytes) throws IOException {
        if (path.getFileName().toString().equals(CompressionStatus.DATA_FILENAME)) return;
        // A file may be reported by both the stream supplier and the compressor's
        // error handler; only the first report may affect the result.
        if (!accounted.add(path)) return;
        synchronized (lock) {
            this.hash ^= newHash;
            this.filesTotalSize += bytes;
            this.filesProcessed++;
        }
        // Release the latch only after the mutation, so a thread returning from
        // await() always observes a fully updated accumulator.
        latch.countDown();
    }

    public long getValue(boolean wait) throws InterruptedException {
        if (wait) {
            if (!latch.await(AWAIT_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                MochiClient.LOGGER.warn("Hash tree incomplete: {} file(s) were never processed; the backup may be incomplete",
                        latch.getCount());
            }
        } else if (latch.getCount() != 0) {
            MochiClient.LOGGER.warn("Finishing with {} files unprocessed!", latch.getCount());
        }

        final long snapshotHash, snapshotProcessed, snapshotSize;
        synchronized (lock) {
            snapshotHash = hash;
            snapshotProcessed = filesProcessed;
            snapshotSize = filesTotalSize;
        }

        var hasher = Globals.CHECKSUM_SUPPLIER.get();
        hasher.update(snapshotHash);
        hasher.update(snapshotProcessed);
        hasher.update(snapshotSize);
        return hasher.getValue();
    }
}
