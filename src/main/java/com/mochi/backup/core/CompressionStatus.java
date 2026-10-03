package com.mochi.backup.core;

import com.mochi.backup.core.restore.RestoreContext;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

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

    public Optional<String> validate(long hash, RestoreContext ctx) throws RuntimeException {
        if (hash != treeHash)
            return Optional.of("Tree Hash mismatch!\n  Expected: 0x" + Long.toHexString(treeHash).toUpperCase() + ", got: 0x" + Long.toHexString(hash).toUpperCase());
        if (!brokenFiles.isEmpty()) return Optional.of("Damaged files present!");
        if (ctx.restoreableFile().getCreationTime().equals(date))
            return Optional.of("Creation date mismatch!\n   Expected: " + date + ", got: " + ctx.restoreableFile().getCreationTime());
        return Optional.empty();
    }

    public static Path resolveStatusFilename(Path directory) { return directory.resolve(DATA_FILENAME); }

    public static CompressionStatus readFromFile(Path directory) throws IOException, ClassNotFoundException {
        try (ObjectInputStream obj = new ObjectInputStream(Files.newInputStream(directory.resolve(DATA_FILENAME)))) {
            return (CompressionStatus) obj.readObject();
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
