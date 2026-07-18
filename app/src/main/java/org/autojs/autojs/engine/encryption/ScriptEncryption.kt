package org.autojs.autojs.engine.encryption

object ScriptEncryption {

    private var mKey = ""
    private var mInitVector = ""

    @Synchronized
    internal fun configure(key: String, initVector: String) {
        mKey = key
        mInitVector = initVector
    }

    @Synchronized
    internal fun snapshotDecryptor(): Decryptor {
        return Decryptor(
            key = mKey.toByteArray().copyOf(),
            initVector = mInitVector,
        )
    }

    @JvmStatic
    fun decrypt(bytes: ByteArray, start: Int = 0, end: Int = bytes.size): ByteArray {
        return snapshotDecryptor().decrypt(bytes, start, end)
    }

    internal class Decryptor internal constructor(
        key: ByteArray,
        initVector: String,
    ) {

        private val key = key.copyOf()
        private val initVector = initVector

        fun decrypt(bytes: ByteArray, start: Int = 0, end: Int = bytes.size): ByteArray {
            return AdvancedEncryptionStandard(key, initVector).decrypt(bytes, start, end)
        }
    }

}
