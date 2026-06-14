(function(global) {
    "use strict";

    function property(key, globalProperty, variable) {
        return {
            key: key,
            summary: "",
            global: !!globalProperty,
            variable: !!variable
        };
    }

    function properties(keys, globalProperty) {
        return keys.map(function(key) {
            return property(key, globalProperty);
        });
    }

    function module(name, summary, moduleProperties) {
        return {
            name: name,
            url: name + ".html",
            summary: summary,
            properties: moduleProperties
        };
    }

    var appProperties = properties([
        "uninstall", "viewFile", "editFile", "openUrl", "launchPackage", "launch",
        "launchApp", "getPackageName", "getAppName", "openAppSetting", "openAppSettings",
        "sendEmail", "intent", "startActivity", "sendBroadcast"
    ]);

    var automatorProperties = properties([
        "click", "longClick", "press", "swipe", "gesture", "gestures", "gestureAsync",
        "gesturesAsync", "scrollDown", "scrollUp", "input", "setText"
    ], true);

    var selectorProperties = properties([
        "id", "idContains", "idStartsWith", "idEndsWith", "idMatches",
        "text", "textContains", "textStartsWith", "textEndsWith", "textMatches",
        "desc", "descContains", "descStartsWith", "descEndsWith", "descMatches",
        "className", "classNameContains", "classNameStartsWith", "classNameEndsWith",
        "classNameMatches", "packageName", "packageNameContains", "packageNameStartsWith",
        "packageNameEndsWith", "packageNameMatches", "bounds", "boundsInside",
        "boundsContains", "drawingOrder", "checkable", "checked", "focusable", "focused",
        "visibleToUser", "accessibilityFocused", "selected", "clickable", "longClickable",
        "enabled", "password", "scrollable", "editable", "contentInvalid",
        "contextClickable", "multiLine", "dismissable"
    ], true);

    var timerProperties = properties([
        "setTimeout", "clearTimeout", "setInterval", "clearInterval", "setImmediate",
        "clearImmediate"
    ], true);

    var keyProperties = properties([
        "back", "home", "powerDialog", "notifications", "quickSettings", "recents",
        "splitScreen", "Back", "Home", "Power", "Menu", "VolumeUp", "VolumeDown",
        "Camera", "Up", "Down", "Left", "Right", "OK", "Text", "KeyCode"
    ], true);

    global.AUTOJS_INDICES = [
        module("globals", "Global functions", properties([
            "sleep", "currentPackage", "currentActivity", "setClip", "getClip", "toast",
            "toastLog", "waitForActivity", "waitForPackage", "exit", "random"
        ], true).concat([
            property("context", true, true)
        ])),
        module("app", "Application and intent helpers", appProperties.map(function(item) {
            item.global = item.key === "launch" || item.key === "launchApp";
            return item;
        })),
        module("automator", "Accessibility action helpers", automatorProperties),
        module("colors", "Color helpers", properties([
            "red", "green", "blue", "alpha", "toString", "rgb", "argb", "parseColor",
            "isSimilar", "equals"
        ])),
        module("console", "Console logging API", properties([
            "show", "hide", "clear", "verbose", "info", "log", "warn", "error", "assert",
            "input", "rawInput", "setSize", "setPosition"
        ])),
        module("device", "Device information and controls", properties([
            "width", "height", "buildId", "broad", "brand", "device", "model", "product",
            "bootloader", "hardware", "fingerprint", "serial", "sdkInt", "incremental",
            "release", "baseOS", "securityPatch", "codename", "getIMEI", "getAndroidId",
            "getMacAddress", "getBrightness", "getBrightnessMode", "setBrightness",
            "setBrightnessMode", "getMusicVolume", "getNotificationVolume", "getAlarmVolume",
            "getMusicMaxVolume", "getNotificationMaxVolume", "getAlarmMaxVolume",
            "setMusicVolume", "setNotificationVolume", "setAlarmVolume", "getBattery",
            "isCharging", "getTotalMem", "getAvailMem", "isScreenOn", "wakeUp",
            "wakeUpIfNeeded", "keepScreenOn", "keepScreenDim", "cancelKeepingAwake",
            "vibrate", "cancelVibration"
        ])),
        module("dialogs", "Dialog helpers", properties([
            "select", "singleChoice", "multiChoice", "rawInput", "input", "alert",
            "confirm", "prompt"
        ])),
        module("engines", "Script engine management", properties([
            "execScript", "execScriptFile", "execAutoFile", "stopAll", "stopAllAndToast",
            "myEngine", "all"
        ])),
        module("events", "Event helpers", properties([
            "emitter", "observeKey", "setKeyInterceptionEnabled", "observeTouch",
            "observeNotification", "onKeyDown", "onKeyUp", "onceKeyDown", "onceKeyUp",
            "onToast", "onNotification", "onTouch", "on", "once", "emit", "addListener",
            "removeListener", "removeAllListeners"
        ])),
        module("files", "File system helpers", properties([
            "isFile", "isDir", "isEmptyDir", "join", "create", "createWithDirs", "exists",
            "ensureDir", "read", "readBytes", "write", "writeBytes", "append", "appendBytes",
            "copy", "move", "rename", "renameWithoutExtension", "getName",
            "getNameWithoutExtension", "getExtension", "remove", "removeDir",
            "getSdcardPath", "cwd", "path", "listDir", "open"
        ])),
        module("floaty", "Floating window helpers", properties([
            "window", "closeAll"
        ])),
        module("http", "HTTP client API", properties([
            "get", "post", "postJson", "postMultipart", "request"
        ])),
        module("images", "Image processing helpers", properties([
            "save", "pixel", "read", "load", "clip", "requestScreenCapture",
            "captureScreen", "findColor", "findColorInRegion", "findColorEquals",
            "findMultiColors", "detectsColor", "detectColor", "findImage",
            "findImageInRegion"
        ], false).map(function(item) {
            item.global = item.key === "requestScreenCapture" ||
                item.key === "captureScreen" ||
                item.key === "findColor" ||
                item.key === "findImage";
            return item;
        })),
        module("keys", "System key helpers", keyProperties),
        module("media", "Media helpers", properties([
            "scanFile", "playMusic", "musicSeekTo", "pauseMusic", "resumeMusic",
            "stopMusic", "isMusicPlaying", "getMusicDuration", "getMusicCurrentPosition"
        ])),
        module("selector", "Accessibility selector helpers", selectorProperties),
        module("sensors", "Sensor helpers", properties([
            "register", "unregister", "unregisterAll", "ignoresUnsupportedSensor"
        ])),
        module("storages", "Persistent storage helpers", properties([
            "create", "remove"
        ])),
        module("threads", "Thread helpers", properties([
            "start", "shutDownAll", "currentThread", "disposable", "atomic", "lock"
        ])),
        module("timers", "Timer helpers", timerProperties),
        module("ui", "UI DSL namespace", properties([
            "layout", "inflate", "run", "post", "findView", "statusBarColor", "finish",
            "isUiThread", "emitter", "registerWidget", "bindingContext"
        ]))
    ];

    if (!global.AutoJsAceIndices) {
        global.AutoJsAceIndices = global.AUTOJS_INDICES;
    }
})(window);
