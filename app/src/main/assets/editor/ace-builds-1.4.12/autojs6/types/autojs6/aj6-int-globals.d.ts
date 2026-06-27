// noinspection JSUnusedGlobalSymbols

// Type definitions for AutoJs6 internal module globals
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 5.1.3
//
// Last modified: Jun 15, 2026

/// <reference path="./index.d.ts" />

/**
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/global/Global.kt
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/global/Legacy.kt
 */

declare let isAutoJs6: boolean;

declare let HEIGHT: number;
declare let WIDTH: number;

declare let axios: any;
declare let cheerio: any;
declare let dayjs: any;

/**
 * @see runtime.sleep
 */
declare function sleep(millis: number): void;
declare function sleep(millisMin: number, millisMax: number): void;
declare function sleep(millis: number, bounds: string): void;

/**
 * @example
 * toastLog("Hello");
 * @example Source code summary (zh-CN: 源代码摘要)
 * runtime.toast(msg);
 * global.log(msg);
 * @see runtime.toast
 * @see log
 */
declare function toastVerbose(msg: any, isLong?: Toast.IsLong, isForcible?: Toast.IsForcible): void;
declare function toastverbose(msg: any, isLong?: Toast.IsLong, isForcible?: Toast.IsForcible): void;

declare function toastLog(msg?: any, isLong?: Toast.IsLong, isForcible?: Toast.IsForcible): void;
declare function toastlog(msg?: any, isLong?: Toast.IsLong, isForcible?: Toast.IsForcible): void;

declare function toastInfo(msg: any, isLong?: Toast.IsLong, isForcible?: Toast.IsForcible): void;
declare function toastinfo(msg: any, isLong?: Toast.IsLong, isForcible?: Toast.IsForcible): void;

declare function toastWarn(msg: any, isLong?: Toast.IsLong, isForcible?: Toast.IsForcible): void;
declare function toastwarn(msg: any, isLong?: Toast.IsLong, isForcible?: Toast.IsForcible): void;

declare function toastError(msg: any, isLong?: Toast.IsLong, isForcible?: Toast.IsForcible): void;
declare function toasterror(msg: any, isLong?: Toast.IsLong, isForcible?: Toast.IsForcible): void;

/**
 * @example
 * try {
 *     throw new Error('test');
 * } catch(e) {
 *     exit(java.lang.Throwable(e));
 * }
 * log('OK');
 * @example Source code summary (zh-CN: 源代码摘要)
 * runtime.exit.bind(runtime)
 * @see runtime.exit
 */
declare function exit(e?: OmniThrowable): void;

/**
 * @deprecated use exit() instead
 * @see exit
 */
declare function stop(): void;

/**
 * @see runtime.isStopped
 */
declare function isStopped(): boolean;

/** @see isStopped */
declare function isShuttingDown(): boolean;

/**
 * @deprecated Use {@link isRunning} instead.
 */
declare function notStopped(): boolean;

/**
 * @example Source code summary (zh-CN: 源代码摘要)
 * return !isStopped();
 */
declare function isRunning(): boolean;

/**
 * @see runtime.setClip
 */
declare function setClip(text: string): void;

/**
 * @see runtime.getClip
 */
declare function getClip(): string;

/**
 * @example
 * while (~sleep(200)) {
 *     if (currentPackage() === "com.example.test") {
 *         break;
 *     }
 * }
 * @example Source code summary (zh-CN: 源代码摘要)
 * global.auto();
 * return runtime.info.getLatestPackage();
 * @see auto
 * @see runtime.info.getLatestPackage
 */
declare function currentPackage(mode?: Global.ComponentFetchMode | Global.ComponentFetchOptions): string;

/**
 * @example
 * while (~sleep(200)) {
 *     if (currentActivity() === "com.sonymobile.home.HomeActivity") {
 *         break;
 *     }
 * }
 * @example Source code summary (zh-CN: 源代码摘要)
 * global.auto();
 * return runtime.info.getLatestActivity();
 * @see auto
 * @see runtime.info.getLatestActivity
 */
declare function currentActivity(mode?: Global.ComponentFetchMode | Global.ComponentFetchOptions): string;

declare function currentComponent(mode?: Global.ComponentFetchMode | Global.ComponentFetchOptions): string;

