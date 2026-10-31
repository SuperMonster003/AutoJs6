// The cases of RhinoClassSyntaxDeviceTest (Rhino class syntax in the production engine). The same file runs in the app,
// from the instrumented test, and in an inrt APK, from tools/rhino/device_rhino_check.py --inrt: the adapter classes of
// the classes that extend Java classes go through the D8 of the host on both paths.
// zh-CN: RhinoClassSyntaxDeviceTest 的用例 (生产引擎中的 Rhino class 语法). 同一份文件在 app 中由设备测试运行, 在 inrt APK 中由
// tools/rhino/device_rhino_check.py --inrt 运行: 继承 Java 类的 class, 其适配器类在两条路径上都经宿主的 D8 转换.

var rhinoClassCases = (function () {
    var all = [];

    function add(name, expected, run) {
        all.push({ name: name, expected: expected, run: run });
    }

    var CountDownLatch = java.util.concurrent.CountDownLatch;
    var SECONDS = java.util.concurrent.TimeUnit.SECONDS;
    var Looper = android.os.Looper;

    // Built by tools/rhino/build_class_fixtures.py.
    var FIXTURE_DEX = 'ZGV4CjAzNwAEsDHW/kWVYZF4/31ks9aNI6HazBm92QWwBAAAcAAAAHhWNBIAAAAAAAAAABAEAAAVAAAAcAAAAAUAAADEAAAABAAAANgAAAABAAAACAEAAAgAAAAQAQAAAQAAAFABAABAAwAAcAEAAGoCAAByAgAAdQIAAHkCAACNAgAAoQIAALwCAADtAgAACAMAAAsDAAAPAwAAEgMAABUDAAAdAwAAJwMAAC4DAAA3AwAAQQMAAEkDAABPAwAAWQMAAAMAAAAEAAAABQAAAAYAAAAIAAAAAQAAAAEAAAAAAAAAAgAAAAIAAABkAgAACAAAAAQAAAAAAAAACQAAAAQAAABkAgAAAwABABIAAAAAAAIAAAAAAAIAAgAAAAAAAgABAAwAAAACAAAAEwAAAAMAAwAAAAAAAwAAAA0AAAADAAAADgAAAAMAAAAPAAAAAwAAAAEAAAAAAAAAAAAAAAcAAAAAAAAA+AMAAAAAAAADAAEAAgAAAFACAAAeAAAAIgACAHAQAQAAABoBCgBuIAIAEAAMAG4QBgACAAwBbiACABAADAAaAQsAbiACABAADABuEAMAAAAMABEAAwABAAIAAABUAgAAFgAAACIAAgBwEAEAAAAaAREAbiACABAADABUIQAAbiACABAADABuEAMAAAAMABEAAwABAAIAAABYAgAAFgAAACIAAgBwEAEAAAAaARAAbiACABAADABUIQAAbiACABAADABuEAMAAAAMABEAAgACAAEAAABcAgAABgAAAHAQAAAAAFsBAAAOABMADgAPAA4AFwAOAAoBAA48LQAAAQAAAAEABjxpbml0PgABTAACTEwAEkxqYXZhL2xhbmcvT2JqZWN0OwASTGphdmEvbGFuZy9TdHJpbmc7ABlMamF2YS9sYW5nL1N0cmluZ0J1aWxkZXI7AC9Mb3JnL2F1dG9qcy9yaGluby9maXh0dXJlL1JoaW5vQ2xhc3NEZXhGaXh0dXJlOwAZUmhpbm9DbGFzc0RleEZpeHR1cmUuamF2YQABVgACVkwAAVsAAV0ABmFwcGVuZAAIZGVzY3JpYmUABWdyZWV0AAdndWFyZGVkAAhndWFyZGVkIAAGaGVsbG8gAARuYW1lAAh0b1N0cmluZwCcAX5+RDh7ImJhY2tlbmQiOiJkZXgiLCJjb21waWxhdGlvbi1tb2RlIjoiZGVidWciLCJoYXMtY2hlY2tzdW1zIjpmYWxzZSwibWluLWFwaSI6MjQsInNoYS0xIjoiMDg0YTg5MTI2YWUwNTk2YzU5OTE0M2RmNTdhNjU0NjQ2YWYyODMxMyIsInZlcnNpb24iOiI5LjIuNC1kZXYifQAAAQEDABIEgYAEtAQFAfACAQG8AwEE+AMNAAAAAAAAAAEAAAAAAAAAAQAAABUAAABwAAAAAgAAAAUAAADEAAAAAwAAAAQAAADYAAAABAAAAAEAAAAIAQAABQAAAAgAAAAQAQAABgAAAAEAAABQAQAAASAAAAQAAABwAQAAAyAAAAQAAABQAgAAARAAAAEAAABkAgAAAiAAABUAAABqAgAAACAAAAEAAAD4AwAAABAAAAEAAAAQBAAA';
    var FIXTURE_JAR = 'UEsDBBQAAAAIAPMWYV2PRlMEpgEAAN4CAAAzAAAAb3JnL2F1dG9qcy9yaGluby9maXh0dXJlL1JoaW5vQ2xhc3NKYXJGaXh0dXJlLmNsYXNzfVHbTttAED2bmx3HEAiXkJK23ElcgR/atyAeiJSHClGpqZCqqg+beDEbGRtt7Kqf1UolSCDxAXxU1dkQgkRCX+ZyZs6Zmd37vzd3AN7DsZBC2kDGRhY5hrke/8HdgIe++6nTE92YIXcgQxkfMqRr9dM8TOQNWDYKsBn2IuW7PImjXt9V5zKM3DP5M06UcD/rrBnwfv8jV60HkCET8gtypeOnMe1YydBvGJhlKD+HjxIZeEJZmEHaxDwtcy6CIFrTwIKNRSwRxC8vRejRMrVJ2foENJJsaImyjRVUGMw4eigyLNYmKQ0Tqwzsm0XHv7bxRlOyvhIiNrFGhe8mNkjET7jyhEfI0rRNTun8ZuTR+cVjGYqT5KIj1BfeCQgxPdHvKtmh0BjJMFjtKFFd0ZK6ozLtQff1FKzTWin6TXpX/SsUZSjOYpPsFmUOVXXdcq7BnHdXMH4Pu7fJziKtK8QoIE9cpq8b8VxiMfLlW8x8vUaxNDdAyfkDg9wAy7/GIrlhW5Hszpj8YUSuPpJfafIAVXKlt9MVFmiN3ZfHr/93fJlsjXwK9X9QSwECFAAUAAAACADzFmFdj0ZTBKYBAADeAgAAMwAAAAAAAAAAAAAAgAEAAAAAb3JnL2F1dG9qcy9yaGluby9maXh0dXJlL1JoaW5vQ2xhc3NKYXJGaXh0dXJlLmNsYXNzUEsFBgAAAAABAAEAYQAAAPcBAAAAAA==';

    add('classCore', 'renamed(1, 2)|shape point!|renamed|Point|true|Point|2|function|TypeError|true', function () {
        class Shape {
            constructor(name) {
                this.name = name;
                this.target = new.target.name;
            }
            static create(...args) { return new this(...args); }
            describe() { return this.name; }
            get label() { return 'shape ' + this.name; }
            set label(value) { this.name = value; }
        }
        class Point extends Shape {
            constructor(x, y) {
                super('point');
                this.x = x;
                this.y = y;
            }
            describe() { return super.describe() + '(' + this.x + ', ' + this.y + ')'; }
            get label() { return super.label + '!'; }
            set label(value) { super.label = value; }
        }
        var p = Point.create(1, 2), before = p.label, threw;
        p.label = 'renamed';
        try { Point(1, 2); } catch (e) { threw = e.name; }
        return [p.describe(), before, p.name, p.target, p instanceof Shape, Point.name, Point.length, typeof Point, threw,
            String(Shape).indexOf('class Shape') === 0].join('|');
    });

    add('fieldsAndPrivateNames', '9|2|12|true|false|count|TypeError', function () {
        class Counter {
            static #instances = 0;
            static created = 'static field';
            static { Counter.ready = Counter.created.length; }
            count = 1;
            #step = 2;
            #double() { return this.#step * 2; }
            get #doubled() { return this.#double(); }
            next() {
                this.count += this.#doubled;
                Counter.#instances++;
                return this;
            }
            static instances() { return Counter.#instances; }
            static isCounter(o) { return #step in o; }
        }
        var c = new Counter().next().next(), threw;
        try { Counter.prototype.next.call({}); } catch (e) { threw = e.name; }
        return [c.count, Counter.instances(), Counter.ready, Counter.isCounter(c), Counter.isCounter({}),
            Object.keys(c).join(), threw].join('|');
    });

    add('builtinSubclasses', 'true|true|boom|AppError: boom|7|string|true|3|3|true|6|true|5|true|2|2|a+b|2|true|true|true', function () {
        class AppError extends Error {
            constructor(message, code) {
                super(message);
                this.name = 'AppError';
                this.code = code;
            }
        }
        class Stack extends Array {
            peek() { return this[this.length - 1]; }
        }
        class Counts extends Map {
            bump(key) { return this.set(key, (this.get(key) || 0) + 1); }
        }
        class Uniques extends Set {}
        class Deferred extends Promise {}
        var e = new AppError('boom', 7);
        var s = new Stack();
        s.push(1, 2, 3);
        var mapped = s.map(function (x) { return x * 2; });
        var c = new Counts();
        c.bump('a').bump('a').bump('b');
        var u = new Uniques([1, 1, 2]);
        var d = new Deferred(function (resolve) { resolve(1); });
        return [e instanceof AppError, e instanceof Error, e.message, String(e), e.code, typeof e.stack, e.stack.length > 0,
            s.length, s.peek(), mapped instanceof Stack, mapped.peek(), Array.isArray(s), Stack.from([5]).peek(),
            c instanceof Counts, c.size, c.get('a'), [...c.keys()].join('+'), u.size, u instanceof Set,
            d instanceof Deferred, d.then(function (x) { return x; }) instanceof Deferred].join('|');
    });

    add('extendsThread', '1|w1:x|true|true|false', function () {
        class Worker extends java.lang.Thread {
            count = 0;
            #secret = 'x';
            constructor(name) { super(name); }
            run() {
                this.count++;
                this.log = this.getName() + ':' + this.#secret;
            }
        }
        var w = new Worker('w1');
        w.start();
        w.join();
        return [w.count, w.log, w instanceof Worker, w instanceof java.lang.Thread, w.isAlive()].join('|');
    });

    add('extendsHandler', 'true|7|true|true', function () {
        var latch = new CountDownLatch(1), seen = {};
        class Inbox extends android.os.Handler {
            handleMessage(message) {
                seen.what = message.what;
                seen.main = Looper.myLooper().equals(Looper.getMainLooper());
                latch.countDown();
            }
        }
        var inbox = new Inbox(Looper.getMainLooper());
        inbox.sendEmptyMessage(7);
        return [latch.await(10, SECONDS), seen.what, seen.main, inbox instanceof android.os.Handler].join('|');
    });

    add('implementsRunnable', 'true|main+plain|true|true', function () {
        var latch = new CountDownLatch(2), names = new java.util.concurrent.ConcurrentSkipListSet();
        class Task extends java.lang.Runnable {
            run() {
                names.add(java.lang.Thread.currentThread().getName());
                latch.countDown();
            }
        }
        var task = new Task();
        new android.os.Handler(Looper.getMainLooper()).post(task);
        new java.lang.Thread(task, 'plain').start();
        return [latch.await(10, SECONDS), Array.from(names).join('+'), task instanceof java.lang.Runnable, task instanceof Task].join('|');
    });

    add('severalInterfaces', 'true|true|true|true', function () {
        class Both extends JavaAdapter(java.lang.Object, java.lang.Runnable, java.lang.Comparable) {
            value = 0;
            run() { this.value++; }
            compareTo(other) { return this.value - other.value; }
        }
        var a = new Both(), b = new Both(), list = new java.util.ArrayList();
        b.run();
        list.add(b);
        list.add(a);
        java.util.Collections.sort(list);
        return [list.get(0) === a, list.get(1) === b, a instanceof java.lang.Comparable, a instanceof Both].join('|');
    });

    add('abstractJavaClass', 'true|true', function () {
        var latch = new CountDownLatch(1);
        class Tick extends java.util.TimerTask {
            run() { latch.countDown(); }
        }
        var timer = new java.util.Timer(true), tick = new Tick();
        timer.schedule(tick, 0);
        var done = latch.await(10, SECONDS);
        timer.cancel();
        return [done, tick instanceof java.util.TimerTask].join('|');
    });

    add('javaCollectionSubclass', '2|6|2|4+6|true', function () {
        class Doubled extends java.util.ArrayList {
            add(value) { return super.add(value * 2); }
        }
        class Sized extends Doubled {
            count() { return super.size(); }
        }
        var d = new Sized(), seen = [];
        d.add(2);
        d.add(3);
        for (var x of d) seen.push(Number(x));
        return [d.count(), Number(d.get(1)), new java.util.ArrayList(d).size(), seen.join('+'), d instanceof java.util.List].join('|');
    });

    // Appendix C Q29 of the Rhino roadmap: what the host hands to scripts works with the iterator protocol, which
    // array destructuring uses as well; an array-like object without Symbol.iterator is a TypeError there.
    // zh-CN: Rhino 路线图附录 C Q29: 宿主交给脚本的集合都走迭代器协议 (数组解构同样如此); 没有 Symbol.iterator 的类数组对象在这里报 TypeError.
    add('hostCollections', 'all iterable|TypeError|TypeError|1', function () {
        var UiObjectCollection = Packages.org.autojs.autojs.core.automator.UiObjectCollection;
        var MatchingResult = Packages.org.autojs.autojs.runtime.api.modules.images.MatchingResult;
        var nodes = new java.util.ArrayList(), javaSet = new java.util.LinkedHashSet(), javaMap = new java.util.LinkedHashMap();
        nodes.add(null);
        nodes.add(null);
        javaSet.add('a');
        javaMap.put('k', 1);
        var directory = context.getFilesDir().getParent();
        var listed = files.listDir(directory);
        function args() { return arguments; }
        var rows = {
            'UiObjectCollection': [UiObjectCollection.of(nodes), 2],
            'UiObjectCollection.EMPTY': [UiObjectCollection.Companion.getEMPTY(), 0],
            'NativeArray (engines.all)': [engines.all(), engines.all().length],
            'NativeArray (colors.toRgb)': [colors.toRgb('#102030'), 3],
            'Java String[] (files.listDir)': [listed, listed.length],
            'Java int[]': [java.lang.reflect.Array.newInstance(java.lang.Integer.TYPE, 3), 3],
            'Java Object[] (List.toArray)': [nodes.toArray(), 2],
            'java.util.List': [java.util.Arrays.asList('a', 'b'), 2],
            'kotlin EmptyList': [Packages.kotlin.collections.CollectionsKt.emptyList(), 0],
            'java.util.Set': [javaSet, 1],
            'java.util.Map': [javaMap, 1],
            'MatchingResult.matches': [new MatchingResult(new java.util.ArrayList()).matches, 0],
            'MatchingResult.points': [new MatchingResult(new java.util.ArrayList()).points, 0],
            'arguments': [args(1, 2), 2],
            'Set': [new Set([1]), 1],
            'Map': [new Map([[1, 2]]), 1],
            'String': ['ab', 2],
            'Uint8Array': [new Uint8Array(2), 2],
        };
        var broken = [];
        Object.keys(rows).forEach(function (name) {
            var value = rows[name][0], count = rows[name][1];
            try {
                var viaForOf = 0;
                for (var item of value) viaForOf++;
                var viaSpread = [...value], viaFrom = Array.from(value), [first] = value;
                if (viaForOf !== count || viaSpread.length !== count || viaFrom.length !== count || (count > 0 && first === undefined)) {
                    broken.push(name + ': ' + [viaForOf, viaSpread.length, viaFrom.length, first]);
                }
            } catch (e) {
                broken.push(name + ': ' + e);
            }
        });
        var destructured, spread;
        try { var [a] = { 0: 1, length: 1 }; destructured = a; } catch (e) { destructured = e.name; }
        try { spread = [...{ 0: 1, length: 1 }]; } catch (e) { spread = e.name; }
        return [broken.length ? broken.join('; ') : 'all iterable', destructured, spread, Array.from({ 0: 1, length: 1 }).length].join('|');
    });

    function fixture(base64, suffix) {
        var file = new java.io.File(context.getCacheDir(), 'rhino-class-' + java.lang.System.nanoTime() + suffix);
        var out = new java.io.FileOutputStream(file);
        out.write(android.util.Base64.decode(base64, 0));
        out.close();
        return file;
    }

    function greeter(Base, name) {
        class Greeter extends Base {
            greet() { return super.greet() + '!'; }
            reveal() { return this.guarded(); }
        }
        var g = new Greeter(name);
        return [g.describe(), g.reveal(), g instanceof Base, g instanceof Greeter].join('|');
    }

    add('extendsClassFromLoadDex', '[hello dex!]|guarded dex|true|true', function () {
        var file = fixture(FIXTURE_DEX, '.dex');
        try {
            runtime.loadDex(file.getPath());
            return greeter(Packages.org.autojs.rhino.fixture.RhinoClassDexFixture, 'dex');
        } finally {
            file.delete();
        }
    });

    add('extendsClassFromLoadJar', '[hello jar!]|guarded jar|true|true', function () {
        var file = fixture(FIXTURE_JAR, '.jar');
        try {
            runtime.loadJar(file.getPath());
            return greeter(Packages.org.autojs.rhino.fixture.RhinoClassJarFixture, 'jar');
        } finally {
            file.delete();
        }
    });

    function verdict(item) {
        var actual;
        try {
            actual = String(item.run());
        } catch (e) {
            actual = 'threw ' + e;
        }
        return actual === item.expected ? 'PASS' : 'FAIL expected <' + item.expected + '> got <' + actual + '>';
    }

    return {
        run: function (name) {
            return verdict(all.filter(function (item) { return item.name === name; })[0]);
        },
        runAll: function (report) {
            all.forEach(function (item) { report(item.name + ' ' + verdict(item)); });
        },
    };
})();
