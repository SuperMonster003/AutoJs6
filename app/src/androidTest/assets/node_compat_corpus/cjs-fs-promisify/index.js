module.exports = async function run() {
  const assert = require("assert");
  const fs = require("fs");
  const util = require("util");
  const readFile = util.promisify(fs.readFile);
  const writeFile = util.promisify(fs.writeFile);
  const file = "./compat-fs-promisify.txt";

  await writeFile(file, "promisify", "utf8");
  assert.strictEqual(await readFile(file, "utf8"), "promisify");
  if (fs.promises && typeof fs.promises.appendFile === "function") {
    await fs.promises.appendFile(file, ":fsp", "utf8");
    assert.strictEqual(await fs.promises.readFile(file, "utf8"), "promisify:fsp");
  }
  return "PASS";
};
