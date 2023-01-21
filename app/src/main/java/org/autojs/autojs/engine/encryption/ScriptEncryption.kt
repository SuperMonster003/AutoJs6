package org.autojs.autojs.engine.encryption

object ScriptEncryption {

    private var mKey = ""
    private var mInitVector = ""

    @JvmStatic
    fun decrypt(bytes: ByteArray, start: Int = 0, end: Int = bytes.size): ByteArray {
        return AdvancedEncryptionStandard(mKey.toByteArray(), mInitVector).decrypt(bytes, start, end)
    }

}