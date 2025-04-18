package net.dongliu.apk.parser.struct.resource

import net.dongliu.apk.parser.struct.*

class NullHeader(headerSize: Int, chunkSize: Int) :
    ChunkHeader(ChunkType.NULL, headerSize, chunkSize.toLong())
