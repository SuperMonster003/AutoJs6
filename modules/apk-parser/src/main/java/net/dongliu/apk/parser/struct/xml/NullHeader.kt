package net.dongliu.apk.parser.struct.xml

import net.dongliu.apk.parser.struct.ChunkHeader

/**
 * Null header.
 *
 * @author dongliu
 */
class NullHeader(chunkType: Int, headerSize: Int, chunkSize: Long) :
    ChunkHeader(chunkType, headerSize, chunkSize)
