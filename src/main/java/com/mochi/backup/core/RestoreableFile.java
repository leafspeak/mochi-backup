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

    private RestoreableFile(Path file, MochiConfig.ArchiveFormat archiveFormat, LocalDateTime creationTime, String comment) {
        this.file = file;
        this.archiveFormat = archiveFormat;
        this.creationTime = creationTime;
        this.comment = comment;
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
            comment = filename.substring(filename.indexOf("#") + 1, parsedPos);
            parsedPos -= comment.length() + 1;
        }
        String timeString = filename.substring(0, parsedPos);
        try {
            return Optional.of(new RestoreableFile(file, format, LocalDateTime.from(Utilities.getDateTimeFormatter(MochiConfigHelper.INSTANCE.get()).parse(timeString)), comment));
        } catch (Exception ignored) {}
        try {
            return Optional.of(new RestoreableFile(file, format, LocalDateTime.from(Globals.defaultDateTimeFormatter.parse(timeString)), comment));
        } catch (Exception ignored) {}
        try {
            java.nio.file.attribute.BasicFileAttributes attrs = Files.readAttributes(file, java.nio.file.attribute.BasicFileAttributes.class);
            return Optional.of(new RestoreableFile(file, format, LocalDateTime.ofInstant(attrs.creationTime().toInstant(), ZoneOffset.systemDefault()), comment));
        } catch (IOException ignored) {}
        return Optional.empty();
    }

    public Path getFile() { return file; }
    public MochiConfig.ArchiveFormat getArchiveFormat() { return archiveFormat; }
    public LocalDateTime getCreationTime() { return creationTime; }
    public Optional<String> getComment() { return Optional.ofNullable(comment); }

    @Override
    public int compareTo(RestoreableFile o) { return creationTime.compareTo(o.creationTime); }

    public String toString() {
        return creationTime.format(Globals.defaultDateTimeFormatter) + (comment != null ? "#" + comment : "");
    }
}
