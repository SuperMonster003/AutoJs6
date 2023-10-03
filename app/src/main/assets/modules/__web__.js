// noinspection JSUnusedGlobalSymbols

/* Overwritten protection. */

let { http } = global;

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Web}
 */
module.exports = function (scriptRuntime, scope) {
    const Context = org.mozilla.javascript.Context;
    const InjectableWebView = org.autojs.autojs.core.web.InjectableWebView;
    const InjectableWebClient = org.autojs.autojs.core.web.InjectableWebClient;

    let _ = {
        Web: (/* @IIFE */ () => {
            /**
             * @implements Internal.Web
             */
            const Web = function () {
                /* Empty body. */
            };

            Web.prototype = {
                constructor: Web,
                newInjectableWebView(activity, url) {
                    if (arguments.length === 2) {
                        return new InjectableWebView(activity, Context.getCurrentContext(), scope, url);
                    }
                    if (arguments.length === 1) {
                        if (typeof arguments[0] === 'string') {
                            return this.newInjectableWebView(scope.activity, /* url = */ arguments[0]);
                        }
                        return this.newInjectableWebView(activity, /* url = */ null);
                    }
                    if (arguments.length === 0) {
                        return this.newInjectableWebView(scope.activity);
                    }
                },
                newInjectableWebClient() {
                    return new InjectableWebClient(Context.getCurrentContext(), scope);
                },
                newWebSocket(url) {
                    return new scope.WebSocket(url);
                },
            };

            return Web;
        })(),
        scopeAugment() {
            /**
             * @type {(keyof Internal.Web)[]}
             */
            let methods = [ 'newInjectableWebView', 'newInjectableWebClient', 'newWebSocket' ];
            __asGlobal__(web, methods, scope);

            const WebSocket = function () {
                if (!(this instanceof WebSocket)) {
                    throw TypeError('WebSocket must be called as constructor');
                }
                if (arguments[0] instanceof MutableOkHttp) {
                    this.client = arguments[0];
                    this.url = arguments[1];
                } else {
                    this.client = http.__okhttp__;
                    this.url = arguments[0];
                }
                if (typeof this.url !== 'string') {
                    throw TypeError(`Invalid url (value: ${this.url}, species: ${species(this.url)}) for WebSocket`);
                }
                return new org.autojs.autojs.core.web.WebSocket(this.client, this.url);
            };

            /* Append all primitive static fields. */
            Object.keys(org.autojs.autojs.core.web.WebSocket).forEach((key) => {
                let value = org.autojs.autojs.core.web.WebSocket[key]
                if (isPrimitive(value)) {
                    WebSocket[key] = value;
                }
            });

            scope.WebSocket = WebSocket;
        },
    };

    /**
     * @type {Internal.Web}
     */
    const web = new _.Web();

    _.scopeAugment();

    return web;
};