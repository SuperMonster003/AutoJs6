( /* @ModuleIIFE */ () => {

    const Looper = android.os.Looper;

    let _ = {
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

    /**
     * @class
     * @extends {Internal.ResultAdapter}
     */
    let ResultAdapter = _.isUiThread()
        ? function () {
            this.cont = continuation.create();
            this.impl = {
                setResult: result => this.cont.resume(result),
                setError: error => this.cont.resumeError(error),
                get: () => this.cont.await(),
            };
        }
        : function () {
            this.disposable = runtime.threads.disposable();
            this.impl = {
                setResult: result => this.disposable.setAndNotify({ result }),
                setError: error => this.disposable.setAndNotify({ error }),
                get: () => _.getOrThrow(this.disposable.blockedGet()),
            };
        };

    Object.assign(ResultAdapter, {
        prototype: {
            constructor: ResultAdapter,
            setResult(result) {
                this.impl.setResult(result);
            },
            setError(error) {
                this.impl.setError(error);
            },
            callback() {
                return function (result, error) {
                    this.result !== undefined
                        ? this.result = { result, error }
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
         * @param {org.autojs.autojs.runtime.api.ScriptPromiseAdapter} promiseAdapter
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
         * @param {Promise<unknown> | org.autojs.autojs.runtime.api.ScriptPromiseAdapter} promise
         * @return {*}
         */
        wait(promise) {
            if (!(promise instanceof Promise)) {
                promise = ResultAdapter.promise(promise);
            }
            return continuation.enabled ? promise.await() : promise.wait();
        },
    });

    /**
     * @type {typeof Internal.ResultAdapter}
     */
    module.exports = ResultAdapter;
})();
