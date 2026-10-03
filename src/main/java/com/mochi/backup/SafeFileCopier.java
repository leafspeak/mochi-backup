package com.mochi.backup;

import com.mochi.backup.Utilities;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.*;

public class SafeFileCopier {

    public static Map<String, Path> copyDirectorySafely(Path sourceDir, Path destDir, List<String> blacklist)
            throws IOException, InterruptedException, ExecutionException {
        Map<String, Path> copied = new ConcurrentHashMap<>();
        Files.createDirectories(destDir);

        List<Path> files;
        try (Stream<Path> stream = Files.walk(sourceDir)) {
            files = stream.filter(p -> !Files.isDirectory(p))
                    .filter(p -> !isBlacklisted(sourceDir.relativize(p).toString(), blacklist))
                    .collect(Collectors.toList());
        }

        int threads = Math.max(1, Runtime.getRuntime().availableProcessors());
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();

        for (Path file : files) {
            futures.add(exec.submit(() -> {
                try {
                    String rel = sourceDir.relativize(file).toString().replace('\\', '/');
                    Path target = destDir.resolve(rel);
                    Files.createDirectories(target.getParent());
                    Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    copied.put(rel, target);
                } catch (IOException e) {
                    MochiClient.LOGGER.error("Copy failed: " + file, e);
                }
            }));
        }
        for (Future<?> f : futures) f.get();
        exec.shutdown();
        return copied;
    }

    private static boolean isBlacklisted(String rel, List<String> blacklist) {
        for (String b : blacklist) {
            if (rel.equals(b) || rel.endsWith("/" + b)) return true;
        }
        return false;
    }
}
