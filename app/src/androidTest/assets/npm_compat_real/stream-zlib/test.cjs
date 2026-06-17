"use strict";

module.exports = async function run() {
  const assert = require("assert");
  const { Readable } = require("stream");
  const isStream = require("is-stream");
  const gzipSize = require("gzip-size");

  const readable = Readable.from(["auto", "js", "6"]);
  assert.strictEqual(isStream(readable), true);

  let text = "";
  for await (const chunk of readable) {
    text += chunk;
  }

  const size = gzipSize.sync(text);
  assert.strictEqual(text, "autojs6");
  assert.strictEqual(typeof size, "number");
  assert.ok(size > text.length);

  console.log("realnpm.stream-zlib=" + isStream(Readable.from(["x"])) + "|" + text + "|" + size);
  return "PASS";
};
