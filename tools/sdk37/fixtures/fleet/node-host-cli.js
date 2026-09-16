// Run inside an installed AutoJs6 host on an owned disposable API 37 AVD.
// Uses a private cache directory; no runtime service or package downloads.
"use strict";
(function () {
    var File = java.io.File;
    var resultFile = new File(context.getCacheDir(), "sdk37-node-host-cli-result.json");
    var result = { ok: false, hostUid: android.os.Process.myUid(), hostPackage: String(context.getPackageName()) };
    try {
        var pkg = "io.github.supermonster003.autojs6.plugin.nodejs";
        var info = context.getPackageManager().getApplicationInfo(pkg, 0);
        result.pluginUid = info.uid;
        if (result.hostUid === result.pluginUid) throw Error("Expected different host and plugin UIDs");
        var executable = new File(info.nativeLibraryDir, "libnodexe.so");
        result.executable = String(executable.getCanonicalPath());
        var root = new File(context.getCacheDir(), "sdk37-node-cli-" + Date.now());
        if (!root.mkdirs()) throw Error("Could not create private test directory");
        var plugin = context.createPackageContext(pkg, 0);
        var zip = new java.util.zip.ZipInputStream(plugin.getAssets().open("nodejs/cli/node-cli-24.21.0.bin"));
        var buffer = java.lang.reflect.Array.newInstance(java.lang.Byte.TYPE, 65536);
        var entry, entries = 0;
        while ((entry = zip.getNextEntry()) !== null) {
            var destination = new File(root, String(entry.getName()));
            if (!String(destination.getCanonicalPath()).startsWith(String(root.getCanonicalPath()) + "/")) throw Error("Unsafe archive path");
            if (entry.isDirectory()) destination.mkdirs();
            else {
                destination.getParentFile().mkdirs();
                var stream = new java.io.FileOutputStream(destination);
                try { var count; while ((count = zip.read(buffer)) !== -1) stream.write(buffer, 0, count); }
                finally { stream.close(); }
            }
            entries++;
        }
        zip.close();
        result.archiveEntries = entries;
        var bin = new File(root, "bin"); bin.mkdir();
        function run(name, args) {
            var link = new File(bin, name);
            android.system.Os.symlink(String(executable.getAbsolutePath()), String(link.getAbsolutePath()));
            var argv = java.lang.reflect.Array.newInstance(java.lang.Class.forName("java.lang.String"), args.length + 1);
            argv[0] = String(link.getAbsolutePath());
            args.forEach(function (arg, index) { argv[index + 1] = String(arg); });
            var outputFile = new File(root, name + "-output.txt");
            var builder = new java.lang.ProcessBuilder(argv).directory(root)
                .redirectErrorStream(true).redirectOutput(outputFile);
            builder.environment().put("AUTOJS6_NODE_CLI_ROOT", String(new File(root, "lib/node_modules").getAbsolutePath()));
            builder.environment().put("COREPACK_ENABLE_NETWORK", "0");
            builder.environment().put("COREPACK_HOME", String(new File(root, ".corepack").getAbsolutePath()));
            builder.environment().put("npm_config_update_notifier", "false");
            builder.environment().put("npm_config_cache", String(new File(root, ".npm").getAbsolutePath()));
            var process = builder.start();
            if (!process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) { process.destroy(); throw Error("Command timed out"); }
            if (outputFile.length() > 16384) throw Error("Unexpected output size");
            var output = files.read(String(outputFile.getAbsolutePath()));
            if (process.exitValue() !== 0) throw Error(name + " failed: " + output);
            return output.trim();
        }
        result.node = run("node", ["-p", "[process.getuid(), process.version, process.execPath === require('fs').realpathSync('/proc/self/exe')].join('|')"]);
        if (result.node !== result.hostUid + "|v24.21.0|true") throw Error("Unexpected Node identity: " + result.node);
        result.npm = run("npm", ["--version"]);
        result.npx = run("npx", ["--version"]);
        result.corepack = run("corepack", ["--version"]);
        if (result.npm !== "11.19.0" || result.npx !== "11.19.0" || result.corepack !== "0.36.0") throw Error("Unexpected CLI versions");
        result.ok = true;
    } catch (error) {
        result.error = String(error) + "\n" + String(error.stack || "");
    }
    files.write(String(resultFile.getAbsolutePath()), JSON.stringify(result, null, 2));
    console.log("sdk37-node-host-cli=" + JSON.stringify(result));
})();
