/*
 * SDK 37 packaged example 3: countdown timed task plus TTS inside a user APK (Roadmap P3.4).
 * The timed task exercises the packaged app's own scheduler (row 31: exact alarms after the
 * 29 -> 37 jump); timed.js writes its own receipt when it fires. TTS speaks in the foreground
 * and waits for the utterance to finish (row 37 / P2.2: audio started by a visible app).
 *
 * zh-CN: SDK 37 打包示例 3: 用户 APK 内的倒计时定时任务与 TTS (Roadmap P3.4). 定时任务走打包应用自身的调度器
 * (第 31 行: 29 -> 37 跨越后的精确闹钟); timed.js 触发时写自己的回执. TTS 在前台朗读并等待结束 (第 37 行 / P2.2:
 * 可见应用发起的音频).
 */
"use strict";
var common = require("./common.js");
var receipt = common.receipt;
var check = common.check;
receipt.example = "timer";
common.save();

var COUNTDOWN_MILLIS = 20000;
var task = null;
check("countdown-task-scheduled", function () {
    task = tasks.addCountdownTask({ path: "./timed.js", after: COUNTDOWN_MILLIS });
    receipt.timedTask = { id: task.getId(), scheduledAt: Date.now(), countdownMillis: COUNTDOWN_MILLIS };
    return task.getId() > 0;
});

var ttsState = "pending";
var ttsDetail = "";
var ttsStartedAt = Date.now();
try {
    tts.utterance("SDK thirty seven packaged app")
        .on("done", function () { ttsState = "done"; })
        .on("error", function (error) { ttsState = "error"; ttsDetail = String(error && error.message ? error.message : error); })
        .on("stop", function () { ttsState = "stopped"; })
        .on("cancelled", function () { ttsState = "cancelled"; });
} catch (e) {
    ttsState = "error";
    ttsDetail = String(e);
}

receipt.ready = true;
common.save();

var TTS_DEADLINE = Date.now() + 45000;
function waitForTts() {
    if (ttsState === "pending" && Date.now() < TTS_DEADLINE) {
        setTimeout(waitForTts, 500);
        return;
    }
    receipt.tts = { state: ttsState, detail: ttsDetail, elapsedMillis: Date.now() - ttsStartedAt };
    check("tts-done", function () {
        return ttsState === "done";
    });
    common.commonChecks(function () {
        common.finish();
    });
}
setTimeout(waitForTts, 500);
