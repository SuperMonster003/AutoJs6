// Type definitions for AutoJs6 internal module automator
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 5.1.3
//
// Last modified: Jun 14, 2026

/// <reference path="../index.d.ts" />

/**
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/automator/Auto.kt
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/automator/Automator.kt
 */

import StrokeParams = Automator.StrokeParams;
import GestureResultCallbackLike = Automator.GestureResultCallbackLike;

declare namespace Internal {

    interface Automator {

        /**
         * @example
         * automator.click(360, 540);
         * @example Source code summary (zh-CN: 源代码摘要)
         * return runtime.automator.click(x, y);
         */
        click(x: number, y: number): boolean;
        click(point: [x: number, y: number]): boolean;
        click(point: {x: number, y: number}): boolean;
        click(point: android.graphics.Point): boolean;
        click(point: org.opencv.core.Point): boolean;

        /**
         * @param text
         * @param [index=0]
         * @example
         * // all "OK" clickable widgets will be clicked
         * automator.click("OK");
         * @example
         * // only the first "OK" clickable widget be clicked
         * automator.click("OK", 0);
         * @example Source code summary (zh-CN: 源代码摘要)
         * return runtime.automator.click(runtime.automator.text(...args));
         */
        click(text: string, index?: number): boolean;

        /**
         * @example
         * automator.click(0, 0, 300, 500);
         * @example Source code summary (zh-CN: 源代码摘要)
         * return runtime.automator.click(runtime.automator.bounds(...args));
         */
        click(left: number, top: number, right: number, bottom: number): boolean;

        click(widget: UiObject): boolean;

        click(bounds: android.graphics.Rect): boolean;

        /**
         * @example
         * automator.longClick(360, 540);
         * @example Source code summary (zh-CN: 源代码摘要)
         * return runtime.automator.longClick(x, y);
         */
        longClick(x: number, y: number): boolean;
        longClick(point: [x: number, y: number]): boolean;
        longClick(point: {x: number, y: number}): boolean;
        longClick(point: android.graphics.Point): boolean;
        longClick(point: org.opencv.core.Point): boolean;

        /**
         * @param text
         * @param [index=0]
         * @example
         * // all "OK" long clickable widgets will be long clicked
         * automator.longClick("OK");
         * @example
         * // only the first "OK" long clickable widget be long clicked
         * automator.longClick("OK", 0);
         * @example Source code summary (zh-CN: 源代码摘要)
         * return runtime.automator.longClick(runtime.automator.text(...args));
         */
        longClick(text: string, index?: number): boolean;

        /**
         * @example
         * automator.longClick(0, 0, 300, 500);
         * @example Source code summary (zh-CN: 源代码摘要)
         * return runtime.automator.longClick(runtime.automator.bounds(...args));
         */
        longClick(left: number, top: number, right: number, bottom: number): boolean;

        longClick(widget: UiObject): boolean;

        longClick(bounds: android.graphics.Rect): boolean;

        /**
         * @Legacy
         *
         * @example
         * for (let i = 0; i < 100; i++) {
         *     press(500, 1000, 1);
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * runtime.automator.press.bind(runtime.automator);
         *
         * @param x
         * @param y
         * @param [duration=ViewConfiguration.getTapTimeout()]
         */
        press(x: number, y: number, duration?: number): boolean;
        /**
         * @param point
         * @param [duration=ViewConfiguration.getTapTimeout()]
         */
        press(point: [x: number, y: number], duration?: number): boolean;
        /** @Recommended */
        press(duration: number, point: [x: number, y: number]): boolean;

        /**
         * @Legacy
         *
         * @example
         * automator.swipe(540, 600, 540, 200, 500);
         * @example Source code summary (zh-CN: 源代码摘要)
         * runtime.automator.swipe.bind(runtime.automator);
         * @example Source code summary (zh-CN: 源代码摘要)
         * gesture(0, duration, intArrayOf(x1, y1), intArrayOf(x2, y2));
         */
        swipe(x1: number, y1: number, x2: number, y2: number, duration: number): boolean;
        swipe(pointA: [x: number, y: number], pointB: [x: number, y: number], duration: number): boolean;
        swipe(pointsGroup: [[x: number, y: number], [x: number, y: number]], duration: number): boolean;
        swipe(points: [x1: number, y1: number, x2: number, y2: number], duration: number): boolean;
        /** @Recommended */
        swipe(duration: number, pointA: [x: number, y: number], pointB: [x: number, y: number]): boolean;
        /** @Recommended */
        swipe(duration: number, pointsGroup: [[x: number, y: number], [x: number, y: number]]): boolean;
        swipe(duration: number, points: [x1: number, y1: number, x2: number, y2: number]): boolean;

