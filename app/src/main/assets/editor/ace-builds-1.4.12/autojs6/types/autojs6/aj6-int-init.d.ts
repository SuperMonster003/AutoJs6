// Type definitions for AutoJs6 internal module init
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 5.1.3
//
// Last modified: Jun 15, 2026
//
// noinspection JSUnusedGlobalSymbols

/// <reference path="../index.d.ts" />

/**
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/ScriptRuntime.kt
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/global/Species.kt
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/global/IsNullish.kt
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/global/Classes.kt
 */

declare let app: Internal.App;
declare let $app: Internal.App;

declare let auto: Internal.Auto;
declare let $auto: Internal.Auto;

declare let autojs: Internal.Autojs;
declare let $autojs: Internal.Autojs;

declare let automator: Internal.Automator;
declare let $automator: Internal.Automator;

declare let base64: Internal.Base64;
declare let $base64: Internal.Base64;

declare let colors: Internal.Colors;
declare let $colors: Internal.Colors;

declare let console: Internal.Console;
declare let $console: Internal.Console;

declare let continuation: Internal.Continuation;
declare let $continuation: Internal.Continuation;

declare let crypto: Internal.Crypto;
declare let $crypto: Internal.Crypto;

declare let device: Internal.Device;
declare let $device: Internal.Device;

declare let dialogs: Internal.Dialogs;
declare let $dialogs: Internal.Dialogs;

declare let engines: Internal.Engines;
declare let $engines: Internal.Engines;

declare let events: Internal.Events;
declare let $events: Internal.Events;

declare let files: Internal.Files;
declare let $files: Internal.Files;

declare let floaty: Internal.Floaty;
declare let $floaty: Internal.Floaty;

declare let http: Internal.Http;
declare let $http: Internal.Http;

declare let i18n: Internal.I18n;
declare let $i18n: Internal.I18n;

declare let images: Internal.Images;
declare let $images: Internal.Images;

declare let media: Internal.Media;
declare let $media: Internal.Media;

declare let notice: Internal.Notice;
declare let $notice: Internal.Notice;

declare let ocr: Internal.Ocr;
declare let $ocr: Internal.Ocr;

declare let barcode: Internal.Barcode;
declare let $barcode: Internal.Barcode;

declare let pinyin: Internal.Pinyin;
declare let $pinyin: Internal.Pinyin;

declare let qrcode: Internal.QrCode;
declare let $qrcode: Internal.QrCode;

declare let s13n: Internal.S13n;
declare let $s13n: Internal.S13n;

declare let plugins: Internal.Plugins;
declare let $plugins: Internal.Plugins;

declare let recorder: Internal.Recorder;
declare let $recorder: Internal.Recorder;

declare let require: Internal.Require;
declare let $require: Internal.Require;

declare let sensors: Internal.Sensors;
declare let $sensors: Internal.Sensors;

declare let shell: Internal.Shell;
declare let $shell: Internal.Shell;

declare let shizuku: Internal.Shizuku;
declare let $shizuku: Internal.Shizuku;

declare let sqlite: Internal.SQLite;
declare let $sqlite: Internal.SQLite;

declare let storages: Internal.Storages;
declare let $storages: Internal.Storages;

declare let tasks: Internal.Tasks;
declare let $tasks: Internal.Tasks;

declare let threads: Internal.Threads;
declare let $threads: Internal.Threads;

declare let timers: Internal.Timers;
declare let $timers: Internal.Timers;

declare let toast: Internal.Toast;
declare let $toast: Internal.Toast;

declare let ui: Internal.UI;
declare let $ui: Internal.UI;

declare let util: Internal.Util;
declare let $util: Internal.Util;

declare let web: Internal.Web;
declare let $web: Internal.Web;

declare let opencc: Internal.OpenCC;
declare let $opencc: Internal.OpenCC;

declare let mime: Internal.Mime;
declare let $mime: Internal.Mime;

declare let cvt: Internal.Converter;
declare let $cvt: Internal.Converter;

declare let fmt: Internal.Formatter;
declare let $fmt: Internal.Formatter;

declare let jsox: Internal.Jsox;
declare let $jsox: Internal.Jsox;

declare let sysprops: Internal.SysProps;
declare let $sysprops: Internal.SysProps;

declare let zip: Internal.Zip;
declare let $zip: Internal.Zip;

declare let nanoid: Internal.NanoID;
declare let $nanoid: Internal.NanoID;

declare let pinyin4j: Internal.Pinyin4j;
declare let $pinyin4j: Internal.Pinyin4j;

declare let mediainfo: Internal.Mediainfo;
declare let $mediainfo: Internal.Mediainfo;

declare let Mathx: Internal.Mathx;
declare let Numberx: Internal.Numberx;
declare let Arrayx: Internal.Arrayx;
declare let Color: Internal.Color;

/**
 * Objects can change the behavior of Object.prototype.toString() by defining
 * a Symbol.toStringTag property, leading to unexpected results.
 * Using Object.prototype.toString() to detect object class, therefore, is **UNRELIABLE**
 * but **NOT** for AutoJs6 (last checked version: v6.2.0).
 *
 * @example expected results
 * const toString = Object.prototype.toString;
 *
 * toString.call(new Date);    // [object Date]
 * toString.call(new String);  // [object String]
 * toString.call(Math);        // [object Math]
 *
 * // Since JavaScript 1.8.5
 * toString.call(undefined);   // [object Undefined]
 * toString.call(null);        // [object Null]
 *
 * @example unexpected results
 * const myDate = new Date();
 * Object.prototype.toString.call(myDate);     // [object Date]
 *
 * myDate[Symbol.toStringTag] = 'myDate';
 * Object.prototype.toString.call(myDate);     // [object myDate]
 *
 * Date.prototype[Symbol.toStringTag] = 'prototype polluted';
 * Object.prototype.toString.call(new Date()); // [object prototype polluted] for Node.js; [object Date] for AutoJs6 (v6.2.0)
 *
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/toString
 */
declare let species: Species;
declare let $species: Species;

declare function isNullish(...values: any[]): boolean;

declare function isInteger(o: any): boolean;

declare function isPrimitive(o: any): boolean;

declare function isReference(o: any): boolean;

declare function isObject(o: any): boolean;

declare function isJavaObject(o: any): boolean;

declare function isJavaClass(o: any): boolean;

declare function isJavaPackage(o: any): boolean;

declare interface Species {
    (o: any): string
        | 'Array' | 'ArrayBuffer' | 'BigInt' | 'Boolean' | 'Continuation' | 'DataView' | 'Date' | 'Error' | 'Float32Array' | 'Float64Array'
        | 'Function' | 'Int16Array' | 'Int32Array' | 'Int8Array' | 'JavaClass' | 'JavaObject' | 'JavaPackage' | 'Map' | 'Namespace'
        | 'Null' | 'Number' | 'Object' | 'QName' | 'RegExp' | 'Set' | 'String' | 'Uint16Array' | 'Uint32Array' | 'Uint8Array' | 'Uint8ClampedArray'
        | 'Undefined' | 'WeakMap' | 'WeakSet' | 'XML' | 'XMLList';

