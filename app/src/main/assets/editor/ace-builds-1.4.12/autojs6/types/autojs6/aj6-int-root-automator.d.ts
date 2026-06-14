// Type definitions for AutoJs6 internal module root-automator
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 5.1.3
//
// Last modified: Jun 14, 2026

/// <reference path="../index.d.ts" />

/**
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/automator/RootAutomator.kt
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/automator/RootAutomatorNativeObject.kt
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/core/inputevent/RootAutomator.java
 */

declare namespace Internal {

    class RootAutomator {

        /**
         * @example
         * let ra = new RootAutomator();
         * events.on('exit', function () {
         *     ra.exit();
         * });
         * ra.tap(device.width / 2, device.height / 2);
         * // ... other codes ... //
         * @example Source code summary (zh-CN: 源代码摘要)
         * module.exports = function (__runtime__, scope) {
         *     function RootAutomator(inputDevice, nonBlockingForReady) {
         *         inputDevice = inputDevice === undefined ? null : inputDevice;
         *         this.__ra__ = Object.create(new org.autojs.autojs.core.inputevent
         *             .RootAutomator(scope.context, inputDevice, !nonBlockingForReady));
         *         let methods = ["sendEvent", "touch", "setScreenMetrics", "touchX", "touchY", "sendSync", "sendMtSync", "tap",
         *             "swipe", "press", "longPress", "touchDown", "touchUp", "touchMove", "getDefaultId", "setDefaultId", "exit"];
         *         for (let i = 0; i < methods.length; i++) {
         *             let method = methods[i];
         *             this[method] = this.__ra__[method].bind(this.__ra__);
         *         }
         *         return this;
         *     }
         *     return RootAutomator;
         * };
         */
        constructor(waitForReady?: boolean | number);

        __ra__: __RootAutomator__;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * private int mDefaultId = 0;
         * public int getDefaultId() {
         *     return mDefaultId;
         * }
         */
        public getDefaultId(): number;

        /**
         * public void longPress(int x, int y) throws IOException {
         *     press(x, y, ViewConfiguration.getLongPressTimeout() + 200, getDefaultId());
         * }
         * @see android.view.ViewConfiguration
         * @see org.autojs.autojs.core.inputevent.RootAutomator.getDefaultId
         */
        public longPress(x: number, y: number): void;
        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public void longPress(int x, int y, int id) throws IOException {
         *     press(x, y, ViewConfiguration.getLongPressTimeout() + 200, id);
         * }
         * @see android.view.ViewConfiguration
         */
        public longPress(x: number, y: number, id: number): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public void press(int x, int y, int duration) throws IOException {
         *     press(x, y, duration, getDefaultId());
         * }
         * @see org.autojs.autojs.core.inputevent.RootAutomator.getDefaultId
         */
        public press(x: number, y: number, duration: number): void;
        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public void press(int x, int y, int duration, int id) throws IOException {
         *     touchDown(x, y, id);
         *     sleep(duration);
         *     touchUp(id);
         * }
         */
        public press(x: number, y: number, duration: number, id: number): void;

        public sendEvent(type: number, code: number, value: number): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public void setDefaultId(int defaultId) {
         *     mDefaultId = defaultId;
         * }
         */
        public setDefaultId(defaultId: number): void;

        public setScreenMetrics(width: number, height: number): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * private int mDefaultId = 0;
         * public void swipe(int x1, int y1, int x2, int y2) throws IOException {
         *     swipe(x1, y1, x2, y2, 300, mDefaultId);
         * }
         */
        public swipe(x1: number, y1: number, x2: number, y2: number): void;
        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * private int mDefaultId = 0;
         * public void swipe(int x1, int y1, int x2, int y2, int duration) throws IOException {
         *     swipe(x1, y1, x2, y2, duration, mDefaultId);
         * }
         */
        public swipe(x1: number, y1: number, x2: number, y2: number, duration: number): void;
        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public void swipe(int x1, int y1, int x2, int y2, int duration, int id) throws IOException {
         *     long now = SystemClock.uptimeMillis();
         *     touchDown(x1, y1, id);
         *     long startTime = now;
         *     long endTime = startTime + duration;
         *     while (now < endTime) {
         *         long elapsedTime = now - startTime;
         *         float alpha = (float) elapsedTime / duration;
         *         // lerp: linear interpolation between two points or rotations [ i guess so :) ]
         *         touchMove((int) lerp(x1, x2, alpha), (int) lerp(y1, y2, alpha), id);
         *         now = SystemClock.uptimeMillis();
         *     }
         *     touchUp(id);
         * }
         */
        public swipe(x1: number, y1: number, x2: number, y2: number, duration: number, id: number): void;