        /**
         * @Legacy
         * @Recommended
         *
         * @example
         * automator.gesture(1000, [0, 0], [500, 500], [500, 1000]);
         * @example Source code summary (zh-CN: 源代码摘要)
         * runtime.automator.gesture.bind(runtime.automator, 0);
         */
        gesture(duration: number, ...point: [x: number, y: number][]): boolean;
        /** @Recommended */
        gesture(duration: number, pointsGroup: [...[x: number, y: number][]]): boolean;
        gesture(duration: number, points: [...number[]]): boolean;

        gestureAsync(duration: number, point: [x: number, y: number], callback: GestureResultCallbackLike): void;
        gestureAsync(duration: number, pointA: [x: number, y: number], pointB: [x: number, y: number], callback: GestureResultCallbackLike): void;
        gestureAsync(duration: number, pointA: [x: number, y: number], pointB: [x: number, y: number], pointC: [x: number, y: number], callback: GestureResultCallbackLike): void;
        gestureAsync(duration: number, pointA: [x: number, y: number], pointB: [x: number, y: number], pointC: [x: number, y: number], pointD: [x: number, y: number], callback: GestureResultCallbackLike): void;
        gestureAsync(duration: number, pointA: [x: number, y: number], pointB: [x: number, y: number], pointC: [x: number, y: number], pointD: [x: number, y: number], pointE: [x: number, y: number], callback: GestureResultCallbackLike): void;
        gestureAsync(duration: number, pointA: [x: number, y: number], pointB: [x: number, y: number], pointC: [x: number, y: number], pointD: [x: number, y: number], pointE: [x: number, y: number], pointF: [x: number, y: number], callback: GestureResultCallbackLike): void;
        gestureAsync(duration: number, pointA: [x: number, y: number], pointB: [x: number, y: number], pointC: [x: number, y: number], pointD: [x: number, y: number], pointE: [x: number, y: number], pointF: [x: number, y: number], pointG: [x: number, y: number], callback: GestureResultCallbackLike): void;
        gestureAsync(duration: number, pointA: [x: number, y: number], pointB: [x: number, y: number], pointC: [x: number, y: number], pointD: [x: number, y: number], pointE: [x: number, y: number], pointF: [x: number, y: number], pointG: [x: number, y: number], pointH: [x: number, y: number], callback: GestureResultCallbackLike): void;
        gestureAsync(duration: number, pointA: [x: number, y: number], pointB: [x: number, y: number], pointC: [x: number, y: number], pointD: [x: number, y: number], pointE: [x: number, y: number], pointF: [x: number, y: number], pointG: [x: number, y: number], pointH: [x: number, y: number], pointI: [x: number, y: number], callback: GestureResultCallbackLike): void;
        /** @Recommended */
        gestureAsync(duration: number, pointsGroup: [...[x: number, y: number][]], callback?: GestureResultCallbackLike): void;
        gestureAsync(duration: number, points: [...number[]], callback?: GestureResultCallbackLike): void;
        /**
         * @Legacy
         * @Recommended
         *
         * @example
         * type StrokeParams =
         * automator.gestureAsync(1000, [0, 0], [500, 500], [500, 1000]);
         * @example Source code summary (zh-CN: 源代码摘要)
         * runtime.automator.gestureAsync.bind(runtime.automator, 0);
         */
        gestureAsync(duration: number, ...point: [x: number, y: number][]): void;