    isArray(o: any): boolean;

    isArrayBuffer(o: any): boolean;

    isBigInt(o: any): boolean;

    isBoolean(o: any): boolean;

    isContinuation(o: any): boolean;

    isDataView(o: any): boolean;

    isDate(o: any): boolean;

    isError(o: any): boolean;

    isFloat32Array(o: any): boolean;

    isFloat64Array(o: any): boolean;

    isFunction(o: any): boolean;

    isInt16Array(o: any): boolean;

    isInt32Array(o: any): boolean;

    isInt8Array(o: any): boolean;

    isJavaObject(o: any): boolean;

    isJavaClass(o: any): boolean;

    isJavaPackage(o: any): boolean;

    isMap(o: any): boolean;

    isNamespace(o: any): boolean;

    isNull(o: any): boolean;

    isNumber(o: any): boolean;

    isObject(o: any): boolean;

    isQName(o: any): boolean;

    isRegExp(o: any): boolean;

    isSet(o: any): boolean;

    isString(o: any): boolean;

    isUint16Array(o: any): boolean;

    isUint32Array(o: any): boolean;

    isUint8Array(o: any): boolean;

    isUint8ClampedArray(o: any): boolean;

    isUndefined(o: any): boolean;

    isWeakMap(o: any): boolean;

    isWeakSet(o: any): boolean;

    isXML(o: any): boolean;

    isXMLList(o: any): boolean;
}

declare class RootAutomator extends Internal.RootAutomator {
    /* Empty body. */
}

declare class App extends org.autojs.autojs.util.App {
    /* Empty body. */
}

declare class RootMode extends org.autojs.autojs.util.RootUtils.RootMode {
    /* Empty body. */
}

declare class Version extends io.github.g00fy2.versioncompare.Version {
    /* Empty body. */
}

declare class ColorTable extends org.autojs.autojs.core.image.ColorTable {
    /* Empty body. */
}

declare class R extends org.autojs.autojs6.R {
    /* Empty body. */
}

declare module org {

    export module autojs {

        export module autojs {

            export module util {

                // @ts-ignore
                export class App extends java.lang.Enum<org.autojs.autojs.util.App> {
                    /* Empty body. */
                }

            }

        }

    }

}

declare class Pref extends org.autojs.autojs.core.pref.Pref {
    /* Empty body. */
}

declare class Canvas extends org.autojs.autojs.core.graphics.ScriptCanvas {
    constructor();
    constructor(bitmap: android.graphics.Bitmap);
    constructor(image: ImageWrapper);
    constructor(width: number, height: number);
}

declare class Image extends ImageWrapper {
    /* Empty body. */
}

declare class Shell extends org.autojs.autojs.runtime.api.Shell {
    /* Empty body. */
}

declare class Paint extends android.graphics.Paint {
    /* Empty body. */
}

declare class KeyEvent extends android.view.KeyEvent {
    /* Empty body. */
}

declare class MotionEvent extends android.view.MotionEvent {
    /* Empty body. */
}

declare class UiObject extends org.autojs.autojs.core.automator.UiObject {
    /* Empty body. */
}

declare class UiObjectCollection extends org.autojs.autojs.core.automator.UiObjectCollection {
    /* Empty body. */
}

declare class UiSelector extends org.autojs.autojs.core.accessibility.UiSelector {
    /* Empty body. */
}

declare class ScriptInterruptedException extends org.autojs.autojs.runtime.exception.ScriptInterruptedException {
    /* Empty body. */
}

declare class Toast extends android.widget.Toast {
    /* Empty body. */
}

declare class NotificationManager extends android.app.NotificationManager {
    /* Empty body. */
}

declare class ComponentName extends android.content.ComponentName {
    /* Empty body. */
}

declare class Handler extends android.os.Handler {
    /* Empty body. */
}

declare class Settings extends android.provider.Settings {
    /* Empty body. */
}

declare class TextToSpeech extends android.speech.tts.TextToSpeech {
    /* Empty body. */
}

declare class SecurityException extends java.lang.SecurityException {
    /* Empty body. */
}

declare class System extends java.lang.System {
    /* Empty body. */
}

declare class Locale extends java.util.Locale {
    /* Empty body. */
}

declare class Crypto extends org.autojs.autojs.core.crypto.Crypto {
    /* Empty body. */
}

declare class TextUtils extends org.autojs.autojs.util.TextUtils {
    /* Empty body. */
}

declare class ScriptEngineService extends org.autojs.autojs.engine.ScriptEngineService {
    /* Empty body. */
}

/**
 * @example Source code summary (zh-CN: 源代码摘要)
 * -- @ScriptVariable
 * public final AccessibilityBridge accessibilityBridge;
 * public UiSelector selector() {
 *     return new UiSelector(accessibilityBridge);
 * }
 * @see org.autojs.autojs.annotation.ScriptVariable
 * @see org.autojs.autojs.core.accessibility.AccessibilityBridge
 * @see org.autojs.autojs.core.accessibility.UiSelector
 */
declare function selector(): Internal.Selector;
declare function $selector(): Internal.Selector;

/**
 * @example Source code summary (zh-CN: 源代码摘要)
 * global.__asGlobal__ = function (obj, functions) {
 *     let len = functions.length;
 *     for (let i = 0; i < len; i++) {
 *         let funcName = functions[i];
 *         let func = obj[funcName];
 *         if (!func) {
 *             continue;
 *         }
 *         (function (obj, funcName, func) {
 *             global[funcName] = function () {
 *                 return func.apply(obj, arguments);
 *             };
 *         })(obj, funcName, func);
 *     }
 * };
 */
declare function __asGlobal__(obj: object, functions: (string | { [prop: string]: string })[], scope?: org.mozilla.javascript.Scriptable): void;

/**
 * @example Source code summary (zh-CN: 源代码摘要)
 * global.__exitIfError__ = function (action) {
 *     try {
 *         return action();
 *     } catch (err) {
 *         if (err instanceof java.lang.Throwable) {
 *             exit(err);
 *         } else if (err instanceof Error) {
 *             exit(new org.mozilla.javascript.EvaluatorException(err.name + ": " + err.message, err.fileName, err.lineNumber));
 *         } else {
 *             exit();
 *         }
 *     }
 * };
 */
declare function __exitIfError__<T>(action: (...args: any) => T): T;

/**
 * @example
 * importClass(org.opencv.core.Point);
 * importClass('org.opencv.core.Point'); // same as above
 * console.log(typeof Point); // "function"
 */
declare function importClass(...cls: Array<string | object>): void;

/**
 * @example
 * importPackage(org.opencv.core);
 * importPackage('org.opencv.core'); // same as above
 * console.log(typeof Point); // "function"
 */
declare function importPackage(...pkg: Array<string | object>): void;

/**
 * @Source org.autojs.autojs.runtime.ScriptRuntime
 */

declare let runtime: Internal.Runtime;

/**
 * @Source org.autojs.autojs.ScriptEngineService
 */

declare let context: Internal.Context;

