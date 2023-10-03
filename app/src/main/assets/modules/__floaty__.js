// noinspection UnnecessaryLocalVariableJS

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Floaty}
 */
module.exports = function (scriptRuntime, scope) {
    const rtFloaty = scriptRuntime.floaty;

    let _ = {
        Floaty: (/* @IIFE */ () => {
            /**
             * @implements Internal.Floaty
             */
            const Floaty = function () {
                /* Empty body. */
            };

            Floaty.prototype = {
                constructor: Floaty,
                closeAll() {
                    rtFloaty.closeAll();
                },
                window(xml) {
                    return _.wrap(rtFloaty.window.bind(rtFloaty), xml);
                },
                rawWindow(xml) {
                    return _.wrap(rtFloaty.rawWindow.bind(rtFloaty), xml);
                },
                hasPermission: () => rtFloaty.hasPermission(),
                requestPermission: () => rtFloaty.requestPermission(),
                ensurePermission: () => rtFloaty.ensurePermission(),
            };

            return Floaty;
        })(),
        toXMLStringIfNeeded(xml) {
            // noinspection JSTypeOfValues
            return typeof xml === 'xml' ? xml.toXMLString() : String(xml);
        },
        /**
         * @param {(f: (context: android.content.Context, parent: android.view.ViewGroup) => android.view.View)
         *     => org.autojs.autojs.runtime.api.Floaty.JsResizableWindow
         *      | org.autojs.autojs.runtime.api.Floaty.JsRawWindow} windowFunction
         * @param {Xml} xml
         * @return {org.autojs.autojs.rhino.ProxyJavaObject|android.view.View}
         */
        wrap(windowFunction, xml) {
            let { layoutInflater } = scriptRuntime.ui;
            let window = windowFunction(function (context, parent) {
                layoutInflater.setContext(context);
                return layoutInflater.inflate(_.toXMLStringIfNeeded(xml), parent, true);
            });
            return Object.assign(new ProxyJavaObject(scope, window, {
                set(name, value) {
                    window[name] = value;
                },
                get(name) {
                    let value = window[name];
                    return typeof value !== 'undefined' ? value : window.findView(name) || undefined;
                },
            }));
        },
    };

    /**
     * @type {Internal.Floaty}
     */
    const floaty = new _.Floaty();

    return floaty;
};