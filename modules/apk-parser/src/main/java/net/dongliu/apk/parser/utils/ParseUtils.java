package net.dongliu.apk.parser.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.dongliu.apk.parser.exception.ParserException;
import net.dongliu.apk.parser.parser.StringPoolEntry;
import net.dongliu.apk.parser.struct.ResValue;
import net.dongliu.apk.parser.struct.ResourceValue;
import net.dongliu.apk.parser.struct.StringPool;
import net.dongliu.apk.parser.struct.StringPoolHeader;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * @author dongliu
 */
public class ParseUtils {

    public static final Charset charsetUTF8 = StandardCharsets.UTF_8;

    /**
     * read string from input buffer. if get EOF before read enough data, throw IOException.
     */
    @NonNull
    public static String readString(@NonNull final ByteBuffer buffer, final boolean utf8) {
        if (utf8) {
            //  The lengths are encoded in the same way as for the 16-bit format
            // but using 8-bit rather than 16-bit integers.
            final int strLen = ParseUtils.readLen(buffer);
            final int bytesLen = ParseUtils.readLen(buffer);
            final byte[] bytes = Buffers.readBytes(buffer, bytesLen);
            final String str = new String(bytes, ParseUtils.charsetUTF8);
            // zero
            final int trailling = Buffers.readUByte(buffer);
            return str;
        } else {
            // The length is encoded as either one or two 16-bit integers as per the commentRef...
            final int strLen = ParseUtils.readLen16(buffer);
            final String str = Buffers.readString(buffer, strLen);
            // zero
            final int trailling = Buffers.readUShort(buffer);
            return str;
        }
    }

    /**
     * read utf-16 encoding str, use zero char to end str.
     */
    @NonNull
    public static String readStringUTF16(@NonNull final ByteBuffer buffer, final int strLen) {
        final String str = Buffers.readString(buffer, strLen);
        for (int i = 0; i < str.length(); i++) {
            final char c = str.charAt(i);
            if (c == 0) {
                return str.substring(0, i);
            }
        }
        return str;
    }

    /**
     * read encoding len.
     * see StringPool.cpp ENCODE_LENGTH
     */
    private static int readLen(@NonNull final ByteBuffer buffer) {
        int len = 0;
        final int i = Buffers.readUByte(buffer);
        if ((i & 0x80) != 0) {
            //read one more byte.
            len |= (i & 0x7f) << 8;
            len += Buffers.readUByte(buffer);
        } else {
            len = i;
        }
        return len;
    }

    /**
     * read encoding len.
     * see Stringpool.cpp ENCODE_LENGTH
     */
    private static int readLen16(@NonNull final ByteBuffer buffer) {
        int len = 0;
        final int i = Buffers.readUShort(buffer);
        if ((i & 0x8000) != 0) {
            len |= (i & 0x7fff) << 16;
            len += Buffers.readUShort(buffer);
        } else {
            len = i;
        }
        return len;
    }

    /**
     * read String pool, for apk binary xml file and resource table.
     */
    @NonNull
    public static StringPool readStringPool(final @NonNull ByteBuffer buffer, final @NonNull StringPoolHeader stringPoolHeader) {
        final long beginPos = buffer.position();
        final int[] offsets = new int[stringPoolHeader.getStringCount()];
        // read strings offset
        if (stringPoolHeader.getStringCount() > 0) {
            for (int idx = 0; idx < stringPoolHeader.getStringCount(); idx++) {
                offsets[idx] = Unsigned.toUInt(Buffers.readUInt(buffer));
            }
        }
        // read flag
        // the string index is sorted by the string values if true
        final boolean sorted = (stringPoolHeader.getFlags() & StringPoolHeader.SORTED_FLAG) != 0;
        // string use utf-8 format if true, otherwise utf-16
        final boolean utf8 = (stringPoolHeader.getFlags() & StringPoolHeader.UTF8_FLAG) != 0;
        // read strings. the head and metas have 28 bytes
        final long stringPos = beginPos + stringPoolHeader.getStringsStart() - (int) stringPoolHeader.headerSize;
        Buffers.position(buffer, stringPos);
        final StringPoolEntry[] entries = new StringPoolEntry[offsets.length];
        for (int i = 0; i < offsets.length; i++) {
            entries[i] = new StringPoolEntry(i, stringPos + Unsigned.toLong(offsets[i]));
        }
        String lastStr = null;
        long lastOffset = -1;
        final StringPool stringPool = new StringPool(stringPoolHeader.getStringCount());
        for (final StringPoolEntry entry : entries) {
            if (entry.offset == lastOffset) {
                stringPool.set(entry.idx, lastStr);
                continue;
            }
            Buffers.position(buffer, entry.offset);
            lastOffset = entry.offset;
            final String str = ParseUtils.readString(buffer, utf8);
            lastStr = str;
            stringPool.set(entry.idx, str);
        }
        // read styles
        //noinspection StatementWithEmptyBody
        if (stringPoolHeader.getStyleCount() > 0) {
            // now we just skip it
        }
        Buffers.position(buffer, beginPos + stringPoolHeader.getBodySize());
        return stringPool;
    }

    /**
     * read res value, convert from different types to string.
     */
    @Nullable
    public static ResourceValue readResValue(final @NonNull ByteBuffer buffer, final StringPool stringPool) {
//        ResValue resValue = new ResValue();
        final int size = Buffers.readUShort(buffer);
        final short res0 = Buffers.readUByte(buffer);
        final short dataType = Buffers.readUByte(buffer);
        switch (dataType) {
            case ResValue.ResType.INT_DEC:
                return ResourceValue.decimal(buffer.getInt());
            case ResValue.ResType.INT_HEX:
                return ResourceValue.hexadecimal(buffer.getInt());
            case ResValue.ResType.STRING:
                final int strRef = buffer.getInt();
                if (strRef >= 0) {
                    return ResourceValue.string(strRef, stringPool);
                } else {
                    return null;
                }
            case ResValue.ResType.REFERENCE:
            case ResValue.ResType.TYPE_DYNAMIC_REFERENCE:
                return ResourceValue.reference(buffer.getInt());
            case ResValue.ResType.INT_BOOLEAN:
                return ResourceValue.bool(buffer.getInt());
            case ResValue.ResType.NULL:
                return ResourceValue.nullValue();
            case ResValue.ResType.INT_COLOR_RGB8:
            case ResValue.ResType.INT_COLOR_RGB4:
                return ResourceValue.rgb(buffer.getInt(), 6);
            case ResValue.ResType.INT_COLOR_ARGB8:
            case ResValue.ResType.INT_COLOR_ARGB4:
                return ResourceValue.rgb(buffer.getInt(), 8);
            case ResValue.ResType.DIMENSION:
                return ResourceValue.dimension(buffer.getInt());
            case ResValue.ResType.FRACTION:
                return ResourceValue.fraction(buffer.getInt());
            default:
                return ResourceValue.raw(buffer.getInt(), dataType);
        }
    }

    public static void checkChunkType(final int expected, final int real) {
        if (expected != real) {
            throw new ParserException("Expect chunk type:" + Integer.toHexString(expected)
                    + ", but got:" + Integer.toHexString(real));
        }
    }

}
