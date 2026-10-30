// Copyright (c) 2026 PaddlePaddle Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.autojs.plugin.paddle.ocr.onnx.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.os.Build
import android.util.Log
import org.autojs.plugin.paddle.ocr.onnx.EngineConfig
import org.autojs.plugin.paddle.ocr.onnx.model.OCRError
import java.io.File
import java.nio.FloatBuffer

class ORTSessionManager(
    private val context: Context,
    private val config: EngineConfig,
) {
    private var env: OrtEnvironment? = null
    private var detSession: OrtSession? = null
    private var recSession: OrtSession? = null
    private var clsSession: OrtSession? = null
    private var detInputName: String = "x"
    private var recInputName: String = "x"
    private var clsInputName: String = "x"
    var coldLoadTimeMs: Long = 0
        private set

    /** [ExecutionProviders.CPU] or [ExecutionProviders.NNAPI], whichever the sessions were created with. */
    var executionProvider: String = ExecutionProviders.CPU
        private set

    val hasClassifier: Boolean get() = clsSession != null

    fun loadModels(detAssetPath: String, recAssetPath: String, clsAssetPath: String? = null) {
        val loadStart = System.currentTimeMillis()
        val ortEnv = OrtEnvironment.getEnvironment()
        env = ortEnv
        // Keep models out of the Java heap by materializing each asset once and letting
        // ONNX Runtime map the file instead of copying a byte array.
        val detModel = materializeModelAsset(detAssetPath)
        val recModel = materializeModelAsset(recAssetPath)
        val clsModel = clsAssetPath?.let { materializeModelAsset(it) }
        if (ExecutionProviders.shouldRequestNnapi(config.useNnapi, Build.VERSION.SDK_INT)) {
            try {
                createSessions(ortEnv, detModel, recModel, clsModel, nnapi = true)
                executionProvider = ExecutionProviders.NNAPI
            } catch (t: Throwable) {
                closeSessions()
                Log.w(TAG, "NNAPI execution provider unavailable, using CPU: $t")
                createSessions(ortEnv, detModel, recModel, clsModel, nnapi = false)
                executionProvider = ExecutionProviders.CPU
            }
        } else {
            if (config.useNnapi) {
                Log.i(TAG, "NNAPI needs Android 8.1 (API ${ExecutionProviders.NNAPI_MIN_SDK}), using CPU")
            }
            createSessions(ortEnv, detModel, recModel, clsModel, nnapi = false)
            executionProvider = ExecutionProviders.CPU
        }
        coldLoadTimeMs = System.currentTimeMillis() - loadStart
        Log.i(TAG, "Sessions ready on $executionProvider in $coldLoadTimeMs ms (cls ${if (clsModel != null) "on" else "off"})")
    }

    private fun createSessions(ortEnv: OrtEnvironment, detModel: File, recModel: File, clsModel: File?, nnapi: Boolean) {
        val opts = OrtSession.SessionOptions()
        try {
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            opts.setIntraOpNumThreads(config.numThreads)
            if (nnapi) {
                opts.addNnapi()
            }
            detSession = createSession(ortEnv, detModel, opts, "detection")
            detInputName = firstInputName(detSession!!, "detection")
            recSession = createSession(ortEnv, recModel, opts, "recognition")
            recInputName = firstInputName(recSession!!, "recognition")
            if (clsModel != null) {
                clsSession = createSession(ortEnv, clsModel, opts, "classification")
                clsInputName = firstInputName(clsSession!!, "classification")
            }
        } catch (t: Throwable) {
            closeSessions()
            throw t
        } finally {
            opts.close()
        }
    }

    private fun createSession(ortEnv: OrtEnvironment, model: File, opts: OrtSession.SessionOptions, modelName: String): OrtSession {
        return try {
            ortEnv.createSession(model.absolutePath, opts)
        } catch (t: Throwable) {
            throw OCRError.ModelLoadFailed(modelName, t)
        }
    }

    private fun firstInputName(session: OrtSession, modelName: String): String {
        return try {
            session.inputNames.iterator().next()
        } catch (t: Throwable) {
            throw OCRError.ModelLoadFailed(modelName, t)
        }
    }

    fun runDetection(input: FloatArray, shape: LongArray): Pair<FloatArray, LongArray> {
        val session = detSession
            ?: throw OCRError.ModelLoadFailed("detection", Exception("Session not initialized"))
        val ortEnv = env
            ?: throw OCRError.ModelLoadFailed("detection", Exception("Environment not initialized"))
        return runSession(ortEnv, session, detInputName, input, shape, "detection")
    }

    fun runRecognition(input: FloatArray, shape: LongArray): Pair<FloatArray, LongArray> {
        val session = recSession
            ?: throw OCRError.ModelLoadFailed("recognition", Exception("Session not initialized"))
        val ortEnv = env
            ?: throw OCRError.ModelLoadFailed("recognition", Exception("Environment not initialized"))
        return runSession(ortEnv, session, recInputName, input, shape, "recognition")
    }

    fun runClassification(input: FloatArray, shape: LongArray): Pair<FloatArray, LongArray> {
        val session = clsSession
            ?: throw OCRError.ModelLoadFailed("classification", Exception("Session not initialized"))
        val ortEnv = env
            ?: throw OCRError.ModelLoadFailed("classification", Exception("Environment not initialized"))
        return runSession(ortEnv, session, clsInputName, input, shape, "classification")
    }

    fun release() {
        closeSessions()
        env = null
    }

    private fun closeSessions() {
        try {
            detSession?.close()
        } finally {
            detSession = null
            try {
                recSession?.close()
            } finally {
                recSession = null
                try {
                    clsSession?.close()
                } finally {
                    clsSession = null
                }
            }
        }
    }

    /**
     * Copies an APK model asset into app-private storage (once per installed APK version) and
     * returns the file, so sessions can be created from a path that ONNX Runtime memory-maps.
     */
    private fun materializeModelAsset(assetPath: String): File {
        return try {
            val directory = File(context.noBackupFilesDir, "ort-models").apply { mkdirs() }
            val target = File(directory, assetPath.replace('/', '_'))
            val stamp = File(directory, target.name + ".stamp")
            val installStamp = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
            if (!target.isFile || !stamp.isFile || stamp.readText() != installStamp) {
                val temporary = File(directory, target.name + ".tmp")
                context.assets.open(assetPath).use { input ->
                    temporary.outputStream().use { output -> input.copyTo(output, COPY_BUFFER_BYTES) }
                }
                if (!temporary.renameTo(target)) {
                    target.delete()
                    check(temporary.renameTo(target)) { "Unable to publish $target" }
                }
                stamp.writeText(installStamp)
            }
            target
        } catch (t: Throwable) {
            throw OCRError.ModelNotFound(assetPath, t)
        }
    }

    private companion object {
        const val TAG = "ORTSessionManager"
        const val COPY_BUFFER_BYTES = 1 shl 20
    }

    private fun runSession(
        ortEnv: OrtEnvironment,
        session: OrtSession,
        inputName: String,
        input: FloatArray,
        shape: LongArray,
        modelName: String,
    ): Pair<FloatArray, LongArray> {
        val tensor = try {
            OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(input), shape)
        } catch (t: Throwable) {
            throw OCRError.InferenceFailed(modelName, t)
        }
        val result = try {
            try {
                session.run(mapOf(inputName to tensor))
            } catch (t: Throwable) {
                throw OCRError.InferenceFailed(modelName, t)
            }
        } finally {
            tensor.close()
        }

        return try {
            try {
                val outputName = session.outputNames.iterator().next()
                val ortValue = result.get(outputName)
                    .orElseThrow { Exception("No output tensor found") }
                val outputTensor = ortValue as? OnnxTensor
                    ?: throw Exception("Output is not an ONNX tensor")
                Pair(copyFloatBuffer(outputTensor.floatBuffer), outputTensor.info.shape)
            } catch (t: Throwable) {
                throw OCRError.InferenceFailed(modelName, t)
            }
        } finally {
            result.close()
        }
    }

    private fun copyFloatBuffer(buffer: FloatBuffer): FloatArray {
        val duplicate = buffer.duplicate()
        duplicate.rewind()
        val output = FloatArray(duplicate.remaining())
        duplicate.get(output)
        return output
    }
}