declare function waitForActivity<R>(activityName: string, limit: number, interval: number, callback: Wait.Callback<string, R>): R extends void ? boolean : R;
declare function waitForActivity<R>(activityName: string, limit: number, callback: Wait.Callback<string, R>): R extends void ? boolean : R;
declare function waitForActivity<R>(activityName: string, callback: Wait.Callback<string, R>): R extends void ? boolean : R;
declare function waitForActivity(activityName: string, limit: number, interval: number): boolean;
declare function waitForActivity(activityName: string, limit: number): boolean;
declare function waitForActivity(activityName: string): boolean;

declare function waitForPackage<R>(packageName: string, limit: number, interval: number, callback: Wait.Callback<string, R>): R extends void ? boolean : R;
declare function waitForPackage<R>(packageName: string, limit: number, callback: Wait.Callback<string, R>): R extends void ? boolean : R;
declare function waitForPackage<R>(packageName: string, callback: Wait.Callback<string, R>): R extends void ? boolean : R;
declare function waitForPackage(packageName: string, limit: number, interval: number): boolean;
declare function waitForPackage(packageName: string, limit: number): boolean;
declare function waitForPackage(packageName: string): boolean;

/**
 * @example
 * setScreenMetrics(720, 1280);
 * @example Source code summary (zh-CN: 源代码摘要)
 * global.setScreenMetrics = runtime.setScreenMetrics.bind(runtime);
 * @see runtime.setScreenMetrics
 */
declare function setScreenMetrics(width: number, height: number): void;

/**
 * @example
 * requiresApi(26); // Oreo 8.0.0
 * requiresApi(25); // Nougat 7.1
 * requiresApi(24); // Nougat 7.0
 * requiresApi(23); // Marshmallow 6.0
 * requiresApi(22); // Lollipop 5.1
 * requiresApi(21); // Lollipop 5.0
 * requiresApi(19); // KitKat 4.4 - 4.4.4
 * requiresApi(18); // Jelly Bean 4.3.x
 * requiresApi(17); // Jelly Bean 4.2.x
 * requiresApi(16); // Jelly Bean 4.1.x
 * requiresApi(15); // Ice Cream Sandwich 4.0.3 - 4.0.4
 * requiresApi(14); // Ice Cream Sandwich 4.0.1 - 4.0.2
 * @example Source code summary (zh-CN: 源代码摘要)
 * global.requiresApi = runtime.requiresApi.bind(runtime);
 * @see runtime.requiresApi
 */
declare function requiresApi(api: number): void;

/**
 * @example
 * // extra log will be printed
 * // look up the code in source code summary
 * requiresAutojsVersion("4.1.1 Alpha2");
 * requiresAutojsVersion(461); // same as above
 * @example Source code summary (zh-CN: 源代码摘要)
 * if (typeof version === 'number') {
 *     if (compare(version, app.autojs.versionCode) > 0) {
 *         throw new Error("需要Auto.js版本号" + version + "以上才能运行");
 *     }
 * } else {
 *     if (compareVersion(version, app.autojs.versionName) > 0) {
 *         throw new Error("需要Auto.js版本" + version + "以上才能运行");
 *     }
 * }
 * function compareVersion(v1, v2) {
 *     v1 = parseVersion(v1);
 *     v2 = parseVersion(v2);
 *     log(v1, v2);
 *     return v1.major != v2.major ? compare(v1.major, v2.major) :
 *         v1.minor != v2.minor ? compare(v1.minor, v2.minor) :
 *         v1.revision != v2.revision ? compare(v1.revision, v2.revision) :
 *         v1.buildType != v2.buildType ? compare(v1.buildType, v2.buildType) :
 *         compare(v1.build, v2.build);
 * }
 * function compare(a, b) {
 *     return a > b ? 1 : a < b ? -1 : 0;
 * }
 * function parseVersion(v) {
 *     let m = /(\d+)\.(\d+)\.(\d+)[ ]?(Alpha|Beta)?(\d*)/.exec(v);
 *     if (!m) throw new Error("版本格式不合法: " + v)
 *     return {
 *         major: parseInt(m[1]),
 *         minor: parseInt(m[2]),
 *         revision: parseInt(m[3]),
 *         buildType: buildType(m[4]),
 *         build: m[5] ? parseInt(m[5]) : 1
 *     };
 * }
 */
declare function requiresAutojsVersion(version: string | number): void;

declare function getScaleBases(): Global.ScaleBases;

declare function getScaleBaseX(): number;

