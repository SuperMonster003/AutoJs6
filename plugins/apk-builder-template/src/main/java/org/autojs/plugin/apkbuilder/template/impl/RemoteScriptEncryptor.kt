package org.autojs.plugin.apkbuilder.template.impl

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class RemoteScriptEncryptor(
    private val key: String,
    private val initVector: String,
) {

    fun isJavaScriptFileName(fileName: String?): Boolean {
        if (fileName == null) {
            return false
        }
        val lower = fileName.lowercase(Locale.ROOT)
        if (isTypeScriptDeclarationFileName(lower)) {
            return false
        }
        return lower.endsWith(".js") ||
            lower.endsWith(".mjs") ||
            lower.endsWith(".cjs") ||
            lower.endsWith(".mts") ||
            lower.endsWith(".cts") ||
            lower.endsWith(".ts") ||
            lower.endsWith(".tsx")
    }

    fun encryptFile(source: File, target: File) {
        val bytes = source.readBytes()
        val flags = parseExecutionMode(source.name, bytes).toShort()
        target.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) {
                throw IllegalStateException("Failed to create parent directory: ${parent.path}")
            }
        }
        FileOutputStream(target, false).use { output ->
            writeHeader(output, flags)
            output.write(encrypt(bytes))
            output.flush()
        }
    }

    private fun parseExecutionMode(fileName: String, bytes: ByteArray): Int {
        val headerFlags = getHeaderFlags(bytes)
        if (headerFlags != FLAG_INVALID_FILE) {
            return headerFlags.toInt()
        }
        val script = bytes.toString(Charsets.UTF_8)
        var mode = parseExecutionMode(script)
        if (isNodeJsFileName(fileName)) {
            mode = mode or EXECUTION_MODE_NODEJS
        }
        return mode
    }

    private fun parseExecutionMode(script: String): Int {
        val index = skipIgnorable(script, 0)
        if (index >= script.length) {
            return EXECUTION_MODE_NORMAL
        }
        val quote = script[index]
        if (quote != '"' && quote != '\'') {
            return EXECUTION_MODE_NORMAL
        }
        val end = findStringLiteralEnd(script, index + 1, quote)
        if (end < 0) {
            return EXECUTION_MODE_NORMAL
        }
        val next = skipHorizontalWhitespace(script, end + 1)
        if (next < script.length && script[next] != ';' && script[next] != '\n' && script[next] != '\r') {
            return EXECUTION_MODE_NORMAL
        }
        return parseExecutionModeByModeStrings(script.substring(index + 1, end))
    }

    private fun skipIgnorable(script: String, start: Int): Int {
        var index = start
        while (index < script.length) {
            val c = script[index]
            if (c.isWhitespace()) {
                index += 1
                continue
            }
            if (c == '/' && index + 1 < script.length) {
                when (script[index + 1]) {
                    '/' -> {
                        index += 2
                        while (index < script.length && script[index] != '\n' && script[index] != '\r') {
                            index += 1
                        }
                        continue
                    }
                    '*' -> {
                        index += 2
                        while (index + 1 < script.length && !(script[index] == '*' && script[index + 1] == '/')) {
                            index += 1
                        }
                        index = (index + 2).coerceAtMost(script.length)
                        continue
                    }
                }
            }
            break
        }
        return index
    }

    private fun skipHorizontalWhitespace(script: String, start: Int): Int {
        var index = start
        while (index < script.length && (script[index] == ' ' || script[index] == '\t')) {
            index += 1
        }
        return index
    }

    private fun findStringLiteralEnd(script: String, start: Int, quote: Char): Int {
        var escaped = false
        var index = start
        while (index < script.length) {
            val c = script[index]
            if (escaped) {
                escaped = false
            } else if (c == '\\') {
                escaped = true
            } else if (c == quote) {
                return index
            } else if (c == '\n' || c == '\r') {
                return -1
            }
            index += 1
        }
        return -1
    }

    private fun parseExecutionModeByModeStrings(value: String): Int {
        var mode = EXECUTION_MODE_NORMAL
        value.split(Regex("\\s*[,;|]\\s*|\\s+"))
            .map { it.lowercase(Locale.ROOT) }
            .forEach { modeString ->
                mode = mode or EXECUTION_MODES.getOrDefault(modeString, EXECUTION_MODE_NORMAL)
            }
        return mode
    }

    private fun isNodeJsFileName(fileName: String?): Boolean {
        if (fileName == null) {
            return false
        }
        val lower = fileName.lowercase(Locale.ROOT)
        if (isTypeScriptDeclarationFileName(lower)) {
            return false
        }
        return lower.endsWith(".mjs") ||
            lower.endsWith(".cjs") ||
            lower.endsWith(".mts") ||
            lower.endsWith(".cts") ||
            lower.endsWith(".ts") ||
            lower.endsWith(".tsx") ||
            lower.endsWith(".node.js")
    }

    private fun isTypeScriptDeclarationFileName(fileName: String): Boolean {
        return fileName.endsWith(".d.ts") ||
            fileName.endsWith(".d.mts") ||
            fileName.endsWith(".d.cts")
    }

    private fun encrypt(plainText: ByteArray): ByteArray {
        val secretKey = SecretKeySpec(key.toByteArray(), ALGORITHM)
        val cipher = Cipher.getInstance(FULL_ALGORITHM)
        val ivParameterSpec = IvParameterSpec(initVector.toByteArray())
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec)
        return cipher.doFinal(plainText)
    }

    private fun getHeaderFlags(bytes: ByteArray): Short {
        if (bytes.size < BLOCK_SIZE || !isValidHeader(bytes)) {
            return FLAG_INVALID_FILE
        }
        return (bytes[BLOCK.size].toShort() * 256 + bytes[BLOCK.size + 1]).toShort()
    }

    private fun isValidHeader(bytes: ByteArray): Boolean {
        if (bytes.size < BLOCK_SIZE) {
            return false
        }
        for (index in BLOCK.indices) {
            if (bytes[index] != BLOCK[index]) {
                return false
            }
        }
        return true
    }

    private fun writeHeader(output: OutputStream, flags: Short = 0) {
        output.write(BLOCK)
        output.write(flags / 256)
        output.write(flags % 256)
    }

    companion object {
        private const val ALGORITHM = "AES"
        private const val FULL_ALGORITHM = "AES/CBC/PKCS5Padding"

        private const val EXECUTION_MODE_NORMAL = 0x0000
        private const val EXECUTION_MODE_UI = 0x0001
        private const val EXECUTION_MODE_AUTO = 0x0002
        private const val EXECUTION_MODE_JSOX = 0x0004
        private const val EXECUTION_MODE_NODEJS = 0x0008
        private const val EXECUTION_MODE_UI_THREAD = 0x0010
        private const val EXECUTION_MODE_RHINO = 0x0020

        private const val FLAG_INVALID_FILE: Short = Short.MIN_VALUE
        private const val BLOCK_SIZE = 8
        private val BLOCK = byteArrayOf(0x77, 0x01, 0x17, 0x7F, 0x12, 0x12)

        private val EXECUTION_MODES = mapOf(
            "ui" to EXECUTION_MODE_UI,
            "auto" to EXECUTION_MODE_AUTO,
            "jsox" to EXECUTION_MODE_JSOX,
            "x" to EXECUTION_MODE_JSOX,
            "nodejs" to EXECUTION_MODE_NODEJS,
            "node" to EXECUTION_MODE_NODEJS,
            "ui-thread" to EXECUTION_MODE_UI_THREAD,
            "rhino" to EXECUTION_MODE_RHINO,
        )
    }
}
