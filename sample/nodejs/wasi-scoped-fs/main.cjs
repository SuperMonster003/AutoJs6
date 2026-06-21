"nodejs";

let rawWasi = "disabled";
try {
  require("node:wasi");
  rawWasi = "available-but-not-autojs-facade";
} catch (error) {
  rawWasi = String(error && (error.code || error.autojs6Code || error.name || "disabled"));
}

console.log("sample.wasi-scoped-fs.raw=" + rawWasi);
console.log("sample.wasi-scoped-fs.facade=design-gated");
console.log("sample.wasi-scoped-fs=PASS");