        /**
         * @example
         * automator.gestures(
         *     [0, 500, [800, 300], [500, 1000]],
         *     [0, 500, [300, 1500], [500, 1000]]
         * );
         * @example Source code summary (zh-CN: 源代码摘要)
         * return runtime.automator.gestures(toStrokes(arguments));
         *
         * function toStrokes(args) {
         *     let screenMetrics = runtime.getScreenMetrics();
         *     let len = args.length;
         *     let strokes = java.lang.reflect.Array.newInstance(android.accessibilityservice.GestureDescription.StrokeDescription, len);
         *     for (let i = 0; i < len; i++) {
         *         let gesture = args[i];
         *         let pointsIndex = 1;
         *         let start, duration;
         *         if (typeof (gesture[1]) == 'number') {
         *             start = gesture[0];
         *             duration = gesture[1];
         *             pointsIndex = 2;
         *         } else {
         *             start = 0;
         *             duration = gesture[0];
         *         }
         *         let gestureLen = gesture.length;
         *         let path = new android.graphics.Path();
         *         path.moveTo(screenMetrics.scaleX(gesture[pointsIndex][0]), screenMetrics.scaleY(gesture[pointsIndex][1]));
         *         for (let j = pointsIndex + 1; j < gestureLen; j++) {
         *             path.lineTo(screenMetrics.scaleX(gesture[j][0]), screenMetrics.scaleY(gesture[j][1]));
         *         }
         *         strokes[i] = new android.accessibilityservice.GestureDescription.StrokeDescription(path, start, duration);
         *     }
         *     return strokes;
         * }
         */
        gestures(...stroke: StrokeParams[]): boolean;

        gesturesAsync(stroke: StrokeParams, callback: GestureResultCallbackLike): void;
        gesturesAsync(strokeA: StrokeParams, strokeB: StrokeParams, callback: GestureResultCallbackLike): void;
        gesturesAsync(strokeA: StrokeParams, strokeB: StrokeParams, strokeC: StrokeParams, callback: GestureResultCallbackLike): void;
        gesturesAsync(strokeA: StrokeParams, strokeB: StrokeParams, strokeC: StrokeParams, strokeD: StrokeParams, callback: GestureResultCallbackLike): void;
        gesturesAsync(strokeA: StrokeParams, strokeB: StrokeParams, strokeC: StrokeParams, strokeD: StrokeParams, strokeE: StrokeParams, callback: GestureResultCallbackLike): void;
        gesturesAsync(strokeA: StrokeParams, strokeB: StrokeParams, strokeC: StrokeParams, strokeD: StrokeParams, strokeE: StrokeParams, strokeF: StrokeParams, callback: GestureResultCallbackLike): void;
        gesturesAsync(strokeA: StrokeParams, strokeB: StrokeParams, strokeC: StrokeParams, strokeD: StrokeParams, strokeE: StrokeParams, strokeF: StrokeParams, strokeG: StrokeParams, callback: GestureResultCallbackLike): void;
        gesturesAsync(strokeA: StrokeParams, strokeB: StrokeParams, strokeC: StrokeParams, strokeD: StrokeParams, strokeE: StrokeParams, strokeF: StrokeParams, strokeG: StrokeParams, strokeH: StrokeParams, callback: GestureResultCallbackLike): void;
        gesturesAsync(strokeA: StrokeParams, strokeB: StrokeParams, strokeC: StrokeParams, strokeD: StrokeParams, strokeE: StrokeParams, strokeF: StrokeParams, strokeG: StrokeParams, strokeH: StrokeParams, strokeI: StrokeParams, callback: GestureResultCallbackLike): void;
        /** @Recommended */
        gesturesAsync(strokesGroup: [...StrokeParams[]], callback?: GestureResultCallbackLike): void;
        /**
         * @Legacy
         * @Recommended
         *
         * @example
         * automator.gesturesAsync(
         *     [0, 500, [800, 300], [500, 1000]],
         *     [0, 500, [300, 1500], [500, 1000]]
         * );
         * @example Source code summary (zh-CN: 源代码摘要)
         * runtime.automator.gesturesAsync(toStrokes(arguments));
         *
         * function toStrokes(args) {
         *     let screenMetrics = runtime.getScreenMetrics();
         *     let len = args.length;
         *     let strokes = java.lang.reflect.Array.newInstance(android.accessibilityservice.GestureDescription.StrokeDescription, len);
         *     for (let i = 0; i < len; i++) {
         *         let gesture = args[i];
         *         let pointsIndex = 1;
         *         let start, duration;
         *         if (typeof (gesture[1]) == 'number') {
         *             start = gesture[0];
         *             duration = gesture[1];
         *             pointsIndex = 2;
         *         } else {
         *             start = 0;
         *             duration = gesture[0];
         *         }
         *         let gestureLen = gesture.length;
         *         let path = new android.graphics.Path();
         *         path.moveTo(screenMetrics.scaleX(gesture[pointsIndex][0]), screenMetrics.scaleY(gesture[pointsIndex][1]));
         *         for (let j = pointsIndex + 1; j < gestureLen; j++) {
         *             path.lineTo(screenMetrics.scaleX(gesture[j][0]), screenMetrics.scaleY(gesture[j][1]));
         *         }
         *         strokes[i] = new android.accessibilityservice.GestureDescription.StrokeDescription(path, start, duration);
         *     }
         *     return strokes;
         * }
         */
        gesturesAsync(...stroke: StrokeParams[]): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * runtime.automator.scrollMaxForward();
         */
        scrollDown(): boolean;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * runtime.automator.scrollForward(index);
         */
        scrollDown(index: number): boolean;

