package com.mochi.backup.core.digest;

import com.mochi.backup.core.Hash;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * BalticHash - a fast tree-hashing algorithm based on SeaHash with xorshift64*.
 */
public class BalticHash implements Hash {
    protected final static long[] IV = { 0x16f11fe89b0d677cL, 0xb480a793d8e6c86cL, 0x6fe2e5aaf078ebc9L, 0x14f994a4c5259381L };
    private final long[] state = Arrays.copyOf(IV, IV.length);
    protected final int buffer_limit = state.length * Long.BYTES;
    protected final byte[] _byte_buffer = new byte[(state.length + 1) * Long.BYTES];
    protected final ByteBuffer buffer = ByteBuffer.wrap(_byte_buffer).order(ByteOrder.LITTLE_ENDIAN);
    protected long hashed_data_length = 0;

    @Override
    public void update(int b) {
        buffer.put((byte) b);
        hashed_data_length += 1;
        if (buffer.position() >= buffer_limit) round();
    }

    @Override
    public void update(long b) {
        buffer.putLong(b);
        hashed_data_length += Long.BYTES;
        if (buffer.position() >= buffer_limit) round();
    }

    @Override
    public void update(byte[] data, int off, int len) {
        int pos = 0;
        while (pos < len) {
            int n = Math.min(len - pos, buffer_limit - buffer.position());
            System.arraycopy(data, off + pos, _byte_buffer, buffer.position(), n);
            pos += n;
            buffer.position(buffer.position() + n);
            if (buffer.position() >= buffer_limit) round();
        }
        hashed_data_length += len;
    }

    @Override
    public long getValue() {
        if (buffer.position() != 0) {
            while (buffer.position() < buffer_limit) buffer.put((byte) 0);
            round();
        }
        long result = state[0];
        result ^= state[1]; result ^= state[2]; result ^= state[3];
        result ^= hashed_data_length;
        return xorshift64star(result);
    }

    protected void round() {
        int p = buffer.position();
        buffer.rewind();
        for (int i = 0; i < 4; i++) state[i] ^= buffer.getLong();
        for (int i = 0; i < 4; i++) state[i] = xorshift64star(state[i]);
        if (p > buffer_limit) {
            System.arraycopy(_byte_buffer, buffer_limit, _byte_buffer, 0, buffer.limit() - p);
            buffer.position(buffer.limit() - p);
        } else buffer.rewind();
    }

    protected long xorshift64star(long s) {
        s ^= (s >> 12); s ^= (s << 25); s ^= (s >> 27);
        return s * 0x2545F4914F6CDD1DL;
    }
}
