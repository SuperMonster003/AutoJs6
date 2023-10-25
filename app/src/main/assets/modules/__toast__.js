// noinspection JSUnusedLocalSymbols,UnnecessaryLocalVariableJS

/**
 * @param {org.autojs.autojs.runtime.ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Toast}
 */
module.exports = function (scriptRuntime, scope) {

    const ScriptToast = org.autojs.autojs.runtime.api.ScriptToast;

    let _ = {
        uiHandler: runtime.getUiHandler(),
        ToastCtor: (/* @IIFE */ () => {
            /**
             * @implements Internal.Toast
             */
            const ToastCtor = function () {
                // @Caution by SuperMonster003 on Oct 11, 2022.
                //  ! android.widget.Toast.makeText() doesn't work well on Android API Level 28 (Android 9) [P].
                //  ! There hasn't been a solution for this so far.
                //  ! Tested devices:
                //  ! 1. SONY XPERIA XZ1 Compact (G8441)
                //  ! 2. Android Studio AVD (Android 9.0 x86)
                /** @global */
                const toast = function (msg, isLong, isForcible) {
                    let $ = {
                        badge: {
                            long: /^l(ong)?$/i,
                            short: /^s(hort)?$/i,
                            forcible: /^f(orcible)?$/i,
                        },
                        toast() {
                            this.init(arguments);
                            this.show();
                        },
                        init() {
                            this.message = isNullish(msg) ? '' : String(msg);
                            this.isLong = this.parseIsLong(isLong);
                            this.isForcible = this.parseIsForcible(isForcible);
                        },
                        parseIsLong(o) {
                            let def = typeof this.isLong === 'boolean' ? this.isLong : false;
                            if (typeof o === 'boolean') {
                                return o;
                            }
                            if (typeof o === 'number') {
                                return Boolean(o);
                            }
                            if (typeof o === 'string') {
                                if (this.badge.long.test(o)) {
                                    return true;
                                }
                                if (this.badge.short.test(o)) {
                                    return false;
                                }
                                if (this.badge.forcible.test(o)) {
                                    this.isForcible = true;
                                    return def;
                                }
                                throw Error(`Invalid param: {name: isLong, value: ${o}, type: ${species(o)}.`);
                            }
                            return def;
                        },
                        parseIsForcible(o) {
                            let def = typeof this.isForcible === 'boolean' ? this.isForcible : false;
                            if (typeof o === 'boolean') {
                                return o;
                            }
                            if (typeof o === 'number') {
                                return Boolean(o);
                            }
                            if (typeof o === 'string') {
                                if (this.badge.forcible.test(o)) {
                                    return true;
                                }
                                if (this.badge.long.test(o)) {
                                    this.isLong = true;
                                    return def;
                                }
                                if (this.badge.short.test(o)) {
                                    this.isLong = false;
                                    return def;
                                }
                                throw Error(`Invalid param: {name: isForcible, value: ${o}, type: ${species(o)}.`);
                            }
                            return def;
                        },
                        show() {
                            if ($.isForcible) {
                                ToastCtor.prototype.dismissAll();
                            }
                            scriptRuntime.uiHandler.toast($.message, $.isLong);
                        },
                    };

                    $.toast();
                };

                return Object.assign(toast, ToastCtor.prototype);
            };

            ToastCtor.prototype = {
                constructor: ToastCtor,
                dismissAll() {
                    runtime.uiHandler.dismissAllToasts();
                },
            };

            return ToastCtor;
        })(),
    };

    /**
     * @type {Internal.Toast}
     */
    const toast = new _.ToastCtor();

    return toast;
};