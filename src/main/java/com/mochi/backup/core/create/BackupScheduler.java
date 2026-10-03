package com.mochi.backup.core.create;

import net.minecraft.server.MinecraftServer;
import com.mochi.backup.Globals;
import com.mochi.backup.MochiConfigHelper;
import com.mochi.backup.ActionInitiator;

import java.time.Instant;

/**
 * Runs backup on a preset interval via server tick events.
 */
public class BackupScheduler {
    private final static MochiConfigHelper config = MochiConfigHelper.INSTANCE;
    private static boolean scheduled = false;
    private static long nextBackup = -1;

    public static void tick(MinecraftServer server) {
        // Don't schedule backups while the server is shutting down
        if (!server.isRunning()) return;
        if (config.get().backupInterval < 1) return;
        long now = Instant.now().getEpochSecond();

        if (config.get().doBackupsOnEmptyServer || server.getPlayerList().getPlayerCount() > 0) {
            if (scheduled) {
                if (nextBackup <= now) {
                    var executor = Globals.INSTANCE.getQueueExecutor();
                    if (executor != null && !executor.isShutdown()) {
                        executor.submit(
                                ExecutableBackup.Builder.newBackupContextBuilder()
                                        .setServer(server)
                                        .setInitiator(ActionInitiator.Timer)
                                        .saveServer()
                                        .announce()
                                        .build()
                        );
                    }
                    nextBackup = now + config.get().backupInterval;
                }
            } else {
                nextBackup = now + config.get().backupInterval;
                scheduled = true;
            }
        } else if (!config.get().doBackupsOnEmptyServer && server.getPlayerList().getPlayerCount() == 0) {
            if (scheduled && nextBackup <= now) {
                var executor = Globals.INSTANCE.getQueueExecutor();
                if (executor != null && !executor.isShutdown()) {
                    executor.submit(
                            ExecutableBackup.Builder.newBackupContextBuilder()
                                    .setServer(server)
                                    .setInitiator(ActionInitiator.Timer)
                                    .saveServer()
                                    .announce()
                                    .build()
                    );
                }
                scheduled = false;
            }
        }
    }

    /** Called on SERVER_STARTING to reset scheduler state for the new session. */
    public static void reset() {
        scheduled = false;
        nextBackup = -1;
    }
}
