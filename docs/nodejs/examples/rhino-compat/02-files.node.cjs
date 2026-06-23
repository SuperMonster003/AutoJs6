"nodejs";

const compat = require("autojs6:compat");

async function main() {
  const path = "out/rhino-compat-files.txt";
  compat.files.write(path, "hello from Node compat files");
  const text = compat.files.read(path);
  await compat.toast(text);
  compat.files.remove(path);
}

main().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