/**
 * @Source org.autojs.autojs.execution.ScriptExecuteActivity
 */

declare let activity: Internal.Activity;

declare namespace Internal {

    class Activity extends org.autojs.autojs.execution.ScriptExecuteActivity {
        window: Activity.PhoneWindow;
    }

    /**
     * Modified by SuperMonster003 on May 23, 2022.
     * Android platform API: 32.
     */
    class Context extends android.content.Context {
        public ACCESSIBILITY_SERVICE: string;
        public ACCOUNT_SERVICE: string;
        public ACTIVITY_SERVICE: string;
        public ALARM_SERVICE: string;
        public APPWIDGET_SERVICE: string;
        public APP_OPS_SERVICE: string;
        public APP_SEARCH_SERVICE: string;
        public AUDIO_SERVICE: string;
        public BATTERY_SERVICE: string;
        public BIND_ABOVE_CLIENT: number;
        public BIND_ADJUST_WITH_ACTIVITY: number;
        public BIND_ALLOW_OOM_MANAGEMENT: number;
        public BIND_AUTO_CREATE: number;
        public BIND_DEBUG_UNBIND: number;
        public BIND_EXTERNAL_SERVICE: number;
        public BIND_IMPORTANT: number;
        public BIND_INCLUDE_CAPABILITIES: number;
        public BIND_NOT_FOREGROUND: number;
        public BIND_NOT_PERCEPTIBLE: number;
        public BIND_WAIVE_PRIORITY: number;
        public BIOMETRIC_SERVICE: string;
        public BLOB_STORE_SERVICE: string;
        public BLUETOOTH_SERVICE: string;
        public BUGREPORT_SERVICE: string;
        public CAMERA_SERVICE: string;
        public CAPTIONING_SERVICE: string;
        public CARRIER_CONFIG_SERVICE: string;
        public CLIPBOARD_SERVICE: string;
        public COMPANION_DEVICE_SERVICE: string;
        public CONNECTIVITY_DIAGNOSTICS_SERVICE: string;
        public CONNECTIVITY_SERVICE: string;
        public CONSUMER_IR_SERVICE: string;
        public CONTEXT_IGNORE_SECURITY: number;
        public CONTEXT_INCLUDE_CODE: number;
        public CONTEXT_RESTRICTED: number;
        public CROSS_PROFILE_APPS_SERVICE: string;
        public DEVICE_POLICY_SERVICE: string;
        public DISPLAY_HASH_SERVICE: string;
        public DISPLAY_SERVICE: string;
        public DOMAIN_VERIFICATION_SERVICE: string;
        public DOWNLOAD_SERVICE: string;
        public DROPBOX_SERVICE: string;
        public EUICC_SERVICE: string;
        public FILE_INTEGRITY_SERVICE: string;
        public FINGERPRINT_SERVICE: string;
        public GAME_SERVICE: string;
        public HARDWARE_PROPERTIES_SERVICE: string;
        public INPUT_METHOD_SERVICE: string;
        public INPUT_SERVICE: string;
        public IPSEC_SERVICE: string;
        public JOB_SCHEDULER_SERVICE: string;
        public KEYGUARD_SERVICE: string;
        public LAUNCHER_APPS_SERVICE: string;
        public LAYOUT_INFLATER_SERVICE: string;
        public LOCATION_SERVICE: string;
        public MEDIA_COMMUNICATION_SERVICE: string;
        public MEDIA_METRICS_SERVICE: string;
        public MEDIA_PROJECTION_SERVICE: string;
        public MEDIA_ROUTER_SERVICE: string;
        public MEDIA_SESSION_SERVICE: string;
        public MIDI_SERVICE: string;
        public MODE_APPEND: number;
        public MODE_ENABLE_WRITE_AHEAD_LOGGING: number;
        public MODE_MULTI_PROCESS: number;
        public MODE_NO_LOCALIZED_COLLATORS: number;
        public MODE_PRIVATE: number;
        public MODE_WORLD_READABLE: number;
        public MODE_WORLD_WRITEABLE: number;
        public NETWORK_STATS_SERVICE: string;
        public NFC_SERVICE: string;
        public NOTIFICATION_SERVICE: string;
        public NSD_SERVICE: string;
        public PEOPLE_SERVICE: string;
        public PERFORMANCE_HINT_SERVICE: string;
        public POWER_SERVICE: string;
        public PRINT_SERVICE: string;
        public RECEIVER_VISIBLE_TO_INSTANT_APPS: number;
        public RESTRICTIONS_SERVICE: string;
        public ROLE_SERVICE: string;
        public SEARCH_SERVICE: string;
        public SENSOR_SERVICE: string;
        public SHORTCUT_SERVICE: string;
        public STORAGE_SERVICE: string;
        public STORAGE_STATS_SERVICE: string;
        public SYSTEM_HEALTH_SERVICE: string;
        public TELECOM_SERVICE: string;
        public TELEPHONY_IMS_SERVICE: string;
        public TELEPHONY_SERVICE: string;
        public TELEPHONY_SUBSCRIPTION_SERVICE: string;
        public TEXT_CLASSIFICATION_SERVICE: string;
        public TEXT_SERVICES_MANAGER_SERVICE: string;
        public TV_INPUT_SERVICE: string;
        public UI_MODE_SERVICE: string;
        public USAGE_STATS_SERVICE: string;
        public USB_SERVICE: string;
        public USER_SERVICE: string;
        public VIBRATOR_MANAGER_SERVICE: string;
        public VIBRATOR_SERVICE: string;
        public VPN_MANAGEMENT_SERVICE: string;
        public WALLPAPER_SERVICE: string;
        public WIFI_AWARE_SERVICE: string;
        public WIFI_P2P_SERVICE: string;
        public WIFI_RTT_RANGING_SERVICE: string;
        public WIFI_SERVICE: string;
        public WINDOW_SERVICE: string;
        public applicationContext: android.content.Context;
        public applicationInfo: android.content.pm.ApplicationInfo;
        public assets: android.content.res.AssetManager;
        public attributionSource: android.content.AttributionSource;
        public attributionTag: string;
        public cacheDir: java.io.File;
        public classLoader: java.lang.ClassLoader;
        public codeCacheDir: java.io.File;
        public contentResolver: android.content.ContentResolver;
        public dataDir: java.io.File;
        public display: android.view.Display;
        public externalCacheDir: java.io.File;
        public externalCacheDirs: Array<java.io.File>;
        /** @deprecated */
        public externalMediaDirs: Array<java.io.File>;
        public filesDir: java.io.File;
        public mainExecutor: java.util.concurrent.Executor;
        public mainLooper: android.os.Looper;
        public noBackupFilesDir: java.io.File;
        public obbDir: java.io.File;
        public obbDirs: Array<java.io.File>;
        public opPackageName: string;
        public packageCodePath: string;
        public packageManager: android.content.pm.PackageManager;
        public packageName: string;
        public packageResourcePath: string;
        public params: android.content.ContextParams;
        public resources: android.content.res.Resources;
        public theme: android.content.res.Resources.Theme;
        /** @deprecated */
        public wallpaper: android.graphics.drawable.Drawable;
        /** @deprecated */
        public wallpaperDesiredMinimumHeight: number;
        /** @deprecated */
        public wallpaperDesiredMinimumWidth: number;
    }

