// noinspection JSUnusedLocalSymbols,UnnecessaryLocalVariableJS

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.S13n}
 */
module.exports = function (scriptRuntime, scope) {
    let _ = {
        S13n: (/* @IIFE */ () => {
            /**
             * @extends Internal.S13n
             */
            const S13n = function () {
                /* Empty body. */
            };

            S13n.prototype = {
                constructor: S13n,
                color(o) {
                    if (typeof o === 'string') {
                        // ColorHex | ColorName
                        return Color(o).toInt();
                    }
                    if (typeof o === 'number') {
                        // ColorInt
                        return Color(o).toInt(); /* For numbers like 0xRRGGBB. */
                    }
                    if (o instanceof Color) {
                        return o.toInt();
                    }
                    if (o instanceof org.autojs.autojs.theme.ThemeColor) {
                        return o.getColorPrimary();
                    }
                    throw TypeError(`Failed to make "{value: ${o}, species: ${species(o)}})" a color being`);
                },
                throwable(o) {
                    if (o instanceof java.lang.Throwable) {
                        return o;
                    }
                    if (typeof o === 'string') {
                        return new java.lang.Exception(o);
                    }
                    if (util.getClassName(o) === 'org.mozilla.javascript.NativeError') {
                        return o.rhinoException || o.javaException || this.throwable(o.message);
                    }
                    throw TypeError(`Failed to make "{value: ${o}, species: ${species(o)}})" a throwable being`);
                },
            };

            return S13n;
        })(),
    };

    /**
     * @type {Internal.S13n}
     */
    const s13n = new _.S13n();

    return s13n;
};