package org.autojs.autojs.pluginclient;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class Buffer {

    public final int length;
    public final byte[] bytes;

    public Buffer(int length) {
        this.bytes = new byte[length];
        this.length = this.bytes.length;
    }

    public Buffer(byte[] bytes) {
        this.bytes = bytes;
        if (this.bytes != null) {
            this.length = this.bytes.length;
        } else {
            this.length = 0;
        }
    }

    public int readInt8(int offset) {
        return ((int) this.bytes[offset] & 0xff);
    }

    public int readInt16BE(int offset) {
        return (((int) this.bytes[offset + 2] & 0xff) << 8) |
                ((int) this.bytes[offset + 3] & 0xff);
    }

    public int readInt16LE(int offset) {
        return ((int) this.bytes[offset] & 0xff) |
                (((int) this.bytes[offset + 1] & 0xff) << 8);
    }

    public int readInt32BE(int offset) {
        return (((int) this.bytes[offset] & 0xff) << 24) |
                (((int) this.bytes[offset + 1] & 0xff) << 16) |
                (((int) this.bytes[offset + 2] & 0xff) << 8) |
                ((int) this.bytes[offset + 3] & 0xff);
    }

    public int readInt32LE(int offset) {
        return ((int) this.bytes[offset] & 0xff) |
                (((int) this.bytes[offset + 1] & 0xff) << 8) |
                (((int) this.bytes[offset + 2] & 0xff) << 16) |
                (((int) this.bytes[offset + 3] & 0xff) << 24);
    }

    public int readUInt8(int offset) {
        return this.readInt8(offset);
    }

    public int readUInt16BE(int offset) {
        return this.readInt16BE(offset);
    }

    public int readUInt16LE(int offset) {
        return this.readInt16LE(offset);
    }

    public int readUInt32BE(int offset) {
        return this.readInt32BE(offset);
    }

    public int readUInt32LE(int offset) {
        return this.readInt32LE(offset);
    }

    public void writeInt8(int value, int offset) {
        this.bytes[offset] = (byte) (value & 0xffL);
    }

    public void writeInt16BE(int value, int offset) {
        this.bytes[offset] = (byte) ((value >>> 8L) & 0xffL);
        this.bytes[offset + 1] = (byte) (value & 0xffL);

    }

    public void writeInt16LE(int value, int offset) {
        this.bytes[offset] = (byte) (value & 0xffL);
        this.bytes[offset + 1] = (byte) ((value >>> 8L) & 0xffL);
    }

    public void writeInt32BE(int value, int offset) {
        this.bytes[offset] = (byte) ((value >>> 24L) & 0xffL);
        this.bytes[offset + 1] = (byte) ((value >>> 16L) & 0xffL);
        this.bytes[offset + 2] = (byte) ((value >>> 8L) & 0xffL);
        this.bytes[offset + 3] = (byte) (value & 0xffL);
    }

    public void writeInt32LE(int value, int offset) {
        this.bytes[offset] = (byte) (value & 0xffL);
        this.bytes[offset + 1] = (byte) ((value >>> 8L) & 0xffL);
        this.bytes[offset + 2] = (byte) ((value >>> 16L) & 0xffL);
        this.bytes[offset + 3] = (byte) ((value >>> 24L) & 0xffL);
    }

    public void writeUInt8(int value, int offset) {
        this.writeInt8(value, offset);
    }

    public void writeUInt16BE(int value, int offset) {
        this.writeInt16BE(value, offset);
    }

    public void writeUInt16LE(int value, int offset) {
        this.writeInt16LE(value, offset);
    }

    public void writeUInt32BE(int value, int offset) {
        this.writeInt32BE(value, offset);
    }

    public void writeUInt32LE(int value, int offset) {
        this.writeInt32LE(value, offset);
    }

    public Buffer slice(int start, int end) {
        int len = end - start;
        if (len <= 0) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(this.bytes, start, len);
        return new Buffer(buffer.array());
    }

    @NonNull
    @Override
    public String toString() {
        return StandardCharsets.UTF_8.decode(ByteBuffer.wrap(this.bytes)).toString();
    }
}