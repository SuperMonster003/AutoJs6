package org.autojs.autojs.core.plugin.opencv

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Properties

/**
 * Provenance record of the OpenCV runtime inside a packaged APK
 * (docs/dev/opencv-ocr-runtime-decoupling-roadmap.md, P1.5).
 *
 * The APK builder writes it only when the project selects the OpenCV library, and records the
 * SHA-256 of every packaged `libopencv_java4.so`. A packaged app loads its bundled OpenCV runtime
 * only when the library matches this record, so a same-named copy brought in by another plugin is
 * never used by images and the `org.opencv` Java API.
 *
 * zh-CN: 打包 APK 中 OpenCV 运行时的来源记录. 仅当项目勾选 OpenCV 时由打包器写入, 记录每个
 * `libopencv_java4.so` 的 SHA-256. 打包应用只在库与记录一致时加载内置 OpenCV, 其他插件带入的同名
 * 副本不会被 images 与 `org.opencv` Java API 使用.
 */
object BundledOpenCvRuntime {

    /** Path relative to the APK `assets/` directory. */
    const val ASSET_PATH = "opencv-runtime.properties"

    private const val SCHEMA = 1
    private const val KEY_SCHEMA = "schema"
    private const val KEY_LIBRARY = "library"
    private const val KEY_PACKAGE_NAME = "packageName"
    private const val KEY_VERSION_NAME = "versionName"
    private const val KEY_VERSION_CODE = "versionCode"
    private const val KEY_SHA256_PREFIX = "sha256."
    private val SHA256 = Regex("[0-9a-f]{64}")

    data class Record(
        val library: String,
        val packageName: String,
        val versionName: String?,
        val versionCode: Long?,
        val sha256ByAbi: Map<String, String>,
    ) {
        fun matches(sha256: String): Boolean = sha256.lowercase() in sha256ByAbi.values
    }

    /** Deterministic UTF-8 text: the same inputs always produce the same APK entry. */
    fun encode(record: Record): ByteArray {
        require(record.sha256ByAbi.isNotEmpty()) { "The OpenCV runtime record needs at least one ABI" }
        val lines = buildList {
            add("$KEY_SCHEMA=$SCHEMA")
            add("$KEY_LIBRARY=${record.library}")
            add("$KEY_PACKAGE_NAME=${record.packageName}")
            record.versionName?.let { add("$KEY_VERSION_NAME=$it") }
            record.versionCode?.let { add("$KEY_VERSION_CODE=$it") }
            record.sha256ByAbi.toSortedMap().forEach { (abi, sha256) ->
                require(SHA256.matches(sha256)) { "Invalid SHA-256 for $abi: $sha256" }
                add("$KEY_SHA256_PREFIX$abi=$sha256")
            }
        }
        return lines.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8)
    }

    /** Returns null when the record is missing a required field or has an unknown schema. */
    fun decode(bytes: ByteArray): Record? {
        val properties = Properties().apply {
            ByteArrayInputStream(bytes).reader(Charsets.UTF_8).use { load(it) }
        }
        if (properties.getProperty(KEY_SCHEMA)?.toIntOrNull() != SCHEMA) return null
        val library = properties.getProperty(KEY_LIBRARY)?.takeIf(String::isNotBlank) ?: return null
        val packageName = properties.getProperty(KEY_PACKAGE_NAME)?.takeIf(String::isNotBlank) ?: return null
        val hashes = properties.stringPropertyNames()
            .filter { it.startsWith(KEY_SHA256_PREFIX) }
            .associate { it.removePrefix(KEY_SHA256_PREFIX) to properties.getProperty(it).trim().lowercase() }
            .filterValues(SHA256::matches)
        if (hashes.isEmpty()) return null
        return Record(
            library = library,
            packageName = packageName,
            versionName = properties.getProperty(KEY_VERSION_NAME),
            versionCode = properties.getProperty(KEY_VERSION_CODE)?.toLongOrNull(),
            sha256ByAbi = hashes,
        )
    }

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
