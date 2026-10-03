package com.mochi.backup.core.create.compressors;

import com.mochi.backup.MochiConfig;
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
        try (InputStream fis = input.getInputStream()) {
            ZipArchiveEntry entry;
            if (input.getPath().isEmpty()) {
                entry = new ZipArchiveEntry(input.getName());
                byte[] buff = new byte[(int) input.size()];
                int len = fis.read(buff);
                Checksum sum = new CRC32();
                sum.update(buff, 0, len);
                entry.setCrc(sum.getValue());
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(input.size());
                ((ZipArchiveOutputStream) arc).putArchiveEntry(entry);
                arc.write(buff, 0, len);
            } else {
                Path file = input.getPath().get();
                // Skip files that disappeared mid-backup (player disconnect cleanup, etc.)
                try {
                    entry = (ZipArchiveEntry) ((ZipArchiveOutputStream) arc).createArchiveEntry(file, input.getName());
                } catch (java.nio.file.NoSuchFileException e) {
                    return;
                }
                if (isDotDat(file.toString())) {
                    long size;
                    try { size = Files.size(file); }
                    catch (java.nio.file.NoSuchFileException ignored) { return; }
                    entry.setMethod(ZipEntry.STORED);
                    entry.setSize(size);
                    entry.setCompressedSize(size);
                    try { entry.setCrc(getCRC(file)); }
                    catch (java.nio.file.NoSuchFileException ignored) {}
                } else entry.setMethod(ZipEntry.DEFLATED);
                ((ZipArchiveOutputStream) arc).putArchiveEntry(entry);
                try { IOUtils.copy(fis, arc); } catch (java.nio.file.NoSuchFileException ignored) {}
                ((ZipArchiveOutputStream) arc).closeArchiveEntry();
            }
            ((ZipArchiveOutputStream) arc).closeArchiveEntry();
        }
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
