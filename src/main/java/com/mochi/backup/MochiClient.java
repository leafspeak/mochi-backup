package com.mochi.backup;

import com.mochi.backup.commands.create.CleanupCommand;
import com.mochi.backup.commands.create.StartBackupCommand;
import com.mochi.backup.commands.manage.BlacklistCommand;
import com.mochi.backup.commands.manage.DeleteCommand;
import com.mochi.backup.commands.manage.ListBackupsCommand;
import com.mochi.backup.commands.manage.WhitelistCommand;
import com.mochi.backup.commands.restore.KillRestoreCommand;
import com.mochi.backup.commands.restore.RestoreBackupCommand;
import com.mochi.backup.core.create.BackupScheduler;
import com.mochi.backup.core.create.ExecutableBackup;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MochiClient implements ModInitializer {
    public static final String MOD_ID = "mochi";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static MochiConfig CONFIG;
    public static BackupManager BACKUP_MANAGER;
    public static RestoreManager RESTORE_MANAGER;

    @Override
    public void onInitialize() {
        migrateConfig();

        MochiConfigHelper.updateInstance(AutoConfig.register(MochiConfig.class, JanksonConfigSerializer::new));
        CONFIG = MochiConfigHelper.INSTANCE.get();

        BACKUP_MANAGER = new BackupManager(CONFIG);
        RESTORE_MANAGER = new RestoreManager(CONFIG);

        Globals.INSTANCE.setCombinedVersionString(
                FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow().getMetadata().getVersion().getFriendlyString()
                + ":" +
                FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString()
        );

        ServerTickEvents.END_SERVER_TICK.register(BackupScheduler::tick);

        // Lifecycle events
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            Globals.INSTANCE.resetQueueExecutor();
            Globals.INSTANCE.resetShutdownBackupTriggered();
            Globals.INSTANCE.updateTMPFSFlag(server);
            com.mochi.backup.core.create.BackupScheduler.reset();
            LOGGER.info("[Mochi] SERVER_STARTING — executor reset, backup flag enabled");
        });

        // PRIMARY trigger: SERVER_STOPPING fires EXACTLY ONCE at the very end of shutdown.
        // Most reliable — fires after all saves complete, survives server crashes.
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            boolean cfgOk = CONFIG.shutdownBackup;
            boolean flagOk = Globals.INSTANCE.globalShutdownBackupFlag.get();
            if (!cfgOk) {
                LOGGER.info("[Mochi] SERVER_STOPPING: shutdownBackup=false, skipping");
                return;
            }
            if (!flagOk) {
                LOGGER.info("[Mochi] SERVER_STOPPING: global flag=false, skipping (restore in progress?)");
                return;
            }
            if (!server.isSingleplayer()) {
                LOGGER.info("[Mochi] SERVER_STOPPING: not singleplayer, skipping");
                return;
            }
            boolean canTrigger = Globals.INSTANCE.tryMarkShutdownBackupTriggered();
            if (!canTrigger) {
                LOGGER.info("[Mochi] SERVER_STOPPING: already triggered this session, skipping");
                return;
            }
            var executor = Globals.INSTANCE.getQueueExecutor();
            if (executor == null || executor.isShutdown()) {
                LOGGER.warn("[Mochi] SERVER_STOPPING: executor unavailable (null={}, isShutdown={})",
                        executor == null, executor != null && executor.isShutdown());
                return;
            }
            LOGGER.info("[Mochi] SERVER_STOPPING: submitting shutdown backup task ✓");
            executor.submit(() -> {
                try {
                    ExecutableBackup.Builder.newBackupContextBuilder()
                            .setServer(server)
                            .setInitiator(ActionInitiator.Shutdown)
                            .setComment("shutdown")
                            .announce()
                            .build()
                            .call();
                } catch (Exception e) {
                    LOGGER.warn("[Mochi] Shutdown backup failed: {}", e.getMessage());
                }
            });
        });

        // FALLBACK trigger: END_SERVER_TICK fires every tick during shutdown.
        // Needed because SERVER_STOPPING may not fire in some scenarios (e.g., integrated
        // server with certain Fabric versions). We do NOT check isRunning() here —
        // the server may still be ticking while winding down.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!CONFIG.shutdownBackup) return;
            if (!Globals.INSTANCE.globalShutdownBackupFlag.get()) return;
            if (!server.isSingleplayer()) return;
            if (server.getPlayerList().getPlayerCount() > 0) return;
            if (!Globals.INSTANCE.tryMarkShutdownBackupTriggered()) return;
            var executor = Globals.INSTANCE.getQueueExecutor();
            if (executor == null || executor.isShutdown()) return;
            LOGGER.info("[Mochi] END_SERVER_TICK: submitting shutdown backup task ✓");
            executor.submit(() -> {
                try {
                    ExecutableBackup.Builder.newBackupContextBuilder()
                            .setServer(server)
                            .setInitiator(ActionInitiator.Shutdown)
                            .setComment("shutdown")
                            .announce()
                            .build()
                            .call();
                } catch (Exception e) {
                    LOGGER.warn("[Mochi] Shutdown backup failed: {}", e.getMessage());
                }
            });
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(net.minecraft.commands.Commands.literal("mochi")
                        .requires(ctx -> {
                            try {
                                var cfg = MochiConfigHelper.INSTANCE.get();
                                var player = ctx.getTextName();
                                return canPlayerRunCommand(cfg.permissionLevel, ctx)
                                        && (!cfg.playerBlacklist.contains(player)
                                            || (ctx.getServer().isSingleplayer() && cfg.alwaysSingleplayerAllowed))
                                        && (cfg.playerWhitelist.isEmpty() || cfg.playerWhitelist.contains(player));
                            } catch (Exception ignored) { return true; }
                        })
                        .then(StartBackupCommand.register())
                        .then(CleanupCommand.register())
                        .then(WhitelistCommand.register())
                        .then(BlacklistCommand.register())
                        .then(RestoreBackupCommand.register())
                        .then(ListBackupsCommand.register())
                        .then(DeleteCommand.register())
                        .then(KillRestoreCommand.register())
                )
        );

        LOGGER.info("Mochi loaded.");
    }

    private boolean canPlayerRunCommand(int level, net.minecraft.commands.CommandSourceStack source) {
        try {
            var perm = switch (level) {
                case 0 -> net.minecraft.commands.Commands.LEVEL_ALL;
                case 1 -> net.minecraft.commands.Commands.LEVEL_MODERATORS;
                case 2 -> net.minecraft.commands.Commands.LEVEL_GAMEMASTERS;
                case 3 -> net.minecraft.commands.Commands.LEVEL_ADMINS;
                case 4 -> net.minecraft.commands.Commands.LEVEL_OWNERS;
                default -> null;
            };
            if (perm == null) return true;
            return perm.check(source.permissions());
        } catch (Exception ignored) { return true; }
    }

    @SuppressWarnings("unchecked")
    private void migrateConfig() {
        java.nio.file.Path oldConfig = FabricLoader.getInstance().getConfigDir().resolve("mochi.json");
        if (java.nio.file.Files.exists(oldConfig)) {
            try {
                com.google.gson.JsonObject root = new com.google.gson.GsonBuilder().create()
                        .fromJson(java.nio.file.Files.readString(oldConfig), com.google.gson.JsonObject.class);
                CONFIG = new MochiConfig();
                if (root.has("path")) CONFIG.path = root.get("path").getAsString();
                if (root.has("backupsToKeep")) CONFIG.backupsToKeep = root.get("backupsToKeep").getAsInt();
                if (root.has("maxAge")) CONFIG.maxAge = root.get("maxAge").getAsLong();
                if (root.has("maxSize")) CONFIG.maxSize = root.get("maxSize").getAsLong();
                if (root.has("backupInterval")) CONFIG.backupInterval = root.get("backupInterval").getAsLong();
                if (root.has("shutdownBackup")) CONFIG.shutdownBackup = root.get("shutdownBackup").getAsBoolean();
                if (root.has("format")) {
                    try { CONFIG.format = MochiConfig.ArchiveFormat.valueOf(root.get("format").getAsString().toUpperCase()); }
                    catch (Exception ignored) {}
                }
                if (root.has("compression")) CONFIG.compression = root.get("compression").getAsInt();
                if (root.has("fileBlacklist")) {
                    var arr = root.get("fileBlacklist").getAsJsonArray();
                    for (var el : arr) CONFIG.fileBlacklist.add(el.getAsString());
                }
                if (root.has("restoreDelay")) CONFIG.restoreDelay = root.get("restoreDelay").getAsInt();
                if (root.has("backupBeforeRestore")) CONFIG.backupBeforeRestore = root.get("backupBeforeRestore").getAsBoolean();
                if (root.has("deleteRestoredBackup")) CONFIG.deleteRestoredBackup = root.get("deleteRestoredBackup").getAsBoolean();
                LOGGER.info("Migrated legacy config from mochi.json");
            } catch (Exception e) {
                LOGGER.warn("Config migration failed, using defaults", e);
                CONFIG = new MochiConfig();
            }
        } else {
            CONFIG = new MochiConfig();
        }
    }
}