        /**
         * @example
         * let ra = new RootAutomator();
         * ra.tap(100, 100);
         * ra.exit();
         * @example Source code summary (zh-CN: 源代码摘要)
         * public void tap(int x, int y) throws IOException {
         *     sendEvent(x, y, mDefaultId);
         * }
         * @see org.autojs.autojs.core.inputevent.RootAutomator.sendEvent
         */
        public tap(x: number, y: number): void;
        /**
         * @example
         * let ra = new RootAutomator();
         * ra.tap(100, 100, 1); // finger 1
         * ra.tap(200, 200, 2); // finger 2
         * ra.exit();
         * @example Source code summary (zh-CN: 源代码摘要)
         * public void tap(int x, int y, int id) throws IOException {
         *     touchDown(x, y, id);
         *     touchUp(id);
         * }
         * @see org.autojs.autojs.core.inputevent.RootAutomator.touchDown
         * @see org.autojs.autojs.core.inputevent.RootAutomator.touchUp
         */
        public tap(x: number, y: number, id: number): void;

        public touch(x: number, y: number): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * private int mDefaultId = 0;
         * public void touchDown(int x, int y) throws IOException {
         *     touchDown(x, y, mDefaultId);
         * }
         */
        public touchDown(x: number, y: number): void;
        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * private Shell mShell;
         * private AtomicInteger mTracingId = new AtomicInteger(1);
         * private SparseIntArray mSlotIdMap = new SparseIntArray();
         * private final Object mReadyLock = new Object();
         * private volatile boolean mReady = false;
         * public void touchDown(int x, int y, int id) throws IOException {
         *     if (mSlotIdMap.size() == 0) {
         *         touchDown0(x, y, id);
         *         return;
         *     }
         *     int slotId = mSlotIdMap.size();
         *     mSlotIdMap.put(id, slotId);
         *     sendEvent(EV_ABS, ABS_MT_SLOT, slotId);
         *     sendEvent(EV_ABS, ABS_MT_TRACKING_ID, mTracingId.getAndIncrement());
         *     sendEvent(EV_ABS, ABS_MT_POSITION_X, scaleX(x));
         *     sendEvent(EV_ABS, ABS_MT_POSITION_Y, scaleY(y));
         *     sendEvent(EV_ABS, ABS_MT_TOUCH_MAJOR, 5);
         *     sendEvent(EV_ABS, ABS_MT_WIDTH_MAJOR, 5);
         *     sendEvent(EV_SYN, SYN_REPORT, 0);
         * }
         * private void touchDown0(int x, int y, int id) throws IOException {
         *     mSlotIdMap.put(id, 0);
         *     sendEvent(EV_ABS, ABS_MT_TRACKING_ID, mTracingId.getAndIncrement());
         *     sendEvent(EV_KEY, BTN_TOUCH, DOWN);
         *     //sendEvent(EV_KEY, BTN_TOOL_FINGER, 0x00000001);
         *     sendEvent(EV_ABS, ABS_MT_POSITION_X, scaleX(x));
         *     sendEvent(EV_ABS, ABS_MT_POSITION_Y, scaleY(y));
         *     //sendEvent(EV_ABS, ABS_MT_PRESSURE, 200);
         *     sendEvent(EV_ABS, ABS_MT_TOUCH_MAJOR, 5);
         *     sendEvent(EV_ABS, ABS_MT_WIDTH_MAJOR, 5);
         *     sendEvent(EV_SYN, SYN_REPORT, 0);
         * }
         * public void sendEvent(int type, int code, int value) throws IOException {
         *     waitForReady();
         *     sendEventInternal(type, code, value);
         * }
         * private void sendEventInternal(int type, int code, int value) {
         *     mShell.exec(type + " " + code + " " + value);
         * }
         * private void waitForReady() throws IOException {
         *     if (mReady) {
         *         return;
         *     }
         *     synchronized (mReadyLock) {
         *         if (mReady) {
         *             return;
         *         }
         *         try {
         *             mReadyLock.wait();
         *         } catch (InterruptedException e) {
         *             exit();
         *             throw new ScriptInterruptedException();
         *         }
         *     }
         * }
         * @see android.util.SparseIntArray
         * @see java.util.concurrent.atomic.AtomicInteger
         * @see org.autojs.autojs.runtime.api.Shell
         * @see org.autojs.autojs.core.inputevent.InputEventCodes
         */
        public touchDown(x: number, y: number, id: number): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public void touchMove(int x, int y) throws IOException {
         *     touchMove(x, y, mDefaultId);
         * }
         */
        public touchMove(x: number, y: number): void;
        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * private Shell mShell;
         * private SparseIntArray mSlotIdMap = new SparseIntArray();
         * private final Object mReadyLock = new Object();
         * private volatile boolean mReady = false;
         * public void touchMove(int x, int y, int id) throws IOException {
         *     int slotId = mSlotIdMap.get(id, 0);
         *     sendEvent(EV_ABS, ABS_MT_SLOT, slotId);
         *     sendEvent(EV_ABS, ABS_MT_TOUCH_MAJOR, 5);
         *     sendEvent(EV_ABS, ABS_MT_POSITION_X, scaleX(x));
         *     sendEvent(EV_ABS, ABS_MT_POSITION_Y, scaleY(y));
         *     sendEvent(EV_SYN, SYN_REPORT, 0x00000000);
         * }
         * public void sendEvent(int type, int code, int value) throws IOException {
         *     waitForReady();
         *     sendEventInternal(type, code, value);
         * }
         * private void sendEventInternal(int type, int code, int value) {
         *     mShell.exec(type + " " + code + " " + value);
         * }
         * private void waitForReady() throws IOException {
         *     if (mReady) {
         *         return;
         *     }
         *     synchronized (mReadyLock) {
         *         if (mReady) {
         *             return;
         *         }
         *         try {
         *             mReadyLock.wait();
         *         } catch (InterruptedException e) {
         *             exit();
         *             throw new ScriptInterruptedException();
         *         }
         *     }
         * }
         * @see android.util.SparseIntArray
         * @see org.autojs.autojs.runtime.api.Shell
         * @see org.autojs.autojs.core.inputevent.InputEventCodes
         */
        public touchMove(x: number, y: number, id: number): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * private int mDefaultId = 0;
         * public void touchUp() throws IOException {
         *     touchUp(mDefaultId);
         * }
         */
        public touchUp(): void;
        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * private SparseIntArray mSlotIdMap = new SparseIntArray();
         * private Shell mShell;
         * private final Object mReadyLock = new Object();
         * private volatile boolean mReady = false;
         * public void touchUp(int id) throws IOException {
         *     int slotId;
         *     int i = mSlotIdMap.indexOfKey(id);
         *     if (i < 0) {
         *         slotId = 0;
         *     } else {
         *         slotId = mSlotIdMap.valueAt(i);
         *         mSlotIdMap.removeAt(i);
         *     }
         *     sendEvent(EV_ABS, ABS_MT_SLOT, slotId);
         *     sendEvent(EV_ABS, ABS_MT_TRACKING_ID, 0xFFFFFFFF);
         *     if (mSlotIdMap.size() == 0) {
         *         sendEvent(EV_KEY, BTN_TOUCH, UP);
         *         //sendEvent(EV_KEY, BTN_TOOL_FINGER, 0x00000000);
         *     }
         *     sendEvent(EV_SYN, SYN_REPORT, 0x00000000);
         * }
         * public void sendEvent(int type, int code, int value) throws IOException {
         *     waitForReady();
         *     sendEventInternal(type, code, value);
         * }
         *
         * private void sendEventInternal(int type, int code, int value) {
         *     mShell.exec(type + " " + code + " " + value);
         * }
         *
         * private void waitForReady() throws IOException {
         *     if (mReady) {
         *         return;
         *     }
         *     synchronized (mReadyLock) {
         *         if (mReady) {
         *             return;
         *         }
         *         try {
         *             mReadyLock.wait();
         *         } catch (InterruptedException e) {
         *             exit();
         *             throw new ScriptInterruptedException();
         *         }
         *     }
         * }
         * @see android.util.SparseIntArray
         * @see org.autojs.autojs.runtime.api.Shell
         * @see org.autojs.autojs.core.inputevent.InputEventCodes
         */
        public touchUp(id: number): void;

