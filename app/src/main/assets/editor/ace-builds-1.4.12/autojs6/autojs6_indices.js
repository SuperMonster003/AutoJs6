(function(global) {
    "use strict";

    var indices = {
        "version": 1,
        "globals": [
            {
                "name": "activity",
                "type": "module",
                "doc": "AutoJs6 Activity API"
            },
            {
                "name": "app",
                "type": "module",
                "doc": "Application and intent helpers"
            },
            {
                "name": "Arrayx",
                "type": "module",
                "doc": "AutoJs6 Arrayx API"
            },
            {
                "name": "auto",
                "type": "module",
                "doc": "AutoJs6 Auto API"
            },
            {
                "name": "autojs",
                "type": "module",
                "doc": "AutoJs6 Autojs API"
            },
            {
                "name": "automator",
                "type": "module",
                "doc": "Accessibility action helpers"
            },
            {
                "name": "barcode",
                "type": "module",
                "doc": "AutoJs6 Barcode API"
            },
            {
                "name": "base64",
                "type": "module",
                "doc": "AutoJs6 Base64 API"
            },
            {
                "name": "Color",
                "type": "module",
                "doc": "AutoJs6 Color API"
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
                "name": "context",
                "type": "module",
                "doc": "AutoJs6 Context API"
            },
            {
                "name": "continuation",
                "type": "module",
                "doc": "AutoJs6 Continuation API"
            },
            {
                "name": "crypto",
                "type": "module",
                "doc": "AutoJs6 Crypto API"
            },
            {
                "name": "cvt",
                "type": "module",
                "doc": "AutoJs6 Converter API"
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
                "name": "fmt",
                "type": "module",
                "doc": "AutoJs6 Formatter API"
            },
            {
                "name": "http",
                "type": "module",
                "doc": "HTTP client API"
            },
            {
                "name": "i18n",
                "type": "module",
                "doc": "AutoJs6 I18n API"
            },
            {
                "name": "images",
                "type": "module",
                "doc": "Image processing helpers"
            },
            {
                "name": "jsox",
                "type": "module",
                "doc": "AutoJs6 Jsox API"
            },
            {
                "name": "keys",
                "type": "module",
                "doc": "System key helpers"
            },
            {
                "name": "Mathx",
                "type": "module",
                "doc": "AutoJs6 Mathx API"
            },
            {
                "name": "media",
                "type": "module",
                "doc": "Media scan and music playback helpers"
            },
            {
                "name": "mediainfo",
                "type": "module",
                "doc": "AutoJs6 Mediainfo API"
            },
            {
                "name": "mime",
                "type": "module",
                "doc": "AutoJs6 Mime API"
            },
            {
                "name": "notice",
                "type": "module",
                "doc": "AutoJs6 Notice API"
            },
            {
                "name": "Numberx",
                "type": "module",
                "doc": "AutoJs6 Numberx API"
            },
            {
                "name": "ocr",
                "type": "module",
                "doc": "AutoJs6 Ocr API"
            },
            {
                "name": "opencc",
                "type": "module",
                "doc": "AutoJs6 OpenCC API"
            },
            {
                "name": "pinyin",
                "type": "module",
                "doc": "AutoJs6 Pinyin API"
            },
            {
                "name": "pinyin4j",
                "type": "module",
                "doc": "AutoJs6 Pinyin4j API"
            },
            {
                "name": "plugins",
                "type": "module",
                "doc": "AutoJs6 Plugins API"
            },
            {
                "name": "qrcode",
                "type": "module",
                "doc": "AutoJs6 QrCode API"
            },
            {
                "name": "recorder",
                "type": "module",
                "doc": "AutoJs6 Recorder API"
            },
            {
                "name": "require",
                "type": "module",
                "doc": "AutoJs6 Require API"
            },
            {
                "name": "runtime",
                "type": "module",
                "doc": "AutoJs6 Runtime API"
            },
            {
                "name": "s13n",
                "type": "module",
                "doc": "AutoJs6 S13n API"
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
                "name": "shell",
                "type": "module",
                "doc": "AutoJs6 Shell API"
            },
            {
                "name": "shizuku",
                "type": "module",
                "doc": "AutoJs6 Shizuku API"
            },
            {
                "name": "sqlite",
                "type": "module",
                "doc": "AutoJs6 SQLite API"
            },
            {
                "name": "storages",
                "type": "module",
                "doc": "Persistent key-value storage helpers"
            },
            {
                "name": "sysprops",
                "type": "module",
                "doc": "AutoJs6 SysProps API"
            },
            {
                "name": "tasks",
                "type": "module",
                "doc": "AutoJs6 Tasks API"
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
                "name": "toast",
                "type": "module",
                "doc": "AutoJs6 Toast API"
            },
            {
                "name": "ui",
                "type": "module",
                "doc": "UI DSL namespace"
            },
            {
                "name": "util",
                "type": "module",
                "doc": "AutoJs6 Util API"
            },
            {
                "name": "web",
                "type": "module",
                "doc": "AutoJs6 Web API"
            },
            {
                "name": "zip",
                "type": "module",
                "doc": "AutoJs6 Zip API"
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
                "name": "launchPackage",
                "type": "function",
                "doc": ""
            },
            {
                "name": "getAppName",
                "type": "function",
                "doc": ""
            },
            {
                "name": "getPackageName",
                "type": "function",
                "doc": ""
            },
            {
                "name": "openAppSetting",
                "type": "function",
                "doc": ""
            },
            {
                "name": "launchSettings",
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
                "name": "gestureAsync",
                "type": "function",
                "doc": ""
            },
            {
                "name": "gestures",
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
                "name": "setText",
                "type": "function",
                "doc": ""
            },
            {
                "name": "input",
                "type": "function",
                "doc": ""
            },
            {
                "name": "log",
                "type": "function",
                "doc": ""
            },
            {
                "name": "print",
                "type": "function",
                "doc": ""
            },
            {
                "name": "err",
                "type": "function",
                "doc": ""
            },
            {
                "name": "warn",
                "type": "function",
                "doc": ""
            },
            {
                "name": "openConsole",
                "type": "function",
                "doc": ""
            },
            {
                "name": "showConsole",
                "type": "function",
                "doc": ""
            },
            {
                "name": "clearConsole",
                "type": "function",
                "doc": ""
            },
            {
                "name": "launchConsole",
                "type": "function",
                "doc": ""
            },
            {
                "name": "rawInput",
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
            },
            {
                "name": "open",
                "type": "function",
                "doc": ""
            },
            {
                "name": "toastVerbose",
                "type": "function",
                "doc": ""
            },
            {
                "name": "toastverbose",
                "type": "function",
                "doc": ""
            },
            {
                "name": "toastlog",
                "type": "function",
                "doc": ""
            },
            {
                "name": "toastInfo",
                "type": "function",
                "doc": ""
            },
            {
                "name": "toastinfo",
                "type": "function",
                "doc": ""
            },
            {
                "name": "toastWarn",
                "type": "function",
                "doc": ""
            },
            {
                "name": "toastwarn",
                "type": "function",
                "doc": ""
            },
            {
                "name": "toastError",
                "type": "function",
                "doc": ""
            },
            {
                "name": "toasterror",
                "type": "function",
                "doc": ""
            },
            {
                "name": "stop",
                "type": "function",
                "doc": ""
            },
            {
                "name": "isStopped",
                "type": "function",
                "doc": ""
            },
            {
                "name": "isShuttingDown",
                "type": "function",
                "doc": ""
            },
            {
                "name": "notStopped",
                "type": "function",
                "doc": ""
            },
            {
                "name": "isRunning",
                "type": "function",
                "doc": ""
            },
            {
                "name": "currentComponent",
                "type": "function",
                "doc": ""
            },
            {
                "name": "setScreenMetrics",
                "type": "function",
                "doc": ""
            },
            {
                "name": "requiresApi",
                "type": "function",
                "doc": ""
            },
            {
                "name": "requiresAutojsVersion",
                "type": "function",
                "doc": ""
            },
            {
                "name": "getScaleBases",
                "type": "function",
                "doc": ""
            },
            {
                "name": "getScaleBaseX",
                "type": "function",
                "doc": ""
            },
            {
                "name": "getScaleBaseY",
                "type": "function",
                "doc": ""
            },
            {
                "name": "setScaleBases",
                "type": "function",
                "doc": ""
            },
            {
                "name": "setScaleBaseX",
                "type": "function",
                "doc": ""
            },
            {
                "name": "setScaleBaseY",
                "type": "function",
                "doc": ""
            },
            {
                "name": "cX",
                "type": "function",
                "doc": ""
            },
            {
                "name": "cY",
                "type": "function",
                "doc": ""
            },
            {
                "name": "cYx",
                "type": "function",
                "doc": ""
            },
            {
                "name": "cXy",
                "type": "function",
                "doc": ""
            },
            {
                "name": "wait",
                "type": "function",
                "doc": ""
            },
            {
                "name": "isObjectSpecies",
                "type": "function",
                "doc": ""
            },
            {
                "name": "TODO",
                "type": "function",
                "doc": ""
            },
            {
                "name": "unwrapJavaObject",
                "type": "function",
                "doc": ""
            },
            {
                "name": "isJavaObject",
                "type": "function",
                "doc": ""
            },
            {
                "name": "isPrimitive",
                "type": "function",
                "doc": ""
            },
            {
                "name": "isReference",
                "type": "function",
                "doc": ""
            },
            {
                "name": "isEmptyObject",
                "type": "function",
                "doc": ""
            },
            {
                "name": "isInteger",
                "type": "function",
                "doc": ""
            },
            {
                "name": "isBigInt",
                "type": "function",
                "doc": ""
            },
            {
                "name": "requestScreenCapture",
                "type": "function",
                "doc": ""
            },
            {
                "name": "requestScreenCaptureAsync",
                "type": "function",
                "doc": ""
            },
            {
                "name": "captureScreen",
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
            },
            {
                "name": "findColor",
                "type": "function",
                "doc": ""
            },
            {
                "name": "findColorEquals",
                "type": "function",
                "doc": ""
            },
            {
                "name": "findColorInRegion",
                "type": "function",
                "doc": ""
            },
            {
                "name": "findMultiColors",
                "type": "function",
                "doc": ""
            },
            {
                "name": "isNullish",
                "type": "function",
                "doc": ""
            },
            {
                "name": "isObject",
                "type": "function",
                "doc": ""
            },
            {
                "name": "isJavaClass",
                "type": "function",
                "doc": ""
            },
            {
                "name": "isJavaPackage",
                "type": "function",
                "doc": ""
            },
            {
                "name": "selector",
                "type": "function",
                "doc": ""
            },
            {
                "name": "importClass",
                "type": "function",
                "doc": ""
            },
            {
                "name": "importPackage",
                "type": "function",
                "doc": ""
            },
            {
                "name": "getClass",
                "type": "function",
                "doc": ""
            },
            {
                "name": "pickup",
                "type": "function",
                "doc": ""
            },
            {
                "name": "detect",
                "type": "function",
                "doc": ""
            },
            {
                "name": "existsAll",
                "type": "function",
                "doc": ""
            },
            {
                "name": "existsOne",
                "type": "function",
                "doc": ""
            },
            {
                "name": "accessibilityFocus",
                "type": "function",
                "doc": ""
            },
            {
                "name": "clearAccessibilityFocus",
                "type": "function",
                "doc": ""
            },
            {
                "name": "clearFocus",
                "type": "function",
                "doc": ""
            },
            {
                "name": "collapse",
                "type": "function",
                "doc": ""
            },
            {
                "name": "contextClick",
                "type": "function",
                "doc": ""
            },
            {
                "name": "copy",
                "type": "function",
                "doc": ""
            },
            {
                "name": "cut",
                "type": "function",
                "doc": ""
            },
            {
                "name": "dismiss",
                "type": "function",
                "doc": ""
            },
            {
                "name": "exists",
                "type": "function",
                "doc": ""
            },
            {
                "name": "expand",
                "type": "function",
                "doc": ""
            },
            {
                "name": "find",
                "type": "function",
                "doc": ""
            },
            {
                "name": "findAndReturnList",
                "type": "function",
                "doc": ""
            },
            {
                "name": "findOf",
                "type": "function",
                "doc": ""
            },
            {
                "name": "findOnce",
                "type": "function",
                "doc": ""
            },
            {
                "name": "findOne",
                "type": "function",
                "doc": ""
            },
            {
                "name": "focus",
                "type": "function",
                "doc": ""
            },
            {
                "name": "paste",
                "type": "function",
                "doc": ""
            },
            {
                "name": "progress",
                "type": "function",
                "doc": ""
            },
            {
                "name": "selection",
                "type": "function",
                "doc": ""
            },
            {
                "name": "scrollBackward",
                "type": "function",
                "doc": ""
            },
            {
                "name": "scrollForward",
                "type": "function",
                "doc": ""
            },
            {
                "name": "scrollLeft",
                "type": "function",
                "doc": ""
            },
            {
                "name": "scrollRight",
                "type": "function",
                "doc": ""
            },
            {
                "name": "scrollTo",
                "type": "function",
                "doc": ""
            },
            {
                "name": "select",
                "type": "function",
                "doc": ""
            },
            {
                "name": "setProgress",
                "type": "function",
                "doc": ""
            },
            {
                "name": "setSelection",
                "type": "function",
                "doc": ""
            },
            {
                "name": "show",
                "type": "function",
                "doc": ""
            },
            {
                "name": "untilFind",
                "type": "function",
                "doc": ""
            },
            {
                "name": "untilFindOne",
                "type": "function",
                "doc": ""
            },
            {
                "name": "waitFor",
                "type": "function",
                "doc": ""
            },
            {
                "name": "accessibilityFocused",
                "type": "function",
                "doc": ""
            },
            {
                "name": "action",
                "type": "function",
                "doc": ""
            },
            {
                "name": "algorithm",
                "type": "function",
                "doc": ""
            },
            {
                "name": "bottom",
                "type": "function",
                "doc": ""
            },
            {
                "name": "bounds",
                "type": "function",
                "doc": ""
            },
            {
                "name": "boundsBottom",
                "type": "function",
                "doc": ""
            },
            {
                "name": "boundsCenterX",
                "type": "function",
                "doc": ""
            },
            {
                "name": "boundsCenterY",
                "type": "function",
                "doc": ""
            },
            {
                "name": "boundsContains",
                "type": "function",
                "doc": ""
            },
            {
                "name": "boundsHeight",
                "type": "function",
                "doc": ""
            },
            {
                "name": "boundsInside",
                "type": "function",
                "doc": ""
            },
            {
                "name": "boundsLeft",
                "type": "function",
                "doc": ""
            },
            {
                "name": "boundsMaxBottom",
                "type": "function",
                "doc": ""
            },
            {
                "name": "boundsMaxCenterX",
                "type": "function",
                "doc": ""
            },
            {
                "name": "boundsMaxCenterY",
                "type": "function",
                "doc": ""
            },
            {
                "name": "boundsMaxHeight",
                "type": "function",
                "doc": ""
            },
            {
                "name": "boundsMaxLeft",
                "type": "function",
                "doc": ""
            },
            {
                "name": "boundsMaxRight",
                "type": "function",
                "doc": ""
            },
            {
                "name": "boundsMaxTop",
                "type": "function",
                "doc": ""
            },
            {
                "name": "boundsMaxWidth",
                "type": "function",
                "doc": ""
            },
            {
                "name": "boundsMinBottom",
                "type": "function",
                "doc": ""
            },
            {
                "name": "boundsMinCenterX",
                "type": "function",
                "doc": ""
            },
            {
                "name": "boundsMinCenterY",
                "type": "function",
                "doc": ""
            },
            {
                "name": "boundsMinHeight",
                "type": "function",
                "doc": ""
            },
            {
                "name": "boundsMinLeft",
                "type": "function",
                "doc": ""
            },
            {
                "name": "boundsMinRight",
                "type": "function",
                "doc": ""
            },
            {
                "name": "boundsMinTop",
                "type": "function",
                "doc": ""
            },
            {
                "name": "boundsMinWidth",
                "type": "function",
                "doc": ""
            },
            {
                "name": "boundsRight",
                "type": "function",
                "doc": ""
            },
            {
                "name": "boundsTop",
                "type": "function",
                "doc": ""
            },
            {
                "name": "boundsWidth",
                "type": "function",
                "doc": ""
            },
            {
                "name": "centerX",
                "type": "function",
                "doc": ""
            },
            {
                "name": "centerY",
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
                "name": "classNameEndsWith",
                "type": "function",
                "doc": ""
            },
            {
                "name": "classNameMatch",
                "type": "function",
                "doc": ""
            },
            {
                "name": "classNameMatches",
                "type": "function",
                "doc": ""
            },
            {
                "name": "classNameStartsWith",
                "type": "function",
                "doc": ""
            },
            {
                "name": "clickable",
                "type": "function",
                "doc": ""
            },
            {
                "name": "column",
                "type": "function",
                "doc": ""
            },
            {
                "name": "columnCount",
                "type": "function",
                "doc": ""
            },
            {
                "name": "columnSpan",
                "type": "function",
                "doc": ""
            },
            {
                "name": "content",
                "type": "function",
                "doc": ""
            },
            {
                "name": "contentContains",
                "type": "function",
                "doc": ""
            },
            {
                "name": "contentEndsWith",
                "type": "function",
                "doc": ""
            },
            {
                "name": "contentInvalid",
                "type": "function",
                "doc": ""
            },
            {
                "name": "contentMatch",
                "type": "function",
                "doc": ""
            },
            {
                "name": "contentMatches",
                "type": "function",
                "doc": ""
            },
            {
                "name": "contentStartsWith",
                "type": "function",
                "doc": ""
            },
            {
                "name": "contextClickable",
                "type": "function",
                "doc": ""
            },
            {
                "name": "currentApp",
                "type": "function",
                "doc": ""
            },
            {
                "name": "depth",
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
                "name": "descEndsWith",
                "type": "function",
                "doc": ""
            },
            {
                "name": "descMatch",
                "type": "function",
                "doc": ""
            },
            {
                "name": "descMatches",
                "type": "function",
                "doc": ""
            },
            {
                "name": "descStartsWith",
                "type": "function",
                "doc": ""
            },
            {
                "name": "dismissable",
                "type": "function",
                "doc": ""
            },
            {
                "name": "drawingOrder",
                "type": "function",
                "doc": ""
            },
            {
                "name": "editable",
                "type": "function",
                "doc": ""
            },
            {
                "name": "enabled",
                "type": "function",
                "doc": ""
            },
            {
                "name": "filter",
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
                "name": "height",
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
                "name": "idEndsWith",
                "type": "function",
                "doc": ""
            },
            {
                "name": "idHex",
                "type": "function",
                "doc": ""
            },
            {
                "name": "idMatch",
                "type": "function",
                "doc": ""
            },
            {
                "name": "idMatches",
                "type": "function",
                "doc": ""
            },
            {
                "name": "idStartsWith",
                "type": "function",
                "doc": ""
            },
            {
                "name": "indexInParent",
                "type": "function",
                "doc": ""
            },
            {
                "name": "left",
                "type": "function",
                "doc": ""
            },
            {
                "name": "longClickable",
                "type": "function",
                "doc": ""
            },
            {
                "name": "maxBottom",
                "type": "function",
                "doc": ""
            },
            {
                "name": "maxCenterX",
                "type": "function",
                "doc": ""
            },
            {
                "name": "maxCenterY",
                "type": "function",
                "doc": ""
            },
            {
                "name": "maxHeight",
                "type": "function",
                "doc": ""
            },
            {
                "name": "maxLeft",
                "type": "function",
                "doc": ""
            },
            {
                "name": "maxRight",
                "type": "function",
                "doc": ""
            },
            {
                "name": "maxTop",
                "type": "function",
                "doc": ""
            },
            {
                "name": "maxWidth",
                "type": "function",
                "doc": ""
            },
            {
                "name": "minBottom",
                "type": "function",
                "doc": ""
            },
            {
                "name": "minCenterX",
                "type": "function",
                "doc": ""
            },
            {
                "name": "minCenterY",
                "type": "function",
                "doc": ""
            },
            {
                "name": "minHeight",
                "type": "function",
                "doc": ""
            },
            {
                "name": "minLeft",
                "type": "function",
                "doc": ""
            },
            {
                "name": "minRight",
                "type": "function",
                "doc": ""
            },
            {
                "name": "minTop",
                "type": "function",
                "doc": ""
            },
            {
                "name": "minWidth",
                "type": "function",
                "doc": ""
            },
            {
                "name": "multiLine",
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
                "name": "packageNameEndsWith",
                "type": "function",
                "doc": ""
            },
            {
                "name": "packageNameMatch",
                "type": "function",
                "doc": ""
            },
            {
                "name": "packageNameMatches",
                "type": "function",
                "doc": ""
            },
            {
                "name": "packageNameStartsWith",
                "type": "function",
                "doc": ""
            },
            {
                "name": "password",
                "type": "function",
                "doc": ""
            },
            {
                "name": "right",
                "type": "function",
                "doc": ""
            },
            {
                "name": "row",
                "type": "function",
                "doc": ""
            },
            {
                "name": "rowCount",
                "type": "function",
                "doc": ""
            },
            {
                "name": "rowSpan",
                "type": "function",
                "doc": ""
            },
            {
                "name": "screenCenterX",
                "type": "function",
                "doc": ""
            },
            {
                "name": "screenCenterY",
                "type": "function",
                "doc": ""
            },
            {
                "name": "screenCoverage",
                "type": "function",
                "doc": ""
            },
            {
                "name": "scrollable",
                "type": "function",
                "doc": ""
            },
            {
                "name": "selected",
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
                "name": "textEndsWith",
                "type": "function",
                "doc": ""
            },
            {
                "name": "textMatch",
                "type": "function",
                "doc": ""
            },
            {
                "name": "textMatches",
                "type": "function",
                "doc": ""
            },
            {
                "name": "textStartsWith",
                "type": "function",
                "doc": ""
            },
            {
                "name": "top",
                "type": "function",
                "doc": ""
            },
            {
                "name": "visibleToUser",
                "type": "function",
                "doc": ""
            },
            {
                "name": "width",
                "type": "function",
                "doc": ""
            },
            {
                "name": "SetScreenMetrics",
                "type": "function",
                "doc": ""
            },
            {
                "name": "Tap",
                "type": "function",
                "doc": ""
            },
            {
                "name": "Swipe",
                "type": "function",
                "doc": ""
            },
            {
                "name": "Screencap",
                "type": "function",
                "doc": ""
            },
            {
                "name": "KeyCode",
                "type": "function",
                "doc": ""
            },
            {
                "name": "Home",
                "type": "function",
                "doc": ""
            },
            {
                "name": "Back",
                "type": "function",
                "doc": ""
            },
            {
                "name": "Power",
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
                "name": "Menu",
                "type": "function",
                "doc": ""
            },
            {
                "name": "Camera",
                "type": "function",
                "doc": ""
            },
            {
                "name": "Text",
                "type": "function",
                "doc": ""
            },
            {
                "name": "Input",
                "type": "function",
                "doc": ""
            },
            {
                "name": "loop",
                "type": "function",
                "doc": ""
            },
            {
                "name": "keepAlive",
                "type": "function",
                "doc": ""
            },
            {
                "name": "clearImmediate",
                "type": "function",
                "doc": ""
            },
            {
                "name": "clearInterval",
                "type": "function",
                "doc": ""
            },
            {
                "name": "clearTimeout",
                "type": "function",
                "doc": ""
            },
            {
                "name": "setImmediate",
                "type": "function",
                "doc": ""
            },
            {
                "name": "setTimeout",
                "type": "function",
                "doc": ""
            },
            {
                "name": "setInterval",
                "type": "function",
                "doc": ""
            },
            {
                "name": "isUiThread",
                "type": "function",
                "doc": ""
            },
            {
                "name": "newInjectableWebClient",
                "type": "function",
                "doc": ""
            },
            {
                "name": "newInjectableWebView",
                "type": "function",
                "doc": ""
            },
            {
                "name": "newWebSocket",
                "type": "function",
                "doc": ""
            },
            {
                "name": "context",
                "type": "variable",
                "doc": ""
            },
            {
                "name": "isAutoJs6",
                "type": "variable",
                "doc": ""
            },
            {
                "name": "HEIGHT",
                "type": "variable",
                "doc": ""
            },
            {
                "name": "WIDTH",
                "type": "variable",
                "doc": ""
            },
            {
                "name": "axios",
                "type": "variable",
                "doc": ""
            },
            {
                "name": "cheerio",
                "type": "variable",
                "doc": ""
            },
            {
                "name": "dayjs",
                "type": "variable",
                "doc": ""
            },
            {
                "name": "species",
                "type": "variable",
                "doc": ""
            },
            {
                "name": "JavaAdapter",
                "type": "variable",
                "doc": ""
            },
            {
                "name": "Packages",
                "type": "variable",
                "doc": ""
            },
            {
                "name": "Error",
                "type": "variable",
                "doc": ""
            },
            {
                "name": "isServiceRunning",
                "type": "function",
                "doc": "isServiceRunning(): boolean;"
            },
            {
                "name": "ensureService",
                "type": "function",
                "doc": "ensureService(): void;"
            },
            {
                "name": "waitForService",
                "type": "function",
                "doc": "waitForService(timeout?: number): void;"
            },
            {
                "name": "lockScreen",
                "type": "function",
                "doc": "lockScreen(): boolean;"
            },
            {
                "name": "takeScreenshot",
                "type": "function",
                "doc": "takeScreenshot(): boolean;"
            },
            {
                "name": "headsethook",
                "type": "function",
                "doc": "headsethook(): boolean;"
            },
            {
                "name": "accessibilityButton",
                "type": "function",
                "doc": "accessibilityButton(): boolean;"
            },
            {
                "name": "accessibilityButtonChooser",
                "type": "function",
                "doc": "accessibilityButtonChooser(): boolean;"
            },
            {
                "name": "accessibilityShortcut",
                "type": "function",
                "doc": "accessibilityShortcut(): boolean;"
            },
            {
                "name": "accessibilityAllApps",
                "type": "function",
                "doc": "accessibilityAllApps(): boolean;"
            },
            {
                "name": "dismissNotificationShade",
                "type": "function",
                "doc": "dismissNotificationShade(): boolean;"
            },
            {
                "name": "setIntervalExt",
                "type": "function",
                "doc": "setIntervalExt(timeoutCallback: Function, interval?: number, timeout?: number | (() => any), resultCallback?: (result: any) => void): number;"
            },
            {
                "name": "home",
                "type": "variable",
                "doc": "home: number;"
            },
            {
                "name": "HOME",
                "type": "variable",
                "doc": "HOME: number;"
            },
            {
                "name": "menu",
                "type": "variable",
                "doc": "menu: number;"
            },
            {
                "name": "MENU",
                "type": "variable",
                "doc": "MENU: number;"
            },
            {
                "name": "back",
                "type": "variable",
                "doc": "back: number;"
            },
            {
                "name": "BACK",
                "type": "variable",
                "doc": "BACK: number;"
            },
            {
                "name": "volumeUp",
                "type": "variable",
                "doc": "volumeUp: number;"
            },
            {
                "name": "volume_up",
                "type": "variable",
                "doc": "volume_up: number;"
            },
            {
                "name": "VOLUME_UP",
                "type": "variable",
                "doc": "VOLUME_UP: number;"
            },
            {
                "name": "volumeDown",
                "type": "variable",
                "doc": "volumeDown: number;"
            },
            {
                "name": "volume_down",
                "type": "variable",
                "doc": "volume_down: number;"
            },
            {
                "name": "VOLUME_DOWN",
                "type": "variable",
                "doc": "VOLUME_DOWN: number;"
            }
        ],
        "modules": {
            "activity": [
                {
                    "name": "window",
                    "type": "variable",
                    "doc": "window: Activity.PhoneWindow;"
                }
            ],
            "app": [
                {
                    "name": "autojs",
                    "type": "variable",
                    "doc": "autojs: Internal.Autojs;"
                },
                {
                    "name": "versionCode",
                    "type": "variable",
                    "doc": "versionCode: number;"
                },
                {
                    "name": "versionName",
                    "type": "variable",
                    "doc": "versionName: string;"
                },
                {
                    "name": "fileProviderAuthority",
                    "type": "variable",
                    "doc": "fileProviderAuthority: 'org.autojs.autojs.fileprovider' | string | null;"
                },
                {
                    "name": "intent",
                    "type": "function",
                    "doc": "intent(o: Intent.Common | Intent): Intent;"
                },
                {
                    "name": "startActivity",
                    "type": "function",
                    "doc": "startActivity(o: Intent.CommonWithRoot | Intent.ShortForm.Activity | Intent | URI): void;"
                },
                {
                    "name": "sendBroadcast",
                    "type": "function",
                    "doc": "sendBroadcast(i: Intent.CommonWithRoot | Intent.ShortForm.Broadcast): void;"
                },
                {
                    "name": "startService",
                    "type": "function",
                    "doc": "startService(i: Intent.CommonWithRoot): void;"
                },
                {
                    "name": "sendEmail",
                    "type": "function",
                    "doc": "sendEmail(options?: Intent.Email): void;"
                },
                {
                    "name": "parseUri",
                    "type": "function",
                    "doc": "parseUri(uri: string | URI): android.net.Uri | null;"
                },
                {
                    "name": "getUriForFile",
                    "type": "function",
                    "doc": "getUriForFile(path: string): string;"
                },
                {
                    "name": "getAppByAlias",
                    "type": "function",
                    "doc": "getAppByAlias(alias: App.Alias | string): App;"
                },
                {
                    "name": "launch",
                    "type": "function",
                    "doc": "launch(app: App | App.Alias | App.PackageName): boolean;"
                },
                {
                    "name": "intentToShell",
                    "type": "function",
                    "doc": "intentToShell(i: Intent.Common): string;"
                },
                {
                    "name": "openUrl",
                    "type": "function",
                    "doc": "openUrl(url: string | URI): void;"
                },
                {
                    "name": "launchApp",
                    "type": "function",
                    "doc": "launchApp(app: App | App.Alias | App.AppName): boolean;"
                },
                {
                    "name": "uninstall",
                    "type": "function",
                    "doc": "uninstall(app: App | App.Alias | App.PackageName): void;"
                },
                {
                    "name": "getAppName",
                    "type": "function",
                    "doc": "getAppName(app: App | App.Alias | App.PackageName): string;"
                },
                {
                    "name": "getCurrentActivity",
                    "type": "function",
                    "doc": "getCurrentActivity(): android.app.Activity;"
                },
                {
                    "name": "editFile",
                    "type": "function",
                    "doc": "editFile(path: string): boolean;"
                },
                {
                    "name": "viewFile",
                    "type": "function",
                    "doc": "viewFile(path: string): boolean;"
                },
                {
                    "name": "getPackageName",
                    "type": "function",
                    "doc": "getPackageName(app: App | App.Alias | App.AppName): string;"
                },
                {
                    "name": "setCurrentActivity",
                    "type": "function",
                    "doc": "setCurrentActivity(currentActivity: android.app.Activity): void;"
                },
                {
                    "name": "launchPackage",
                    "type": "function",
                    "doc": "launchPackage(app: App | App.Alias | App.PackageName): boolean;"
                },
                {
                    "name": "sendLocalBroadcastSync",
                    "type": "function",
                    "doc": "sendLocalBroadcastSync(intent: Intent);"
                },
                {
                    "name": "openAppSetting",
                    "type": "function",
                    "doc": "openAppSetting(app: App | App.Alias | App.PackageName): boolean;"
                },
                {
                    "name": "launchSettings",
                    "type": "function",
                    "doc": "launchSettings(app: App | App.Alias | App.PackageName): boolean;"
                },
                {
                    "name": "getFileProviderAuthority",
                    "type": "function",
                    "doc": "getFileProviderAuthority(): string;"
                },
                {
                    "name": "openAppSettings",
                    "type": "function",
                    "doc": ""
                }
            ],
            "Arrayx": [
                {
                    "name": "ensureArray",
                    "type": "function",
                    "doc": "ensureArray(...o: any[]);"
                },
                {
                    "name": "intersect",
                    "type": "function",
                    "doc": "intersect<T, U>(arr: T[], ...others: U[][]): (T & U)[];"
                },
                {
                    "name": "union",
                    "type": "function",
                    "doc": "union<T, U>(arr: T[], ...others: U[][]): (T | U)[];"
                },
                {
                    "name": "distinct",
                    "type": "function",
                    "doc": "distinct<T>(arr: T[]): T[];"
                },
                {
                    "name": "distinctBy",
                    "type": "function",
                    "doc": "distinctBy<T, U>(arr: T[], selector: (T) => U): T[];"
                },
                {
                    "name": "sortBy",
                    "type": "function",
                    "doc": "sortBy<T, U>(arr: T[], selector: (T) => U): T[];"
                },
                {
                    "name": "sortDescending",
                    "type": "function",
                    "doc": "sortDescending<T, U>(arr: T[]): T[];"
                },
                {
                    "name": "sortByDescending",
                    "type": "function",
                    "doc": "sortByDescending<T, U>(arr: T[], selector: (T) => U): T[];"
                },
                {
                    "name": "sorted",
                    "type": "function",
                    "doc": "sorted<T>(arr: T[]): T[];"
                },
                {
                    "name": "sortedBy",
                    "type": "function",
                    "doc": "sortedBy<T, U>(arr: T[], selector: (T) => U): T[];"
                },
                {
                    "name": "sortedDescending",
                    "type": "function",
                    "doc": "sortedDescending<T, U>(arr: T[]): T[];"
                },
                {
                    "name": "sortedByDescending",
                    "type": "function",
                    "doc": "sortedByDescending<T, U>(arr: T[], selector: (T) => U): T[];"
                },
                {
                    "name": "shuffle",
                    "type": "function",
                    "doc": "shuffle(arr: any[]);"
                }
            ],
            "auto": [
                {
                    "name": "start",
                    "type": "function",
                    "doc": "start(): boolean;"
                },
                {
                    "name": "enable",
                    "type": "function",
                    "doc": "enable(): boolean;"
                },
                {
                    "name": "stop",
                    "type": "function",
                    "doc": "stop(): boolean;"
                },
                {
                    "name": "disable",
                    "type": "function",
                    "doc": "disable(): boolean;"
                },
                {
                    "name": "hasInstance",
                    "type": "function",
                    "doc": "hasInstance(): boolean;"
                },
                {
                    "name": "hasService",
                    "type": "function",
                    "doc": "hasService(): boolean;"
                },
                {
                    "name": "exists",
                    "type": "function",
                    "doc": "exists(): boolean;"
                },
                {
                    "name": "isRunning",
                    "type": "function",
                    "doc": "isRunning(): boolean;"
                },
                {
                    "name": "isOperational",
                    "type": "function",
                    "doc": "isOperational(): boolean;"
                },
                {
                    "name": "stateListener",
                    "type": "function",
                    "doc": "stateListener(listener?: Automator.StateListener | null): void;"
                },
                {
                    "name": "registerEvent",
                    "type": "function",
                    "doc": "registerEvent(name: string, listener?: Automator.AccessibilityEventListener | null): void;"
                },
                {
                    "name": "registerEvents",
                    "type": "function",
                    "doc": "registerEvents(name: string, listener?: Automator.AccessibilityEventListener | null): void;"
                },
                {
                    "name": "removeEvent",
                    "type": "function",
                    "doc": "removeEvent(name: string): void;"
                },
                {
                    "name": "removeEvents",
                    "type": "function",
                    "doc": "removeEvents(name: string): void;"
                },
                {
                    "name": "service",
                    "type": "variable",
                    "doc": "get service(): org.autojs.autojs.core.accessibility.AccessibilityService | null;"
                },
                {
                    "name": "services",
                    "type": "variable",
                    "doc": "get services(): string[];"
                },
                {
                    "name": "windows",
                    "type": "variable",
                    "doc": "get windows(): android.view.accessibility.AccessibilityWindowInfo[];"
                },
                {
                    "name": "root",
                    "type": "variable",
                    "doc": "get root(): UiObject | null;"
                },
                {
                    "name": "rootInActiveWindow",
                    "type": "variable",
                    "doc": "get rootInActiveWindow(): UiObject | null;"
                },
                {
                    "name": "windowRoots",
                    "type": "variable",
                    "doc": "get windowRoots(): UiObject[];"
                },
                {
                    "name": "state",
                    "type": "variable",
                    "doc": "get state(): Automator.AutoState;"
                },
                {
                    "name": "waitFor",
                    "type": "function",
                    "doc": "waitFor(timeout?: number | null): void;"
                },
                {
                    "name": "setMode",
                    "type": "function",
                    "doc": "setMode(modeStr: Automator.Mode): void;"
                },
                {
                    "name": "setFlags",
                    "type": "function",
                    "doc": "setFlags(flags: Automator.Flags | Automator.Flags[]): void;"
                },
                {
                    "name": "setWindowFilter",
                    "type": "function",
                    "doc": "setWindowFilter(filter?: Automator.WindowFilterLike | null): void;"
                },
                {
                    "name": "launchSettings",
                    "type": "function",
                    "doc": "launchSettings(): void;"
                },
                {
                    "name": "clearCache",
                    "type": "function",
                    "doc": "clearCache(): boolean;"
                },
                {
                    "name": "currentPackage",
                    "type": "function",
                    "doc": "currentPackage(): string;"
                },
                {
                    "name": "currentActivity",
                    "type": "function",
                    "doc": "currentActivity(): string;"
                },
                {
                    "name": "currentComponent",
                    "type": "function",
                    "doc": "currentComponent(): string;"
                }
            ],
            "autojs": [
                {
                    "name": "versionCode",
                    "type": "variable",
                    "doc": "versionCode: string;"
                },
                {
                    "name": "versionName",
                    "type": "variable",
                    "doc": "versionName: string;"
                },
                {
                    "name": "versionDate",
                    "type": "variable",
                    "doc": "versionDate: string;"
                },
                {
                    "name": "name",
                    "type": "variable",
                    "doc": "name: string;"
                },
                {
                    "name": "packageName",
                    "type": "variable",
                    "doc": "packageName: string;"
                },
                {
                    "name": "R",
                    "type": "variable",
                    "doc": "R: org.autojs.autojs6.R;"
                },
                {
                    "name": "version",
                    "type": "variable",
                    "doc": "version: AutojsVersion;"
                },
                {
                    "name": "setRootMode",
                    "type": "function",
                    "doc": "setRootMode(mode: number | boolean | 'auto' | 'root' | 'non-root', isWriteIntoPreference?: boolean | 'write_into_pref'): void;"
                },
                {
                    "name": "getRootMode",
                    "type": "function",
                    "doc": "getRootMode(): org.autojs.autojs.util.RootUtils.RootMode;"
                },
                {
                    "name": "isRootAvailable",
                    "type": "function",
                    "doc": "isRootAvailable(): boolean;"
                },
                {
                    "name": "canModifySystemSettings",
                    "type": "function",
                    "doc": "canModifySystemSettings(): boolean;"
                },
                {
                    "name": "canWriteSecureSettings",
                    "type": "function",
                    "doc": "canWriteSecureSettings(): boolean;"
                },
                {
                    "name": "canDisplayOverOtherApps",
                    "type": "function",
                    "doc": "canDisplayOverOtherApps(): boolean;"
                },
                {
                    "name": "getLanguage",
                    "type": "function",
                    "doc": "getLanguage(): java.util.Locale;"
                },
                {
                    "name": "getLanguageTag",
                    "type": "function",
                    "doc": "getLanguageTag(): string;"
                },
                {
                    "name": "themeColor",
                    "type": "variable",
                    "doc": "get themeColor(): org.autojs.autojs.theme.ThemeColor;"
                }
            ],
            "automator": [
                {
                    "name": "click",
                    "type": "function",
                    "doc": "click(x: number, y: number): boolean;"
                },
                {
                    "name": "longClick",
                    "type": "function",
                    "doc": "longClick(x: number, y: number): boolean;"
                },
                {
                    "name": "press",
                    "type": "function",
                    "doc": "press(x: number, y: number, duration?: number): boolean;"
                },
                {
                    "name": "swipe",
                    "type": "function",
                    "doc": "swipe(x1: number, y1: number, x2: number, y2: number, duration: number): boolean;"
                },
                {
                    "name": "gesture",
                    "type": "function",
                    "doc": "gesture(duration: number, ...point: [x: number, y: number][]): boolean;"
                },
                {
                    "name": "gestureAsync",
                    "type": "function",
                    "doc": "gestureAsync(duration: number, point: [x: number, y: number], callback: GestureResultCallbackLike): void;"
                },
                {
                    "name": "gestures",
                    "type": "function",
                    "doc": "gestures(...stroke: StrokeParams[]): boolean;"
                },
                {
                    "name": "gesturesAsync",
                    "type": "function",
                    "doc": "gesturesAsync(stroke: StrokeParams, callback: GestureResultCallbackLike): void;"
                },
                {
                    "name": "scrollDown",
                    "type": "function",
                    "doc": "scrollDown(): boolean;"
                },
                {
                    "name": "scrollUp",
                    "type": "function",
                    "doc": "scrollUp(): boolean;"
                },
                {
                    "name": "setText",
                    "type": "function",
                    "doc": "setText(text: string): boolean;"
                },
                {
                    "name": "input",
                    "type": "function",
                    "doc": "input(text: string): boolean;"
                },
                {
                    "name": "captureScreen",
                    "type": "function",
                    "doc": "captureScreen(): ImageWrapper;"
                },
                {
                    "name": "isServiceRunning",
                    "type": "function",
                    "doc": "isServiceRunning(): boolean;"
                },
                {
                    "name": "ensureService",
                    "type": "function",
                    "doc": "ensureService(): void;"
                },
                {
                    "name": "waitForService",
                    "type": "function",
                    "doc": "waitForService(timeout?: number): void;"
                },
                {
                    "name": "lockScreen",
                    "type": "function",
                    "doc": "lockScreen(): boolean;"
                },
                {
                    "name": "takeScreenshot",
                    "type": "function",
                    "doc": "takeScreenshot(): boolean;"
                },
                {
                    "name": "headsethook",
                    "type": "function",
                    "doc": "headsethook(): boolean;"
                },
                {
                    "name": "accessibilityButton",
                    "type": "function",
                    "doc": "accessibilityButton(): boolean;"
                },
                {
                    "name": "accessibilityButtonChooser",
                    "type": "function",
                    "doc": "accessibilityButtonChooser(): boolean;"
                },
                {
                    "name": "accessibilityShortcut",
                    "type": "function",
                    "doc": "accessibilityShortcut(): boolean;"
                },
                {
                    "name": "accessibilityAllApps",
                    "type": "function",
                    "doc": "accessibilityAllApps(): boolean;"
                },
                {
                    "name": "dismissNotificationShade",
                    "type": "function",
                    "doc": "dismissNotificationShade(): boolean;"
                },
                {
                    "name": "back",
                    "type": "function",
                    "doc": "back(): boolean;"
                },
                {
                    "name": "home",
                    "type": "function",
                    "doc": "home(): boolean;"
                },
                {
                    "name": "powerDialog",
                    "type": "function",
                    "doc": "powerDialog(): boolean;"
                },
                {
                    "name": "notifications",
                    "type": "function",
                    "doc": "notifications(): boolean;"
                },
                {
                    "name": "quickSettings",
                    "type": "function",
                    "doc": "quickSettings(): boolean;"
                },
                {
                    "name": "recents",
                    "type": "function",
                    "doc": "recents(): boolean;"
                },
                {
                    "name": "splitScreen",
                    "type": "function",
                    "doc": "splitScreen(): boolean;"
                }
            ],
            "barcode": [
                {
                    "name": "detect",
                    "type": "function",
                    "doc": "detect(options?: DetectOptions): Barcode.Result | Barcode.Result[] | null;"
                },
                {
                    "name": "detectAll",
                    "type": "function",
                    "doc": "detectAll(options?: DetectOptions): Barcode.Result[];"
                },
                {
                    "name": "recognizeText",
                    "type": "function",
                    "doc": "recognizeText(options?: DetectOptions): string | string[] | null;"
                },
                {
                    "name": "recognizeTexts",
                    "type": "function",
                    "doc": "recognizeTexts(options?: DetectOptions): string[];"
                }
            ],
            "base64": [
                {
                    "name": "encode",
                    "type": "function",
                    "doc": "encode(o: Base64.Input, encoding?: Base64.Encoding): string;"
                },
                {
                    "name": "decode",
                    "type": "function",
                    "doc": "decode(o: Base64.Input, encoding?: Base64.Encoding): string;"
                }
            ],
            "Color": [
                {
                    "name": "new",
                    "type": "function",
                    "doc": "new(color?: OmniColor);"
                },
                {
                    "name": "color",
                    "type": "variable",
                    "doc": "color: ColorInt;"
                },
                {
                    "name": "summary",
                    "type": "function",
                    "doc": "summary(): string;"
                },
                {
                    "name": "toHex",
                    "type": "function",
                    "doc": "toHex(alpha?: boolean | 'auto' | 'none' | 'keep'): string;"
                },
                {
                    "name": "toFullHex",
                    "type": "function",
                    "doc": "toFullHex(): string;"
                },
                {
                    "name": "toInt",
                    "type": "function",
                    "doc": "toInt(): number;"
                },
                {
                    "name": "alpha",
                    "type": "function",
                    "doc": "alpha(options?: ComponentOptions): number;"
                },
                {
                    "name": "getAlpha",
                    "type": "function",
                    "doc": "getAlpha(options?: ComponentOptions): number;"
                },
                {
                    "name": "alphaDouble",
                    "type": "function",
                    "doc": "alphaDouble(): number;"
                },
                {
                    "name": "getAlphaDouble",
                    "type": "function",
                    "doc": "getAlphaDouble(): number;"
                },
                {
                    "name": "red",
                    "type": "function",
                    "doc": "red(options?: ComponentOptions): number;"
                },
                {
                    "name": "getRed",
                    "type": "function",
                    "doc": "getRed(options?: ComponentOptions): number;"
                },
                {
                    "name": "redDouble",
                    "type": "function",
                    "doc": "redDouble(): number;"
                },
                {
                    "name": "getRedDouble",
                    "type": "function",
                    "doc": "getRedDouble(): number;"
                },
                {
                    "name": "green",
                    "type": "function",
                    "doc": "green(options?: ComponentOptions): number;"
                },
                {
                    "name": "getGreen",
                    "type": "function",
                    "doc": "getGreen(options?: ComponentOptions): number;"
                },
                {
                    "name": "greenDouble",
                    "type": "function",
                    "doc": "greenDouble(): number;"
                },
                {
                    "name": "getGreenDouble",
                    "type": "function",
                    "doc": "getGreenDouble(): number;"
                },
                {
                    "name": "blue",
                    "type": "function",
                    "doc": "blue(options?: ComponentOptions): number;"
                },
                {
                    "name": "getBlue",
                    "type": "function",
                    "doc": "getBlue(options?: ComponentOptions): number;"
                },
                {
                    "name": "blueDouble",
                    "type": "function",
                    "doc": "blueDouble(): number;"
                },
                {
                    "name": "getBlueDouble",
                    "type": "function",
                    "doc": "getBlueDouble(): number;"
                },
                {
                    "name": "setAlpha",
                    "type": "function",
                    "doc": "setAlpha(alpha: ColorComponent): this;"
                },
                {
                    "name": "setAlphaRelative",
                    "type": "function",
                    "doc": "setAlphaRelative(percentage: number | PercentString): this;"
                },
                {
                    "name": "removeAlpha",
                    "type": "function",
                    "doc": "removeAlpha(): this;"
                },
                {
                    "name": "setRed",
                    "type": "function",
                    "doc": "setRed(red: ColorComponent): this;"
                },
                {
                    "name": "setRedRelative",
                    "type": "function",
                    "doc": "setRedRelative(percentage: number | PercentString): this;"
                },
                {
                    "name": "removeRed",
                    "type": "function",
                    "doc": "removeRed(): this;"
                },
                {
                    "name": "setGreen",
                    "type": "function",
                    "doc": "setGreen(green: ColorComponent): this;"
                },
                {
                    "name": "setGreenRelative",
                    "type": "function",
                    "doc": "setGreenRelative(percentage: number | PercentString): this;"
                },
                {
                    "name": "removeGreen",
                    "type": "function",
                    "doc": "removeGreen(): this;"
                },
                {
                    "name": "setBlue",
                    "type": "function",
                    "doc": "setBlue(blue: ColorComponent): this;"
                },
                {
                    "name": "setBlueRelative",
                    "type": "function",
                    "doc": "setBlueRelative(percentage: number | PercentString): this;"
                },
                {
                    "name": "removeBlue",
                    "type": "function",
                    "doc": "removeBlue(): this;"
                },
                {
                    "name": "setRgb",
                    "type": "function",
                    "doc": "setRgb(red: ColorComponent, green: ColorComponent, blue: ColorComponent): this;"
                },
                {
                    "name": "setArgb",
                    "type": "function",
                    "doc": "setArgb(alpha: ColorComponent, red: ColorComponent, green: ColorComponent, blue: ColorComponent): this;"
                },
                {
                    "name": "setRgba",
                    "type": "function",
                    "doc": "setRgba(red: ColorComponent, green: ColorComponent, blue: ColorComponent, alpha: ColorComponent): this;"
                },
                {
                    "name": "setHsv",
                    "type": "function",
                    "doc": "setHsv(h: ColorHueComponent, s: ColorComponent, v: ColorComponent): this;"
                },
                {
                    "name": "setHsva",
                    "type": "function",
                    "doc": "setHsva(h: ColorHueComponent, s: ColorComponent, v: ColorComponent, a: ColorComponent): this;"
                },
                {
                    "name": "setHsl",
                    "type": "function",
                    "doc": "setHsl(h: ColorHueComponent, s: ColorComponent, l: ColorComponent): this;"
                },
                {
                    "name": "setHsla",
                    "type": "function",
                    "doc": "setHsla(h: ColorHueComponent, s: ColorComponent, l: ColorComponent, a: ColorComponent): this;"
                },
                {
                    "name": "toRgb",
                    "type": "function",
                    "doc": "toRgb(): number[];"
                },
                {
                    "name": "toRgba",
                    "type": "function",
                    "doc": "toRgba(options?: ToComponentOptions): number[];"
                },
                {
                    "name": "toArgb",
                    "type": "function",
                    "doc": "toArgb(options?: ToComponentOptions): number[];"
                },
                {
                    "name": "toHsv",
                    "type": "function",
                    "doc": "toHsv(hsvResultContainer?: FloatArray): number[];"
                },
                {
                    "name": "toHsva",
                    "type": "function",
                    "doc": "toHsva(hsvaResultContainer?: FloatArray): number[];"
                },
                {
                    "name": "toHsl",
                    "type": "function",
                    "doc": "toHsl(hslResultContainer?: FloatArray): number[];"
                },
                {
                    "name": "toHsla",
                    "type": "function",
                    "doc": "toHsla(): number[];"
                },
                {
                    "name": "toRgbString",
                    "type": "function",
                    "doc": "toRgbString(): string;"
                },
                {
                    "name": "toRgbaString",
                    "type": "function",
                    "doc": "toRgbaString(options?: boolean | StringOptions): string;"
                },
                {
                    "name": "toArgbString",
                    "type": "function",
                    "doc": "toArgbString(options?: boolean | StringOptions): string;"
                },
                {
                    "name": "toHsvString",
                    "type": "function",
                    "doc": "toHsvString(): string;"
                },
                {
                    "name": "toHsvaString",
                    "type": "function",
                    "doc": "toHsvaString(options?: boolean | StringOptions): string;"
                },
                {
                    "name": "toHslString",
                    "type": "function",
                    "doc": "toHslString(): string;"
                },
                {
                    "name": "toHslaString",
                    "type": "function",
                    "doc": "toHslaString(options?: boolean | StringOptions): string;"
                },
                {
                    "name": "isSimilar",
                    "type": "function",
                    "doc": "isSimilar(other: OmniColor, threshold?: number, algorithm?: DetectionAlgorithm): boolean;"
                },
                {
                    "name": "isEqual",
                    "type": "function",
                    "doc": "isEqual(other: OmniColor, alphaMatters?: boolean): boolean;"
                },
                {
                    "name": "equals",
                    "type": "function",
                    "doc": "equals(other: OmniColor): boolean;"
                },
                {
                    "name": "luminance",
                    "type": "function",
                    "doc": "luminance(): number;"
                },
                {
                    "name": "toColorStateList",
                    "type": "function",
                    "doc": "toColorStateList(): android.content.res.ColorStateList;"
                },
                {
                    "name": "setPaintColor",
                    "type": "function",
                    "doc": "setPaintColor(paint: Paint): this;"
                }
            ],
            "colors": [
                {
                    "name": "web",
                    "type": "variable",
                    "doc": "web: typeof org.autojs.autojs.core.image.ColorTable.Web;"
                },
                {
                    "name": "css",
                    "type": "variable",
                    "doc": "css: typeof org.autojs.autojs.core.image.ColorTable.Css;"
                },
                {
                    "name": "material",
                    "type": "variable",
                    "doc": "material: typeof org.autojs.autojs.core.image.ColorTable.Material;"
                },
                {
                    "name": "android",
                    "type": "variable",
                    "doc": "android: typeof org.autojs.autojs.core.image.ColorTable.Android;"
                },
                {
                    "name": "all",
                    "type": "variable",
                    "doc": "all: Colors.ColorMap;"
                },
                {
                    "name": "themeColor",
                    "type": "variable",
                    "doc": "themeColor: org.autojs.autojs.theme.ThemeColor;"
                },
                {
                    "name": "ALICE_BLUE",
                    "type": "variable",
                    "doc": "ALICE_BLUE: number;"
                },
                {
                    "name": "ALIZARIN_CRIMSON",
                    "type": "variable",
                    "doc": "ALIZARIN_CRIMSON: number;"
                },
                {
                    "name": "AMBER",
                    "type": "variable",
                    "doc": "AMBER: number;"
                },
                {
                    "name": "AMBER_50",
                    "type": "variable",
                    "doc": "AMBER_50: number;"
                },
                {
                    "name": "AMBER_100",
                    "type": "variable",
                    "doc": "AMBER_100: number;"
                },
                {
                    "name": "AMBER_200",
                    "type": "variable",
                    "doc": "AMBER_200: number;"
                },
                {
                    "name": "AMBER_300",
                    "type": "variable",
                    "doc": "AMBER_300: number;"
                },
                {
                    "name": "AMBER_400",
                    "type": "variable",
                    "doc": "AMBER_400: number;"
                },
                {
                    "name": "AMBER_500",
                    "type": "variable",
                    "doc": "AMBER_500: number;"
                },
                {
                    "name": "AMBER_600",
                    "type": "variable",
                    "doc": "AMBER_600: number;"
                },
                {
                    "name": "AMBER_700",
                    "type": "variable",
                    "doc": "AMBER_700: number;"
                },
                {
                    "name": "AMBER_800",
                    "type": "variable",
                    "doc": "AMBER_800: number;"
                },
                {
                    "name": "AMBER_900",
                    "type": "variable",
                    "doc": "AMBER_900: number;"
                },
                {
                    "name": "AMBER_A100",
                    "type": "variable",
                    "doc": "AMBER_A100: number;"
                },
                {
                    "name": "AMBER_A200",
                    "type": "variable",
                    "doc": "AMBER_A200: number;"
                },
                {
                    "name": "AMBER_A400",
                    "type": "variable",
                    "doc": "AMBER_A400: number;"
                },
                {
                    "name": "AMBER_A700",
                    "type": "variable",
                    "doc": "AMBER_A700: number;"
                },
                {
                    "name": "AMETHYST",
                    "type": "variable",
                    "doc": "AMETHYST: number;"
                },
                {
                    "name": "ANTIQUE_WHITE",
                    "type": "variable",
                    "doc": "ANTIQUE_WHITE: number;"
                },
                {
                    "name": "APPLE_GREEN",
                    "type": "variable",
                    "doc": "APPLE_GREEN: number;"
                },
                {
                    "name": "APRICOT",
                    "type": "variable",
                    "doc": "APRICOT: number;"
                },
                {
                    "name": "AQUA",
                    "type": "variable",
                    "doc": "AQUA: number;"
                },
                {
                    "name": "AQUAMARINE",
                    "type": "variable",
                    "doc": "AQUAMARINE: number;"
                },
                {
                    "name": "AQUA_BLUE",
                    "type": "variable",
                    "doc": "AQUA_BLUE: number;"
                },
                {
                    "name": "AZURE",
                    "type": "variable",
                    "doc": "AZURE: number;"
                },
                {
                    "name": "BABY_BLUE",
                    "type": "variable",
                    "doc": "BABY_BLUE: number;"
                },
                {
                    "name": "BABY_PINK",
                    "type": "variable",
                    "doc": "BABY_PINK: number;"
                },
                {
                    "name": "BEIGE",
                    "type": "variable",
                    "doc": "BEIGE: number;"
                },
                {
                    "name": "BISQUE",
                    "type": "variable",
                    "doc": "BISQUE: number;"
                },
                {
                    "name": "BLACK",
                    "type": "variable",
                    "doc": "BLACK: number;"
                },
                {
                    "name": "BLACK_1000",
                    "type": "variable",
                    "doc": "BLACK_1000: number;"
                },
                {
                    "name": "BLANCHED_ALMOND",
                    "type": "variable",
                    "doc": "BLANCHED_ALMOND: number;"
                },
                {
                    "name": "BLUE",
                    "type": "variable",
                    "doc": "BLUE: number;"
                },
                {
                    "name": "BLUE_50",
                    "type": "variable",
                    "doc": "BLUE_50: number;"
                },
                {
                    "name": "BLUE_100",
                    "type": "variable",
                    "doc": "BLUE_100: number;"
                },
                {
                    "name": "BLUE_200",
                    "type": "variable",
                    "doc": "BLUE_200: number;"
                },
                {
                    "name": "BLUE_300",
                    "type": "variable",
                    "doc": "BLUE_300: number;"
                },
                {
                    "name": "BLUE_400",
                    "type": "variable",
                    "doc": "BLUE_400: number;"
                },
                {
                    "name": "BLUE_500",
                    "type": "variable",
                    "doc": "BLUE_500: number;"
                },
                {
                    "name": "BLUE_600",
                    "type": "variable",
                    "doc": "BLUE_600: number;"
                },
                {
                    "name": "BLUE_700",
                    "type": "variable",
                    "doc": "BLUE_700: number;"
                },
                {
                    "name": "BLUE_800",
                    "type": "variable",
                    "doc": "BLUE_800: number;"
                },
                {
                    "name": "BLUE_900",
                    "type": "variable",
                    "doc": "BLUE_900: number;"
                },
                {
                    "name": "BLUE_A100",
                    "type": "variable",
                    "doc": "BLUE_A100: number;"
                },
                {
                    "name": "BLUE_A200",
                    "type": "variable",
                    "doc": "BLUE_A200: number;"
                },
                {
                    "name": "BLUE_A400",
                    "type": "variable",
                    "doc": "BLUE_A400: number;"
                },
                {
                    "name": "BLUE_A700",
                    "type": "variable",
                    "doc": "BLUE_A700: number;"
                },
                {
                    "name": "BLUE_GREY",
                    "type": "variable",
                    "doc": "BLUE_GREY: number;"
                },
                {
                    "name": "BLUE_GRAY",
                    "type": "variable",
                    "doc": "BLUE_GRAY: number;"
                },
                {
                    "name": "BLUE_GREY_50",
                    "type": "variable",
                    "doc": "BLUE_GREY_50: number;"
                },
                {
                    "name": "BLUE_GRAY_50",
                    "type": "variable",
                    "doc": "BLUE_GRAY_50: number;"
                },
                {
                    "name": "BLUE_GREY_100",
                    "type": "variable",
                    "doc": "BLUE_GREY_100: number;"
                },
                {
                    "name": "BLUE_GRAY_100",
                    "type": "variable",
                    "doc": "BLUE_GRAY_100: number;"
                },
                {
                    "name": "BLUE_GREY_200",
                    "type": "variable",
                    "doc": "BLUE_GREY_200: number;"
                },
                {
                    "name": "BLUE_GRAY_200",
                    "type": "variable",
                    "doc": "BLUE_GRAY_200: number;"
                },
                {
                    "name": "BLUE_GREY_300",
                    "type": "variable",
                    "doc": "BLUE_GREY_300: number;"
                },
                {
                    "name": "BLUE_GRAY_300",
                    "type": "variable",
                    "doc": "BLUE_GRAY_300: number;"
                },
                {
                    "name": "BLUE_GREY_400",
                    "type": "variable",
                    "doc": "BLUE_GREY_400: number;"
                },
                {
                    "name": "BLUE_GRAY_400",
                    "type": "variable",
                    "doc": "BLUE_GRAY_400: number;"
                },
                {
                    "name": "BLUE_GREY_500",
                    "type": "variable",
                    "doc": "BLUE_GREY_500: number;"
                },
                {
                    "name": "BLUE_GRAY_500",
                    "type": "variable",
                    "doc": "BLUE_GRAY_500: number;"
                },
                {
                    "name": "BLUE_GREY_600",
                    "type": "variable",
                    "doc": "BLUE_GREY_600: number;"
                },
                {
                    "name": "BLUE_GRAY_600",
                    "type": "variable",
                    "doc": "BLUE_GRAY_600: number;"
                },
                {
                    "name": "BLUE_GREY_700",
                    "type": "variable",
                    "doc": "BLUE_GREY_700: number;"
                },
                {
                    "name": "BLUE_GRAY_700",
                    "type": "variable",
                    "doc": "BLUE_GRAY_700: number;"
                },
                {
                    "name": "BLUE_GREY_800",
                    "type": "variable",
                    "doc": "BLUE_GREY_800: number;"
                },
                {
                    "name": "BLUE_GRAY_800",
                    "type": "variable",
                    "doc": "BLUE_GRAY_800: number;"
                },
                {
                    "name": "BLUE_GREY_900",
                    "type": "variable",
                    "doc": "BLUE_GREY_900: number;"
                },
                {
                    "name": "BLUE_GRAY_900",
                    "type": "variable",
                    "doc": "BLUE_GRAY_900: number;"
                },
                {
                    "name": "BLUE_VIOLET",
                    "type": "variable",
                    "doc": "BLUE_VIOLET: number;"
                },
                {
                    "name": "BRIGHT_GREEN",
                    "type": "variable",
                    "doc": "BRIGHT_GREEN: number;"
                },
                {
                    "name": "BRONZE",
                    "type": "variable",
                    "doc": "BRONZE: number;"
                },
                {
                    "name": "BROWN",
                    "type": "variable",
                    "doc": "BROWN: number;"
                },
                {
                    "name": "BROWN_50",
                    "type": "variable",
                    "doc": "BROWN_50: number;"
                },
                {
                    "name": "BROWN_100",
                    "type": "variable",
                    "doc": "BROWN_100: number;"
                },
                {
                    "name": "BROWN_200",
                    "type": "variable",
                    "doc": "BROWN_200: number;"
                },
                {
                    "name": "BROWN_300",
                    "type": "variable",
                    "doc": "BROWN_300: number;"
                },
                {
                    "name": "BROWN_400",
                    "type": "variable",
                    "doc": "BROWN_400: number;"
                },
                {
                    "name": "BROWN_500",
                    "type": "variable",
                    "doc": "BROWN_500: number;"
                },
                {
                    "name": "BROWN_600",
                    "type": "variable",
                    "doc": "BROWN_600: number;"
                },
                {
                    "name": "BROWN_700",
                    "type": "variable",
                    "doc": "BROWN_700: number;"
                },
                {
                    "name": "BROWN_800",
                    "type": "variable",
                    "doc": "BROWN_800: number;"
                },
                {
                    "name": "BROWN_900",
                    "type": "variable",
                    "doc": "BROWN_900: number;"
                },
                {
                    "name": "BURGUNDY",
                    "type": "variable",
                    "doc": "BURGUNDY: number;"
                },
                {
                    "name": "BURLY_WOOD",
                    "type": "variable",
                    "doc": "BURLY_WOOD: number;"
                },
                {
                    "name": "BURNT_ORANGE",
                    "type": "variable",
                    "doc": "BURNT_ORANGE: number;"
                },
                {
                    "name": "CADET_BLUE",
                    "type": "variable",
                    "doc": "CADET_BLUE: number;"
                },
                {
                    "name": "CAMEL",
                    "type": "variable",
                    "doc": "CAMEL: number;"
                },
                {
                    "name": "CAMELLIA",
                    "type": "variable",
                    "doc": "CAMELLIA: number;"
                },
                {
                    "name": "CANARY_YELLOW",
                    "type": "variable",
                    "doc": "CANARY_YELLOW: number;"
                },
                {
                    "name": "CARDINAL_RED",
                    "type": "variable",
                    "doc": "CARDINAL_RED: number;"
                },
                {
                    "name": "CARMINE",
                    "type": "variable",
                    "doc": "CARMINE: number;"
                },
                {
                    "name": "CELADON",
                    "type": "variable",
                    "doc": "CELADON: number;"
                },
                {
                    "name": "CERISE",
                    "type": "variable",
                    "doc": "CERISE: number;"
                },
                {
                    "name": "CERULEAN_BLUE",
                    "type": "variable",
                    "doc": "CERULEAN_BLUE: number;"
                },
                {
                    "name": "CHAMPAGNE_YELLOW",
                    "type": "variable",
                    "doc": "CHAMPAGNE_YELLOW: number;"
                },
                {
                    "name": "CHARTREUSE",
                    "type": "variable",
                    "doc": "CHARTREUSE: number;"
                },
                {
                    "name": "CHOCOLATE",
                    "type": "variable",
                    "doc": "CHOCOLATE: number;"
                },
                {
                    "name": "CHROME_YELLOW",
                    "type": "variable",
                    "doc": "CHROME_YELLOW: number;"
                },
                {
                    "name": "CLEMATIS",
                    "type": "variable",
                    "doc": "CLEMATIS: number;"
                },
                {
                    "name": "COBALT_BLUE",
                    "type": "variable",
                    "doc": "COBALT_BLUE: number;"
                },
                {
                    "name": "COBALT_GREEN",
                    "type": "variable",
                    "doc": "COBALT_GREEN: number;"
                },
                {
                    "name": "COCONUT_BROWN",
                    "type": "variable",
                    "doc": "COCONUT_BROWN: number;"
                },
                {
                    "name": "COFFEE",
                    "type": "variable",
                    "doc": "COFFEE: number;"
                },
                {
                    "name": "CORAL",
                    "type": "variable",
                    "doc": "CORAL: number;"
                },
                {
                    "name": "CORAL_PINK",
                    "type": "variable",
                    "doc": "CORAL_PINK: number;"
                },
                {
                    "name": "CORNFLOWER_BLUE",
                    "type": "variable",
                    "doc": "CORNFLOWER_BLUE: number;"
                },
                {
                    "name": "CORN_SILK",
                    "type": "variable",
                    "doc": "CORN_SILK: number;"
                },
                {
                    "name": "CREAM",
                    "type": "variable",
                    "doc": "CREAM: number;"
                },
                {
                    "name": "CRIMSON",
                    "type": "variable",
                    "doc": "CRIMSON: number;"
                },
                {
                    "name": "CYAN",
                    "type": "variable",
                    "doc": "CYAN: number;"
                },
                {
                    "name": "CYAN_50",
                    "type": "variable",
                    "doc": "CYAN_50: number;"
                },
                {
                    "name": "CYAN_100",
                    "type": "variable",
                    "doc": "CYAN_100: number;"
                },
                {
                    "name": "CYAN_200",
                    "type": "variable",
                    "doc": "CYAN_200: number;"
                },
                {
                    "name": "CYAN_300",
                    "type": "variable",
                    "doc": "CYAN_300: number;"
                },
                {
                    "name": "CYAN_400",
                    "type": "variable",
                    "doc": "CYAN_400: number;"
                },
                {
                    "name": "CYAN_500",
                    "type": "variable",
                    "doc": "CYAN_500: number;"
                },
                {
                    "name": "CYAN_600",
                    "type": "variable",
                    "doc": "CYAN_600: number;"
                },
                {
                    "name": "CYAN_700",
                    "type": "variable",
                    "doc": "CYAN_700: number;"
                },
                {
                    "name": "CYAN_800",
                    "type": "variable",
                    "doc": "CYAN_800: number;"
                },
                {
                    "name": "CYAN_900",
                    "type": "variable",
                    "doc": "CYAN_900: number;"
                },
                {
                    "name": "CYAN_A100",
                    "type": "variable",
                    "doc": "CYAN_A100: number;"
                },
                {
                    "name": "CYAN_A200",
                    "type": "variable",
                    "doc": "CYAN_A200: number;"
                },
                {
                    "name": "CYAN_A400",
                    "type": "variable",
                    "doc": "CYAN_A400: number;"
                },
                {
                    "name": "CYAN_A700",
                    "type": "variable",
                    "doc": "CYAN_A700: number;"
                },
                {
                    "name": "CYAN_BLUE",
                    "type": "variable",
                    "doc": "CYAN_BLUE: number;"
                },
                {
                    "name": "DARK_BLUE",
                    "type": "variable",
                    "doc": "DARK_BLUE: number;"
                },
                {
                    "name": "DARK_CYAN",
                    "type": "variable",
                    "doc": "DARK_CYAN: number;"
                },
                {
                    "name": "DARK_GOLDENROD",
                    "type": "variable",
                    "doc": "DARK_GOLDENROD: number;"
                },
                {
                    "name": "DARK_GRAY",
                    "type": "variable",
                    "doc": "DARK_GRAY: number;"
                },
                {
                    "name": "DARK_GREEN",
                    "type": "variable",
                    "doc": "DARK_GREEN: number;"
                },
                {
                    "name": "DARK_GREY",
                    "type": "variable",
                    "doc": "DARK_GREY: number;"
                },
                {
                    "name": "DARK_KHAKI",
                    "type": "variable",
                    "doc": "DARK_KHAKI: number;"
                },
                {
                    "name": "DARK_MAGENTA",
                    "type": "variable",
                    "doc": "DARK_MAGENTA: number;"
                },
                {
                    "name": "DARK_MINERAL_BLUE",
                    "type": "variable",
                    "doc": "DARK_MINERAL_BLUE: number;"
                },
                {
                    "name": "DARK_OLIVE_GREEN",
                    "type": "variable",
                    "doc": "DARK_OLIVE_GREEN: number;"
                },
                {
                    "name": "DARK_ORANGE",
                    "type": "variable",
                    "doc": "DARK_ORANGE: number;"
                },
                {
                    "name": "DARK_ORCHID",
                    "type": "variable",
                    "doc": "DARK_ORCHID: number;"
                },
                {
                    "name": "DARK_POWDER_BLUE",
                    "type": "variable",
                    "doc": "DARK_POWDER_BLUE: number;"
                },
                {
                    "name": "DARK_RED",
                    "type": "variable",
                    "doc": "DARK_RED: number;"
                },
                {
                    "name": "DARK_SALMON",
                    "type": "variable",
                    "doc": "DARK_SALMON: number;"
                },
                {
                    "name": "DARK_SEA_GREEN",
                    "type": "variable",
                    "doc": "DARK_SEA_GREEN: number;"
                },
                {
                    "name": "DARK_SLATE_BLUE",
                    "type": "variable",
                    "doc": "DARK_SLATE_BLUE: number;"
                },
                {
                    "name": "DARK_SLATE_GRAY",
                    "type": "variable",
                    "doc": "DARK_SLATE_GRAY: number;"
                },
                {
                    "name": "DARK_SLATE_GREY",
                    "type": "variable",
                    "doc": "DARK_SLATE_GREY: number;"
                },
                {
                    "name": "DARK_TURQUOISE",
                    "type": "variable",
                    "doc": "DARK_TURQUOISE: number;"
                },
                {
                    "name": "DARK_VIOLET",
                    "type": "variable",
                    "doc": "DARK_VIOLET: number;"
                },
                {
                    "name": "DEEP_ORANGE",
                    "type": "variable",
                    "doc": "DEEP_ORANGE: number;"
                },
                {
                    "name": "DEEP_ORANGE_50",
                    "type": "variable",
                    "doc": "DEEP_ORANGE_50: number;"
                },
                {
                    "name": "DEEP_ORANGE_100",
                    "type": "variable",
                    "doc": "DEEP_ORANGE_100: number;"
                },
                {
                    "name": "DEEP_ORANGE_200",
                    "type": "variable",
                    "doc": "DEEP_ORANGE_200: number;"
                },
                {
                    "name": "DEEP_ORANGE_300",
                    "type": "variable",
                    "doc": "DEEP_ORANGE_300: number;"
                },
                {
                    "name": "DEEP_ORANGE_400",
                    "type": "variable",
                    "doc": "DEEP_ORANGE_400: number;"
                },
                {
                    "name": "DEEP_ORANGE_500",
                    "type": "variable",
                    "doc": "DEEP_ORANGE_500: number;"
                },
                {
                    "name": "DEEP_ORANGE_600",
                    "type": "variable",
                    "doc": "DEEP_ORANGE_600: number;"
                },
                {
                    "name": "DEEP_ORANGE_700",
                    "type": "variable",
                    "doc": "DEEP_ORANGE_700: number;"
                },
                {
                    "name": "DEEP_ORANGE_800",
                    "type": "variable",
                    "doc": "DEEP_ORANGE_800: number;"
                },
                {
                    "name": "DEEP_ORANGE_900",
                    "type": "variable",
                    "doc": "DEEP_ORANGE_900: number;"
                },
                {
                    "name": "DEEP_ORANGE_A100",
                    "type": "variable",
                    "doc": "DEEP_ORANGE_A100: number;"
                },
                {
                    "name": "DEEP_ORANGE_A200",
                    "type": "variable",
                    "doc": "DEEP_ORANGE_A200: number;"
                },
                {
                    "name": "DEEP_ORANGE_A400",
                    "type": "variable",
                    "doc": "DEEP_ORANGE_A400: number;"
                },
                {
                    "name": "DEEP_ORANGE_A700",
                    "type": "variable",
                    "doc": "DEEP_ORANGE_A700: number;"
                },
                {
                    "name": "DEEP_PINK",
                    "type": "variable",
                    "doc": "DEEP_PINK: number;"
                },
                {
                    "name": "DEEP_PURPLE",
                    "type": "variable",
                    "doc": "DEEP_PURPLE: number;"
                },
                {
                    "name": "DEEP_PURPLE_50",
                    "type": "variable",
                    "doc": "DEEP_PURPLE_50: number;"
                },
                {
                    "name": "DEEP_PURPLE_100",
                    "type": "variable",
                    "doc": "DEEP_PURPLE_100: number;"
                },
                {
                    "name": "DEEP_PURPLE_200",
                    "type": "variable",
                    "doc": "DEEP_PURPLE_200: number;"
                },
                {
                    "name": "DEEP_PURPLE_300",
                    "type": "variable",
                    "doc": "DEEP_PURPLE_300: number;"
                },
                {
                    "name": "DEEP_PURPLE_400",
                    "type": "variable",
                    "doc": "DEEP_PURPLE_400: number;"
                },
                {
                    "name": "DEEP_PURPLE_500",
                    "type": "variable",
                    "doc": "DEEP_PURPLE_500: number;"
                },
                {
                    "name": "DEEP_PURPLE_600",
                    "type": "variable",
                    "doc": "DEEP_PURPLE_600: number;"
                },
                {
                    "name": "DEEP_PURPLE_700",
                    "type": "variable",
                    "doc": "DEEP_PURPLE_700: number;"
                },
                {
                    "name": "DEEP_PURPLE_800",
                    "type": "variable",
                    "doc": "DEEP_PURPLE_800: number;"
                },
                {
                    "name": "DEEP_PURPLE_900",
                    "type": "variable",
                    "doc": "DEEP_PURPLE_900: number;"
                },
                {
                    "name": "DEEP_PURPLE_A100",
                    "type": "variable",
                    "doc": "DEEP_PURPLE_A100: number;"
                },
                {
                    "name": "DEEP_PURPLE_A200",
                    "type": "variable",
                    "doc": "DEEP_PURPLE_A200: number;"
                },
                {
                    "name": "DEEP_PURPLE_A400",
                    "type": "variable",
                    "doc": "DEEP_PURPLE_A400: number;"
                },
                {
                    "name": "DEEP_PURPLE_A700",
                    "type": "variable",
                    "doc": "DEEP_PURPLE_A700: number;"
                },
                {
                    "name": "DEEP_SKY_BLUE",
                    "type": "variable",
                    "doc": "DEEP_SKY_BLUE: number;"
                },
                {
                    "name": "DIM_GRAY",
                    "type": "variable",
                    "doc": "DIM_GRAY: number;"
                },
                {
                    "name": "DIM_GREY",
                    "type": "variable",
                    "doc": "DIM_GREY: number;"
                },
                {
                    "name": "DKGRAY",
                    "type": "variable",
                    "doc": "DKGRAY: number;"
                },
                {
                    "name": "DODGER_BLUE",
                    "type": "variable",
                    "doc": "DODGER_BLUE: number;"
                },
                {
                    "name": "EMERALD",
                    "type": "variable",
                    "doc": "EMERALD: number;"
                },
                {
                    "name": "FIRE_BRICK",
                    "type": "variable",
                    "doc": "FIRE_BRICK: number;"
                },
                {
                    "name": "FLAMINGO",
                    "type": "variable",
                    "doc": "FLAMINGO: number;"
                },
                {
                    "name": "FLORAL_WHITE",
                    "type": "variable",
                    "doc": "FLORAL_WHITE: number;"
                },
                {
                    "name": "FOLIAGE_GREEN",
                    "type": "variable",
                    "doc": "FOLIAGE_GREEN: number;"
                },
                {
                    "name": "FOREST_GREEN",
                    "type": "variable",
                    "doc": "FOREST_GREEN: number;"
                },
                {
                    "name": "FRESH_LEAVES",
                    "type": "variable",
                    "doc": "FRESH_LEAVES: number;"
                },
                {
                    "name": "FUCHSIA",
                    "type": "variable",
                    "doc": "FUCHSIA: number;"
                },
                {
                    "name": "GAINSBORO",
                    "type": "variable",
                    "doc": "GAINSBORO: number;"
                },
                {
                    "name": "GHOST_WHITE",
                    "type": "variable",
                    "doc": "GHOST_WHITE: number;"
                },
                {
                    "name": "GOLD",
                    "type": "variable",
                    "doc": "GOLD: number;"
                },
                {
                    "name": "GOLDEN",
                    "type": "variable",
                    "doc": "GOLDEN: number;"
                },
                {
                    "name": "GOLDENROD",
                    "type": "variable",
                    "doc": "GOLDENROD: number;"
                },
                {
                    "name": "GRASS_GREEN",
                    "type": "variable",
                    "doc": "GRASS_GREEN: number;"
                },
                {
                    "name": "GRAY",
                    "type": "variable",
                    "doc": "GRAY: number;"
                },
                {
                    "name": "GRAYISH_PURPLE",
                    "type": "variable",
                    "doc": "GRAYISH_PURPLE: number;"
                },
                {
                    "name": "GREEN",
                    "type": "variable",
                    "doc": "GREEN: number;"
                },
                {
                    "name": "GREEN_50",
                    "type": "variable",
                    "doc": "GREEN_50: number;"
                },
                {
                    "name": "GREEN_100",
                    "type": "variable",
                    "doc": "GREEN_100: number;"
                },
                {
                    "name": "GREEN_200",
                    "type": "variable",
                    "doc": "GREEN_200: number;"
                },
                {
                    "name": "GREEN_300",
                    "type": "variable",
                    "doc": "GREEN_300: number;"
                },
                {
                    "name": "GREEN_400",
                    "type": "variable",
                    "doc": "GREEN_400: number;"
                },
                {
                    "name": "GREEN_500",
                    "type": "variable",
                    "doc": "GREEN_500: number;"
                },
                {
                    "name": "GREEN_600",
                    "type": "variable",
                    "doc": "GREEN_600: number;"
                },
                {
                    "name": "GREEN_700",
                    "type": "variable",
                    "doc": "GREEN_700: number;"
                },
                {
                    "name": "GREEN_800",
                    "type": "variable",
                    "doc": "GREEN_800: number;"
                },
                {
                    "name": "GREEN_900",
                    "type": "variable",
                    "doc": "GREEN_900: number;"
                },
                {
                    "name": "GREEN_A100",
                    "type": "variable",
                    "doc": "GREEN_A100: number;"
                },
                {
                    "name": "GREEN_A200",
                    "type": "variable",
                    "doc": "GREEN_A200: number;"
                },
                {
                    "name": "GREEN_A400",
                    "type": "variable",
                    "doc": "GREEN_A400: number;"
                },
                {
                    "name": "GREEN_A700",
                    "type": "variable",
                    "doc": "GREEN_A700: number;"
                },
                {
                    "name": "GREEN_YELLOW",
                    "type": "variable",
                    "doc": "GREEN_YELLOW: number;"
                },
                {
                    "name": "GREY",
                    "type": "variable",
                    "doc": "GREY: number;"
                },
                {
                    "name": "GREY_50",
                    "type": "variable",
                    "doc": "GREY_50: number;"
                },
                {
                    "name": "GRAY_50",
                    "type": "variable",
                    "doc": "GRAY_50: number;"
                },
                {
                    "name": "GREY_100",
                    "type": "variable",
                    "doc": "GREY_100: number;"
                },
                {
                    "name": "GRAY_100",
                    "type": "variable",
                    "doc": "GRAY_100: number;"
                },
                {
                    "name": "GREY_200",
                    "type": "variable",
                    "doc": "GREY_200: number;"
                },
                {
                    "name": "GRAY_200",
                    "type": "variable",
                    "doc": "GRAY_200: number;"
                },
                {
                    "name": "GREY_300",
                    "type": "variable",
                    "doc": "GREY_300: number;"
                },
                {
                    "name": "GRAY_300",
                    "type": "variable",
                    "doc": "GRAY_300: number;"
                },
                {
                    "name": "GREY_400",
                    "type": "variable",
                    "doc": "GREY_400: number;"
                },
                {
                    "name": "GRAY_400",
                    "type": "variable",
                    "doc": "GRAY_400: number;"
                },
                {
                    "name": "GREY_500",
                    "type": "variable",
                    "doc": "GREY_500: number;"
                },
                {
                    "name": "GRAY_500",
                    "type": "variable",
                    "doc": "GRAY_500: number;"
                },
                {
                    "name": "GREY_600",
                    "type": "variable",
                    "doc": "GREY_600: number;"
                },
                {
                    "name": "GRAY_600",
                    "type": "variable",
                    "doc": "GRAY_600: number;"
                },
                {
                    "name": "GREY_700",
                    "type": "variable",
                    "doc": "GREY_700: number;"
                },
                {
                    "name": "GRAY_700",
                    "type": "variable",
                    "doc": "GRAY_700: number;"
                },
                {
                    "name": "GREY_800",
                    "type": "variable",
                    "doc": "GREY_800: number;"
                },
                {
                    "name": "GRAY_800",
                    "type": "variable",
                    "doc": "GRAY_800: number;"
                },
                {
                    "name": "GREY_900",
                    "type": "variable",
                    "doc": "GREY_900: number;"
                },
                {
                    "name": "GRAY_900",
                    "type": "variable",
                    "doc": "GRAY_900: number;"
                },
                {
                    "name": "HELIOTROPE",
                    "type": "variable",
                    "doc": "HELIOTROPE: number;"
                },
                {
                    "name": "HONEYDEW",
                    "type": "variable",
                    "doc": "HONEYDEW: number;"
                },
                {
                    "name": "HONEY_ORANGE",
                    "type": "variable",
                    "doc": "HONEY_ORANGE: number;"
                },
                {
                    "name": "HORIZON_BLUE",
                    "type": "variable",
                    "doc": "HORIZON_BLUE: number;"
                },
                {
                    "name": "HOT_PINK",
                    "type": "variable",
                    "doc": "HOT_PINK: number;"
                },
                {
                    "name": "INDIAN_RED",
                    "type": "variable",
                    "doc": "INDIAN_RED: number;"
                },
                {
                    "name": "INDIGO",
                    "type": "variable",
                    "doc": "INDIGO: number;"
                },
                {
                    "name": "INDIGO_50",
                    "type": "variable",
                    "doc": "INDIGO_50: number;"
                },
                {
                    "name": "INDIGO_100",
                    "type": "variable",
                    "doc": "INDIGO_100: number;"
                },
                {
                    "name": "INDIGO_200",
                    "type": "variable",
                    "doc": "INDIGO_200: number;"
                },
                {
                    "name": "INDIGO_300",
                    "type": "variable",
                    "doc": "INDIGO_300: number;"
                },
                {
                    "name": "INDIGO_400",
                    "type": "variable",
                    "doc": "INDIGO_400: number;"
                },
                {
                    "name": "INDIGO_500",
                    "type": "variable",
                    "doc": "INDIGO_500: number;"
                },
                {
                    "name": "INDIGO_600",
                    "type": "variable",
                    "doc": "INDIGO_600: number;"
                },
                {
                    "name": "INDIGO_700",
                    "type": "variable",
                    "doc": "INDIGO_700: number;"
                },
                {
                    "name": "INDIGO_800",
                    "type": "variable",
                    "doc": "INDIGO_800: number;"
                },
                {
                    "name": "INDIGO_900",
                    "type": "variable",
                    "doc": "INDIGO_900: number;"
                },
                {
                    "name": "INDIGO_A100",
                    "type": "variable",
                    "doc": "INDIGO_A100: number;"
                },
                {
                    "name": "INDIGO_A200",
                    "type": "variable",
                    "doc": "INDIGO_A200: number;"
                },
                {
                    "name": "INDIGO_A400",
                    "type": "variable",
                    "doc": "INDIGO_A400: number;"
                },
                {
                    "name": "INDIGO_A700",
                    "type": "variable",
                    "doc": "INDIGO_A700: number;"
                },
                {
                    "name": "INTERNATIONAL_KLEIN_BLUE",
                    "type": "variable",
                    "doc": "INTERNATIONAL_KLEIN_BLUE: number;"
                },
                {
                    "name": "IRON_GRAY",
                    "type": "variable",
                    "doc": "IRON_GRAY: number;"
                },
                {
                    "name": "IVORY",
                    "type": "variable",
                    "doc": "IVORY: number;"
                },
                {
                    "name": "IVY_GREEN",
                    "type": "variable",
                    "doc": "IVY_GREEN: number;"
                },
                {
                    "name": "JASMINE",
                    "type": "variable",
                    "doc": "JASMINE: number;"
                },
                {
                    "name": "KHAKI",
                    "type": "variable",
                    "doc": "KHAKI: number;"
                },
                {
                    "name": "LAPIS_LAZULI",
                    "type": "variable",
                    "doc": "LAPIS_LAZULI: number;"
                },
                {
                    "name": "LAVENDER",
                    "type": "variable",
                    "doc": "LAVENDER: number;"
                },
                {
                    "name": "LAVENDER_BLUE",
                    "type": "variable",
                    "doc": "LAVENDER_BLUE: number;"
                },
                {
                    "name": "LAVENDER_BLUSH",
                    "type": "variable",
                    "doc": "LAVENDER_BLUSH: number;"
                },
                {
                    "name": "LAVENDER_MAGENTA",
                    "type": "variable",
                    "doc": "LAVENDER_MAGENTA: number;"
                },
                {
                    "name": "LAVENDER_MIST",
                    "type": "variable",
                    "doc": "LAVENDER_MIST: number;"
                },
                {
                    "name": "LAWN_GREEN",
                    "type": "variable",
                    "doc": "LAWN_GREEN: number;"
                },
                {
                    "name": "LEMON_CHIFFON",
                    "type": "variable",
                    "doc": "LEMON_CHIFFON: number;"
                },
                {
                    "name": "LIGHT_BLUE",
                    "type": "variable",
                    "doc": "LIGHT_BLUE: number;"
                },
                {
                    "name": "LIGHT_BLUE_50",
                    "type": "variable",
                    "doc": "LIGHT_BLUE_50: number;"
                },
                {
                    "name": "LIGHT_BLUE_100",
                    "type": "variable",
                    "doc": "LIGHT_BLUE_100: number;"
                },
                {
                    "name": "LIGHT_BLUE_200",
                    "type": "variable",
                    "doc": "LIGHT_BLUE_200: number;"
                },
                {
                    "name": "LIGHT_BLUE_300",
                    "type": "variable",
                    "doc": "LIGHT_BLUE_300: number;"
                },
                {
                    "name": "LIGHT_BLUE_400",
                    "type": "variable",
                    "doc": "LIGHT_BLUE_400: number;"
                },
                {
                    "name": "LIGHT_BLUE_500",
                    "type": "variable",
                    "doc": "LIGHT_BLUE_500: number;"
                },
                {
                    "name": "LIGHT_BLUE_600",
                    "type": "variable",
                    "doc": "LIGHT_BLUE_600: number;"
                },
                {
                    "name": "LIGHT_BLUE_700",
                    "type": "variable",
                    "doc": "LIGHT_BLUE_700: number;"
                },
                {
                    "name": "LIGHT_BLUE_800",
                    "type": "variable",
                    "doc": "LIGHT_BLUE_800: number;"
                },
                {
                    "name": "LIGHT_BLUE_900",
                    "type": "variable",
                    "doc": "LIGHT_BLUE_900: number;"
                },
                {
                    "name": "LIGHT_BLUE_A100",
                    "type": "variable",
                    "doc": "LIGHT_BLUE_A100: number;"
                },
                {
                    "name": "LIGHT_BLUE_A200",
                    "type": "variable",
                    "doc": "LIGHT_BLUE_A200: number;"
                },
                {
                    "name": "LIGHT_BLUE_A400",
                    "type": "variable",
                    "doc": "LIGHT_BLUE_A400: number;"
                },
                {
                    "name": "LIGHT_BLUE_A700",
                    "type": "variable",
                    "doc": "LIGHT_BLUE_A700: number;"
                },
                {
                    "name": "LIGHT_CORAL",
                    "type": "variable",
                    "doc": "LIGHT_CORAL: number;"
                },
                {
                    "name": "LIGHT_CYAN",
                    "type": "variable",
                    "doc": "LIGHT_CYAN: number;"
                },
                {
                    "name": "LIGHT_GOLDENROD_YELLOW",
                    "type": "variable",
                    "doc": "LIGHT_GOLDENROD_YELLOW: number;"
                },
                {
                    "name": "LIGHT_GRAY",
                    "type": "variable",
                    "doc": "LIGHT_GRAY: number;"
                },
                {
                    "name": "LIGHT_GREEN",
                    "type": "variable",
                    "doc": "LIGHT_GREEN: number;"
                },
                {
                    "name": "LIGHT_GREEN_50",
                    "type": "variable",
                    "doc": "LIGHT_GREEN_50: number;"
                },
                {
                    "name": "LIGHT_GREEN_100",
                    "type": "variable",
                    "doc": "LIGHT_GREEN_100: number;"
                },
                {
                    "name": "LIGHT_GREEN_200",
                    "type": "variable",
                    "doc": "LIGHT_GREEN_200: number;"
                },
                {
                    "name": "LIGHT_GREEN_300",
                    "type": "variable",
                    "doc": "LIGHT_GREEN_300: number;"
                },
                {
                    "name": "LIGHT_GREEN_400",
                    "type": "variable",
                    "doc": "LIGHT_GREEN_400: number;"
                },
                {
                    "name": "LIGHT_GREEN_500",
                    "type": "variable",
                    "doc": "LIGHT_GREEN_500: number;"
                },
                {
                    "name": "LIGHT_GREEN_600",
                    "type": "variable",
                    "doc": "LIGHT_GREEN_600: number;"
                },
                {
                    "name": "LIGHT_GREEN_700",
                    "type": "variable",
                    "doc": "LIGHT_GREEN_700: number;"
                },
                {
                    "name": "LIGHT_GREEN_800",
                    "type": "variable",
                    "doc": "LIGHT_GREEN_800: number;"
                },
                {
                    "name": "LIGHT_GREEN_900",
                    "type": "variable",
                    "doc": "LIGHT_GREEN_900: number;"
                },
                {
                    "name": "LIGHT_GREEN_A100",
                    "type": "variable",
                    "doc": "LIGHT_GREEN_A100: number;"
                },
                {
                    "name": "LIGHT_GREEN_A200",
                    "type": "variable",
                    "doc": "LIGHT_GREEN_A200: number;"
                },
                {
                    "name": "LIGHT_GREEN_A400",
                    "type": "variable",
                    "doc": "LIGHT_GREEN_A400: number;"
                },
                {
                    "name": "LIGHT_GREEN_A700",
                    "type": "variable",
                    "doc": "LIGHT_GREEN_A700: number;"
                },
                {
                    "name": "LIGHT_GREY",
                    "type": "variable",
                    "doc": "LIGHT_GREY: number;"
                },
                {
                    "name": "LIGHT_KHAKI",
                    "type": "variable",
                    "doc": "LIGHT_KHAKI: number;"
                },
                {
                    "name": "LIGHT_LIME",
                    "type": "variable",
                    "doc": "LIGHT_LIME: number;"
                },
                {
                    "name": "LIGHT_PINK",
                    "type": "variable",
                    "doc": "LIGHT_PINK: number;"
                },
                {
                    "name": "LIGHT_SALMON",
                    "type": "variable",
                    "doc": "LIGHT_SALMON: number;"
                },
                {
                    "name": "LIGHT_SEA_GREEN",
                    "type": "variable",
                    "doc": "LIGHT_SEA_GREEN: number;"
                },
                {
                    "name": "LIGHT_SKY_BLUE",
                    "type": "variable",
                    "doc": "LIGHT_SKY_BLUE: number;"
                },
                {
                    "name": "LIGHT_SLATE_GRAY",
                    "type": "variable",
                    "doc": "LIGHT_SLATE_GRAY: number;"
                },
                {
                    "name": "LIGHT_SLATE_GREY",
                    "type": "variable",
                    "doc": "LIGHT_SLATE_GREY: number;"
                },
                {
                    "name": "LIGHT_STEEL_BLUE",
                    "type": "variable",
                    "doc": "LIGHT_STEEL_BLUE: number;"
                },
                {
                    "name": "LIGHT_VIOLET",
                    "type": "variable",
                    "doc": "LIGHT_VIOLET: number;"
                },
                {
                    "name": "LIGHT_YELLOW",
                    "type": "variable",
                    "doc": "LIGHT_YELLOW: number;"
                },
                {
                    "name": "LILAC",
                    "type": "variable",
                    "doc": "LILAC: number;"
                },
                {
                    "name": "LIME",
                    "type": "variable",
                    "doc": "LIME: number;"
                },
                {
                    "name": "LIME_50",
                    "type": "variable",
                    "doc": "LIME_50: number;"
                },
                {
                    "name": "LIME_100",
                    "type": "variable",
                    "doc": "LIME_100: number;"
                },
                {
                    "name": "LIME_200",
                    "type": "variable",
                    "doc": "LIME_200: number;"
                },
                {
                    "name": "LIME_300",
                    "type": "variable",
                    "doc": "LIME_300: number;"
                },
                {
                    "name": "LIME_400",
                    "type": "variable",
                    "doc": "LIME_400: number;"
                },
                {
                    "name": "LIME_500",
                    "type": "variable",
                    "doc": "LIME_500: number;"
                },
                {
                    "name": "LIME_600",
                    "type": "variable",
                    "doc": "LIME_600: number;"
                },
                {
                    "name": "LIME_700",
                    "type": "variable",
                    "doc": "LIME_700: number;"
                },
                {
                    "name": "LIME_800",
                    "type": "variable",
                    "doc": "LIME_800: number;"
                },
                {
                    "name": "LIME_900",
                    "type": "variable",
                    "doc": "LIME_900: number;"
                },
                {
                    "name": "LIME_A100",
                    "type": "variable",
                    "doc": "LIME_A100: number;"
                },
                {
                    "name": "LIME_A200",
                    "type": "variable",
                    "doc": "LIME_A200: number;"
                },
                {
                    "name": "LIME_A400",
                    "type": "variable",
                    "doc": "LIME_A400: number;"
                },
                {
                    "name": "LIME_A700",
                    "type": "variable",
                    "doc": "LIME_A700: number;"
                },
                {
                    "name": "LIME_GREEN",
                    "type": "variable",
                    "doc": "LIME_GREEN: number;"
                },
                {
                    "name": "LINEN",
                    "type": "variable",
                    "doc": "LINEN: number;"
                },
                {
                    "name": "LTGRAY",
                    "type": "variable",
                    "doc": "LTGRAY: number;"
                },
                {
                    "name": "MAGENTA",
                    "type": "variable",
                    "doc": "MAGENTA: number;"
                },
                {
                    "name": "MAGENTA_ROSE",
                    "type": "variable",
                    "doc": "MAGENTA_ROSE: number;"
                },
                {
                    "name": "MALACHITE",
                    "type": "variable",
                    "doc": "MALACHITE: number;"
                },
                {
                    "name": "MALLOW",
                    "type": "variable",
                    "doc": "MALLOW: number;"
                },
                {
                    "name": "MARIGOLD",
                    "type": "variable",
                    "doc": "MARIGOLD: number;"
                },
                {
                    "name": "MARINE_BLUE",
                    "type": "variable",
                    "doc": "MARINE_BLUE: number;"
                },
                {
                    "name": "MAROON",
                    "type": "variable",
                    "doc": "MAROON: number;"
                },
                {
                    "name": "MAUVE",
                    "type": "variable",
                    "doc": "MAUVE: number;"
                },
                {
                    "name": "MEDIUM_AQUAMARINE",
                    "type": "variable",
                    "doc": "MEDIUM_AQUAMARINE: number;"
                },
                {
                    "name": "MEDIUM_BLUE",
                    "type": "variable",
                    "doc": "MEDIUM_BLUE: number;"
                },
                {
                    "name": "MEDIUM_LAVENDER_MAGENTA",
                    "type": "variable",
                    "doc": "MEDIUM_LAVENDER_MAGENTA: number;"
                },
                {
                    "name": "MEDIUM_ORCHID",
                    "type": "variable",
                    "doc": "MEDIUM_ORCHID: number;"
                },
                {
                    "name": "MEDIUM_PURPLE",
                    "type": "variable",
                    "doc": "MEDIUM_PURPLE: number;"
                },
                {
                    "name": "MEDIUM_SEA_GREEN",
                    "type": "variable",
                    "doc": "MEDIUM_SEA_GREEN: number;"
                },
                {
                    "name": "MEDIUM_SLATE_BLUE",
                    "type": "variable",
                    "doc": "MEDIUM_SLATE_BLUE: number;"
                },
                {
                    "name": "MEDIUM_SPRING_GREEN",
                    "type": "variable",
                    "doc": "MEDIUM_SPRING_GREEN: number;"
                },
                {
                    "name": "MEDIUM_TURQUOISE",
                    "type": "variable",
                    "doc": "MEDIUM_TURQUOISE: number;"
                },
                {
                    "name": "MEDIUM_VIOLET_RED",
                    "type": "variable",
                    "doc": "MEDIUM_VIOLET_RED: number;"
                },
                {
                    "name": "MIDNIGHT_BLUE",
                    "type": "variable",
                    "doc": "MIDNIGHT_BLUE: number;"
                },
                {
                    "name": "MIMOSA",
                    "type": "variable",
                    "doc": "MIMOSA: number;"
                },
                {
                    "name": "MINERAL_BLUE",
                    "type": "variable",
                    "doc": "MINERAL_BLUE: number;"
                },
                {
                    "name": "MINERAL_VIOLET",
                    "type": "variable",
                    "doc": "MINERAL_VIOLET: number;"
                },
                {
                    "name": "MINT",
                    "type": "variable",
                    "doc": "MINT: number;"
                },
                {
                    "name": "MINT_CREAM",
                    "type": "variable",
                    "doc": "MINT_CREAM: number;"
                },
                {
                    "name": "MISTY_ROSE",
                    "type": "variable",
                    "doc": "MISTY_ROSE: number;"
                },
                {
                    "name": "MOCCASIN",
                    "type": "variable",
                    "doc": "MOCCASIN: number;"
                },
                {
                    "name": "MOON_YELLOW",
                    "type": "variable",
                    "doc": "MOON_YELLOW: number;"
                },
                {
                    "name": "MOSS_GREEN",
                    "type": "variable",
                    "doc": "MOSS_GREEN: number;"
                },
                {
                    "name": "MUSTARD",
                    "type": "variable",
                    "doc": "MUSTARD: number;"
                },
                {
                    "name": "NAVAJO_WHITE",
                    "type": "variable",
                    "doc": "NAVAJO_WHITE: number;"
                },
                {
                    "name": "NAVY",
                    "type": "variable",
                    "doc": "NAVY: number;"
                },
                {
                    "name": "NAVY_BLUE",
                    "type": "variable",
                    "doc": "NAVY_BLUE: number;"
                },
                {
                    "name": "OCHER",
                    "type": "variable",
                    "doc": "OCHER: number;"
                },
                {
                    "name": "OLD_LACE",
                    "type": "variable",
                    "doc": "OLD_LACE: number;"
                },
                {
                    "name": "OLD_ROSE",
                    "type": "variable",
                    "doc": "OLD_ROSE: number;"
                },
                {
                    "name": "OLIVE",
                    "type": "variable",
                    "doc": "OLIVE: number;"
                },
                {
                    "name": "OLIVE_DRAB",
                    "type": "variable",
                    "doc": "OLIVE_DRAB: number;"
                },
                {
                    "name": "OPERA_MAUVE",
                    "type": "variable",
                    "doc": "OPERA_MAUVE: number;"
                },
                {
                    "name": "ORANGE",
                    "type": "variable",
                    "doc": "ORANGE: number;"
                },
                {
                    "name": "ORANGE_50",
                    "type": "variable",
                    "doc": "ORANGE_50: number;"
                },
                {
                    "name": "ORANGE_100",
                    "type": "variable",
                    "doc": "ORANGE_100: number;"
                },
                {
                    "name": "ORANGE_200",
                    "type": "variable",
                    "doc": "ORANGE_200: number;"
                },
                {
                    "name": "ORANGE_300",
                    "type": "variable",
                    "doc": "ORANGE_300: number;"
                },
                {
                    "name": "ORANGE_400",
                    "type": "variable",
                    "doc": "ORANGE_400: number;"
                },
                {
                    "name": "ORANGE_500",
                    "type": "variable",
                    "doc": "ORANGE_500: number;"
                },
                {
                    "name": "ORANGE_600",
                    "type": "variable",
                    "doc": "ORANGE_600: number;"
                },
                {
                    "name": "ORANGE_700",
                    "type": "variable",
                    "doc": "ORANGE_700: number;"
                },
                {
                    "name": "ORANGE_800",
                    "type": "variable",
                    "doc": "ORANGE_800: number;"
                },
                {
                    "name": "ORANGE_900",
                    "type": "variable",
                    "doc": "ORANGE_900: number;"
                },
                {
                    "name": "ORANGE_A100",
                    "type": "variable",
                    "doc": "ORANGE_A100: number;"
                },
                {
                    "name": "ORANGE_A200",
                    "type": "variable",
                    "doc": "ORANGE_A200: number;"
                },
                {
                    "name": "ORANGE_A400",
                    "type": "variable",
                    "doc": "ORANGE_A400: number;"
                },
                {
                    "name": "ORANGE_A700",
                    "type": "variable",
                    "doc": "ORANGE_A700: number;"
                },
                {
                    "name": "ORANGE_RED",
                    "type": "variable",
                    "doc": "ORANGE_RED: number;"
                },
                {
                    "name": "ORCHID",
                    "type": "variable",
                    "doc": "ORCHID: number;"
                },
                {
                    "name": "PAIL_LILAC",
                    "type": "variable",
                    "doc": "PAIL_LILAC: number;"
                },
                {
                    "name": "PALE_BLUE",
                    "type": "variable",
                    "doc": "PALE_BLUE: number;"
                },
                {
                    "name": "PALE_DENIM",
                    "type": "variable",
                    "doc": "PALE_DENIM: number;"
                },
                {
                    "name": "PALE_GOLDENROD",
                    "type": "variable",
                    "doc": "PALE_GOLDENROD: number;"
                },
                {
                    "name": "PALE_GREEN",
                    "type": "variable",
                    "doc": "PALE_GREEN: number;"
                },
                {
                    "name": "PALE_OCHRE",
                    "type": "variable",
                    "doc": "PALE_OCHRE: number;"
                },
                {
                    "name": "PALE_TURQUOISE",
                    "type": "variable",
                    "doc": "PALE_TURQUOISE: number;"
                },
                {
                    "name": "PALE_VIOLET_RED",
                    "type": "variable",
                    "doc": "PALE_VIOLET_RED: number;"
                },
                {
                    "name": "PANSY",
                    "type": "variable",
                    "doc": "PANSY: number;"
                },
                {
                    "name": "PAPAYA_WHIP",
                    "type": "variable",
                    "doc": "PAPAYA_WHIP: number;"
                },
                {
                    "name": "PATRIARCH",
                    "type": "variable",
                    "doc": "PATRIARCH: number;"
                },
                {
                    "name": "PEACH",
                    "type": "variable",
                    "doc": "PEACH: number;"
                },
                {
                    "name": "PEACH_PEARL",
                    "type": "variable",
                    "doc": "PEACH_PEARL: number;"
                },
                {
                    "name": "PEACH_PUFF",
                    "type": "variable",
                    "doc": "PEACH_PUFF: number;"
                },
                {
                    "name": "PEACOCK_BLUE",
                    "type": "variable",
                    "doc": "PEACOCK_BLUE: number;"
                },
                {
                    "name": "PEACOCK_GREEN",
                    "type": "variable",
                    "doc": "PEACOCK_GREEN: number;"
                },
                {
                    "name": "PEARL_PINK",
                    "type": "variable",
                    "doc": "PEARL_PINK: number;"
                },
                {
                    "name": "PERIWINKLE",
                    "type": "variable",
                    "doc": "PERIWINKLE: number;"
                },
                {
                    "name": "PERSIMMON",
                    "type": "variable",
                    "doc": "PERSIMMON: number;"
                },
                {
                    "name": "PERU",
                    "type": "variable",
                    "doc": "PERU: number;"
                },
                {
                    "name": "PINK",
                    "type": "variable",
                    "doc": "PINK: number;"
                },
                {
                    "name": "PINK_50",
                    "type": "variable",
                    "doc": "PINK_50: number;"
                },
                {
                    "name": "PINK_100",
                    "type": "variable",
                    "doc": "PINK_100: number;"
                },
                {
                    "name": "PINK_200",
                    "type": "variable",
                    "doc": "PINK_200: number;"
                },
                {
                    "name": "PINK_300",
                    "type": "variable",
                    "doc": "PINK_300: number;"
                },
                {
                    "name": "PINK_400",
                    "type": "variable",
                    "doc": "PINK_400: number;"
                },
                {
                    "name": "PINK_500",
                    "type": "variable",
                    "doc": "PINK_500: number;"
                },
                {
                    "name": "PINK_600",
                    "type": "variable",
                    "doc": "PINK_600: number;"
                },
                {
                    "name": "PINK_700",
                    "type": "variable",
                    "doc": "PINK_700: number;"
                },
                {
                    "name": "PINK_800",
                    "type": "variable",
                    "doc": "PINK_800: number;"
                },
                {
                    "name": "PINK_900",
                    "type": "variable",
                    "doc": "PINK_900: number;"
                },
                {
                    "name": "PINK_A100",
                    "type": "variable",
                    "doc": "PINK_A100: number;"
                },
                {
                    "name": "PINK_A200",
                    "type": "variable",
                    "doc": "PINK_A200: number;"
                },
                {
                    "name": "PINK_A400",
                    "type": "variable",
                    "doc": "PINK_A400: number;"
                },
                {
                    "name": "PINK_A700",
                    "type": "variable",
                    "doc": "PINK_A700: number;"
                },
                {
                    "name": "PLUM",
                    "type": "variable",
                    "doc": "PLUM: number;"
                },
                {
                    "name": "POWDER_BLUE",
                    "type": "variable",
                    "doc": "POWDER_BLUE: number;"
                },
                {
                    "name": "PRUSSIAN_BLUE",
                    "type": "variable",
                    "doc": "PRUSSIAN_BLUE: number;"
                },
                {
                    "name": "PURPLE",
                    "type": "variable",
                    "doc": "PURPLE: number;"
                },
                {
                    "name": "PURPLE_50",
                    "type": "variable",
                    "doc": "PURPLE_50: number;"
                },
                {
                    "name": "PURPLE_100",
                    "type": "variable",
                    "doc": "PURPLE_100: number;"
                },
                {
                    "name": "PURPLE_200",
                    "type": "variable",
                    "doc": "PURPLE_200: number;"
                },
                {
                    "name": "PURPLE_300",
                    "type": "variable",
                    "doc": "PURPLE_300: number;"
                },
                {
                    "name": "PURPLE_400",
                    "type": "variable",
                    "doc": "PURPLE_400: number;"
                },
                {
                    "name": "PURPLE_500",
                    "type": "variable",
                    "doc": "PURPLE_500: number;"
                },
                {
                    "name": "PURPLE_600",
                    "type": "variable",
                    "doc": "PURPLE_600: number;"
                },
                {
                    "name": "PURPLE_700",
                    "type": "variable",
                    "doc": "PURPLE_700: number;"
                },
                {
                    "name": "PURPLE_800",
                    "type": "variable",
                    "doc": "PURPLE_800: number;"
                },
                {
                    "name": "PURPLE_900",
                    "type": "variable",
                    "doc": "PURPLE_900: number;"
                },
                {
                    "name": "PURPLE_A100",
                    "type": "variable",
                    "doc": "PURPLE_A100: number;"
                },
                {
                    "name": "PURPLE_A200",
                    "type": "variable",
                    "doc": "PURPLE_A200: number;"
                },
                {
                    "name": "PURPLE_A400",
                    "type": "variable",
                    "doc": "PURPLE_A400: number;"
                },
                {
                    "name": "PURPLE_A700",
                    "type": "variable",
                    "doc": "PURPLE_A700: number;"
                },
                {
                    "name": "REBECCA_PURPLE",
                    "type": "variable",
                    "doc": "REBECCA_PURPLE: number;"
                },
                {
                    "name": "RED",
                    "type": "variable",
                    "doc": "RED: number;"
                },
                {
                    "name": "RED_50",
                    "type": "variable",
                    "doc": "RED_50: number;"
                },
                {
                    "name": "RED_100",
                    "type": "variable",
                    "doc": "RED_100: number;"
                },
                {
                    "name": "RED_200",
                    "type": "variable",
                    "doc": "RED_200: number;"
                },
                {
                    "name": "RED_300",
                    "type": "variable",
                    "doc": "RED_300: number;"
                },
                {
                    "name": "RED_400",
                    "type": "variable",
                    "doc": "RED_400: number;"
                },
                {
                    "name": "RED_500",
                    "type": "variable",
                    "doc": "RED_500: number;"
                },
                {
                    "name": "RED_600",
                    "type": "variable",
                    "doc": "RED_600: number;"
                },
                {
                    "name": "RED_700",
                    "type": "variable",
                    "doc": "RED_700: number;"
                },
                {
                    "name": "RED_800",
                    "type": "variable",
                    "doc": "RED_800: number;"
                },
                {
                    "name": "RED_900",
                    "type": "variable",
                    "doc": "RED_900: number;"
                },
                {
                    "name": "RED_A100",
                    "type": "variable",
                    "doc": "RED_A100: number;"
                },
                {
                    "name": "RED_A200",
                    "type": "variable",
                    "doc": "RED_A200: number;"
                },
                {
                    "name": "RED_A400",
                    "type": "variable",
                    "doc": "RED_A400: number;"
                },
                {
                    "name": "RED_A700",
                    "type": "variable",
                    "doc": "RED_A700: number;"
                },
                {
                    "name": "ROSE",
                    "type": "variable",
                    "doc": "ROSE: number;"
                },
                {
                    "name": "ROSE_PINK",
                    "type": "variable",
                    "doc": "ROSE_PINK: number;"
                },
                {
                    "name": "ROSY_BROWN",
                    "type": "variable",
                    "doc": "ROSY_BROWN: number;"
                },
                {
                    "name": "ROYAL_BLUE",
                    "type": "variable",
                    "doc": "ROYAL_BLUE: number;"
                },
                {
                    "name": "RUBY",
                    "type": "variable",
                    "doc": "RUBY: number;"
                },
                {
                    "name": "SADDLE_BROWN",
                    "type": "variable",
                    "doc": "SADDLE_BROWN: number;"
                },
                {
                    "name": "SALMON",
                    "type": "variable",
                    "doc": "SALMON: number;"
                },
                {
                    "name": "SALMON_PINK",
                    "type": "variable",
                    "doc": "SALMON_PINK: number;"
                },
                {
                    "name": "SALVIA_BLUE",
                    "type": "variable",
                    "doc": "SALVIA_BLUE: number;"
                },
                {
                    "name": "SAND_BEIGE",
                    "type": "variable",
                    "doc": "SAND_BEIGE: number;"
                },
                {
                    "name": "SAND_BROWN",
                    "type": "variable",
                    "doc": "SAND_BROWN: number;"
                },
                {
                    "name": "SAPPHIRE",
                    "type": "variable",
                    "doc": "SAPPHIRE: number;"
                },
                {
                    "name": "SAXE_BLUE",
                    "type": "variable",
                    "doc": "SAXE_BLUE: number;"
                },
                {
                    "name": "SCARLET",
                    "type": "variable",
                    "doc": "SCARLET: number;"
                },
                {
                    "name": "SEASHELL",
                    "type": "variable",
                    "doc": "SEASHELL: number;"
                },
                {
                    "name": "SEA_GREEN",
                    "type": "variable",
                    "doc": "SEA_GREEN: number;"
                },
                {
                    "name": "SEPIA",
                    "type": "variable",
                    "doc": "SEPIA: number;"
                },
                {
                    "name": "SHELL_PINK",
                    "type": "variable",
                    "doc": "SHELL_PINK: number;"
                },
                {
                    "name": "SIENNA",
                    "type": "variable",
                    "doc": "SIENNA: number;"
                },
                {
                    "name": "SILVER",
                    "type": "variable",
                    "doc": "SILVER: number;"
                },
                {
                    "name": "SKY_BLUE",
                    "type": "variable",
                    "doc": "SKY_BLUE: number;"
                },
                {
                    "name": "SLATE_BLUE",
                    "type": "variable",
                    "doc": "SLATE_BLUE: number;"
                },
                {
                    "name": "SLATE_GRAY",
                    "type": "variable",
                    "doc": "SLATE_GRAY: number;"
                },
                {
                    "name": "SLATE_GREY",
                    "type": "variable",
                    "doc": "SLATE_GREY: number;"
                },
                {
                    "name": "SNOW",
                    "type": "variable",
                    "doc": "SNOW: number;"
                },
                {
                    "name": "SPINEL_RED",
                    "type": "variable",
                    "doc": "SPINEL_RED: number;"
                },
                {
                    "name": "SPRING_GREEN",
                    "type": "variable",
                    "doc": "SPRING_GREEN: number;"
                },
                {
                    "name": "STEEL_BLUE",
                    "type": "variable",
                    "doc": "STEEL_BLUE: number;"
                },
                {
                    "name": "STRONG_BLUE",
                    "type": "variable",
                    "doc": "STRONG_BLUE: number;"
                },
                {
                    "name": "STRONG_RED",
                    "type": "variable",
                    "doc": "STRONG_RED: number;"
                },
                {
                    "name": "SUN_ORANGE",
                    "type": "variable",
                    "doc": "SUN_ORANGE: number;"
                },
                {
                    "name": "TAN",
                    "type": "variable",
                    "doc": "TAN: number;"
                },
                {
                    "name": "TANGERINE",
                    "type": "variable",
                    "doc": "TANGERINE: number;"
                },
                {
                    "name": "TANGERINE_YELLOW",
                    "type": "variable",
                    "doc": "TANGERINE_YELLOW: number;"
                },
                {
                    "name": "TEAL",
                    "type": "variable",
                    "doc": "TEAL: number;"
                },
                {
                    "name": "TEAL_50",
                    "type": "variable",
                    "doc": "TEAL_50: number;"
                },
                {
                    "name": "TEAL_100",
                    "type": "variable",
                    "doc": "TEAL_100: number;"
                },
                {
                    "name": "TEAL_200",
                    "type": "variable",
                    "doc": "TEAL_200: number;"
                },
                {
                    "name": "TEAL_300",
                    "type": "variable",
                    "doc": "TEAL_300: number;"
                },
                {
                    "name": "TEAL_400",
                    "type": "variable",
                    "doc": "TEAL_400: number;"
                },
                {
                    "name": "TEAL_500",
                    "type": "variable",
                    "doc": "TEAL_500: number;"
                },
                {
                    "name": "TEAL_600",
                    "type": "variable",
                    "doc": "TEAL_600: number;"
                },
                {
                    "name": "TEAL_700",
                    "type": "variable",
                    "doc": "TEAL_700: number;"
                },
                {
                    "name": "TEAL_800",
                    "type": "variable",
                    "doc": "TEAL_800: number;"
                },
                {
                    "name": "TEAL_900",
                    "type": "variable",
                    "doc": "TEAL_900: number;"
                },
                {
                    "name": "TEAL_A100",
                    "type": "variable",
                    "doc": "TEAL_A100: number;"
                },
                {
                    "name": "TEAL_A200",
                    "type": "variable",
                    "doc": "TEAL_A200: number;"
                },
                {
                    "name": "TEAL_A400",
                    "type": "variable",
                    "doc": "TEAL_A400: number;"
                },
                {
                    "name": "TEAL_A700",
                    "type": "variable",
                    "doc": "TEAL_A700: number;"
                },
                {
                    "name": "THISTLE",
                    "type": "variable",
                    "doc": "THISTLE: number;"
                },
                {
                    "name": "TOMATO",
                    "type": "variable",
                    "doc": "TOMATO: number;"
                },
                {
                    "name": "TRANSPARENT",
                    "type": "variable",
                    "doc": "TRANSPARENT: number;"
                },
                {
                    "name": "TROPICAL_ORANGE",
                    "type": "variable",
                    "doc": "TROPICAL_ORANGE: number;"
                },
                {
                    "name": "TURQUOISE",
                    "type": "variable",
                    "doc": "TURQUOISE: number;"
                },
                {
                    "name": "TURQUOISE_BLUE",
                    "type": "variable",
                    "doc": "TURQUOISE_BLUE: number;"
                },
                {
                    "name": "TURQUOISE_GREEN",
                    "type": "variable",
                    "doc": "TURQUOISE_GREEN: number;"
                },
                {
                    "name": "ULTRAMARINE",
                    "type": "variable",
                    "doc": "ULTRAMARINE: number;"
                },
                {
                    "name": "VERMILION",
                    "type": "variable",
                    "doc": "VERMILION: number;"
                },
                {
                    "name": "VERY_LIGHT_MALACHITE_GREEN",
                    "type": "variable",
                    "doc": "VERY_LIGHT_MALACHITE_GREEN: number;"
                },
                {
                    "name": "VIOLET",
                    "type": "variable",
                    "doc": "VIOLET: number;"
                },
                {
                    "name": "VIRIDIAN",
                    "type": "variable",
                    "doc": "VIRIDIAN: number;"
                },
                {
                    "name": "WEDGWOOD_BLUE",
                    "type": "variable",
                    "doc": "WEDGWOOD_BLUE: number;"
                },
                {
                    "name": "WHEAT",
                    "type": "variable",
                    "doc": "WHEAT: number;"
                },
                {
                    "name": "WHITE",
                    "type": "variable",
                    "doc": "WHITE: number;"
                },
                {
                    "name": "WHITE_1000",
                    "type": "variable",
                    "doc": "WHITE_1000: number;"
                },
                {
                    "name": "WHITE_SMOKE",
                    "type": "variable",
                    "doc": "WHITE_SMOKE: number;"
                },
                {
                    "name": "WISTERIA",
                    "type": "variable",
                    "doc": "WISTERIA: number;"
                },
                {
                    "name": "YELLOW",
                    "type": "variable",
                    "doc": "YELLOW: number;"
                },
                {
                    "name": "YELLOW_50",
                    "type": "variable",
                    "doc": "YELLOW_50: number;"
                },
                {
                    "name": "YELLOW_100",
                    "type": "variable",
                    "doc": "YELLOW_100: number;"
                },
                {
                    "name": "YELLOW_200",
                    "type": "variable",
                    "doc": "YELLOW_200: number;"
                },
                {
                    "name": "YELLOW_300",
                    "type": "variable",
                    "doc": "YELLOW_300: number;"
                },
                {
                    "name": "YELLOW_400",
                    "type": "variable",
                    "doc": "YELLOW_400: number;"
                },
                {
                    "name": "YELLOW_500",
                    "type": "variable",
                    "doc": "YELLOW_500: number;"
                },
                {
                    "name": "YELLOW_600",
                    "type": "variable",
                    "doc": "YELLOW_600: number;"
                },
                {
                    "name": "YELLOW_700",
                    "type": "variable",
                    "doc": "YELLOW_700: number;"
                },
                {
                    "name": "YELLOW_800",
                    "type": "variable",
                    "doc": "YELLOW_800: number;"
                },
                {
                    "name": "YELLOW_900",
                    "type": "variable",
                    "doc": "YELLOW_900: number;"
                },
                {
                    "name": "YELLOW_A100",
                    "type": "variable",
                    "doc": "YELLOW_A100: number;"
                },
                {
                    "name": "YELLOW_A200",
                    "type": "variable",
                    "doc": "YELLOW_A200: number;"
                },
                {
                    "name": "YELLOW_A400",
                    "type": "variable",
                    "doc": "YELLOW_A400: number;"
                },
                {
                    "name": "YELLOW_A700",
                    "type": "variable",
                    "doc": "YELLOW_A700: number;"
                },
                {
                    "name": "YELLOW_GREEN",
                    "type": "variable",
                    "doc": "YELLOW_GREEN: number;"
                },
                {
                    "name": "alpha",
                    "type": "function",
                    "doc": "alpha(color: OmniColor, options?: ComponentOptions): number;"
                },
                {
                    "name": "getAlpha",
                    "type": "function",
                    "doc": "getAlpha(color: OmniColor, options?: ComponentOptions): number;"
                },
                {
                    "name": "alphaDouble",
                    "type": "function",
                    "doc": "alphaDouble(color: OmniColor): number;"
                },
                {
                    "name": "getAlphaDouble",
                    "type": "function",
                    "doc": "getAlphaDouble(color: OmniColor): number;"
                },
                {
                    "name": "red",
                    "type": "function",
                    "doc": "red(color: OmniColor, options?: ComponentOptions): number;"
                },
                {
                    "name": "getRed",
                    "type": "function",
                    "doc": "getRed(color: OmniColor, options?: ComponentOptions): number;"
                },
                {
                    "name": "redDouble",
                    "type": "function",
                    "doc": "redDouble(color: OmniColor): number;"
                },
                {
                    "name": "getRedDouble",
                    "type": "function",
                    "doc": "getRedDouble(color: OmniColor): number;"
                },
                {
                    "name": "green",
                    "type": "function",
                    "doc": "green(color: OmniColor, options?: ComponentOptions): number;"
                },
                {
                    "name": "getGreen",
                    "type": "function",
                    "doc": "getGreen(color: OmniColor, options?: ComponentOptions): number;"
                },
                {
                    "name": "greenDouble",
                    "type": "function",
                    "doc": "greenDouble(color: OmniColor): number;"
                },
                {
                    "name": "getGreenDouble",
                    "type": "function",
                    "doc": "getGreenDouble(color: OmniColor): number;"
                },
                {
                    "name": "blue",
                    "type": "function",
                    "doc": "blue(color: OmniColor, options?: ComponentOptions): number;"
                },
                {
                    "name": "getBlue",
                    "type": "function",
                    "doc": "getBlue(color: OmniColor, options?: ComponentOptions): number;"
                },
                {
                    "name": "blueDouble",
                    "type": "function",
                    "doc": "blueDouble(color: OmniColor): number;"
                },
                {
                    "name": "getBlueDouble",
                    "type": "function",
                    "doc": "getBlueDouble(color: OmniColor): number;"
                },
                {
                    "name": "setAlpha",
                    "type": "function",
                    "doc": "setAlpha(color: OmniColor, alpha: ColorComponent): number;"
                },
                {
                    "name": "setAlphaRelative",
                    "type": "function",
                    "doc": "setAlphaRelative(color: OmniColor, percentage: number | PercentString): number;"
                },
                {
                    "name": "removeAlpha",
                    "type": "function",
                    "doc": "removeAlpha(color: OmniColor): number;"
                },
                {
                    "name": "setRed",
                    "type": "function",
                    "doc": "setRed(color: OmniColor, red: ColorComponent): number;"
                },
                {
                    "name": "setRedRelative",
                    "type": "function",
                    "doc": "setRedRelative(color: OmniColor, percentage: number | PercentString): number;"
                },
                {
                    "name": "removeRed",
                    "type": "function",
                    "doc": "removeRed(color: OmniColor): number;"
                },
                {
                    "name": "setGreen",
                    "type": "function",
                    "doc": "setGreen(color: OmniColor, green: ColorComponent): number;"
                },
                {
                    "name": "setGreenRelative",
                    "type": "function",
                    "doc": "setGreenRelative(color: OmniColor, percentage: number | PercentString): number;"
                },
                {
                    "name": "removeGreen",
                    "type": "function",
                    "doc": "removeGreen(color: OmniColor): number;"
                },
                {
                    "name": "setBlue",
                    "type": "function",
                    "doc": "setBlue(color: OmniColor, blue: ColorComponent): number;"
                },
                {
                    "name": "setBlueRelative",
                    "type": "function",
                    "doc": "setBlueRelative(color: OmniColor, percentage: number | PercentString): number;"
                },
                {
                    "name": "removeBlue",
                    "type": "function",
                    "doc": "removeBlue(color: OmniColor): number;"
                },
                {
                    "name": "toInt",
                    "type": "function",
                    "doc": "toInt(color: OmniColor): number;"
                },
                {
                    "name": "parseColor",
                    "type": "function",
                    "doc": "parseColor(colorString: string): number;"
                },
                {
                    "name": "toHex",
                    "type": "function",
                    "doc": "toHex(color: OmniColor, alpha?: boolean | 'auto' | 'none' | 'keep'): string;"
                },
                {
                    "name": "toString",
                    "type": "function",
                    "doc": "toString(color: OmniColor, alpha?: boolean | 'auto' | 'none' | 'keep'): string;"
                },
                {
                    "name": "toFullHex",
                    "type": "function",
                    "doc": "toFullHex(color: OmniColor): string;"
                },
                {
                    "name": "rgb",
                    "type": "function",
                    "doc": "rgb(red: ColorComponent, green: ColorComponent, blue: ColorComponent): number;"
                },
                {
                    "name": "argb",
                    "type": "function",
                    "doc": "argb(alpha: ColorComponent, red: ColorComponent, green: ColorComponent, blue: ColorComponent): number;"
                },
                {
                    "name": "rgba",
                    "type": "function",
                    "doc": "rgba(red: ColorComponent, green: ColorComponent, blue: ColorComponent, alpha: ColorComponent): number;"
                },
                {
                    "name": "hsv",
                    "type": "function",
                    "doc": "hsv(h: ColorHueComponent, s: ColorComponent, v: ColorComponent): number;"
                },
                {
                    "name": "hsva",
                    "type": "function",
                    "doc": "hsva(h: ColorHueComponent, s: ColorComponent, v: ColorComponent, a: ColorComponent): number;"
                },
                {
                    "name": "hsl",
                    "type": "function",
                    "doc": "hsl(h: ColorHueComponent, s: ColorComponent, l: ColorComponent): number;"
                },
                {
                    "name": "hsla",
                    "type": "function",
                    "doc": "hsla(h: ColorHueComponent, s: ColorComponent, l: ColorComponent, a: ColorComponent): number;"
                },
                {
                    "name": "toRgb",
                    "type": "function",
                    "doc": "toRgb(color: OmniColor): number[];"
                },
                {
                    "name": "toRgba",
                    "type": "function",
                    "doc": "toRgba(color: OmniColor, options?: ToComponentOptions): number[];"
                },
                {
                    "name": "toArgb",
                    "type": "function",
                    "doc": "toArgb(color: OmniColor, options?: ToComponentOptions): number[];"
                },
                {
                    "name": "toHsv",
                    "type": "function",
                    "doc": "toHsv(color: OmniColor): number[];"
                },
                {
                    "name": "toHsva",
                    "type": "function",
                    "doc": "toHsva(color: OmniColor): number[];"
                },
                {
                    "name": "toHsl",
                    "type": "function",
                    "doc": "toHsl(color: OmniColor): number[];"
                },
                {
                    "name": "toHsla",
                    "type": "function",
                    "doc": "toHsla(color: OmniColor): number[];"
                },
                {
                    "name": "toRgbString",
                    "type": "function",
                    "doc": "toRgbString(color: OmniColor): string;"
                },
                {
                    "name": "toRgbaString",
                    "type": "function",
                    "doc": "toRgbaString(color: OmniColor, options?: boolean | StringOptions): string;"
                },
                {
                    "name": "toArgbString",
                    "type": "function",
                    "doc": "toArgbString(color: OmniColor, options?: boolean | StringOptions): string;"
                },
                {
                    "name": "toHsvString",
                    "type": "function",
                    "doc": "toHsvString(color: OmniColor): string;"
                },
                {
                    "name": "toHsvaString",
                    "type": "function",
                    "doc": "toHsvaString(color: OmniColor, options?: boolean | StringOptions): string;"
                },
                {
                    "name": "toHslString",
                    "type": "function",
                    "doc": "toHslString(color: OmniColor): string;"
                },
                {
                    "name": "toHslaString",
                    "type": "function",
                    "doc": "toHslaString(color: OmniColor, options?: boolean | StringOptions): string;"
                },
                {
                    "name": "isSimilar",
                    "type": "function",
                    "doc": "isSimilar(colorA: OmniColor, colorB: OmniColor, threshold?: number, algorithm?: DetectionAlgorithm): boolean;"
                },
                {
                    "name": "isEqual",
                    "type": "function",
                    "doc": "isEqual(colorA: OmniColor, colorB: OmniColor, alphaMatters?: boolean): boolean;"
                },
                {
                    "name": "equals",
                    "type": "function",
                    "doc": "equals(colorA: OmniColor, colorB: OmniColor): boolean;"
                },
                {
                    "name": "luminance",
                    "type": "function",
                    "doc": "luminance(color: OmniColor): number;"
                },
                {
                    "name": "toColorStateList",
                    "type": "function",
                    "doc": "toColorStateList(...color: (OmniColor)[]): android.content.res.ColorStateList;"
                },
                {
                    "name": "setPaintColor",
                    "type": "function",
                    "doc": "setPaintColor(paint: Paint, color: OmniColor): void;"
                },
                {
                    "name": "build",
                    "type": "function",
                    "doc": "build(): Color;"
                },
                {
                    "name": "summary",
                    "type": "function",
                    "doc": "summary(color: OmniColor): string;"
                }
            ],
            "console": [
                {
                    "name": "assert",
                    "type": "function",
                    "doc": "assert(value: boolean | (() => boolean), message?: string): void;"
                },
                {
                    "name": "rawInput",
                    "type": "function",
                    "doc": "rawInput(): string;"
                },
                {
                    "name": "input",
                    "type": "function",
                    "doc": "input(...data): any;"
                },
                {
                    "name": "verbose",
                    "type": "function",
                    "doc": "verbose(data?: any, ...text): void;"
                },
                {
                    "name": "log",
                    "type": "function",
                    "doc": "log(data?: any, ...text): void;"
                },
                {
                    "name": "info",
                    "type": "function",
                    "doc": "info(data?: any, ...text): void;"
                },
                {
                    "name": "warn",
                    "type": "function",
                    "doc": "warn(data?: any, ...text): void;"
                },
                {
                    "name": "error",
                    "type": "function",
                    "doc": "error(data?: any, ...text): void;"
                },
                {
                    "name": "print",
                    "type": "function",
                    "doc": "print(data?: any, ...text): void;"
                },
                {
                    "name": "time",
                    "type": "function",
                    "doc": "time(label?: string): void;"
                },
                {
                    "name": "timeEnd",
                    "type": "function",
                    "doc": "timeEnd(label?: string): void;"
                },
                {
                    "name": "trace",
                    "type": "function",
                    "doc": "trace(message: string, ...args): void;"
                },
                {
                    "name": "setGlobalLogConfig",
                    "type": "function",
                    "doc": "setGlobalLogConfig(config: Console.GlobalLogConfig): void;"
                },
                {
                    "name": "resetGlobalLogConfig",
                    "type": "function",
                    "doc": "resetGlobalLogConfig(): void;"
                },
                {
                    "name": "show",
                    "type": "function",
                    "doc": "show(isAutoExpand?: boolean): this;"
                },
                {
                    "name": "hide",
                    "type": "function",
                    "doc": "hide(): this;"
                },
                {
                    "name": "clear",
                    "type": "function",
                    "doc": "clear(): this;"
                },
                {
                    "name": "reset",
                    "type": "function",
                    "doc": "reset(): this;"
                },
                {
                    "name": "expand",
                    "type": "function",
                    "doc": "expand(): this;"
                },
                {
                    "name": "collapse",
                    "type": "function",
                    "doc": "collapse(): this;"
                },
                {
                    "name": "launch",
                    "type": "function",
                    "doc": "launch(): void;"
                },
                {
                    "name": "build",
                    "type": "function",
                    "doc": "build(options?: Console.BuildOptions): org.autojs.autojs.core.console.ConsoleImpl.Configurator;"
                },
                {
                    "name": "setSize",
                    "type": "function",
                    "doc": "setSize(width: number, height: number): this;"
                },
                {
                    "name": "setPosition",
                    "type": "function",
                    "doc": "setPosition(x: number, y: number): this;"
                },
                {
                    "name": "setTitle",
                    "type": "function",
                    "doc": "setTitle(title: string): this;"
                },
                {
                    "name": "setTitleTextSize",
                    "type": "function",
                    "doc": "setTitleTextSize(size: number): this;"
                },
                {
                    "name": "setTitleTextColor",
                    "type": "function",
                    "doc": "setTitleTextColor(color: OmniColor): this;"
                },
                {
                    "name": "setTitleBackgroundColor",
                    "type": "function",
                    "doc": "setTitleBackgroundColor(color: OmniColor): this;"
                },
                {
                    "name": "setTitleBackgroundTint",
                    "type": "function",
                    "doc": "setTitleBackgroundTint(color: OmniColor): this;"
                },
                {
                    "name": "setTitleBackgroundAlpha",
                    "type": "function",
                    "doc": "setTitleBackgroundAlpha(alpha: number): this;"
                },
                {
                    "name": "resetTitleBackgroundAlpha",
                    "type": "function",
                    "doc": "resetTitleBackgroundAlpha(): this;"
                },
                {
                    "name": "setTitleIconsTint",
                    "type": "function",
                    "doc": "setTitleIconsTint(color: OmniColor): this;"
                },
                {
                    "name": "setContentTextSize",
                    "type": "function",
                    "doc": "setContentTextSize(size: number): this;"
                },
                {
                    "name": "setContentTextColor",
                    "type": "function",
                    "doc": "setContentTextColor(colors: OmniColor): this;"
                },
                {
                    "name": "setContentBackgroundColor",
                    "type": "function",
                    "doc": "setContentBackgroundColor(color: OmniColor): this;"
                },
                {
                    "name": "setContentBackgroundTint",
                    "type": "function",
                    "doc": "setContentBackgroundTint(color: OmniColor): this;"
                },
                {
                    "name": "setContentBackgroundAlpha",
                    "type": "function",
                    "doc": "setContentBackgroundAlpha(alpha: number): this;"
                },
                {
                    "name": "resetContentBackgroundAlpha",
                    "type": "function",
                    "doc": "resetContentBackgroundAlpha(): this;"
                },
                {
                    "name": "setTextSize",
                    "type": "function",
                    "doc": "setTextSize(size: number): this;"
                },
                {
                    "name": "setTextColor",
                    "type": "function",
                    "doc": "setTextColor(color: OmniColor): this;"
                },
                {
                    "name": "setBackgroundColor",
                    "type": "function",
                    "doc": "setBackgroundColor(color: OmniColor): this;"
                },
                {
                    "name": "setBackgroundTint",
                    "type": "function",
                    "doc": "setBackgroundTint(color: OmniColor): this;"
                },
                {
                    "name": "setBackgroundAlpha",
                    "type": "function",
                    "doc": "setBackgroundAlpha(alpha: number): this;"
                },
                {
                    "name": "resetBackgroundAlpha",
                    "type": "function",
                    "doc": "resetBackgroundAlpha(): this;"
                },
                {
                    "name": "setExitOnClose",
                    "type": "function",
                    "doc": "setExitOnClose(timeout: number): this;"
                },
                {
                    "name": "setTouchable",
                    "type": "function",
                    "doc": "setTouchable(touchable?: boolean): this;"
                },
                {
                    "name": "printAllStackTrace",
                    "type": "function",
                    "doc": "printAllStackTrace(t: OmniThrowable): void;"
                }
            ],
            "context": [
                {
                    "name": "ACCESSIBILITY_SERVICE",
                    "type": "variable",
                    "doc": "ACCESSIBILITY_SERVICE: string;"
                },
                {
                    "name": "ACCOUNT_SERVICE",
                    "type": "variable",
                    "doc": "ACCOUNT_SERVICE: string;"
                },
                {
                    "name": "ACTIVITY_SERVICE",
                    "type": "variable",
                    "doc": "ACTIVITY_SERVICE: string;"
                },
                {
                    "name": "ALARM_SERVICE",
                    "type": "variable",
                    "doc": "ALARM_SERVICE: string;"
                },
                {
                    "name": "APPWIDGET_SERVICE",
                    "type": "variable",
                    "doc": "APPWIDGET_SERVICE: string;"
                },
                {
                    "name": "APP_OPS_SERVICE",
                    "type": "variable",
                    "doc": "APP_OPS_SERVICE: string;"
                },
                {
                    "name": "APP_SEARCH_SERVICE",
                    "type": "variable",
                    "doc": "APP_SEARCH_SERVICE: string;"
                },
                {
                    "name": "AUDIO_SERVICE",
                    "type": "variable",
                    "doc": "AUDIO_SERVICE: string;"
                },
                {
                    "name": "BATTERY_SERVICE",
                    "type": "variable",
                    "doc": "BATTERY_SERVICE: string;"
                },
                {
                    "name": "BIND_ABOVE_CLIENT",
                    "type": "variable",
                    "doc": "BIND_ABOVE_CLIENT: number;"
                },
                {
                    "name": "BIND_ADJUST_WITH_ACTIVITY",
                    "type": "variable",
                    "doc": "BIND_ADJUST_WITH_ACTIVITY: number;"
                },
                {
                    "name": "BIND_ALLOW_OOM_MANAGEMENT",
                    "type": "variable",
                    "doc": "BIND_ALLOW_OOM_MANAGEMENT: number;"
                },
                {
                    "name": "BIND_AUTO_CREATE",
                    "type": "variable",
                    "doc": "BIND_AUTO_CREATE: number;"
                },
                {
                    "name": "BIND_DEBUG_UNBIND",
                    "type": "variable",
                    "doc": "BIND_DEBUG_UNBIND: number;"
                },
                {
                    "name": "BIND_EXTERNAL_SERVICE",
                    "type": "variable",
                    "doc": "BIND_EXTERNAL_SERVICE: number;"
                },
                {
                    "name": "BIND_IMPORTANT",
                    "type": "variable",
                    "doc": "BIND_IMPORTANT: number;"
                },
                {
                    "name": "BIND_INCLUDE_CAPABILITIES",
                    "type": "variable",
                    "doc": "BIND_INCLUDE_CAPABILITIES: number;"
                },
                {
                    "name": "BIND_NOT_FOREGROUND",
                    "type": "variable",
                    "doc": "BIND_NOT_FOREGROUND: number;"
                },
                {
                    "name": "BIND_NOT_PERCEPTIBLE",
                    "type": "variable",
                    "doc": "BIND_NOT_PERCEPTIBLE: number;"
                },
                {
                    "name": "BIND_WAIVE_PRIORITY",
                    "type": "variable",
                    "doc": "BIND_WAIVE_PRIORITY: number;"
                },
                {
                    "name": "BIOMETRIC_SERVICE",
                    "type": "variable",
                    "doc": "BIOMETRIC_SERVICE: string;"
                },
                {
                    "name": "BLOB_STORE_SERVICE",
                    "type": "variable",
                    "doc": "BLOB_STORE_SERVICE: string;"
                },
                {
                    "name": "BLUETOOTH_SERVICE",
                    "type": "variable",
                    "doc": "BLUETOOTH_SERVICE: string;"
                },
                {
                    "name": "BUGREPORT_SERVICE",
                    "type": "variable",
                    "doc": "BUGREPORT_SERVICE: string;"
                },
                {
                    "name": "CAMERA_SERVICE",
                    "type": "variable",
                    "doc": "CAMERA_SERVICE: string;"
                },
                {
                    "name": "CAPTIONING_SERVICE",
                    "type": "variable",
                    "doc": "CAPTIONING_SERVICE: string;"
                },
                {
                    "name": "CARRIER_CONFIG_SERVICE",
                    "type": "variable",
                    "doc": "CARRIER_CONFIG_SERVICE: string;"
                },
                {
                    "name": "CLIPBOARD_SERVICE",
                    "type": "variable",
                    "doc": "CLIPBOARD_SERVICE: string;"
                },
                {
                    "name": "COMPANION_DEVICE_SERVICE",
                    "type": "variable",
                    "doc": "COMPANION_DEVICE_SERVICE: string;"
                },
                {
                    "name": "CONNECTIVITY_DIAGNOSTICS_SERVICE",
                    "type": "variable",
                    "doc": "CONNECTIVITY_DIAGNOSTICS_SERVICE: string;"
                },
                {
                    "name": "CONNECTIVITY_SERVICE",
                    "type": "variable",
                    "doc": "CONNECTIVITY_SERVICE: string;"
                },
                {
                    "name": "CONSUMER_IR_SERVICE",
                    "type": "variable",
                    "doc": "CONSUMER_IR_SERVICE: string;"
                },
                {
                    "name": "CONTEXT_IGNORE_SECURITY",
                    "type": "variable",
                    "doc": "CONTEXT_IGNORE_SECURITY: number;"
                },
                {
                    "name": "CONTEXT_INCLUDE_CODE",
                    "type": "variable",
                    "doc": "CONTEXT_INCLUDE_CODE: number;"
                },
                {
                    "name": "CONTEXT_RESTRICTED",
                    "type": "variable",
                    "doc": "CONTEXT_RESTRICTED: number;"
                },
                {
                    "name": "CROSS_PROFILE_APPS_SERVICE",
                    "type": "variable",
                    "doc": "CROSS_PROFILE_APPS_SERVICE: string;"
                },
                {
                    "name": "DEVICE_POLICY_SERVICE",
                    "type": "variable",
                    "doc": "DEVICE_POLICY_SERVICE: string;"
                },
                {
                    "name": "DISPLAY_HASH_SERVICE",
                    "type": "variable",
                    "doc": "DISPLAY_HASH_SERVICE: string;"
                },
                {
                    "name": "DISPLAY_SERVICE",
                    "type": "variable",
                    "doc": "DISPLAY_SERVICE: string;"
                },
                {
                    "name": "DOMAIN_VERIFICATION_SERVICE",
                    "type": "variable",
                    "doc": "DOMAIN_VERIFICATION_SERVICE: string;"
                },
                {
                    "name": "DOWNLOAD_SERVICE",
                    "type": "variable",
                    "doc": "DOWNLOAD_SERVICE: string;"
                },
                {
                    "name": "DROPBOX_SERVICE",
                    "type": "variable",
                    "doc": "DROPBOX_SERVICE: string;"
                },
                {
                    "name": "EUICC_SERVICE",
                    "type": "variable",
                    "doc": "EUICC_SERVICE: string;"
                },
                {
                    "name": "FILE_INTEGRITY_SERVICE",
                    "type": "variable",
                    "doc": "FILE_INTEGRITY_SERVICE: string;"
                },
                {
                    "name": "FINGERPRINT_SERVICE",
                    "type": "variable",
                    "doc": "FINGERPRINT_SERVICE: string;"
                },
                {
                    "name": "GAME_SERVICE",
                    "type": "variable",
                    "doc": "GAME_SERVICE: string;"
                },
                {
                    "name": "HARDWARE_PROPERTIES_SERVICE",
                    "type": "variable",
                    "doc": "HARDWARE_PROPERTIES_SERVICE: string;"
                },
                {
                    "name": "INPUT_METHOD_SERVICE",
                    "type": "variable",
                    "doc": "INPUT_METHOD_SERVICE: string;"
                },
                {
                    "name": "INPUT_SERVICE",
                    "type": "variable",
                    "doc": "INPUT_SERVICE: string;"
                },
                {
                    "name": "IPSEC_SERVICE",
                    "type": "variable",
                    "doc": "IPSEC_SERVICE: string;"
                },
                {
                    "name": "JOB_SCHEDULER_SERVICE",
                    "type": "variable",
                    "doc": "JOB_SCHEDULER_SERVICE: string;"
                },
                {
                    "name": "KEYGUARD_SERVICE",
                    "type": "variable",
                    "doc": "KEYGUARD_SERVICE: string;"
                },
                {
                    "name": "LAUNCHER_APPS_SERVICE",
                    "type": "variable",
                    "doc": "LAUNCHER_APPS_SERVICE: string;"
                },
                {
                    "name": "LAYOUT_INFLATER_SERVICE",
                    "type": "variable",
                    "doc": "LAYOUT_INFLATER_SERVICE: string;"
                },
                {
                    "name": "LOCATION_SERVICE",
                    "type": "variable",
                    "doc": "LOCATION_SERVICE: string;"
                },
                {
                    "name": "MEDIA_COMMUNICATION_SERVICE",
                    "type": "variable",
                    "doc": "MEDIA_COMMUNICATION_SERVICE: string;"
                },
                {
                    "name": "MEDIA_METRICS_SERVICE",
                    "type": "variable",
                    "doc": "MEDIA_METRICS_SERVICE: string;"
                },
                {
                    "name": "MEDIA_PROJECTION_SERVICE",
                    "type": "variable",
                    "doc": "MEDIA_PROJECTION_SERVICE: string;"
                },
                {
                    "name": "MEDIA_ROUTER_SERVICE",
                    "type": "variable",
                    "doc": "MEDIA_ROUTER_SERVICE: string;"
                },
                {
                    "name": "MEDIA_SESSION_SERVICE",
                    "type": "variable",
                    "doc": "MEDIA_SESSION_SERVICE: string;"
                },
                {
                    "name": "MIDI_SERVICE",
                    "type": "variable",
                    "doc": "MIDI_SERVICE: string;"
                },
                {
                    "name": "MODE_APPEND",
                    "type": "variable",
                    "doc": "MODE_APPEND: number;"
                },
                {
                    "name": "MODE_ENABLE_WRITE_AHEAD_LOGGING",
                    "type": "variable",
                    "doc": "MODE_ENABLE_WRITE_AHEAD_LOGGING: number;"
                },
                {
                    "name": "MODE_MULTI_PROCESS",
                    "type": "variable",
                    "doc": "MODE_MULTI_PROCESS: number;"
                },
                {
                    "name": "MODE_NO_LOCALIZED_COLLATORS",
                    "type": "variable",
                    "doc": "MODE_NO_LOCALIZED_COLLATORS: number;"
                },
                {
                    "name": "MODE_PRIVATE",
                    "type": "variable",
                    "doc": "MODE_PRIVATE: number;"
                },
                {
                    "name": "MODE_WORLD_READABLE",
                    "type": "variable",
                    "doc": "MODE_WORLD_READABLE: number;"
                },
                {
                    "name": "MODE_WORLD_WRITEABLE",
                    "type": "variable",
                    "doc": "MODE_WORLD_WRITEABLE: number;"
                },
                {
                    "name": "NETWORK_STATS_SERVICE",
                    "type": "variable",
                    "doc": "NETWORK_STATS_SERVICE: string;"
                },
                {
                    "name": "NFC_SERVICE",
                    "type": "variable",
                    "doc": "NFC_SERVICE: string;"
                },
                {
                    "name": "NOTIFICATION_SERVICE",
                    "type": "variable",
                    "doc": "NOTIFICATION_SERVICE: string;"
                },
                {
                    "name": "NSD_SERVICE",
                    "type": "variable",
                    "doc": "NSD_SERVICE: string;"
                },
                {
                    "name": "PEOPLE_SERVICE",
                    "type": "variable",
                    "doc": "PEOPLE_SERVICE: string;"
                },
                {
                    "name": "PERFORMANCE_HINT_SERVICE",
                    "type": "variable",
                    "doc": "PERFORMANCE_HINT_SERVICE: string;"
                },
                {
                    "name": "POWER_SERVICE",
                    "type": "variable",
                    "doc": "POWER_SERVICE: string;"
                },
                {
                    "name": "PRINT_SERVICE",
                    "type": "variable",
                    "doc": "PRINT_SERVICE: string;"
                },
                {
                    "name": "RECEIVER_VISIBLE_TO_INSTANT_APPS",
                    "type": "variable",
                    "doc": "RECEIVER_VISIBLE_TO_INSTANT_APPS: number;"
                },
                {
                    "name": "RESTRICTIONS_SERVICE",
                    "type": "variable",
                    "doc": "RESTRICTIONS_SERVICE: string;"
                },
                {
                    "name": "ROLE_SERVICE",
                    "type": "variable",
                    "doc": "ROLE_SERVICE: string;"
                },
                {
                    "name": "SEARCH_SERVICE",
                    "type": "variable",
                    "doc": "SEARCH_SERVICE: string;"
                },
                {
                    "name": "SENSOR_SERVICE",
                    "type": "variable",
                    "doc": "SENSOR_SERVICE: string;"
                },
                {
                    "name": "SHORTCUT_SERVICE",
                    "type": "variable",
                    "doc": "SHORTCUT_SERVICE: string;"
                },
                {
                    "name": "STORAGE_SERVICE",
                    "type": "variable",
                    "doc": "STORAGE_SERVICE: string;"
                },
                {
                    "name": "STORAGE_STATS_SERVICE",
                    "type": "variable",
                    "doc": "STORAGE_STATS_SERVICE: string;"
                },
                {
                    "name": "SYSTEM_HEALTH_SERVICE",
                    "type": "variable",
                    "doc": "SYSTEM_HEALTH_SERVICE: string;"
                },
                {
                    "name": "TELECOM_SERVICE",
                    "type": "variable",
                    "doc": "TELECOM_SERVICE: string;"
                },
                {
                    "name": "TELEPHONY_IMS_SERVICE",
                    "type": "variable",
                    "doc": "TELEPHONY_IMS_SERVICE: string;"
                },
                {
                    "name": "TELEPHONY_SERVICE",
                    "type": "variable",
                    "doc": "TELEPHONY_SERVICE: string;"
                },
                {
                    "name": "TELEPHONY_SUBSCRIPTION_SERVICE",
                    "type": "variable",
                    "doc": "TELEPHONY_SUBSCRIPTION_SERVICE: string;"
                },
                {
                    "name": "TEXT_CLASSIFICATION_SERVICE",
                    "type": "variable",
                    "doc": "TEXT_CLASSIFICATION_SERVICE: string;"
                },
                {
                    "name": "TEXT_SERVICES_MANAGER_SERVICE",
                    "type": "variable",
                    "doc": "TEXT_SERVICES_MANAGER_SERVICE: string;"
                },
                {
                    "name": "TV_INPUT_SERVICE",
                    "type": "variable",
                    "doc": "TV_INPUT_SERVICE: string;"
                },
                {
                    "name": "UI_MODE_SERVICE",
                    "type": "variable",
                    "doc": "UI_MODE_SERVICE: string;"
                },
                {
                    "name": "USAGE_STATS_SERVICE",
                    "type": "variable",
                    "doc": "USAGE_STATS_SERVICE: string;"
                },
                {
                    "name": "USB_SERVICE",
                    "type": "variable",
                    "doc": "USB_SERVICE: string;"
                },
                {
                    "name": "USER_SERVICE",
                    "type": "variable",
                    "doc": "USER_SERVICE: string;"
                },
                {
                    "name": "VIBRATOR_MANAGER_SERVICE",
                    "type": "variable",
                    "doc": "VIBRATOR_MANAGER_SERVICE: string;"
                },
                {
                    "name": "VIBRATOR_SERVICE",
                    "type": "variable",
                    "doc": "VIBRATOR_SERVICE: string;"
                },
                {
                    "name": "VPN_MANAGEMENT_SERVICE",
                    "type": "variable",
                    "doc": "VPN_MANAGEMENT_SERVICE: string;"
                },
                {
                    "name": "WALLPAPER_SERVICE",
                    "type": "variable",
                    "doc": "WALLPAPER_SERVICE: string;"
                },
                {
                    "name": "WIFI_AWARE_SERVICE",
                    "type": "variable",
                    "doc": "WIFI_AWARE_SERVICE: string;"
                },
                {
                    "name": "WIFI_P2P_SERVICE",
                    "type": "variable",
                    "doc": "WIFI_P2P_SERVICE: string;"
                },
                {
                    "name": "WIFI_RTT_RANGING_SERVICE",
                    "type": "variable",
                    "doc": "WIFI_RTT_RANGING_SERVICE: string;"
                },
                {
                    "name": "WIFI_SERVICE",
                    "type": "variable",
                    "doc": "WIFI_SERVICE: string;"
                },
                {
                    "name": "WINDOW_SERVICE",
                    "type": "variable",
                    "doc": "WINDOW_SERVICE: string;"
                },
                {
                    "name": "applicationContext",
                    "type": "variable",
                    "doc": "applicationContext: android.content.Context;"
                },
                {
                    "name": "applicationInfo",
                    "type": "variable",
                    "doc": "applicationInfo: android.content.pm.ApplicationInfo;"
                },
                {
                    "name": "assets",
                    "type": "variable",
                    "doc": "assets: android.content.res.AssetManager;"
                },
                {
                    "name": "attributionSource",
                    "type": "variable",
                    "doc": "attributionSource: android.content.AttributionSource;"
                },
                {
                    "name": "attributionTag",
                    "type": "variable",
                    "doc": "attributionTag: string;"
                },
                {
                    "name": "cacheDir",
                    "type": "variable",
                    "doc": "cacheDir: java.io.File;"
                },
                {
                    "name": "classLoader",
                    "type": "variable",
                    "doc": "classLoader: java.lang.ClassLoader;"
                },
                {
                    "name": "codeCacheDir",
                    "type": "variable",
                    "doc": "codeCacheDir: java.io.File;"
                },
                {
                    "name": "contentResolver",
                    "type": "variable",
                    "doc": "contentResolver: android.content.ContentResolver;"
                },
                {
                    "name": "dataDir",
                    "type": "variable",
                    "doc": "dataDir: java.io.File;"
                },
                {
                    "name": "display",
                    "type": "variable",
                    "doc": "display: android.view.Display;"
                },
                {
                    "name": "externalCacheDir",
                    "type": "variable",
                    "doc": "externalCacheDir: java.io.File;"
                },
                {
                    "name": "externalCacheDirs",
                    "type": "variable",
                    "doc": "externalCacheDirs: Array<java.io.File>;"
                },
                {
                    "name": "externalMediaDirs",
                    "type": "variable",
                    "doc": "externalMediaDirs: Array<java.io.File>;"
                },
                {
                    "name": "filesDir",
                    "type": "variable",
                    "doc": "filesDir: java.io.File;"
                },
                {
                    "name": "mainExecutor",
                    "type": "variable",
                    "doc": "mainExecutor: java.util.concurrent.Executor;"
                },
                {
                    "name": "mainLooper",
                    "type": "variable",
                    "doc": "mainLooper: android.os.Looper;"
                },
                {
                    "name": "noBackupFilesDir",
                    "type": "variable",
                    "doc": "noBackupFilesDir: java.io.File;"
                },
                {
                    "name": "obbDir",
                    "type": "variable",
                    "doc": "obbDir: java.io.File;"
                },
                {
                    "name": "obbDirs",
                    "type": "variable",
                    "doc": "obbDirs: Array<java.io.File>;"
                },
                {
                    "name": "opPackageName",
                    "type": "variable",
                    "doc": "opPackageName: string;"
                },
                {
                    "name": "packageCodePath",
                    "type": "variable",
                    "doc": "packageCodePath: string;"
                },
                {
                    "name": "packageManager",
                    "type": "variable",
                    "doc": "packageManager: android.content.pm.PackageManager;"
                },
                {
                    "name": "packageName",
                    "type": "variable",
                    "doc": "packageName: string;"
                },
                {
                    "name": "packageResourcePath",
                    "type": "variable",
                    "doc": "packageResourcePath: string;"
                },
                {
                    "name": "params",
                    "type": "variable",
                    "doc": "params: android.content.ContextParams;"
                },
                {
                    "name": "resources",
                    "type": "variable",
                    "doc": "resources: android.content.res.Resources;"
                },
                {
                    "name": "theme",
                    "type": "variable",
                    "doc": "theme: android.content.res.Resources.Theme;"
                },
                {
                    "name": "wallpaper",
                    "type": "variable",
                    "doc": "wallpaper: android.graphics.drawable.Drawable;"
                },
                {
                    "name": "wallpaperDesiredMinimumHeight",
                    "type": "variable",
                    "doc": "wallpaperDesiredMinimumHeight: number;"
                },
                {
                    "name": "wallpaperDesiredMinimumWidth",
                    "type": "variable",
                    "doc": "wallpaperDesiredMinimumWidth: number;"
                }
            ],
            "continuation": [
                {
                    "name": "create",
                    "type": "function",
                    "doc": "create(scope?: org.mozilla.javascript.Scriptable): Continuation.Creator;"
                },
                {
                    "name": "await",
                    "type": "function",
                    "doc": "await(promise: Promise<unknown> | any): any;"
                },
                {
                    "name": "delay",
                    "type": "function",
                    "doc": "delay(millis: number): void;"
                },
                {
                    "name": "enabled",
                    "type": "variable",
                    "doc": "get enabled(): boolean;"
                }
            ],
            "crypto": [
                {
                    "name": "Key",
                    "type": "variable",
                    "doc": "Key: typeof Crypto.Key;"
                },
                {
                    "name": "KeyPair",
                    "type": "variable",
                    "doc": "KeyPair: typeof Crypto.KeyPair;"
                },
                {
                    "name": "fromHex",
                    "type": "function",
                    "doc": "fromHex(hex: string): number[];"
                },
                {
                    "name": "toHex",
                    "type": "function",
                    "doc": "toHex(bytes: number[]): string;"
                },
                {
                    "name": "digest",
                    "type": "function",
                    "doc": "digest(message: string, algorithm?: Crypto.DigestAlgorithm, options?: Crypto.DigestOptions): Crypto.Output;"
                },
                {
                    "name": "encrypt",
                    "type": "function",
                    "doc": "encrypt(data: Crypto.Input, key: Crypto.Key | java.security.Key, transformation: Crypto.CipherTransformation.All, options?: Crypto.CipherOptions, ): Crypto.Output;"
                },
                {
                    "name": "decrypt",
                    "type": "function",
                    "doc": "decrypt(data: Crypto.Input, key: Crypto.Key | java.security.Key, transformation: Crypto.CipherTransformation.All, options?: Crypto.CipherOptions, ): Crypto.Output;"
                },
                {
                    "name": "generateKeyPair",
                    "type": "function",
                    "doc": "generateKeyPair(algorithm: Crypto.KeyPairGeneratorAlgorithm, length?: number): Crypto.KeyPair;"
                }
            ],
            "cvt": [
                {
                    "name": "bytes",
                    "type": "variable",
                    "doc": "bytes: Converter.Bytes;"
                }
            ],
            "device": [
                {
                    "name": "width",
                    "type": "variable",
                    "doc": "get width(): number;"
                },
                {
                    "name": "height",
                    "type": "variable",
                    "doc": "get height(): number;"
                },
                {
                    "name": "rotation",
                    "type": "variable",
                    "doc": "get rotation(): number;"
                },
                {
                    "name": "orientation",
                    "type": "variable",
                    "doc": "get orientation(): number;"
                },
                {
                    "name": "density",
                    "type": "variable",
                    "doc": "get density(): number;"
                },
                {
                    "name": "summary",
                    "type": "function",
                    "doc": "summary(): string;"
                },
                {
                    "name": "digest",
                    "type": "function",
                    "doc": "digest(): string;"
                },
                {
                    "name": "buildId",
                    "type": "variable",
                    "doc": "buildId: string;"
                },
                {
                    "name": "buildDisplay",
                    "type": "variable",
                    "doc": "buildDisplay: string;"
                },
                {
                    "name": "product",
                    "type": "variable",
                    "doc": "product: string;"
                },
                {
                    "name": "board",
                    "type": "variable",
                    "doc": "board: string;"
                },
                {
                    "name": "brand",
                    "type": "variable",
                    "doc": "brand: string;"
                },
                {
                    "name": "device",
                    "type": "variable",
                    "doc": "device: string;"
                },
                {
                    "name": "model",
                    "type": "variable",
                    "doc": "model: string;"
                },
                {
                    "name": "bootloader",
                    "type": "variable",
                    "doc": "bootloader: string;"
                },
                {
                    "name": "hardware",
                    "type": "variable",
                    "doc": "hardware: string;"
                },
                {
                    "name": "fingerprint",
                    "type": "variable",
                    "doc": "fingerprint: string;"
                },
                {
                    "name": "sdkInt",
                    "type": "variable",
                    "doc": "sdkInt: number;"
                },
                {
                    "name": "incremental",
                    "type": "variable",
                    "doc": "incremental: string;"
                },
                {
                    "name": "release",
                    "type": "variable",
                    "doc": "release: string;"
                },
                {
                    "name": "baseOS",
                    "type": "variable",
                    "doc": "baseOS: string;"
                },
                {
                    "name": "securityPatch",
                    "type": "variable",
                    "doc": "securityPatch: string;"
                },
                {
                    "name": "codename",
                    "type": "variable",
                    "doc": "codename: string;"
                },
                {
                    "name": "serial",
                    "type": "variable",
                    "doc": "serial: string | null;"
                },
                {
                    "name": "imei",
                    "type": "variable",
                    "doc": "imei: string | null;"
                },
                {
                    "name": "manufacturer",
                    "type": "variable",
                    "doc": "manufacturer: string;"
                },
                {
                    "name": "cancelKeepingAwake",
                    "type": "function",
                    "doc": "cancelKeepingAwake(): void;"
                },
                {
                    "name": "cancelVibration",
                    "type": "function",
                    "doc": "cancelVibration(): void;"
                },
                {
                    "name": "getAlarmMaxVolume",
                    "type": "function",
                    "doc": "getAlarmMaxVolume(): number;"
                },
                {
                    "name": "getAlarmVolume",
                    "type": "function",
                    "doc": "getAlarmVolume(): number;"
                },
                {
                    "name": "getAndroidId",
                    "type": "function",
                    "doc": "getAndroidId(): string;"
                },
                {
                    "name": "getAvailMem",
                    "type": "function",
                    "doc": "getAvailMem(): number;"
                },
                {
                    "name": "getBattery",
                    "type": "function",
                    "doc": "getBattery(): number;"
                },
                {
                    "name": "getBrightness",
                    "type": "function",
                    "doc": "getBrightness(): number;"
                },
                {
                    "name": "getBrightnessMode",
                    "type": "function",
                    "doc": "getBrightnessMode(): number;"
                },
                {
                    "name": "getMacAddress",
                    "type": "function",
                    "doc": "getMacAddress(): string;"
                },
                {
                    "name": "getMusicMaxVolume",
                    "type": "function",
                    "doc": "getMusicMaxVolume(): number;"
                },
                {
                    "name": "getMusicVolume",
                    "type": "function",
                    "doc": "getMusicVolume(): number;"
                },
                {
                    "name": "getNotificationMaxVolume",
                    "type": "function",
                    "doc": "getNotificationMaxVolume(): number;"
                },
                {
                    "name": "getNotificationVolume",
                    "type": "function",
                    "doc": "getNotificationVolume(): number;"
                },
                {
                    "name": "getTotalMem",
                    "type": "function",
                    "doc": "getTotalMem(): number;"
                },
                {
                    "name": "getIMEI",
                    "type": "function",
                    "doc": "getIMEI(): string | null;"
                },
                {
                    "name": "getSerial",
                    "type": "function",
                    "doc": "getSerial(): string | null;"
                },
                {
                    "name": "isCharging",
                    "type": "function",
                    "doc": "isCharging(): boolean;"
                },
                {
                    "name": "isScreenOn",
                    "type": "function",
                    "doc": "isScreenOn(): boolean;"
                },
                {
                    "name": "isScreenOff",
                    "type": "function",
                    "doc": "isScreenOff(): boolean;"
                },
                {
                    "name": "isScreenPortrait",
                    "type": "function",
                    "doc": "isScreenPortrait(): boolean;"
                },
                {
                    "name": "isScreenLandscape",
                    "type": "function",
                    "doc": "isScreenLandscape(): boolean;"
                },
                {
                    "name": "keepAwake",
                    "type": "function",
                    "doc": "keepAwake(flags: number): void;"
                },
                {
                    "name": "keepScreenDim",
                    "type": "function",
                    "doc": "keepScreenDim(): void;"
                },
                {
                    "name": "keepScreenOn",
                    "type": "function",
                    "doc": "keepScreenOn(): void;"
                },
                {
                    "name": "setAlarmVolume",
                    "type": "function",
                    "doc": "setAlarmVolume(i: number): void;"
                },
                {
                    "name": "setBrightness",
                    "type": "function",
                    "doc": "setBrightness(b: number): void;"
                },
                {
                    "name": "setBrightnessMode",
                    "type": "function",
                    "doc": "setBrightnessMode(b: number): void;"
                },
                {
                    "name": "setMusicVolume",
                    "type": "function",
                    "doc": "setMusicVolume(i: number): void;"
                },
                {
                    "name": "setNotificationVolume",
                    "type": "function",
                    "doc": "setNotificationVolume(i: number): void;"
                },
                {
                    "name": "toString",
                    "type": "function",
                    "doc": "toString(): string;"
                },
                {
                    "name": "vibrate",
                    "type": "function",
                    "doc": "vibrate(pattern: OmniVibrationPattern, delay?: number): void;"
                },
                {
                    "name": "wakeUp",
                    "type": "function",
                    "doc": "wakeUp(): void;"
                },
                {
                    "name": "wakeUpIfNeeded",
                    "type": "function",
                    "doc": "wakeUpIfNeeded(): void;"
                },
                {
                    "name": "getIpAddress",
                    "type": "function",
                    "doc": "getIpAddress(useIPv4?: boolean | null): string;"
                },
                {
                    "name": "getIpv6Address",
                    "type": "function",
                    "doc": "getIpv6Address(): string;"
                },
                {
                    "name": "getGatewayAddress",
                    "type": "function",
                    "doc": "getGatewayAddress(): string;"
                },
                {
                    "name": "isActiveNetworkMetered",
                    "type": "function",
                    "doc": "isActiveNetworkMetered(): boolean;"
                },
                {
                    "name": "isConnectedOrConnecting",
                    "type": "function",
                    "doc": "isConnectedOrConnecting(): boolean;"
                },
                {
                    "name": "isWifiAvailable",
                    "type": "function",
                    "doc": "isWifiAvailable(): boolean;"
                },
                {
                    "name": "getSharedDeviceId",
                    "type": "function",
                    "doc": "getSharedDeviceId(): string | null;"
                },
                {
                    "name": "setPointerLocation",
                    "type": "function",
                    "doc": "setPointerLocation(enabled: boolean): boolean;"
                },
                {
                    "name": "setPointerLocationEnabled",
                    "type": "function",
                    "doc": "setPointerLocationEnabled(): boolean;"
                },
                {
                    "name": "setPointerLocationDisabled",
                    "type": "function",
                    "doc": "setPointerLocationDisabled(): boolean;"
                },
                {
                    "name": "isPointerLocationEnabled",
                    "type": "function",
                    "doc": "isPointerLocationEnabled(): boolean;"
                },
                {
                    "name": "isPointerLocationDisabled",
                    "type": "function",
                    "doc": "isPointerLocationDisabled(): boolean;"
                },
                {
                    "name": "togglePointerLocation",
                    "type": "function",
                    "doc": "togglePointerLocation(): boolean;"
                },
                {
                    "name": "broad",
                    "type": "function",
                    "doc": ""
                }
            ],
            "dialogs": [
                {
                    "name": "rawInput",
                    "type": "function",
                    "doc": "rawInput(title: string, prefill?: string, callback?: (input: string) => void): string | Promise<string>;"
                },
                {
                    "name": "input",
                    "type": "function",
                    "doc": "input(title: string, prefill?: string, callback?: (input: string) => void): Promise<any> | any;"
                },
                {
                    "name": "prompt",
                    "type": "function",
                    "doc": "prompt(title: string, prefill?: string, callback?: (input: string) => void): string | Promise<any>;"
                },
                {
                    "name": "alert",
                    "type": "function",
                    "doc": "alert(title: string, content?: string, callback?: (input: null) => void): void | Promise<any>;"
                },
                {
                    "name": "confirm",
                    "type": "function",
                    "doc": "confirm(title: string, content?: string, callback?: (input: null) => void): boolean | Promise<any>;"
                },
                {
                    "name": "select",
                    "type": "function",
                    "doc": "select(title: string, items: any[], callback?: (selectedIndex: number) => void): number | Promise<number>;"
                },
                {
                    "name": "singleChoice",
                    "type": "function",
                    "doc": "singleChoice(title: string, items: any[], defaultIndex?: number, callback?: (selectedIndex: number) => void): number | Promise<any>;"
                },
                {
                    "name": "multiChoice",
                    "type": "function",
                    "doc": "multiChoice(title: string, items: any[], defaultIndices?: number[]): number[] | Promise<any>;"
                },
                {
                    "name": "build",
                    "type": "function",
                    "doc": "build(properties: Dialogs.Build.Properties): Dialogs.JsDialog;"
                }
            ],
            "engines": [
                {
                    "name": "myEngine",
                    "type": "function",
                    "doc": "myEngine(): org.autojs.autojs.engine.RhinoJavaScriptEngine;"
                },
                {
                    "name": "all",
                    "type": "function",
                    "doc": "all(): org.autojs.autojs.engine.ScriptEngine<any>[];"
                },
                {
                    "name": "stopAll",
                    "type": "function",
                    "doc": "stopAll(): number;"
                },
                {
                    "name": "stopAllAndToast",
                    "type": "function",
                    "doc": "stopAllAndToast(): void;"
                },
                {
                    "name": "execScript",
                    "type": "function",
                    "doc": "execScript(name: string, script: string, config: org.autojs.autojs.execution.ExecutionConfig): org.autojs.autojs.execution.ScriptExecution;"
                },
                {
                    "name": "execScriptFile",
                    "type": "function",
                    "doc": "execScriptFile(path: string, config: org.autojs.autojs.execution.ExecutionConfig): org.autojs.autojs.execution.ScriptExecution;"
                },
                {
                    "name": "execAutoFile",
                    "type": "function",
                    "doc": "execAutoFile(path: string, config: org.autojs.autojs.execution.ExecutionConfig): org.autojs.autojs.execution.ScriptExecution;"
                }
            ],
            "events": [
                {
                    "name": "broadcast",
                    "type": "variable",
                    "doc": "broadcast: org.autojs.autojs.core.broadcast.BroadcastEmitter;"
                },
                {
                    "name": "emitter",
                    "type": "function",
                    "doc": "emitter(): EventEmitter$;"
                },
                {
                    "name": "setTouchEventTimeout",
                    "type": "function",
                    "doc": "setTouchEventTimeout(timeout: number): void;"
                },
                {
                    "name": "getTouchEventTimeout",
                    "type": "function",
                    "doc": "getTouchEventTimeout(): number;"
                },
                {
                    "name": "observeKey",
                    "type": "function",
                    "doc": "observeKey(): void;"
                },
                {
                    "name": "observeTouch",
                    "type": "function",
                    "doc": "observeTouch(): void;"
                },
                {
                    "name": "observeNotification",
                    "type": "function",
                    "doc": "observeNotification(): void;"
                },
                {
                    "name": "removeNotificationObserver",
                    "type": "function",
                    "doc": "removeNotificationObserver(): void;"
                },
                {
                    "name": "observeToast",
                    "type": "function",
                    "doc": "observeToast(): void;"
                },
                {
                    "name": "removeToastObserver",
                    "type": "function",
                    "doc": "removeToastObserver(): void;"
                },
                {
                    "name": "onceKeyDown",
                    "type": "function",
                    "doc": "onceKeyDown(keyName: Keys.Name, listener: (event: android.view.KeyEvent) => void): org.autojs.autojs.runtime.api.Events;"
                },
                {
                    "name": "onceKeyUp",
                    "type": "function",
                    "doc": "onceKeyUp(keyName: Keys.Name, listener: (event: android.view.KeyEvent) => void): org.autojs.autojs.runtime.api.Events;"
                },
                {
                    "name": "onKeyDown",
                    "type": "function",
                    "doc": "onKeyDown(keyName: Keys.Name, listener: (event: android.view.KeyEvent) => void): org.autojs.autojs.runtime.api.Events;"
                },
                {
                    "name": "onKeyUp",
                    "type": "function",
                    "doc": "onKeyUp(keyName: Keys.Name, listener: (event: android.view.KeyEvent) => void): org.autojs.autojs.runtime.api.Events;"
                },
                {
                    "name": "onNotification",
                    "type": "function",
                    "doc": "onNotification(listener: (notification: org.autojs.autojs.core.notification.Notification) => void): org.autojs.autojs.runtime.api.Events;"
                },
                {
                    "name": "onToast",
                    "type": "function",
                    "doc": "onToast(listener: (toast: org.autojs.autojs.core.accessibility.AccessibilityNotificationObserver.Toast) => void): org.autojs.autojs.runtime.api.Events;"
                },
                {
                    "name": "onTouch",
                    "type": "function",
                    "doc": "onTouch(listener: (point: android.graphics.Point) => void): org.autojs.autojs.runtime.api.Events;"
                },
                {
                    "name": "removeAllKeyDownListeners",
                    "type": "function",
                    "doc": "removeAllKeyDownListeners(keyName: Keys.Name): org.autojs.autojs.runtime.api.Events;"
                },
                {
                    "name": "removeAllKeyUpListeners",
                    "type": "function",
                    "doc": "removeAllKeyUpListeners(keyName: Keys.Name): org.autojs.autojs.runtime.api.Events;"
                },
                {
                    "name": "removeAllTouchListeners",
                    "type": "function",
                    "doc": "removeAllTouchListeners(): org.autojs.autojs.runtime.api.Events;"
                },
                {
                    "name": "setKeyInterceptionEnabled",
                    "type": "function",
                    "doc": "setKeyInterceptionEnabled(interceptionEnabled: boolean): void;"
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
                    "name": "join",
                    "type": "function",
                    "doc": "join(parent: string, ...child: string[]): string;"
                },
                {
                    "name": "append",
                    "type": "function",
                    "doc": "append(path: string, text: string, encoding?: StandardCharsets): void;"
                },
                {
                    "name": "appendBytes",
                    "type": "function",
                    "doc": "appendBytes(path: string, bytes: number[]): void;"
                },
                {
                    "name": "copy",
                    "type": "function",
                    "doc": "copy(pathFrom: string, pathTo: string): boolean;"
                },
                {
                    "name": "create",
                    "type": "function",
                    "doc": "create(path: string): boolean;"
                },
                {
                    "name": "createIfNotExists",
                    "type": "function",
                    "doc": "createIfNotExists(path: string): boolean;"
                },
                {
                    "name": "createWithDirs",
                    "type": "function",
                    "doc": "createWithDirs(path: string): boolean;"
                },
                {
                    "name": "cwd",
                    "type": "function",
                    "doc": "cwd(): string;"
                },
                {
                    "name": "ensureDir",
                    "type": "function",
                    "doc": "ensureDir(path: string): boolean;"
                },
                {
                    "name": "exists",
                    "type": "function",
                    "doc": "exists(path: string): boolean;"
                },
                {
                    "name": "getExtension",
                    "type": "function",
                    "doc": "getExtension(fileName: string): string;"
                },
                {
                    "name": "getHumanReadableSize",
                    "type": "function",
                    "doc": "getHumanReadableSize(bytes: number): string;"
                },
                {
                    "name": "getName",
                    "type": "function",
                    "doc": "getName(filePath: string): string;"
                },
                {
                    "name": "getNameWithoutExtension",
                    "type": "function",
                    "doc": "getNameWithoutExtension(filePath: string): string;"
                },
                {
                    "name": "getSdcardPath",
                    "type": "function",
                    "doc": "getSdcardPath(): string;"
                },
                {
                    "name": "getSimplifiedPath",
                    "type": "function",
                    "doc": "getSimplifiedPath(path: string): string;"
                },
                {
                    "name": "isDir",
                    "type": "function",
                    "doc": "isDir(path: string): boolean;"
                },
                {
                    "name": "isEmptyDir",
                    "type": "function",
                    "doc": "isEmptyDir(path: string): boolean;"
                },
                {
                    "name": "isFile",
                    "type": "function",
                    "doc": "isFile(path: string): boolean;"
                },
                {
                    "name": "listDir",
                    "type": "function",
                    "doc": "listDir(path: string, filter?: org.autojs.autojs.tool.Func1<string, java.lang.Boolean> | ((name: string) => boolean)): string[];"
                },
                {
                    "name": "move",
                    "type": "function",
                    "doc": "move(pathFrom: string, pathTo: string): boolean;"
                },
                {
                    "name": "open",
                    "type": "function",
                    "doc": "open(path: string, mode?: 'r', encoding?: StandardCharsets, bufferSize?: number): org.autojs.autojs.pio.PReadableTextFile;"
                },
                {
                    "name": "path",
                    "type": "function",
                    "doc": "path(relativePath?: string | null): string | null;"
                },
                {
                    "name": "read",
                    "type": "function",
                    "doc": "read(path: string, encoding?: StandardCharsets): string;"
                },
                {
                    "name": "readAssets",
                    "type": "function",
                    "doc": "readAssets(path: Files.AssetsPaths, encoding?: StandardCharsets): string;"
                },
                {
                    "name": "readBytes",
                    "type": "function",
                    "doc": "readBytes(path: string): number[];"
                },
                {
                    "name": "remove",
                    "type": "function",
                    "doc": "remove(path: string): boolean;"
                },
                {
                    "name": "removeDir",
                    "type": "function",
                    "doc": "removeDir(path: string): boolean;"
                },
                {
                    "name": "rename",
                    "type": "function",
                    "doc": "rename(path: string, newName: string): boolean;"
                },
                {
                    "name": "renameWithoutExtension",
                    "type": "function",
                    "doc": "renameWithoutExtension(path: string, newName: string): boolean;"
                },
                {
                    "name": "write",
                    "type": "function",
                    "doc": "write(path: string, text: string, encoding?: StandardCharsets): void;"
                },
                {
                    "name": "writeBytes",
                    "type": "function",
                    "doc": "writeBytes(path: string, bytes: number[]): void;"
                },
                {
                    "name": "toFile",
                    "type": "function",
                    "doc": "toFile(path: string): java.io.File;"
                }
            ],
            "floaty": [
                {
                    "name": "window",
                    "type": "function",
                    "doc": "window(layout: org.autojs.autojs.core.floaty.BaseResizableFloatyWindow.ViewSupplier | Xml): org.autojs.autojs.runtime.api.Floaty.JsResizableWindow;"
                },
                {
                    "name": "rawWindow",
                    "type": "function",
                    "doc": "rawWindow(layout: org.autojs.autojs.core.floaty.RawWindow.RawFloaty | Xml): org.autojs.autojs.runtime.api.Floaty.JsRawWindow;"
                },
                {
                    "name": "hasPermission",
                    "type": "function",
                    "doc": "hasPermission(): boolean;"
                },
                {
                    "name": "requestPermission",
                    "type": "function",
                    "doc": "requestPermission(): void;"
                },
                {
                    "name": "ensurePermission",
                    "type": "function",
                    "doc": "ensurePermission(): void;"
                },
                {
                    "name": "closeAll",
                    "type": "function",
                    "doc": "closeAll(): void;"
                },
                {
                    "name": "getClip",
                    "type": "function",
                    "doc": "getClip(maxDelayAfterWindowReady?: number): string;"
                }
            ],
            "fmt": [
                {
                    "name": "bytes",
                    "type": "variable",
                    "doc": "bytes: Formatter.Bytes;"
                }
            ],
            "http": [
                {
                    "name": "client",
                    "type": "function",
                    "doc": "client(): okhttp3.OkHttpClient;"
                },
                {
                    "name": "get",
                    "type": "function",
                    "doc": "get(url: string, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Http.WrappedResponse | void;"
                },
                {
                    "name": "getAsync",
                    "type": "function",
                    "doc": "getAsync(url: string, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Promise<Http.WrappedResponse>;"
                },
                {
                    "name": "head",
                    "type": "function",
                    "doc": "head(url: string, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Http.WrappedResponse | void;"
                },
                {
                    "name": "headAsync",
                    "type": "function",
                    "doc": "headAsync(url: string, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Promise<Http.WrappedResponse>;"
                },
                {
                    "name": "post",
                    "type": "function",
                    "doc": "post(url: string, data?: Http.RequestData, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Http.WrappedResponse | void;"
                },
                {
                    "name": "postAsync",
                    "type": "function",
                    "doc": "postAsync(url: string, data?: Http.RequestData, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Promise<Http.WrappedResponse>;"
                },
                {
                    "name": "postJson",
                    "type": "function",
                    "doc": "postJson(url: string, data?: any, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Http.WrappedResponse | void;"
                },
                {
                    "name": "postJsonAsync",
                    "type": "function",
                    "doc": "postJsonAsync(url: string, data?: any, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Promise<Http.WrappedResponse>;"
                },
                {
                    "name": "postMultipart",
                    "type": "function",
                    "doc": "postMultipart(url: string, files: Http.MultipartFiles, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Http.WrappedResponse | void;"
                },
                {
                    "name": "postMultipartAsync",
                    "type": "function",
                    "doc": "postMultipartAsync(url: string, files: Http.MultipartFiles, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Promise<Http.WrappedResponse>;"
                },
                {
                    "name": "request",
                    "type": "function",
                    "doc": "request(url: string, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Http.WrappedResponse | void;"
                },
                {
                    "name": "requestAsync",
                    "type": "function",
                    "doc": "requestAsync(url: string, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Promise<Http.WrappedResponse>;"
                },
                {
                    "name": "put",
                    "type": "function",
                    "doc": "put(url: string, data?: Http.RequestData, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Http.WrappedResponse | void;"
                },
                {
                    "name": "putAsync",
                    "type": "function",
                    "doc": "putAsync(url: string, data?: Http.RequestData, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Promise<Http.WrappedResponse>;"
                },
                {
                    "name": "delete",
                    "type": "function",
                    "doc": "delete(url: string, data?: Http.RequestData, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Http.WrappedResponse | void;"
                },
                {
                    "name": "del",
                    "type": "function",
                    "doc": "del(url: string, data?: Http.RequestData, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Http.WrappedResponse | void;"
                },
                {
                    "name": "deleteAsync",
                    "type": "function",
                    "doc": "deleteAsync(url: string, data?: Http.RequestData, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Promise<Http.WrappedResponse>;"
                },
                {
                    "name": "delAsync",
                    "type": "function",
                    "doc": "delAsync(url: string, data?: Http.RequestData, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Promise<Http.WrappedResponse>;"
                },
                {
                    "name": "buildRequest",
                    "type": "function",
                    "doc": "buildRequest(url: string, options?: Http.RequestBuilderOptions): okhttp3.Request;"
                }
            ],
            "i18n": [
                {
                    "name": "banana",
                    "type": "variable",
                    "doc": "banana: Banana;"
                },
                {
                    "name": "setPath",
                    "type": "function",
                    "doc": "setPath(relativePath: string): void;"
                },
                {
                    "name": "setLocale",
                    "type": "function",
                    "doc": "setLocale(locale: string): void;"
                },
                {
                    "name": "getFallbackLocales",
                    "type": "function",
                    "doc": "getFallbackLocales(): string[];"
                },
                {
                    "name": "getParser",
                    "type": "function",
                    "doc": "getParser(): Banana.Parser;"
                },
                {
                    "name": "getPath",
                    "type": "function",
                    "doc": "getPath(): string;"
                },
                {
                    "name": "getLocale",
                    "type": "function",
                    "doc": "getLocale(): string;"
                },
                {
                    "name": "getFinalFallback",
                    "type": "function",
                    "doc": "getFinalFallback(): string;"
                },
                {
                    "name": "load",
                    "type": "function",
                    "doc": "load(messageSource: object, locale: string): void;"
                },
                {
                    "name": "loadAll",
                    "type": "function",
                    "doc": "loadAll(): void;"
                }
            ],
            "images": [
                {
                    "name": "initOpenCvIfNeeded",
                    "type": "function",
                    "doc": "initOpenCvIfNeeded(): void;"
                },
                {
                    "name": "captureScreen",
                    "type": "function",
                    "doc": "captureScreen(): ImageWrapper;"
                },
                {
                    "name": "read",
                    "type": "function",
                    "doc": "read(path: string, isStrict?: boolean): ImageWrapper | null;"
                },
                {
                    "name": "imread",
                    "type": "function",
                    "doc": "imread(path: string): org.autojs.autojs.core.opencv.Mat;"
                },
                {
                    "name": "copy",
                    "type": "function",
                    "doc": "copy(image: Images.ImageSource): ImageWrapper;"
                },
                {
                    "name": "load",
                    "type": "function",
                    "doc": "load(src: string): ImageWrapper | null;"
                },
                {
                    "name": "loadAsync",
                    "type": "function",
                    "doc": "loadAsync(src: string): Promise<ImageWrapper | null>;"
                },
                {
                    "name": "clip",
                    "type": "function",
                    "doc": "clip(image: Images.ImageSource, x: number, y: number, w: number, h: number): ImageWrapper;"
                },
                {
                    "name": "pixel",
                    "type": "function",
                    "doc": "pixel(image: ImageWrapper, x: number, y: number): ColorInt;"
                },
                {
                    "name": "adaptiveThreshold",
                    "type": "function",
                    "doc": "adaptiveThreshold( src: ImageWrapper, maxValue: number, adaptiveMethod: Images.AdaptiveThresholdTypes, thresholdType: 'BINARY' | 'BINARY_INV', blockSize: number, C: number, ): ImageWrapper;"
                },
                {
                    "name": "blur",
                    "type": "function",
                    "doc": "blur( src: ImageWrapper, ksize: Side | [Width, Height?], anchor?: [X, Y], type?: Images.BorderTypes, ): ImageWrapper;"
                },
                {
                    "name": "concat",
                    "type": "function",
                    "doc": "concat( imgA: ImageWrapper, imgB: ImageWrapper, direction?: 'LEFT' | 'RIGHT' | 'TOP' | 'BOTTOM', ): ImageWrapper;"
                },
                {
                    "name": "cvtColor",
                    "type": "function",
                    "doc": "cvtColor(src: ImageWrapper, code: ConversionCodes, dstCn?: number): ImageWrapper;"
                },
                {
                    "name": "detectsColor",
                    "type": "function",
                    "doc": "detectsColor( img: ImageWrapper, color: Color$, x: number, y: number, threshold?: number, algorithm?: DetectionAlgorithm, ): boolean;"
                },
                {
                    "name": "findAllPointsForColor",
                    "type": "function",
                    "doc": "findAllPointsForColor(img: ImageWrapper, color: Color$, options?: { similarity?: number, threshold?: number, region?: OmniRegion, }): OpenCV.Points;"
                },
                {
                    "name": "findCircles",
                    "type": "function",
                    "doc": "findCircles(grayImg: ImageWrapper, options?: Images.Circles.Options): Images.Circles.Result;"
                },
                {
                    "name": "findColor",
                    "type": "function",
                    "doc": "findColor(img: ImageWrapper, color: Color$, options?: { similarity?: number, threshold?: number, region?: OmniRegion, }): OpenCV.Point | null;"
                },
                {
                    "name": "findColorEquals",
                    "type": "function",
                    "doc": "findColorEquals(img: ImageWrapper, color: Color$, x?: X, y?: Y, width?: Width, height?: Height): OpenCV.Point | null;"
                },
                {
                    "name": "findColorInRegion",
                    "type": "function",
                    "doc": "findColorInRegion(img: ImageWrapper, color: Color$, x?: X, y?: Y, width?: Width, height?: Height, threshold?: number): OpenCV.Point | null;"
                },
                {
                    "name": "findImage",
                    "type": "function",
                    "doc": "findImage(img: ImageWrapper, template: ImageWrapper, options?: { threshold?: number, weakThreshold?: number, level?: number, region?: OmniRegion, }): OpenCV.Point | null;"
                },
                {
                    "name": "findImageInRegion",
                    "type": "function",
                    "doc": "findImageInRegion( image: ImageWrapper, template: ImageWrapper, x?: X, y?: Y, width?: Width, height?: Height, threshold?: number, ): OpenCV.Point | null;"
                },
                {
                    "name": "findMultiColors",
                    "type": "function",
                    "doc": "findMultiColors( img: ImageWrapper, firstColor: Color$, paths: [X, Y, Color$][], options?: { region?: OmniRegion; threshold?: number; }, ): OpenCV.Point | null;"
                },
                {
                    "name": "fromBase64",
                    "type": "function",
                    "doc": "fromBase64(base64: string): ImageWrapper;"
                },
                {
                    "name": "fromBytes",
                    "type": "function",
                    "doc": "fromBytes(bytes: number[]): ImageWrapper;"
                },
                {
                    "name": "gaussianBlur",
                    "type": "function",
                    "doc": "gaussianBlur( src: ImageWrapper, ksize: Side | [Width, Height?], sigmaX?: number, sigmaY?: number, type?: Images.BorderTypes, ): ImageWrapper;"
                },
                {
                    "name": "bilateralFilter",
                    "type": "function",
                    "doc": "bilateralFilter( img: ImageWrapper, d?: number, sigma_color?: number, sigma_space?: number, border_type?: Images.BorderTypes, ): ImageWrapper;"
                },
                {
                    "name": "grayscale",
                    "type": "function",
                    "doc": "grayscale(src: ImageWrapper, dstCn?: number): ImageWrapper;"
                },
                {
                    "name": "inRange",
                    "type": "function",
                    "doc": "inRange(img: ImageWrapper, lowerBound: Color$, upperBound: Color$): ImageWrapper;"
                },
                {
                    "name": "interval",
                    "type": "function",
                    "doc": "interval(img: ImageWrapper, color: Color$, tolerance: number): ImageWrapper;"
                },
                {
                    "name": "matchTemplate",
                    "type": "function",
                    "doc": "matchTemplate(img: ImageWrapper, template: ImageWrapper, options?: { threshold?: number; weakThreshold?: number; level?: number; region?: OmniRegion; max?: number; }): Images.MatchingResult;"
                },
                {
                    "name": "matToImage",
                    "type": "function",
                    "doc": "matToImage(mat: org.autojs.autojs.core.opencv.Mat | org.opencv.core.Mat): ImageWrapper;"
                },
                {
                    "name": "medianBlur",
                    "type": "function",
                    "doc": "medianBlur(src: ImageWrapper, ksize: Side | [Side, Side]): ImageWrapper;"
                },
                {
                    "name": "readPixels",
                    "type": "function",
                    "doc": "readPixels(path: string): { data: number[], width: Width, height: Height };"
                },
                {
                    "name": "requestScreenCapture",
                    "type": "function",
                    "doc": "requestScreenCapture(landscape?: boolean): boolean;"
                },
                {
                    "name": "requestScreenCaptureAsync",
                    "type": "function",
                    "doc": "requestScreenCaptureAsync(landscape?: boolean): Promise<boolean>;"
                },
                {
                    "name": "stopScreenCapture",
                    "type": "function",
                    "doc": "stopScreenCapture(): void;"
                },
                {
                    "name": "getScreenCaptureOptions",
                    "type": "function",
                    "doc": "getScreenCaptureOptions(): org.autojs.autojs.core.image.capture.ScreenCapturer.Options | null;"
                },
                {
                    "name": "resize",
                    "type": "function",
                    "doc": "resize(src: ImageWrapper, dsize: Side | [Width, Height?], interpolation?: Images.InterpolationFlags): ImageWrapper;"
                },
                {
                    "name": "rotate",
                    "type": "function",
                    "doc": "rotate(img: ImageWrapper, degree: number, ctx?: number, cty?: number): ImageWrapper;"
                },
                {
                    "name": "save",
                    "type": "function",
                    "doc": "save(img: ImageWrapper, path: string, format?: Images.Format, quality?: number): boolean;"
                },
                {
                    "name": "saveImage",
                    "type": "function",
                    "doc": "saveImage(img: ImageWrapper, path: string, format?: Images.Format, quality?: number): boolean;"
                },
                {
                    "name": "scale",
                    "type": "function",
                    "doc": "scale(src: ImageWrapper, fx: number, fy: number, interpolation?: Images.InterpolationFlags): ImageWrapper;"
                },
                {
                    "name": "threshold",
                    "type": "function",
                    "doc": "threshold(src: ImageWrapper, threshold: number, maxVal: number, type?: Images.ThresholdTypes): ImageWrapper;"
                },
                {
                    "name": "toBase64",
                    "type": "function",
                    "doc": "toBase64(img: Images.ImageSource, format?: Images.Format, quality?: number): string;"
                },
                {
                    "name": "toBytes",
                    "type": "function",
                    "doc": "toBytes(img: Images.ImageSource, format?: Images.Format, quality?: number): number[];"
                },
                {
                    "name": "isRecycled",
                    "type": "function",
                    "doc": "isRecycled(...images: ImageWrapper[]): boolean;"
                },
                {
                    "name": "recycle",
                    "type": "function",
                    "doc": "recycle(...images: ImageWrapper[]): boolean;"
                },
                {
                    "name": "compress",
                    "type": "function",
                    "doc": "compress(img: Images.ImageSource, format?: Images.Format, quality?: number): ImageWrapper;"
                },
                {
                    "name": "compressToBytes",
                    "type": "function",
                    "doc": "compressToBytes(img: Images.ImageSource, format?: Images.Format, quality?: number): number[];"
                },
                {
                    "name": "downsample",
                    "type": "function",
                    "doc": "downsample(src: Images.DownsampleSource, reqWidth: number, reqHeight: number, withAlpha?: boolean): ImageWrapper;"
                },
                {
                    "name": "getSize",
                    "type": "function",
                    "doc": "getSize(img: ImageWrapper | android.graphics.Bitmap | org.opencv.core.Mat | string): org.opencv.core.Size;"
                },
                {
                    "name": "getWidth",
                    "type": "function",
                    "doc": "getWidth(img: ImageWrapper | android.graphics.Bitmap | org.opencv.core.Mat | string): number;"
                },
                {
                    "name": "getHeight",
                    "type": "function",
                    "doc": "getHeight(img: ImageWrapper | android.graphics.Bitmap | org.opencv.core.Mat | string): number;"
                },
                {
                    "name": "buildRegion",
                    "type": "function",
                    "doc": "buildRegion(img: Images.ImageSource, region: OmniRegion): org.opencv.core.Rect;"
                },
                {
                    "name": "detectAndComputeFeatures",
                    "type": "function",
                    "doc": "detectAndComputeFeatures(img: Images.ImageSource, options?: Images.DetectAndComputeFeaturesOptions): Images.ImageFeatures;"
                },
                {
                    "name": "matchFeatures",
                    "type": "function",
                    "doc": "matchFeatures(sceneFeatures: Images.ImageFeatures, objectFeatures: Images.ImageFeatures, options?: Images.FeatureMatchingOptions): Images.ObjectFrame | null;"
                },
                {
                    "name": "psnr",
                    "type": "function",
                    "doc": "psnr(imageA: Images.ComparableImageSource, imageB: Images.ComparableImageSource): number;"
                },
                {
                    "name": "ssim",
                    "type": "function",
                    "doc": "ssim(imageA: Images.ComparableImageSource, imageB: Images.ComparableImageSource): number;"
                },
                {
                    "name": "mssim",
                    "type": "function",
                    "doc": "mssim(imageA: Images.ComparableImageSource, imageB: Images.ComparableImageSource): number;"
                },
                {
                    "name": "hist",
                    "type": "function",
                    "doc": "hist(imageA: Images.ComparableImageSource, imageB: Images.ComparableImageSource): number;"
                },
                {
                    "name": "mse",
                    "type": "function",
                    "doc": "mse(imageA: Images.ComparableImageSource, imageB: Images.ComparableImageSource): number;"
                },
                {
                    "name": "ncc",
                    "type": "function",
                    "doc": "ncc(imageA: Images.ComparableImageSource, imageB: Images.ComparableImageSource): number;"
                },
                {
                    "name": "isEqual",
                    "type": "function",
                    "doc": "isEqual(imageA: Images.ImageSource, imageB: Images.ImageSource): boolean;"
                },
                {
                    "name": "getSimilarity",
                    "type": "function",
                    "doc": "getSimilarity(imageA: Images.ComparableImageSource, imageB: Images.ComparableImageSource, options?: Images.SimilarityOptions): number;"
                },
                {
                    "name": "detectColor",
                    "type": "function",
                    "doc": ""
                }
            ],
            "jsox": [
                {
                    "name": "extend",
                    "type": "function",
                    "doc": "extend(...modules: Jsox.ModuleArg[]): void;"
                },
                {
                    "name": "extendAll",
                    "type": "function",
                    "doc": "extendAll(): void;"
                }
            ],
            "keys": [
                {
                    "name": "home",
                    "type": "variable",
                    "doc": "home: number;"
                },
                {
                    "name": "HOME",
                    "type": "variable",
                    "doc": "HOME: number;"
                },
                {
                    "name": "menu",
                    "type": "variable",
                    "doc": "menu: number;"
                },
                {
                    "name": "MENU",
                    "type": "variable",
                    "doc": "MENU: number;"
                },
                {
                    "name": "back",
                    "type": "variable",
                    "doc": "back: number;"
                },
                {
                    "name": "BACK",
                    "type": "variable",
                    "doc": "BACK: number;"
                },
                {
                    "name": "volumeUp",
                    "type": "variable",
                    "doc": "volumeUp: number;"
                },
                {
                    "name": "volume_up",
                    "type": "variable",
                    "doc": "volume_up: number;"
                },
                {
                    "name": "VOLUME_UP",
                    "type": "variable",
                    "doc": "VOLUME_UP: number;"
                },
                {
                    "name": "volumeDown",
                    "type": "variable",
                    "doc": "volumeDown: number;"
                },
                {
                    "name": "volume_down",
                    "type": "variable",
                    "doc": "volume_down: number;"
                },
                {
                    "name": "VOLUME_DOWN",
                    "type": "variable",
                    "doc": "VOLUME_DOWN: number;"
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
            "Mathx": [
                {
                    "name": "randInt",
                    "type": "function",
                    "doc": "randInt(range?: number[]): number;"
                },
                {
                    "name": "sum",
                    "type": "function",
                    "doc": "sum(num: number[], fraction?: number): number;"
                },
                {
                    "name": "avg",
                    "type": "function",
                    "doc": "avg(num: number[], fraction?: number): number;"
                },
                {
                    "name": "median",
                    "type": "function",
                    "doc": "median(num?: number[], fraction?: number): number;"
                },
                {
                    "name": "var",
                    "type": "function",
                    "doc": "var(num: number[], fraction?: number): number;"
                },
                {
                    "name": "std",
                    "type": "function",
                    "doc": "std(num: number[], fraction?: number): number;"
                },
                {
                    "name": "cv",
                    "type": "function",
                    "doc": "cv(num: number[], fraction?: number): number;"
                },
                {
                    "name": "max",
                    "type": "function",
                    "doc": "max(num: number[], fraction?: number): number;"
                },
                {
                    "name": "min",
                    "type": "function",
                    "doc": "min(num: number[], fraction?: number): number;"
                },
                {
                    "name": "dist",
                    "type": "function",
                    "doc": "dist( point1: [number, number] | { x: number, y: number } | org.opencv.core.Point | android.graphics.Rect, point2: [number, number] | { x: number, y: number } | org.opencv.core.Point | android.graphics.Rect, fraction?: number, ): number;"
                },
                {
                    "name": "logMn",
                    "type": "function",
                    "doc": "logMn(base: number, antilogarithm: number, fraction?: number): number;"
                },
                {
                    "name": "floorLog",
                    "type": "function",
                    "doc": "floorLog(base: number, antilogarithm: number): number;"
                },
                {
                    "name": "ceilLog",
                    "type": "function",
                    "doc": "ceilLog(base: number, antilogarithm: number): number;"
                },
                {
                    "name": "roundLog",
                    "type": "function",
                    "doc": "roundLog(base: number, antilogarithm: number): number;"
                },
                {
                    "name": "floorPow",
                    "type": "function",
                    "doc": "floorPow(base: number, power: number): number;"
                },
                {
                    "name": "ceilPow",
                    "type": "function",
                    "doc": "ceilPow(base: number, power: number): number;"
                },
                {
                    "name": "roundPow",
                    "type": "function",
                    "doc": "roundPow(base: number, power: number): number;"
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
            "mediainfo": [
                {
                    "name": "read",
                    "type": "function",
                    "doc": "read(path: string): Mediainfo.Result;"
                }
            ],
            "mime": [
                {
                    "name": "getMediaType",
                    "type": "function",
                    "doc": "getMediaType(mediaType: string): okhttp3.MediaType;"
                },
                {
                    "name": "fromFileOr",
                    "type": "function",
                    "doc": "fromFileOr(path: string, defaultType: string): string;"
                },
                {
                    "name": "fromFileOrWildcard",
                    "type": "function",
                    "doc": "fromFileOrWildcard(path: string): string;"
                },
                {
                    "name": "parseMediaType",
                    "type": "function",
                    "doc": "parseMediaType(mediaType: string): okhttp3.MediaType | null;"
                },
                {
                    "name": "fromFile",
                    "type": "function",
                    "doc": "fromFile(path: string): string | null;"
                },
                {
                    "name": "DIRECTORY_MIME_TYPE",
                    "type": "variable",
                    "doc": "DIRECTORY_MIME_TYPE: eu.medsea.mimeutil.MimeType;"
                },
                {
                    "name": "UNKNOWN_MIME_TYPE",
                    "type": "variable",
                    "doc": "UNKNOWN_MIME_TYPE: eu.medsea.mimeutil.MimeType;"
                },
                {
                    "name": "addKnownMimeType",
                    "type": "function",
                    "doc": "addKnownMimeType(mimeType: eu.medsea.mimeutil.MimeType): void;"
                },
                {
                    "name": "getExtension",
                    "type": "function",
                    "doc": "getExtension(file: java.io.File): string;"
                },
                {
                    "name": "getFirstMimeType",
                    "type": "function",
                    "doc": "getFirstMimeType(mimeTypes: string): eu.medsea.mimeutil.MimeType;"
                },
                {
                    "name": "getInputStreamForURL",
                    "type": "function",
                    "doc": "getInputStreamForURL(url: java.net.URL): java.io.InputStream;"
                },
                {
                    "name": "getKnownMimeTypes",
                    "type": "function",
                    "doc": "getKnownMimeTypes(): java.util.Collection<any>;"
                },
                {
                    "name": "getMimeQuality",
                    "type": "function",
                    "doc": "getMimeQuality(mimeType: string): number;"
                },
                {
                    "name": "getMostSpecificMimeType",
                    "type": "function",
                    "doc": "getMostSpecificMimeType(mimeTypes: java.util.Collection<any>): eu.medsea.mimeutil.MimeType;"
                },
                {
                    "name": "getNativeOrder",
                    "type": "function",
                    "doc": "getNativeOrder(): java.nio.ByteOrder;"
                },
                {
                    "name": "getPreferredMimeType",
                    "type": "function",
                    "doc": "getPreferredMimeType(accept: string, canProvide: string): eu.medsea.mimeutil.MimeType;"
                },
                {
                    "name": "getQuality",
                    "type": "function",
                    "doc": "getQuality(mimeType: string): number;"
                },
                {
                    "name": "getSubtype",
                    "type": "function",
                    "doc": "getSubtype(mimeType: string): string;"
                },
                {
                    "name": "getType",
                    "type": "function",
                    "doc": "getType(mimeType: string): string;"
                },
                {
                    "name": "isMimeTypeKnown",
                    "type": "function",
                    "doc": "isMimeTypeKnown(mimeType: eu.medsea.mimeutil.MimeType): boolean;"
                },
                {
                    "name": "isTextMimeType",
                    "type": "function",
                    "doc": "isTextMimeType(mimeType: eu.medsea.mimeutil.MimeType): boolean;"
                },
                {
                    "name": "APPLICATION_1D_INTERLEAVED_PARITYFEC",
                    "type": "variable",
                    "doc": "APPLICATION_1D_INTERLEAVED_PARITYFEC: \"application/1d-interleaved-parityfec\";"
                },
                {
                    "name": "APPLICATION_3GPDASH_QOE_REPORT_XML",
                    "type": "variable",
                    "doc": "APPLICATION_3GPDASH_QOE_REPORT_XML: \"application/3gpdash-qoe-report+xml\";"
                },
                {
                    "name": "APPLICATION_3GPPHALFORMS_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_3GPPHALFORMS_JSON: \"application/3gppHalForms+json\";"
                },
                {
                    "name": "APPLICATION_3GPPHAL_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_3GPPHAL_JSON: \"application/3gppHal+json\";"
                },
                {
                    "name": "APPLICATION_3GPP_IMS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_3GPP_IMS_XML: \"application/3gpp-ims+xml\";"
                },
                {
                    "name": "APPLICATION_A2L",
                    "type": "variable",
                    "doc": "APPLICATION_A2L: \"application/A2L\";"
                },
                {
                    "name": "APPLICATION_ACE_CBOR",
                    "type": "variable",
                    "doc": "APPLICATION_ACE_CBOR: \"application/ace+cbor\";"
                },
                {
                    "name": "APPLICATION_ACE_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_ACE_JSON: \"application/ace+json\";"
                },
                {
                    "name": "APPLICATION_ACTIVEMESSAGE",
                    "type": "variable",
                    "doc": "APPLICATION_ACTIVEMESSAGE: \"application/activemessage\";"
                },
                {
                    "name": "APPLICATION_ACTIVITY_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_ACTIVITY_JSON: \"application/activity+json\";"
                },
                {
                    "name": "APPLICATION_AIF_CBOR",
                    "type": "variable",
                    "doc": "APPLICATION_AIF_CBOR: \"application/aif+cbor\";"
                },
                {
                    "name": "APPLICATION_AIF_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_AIF_JSON: \"application/aif+json\";"
                },
                {
                    "name": "APPLICATION_ALTO_CDNIFILTER_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_ALTO_CDNIFILTER_JSON: \"application/alto-cdnifilter+json\";"
                },
                {
                    "name": "APPLICATION_ALTO_CDNI_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_ALTO_CDNI_JSON: \"application/alto-cdni+json\";"
                },
                {
                    "name": "APPLICATION_ALTO_COSTMAPFILTER_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_ALTO_COSTMAPFILTER_JSON: \"application/alto-costmapfilter+json\";"
                },
                {
                    "name": "APPLICATION_ALTO_COSTMAP_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_ALTO_COSTMAP_JSON: \"application/alto-costmap+json\";"
                },
                {
                    "name": "APPLICATION_ALTO_DIRECTORY_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_ALTO_DIRECTORY_JSON: \"application/alto-directory+json\";"
                },
                {
                    "name": "APPLICATION_ALTO_ENDPOINTCOSTPARAMS_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_ALTO_ENDPOINTCOSTPARAMS_JSON: \"application/alto-endpointcostparams+json\";"
                },
                {
                    "name": "APPLICATION_ALTO_ENDPOINTCOST_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_ALTO_ENDPOINTCOST_JSON: \"application/alto-endpointcost+json\";"
                },
                {
                    "name": "APPLICATION_ALTO_ENDPOINTPROPPARAMS_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_ALTO_ENDPOINTPROPPARAMS_JSON: \"application/alto-endpointpropparams+json\";"
                },
                {
                    "name": "APPLICATION_ALTO_ENDPOINTPROP_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_ALTO_ENDPOINTPROP_JSON: \"application/alto-endpointprop+json\";"
                },
                {
                    "name": "APPLICATION_ALTO_ERROR_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_ALTO_ERROR_JSON: \"application/alto-error+json\";"
                },
                {
                    "name": "APPLICATION_ALTO_NETWORKMAPFILTER_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_ALTO_NETWORKMAPFILTER_JSON: \"application/alto-networkmapfilter+json\";"
                },
                {
                    "name": "APPLICATION_ALTO_NETWORKMAP_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_ALTO_NETWORKMAP_JSON: \"application/alto-networkmap+json\";"
                },
                {
                    "name": "APPLICATION_ALTO_PROPMAPPARAMS_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_ALTO_PROPMAPPARAMS_JSON: \"application/alto-propmapparams+json\";"
                },
                {
                    "name": "APPLICATION_ALTO_PROPMAP_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_ALTO_PROPMAP_JSON: \"application/alto-propmap+json\";"
                },
                {
                    "name": "APPLICATION_ALTO_TIPSPARAMS_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_ALTO_TIPSPARAMS_JSON: \"application/alto-tipsparams+json\";"
                },
                {
                    "name": "APPLICATION_ALTO_TIPS_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_ALTO_TIPS_JSON: \"application/alto-tips+json\";"
                },
                {
                    "name": "APPLICATION_ALTO_UPDATESTREAMCONTROL_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_ALTO_UPDATESTREAMCONTROL_JSON: \"application/alto-updatestreamcontrol+json\";"
                },
                {
                    "name": "APPLICATION_ALTO_UPDATESTREAMPARAMS_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_ALTO_UPDATESTREAMPARAMS_JSON: \"application/alto-updatestreamparams+json\";"
                },
                {
                    "name": "APPLICATION_AML",
                    "type": "variable",
                    "doc": "APPLICATION_AML: \"application/AML\";"
                },
                {
                    "name": "APPLICATION_ANDREW_INSET",
                    "type": "variable",
                    "doc": "APPLICATION_ANDREW_INSET: \"application/andrew-inset\";"
                },
                {
                    "name": "APPLICATION_ANNODEX",
                    "type": "variable",
                    "doc": "APPLICATION_ANNODEX: \"application/annodex\";"
                },
                {
                    "name": "APPLICATION_APPLEFILE",
                    "type": "variable",
                    "doc": "APPLICATION_APPLEFILE: \"application/applefile\";"
                },
                {
                    "name": "APPLICATION_ATF",
                    "type": "variable",
                    "doc": "APPLICATION_ATF: \"application/ATF\";"
                },
                {
                    "name": "APPLICATION_ATFX",
                    "type": "variable",
                    "doc": "APPLICATION_ATFX: \"application/ATFX\";"
                },
                {
                    "name": "APPLICATION_ATOMCAT_XML",
                    "type": "variable",
                    "doc": "APPLICATION_ATOMCAT_XML: \"application/atomcat+xml\";"
                },
                {
                    "name": "APPLICATION_ATOMDELETED_XML",
                    "type": "variable",
                    "doc": "APPLICATION_ATOMDELETED_XML: \"application/atomdeleted+xml\";"
                },
                {
                    "name": "APPLICATION_ATOMICMAIL",
                    "type": "variable",
                    "doc": "APPLICATION_ATOMICMAIL: \"application/atomicmail\";"
                },
                {
                    "name": "APPLICATION_ATOMSERV_XML",
                    "type": "variable",
                    "doc": "APPLICATION_ATOMSERV_XML: \"application/atomserv+xml\";"
                },
                {
                    "name": "APPLICATION_ATOMSVC_XML",
                    "type": "variable",
                    "doc": "APPLICATION_ATOMSVC_XML: \"application/atomsvc+xml\";"
                },
                {
                    "name": "APPLICATION_ATOM_XML",
                    "type": "variable",
                    "doc": "APPLICATION_ATOM_XML: \"application/atom+xml\";"
                },
                {
                    "name": "APPLICATION_ATSC_DWD_XML",
                    "type": "variable",
                    "doc": "APPLICATION_ATSC_DWD_XML: \"application/atsc-dwd+xml\";"
                },
                {
                    "name": "APPLICATION_ATSC_DYNAMIC_EVENT_MESSAGE",
                    "type": "variable",
                    "doc": "APPLICATION_ATSC_DYNAMIC_EVENT_MESSAGE: \"application/atsc-dynamic-event-message\";"
                },
                {
                    "name": "APPLICATION_ATSC_HELD_XML",
                    "type": "variable",
                    "doc": "APPLICATION_ATSC_HELD_XML: \"application/atsc-held+xml\";"
                },
                {
                    "name": "APPLICATION_ATSC_RDT_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_ATSC_RDT_JSON: \"application/atsc-rdt+json\";"
                },
                {
                    "name": "APPLICATION_ATSC_RSAT_XML",
                    "type": "variable",
                    "doc": "APPLICATION_ATSC_RSAT_XML: \"application/atsc-rsat+xml\";"
                },
                {
                    "name": "APPLICATION_ATXML",
                    "type": "variable",
                    "doc": "APPLICATION_ATXML: \"application/ATXML\";"
                },
                {
                    "name": "APPLICATION_AT_JWT",
                    "type": "variable",
                    "doc": "APPLICATION_AT_JWT: \"application/at+jwt\";"
                },
                {
                    "name": "APPLICATION_AUTH_POLICY_XML",
                    "type": "variable",
                    "doc": "APPLICATION_AUTH_POLICY_XML: \"application/auth-policy+xml\";"
                },
                {
                    "name": "APPLICATION_AUTOMATIONML_AMLX_ZIP",
                    "type": "variable",
                    "doc": "APPLICATION_AUTOMATIONML_AMLX_ZIP: \"application/automationml-amlx+zip\";"
                },
                {
                    "name": "APPLICATION_AUTOMATIONML_AML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_AUTOMATIONML_AML_XML: \"application/automationml-aml+xml\";"
                },
                {
                    "name": "APPLICATION_BACNET_XDD_ZIP",
                    "type": "variable",
                    "doc": "APPLICATION_BACNET_XDD_ZIP: \"application/bacnet-xdd+zip\";"
                },
                {
                    "name": "APPLICATION_BATCH_SMTP",
                    "type": "variable",
                    "doc": "APPLICATION_BATCH_SMTP: \"application/batch-SMTP\";"
                },
                {
                    "name": "APPLICATION_BBOLIN",
                    "type": "variable",
                    "doc": "APPLICATION_BBOLIN: \"application/bbolin\";"
                },
                {
                    "name": "APPLICATION_BEEP_XML",
                    "type": "variable",
                    "doc": "APPLICATION_BEEP_XML: \"application/beep+xml\";"
                },
                {
                    "name": "APPLICATION_BZ2",
                    "type": "variable",
                    "doc": "APPLICATION_BZ2: \"application/bz2\";"
                },
                {
                    "name": "APPLICATION_C2PA",
                    "type": "variable",
                    "doc": "APPLICATION_C2PA: \"application/c2pa\";"
                },
                {
                    "name": "APPLICATION_CALENDAR_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_CALENDAR_JSON: \"application/calendar+json\";"
                },
                {
                    "name": "APPLICATION_CALENDAR_XML",
                    "type": "variable",
                    "doc": "APPLICATION_CALENDAR_XML: \"application/calendar+xml\";"
                },
                {
                    "name": "APPLICATION_CALL_COMPLETION",
                    "type": "variable",
                    "doc": "APPLICATION_CALL_COMPLETION: \"application/call-completion\";"
                },
                {
                    "name": "APPLICATION_CALS_1840",
                    "type": "variable",
                    "doc": "APPLICATION_CALS_1840: \"application/CALS-1840\";"
                },
                {
                    "name": "APPLICATION_CAPTIVE_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_CAPTIVE_JSON: \"application/captive+json\";"
                },
                {
                    "name": "APPLICATION_CBOR",
                    "type": "variable",
                    "doc": "APPLICATION_CBOR: \"application/cbor\";"
                },
                {
                    "name": "APPLICATION_CBOR_SEQ",
                    "type": "variable",
                    "doc": "APPLICATION_CBOR_SEQ: \"application/cbor-seq\";"
                },
                {
                    "name": "APPLICATION_CCCEX",
                    "type": "variable",
                    "doc": "APPLICATION_CCCEX: \"application/cccex\";"
                },
                {
                    "name": "APPLICATION_CCMP_XML",
                    "type": "variable",
                    "doc": "APPLICATION_CCMP_XML: \"application/ccmp+xml\";"
                },
                {
                    "name": "APPLICATION_CCXML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_CCXML_XML: \"application/ccxml+xml\";"
                },
                {
                    "name": "APPLICATION_CDA_XML",
                    "type": "variable",
                    "doc": "APPLICATION_CDA_XML: \"application/cda+xml\";"
                },
                {
                    "name": "APPLICATION_CDFX_XML",
                    "type": "variable",
                    "doc": "APPLICATION_CDFX_XML: \"application/CDFX+XML\";"
                },
                {
                    "name": "APPLICATION_CDMI_CAPABILITY",
                    "type": "variable",
                    "doc": "APPLICATION_CDMI_CAPABILITY: \"application/cdmi-capability\";"
                },
                {
                    "name": "APPLICATION_CDMI_CONTAINER",
                    "type": "variable",
                    "doc": "APPLICATION_CDMI_CONTAINER: \"application/cdmi-container\";"
                },
                {
                    "name": "APPLICATION_CDMI_DOMAIN",
                    "type": "variable",
                    "doc": "APPLICATION_CDMI_DOMAIN: \"application/cdmi-domain\";"
                },
                {
                    "name": "APPLICATION_CDMI_OBJECT",
                    "type": "variable",
                    "doc": "APPLICATION_CDMI_OBJECT: \"application/cdmi-object\";"
                },
                {
                    "name": "APPLICATION_CDMI_QUEUE",
                    "type": "variable",
                    "doc": "APPLICATION_CDMI_QUEUE: \"application/cdmi-queue\";"
                },
                {
                    "name": "APPLICATION_CDNI",
                    "type": "variable",
                    "doc": "APPLICATION_CDNI: \"application/cdni\";"
                },
                {
                    "name": "APPLICATION_CEA",
                    "type": "variable",
                    "doc": "APPLICATION_CEA: \"application/CEA\";"
                },
                {
                    "name": "APPLICATION_CEA_2018_XML",
                    "type": "variable",
                    "doc": "APPLICATION_CEA_2018_XML: \"application/cea-2018+xml\";"
                },
                {
                    "name": "APPLICATION_CELLML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_CELLML_XML: \"application/cellml+xml\";"
                },
                {
                    "name": "APPLICATION_CFW",
                    "type": "variable",
                    "doc": "APPLICATION_CFW: \"application/cfw\";"
                },
                {
                    "name": "APPLICATION_CID_EDHOC_CBOR_SEQ",
                    "type": "variable",
                    "doc": "APPLICATION_CID_EDHOC_CBOR_SEQ: \"application/cid-edhoc+cbor-seq\";"
                },
                {
                    "name": "APPLICATION_CITY_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_CITY_JSON: \"application/city+json\";"
                },
                {
                    "name": "APPLICATION_CLR",
                    "type": "variable",
                    "doc": "APPLICATION_CLR: \"application/clr\";"
                },
                {
                    "name": "APPLICATION_CLUE_INFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_CLUE_INFO_XML: \"application/clue_info+xml\";"
                },
                {
                    "name": "APPLICATION_CLUE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_CLUE_XML: \"application/clue+xml\";"
                },
                {
                    "name": "APPLICATION_CMS",
                    "type": "variable",
                    "doc": "APPLICATION_CMS: \"application/cms\";"
                },
                {
                    "name": "APPLICATION_CNRP_XML",
                    "type": "variable",
                    "doc": "APPLICATION_CNRP_XML: \"application/cnrp+xml\";"
                },
                {
                    "name": "APPLICATION_COAP_GROUP_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_COAP_GROUP_JSON: \"application/coap-group+json\";"
                },
                {
                    "name": "APPLICATION_COAP_PAYLOAD",
                    "type": "variable",
                    "doc": "APPLICATION_COAP_PAYLOAD: \"application/coap-payload\";"
                },
                {
                    "name": "APPLICATION_COMMONGROUND",
                    "type": "variable",
                    "doc": "APPLICATION_COMMONGROUND: \"application/commonground\";"
                },
                {
                    "name": "APPLICATION_CONCISE_PROBLEM_DETAILS_CBOR",
                    "type": "variable",
                    "doc": "APPLICATION_CONCISE_PROBLEM_DETAILS_CBOR: \"application/concise-problem-details+cbor\";"
                },
                {
                    "name": "APPLICATION_CONFERENCE_INFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_CONFERENCE_INFO_XML: \"application/conference-info+xml\";"
                },
                {
                    "name": "APPLICATION_COSE",
                    "type": "variable",
                    "doc": "APPLICATION_COSE: \"application/cose\";"
                },
                {
                    "name": "APPLICATION_COSE_KEY",
                    "type": "variable",
                    "doc": "APPLICATION_COSE_KEY: \"application/cose-key\";"
                },
                {
                    "name": "APPLICATION_COSE_KEY_SET",
                    "type": "variable",
                    "doc": "APPLICATION_COSE_KEY_SET: \"application/cose-key-set\";"
                },
                {
                    "name": "APPLICATION_COSE_X509",
                    "type": "variable",
                    "doc": "APPLICATION_COSE_X509: \"application/cose-x509\";"
                },
                {
                    "name": "APPLICATION_CPL_XML",
                    "type": "variable",
                    "doc": "APPLICATION_CPL_XML: \"application/cpl+xml\";"
                },
                {
                    "name": "APPLICATION_CSRATTRS",
                    "type": "variable",
                    "doc": "APPLICATION_CSRATTRS: \"application/csrattrs\";"
                },
                {
                    "name": "APPLICATION_CSTADATA_XML",
                    "type": "variable",
                    "doc": "APPLICATION_CSTADATA_XML: \"application/CSTAdata+xml\";"
                },
                {
                    "name": "APPLICATION_CSTA_XML",
                    "type": "variable",
                    "doc": "APPLICATION_CSTA_XML: \"application/csta+xml\";"
                },
                {
                    "name": "APPLICATION_CSVM_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_CSVM_JSON: \"application/csvm+json\";"
                },
                {
                    "name": "APPLICATION_CU_SEEME",
                    "type": "variable",
                    "doc": "APPLICATION_CU_SEEME: \"application/cu-seeme\";"
                },
                {
                    "name": "APPLICATION_CWL",
                    "type": "variable",
                    "doc": "APPLICATION_CWL: \"application/cwl\";"
                },
                {
                    "name": "APPLICATION_CWL_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_CWL_JSON: \"application/cwl+json\";"
                },
                {
                    "name": "APPLICATION_CWT",
                    "type": "variable",
                    "doc": "APPLICATION_CWT: \"application/cwt\";"
                },
                {
                    "name": "APPLICATION_CYBERCASH",
                    "type": "variable",
                    "doc": "APPLICATION_CYBERCASH: \"application/cybercash\";"
                },
                {
                    "name": "APPLICATION_DASHDELTA",
                    "type": "variable",
                    "doc": "APPLICATION_DASHDELTA: \"application/dashdelta\";"
                },
                {
                    "name": "APPLICATION_DASH_PATCH_XML",
                    "type": "variable",
                    "doc": "APPLICATION_DASH_PATCH_XML: \"application/dash-patch+xml\";"
                },
                {
                    "name": "APPLICATION_DASH_XML",
                    "type": "variable",
                    "doc": "APPLICATION_DASH_XML: \"application/dash+xml\";"
                },
                {
                    "name": "APPLICATION_DAVMOUNT_XML",
                    "type": "variable",
                    "doc": "APPLICATION_DAVMOUNT_XML: \"application/davmount+xml\";"
                },
                {
                    "name": "APPLICATION_DCA_RFT",
                    "type": "variable",
                    "doc": "APPLICATION_DCA_RFT: \"application/dca-rft\";"
                },
                {
                    "name": "APPLICATION_DCD",
                    "type": "variable",
                    "doc": "APPLICATION_DCD: \"application/DCD\";"
                },
                {
                    "name": "APPLICATION_DEC_DX",
                    "type": "variable",
                    "doc": "APPLICATION_DEC_DX: \"application/dec-dx\";"
                },
                {
                    "name": "APPLICATION_DIALOG_INFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_DIALOG_INFO_XML: \"application/dialog-info+xml\";"
                },
                {
                    "name": "APPLICATION_DICOM",
                    "type": "variable",
                    "doc": "APPLICATION_DICOM: \"application/dicom\";"
                },
                {
                    "name": "APPLICATION_DICOM_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_DICOM_JSON: \"application/dicom+json\";"
                },
                {
                    "name": "APPLICATION_DICOM_XML",
                    "type": "variable",
                    "doc": "APPLICATION_DICOM_XML: \"application/dicom+xml\";"
                },
                {
                    "name": "APPLICATION_DII",
                    "type": "variable",
                    "doc": "APPLICATION_DII: \"application/DII\";"
                },
                {
                    "name": "APPLICATION_DIT",
                    "type": "variable",
                    "doc": "APPLICATION_DIT: \"application/DIT\";"
                },
                {
                    "name": "APPLICATION_DNS",
                    "type": "variable",
                    "doc": "APPLICATION_DNS: \"application/dns\";"
                },
                {
                    "name": "APPLICATION_DNS_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_DNS_JSON: \"application/dns+json\";"
                },
                {
                    "name": "APPLICATION_DNS_MESSAGE",
                    "type": "variable",
                    "doc": "APPLICATION_DNS_MESSAGE: \"application/dns-message\";"
                },
                {
                    "name": "APPLICATION_DOCBOOK_XML",
                    "type": "variable",
                    "doc": "APPLICATION_DOCBOOK_XML: \"application/docbook+xml\";"
                },
                {
                    "name": "APPLICATION_DOTS_CBOR",
                    "type": "variable",
                    "doc": "APPLICATION_DOTS_CBOR: \"application/dots+cbor\";"
                },
                {
                    "name": "APPLICATION_DPOP_JWT",
                    "type": "variable",
                    "doc": "APPLICATION_DPOP_JWT: \"application/dpop+jwt\";"
                },
                {
                    "name": "APPLICATION_DSKPP_XML",
                    "type": "variable",
                    "doc": "APPLICATION_DSKPP_XML: \"application/dskpp+xml\";"
                },
                {
                    "name": "APPLICATION_DSPTYPE",
                    "type": "variable",
                    "doc": "APPLICATION_DSPTYPE: \"application/dsptype\";"
                },
                {
                    "name": "APPLICATION_DSSC_DER",
                    "type": "variable",
                    "doc": "APPLICATION_DSSC_DER: \"application/dssc+der\";"
                },
                {
                    "name": "APPLICATION_DSSC_XML",
                    "type": "variable",
                    "doc": "APPLICATION_DSSC_XML: \"application/dssc+xml\";"
                },
                {
                    "name": "APPLICATION_DVCS",
                    "type": "variable",
                    "doc": "APPLICATION_DVCS: \"application/dvcs\";"
                },
                {
                    "name": "APPLICATION_ECMASCRIPT",
                    "type": "variable",
                    "doc": "APPLICATION_ECMASCRIPT: \"application/ecmascript\";"
                },
                {
                    "name": "APPLICATION_EDHOC_CBOR_SEQ",
                    "type": "variable",
                    "doc": "APPLICATION_EDHOC_CBOR_SEQ: \"application/edhoc+cbor-seq\";"
                },
                {
                    "name": "APPLICATION_EDIFACT",
                    "type": "variable",
                    "doc": "APPLICATION_EDIFACT: \"application/EDIFACT\";"
                },
                {
                    "name": "APPLICATION_EDI_CONSENT",
                    "type": "variable",
                    "doc": "APPLICATION_EDI_CONSENT: \"application/EDI-consent\";"
                },
                {
                    "name": "APPLICATION_EDI_X12",
                    "type": "variable",
                    "doc": "APPLICATION_EDI_X12: \"application/EDI-X12\";"
                },
                {
                    "name": "APPLICATION_EFI",
                    "type": "variable",
                    "doc": "APPLICATION_EFI: \"application/efi\";"
                },
                {
                    "name": "APPLICATION_ELM_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_ELM_JSON: \"application/elm+json\";"
                },
                {
                    "name": "APPLICATION_ELM_XML",
                    "type": "variable",
                    "doc": "APPLICATION_ELM_XML: \"application/elm+xml\";"
                },
                {
                    "name": "APPLICATION_EMERGENCYCALLDATA_CAP_XML",
                    "type": "variable",
                    "doc": "APPLICATION_EMERGENCYCALLDATA_CAP_XML: \"application/EmergencyCallData.cap+xml\";"
                },
                {
                    "name": "APPLICATION_EMERGENCYCALLDATA_COMMENT_XML",
                    "type": "variable",
                    "doc": "APPLICATION_EMERGENCYCALLDATA_COMMENT_XML: \"application/EmergencyCallData.Comment+xml\";"
                },
                {
                    "name": "APPLICATION_EMERGENCYCALLDATA_CONTROL_XML",
                    "type": "variable",
                    "doc": "APPLICATION_EMERGENCYCALLDATA_CONTROL_XML: \"application/EmergencyCallData.Control+xml\";"
                },
                {
                    "name": "APPLICATION_EMERGENCYCALLDATA_DEVICEINFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_EMERGENCYCALLDATA_DEVICEINFO_XML: \"application/EmergencyCallData.DeviceInfo+xml\";"
                },
                {
                    "name": "APPLICATION_EMERGENCYCALLDATA_ECALL_MSD",
                    "type": "variable",
                    "doc": "APPLICATION_EMERGENCYCALLDATA_ECALL_MSD: \"application/EmergencyCallData.eCall.MSD\";"
                },
                {
                    "name": "APPLICATION_EMERGENCYCALLDATA_LEGACYESN_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_EMERGENCYCALLDATA_LEGACYESN_JSON: \"application/EmergencyCallData.LegacyESN+json\";"
                },
                {
                    "name": "APPLICATION_EMERGENCYCALLDATA_PROVIDERINFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_EMERGENCYCALLDATA_PROVIDERINFO_XML: \"application/EmergencyCallData.ProviderInfo+xml\";"
                },
                {
                    "name": "APPLICATION_EMERGENCYCALLDATA_SERVICEINFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_EMERGENCYCALLDATA_SERVICEINFO_XML: \"application/EmergencyCallData.ServiceInfo+xml\";"
                },
                {
                    "name": "APPLICATION_EMERGENCYCALLDATA_SUBSCRIBERINFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_EMERGENCYCALLDATA_SUBSCRIBERINFO_XML: \"application/EmergencyCallData.SubscriberInfo+xml\";"
                },
                {
                    "name": "APPLICATION_EMERGENCYCALLDATA_VEDS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_EMERGENCYCALLDATA_VEDS_XML: \"application/EmergencyCallData.VEDS+xml\";"
                },
                {
                    "name": "APPLICATION_EMMA_XML",
                    "type": "variable",
                    "doc": "APPLICATION_EMMA_XML: \"application/emma+xml\";"
                },
                {
                    "name": "APPLICATION_EMOTIONML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_EMOTIONML_XML: \"application/emotionml+xml\";"
                },
                {
                    "name": "APPLICATION_ENCAPRTP",
                    "type": "variable",
                    "doc": "APPLICATION_ENCAPRTP: \"application/encaprtp\";"
                },
                {
                    "name": "APPLICATION_EPP_XML",
                    "type": "variable",
                    "doc": "APPLICATION_EPP_XML: \"application/epp+xml\";"
                },
                {
                    "name": "APPLICATION_EPUB_ZIP",
                    "type": "variable",
                    "doc": "APPLICATION_EPUB_ZIP: \"application/epub+zip\";"
                },
                {
                    "name": "APPLICATION_ESHOP",
                    "type": "variable",
                    "doc": "APPLICATION_ESHOP: \"application/eshop\";"
                },
                {
                    "name": "APPLICATION_EXAMPLE",
                    "type": "variable",
                    "doc": "APPLICATION_EXAMPLE: \"application/example\";"
                },
                {
                    "name": "APPLICATION_EXI",
                    "type": "variable",
                    "doc": "APPLICATION_EXI: \"application/exi\";"
                },
                {
                    "name": "APPLICATION_EXPECT_CT_REPORT_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_EXPECT_CT_REPORT_JSON: \"application/expect-ct-report+json\";"
                },
                {
                    "name": "APPLICATION_EXPRESS",
                    "type": "variable",
                    "doc": "APPLICATION_EXPRESS: \"application/express\";"
                },
                {
                    "name": "APPLICATION_FASTINFOSET",
                    "type": "variable",
                    "doc": "APPLICATION_FASTINFOSET: \"application/fastinfoset\";"
                },
                {
                    "name": "APPLICATION_FASTSOAP",
                    "type": "variable",
                    "doc": "APPLICATION_FASTSOAP: \"application/fastsoap\";"
                },
                {
                    "name": "APPLICATION_FDF",
                    "type": "variable",
                    "doc": "APPLICATION_FDF: \"application/fdf\";"
                },
                {
                    "name": "APPLICATION_FDT_XML",
                    "type": "variable",
                    "doc": "APPLICATION_FDT_XML: \"application/fdt+xml\";"
                },
                {
                    "name": "APPLICATION_FHIR_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_FHIR_JSON: \"application/fhir+json\";"
                },
                {
                    "name": "APPLICATION_FHIR_XML",
                    "type": "variable",
                    "doc": "APPLICATION_FHIR_XML: \"application/fhir+xml\";"
                },
                {
                    "name": "APPLICATION_FITS",
                    "type": "variable",
                    "doc": "APPLICATION_FITS: \"application/fits\";"
                },
                {
                    "name": "APPLICATION_FLEXFEC",
                    "type": "variable",
                    "doc": "APPLICATION_FLEXFEC: \"application/flexfec\";"
                },
                {
                    "name": "APPLICATION_FONT_SFNT",
                    "type": "variable",
                    "doc": "APPLICATION_FONT_SFNT: \"application/font-sfnt\";"
                },
                {
                    "name": "APPLICATION_FONT_TDPFR",
                    "type": "variable",
                    "doc": "APPLICATION_FONT_TDPFR: \"application/font-tdpfr\";"
                },
                {
                    "name": "APPLICATION_FONT_WOFF",
                    "type": "variable",
                    "doc": "APPLICATION_FONT_WOFF: \"application/font-woff\";"
                },
                {
                    "name": "APPLICATION_FORM_URLENCODED",
                    "type": "variable",
                    "doc": "APPLICATION_FORM_URLENCODED: \"application/x-www-form-urlencoded\";"
                },
                {
                    "name": "APPLICATION_FRAMEWORK_ATTRIBUTES_XML",
                    "type": "variable",
                    "doc": "APPLICATION_FRAMEWORK_ATTRIBUTES_XML: \"application/framework-attributes+xml\";"
                },
                {
                    "name": "APPLICATION_FUTURESPLASH",
                    "type": "variable",
                    "doc": "APPLICATION_FUTURESPLASH: \"application/futuresplash\";"
                },
                {
                    "name": "APPLICATION_GEOPACKAGE_SQLITE3",
                    "type": "variable",
                    "doc": "APPLICATION_GEOPACKAGE_SQLITE3: \"application/geopackage+sqlite3\";"
                },
                {
                    "name": "APPLICATION_GEOXACML_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_GEOXACML_JSON: \"application/geoxacml+json\";"
                },
                {
                    "name": "APPLICATION_GEOXACML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_GEOXACML_XML: \"application/geoxacml+xml\";"
                },
                {
                    "name": "APPLICATION_GEO_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_GEO_JSON: \"application/geo+json\";"
                },
                {
                    "name": "APPLICATION_GEO_JSON_SEQ",
                    "type": "variable",
                    "doc": "APPLICATION_GEO_JSON_SEQ: \"application/geo+json-seq\";"
                },
                {
                    "name": "APPLICATION_GHOSTVIEW",
                    "type": "variable",
                    "doc": "APPLICATION_GHOSTVIEW: \"application/ghostview\";"
                },
                {
                    "name": "APPLICATION_GLTF_BUFFER",
                    "type": "variable",
                    "doc": "APPLICATION_GLTF_BUFFER: \"application/gltf-buffer\";"
                },
                {
                    "name": "APPLICATION_GML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_GML_XML: \"application/gml+xml\";"
                },
                {
                    "name": "APPLICATION_GNAP_BINDING_JWS",
                    "type": "variable",
                    "doc": "APPLICATION_GNAP_BINDING_JWS: \"application/gnap-binding-jws\";"
                },
                {
                    "name": "APPLICATION_GNAP_BINDING_JWSD",
                    "type": "variable",
                    "doc": "APPLICATION_GNAP_BINDING_JWSD: \"application/gnap-binding-jwsd\";"
                },
                {
                    "name": "APPLICATION_GNAP_BINDING_ROTATION_JWS",
                    "type": "variable",
                    "doc": "APPLICATION_GNAP_BINDING_ROTATION_JWS: \"application/gnap-binding-rotation-jws\";"
                },
                {
                    "name": "APPLICATION_GNAP_BINDING_ROTATION_JWSD",
                    "type": "variable",
                    "doc": "APPLICATION_GNAP_BINDING_ROTATION_JWSD: \"application/gnap-binding-rotation-jwsd\";"
                },
                {
                    "name": "APPLICATION_GZIP",
                    "type": "variable",
                    "doc": "APPLICATION_GZIP: \"application/gzip\";"
                },
                {
                    "name": "APPLICATION_H224",
                    "type": "variable",
                    "doc": "APPLICATION_H224: \"application/H224\";"
                },
                {
                    "name": "APPLICATION_HELD_XML",
                    "type": "variable",
                    "doc": "APPLICATION_HELD_XML: \"application/held+xml\";"
                },
                {
                    "name": "APPLICATION_HL7V2_XML",
                    "type": "variable",
                    "doc": "APPLICATION_HL7V2_XML: \"application/hl7v2+xml\";"
                },
                {
                    "name": "APPLICATION_HTA",
                    "type": "variable",
                    "doc": "APPLICATION_HTA: \"application/hta\";"
                },
                {
                    "name": "APPLICATION_HTTP",
                    "type": "variable",
                    "doc": "APPLICATION_HTTP: \"application/http\";"
                },
                {
                    "name": "APPLICATION_HYPERSTUDIO",
                    "type": "variable",
                    "doc": "APPLICATION_HYPERSTUDIO: \"application/hyperstudio\";"
                },
                {
                    "name": "APPLICATION_IBE_KEY_REQUEST_XML",
                    "type": "variable",
                    "doc": "APPLICATION_IBE_KEY_REQUEST_XML: \"application/ibe-key-request+xml\";"
                },
                {
                    "name": "APPLICATION_IBE_PKG_REPLY_XML",
                    "type": "variable",
                    "doc": "APPLICATION_IBE_PKG_REPLY_XML: \"application/ibe-pkg-reply+xml\";"
                },
                {
                    "name": "APPLICATION_IBE_PP_DATA",
                    "type": "variable",
                    "doc": "APPLICATION_IBE_PP_DATA: \"application/ibe-pp-data\";"
                },
                {
                    "name": "APPLICATION_IGES",
                    "type": "variable",
                    "doc": "APPLICATION_IGES: \"application/iges\";"
                },
                {
                    "name": "APPLICATION_IM_ISCOMPOSING_XML",
                    "type": "variable",
                    "doc": "APPLICATION_IM_ISCOMPOSING_XML: \"application/im-iscomposing+xml\";"
                },
                {
                    "name": "APPLICATION_INDEX",
                    "type": "variable",
                    "doc": "APPLICATION_INDEX: \"application/index\";"
                },
                {
                    "name": "APPLICATION_INDEX_CMD",
                    "type": "variable",
                    "doc": "APPLICATION_INDEX_CMD: \"application/index.cmd\";"
                },
                {
                    "name": "APPLICATION_INDEX_OBJ",
                    "type": "variable",
                    "doc": "APPLICATION_INDEX_OBJ: \"application/index.obj\";"
                },
                {
                    "name": "APPLICATION_INDEX_RESPONSE",
                    "type": "variable",
                    "doc": "APPLICATION_INDEX_RESPONSE: \"application/index.response\";"
                },
                {
                    "name": "APPLICATION_INDEX_VND",
                    "type": "variable",
                    "doc": "APPLICATION_INDEX_VND: \"application/index.vnd\";"
                },
                {
                    "name": "APPLICATION_INKML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_INKML_XML: \"application/inkml+xml\";"
                },
                {
                    "name": "APPLICATION_IOTA_MMC_WBXML",
                    "type": "variable",
                    "doc": "APPLICATION_IOTA_MMC_WBXML: \"application/iota.mmc-wbxml\";"
                },
                {
                    "name": "APPLICATION_IOTA_MMC_XML",
                    "type": "variable",
                    "doc": "APPLICATION_IOTA_MMC_XML: \"application/iota.mmc-xml\";"
                },
                {
                    "name": "APPLICATION_IOTP",
                    "type": "variable",
                    "doc": "APPLICATION_IOTP: \"application/IOTP\";"
                },
                {
                    "name": "APPLICATION_IPFIX",
                    "type": "variable",
                    "doc": "APPLICATION_IPFIX: \"application/ipfix\";"
                },
                {
                    "name": "APPLICATION_IPP",
                    "type": "variable",
                    "doc": "APPLICATION_IPP: \"application/ipp\";"
                },
                {
                    "name": "APPLICATION_ISUP",
                    "type": "variable",
                    "doc": "APPLICATION_ISUP: \"application/ISUP\";"
                },
                {
                    "name": "APPLICATION_ITS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_ITS_XML: \"application/its+xml\";"
                },
                {
                    "name": "APPLICATION_JAVASCRIPT",
                    "type": "variable",
                    "doc": "APPLICATION_JAVASCRIPT: \"application/javascript\";"
                },
                {
                    "name": "APPLICATION_JAVA_ARCHIVE",
                    "type": "variable",
                    "doc": "APPLICATION_JAVA_ARCHIVE: \"application/java-archive\";"
                },
                {
                    "name": "APPLICATION_JAVA_SERIALIZED_OBJECT",
                    "type": "variable",
                    "doc": "APPLICATION_JAVA_SERIALIZED_OBJECT: \"application/java-serialized-object\";"
                },
                {
                    "name": "APPLICATION_JAVA_VM",
                    "type": "variable",
                    "doc": "APPLICATION_JAVA_VM: \"application/java-vm\";"
                },
                {
                    "name": "APPLICATION_JF2FEED_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_JF2FEED_JSON: \"application/jf2feed+json\";"
                },
                {
                    "name": "APPLICATION_JOSE",
                    "type": "variable",
                    "doc": "APPLICATION_JOSE: \"application/jose\";"
                },
                {
                    "name": "APPLICATION_JOSE_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_JOSE_JSON: \"application/jose+json\";"
                },
                {
                    "name": "APPLICATION_JRD_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_JRD_JSON: \"application/jrd+json\";"
                },
                {
                    "name": "APPLICATION_JSCALENDAR_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_JSCALENDAR_JSON: \"application/jscalendar+json\";"
                },
                {
                    "name": "APPLICATION_JSCONTACT_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_JSCONTACT_JSON: \"application/jscontact+json\";"
                },
                {
                    "name": "APPLICATION_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_JSON: \"application/json\";"
                },
                {
                    "name": "APPLICATION_JSONPATH",
                    "type": "variable",
                    "doc": "APPLICATION_JSONPATH: \"application/jsonpath\";"
                },
                {
                    "name": "APPLICATION_JSON_PATCH_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_JSON_PATCH_JSON: \"application/json-patch+json\";"
                },
                {
                    "name": "APPLICATION_JSON_SEQ",
                    "type": "variable",
                    "doc": "APPLICATION_JSON_SEQ: \"application/json-seq\";"
                },
                {
                    "name": "APPLICATION_JWK_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_JWK_JSON: \"application/jwk+json\";"
                },
                {
                    "name": "APPLICATION_JWK_SET_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_JWK_SET_JSON: \"application/jwk-set+json\";"
                },
                {
                    "name": "APPLICATION_JWT",
                    "type": "variable",
                    "doc": "APPLICATION_JWT: \"application/jwt\";"
                },
                {
                    "name": "APPLICATION_KPML_REQUEST_XML",
                    "type": "variable",
                    "doc": "APPLICATION_KPML_REQUEST_XML: \"application/kpml-request+xml\";"
                },
                {
                    "name": "APPLICATION_KPML_RESPONSE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_KPML_RESPONSE_XML: \"application/kpml-response+xml\";"
                },
                {
                    "name": "APPLICATION_LD_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_LD_JSON: \"application/ld+json\";"
                },
                {
                    "name": "APPLICATION_LGR_XML",
                    "type": "variable",
                    "doc": "APPLICATION_LGR_XML: \"application/lgr+xml\";"
                },
                {
                    "name": "APPLICATION_LINKSET",
                    "type": "variable",
                    "doc": "APPLICATION_LINKSET: \"application/linkset\";"
                },
                {
                    "name": "APPLICATION_LINKSET_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_LINKSET_JSON: \"application/linkset+json\";"
                },
                {
                    "name": "APPLICATION_LINK_FORMAT",
                    "type": "variable",
                    "doc": "APPLICATION_LINK_FORMAT: \"application/link-format\";"
                },
                {
                    "name": "APPLICATION_LOAD_CONTROL_XML",
                    "type": "variable",
                    "doc": "APPLICATION_LOAD_CONTROL_XML: \"application/load-control+xml\";"
                },
                {
                    "name": "APPLICATION_LOGOUT_JWT",
                    "type": "variable",
                    "doc": "APPLICATION_LOGOUT_JWT: \"application/logout+jwt\";"
                },
                {
                    "name": "APPLICATION_LOSTSYNC_XML",
                    "type": "variable",
                    "doc": "APPLICATION_LOSTSYNC_XML: \"application/lostsync+xml\";"
                },
                {
                    "name": "APPLICATION_LOST_XML",
                    "type": "variable",
                    "doc": "APPLICATION_LOST_XML: \"application/lost+xml\";"
                },
                {
                    "name": "APPLICATION_LPF_ZIP",
                    "type": "variable",
                    "doc": "APPLICATION_LPF_ZIP: \"application/lpf+zip\";"
                },
                {
                    "name": "APPLICATION_LXF",
                    "type": "variable",
                    "doc": "APPLICATION_LXF: \"application/LXF\";"
                },
                {
                    "name": "APPLICATION_M3G",
                    "type": "variable",
                    "doc": "APPLICATION_M3G: \"application/m3g\";"
                },
                {
                    "name": "APPLICATION_MACWRITEII",
                    "type": "variable",
                    "doc": "APPLICATION_MACWRITEII: \"application/macwriteii\";"
                },
                {
                    "name": "APPLICATION_MAC_BINHEX40",
                    "type": "variable",
                    "doc": "APPLICATION_MAC_BINHEX40: \"application/mac-binhex40\";"
                },
                {
                    "name": "APPLICATION_MAC_COMPACTPRO",
                    "type": "variable",
                    "doc": "APPLICATION_MAC_COMPACTPRO: \"application/mac-compactpro\";"
                },
                {
                    "name": "APPLICATION_MADS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_MADS_XML: \"application/mads+xml\";"
                },
                {
                    "name": "APPLICATION_MANIFEST_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_MANIFEST_JSON: \"application/manifest+json\";"
                },
                {
                    "name": "APPLICATION_MARC",
                    "type": "variable",
                    "doc": "APPLICATION_MARC: \"application/marc\";"
                },
                {
                    "name": "APPLICATION_MARCXML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_MARCXML_XML: \"application/marcxml+xml\";"
                },
                {
                    "name": "APPLICATION_MATHEMATICA",
                    "type": "variable",
                    "doc": "APPLICATION_MATHEMATICA: \"application/mathematica\";"
                },
                {
                    "name": "APPLICATION_MATHML_CONTENT_XML",
                    "type": "variable",
                    "doc": "APPLICATION_MATHML_CONTENT_XML: \"application/mathml-content+xml\";"
                },
                {
                    "name": "APPLICATION_MATHML_PRESENTATION_XML",
                    "type": "variable",
                    "doc": "APPLICATION_MATHML_PRESENTATION_XML: \"application/mathml-presentation+xml\";"
                },
                {
                    "name": "APPLICATION_MATHML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_MATHML_XML: \"application/mathml+xml\";"
                },
                {
                    "name": "APPLICATION_MBMS_ASSOCIATED_PROCEDURE_DESCRIPTION_XML",
                    "type": "variable",
                    "doc": "APPLICATION_MBMS_ASSOCIATED_PROCEDURE_DESCRIPTION_XML: \"application/mbms-associated-procedure-description+xml\";"
                },
                {
                    "name": "APPLICATION_MBMS_DEREGISTER_XML",
                    "type": "variable",
                    "doc": "APPLICATION_MBMS_DEREGISTER_XML: \"application/mbms-deregister+xml\";"
                },
                {
                    "name": "APPLICATION_MBMS_ENVELOPE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_MBMS_ENVELOPE_XML: \"application/mbms-envelope+xml\";"
                },
                {
                    "name": "APPLICATION_MBMS_MSK_RESPONSE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_MBMS_MSK_RESPONSE_XML: \"application/mbms-msk-response+xml\";"
                },
                {
                    "name": "APPLICATION_MBMS_MSK_XML",
                    "type": "variable",
                    "doc": "APPLICATION_MBMS_MSK_XML: \"application/mbms-msk+xml\";"
                },
                {
                    "name": "APPLICATION_MBMS_PROTECTION_DESCRIPTION_XML",
                    "type": "variable",
                    "doc": "APPLICATION_MBMS_PROTECTION_DESCRIPTION_XML: \"application/mbms-protection-description+xml\";"
                },
                {
                    "name": "APPLICATION_MBMS_RECEPTION_REPORT_XML",
                    "type": "variable",
                    "doc": "APPLICATION_MBMS_RECEPTION_REPORT_XML: \"application/mbms-reception-report+xml\";"
                },
                {
                    "name": "APPLICATION_MBMS_REGISTER_RESPONSE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_MBMS_REGISTER_RESPONSE_XML: \"application/mbms-register-response+xml\";"
                },
                {
                    "name": "APPLICATION_MBMS_REGISTER_XML",
                    "type": "variable",
                    "doc": "APPLICATION_MBMS_REGISTER_XML: \"application/mbms-register+xml\";"
                },
                {
                    "name": "APPLICATION_MBMS_SCHEDULE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_MBMS_SCHEDULE_XML: \"application/mbms-schedule+xml\";"
                },
                {
                    "name": "APPLICATION_MBMS_USER_SERVICE_DESCRIPTION_XML",
                    "type": "variable",
                    "doc": "APPLICATION_MBMS_USER_SERVICE_DESCRIPTION_XML: \"application/mbms-user-service-description+xml\";"
                },
                {
                    "name": "APPLICATION_MBOX",
                    "type": "variable",
                    "doc": "APPLICATION_MBOX: \"application/mbox\";"
                },
                {
                    "name": "APPLICATION_MEDIASERVERCONTROL_XML",
                    "type": "variable",
                    "doc": "APPLICATION_MEDIASERVERCONTROL_XML: \"application/mediaservercontrol+xml\";"
                },
                {
                    "name": "APPLICATION_MEDIA_CONTROL_XML",
                    "type": "variable",
                    "doc": "APPLICATION_MEDIA_CONTROL_XML: \"application/media_control+xml\";"
                },
                {
                    "name": "APPLICATION_MEDIA_POLICY_DATASET_XML",
                    "type": "variable",
                    "doc": "APPLICATION_MEDIA_POLICY_DATASET_XML: \"application/media-policy-dataset+xml\";"
                },
                {
                    "name": "APPLICATION_MERGE_PATCH_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_MERGE_PATCH_JSON: \"application/merge-patch+json\";"
                },
                {
                    "name": "APPLICATION_METALINK4_XML",
                    "type": "variable",
                    "doc": "APPLICATION_METALINK4_XML: \"application/metalink4+xml\";"
                },
                {
                    "name": "APPLICATION_METS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_METS_XML: \"application/mets+xml\";"
                },
                {
                    "name": "APPLICATION_MF4",
                    "type": "variable",
                    "doc": "APPLICATION_MF4: \"application/MF4\";"
                },
                {
                    "name": "APPLICATION_MIKEY",
                    "type": "variable",
                    "doc": "APPLICATION_MIKEY: \"application/mikey\";"
                },
                {
                    "name": "APPLICATION_MIPC",
                    "type": "variable",
                    "doc": "APPLICATION_MIPC: \"application/mipc\";"
                },
                {
                    "name": "APPLICATION_MISSING_BLOCKS_CBOR_SEQ",
                    "type": "variable",
                    "doc": "APPLICATION_MISSING_BLOCKS_CBOR_SEQ: \"application/missing-blocks+cbor-seq\";"
                },
                {
                    "name": "APPLICATION_MMT_AEI_XML",
                    "type": "variable",
                    "doc": "APPLICATION_MMT_AEI_XML: \"application/mmt-aei+xml\";"
                },
                {
                    "name": "APPLICATION_MMT_USD_XML",
                    "type": "variable",
                    "doc": "APPLICATION_MMT_USD_XML: \"application/mmt-usd+xml\";"
                },
                {
                    "name": "APPLICATION_MODS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_MODS_XML: \"application/mods+xml\";"
                },
                {
                    "name": "APPLICATION_MOSSKEY_DATA",
                    "type": "variable",
                    "doc": "APPLICATION_MOSSKEY_DATA: \"application/mosskey-data\";"
                },
                {
                    "name": "APPLICATION_MOSSKEY_REQUEST",
                    "type": "variable",
                    "doc": "APPLICATION_MOSSKEY_REQUEST: \"application/mosskey-request\";"
                },
                {
                    "name": "APPLICATION_MOSS_KEYS",
                    "type": "variable",
                    "doc": "APPLICATION_MOSS_KEYS: \"application/moss-keys\";"
                },
                {
                    "name": "APPLICATION_MOSS_SIGNATURE",
                    "type": "variable",
                    "doc": "APPLICATION_MOSS_SIGNATURE: \"application/moss-signature\";"
                },
                {
                    "name": "APPLICATION_MP21",
                    "type": "variable",
                    "doc": "APPLICATION_MP21: \"application/mp21\";"
                },
                {
                    "name": "APPLICATION_MP4",
                    "type": "variable",
                    "doc": "APPLICATION_MP4: \"application/mp4\";"
                },
                {
                    "name": "APPLICATION_MPEG4_GENERIC",
                    "type": "variable",
                    "doc": "APPLICATION_MPEG4_GENERIC: \"application/mpeg4-generic\";"
                },
                {
                    "name": "APPLICATION_MPEG4_IOD",
                    "type": "variable",
                    "doc": "APPLICATION_MPEG4_IOD: \"application/mpeg4-iod\";"
                },
                {
                    "name": "APPLICATION_MPEG4_IOD_XMT",
                    "type": "variable",
                    "doc": "APPLICATION_MPEG4_IOD_XMT: \"application/mpeg4-iod-xmt\";"
                },
                {
                    "name": "APPLICATION_MRB_CONSUMER_XML",
                    "type": "variable",
                    "doc": "APPLICATION_MRB_CONSUMER_XML: \"application/mrb-consumer+xml\";"
                },
                {
                    "name": "APPLICATION_MRB_PUBLISH_XML",
                    "type": "variable",
                    "doc": "APPLICATION_MRB_PUBLISH_XML: \"application/mrb-publish+xml\";"
                },
                {
                    "name": "APPLICATION_MSACCESS",
                    "type": "variable",
                    "doc": "APPLICATION_MSACCESS: \"application/msaccess\";"
                },
                {
                    "name": "APPLICATION_MSC_IVR_XML",
                    "type": "variable",
                    "doc": "APPLICATION_MSC_IVR_XML: \"application/msc-ivr+xml\";"
                },
                {
                    "name": "APPLICATION_MSC_MIXER_XML",
                    "type": "variable",
                    "doc": "APPLICATION_MSC_MIXER_XML: \"application/msc-mixer+xml\";"
                },
                {
                    "name": "APPLICATION_MSEXCEL",
                    "type": "variable",
                    "doc": "APPLICATION_MSEXCEL: \"application/vnd.ms-excel\";"
                },
                {
                    "name": "APPLICATION_MSPOWERPOINT",
                    "type": "variable",
                    "doc": "APPLICATION_MSPOWERPOINT: \"application/vnd.ms-powerpoint\";"
                },
                {
                    "name": "APPLICATION_MSWORD",
                    "type": "variable",
                    "doc": "APPLICATION_MSWORD: \"application/msword\";"
                },
                {
                    "name": "APPLICATION_MS_EXCEL",
                    "type": "variable",
                    "doc": "APPLICATION_MS_EXCEL: \"application/vnd.ms-excel\";"
                },
                {
                    "name": "APPLICATION_MS_POWERPOINT",
                    "type": "variable",
                    "doc": "APPLICATION_MS_POWERPOINT: \"application/vnd.ms-powerpoint\";"
                },
                {
                    "name": "APPLICATION_MS_TNEF",
                    "type": "variable",
                    "doc": "APPLICATION_MS_TNEF: \"application/ms-tnef\";"
                },
                {
                    "name": "APPLICATION_MS_WORD",
                    "type": "variable",
                    "doc": "APPLICATION_MS_WORD: \"application/msword\";"
                },
                {
                    "name": "APPLICATION_MUD_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_MUD_JSON: \"application/mud+json\";"
                },
                {
                    "name": "APPLICATION_MULTIPART_CORE",
                    "type": "variable",
                    "doc": "APPLICATION_MULTIPART_CORE: \"application/multipart-core\";"
                },
                {
                    "name": "APPLICATION_MXF",
                    "type": "variable",
                    "doc": "APPLICATION_MXF: \"application/mxf\";"
                },
                {
                    "name": "APPLICATION_NASDATA",
                    "type": "variable",
                    "doc": "APPLICATION_NASDATA: \"application/nasdata\";"
                },
                {
                    "name": "APPLICATION_NEWS_CHECKGROUPS",
                    "type": "variable",
                    "doc": "APPLICATION_NEWS_CHECKGROUPS: \"application/news-checkgroups\";"
                },
                {
                    "name": "APPLICATION_NEWS_GROUPINFO",
                    "type": "variable",
                    "doc": "APPLICATION_NEWS_GROUPINFO: \"application/news-groupinfo\";"
                },
                {
                    "name": "APPLICATION_NEWS_MESSAGE_ID",
                    "type": "variable",
                    "doc": "APPLICATION_NEWS_MESSAGE_ID: \"application/news-message-id\";"
                },
                {
                    "name": "APPLICATION_NEWS_TRANSMISSION",
                    "type": "variable",
                    "doc": "APPLICATION_NEWS_TRANSMISSION: \"application/news-transmission\";"
                },
                {
                    "name": "APPLICATION_NLSML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_NLSML_XML: \"application/nlsml+xml\";"
                },
                {
                    "name": "APPLICATION_NODE",
                    "type": "variable",
                    "doc": "APPLICATION_NODE: \"application/node\";"
                },
                {
                    "name": "APPLICATION_NSS",
                    "type": "variable",
                    "doc": "APPLICATION_NSS: \"application/nss\";"
                },
                {
                    "name": "APPLICATION_N_QUADS",
                    "type": "variable",
                    "doc": "APPLICATION_N_QUADS: \"application/n-quads\";"
                },
                {
                    "name": "APPLICATION_N_TRIPLES",
                    "type": "variable",
                    "doc": "APPLICATION_N_TRIPLES: \"application/n-triples\";"
                },
                {
                    "name": "APPLICATION_OASIS_OPENDOCUMENT_PRESENTATION",
                    "type": "variable",
                    "doc": "APPLICATION_OASIS_OPENDOCUMENT_PRESENTATION: \"application/vnd.oasis.opendocument.presentation\";"
                },
                {
                    "name": "APPLICATION_OASIS_OPENDOCUMENT_SPREADSHEET",
                    "type": "variable",
                    "doc": "APPLICATION_OASIS_OPENDOCUMENT_SPREADSHEET: \"application/vnd.oasis.opendocument.spreadsheet\";"
                },
                {
                    "name": "APPLICATION_OASIS_OPENDOCUMENT_TEXT",
                    "type": "variable",
                    "doc": "APPLICATION_OASIS_OPENDOCUMENT_TEXT: \"application/vnd.oasis.opendocument.text\";"
                },
                {
                    "name": "APPLICATION_OASIS_PRESENTATION",
                    "type": "variable",
                    "doc": "APPLICATION_OASIS_PRESENTATION: \"application/vnd.oasis.opendocument.presentation\";"
                },
                {
                    "name": "APPLICATION_OASIS_SPREADSHEET",
                    "type": "variable",
                    "doc": "APPLICATION_OASIS_SPREADSHEET: \"application/vnd.oasis.opendocument.spreadsheet\";"
                },
                {
                    "name": "APPLICATION_OASIS_TEXT",
                    "type": "variable",
                    "doc": "APPLICATION_OASIS_TEXT: \"application/vnd.oasis.opendocument.text\";"
                },
                {
                    "name": "APPLICATION_OAUTH_AUTHZ_REQ_JWT",
                    "type": "variable",
                    "doc": "APPLICATION_OAUTH_AUTHZ_REQ_JWT: \"application/oauth-authz-req+jwt\";"
                },
                {
                    "name": "APPLICATION_OBLIVIOUS_DNS_MESSAGE",
                    "type": "variable",
                    "doc": "APPLICATION_OBLIVIOUS_DNS_MESSAGE: \"application/oblivious-dns-message\";"
                },
                {
                    "name": "APPLICATION_OCSP_REQUEST",
                    "type": "variable",
                    "doc": "APPLICATION_OCSP_REQUEST: \"application/ocsp-request\";"
                },
                {
                    "name": "APPLICATION_OCSP_RESPONSE",
                    "type": "variable",
                    "doc": "APPLICATION_OCSP_RESPONSE: \"application/ocsp-response\";"
                },
                {
                    "name": "APPLICATION_OCTET_STREAM",
                    "type": "variable",
                    "doc": "APPLICATION_OCTET_STREAM: \"application/octet-stream\";"
                },
                {
                    "name": "APPLICATION_ODA",
                    "type": "variable",
                    "doc": "APPLICATION_ODA: \"application/oda\";"
                },
                {
                    "name": "APPLICATION_ODM_XML",
                    "type": "variable",
                    "doc": "APPLICATION_ODM_XML: \"application/odm+xml\";"
                },
                {
                    "name": "APPLICATION_ODX",
                    "type": "variable",
                    "doc": "APPLICATION_ODX: \"application/ODX\";"
                },
                {
                    "name": "APPLICATION_OEBPS_PACKAGE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_OEBPS_PACKAGE_XML: \"application/oebps-package+xml\";"
                },
                {
                    "name": "APPLICATION_OGG",
                    "type": "variable",
                    "doc": "APPLICATION_OGG: \"application/ogg\";"
                },
                {
                    "name": "APPLICATION_OHTTP_KEYS",
                    "type": "variable",
                    "doc": "APPLICATION_OHTTP_KEYS: \"application/ohttp-keys\";"
                },
                {
                    "name": "APPLICATION_OMA_DIRECTORY_XML",
                    "type": "variable",
                    "doc": "APPLICATION_OMA_DIRECTORY_XML: \"application/oma-directory+xml\";"
                },
                {
                    "name": "APPLICATION_ONENOTE",
                    "type": "variable",
                    "doc": "APPLICATION_ONENOTE: \"application/onenote\";"
                },
                {
                    "name": "APPLICATION_OPC_NODESET_XML",
                    "type": "variable",
                    "doc": "APPLICATION_OPC_NODESET_XML: \"application/opc-nodeset+xml\";"
                },
                {
                    "name": "APPLICATION_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_PRESENTATION",
                    "type": "variable",
                    "doc": "APPLICATION_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_PRESENTATION: \"application/vnd.openxmlformats-officedocument.presentationml.presentation\";"
                },
                {
                    "name": "APPLICATION_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_SHEET",
                    "type": "variable",
                    "doc": "APPLICATION_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_SHEET: \"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\";"
                },
                {
                    "name": "APPLICATION_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_DOCUMENT",
                    "type": "variable",
                    "doc": "APPLICATION_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_DOCUMENT: \"application/vnd.openxmlformats-officedocument.wordprocessingml.document\";"
                },
                {
                    "name": "APPLICATION_OPENXML_DOCUMENT",
                    "type": "variable",
                    "doc": "APPLICATION_OPENXML_DOCUMENT: \"application/vnd.openxmlformats-officedocument.wordprocessingml.document\";"
                },
                {
                    "name": "APPLICATION_OPENXML_PRESENTATION",
                    "type": "variable",
                    "doc": "APPLICATION_OPENXML_PRESENTATION: \"application/vnd.openxmlformats-officedocument.presentationml.presentation\";"
                },
                {
                    "name": "APPLICATION_OPENXML_SHEET",
                    "type": "variable",
                    "doc": "APPLICATION_OPENXML_SHEET: \"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\";"
                },
                {
                    "name": "APPLICATION_OSCORE",
                    "type": "variable",
                    "doc": "APPLICATION_OSCORE: \"application/oscore\";"
                },
                {
                    "name": "APPLICATION_OXPS",
                    "type": "variable",
                    "doc": "APPLICATION_OXPS: \"application/oxps\";"
                },
                {
                    "name": "APPLICATION_P21",
                    "type": "variable",
                    "doc": "APPLICATION_P21: \"application/p21\";"
                },
                {
                    "name": "APPLICATION_P21_ZIP",
                    "type": "variable",
                    "doc": "APPLICATION_P21_ZIP: \"application/p21+zip\";"
                },
                {
                    "name": "APPLICATION_P2P_OVERLAY_XML",
                    "type": "variable",
                    "doc": "APPLICATION_P2P_OVERLAY_XML: \"application/p2p-overlay+xml\";"
                },
                {
                    "name": "APPLICATION_PARITYFEC",
                    "type": "variable",
                    "doc": "APPLICATION_PARITYFEC: \"application/parityfec\";"
                },
                {
                    "name": "APPLICATION_PASSPORT",
                    "type": "variable",
                    "doc": "APPLICATION_PASSPORT: \"application/passport\";"
                },
                {
                    "name": "APPLICATION_PATCH_OPS_ERROR_XML",
                    "type": "variable",
                    "doc": "APPLICATION_PATCH_OPS_ERROR_XML: \"application/patch-ops-error+xml\";"
                },
                {
                    "name": "APPLICATION_PDF",
                    "type": "variable",
                    "doc": "APPLICATION_PDF: \"application/pdf\";"
                },
                {
                    "name": "APPLICATION_PDX",
                    "type": "variable",
                    "doc": "APPLICATION_PDX: \"application/PDX\";"
                },
                {
                    "name": "APPLICATION_PEM_CERTIFICATE_CHAIN",
                    "type": "variable",
                    "doc": "APPLICATION_PEM_CERTIFICATE_CHAIN: \"application/pem-certificate-chain\";"
                },
                {
                    "name": "APPLICATION_PGP_ENCRYPTED",
                    "type": "variable",
                    "doc": "APPLICATION_PGP_ENCRYPTED: \"application/pgp-encrypted\";"
                },
                {
                    "name": "APPLICATION_PGP_KEYS",
                    "type": "variable",
                    "doc": "APPLICATION_PGP_KEYS: \"application/pgp-keys\";"
                },
                {
                    "name": "APPLICATION_PGP_SIGNATURE",
                    "type": "variable",
                    "doc": "APPLICATION_PGP_SIGNATURE: \"application/pgp-signature\";"
                },
                {
                    "name": "APPLICATION_PICS_RULES",
                    "type": "variable",
                    "doc": "APPLICATION_PICS_RULES: \"application/pics-rules\";"
                },
                {
                    "name": "APPLICATION_PIDF_DIFF_XML",
                    "type": "variable",
                    "doc": "APPLICATION_PIDF_DIFF_XML: \"application/pidf-diff+xml\";"
                },
                {
                    "name": "APPLICATION_PIDF_XML",
                    "type": "variable",
                    "doc": "APPLICATION_PIDF_XML: \"application/pidf+xml\";"
                },
                {
                    "name": "APPLICATION_PKCS10",
                    "type": "variable",
                    "doc": "APPLICATION_PKCS10: \"application/pkcs10\";"
                },
                {
                    "name": "APPLICATION_PKCS12",
                    "type": "variable",
                    "doc": "APPLICATION_PKCS12: \"application/pkcs12\";"
                },
                {
                    "name": "APPLICATION_PKCS7_MIME",
                    "type": "variable",
                    "doc": "APPLICATION_PKCS7_MIME: \"application/pkcs7-mime\";"
                },
                {
                    "name": "APPLICATION_PKCS7_SIGNATURE",
                    "type": "variable",
                    "doc": "APPLICATION_PKCS7_SIGNATURE: \"application/pkcs7-signature\";"
                },
                {
                    "name": "APPLICATION_PKCS8",
                    "type": "variable",
                    "doc": "APPLICATION_PKCS8: \"application/pkcs8\";"
                },
                {
                    "name": "APPLICATION_PKCS8_ENCRYPTED",
                    "type": "variable",
                    "doc": "APPLICATION_PKCS8_ENCRYPTED: \"application/pkcs8-encrypted\";"
                },
                {
                    "name": "APPLICATION_PKIXCMP",
                    "type": "variable",
                    "doc": "APPLICATION_PKIXCMP: \"application/pkixcmp\";"
                },
                {
                    "name": "APPLICATION_PKIX_ATTR_CERT",
                    "type": "variable",
                    "doc": "APPLICATION_PKIX_ATTR_CERT: \"application/pkix-attr-cert\";"
                },
                {
                    "name": "APPLICATION_PKIX_CERT",
                    "type": "variable",
                    "doc": "APPLICATION_PKIX_CERT: \"application/pkix-cert\";"
                },
                {
                    "name": "APPLICATION_PKIX_CRL",
                    "type": "variable",
                    "doc": "APPLICATION_PKIX_CRL: \"application/pkix-crl\";"
                },
                {
                    "name": "APPLICATION_PKIX_PKIPATH",
                    "type": "variable",
                    "doc": "APPLICATION_PKIX_PKIPATH: \"application/pkix-pkipath\";"
                },
                {
                    "name": "APPLICATION_PLS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_PLS_XML: \"application/pls+xml\";"
                },
                {
                    "name": "APPLICATION_POC_SETTINGS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_POC_SETTINGS_XML: \"application/poc-settings+xml\";"
                },
                {
                    "name": "APPLICATION_POSTSCRIPT",
                    "type": "variable",
                    "doc": "APPLICATION_POSTSCRIPT: \"application/postscript\";"
                },
                {
                    "name": "APPLICATION_PPSP_TRACKER_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_PPSP_TRACKER_JSON: \"application/ppsp-tracker+json\";"
                },
                {
                    "name": "APPLICATION_PRIVATE_TOKEN_ISSUER_DIRECTORY",
                    "type": "variable",
                    "doc": "APPLICATION_PRIVATE_TOKEN_ISSUER_DIRECTORY: \"application/private-token-issuer-directory\";"
                },
                {
                    "name": "APPLICATION_PRIVATE_TOKEN_REQUEST",
                    "type": "variable",
                    "doc": "APPLICATION_PRIVATE_TOKEN_REQUEST: \"application/private-token-request\";"
                },
                {
                    "name": "APPLICATION_PRIVATE_TOKEN_RESPONSE",
                    "type": "variable",
                    "doc": "APPLICATION_PRIVATE_TOKEN_RESPONSE: \"application/private-token-response\";"
                },
                {
                    "name": "APPLICATION_PROBLEM_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_PROBLEM_JSON: \"application/problem+json\";"
                },
                {
                    "name": "APPLICATION_PROBLEM_XML",
                    "type": "variable",
                    "doc": "APPLICATION_PROBLEM_XML: \"application/problem+xml\";"
                },
                {
                    "name": "APPLICATION_PROVENANCE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_PROVENANCE_XML: \"application/provenance+xml\";"
                },
                {
                    "name": "APPLICATION_PRS_ALVESTRAND_TITRAX_SHEET",
                    "type": "variable",
                    "doc": "APPLICATION_PRS_ALVESTRAND_TITRAX_SHEET: \"application/prs.alvestrand.titrax-sheet\";"
                },
                {
                    "name": "APPLICATION_PRS_CWW",
                    "type": "variable",
                    "doc": "APPLICATION_PRS_CWW: \"application/prs.cww\";"
                },
                {
                    "name": "APPLICATION_PRS_CYN",
                    "type": "variable",
                    "doc": "APPLICATION_PRS_CYN: \"application/prs.cyn\";"
                },
                {
                    "name": "APPLICATION_PRS_HPUB_ZIP",
                    "type": "variable",
                    "doc": "APPLICATION_PRS_HPUB_ZIP: \"application/prs.hpub+zip\";"
                },
                {
                    "name": "APPLICATION_PRS_IMPLIED_DOCUMENT_XML",
                    "type": "variable",
                    "doc": "APPLICATION_PRS_IMPLIED_DOCUMENT_XML: \"application/prs.implied-document+xml\";"
                },
                {
                    "name": "APPLICATION_PRS_IMPLIED_EXECUTABLE",
                    "type": "variable",
                    "doc": "APPLICATION_PRS_IMPLIED_EXECUTABLE: \"application/prs.implied-executable\";"
                },
                {
                    "name": "APPLICATION_PRS_IMPLIED_OBJECT_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_PRS_IMPLIED_OBJECT_JSON: \"application/prs.implied-object+json\";"
                },
                {
                    "name": "APPLICATION_PRS_IMPLIED_OBJECT_JSON_SEQ",
                    "type": "variable",
                    "doc": "APPLICATION_PRS_IMPLIED_OBJECT_JSON_SEQ: \"application/prs.implied-object+json-seq\";"
                },
                {
                    "name": "APPLICATION_PRS_IMPLIED_OBJECT_YAML",
                    "type": "variable",
                    "doc": "APPLICATION_PRS_IMPLIED_OBJECT_YAML: \"application/prs.implied-object+yaml\";"
                },
                {
                    "name": "APPLICATION_PRS_IMPLIED_STRUCTURE",
                    "type": "variable",
                    "doc": "APPLICATION_PRS_IMPLIED_STRUCTURE: \"application/prs.implied-structure\";"
                },
                {
                    "name": "APPLICATION_PRS_NPREND",
                    "type": "variable",
                    "doc": "APPLICATION_PRS_NPREND: \"application/prs.nprend\";"
                },
                {
                    "name": "APPLICATION_PRS_PLUCKER",
                    "type": "variable",
                    "doc": "APPLICATION_PRS_PLUCKER: \"application/prs.plucker\";"
                },
                {
                    "name": "APPLICATION_PRS_RDF_XML_CRYPT",
                    "type": "variable",
                    "doc": "APPLICATION_PRS_RDF_XML_CRYPT: \"application/prs.rdf-xml-crypt\";"
                },
                {
                    "name": "APPLICATION_PRS_VCFBZIP2",
                    "type": "variable",
                    "doc": "APPLICATION_PRS_VCFBZIP2: \"application/prs.vcfbzip2\";"
                },
                {
                    "name": "APPLICATION_PRS_XSF_XML",
                    "type": "variable",
                    "doc": "APPLICATION_PRS_XSF_XML: \"application/prs.xsf+xml\";"
                },
                {
                    "name": "APPLICATION_PSKC_XML",
                    "type": "variable",
                    "doc": "APPLICATION_PSKC_XML: \"application/pskc+xml\";"
                },
                {
                    "name": "APPLICATION_PVD_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_PVD_JSON: \"application/pvd+json\";"
                },
                {
                    "name": "APPLICATION_QSIG",
                    "type": "variable",
                    "doc": "APPLICATION_QSIG: \"application/QSIG\";"
                },
                {
                    "name": "APPLICATION_RAPTORFEC",
                    "type": "variable",
                    "doc": "APPLICATION_RAPTORFEC: \"application/raptorfec\";"
                },
                {
                    "name": "APPLICATION_RDAP_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_RDAP_JSON: \"application/rdap+json\";"
                },
                {
                    "name": "APPLICATION_RDF_XML",
                    "type": "variable",
                    "doc": "APPLICATION_RDF_XML: \"application/rdf+xml\";"
                },
                {
                    "name": "APPLICATION_REGINFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_REGINFO_XML: \"application/reginfo+xml\";"
                },
                {
                    "name": "APPLICATION_RELAX_NG_COMPACT_SYNTAX",
                    "type": "variable",
                    "doc": "APPLICATION_RELAX_NG_COMPACT_SYNTAX: \"application/relax-ng-compact-syntax\";"
                },
                {
                    "name": "APPLICATION_REMOTE_PRINTING",
                    "type": "variable",
                    "doc": "APPLICATION_REMOTE_PRINTING: \"application/remote-printing\";"
                },
                {
                    "name": "APPLICATION_REPUTON_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_REPUTON_JSON: \"application/reputon+json\";"
                },
                {
                    "name": "APPLICATION_RESOURCE_LISTS_DIFF_XML",
                    "type": "variable",
                    "doc": "APPLICATION_RESOURCE_LISTS_DIFF_XML: \"application/resource-lists-diff+xml\";"
                },
                {
                    "name": "APPLICATION_RESOURCE_LISTS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_RESOURCE_LISTS_XML: \"application/resource-lists+xml\";"
                },
                {
                    "name": "APPLICATION_RFC_XML",
                    "type": "variable",
                    "doc": "APPLICATION_RFC_XML: \"application/rfc+xml\";"
                },
                {
                    "name": "APPLICATION_RISCOS",
                    "type": "variable",
                    "doc": "APPLICATION_RISCOS: \"application/riscos\";"
                },
                {
                    "name": "APPLICATION_RLMI_XML",
                    "type": "variable",
                    "doc": "APPLICATION_RLMI_XML: \"application/rlmi+xml\";"
                },
                {
                    "name": "APPLICATION_RLS_SERVICES_XML",
                    "type": "variable",
                    "doc": "APPLICATION_RLS_SERVICES_XML: \"application/rls-services+xml\";"
                },
                {
                    "name": "APPLICATION_ROUTE_APD_XML",
                    "type": "variable",
                    "doc": "APPLICATION_ROUTE_APD_XML: \"application/route-apd+xml\";"
                },
                {
                    "name": "APPLICATION_ROUTE_S_TSID_XML",
                    "type": "variable",
                    "doc": "APPLICATION_ROUTE_S_TSID_XML: \"application/route-s-tsid+xml\";"
                },
                {
                    "name": "APPLICATION_ROUTE_USD_XML",
                    "type": "variable",
                    "doc": "APPLICATION_ROUTE_USD_XML: \"application/route-usd+xml\";"
                },
                {
                    "name": "APPLICATION_RPKI_CHECKLIST",
                    "type": "variable",
                    "doc": "APPLICATION_RPKI_CHECKLIST: \"application/rpki-checklist\";"
                },
                {
                    "name": "APPLICATION_RPKI_GHOSTBUSTERS",
                    "type": "variable",
                    "doc": "APPLICATION_RPKI_GHOSTBUSTERS: \"application/rpki-ghostbusters\";"
                },
                {
                    "name": "APPLICATION_RPKI_MANIFEST",
                    "type": "variable",
                    "doc": "APPLICATION_RPKI_MANIFEST: \"application/rpki-manifest\";"
                },
                {
                    "name": "APPLICATION_RPKI_PUBLICATION",
                    "type": "variable",
                    "doc": "APPLICATION_RPKI_PUBLICATION: \"application/rpki-publication\";"
                },
                {
                    "name": "APPLICATION_RPKI_ROA",
                    "type": "variable",
                    "doc": "APPLICATION_RPKI_ROA: \"application/rpki-roa\";"
                },
                {
                    "name": "APPLICATION_RPKI_UPDOWN",
                    "type": "variable",
                    "doc": "APPLICATION_RPKI_UPDOWN: \"application/rpki-updown\";"
                },
                {
                    "name": "APPLICATION_RSS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_RSS_XML: \"application/rss+xml\";"
                },
                {
                    "name": "APPLICATION_RTF",
                    "type": "variable",
                    "doc": "APPLICATION_RTF: \"application/rtf\";"
                },
                {
                    "name": "APPLICATION_RTPLOOPBACK",
                    "type": "variable",
                    "doc": "APPLICATION_RTPLOOPBACK: \"application/rtploopback\";"
                },
                {
                    "name": "APPLICATION_RTX",
                    "type": "variable",
                    "doc": "APPLICATION_RTX: \"application/rtx\";"
                },
                {
                    "name": "APPLICATION_SAMLASSERTION_XML",
                    "type": "variable",
                    "doc": "APPLICATION_SAMLASSERTION_XML: \"application/samlassertion+xml\";"
                },
                {
                    "name": "APPLICATION_SAMLMETADATA_XML",
                    "type": "variable",
                    "doc": "APPLICATION_SAMLMETADATA_XML: \"application/samlmetadata+xml\";"
                },
                {
                    "name": "APPLICATION_SARIF_EXTERNAL_PROPERTIES_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_SARIF_EXTERNAL_PROPERTIES_JSON: \"application/sarif-external-properties+json\";"
                },
                {
                    "name": "APPLICATION_SARIF_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_SARIF_JSON: \"application/sarif+json\";"
                },
                {
                    "name": "APPLICATION_SBE",
                    "type": "variable",
                    "doc": "APPLICATION_SBE: \"application/sbe\";"
                },
                {
                    "name": "APPLICATION_SBML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_SBML_XML: \"application/sbml+xml\";"
                },
                {
                    "name": "APPLICATION_SCAIP_XML",
                    "type": "variable",
                    "doc": "APPLICATION_SCAIP_XML: \"application/scaip+xml\";"
                },
                {
                    "name": "APPLICATION_SCIM_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_SCIM_JSON: \"application/scim+json\";"
                },
                {
                    "name": "APPLICATION_SCVP_CV_REQUEST",
                    "type": "variable",
                    "doc": "APPLICATION_SCVP_CV_REQUEST: \"application/scvp-cv-request\";"
                },
                {
                    "name": "APPLICATION_SCVP_CV_RESPONSE",
                    "type": "variable",
                    "doc": "APPLICATION_SCVP_CV_RESPONSE: \"application/scvp-cv-response\";"
                },
                {
                    "name": "APPLICATION_SCVP_VP_REQUEST",
                    "type": "variable",
                    "doc": "APPLICATION_SCVP_VP_REQUEST: \"application/scvp-vp-request\";"
                },
                {
                    "name": "APPLICATION_SCVP_VP_RESPONSE",
                    "type": "variable",
                    "doc": "APPLICATION_SCVP_VP_RESPONSE: \"application/scvp-vp-response\";"
                },
                {
                    "name": "APPLICATION_SDP",
                    "type": "variable",
                    "doc": "APPLICATION_SDP: \"application/sdp\";"
                },
                {
                    "name": "APPLICATION_SECEVENT_JWT",
                    "type": "variable",
                    "doc": "APPLICATION_SECEVENT_JWT: \"application/secevent+jwt\";"
                },
                {
                    "name": "APPLICATION_SENML_CBOR",
                    "type": "variable",
                    "doc": "APPLICATION_SENML_CBOR: \"application/senml+cbor\";"
                },
                {
                    "name": "APPLICATION_SENML_ETCH_CBOR",
                    "type": "variable",
                    "doc": "APPLICATION_SENML_ETCH_CBOR: \"application/senml-etch+cbor\";"
                },
                {
                    "name": "APPLICATION_SENML_ETCH_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_SENML_ETCH_JSON: \"application/senml-etch+json\";"
                },
                {
                    "name": "APPLICATION_SENML_EXI",
                    "type": "variable",
                    "doc": "APPLICATION_SENML_EXI: \"application/senml-exi\";"
                },
                {
                    "name": "APPLICATION_SENML_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_SENML_JSON: \"application/senml+json\";"
                },
                {
                    "name": "APPLICATION_SENML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_SENML_XML: \"application/senml+xml\";"
                },
                {
                    "name": "APPLICATION_SENSML_CBOR",
                    "type": "variable",
                    "doc": "APPLICATION_SENSML_CBOR: \"application/sensml+cbor\";"
                },
                {
                    "name": "APPLICATION_SENSML_EXI",
                    "type": "variable",
                    "doc": "APPLICATION_SENSML_EXI: \"application/sensml-exi\";"
                },
                {
                    "name": "APPLICATION_SENSML_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_SENSML_JSON: \"application/sensml+json\";"
                },
                {
                    "name": "APPLICATION_SENSML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_SENSML_XML: \"application/sensml+xml\";"
                },
                {
                    "name": "APPLICATION_SEP_EXI",
                    "type": "variable",
                    "doc": "APPLICATION_SEP_EXI: \"application/sep-exi\";"
                },
                {
                    "name": "APPLICATION_SEP_XML",
                    "type": "variable",
                    "doc": "APPLICATION_SEP_XML: \"application/sep+xml\";"
                },
                {
                    "name": "APPLICATION_SESSION_INFO",
                    "type": "variable",
                    "doc": "APPLICATION_SESSION_INFO: \"application/session-info\";"
                },
                {
                    "name": "APPLICATION_SET_PAYMENT",
                    "type": "variable",
                    "doc": "APPLICATION_SET_PAYMENT: \"application/set-payment\";"
                },
                {
                    "name": "APPLICATION_SET_PAYMENT_INITIATION",
                    "type": "variable",
                    "doc": "APPLICATION_SET_PAYMENT_INITIATION: \"application/set-payment-initiation\";"
                },
                {
                    "name": "APPLICATION_SET_REGISTRATION",
                    "type": "variable",
                    "doc": "APPLICATION_SET_REGISTRATION: \"application/set-registration\";"
                },
                {
                    "name": "APPLICATION_SET_REGISTRATION_INITIATION",
                    "type": "variable",
                    "doc": "APPLICATION_SET_REGISTRATION_INITIATION: \"application/set-registration-initiation\";"
                },
                {
                    "name": "APPLICATION_SGML",
                    "type": "variable",
                    "doc": "APPLICATION_SGML: \"application/SGML\";"
                },
                {
                    "name": "APPLICATION_SGML_OPEN_CATALOG",
                    "type": "variable",
                    "doc": "APPLICATION_SGML_OPEN_CATALOG: \"application/sgml-open-catalog\";"
                },
                {
                    "name": "APPLICATION_SHF_XML",
                    "type": "variable",
                    "doc": "APPLICATION_SHF_XML: \"application/shf+xml\";"
                },
                {
                    "name": "APPLICATION_SIEVE",
                    "type": "variable",
                    "doc": "APPLICATION_SIEVE: \"application/sieve\";"
                },
                {
                    "name": "APPLICATION_SIMPLESYMBOLCONTAINER",
                    "type": "variable",
                    "doc": "APPLICATION_SIMPLESYMBOLCONTAINER: \"application/simpleSymbolContainer\";"
                },
                {
                    "name": "APPLICATION_SIMPLE_FILTER_XML",
                    "type": "variable",
                    "doc": "APPLICATION_SIMPLE_FILTER_XML: \"application/simple-filter+xml\";"
                },
                {
                    "name": "APPLICATION_SIMPLE_MESSAGE_SUMMARY",
                    "type": "variable",
                    "doc": "APPLICATION_SIMPLE_MESSAGE_SUMMARY: \"application/simple-message-summary\";"
                },
                {
                    "name": "APPLICATION_SIPC",
                    "type": "variable",
                    "doc": "APPLICATION_SIPC: \"application/sipc\";"
                },
                {
                    "name": "APPLICATION_SLA",
                    "type": "variable",
                    "doc": "APPLICATION_SLA: \"application/sla\";"
                },
                {
                    "name": "APPLICATION_SLATE",
                    "type": "variable",
                    "doc": "APPLICATION_SLATE: \"application/slate\";"
                },
                {
                    "name": "APPLICATION_SMIL",
                    "type": "variable",
                    "doc": "APPLICATION_SMIL: \"application/smil\";"
                },
                {
                    "name": "APPLICATION_SMIL_XML",
                    "type": "variable",
                    "doc": "APPLICATION_SMIL_XML: \"application/smil+xml\";"
                },
                {
                    "name": "APPLICATION_SMPTE336M",
                    "type": "variable",
                    "doc": "APPLICATION_SMPTE336M: \"application/smpte336m\";"
                },
                {
                    "name": "APPLICATION_SOAP_FASTINFOSET",
                    "type": "variable",
                    "doc": "APPLICATION_SOAP_FASTINFOSET: \"application/soap+fastinfoset\";"
                },
                {
                    "name": "APPLICATION_SOAP_XML",
                    "type": "variable",
                    "doc": "APPLICATION_SOAP_XML: \"application/soap+xml\";"
                },
                {
                    "name": "APPLICATION_SPARQL_QUERY",
                    "type": "variable",
                    "doc": "APPLICATION_SPARQL_QUERY: \"application/sparql-query\";"
                },
                {
                    "name": "APPLICATION_SPARQL_RESULTS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_SPARQL_RESULTS_XML: \"application/sparql-results+xml\";"
                },
                {
                    "name": "APPLICATION_SPDX_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_SPDX_JSON: \"application/spdx+json\";"
                },
                {
                    "name": "APPLICATION_SPIRITS_EVENT_XML",
                    "type": "variable",
                    "doc": "APPLICATION_SPIRITS_EVENT_XML: \"application/spirits-event+xml\";"
                },
                {
                    "name": "APPLICATION_SQL",
                    "type": "variable",
                    "doc": "APPLICATION_SQL: \"application/sql\";"
                },
                {
                    "name": "APPLICATION_SRGS",
                    "type": "variable",
                    "doc": "APPLICATION_SRGS: \"application/srgs\";"
                },
                {
                    "name": "APPLICATION_SRGS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_SRGS_XML: \"application/srgs+xml\";"
                },
                {
                    "name": "APPLICATION_SRU_XML",
                    "type": "variable",
                    "doc": "APPLICATION_SRU_XML: \"application/sru+xml\";"
                },
                {
                    "name": "APPLICATION_SSML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_SSML_XML: \"application/ssml+xml\";"
                },
                {
                    "name": "APPLICATION_STIX_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_STIX_JSON: \"application/stix+json\";"
                },
                {
                    "name": "APPLICATION_SVG_XML",
                    "type": "variable",
                    "doc": "APPLICATION_SVG_XML: \"application/svg+xml\";"
                },
                {
                    "name": "APPLICATION_SWID_CBOR",
                    "type": "variable",
                    "doc": "APPLICATION_SWID_CBOR: \"application/swid+cbor\";"
                },
                {
                    "name": "APPLICATION_SWID_XML",
                    "type": "variable",
                    "doc": "APPLICATION_SWID_XML: \"application/swid+xml\";"
                },
                {
                    "name": "APPLICATION_TAMP_APEX_UPDATE",
                    "type": "variable",
                    "doc": "APPLICATION_TAMP_APEX_UPDATE: \"application/tamp-apex-update\";"
                },
                {
                    "name": "APPLICATION_TAMP_APEX_UPDATE_CONFIRM",
                    "type": "variable",
                    "doc": "APPLICATION_TAMP_APEX_UPDATE_CONFIRM: \"application/tamp-apex-update-confirm\";"
                },
                {
                    "name": "APPLICATION_TAMP_COMMUNITY_UPDATE",
                    "type": "variable",
                    "doc": "APPLICATION_TAMP_COMMUNITY_UPDATE: \"application/tamp-community-update\";"
                },
                {
                    "name": "APPLICATION_TAMP_COMMUNITY_UPDATE_CONFIRM",
                    "type": "variable",
                    "doc": "APPLICATION_TAMP_COMMUNITY_UPDATE_CONFIRM: \"application/tamp-community-update-confirm\";"
                },
                {
                    "name": "APPLICATION_TAMP_ERROR",
                    "type": "variable",
                    "doc": "APPLICATION_TAMP_ERROR: \"application/tamp-error\";"
                },
                {
                    "name": "APPLICATION_TAMP_SEQUENCE_ADJUST",
                    "type": "variable",
                    "doc": "APPLICATION_TAMP_SEQUENCE_ADJUST: \"application/tamp-sequence-adjust\";"
                },
                {
                    "name": "APPLICATION_TAMP_SEQUENCE_ADJUST_CONFIRM",
                    "type": "variable",
                    "doc": "APPLICATION_TAMP_SEQUENCE_ADJUST_CONFIRM: \"application/tamp-sequence-adjust-confirm\";"
                },
                {
                    "name": "APPLICATION_TAMP_STATUS_QUERY",
                    "type": "variable",
                    "doc": "APPLICATION_TAMP_STATUS_QUERY: \"application/tamp-status-query\";"
                },
                {
                    "name": "APPLICATION_TAMP_STATUS_RESPONSE",
                    "type": "variable",
                    "doc": "APPLICATION_TAMP_STATUS_RESPONSE: \"application/tamp-status-response\";"
                },
                {
                    "name": "APPLICATION_TAMP_UPDATE",
                    "type": "variable",
                    "doc": "APPLICATION_TAMP_UPDATE: \"application/tamp-update\";"
                },
                {
                    "name": "APPLICATION_TAMP_UPDATE_CONFIRM",
                    "type": "variable",
                    "doc": "APPLICATION_TAMP_UPDATE_CONFIRM: \"application/tamp-update-confirm\";"
                },
                {
                    "name": "APPLICATION_TAXII_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_TAXII_JSON: \"application/taxii+json\";"
                },
                {
                    "name": "APPLICATION_TD_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_TD_JSON: \"application/td+json\";"
                },
                {
                    "name": "APPLICATION_TEI_XML",
                    "type": "variable",
                    "doc": "APPLICATION_TEI_XML: \"application/tei+xml\";"
                },
                {
                    "name": "APPLICATION_TETRA_ISI",
                    "type": "variable",
                    "doc": "APPLICATION_TETRA_ISI: \"application/TETRA_ISI\";"
                },
                {
                    "name": "APPLICATION_THRAUD_XML",
                    "type": "variable",
                    "doc": "APPLICATION_THRAUD_XML: \"application/thraud+xml\";"
                },
                {
                    "name": "APPLICATION_TIMESTAMPED_DATA",
                    "type": "variable",
                    "doc": "APPLICATION_TIMESTAMPED_DATA: \"application/timestamped-data\";"
                },
                {
                    "name": "APPLICATION_TIMESTAMP_QUERY",
                    "type": "variable",
                    "doc": "APPLICATION_TIMESTAMP_QUERY: \"application/timestamp-query\";"
                },
                {
                    "name": "APPLICATION_TIMESTAMP_REPLY",
                    "type": "variable",
                    "doc": "APPLICATION_TIMESTAMP_REPLY: \"application/timestamp-reply\";"
                },
                {
                    "name": "APPLICATION_TLSRPT_GZIP",
                    "type": "variable",
                    "doc": "APPLICATION_TLSRPT_GZIP: \"application/tlsrpt+gzip\";"
                },
                {
                    "name": "APPLICATION_TLSRPT_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_TLSRPT_JSON: \"application/tlsrpt+json\";"
                },
                {
                    "name": "APPLICATION_TM_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_TM_JSON: \"application/tm+json\";"
                },
                {
                    "name": "APPLICATION_TNAUTHLIST",
                    "type": "variable",
                    "doc": "APPLICATION_TNAUTHLIST: \"application/tnauthlist\";"
                },
                {
                    "name": "APPLICATION_TOKEN_INTROSPECTION_JWT",
                    "type": "variable",
                    "doc": "APPLICATION_TOKEN_INTROSPECTION_JWT: \"application/token-introspection+jwt\";"
                },
                {
                    "name": "APPLICATION_TRICKLE_ICE_SDPFRAG",
                    "type": "variable",
                    "doc": "APPLICATION_TRICKLE_ICE_SDPFRAG: \"application/trickle-ice-sdpfrag\";"
                },
                {
                    "name": "APPLICATION_TRIG",
                    "type": "variable",
                    "doc": "APPLICATION_TRIG: \"application/trig\";"
                },
                {
                    "name": "APPLICATION_TTML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_TTML_XML: \"application/ttml+xml\";"
                },
                {
                    "name": "APPLICATION_TVE_TRIGGER",
                    "type": "variable",
                    "doc": "APPLICATION_TVE_TRIGGER: \"application/tve-trigger\";"
                },
                {
                    "name": "APPLICATION_TZIF",
                    "type": "variable",
                    "doc": "APPLICATION_TZIF: \"application/tzif\";"
                },
                {
                    "name": "APPLICATION_TZIF_LEAP",
                    "type": "variable",
                    "doc": "APPLICATION_TZIF_LEAP: \"application/tzif-leap\";"
                },
                {
                    "name": "APPLICATION_ULPFEC",
                    "type": "variable",
                    "doc": "APPLICATION_ULPFEC: \"application/ulpfec\";"
                },
                {
                    "name": "APPLICATION_URC_GRPSHEET_XML",
                    "type": "variable",
                    "doc": "APPLICATION_URC_GRPSHEET_XML: \"application/urc-grpsheet+xml\";"
                },
                {
                    "name": "APPLICATION_URC_RESSHEET_XML",
                    "type": "variable",
                    "doc": "APPLICATION_URC_RESSHEET_XML: \"application/urc-ressheet+xml\";"
                },
                {
                    "name": "APPLICATION_URC_TARGETDESC_XML",
                    "type": "variable",
                    "doc": "APPLICATION_URC_TARGETDESC_XML: \"application/urc-targetdesc+xml\";"
                },
                {
                    "name": "APPLICATION_URC_UISOCKETDESC_XML",
                    "type": "variable",
                    "doc": "APPLICATION_URC_UISOCKETDESC_XML: \"application/urc-uisocketdesc+xml\";"
                },
                {
                    "name": "APPLICATION_VCARD_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VCARD_JSON: \"application/vcard+json\";"
                },
                {
                    "name": "APPLICATION_VCARD_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VCARD_XML: \"application/vcard+xml\";"
                },
                {
                    "name": "APPLICATION_VEMMI",
                    "type": "variable",
                    "doc": "APPLICATION_VEMMI: \"application/vemmi\";"
                },
                {
                    "name": "APPLICATION_VNC_CMCC_DCD_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VNC_CMCC_DCD_XML: \"application/vnc.cmcc.dcd+xml\";"
                },
                {
                    "name": "APPLICATION_VND_1000MINDS_DECISION_MODEL_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_1000MINDS_DECISION_MODEL_XML: \"application/vnd.1000minds.decision-model+xml\";"
                },
                {
                    "name": "APPLICATION_VND_1OB",
                    "type": "variable",
                    "doc": "APPLICATION_VND_1OB: \"application/vnd.1ob\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP2_BCMCSINFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP2_BCMCSINFO_XML: \"application/vnd.3gpp2.bcmcsinfo+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP2_SMS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP2_SMS: \"application/vnd.3gpp2.sms\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP2_TCAP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP2_TCAP: \"application/vnd.3gpp2.tcap\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_5GNAS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_5GNAS: \"application/vnd.3gpp.5gnas\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_5GSA2X",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_5GSA2X: \"application/vnd.3gpp.5gsa2x\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_5GSA2X_LOCAL_SERVICE_INFORMATION",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_5GSA2X_LOCAL_SERVICE_INFORMATION: \"application/vnd.3gpp.5gsa2x-local-service-information\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_ACCESS_TRANSFER_EVENTS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_ACCESS_TRANSFER_EVENTS_XML: \"application/vnd.3gpp.access-transfer-events+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_BSF_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_BSF_XML: \"application/vnd.3gpp.bsf+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_CRS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_CRS_XML: \"application/vnd.3gpp.crs+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_CURRENT_LOCATION_DISCOVERY_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_CURRENT_LOCATION_DISCOVERY_XML: \"application/vnd.3gpp.current-location-discovery+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_GMOP_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_GMOP_XML: \"application/vnd.3gpp.GMOP+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_GTPC",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_GTPC: \"application/vnd.3gpp.gtpc\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_INTERWORKING_DATA",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_INTERWORKING_DATA: \"application/vnd.3gpp.interworking-data\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_LPP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_LPP: \"application/vnd.3gpp.lpp\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCDATA_AFFILIATION_COMMAND_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCDATA_AFFILIATION_COMMAND_XML: \"application/vnd.3gpp.mcdata-affiliation-command+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCDATA_INFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCDATA_INFO_XML: \"application/vnd.3gpp.mcdata-info+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCDATA_MSGSTORE_CTRL_REQUEST_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCDATA_MSGSTORE_CTRL_REQUEST_XML: \"application/vnd.3gpp.mcdata-msgstore-ctrl-request+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCDATA_PAYLOAD",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCDATA_PAYLOAD: \"application/vnd.3gpp.mcdata-payload\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCDATA_REGROUP_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCDATA_REGROUP_XML: \"application/vnd.3gpp.mcdata-regroup+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCDATA_SERVICE_CONFIG_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCDATA_SERVICE_CONFIG_XML: \"application/vnd.3gpp.mcdata-service-config+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCDATA_SIGNALLING",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCDATA_SIGNALLING: \"application/vnd.3gpp.mcdata-signalling\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCDATA_UE_CONFIG_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCDATA_UE_CONFIG_XML: \"application/vnd.3gpp.mcdata-ue-config+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCDATA_USER_PROFILE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCDATA_USER_PROFILE_XML: \"application/vnd.3gpp.mcdata-user-profile+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCPTT_AFFILIATION_COMMAND_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCPTT_AFFILIATION_COMMAND_XML: \"application/vnd.3gpp.mcptt-affiliation-command+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCPTT_FLOOR_REQUEST_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCPTT_FLOOR_REQUEST_XML: \"application/vnd.3gpp.mcptt-floor-request+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCPTT_INFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCPTT_INFO_XML: \"application/vnd.3gpp.mcptt-info+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCPTT_LOCATION_INFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCPTT_LOCATION_INFO_XML: \"application/vnd.3gpp.mcptt-location-info+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCPTT_MBMS_USAGE_INFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCPTT_MBMS_USAGE_INFO_XML: \"application/vnd.3gpp.mcptt-mbms-usage-info+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCPTT_REGROUP_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCPTT_REGROUP_XML: \"application/vnd.3gpp.mcptt-regroup+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCPTT_SERVICE_CONFIG_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCPTT_SERVICE_CONFIG_XML: \"application/vnd.3gpp.mcptt-service-config+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCPTT_SIGNED_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCPTT_SIGNED_XML: \"application/vnd.3gpp.mcptt-signed+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCPTT_UE_CONFIG_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCPTT_UE_CONFIG_XML: \"application/vnd.3gpp.mcptt-ue-config+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCPTT_UE_INIT_CONFIG_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCPTT_UE_INIT_CONFIG_XML: \"application/vnd.3gpp.mcptt-ue-init-config+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCPTT_USER_PROFILE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCPTT_USER_PROFILE_XML: \"application/vnd.3gpp.mcptt-user-profile+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCVIDEO_AFFILIATION_COMMAND_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCVIDEO_AFFILIATION_COMMAND_XML: \"application/vnd.3gpp.mcvideo-affiliation-command+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCVIDEO_AFFILIATION_INFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCVIDEO_AFFILIATION_INFO_XML: \"application/vnd.3gpp.mcvideo-affiliation-info+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCVIDEO_INFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCVIDEO_INFO_XML: \"application/vnd.3gpp.mcvideo-info+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCVIDEO_LOCATION_INFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCVIDEO_LOCATION_INFO_XML: \"application/vnd.3gpp.mcvideo-location-info+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCVIDEO_MBMS_USAGE_INFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCVIDEO_MBMS_USAGE_INFO_XML: \"application/vnd.3gpp.mcvideo-mbms-usage-info+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCVIDEO_REGROUP_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCVIDEO_REGROUP_XML: \"application/vnd.3gpp.mcvideo-regroup+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCVIDEO_SERVICE_CONFIG_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCVIDEO_SERVICE_CONFIG_XML: \"application/vnd.3gpp.mcvideo-service-config+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCVIDEO_TRANSMISSION_REQUEST_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCVIDEO_TRANSMISSION_REQUEST_XML: \"application/vnd.3gpp.mcvideo-transmission-request+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCVIDEO_UE_CONFIG_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCVIDEO_UE_CONFIG_XML: \"application/vnd.3gpp.mcvideo-ue-config+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MCVIDEO_USER_PROFILE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MCVIDEO_USER_PROFILE_XML: \"application/vnd.3gpp.mcvideo-user-profile+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MC_SIGNALLING_EAR",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MC_SIGNALLING_EAR: \"application/vnd.3gpp.mc-signalling-ear\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_MID_CALL_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_MID_CALL_XML: \"application/vnd.3gpp.mid-call+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_NGAP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_NGAP: \"application/vnd.3gpp.ngap\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_PFCP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_PFCP: \"application/vnd.3gpp.pfcp\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_PIC_BW_LARGE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_PIC_BW_LARGE: \"application/vnd.3gpp.pic-bw-large\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_PIC_BW_SMALL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_PIC_BW_SMALL: \"application/vnd.3gpp.pic-bw-small\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_PIC_BW_VAR",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_PIC_BW_VAR: \"application/vnd.3gpp.pic-bw-var\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_PROSE_PC3ACH_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_PROSE_PC3ACH_XML: \"application/vnd.3gpp-prose-pc3ach+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_PROSE_PC3A_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_PROSE_PC3A_XML: \"application/vnd.3gpp-prose-pc3a+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_PROSE_PC3CH_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_PROSE_PC3CH_XML: \"application/vnd.3gpp-prose-pc3ch+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_PROSE_PC8_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_PROSE_PC8_XML: \"application/vnd.3gpp-prose-pc8+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_PROSE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_PROSE_XML: \"application/vnd.3gpp-prose+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_S1AP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_S1AP: \"application/vnd.3gpp.s1ap\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_SEAL_GROUP_DOC_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_SEAL_GROUP_DOC_XML: \"application/vnd.3gpp.seal-group-doc+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_SEAL_INFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_SEAL_INFO_XML: \"application/vnd.3gpp.seal-info+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_SEAL_LOCATION_INFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_SEAL_LOCATION_INFO_XML: \"application/vnd.3gpp.seal-location-info+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_SEAL_MBMS_USAGE_INFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_SEAL_MBMS_USAGE_INFO_XML: \"application/vnd.3gpp.seal-mbms-usage-info+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_SEAL_NETWORK_QOS_MANAGEMENT_INFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_SEAL_NETWORK_QOS_MANAGEMENT_INFO_XML: \"application/vnd.3gpp.seal-network-QoS-management-info+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_SEAL_UE_CONFIG_INFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_SEAL_UE_CONFIG_INFO_XML: \"application/vnd.3gpp.seal-ue-config-info+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_SEAL_UNICAST_INFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_SEAL_UNICAST_INFO_XML: \"application/vnd.3gpp.seal-unicast-info+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_SEAL_USER_PROFILE_INFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_SEAL_USER_PROFILE_INFO_XML: \"application/vnd.3gpp.seal-user-profile-info+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_SMS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_SMS: \"application/vnd.3gpp.sms\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_SMS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_SMS_XML: \"application/vnd.3gpp.sms+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_SRVCC_EXT_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_SRVCC_EXT_XML: \"application/vnd.3gpp.srvcc-ext+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_SRVCC_INFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_SRVCC_INFO_XML: \"application/vnd.3gpp.SRVCC-info+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_STATE_AND_EVENT_INFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_STATE_AND_EVENT_INFO_XML: \"application/vnd.3gpp.state-and-event-info+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_USSD_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_USSD_XML: \"application/vnd.3gpp.ussd+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_V2X",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_V2X: \"application/vnd.3gpp.v2x\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_V2X_LOCAL_SERVICE_INFORMATION",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_V2X_LOCAL_SERVICE_INFORMATION: \"application/vnd.3gpp-v2x-local-service-information\";"
                },
                {
                    "name": "APPLICATION_VND_3GPP_VAE_INFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3GPP_VAE_INFO_XML: \"application/vnd.3gpp.vae-info+xml\";"
                },
                {
                    "name": "APPLICATION_VND_3LIGHTSSOFTWARE_IMAGESCAL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3LIGHTSSOFTWARE_IMAGESCAL: \"application/vnd.3lightssoftware.imagescal\";"
                },
                {
                    "name": "APPLICATION_VND_3M_POST_IT_NOTES",
                    "type": "variable",
                    "doc": "APPLICATION_VND_3M_POST_IT_NOTES: \"application/vnd.3M.Post-it-Notes\";"
                },
                {
                    "name": "APPLICATION_VND_ACCPAC_SIMPLY_ASO",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ACCPAC_SIMPLY_ASO: \"application/vnd.accpac.simply.aso\";"
                },
                {
                    "name": "APPLICATION_VND_ACCPAC_SIMPLY_IMP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ACCPAC_SIMPLY_IMP: \"application/vnd.accpac.simply.imp\";"
                },
                {
                    "name": "APPLICATION_VND_ACM_ADDRESSXFER_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ACM_ADDRESSXFER_JSON: \"application/vnd.acm.addressxfer+json\";"
                },
                {
                    "name": "APPLICATION_VND_ACM_CHATBOT_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ACM_CHATBOT_JSON: \"application/vnd.acm.chatbot+json\";"
                },
                {
                    "name": "APPLICATION_VND_ACUCOBOL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ACUCOBOL: \"application/vnd.acucobol\";"
                },
                {
                    "name": "APPLICATION_VND_ACUCORP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ACUCORP: \"application/vnd.acucorp\";"
                },
                {
                    "name": "APPLICATION_VND_ADOBE_FLASH_MOVIE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ADOBE_FLASH_MOVIE: \"application/vnd.adobe.flash.movie\";"
                },
                {
                    "name": "APPLICATION_VND_ADOBE_FORMSCENTRAL_FCDT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ADOBE_FORMSCENTRAL_FCDT: \"application/vnd.adobe.formscentral.fcdt\";"
                },
                {
                    "name": "APPLICATION_VND_ADOBE_FXP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ADOBE_FXP: \"application/vnd.adobe.fxp\";"
                },
                {
                    "name": "APPLICATION_VND_ADOBE_PARTIAL_UPLOAD",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ADOBE_PARTIAL_UPLOAD: \"application/vnd.adobe.partial-upload\";"
                },
                {
                    "name": "APPLICATION_VND_ADOBE_XDP_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ADOBE_XDP_XML: \"application/vnd.adobe.xdp+xml\";"
                },
                {
                    "name": "APPLICATION_VND_AETHER_IMP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_AETHER_IMP: \"application/vnd.aether.imp\";"
                },
                {
                    "name": "APPLICATION_VND_AFPC_AFPLINEDATA",
                    "type": "variable",
                    "doc": "APPLICATION_VND_AFPC_AFPLINEDATA: \"application/vnd.afpc.afplinedata\";"
                },
                {
                    "name": "APPLICATION_VND_AFPC_AFPLINEDATA_PAGEDEF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_AFPC_AFPLINEDATA_PAGEDEF: \"application/vnd.afpc.afplinedata-pagedef\";"
                },
                {
                    "name": "APPLICATION_VND_AFPC_CMOCA_CMRESOURCE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_AFPC_CMOCA_CMRESOURCE: \"application/vnd.afpc.cmoca-cmresource\";"
                },
                {
                    "name": "APPLICATION_VND_AFPC_FOCA_CHARSET",
                    "type": "variable",
                    "doc": "APPLICATION_VND_AFPC_FOCA_CHARSET: \"application/vnd.afpc.foca-charset\";"
                },
                {
                    "name": "APPLICATION_VND_AFPC_FOCA_CODEDFONT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_AFPC_FOCA_CODEDFONT: \"application/vnd.afpc.foca-codedfont\";"
                },
                {
                    "name": "APPLICATION_VND_AFPC_FOCA_CODEPAGE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_AFPC_FOCA_CODEPAGE: \"application/vnd.afpc.foca-codepage\";"
                },
                {
                    "name": "APPLICATION_VND_AFPC_MODCA",
                    "type": "variable",
                    "doc": "APPLICATION_VND_AFPC_MODCA: \"application/vnd.afpc.modca\";"
                },
                {
                    "name": "APPLICATION_VND_AFPC_MODCA_CMTABLE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_AFPC_MODCA_CMTABLE: \"application/vnd.afpc.modca-cmtable\";"
                },
                {
                    "name": "APPLICATION_VND_AFPC_MODCA_FORMDEF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_AFPC_MODCA_FORMDEF: \"application/vnd.afpc.modca-formdef\";"
                },
                {
                    "name": "APPLICATION_VND_AFPC_MODCA_MEDIUMMAP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_AFPC_MODCA_MEDIUMMAP: \"application/vnd.afpc.modca-mediummap\";"
                },
                {
                    "name": "APPLICATION_VND_AFPC_MODCA_OBJECTCONTAINER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_AFPC_MODCA_OBJECTCONTAINER: \"application/vnd.afpc.modca-objectcontainer\";"
                },
                {
                    "name": "APPLICATION_VND_AFPC_MODCA_OVERLAY",
                    "type": "variable",
                    "doc": "APPLICATION_VND_AFPC_MODCA_OVERLAY: \"application/vnd.afpc.modca-overlay\";"
                },
                {
                    "name": "APPLICATION_VND_AFPC_MODCA_PAGESEGMENT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_AFPC_MODCA_PAGESEGMENT: \"application/vnd.afpc.modca-pagesegment\";"
                },
                {
                    "name": "APPLICATION_VND_AGE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_AGE: \"application/vnd.age\";"
                },
                {
                    "name": "APPLICATION_VND_AHEAD_SPACE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_AHEAD_SPACE: \"application/vnd.ahead.space\";"
                },
                {
                    "name": "APPLICATION_VND_AH_BARCODE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_AH_BARCODE: \"application/vnd.ah-barcode\";"
                },
                {
                    "name": "APPLICATION_VND_AIRZIP_FILESECURE_AZF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_AIRZIP_FILESECURE_AZF: \"application/vnd.airzip.filesecure.azf\";"
                },
                {
                    "name": "APPLICATION_VND_AIRZIP_FILESECURE_AZS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_AIRZIP_FILESECURE_AZS: \"application/vnd.airzip.filesecure.azs\";"
                },
                {
                    "name": "APPLICATION_VND_AMADEUS_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_AMADEUS_JSON: \"application/vnd.amadeus+json\";"
                },
                {
                    "name": "APPLICATION_VND_AMAZON_MOBI8_EBOOK",
                    "type": "variable",
                    "doc": "APPLICATION_VND_AMAZON_MOBI8_EBOOK: \"application/vnd.amazon.mobi8-ebook\";"
                },
                {
                    "name": "APPLICATION_VND_AMERICANDYNAMICS_ACC",
                    "type": "variable",
                    "doc": "APPLICATION_VND_AMERICANDYNAMICS_ACC: \"application/vnd.americandynamics.acc\";"
                },
                {
                    "name": "APPLICATION_VND_AMIGA_AMI",
                    "type": "variable",
                    "doc": "APPLICATION_VND_AMIGA_AMI: \"application/vnd.amiga.ami\";"
                },
                {
                    "name": "APPLICATION_VND_AMUNDSEN_MAZE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_AMUNDSEN_MAZE_XML: \"application/vnd.amundsen.maze+xml\";"
                },
                {
                    "name": "APPLICATION_VND_ANDROID_OTA",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ANDROID_OTA: \"application/vnd.android.ota\";"
                },
                {
                    "name": "APPLICATION_VND_ANDROID_PACKAGE_ARCHIVE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ANDROID_PACKAGE_ARCHIVE: \"application/vnd.android.package-archive\";"
                },
                {
                    "name": "APPLICATION_VND_ANKI",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ANKI: \"application/vnd.anki\";"
                },
                {
                    "name": "APPLICATION_VND_ANSER_WEB_CERTIFICATE_ISSUE_INITIATION",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ANSER_WEB_CERTIFICATE_ISSUE_INITIATION: \"application/vnd.anser-web-certificate-issue-initiation\";"
                },
                {
                    "name": "APPLICATION_VND_ANSER_WEB_FUNDS_TRANSFER_INITIATION",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ANSER_WEB_FUNDS_TRANSFER_INITIATION: \"application/vnd.anser-web-funds-transfer-initiation\";"
                },
                {
                    "name": "APPLICATION_VND_ANTIX_GAME_COMPONENT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ANTIX_GAME_COMPONENT: \"application/vnd.antix.game-component\";"
                },
                {
                    "name": "APPLICATION_VND_APACHE_ARROW_FILE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_APACHE_ARROW_FILE: \"application/vnd.apache.arrow.file\";"
                },
                {
                    "name": "APPLICATION_VND_APACHE_ARROW_STREAM",
                    "type": "variable",
                    "doc": "APPLICATION_VND_APACHE_ARROW_STREAM: \"application/vnd.apache.arrow.stream\";"
                },
                {
                    "name": "APPLICATION_VND_APACHE_PARQUET",
                    "type": "variable",
                    "doc": "APPLICATION_VND_APACHE_PARQUET: \"application/vnd.apache.parquet\";"
                },
                {
                    "name": "APPLICATION_VND_APACHE_THRIFT_BINARY",
                    "type": "variable",
                    "doc": "APPLICATION_VND_APACHE_THRIFT_BINARY: \"application/vnd.apache.thrift.binary\";"
                },
                {
                    "name": "APPLICATION_VND_APACHE_THRIFT_COMPACT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_APACHE_THRIFT_COMPACT: \"application/vnd.apache.thrift.compact\";"
                },
                {
                    "name": "APPLICATION_VND_APACHE_THRIFT_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_APACHE_THRIFT_JSON: \"application/vnd.apache.thrift.json\";"
                },
                {
                    "name": "APPLICATION_VND_APEXLANG",
                    "type": "variable",
                    "doc": "APPLICATION_VND_APEXLANG: \"application/vnd.apexlang\";"
                },
                {
                    "name": "APPLICATION_VND_API_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_API_JSON: \"application/vnd.api+json\";"
                },
                {
                    "name": "APPLICATION_VND_APLEXTOR_WARRP_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_APLEXTOR_WARRP_JSON: \"application/vnd.aplextor.warrp+json\";"
                },
                {
                    "name": "APPLICATION_VND_APOTHEKENDE_RESERVATION_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_APOTHEKENDE_RESERVATION_JSON: \"application/vnd.apothekende.reservation+json\";"
                },
                {
                    "name": "APPLICATION_VND_APPLE_INSTALLER_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_APPLE_INSTALLER_XML: \"application/vnd.apple.installer+xml\";"
                },
                {
                    "name": "APPLICATION_VND_APPLE_KEYNOTE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_APPLE_KEYNOTE: \"application/vnd.apple.keynote\";"
                },
                {
                    "name": "APPLICATION_VND_APPLE_MPEGURL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_APPLE_MPEGURL: \"application/vnd.apple.mpegurl\";"
                },
                {
                    "name": "APPLICATION_VND_APPLE_NUMBERS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_APPLE_NUMBERS: \"application/vnd.apple.numbers\";"
                },
                {
                    "name": "APPLICATION_VND_APPLE_PAGES",
                    "type": "variable",
                    "doc": "APPLICATION_VND_APPLE_PAGES: \"application/vnd.apple.pages\";"
                },
                {
                    "name": "APPLICATION_VND_ARASTRA_SWI",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ARASTRA_SWI: \"application/vnd.arastra.swi\";"
                },
                {
                    "name": "APPLICATION_VND_ARISTANETWORKS_SWI",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ARISTANETWORKS_SWI: \"application/vnd.aristanetworks.swi\";"
                },
                {
                    "name": "APPLICATION_VND_ARTISAN_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ARTISAN_JSON: \"application/vnd.artisan+json\";"
                },
                {
                    "name": "APPLICATION_VND_ARTSQUARE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ARTSQUARE: \"application/vnd.artsquare\";"
                },
                {
                    "name": "APPLICATION_VND_ASTRAEA_SOFTWARE_IOTA",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ASTRAEA_SOFTWARE_IOTA: \"application/vnd.astraea-software.iota\";"
                },
                {
                    "name": "APPLICATION_VND_AUDIOGRAPH",
                    "type": "variable",
                    "doc": "APPLICATION_VND_AUDIOGRAPH: \"application/vnd.audiograph\";"
                },
                {
                    "name": "APPLICATION_VND_AUTOPACKAGE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_AUTOPACKAGE: \"application/vnd.autopackage\";"
                },
                {
                    "name": "APPLICATION_VND_AVALON_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_AVALON_JSON: \"application/vnd.avalon+json\";"
                },
                {
                    "name": "APPLICATION_VND_AVISTAR_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_AVISTAR_XML: \"application/vnd.avistar+xml\";"
                },
                {
                    "name": "APPLICATION_VND_BALSAMIQ_BMML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_BALSAMIQ_BMML_XML: \"application/vnd.balsamiq.bmml+xml\";"
                },
                {
                    "name": "APPLICATION_VND_BALSAMIQ_BMPR",
                    "type": "variable",
                    "doc": "APPLICATION_VND_BALSAMIQ_BMPR: \"application/vnd.balsamiq.bmpr\";"
                },
                {
                    "name": "APPLICATION_VND_BANANA_ACCOUNTING",
                    "type": "variable",
                    "doc": "APPLICATION_VND_BANANA_ACCOUNTING: \"application/vnd.banana-accounting\";"
                },
                {
                    "name": "APPLICATION_VND_BBF_USP_ERROR",
                    "type": "variable",
                    "doc": "APPLICATION_VND_BBF_USP_ERROR: \"application/vnd.bbf.usp.error\";"
                },
                {
                    "name": "APPLICATION_VND_BBF_USP_MSG",
                    "type": "variable",
                    "doc": "APPLICATION_VND_BBF_USP_MSG: \"application/vnd.bbf.usp.msg\";"
                },
                {
                    "name": "APPLICATION_VND_BBF_USP_MSG_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_BBF_USP_MSG_JSON: \"application/vnd.bbf.usp.msg+json\";"
                },
                {
                    "name": "APPLICATION_VND_BEKITZUR_STECH_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_BEKITZUR_STECH_JSON: \"application/vnd.bekitzur-stech+json\";"
                },
                {
                    "name": "APPLICATION_VND_BELIGHTSOFT_LHZD_ZIP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_BELIGHTSOFT_LHZD_ZIP: \"application/vnd.belightsoft.lhzd+zip\";"
                },
                {
                    "name": "APPLICATION_VND_BELIGHTSOFT_LHZL_ZIP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_BELIGHTSOFT_LHZL_ZIP: \"application/vnd.belightsoft.lhzl+zip\";"
                },
                {
                    "name": "APPLICATION_VND_BINT_MED_CONTENT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_BINT_MED_CONTENT: \"application/vnd.bint.med-content\";"
                },
                {
                    "name": "APPLICATION_VND_BIOPAX_RDF_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_BIOPAX_RDF_XML: \"application/vnd.biopax.rdf+xml\";"
                },
                {
                    "name": "APPLICATION_VND_BLINK_IDB_VALUE_WRAPPER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_BLINK_IDB_VALUE_WRAPPER: \"application/vnd.blink-idb-value-wrapper\";"
                },
                {
                    "name": "APPLICATION_VND_BLUEICE_MULTIPASS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_BLUEICE_MULTIPASS: \"application/vnd.blueice.multipass\";"
                },
                {
                    "name": "APPLICATION_VND_BLUETOOTH_EP_OOB",
                    "type": "variable",
                    "doc": "APPLICATION_VND_BLUETOOTH_EP_OOB: \"application/vnd.bluetooth.ep.oob\";"
                },
                {
                    "name": "APPLICATION_VND_BLUETOOTH_LE_OOB",
                    "type": "variable",
                    "doc": "APPLICATION_VND_BLUETOOTH_LE_OOB: \"application/vnd.bluetooth.le.oob\";"
                },
                {
                    "name": "APPLICATION_VND_BMI",
                    "type": "variable",
                    "doc": "APPLICATION_VND_BMI: \"application/vnd.bmi\";"
                },
                {
                    "name": "APPLICATION_VND_BPF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_BPF: \"application/vnd.bpf\";"
                },
                {
                    "name": "APPLICATION_VND_BPF3",
                    "type": "variable",
                    "doc": "APPLICATION_VND_BPF3: \"application/vnd.bpf3\";"
                },
                {
                    "name": "APPLICATION_VND_BUSINESSOBJECTS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_BUSINESSOBJECTS: \"application/vnd.businessobjects\";"
                },
                {
                    "name": "APPLICATION_VND_BYU_UAPI_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_BYU_UAPI_JSON: \"application/vnd.byu.uapi+json\";"
                },
                {
                    "name": "APPLICATION_VND_BZIP3",
                    "type": "variable",
                    "doc": "APPLICATION_VND_BZIP3: \"application/vnd.bzip3\";"
                },
                {
                    "name": "APPLICATION_VND_C3VOC_SCHEDULE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_C3VOC_SCHEDULE_XML: \"application/vnd.c3voc.schedule+xml\";"
                },
                {
                    "name": "APPLICATION_VND_CAB_JSCRIPT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CAB_JSCRIPT: \"application/vnd.cab-jscript\";"
                },
                {
                    "name": "APPLICATION_VND_CANON_CPDL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CANON_CPDL: \"application/vnd.canon-cpdl\";"
                },
                {
                    "name": "APPLICATION_VND_CANON_LIPS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CANON_LIPS: \"application/vnd.canon-lips\";"
                },
                {
                    "name": "APPLICATION_VND_CAPASYSTEMS_PG_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CAPASYSTEMS_PG_JSON: \"application/vnd.capasystems-pg+json\";"
                },
                {
                    "name": "APPLICATION_VND_CENDIO_THINLINC_CLIENTCONF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CENDIO_THINLINC_CLIENTCONF: \"application/vnd.cendio.thinlinc.clientconf\";"
                },
                {
                    "name": "APPLICATION_VND_CENTURY_SYSTEMS_TCP_STREAM",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CENTURY_SYSTEMS_TCP_STREAM: \"application/vnd.century-systems.tcp_stream\";"
                },
                {
                    "name": "APPLICATION_VND_CHEMDRAW_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CHEMDRAW_XML: \"application/vnd.chemdraw+xml\";"
                },
                {
                    "name": "APPLICATION_VND_CHESS_PGN",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CHESS_PGN: \"application/vnd.chess-pgn\";"
                },
                {
                    "name": "APPLICATION_VND_CHIPNUTS_KARAOKE_MMD",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CHIPNUTS_KARAOKE_MMD: \"application/vnd.chipnuts.karaoke-mmd\";"
                },
                {
                    "name": "APPLICATION_VND_CIEDI",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CIEDI: \"application/vnd.ciedi\";"
                },
                {
                    "name": "APPLICATION_VND_CINDERELLA",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CINDERELLA: \"application/vnd.cinderella\";"
                },
                {
                    "name": "APPLICATION_VND_CIRPACK_ISDN_EXT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CIRPACK_ISDN_EXT: \"application/vnd.cirpack.isdn-ext\";"
                },
                {
                    "name": "APPLICATION_VND_CITATIONSTYLES_STYLE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CITATIONSTYLES_STYLE_XML: \"application/vnd.citationstyles.style+xml\";"
                },
                {
                    "name": "APPLICATION_VND_CLAYMORE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CLAYMORE: \"application/vnd.claymore\";"
                },
                {
                    "name": "APPLICATION_VND_CLOANTO_RP9",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CLOANTO_RP9: \"application/vnd.cloanto.rp9\";"
                },
                {
                    "name": "APPLICATION_VND_CLONK_C4GROUP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CLONK_C4GROUP: \"application/vnd.clonk.c4group\";"
                },
                {
                    "name": "APPLICATION_VND_CLUETRUST_CARTOMOBILE_CONFIG",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CLUETRUST_CARTOMOBILE_CONFIG: \"application/vnd.cluetrust.cartomobile-config\";"
                },
                {
                    "name": "APPLICATION_VND_CLUETRUST_CARTOMOBILE_CONFIG_PKG",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CLUETRUST_CARTOMOBILE_CONFIG_PKG: \"application/vnd.cluetrust.cartomobile-config-pkg\";"
                },
                {
                    "name": "APPLICATION_VND_CMCC_BOMBING_WBXML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CMCC_BOMBING_WBXML: \"application/vnd.cmcc.bombing+wbxml\";"
                },
                {
                    "name": "APPLICATION_VND_CMCC_SETTING_WBXML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CMCC_SETTING_WBXML: \"application/vnd.cmcc.setting+wbxml\";"
                },
                {
                    "name": "APPLICATION_VND_CNCF_HELM_CHART_CONTENT_V1_TAR_GZIP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CNCF_HELM_CHART_CONTENT_V1_TAR_GZIP: \"application/vnd.cncf.helm.chart.content.v1.tar+gzip\";"
                },
                {
                    "name": "APPLICATION_VND_CNCF_HELM_CHART_PROVENANCE_V1_PROV",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CNCF_HELM_CHART_PROVENANCE_V1_PROV: \"application/vnd.cncf.helm.chart.provenance.v1.prov\";"
                },
                {
                    "name": "APPLICATION_VND_CNCF_HELM_CONFIG_V1_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CNCF_HELM_CONFIG_V1_JSON: \"application/vnd.cncf.helm.config.v1+json\";"
                },
                {
                    "name": "APPLICATION_VND_COFFEESCRIPT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_COFFEESCRIPT: \"application/vnd.coffeescript\";"
                },
                {
                    "name": "APPLICATION_VND_COLLABIO_XODOCUMENTS_DOCUMENT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_COLLABIO_XODOCUMENTS_DOCUMENT: \"application/vnd.collabio.xodocuments.document\";"
                },
                {
                    "name": "APPLICATION_VND_COLLABIO_XODOCUMENTS_DOCUMENT_TEMPLATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_COLLABIO_XODOCUMENTS_DOCUMENT_TEMPLATE: \"application/vnd.collabio.xodocuments.document-template\";"
                },
                {
                    "name": "APPLICATION_VND_COLLABIO_XODOCUMENTS_PRESENTATION",
                    "type": "variable",
                    "doc": "APPLICATION_VND_COLLABIO_XODOCUMENTS_PRESENTATION: \"application/vnd.collabio.xodocuments.presentation\";"
                },
                {
                    "name": "APPLICATION_VND_COLLABIO_XODOCUMENTS_PRESENTATION_TEMPLATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_COLLABIO_XODOCUMENTS_PRESENTATION_TEMPLATE: \"application/vnd.collabio.xodocuments.presentation-template\";"
                },
                {
                    "name": "APPLICATION_VND_COLLABIO_XODOCUMENTS_SPREADSHEET",
                    "type": "variable",
                    "doc": "APPLICATION_VND_COLLABIO_XODOCUMENTS_SPREADSHEET: \"application/vnd.collabio.xodocuments.spreadsheet\";"
                },
                {
                    "name": "APPLICATION_VND_COLLABIO_XODOCUMENTS_SPREADSHEET_TEMPLATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_COLLABIO_XODOCUMENTS_SPREADSHEET_TEMPLATE: \"application/vnd.collabio.xodocuments.spreadsheet-template\";"
                },
                {
                    "name": "APPLICATION_VND_COLLECTION_DOC_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_COLLECTION_DOC_JSON: \"application/vnd.collection.doc+json\";"
                },
                {
                    "name": "APPLICATION_VND_COLLECTION_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_COLLECTION_JSON: \"application/vnd.collection+json\";"
                },
                {
                    "name": "APPLICATION_VND_COLLECTION_NEXT_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_COLLECTION_NEXT_JSON: \"application/vnd.collection.next+json\";"
                },
                {
                    "name": "APPLICATION_VND_COMICBOOK_RAR",
                    "type": "variable",
                    "doc": "APPLICATION_VND_COMICBOOK_RAR: \"application/vnd.comicbook-rar\";"
                },
                {
                    "name": "APPLICATION_VND_COMICBOOK_ZIP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_COMICBOOK_ZIP: \"application/vnd.comicbook+zip\";"
                },
                {
                    "name": "APPLICATION_VND_COMMERCE_BATTELLE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_COMMERCE_BATTELLE: \"application/vnd.commerce-battelle\";"
                },
                {
                    "name": "APPLICATION_VND_COMMONSPACE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_COMMONSPACE: \"application/vnd.commonspace\";"
                },
                {
                    "name": "APPLICATION_VND_COMSOCALLER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_COMSOCALLER: \"application/vnd.comsocaller\";"
                },
                {
                    "name": "APPLICATION_VND_CONTACT_CMSG",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CONTACT_CMSG: \"application/vnd.contact.cmsg\";"
                },
                {
                    "name": "APPLICATION_VND_COREOS_IGNITION_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_COREOS_IGNITION_JSON: \"application/vnd.coreos.ignition+json\";"
                },
                {
                    "name": "APPLICATION_VND_COSMOCALLER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_COSMOCALLER: \"application/vnd.cosmocaller\";"
                },
                {
                    "name": "APPLICATION_VND_CRICK_CLICKER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CRICK_CLICKER: \"application/vnd.crick.clicker\";"
                },
                {
                    "name": "APPLICATION_VND_CRICK_CLICKER_KEYBOARD",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CRICK_CLICKER_KEYBOARD: \"application/vnd.crick.clicker.keyboard\";"
                },
                {
                    "name": "APPLICATION_VND_CRICK_CLICKER_PALETTE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CRICK_CLICKER_PALETTE: \"application/vnd.crick.clicker.palette\";"
                },
                {
                    "name": "APPLICATION_VND_CRICK_CLICKER_TEMPLATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CRICK_CLICKER_TEMPLATE: \"application/vnd.crick.clicker.template\";"
                },
                {
                    "name": "APPLICATION_VND_CRICK_CLICKER_WORDBANK",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CRICK_CLICKER_WORDBANK: \"application/vnd.crick.clicker.wordbank\";"
                },
                {
                    "name": "APPLICATION_VND_CRITICALTOOLS_WBS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CRITICALTOOLS_WBS_XML: \"application/vnd.criticaltools.wbs+xml\";"
                },
                {
                    "name": "APPLICATION_VND_CRYPTII_PIPE_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CRYPTII_PIPE_JSON: \"application/vnd.cryptii.pipe+json\";"
                },
                {
                    "name": "APPLICATION_VND_CRYPTOMATOR_ENCRYPTED",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CRYPTOMATOR_ENCRYPTED: \"application/vnd.cryptomator.encrypted\";"
                },
                {
                    "name": "APPLICATION_VND_CRYPTOMATOR_VAULT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CRYPTOMATOR_VAULT: \"application/vnd.cryptomator.vault\";"
                },
                {
                    "name": "APPLICATION_VND_CRYPTO_SHADE_FILE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CRYPTO_SHADE_FILE: \"application/vnd.crypto-shade-file\";"
                },
                {
                    "name": "APPLICATION_VND_CTCT_WS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CTCT_WS_XML: \"application/vnd.ctct.ws+xml\";"
                },
                {
                    "name": "APPLICATION_VND_CTC_POSML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CTC_POSML: \"application/vnd.ctc-posml\";"
                },
                {
                    "name": "APPLICATION_VND_CUPS_PDF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CUPS_PDF: \"application/vnd.cups-pdf\";"
                },
                {
                    "name": "APPLICATION_VND_CUPS_POSTSCRIPT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CUPS_POSTSCRIPT: \"application/vnd.cups-postscript\";"
                },
                {
                    "name": "APPLICATION_VND_CUPS_PPD",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CUPS_PPD: \"application/vnd.cups-ppd\";"
                },
                {
                    "name": "APPLICATION_VND_CUPS_RASTER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CUPS_RASTER: \"application/vnd.cups-raster\";"
                },
                {
                    "name": "APPLICATION_VND_CUPS_RAW",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CUPS_RAW: \"application/vnd.cups-raw\";"
                },
                {
                    "name": "APPLICATION_VND_CURL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CURL: \"application/vnd.curl\";"
                },
                {
                    "name": "APPLICATION_VND_CYAN_DEAN_ROOT_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CYAN_DEAN_ROOT_XML: \"application/vnd.cyan.dean.root+xml\";"
                },
                {
                    "name": "APPLICATION_VND_CYBANK",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CYBANK: \"application/vnd.cybank\";"
                },
                {
                    "name": "APPLICATION_VND_CYCLONEDX_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CYCLONEDX_JSON: \"application/vnd.cyclonedx+json\";"
                },
                {
                    "name": "APPLICATION_VND_CYCLONEDX_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_CYCLONEDX_XML: \"application/vnd.cyclonedx+xml\";"
                },
                {
                    "name": "APPLICATION_VND_D2L_COURSEPACKAGE1P0_ZIP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_D2L_COURSEPACKAGE1P0_ZIP: \"application/vnd.d2l.coursepackage1p0+zip\";"
                },
                {
                    "name": "APPLICATION_VND_D3M_DATASET",
                    "type": "variable",
                    "doc": "APPLICATION_VND_D3M_DATASET: \"application/vnd.d3m-dataset\";"
                },
                {
                    "name": "APPLICATION_VND_D3M_PROBLEM",
                    "type": "variable",
                    "doc": "APPLICATION_VND_D3M_PROBLEM: \"application/vnd.d3m-problem\";"
                },
                {
                    "name": "APPLICATION_VND_DART",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DART: \"application/vnd.dart\";"
                },
                {
                    "name": "APPLICATION_VND_DATALOG",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DATALOG: \"application/vnd.datalog\";"
                },
                {
                    "name": "APPLICATION_VND_DATAPACKAGE_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DATAPACKAGE_JSON: \"application/vnd.datapackage+json\";"
                },
                {
                    "name": "APPLICATION_VND_DATARESOURCE_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DATARESOURCE_JSON: \"application/vnd.dataresource+json\";"
                },
                {
                    "name": "APPLICATION_VND_DATA_VISION_RDZ",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DATA_VISION_RDZ: \"application/vnd.data-vision.rdz\";"
                },
                {
                    "name": "APPLICATION_VND_DBF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DBF: \"application/vnd.dbf\";"
                },
                {
                    "name": "APPLICATION_VND_DEBIAN_BINARY_PACKAGE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DEBIAN_BINARY_PACKAGE: \"application/vnd.debian.binary-package\";"
                },
                {
                    "name": "APPLICATION_VND_DECE_DATA",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DECE_DATA: \"application/vnd.dece.data\";"
                },
                {
                    "name": "APPLICATION_VND_DECE_TTML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DECE_TTML_XML: \"application/vnd.dece.ttml+xml\";"
                },
                {
                    "name": "APPLICATION_VND_DECE_UNSPECIFIED",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DECE_UNSPECIFIED: \"application/vnd.dece.unspecified\";"
                },
                {
                    "name": "APPLICATION_VND_DECE_ZIP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DECE_ZIP: \"application/vnd.dece.zip\";"
                },
                {
                    "name": "APPLICATION_VND_DENOVO_FCSELAYOUT_LINK",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DENOVO_FCSELAYOUT_LINK: \"application/vnd.denovo.fcselayout-link\";"
                },
                {
                    "name": "APPLICATION_VND_DESMUME_MOVIE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DESMUME_MOVIE: \"application/vnd.desmume.movie\";"
                },
                {
                    "name": "APPLICATION_VND_DIR_BI_PLATE_DL_NOSUFFIX",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DIR_BI_PLATE_DL_NOSUFFIX: \"application/vnd.dir-bi.plate-dl-nosuffix\";"
                },
                {
                    "name": "APPLICATION_VND_DM_DELEGATION_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DM_DELEGATION_XML: \"application/vnd.dm.delegation+xml\";"
                },
                {
                    "name": "APPLICATION_VND_DNA",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DNA: \"application/vnd.dna\";"
                },
                {
                    "name": "APPLICATION_VND_DOCOMO_PF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DOCOMO_PF: \"application/vnd.docomo.pf\";"
                },
                {
                    "name": "APPLICATION_VND_DOCOMO_PF2",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DOCOMO_PF2: \"application/vnd.docomo.pf2\";"
                },
                {
                    "name": "APPLICATION_VND_DOCOMO_UB",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DOCOMO_UB: \"application/vnd.docomo.ub\";"
                },
                {
                    "name": "APPLICATION_VND_DOCUMENT_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DOCUMENT_JSON: \"application/vnd.document+json\";"
                },
                {
                    "name": "APPLICATION_VND_DOLBY_MOBILE_1",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DOLBY_MOBILE_1: \"application/vnd.dolby.mobile.1\";"
                },
                {
                    "name": "APPLICATION_VND_DOLBY_MOBILE_2",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DOLBY_MOBILE_2: \"application/vnd.dolby.mobile.2\";"
                },
                {
                    "name": "APPLICATION_VND_DOREMIR_SCORECLOUD_BINARY_DOCUMENT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DOREMIR_SCORECLOUD_BINARY_DOCUMENT: \"application/vnd.doremir.scorecloud-binary-document\";"
                },
                {
                    "name": "APPLICATION_VND_DPGRAPH",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DPGRAPH: \"application/vnd.dpgraph\";"
                },
                {
                    "name": "APPLICATION_VND_DREAMFACTORY",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DREAMFACTORY: \"application/vnd.dreamfactory\";"
                },
                {
                    "name": "APPLICATION_VND_DRIVE_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DRIVE_JSON: \"application/vnd.drive+json\";"
                },
                {
                    "name": "APPLICATION_VND_DTG_LOCAL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DTG_LOCAL: \"application/vnd.dtg.local\";"
                },
                {
                    "name": "APPLICATION_VND_DTG_LOCAL_FLASH",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DTG_LOCAL_FLASH: \"application/vnd.dtg.local.flash\";"
                },
                {
                    "name": "APPLICATION_VND_DTG_LOCAL_HTML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DTG_LOCAL_HTML: \"application/vnd.dtg.local.html\";"
                },
                {
                    "name": "APPLICATION_VND_DVB_AIT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DVB_AIT: \"application/vnd.dvb.ait\";"
                },
                {
                    "name": "APPLICATION_VND_DVB_DVBISL_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DVB_DVBISL_XML: \"application/vnd.dvb.dvbisl+xml\";"
                },
                {
                    "name": "APPLICATION_VND_DVB_DVBJ",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DVB_DVBJ: \"application/vnd.dvb.dvbj\";"
                },
                {
                    "name": "APPLICATION_VND_DVB_ESGCONTAINER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DVB_ESGCONTAINER: \"application/vnd.dvb.esgcontainer\";"
                },
                {
                    "name": "APPLICATION_VND_DVB_IPDCDFTNOTIFACCESS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DVB_IPDCDFTNOTIFACCESS: \"application/vnd.dvb.ipdcdftnotifaccess\";"
                },
                {
                    "name": "APPLICATION_VND_DVB_IPDCESGACCESS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DVB_IPDCESGACCESS: \"application/vnd.dvb.ipdcesgaccess\";"
                },
                {
                    "name": "APPLICATION_VND_DVB_IPDCESGACCESS2",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DVB_IPDCESGACCESS2: \"application/vnd.dvb.ipdcesgaccess2\";"
                },
                {
                    "name": "APPLICATION_VND_DVB_IPDCESGPDD",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DVB_IPDCESGPDD: \"application/vnd.dvb.ipdcesgpdd\";"
                },
                {
                    "name": "APPLICATION_VND_DVB_IPDCROAMING",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DVB_IPDCROAMING: \"application/vnd.dvb.ipdcroaming\";"
                },
                {
                    "name": "APPLICATION_VND_DVB_IPTV_ALFEC_BASE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DVB_IPTV_ALFEC_BASE: \"application/vnd.dvb.iptv.alfec-base\";"
                },
                {
                    "name": "APPLICATION_VND_DVB_IPTV_ALFEC_ENHANCEMENT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DVB_IPTV_ALFEC_ENHANCEMENT: \"application/vnd.dvb.iptv.alfec-enhancement\";"
                },
                {
                    "name": "APPLICATION_VND_DVB_NOTIF_AGGREGATE_ROOT_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DVB_NOTIF_AGGREGATE_ROOT_XML: \"application/vnd.dvb.notif-aggregate-root+xml\";"
                },
                {
                    "name": "APPLICATION_VND_DVB_NOTIF_CONTAINER_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DVB_NOTIF_CONTAINER_XML: \"application/vnd.dvb.notif-container+xml\";"
                },
                {
                    "name": "APPLICATION_VND_DVB_NOTIF_GENERIC_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DVB_NOTIF_GENERIC_XML: \"application/vnd.dvb.notif-generic+xml\";"
                },
                {
                    "name": "APPLICATION_VND_DVB_NOTIF_IA_MSGLIST_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DVB_NOTIF_IA_MSGLIST_XML: \"application/vnd.dvb.notif-ia-msglist+xml\";"
                },
                {
                    "name": "APPLICATION_VND_DVB_NOTIF_IA_REGISTRATION_REQUEST_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DVB_NOTIF_IA_REGISTRATION_REQUEST_XML: \"application/vnd.dvb.notif-ia-registration-request+xml\";"
                },
                {
                    "name": "APPLICATION_VND_DVB_NOTIF_IA_REGISTRATION_RESPONSE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DVB_NOTIF_IA_REGISTRATION_RESPONSE_XML: \"application/vnd.dvb.notif-ia-registration-response+xml\";"
                },
                {
                    "name": "APPLICATION_VND_DVB_NOTIF_INIT_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DVB_NOTIF_INIT_XML: \"application/vnd.dvb.notif-init+xml\";"
                },
                {
                    "name": "APPLICATION_VND_DVB_PFR",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DVB_PFR: \"application/vnd.dvb.pfr\";"
                },
                {
                    "name": "APPLICATION_VND_DVB_SERVICE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DVB_SERVICE: \"application/vnd.dvb.service\";"
                },
                {
                    "name": "APPLICATION_VND_DXR",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DXR: \"application/vnd.dxr\";"
                },
                {
                    "name": "APPLICATION_VND_DYNAGEO",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DYNAGEO: \"application/vnd.dynageo\";"
                },
                {
                    "name": "APPLICATION_VND_DZR",
                    "type": "variable",
                    "doc": "APPLICATION_VND_DZR: \"application/vnd.dzr\";"
                },
                {
                    "name": "APPLICATION_VND_EASYKARAOKE_CDGDOWNLOAD",
                    "type": "variable",
                    "doc": "APPLICATION_VND_EASYKARAOKE_CDGDOWNLOAD: \"application/vnd.easykaraoke.cdgdownload\";"
                },
                {
                    "name": "APPLICATION_VND_ECDIS_UPDATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ECDIS_UPDATE: \"application/vnd.ecdis-update\";"
                },
                {
                    "name": "APPLICATION_VND_ECIP_RLP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ECIP_RLP: \"application/vnd.ecip.rlp\";"
                },
                {
                    "name": "APPLICATION_VND_ECLIPSE_DITTO_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ECLIPSE_DITTO_JSON: \"application/vnd.eclipse.ditto+json\";"
                },
                {
                    "name": "APPLICATION_VND_ECOWIN_CHART",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ECOWIN_CHART: \"application/vnd.ecowin.chart\";"
                },
                {
                    "name": "APPLICATION_VND_ECOWIN_FILEREQUEST",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ECOWIN_FILEREQUEST: \"application/vnd.ecowin.filerequest\";"
                },
                {
                    "name": "APPLICATION_VND_ECOWIN_FILEUPDATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ECOWIN_FILEUPDATE: \"application/vnd.ecowin.fileupdate\";"
                },
                {
                    "name": "APPLICATION_VND_ECOWIN_SERIES",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ECOWIN_SERIES: \"application/vnd.ecowin.series\";"
                },
                {
                    "name": "APPLICATION_VND_ECOWIN_SERIESREQUEST",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ECOWIN_SERIESREQUEST: \"application/vnd.ecowin.seriesrequest\";"
                },
                {
                    "name": "APPLICATION_VND_ECOWIN_SERIESUPDATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ECOWIN_SERIESUPDATE: \"application/vnd.ecowin.seriesupdate\";"
                },
                {
                    "name": "APPLICATION_VND_EFI_IMG",
                    "type": "variable",
                    "doc": "APPLICATION_VND_EFI_IMG: \"application/vnd.efi.img\";"
                },
                {
                    "name": "APPLICATION_VND_EFI_ISO",
                    "type": "variable",
                    "doc": "APPLICATION_VND_EFI_ISO: \"application/vnd.efi.iso\";"
                },
                {
                    "name": "APPLICATION_VND_ELN_ZIP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ELN_ZIP: \"application/vnd.eln+zip\";"
                },
                {
                    "name": "APPLICATION_VND_EMCLIENT_ACCESSREQUEST_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_EMCLIENT_ACCESSREQUEST_XML: \"application/vnd.emclient.accessrequest+xml\";"
                },
                {
                    "name": "APPLICATION_VND_ENLIVEN",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ENLIVEN: \"application/vnd.enliven\";"
                },
                {
                    "name": "APPLICATION_VND_ENPHASE_ENVOY",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ENPHASE_ENVOY: \"application/vnd.enphase.envoy\";"
                },
                {
                    "name": "APPLICATION_VND_EPRINTS_DATA_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_EPRINTS_DATA_XML: \"application/vnd.eprints.data+xml\";"
                },
                {
                    "name": "APPLICATION_VND_EPSON_ESF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_EPSON_ESF: \"application/vnd.epson.esf\";"
                },
                {
                    "name": "APPLICATION_VND_EPSON_MSF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_EPSON_MSF: \"application/vnd.epson.msf\";"
                },
                {
                    "name": "APPLICATION_VND_EPSON_QUICKANIME",
                    "type": "variable",
                    "doc": "APPLICATION_VND_EPSON_QUICKANIME: \"application/vnd.epson.quickanime\";"
                },
                {
                    "name": "APPLICATION_VND_EPSON_SALT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_EPSON_SALT: \"application/vnd.epson.salt\";"
                },
                {
                    "name": "APPLICATION_VND_EPSON_SSF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_EPSON_SSF: \"application/vnd.epson.ssf\";"
                },
                {
                    "name": "APPLICATION_VND_ERICSSON_QUICKCALL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ERICSSON_QUICKCALL: \"application/vnd.ericsson.quickcall\";"
                },
                {
                    "name": "APPLICATION_VND_EROFS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_EROFS: \"application/vnd.erofs\";"
                },
                {
                    "name": "APPLICATION_VND_ESPASS_ESPASS_ZIP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ESPASS_ESPASS_ZIP: \"application/vnd.espass-espass+zip\";"
                },
                {
                    "name": "APPLICATION_VND_ESZIGNO3_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ESZIGNO3_XML: \"application/vnd.eszigno3+xml\";"
                },
                {
                    "name": "APPLICATION_VND_ETSI_AOC_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ETSI_AOC_XML: \"application/vnd.etsi.aoc+xml\";"
                },
                {
                    "name": "APPLICATION_VND_ETSI_ASIC_E_ZIP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ETSI_ASIC_E_ZIP: \"application/vnd.etsi.asic-e+zip\";"
                },
                {
                    "name": "APPLICATION_VND_ETSI_ASIC_S_ZIP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ETSI_ASIC_S_ZIP: \"application/vnd.etsi.asic-s+zip\";"
                },
                {
                    "name": "APPLICATION_VND_ETSI_CUG_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ETSI_CUG_XML: \"application/vnd.etsi.cug+xml\";"
                },
                {
                    "name": "APPLICATION_VND_ETSI_IPTVCOMMAND_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ETSI_IPTVCOMMAND_XML: \"application/vnd.etsi.iptvcommand+xml\";"
                },
                {
                    "name": "APPLICATION_VND_ETSI_IPTVDISCOVERY_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ETSI_IPTVDISCOVERY_XML: \"application/vnd.etsi.iptvdiscovery+xml\";"
                },
                {
                    "name": "APPLICATION_VND_ETSI_IPTVPROFILE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ETSI_IPTVPROFILE_XML: \"application/vnd.etsi.iptvprofile+xml\";"
                },
                {
                    "name": "APPLICATION_VND_ETSI_IPTVSAD_BC_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ETSI_IPTVSAD_BC_XML: \"application/vnd.etsi.iptvsad-bc+xml\";"
                },
                {
                    "name": "APPLICATION_VND_ETSI_IPTVSAD_COD_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ETSI_IPTVSAD_COD_XML: \"application/vnd.etsi.iptvsad-cod+xml\";"
                },
                {
                    "name": "APPLICATION_VND_ETSI_IPTVSAD_NPVR_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ETSI_IPTVSAD_NPVR_XML: \"application/vnd.etsi.iptvsad-npvr+xml\";"
                },
                {
                    "name": "APPLICATION_VND_ETSI_IPTVSERVICE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ETSI_IPTVSERVICE_XML: \"application/vnd.etsi.iptvservice+xml\";"
                },
                {
                    "name": "APPLICATION_VND_ETSI_IPTVSYNC_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ETSI_IPTVSYNC_XML: \"application/vnd.etsi.iptvsync+xml\";"
                },
                {
                    "name": "APPLICATION_VND_ETSI_IPTVUEPROFILE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ETSI_IPTVUEPROFILE_XML: \"application/vnd.etsi.iptvueprofile+xml\";"
                },
                {
                    "name": "APPLICATION_VND_ETSI_MCID_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ETSI_MCID_XML: \"application/vnd.etsi.mcid+xml\";"
                },
                {
                    "name": "APPLICATION_VND_ETSI_MHEG5",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ETSI_MHEG5: \"application/vnd.etsi.mheg5\";"
                },
                {
                    "name": "APPLICATION_VND_ETSI_OVERLOAD_CONTROL_POLICY_DATASET_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ETSI_OVERLOAD_CONTROL_POLICY_DATASET_XML: \"application/vnd.etsi.overload-control-policy-dataset+xml\";"
                },
                {
                    "name": "APPLICATION_VND_ETSI_PSTN_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ETSI_PSTN_XML: \"application/vnd.etsi.pstn+xml\";"
                },
                {
                    "name": "APPLICATION_VND_ETSI_SCI_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ETSI_SCI_XML: \"application/vnd.etsi.sci+xml\";"
                },
                {
                    "name": "APPLICATION_VND_ETSI_SIMSERVS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ETSI_SIMSERVS_XML: \"application/vnd.etsi.simservs+xml\";"
                },
                {
                    "name": "APPLICATION_VND_ETSI_TIMESTAMP_TOKEN",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ETSI_TIMESTAMP_TOKEN: \"application/vnd.etsi.timestamp-token\";"
                },
                {
                    "name": "APPLICATION_VND_ETSI_TSL_DER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ETSI_TSL_DER: \"application/vnd.etsi.tsl.der\";"
                },
                {
                    "name": "APPLICATION_VND_ETSI_TSL_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ETSI_TSL_XML: \"application/vnd.etsi.tsl+xml\";"
                },
                {
                    "name": "APPLICATION_VND_EUDORA_DATA",
                    "type": "variable",
                    "doc": "APPLICATION_VND_EUDORA_DATA: \"application/vnd.eudora.data\";"
                },
                {
                    "name": "APPLICATION_VND_EU_KASPARIAN_CAR_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_EU_KASPARIAN_CAR_JSON: \"application/vnd.eu.kasparian.car+json\";"
                },
                {
                    "name": "APPLICATION_VND_EVOLV_ECIG_PROFILE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_EVOLV_ECIG_PROFILE: \"application/vnd.evolv.ecig.profile\";"
                },
                {
                    "name": "APPLICATION_VND_EVOLV_ECIG_SETTINGS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_EVOLV_ECIG_SETTINGS: \"application/vnd.evolv.ecig.settings\";"
                },
                {
                    "name": "APPLICATION_VND_EVOLV_ECIG_THEME",
                    "type": "variable",
                    "doc": "APPLICATION_VND_EVOLV_ECIG_THEME: \"application/vnd.evolv.ecig.theme\";"
                },
                {
                    "name": "APPLICATION_VND_EXSTREAM_EMPOWER_ZIP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_EXSTREAM_EMPOWER_ZIP: \"application/vnd.exstream-empower+zip\";"
                },
                {
                    "name": "APPLICATION_VND_EXSTREAM_PACKAGE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_EXSTREAM_PACKAGE: \"application/vnd.exstream-package\";"
                },
                {
                    "name": "APPLICATION_VND_EZPIX_ALBUM",
                    "type": "variable",
                    "doc": "APPLICATION_VND_EZPIX_ALBUM: \"application/vnd.ezpix-album\";"
                },
                {
                    "name": "APPLICATION_VND_EZPIX_PACKAGE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_EZPIX_PACKAGE: \"application/vnd.ezpix-package\";"
                },
                {
                    "name": "APPLICATION_VND_FAMILYSEARCH_GEDCOM_ZIP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FAMILYSEARCH_GEDCOM_ZIP: \"application/vnd.familysearch.gedcom+zip\";"
                },
                {
                    "name": "APPLICATION_VND_FASTCOPY_DISK_IMAGE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FASTCOPY_DISK_IMAGE: \"application/vnd.fastcopy-disk-image\";"
                },
                {
                    "name": "APPLICATION_VND_FDF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FDF: \"application/vnd.fdf\";"
                },
                {
                    "name": "APPLICATION_VND_FDSN_MSEED",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FDSN_MSEED: \"application/vnd.fdsn.mseed\";"
                },
                {
                    "name": "APPLICATION_VND_FDSN_SEED",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FDSN_SEED: \"application/vnd.fdsn.seed\";"
                },
                {
                    "name": "APPLICATION_VND_FFSNS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FFSNS: \"application/vnd.ffsns\";"
                },
                {
                    "name": "APPLICATION_VND_FICLAB_FLB_ZIP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FICLAB_FLB_ZIP: \"application/vnd.ficlab.flb+zip\";"
                },
                {
                    "name": "APPLICATION_VND_FILMIT_ZFC",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FILMIT_ZFC: \"application/vnd.filmit.zfc\";"
                },
                {
                    "name": "APPLICATION_VND_FINTS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FINTS: \"application/vnd.fints\";"
                },
                {
                    "name": "APPLICATION_VND_FIREMONKEYS_CLOUDCELL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FIREMONKEYS_CLOUDCELL: \"application/vnd.firemonkeys.cloudcell\";"
                },
                {
                    "name": "APPLICATION_VND_FLOGRAPHIT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FLOGRAPHIT: \"application/vnd.FloGraphIt\";"
                },
                {
                    "name": "APPLICATION_VND_FLUXTIME_CLIP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FLUXTIME_CLIP: \"application/vnd.fluxtime.clip\";"
                },
                {
                    "name": "APPLICATION_VND_FONT_FONTFORGE_SFD",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FONT_FONTFORGE_SFD: \"application/vnd.font-fontforge-sfd\";"
                },
                {
                    "name": "APPLICATION_VND_FRAMEMAKER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FRAMEMAKER: \"application/vnd.framemaker\";"
                },
                {
                    "name": "APPLICATION_VND_FREELOG_COMIC",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FREELOG_COMIC: \"application/vnd.freelog.comic\";"
                },
                {
                    "name": "APPLICATION_VND_FROGANS_FNC",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FROGANS_FNC: \"application/vnd.frogans.fnc\";"
                },
                {
                    "name": "APPLICATION_VND_FROGANS_LTF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FROGANS_LTF: \"application/vnd.frogans.ltf\";"
                },
                {
                    "name": "APPLICATION_VND_FSC_WEBLAUNCH",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FSC_WEBLAUNCH: \"application/vnd.fsc.weblaunch\";"
                },
                {
                    "name": "APPLICATION_VND_FUJIFILM_FB_DOCUWORKS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FUJIFILM_FB_DOCUWORKS: \"application/vnd.fujifilm.fb.docuworks\";"
                },
                {
                    "name": "APPLICATION_VND_FUJIFILM_FB_DOCUWORKS_BINDER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FUJIFILM_FB_DOCUWORKS_BINDER: \"application/vnd.fujifilm.fb.docuworks.binder\";"
                },
                {
                    "name": "APPLICATION_VND_FUJIFILM_FB_DOCUWORKS_CONTAINER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FUJIFILM_FB_DOCUWORKS_CONTAINER: \"application/vnd.fujifilm.fb.docuworks.container\";"
                },
                {
                    "name": "APPLICATION_VND_FUJIFILM_FB_JFI_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FUJIFILM_FB_JFI_XML: \"application/vnd.fujifilm.fb.jfi+xml\";"
                },
                {
                    "name": "APPLICATION_VND_FUJITSU_OASYS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FUJITSU_OASYS: \"application/vnd.fujitsu.oasys\";"
                },
                {
                    "name": "APPLICATION_VND_FUJITSU_OASYS2",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FUJITSU_OASYS2: \"application/vnd.fujitsu.oasys2\";"
                },
                {
                    "name": "APPLICATION_VND_FUJITSU_OASYS3",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FUJITSU_OASYS3: \"application/vnd.fujitsu.oasys3\";"
                },
                {
                    "name": "APPLICATION_VND_FUJITSU_OASYSGP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FUJITSU_OASYSGP: \"application/vnd.fujitsu.oasysgp\";"
                },
                {
                    "name": "APPLICATION_VND_FUJITSU_OASYSPRS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FUJITSU_OASYSPRS: \"application/vnd.fujitsu.oasysprs\";"
                },
                {
                    "name": "APPLICATION_VND_FUJIXEROX_ART4",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FUJIXEROX_ART4: \"application/vnd.fujixerox.ART4\";"
                },
                {
                    "name": "APPLICATION_VND_FUJIXEROX_ART_EX",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FUJIXEROX_ART_EX: \"application/vnd.fujixerox.ART-EX\";"
                },
                {
                    "name": "APPLICATION_VND_FUJIXEROX_DDD",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FUJIXEROX_DDD: \"application/vnd.fujixerox.ddd\";"
                },
                {
                    "name": "APPLICATION_VND_FUJIXEROX_DOCUWORKS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FUJIXEROX_DOCUWORKS: \"application/vnd.fujixerox.docuworks\";"
                },
                {
                    "name": "APPLICATION_VND_FUJIXEROX_DOCUWORKS_BINDER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FUJIXEROX_DOCUWORKS_BINDER: \"application/vnd.fujixerox.docuworks.binder\";"
                },
                {
                    "name": "APPLICATION_VND_FUJIXEROX_DOCUWORKS_CONTAINER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FUJIXEROX_DOCUWORKS_CONTAINER: \"application/vnd.fujixerox.docuworks.container\";"
                },
                {
                    "name": "APPLICATION_VND_FUJIXEROX_HBPL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FUJIXEROX_HBPL: \"application/vnd.fujixerox.HBPL\";"
                },
                {
                    "name": "APPLICATION_VND_FUTOIN_CBOR",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FUTOIN_CBOR: \"application/vnd.futoin+cbor\";"
                },
                {
                    "name": "APPLICATION_VND_FUTOIN_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FUTOIN_JSON: \"application/vnd.futoin+json\";"
                },
                {
                    "name": "APPLICATION_VND_FUT_MISNET",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FUT_MISNET: \"application/vnd.fut-misnet\";"
                },
                {
                    "name": "APPLICATION_VND_FUZZYSHEET",
                    "type": "variable",
                    "doc": "APPLICATION_VND_FUZZYSHEET: \"application/vnd.fuzzysheet\";"
                },
                {
                    "name": "APPLICATION_VND_F_SECURE_MOBILE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_F_SECURE_MOBILE: \"application/vnd.f-secure.mobile\";"
                },
                {
                    "name": "APPLICATION_VND_GENOMATIX_TUXEDO",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GENOMATIX_TUXEDO: \"application/vnd.genomatix.tuxedo\";"
                },
                {
                    "name": "APPLICATION_VND_GENOZIP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GENOZIP: \"application/vnd.genozip\";"
                },
                {
                    "name": "APPLICATION_VND_GENTICS_GRD_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GENTICS_GRD_JSON: \"application/vnd.gentics.grd+json\";"
                },
                {
                    "name": "APPLICATION_VND_GENTOO_CATMETADATA_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GENTOO_CATMETADATA_XML: \"application/vnd.gentoo.catmetadata+xml\";"
                },
                {
                    "name": "APPLICATION_VND_GENTOO_EBUILD",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GENTOO_EBUILD: \"application/vnd.gentoo.ebuild\";"
                },
                {
                    "name": "APPLICATION_VND_GENTOO_ECLASS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GENTOO_ECLASS: \"application/vnd.gentoo.eclass\";"
                },
                {
                    "name": "APPLICATION_VND_GENTOO_GPKG",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GENTOO_GPKG: \"application/vnd.gentoo.gpkg\";"
                },
                {
                    "name": "APPLICATION_VND_GENTOO_MANIFEST",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GENTOO_MANIFEST: \"application/vnd.gentoo.manifest\";"
                },
                {
                    "name": "APPLICATION_VND_GENTOO_PKGMETADATA_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GENTOO_PKGMETADATA_XML: \"application/vnd.gentoo.pkgmetadata+xml\";"
                },
                {
                    "name": "APPLICATION_VND_GENTOO_XPAK",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GENTOO_XPAK: \"application/vnd.gentoo.xpak\";"
                },
                {
                    "name": "APPLICATION_VND_GEOCUBE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GEOCUBE_XML: \"application/vnd.geocube+xml\";"
                },
                {
                    "name": "APPLICATION_VND_GEOGEBRA_FILE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GEOGEBRA_FILE: \"application/vnd.geogebra.file\";"
                },
                {
                    "name": "APPLICATION_VND_GEOGEBRA_SLIDES",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GEOGEBRA_SLIDES: \"application/vnd.geogebra.slides\";"
                },
                {
                    "name": "APPLICATION_VND_GEOGEBRA_TOOL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GEOGEBRA_TOOL: \"application/vnd.geogebra.tool\";"
                },
                {
                    "name": "APPLICATION_VND_GEOMETRY_EXPLORER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GEOMETRY_EXPLORER: \"application/vnd.geometry-explorer\";"
                },
                {
                    "name": "APPLICATION_VND_GEONEXT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GEONEXT: \"application/vnd.geonext\";"
                },
                {
                    "name": "APPLICATION_VND_GEOPLAN",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GEOPLAN: \"application/vnd.geoplan\";"
                },
                {
                    "name": "APPLICATION_VND_GEOSPACE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GEOSPACE: \"application/vnd.geospace\";"
                },
                {
                    "name": "APPLICATION_VND_GEO_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GEO_JSON: \"application/vnd.geo+json\";"
                },
                {
                    "name": "APPLICATION_VND_GERBER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GERBER: \"application/vnd.gerber\";"
                },
                {
                    "name": "APPLICATION_VND_GLOBALPLATFORM_CARD_CONTENT_MGT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GLOBALPLATFORM_CARD_CONTENT_MGT: \"application/vnd.globalplatform.card-content-mgt\";"
                },
                {
                    "name": "APPLICATION_VND_GLOBALPLATFORM_CARD_CONTENT_MGT_RESPONSE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GLOBALPLATFORM_CARD_CONTENT_MGT_RESPONSE: \"application/vnd.globalplatform.card-content-mgt-response\";"
                },
                {
                    "name": "APPLICATION_VND_GMX",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GMX: \"application/vnd.gmx\";"
                },
                {
                    "name": "APPLICATION_VND_GNU_TALER_EXCHANGE_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GNU_TALER_EXCHANGE_JSON: \"application/vnd.gnu.taler.exchange+json\";"
                },
                {
                    "name": "APPLICATION_VND_GNU_TALER_MERCHANT_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GNU_TALER_MERCHANT_JSON: \"application/vnd.gnu.taler.merchant+json\";"
                },
                {
                    "name": "APPLICATION_VND_GOOGLE_APPS_DOCUMENT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GOOGLE_APPS_DOCUMENT: \"application/vnd.google-apps.document\";"
                },
                {
                    "name": "APPLICATION_VND_GOOGLE_APPS_DRAWING",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GOOGLE_APPS_DRAWING: \"application/vnd.google-apps.drawing\";"
                },
                {
                    "name": "APPLICATION_VND_GOOGLE_APPS_PRESENTATION",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GOOGLE_APPS_PRESENTATION: \"application/vnd.google-apps.presentation\";"
                },
                {
                    "name": "APPLICATION_VND_GOOGLE_APPS_SPREADSHEET",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GOOGLE_APPS_SPREADSHEET: \"application/vnd.google-apps.spreadsheet\";"
                },
                {
                    "name": "APPLICATION_VND_GOOGLE_EARTH_KML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GOOGLE_EARTH_KML_XML: \"application/vnd.google-earth.kml+xml\";"
                },
                {
                    "name": "APPLICATION_VND_GOOGLE_EARTH_KMZ",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GOOGLE_EARTH_KMZ: \"application/vnd.google-earth.kmz\";"
                },
                {
                    "name": "APPLICATION_VND_GOV_SK_E_FORM_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GOV_SK_E_FORM_XML: \"application/vnd.gov.sk.e-form+xml\";"
                },
                {
                    "name": "APPLICATION_VND_GOV_SK_E_FORM_ZIP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GOV_SK_E_FORM_ZIP: \"application/vnd.gov.sk.e-form+zip\";"
                },
                {
                    "name": "APPLICATION_VND_GOV_SK_XMLDATACONTAINER_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GOV_SK_XMLDATACONTAINER_XML: \"application/vnd.gov.sk.xmldatacontainer+xml\";"
                },
                {
                    "name": "APPLICATION_VND_GPXSEE_MAP_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GPXSEE_MAP_XML: \"application/vnd.gpxsee.map+xml\";"
                },
                {
                    "name": "APPLICATION_VND_GRAFEQ",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GRAFEQ: \"application/vnd.grafeq\";"
                },
                {
                    "name": "APPLICATION_VND_GRIDMP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GRIDMP: \"application/vnd.gridmp\";"
                },
                {
                    "name": "APPLICATION_VND_GROOVE_ACCOUNT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GROOVE_ACCOUNT: \"application/vnd.groove-account\";"
                },
                {
                    "name": "APPLICATION_VND_GROOVE_HELP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GROOVE_HELP: \"application/vnd.groove-help\";"
                },
                {
                    "name": "APPLICATION_VND_GROOVE_IDENTITY_MESSAGE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GROOVE_IDENTITY_MESSAGE: \"application/vnd.groove-identity-message\";"
                },
                {
                    "name": "APPLICATION_VND_GROOVE_INJECTOR",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GROOVE_INJECTOR: \"application/vnd.groove-injector\";"
                },
                {
                    "name": "APPLICATION_VND_GROOVE_TOOL_MESSAGE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GROOVE_TOOL_MESSAGE: \"application/vnd.groove-tool-message\";"
                },
                {
                    "name": "APPLICATION_VND_GROOVE_TOOL_TEMPLATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GROOVE_TOOL_TEMPLATE: \"application/vnd.groove-tool-template\";"
                },
                {
                    "name": "APPLICATION_VND_GROOVE_VCARD",
                    "type": "variable",
                    "doc": "APPLICATION_VND_GROOVE_VCARD: \"application/vnd.groove-vcard\";"
                },
                {
                    "name": "APPLICATION_VND_HAL_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_HAL_JSON: \"application/vnd.hal+json\";"
                },
                {
                    "name": "APPLICATION_VND_HAL_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_HAL_XML: \"application/vnd.hal+xml\";"
                },
                {
                    "name": "APPLICATION_VND_HANDHELD_ENTERTAINMENT_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_HANDHELD_ENTERTAINMENT_XML: \"application/vnd.HandHeld-Entertainment+xml\";"
                },
                {
                    "name": "APPLICATION_VND_HBCI",
                    "type": "variable",
                    "doc": "APPLICATION_VND_HBCI: \"application/vnd.hbci\";"
                },
                {
                    "name": "APPLICATION_VND_HCL_BIREPORTS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_HCL_BIREPORTS: \"application/vnd.hcl-bireports\";"
                },
                {
                    "name": "APPLICATION_VND_HC_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_HC_JSON: \"application/vnd.hc+json\";"
                },
                {
                    "name": "APPLICATION_VND_HDT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_HDT: \"application/vnd.hdt\";"
                },
                {
                    "name": "APPLICATION_VND_HEROKU_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_HEROKU_JSON: \"application/vnd.heroku+json\";"
                },
                {
                    "name": "APPLICATION_VND_HHE_LESSON_PLAYER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_HHE_LESSON_PLAYER: \"application/vnd.hhe.lesson-player\";"
                },
                {
                    "name": "APPLICATION_VND_HP_HPGL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_HP_HPGL: \"application/vnd.hp-HPGL\";"
                },
                {
                    "name": "APPLICATION_VND_HP_HPID",
                    "type": "variable",
                    "doc": "APPLICATION_VND_HP_HPID: \"application/vnd.hp-hpid\";"
                },
                {
                    "name": "APPLICATION_VND_HP_HPS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_HP_HPS: \"application/vnd.hp-hps\";"
                },
                {
                    "name": "APPLICATION_VND_HP_JLYT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_HP_JLYT: \"application/vnd.hp-jlyt\";"
                },
                {
                    "name": "APPLICATION_VND_HP_PCL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_HP_PCL: \"application/vnd.hp-PCL\";"
                },
                {
                    "name": "APPLICATION_VND_HP_PCLXL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_HP_PCLXL: \"application/vnd.hp-PCLXL\";"
                },
                {
                    "name": "APPLICATION_VND_HSL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_HSL: \"application/vnd.hsl\";"
                },
                {
                    "name": "APPLICATION_VND_HTTPHONE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_HTTPHONE: \"application/vnd.httphone\";"
                },
                {
                    "name": "APPLICATION_VND_HYDROSTATIX_SOF_DATA",
                    "type": "variable",
                    "doc": "APPLICATION_VND_HYDROSTATIX_SOF_DATA: \"application/vnd.hydrostatix.sof-data\";"
                },
                {
                    "name": "APPLICATION_VND_HYPERDRIVE_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_HYPERDRIVE_JSON: \"application/vnd.hyperdrive+json\";"
                },
                {
                    "name": "APPLICATION_VND_HYPER_ITEM_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_HYPER_ITEM_JSON: \"application/vnd.hyper-item+json\";"
                },
                {
                    "name": "APPLICATION_VND_HYPER_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_HYPER_JSON: \"application/vnd.hyper+json\";"
                },
                {
                    "name": "APPLICATION_VND_HZN_3D_CROSSWORD",
                    "type": "variable",
                    "doc": "APPLICATION_VND_HZN_3D_CROSSWORD: \"application/vnd.hzn-3d-crossword\";"
                },
                {
                    "name": "APPLICATION_VND_IBM_AFPLINEDATA",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IBM_AFPLINEDATA: \"application/vnd.ibm.afplinedata\";"
                },
                {
                    "name": "APPLICATION_VND_IBM_ELECTRONIC_MEDIA",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IBM_ELECTRONIC_MEDIA: \"application/vnd.ibm.electronic-media\";"
                },
                {
                    "name": "APPLICATION_VND_IBM_MINIPAY",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IBM_MINIPAY: \"application/vnd.ibm.MiniPay\";"
                },
                {
                    "name": "APPLICATION_VND_IBM_MODCAP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IBM_MODCAP: \"application/vnd.ibm.modcap\";"
                },
                {
                    "name": "APPLICATION_VND_IBM_RIGHTS_MANAGEMENT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IBM_RIGHTS_MANAGEMENT: \"application/vnd.ibm.rights-management\";"
                },
                {
                    "name": "APPLICATION_VND_IBM_SECURE_CONTAINER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IBM_SECURE_CONTAINER: \"application/vnd.ibm.secure-container\";"
                },
                {
                    "name": "APPLICATION_VND_ICCPROFILE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ICCPROFILE: \"application/vnd.iccprofile\";"
                },
                {
                    "name": "APPLICATION_VND_IEEE_1905",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IEEE_1905: \"application/vnd.ieee.1905\";"
                },
                {
                    "name": "APPLICATION_VND_IGLOADER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IGLOADER: \"application/vnd.igloader\";"
                },
                {
                    "name": "APPLICATION_VND_IMAGEMETER_FOLDER_ZIP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IMAGEMETER_FOLDER_ZIP: \"application/vnd.imagemeter.folder+zip\";"
                },
                {
                    "name": "APPLICATION_VND_IMAGEMETER_IMAGE_ZIP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IMAGEMETER_IMAGE_ZIP: \"application/vnd.imagemeter.image+zip\";"
                },
                {
                    "name": "APPLICATION_VND_IMMERVISION_IVP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IMMERVISION_IVP: \"application/vnd.immervision-ivp\";"
                },
                {
                    "name": "APPLICATION_VND_IMMERVISION_IVU",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IMMERVISION_IVU: \"application/vnd.immervision-ivu\";"
                },
                {
                    "name": "APPLICATION_VND_IMS_IMSCCV1P1",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IMS_IMSCCV1P1: \"application/vnd.ims.imsccv1p1\";"
                },
                {
                    "name": "APPLICATION_VND_IMS_IMSCCV1P2",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IMS_IMSCCV1P2: \"application/vnd.ims.imsccv1p2\";"
                },
                {
                    "name": "APPLICATION_VND_IMS_IMSCCV1P3",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IMS_IMSCCV1P3: \"application/vnd.ims.imsccv1p3\";"
                },
                {
                    "name": "APPLICATION_VND_IMS_LIS_V2_RESULT_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IMS_LIS_V2_RESULT_JSON: \"application/vnd.ims.lis.v2.result+json\";"
                },
                {
                    "name": "APPLICATION_VND_IMS_LTI_V2_TOOLCONSUMERPROFILE_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IMS_LTI_V2_TOOLCONSUMERPROFILE_JSON: \"application/vnd.ims.lti.v2.toolconsumerprofile+json\";"
                },
                {
                    "name": "APPLICATION_VND_IMS_LTI_V2_TOOLPROXY_ID_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IMS_LTI_V2_TOOLPROXY_ID_JSON: \"application/vnd.ims.lti.v2.toolproxy.id+json\";"
                },
                {
                    "name": "APPLICATION_VND_IMS_LTI_V2_TOOLPROXY_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IMS_LTI_V2_TOOLPROXY_JSON: \"application/vnd.ims.lti.v2.toolproxy+json\";"
                },
                {
                    "name": "APPLICATION_VND_IMS_LTI_V2_TOOLSETTINGS_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IMS_LTI_V2_TOOLSETTINGS_JSON: \"application/vnd.ims.lti.v2.toolsettings+json\";"
                },
                {
                    "name": "APPLICATION_VND_IMS_LTI_V2_TOOLSETTINGS_SIMPLE_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IMS_LTI_V2_TOOLSETTINGS_SIMPLE_JSON: \"application/vnd.ims.lti.v2.toolsettings.simple+json\";"
                },
                {
                    "name": "APPLICATION_VND_INFORMEDCONTROL_RMS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_INFORMEDCONTROL_RMS_XML: \"application/vnd.informedcontrol.rms+xml\";"
                },
                {
                    "name": "APPLICATION_VND_INFORMIX_VISIONARY",
                    "type": "variable",
                    "doc": "APPLICATION_VND_INFORMIX_VISIONARY: \"application/vnd.informix-visionary\";"
                },
                {
                    "name": "APPLICATION_VND_INFOTECH_PROJECT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_INFOTECH_PROJECT: \"application/vnd.infotech.project\";"
                },
                {
                    "name": "APPLICATION_VND_INFOTECH_PROJECT_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_INFOTECH_PROJECT_XML: \"application/vnd.infotech.project+xml\";"
                },
                {
                    "name": "APPLICATION_VND_INNOPATH_WAMP_NOTIFICATION",
                    "type": "variable",
                    "doc": "APPLICATION_VND_INNOPATH_WAMP_NOTIFICATION: \"application/vnd.innopath.wamp.notification\";"
                },
                {
                    "name": "APPLICATION_VND_INSORS_IGM",
                    "type": "variable",
                    "doc": "APPLICATION_VND_INSORS_IGM: \"application/vnd.insors.igm\";"
                },
                {
                    "name": "APPLICATION_VND_INTERCON_FORMNET",
                    "type": "variable",
                    "doc": "APPLICATION_VND_INTERCON_FORMNET: \"application/vnd.intercon.formnet\";"
                },
                {
                    "name": "APPLICATION_VND_INTERGEO",
                    "type": "variable",
                    "doc": "APPLICATION_VND_INTERGEO: \"application/vnd.intergeo\";"
                },
                {
                    "name": "APPLICATION_VND_INTERTRUST_DIGIBOX",
                    "type": "variable",
                    "doc": "APPLICATION_VND_INTERTRUST_DIGIBOX: \"application/vnd.intertrust.digibox\";"
                },
                {
                    "name": "APPLICATION_VND_INTERTRUST_NNCP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_INTERTRUST_NNCP: \"application/vnd.intertrust.nncp\";"
                },
                {
                    "name": "APPLICATION_VND_INTU_QBO",
                    "type": "variable",
                    "doc": "APPLICATION_VND_INTU_QBO: \"application/vnd.intu.qbo\";"
                },
                {
                    "name": "APPLICATION_VND_INTU_QFX",
                    "type": "variable",
                    "doc": "APPLICATION_VND_INTU_QFX: \"application/vnd.intu.qfx\";"
                },
                {
                    "name": "APPLICATION_VND_IPFS_IPNS_RECORD",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IPFS_IPNS_RECORD: \"application/vnd.ipfs.ipns-record\";"
                },
                {
                    "name": "APPLICATION_VND_IPLD_CAR",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IPLD_CAR: \"application/vnd.ipld.car\";"
                },
                {
                    "name": "APPLICATION_VND_IPLD_DAG_CBOR",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IPLD_DAG_CBOR: \"application/vnd.ipld.dag-cbor\";"
                },
                {
                    "name": "APPLICATION_VND_IPLD_DAG_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IPLD_DAG_JSON: \"application/vnd.ipld.dag-json\";"
                },
                {
                    "name": "APPLICATION_VND_IPLD_RAW",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IPLD_RAW: \"application/vnd.ipld.raw\";"
                },
                {
                    "name": "APPLICATION_VND_IPTC_G2_CATALOGITEM_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IPTC_G2_CATALOGITEM_XML: \"application/vnd.iptc.g2.catalogitem+xml\";"
                },
                {
                    "name": "APPLICATION_VND_IPTC_G2_CONCEPTITEM_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IPTC_G2_CONCEPTITEM_XML: \"application/vnd.iptc.g2.conceptitem+xml\";"
                },
                {
                    "name": "APPLICATION_VND_IPTC_G2_KNOWLEDGEITEM_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IPTC_G2_KNOWLEDGEITEM_XML: \"application/vnd.iptc.g2.knowledgeitem+xml\";"
                },
                {
                    "name": "APPLICATION_VND_IPTC_G2_NEWSITEM_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IPTC_G2_NEWSITEM_XML: \"application/vnd.iptc.g2.newsitem+xml\";"
                },
                {
                    "name": "APPLICATION_VND_IPTC_G2_NEWSMESSAGE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IPTC_G2_NEWSMESSAGE_XML: \"application/vnd.iptc.g2.newsmessage+xml\";"
                },
                {
                    "name": "APPLICATION_VND_IPTC_G2_PACKAGEITEM_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IPTC_G2_PACKAGEITEM_XML: \"application/vnd.iptc.g2.packageitem+xml\";"
                },
                {
                    "name": "APPLICATION_VND_IPTC_G2_PLANNINGITEM_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IPTC_G2_PLANNINGITEM_XML: \"application/vnd.iptc.g2.planningitem+xml\";"
                },
                {
                    "name": "APPLICATION_VND_IPUNPLUGGED_RCPROFILE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IPUNPLUGGED_RCPROFILE: \"application/vnd.ipunplugged.rcprofile\";"
                },
                {
                    "name": "APPLICATION_VND_IREPOSITORY_PACKAGE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IREPOSITORY_PACKAGE_XML: \"application/vnd.irepository.package+xml\";"
                },
                {
                    "name": "APPLICATION_VND_ISAC_FCS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ISAC_FCS: \"application/vnd.isac.fcs\";"
                },
                {
                    "name": "APPLICATION_VND_ISO11783_10_ZIP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ISO11783_10_ZIP: \"application/vnd.iso11783-10+zip\";"
                },
                {
                    "name": "APPLICATION_VND_IS_XPR",
                    "type": "variable",
                    "doc": "APPLICATION_VND_IS_XPR: \"application/vnd.is-xpr\";"
                },
                {
                    "name": "APPLICATION_VND_JAM",
                    "type": "variable",
                    "doc": "APPLICATION_VND_JAM: \"application/vnd.jam\";"
                },
                {
                    "name": "APPLICATION_VND_JAPANNET_DIRECTORY_SERVICE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_JAPANNET_DIRECTORY_SERVICE: \"application/vnd.japannet-directory-service\";"
                },
                {
                    "name": "APPLICATION_VND_JAPANNET_JPNSTORE_WAKEUP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_JAPANNET_JPNSTORE_WAKEUP: \"application/vnd.japannet-jpnstore-wakeup\";"
                },
                {
                    "name": "APPLICATION_VND_JAPANNET_PAYMENT_WAKEUP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_JAPANNET_PAYMENT_WAKEUP: \"application/vnd.japannet-payment-wakeup\";"
                },
                {
                    "name": "APPLICATION_VND_JAPANNET_REGISTRATION",
                    "type": "variable",
                    "doc": "APPLICATION_VND_JAPANNET_REGISTRATION: \"application/vnd.japannet-registration\";"
                },
                {
                    "name": "APPLICATION_VND_JAPANNET_REGISTRATION_WAKEUP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_JAPANNET_REGISTRATION_WAKEUP: \"application/vnd.japannet-registration-wakeup\";"
                },
                {
                    "name": "APPLICATION_VND_JAPANNET_SETSTORE_WAKEUP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_JAPANNET_SETSTORE_WAKEUP: \"application/vnd.japannet-setstore-wakeup\";"
                },
                {
                    "name": "APPLICATION_VND_JAPANNET_VERIFICATION",
                    "type": "variable",
                    "doc": "APPLICATION_VND_JAPANNET_VERIFICATION: \"application/vnd.japannet-verification\";"
                },
                {
                    "name": "APPLICATION_VND_JAPANNET_VERIFICATION_WAKEUP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_JAPANNET_VERIFICATION_WAKEUP: \"application/vnd.japannet-verification-wakeup\";"
                },
                {
                    "name": "APPLICATION_VND_JCP_JAVAME_MIDLET_RMS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_JCP_JAVAME_MIDLET_RMS: \"application/vnd.jcp.javame.midlet-rms\";"
                },
                {
                    "name": "APPLICATION_VND_JISP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_JISP: \"application/vnd.jisp\";"
                },
                {
                    "name": "APPLICATION_VND_JOOST_JODA_ARCHIVE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_JOOST_JODA_ARCHIVE: \"application/vnd.joost.joda-archive\";"
                },
                {
                    "name": "APPLICATION_VND_JSK_ISDN_NGN",
                    "type": "variable",
                    "doc": "APPLICATION_VND_JSK_ISDN_NGN: \"application/vnd.jsk.isdn-ngn\";"
                },
                {
                    "name": "APPLICATION_VND_KAHOOTZ",
                    "type": "variable",
                    "doc": "APPLICATION_VND_KAHOOTZ: \"application/vnd.kahootz\";"
                },
                {
                    "name": "APPLICATION_VND_KDE_KARBON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_KDE_KARBON: \"application/vnd.kde.karbon\";"
                },
                {
                    "name": "APPLICATION_VND_KDE_KCHART",
                    "type": "variable",
                    "doc": "APPLICATION_VND_KDE_KCHART: \"application/vnd.kde.kchart\";"
                },
                {
                    "name": "APPLICATION_VND_KDE_KFORMULA",
                    "type": "variable",
                    "doc": "APPLICATION_VND_KDE_KFORMULA: \"application/vnd.kde.kformula\";"
                },
                {
                    "name": "APPLICATION_VND_KDE_KIVIO",
                    "type": "variable",
                    "doc": "APPLICATION_VND_KDE_KIVIO: \"application/vnd.kde.kivio\";"
                },
                {
                    "name": "APPLICATION_VND_KDE_KONTOUR",
                    "type": "variable",
                    "doc": "APPLICATION_VND_KDE_KONTOUR: \"application/vnd.kde.kontour\";"
                },
                {
                    "name": "APPLICATION_VND_KDE_KPRESENTER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_KDE_KPRESENTER: \"application/vnd.kde.kpresenter\";"
                },
                {
                    "name": "APPLICATION_VND_KDE_KSPREAD",
                    "type": "variable",
                    "doc": "APPLICATION_VND_KDE_KSPREAD: \"application/vnd.kde.kspread\";"
                },
                {
                    "name": "APPLICATION_VND_KDE_KWORD",
                    "type": "variable",
                    "doc": "APPLICATION_VND_KDE_KWORD: \"application/vnd.kde.kword\";"
                },
                {
                    "name": "APPLICATION_VND_KENAMEAAPP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_KENAMEAAPP: \"application/vnd.kenameaapp\";"
                },
                {
                    "name": "APPLICATION_VND_KIDSPIRATION",
                    "type": "variable",
                    "doc": "APPLICATION_VND_KIDSPIRATION: \"application/vnd.kidspiration\";"
                },
                {
                    "name": "APPLICATION_VND_KINAR",
                    "type": "variable",
                    "doc": "APPLICATION_VND_KINAR: \"application/vnd.Kinar\";"
                },
                {
                    "name": "APPLICATION_VND_KOAN",
                    "type": "variable",
                    "doc": "APPLICATION_VND_KOAN: \"application/vnd.koan\";"
                },
                {
                    "name": "APPLICATION_VND_KODAK_DESCRIPTOR",
                    "type": "variable",
                    "doc": "APPLICATION_VND_KODAK_DESCRIPTOR: \"application/vnd.kodak-descriptor\";"
                },
                {
                    "name": "APPLICATION_VND_LAS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_LAS: \"application/vnd.las\";"
                },
                {
                    "name": "APPLICATION_VND_LASZIP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_LASZIP: \"application/vnd.laszip\";"
                },
                {
                    "name": "APPLICATION_VND_LAS_LAS_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_LAS_LAS_JSON: \"application/vnd.las.las+json\";"
                },
                {
                    "name": "APPLICATION_VND_LAS_LAS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_LAS_LAS_XML: \"application/vnd.las.las+xml\";"
                },
                {
                    "name": "APPLICATION_VND_LDEV_PRODUCTLICENSING",
                    "type": "variable",
                    "doc": "APPLICATION_VND_LDEV_PRODUCTLICENSING: \"application/vnd.ldev.productlicensing\";"
                },
                {
                    "name": "APPLICATION_VND_LEAP_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_LEAP_JSON: \"application/vnd.leap+json\";"
                },
                {
                    "name": "APPLICATION_VND_LIBERTY_REQUEST_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_LIBERTY_REQUEST_XML: \"application/vnd.liberty-request+xml\";"
                },
                {
                    "name": "APPLICATION_VND_LLAMAGRAPHICS_LIFE_BALANCE_DESKTOP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_LLAMAGRAPHICS_LIFE_BALANCE_DESKTOP: \"application/vnd.llamagraphics.life-balance.desktop\";"
                },
                {
                    "name": "APPLICATION_VND_LLAMAGRAPHICS_LIFE_BALANCE_EXCHANGE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_LLAMAGRAPHICS_LIFE_BALANCE_EXCHANGE_XML: \"application/vnd.llamagraphics.life-balance.exchange+xml\";"
                },
                {
                    "name": "APPLICATION_VND_LOGIPIPE_CIRCUIT_ZIP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_LOGIPIPE_CIRCUIT_ZIP: \"application/vnd.logipipe.circuit+zip\";"
                },
                {
                    "name": "APPLICATION_VND_LOOM",
                    "type": "variable",
                    "doc": "APPLICATION_VND_LOOM: \"application/vnd.loom\";"
                },
                {
                    "name": "APPLICATION_VND_LOTUS_1_2_3",
                    "type": "variable",
                    "doc": "APPLICATION_VND_LOTUS_1_2_3: \"application/vnd.lotus-1-2-3\";"
                },
                {
                    "name": "APPLICATION_VND_LOTUS_APPROACH",
                    "type": "variable",
                    "doc": "APPLICATION_VND_LOTUS_APPROACH: \"application/vnd.lotus-approach\";"
                },
                {
                    "name": "APPLICATION_VND_LOTUS_FREELANCE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_LOTUS_FREELANCE: \"application/vnd.lotus-freelance\";"
                },
                {
                    "name": "APPLICATION_VND_LOTUS_NOTES",
                    "type": "variable",
                    "doc": "APPLICATION_VND_LOTUS_NOTES: \"application/vnd.lotus-notes\";"
                },
                {
                    "name": "APPLICATION_VND_LOTUS_ORGANIZER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_LOTUS_ORGANIZER: \"application/vnd.lotus-organizer\";"
                },
                {
                    "name": "APPLICATION_VND_LOTUS_SCREENCAM",
                    "type": "variable",
                    "doc": "APPLICATION_VND_LOTUS_SCREENCAM: \"application/vnd.lotus-screencam\";"
                },
                {
                    "name": "APPLICATION_VND_LOTUS_WORDPRO",
                    "type": "variable",
                    "doc": "APPLICATION_VND_LOTUS_WORDPRO: \"application/vnd.lotus-wordpro\";"
                },
                {
                    "name": "APPLICATION_VND_MACPORTS_PORTPKG",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MACPORTS_PORTPKG: \"application/vnd.macports.portpkg\";"
                },
                {
                    "name": "APPLICATION_VND_MAPBOX_VECTOR_TILE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MAPBOX_VECTOR_TILE: \"application/vnd.mapbox-vector-tile\";"
                },
                {
                    "name": "APPLICATION_VND_MARLIN_DRM_ACTIONTOKEN_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MARLIN_DRM_ACTIONTOKEN_XML: \"application/vnd.marlin.drm.actiontoken+xml\";"
                },
                {
                    "name": "APPLICATION_VND_MARLIN_DRM_CONFTOKEN_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MARLIN_DRM_CONFTOKEN_XML: \"application/vnd.marlin.drm.conftoken+xml\";"
                },
                {
                    "name": "APPLICATION_VND_MARLIN_DRM_LICENSE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MARLIN_DRM_LICENSE_XML: \"application/vnd.marlin.drm.license+xml\";"
                },
                {
                    "name": "APPLICATION_VND_MARLIN_DRM_MDCF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MARLIN_DRM_MDCF: \"application/vnd.marlin.drm.mdcf\";"
                },
                {
                    "name": "APPLICATION_VND_MASON_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MASON_JSON: \"application/vnd.mason+json\";"
                },
                {
                    "name": "APPLICATION_VND_MAXAR_ARCHIVE_3TZ_ZIP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MAXAR_ARCHIVE_3TZ_ZIP: \"application/vnd.maxar.archive.3tz+zip\";"
                },
                {
                    "name": "APPLICATION_VND_MAXMIND_MAXMIND_DB",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MAXMIND_MAXMIND_DB: \"application/vnd.maxmind.maxmind-db\";"
                },
                {
                    "name": "APPLICATION_VND_MCD",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MCD: \"application/vnd.mcd\";"
                },
                {
                    "name": "APPLICATION_VND_MDL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MDL: \"application/vnd.mdl\";"
                },
                {
                    "name": "APPLICATION_VND_MDL_MBSDF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MDL_MBSDF: \"application/vnd.mdl-mbsdf\";"
                },
                {
                    "name": "APPLICATION_VND_MEDCALCDATA",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MEDCALCDATA: \"application/vnd.medcalcdata\";"
                },
                {
                    "name": "APPLICATION_VND_MEDIASTATION_CDKEY",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MEDIASTATION_CDKEY: \"application/vnd.mediastation.cdkey\";"
                },
                {
                    "name": "APPLICATION_VND_MEDICALHOLODECK_RECORDXR",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MEDICALHOLODECK_RECORDXR: \"application/vnd.medicalholodeck.recordxr\";"
                },
                {
                    "name": "APPLICATION_VND_MERIDIAN_SLINGSHOT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MERIDIAN_SLINGSHOT: \"application/vnd.meridian-slingshot\";"
                },
                {
                    "name": "APPLICATION_VND_MERMAID",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MERMAID: \"application/vnd.mermaid\";"
                },
                {
                    "name": "APPLICATION_VND_MFER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MFER: \"application/vnd.MFER\";"
                },
                {
                    "name": "APPLICATION_VND_MFMP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MFMP: \"application/vnd.mfmp\";"
                },
                {
                    "name": "APPLICATION_VND_MICROGRAFX_FLO",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MICROGRAFX_FLO: \"application/vnd.micrografx.flo\";"
                },
                {
                    "name": "APPLICATION_VND_MICROGRAFX_IGX",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MICROGRAFX_IGX: \"application/vnd.micrografx.igx\";"
                },
                {
                    "name": "APPLICATION_VND_MICROSOFT_PORTABLE_EXECUTABLE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MICROSOFT_PORTABLE_EXECUTABLE: \"application/vnd.microsoft.portable-executable\";"
                },
                {
                    "name": "APPLICATION_VND_MICROSOFT_WINDOWS_THUMBNAIL_CACHE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MICROSOFT_WINDOWS_THUMBNAIL_CACHE: \"application/vnd.microsoft.windows.thumbnail-cache\";"
                },
                {
                    "name": "APPLICATION_VND_MICRO_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MICRO_JSON: \"application/vnd.micro+json\";"
                },
                {
                    "name": "APPLICATION_VND_MIELE_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MIELE_JSON: \"application/vnd.miele+json\";"
                },
                {
                    "name": "APPLICATION_VND_MIF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MIF: \"application/vnd.mif\";"
                },
                {
                    "name": "APPLICATION_VND_MINISOFT_HP3000_SAVE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MINISOFT_HP3000_SAVE: \"application/vnd.minisoft-hp3000-save\";"
                },
                {
                    "name": "APPLICATION_VND_MITSUBISHI_MISTY_GUARD_TRUSTWEB",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MITSUBISHI_MISTY_GUARD_TRUSTWEB: \"application/vnd.mitsubishi.misty-guard.trustweb\";"
                },
                {
                    "name": "APPLICATION_VND_MOBIUS_DAF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MOBIUS_DAF: \"application/vnd.Mobius.DAF\";"
                },
                {
                    "name": "APPLICATION_VND_MOBIUS_DIS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MOBIUS_DIS: \"application/vnd.Mobius.DIS\";"
                },
                {
                    "name": "APPLICATION_VND_MOBIUS_MBK",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MOBIUS_MBK: \"application/vnd.Mobius.MBK\";"
                },
                {
                    "name": "APPLICATION_VND_MOBIUS_MQY",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MOBIUS_MQY: \"application/vnd.Mobius.MQY\";"
                },
                {
                    "name": "APPLICATION_VND_MOBIUS_MSL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MOBIUS_MSL: \"application/vnd.Mobius.MSL\";"
                },
                {
                    "name": "APPLICATION_VND_MOBIUS_PLC",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MOBIUS_PLC: \"application/vnd.Mobius.PLC\";"
                },
                {
                    "name": "APPLICATION_VND_MOBIUS_TXF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MOBIUS_TXF: \"application/vnd.Mobius.TXF\";"
                },
                {
                    "name": "APPLICATION_VND_MODL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MODL: \"application/vnd.modl\";"
                },
                {
                    "name": "APPLICATION_VND_MOPHUN_APPLICATION",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MOPHUN_APPLICATION: \"application/vnd.mophun.application\";"
                },
                {
                    "name": "APPLICATION_VND_MOPHUN_CERTIFICATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MOPHUN_CERTIFICATE: \"application/vnd.mophun.certificate\";"
                },
                {
                    "name": "APPLICATION_VND_MOTOROLA_FLEXSUITE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MOTOROLA_FLEXSUITE: \"application/vnd.motorola.flexsuite\";"
                },
                {
                    "name": "APPLICATION_VND_MOTOROLA_FLEXSUITE_ADSI",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MOTOROLA_FLEXSUITE_ADSI: \"application/vnd.motorola.flexsuite.adsi\";"
                },
                {
                    "name": "APPLICATION_VND_MOTOROLA_FLEXSUITE_FIS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MOTOROLA_FLEXSUITE_FIS: \"application/vnd.motorola.flexsuite.fis\";"
                },
                {
                    "name": "APPLICATION_VND_MOTOROLA_FLEXSUITE_GOTAP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MOTOROLA_FLEXSUITE_GOTAP: \"application/vnd.motorola.flexsuite.gotap\";"
                },
                {
                    "name": "APPLICATION_VND_MOTOROLA_FLEXSUITE_KMR",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MOTOROLA_FLEXSUITE_KMR: \"application/vnd.motorola.flexsuite.kmr\";"
                },
                {
                    "name": "APPLICATION_VND_MOTOROLA_FLEXSUITE_TTC",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MOTOROLA_FLEXSUITE_TTC: \"application/vnd.motorola.flexsuite.ttc\";"
                },
                {
                    "name": "APPLICATION_VND_MOTOROLA_FLEXSUITE_WEM",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MOTOROLA_FLEXSUITE_WEM: \"application/vnd.motorola.flexsuite.wem\";"
                },
                {
                    "name": "APPLICATION_VND_MOTOROLA_IPRM",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MOTOROLA_IPRM: \"application/vnd.motorola.iprm\";"
                },
                {
                    "name": "APPLICATION_VND_MOTOROLA_SCREEN3_GZIP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MOTOROLA_SCREEN3_GZIP: \"application/vnd.motorola.screen3+gzip\";"
                },
                {
                    "name": "APPLICATION_VND_MOTOROLA_SCREEN3_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MOTOROLA_SCREEN3_XML: \"application/vnd.motorola.screen3+xml\";"
                },
                {
                    "name": "APPLICATION_VND_MOZILLA_XUL_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MOZILLA_XUL_XML: \"application/vnd.mozilla.xul+xml\";"
                },
                {
                    "name": "APPLICATION_VND_MSA_DISK_IMAGE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MSA_DISK_IMAGE: \"application/vnd.msa-disk-image\";"
                },
                {
                    "name": "APPLICATION_VND_MSEQ",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MSEQ: \"application/vnd.mseq\";"
                },
                {
                    "name": "APPLICATION_VND_MSIGN",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MSIGN: \"application/vnd.msign\";"
                },
                {
                    "name": "APPLICATION_VND_MS_3MFDOCUMENT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_3MFDOCUMENT: \"application/vnd.ms-3mfdocument\";"
                },
                {
                    "name": "APPLICATION_VND_MS_ARTGALRY",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_ARTGALRY: \"application/vnd.ms-artgalry\";"
                },
                {
                    "name": "APPLICATION_VND_MS_ASF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_ASF: \"application/vnd.ms-asf\";"
                },
                {
                    "name": "APPLICATION_VND_MS_CAB_COMPRESSED",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_CAB_COMPRESSED: \"application/vnd.ms-cab-compressed\";"
                },
                {
                    "name": "APPLICATION_VND_MS_EXCEL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_EXCEL: \"application/vnd.ms-excel\";"
                },
                {
                    "name": "APPLICATION_VND_MS_EXCEL_ADDIN_MACROENABLED_12",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_EXCEL_ADDIN_MACROENABLED_12: \"application/vnd.ms-excel.addin.macroEnabled.12\";"
                },
                {
                    "name": "APPLICATION_VND_MS_EXCEL_SHEET_BINARY_MACROENABLED_12",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_EXCEL_SHEET_BINARY_MACROENABLED_12: \"application/vnd.ms-excel.sheet.binary.macroEnabled.12\";"
                },
                {
                    "name": "APPLICATION_VND_MS_EXCEL_SHEET_MACROENABLED_12",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_EXCEL_SHEET_MACROENABLED_12: \"application/vnd.ms-excel.sheet.macroEnabled.12\";"
                },
                {
                    "name": "APPLICATION_VND_MS_EXCEL_TEMPLATE_MACROENABLED_12",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_EXCEL_TEMPLATE_MACROENABLED_12: \"application/vnd.ms-excel.template.macroEnabled.12\";"
                },
                {
                    "name": "APPLICATION_VND_MS_FONTOBJECT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_FONTOBJECT: \"application/vnd.ms-fontobject\";"
                },
                {
                    "name": "APPLICATION_VND_MS_HTMLHELP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_HTMLHELP: \"application/vnd.ms-htmlhelp\";"
                },
                {
                    "name": "APPLICATION_VND_MS_IMS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_IMS: \"application/vnd.ms-ims\";"
                },
                {
                    "name": "APPLICATION_VND_MS_LRM",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_LRM: \"application/vnd.ms-lrm\";"
                },
                {
                    "name": "APPLICATION_VND_MS_OFFICETHEME",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_OFFICETHEME: \"application/vnd.ms-officetheme\";"
                },
                {
                    "name": "APPLICATION_VND_MS_OFFICE_ACTIVEX_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_OFFICE_ACTIVEX_XML: \"application/vnd.ms-office.activeX+xml\";"
                },
                {
                    "name": "APPLICATION_VND_MS_PKI_SECCAT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_PKI_SECCAT: \"application/vnd.ms-pki.seccat\";"
                },
                {
                    "name": "APPLICATION_VND_MS_PLAYREADY_INITIATOR_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_PLAYREADY_INITIATOR_XML: \"application/vnd.ms-playready.initiator+xml\";"
                },
                {
                    "name": "APPLICATION_VND_MS_POWERPOINT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_POWERPOINT: \"application/vnd.ms-powerpoint\";"
                },
                {
                    "name": "APPLICATION_VND_MS_POWERPOINT_ADDIN_MACROENABLED_12",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_POWERPOINT_ADDIN_MACROENABLED_12: \"application/vnd.ms-powerpoint.addin.macroEnabled.12\";"
                },
                {
                    "name": "APPLICATION_VND_MS_POWERPOINT_PRESENTATION_MACROENABLED_12",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_POWERPOINT_PRESENTATION_MACROENABLED_12: \"application/vnd.ms-powerpoint.presentation.macroEnabled.12\";"
                },
                {
                    "name": "APPLICATION_VND_MS_POWERPOINT_SLIDESHOW_MACROENABLED_12",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_POWERPOINT_SLIDESHOW_MACROENABLED_12: \"application/vnd.ms-powerpoint.slideshow.macroEnabled.12\";"
                },
                {
                    "name": "APPLICATION_VND_MS_POWERPOINT_SLIDE_MACROENABLED_12",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_POWERPOINT_SLIDE_MACROENABLED_12: \"application/vnd.ms-powerpoint.slide.macroEnabled.12\";"
                },
                {
                    "name": "APPLICATION_VND_MS_POWERPOINT_TEMPLATE_MACROENABLED_12",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_POWERPOINT_TEMPLATE_MACROENABLED_12: \"application/vnd.ms-powerpoint.template.macroEnabled.12\";"
                },
                {
                    "name": "APPLICATION_VND_MS_PRINTDEVICECAPABILITIES_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_PRINTDEVICECAPABILITIES_XML: \"application/vnd.ms-PrintDeviceCapabilities+xml\";"
                },
                {
                    "name": "APPLICATION_VND_MS_PRINTSCHEMATICKET_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_PRINTSCHEMATICKET_XML: \"application/vnd.ms-PrintSchemaTicket+xml\";"
                },
                {
                    "name": "APPLICATION_VND_MS_PROJECT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_PROJECT: \"application/vnd.ms-project\";"
                },
                {
                    "name": "APPLICATION_VND_MS_TNEF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_TNEF: \"application/vnd.ms-tnef\";"
                },
                {
                    "name": "APPLICATION_VND_MS_WINDOWS_DEVICEPAIRING",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_WINDOWS_DEVICEPAIRING: \"application/vnd.ms-windows.devicepairing\";"
                },
                {
                    "name": "APPLICATION_VND_MS_WINDOWS_NWPRINTING_OOB",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_WINDOWS_NWPRINTING_OOB: \"application/vnd.ms-windows.nwprinting.oob\";"
                },
                {
                    "name": "APPLICATION_VND_MS_WINDOWS_PRINTERPAIRING",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_WINDOWS_PRINTERPAIRING: \"application/vnd.ms-windows.printerpairing\";"
                },
                {
                    "name": "APPLICATION_VND_MS_WINDOWS_WSD_OOB",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_WINDOWS_WSD_OOB: \"application/vnd.ms-windows.wsd.oob\";"
                },
                {
                    "name": "APPLICATION_VND_MS_WMDRM_LIC_CHLG_REQ",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_WMDRM_LIC_CHLG_REQ: \"application/vnd.ms-wmdrm.lic-chlg-req\";"
                },
                {
                    "name": "APPLICATION_VND_MS_WMDRM_LIC_RESP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_WMDRM_LIC_RESP: \"application/vnd.ms-wmdrm.lic-resp\";"
                },
                {
                    "name": "APPLICATION_VND_MS_WMDRM_METER_CHLG_REQ",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_WMDRM_METER_CHLG_REQ: \"application/vnd.ms-wmdrm.meter-chlg-req\";"
                },
                {
                    "name": "APPLICATION_VND_MS_WMDRM_METER_RESP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_WMDRM_METER_RESP: \"application/vnd.ms-wmdrm.meter-resp\";"
                },
                {
                    "name": "APPLICATION_VND_MS_WORD_DOCUMENT_MACROENABLED_12",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_WORD_DOCUMENT_MACROENABLED_12: \"application/vnd.ms-word.document.macroEnabled.12\";"
                },
                {
                    "name": "APPLICATION_VND_MS_WORD_TEMPLATE_MACROENABLED_12",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_WORD_TEMPLATE_MACROENABLED_12: \"application/vnd.ms-word.template.macroEnabled.12\";"
                },
                {
                    "name": "APPLICATION_VND_MS_WORKS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_WORKS: \"application/vnd.ms-works\";"
                },
                {
                    "name": "APPLICATION_VND_MS_WPL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_WPL: \"application/vnd.ms-wpl\";"
                },
                {
                    "name": "APPLICATION_VND_MS_XPSDOCUMENT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MS_XPSDOCUMENT: \"application/vnd.ms-xpsdocument\";"
                },
                {
                    "name": "APPLICATION_VND_MULTIAD_CREATOR",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MULTIAD_CREATOR: \"application/vnd.multiad.creator\";"
                },
                {
                    "name": "APPLICATION_VND_MULTIAD_CREATOR_CIF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MULTIAD_CREATOR_CIF: \"application/vnd.multiad.creator.cif\";"
                },
                {
                    "name": "APPLICATION_VND_MUSICIAN",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MUSICIAN: \"application/vnd.musician\";"
                },
                {
                    "name": "APPLICATION_VND_MUSIC_NIFF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MUSIC_NIFF: \"application/vnd.music-niff\";"
                },
                {
                    "name": "APPLICATION_VND_MUVEE_STYLE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MUVEE_STYLE: \"application/vnd.muvee.style\";"
                },
                {
                    "name": "APPLICATION_VND_MYNFC",
                    "type": "variable",
                    "doc": "APPLICATION_VND_MYNFC: \"application/vnd.mynfc\";"
                },
                {
                    "name": "APPLICATION_VND_NACAMAR_YBRID_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NACAMAR_YBRID_JSON: \"application/vnd.nacamar.ybrid+json\";"
                },
                {
                    "name": "APPLICATION_VND_NATO_BINDINGDATAOBJECT_CBOR",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NATO_BINDINGDATAOBJECT_CBOR: \"application/vnd.nato.bindingdataobject+cbor\";"
                },
                {
                    "name": "APPLICATION_VND_NATO_BINDINGDATAOBJECT_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NATO_BINDINGDATAOBJECT_JSON: \"application/vnd.nato.bindingdataobject+json\";"
                },
                {
                    "name": "APPLICATION_VND_NATO_BINDINGDATAOBJECT_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NATO_BINDINGDATAOBJECT_XML: \"application/vnd.nato.bindingdataobject+xml\";"
                },
                {
                    "name": "APPLICATION_VND_NATO_OPENXMLFORMATS_PACKAGE_IEPD_ZIP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NATO_OPENXMLFORMATS_PACKAGE_IEPD_ZIP: \"application/vnd.nato.openxmlformats-package.iepd+zip\";"
                },
                {
                    "name": "APPLICATION_VND_NCD_CONTROL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NCD_CONTROL: \"application/vnd.ncd.control\";"
                },
                {
                    "name": "APPLICATION_VND_NCD_REFERENCE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NCD_REFERENCE: \"application/vnd.ncd.reference\";"
                },
                {
                    "name": "APPLICATION_VND_NEARST_INV_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NEARST_INV_JSON: \"application/vnd.nearst.inv+json\";"
                },
                {
                    "name": "APPLICATION_VND_NEBUMIND_LINE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NEBUMIND_LINE: \"application/vnd.nebumind.line\";"
                },
                {
                    "name": "APPLICATION_VND_NERVANA",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NERVANA: \"application/vnd.nervana\";"
                },
                {
                    "name": "APPLICATION_VND_NETFPX",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NETFPX: \"application/vnd.netfpx\";"
                },
                {
                    "name": "APPLICATION_VND_NEUROLANGUAGE_NLU",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NEUROLANGUAGE_NLU: \"application/vnd.neurolanguage.nlu\";"
                },
                {
                    "name": "APPLICATION_VND_NIMN",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NIMN: \"application/vnd.nimn\";"
                },
                {
                    "name": "APPLICATION_VND_NINTENDO_NITRO_ROM",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NINTENDO_NITRO_ROM: \"application/vnd.nintendo.nitro.rom\";"
                },
                {
                    "name": "APPLICATION_VND_NINTENDO_SNES_ROM",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NINTENDO_SNES_ROM: \"application/vnd.nintendo.snes.rom\";"
                },
                {
                    "name": "APPLICATION_VND_NITF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NITF: \"application/vnd.nitf\";"
                },
                {
                    "name": "APPLICATION_VND_NOBLENET_DIRECTORY",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NOBLENET_DIRECTORY: \"application/vnd.noblenet-directory\";"
                },
                {
                    "name": "APPLICATION_VND_NOBLENET_SEALER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NOBLENET_SEALER: \"application/vnd.noblenet-sealer\";"
                },
                {
                    "name": "APPLICATION_VND_NOBLENET_WEB",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NOBLENET_WEB: \"application/vnd.noblenet-web\";"
                },
                {
                    "name": "APPLICATION_VND_NOKIA_CATALOGS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NOKIA_CATALOGS: \"application/vnd.nokia.catalogs\";"
                },
                {
                    "name": "APPLICATION_VND_NOKIA_CONML_WBXML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NOKIA_CONML_WBXML: \"application/vnd.nokia.conml+wbxml\";"
                },
                {
                    "name": "APPLICATION_VND_NOKIA_CONML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NOKIA_CONML_XML: \"application/vnd.nokia.conml+xml\";"
                },
                {
                    "name": "APPLICATION_VND_NOKIA_IPDC_PURCHASE_RESPONSE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NOKIA_IPDC_PURCHASE_RESPONSE: \"application/vnd.nokia.ipdc-purchase-response\";"
                },
                {
                    "name": "APPLICATION_VND_NOKIA_IPTV_CONFIG_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NOKIA_IPTV_CONFIG_XML: \"application/vnd.nokia.iptv.config+xml\";"
                },
                {
                    "name": "APPLICATION_VND_NOKIA_ISDS_RADIO_PRESETS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NOKIA_ISDS_RADIO_PRESETS: \"application/vnd.nokia.iSDS-radio-presets\";"
                },
                {
                    "name": "APPLICATION_VND_NOKIA_LANDMARKCOLLECTION_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NOKIA_LANDMARKCOLLECTION_XML: \"application/vnd.nokia.landmarkcollection+xml\";"
                },
                {
                    "name": "APPLICATION_VND_NOKIA_LANDMARK_WBXML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NOKIA_LANDMARK_WBXML: \"application/vnd.nokia.landmark+wbxml\";"
                },
                {
                    "name": "APPLICATION_VND_NOKIA_LANDMARK_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NOKIA_LANDMARK_XML: \"application/vnd.nokia.landmark+xml\";"
                },
                {
                    "name": "APPLICATION_VND_NOKIA_NCD",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NOKIA_NCD: \"application/vnd.nokia.ncd\";"
                },
                {
                    "name": "APPLICATION_VND_NOKIA_N_GAGE_AC_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NOKIA_N_GAGE_AC_XML: \"application/vnd.nokia.n-gage.ac+xml\";"
                },
                {
                    "name": "APPLICATION_VND_NOKIA_N_GAGE_DATA",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NOKIA_N_GAGE_DATA: \"application/vnd.nokia.n-gage.data\";"
                },
                {
                    "name": "APPLICATION_VND_NOKIA_N_GAGE_SYMBIAN_INSTALL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NOKIA_N_GAGE_SYMBIAN_INSTALL: \"application/vnd.nokia.n-gage.symbian.install\";"
                },
                {
                    "name": "APPLICATION_VND_NOKIA_PCD_WBXML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NOKIA_PCD_WBXML: \"application/vnd.nokia.pcd+wbxml\";"
                },
                {
                    "name": "APPLICATION_VND_NOKIA_PCD_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NOKIA_PCD_XML: \"application/vnd.nokia.pcd+xml\";"
                },
                {
                    "name": "APPLICATION_VND_NOKIA_RADIO_PRESET",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NOKIA_RADIO_PRESET: \"application/vnd.nokia.radio-preset\";"
                },
                {
                    "name": "APPLICATION_VND_NOKIA_RADIO_PRESETS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NOKIA_RADIO_PRESETS: \"application/vnd.nokia.radio-presets\";"
                },
                {
                    "name": "APPLICATION_VND_NOKIA_SYNCSET_WBXML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NOKIA_SYNCSET_WBXML: \"application/vnd.nokia.syncset+wbxml\";"
                },
                {
                    "name": "APPLICATION_VND_NOVADIGM_EDM",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NOVADIGM_EDM: \"application/vnd.novadigm.EDM\";"
                },
                {
                    "name": "APPLICATION_VND_NOVADIGM_EDX",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NOVADIGM_EDX: \"application/vnd.novadigm.EDX\";"
                },
                {
                    "name": "APPLICATION_VND_NOVADIGM_EXT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NOVADIGM_EXT: \"application/vnd.novadigm.EXT\";"
                },
                {
                    "name": "APPLICATION_VND_NTT_LOCAL_CONTENT_SHARE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NTT_LOCAL_CONTENT_SHARE: \"application/vnd.ntt-local.content-share\";"
                },
                {
                    "name": "APPLICATION_VND_NTT_LOCAL_FILE_TRANSFER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NTT_LOCAL_FILE_TRANSFER: \"application/vnd.ntt-local.file-transfer\";"
                },
                {
                    "name": "APPLICATION_VND_NTT_LOCAL_OGW_REMOTE_ACCESS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NTT_LOCAL_OGW_REMOTE_ACCESS: \"application/vnd.ntt-local.ogw_remote-access\";"
                },
                {
                    "name": "APPLICATION_VND_NTT_LOCAL_SIP_TA_REMOTE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NTT_LOCAL_SIP_TA_REMOTE: \"application/vnd.ntt-local.sip-ta_remote\";"
                },
                {
                    "name": "APPLICATION_VND_NTT_LOCAL_SIP_TA_TCP_STREAM",
                    "type": "variable",
                    "doc": "APPLICATION_VND_NTT_LOCAL_SIP_TA_TCP_STREAM: \"application/vnd.ntt-local.sip-ta_tcp_stream\";"
                },
                {
                    "name": "APPLICATION_VND_OAI_WORKFLOWS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OAI_WORKFLOWS: \"application/vnd.oai.workflows\";"
                },
                {
                    "name": "APPLICATION_VND_OAI_WORKFLOWS_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OAI_WORKFLOWS_JSON: \"application/vnd.oai.workflows+json\";"
                },
                {
                    "name": "APPLICATION_VND_OAI_WORKFLOWS_YAML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OAI_WORKFLOWS_YAML: \"application/vnd.oai.workflows+yaml\";"
                },
                {
                    "name": "APPLICATION_VND_OASIS_OPENDOCUMENT_BASE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OASIS_OPENDOCUMENT_BASE: \"application/vnd.oasis.opendocument.base\";"
                },
                {
                    "name": "APPLICATION_VND_OASIS_OPENDOCUMENT_CHART",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OASIS_OPENDOCUMENT_CHART: \"application/vnd.oasis.opendocument.chart\";"
                },
                {
                    "name": "APPLICATION_VND_OASIS_OPENDOCUMENT_CHART_TEMPLATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OASIS_OPENDOCUMENT_CHART_TEMPLATE: \"application/vnd.oasis.opendocument.chart-template\";"
                },
                {
                    "name": "APPLICATION_VND_OASIS_OPENDOCUMENT_DATABASE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OASIS_OPENDOCUMENT_DATABASE: \"application/vnd.oasis.opendocument.database\";"
                },
                {
                    "name": "APPLICATION_VND_OASIS_OPENDOCUMENT_FORMULA",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OASIS_OPENDOCUMENT_FORMULA: \"application/vnd.oasis.opendocument.formula\";"
                },
                {
                    "name": "APPLICATION_VND_OASIS_OPENDOCUMENT_FORMULA_TEMPLATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OASIS_OPENDOCUMENT_FORMULA_TEMPLATE: \"application/vnd.oasis.opendocument.formula-template\";"
                },
                {
                    "name": "APPLICATION_VND_OASIS_OPENDOCUMENT_GRAPHICS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OASIS_OPENDOCUMENT_GRAPHICS: \"application/vnd.oasis.opendocument.graphics\";"
                },
                {
                    "name": "APPLICATION_VND_OASIS_OPENDOCUMENT_GRAPHICS_TEMPLATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OASIS_OPENDOCUMENT_GRAPHICS_TEMPLATE: \"application/vnd.oasis.opendocument.graphics-template\";"
                },
                {
                    "name": "APPLICATION_VND_OASIS_OPENDOCUMENT_IMAGE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OASIS_OPENDOCUMENT_IMAGE: \"application/vnd.oasis.opendocument.image\";"
                },
                {
                    "name": "APPLICATION_VND_OASIS_OPENDOCUMENT_IMAGE_TEMPLATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OASIS_OPENDOCUMENT_IMAGE_TEMPLATE: \"application/vnd.oasis.opendocument.image-template\";"
                },
                {
                    "name": "APPLICATION_VND_OASIS_OPENDOCUMENT_PRESENTATION",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OASIS_OPENDOCUMENT_PRESENTATION: \"application/vnd.oasis.opendocument.presentation\";"
                },
                {
                    "name": "APPLICATION_VND_OASIS_OPENDOCUMENT_PRESENTATION_TEMPLATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OASIS_OPENDOCUMENT_PRESENTATION_TEMPLATE: \"application/vnd.oasis.opendocument.presentation-template\";"
                },
                {
                    "name": "APPLICATION_VND_OASIS_OPENDOCUMENT_SPREADSHEET",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OASIS_OPENDOCUMENT_SPREADSHEET: \"application/vnd.oasis.opendocument.spreadsheet\";"
                },
                {
                    "name": "APPLICATION_VND_OASIS_OPENDOCUMENT_SPREADSHEET_TEMPLATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OASIS_OPENDOCUMENT_SPREADSHEET_TEMPLATE: \"application/vnd.oasis.opendocument.spreadsheet-template\";"
                },
                {
                    "name": "APPLICATION_VND_OASIS_OPENDOCUMENT_TEXT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OASIS_OPENDOCUMENT_TEXT: \"application/vnd.oasis.opendocument.text\";"
                },
                {
                    "name": "APPLICATION_VND_OASIS_OPENDOCUMENT_TEXT_MASTER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OASIS_OPENDOCUMENT_TEXT_MASTER: \"application/vnd.oasis.opendocument.text-master\";"
                },
                {
                    "name": "APPLICATION_VND_OASIS_OPENDOCUMENT_TEXT_MASTER_TEMPLATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OASIS_OPENDOCUMENT_TEXT_MASTER_TEMPLATE: \"application/vnd.oasis.opendocument.text-master-template\";"
                },
                {
                    "name": "APPLICATION_VND_OASIS_OPENDOCUMENT_TEXT_TEMPLATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OASIS_OPENDOCUMENT_TEXT_TEMPLATE: \"application/vnd.oasis.opendocument.text-template\";"
                },
                {
                    "name": "APPLICATION_VND_OASIS_OPENDOCUMENT_TEXT_WEB",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OASIS_OPENDOCUMENT_TEXT_WEB: \"application/vnd.oasis.opendocument.text-web\";"
                },
                {
                    "name": "APPLICATION_VND_OASIS_PRESENTATION",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OASIS_PRESENTATION: \"application/vnd.oasis.opendocument.presentation\";"
                },
                {
                    "name": "APPLICATION_VND_OASIS_SPREADSHEET",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OASIS_SPREADSHEET: \"application/vnd.oasis.opendocument.spreadsheet\";"
                },
                {
                    "name": "APPLICATION_VND_OASIS_TEXT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OASIS_TEXT: \"application/vnd.oasis.opendocument.text\";"
                },
                {
                    "name": "APPLICATION_VND_OBN",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OBN: \"application/vnd.obn\";"
                },
                {
                    "name": "APPLICATION_VND_OCF_CBOR",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OCF_CBOR: \"application/vnd.ocf+cbor\";"
                },
                {
                    "name": "APPLICATION_VND_OCI_IMAGE_MANIFEST_V1_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OCI_IMAGE_MANIFEST_V1_JSON: \"application/vnd.oci.image.manifest.v1+json\";"
                },
                {
                    "name": "APPLICATION_VND_OFTN_L10N_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OFTN_L10N_JSON: \"application/vnd.oftn.l10n+json\";"
                },
                {
                    "name": "APPLICATION_VND_OIPF_CONTENTACCESSDOWNLOAD_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OIPF_CONTENTACCESSDOWNLOAD_XML: \"application/vnd.oipf.contentaccessdownload+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OIPF_CONTENTACCESSSTREAMING_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OIPF_CONTENTACCESSSTREAMING_XML: \"application/vnd.oipf.contentaccessstreaming+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OIPF_CSPG_HEXBINARY",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OIPF_CSPG_HEXBINARY: \"application/vnd.oipf.cspg-hexbinary\";"
                },
                {
                    "name": "APPLICATION_VND_OIPF_DAE_SVG_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OIPF_DAE_SVG_XML: \"application/vnd.oipf.dae.svg+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OIPF_DAE_XHTML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OIPF_DAE_XHTML_XML: \"application/vnd.oipf.dae.xhtml+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OIPF_MIPPVCONTROLMESSAGE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OIPF_MIPPVCONTROLMESSAGE_XML: \"application/vnd.oipf.mippvcontrolmessage+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OIPF_PAE_GEM",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OIPF_PAE_GEM: \"application/vnd.oipf.pae.gem\";"
                },
                {
                    "name": "APPLICATION_VND_OIPF_SPDISCOVERY_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OIPF_SPDISCOVERY_XML: \"application/vnd.oipf.spdiscovery+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OIPF_SPDLIST_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OIPF_SPDLIST_XML: \"application/vnd.oipf.spdlist+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OIPF_UEPROFILE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OIPF_UEPROFILE_XML: \"application/vnd.oipf.ueprofile+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OIPF_USERPROFILE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OIPF_USERPROFILE_XML: \"application/vnd.oipf.userprofile+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OLPC_SUGAR",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OLPC_SUGAR: \"application/vnd.olpc-sugar\";"
                },
                {
                    "name": "APPLICATION_VND_OMADS_EMAIL_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMADS_EMAIL_XML: \"application/vnd.omads-email+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OMADS_FILE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMADS_FILE_XML: \"application/vnd.omads-file+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OMADS_FOLDER_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMADS_FOLDER_XML: \"application/vnd.omads-folder+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OMALOC_SUPL_INIT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMALOC_SUPL_INIT: \"application/vnd.omaloc-supl-init\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_BCAST_ASSOCIATED_PROCEDURE_PARAMETER_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_BCAST_ASSOCIATED_PROCEDURE_PARAMETER_XML: \"application/vnd.oma.bcast.associated-procedure-parameter+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_BCAST_DRM_TRIGGER_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_BCAST_DRM_TRIGGER_XML: \"application/vnd.oma.bcast.drm-trigger+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_BCAST_IMD_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_BCAST_IMD_XML: \"application/vnd.oma.bcast.imd+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_BCAST_LTKM",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_BCAST_LTKM: \"application/vnd.oma.bcast.ltkm\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_BCAST_NOTIFICATION_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_BCAST_NOTIFICATION_XML: \"application/vnd.oma.bcast.notification+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_BCAST_PROVISIONINGTRIGGER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_BCAST_PROVISIONINGTRIGGER: \"application/vnd.oma.bcast.provisioningtrigger\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_BCAST_SGBOOT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_BCAST_SGBOOT: \"application/vnd.oma.bcast.sgboot\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_BCAST_SGDD_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_BCAST_SGDD_XML: \"application/vnd.oma.bcast.sgdd+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_BCAST_SGDU",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_BCAST_SGDU: \"application/vnd.oma.bcast.sgdu\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_BCAST_SIMPLE_SYMBOL_CONTAINER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_BCAST_SIMPLE_SYMBOL_CONTAINER: \"application/vnd.oma.bcast.simple-symbol-container\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_BCAST_SMARTCARD_TRIGGER_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_BCAST_SMARTCARD_TRIGGER_XML: \"application/vnd.oma.bcast.smartcard-trigger+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_BCAST_SPROV_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_BCAST_SPROV_XML: \"application/vnd.oma.bcast.sprov+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_BCAST_STKM",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_BCAST_STKM: \"application/vnd.oma.bcast.stkm\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_CAB_ADDRESS_BOOK_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_CAB_ADDRESS_BOOK_XML: \"application/vnd.oma.cab-address-book+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_CAB_FEATURE_HANDLER_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_CAB_FEATURE_HANDLER_XML: \"application/vnd.oma.cab-feature-handler+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_CAB_PCC_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_CAB_PCC_XML: \"application/vnd.oma.cab-pcc+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_CAB_SUBS_INVITE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_CAB_SUBS_INVITE_XML: \"application/vnd.oma.cab-subs-invite+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_CAB_USER_PREFS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_CAB_USER_PREFS_XML: \"application/vnd.oma.cab-user-prefs+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_DCD",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_DCD: \"application/vnd.oma.dcd\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_DCDC",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_DCDC: \"application/vnd.oma.dcdc\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_DD2_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_DD2_XML: \"application/vnd.oma.dd2+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_DD_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_DD_XML: \"application/vnd.oma.dd+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_DRM_CONTENT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_DRM_CONTENT: \"application/vnd.oma.drm.content\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_DRM_MESSAGE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_DRM_MESSAGE: \"application/vnd.oma.drm.message\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_DRM_RIGHTS_WBXML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_DRM_RIGHTS_WBXML: \"application/vnd.oma.drm.rights+wbxml\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_DRM_RIGHTS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_DRM_RIGHTS_XML: \"application/vnd.oma.drm.rights+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_DRM_RISD_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_DRM_RISD_XML: \"application/vnd.oma.drm.risd+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_DRM_ROAP_TRIGGER_WBXML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_DRM_ROAP_TRIGGER_WBXML: \"application/vnd.oma.drm.roap-trigger+wbxml\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_GROUP_USAGE_LIST_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_GROUP_USAGE_LIST_XML: \"application/vnd.oma.group-usage-list+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_LWM2M_CBOR",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_LWM2M_CBOR: \"application/vnd.oma.lwm2m+cbor\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_LWM2M_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_LWM2M_JSON: \"application/vnd.oma.lwm2m+json\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_LWM2M_TLV",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_LWM2M_TLV: \"application/vnd.oma.lwm2m+tlv\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_PAL_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_PAL_XML: \"application/vnd.oma.pal+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_POC_DETAILED_PROGRESS_REPORT_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_POC_DETAILED_PROGRESS_REPORT_XML: \"application/vnd.oma.poc.detailed-progress-report+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_POC_FINAL_REPORT_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_POC_FINAL_REPORT_XML: \"application/vnd.oma.poc.final-report+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_POC_GROUPS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_POC_GROUPS_XML: \"application/vnd.oma.poc.groups+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_POC_INVOCATION_DESCRIPTOR_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_POC_INVOCATION_DESCRIPTOR_XML: \"application/vnd.oma.poc.invocation-descriptor+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_POC_OPTIMIZED_PROGRESS_REPORT_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_POC_OPTIMIZED_PROGRESS_REPORT_XML: \"application/vnd.oma.poc.optimized-progress-report+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_PUSH",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_PUSH: \"application/vnd.oma.push\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_SCIDM_MESSAGES_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_SCIDM_MESSAGES_XML: \"application/vnd.oma.scidm.messages+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_SCWS_CONFIG",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_SCWS_CONFIG: \"application/vnd.oma-scws-config\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_SCWS_HTTP_REQUEST",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_SCWS_HTTP_REQUEST: \"application/vnd.oma-scws-http-request\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_SCWS_HTTP_RESPONSE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_SCWS_HTTP_RESPONSE: \"application/vnd.oma-scws-http-response\";"
                },
                {
                    "name": "APPLICATION_VND_OMA_XCAP_DIRECTORY_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OMA_XCAP_DIRECTORY_XML: \"application/vnd.oma.xcap-directory+xml\";"
                },
                {
                    "name": "APPLICATION_VND_ONEPAGER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ONEPAGER: \"application/vnd.onepager\";"
                },
                {
                    "name": "APPLICATION_VND_ONEPAGERTAMP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ONEPAGERTAMP: \"application/vnd.onepagertamp\";"
                },
                {
                    "name": "APPLICATION_VND_ONEPAGERTAMX",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ONEPAGERTAMX: \"application/vnd.onepagertamx\";"
                },
                {
                    "name": "APPLICATION_VND_ONEPAGERTAT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ONEPAGERTAT: \"application/vnd.onepagertat\";"
                },
                {
                    "name": "APPLICATION_VND_ONEPAGERTATP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ONEPAGERTATP: \"application/vnd.onepagertatp\";"
                },
                {
                    "name": "APPLICATION_VND_ONEPAGERTATX",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ONEPAGERTATX: \"application/vnd.onepagertatx\";"
                },
                {
                    "name": "APPLICATION_VND_ONVIF_METADATA",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ONVIF_METADATA: \"application/vnd.onvif.metadata\";"
                },
                {
                    "name": "APPLICATION_VND_OPENBLOX_GAME_BINARY",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENBLOX_GAME_BINARY: \"application/vnd.openblox.game-binary\";"
                },
                {
                    "name": "APPLICATION_VND_OPENBLOX_GAME_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENBLOX_GAME_XML: \"application/vnd.openblox.game+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENEYE_OEB",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENEYE_OEB: \"application/vnd.openeye.oeb\";"
                },
                {
                    "name": "APPLICATION_VND_OPENSTREETMAP_DATA_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENSTREETMAP_DATA_XML: \"application/vnd.openstreetmap.data+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENTIMESTAMPS_OTS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENTIMESTAMPS_OTS: \"application/vnd.opentimestamps.ots\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_CUSTOMXMLPROPERTIES_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_CUSTOMXMLPROPERTIES_XML: \"application/vnd.openxmlformats-officedocument.customXmlProperties+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_CUSTOM_PROPERTIES_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_CUSTOM_PROPERTIES_XML: \"application/vnd.openxmlformats-officedocument.custom-properties+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_DRAWINGML_CHARTSHAPES_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_DRAWINGML_CHARTSHAPES_XML: \"application/vnd.openxmlformats-officedocument.drawingml.chartshapes+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_DRAWINGML_CHART_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_DRAWINGML_CHART_XML: \"application/vnd.openxmlformats-officedocument.drawingml.chart+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_DRAWINGML_DIAGRAMCOLORS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_DRAWINGML_DIAGRAMCOLORS_XML: \"application/vnd.openxmlformats-officedocument.drawingml.diagramColors+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_DRAWINGML_DIAGRAMDATA_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_DRAWINGML_DIAGRAMDATA_XML: \"application/vnd.openxmlformats-officedocument.drawingml.diagramData+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_DRAWINGML_DIAGRAMLAYOUT_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_DRAWINGML_DIAGRAMLAYOUT_XML: \"application/vnd.openxmlformats-officedocument.drawingml.diagramLayout+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_DRAWINGML_DIAGRAMSTYLE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_DRAWINGML_DIAGRAMSTYLE_XML: \"application/vnd.openxmlformats-officedocument.drawingml.diagramStyle+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_DRAWING_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_DRAWING_XML: \"application/vnd.openxmlformats-officedocument.drawing+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_EXTENDED_PROPERTIES_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_EXTENDED_PROPERTIES_XML: \"application/vnd.openxmlformats-officedocument.extended-properties+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_COMMENTAUTHORS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_COMMENTAUTHORS_XML: \"application/vnd.openxmlformats-officedocument.presentationml.commentAuthors+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_COMMENTS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_COMMENTS_XML: \"application/vnd.openxmlformats-officedocument.presentationml.comments+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_HANDOUTMASTER_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_HANDOUTMASTER_XML: \"application/vnd.openxmlformats-officedocument.presentationml.handoutMaster+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_NOTESMASTER_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_NOTESMASTER_XML: \"application/vnd.openxmlformats-officedocument.presentationml.notesMaster+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_NOTESSLIDE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_NOTESSLIDE_XML: \"application/vnd.openxmlformats-officedocument.presentationml.notesSlide+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_PRESENTATION",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_PRESENTATION: \"application/vnd.openxmlformats-officedocument.presentationml.presentation\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_PRESENTATION_MAIN_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_PRESENTATION_MAIN_XML: \"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_PRESPROPS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_PRESPROPS_XML: \"application/vnd.openxmlformats-officedocument.presentationml.presProps+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_SLIDE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_SLIDE: \"application/vnd.openxmlformats-officedocument.presentationml.slide\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_SLIDELAYOUT_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_SLIDELAYOUT_XML: \"application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_SLIDEMASTER_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_SLIDEMASTER_XML: \"application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_SLIDESHOW",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_SLIDESHOW: \"application/vnd.openxmlformats-officedocument.presentationml.slideshow\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_SLIDESHOW_MAIN_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_SLIDESHOW_MAIN_XML: \"application/vnd.openxmlformats-officedocument.presentationml.slideshow.main+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_SLIDEUPDATEINFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_SLIDEUPDATEINFO_XML: \"application/vnd.openxmlformats-officedocument.presentationml.slideUpdateInfo+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_SLIDE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_SLIDE_XML: \"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_TABLESTYLES_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_TABLESTYLES_XML: \"application/vnd.openxmlformats-officedocument.presentationml.tableStyles+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_TAGS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_TAGS_XML: \"application/vnd.openxmlformats-officedocument.presentationml.tags+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_TEMPLATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_TEMPLATE: \"application/vnd.openxmlformats-officedocument.presentationml.template\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_TEMPLATE_MAIN_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_TEMPLATE_MAIN_XML: \"application/vnd.openxmlformats-officedocument.presentationml.template.main+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_VIEWPROPS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_VIEWPROPS_XML: \"application/vnd.openxmlformats-officedocument.presentationml.viewProps+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_CALCCHAIN_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_CALCCHAIN_XML: \"application/vnd.openxmlformats-officedocument.spreadsheetml.calcChain+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_CHARTSHEET_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_CHARTSHEET_XML: \"application/vnd.openxmlformats-officedocument.spreadsheetml.chartsheet+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_COMMENTS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_COMMENTS_XML: \"application/vnd.openxmlformats-officedocument.spreadsheetml.comments+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_CONNECTIONS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_CONNECTIONS_XML: \"application/vnd.openxmlformats-officedocument.spreadsheetml.connections+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_DIALOGSHEET_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_DIALOGSHEET_XML: \"application/vnd.openxmlformats-officedocument.spreadsheetml.dialogsheet+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_EXTERNALLINK_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_EXTERNALLINK_XML: \"application/vnd.openxmlformats-officedocument.spreadsheetml.externalLink+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_PIVOTCACHEDEFINITION_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_PIVOTCACHEDEFINITION_XML: \"application/vnd.openxmlformats-officedocument.spreadsheetml.pivotCacheDefinition+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_PIVOTCACHERECORDS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_PIVOTCACHERECORDS_XML: \"application/vnd.openxmlformats-officedocument.spreadsheetml.pivotCacheRecords+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_PIVOTTABLE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_PIVOTTABLE_XML: \"application/vnd.openxmlformats-officedocument.spreadsheetml.pivotTable+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_QUERYTABLE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_QUERYTABLE_XML: \"application/vnd.openxmlformats-officedocument.spreadsheetml.queryTable+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_REVISIONHEADERS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_REVISIONHEADERS_XML: \"application/vnd.openxmlformats-officedocument.spreadsheetml.revisionHeaders+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_REVISIONLOG_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_REVISIONLOG_XML: \"application/vnd.openxmlformats-officedocument.spreadsheetml.revisionLog+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_SHAREDSTRINGS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_SHAREDSTRINGS_XML: \"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_SHEET",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_SHEET: \"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_SHEETMETADATA_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_SHEETMETADATA_XML: \"application/vnd.openxmlformats-officedocument.spreadsheetml.sheetMetadata+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_SHEET_MAIN_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_SHEET_MAIN_XML: \"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_STYLES_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_STYLES_XML: \"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_TABLESINGLECELLS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_TABLESINGLECELLS_XML: \"application/vnd.openxmlformats-officedocument.spreadsheetml.tableSingleCells+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_TABLE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_TABLE_XML: \"application/vnd.openxmlformats-officedocument.spreadsheetml.table+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_TEMPLATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_TEMPLATE: \"application/vnd.openxmlformats-officedocument.spreadsheetml.template\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_TEMPLATE_MAIN_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_TEMPLATE_MAIN_XML: \"application/vnd.openxmlformats-officedocument.spreadsheetml.template.main+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_USERNAMES_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_USERNAMES_XML: \"application/vnd.openxmlformats-officedocument.spreadsheetml.userNames+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_VOLATILEDEPENDENCIES_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_VOLATILEDEPENDENCIES_XML: \"application/vnd.openxmlformats-officedocument.spreadsheetml.volatileDependencies+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_WORKSHEET_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_WORKSHEET_XML: \"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_THEMEOVERRIDE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_THEMEOVERRIDE_XML: \"application/vnd.openxmlformats-officedocument.themeOverride+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_THEME_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_THEME_XML: \"application/vnd.openxmlformats-officedocument.theme+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_VMLDRAWING",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_VMLDRAWING: \"application/vnd.openxmlformats-officedocument.vmlDrawing\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_COMMENTS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_COMMENTS_XML: \"application/vnd.openxmlformats-officedocument.wordprocessingml.comments+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_DOCUMENT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_DOCUMENT: \"application/vnd.openxmlformats-officedocument.wordprocessingml.document\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_DOCUMENT_GLOSSARY_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_DOCUMENT_GLOSSARY_XML: \"application/vnd.openxmlformats-officedocument.wordprocessingml.document.glossary+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_DOCUMENT_MAIN_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_DOCUMENT_MAIN_XML: \"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_ENDNOTES_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_ENDNOTES_XML: \"application/vnd.openxmlformats-officedocument.wordprocessingml.endnotes+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_FONTTABLE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_FONTTABLE_XML: \"application/vnd.openxmlformats-officedocument.wordprocessingml.fontTable+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_FOOTER_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_FOOTER_XML: \"application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_FOOTNOTES_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_FOOTNOTES_XML: \"application/vnd.openxmlformats-officedocument.wordprocessingml.footnotes+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_NUMBERING_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_NUMBERING_XML: \"application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_SETTINGS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_SETTINGS_XML: \"application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_STYLES_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_STYLES_XML: \"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_TEMPLATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_TEMPLATE: \"application/vnd.openxmlformats-officedocument.wordprocessingml.template\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_TEMPLATE_MAIN_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_TEMPLATE_MAIN_XML: \"application/vnd.openxmlformats-officedocument.wordprocessingml.template.main+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_WEBSETTINGS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_WEBSETTINGS_XML: \"application/vnd.openxmlformats-officedocument.wordprocessingml.webSettings+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_PACKAGE_CORE_PROPERTIES_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_PACKAGE_CORE_PROPERTIES_XML: \"application/vnd.openxmlformats-package.core-properties+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_PACKAGE_DIGITAL_SIGNATURE_XMLSIGNATURE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_PACKAGE_DIGITAL_SIGNATURE_XMLSIGNATURE_XML: \"application/vnd.openxmlformats-package.digital-signature-xmlsignature+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXMLFORMATS_PACKAGE_RELATIONSHIPS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXMLFORMATS_PACKAGE_RELATIONSHIPS_XML: \"application/vnd.openxmlformats-package.relationships+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXML_DOCUMENT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXML_DOCUMENT: \"application/vnd.openxmlformats-officedocument.wordprocessingml.document\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXML_PRESENTATION",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXML_PRESENTATION: \"application/vnd.openxmlformats-officedocument.presentationml.presentation\";"
                },
                {
                    "name": "APPLICATION_VND_OPENXML_SHEET",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OPENXML_SHEET: \"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\";"
                },
                {
                    "name": "APPLICATION_VND_ORACLE_RESOURCE_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ORACLE_RESOURCE_JSON: \"application/vnd.oracle.resource+json\";"
                },
                {
                    "name": "APPLICATION_VND_ORANGE_INDATA",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ORANGE_INDATA: \"application/vnd.orange.indata\";"
                },
                {
                    "name": "APPLICATION_VND_OSA_NETDEPLOY",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OSA_NETDEPLOY: \"application/vnd.osa.netdeploy\";"
                },
                {
                    "name": "APPLICATION_VND_OSGEO_MAPGUIDE_PACKAGE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OSGEO_MAPGUIDE_PACKAGE: \"application/vnd.osgeo.mapguide.package\";"
                },
                {
                    "name": "APPLICATION_VND_OSGI_BUNDLE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OSGI_BUNDLE: \"application/vnd.osgi.bundle\";"
                },
                {
                    "name": "APPLICATION_VND_OSGI_DP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OSGI_DP: \"application/vnd.osgi.dp\";"
                },
                {
                    "name": "APPLICATION_VND_OSGI_SUBSYSTEM",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OSGI_SUBSYSTEM: \"application/vnd.osgi.subsystem\";"
                },
                {
                    "name": "APPLICATION_VND_OTPS_CT_KIP_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OTPS_CT_KIP_XML: \"application/vnd.otps.ct-kip+xml\";"
                },
                {
                    "name": "APPLICATION_VND_OXLI_COUNTGRAPH",
                    "type": "variable",
                    "doc": "APPLICATION_VND_OXLI_COUNTGRAPH: \"application/vnd.oxli.countgraph\";"
                },
                {
                    "name": "APPLICATION_VND_PAGERDUTY_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_PAGERDUTY_JSON: \"application/vnd.pagerduty+json\";"
                },
                {
                    "name": "APPLICATION_VND_PALM",
                    "type": "variable",
                    "doc": "APPLICATION_VND_PALM: \"application/vnd.palm\";"
                },
                {
                    "name": "APPLICATION_VND_PANOPLY",
                    "type": "variable",
                    "doc": "APPLICATION_VND_PANOPLY: \"application/vnd.panoply\";"
                },
                {
                    "name": "APPLICATION_VND_PAOS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_PAOS_XML: \"application/vnd.paos.xml\";"
                },
                {
                    "name": "APPLICATION_VND_PATENTDIVE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_PATENTDIVE: \"application/vnd.patentdive\";"
                },
                {
                    "name": "APPLICATION_VND_PATIENTECOMMSDOC",
                    "type": "variable",
                    "doc": "APPLICATION_VND_PATIENTECOMMSDOC: \"application/vnd.patientecommsdoc\";"
                },
                {
                    "name": "APPLICATION_VND_PAWAAFILE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_PAWAAFILE: \"application/vnd.pawaafile\";"
                },
                {
                    "name": "APPLICATION_VND_PCOS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_PCOS: \"application/vnd.pcos\";"
                },
                {
                    "name": "APPLICATION_VND_PG_FORMAT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_PG_FORMAT: \"application/vnd.pg.format\";"
                },
                {
                    "name": "APPLICATION_VND_PG_OSASLI",
                    "type": "variable",
                    "doc": "APPLICATION_VND_PG_OSASLI: \"application/vnd.pg.osasli\";"
                },
                {
                    "name": "APPLICATION_VND_PHONECOM_MMC_WBXML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_PHONECOM_MMC_WBXML: \"application/vnd.phonecom.mmc-wbxml\";"
                },
                {
                    "name": "APPLICATION_VND_PIACCESS_APPLICATION_LICENCE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_PIACCESS_APPLICATION_LICENCE: \"application/vnd.piaccess.application-licence\";"
                },
                {
                    "name": "APPLICATION_VND_PICSEL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_PICSEL: \"application/vnd.picsel\";"
                },
                {
                    "name": "APPLICATION_VND_PMI_WIDGET",
                    "type": "variable",
                    "doc": "APPLICATION_VND_PMI_WIDGET: \"application/vnd.pmi.widget\";"
                },
                {
                    "name": "APPLICATION_VND_POCKETLEARN",
                    "type": "variable",
                    "doc": "APPLICATION_VND_POCKETLEARN: \"application/vnd.pocketlearn\";"
                },
                {
                    "name": "APPLICATION_VND_POC_GROUP_ADVERTISEMENT_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_POC_GROUP_ADVERTISEMENT_XML: \"application/vnd.poc.group-advertisement+xml\";"
                },
                {
                    "name": "APPLICATION_VND_POWERBUILDER6",
                    "type": "variable",
                    "doc": "APPLICATION_VND_POWERBUILDER6: \"application/vnd.powerbuilder6\";"
                },
                {
                    "name": "APPLICATION_VND_POWERBUILDER6_S",
                    "type": "variable",
                    "doc": "APPLICATION_VND_POWERBUILDER6_S: \"application/vnd.powerbuilder6-s\";"
                },
                {
                    "name": "APPLICATION_VND_POWERBUILDER7",
                    "type": "variable",
                    "doc": "APPLICATION_VND_POWERBUILDER7: \"application/vnd.powerbuilder7\";"
                },
                {
                    "name": "APPLICATION_VND_POWERBUILDER75",
                    "type": "variable",
                    "doc": "APPLICATION_VND_POWERBUILDER75: \"application/vnd.powerbuilder75\";"
                },
                {
                    "name": "APPLICATION_VND_POWERBUILDER75_S",
                    "type": "variable",
                    "doc": "APPLICATION_VND_POWERBUILDER75_S: \"application/vnd.powerbuilder75-s\";"
                },
                {
                    "name": "APPLICATION_VND_POWERBUILDER7_S",
                    "type": "variable",
                    "doc": "APPLICATION_VND_POWERBUILDER7_S: \"application/vnd.powerbuilder7-s\";"
                },
                {
                    "name": "APPLICATION_VND_PREMINET",
                    "type": "variable",
                    "doc": "APPLICATION_VND_PREMINET: \"application/vnd.preminet\";"
                },
                {
                    "name": "APPLICATION_VND_PREVIEWSYSTEMS_BOX",
                    "type": "variable",
                    "doc": "APPLICATION_VND_PREVIEWSYSTEMS_BOX: \"application/vnd.previewsystems.box\";"
                },
                {
                    "name": "APPLICATION_VND_PROTEUS_MAGAZINE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_PROTEUS_MAGAZINE: \"application/vnd.proteus.magazine\";"
                },
                {
                    "name": "APPLICATION_VND_PSFS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_PSFS: \"application/vnd.psfs\";"
                },
                {
                    "name": "APPLICATION_VND_PT_MUNDUSMUNDI",
                    "type": "variable",
                    "doc": "APPLICATION_VND_PT_MUNDUSMUNDI: \"application/vnd.pt.mundusmundi\";"
                },
                {
                    "name": "APPLICATION_VND_PUBLISHARE_DELTA_TREE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_PUBLISHARE_DELTA_TREE: \"application/vnd.publishare-delta-tree\";"
                },
                {
                    "name": "APPLICATION_VND_PVI_PTID1",
                    "type": "variable",
                    "doc": "APPLICATION_VND_PVI_PTID1: \"application/vnd.pvi.ptid1\";"
                },
                {
                    "name": "APPLICATION_VND_PWG_MULTIPLEXED",
                    "type": "variable",
                    "doc": "APPLICATION_VND_PWG_MULTIPLEXED: \"application/vnd.pwg-multiplexed\";"
                },
                {
                    "name": "APPLICATION_VND_PWG_XHTML_PRINT_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_PWG_XHTML_PRINT_XML: \"application/vnd.pwg-xhtml-print+xml\";"
                },
                {
                    "name": "APPLICATION_VND_QUALCOMM_BREW_APP_RES",
                    "type": "variable",
                    "doc": "APPLICATION_VND_QUALCOMM_BREW_APP_RES: \"application/vnd.qualcomm.brew-app-res\";"
                },
                {
                    "name": "APPLICATION_VND_QUARANTAINENET",
                    "type": "variable",
                    "doc": "APPLICATION_VND_QUARANTAINENET: \"application/vnd.quarantainenet\";"
                },
                {
                    "name": "APPLICATION_VND_QUARK_QUARKXPRESS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_QUARK_QUARKXPRESS: \"application/vnd.Quark.QuarkXPress\";"
                },
                {
                    "name": "APPLICATION_VND_QUOBJECT_QUOXDOCUMENT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_QUOBJECT_QUOXDOCUMENT: \"application/vnd.quobject-quoxdocument\";"
                },
                {
                    "name": "APPLICATION_VND_RADISYS_MOML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_RADISYS_MOML_XML: \"application/vnd.radisys.moml+xml\";"
                },
                {
                    "name": "APPLICATION_VND_RADISYS_MSML_AUDIT_CONF_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_RADISYS_MSML_AUDIT_CONF_XML: \"application/vnd.radisys.msml-audit-conf+xml\";"
                },
                {
                    "name": "APPLICATION_VND_RADISYS_MSML_AUDIT_CONN_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_RADISYS_MSML_AUDIT_CONN_XML: \"application/vnd.radisys.msml-audit-conn+xml\";"
                },
                {
                    "name": "APPLICATION_VND_RADISYS_MSML_AUDIT_DIALOG_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_RADISYS_MSML_AUDIT_DIALOG_XML: \"application/vnd.radisys.msml-audit-dialog+xml\";"
                },
                {
                    "name": "APPLICATION_VND_RADISYS_MSML_AUDIT_STREAM_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_RADISYS_MSML_AUDIT_STREAM_XML: \"application/vnd.radisys.msml-audit-stream+xml\";"
                },
                {
                    "name": "APPLICATION_VND_RADISYS_MSML_AUDIT_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_RADISYS_MSML_AUDIT_XML: \"application/vnd.radisys.msml-audit+xml\";"
                },
                {
                    "name": "APPLICATION_VND_RADISYS_MSML_CONF_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_RADISYS_MSML_CONF_XML: \"application/vnd.radisys.msml-conf+xml\";"
                },
                {
                    "name": "APPLICATION_VND_RADISYS_MSML_DIALOG_BASE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_RADISYS_MSML_DIALOG_BASE_XML: \"application/vnd.radisys.msml-dialog-base+xml\";"
                },
                {
                    "name": "APPLICATION_VND_RADISYS_MSML_DIALOG_FAX_DETECT_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_RADISYS_MSML_DIALOG_FAX_DETECT_XML: \"application/vnd.radisys.msml-dialog-fax-detect+xml\";"
                },
                {
                    "name": "APPLICATION_VND_RADISYS_MSML_DIALOG_FAX_SENDRECV_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_RADISYS_MSML_DIALOG_FAX_SENDRECV_XML: \"application/vnd.radisys.msml-dialog-fax-sendrecv+xml\";"
                },
                {
                    "name": "APPLICATION_VND_RADISYS_MSML_DIALOG_GROUP_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_RADISYS_MSML_DIALOG_GROUP_XML: \"application/vnd.radisys.msml-dialog-group+xml\";"
                },
                {
                    "name": "APPLICATION_VND_RADISYS_MSML_DIALOG_SPEECH_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_RADISYS_MSML_DIALOG_SPEECH_XML: \"application/vnd.radisys.msml-dialog-speech+xml\";"
                },
                {
                    "name": "APPLICATION_VND_RADISYS_MSML_DIALOG_TRANSFORM_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_RADISYS_MSML_DIALOG_TRANSFORM_XML: \"application/vnd.radisys.msml-dialog-transform+xml\";"
                },
                {
                    "name": "APPLICATION_VND_RADISYS_MSML_DIALOG_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_RADISYS_MSML_DIALOG_XML: \"application/vnd.radisys.msml-dialog+xml\";"
                },
                {
                    "name": "APPLICATION_VND_RADISYS_MSML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_RADISYS_MSML_XML: \"application/vnd.radisys.msml+xml\";"
                },
                {
                    "name": "APPLICATION_VND_RAINSTOR_DATA",
                    "type": "variable",
                    "doc": "APPLICATION_VND_RAINSTOR_DATA: \"application/vnd.rainstor.data\";"
                },
                {
                    "name": "APPLICATION_VND_RAPID",
                    "type": "variable",
                    "doc": "APPLICATION_VND_RAPID: \"application/vnd.rapid\";"
                },
                {
                    "name": "APPLICATION_VND_RAR",
                    "type": "variable",
                    "doc": "APPLICATION_VND_RAR: \"application/vnd.rar\";"
                },
                {
                    "name": "APPLICATION_VND_REALVNC_BED",
                    "type": "variable",
                    "doc": "APPLICATION_VND_REALVNC_BED: \"application/vnd.realvnc.bed\";"
                },
                {
                    "name": "APPLICATION_VND_RECORDARE_MUSICXML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_RECORDARE_MUSICXML: \"application/vnd.recordare.musicxml\";"
                },
                {
                    "name": "APPLICATION_VND_RECORDARE_MUSICXML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_RECORDARE_MUSICXML_XML: \"application/vnd.recordare.musicxml+xml\";"
                },
                {
                    "name": "APPLICATION_VND_RELPIPE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_RELPIPE: \"application/vnd.relpipe\";"
                },
                {
                    "name": "APPLICATION_VND_RENLEARN_RLPRINT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_RENLEARN_RLPRINT: \"application/vnd.RenLearn.rlprint\";"
                },
                {
                    "name": "APPLICATION_VND_RESILIENT_LOGIC",
                    "type": "variable",
                    "doc": "APPLICATION_VND_RESILIENT_LOGIC: \"application/vnd.resilient.logic\";"
                },
                {
                    "name": "APPLICATION_VND_RESTFUL_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_RESTFUL_JSON: \"application/vnd.restful+json\";"
                },
                {
                    "name": "APPLICATION_VND_RIG_CRYPTONOTE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_RIG_CRYPTONOTE: \"application/vnd.rig.cryptonote\";"
                },
                {
                    "name": "APPLICATION_VND_RIM_COD",
                    "type": "variable",
                    "doc": "APPLICATION_VND_RIM_COD: \"application/vnd.rim.cod\";"
                },
                {
                    "name": "APPLICATION_VND_ROUTE66_LINK66_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ROUTE66_LINK66_XML: \"application/vnd.route66.link66+xml\";"
                },
                {
                    "name": "APPLICATION_VND_RS_274X",
                    "type": "variable",
                    "doc": "APPLICATION_VND_RS_274X: \"application/vnd.rs-274x\";"
                },
                {
                    "name": "APPLICATION_VND_RUCKUS_DOWNLOAD",
                    "type": "variable",
                    "doc": "APPLICATION_VND_RUCKUS_DOWNLOAD: \"application/vnd.ruckus.download\";"
                },
                {
                    "name": "APPLICATION_VND_S3SMS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_S3SMS: \"application/vnd.s3sms\";"
                },
                {
                    "name": "APPLICATION_VND_SAILINGTRACKER_TRACK",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SAILINGTRACKER_TRACK: \"application/vnd.sailingtracker.track\";"
                },
                {
                    "name": "APPLICATION_VND_SAR",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SAR: \"application/vnd.sar\";"
                },
                {
                    "name": "APPLICATION_VND_SBM_CID",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SBM_CID: \"application/vnd.sbm.cid\";"
                },
                {
                    "name": "APPLICATION_VND_SBM_MID2",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SBM_MID2: \"application/vnd.sbm.mid2\";"
                },
                {
                    "name": "APPLICATION_VND_SCRIBUS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SCRIBUS: \"application/vnd.scribus\";"
                },
                {
                    "name": "APPLICATION_VND_SEALEDMEDIA_SOFTSEAL_HTML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SEALEDMEDIA_SOFTSEAL_HTML: \"application/vnd.sealedmedia.softseal.html\";"
                },
                {
                    "name": "APPLICATION_VND_SEALEDMEDIA_SOFTSEAL_PDF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SEALEDMEDIA_SOFTSEAL_PDF: \"application/vnd.sealedmedia.softseal.pdf\";"
                },
                {
                    "name": "APPLICATION_VND_SEALED_3DF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SEALED_3DF: \"application/vnd.sealed.3df\";"
                },
                {
                    "name": "APPLICATION_VND_SEALED_CSF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SEALED_CSF: \"application/vnd.sealed.csf\";"
                },
                {
                    "name": "APPLICATION_VND_SEALED_DOC",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SEALED_DOC: \"application/vnd.sealed.doc\";"
                },
                {
                    "name": "APPLICATION_VND_SEALED_EML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SEALED_EML: \"application/vnd.sealed.eml\";"
                },
                {
                    "name": "APPLICATION_VND_SEALED_MHT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SEALED_MHT: \"application/vnd.sealed.mht\";"
                },
                {
                    "name": "APPLICATION_VND_SEALED_NET",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SEALED_NET: \"application/vnd.sealed.net\";"
                },
                {
                    "name": "APPLICATION_VND_SEALED_PPT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SEALED_PPT: \"application/vnd.sealed.ppt\";"
                },
                {
                    "name": "APPLICATION_VND_SEALED_TIFF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SEALED_TIFF: \"application/vnd.sealed.tiff\";"
                },
                {
                    "name": "APPLICATION_VND_SEALED_XLS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SEALED_XLS: \"application/vnd.sealed.xls\";"
                },
                {
                    "name": "APPLICATION_VND_SEEMAIL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SEEMAIL: \"application/vnd.seemail\";"
                },
                {
                    "name": "APPLICATION_VND_SEIS_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SEIS_JSON: \"application/vnd.seis+json\";"
                },
                {
                    "name": "APPLICATION_VND_SEMA",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SEMA: \"application/vnd.sema\";"
                },
                {
                    "name": "APPLICATION_VND_SEMD",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SEMD: \"application/vnd.semd\";"
                },
                {
                    "name": "APPLICATION_VND_SEMF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SEMF: \"application/vnd.semf\";"
                },
                {
                    "name": "APPLICATION_VND_SHADE_SAVE_FILE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SHADE_SAVE_FILE: \"application/vnd.shade-save-file\";"
                },
                {
                    "name": "APPLICATION_VND_SHANA_INFORMED_FORMDATA",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SHANA_INFORMED_FORMDATA: \"application/vnd.shana.informed.formdata\";"
                },
                {
                    "name": "APPLICATION_VND_SHANA_INFORMED_FORMTEMPLATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SHANA_INFORMED_FORMTEMPLATE: \"application/vnd.shana.informed.formtemplate\";"
                },
                {
                    "name": "APPLICATION_VND_SHANA_INFORMED_INTERCHANGE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SHANA_INFORMED_INTERCHANGE: \"application/vnd.shana.informed.interchange\";"
                },
                {
                    "name": "APPLICATION_VND_SHANA_INFORMED_PACKAGE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SHANA_INFORMED_PACKAGE: \"application/vnd.shana.informed.package\";"
                },
                {
                    "name": "APPLICATION_VND_SHOOTPROOF_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SHOOTPROOF_JSON: \"application/vnd.shootproof+json\";"
                },
                {
                    "name": "APPLICATION_VND_SHOPKICK_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SHOPKICK_JSON: \"application/vnd.shopkick+json\";"
                },
                {
                    "name": "APPLICATION_VND_SHP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SHP: \"application/vnd.shp\";"
                },
                {
                    "name": "APPLICATION_VND_SHX",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SHX: \"application/vnd.shx\";"
                },
                {
                    "name": "APPLICATION_VND_SIGROK_SESSION",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SIGROK_SESSION: \"application/vnd.sigrok.session\";"
                },
                {
                    "name": "APPLICATION_VND_SIMTECH_MINDMAPPER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SIMTECH_MINDMAPPER: \"application/vnd.SimTech-MindMapper\";"
                },
                {
                    "name": "APPLICATION_VND_SIREN_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SIREN_JSON: \"application/vnd.siren+json\";"
                },
                {
                    "name": "APPLICATION_VND_SMAF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SMAF: \"application/vnd.smaf\";"
                },
                {
                    "name": "APPLICATION_VND_SMART_NOTEBOOK",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SMART_NOTEBOOK: \"application/vnd.smart.notebook\";"
                },
                {
                    "name": "APPLICATION_VND_SMART_TEACHER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SMART_TEACHER: \"application/vnd.smart.teacher\";"
                },
                {
                    "name": "APPLICATION_VND_SMINTIO_PORTALS_ARCHIVE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SMINTIO_PORTALS_ARCHIVE: \"application/vnd.smintio.portals.archive\";"
                },
                {
                    "name": "APPLICATION_VND_SNESDEV_PAGE_TABLE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SNESDEV_PAGE_TABLE: \"application/vnd.snesdev-page-table\";"
                },
                {
                    "name": "APPLICATION_VND_SOFTWARE602_FILLER_FORM_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SOFTWARE602_FILLER_FORM_XML: \"application/vnd.software602.filler.form+xml\";"
                },
                {
                    "name": "APPLICATION_VND_SOFTWARE602_FILLER_FORM_XML_ZIP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SOFTWARE602_FILLER_FORM_XML_ZIP: \"application/vnd.software602.filler.form-xml-zip\";"
                },
                {
                    "name": "APPLICATION_VND_SOLENT_SDKM_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SOLENT_SDKM_XML: \"application/vnd.solent.sdkm+xml\";"
                },
                {
                    "name": "APPLICATION_VND_SPOTFIRE_DXP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SPOTFIRE_DXP: \"application/vnd.spotfire.dxp\";"
                },
                {
                    "name": "APPLICATION_VND_SPOTFIRE_SFS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SPOTFIRE_SFS: \"application/vnd.spotfire.sfs\";"
                },
                {
                    "name": "APPLICATION_VND_SQLITE3",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SQLITE3: \"application/vnd.sqlite3\";"
                },
                {
                    "name": "APPLICATION_VND_SSS_COD",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SSS_COD: \"application/vnd.sss-cod\";"
                },
                {
                    "name": "APPLICATION_VND_SSS_DTF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SSS_DTF: \"application/vnd.sss-dtf\";"
                },
                {
                    "name": "APPLICATION_VND_SSS_NTF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SSS_NTF: \"application/vnd.sss-ntf\";"
                },
                {
                    "name": "APPLICATION_VND_STARDIVISION_CALC",
                    "type": "variable",
                    "doc": "APPLICATION_VND_STARDIVISION_CALC: \"application/vnd.stardivision.calc\";"
                },
                {
                    "name": "APPLICATION_VND_STARDIVISION_CHART",
                    "type": "variable",
                    "doc": "APPLICATION_VND_STARDIVISION_CHART: \"application/vnd.stardivision.chart\";"
                },
                {
                    "name": "APPLICATION_VND_STARDIVISION_DRAW",
                    "type": "variable",
                    "doc": "APPLICATION_VND_STARDIVISION_DRAW: \"application/vnd.stardivision.draw\";"
                },
                {
                    "name": "APPLICATION_VND_STARDIVISION_IMPRESS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_STARDIVISION_IMPRESS: \"application/vnd.stardivision.impress\";"
                },
                {
                    "name": "APPLICATION_VND_STARDIVISION_MATH",
                    "type": "variable",
                    "doc": "APPLICATION_VND_STARDIVISION_MATH: \"application/vnd.stardivision.math\";"
                },
                {
                    "name": "APPLICATION_VND_STARDIVISION_WRITER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_STARDIVISION_WRITER: \"application/vnd.stardivision.writer\";"
                },
                {
                    "name": "APPLICATION_VND_STARDIVISION_WRITER_GLOBAL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_STARDIVISION_WRITER_GLOBAL: \"application/vnd.stardivision.writer-global\";"
                },
                {
                    "name": "APPLICATION_VND_STEPMANIA_PACKAGE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_STEPMANIA_PACKAGE: \"application/vnd.stepmania.package\";"
                },
                {
                    "name": "APPLICATION_VND_STEPMANIA_STEPCHART",
                    "type": "variable",
                    "doc": "APPLICATION_VND_STEPMANIA_STEPCHART: \"application/vnd.stepmania.stepchart\";"
                },
                {
                    "name": "APPLICATION_VND_STREET_STREAM",
                    "type": "variable",
                    "doc": "APPLICATION_VND_STREET_STREAM: \"application/vnd.street-stream\";"
                },
                {
                    "name": "APPLICATION_VND_SUN_WADL_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SUN_WADL_XML: \"application/vnd.sun.wadl+xml\";"
                },
                {
                    "name": "APPLICATION_VND_SUN_XML_CALC",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SUN_XML_CALC: \"application/vnd.sun.xml.calc\";"
                },
                {
                    "name": "APPLICATION_VND_SUN_XML_CALC_TEMPLATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SUN_XML_CALC_TEMPLATE: \"application/vnd.sun.xml.calc.template\";"
                },
                {
                    "name": "APPLICATION_VND_SUN_XML_DRAW",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SUN_XML_DRAW: \"application/vnd.sun.xml.draw\";"
                },
                {
                    "name": "APPLICATION_VND_SUN_XML_DRAW_TEMPLATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SUN_XML_DRAW_TEMPLATE: \"application/vnd.sun.xml.draw.template\";"
                },
                {
                    "name": "APPLICATION_VND_SUN_XML_IMPRESS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SUN_XML_IMPRESS: \"application/vnd.sun.xml.impress\";"
                },
                {
                    "name": "APPLICATION_VND_SUN_XML_IMPRESS_TEMPLATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SUN_XML_IMPRESS_TEMPLATE: \"application/vnd.sun.xml.impress.template\";"
                },
                {
                    "name": "APPLICATION_VND_SUN_XML_WRITER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SUN_XML_WRITER: \"application/vnd.sun.xml.writer\";"
                },
                {
                    "name": "APPLICATION_VND_SUN_XML_WRITER_GLOBAL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SUN_XML_WRITER_GLOBAL: \"application/vnd.sun.xml.writer.global\";"
                },
                {
                    "name": "APPLICATION_VND_SUN_XML_WRITER_TEMPLATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SUN_XML_WRITER_TEMPLATE: \"application/vnd.sun.xml.writer.template\";"
                },
                {
                    "name": "APPLICATION_VND_SUS_CALENDAR",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SUS_CALENDAR: \"application/vnd.sus-calendar\";"
                },
                {
                    "name": "APPLICATION_VND_SVD",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SVD: \"application/vnd.svd\";"
                },
                {
                    "name": "APPLICATION_VND_SWIFTVIEW_ICS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SWIFTVIEW_ICS: \"application/vnd.swiftview-ics\";"
                },
                {
                    "name": "APPLICATION_VND_SYBYL_MOL2",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SYBYL_MOL2: \"application/vnd.sybyl.mol2\";"
                },
                {
                    "name": "APPLICATION_VND_SYCLE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SYCLE_XML: \"application/vnd.sycle+xml\";"
                },
                {
                    "name": "APPLICATION_VND_SYFT_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SYFT_JSON: \"application/vnd.syft+json\";"
                },
                {
                    "name": "APPLICATION_VND_SYMBIAN_INSTALL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SYMBIAN_INSTALL: \"application/vnd.symbian.install\";"
                },
                {
                    "name": "APPLICATION_VND_SYNCML_DMDDF_WBXML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SYNCML_DMDDF_WBXML: \"application/vnd.syncml.dmddf+wbxml\";"
                },
                {
                    "name": "APPLICATION_VND_SYNCML_DMDDF_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SYNCML_DMDDF_XML: \"application/vnd.syncml.dmddf+xml\";"
                },
                {
                    "name": "APPLICATION_VND_SYNCML_DMTNDS_WBXML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SYNCML_DMTNDS_WBXML: \"application/vnd.syncml.dmtnds+wbxml\";"
                },
                {
                    "name": "APPLICATION_VND_SYNCML_DMTNDS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SYNCML_DMTNDS_XML: \"application/vnd.syncml.dmtnds+xml\";"
                },
                {
                    "name": "APPLICATION_VND_SYNCML_DM_NOTIFICATION",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SYNCML_DM_NOTIFICATION: \"application/vnd.syncml.dm.notification\";"
                },
                {
                    "name": "APPLICATION_VND_SYNCML_DM_WBXML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SYNCML_DM_WBXML: \"application/vnd.syncml.dm+wbxml\";"
                },
                {
                    "name": "APPLICATION_VND_SYNCML_DM_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SYNCML_DM_XML: \"application/vnd.syncml.dm+xml\";"
                },
                {
                    "name": "APPLICATION_VND_SYNCML_DS_NOTIFICATION",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SYNCML_DS_NOTIFICATION: \"application/vnd.syncml.ds.notification\";"
                },
                {
                    "name": "APPLICATION_VND_SYNCML_NOTIFICATION",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SYNCML_NOTIFICATION: \"application/vnd.syncml.notification\";"
                },
                {
                    "name": "APPLICATION_VND_SYNCML_WBXML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SYNCML_WBXML: \"application/vnd.syncml+wbxml\";"
                },
                {
                    "name": "APPLICATION_VND_SYNCML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_SYNCML_XML: \"application/vnd.syncml+xml\";"
                },
                {
                    "name": "APPLICATION_VND_TABLESCHEMA_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_TABLESCHEMA_JSON: \"application/vnd.tableschema+json\";"
                },
                {
                    "name": "APPLICATION_VND_TAO_INTENT_MODULE_ARCHIVE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_TAO_INTENT_MODULE_ARCHIVE: \"application/vnd.tao.intent-module-archive\";"
                },
                {
                    "name": "APPLICATION_VND_TCPDUMP_PCAP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_TCPDUMP_PCAP: \"application/vnd.tcpdump.pcap\";"
                },
                {
                    "name": "APPLICATION_VND_THINK_CELL_PPTTC_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_THINK_CELL_PPTTC_JSON: \"application/vnd.think-cell.ppttc+json\";"
                },
                {
                    "name": "APPLICATION_VND_TMD_MEDIAFLEX_API_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_TMD_MEDIAFLEX_API_XML: \"application/vnd.tmd.mediaflex.api+xml\";"
                },
                {
                    "name": "APPLICATION_VND_TML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_TML: \"application/vnd.tml\";"
                },
                {
                    "name": "APPLICATION_VND_TMOBILE_LIVETV",
                    "type": "variable",
                    "doc": "APPLICATION_VND_TMOBILE_LIVETV: \"application/vnd.tmobile-livetv\";"
                },
                {
                    "name": "APPLICATION_VND_TRID_TPT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_TRID_TPT: \"application/vnd.trid.tpt\";"
                },
                {
                    "name": "APPLICATION_VND_TRISCAPE_MXS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_TRISCAPE_MXS: \"application/vnd.triscape.mxs\";"
                },
                {
                    "name": "APPLICATION_VND_TRI_ONESOURCE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_TRI_ONESOURCE: \"application/vnd.tri.onesource\";"
                },
                {
                    "name": "APPLICATION_VND_TRUEAPP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_TRUEAPP: \"application/vnd.trueapp\";"
                },
                {
                    "name": "APPLICATION_VND_TRUEDOC",
                    "type": "variable",
                    "doc": "APPLICATION_VND_TRUEDOC: \"application/vnd.truedoc\";"
                },
                {
                    "name": "APPLICATION_VND_TVE_TRIGGER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_TVE_TRIGGER: \"application/vnd.tve-trigger\";"
                },
                {
                    "name": "APPLICATION_VND_UBISOFT_WEBPLAYER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_UBISOFT_WEBPLAYER: \"application/vnd.ubisoft.webplayer\";"
                },
                {
                    "name": "APPLICATION_VND_UFDL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_UFDL: \"application/vnd.ufdl\";"
                },
                {
                    "name": "APPLICATION_VND_UIQ_THEME",
                    "type": "variable",
                    "doc": "APPLICATION_VND_UIQ_THEME: \"application/vnd.uiq.theme\";"
                },
                {
                    "name": "APPLICATION_VND_UMAJIN",
                    "type": "variable",
                    "doc": "APPLICATION_VND_UMAJIN: \"application/vnd.umajin\";"
                },
                {
                    "name": "APPLICATION_VND_UNITY",
                    "type": "variable",
                    "doc": "APPLICATION_VND_UNITY: \"application/vnd.unity\";"
                },
                {
                    "name": "APPLICATION_VND_UOML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_UOML_XML: \"application/vnd.uoml+xml\";"
                },
                {
                    "name": "APPLICATION_VND_UPLANET_ALERT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_UPLANET_ALERT: \"application/vnd.uplanet.alert\";"
                },
                {
                    "name": "APPLICATION_VND_UPLANET_ALERT_WBXML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_UPLANET_ALERT_WBXML: \"application/vnd.uplanet.alert-wbxml\";"
                },
                {
                    "name": "APPLICATION_VND_UPLANET_BEARER_CHOICE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_UPLANET_BEARER_CHOICE: \"application/vnd.uplanet.bearer-choice\";"
                },
                {
                    "name": "APPLICATION_VND_UPLANET_BEARER_CHOICE_WBXML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_UPLANET_BEARER_CHOICE_WBXML: \"application/vnd.uplanet.bearer-choice-wbxml\";"
                },
                {
                    "name": "APPLICATION_VND_UPLANET_CACHEOP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_UPLANET_CACHEOP: \"application/vnd.uplanet.cacheop\";"
                },
                {
                    "name": "APPLICATION_VND_UPLANET_CACHEOP_WBXML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_UPLANET_CACHEOP_WBXML: \"application/vnd.uplanet.cacheop-wbxml\";"
                },
                {
                    "name": "APPLICATION_VND_UPLANET_CHANNEL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_UPLANET_CHANNEL: \"application/vnd.uplanet.channel\";"
                },
                {
                    "name": "APPLICATION_VND_UPLANET_CHANNEL_WBXML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_UPLANET_CHANNEL_WBXML: \"application/vnd.uplanet.channel-wbxml\";"
                },
                {
                    "name": "APPLICATION_VND_UPLANET_LIST",
                    "type": "variable",
                    "doc": "APPLICATION_VND_UPLANET_LIST: \"application/vnd.uplanet.list\";"
                },
                {
                    "name": "APPLICATION_VND_UPLANET_LISTCMD",
                    "type": "variable",
                    "doc": "APPLICATION_VND_UPLANET_LISTCMD: \"application/vnd.uplanet.listcmd\";"
                },
                {
                    "name": "APPLICATION_VND_UPLANET_LISTCMD_WBXML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_UPLANET_LISTCMD_WBXML: \"application/vnd.uplanet.listcmd-wbxml\";"
                },
                {
                    "name": "APPLICATION_VND_UPLANET_LIST_WBXML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_UPLANET_LIST_WBXML: \"application/vnd.uplanet.list-wbxml\";"
                },
                {
                    "name": "APPLICATION_VND_UPLANET_PROVISIONING_STATUS_URI",
                    "type": "variable",
                    "doc": "APPLICATION_VND_UPLANET_PROVISIONING_STATUS_URI: \"application/vnd.uplanet.provisioning-status-uri\";"
                },
                {
                    "name": "APPLICATION_VND_UPLANET_SIGNAL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_UPLANET_SIGNAL: \"application/vnd.uplanet.signal\";"
                },
                {
                    "name": "APPLICATION_VND_URI_MAP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_URI_MAP: \"application/vnd.uri-map\";"
                },
                {
                    "name": "APPLICATION_VND_VALVE_SOURCE_MATERIAL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_VALVE_SOURCE_MATERIAL: \"application/vnd.valve.source.material\";"
                },
                {
                    "name": "APPLICATION_VND_VCX",
                    "type": "variable",
                    "doc": "APPLICATION_VND_VCX: \"application/vnd.vcx\";"
                },
                {
                    "name": "APPLICATION_VND_VD_STUDY",
                    "type": "variable",
                    "doc": "APPLICATION_VND_VD_STUDY: \"application/vnd.vd-study\";"
                },
                {
                    "name": "APPLICATION_VND_VECTORWORKS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_VECTORWORKS: \"application/vnd.vectorworks\";"
                },
                {
                    "name": "APPLICATION_VND_VEL_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_VEL_JSON: \"application/vnd.vel+json\";"
                },
                {
                    "name": "APPLICATION_VND_VERIMATRIX_VCAS",
                    "type": "variable",
                    "doc": "APPLICATION_VND_VERIMATRIX_VCAS: \"application/vnd.verimatrix.vcas\";"
                },
                {
                    "name": "APPLICATION_VND_VERITONE_AION_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_VERITONE_AION_JSON: \"application/vnd.veritone.aion+json\";"
                },
                {
                    "name": "APPLICATION_VND_VERYANT_THIN",
                    "type": "variable",
                    "doc": "APPLICATION_VND_VERYANT_THIN: \"application/vnd.veryant.thin\";"
                },
                {
                    "name": "APPLICATION_VND_VES_ENCRYPTED",
                    "type": "variable",
                    "doc": "APPLICATION_VND_VES_ENCRYPTED: \"application/vnd.ves.encrypted\";"
                },
                {
                    "name": "APPLICATION_VND_VIDSOFT_VIDCONFERENCE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_VIDSOFT_VIDCONFERENCE: \"application/vnd.vidsoft.vidconference\";"
                },
                {
                    "name": "APPLICATION_VND_VISIO",
                    "type": "variable",
                    "doc": "APPLICATION_VND_VISIO: \"application/vnd.visio\";"
                },
                {
                    "name": "APPLICATION_VND_VISIONARY",
                    "type": "variable",
                    "doc": "APPLICATION_VND_VISIONARY: \"application/vnd.visionary\";"
                },
                {
                    "name": "APPLICATION_VND_VIVIDENCE_SCRIPTFILE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_VIVIDENCE_SCRIPTFILE: \"application/vnd.vividence.scriptfile\";"
                },
                {
                    "name": "APPLICATION_VND_VSF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_VSF: \"application/vnd.vsf\";"
                },
                {
                    "name": "APPLICATION_VND_WAP_CERT_RESPONSE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WAP_CERT_RESPONSE: \"application/vnd.wap.cert-response\";"
                },
                {
                    "name": "APPLICATION_VND_WAP_COC",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WAP_COC: \"application/vnd.wap.coc\";"
                },
                {
                    "name": "APPLICATION_VND_WAP_CONNECTIVITY_WBXML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WAP_CONNECTIVITY_WBXML: \"application/vnd.wap.connectivity-wbxml\";"
                },
                {
                    "name": "APPLICATION_VND_WAP_EMN_WBXML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WAP_EMN_WBXML: \"application/vnd.wap.emn+wbxml\";"
                },
                {
                    "name": "APPLICATION_VND_WAP_HASHED_CERTIFICATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WAP_HASHED_CERTIFICATE: \"application/vnd.wap.hashed-certificate\";"
                },
                {
                    "name": "APPLICATION_VND_WAP_LOCC_WBXML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WAP_LOCC_WBXML: \"application/vnd.wap.locc+wbxml\";"
                },
                {
                    "name": "APPLICATION_VND_WAP_LOC_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WAP_LOC_XML: \"application/vnd.wap.loc+xml\";"
                },
                {
                    "name": "APPLICATION_VND_WAP_MMS_MESSAGE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WAP_MMS_MESSAGE: \"application/vnd.wap.mms-message\";"
                },
                {
                    "name": "APPLICATION_VND_WAP_MULTIPART_ALTERNATIVE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WAP_MULTIPART_ALTERNATIVE: \"application/vnd.wap.multipart.alternative\";"
                },
                {
                    "name": "APPLICATION_VND_WAP_MULTIPART_BYTERANGES",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WAP_MULTIPART_BYTERANGES: \"application/vnd.wap.multipart.byteranges\";"
                },
                {
                    "name": "APPLICATION_VND_WAP_MULTIPART_FORM_DATA",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WAP_MULTIPART_FORM_DATA: \"application/vnd.wap.multipart.form-data\";"
                },
                {
                    "name": "APPLICATION_VND_WAP_MULTIPART_MIXED",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WAP_MULTIPART_MIXED: \"application/vnd.wap.multipart.mixed\";"
                },
                {
                    "name": "APPLICATION_VND_WAP_MULTIPART_RELATED",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WAP_MULTIPART_RELATED: \"application/vnd.wap.multipart.related\";"
                },
                {
                    "name": "APPLICATION_VND_WAP_MULTIPART_WILDCARD",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WAP_MULTIPART_WILDCARD: \"application/vnd.wap.multipart.*\";"
                },
                {
                    "name": "APPLICATION_VND_WAP_ROLLOVER_CERTIFICATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WAP_ROLLOVER_CERTIFICATE: \"application/vnd.wap.rollover-certificate\";"
                },
                {
                    "name": "APPLICATION_VND_WAP_SIA",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WAP_SIA: \"application/vnd.wap.sia\";"
                },
                {
                    "name": "APPLICATION_VND_WAP_SIC",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WAP_SIC: \"application/vnd.wap.sic\";"
                },
                {
                    "name": "APPLICATION_VND_WAP_SIGNED_CERTIFICATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WAP_SIGNED_CERTIFICATE: \"application/vnd.wap.signed-certificate\";"
                },
                {
                    "name": "APPLICATION_VND_WAP_SLC",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WAP_SLC: \"application/vnd.wap.slc\";"
                },
                {
                    "name": "APPLICATION_VND_WAP_UAPROF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WAP_UAPROF: \"application/vnd.wap.uaprof\";"
                },
                {
                    "name": "APPLICATION_VND_WAP_WBXML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WAP_WBXML: \"application/vnd.wap.wbxml\";"
                },
                {
                    "name": "APPLICATION_VND_WAP_WMLC",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WAP_WMLC: \"application/vnd.wap.wmlc\";"
                },
                {
                    "name": "APPLICATION_VND_WAP_WMLSCRIPTC",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WAP_WMLSCRIPTC: \"application/vnd.wap.wmlscriptc\";"
                },
                {
                    "name": "APPLICATION_VND_WAP_WTA_EVENTC",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WAP_WTA_EVENTC: \"application/vnd.wap.wta-eventc\";"
                },
                {
                    "name": "APPLICATION_VND_WAP_WTLS_CA_CERTIFICATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WAP_WTLS_CA_CERTIFICATE: \"application/vnd.wap.wtls-ca-certificate\";"
                },
                {
                    "name": "APPLICATION_VND_WAP_WTLS_USER_CERTIFICATE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WAP_WTLS_USER_CERTIFICATE: \"application/vnd.wap.wtls-user-certificate\";"
                },
                {
                    "name": "APPLICATION_VND_WAP_XHTML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WAP_XHTML_XML: \"application/vnd.wap.xhtml+xml\";"
                },
                {
                    "name": "APPLICATION_VND_WASMFLOW_WAFL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WASMFLOW_WAFL: \"application/vnd.wasmflow.wafl\";"
                },
                {
                    "name": "APPLICATION_VND_WEBTURBO",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WEBTURBO: \"application/vnd.webturbo\";"
                },
                {
                    "name": "APPLICATION_VND_WFA_DPP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WFA_DPP: \"application/vnd.wfa.dpp\";"
                },
                {
                    "name": "APPLICATION_VND_WFA_P2P",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WFA_P2P: \"application/vnd.wfa.p2p\";"
                },
                {
                    "name": "APPLICATION_VND_WFA_WSC",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WFA_WSC: \"application/vnd.wfa.wsc\";"
                },
                {
                    "name": "APPLICATION_VND_WINDOWS_DEVICEPAIRING",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WINDOWS_DEVICEPAIRING: \"application/vnd.windows.devicepairing\";"
                },
                {
                    "name": "APPLICATION_VND_WMC",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WMC: \"application/vnd.wmc\";"
                },
                {
                    "name": "APPLICATION_VND_WMF_BOOTSTRAP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WMF_BOOTSTRAP: \"application/vnd.wmf.bootstrap\";"
                },
                {
                    "name": "APPLICATION_VND_WOLFRAM_MATHEMATICA",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WOLFRAM_MATHEMATICA: \"application/vnd.wolfram.mathematica\";"
                },
                {
                    "name": "APPLICATION_VND_WOLFRAM_MATHEMATICA_PACKAGE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WOLFRAM_MATHEMATICA_PACKAGE: \"application/vnd.wolfram.mathematica.package\";"
                },
                {
                    "name": "APPLICATION_VND_WOLFRAM_PLAYER",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WOLFRAM_PLAYER: \"application/vnd.wolfram.player\";"
                },
                {
                    "name": "APPLICATION_VND_WORDLIFT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WORDLIFT: \"application/vnd.wordlift\";"
                },
                {
                    "name": "APPLICATION_VND_WORDPERFECT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WORDPERFECT: \"application/vnd.wordperfect\";"
                },
                {
                    "name": "APPLICATION_VND_WORDPERFECT5_1",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WORDPERFECT5_1: \"application/vnd.wordperfect5.1\";"
                },
                {
                    "name": "APPLICATION_VND_WQD",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WQD: \"application/vnd.wqd\";"
                },
                {
                    "name": "APPLICATION_VND_WRQ_HP3000_LABELLED",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WRQ_HP3000_LABELLED: \"application/vnd.wrq-hp3000-labelled\";"
                },
                {
                    "name": "APPLICATION_VND_WT_STF",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WT_STF: \"application/vnd.wt.stf\";"
                },
                {
                    "name": "APPLICATION_VND_WV_CSP_CIR",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WV_CSP_CIR: \"application/vnd.wv.csp.cir\";"
                },
                {
                    "name": "APPLICATION_VND_WV_CSP_WBXML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WV_CSP_WBXML: \"application/vnd.wv.csp+wbxml\";"
                },
                {
                    "name": "APPLICATION_VND_WV_CSP_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WV_CSP_XML: \"application/vnd.wv.csp+xml\";"
                },
                {
                    "name": "APPLICATION_VND_WV_SSP_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_WV_SSP_XML: \"application/vnd.wv.ssp+xml\";"
                },
                {
                    "name": "APPLICATION_VND_XACML_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VND_XACML_JSON: \"application/vnd.xacml+json\";"
                },
                {
                    "name": "APPLICATION_VND_XARA",
                    "type": "variable",
                    "doc": "APPLICATION_VND_XARA: \"application/vnd.xara\";"
                },
                {
                    "name": "APPLICATION_VND_XECRETS_ENCRYPTED",
                    "type": "variable",
                    "doc": "APPLICATION_VND_XECRETS_ENCRYPTED: \"application/vnd.xecrets-encrypted\";"
                },
                {
                    "name": "APPLICATION_VND_XFDL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_XFDL: \"application/vnd.xfdl\";"
                },
                {
                    "name": "APPLICATION_VND_XFDL_WEBFORM",
                    "type": "variable",
                    "doc": "APPLICATION_VND_XFDL_WEBFORM: \"application/vnd.xfdl.webform\";"
                },
                {
                    "name": "APPLICATION_VND_XMI_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_XMI_XML: \"application/vnd.xmi+xml\";"
                },
                {
                    "name": "APPLICATION_VND_XMPIE_CPKG",
                    "type": "variable",
                    "doc": "APPLICATION_VND_XMPIE_CPKG: \"application/vnd.xmpie.cpkg\";"
                },
                {
                    "name": "APPLICATION_VND_XMPIE_DPKG",
                    "type": "variable",
                    "doc": "APPLICATION_VND_XMPIE_DPKG: \"application/vnd.xmpie.dpkg\";"
                },
                {
                    "name": "APPLICATION_VND_XMPIE_PLAN",
                    "type": "variable",
                    "doc": "APPLICATION_VND_XMPIE_PLAN: \"application/vnd.xmpie.plan\";"
                },
                {
                    "name": "APPLICATION_VND_XMPIE_PPKG",
                    "type": "variable",
                    "doc": "APPLICATION_VND_XMPIE_PPKG: \"application/vnd.xmpie.ppkg\";"
                },
                {
                    "name": "APPLICATION_VND_XMPIE_XLIM",
                    "type": "variable",
                    "doc": "APPLICATION_VND_XMPIE_XLIM: \"application/vnd.xmpie.xlim\";"
                },
                {
                    "name": "APPLICATION_VND_YAMAHA_HV_DIC",
                    "type": "variable",
                    "doc": "APPLICATION_VND_YAMAHA_HV_DIC: \"application/vnd.yamaha.hv-dic\";"
                },
                {
                    "name": "APPLICATION_VND_YAMAHA_HV_SCRIPT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_YAMAHA_HV_SCRIPT: \"application/vnd.yamaha.hv-script\";"
                },
                {
                    "name": "APPLICATION_VND_YAMAHA_HV_VOICE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_YAMAHA_HV_VOICE: \"application/vnd.yamaha.hv-voice\";"
                },
                {
                    "name": "APPLICATION_VND_YAMAHA_OPENSCOREFORMAT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_YAMAHA_OPENSCOREFORMAT: \"application/vnd.yamaha.openscoreformat\";"
                },
                {
                    "name": "APPLICATION_VND_YAMAHA_OPENSCOREFORMAT_OSFPVG_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_YAMAHA_OPENSCOREFORMAT_OSFPVG_XML: \"application/vnd.yamaha.openscoreformat.osfpvg+xml\";"
                },
                {
                    "name": "APPLICATION_VND_YAMAHA_REMOTE_SETUP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_YAMAHA_REMOTE_SETUP: \"application/vnd.yamaha.remote-setup\";"
                },
                {
                    "name": "APPLICATION_VND_YAMAHA_SMAF_AUDIO",
                    "type": "variable",
                    "doc": "APPLICATION_VND_YAMAHA_SMAF_AUDIO: \"application/vnd.yamaha.smaf-audio\";"
                },
                {
                    "name": "APPLICATION_VND_YAMAHA_SMAF_PHRASE",
                    "type": "variable",
                    "doc": "APPLICATION_VND_YAMAHA_SMAF_PHRASE: \"application/vnd.yamaha.smaf-phrase\";"
                },
                {
                    "name": "APPLICATION_VND_YAMAHA_THROUGH_NGN",
                    "type": "variable",
                    "doc": "APPLICATION_VND_YAMAHA_THROUGH_NGN: \"application/vnd.yamaha.through-ngn\";"
                },
                {
                    "name": "APPLICATION_VND_YAMAHA_TUNNEL_UDPENCAP",
                    "type": "variable",
                    "doc": "APPLICATION_VND_YAMAHA_TUNNEL_UDPENCAP: \"application/vnd.yamaha.tunnel-udpencap\";"
                },
                {
                    "name": "APPLICATION_VND_YAOWEME",
                    "type": "variable",
                    "doc": "APPLICATION_VND_YAOWEME: \"application/vnd.yaoweme\";"
                },
                {
                    "name": "APPLICATION_VND_YELLOWRIVER_CUSTOM_MENU",
                    "type": "variable",
                    "doc": "APPLICATION_VND_YELLOWRIVER_CUSTOM_MENU: \"application/vnd.yellowriver-custom-menu\";"
                },
                {
                    "name": "APPLICATION_VND_YOUTUBE_YT",
                    "type": "variable",
                    "doc": "APPLICATION_VND_YOUTUBE_YT: \"application/vnd.youtube.yt\";"
                },
                {
                    "name": "APPLICATION_VND_ZUL",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ZUL: \"application/vnd.zul\";"
                },
                {
                    "name": "APPLICATION_VND_ZZAZZ_DECK_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VND_ZZAZZ_DECK_XML: \"application/vnd.zzazz.deck+xml\";"
                },
                {
                    "name": "APPLICATION_VOICEXML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_VOICEXML_XML: \"application/voicexml+xml\";"
                },
                {
                    "name": "APPLICATION_VOUCHER_CMS_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_VOUCHER_CMS_JSON: \"application/voucher-cms+json\";"
                },
                {
                    "name": "APPLICATION_VQ_RTCPXR",
                    "type": "variable",
                    "doc": "APPLICATION_VQ_RTCPXR: \"application/vq-rtcpxr\";"
                },
                {
                    "name": "APPLICATION_WASM",
                    "type": "variable",
                    "doc": "APPLICATION_WASM: \"application/wasm\";"
                },
                {
                    "name": "APPLICATION_WATCHERINFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_WATCHERINFO_XML: \"application/watcherinfo+xml\";"
                },
                {
                    "name": "APPLICATION_WEBPUSH_OPTIONS_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_WEBPUSH_OPTIONS_JSON: \"application/webpush-options+json\";"
                },
                {
                    "name": "APPLICATION_WHOISPP_QUERY",
                    "type": "variable",
                    "doc": "APPLICATION_WHOISPP_QUERY: \"application/whoispp-query\";"
                },
                {
                    "name": "APPLICATION_WHOISPP_RESPONSE",
                    "type": "variable",
                    "doc": "APPLICATION_WHOISPP_RESPONSE: \"application/whoispp-response\";"
                },
                {
                    "name": "APPLICATION_WIDGET",
                    "type": "variable",
                    "doc": "APPLICATION_WIDGET: \"application/widget\";"
                },
                {
                    "name": "APPLICATION_WILDCARD",
                    "type": "variable",
                    "doc": "APPLICATION_WILDCARD: \"application/*\";"
                },
                {
                    "name": "APPLICATION_WITA",
                    "type": "variable",
                    "doc": "APPLICATION_WITA: \"application/wita\";"
                },
                {
                    "name": "APPLICATION_WML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_WML_XML: \"application/wml+xml\";"
                },
                {
                    "name": "APPLICATION_WORDPERFECT5_1",
                    "type": "variable",
                    "doc": "APPLICATION_WORDPERFECT5_1: \"application/wordperfect5.1\";"
                },
                {
                    "name": "APPLICATION_WSDL_XML",
                    "type": "variable",
                    "doc": "APPLICATION_WSDL_XML: \"application/wsdl+xml\";"
                },
                {
                    "name": "APPLICATION_WSPOLICY_XML",
                    "type": "variable",
                    "doc": "APPLICATION_WSPOLICY_XML: \"application/wspolicy+xml\";"
                },
                {
                    "name": "APPLICATION_X400_BP",
                    "type": "variable",
                    "doc": "APPLICATION_X400_BP: \"application/x400-bp\";"
                },
                {
                    "name": "APPLICATION_XACML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_XACML_XML: \"application/xacml+xml\";"
                },
                {
                    "name": "APPLICATION_XCAP_ATT_XML",
                    "type": "variable",
                    "doc": "APPLICATION_XCAP_ATT_XML: \"application/xcap-att+xml\";"
                },
                {
                    "name": "APPLICATION_XCAP_CAPS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_XCAP_CAPS_XML: \"application/xcap-caps+xml\";"
                },
                {
                    "name": "APPLICATION_XCAP_DIFF_XML",
                    "type": "variable",
                    "doc": "APPLICATION_XCAP_DIFF_XML: \"application/xcap-diff+xml\";"
                },
                {
                    "name": "APPLICATION_XCAP_EL_XML",
                    "type": "variable",
                    "doc": "APPLICATION_XCAP_EL_XML: \"application/xcap-el+xml\";"
                },
                {
                    "name": "APPLICATION_XCAP_ERROR_XML",
                    "type": "variable",
                    "doc": "APPLICATION_XCAP_ERROR_XML: \"application/xcap-error+xml\";"
                },
                {
                    "name": "APPLICATION_XCAP_NS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_XCAP_NS_XML: \"application/xcap-ns+xml\";"
                },
                {
                    "name": "APPLICATION_XCON_CONFERENCE_INFO_DIFF_XML",
                    "type": "variable",
                    "doc": "APPLICATION_XCON_CONFERENCE_INFO_DIFF_XML: \"application/xcon-conference-info-diff+xml\";"
                },
                {
                    "name": "APPLICATION_XCON_CONFERENCE_INFO_XML",
                    "type": "variable",
                    "doc": "APPLICATION_XCON_CONFERENCE_INFO_XML: \"application/xcon-conference-info+xml\";"
                },
                {
                    "name": "APPLICATION_XENC_XML",
                    "type": "variable",
                    "doc": "APPLICATION_XENC_XML: \"application/xenc+xml\";"
                },
                {
                    "name": "APPLICATION_XFDF",
                    "type": "variable",
                    "doc": "APPLICATION_XFDF: \"application/xfdf\";"
                },
                {
                    "name": "APPLICATION_XHTML_XML",
                    "type": "variable",
                    "doc": "APPLICATION_XHTML_XML: \"application/xhtml+xml\";"
                },
                {
                    "name": "APPLICATION_XLIFF_XML",
                    "type": "variable",
                    "doc": "APPLICATION_XLIFF_XML: \"application/xliff+xml\";"
                },
                {
                    "name": "APPLICATION_XML",
                    "type": "variable",
                    "doc": "APPLICATION_XML: \"application/xml\";"
                },
                {
                    "name": "APPLICATION_XML_DTD",
                    "type": "variable",
                    "doc": "APPLICATION_XML_DTD: \"application/xml-dtd\";"
                },
                {
                    "name": "APPLICATION_XML_EXTERNAL_PARSED_ENTITY",
                    "type": "variable",
                    "doc": "APPLICATION_XML_EXTERNAL_PARSED_ENTITY: \"application/xml-external-parsed-entity\";"
                },
                {
                    "name": "APPLICATION_XML_PATCH_XML",
                    "type": "variable",
                    "doc": "APPLICATION_XML_PATCH_XML: \"application/xml-patch+xml\";"
                },
                {
                    "name": "APPLICATION_XMPP_XML",
                    "type": "variable",
                    "doc": "APPLICATION_XMPP_XML: \"application/xmpp+xml\";"
                },
                {
                    "name": "APPLICATION_XOP_XML",
                    "type": "variable",
                    "doc": "APPLICATION_XOP_XML: \"application/xop+xml\";"
                },
                {
                    "name": "APPLICATION_XSLT_XML",
                    "type": "variable",
                    "doc": "APPLICATION_XSLT_XML: \"application/xslt+xml\";"
                },
                {
                    "name": "APPLICATION_XSPF_XML",
                    "type": "variable",
                    "doc": "APPLICATION_XSPF_XML: \"application/xspf+xml\";"
                },
                {
                    "name": "APPLICATION_XV_XML",
                    "type": "variable",
                    "doc": "APPLICATION_XV_XML: \"application/xv+xml\";"
                },
                {
                    "name": "APPLICATION_X_123",
                    "type": "variable",
                    "doc": "APPLICATION_X_123: \"application/x-123\";"
                },
                {
                    "name": "APPLICATION_X_7Z_COMPRESSED",
                    "type": "variable",
                    "doc": "APPLICATION_X_7Z_COMPRESSED: \"application/x-7z-compressed\";"
                },
                {
                    "name": "APPLICATION_X_ABIWORD",
                    "type": "variable",
                    "doc": "APPLICATION_X_ABIWORD: \"application/x-abiword\";"
                },
                {
                    "name": "APPLICATION_X_APPLE_DISKIMAGE",
                    "type": "variable",
                    "doc": "APPLICATION_X_APPLE_DISKIMAGE: \"application/x-apple-diskimage\";"
                },
                {
                    "name": "APPLICATION_X_BCPIO",
                    "type": "variable",
                    "doc": "APPLICATION_X_BCPIO: \"application/x-bcpio\";"
                },
                {
                    "name": "APPLICATION_X_BITTORRENT",
                    "type": "variable",
                    "doc": "APPLICATION_X_BITTORRENT: \"application/x-bittorrent\";"
                },
                {
                    "name": "APPLICATION_X_CAB",
                    "type": "variable",
                    "doc": "APPLICATION_X_CAB: \"application/x-cab\";"
                },
                {
                    "name": "APPLICATION_X_CBR",
                    "type": "variable",
                    "doc": "APPLICATION_X_CBR: \"application/x-cbr\";"
                },
                {
                    "name": "APPLICATION_X_CBZ",
                    "type": "variable",
                    "doc": "APPLICATION_X_CBZ: \"application/x-cbz\";"
                },
                {
                    "name": "APPLICATION_X_CDF",
                    "type": "variable",
                    "doc": "APPLICATION_X_CDF: \"application/x-cdf\";"
                },
                {
                    "name": "APPLICATION_X_CDLINK",
                    "type": "variable",
                    "doc": "APPLICATION_X_CDLINK: \"application/x-cdlink\";"
                },
                {
                    "name": "APPLICATION_X_CHESS_PGN",
                    "type": "variable",
                    "doc": "APPLICATION_X_CHESS_PGN: \"application/x-chess-pgn\";"
                },
                {
                    "name": "APPLICATION_X_COMSOL",
                    "type": "variable",
                    "doc": "APPLICATION_X_COMSOL: \"application/x-comsol\";"
                },
                {
                    "name": "APPLICATION_X_CORE",
                    "type": "variable",
                    "doc": "APPLICATION_X_CORE: \"application/x-core\";"
                },
                {
                    "name": "APPLICATION_X_CPIO",
                    "type": "variable",
                    "doc": "APPLICATION_X_CPIO: \"application/x-cpio\";"
                },
                {
                    "name": "APPLICATION_X_CSH",
                    "type": "variable",
                    "doc": "APPLICATION_X_CSH: \"application/x-csh\";"
                },
                {
                    "name": "APPLICATION_X_DEB",
                    "type": "variable",
                    "doc": "APPLICATION_X_DEB: \"application/x-deb\";"
                },
                {
                    "name": "APPLICATION_X_DEBIAN_PACKAGE",
                    "type": "variable",
                    "doc": "APPLICATION_X_DEBIAN_PACKAGE: \"application/x-debian-package\";"
                },
                {
                    "name": "APPLICATION_X_DIRECTOR",
                    "type": "variable",
                    "doc": "APPLICATION_X_DIRECTOR: \"application/x-director\";"
                },
                {
                    "name": "APPLICATION_X_DMS",
                    "type": "variable",
                    "doc": "APPLICATION_X_DMS: \"application/x-dms\";"
                },
                {
                    "name": "APPLICATION_X_DOOM",
                    "type": "variable",
                    "doc": "APPLICATION_X_DOOM: \"application/x-doom\";"
                },
                {
                    "name": "APPLICATION_X_DVI",
                    "type": "variable",
                    "doc": "APPLICATION_X_DVI: \"application/x-dvi\";"
                },
                {
                    "name": "APPLICATION_X_EXECUTABLE",
                    "type": "variable",
                    "doc": "APPLICATION_X_EXECUTABLE: \"application/x-executable\";"
                },
                {
                    "name": "APPLICATION_X_FONT",
                    "type": "variable",
                    "doc": "APPLICATION_X_FONT: \"application/x-font\";"
                },
                {
                    "name": "APPLICATION_X_FONT_PCF",
                    "type": "variable",
                    "doc": "APPLICATION_X_FONT_PCF: \"application/x-font-pcf\";"
                },
                {
                    "name": "APPLICATION_X_FONT_TTF",
                    "type": "variable",
                    "doc": "APPLICATION_X_FONT_TTF: \"application/x-font-ttf\";"
                },
                {
                    "name": "APPLICATION_X_FONT_WOFF",
                    "type": "variable",
                    "doc": "APPLICATION_X_FONT_WOFF: \"application/x-font-woff\";"
                },
                {
                    "name": "APPLICATION_X_FREEMIND",
                    "type": "variable",
                    "doc": "APPLICATION_X_FREEMIND: \"application/x-freemind\";"
                },
                {
                    "name": "APPLICATION_X_FUTURESPLASH",
                    "type": "variable",
                    "doc": "APPLICATION_X_FUTURESPLASH: \"application/x-futuresplash\";"
                },
                {
                    "name": "APPLICATION_X_GANTTPROJECT",
                    "type": "variable",
                    "doc": "APPLICATION_X_GANTTPROJECT: \"application/x-ganttproject\";"
                },
                {
                    "name": "APPLICATION_X_GNUMERIC",
                    "type": "variable",
                    "doc": "APPLICATION_X_GNUMERIC: \"application/x-gnumeric\";"
                },
                {
                    "name": "APPLICATION_X_GO_SGF",
                    "type": "variable",
                    "doc": "APPLICATION_X_GO_SGF: \"application/x-go-sgf\";"
                },
                {
                    "name": "APPLICATION_X_GRAPHING_CALCULATOR",
                    "type": "variable",
                    "doc": "APPLICATION_X_GRAPHING_CALCULATOR: \"application/x-graphing-calculator\";"
                },
                {
                    "name": "APPLICATION_X_GTAR",
                    "type": "variable",
                    "doc": "APPLICATION_X_GTAR: \"application/x-gtar\";"
                },
                {
                    "name": "APPLICATION_X_GTAR_COMPRESSED",
                    "type": "variable",
                    "doc": "APPLICATION_X_GTAR_COMPRESSED: \"application/x-gtar-compressed\";"
                },
                {
                    "name": "APPLICATION_X_HDF",
                    "type": "variable",
                    "doc": "APPLICATION_X_HDF: \"application/x-hdf\";"
                },
                {
                    "name": "APPLICATION_X_HDMLC",
                    "type": "variable",
                    "doc": "APPLICATION_X_HDMLC: \"application/x-hdmlc\";"
                },
                {
                    "name": "APPLICATION_X_HWP",
                    "type": "variable",
                    "doc": "APPLICATION_X_HWP: \"application/x-hwp\";"
                },
                {
                    "name": "APPLICATION_X_ICA",
                    "type": "variable",
                    "doc": "APPLICATION_X_ICA: \"application/x-ica\";"
                },
                {
                    "name": "APPLICATION_X_INFO",
                    "type": "variable",
                    "doc": "APPLICATION_X_INFO: \"application/x-info\";"
                },
                {
                    "name": "APPLICATION_X_INTERNET_SIGNUP",
                    "type": "variable",
                    "doc": "APPLICATION_X_INTERNET_SIGNUP: \"application/x-internet-signup\";"
                },
                {
                    "name": "APPLICATION_X_IPHONE",
                    "type": "variable",
                    "doc": "APPLICATION_X_IPHONE: \"application/x-iphone\";"
                },
                {
                    "name": "APPLICATION_X_ISO9660_IMAGE",
                    "type": "variable",
                    "doc": "APPLICATION_X_ISO9660_IMAGE: \"application/x-iso9660-image\";"
                },
                {
                    "name": "APPLICATION_X_JAM",
                    "type": "variable",
                    "doc": "APPLICATION_X_JAM: \"application/x-jam\";"
                },
                {
                    "name": "APPLICATION_X_JAVASCRIPT",
                    "type": "variable",
                    "doc": "APPLICATION_X_JAVASCRIPT: \"application/x-javascript\";"
                },
                {
                    "name": "APPLICATION_X_JAVA_APPLET",
                    "type": "variable",
                    "doc": "APPLICATION_X_JAVA_APPLET: \"application/x-java-applet\";"
                },
                {
                    "name": "APPLICATION_X_JAVA_BEAN",
                    "type": "variable",
                    "doc": "APPLICATION_X_JAVA_BEAN: \"application/x-java-bean\";"
                },
                {
                    "name": "APPLICATION_X_JAVA_JNLP_FILE",
                    "type": "variable",
                    "doc": "APPLICATION_X_JAVA_JNLP_FILE: \"application/x-java-jnlp-file\";"
                },
                {
                    "name": "APPLICATION_X_JMOL",
                    "type": "variable",
                    "doc": "APPLICATION_X_JMOL: \"application/x-jmol\";"
                },
                {
                    "name": "APPLICATION_X_KCHART",
                    "type": "variable",
                    "doc": "APPLICATION_X_KCHART: \"application/x-kchart\";"
                },
                {
                    "name": "APPLICATION_X_KDELNK",
                    "type": "variable",
                    "doc": "APPLICATION_X_KDELNK: \"application/x-kdelnk\";"
                },
                {
                    "name": "APPLICATION_X_KILLUSTRATOR",
                    "type": "variable",
                    "doc": "APPLICATION_X_KILLUSTRATOR: \"application/x-killustrator\";"
                },
                {
                    "name": "APPLICATION_X_KOAN",
                    "type": "variable",
                    "doc": "APPLICATION_X_KOAN: \"application/x-koan\";"
                },
                {
                    "name": "APPLICATION_X_KPRESENTER",
                    "type": "variable",
                    "doc": "APPLICATION_X_KPRESENTER: \"application/x-kpresenter\";"
                },
                {
                    "name": "APPLICATION_X_KSPREAD",
                    "type": "variable",
                    "doc": "APPLICATION_X_KSPREAD: \"application/x-kspread\";"
                },
                {
                    "name": "APPLICATION_X_KWORD",
                    "type": "variable",
                    "doc": "APPLICATION_X_KWORD: \"application/x-kword\";"
                },
                {
                    "name": "APPLICATION_X_LATEX",
                    "type": "variable",
                    "doc": "APPLICATION_X_LATEX: \"application/x-latex\";"
                },
                {
                    "name": "APPLICATION_X_LHA",
                    "type": "variable",
                    "doc": "APPLICATION_X_LHA: \"application/x-lha\";"
                },
                {
                    "name": "APPLICATION_X_LYX",
                    "type": "variable",
                    "doc": "APPLICATION_X_LYX: \"application/x-lyx\";"
                },
                {
                    "name": "APPLICATION_X_LZH",
                    "type": "variable",
                    "doc": "APPLICATION_X_LZH: \"application/x-lzh\";"
                },
                {
                    "name": "APPLICATION_X_LZX",
                    "type": "variable",
                    "doc": "APPLICATION_X_LZX: \"application/x-lzx\";"
                },
                {
                    "name": "APPLICATION_X_MAKER",
                    "type": "variable",
                    "doc": "APPLICATION_X_MAKER: \"application/x-maker\";"
                },
                {
                    "name": "APPLICATION_X_MIF",
                    "type": "variable",
                    "doc": "APPLICATION_X_MIF: \"application/x-mif\";"
                },
                {
                    "name": "APPLICATION_X_MPEGURL",
                    "type": "variable",
                    "doc": "APPLICATION_X_MPEGURL: \"application/x-mpegURL\";"
                },
                {
                    "name": "APPLICATION_X_MSDOS_PROGRAM",
                    "type": "variable",
                    "doc": "APPLICATION_X_MSDOS_PROGRAM: \"application/x-msdos-program\";"
                },
                {
                    "name": "APPLICATION_X_MSI",
                    "type": "variable",
                    "doc": "APPLICATION_X_MSI: \"application/x-msi\";"
                },
                {
                    "name": "APPLICATION_X_MS_APPLICATION",
                    "type": "variable",
                    "doc": "APPLICATION_X_MS_APPLICATION: \"application/x-ms-application\";"
                },
                {
                    "name": "APPLICATION_X_MS_MANIFEST",
                    "type": "variable",
                    "doc": "APPLICATION_X_MS_MANIFEST: \"application/x-ms-manifest\";"
                },
                {
                    "name": "APPLICATION_X_MS_WMD",
                    "type": "variable",
                    "doc": "APPLICATION_X_MS_WMD: \"application/x-ms-wmd\";"
                },
                {
                    "name": "APPLICATION_X_MS_WMZ",
                    "type": "variable",
                    "doc": "APPLICATION_X_MS_WMZ: \"application/x-ms-wmz\";"
                },
                {
                    "name": "APPLICATION_X_NETCDF",
                    "type": "variable",
                    "doc": "APPLICATION_X_NETCDF: \"application/x-netcdf\";"
                },
                {
                    "name": "APPLICATION_X_NS_PROXY_AUTOCONFIG",
                    "type": "variable",
                    "doc": "APPLICATION_X_NS_PROXY_AUTOCONFIG: \"application/x-ns-proxy-autoconfig\";"
                },
                {
                    "name": "APPLICATION_X_NWC",
                    "type": "variable",
                    "doc": "APPLICATION_X_NWC: \"application/x-nwc\";"
                },
                {
                    "name": "APPLICATION_X_OBJECT",
                    "type": "variable",
                    "doc": "APPLICATION_X_OBJECT: \"application/x-object\";"
                },
                {
                    "name": "APPLICATION_X_OZ_APPLICATION",
                    "type": "variable",
                    "doc": "APPLICATION_X_OZ_APPLICATION: \"application/x-oz-application\";"
                },
                {
                    "name": "APPLICATION_X_PKCS12",
                    "type": "variable",
                    "doc": "APPLICATION_X_PKCS12: \"application/x-pkcs12\";"
                },
                {
                    "name": "APPLICATION_X_PKCS7_CERTIFICATES",
                    "type": "variable",
                    "doc": "APPLICATION_X_PKCS7_CERTIFICATES: \"application/x-pkcs7-certificates\";"
                },
                {
                    "name": "APPLICATION_X_PKCS7_CERTREQRESP",
                    "type": "variable",
                    "doc": "APPLICATION_X_PKCS7_CERTREQRESP: \"application/x-pkcs7-certreqresp\";"
                },
                {
                    "name": "APPLICATION_X_PKCS7_CRL",
                    "type": "variable",
                    "doc": "APPLICATION_X_PKCS7_CRL: \"application/x-pkcs7-crl\";"
                },
                {
                    "name": "APPLICATION_X_PKCS7_MIME",
                    "type": "variable",
                    "doc": "APPLICATION_X_PKCS7_MIME: \"application/x-pkcs7-mime\";"
                },
                {
                    "name": "APPLICATION_X_PKCS7_SIGNATURE",
                    "type": "variable",
                    "doc": "APPLICATION_X_PKCS7_SIGNATURE: \"application/x-pkcs7-signature\";"
                },
                {
                    "name": "APPLICATION_X_PKI_MESSAGE",
                    "type": "variable",
                    "doc": "APPLICATION_X_PKI_MESSAGE: \"application/x-pki-message\";"
                },
                {
                    "name": "APPLICATION_X_PYTHON_CODE",
                    "type": "variable",
                    "doc": "APPLICATION_X_PYTHON_CODE: \"application/x-python-code\";"
                },
                {
                    "name": "APPLICATION_X_QGIS",
                    "type": "variable",
                    "doc": "APPLICATION_X_QGIS: \"application/x-qgis\";"
                },
                {
                    "name": "APPLICATION_X_QUICKTIMEPLAYER",
                    "type": "variable",
                    "doc": "APPLICATION_X_QUICKTIMEPLAYER: \"application/x-quicktimeplayer\";"
                },
                {
                    "name": "APPLICATION_X_RAR_COMPRESSED",
                    "type": "variable",
                    "doc": "APPLICATION_X_RAR_COMPRESSED: \"application/x-rar-compressed\";"
                },
                {
                    "name": "APPLICATION_X_RDP",
                    "type": "variable",
                    "doc": "APPLICATION_X_RDP: \"application/x-rdp\";"
                },
                {
                    "name": "APPLICATION_X_REDHAT_PACKAGE_MANAGER",
                    "type": "variable",
                    "doc": "APPLICATION_X_REDHAT_PACKAGE_MANAGER: \"application/x-redhat-package-manager\";"
                },
                {
                    "name": "APPLICATION_X_RSS_XML",
                    "type": "variable",
                    "doc": "APPLICATION_X_RSS_XML: \"application/x-rss+xml\";"
                },
                {
                    "name": "APPLICATION_X_RUBY",
                    "type": "variable",
                    "doc": "APPLICATION_X_RUBY: \"application/x-ruby\";"
                },
                {
                    "name": "APPLICATION_X_RX",
                    "type": "variable",
                    "doc": "APPLICATION_X_RX: \"application/x-rx\";"
                },
                {
                    "name": "APPLICATION_X_SCILAB",
                    "type": "variable",
                    "doc": "APPLICATION_X_SCILAB: \"application/x-scilab\";"
                },
                {
                    "name": "APPLICATION_X_SCILAB_XCOS",
                    "type": "variable",
                    "doc": "APPLICATION_X_SCILAB_XCOS: \"application/x-scilab-xcos\";"
                },
                {
                    "name": "APPLICATION_X_SH",
                    "type": "variable",
                    "doc": "APPLICATION_X_SH: \"application/x-sh\";"
                },
                {
                    "name": "APPLICATION_X_SHAR",
                    "type": "variable",
                    "doc": "APPLICATION_X_SHAR: \"application/x-shar\";"
                },
                {
                    "name": "APPLICATION_X_SHELLSCRIPT",
                    "type": "variable",
                    "doc": "APPLICATION_X_SHELLSCRIPT: \"application/x-shellscript\";"
                },
                {
                    "name": "APPLICATION_X_SHOCKWAVE_FLASH",
                    "type": "variable",
                    "doc": "APPLICATION_X_SHOCKWAVE_FLASH: \"application/x-shockwave-flash\";"
                },
                {
                    "name": "APPLICATION_X_SILVERLIGHT",
                    "type": "variable",
                    "doc": "APPLICATION_X_SILVERLIGHT: \"application/x-silverlight\";"
                },
                {
                    "name": "APPLICATION_X_SQL",
                    "type": "variable",
                    "doc": "APPLICATION_X_SQL: \"application/x-sql\";"
                },
                {
                    "name": "APPLICATION_X_STUFFIT",
                    "type": "variable",
                    "doc": "APPLICATION_X_STUFFIT: \"application/x-stuffit\";"
                },
                {
                    "name": "APPLICATION_X_SV4CPIO",
                    "type": "variable",
                    "doc": "APPLICATION_X_SV4CPIO: \"application/x-sv4cpio\";"
                },
                {
                    "name": "APPLICATION_X_SV4CRC",
                    "type": "variable",
                    "doc": "APPLICATION_X_SV4CRC: \"application/x-sv4crc\";"
                },
                {
                    "name": "APPLICATION_X_TAR",
                    "type": "variable",
                    "doc": "APPLICATION_X_TAR: \"application/x-tar\";"
                },
                {
                    "name": "APPLICATION_X_TCL",
                    "type": "variable",
                    "doc": "APPLICATION_X_TCL: \"application/x-tcl\";"
                },
                {
                    "name": "APPLICATION_X_TEX",
                    "type": "variable",
                    "doc": "APPLICATION_X_TEX: \"application/x-tex\";"
                },
                {
                    "name": "APPLICATION_X_TEXINFO",
                    "type": "variable",
                    "doc": "APPLICATION_X_TEXINFO: \"application/x-texinfo\";"
                },
                {
                    "name": "APPLICATION_X_TEX_GF",
                    "type": "variable",
                    "doc": "APPLICATION_X_TEX_GF: \"application/x-tex-gf\";"
                },
                {
                    "name": "APPLICATION_X_TEX_PK",
                    "type": "variable",
                    "doc": "APPLICATION_X_TEX_PK: \"application/x-tex-pk\";"
                },
                {
                    "name": "APPLICATION_X_TRASH",
                    "type": "variable",
                    "doc": "APPLICATION_X_TRASH: \"application/x-trash\";"
                },
                {
                    "name": "APPLICATION_X_TROFF",
                    "type": "variable",
                    "doc": "APPLICATION_X_TROFF: \"application/x-troff\";"
                },
                {
                    "name": "APPLICATION_X_TROFF_MAN",
                    "type": "variable",
                    "doc": "APPLICATION_X_TROFF_MAN: \"application/x-troff-man\";"
                },
                {
                    "name": "APPLICATION_X_TROFF_ME",
                    "type": "variable",
                    "doc": "APPLICATION_X_TROFF_ME: \"application/x-troff-me\";"
                },
                {
                    "name": "APPLICATION_X_TROFF_MS",
                    "type": "variable",
                    "doc": "APPLICATION_X_TROFF_MS: \"application/x-troff-ms\";"
                },
                {
                    "name": "APPLICATION_X_TROFF_MSVIDEO",
                    "type": "variable",
                    "doc": "APPLICATION_X_TROFF_MSVIDEO: \"application/x-troff-msvideo\";"
                },
                {
                    "name": "APPLICATION_X_USTAR",
                    "type": "variable",
                    "doc": "APPLICATION_X_USTAR: \"application/x-ustar\";"
                },
                {
                    "name": "APPLICATION_X_VIDEOLAN",
                    "type": "variable",
                    "doc": "APPLICATION_X_VIDEOLAN: \"application/x-videolan\";"
                },
                {
                    "name": "APPLICATION_X_WAIS_SOURCE",
                    "type": "variable",
                    "doc": "APPLICATION_X_WAIS_SOURCE: \"application/x-wais-source\";"
                },
                {
                    "name": "APPLICATION_X_WEBARCHIVE",
                    "type": "variable",
                    "doc": "APPLICATION_X_WEBARCHIVE: \"application/x-webarchive\";"
                },
                {
                    "name": "APPLICATION_X_WEBARCHIVE_XML",
                    "type": "variable",
                    "doc": "APPLICATION_X_WEBARCHIVE_XML: \"application/x-webarchive-xml\";"
                },
                {
                    "name": "APPLICATION_X_WINGZ",
                    "type": "variable",
                    "doc": "APPLICATION_X_WINGZ: \"application/x-wingz\";"
                },
                {
                    "name": "APPLICATION_X_WWW_FORM_URLENCODED",
                    "type": "variable",
                    "doc": "APPLICATION_X_WWW_FORM_URLENCODED: \"application/x-www-form-urlencoded\";"
                },
                {
                    "name": "APPLICATION_X_X509_CA_CERT",
                    "type": "variable",
                    "doc": "APPLICATION_X_X509_CA_CERT: \"application/x-x509-ca-cert\";"
                },
                {
                    "name": "APPLICATION_X_X509_CA_RA_CERT",
                    "type": "variable",
                    "doc": "APPLICATION_X_X509_CA_RA_CERT: \"application/x-x509-ca-ra-cert\";"
                },
                {
                    "name": "APPLICATION_X_X509_NEXT_CA_CERT",
                    "type": "variable",
                    "doc": "APPLICATION_X_X509_NEXT_CA_CERT: \"application/x-x509-next-ca-cert\";"
                },
                {
                    "name": "APPLICATION_X_X509_USER_CERT",
                    "type": "variable",
                    "doc": "APPLICATION_X_X509_USER_CERT: \"application/x-x509-user-cert\";"
                },
                {
                    "name": "APPLICATION_X_X968_CA_CERT",
                    "type": "variable",
                    "doc": "APPLICATION_X_X968_CA_CERT: \"application/x-x968-ca-cert\";"
                },
                {
                    "name": "APPLICATION_X_X968_CROSS_CERT",
                    "type": "variable",
                    "doc": "APPLICATION_X_X968_CROSS_CERT: \"application/x-x968-cross-cert\";"
                },
                {
                    "name": "APPLICATION_X_X968_USER_CERT",
                    "type": "variable",
                    "doc": "APPLICATION_X_X968_USER_CERT: \"application/x-x968-user-cert\";"
                },
                {
                    "name": "APPLICATION_X_XCF",
                    "type": "variable",
                    "doc": "APPLICATION_X_XCF: \"application/x-xcf\";"
                },
                {
                    "name": "APPLICATION_X_XFIG",
                    "type": "variable",
                    "doc": "APPLICATION_X_XFIG: \"application/x-xfig\";"
                },
                {
                    "name": "APPLICATION_X_XPINSTALL",
                    "type": "variable",
                    "doc": "APPLICATION_X_XPINSTALL: \"application/x-xpinstall\";"
                },
                {
                    "name": "APPLICATION_X_XZ",
                    "type": "variable",
                    "doc": "APPLICATION_X_XZ: \"application/x-xz\";"
                },
                {
                    "name": "APPLICATION_YAML",
                    "type": "variable",
                    "doc": "APPLICATION_YAML: \"application/yaml\";"
                },
                {
                    "name": "APPLICATION_YANG",
                    "type": "variable",
                    "doc": "APPLICATION_YANG: \"application/yang\";"
                },
                {
                    "name": "APPLICATION_YANG_DATA_CBOR",
                    "type": "variable",
                    "doc": "APPLICATION_YANG_DATA_CBOR: \"application/yang-data+cbor\";"
                },
                {
                    "name": "APPLICATION_YANG_DATA_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_YANG_DATA_JSON: \"application/yang-data+json\";"
                },
                {
                    "name": "APPLICATION_YANG_DATA_XML",
                    "type": "variable",
                    "doc": "APPLICATION_YANG_DATA_XML: \"application/yang-data+xml\";"
                },
                {
                    "name": "APPLICATION_YANG_PATCH_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_YANG_PATCH_JSON: \"application/yang-patch+json\";"
                },
                {
                    "name": "APPLICATION_YANG_PATCH_XML",
                    "type": "variable",
                    "doc": "APPLICATION_YANG_PATCH_XML: \"application/yang-patch+xml\";"
                },
                {
                    "name": "APPLICATION_YANG_SID_JSON",
                    "type": "variable",
                    "doc": "APPLICATION_YANG_SID_JSON: \"application/yang-sid+json\";"
                },
                {
                    "name": "APPLICATION_YIN_XML",
                    "type": "variable",
                    "doc": "APPLICATION_YIN_XML: \"application/yin+xml\";"
                },
                {
                    "name": "APPLICATION_ZIP",
                    "type": "variable",
                    "doc": "APPLICATION_ZIP: \"application/zip\";"
                },
                {
                    "name": "APPLICATION_ZLIB",
                    "type": "variable",
                    "doc": "APPLICATION_ZLIB: \"application/zlib\";"
                },
                {
                    "name": "APPLICATION_ZSTD",
                    "type": "variable",
                    "doc": "APPLICATION_ZSTD: \"application/zstd\";"
                },
                {
                    "name": "AUDIO_1D_INTERLEAVED_PARITYFEC",
                    "type": "variable",
                    "doc": "AUDIO_1D_INTERLEAVED_PARITYFEC: \"audio/1d-interleaved-parityfec\";"
                },
                {
                    "name": "AUDIO_32KADPCM",
                    "type": "variable",
                    "doc": "AUDIO_32KADPCM: \"audio/32kadpcm\";"
                },
                {
                    "name": "AUDIO_3GPP",
                    "type": "variable",
                    "doc": "AUDIO_3GPP: \"audio/3gpp\";"
                },
                {
                    "name": "AUDIO_3GPP2",
                    "type": "variable",
                    "doc": "AUDIO_3GPP2: \"audio/3gpp2\";"
                },
                {
                    "name": "AUDIO_AAC",
                    "type": "variable",
                    "doc": "AUDIO_AAC: \"audio/aac\";"
                },
                {
                    "name": "AUDIO_AC3",
                    "type": "variable",
                    "doc": "AUDIO_AC3: \"audio/ac3\";"
                },
                {
                    "name": "AUDIO_AIFF",
                    "type": "variable",
                    "doc": "AUDIO_AIFF: \"audio/aiff\";"
                },
                {
                    "name": "AUDIO_AMR",
                    "type": "variable",
                    "doc": "AUDIO_AMR: \"audio/amr\";"
                },
                {
                    "name": "AUDIO_AMR_WB",
                    "type": "variable",
                    "doc": "AUDIO_AMR_WB: \"audio/AMR-WB\";"
                },
                {
                    "name": "AUDIO_AMR_WB_PLUS",
                    "type": "variable",
                    "doc": "AUDIO_AMR_WB_PLUS: \"audio/amr-wb+\";"
                },
                {
                    "name": "AUDIO_ANNODEX",
                    "type": "variable",
                    "doc": "AUDIO_ANNODEX: \"audio/annodex\";"
                },
                {
                    "name": "AUDIO_APTX",
                    "type": "variable",
                    "doc": "AUDIO_APTX: \"audio/aptx\";"
                },
                {
                    "name": "AUDIO_ASC",
                    "type": "variable",
                    "doc": "AUDIO_ASC: \"audio/asc\";"
                },
                {
                    "name": "AUDIO_ATRAC3",
                    "type": "variable",
                    "doc": "AUDIO_ATRAC3: \"audio/ATRAC3\";"
                },
                {
                    "name": "AUDIO_ATRAC_ADVANCED_LOSSLESS",
                    "type": "variable",
                    "doc": "AUDIO_ATRAC_ADVANCED_LOSSLESS: \"audio/ATRAC-ADVANCED-LOSSLESS\";"
                },
                {
                    "name": "AUDIO_ATRAC_X",
                    "type": "variable",
                    "doc": "AUDIO_ATRAC_X: \"audio/ATRAC-X\";"
                },
                {
                    "name": "AUDIO_BASIC",
                    "type": "variable",
                    "doc": "AUDIO_BASIC: \"audio/basic\";"
                },
                {
                    "name": "AUDIO_BV16",
                    "type": "variable",
                    "doc": "AUDIO_BV16: \"audio/BV16\";"
                },
                {
                    "name": "AUDIO_BV32",
                    "type": "variable",
                    "doc": "AUDIO_BV32: \"audio/BV32\";"
                },
                {
                    "name": "AUDIO_CLEARMODE",
                    "type": "variable",
                    "doc": "AUDIO_CLEARMODE: \"audio/clearmode\";"
                },
                {
                    "name": "AUDIO_CN",
                    "type": "variable",
                    "doc": "AUDIO_CN: \"audio/CN\";"
                },
                {
                    "name": "AUDIO_CSOUND",
                    "type": "variable",
                    "doc": "AUDIO_CSOUND: \"audio/csound\";"
                },
                {
                    "name": "AUDIO_DAT12",
                    "type": "variable",
                    "doc": "AUDIO_DAT12: \"audio/DAT12\";"
                },
                {
                    "name": "AUDIO_DLS",
                    "type": "variable",
                    "doc": "AUDIO_DLS: \"audio/dls\";"
                },
                {
                    "name": "AUDIO_DSR_ES201108",
                    "type": "variable",
                    "doc": "AUDIO_DSR_ES201108: \"audio/dsr-es201108\";"
                },
                {
                    "name": "AUDIO_DSR_ES202050",
                    "type": "variable",
                    "doc": "AUDIO_DSR_ES202050: \"audio/dsr-es202050\";"
                },
                {
                    "name": "AUDIO_DSR_ES202211",
                    "type": "variable",
                    "doc": "AUDIO_DSR_ES202211: \"audio/dsr-es202211\";"
                },
                {
                    "name": "AUDIO_DSR_ES202212",
                    "type": "variable",
                    "doc": "AUDIO_DSR_ES202212: \"audio/dsr-es202212\";"
                },
                {
                    "name": "AUDIO_DV",
                    "type": "variable",
                    "doc": "AUDIO_DV: \"audio/DV\";"
                },
                {
                    "name": "AUDIO_DVI4",
                    "type": "variable",
                    "doc": "AUDIO_DVI4: \"audio/DVI4\";"
                },
                {
                    "name": "AUDIO_EAC3",
                    "type": "variable",
                    "doc": "AUDIO_EAC3: \"audio/eac3\";"
                },
                {
                    "name": "AUDIO_ENCAPRTP",
                    "type": "variable",
                    "doc": "AUDIO_ENCAPRTP: \"audio/encaprtp\";"
                },
                {
                    "name": "AUDIO_EVRC",
                    "type": "variable",
                    "doc": "AUDIO_EVRC: \"audio/EVRC\";"
                },
                {
                    "name": "AUDIO_EVRC0",
                    "type": "variable",
                    "doc": "AUDIO_EVRC0: \"audio/EVRC0\";"
                },
                {
                    "name": "AUDIO_EVRC1",
                    "type": "variable",
                    "doc": "AUDIO_EVRC1: \"audio/EVRC1\";"
                },
                {
                    "name": "AUDIO_EVRCB",
                    "type": "variable",
                    "doc": "AUDIO_EVRCB: \"audio/EVRCB\";"
                },
                {
                    "name": "AUDIO_EVRCB0",
                    "type": "variable",
                    "doc": "AUDIO_EVRCB0: \"audio/EVRCB0\";"
                },
                {
                    "name": "AUDIO_EVRCB1",
                    "type": "variable",
                    "doc": "AUDIO_EVRCB1: \"audio/EVRCB1\";"
                },
                {
                    "name": "AUDIO_EVRCNW",
                    "type": "variable",
                    "doc": "AUDIO_EVRCNW: \"audio/EVRCNW\";"
                },
                {
                    "name": "AUDIO_EVRCNW0",
                    "type": "variable",
                    "doc": "AUDIO_EVRCNW0: \"audio/EVRCNW0\";"
                },
                {
                    "name": "AUDIO_EVRCNW1",
                    "type": "variable",
                    "doc": "AUDIO_EVRCNW1: \"audio/EVRCNW1\";"
                },
                {
                    "name": "AUDIO_EVRCWB",
                    "type": "variable",
                    "doc": "AUDIO_EVRCWB: \"audio/EVRCWB\";"
                },
                {
                    "name": "AUDIO_EVRCWB0",
                    "type": "variable",
                    "doc": "AUDIO_EVRCWB0: \"audio/EVRCWB0\";"
                },
                {
                    "name": "AUDIO_EVRCWB1",
                    "type": "variable",
                    "doc": "AUDIO_EVRCWB1: \"audio/EVRCWB1\";"
                },
                {
                    "name": "AUDIO_EVRC_QCP",
                    "type": "variable",
                    "doc": "AUDIO_EVRC_QCP: \"audio/EVRC-QCP\";"
                },
                {
                    "name": "AUDIO_EVS",
                    "type": "variable",
                    "doc": "AUDIO_EVS: \"audio/EVS\";"
                },
                {
                    "name": "AUDIO_EXAMPLE",
                    "type": "variable",
                    "doc": "AUDIO_EXAMPLE: \"audio/example\";"
                },
                {
                    "name": "AUDIO_FLAC",
                    "type": "variable",
                    "doc": "AUDIO_FLAC: \"audio/flac\";"
                },
                {
                    "name": "AUDIO_FLEXFEC",
                    "type": "variable",
                    "doc": "AUDIO_FLEXFEC: \"audio/flexfec\";"
                },
                {
                    "name": "AUDIO_FWDRED",
                    "type": "variable",
                    "doc": "AUDIO_FWDRED: \"audio/fwdred\";"
                },
                {
                    "name": "AUDIO_G711_0",
                    "type": "variable",
                    "doc": "AUDIO_G711_0: \"audio/G711-0\";"
                },
                {
                    "name": "AUDIO_G719",
                    "type": "variable",
                    "doc": "AUDIO_G719: \"audio/G719\";"
                },
                {
                    "name": "AUDIO_G722",
                    "type": "variable",
                    "doc": "AUDIO_G722: \"audio/G722\";"
                },
                {
                    "name": "AUDIO_G7221",
                    "type": "variable",
                    "doc": "AUDIO_G7221: \"audio/G7221\";"
                },
                {
                    "name": "AUDIO_G723",
                    "type": "variable",
                    "doc": "AUDIO_G723: \"audio/G723\";"
                },
                {
                    "name": "AUDIO_G726_16",
                    "type": "variable",
                    "doc": "AUDIO_G726_16: \"audio/G726-16\";"
                },
                {
                    "name": "AUDIO_G726_24",
                    "type": "variable",
                    "doc": "AUDIO_G726_24: \"audio/G726-24\";"
                },
                {
                    "name": "AUDIO_G726_32",
                    "type": "variable",
                    "doc": "AUDIO_G726_32: \"audio/G726-32\";"
                },
                {
                    "name": "AUDIO_G726_40",
                    "type": "variable",
                    "doc": "AUDIO_G726_40: \"audio/G726-40\";"
                },
                {
                    "name": "AUDIO_G728",
                    "type": "variable",
                    "doc": "AUDIO_G728: \"audio/G728\";"
                },
                {
                    "name": "AUDIO_G729",
                    "type": "variable",
                    "doc": "AUDIO_G729: \"audio/G729\";"
                },
                {
                    "name": "AUDIO_G7291",
                    "type": "variable",
                    "doc": "AUDIO_G7291: \"audio/G7291\";"
                },
                {
                    "name": "AUDIO_G729D",
                    "type": "variable",
                    "doc": "AUDIO_G729D: \"audio/G729D\";"
                },
                {
                    "name": "AUDIO_G729E",
                    "type": "variable",
                    "doc": "AUDIO_G729E: \"audio/G729E\";"
                },
                {
                    "name": "AUDIO_GSM",
                    "type": "variable",
                    "doc": "AUDIO_GSM: \"audio/GSM\";"
                },
                {
                    "name": "AUDIO_GSM_EFR",
                    "type": "variable",
                    "doc": "AUDIO_GSM_EFR: \"audio/GSM-EFR\";"
                },
                {
                    "name": "AUDIO_GSM_HR_08",
                    "type": "variable",
                    "doc": "AUDIO_GSM_HR_08: \"audio/GSM-HR-08\";"
                },
                {
                    "name": "AUDIO_G_722_1",
                    "type": "variable",
                    "doc": "AUDIO_G_722_1: \"audio/g.722.1\";"
                },
                {
                    "name": "AUDIO_ILBC",
                    "type": "variable",
                    "doc": "AUDIO_ILBC: \"audio/iLBC\";"
                },
                {
                    "name": "AUDIO_IP_MR_V2_5",
                    "type": "variable",
                    "doc": "AUDIO_IP_MR_V2_5: \"audio/ip-mr_v2.5\";"
                },
                {
                    "name": "AUDIO_L16",
                    "type": "variable",
                    "doc": "AUDIO_L16: \"audio/L16\";"
                },
                {
                    "name": "AUDIO_L20",
                    "type": "variable",
                    "doc": "AUDIO_L20: \"audio/L20\";"
                },
                {
                    "name": "AUDIO_L24",
                    "type": "variable",
                    "doc": "AUDIO_L24: \"audio/L24\";"
                },
                {
                    "name": "AUDIO_L8",
                    "type": "variable",
                    "doc": "AUDIO_L8: \"audio/L8\";"
                },
                {
                    "name": "AUDIO_LPC",
                    "type": "variable",
                    "doc": "AUDIO_LPC: \"audio/LPC\";"
                },
                {
                    "name": "AUDIO_MATROSKA",
                    "type": "variable",
                    "doc": "AUDIO_MATROSKA: \"audio/matroska\";"
                },
                {
                    "name": "AUDIO_MELP",
                    "type": "variable",
                    "doc": "AUDIO_MELP: \"audio/MELP\";"
                },
                {
                    "name": "AUDIO_MELP1200",
                    "type": "variable",
                    "doc": "AUDIO_MELP1200: \"audio/MELP1200\";"
                },
                {
                    "name": "AUDIO_MELP2400",
                    "type": "variable",
                    "doc": "AUDIO_MELP2400: \"audio/MELP2400\";"
                },
                {
                    "name": "AUDIO_MELP600",
                    "type": "variable",
                    "doc": "AUDIO_MELP600: \"audio/MELP600\";"
                },
                {
                    "name": "AUDIO_MHAS",
                    "type": "variable",
                    "doc": "AUDIO_MHAS: \"audio/mhas\";"
                },
                {
                    "name": "AUDIO_MIDI",
                    "type": "variable",
                    "doc": "AUDIO_MIDI: \"audio/midi\";"
                },
                {
                    "name": "AUDIO_MIDI_CLIP",
                    "type": "variable",
                    "doc": "AUDIO_MIDI_CLIP: \"audio/midi-clip\";"
                },
                {
                    "name": "AUDIO_MOBILE_XMF",
                    "type": "variable",
                    "doc": "AUDIO_MOBILE_XMF: \"audio/mobile-xmf\";"
                },
                {
                    "name": "AUDIO_MP4",
                    "type": "variable",
                    "doc": "AUDIO_MP4: \"audio/mp4\";"
                },
                {
                    "name": "AUDIO_MP4A_LATM",
                    "type": "variable",
                    "doc": "AUDIO_MP4A_LATM: \"audio/MP4A-LATM\";"
                },
                {
                    "name": "AUDIO_MPA",
                    "type": "variable",
                    "doc": "AUDIO_MPA: \"audio/MPA\";"
                },
                {
                    "name": "AUDIO_MPA_ROBUST",
                    "type": "variable",
                    "doc": "AUDIO_MPA_ROBUST: \"audio/mpa-robust\";"
                },
                {
                    "name": "AUDIO_MPEG",
                    "type": "variable",
                    "doc": "AUDIO_MPEG: \"audio/mpeg\";"
                },
                {
                    "name": "AUDIO_MPEG4_GENERIC",
                    "type": "variable",
                    "doc": "AUDIO_MPEG4_GENERIC: \"audio/mpeg4-generic\";"
                },
                {
                    "name": "AUDIO_MPEGURL",
                    "type": "variable",
                    "doc": "AUDIO_MPEGURL: \"audio/mpegurl\";"
                },
                {
                    "name": "AUDIO_OGG",
                    "type": "variable",
                    "doc": "AUDIO_OGG: \"audio/ogg\";"
                },
                {
                    "name": "AUDIO_OPUS",
                    "type": "variable",
                    "doc": "AUDIO_OPUS: \"audio/opus\";"
                },
                {
                    "name": "AUDIO_PARITYFEC",
                    "type": "variable",
                    "doc": "AUDIO_PARITYFEC: \"audio/parityfec\";"
                },
                {
                    "name": "AUDIO_PCMA",
                    "type": "variable",
                    "doc": "AUDIO_PCMA: \"audio/PCMA\";"
                },
                {
                    "name": "AUDIO_PCMA_WB",
                    "type": "variable",
                    "doc": "AUDIO_PCMA_WB: \"audio/PCMA-WB\";"
                },
                {
                    "name": "AUDIO_PCMU",
                    "type": "variable",
                    "doc": "AUDIO_PCMU: \"audio/PCMU\";"
                },
                {
                    "name": "AUDIO_PCMU_WB",
                    "type": "variable",
                    "doc": "AUDIO_PCMU_WB: \"audio/PCMU-WB\";"
                },
                {
                    "name": "AUDIO_PRS_SID",
                    "type": "variable",
                    "doc": "AUDIO_PRS_SID: \"audio/prs.sid\";"
                },
                {
                    "name": "AUDIO_QCELP",
                    "type": "variable",
                    "doc": "AUDIO_QCELP: \"audio/QCELP\";"
                },
                {
                    "name": "AUDIO_RAPTORFEC",
                    "type": "variable",
                    "doc": "AUDIO_RAPTORFEC: \"audio/raptorfec\";"
                },
                {
                    "name": "AUDIO_RED",
                    "type": "variable",
                    "doc": "AUDIO_RED: \"audio/RED\";"
                },
                {
                    "name": "AUDIO_RTPLOOPBACK",
                    "type": "variable",
                    "doc": "AUDIO_RTPLOOPBACK: \"audio/rtploopback\";"
                },
                {
                    "name": "AUDIO_RTP_ENC_AESCM128",
                    "type": "variable",
                    "doc": "AUDIO_RTP_ENC_AESCM128: \"audio/rtp-enc-aescm128\";"
                },
                {
                    "name": "AUDIO_RTP_MIDI",
                    "type": "variable",
                    "doc": "AUDIO_RTP_MIDI: \"audio/rtp-midi\";"
                },
                {
                    "name": "AUDIO_RTX",
                    "type": "variable",
                    "doc": "AUDIO_RTX: \"audio/rtx\";"
                },
                {
                    "name": "AUDIO_SCIP",
                    "type": "variable",
                    "doc": "AUDIO_SCIP: \"audio/scip\";"
                },
                {
                    "name": "AUDIO_SMV",
                    "type": "variable",
                    "doc": "AUDIO_SMV: \"audio/SMV\";"
                },
                {
                    "name": "AUDIO_SMV0",
                    "type": "variable",
                    "doc": "AUDIO_SMV0: \"audio/SMV0\";"
                },
                {
                    "name": "AUDIO_SMV_QCP",
                    "type": "variable",
                    "doc": "AUDIO_SMV_QCP: \"audio/SMV-QCP\";"
                },
                {
                    "name": "AUDIO_SOFA",
                    "type": "variable",
                    "doc": "AUDIO_SOFA: \"audio/sofa\";"
                },
                {
                    "name": "AUDIO_SPEEX",
                    "type": "variable",
                    "doc": "AUDIO_SPEEX: \"audio/speex\";"
                },
                {
                    "name": "AUDIO_SP_MIDI",
                    "type": "variable",
                    "doc": "AUDIO_SP_MIDI: \"audio/sp-midi\";"
                },
                {
                    "name": "AUDIO_T140C",
                    "type": "variable",
                    "doc": "AUDIO_T140C: \"audio/t140c\";"
                },
                {
                    "name": "AUDIO_T38",
                    "type": "variable",
                    "doc": "AUDIO_T38: \"audio/t38\";"
                },
                {
                    "name": "AUDIO_TELEPHONE_EVENT",
                    "type": "variable",
                    "doc": "AUDIO_TELEPHONE_EVENT: \"audio/telephone-event\";"
                },
                {
                    "name": "AUDIO_TETRA_ACELP",
                    "type": "variable",
                    "doc": "AUDIO_TETRA_ACELP: \"audio/TETRA_ACELP\";"
                },
                {
                    "name": "AUDIO_TETRA_ACELP_BB",
                    "type": "variable",
                    "doc": "AUDIO_TETRA_ACELP_BB: \"audio/TETRA_ACELP_BB\";"
                },
                {
                    "name": "AUDIO_TONE",
                    "type": "variable",
                    "doc": "AUDIO_TONE: \"audio/tone\";"
                },
                {
                    "name": "AUDIO_TSVCIS",
                    "type": "variable",
                    "doc": "AUDIO_TSVCIS: \"audio/TSVCIS\";"
                },
                {
                    "name": "AUDIO_UEMCLIP",
                    "type": "variable",
                    "doc": "AUDIO_UEMCLIP: \"audio/UEMCLIP\";"
                },
                {
                    "name": "AUDIO_ULPFEC",
                    "type": "variable",
                    "doc": "AUDIO_ULPFEC: \"audio/ulpfec\";"
                },
                {
                    "name": "AUDIO_USAC",
                    "type": "variable",
                    "doc": "AUDIO_USAC: \"audio/usac\";"
                },
                {
                    "name": "AUDIO_VDVI",
                    "type": "variable",
                    "doc": "AUDIO_VDVI: \"audio/VDVI\";"
                },
                {
                    "name": "AUDIO_VMR_WB",
                    "type": "variable",
                    "doc": "AUDIO_VMR_WB: \"audio/VMR-WB\";"
                },
                {
                    "name": "AUDIO_VND_3GPP_IUFP",
                    "type": "variable",
                    "doc": "AUDIO_VND_3GPP_IUFP: \"audio/vnd.3gpp.iufp\";"
                },
                {
                    "name": "AUDIO_VND_4SB",
                    "type": "variable",
                    "doc": "AUDIO_VND_4SB: \"audio/vnd.4SB\";"
                },
                {
                    "name": "AUDIO_VND_AUDIOKOZ",
                    "type": "variable",
                    "doc": "AUDIO_VND_AUDIOKOZ: \"audio/vnd.audiokoz\";"
                },
                {
                    "name": "AUDIO_VND_CELP",
                    "type": "variable",
                    "doc": "AUDIO_VND_CELP: \"audio/vnd.CELP\";"
                },
                {
                    "name": "AUDIO_VND_CISCO_NSE",
                    "type": "variable",
                    "doc": "AUDIO_VND_CISCO_NSE: \"audio/vnd.cisco.nse\";"
                },
                {
                    "name": "AUDIO_VND_CMLES_RADIO_EVENTS",
                    "type": "variable",
                    "doc": "AUDIO_VND_CMLES_RADIO_EVENTS: \"audio/vnd.cmles.radio-events\";"
                },
                {
                    "name": "AUDIO_VND_CNS_ANP1",
                    "type": "variable",
                    "doc": "AUDIO_VND_CNS_ANP1: \"audio/vnd.cns.anp1\";"
                },
                {
                    "name": "AUDIO_VND_CNS_INF1",
                    "type": "variable",
                    "doc": "AUDIO_VND_CNS_INF1: \"audio/vnd.cns.inf1\";"
                },
                {
                    "name": "AUDIO_VND_DECE_AUDIO",
                    "type": "variable",
                    "doc": "AUDIO_VND_DECE_AUDIO: \"audio/vnd.dece.audio\";"
                },
                {
                    "name": "AUDIO_VND_DIGITAL_WINDS",
                    "type": "variable",
                    "doc": "AUDIO_VND_DIGITAL_WINDS: \"audio/vnd.digital-winds\";"
                },
                {
                    "name": "AUDIO_VND_DLNA_ADTS",
                    "type": "variable",
                    "doc": "AUDIO_VND_DLNA_ADTS: \"audio/vnd.dlna.adts\";"
                },
                {
                    "name": "AUDIO_VND_DOLBY_HEAAC_1",
                    "type": "variable",
                    "doc": "AUDIO_VND_DOLBY_HEAAC_1: \"audio/vnd.dolby.heaac.1\";"
                },
                {
                    "name": "AUDIO_VND_DOLBY_HEAAC_2",
                    "type": "variable",
                    "doc": "AUDIO_VND_DOLBY_HEAAC_2: \"audio/vnd.dolby.heaac.2\";"
                },
                {
                    "name": "AUDIO_VND_DOLBY_MLP",
                    "type": "variable",
                    "doc": "AUDIO_VND_DOLBY_MLP: \"audio/vnd.dolby.mlp\";"
                },
                {
                    "name": "AUDIO_VND_DOLBY_MPS",
                    "type": "variable",
                    "doc": "AUDIO_VND_DOLBY_MPS: \"audio/vnd.dolby.mps\";"
                },
                {
                    "name": "AUDIO_VND_DOLBY_PL2",
                    "type": "variable",
                    "doc": "AUDIO_VND_DOLBY_PL2: \"audio/vnd.dolby.pl2\";"
                },
                {
                    "name": "AUDIO_VND_DOLBY_PL2X",
                    "type": "variable",
                    "doc": "AUDIO_VND_DOLBY_PL2X: \"audio/vnd.dolby.pl2x\";"
                },
                {
                    "name": "AUDIO_VND_DOLBY_PL2Z",
                    "type": "variable",
                    "doc": "AUDIO_VND_DOLBY_PL2Z: \"audio/vnd.dolby.pl2z\";"
                },
                {
                    "name": "AUDIO_VND_DOLBY_PULSE_1",
                    "type": "variable",
                    "doc": "AUDIO_VND_DOLBY_PULSE_1: \"audio/vnd.dolby.pulse.1\";"
                },
                {
                    "name": "AUDIO_VND_DRA",
                    "type": "variable",
                    "doc": "AUDIO_VND_DRA: \"audio/vnd.dra\";"
                },
                {
                    "name": "AUDIO_VND_DTS",
                    "type": "variable",
                    "doc": "AUDIO_VND_DTS: \"audio/vnd.dts\";"
                },
                {
                    "name": "AUDIO_VND_DTS_HD",
                    "type": "variable",
                    "doc": "AUDIO_VND_DTS_HD: \"audio/vnd.dts.hd\";"
                },
                {
                    "name": "AUDIO_VND_DTS_UHD",
                    "type": "variable",
                    "doc": "AUDIO_VND_DTS_UHD: \"audio/vnd.dts.uhd\";"
                },
                {
                    "name": "AUDIO_VND_DVB_FILE",
                    "type": "variable",
                    "doc": "AUDIO_VND_DVB_FILE: \"audio/vnd.dvb.file\";"
                },
                {
                    "name": "AUDIO_VND_EVERAD_PLJ",
                    "type": "variable",
                    "doc": "AUDIO_VND_EVERAD_PLJ: \"audio/vnd.everad.plj\";"
                },
                {
                    "name": "AUDIO_VND_HNS_AUDIO",
                    "type": "variable",
                    "doc": "AUDIO_VND_HNS_AUDIO: \"audio/vnd.hns.audio\";"
                },
                {
                    "name": "AUDIO_VND_LUCENT_VOICE",
                    "type": "variable",
                    "doc": "AUDIO_VND_LUCENT_VOICE: \"audio/vnd.lucent.voice\";"
                },
                {
                    "name": "AUDIO_VND_MS_PLAYREADY_MEDIA_PYA",
                    "type": "variable",
                    "doc": "AUDIO_VND_MS_PLAYREADY_MEDIA_PYA: \"audio/vnd.ms-playready.media.pya\";"
                },
                {
                    "name": "AUDIO_VND_NOKIA_MOBILE_XMF",
                    "type": "variable",
                    "doc": "AUDIO_VND_NOKIA_MOBILE_XMF: \"audio/vnd.nokia.mobile-xmf\";"
                },
                {
                    "name": "AUDIO_VND_NORTEL_VBK",
                    "type": "variable",
                    "doc": "AUDIO_VND_NORTEL_VBK: \"audio/vnd.nortel.vbk\";"
                },
                {
                    "name": "AUDIO_VND_NUERA_ECELP4800",
                    "type": "variable",
                    "doc": "AUDIO_VND_NUERA_ECELP4800: \"audio/vnd.nuera.ecelp4800\";"
                },
                {
                    "name": "AUDIO_VND_NUERA_ECELP7470",
                    "type": "variable",
                    "doc": "AUDIO_VND_NUERA_ECELP7470: \"audio/vnd.nuera.ecelp7470\";"
                },
                {
                    "name": "AUDIO_VND_NUERA_ECELP9600",
                    "type": "variable",
                    "doc": "AUDIO_VND_NUERA_ECELP9600: \"audio/vnd.nuera.ecelp9600\";"
                },
                {
                    "name": "AUDIO_VND_OCTEL_SBC",
                    "type": "variable",
                    "doc": "AUDIO_VND_OCTEL_SBC: \"audio/vnd.octel.sbc\";"
                },
                {
                    "name": "AUDIO_VND_PRESONUS_MULTITRACK",
                    "type": "variable",
                    "doc": "AUDIO_VND_PRESONUS_MULTITRACK: \"audio/vnd.presonus.multitrack\";"
                },
                {
                    "name": "AUDIO_VND_QCELP",
                    "type": "variable",
                    "doc": "AUDIO_VND_QCELP: \"audio/vnd.qcelp\";"
                },
                {
                    "name": "AUDIO_VND_RHETOREX_32KADPCM",
                    "type": "variable",
                    "doc": "AUDIO_VND_RHETOREX_32KADPCM: \"audio/vnd.rhetorex.32kadpcm\";"
                },
                {
                    "name": "AUDIO_VND_RIP",
                    "type": "variable",
                    "doc": "AUDIO_VND_RIP: \"audio/vnd.rip\";"
                },
                {
                    "name": "AUDIO_VND_SEALEDMEDIA_SOFTSEAL_MPEG",
                    "type": "variable",
                    "doc": "AUDIO_VND_SEALEDMEDIA_SOFTSEAL_MPEG: \"audio/vnd.sealedmedia.softseal.mpeg\";"
                },
                {
                    "name": "AUDIO_VND_VMX_CVSD",
                    "type": "variable",
                    "doc": "AUDIO_VND_VMX_CVSD: \"audio/vnd.vmx.cvsd\";"
                },
                {
                    "name": "AUDIO_VORBIS",
                    "type": "variable",
                    "doc": "AUDIO_VORBIS: \"audio/vorbis\";"
                },
                {
                    "name": "AUDIO_VORBIS_CONFIG",
                    "type": "variable",
                    "doc": "AUDIO_VORBIS_CONFIG: \"audio/vorbis-config\";"
                },
                {
                    "name": "AUDIO_WAV",
                    "type": "variable",
                    "doc": "AUDIO_WAV: \"audio/wav\";"
                },
                {
                    "name": "AUDIO_WILDCARD",
                    "type": "variable",
                    "doc": "AUDIO_WILDCARD: \"audio/*\";"
                },
                {
                    "name": "AUDIO_X_AIFF",
                    "type": "variable",
                    "doc": "AUDIO_X_AIFF: \"audio/x-aiff\";"
                },
                {
                    "name": "AUDIO_X_GSM",
                    "type": "variable",
                    "doc": "AUDIO_X_GSM: \"audio/x-gsm\";"
                },
                {
                    "name": "AUDIO_X_MPEGURL",
                    "type": "variable",
                    "doc": "AUDIO_X_MPEGURL: \"audio/x-mpegurl\";"
                },
                {
                    "name": "AUDIO_X_MS_WAX",
                    "type": "variable",
                    "doc": "AUDIO_X_MS_WAX: \"audio/x-ms-wax\";"
                },
                {
                    "name": "AUDIO_X_MS_WMA",
                    "type": "variable",
                    "doc": "AUDIO_X_MS_WMA: \"audio/x-ms-wma\";"
                },
                {
                    "name": "AUDIO_X_PN_REALAUDIO",
                    "type": "variable",
                    "doc": "AUDIO_X_PN_REALAUDIO: \"audio/x-pn-realaudio\";"
                },
                {
                    "name": "AUDIO_X_PN_REALAUDIO_PLUGIN",
                    "type": "variable",
                    "doc": "AUDIO_X_PN_REALAUDIO_PLUGIN: \"audio/x-pn-realaudio-plugin\";"
                },
                {
                    "name": "AUDIO_X_REALAUDIO",
                    "type": "variable",
                    "doc": "AUDIO_X_REALAUDIO: \"audio/x-realaudio\";"
                },
                {
                    "name": "AUDIO_X_SCPLS",
                    "type": "variable",
                    "doc": "AUDIO_X_SCPLS: \"audio/x-scpls\";"
                },
                {
                    "name": "AUDIO_X_SD2",
                    "type": "variable",
                    "doc": "AUDIO_X_SD2: \"audio/x-sd2\";"
                },
                {
                    "name": "AUDIO_X_WAV",
                    "type": "variable",
                    "doc": "AUDIO_X_WAV: \"audio/x-wav\";"
                },
                {
                    "name": "CHEMICAL_X_ALCHEMY",
                    "type": "variable",
                    "doc": "CHEMICAL_X_ALCHEMY: \"chemical/x-alchemy\";"
                },
                {
                    "name": "CHEMICAL_X_CACHE",
                    "type": "variable",
                    "doc": "CHEMICAL_X_CACHE: \"chemical/x-cache\";"
                },
                {
                    "name": "CHEMICAL_X_CACHE_CSF",
                    "type": "variable",
                    "doc": "CHEMICAL_X_CACHE_CSF: \"chemical/x-cache-csf\";"
                },
                {
                    "name": "CHEMICAL_X_CACTVS_BINARY",
                    "type": "variable",
                    "doc": "CHEMICAL_X_CACTVS_BINARY: \"chemical/x-cactvs-binary\";"
                },
                {
                    "name": "CHEMICAL_X_CDX",
                    "type": "variable",
                    "doc": "CHEMICAL_X_CDX: \"chemical/x-cdx\";"
                },
                {
                    "name": "CHEMICAL_X_CERIUS",
                    "type": "variable",
                    "doc": "CHEMICAL_X_CERIUS: \"chemical/x-cerius\";"
                },
                {
                    "name": "CHEMICAL_X_CHEM3D",
                    "type": "variable",
                    "doc": "CHEMICAL_X_CHEM3D: \"chemical/x-chem3d\";"
                },
                {
                    "name": "CHEMICAL_X_CHEMDRAW",
                    "type": "variable",
                    "doc": "CHEMICAL_X_CHEMDRAW: \"chemical/x-chemdraw\";"
                },
                {
                    "name": "CHEMICAL_X_CIF",
                    "type": "variable",
                    "doc": "CHEMICAL_X_CIF: \"chemical/x-cif\";"
                },
                {
                    "name": "CHEMICAL_X_CMDF",
                    "type": "variable",
                    "doc": "CHEMICAL_X_CMDF: \"chemical/x-cmdf\";"
                },
                {
                    "name": "CHEMICAL_X_CML",
                    "type": "variable",
                    "doc": "CHEMICAL_X_CML: \"chemical/x-cml\";"
                },
                {
                    "name": "CHEMICAL_X_COMPASS",
                    "type": "variable",
                    "doc": "CHEMICAL_X_COMPASS: \"chemical/x-compass\";"
                },
                {
                    "name": "CHEMICAL_X_CROSSFIRE",
                    "type": "variable",
                    "doc": "CHEMICAL_X_CROSSFIRE: \"chemical/x-crossfire\";"
                },
                {
                    "name": "CHEMICAL_X_CSML",
                    "type": "variable",
                    "doc": "CHEMICAL_X_CSML: \"chemical/x-csml\";"
                },
                {
                    "name": "CHEMICAL_X_CTX",
                    "type": "variable",
                    "doc": "CHEMICAL_X_CTX: \"chemical/x-ctx\";"
                },
                {
                    "name": "CHEMICAL_X_CXF",
                    "type": "variable",
                    "doc": "CHEMICAL_X_CXF: \"chemical/x-cxf\";"
                },
                {
                    "name": "CHEMICAL_X_EMBL_DL_NUCLEOTIDE",
                    "type": "variable",
                    "doc": "CHEMICAL_X_EMBL_DL_NUCLEOTIDE: \"chemical/x-embl-dl-nucleotide\";"
                },
                {
                    "name": "CHEMICAL_X_GALACTIC_SPC",
                    "type": "variable",
                    "doc": "CHEMICAL_X_GALACTIC_SPC: \"chemical/x-galactic-spc\";"
                },
                {
                    "name": "CHEMICAL_X_GAMESS_INPUT",
                    "type": "variable",
                    "doc": "CHEMICAL_X_GAMESS_INPUT: \"chemical/x-gamess-input\";"
                },
                {
                    "name": "CHEMICAL_X_GAUSSIAN_CHECKPOINT",
                    "type": "variable",
                    "doc": "CHEMICAL_X_GAUSSIAN_CHECKPOINT: \"chemical/x-gaussian-checkpoint\";"
                },
                {
                    "name": "CHEMICAL_X_GAUSSIAN_CUBE",
                    "type": "variable",
                    "doc": "CHEMICAL_X_GAUSSIAN_CUBE: \"chemical/x-gaussian-cube\";"
                },
                {
                    "name": "CHEMICAL_X_GAUSSIAN_INPUT",
                    "type": "variable",
                    "doc": "CHEMICAL_X_GAUSSIAN_INPUT: \"chemical/x-gaussian-input\";"
                },
                {
                    "name": "CHEMICAL_X_GAUSSIAN_LOG",
                    "type": "variable",
                    "doc": "CHEMICAL_X_GAUSSIAN_LOG: \"chemical/x-gaussian-log\";"
                },
                {
                    "name": "CHEMICAL_X_GCG8_SEQUENCE",
                    "type": "variable",
                    "doc": "CHEMICAL_X_GCG8_SEQUENCE: \"chemical/x-gcg8-sequence\";"
                },
                {
                    "name": "CHEMICAL_X_GENBANK",
                    "type": "variable",
                    "doc": "CHEMICAL_X_GENBANK: \"chemical/x-genbank\";"
                },
                {
                    "name": "CHEMICAL_X_HIN",
                    "type": "variable",
                    "doc": "CHEMICAL_X_HIN: \"chemical/x-hin\";"
                },
                {
                    "name": "CHEMICAL_X_ISOSTAR",
                    "type": "variable",
                    "doc": "CHEMICAL_X_ISOSTAR: \"chemical/x-isostar\";"
                },
                {
                    "name": "CHEMICAL_X_JCAMP_DX",
                    "type": "variable",
                    "doc": "CHEMICAL_X_JCAMP_DX: \"chemical/x-jcamp-dx\";"
                },
                {
                    "name": "CHEMICAL_X_KINEMAGE",
                    "type": "variable",
                    "doc": "CHEMICAL_X_KINEMAGE: \"chemical/x-kinemage\";"
                },
                {
                    "name": "CHEMICAL_X_MACMOLECULE",
                    "type": "variable",
                    "doc": "CHEMICAL_X_MACMOLECULE: \"chemical/x-macmolecule\";"
                },
                {
                    "name": "CHEMICAL_X_MACROMODEL_INPUT",
                    "type": "variable",
                    "doc": "CHEMICAL_X_MACROMODEL_INPUT: \"chemical/x-macromodel-input\";"
                },
                {
                    "name": "CHEMICAL_X_MDL_MOLFILE",
                    "type": "variable",
                    "doc": "CHEMICAL_X_MDL_MOLFILE: \"chemical/x-mdl-molfile\";"
                },
                {
                    "name": "CHEMICAL_X_MDL_RDFILE",
                    "type": "variable",
                    "doc": "CHEMICAL_X_MDL_RDFILE: \"chemical/x-mdl-rdfile\";"
                },
                {
                    "name": "CHEMICAL_X_MDL_RXNFILE",
                    "type": "variable",
                    "doc": "CHEMICAL_X_MDL_RXNFILE: \"chemical/x-mdl-rxnfile\";"
                },
                {
                    "name": "CHEMICAL_X_MDL_SDFILE",
                    "type": "variable",
                    "doc": "CHEMICAL_X_MDL_SDFILE: \"chemical/x-mdl-sdfile\";"
                },
                {
                    "name": "CHEMICAL_X_MDL_TGF",
                    "type": "variable",
                    "doc": "CHEMICAL_X_MDL_TGF: \"chemical/x-mdl-tgf\";"
                },
                {
                    "name": "CHEMICAL_X_MMCIF",
                    "type": "variable",
                    "doc": "CHEMICAL_X_MMCIF: \"chemical/x-mmcif\";"
                },
                {
                    "name": "CHEMICAL_X_MOL2",
                    "type": "variable",
                    "doc": "CHEMICAL_X_MOL2: \"chemical/x-mol2\";"
                },
                {
                    "name": "CHEMICAL_X_MOLCONN_Z",
                    "type": "variable",
                    "doc": "CHEMICAL_X_MOLCONN_Z: \"chemical/x-molconn-Z\";"
                },
                {
                    "name": "CHEMICAL_X_MOPAC_GRAPH",
                    "type": "variable",
                    "doc": "CHEMICAL_X_MOPAC_GRAPH: \"chemical/x-mopac-graph\";"
                },
                {
                    "name": "CHEMICAL_X_MOPAC_INPUT",
                    "type": "variable",
                    "doc": "CHEMICAL_X_MOPAC_INPUT: \"chemical/x-mopac-input\";"
                },
                {
                    "name": "CHEMICAL_X_MOPAC_OUT",
                    "type": "variable",
                    "doc": "CHEMICAL_X_MOPAC_OUT: \"chemical/x-mopac-out\";"
                },
                {
                    "name": "CHEMICAL_X_MOPAC_VIB",
                    "type": "variable",
                    "doc": "CHEMICAL_X_MOPAC_VIB: \"chemical/x-mopac-vib\";"
                },
                {
                    "name": "CHEMICAL_X_NCBI_ASN1",
                    "type": "variable",
                    "doc": "CHEMICAL_X_NCBI_ASN1: \"chemical/x-ncbi-asn1\";"
                },
                {
                    "name": "CHEMICAL_X_NCBI_ASN1_ASCII",
                    "type": "variable",
                    "doc": "CHEMICAL_X_NCBI_ASN1_ASCII: \"chemical/x-ncbi-asn1-ascii\";"
                },
                {
                    "name": "CHEMICAL_X_NCBI_ASN1_BINARY",
                    "type": "variable",
                    "doc": "CHEMICAL_X_NCBI_ASN1_BINARY: \"chemical/x-ncbi-asn1-binary\";"
                },
                {
                    "name": "CHEMICAL_X_NCBI_ASN1_SPEC",
                    "type": "variable",
                    "doc": "CHEMICAL_X_NCBI_ASN1_SPEC: \"chemical/x-ncbi-asn1-spec\";"
                },
                {
                    "name": "CHEMICAL_X_PDB",
                    "type": "variable",
                    "doc": "CHEMICAL_X_PDB: \"chemical/x-pdb\";"
                },
                {
                    "name": "CHEMICAL_X_ROSDAL",
                    "type": "variable",
                    "doc": "CHEMICAL_X_ROSDAL: \"chemical/x-rosdal\";"
                },
                {
                    "name": "CHEMICAL_X_SWISSPROT",
                    "type": "variable",
                    "doc": "CHEMICAL_X_SWISSPROT: \"chemical/x-swissprot\";"
                },
                {
                    "name": "CHEMICAL_X_VAMAS_ISO14976",
                    "type": "variable",
                    "doc": "CHEMICAL_X_VAMAS_ISO14976: \"chemical/x-vamas-iso14976\";"
                },
                {
                    "name": "CHEMICAL_X_VMD",
                    "type": "variable",
                    "doc": "CHEMICAL_X_VMD: \"chemical/x-vmd\";"
                },
                {
                    "name": "CHEMICAL_X_XTEL",
                    "type": "variable",
                    "doc": "CHEMICAL_X_XTEL: \"chemical/x-xtel\";"
                },
                {
                    "name": "CHEMICAL_X_XYZ",
                    "type": "variable",
                    "doc": "CHEMICAL_X_XYZ: \"chemical/x-xyz\";"
                },
                {
                    "name": "FONT_COLLECTION",
                    "type": "variable",
                    "doc": "FONT_COLLECTION: \"font/collection\";"
                },
                {
                    "name": "FONT_OTF",
                    "type": "variable",
                    "doc": "FONT_OTF: \"font/otf\";"
                },
                {
                    "name": "FONT_SFNT",
                    "type": "variable",
                    "doc": "FONT_SFNT: \"font/sfnt\";"
                },
                {
                    "name": "FONT_TTF",
                    "type": "variable",
                    "doc": "FONT_TTF: \"font/ttf\";"
                },
                {
                    "name": "FONT_WOFF",
                    "type": "variable",
                    "doc": "FONT_WOFF: \"font/woff\";"
                },
                {
                    "name": "FONT_WOFF2",
                    "type": "variable",
                    "doc": "FONT_WOFF2: \"font/woff2\";"
                },
                {
                    "name": "IMAGE_ACES",
                    "type": "variable",
                    "doc": "IMAGE_ACES: \"image/aces\";"
                },
                {
                    "name": "IMAGE_APNG",
                    "type": "variable",
                    "doc": "IMAGE_APNG: \"image/apng\";"
                },
                {
                    "name": "IMAGE_AVCI",
                    "type": "variable",
                    "doc": "IMAGE_AVCI: \"image/avci\";"
                },
                {
                    "name": "IMAGE_AVCS",
                    "type": "variable",
                    "doc": "IMAGE_AVCS: \"image/avcs\";"
                },
                {
                    "name": "IMAGE_AVIF",
                    "type": "variable",
                    "doc": "IMAGE_AVIF: \"image/avif\";"
                },
                {
                    "name": "IMAGE_BMP",
                    "type": "variable",
                    "doc": "IMAGE_BMP: \"image/bmp\";"
                },
                {
                    "name": "IMAGE_CGM",
                    "type": "variable",
                    "doc": "IMAGE_CGM: \"image/cgm\";"
                },
                {
                    "name": "IMAGE_DICOM_RLE",
                    "type": "variable",
                    "doc": "IMAGE_DICOM_RLE: \"image/dicom-rle\";"
                },
                {
                    "name": "IMAGE_DPX",
                    "type": "variable",
                    "doc": "IMAGE_DPX: \"image/dpx\";"
                },
                {
                    "name": "IMAGE_EMF",
                    "type": "variable",
                    "doc": "IMAGE_EMF: \"image/emf\";"
                },
                {
                    "name": "IMAGE_EXAMPLE",
                    "type": "variable",
                    "doc": "IMAGE_EXAMPLE: \"image/example\";"
                },
                {
                    "name": "IMAGE_FITS",
                    "type": "variable",
                    "doc": "IMAGE_FITS: \"image/fits\";"
                },
                {
                    "name": "IMAGE_G3FAX",
                    "type": "variable",
                    "doc": "IMAGE_G3FAX: \"image/g3fax\";"
                },
                {
                    "name": "IMAGE_GIF",
                    "type": "variable",
                    "doc": "IMAGE_GIF: \"image/gif\";"
                },
                {
                    "name": "IMAGE_HEIC",
                    "type": "variable",
                    "doc": "IMAGE_HEIC: \"image/heic\";"
                },
                {
                    "name": "IMAGE_HEIC_SEQUENCE",
                    "type": "variable",
                    "doc": "IMAGE_HEIC_SEQUENCE: \"image/heic-sequence\";"
                },
                {
                    "name": "IMAGE_HEIF",
                    "type": "variable",
                    "doc": "IMAGE_HEIF: \"image/heif\";"
                },
                {
                    "name": "IMAGE_HEIF_SEQUENCE",
                    "type": "variable",
                    "doc": "IMAGE_HEIF_SEQUENCE: \"image/heif-sequence\";"
                },
                {
                    "name": "IMAGE_HEJ2K",
                    "type": "variable",
                    "doc": "IMAGE_HEJ2K: \"image/hej2k\";"
                },
                {
                    "name": "IMAGE_HSJ2",
                    "type": "variable",
                    "doc": "IMAGE_HSJ2: \"image/hsj2\";"
                },
                {
                    "name": "IMAGE_IEF",
                    "type": "variable",
                    "doc": "IMAGE_IEF: \"image/ief\";"
                },
                {
                    "name": "IMAGE_J2C",
                    "type": "variable",
                    "doc": "IMAGE_J2C: \"image/j2c\";"
                },
                {
                    "name": "IMAGE_JLS",
                    "type": "variable",
                    "doc": "IMAGE_JLS: \"image/jls\";"
                },
                {
                    "name": "IMAGE_JP2",
                    "type": "variable",
                    "doc": "IMAGE_JP2: \"image/jp2\";"
                },
                {
                    "name": "IMAGE_JPEG",
                    "type": "variable",
                    "doc": "IMAGE_JPEG: \"image/jpeg\";"
                },
                {
                    "name": "IMAGE_JPH",
                    "type": "variable",
                    "doc": "IMAGE_JPH: \"image/jph\";"
                },
                {
                    "name": "IMAGE_JPHC",
                    "type": "variable",
                    "doc": "IMAGE_JPHC: \"image/jphc\";"
                },
                {
                    "name": "IMAGE_JPM",
                    "type": "variable",
                    "doc": "IMAGE_JPM: \"image/jpm\";"
                },
                {
                    "name": "IMAGE_JPX",
                    "type": "variable",
                    "doc": "IMAGE_JPX: \"image/jpx\";"
                },
                {
                    "name": "IMAGE_JXL",
                    "type": "variable",
                    "doc": "IMAGE_JXL: \"image/jxl\";"
                },
                {
                    "name": "IMAGE_JXR",
                    "type": "variable",
                    "doc": "IMAGE_JXR: \"image/jxr\";"
                },
                {
                    "name": "IMAGE_JXRA",
                    "type": "variable",
                    "doc": "IMAGE_JXRA: \"image/jxrA\";"
                },
                {
                    "name": "IMAGE_JXRS",
                    "type": "variable",
                    "doc": "IMAGE_JXRS: \"image/jxrS\";"
                },
                {
                    "name": "IMAGE_JXS",
                    "type": "variable",
                    "doc": "IMAGE_JXS: \"image/jxs\";"
                },
                {
                    "name": "IMAGE_JXSC",
                    "type": "variable",
                    "doc": "IMAGE_JXSC: \"image/jxsc\";"
                },
                {
                    "name": "IMAGE_JXSI",
                    "type": "variable",
                    "doc": "IMAGE_JXSI: \"image/jxsi\";"
                },
                {
                    "name": "IMAGE_JXSS",
                    "type": "variable",
                    "doc": "IMAGE_JXSS: \"image/jxss\";"
                },
                {
                    "name": "IMAGE_KTX",
                    "type": "variable",
                    "doc": "IMAGE_KTX: \"image/ktx\";"
                },
                {
                    "name": "IMAGE_KTX2",
                    "type": "variable",
                    "doc": "IMAGE_KTX2: \"image/ktx2\";"
                },
                {
                    "name": "IMAGE_NAPLPS",
                    "type": "variable",
                    "doc": "IMAGE_NAPLPS: \"image/naplps\";"
                },
                {
                    "name": "IMAGE_PCX",
                    "type": "variable",
                    "doc": "IMAGE_PCX: \"image/pcx\";"
                },
                {
                    "name": "IMAGE_PNG",
                    "type": "variable",
                    "doc": "IMAGE_PNG: \"image/png\";"
                },
                {
                    "name": "IMAGE_PRS_BTIF",
                    "type": "variable",
                    "doc": "IMAGE_PRS_BTIF: \"image/prs.btif\";"
                },
                {
                    "name": "IMAGE_PRS_PTI",
                    "type": "variable",
                    "doc": "IMAGE_PRS_PTI: \"image/prs.pti\";"
                },
                {
                    "name": "IMAGE_PWG_RASTER",
                    "type": "variable",
                    "doc": "IMAGE_PWG_RASTER: \"image/pwg-raster\";"
                },
                {
                    "name": "IMAGE_SVG_XML",
                    "type": "variable",
                    "doc": "IMAGE_SVG_XML: \"image/svg+xml\";"
                },
                {
                    "name": "IMAGE_T38",
                    "type": "variable",
                    "doc": "IMAGE_T38: \"image/t38\";"
                },
                {
                    "name": "IMAGE_TIFF",
                    "type": "variable",
                    "doc": "IMAGE_TIFF: \"image/tiff\";"
                },
                {
                    "name": "IMAGE_TIFF_FX",
                    "type": "variable",
                    "doc": "IMAGE_TIFF_FX: \"image/tiff-fx\";"
                },
                {
                    "name": "IMAGE_VND_ADOBE_PHOTOSHOP",
                    "type": "variable",
                    "doc": "IMAGE_VND_ADOBE_PHOTOSHOP: \"image/vnd.adobe.photoshop\";"
                },
                {
                    "name": "IMAGE_VND_AIRZIP_ACCELERATOR_AZV",
                    "type": "variable",
                    "doc": "IMAGE_VND_AIRZIP_ACCELERATOR_AZV: \"image/vnd.airzip.accelerator.azv\";"
                },
                {
                    "name": "IMAGE_VND_CNS_INF2",
                    "type": "variable",
                    "doc": "IMAGE_VND_CNS_INF2: \"image/vnd.cns.inf2\";"
                },
                {
                    "name": "IMAGE_VND_DECE_GRAPHIC",
                    "type": "variable",
                    "doc": "IMAGE_VND_DECE_GRAPHIC: \"image/vnd.dece.graphic\";"
                },
                {
                    "name": "IMAGE_VND_DJVU",
                    "type": "variable",
                    "doc": "IMAGE_VND_DJVU: \"image/vnd.djvu\";"
                },
                {
                    "name": "IMAGE_VND_DVB_SUBTITLE",
                    "type": "variable",
                    "doc": "IMAGE_VND_DVB_SUBTITLE: \"image/vnd.dvb.subtitle\";"
                },
                {
                    "name": "IMAGE_VND_DWG",
                    "type": "variable",
                    "doc": "IMAGE_VND_DWG: \"image/vnd.dwg\";"
                },
                {
                    "name": "IMAGE_VND_DXF",
                    "type": "variable",
                    "doc": "IMAGE_VND_DXF: \"image/vnd.dxf\";"
                },
                {
                    "name": "IMAGE_VND_FASTBIDSHEET",
                    "type": "variable",
                    "doc": "IMAGE_VND_FASTBIDSHEET: \"image/vnd.fastbidsheet\";"
                },
                {
                    "name": "IMAGE_VND_FPX",
                    "type": "variable",
                    "doc": "IMAGE_VND_FPX: \"image/vnd.fpx\";"
                },
                {
                    "name": "IMAGE_VND_FST",
                    "type": "variable",
                    "doc": "IMAGE_VND_FST: \"image/vnd.fst\";"
                },
                {
                    "name": "IMAGE_VND_FUJIXEROX_EDMICS_MMR",
                    "type": "variable",
                    "doc": "IMAGE_VND_FUJIXEROX_EDMICS_MMR: \"image/vnd.fujixerox.edmics-mmr\";"
                },
                {
                    "name": "IMAGE_VND_FUJIXEROX_EDMICS_RLC",
                    "type": "variable",
                    "doc": "IMAGE_VND_FUJIXEROX_EDMICS_RLC: \"image/vnd.fujixerox.edmics-rlc\";"
                },
                {
                    "name": "IMAGE_VND_GLOBALGRAPHICS_PGB",
                    "type": "variable",
                    "doc": "IMAGE_VND_GLOBALGRAPHICS_PGB: \"image/vnd.globalgraphics.pgb\";"
                },
                {
                    "name": "IMAGE_VND_MICROSOFT_ICON",
                    "type": "variable",
                    "doc": "IMAGE_VND_MICROSOFT_ICON: \"image/vnd.microsoft.icon\";"
                },
                {
                    "name": "IMAGE_VND_MIX",
                    "type": "variable",
                    "doc": "IMAGE_VND_MIX: \"image/vnd.mix\";"
                },
                {
                    "name": "IMAGE_VND_MOZILLA_APNG",
                    "type": "variable",
                    "doc": "IMAGE_VND_MOZILLA_APNG: \"image/vnd.mozilla.apng\";"
                },
                {
                    "name": "IMAGE_VND_MS_MODI",
                    "type": "variable",
                    "doc": "IMAGE_VND_MS_MODI: \"image/vnd.ms-modi\";"
                },
                {
                    "name": "IMAGE_VND_NET_FPX",
                    "type": "variable",
                    "doc": "IMAGE_VND_NET_FPX: \"image/vnd.net-fpx\";"
                },
                {
                    "name": "IMAGE_VND_PCO_B16",
                    "type": "variable",
                    "doc": "IMAGE_VND_PCO_B16: \"image/vnd.pco.b16\";"
                },
                {
                    "name": "IMAGE_VND_RADIANCE",
                    "type": "variable",
                    "doc": "IMAGE_VND_RADIANCE: \"image/vnd.radiance\";"
                },
                {
                    "name": "IMAGE_VND_SEALEDMEDIA_SOFTSEAL_GIF",
                    "type": "variable",
                    "doc": "IMAGE_VND_SEALEDMEDIA_SOFTSEAL_GIF: \"image/vnd.sealedmedia.softseal.gif\";"
                },
                {
                    "name": "IMAGE_VND_SEALEDMEDIA_SOFTSEAL_JPG",
                    "type": "variable",
                    "doc": "IMAGE_VND_SEALEDMEDIA_SOFTSEAL_JPG: \"image/vnd.sealedmedia.softseal.jpg\";"
                },
                {
                    "name": "IMAGE_VND_SEALED_PNG",
                    "type": "variable",
                    "doc": "IMAGE_VND_SEALED_PNG: \"image/vnd.sealed.png\";"
                },
                {
                    "name": "IMAGE_VND_SVF",
                    "type": "variable",
                    "doc": "IMAGE_VND_SVF: \"image/vnd.svf\";"
                },
                {
                    "name": "IMAGE_VND_TENCENT_TAP",
                    "type": "variable",
                    "doc": "IMAGE_VND_TENCENT_TAP: \"image/vnd.tencent.tap\";"
                },
                {
                    "name": "IMAGE_VND_VALVE_SOURCE_TEXTURE",
                    "type": "variable",
                    "doc": "IMAGE_VND_VALVE_SOURCE_TEXTURE: \"image/vnd.valve.source.texture\";"
                },
                {
                    "name": "IMAGE_VND_WAP_WBMP",
                    "type": "variable",
                    "doc": "IMAGE_VND_WAP_WBMP: \"image/vnd.wap.wbmp\";"
                },
                {
                    "name": "IMAGE_VND_XIFF",
                    "type": "variable",
                    "doc": "IMAGE_VND_XIFF: \"image/vnd.xiff\";"
                },
                {
                    "name": "IMAGE_VND_ZBRUSH_PCX",
                    "type": "variable",
                    "doc": "IMAGE_VND_ZBRUSH_PCX: \"image/vnd.zbrush.pcx\";"
                },
                {
                    "name": "IMAGE_WEBP",
                    "type": "variable",
                    "doc": "IMAGE_WEBP: \"image/webp\";"
                },
                {
                    "name": "IMAGE_WILDCARD",
                    "type": "variable",
                    "doc": "IMAGE_WILDCARD: \"image/*\";"
                },
                {
                    "name": "IMAGE_WMF",
                    "type": "variable",
                    "doc": "IMAGE_WMF: \"image/wmf\";"
                },
                {
                    "name": "IMAGE_X_CANON_CR2",
                    "type": "variable",
                    "doc": "IMAGE_X_CANON_CR2: \"image/x-canon-cr2\";"
                },
                {
                    "name": "IMAGE_X_CANON_CRW",
                    "type": "variable",
                    "doc": "IMAGE_X_CANON_CRW: \"image/x-canon-crw\";"
                },
                {
                    "name": "IMAGE_X_CMU_RAST",
                    "type": "variable",
                    "doc": "IMAGE_X_CMU_RAST: \"image/x-cmu-rast\";"
                },
                {
                    "name": "IMAGE_X_CMU_RASTER",
                    "type": "variable",
                    "doc": "IMAGE_X_CMU_RASTER: \"image/x-cmu-raster\";"
                },
                {
                    "name": "IMAGE_X_CORELDRAW",
                    "type": "variable",
                    "doc": "IMAGE_X_CORELDRAW: \"image/x-coreldraw\";"
                },
                {
                    "name": "IMAGE_X_CORELDRAWPATTERN",
                    "type": "variable",
                    "doc": "IMAGE_X_CORELDRAWPATTERN: \"image/x-coreldrawpattern\";"
                },
                {
                    "name": "IMAGE_X_CORELDRAWTEMPLATE",
                    "type": "variable",
                    "doc": "IMAGE_X_CORELDRAWTEMPLATE: \"image/x-coreldrawtemplate\";"
                },
                {
                    "name": "IMAGE_X_CORELPHOTOPAINT",
                    "type": "variable",
                    "doc": "IMAGE_X_CORELPHOTOPAINT: \"image/x-corelphotopaint\";"
                },
                {
                    "name": "IMAGE_X_EMF",
                    "type": "variable",
                    "doc": "IMAGE_X_EMF: \"image/emf\";"
                },
                {
                    "name": "IMAGE_X_EPSON_ERF",
                    "type": "variable",
                    "doc": "IMAGE_X_EPSON_ERF: \"image/x-epson-erf\";"
                },
                {
                    "name": "IMAGE_X_ICON",
                    "type": "variable",
                    "doc": "IMAGE_X_ICON: \"image/x-icon\";"
                },
                {
                    "name": "IMAGE_X_JG",
                    "type": "variable",
                    "doc": "IMAGE_X_JG: \"image/x-jg\";"
                },
                {
                    "name": "IMAGE_X_JNG",
                    "type": "variable",
                    "doc": "IMAGE_X_JNG: \"image/x-jng\";"
                },
                {
                    "name": "IMAGE_X_MS_BMP",
                    "type": "variable",
                    "doc": "IMAGE_X_MS_BMP: \"image/x-ms-bmp\";"
                },
                {
                    "name": "IMAGE_X_NIKON_NEF",
                    "type": "variable",
                    "doc": "IMAGE_X_NIKON_NEF: \"image/x-nikon-nef\";"
                },
                {
                    "name": "IMAGE_X_OLYMPUS_ORF",
                    "type": "variable",
                    "doc": "IMAGE_X_OLYMPUS_ORF: \"image/x-olympus-orf\";"
                },
                {
                    "name": "IMAGE_X_PHOTOSHOP",
                    "type": "variable",
                    "doc": "IMAGE_X_PHOTOSHOP: \"image/x-photoshop\";"
                },
                {
                    "name": "IMAGE_X_PORTABLE_ANYMAP",
                    "type": "variable",
                    "doc": "IMAGE_X_PORTABLE_ANYMAP: \"image/x-portable-anymap\";"
                },
                {
                    "name": "IMAGE_X_PORTABLE_BITMAP",
                    "type": "variable",
                    "doc": "IMAGE_X_PORTABLE_BITMAP: \"image/x-portable-bitmap\";"
                },
                {
                    "name": "IMAGE_X_PORTABLE_GRAYMAP",
                    "type": "variable",
                    "doc": "IMAGE_X_PORTABLE_GRAYMAP: \"image/x-portable-graymap\";"
                },
                {
                    "name": "IMAGE_X_PORTABLE_PIXMAP",
                    "type": "variable",
                    "doc": "IMAGE_X_PORTABLE_PIXMAP: \"image/x-portable-pixmap\";"
                },
                {
                    "name": "IMAGE_X_RGB",
                    "type": "variable",
                    "doc": "IMAGE_X_RGB: \"image/x-rgb\";"
                },
                {
                    "name": "IMAGE_X_UP_WPNG",
                    "type": "variable",
                    "doc": "IMAGE_X_UP_WPNG: \"image/x-up-wpng\";"
                },
                {
                    "name": "IMAGE_X_WMF",
                    "type": "variable",
                    "doc": "IMAGE_X_WMF: \"image/wmf\";"
                },
                {
                    "name": "IMAGE_X_XBITMAP",
                    "type": "variable",
                    "doc": "IMAGE_X_XBITMAP: \"image/x-xbitmap\";"
                },
                {
                    "name": "IMAGE_X_XPIXMAP",
                    "type": "variable",
                    "doc": "IMAGE_X_XPIXMAP: \"image/x-xpixmap\";"
                },
                {
                    "name": "IMAGE_X_XWINDOWDUMP",
                    "type": "variable",
                    "doc": "IMAGE_X_XWINDOWDUMP: \"image/x-xwindowdump\";"
                },
                {
                    "name": "INODE_BLOCKDEVICE",
                    "type": "variable",
                    "doc": "INODE_BLOCKDEVICE: \"inode/blockdevice\";"
                },
                {
                    "name": "INODE_CHARDEVICE",
                    "type": "variable",
                    "doc": "INODE_CHARDEVICE: \"inode/chardevice\";"
                },
                {
                    "name": "INODE_DIRECTORY",
                    "type": "variable",
                    "doc": "INODE_DIRECTORY: \"inode/directory\";"
                },
                {
                    "name": "INODE_DIRECTORY_LOCKED",
                    "type": "variable",
                    "doc": "INODE_DIRECTORY_LOCKED: \"inode/directory-locked\";"
                },
                {
                    "name": "INODE_FIFO",
                    "type": "variable",
                    "doc": "INODE_FIFO: \"inode/fifo\";"
                },
                {
                    "name": "INODE_SOCKET",
                    "type": "variable",
                    "doc": "INODE_SOCKET: \"inode/socket\";"
                },
                {
                    "name": "MEDIA_TYPE_WILDCARD",
                    "type": "variable",
                    "doc": "MEDIA_TYPE_WILDCARD: \"*\";"
                },
                {
                    "name": "MESSAGE_BHTTP",
                    "type": "variable",
                    "doc": "MESSAGE_BHTTP: \"message/bhttp\";"
                },
                {
                    "name": "MESSAGE_CPIM",
                    "type": "variable",
                    "doc": "MESSAGE_CPIM: \"message/CPIM\";"
                },
                {
                    "name": "MESSAGE_DELIVERY_STATUS",
                    "type": "variable",
                    "doc": "MESSAGE_DELIVERY_STATUS: \"message/delivery-status\";"
                },
                {
                    "name": "MESSAGE_DISPOSITION_NOTIFICATION",
                    "type": "variable",
                    "doc": "MESSAGE_DISPOSITION_NOTIFICATION: \"message/disposition-notification\";"
                },
                {
                    "name": "MESSAGE_EXAMPLE",
                    "type": "variable",
                    "doc": "MESSAGE_EXAMPLE: \"message/example\";"
                },
                {
                    "name": "MESSAGE_EXTERNAL_BODY",
                    "type": "variable",
                    "doc": "MESSAGE_EXTERNAL_BODY: \"message/external-body\";"
                },
                {
                    "name": "MESSAGE_FEEDBACK_REPORT",
                    "type": "variable",
                    "doc": "MESSAGE_FEEDBACK_REPORT: \"message/feedback-report\";"
                },
                {
                    "name": "MESSAGE_GLOBAL",
                    "type": "variable",
                    "doc": "MESSAGE_GLOBAL: \"message/global\";"
                },
                {
                    "name": "MESSAGE_GLOBAL_DELIVERY_STATUS",
                    "type": "variable",
                    "doc": "MESSAGE_GLOBAL_DELIVERY_STATUS: \"message/global-delivery-status\";"
                },
                {
                    "name": "MESSAGE_GLOBAL_DISPOSITION_NOTIFICATION",
                    "type": "variable",
                    "doc": "MESSAGE_GLOBAL_DISPOSITION_NOTIFICATION: \"message/global-disposition-notification\";"
                },
                {
                    "name": "MESSAGE_GLOBAL_HEADERS",
                    "type": "variable",
                    "doc": "MESSAGE_GLOBAL_HEADERS: \"message/global-headers\";"
                },
                {
                    "name": "MESSAGE_HTTP",
                    "type": "variable",
                    "doc": "MESSAGE_HTTP: \"message/http\";"
                },
                {
                    "name": "MESSAGE_IMDN_XML",
                    "type": "variable",
                    "doc": "MESSAGE_IMDN_XML: \"message/imdn+xml\";"
                },
                {
                    "name": "MESSAGE_MLS",
                    "type": "variable",
                    "doc": "MESSAGE_MLS: \"message/mls\";"
                },
                {
                    "name": "MESSAGE_NEWS",
                    "type": "variable",
                    "doc": "MESSAGE_NEWS: \"message/news\";"
                },
                {
                    "name": "MESSAGE_OHTTP_REQ",
                    "type": "variable",
                    "doc": "MESSAGE_OHTTP_REQ: \"message/ohttp-req\";"
                },
                {
                    "name": "MESSAGE_OHTTP_RES",
                    "type": "variable",
                    "doc": "MESSAGE_OHTTP_RES: \"message/ohttp-res\";"
                },
                {
                    "name": "MESSAGE_RFC822",
                    "type": "variable",
                    "doc": "MESSAGE_RFC822: \"message/rfc822\";"
                },
                {
                    "name": "MESSAGE_SIP",
                    "type": "variable",
                    "doc": "MESSAGE_SIP: \"message/sip\";"
                },
                {
                    "name": "MESSAGE_SIPFRAG",
                    "type": "variable",
                    "doc": "MESSAGE_SIPFRAG: \"message/sipfrag\";"
                },
                {
                    "name": "MESSAGE_S_HTTP",
                    "type": "variable",
                    "doc": "MESSAGE_S_HTTP: \"message/s-http\";"
                },
                {
                    "name": "MESSAGE_TRACKING_STATUS",
                    "type": "variable",
                    "doc": "MESSAGE_TRACKING_STATUS: \"message/tracking-status\";"
                },
                {
                    "name": "MESSAGE_VND_SI_SIMP",
                    "type": "variable",
                    "doc": "MESSAGE_VND_SI_SIMP: \"message/vnd.si.simp\";"
                },
                {
                    "name": "MESSAGE_VND_WFA_WSC",
                    "type": "variable",
                    "doc": "MESSAGE_VND_WFA_WSC: \"message/vnd.wfa.wsc\";"
                },
                {
                    "name": "MODEL_3MF",
                    "type": "variable",
                    "doc": "MODEL_3MF: \"model/3mf\";"
                },
                {
                    "name": "MODEL_E57",
                    "type": "variable",
                    "doc": "MODEL_E57: \"model/e57\";"
                },
                {
                    "name": "MODEL_EXAMPLE",
                    "type": "variable",
                    "doc": "MODEL_EXAMPLE: \"model/example\";"
                },
                {
                    "name": "MODEL_GLTF_BINARY",
                    "type": "variable",
                    "doc": "MODEL_GLTF_BINARY: \"model/gltf-binary\";"
                },
                {
                    "name": "MODEL_GLTF_JSON",
                    "type": "variable",
                    "doc": "MODEL_GLTF_JSON: \"model/gltf+json\";"
                },
                {
                    "name": "MODEL_IGES",
                    "type": "variable",
                    "doc": "MODEL_IGES: \"model/iges\";"
                },
                {
                    "name": "MODEL_JT",
                    "type": "variable",
                    "doc": "MODEL_JT: \"model/JT\";"
                },
                {
                    "name": "MODEL_MESH",
                    "type": "variable",
                    "doc": "MODEL_MESH: \"model/mesh\";"
                },
                {
                    "name": "MODEL_MTL",
                    "type": "variable",
                    "doc": "MODEL_MTL: \"model/mtl\";"
                },
                {
                    "name": "MODEL_OBJ",
                    "type": "variable",
                    "doc": "MODEL_OBJ: \"model/obj\";"
                },
                {
                    "name": "MODEL_PRC",
                    "type": "variable",
                    "doc": "MODEL_PRC: \"model/prc\";"
                },
                {
                    "name": "MODEL_STEP",
                    "type": "variable",
                    "doc": "MODEL_STEP: \"model/step\";"
                },
                {
                    "name": "MODEL_STEP_XML",
                    "type": "variable",
                    "doc": "MODEL_STEP_XML: \"model/step+xml\";"
                },
                {
                    "name": "MODEL_STEP_XML_ZIP",
                    "type": "variable",
                    "doc": "MODEL_STEP_XML_ZIP: \"model/step-xml+zip\";"
                },
                {
                    "name": "MODEL_STEP_ZIP",
                    "type": "variable",
                    "doc": "MODEL_STEP_ZIP: \"model/step+zip\";"
                },
                {
                    "name": "MODEL_STL",
                    "type": "variable",
                    "doc": "MODEL_STL: \"model/stl\";"
                },
                {
                    "name": "MODEL_U3D",
                    "type": "variable",
                    "doc": "MODEL_U3D: \"model/u3d\";"
                },
                {
                    "name": "MODEL_VND_BARY",
                    "type": "variable",
                    "doc": "MODEL_VND_BARY: \"model/vnd.bary\";"
                },
                {
                    "name": "MODEL_VND_CLD",
                    "type": "variable",
                    "doc": "MODEL_VND_CLD: \"model/vnd.cld\";"
                },
                {
                    "name": "MODEL_VND_COLLADA_XML",
                    "type": "variable",
                    "doc": "MODEL_VND_COLLADA_XML: \"model/vnd.collada+xml\";"
                },
                {
                    "name": "MODEL_VND_DWF",
                    "type": "variable",
                    "doc": "MODEL_VND_DWF: \"model/vnd.dwf\";"
                },
                {
                    "name": "MODEL_VND_FLATLAND_3DML",
                    "type": "variable",
                    "doc": "MODEL_VND_FLATLAND_3DML: \"model/vnd.flatland.3dml\";"
                },
                {
                    "name": "MODEL_VND_GDL",
                    "type": "variable",
                    "doc": "MODEL_VND_GDL: \"model/vnd.gdl\";"
                },
                {
                    "name": "MODEL_VND_GS_GDL",
                    "type": "variable",
                    "doc": "MODEL_VND_GS_GDL: \"model/vnd.gs-gdl\";"
                },
                {
                    "name": "MODEL_VND_GTW",
                    "type": "variable",
                    "doc": "MODEL_VND_GTW: \"model/vnd.gtw\";"
                },
                {
                    "name": "MODEL_VND_MOML_XML",
                    "type": "variable",
                    "doc": "MODEL_VND_MOML_XML: \"model/vnd.moml+xml\";"
                },
                {
                    "name": "MODEL_VND_MTS",
                    "type": "variable",
                    "doc": "MODEL_VND_MTS: \"model/vnd.mts\";"
                },
                {
                    "name": "MODEL_VND_OPENGEX",
                    "type": "variable",
                    "doc": "MODEL_VND_OPENGEX: \"model/vnd.opengex\";"
                },
                {
                    "name": "MODEL_VND_PARASOLID_TRANSMIT_BINARY",
                    "type": "variable",
                    "doc": "MODEL_VND_PARASOLID_TRANSMIT_BINARY: \"model/vnd.parasolid.transmit.binary\";"
                },
                {
                    "name": "MODEL_VND_PARASOLID_TRANSMIT_TEXT",
                    "type": "variable",
                    "doc": "MODEL_VND_PARASOLID_TRANSMIT_TEXT: \"model/vnd.parasolid.transmit.text\";"
                },
                {
                    "name": "MODEL_VND_PYTHA_PYOX",
                    "type": "variable",
                    "doc": "MODEL_VND_PYTHA_PYOX: \"model/vnd.pytha.pyox\";"
                },
                {
                    "name": "MODEL_VND_ROSETTE_ANNOTATED_DATA_MODEL",
                    "type": "variable",
                    "doc": "MODEL_VND_ROSETTE_ANNOTATED_DATA_MODEL: \"model/vnd.rosette.annotated-data-model\";"
                },
                {
                    "name": "MODEL_VND_SAP_VDS",
                    "type": "variable",
                    "doc": "MODEL_VND_SAP_VDS: \"model/vnd.sap.vds\";"
                },
                {
                    "name": "MODEL_VND_USDA",
                    "type": "variable",
                    "doc": "MODEL_VND_USDA: \"model/vnd.usda\";"
                },
                {
                    "name": "MODEL_VND_USDZ_ZIP",
                    "type": "variable",
                    "doc": "MODEL_VND_USDZ_ZIP: \"model/vnd.usdz+zip\";"
                },
                {
                    "name": "MODEL_VND_VALVE_SOURCE_COMPILED_MAP",
                    "type": "variable",
                    "doc": "MODEL_VND_VALVE_SOURCE_COMPILED_MAP: \"model/vnd.valve.source.compiled-map\";"
                },
                {
                    "name": "MODEL_VND_VTU",
                    "type": "variable",
                    "doc": "MODEL_VND_VTU: \"model/vnd.vtu\";"
                },
                {
                    "name": "MODEL_VRML",
                    "type": "variable",
                    "doc": "MODEL_VRML: \"model/vrml\";"
                },
                {
                    "name": "MODEL_X3D_BINARY",
                    "type": "variable",
                    "doc": "MODEL_X3D_BINARY: \"model/x3d+binary\";"
                },
                {
                    "name": "MODEL_X3D_FASTINFOSET",
                    "type": "variable",
                    "doc": "MODEL_X3D_FASTINFOSET: \"model/x3d+fastinfoset\";"
                },
                {
                    "name": "MODEL_X3D_VRML",
                    "type": "variable",
                    "doc": "MODEL_X3D_VRML: \"model/x3d-vrml\";"
                },
                {
                    "name": "MODEL_X3D_XML",
                    "type": "variable",
                    "doc": "MODEL_X3D_XML: \"model/x3d+xml\";"
                },
                {
                    "name": "MULTIPART_ALTERNATIVE",
                    "type": "variable",
                    "doc": "MULTIPART_ALTERNATIVE: \"multipart/alternative\";"
                },
                {
                    "name": "MULTIPART_APPLEDOUBLE",
                    "type": "variable",
                    "doc": "MULTIPART_APPLEDOUBLE: \"multipart/appledouble\";"
                },
                {
                    "name": "MULTIPART_BYTERANGES",
                    "type": "variable",
                    "doc": "MULTIPART_BYTERANGES: \"multipart/byteranges\";"
                },
                {
                    "name": "MULTIPART_BYTERANTES",
                    "type": "variable",
                    "doc": "MULTIPART_BYTERANTES: \"multipart/byterantes\";"
                },
                {
                    "name": "MULTIPART_DIGEST",
                    "type": "variable",
                    "doc": "MULTIPART_DIGEST: \"multipart/digest\";"
                },
                {
                    "name": "MULTIPART_ENCRYPTED",
                    "type": "variable",
                    "doc": "MULTIPART_ENCRYPTED: \"multipart/encrypted\";"
                },
                {
                    "name": "MULTIPART_EXAMPLE",
                    "type": "variable",
                    "doc": "MULTIPART_EXAMPLE: \"multipart/example\";"
                },
                {
                    "name": "MULTIPART_FORM_DATA",
                    "type": "variable",
                    "doc": "MULTIPART_FORM_DATA: \"multipart/form-data\";"
                },
                {
                    "name": "MULTIPART_HEADER_SET",
                    "type": "variable",
                    "doc": "MULTIPART_HEADER_SET: \"multipart/header-set\";"
                },
                {
                    "name": "MULTIPART_MIXED",
                    "type": "variable",
                    "doc": "MULTIPART_MIXED: \"multipart/mixed\";"
                },
                {
                    "name": "MULTIPART_MULTILINGUAL",
                    "type": "variable",
                    "doc": "MULTIPART_MULTILINGUAL: \"multipart/multilingual\";"
                },
                {
                    "name": "MULTIPART_PARALLEL",
                    "type": "variable",
                    "doc": "MULTIPART_PARALLEL: \"multipart/parallel\";"
                },
                {
                    "name": "MULTIPART_RELATED",
                    "type": "variable",
                    "doc": "MULTIPART_RELATED: \"multipart/related\";"
                },
                {
                    "name": "MULTIPART_REPORT",
                    "type": "variable",
                    "doc": "MULTIPART_REPORT: \"multipart/report\";"
                },
                {
                    "name": "MULTIPART_SIGNED",
                    "type": "variable",
                    "doc": "MULTIPART_SIGNED: \"multipart/signed\";"
                },
                {
                    "name": "MULTIPART_VND_BINT_MED_PLUS",
                    "type": "variable",
                    "doc": "MULTIPART_VND_BINT_MED_PLUS: \"multipart/vnd.bint.med-plus\";"
                },
                {
                    "name": "MULTIPART_VOICE_MESSAGE",
                    "type": "variable",
                    "doc": "MULTIPART_VOICE_MESSAGE: \"multipart/voice-message\";"
                },
                {
                    "name": "MULTIPART_WILDCARD",
                    "type": "variable",
                    "doc": "MULTIPART_WILDCARD: \"multipart/*\";"
                },
                {
                    "name": "MULTIPART_X_MIXED_REPLACE",
                    "type": "variable",
                    "doc": "MULTIPART_X_MIXED_REPLACE: \"multipart/x-mixed-replace\";"
                },
                {
                    "name": "SERVER_SENT_EVENTS",
                    "type": "variable",
                    "doc": "SERVER_SENT_EVENTS: \"text/event-stream\";"
                },
                {
                    "name": "TEXT_1D_INTERLEAVED_PARITYFEC",
                    "type": "variable",
                    "doc": "TEXT_1D_INTERLEAVED_PARITYFEC: \"text/1d-interleaved-parityfec\";"
                },
                {
                    "name": "TEXT_CACHE_MANIFEST",
                    "type": "variable",
                    "doc": "TEXT_CACHE_MANIFEST: \"text/cache-manifest\";"
                },
                {
                    "name": "TEXT_CALENDAR",
                    "type": "variable",
                    "doc": "TEXT_CALENDAR: \"text/calendar\";"
                },
                {
                    "name": "TEXT_CQL",
                    "type": "variable",
                    "doc": "TEXT_CQL: \"text/cql\";"
                },
                {
                    "name": "TEXT_CQL_EXPRESSION",
                    "type": "variable",
                    "doc": "TEXT_CQL_EXPRESSION: \"text/cql-expression\";"
                },
                {
                    "name": "TEXT_CQL_IDENTIFIER",
                    "type": "variable",
                    "doc": "TEXT_CQL_IDENTIFIER: \"text/cql-identifier\";"
                },
                {
                    "name": "TEXT_CSS",
                    "type": "variable",
                    "doc": "TEXT_CSS: \"text/css\";"
                },
                {
                    "name": "TEXT_CSV",
                    "type": "variable",
                    "doc": "TEXT_CSV: \"text/csv\";"
                },
                {
                    "name": "TEXT_CSV_SCHEMA",
                    "type": "variable",
                    "doc": "TEXT_CSV_SCHEMA: \"text/csv-schema\";"
                },
                {
                    "name": "TEXT_DIRECTORY",
                    "type": "variable",
                    "doc": "TEXT_DIRECTORY: \"text/directory\";"
                },
                {
                    "name": "TEXT_DIRECTORY_PROFILE_VCARD",
                    "type": "variable",
                    "doc": "TEXT_DIRECTORY_PROFILE_VCARD: \"text/directory;profile=vCard\";"
                },
                {
                    "name": "TEXT_DNS",
                    "type": "variable",
                    "doc": "TEXT_DNS: \"text/dns\";"
                },
                {
                    "name": "TEXT_ECMASCRIPT",
                    "type": "variable",
                    "doc": "TEXT_ECMASCRIPT: \"text/ecmascript\";"
                },
                {
                    "name": "TEXT_ENCAPRTP",
                    "type": "variable",
                    "doc": "TEXT_ENCAPRTP: \"text/encaprtp\";"
                },
                {
                    "name": "TEXT_ENGLISH",
                    "type": "variable",
                    "doc": "TEXT_ENGLISH: \"text/english\";"
                },
                {
                    "name": "TEXT_ENRICHED",
                    "type": "variable",
                    "doc": "TEXT_ENRICHED: \"text/enriched\";"
                },
                {
                    "name": "TEXT_EVENT_STREAM",
                    "type": "variable",
                    "doc": "TEXT_EVENT_STREAM: \"text/event-stream\";"
                },
                {
                    "name": "TEXT_EXAMPLE",
                    "type": "variable",
                    "doc": "TEXT_EXAMPLE: \"text/example\";"
                },
                {
                    "name": "TEXT_FHIRPATH",
                    "type": "variable",
                    "doc": "TEXT_FHIRPATH: \"text/fhirpath\";"
                },
                {
                    "name": "TEXT_FLEXFEC",
                    "type": "variable",
                    "doc": "TEXT_FLEXFEC: \"text/flexfec\";"
                },
                {
                    "name": "TEXT_FWDRED",
                    "type": "variable",
                    "doc": "TEXT_FWDRED: \"text/fwdred\";"
                },
                {
                    "name": "TEXT_GFF3",
                    "type": "variable",
                    "doc": "TEXT_GFF3: \"text/gff3\";"
                },
                {
                    "name": "TEXT_GRAMMAR_REF_LIST",
                    "type": "variable",
                    "doc": "TEXT_GRAMMAR_REF_LIST: \"text/grammar-ref-list\";"
                },
                {
                    "name": "TEXT_H323",
                    "type": "variable",
                    "doc": "TEXT_H323: \"text/h323\";"
                },
                {
                    "name": "TEXT_HL7V2",
                    "type": "variable",
                    "doc": "TEXT_HL7V2: \"text/hl7v2\";"
                },
                {
                    "name": "TEXT_HTML",
                    "type": "variable",
                    "doc": "TEXT_HTML: \"text/html\";"
                },
                {
                    "name": "TEXT_IULS",
                    "type": "variable",
                    "doc": "TEXT_IULS: \"text/iuls\";"
                },
                {
                    "name": "TEXT_JAVASCRIPT",
                    "type": "variable",
                    "doc": "TEXT_JAVASCRIPT: \"text/javascript\";"
                },
                {
                    "name": "TEXT_JCR_CND",
                    "type": "variable",
                    "doc": "TEXT_JCR_CND: \"text/jcr-cnd\";"
                },
                {
                    "name": "TEXT_MARKDOWN",
                    "type": "variable",
                    "doc": "TEXT_MARKDOWN: \"text/markdown\";"
                },
                {
                    "name": "TEXT_MATHML",
                    "type": "variable",
                    "doc": "TEXT_MATHML: \"text/mathml\";"
                },
                {
                    "name": "TEXT_MIZAR",
                    "type": "variable",
                    "doc": "TEXT_MIZAR: \"text/mizar\";"
                },
                {
                    "name": "TEXT_N3",
                    "type": "variable",
                    "doc": "TEXT_N3: \"text/n3\";"
                },
                {
                    "name": "TEXT_PARAMETERS",
                    "type": "variable",
                    "doc": "TEXT_PARAMETERS: \"text/parameters\";"
                },
                {
                    "name": "TEXT_PARITYFEC",
                    "type": "variable",
                    "doc": "TEXT_PARITYFEC: \"text/parityfec\";"
                },
                {
                    "name": "TEXT_PLAIN",
                    "type": "variable",
                    "doc": "TEXT_PLAIN: \"text/plain\";"
                },
                {
                    "name": "TEXT_PROVENANCE_NOTATION",
                    "type": "variable",
                    "doc": "TEXT_PROVENANCE_NOTATION: \"text/provenance-notation\";"
                },
                {
                    "name": "TEXT_PRS_FALLENSTEIN_RST",
                    "type": "variable",
                    "doc": "TEXT_PRS_FALLENSTEIN_RST: \"text/prs.fallenstein.rst\";"
                },
                {
                    "name": "TEXT_PRS_LINES_TAG",
                    "type": "variable",
                    "doc": "TEXT_PRS_LINES_TAG: \"text/prs.lines.tag\";"
                },
                {
                    "name": "TEXT_PRS_PROP_LOGIC",
                    "type": "variable",
                    "doc": "TEXT_PRS_PROP_LOGIC: \"text/prs.prop.logic\";"
                },
                {
                    "name": "TEXT_PRS_TEXI",
                    "type": "variable",
                    "doc": "TEXT_PRS_TEXI: \"text/prs.texi\";"
                },
                {
                    "name": "TEXT_RAPTORFEC",
                    "type": "variable",
                    "doc": "TEXT_RAPTORFEC: \"text/raptorfec\";"
                },
                {
                    "name": "TEXT_RED",
                    "type": "variable",
                    "doc": "TEXT_RED: \"text/RED\";"
                },
                {
                    "name": "TEXT_RFC822_HEADERS",
                    "type": "variable",
                    "doc": "TEXT_RFC822_HEADERS: \"text/rfc822-headers\";"
                },
                {
                    "name": "TEXT_RICHTEXT",
                    "type": "variable",
                    "doc": "TEXT_RICHTEXT: \"text/richtext\";"
                },
                {
                    "name": "TEXT_RTF",
                    "type": "variable",
                    "doc": "TEXT_RTF: \"text/rtf\";"
                },
                {
                    "name": "TEXT_RTPLOOPBACK",
                    "type": "variable",
                    "doc": "TEXT_RTPLOOPBACK: \"text/rtploopback\";"
                },
                {
                    "name": "TEXT_RTP_ENC_AESCM128",
                    "type": "variable",
                    "doc": "TEXT_RTP_ENC_AESCM128: \"text/rtp-enc-aescm128\";"
                },
                {
                    "name": "TEXT_RTX",
                    "type": "variable",
                    "doc": "TEXT_RTX: \"text/rtx\";"
                },
                {
                    "name": "TEXT_SCRIPTLET",
                    "type": "variable",
                    "doc": "TEXT_SCRIPTLET: \"text/scriptlet\";"
                },
                {
                    "name": "TEXT_SGML",
                    "type": "variable",
                    "doc": "TEXT_SGML: \"text/SGML\";"
                },
                {
                    "name": "TEXT_SHACLC",
                    "type": "variable",
                    "doc": "TEXT_SHACLC: \"text/shaclc\";"
                },
                {
                    "name": "TEXT_SHEX",
                    "type": "variable",
                    "doc": "TEXT_SHEX: \"text/shex\";"
                },
                {
                    "name": "TEXT_SPDX",
                    "type": "variable",
                    "doc": "TEXT_SPDX: \"text/spdx\";"
                },
                {
                    "name": "TEXT_STRINGS",
                    "type": "variable",
                    "doc": "TEXT_STRINGS: \"text/strings\";"
                },
                {
                    "name": "TEXT_T140",
                    "type": "variable",
                    "doc": "TEXT_T140: \"text/t140\";"
                },
                {
                    "name": "TEXT_TAB_SEPARATED_VALUES",
                    "type": "variable",
                    "doc": "TEXT_TAB_SEPARATED_VALUES: \"text/tab-separated-values\";"
                },
                {
                    "name": "TEXT_TEXMACS",
                    "type": "variable",
                    "doc": "TEXT_TEXMACS: \"text/texmacs\";"
                },
                {
                    "name": "TEXT_TROFF",
                    "type": "variable",
                    "doc": "TEXT_TROFF: \"text/troff\";"
                },
                {
                    "name": "TEXT_TURTLE",
                    "type": "variable",
                    "doc": "TEXT_TURTLE: \"text/turtle\";"
                },
                {
                    "name": "TEXT_ULPFEC",
                    "type": "variable",
                    "doc": "TEXT_ULPFEC: \"text/ulpfec\";"
                },
                {
                    "name": "TEXT_URI_LIST",
                    "type": "variable",
                    "doc": "TEXT_URI_LIST: \"text/uri-list\";"
                },
                {
                    "name": "TEXT_VCARD",
                    "type": "variable",
                    "doc": "TEXT_VCARD: \"text/vcard\";"
                },
                {
                    "name": "TEXT_VND_A",
                    "type": "variable",
                    "doc": "TEXT_VND_A: \"text/vnd.a\";"
                },
                {
                    "name": "TEXT_VND_ABC",
                    "type": "variable",
                    "doc": "TEXT_VND_ABC: \"text/vnd.abc\";"
                },
                {
                    "name": "TEXT_VND_ASCII_ART",
                    "type": "variable",
                    "doc": "TEXT_VND_ASCII_ART: \"text/vnd.ascii-art\";"
                },
                {
                    "name": "TEXT_VND_CURL",
                    "type": "variable",
                    "doc": "TEXT_VND_CURL: \"text/vnd.curl\";"
                },
                {
                    "name": "TEXT_VND_DEBIAN_COPYRIGHT",
                    "type": "variable",
                    "doc": "TEXT_VND_DEBIAN_COPYRIGHT: \"text/vnd.debian.copyright\";"
                },
                {
                    "name": "TEXT_VND_DMCLIENTSCRIPT",
                    "type": "variable",
                    "doc": "TEXT_VND_DMCLIENTSCRIPT: \"text/vnd.DMClientScript\";"
                },
                {
                    "name": "TEXT_VND_DVB_SUBTITLE",
                    "type": "variable",
                    "doc": "TEXT_VND_DVB_SUBTITLE: \"text/vnd.dvb.subtitle\";"
                },
                {
                    "name": "TEXT_VND_ESMERTEC_THEME_DESCRIPTOR",
                    "type": "variable",
                    "doc": "TEXT_VND_ESMERTEC_THEME_DESCRIPTOR: \"text/vnd.esmertec.theme-descriptor\";"
                },
                {
                    "name": "TEXT_VND_EXCHANGEABLE",
                    "type": "variable",
                    "doc": "TEXT_VND_EXCHANGEABLE: \"text/vnd.exchangeable\";"
                },
                {
                    "name": "TEXT_VND_FAMILYSEARCH_GEDCOM",
                    "type": "variable",
                    "doc": "TEXT_VND_FAMILYSEARCH_GEDCOM: \"text/vnd.familysearch.gedcom\";"
                },
                {
                    "name": "TEXT_VND_FICLAB_FLT",
                    "type": "variable",
                    "doc": "TEXT_VND_FICLAB_FLT: \"text/vnd.ficlab.flt\";"
                },
                {
                    "name": "TEXT_VND_FLATLAND_3DML",
                    "type": "variable",
                    "doc": "TEXT_VND_FLATLAND_3DML: \"text/vnd.flatland.3dml\";"
                },
                {
                    "name": "TEXT_VND_FLY",
                    "type": "variable",
                    "doc": "TEXT_VND_FLY: \"text/vnd.fly\";"
                },
                {
                    "name": "TEXT_VND_FMI_FLEXSTOR",
                    "type": "variable",
                    "doc": "TEXT_VND_FMI_FLEXSTOR: \"text/vnd.fmi.flexstor\";"
                },
                {
                    "name": "TEXT_VND_GML",
                    "type": "variable",
                    "doc": "TEXT_VND_GML: \"text/vnd.gml\";"
                },
                {
                    "name": "TEXT_VND_GRAPHVIZ",
                    "type": "variable",
                    "doc": "TEXT_VND_GRAPHVIZ: \"text/vnd.graphviz\";"
                },
                {
                    "name": "TEXT_VND_HANS",
                    "type": "variable",
                    "doc": "TEXT_VND_HANS: \"text/vnd.hans\";"
                },
                {
                    "name": "TEXT_VND_HGL",
                    "type": "variable",
                    "doc": "TEXT_VND_HGL: \"text/vnd.hgl\";"
                },
                {
                    "name": "TEXT_VND_IN3D_3DML",
                    "type": "variable",
                    "doc": "TEXT_VND_IN3D_3DML: \"text/vnd.in3d.3dml\";"
                },
                {
                    "name": "TEXT_VND_IN3D_SPOT",
                    "type": "variable",
                    "doc": "TEXT_VND_IN3D_SPOT: \"text/vnd.in3d.spot\";"
                },
                {
                    "name": "TEXT_VND_IPTC_NEWSML",
                    "type": "variable",
                    "doc": "TEXT_VND_IPTC_NEWSML: \"text/vnd.IPTC.NewsML\";"
                },
                {
                    "name": "TEXT_VND_IPTC_NITF",
                    "type": "variable",
                    "doc": "TEXT_VND_IPTC_NITF: \"text/vnd.IPTC.NITF\";"
                },
                {
                    "name": "TEXT_VND_LATEX_Z",
                    "type": "variable",
                    "doc": "TEXT_VND_LATEX_Z: \"text/vnd.latex-z\";"
                },
                {
                    "name": "TEXT_VND_MOTOROLA_REFLEX",
                    "type": "variable",
                    "doc": "TEXT_VND_MOTOROLA_REFLEX: \"text/vnd.motorola.reflex\";"
                },
                {
                    "name": "TEXT_VND_MS_MEDIAPACKAGE",
                    "type": "variable",
                    "doc": "TEXT_VND_MS_MEDIAPACKAGE: \"text/vnd.ms-mediapackage\";"
                },
                {
                    "name": "TEXT_VND_NET2PHONE_COMMCENTER_COMMAND",
                    "type": "variable",
                    "doc": "TEXT_VND_NET2PHONE_COMMCENTER_COMMAND: \"text/vnd.net2phone.commcenter.command\";"
                },
                {
                    "name": "TEXT_VND_RADISYS_MSML_BASIC_LAYOUT",
                    "type": "variable",
                    "doc": "TEXT_VND_RADISYS_MSML_BASIC_LAYOUT: \"text/vnd.radisys.msml-basic-layout\";"
                },
                {
                    "name": "TEXT_VND_SENX_WARPSCRIPT",
                    "type": "variable",
                    "doc": "TEXT_VND_SENX_WARPSCRIPT: \"text/vnd.senx.warpscript\";"
                },
                {
                    "name": "TEXT_VND_SI_URICATALOGUE",
                    "type": "variable",
                    "doc": "TEXT_VND_SI_URICATALOGUE: \"text/vnd.si.uricatalogue\";"
                },
                {
                    "name": "TEXT_VND_SOSI",
                    "type": "variable",
                    "doc": "TEXT_VND_SOSI: \"text/vnd.sosi\";"
                },
                {
                    "name": "TEXT_VND_SUN_J2ME_APP_DESCRIPTOR",
                    "type": "variable",
                    "doc": "TEXT_VND_SUN_J2ME_APP_DESCRIPTOR: \"text/vnd.sun.j2me.app-descriptor\";"
                },
                {
                    "name": "TEXT_VND_TROLLTECH_LINGUIST",
                    "type": "variable",
                    "doc": "TEXT_VND_TROLLTECH_LINGUIST: \"text/vnd.trolltech.linguist\";"
                },
                {
                    "name": "TEXT_VND_WAP_CO",
                    "type": "variable",
                    "doc": "TEXT_VND_WAP_CO: \"text/vnd.wap.co\";"
                },
                {
                    "name": "TEXT_VND_WAP_CONNECTIVITY_XML",
                    "type": "variable",
                    "doc": "TEXT_VND_WAP_CONNECTIVITY_XML: \"text/vnd.wap.connectivity-xml\";"
                },
                {
                    "name": "TEXT_VND_WAP_EMN_XML",
                    "type": "variable",
                    "doc": "TEXT_VND_WAP_EMN_XML: \"text/vnd.wap.emn+xml\";"
                },
                {
                    "name": "TEXT_VND_WAP_SI",
                    "type": "variable",
                    "doc": "TEXT_VND_WAP_SI: \"text/vnd.wap.si\";"
                },
                {
                    "name": "TEXT_VND_WAP_SL",
                    "type": "variable",
                    "doc": "TEXT_VND_WAP_SL: \"text/vnd.wap.sl\";"
                },
                {
                    "name": "TEXT_VND_WAP_WML",
                    "type": "variable",
                    "doc": "TEXT_VND_WAP_WML: \"text/vnd.wap.wml\";"
                },
                {
                    "name": "TEXT_VND_WAP_WMLSCRIPT",
                    "type": "variable",
                    "doc": "TEXT_VND_WAP_WMLSCRIPT: \"text/vnd.wap.wmlscript\";"
                },
                {
                    "name": "TEXT_VND_WAP_WTA_EVENT",
                    "type": "variable",
                    "doc": "TEXT_VND_WAP_WTA_EVENT: \"text/vnd.wap.wta-event\";"
                },
                {
                    "name": "TEXT_VTT",
                    "type": "variable",
                    "doc": "TEXT_VTT: \"text/vtt\";"
                },
                {
                    "name": "TEXT_WGSL",
                    "type": "variable",
                    "doc": "TEXT_WGSL: \"text/wgsl\";"
                },
                {
                    "name": "TEXT_WILDCARD",
                    "type": "variable",
                    "doc": "TEXT_WILDCARD: \"text/*\";"
                },
                {
                    "name": "TEXT_XML",
                    "type": "variable",
                    "doc": "TEXT_XML: \"text/xml\";"
                },
                {
                    "name": "TEXT_XML_EXTERNAL_PARSED_ENTITY",
                    "type": "variable",
                    "doc": "TEXT_XML_EXTERNAL_PARSED_ENTITY: \"text/xml-external-parsed-entity\";"
                },
                {
                    "name": "TEXT_X_BIBTEX",
                    "type": "variable",
                    "doc": "TEXT_X_BIBTEX: \"text/x-bibtex\";"
                },
                {
                    "name": "TEXT_X_BOO",
                    "type": "variable",
                    "doc": "TEXT_X_BOO: \"text/x-boo\";"
                },
                {
                    "name": "TEXT_X_CHDR",
                    "type": "variable",
                    "doc": "TEXT_X_CHDR: \"text/x-chdr\";"
                },
                {
                    "name": "TEXT_X_COMPONENT",
                    "type": "variable",
                    "doc": "TEXT_X_COMPONENT: \"text/x-component\";"
                },
                {
                    "name": "TEXT_X_CRONTAB",
                    "type": "variable",
                    "doc": "TEXT_X_CRONTAB: \"text/x-crontab\";"
                },
                {
                    "name": "TEXT_X_CSH",
                    "type": "variable",
                    "doc": "TEXT_X_CSH: \"text/x-csh\";"
                },
                {
                    "name": "TEXT_X_CSRC",
                    "type": "variable",
                    "doc": "TEXT_X_CSRC: \"text/x-csrc\";"
                },
                {
                    "name": "TEXT_X_C_HDR",
                    "type": "variable",
                    "doc": "TEXT_X_C_HDR: \"text/x-c++hdr\";"
                },
                {
                    "name": "TEXT_X_C_SRC",
                    "type": "variable",
                    "doc": "TEXT_X_C_SRC: \"text/x-c++src\";"
                },
                {
                    "name": "TEXT_X_DIFF",
                    "type": "variable",
                    "doc": "TEXT_X_DIFF: \"text/x-diff\";"
                },
                {
                    "name": "TEXT_X_DSRC",
                    "type": "variable",
                    "doc": "TEXT_X_DSRC: \"text/x-dsrc\";"
                },
                {
                    "name": "TEXT_X_HASKELL",
                    "type": "variable",
                    "doc": "TEXT_X_HASKELL: \"text/x-haskell\";"
                },
                {
                    "name": "TEXT_X_HDML",
                    "type": "variable",
                    "doc": "TEXT_X_HDML: \"text/x-hdml\";"
                },
                {
                    "name": "TEXT_X_JAVA",
                    "type": "variable",
                    "doc": "TEXT_X_JAVA: \"text/x-java\";"
                },
                {
                    "name": "TEXT_X_LILYPOND",
                    "type": "variable",
                    "doc": "TEXT_X_LILYPOND: \"text/x-lilypond\";"
                },
                {
                    "name": "TEXT_X_LITERATE_HASKELL",
                    "type": "variable",
                    "doc": "TEXT_X_LITERATE_HASKELL: \"text/x-literate-haskell\";"
                },
                {
                    "name": "TEXT_X_MAKEFILE",
                    "type": "variable",
                    "doc": "TEXT_X_MAKEFILE: \"text/x-makefile\";"
                },
                {
                    "name": "TEXT_X_MOC",
                    "type": "variable",
                    "doc": "TEXT_X_MOC: \"text/x-moc\";"
                },
                {
                    "name": "TEXT_X_PASCAL",
                    "type": "variable",
                    "doc": "TEXT_X_PASCAL: \"text/x-pascal\";"
                },
                {
                    "name": "TEXT_X_PCS_GCD",
                    "type": "variable",
                    "doc": "TEXT_X_PCS_GCD: \"text/x-pcs-gcd\";"
                },
                {
                    "name": "TEXT_X_PERL",
                    "type": "variable",
                    "doc": "TEXT_X_PERL: \"text/x-perl\";"
                },
                {
                    "name": "TEXT_X_PYTHON",
                    "type": "variable",
                    "doc": "TEXT_X_PYTHON: \"text/x-python\";"
                },
                {
                    "name": "TEXT_X_SCALA",
                    "type": "variable",
                    "doc": "TEXT_X_SCALA: \"text/x-scala\";"
                },
                {
                    "name": "TEXT_X_SERVER_PARSED_HTML",
                    "type": "variable",
                    "doc": "TEXT_X_SERVER_PARSED_HTML: \"text/x-server-parsed-html\";"
                },
                {
                    "name": "TEXT_X_SETEXT",
                    "type": "variable",
                    "doc": "TEXT_X_SETEXT: \"text/x-setext\";"
                },
                {
                    "name": "TEXT_X_SFV",
                    "type": "variable",
                    "doc": "TEXT_X_SFV: \"text/x-sfv\";"
                },
                {
                    "name": "TEXT_X_SH",
                    "type": "variable",
                    "doc": "TEXT_X_SH: \"text/x-sh\";"
                },
                {
                    "name": "TEXT_X_TCL",
                    "type": "variable",
                    "doc": "TEXT_X_TCL: \"text/x-tcl\";"
                },
                {
                    "name": "TEXT_X_TEX",
                    "type": "variable",
                    "doc": "TEXT_X_TEX: \"text/x-tex\";"
                },
                {
                    "name": "TEXT_X_TTML",
                    "type": "variable",
                    "doc": "TEXT_X_TTML: \"text/x-ttml\";"
                },
                {
                    "name": "TEXT_X_VCALENDAR",
                    "type": "variable",
                    "doc": "TEXT_X_VCALENDAR: \"text/x-vcalendar\";"
                },
                {
                    "name": "VIDEO_1D_INTERLEAVED_PARITYFEC",
                    "type": "variable",
                    "doc": "VIDEO_1D_INTERLEAVED_PARITYFEC: \"video/1d-interleaved-parityfec\";"
                },
                {
                    "name": "VIDEO_3GPP",
                    "type": "variable",
                    "doc": "VIDEO_3GPP: \"video/3gpp\";"
                },
                {
                    "name": "VIDEO_3GPP2",
                    "type": "variable",
                    "doc": "VIDEO_3GPP2: \"video/3gpp2\";"
                },
                {
                    "name": "VIDEO_3GPP_TT",
                    "type": "variable",
                    "doc": "VIDEO_3GPP_TT: \"video/3gpp-tt\";"
                },
                {
                    "name": "VIDEO_ANNODEX",
                    "type": "variable",
                    "doc": "VIDEO_ANNODEX: \"video/annodex\";"
                },
                {
                    "name": "VIDEO_AV1",
                    "type": "variable",
                    "doc": "VIDEO_AV1: \"video/AV1\";"
                },
                {
                    "name": "VIDEO_BMPEG",
                    "type": "variable",
                    "doc": "VIDEO_BMPEG: \"video/BMPEG\";"
                },
                {
                    "name": "VIDEO_BT656",
                    "type": "variable",
                    "doc": "VIDEO_BT656: \"video/BT656\";"
                },
                {
                    "name": "VIDEO_CELB",
                    "type": "variable",
                    "doc": "VIDEO_CELB: \"video/CelB\";"
                },
                {
                    "name": "VIDEO_DL",
                    "type": "variable",
                    "doc": "VIDEO_DL: \"video/dl\";"
                },
                {
                    "name": "VIDEO_DV",
                    "type": "variable",
                    "doc": "VIDEO_DV: \"video/DV\";"
                },
                {
                    "name": "VIDEO_ENCAPRTP",
                    "type": "variable",
                    "doc": "VIDEO_ENCAPRTP: \"video/encaprtp\";"
                },
                {
                    "name": "VIDEO_EVC",
                    "type": "variable",
                    "doc": "VIDEO_EVC: \"video/evc\";"
                },
                {
                    "name": "VIDEO_EXAMPLE",
                    "type": "variable",
                    "doc": "VIDEO_EXAMPLE: \"video/example\";"
                },
                {
                    "name": "VIDEO_FFV1",
                    "type": "variable",
                    "doc": "VIDEO_FFV1: \"video/FFV1\";"
                },
                {
                    "name": "VIDEO_FLEXFEC",
                    "type": "variable",
                    "doc": "VIDEO_FLEXFEC: \"video/flexfec\";"
                },
                {
                    "name": "VIDEO_FLI",
                    "type": "variable",
                    "doc": "VIDEO_FLI: \"video/fli\";"
                },
                {
                    "name": "VIDEO_GL",
                    "type": "variable",
                    "doc": "VIDEO_GL: \"video/gl\";"
                },
                {
                    "name": "VIDEO_H261",
                    "type": "variable",
                    "doc": "VIDEO_H261: \"video/H261\";"
                },
                {
                    "name": "VIDEO_H263",
                    "type": "variable",
                    "doc": "VIDEO_H263: \"video/H263\";"
                },
                {
                    "name": "VIDEO_H263_1998",
                    "type": "variable",
                    "doc": "VIDEO_H263_1998: \"video/H263-1998\";"
                },
                {
                    "name": "VIDEO_H263_2000",
                    "type": "variable",
                    "doc": "VIDEO_H263_2000: \"video/H263-2000\";"
                },
                {
                    "name": "VIDEO_H264",
                    "type": "variable",
                    "doc": "VIDEO_H264: \"video/H264\";"
                },
                {
                    "name": "VIDEO_H264_RCDO",
                    "type": "variable",
                    "doc": "VIDEO_H264_RCDO: \"video/H264-RCDO\";"
                },
                {
                    "name": "VIDEO_H264_SVC",
                    "type": "variable",
                    "doc": "VIDEO_H264_SVC: \"video/H264-SVC\";"
                },
                {
                    "name": "VIDEO_H265",
                    "type": "variable",
                    "doc": "VIDEO_H265: \"video/H265\";"
                },
                {
                    "name": "VIDEO_H266",
                    "type": "variable",
                    "doc": "VIDEO_H266: \"video/H266\";"
                },
                {
                    "name": "VIDEO_ISO_SEGMENT",
                    "type": "variable",
                    "doc": "VIDEO_ISO_SEGMENT: \"video/iso.segment\";"
                },
                {
                    "name": "VIDEO_JPEG",
                    "type": "variable",
                    "doc": "VIDEO_JPEG: \"video/JPEG\";"
                },
                {
                    "name": "VIDEO_JPEG2000",
                    "type": "variable",
                    "doc": "VIDEO_JPEG2000: \"video/jpeg2000\";"
                },
                {
                    "name": "VIDEO_JXSV",
                    "type": "variable",
                    "doc": "VIDEO_JXSV: \"video/jxsv\";"
                },
                {
                    "name": "VIDEO_MATROSKA",
                    "type": "variable",
                    "doc": "VIDEO_MATROSKA: \"video/matroska\";"
                },
                {
                    "name": "VIDEO_MATROSKA_3D",
                    "type": "variable",
                    "doc": "VIDEO_MATROSKA_3D: \"video/matroska-3d\";"
                },
                {
                    "name": "VIDEO_MJ2",
                    "type": "variable",
                    "doc": "VIDEO_MJ2: \"video/mj2\";"
                },
                {
                    "name": "VIDEO_MP1S",
                    "type": "variable",
                    "doc": "VIDEO_MP1S: \"video/MP1S\";"
                },
                {
                    "name": "VIDEO_MP2P",
                    "type": "variable",
                    "doc": "VIDEO_MP2P: \"video/MP2P\";"
                },
                {
                    "name": "VIDEO_MP2T",
                    "type": "variable",
                    "doc": "VIDEO_MP2T: \"video/MP2T\";"
                },
                {
                    "name": "VIDEO_MP4",
                    "type": "variable",
                    "doc": "VIDEO_MP4: \"video/mp4\";"
                },
                {
                    "name": "VIDEO_MP4V_ES",
                    "type": "variable",
                    "doc": "VIDEO_MP4V_ES: \"video/MP4V-ES\";"
                },
                {
                    "name": "VIDEO_MPEG",
                    "type": "variable",
                    "doc": "VIDEO_MPEG: \"video/mpeg\";"
                },
                {
                    "name": "VIDEO_MPEG4_GENERIC",
                    "type": "variable",
                    "doc": "VIDEO_MPEG4_GENERIC: \"video/mpeg4-generic\";"
                },
                {
                    "name": "VIDEO_MPV",
                    "type": "variable",
                    "doc": "VIDEO_MPV: \"video/MPV\";"
                },
                {
                    "name": "VIDEO_NV",
                    "type": "variable",
                    "doc": "VIDEO_NV: \"video/nv\";"
                },
                {
                    "name": "VIDEO_OGG",
                    "type": "variable",
                    "doc": "VIDEO_OGG: \"video/ogg\";"
                },
                {
                    "name": "VIDEO_PARITYFEC",
                    "type": "variable",
                    "doc": "VIDEO_PARITYFEC: \"video/parityfec\";"
                },
                {
                    "name": "VIDEO_POINTER",
                    "type": "variable",
                    "doc": "VIDEO_POINTER: \"video/pointer\";"
                },
                {
                    "name": "VIDEO_QUICKTIME",
                    "type": "variable",
                    "doc": "VIDEO_QUICKTIME: \"video/quicktime\";"
                },
                {
                    "name": "VIDEO_RAPTORFEC",
                    "type": "variable",
                    "doc": "VIDEO_RAPTORFEC: \"video/raptorfec\";"
                },
                {
                    "name": "VIDEO_RAW",
                    "type": "variable",
                    "doc": "VIDEO_RAW: \"video/raw\";"
                },
                {
                    "name": "VIDEO_RTPLOOPBACK",
                    "type": "variable",
                    "doc": "VIDEO_RTPLOOPBACK: \"video/rtploopback\";"
                },
                {
                    "name": "VIDEO_RTP_ENC_AESCM128",
                    "type": "variable",
                    "doc": "VIDEO_RTP_ENC_AESCM128: \"video/rtp-enc-aescm128\";"
                },
                {
                    "name": "VIDEO_RTX",
                    "type": "variable",
                    "doc": "VIDEO_RTX: \"video/rtx\";"
                },
                {
                    "name": "VIDEO_SCIP",
                    "type": "variable",
                    "doc": "VIDEO_SCIP: \"video/scip\";"
                },
                {
                    "name": "VIDEO_SGI_MOVIE",
                    "type": "variable",
                    "doc": "VIDEO_SGI_MOVIE: \"video/sgi-movie\";"
                },
                {
                    "name": "VIDEO_SMPTE291",
                    "type": "variable",
                    "doc": "VIDEO_SMPTE291: \"video/smpte291\";"
                },
                {
                    "name": "VIDEO_SMPTE292M",
                    "type": "variable",
                    "doc": "VIDEO_SMPTE292M: \"video/SMPTE292M\";"
                },
                {
                    "name": "VIDEO_ULPFEC",
                    "type": "variable",
                    "doc": "VIDEO_ULPFEC: \"video/ulpfec\";"
                },
                {
                    "name": "VIDEO_VC1",
                    "type": "variable",
                    "doc": "VIDEO_VC1: \"video/vc1\";"
                },
                {
                    "name": "VIDEO_VC2",
                    "type": "variable",
                    "doc": "VIDEO_VC2: \"video/vc2\";"
                },
                {
                    "name": "VIDEO_VND_CCTV",
                    "type": "variable",
                    "doc": "VIDEO_VND_CCTV: \"video/vnd.CCTV\";"
                },
                {
                    "name": "VIDEO_VND_DECE_HD",
                    "type": "variable",
                    "doc": "VIDEO_VND_DECE_HD: \"video/vnd.dece.hd\";"
                },
                {
                    "name": "VIDEO_VND_DECE_MOBILE",
                    "type": "variable",
                    "doc": "VIDEO_VND_DECE_MOBILE: \"video/vnd.dece.mobile\";"
                },
                {
                    "name": "VIDEO_VND_DECE_MP4",
                    "type": "variable",
                    "doc": "VIDEO_VND_DECE_MP4: \"video/vnd.dece.mp4\";"
                },
                {
                    "name": "VIDEO_VND_DECE_PD",
                    "type": "variable",
                    "doc": "VIDEO_VND_DECE_PD: \"video/vnd.dece.pd\";"
                },
                {
                    "name": "VIDEO_VND_DECE_SD",
                    "type": "variable",
                    "doc": "VIDEO_VND_DECE_SD: \"video/vnd.dece.sd\";"
                },
                {
                    "name": "VIDEO_VND_DECE_VIDEO",
                    "type": "variable",
                    "doc": "VIDEO_VND_DECE_VIDEO: \"video/vnd.dece.video\";"
                },
                {
                    "name": "VIDEO_VND_DIRECTV_MPEG",
                    "type": "variable",
                    "doc": "VIDEO_VND_DIRECTV_MPEG: \"video/vnd.directv.mpeg\";"
                },
                {
                    "name": "VIDEO_VND_DIRECTV_MPEG_TTS",
                    "type": "variable",
                    "doc": "VIDEO_VND_DIRECTV_MPEG_TTS: \"video/vnd.directv.mpeg-tts\";"
                },
                {
                    "name": "VIDEO_VND_DLNA_MPEG_TTS",
                    "type": "variable",
                    "doc": "VIDEO_VND_DLNA_MPEG_TTS: \"video/vnd.dlna.mpeg-tts\";"
                },
                {
                    "name": "VIDEO_VND_DVB_FILE",
                    "type": "variable",
                    "doc": "VIDEO_VND_DVB_FILE: \"video/vnd.dvb.file\";"
                },
                {
                    "name": "VIDEO_VND_FVT",
                    "type": "variable",
                    "doc": "VIDEO_VND_FVT: \"video/vnd.fvt\";"
                },
                {
                    "name": "VIDEO_VND_HNS_VIDEO",
                    "type": "variable",
                    "doc": "VIDEO_VND_HNS_VIDEO: \"video/vnd.hns.video\";"
                },
                {
                    "name": "VIDEO_VND_IPTVFORUM_1DPARITYFEC_1010",
                    "type": "variable",
                    "doc": "VIDEO_VND_IPTVFORUM_1DPARITYFEC_1010: \"video/vnd.iptvforum.1dparityfec-1010\";"
                },
                {
                    "name": "VIDEO_VND_IPTVFORUM_1DPARITYFEC_2005",
                    "type": "variable",
                    "doc": "VIDEO_VND_IPTVFORUM_1DPARITYFEC_2005: \"video/vnd.iptvforum.1dparityfec-2005\";"
                },
                {
                    "name": "VIDEO_VND_IPTVFORUM_2DPARITYFEC_1010",
                    "type": "variable",
                    "doc": "VIDEO_VND_IPTVFORUM_2DPARITYFEC_1010: \"video/vnd.iptvforum.2dparityfec-1010\";"
                },
                {
                    "name": "VIDEO_VND_IPTVFORUM_2DPARITYFEC_2005",
                    "type": "variable",
                    "doc": "VIDEO_VND_IPTVFORUM_2DPARITYFEC_2005: \"video/vnd.iptvforum.2dparityfec-2005\";"
                },
                {
                    "name": "VIDEO_VND_IPTVFORUM_TTSAVC",
                    "type": "variable",
                    "doc": "VIDEO_VND_IPTVFORUM_TTSAVC: \"video/vnd.iptvforum.ttsavc\";"
                },
                {
                    "name": "VIDEO_VND_IPTVFORUM_TTSMPEG2",
                    "type": "variable",
                    "doc": "VIDEO_VND_IPTVFORUM_TTSMPEG2: \"video/vnd.iptvforum.ttsmpeg2\";"
                },
                {
                    "name": "VIDEO_VND_MOTOROLA_VIDEO",
                    "type": "variable",
                    "doc": "VIDEO_VND_MOTOROLA_VIDEO: \"video/vnd.motorola.video\";"
                },
                {
                    "name": "VIDEO_VND_MOTOROLA_VIDEOP",
                    "type": "variable",
                    "doc": "VIDEO_VND_MOTOROLA_VIDEOP: \"video/vnd.motorola.videop\";"
                },
                {
                    "name": "VIDEO_VND_MPEGURL",
                    "type": "variable",
                    "doc": "VIDEO_VND_MPEGURL: \"video/vnd.mpegurl\";"
                },
                {
                    "name": "VIDEO_VND_MS_PLAYREADY_MEDIA_PYV",
                    "type": "variable",
                    "doc": "VIDEO_VND_MS_PLAYREADY_MEDIA_PYV: \"video/vnd.ms-playready.media.pyv\";"
                },
                {
                    "name": "VIDEO_VND_MTS",
                    "type": "variable",
                    "doc": "VIDEO_VND_MTS: \"video/vnd.mts\";"
                },
                {
                    "name": "VIDEO_VND_NOKIA_INTERLEAVED_MULTIMEDIA",
                    "type": "variable",
                    "doc": "VIDEO_VND_NOKIA_INTERLEAVED_MULTIMEDIA: \"video/vnd.nokia.interleaved-multimedia\";"
                },
                {
                    "name": "VIDEO_VND_NOKIA_MP4VR",
                    "type": "variable",
                    "doc": "VIDEO_VND_NOKIA_MP4VR: \"video/vnd.nokia.mp4vr\";"
                },
                {
                    "name": "VIDEO_VND_NOKIA_VIDEOVOIP",
                    "type": "variable",
                    "doc": "VIDEO_VND_NOKIA_VIDEOVOIP: \"video/vnd.nokia.videovoip\";"
                },
                {
                    "name": "VIDEO_VND_OBJECTVIDEO",
                    "type": "variable",
                    "doc": "VIDEO_VND_OBJECTVIDEO: \"video/vnd.objectvideo\";"
                },
                {
                    "name": "VIDEO_VND_RADGAMETTOOLS_BINK",
                    "type": "variable",
                    "doc": "VIDEO_VND_RADGAMETTOOLS_BINK: \"video/vnd.radgamettools.bink\";"
                },
                {
                    "name": "VIDEO_VND_RADGAMETTOOLS_SMACKER",
                    "type": "variable",
                    "doc": "VIDEO_VND_RADGAMETTOOLS_SMACKER: \"video/vnd.radgamettools.smacker\";"
                },
                {
                    "name": "VIDEO_VND_SEALEDMEDIA_SOFTSEAL_MOV",
                    "type": "variable",
                    "doc": "VIDEO_VND_SEALEDMEDIA_SOFTSEAL_MOV: \"video/vnd.sealedmedia.softseal.mov\";"
                },
                {
                    "name": "VIDEO_VND_SEALED_MPEG1",
                    "type": "variable",
                    "doc": "VIDEO_VND_SEALED_MPEG1: \"video/vnd.sealed.mpeg1\";"
                },
                {
                    "name": "VIDEO_VND_SEALED_MPEG4",
                    "type": "variable",
                    "doc": "VIDEO_VND_SEALED_MPEG4: \"video/vnd.sealed.mpeg4\";"
                },
                {
                    "name": "VIDEO_VND_SEALED_SWF",
                    "type": "variable",
                    "doc": "VIDEO_VND_SEALED_SWF: \"video/vnd.sealed.swf\";"
                },
                {
                    "name": "VIDEO_VND_UVVU_MP4",
                    "type": "variable",
                    "doc": "VIDEO_VND_UVVU_MP4: \"video/vnd.uvvu.mp4\";"
                },
                {
                    "name": "VIDEO_VND_VIVO",
                    "type": "variable",
                    "doc": "VIDEO_VND_VIVO: \"video/vnd.vivo\";"
                },
                {
                    "name": "VIDEO_VND_YOUTUBE_YT",
                    "type": "variable",
                    "doc": "VIDEO_VND_YOUTUBE_YT: \"video/vnd.youtube.yt\";"
                },
                {
                    "name": "VIDEO_VP8",
                    "type": "variable",
                    "doc": "VIDEO_VP8: \"video/VP8\";"
                },
                {
                    "name": "VIDEO_VP9",
                    "type": "variable",
                    "doc": "VIDEO_VP9: \"video/VP9\";"
                },
                {
                    "name": "VIDEO_WEBM",
                    "type": "variable",
                    "doc": "VIDEO_WEBM: \"video/webm\";"
                },
                {
                    "name": "VIDEO_WILDCARD",
                    "type": "variable",
                    "doc": "VIDEO_WILDCARD: \"video/*\";"
                },
                {
                    "name": "VIDEO_X_FLV",
                    "type": "variable",
                    "doc": "VIDEO_X_FLV: \"video/x-flv\";"
                },
                {
                    "name": "VIDEO_X_LA_ASF",
                    "type": "variable",
                    "doc": "VIDEO_X_LA_ASF: \"video/x-la-asf\";"
                },
                {
                    "name": "VIDEO_X_MATROSKA",
                    "type": "variable",
                    "doc": "VIDEO_X_MATROSKA: \"video/x-matroska\";"
                },
                {
                    "name": "VIDEO_X_MNG",
                    "type": "variable",
                    "doc": "VIDEO_X_MNG: \"video/x-mng\";"
                },
                {
                    "name": "VIDEO_X_MSVIDEO",
                    "type": "variable",
                    "doc": "VIDEO_X_MSVIDEO: \"video/x-msvideo\";"
                },
                {
                    "name": "VIDEO_X_MS_ASF",
                    "type": "variable",
                    "doc": "VIDEO_X_MS_ASF: \"video/x-ms-asf\";"
                },
                {
                    "name": "VIDEO_X_MS_WM",
                    "type": "variable",
                    "doc": "VIDEO_X_MS_WM: \"video/x-ms-wm\";"
                },
                {
                    "name": "VIDEO_X_MS_WMV",
                    "type": "variable",
                    "doc": "VIDEO_X_MS_WMV: \"video/x-ms-wmv\";"
                },
                {
                    "name": "VIDEO_X_MS_WMX",
                    "type": "variable",
                    "doc": "VIDEO_X_MS_WMX: \"video/x-ms-wmx\";"
                },
                {
                    "name": "VIDEO_X_MS_WVX",
                    "type": "variable",
                    "doc": "VIDEO_X_MS_WVX: \"video/x-ms-wvx\";"
                },
                {
                    "name": "VIDEO_X_SGI_MOVIE",
                    "type": "variable",
                    "doc": "VIDEO_X_SGI_MOVIE: \"video/x-sgi-movie\";"
                },
                {
                    "name": "VND_ANDROID_DOCUMENT_DIRECTORY",
                    "type": "variable",
                    "doc": "VND_ANDROID_DOCUMENT_DIRECTORY: \"vnd.android.document/directory\";"
                },
                {
                    "name": "WILDCARD",
                    "type": "variable",
                    "doc": "WILDCARD: \"*/*\";"
                },
                {
                    "name": "X_CONFERENCE_X_COOLTALK",
                    "type": "variable",
                    "doc": "X_CONFERENCE_X_COOLTALK: \"x-conference/x-cooltalk\";"
                },
                {
                    "name": "X_EPOC_X_SISX_APP",
                    "type": "variable",
                    "doc": "X_EPOC_X_SISX_APP: \"x-epoc/x-sisx-app\";"
                },
                {
                    "name": "X_WAP_MULTIPART_VND_UPLANET_HEADER_SET",
                    "type": "variable",
                    "doc": "X_WAP_MULTIPART_VND_UPLANET_HEADER_SET: \"x-wap.multipart/vnd.uplanet.header-set\";"
                },
                {
                    "name": "X_WORLD_X_VRML",
                    "type": "variable",
                    "doc": "X_WORLD_X_VRML: \"x-world/x-vrml\";"
                }
            ],
            "notice": [
                {
                    "name": "channel",
                    "type": "variable",
                    "doc": "channel: Notice.Channel;"
                },
                {
                    "name": "builder",
                    "type": "variable",
                    "doc": "builder: androidx.core.app.NotificationCompat.Builder;"
                },
                {
                    "name": "getBuilder",
                    "type": "function",
                    "doc": "getBuilder(): androidx.core.app.NotificationCompat.Builder;"
                },
                {
                    "name": "isEnabled",
                    "type": "function",
                    "doc": "isEnabled(): boolean;"
                },
                {
                    "name": "ensureEnabled",
                    "type": "function",
                    "doc": "ensureEnabled(): void;"
                },
                {
                    "name": "launchSettings",
                    "type": "function",
                    "doc": "launchSettings(): void;"
                },
                {
                    "name": "config",
                    "type": "function",
                    "doc": "config(preset: Notice.ConfigPreset): void;"
                },
                {
                    "name": "cancel",
                    "type": "function",
                    "doc": "cancel(id: number | null | undefined): void;"
                }
            ],
            "Numberx": [
                {
                    "name": "ICU",
                    "type": "variable",
                    "doc": "ICU: 996;"
                },
                {
                    "name": "ensureNumber",
                    "type": "function",
                    "doc": "ensureNumber(...o: any[]);"
                },
                {
                    "name": "check",
                    "type": "function",
                    "doc": "check(...numOrOpr: (number | '<' | '<=' | '>' | '>=' | '=')[]): boolean;"
                },
                {
                    "name": "clamp",
                    "type": "function",
                    "doc": "clamp(num: number, ...clamps: (any | any[])[]): number;"
                },
                {
                    "name": "clampTo",
                    "type": "function",
                    "doc": "clampTo(num: number, range: number[], cycle?: number): number;"
                },
                {
                    "name": "toFixedNum",
                    "type": "function",
                    "doc": "toFixedNum(num: number, fraction?: number): number;"
                },
                {
                    "name": "padStart",
                    "type": "function",
                    "doc": "padStart(num: number, targetLength: number, pad?: number | string): string;"
                },
                {
                    "name": "padEnd",
                    "type": "function",
                    "doc": "padEnd(num: number, targetLength: number, pad?: number | string): string;"
                },
                {
                    "name": "parseFloat",
                    "type": "function",
                    "doc": "parseFloat(string: string, radix?: number): number;"
                },
                {
                    "name": "parsePercent",
                    "type": "function",
                    "doc": "parsePercent(percentage: Percentage$): number;"
                },
                {
                    "name": "parseRatio",
                    "type": "function",
                    "doc": "parseRatio(ratio: string): number;"
                },
                {
                    "name": "parseAny",
                    "type": "function",
                    "doc": "parseAny(o: any): number;"
                }
            ],
            "ocr": [
                {
                    "name": "paddle",
                    "type": "variable",
                    "doc": "paddle: OcrPaddle;"
                },
                {
                    "name": "mlkit",
                    "type": "variable",
                    "doc": "mlkit: OcrMLKit;"
                },
                {
                    "name": "rapid",
                    "type": "variable",
                    "doc": "rapid: OcrRapid;"
                },
                {
                    "name": "recognizeText",
                    "type": "function",
                    "doc": "recognizeText(options?: DetectOptionsAny): string[];"
                },
                {
                    "name": "detect",
                    "type": "function",
                    "doc": "detect(options?: DetectOptionsAny): org.autojs.autojs.runtime.api.OcrResult[];"
                },
                {
                    "name": "mode",
                    "type": "variable",
                    "doc": "get mode(): ModeName;"
                },
                {
                    "name": "tap",
                    "type": "function",
                    "doc": "tap(mode: Mode): void;"
                },
                {
                    "name": "summary",
                    "type": "function",
                    "doc": "summary(): string;"
                }
            ],
            "opencc": [
                {
                    "name": "convert",
                    "type": "function",
                    "doc": "convert(s: string, type: OpenCC.ConversionTypeName | ConversionType): string;"
                },
                {
                    "name": "hk2s",
                    "type": "function",
                    "doc": "hk2s(s: string): string;"
                },
                {
                    "name": "hk2t",
                    "type": "function",
                    "doc": "hk2t(s: string): string;"
                },
                {
                    "name": "jp2t",
                    "type": "function",
                    "doc": "jp2t(s: string): string;"
                },
                {
                    "name": "s2hk",
                    "type": "function",
                    "doc": "s2hk(s: string): string;"
                },
                {
                    "name": "s2t",
                    "type": "function",
                    "doc": "s2t(s: string): string;"
                },
                {
                    "name": "s2tw",
                    "type": "function",
                    "doc": "s2tw(s: string): string;"
                },
                {
                    "name": "s2twp",
                    "type": "function",
                    "doc": "s2twp(s: string): string;"
                },
                {
                    "name": "s2twi",
                    "type": "function",
                    "doc": "s2twi(s: string): string;"
                },
                {
                    "name": "t2hk",
                    "type": "function",
                    "doc": "t2hk(s: string): string;"
                },
                {
                    "name": "t2s",
                    "type": "function",
                    "doc": "t2s(s: string): string;"
                },
                {
                    "name": "t2tw",
                    "type": "function",
                    "doc": "t2tw(s: string): string;"
                },
                {
                    "name": "tw2s",
                    "type": "function",
                    "doc": "tw2s(s: string): string;"
                },
                {
                    "name": "t2jp",
                    "type": "function",
                    "doc": "t2jp(s: string): string;"
                },
                {
                    "name": "tw2t",
                    "type": "function",
                    "doc": "tw2t(s: string): string;"
                },
                {
                    "name": "tw2sp",
                    "type": "function",
                    "doc": "tw2sp(s: string): string;"
                },
                {
                    "name": "twi2s",
                    "type": "function",
                    "doc": "twi2s(s: string): string;"
                },
                {
                    "name": "s2jp",
                    "type": "function",
                    "doc": "s2jp(s: string): string;"
                },
                {
                    "name": "t2twi",
                    "type": "function",
                    "doc": "t2twi(s: string): string;"
                },
                {
                    "name": "hk2tw",
                    "type": "function",
                    "doc": "hk2tw(s: string): string;"
                },
                {
                    "name": "hk2twi",
                    "type": "function",
                    "doc": "hk2twi(s: string): string;"
                },
                {
                    "name": "hk2jp",
                    "type": "function",
                    "doc": "hk2jp(s: string): string;"
                },
                {
                    "name": "tw2hk",
                    "type": "function",
                    "doc": "tw2hk(s: string): string;"
                },
                {
                    "name": "tw2twi",
                    "type": "function",
                    "doc": "tw2twi(s: string): string;"
                },
                {
                    "name": "tw2jp",
                    "type": "function",
                    "doc": "tw2jp(s: string): string;"
                },
                {
                    "name": "twi2t",
                    "type": "function",
                    "doc": "twi2t(s: string): string;"
                },
                {
                    "name": "twi2hk",
                    "type": "function",
                    "doc": "twi2hk(s: string): string;"
                },
                {
                    "name": "twi2tw",
                    "type": "function",
                    "doc": "twi2tw(s: string): string;"
                },
                {
                    "name": "twi2jp",
                    "type": "function",
                    "doc": "twi2jp(s: string): string;"
                },
                {
                    "name": "jp2s",
                    "type": "function",
                    "doc": "jp2s(s: string): string;"
                },
                {
                    "name": "jp2hk",
                    "type": "function",
                    "doc": "jp2hk(s: string): string;"
                },
                {
                    "name": "jp2tw",
                    "type": "function",
                    "doc": "jp2tw(s: string): string;"
                },
                {
                    "name": "jp2twi",
                    "type": "function",
                    "doc": "jp2twi(s: string): string;"
                }
            ],
            "pinyin": [
                {
                    "name": "STYLE_NORMAL",
                    "type": "variable",
                    "doc": "STYLE_NORMAL: PinyinStyle;"
                },
                {
                    "name": "STYLE_TONE",
                    "type": "variable",
                    "doc": "STYLE_TONE: PinyinStyle;"
                },
                {
                    "name": "STYLE_TONE2",
                    "type": "variable",
                    "doc": "STYLE_TONE2: PinyinStyle;"
                },
                {
                    "name": "STYLE_TO3NE",
                    "type": "variable",
                    "doc": "STYLE_TO3NE: PinyinStyle;"
                },
                {
                    "name": "STYLE_INITIALS",
                    "type": "variable",
                    "doc": "STYLE_INITIALS: PinyinStyle;"
                },
                {
                    "name": "STYLE_FIRST_LETTER",
                    "type": "variable",
                    "doc": "STYLE_FIRST_LETTER: PinyinStyle;"
                },
                {
                    "name": "MODE_NORMAL",
                    "type": "variable",
                    "doc": "MODE_NORMAL: PinyinMode;"
                },
                {
                    "name": "MODE_SURNAME",
                    "type": "variable",
                    "doc": "MODE_SURNAME: PinyinMode;"
                },
                {
                    "name": "MODE_PLACENAME",
                    "type": "variable",
                    "doc": "MODE_PLACENAME: PinyinMode;"
                },
                {
                    "name": "MODE_PLACE_NAME",
                    "type": "variable",
                    "doc": "MODE_PLACE_NAME: PinyinMode;"
                },
                {
                    "name": "convert",
                    "type": "function",
                    "doc": "convert(hansArg: string, options?: Pinyin.ConvertOptions): Pinyin.ResultList;"
                },
                {
                    "name": "simple",
                    "type": "function",
                    "doc": "simple(str: string, enableNumericTone?: boolean, enableSegment?: boolean): string;"
                },
                {
                    "name": "compare",
                    "type": "function",
                    "doc": "compare(hanA: string, hanB?: string): string;"
                },
                {
                    "name": "compact",
                    "type": "function",
                    "doc": "compact(arr: Pinyin.Matrix, options?: unknown): string;"
                },
                {
                    "name": "fromCodePoint",
                    "type": "function",
                    "doc": "fromCodePoint(codePoint: number): string | null;"
                },
                {
                    "name": "fromPhrase",
                    "type": "function",
                    "doc": "fromPhrase(phrase: string): Pinyin.Matrix;"
                }
            ],
            "pinyin4j": [
                {
                    "name": "of",
                    "type": "function",
                    "doc": "of(source: string, options?: string | Pinyin4j.Options): string;"
                }
            ],
            "plugins": [
                {
                    "name": "load",
                    "type": "function",
                    "doc": "load(packageName: string): any;"
                }
            ],
            "qrcode": [
                {
                    "name": "detect",
                    "type": "function",
                    "doc": "detect(options?: DetectOptions): QrCode.Result | QrCode.Result[] | null;"
                },
                {
                    "name": "detectAll",
                    "type": "function",
                    "doc": "detectAll(options?: DetectOptions): QrCode.Result[];"
                },
                {
                    "name": "recognizeText",
                    "type": "function",
                    "doc": "recognizeText(options?: DetectOptions): string | string[] | null;"
                },
                {
                    "name": "recognizeTexts",
                    "type": "function",
                    "doc": "recognizeTexts(options?: DetectOptions): string[];"
                }
            ],
            "recorder": [
                {
                    "name": "save",
                    "type": "function",
                    "doc": "save(key?: string | null, ts?: number | null): number;"
                },
                {
                    "name": "load",
                    "type": "function",
                    "doc": "load(key?: string | null, ts?: number | null): number;"
                },
                {
                    "name": "isLessThan",
                    "type": "function",
                    "doc": "isLessThan(key?: string | null, compare?: any): boolean;"
                },
                {
                    "name": "isGreaterThan",
                    "type": "function",
                    "doc": "isGreaterThan(key?: string | null, compare?: any): boolean;"
                },
                {
                    "name": "has",
                    "type": "function",
                    "doc": "has(key?: string | null): boolean;"
                },
                {
                    "name": "remove",
                    "type": "function",
                    "doc": "remove(key?: string | null): boolean;"
                },
                {
                    "name": "clear",
                    "type": "function",
                    "doc": "clear(): void;"
                }
            ],
            "require": [
                {
                    "name": "debug",
                    "type": "variable",
                    "doc": "debug: boolean;"
                },
                {
                    "name": "cache",
                    "type": "variable",
                    "doc": "cache: { [prop: string]: object | any };"
                },
                {
                    "name": "extensions",
                    "type": "variable",
                    "doc": "extensions: {};"
                },
                {
                    "name": "root",
                    "type": "variable",
                    "doc": "root: string;"
                },
                {
                    "name": "NODE_PATH",
                    "type": "variable",
                    "doc": "NODE_PATH: any;"
                },
                {
                    "name": "resolve",
                    "type": "function",
                    "doc": "resolve(id: Require.ID, parent?: { id?: string }): { path: string, core: boolean } | string | false;"
                },
                {
                    "name": "paths",
                    "type": "function",
                    "doc": "paths(): string[];"
                }
            ],
            "runtime": [
                {
                    "name": "applicationContext",
                    "type": "variable",
                    "doc": "applicationContext: android.content.Context;"
                },
                {
                    "name": "clip",
                    "type": "variable",
                    "doc": "clip: string;"
                },
                {
                    "name": "images",
                    "type": "variable",
                    "doc": "images: org.autojs.autojs.runtime.api.Images;"
                },
                {
                    "name": "rootShell",
                    "type": "variable",
                    "doc": "rootShell: org.autojs.autojs.runtime.api.AbstractShell;"
                },
                {
                    "name": "screenMetrics",
                    "type": "variable",
                    "doc": "screenMetrics: org.autojs.autojs.runtime.api.ScreenMetrics;"
                },
                {
                    "name": "topLevelScope",
                    "type": "variable",
                    "doc": "topLevelScope: org.autojs.autojs.rhino.TopLevelScope;"
                },
                {
                    "name": "createContinuation",
                    "type": "function",
                    "doc": "createContinuation(scope?: org.mozilla.javascript.Scriptable): org.autojs.autojs.rhino.continuation.Continuation;"
                },
                {
                    "name": "exit",
                    "type": "function",
                    "doc": "exit(): void;"
                },
                {
                    "name": "getClip",
                    "type": "function",
                    "doc": "getClip(): string;"
                },
                {
                    "name": "getImages",
                    "type": "function",
                    "doc": "getImages(): org.autojs.autojs.runtime.api.Images;"
                },
                {
                    "name": "isStopped",
                    "type": "function",
                    "doc": "isStopped(): boolean;"
                },
                {
                    "name": "loadDex",
                    "type": "function",
                    "doc": "loadDex(path: string): void;"
                },
                {
                    "name": "loadJar",
                    "type": "function",
                    "doc": "loadJar(path: string): void;"
                },
                {
                    "name": "requestPermissions",
                    "type": "function",
                    "doc": "requestPermissions(permissions: ('access_fine_location' | 'record_audio')[]): void;"
                },
                {
                    "name": "requiresApi",
                    "type": "function",
                    "doc": "requiresApi(api: number): void;"
                },
                {
                    "name": "setApplicationContext",
                    "type": "function",
                    "doc": "setApplicationContext(context: android.content.Context): void;"
                },
                {
                    "name": "setClip",
                    "type": "function",
                    "doc": "setClip(text: string): void;"
                },
                {
                    "name": "setScreenMetrics",
                    "type": "function",
                    "doc": "setScreenMetrics(width: number, height: number): void;"
                },
                {
                    "name": "setTopLevelScope",
                    "type": "function",
                    "doc": "setTopLevelScope(topLevelScope: org.autojs.autojs.rhino.TopLevelScope): void;"
                },
                {
                    "name": "shell",
                    "type": "function",
                    "doc": "shell(cmd: string, root: number): org.autojs.autojs.runtime.api.AbstractShell.Result;"
                },
                {
                    "name": "sleep",
                    "type": "function",
                    "doc": "sleep(millis: number): void;"
                },
                {
                    "name": "stop",
                    "type": "function",
                    "doc": "stop(): void;"
                },
                {
                    "name": "toast",
                    "type": "function",
                    "doc": "toast(text): void;"
                }
            ],
            "s13n": [
                {
                    "name": "color",
                    "type": "function",
                    "doc": "color(o: OmniColor): ColorInt;"
                },
                {
                    "name": "throwable",
                    "type": "function",
                    "doc": "throwable(o: OmniThrowable): java.lang.Throwable;"
                },
                {
                    "name": "point",
                    "type": "function",
                    "doc": "point(o: { x: number, y: number }): android.graphics.Point;"
                },
                {
                    "name": "time",
                    "type": "function",
                    "doc": "time(source: S13n.TimeSourceParam, options: { fromUnit?: S13n.TimeUnitParam; toUnit?: S13n.TimeUnitParam; }): number;"
                },
                {
                    "name": "bytes",
                    "type": "function",
                    "doc": "bytes(source: Converter.BytesValue, fromUnit?: S13n.BytesUnitParam): Converter.BytesResult;"
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
                    "name": "ignoresUnsupportedSensor",
                    "type": "variable",
                    "doc": "ignoresUnsupportedSensor: boolean;"
                },
                {
                    "name": "register",
                    "type": "function",
                    "doc": "register(sensorName: string, delay?: number): org.autojs.autojs.runtime.api.Sensors.SensorEventEmitter;"
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
                }
            ],
            "shell": [
                {
                    "name": "execCommand",
                    "type": "function",
                    "doc": "execCommand(command: Shell.Command): AbstractShell.Result;"
                },
                {
                    "name": "getCommand",
                    "type": "function",
                    "doc": "getCommand(command: Shell.Command): string;"
                },
                {
                    "name": "fromIntent",
                    "type": "function",
                    "doc": "fromIntent(i: Intent.Common): string;"
                },
                {
                    "name": "kill",
                    "type": "function",
                    "doc": "kill(app: org.autojs.autojs.util.App | string): boolean;"
                },
                {
                    "name": "currentPackage",
                    "type": "function",
                    "doc": "currentPackage(): string;"
                },
                {
                    "name": "currentActivity",
                    "type": "function",
                    "doc": "currentActivity(): string;"
                },
                {
                    "name": "currentComponent",
                    "type": "function",
                    "doc": "currentComponent(): string;"
                }
            ],
            "shizuku": [
                {
                    "name": "state",
                    "type": "variable",
                    "doc": "state: Shizuku.State;"
                },
                {
                    "name": "execCommand",
                    "type": "function",
                    "doc": "execCommand(command: Shell.Command): AbstractShell.Result;"
                },
                {
                    "name": "getCommand",
                    "type": "function",
                    "doc": "getCommand(command: Shell.Command): string;"
                },
                {
                    "name": "kill",
                    "type": "function",
                    "doc": "kill(app: org.autojs.autojs.util.App | string): boolean;"
                },
                {
                    "name": "currentPackage",
                    "type": "function",
                    "doc": "currentPackage(): string;"
                },
                {
                    "name": "currentActivity",
                    "type": "function",
                    "doc": "currentActivity(): string;"
                },
                {
                    "name": "currentComponent",
                    "type": "function",
                    "doc": "currentComponent(): string;"
                }
            ],
            "sqlite": [
                {
                    "name": "open",
                    "type": "function",
                    "doc": "open(databaseFilePath: string, options?: SQLiteOpenOptions | null, callback?: DatabaseCallback | JavaScriptDatabaseCallback | null): WrappedDatabase;"
                }
            ],
            "storages": [
                {
                    "name": "create",
                    "type": "function",
                    "doc": "create(name: string | number | boolean): Storages.Storage;"
                },
                {
                    "name": "remove",
                    "type": "function",
                    "doc": "remove(name: string | number | boolean): void;"
                },
                {
                    "name": "removeSync",
                    "type": "function",
                    "doc": "removeSync(name: string | number | boolean): boolean;"
                },
                {
                    "name": "all",
                    "type": "function",
                    "doc": "all(): Storages.Storage[];"
                },
                {
                    "name": "names",
                    "type": "function",
                    "doc": "names(): string[];"
                }
            ],
            "sysprops": [
                {
                    "name": "get",
                    "type": "function",
                    "doc": "get(propName: string, defaultValue?: string): string | null;"
                },
                {
                    "name": "getInt",
                    "type": "function",
                    "doc": "getInt(propName: string, defaultValue?: number): number | null;"
                },
                {
                    "name": "getBoolean",
                    "type": "function",
                    "doc": "getBoolean(propName: string, defaultValue?: boolean): boolean | null;"
                },
                {
                    "name": "getAll",
                    "type": "function",
                    "doc": "getAll(): SysProps.Properties;"
                }
            ],
            "tasks": [
                {
                    "name": "addTask",
                    "type": "function",
                    "doc": "addTask<T extends TimedTask$ | IntentTask$>(task: T): T;"
                },
                {
                    "name": "addDailyTask",
                    "type": "function",
                    "doc": "addDailyTask<TResult = TimedTask$>(options?: Tasks.TimedTask.Daily<TResult>): Tasks.AddTaskResult<TimedTask$, TResult>;"
                },
                {
                    "name": "addWeeklyTask",
                    "type": "function",
                    "doc": "addWeeklyTask<TResult = TimedTask$>(options?: Tasks.TimedTask.Weekly<TResult>): Tasks.AddTaskResult<TimedTask$, TResult>;"
                },
                {
                    "name": "addDisposableTask",
                    "type": "function",
                    "doc": "addDisposableTask<TResult = TimedTask$>(options?: Tasks.TimedTask.Disposable<TResult>): Tasks.AddTaskResult<TimedTask$, TResult>;"
                },
                {
                    "name": "addIntentTask",
                    "type": "function",
                    "doc": "addIntentTask<TResult = IntentTask$>(options: Tasks.IntentTask.Basic<TResult>): Tasks.AddTaskResult<IntentTask$, TResult>;"
                },
                {
                    "name": "getTimedTask",
                    "type": "function",
                    "doc": "getTimedTask(id: number): TimedTask$ | null;"
                },
                {
                    "name": "getIntentTask",
                    "type": "function",
                    "doc": "getIntentTask(id: number): IntentTask$ | null;"
                },
                {
                    "name": "removeTask",
                    "type": "function",
                    "doc": "removeTask(task: TimedTask$ | IntentTask$ | null | undefined): boolean;"
                },
                {
                    "name": "removeTimedTask",
                    "type": "function",
                    "doc": "removeTimedTask(id: number): boolean;"
                },
                {
                    "name": "removeIntentTask",
                    "type": "function",
                    "doc": "removeIntentTask(id: number): boolean;"
                },
                {
                    "name": "updateTask",
                    "type": "function",
                    "doc": "updateTask(task: TimedTask$ | IntentTask$ | null | undefined): boolean;"
                },
                {
                    "name": "queryTimedTasks",
                    "type": "function",
                    "doc": "queryTimedTasks(options?: { path?: string }): TimedTask$[];"
                },
                {
                    "name": "queryIntentTasks",
                    "type": "function",
                    "doc": "queryIntentTasks(options?: { path?: string, action?: string }): IntentTask$[];"
                },
                {
                    "name": "timeFlagToDays",
                    "type": "function",
                    "doc": "timeFlagToDays(flag: number): number[];"
                },
                {
                    "name": "daysToTimeFlag",
                    "type": "function",
                    "doc": "daysToTimeFlag(days: number[]): number;"
                }
            ],
            "threads": [
                {
                    "name": "start",
                    "type": "function",
                    "doc": "start(runnable: java.lang.Runnable | Func): org.autojs.autojs.runtime.api.Threads.Companion.TimerThread | undefined;"
                },
                {
                    "name": "pool",
                    "type": "function",
                    "doc": "pool(options?: Threads.PoolOptions | null): java.util.concurrent.ThreadPoolExecutor;"
                },
                {
                    "name": "lock",
                    "type": "function",
                    "doc": "lock(): java.util.concurrent.locks.Lock;"
                },
                {
                    "name": "atomic",
                    "type": "function",
                    "doc": "atomic(value?: number): java.util.concurrent.atomic.AtomicLong;"
                },
                {
                    "name": "disposable",
                    "type": "function",
                    "doc": "disposable(): Threads.Disposable;"
                },
                {
                    "name": "currentThread",
                    "type": "function",
                    "doc": "currentThread(): any;"
                },
                {
                    "name": "interrupt",
                    "type": "function",
                    "doc": "interrupt(thread: org.autojs.autojs.core.looper.TimerThread | any): void;"
                },
                {
                    "name": "shutDownAll",
                    "type": "function",
                    "doc": ""
                }
            ],
            "timers": [
                {
                    "name": "setIntervalExt",
                    "type": "function",
                    "doc": "setIntervalExt(timeoutCallback: Function, interval?: number, timeout?: number | (() => any), resultCallback?: (result: any) => void): number;"
                },
                {
                    "name": "keepAlive",
                    "type": "function",
                    "doc": "keepAlive(timeout?: number | null): number;"
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
                }
            ],
            "toast": [
                {
                    "name": "dismissAll",
                    "type": "function",
                    "doc": "dismissAll(): void;"
                }
            ],
            "ui": [
                {
                    "name": "layoutInflater",
                    "type": "variable",
                    "doc": "layoutInflater: org.autojs.autojs.core.ui.inflater.DynamicLayoutInflater;"
                },
                {
                    "name": "bindingContext",
                    "type": "variable",
                    "doc": "bindingContext: object;"
                },
                {
                    "name": "R",
                    "type": "variable",
                    "doc": "R: org.autojs.autojs6.R;"
                },
                {
                    "name": "Widget",
                    "type": "variable",
                    "doc": "Widget: { new(): UI.Widget; };"
                },
                {
                    "name": "useAndroidLayout",
                    "type": "function",
                    "doc": "useAndroidLayout(b?: boolean | null): void;"
                },
                {
                    "name": "emitter",
                    "type": "variable",
                    "doc": "get emitter(): EventEmitter$ | null;"
                },
                {
                    "name": "root",
                    "type": "variable",
                    "doc": "get root(): android.widget.FrameLayout;"
                },
                {
                    "name": "statusBarHeight",
                    "type": "variable",
                    "doc": "get statusBarHeight(): number;"
                },
                {
                    "name": "visibleStatusBarHeight",
                    "type": "variable",
                    "doc": "get visibleStatusBarHeight(): number;"
                },
                {
                    "name": "navigationBarHeight",
                    "type": "variable",
                    "doc": "get navigationBarHeight(): number;"
                },
                {
                    "name": "visibleNavigationBarHeight",
                    "type": "variable",
                    "doc": "get visibleNavigationBarHeight(): number;"
                },
                {
                    "name": "layout",
                    "type": "function",
                    "doc": "layout(layout: Xml): void;"
                },
                {
                    "name": "layoutFile",
                    "type": "function",
                    "doc": "layoutFile(path: string): void;"
                },
                {
                    "name": "inflate",
                    "type": "function",
                    "doc": "inflate(xml: Xml, parent?: android.view.ViewGroup, isAttachedToParent?: boolean): UI.View;"
                },
                {
                    "name": "registerWidget",
                    "type": "function",
                    "doc": "registerWidget(name: string, widget: Func): void;"
                },
                {
                    "name": "setContentView",
                    "type": "function",
                    "doc": "setContentView(view: android.view.View): void;"
                },
                {
                    "name": "findById",
                    "type": "function",
                    "doc": "findById(stringId: string): android.view.View;"
                },
                {
                    "name": "findView",
                    "type": "function",
                    "doc": "findView(stringId: string): android.view.View;"
                },
                {
                    "name": "isUiThread",
                    "type": "function",
                    "doc": "isUiThread(): boolean;"
                },
                {
                    "name": "run",
                    "type": "function",
                    "doc": "run<R>(action: () => R): R;"
                },
                {
                    "name": "post",
                    "type": "function",
                    "doc": "post(action: Func, delay?: number): boolean;"
                },
                {
                    "name": "statusBarColor",
                    "type": "function",
                    "doc": "statusBarColor(color: OmniColor): void;"
                },
                {
                    "name": "statusBarIconLight",
                    "type": "function",
                    "doc": "statusBarIconLight(isLight?: boolean): void;"
                },
                {
                    "name": "statusBarIconLightBy",
                    "type": "function",
                    "doc": "statusBarIconLightBy(refColor: OmniColor): void;"
                },
                {
                    "name": "backgroundColor",
                    "type": "function",
                    "doc": "backgroundColor(color: OmniColor): void;"
                },
                {
                    "name": "navigationBarColor",
                    "type": "function",
                    "doc": "navigationBarColor(color: OmniColor): void;"
                },
                {
                    "name": "navigationBarIconLight",
                    "type": "function",
                    "doc": "navigationBarIconLight(isLight?: boolean): void;"
                },
                {
                    "name": "navigationBarIconLightBy",
                    "type": "function",
                    "doc": "navigationBarIconLightBy(refColor: OmniColor): void;"
                },
                {
                    "name": "finish",
                    "type": "function",
                    "doc": "finish(): void;"
                },
                {
                    "name": "keepScreenOn",
                    "type": "function",
                    "doc": "keepScreenOn(): void;"
                },
                {
                    "name": "getStatusBarHeight",
                    "type": "function",
                    "doc": "getStatusBarHeight(options?: UI.BarHeightOptions): number;"
                },
                {
                    "name": "getVisibleStatusBarHeight",
                    "type": "function",
                    "doc": "getVisibleStatusBarHeight(options?: UI.BarHeightOptions): number;"
                },
                {
                    "name": "getNavigationBarHeight",
                    "type": "function",
                    "doc": "getNavigationBarHeight(options?: UI.BarHeightOptions): number;"
                },
                {
                    "name": "getVisibleNavigationBarHeight",
                    "type": "function",
                    "doc": "getVisibleNavigationBarHeight(options?: UI.BarHeightOptions): number;"
                },
                {
                    "name": "findByStringId",
                    "type": "function",
                    "doc": "findByStringId(view: android.view.View, stringId: string): android.view.View;"
                }
            ],
            "util": [
                {
                    "name": "version",
                    "type": "variable",
                    "doc": "version: Util.Version;"
                },
                {
                    "name": "java",
                    "type": "variable",
                    "doc": "java: Util.Java;"
                },
                {
                    "name": "inspect",
                    "type": "variable",
                    "doc": "inspect: Util.Inspect;"
                },
                {
                    "name": "morseCode",
                    "type": "variable",
                    "doc": "morseCode: Util.MorseCode;"
                },
                {
                    "name": "versionCodes",
                    "type": "variable",
                    "doc": "versionCodes: Util.VersionCodes;"
                },
                {
                    "name": "isArray",
                    "type": "function",
                    "doc": "isArray(arg: any): boolean;"
                },
                {
                    "name": "isBoolean",
                    "type": "function",
                    "doc": "isBoolean(arg: any): boolean;"
                },
                {
                    "name": "isNull",
                    "type": "function",
                    "doc": "isNull(arg: any): boolean;"
                },
                {
                    "name": "isNullOrUndefined",
                    "type": "function",
                    "doc": "isNullOrUndefined(arg: any): boolean;"
                },
                {
                    "name": "isNumber",
                    "type": "function",
                    "doc": "isNumber(arg: any): boolean;"
                },
                {
                    "name": "isString",
                    "type": "function",
                    "doc": "isString(arg: any): boolean;"
                },
                {
                    "name": "isSymbol",
                    "type": "function",
                    "doc": "isSymbol(arg: any): boolean;"
                },
                {
                    "name": "isBigInt",
                    "type": "function",
                    "doc": "isBigInt(arg: any): boolean;"
                },
                {
                    "name": "isUndefined",
                    "type": "function",
                    "doc": "isUndefined(arg: any): boolean;"
                },
                {
                    "name": "isRegExp",
                    "type": "function",
                    "doc": "isRegExp(arg: any): boolean;"
                },
                {
                    "name": "isObject",
                    "type": "function",
                    "doc": "isObject(arg: any): boolean;"
                },
                {
                    "name": "isDate",
                    "type": "function",
                    "doc": "isDate(arg: any): boolean;"
                },
                {
                    "name": "isError",
                    "type": "function",
                    "doc": "isError(arg: any): boolean;"
                },
                {
                    "name": "isFunction",
                    "type": "function",
                    "doc": "isFunction(arg: any): boolean;"
                },
                {
                    "name": "isPrimitive",
                    "type": "function",
                    "doc": "isPrimitive(arg: any): boolean;"
                },
                {
                    "name": "isReference",
                    "type": "function",
                    "doc": "isReference(arg: any): boolean;"
                },
                {
                    "name": "isEmptyObject",
                    "type": "function",
                    "doc": "isEmptyObject(arg: any): boolean;"
                },
                {
                    "name": "isInteger",
                    "type": "function",
                    "doc": "isInteger(arg: any): boolean;"
                },
                {
                    "name": "isJavaArray",
                    "type": "function",
                    "doc": "isJavaArray(o: any): boolean;"
                },
                {
                    "name": "isJavaObject",
                    "type": "function",
                    "doc": "isJavaObject(o: any): boolean;"
                },
                {
                    "name": "unwrapJavaObject",
                    "type": "function",
                    "doc": "unwrapJavaObject(o: { getClass(): java.lang.Class<any> }): any;"
                },
                {
                    "name": "extend",
                    "type": "function",
                    "doc": "extend(subClass: Function | object, parentClass: Function | object | null): void;"
                },
                {
                    "name": "format",
                    "type": "function",
                    "doc": "format(...args: any[]): string;"
                },
                {
                    "name": "log",
                    "type": "function",
                    "doc": "log(...args: any[]): void;"
                },
                {
                    "name": "deprecate",
                    "type": "function",
                    "doc": "deprecate(fn: Func, msg: string): Func;"
                },
                {
                    "name": "debuglog",
                    "type": "function",
                    "doc": "debuglog(set): any;"
                },
                {
                    "name": "getClass",
                    "type": "function",
                    "doc": "getClass(o: java.lang.Class<any> | object): java.lang.Class<any>;"
                },
                {
                    "name": "class",
                    "type": "function",
                    "doc": "class(o: java.lang.Class<any> | object): java.lang.Class<any>;"
                },
                {
                    "name": "getClassName",
                    "type": "function",
                    "doc": "getClassName(o: java.lang.Class<any> | object): string;"
                },
                {
                    "name": "className",
                    "type": "function",
                    "doc": "className(o: java.lang.Class<any> | object): string;"
                },
                {
                    "name": "checkStringParam",
                    "type": "function",
                    "doc": "checkStringParam(src: string | { toString(): string } | any, pattern: string | RegExp): boolean;"
                },
                {
                    "name": "assureStringStartsWith",
                    "type": "function",
                    "doc": "assureStringStartsWith(s: string, start: string): string;"
                },
                {
                    "name": "assureStringEndsWith",
                    "type": "function",
                    "doc": "assureStringEndsWith(s: string, end: string): string;"
                },
                {
                    "name": "assureStringSurroundsWith",
                    "type": "function",
                    "doc": "assureStringSurroundsWith(s: string, start: string, end?: string): string;"
                },
                {
                    "name": "ensureType",
                    "type": "function",
                    "doc": "ensureType(o: any, type: 'number' | 'boolean' | 'string' | 'undefined' | 'symbol' | 'bigint' | 'object' | 'function'): void;"
                },
                {
                    "name": "ensureStringType",
                    "type": "function",
                    "doc": "ensureStringType(...o: any[]): void;"
                },
                {
                    "name": "ensureNumberType",
                    "type": "function",
                    "doc": "ensureNumberType(...o: any[]): void;"
                },
                {
                    "name": "ensureUndefinedType",
                    "type": "function",
                    "doc": "ensureUndefinedType(...o: any[]): void;"
                },
                {
                    "name": "ensureBooleanType",
                    "type": "function",
                    "doc": "ensureBooleanType(...o: any[]): void;"
                },
                {
                    "name": "ensureSymbolType",
                    "type": "function",
                    "doc": "ensureSymbolType(...o: any[]): void;"
                },
                {
                    "name": "ensureBigintType",
                    "type": "function",
                    "doc": "ensureBigintType(...o: any[]): void;"
                },
                {
                    "name": "ensureObjectType",
                    "type": "function",
                    "doc": "ensureObjectType(...o: any[]): void;"
                },
                {
                    "name": "ensureFunctionType",
                    "type": "function",
                    "doc": "ensureFunctionType(...o: any[]): void;"
                },
                {
                    "name": "ensureNonNullObjectType",
                    "type": "function",
                    "doc": "ensureNonNullObjectType(...o: any[]): void;"
                },
                {
                    "name": "ensureArrayType",
                    "type": "function",
                    "doc": "ensureArrayType(...o: any[]): void;"
                },
                {
                    "name": "toRegular",
                    "type": "function",
                    "doc": "toRegular<T extends Function>(f: T): T;"
                },
                {
                    "name": "toRegularAndCall",
                    "type": "function",
                    "doc": "toRegularAndCall<T>(f: (...args) => T, ...o: any[]): T;"
                },
                {
                    "name": "toRegularAndApply",
                    "type": "function",
                    "doc": "toRegularAndApply<T>(f: (...args) => T, args: any[]): T;"
                }
            ],
            "web": [
                {
                    "name": "newInjectableWebView",
                    "type": "function",
                    "doc": "newInjectableWebView(context?: android.content.Context | string, url?: string): org.autojs.autojs.core.web.InjectableWebView;"
                },
                {
                    "name": "newInjectableWebClient",
                    "type": "function",
                    "doc": "newInjectableWebClient(): org.autojs.autojs.core.web.InjectableWebClient;"
                },
                {
                    "name": "newWebSocket",
                    "type": "function",
                    "doc": "newWebSocket(url: string): org.autojs.autojs.core.web.WebSocket;"
                }
            ],
            "zip": [
                {
                    "name": "open",
                    "type": "function",
                    "doc": "open(zipPath: string, options?: Zip.OpenOptions): Zip.ZipFileHandle;"
                },
                {
                    "name": "zipFile",
                    "type": "function",
                    "doc": "zipFile(filePath: string, destZipPath: Zip.DestinationPath, options?: Zip.ZipOptions): Zip.ZipFileHandle;"
                },
                {
                    "name": "zipDir",
                    "type": "function",
                    "doc": "zipDir(dirPath: string, destZipPath: Zip.DestinationPath, options?: Zip.ZipOptions): Zip.ZipFileHandle;"
                },
                {
                    "name": "zipFiles",
                    "type": "function",
                    "doc": "zipFiles(filePathList: Zip.FilePathList, destZipPath: Zip.DestinationPath, options?: Zip.ZipOptions): Zip.ZipFileHandle;"
                },
                {
                    "name": "unzip",
                    "type": "function",
                    "doc": "unzip(zipPath: string, destPath: Zip.DestinationPath, options?: Zip.UnzipOptions): Zip.ZipFileHandle;"
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
