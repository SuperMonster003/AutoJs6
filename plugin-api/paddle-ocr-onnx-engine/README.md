# paddle-ocr-onnx-engine

Embedded PP-OCRv3 engine on ONNX Runtime, used by packaged applications whose project selects the
"Paddle OCR" library and whose packager picked the ONNX Runtime plugin variant (`v3`). The host
application itself never runs it: inside AutoJs6, `ocr.paddle` binds the installed plugin.

## Source

The pipeline under `org.autojs.plugin.paddle.ocr.onnx` (everything except `OnnxPaddleOcrEngine.kt`)
is copied from `libs/ppocr-android-sdk` of `AutoJs6-Plugin-Paddle-OCR-PP-OCRv3` (b9fa0cc, roadmap
P1.2), which is derived from PaddleOCR (Apache License 2.0, the per-file headers are kept). Changes
made here:

- Package renamed from `io.github.supermonster003.autojs6.plugin.paddleocr.v3.sdk` to
  `org.autojs.plugin.paddle.ocr.onnx`.
- `PaddleOCR` is a blocking API without coroutines; `recognize` accepts a per-call `PaddleOCRConfig`,
  so detection and score settings change without reloading the models.
- `OpenCVUtils` is removed: the caller loads OpenCV through the host (`OpenCvPluginHost`).

`OnnxPaddleOcrEngine` maps `OcrOptions` as the plugin's `PpOcrV3EngineSettings.resolve` does (roadmap
D25): 0 or negative `cpuThreadNum` -> 4 threads, `useSlim` -> slim det / rec when packaged,
`useOpenCL` -> NNAPI execution provider with CPU fallback (result extras `executionProvider`),
`scoreThreshold` -1 -> 0.1, extras `useCls` -> orientation classification, and the same det tuning
extras. When the plugin SDK changes, copy it again and keep the changes above.

## Native libraries

Neither ONNX Runtime nor OpenCV natives are packaged into the host APK. `app/build.gradle.kts`
excludes `libonnxruntime.so` and `libonnxruntime4j_jni.so`; the APK packager copies them, together
with `libopencv_java4.so` from the OpenCV plugin, from the installed plugins. The ONNX Runtime
version in `gradle/libs.versions.toml` must match the plugin's.
