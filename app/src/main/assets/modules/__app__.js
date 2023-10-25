// noinspection JSUnusedGlobalSymbols

/* Overwritten protection. */

let { autojs, shell, files, util } = global;

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.App}
 */
module.exports = function (scriptRuntime, scope) {
    const JavaInteger = java.lang.Integer;

    /**
     * @type {org.autojs.autojs.runtime.api.AppUtils}
     */
    const rtApp = scriptRuntime.app;

    const packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);

    // noinspection SpellCheckingInspection
    let _ = {
        App: (/* @IIFE */ () => {
            /**
             * @extends Internal.App
             */
            const App = function () {
                /* Empty body. */
            };

            App.prototype = {
                constructor: App,
                autojs,
                versionCode: packageInfo.versionCode,
                versionName: packageInfo.versionName,
                /**
                 * @param {App.Alias | App.PackageName} app
                 * @returns {boolean}
                 */
                launch(app) {
                    return this.launchPackage(app);
                },
                /**
                 * @param {Intent.Common | Intent} o
                 * @return {Intent}
                 */
                intent(o) {
                    if (o instanceof Intent) {
                        return o;
                    }

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
                 * @param {Intent.Common} i
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
                            let body = species.isObject(o) ? o.body : o;
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
                    if (o instanceof URI) {
                        return this.openUrl(o);
                    }
                    if (typeof o === 'string') {
                        if (o.includes('://')) {
                            return this.openUrl(o);
                        }
                        let rexWebSiteWithoutProtocol = /^(www.)?[a-z0-9]+(\.[a-z]{2,}){1,3}(#?\/?[a-zA-Z0-9#]+)*\/?(\?[a-zA-Z0-9-_]+=[a-zA-Z0-9-%]+&?)?$/;
                        if (rexWebSiteWithoutProtocol.test(o)) {
                            return this.openUrl(`http://${o}`);
                        }
                        let prop = runtime.getProperty(`${AppUtils.Companion.getActivityShortFormPrefix()}${o}`);
                        if (!prop) {
                            throw Error(`Activity short form ${o} not found`);
                        }
                        let intent = new Intent(context, prop).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        return context.startActivity(intent);
                    }
                    if (species.isObject(o) && o.root) {
                        shell(`am start ${this.intentToShell(o)}`, true);
                    } else {
                        context.startActivity(this.intent(o).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    }
                },
                startService(i) {
                    if (species.isObject(i) && i.root) {
                        // noinspection SpellCheckingInspection
                        shell(`am startservice ${this.intentToShell(i)}`, true);
                    } else {
                        context.startService(this.intent(i));
                    }
                },
                /**
                 * @param {Intent.Email} [options]
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
                        let property = runtime.getProperty(`${AppUtils.Companion.getBroadcastShortFormPrefix()}${i}`);
                        if (!property) {
                            throw Error(`Broadcast short form ${i} not found`);
                        }
                        this.sendLocalBroadcastSync(this.intent({ action: property }));
                    } else {
                        if (species.isObject(i) && i.root) {
                            shell(`am broadcast ${this.intentToShell(i)}`, true);
                        } else {
                            context.sendBroadcast(this.intent(i));
                        }
                    }
                },
                parseUri(uri) {
                    if (typeof uri === 'string') {
                        return uri.startsWith(_.protocol.file) ? this.getUriForFile(uri) : Uri.parse(uri);
                    }
                    if (uri instanceof URI) {
                        return this.parseUri(uri.getHost());
                    }
                    return null;
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
                    return this.launchSettings(app);
                },
                launchSettings(app) {
                    if (app instanceof App) {
                        app = app.getPackageName();
                    }
                    let preset = _.getAppByAlias(app);
                    return rtApp.launchSettings(preset ? preset.getPackageName() : app);
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
                viewFile(path) {
                    return _.performFileAction('view', path);
                },
                editFile(path) {
                    return _.performFileAction('edit', path);
                },
            };

            Object.setPrototypeOf(App.prototype, rtApp);

            return App;
        })(),
        protocol: {
            file: 'file://',
        },
        /**
         * @returns {Object.<App.Alias, string>}
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
                            if (species.isObject(val)) {
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
         * @param {Intent.Common} intent
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
            let methods = [ 'launchPackage', 'launch', 'launchApp', 'getPackageName', 'getAppName', 'openAppSetting', 'launchSettings' ];
            __asGlobal__(app, methods, scope);
        },
        /**
         * @param {'edit'|'view'} actionName
         * @param {string} path
         * @returns {boolean}
         */
        performFileAction(actionName, path) {
            if (typeof path !== 'string') {
                throw TypeError(`Can't ${actionName} "${path}" as it isn't a string`);
            }
            let nicePath = files.path(path);
            if (!files.exists(nicePath)) {
                throw Error(`Can't ${actionName} "${path}" as it doesn't exist`);
            }
            if (!files.isFile(nicePath)) {
                throw Error(`Can't ${actionName} "${path}" as it isn't a file`);
            }
            return rtApp[`${actionName}File`].call(rtApp, nicePath);
        },
    };

    /**
     * @type {Internal.App}
     */
    const app = new _.App();

    _.scopeAugment();

    return app;
};