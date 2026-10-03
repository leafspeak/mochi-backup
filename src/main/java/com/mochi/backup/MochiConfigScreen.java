package com.mochi.backup;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;

public class MochiConfigScreen {
    public static Screen create(Screen parent) {
        ConfigBuilder b = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Mochi Settings"));
        ConfigEntryBuilder e = b.entryBuilder();
        MochiConfig cfg = MochiClient.CONFIG;

        // General
        ConfigCategory gen = b.getOrCreateCategory(Component.literal("General"));
        gen.addEntry(e.startStrField(Component.literal("Backup Folder"), cfg.path)
                .setDefaultValue("mochi_backups/")
                .setTooltip(Component.literal("Where backups are stored."))
                .setSaveConsumer(v -> cfg.path = v).build());
        gen.addEntry(e.startBooleanToggle(Component.literal("Per-World Folders"), cfg.perWorldBackup)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Store each world in its own subfolder."))
                .setSaveConsumer(v -> cfg.perWorldBackup = v).build());
        gen.addEntry(e.startIntField(Component.literal("Backups to Keep"), cfg.backupsToKeep)
                .setDefaultValue(10).setMin(0)
                .setTooltip(Component.literal("0 = disabled."))
                .setSaveConsumer(v -> cfg.backupsToKeep = v).build());
        gen.addEntry(e.startIntField(Component.literal("Max Age (seconds)"), (int) cfg.maxAge)
                .setDefaultValue(0).setMin(0)
                .setTooltip(Component.literal("0 = disabled."))
                .setSaveConsumer(v -> cfg.maxAge = v).build());
        gen.addEntry(e.startIntField(Component.literal("Max Size (KB)"), (int) cfg.maxSize)
                .setDefaultValue(0).setMin(0)
                .setTooltip(Component.literal("0 = disabled."))
                .setSaveConsumer(v -> cfg.maxSize = v).build());

        // Backup
        ConfigCategory bk = b.getOrCreateCategory(Component.literal("Backup"));
        bk.addEntry(e.startIntField(Component.literal("Interval (seconds)"), (int) cfg.backupInterval)
                .setDefaultValue(3600).setMin(0)
                .setTooltip(Component.literal("0 = disabled. Auto-backup interval."))
                .setSaveConsumer(v -> cfg.backupInterval = v).build());
        bk.addEntry(e.startBooleanToggle(Component.literal("Backup on Empty Server"), cfg.doBackupsOnEmptyServer)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Allow backups when no players are online."))
                .setSaveConsumer(v -> cfg.doBackupsOnEmptyServer = v).build());
        bk.addEntry(e.startBooleanToggle(Component.literal("Backup on Shutdown"), cfg.shutdownBackup)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Create a backup when the server stops."))
                .setSaveConsumer(v -> cfg.shutdownBackup = v).build());
        bk.addEntry(e.startIntSlider(Component.literal("Compression Level"), cfg.compression, 0, 9)
                .setDefaultValue(7)
                .setTooltip(Component.literal("0 = fastest, 9 = smallest."))
                .setSaveConsumer(v -> cfg.compression = v).build());
        bk.addEntry(e.startIntSlider(Component.literal("Compression Threads"), cfg.compressionCoreCountLimit, 0, 32)
                .setDefaultValue(0)
                .setTooltip(Component.literal("0 = use all cores."))
                .setSaveConsumer(v -> cfg.compressionCoreCountLimit = v).build());
        bk.addEntry(e.startSelector(Component.literal("Archive Format"),
                new String[]{"ZIP", "GZIP", "TAR"}, cfg.format.name())
                .setDefaultValue("ZIP")
                .setTooltip(Component.literal("ZIP is fastest. GZIP/TAR produce smaller files."))
                .setSaveConsumer(v -> { try { cfg.format = MochiConfig.ArchiveFormat.valueOf(v); } catch (Exception ignored) {} }).build());
        bk.addEntry(e.startBooleanToggle(Component.literal("Broadcast Start"), cfg.broadcastBackupStart)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Announce to all players when backup starts."))
                .setSaveConsumer(v -> cfg.broadcastBackupStart = v).build());
        bk.addEntry(e.startBooleanToggle(Component.literal("Broadcast Done"), cfg.broadcastBackupDone)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Announce to all players when backup finishes."))
                .setSaveConsumer(v -> cfg.broadcastBackupDone = v).build());
        bk.addEntry(e.startStrList(Component.literal("File Blacklist"), cfg.fileBlacklist)
                .setDefaultValue(new ArrayList<>())
                .setTooltip(Component.literal("Files to skip during backup."))
                .setSaveConsumer(v -> cfg.fileBlacklist = new ArrayList<>(v)).build());
        bk.addEntry(e.startStrField(Component.literal("Date Format"), cfg.dateTimeFormat)
                .setDefaultValue("yyyy-MM-dd_HH-mm-ss")
                .setTooltip(Component.literal("Java DateTimeFormatter pattern for backup filenames."))
                .setSaveConsumer(v -> cfg.dateTimeFormat = v).build());
        bk.addEntry(e.startSelector(Component.literal("Integrity Mode"),
                new String[]{"Strict", "Permissible", "Very Permissible"}, cfg.integrityVerificationMode.name())
                .setDefaultValue("STRICT")
                .setTooltip(Component.literal("Strict verifies hashes. Permissible warns. Very Permissible skips checks."))
                .setSaveConsumer(v -> { try { cfg.integrityVerificationMode = MochiConfig.IntegrityVerificationMode.valueOf(v); } catch (Exception ignored) {} }).build());

        // Restore
        ConfigCategory rs = b.getOrCreateCategory(Component.literal("Restore"));
        rs.addEntry(e.startIntField(Component.literal("Restore Delay (seconds)"), cfg.restoreDelay)
                .setDefaultValue(30).setMin(3)
                .setTooltip(Component.literal("Seconds to wait before the world stops for restore."))
                .setSaveConsumer(v -> cfg.restoreDelay = v).build());
        rs.addEntry(e.startBooleanToggle(Component.literal("Backup Before Restore"), cfg.backupBeforeRestore)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Safety backup of current world before restoring."))
                .setSaveConsumer(v -> cfg.backupBeforeRestore = v).build());
        rs.addEntry(e.startBooleanToggle(Component.literal("Delete After Restore"), cfg.deleteRestoredBackup)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Delete the backup file after successful restore."))
                .setSaveConsumer(v -> cfg.deleteRestoredBackup = v).build());

        // Permissions
        ConfigCategory perm = b.getOrCreateCategory(Component.literal("Permissions"));
        perm.addEntry(e.startIntField(Component.literal("Permission Level"), cfg.permissionLevel)
                .setDefaultValue(4).setMin(0)
                .setTooltip(Component.literal("Minimal permission level to run backup commands."))
                .setSaveConsumer(v -> cfg.permissionLevel = v).build());
        perm.addEntry(e.startBooleanToggle(Component.literal("Always Allow Singleplayer"), cfg.alwaysSingleplayerAllowed)
                .setDefaultValue(true)
                .setTooltip(Component.literal("In singleplayer, the owner can always run commands."))
                .setSaveConsumer(v -> cfg.alwaysSingleplayerAllowed = v).build());
        perm.addEntry(e.startStrList(Component.literal("Player Whitelist"), cfg.playerWhitelist)
                .setDefaultValue(new ArrayList<>())
                .setTooltip(Component.literal("Players allowed regardless of permission level."))
                .setSaveConsumer(v -> cfg.playerWhitelist = new ArrayList<>(v)).build());
        perm.addEntry(e.startStrList(Component.literal("Player Blacklist"), cfg.playerBlacklist)
                .setDefaultValue(new ArrayList<>())
                .setTooltip(Component.literal("Players blocked even with permission level."))
                .setSaveConsumer(v -> cfg.playerBlacklist = new ArrayList<>(v)).build());

        // Discord
        ConfigCategory dc = b.getOrCreateCategory(Component.literal("Discord"));
        dc.addEntry(e.startBooleanToggle(Component.literal("Enable Discord Notifications"), cfg.discordEnabled)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Send embed notifications to Discord on backup/restore events."))
                .setSaveConsumer(v -> cfg.discordEnabled = v).build());
        dc.addEntry(e.startStrField(Component.literal("Webhook URL"), cfg.discordWebhookUrl)
                .setDefaultValue("")
                .setTooltip(Component.literal("Discord webhook URL (https://discord.com/api/webhooks/...)"))
                .setSaveConsumer(v -> cfg.discordWebhookUrl = v).build());
        dc.addEntry(e.startBooleanToggle(Component.literal("On Backup Start"), cfg.discordSendBackupStart)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Send notification when a backup begins."))
                .setSaveConsumer(v -> cfg.discordSendBackupStart = v).build());
        dc.addEntry(e.startBooleanToggle(Component.literal("On Backup Done"), cfg.discordSendBackupDone)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Send notification when a backup completes successfully."))
                .setSaveConsumer(v -> cfg.discordSendBackupDone = v).build());
        dc.addEntry(e.startBooleanToggle(Component.literal("On Backup Failed"), cfg.discordSendBackupFailed)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Send notification when a backup fails."))
                .setSaveConsumer(v -> cfg.discordSendBackupFailed = v).build());
        dc.addEntry(e.startBooleanToggle(Component.literal("On Restore"), cfg.discordSendRestore)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Send notification when a world is restored from backup."))
                .setSaveConsumer(v -> cfg.discordSendRestore = v).build());

        b.setSavingRunnable(() -> MochiConfigHelper.INSTANCE.save());
        return b.build();
    }
}
