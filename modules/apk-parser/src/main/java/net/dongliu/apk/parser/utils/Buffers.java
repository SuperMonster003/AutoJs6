package net.dongliu.apk.parser.utils;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * utils method for byte buffer
 * <p>
 * Cast java.nio.ByteBuffer instances where necessary to java.nio.Buffer to avoid NoSuchMethodError
 * when running on Java 6 to Java 8.
 * The Java 9 ByteBuffer classes introduces overloaded methods with covariant return types the following methods:
 * position, limit, flip, clear, mark, reset, rewind, etc.
 *
 * @author Liu Dong dongliu@live.cn
 */
public class Buffers {

    /**
     * get one unsigned byte as short type
     */
    public static short readUByte(final @NonNull ByteBuffer buffer) {
        final byte b = buffer.get();
        return (short) (b & 0xff);
    }

    /**
     * get one unsigned short as int type
     */
    public static int readUShort(final @NonNull ByteBuffer buffer) {
        final short s = buffer.getShort();
        return s & 0xffff;
    }

    /**
     * get one unsigned int as long type
     */
    public static long readUInt(final @NonNull ByteBuffer buffer) {
        final int i = buffer.getInt();
        return i & 0xffffffffL;
    }

    /**
     * get bytes
     */
    @NonNull
    public static byte[] readBytes(final @NonNull ByteBuffer buffer, final int size) {
        final byte[] bytes = new byte[size];
        buffer.get(bytes);
        return bytes;
    }

    /**
     * get all bytes remains
     */
    @NonNull
    public static byte[] readBytes(final @NonNull ByteBuffer buffer) {
        return Buffers.readBytes(buffer, buffer.remaining());
    }

    /**
     * Read ascii string ,by len
     */
    @NonNull
    public static String readAsciiString(final @NonNull ByteBuffer buffer, final int strLen) {
        final byte[] bytes = new byte[strLen];
        buffer.get(bytes);
        return new String(bytes);
    }

    /**
     * read utf16 strings, use strLen, not ending 0 char.
     */
    @NonNull
    public static String readString(final @NonNull ByteBuffer buffer, final int strLen) {
        final StringBuilder sb = new StringBuilder(strLen);
        for (int i = 0; i < strLen; i++) {
            sb.append(buffer.getChar());
        }
        return sb.toString();
    }

    /**
     * read utf16 strings, ending with 0 char.
     */
    @NonNull
    public static String readZeroTerminatedString(final @NonNull ByteBuffer buffer, final int strLen) {
        final StringBuilder sb = new StringBuilder(strLen);
        for (int i = 0; i < strLen; i++) {
            final char c = buffer.getChar();
            if (c == '\0') {
                Buffers.skip(buffer, (strLen - i - 1) * 2);
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * skip count bytes
     */
    public static void skip(final @NonNull ByteBuffer buffer, final int count) {
        Buffers.position(buffer, buffer.position() + count);
    }

    /**
     * set position
     */
    public static void position(final @NonNull ByteBuffer buffer, final int position) {
        buffer.position(position);
    }

    /**
     * set position
     */
    public static void position(final @NonNull ByteBuffer buffer, final long position) {
        Buffers.position(buffer, Unsigned.ensureUInt(position));
    }

    /**
     * Return one new ByteBuffer from current position, with size, the byte order of new buffer will be set to little endian;
     * And advance the original buffer with size.
     */
    @NonNull
    public static ByteBuffer sliceAndSkip(final @NonNull ByteBuffer buffer, final int size) {
        final ByteBuffer buf = buffer.slice().order(ByteOrder.LITTLE_ENDIAN);
        final ByteBuffer slice = (ByteBuffer) buf.limit(buf.position() + size);
        Buffers.skip(buffer, size);
        return slice;
    }
}
