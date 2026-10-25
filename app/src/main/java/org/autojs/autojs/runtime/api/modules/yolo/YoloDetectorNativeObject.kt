package org.autojs.autojs.runtime.api.modules.yolo

import android.graphics.RectF
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.core.plugin.yolo.YoloPluginHost
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.coerceDoubleNumber
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.autojs.plugin.yolo.api.YoloContract
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/** A Rhino-visible owner of exactly one remote YOLO session. */
class YoloDetectorNativeObject internal constructor(
    private val scriptRuntime: ScriptRuntime,
    private val session: YoloPluginHost.Session,
) : NativeObject(), Closeable {

    private val closed = AtomicBoolean(false)

    init {
        RhinoUtils.initNativeObjectPrototype(this)
        defineProperty(
            "detect",
            newBaseFunction(
                scriptRuntime.topLevelScope,
                "detect",
                { args -> detectRhino(args) },
                NOT_CONSTRUCTABLE,
            ),
            READONLY or DONTENUM or PERMANENT,
        )
        defineProperty(
            "close",
            newBaseFunction(
                scriptRuntime.topLevelScope,
                "close",
                { args ->
                    if (args.isNotEmpty()) {
                        throw YoloScriptException(
                            YoloScriptException.INVALID_ARGUMENT,
                            "detector.close does not accept arguments",
                        )
                    }
                    close()
                    RhinoUtils.UNDEFINED
                },
                NOT_CONSTRUCTABLE,
            ),
            READONLY or DONTENUM or PERMANENT,
        )
        scriptRuntime.closeableManager.add(this)
    }

    override fun getClassName(): String = "YoloDetector"

    private fun detectRhino(args: Array<out Any?>): NativeArray {
        if (args.size !in 1..2) {
            throw YoloScriptException(
                YoloScriptException.INVALID_ARGUMENT,
                "detector.detect accepts image and optional options",
            )
        }
        ensureOpen()
        val image = RhinoUtils.unwrap(args[0]) as? ImageWrapper
            ?: throw YoloScriptException(
                YoloScriptException.INVALID_ARGUMENT,
                "detector.detect image must be an ImageWrapper",
            )
        val options = optionsOrEmpty(RhinoUtils.unwrap(args.getOrNull(1)))
        val detectOptions = parseDetectOptions(options)
        val bitmap = try {
            image.bitmap
        } catch (error: Exception) {
            throw YoloScriptException(
                YoloScriptException.INVALID_ARGUMENT,
                "detector.detect image is unavailable or recycled",
                error,
            )
        }

        val result = try {
            runBlocking(scriptRuntime.coroutineContext) {
                session.detect(bitmap, detectOptions)
            }
        } catch (error: Exception) {
            rethrowIfInterrupted(error)
            if (closed.get() || session.isClosed) {
                throw YoloScriptException(
                    YoloScriptException.SESSION_CLOSED,
                    error.message ?: "the YOLO detector session is closed",
                    error,
                )
            }
            throw YoloScriptException(
                YoloScriptException.DETECT_FAILED,
                error.message ?: "YOLO detection failed",
                error,
            )
        }

        if (result.imageWidth != bitmap.width || result.imageHeight != bitmap.height) {
            throw YoloScriptException(
                YoloScriptException.DETECT_FAILED,
                "provider result image dimensions do not match the input image",
            )
        }
        val detections = result.detections.map { detection ->
            mapOf(
                "classId" to detection.classId,
                "label" to detection.label,
                "confidence" to detection.confidence,
                "bounds" to RectF(
                    detection.bounds.left.toFloat(),
                    detection.bounds.top.toFloat(),
                    detection.bounds.right.toFloat(),
                    detection.bounds.bottom.toFloat(),
                ),
            )
        }
        return RhinoUtils.toJsValue(scriptRuntime.topLevelScope, detections) as NativeArray
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            session.close()
        } finally {
            scriptRuntime.closeableManager.remove(this)
        }
    }

    private fun ensureOpen() {
        if (closed.get() || session.isClosed) {
            throw YoloScriptException(
                YoloScriptException.SESSION_CLOSED,
                "the YOLO detector is closed; call yolo.load again",
            )
        }
    }

    private fun optionsOrEmpty(rawOptions: Any?): NativeObject = when {
        rawOptions.isJsNullish() -> newNativeObject(scriptRuntime.topLevelScope)
        rawOptions is NativeObject -> rawOptions
        else -> throw YoloScriptException(
            YoloScriptException.INVALID_ARGUMENT,
            "detector.detect options must be a JavaScript object",
        )
    }

    private fun parseDetectOptions(options: NativeObject): YoloPluginHost.DetectOptions {
        val confidence = coerceDoubleOption(
            options = options,
            name = "confidence",
            fallback = YoloPluginHost.DEFAULT_CONFIDENCE_THRESHOLD,
        )
        val iouThreshold = coerceDoubleOption(
            options = options,
            name = "iouThreshold",
            fallback = YoloPluginHost.DEFAULT_IOU_THRESHOLD,
        )
        val maxDetections = coerceIntOption(
            options = options,
            name = "maxDetections",
            fallback = YoloPluginHost.DEFAULT_MAX_DETECTIONS,
        )
        val timeoutMillis = coerceLongOption(
            options = options,
            name = "timeoutMillis",
            fallback = YoloContract.DEFAULT_TIMEOUT_MILLIS,
        )
        if (!confidence.isFinite() || confidence !in 0.0..1.0) {
            invalidOption("confidence", "must be finite and in [0, 1]")
        }
        if (!iouThreshold.isFinite() || iouThreshold !in 0.0..1.0) {
            invalidOption("iouThreshold", "must be finite and in [0, 1]")
        }
        if (maxDetections <= 0) invalidOption("maxDetections", "must be positive")
        if (maxDetections > YoloContract.MAX_DETECTIONS) {
            invalidOption("maxDetections", "exceeds ${YoloContract.MAX_DETECTIONS}")
        }
        if (timeoutMillis <= 0L) invalidOption("timeoutMillis", "must be positive")
        if (timeoutMillis > YoloContract.MAX_TIMEOUT_MILLIS) {
            invalidOption("timeoutMillis", "exceeds ${YoloContract.MAX_TIMEOUT_MILLIS}")
        }
        return YoloPluginHost.DetectOptions(
            confidenceThreshold = confidence,
            iouThreshold = iouThreshold,
            maxDetections = maxDetections,
            timeoutMillis = timeoutMillis,
        )
    }

    private fun coerceDoubleOption(
        options: NativeObject,
        name: String,
        fallback: Double,
    ): Double = try {
        options.inquire(name)?.let(::coerceDoubleNumber) ?: fallback
    } catch (error: Exception) {
        throw invalidOption(name, error)
    }

    private fun coerceIntOption(
        options: NativeObject,
        name: String,
        fallback: Int,
    ): Int = try {
        options.inquire(name)?.let(::coerceIntNumber) ?: fallback
    } catch (error: Exception) {
        throw invalidOption(name, error)
    }

    private fun coerceLongOption(
        options: NativeObject,
        name: String,
        fallback: Long,
    ): Long = try {
        options.inquire(name)?.let(::coerceLongNumber) ?: fallback
    } catch (error: Exception) {
        throw invalidOption(name, error)
    }

    private fun invalidOption(name: String, cause: Throwable): YoloScriptException =
        YoloScriptException(
            YoloScriptException.INVALID_ARGUMENT,
            "options.$name is invalid",
            cause,
        )

    private fun invalidOption(name: String, detail: String): Nothing =
        throw YoloScriptException(
            YoloScriptException.INVALID_ARGUMENT,
            "options.$name $detail",
        )

    private fun rethrowIfInterrupted(error: Exception) {
        if (ScriptInterruptedException.causedByInterrupt(error)) throw error
    }
}
