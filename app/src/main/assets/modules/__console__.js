/* Overwritten protection. */

let { files, util } = global;

/**
 * @param {org.autojs.autojs.runtime.ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Console}
 */
module.exports = function (scriptRuntime, scope) {
    const Log = android.util.Log;
    const Level = org.apache.log4j.Level;
    const ConsoleUtils = org.autojs.autojs.util.ConsoleUtils;
    const LogConfigurator = de.mindpipe.android.logging.log4j.LogConfigurator;

    const rtConsole = scriptRuntime.console;

    let _ = {
        Console: ( /* @IIFE */ () => {
            /**
             * @extends Internal.Console
             */
            const Console = function () {
                // Empty interface body.
            };

            Console.prototype = {
                constructor: Console,
                trace: function captureStack(message, level) {
                    let target = {};
                    Error.captureStackTrace(target, captureStack);
                    if (typeof level === 'string') {
                        level = level.toUpperCase();
                    }
                    let msg = `${util.format(message)}\n${target.stack}`;
                    switch (level) {
                        case Log.VERBOSE:
                        case 'VERBOSE':
                            console.verbose(msg);
                            break;
                        case Log.DEBUG:
                        case 'DEBUG':
                            console.log(msg);
                            break;
                        case Log.INFO:
                        case 'INFO':
                            console.info(msg);
                            break;
                        case Log.WARN:
                        case 'WARN':
                            console.warn(msg);
                            break;
                        case Log.ERROR:
                        case 'ERROR':
                            console.error(msg);
                            break;
                        default:
                            console.log(msg);
                    }
                },
                assert(value, message) {
                    rtConsole.assertTrue(
                        Boolean(typeof value === 'function' ? value() : value),
                        message || util.getClassName(java.lang.AssertionError));
                },
                input(data, param) {
                    return eval(String(this.rawInput(data, param)));
                },
                log() {
                    rtConsole.log(util.format.apply(util, arguments));
                },
                verbose() {
                    rtConsole.verbose(util.format.apply(util, arguments));
                },
                print() {
                    rtConsole.print(Log.DEBUG, util.format.apply(util, arguments));
                },
                info() {
                    rtConsole.info(util.format.apply(util, arguments));
                },
                warn() {
                    rtConsole.warn(util.format.apply(util, arguments));
                },
                error() {
                    rtConsole.error(util.format.apply(util, arguments));
                },
                time(label) {
                    _.timeTable.save(label);
                },
                timeEnd(label) {
                    _.timeTable.loadAndLog(label);
                },
                setGlobalLogConfig(config) {
                    let configurator = new LogConfigurator();
                    if (config.file) {
                        configurator.setFileName(files.path(config.file));
                        configurator.setUseFileAppender(true);
                    }
                    configurator.setFilePattern(_.parseOption(config.filePattern, '%m%n'));
                    configurator.setMaxFileSize(_.parseOption(config.maxFileSize, 512 * 1024));
                    configurator.setImmediateFlush(_.parseOption(config.immediateFlush, true));
                    configurator.setRootLevel(Level[_.parseOption(config.rootLevel, 'ALL').toUpperCase()]);
                    configurator.setMaxBackupSize(_.parseOption(config.maxBackupSize, 5));
                    configurator.setResetConfiguration(_.parseOption(config.resetConfiguration, true));
                    configurator.configure();
                },
                launch() {
                    ConsoleUtils.launch();
                },
            };

            Object.keys(rtConsole).forEach((key) => {
                if (!Console.prototype.hasOwnProperty(key)) {
                    if (typeof rtConsole[key] === 'function') {
                        Console.prototype[key] = rtConsole[key].bind(rtConsole);
                    } else {
                        Console.prototype[key] = rtConsole[key];
                    }
                }
            });

            return Console;
        })(),
        timeTable: {
            data: {},
            default: 'default',
            uptimeMillis() {
                return android.os.SystemClock.uptimeMillis();
            },
            parseLabel(label) {
                return label || this.default;
            },
            save(label) {
                this.data[this.parseLabel(label)] = this.uptimeMillis();
            },
            load(label) {
                return this.data[this.parseLabel(label)];
            },
            remove(label) {
                delete this.data[this.parseLabel(label)];
            },
            log(label, text) {
                console.log(`${this.parseLabel(label)}: ${text}ms`);
            },
            loadAndLog(label) {
                let text = this.uptimeMillis() - this.load(label);
                this.remove(label);
                this.log(label, text);
            },
        },
        parseOption(value, def) {
            return value === undefined ? def : value;
        },
        scopeAugment() {
            __asGlobal__(console, [
                'verbose', 'print', 'log', 'warn', { err: 'error' },
                { openConsole: 'show' }, { clearConsole: 'clear' }, { launchConsole: 'launch' },
            ]);
        },
    };

    /**
     * @type {Internal.Console}
     */
    const console = new _.Console();

    _.scopeAugment();

    return console;
};