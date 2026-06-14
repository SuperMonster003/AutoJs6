// Type definitions for AutoJs6 internal module events
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 5.1.3
//
// Last modified: Jun 14, 2026

/// <reference path="../index.d.ts" />

/**
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/events/Events.kt
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/events/Keys.kt
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/Events.java
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/core/eventloop/EventEmitter.java
 */

declare namespace Internal {

    class Events extends org.autojs.autojs.runtime.api.Events {

        __asEmitter__(): EventEmitter$;
        __asEmitter__(obj: null | undefined, thread?: Events.ThreadTarget | null): EventEmitter$;
        __asEmitter__<T extends object>(obj: T, thread?: Events.ThreadTarget | null): T & EventEmitter$;

        /**
         * @Overrides for JSDoc or more specific types
         */

        /**
         * @example
         * events.broadcast.on("hello", function (name) {
         *     toast("Hello, " + name);
         * });
         * events.broadcast.emit("hello", "John");
         */
        broadcast: org.autojs.autojs.core.broadcast.BroadcastEmitter;

        /**
         * @example
         * let sum = events.emitter(threads.currentThread()).on('result', function (s) {
         *     log("sum: " + s);
         *     log("thread: " + threads.currentThread());
         *     toast('ok');
         * });
         * threads.start(function () {
         *     toastLog('calculating...');
         *     let s = 0;
         *     for (let i = 1; i <= 6e6; i++) {
         *         s += i;
         *     }
         *     sum.emit('result', s);
         * });
         */
        emitter(): EventEmitter$;
        emitter(thread: java.lang.Thread): EventEmitter$;
        emitter(thread: org.autojs.autojs.core.looper.MainThreadProxy): EventEmitter$;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * class Test {
         *     private long mTouchEventTimeout = 10;
         *     public void setTouchEventTimeout(long touchEventTimeout) {
         *         mTouchEventTimeout = touchEventTimeout;
         *     }
         * }
         */
        setTouchEventTimeout(timeout: number): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * class Test {
         *     private long mTouchEventTimeout = 10;
         *     public long getTouchEventTimeout() {
         *         return mTouchEventTimeout;
         *     }
         * }
         */
        getTouchEventTimeout(): number;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * private boolean mListeningKey = false;
         * private Loopers mLoopers;
         * private Context mContext;
         * private Handler mHandler;
         * private ScriptRuntime mScriptRuntime;
         * private AccessibilityBridge mAccessibilityBridge;
         * public void observeKey() {
         *     if (mListeningKey)
         *         return;
         *     AccessibilityService service = getAccessibilityService();
         *     if ((service.getServiceInfo().flags & AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) == 0) {
         *         throw new ScriptException(mContext.getString(R.string.text_should_enable_key_observing));
         *     }
         *     ensureHandler();
         *     mLoopers.waitWhenIdle(true);
         *     mListeningKey = true;
         *     mAccessibilityBridge.ensureServiceEnabled();
         *     service.getOnKeyObserver().addListener(this);
         * }
         * private AccessibilityService getAccessibilityService() {
         *     mScriptRuntime.ensureAccessibilityServiceEnabled();
         *     AccessibilityService service = mAccessibilityBridge.getService();
         *     if (service == null)
         *         throw new ScriptException('AccessibilityService = null');
         *     return service;
         * }
         * private void ensureHandler() {
         *     if (mHandler == null) {
         *         mHandler = new Handler();
         *     }
         * }
         * @see android.accessibilityservice.AccessibilityServiceInfo
         * @see android.content.Context
         * @see android.os.Handler
         * @see org.autojs.autojs.core.accessibility.AccessibilityBridge
         * @see org.autojs.autojs.core.looper.Loopers
         * @see org.autojs.autojs.runtime.ScriptRuntime
         * @see org.autojs.autojs.runtime.exception.ScriptException
         * @see org.autojs.autojs.core.accessibility.AccessibilityService
         */
        observeKey(): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * private TouchObserver mTouchObserver;
         * private Loopers mLoopers;
         * private Handler mHandler;
         * private Context mContext;
         * public void observeTouch() {
         *     if (mTouchObserver != null)
         *         return;
         *     ensureHandler();
         *     mLoopers.waitWhenIdle(true);
         *     mTouchObserver = new TouchObserver(InputEventObserver.getGlobal(mContext));
         *     mTouchObserver.setOnTouchEventListener(this);
         *     mTouchObserver.observe();
         * }
         * private void ensureHandler() {
         *     if (mHandler == null) {
         *         mHandler = new Handler();
         *     }
         * }
         * @see android.content.Context
         * @see android.os.Handler
         * @see org.autojs.autojs.core.inputevent.InputEventObserver
         * @see org.autojs.autojs.core.inputevent.TouchObserver
         * @see org.autojs.autojs.core.looper.Loopers
         */
        observeTouch(): void;

