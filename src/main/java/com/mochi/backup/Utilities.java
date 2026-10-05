package com.mochi.backup;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import com.mochi.backup.mixin.MinecraftServerSessionAccessor;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Collection;

public class Utilities {
    public static boolean wasSentByPlayer(net.minecraft.commands.CommandSourceStack source) { return source.isPlayer(); }

    public static void notifyPlayers(@NotNull MinecraftServer server, String msg) {
        net.minecraft.network.chat.Component message = net.minecraft.network.chat.Component.literal("[Mochi] " + msg);
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    public static String getLevelName(MinecraftServer server) {
        return ((MinecraftServerSessionAccessor) server).getSession().getLevelDirectory().directoryName();
    }

    public static Path getWorldFolder(MinecraftServer server) {
        return ((MinecraftServerSessionAccessor) server).getSession().getLevelDirectory().path();
    }

    public static void deleteDirectory(Path path) throws IOException {
        Files.walkFileTree(path, new java.nio.file.SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static Path getBackupRootPath(MochiConfig config, String worldName, Path serverDir) {
        // Resolve backup path relative to the GAME ROOT directory,
        // not the JVM working directory or the world save directory.
        // Walk up from serverDir to find the game root (the dir containing "saves").
        Path gameRoot = findGameRoot(serverDir);
        Path basePath;
        if (config.path.startsWith("/") || Paths.get(config.path).getRoot() != null) {
            // Absolute path — use as-is
            basePath = Path.of(config.path);
        } else {
            // Relative to game root (so "mochi_backups/" goes into the profile dir)
            basePath = gameRoot.resolve(config.path);
        }
        if (config.perWorldBackup) basePath = basePath.resolve(worldName);
        if (Files.notExists(basePath)) {
            try { Files.createDirectories(basePath); } catch (IOException e) { /* ignore */ }
        }
        return basePath;
    }

    /**
     * Walks up from a server/world directory to find the game root directory.
     * The game root is identified as the first parent that contains a "config" subdir.
     * Falls back to the immediate parent of the world directory if not found.
     */
    private static Path findGameRoot(Path start) {
        Path current = start.toAbsolutePath().normalize();
        for (int i = 0; i < 10; i++) {
            if (Files.isDirectory(current.resolve("config"))) return current;
            current = current.getParent();
            if (current == null) break;
        }
        // Fallback: parent of the world directory
        return start.getParent();
    }

    public static boolean isBlacklisted(Path path, MochiConfig config) {
        String name = path.getFileName().toString();
        if (name.equals("session.lock")) return true;
        if (name.equals(com.mochi.backup.core.CompressionStatus.DATA_FILENAME)) return true;
        for (String b : config.fileBlacklist) {
            if (name.equals(b) || path.toString().endsWith("/" + b)) return true;
        }
        return false;
    }

    public static DateTimeFormatter getDateTimeFormatter(MochiConfig config) {
        return DateTimeFormatter.ofPattern(config.dateTimeFormat);
    }

    public static String formatDuration(Duration duration) {
        DateTimeFormatter formatter;
        if (duration.toHours() > 0) formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
        else if (duration.toMinutes() > 0) formatter = DateTimeFormatter.ofPattern("mm:ss.SSS");
        else formatter = DateTimeFormatter.ofPattern("ss.SSS");
        return LocalTime.ofNanoOfDay(duration.toNanos()).format(formatter);
    }
}