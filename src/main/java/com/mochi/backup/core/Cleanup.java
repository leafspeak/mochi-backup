package com.mochi.backup.core;

import com.mochi.backup.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

public class Cleanup implements Callable<Integer> {
    private final static MochiLogger log = new MochiLogger(MochiClient.MOD_ID);
    private final static MochiConfigHelper config = MochiConfigHelper.INSTANCE;

    private final net.minecraft.commands.CommandSourceStack ctx;
    private final String worldName;

    public Cleanup(net.minecraft.commands.CommandSourceStack ctx, String worldName) {
        this.ctx = ctx;
        this.worldName = worldName;
    }

    @Override
    public Integer call() {
        Path root = Utilities.getBackupRootPath(config.get(), worldName,
                ctx != null ? ctx.getServer().getServerDirectory() : Path.of(System.getProperty("user.home")));
        int deleted = 0;
        if (!Files.isDirectory(root) || !Files.exists(root) || isEmpty(root)) return 0;

        if (config.get().maxAge > 0) {
            final long now = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
            deleted += RestoreableFile.applyOnFiles(root, 0L,
                    e -> log.error("Cleanup error", e),
                    s -> s.filter(f -> now - f.getCreationTime().toEpochSecond(ZoneOffset.UTC) > config.get().maxAge)
                            .filter(f -> deleteFile(f.getFile(), ctx)).count()
            );
        }

        final int noToKeep = config.get().backupsToKeep > 0 ? config.get().backupsToKeep : Integer.MAX_VALUE;
        final long maxSize = config.get().maxSize > 0 ? config.get().maxSize * 1024 : Long.MAX_VALUE;
        long[] counts = count(root);
        long n = counts[0], size = counts[1];

        var it = RestoreableFile.applyOnFiles(root, null,
                e -> log.error("Cleanup error", e),
                s -> s.sorted().toList().iterator());

        if (it == null) return deleted;

        while (it.hasNext() && (n > noToKeep || size > maxSize)) {
            Path f = it.next().getFile();
            long sz;
            try { sz = Files.size(f); } catch (IOException e) { size = 0; continue; }
            if (!deleteFile(f, ctx)) continue;
            size -= sz; n--; deleted++;
        }
        return deleted;
    }

    private long[] count(Path root) {
        long n = 0, size = 0;
        try (Stream<Path> stream = Files.list(root)) {
            var it = stream.flatMap(f -> RestoreableFile.build(f).stream()).iterator();
            while (it.hasNext()) {
                var f = it.next();
                try { size += Files.size(f.getFile()); }
                catch (IOException e) { log.error("Couldn't get size of " + f.getFile(), e); }
                n++;
            }
        } catch (IOException e) { log.error("Error counting files", e); }
        return new long[]{n, size};
    }

    private boolean isEmpty(Path root) {
        if (!Files.isDirectory(root)) return false;
        return RestoreableFile.applyOnFiles(root, false, e -> {}, s -> s.findFirst().isEmpty());
    }

    private boolean deleteFile(Path f, net.minecraft.commands.CommandSourceStack ctx) {
        if (Globals.INSTANCE.getLockedFile().filter(p -> p == f).isPresent()) return false;
        try {
            Files.delete(f);
            log.sendInfo(ctx, "Deleted: {}", f.getFileName());
            return true;
        } catch (IOException e) {
            if (Utilities.wasSentByPlayer(ctx)) log.sendError(ctx, "Failed to delete: {}", f.getFileName());
            log.error("Failed to delete: {}", f, e);
            return false;
        }
    }
}
