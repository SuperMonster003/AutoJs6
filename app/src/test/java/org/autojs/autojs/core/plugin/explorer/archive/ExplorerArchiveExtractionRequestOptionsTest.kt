package org.autojs.autojs.core.plugin.explorer.archive

import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerArchiveExtractionRequestOptionsTest {

    @Test
    fun passwordStorageIsCopiedBoundedAndCleared() {
        val callerPassword = "transient-host-password".toCharArray()
        val options = ExplorerArchiveExtractionRequestOptions(
            password = callerPassword,
            skipUnsafePaths = true,
        )
        callerPassword.fill('\u0000')

        val firstCopy = requireNotNull(options.passwordChars())
        assertArrayEquals("transient-host-password".toCharArray(), firstCopy)
        firstCopy.fill('\u0000')
        assertTrue(options.hasRecoveryOptions)
        assertFalse(options.toString().contains("transient-host-password"))

        options.close()
        assertNull(options.passwordChars())
        assertFalse(options.hasPassword)
        assertTrue(options.hasRecoveryOptions)
    }

    @Test
    fun oversizedPasswordIsRejected() {
        val password = CharArray(ExplorerActionProtocol.MAX_ARCHIVE_PASSWORD_LENGTH + 1) { 'x' }
        try {
            assertThrows(IllegalArgumentException::class.java) {
                ExplorerArchiveExtractionRequestOptions(password = password)
            }
        } finally {
            password.fill('\u0000')
        }
    }
}
