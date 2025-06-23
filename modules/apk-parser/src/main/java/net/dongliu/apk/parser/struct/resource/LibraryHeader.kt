package net.dongliu.apk.parser.struct.resource

import net.dongliu.apk.parser.struct.*
import net.dongliu.apk.parser.utils.Buffers
import net.dongliu.apk.parser.utils.Unsigned.ensureUInt
import java.nio.ByteBuffer

/**
 * Table library chunk header
 *
 * @author Liu Dong
 */
class LibraryHeader(headerSize: Int, chunkSize: Long, buffer: ByteBuffer) :
    ChunkHeader(ChunkType.TABLE_LIBRARY, headerSize, chunkSize) {
    /**
     * uint32 value, The number of shared libraries linked in this resource table.
     */
    val count: Int

    init {
        count = ensureUInt(Buffers.readUInt(buffer))
    }
}
