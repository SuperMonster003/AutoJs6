// ROADMAP P2.5 probe: the startup objects moved to Kotlin (__exitIfError__, __asGlobal__, continuation.await / delay,
// ResultAdapter) and the polyfills installed on first access (Object.observe, Array.observe, structuredClone).
// Each line is "AJ6P25 <name> <JSON value or throws ...>"; the first lines read the array methods and the key order
// before any polyfill name is read. Run by device_bootstrap_check.py.
var MARK = 'AJ6P25';

function rec(name, f) {
    var v;
    try {
        v = JSON.stringify(f());
    } catch (e) {
        v = 'throws ' + (e && e.name) + ': ' + (e && e.message);
    }
    console.log(MARK + ' ' + name + ' ' + v);
}

rec('push.initial', function () {
    return [Array.prototype.push.name, Array.prototype.push.hasOwnProperty('_original')];
});
rec('keys.global', function () {
    var k = Object.keys(global);
    var i = k.indexOf('__exitIfError__');
    return k.slice(i - 1, i + 6);
});
rec('keys.Object', function () {
    return Object.keys(Object);
});
rec('keys.Array', function () {
    return Object.keys(Array);
});
rec('global.descriptors', function () {
    return ['__exitIfError__', '__asGlobal__', 'ResultAdapter', 'structuredClone'].map(function (k) {
        var d = Object.getOwnPropertyDescriptor(global, k);
        var f = d.value;
        return [k, typeof f, f.name, f.length, f.hasOwnProperty('prototype'), d.enumerable, d.writable, d.configurable].join(' ');
    });
});
rec('push.afterStructuredClone', function () {
    return Array.prototype.push.name;
});
rec('observe.descriptors', function () {
    return [['Object', 'observe'], ['Object', 'unobserve'], ['Object', 'getNotifier'], ['Object', 'deliverChangeRecords'], ['Array', 'observe'], ['Array', 'unobserve']].map(function (p) {
        var d = Object.getOwnPropertyDescriptor(global[p[0]], p[1]);
        return [p.join('.'), typeof d.value, d.value.length, d.value.name, d.enumerable, d.writable, d.configurable].join(' ');
    });
});
rec('push.afterObserve', function () {
    return ['push', 'pop', 'shift', 'unshift', 'splice'].map(function (k) {
        return Array.prototype[k].name + ':' + Array.prototype[k].length + ':' + typeof Array.prototype[k]._original;
    });
});
rec('continuation.prototype', function () {
    var p = Object.getPrototypeOf(continuation);
    return Object.keys(p).map(function (k) {
        return k + ':' + p[k].length + ':' + p[k].hasOwnProperty('prototype');
    });
});
rec('ResultAdapter.shape', function () {
    var s = Object.getPrototypeOf(ResultAdapter);
    return [ResultAdapter.name, ResultAdapter.length, Object.keys(s).join(), Object.keys(ResultAdapter.prototype).join(), ResultAdapter instanceof Function, Object.getPrototypeOf(s) === Object.prototype];
});
rec('require', function () {
    return [require('continuation') === Object.getPrototypeOf(continuation), require('result-adapter') === ResultAdapter, typeof require('object-observe-lite.min'), typeof require('structured-clone.min')];
});
rec('require.internal', function () {
    return typeof require('internal');
});
rec('require.result_adapter', function () {
    return typeof require('result_adapter');
});
rec('exitIfError', function () {
    return __exitIfError__(function () {
        return 42;
    });
});
rec('asGlobal', function () {
    var o = { v: 5, f: function () { return this.v; } };
    __asGlobal__(o, ['f', { g: 'f' }]);
    return [f(), g()];
});
rec('observe.array', function () {
    var log = [];
    var h = function (cs) {
        cs.forEach(function (c) { log.push(c.type + ':' + c.index + ':' + c.addedCount); });
    };
    var a = [1];
    Array.observe(a, h);
    a.push(2);
    a.splice(0, 1);
    Object.deliverChangeRecords(h);
    Array.unobserve(a, h);
    return log;
});
rec('observe.object', function () {
    var log = [];
    var h = function (cs) {
        cs.forEach(function (c) { log.push(c.type + ':' + c.name); });
    };
    var o = { a: 1 };
    Object.observe(o, h);
    o.a = 2;
    o.b = 1;
    delete o.a;
    Object.deliverChangeRecords(h);
    Object.unobserve(o, h);
    return log;
});
rec('structuredClone', function () {
    var s = { a: [1, { b: 2 }], m: new Map([[1, 2]]) };
    s.self = s;
    var c = structuredClone(s);
    return [c !== s, c.a[1].b, c.m.get(1), c.self === c];
});
rec('ResultAdapter.promise', function () {
    return ResultAdapter.promise(new org.autojs.autojs.runtime.api.ScriptPromiseAdapter()) instanceof Promise;
});
rec('ResultAdapter.instance', function () {
    var a = new ResultAdapter();
    a.setResult(3);
    return [Object.keys(a).join(), a.get()];
});
// Settled on another thread, as the images / automator callers do.
rec('ResultAdapter.wait', function () {
    var a = new org.autojs.autojs.runtime.api.ScriptPromiseAdapter();
    threads.start(function () {
        sleep(200);
        a.resolve('waited');
    });
    return ResultAdapter.wait(a);
});
console.log(MARK + ' done');
