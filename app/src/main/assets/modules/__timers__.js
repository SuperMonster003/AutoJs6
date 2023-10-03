/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Timers}
 */
module.exports = function (scriptRuntime, scope) {

    let _ = {
        Timers: (() => {
            /**
             * @implements Internal.Timers
             */
            const Timers = function () {
                /* Empty body. */
            };

            Timers.prototype = {
                constructor: Timers,
                setIntervalExt(listener, interval, timeout, callback) {
                    let $ = {
                        initTimestamp: Date.now(),
                        interval: interval > 0 ? interval : 200,
                        timeout: timeout > 0 ? timeout : Infinity,
                        setIntervalExt() {
                            return setTimeout(this.run.bind(this), this.interval);
                        },
                        run() {
                            listener();
                            this.relayIFN();
                        },
                        relayIFN() {
                            if (!this.isTimedOut()) {
                                this.setIntervalExt();
                            } else if (typeof callback === 'function') {
                                callback.call(this, this.timeoutResult);
                            }
                        },
                        isTimedOut() {
                            if (typeof this.isTimedOutCache !== 'function') {
                                this.isTimedOutCache = typeof timeout === 'function'
                                    ? timeout.bind(this)
                                    : () => Date.now() - this.initTimestamp > this.timeout;
                            }
                            this.timeoutResult = this.isTimedOutCache();
                            return this.timeoutResult !== false && !isNullish(this.timeoutResult);
                        },
                    };
                    return $.setIntervalExt();
                },
            };

            Object.setPrototypeOf(Timers.prototype, scriptRuntime.timers);

            return Timers;
        })(),
        scopeAugment() {
            /**
             * @type {(keyof Internal.Timers)[]}
             */
            let methods = [
                'setTimeout', 'clearTimeout',
                'setInterval', 'clearInterval',
                'setImmediate', 'clearImmediate',
            ];
            __asGlobal__(scriptRuntime.timers, methods, scope);

            Object.assign(scope, {
                /** @global */
                loop() {
                    // @Abandoned by SuperMonster003 as of May 3, 2023.
                    // scriptRuntime.console.warn('Method loop() is deprecated and has no effect.');
                    throw Error(context.getString(R.strings.error_abandoned_method, 'global.loop'));
                },
            });
        },
    };

    /**
     * @type {Internal.Timers}
     */
    const timers = new _.Timers();

    _.scopeAugment();

    return timers;
};