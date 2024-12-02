// noinspection JSUnusedGlobalSymbols,JSUnusedLocalSymbols,UnnecessaryLocalVariableJS

!function() {

    const RtFiles = org.autojs.autojs.runtime.api.Files;

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
                    if (!runtime.files.isDir(relativePath)) {
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
                    if (!runtime.files.isDir(_.config.path)) {
                        let fallback = RtFiles.join('assets', _.config.path);
                        if (runtime.files.isDir(fallback)) {
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
                            messageSource = RtFiles.join(this.getPath(), `${messageSource}.json`);
                        }
                        let path = runtime.files.path(messageSource);
                        if (!runtime.files.isFile(path)) {
                            throw Error(`Invalid path: ${path}`);
                        }
                        try {
                            return this.load(JSON.parse(runtime.files.read(path)), locale);
                        } catch (e) {
                            runtime.console.warn(`${e.message}\n${e.stack}`);
                            throw Error(`Failed to parse JSON file: ${path}`);
                        }
                    }
                    throw TypeError(`Unknown message source: ${messageSource}`);
                },
                loadAll() {
                    runtime.files
                        .listDir(this.getPath(), file => runtime.files.getExtension(file).toLowerCase() === 'json')
                        .forEach((file) => this.load(runtime.files.getNameWithoutExtension(file)));
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

    i18n.loadAll();
    i18n.setLocale('default');

    module.exports = i18n;

}();