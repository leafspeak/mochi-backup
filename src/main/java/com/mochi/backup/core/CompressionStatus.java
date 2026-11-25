package com.mochi.backup.core;

import com.mochi.backup.core.restore.RestoreContext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record CompressionStatus(
        long treeHash,
        Map<String, String> brokenFiles,
        LocalDateTime date,
        long startTimestamp,
        long finishTimestamp,
        String version
) implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String DATA_FILENAME = "mochi_status.data";

    /**
     * The status blob is read straight out of the backup archive, so deserialising it without a
     * filter is an arbitrary-deserialisation vector: a crafted archive could ask for any class on
     * the classpath. Only the types a genuine status file can contain are allowed through.
     */
    private static final Set<String> ALLOWED_CLASSES = Set.of(
            "com.mochi.backup.core.CompressionStatus",
            "java.util.HashMap",
            "java.util.LinkedHashMap",
            "java.lang.String",
            "java.lang.Long",
            "java.lang.Integer",
            "java.lang.Number",
            "java.time.LocalDateTime",
            "java.time.LocalDate",
            "java.time.LocalTime",
            "java.time.Ser"
    );

    private static final ObjectInputFilter FILTER = info -> {
        if (info.depth() > 20 || info.references() > 100_000 || info.arrayLength() > 1_000_000) {
            return ObjectInputFilter.Status.REJECTED;
        }
        Class<?> clazz = info.serialClass();
        if (clazz == null) return ObjectInputFilter.Status.UNDECIDED;
        if (clazz.isArray()) {
            Class<?> component = clazz.getComponentType();
            return component == byte.class || component == Object.class
                    ? ObjectInputFilter.Status.ALLOWED
                    : ObjectInputFilter.Status.REJECTED;
        }
        return ALLOWED_CLASSES.contains(clazz.getName())
                ? ObjectInputFilter.Status.ALLOWED
                : ObjectInputFilter.Status.REJECTED;
    };

    public Optional<String> validate(long hash, RestoreContext ctx) {
        if (hash != treeHash)
            return Optional.of("Tree Hash mismatch!\n  Expected: 0x" + Long.toHexString(treeHash).toUpperCase()
                    + ", got: 0x" + Long.toHexString(hash).toUpperCase());
        if (brokenFiles != null && !brokenFiles.isEmpty())
            return Optional.of("Damaged files present: " + brokenFiles.keySet());
        // The timestamp comparison only makes sense when the backup's time was parsed from its
        // own file name. (The old code had this inverted, so it reported a mismatch whenever the
        // dates matched - and contained nothing else, which is why it never caught anything.)
        if (ctx.restoreableFile().isTimeFromName() && date != null) {
            LocalDateTime expected = ctx.restoreableFile().getCreationTime().truncatedTo(ChronoUnit.SECONDS);
            LocalDateTime actual = date.truncatedTo(ChronoUnit.SECONDS);
            if (!expected.equals(actual))
                return Optional.of("Creation date mismatch!\n   Expected: " + date
                        + ", got: " + ctx.restoreableFile().getCreationTime());
        }
        return Optional.empty();
    }

    public static Path resolveStatusFilename(Path directory) { return directory.resolve(DATA_FILENAME); }

    public static CompressionStatus readFromFile(Path directory) throws IOException, ClassNotFoundException {
        try (ObjectInputStream obj = new ObjectInputStream(Files.newInputStream(directory.resolve(DATA_FILENAME)))) {
            obj.setObjectInputFilter(FILTER);
            Object read = obj.readObject();
            if (!(read instanceof CompressionStatus status)) {
                throw new IOException("Unexpected integrity status payload: " + read.getClass().getName());
            }
            return status;
        }
    }

    public byte[] serialize() throws IOException {
        try (ByteArrayOutputStream bo = new ByteArrayOutputStream();
             ObjectOutputStream o = new ObjectOutputStream(bo)) {
            o.writeObject(this);
            return bo.toByteArray();
        }
    }
}
