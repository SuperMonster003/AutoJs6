// noinspection JSUnusedGlobalSymbols

(function () {
    const System = java.lang.System;
    const Runtime = java.lang.Runtime;

    const startNanos = System.nanoTime();
    const listeners = {};

    function javaMapToObject(map) {
        const obj = {};
        try {
            const iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                const entry = iterator.next();
                obj[String(entry.getKey())] = String(entry.getValue());
            }
        } catch (_) {
            // Ignore restricted environment maps.
        }
        return obj;
    }

    function emit(type, args) {
        const callbacks = (listeners[type] || []).slice();
        callbacks.forEach((callback) => callback.apply(process, args));
        return callbacks.length > 0;
    }

    function addListener(type, callback, once) {
        if (typeof callback !== 'function') {
            throw new TypeError('listener must be a function');
        }
        const key = String(type);
        const wrapper = once
            ? function () {
                process.removeListener(key, wrapper);
                return callback.apply(this, arguments);
            }
            : callback;
        wrapper.listener = callback;
        (listeners[key] || (listeners[key] = [])).push(wrapper);
        return process;
    }

    const process = global.process || {};

    Object.assign(process, {
        title: 'AutoJs6 Rhino Node.js compatibility runtime',
        browser: false,
        platform: 'android',
        arch: String(System.getProperty('os.arch') || ''),
        version: 'v0.0.0-rhino',
        versions: {
            node: '0.0.0-rhino',
            rhino: String((function () {
                try {
                    return org.mozilla.javascript.Context.getCurrentContext().getImplementationVersion();
                } catch (_) {
                    return 'unknown';
                }
            })()),
        },
        release: {
            name: 'node',
        },
        env: process.env || javaMapToObject(System.getenv()),
        argv: process.argv || [],
        execArgv: process.execArgv || [],
        exitCode: process.exitCode || 0,
        pid: (function () {
            try {
                return android.os.Process.myPid();
            } catch (_) {
                return 0;
            }
        })(),
        cwd() {
            return String(runtime.files.cwd() || '');
        },
        chdir() {
            throw new Error('process.chdir() is not supported by AutoJs6 Rhino compatibility mode');
        },
        uptime() {
            return (System.nanoTime() - startNanos) / 1000000000;
        },
        hrtime(previous) {
            let nanos = System.nanoTime() - startNanos;
            if (previous && previous.length >= 2) {
                nanos -= (Number(previous[0]) * 1000000000) + Number(previous[1]);
            }
            const seconds = Math.floor(nanos / 1000000000);
            return [ seconds, nanos - seconds * 1000000000 ];
        },
        memoryUsage() {
            const rt = Runtime.getRuntime();
            return {
                rss: rt.totalMemory(),
                heapTotal: rt.totalMemory(),
                heapUsed: rt.totalMemory() - rt.freeMemory(),
                external: 0,
                arrayBuffers: 0,
            };
        },
        nextTick(callback) {
            if (typeof callback !== 'function') {
                throw new TypeError('callback must be a function');
            }
            const args = Array.prototype.slice.call(arguments, 1);
            return setTimeout(function () {
                callback.apply(process, args);
            }, 0);
        },
        exit(code) {
            process.exitCode = code == null ? 0 : Number(code) | 0;
            runtime.exit();
        },
        on(type, callback) {
            return addListener(type, callback, false);
        },
        addListener(type, callback) {
            return addListener(type, callback, false);
        },
        once(type, callback) {
            return addListener(type, callback, true);
        },
        removeListener(type, callback) {
            const key = String(type);
            const callbacks = listeners[key];
            if (!callbacks) {
                return process;
            }
            listeners[key] = callbacks.filter((fn) => fn !== callback && fn.listener !== callback);
            return process;
        },
        off(type, callback) {
            return process.removeListener(type, callback);
        },
        removeAllListeners(type) {
            if (type == null) {
                Object.keys(listeners).forEach((key) => delete listeners[key]);
            } else {
                delete listeners[String(type)];
            }
            return process;
        },
        emit(type) {
            return emit(String(type), Array.prototype.slice.call(arguments, 1));
        },
        listenerCount(type) {
            return (listeners[String(type)] || []).length;
        },
    });

    if (!process.stdout) {
        process.stdout = {
            isTTY: false,
            write(value) {
                console.log(String(value).replace(/\n$/, ''));
                return true;
            },
        };
    }
    if (!process.stderr) {
        process.stderr = {
            isTTY: false,
            write(value) {
                console.error(String(value).replace(/\n$/, ''));
                return true;
            },
        };
    }
    if (!process.stdin) {
        process.stdin = {
            isTTY: false,
            read() {
                return null;
            },
        };
    }

    global.process = process;
    module.exports = process;
})();
