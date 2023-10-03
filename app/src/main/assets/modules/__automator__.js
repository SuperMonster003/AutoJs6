// noinspection JSUnusedGlobalSymbols

/* Overwritten protection. */

let { util } = global;

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Automator}
 */
module.exports = function (scriptRuntime, scope) {
    const ResultAdapter = require('result-adapter');

    const Path = android.graphics.Path;
    const Rect = android.graphics.Rect;
    const GestureDescription = android.accessibilityservice.GestureDescription;
    const AccessibilityBridge = org.autojs.autojs.core.accessibility.AccessibilityBridge;

    /**
     * @type {org.autojs.autojs.core.accessibility.SimpleActionAutomator}
     */
    const rtAutomator = scriptRuntime.automator;

    /**
     * @type {org.autojs.autojs.core.accessibility.AccessibilityBridge}
     */
    const a11yBridge = scriptRuntime.accessibilityBridge;

    let _ = {
        Auto: (/* @IIFE */ () => {
            /**
             * @implements Internal.Auto
             */
            const Auto = function () {
                return Object.assign(function (mode) {
                    if (typeof mode === 'string') {
                        auto.setMode(mode);
                    }
                    a11yBridge.ensureServiceEnabled();
                }, Auto.prototype);
            };

            Auto.prototype = {
                constructor: Auto,
                get service() {
                    return a11yBridge.getService();
                },
                get windows() {
                    return this.service === null ? [] : util.java.toJsArray(this.service.getWindows(), true);
                },
                get root() {
                    let root = a11yBridge.getRootInCurrentWindow();
                    return root ? UiObject.createRoot(root) : null;
                },
                get rootInActiveWindow() {
                    let root = a11yBridge.getRootInActiveWindow();
                    return root ? UiObject.createRoot(root) : null;
                },
                get windowRoots() {
                    return util.java.toJsArray(a11yBridge.windowRoots(), false)
                        .map(root => UiObject.createRoot(root));
                },
                stateListener(listener) {
                    return a11yBridge.setAccessibilityListener(listener);
                },
                registerEvent(name, listener) {
                    return rtAutomator.registerEvent(name, listener);
                },
                /** @deprecated */
                registerEvents(name, listener) {
                    return rtAutomator.registerEvent(name, listener);
                },
                removeEvent(name) {
                    return rtAutomator.removeEvent(name);
                },
                /** @deprecated */
                removeEvents(name) {
                    return rtAutomator.removeEvent(name);
                },
                waitFor(timeout) {
                    automator.waitForService(timeout);
                },
                setMode(modeStr) {
                    if (typeof modeStr !== 'string') {
                        throw TypeError('Mode should be a string for auto.setMode()');
                    }
                    let mode = _.modes[modeStr];
                    if (mode === undefined) {
                        throw Error(`Unknown mode for auto.setMode(): ${modeStr}`);
                    }
                    a11yBridge.setMode(mode);
                },
                setFlags(flags) {
                    let flagStrings;
                    if (Array.isArray(flags)) {
                        flagStrings = flags;
                    } else if (typeof flags === 'string') {
                        flagStrings = [ flags ];
                    } else {
                        throw TypeError(`Unknown flags: ${flags}`);
                    }
                    let flagsInt = 0;
                    flagStrings.forEach((s) => {
                        let flag = _.flagsMap[s];
                        if (flag === undefined) {
                            throw Error(`Unknown flag for auto.setFlags(): ${flag}`);
                        }
                        flagsInt |= flag;
                    });
                    a11yBridge.setFlags(flagsInt);
                },
                setWindowFilter(filter) {
                    a11yBridge.setWindowFilter(new AccessibilityBridge.WindowFilter({ filter }));
                },
            };

            return Auto;
        })(),
        Automator: (/* @IIFE */ () => {
            /**
             * @implements Internal.Automator
             */
            const Automator = function () {

            };

            Automator.prototype = {
                constructor: Automator,
                press(x, y, delay) {
                    return rtAutomator.press(x, y, delay);
                },
                gesture(duration, points) {
                    return rtAutomator.gesture.apply(rtAutomator, [ 0 ].concat(Array.from(arguments)));
                },
                gestureAsync(duration, points) {
                    return rtAutomator.gestureAsync.apply(rtAutomator, [ 0 ].concat(Array.from(arguments)));
                },
                swipe(x1, y1, x2, y2, duration) {
                    return rtAutomator.swipe(x1, y1, x2, y2, duration);
                },
                isServiceRunning() {
                    return rtAutomator.isServiceRunning();
                },
                ensureService() {
                    rtAutomator.ensureService();
                },
                waitForService(timeout) {
                    a11yBridge.waitForServiceEnabled(_.parseNumber(timeout, -1));
                },
                click() {
                    if (arguments.length === 2) {
                        let [ x, y ] = arguments;
                        if (typeof x === 'number' && typeof y === 'number') {
                            return rtAutomator.click(x, y);
                        }
                    }
                    let target = arguments[0];
                    if (target instanceof Rect) {
                        return this.click(target.centerX(), target.centerY());
                    }
                    if (target instanceof UiObject) {
                        return target.clickable() ? target.click() : this.click(target.bounds());
                    }
                    return _.performAction(function (target) {
                        return rtAutomator.click(target);
                    }, arguments);
                },
                longClick() {
                    if (arguments.length === 2) {
                        let [ x, y ] = arguments;
                        if (typeof x === 'number' && typeof y === 'number') {
                            return rtAutomator.longClick(x, y);
                        }
                    }
                    return _.performAction(function (target) {
                        return rtAutomator.longClick(target);
                    }, arguments);
                },
                input() {
                    if (arguments.length === 2) {
                        let [ index, text ] = arguments;
                        return rtAutomator.appendText(rtAutomator.editable(index), text);
                    } else {
                        let [ text ] = arguments;
                        return rtAutomator.appendText(rtAutomator.editable(-1), text);
                    }
                },
                gestures() {
                    return rtAutomator.gestures(_.toStrokes(arguments));
                },
                gesturesAsync() {
                    rtAutomator.gesturesAsync(_.toStrokes(arguments));
                },
                scrollDown(index) {
                    if (typeof index === 'number') {
                        return rtAutomator.scrollForward(index);
                    }
                    if (arguments.length === 0) {
                        return rtAutomator.scrollMaxForward();
                    }

                    // @Comment by SuperMonster003 on Apr 20, 2022.
                    //  ! Method runtime.automator.scrollForward() should be invoked with number rather than ActionTarget.
                    //  ! Thus, there is a strong possibility that performAction() won't work properly as expected.

                    // return _.performAction(function (target) {
                    //     return runtime.automator.scrollForward(target);
                    // }, arguments);
                },
                scrollUp(index) {
                    if (typeof index === 'number') {
                        return rtAutomator.scrollBackward(index);
                    }
                    if (arguments.length === 0) {
                        return rtAutomator.scrollMaxBackward();
                    }

                    // @Comment by SuperMonster003 on Apr 20, 2022.
                    //  ! Method runtime.automator.scrollBackward() should be invoked with number rather than ActionTarget.
                    //  ! Thus, there is a strong possibility that performAction() won't work properly as expected.

                    // return _.performAction(function (target) {
                    //     return runtime.automator.scrollBackward(target);
                    // }, arguments);
                },
                setText() {
                    if (arguments.length === 2) {
                        let [ index, text ] = arguments;
                        return rtAutomator.setText(rtAutomator.editable(index), text);
                    } else {
                        let [ text ] = arguments;
                        return rtAutomator.setText(rtAutomator.editable(-1), text);
                    }
                },
                captureScreen() {
                    return ResultAdapter.wait(rtAutomator.captureScreen());
                },
                lockScreen() {
                    return rtAutomator.lockScreen();
                },
                takeScreenshot() {
                    return rtAutomator.takeScreenshot();
                },
                headsethook() {
                    return rtAutomator.headsethook();
                },
                accessibilityButton() {
                    return rtAutomator.accessibilityButton();
                },
                accessibilityButtonChooser() {
                    return rtAutomator.accessibilityButtonChooser();
                },
                accessibilityShortcut() {
                    return rtAutomator.accessibilityShortcut();
                },
                accessibilityAllApps() {
                    return rtAutomator.accessibilityAllApps();
                },
                dismissNotificationShade() {
                    return rtAutomator.dismissNotificationShade();
                },
            };

            return Automator;
        })(),
        modes: {
            normal: AccessibilityBridge.MODE_NORMAL,
            fast: AccessibilityBridge.MODE_FAST,
        },
        flagsMap: {
            findOnUiThread: AccessibilityBridge.FLAG_FIND_ON_UI_THREAD,
            useUsageStats: AccessibilityBridge.FLAG_USE_USAGE_STATS,
            useShell: AccessibilityBridge.FLAG_USE_SHELL,
        },
        /**
         * @template {boolean} T
         * @param {(target: org.autojs.autojs.core.automator.action.ActionTarget) => T} action
         * @param {IArguments} args
         * @return {T}
         */
        performAction(action, args) {
            if (args.length === 4) {
                let [ left, top, right, bottom ] = args;
                return action(rtAutomator.bounds(left, top, right, bottom));
            }
            if (args.length === 2) {
                let [ text, index ] = args;
                return action(rtAutomator.text(text, index));
            }
            let [ text ] = args;
            return action(rtAutomator.text(text, -1));
        },
        toStrokes(argsList) {
            let screenMetrics = scriptRuntime.getScreenMetrics();
            let strokes = java.lang.reflect.Array.newInstance(GestureDescription.StrokeDescription, argsList.length);

            for (let i = 0; i < argsList.length; i += 1) {
                let args = argsList[i];
                let startTime, durationIndex, pointsIndex;
                if (typeof args[1] /* duration */ === 'number') {
                    /* arguments: [startTime, duration, points[]] */
                    startTime = args[0];
                    durationIndex = 1;
                    pointsIndex = 2;
                } else {
                    /* arguments: [duration, points[]] */
                    startTime = 0; // default value
                    durationIndex = 0;
                    pointsIndex = 1;
                }
                let path = new Path();
                let [ x, y ] = args[pointsIndex];
                path.moveTo(screenMetrics.scaleX(x), screenMetrics.scaleY(y));
                for (let j = pointsIndex + 1; j < args.length; j += 1) {
                    let [ x, y ] = args[j];
                    path.lineTo(screenMetrics.scaleX(x), screenMetrics.scaleY(y));
                }
                strokes[i] = new GestureDescription.StrokeDescription(path, startTime, args[durationIndex]);
            }

            return strokes;
        },
        /**
         * @param {any} num
         * @param {number|function():number} [def=0]
         * @returns {number}
         */
        parseNumber(num, def) {
            return typeof num === 'number' ? num : typeof def === 'function' ? def() : def || 0;
        },
        scopeAugment() {
            /**
             * @type {(keyof Internal.Automator)[]}
             */
            let methods = [
                'click', 'longClick', 'press', 'swipe',
                'gesture', 'gestures', 'gestureAsync', 'gesturesAsync',
                'scrollDown', 'scrollUp', 'input', 'setText',
            ];
            __asGlobal__(automator, methods, scope);

            /**
             * @type {(keyof org.autojs.autojs.core.accessibility.SimpleActionAutomator)[]}
             */
            let methodsRt = [
                'back', 'home', 'powerDialog', 'notifications',
                'quickSettings', 'recents', 'splitScreen',
            ];
            __asGlobal__(rtAutomator, methodsRt, scope);

            /**
             * @Caution by SuperMonster003 on Apr 23, 2022.
             * Use 'bind' or 'assign' will lose appended properties.
             *
             * @example
             * let f = function () {}; f.code = 1;
             * let g = f; console.log(g.code); // 1
             * let h = f.bind({}); console.log(h.code); // undefined
             * let o = {}; Object.assign(o, {f}); console.log(o.code); // undefined
             */
            scope.auto = auto;
        },
    };

    /**
     * @type {Internal.Auto}
     */
    const auto = new _.Auto();

    /**
     * @type {Internal.Automator}
     */
    const automator = new _.Automator();

    _.scopeAugment();

    return automator;
};