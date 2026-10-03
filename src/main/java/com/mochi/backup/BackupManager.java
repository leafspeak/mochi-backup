package com.mochi.backup;

import com.mochi.backup.Utilities;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Legacy backup manager kept for backwards compatibility.
 * New code should use ExecutableBackup / BackupScheduler instead.
 */
@Deprecated
public class BackupManager {
    public static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private final MochiConfig config;

    public BackupManager(MochiConfig config) { this.config = config; }

    public static MinecraftServer serverOf(Minecraft client) {
        if (client == null) return null;
        for (String name : new String[]{"getSingleplayerServer", "getServer"}) {
            try {
                Method m = Minecraft.class.getMethod(name);
                Object r = m.invoke(client);
                if (r instanceof MinecraftServer s) return s;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public static String worldNameOf(MinecraftServer server) {
        if (server == null) return "unknown";
        try {
            Object wd = MinecraftServer.class.getMethod("getWorldData").invoke(server);
            if (wd != null) {
                Object name = wd.getClass().getMethod("getLevelName").invoke(wd);
                if (name instanceof String s) return s;
            }
        } catch (Throwable ignored) {}
        return "world";
    }

    public static Path worldFolderOf(MinecraftServer server) {
        if (server == null) return null;
        try {
            Method m = MinecraftServer.class.getMethod("getWorldPath", net.minecraft.world.level.storage.LevelResource.class);
            Object r = m.invoke(server, net.minecraft.world.level.storage.LevelResource.ROOT);
            if (r instanceof Path p) return p;
        } catch (Throwable ignored) {}
        return null;
    }

    public static void forceSave(MinecraftServer server) {
        if (server == null) return;
        try {
            Method m = MinecraftServer.class.getMethod("saveAll", boolean.class, boolean.class, boolean.class);
            m.invoke(server, true, true, false);
        } catch (Throwable ignored) {}
    }

    public Path getBackupDir(String worldName) {
        String safe = worldName.replaceAll("[^a-zA-Z0-9_-]", "_");
        return Paths.get(config.path, safe);
    }

    public boolean createBackup(Minecraft client, String comment) {
        MinecraftServer server = serverOf(client);
        if (server == null) { MochiClient.LOGGER.warn("Not in singleplayer."); return false; }
        String worldName = worldNameOf(server);
        Path worldDir = worldFolderOf(server);
        if (worldDir == null || !Files.exists(worldDir)) return false;

        forceSave(server);
        try {
            Path backupDir = getBackupDir(worldName);
            Files.createDirectories(backupDir);
            Path tempDir = Files.createTempDirectory("mochi_copy_");
            try {
                SafeFileCopier.copyDirectorySafely(worldDir, tempDir, config.fileBlacklist);
                String ts = LocalDateTime.now().format(TIME_FMT);
                String safeComment = comment == null || comment.isBlank() ? "" : "_" + comment.replaceAll("[^a-zA-Z0-9_-]", "_");
                String ext = MochiConfig.ArchiveFormat.GZIP == config.format ? ".tar.gz" : ".zip";
                Path backupFile = backupDir.resolve("mochi_" + ts + safeComment + ext);
                if (MochiConfig.ArchiveFormat.GZIP == config.format) createTarGz(backupFile, tempDir);
                else createZip(backupFile, tempDir);
                MochiClient.LOGGER.info("Backup created: {} ({} KB)", backupFile.getFileName(), Files.size(backupFile) / 1024);
                cleanup(worldName);
                return true;
            } finally { deleteDir(tempDir); }
        } catch (Exception e) {
            MochiClient.LOGGER.error("Backup failed", e);
            return false;
        }
    }

    private void createZip(Path out, Path dir) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(out))) {
            zos.setLevel(config.compression);
            try (Stream<Path> s = Files.walk(dir)) {
                s.filter(p -> !Files.isDirectory(p)).forEach(p -> {
                    try {
                        zos.putNextEntry(new ZipEntry(dir.relativize(p).toString().replace('\\', '/')));
                        Files.copy(p, zos);
                        zos.closeEntry();
                    } catch (IOException e) { MochiClient.LOGGER.error("Zip entry failed", e); }
                });
            }
        }
    }

    private void createTarGz(Path out, Path dir) throws IOException {
        try (GzipCompressorOutputStream gz = new GzipCompressorOutputStream(Files.newOutputStream(out));
             TarArchiveOutputStream tar = new TarArchiveOutputStream(gz)) {
            try (Stream<Path> s = Files.walk(dir)) {
                s.filter(p -> !Files.isDirectory(p)).forEach(p -> {
                    try {
                        TarArchiveEntry entry = new TarArchiveEntry(dir.relativize(p).toString().replace('\\', '/'));
                        entry.setSize(Files.size(p));
                        tar.putArchiveEntry(entry);
                        Files.copy(p, tar);
                        tar.closeArchiveEntry();
                    } catch (IOException e) { MochiClient.LOGGER.error("Tar entry failed", e); }
                });
            }
        }
    }

    static void deleteDir(Path dir) {
        try {
            if (Files.exists(dir)) {
                try (Stream<Path> s = Files.walk(dir)) {
                    s.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
                }
            }
        } catch (IOException ignored) {}
    }

    public List<Path> listBackups(String worldName) {
        try {
            Path dir = getBackupDir(worldName);
            if (!Files.exists(dir)) return Collections.emptyList();
            try (Stream<Path> s = Files.list(dir)) {
                return s.filter(p -> p.toString().endsWith(".zip") || p.toString().endsWith(".tar.gz"))
                        .sorted((a, b) -> {
                            try { return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a)); }
                            catch (IOException e) { return 0; }
                        }).collect(Collectors.toList());
            }
        } catch (IOException e) { return Collections.emptyList(); }
    }

    public int cleanup(String worldName) {
        int deleted = 0;
        try {
            List<Path> backups = listBackups(worldName);
            if (backups.isEmpty()) return 0;
            if (config.backupsToKeep > 0 && backups.size() > config.backupsToKeep) {
                for (int i = config.backupsToKeep; i < backups.size(); i++) {
                    Files.deleteIfExists(backups.get(i)); deleted++;
                }
            }
            if (config.maxAge > 0) {
                long cutoff = System.currentTimeMillis() - config.maxAge * 1000L;
                for (Path b : backups) {
                    if (Files.getLastModifiedTime(b).toMillis() < cutoff) { Files.deleteIfExists(b); deleted++; }
                }
            }
            if (config.maxSize > 0) {
                long total = 0;
                for (Path b : backups) total += Files.size(b);
                long max = config.maxSize * 1024L;
                if (total > max) {
                    List<Path> oldest = new ArrayList<>(backups);
                    oldest.sort(Comparator.comparingLong(p -> {
                        try { return Files.getLastModifiedTime(p).toMillis(); } catch (IOException e) { return 0; }
                    }));
                    for (Path b : oldest) {
                        if (total <= max) break;
                        long sz = Files.size(b);
                        Files.deleteIfExists(b); total -= sz; deleted++;
                    }
                }
            }
        } catch (IOException e) { MochiClient.LOGGER.error("Cleanup failed", e); }
        return deleted;
    }

    public boolean deleteBackup(String worldName, String fileName) {
        try {
            Path b = getBackupDir(worldName).resolve(fileName);
            if (Files.exists(b)) { Files.delete(b); return true; }
        } catch (IOException e) { MochiClient.LOGGER.error("Delete failed", e); }
        return false;
    }
}
