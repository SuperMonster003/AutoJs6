package net.dongliu.apk.parser.struct.xml;

import net.dongliu.apk.parser.struct.ChunkHeader;

/**
 * Binary XML header. It is simply a struct ResChunk_header.
 * The header.type is always 0×0003 (XML).
 *
 * @author dongliu
 */
public class XmlHeader extends ChunkHeader {
    public XmlHeader(final int chunkType, final int headerSize, final long chunkSize) {
        super(chunkType, headerSize, chunkSize);
    }
}
