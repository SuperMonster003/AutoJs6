'ui';

/* 如需使用本地视频文件作为播放源, 可使用相对路径, 如 src="./video/sample.mp4". */
/* controller 属性用于显示 android 内置的简单控制器视图, 点击视频区域可弹出控制器. */

ui.layout(<vertical>
    <video id="video" src="@raw/text_tool" controller></video>
    <button id="video_btn" text="play" isColored bg="teal-600" />
</vertical>);

/** @type {JsButton} */
let videoBtnView = ui['video_btn'];

/** @type {JsVideoView} */
let videoView = ui['video'];

videoBtnView.on('click', () => {
    if (videoView.isPlaying()) {
        videoView.pause();
        setStatePaused();
    } else {
        videoView.start();
        setStatePlaying();
    }
});

videoView.setOnCompletionListener({
    onCompletion(mediaPlayer) {
        mediaPlayer.reset();
        videoView.attrReset('path');
        setStatePaused();
    },
});

function setStatePaused() {
    videoBtnView.attr('text', 'play');
    videoBtnView.attr('bg', 'teal-600');
}

function setStatePlaying() {
    videoBtnView.attr('text', 'pause');
    videoBtnView.attr('bg', 'blue-800');
}