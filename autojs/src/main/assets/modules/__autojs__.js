// noinspection JSUnusedGlobalSymbols

/* Here, importClass() is not recommended for intelligent code completion in IDE like WebStorm. */
/* The same is true of destructuring assignment syntax (like `let {Uri} = android.net`). */

const Manifest = android.Manifest;
const BuildConfig = org.autojs.autojs6.BuildConfig;
const PackageManager = android.content.pm.PackageManager;
const FloatingPermission = com.stardust.autojs.util.FloatingPermission;

const RootTool = org.autojs.autojs.tool.RootTool;
const RootMode = RootTool.RootMode;

const Settings = android.provider.Settings;
const System = Settings.System;

let _ = {
    init(__runtime__, scope) {
        this.runtime = __runtime__;
        this.scope = scope;
        this.autojs = {};
    },
    getModule() {
        return this.autojs;
    },
    selfAugment() {
        Object.assign(this.autojs, {
            /**
             * @example
             * autojs.versionCode; // e.g. 523
             */
            versionCode: BuildConfig.VERSION_CODE,
            /**
             * @example
             * autojs.versionCode; // e.g. '6.0.2'
             */
            versionName: BuildConfig.VERSION_NAME,
            /**
             * @example
             * autojs.versionDate; // e.g. 'May 23, 2011'
             */
            versionDate: BuildConfig.VERSION_DATE,
            /**
             * @see Internal.Autojs.setRootMode
             */
            setRootMode(mode, isWriteIntoPreference) {
                let isWriteIntoPref = ( /* @IIFE */ () => {
                    if (typeof isWriteIntoPreference === 'undefined') {
                        return false;
                    }
                    if (typeof isWriteIntoPreference === 'boolean') {
                        return isWriteIntoPreference;
                    }
                    return util.checkStringParam(isWriteIntoPreference, 'write_into_pref');
                })();
                if (mode === 1 || mode === true || util.checkStringParam(mode, 'root')) {
                    RootTool.setRootMode(RootMode.FORCE_ROOT, isWriteIntoPref);
                } else if (mode === 0 || mode === false || util.checkStringParam(mode, 'non-root')) {
                    RootTool.setRootMode(RootMode.FORCE_NON_ROOT, isWriteIntoPref);
                } else if (mode === -1 || util.checkStringParam(mode, 'auto')) {
                    RootTool.setRootMode(RootMode.AUTO_DETECT, isWriteIntoPref);
                } else {
                    let errPrefix = `Unknown mode (${mode}) for setRootMode()`;
                    if (Boolean(mode) === false) {
                        throw Error(`${errPrefix}. Did you mean to use false or 0 or 'non-root' to forcibly set non-root mode?`);
                    }
                    if (Boolean(mode) === true) {
                        throw Error(`${errPrefix}. Did you mean to use true or 1 or 'root' to forcibly set root mode?`);
                    }
                    throw Error(errPrefix);
                }
            },
            getRootMode() {
                return RootTool.getRootMode();
            },
            isRootAvailable() {
                return RootTool.isRootAvailable();
            },
            canModifySystemSettings() {
                return System.canWrite(context);
            },
            canWriteSecureSettings() {
                return context.checkCallingOrSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) === PackageManager.PERMISSION_GRANTED;
            },
            canDisplayOverOtherApps() {
                return FloatingPermission.canDrawOverlays(context);
            },
        });
    },
};

let $ = {
    getModule(__runtime__, scope) {
        _.init(__runtime__, scope);

        _.selfAugment();

        return _.getModule();
    },
};

/**
 * @param {com.stardust.autojs.runtime.ScriptRuntime} __runtime__
 * @param {org.mozilla.javascript.Scriptable} scope
 * @return {Internal.Autojs}
 */
module.exports = function (__runtime__, scope) {
    return $.getModule(__runtime__, scope);
};