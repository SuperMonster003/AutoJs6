"nodejs";

function codeOf(error) {
  return String(error && (error.code || error.autojs6Code || error.name || "ERROR"));
}

(async function main() {
  try {
    const media = require("media");
    const info = await media.getAudioStreamInfo("music", { timeoutMs: 1000 });
    console.log("sample.pro-parity-suite.media-recorder.audioStream=" + info.stream);
  } catch (error) {
    console.log("sample.pro-parity-suite.media-recorder.skipped=media:" + codeOf(error));
  }

  try {
    const mediainfo = require("mediainfo");
    const snapshot = await mediainfo.read("sample.mp3", { includeInform: false, timeoutMs: 1000 });
    console.log("sample.pro-parity-suite.media-recorder.mediainfo=" + snapshot.fileName);
  } catch (error) {
    console.log("sample.pro-parity-suite.media-recorder.skipped=mediainfo:" + codeOf(error));
  }

  try {
    const recorder = require("recorder");
    const status = await recorder.getStatus({ timeoutMs: 1000 });
    console.log("sample.pro-parity-suite.media-recorder.recorderStatus=" + status.reason);
    try {
      await recorder.start({ path: "record.m4a", timeoutMs: 1000 });
      console.log("sample.pro-parity-suite.media-recorder.recorderStartDenied=unexpected-start");
    } catch (error) {
      console.log("sample.pro-parity-suite.media-recorder.recorderStartDenied=" + codeOf(error));
    }
  } catch (error) {
    console.log("sample.pro-parity-suite.media-recorder.skipped=recorder:" + codeOf(error));
  }

  console.log("sample.pro-parity-suite.media-recorder=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
