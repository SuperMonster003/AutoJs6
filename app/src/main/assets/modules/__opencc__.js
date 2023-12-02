// noinspection JSUnusedGlobalSymbols,JSUnusedLocalSymbols,UnnecessaryLocalVariableJS

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.OpenCC}
 */
module.exports = function (scriptRuntime, scope) {

    const ChineseConverter = com.zqc.opencc.android.lib.ChineseConverter;
    const ConversionType = com.zqc.opencc.android.lib.ConversionType;

    let _ = {
        OpenCC: (/* @IIFE */ () => {
            /**
             * @extends Internal.OpenCC
             */
            const OpenCC = function () {
                return Object.assign(function () {
                    return OpenCC.prototype.convert.apply(OpenCC.prototype, arguments);
                }, OpenCC.prototype);
            };

            OpenCC.prototype = {
                constructor: OpenCC,
                convert(s, type) {
                    if (typeof type === 'string') {
                        let niceType = type.toUpperCase();
                        if (niceType in ConversionType) {
                            return ChineseConverter.convert(s, ConversionType[niceType], context);
                        }
                        return OpenCC.prototype[type.toLowerCase()](s);
                    }
                    if (type instanceof ConversionType) {
                        return ChineseConverter.convert(s, type, context);
                    }
                    throw TypeError(`Unknown type "${type}" for opencc.convert`);
                },

                /* OpenCC conversion. */

                hk2s: (s) => /* Stub. */ '',
                hk2t: (s) => /* Stub. */ '',
                jp2t: (s) => /* Stub. */ '',
                s2hk: (s) => /* Stub. */ '',
                s2t: (s) => /* Stub. */ '',
                s2tw: (s) => /* Stub. */ '',
                t2hk: (s) => /* Stub. */ '',
                t2jp: (s) => /* Stub. */ '',
                t2s: (s) => /* Stub. */ '',
                t2tw: (s) => /* Stub. */ '',
                tw2s: (s) => /* Stub. */ '',
                tw2t: (s) => /* Stub. */ '',

                /* OpenCC conversion obsoleted by AutoJs6. */

                /** @deprecated */
                s2twp: (s) => /* Stub. */ '',
                s2twi: (s) => opencc.convert(s, ConversionType.S2TWP),
                /** @deprecated */
                tw2sp: (s) => /* Stub. */ '',
                twi2s: (s) => opencc.convert(s, ConversionType.TW2SP),

                /* Encapsulated conversion. */

                s2jp: (s) => opencc.t2jp(opencc.s2t(s)),
                t2twi: (s) => opencc.s2twi(opencc.t2s(s)),
                hk2tw: (s) => opencc.t2tw(opencc.hk2t(s)),
                hk2twi: (s) => opencc.s2twi(opencc.hk2s(s)),
                hk2jp: (s) => opencc.t2jp(opencc.hk2t(s)),
                tw2hk: (s) => opencc.t2hk(opencc.tw2t(s)),
                tw2twi: (s) => opencc.s2twi(opencc.tw2s(s)),
                tw2jp: (s) => opencc.t2jp(opencc.tw2t(s)),
                twi2t: (s) => opencc.s2t(opencc.twi2s(s)),
                twi2hk: (s) => opencc.s2hk(opencc.twi2s(s)),
                twi2tw: (s) => opencc.s2tw(opencc.twi2s(s)),
                twi2jp: (s) => opencc.t2jp(opencc.s2t(opencc.twi2s(s))),
                jp2s: (s) => opencc.t2s(opencc.jp2t(s)),
                jp2hk: (s) => opencc.t2hk(opencc.jp2t(s)),
                jp2tw: (s) => opencc.t2tw(opencc.jp2t(s)),
                jp2twi: (s) => opencc.s2twi(opencc.t2s(opencc.jp2t(s))),
            };

            Object.keys(ConversionType).forEach((key) => {
                OpenCC.prototype[key.toLowerCase()] = (s) => ChineseConverter.convert(s, ConversionType[key], context);
            });

            return OpenCC;
        })(),
    };

    /**
     * @type {Internal.OpenCC}
     */
    const opencc = new _.OpenCC();

    return opencc;
};