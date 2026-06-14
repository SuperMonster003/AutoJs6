// noinspection JSUnusedGlobalSymbols

(function () {
    const process = require('process');
    const { Buffer } = require('buffer');

    global.process = process;
    global.Buffer = Buffer;

    if (typeof global.setImmediate !== 'function') {
        global.setImmediate = function (callback) {
            if (typeof callback !== 'function') {
                throw new TypeError('callback must be a function');
            }
            const args = Array.prototype.slice.call(arguments, 1);
            return setTimeout(function () {
                callback.apply(global, args);
            }, 0);
        };
    }

    if (typeof global.clearImmediate !== 'function') {
        global.clearImmediate = function (id) {
            return clearTimeout(id);
        };
    }

    global.__nodejs_set_main__ = function (filename, dirname, argv, execArgv) {
        process.argv = Array.isArray(argv) ? argv : [ 'autojs6', String(filename || '') ];
        process.execArgv = execArgv == null ? [] : execArgv;
        process.mainModule = require.main || null;
        global.__filename = String(filename || '');
        global.__dirname = String(dirname || '');
    };

    module.exports = {
        process,
        Buffer,
    };
})();
