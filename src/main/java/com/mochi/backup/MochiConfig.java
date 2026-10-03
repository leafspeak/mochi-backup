package com.mochi.backup;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Config(name = MochiClient.MOD_ID)
public class MochiConfig implements ConfigData {
    public boolean perWorldBackup = true;
    public long backupInterval = 3600;
    public int restoreDelay = 30;
    public boolean doBackupsOnEmptyServer = false;
    public boolean shutdownBackup = true;
    public boolean backupBeforeRestore = true;
    public String path = "mochi_backups/";
    public List<String> fileBlacklist = new ArrayList<>();
    public boolean deleteRestoredBackup = true;
    public int backupsToKeep = 10;
    public long maxAge = 0;
    public long maxSize = 0;
    public int compression = 7;
    public int compressionCoreCountLimit = 0;
    public ArchiveFormat format = ArchiveFormat.ZIP;
    public int permissionLevel = 4;
    public boolean alwaysSingleplayerAllowed = true;
    public List<String> playerWhitelist = new ArrayList<>();
    public List<String> playerBlacklist = new ArrayList<>();
    public boolean broadcastBackupStart = true;
    public boolean broadcastBackupDone = true;
    // Discord webhook settings
    public boolean discordEnabled = false;
    public String discordWebhookUrl = "";
    public boolean discordSendBackupStart = true;
    public boolean discordSendBackupDone = true;
    public boolean discordSendBackupFailed = true;
    public boolean discordSendRestore = true;
    public String dateTimeFormat = "yyyy-MM-dd_HH-mm-ss";
    public IntegrityVerificationMode integrityVerificationMode = IntegrityVerificationMode.STRICT;

    public enum IntegrityVerificationMode {
        STRICT, PERMISSIBLE, VERY_PERMISSIBLE;
        public boolean isStrict() { return this == STRICT; }
        public boolean verify() { return this != VERY_PERMISSIBLE; }
    }

    public enum ArchiveFormat {
        ZIP("zip"),
        GZIP("tar", "gz"),
        TAR("tar");

        private final List<String> extensionPieces;

        ArchiveFormat(String... parts) {
            extensionPieces = Arrays.asList(parts);
        }

        public String getCompleteString() {
            StringBuilder b = new StringBuilder();
            extensionPieces.forEach(s -> b.append('.').append(s));
            return b.toString();
        }

        public String getLastPiece() {
            return extensionPieces.get(extensionPieces.size() - 1);
        }
    }

    @Override
    public void validatePostLoad() throws ValidationException {
        if (compressionCoreCountLimit > 0 && compressionCoreCountLimit > Runtime.getRuntime().availableProcessors())
            throw new ValidationException("compressionCoreCountLimit exceeds available cores: " + Runtime.getRuntime().availableProcessors());
        try { DateTimeFormatter.ofPattern(dateTimeFormat); }
        catch (IllegalArgumentException e) {
            throw new ValidationException("dateTimeFormat is invalid! See Oracle DateTimeFormatter docs.", e);
        }
    }
}
