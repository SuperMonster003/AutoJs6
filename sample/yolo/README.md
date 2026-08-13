# YOLO NCNN Preview sample

This R3 sample uses the independently installed YOLO NCNN provider. Before running it:

1. Install the provider signed for the current AutoJs6 build.
2. Explicitly authorize and enable it in Plugin Center. Installing it alone keeps it disabled.
3. Put a local model under `models/yolo11n` with exactly these files:
   `model.json`, `model.ncnn.param`, and `model.ncnn.bin`.
4. Put the input image at `bus.jpg`, or change the path in the script.

The model and image are intentionally not included. R3 supports only CPU detection and an exact
provider component; it does not silently select or fall back to another provider.

Preview failures start with a stable `YOLO_*` code such as `YOLO_COMPONENT_REQUIRED`,
`YOLO_MODEL_INVALID`, `YOLO_MODEL_REJECTED`, `YOLO_PROVIDER_UNAVAILABLE`, or
`YOLO_UNSUPPORTED_CAPABILITY`, or `YOLO_DETECT_FAILED`. `YOLO_MODEL_REJECTED` means the
provider rejected the manifest, model graph, weights, or declared decoder profile after the
files were transferred. `YOLO_UNSUPPORTED_CAPABILITY` means the requested device, decoder,
protocol, or resource setting is outside the selected provider's advertised capabilities.
