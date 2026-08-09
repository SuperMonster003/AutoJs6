package org.autojs.plugin.dexcompiler.fake

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DeterministicDexZip {
    const val ENTRY_NAME = "classes.dex"
    private const val DOS_EPOCH_MILLIS = 315_532_800_000L

    fun create(classesDex: ByteArray): ByteArray {
        require(classesDex.isNotEmpty()) { "classes.dex must not be empty" }
        val crc = CRC32().apply { update(classesDex) }
        return ByteArrayOutputStream(classesDex.size + 256).use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                val entry = ZipEntry(ENTRY_NAME).apply {
                    method = ZipEntry.STORED
                    size = classesDex.size.toLong()
                    compressedSize = classesDex.size.toLong()
                    this.crc = crc.value
                    time = DOS_EPOCH_MILLIS
                    extra = byteArrayOf()
                }
                zip.putNextEntry(entry)
                zip.write(classesDex)
                zip.closeEntry()
            }
            bytes.toByteArray()
        }
    }
}
