package com.mochi.backup.core.restore;

import com.mochi.backup.*;
import com.mochi.backup.core.CompressionStatus;
import com.mochi.backup.core.create.ExecutableBackup;
import com.mochi.backup.core.restore.decompressors.GenericTarDecompressor;
import com.mochi.backup.core.restore.decompressors.ZipDecompressor;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class RestoreBackupRunnable implements Runnable {
    private final static MochiLogger log = new MochiLogger(MochiClient.MOD_ID);
    private final static MochiConfigHelper config = MochiConfigHelper.INSTANCE;

    private final RestoreContext ctx;

    public RestoreBackupRunnable(RestoreContext ctx) { this.ctx = ctx; }

    @Override
    public void run() {
        Globals.INSTANCE.globalShutdownBackupFlag.set(false);
        Globals.INSTANCE.resetShutdownBackupTriggered();

        final net.minecraft.server.MinecraftServer server = ctx.server();
        Path tempWorld = null;

        try {
            final Path worldFile = Utilities.getWorldFolder(server);
            if (worldFile == null) {
                fail("Could not resolve the world folder — restore cancelled.");
                return;
            }

            // === STEP 0: never fight a running backup for the world folder ===
            if (!Globals.INSTANCE.awaitBackupIdle(5, TimeUnit.MINUTES)) {
                fail("A backup is still running — restore cancelled. Your world was not touched.");
                return;
            }

            // === STEP 1: safety backup, on this thread so the server is not frozen ===
            if (config.get().backupBeforeRestore) {
                log.info("Creating safety backup before restore...");
                try {
                    ExecutableBackup.Builder.newBackupContextBuilder()
                            .setServer(server)
                            .setInitiator(ActionInitiator.Restore)
                            .setComment("before_restore")
                            .announce()
                            .build()
                            .call();
                    log.info("Safety backup created.");
                } catch (Exception e) {
                    log.error("Safety backup failed - aborting restore so the world stays untouched", e);
                    fail("The safety backup failed, so the restore was cancelled. Your world was not touched.");
                    return;
                }
            }

            // === STEP 2: decompress next to the world (same volume) ===
            try {
                tempWorld = Files.createTempDirectory(server.getServerDirectory().toAbsolutePath(), "mochi_restore_");
            } catch (IOException e) {
                log.error("Failed to create temp dir", e);
                fail("Could not create a temporary directory — restore cancelled.");
                return;
            }
            final Path extracted = tempWorld;

            long hash;
            try {
                if (ctx.restoreableFile().getArchiveFormat() == MochiConfig.ArchiveFormat.ZIP) {
                    hash = ZipDecompressor.decompress(ctx.restoreableFile().getFile(), extracted);
                } else {
                    hash = GenericTarDecompressor.decompress(ctx.restoreableFile().getFile(), extracted);
                }
            } catch (IOException e) {
                log.error("Failed to decompress backup", e);
                fail("Failed to decompress the backup — your world was not touched.");
                return;
            }
            log.info("Decompression complete.");

            // === STEP 3: integrity verification (result is actually used now) ===
            Optional<String> integrityError = validateIntegrity(extracted, hash);
            if (integrityError.isPresent()) {
                String reason = integrityError.get();
                log.warn("Integrity check reported: {}", reason);
                if (config.get().integrityVerificationMode.isStrict()) {
                    fail("Backup failed its integrity check and Integrity Mode is Strict:\n" + reason
                            + "\nYour world was not touched. Set Integrity Mode to Permissible to restore anyway.");
                    return;
                }
                notifyPlayers("Warning: backup integrity check reported: " + reason);
            }

            // === STEP 4: the extracted world must be complete before anything is replaced ===
            if (Files.notExists(extracted.resolve("level.dat"))) {
                log.error("Restore aborted: extracted backup has no level.dat");
                fail("The backup does not contain a complete world (level.dat is missing) — restore cancelled.");
                return;
            }

            // === STEP 5: halt the server ===
            log.info("Halting server...");
            try {
                Method haltMethod = net.minecraft.server.MinecraftServer.class.getDeclaredMethod("halt", boolean.class);
                haltMethod.setAccessible(true);
                haltMethod.invoke(server, false);
                log.info("Server halted.");
            } catch (Exception e) {
                log.warn("Could not call halt(false)", e);
            }

            // === STEP 6: wait for the server thread, bounded ===
            waitForServerThread(server);

            // === STEP 7: release the session (file handles) ===
            closeSession(server);

            // === STEP 8: swap the world, keeping the old one until the new one is verified ===
            log.info("Replacing world...");
            Path displacedWorld = worldFile.resolveSibling(worldFile.getFileName() + ".mochi-pre-restore");
            try {
                Utilities.deleteDirectory(displacedWorld); // leftover from an earlier failed attempt
            } catch (IOException ignored) {}
            try {
                Files.move(worldFile, displacedWorld);
            } catch (IOException e) {
                log.error("Failed to move the current world aside - restore cancelled", e);
                fail("Could not move the current world aside — restore cancelled. Your world is intact.");
                return;
            }
            try {
                Files.move(extracted, worldFile);
            } catch (IOException e) {
                log.error("Failed to install the restored world - rolling back", e);
                rollback(displacedWorld, worldFile);
                return;
            }
            if (Files.notExists(worldFile.resolve("level.dat"))) {
                log.error("Installed world has no level.dat - rolling back");
                try { Utilities.deleteDirectory(worldFile); } catch (IOException ignored) {}
                rollback(displacedWorld, worldFile);
                return;
            }
            try { Utilities.deleteDirectory(displacedWorld); } catch (IOException e) { log.warn("Could not remove old world copy {}", displacedWorld, e); }
            log.info("World replaced.");

            if (config.get().deleteRestoredBackup) {
                try { Files.delete(ctx.restoreableFile().getFile()); } catch (IOException ignored) {}
            }
            log.info("Restore complete.");

            var discordCfg = config.get();
            if (discordCfg.discordEnabled && !discordCfg.discordWebhookUrl.isBlank() && discordCfg.discordSendRestore) {
                try {
                    DiscordWebhook.sendSuccess(discordCfg.discordWebhookUrl, "World Restored",
                            "Successfully restored world from backup.\nWorld: " + Utilities.getLevelName(server));
                } catch (Exception ignored) {}
            }

            log.info("Done!");
        } finally {
            // The temp world is only left behind when the swap did not happen; never leak it.
            if (tempWorld != null && Files.exists(tempWorld)) {
                try { Utilities.deleteDirectory(tempWorld); }
                catch (IOException e) { log.warn("Could not remove temporary world {}", tempWorld, e); }
            }
            Globals.INSTANCE.setRestorePending(false);
            Globals.INSTANCE.setLockedFile(null);
            Globals.INSTANCE.globalShutdownBackupFlag.set(true);
            Globals.INSTANCE.resetShutdownBackupTriggered();
        }
    }

    /** Reads and validates the stored integrity status; empty means "no blocking problem". */
    private Optional<String> validateIntegrity(Path extracted, long hash) {
        if (config.get().integrityVerificationMode == MochiConfig.IntegrityVerificationMode.VERY_PERMISSIBLE) {
            log.info("Integrity verification skipped (Very Permissible).");
            return Optional.empty();
        }
        Path statusFile = CompressionStatus.resolveStatusFilename(extracted);
        if (Files.notExists(statusFile)) {
            log.warn("No integrity status file in the backup; proceeding without verification.");
            return Optional.empty();
        }
        try {
            CompressionStatus status = CompressionStatus.readFromFile(extracted);
            log.info("Integrity: treeHash=0x{}", Long.toHexString(status.treeHash()).toUpperCase());
            try { Files.delete(statusFile); } catch (IOException ignored) {}
            return status.validate(hash, ctx);
        } catch (Exception e) {
            // An unreadable/foreign status blob must not brick restores of otherwise valid archives.
            log.warn("Could not read the integrity status file; proceeding without verification", e);
            return Optional.empty();
        }
    }

    private void waitForServerThread(net.minecraft.server.MinecraftServer server) {
        log.info("Waiting for server to stop...");
        try {
            Method getThread = net.minecraft.server.MinecraftServer.class.getDeclaredMethod("getRunningThread");
            getThread.setAccessible(true);
            Thread t = (Thread) getThread.invoke(server);
            if (t == null) return;
            FutureTask<Void> task = new FutureTask<>(() -> { t.join(); return null; });
            Thread waiter = new Thread(task, "Mochi-Restore-Wait");
            waiter.setDaemon(true);
            waiter.start();
            task.get(60, TimeUnit.SECONDS);
            log.info("Server stopped.");
        } catch (TimeoutException e) {
            log.warn("Server thread did not stop within 60 seconds; continuing anyway");
        } catch (Exception e) {
            log.warn("Could not wait for the server thread", e);
            try { Thread.sleep(10_000L); } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void closeSession(net.minecraft.server.MinecraftServer server) {
        try {
            Method getSession = net.minecraft.server.MinecraftServer.class.getDeclaredMethod("getSession");
            getSession.setAccessible(true);
            Object session = getSession.invoke(server);
            if (session != null) session.getClass().getMethod("close").invoke(session);
        } catch (Exception ignored) {}
    }

    private void rollback(Path displacedWorld, Path worldFile) {
        try {
            Files.move(displacedWorld, worldFile);
            log.info("Rolled back to the previous world.");
        } catch (IOException rollbackFailure) {
            log.error("ROLLBACK FAILED - the previous world is still at {}", displacedWorld, rollbackFailure);
        }
    }

    private void fail(String playerMessage) {
        log.error("Restore failed: {}", playerMessage);
        notifyPlayers(playerMessage);
    }

    private void notifyPlayers(String message) {
        try {
            Utilities.notifyPlayers(ctx.server(), message);
        } catch (Exception ignored) {
            // The server may already be halted; the log line above is then the record.
        }
    }
}