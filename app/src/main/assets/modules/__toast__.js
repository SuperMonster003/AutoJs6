/**
 * @param {org.autojs.autojs.runtime.ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Toast}
 */
module.exports = function (scriptRuntime, scope) {
    let _ = {
        uiHandler: runtime.getUiHandler(),
        toasts: {
            /**
             * @type {Set<android.widget.Toast>}
             */
            pool: new Set(),
            lock: new ReentrantLock(),
            add(t) {
                if (t instanceof Toast) {
                    this.lock.lock();
                    this.pool.add(t);
                    this.addCallback(t);
                    this.lock.unlock();
                }
            },
            /**
             * @param {android.widget.Toast} t
             */
            addCallback(t) {
                _.uiHandler.postDelayed(new java.lang.Runnable({
                    run: () => _.toasts.remove(t),
                }), this.getDuration(t) + 1e3 /* As toast may show with some delay. */);
            },
            remove(t) {
                if (this.pool.has(t)) {
                    this.lock.lock();
                    this.pool.delete(t);
                    this.lock.unlock();
                }
            },
            dismissAll() {
                if (this.pool.size > 0) {
                    this.lock.lock();
                    this.pool.forEach((t) => t.cancel());
                    this.pool.clear();
                    this.lock.unlock();
                }
            },
            getDuration(t) {
                let du = {
                    SHORT_DELAY: 2e3,
                    LONG_DELAY: 3.5e3,
                };
                switch (t.getDuration()) {
                    case Toast.LENGTH_SHORT:
                        return du.SHORT_DELAY;
                    case Toast.LENGTH_LONG:
                        return du.LONG_DELAY;
                    default:
                        return Math.max.apply(null, Object.values(du));
                }
            },
        },
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
                        rex: {
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
                                if (this.rex.long.test(isLong)) {
                                    return true;
                                }
                                if (this.rex.short.test(isLong)) {
                                    return false;
                                }
                                if (this.rex.forcible.test(isLong)) {
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
                                if (this.rex.forcible.test(isForcible)) {
                                    return true;
                                }
                                throw Error(`Invalid param: {name: isForcible, value: ${isForcible}, type: ${species(isForcible)}.`);
                            }
                            return false;
                        },
                        show() {
                            _.uiHandler.post(() => {
                                if ($.isForcible) {
                                    _.toasts.dismissAll();
                                }
                                let len = $.isLong ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT;
                                let o = Toast.makeText(_.uiHandler.getContext(), $.message, len);
                                _.toasts.add(o);
                                o.show();
                            });
                        },
                    };

                    $.toast();
                };

                return Object.assign(toast, ToastCtor.prototype);
            };

            ToastCtor.prototype = {
                constructor: ToastCtor,
                dismissAll() {
                    _.uiHandler.post(() => {
                        _.toasts.dismissAll();
                    });
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