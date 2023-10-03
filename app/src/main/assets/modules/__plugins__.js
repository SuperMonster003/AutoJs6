/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Plugins}
 */
module.exports = function (scriptRuntime, scope) {
    let _ = {
        Plugins: (/* @IIFE */ () => {
            /**
             * @implements Internal.Plugins
             */
            const Plugins = function () {
                /* Empty body. */
            };

            Plugins.prototype = {
                constructor: Plugins,
                /**
                 * @type {Internal.Plugins.Extend}
                 */
                extend: (/* @IIFE */ () => {
                    /**
                     * @implements Internal.Plugins.Extend
                     */
                    let Extend = function () {
                        return Object.assign(function (modules) {
                            Array.from(new Set(Array.from(arguments).flat(Infinity))).forEach((name) => {
                                let moduleName = _.normalizeModuleName(name);
                                if (moduleName in _.modules) {
                                    _.modules[moduleName].extendJsBuildInObjects();
                                }
                            });
                        }, Extend.prototype);
                    };

                    Extend.prototype = {
                        constructor: Extend,
                        exclude() {
                            Array.from(arguments).flat(Infinity).forEach((name) => {
                                let moduleName = _.normalizeModuleName(name);
                                if (!_.excludes.includes(moduleName)) {
                                    _.excludes.push(moduleName);
                                }
                            });
                        },
                        registerModule(module) {
                            Object.assign(_.modules, module);
                        },
                    };

                    return new Extend();
                })(),
                extendAll() {
                    Object.entries(_.modules).forEach((entry) => {
                        let [ name, action ] = entry;
                        if (!_.excludes.includes(name)) {
                            action.extendJsBuildInObjects();
                        }
                    });
                },
                extendAllBut() {
                    this.extend.exclude.apply(this.extend, arguments);
                    this.extendAll();
                },
                load(name) {
                    if (typeof name !== 'string') {
                        throw TypeError('The "name" argument for plugins.load() must be of type string');
                    }
                    if (name.includes('.') && !name.endsWith('.js')) /* As package name. */ {
                        let plugin = scriptRuntime.plugins.load(name);
                        let moduleExportedFunc = require(plugin.getMainScriptPath());
                        return moduleExportedFunc(plugin.unwrap());
                    }
                    if (files.exists('./plugins')) /* As project-level plugins name. */ {
                        return require(`./plugins${name}`);
                    }
                    throw Error('A directory named "plugins" must be found in the root directory of current project');
                },
            };

            return Plugins;
        })(),
        /**
         * @type {Object.<string, Internal.Plugins.ExtendModules.Interface>}
         */
        modules: {},
        /**
         * @type {string[]}
         */
        excludes: [],
        /**
         * @param {string} name
         * @returns {string}
         */
        normalizeModuleName(name) {
            if (!name.endsWith('x')) {
                name += 'x';
            }
            return StringUtils.toUpperCaseFirst(name);
        },
    };

    /**
     * @type {Internal.Plugins}
     */
    const plugins = new _.Plugins();

    return plugins;
};