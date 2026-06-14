// Type definitions for AutoJs6 internal module app
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 4.3.5
//
// Last modified: Oct 21, 2021

/// <reference path="../index.d.ts" />

/**
 * @Source %AutoJs6Assets%/modules/__app__.js
 */

declare namespace Internal {

    class App extends org.autojs.autojs.runtime.api.AppUtils {

        autojs: Internal.Autojs;

        /**
         * @example
         * // e.g. 461 (of Auto.js 4.1.1 Alpha2)
         * log(app.versionCode);
         * @example Source code summary (zh-CN: 源代码摘要)
         * context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode
         */
        versionCode: number;

        /**
         * @example
         * // e.g. 4.1.1 Alpha2
         * log(app.versionName);
         * @example Source code summary (zh-CN: 源代码摘要)
         * context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName
         */
        versionName: string;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * context.packageName + ".fileprovider"
         */
        fileProviderAuthority: 'org.autojs.autojs.fileprovider' | string | null;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * app.intent = function (i) {
         *     let intent = new android.content.Intent();
         *     if (i.className && i.packageName) {
         *         intent.setClassName(i.packageName, i.className);
         *     }
         *     if (i.extras) {
         *         for (let key in i.extras) {
         *             intent.putExtra(key, i.extras[key]);
         *         }
         *     }
         *     if (i.category) {
         *         if (i.category instanceof Array) {
         *             for (let j = 0; i < i.category.length; j++) {
         *                 intent.addCategory(i.category[j]);
         *             }
         *         } else {
         *             intent.addCategory(i.category);
         *         }
         *     }
         *     if (i.action) {
         *         if (i.action.indexOf('.') === -1) {
         *             i.action = "android.intent.action." + i.action;
         *         }
         *         intent.setAction(i.action);
         *     }
         *     if (i.flags) {
         *         let flags = 0;
         *         if (Array.isArray(i.flags)) {
         *             for (let j = 0; j < i.flags.length; j++) {
         *                 flags |= parseIntentFlag(i.flags[j]);
         *             }
         *         } else {
         *             flags = parseIntentFlag(i.flags);
         *         }
         *         intent.setFlags(flags);
         *     }
         *     if (i.type) {
         *         if (i.data) {
         *             intent.setDataAndType(app.parseUri(i.data), i.type);
         *         } else {
         *             intent.setType(i.type);
         *         }
         *     } else if (i.data) {
         *         intent.setData(android.net.Uri.parse(i.data));
         *     }
         *     return intent;
         *
         *     function parseIntentFlag(flag) {
         *         if (typeof (flag) == 'string') {
         *             return android.content.Intent["FLAG_" + flag.toUpperCase()];
         *         }
         *         return flag;
         *     }
         * };
         */
        intent(o: Intent.Common | Intent): Intent;

        /**
         * @example
         * let android_intent = app.intent({
         *     action: "VIEW",
         *     packageName: "com.example.test",
         *     className: "com.example.test.mainActivity",
         * });
         * app.startActivity(android_intent);
         * @example
         * let intent = {
         *     action: "VIEW",
         *     packageName: "com.example.test",
         *     className: "com.example.test.mainActivity",
         * };
         * app.startActivity(intent);
         * @example
         * app.startActivity("console");
         * @example
         * app.startActivity({
         *     action: "SEND",
         *     type: "text/plain",
         *     data: "file:///sdcard/1.txt"
         * });
         * @example Source code summary (zh-CN: 源代码摘要)
         * app.startActivity = function (i) {
         *     if (typeof (i) == "string") {
         *         if (runtime.getProperty("class." + i)) {
         *             context.startActivity(new Intent(context, runtime.getProperty("class." + i))
         *                 .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
         *             return;
         *         } else {
         *             throw new Error("class " + i + " not found");
         *         }
         *     }
         *     if (i instanceof android.content.Intent) {
         *         context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
         *         return;
         *     }
         *     if (i && i.root) {
         *         shell("am start " + app.intentToShell(i), true);
         *     } else {
         *         context.startActivity(app.intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
         *     }
         * };
         */
        startActivity(o: Intent.CommonWithRoot | Intent.ShortForm.Activity | Intent | URI): void;

