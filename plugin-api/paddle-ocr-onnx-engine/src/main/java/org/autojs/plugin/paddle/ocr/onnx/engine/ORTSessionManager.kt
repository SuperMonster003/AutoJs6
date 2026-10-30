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

    val hasClassifier: Boolean get() = clsSession != null

    fun loadModels(detAssetPath: String, recAssetPath: String, clsAssetPath: String? = null) {
        val loadStart = System.currentTimeMillis()
        env = OrtEnvironment.getEnvironment()
        val opts = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            // 0 or a negative count keeps the ONNX Runtime default (one thread per physical core).
            // zh-CN: 0 或负数沿用 ONNX Runtime 默认值 (每个物理核心一个线程).
            if (config.numThreads > 0) setIntraOpNumThreads(config.numThreads)
        }
        try {
            val ortEnv = env ?: throw OCRError.ModelLoadFailed("OCR", Exception("Environment not initialized"))
            // Server-class models exceed 100 MB; keep them out of the Java heap by materializing the
            // asset once and letting ONNX Runtime map the file instead of copying a byte array.
            val detModel = materializeModelAsset(detAssetPath)
            val recModel = materializeModelAsset(recAssetPath)
            try {
                detSession = ortEnv.createSession(detModel.absolutePath, opts)
            } catch (t: Throwable) {
                throw OCRError.ModelLoadFailed("detection", t)
            }
            try {
                recSession = ortEnv.createSession(recModel.absolutePath, opts)
            } catch (t: Throwable) {
                detSession?.close()
                detSession = null
                throw OCRError.ModelLoadFailed("recognition", t)
            }

            detInputName = try {
                detSession!!.inputNames.iterator().next()
            } catch (t: Throwable) {
                throw OCRError.ModelLoadFailed("detection", t)
            }
            recInputName = try {
                recSession!!.inputNames.iterator().next()
            } catch (t: Throwable) {
                throw OCRError.ModelLoadFailed("recognition", t)
            }
            if (clsAssetPath != null) {
                clsSession = try {
                    ortEnv.createSession(materializeModelAsset(clsAssetPath).absolutePath, opts)
                } catch (t: Throwable) {
                    throw OCRError.ModelLoadFailed("classification", t)
                }
                clsInputName = try {
                    clsSession!!.inputNames.iterator().next()
                } catch (t: Throwable) {
                    throw OCRError.ModelLoadFailed("classification", t)
                }
            }
            coldLoadTimeMs = System.currentTimeMillis() - loadStart
        } finally {
            opts.close()
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
                    env = null
                }
            }
        }
    }

    /**
     * Copies an APK model asset into app-private storage (once per installed APK version) and
     * returns the file, so sessions can be created from a path that ONNX Runtime memory-maps.
     * Reading a 90-110 MB asset into a byte array exhausts the default 256 MB Java heap.
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
