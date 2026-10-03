package com.mochi.backup.core.restore;

import com.mochi.backup.*;
import com.mochi.backup.core.RestoreableFile;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class RestoreHelper {
    private final static MochiLogger log = new MochiLogger(MochiClient.MOD_ID);
    private final static MochiConfigHelper config = MochiConfigHelper.INSTANCE;

    public static Optional<RestoreableFile> findFileAndLockIfPresent(LocalDateTime backupTime, MinecraftServer server) {
        Path root = Utilities.getBackupRootPath(config.get(), Utilities.getLevelName(server));
        Optional<RestoreableFile> result = RestoreableFile.applyOnFiles(root, Optional.empty(),
                e -> log.error("Error locking file", e),
                s -> s.filter(rf -> rf.getCreationTime().equals(backupTime)).findFirst());
        result.ifPresent(r -> Globals.INSTANCE.setLockedFile(r.getFile()));
        return result;
    }

    public static Optional<RestoreableFile> getLatestAndLockIfPresent(MinecraftServer server) {
        var available = getAvailableBackups(server);
        if (available.isEmpty()) return Optional.empty();
        var latest = available.getLast();
        Globals.INSTANCE.setLockedFile(latest.getFile());
        return Optional.of(latest);
    }

    public static AwaitThread create(RestoreContext ctx) {
        if (ctx.initiator() == ActionInitiator.Player)
            log.info("Restore initiated by: {}", ctx.commandSource().getTextName());
        else
            log.info("Restore initiated from Server Console");

        Utilities.notifyPlayers(ctx.server(),
                "Server shutting down in " + config.get().restoreDelay + " seconds for restore!");

        return new AwaitThread(config.get().restoreDelay, new RestoreBackupRunnable(ctx));
    }

    public static LinkedList<RestoreableFile> getAvailableBackups(MinecraftServer server) {
        Path root = Utilities.getBackupRootPath(config.get(), Utilities.getLevelName(server));
        return RestoreableFile.applyOnFiles(root, new LinkedList<>(),
                e -> log.error("Error listing backups", e),
                s -> s.sorted().collect(Collectors.toCollection(LinkedList::new)));
    }
}
