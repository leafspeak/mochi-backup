package com.mochi.backup.core.create.compressors.tar;

import com.mochi.backup.core.create.ExecutableBackup;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.*;

public class ParallelGzipCompressor extends AbstractTarArchiver {
    public static ParallelGzipCompressor getInstance() { return new ParallelGzipCompressor(); }

    @Override
    protected OutputStream getCompressorOutputStream(OutputStream stream, ExecutableBackup ctx, int coreLimit) throws IOException {
        return new GzipCompressorOutputStream(stream);
    }
}
