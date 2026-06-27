"nodejs";

const fs = require("fs");
const fsp = require("fs/promises");
const path = require("path");

(async () => {
  const dir = path.join(process.cwd(), "tmp-watch");
  const file = path.join(dir, "watched.txt");
  await fsp.mkdir(dir, { recursive: true });
  await fsp.writeFile(file, "before", "utf8");

  const watcher = fs.watch(file, { persistent: false }, () => {});
  try {
    await fsp.writeFile(file, "after", "utf8");
    await new Promise((resolve) => setTimeout(resolve, 200));
  } finally {
    watcher.close();
    await fsp.rm(dir, { recursive: true, force: true });
  }

  console.log("sample.fs-watch.closed=true");
  console.log("sample.fs-watch=PASS");
})().catch((error) => {
  console.error(error && error.stack || error);
  process.exitCode = 1;
});
