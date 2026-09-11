package org.autojs.autojs.core.plugin.nativealign

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Reads only the bounded ELF prefix. Invalid or incomplete program headers fail closed. */
object ElfLoadAlignment {
    const val PREFIX_SIZE = 65536
    data class Load(val offset: Long, val address: Long, val alignment: Long)
    data class Result(val machine: Int, val loads: List<Load>, val relroEnds: List<Long>) {
        val minLoadAlign: Long get() = loads.minOf { it.alignment }
        val abi: String get() = when (machine) {
            183 -> "arm64-v8a"
            62 -> "x86_64"
            40 -> "armeabi-v7a"
            3 -> "x86"
            else -> "elf-machine-$machine"
        }
    }

    fun parse(prefix: ByteArray): Result {
        require(prefix.size >= 16 && prefix.take(4) == listOf<Byte>(0x7f, 0x45, 0x4c, 0x46)) { "Not an ELF file" }
        val is64 = when (prefix[4].toInt()) { 1 -> false; 2 -> true; else -> throw IllegalArgumentException("Invalid ELF class") }
        val order = when (prefix[5].toInt()) { 1 -> ByteOrder.LITTLE_ENDIAN; 2 -> ByteOrder.BIG_ENDIAN; else -> throw IllegalArgumentException("Invalid ELF byte order") }
        require(prefix[6].toInt() == 1) { "Invalid ELF version" }
        val headerSize = if (is64) 64 else 52
        require(prefix.size >= headerSize) { "Truncated ELF header" }
        val data = ByteBuffer.wrap(prefix).order(order)
        fun u16(at: Int) = data.getShort(at).toInt() and 0xffff
        fun u32(at: Int) = data.getInt(at).toLong() and 0xffffffffL
        fun word(at: Int) = (if (is64) data.getLong(at) else u32(at)).also {
            require(it >= 0) { "ELF integer exceeds supported range" }
        }
        require(u32(20) == 1L) { "Invalid ELF header version" }
        val phoff = word(if (is64) 32 else 28)
        val entrySize = u16(if (is64) 54 else 42)
        val count = u16(if (is64) 56 else 44)
        require(count in 1 until 0xffff && entrySize >= if (is64) 56 else 32) { "Invalid ELF program header table" }
        require(phoff >= headerSize && phoff <= prefix.size && count.toLong() * entrySize <= prefix.size - phoff) {
            "ELF program header table exceeds the 64 KiB prefix"
        }
        val loads = mutableListOf<Load>()
        val relroEnds = mutableListOf<Long>()
        repeat(count) { index ->
            val at = (phoff + index.toLong() * entrySize).toInt()
            val type = u32(at)
            val offset = word(at + if (is64) 8 else 4)
            val address = word(at + if (is64) 16 else 8)
            if (type == 1L) {
                val align = word(at + if (is64) 48 else 28)
                require(align > 0 && align and (align - 1) == 0L) { "Invalid PT_LOAD alignment: $align" }
                require(offset % align == address % align) { "Incongruent PT_LOAD offset and address" }
                loads += Load(offset, address, align)
            } else if (type == 0x6474e552L) {
                relroEnds += Math.addExact(address, word(at + if (is64) 40 else 20))
            }
        }
        require(loads.isNotEmpty()) { "ELF has no PT_LOAD segments" }
        return Result(u16(18), loads, relroEnds)
    }
}
