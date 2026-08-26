package org.autojs.autojs.core.plugin.explorer.archive

import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerArchiveOpenValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerArchiveOpenRequestOptionsTest {

    @Test
    fun `password is copied hidden from logs and cleared on close`() {
        val callerPassword = "archive-test-2026".toCharArray()
        val options = ExplorerArchiveOpenRequestOptions(callerPassword)
        callerPassword.fill('x')

        val requestPassword = requireNotNull(options.passwordChars())
        assertEquals("archive-test-2026", requestPassword.concatToString())
        assertTrue(options.hasPassword)
        assertFalse(options.toString().contains("archive-test-2026"))

        requestPassword.fill('\u0000')
        options.close()
        assertFalse(options.hasPassword)
        assertNull(options.passwordChars())
    }

    @Test
    fun `oversized password is rejected`() {
        val password = CharArray(ExplorerActionProtocol.MAX_ARCHIVE_PASSWORD_LENGTH + 1) { 'x' }
        assertThrows(IllegalArgumentException::class.java) {
            ExplorerArchiveOpenRequestOptions(password)
        }
    }

    @Test
    fun `only v11 password service errors become recovery interactions`() {
        assertNull(
            archiveOpenInteractionForServiceError(
                10,
                ExplorerArchiveOpenValues.ERROR_PASSWORD_REQUIRED,
            ),
        )
        assertEquals(
            ExplorerArchiveOpenValues.ERROR_PASSWORD_REQUIRED,
            archiveOpenInteractionForServiceError(
                11,
                ExplorerArchiveOpenValues.ERROR_PASSWORD_REQUIRED,
            ),
        )
        assertEquals(
            ExplorerArchiveOpenValues.ERROR_WRONG_PASSWORD,
            archiveOpenInteractionForServiceError(
                11,
                ExplorerArchiveOpenValues.ERROR_WRONG_PASSWORD,
            ),
        )
        assertNull(archiveOpenInteractionForServiceError(11, Int.MAX_VALUE))
    }
}
