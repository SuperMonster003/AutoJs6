"""Run the host's public `ocr.recognizeText` against installed OCR provider plugins on a device.

The host debug APK and the providers under test must already be installed. The script is launched
through `RunIntentActivity`; the receipt is polled from the host's external files directory.

Examples:
  python tools/16kb/ocr_provider_smoke.py localhost:54059 --jobs paddle:v6 --image generated
  python tools/16kb/ocr_provider_smoke.py emulator-5582 --jobs paddle:v3 rapid:v3 rapid:v6 --image reference
"""
import argparse
import json
import shlex
import subprocess
import sys
import time
from pathlib import Path

HOST = "org.autojs.autojs6"
RUNNER = f"{HOST}/org.autojs.autojs.external.open.RunIntentActivity"
FILES = f"/sdcard/Android/data/{HOST}/files"

TEMPLATE = r'''"ui";
ui.layout(<vertical padding="24"><text id="result" text="OCR provider smoke" /></vertical>);
threads.start(function () {
    var receipt = {label: "__LABEL__", image: "__IMAGE__", pageSize: Number(android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE)), results: []};
    var output = new java.io.File(context.getExternalFilesDir(null), "__RECEIPT__").getAbsolutePath();
    function makeBitmap(size) {
        if (receipt.image === "reference") {
            var id = context.getResources().getIdentifier("paddle_ocr_test", "drawable", context.getPackageName());
            var decoded = android.graphics.BitmapFactory.decodeResource(context.getResources(), id);
            var scaled = android.graphics.Bitmap.createScaledBitmap(decoded, decoded.getWidth() * 4, decoded.getHeight() * 4, false);
            decoded.recycle();
            return scaled;
        }
        var bitmap = android.graphics.Bitmap.createBitmap(size[0], size[1], android.graphics.Bitmap.Config.ARGB_8888);
        var canvas = new android.graphics.Canvas(bitmap);
        canvas["drawColor(int)"](android.graphics.Color.WHITE);
        var paint = new android.graphics.Paint();
        paint["setColor(int)"](android.graphics.Color.BLACK);
        var textSize = Math.max(24, Math.floor(Math.min(size[1] / 3.5, size[0] / 11)));
        paint.setTextSize(textSize);
        paint.setAntiAlias(true);
        canvas.drawText("TEST 16 KB", 50, textSize * 1.3, paint);
        canvas.drawText("Hello World", 50, textSize * 2.8, paint);
        return bitmap;
    }
    var jobs = __JOBS__;
    var sizes = __SIZES__;
    var enablePackages = __ENABLE_PACKAGES__;
    enablePackages.forEach(function (packageName) {
        Packages.org.autojs.autojs.core.plugin.center.PluginEnableStore.INSTANCE.setEnabled(context, packageName, true);
    });
    receipt.enabledPackages = enablePackages;
    sizes.forEach(function (size) {
        var bitmap = makeBitmap(size);
        var image = Packages.org.autojs.autojs.core.image.ImageWrapper.ofBitmap(runtime, bitmap);
        receipt.width = bitmap.getWidth();
        receipt.height = bitmap.getHeight();
        try {
            jobs.forEach(function (job) {
                var row = {mode: job.mode, variant: job.variant, width: bitmap.getWidth(), height: bitmap.getHeight()};
                var start = Date.now();
                try {
                    row.text = ocr.recognizeText(image, job);
                    row.ok = row.text.length > 0;
                } catch (e) { row.ok = false; row.error = String(e) + "\n" + e.stack; }
                row.milliseconds = Date.now() - start;
                receipt.results.push(row);
                files.write(output, JSON.stringify(receipt));
                android.util.Log.i("OcrProviderSmoke", JSON.stringify(row));
            });
        } finally { image.recycle(); }
    });
    receipt.ok = receipt.results.every(function (row) { return row.ok; });
    receipt.complete = true;
    files.write(output, JSON.stringify(receipt));
    ui.run(function () { ui.result.setText(JSON.stringify(receipt)); });
});
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("serial")
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--jobs", nargs="+", required=True, help="mode:variant pairs, e.g. paddle:v6 rapid:v3")
    parser.add_argument("--image", choices=("generated", "reference"), default="generated",
                        help="generated = 1000x320 canvas text; reference = host drawable paddle_ocr_test scaled x4")
    parser.add_argument("--sizes", nargs="+", default=["1000x320"],
                        help="generated canvas sizes as WIDTHxHEIGHT; every job runs once per size (ignored for reference)")
    parser.add_argument("--enable-packages", nargs="*", default=[],
                        help="plugin package names to mark enabled in Plugin Center before the jobs run "
                             "(fresh installs on 16 KB devices are not auto-enabled when the index records 4 KB)")
    parser.add_argument("--label", default=None)
    parser.add_argument("--out", type=Path, default=Path("build/tmp-16kb/ocr-provider-smoke"))
    parser.add_argument("--timeout", type=int, default=480)
    parser.add_argument("--expect-page-size", type=int, default=None)
    args = parser.parse_args()
    label = args.label or ("-".join(j.replace(":", "") for j in args.jobs) + "-" + args.image)
    receipt_name = f"ocr-provider-smoke-{label}.json"
    jobs = [dict(mode=j.split(":", 1)[0], variant=j.split(":", 1)[1]) for j in args.jobs]
    sizes = [[1, 1]] if args.image == "reference" else [[int(v) for v in s.lower().split("x", 1)] for s in args.sizes]
    script = (TEMPLATE.replace("__LABEL__", label).replace("__IMAGE__", args.image)
              .replace("__RECEIPT__", receipt_name).replace("__JOBS__", json.dumps(jobs))
              .replace("__SIZES__", json.dumps(sizes))
              .replace("__ENABLE_PACKAGES__", json.dumps(args.enable_packages)))
    args.out.mkdir(parents=True, exist_ok=True)

    def adb(*command, check=True, timeout=900):
        result = subprocess.run([args.adb, "-s", args.serial, *command], text=True, encoding="utf-8",
                                errors="replace", capture_output=True, timeout=timeout)
        if check and result.returncode:
            raise RuntimeError(f"adb {command}: {result.stdout}\n{result.stderr}")
        return (result.stdout + result.stderr).strip()

    adb("shell", "rm", "-f", f"{FILES}/{receipt_name}", check=False)
    adb("logcat", "-c", check=False)
    adb("shell", "am", "force-stop", HOST)
    print(adb("shell", shlex.join(["am", "start", "-n", RUNNER, "--es", "script", script])), flush=True)
    deadline = time.monotonic() + args.timeout
    result = None
    while time.monotonic() < deadline:
        raw = adb("shell", "cat", f"{FILES}/{receipt_name}", check=False, timeout=60)
        try:
            result = json.loads(raw)
            if result.get("complete"):
                break
        except json.JSONDecodeError:
            pass
        time.sleep(3)
    result = result or {"label": label, "error": "No completed receipt", "ok": False}
    result["serial"] = args.serial
    result["hostApkSha256"] = adb("shell", "sha256sum", adb("shell", "pm", "path", HOST).splitlines()[0].removeprefix("package:")).split()[0]
    (args.out / receipt_name).write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    (args.out / receipt_name.replace(".json", ".logcat.txt")).write_text(
        adb("logcat", "-d", "-s", "OcrProviderSmoke:I", "AndroidRuntime:E", "libc:F", "DEBUG:F", "*:S", check=False), encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2), flush=True)
    ok = bool(result.get("ok")) and (args.expect_page_size is None or result.get("pageSize") == args.expect_page_size)
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
