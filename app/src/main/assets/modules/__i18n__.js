// noinspection JSUnusedGlobalSymbols

/* Overwritten protection. */

let { files } = global;

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.I18n}
 */
module.exports = function (scriptRuntime, scope) {
    let _ = {
        I18n: (/* @IIFE */ () => {
            /**
             * @type {Internal.Banana}
             */
            const banana = new (require('banana-i18n'))(void 0, { finalFallback: 'default' });

            /**
             * @implements Internal.I18n
             */
            const I18n = function () {
                return Object.assign(banana.i18n.bind(banana), I18n.prototype);
            };

            I18n.prototype = {
                constructor: I18n,
                banana,
                setPath(relativePath) {
                    if (!files.isDir(relativePath)) {
                        throw Error(`Invalid path: ${relativePath}`);
                    }
                    _.config.path = relativePath;
                },
                setLocale(locale) {
                    banana.setLocale(locale);
                },
                getFallbackLocales() {
                    return banana.getFallbackLocales();
                },
                getParser() {
                    return banana.parser;
                },
                getPath() {
                    if (!files.isDir(_.config.path)) {
                        let fallback = files.join('assets', _.config.path);
                        if (files.isDir(fallback)) {
                            this.setPath(fallback);
                        }
                    }
                    return _.config.path;
                },
                getLocale() {
                    return banana.locale;
                },
                getFinalFallback() {
                    return banana.finalFallback;
                },
                load(messageSource, locale) {
                    if (typeof messageSource === 'object') {
                        return banana.load(messageSource, locale);
                    }
                    if (typeof messageSource === 'string') {
                        if (arguments.length === 1) {
                            return this.load(messageSource, messageSource);
                        }
                        if (!`${messageSource}`.includes(java.io.File.separator)) {
                            messageSource = files.join(this.getPath(), `${messageSource}.json`);
                        }
                        let path = files.path(messageSource);
                        if (!files.isFile(path)) {
                            throw Error(`Invalid path: ${path}`);
                        }
                        try {
                            return this.load(JSON.parse(files.read(path)), locale);
                        } catch (e) {
                            scriptRuntime.console.warn(`${e.message}\n${e.stack}`);
                            throw Error(`Failed to parse JSON file: ${path}`);
                        }
                    }
                    throw TypeError(`Unknown message source: ${messageSource}`);
                },
                loadAll() {
                    files
                        .listDir(this.getPath(), file => files.getExtension(file).toLowerCase() === 'json')
                        .forEach((file) => this.load(files.getNameWithoutExtension(file)));
                },
            };

            return I18n;
        })(),
        config: { path: 'i18n' },
    };

    /**
     * @type {Internal.I18n}
     */
    const i18n = new _.I18n();

    return i18n;
};