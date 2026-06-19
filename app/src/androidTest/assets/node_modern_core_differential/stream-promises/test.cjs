"use strict";

module.exports = async function run() {
  const { Readable, Writable, PassThrough } = require("stream");
  const streamPromises = require("stream/promises");
  const chunks = [];
  await streamPromises.pipeline(
    Readable.from(["a", "b"]),
    new Writable({
      write(chunk, encoding, callback) {
        chunks.push(chunk.toString());
        callback();
      },
    }),
  );
  const pass = new PassThrough();
  const finishedValue = streamPromises.finished(pass).then(() => "finished");
  pass.end("x");
  pass.resume();
  console.log("moderncore.stream-promises=PASS|" + [
    chunks.join(""),
    await finishedValue,
    typeof streamPromises.pipeline,
    typeof streamPromises.finished,
  ].join("|"));
  return "PASS";
};
