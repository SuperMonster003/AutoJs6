package net.dongliu.apk.parser.struct.resource;

import androidx.annotation.NonNull;

import net.dongliu.apk.parser.struct.ChunkHeader;
import net.dongliu.apk.parser.struct.ChunkType;
import net.dongliu.apk.parser.utils.Buffers;
import net.dongliu.apk.parser.utils.Unsigned;

import java.nio.ByteBuffer;

/**
 * resource file header
 *
 * @author dongliu
 */
public class ResourceTableHeader extends ChunkHeader {
    /**
     * The number of ResTable_package structures. uint32
     */
    private final int packageCount;

    public ResourceTableHeader(final int headerSize, final int chunkSize, final @NonNull ByteBuffer buffer) {
        super(ChunkType.TABLE, headerSize, chunkSize);
        this.packageCount = Unsigned.toUInt(Buffers.readUInt(buffer));
    }

    public long getPackageCount() {
        return Unsigned.toLong(this.packageCount);
    }

}
