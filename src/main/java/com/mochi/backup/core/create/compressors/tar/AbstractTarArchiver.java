package com.mochi.backup.core.create.compressors.tar;

import com.mochi.backup.core.create.ExecutableBackup;
import com.mochi.backup.core.create.InputSupplier;
import com.mochi.backup.core.create.compressors.AbstractCompressor;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.io.*;

public class AbstractTarArchiver extends AbstractCompressor {
    protected OutputStream getCompressorOutputStream(OutputStream stream, ExecutableBackup ctx, int coreLimit) throws IOException {
        return stream;
    }

    @Override
    protected OutputStream createArchiveOutputStream(OutputStream stream, ExecutableBackup ctx, int coreLimit) throws IOException {
        TarArchiveOutputStream tar = new TarArchiveOutputStream(getCompressorOutputStream(stream, ctx, coreLimit));
        tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
        tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
        return tar;
    }

    @Override
    protected void addEntry(InputSupplier input, OutputStream arc) throws IOException {
        try (InputStream fis = input.getInputStream()) {
            TarArchiveEntry entry;
            if (input.getPath().isEmpty()) {
                entry = new TarArchiveEntry(input.getName());
                entry.setSize(input.size());
            } else entry = (TarArchiveEntry) ((TarArchiveOutputStream) arc).createArchiveEntry(input.getPath().get(), input.getName());
            ((TarArchiveOutputStream) arc).putArchiveEntry(entry);
            IOUtils.copy(fis, arc);
            ((TarArchiveOutputStream) arc).closeArchiveEntry();
        }
    }
}