    /**
     * Created by SuperMonster003 on May 23, 2022.
     * Mixed Class A and B (both are "ui selector"s) together.
     * Class A: org.autojs.autojs.core.accessibility.UiSelector
     * Class B: org.autojs.autojs.core.automator.UiGlobalSelector
     * @example Source code summary (zh-CN: 源代码摘要)
     * this.scope[method] = ((method) => {
     *     return function () {
     *         let s = global.selector();
     *         return s[method].apply(s, arguments);
     *     };
     * })(method);
     */
    class Selector extends org.autojs.autojs.core.accessibility.UiSelector {

        // @ts-ignore
        public accessibilityFocused(b?: boolean): this;

        // @ts-ignore
        public action(...actions: any[]): this;

        // @ts-ignore
        public algorithm(str: string): this;

        // @ts-ignore
        public bottom(min: number, max: number): this;
        // @ts-ignore
        public bottom(value: number): this;

        // @ts-ignore
        public bounds(l: number, t: number, r: number, b: number): this;

        // @ts-ignore
        public boundsBottom(min: number, max: number): this;
        // @ts-ignore
        public boundsBottom(value: number): this;

        // @ts-ignore
        public boundsCenterX(min: number, max: number): this;
        // @ts-ignore
        public boundsCenterX(value: number): this;

        // @ts-ignore
        public boundsCenterY(min: number, max: number): this;
        // @ts-ignore
        public boundsCenterY(value: number): this;

        // @ts-ignore
        public boundsContains(l: number, t: number, r: number, b: number): this;

        // @ts-ignore
        public boundsHeight(min: number, max: number): this;
        // @ts-ignore
        public boundsHeight(value: number): this;

        // @ts-ignore
        public boundsInside(l: number, t: number, r: number, b: number): this;

        // @ts-ignore
        public boundsLeft(min: number, max: number): this;
        // @ts-ignore
        public boundsLeft(value: number): this;

        // @ts-ignore
        public boundsMaxBottom(value: number): this;

        // @ts-ignore
        public boundsMaxCenterX(value: number): this;

        // @ts-ignore
        public boundsMaxCenterY(value: number): this;

        // @ts-ignore
        public boundsMaxHeight(value: number): this;

        // @ts-ignore
        public boundsMaxLeft(value: number): this;

        // @ts-ignore
        public boundsMaxRight(value: number): this;

        // @ts-ignore
        public boundsMaxTop(value: number): this;

        // @ts-ignore
        public boundsMaxWidth(value: number): this;

        // @ts-ignore
        public boundsMinBottom(value: number): this;

        // @ts-ignore
        public boundsMinCenterX(value: number): this;

        // @ts-ignore
        public boundsMinCenterY(value: number): this;

        // @ts-ignore
        public boundsMinHeight(value: number): this;

        // @ts-ignore
        public boundsMinLeft(value: number): this;

        // @ts-ignore
        public boundsMinRight(value: number): this;

        // @ts-ignore
        public boundsMinTop(value: number): this;

        // @ts-ignore
        public boundsMinWidth(value: number): this;

        // @ts-ignore
        public boundsRight(min: number, max: number): this;
        // @ts-ignore
        public boundsRight(value: number): this;

        // @ts-ignore
        public boundsTop(min: number, max: number): this;
        // @ts-ignore
        public boundsTop(value: number): this;

        // @ts-ignore
        public boundsWidth(min: number, max: number): this;
        // @ts-ignore
        public boundsWidth(value: number): this;

        // @ts-ignore
        public centerX(min: number, max: number): this;
        // @ts-ignore
        public centerX(value: number): this;

        // @ts-ignore
        public centerY(min: number, max: number): this;
        // @ts-ignore
        public centerY(value: number): this;

        // @ts-ignore
        public checkable(b?: boolean): this;

        // @ts-ignore
        public checked(b?: boolean): this;

        // @ts-ignore
        public className(str: Selector.ClassName | Selector.ClassNameAbbr | string): this;

        // @ts-ignore
        public classNameContains(str: string): this;

        // @ts-ignore
        public classNameEndsWith(suffix: string): this;

        // @ts-ignore
        public classNameMatch(regex: string | RegExp): this;

        /** @deprecated */
        // @ts-ignore
        public classNameMatches(regex: string | RegExp): this;

        // @ts-ignore
        public classNameStartsWith(prefix: string): this;

        // @ts-ignore
        public clickable(b?: boolean): this;

        // @ts-ignore
        public column(d: number): this;

        // @ts-ignore
        public columnCount(d: number): this;

        // @ts-ignore
        public columnSpan(d: number): this;

        // @ts-ignore
        public content(str: string): this;

        // @ts-ignore
        public contentContains(str: string): this;

        // @ts-ignore
        public contentEndsWith(suffix: string): this;

        // @ts-ignore
        public contentInvalid(b?: boolean): this;

        // @ts-ignore
        public contentMatch(regex: string | RegExp): this;

        /** @deprecated */
        // @ts-ignore
        public contentMatches(regex: string | RegExp): this;

        // @ts-ignore
        public contentStartsWith(prefix: string): this;

        // @ts-ignore
        public contextClickable(b?: boolean): this;

        // @ts-ignore
        public currentApp(app: org.autojs.autojs.util.App): this;
        // @ts-ignore
        public currentApp(name: string): this;

        // @ts-ignore
        public depth(d: number): this;

        // @ts-ignore
        public desc(str: string): this;

        // @ts-ignore
        public descContains(str: string): this;

        // @ts-ignore
        public descEndsWith(suffix: string): this;

        // @ts-ignore
        public descMatch(regex: string | RegExp): this;

        /** @deprecated */
        // @ts-ignore
        public descMatches(regex: string | RegExp): this;

        // @ts-ignore
        public descStartsWith(prefix: string): this;

        // @ts-ignore
        public dismissable(b?: boolean): this;

        // @ts-ignore
        public drawingOrder(order: number): this;

        // @ts-ignore
        public editable(b?: boolean): this;

        // @ts-ignore
        public enabled(b?: boolean): this;

        // @ts-ignore
        public filter(filter: org.autojs.autojs.core.automator.filter.BooleanFilter.BooleanSupplier | ((w: UiObject) => boolean)): this;

        // @ts-ignore
        public focusable(b?: boolean): this;

        // @ts-ignore
        public focused(b?: boolean): this;

        // @ts-ignore
        public height(min: number, max: number): this;
        // @ts-ignore
        public height(value: number): this;

        // @ts-ignore
        public id(str: string): this;

        // @ts-ignore
        public idContains(str: string): this;

        // @ts-ignore
        public idEndsWith(suffix: string): this;

        // @ts-ignore
        public idHex(str: string): this;

        // @ts-ignore
        public idMatch(regex: string | RegExp): this;

        /** @deprecated */
        // @ts-ignore
        public idMatches(regex: string | RegExp): this;