        /**
         * @param text
         * @param [index=0]
         * @example Source code summary (zh-CN: 源代码摘要)
         * return runtime.automator.scrollForward(runtime.automator.text(...args));
         */
        scrollDown(text: string, index?: number): boolean;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * return runtime.automator.scrollDown(runtime.automator.bounds(...args));
         */
        scrollDown(left: number, top: number, right: number, bottom: number): boolean;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * runtime.automator.scrollMaxBackward();
         */
        scrollUp(): boolean;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * runtime.automator.scrollBackward(index);
         */
        scrollUp(index: number): boolean;

        /**
         * @param text
         * @param [index=0]
         * @example Source code summary (zh-CN: 源代码摘要)
         * return runtime.automator.scrollBackward(runtime.automator.text(...args));
         */
        scrollUp(text: string, index?: number): boolean;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * return runtime.automator.scrollUp(runtime.automator.bounds(...args));
         */
        scrollUp(left: number, top: number, right: number, bottom: number): boolean;

        /**
         * Replace old text with new one
         * @example
         * automator.setText("test");
         * @example Source code summary (zh-CN: 源代码摘要)
         * runtime.automator.setText(runtime.automator.editable(-1), text);
         */
        setText(text: string): boolean;

        /**
         * Replace old text with new one
         * @example
         * automator.setText(2, "test");
         * @example Source code summary (zh-CN: 源代码摘要)
         * runtime.automator.setText(runtime.automator.editable(index), text);
         */
        setText(index: number, text: string): boolean;

        /**
         * Append text to old text
         * @example
         * automator.input("test");
         * @example Source code summary (zh-CN: 源代码摘要)
         * runtime.automator.appendText(runtime.automator.editable(-1), text);
         */
        input(text: string): boolean;

        /**
         * Append text to old text
         * @example
         * automator.input(2, "test");
         * @example Source code summary (zh-CN: 源代码摘要)
         * runtime.automator.appendText(runtime.automator.editable(index), text);
         */
        input(index: number, text: string): boolean;

        captureScreen(): ImageWrapper;

        isServiceRunning(): boolean;

        ensureService(): void;

        waitForService(timeout?: number): void;

        // @RequiresApi(28)
        lockScreen(): boolean;

        // @RequiresApi(28)
        takeScreenshot(): boolean;

        // @RequiresApi(30)
        headsethook(): boolean;

        // @RequiresApi(30)
        accessibilityButton(): boolean;

        // @RequiresApi(30)
        accessibilityButtonChooser(): boolean;

        // @RequiresApi(30)
        accessibilityShortcut(): boolean;

        // @RequiresApi(30)
        accessibilityAllApps(): boolean;

        // @RequiresApi(30)
        dismissNotificationShade(): boolean;

        back(): boolean;

        home(): boolean;

        powerDialog(): boolean;

        notifications(): boolean;

        quickSettings(): boolean;

        recents(): boolean;

        splitScreen(): boolean;

    }

