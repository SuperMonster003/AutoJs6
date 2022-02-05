let Looper = android.os.Looper;
let Toast = android.widget.Toast;
let Runnable = java.lang.Runnable;
let ScriptInterruptedException = com.stardust.autojs.runtime.exception.ScriptInterruptedException;
let ReentrantLock = java.util.concurrent.locks.ReentrantLock;

module.exports = function (runtime, global) {
    let _ = {
        buildTypes: {
            release: 100, beta: 50, alpha: 0,
        },
        ensureNonUiThread() {
            if (ui.isUiThread()) {
                throw new Error('不能在ui线程执行阻塞操作，请在子线程或子脚本执行，或者使用setInterval循环检测当前activity和package');
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
            const m = /(\d+)\.(\d+)\.(\d+)[ ]?(Alpha|Beta)?(\d*)/.exec(v);
            if (!m) {
                throw new Error('版本格式不合法: ' + v);
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
        toasts: {
            /**
             * @type {android.widget.Toast[]}
             */
            pool: [],
            lock: new ReentrantLock(),
            add(t) {
                if (t instanceof Toast) {
                    this.lock.lock();
                    this.pool.push(t);
                    this.lock.unlock();
                }
            },
            dismiss() {
                this.lock.lock();
                this.pool.forEach((t) => {
                    t.cancel();
                });
                this.pool.splice(0);
                this.lock.unlock();
            },
        },
    };

    Object.assign(global, {
        de: Packages.de,
        okhttp3: Packages.okhttp3,
        androidx: Packages.androidx,
        toast(msg, is_long, is_forcible) {
            let $ = {
                toast() {
                    this.init(arguments);
                    this.show();
                },
                init() {
                    this.message = isNullish(msg) ? '' : msg.toString();
                    this.is_long = this.parseIsLong(is_long);
                    this.is_forcible = is_forcible;
                },
                parseIsLong(is_long) {
                    if (typeof is_long === 'number') {
                        return Number(!!is_long);
                    }
                    if (typeof is_long === 'string') {
                        return Number(/^l(ong)?$/i.test(is_long));
                    }
                    if (typeof is_long === 'boolean') {
                        return Number(is_long);
                    }
                    return 0;
                },
                show() {
                    ui.post(() => {
                        new android.os.Handler(Looper.getMainLooper()).post(new Runnable({
                            run() {
                                if ($.is_forcible) {
                                    $.dismiss();
                                }
                                $.toastObj = Toast.makeText(context, $.message, $.is_long);
                                _.toasts.add($.toastObj);
                                $.toastObj.show();
                            },
                        }));
                    });
                },
            };

            $.toast();
        },
        toastLog(msg, is_long, is_forcible) {
            this.toast.apply(this, arguments);
            this.log(msg);
        },
        sleep(millis_min, millis_max) {
            let $ = {
                rex_num: /[+-]?(\d+(\.\d+)?(e\d+)?)/,
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
                            runtime.sleep(this.min + this.rand_bound);
                        } catch (e) {
                            if (!(e.javaException instanceof ScriptInterruptedException)) {
                                throw e;
                            }
                        }
                    }
                },
                trigger() {
                    if (ui.isUiThread()) {
                        throw Error('不能在ui线程执行阻塞操作，请使用setTimeout代替');
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
                        throw TypeError('Type of millis_min must be a number');
                    }
                    this.min = min;
                },
                parseMax(max) {
                    if (typeof max === 'number') {
                        this.max = max;
                    } else if (typeof max === 'string') {
                        let matched = max.match(this.rex_num);
                        if (matched === null) {
                            throw TypeError('String millis_max must have a number contained');
                        }
                        let delta = Number(matched[0]);
                        this.max = this.min + delta;
                        this.min = this.min - delta;
                    } else {
                        this.max = this.min;
                    }
                },
                parseRandBound() {
                    this.rand_bound = Math.ceil(Math.random() * (this.max - this.min));
                },
            };

            (function $LazY() {
                return this.sleep = $.sleep.bind($);
            }).call(this).call(null, millis_min, millis_max);
        },
        isStopped() {
            return runtime.isStopped();
        },
        notStopped() {
            return !this.isStopped();
        },
        isRunning() {
            return this.notStopped();
        },
        exit() {
            return runtime.exit.apply(runtime, arguments);
        },
        stop() {
            this.exit();
        },
        setClip(text) {
            return runtime.setClip(text);
        },
        getClip() {
            return runtime.getClip();
        },
        currentPackage() {
            this.auto();
            return runtime.info.getLatestPackage();
        },
        currentActivity() {
            this.auto();
            return runtime.info.getLatestActivity();
        },
        waitForActivity(activity, period) {
            _.ensureNonUiThread();
            period = period || 200;
            while (this.currentActivity() !== activity) {
                sleep(period);
            }
        },
        waitForPackage(packageName, period) {
            _.ensureNonUiThread();
            period = period || 200;
            while (this.currentPackage() !== packageName) {
                sleep(period);
            }
        },
        random(min, max) {
            if (arguments.length === 0) {
                return Math.random();
            }
            return Math.floor(Math.random() * (max - min + 1)) + min;
        },
        setScreenMetrics() {
            return runtime.setScreenMetrics.apply(runtime, arguments);
        },
        requiresApi() {
            return runtime.requiresApi.apply(runtime, arguments);
        },
        requiresAutojsVersion(version) {
            if (typeof version === 'number') {
                if (_.compare(version, app.autojs.versionCode) > 0) {
                    throw new Error('需要Auto.js版本号' + version + '以上才能运行');
                }
            } else {
                if (_.compareVersion(version, app.autojs.versionName) > 0) {
                    throw new Error('需要Auto.js版本' + version + '以上才能运行');
                }
            }
        },
        isPlainObject(o) {
            return Object.prototype.toString.call(o).slice(8, -1) === 'Object';
        },
        isInteger(o) {
            return Number.isInteger(o);
        },
        isNullish(o) {
            // nullish coalescing operator: ??
            return o === null || o === undefined;
        },
        isPrimitive(o) {
            return o !== Object(o);
        },
        isReference(o) {
            return o === Object(o);
        },
        $bind() {
            this.toast.dismiss = function () {
                ui.post(() => {
                    new android.os.Handler(Looper.getMainLooper()).post(new Runnable({
                        run() {
                            _.toasts.dismiss();
                        },
                    }));
                });
            };
            return this;
        },
    }.$bind());
};