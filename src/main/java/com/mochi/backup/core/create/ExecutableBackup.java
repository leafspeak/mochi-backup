package com.mochi.backup.core.create;

import com.mochi.backup.*;
import com.mochi.backup.ActionInitiator;
import com.mochi.backup.Globals;
import com.mochi.backup.MochiConfigHelper;
import com.mochi.backup.core.*;
import com.mochi.backup.Utilities;
import com.mochi.backup.core.create.compressors.ParallelZipCompressor;
import com.mochi.backup.core.create.compressors.ZipCompressor;
import com.mochi.backup.core.create.compressors.tar.AbstractTarArchiver;
import com.mochi.backup.core.create.compressors.tar.ParallelGzipCompressor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

public record ExecutableBackup(@NotNull MinecraftServer server,
                               CommandSourceStack commandSource,
                               ActionInitiator initiator,
                               boolean save,
                               boolean cleanup,
                               String comment,
                               LocalDateTime startDate) implements Callable<Void> {

    private final static MochiLogger log = new MochiLogger(MochiClient.MOD_ID);
    private final static MochiConfigHelper config = MochiConfigHelper.INSTANCE;

    public boolean startedByPlayer() { return initiator == ActionInitiator.Player; }

    public void announce() {
        String msg = "Server backup will begin shortly. You may experience some lag.";
        if (config.get().broadcastBackupStart) {
            if (commandSource != null && commandSource.isPlayer()) {
                log.sendInfo(commandSource, msg);
            } else {
                Utilities.notifyPlayers(server, msg);
            }
        } else if (commandSource != null) {
            log.sendInfo(commandSource, msg);
        } else {
            log.info(msg);
        }

        StringBuilder b = new StringBuilder();
        b.append("Backup started ").append(initiator.getPrefix());
        if (startedByPlayer() && commandSource != null) b.append(commandSource.getTextName());
        else b.append(initiator.getName());
        b.append(" on: ").append(Utilities.getDateTimeFormatter(config.get()).format(LocalDateTime.now()));
        log.info(b.toString());

        // Discord notification on start
        var discordCfg = config.get();
        if (discordCfg.discordEnabled
                && !discordCfg.discordWebhookUrl.isBlank()
                && discordCfg.discordSendBackupStart) {
            String player = (commandSource != null && commandSource.isPlayer()) ? commandSource.getTextName() : "Server";
            DiscordWebhook.sendWarn(
                    discordCfg.discordWebhookUrl,
                    "Backup Started",
                    "**" + player + "** initiated a backup\n"
                    + (comment != null && !comment.isBlank() ? "Comment: `" + comment + "`\n" : "")
                    + "World: " + Utilities.getLevelName(server)
            );
        }
    }

    @Override
    public Void call() throws Exception {
        Path outFile = Utilities.getBackupRootPath(config.get(), Utilities.getLevelName(server), server.getServerDirectory())
                .resolve(getFileName());
        log.info("Backup saving to: {}", outFile);

        log.trace("Outfile: {}", outFile);
        AtomicReference<Optional<WorldSavingState>> stateRef = new AtomicReference<>(Optional.empty());
        Path ownedFile = null;

        try {
            Globals.INSTANCE.setBackupRunning(true);
            // Save must happen on the server tick thread; queue it and wait for completion
            java.util.concurrent.atomic.AtomicReference<Throwable> saveError = new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.CountDownLatch saveDone = new java.util.concurrent.CountDownLatch(1);
            boolean taskQueued = false;
            try {
                server.executeIfPossible(() -> {
                    try {
                        if (save) {
                            if (commandSource != null) log.sendInfo(commandSource, "Saving server...");
                            else log.info("Saving server...");
                            // Save BOTH chunks AND player data before backup
                            server.getPlayerList().saveAll();
                            server.saveAllChunks(true, true, false);
                        }
                        stateRef.set(Optional.of(WorldSavingState.disable(server)));
                    } catch (Throwable t) {
                        saveError.set(t);
                    } finally {
                        saveDone.countDown();
                    }
                });
                taskQueued = true;
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
                // Server is already shutting down or dead — cannot save.
                // This is normal during shutdown; the world data is already on disk.
                if (commandSource != null) log.sendInfo(commandSource, "Server shutting down — skipping save phase.");
                else log.info("Server shutting down — skipping save phase.");
            }
            // Wait for the queued save to actually finish instead of sleeping a fixed 10 seconds.
            if (taskQueued) {
                try {
                    if (!saveDone.await(60, java.util.concurrent.TimeUnit.SECONDS)) {
                        log.warn("Timed out after 60s waiting for the server save to finish");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            Throwable err = saveError.get();
            if (err != null) throw new RuntimeException("Server save failed", err);

            Globals.INSTANCE.updateTMPFSFlag(server);
            if (commandSource != null) log.sendInfo(commandSource, "Starting backup");
            else log.info("Starting backup");

            Path world = Utilities.getWorldFolder(server);
            Files.createDirectories(outFile.getParent());
            // Never overwrite an existing backup: two backups in the same second used to collide,
            // and the failure path then deleted the earlier, perfectly valid archive.
            outFile = reserveUniqueFile(outFile);
            ownedFile = outFile;

            int coreCount = config.get().compressionCoreCountLimit <= 0
                    ? Runtime.getRuntime().availableProcessors()
                    : Math.min(config.get().compressionCoreCountLimit, Runtime.getRuntime().availableProcessors());

            log.debug("Compression threads: {}, available: {}", coreCount, Runtime.getRuntime().availableProcessors());

            switch (config.get().format) {
                case ZIP -> {
                    if (coreCount > 1 && !Globals.INSTANCE.disableTMPFS())
                        ParallelZipCompressor.getInstance().createArchive(world, outFile, this, coreCount);
                    else
                        ZipCompressor.getInstance().createArchive(world, outFile, this, coreCount);
                }
                case GZIP -> ParallelGzipCompressor.getInstance().createArchive(world, outFile, this, coreCount);
                case TAR -> new AbstractTarArchiver().createArchive(world, outFile, this, coreCount);
            }

            if (cleanup) new Cleanup(commandSource, Utilities.getLevelName(server), server.getServerDirectory()).call();

            String doneMsg = "Backup complete!";
            if (config.get().broadcastBackupDone) {
                if (commandSource != null && commandSource.isPlayer())
                    log.sendInfo(commandSource, doneMsg);
                else
                    Utilities.notifyPlayers(server, doneMsg);
            } else if (commandSource != null) {
                log.sendInfo(commandSource, doneMsg);
            }

            // Discord notification on success
            var discordCfg = config.get();
            if (discordCfg.discordEnabled
                    && !discordCfg.discordWebhookUrl.isBlank()
                    && discordCfg.discordSendBackupDone) {
                String fname = outFile.getFileName().toString();
                DiscordWebhook.sendSuccess(
                        discordCfg.discordWebhookUrl,
                        "Backup Complete",
                        "**" + fname + "**\nWorld: " + Utilities.getLevelName(server)
                                + "\nDuration: " + Utilities.formatDuration(
                                    java.time.Duration.between(startDate, java.time.LocalDateTime.now()))
                );
            }

        } catch (Throwable e) {
            log.error("Backup creation failed!", e);
            // Discord notification on failure
            var discordCfg = config.get();
            if (discordCfg.discordEnabled
                    && !discordCfg.discordWebhookUrl.isBlank()
                    && discordCfg.discordSendBackupFailed) {
                DiscordWebhook.sendError(
                        discordCfg.discordWebhookUrl,
                        "Backup Failed",
                        "An error occurred while creating the backup.\n```"
                        + describe(e) + "```"
                );
            }
            // A partial archive must never be left behind under a valid backup name, in any mode.
            // Only delete a file that this invocation actually created.
            if (ownedFile != null) {
                try { Files.deleteIfExists(ownedFile); }
                catch (IOException ex) { log.error("Failed to delete partial backup: {}", ownedFile, ex); }
            }
            if (initiator == ActionInitiator.Player && commandSource != null)
                log.sendError(commandSource, "Backup creation failed!");
            throw e;
        } finally {
            if (stateRef.get().isPresent()) stateRef.get().get().enable(server);
            Globals.INSTANCE.setBackupRunning(false);
        }
        return null;
    }

    /** Creates the output file, appending a counter if a backup with that name already exists. */
    private static Path reserveUniqueFile(Path desired) throws IOException {
        try {
            return Files.createFile(desired);
        } catch (java.nio.file.FileAlreadyExistsException e) {
            String name = desired.getFileName().toString();
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            String extension = dot > 0 ? name.substring(dot) : "";
            for (int i = 2; i < 1000; i++) {
                Path candidate = desired.resolveSibling(base + "-" + i + extension);
                try {
                    return Files.createFile(candidate);
                } catch (java.nio.file.FileAlreadyExistsException ignored) {
                    // try the next index
                }
            }
            throw e;
        }
    }

    /** Null-safe, bounded description of a throwable for user-facing text. */
    private static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) message = throwable.getClass().getName();
        return message.length() > 1024 ? message.substring(0, 1024) : message;
    }

    private String getFileName() {
        return Utilities.getDateTimeFormatter(config.get()).format(startDate)
                + (comment != null ? "#" + comment.replaceAll("[\\\\/:*?\"<>|#]", "") : "")
                + config.get().format.getCompleteString();
    }

    public static class Builder {
        private MinecraftServer server;
        private CommandSourceStack commandSource;
        private ActionInitiator initiator;
        private boolean save;
        private boolean cleanup = true;
        private String comment;
        private boolean announce;
        private boolean guessInitiator;

        public static Builder newBackupContextBuilder() { return new Builder(); }
        public Builder setCommandSource(CommandSourceStack s) { commandSource = s; return this; }
        public Builder setServer(MinecraftServer s) { server = s; return this; }
        public Builder setInitiator(ActionInitiator i) { initiator = i; return this; }
        public Builder setComment(String c) { comment = c; return this; }
        public Builder guessInitiator() { guessInitiator = true; return this; }
        public Builder saveServer() { save = true; return this; }
        public Builder noCleanup() { cleanup = false; return this; }
        public Builder announce() { announce = true; return this; }

        public ExecutableBackup build() {
            if (guessInitiator)
                initiator = commandSource != null && commandSource.isPlayer()
                        ? ActionInitiator.Player : ActionInitiator.ServerConsole;
            else if (initiator == null) throw new NoSuchElementException("No initiator provided!");

            if (server == null) {
                if (commandSource != null) server = commandSource.getServer();
                else throw new RuntimeException("Neither server nor commandSource provided!");
            }

            ExecutableBackup v = new ExecutableBackup(server, commandSource, initiator, save, cleanup, comment, LocalDateTime.now());
            if (announce) v.announce();
            return v;
        }
    }
}
