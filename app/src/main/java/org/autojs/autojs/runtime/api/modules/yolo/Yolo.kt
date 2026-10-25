package org.autojs.autojs.runtime.api.modules.yolo

import android.content.ComponentName
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.plugin.yolo.YoloPluginHost
import org.autojs.autojs.core.plugin.yolo.YoloSessionOpenException
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.autojs.plugin.yolo.api.YoloContract
import org.autojs.plugin.yolo.api.YoloContractException
import org.autojs.plugin.yolo.api.YoloContractViolation
import org.autojs.plugin.yolo.api.YoloDevice
import org.autojs.plugin.yolo.api.YoloErrorCode
import org.mozilla.javascript.NativeObject
import java.io.File

/** Explicit-provider Rhino Preview entry point for the isolated YOLO plugin. */
@Suppress("unused")
class Yolo(private val scriptRuntime: ScriptRuntime) : ArgumentGuards() {

    companion object : ArgumentGuards() {

        private const val MANIFEST_FILE = "model.json"
        private const val PARAM_FILE = "model.ncnn.param"
        private const val BIN_FILE = "model.ncnn.bin"

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun load(
            scriptRuntime: ScriptRuntime,
            args: Array<out Any?>,
        ): YoloDetectorNativeObject {
            if (args.size !in 1..2) {
                throw YoloScriptException(
                    YoloScriptException.INVALID_ARGUMENT,
                    "yolo.load accepts modelDir and optional options",
                )
            }
            return unwrapArguments(args) { argList ->
                val rawOptions = argList.getOrNull(1)
                val options = optionsOrEmpty(scriptRuntime, rawOptions)
                val component = parseComponent(options)
                val openOptions = parseOpenOptions(options)
                val modelArtifacts = resolveModelArtifacts(scriptRuntime, argList[0])

                val target = try {
                    runBlocking(scriptRuntime.coroutineContext) {
                        YoloPluginHost.requireExactTarget(ScriptRuntime.applicationContext, component)
                    }
                } catch (error: Exception) {
                    rethrowIfInterrupted(error)
                    throw YoloScriptException(
                        YoloScriptException.PROVIDER_UNAVAILABLE,
                        error.message ?: "the explicit YOLO provider is unavailable",
                        error,
                    )
                }

                val session = try {
                    runBlocking(scriptRuntime.coroutineContext) {
                        YoloPluginHost.openSession(
                            context = ScriptRuntime.applicationContext,
                            target = target,
                            modelArtifacts = modelArtifacts,
                            options = openOptions,
                        )
                    }
                } catch (error: Exception) {
                    rethrowIfInterrupted(error)
                    val code = openFailureScriptCode(error)
                    throw YoloScriptException(
                        code,
                        error.message ?: "the YOLO session could not be opened",
                        error,
                    )
                }

                try {
                    YoloDetectorNativeObject(scriptRuntime, session)
                } catch (error: Throwable) {
                    runCatching { session.close() }
                    throw error
                }
            }
        }

        private fun openFailureScriptCode(error: Exception): String {
            val remoteCode = (error as? YoloSessionOpenException)?.code
            if (remoteCode == YoloErrorCode.MODEL_REJECTED) {
                return YoloScriptException.MODEL_REJECTED
            }
            if (
                remoteCode == YoloErrorCode.UNSUPPORTED_CAPABILITY ||
                remoteCode == YoloErrorCode.UNSUPPORTED_PROTOCOL
            ) {
                return YoloScriptException.UNSUPPORTED_CAPABILITY
            }
            val localViolation = (error as? YoloContractException)?.violation
            if (
                localViolation == YoloContractViolation.CAPABILITY_INCOMPATIBLE ||
                localViolation == YoloContractViolation.PROTOCOL_INCOMPATIBLE
            ) {
                return YoloScriptException.UNSUPPORTED_CAPABILITY
            }
            return YoloScriptException.SESSION_OPEN_FAILED
        }

        private fun optionsOrEmpty(scriptRuntime: ScriptRuntime, rawOptions: Any?): NativeObject = when {
            rawOptions.isJsNullish() -> newNativeObject(scriptRuntime.topLevelScope)
            rawOptions is NativeObject -> rawOptions
            else -> throw YoloScriptException(
                YoloScriptException.INVALID_ARGUMENT,
                "yolo.load options must be a JavaScript object",
            )
        }

        private fun parseComponent(options: NativeObject): ComponentName {
            val rawComponent = options.inquire("component")
            if (rawComponent.isJsNullish()) {
                throw YoloScriptException(
                    YoloScriptException.COMPONENT_REQUIRED,
                    "yolo.load requires options.component; provider fallback is disabled",
                )
            }
            val flattened = try {
                coerceString(rawComponent).trim()
            } catch (error: Exception) {
                throw YoloScriptException(
                    YoloScriptException.INVALID_ARGUMENT,
                    "options.component must be a flattened Android component string",
                    error,
                )
            }
            if (flattened.isBlank()) {
                throw YoloScriptException(
                    YoloScriptException.COMPONENT_REQUIRED,
                    "options.component must not be blank; provider fallback is disabled",
                )
            }
            return ComponentName.unflattenFromString(flattened)
                ?: throw YoloScriptException(
                    YoloScriptException.INVALID_ARGUMENT,
                    "options.component must use the form package/class",
                )
        }

        private fun parseOpenOptions(options: NativeObject): YoloPluginHost.OpenSessionOptions {
            val device = coerceStringOption(options, "device", "cpu").trim().lowercase()
            if (device != "cpu") {
                throw YoloScriptException(
                    YoloScriptException.UNSUPPORTED_CAPABILITY,
                    "the current YOLO provider profile supports options.device=\"cpu\" only",
                )
            }
            val threads = coerceIntOption(
                options = options,
                name = "threads",
                fallback = YoloPluginHost.DEFAULT_CPU_THREADS,
            )
            val decoderId = coerceStringOption(
                options,
                "decoderId",
                YoloPluginHost.DEFAULT_DECODER_ID,
            ).trim()
            val timeoutMillis = coerceLongOption(
                options = options,
                name = "timeoutMillis",
                fallback = YoloPluginHost.DEFAULT_OPEN_TIMEOUT_MILLIS,
            )
            if (threads <= 0) invalidOption("threads", "must be positive")
            if (threads > YoloContract.MAX_CPU_THREADS) {
                invalidOption("threads", "exceeds ${YoloContract.MAX_CPU_THREADS}")
            }
            if (decoderId.isBlank()) invalidOption("decoderId", "must not be blank")
            if (timeoutMillis <= 0L) invalidOption("timeoutMillis", "must be positive")
            if (timeoutMillis > YoloContract.MAX_TIMEOUT_MILLIS) {
                invalidOption("timeoutMillis", "exceeds ${YoloContract.MAX_TIMEOUT_MILLIS}")
            }
            return YoloPluginHost.OpenSessionOptions(
                device = YoloDevice.CPU,
                cpuThreads = threads,
                decoderId = decoderId,
                timeoutMillis = timeoutMillis,
            )
        }

        private fun resolveModelArtifacts(
            scriptRuntime: ScriptRuntime,
            rawModelDirectory: Any?,
        ): List<YoloPluginHost.ModelArtifactFile> {
            val modelPath = try {
                coerceString(rawModelDirectory).trim()
            } catch (error: Exception) {
                throw YoloScriptException(
                    YoloScriptException.MODEL_INVALID,
                    "yolo.load modelDir must be a non-empty path",
                    error,
                )
            }
            if (modelPath.isBlank()) {
                throw YoloScriptException(
                    YoloScriptException.MODEL_INVALID,
                    "yolo.load modelDir must not be blank",
                )
            }
            val directory = try {
                File(scriptRuntime.files.nonNullPath(modelPath)).canonicalFile
            } catch (error: Exception) {
                throw YoloScriptException(
                    YoloScriptException.MODEL_INVALID,
                    "modelDir could not be resolved",
                    error,
                )
            }
            if (!directory.isDirectory) {
                throw YoloScriptException(
                    YoloScriptException.MODEL_INVALID,
                    "modelDir is not a readable directory: ${directory.path}",
                )
            }
            return listOf(
                requireModelArtifact(directory, "manifest", MANIFEST_FILE),
                requireModelArtifact(directory, "ncnn-param", PARAM_FILE),
                requireModelArtifact(directory, "ncnn-bin", BIN_FILE),
            )
        }

        private fun requireModelArtifact(
            directory: File,
            role: String,
            fileName: String,
        ): YoloPluginHost.ModelArtifactFile {
            val file = try {
                File(directory, fileName).canonicalFile
            } catch (error: Exception) {
                throw YoloScriptException(
                    YoloScriptException.MODEL_INVALID,
                    "model artifact $fileName could not be resolved",
                    error,
                )
            }
            if (file.parentFile != directory || !file.isFile || !file.canRead()) {
                throw YoloScriptException(
                    YoloScriptException.MODEL_INVALID,
                    "modelDir must contain a readable $fileName",
                )
            }
            return YoloPluginHost.ModelArtifactFile(
                role = role,
                logicalName = fileName,
                file = file,
            )
        }

        private fun coerceIntOption(
            options: NativeObject,
            name: String,
            fallback: Int,
        ): Int = try {
            options.inquire(name)?.let(::coerceIntNumber) ?: fallback
        } catch (error: Exception) {
            throw YoloScriptException(
                YoloScriptException.INVALID_ARGUMENT,
                "options.$name is invalid",
                error,
            )
        }

        private fun coerceLongOption(
            options: NativeObject,
            name: String,
            fallback: Long,
        ): Long = try {
            options.inquire(name)?.let(::coerceLongNumber) ?: fallback
        } catch (error: Exception) {
            throw YoloScriptException(
                YoloScriptException.INVALID_ARGUMENT,
                "options.$name is invalid",
                error,
            )
        }

        private fun coerceStringOption(
            options: NativeObject,
            name: String,
            fallback: String,
        ): String = try {
            options.inquire(name)?.let(::coerceString) ?: fallback
        } catch (error: Exception) {
            throw YoloScriptException(
                YoloScriptException.INVALID_ARGUMENT,
                "options.$name is invalid",
                error,
            )
        }

        private fun invalidOption(name: String, detail: String): Nothing =
            throw YoloScriptException(
                YoloScriptException.INVALID_ARGUMENT,
                "options.$name $detail",
            )

        private fun rethrowIfInterrupted(error: Exception) {
            if (ScriptInterruptedException.causedByInterrupt(error)) throw error
        }
    }
}
