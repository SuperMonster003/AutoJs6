/**
 * @param {org.autojs.autojs.runtime.ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Floaty}
 */
module.exports = function (scriptRuntime, scope) {
    const ProxyJavaObject = org.autojs.autojs.rhino.ProxyJavaObject;

    const rtFloaty = scriptRuntime.floaty;

    let _ = {
        Floaty: ( /* @IIFE */ () => {
            /**
             * @implements Internal.Floaty
             */
            const Floaty = function () {
                // Empty interface body.
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
            let proxyObject = new ProxyJavaObject(scope, window, getClass(window));
            proxyObject.__proxy__ = {
                set(name, value) {
                    window[name] = value;
                },
                get(name) {
                    let value = window[name];
                    if (typeof value === 'undefined') {
                        if (!value) {
                            value = window.findView(name);
                        }
                        if (!value) {
                            value = undefined;
                        }
                    }
                    return value;
                },
            };
            return proxyObject;
        },
    };

    /**
     * @type {Internal.Floaty}
     */
    const floaty = new _.Floaty();

    return floaty;
};