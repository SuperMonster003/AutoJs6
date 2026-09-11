package org.autojs.autojs.core.plugin.nativealign

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ElfLoadAlignmentTest {
    private fun fixture(align: Long = 16384, is64: Boolean = true, order: ByteOrder = ByteOrder.LITTLE_ENDIAN): ByteArray {
        val bytes = ByteArray(160)
        val data = ByteBuffer.wrap(bytes).order(order)
        data.put(byteArrayOf(0x7f, 0x45, 0x4c, 0x46, if (is64) 2 else 1, if (order == ByteOrder.LITTLE_ENDIAN) 1 else 2, 1))
        data.putShort(18, if (is64) 183 else 40)
        data.putInt(20, 1)
        if (is64) {
            data.putLong(32, 64); data.putShort(54, 56); data.putShort(56, 1)
            data.putInt(64, 1); data.putLong(112, align)
        } else {
            data.putInt(28, 52); data.putShort(42, 32); data.putShort(44, 1)
            data.putInt(52, 1); data.putInt(80, align.toInt())
        }
        return bytes
    }

    @Test fun parsesBothElfClassesAndByteOrders() {
        for (is64 in listOf(false, true)) for (order in listOf(ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN)) {
            assertEquals(16384L, NativeLibraryAlignment.read(fixture(is64 = is64, order = order).inputStream()).minLoadAlign)
        }
    }

    @Test fun rejectsTruncatedProgramTableInvalidAlignmentAndWrongMagic() {
        for (bytes in listOf(ByteArray(64), fixture().copyOf(80), fixture(12288), fixture(0),
            fixture().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putLong(32, Long.MAX_VALUE) })) {
            assertThrows(IllegalArgumentException::class.java) { ElfLoadAlignment.parse(bytes) }
        }
    }

    @Test fun rejectsLibraryBeforeLoadingOnlyWhenDeviceRequiresLargerPages() {
        val file = kotlin.io.path.createTempFile("alignment", ".so").toFile()
        try {
            file.writeBytes(fixture(4096))
            assertEquals(4096L, NativeLibraryAlignment.requireCompatible(file, 4096))
            val failure = assertThrows(NativePageAlignmentException::class.java) {
                NativeLibraryAlignment.requireCompatible(file, 16384)
            }
            assertEquals(4096L, failure.minLoadAlign)
            assertEquals(16384L, failure.pageSize)
            file.writeBytes(fixture())
            assertEquals(16384L, NativeLibraryAlignment.requireCompatible(file, 16384))
        } finally { file.delete() }
    }
}
