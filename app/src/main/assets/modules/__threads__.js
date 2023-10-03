// noinspection NpmUsedModulesInstalled

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Threads}
 */
module.exports = function (scriptRuntime, scope) {
    const Throwable = java.lang.Throwable;
    const Synchronizer = org.mozilla.javascript.Synchronizer;
    const TimerThread = org.autojs.autojs.core.looper.TimerThread;

    const rtThreads = runtime.threads;

    let _ = {
        Threads: (/* @IIFE */ () => {
            /**
             * @implements Internal.Threads
             */
            const Threads = function () {
                /* Empty body. */
            };

            Threads.prototype = {
                constructor: Threads,
                atomic(value) {
                    return rtThreads.atomic.apply(rtThreads, arguments);
                },
                currentThread() {
                    return rtThreads.currentThread.apply(rtThreads, arguments);
                },
                disposable() {
                    return rtThreads.disposable.apply(rtThreads, arguments);
                },
                lock() {
                    return rtThreads.lock.apply(rtThreads, arguments);
                },
                interrupt(thread) {
                    if (thread instanceof TimerThread) {
                        thread.isAlive() && thread.interrupt();
                    }
                },
                start(runnable) {
                    try {
                        // noinspection JSCheckFunctionSignatures
                        return rtThreads.start(runnable);
                    } catch (e) {
                        if (!ScriptInterruptedException.causedByInterrupted(new Throwable(e))) {
                            if (!e.message.endsWith(context.getString(R.strings.error_script_is_on_exiting))) {
                                throw Error(`${e}\n${e.stack}`);
                            }
                        }
                    }
                },
            };

            Object.setPrototypeOf(Threads.prototype, scriptRuntime.threads);

            return Threads;
        })(),
        scopeAugment() {
            Object.assign(scope, {
                /**
                 * @global
                 */
                sync(func, lock) {
                    return new Synchronizer(func, lock || null);
                },
            });
        },
        promiseAugment() {
            /**
             * @implements Internal.Threads.PromiseExtension
             */
            const PromiseExtension = function () {
                /* Empty body. */
            };

            Object.assign(PromiseExtension.prototype, {
                wait() {
                    let disposable = scriptRuntime.threads.disposable();
                    this
                        .then(result => disposable.setAndNotify({ result: result }))
                        .catch(error => disposable.setAndNotify({ error: error }));

                    let resultObj = disposable.blockedGet();
                    if (resultObj.error) {
                        throw resultObj.error;
                    }
                    return resultObj.result;
                },
            });

            Object.assign(Promise.prototype, PromiseExtension.prototype);
        },
    };

    /**
     * @type {Internal.Threads}
     */
    const threads = new _.Threads();

    _.scopeAugment();
    _.promiseAugment();

    return threads;
};