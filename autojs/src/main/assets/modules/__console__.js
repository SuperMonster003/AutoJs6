// noinspection JSUnusedGlobalSymbols

/* Here, importClass() is not recommended for intelligent code completion in IDE like WebStorm. */
/* The same is true of destructuring assignment syntax (like `let {Uri} = android.net`). */

const Log = android.util.Log;
const Level = org.apache.log4j.Level;
const ConsoleTool = org.autojs.autojs.tool.ConsoleTool;
const LogConfigurator = de.mindpipe.android.logging.log4j.LogConfigurator;

let _ = {
    init(__runtime__, scope) {
        this.runtime = __runtime__;
        this.scope = scope;

        /**
         * @type {com.stardust.autojs.core.console.ConsoleImpl}
         */
        this.rtConsole = __runtime__.console;
        this.console = {};
    },
    getModule() {
        return this.console;
    },
    selfAugment() {
        let __ = {
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
                    _.console.log(`${this.parseLabel(label)}: ${text}ms`);
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
        };

        Object.assign(this.console, {
            show: _.rtConsole.show.bind(_.rtConsole),
            hide: _.rtConsole.hide.bind(_.rtConsole),
            clear: _.rtConsole.clear.bind(_.rtConsole),
            setSize: _.rtConsole.setSize.bind(_.rtConsole),
            setPosition: _.rtConsole.setPosition.bind(_.rtConsole),
            setTitle: _.rtConsole.setTitle.bind(_.rtConsole),
            rawInput: _.rtConsole.rawInput.bind(_.rtConsole),
            assert(value, message) {
                _.rtConsole.assertTrue(value, message || '');
            },
            input(data, param) {
                return eval(String(this.rawInput(data, param)));
            },
            log() {
                _.rtConsole.log(util.format.apply(util, arguments));
            },
            verbose() {
                _.rtConsole.verbose(util.format.apply(util, arguments));
            },
            print() {
                _.rtConsole.print(Log.DEBUG, util.format.apply(util, arguments));
            },
            info() {
                _.rtConsole.info(util.format.apply(util, arguments));
            },
            warn() {
                _.rtConsole.warn(util.format.apply(util, arguments));
            },
            error() {
                _.rtConsole.error(util.format.apply(util, arguments));
            },
            time(label) {
                __.timeTable.save(label);
            },
            timeEnd(label) {
                __.timeTable.loadAndLog(label);
            },
            trace: ( /* @IIFE */ () => function captureStack(message, level) {
                let target = {};
                Error.captureStackTrace(target, captureStack);
                if (typeof level === 'string') {
                    level = level.toUpperCase();
                }
                let msg = `${util.format(message)}\n${target.stack}`;
                switch (level) {
                    case Log.VERBOSE:
                    case 'VERBOSE':
                        _.console.verbose(msg);
                        break;
                    case Log.DEBUG:
                    case 'DEBUG':
                        _.console.debug(msg);
                        break;
                    case Log.INFO:
                    case 'INFO':
                        _.console.info(msg);
                        break;
                    case Log.WARN:
                    case 'WARN':
                        _.console.warn(msg);
                        break;
                    case Log.ERROR:
                    case 'ERROR':
                        _.console.error(msg);
                        break;
                    default:
                        _.console.log(msg);
                }
            })(),
            setGlobalLogConfig(config) {
                let configurator = new LogConfigurator();
                if (config.file) {
                    configurator.setFileName(files.path(config.file));
                    configurator.setUseFileAppender(true);
                }
                configurator.setFilePattern(__.parseOption(config.filePattern, '%m%n'));
                configurator.setMaxFileSize(__.parseOption(config.maxFileSize, 512 * 1024));
                configurator.setImmediateFlush(__.parseOption(config.immediateFlush, true));
                configurator.setRootLevel(Level[__.parseOption(config.rootLevel, 'ALL').toUpperCase()]);
                configurator.setMaxBackupSize(__.parseOption(config.maxBackupSize, 5));
                configurator.setResetConfiguration(__.parseOption(config.resetConfiguration, true));
                configurator.configure();
            },
            launch() {
                ConsoleTool.launch();
            },
        });
    },
    scopeAugment() {
        __asGlobal__(this.console, [
            'print', 'log', 'warn', {err: 'error'},
            {openConsole: 'show'}, {clearConsole: 'clear'}, {launchConsole: 'launch'},
        ]);
    },
};

let $ = {
    getModule(__runtime__, scope) {
        _.init(__runtime__, scope);

        _.selfAugment();
        _.scopeAugment();

        return _.getModule();
    },
};

/**
 * @param {com.stardust.autojs.runtime.ScriptRuntime} __runtime__
 * @param {org.mozilla.javascript.Scriptable} scope
 * @return {Internal.Console}
 */
module.exports = function (__runtime__, scope) {
    return $.getModule(__runtime__, scope);
};