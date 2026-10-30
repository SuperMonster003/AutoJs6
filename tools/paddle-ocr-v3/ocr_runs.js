"ui";
// Runs ocr.paddle on the samples next to this script and writes the texts to a JSON file.
// device_check.py fills CONFIG for host runs and packaged applications alike:
//   dir      sample directory, null for the working directory (packaged applications)
//   output   result file name in the external files directory of the running application
//   priority whether to report the stored Plugin Center order of paddle-ocr plugins (host only)
//   open     activity of the running application opened after the runs, for example Plugin Center (host only)
//   runs     [{ id, file, options, repeat, disable }], where disable lists packages switched off in the Plugin Center
//            store during that run (host only) and switched back afterwards
// zh-CN: 对脚本旁的样图运行 ocr.paddle 并把识别文本写入 JSON 文件. CONFIG 由 device_check.py 填入, 宿主运行与打包应用共用.
var CONFIG = __CONFIG__;

ui.layout(<vertical padding="24"><text id="state" text="Paddle OCR check running" /></vertical>);

threads.start(function () {
    var result = { runs: [], maps: [] };
    var dir = CONFIG.dir || files.cwd();
    function describe(error) {
        var text = String(error);
        if (error && error.javaException) text += " / " + String(error.javaException);
        return text;
    }
    function texts(list) {
        var out = [];
        var size = typeof list.size === "function" ? list.size() : list.length;
        for (var i = 0; i < size; i++) out.push(String(typeof list.get === "function" ? list.get(i) : list[i]));
        return out;
    }
    function mappedLibraries() {
        var lines = java.nio.file.Files.readAllLines(java.nio.file.Paths.get("/proc/self/maps"));
        var seen = {};
        var out = [];
        for (var i = 0; i < lines.size(); i++) {
            var match = /\/(lib[^\/\s]+\.so)\s*$/.exec(String(lines.get(i)));
            if (!match || !/onnxruntime|paddle_light|libNative|opencv|c\+\+_shared/.test(match[1])) continue;
            if (!seen[match[1]]) { seen[match[1]] = true; out.push(match[1]); }
        }
        return out;
    }
    try {
        if (CONFIG.priority) {
            var priority = org.autojs.autojs.core.plugin.center.PluginVariantPriorityStore.INSTANCE;
            result.storedOrder = texts(priority.getOrder("paddle-ocr"));
        }
        CONFIG.runs.forEach(function (run) {
            var entry = { id: run.id, times: [], disabled: {} };
            var store = run.disable && run.disable.length ? org.autojs.autojs.core.plugin.center.PluginEnableStore.INSTANCE : null;
            var previous = {};
            try {
                if (store) {
                    run.disable.forEach(function (name) {
                        previous[name] = store.isEnabled(context, name, true);
                        store.setEnabled(context, name, false);
                        entry.disabled[name] = previous[name];
                    });
                }
                entry.start = Date.now();
                var img = images.read(files.join(dir, run.file));
                if (!img) throw new Error("cannot read " + run.file);
                try {
                    for (var i = 0; i < run.repeat; i++) {
                        var begin = Date.now();
                        entry.texts = texts(ocr.paddle(img, run.options));
                        entry.times.push(Date.now() - begin);
                    }
                } finally {
                    img.recycle();
                }
            } catch (error) {
                entry.error = describe(error);
            } finally {
                entry.end = Date.now();
                Object.keys(previous).forEach(function (name) {
                    store.setEnabled(context, name, previous[name]);
                });
                // The store writes asynchronously; commit before a later instrumentation run restarts the process.
                if (store) context.getSharedPreferences("plugin_center_enable_state", 0).edit().commit();
            }
            result.runs.push(entry);
        });
        result.maps = mappedLibraries();
        if (CONFIG.open) {
            var intent = new android.content.Intent();
            intent.setClassName(context.getPackageName(), CONFIG.open);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    } catch (error) {
        result.error = describe(error);
    }
    var output = files.join(String(context.getExternalFilesDir(null).getAbsolutePath()), CONFIG.output);
    files.write(output + ".part", JSON.stringify(result));
    new java.io.File(output + ".part").renameTo(new java.io.File(output));
    exit();
});
