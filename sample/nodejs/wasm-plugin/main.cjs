"nodejs";

function codeOf(error) {
  return String(error && (error.code || error.autojs6Code || error.name || "ERROR"));
}

(async () => {
  try {
    const plugins = require("plugins");
    plugins.install("org.example.wasm_math", {
      source: "./plugins/org.example.wasm_math",
      config: { mode: "wasm-policy" }
    });
    const plugin = plugins.load("org.example.wasm_math");
    const result = await plugin.add(19, 23);
    console.log("sample.wasm-plugin.add=" + result);
    await plugins.unload("org.example.wasm_math");
  } catch (error) {
    console.log("sample.wasm-plugin.unavailable=" + codeOf(error));
  }

  console.log("sample.wasm-plugin=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
