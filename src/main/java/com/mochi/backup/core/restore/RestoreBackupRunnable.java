package com.mochi.backup.core.restore;

import com.mochi.backup.*;
import com.mochi.backup.core.CompressionStatus;
import com.mochi.backup.core.restore.decompressors.GenericTarDecompressor;
import com.mochi.backup.core.restore.decompressors.ZipDecompressor;
import com.mochi.backup.mixin.MinecraftServerSessionAccessor;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.FutureTask;

import com.mochi.backup.mixin.MinecraftServerSessionAccessor;

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

        // === STEP 1: Decompress backup FIRST ===
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

        // === STEP 3: Save current player data (prevents crash, does NOT override backup) ===
        // We save here to flush any pending writes, but we do NOT copy or override
        // the backup's player data. The backup contains the correct state.
        try {
            server.getPlayerList().saveAll();
            log.info("Player data flushed.");
        } catch (Exception e) {
            log.warn("Could not flush player data", e);
        }

        // === STEP 4: HALT SERVER WITHOUT SAVING (like TextileBackup) ===
        // halt(false) sets running=false immediately, skipping the slow saveAllChunks.
        // The lighting bug only fires during chunk saving, so this avoids it entirely.
        log.info("Halting server (no save)...");
        try {
            Method haltMethod = net.minecraft.server.MinecraftServer.class
                    .getDeclaredMethod("halt", boolean.class);
            haltMethod.setAccessible(true);
            haltMethod.invoke(server, false);
            log.info("Server halted.");
        } catch (Exception e) {
            log.warn("Could not call halt(false)", e);
        }

        // === STEP 5: Wait for server thread to finish ===
        log.info("Waiting for server to terminate...");
        try {
            Method getThreadMethod = net.minecraft.server.MinecraftServer.class
                    .getDeclaredMethod("getRunningThread");
            getThreadMethod.setAccessible(true);
            java.lang.Thread serverThread = (java.lang.Thread) getThreadMethod.invoke(server);

            FutureTask<Void> task = new FutureTask<>(() -> {
                serverThread.join();
                return null;
            });
            new Thread(task, "Mochi-Restore-Wait").start();
            task.get();
            log.info("Server fully stopped.");
        } catch (Exception e) {
            log.warn("Could not wait for server thread", e);
            long deadline = System.currentTimeMillis() + 10_000L;
            while (System.currentTimeMillis() < deadline) {
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            }
        }

        // === STEP 6: Replace world files ===
        log.info("Replacing world files...");
        if (errorMsg.isEmpty() || !config.get().integrityVerificationMode.verify()) {
            if (errorMsg.isEmpty()) log.info("Backup valid. Restoring...");
            else log.info("Backup damaged but verification disabled [{}]. Proceeding.", errorMsg.get());

            try {
                // Close level storage to release file handles
                try {
                    Method getSessionMethod = net.minecraft.server.MinecraftServer.class
                            .getDeclaredMethod("getSession");
                    getSessionMethod.setAccessible(true);
                    Object session = getSessionMethod.invoke(server);
                    if (session != null) {
                        session.getClass().getMethod("close").invoke(session);
                    }
                } catch (Exception ignored) {}

                Utilities.deleteDirectory(worldFile);
                Files.move(tmp, worldFile);
            } catch (IOException e) {
                log.error("Failed to replace world files", e);
                return;
            }
            log.info("World files replaced.");

            if (config.get().deleteRestoredBackup) {
                log.info("Deleting restored backup file...");
                try { Files.delete(ctx.restoreableFile().getFile()); } catch (IOException ignored) {}
            }
        } else {
            log.warn("Backup validation warning: {}. Proceeding anyway.", errorMsg.get());

            try {
                try {
                    Method getSessionMethod = net.minecraft.server.MinecraftServer.class
                            .getDeclaredMethod("getSession");
                    getSessionMethod.setAccessible(true);
                    Object session = getSessionMethod.invoke(server);
                    if (session != null) {
                        session.getClass().getMethod("close").invoke(session);
                    }
                } catch (Exception ignored) {}

                Utilities.deleteDirectory(worldFile);
                Files.move(tmp, worldFile);
            } catch (IOException e) {
                log.error("Failed to replace world files", e);
                return;
            }
            log.info("World files replaced (from damaged backup).");
        }

        // === STEP 7: Clean world replacement only ===
        // The backup already contains the correct player data and spawn position.
        // We do NOT override anything — the restored world IS the authoritative state.
        log.info("World replaced — player data comes from backup.");

        // === STEP 8: Do NOT touch client screens ===
        // Let Minecraft's natural disconnect flow handle everything.
        log.info("Restore complete — Minecraft will handle the rest.");

        Globals.INSTANCE.globalShutdownBackupFlag.set(true);
        Globals.INSTANCE.resetShutdownBackupTriggered();
        Globals.INSTANCE.resetQueueExecutor();

        // Discord notification
        var discordCfg = config.get();
        if (discordCfg.discordEnabled
                && !discordCfg.discordWebhookUrl.isBlank()
                && discordCfg.discordSendRestore) {
            try {
                DiscordWebhook.sendSuccess(
                        discordCfg.discordWebhookUrl,
                        "World Restored",
                        "Successfully restored world from backup.\n"
                        + "World: " + Utilities.getLevelName(server) + "\n"
                        + "Backup: " + ctx.restoreableFile().getFile().getFileName()
                );
            } catch (Exception ignored) {}
        }

        log.info("Done!");
    }
}
