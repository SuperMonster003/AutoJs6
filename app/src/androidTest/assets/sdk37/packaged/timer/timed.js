/*
 * Runs from the packaged app's timed-task scheduler 20 s after main.js scheduled it.
 * Writes a separate receipt so the runner can tell the alarm delivery from the launcher run.
 *
 * zh-CN: 由打包应用的定时任务调度器在 main.js 调度后 20 s 运行. 写独立回执, 使运行器区分闹钟触发与启动器运行.
 */
"use strict";
var file = new java.io.File(context.getExternalFilesDir(null), "sdk37-timed.json");
files.write(file.getAbsolutePath(), JSON.stringify({
    fired: true,
    firedAt: Date.now(),
    target: context.getApplicationInfo().targetSdkVersion,
    api: android.os.Build.VERSION.SDK_INT,
}));
