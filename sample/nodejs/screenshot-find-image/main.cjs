"nodejs";

(async function main() {
  const fs = require("fs");
  const image = require("image");

  const onePixelPng = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=";
  fs.writeFileSync("template.png", Buffer.from(onePixelPng, "base64"));

  try {
    const capture = await image.captureScreen({
      requireExistingPermission: true,
      timeoutMs: 3000
    });
    const template = await image.readImage("template.png", { timeoutMs: 1000 });
    const point = await image.findImage(capture, template, { threshold: 0.9, timeoutMs: 3000 });
    await image.recycle(template);
    await image.recycle(capture);
    console.log("sample.screenshot-find-image.point=" + JSON.stringify(point));
  } catch (error) {
    console.log("sample.screenshot-find-image.skipped=" + (error && (error.autojs6Code || error.code || error.name)));
  }

  console.log("sample.screenshot-find-image=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
