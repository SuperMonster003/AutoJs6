package org.autojs.plugin.dexcompiler.fake

import org.autojs.plugin.dexcompiler.api.DexSha256

object EmbeddedDexFixture {
    const val SIZE_BYTES = 636
    const val SHA256_HEX = "f19638c6edf9226ed67754665c139ae933b387d4013c43ef3e2262fd839e4189"

    private const val BASE64 =
        "ZGV4CjAzNwASXjjjXGwMzzWOt5E1U9LtrMNEIlte+Ad8AgAAcAAAAHhWNBIAAAAAAAAAAPQBAAAIAAAAcAAAAAQAAACQAAAAAgAAAKAAAAAAAAAAAAAAAAMAAAC4AAAAAQAAANAAAACMAQAA8AAAACQBAAAsAQAAOgEAAD0BAABRAQAAbwEAAHIBAAB6AQAAAgAAAAMAAAAEAAAABQAAAAIAAAAAAAAAAAAAAAUAAAADAAAAAAAAAAEAAQAAAAAAAgABAAAAAAACAAAABgAAAAIAAAARAAAAAQAAAAAAAAABAAAAAAAAAOUBAAAAAAAAAQAAAAAAAAAAAAAAAwAAABMAKgAPAAAAAQABAAEAAAAgAQAABAAAAHAQAAAAAA4ABAAOAAY8aW5pdD4ADEZpeHR1cmUuamF2YQABSQASTGphdmEvbGFuZy9PYmplY3Q7ABxMb3JnL2F1dG9qcy9maXh0dXJlL0ZpeHR1cmU7AAFWAAZhbnN3ZXIAaX5+RDh7ImJhY2tlbmQiOiJkZXgiLCJjb21waWxhdGlvbi1tb2RlIjoicmVsZWFzZSIsImhhcy1jaGVja3N1bXMiOmZhbHNlLCJtaW4tYXBpIjoyNCwidmVyc2lvbiI6IjguMTMuMTkifQAAAAIAAYKABIgCAQnwAQALAAAAAAAAAAEAAAAAAAAAAQAAAAgAAABwAAAAAgAAAAQAAACQAAAAAwAAAAIAAACgAAAABQAAAAMAAAC4AAAABgAAAAEAAADQAAAAASAAAAIAAADwAAAAAyAAAAEAAAAgAQAAAiAAAAgAAAAkAQAAACAAAAEAAADlAQAAABAAAAEAAAD0AQAA"

    private val decoded: ByteArray by lazy(LazyThreadSafetyMode.PUBLICATION) {
        EmbeddedBase64.decode(BASE64).also { bytes ->
            check(bytes.size == SIZE_BYTES) { "Embedded classes.dex size mismatch" }
            check(bytes.copyOfRange(0, DEX_MAGIC.size).contentEquals(DEX_MAGIC)) {
                "Embedded classes.dex magic mismatch"
            }
            check(FakeDexHashes.sha256(bytes).toHexString() == SHA256_HEX) {
                "Embedded classes.dex SHA-256 mismatch"
            }
        }
    }

    val sha256: DexSha256 by lazy(LazyThreadSafetyMode.PUBLICATION) {
        DexSha256.fromBytes(hexToBytes(SHA256_HEX))
    }

    fun bytes(): ByteArray = decoded.copyOf()

    private fun hexToBytes(value: String): ByteArray = ByteArray(value.length / 2) { index ->
        value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }

    private val DEX_MAGIC = byteArrayOf(
        'd'.code.toByte(),
        'e'.code.toByte(),
        'x'.code.toByte(),
        '\n'.code.toByte(),
        '0'.code.toByte(),
        '3'.code.toByte(),
        '7'.code.toByte(),
        0,
    )
}

private object EmbeddedBase64 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val values = IntArray(128) { -1 }.also { table ->
        ALPHABET.forEachIndexed { index, char -> table[char.code] = index }
    }

    fun decode(value: String): ByteArray {
        require(value.length % 4 == 0) { "Base64 length must be divisible by four" }
        val padding = when {
            value.endsWith("==") -> 2
            value.endsWith("=") -> 1
            else -> 0
        }
        val output = ByteArray(value.length / 4 * 3 - padding)
        var outputIndex = 0
        value.chunked(4).forEachIndexed { chunkIndex, chunk ->
            val lastChunk = chunkIndex == value.length / 4 - 1
            val a = decodeChar(chunk[0])
            val b = decodeChar(chunk[1])
            val c = if (chunk[2] == '=') 0 else decodeChar(chunk[2])
            val d = if (chunk[3] == '=') 0 else decodeChar(chunk[3])
            require(lastChunk || (chunk[2] != '=' && chunk[3] != '=')) {
                "Base64 padding is only allowed in the final quartet"
            }
            require(chunk[2] != '=' || chunk[3] == '=') { "Base64 padding is malformed" }

            val combined = (a shl 18) or (b shl 12) or (c shl 6) or d
            if (outputIndex < output.size) output[outputIndex++] = (combined ushr 16).toByte()
            if (outputIndex < output.size) output[outputIndex++] = (combined ushr 8).toByte()
            if (outputIndex < output.size) output[outputIndex++] = combined.toByte()
        }
        check(outputIndex == output.size) { "Base64 output length mismatch" }
        return output
    }

    private fun decodeChar(value: Char): Int {
        require(value.code < values.size && values[value.code] >= 0) { "Invalid Base64 character" }
        return values[value.code]
    }
}
