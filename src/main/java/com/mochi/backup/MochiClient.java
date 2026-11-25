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

    @Override
    public void onInitialize() {
        migrateConfig();

        MochiConfigHelper.updateInstance(AutoConfig.register(MochiConfig.class, JanksonConfigSerializer::new));
        CONFIG = MochiConfigHelper.INSTANCE.get();

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
                                boolean singleplayerOwner =
                                        ctx.getServer() != null && ctx.getServer().isSingleplayer() && cfg.alwaysSingleplayerAllowed;
                                // In singleplayer the owner may always use /mochi, with or without cheats.
                                if (!singleplayerOwner && !canPlayerRunCommand(cfg.permissionLevel, ctx)) return false;
                                // Whitelist/blacklist are player concepts: the console and command
                                // blocks are governed by their permission level alone.
                                if (!ctx.isPlayer()) return true;
                                String player = ctx.getTextName();
                                if (cfg.playerBlacklist.contains(player) && !singleplayerOwner) return false;
                                return cfg.playerWhitelist.isEmpty() || cfg.playerWhitelist.contains(player);
                            } catch (Exception e) {
                                // Fail closed: a broken config must never expose /mochi restore or
                                // /mochi delete to every player.
                                LOGGER.warn("Could not evaluate /mochi permission, denying access", e);
                                return false;
                            }
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
        // Out-of-range values map to the most restrictive level instead of granting access.
        int effective = (level < 0 || level > 4) ? 4 : level;
        var perm = switch (effective) {
            case 0 -> net.minecraft.commands.Commands.LEVEL_ALL;
            case 1 -> net.minecraft.commands.Commands.LEVEL_MODERATORS;
            case 2 -> net.minecraft.commands.Commands.LEVEL_GAMEMASTERS;
            case 3 -> net.minecraft.commands.Commands.LEVEL_ADMINS;
            default -> net.minecraft.commands.Commands.LEVEL_OWNERS;
        };
        return perm.check(source.permissions());
    }

    /**
     * Migrates a legacy {@code config/mochi.json} to {@code config/mochi.json5}.
     * <p>
     * AutoConfig is registered with {@code JanksonConfigSerializer}, which reads and writes
     * {@code mochi.json5}. The old code converted in the opposite direction and then discarded
     * the values it had parsed, so every install coming from v1/v2 silently lost its settings.
     */
    private void migrateConfig() {
        java.nio.file.Path cfgDir = FabricLoader.getInstance().getConfigDir();
        java.nio.file.Path json5Path = cfgDir.resolve("mochi.json5");
        java.nio.file.Path jsonPath = cfgDir.resolve("mochi.json");

        if (java.nio.file.Files.exists(json5Path) || !java.nio.file.Files.exists(jsonPath)) return;

        try {
            String content = java.nio.file.Files.readString(jsonPath);
            com.google.gson.JsonObject legacy = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
            com.google.gson.JsonObject migrated = new com.google.gson.JsonObject();

            // Keep every key that still exists on the config class.
            for (java.lang.reflect.Field field : MochiConfig.class.getFields()) {
                if (legacy.has(field.getName())) {
                    migrated.add(field.getName(), legacy.get(field.getName()));
                }
            }
            // Renamed keys from older versions.
            if (legacy.has("whitelist")) migrated.add("playerWhitelist", legacy.get("whitelist"));
            if (legacy.has("blacklist")) migrated.add("playerBlacklist", legacy.get("blacklist"));

            java.nio.file.Files.writeString(json5Path,
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(migrated));
            LOGGER.info("Migrated config/mochi.json -> config/mochi.json5 ({} keys)", migrated.size());
        } catch (Exception e) {
            LOGGER.warn("Could not migrate config/mochi.json to mochi.json5; keeping defaults", e);
        }
    }
}
