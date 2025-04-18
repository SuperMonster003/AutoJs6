package net.dongliu.apk.parser.struct.xml;

import androidx.annotation.NonNull;

import net.dongliu.apk.parser.struct.ChunkHeader;
import net.dongliu.apk.parser.utils.Buffers;

import java.nio.ByteBuffer;

/**
 * @author dongliu
 */
public class XmlNodeHeader extends ChunkHeader {
    /**
     * Line number in original source file at which this element appeared.
     */
    public final int lineNum;
    /**
     * Optional XML comment string pool ref, -1 if none
     */
    public final int commentRef;

    public XmlNodeHeader(final int chunkType, final int headerSize, final long chunkSize, final @NonNull ByteBuffer buffer) {
        super(chunkType, headerSize, chunkSize);
        this.lineNum = (int) Buffers.readUInt(buffer);
        this.commentRef = (int) Buffers.readUInt(buffer);
    }

}