        observeNotification(): void;

        removeNotificationObserver(): void;

        observeToast(): void;

        removeToastObserver(): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * private static final String PREFIX_KEY_DOWN = "__key_down__#";
         * public Events onceKeyDown(String keyName, Object listener) {
         *     once(PREFIX_KEY_DOWN + keyName, listener);
         *     return this;
         * }
         * public EventEmitter once(String eventName, Object listener) {
         *     getListeners(eventName).add(listener, true);
         *     return this;
         * }
         * -- @NonNull
         * private Listeners getListeners(String eventName) {
         *     Listeners listeners = mListenersMap.get(eventName);
         *     if (listeners == null) {
         *         listeners = new Listeners();
         *         mListenersMap.put(eventName, listeners);
         *     }
         *     return listeners;
         * }
         * @see org.autojs.autojs.core.eventloop.EventEmitter
         * @see org.autojs.autojs.runtime.api.Events
         */
        onceKeyDown(keyName: Keys.Name, listener: (event: android.view.KeyEvent) => void): org.autojs.autojs.runtime.api.Events;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * private static final String PREFIX_KEY_UP = "__key_up__#";
         * public Events onceKeyUp(String keyName, Object listener) {
         *     once(PREFIX_KEY_UP + keyName, listener);
         *     return this;
         * }
         * public EventEmitter once(String eventName, Object listener) {
         *     getListeners(eventName).add(listener, true);
         *     return this;
         * }
         * -- @NonNull
         * private Listeners getListeners(String eventName) {
         *     Listeners listeners = mListenersMap.get(eventName);
         *     if (listeners == null) {
         *         listeners = new Listeners();
         *         mListenersMap.put(eventName, listeners);
         *     }
         *     return listeners;
         * }
         * @see org.autojs.autojs.core.eventloop.EventEmitter
         * @see org.autojs.autojs.runtime.api.Events
         */
        onceKeyUp(keyName: Keys.Name, listener: (event: android.view.KeyEvent) => void): org.autojs.autojs.runtime.api.Events;

        /**
         * @example
         * events.observeKey();
         * events.onKeyDown("volume_up", function (event) {
         *     toast('音量上键被按下');
         * });
         * events.onKeyDown("menu", function (event) {
         *     toast('菜单键被按下');
         *     exit();
         * });
         * @example Source code summary (zh-CN: 源代码摘要)
         * private static final String PREFIX_KEY_DOWN = "__key_down__#";
         * public Events onKeyDown(String keyName, Object listener) {
         *     on(PREFIX_KEY_DOWN + keyName, listener);
         *     return this;
         * }
         * public EventEmitter on(String eventName, Object listener) {
         *     getListeners(eventName).add(listener, false);
         *     return this;
         * }
         * -- @NonNull
         * private Listeners getListeners(String eventName) {
         *     Listeners listeners = mListenersMap.get(eventName);
         *     if (listeners == null) {
         *         listeners = new Listeners();
         *         mListenersMap.put(eventName, listeners);
         *     }
         *     return listeners;
         * }
         * @see org.autojs.autojs.core.eventloop.EventEmitter
         * @see org.autojs.autojs.runtime.api.Events
         */
        onKeyDown(keyName: Keys.Name, listener: (event: android.view.KeyEvent) => void): org.autojs.autojs.runtime.api.Events;