    interface Auto {
        /**
         * Set accessibility (aka a11y) service mode and make sure it's enabled.
         * An exception will be thrown and a11y configuration page will be prompted without the service running normally.
         * zh-CN: 设置无障碍模式并确保无障碍服务启动; 如果无障碍服务未启用, 则抛出异常并跳转到无障碍服务启用界面
         * @param [mode="normal"]
         * @param [isForcibleRestart=false]
         * @example
         * // normal mode (default)
         * // zh-CN: 正常模式 (默认)
         * auto();
         * auto("normal"); // same as above
         *
         * // fast mode
         * // inspect screen layout at a faster speed in this mode
         * // zh-CN: 快速模式
         * // 该模式下会启用控件缓存, 从而选择器获取屏幕控件更快
         * // 对于需要快速的控件操作的脚本可以使用该模式, 一般脚本则没有必要使用该函数
         * auto("fast");
         *
         * // the additional
         * // auto.waitFor() and auto.setMode() are recommended,
         * // as auto() may interrupt the current script
         * // zh-CN: 附加说明
         * // 建议使用 auto.waitFor() 和 auto.setMode() 代替该方法
         * // 若无障碍服务未启动, 使用 auto() 后脚本会立即停止运行
         * // 而 auto.waitFor() 在无障碍服务启动后使脚本继续运行
         * @example Source code summary (zh-CN: 源代码摘要)
         * if (mode) {
         *    global.auto.setMode(mode);
         * }
         * runtime.accessibilityBridge.ensureServiceEnabled();
         * @see auto.waitFor
         * @see ensureServiceStarted
         */
        (mode?: Automator.Mode, isForcibleRestart?: boolean): void;
        (isForcibleRestart: boolean): void;
        (mode: Automator.Mode | null, isForcibleRestart: boolean): void;

        start(): boolean;

        enable(): boolean;

        stop(): boolean;

        disable(): boolean;

        hasInstance(): boolean;

        hasService(): boolean;

        exists(): boolean;

        isRunning(): boolean;

        isOperational(): boolean;

        stateListener(listener?: Automator.StateListener | null): void;

        registerEvent(name: string, listener?: Automator.AccessibilityEventListener | null): void;

        registerEvents(name: string, listener?: Automator.AccessibilityEventListener | null): void;

        removeEvent(name: string): void;

        removeEvents(name: string): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * return runtime.accessibilityBridge.getService();
         * @see org.autojs.autojs.core.accessibility.AccessibilityBridge.getService
         */
        get service(): org.autojs.autojs.core.accessibility.AccessibilityService | null;

        get services(): string[];

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * let service = auto.service;
         * return service === null ? [] : util.java.toJsArray(service.getWindows(), true);
         * @see auto.service
         * @see android.accessibilityservice.AccessibilityService.getWindows
         */
        get windows(): android.view.accessibility.AccessibilityWindowInfo[];

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * let root = runtime.accessibilityBridge.getRootInCurrentWindow(); // may be null
         * return root && org.autojs.autojs.core.automator.UiObject.Companion.createRoot(root);
         * @see org.autojs.autojs.core.automator.UiObject.Companion.createRoot
         */
        get root(): UiObject | null;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * let root = runtime.accessibilityBridge.getRootInActiveWindow();
         * return root && org.autojs.autojs.core.automator.UiObject.Companion.createRoot(root);
         * @see org.autojs.autojs.core.automator.UiObject.Companion.createRoot
         */
        get rootInActiveWindow(): UiObject | null;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * return util.java.toJsArray(runtime.accessibilityBridge.windowRoots(), false)
         *     .map(root => org.autojs.autojs.core.automator.UiObject.Companion.createRoot(root));
         * @see org.autojs.autojs.core.accessibility.AccessibilityBridge.windowRoots
         * @see org.autojs.autojs.core.automator.UiObject.Companion.createRoot
         */
        get windowRoots(): UiObject[];

        get state(): Automator.AutoState;

        /**
         * @example
         * auto.waitFor();
         * @example Source code summary (zh-CN: 源代码摘要)
         * runtime.accessibilityBridge.waitForServiceEnabled();
         * @see org.autojs.autojs.core.accessibility.AccessibilityBridge.waitForServiceEnabled
         */
        waitFor(timeout?: number | null): void;

        /**
         * @example
         * auto.setMode("normal");
         * @example
         * auto.setMode("fast");
         * @example Source code summary (zh-CN: 源代码摘要)
         * runtime.accessibilityBridge.setMode({"normal": 0, "fast": 1}[modeStr]);
         * @see org.autojs.autojs.core.accessibility.AccessibilityBridge.setMode
         */
        setMode(modeStr: Automator.Mode): void;

