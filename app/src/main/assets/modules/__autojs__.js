// noinspection JSUnusedGlobalSymbols

/* Overwritten protection. */

let { util } = global;

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Autojs}
 */
module.exports = function (scriptRuntime, scope) {
    const Manifest = android.Manifest;
    const BuildConfig = org.autojs.autojs6.BuildConfig;
    const PackageManager = android.content.pm.PackageManager;
    const RootUtils = org.autojs.autojs.util.RootUtils;
    const RootMode = RootUtils.RootMode;
    const Settings = android.provider.Settings;
    const System = Settings.System;

    let _ = {
        Autojs: (/* @IIFE */ () => {
            /**
             * @implements Internal.Autojs
             */
            const Autojs = function () {
                /* Empty body. */
            };

            Autojs.prototype = {
                constructor: Autojs,
                versionCode: BuildConfig.VERSION_CODE,
                versionName: BuildConfig.VERSION_NAME,
                versionDate: BuildConfig.VERSION_DATE,
                version: {
                    code: BuildConfig.VERSION_CODE,
                    name: BuildConfig.VERSION_NAME,
                    date: BuildConfig.VERSION_DATE,
                    isHigherThan(otherVersion) {
                        return new Version(this.name).isHigherThan(otherVersion);
                    },
                    isLowerThan(otherVersion) {
                        return new Version(this.name).isLowerThan(otherVersion);
                    },
                    isEqual(otherVersion) {
                        return new Version(this.name).isEqual(otherVersion);
                    },
                    isAtLeast(otherVersion, ignoreSuffix) {
                        if (typeof ignoreSuffix === 'undefined') {
                            return new Version(this.name).isAtLeast(otherVersion);
                        }
                        return new Version(this.name).isAtLeast(otherVersion, Boolean(ignoreSuffix));
                    },
                },
                R: global.R,
                name: context.getString(R.strings.app_name),
                get rotation() {
                    return ScreenMetrics.getRotation();
                },
                get orientation() {
                    return ScreenMetrics.getOrientation();
                },
                isScreenPortrait() {
                    return ScreenMetrics.isScreenPortrait();
                },
                isScreenLandscape() {
                    return ScreenMetrics.isScreenLandscape();
                },
                isRootAvailable() {
                    return RootUtils.isRootAvailable();
                },
                getRootMode() {
                    return RootUtils.getRootMode();
                },
                setRootMode(mode, isWriteIntoPreference) {
                    let isWriteIntoPref = (/* @IIFE */ () => {
                        if (typeof isWriteIntoPreference === 'undefined') {
                            return false;
                        }
                        if (typeof isWriteIntoPreference === 'boolean') {
                            return isWriteIntoPreference;
                        }
                        return util.checkStringParam(isWriteIntoPreference, 'write_into_pref');
                    })();
                    if (mode === 1 || mode === true || util.checkStringParam(mode, 'root')) {
                        RootUtils.setRootMode(RootMode.FORCE_ROOT, isWriteIntoPref);
                    } else if (mode === 0 || mode === false || util.checkStringParam(mode, 'non-root')) {
                        RootUtils.setRootMode(RootMode.FORCE_NON_ROOT, isWriteIntoPref);
                    } else if (mode === -1 || util.checkStringParam(mode, 'auto')) {
                        RootUtils.setRootMode(RootMode.AUTO_DETECT, isWriteIntoPref);
                    } else {
                        let errPrefix = `Unknown mode (${mode}) for setRootMode()`;
                        if (!mode) {
                            throw Error(`${errPrefix}. Did you mean to use false or 0 or 'non-root' to forcibly set non-root mode?`);
                        } else {
                            throw Error(`${errPrefix}. Did you mean to use true or 1 or 'root' to forcibly set root mode?`);
                        }
                    }
                },
                canModifySystemSettings() {
                    return System.canWrite(context);
                },
                canWriteSecureSettings() {
                    return context.checkCallingOrSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) === PackageManager.PERMISSION_GRANTED;
                },
                canDisplayOverOtherApps() {
                    return Settings.canDrawOverlays(context);
                },
                getLanguage() {
                    return org.autojs.autojs.pref.Language.getPrefLanguage().getLocale();
                },
                getLanguageTag() {
                    return this.getLanguage().toLanguageTag();
                },
                get themeColor() {
                    return org.autojs.autojs.theme.ThemeColorManager.getCurrentThemeColor();
                },
            };

            return Autojs;
        })(),
    };

    /**
     * @type {Internal.Autojs}
     */
    const autojs = new _.Autojs();

    return autojs;
};