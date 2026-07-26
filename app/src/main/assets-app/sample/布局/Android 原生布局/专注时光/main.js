'ui';

ui.useAndroidLayout();
ui.layoutFile('res/layout/activity_main.xml');

const MODES = {
    focus: {
        seconds: 25 * 60,
        label: '专注阶段',
        hint: '保持专注, 一次只做一件事.',
    },
    rest: {
        seconds: 5 * 60,
        label: '休息阶段',
        hint: '起身活动一下, 为下一轮充电.',
    },
};

let modeName = 'focus';
let remainingSeconds = MODES[modeName].seconds;
let completedSessions = 0;
let timerId = null;

ui.statusBarColor('#FCE7DC');
ui.statusBarIconLight(true);
render();

ui['start_pause'].on('click', () => {
    timerId === null ? startTimer() : pauseTimer();
});

ui['reset'].on('click', () => {
    pauseTimer();
    remainingSeconds = MODES[modeName].seconds;
    render();
});

ui['skip'].on('click', () => {
    pauseTimer();
    switchMode();
    render();
});

ui.emitter.on('destroy', () => pauseTimer());

function startTimer() {
    if (timerId !== null) {
        return;
    }
    timerId = setInterval(() => {
        remainingSeconds -= 1;
        if (remainingSeconds <= 0) {
            if (modeName === 'focus') {
                completedSessions += 1;
            }
            switchMode();
            toast(modeName === 'focus' ? '休息结束, 继续专注.' : '本轮完成, 休息一下.');
        }
        render();
    }, 1e3);
    render();
}

function pauseTimer() {
    if (timerId === null) {
        return;
    }
    clearInterval(timerId);
    timerId = null;
    render();
}

function switchMode() {
    modeName = modeName === 'focus' ? 'rest' : 'focus';
    remainingSeconds = MODES[modeName].seconds;
}

function render() {
    const mode = MODES[modeName];
    const minutes = Math.floor(remainingSeconds / 60);
    const seconds = remainingSeconds % 60;
    const elapsed = mode.seconds - remainingSeconds;

    ui['mode_label'].setText(mode.label);
    ui['mode_hint'].setText(mode.hint);
    ui['timer_text'].setText(`${pad(minutes)}:${pad(seconds)}`);
    ui['timer_progress'].setMax(mode.seconds);
    ui['timer_progress'].setProgress(elapsed);
    ui['session_value'].setText(`${completedSessions} / 4`);
    ui['start_pause'].setText(timerId === null ? '开始' : '暂停');
}

function pad(value) {
    return value < 10 ? `0${value}` : String(value);
}