        /**
         * @example
         * auto.setFlags("useUsageStats");
         * @example
         * auto.setFlags(["useShell", "useUsageStats", "findOnUiThread"]);
         * @example Source code summary (zh-CN: 源代码摘要)
         * const flagsMap = {"findOnUiThread": 1, "useUsageStats": 2, "useShell": 4};
         * let flagsStrings = Array.isArray(flags)) ? flags : [flags];
         * let flagsInt = 0;
         * flagStrings.forEach(str => flagsInt |= flagsMap[str]);
         * runtime.accessibilityBridge.setFlags(flagsInt);
         * @see org.autojs.autojs.core.accessibility.AccessibilityBridge.setFlags
         */
        setFlags(flags: Automator.Flags | Automator.Flags[]): void;

        /**
         * @example
         * auto.setWindowFilter(function (window) {
         *     return window.title === "QQ";
         * });
         * @example Source code summary (zh-CN: 源代码摘要)
         * runtime.accessibilityBridge.setWindowFilter(
         *     new org.autojs.autojs.core.accessibility.AccessibilityBridge.WindowFilter(filter)
         * );
         * @see org.autojs.autojs.core.accessibility.AccessibilityBridge.WindowFilter
         * @see android.view.accessibility.AccessibilityWindowInfo
         */
        setWindowFilter(filter?: Automator.WindowFilterLike | null): void;

        launchSettings(): void;

        clearCache(): boolean;

        currentPackage(): string;

        currentActivity(): string;

        currentComponent(): string;
    }
}

declare namespace Automator {
    type Flags = 'findOnUiThread' | 'useUsageStats' | 'useShell';
    type Mode = 'normal' | 'fast';
    type AccessibilityEvent = org.autojs.autojs.core.automator.AccessibilityEventWrapper;
    type AccessibilityEventListener = org.autojs.autojs.core.accessibility.SimpleActionAutomator.Companion.AccessibilityEventCallback | ((event: AccessibilityEvent) => void) | {
        onAccessibilityEvent(event: AccessibilityEvent): void;
    };
    interface AutoState {
        hasInstance: boolean;
        hasService: boolean;
        isRunning: boolean;
        isOperational: boolean;
    }
    type GestureResultCallbackLike = ((isComplete: boolean) => void) | {
        onCompleted?(gestureDescription: android.accessibilityservice.GestureDescription): void;
        onCancelled?(gestureDescription: android.accessibilityservice.GestureDescription): void;
    }
    type StateListener = org.autojs.autojs.core.accessibility.AccessibilityServiceCallback | {
        onConnected?(): void;
        onDisconnected?(): void;
    };
    type StrokeParams = [startTime: number, duration: number, ...point: [x: number, y: number][]] | [duration: number, ...point: [x: number, y: number][]];
    type WindowFilterLike = boolean | org.autojs.autojs.core.accessibility.AccessibilityBridge.WindowFilter | ((info: android.view.accessibility.AccessibilityWindowInfo) => boolean);
}

/**
 * @see org.autojs.autojs.core.accessibility.SimpleActionAutomator.back
 */
declare function back(): boolean;

/**
 * @see org.autojs.autojs.core.accessibility.SimpleActionAutomator.home
 */
declare function home(): boolean;

/**
 * @see org.autojs.autojs.core.accessibility.SimpleActionAutomator.powerDialog
 */
declare function powerDialog(): boolean;

/**
 * @see org.autojs.autojs.core.accessibility.SimpleActionAutomator.notifications
 */
declare function notifications(): boolean;

/**
 * @see org.autojs.autojs.core.accessibility.SimpleActionAutomator.quickSettings
 */
declare function quickSettings(): boolean;

/**
 * @see org.autojs.autojs.core.accessibility.SimpleActionAutomator.recents
 */
declare function recents(): boolean;

/**
 * @see org.autojs.autojs.core.accessibility.SimpleActionAutomator.splitScreen
 */
declare function splitScreen(): boolean;

declare function click(bounds: android.graphics.Rect): boolean;

declare function click(widget: UiObject): boolean;

/**
 * @param text
 * @param [index=0]
 * @see Internal.Automator.click
 */
declare function click(text: string, index?: number): boolean;

/**
 * @see Internal.Automator.click
 */
declare function click(x: number, y: number): boolean;
declare function click(point: [x: number, y: number]): boolean;
declare function click(point: {x: number, y: number}): boolean;
declare function click(point: android.graphics.Point): boolean;
declare function click(point: org.opencv.core.Point): boolean;

/**
 * @see Internal.Automator.click
 */