        // @ts-ignore
        public idStartsWith(prefix: string): this;

        // @ts-ignore
        public indexInParent(index: number): this;

        // @ts-ignore
        public left(min: number, max: number): this;
        // @ts-ignore
        public left(value: number): this;

        // @ts-ignore
        public longClickable(b?: boolean): this;

        // @ts-ignore
        public maxBottom(value: number): this;

        // @ts-ignore
        public maxCenterX(value: number): this;

        // @ts-ignore
        public maxCenterY(value: number): this;

        // @ts-ignore
        public maxHeight(value: number): this;

        // @ts-ignore
        public maxLeft(value: number): this;

        // @ts-ignore
        public maxRight(value: number): this;

        // @ts-ignore
        public maxTop(value: number): this;

        // @ts-ignore
        public maxWidth(value: number): this;

        // @ts-ignore
        public minBottom(value: number): this;

        // @ts-ignore
        public minCenterX(value: number): this;

        // @ts-ignore
        public minCenterY(value: number): this;

        // @ts-ignore
        public minHeight(value: number): this;

        // @ts-ignore
        public minLeft(value: number): this;

        // @ts-ignore
        public minRight(value: number): this;

        // @ts-ignore
        public minTop(value: number): this;

        // @ts-ignore
        public minWidth(value: number): this;

        // @ts-ignore
        public multiLine(b?: boolean): this;

        // @ts-ignore
        public packageName(app: org.autojs.autojs.util.App): this;
        // @ts-ignore
        public packageName(str: string): this;

        // @ts-ignore
        public packageNameContains(str: string): this;

        // @ts-ignore
        public packageNameEndsWith(suffix: string): this;

        // @ts-ignore
        public packageNameMatch(regex: string | RegExp): this;

        /** @deprecated */
        // @ts-ignore
        public packageNameMatches(regex: string | RegExp): this;

        // @ts-ignore
        public packageNameStartsWith(prefix: string): this;

        // @ts-ignore
        public password(b?: boolean): this;

        // @ts-ignore
        public right(min: number, max: number): this;
        // @ts-ignore
        public right(value: number): this;

        // @ts-ignore
        public row(d: number): this;

        // @ts-ignore
        public rowCount(d: number): this;

        // @ts-ignore
        public rowSpan(d: number): this;

        // @ts-ignore
        public screenCenterX(): this;
        // @ts-ignore
        public screenCenterX(b: boolean): this;
        // @ts-ignore
        public screenCenterX(b: boolean, tolerance: number): this;
        // @ts-ignore
        public screenCenterX(tolerance: number): this;

        // @ts-ignore
        public screenCenterY(): this;
        // @ts-ignore
        public screenCenterY(b: boolean): this;
        // @ts-ignore
        public screenCenterY(b: boolean, tolerance: number): this;
        // @ts-ignore
        public screenCenterY(tolerance: number): this;

        // @ts-ignore
        public screenCoverage(): this;
        // @ts-ignore
        public screenCoverage(min: number): this;

        // @ts-ignore
        public scrollable(b?: boolean): this;

        // @ts-ignore
        public selected(b?: boolean): this;

        // @ts-ignore
        public text(str: string): this;

        // @ts-ignore
        public textContains(str: string): this;

        // @ts-ignore
        public textEndsWith(suffix: string): this;

        // @ts-ignore
        public textMatch(regex: string | RegExp): this;

        /** @deprecated */
        // @ts-ignore
        public textMatches(regex: string | RegExp): this;

        // @ts-ignore
        public textStartsWith(prefix: string): this;

        // @ts-ignore
        public top(min: number, max: number): this;
        // @ts-ignore
        public top(value: number): this;

        // @ts-ignore
        public visibleToUser(b?: boolean): this;

        // @ts-ignore
        public width(min: number, max: number): this;
        // @ts-ignore
        public width(value: number): this;

    }

    class Runtime extends org.autojs.autojs.runtime.ScriptRuntime {

        public applicationContext: android.content.Context;

        /**
         * @example
         * let str_clip = runtime.clip;
         * console.log("clipboard text: " + str_clip);
         * @example Source code summary (zh-CN: 源代码摘要)
         * if (Looper.myLooper() === Looper.getMainLooper()) {
         *     return ClipboardUtil.getClipOrEmpty(uiHandler.getContext()).toString();
         * }
         * let clip = new VolatileDispose();
         * uiHandler.post(function () {
         *     clip.setAndNotify(ClipboardUtil.getClipOrEmpty(uiHandler.getContext()).toString());
         * });
         * return clip.blockedGetOrThrow(ScriptInterruptedException.class);
         * @see org.autojs.autojs.concurrent.VolatileDispose
         * @see android.os.Looper
         * @see org.autojs.autojs.util.ClipboardUtils
         */
        public clip: string;
        /**
         * @example
         * let java_images = runtime.images;
         * let orientation = org.autojs.autojs.core.image.capture.ScreenCapturer.ORIENTATION_AUTO;
         * const ResultAdapter = require("result-adapter");
         *
         * if (ResultAdapter.wait(java_images.requestScreenCapture(orientation))) {
         *     let capt = java_images.captureScreen();
         *     console.log(capt.width + " × " + capt.height); // e.g. 720 × 1280
         * } else {
         *     console.error('request screen capture permission failed');
         * }
         */
        public images: org.autojs.autojs.runtime.api.Images;
        public rootShell: org.autojs.autojs.runtime.api.AbstractShell;
        public screenMetrics: org.autojs.autojs.runtime.api.ScreenMetrics;
        public topLevelScope: org.autojs.autojs.rhino.TopLevelScope;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * private TopLevelScope mTopLevelScope;
         * public Continuation createContinuation() {
         *     return Continuation.Companion.create(this, mTopLevelScope);
         * }
         * public Continuation createContinuation(Scriptable scope) {
         *     return Continuation.Companion.create(this, scope);
         * }
         * @see org.autojs.autojs.rhino.TopLevelScope
         * @see org.autojs.autojs.rhino.continuation.Continuation
         * @see org.mozilla.javascript.Scriptable
         * @param [scope=global]
         */
        public createContinuation(scope?: org.mozilla.javascript.Scriptable): org.autojs.autojs.rhino.continuation.Continuation;

        /**
         * @example
         * runtime.exit();
         * @example Source code summary (zh-CN: 源代码摘要)
         * mThread.interrupt();
         * engines.myEngine().forceStop();
         * threads.exit();
         * if (Looper.myLooper() !== Looper.getMainLooper()) {
         *     throw new ScriptInterruptedException();
         * }
         * @see org.autojs.autojs.runtime.ScriptRuntime.exit
         */
        public exit(): void;
        /**
         * @example
         * try {throw new Error('test')}
         * catch(e) {runtime.exit(java.lang.Throwable(e))}
         * log("OK");
         * @example Source code summary (zh-CN: 源代码摘要)
         * engines.myEngine().uncaughtException(e);
         * exit();
         * @see org.autojs.autojs.runtime.ScriptRuntime.exit
         */
        public exit(e: java.lang.Throwable): void;

