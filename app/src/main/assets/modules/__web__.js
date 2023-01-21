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
        Web: ( /* @IIFE */ () => {
            /**
             * @implements Internal.Web
             */
            const Web = function () {
                // Empty interface body.
            };

            Web.prototype = {
                constructor: Web,
            };

            return Web;
        })(),
        scopeAugment() {
            Object.assign(scope, {
                /**
                 * @global
                 */
                newInjectableWebClient() {
                    return new InjectableWebClient(Context.getCurrentContext(), scope);
                },
                /**
                 * @global
                 */
                newInjectableWebView(activity) {
                    let ctx = activity || scope.activity;
                    return new InjectableWebView(ctx, Context.getCurrentContext(), scope);
                },
            });
        },
    };

    /**
     * @type {Internal.Web}
     */
    const web = new _.Web();

    _.scopeAugment();

    return web;
};