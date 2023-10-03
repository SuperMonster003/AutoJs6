/* Overwritten protection. */

let { engines, global: _global } = global;

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Continuation}
 */
module.exports = function (scriptRuntime, scope) {
    // @Caution by SuperMonster003 on Apr 19, 2022.
    //  ! Do not declare globally because variable Continuation which
    //  ! extends org.mozilla.javascript.NativeContinuation has already declared.
    const Result = org.autojs.autojs.rhino.continuation.Continuation.Result;

    let _ = {
        Creator: (/* @IIFE */ () => {
            /**
             * @extends Internal.Continuation.Creator
             */
            const Creator = function (scope) {
                this.cont = Object.create(scriptRuntime.createContinuation(scope || _global));
            };

            Creator.prototype = {
                constructor: Creator,
                await() {
                    /**
                     * @Caution by SuperMonster003 on Apr 19, 2022.
                     * Continuation without "continuation feature" will cause an exception
                     * which makes all invocations failed and interrupted here.
                     *
                     * @example Exception snippet
                     * Wrapped java.lang.IllegalStateException:
                     * Cannot capture continuation from JavaScript code not called directly
                     * by executeScriptWithContinuations or callFunctionWithContinuations
                     *
                     * @example Code for reappearance
                     * engines.myEngine().hasFeature('continuation'); // false
                     * Object.create(runtime.createContinuation()).suspend(); // throw error
                     */
                    let result = this.cont.suspend();
                    if (result.error !== null) {
                        // throw result.error;
                        if (typeof result.error.message === 'string') {
                            ScriptRuntime.popException(result.error.message);
                        } else {
                            ScriptRuntime.popException(String(result.error));
                        }
                        return result.error;
                    }
                    return result.result;
                },
                resumeError(error) {
                    if (isNullish(error)) {
                        throw TypeError('Error is null or undefined');
                    }
                    this.cont.resumeWith(Result.failure(error));
                },
                resume(result) {
                    this.cont.resumeWith(Result.success(result));
                },
            };

            return Creator;
        })(),
        Continuation: (/* @IIFE */ () => {
            /**
             * @implements Internal.Continuation
             */
            const Continuation = function () {
                return Object.assign(function () {
                    /* Empty body. */
                }, Continuation.prototype);
            };

            Continuation.prototype = {
                constructor: Continuation,
                get enabled() {
                    return engines.myEngine().hasFeature('continuation');
                },
                create(scope) {
                    return new _.Creator(scope);
                },
                await(promise) {
                    const cont = this.create(scope);
                    promise
                        .then(result => cont.resume(result))
                        .catch(error => cont.resumeError(error));
                    return cont.await();
                },
                delay(millis) {
                    const cont = this.create(scope);
                    setTimeout(() => cont.resume(), millis);
                    cont.await();
                },
            };

            return Continuation;
        })(),
        promiseAugment() {
            /**
             * @implements Internal.Continuation.PromiseExtension
             */
            const PromiseExtension = function () {
                /* Empty body. */
            };

            Object.assign(PromiseExtension.prototype, {
                await() {
                    return continuation.await(this);
                },
            });

            Object.assign(Promise.prototype, PromiseExtension.prototype);
        },
    };

    /**
     * @type {Internal.Continuation}
     */
    const continuation = new _.Continuation();

    _.promiseAugment();

    return continuation;
};