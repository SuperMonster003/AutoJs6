// noinspection JSUnusedGlobalSymbols

/* Here, importClass() is not recommended for intelligent code completion in IDE like WebStorm. */
/* The same is true of destructuring assignment syntax (like `let {Uri} = android.net`). */

const Context = org.mozilla.javascript.Context;
const InjectableWebView = com.stardust.autojs.core.web.InjectableWebView;
const InjectableWebClient = com.stardust.autojs.core.web.InjectableWebClient;

let _ = {
    init(__runtime__, scope) {
        this.runtime = __runtime__;
        this.scope = scope;

        this.web = {};
        this.currentContext = Context.getCurrentContext();
    },
    getModule() {
        return this.web;
    },
    selfAugment() {
        Object.assign(this.web, {
            // Empty augmentations so far
        });
    },
    scopeAugment() {
        Object.assign(this.scope, {
            newInjectableWebClient() {
                return new InjectableWebClient(_.currentContext, _.scope);
            },
            newInjectableWebView(activity) {
                /**
                 * @type {android.content.Context}
                 */
                let context = activity || _.scope.activity;
                return new InjectableWebView(context, _.currentContext, _.scope);
            },
        });
    },
};

let $ = {
    getModule(__runtime__, scope) {
        _.init(__runtime__, scope);

        _.selfAugment();
        _.scopeAugment();

        return _.getModule();
    },
};

/**
 * @param {com.stardust.autojs.runtime.ScriptRuntime} __runtime__
 * @param {org.mozilla.javascript.Scriptable} scope
 * @return {Internal.Web}
 */
module.exports = function (__runtime__, scope) {
    return $.getModule(__runtime__, scope);
};