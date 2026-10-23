// ROADMAP P3.7 smoke: modules that suspend at their top level in a project with the continuation feature (outer.js
// requires inner.js, each awaits a Promise settled by a timer). Run by tools/augment/device_require_check.py.
var MARK = 'AJ6REQ';
try {
    var result = require('./outer');
    console.log(MARK + ' cont-modules ' + JSON.stringify([result, require.cache[require.resolve('./outer')].loaded, continuation.enabled]));
} catch (e) {
    console.log(MARK + ' cont-modules throws ' + e.name + ' [' + e.message + ']');
}
console.log(MARK + ' done');
