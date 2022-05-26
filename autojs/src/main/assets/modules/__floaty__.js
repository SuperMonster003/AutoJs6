/* Here, importClass() is not recommended for intelligent code completion in IDE like WebStorm. */
/* The same is true of destructuring assignment syntax (like `let {Uri} = android.net`). */

const ProxyJavaObject = com.stardust.autojs.rhino.ProxyJavaObject;

let _ = {
    init(__runtime__, scope) {
        this.runtime = __runtime__;
        this.scope = scope;

        /**
         * @type {com.stardust.autojs.runtime.api.Floaty}
         */
        this.rtFloaty = __runtime__.floaty;
        this.floaty = {};

        this.layoutInflater = this.runtime.ui.layoutInflater;
    },
    getModule() {
        return this.floaty;
    },
    selfAugment() {
        Object.assign(this.floaty, {
            closeAll: () => _.rtFloaty.closeAll(),
            window: xml => _.wrap(_.rtFloaty.window.bind(_.rtFloaty), xml),
            rawWindow: xml => _.wrap(_.rtFloaty.rawWindow.bind(_.rtFloaty), xml),
        });
    },
    toXMLStringIfNeeded(xml) {
        // noinspection JSTypeOfValues
        return typeof xml === 'xml' ? xml.toXMLString() : String(xml);
    },
    /**
     * @param {(f: (context: android.content.Context, parent: android.view.ViewGroup) => android.view.View)
     *     => com.stardust.autojs.runtime.api.Floaty.JsResizableWindow
     *      | com.stardust.autojs.runtime.api.Floaty.JsRawWindow} windowFunction
     * @param {Xml} xml
     * @return {com.stardust.autojs.rhino.ProxyJavaObject|android.view.View}
     */
    wrap(windowFunction, xml) {
        let window = windowFunction(function (context, parent) {
            _.layoutInflater.setContext(context);
            return _.layoutInflater.inflate(_.toXMLStringIfNeeded(xml), parent, true);
        });
        let proxyObject = new ProxyJavaObject(_.scope, window, getClass(window));
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

let $ = {
    getModule(__runtime__, scope) {
        _.init(__runtime__, scope);

        _.selfAugment();

        return _.getModule();
    },
};

/**
 * @param {com.stardust.autojs.runtime.ScriptRuntime} __runtime__
 * @param {org.mozilla.javascript.Scriptable} scope
 * @return {Internal.Floaty}
 */
module.exports = function (__runtime__, scope) {
    return $.getModule(__runtime__, scope);
};