package org.autojs.plugin.dexcompiler.fake

import org.autojs.plugin.dexcompiler.api.DexSha256
import java.io.InputStream
import java.security.MessageDigest

object FakeDexHashes {
    fun sha256(bytes: ByteArray): DexSha256 = DexSha256.digest(bytes)

    fun sha256(input: InputStream): DexSha256 {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            if (read > 0) {
                digest.update(buffer, 0, read)
            }
        }
        return DexSha256.fromBytes(digest.digest())
    }
}
