package com.mochi.backup.core;

import com.mochi.backup.Globals;
import com.mochi.backup.MochiConfig;
import com.mochi.backup.MochiConfigHelper;
import com.mochi.backup.Utilities;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public class RestoreableFile implements Comparable<RestoreableFile> {
    private final Path file;
    private final MochiConfig.ArchiveFormat archiveFormat;
    private final LocalDateTime creationTime;
    private final String comment;
    /** True when {@link #creationTime} was parsed from the file name (rather than the filesystem). */
    private final boolean timeFromName;

    private RestoreableFile(Path file, MochiConfig.ArchiveFormat archiveFormat, LocalDateTime creationTime,
                            String comment, boolean timeFromName) {
        this.file = file;
        this.archiveFormat = archiveFormat;
        this.creationTime = creationTime;
        this.comment = comment;
        this.timeFromName = timeFromName;
    }

    public static <T> T applyOnFiles(Path root, T def, Consumer<IOException> errorConsumer, Function<Stream<RestoreableFile>, T> streamConsumer) {
        try (Stream<Path> stream = Files.list(root)) {
            return streamConsumer.apply(stream.flatMap(f -> build(f).stream()));
        } catch (IOException e) { errorConsumer.accept(e); }
        return def;
    }

    public static Optional<RestoreableFile> build(Path file) throws NoSuchElementException {
        if (!Files.exists(file) || !Files.isRegularFile(file)) return Optional.empty();
        String filename = file.getFileName().toString();
        var format = Arrays.stream(MochiConfig.ArchiveFormat.values())
                .filter(f -> filename.endsWith(f.getCompleteString()))
                .findAny().orElse(null);
        if (format == null) return Optional.empty();

        int parsedPos = filename.length() - format.getCompleteString().length();
        String comment = null;
        if (filename.contains("#")) {
            int hashIndex = filename.indexOf('#');
            if (hashIndex < parsedPos) {
                comment = filename.substring(hashIndex + 1, parsedPos);
                parsedPos = hashIndex;
            }
        }
        String timeString = filename.substring(0, parsedPos);

        // Legacy v1 naming: mochi_<epochMillis>[_<comment>].<ext>.
        // The time did not come from the configured date format, so the date check is skipped.
        if (timeString.startsWith("mochi_")) {
            String rest = timeString.substring("mochi_".length());
            int separator = rest.indexOf('_');
            String numberPart = separator >= 0 ? rest.substring(0, separator) : rest;
            try {
                long epochMillis = Long.parseLong(numberPart);
                if (comment == null && separator >= 0) comment = rest.substring(separator + 1);
                return Optional.of(new RestoreableFile(file, format,
                        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), ZoneOffset.systemDefault()),
                        comment, false));
            } catch (NumberFormatException ignored) {
                // fall through to the configured date formats
            }
        }

        try {
            return Optional.of(new RestoreableFile(file, format,
                    LocalDateTime.from(Utilities.getDateTimeFormatter(MochiConfigHelper.INSTANCE.get()).parse(timeString)),
                    comment, true));
        } catch (Exception ignored) {}
        try {
            return Optional.of(new RestoreableFile(file, format,
                    LocalDateTime.from(Globals.defaultDateTimeFormatter.parse(timeString)), comment, true));
        } catch (Exception ignored) {}
        try {
            java.nio.file.attribute.BasicFileAttributes attrs = Files.readAttributes(file, java.nio.file.attribute.BasicFileAttributes.class);
            return Optional.of(new RestoreableFile(file, format,
                    LocalDateTime.ofInstant(attrs.creationTime().toInstant(), ZoneOffset.systemDefault()), comment, false));
        } catch (IOException ignored) {}
        return Optional.empty();
    }

    public Path getFile() { return file; }
    public MochiConfig.ArchiveFormat getArchiveFormat() { return archiveFormat; }
    public LocalDateTime getCreationTime() { return creationTime; }
    public Optional<String> getComment() { return Optional.ofNullable(comment); }
    public boolean isTimeFromName() { return timeFromName; }

    @Override
    public int compareTo(RestoreableFile o) {
        int byTime = creationTime.compareTo(o.creationTime);
        if (byTime != 0) return byTime;
        // Two backups can share a timestamp; fall back to the name so ordering is deterministic.
        return file.getFileName().toString().compareTo(o.file.getFileName().toString());
    }

    public String toString() {
        return creationTime.format(Globals.defaultDateTimeFormatter) + (comment != null ? "#" + comment : "");
    }
}
