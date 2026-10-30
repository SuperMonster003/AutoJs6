# paddle-ocr-onnx-engine

Embedded PP-OCRv3 engine on ONNX Runtime, used by packaged applications whose project selects the
"Paddle OCR" library and whose packager picked the ONNX Runtime plugin variant (`v3`). The host
application itself never runs it: inside AutoJs6, `ocr.paddle` binds the installed plugin.

## Source

The pipeline under `org.autojs.plugin.paddle.ocr.onnx` (everything except `OnnxPaddleOcrEngine.kt`)
is copied from `libs/ppocr-android-sdk` of `AutoJs6-Plugin-Paddle-OCR-PP-OCRv4` (318ecb3), which is
derived from PaddleOCR (Apache License 2.0, the per-file headers are kept). Changes made here:

- Package renamed from `com.paddle.ocr` to `org.autojs.plugin.paddle.ocr.onnx`.
- `PaddleOCR` is a blocking API without coroutines; `recognize` accepts a per-call `PaddleOCRConfig`.
- `OpenCVUtils` is removed: the caller loads OpenCV through the host (`OpenCvPluginHost`).
- Text line orientation classification (`ClassificationEngine`, `ClsPreprocessor`) runs between
  detection and recognition when a cls model is given.
- `EngineConfig.numThreads <= 0` keeps the ONNX Runtime default thread count.

Pending: sync with the PP-OCRv3 plugin SDK (`AutoJs6-Plugin-Paddle-OCR-PP-OCRv3`, roadmap P1.2) once
it is final, so both sides share the cls, slim and NNAPI behaviour.

## Native libraries

Neither ONNX Runtime nor OpenCV natives are packaged into the host APK. `app/build.gradle.kts`
excludes `libonnxruntime.so` and `libonnxruntime4j_jni.so`; the APK packager copies them, together
with `libopencv_java4.so` from the OpenCV plugin, from the installed plugins. The ONNX Runtime
version in `gradle/libs.versions.toml` must match the plugin's.
