package org.autojs.autojs.core.plugin.r8

import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class R8CompilerDexZipValidatorTest {
    @Test
    fun canonicalDexZipPassesHeaderSignatureAndChecksumValidation() {
        withZip(R8CompilerTransactionTestFixtures.validDexZip()) { file ->
            R8CompilerDexZipValidator.validate(file, maximumEntries = 64, maximumUncompressedBytes = 1024)
        }
    }

    @Test
    fun invalidDexAndNonCanonicalEntryNameAreRejected() {
        expectRejected(R8CompilerTransactionTestFixtures.invalidDexZip())
        expectRejected(
            R8CompilerTransactionTestFixtures.dexZip(
                R8CompilerTransactionTestFixtures.validDex(),
                "other.dex",
            ),
        )
    }

    @Test
    fun zipTrailingBytesAreRejected() {
        expectRejected(R8CompilerTransactionTestFixtures.validDexZip() + byteArrayOf(1))
    }

    private fun expectRejected(bytes: ByteArray) {
        withZip(bytes) { file ->
            try {
                R8CompilerDexZipValidator.validate(file, 64, 1024)
                fail("Expected R8 DEX ZIP rejection")
            } catch (_: IllegalArgumentException) {
                Unit
            }
        }
    }

    private fun withZip(bytes: ByteArray, action: (File) -> Unit) {
        val file = kotlin.io.path.createTempFile("r8-dex-", ".zip").toFile()
        try {
            file.writeBytes(bytes)
            action(file)
        } finally {
            file.delete()
        }
    }
}
