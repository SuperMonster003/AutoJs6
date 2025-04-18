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
public class TypeHeader extends ChunkHeader {

    public static final long NO_ENTRY = 0xFFFFFFFFL;

    /**
     * The type identifier this chunk is holding.  Type IDs start at 1 (corresponding to the value
     * of the type bits in a resource identifier).  0 is invalid.
     * uint8_t
     */
    private final byte id;

    /**
     * Must be 0. uint8_t
     */
    private final byte flags;
    /**
     * Must be 0. uint16_t
     */
    private final short res;

    /**
     * Number of uint32_t entry indices that follow. uint32
     */
    public final int entryCount;

    /**
     * Offset from header where ResTable_entry data starts.uint32_t
     */
    public final int entriesStart;

    /**
     * Configuration this collection of entries is designed for.
     */
    @NonNull
    public final ResTableConfig config;

    public TypeHeader(final int headerSize, final long chunkSize, @NonNull final ByteBuffer buffer) {
        super(ChunkType.TABLE_TYPE, headerSize, chunkSize);
        this.id = Unsigned.toUByte(Buffers.readUByte(buffer));
        this.flags = Unsigned.toUByte(Buffers.readUByte(buffer));
        this.res = Unsigned.toUShort(Buffers.readUShort(buffer));
        this.entryCount = Unsigned.ensureUInt(Buffers.readUInt(buffer));
        this.entriesStart = Unsigned.ensureUInt(Buffers.readUInt(buffer));
        this.config = this.readResTableConfig(buffer);
    }

    public short getId() {
        return Unsigned.toShort(this.id);
    }

    public short getFlags() {
        return Unsigned.toUShort(this.flags);
    }

    public int getRes() {
        return Unsigned.toInt(this.res);
    }

    @NonNull
    private ResTableConfig readResTableConfig(final ByteBuffer buffer) {
        final long beginPos = buffer.position();
        final ResTableConfig config = new ResTableConfig();
        final long size = Buffers.readUInt(buffer);
        // imsi
        config.setMcc(buffer.getShort());
        config.setMnc(buffer.getShort());
        //read locale
        config.setLanguage(new String(Buffers.readBytes(buffer, 2)).replace("\0", ""));
        config.setCountry(new String(Buffers.readBytes(buffer, 2)).replace("\0", ""));
        //screen type
        config.setOrientation(Buffers.readUByte(buffer));
        config.setTouchscreen(Buffers.readUByte(buffer));
        config.setDensity(Buffers.readUShort(buffer));
        // now just skip the others...
        final long endPos = buffer.position();
        Buffers.skip(buffer, (int) (size - (endPos - beginPos)));
        return config;
    }
}