declare function click(left: number, top: number, right: number, bottom: number): boolean;

/**
 * @see Internal.Automator.longClick
 */
declare function longClick(x: number, y: number): boolean;
declare function longClick(point: [x: number, y: number]): boolean;
declare function longClick(point: {x: number, y: number}): boolean;
declare function longClick(point: android.graphics.Point): boolean;
declare function longClick(point: org.opencv.core.Point): boolean;
/**
 * @param text
 * @param [index=0]
 * @see Internal.Automator.longClick
 */
declare function longClick(text: string, index?: number): boolean;
declare function longClick(bounds: android.graphics.Rect): boolean;
declare function longClick(widget: UiObject): boolean;
/**
 * @see Internal.Automator.longClick
 */
declare function longClick(left: number, top: number, right: number, bottom: number): boolean;

/**
 * @Legacy
 *
 * @see Internal.Automator.press
 */
declare function press(x: number, y: number, duration?: number): boolean;
declare function press(point: [x: number, y: number], duration?: number): boolean;
/** @Recommended */
declare function press(duration: number, point: [x: number, y: number]): boolean;

/**
 * @Legacy
 *
 * @see Internal.Automator.swipe
 */
declare function swipe(x1: number, y1: number, x2: number, y2: number, duration: number): boolean;
declare function swipe(pointA: [x: number, y: number], pointB: [x: number, y: number], duration: number): boolean;
declare function swipe(pointsGroup: [[x: number, y: number], [x: number, y: number]], duration: number): boolean;
declare function swipe(points: [x1: number, y1: number, x2: number, y2: number], duration: number): boolean;
/** @Recommended */
declare function swipe(duration: number, pointA: [x: number, y: number], pointB: [x: number, y: number]): boolean;
/** @Recommended */
declare function swipe(duration: number, pointsGroup: [[x: number, y: number], [x: number, y: number]]): boolean;
declare function swipe(duration: number, points: [x1: number, y1: number, x2: number, y2: number]): boolean;

/**
 * @Legacy
 * @Recommended
 *
 * @see Internal.Automator.gesture
 */
declare function gesture(duration: number, ...point: [x: number, y: number][]): boolean;
/** @Recommended */
declare function gesture(duration: number, pointsGroup: [...[x: number, y: number][]]): boolean;
declare function gesture(duration: number, points: [...number[]]): boolean;

declare function gestureAsync(duration: number, point: [x: number, y: number], callback: GestureResultCallbackLike): void;
declare function gestureAsync(duration: number, pointA: [x: number, y: number], pointB: [x: number, y: number], callback: GestureResultCallbackLike): void;
declare function gestureAsync(duration: number, pointA: [x: number, y: number], pointB: [x: number, y: number], pointC: [x: number, y: number], callback: GestureResultCallbackLike): void;
declare function gestureAsync(duration: number, pointA: [x: number, y: number], pointB: [x: number, y: number], pointC: [x: number, y: number], pointD: [x: number, y: number], callback: GestureResultCallbackLike): void;
declare function gestureAsync(duration: number, pointA: [x: number, y: number], pointB: [x: number, y: number], pointC: [x: number, y: number], pointD: [x: number, y: number], pointE: [x: number, y: number], callback: GestureResultCallbackLike): void;
declare function gestureAsync(duration: number, pointA: [x: number, y: number], pointB: [x: number, y: number], pointC: [x: number, y: number], pointD: [x: number, y: number], pointE: [x: number, y: number], pointF: [x: number, y: number], callback: GestureResultCallbackLike): void;
declare function gestureAsync(duration: number, pointA: [x: number, y: number], pointB: [x: number, y: number], pointC: [x: number, y: number], pointD: [x: number, y: number], pointE: [x: number, y: number], pointF: [x: number, y: number], pointG: [x: number, y: number], callback: GestureResultCallbackLike): void;
declare function gestureAsync(duration: number, pointA: [x: number, y: number], pointB: [x: number, y: number], pointC: [x: number, y: number], pointD: [x: number, y: number], pointE: [x: number, y: number], pointF: [x: number, y: number], pointG: [x: number, y: number], pointH: [x: number, y: number], callback: GestureResultCallbackLike): void;
declare function gestureAsync(duration: number, pointA: [x: number, y: number], pointB: [x: number, y: number], pointC: [x: number, y: number], pointD: [x: number, y: number], pointE: [x: number, y: number], pointF: [x: number, y: number], pointG: [x: number, y: number], pointH: [x: number, y: number], pointI: [x: number, y: number], callback: GestureResultCallbackLike): void;
/** @Recommended */
declare function gestureAsync(duration: number, pointsGroup: [...[x: number, y: number][]], callback?: GestureResultCallbackLike): void;
declare function gestureAsync(duration: number, points: [...number[]], callback?: GestureResultCallbackLike): void;
/**
 * @Legacy
 * @Recommended
 *
 * @see Internal.Automator.gestureAsync
 */
