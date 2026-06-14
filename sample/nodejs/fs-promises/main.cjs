"nodejs";

(async function main() {
  const assert = require("assert");
  const fsp = require("fs/promises");

  await fsp.mkdir("out", { recursive: true });
  await fsp.writeFile("out/message.txt", "promise fs", "utf8");
  await fsp.appendFile("out/message.txt", " ok", "utf8");

  const message = await fsp.readFile("out/message.txt", "utf8");
  assert.strictEqual(message, "promise fs ok");

  console.log("sample.fs-promises.message=" + message);
  console.log("sample.fs-promises=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
