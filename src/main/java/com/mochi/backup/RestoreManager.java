package com.mochi.backup;

import com.mochi.backup.mixin.MinecraftServerSessionAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Legacy restore manager kept for backwards compatibility.
 * New code should use RestoreBackupRunnable instead.
 */
@Deprecated
public class RestoreManager {
    private final MochiConfig config;

    public RestoreManager(MochiConfig config) { this.config = config; }

    public void scheduleRestore(Minecraft client, Path backupFile, String filename, String worldName) {
        new AwaitThread(config.restoreDelay, new RestoreRunnable(client, backupFile, filename)).start();
    }

    private class RestoreRunnable implements Runnable {
        private final Minecraft client;
        private final Path backupFile;
        private final String filename;

        RestoreRunnable(Minecraft client, Path backupFile, String filename) {
            this.client = client;
            this.backupFile = backupFile;
            this.filename = filename;
        }

        @Override
        public void run() {
            MinecraftServer server = BackupManager.serverOf(client);
            if (server == null) { MochiClient.LOGGER.error("Not in singleplayer."); return; }

            Path worldFile = BackupManager.worldFolderOf(server);
            if (worldFile == null) { MochiClient.LOGGER.error("Could not resolve world folder."); return; }

            MochiClient.LOGGER.info("=== MOCHI RESTORE STARTED ===");

            if (config.backupBeforeRestore) {
                MochiClient.LOGGER.info("Creating safety backup...");
                MochiClient.BACKUP_MANAGER.createBackup(client, "before_restore");
            }

            MochiClient.LOGGER.info("Stopping server...");
            try {
                MinecraftServer.class.getMethod("stop", boolean.class).invoke(server, false);
            } catch (Throwable t) {
                try { MinecraftServer.class.getMethod("stopServer").invoke(server); }
                catch (Throwable t2) { MochiClient.LOGGER.error("Could not stop server", t2); return; }
            }

            Path tmp;
            try {
                Path parent = worldFile.getParent().toAbsolutePath();
                tmp = Files.createTempDirectory(parent, "mochi_restore_");
            } catch (IOException e) { MochiClient.LOGGER.error("Failed to create temp dir", e); return; }

            try {
                MochiClient.LOGGER.info("Unpacking backup...");
                if (filename.endsWith(".tar.gz")) unpackTarGz(backupFile, tmp);
                else unpackZip(backupFile, tmp);
            } catch (IOException e) { MochiClient.LOGGER.error("Unpack failed", e); BackupManager.deleteDir(tmp); return; }

            try {
                java.lang.reflect.Method m = MinecraftServer.class.getMethod("getThread");
                Thread t = (Thread) m.invoke(server);
                if (t != null) {
                    MochiClient.LOGGER.info("Joining server thread...");
                    t.join();
                }
            } catch (Throwable ignored) {}

            try {
                Object session = ((MinecraftServerSessionAccessor) server).getSession();
                if (session instanceof AutoCloseable ac) ac.close();
            } catch (Throwable t) {
                MochiClient.LOGGER.warn("Could not close session", t);
                tryReflectionClose(server);
            }

            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            System.gc();
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}

            try {
                BackupManager.deleteDir(worldFile);
                Files.move(tmp, worldFile, StandardCopyOption.REPLACE_EXISTING);
                MochiClient.LOGGER.info("=== RESTORE COMPLETED ===");
            } catch (IOException e) {
                MochiClient.LOGGER.error("Move failed, trying copy...", e);
                try {
                    copyDir(tmp, worldFile);
                    BackupManager.deleteDir(tmp);
                } catch (IOException ex) { MochiClient.LOGGER.error("Recovery failed", ex); }
            }

            if (config.deleteRestoredBackup) {
                try { Files.deleteIfExists(backupFile); } catch (IOException ignored) {}
            }

            if (Files.exists(worldFile.resolve("level.dat")))
                MochiClient.LOGGER.info("VERIFICATION: level.dat exists.");
            else
                MochiClient.LOGGER.error("VERIFICATION FAILED: level.dat missing!");

            MochiClient.LOGGER.info("=== MOCHI RESTORE FINISHED ===");
        }
    }

    private static void tryReflectionClose(MinecraftServer server) {
        for (String fn : new String[]{"storageSource", "session", "storage"}) {
            try {
                var f = MinecraftServer.class.getDeclaredField(fn);
                f.setAccessible(true);
                Object v = f.get(server);
                if (v instanceof AutoCloseable ac) { ac.close(); return; }
            } catch (Exception ignored) {}
        }
    }

    private static void unpackZip(Path zip, Path target) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e; int n = 0;
            while ((e = zis.getNextEntry()) != null) {
                Path out = target.resolve(e.getName()).normalize();
                if (!out.startsWith(target)) continue;
                if (e.isDirectory()) Files.createDirectories(out);
                else { Files.createDirectories(out.getParent()); Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING); n++; }
                zis.closeEntry();
            }
        }
    }

    private static void unpackTarGz(Path tarGz, Path target) throws IOException {
        try (GzipCompressorInputStream gz = new GzipCompressorInputStream(Files.newInputStream(tarGz));
             TarArchiveInputStream tar = new TarArchiveInputStream(gz)) {
            TarArchiveEntry e; int n = 0;
            while ((e = tar.getNextEntry()) != null) {
                Path out = target.resolve(e.getName()).normalize();
                if (!out.startsWith(target)) continue;
                if (e.isDirectory()) Files.createDirectories(out);
                else { Files.createDirectories(out.getParent()); Files.copy(tar, out, StandardCopyOption.REPLACE_EXISTING); n++; }
            }
        }
    }

    private static void copyDir(Path src, Path dst) throws IOException {
        Files.createDirectories(dst);
        try (java.util.stream.Stream<Path> s = Files.walk(src)) {
            s.forEach(p -> {
                try {
                    Path d = dst.resolve(src.relativize(p));
                    if (Files.isDirectory(p)) Files.createDirectories(d);
                    else { Files.createDirectories(d.getParent()); Files.copy(p, d, StandardCopyOption.REPLACE_EXISTING); }
                } catch (IOException e) { MochiClient.LOGGER.error("Copy failed", e); }
            });
        }
    }
}