        /**
         * @example
         * app.sendBroadcast("inspect_layout_bounds");
         * @example Source code summary (zh-CN: 源代码摘要)
         * app.sendBroadcast = function (i) {
         *     if (typeof (i) == "string") {
         *         if (runtime.getProperty("broadcast." + i)) {
         *             app.sendLocalBroadcastSync(app.intent({action: runtime.getProperty("broadcast." + i)}));
         *         }
         *     }
         *     if (i && i.root) {
         *         shell("am broadcast " + app.intentToShell(i), true);
         *     } else {
         *         context.sendBroadcast(app.intent(i));
         *     }
         * };
         * @see org.autojs.autojs.runtime.api.AppUtils.sendLocalBroadcastSync
         * @see androidx.localbroadcastmanager.content.LocalBroadcastManager.sendBroadcast
         */
        sendBroadcast(i: Intent.CommonWithRoot | Intent.ShortForm.Broadcast): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * app.startService = function (i) {
         *     if (i && i.root) {
         *         shell("am startservice " + app.intentToShell(i), true);
         *     } else {
         *         context.startService(app.intent(i));
         *     }
         * };
         * @see android.content.Context.startService
         */
        startService(i: Intent.CommonWithRoot): void;

        /**
         * @example
         * app.sendEmail({
         *     email: ['10086@qq.com', "10001@qq.com"],
         *     subject: "这是一个邮件标题",
         *     text: "这是邮件正文",
         * });
         * @example Source code summary (zh-CN: 源代码摘要)
         * app.sendEmail = function (options) {
         *     options = options || {};
         *     let i = new Intent(Intent.ACTION_SEND);
         *     if (options.email) {
         *         i.putExtra(Intent.EXTRA_EMAIL, toArray(options.email));
         *     }
         *     if (options.cc) {
         *         i.putExtra(Intent.EXTRA_CC, toArray(options.cc));
         *     }
         *     if (options.bcc) {
         *         i.putExtra(Intent.EXTRA_BCC, toArray(options.bcc));
         *     }
         *     if (options.subject) {
         *         i.putExtra(Intent.EXTRA_SUBJECT, options.subject);
         *     }
         *     if (options.text) {
         *         i.putExtra(Intent.EXTRA_TEXT, options.text);
         *     }
         *     if (options.attachment) {
         *         i.putExtra(Intent.EXTRA_STREAM, app.parseUri(options.attachment));
         *     }
         *     i.setType('message/rfc822');
         *     context.startActivity(Intent.createChooser(i, "发送邮件").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
         *
         *     function toArray(arg) {
         *         if (typeof (arg) == 'string') {
         *             arg = [arg];
         *         }
         *         let arr = util.java.array("string", arg.length);
         *         for (let i = 0; i < arg.length; i++) {
         *             arr[i] = arg;
         *         }
         *         return arr;
         *     }
         * };
         */
        sendEmail(options?: Intent.Email): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * app.parseUri = function (uri) {
         *     if (uri.startsWith('file://')) {
         *         return app.getUriForFile(uri);
         *     }
         *     return android.net.Uri.parse(uri);
         * };
         * @see app.getUriForFile
         * @see android.net.Uri.parse
         */
        parseUri(uri: string | URI): android.net.Uri | null;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * app.getUriForFile = function (path) {
         *     if (path.startsWith('file://')) {
         *         path = path.substring(7);
         *     }
         *     let file = new java.io.File(files.path(path));
         *     if (app.fileProviderAuthority == null) {
         *         return android.net.Uri.fromFile(file);
         *     }
         *     return Packages['androidx'].core.content.FileProvider.getUriForFile(context,
         *         app.fileProviderAuthority, file);
         * };
         * @see android.net.Uri.fromFile
         * @see org.autojs.autojs.runtime.api.AppUtils.getFileProviderAuthority
         * @see androidx.core.content.FileProvider.getUriForFile
         */
        getUriForFile(path: string): string;

        getAppByAlias(alias: App.Alias | string): App;

        /**
         * @example
         * app.launch("com.example.test");
         * @see app.launchPackage
         */
        launch(app: App | App.Alias | App.PackageName): boolean;

