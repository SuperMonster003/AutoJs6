// noinspection JSUnusedGlobalSymbols

/* Overwritten protection. */

let { autojs, shell, files, util } = global;

/**
 * @param {org.autojs.autojs.runtime.ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.App}
 */
module.exports = function (scriptRuntime, scope) {
    const File = java.io.File;
    const Uri = android.net.Uri;
    const JavaInteger = java.lang.Integer;
    const FileProvider = androidx.core.content.FileProvider;

    /**
     * @type {org.autojs.autojs.runtime.api.AppUtils}
     */
    const rtApp = scriptRuntime.app;

    const packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);

    // noinspection SpellCheckingInspection
    let _ = {
        App: ( /* @IIFE */ () => {
            /**
             * @extends Internal.App
             */
            const App = function () {
                // Empty interface body.
            };

            App.prototype = {
                constructor: App,
                autojs,
                versionCode: packageInfo.versionCode,
                versionName: packageInfo.versionName,
                /**
                 * @param {App.Intent.Preset.AppAlias | App.PackageName} app
                 * @returns {boolean}
                 */
                launch(app) {
                    return this.launchPackage(app);
                },
                /**
                 * @param {App.Intent.Common} o
                 * @return {Intent}
                 */
                intent(o) {
                    let intent = new Intent();

                    if (o.url) {
                        o.data = _.parseIntentUrl(o);
                    }
                    if (o.package) {
                        o.packageName = o.packageName || o.package;
                    } else if (o.packageName) {
                        o.package = o.packageName;
                    }
                    if (o.packageName) {
                        let k = String(o.packageName);
                        let presets = _.getPresetPackageNames();
                        if (k in presets) {
                            o.packageName = presets[k];
                        }
                        if (o.className) {
                            intent.setClassName(o.packageName, _.parseClassName(o));
                        } else {
                            // @Hint by SuperMonster003 on Jun 23, 2020.
                            //  ! the Intent can only match the components
                            //  ! in the given application package with setPackage().
                            //  ! Otherwise, if there's more than one app that can handle the intent,
                            //  ! the system presents the user with a dialog to pick which app to use.
                            intent.setPackage(o.packageName);
                        }
                    }
                    if (o.extras) {
                        Object.entries(o.extras).forEach((pairs) => {
                            let [ key, value ] = pairs;
                            intent.putExtra(key, value);
                        });
                    }
                    if (o.category) {
                        if (Array.isArray(o.category)) {
                            o.category.forEach(cat => intent.addCategory(o.category[cat]));
                        } else {
                            intent.addCategory(o.category);
                        }
                    }
                    if (o.action) {
                        intent.setAction(_.parseIntentAction(o.action));
                    }
                    if (o.flags) {
                        intent.setFlags(_.parseIntentFlags(o.flags));
                    }
                    if (o.type) {
                        if (o.data) {
                            intent.setDataAndType(this.parseUri(o.data), o.type);
                        } else {
                            intent.setType(o.type);
                        }
                    } else if (o.data) {
                        intent.setData(Uri.parse(o.data));
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
                        quote(str) {
                            return `'${str.replace('\'', '\\\'')}'`;
                        },
                        isInt(x) {
                            return Number.isInteger(x) && x <= JavaInteger.MAX_VALUE && x >= JavaInteger.MIN_VALUE;
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
                            let body = isObjectSpecies(o) ? o.body : o;
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

                            Object.defineProperty(this, 'cmd', { get: () => __.cmd });

                            return this;
                        },
                        parseNames() {
                            if (i.className && i.packageName) {
                                let body = `${i.packageName}/${i.className}`;
                                __.append('n', { body, isQuote: true });
                            }
                            return this;
                        },
                        parseExtras() {
                            if (i.extras) {
                                Object.entries(i.extras).forEach((pairs) => {
                                    let [ key, value ] = pairs;
                                    if (typeof value === 'string') {
                                        return __.append('-es', [
                                            { body: key, isQuote: true },
                                            { body: value, isQuote: true },
                                        ]);
                                    }
                                    if (Array.isArray(value)) {
                                        if (value.length === 0) {
                                            throw Error(`Empty array: ${key}`);
                                        }
                                        let [ element ] = value;
                                        return typeof element === 'string'
                                            ? __.append('-esa', [
                                                { body: key, isQuote: true },
                                                { body: value.map(__.quote).join() },
                                            ])
                                            : __.append(`-e${__.parseType(element)}a`, [
                                                { body: key, isQuote: true },
                                                { body: value },
                                            ]);
                                    }
                                    return __.append(`-e${__.parseType(value)}`, [
                                        { body: key, isQuote: true },
                                        { body: value },
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
                startActivity(o) {
                    if (o instanceof Intent) {
                        return context.startActivity(o.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    }
                    if (typeof o === 'string') {
                        let prop = runtime.getProperty(`class.${o}`);
                        if (!prop) {
                            throw Error(`Class ${o} not found`);
                        }
                        let intent = new Intent(context, prop).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        return context.startActivity(intent);
                    }
                    if (isObjectSpecies(o) && o.root) {
                        shell(`am start ${this.intentToShell(o)}`, true);
                    } else {
                        context.startActivity(this.intent(o).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    }
                },
                startService(i) {
                    if (isObjectSpecies(i) && i.root) {
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
                            this.sendLocalBroadcastSync(this.intent({ action: property }));
                        }
                    } else {
                        if (isObjectSpecies(i) && i.root) {
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
                getAppByAlias(alias) {
                    return _.getAppByAlias(alias);
                },
                launchPackage(app) {
                    if (app instanceof App) {
                        app = app.getPackageName();
                    }
                    let preset = _.getAppByAlias(app);
                    return rtApp.launchPackage(preset ? preset.getPackageName() : app);
                },
                launchApp(app) {
                    if (app instanceof App) {
                        app = app.getAppName();
                    }
                    let preset = _.getAppByAlias(app);
                    return rtApp.launchApp(preset ? preset.getAppName() : app);
                },
                getAppName(app) {
                    if (app instanceof App) {
                        return app.getAppName();
                    }
                    let preset = _.getAppByAlias(app);
                    return preset ? preset.getAppName() : rtApp.getAppName(String(app));
                },
                getPackageName(app) {
                    if (app instanceof App) {
                        app = app.getPackageName();
                    }
                    let preset = _.getAppByAlias(app);
                    return preset ? preset.getPackageName() : rtApp.getPackageName(String(app));
                },
                openAppSetting(app) {
                    return this.openAppSettings(app);
                },
                openAppSettings(app) {
                    if (app instanceof App) {
                        app = app.getPackageName();
                    }
                    let preset = _.getAppByAlias(app);
                    return rtApp.openAppSettings(preset ? preset.getPackageName() : app);
                },
                uninstall(app) {
                    if (app instanceof App) {
                        app = app.getPackageName();
                    }
                    let preset = _.getAppByAlias(app);
                    return rtApp.uninstall(preset ? preset.getPackageName() : app);
                },
                isVersionNewer(name, version) {
                    //// -=-= PENDING =-=- ////
                },
            };

            Object.setPrototypeOf(App.prototype, rtApp);

            return App;
        })(),
        protocol: {
            file: 'file://',
        },
        /**
         * @returns {Object.<App.Intent.Preset.AppAlias, string>}
         */
        getPresetPackageNames() {
            if (_._presetPackageNames === undefined) {
                _._presetPackageNames = {};
                App.values().forEach((o) => {
                    _._presetPackageNames[o.getAlias()] = o.getPackageName();
                });
            }
            return _._presetPackageNames;
        },
        /**
         * @param {string} alias
         * @returns {org.autojs.autojs.util.App}
         */
        getAppByAlias(alias) {
            return App.getAppByAlias(String(alias));
        },
        toArray(arg) {
            if (!Array.isArray(arg)) {
                arg = [ arg ];
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
        parseIntentUrl(o) {
            let __ = {
                /**
                 * @param {Appx.Intent.URI} uri
                 * @return {string}
                 */
                parseUrlObject(uri) {
                    let { src, query, exclude } = uri;
                    if (!src || !query) {
                        return src;
                    }
                    let separator = src.match(/\?/) ? '&' : '?';
                    return src + separator + (function parse(query) {
                        exclude = exclude || [];
                        if (!Array.isArray(exclude)) {
                            exclude = [ exclude ];
                        }
                        return Object.keys(query).map((key) => {
                            let val = query[key];
                            if (isObjectSpecies(val)) {
                                val = key === 'url' ? __.parseUrlObject(val) : parse(val);
                                val = (key === '__webview_options__' ? '&' : '') + val;
                            }
                            if (!exclude.includes(key)) {
                                val = encodeURI(val);
                            }
                            return key + '=' + val;
                        }).join('&');
                    })(query);
                },
            };
            let { url } = o;
            return typeof url === 'object' ? __.parseUrlObject(url) : url;
        },
        /**
         * @param {App.Intent.Common} intent
         * @returns {string}
         */
        parseClassName(intent) {
            return intent.className.replace(/@\{(\w+?)}|@(\w+)/g, ($, $1, $2) => {
                let key = $1 || $2;
                if (key in intent) {
                    return intent[$1 || $2];
                }
                throw ReferenceError(`Intent object doesn't have a key named ${key}`);
            });
        },
        scopeAugment() {
            /**
             * @type {(keyof Internal.App)[]}
             */
            let methods = [ 'launchPackage', 'launch', 'launchApp', 'getPackageName', 'getAppName', 'openAppSetting', 'openAppSettings' ];
            __asGlobal__(app, methods, scope);
        },
    };

    /**
     * @type {Internal.App}
     */
    const app = new _.App();

    _.scopeAugment();

    return app;
};