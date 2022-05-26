// noinspection NpmUsedModulesInstalled

/* Here, importClass() is not recommended for intelligent code completion in IDE like WebStorm. */
/* The same is true of destructuring assignment syntax (like `let {Uri} = android.net`). */

const Runnable = java.lang.Runnable;
const Throwable = java.lang.Throwable;
const Synchronizer = org.mozilla.javascript.Synchronizer;
const TimerThread = com.stardust.autojs.core.looper.TimerThread;
const ScriptInterruptedException = com.stardust.autojs.runtime.exception.ScriptInterruptedException;

// @Hint by SuperMonster003 on May 5, 2022.
//  ! Store global function reference(s) immediately in case
//  ! the one(s) being overwritten (usually by accident).
const $isPlainObject = global.isPlainObject.bind(global);

let _ = {
    init(__runtime__, scope) {
        this.runtime = __runtime__;
        this.scope = scope;

        /**
         * @type {com.stardust.autojs.runtime.api.Threads}
         */
        this.threads = Object.create(__runtime__.threads);
        this.uberStart = this.threads.start.bind(this.threads);
    },
    getModule() {
        return this.threads;
    },
    selfAugment() {
        // @Caution by SuperMonster003 on May 4, 2022.
        //  ! Object.assign will cause Error:
        //  ! Java method "xxx" cannot be assigned to.
        Object.defineProperties(this.threads, {
            start: {
                /**
                 * @param {function|java.lang.Runnable|any} f
                 * @return {com.stardust.autojs.core.looper.TimerThread}
                 */
                value(f) {
                    try {
                        if (_.isNormalFunction(f) || f instanceof Runnable) {
                            return _.start(f);
                        }
                        if (_.isArrowFunction(f)) {
                            return _.start(new Runnable({run: () => f()}));
                        }
                        // noinspection ExceptionCaughtLocallyJS
                        throw TypeError(`Unsupported type "${typeof f}" for threads.start()`);
                    } catch (e) {
                        if (!ScriptInterruptedException.causedByInterrupted(new Throwable(e))) {
                            if (!e.message.match(/script exiting/)) {
                                throw Error(`${e}\n${e.stack}`);
                            }
                        }
                    }
                },
            },
            interrupt: {
                /**
                 * @param {com.stardust.autojs.core.looper.TimerThread|*} thread
                 */
                value(thread) {
                    if (thread instanceof TimerThread) {
                        thread.isAlive() && thread.interrupt();
                    }
                },
            },
        });
    },
    scopeAugment() {
        this.scope.sync = function (func, lock) {
            return new Synchronizer(func, lock || null);
        };
    },
    promiseAugment() {
        Promise.prototype.wait = function () {
            let disposable = threads.disposable();
            this
                .then(result => disposable.setAndNotify({result: result}))
                .catch(error => disposable.setAndNotify({error: error}));

            let resultObj = disposable.blockedGet();
            if (resultObj.error) {
                throw resultObj.error;
            }
            return resultObj.result;
        };
    },
    /**
     * @param {function|java.lang.Runnable} f
     * @return {com.stardust.autojs.core.looper.TimerThread}
     */
    start(f) {
        let uberThread = this.uberStart(f);
        let thread = Object.create(uberThread);

        // @Caution by SuperMonster003 on May 4, 2022.
        //  ! Object.assign will cause Error:
        //  ! Java method "join" cannot be assigned to.
        return Object.defineProperty(thread, 'join', {
            value(time) {
                if (!time || !isFinite(time)) {
                    time = 0; // infinite
                } else if (time < 1) {
                    time = 1; // 1 ms
                }
                uberThread.join(time);
            },
        });
    },
    isNormalFunction(f) {
        return typeof f === 'function' && $isPlainObject(f.prototype);
    },
    isArrowFunction(f) {
        return typeof f === 'function' && !$isPlainObject(f.prototype);
    },
};

let $ = {
    getModule(__runtime__, scope) {
        _.init(__runtime__, scope);

        _.selfAugment();
        _.scopeAugment();
        _.promiseAugment();

        return _.getModule();
    },
};

/**
 * @param {com.stardust.autojs.runtime.ScriptRuntime} __runtime__
 * @param {org.mozilla.javascript.Scriptable} scope
 * @return {com.stardust.autojs.runtime.api.Threads}
 */
module.exports = function (__runtime__, scope) {
    return $.getModule(__runtime__, scope);
};