        /**
         * @example
         * let str_clip = runtime.getClip();
         * console.log("clipboard text: " + str_clip);
         * @example Source code summary (zh-CN: 源代码摘要)
         * if (Looper.myLooper() === Looper.getMainLooper()) {
         *     return ClipboardUtil.getClipOrEmpty(uiHandler.getContext()).toString();
         * }
         * let clip = new VolatileDispose();
         * uiHandler.post(function () {
         *     clip.setAndNotify(ClipboardUtil.getClipOrEmpty(uiHandler.getContext()).toString());
         * });
         * return clip.blockedGetOrThrow(ScriptInterruptedException.class);
         * @see org.autojs.autojs.concurrent.VolatileDispose
         * @see android.os.Looper
         * @see org.autojs.autojs.util.ClipboardUtils
         */
        public getClip(): string;

        /**
         * @example
         * let java_images = runtime.getImages();
         * let orientation = org.autojs.autojs.core.image.capture.ScreenCapturer.ORIENTATION_AUTO;
         * const ResultAdapter = require("result-adapter");
         *
         * if (ResultAdapter.wait(java_images.requestScreenCapture(orientation))) {
         *     let capt = java_images.captureScreen();
         *     console.log(capt.width + " × " + capt.height); // e.g. 720 × 1280
         * } else {
         *     console.error('request screen capture permission failed');
         * }
         */
        public getImages(): org.autojs.autojs.runtime.api.Images;

        /**
         * @example
         * runtime.isStopped();
         * @example Source code summary (zh-CN: 源代码摘要)
         * Thread.currentThread().isInterrupted();
         * @see java.lang.Thread
         */
        public isStopped(): boolean;

        /**
         * @example
         * runtime.loadDex("test.dex");
         * @example Source code summary (zh-CN: 源代码摘要)
         * ContextFactory.getGlobal().getApplicationClassLoader()).loadDex(new File(path));
         * @see org.mozilla.javascript.ContextFactory
         */
        public loadDex(path: string): void;

        /**
         * @example
         * runtime.loadJar("test.jar");
         * @example Source code summary (zh-CN: 源代码摘要)
         * ContextFactory.getGlobal().getApplicationClassLoader()).loadJar(new File(path));
         * @see org.mozilla.javascript.ContextFactory
         */
        public loadJar(path: string): void;

        /**
         * @example
         * runtime.requestPermissions("access_fine_location"); // GPS
         * runtime.requestPermissions("record_audio"); // REC
         * @example Source code summary (zh-CN: 源代码摘要)
         * if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
         *     permissions = Permissions.getPermissionsNeedToRequest(context, permissions);
         *     permissions.length && Permissions.requestPermissions(context, permissions);
         * }
         * @see org.autojs.autojs.core.permission.Permissions
         */
        public requestPermissions(permissions: ('access_fine_location' | 'record_audio')[]): void;

        /**
         * @example
         * runtime.requiresApi(26); // Oreo 8.0.0
         * runtime.requiresApi(25); // Nougat 7.1
         * runtime.requiresApi(24); // Nougat 7.0
         * runtime.requiresApi(23); // Marshmallow 6.0
         * runtime.requiresApi(22); // Lollipop 5.1
         * runtime.requiresApi(21); // Lollipop 5.0
         * runtime.requiresApi(19); // KitKat 4.4 - 4.4.4
         * runtime.requiresApi(18); // Jelly Bean 4.3.x
         * runtime.requiresApi(17); // Jelly Bean 4.2.x
         * runtime.requiresApi(16); // Jelly Bean 4.1.x
         * runtime.requiresApi(15); // Ice Cream Sandwich 4.0.3 - 4.0.4
         * runtime.requiresApi(14); // Ice Cream Sandwich 4.0.1 - 4.0.2
         * @example Source code summary (zh-CN: 源代码摘要)
         * if (Build.VERSION.SDK_INT < i) {
         *     let str_a = GlobalAppContext.getString(R.string.text_requires_sdk_version_to_run_the_script);
         *     let str_b = SdkVersionUtil.sdkIntToString(i);
         *     throw new ScriptException(str_a + str_b);
         * }
         * @see org.autojs.autojs.app.GlobalAppContext
         * @see org.autojs.autojs.util.SdkVersionUtils
         * @see org.autojs.autojs.runtime.exception.ScriptException
         */
        public requiresApi(api: number): void;

        public setApplicationContext(context: android.content.Context): void;

        /**
         * @example
         * runtime.setClip("Hello");
         * @example Source code summary (zh-CN: 源代码摘要)
         * if (Looper.myLooper() == Looper.getMainLooper()) {
         *     ClipboardUtil.setClip(uiHandler.getContext(), text);
         * } else {
         *     let dispose = new VolatileDispose();
         *     uiHandler.post(function () {
         *         ClipboardUtil.setClip(uiHandler.getContext(), text);
         *         dispose.setAndNotify(text);
         *     });
         *     dispose.blockedGet();
         * }
         * @see org.autojs.autojs.concurrent.VolatileDispose
         * @see android.os.Looper
         * @see org.autojs.autojs.util.ClipboardUtils
         */
        public setClip(text: string): void;

        /**
         * @example
         * runtime.setScreenMetrics(720, 1280);
         * @example Source code summary (zh-CN: 源代码摘要)
         * let mScreenMetrics = new ScreenMetrics();
         * mScreenMetrics.setScreenMetrics(width, height);
         * @see org.autojs.autojs.runtime.api.ScreenMetrics
         */
        public setScreenMetrics(width: number, height: number): void;

        public setTopLevelScope(topLevelScope: org.autojs.autojs.rhino.TopLevelScope): void;

        public shell(cmd: string, root: number): org.autojs.autojs.runtime.api.AbstractShell.Result;

        /**
         * @example
         * sleep(1000);
         * @example Source code summary (zh-CN: 源代码摘要)
         * if (ui.isUiThread()) {
         *     throw new Error('不能在ui线程执行阻塞操作，请使用setTimeout代替');
         * }
         * runtime.sleep(t);
         * @see java.lang.Thread.sleep
         */
        public sleep(millis: number): void;

        /** @deprecated */
        public stop(): void;

        /**
         * @example
         * toast("Hello");
         * @example Source code summary (zh-CN: 源代码摘要)
         * uiHandler.toast(text);
         * @see org.autojs.autojs.runtime.ScriptRuntime.uiHandler
         */
        public toast(text): void;

    }

}

declare namespace Activity {

    class PhoneWindow extends com.android.internal.policy.PhoneWindow {
        public decorView: com.android.internal.policy.DecorView;
    }

}

// @Supplement

declare class Intent extends android.content.Intent {
    /* Empty body. */
}

declare class Context extends android.content.Context {
    /* Empty body. */
}

declare class OkHttpClient extends okhttp3.OkHttpClient {
    /* Empty body. */
}

declare class __engine__ extends org.autojs.autojs.engine.LoopBasedJavaScriptEngine {
    /* Empty body. */
}

