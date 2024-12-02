module.exports = {
    __exitIfError__(action) {
        try {
            return action();
        } catch (e) {
            if (e instanceof java.lang.Throwable) {
                runtime.exit(e);
            } else if (e instanceof Error) {
                runtime.exit(new EvaluatorException(`${e.name}: ${e.message}`, e.fileName, e.lineNumber));
            } else {
                runtime.exit();
            }
        }
    },
    __asGlobal__(obj, functions, scope) {
        functions.forEach((name) => {
            let { objKey, scopeKey } = (/* @IIFE */ () => {
                if (typeof name === 'string') {
                    let objKey = scopeKey = name;
                    return { objKey, scopeKey };
                }
                if (typeof name === 'object') {
                    let [ objKey ] = Object.values(name);
                    let [ scopeKey ] = Object.keys(name);
                    return { objKey, scopeKey };
                }
                throw TypeError(`Unknown type of name (${name}) in functions for __asGlobal__`);
            })();
            let f = obj[objKey];
            if (typeof f !== 'function') {
                throw ReferenceError(`${objKey} doesn't exist on object: ${obj}`);
            }
            if (isObject(scope)) {
                scope[scopeKey] = f.bind(obj);
            } else {
                global[scopeKey] = f.bind(obj);
            }
        });
    },
};