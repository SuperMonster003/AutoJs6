// The cases of RhinoAsyncDeviceTest (async functions of Rhino in the production engine). The same file runs in the app,
// from the instrumented test, and in an inrt APK, from tools/rhino/device_rhino_check.py --inrt. A case returns its value
// or a Promise of it, so a case runs on the thread that started it until the Promise settles.
// zh-CN: RhinoAsyncDeviceTest 的用例 (生产引擎中 Rhino 的 async 函数). 同一份文件在 app 中由设备测试运行, 在 inrt APK 中由
// tools/rhino/device_rhino_check.py --inrt 运行. 用例返回结果或结果的 Promise, 启动用例的线程一直运行到该 Promise 落定.

var rhinoAsyncCases = (function () {
    var all = [];
    var CASE_LIMIT_MILLIS = 30000;

    function add(name, expected, run) {
        all.push({ name: name, expected: expected, run: run });
    }

    function delay(millis) {
        return new Promise(function (resolve) { setTimeout(resolve, millis); });
    }

    function threadId() {
        return java.lang.Thread.currentThread().getId();
    }

    function javaThread(run) {
        new java.lang.Thread(new java.lang.Runnable({ run: run })).start();
    }

    // Answers one request on a free port of the device.
    function serve(body) {
        var crlf = String.fromCharCode(13, 10);
        var server = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName('127.0.0.1'));
        javaThread(function () {
            var socket = server.accept();
            var reader = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()));
            var line = reader.readLine();
            while (line != null && String(line).length > 0) line = reader.readLine();
            var head = ['HTTP/1.1 200 OK', 'Content-Length: ' + body.length, 'Connection: close', '', ''].join(crlf);
            socket.getOutputStream().write(new java.lang.String(head + body).getBytes('UTF-8'));
            socket.getOutputStream().flush();
            socket.close();
            server.close();
        });
        return server.getLocalPort();
    }

    add('microtasksBeforeTimers', 'sync|await|then|then of then|timeout|then of timeout|second timeout', async function () {
        var log = [];
        function push(line) { return function () { log.push(line); }; }
        setTimeout(function () {
            log.push('timeout');
            Promise.resolve().then(push('then of timeout'));
        }, 0);
        setTimeout(push('second timeout'), 0);
        (async function () { await null; log.push('await'); })();
        Promise.resolve().then(push('then')).then(push('then of then'));
        log.push('sync');
        await delay(50);
        return log.join('|');
    });

    add('awaitTimer', 'late|true|caught no', async function () {
        var from = Date.now();
        var value = await new Promise(function (resolve) { setTimeout(function () { resolve('late'); }, 30); });
        var elapsed = Date.now() - from, caught;
        try {
            await new Promise(function (resolve, reject) { setTimeout(function () { reject(new Error('no')); }, 1); });
        } catch (e) {
            caught = 'caught ' + e.message;
        }
        return [value, elapsed >= 25, caught].join('|');
    });

    add('hostPromiseOrder', 'sync|await v|then v|timeout', async function () {
        var log = [];
        var adapter = new org.autojs.autojs.runtime.api.ScriptPromiseAdapter();
        var promise = ResultAdapter.promise(adapter);
        (async function () { log.push('await ' + await promise); })();
        promise.then(function (value) { log.push('then ' + value); });
        setTimeout(function () { log.push('timeout'); }, 0);
        adapter.resolve('v');
        log.push('sync');
        await delay(30);
        return log.join('|');
    });

    add('awaitHostPromise', 'true|true|true|200|served|true|6', async function () {
        var from = threadId();
        var promise = http.getAsync('http://127.0.0.1:' + serve('served') + '/');
        var kinds = [promise instanceof Promise, typeof promise.await, typeof promise.wait].map(function (kind) {
            return kind === true || kind === 'function';
        });
        var response = await promise;
        var body = String(response.body.string());
        return kinds.concat(response.statusCode, body, threadId() === from, await flow.of(6).toPromise()).join('|');
    });

    add('awaitJavaFuture', 'java.util.concurrent.CompletableFuture', async function () {
        var future = java.util.concurrent.CompletableFuture.completedFuture(1);
        return String((await future).getClass().getName());
    });

    add('settledByOtherThreads', 'java|true|threads|true', async function () {
        var from = threadId();
        var java_ = await new Promise(function (resolve) { javaThread(function () { resolve('java'); }); });
        var afterJava = threadId() === from;
        var threads_ = await new Promise(function (resolve) { threads.start(function () { resolve('threads'); }); });
        return [java_, afterJava, threads_, threadId() === from].join('|');
    });

    add('threadsAsyncFunction', 'true|true|true|caller|true', async function () {
        var settle;
        var fromCaller = new Promise(function (resolve) { settle = resolve; });
        var ended = new Promise(function (resolve) {
            threads.start(function () {
                var me = threadId(), log = [];
                // A pending Promise does not keep a thread running.
                var keep = setInterval(function () {}, 20);
                (async function () {
                    log.push(threadId() === me);
                    await null;
                    log.push(threadId() === me);
                    await delay(5);
                    log.push(threadId() === me);
                    log.push(await fromCaller);
                    log.push(threadId() === me);
                    clearInterval(keep);
                    resolve(log.join('|'));
                })();
            });
        });
        await delay(30);
        settle('caller');
        return await ended;
    });

    add('forAwait', '1|2|3|done|a|b|x|y|stopped', async function () {
        var log = [];
        async function* numbers() {
            yield 1;
            await delay(1);
            yield 2;
            yield Promise.resolve(3);
        }
        for await (var number of numbers()) log.push(number);
        log.push('done');
        for await (var item of [Promise.resolve('a'), 'b']) log.push(item);
        var custom = {
            [Symbol.asyncIterator]() {
                var left = ['x', 'y'];
                return {
                    next() { return Promise.resolve({ done: left.length === 0, value: left.shift() }); },
                    return() { log.push('stopped'); return Promise.resolve({}); },
                };
            },
        };
        for await (var text of custom) {
            log.push(text);
            if (text === 'y') break;
        }
        return log.join('|');
    });

    add('asyncGenerator', '1 false|cleanup|9 true|undefined true|cleanup|threw no|function', async function () {
        var log = [];
        function show(step) { return step.value + ' ' + step.done; }
        async function* guarded() {
            try {
                yield 1;
                yield 2;
            } finally {
                log.push('cleanup');
            }
        }
        var iterator = guarded();
        log.push(show(await iterator.next()));
        log.push(show(await iterator.return(9)));
        log.push(show(await iterator.next()));
        var other = guarded();
        await other.next();
        try {
            await other.throw(new Error('no'));
        } catch (e) {
            log.push('threw ' + e.message);
        }
        log.push(typeof iterator[Symbol.asyncIterator]);
        return log.join('|');
    });

    add('classAsyncMethods', '3|3|3,3|true', async function () {
        class Base {
            async value() {
                await null;
                return 1;
            }
        }
        class Derived extends Base {
            #extra = 2;
            async value() { return (await super.value()) + this.#extra; }
            static async create() { return new this(); }
            async *values() {
                yield await this.value();
                yield await this.value();
            }
            get later() { return (async () => await this.value())(); }
        }
        var derived = await Derived.create(), values = [];
        for await (var value of derived.values()) values.push(value);
        return [await derived.value(), await derived.later, values.join(), derived.value() instanceof Promise].join('|');
    });

    add('optionalCallOfMissingMethod', 'TypeError|undefined|undefined', function () {
        var a = {}, none, caught;
        try {
            a?.b();
        } catch (e) {
            caught = e.name;
        }
        return [caught, String(a.b?.()), String(none?.b.c)].join('|');
    });

    add('switchCaseFunctionOnEntry', 'function', function () {
        function f() {
            switch (1) {
                case 0:
                    function inner() {}
                case 1:
                    return typeof inner;
            }
        }
        return f();
    });

    add('blockFunctionFromOutside', 'undefined|function|function', function () {
        function f() {
            function outside() { return typeof inner; }
            var before, within;
            {
                before = outside();
                within = typeof inner;
                function inner() {}
            }
            return [before, within, outside()].join('|');
        }
        return f();
    });

    async function verdict(item) {
        // A pending Promise does not keep a script running.
        var keep = setInterval(function () {}, 20), limit, actual;
        var limited = new Promise(function (resolve) {
            limit = setTimeout(function () { resolve('no end in ' + CASE_LIMIT_MILLIS + ' ms'); }, CASE_LIMIT_MILLIS);
        });
        try {
            actual = String(await Promise.race([item.run(), limited]));
        } catch (e) {
            actual = 'threw ' + e;
        }
        clearTimeout(limit);
        clearInterval(keep);
        return actual === item.expected ? 'PASS' : 'FAIL expected <' + item.expected + '> got <' + actual + '>';
    }

    return {
        run: function (name, report) {
            verdict(all.filter(function (item) { return item.name === name; })[0]).then(report);
        },
        runAll: function (report, done) {
            var index = 0;
            function next() {
                if (index === all.length) {
                    done();
                    return;
                }
                var item = all[index++];
                verdict(item).then(function (line) {
                    report(item.name + ' ' + line);
                    next();
                });
            }
            next();
        },
    };
})();
