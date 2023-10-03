// noinspection JSUnusedGlobalSymbols

/* Overwritten protection. */

let { autojs, console, device, pickup, ui, s13n, Numberx } = global;

/* Here, importClass() is not recommended for intelligent code completion in IDE like WebStorm. */
/* The same is true of destructuring assignment syntax (like `let {Uri} = android.net`). */

/**
 * @param {org.autojs.autojs.runtime.ScriptRuntime} runtime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 */
module.exports = function (runtime, scope) {
    let _ = {
        scale: {
            base: {
                x: 720,
                y: 1280,
            },
            baseState: {
                x: false,
                y: false,
            },
            ensureBase(o) {
                if (!(o > 0 && Number.isInteger(o))) {
                    throw RangeError(`Scale base "${o}" must be a positive integer.`);
                }
            },
            ensureBaseXSetOnlyOnce(x) {
                if (this.baseState.x === x) {
                    throw Error(`Scale base X could be set only once, ${this.base.x} has been set as the base.`);
                }
            },
            ensureBaseYSetOnlyOnce(y) {
                if (this.baseState.y === y) {
                    throw Error(`Scale base Y could be set only once, ${this.base.y} has been set as the base.`);
                }
            },
            ensureBasesConsistent() {
                if (this.baseState.x !== this.baseState.y) {
                    throw Error(`Scale bases must be consistent, { x: ${this.baseState.x}, y: ${this.baseState.y} }.`);
                }
            },
        },
        buildTypes: {
            release: 100, beta: 50, alpha: 0,
        },
        extensions: {
            /** @global */
            get WIDTH() {
                return ScreenMetrics.getDeviceScreenWidth();
            },
            /** @global */
            get HEIGHT() {
                return ScreenMetrics.getDeviceScreenHeight();
            },
            /** @global */
            toastLog(msg, isLong, isForcible) {
                toast.apply(this, arguments);
                console.log(msg);
            },
            /** @global */
            sleep(millisMin, millisMax) {
                let $ = {
                    rexNum: /[+-]?(\d+(\.\d+)?(e\d+)?)/,
                    set min(v) {
                        this._min = Number(v);
                    },
                    get min() {
                        return Math.max(this._min, 0);
                    },
                    set max(v) {
                        this._max = Number(v);
                    },
                    get max() {
                        return Math.min(this._max, Number.MAX_SAFE_INTEGER);
                    },
                    sleep(min, max) {
                        if (this.trigger()) {
                            this.parseArgs(min, max);
                            try {
                                runtime.sleep(this.min + this.randBound);
                            } catch (e) {
                                if (!(e.javaException instanceof ScriptInterruptedException)) {
                                    throw e;
                                }
                            }
                        }
                    },
                    trigger() {
                        if (ui.isUiThread()) {
                            throw Error('不能在ui线程执行阻塞操作，请使用setTimeout代替.');
                        }
                        return true;
                    },
                    parseArgs(min, max) {
                        this.parseMin(min);
                        this.parseMax(max);
                        this.parseRandBound();
                    },
                    parseMin(min) {
                        if (typeof min !== 'number') {
                            throw TypeError('Type of millisMin must be a number.');
                        }
                        this.min = min;
                    },
                    parseMax(max) {
                        if (typeof max === 'number') {
                            this.max = max;
                        } else if (typeof max === 'string') {
                            let matched = max.match(this.rexNum);
                            if (matched === null) {
                                throw TypeError('String millisMax must have a number contained.');
                            }
                            let delta = Number(matched[0]);
                            this.max = this.min + delta;
                            this.min = this.min - delta;
                        } else {
                            this.max = this.min;
                        }
                    },
                    parseRandBound() {
                        this.randBound = Math.ceil(Math.random() * (this.max - this.min));
                    },
                };

                $.sleep(millisMin, millisMax);
            },
            /** @global */
            isStopped() {
                return runtime.isStopped();
            },
            /** @global */
            isShuttingDown() {
                return isStopped();
            },
            /** @global */
            notStopped() {
                return !this.isStopped();
            },
            /** @global */
            isRunning() {
                return !this.isStopped();
            },
            /** @global */
            exit(e) {
                if (typeof e === 'undefined') {
                    runtime.exit();
                } else {
                    runtime.exit(s13n.throwable(e));
                }
            },
            /** @global */
            stop() {
                this.exit();
            },
            /** @global */
            setClip(text) {
                return runtime.setClip(text);
            },
            /** @global */
            getClip() {
                return runtime.getClip();
            },
            /** @global */
            currentPackage() {
                auto();
                return runtime.info.getLatestPackage();
            },
            /** @global */
            currentActivity() {
                auto();
                return runtime.info.getLatestActivity();
            },
            /**
             * @global
             * @param {Wait.Condition} condition
             * @param {?number|Wait.Callback} [limit=10e3]
             * @param {?number|Wait.Callback} [interval=200]
             * @param {Wait.Callback} [callback]
             * @return {any}
             */
            wait(condition, limit, interval, callback) {
                if (species.isObject(arguments[1])) {
                    // @Overload wait(condition, callback): any
                    return this.wait(condition, /* limit = */ null, /* interval = */ null, /* callback = */ arguments[1]);
                }

                if (species.isObject(arguments[2])) {
                    // @Overload wait(condition, limit, callback): any
                    return this.wait(condition, limit, /* interval = */ null, /* callback = */ arguments[2]);
                }

                let $ = {
                    result: false,
                    start: Date.now(),
                    callback: callback || {},
                    parseArgs() {
                        let lmt = typeof limit === 'number' ? limit : limit === null ? NaN : Number(limit);
                        if (isNaN(lmt)) {
                            lmt = 10e3;
                        }
                        if (lmt < 0) {
                            throw Error(`Limitation (${lmt}) cannot be negative for wait().`);
                        }
                        if (lmt < 100) {
                            this.times = lmt;
                            this.timeout = Infinity;
                        } else {
                            this.times = Infinity;
                            this.timeout = lmt;
                        }

                        let itv = typeof interval === 'number' ? interval : interval === null ? NaN : Number(interval);
                        if (isNaN(itv)) {
                            itv = 200;
                        }
                        if (!isFinite(itv)) {
                            throw Error(`Interval cannot be Infinity for wait().`);
                        }
                        if (itv < 0) {
                            throw Error(`Interval (${itv}) cannot be negative for wait().`);
                        }
                        this.interval = itv;

                        if (this.interval >= this.timeout) {
                            this.times = Math.min(this.times, 1);
                        }
                    },
                    check() {
                        if (condition instanceof UiObject) {
                            throw TypeError('UiObject cannot be used as the condition for wait().');
                        }
                        return typeof condition === 'function' ? condition() : pickup(condition);
                    },
                    wait() {
                        if (!this.times) {
                            return;
                        }
                        while (true) {
                            let checked = this.checked = this.check();

                            // Some falsy values [ 0, 0n, -0, "" (empty string) ] should pass the check.
                            this.result = !(isNullish(checked) || Number.isNaN(checked) || checked === false);

                            this.times -= 1;

                            if (this.result || !this.times) {
                                break;
                            }
                            if (Date.now() - this.start > this.timeout) {
                                break;
                            }
                            sleep(this.interval);
                        }
                    },
                    callbackIFN() {
                        let fn = this.result ? this.callback.then : this.callback.else;
                        if (fn !== undefined) {
                            if (typeof fn !== 'function') {
                                throw TypeError(`Callback must be function type for wait().`);
                            }
                            let res = fn(this.checked);
                            if (res !== undefined) {
                                this.result = res;
                            }
                        }
                    },
                    getResult() {
                        this.parseArgs();
                        this.wait();
                        this.callbackIFN();

                        return this.result;
                    },
                };

                return $.getResult();
            },
            /** @global */
            waitForActivity(activityName, limit, interval, callback) {
                _.ensureNonUiThread();
                let condition = () => currentActivity() === activityName;
                return wait.apply(scope, [ condition ].concat(Array.from(arguments).slice(1)));
            },
            /** @global */
            waitForPackage(packageName, limit, interval, callback) {
                _.ensureNonUiThread();
                let condition = () => currentPackage() === packageName;
                return wait.apply(scope, [ condition ].concat(Array.from(arguments).slice(1)));
            },
            /** @global */
            random(min, max) {
                if (arguments.length === 0) {
                    return Math.random();
                }
                return Math.floor(Math.random() * (max - min + 1)) + min;
            },
            /** @global */
            setScreenMetrics(width, height) {
                runtime.setScreenMetrics(width, height);
            },
            /** @global */
            requiresApi(requiresApi) {
                ScriptRuntime.requiresApi(requiresApi);
            },
            /** @global */
            requiresAutojsVersion(version) {
                if (typeof version === 'number') {
                    if (_.compare(version, autojs.versionCode) > 0) {
                        throw Error(`AutoJs6 版本号需不低于 ${version}.`);
                    }
                } else {
                    if (_.compareVersion(version, autojs.versionName) > 0) {
                        throw Error(`AutoJs6 版本需不低于 ${version}.`);
                    }
                }
            },
            getScaleBases() {
                return _.scale.base;
            },
            getScaleBaseX() {
                return _.scale.base.x;
            },
            getScaleBaseY() {
                return _.scale.base.y;
            },
            setScaleBases(baseX, baseY) {
                this.setScaleBaseX(baseX);
                this.setScaleBaseY(baseY);
            },
            setScaleBaseX(baseX) {
                _.scale.ensureBaseXSetOnlyOnce(baseX);
                _.scale.ensureBase(baseX);
                _.scale.base.x = baseX;
                _.scale.baseState.x = true;
            },
            setScaleBaseY(baseY) {
                _.scale.ensureBaseYSetOnlyOnce(baseY);
                _.scale.ensureBase(baseY);
                _.scale.base.y = baseY;
                _.scale.baseState.y = true;
            },
            /** @global */
            cX(num, base, isRatio) {
                let W = device.width;
                if (arguments.length === 0) {
                    return W;
                }
                // @Overload
                if (typeof base === 'boolean') {
                    return this.cX(num, null, base);
                }
                if (Math.abs(num) < 1 && isRatio !== false || isRatio) {
                    return Math.round(W * num);
                }
                if (typeof base === 'number') {
                    _.scale.ensureBase(base);
                } else {
                    base = _.scale.base.x;
                }
                return Math.round(W * num / base);
            },
            /** @global */
            cY(num, base, isRatio) {
                let H = device.height;
                if (arguments.length === 0) {
                    return H;
                }
                // @Overload
                if (typeof base === 'boolean') {
                    return this.cY(num, null, base);
                }
                if (Math.abs(num) < 1 && isRatio !== false || isRatio) {
                    return Math.round(H * num);
                }
                if (typeof base === 'number') {
                    _.scale.ensureBase(base);
                } else {
                    base = _.scale.base.y;
                }
                return Math.round(H * num / base);
            },
            /** @global */
            cYx(num, base, isRatio) {
                _.scale.ensureBasesConsistent();
                // @Overload
                if (typeof base === 'boolean') {
                    return this.cYx(num, null, base);
                }
                // @Overload
                if (typeof base === 'string') {
                    return this.cYx(num, Numberx.parseRatio(base), true);
                }
                let W = device.width;
                if (Math.abs(num) < 1 || isRatio) {
                    if (isNullish(base)) {
                        base = _.scale.base.y / _.scale.base.x;
                    }
                    if (typeof base === 'number') {
                        return Numberx.check(0, '<', base, '<=', 1)
                            ? Math.round(num * W / base)
                            : Math.round(num * W * base);
                    }
                    throw Error('Base of cYx() must be a valid number.');
                }
                if (isNullish(base)) {
                    base = _.scale.base.x;
                }
                return Math.round(num * W / base);
            },
            /** @global */
            cXy(num, base, isRatio) {
                _.scale.ensureBasesConsistent();
                // @Overload
                if (typeof base === 'boolean') {
                    return this.cXy(num, null, base);
                }
                // @Overload
                if (typeof base === 'string') {
                    return this.cXy(num, Numberx.parseRatio(base), true);
                }
                let H = device.height;
                if (Math.abs(num) < 1 || isRatio) {
                    if (isNullish(base)) {
                        base = _.scale.base.y / _.scale.base.x;
                    }
                    if (typeof base === 'number') {
                        return Numberx.check(0, '<', base, '<=', 1)
                            ? Math.round(num * H * base)
                            : Math.round(num * H / base);
                    }
                    throw Error('Base of cXy() must be a valid number.');
                }
                if (isNullish(base)) {
                    base = _.scale.base.y;
                }
                return Math.round(num * H / base);
            },
            // $selfProtect() {
            //     /* Protection of global properties from being modified or deleted. */
            //     (/* @IIFE */ () => [
            //         [ 'auto', 'colors' ],
            //
            //         [ 'android', 'com', 'edu', 'java', 'javax', 'net', 'org' ],
            //
            //         [ 'Array', 'BigInt', 'Boolean', 'Error', 'Function', 'JSON', 'Map', 'Math' ],
            //         [ 'Module', 'Number', 'Object', 'Promise', 'RegExp', 'Set', 'String', 'Symbol' ],
            //
            //         [ 'clearImmediate', 'clearInterval', 'clearTimeout', 'decodeURI', 'decodeURIComponent' ],
            //         [ 'encodeURI', 'encodeURIComponent', 'escape', 'eval', 'isFinite', 'isNaN', 'parseFloat' ],
            //         [ 'parseInt', 'setImmediate', 'setInterval', 'setTimeout', 'unescape' ],
            //
            //         [ 'Packages', 'alert', 'confirm', 'click', 'err', 'log', 'pickup', 'detect', 'prompt' ],
            //     ]
            //         .flat()
            //         .filter((name) => {
            //             return Object.prototype.hasOwnProperty.call(scope, name)
            //                 && Object.getOwnPropertyDescriptor(scope, name).writable;
            //         })
            //         .forEach((key) => {
            //             Object.defineProperty(scope, key, {
            //                 configurable: false,
            //                 writable: false,
            //             });
            //         }))();
            //
            //     delete this.$selfProtect;
            // },
            $appropriateProtect() {
                [
                    'continuation', 'selector',
                ].forEach((key) => {
                    Object.defineProperty(scope, key, {
                        enumerable: true,
                        configurable: false,
                        writable: false,
                    });
                });

                delete this.$appropriateProtect;
            },
        },
        legacies: {
            isObjectSpecies(o) {
                return species.isObject(o);
            },
        },
        ensureNonUiThread() {
            if (ui.isUiThread()) {
                throw Error('不能在ui线程执行阻塞操作，请在子线程或子脚本执行，或者使用setInterval循环检测当前activity和package.');
            }
        },
        compareVersion(v1, v2) {
            v1 = this.parseVersion(v1);
            v2 = this.parseVersion(v2);
            if (v1.major !== v2.major) {
                return this.compare(v1.major, v2.major);
            }
            if (v1.minor !== v2.minor) {
                return this.compare(v1.minor, v2.minor);
            }
            if (v1.revision !== v2.revision) {
                return this.compare(v1.revision, v2.revision);
            }
            if (v1.buildType !== v2.buildType) {
                return this.compare(v1.buildType, v2.buildType);
            }
            return this.compare(v1.build, v2.build);
        },
        compare(a, b) {
            return a > b ? 1 : a < b ? -1 : 0;
        },
        parseVersion(v) {
            const m = /(\d+)\.(\d+)\.(\d+)\s*(A(lpha)?|B(eta)?)?(\d*)/i.exec(v);
            if (!m) {
                throw Error(`版本格式不合法: ${v}.`);
            }
            return {
                major: parseInt(m[1]),
                minor: parseInt(m[2]),
                revision: parseInt(m[3]),
                buildType: _.buildType(m[4]),
                build: m[5] ? parseInt(m[5]) : 1,
            };
        },
        buildType(str) {
            if (str === 'Alpha') {
                return this.buildTypes.alpha;
            }
            if (str === 'Beta') {
                return this.buildTypes.beta;
            }
            return this.buildTypes.release;
        },
    };

    Object.assign(scope, _.extensions, _.legacies);

    // Object.keys(_.extensions)
    //     .filter(key => !key.startsWith('$'))
    //     .forEach((key) => {
    //         Object.defineProperty(scope, key, {
    //             enumerable: true,
    //             configurable: false,
    //             writable: false,
    //         });
    //     });
};