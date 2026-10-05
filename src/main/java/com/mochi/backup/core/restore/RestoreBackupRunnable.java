package com.mochi.backup.core.restore;

import com.mochi.backup.*;
import com.mochi.backup.core.CompressionStatus;
import com.mochi.backup.core.restore.decompressors.GenericTarDecompressor;
import com.mochi.backup.core.restore.decompressors.ZipDecompressor;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.FutureTask;

public class RestoreBackupRunnable implements Runnable {
    private final static MochiLogger log = new MochiLogger(MochiClient.MOD_ID);
    private final static MochiConfigHelper config = MochiConfigHelper.INSTANCE;

    private final RestoreContext ctx;

    public RestoreBackupRunnable(RestoreContext ctx) { this.ctx = ctx; }

    @Override
    public void run() {
        Globals.INSTANCE.globalShutdownBackupFlag.set(false);
        Globals.INSTANCE.resetShutdownBackupTriggered();
        log.info("Shutting down server...");

        final net.minecraft.server.MinecraftServer server = ctx.server();
        Path worldFile = Utilities.getWorldFolder(server);
        Path tmp;
        try {
            tmp = Files.createTempDirectory(server.getServerDirectory().toAbsolutePath(), "mochi_restore_");
        } catch (IOException e) {
            log.error("Failed to create temp dir", e);
            return;
        }

        // === STEP 1: Decompress backup ===
        long hash;
        try {
            if (ctx.restoreableFile().getArchiveFormat() == MochiConfig.ArchiveFormat.ZIP) {
                hash = ZipDecompressor.decompress(ctx.restoreableFile().getFile(), tmp);
            } else {
                hash = GenericTarDecompressor.decompress(ctx.restoreableFile().getFile(), tmp);
            }
        } catch (IOException e) {
            log.error("Failed to decompress backup", e);
            return;
        }
        log.info("Decompression complete.");

        // === STEP 2: Validate backup ===
        Optional<String> errorMsg;
        try {
            if (Files.notExists(CompressionStatus.resolveStatusFilename(tmp))) {
                errorMsg = Optional.of("No integrity status file. Proceeding anyway.");
            } else {
                CompressionStatus status = CompressionStatus.readFromFile(tmp);
                log.info("Integrity: treeHash=0x" + Long.toHexString(status.treeHash()).toUpperCase());
                try { Files.delete(tmp.resolve(CompressionStatus.DATA_FILENAME)); } catch (IOException ignored) {}
                errorMsg = status.validate(hash, ctx);
            }
        } catch (Exception e) {
            log.warn("Integrity check failed, proceeding anyway", e);
            errorMsg = Optional.empty();
        }

        // === STEP 3: Halt server ===
        log.info("Halting server...");
        try {
            Method haltMethod = net.minecraft.server.MinecraftServer.class.getDeclaredMethod("halt", boolean.class);
            haltMethod.setAccessible(true);
            haltMethod.invoke(server, false);
            log.info("Server halted.");
        } catch (Exception e) {
            log.warn("Could not call halt(false)", e);
        }

        // === STEP 4: Wait for server thread to die ===
        log.info("Waiting for server to stop...");
        try {
            Method getThread = net.minecraft.server.MinecraftServer.class.getDeclaredMethod("getRunningThread");
            getThread.setAccessible(true);
            Thread t = (Thread) getThread.invoke(server);
            FutureTask<Void> task = new FutureTask<>(() -> { t.join(); return null; });
            new Thread(task, "Mochi-Restore-Wait").start();
            task.get();
            log.info("Server stopped.");
        } catch (Exception e) {
            log.warn("Could not wait for server thread", e);
            long deadline = System.currentTimeMillis() + 10_000L;
            while (System.currentTimeMillis() < deadline) {
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            }
        }

        // === STEP 5: Replace world folder ===
        log.info("Replacing world...");
        try {
            try {
                Method getSession = net.minecraft.server.MinecraftServer.class.getDeclaredMethod("getSession");
                getSession.setAccessible(true);
                Object session = getSession.invoke(server);
                if (session != null) {
                    session.getClass().getMethod("close").invoke(session);
                }
            } catch (Exception ignored) {}
            Utilities.deleteDirectory(worldFile);
            Files.move(tmp, worldFile);
        } catch (IOException e) {
            log.error("Failed to replace world", e);
            return;
        }
        log.info("World replaced.");

        if (config.get().deleteRestoredBackup) {
            try { Files.delete(ctx.restoreableFile().getFile()); } catch (IOException ignored) {}
        }

        log.info("Restore complete.");

        Globals.INSTANCE.globalShutdownBackupFlag.set(true);
        Globals.INSTANCE.resetShutdownBackupTriggered();

        var discordCfg = config.get();
        if (discordCfg.discordEnabled && !discordCfg.discordWebhookUrl.isBlank() && discordCfg.discordSendRestore) {
            try {
                DiscordWebhook.sendSuccess(discordCfg.discordWebhookUrl, "World Restored",
                        "Successfully restored world from backup.\nWorld: " + Utilities.getLevelName(server));
            } catch (Exception ignored) {}
        }

        log.info("Done!");
    }
}