        /**
         * @example
         * shell("am start " + app.intentToShell({
         *     packageName: "org.autojs.autojs",
         *     className: "org.autojs.autojs.ui.settings.SettingsActivity_"
         * }), true);
         * @example Source code summary (zh-CN: 源代码摘要)
         * app.intentToShell = function (i) {
         *     let cmd = "";
         *     if (i.className && i.packageName) {
         *         addOption("n", i.packageName + "/" + i.className);
         *     }
         *     if (i.extras) {
         *         for (let key in i.extras) {
         *             let value = i.extras[key];
         *             if (typeof (value) == 'string') {
         *                 addOption("-es", quoteStr(key) + '\x20' + quoteStr(value), false);
         *             } else if (Array.isArray(value)) {
         *                 if (value.length === 0) {
         *                     throw new Error('Empty array: ' + key);
         *                 }
         *                 let e = value[0];
         *                 if (typeof (e) == 'string') {
         *                     cmd += ' --esa ' + quoteStr(key) + '\x20';
         *                     for (let str of value) {
         *                         cmd += quoteStr(str) + ',';
         *                     }
         *                     cmd = cmd.substring(0, cmd.length - 1);
         *                 } else {
         *                     addOption('-e' + typeChar(e) + 'a', quoteStr(key) + '\x20' + value, false);
         *                 }
         *             } else {
         *                 addOption('-e' + typeChar(value), quoteStr(key) + '\x20' + value, false);
         *             }
         *         }
         *     }
         *     if (i.category) {
         *         if (i.category instanceof Array) {
         *             for (let j = 0; i < i.category.length; j++) {
         *                 addOption('c', i.category[j], false);
         *             }
         *         } else {
         *             addOption('c', i.category, false);
         *         }
         *     }
         *     if (i.action) {
         *         if (i.action.indexOf('.') === -1) {
         *             i.action = "android.intent.action." + i.action;
         *         }
         *         addOption('a', i.action);
         *     }
         *     if (i.flags) {
         *         let flags = 0;
         *         if (Array.isArray(i.flags)) {
         *             for (let j = 0; j < i.flags.length; j++) {
         *                 flags |= parseIntentFlag(i.flags[j]);
         *             }
         *         } else {
         *             flags = parseIntentFlag(i.flags);
         *         }
         *         addOption('f', flags, false);
         *     }
         *     if (i.type) {
         *         addOption('t', i.type, false);
         *     }
         *     if (i.data) {
         *         addOption('d', i.data, false);
         *     }
         *     return cmd;
         *
         *     function parseIntentFlag(flag) {
         *         if (typeof (flag) == 'string') {
         *             return android.content.Intent["FLAG_" + flag.toUpperCase()];
         *         }
         *         return flag;
         *     }
         *     function quoteStr(str) {
         *         return "'" + str.replace("'", "\\'") + "'";
         *     }
         *     function isInt(value) {
         *         return Number.isInteger(value) && value <= java.lang.Integer.MAX_VALUE && value >= java.lang.Integer.MIN_VALUE;
         *     }
         *     function typeChar(value) {
         *         if (typeof (value) == 'boolean') {
         *             return 'z';
         *         }
         *         if (typeof (value) == 'number') {
         *             if (Number.isInteger(value)) {
         *                 if (isInt(value)) {
         *                     return 'i';
         *                 } else {
         *                     return 'l';
         *                 }
         *             } else {
         *                 return 'f';
         *             }
         *         }
         *         throw new TypeError("unknown type: " + value);
         *     }
         *     function addOption(option, param, quote) {
         *         if (quote === undefined || quote === true) {
         *             param = quoteStr(param);
         *         }
         *         cmd += " -" + option + " " + param;
         *     }
         * };
         */
        intentToShell(i: Intent.Common): string;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * if (!url.startsWith("http://") && !url.startsWith("https://")) {
         *     url = "http://" + url;
         * }
         * mContext.startActivity(new Intent(Intent.ACTION_VIEW)
         *     .setData(Uri.parse(url))
         *     .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
         * );
         * @see android.net.Uri
         * @see android.content.Intent
         */
        openUrl(url: string | URI): void;

