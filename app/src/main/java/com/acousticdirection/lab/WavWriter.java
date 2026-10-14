package com.acousticdirection.lab;

import java.io.*;
import java.nio.*;

public final class WavWriter implements AutoCloseable {
    private final RandomAccessFile out;
    private final int rate, channels;
    private long dataBytes;
    public WavWriter(File file, int channels, int rate) throws IOException {
        this.rate = rate; this.channels = channels;
        out = new RandomAccessFile(file, "rw");
        out.setLength(0); out.write(new byte[44]);
    }
    public void write(short[] data, int count) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < count; i++) b.putShort(data[i]);
        out.write(b.array());
        dataBytes += count * 2L;
    }
    @Override public void close() throws IOException {
        ByteBuffer b = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes("US-ASCII")); b.putInt((int)(36 + dataBytes));
        b.put("WAVEfmt ".getBytes("US-ASCII")); b.putInt(16);
        b.putShort((short)1); b.putShort((short)channels);
        b.putInt(rate); b.putInt(rate * channels * 2);
        b.putShort((short)(channels * 2)); b.putShort((short)16);
        b.put("data".getBytes("US-ASCII")); b.putInt((int)dataBytes);
        out.seek(0); out.write(b.array()); out.close();
    }
}