        /**
         * @example
         * events.observeKey();
         * events.onKeyDown("volume_down", function (event) {
         *     toast('音量上键弹起');
         * });
         * events.onKeyDown("home", function (event) {
         *     toast('Home键弹起');
         *     exit();
         * });
         * @example Source code summary (zh-CN: 源代码摘要)
         * private static final String PREFIX_KEY_UP = "__key_up__#";
         * public Events onKeyUp(String keyName, Object listener) {
         *     on(PREFIX_KEY_UP + keyName, listener);
         *     return this;
         * }
         * public EventEmitter on(String eventName, Object listener) {
         *     getListeners(eventName).add(listener, false);
         *     return this;
         * }
         * -- @NonNull
         * private Listeners getListeners(String eventName) {
         *     Listeners listeners = mListenersMap.get(eventName);
         *     if (listeners == null) {
         *         listeners = new Listeners();
         *         mListenersMap.put(eventName, listeners);
         *     }
         *     return listeners;
         * }
         * @see org.autojs.autojs.core.eventloop.EventEmitter
         * @see org.autojs.autojs.runtime.api.Events
         */
        onKeyUp(keyName: Keys.Name, listener: (event: android.view.KeyEvent) => void): org.autojs.autojs.runtime.api.Events;

        /**
         * @example
         * events.observeNotification();
         * events.onNotification(function (notification) {
         *     log("title: " + notification.getTitle());
         *     log("text: " + notification.getText());
         *     log("package: " + notification.getPackageName());
         *     exit();
         * });
         */
        onNotification(listener: (notification: org.autojs.autojs.core.notification.Notification) => void): org.autojs.autojs.runtime.api.Events;

        onNotification(notification: org.autojs.autojs.core.notification.Notification): void;

        /**
         * @example
         * events.observeToast();
         * events.onToast(function (toast) {
         *     log("内容: " + toast.getText());
         *     log("内容组: " + toast.getTexts());
         *     log("包名: " + toast.getPackageName());
         * });
         */
        onToast(listener: (toast: org.autojs.autojs.core.accessibility.AccessibilityNotificationObserver.Toast) => void): org.autojs.autojs.runtime.api.Events;

        onToast(toast: org.autojs.autojs.core.accessibility.AccessibilityNotificationObserver.Toast): void;

        /**
         * @example
         * events.observeTouch();
         * events.onTouch(function (p) {
         *     log(p.x + ", " + p.y);
         *     exit();
         * });
         * @example Source code summary (zh-CN: 源代码摘要)
         * private Map<String, Listeners> mListenersMap = new HashMap<>();
         * public Events onTouch(Object listener) {
         *     on("touch", listener);
         *     return this;
         * }
         * public EventEmitter on(String eventName, Object listener) {
         *     getListeners(eventName).add(listener, false);
         *     return this;
         * }
         * -- @NonNull
         * private Listeners getListeners(String eventName) {
         *     Listeners listeners = mListenersMap.get(eventName);
         *     if (listeners == null) {
         *         listeners = new Listeners();
         *         mListenersMap.put(eventName, listeners);
         *     }
         *     return listeners;
         * }
         * @see org.autojs.autojs.core.eventloop.EventEmitter
         * @see org.autojs.autojs.runtime.api.Events
         * @see java.util.HashMap
         * @see java.util.Map
         */
        onTouch(listener: (point: android.graphics.Point) => void): org.autojs.autojs.runtime.api.Events;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * private long mLastTouchEventMillis;
         * private long mTouchEventTimeout = 10;
         * private Handler mHandler;
         * private Map<String, Listeners> mListenersMap = new HashMap<>();
         * public void onTouch(final int x, final int y) {
         *     if (System.currentTimeMillis() - mLastTouchEventMillis < mTouchEventTimeout) {
         *         return;
         *     }
         *     mLastTouchEventMillis = System.currentTimeMillis();
         *     mHandler.post(() -> emit("touch", new Point(x, y)));
         * }
         * public boolean emit(String eventName, Object... args) {
         *     Listeners listeners = mListenersMap.get(eventName);
         *     if (listeners == null || listeners.empty())
         *         return false;
         *     listeners.emit(args);
         *     return true;
         * }
         * @see android.graphics.Point
         * @see android.os.Handler
         * @see java.util.HashMap
         */
        onTouch(x: number, y: number): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * private static final String PREFIX_KEY_DOWN = "__key_down__#";
         * private Map<String, Listeners> mListenersMap = new HashMap<>();
         * public Events removeAllKeyDownListeners(String keyName) {
         *     removeAllListeners(PREFIX_KEY_DOWN + keyName);
         *     return this;
         * }
         * public EventEmitter removeAllListeners(String eventName) {
         *     mListenersMap.remove(eventName);
         *     return this;
         * }
         * @see org.autojs.autojs.core.eventloop.EventEmitter
         * @see org.autojs.autojs.runtime.api.Events
         */
        removeAllKeyDownListeners(keyName: Keys.Name): org.autojs.autojs.runtime.api.Events;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * private static final String PREFIX_KEY_UP = "__key_up__#";
         * private Map<String, Listeners> mListenersMap = new HashMap<>();
         * public Events removeAllKeyUpListeners(String keyName) {
         *     removeAllListeners(PREFIX_KEY_UP + keyName);
         *     return this;
         * }
         * public EventEmitter removeAllListeners(String eventName) {
         *     mListenersMap.remove(eventName);
         *     return this;
         * }
         * @see org.autojs.autojs.core.eventloop.EventEmitter
         * @see org.autojs.autojs.runtime.api.Events
         */
        removeAllKeyUpListeners(keyName: Keys.Name): org.autojs.autojs.runtime.api.Events;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * private Map<String, Listeners> mListenersMap = new HashMap<>();
         * public Events removeAllTouchListeners() {
         *     removeAllListeners('touch');
         *     return this;
         * }
         * public EventEmitter removeAllListeners(String eventName) {
         *     mListenersMap.remove(eventName);
         *     return this;
         * }
         * @see org.autojs.autojs.core.eventloop.EventEmitter
         * @see org.autojs.autojs.runtime.api.Events
         * @see java.util.HashMap
         */
        removeAllTouchListeners(): org.autojs.autojs.runtime.api.Events;

