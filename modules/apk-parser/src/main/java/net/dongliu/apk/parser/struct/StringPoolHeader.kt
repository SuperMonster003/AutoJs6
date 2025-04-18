package net.dongliu.apk.parser.struct

import net.dongliu.apk.parser.utils.*
import java.nio.ByteBuffer

/**
 * String pool chunk header.
 *
 * @author dongliu
 */
@Suppress("MemberVisibilityCanBePrivate")
class StringPoolHeader(headerSize: Int, chunkSize: Long, buffer: ByteBuffer) :
    ChunkHeader(ChunkType.STRING_POOL, headerSize, chunkSize) {
    /**
     * Number of style span arrays in the pool (number of uint32_t indices
     * follow the string indices).
     */
    val stringCount: Int

    /**
     * Number of style span arrays in the pool (number of uint32_t indices
     * follow the string indices).
     */
    val styleCount: Int
    val flags: Long

    /**
     * Index from header of the string data.
     */
    val stringsStart: Long

    /**
     * Index from header of the style data.
     */
    val stylesStart: Long

    init {
        stringCount = Unsigned.ensureUInt(Buffers.readUInt(buffer))
        this.styleCount = Unsigned.ensureUInt(Buffers.readUInt(buffer))
        flags = Buffers.readUInt(buffer)
        stringsStart = Buffers.readUInt(buffer)
        stylesStart = Buffers.readUInt(buffer)
    }

    companion object {
        /**
         * If set, the string index is sorted by the string values (based on strcmp16()).
         */
        const val SORTED_FLAG = 1

        /**
         * String pool is encoded in UTF-8
         */
        const val UTF8_FLAG = 1 shl 8
    }
}
