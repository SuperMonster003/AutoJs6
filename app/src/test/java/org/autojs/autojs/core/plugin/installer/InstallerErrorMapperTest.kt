package org.autojs.autojs.core.plugin.installer

import org.autojs.plugin.installer.api.InstallerErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

class InstallerErrorMapperTest {

    @Test
    fun `normalize keeps known codes and collapses unknown ones`() {
        val known = InstallerErrorMapper.normalize(InstallerErrorCodes.SIGNATURE_MISMATCH, "  signer differs ", retryable = null, status = 5, systemMessage = "INSTALL_FAILED_UPDATE_INCOMPATIBLE", packageName = "com.x")
        assertEquals(InstallerErrorCodes.SIGNATURE_MISMATCH, known.code)
        assertEquals("signer differs", known.message)
        assertFalse(known.retryable)
        assertEquals(5, known.status)
        assertEquals("com.x", known.packageName)

        val unknown = InstallerErrorMapper.normalize("SOMETHING_ELSE", null, retryable = null)
        assertEquals(InstallerErrorCodes.INTERNAL, unknown.code)
        assertEquals("unknown error code SOMETHING_ELSE", unknown.message)

        val blank = InstallerErrorMapper.normalize(InstallerErrorCodes.TIMEOUT, "", retryable = null)
        assertEquals(InstallerErrorMapper.defaultMessage(InstallerErrorCodes.TIMEOUT), blank.message)
        assertTrue(blank.retryable)

        val long = InstallerErrorMapper.normalize(InstallerErrorCodes.INTERNAL, "m".repeat(5000), retryable = false)
        assertEquals(2048, long.message.length)
    }

    @Test
    fun `host-side throwables map by type and phase`() {
        assertEquals(InstallerErrorCodes.SOURCE_NOT_FOUND, InstallerErrorMapper.fromThrowable(FileNotFoundException("missing"), InstallerErrorMapper.Phase.OPEN).code)
        assertEquals(InstallerErrorCodes.SOURCE_UNREADABLE, InstallerErrorMapper.fromThrowable(IOException("locked"), InstallerErrorMapper.Phase.OPEN).code)
        assertEquals(InstallerErrorCodes.INVALID_ARGUMENT, InstallerErrorMapper.fromThrowable(IllegalArgumentException("bad"), InstallerErrorMapper.Phase.CALL).code)
        assertEquals(InstallerErrorCodes.PLUGIN_UNAVAILABLE, InstallerErrorMapper.fromThrowable(SecurityException("no"), InstallerErrorMapper.Phase.CALL).code)
        assertEquals(InstallerErrorCodes.SOURCE_UNREADABLE, InstallerErrorMapper.fromThrowable(SecurityException("grant revoked"), InstallerErrorMapper.Phase.SOURCE).code)
        assertEquals(InstallerErrorCodes.PLUGIN_UNAVAILABLE, InstallerErrorMapper.fromThrowable(IllegalStateException("not found"), InstallerErrorMapper.Phase.DISCOVERY).code)
        assertEquals(InstallerErrorCodes.INTERNAL, InstallerErrorMapper.fromThrowable(IllegalStateException("bug"), InstallerErrorMapper.Phase.SESSION).code)
        assertEquals(InstallerErrorCodes.CANCELLED, InstallerErrorMapper.fromThrowable(InterruptedException(), InstallerErrorMapper.Phase.CALL).code)
        val wrapped = InstallerPluginException(InstallerErrorMapper.error(InstallerErrorCodes.USER_CANCELLED, "user"))
        assertEquals(wrapped.error, InstallerErrorMapper.fromThrowable(wrapped, InstallerErrorMapper.Phase.SESSION))
        assertEquals("USER_CANCELLED: user", wrapped.message)
    }

    @Test
    fun `concrete authorizer survives error normalization`() {
        assertEquals("shizuku", InstallerErrorMapper.normalize("INSTALL_FAILED", "denied", false, authorizer = "shizuku").authorizer)
        assertEquals(null, InstallerErrorMapper.normalize("INSTALL_FAILED", "denied", false, authorizer = "unknown").authorizer)
        assertEquals("root", InstallerJson.decodeError("""{"code":"INSTALL_FAILED","authorizer":"root"}""").authorizer)
    }

    @Test
    fun `every contract code has a default message`() {
        InstallerErrorCodes.ALL.forEach { code ->
            assertTrue(code, InstallerErrorMapper.defaultMessage(code).isNotBlank())
        }
    }
}