        /**
         * @example
         * events.setKeyInterceptionEnabled(true);
         * events.observeKey();
         * events.onKeyDown("volume_up", () => log("音量上键被按下"));
         * @example Source code summary (zh-CN: 源代码摘要)
         * private Set<String> mInterceptedKeys = new HashSet<>();
         * private KeyInterceptor mKeyInterceptor;
         * private volatile boolean mInterceptsAllKey = false;
         * private AccessibilityBridge mAccessibilityBridge;
         * private ScriptRuntime mScriptRuntime;
         * public void setKeyInterceptionEnabled(boolean enabled) {
         *     mInterceptsAllKey = enabled;
         *     if (mInterceptsAllKey) {
         *         ensureKeyInterceptor();
         *     }
         * }
         * private void ensureKeyInterceptor() {
         *     if (mKeyInterceptor != null)
         *         return;
         *     mKeyInterceptor = event -> {
         *         if (mInterceptsAllKey)
         *             return true;
         *         String keyName = KeyEvent.keyCodeToString(event.getKeyCode()).substring(8).toLowerCase();
         *         return mInterceptedKeys.contains(keyName);
         *     };
         *     getAccessibilityService().getKeyInterrupterObserver().addKeyInterrupter(mKeyInterceptor);
         * }
         * private AccessibilityService getAccessibilityService() {
         *     mScriptRuntime.ensureAccessibilityServiceEnabled();
         *     AccessibilityService service = mAccessibilityBridge.getService();
         *     if (service == null)
         *         throw new ScriptException('AccessibilityService = null');
         *     return service;
         * }
         * @see android.view.KeyEvent
         * @see org.autojs.autojs.core.accessibility.AccessibilityBridge
         * @see org.autojs.autojs.runtime.ScriptRuntime
         * @see org.autojs.autojs.runtime.exception.ScriptException
         * @see org.autojs.autojs.core.accessibility.AccessibilityService
         * @see org.autojs.autojs.core.accessibility.KeyInterceptor
         * @see java.util.HashSet
         * @see java.util.Set
         */
        setKeyInterceptionEnabled(interceptionEnabled: boolean): void;

