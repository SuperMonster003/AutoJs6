# ocr-automation

OCR automation example showing the `ocr` and `image` bridge boundary.

- Capabilities: `ocr`, `image`
- Expected provider: Android OCR provider over opaque image handles
- Packaged support: model assets and live OCR provider policy are future gated
- Security limitations: raw bitmap, OCR engine, and native model objects are never exposed to Node

Expected output is listed in `expected-output.txt`.
