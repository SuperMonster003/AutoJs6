// ROADMAP P4.3: require and the global getters of the 14 module names of the Bundled Libraries plugin, run by
// tools/augment/device_plugin_modules_check.py in each plugin state. One "AJ6REQ <item> <json>" line per item.

function out(item, value) {
    console.log('AJ6REQ ' + item + ' ' + JSON.stringify(value));
}

function probe(f) {
    try {
        return ['ok', f()];
    } catch (e) {
        return ['throws', String(e.name), String(e.code), String(e.library), String(e.reason)];
    }
}

var CJK = String.fromCharCode(0x4e2d, 0x6587);

var checks = [
    ['axios', function () { return typeof require('axios').get; }],
    ['cheerio', function () { return require('cheerio').load('<p>x</p>')('p').text(); }],
    ['dayjs', function () { return require('dayjs')(0).valueOf(); }],
    ['i18n', function () { return typeof require('i18n'); }],
    ['banana-i18n', function () { return typeof require('banana-i18n'); }],
    ['pangu', function () { return require('pangu').spaceText(CJK + 'abc' + CJK).length; }],
    ['lodash', function () { return require('lodash').map([1, 2], function (x) { return x * 2; }).join(); }],
    ['buffer', function () { return require('buffer').Buffer.from('ab').length; }],
    ['events', function () {
        var emitter = new (require('events').EventEmitter)();
        var got;
        emitter.on('x', function (v) { got = v; });
        emitter.emit('x', 7);
        return got;
    }],
    ['fs', function () { return require('fs').existsSync('/sdcard'); }],
    ['os', function () { return typeof require('os').platform(); }],
    // Not join / normalize: their normalizeArray hits the loop const of the Rhino fork (roadmap P3.7 (9)).
    ['path', function () { return require('path').isAbsolute('/a'); }],
    ['process', function () { return typeof require('process').env; }],
    ['node-path', function () { return require('node:path').sep; }],
    // Last: nodejs writes process, Buffer and setImmediate to the global object.
    ['nodejs', function () { require('nodejs'); return typeof setImmediate; }],
];

checks.forEach(function (check) {
    out('require-' + check[0], probe(check[1]));
});

['axios', 'cheerio', 'dayjs', 'i18n', 'pangu'].forEach(function (name) {
    out('getter-' + name, probe(function () { return typeof global[name]; }));
});

out('identity-axios', probe(function () { return require('axios') === axios && require('dayjs') === dayjs; }));

try {
    require('axios');
    out('message-axios', 'no throw');
} catch (e) {
    out('message-axios', String(e.message));
    out('position-axios', [String(e.fileName).split('/').pop(), e.lineNumber > 0]);
}

console.log('AJ6REQ done 1');
