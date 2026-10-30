#!/usr/bin/env python3
"""Relative paths of the bridged file members on a device (AutoJs6 api-coverage Q120 (b)): files.toFile, files.open and zip
run in the host process and take relative paths against the script's source directory, in a Node project and in a Bun
script, main thread and Worker. Each run pushes a script next to data.txt in an empty directory on shared storage, so
that the file exists only there: the private working directory of the runtime has no such path for the host. Bun also
runs with java.interop as its only grant of the family: the directory comes from execution.control, not files.control.
Only one line per check is printed; the pushed directory is removed afterwards. Grants are set and restored by
device_bun_automation.py grant; the packages are recorded and restored by its record / restore phases.

Usage: device_relative_paths.py --serial SERIAL [--runtimes node,bun] [--report FILE]
Expects host 6.8.0 with the Node and Bun plugins installed, all-files access for the host, and for Bun the grants
java.interop on (files.control may be off).
"""
import argparse
import json
from pathlib import Path
import sys
import uuid

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import device_bun_js_tools as base  # noqa: E402

base.DEVICE_DIR = "/sdcard/Download/aj6-q120"
DATA = "relative path check" + chr(10)

# The checks, against D (the directory the script was pushed to; the host may report it as /sdcard or /storage/emulated/0):
# each result is {name: value}, compared with EXPECTED.
BODY = """
async function aj6Checks(files, zip, java, D, cwd) {
    const out = {};
    const inside = (path, name) => String(path).endsWith(D.slice(D.indexOf('/Download/')) + name);
    const check = async (name, run) => {
        try { out[name] = await run(); } catch (e) { out[name] = 'threw ' + (e && e.name) + ': ' + String(e && e.message).slice(0, 160); }
    };
    await check('cwd', () => inside(cwd(), ''));
    await check('toFile.path', async () => {
        const file = await files.toFile('data.txt');
        const path = await file.call('getPath'), exists = await file.call('exists');
        await java.release(file);
        return [inside(path, '/data.txt'), exists];
    });
    await check('open.read', async () => {
        const handle = await files.open('data.txt', 'r');
        const text = await handle.call('read');
        await handle.call('close');
        await java.release(handle);
        return String(text);
    });
    await check('open.parent', async () => {
        const handle = await files.open('./sub/../data.txt', 'r');
        await handle.call('close');
        await java.release(handle);
        return true;
    });
    await check('zip.create', async () => {
        await zip.zipFile('data.txt', 'out.zip');
        const file = await files.toFile('out.zip');
        const exists = await file.call('exists');
        await java.release(file);
        return exists;
    });
    await check('zip.open', async () => {
        const archive = await zip.open('out.zip');
        const valid = await archive.isValidZipFile();
        const removed = await files.toFile('out.zip');
        await removed.call('delete');
        await java.release(removed);
        return valid;
    });
    await check('absolute', async () => {
        const file = await files.toFile(D + '/data.txt');
        const exists = await file.call('exists');
        await java.release(file);
        return exists;
    });
    return out;
}
"""
EXPECTED = {"cwd": True, "toFile.path": [True, True], "open.read": DATA, "open.parent": True, "zip.create": True,
            "zip.open": True, "absolute": True}

NODE_MAIN = """
"nodejs";
const AJ6_RUN = 'RUN', D = 'DIR';
EMIT
BODY
const {Worker} = require('node:worker_threads');
const cwd = () => require('autojs6:engines').myEngine().hostCwd;
(async () => {
    const [files, zip, java] = [require('autojs6:files'), require('autojs6:zip'), require('autojs6:java')];
    aj6Emit(AJ6_RUN, 0, await aj6Checks(files, zip, java, D, cwd));
    aj6Emit(AJ6_RUN, 1, await new Promise((resolve, reject) => {
        const worker = new Worker(`
const {parentPort} = require('node:worker_threads');
const aj6Checks = ${aj6Checks};
aj6Checks(require('autojs6:files'), require('autojs6:zip'), require('autojs6:java'), ${JSON.stringify(D)}, ${cwd})
    .then(value => parentPort.postMessage(value), e => parentPort.postMessage({error: String(e && e.stack || e)}));
`, {eval: true});
        worker.on('message', resolve);
        worker.on('error', reject);
    }));
})().catch(e => aj6Emit(AJ6_RUN, 2, {error: String(e && e.stack || e)})).finally(() => aj6Emit(AJ6_RUN, -1, {done: 1}));
"""

