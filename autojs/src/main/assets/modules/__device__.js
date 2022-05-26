let _ = {
    init(__runtime__, scope) {
        this.runtime = __runtime__;
        this.scope = scope;

        /**
         * @type {com.stardust.autojs.runtime.api.Device}
         */
        this.rtDevice = __runtime__.device;
        this.device = Object.create(this.rtDevice);
    },
    getModule() {
        return this.device;
    },
    selfAugment() {
        // @Caution by SuperMonster003 on May 10, 2022.
        //  ! Object.assign will cause Error:
        //  ! Java method "xxx" cannot be assigned to.
        Object.defineProperties(this.device, {
            vibrate: {
                value() {
                    let isMorseString = () => typeof arguments[0] === 'string';

                    if (isMorseString()) {
                        util.morseCode.vibrate.apply(util.morseCode, arguments);
                    } else {
                        _.rtDevice.vibrate.apply(_.rtDevice, arguments);
                    }
                },
                enumerable: true,
            },
            getIMEI: {
                value(suppressWarnings) {
                    try {
                        return _.rtDevice.getIMEI();
                    } catch (e) {
                        if (!suppressWarnings && e.javaException instanceof java.lang.SecurityException) {
                            console.trace(`Can't get IMEI without "read phone state" permission`, 'warn');
                        }
                    }
                    return null;
                },
                enumerable: true,
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
 * @return {Internal.Device}
 */
module.exports = function (__runtime__, scope) {
    return $.getModule(__runtime__, scope);
};