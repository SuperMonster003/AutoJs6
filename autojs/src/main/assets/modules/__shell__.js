let _ = {
    init(__runtime__, scope) {
        this.runtime = __runtime__;
        this.scope = scope;

        /**
         * @type {(cmd: string, root: number) => com.stardust.autojs.runtime.api.AbstractShell.Result}
         */
        this.rtShell = __runtime__.shell.bind(__runtime__);

        /**
         *
         * @type {com.stardust.autojs.runtime.api.AbstractShell}
         */
        this.rtRootShell = __runtime__.getRootShell();
    },
    getModule() {
        return (cmd, root) => this.rtShell(cmd, Number(Boolean(root)));
    },
    scopeAugment() {
        Object.assign(this.scope, {
            Menu: () => KeyCode(KeyEvent.KEYCODE_MENU),
            Home: () => KeyCode(KeyEvent.KEYCODE_HOME),
            Back: () => KeyCode(KeyEvent.KEYCODE_BACK),
            Up: () => KeyCode(KeyEvent.KEYCODE_DPAD_UP),
            Down: () => KeyCode(KeyEvent.KEYCODE_DPAD_DOWN),
            Left: () => KeyCode(KeyEvent.KEYCODE_DPAD_LEFT),
            Right: () => KeyCode(KeyEvent.KEYCODE_DPAD_RIGHT),
            OK: () => KeyCode(KeyEvent.KEYCODE_DPAD_CENTER),
            VolumeUp: () => KeyCode(KeyEvent.KEYCODE_VOLUME_UP),
            VolumeDown: () => KeyCode(KeyEvent.KEYCODE_VOLUME_DOWN),
            Power: () => KeyCode(KeyEvent.KEYCODE_POWER),
            Camera: () => KeyCode(KeyEvent.KEYCODE_CAMERA),
            Text: text => this.rtRootShell.Text(text),
            Input: text => this.rtRootShell.Text(text),
            Tap: (x, y) => this.rtRootShell.Tap(x, y),
            Screencap: path => this.rtRootShell.Screencap(path),
            KeyCode: keyCode => this.rtRootShell.KeyCode(keyCode),
            SetScreenMetrics: (w, h) => this.rtRootShell.SetScreenMetrics(w, h),
            Swipe: (x1, y1, x2, y2, duration) => duration === undefined
                ? this.rtRootShell.Swipe(x1, y1, x2, y2)
                : this.rtRootShell.Swipe(x1, y1, x2, y2, duration),
        });
    },
};

let $ = {
    getModule(__runtime__, scope) {
        _.init(__runtime__, scope);

        _.scopeAugment();

        return _.getModule();
    },
};

/**
 * @param {com.stardust.autojs.runtime.ScriptRuntime} __runtime__
 * @param {org.mozilla.javascript.Scriptable} scope
 * @return {Internal.Shell}
 */
module.exports = function (__runtime__, scope) {
    return $.getModule(__runtime__, scope);
};