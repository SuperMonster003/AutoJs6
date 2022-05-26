// noinspection JSUnusedGlobalSymbols

/* Here, importClass() is not recommended for intelligent code completion in IDE like WebStorm. */
/* The same is true of destructuring assignment syntax (like `let {Uri} = android.net`). */

const File = java.io.File;
const Uri = android.net.Uri;
const javaInteger = java.lang.Integer;
const BuildConfig = org.autojs.autojs6.BuildConfig;
const FileProvider = androidx.core.content.FileProvider;

// @Hint by SuperMonster003 on May 5, 2022.
//  ! Store global function reference(s) immediately in case
//  ! the one(s) being overwritten (usually by accident).
const $isPlainObject = global.isPlainObject.bind(global);

let _ = {
    protocol: {
        file: 'file://',
    },
    init(__runtime__, scope) {
        this.runtime = __runtime__;
        this.scope = scope;
        this.app = Object.create(__runtime__.app);
    },
    getModule() {
        return this.app;
    },
    selfAugment() {
        Object.assign(this.app, {
            autojs: {
                versionCode: BuildConfig.VERSION_CODE,
                versionName: BuildConfig.VERSION_NAME,
                versionDate: BuildConfig.VERSION_DATE,
            },
            versionCode: _.getPackageInfo().versionCode,
            versionName: _.getPackageInfo().versionName,
            launch(packageName) {
                return this.launchPackage(packageName);
            },
            /**
             * @param {App.Intent.Common} i
             * @return {Intent}
             */
            intent(i) {
                let intent = new Intent();
                if (i.className && i.packageName) {
                    intent.setClassName(i.packageName, i.className);
                }
                if (i.extras) {
                    Object.entries(i.extras).forEach((pairs) => {
                        let [key, value] = pairs;
                        intent.putExtra(key, value);
                    });
                }
                if (i.category) {
                    if (Array.isArray(i.category)) {
                        i.category.forEach(cat => intent.addCategory(i.category[cat]));
                    } else {
                        intent.addCategory(i.category);
                    }
                }
                if (i.action) {
                    intent.setAction(_.parseIntentAction(i.action));
                }
                if (i.flags) {
                    intent.setFlags(_.parseIntentFlags(i.flags));
                }
                if (i.type) {
                    if (i.data) {
                        intent.setDataAndType(this.parseUri(i.data), i.type);
                    } else {
                        intent.setType(i.type);
                    }
                } else if (i.data) {
                    intent.setData(Uri.parse(i.data));
                }
                return intent;
            },
            /**
             * @param {App.Intent.Common} i
             * @return {string}
             */
            intentToShell(i) {
                let __ = {
                    init() {
                        this.cmd = '';
                    },
                    /**
                     * @typedef {{ body: string, isQuote?: boolean }} CmdBody
                     * @typedef {CmdBody | CmdBody[] | string} CmdBodies
                     */
                    /**
                     * @param {string} cmdOptions
                     * @param {CmdBodies} cmdBodies
                     */
                    append(cmdOptions, cmdBodies) {
                        this.cmd += ` -${cmdOptions} ${this.parseCmdBodies(cmdBodies)}`;
                    },
                    /**
                     * @param {string} str
                     * @return {string}
                     */
                    quote(str) {
                        return `'${str.replace('\'', '\\\'')}'`;
                    },
                    isInt(x) {
                        return Number.isInteger(x) && x <= javaInteger.MAX_VALUE && x >= javaInteger.MIN_VALUE;
                    },
                    parseType(type) {
                        if (typeof type === 'boolean') {
                            return 'z';
                        }
                        if (typeof type === 'number') {
                            return !Number.isInteger(type) ? 'f' : this.isInt(type) ? 'i' : 'l';
                        }
                        throw TypeError(`Unknown type: ${type}`);
                    },
                    /**
                     * @param {CmdBodies} o
                     * @return {string}
                     */
                    parseCmdBodies(o) {
                        if (Array.isArray(o)) {
                            return o.map((p) => {
                                return p.isQuote ? this.quote(p.body) : p.body;
                            }).join('\x20');
                        }
                        let body = $isPlainObject(o) ? o.body : o;
                        return o.isQuote ? this.quote(body) : body;
                    },
                };

                let $$ = {
                    getResult() {
                        this.init()
                            .parseNames()
                            .parseExtras()
                            .parseCategory()
                            .parseAction()
                            .parseFlags()
                            .parseType()
                            .parseData();

                        return this.cmd;
                    },
                    init() {
                        __.init();

                        Object.defineProperty(this, 'cmd', {get: () => __.cmd});

                        return this;
                    },
                    parseNames() {
                        if (i.className && i.packageName) {
                            let body = `${i.packageName}/${i.className}`;
                            __.append('n', {body, isQuote: true});
                        }
                        return this;
                    },
                    parseExtras() {
                        if (i.extras) {
                            Object.entries(i.extras).forEach((pairs) => {
                                let [key, value] = pairs;
                                if (typeof value === 'string') {
                                    return __.append('-es', [
                                        {body: key, isQuote: true},
                                        {body: value, isQuote: true},
                                    ]);
                                }
                                if (Array.isArray(value)) {
                                    if (value.length === 0) {
                                        throw Error(`Empty array: ${key}`);
                                    }
                                    let [element] = value;
                                    return typeof element === 'string'
                                        ? __.append('-esa', [
                                            {body: key, isQuote: true},
                                            {body: value.map(__.quote).join()},
                                        ])
                                        : __.append(`-e${__.parseType(element)}a`, [
                                            {body: key, isQuote: true},
                                            {body: value},
                                        ]);
                                }
                                return __.append(`-e${__.parseType(value)}`, [
                                    {body: key, isQuote: true},
                                    {body: value},
                                ]);
                            });
                        }
                        return this;
                    },
                    parseCategory() {
                        if (i.category) {
                            if (Array.isArray(i.category)) {
                                i.category.forEach(cat => __.append('c', cat));
                            } else {
                                __.append('c', i.category);
                            }
                        }
                        return this;
                    },
                    parseAction() {
                        if (i.action) {
                            __.append('a', {
                                body: _.parseIntentAction(i.action),
                                isQuote: true,
                            });
                        }
                        return this;
                    },
                    parseFlags() {
                        if (i.flags) {
                            __.append('f', _.parseIntentFlags(i.flags));
                        }
                        return this;
                    },
                    parseType() {
                        if (i.type) {
                            __.append('t', i.type);
                        }
                        return this;
                    },
                    parseData() {
                        if (i.data) {
                            __.append('d', i.data);
                        }
                        return this;
                    },
                };

                return $$.getResult();
            },
            startActivity(i) {
                if (i instanceof Intent) {
                    return context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                }
                if (typeof i === 'string') {
                    let property = runtime.getProperty(`class.${i}`);
                    if (!property) {
                        throw Error(`Class ${i} not found`);
                    }
                    let intent = new Intent(context, property)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    return context.startActivity(intent);
                }
                if ($isPlainObject(i) && i.root) {
                    shell(`am start ${this.intentToShell(i)}`, true);
                } else {
                    context.startActivity(this.intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                }

            },
            startService(i) {
                if ($isPlainObject(i) && i.root) {
                    // noinspection SpellCheckingInspection
                    shell(`am startservice ${this.intentToShell(i)}`, true);
                } else {
                    context.startService(this.intent(i));
                }
            },
            /**
             * @param {App.Intent.Email} [options]
             */
            sendEmail(options) {
                let i = new Intent(Intent.ACTION_SEND);
                let opt = options || {};

                if (opt.email) {
                    i.putExtra(Intent.EXTRA_EMAIL, _.toArray(opt.email));
                }
                if (opt.cc) {
                    i.putExtra(Intent.EXTRA_CC, _.toArray(opt.cc));
                }
                if (opt.bcc) {
                    i.putExtra(Intent.EXTRA_BCC, _.toArray(opt.bcc));
                }
                if (opt.subject) {
                    i.putExtra(Intent.EXTRA_SUBJECT, opt.subject);
                }
                if (opt.text) {
                    i.putExtra(Intent.EXTRA_TEXT, opt.text);
                }
                if (opt.attachment) {
                    i.putExtra(Intent.EXTRA_STREAM, this.parseUri(opt.attachment));
                }
                i.setType('message/rfc822');

                this.startActivity(Intent.createChooser(i, 'Send Email'));
            },
            sendBroadcast(i) {
                if (typeof i === 'string') {
                    let property = runtime.getProperty(`broadcast.${i}`);
                    if (property) {
                        this.sendLocalBroadcastSync(this.intent({action: property}));
                    }
                } else {
                    if ($isPlainObject(i) && i.root) {
                        shell(`am broadcast ${this.intentToShell(i)}`, true);
                    } else {
                        context.sendBroadcast(this.intent(i));
                    }
                }
            },
            parseUri(uri) {
                return uri.startsWith(_.protocol.file) ? this.getUriForFile(uri) : Uri.parse(uri);
            },
            getUriForFile(path) {
                if (path.startsWith(_.protocol.file)) {
                    path = path.slice(_.protocol.file.length);
                }
                let file = new File(files.path(path));
                return this.fileProviderAuthority === null
                    ? Uri.fromFile(file)
                    : FileProvider.getUriForFile(context, this.fileProviderAuthority, file);
            },
        });
    },
    scopeAugment() {
        /**
         * @type {(keyof Internal.App)[]}
         */
        let methods = ['launchPackage', 'launch', 'launchApp', 'getPackageName', 'getAppName', 'openAppSetting'];
        __asGlobal__(this.app, methods);
    },
    getPackageInfo() {
        return context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
    },
    toArray(arg) {
        if (!Array.isArray(arg)) {
            arg = [arg];
        }
        let arr = util.java.array('string', arg.length);
        arg.forEach((o, i) => arr[i] = o);
        return arr;
    },
    parseIntentFlags(flags) {
        let parse = (o) => {
            if (typeof o === 'string') {
                return Intent[`FLAG_${o.toUpperCase()}`];
            }
            if (typeof o === 'number') {
                return o;
            }
            throw TypeError(`Invalid flags: ${o}`);
        };
        let result = 0x0;
        if (Array.isArray(flags)) {
            flags.forEach(flag => result |= parse(flag));
        } else {
            result = parse(flags);
        }
        return result;
    },
    parseIntentAction(action) {
        if (typeof action === 'string' && !action.includes('.')) {
            action = `android.intent.action.${action}`;
        }
        return action;
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
 * @return {Internal.App}
 */
module.exports = function (__runtime__, scope) {
    return $.getModule(__runtime__, scope);
};