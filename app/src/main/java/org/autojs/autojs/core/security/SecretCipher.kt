package org.autojs.autojs.core.security

import java.security.GeneralSecurityException

/**
 * Encrypts opaque secrets while authenticating caller-supplied associated data.
 *
 * Implementations must never persist or return plaintext when encryption fails.
 */
internal interface SecretCipher {

    @Throws(SecretCipherException::class)
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): String

    @Throws(SecretCipherException::class)
    fun decrypt(payload: String, associatedData: ByteArray): ByteArray
}

internal class SecretCipherException(
    message: String,
    cause: Throwable? = null,
) : GeneralSecurityException(message, cause)
