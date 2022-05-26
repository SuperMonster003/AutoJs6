// @Hint by SuperMonster003 on May 5, 2022.
//  ! Store global function reference(s) immediately in case
//  ! the one(s) being overwritten (usually by accident).
const $isNullish = global.isNullish.bind(global);

let _ = {
    // @Caution by SuperMonster003 on Apr 19, 2022.
    //  ! Do not declare globally because variable Continuation which
    //  ! extends org.mozilla.javascript.NativeContinuation has already declared.
    Continuation: com.stardust.autojs.rhino.continuation.Continuation,
    init(__runtime__, scope) {
        this.runtime = __runtime__;
        this.scope = scope;
        this.continuation = () => {
            // Empty module body
        };
    },
    /**
     * @param {org.mozilla.javascript.Scriptable} scope
     * @param {Promise} promise
     * @return {any}
     */
    awaitPromise(scope, promise) {
        const cont = continuation.create(scope);
        promise
            .then(result => cont.resume(result))
            .catch(error => cont.resumeError(error));
        return cont.await();
    },
    selfAugment() {
        Object.assign(this.continuation, {
            get enabled() {
                return engines.myEngine().hasFeature('continuation');
            },
            create(scope) {
                const cont = Object.create(_.runtime.createContinuation(scope || global));
                cont.await = function () {
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
                    let result = cont.suspend();
                    if (result.error !== null) {
                        throw result.error;
                    }
                    return result.result;
                };
                cont.resumeError = function (error) {
                    if ($isNullish(error)) {
                        throw TypeError('Error is null or undefined');
                    }
                    cont.resumeWith(_.Continuation.Result.Companion.failure(error));
                };
                cont.resume = function (result) {
                    cont.resumeWith(_.Continuation.Result.Companion.success(result));
                };
                return cont;
            },
            await(promise) {
                return _.awaitPromise(_.scope, promise);
            },
            delay(millis) {
                const cont = continuation.create();
                setTimeout(() => cont.resume(), millis);
                cont.await();
            },
        });
    },
    promiseAugment() {
        Promise.prototype.await = function () {
            return continuation.await(this);
        };
    },
    getModule() {
        return this.continuation;
    },
};

let $ = {
    getModule(__runtime__, scope) {
        _.init(__runtime__, scope);

        _.selfAugment();
        _.promiseAugment();

        return _.getModule();
    },
};

/**
 * @param {com.stardust.autojs.runtime.ScriptRuntime} __runtime__
 * @param {org.mozilla.javascript.Scriptable} scope
 * @return {Internal.Continuation}
 */
module.exports = function (__runtime__, scope) {
    return $.getModule(__runtime__, scope);
};