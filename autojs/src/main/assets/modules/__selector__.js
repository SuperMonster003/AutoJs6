let _ = {
    init(__runtime__, scope) {
        Object.assign(this, {
            runtime: __runtime__,
            scope: scope,
            getRuntimeSelector() {
                return __runtime__.selector();
            },
        });

        // noinspection JSValidateTypes
        this.javaObjectSample = new java.lang.Object();
    },
    getModule() {
        return this.getRuntimeSelector.bind(this);
    },
    scopeAugment() {
        for (let method in this.getRuntimeSelector()) {
            if (this.isInJavaObject(method) || this.isInScope(method)) {
                continue;
            }
            // @Caution by SuperMonster003 as of Apr 26, 2022.
            //  ! Make param "method" scoped to this IIFE immediately.
            //  ! Unwrapping this IIFE will cause TypeError.
            //  ! Reappearance: let f = idMatches; f(/.+/).findOnce();
            //  ! TypeError: Cannot find function findOnce in object true.
            // @ScopeBinding
            this.scope[method] = ( /* @IIFE */ (method) => {
                return function () {
                    let s = global.selector();
                    return s[method].apply(s, arguments);
                };
            })(method);
        }
    },
    isInJavaObject(key) {
        return key in this.javaObjectSample;
    },
    isInScope(key) {
        return key in this.scope;
    },
};

let $ = {
    getModule(__runtime__, scope) {
        _.init(__runtime__, scope);

        _.scopeAugment();

        return _.getModule();
    },
};

/**
 * @param {com.stardust.autojs.runtime.ScriptRuntime} __runtime__
 * @param {org.mozilla.javascript.Scriptable} scope
 * @return {Internal.Selector}
 */
module.exports = function (__runtime__, scope) {
    return $.getModule(__runtime__, scope);
};