        /**
         * Make keys dysfunctional but responsive for appended listeners
         * @example
         * // VOL+ is not able to adjust volume
         * events.setKeyInterceptionEnabled("volume_up", true);
         * events.observeKey();
         * events.onKeyDown("volume_up", () => log("音量上键被按下"));
         * @example Source code summary (zh-CN: 源代码摘要)
         * private Set<String> mInterceptedKeys = new HashSet<>();
         * private KeyInterceptor mKeyInterceptor;
         * private volatile boolean mInterceptsAllKey = false;
         * private AccessibilityBridge mAccessibilityBridge;
         * private ScriptRuntime mScriptRuntime;
         * public void setKeyInterceptionEnabled(String key, boolean enabled) {
         *     if (enabled) {
         *         mInterceptedKeys.add(key);
         *         ensureKeyInterceptor();
         *     } else {
         *         mInterceptedKeys.remove(key);
         *     }
         * }
         * private void ensureKeyInterceptor() {
         *     if (mKeyInterceptor != null)
         *         return;
         *     mKeyInterceptor = event -> {
         *         if (mInterceptsAllKey)
         *             return true;
         *         String keyName = KeyEvent.keyCodeToString(event.getKeyCode()).substring(8).toLowerCase();
         *         return mInterceptedKeys.contains(keyName);
         *     };
         *     getAccessibilityService().getKeyInterrupterObserver().addKeyInterrupter(mKeyInterceptor);
         * }
         * private AccessibilityService getAccessibilityService() {
         *     mScriptRuntime.ensureAccessibilityServiceEnabled();
         *     AccessibilityService service = mAccessibilityBridge.getService();
         *     if (service == null)
         *         throw new ScriptException('AccessibilityService = null');
         *     return service;
         * }
         * @see android.view.KeyEvent
         * @see org.autojs.autojs.core.accessibility.AccessibilityBridge
         * @see org.autojs.autojs.runtime.ScriptRuntime
         * @see org.autojs.autojs.runtime.exception.ScriptException
         * @see org.autojs.autojs.core.accessibility.AccessibilityService
         * @see org.autojs.autojs.core.accessibility.KeyInterceptor
         * @see java.util.HashSet
         * @see java.util.Set
         */
        setKeyInterceptionEnabled(keyName: Keys.Name, interceptionEnabled: boolean): void;

    }

    interface Keys {

        /**
         * android.view.KeyEvent.KEYCODE_HOME // 3
         */
        home: number;
        HOME: number;
        /**
         * android.view.KeyEvent.KEYCODE_MENU // 82
         */
        menu: number;
        MENU: number;
        /**
         * android.view.KeyEvent.KEYCODE_BACK // 4
         */
        back: number;
        BACK: number;
        /**
         * android.view.KeyEvent.KEYCODE_VOLUME_UP // 24
         */
        volumeUp: number;
        volume_up: number;
        VOLUME_UP: number;
        /**
         * android.view.KeyEvent.KEYCODE_VOLUME_DOWN // 25
         */
        volumeDown: number;
        volume_down: number;
        VOLUME_DOWN: number;

    }

}

declare namespace Events {

    type ThreadTarget = java.lang.Thread | org.autojs.autojs.core.looper.MainThreadProxy;

}

declare namespace Keys {

    type Name = 'home' | 'menu' | 'back' | 'volume_up' | 'volume_down';

}

declare let keys: Internal.Keys;
declare let $keys: Internal.Keys;

declare class EventEmitter$ extends org.autojs.autojs.core.eventloop.EventEmitter {

