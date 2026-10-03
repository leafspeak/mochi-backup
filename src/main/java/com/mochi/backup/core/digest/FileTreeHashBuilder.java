package com.mochi.backup.core.digest;

import com.mochi.backup.Globals;
import com.mochi.backup.MochiClient;
import com.mochi.backup.core.CompressionStatus;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

public class FileTreeHashBuilder {
    private final Object lock = new Object();
    private long hash = 0, filesProcessed = 0, filesTotalSize = 0;
    private final CountDownLatch latch;

    public FileTreeHashBuilder(int filesToProcess) {
        latch = new CountDownLatch(filesToProcess);
    }

    public void update(Path path, long newHash, long bytes) throws IOException {
        if (path.getFileName().toString().equals(CompressionStatus.DATA_FILENAME)) return;
        latch.countDown();
        synchronized (lock) {
            this.hash ^= newHash;
            this.filesTotalSize += bytes;
            this.filesProcessed++;
        }
    }

    public int getRemaining() { return (int) latch.getCount(); }

    public long getValue(boolean wait) throws InterruptedException {
        if (wait) latch.await();
        else if (latch.getCount() != 0)
            MochiClient.LOGGER.warn("Finishing with {} files unprocessed!", latch.getCount());

        var hasher = Globals.CHECKSUM_SUPPLIER.get();
        hasher.update(hash);
        hasher.update(filesProcessed);
        hasher.update(filesTotalSize);
        return hasher.getValue();
    }
}
