exports.name = "cjs-cycle";
exports.seen = [];
exports.note = function note(value) {
  exports.seen.push(value);
};

const esm = require("./esm-cycle.mjs");
exports.fromEsm = esm.name;
exports.esmSawCjs = esm.sawCjs;
exports.esmSawDone = esm.sawDone;
exports.done = true;

exports.result = function result() {
  return {
    fromEsm: exports.fromEsm,
    esmSawCjs: exports.esmSawCjs,
    esmSawDone: exports.esmSawDone,
    seen: exports.seen.join(","),
    done: exports.done,
  };
};
