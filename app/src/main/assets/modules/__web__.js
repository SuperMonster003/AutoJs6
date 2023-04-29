// noinspection JSUnusedGlobalSymbols

/**
 * @param {org.autojs.autojs.runtime.ScriptRuntime} scriptRuntime
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
                // Empty interface body.
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
            };

            return Web;
        })(),
        scopeAugment() {
            /**
             * @type {(keyof Internal.Web)[]}
             */
            let methods = [ 'newInjectableWebView', 'newInjectableWebClient' ];
            __asGlobal__(web, methods, scope);
        },
    };

    /**
     * @type {Internal.Web}
     */
    const web = new _.Web();

    _.scopeAugment();

    return web;
};