package net.dongliu.apk.parser.struct.xml

import net.dongliu.apk.parser.struct.ChunkHeader

/**
 * @author dongliu
 */
class XmlResourceMapHeader(chunkType: Int, headerSize: Int, chunkSize: Long) :
    ChunkHeader(chunkType, headerSize, chunkSize)