declare class MutableOkHttp extends org.autojs.autojs.core.http.MutableOkHttp {
    /* Empty body. */
}

declare class VolatileBox<T> extends org.autojs.autojs.concurrent.VolatileBox<T> {
    /* Empty body. */
}

declare class ReentrantLock extends java.util.concurrent.locks.ReentrantLock {
    /* Empty body. */
}

declare class ScreenMetrics extends org.autojs.autojs.runtime.api.ScreenMetrics {
    /* Empty body. */
}

declare class WebView extends android.webkit.WebView {
    /* Empty body. */
}

declare class WebViewClient extends android.webkit.WebViewClient {
    /* Empty body. */
}

declare class WebChromeClient extends android.webkit.WebChromeClient {
    /* Empty body. */
}

declare class StandardCharsets extends java.nio.charset.StandardCharsets {
    /* Empty body. */
}

declare class PendingIntent extends android.app.PendingIntent {
    /* Empty body. */
}

declare class GlobalAppContext extends org.autojs.autojs.app.GlobalAppContext {
    /* Empty body. */
}

declare class ArrayUtils extends org.autojs.autojs.util.ArrayUtils {
    /* Empty body. */
}

declare class DisplayUtils extends org.autojs.autojs.util.DisplayUtils {
    /* Empty body. */
}

declare class ColorUtils extends org.autojs.autojs.util.ColorUtils {
    /* Empty body. */
}

declare class StringUtils extends org.autojs.autojs.util.StringUtils {
    /* Empty body. */
}

declare class ProxyObject extends org.autojs.autojs.rhino.ProxyObject {
    /* Empty body. */
}

declare class ProxyJavaObject extends org.autojs.autojs.rhino.ProxyJavaObject {
    /* Empty body. */
}

declare class NotificationCompat extends androidx.core.app.NotificationCompat {
    /* Empty body. */
}

declare class NotificationManagerCompat extends androidx.core.app.NotificationManagerCompat {
    /* Empty body. */
}

declare class URI extends java.net.URI {
    /* Empty body. */
}

declare class File extends java.io.File {
    /* Empty body. */
}

/* View classes. */

declare class JsAppBarLayout extends org.autojs.autojs.core.ui.widget.JsAppBarLayout {
    /* Empty body. */
}

declare class JsButton extends org.autojs.autojs.core.ui.widget.JsButton {
    /* Empty body. */
}

declare class JsCanvasView extends org.autojs.autojs.core.ui.widget.JsCanvasView {
    /* Empty body. */
}

declare class JsCardView extends org.autojs.autojs.core.ui.widget.JsCardView {
    /* Empty body. */
}

declare class JsCheckBox extends org.autojs.autojs.core.ui.widget.JsCheckBox {
    /* Empty body. */
}

declare class JsConsoleView extends org.autojs.autojs.core.ui.widget.JsConsoleView {
    /* Empty body. */
}

declare class JsDatePicker extends org.autojs.autojs.core.ui.widget.JsDatePicker {
    /* Empty body. */
}

declare class JsDrawerLayout extends org.autojs.autojs.core.ui.widget.JsDrawerLayout {
    /* Empty body. */
}

declare class JsEditText extends org.autojs.autojs.core.ui.widget.JsEditText {
    /* Empty body. */
}

declare class JsFloatingActionButton extends org.autojs.autojs.core.ui.widget.JsFloatingActionButton {
    /* Empty body. */
}

declare class JsFrameLayout extends org.autojs.autojs.core.ui.widget.JsFrameLayout {
    /* Empty body. */
}

declare class JsGridView extends org.autojs.autojs.core.ui.widget.JsGridView {
    /* Empty body. */
}

declare class JsImageButton extends org.autojs.autojs.core.ui.widget.JsImageButton {
    /* Empty body. */
}

declare class JsImageView extends org.autojs.autojs.core.ui.widget.JsImageView {
    /* Empty body. */
}

declare class JsLinearLayout extends org.autojs.autojs.core.ui.widget.JsLinearLayout {
    /* Empty body. */
}

declare class JsListView extends org.autojs.autojs.core.ui.widget.JsListView {
    /* Empty body. */
}

declare class JsProgressBar extends org.autojs.autojs.core.ui.widget.JsProgressBar {
    /* Empty body. */
}

declare class JsRadioButton extends org.autojs.autojs.core.ui.widget.JsRadioButton {
    /* Empty body. */
}

declare class JsRadioGroup extends org.autojs.autojs.core.ui.widget.JsRadioGroup {
    /* Empty body. */
}

declare class JsRatingBar extends org.autojs.autojs.core.ui.widget.JsRatingBar {
    /* Empty body. */
}

declare class JsRelativeLayout extends org.autojs.autojs.core.ui.widget.JsRelativeLayout {
    /* Empty body. */
}

declare class JsScrollView extends org.autojs.autojs.core.ui.widget.JsScrollView {
    /* Empty body. */
}

declare class JsSeekBar extends org.autojs.autojs.core.ui.widget.JsSeekBar {
    /* Empty body. */
}

declare class JsSpinner extends org.autojs.autojs.core.ui.widget.JsSpinner {
    /* Empty body. */
}

declare class JsSwitch extends org.autojs.autojs.core.ui.widget.JsSwitch {
    /* Empty body. */
}

declare class JsTabLayout extends org.autojs.autojs.core.ui.widget.JsTabLayout {
    /* Empty body. */
}

declare class JsTextClock extends org.autojs.autojs.core.ui.widget.JsTextClock {
    /* Empty body. */
}

declare class JsTextView extends org.autojs.autojs.core.ui.widget.JsTextView {
    /* Empty body. */
}

declare class JsTimePicker extends org.autojs.autojs.core.ui.widget.JsTimePicker {
    /* Empty body. */
}

declare class JsToggleButton extends org.autojs.autojs.core.ui.widget.JsToggleButton {
    /* Empty body. */
}

declare class JsToolbar extends org.autojs.autojs.core.ui.widget.JsToolbar {
    /* Empty body. */
}

declare class JsViewPager extends org.autojs.autojs.core.ui.widget.JsViewPager {
    /* Empty body. */
}

declare class JsWebView extends org.autojs.autojs.core.ui.widget.JsWebView {
    /* Empty body. */
}

declare class JsVideoView extends org.autojs.autojs.core.ui.widget.JsVideoView {
    /* Empty body. */
}

declare class JsViewFlipper extends org.autojs.autojs.core.ui.widget.JsViewFlipper {
    /* Empty body. */
}

declare class JsViewSwitcher extends org.autojs.autojs.core.ui.widget.JsViewSwitcher {
    /* Empty body. */
}

declare class ScriptRuntime extends org.autojs.autojs.runtime.ScriptRuntime {
    /* Empty body. */
}

declare class EventEmitter extends org.autojs.autojs.core.eventloop.EventEmitter {
    /* Empty body. */
}

declare class FileProvider extends androidx.core.content.FileProvider {
    /* Empty body. */
}

declare class AppUtils extends org.autojs.autojs.runtime.api.AppUtils {
    /* Empty body. */
}

declare class Uri extends android.net.Uri {
    /* Empty body. */
}

