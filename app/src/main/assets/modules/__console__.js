/* Overwritten protection. */
// noinspection JSValidateTypes

let { files, util, s13n } = global;

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Console}
 */
module.exports = function (scriptRuntime, scope) {
    const Log = android.util.Log;
    const Level = org.apache.log4j.Level;
    const LogManager = org.apache.log4j.LogManager;
    const LogConfigurator = de.mindpipe.android.logging.log4j.LogConfigurator;
    const ConsoleUtils = org.autojs.autojs.util.ConsoleUtils;

    // noinspection JSValidateTypes
    /** @type {org.autojs.autojs.core.console.GlobalConsole} */
    const rtConsole = scriptRuntime.console;

    const multiArgsWrapperWhiteList = [ 'size', 'position' ];

    let _ = {
        Console: (/* @IIFE */ () => {
            /**
             * @extends Internal.Console
             */
            const Console = function () {
                /* Empty body. */
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
                show() {
                    rtConsole.show.apply(rtConsole, arguments);
                    return this;
                },
                hide() {
                    rtConsole.hide.apply(rtConsole, arguments);
                    return this;
                },
                reset() {
                    rtConsole.reset.apply(rtConsole, arguments);
                    return this;
                },
                clear() {
                    rtConsole.clear.apply(rtConsole, arguments);
                    return this;
                },
                expand() {
                    rtConsole.expand.apply(rtConsole, arguments);
                    return this;
                },
                collapse() {
                    rtConsole.collapse.apply(rtConsole, arguments);
                    return this;
                },
                assert(value, message) {
                    rtConsole.assertTrue(
                        Boolean(typeof value === 'function' ? value() : value),
                        message || util.getClassName(java.lang.AssertionError));
                },
                input() {
                    // @Abandoned by SuperMonster003 as of May 3, 2023.
                    // return eval(String(this.rawInput(data, param)));
                    throw Error(context.getString(R.strings.error_abandoned_method, 'console.input'));
                },
                rawInput() {
                    // @Abandoned by SuperMonster003 as of May 3, 2023.
                    throw Error(context.getString(R.strings.error_abandoned_method, 'console.rawInput'));
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
                build(options) {
                    let configurator = rtConsole.getConfigurator();
                    let opt = options || {};

                    Object.keys(opt).forEach((k) => {
                        let fName = `set${StringUtils.toUpperCaseFirst(k)}`;
                        if (!(fName in configurator) || typeof configurator[fName] !== 'function') {
                            throw TypeError(`Unknown key "${k}" of options for builder.build`);
                        }
                        let value = opt[k];
                        if (Array.isArray(value) && multiArgsWrapperWhiteList.includes(k)) {
                            console[fName].apply(console, value);
                        } else {
                            console[fName].call(console, value);
                        }
                    });
                    return configurator;
                },
                setSize(w, h) {
                    util.ensureNumberType(w, h);
                    rtConsole.setSize(DisplayUtils.toRoundDoubleX(w), DisplayUtils.toRoundDoubleY(h));
                    return this;
                },
                setPosition(x, y) {
                    util.ensureNumberType(x, y);
                    rtConsole.setPosition(DisplayUtils.toRoundDoubleX(x), DisplayUtils.toRoundDoubleY(y));
                    return this;
                },
                setTitle(title) {
                    rtConsole.setTitle(String(title));
                    return this;
                },
                setTitleTextSize(size) {
                    rtConsole.setTitleTextSize(Number(size));
                    return this;
                },
                setTitleTextColor(color) {
                    rtConsole.setTitleTextColor(s13n.color(color));
                    return this;
                },
                setTitleBackgroundColor(color) {
                    rtConsole.setTitleBackgroundColor(s13n.color(color));
                    return this;
                },
                setTitleBackgroundAlpha(alpha) {
                    rtConsole.setTitleBackgroundAlpha(ColorUtils.toUnit8(alpha, true));
                    return this;
                },
                setTitleIconsTint(color) {
                    rtConsole.setTitleIconsTint(s13n.color(color));
                    return this;
                },
                setContentTextSize(size) {
                    rtConsole.setContentTextSize(Number(size));
                    return this;
                },
                setContentTextColor(colors) {
                    if (arguments.length === 1) {
                        if (Array.isArray(colors)) {

                            // @Hint by SuperMonster003 on Apr 9, 2023.
                            //  ! Notice that JavaScript Array
                            //  ! with empty elements (like [ 1, 2, , , 3 ])
                            //  ! is not acceptable for Rhino.
                            //  ! A simple mapping is needed.

                            let tmp = Array(6).fill(null);
                            colors.forEach((o, i) => tmp[i] = s13n.color(o));
                            rtConsole.setContentTextColor(tmp);
                            return this;
                        }
                        if (species.isObject(colors)) {
                            let tmp = Array(6).fill(null);
                            [ 'verbose', 'log', 'info', 'warn', 'error', 'assert' ].forEach((s, i) => {
                                if (s in colors) {
                                    tmp[i] = s13n.color(colors[s]);
                                }
                            });
                            rtConsole.setContentTextColor(tmp);
                            return this;
                        }
                        rtConsole.setContentTextColor(Array(6).fill(s13n.color(colors)));
                        return this;
                    }
                    // noinspection JSCheckFunctionSignatures
                    rtConsole.setContentTextColor(Array.from(arguments).flat(Infinity).map(o => s13n.color(o)));
                    return this;
                },
                setContentBackgroundColor(color) {
                    rtConsole.setContentBackgroundColor(s13n.color(color));
                    return this;
                },
                setContentBackgroundAlpha(alpha) {
                    rtConsole.setContentBackgroundAlpha(ColorUtils.toUnit8(alpha, true));
                    return this;
                },
                setTextSize(size) {
                    rtConsole.setTextSize(Number(size));
                    return this;
                },
                setTextColor(color) {
                    rtConsole.setTextColor(s13n.color(color));
                    return this;
                },
                setBackgroundColor(color) {
                    rtConsole.setBackgroundColor(s13n.color(color));
                    return this;
                },
                setBackgroundAlpha(alpha) {
                    rtConsole.setBackgroundAlpha(ColorUtils.toUnit8(alpha, true));
                    return this;
                },
                setExitOnClose(exitOnClose /* or timeout */) {
                    rtConsole.setExitOnClose.apply(rtConsole, arguments);
                    return this;
                },
                setGlobalLogConfig(config) {
                    let configurator = new LogConfigurator();
                    configurator.setFileName(files.path(_.parseOption(config.file, 'android-log4j.log')));
                    configurator.setUseFileAppender(true);
                    configurator.setFilePattern(_.parseOption(config.filePattern, '%m%n'));
                    configurator.setMaxFileSize(_.parseOption(config.maxFileSize, 512 * 1024));
                    configurator.setImmediateFlush(_.parseOption(config.immediateFlush, true));
                    configurator.setRootLevel(Level[_.parseOption(config.rootLevel, 'ALL').toUpperCase()]);
                    configurator.setMaxBackupSize(_.parseOption(config.maxBackupSize, 5));
                    configurator.setResetConfiguration(_.parseOption(config.resetConfiguration, true));
                    configurator.configure();
                },
                resetGlobalLogConfig() {
                    LogManager.getLoggerRepository().resetConfiguration();
                },
                launch() {
                    ConsoleUtils.launch();
                },
                printAllStackTrace(t) {
                    rtConsole.printAllStackTrace(s13n.throwable(t));
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
            let methods = [
                'verbose', 'print', 'log', 'warn',
                { err: 'error' },
                { openConsole: 'show' },
                { showConsole: 'show' },
                { clearConsole: 'clear' },
                { launchConsole: 'launch' },
            ];
            __asGlobal__(console, methods, scope);
        },
    };

    /**
     * @type {Internal.Console}
     */
    const console = new _.Console();

    _.scopeAugment();

    return new ProxyObject(scope, {
        set(k, v) {
            if (k === 'constructor') {
                return;
            }
            let fName = `set${StringUtils.toUpperCaseFirst(k)}`;
            if (!(fName in console) || typeof console[fName] !== 'function') {
                throw TypeError(`Unknown key "${k}" for console setter`);
            }
            if (Array.isArray(v) && multiArgsWrapperWhiteList.includes(k)) {
                console[fName].apply(console, v);
            } else {
                console[fName].call(console, v);
            }
        },
        get(k) {
            if (typeof console[k] === 'function') {
                return console[k].bind(console);
            }
            return console[k];
        },
    });
};