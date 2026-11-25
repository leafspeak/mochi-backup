package com.mochi.backup.core.create.compressors;

import com.mochi.backup.MochiConfigHelper;
import com.mochi.backup.Utilities;
import com.mochi.backup.core.create.ExecutableBackup;
import com.mochi.backup.core.create.InputSupplier;
import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import java.util.zip.ZipEntry;

public class ZipCompressor extends AbstractCompressor {
    public static ZipCompressor getInstance() { return new ZipCompressor(); }

    @Override
    protected OutputStream createArchiveOutputStream(OutputStream stream, ExecutableBackup ctx, int coreLimit) {
        ZipArchiveOutputStream arc = new ZipArchiveOutputStream(stream);
        arc.setMethod(ZipArchiveOutputStream.DEFLATED);
        arc.setUseZip64(Zip64Mode.AsNeeded);
        arc.setLevel(MochiConfigHelper.INSTANCE.get().compression);
        arc.setComment("Created by Mochi on: " + Utilities.getDateTimeFormatter(MochiConfigHelper.INSTANCE.get()).format(LocalDateTime.now()));
        return arc;
    }

    @Override
    protected void addEntry(InputSupplier input, OutputStream arc) throws IOException {
        ZipArchiveOutputStream zip = (ZipArchiveOutputStream) arc;

        if (input.getPath().isEmpty()) {
            // In-memory entry (the integrity status blob). STORED needs an exact CRC and size,
            // and the payload must be read in full: a single read() need not fill the buffer.
            byte[] data;
            try (InputStream fis = input.getInputStream()) {
                data = fis.readNBytes((int) input.size());
            }
            ZipArchiveEntry entry = new ZipArchiveEntry(input.getName());
            Checksum sum = new CRC32();
            sum.update(data, 0, data.length);
            entry.setCrc(sum.getValue());
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(data.length);
            zip.putArchiveEntry(entry);
            zip.write(data, 0, data.length);
            zip.closeArchiveEntry();
            return;
        }

        Path file = input.getPath().get();

        // Skip files that disappeared mid-backup (player disconnect cleanup, etc.)
        ZipArchiveEntry entry;
        try {
            entry = (ZipArchiveEntry) zip.createArchiveEntry(file, input.getName());
        } catch (java.nio.file.NoSuchFileException e) {
            accountSkippedFile(input, file);
            return;
        }

        if (isDotDat(file.toString())) {
            long size;
            try {
                size = Files.size(file);
            } catch (java.nio.file.NoSuchFileException e) {
                accountSkippedFile(input, file);
                return;
            }
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(size);
            entry.setCompressedSize(size);
            try {
                entry.setCrc(getCRC(file));
            } catch (java.nio.file.NoSuchFileException e) {
                accountSkippedFile(input, file);
                return;
            }
        } else {
            entry.setMethod(ZipEntry.DEFLATED);
        }

        zip.putArchiveEntry(entry);
        try (InputStream fis = input.getInputStream()) {
            try {
                IOUtils.copy(fis, arc);
            } catch (java.nio.file.NoSuchFileException ignored) {
                // Vanished while reading. The entry is closed below and the stream
                // supplier has already recorded it, so the hash stays consistent.
            }
        } catch (IOException e) {
            // The stream could not be opened at all; FileInputStreamSupplier has already
            // released the hash latch, so close the entry and let the caller decide.
            zip.closeArchiveEntry();
            throw e;
        }
        // Exactly one close per opened entry - closing twice throws
        // "No current entry to close" and failed every sequential ZIP backup.
        zip.closeArchiveEntry();
    }

    protected static boolean isDotDat(String filename) {
        String[] arr = filename.split("\\.");
        return arr[arr.length - 1].contains("dat");
    }

    protected static long getCRC(Path file) throws IOException {
        Checksum sum = new CRC32();
        byte[] buffer = new byte[8192];
        int len;
        try (InputStream stream = Files.newInputStream(file)) {
            while ((len = stream.read(buffer)) != -1) sum.update(buffer, 0, len);
        }
        return sum.getValue();
    }
}