declare class JavaScriptSource extends org.autojs.autojs.script.JavaScriptSource {
    /* Empty body. */
}

declare class Looper extends android.os.Looper {
    /* Empty body. */
}

declare class Runnable extends java.lang.Runnable {
    /* Empty body. */
}

declare class Build extends android.os.Build {
    /* Empty body. */
}

declare class Manifest extends android.Manifest {
    /* Empty body. */
}

declare class BuildConfig extends org.autojs.autojs6.BuildConfig {
    /* Empty body. */
}

declare class PackageManager extends android.content.pm.PackageManager {
    /* Empty body. */
}

declare class NetworkUtils extends org.autojs.autojs.util.NetworkUtils {
    /* Empty body. */
}

declare class DeviceUtils extends org.autojs.autojs.util.DeviceUtils {
    /* Empty body. */
}

declare class JavaUtils extends org.autojs.autojs.util.JavaUtils {
    /* Empty body. */
}

declare class RootUtils extends org.autojs.autojs.util.RootUtils {
    /* Empty body. */
}

declare class InputType extends android.text.InputType {
    /* Empty body. */
}

declare class TextWatcher extends android.text.TextWatcher {
    /* Empty body. */
}

declare class AccessibilityBridge extends org.autojs.autojs.core.accessibility.AccessibilityBridge {
    /* Empty body. */
}

declare class Base64 extends android.util.Base64 {
    /* Empty body. */
}

declare class ColorStateList extends android.content.res.ColorStateList {
    /* Empty body. */
}

declare class ColorDetector extends org.autojs.autojs.core.image.ColorDetector {
    /* Empty body. */
}

declare class ThemeColor extends org.autojs.autojs.theme.ThemeColor {
    /* Empty body. */
}

declare class AtomicLong extends java.util.concurrent.atomic.AtomicLong {
    /* Empty body. */
}

declare class BigTextStyle extends androidx.core.app.NotificationCompat.BigTextStyle {
    /* Empty body. */
}

declare class Bitmap extends android.graphics.Bitmap {
    /* Empty body. */
}

declare class BitmapFactory extends android.graphics.BitmapFactory {
    /* Empty body. */
}

declare class Typeface extends android.graphics.Typeface {
    /* Empty body. */
}

declare class ByteArrayOutputStream extends java.io.ByteArrayOutputStream {
    /* Empty body. */
}

declare class Callback extends okhttp3.Callback {
    /* Empty body. */
}

declare class ColorDrawable extends android.graphics.drawable.ColorDrawable {
    /* Empty body. */
}

declare class ConsoleUtils extends org.autojs.autojs.util.ConsoleUtils {
    /* Empty body. */
}

declare class ContextThemeWrapper extends android.view.ContextThemeWrapper {
    /* Empty body. */
}

declare class DynamicLayoutInflater extends org.autojs.autojs.core.ui.inflater.DynamicLayoutInflater {
    /* Empty body. */
}

declare class FormBody extends okhttp3.FormBody {
    /* Empty body. */
}

declare class Gravity extends android.view.Gravity {
    /* Empty body. */
}

declare class IntentUtils extends org.autojs.autojs.util.IntentUtils {
    /* Empty body. */
}

declare class JavaScriptEngine extends org.autojs.autojs.engine.JavaScriptEngine {
    /* Empty body. */
}

declare class ScaleGestureDetector extends android.view.ScaleGestureDetector {
    /* Empty body. */
}

declare class WindowManager extends android.view.WindowManager {
    /* Empty body. */
}

declare class LayoutParams extends android.view.WindowManager.LayoutParams {
    /* Empty body. */
}

declare class Level extends org.apache.log4j.Level {
    /* Empty body. */
}

declare class Linkify extends android.text.util.Linkify {
    /* Empty body. */
}

declare class Log extends android.util.Log {
    /* Empty body. */
}

declare class TypedValue extends android.util.TypedValue {
    /* Empty body. */
}

declare class LogConfigurator extends de.mindpipe.android.logging.log4j.LogConfigurator {
    /* Empty body. */
}

declare class LogManager extends org.apache.log4j.LogManager {
    /* Empty body. */
}

declare class Mat extends org.autojs.autojs.core.opencv.Mat {
    /* Empty body. */
}

declare class MediaType extends okhttp3.MediaType {
    /* Empty body. */
}

declare class MimeTypeMap extends android.webkit.MimeTypeMap {
    /* Empty body. */
}

declare class MultipartBody extends okhttp3.MultipartBody {
    /* Empty body. */
}

declare class Notification extends android.app.Notification {
    /* Empty body. */
}

declare class NotificationUtils extends org.autojs.autojs.util.NotificationUtils {
    /* Empty body. */
}

declare class PFile extends org.autojs.autojs.pio.PFile {
    /* Empty body. */
}

declare class Request extends okhttp3.Request {
    /* Empty body. */
}

declare class RequestBody extends okhttp3.RequestBody {
    /* Empty body. */
}

declare class Snackbar extends com.google.android.material.snackbar.Snackbar {
    /* Empty body. */
}

declare class MaterialDialog extends com.afollestad.materialdialogs.MaterialDialog {
    /* Empty body. */
}

declare class Thread extends java.lang.Thread {
    /* Empty body. */
}

declare class Throwable extends java.lang.Throwable {
    /* Empty body. */
}

declare class ApkBuilder extends org.autojs.autojs.apkbuilder.ApkBuilder {
    /* Empty body. */
}

declare class GestureResultCallback extends android.accessibilityservice.AccessibilityService.GestureResultCallback {
    /* Empty body. */
}

declare class TimeUnit extends java.util.concurrent.TimeUnit {
    /* Empty body. */
}

declare class Imgproc extends org.opencv.imgproc.Imgproc {
    /* Empty body. */
}

declare class Imgcodecs extends org.opencv.imgcodecs.Imgcodecs {
    /* Empty body. */
}

declare class CvType extends org.opencv.core.CvType {
    /* Empty body. */
}

declare class ScriptModuleIdentifier extends org.autojs.autojs.engine.ScriptEngineService.ScriptModuleIdentifier {
    /* Empty body. */
}

declare class TopLevelScope extends org.autojs.autojs.rhino.TopLevelScope {
    /* Empty body. */
}

declare class ContinuationResult extends org.autojs.autojs.rhino.continuation.Continuation.Result {
    /* Empty body. */
}

declare class ContinuationCreator extends org.autojs.autojs.runtime.api.augment.continuation.Creator {
    /* Empty body. */
}

declare class RhinoUtils extends org.autojs.autojs.util.RhinoUtils {
    /* Empty body. */
}

declare class EvaluatorException extends org.mozilla.javascript.EvaluatorException {
    /* Empty body. */
}

declare class PorterDuff extends android.graphics.PorterDuff {
    /* Empty body. */
}

declare class ImageViewCompat extends androidx.core.widget.ImageViewCompat {
    /* Empty body. */
}

declare class AndroidUtils extends org.autojs.autojs.util.AndroidUtils {
    /* Empty body. */
}
