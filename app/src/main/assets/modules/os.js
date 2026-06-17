// noinspection JSUnusedGlobalSymbols

(function () {
    const System = java.lang.System;
    const Runtime = java.lang.Runtime;

    function tmpdir() {
        return String(System.getProperty('java.io.tmpdir') || runtime.files.cwd() || '');
    }

    module.exports = {
        EOL: '\n',
        arch() {
            return String(System.getProperty('os.arch') || '');
        },
        platform() {
            return 'android';
        },
        type() {
            return 'Android';
        },
        release() {
            try {
                return String(android.os.Build.VERSION.RELEASE || '');
            } catch (_) {
                return String(System.getProperty('os.version') || '');
            }
        },
        hostname() {
            return 'localhost';
        },
        homedir() {
            return String(System.getProperty('user.home') || runtime.files.cwd() || '');
        },
        tmpdir,
        totalmem() {
            return Runtime.getRuntime().maxMemory();
        },
        freemem() {
            return Runtime.getRuntime().freeMemory();
        },
        cpus() {
            const count = Runtime.getRuntime().availableProcessors();
            const result = [];
            for (let i = 0; i < count; i += 1) {
                result.push({
                    model: 'Android CPU',
                    speed: 0,
                    times: {
                        user: 0,
                        nice: 0,
                        sys: 0,
                        idle: 0,
                        irq: 0,
                    },
                });
            }
            return result;
        },
        endianness() {
            return 'LE';
        },
    };
})();
