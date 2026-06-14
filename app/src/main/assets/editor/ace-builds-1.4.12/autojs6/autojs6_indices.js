(function(global) {
    "use strict";

    function moduleEntry(name, doc) {
        return { name: name, type: "module", doc: doc };
    }

    function entry(name, type, doc) {
        return { name: name, type: type || "function", doc: doc || "" };
    }

    function entries(names, type) {
        return names.map(function(name) {
            return entry(name, type);
        });
    }

    var appEntries = entries([
        "uninstall", "viewFile", "editFile", "openUrl", "launchPackage", "launch",
        "launchApp", "getPackageName", "getAppName", "openAppSetting", "openAppSettings",
        "sendEmail", "intent", "startActivity", "sendBroadcast"
    ]);

    var automatorEntries = entries([
        "click", "longClick", "press", "swipe", "gesture", "gestures", "gestureAsync",
        "gesturesAsync", "scrollDown", "scrollUp", "input", "setText"
    ]);

    var selectorEntries = entries([
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
    ]);

    var timerEntries = entries([
        "setTimeout", "clearTimeout", "setInterval", "clearInterval", "setImmediate",
        "clearImmediate"
    ]);

    var keyEntries = entries([
        "back", "home", "powerDialog", "notifications", "quickSettings", "recents",
        "splitScreen", "Back", "Home", "Power", "Menu", "VolumeUp", "VolumeDown",
        "Camera", "Up", "Down", "Left", "Right", "OK", "Text", "KeyCode"
    ]);

    var modules = {
        app: appEntries,
        automator: automatorEntries,
        colors: entries([
            "red", "green", "blue", "alpha", "toString", "rgb", "argb", "parseColor",
            "isSimilar", "equals"
        ]),
        console: entries([
            "show", "hide", "clear", "verbose", "info", "log", "warn", "error", "assert",
            "input", "rawInput", "setSize", "setPosition"
        ]),
        device: entries([
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
        ], "member"),
        dialogs: entries([
            "select", "singleChoice", "multiChoice", "rawInput", "input", "alert",
            "confirm", "prompt"
        ]),
        engines: entries([
            "execScript", "execScriptFile", "execAutoFile", "stopAll", "stopAllAndToast",
            "myEngine", "all"
        ]),
        events: entries([
            "emitter", "observeKey", "setKeyInterceptionEnabled", "observeTouch",
            "observeNotification", "onKeyDown", "onKeyUp", "onceKeyDown", "onceKeyUp",
            "onToast", "onNotification", "removeAllKeyDownListeners",
            "removeAllKeyUpListeners", "onTouch", "removeAllTouchListeners",
            "getTouchEventTimeout", "setTouchEventTimeout", "on", "once", "emit",
            "getListeners", "addListener", "eventNames", "listenerCount", "listeners",
            "prependListener", "prependOnceListener", "removeAllListeners", "removeListener",
            "setMaxListeners", "getMaxListeners", "defaultMaxListeners"
        ]),
        files: entries([
            "isFile", "isDir", "isEmptyDir", "join", "create", "createWithDirs", "exists",
            "ensureDir", "read", "readBytes", "write", "writeBytes", "append", "appendBytes",
            "copy", "move", "rename", "renameWithoutExtension", "getName",
            "getNameWithoutExtension", "getExtension", "remove", "removeDir",
            "getSdcardPath", "cwd", "path", "listDir", "open"
        ]),
        floaty: entries([
            "window", "closeAll"
        ]),
        http: entries([
            "get", "post", "postJson", "postMultipart", "request"
        ]),
        images: entries([
            "save", "pixel", "read", "load", "clip", "requestScreenCapture",
            "captureScreen", "findColor", "findColorInRegion", "findColorEquals",
            "findMultiColors", "detectsColor", "detectColor", "findImage",
            "findImageInRegion"
        ]),
        keys: keyEntries,
        media: entries([
            "scanFile", "playMusic", "musicSeekTo", "pauseMusic", "resumeMusic",
            "stopMusic", "isMusicPlaying", "getMusicDuration", "getMusicCurrentPosition"
        ]),
        selector: selectorEntries,
        sensors: entries([
            "register", "unregister", "unregisterAll", "ignoresUnsupportedSensor"
        ]),
        storages: entries([
            "create", "remove"
        ]),
        threads: entries([
            "start", "shutDownAll", "currentThread", "disposable", "atomic", "lock"
        ]),
        timers: timerEntries,
        ui: entries([
            "layout", "inflate", "run", "post", "findView", "statusBarColor", "finish",
            "isUiThread", "emitter", "registerWidget", "bindingContext"
        ])
    };

    global.AutoJsAceIndices = {
        globals: [
            moduleEntry("app", "Application and intent helpers"),
            moduleEntry("automator", "Accessibility action helpers"),
            moduleEntry("colors", "Color conversion and comparison helpers"),
            moduleEntry("console", "Console logging API"),
            moduleEntry("device", "Device information and controls"),
            moduleEntry("dialogs", "Dialog helpers"),
            moduleEntry("engines", "Script engine management"),
            moduleEntry("events", "Event emitter and system event helpers"),
            moduleEntry("files", "File system helpers"),
            moduleEntry("floaty", "Floating window helpers"),
            moduleEntry("http", "HTTP client API"),
            moduleEntry("images", "Image processing helpers"),
            moduleEntry("keys", "System key helpers"),
            moduleEntry("media", "Media scan and music playback helpers"),
            moduleEntry("selector", "Accessibility selector helpers"),
            moduleEntry("sensors", "Sensor listener helpers"),
            moduleEntry("storages", "Persistent key-value storage helpers"),
            moduleEntry("threads", "Thread helpers"),
            moduleEntry("timers", "Timer helpers"),
            moduleEntry("ui", "UI DSL namespace")
        ].concat(entries([
            "sleep", "currentPackage", "currentActivity", "setClip", "getClip", "toast",
            "toastLog", "waitForActivity", "waitForPackage", "exit", "random"
        ])).concat([
            entry("context", "variable", "Android context exposed to scripts")
        ]).concat(
            appEntries,
            automatorEntries,
            selectorEntries,
            timerEntries,
            keyEntries,
            entries(["requestScreenCapture", "captureScreen", "findColor", "findImage"])
        ),
        modules: modules,
        snippets: [
            { name: "toast", snippet: "toast(${1:message});", meta: "snippet" },
            { name: "log", snippet: "console.log(${1:value});", meta: "snippet" },
            { name: "sleep", snippet: "sleep(${1:1000});", meta: "snippet" },
            { name: "click", snippet: "click(${1:x}, ${2:y});", meta: "snippet" },
            { name: "swipe", snippet: "swipe(${1:x1}, ${2:y1}, ${3:x2}, ${4:y2}, ${5:duration});", meta: "snippet" },
            { name: "requestScreenCapture", snippet: "requestScreenCapture();", meta: "snippet" },
            { name: "threads.start", snippet: "threads.start(function() {\n    ${1}\n});", meta: "snippet" },
            { name: "setTimeout", snippet: "setTimeout(function() {\n    ${1}\n}, ${2:1000});", meta: "snippet" },
            { name: "ui.layout", snippet: "ui.layout(\n    <vertical>\n        ${1}\n    </vertical>\n);", meta: "snippet" }
        ]
    };
})(window);
