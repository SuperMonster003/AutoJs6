package org.autojs.autojs.core.plugin.nativealign

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Dynamic linking facts of an ELF shared library, read from its section headers.
 *
 * Only the dynamic section, the dynamic symbol table, their string tables and note sections are
 * read, so large libraries are not loaded into memory. Malformed input fails with
 * [IllegalArgumentException].
 *
 * zh-CN: 从节头读取 ELF 共享库的动态链接信息. 只读取动态段, 动态符号表, 对应字符串表与注记节,
 * 不会把大型库整体载入内存. 格式错误时抛出 [IllegalArgumentException].
 */
class ElfDynamicInfo(
    /** DT_NEEDED entries in file order. */
    val needed: List<String>,
    /** Names of defined GLOBAL or WEAK dynamic symbols. */
    val definedSymbols: Set<String>,
    /** Names of undefined GLOBAL (non-weak) dynamic symbols. */
    val requiredSymbols: Set<String>,
    /** The NT_ANDROID_TYPE_IDENT note written by NDK toolchains, if present. */
    val androidIdent: AndroidIdent?,
) {

    data class AndroidIdent(val apiLevel: Int, val ndkVersion: String, val ndkBuildNumber: String) {
        /** Toolchain build number, used to order runtimes from the same NDK lineage. */
        val buildNumber: Long
            get() = ndkBuildNumber.trim().toLongOrNull() ?: 0L
    }

    companion object {

        private const val SHT_DYNAMIC = 6
        private const val SHT_NOTE = 7
        private const val SHT_DYNSYM = 11
        private const val DT_NULL = 0L
        private const val DT_NEEDED = 1L
        private const val NT_ANDROID_TYPE_IDENT = 1
        private const val STB_GLOBAL = 1
        private const val STB_WEAK = 2
        private const val MAX_SECTION_BYTES = 64L shl 20

        @JvmStatic
        fun read(file: File): ElfDynamicInfo = RandomAccessFile(file, "r").use { read(it) }

        fun read(input: RandomAccessFile): ElfDynamicInfo {
            val ident = ByteArray(16).also { readFully(input, 0, it) }
            require(ident[0] == 0x7f.toByte() && ident[1] == 'E'.code.toByte() && ident[2] == 'L'.code.toByte() && ident[3] == 'F'.code.toByte()) {
                "Not an ELF file"
            }
            val is64 = when (ident[4].toInt()) {
                1 -> false
                2 -> true
                else -> throw IllegalArgumentException("Invalid ELF class")
            }
            val order = when (ident[5].toInt()) {
                1 -> ByteOrder.LITTLE_ENDIAN
                2 -> ByteOrder.BIG_ENDIAN
                else -> throw IllegalArgumentException("Invalid ELF byte order")
            }
            val header = buffer(input, 0, if (is64) 64 else 52, order)
            val sectionOffset = if (is64) header.getLong(40) else header.getInt(32).toUnsignedLong()
            val sectionEntrySize = header.getShort(if (is64) 58 else 46).toUnsignedInt()
            val sectionCount = header.getShort(if (is64) 60 else 48).toUnsignedInt()
            require(sectionCount > 0 && sectionOffset > 0) { "ELF has no section headers" }
            require(sectionEntrySize >= if (is64) 64 else 40) { "Invalid ELF section header size" }
            val table = buffer(input, sectionOffset, sectionEntrySize.toLong() * sectionCount, order)
            val sections = List(sectionCount) { index ->
                val at = index * sectionEntrySize
                if (is64) {
                    Section(
                        type = table.getInt(at + 4),
                        offset = table.getLong(at + 24),
                        size = table.getLong(at + 32),
                        link = table.getInt(at + 40),
                        entrySize = table.getLong(at + 56),
                    )
                } else {
                    Section(
                        type = table.getInt(at + 4),
                        offset = table.getInt(at + 16).toUnsignedLong(),
                        size = table.getInt(at + 20).toUnsignedLong(),
                        link = table.getInt(at + 24),
                        entrySize = table.getInt(at + 36).toUnsignedLong(),
                    )
                }
            }
            val strings = HashMap<Int, ByteArray>()
            fun stringTable(index: Int): ByteArray = strings.getOrPut(index) {
                val section = sections.getOrNull(index) ?: throw IllegalArgumentException("Invalid ELF string table index")
                bytes(input, section.offset, section.size)
            }

            val needed = mutableListOf<String>()
            sections.filter { it.type == SHT_DYNAMIC }.forEach { section ->
                val names = stringTable(section.link)
                val entrySize = if (is64) 16 else 8
                val data = buffer(input, section.offset, section.size, order)
                var at = 0
                while (at + entrySize <= data.capacity()) {
                    val tag = if (is64) data.getLong(at) else data.getInt(at).toLong()
                    val value = if (is64) data.getLong(at + 8) else data.getInt(at + 4).toUnsignedLong()
                    if (tag == DT_NULL) break
                    if (tag == DT_NEEDED) needed += cString(names, value)
                    at += entrySize
                }
            }

            val defined = HashSet<String>()
            val required = HashSet<String>()
            sections.filter { it.type == SHT_DYNSYM }.forEach { section ->
                val names = stringTable(section.link)
                val entrySize = if (is64) 24 else 16
                val data = buffer(input, section.offset, section.size, order)
                var at = entrySize // Entry 0 is the reserved null symbol.
                while (at + entrySize <= data.capacity()) {
                    val nameOffset = data.getInt(at).toUnsignedLong()
                    val info = (if (is64) data.get(at + 4) else data.get(at + 12)).toInt() and 0xff
                    val sectionIndex = data.getShort(if (is64) at + 6 else at + 14).toUnsignedInt()
                    val bind = info ushr 4
                    if (nameOffset != 0L && (bind == STB_GLOBAL || bind == STB_WEAK)) {
                        val name = cString(names, nameOffset)
                        when {
                            sectionIndex != 0 -> defined += name
                            bind == STB_GLOBAL -> required += name
                        }
                    }
                    at += entrySize
                }
            }

            var androidIdent: AndroidIdent? = null
            sections.filter { it.type == SHT_NOTE && it.size in 12..MAX_SECTION_BYTES }.forEach { section ->
                val data = buffer(input, section.offset, section.size, order)
                val raw = data.array()
                var at = 0
                while (at + 12 <= raw.size) {
                    val nameSize = data.getInt(at).toUnsignedLong()
                    val descSize = data.getInt(at + 4).toUnsignedLong()
                    val type = data.getInt(at + 8)
                    if (nameSize > raw.size || descSize > raw.size) break
                    val nameAt = at + 12
                    val descAt = nameAt + align4(nameSize)
                    val next = descAt + align4(descSize)
                    if (next > raw.size) break
                    val name = String(raw, nameAt, nameSize.toInt(), Charsets.US_ASCII).trimEnd('\u0000')
                    if (name == "Android" && type == NT_ANDROID_TYPE_IDENT && descSize >= 4) {
                        val desc = raw.copyOfRange(descAt, descAt + descSize.toInt())
                        androidIdent = AndroidIdent(
                            apiLevel = ByteBuffer.wrap(desc, 0, 4).order(order).int,
                            ndkVersion = fixedString(desc, 4, 64),
                            ndkBuildNumber = fixedString(desc, 68, 64),
                        )
                    }
                    at = next.toInt()
                }
            }
            return ElfDynamicInfo(needed, defined, required, androidIdent)
        }

        private data class Section(val type: Int, val offset: Long, val size: Long, val link: Int, val entrySize: Long)

        private fun readFully(input: RandomAccessFile, offset: Long, target: ByteArray) {
            require(offset >= 0 && offset + target.size <= input.length()) { "ELF data exceeds the file" }
            input.seek(offset)
            input.readFully(target)
        }

        private fun bytes(input: RandomAccessFile, offset: Long, size: Long): ByteArray {
            require(size in 0..MAX_SECTION_BYTES) { "ELF section is too large: $size" }
            return ByteArray(size.toInt()).also { readFully(input, offset, it) }
        }

        private fun buffer(input: RandomAccessFile, offset: Long, size: Long, order: ByteOrder): ByteBuffer =
            ByteBuffer.wrap(bytes(input, offset, size)).order(order)

        private fun cString(table: ByteArray, offset: Long): String {
            require(offset in 0 until table.size) { "ELF string offset is out of range" }
            var end = offset.toInt()
            while (end < table.size && table[end] != 0.toByte()) end++
            return String(table, offset.toInt(), end - offset.toInt(), Charsets.UTF_8)
        }

        private fun fixedString(bytes: ByteArray, offset: Int, length: Int): String {
            if (offset >= bytes.size) return ""
            val end = minOf(bytes.size, offset + length)
            val terminator = (offset until end).firstOrNull { bytes[it] == 0.toByte() } ?: end
            return String(bytes, offset, terminator - offset, Charsets.US_ASCII)
        }

        private fun align4(value: Long): Int = ((value + 3) and 3L.inv()).toInt()

        private fun Int.toUnsignedLong(): Long = toLong() and 0xffffffffL

        private fun Short.toUnsignedInt(): Int = toInt() and 0xffff
    }
}
