/* Here, importClass() is not recommended for intelligent code completion in IDE like WebStorm. */
/* The same is true of destructuring assignment syntax (like `let {Uri} = android.net`). */

const Looper = android.os.Looper;

let _ = {
    init() {
        this.ResultAdapter = _.isUiThread()
            ? function () {
                this.cont = continuation.create();
                this.impl = {
                    setResult: result => this.cont.resume(result),
                    setError: error => this.cont.resumeError(error),
                    get: () => this.cont.await(),
                };
            }
            : function () {
                this.disposable = threads.disposable();
                this.impl = {
                    setResult: result => this.disposable.setAndNotify({result}),
                    setError: error => this.disposable.setAndNotify({error}),
                    get: () => _.getOrThrow(this.disposable.blockedGet()),
                };
            };
    },
    getModule() {
        return this.ResultAdapter;
    },
    selfAugment() {
        Object.assign(this.ResultAdapter, {
            prototype: {
                constructor: this.ResultAdapter,
                setResult(result) {
                    this.impl.setResult(result);
                },
                setError(error) {
                    this.impl.setError(error);
                },
                callback() {
                    return function (result, error) {
                        this.result !== undefined
                            ? this.result = {result, error}
                            : error ? this.setError(error) : this.setResult(result);
                    }.bind(this);
                },
                get() {
                    if (this.result) {
                        return _.getOrThrow(this.result);
                    }
                    this.result = null;
                    return this.impl.get();
                },
            },
            /**
             * @param {com.stardust.autojs.core.util.ScriptPromiseAdapter} promiseAdapter
             * @return {Promise<unknown>}
             */
            promise(promiseAdapter) {
                return new Promise((resolve, reject) => {
                    promiseAdapter
                        .onResolve(result => resolve(result))
                        .onReject(error => reject(error));
                });
            },
            /**
             * @param {Promise<unknown> | com.stardust.autojs.core.util.ScriptPromiseAdapter} promise
             * @return {*}
             */
            wait(promise) {
                if (!(promise instanceof Promise)) {
                    promise = _.ResultAdapter.promise(promise);
                }
                return continuation.enabled ? promise.await() : promise.wait();
            },
        });
    },
    isUiThread() {
        return Looper.myLooper() === Looper.getMainLooper();
    },
    getOrThrow(result) {
        if (result.error) {
            throw result.error;
        }
        return result.result;
    },
};

let $ = {
    getModule() {
        _.init();
        _.selfAugment();
        return _.getModule();
    },
};

/**
 * @type {typeof Internal.ResultAdapter}
 */
module.exports = $.getModule();