        /**
         * @example
         * app.launchApp("Auto.js");
         * @example Source code summary (zh-CN: 源代码摘要)
         * let pkg = getPackageName(appName);
         * if (pkg === null) {
         *     return false;
         * }
         * return launchPackage(pkg);
         * @see app.getPackageName
         * @see app.launchPackage
         * @see org.autojs.autojs.runtime.api.AppUtils.launchApp
         */
        launchApp(app: App | App.Alias | App.AppName): boolean;

        /**
         * @example
         * app.uninstall("com.tencent.mobileqq");
         * @example Source code summary (zh-CN: 源代码摘要)
         * let intent = new Intent(
         *     Intent.ACTION_DELETE, Uri.parse("package:" + packageName)
         * ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
         * mContext.startActivity(intent);
         * @see android.content.Intent
         */
        uninstall(app: App | App.Alias | App.PackageName): void;

        /**
         * @example
         * app.getAppName("com.tencent.mobileqq"); // "QQ"
         * @example Source code summary (zh-CN: 源代码摘要)
         * let packageManager = context.getPackageManager();
         * try {
         *     let applicationInfo = packageManager.getApplicationInfo(packageName, 0);
         *     let appName = packageManager.getApplicationLabel(applicationInfo);
         *     return appName == null ? null : appName.toString();
         * } catch (e) {
         *     return null;
         * }
         */
        getAppName(app: App | App.Alias | App.PackageName): string;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * Log.d("App", "getCurrentActivity: " + mCurrentActivity.get());
         * return mCurrentActivity.get();
         * @see android.util.Log
         */
        getCurrentActivity(): android.app.Activity;

        /**
         * @example
         * app.editFile("/sdcard/1.txt/);
         * @example Source code summary (zh-CN: 源代码摘要)
         * if (path == null) throw new NullPointerException("path == null");
         * IntentUtil.editFile(mContext, path, mFileProviderAuthority);
         * @see org.autojs.autojs.util.IntentUtils
         * @see java.lang.NullPointerException
         */
        editFile(path: string): boolean;

        /**
         * @example
         * app.viewFile("/sdcard/1.txt");
         * @example Source code summary (zh-CN: 源代码摘要)
         * if (path == null) throw new NullPointerException("path == null");
         * IntentUtil.viewFile(mContext, path, mFileProviderAuthority);
         * @see org.autojs.autojs.util.IntentUtils
         * @see java.lang.NullPointerException
         */
        viewFile(path: string): boolean;

        /**
         * Use with caution as it might be time-consuming
         * @example
         * app.getPackageName("QQ"); // "com.tencent.mobileqq"
         * @example Source code summary (zh-CN: 源代码摘要)
         * let packageManager = context.getPackageManager();
         * let installedApplications = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
         * for (let applicationInfo in installedApplications) {
         *     if (packageManager.getApplicationLabel(applicationInfo).toString().equals(appName)) {
         *         return applicationInfo.packageName;
         *     }
         * }
         * return null;
         * @see android.content.pm.PackageManager
         */
        getPackageName(app: App | App.Alias | App.AppName): string;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * mCurrentActivity = new WeakReference<>(currentActivity);
         * Log.d("App", "setCurrentActivity: " + currentActivity);
         * @see android.util.Log
         * @see java.lang.ref.WeakReference
         */
        setCurrentActivity(currentActivity: android.app.Activity): void;

        /**
         * @example
         * app.launchPackage("com.example.test");
         * @example Source code summary (zh-CN: 源代码摘要)
         * try {
         *     let packageManager = context.getPackageManager();
         *     let intent = packageManager
         *         .getLaunchIntentForPackage(packageName)
         *         .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
         *     mContext.startActivity(intent);
         * } catch (e) {
           return false;
       }
         * @see org.autojs.autojs.runtime.api.AppUtils.launchPackage
         */
        launchPackage(app: App | App.Alias | App.PackageName): boolean;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * LocalBroadcastManager.getInstance(mContext).sendBroadcastSync(intent);
         * @see androidx.localbroadcastmanager.content.LocalBroadcastManager
         */
        sendLocalBroadcastSync(intent: Intent);

