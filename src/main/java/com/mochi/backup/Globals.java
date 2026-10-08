package com.mochi.backup;

import net.minecraft.server.MinecraftServer;
import com.mochi.backup.core.digest.BalticHash;
import com.mochi.backup.core.Hash;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class Globals {
    public static final Globals INSTANCE = new Globals();
    public static final DateTimeFormatter defaultDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    public static final Supplier<Hash> CHECKSUM_SUPPLIER = BalticHash::new;

    private ExecutorService executorService = null;
    public final AtomicBoolean globalShutdownBackupFlag = new AtomicBoolean(true);
    // Prevents triggering the shutdown-backup task multiple ticks in a row as the server winds down.
    private volatile boolean shutdownBackupTriggered = false;
    public boolean disableWatchdog = false;
    private boolean disableTMPFiles = false;
    private com.mochi.backup.core.restore.AwaitThread restoreAwaitThread = null;
    private Path lockedPath = null;
    private String combinedVersionString;
    // Cache world size to avoid re-scanning on every start
    private volatile Long cachedWorldSize = null;

    private Globals() {}

    public ExecutorService getQueueExecutor() { return executorService; }

    public void resetQueueExecutor() {
        if (Objects.nonNull(executorService) && !executorService.isShutdown()) return;
        executorService = Executors.newSingleThreadExecutor();
    }

    public void shutdownQueueExecutor(long timeout) {
        if (executorService.isShutdown()) return;
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(timeout, TimeUnit.MICROSECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            MochiClient.LOGGER.error("Interrupted while waiting for executor", e);
        }
    }

    // Atomic one-shot: only one caller wins; resets when a new server session starts.
    public boolean tryMarkShutdownBackupTriggered() {
        if (shutdownBackupTriggered) return false;
        synchronized (this) {
            if (shutdownBackupTriggered) return false;
            shutdownBackupTriggered = true;
            return true;
        }
    }
    public void resetShutdownBackupTriggered() {
        shutdownBackupTriggered = false;
    }

    public Optional<com.mochi.backup.core.restore.AwaitThread> getAwaitThread() { return Optional.ofNullable(restoreAwaitThread); }
    public void setAwaitThread(com.mochi.backup.core.restore.AwaitThread th) { restoreAwaitThread = th; }

    public Optional<Path> getLockedFile() { return Optional.ofNullable(lockedPath); }
    public void setLockedFile(Path p) { lockedPath = p; }

    public synchronized boolean disableTMPFS() { return disableTMPFiles; }

    public synchronized void updateTMPFSFlag(MinecraftServer server) {
        disableTMPFiles = false;
        Path tmpDir = Path.of(System.getProperty("java.io.tmpdir"));
        if (!Files.isWritable(tmpDir)) {
            MochiClient.LOGGER.warn("TMP filesystem ({}) is read-only!", tmpDir);
            disableTMPFiles = true;
            return;
        }
        // Only check world size for parallel backup formats that write temp scatter files.
        // ZIP streams directly to output — no temp files needed, skip the expensive scan.
        // Cache the result so we don't re-scan on every start.
        long worldSize = getWorldSizeCached(server);
        long tmpFree = tmpDir.toFile().getUsableSpace();
        // Use a conservative threshold: disable temp files only if world exceeds 80% of tmp space
        if (worldSize > tmpFree * 4 / 5) {
            MochiClient.LOGGER.warn("TMP dir too small for parallel backup! tmp={}B world={}B", tmpFree, worldSize);
            disableTMPFiles = true;
        } else if (worldSize > 0) {
            MochiClient.LOGGER.debug("World size {}B, tmp free {}B — temp files OK", worldSize, tmpFree);
        }
    }

    /** Returns cached world size; scans once and stores result. */
    private long getWorldSizeCached(MinecraftServer server) {
        if (cachedWorldSize != null) return cachedWorldSize;
        try {
            cachedWorldSize = worldSize(Utilities.getWorldFolder(server));
        } catch (Exception e) {
            MochiClient.LOGGER.warn("Failed to calc world size, defaulting to 0", e);
            cachedWorldSize = 0L;
        }
        return cachedWorldSize;
    }

    private long worldSize(Path worldDir) throws Exception {
        long[] size = {0};
        try (var stream = Files.walk(worldDir)) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                try { size[0] += Files.size(p); } catch (Exception ignored) {}
            });
        }
        return size[0];
    }

    public String getCombinedVersionString() { return combinedVersionString; }
    public void setCombinedVersionString(String s) { this.combinedVersionString = s; }
}
