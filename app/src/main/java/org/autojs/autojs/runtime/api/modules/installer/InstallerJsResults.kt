package org.autojs.autojs.runtime.api.modules.installer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.autojs.autojs.core.plugin.installer.InstallerErrorMapper
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.installer.api.InstallerContract
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.VarScope

/** Result decoration is deliberately separate from the immutable values sent by worker threads. */
internal object InstallerJsResults {
    fun toJs(runtime: ScriptRuntime, plain: Any?, inspect: Boolean = false): Any? =
        RhinoUtils.withRhinoContext(runtime) { cx ->
            decorate(cx, runtime.topLevelScope, RhinoUtils.toJsValue(runtime.topLevelScope, plain), inspect)
        }

    fun decorate(runtime: ScriptRuntime, value: Any?, inspect: Boolean = false): Any? =
        RhinoUtils.withRhinoContext(runtime) { cx -> decorate(cx, runtime.topLevelScope, value, inspect) }

    fun decorate(cx: Context, scope: VarScope, value: Any?, inspect: Boolean = false, iconDecoder: (String) -> Any? = ::decodeIcon): Any? {
        if (value is NativeArray) {
            for (index in 0 until value.length.toInt()) decorate(cx, scope, value.get(index, value))
            return value
        }
        val result = value as? Scriptable ?: return value
        val rawError = ScriptableObject.getProperty(result, "error") as? Scriptable
        if (rawError != null && !InstallerJsErrors.constructor(cx, scope).hasInstance(rawError)) {
            fun text(name: String): String? = (ScriptableObject.getProperty(rawError, name) as? CharSequence)?.toString()
            val error = InstallerErrorMapper.normalize(
                code = text("code"), message = text("message"),
                retryable = ScriptableObject.getProperty(rawError, "retryable") as? Boolean,
                status = (ScriptableObject.getProperty(rawError, "status") as? Number)?.toInt(),
                systemMessage = text("systemMessage"), packageName = text("packageName"),
                authorizer = text("authorizer") ?: (ScriptableObject.getProperty(result, "authorizer") as? String),
            )
            ScriptableObject.putProperty(result, "error", InstallerJsErrors.newInstallerError(cx, scope, error))
        }
        if (inspect) {
            val encoded = ScriptableObject.getProperty(result, "icon")
            ScriptableObject.deleteProperty(result, "icon")
            if (encoded is String) {
                iconDecoder(encoded)?.let { bitmap -> ScriptableObject.putProperty(result, "icon", Context.javaToJS(bitmap, scope)) }
            }
        }
        return result
    }

    /** Optional icons are ignored when malformed or over budget; the archive information remains usable. */
    private fun decodeIcon(encoded: String): Bitmap? = try {
        val bytes = iconBytes(encoded) { Base64.decode(it, Base64.NO_WRAP) }
        if (bytes == null) null else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outMimeType != "image/png" || !safeDimensions(bounds.outWidth, bounds.outHeight)) null else {
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })
                if (bitmap != null && (bitmap.byteCount > MAX_DECODED_BYTES || !safeDimensions(bitmap.width, bitmap.height))) {
                    bitmap.recycle()
                    null
                } else bitmap
            }
        }
    } catch (_: RuntimeException) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }

    /** Pure validation seam also limits dimensions before asking Android to inspect compressed data. */
    internal fun iconBytes(encoded: String, decode: (String) -> ByteArray): ByteArray? {
        if (encoded.isEmpty() || encoded.length > ((InstallerContract.MAX_ICON_BYTES + 2) / 3) * 4) return null
        val bytes = try { decode(encoded) } catch (_: IllegalArgumentException) { return null }
        if (bytes.size !in 33..InstallerContract.MAX_ICON_BYTES || !PNG_MAGIC.indices.all { bytes[it] == PNG_MAGIC[it] }) return null
        if (readInt(bytes, 8) != 13 || !bytes.copyOfRange(12, 16).contentEquals(byteArrayOf(73, 72, 68, 82))) return null
        if (!safeDimensions(readInt(bytes, 16), readInt(bytes, 20))) return null
        return bytes
    }

    private fun safeDimensions(width: Int, height: Int): Boolean =
        width in 1..MAX_ICON_DIMENSION && height in 1..MAX_ICON_DIMENSION && width.toLong() * height * 4 <= MAX_DECODED_BYTES

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 255) shl 24) or ((bytes[offset + 1].toInt() and 255) shl 16) or
            ((bytes[offset + 2].toInt() and 255) shl 8) or (bytes[offset + 3].toInt() and 255)

    private val PNG_MAGIC = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
    private const val MAX_ICON_DIMENSION = 1024
    private const val MAX_DECODED_BYTES = 4 * 1024 * 1024
}
