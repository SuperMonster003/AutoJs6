// noinspection UnnecessaryLocalVariableJS,JSUnusedLocalSymbols

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Toast}
 */
module.exports = function (scriptRuntime, scope) {
    let rtToast = scriptRuntime.toast;
    let _ = {
        ToastCtor: (/* @IIFE */ () => {
            /**
             * @implements Internal.Toast
             */
            const Ctor = function () {
                /** @global */
                const instance = function (msg, isLong, isForcible) {
                    let $ = {
                        badge: {
                            long: /^l(ong)?$/i,
                            short: /^s(hort)?$/i,
                            forcible: /^f(orcible)?$/i,
                        },
                        init() {
                            this.message = isNullish(msg) ? '' : String(msg);
                            this.isForcible = this.parseIsForcible(isForcible);
                            this.isLong = this.parseIsLong(isLong);
                        },
                        parseIsLong(isLong) {
                            if (typeof isLong === 'boolean') {
                                return isLong;
                            }
                            if (typeof isLong === 'number') {
                                return Boolean(isLong);
                            }
                            if (typeof isLong === 'string') {
                                if (this.badge.long.test(isLong)) {
                                    return true;
                                }
                                if (this.badge.short.test(isLong)) {
                                    return false;
                                }
                                if (this.badge.forcible.test(isLong)) {
                                    this.isForcible = true;
                                    return false;
                                }
                                throw Error(`Invalid param: {name: isLong, value: ${isLong}, type: ${species(isLong)}.`);
                            }
                            return false;
                        },
                        parseIsForcible(isForcible) {
                            if (typeof isForcible === 'boolean') {
                                return isForcible;
                            }
                            if (typeof isForcible === 'number') {
                                return Boolean(isForcible);
                            }
                            if (typeof isForcible === 'string') {
                                if (this.badge.forcible.test(isForcible)) {
                                    return true;
                                }
                                throw Error(`Invalid param: {name: isForcible, value: ${isForcible}, type: ${species(isForcible)}.`);
                            }
                            return false;
                        },
                        makeToast() {
                            rtToast.makeToast(this.message, this.isLong, this.isForcible);
                        },
                    };

                    $.init();
                    $.makeToast();
                };

                return Object.assign(instance, Ctor.prototype);
            };

            Ctor.prototype = {
                constructor: Ctor,
                dismissAll: () => rtToast.dismissAll(),
            };

            return Ctor;
        })(),
    };

    /**
     * @type {Internal.Toast}
     */
    const toast = new _.ToastCtor();

    return toast;
};