// ROADMAP P3.7 smoke of require: run by tools/augment/device_require_check.py, which checks the "AJ6REQ <item> <value>"
// lines against its expected values (until P5.2 it compared them with those of jvm-npm, the switch of the new script
// module layer off). Paths are printed relative to this directory ("<root>"); the items of the registered differences
// from jvm-npm (appendix E "P3.7") are listed in the tool.
var MARK = 'AJ6REQ';
var root = String(files.cwd());
var canonical = String(new java.io.File(root).getCanonicalPath());

function clean(text) {
    return String(text).split(canonical).join('<root>').split(root).join('<root>');
}

function describe(e) {
    if (e === null || typeof e !== 'object') {
        return 'value ' + String(e);
    }
    return (e instanceof Error ? '' : 'non-Error ') + e.name + ' [' + e.message + ']' + (e.code ? ' code=' + e.code : '');
}

function show(item, fn) {
    var text;
    try {
        text = JSON.stringify(fn());
    } catch (e) {
        text = 'throws ' + describe(e);
    }
    console.log(MARK + ' ' + item + ' ' + clean(text));
}

show('surface', function () {
    return [typeof Module, typeof Module._load, typeof Module.runMain, typeof require, typeof require.resolve,
        typeof require.paths, typeof require.cache, typeof require.extensions, typeof require.root, Object.keys(require).sort()];
});
show('file', function () {
    var a = require('./lib/a');
    return [a.name, a.file, a.dir, a.self, typeof hiddenTopLevel];
});
show('same-module', function () {
    return require('./lib/a') === require('./lib/../lib/a.js');
});
show('resolve', function () {
    return require.resolve('./lib/a');
});
show('cache-key', function () {
    return require.cache[require.resolve('./lib/a')].exports === require('./lib/a');
});
show('fresh-load', function () {
    var key = require.resolve('./lib/a');
    var first = require('./lib/a');
    delete require.cache[key];
    var second = require('./lib/a');
    return [first !== second, second.name];
});
show('json', function () {
    return require('./lib/data.json');
});
show('json-cached', function () {
    return require('./lib/data') === require('./lib/data.json');
});
show('package-main', function () {
    return require('./pkg');
});
show('index', function () {
    return require('./idx');
});
show('node-modules-up', function () {
    return require('./sub/deep/x');
});
show('cycle', function () {
    return require('./cycle/a');
});
// Issue #564: two modules declare "const log" (a global name) at their top level, the second also "let toast".
show('issue-564', function () {
    return [require('./i564/lib/a'), require('./i564/lib/b'), typeof log, typeof toast];
});
show('shebang', function () {
    return require('./lib/shebang');
});
show('core-path', function () {
    return require('path').join('a', 'b');
});
show('core-events', function () {
    return [typeof require('events'), require('events') === require('node:events')];
});
show('axios', function () {
    return require('axios') === axios;
});
show('paths', function () {
    return Array.isArray(require.paths());
});
show('module-error', function () {
    try {
        require('./lib/throws');
    } catch (e) {
        return [e.name, e.fileName, e.lineNumber];
    }
});
show('module-error-uncached', function () {
    return require.cache[require.resolve('./lib/throws')] === undefined;
});
show('missing', function () {
    return require('./nope');
});
show('missing-bare', function () {
    return require('aj6-nope-pkg');
});
show('bad-arg', function () {
    return require(1);
});
show('empty', function () {
    return require('./lib/empty');
});
show('upper-ext', function () {
    return require('./lib/Up.JS');
});
show('parse-error', function () {
    try {
        require('./lib/bad-syntax');
    } catch (e) {
        return [e.name, e instanceof Error, e.code];
    }
});
show('package-error', function () {
    try {
        require('./badpkg');
    } catch (e) {
        return [e.name, e.message, e.code, Object.prototype.toString.call(e)];
    }
});
show('run-main', function () {
    var exported = Module.runMain('./lib/main-target');
    return [exported, require.main && require.main.id, typeof process === 'undefined' ? null : process.mainModule === require.main];
});
show('run-main-missing', function () {
    return Module.runMain('./nope');
});
console.log(MARK + ' done');
