// noinspection JSUnusedGlobalSymbols

/**
 * Updated and modified by SuperMonster003 on May 23, 2022.
 * @see https://raw.githubusercontent.com/taylorhakes/promise-polyfill/master/dist/polyfill.js
 */

/**
 * @type {PromiseConstructorLike}
 */
module.exports = (/* @IIFE */ () => {
    let _ = {
        isArray(x) {
            return Boolean(x && typeof x.length !== 'undefined');
        },
        noop() {
        },
        // Polyfill for Function.prototype.bind
        bind(fn, thisArg) {
            return function () {
                fn.apply(thisArg, arguments);
            };
        },
        handle(self, deferred) {
            while (self._state === 3) {
                self = self._value;
            }
            if (self._state === 0) {
                self._deferreds.push(deferred);
                return;
            }
            self._handled = true;
            Promise._immediateFn(function () {
                let cb = self._state === 1 ? deferred.onFulfilled : deferred.onRejected;
                if (cb === null) {
                    (self._state === 1 ? _.resolve : _.reject)(deferred.promise, self._value);
                    return;
                }
                let ret;
                try {
                    ret = cb(self._value);
                } catch (e) {
                    _.reject(deferred.promise, e);
                    return;
                }
                _.resolve(deferred.promise, ret);
            });
        },
        resolve(self, newValue) {
            try {
                // Promise Resolution Procedure: https://github.com/promises-aplus/promises-spec#the-promise-resolution-procedure
                if (newValue === self) {
                    // noinspection ExceptionCaughtLocallyJS
                    throw new TypeError('A promise cannot be resolved with itself.');
                }
                if (newValue && (typeof newValue === 'object' || typeof newValue === 'function')) {
                    let then = newValue.then;
                    if (newValue instanceof Promise) {
                        self._state = 3;
                        self._value = newValue;
                        _.finale(self);
                        return;
                    } else if (typeof then === 'function') {
                        _.doResolve(_.bind(then, newValue), self);
                        return;
                    }
                }
                self._state = 1;
                self._value = newValue;
                _.finale(self);
            } catch (e) {
                _.reject(self, e);
            }
        },
        reject(self, newValue) {
            self._state = 2;
            self._value = newValue;
            _.finale(self);
        },
        finale(self) {
            if (self._state === 2 && self._deferreds.length === 0) {
                Promise._immediateFn(function () {
                    if (!self._handled) {
                        Promise._unhandledRejectionFn(self._value);
                    }
                });
            }

            for (let i = 0, len = self._deferreds.length; i < len; i++) {
                _.handle(self, self._deferreds[i]);
            }
            self._deferreds = null;
        },
        /**
         * @constructor
         */
        Handler(onFulfilled, onRejected, promise) {
            this.onFulfilled = typeof onFulfilled === 'function' ? onFulfilled : null;
            this.onRejected = typeof onRejected === 'function' ? onRejected : null;
            this.promise = promise;
        },
        /**
         * Take a potentially misbehaving resolver function and make sure
         * onFulfilled and onRejected are only called once.
         *
         * Makes no guarantees about asynchrony.
         */
        doResolve(fn, self) {
            let done = false;
            try {
                fn(
                    function (value) {
                        if (done) return;
                        done = true;
                        _.resolve(self, value);
                    },
                    function (reason) {
                        if (done) return;
                        done = true;
                        _.reject(self, reason);
                    },
                );
            } catch (ex) {
                if (done) return;
                done = true;
                _.reject(self, ex);
            }
        },
    };

    /**
     * @constructor
     * @param {Function} fn
     */
    let Promise = function (fn) {
        if (!(this instanceof Promise)) {
            throw new TypeError('Promises must be constructed via new');
        }
        if (typeof fn !== 'function') {
            throw new TypeError('not a function');
        }
        /** @type {!number} */
        this._state = 0;
        /** @type {!boolean} */
        this._handled = false;
        /** @type {Promise|undefined} */
        this._value = undefined;
        /** @type {!Array<!Function>} */
        this._deferreds = [];

        _.doResolve(fn, this);
    };

    Object.assign(Promise.prototype, {
        catch(onRejected) {
            return this.then(null, onRejected);
        },
        then(onFulfilled, onRejected) {
            let prom = new this.constructor(_.noop);
            _.handle(this, new _.Handler(onFulfilled, onRejected, prom));
            return prom;
        },
        finally(callback) {
            let constructor = this.constructor;
            return this.then(
                function (value) {
                    return constructor.resolve(callback()).then(function () {
                        return value;
                    });
                },
                function (reason) {
                    return constructor.resolve(callback()).then(function () {
                        return constructor.reject(reason);
                    });
                },
            );
        },
        await() {
            return continuation.await(this);
        },
        wait() {
            let disposable = threads.disposable();
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

    Object.setPrototypeOf(Promise, {
        // Use polyfill for setImmediate for performance gains
        _immediateFn(fn) {
            typeof setImmediate === 'function'
                ? setImmediate(fn)
                : setTimeout(fn, 0);
        },
        _unhandledRejectionFn(err) {
            if (typeof console !== 'undefined') {
                console.warn('Possible Unhandled Promise Rejection:', err);
            }
        },
        all(arr) {
            return new Promise(function (resolve, reject) {
                if (!_.isArray(arr)) {
                    return reject(new TypeError('Promise.all accepts an array'));
                }

                let args = Array.prototype.slice.call(arr);
                let remaining = args.length;
                if (remaining === 0) {
                    return resolve([]);
                }

                function res(i, val) {
                    try {
                        if (val && (typeof val === 'object' || typeof val === 'function')) {
                            let then = val.then;
                            if (typeof then === 'function') {
                                then.call(
                                    val,
                                    function (val) {
                                        res(i, val);
                                    },
                                    reject,
                                );
                                return;
                            }
                        }
                        args[i] = val;
                        if (--remaining === 0) {
                            resolve(args);
                        }
                    } catch (ex) {
                        reject(ex);
                    }
                }

                for (let i = 0; i < args.length; i++) {
                    res(i, args[i]);
                }
            });
        },
        allSettled(arr) {
            let P = this;
            return new P(function (resolve, reject) {
                if (!(arr && typeof arr.length !== 'undefined')) {
                    let msg = `${typeof arr} ${arr} is not iterable (cannot read property Symbol(Symbol.iterator))`;
                    return reject(new TypeError(msg));
                }
                let args = Array.prototype.slice.call(arr);
                let remaining = args.length;
                if (remaining === 0) {
                    return resolve([]);
                }

                function res(i, val) {
                    if (val && (typeof val === 'object' || typeof val === 'function')) {
                        let then = val.then;
                        if (typeof then === 'function') {
                            then.call(
                                val,
                                function (val) {
                                    res(i, val);
                                },
                                function (e) {
                                    args[i] = { status: 'rejected', reason: e };
                                    if (--remaining === 0) {
                                        resolve(args);
                                    }
                                },
                            );
                            return;
                        }
                    }
                    args[i] = { status: 'fulfilled', value: val };
                    if (--remaining === 0) {
                        resolve(args);
                    }
                }

                for (let i = 0; i < args.length; i++) {
                    res(i, args[i]);
                }
            });
        },
        resolve(value) {
            if (value && typeof value === 'object' && value.constructor === Promise) {
                return value;
            }
            return new Promise(function (resolve) {
                resolve(value);
            });
        },
        reject(value) {
            return new Promise(function (resolve, reject) {
                reject(value);
            });
        },
        race(arr) {
            return new Promise(function (resolve, reject) {
                if (!_.isArray(arr)) {
                    return reject(new TypeError('Promise.race accepts an array'));
                }

                for (let i = 0, len = arr.length; i < len; i++) {
                    Promise.resolve(arr[i]).then(resolve, reject);
                }
            });
        },
    });

    return Promise;
})();