declare function getScaleBaseY(): number;

declare function setScaleBases(baseX: number, baseY: number): void;

declare function setScaleBaseX(baseX: number): void;

declare function setScaleBaseY(baseY: number): void;

/**
 * @example
 * let amount = 3;
 * while(amount--) {
 *     log(random(80, 100));
 * }
 * @example Source code summary (zh-CN: 源代码摘要)
 * return Math.floor(Math.random() * (max - min + 1)) + min;
 * @see Math.random
 * @see Math.floor
 */
declare function random(min: number, max: number): number;
/**
 * @example
 * let amount = 3;
 * while(amount--) {
 *     log(random());
 * }
 * @example Source code summary (zh-CN: 源代码摘要)
 * return Math.random();
 * @see Math.random
 */
declare function random(): number;

declare function cX(num: number, base: number, isRatio?: boolean | 'auto'): number;
declare function cX(num: number, isRatio?: boolean | 'auto'): number;
declare function cX(): number;

declare function cY(num: number, base: number, isRatio?: boolean | 'auto'): number;
declare function cY(num: number, isRatio?: boolean | 'auto'): number;
declare function cY(): number;

declare function cYx(num: number, base: number | string, isRatio?: boolean | 'auto'): number;
declare function cYx(num: number, isRatio?: boolean | 'auto'): number;

declare function cXy(num: number, base: number | string, isRatio?: boolean | 'auto'): number;
declare function cXy(num: number, isRatio?: boolean | 'auto'): number;

/**
 * Wait a period of time until a condition is met
 * @param condition - wait until the condition is met
 * @param [limit=10e3] - if < 100, taken as times
 * @param [interval=200]
 * @param callback
 * @example
 * console.log(wait('hello'));
 * console.log(wait('hello', 10e3));
 * console.log(wait('hello', 10e3, 200));
 * @example
 * console.log(wait('hello'));
 * console.log(wait(text('hello')));
 * console.log(wait(() => text('hello').exists()));
 * @example
 * if (wait(() => Date.now() > new Date(2021, 0), 0, 100)) {
 *     toastLog('Welcome to 2021');
 * }
 * @example
 * wait(() => pickup(/\w{2,}/), 1e3, 120, {
 *     then: res => console.log(res.bounds()),
 * });
 * wait(() => pickup(/\w{2,}/, 'wc'), 1e3, 120, {
 *     then(res) {
 *         res.forEach(w => console.log(detect(w, 'txt')));
 *     },
 * });
 */
declare function wait<T, R>(condition: Wait.Condition<T>, limit: number, interval: number, callback: Wait.Callback<T, R>): R extends void ? boolean : R;
declare function wait<T, R>(condition: Wait.Condition<T>, limit: number, callback: Wait.Callback<T, R>): R extends void ? boolean : R;
declare function wait<T, R>(condition: Wait.Condition<T>, callback: Wait.Callback<T, R>): R extends void ? boolean : R;
declare function wait(condition: Wait.Condition<any>, limit: number, interval: number): boolean;
declare function wait(condition: Wait.Condition<any>, limit: number): boolean;
declare function wait(condition: Wait.Condition<any>): boolean;

declare namespace Wait {

    interface Callback<T, R> {
        then?(result?: T): R;

        else?(result?: T): R;
    }

    type Condition<T> = Pickup.Selector | (() => T);

}

declare namespace Global {

    type ComponentFetchMode = 'auto' | 'automatic' | 'automatism' | 'a11y' | 'accessibility' | 'shizuku' | 'root';

    interface ComponentFetchOptions {
        by?: ComponentFetchMode;
        mode?: ComponentFetchMode;
    }

    interface ScaleBases {
        x: number;
        y: number;
    }

}

/**
 * @deprecated Use {@link Species.isObject} instead.
 */
declare function isObjectSpecies(o: any): boolean;

/**
 * @throws org.autojs.autojs.runtime.exception.NotImplementedError
 * @function
 */
declare function TODO(reason?: string): void;

declare function unwrapJavaObject(o: { getClass(): java.lang.Class<any> }): any;

declare function isJavaObject(o: any): boolean;

declare function isPrimitive(arg: any): boolean;

declare function isReference(arg: any): boolean;

declare function isEmptyObject(arg: any): boolean;

declare function isInteger(arg: any): boolean;

declare function isBigInt(arg: any): boolean;