        public touchX(x: number): void;

        public touchY(y: number): void;

    }

    class __RootAutomator__ extends org.autojs.autojs.core.inputevent.RootAutomator {

        public constructor(context: android.content.Context, inputDevice: string, waitForReady: boolean);

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * private int mDefaultId = 0;
         * public int getDefaultId() {
         *     return mDefaultId;
         * }
         */
        public getDefaultId(): number;

        /**
         * public void longPress(int x, int y) throws IOException {
         *     press(x, y, ViewConfiguration.getLongPressTimeout() + 200, getDefaultId());
         * }
         * @see android.view.ViewConfiguration
         * @see org.autojs.autojs.core.inputevent.RootAutomator.getDefaultId
         */
        public longPress(x: number, y: number): void;
        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public void longPress(int x, int y, int id) throws IOException {
         *     press(x, y, ViewConfiguration.getLongPressTimeout() + 200, id);
         * }
         * @see android.view.ViewConfiguration
         */
        public longPress(x: number, y: number, id: number): void;

        public onInterrupted(exception: java.lang.InterruptedException): void;

        public onNewLine(line: string): void;

        public onOutput(str: string): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public void press(int x, int y, int duration) throws IOException {
         *     press(x, y, duration, getDefaultId());
         * }
         * @see org.autojs.autojs.core.inputevent.RootAutomator.getDefaultId
         */
        public press(x: number, y: number, duration: number): void;
        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public void press(int x, int y, int duration, int id) throws IOException {
         *     touchDown(x, y, id);
         *     sleep(duration);
         *     touchUp(id);
         * }
         */
        public press(x: number, y: number, duration: number, id: number): void;

