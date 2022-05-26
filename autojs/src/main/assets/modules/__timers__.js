// @Hint by SuperMonster003 on May 5, 2022.
//  ! Store global function reference(s) immediately in case
//  ! the one(s) being overwritten (usually by accident).
const $isNullish = global.isNullish.bind(global);

let _ = {
    init(__runtime__, scope) {
        this.runtime = __runtime__;
        this.scope = scope;

        /**
         * @type {com.stardust.autojs.runtime.api.Timers}
         */
        this.rtTimers = __runtime__.timers;
        this.timers = Object.create(this.rtTimers);
    },
    getModule() {
        return this.timers;
    },
    selfAugment() {
        Object.assign(this.timers, {
            /**
             * @param {function} listener
             * @param {number} [interval=200]
             * @param {number|function():boolean} [timeout=Infinity]
             * @param {function} [callback]
             * @return {number}
             */
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
                        return this.timeoutResult !== false && !$isNullish(this.timeoutResult);
                    },
                };
                // noinspection JSValidateTypes
                return $.setIntervalExt();
            },
        });
    },
    scopeAugment() {
        /**
         * @type {(keyof Internal.Timers)[]}
         */
        let methods = [
            'setTimeout', 'clearTimeout',
            'setInterval', 'clearInterval',
            'setImmediate', 'clearImmediate',
        ];
        __asGlobal__(this.rtTimers, methods);

        this.scope.loop = () => console.warn('Method loop() is deprecated and has no effect.');
    },
};

let $ = {
    getModule(__runtime__, scope) {
        _.init(__runtime__, scope);

        _.selfAugment();
        _.scopeAugment();

        return _.getModule();
    },
};

/**
 * @param {com.stardust.autojs.runtime.ScriptRuntime} __runtime__
 * @param {org.mozilla.javascript.Scriptable} scope
 * @return {Internal.Timers}
 */
module.exports = function (__runtime__, scope) {
    return $.getModule(__runtime__, scope);
};