    public on(eventName: string, listener: any): this;
    /**
     * @example
     * auto();
     * events.observeKey();
     * events.on("key", function (keyCode, event) {
     *     if (keyCode === keys.volume_down && event.getAction() === event.ACTION_UP) {
     *         toast('音量减键弹起');
     *         exit();
     *     }
     * });
     */
    public on(eventName: 'key' | 'key_down' | 'key_up', listener: (keyCode: number, event: android.view.KeyEvent) => void): this;
    /**
     * @example
     * events.observeTouch();
     * events.on("touch", function (point) {
     *     toast('You\'ve touched (' + point.x + ", " + point.y + ")");
     *     exit();
     * });
     */
    public on(eventName: 'touch', listener: (point: android.graphics.Point) => void): this;
    /**
     * @example
     * events.observeNotification();
     * events.on("notification", function(notification) {
     *     log("title: " + notification.getTitle());
     *     log("text: " + notification.getText());
     *     log("package: " + notification.getPackageName());
     *     exit();
     * });
     */
    public on(eventName: 'notification', listener: (notification: org.autojs.autojs.core.notification.Notification) => void): this;
    /**
     * @example
     * events.observeToast();
     * events.on("toast", function (toast) {
     *     log("内容: " + toast.getText());
     *     log("内容组: " + toast.getTexts());
     *     log("包名: " + toast.getPackageName());
     * });
     */
    public on(eventName: 'toast', listener: (toast: org.autojs.autojs.core.accessibility.AccessibilityNotificationObserver.Toast) => void): this;
    /**
     * @example
     * // marked by SuperMonster003 on Aug 4, 2020
     * // this is not available and i don't know the resolution :(
     * events.observeGesture();
     * events.on("gesture", function (gesture) {
     *     log(gesture);
     * });
     */
    public on(eventName: 'gesture', listener: (gestureString: string) => void): this;
    /**
     * @example
     * events.on("exit", function() {
     *     toast('Goodbye');
     * })
     * exit();
     */
    public on(eventName: 'exit', listener: () => void): this;
    public on(eventName: 'accuracy_change', listener: (accuracy: number) => void): this;
    public on(eventName: 'unsupported_sensor', listener: (sensorName: string) => void): this;

    public once(eventName: string, listener: any): this;
    /**
     * @example
     * auto();
     * events.observeKey();
     * events.once("key", function (keyCode, event) {
     *     if (keyCode === keys.volume_down && event.getAction() === event.ACTION_UP) {
     *         toast('音量减键弹起');
     *         exit();
     *     }
     * });
     */
    public once(eventName: 'key' | 'key_down' | 'key_up', listener: (keyCode: number, event: android.view.KeyEvent) => void): this;
    /**
     * @example
     * events.observeTouch();
     * events.once("touch", function (point) {
     *     toast('You\'ve touched (' + point.x + ", " + point.y + ")");
     *     exit();
     * });
     */
    public once(eventName: 'touch', listener: (point: android.graphics.Point) => void): this;
    /**
     * @example
     * events.observeNotification();
     * events.once("notification", function(notification) {
     *     log("title: " + notification.getTitle());
     *     log("text: " + notification.getText());
     *     log("package: " + notification.getPackageName());
     *     exit();
     * });
     */
    public once(eventName: 'notification', listener: (notification: org.autojs.autojs.core.notification.Notification) => void): this;
    /**
     * @example
     * events.observeToast();
     * events.once("toast", function (toast) {
     *     log("内容: " + toast.getText());
     *     log("内容组: " + toast.getTexts());
     *     log("包名: " + toast.getPackageName());
     * });
     */
    public once(eventName: 'toast', listener: (toast: org.autojs.autojs.core.accessibility.AccessibilityNotificationObserver.Toast) => void): this;
    /**
     * @example
     * // marked by SuperMonster003 on Aug 4, 2020
     * // this is not available and i don't know the resolution :(
     * events.observeGesture();
     * events.once("gesture", function (gesture) {
     *     log(gesture);
     * });
     */
    public once(eventName: 'gesture', listener: (gestureString: string) => void): this;
    /**
     * @example
     * events.once("exit", function() {
     *     toast('Goodbye');
     * })
     * exit();
     */
    public once(eventName: 'exit', listener: () => void): this;

    public emit(eventName: string, ...args: any[]): boolean;
    public emitSticky(eventName: string, ...args: any[]): boolean;
    public eventNames(): string[];
    public getMaxListeners(): number;
    public listenerCount(eventName: string): number;
    public listeners(eventName: string): any[];
    public prependListener(eventName: string, listener: any): this;
    public prependOnceListener(eventName: string, listener: any): this;
    public removeAllListeners(eventName?: string): this;
    public removeListener(eventName: string, listener: any): this;
    public setMaxListeners(maxListeners: number): this;

}
