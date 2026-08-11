"use strict";

const providerComponent =
  "io.github.supermonster003.autojs6.plugin.yolo.ncnn/.provider.YoloProviderService";
const modelDirectory = files.path("./models/yolo11n");
const imagePath = files.path("./bus.jpg");

let detector = null;
let image = null;

try {
  detector = yolo.load(modelDirectory, {
    component: providerComponent,
    device: "cpu",
    threads: 4,
    decoderId: "ultralytics-detect",
    timeoutMillis: 120000,
  });
  image = images.read(imagePath, true);

  const detections = detector.detect(image, {
    confidence: 0.25,
    iouThreshold: 0.45,
    maxDetections: 100,
    timeoutMillis: 30000,
  });

  detections.forEach((detection) => {
    const bounds = detection.bounds;
    console.log(
      `${detection.label} ${detection.confidence.toFixed(4)} ` +
        `[${bounds.left}, ${bounds.top}, ${bounds.right}, ${bounds.bottom}] ` +
        `center=(${bounds.centerX()}, ${bounds.centerY()})`,
    );
  });
} finally {
  if (image !== null) {
    images.recycle(image);
  }
  if (detector !== null) {
    detector.close();
  }
}