BUN_MAIN = """
"bun";
const AJ6_RUN = 'RUN', D = 'DIR';
EMIT
BODY
(async () => {
    const {createHostClient} = await import(process.env.AUTOJS6_HOST_CLIENT_MODULE);
    const host = await createHostClient();
    aj6Emit(AJ6_RUN, 0, await aj6Checks(host.files, host.zip, host.java, D, () => D));
    const worker = new Worker(URL.createObjectURL(new Blob([`
BODY_TEXT
self.onmessage = async () => {
    try {
        const {createHostClient} = await import(process.env.AUTOJS6_HOST_CLIENT_MODULE);
        const host = await createHostClient();
        postMessage(await aj6Checks(host.files, host.zip, host.java, 'DIR', () => 'DIR'));
    } catch (e) {
        postMessage({error: String(e && e.stack || e)});
    }
};
`], {type: 'text/javascript'})));
    aj6Emit(AJ6_RUN, 1, await new Promise((resolve, reject) => {
        worker.onmessage = event => resolve(event.data);
        worker.onerror = event => reject(new Error(event.message));
        worker.postMessage(1);
    }));
    worker.terminate();
})().catch(e => aj6Emit(AJ6_RUN, 2, {error: String(e && e.stack || e)})).finally(() => aj6Emit(AJ6_RUN, -1, {done: 1}));
"""


def script(template, run, directory):
    body_text = BODY.replace("\\", "\\\\").replace("`", "\\`").replace("${", "\\${")
    return (template.lstrip().replace("BODY_TEXT", body_text).replace("BODY", BODY).replace("EMIT", base.EMIT_JS)
            .replace("RUN", run).replace("DIR", directory))


def run_one(serial, runtime):
    run = uuid.uuid4().hex[:8]
    # The source directory as the host reports it: the canonical form of the pushed path.
    directory = base.DEVICE_DIR.replace("/sdcard", "/storage/emulated/0")
    extra = {"data.txt": DATA.encode("utf-8")}
    if runtime == "node":
        capabilities = ["java_interop", "files", "files.write", "engines"]
        extra["project.json"] = json.dumps({"name": "q120", "type": "node", "main": "main.js",
                                            "node": {"permissions": capabilities, "timeoutMs": 120000}}).encode("utf-8")
    results = base.run_script(serial, "main.js", script(NODE_MAIN if runtime == "node" else BUN_MAIN, run, directory), run, 120, extra)
    report, failed = {}, 0
    for index, form in ((0, "main"), (1, "worker")):
        out = results.get(index) or {}
        for name, expected in EXPECTED.items():
            ok = out.get(name) == expected
            report[f"{runtime} {form} {name}"] = "pass" if ok else json.dumps(out.get(name, out.get("error", "missing")), ensure_ascii=False)[:220]
            failed += not ok
    if 2 in results:
        report[f"{runtime} error"] = results[2].get("error", "")[:300]
        failed += 1
    return report, failed


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--runtimes", default="node,bun")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    sys.stdout.reconfigure(errors="backslashreplace")
    report, failed = {}, 0
    for runtime in args.runtimes.split(","):
        part, count = run_one(args.serial, runtime)
        report.update(part)
        failed += count
        print(f"{runtime}: {len(part) - count} / {len(part)}")
        for key, value in part.items():
            if value != "pass":
                print(f"  {key}: {value}")
    if args.report:
        args.report.write_text(json.dumps(report, indent=1, ensure_ascii=False) + chr(10), encoding="utf-8")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
