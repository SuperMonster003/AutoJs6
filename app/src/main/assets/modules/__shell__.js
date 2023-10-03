/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Shell}
 */
module.exports = function (scriptRuntime, scope) {
    const rtRootShell = scriptRuntime.getRootShell();

    let _ = {
        ShellCtor: (/* @IIFE */ () => {
            /**
             * @implements Internal.Shell
             */
            const ShellCtor = function () {
                /** @global */
                const shell = function (cmd, root) {
                    return scriptRuntime.shell(cmd, Number(Boolean(root)));
                };
                return Object.assign(shell, ShellCtor.prototype);
            };

            ShellCtor.prototype = {
                constructor: ShellCtor,
                fromIntent(i) {
                    return app.intentToShell(i);
                },
            };

            return ShellCtor;
        })(),
        scopeAugment() {
            Object.assign(scope, {
                /** @global */
                Menu: () => KeyCode(KeyEvent.KEYCODE_MENU),
                /** @global */
                Home: () => KeyCode(KeyEvent.KEYCODE_HOME),
                /** @global */
                Back: () => KeyCode(KeyEvent.KEYCODE_BACK),
                /** @global */
                Up: () => KeyCode(KeyEvent.KEYCODE_DPAD_UP),
                /** @global */
                Down: () => KeyCode(KeyEvent.KEYCODE_DPAD_DOWN),
                /** @global */
                Left: () => KeyCode(KeyEvent.KEYCODE_DPAD_LEFT),
                /** @global */
                Right: () => KeyCode(KeyEvent.KEYCODE_DPAD_RIGHT),
                /** @global */
                OK: () => KeyCode(KeyEvent.KEYCODE_DPAD_CENTER),
                /** @global */
                VolumeUp: () => KeyCode(KeyEvent.KEYCODE_VOLUME_UP),
                /** @global */
                VolumeDown: () => KeyCode(KeyEvent.KEYCODE_VOLUME_DOWN),
                /** @global */
                Power: () => KeyCode(KeyEvent.KEYCODE_POWER),
                /** @global */
                Camera: () => KeyCode(KeyEvent.KEYCODE_CAMERA),
                /** @global */
                Text: text => rtRootShell.Text(text),
                /** @global */
                Input: text => rtRootShell.Text(text),
                /** @global */
                Tap: (x, y) => rtRootShell.Tap(x, y),
                /** @global */
                Screencap: path => rtRootShell.Screencap(path),
                /** @global */
                KeyCode: keyCode => rtRootShell.KeyCode(keyCode),
                /** @global */
                SetScreenMetrics: (w, h) => rtRootShell.SetScreenMetrics(w, h),
                /** @global */
                Swipe: (x1, y1, x2, y2, duration) => duration === undefined
                    ? rtRootShell.Swipe(x1, y1, x2, y2)
                    : rtRootShell.Swipe(x1, y1, x2, y2, duration),
            });
        },
    };

    const shell = new _.ShellCtor();

    _.scopeAugment();

    return shell;
};