"nodejs";

function codeOf(error) {
  return String(error && (error.code || error.autojs6Code || error.name || "ERROR"));
}

(async function main() {
  const plugins = require("plugins");
  console.log("sample.js-plugin-ui.provider=js-plugin-manager");

  try {
    plugins.install("org.example.sample_ui", {
      source: "./plugins/org.example.sample_ui",
      config: { title: "Plugin UI" }
    });
    const plugin = plugins.load("org.example.sample_ui");
    const descriptor = plugin.layout();
    console.log("sample.js-plugin-ui.layout=" + descriptor.type + "/" + descriptor.children.length);

    try {
      const ui = require("ui");
      const handle = await ui.showLayout(descriptor, { timeoutMs: 1000, drainIntervalMs: 50 });
      await handle.close({ timeoutMs: 1000 });
      console.log("sample.js-plugin-ui.closed=true");
    } catch (error) {
      console.log("sample.js-plugin-ui.ui=" + codeOf(error));
    }

    await plugins.unload("org.example.sample_ui");
  } catch (error) {
    console.log("sample.js-plugin-ui.unavailable=" + codeOf(error));
  }

  console.log("sample.js-plugin-ui=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
