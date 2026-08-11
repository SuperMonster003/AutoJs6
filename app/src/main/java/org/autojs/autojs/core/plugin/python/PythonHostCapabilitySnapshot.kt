package org.autojs.autojs.core.plugin.python

import android.content.Context
import android.os.Build
import org.autojs.autojs.script.PythonProjectSource
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.python.runtime.api.PythonRequestId
import org.autojs.plugin.python.runtime.api.PythonRuntimeContract
import java.nio.charset.StandardCharsets

/**
 * A bounded, immutable host-created document for the R5 read-only preview API.
 *
 * The document deliberately contains no Android identifier, Context, Binder or Java object. It is
 * transported as one ordinary exact-length/SHA-256 PFD and parsed inside the isolated runtime.
 */
internal object PythonHostCapabilitySnapshot {

    fun create(
        context: Context,
        requestId: PythonRequestId,
        source: org.autojs.autojs.script.PythonFileSource,
    ): ByteArray = encode(
        Document(
            executionId = requestId.toString(),
            entryPoint = when (source) {
                is PythonProjectSource -> source.entryPoint
                else -> source.semanticFile.name
            },
            projectFilesAvailable = source is PythonProjectSource,
            app = AppSnapshot(
                packageName = context.packageName,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE.toLong(),
                debuggable = BuildConfig.DEBUG,
            ),
            device = DeviceSnapshot(
                sdkInt = Build.VERSION.SDK_INT,
                release = Build.VERSION.RELEASE.orEmpty(),
                manufacturer = Build.MANUFACTURER.orEmpty(),
                brand = Build.BRAND.orEmpty(),
                model = Build.MODEL.orEmpty(),
                supportedAbis = Build.SUPPORTED_ABIS?.toList().orEmpty(),
            ),
        ),
    )

    internal data class Document(
        val executionId: String,
        val entryPoint: String,
        val projectFilesAvailable: Boolean,
        val app: AppSnapshot,
        val device: DeviceSnapshot,
    )

    internal data class AppSnapshot(
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
        val debuggable: Boolean,
    )

    internal data class DeviceSnapshot(
        val sdkInt: Int,
        val release: String,
        val manufacturer: String,
        val brand: String,
        val model: String,
        val supportedAbis: List<String>,
    )

    internal fun encode(document: Document): ByteArray {
        require(document.executionId.isNotBlank()) { "Python execution ID is blank" }
        require(document.entryPoint.isNotBlank()) { "Python entry point is blank" }
        require(document.app.packageName.isNotBlank()) { "Host package name is blank" }
        require(document.app.versionCode > 0L) { "Host version code must be positive" }
        require(document.device.sdkInt > 0) { "Android API level must be positive" }
        require(document.device.supportedAbis.isNotEmpty()) { "Device ABI snapshot is empty" }

        val grants = buildList {
            add("app.snapshot.read")
            add("device.snapshot.read")
            if (document.projectFilesAvailable) add("project.files.read")
        }
        val json = buildString(512) {
            append('{')
            append("\"schemaVersion\":1,")
            append("\"execution\":{")
            field("id", document.executionId)
            append(',')
            field("entryPoint", document.entryPoint)
            append(',')
            append("\"project\":").append(document.projectFilesAvailable)
            append("},\"grants\":[")
            grants.forEachIndexed { index, grant ->
                if (index > 0) append(',')
                quoted(grant)
            }
            append("],\"app\":{")
            field("packageName", document.app.packageName)
            append(',')
            field("versionName", document.app.versionName)
            append(',')
            append("\"versionCode\":").append(document.app.versionCode)
            append(',')
            append("\"debuggable\":").append(document.app.debuggable)
            append("},\"device\":{")
            append("\"sdkInt\":").append(document.device.sdkInt)
            append(',')
            field("release", document.device.release)
            append(',')
            field("manufacturer", document.device.manufacturer)
            append(',')
            field("brand", document.device.brand)
            append(',')
            field("model", document.device.model)
            append(",\"supportedAbis\":[")
            document.device.supportedAbis.forEachIndexed { index, abi ->
                if (index > 0) append(',')
                quoted(abi)
            }
            append("]}}")
        }
        val encoded = json.toByteArray(StandardCharsets.UTF_8)
        require(
            encoded.isNotEmpty() &&
                encoded.size.toLong() <= PythonRuntimeContract.MAX_HOST_CAPABILITY_SNAPSHOT_BYTES,
        ) {
            "Host capability snapshot exceeds the protocol limit"
        }
        return encoded
    }

    private fun StringBuilder.field(name: String, value: String) {
        quoted(name)
        append(':')
        quoted(value)
    }

    private fun StringBuilder.quoted(value: String) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }
}
