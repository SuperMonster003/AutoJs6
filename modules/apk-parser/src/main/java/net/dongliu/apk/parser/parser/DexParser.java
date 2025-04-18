package net.dongliu.apk.parser.parser;

import androidx.annotation.NonNull;

import net.dongliu.apk.parser.bean.DexClass;
import net.dongliu.apk.parser.exception.ParserException;
import net.dongliu.apk.parser.struct.StringPool;
import net.dongliu.apk.parser.struct.dex.DexClassStruct;
import net.dongliu.apk.parser.struct.dex.DexHeader;
import net.dongliu.apk.parser.utils.Buffers;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * parse dex file.
 * current we only get the class name.
 * see:
 * http://source.android.com/devices/tech/dalvik/dex-format.html
 * http://dexandroid.googlecode.com/svn/trunk/dalvik/libdex/DexFile.h
 *
 * @author dongliu
 */
public class DexParser {

    private final ByteBuffer buffer;

    private static final int NO_INDEX = 0xffffffff;

    public DexParser(final @NonNull ByteBuffer buffer) {
        this.buffer = buffer.duplicate();
        this.buffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    @NonNull
    public DexClass[] parse() {
        // read magic
        final String magic = new String(Buffers.readBytes(this.buffer, 8));
        if (!magic.startsWith("dex\n")) {
            return new DexClass[0];
        }
        final int version = Integer.parseInt(magic.substring(4, 7));
        // now the version is 035
        if (version < 35) {
            // version 009 was used for the M3 releases of the Android platform (November–December 2007),
            // and version 013 was used for the M5 releases of the Android platform (February–March 2008)
            throw new ParserException("Dex file version: " + version + " is not supported");
        }
        // read header
        final DexHeader header = this.readDexHeader();
        header.setVersion(version);
        // read string pool
        final long[] stringOffsets = this.readStringPool(header.getStringIdsOff(), header.getStringIdsSize());
        // read types
        final int[] typeIds = this.readTypes(header.getTypeIdsOff(), header.getTypeIdsSize());
        // read classes
        final DexClassStruct[] dexClassStructs = this.readClass(header.getClassDefsOff(),
                header.getClassDefsSize());
        final StringPool stringpool = this.readStrings(stringOffsets);
        final String[] types = new String[typeIds.length];
        for (int i = 0; i < typeIds.length; i++) {
            types[i] = stringpool.get(typeIds[i]);
        }
        final DexClass[] dexClasses = new DexClass[dexClassStructs.length];
        for (int i = 0; i < dexClassStructs.length; i++) {
            final DexClassStruct dexClassStruct = dexClassStructs[i];
            String superClass = null;
            if (dexClassStruct.getSuperclassIdx() != DexParser.NO_INDEX) {
                superClass = types[dexClassStruct.getSuperclassIdx()];
            }
            dexClasses[i] = new DexClass(
                    types[dexClassStruct.getClassIdx()],
                    superClass,
                    dexClassStruct.getAccessFlags());
        }
        return dexClasses;
    }

    /**
     * read class info.
     */
    private DexClassStruct[] readClass(final long classDefsOff, final int classDefsSize) {
        Buffers.position(this.buffer, classDefsOff);
        final DexClassStruct[] dexClassStructs = new DexClassStruct[classDefsSize];
        for (int i = 0; i < classDefsSize; i++) {
            final DexClassStruct dexClassStruct = new DexClassStruct();
            dexClassStruct.setClassIdx(this.buffer.getInt());
            dexClassStruct.setAccessFlags(this.buffer.getInt());
            dexClassStruct.setSuperclassIdx(this.buffer.getInt());
            dexClassStruct.setInterfacesOff(Buffers.readUInt(this.buffer));
            dexClassStruct.setSourceFileIdx(this.buffer.getInt());
            dexClassStruct.setAnnotationsOff(Buffers.readUInt(this.buffer));
            dexClassStruct.setClassDataOff(Buffers.readUInt(this.buffer));
            dexClassStruct.setStaticValuesOff(Buffers.readUInt(this.buffer));
            dexClassStructs[i] = dexClassStruct;
        }
        return dexClassStructs;
    }

    /**
     * read types.
     */
    private int[] readTypes(final long typeIdsOff, final int typeIdsSize) {
        Buffers.position(this.buffer, typeIdsOff);
        final int[] typeIds = new int[typeIdsSize];
        for (int i = 0; i < typeIdsSize; i++) {
            typeIds[i] = (int) Buffers.readUInt(this.buffer);
        }
        return typeIds;
    }

    /**
     * read string pool for dex file.
     * dex file string pool diff a bit with binary xml file or resource table.
     */
    private StringPool readStrings(final long[] offsets) {
        // read strings.
        // buffer some apk, the strings' offsets may not well ordered. we sort it first
        final StringPoolEntry[] entries = new StringPoolEntry[offsets.length];
        for (int i = 0; i < offsets.length; i++) {
            entries[i] = new StringPoolEntry(i, offsets[i]);
        }
        String lastStr = null;
        long lastOffset = -1;
        final StringPool stringpool = new StringPool(offsets.length);
        for (final StringPoolEntry entry : entries) {
            if (entry.offset == lastOffset) {
                stringpool.set(entry.idx, lastStr);
                continue;
            }
            Buffers.position(this.buffer, entry.offset);
            lastOffset = entry.offset;
            final String str = this.readString();
            lastStr = str;
            stringpool.set(entry.idx, str);
        }
        return stringpool;
    }

    /*
     * read string identifiers list.
     */
    private long[] readStringPool(final long stringIdsOff, final int stringIdsSize) {
        Buffers.position(this.buffer, stringIdsOff);
        final long[] offsets = new long[stringIdsSize];
        for (int i = 0; i < stringIdsSize; i++) {
            offsets[i] = Buffers.readUInt(this.buffer);
        }
        return offsets;
    }

    /**
     * read dex encoding string.
     */
    @NonNull
    private String readString() {
        // the length is char len, not byte len
        final int strLen = this.readVarInts();
        return this.readString(strLen);
    }

    /**
     * read Modified UTF-8 encoding str.
     *
     * @param strLen the java-utf16-char len, not strLen nor bytes len.
     */
    @NonNull
    private String readString(final int strLen) {
        final char[] chars = new char[strLen];
        for (int i = 0; i < strLen; i++) {
            final short a = Buffers.readUByte(this.buffer);
            if ((a & 0x80) == 0) {
                // ascii char
                chars[i] = (char) a;
            } else if ((a & 0xe0) == 0xc0) {
                // read one more
                final short b = Buffers.readUByte(this.buffer);
                chars[i] = (char) (((a & 0x1F) << 6) | (b & 0x3F));
            } else if ((a & 0xf0) == 0xe0) {
                final short b = Buffers.readUByte(this.buffer);
                final short c = Buffers.readUByte(this.buffer);
                chars[i] = (char) (((a & 0x0F) << 12) | ((b & 0x3F) << 6) | (c & 0x3F));
            } else //noinspection StatementWithEmptyBody
                if ((a & 0xf0) == 0xf0) {
                    //throw new UTFDataFormatException();
                } else {
                    //throw new UTFDataFormatException();
                }
            //noinspection StatementWithEmptyBody
            if (chars[i] == 0) {
                // the end of string.
            }
        }
        return new String(chars);
    }

    /**
     * read varints.
     */
    private int readVarInts() {
        int i = 0;
        int count = 0;
        short s;
        do {
            if (count > 4) {
                throw new ParserException("read varints error.");
            }
            s = Buffers.readUByte(this.buffer);
            i |= (s & 0x7f) << (count * 7);
            count++;
        } while ((s & 0x80) != 0);
        return i;
    }

    private DexHeader readDexHeader() {
        // check sum. skip
        this.buffer.getInt();
        // signature skip
        Buffers.readBytes(this.buffer, DexHeader.kSHA1DigestLen);
        final DexHeader header = new DexHeader();
        header.setFileSize(Buffers.readUInt(this.buffer));
        header.setHeaderSize(Buffers.readUInt(this.buffer));
        // skip?
        Buffers.readUInt(this.buffer);
        // static link data
        header.setLinkSize(Buffers.readUInt(this.buffer));
        header.setLinkOff(Buffers.readUInt(this.buffer));
        // the map data is just the same as dex header.
        header.setMapOff(Buffers.readUInt(this.buffer));
        header.setStringIdsSize(this.buffer.getInt());
        header.setStringIdsOff(Buffers.readUInt(this.buffer));
        header.setTypeIdsSize(this.buffer.getInt());
        header.setTypeIdsOff(Buffers.readUInt(this.buffer));
        header.setProtoIdsSize(this.buffer.getInt());
        header.setProtoIdsOff(Buffers.readUInt(this.buffer));
        header.setFieldIdsSize(this.buffer.getInt());
        header.setFieldIdsOff(Buffers.readUInt(this.buffer));
        header.setMethodIdsSize(this.buffer.getInt());
        header.setMethodIdsOff(Buffers.readUInt(this.buffer));
        header.setClassDefsSize(this.buffer.getInt());
        header.setClassDefsOff(Buffers.readUInt(this.buffer));
        header.setDataSize(this.buffer.getInt());
        header.setDataOff(Buffers.readUInt(this.buffer));
        Buffers.position(this.buffer, header.getHeaderSize());
        return header;
    }

}
