// noinspection JSUnusedGlobalSymbols

const ResultAdapter = require('result-adapter');

/* Here, importClass() is not recommended for intelligent code completion in IDE like WebStorm. */
/* The same is true of destructuring assignment syntax (like `let {Uri} = android.net`). */

const Path = android.graphics.Path;
const UiObject = com.stardust.automator.UiObject;
const GestureDescription = android.accessibilityservice.GestureDescription;
const AccessibilityBridge = com.stardust.autojs.core.accessibility.AccessibilityBridge;

let _ = {
    modes: {
        normal: AccessibilityBridge.MODE_NORMAL,
        fast: AccessibilityBridge.MODE_FAST,
    },
    flagsMap: {
        findOnUiThread: AccessibilityBridge.FLAG_FIND_ON_UI_THREAD,
        useUsageStats: AccessibilityBridge.FLAG_USE_USAGE_STATS,
        useShell: AccessibilityBridge.FLAG_USE_SHELL,
    },
    init(__runtime__, scope) {
        this.runtime = __runtime__;
        this.scope = scope;

        /**
         * @type {Internal.Automator | {}}
         */
        this.automator = {};
        this.auto = (mode) => {
            if (typeof mode === 'string') {
                auto.setMode(mode);
            }
            _.a11yBridge.ensureServiceEnabled();
        };

        /**
         * @type {com.stardust.autojs.core.accessibility.SimpleActionAutomator}
         */
        this.rtAutomator = __runtime__.automator;
        /**
         * @type {com.stardust.autojs.core.accessibility.AccessibilityBridge}
         */
        this.a11yBridge = __runtime__.accessibilityBridge;
    },
    getModule() {
        return this.automator;
    },
    selfAugment() {
        Object.assign(this.automator, {
            press: this.rtAutomator.press.bind(this.rtAutomator),
            gesture: this.rtAutomator.gesture.bind(this.rtAutomator, 0),
            gestureAsync: this.rtAutomator.gestureAsync.bind(this.rtAutomator, 0),
            swipe: this.rtAutomator.swipe.bind(this.rtAutomator),
            isServiceEnabled: this.rtAutomator.isServiceEnabled.bind(this.rtAutomator),
            ensureService: this.rtAutomator.ensureService.bind(this.rtAutomator),
            lockScreen: this.rtAutomator.lockScreen.bind(this.rtAutomator),
            takeScreenshot: this.rtAutomator.takeScreenshot.bind(this.rtAutomator),
            headsethook: this.rtAutomator.headsethook.bind(this.rtAutomator),
            accessibilityButton: this.rtAutomator.accessibilityButton.bind(this.rtAutomator),
            accessibilityButtonChooser: this.rtAutomator.accessibilityButtonChooser.bind(this.rtAutomator),
            accessibilityShortcut: this.rtAutomator.accessibilityShortcut.bind(this.rtAutomator),
            accessibilityAllApps: this.rtAutomator.accessibilityAllApps.bind(this.rtAutomator),
            dismissNotificationShade: this.rtAutomator.dismissNotificationShade.bind(this.rtAutomator),
            waitForService(timeout) {
                _.a11yBridge.waitForServiceEnabled(_.parseNumber(timeout, -1));
            },
            click() {
                if (arguments.length === 2) {
                    let [x, y] = arguments;
                    if (typeof x === 'number' && typeof y === 'number') {
                        return _.rtAutomator.click(x, y);
                    }
                }
                return _.performAction(function (target) {
                    return _.rtAutomator.click(target);
                }, arguments);
            },
            longClick() {
                if (arguments.length === 2) {
                    let [x, y] = arguments;
                    if (typeof x === 'number' && typeof y === 'number') {
                        return _.rtAutomator.longClick(x, y);
                    }
                }
                return _.performAction(function (target) {
                    return _.rtAutomator.longClick(target);
                }, arguments);
            },
            input() {
                if (arguments.length === 2) {
                    let [index, text] = arguments;
                    return _.rtAutomator.appendText(_.rtAutomator.editable(index), text);
                } else {
                    let [text] = arguments;
                    return _.rtAutomator.appendText(_.rtAutomator.editable(-1), text);
                }
            },
            gestures() {
                return _.rtAutomator.gestures(_.toStrokes(arguments));
            },
            gesturesAsync() {
                _.rtAutomator.gesturesAsync(_.toStrokes(arguments));
            },
            scrollDown(index) {
                if (typeof index === 'number') {
                    return _.rtAutomator.scrollForward(index);
                }
                if (arguments.length === 0) {
                    return _.rtAutomator.scrollMaxForward();
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
                    return _.rtAutomator.scrollBackward(index);
                }
                if (arguments.length === 0) {
                    return _.rtAutomator.scrollMaxBackward();
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
                    let [index, text] = arguments;
                    return _.rtAutomator.setText(_.rtAutomator.editable(index), text);
                } else {
                    let [text] = arguments;
                    return _.rtAutomator.setText(_.rtAutomator.editable(-1), text);
                }
            },
            captureScreen() {
                return ResultAdapter.wait(_.rtAutomator.captureScreen());
            },
        });

        Object.assign(this.auto, {
            get service() {
                return _.a11yBridge.getService();
            },
            get windows() {
                return this.service === null ? [] : util.java.toJsArray(this.service.getWindows(), true);
            },
            get root() {
                let root = _.a11yBridge.getRootInCurrentWindow();
                return root ? UiObject.Companion.createRoot(root) : null;
            },
            get rootInActiveWindow() {
                let root = _.a11yBridge.getRootInActiveWindow();
                return root ? UiObject.Companion.createRoot(root) : null;
            },
            get windowRoots() {
                return util.java.toJsArray(_.a11yBridge.windowRoots(), false)
                    .map(root => UiObject.Companion.createRoot(root));
            },
            waitFor(timeout) {
                _.automator.waitForService(timeout);
            },
            setMode(modeStr) {
                if (typeof modeStr !== 'string') {
                    throw TypeError('Mode should be a string for auto.setMode()');
                }
                let mode = _.modes[modeStr];
                if (mode === undefined) {
                    throw Error(`Unknown mode for auto.setMode(): ${modeStr}`);
                }
                _.a11yBridge.setMode(mode);
            },
            setFlags(flags) {
                let flagStrings;
                if (Array.isArray(flags)) {
                    flagStrings = flags;
                } else if (typeof flags === 'string') {
                    flagStrings = [flags];
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
                _.a11yBridge.setFlags(flagsInt);
            },
            setWindowFilter(filter) {
                _.a11yBridge.setWindowFilter(new AccessibilityBridge.WindowFilter(filter));
            },
        });
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
        __asGlobal__(this.automator, methods);

        /**
         * @type {(keyof com.stardust.autojs.core.accessibility.SimpleActionAutomator)[]}
         */
        let methodsRt = [
            'back', 'home', 'powerDialog', 'notifications',
            'quickSettings', 'recents', 'splitScreen',
        ];
        __asGlobal__(this.rtAutomator, methodsRt);

        /**
         * @Caution by SuperMonster003 on Apr 23, 2022.
         * Bind "this" will make bound function lose appended properties.
         *
         * @example
         * let f = function () {}; f.code = 1;
         * let g = f; console.log(g.code); // 1
         * let h = f.bind({}); console.log(h.code); // undefined
         */
        this.scope.auto = this.auto;
    },
    /**
     * @template {boolean} T
     * @param {function(target: com.stardust.automator.simple_action.ActionTarget): T} action
     * @param {IArguments} args
     * @return {T}
     */
    performAction(action, args) {
        if (args.length === 4) {
            let [left, top, right, bottom] = args;
            return action(_.rtAutomator.bounds(left, top, right, bottom));
        }
        if (args.length === 2) {
            let [text, index] = args;
            return action(_.rtAutomator.text(text, index));
        }
        let [text] = args;
        return action(_.rtAutomator.text(text, -1));
    },
    toStrokes(argsList) {
        let screenMetrics = this.runtime.getScreenMetrics();
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
            let [x, y] = args[pointsIndex];
            path.moveTo(screenMetrics.scaleX(x), screenMetrics.scaleY(y));
            for (let j = pointsIndex + 1; j < args.length; j += 1) {
                let [x, y] = args[j];
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
};

let $ = {
    getModule(__runtime__, scope) {
        _.init(__runtime__, scope);

        _.selfAugment();
        _.scopeAugment();

        return _.getModule();
    },
};

/**
 * @param {com.stardust.autojs.runtime.ScriptRuntime} __runtime__
 * @param {org.mozilla.javascript.Scriptable} scope
 * @return {Internal.Automator}
 */
module.exports = function (__runtime__, scope) {
    return $.getModule(__runtime__, scope);
};