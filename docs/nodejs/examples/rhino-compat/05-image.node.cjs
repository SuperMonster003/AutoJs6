"nodejs";

const compat = require("autojs6:compat");

async function main() {
  const image = await compat.images.read("template.png", { timeoutMs: 1000 });
  const template = await compat.images.clip(image, [0, 0, 20, 20], { timeoutMs: 1000 });
  const point = await compat.images.findImage(image, template, {
    region: [0, 0, 200, 200],
    threshold: 0.9
  });
  console.log(JSON.stringify(point));
  await compat.images.recycle(template);
  await compat.images.recycle(image);
}

main().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
