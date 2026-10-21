// noinspection JSUnusedGlobalSymbols

/**
 *  Copyright 2014-2016 Red Hat, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License")
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
// Since we intend to use the Function constructor.
/* jshint evil: true */

( /* @ModuleIIFE */ () => {

    const File = java.io.File;
    const Scanner = java.util.Scanner;
    const System = java.lang.System;
    const Thread = java.lang.Thread;
    const EncryptedScriptFileHeader = Packages.org.autojs.autojs.script.EncryptedScriptFileHeader;
    const ScriptEncryption = Packages.org.autojs.autojs.engine.encryption.ScriptEncryption;
    const StandardCharsets = java.nio.charset.StandardCharsets;

    let _ = {
        Module: (/* @IIFE */ () => {
            /**
             * @extends Internal.Require.Module
             */
            const Module = function (id, parent, core) {
                this._exports = {};
                this.id = id;
                this.core = core;
                this.parent = parent;
                this.children = [];
                this.filename = id;
                this.loaded = false;

                Object.defineProperty(this, 'exports', {
                    get() {
                        return this._exports;
                    },
                    set(val) {
                        this._exports = val;
                    },
                });
                this.exports = {};

                if (parent && parent.children) {
                    parent.children.push(this);
                }

                this.require = function (id) {
                    return require(id, this);
                }.bind(this);
            };

            Object.assign(Module, {
                require(id, parent) {
                    return require(id, parent);
                },
                _load(file, parent, core) {
                    const cached = require.cache[file];
                    if (cached) {
                        return cached.exports === undefined ? cached : cached.exports;
                    }

                    const module = new Module(file, parent, core);
                    require.cache[file] = module;
                    try {
                        let body = _.readFile(file, core);
                        if (body.startsWith('#!')) {
                            body = '//' + body;
                        }
                        const dirname = core ? '' : String(new File(file).getParent() || '');
                        const moduleRequire = module.require;
                        moduleRequire.resolve = function (id) {
                            return require.resolve(id, module);
                        };
                        moduleRequire.paths = function () {
                            return require.paths();
                        };
                        moduleRequire.cache = require.cache;
                        moduleRequire.extensions = require.extensions;
                        const wrapper = new Function('exports', 'require', 'module', '__filename', '__dirname', body);
                        wrapper.call(module.exports, module.exports, moduleRequire, module, file, dirname);
                        module.loaded = true;
                        return module.exports;
                    } catch (e) {
                        delete require.cache[file];
                        throw e;
                    }
                },
                runMain(main) {
                    const file = require.resolve(main);
                    const exported = Module._load(file);
                    require.main = require.cache[file] || null;
                    if (global.process) {
                        global.process.mainModule = require.main;
                    }
                    return exported;
                },
            });

            return Module;
        })(),
        RequireCtor: (/* @IIFE */ () => {
            /**
             * @implements Internal.Require
             */
            const RequireCtor = function () {
                return Object.assign(function (id, parent) {
                    if (_.isNodeProtocol(id)) {
                        id = _.stripNodeProtocol(id);
                    }
                    const normalizedPath = _.normalizeName(id);
                    if (_.builtInModules.includes(normalizedPath) && !runtime.files.exists(normalizedPath)) {
                        return NativeRequire.require(normalizedPath);
                    }
                    // noinspection HttpUrlsUsage
                    if (id.startsWith('http://') || id.startsWith('https://')) {
                        return NativeRequire.require(id);
                    }

                    let file = this.resolve(id, parent);
                    if (!file) {
                        if (typeof NativeRequire.require === 'function') {
                            if (this.debug) {
                                System.out.println([ 'Cannot resolve', id, 'defaulting to native' ].join(' '));
                            }
                            let nativeRequired = NativeRequire.require(id);
                            if (nativeRequired) {
                                return nativeRequired;
                            }
                        }
                        if (this.debug) {
                            System.err.println('Cannot find module ' + id);
                        }
                        throw new _.ModuleError('Cannot find module ' + id, 'MODULE_NOT_FOUND');
                    }
                    let core = false;
                    if (file.core) {
                        core = true;
                        file = file.path;
                    }
                    if (this.cache[file]) {
                        const cached = this.cache[file];
                        return cached.exports === undefined ? cached : cached.exports;
                    }
                    if (_.isJavaScriptFileName(file)) {
                        return _.Module._load(file, parent, core);
                    }
                    if (file.endsWith('.json')) {
                        return _.loadJSON(file);
                    }
                }.bind(this), RequireCtor.prototype);
            };

            Object.assign(RequireCtor.prototype, {
                NODE_PATH: undefined,
                // System.getProperty('user.dir');
                root: runtime.files.cwd(),
                debug: true,
                cache: {},
                extensions: {},
                resolve(id, parent) {
                    const roots = _.findRoots(parent);
                    for (let i = 0; i < roots.length; ++i) {
                        const root = roots[i];
                        const result = _.resolveCoreModule(id)
                            || _.resolveAsFile(id, root, '.js')
                            || _.resolveAsFile(id, root, '.mjs')
                            || _.resolveAsFile(id, root, '.json')
                            || _.resolveAsDirectory(id, root)
                            || _.resolveAsNodeModule(id, root);
                        if (result) {
                            return result;
                        }
                    }
                    return false;
                },
                paths() {
                    let r = [
                        System.getProperty('user.home') + '/.node_modules',
                        System.getProperty('user.home') + '/.node_libraries',
                    ];

                    if (this.NODE_PATH) {
                        r = r.concat(_.parsePaths(this.NODE_PATH));
                    } else {
                        const { NODE_PATH } = System.getenv();
                        if (NODE_PATH) {
                            r = r.concat(_.parsePaths(NODE_PATH));
                        }
                    }
                    // r.push( $PREFIX + "/node/library" )
                    return r;
                },
            });

            return RequireCtor;
        })(),
        ModuleError: (/* @IIFE */ () => {
            /**
             * @param {?string} [message]
             * @param {?string} [code]
             * @param {?string} [cause]
             * @extends Error
             */
            const ModuleError = function (message, code, cause) {
                this.message = message || 'Error loading module';
                this.code = code || 'UNDEFINED';
                this.cause = cause;
            };

            ModuleError.prototype = Object.assign(new Error(), { constructor: ModuleError });

            return ModuleError;
        })(),
        builtInModules: [
            'buffer.js',
            'events.js',
            'fs.js',
            'lodash.js',
            'nodejs.js',
            'os.js',
            'path.js',
            'process.js',
        ],
        isNodeProtocol(id) {
            return String(id).startsWith('node:');
        },
        stripNodeProtocol(id) {
            return this.isNodeProtocol(id) ? String(id).slice(5) : id;
        },
        isJavaScriptFileName(fileName) {
            const name = String(fileName);
            return name.endsWith('.js') || name.endsWith('.mjs');
        },
        hasKnownExtension(fileName) {
            return /\.(?:js|mjs|json)$/i.test(String(fileName));
        },
        findRoots(parent) {
            return [ this.findRoot(parent) ].concat(require.paths());
        },
        findRoot(parent) {
            if (!parent || !parent.id) {
                return require.root;
            }
            return (/* pathParts = */ parent.id.split(/[\/|\\,]+/g).slice(0, -1)).join(File.separator);
        },
        readFile(filename, core) {
            try {
                if (!core) {
                    const encryptedBytes = runtime.files.readBytes(filename);
                    if (encryptedBytes.length >= EncryptedScriptFileHeader.BLOCK_SIZE
                        && EncryptedScriptFileHeader.isValidFile(encryptedBytes)) {
                        const clearText = ScriptEncryption.decrypt(
                            encryptedBytes,
                            EncryptedScriptFileHeader.BLOCK_SIZE,
                            encryptedBytes.length,
                        );
                        try {
                            return String(new java.lang.String(clearText, StandardCharsets.UTF_8));
                        } finally {
                            for (let i = 0; i < clearText.length; i++) {
                                clearText[i] = 0;
                            }
                        }
                    }
                }
                let input = core
                    ? Thread.currentThread().getContextClassLoader().getResourceAsStream(filename)
                    : new File(filename);
                // TODO: I think this is not very efficient
                return new Scanner(input).useDelimiter('\\A').next();
            } catch (e) {
                throw new _.ModuleError(`Cannot read file [${filename}]: `, 'IO_ERROR', e);
            }
        },
        parsePaths: (paths) => paths && paths !== ''
            ? paths.split(/* separator = */ System.getProperty('os.name').toLowerCase().includes('win') ? ';' : ':')
            : [],
        normalizeName(fileName, ext) {
            if (this.hasKnownExtension(fileName)) {
                return fileName;
            }
            ext = ext || '.js';
            if (!fileName.endsWith(ext)) {
                fileName += ext;
            }
            return fileName;
        },
        loadJSON(file) {
            return require.cache[file] = JSON.parse(this.readFile(file));
        },
        resolveCoreModule(id) {
            const name = this.normalizeName(id);
            if (Thread.currentThread().getContextClassLoader().getResource(name)) {
                return { path: name, core: true };
            }
        },
        resolveAsFile(id, root, ext) {
            let file;
            if (!id.startsWith(File.separator)) {
                file = new File([ root, this.normalizeName(id, ext) ].join(File.separator));
                if (!file.exists()) {
                    return;
                }
            } else {
                file = new File(this.normalizeName(id, ext));
                if (!file.exists()) {
                    return this.resolveAsDirectory(id);
                }
            }
            return file.getCanonicalPath();
        },
        resolveAsDirectory(id, root) {
            const base = [ root, id ].join(File.separator);
            const file = new File([ base, 'package.json' ].join(File.separator));
            if (file.exists()) {
                try {
                    const body = this.readFile(file.getCanonicalPath());
                    const pkg = JSON.parse(body);
                    if (pkg.main) {
                        return this.resolveAsFile(pkg.main, base)
                            || this.resolveAsDirectory(pkg.main, base);
                    }
                    return this.resolveAsFile('index.js', base);
                } catch (ex) {
                    throw new _.ModuleError('Cannot load JSON file', 'PARSE_ERROR', ex);
                }
            }
            return this.resolveAsFile('index.js', base);
        },
        resolveAsNodeModule(id, root) {
            const base = [ root, 'node_modules' ].join(File.separator);
            return this.resolveAsFile(id, base)
                || this.resolveAsDirectory(id, base)
                || (root ? this.resolveAsNodeModule(id, new File(root).getParent()) : false);
        },
    };

    let NativeRequire = (/* @IIFE */ function () {
        const o = this['NativeRequire'] || {};
        if (!o.require && typeof this.require === 'function') {
            o.require = this.require;
        }
        return o;
    }).call(global);

    /**
     * @type {Internal.Require}
     */
    const require = global.require = new _.RequireCtor();

    _.Module.require.resolve = function (id, parent) {
        return require.resolve(id, parent);
    };
    _.Module.require.paths = function () {
        return require.paths();
    };
    _.Module.require.cache = require.cache;
    _.Module.require.extensions = require.extensions;
    Object.defineProperty(_.Module.require, 'main', {
        get() {
            return require.main;
        },
    });
    Object.defineProperty(_.Module.require, 'root', {
        get() {
            return require.root;
        },
        set(value) {
            require.root = value;
        },
    });

    module.exports = _.Module;
})();