        public sendEvent(type: number, code: number, value: number): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public void setDefaultId(int defaultId) {
         *     mDefaultId = defaultId;
         * }
         */
        public setDefaultId(defaultId: number): void;

        public setScreenMetrics(width: number, height: number): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * private int mDefaultId = 0;
         * public void swipe(int x1, int y1, int x2, int y2) throws IOException {
         *     swipe(x1, y1, x2, y2, 300, mDefaultId);
         * }
         */
        public swipe(x1: number, y1: number, x2: number, y2: number): void;
        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * private int mDefaultId = 0;
         * public void swipe(int x1, int y1, int x2, int y2, int duration) throws IOException {
         *     swipe(x1, y1, x2, y2, duration, mDefaultId);
         * }
         */
        public swipe(x1: number, y1: number, x2: number, y2: number, duration: number): void;
        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public void swipe(int x1, int y1, int x2, int y2, int duration, int id) throws IOException {
         *     long now = SystemClock.uptimeMillis();
         *     touchDown(x1, y1, id);
         *     long startTime = now;
         *     long endTime = startTime + duration;
         *     while (now < endTime) {
         *         long elapsedTime = now - startTime;
         *         float alpha = (float) elapsedTime / duration;
         *         // lerp: linear interpolation between two points or rotations [ i guess so :) ]
         *         touchMove((int) lerp(x1, x2, alpha), (int) lerp(y1, y2, alpha), id);
         *         now = SystemClock.uptimeMillis();
         *     }
         *     touchUp(id);
         * }
         */
        public swipe(x1: number, y1: number, x2: number, y2: number, duration: number, id: number): void;

