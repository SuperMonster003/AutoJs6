// Type definitions for AutoJs6 internal module autojs
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 4.5.4
//
// Last modified: May 10, 2022

/// <reference path="../index.d.ts" />

/**
 * @Source %AutoJs6Assets%/modules/__autojs__.js
 */

declare namespace Internal {

    interface Autojs extends AutoJsBridge, org.autojs.autojs.runtime.api.Events {

        /**
         * @example
         * autojs.versionCode; // e.g. 523
         */
        versionCode: string;

        /**
         * @example
         * autojs.versionCode; // e.g. '6.0.2'
         */
        versionName: string;

        /**
         * @example
         * autojs.versionDate; // e.g. 'May 23, 2011'
         */
        versionDate: string;

        /**
         * @example
         * autojs.name; // e.g. 'AutoJs6'
         */
        name: string;

        packageName: string;

        R: org.autojs.autojs6.R;

        version: AutojsVersion;

        /**
         * @example
         * // 1 or true or 'root' (force set root mode)
         * // 0 or false or 'non-root' (force set non-root mode)
         * // -1 or 'auto' (auto detect)
         * autojs.setRootMode('non-root');
         * console.log(autojs.getRootMode()); // non-root mode information
         * console.log(autojs.isRootAvailable()); // false (even if device has root access)
         */
        setRootMode(mode: number | boolean | 'auto' | 'root' | 'non-root', isWriteIntoPreference?: boolean | 'write_into_pref'): void;

        getRootMode(): org.autojs.autojs.util.RootUtils.RootMode;

        isRootAvailable(): boolean;

        canModifySystemSettings(): boolean;

        canWriteSecureSettings(): boolean;

        canDisplayOverOtherApps(): boolean;

        getLanguage(): java.util.Locale;

        getLanguageTag(): string;

        get themeColor(): org.autojs.autojs.theme.ThemeColor;

    }

    interface AutojsVersion {

        /**
         * @example
         * autojs.version.code; // e.g. 523
         */
        code: string;

        /**
         * @example
         * autojs.version.code; // e.g. '6.0.2'
         */
        name: string;

        /**
         * @example
         * autojs.version.date; // e.g. 'May 23, 2011'
         */
        date: string;

        /**
         * @example
         * autojs.version.name; // e.g. "6.2.0"
         * autojs.version.isHigherThan(6); // e.g. true
         * autojs.version.isHigherThan(7); // e.g. false
         * autojs.version.isHigherThan('4.1.0'); // e.g. true
         * autojs.version.isHigherThan('4.1.1 alpha'); // e.g. true
         * autojs.version.isHigherThan('6.2.0 alpha2'); // e.g. true
         * autojs.version.isHigherThan('8.9.6 beta'); // e.g. false
         */
        isHigherThan(otherVersion: string | number | Version): boolean;

        isLowerThan(otherVersion: string | number | Version): boolean;

        isEqual(otherVersion: string | number | Version): boolean;

        isAtLeast(otherVersion: string | number | Version, ignoreSuffix?: boolean): boolean;

    }

    interface AutoJsBridge {

        requestHandlers: {};

        onEventInternal(event: any, args: any): void;

        send(event: any, ...args: any[]): void;

        invoke(channel: any, ...args: any[]): Promise<unknown>;

        handle(channel: any, handler: any): this;

        removeHandler(channel: any): void;

        handleRequest(request: any): void;

        sendResponse(request: any, result: any, error: any): void;

    }

}