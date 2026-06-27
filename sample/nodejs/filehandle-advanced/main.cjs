"nodejs";

const fs = require("fs/promises");
const path = require("path");

(async () => {
  const dir = path.join(process.cwd(), "tmp");
  const file = path.join(dir, "phase8.txt");
  await fs.mkdir(dir, { recursive: true });

  const handle = await fs.open(file, "w+");
  try {
    await handle.writeFile("phase8-filehandle", "utf8");
    await handle.sync();
  } finally {
    await handle.close();
  }

  const value = await fs.readFile(file, "utf8");
  await fs.rm(dir, { recursive: true, force: true });
  console.log("sample.filehandle-advanced.value=" + value);
  console.log("sample.filehandle-advanced=PASS");
})().catch((error) => {
  console.error(error && error.stack || error);
  process.exitCode = 1;
});
