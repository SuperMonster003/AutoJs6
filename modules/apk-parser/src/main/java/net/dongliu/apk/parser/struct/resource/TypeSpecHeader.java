package net.dongliu.apk.parser.struct.resource;

import androidx.annotation.NonNull;

import net.dongliu.apk.parser.struct.ChunkHeader;
import net.dongliu.apk.parser.struct.ChunkType;
import net.dongliu.apk.parser.utils.Buffers;
import net.dongliu.apk.parser.utils.Unsigned;

import java.nio.ByteBuffer;

/**
 * @author dongliu
 */
public class TypeSpecHeader extends ChunkHeader {

    /**
     * The type identifier this chunk is holding.  Type IDs start at 1 (corresponding to the value
     * of the type bits in a resource identifier).  0 is invalid.
     * The id also specifies the name of the Resource type. It is the string at index id - 1 in the
     * typeStrings StringPool chunk in the containing Package chunk.
     * uint8_t
     */
    private final byte id;

    /**
     * Must be 0. uint8_t
     */
    private final byte res0;

    /**
     * Must be 0.uint16_t
     */
    private final short res1;

    /**
     * Number of uint32_t entry configuration masks that follow.
     */
    private final int entryCount;

    public TypeSpecHeader(final int headerSize, final long chunkSize, final @NonNull ByteBuffer buffer) {
        super(ChunkType.TABLE_TYPE_SPEC, headerSize, chunkSize);
        this.id = Unsigned.toUByte(Buffers.readUByte(buffer));
        this.res0 = Unsigned.toUByte(Buffers.readUByte(buffer));
        this.res1 = Unsigned.toUShort(Buffers.readUShort(buffer));
        this.entryCount = Unsigned.ensureUInt(Buffers.readUInt(buffer));
    }

    public short getId() {
        return Unsigned.toShort(this.id);
    }

    public short getRes0() {
        return Unsigned.toShort(this.res0);
    }

    public int getRes1() {
        return Unsigned.toInt(this.res1);
    }

    public int getEntryCount() {
        return this.entryCount;
    }

}