        /**
         * @example
         * let ra = new RootAutomator();
         * ra.tap(100, 100);
         * ra.exit();
         * @example Source code summary (zh-CN: 源代码摘要)
         * public void tap(int x, int y) throws IOException {
         *     sendEvent(x, y, mDefaultId);
         * }
         * @see org.autojs.autojs.core.inputevent.RootAutomator.sendEvent
         */
        public tap(x: number, y: number): void;
        /**
         * @example
         * let ra = new RootAutomator();
         * ra.tap(100, 100, 1); // finger 1
         * ra.tap(200, 200, 2); // finger 2
         * ra.exit();
         * @example Source code summary (zh-CN: 源代码摘要)
         * public void tap(int x, int y, int id) throws IOException {
         *     touchDown(x, y, id);
         *     touchUp(id);
         * }
         * @see org.autojs.autojs.core.inputevent.RootAutomator.touchDown
         * @see org.autojs.autojs.core.inputevent.RootAutomator.touchUp
         */
        public tap(x: number, y: number, id: number): void;

        public touch(x: number, y: number): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * private int mDefaultId = 0;
         * public void touchDown(int x, int y) throws IOException {
         *     touchDown(x, y, mDefaultId);
         * }
         */
        public touchDown(x: number, y: number): void;
        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * private Shell mShell;
         * private AtomicInteger mTracingId = new AtomicInteger(1);
         * private SparseIntArray mSlotIdMap = new SparseIntArray();
         * private final Object mReadyLock = new Object();
         * private volatile boolean mReady = false;
         * public void touchDown(int x, int y, int id) throws IOException {
         *     if (mSlotIdMap.size() == 0) {
         *         touchDown0(x, y, id);
         *         return;
         *     }
         *     int slotId = mSlotIdMap.size();
         *     mSlotIdMap.put(id, slotId);
         *     sendEvent(EV_ABS, ABS_MT_SLOT, slotId);
         *     sendEvent(EV_ABS, ABS_MT_TRACKING_ID, mTracingId.getAndIncrement());
         *     sendEvent(EV_ABS, ABS_MT_POSITION_X, scaleX(x));
         *     sendEvent(EV_ABS, ABS_MT_POSITION_Y, scaleY(y));
         *     sendEvent(EV_ABS, ABS_MT_TOUCH_MAJOR, 5);
         *     sendEvent(EV_ABS, ABS_MT_WIDTH_MAJOR, 5);
         *     sendEvent(EV_SYN, SYN_REPORT, 0);
         * }
         * private void touchDown0(int x, int y, int id) throws IOException {
         *     mSlotIdMap.put(id, 0);
         *     sendEvent(EV_ABS, ABS_MT_TRACKING_ID, mTracingId.getAndIncrement());
         *     sendEvent(EV_KEY, BTN_TOUCH, DOWN);
         *     //sendEvent(EV_KEY, BTN_TOOL_FINGER, 0x00000001);
         *     sendEvent(EV_ABS, ABS_MT_POSITION_X, scaleX(x));
         *     sendEvent(EV_ABS, ABS_MT_POSITION_Y, scaleY(y));
         *     //sendEvent(EV_ABS, ABS_MT_PRESSURE, 200);
         *     sendEvent(EV_ABS, ABS_MT_TOUCH_MAJOR, 5);
         *     sendEvent(EV_ABS, ABS_MT_WIDTH_MAJOR, 5);
         *     sendEvent(EV_SYN, SYN_REPORT, 0);
         * }
         * public void sendEvent(int type, int code, int value) throws IOException {
         *     waitForReady();
         *     sendEventInternal(type, code, value);
         * }
         * private void sendEventInternal(int type, int code, int value) {
         *     mShell.exec(type + " " + code + " " + value);
         * }
         * private void waitForReady() throws IOException {
         *     if (mReady) {
         *         return;
         *     }
         *     synchronized (mReadyLock) {
         *         if (mReady) {
         *             return;
         *         }
         *         try {
         *             mReadyLock.wait();
         *         } catch (InterruptedException e) {
         *             exit();
         *             throw new ScriptInterruptedException();
         *         }
         *     }
         * }
         * @see android.util.SparseIntArray
         * @see java.util.concurrent.atomic.AtomicInteger
         * @see org.autojs.autojs.runtime.api.Shell
         * @see org.autojs.autojs.core.inputevent.InputEventCodes
         */
        public touchDown(x: number, y: number, id: number): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public void touchMove(int x, int y) throws IOException {
         *     touchMove(x, y, mDefaultId);
         * }
         */
        public touchMove(x: number, y: number): void;
        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * private Shell mShell;
         * private SparseIntArray mSlotIdMap = new SparseIntArray();
         * private final Object mReadyLock = new Object();
         * private volatile boolean mReady = false;
         * public void touchMove(int x, int y, int id) throws IOException {
         *     int slotId = mSlotIdMap.get(id, 0);
         *     sendEvent(EV_ABS, ABS_MT_SLOT, slotId);
         *     sendEvent(EV_ABS, ABS_MT_TOUCH_MAJOR, 5);
         *     sendEvent(EV_ABS, ABS_MT_POSITION_X, scaleX(x));
         *     sendEvent(EV_ABS, ABS_MT_POSITION_Y, scaleY(y));
         *     sendEvent(EV_SYN, SYN_REPORT, 0x00000000);
         * }
         * public void sendEvent(int type, int code, int value) throws IOException {
         *     waitForReady();
         *     sendEventInternal(type, code, value);
         * }
         * private void sendEventInternal(int type, int code, int value) {
         *     mShell.exec(type + " " + code + " " + value);
         * }
         * private void waitForReady() throws IOException {
         *     if (mReady) {
         *         return;
         *     }
         *     synchronized (mReadyLock) {
         *         if (mReady) {
         *             return;
         *         }
         *         try {
         *             mReadyLock.wait();
         *         } catch (InterruptedException e) {
         *             exit();
         *             throw new ScriptInterruptedException();
         *         }
         *     }
         * }
         * @see android.util.SparseIntArray
         * @see org.autojs.autojs.runtime.api.Shell
         * @see org.autojs.autojs.core.inputevent.InputEventCodes
         */
        public touchMove(x: number, y: number, id: number): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * private int mDefaultId = 0;
         * public void touchUp() throws IOException {
         *     touchUp(mDefaultId);
         * }
         */
        public touchUp(): void;
        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * private SparseIntArray mSlotIdMap = new SparseIntArray();
         * private Shell mShell;
         * private final Object mReadyLock = new Object();
         * private volatile boolean mReady = false;
         * public void touchUp(int id) throws IOException {
         *     int slotId;
         *     int i = mSlotIdMap.indexOfKey(id);
         *     if (i < 0) {
         *         slotId = 0;
         *     } else {
         *         slotId = mSlotIdMap.valueAt(i);
         *         mSlotIdMap.removeAt(i);
         *     }
         *     sendEvent(EV_ABS, ABS_MT_SLOT, slotId);
         *     sendEvent(EV_ABS, ABS_MT_TRACKING_ID, 0xFFFFFFFF);
         *     if (mSlotIdMap.size() == 0) {
         *         sendEvent(EV_KEY, BTN_TOUCH, UP);
         *         //sendEvent(EV_KEY, BTN_TOOL_FINGER, 0x00000000);
         *     }
         *     sendEvent(EV_SYN, SYN_REPORT, 0x00000000);
         * }
         * public void sendEvent(int type, int code, int value) throws IOException {
         *     waitForReady();
         *     sendEventInternal(type, code, value);
         * }
         *
         * private void sendEventInternal(int type, int code, int value) {
         *     mShell.exec(type + " " + code + " " + value);
         * }
         *
         * private void waitForReady() throws IOException {
         *     if (mReady) {
         *         return;
         *     }
         *     synchronized (mReadyLock) {
         *         if (mReady) {
         *             return;
         *         }
         *         try {
         *             mReadyLock.wait();
         *         } catch (InterruptedException e) {
         *             exit();
         *             throw new ScriptInterruptedException();
         *         }
         *     }
         * }
         * @see android.util.SparseIntArray
         * @see org.autojs.autojs.runtime.api.Shell
         * @see org.autojs.autojs.core.inputevent.InputEventCodes
         */
        public touchUp(id: number): void;

        public touchX(x: number): void;

        public touchY(y: number): void;

    }

}