        /**
         * @deprecated
         * @replaceWith launchSettings
         * @example Source code summary (zh-CN: 源代码摘要)
         * return IntentUtil.goToAppDetailSettings(context, packageName);
         * @see org.autojs.autojs.util.IntentUtils
         */
        openAppSetting(app: App | App.Alias | App.PackageName): boolean;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * return IntentUtil.goToAppDetailSettings(context, packageName);
         * @see org.autojs.autojs.util.IntentUtils
         */
        launchSettings(app: App | App.Alias | App.PackageName): boolean;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * return mFileProviderAuthority;
         */
        getFileProviderAuthority(): string;

    }

}

declare namespace App {

    type PackageName = string;
    type AppName = string;

    // noinspection SpellCheckingInspection
    type Alias = 'accuweather' | 'adm' | 'alipay' | 'amap' | 'appops' | 'aquamail' | 'autojs' | 'autojs6' | 'autojspro'
        | 'baidumap' | 'bilibili' | 'brevent' | 'calendar' | 'chrome' | 'coolapk' | 'dianping' | 'digical' | 'drive' | 'es'
        | 'eudic' | 'excel' | 'firefox' | 'fx' | 'geometricweather' | 'httpcanary' | 'idlefish' | 'idm+' | 'jd' | 'keep'
        | 'keepnotes' | 'magisk' | 'meituan' | 'mt' | 'mxpro' | 'onedrive' | 'packetcapture' | 'parallelspace' | 'powerpoint'
        | 'pulsarplus' | 'pureweather' | 'qq' | 'qqmusic' | 'sdmaid' | 'shizuku' | 'stopapp' | 'taobao' | 'trainnote' | 'twitter'
        | 'unionpay' | 'via' | 'vysor' | 'wechat' | 'word' | 'zhihu';

}

declare namespace Intent {

    namespace ShortForm {

        type Activity = 'settings' | 'preferences' | 'pref' | 'console' | 'log' | 'homepage' | 'home' | 'about' | 'build' | 'documentation' | 'doc' | 'docs';

        type Broadcast = 'inspect_layout_bounds' | 'layout_bounds' | 'bounds' | 'inspect_layout_hierarchy' | 'layout_hierarchy' | 'hierarchy';

    }

    /**
     * @example with protocol
     * "http://www.msn.com"
     * "https://www.msn.com"
     * "ftp://someFtpAddress"
     *
     * @example without protocol
     * "www.msn.com"
     * "msn.com"
     */
    type UriString = string;

    interface URI {
        src: Alipay.URI.StartApp | string;
        query?: {
            [prop: string]: any;
            url?: string | URI;
        } | Alipay.JSBridge.PushWindowOptions,
        exclude?: string | string[];
    }

    interface Common {
        url?: string | URI;
        className?: string;
        /**
         * Alias for packageName
         * @see packageName
         */
        package?: App.Alias | string;
        packageName?: App.Alias | string;
        extras?: { [name: string]: android.os.Bundle | any[] | any };
        category?: any[] | any;
        action?: string;
        flags?: any[] | any;
        type?: string;
        data?: string;
    }

    interface CommonWithRoot extends Common {
        root?: boolean;
    }

    interface Email {
        email?: string | string[];
        cc?: string | string[];
        bcc?: string | string[];
        subject?: string;
        text?: string;
        attachment?: string;
    }

}

/**
 * @see app.launch
 */
declare function launch(app: App | App.Alias | App.PackageName): boolean;

/**
 * @see app.launchApp
 */
declare function launchApp(app: App | App.Alias | App.AppName): boolean;

/**
 * @see app.launchPackage
 */
declare function launchPackage(app: App | App.Alias | App.PackageName): boolean;

/**
 * @see app.getAppName
 */
declare function getAppName(app: App | App.Alias | App.PackageName): string;

/**
 * @see app.getPackageName
 */
declare function getPackageName(app: App | App.Alias | App.AppName): string;

/**
 * @deprecated
 * @replaceWith launchSettings
 * @see app.openAppSetting
 */
declare function openAppSetting(app: App | App.Alias | App.PackageName): boolean;

/**
 * @see app.launchSettings
 */
declare function launchSettings(app: App | App.Alias | App.PackageName): boolean;