declare function gestureAsync(duration: number, ...point: [x: number, y: number][]): void;

/**
 * @see Internal.Automator.gestures
 */
declare function gestures(...stroke: StrokeParams[]): boolean;

declare function gesturesAsync(stroke: StrokeParams, callback: GestureResultCallbackLike): void;
declare function gesturesAsync(strokeA: StrokeParams, strokeB: StrokeParams, callback: GestureResultCallbackLike): void;
declare function gesturesAsync(strokeA: StrokeParams, strokeB: StrokeParams, strokeC: StrokeParams, callback: GestureResultCallbackLike): void;
declare function gesturesAsync(strokeA: StrokeParams, strokeB: StrokeParams, strokeC: StrokeParams, strokeD: StrokeParams, callback: GestureResultCallbackLike): void;
declare function gesturesAsync(strokeA: StrokeParams, strokeB: StrokeParams, strokeC: StrokeParams, strokeD: StrokeParams, strokeE: StrokeParams, callback: GestureResultCallbackLike): void;
declare function gesturesAsync(strokeA: StrokeParams, strokeB: StrokeParams, strokeC: StrokeParams, strokeD: StrokeParams, strokeE: StrokeParams, strokeF: StrokeParams, callback: GestureResultCallbackLike): void;
declare function gesturesAsync(strokeA: StrokeParams, strokeB: StrokeParams, strokeC: StrokeParams, strokeD: StrokeParams, strokeE: StrokeParams, strokeF: StrokeParams, strokeG: StrokeParams, callback: GestureResultCallbackLike): void;
declare function gesturesAsync(strokeA: StrokeParams, strokeB: StrokeParams, strokeC: StrokeParams, strokeD: StrokeParams, strokeE: StrokeParams, strokeF: StrokeParams, strokeG: StrokeParams, strokeH: StrokeParams, callback: GestureResultCallbackLike): void;
declare function gesturesAsync(strokeA: StrokeParams, strokeB: StrokeParams, strokeC: StrokeParams, strokeD: StrokeParams, strokeE: StrokeParams, strokeF: StrokeParams, strokeG: StrokeParams, strokeH: StrokeParams, strokeI: StrokeParams, callback: GestureResultCallbackLike): void;
/** @Recommended */
declare function gesturesAsync(strokesGroup: [...StrokeParams[]], callback?: GestureResultCallbackLike): void;
/**
 * @Legacy
 * @Recommended
 *
 * @see Internal.Automator.gesturesAsync
 */
declare function gesturesAsync(...stroke: StrokeParams[]): void;

/**
 * @see Internal.Automator.scrollDown
 */
declare function scrollDown(index?: number): boolean;
/**
 * @param text
 * @param [index=0]
 * @see Internal.Automator.scrollDown
 */
declare function scrollDown(text: string, index?: number): boolean;
/**
 * @see Internal.Automator.scrollDown
 */
declare function scrollDown(left: number, top: number, right: number, bottom: number): boolean;

/**
 * @see Internal.Automator.scrollUp
 */
declare function scrollUp(index?: number): boolean;
/**
 * @param text
 * @param [index=0]
 * @see Internal.Automator.scrollUp
 */
declare function scrollUp(text: string, index?: number): boolean;
/**
 * @see Internal.Automator.scrollUp
 */
declare function scrollUp(left: number, top: number, right: number, bottom: number): boolean;

/**
 * @see Internal.Automator.setText
 */
declare function setText(text: string): boolean;
/**
 * @see Internal.Automator.setText
 */
declare function setText(index: number, text: string): boolean;

/**
 * @see Internal.Automator.input
 */
declare function input(text: string): boolean;
/**
 * @see Internal.Automator.input
 */
declare function input(index: number, text: string): boolean;
