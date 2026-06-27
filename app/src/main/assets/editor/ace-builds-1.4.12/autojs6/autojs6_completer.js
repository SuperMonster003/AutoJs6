(function(global) {
    "use strict";

    var IDENTIFIER_REGEX = /[a-zA-Z_0-9$]/;

    function nameOf(item) {
        return item && (item.name || item.key || item.value);
    }

    function startsWithIgnoreCase(value, prefix) {
        value = String(value || "").toLowerCase();
        prefix = String(prefix || "").toLowerCase();
        return value.indexOf(prefix) === 0;
    }

    function builtinMember(name, signature, doc, type) {
        return {
            name: name,
            type: type || "function",
            doc: (signature || "") + (doc ? "\n" + doc : ""),
            signature: signature || ""
        };
    }

    var ECMASCRIPT_GLOBALS = [
        { name: "Object", type: "class", doc: "ECMAScript Object constructor." },
        { name: "Array", type: "class", doc: "ECMAScript Array constructor." },
        { name: "String", type: "class", doc: "ECMAScript String constructor." },
        { name: "Number", type: "class", doc: "ECMAScript Number constructor." },
        { name: "Boolean", type: "class", doc: "ECMAScript Boolean constructor." },
        { name: "Date", type: "class", doc: "ECMAScript Date constructor." },
        { name: "RegExp", type: "class", doc: "ECMAScript RegExp constructor." },
        { name: "Promise", type: "class", doc: "ECMAScript Promise constructor." },
        { name: "Function", type: "class", doc: "ECMAScript Function constructor." },
        { name: "Error", type: "class", doc: "ECMAScript Error constructor." },
        { name: "Math", type: "module", doc: "ECMAScript Math helpers." },
        { name: "JSON", type: "module", doc: "ECMAScript JSON helpers." },
        { name: "undefined", type: "variable", doc: "The undefined primitive value." },
        { name: "NaN", type: "variable", doc: "The Not-a-Number value." },
        { name: "Infinity", type: "variable", doc: "The numeric Infinity value." }
    ];

    var ECMASCRIPT_MODULES = {
        Object: [
            builtinMember("assign", "Object.assign(target: object, ...sources: object[]): object;", "Copies enumerable own properties from source objects to a target object."),
            builtinMember("create", "Object.create(proto: object | null, propertiesObject?: object): object;", "Creates a new object with the specified prototype object."),
            builtinMember("defineProperty", "Object.defineProperty(obj: object, prop: PropertyKey, descriptor: PropertyDescriptor): object;", "Defines or modifies a property on an object."),
            builtinMember("entries", "Object.entries(obj: object): [string, any][];", "Returns enumerable own string-keyed property pairs."),
            builtinMember("freeze", "Object.freeze(obj: object): object;", "Freezes an object."),
            builtinMember("fromEntries", "Object.fromEntries(entries: Iterable<any>): object;", "Transforms key-value pairs into an object."),
            builtinMember("getOwnPropertyDescriptor", "Object.getOwnPropertyDescriptor(obj: object, prop: PropertyKey): PropertyDescriptor | undefined;", "Returns an own property descriptor."),
            builtinMember("getOwnPropertyNames", "Object.getOwnPropertyNames(obj: object): string[];", "Returns own property names."),
            builtinMember("getPrototypeOf", "Object.getPrototypeOf(obj: object): object | null;", "Returns the prototype of an object."),
            builtinMember("hasOwn", "Object.hasOwn(obj: object, prop: PropertyKey): boolean;", "Checks whether an object has an own property."),
            builtinMember("keys", "Object.keys(obj: object): string[];", "Returns enumerable own string-keyed property names."),
            builtinMember("seal", "Object.seal(obj: object): object;", "Seals an object."),
            builtinMember("values", "Object.values(obj: object): any[];", "Returns enumerable own string-keyed property values.")
        ],
        "Object.prototype": [
            builtinMember("hasOwnProperty", "Object.prototype.hasOwnProperty(propertyKey: PropertyKey): boolean;", "Returns whether this object has the specified own property."),
            builtinMember("isPrototypeOf", "Object.prototype.isPrototypeOf(object: object): boolean;", "Returns whether this object exists in another object's prototype chain."),
            builtinMember("propertyIsEnumerable", "Object.prototype.propertyIsEnumerable(propertyKey: PropertyKey): boolean;", "Returns whether the specified own property is enumerable."),
            builtinMember("toString", "Object.prototype.toString(): string;", "Returns a string representation of the object."),
            builtinMember("valueOf", "Object.prototype.valueOf(): object;", "Returns the primitive value of the object.")
        ],
        Array: [
            builtinMember("from", "Array.from(arrayLike: ArrayLike<any> | Iterable<any>, mapFn?: Function, thisArg?: any): any[];", "Creates an array from an iterable or array-like object."),
            builtinMember("isArray", "Array.isArray(value: any): boolean;", "Checks whether a value is an array."),
            builtinMember("of", "Array.of(...items: any[]): any[];", "Creates an array from its arguments.")
        ],
        "Array.prototype": [
            builtinMember("at", "Array.prototype.at(index: number): any;", "Returns the item at the given index."),
            builtinMember("concat", "Array.prototype.concat(...items: any[]): any[];", "Combines arrays and values."),
            builtinMember("every", "Array.prototype.every(callback: Function, thisArg?: any): boolean;", "Tests whether all elements satisfy a callback."),
            builtinMember("filter", "Array.prototype.filter(callback: Function, thisArg?: any): any[];", "Creates an array with elements that satisfy a callback."),
            builtinMember("find", "Array.prototype.find(callback: Function, thisArg?: any): any;", "Returns the first element that satisfies a callback."),
            builtinMember("findIndex", "Array.prototype.findIndex(callback: Function, thisArg?: any): number;", "Returns the index of the first element that satisfies a callback."),
            builtinMember("flat", "Array.prototype.flat(depth?: number): any[];", "Flattens nested arrays."),
            builtinMember("flatMap", "Array.prototype.flatMap(callback: Function, thisArg?: any): any[];", "Maps and flattens one level."),
            builtinMember("forEach", "Array.prototype.forEach(callback: Function, thisArg?: any): void;", "Runs a callback for each element."),
            builtinMember("includes", "Array.prototype.includes(searchElement: any, fromIndex?: number): boolean;", "Checks whether an array includes a value."),
            builtinMember("indexOf", "Array.prototype.indexOf(searchElement: any, fromIndex?: number): number;", "Returns the first index of a value."),
            builtinMember("join", "Array.prototype.join(separator?: string): string;", "Joins array elements into a string."),
            builtinMember("map", "Array.prototype.map(callback: Function, thisArg?: any): any[];", "Creates an array by mapping each element."),
            builtinMember("pop", "Array.prototype.pop(): any;", "Removes and returns the last element."),
            builtinMember("push", "Array.prototype.push(...items: any[]): number;", "Appends items and returns the new length."),
            builtinMember("reduce", "Array.prototype.reduce(callback: Function, initialValue?: any): any;", "Reduces an array to a single value."),
            builtinMember("reverse", "Array.prototype.reverse(): any[];", "Reverses an array in place."),
            builtinMember("shift", "Array.prototype.shift(): any;", "Removes and returns the first element."),
            builtinMember("slice", "Array.prototype.slice(start?: number, end?: number): any[];", "Returns a shallow copy of part of an array."),
            builtinMember("some", "Array.prototype.some(callback: Function, thisArg?: any): boolean;", "Tests whether at least one element satisfies a callback."),
            builtinMember("sort", "Array.prototype.sort(compareFn?: Function): any[];", "Sorts an array in place."),
            builtinMember("splice", "Array.prototype.splice(start: number, deleteCount?: number, ...items: any[]): any[];", "Changes array contents in place."),
            builtinMember("unshift", "Array.prototype.unshift(...items: any[]): number;", "Prepends items and returns the new length.")
        ],
        String: [
            builtinMember("fromCharCode", "String.fromCharCode(...codes: number[]): string;", "Creates a string from UTF-16 code units."),
            builtinMember("fromCodePoint", "String.fromCodePoint(...codePoints: number[]): string;", "Creates a string from Unicode code points."),
            builtinMember("raw", "String.raw(strings: TemplateStringsArray, ...substitutions: any[]): string;", "Returns the raw string form of a template literal.")
        ],
        "String.prototype": [
            builtinMember("charAt", "String.prototype.charAt(index: number): string;", "Returns the character at an index."),
            builtinMember("charCodeAt", "String.prototype.charCodeAt(index: number): number;", "Returns the UTF-16 code unit at an index."),
            builtinMember("endsWith", "String.prototype.endsWith(searchString: string, endPosition?: number): boolean;", "Checks whether a string ends with another string."),
            builtinMember("includes", "String.prototype.includes(searchString: string, position?: number): boolean;", "Checks whether a string contains another string."),
            builtinMember("indexOf", "String.prototype.indexOf(searchString: string, position?: number): number;", "Returns the first index of a substring."),
            builtinMember("match", "String.prototype.match(regexp: RegExp | string): RegExpMatchArray | null;", "Matches a string against a regular expression."),
            builtinMember("padEnd", "String.prototype.padEnd(targetLength: number, padString?: string): string;", "Pads the end of a string."),
            builtinMember("padStart", "String.prototype.padStart(targetLength: number, padString?: string): string;", "Pads the start of a string."),
            builtinMember("replace", "String.prototype.replace(searchValue: string | RegExp, replaceValue: string): string;", "Replaces matched text."),
            builtinMember("replaceAll", "String.prototype.replaceAll(searchValue: string | RegExp, replaceValue: string): string;", "Replaces all matched text."),
            builtinMember("search", "String.prototype.search(regexp: RegExp | string): number;", "Searches for a regular expression match."),
            builtinMember("slice", "String.prototype.slice(start?: number, end?: number): string;", "Extracts part of a string."),
            builtinMember("split", "String.prototype.split(separator?: string | RegExp, limit?: number): string[];", "Splits a string into substrings."),
            builtinMember("startsWith", "String.prototype.startsWith(searchString: string, position?: number): boolean;", "Checks whether a string starts with another string."),
            builtinMember("substring", "String.prototype.substring(start: number, end?: number): string;", "Returns a substring."),
            builtinMember("toLowerCase", "String.prototype.toLowerCase(): string;", "Converts a string to lower case."),
            builtinMember("toUpperCase", "String.prototype.toUpperCase(): string;", "Converts a string to upper case."),
            builtinMember("trim", "String.prototype.trim(): string;", "Removes leading and trailing whitespace."),
            builtinMember("trimEnd", "String.prototype.trimEnd(): string;", "Removes trailing whitespace."),
            builtinMember("trimStart", "String.prototype.trimStart(): string;", "Removes leading whitespace.")
        ],
        Number: [
            builtinMember("isFinite", "Number.isFinite(value: any): boolean;", "Checks whether a value is a finite number."),
            builtinMember("isInteger", "Number.isInteger(value: any): boolean;", "Checks whether a value is an integer."),
            builtinMember("isNaN", "Number.isNaN(value: any): boolean;", "Checks whether a value is NaN."),
            builtinMember("parseFloat", "Number.parseFloat(string: string): number;", "Parses a string as a floating-point number."),
            builtinMember("parseInt", "Number.parseInt(string: string, radix?: number): number;", "Parses a string as an integer.")
        ],
        "Number.prototype": [
            builtinMember("toFixed", "Number.prototype.toFixed(fractionDigits?: number): string;", "Formats a number with fixed-point notation."),
            builtinMember("toPrecision", "Number.prototype.toPrecision(precision?: number): string;", "Formats a number to a given precision."),
            builtinMember("toString", "Number.prototype.toString(radix?: number): string;", "Returns a string representation of the number."),
            builtinMember("valueOf", "Number.prototype.valueOf(): number;", "Returns the primitive number value.")
        ],
        Math: [
            builtinMember("abs", "Math.abs(x: number): number;", "Returns the absolute value."),
            builtinMember("ceil", "Math.ceil(x: number): number;", "Rounds up."),
            builtinMember("floor", "Math.floor(x: number): number;", "Rounds down."),
            builtinMember("max", "Math.max(...values: number[]): number;", "Returns the largest value."),
            builtinMember("min", "Math.min(...values: number[]): number;", "Returns the smallest value."),
            builtinMember("pow", "Math.pow(base: number, exponent: number): number;", "Returns base raised to exponent."),
            builtinMember("random", "Math.random(): number;", "Returns a pseudo-random number in [0, 1)."),
            builtinMember("round", "Math.round(x: number): number;", "Rounds to the nearest integer."),
            builtinMember("sign", "Math.sign(x: number): number;", "Returns the sign of a number."),
            builtinMember("sqrt", "Math.sqrt(x: number): number;", "Returns the square root."),
            builtinMember("trunc", "Math.trunc(x: number): number;", "Truncates the fractional part.")
        ],
        JSON: [
            builtinMember("parse", "JSON.parse(text: string, reviver?: Function): any;", "Parses a JSON string."),
            builtinMember("stringify", "JSON.stringify(value: any, replacer?: Function | any[], space?: string | number): string;", "Converts a value to JSON text.")
        ],
        Date: [
            builtinMember("now", "Date.now(): number;", "Returns the current timestamp in milliseconds."),
            builtinMember("parse", "Date.parse(dateString: string): number;", "Parses a date string."),
            builtinMember("UTC", "Date.UTC(year: number, month: number, date?: number, hours?: number, minutes?: number, seconds?: number, ms?: number): number;", "Returns a UTC timestamp.")
        ],
        "Date.prototype": [
            builtinMember("getTime", "Date.prototype.getTime(): number;", "Returns the timestamp in milliseconds."),
            builtinMember("toISOString", "Date.prototype.toISOString(): string;", "Returns an ISO date string."),
            builtinMember("toLocaleString", "Date.prototype.toLocaleString(): string;", "Returns a locale-sensitive date string.")
        ],
        "RegExp.prototype": [
            builtinMember("exec", "RegExp.prototype.exec(string: string): RegExpExecArray | null;", "Executes a regular expression search."),
            builtinMember("test", "RegExp.prototype.test(string: string): boolean;", "Tests whether a regular expression matches a string.")
        ],
        Promise: [
            builtinMember("all", "Promise.all(iterable: Iterable<any>): Promise<any[]>;", "Resolves when all promises resolve."),
            builtinMember("allSettled", "Promise.allSettled(iterable: Iterable<any>): Promise<any[]>;", "Resolves after all promises settle."),
            builtinMember("any", "Promise.any(iterable: Iterable<any>): Promise<any>;", "Resolves when any promise resolves."),
            builtinMember("race", "Promise.race(iterable: Iterable<any>): Promise<any>;", "Resolves or rejects when the first promise settles."),
            builtinMember("reject", "Promise.reject(reason?: any): Promise<never>;", "Returns a rejected promise."),
            builtinMember("resolve", "Promise.resolve(value?: any): Promise<any>;", "Returns a resolved promise.")
        ],
        "Promise.prototype": [
            builtinMember("then", "Promise.prototype.then(onFulfilled?: Function, onRejected?: Function): Promise<any>;", "Adds fulfillment and rejection handlers."),
            builtinMember("catch", "Promise.prototype.catch(onRejected?: Function): Promise<any>;", "Adds a rejection handler."),
            builtinMember("finally", "Promise.prototype.finally(onFinally?: Function): Promise<any>;", "Adds a handler that runs when the promise settles.")
        ],
        "Function.prototype": [
            builtinMember("apply", "Function.prototype.apply(thisArg: any, argArray?: any): any;", "Calls a function with a this value and array-like arguments."),
            builtinMember("bind", "Function.prototype.bind(thisArg: any, ...args: any[]): Function;", "Creates a bound function."),
            builtinMember("call", "Function.prototype.call(thisArg: any, ...args: any[]): any;", "Calls a function with a this value and arguments.")
        ],
        "Boolean.prototype": [
            builtinMember("toString", "Boolean.prototype.toString(): string;", "Returns a string representation of the boolean."),
            builtinMember("valueOf", "Boolean.prototype.valueOf(): boolean;", "Returns the primitive boolean value.")
        ],
        "Error.prototype": [
            builtinMember("toString", "Error.prototype.toString(): string;", "Returns a string representation of the error.")
        ]
    };

    function toCompletion(item, score, options) {
        var name = nameOf(item);
        var completion = {
            caption: options && options.caption || item.caption || name,
            value: options && options.value || item.value || name,
            meta: options && options.meta || item.meta || item.type || "autojs",
            score: score,
            docText: item.doc || item.summary || item.signature || ""
        };
        if (item.snippet) {
            completion.snippet = item.snippet;
        } else if (item.value && item.value.indexOf("${") >= 0) {
            completion.snippet = item.value;
        }
        return completion;
    }

    function normalizeProperty(property) {
        return {
            name: property.name || property.key,
            value: property.value,
            type: property.type || property.kind || (property.variable ? "variable" : "function"),
            doc: property.doc || property.summary || "",
            signature: property.signature,
            meta: property.meta
        };
    }

    function normalizeIndices(indices) {
        var normalized = {
            globals: [],
            modules: Object.create(null),
            snippets: indices && indices.snippets || []
        };

        if (Array.isArray(indices)) {
            indices.forEach(function(moduleJson) {
                var moduleName = moduleJson.name;
                var properties = moduleJson.properties || [];
                if (moduleName !== "globals" && moduleName !== "global") {
                    normalized.globals.push({
                        name: moduleName,
                        type: "module",
                        doc: moduleJson.summary || ""
                    });
                    normalized.modules[moduleName] = properties.map(normalizeProperty);
                }
                properties.forEach(function(property) {
                    if (property.global) {
                        normalized.globals.push(normalizeProperty(property));
                    }
                });
            });
            return appendEcmascriptBuiltins(normalized);
        }

        indices = indices || {};
        normalized.globals = indices.globals || [];
        if (Array.isArray(indices.modules)) {
            indices.modules.forEach(function(moduleJson) {
                normalized.modules[moduleJson.name] = (moduleJson.properties || []).map(normalizeProperty);
            });
        } else {
            var modules = indices.modules || {};
            Object.keys(modules).forEach(function(moduleName) {
                if (Array.isArray(modules[moduleName])) {
                    normalized.modules[moduleName] = modules[moduleName];
                }
            });
        }
        return appendEcmascriptBuiltins(normalized);
    }

    function appendUniqueByName(items, additions, normalizer) {
        (additions || []).forEach(function(addition) {
            var item = normalizer ? normalizer(addition) : addition;
            if (!findByName(items, nameOf(item))) {
                items.push(item);
            }
        });
    }

    function appendEcmascriptBuiltins(source) {
        appendUniqueByName(source.globals, ECMASCRIPT_GLOBALS);
        Object.keys(ECMASCRIPT_MODULES).forEach(function(moduleName) {
            if (!Array.isArray(source.modules[moduleName])) {
                source.modules[moduleName] = [];
            }
            appendUniqueByName(source.modules[moduleName], ECMASCRIPT_MODULES[moduleName], normalizeProperty);
        });
        return source;
    }

    function membersForModule(source, moduleName) {
        if (!source || !source.modules || !Object.prototype.hasOwnProperty.call(source.modules, moduleName)) {
            return null;
        }
        var members = source.modules[moduleName];
        return Array.isArray(members) ? members : null;
    }

    function findMemberContext(session, pos) {
        var line = session.getLine(pos.row).substring(0, pos.column);
        var match = /((?:[A-Za-z_$][A-Za-z0-9_$]*\.)*[A-Za-z_$][A-Za-z0-9_$]*)\.([A-Za-z_$][A-Za-z0-9_$]*)?$/.exec(line);
        if (!match) {
            return null;
        }
        return {
            moduleName: match[1],
            memberPrefix: match[2] || ""
        };
    }

    function identifierAt(line, column) {
        line = String(line || "");
        column = Math.max(0, Math.min(Number(column) || 0, line.length));
        var probe = column;
        if (probe >= line.length || !IDENTIFIER_REGEX.test(line.charAt(probe))) {
            probe = column - 1;
        }
        if (probe < 0 || !IDENTIFIER_REGEX.test(line.charAt(probe))) {
            return null;
        }
        var start = probe;
        var end = probe + 1;
        while (start > 0 && IDENTIFIER_REGEX.test(line.charAt(start - 1))) {
            start--;
        }
        while (end < line.length && IDENTIFIER_REGEX.test(line.charAt(end))) {
            end++;
        }
        return {
            name: line.substring(start, end),
            startColumn: start,
            endColumn: end
        };
    }

    function findByName(items, name) {
        var expected = String(name || "");
        for (var i = 0; i < (items || []).length; i++) {
            if (nameOf(items[i]) === expected) {
                return items[i];
            }
        }
        return null;
    }

    function membersForContext(source, moduleName) {
        var members = membersForModule(source, moduleName);
        if (members) {
            return members;
        }
        if (moduleName && moduleName.indexOf(".") < 0) {
            return membersForModule(source, "Object.prototype");
        }
        if (/\.prototype$/.test(moduleName || "")) {
            return membersForModule(source, "Object.prototype");
        }
        return null;
    }

    function toHover(item, caption, meta, row, token) {
        if (!item) {
            return null;
        }
        var docText = item.doc || item.summary || item.signature || "";
        if (!docText) {
            return null;
        }
        return {
            caption: caption || nameOf(item),
            value: item.value || nameOf(item),
            meta: meta || item.meta || item.type || "autojs",
            docText: docText,
            signature: item.signature || "",
            range: {
                start: { row: row, column: token.startColumn },
                end: { row: row, column: token.endColumn }
            }
        };
    }

    function findHover(source, session, pos) {
        if (!session || !session.getLine || !pos) {
            return null;
        }
        var row = Math.max(0, Number(pos.row) || 0);
        var line = session.getLine(row) || "";
        var token = identifierAt(line, pos.column);
        if (!token) {
            return null;
        }

        var beforeToken = line.substring(0, token.startColumn);
        var memberMatch = /((?:[A-Za-z_$][A-Za-z0-9_$]*\.)*[A-Za-z_$][A-Za-z0-9_$]*)\s*\.\s*$/.exec(beforeToken);
        var members = memberMatch && membersForContext(source, memberMatch[1]);
        if (members) {
            var member = findByName(members, token.name);
            return toHover(member, memberMatch[1] + "." + token.name, memberMatch[1], row, token);
        }

        var globalItem = findByName(source.globals, token.name);
        return toHover(globalItem, token.name, globalItem && (globalItem.meta || globalItem.type), row, token);
    }

    function retrievePrecedingIdentifier(text, pos) {
        var buf = [];
        for (var i = pos - 1; i >= 0; i--) {
            if (IDENTIFIER_REGEX.test(text[i]) || (i === pos - 1 && text[i] === ".")) {
                buf.push(text[i]);
            } else {
                break;
            }
        }
        return buf.reverse().join("");
    }

    function collect(items, prefix, score, mapper) {
        var completions = [];
        (items || []).forEach(function(item) {
            var name = nameOf(item);
            if (name && startsWithIgnoreCase(name, prefix)) {
                completions.push(mapper ? mapper(item, name) : toCompletion(item, score));
            }
        });
        return completions;
    }

    function dedupe(completions) {
        var seen = Object.create(null);
        return completions.filter(function(item) {
            var key = item.caption || item.value || item.snippet || "";
            if (seen[key]) {
                return false;
            }
            seen[key] = true;
            return true;
        });
    }

    function createCompleter(indices) {
        var source = normalizeIndices(indices);
        return {
            retrievePrecedingIdentifier: retrievePrecedingIdentifier,

            getCompletions: function(editor, session, pos, prefix, callback) {
                var memberContext = findMemberContext(session, pos);
                var members = memberContext && membersForContext(source, memberContext.moduleName);
                if (members) {
                    var replaceWithQualifiedName = (prefix || "").indexOf(".") >= 0;
                    callback(null, dedupe(collect(
                        members,
                        memberContext.memberPrefix,
                        1000,
                        function(item, name) {
                            var qualifiedName = memberContext.moduleName + "." + name;
                            return toCompletion(item, 1000, {
                                caption: replaceWithQualifiedName ? qualifiedName : name,
                                value: replaceWithQualifiedName ? qualifiedName : name,
                                meta: memberContext.moduleName
                            });
                        }
                    )));
                    return;
                }

                if (!prefix) {
                    callback(null, []);
                    return;
                }

                callback(null, dedupe(
                    collect(source.snippets, prefix, 950)
                        .concat(collect(source.globals, prefix, 900))
                ));
            },

            getDocTooltip: function(item) {
                if (!item || !item.docText) {
                    return null;
                }
                return { docText: item.docText };
            },

            getHover: function(session, pos) {
                return findHover(source, session, pos);
            }
        };
    }

    var activeCompleter = null;

    global.AutoJsAceCompleter = {
        createCompleter: createCompleter,

        install: function(ace) {
            var languageTools = ace.require("ace/ext/language_tools");
            activeCompleter = createCompleter(global.AutoJsAceIndices || global.AUTOJS_INDICES || {});
            languageTools.addCompleter(activeCompleter);
            return activeCompleter;
        },

        getActiveCompleter: function() {
            return activeCompleter;
        }
    };
})(window);
