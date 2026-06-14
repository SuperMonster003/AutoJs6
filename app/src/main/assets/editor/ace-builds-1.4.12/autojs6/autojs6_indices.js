(function(global) {
    "use strict";

    var indices = {
        "version": 1,
        "globals": [
            {
                "name": "app",
                "type": "module",
                "doc": "Application and intent helpers"
            },
            {
                "name": "automator",
                "type": "module",
                "doc": "Accessibility action helpers"
            },
            {
                "name": "colors",
                "type": "module",
                "doc": "Color conversion and comparison helpers"
            },
            {
                "name": "console",
                "type": "module",
                "doc": "Console logging API"
            },
            {
                "name": "device",
                "type": "module",
                "doc": "Device information and controls"
            },
            {
                "name": "dialogs",
                "type": "module",
                "doc": "Dialog helpers"
            },
            {
                "name": "engines",
                "type": "module",
                "doc": "Script engine management"
            },
            {
                "name": "events",
                "type": "module",
                "doc": "Event emitter and system event helpers"
            },
            {
                "name": "files",
                "type": "module",
                "doc": "File system helpers"
            },
            {
                "name": "floaty",
                "type": "module",
                "doc": "Floating window helpers"
            },
            {
                "name": "http",
                "type": "module",
                "doc": "HTTP client API"
            },
            {
                "name": "images",
                "type": "module",
                "doc": "Image processing helpers"
            },
            {
                "name": "keys",
                "type": "module",
                "doc": "System key helpers"
            },
            {
                "name": "media",
                "type": "module",
                "doc": "Media scan and music playback helpers"
            },
            {
                "name": "selector",
                "type": "module",
                "doc": "Accessibility selector helpers"
            },
            {
                "name": "sensors",
                "type": "module",
                "doc": "Sensor listener helpers"
            },
            {
                "name": "storages",
                "type": "module",
                "doc": "Persistent key-value storage helpers"
            },
            {
                "name": "threads",
                "type": "module",
                "doc": "Thread helpers"
            },
            {
                "name": "timers",
                "type": "module",
                "doc": "Timer helpers"
            },
            {
                "name": "ui",
                "type": "module",
                "doc": "UI DSL namespace"
            },
            {
                "name": "sleep",
                "type": "function",
                "doc": ""
            },
            {
                "name": "currentPackage",
                "type": "function",
                "doc": ""
            },
            {
                "name": "currentActivity",
                "type": "function",
                "doc": ""
            },
            {
                "name": "setClip",
                "type": "function",
                "doc": ""
            },
            {
                "name": "getClip",
                "type": "function",
                "doc": ""
            },
            {
                "name": "toast",
                "type": "function",
                "doc": ""
            },
            {
                "name": "toastLog",
                "type": "function",
                "doc": ""
            },
            {
                "name": "waitForActivity",
                "type": "function",
                "doc": ""
            },
            {
                "name": "waitForPackage",
                "type": "function",
                "doc": ""
            },
            {
                "name": "exit",
                "type": "function",
                "doc": ""
            },
            {
                "name": "random",
                "type": "function",
                "doc": ""
            },
            {
                "name": "context",
                "type": "variable",
                "doc": ""
            },
            {
                "name": "launch",
                "type": "function",
                "doc": ""
            },
            {
                "name": "launchApp",
                "type": "function",
                "doc": ""
            },
            {
                "name": "click",
                "type": "function",
                "doc": ""
            },
            {
                "name": "longClick",
                "type": "function",
                "doc": ""
            },
            {
                "name": "press",
                "type": "function",
                "doc": ""
            },
            {
                "name": "swipe",
                "type": "function",
                "doc": ""
            },
            {
                "name": "gesture",
                "type": "function",
                "doc": ""
            },
            {
                "name": "gestures",
                "type": "function",
                "doc": ""
            },
            {
                "name": "gestureAsync",
                "type": "function",
                "doc": ""
            },
            {
                "name": "gesturesAsync",
                "type": "function",
                "doc": ""
            },
            {
                "name": "scrollDown",
                "type": "function",
                "doc": ""
            },
            {
                "name": "scrollUp",
                "type": "function",
                "doc": ""
            },
            {
                "name": "input",
                "type": "function",
                "doc": ""
            },
            {
                "name": "setText",
                "type": "function",
                "doc": ""
            },
            {
                "name": "id",
                "type": "function",
                "doc": ""
            },
            {
                "name": "idContains",
                "type": "function",
                "doc": ""
            },
            {
                "name": "idStartsWith",
                "type": "function",
                "doc": ""
            },
            {
                "name": "idEndsWith",
                "type": "function",
                "doc": ""
            },
            {
                "name": "idMatches",
                "type": "function",
                "doc": ""
            },
            {
                "name": "text",
                "type": "function",
                "doc": ""
            },
            {
                "name": "textContains",
                "type": "function",
                "doc": ""
            },
            {
                "name": "textStartsWith",
                "type": "function",
                "doc": ""
            },
            {
                "name": "textEndsWith",
                "type": "function",
                "doc": ""
            },
            {
                "name": "textMatches",
                "type": "function",
                "doc": ""
            },
            {
                "name": "desc",
                "type": "function",
                "doc": ""
            },
            {
                "name": "descContains",
                "type": "function",
                "doc": ""
            },
            {
                "name": "descStartsWith",
                "type": "function",
                "doc": ""
            },
            {
                "name": "descEndsWith",
                "type": "function",
                "doc": ""
            },
            {
                "name": "descMatches",
                "type": "function",
                "doc": ""
            },
            {
                "name": "className",
                "type": "function",
                "doc": ""
            },
            {
                "name": "classNameContains",
                "type": "function",
                "doc": ""
            },
            {
                "name": "classNameStartsWith",
                "type": "function",
                "doc": ""
            },
            {
                "name": "classNameEndsWith",
                "type": "function",
                "doc": ""
            },
            {
                "name": "classNameMatches",
                "type": "function",
                "doc": ""
            },
            {
                "name": "packageName",
                "type": "function",
                "doc": ""
            },
            {
                "name": "packageNameContains",
                "type": "function",
                "doc": ""
            },
            {
                "name": "packageNameStartsWith",
                "type": "function",
                "doc": ""
            },
            {
                "name": "packageNameEndsWith",
                "type": "function",
                "doc": ""
            },
            {
                "name": "packageNameMatches",
                "type": "function",
                "doc": ""
            },
            {
                "name": "bounds",
                "type": "function",
                "doc": ""
            },
            {
                "name": "boundsInside",
                "type": "function",
                "doc": ""
            },
            {
                "name": "boundsContains",
                "type": "function",
                "doc": ""
            },
            {
                "name": "drawingOrder",
                "type": "function",
                "doc": ""
            },
            {
                "name": "checkable",
                "type": "function",
                "doc": ""
            },
            {
                "name": "checked",
                "type": "function",
                "doc": ""
            },
            {
                "name": "focusable",
                "type": "function",
                "doc": ""
            },
            {
                "name": "focused",
                "type": "function",
                "doc": ""
            },
            {
                "name": "visibleToUser",
                "type": "function",
                "doc": ""
            },
            {
                "name": "accessibilityFocused",
                "type": "function",
                "doc": ""
            },
            {
                "name": "selected",
                "type": "function",
                "doc": ""
            },
            {
                "name": "clickable",
                "type": "function",
                "doc": ""
            },
            {
                "name": "longClickable",
                "type": "function",
                "doc": ""
            },
            {
                "name": "enabled",
                "type": "function",
                "doc": ""
            },
            {
                "name": "password",
                "type": "function",
                "doc": ""
            },
            {
                "name": "scrollable",
                "type": "function",
                "doc": ""
            },
            {
                "name": "editable",
                "type": "function",
                "doc": ""
            },
            {
                "name": "contentInvalid",
                "type": "function",
                "doc": ""
            },
            {
                "name": "contextClickable",
                "type": "function",
                "doc": ""
            },
            {
                "name": "multiLine",
                "type": "function",
                "doc": ""
            },
            {
                "name": "dismissable",
                "type": "function",
                "doc": ""
            },
            {
                "name": "setTimeout",
                "type": "function",
                "doc": ""
            },
            {
                "name": "clearTimeout",
                "type": "function",
                "doc": ""
            },
            {
                "name": "setInterval",
                "type": "function",
                "doc": ""
            },
            {
                "name": "clearInterval",
                "type": "function",
                "doc": ""
            },
            {
                "name": "setImmediate",
                "type": "function",
                "doc": ""
            },
            {
                "name": "clearImmediate",
                "type": "function",
                "doc": ""
            },
            {
                "name": "back",
                "type": "function",
                "doc": ""
            },
            {
                "name": "home",
                "type": "function",
                "doc": ""
            },
            {
                "name": "powerDialog",
                "type": "function",
                "doc": ""
            },
            {
                "name": "notifications",
                "type": "function",
                "doc": ""
            },
            {
                "name": "quickSettings",
                "type": "function",
                "doc": ""
            },
            {
                "name": "recents",
                "type": "function",
                "doc": ""
            },
            {
                "name": "splitScreen",
                "type": "function",
                "doc": ""
            },
            {
                "name": "Back",
                "type": "function",
                "doc": ""
            },
            {
                "name": "Home",
                "type": "function",
                "doc": ""
            },
            {
                "name": "Power",
                "type": "function",
                "doc": ""
            },
            {
                "name": "Menu",
                "type": "function",
                "doc": ""
            },
            {
                "name": "VolumeUp",
                "type": "function",
                "doc": ""
            },
            {
                "name": "VolumeDown",
                "type": "function",
                "doc": ""
            },
            {
                "name": "Camera",
                "type": "function",
                "doc": ""
            },
            {
                "name": "Up",
                "type": "function",
                "doc": ""
            },
            {
                "name": "Down",
                "type": "function",
                "doc": ""
            },
            {
                "name": "Left",
                "type": "function",
                "doc": ""
            },
            {
                "name": "Right",
                "type": "function",
                "doc": ""
            },
            {
                "name": "OK",
                "type": "function",
                "doc": ""
            },
            {
                "name": "Text",
                "type": "function",
                "doc": ""
            },
            {
                "name": "KeyCode",
                "type": "function",
                "doc": ""
            },
            {
                "name": "requestScreenCapture",
                "type": "function",
                "doc": ""
            },
            {
                "name": "captureScreen",
                "type": "function",
                "doc": ""
            },
            {
                "name": "findColor",
                "type": "function",
                "doc": ""
            },
            {
                "name": "findImage",
                "type": "function",
                "doc": ""
            }
        ],
        "modules": {
            "app": [
                {
                    "name": "uninstall",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "viewFile",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "editFile",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "openUrl",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "launchPackage",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "launch",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "launchApp",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "getPackageName",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "getAppName",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "openAppSetting",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "openAppSettings",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "sendEmail",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "intent",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "startActivity",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "sendBroadcast",
                    "type": "function",
                    "doc": ""
                }
            ],
            "automator": [
                {
                    "name": "click",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "longClick",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "press",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "swipe",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "gesture",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "gestures",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "gestureAsync",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "gesturesAsync",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "scrollDown",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "scrollUp",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "input",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "setText",
                    "type": "function",
                    "doc": ""
                }
            ],
            "colors": [
                {
                    "name": "red",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "green",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "blue",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "alpha",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "toString",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "rgb",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "argb",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "parseColor",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "isSimilar",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "equals",
                    "type": "function",
                    "doc": ""
                }
            ],
            "console": [
                {
                    "name": "show",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "hide",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "clear",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "verbose",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "info",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "log",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "warn",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "error",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "assert",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "input",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "rawInput",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "setSize",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "setPosition",
                    "type": "function",
                    "doc": ""
                }
            ],
            "device": [
                {
                    "name": "width",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "height",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "buildId",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "broad",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "brand",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "device",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "model",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "product",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "bootloader",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "hardware",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "fingerprint",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "serial",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "sdkInt",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "incremental",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "release",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "baseOS",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "securityPatch",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "codename",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "getIMEI",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "getAndroidId",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "getMacAddress",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "getBrightness",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "getBrightnessMode",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "setBrightness",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "setBrightnessMode",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "getMusicVolume",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "getNotificationVolume",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "getAlarmVolume",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "getMusicMaxVolume",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "getNotificationMaxVolume",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "getAlarmMaxVolume",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "setMusicVolume",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "setNotificationVolume",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "setAlarmVolume",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "getBattery",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "isCharging",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "getTotalMem",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "getAvailMem",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "isScreenOn",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "wakeUp",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "wakeUpIfNeeded",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "keepScreenOn",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "keepScreenDim",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "cancelKeepingAwake",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "vibrate",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "cancelVibration",
                    "type": "function",
                    "doc": ""
                }
            ],
            "dialogs": [
                {
                    "name": "select",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "singleChoice",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "multiChoice",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "rawInput",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "input",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "alert",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "confirm",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "prompt",
                    "type": "function",
                    "doc": ""
                }
            ],
            "engines": [
                {
                    "name": "execScript",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "execScriptFile",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "execAutoFile",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "stopAll",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "stopAllAndToast",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "myEngine",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "all",
                    "type": "function",
                    "doc": ""
                }
            ],
            "events": [
                {
                    "name": "emitter",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "observeKey",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "setKeyInterceptionEnabled",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "observeTouch",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "observeNotification",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "onKeyDown",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "onKeyUp",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "onceKeyDown",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "onceKeyUp",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "onToast",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "onNotification",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "removeAllKeyDownListeners",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "removeAllKeyUpListeners",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "onTouch",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "removeAllTouchListeners",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "getTouchEventTimeout",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "setTouchEventTimeout",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "on",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "once",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "emit",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "getListeners",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "addListener",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "eventNames",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "listenerCount",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "listeners",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "prependListener",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "prependOnceListener",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "removeAllListeners",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "removeListener",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "setMaxListeners",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "getMaxListeners",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "defaultMaxListeners",
                    "type": "function",
                    "doc": ""
                }
            ],
            "files": [
                {
                    "name": "isFile",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "isDir",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "isEmptyDir",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "join",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "create",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "createWithDirs",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "exists",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "ensureDir",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "read",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "readBytes",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "write",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "writeBytes",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "append",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "appendBytes",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "copy",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "move",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "rename",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "renameWithoutExtension",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "getName",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "getNameWithoutExtension",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "getExtension",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "remove",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "removeDir",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "getSdcardPath",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "cwd",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "path",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "listDir",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "open",
                    "type": "function",
                    "doc": ""
                }
            ],
            "floaty": [
                {
                    "name": "window",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "closeAll",
                    "type": "function",
                    "doc": ""
                }
            ],
            "http": [
                {
                    "name": "get",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "post",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "postJson",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "postMultipart",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "request",
                    "type": "function",
                    "doc": ""
                }
            ],
            "images": [
                {
                    "name": "save",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "pixel",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "read",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "load",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "clip",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "requestScreenCapture",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "captureScreen",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "findColor",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "findColorInRegion",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "findColorEquals",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "findMultiColors",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "detectsColor",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "detectColor",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "findImage",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "findImageInRegion",
                    "type": "function",
                    "doc": ""
                }
            ],
            "keys": [
                {
                    "name": "back",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "home",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "powerDialog",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "notifications",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "quickSettings",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "recents",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "splitScreen",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "Back",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "Home",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "Power",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "Menu",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "VolumeUp",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "VolumeDown",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "Camera",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "Up",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "Down",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "Left",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "Right",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "OK",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "Text",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "KeyCode",
                    "type": "function",
                    "doc": ""
                }
            ],
            "media": [
                {
                    "name": "scanFile",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "playMusic",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "musicSeekTo",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "pauseMusic",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "resumeMusic",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "stopMusic",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "isMusicPlaying",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "getMusicDuration",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "getMusicCurrentPosition",
                    "type": "function",
                    "doc": ""
                }
            ],
            "selector": [
                {
                    "name": "id",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "idContains",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "idStartsWith",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "idEndsWith",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "idMatches",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "text",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "textContains",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "textStartsWith",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "textEndsWith",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "textMatches",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "desc",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "descContains",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "descStartsWith",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "descEndsWith",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "descMatches",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "className",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "classNameContains",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "classNameStartsWith",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "classNameEndsWith",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "classNameMatches",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "packageName",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "packageNameContains",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "packageNameStartsWith",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "packageNameEndsWith",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "packageNameMatches",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "bounds",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "boundsInside",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "boundsContains",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "drawingOrder",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "checkable",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "checked",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "focusable",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "focused",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "visibleToUser",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "accessibilityFocused",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "selected",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "clickable",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "longClickable",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "enabled",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "password",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "scrollable",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "editable",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "contentInvalid",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "contextClickable",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "multiLine",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "dismissable",
                    "type": "function",
                    "doc": ""
                }
            ],
            "sensors": [
                {
                    "name": "register",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "unregister",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "unregisterAll",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "ignoresUnsupportedSensor",
                    "type": "function",
                    "doc": ""
                }
            ],
            "storages": [
                {
                    "name": "create",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "remove",
                    "type": "function",
                    "doc": ""
                }
            ],
            "threads": [
                {
                    "name": "start",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "shutDownAll",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "currentThread",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "disposable",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "atomic",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "lock",
                    "type": "function",
                    "doc": ""
                }
            ],
            "timers": [
                {
                    "name": "setTimeout",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "clearTimeout",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "setInterval",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "clearInterval",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "setImmediate",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "clearImmediate",
                    "type": "function",
                    "doc": ""
                }
            ],
            "ui": [
                {
                    "name": "layout",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "inflate",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "run",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "post",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "findView",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "statusBarColor",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "finish",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "isUiThread",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "emitter",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "registerWidget",
                    "type": "function",
                    "doc": ""
                },
                {
                    "name": "bindingContext",
                    "type": "function",
                    "doc": ""
                }
            ]
        },
        "snippets": [
            {
                "name": "toast",
                "snippet": "toast(${1:message});",
                "meta": "snippet"
            },
            {
                "name": "log",
                "snippet": "console.log(${1:value});",
                "meta": "snippet"
            },
            {
                "name": "sleep",
                "snippet": "sleep(${1:1000});",
                "meta": "snippet"
            },
            {
                "name": "click",
                "snippet": "click(${1:x}, ${2:y});",
                "meta": "snippet"
            },
            {
                "name": "swipe",
                "snippet": "swipe(${1:x1}, ${2:y1}, ${3:x2}, ${4:y2}, ${5:duration});",
                "meta": "snippet"
            },
            {
                "name": "requestScreenCapture",
                "snippet": "requestScreenCapture();",
                "meta": "snippet"
            },
            {
                "name": "threads.start",
                "snippet": "threads.start(function() {\\n    ${1}\\n});",
                "meta": "snippet"
            },
            {
                "name": "setTimeout",
                "snippet": "setTimeout(function() {\\n    ${1}\\n}, ${2:1000});",
                "meta": "snippet"
            },
            {
                "name": "ui.layout",
                "snippet": "ui.layout(\\n    <vertical>\\n        ${1}\\n    </vertical>\\n);",
                "meta": "snippet"
            }
        ]
    };

    global.AUTOJS6_INDICES = indices;
    global.AutoJsAceIndices = indices;
    global.AUTOJS_INDICES = global.AUTOJS6_INDICES || global.AUTOJS_INDICES;
})(window);
