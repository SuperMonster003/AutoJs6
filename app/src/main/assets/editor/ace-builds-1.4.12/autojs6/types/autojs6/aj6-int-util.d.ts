// Type definitions for AutoJs6 internal module util
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 4.7.4
//
// Last modified: Nov 8, 2021

/// <reference path="../index.d.ts" />

/**
 * @Source %AutoJs6Assets%/modules/__util__.js
 */

declare namespace Internal {

    interface Util {

        version: Util.Version;

        java: Util.Java;

        inspect: Util.Inspect;

        /**
         * @example Use method with argument(s) to get an object with information
         * let str = 'love';
         * let morse = util.morseCode(str);
         * morse.pattern; // same as morse.getPattern()
         * morse.code; // same as morse.getCode()
         * morse.vibrate();
         *
         * @example Use method's
         * let str = 'love';
         * util.morseCode.getPattern(str); // [100, 100, 300, ...] -- without delay
         * util.morseCode.getCode(str); // '·-··   ---   ···-   ·'
         * util.morseCode.vibrate(str);
         */
        morseCode: Util.MorseCode;

        /**
         * @example Property util.versionCodes.XXX
         * console.log(util.versionCodes.R);
         * console.log(util.versionCodes.O);
         * console.log(util.versionCodes.O_MR1);
         *
         * @example Method util.versionCodes.toString()
         * console.log(util.versionCodes.toString('detail'));
         *
         * @example Method util.versionCodes.search()
         * console.log(util.versionCodes.search(30));
         * console.log(util.versionCodes.search('R'));
         * console.log(util.versionCodes.search('Android 11'));
         *
         * console.log(util.versionCodes.search('oreo'));
         * console.log(util.versionCodes.search('pie'));
         *
         * console.log(util.versionCodes.search('cookie'));
         * console.log(util.versionCodes.search('snow'));
         * console.log(util.versionCodes.search('nougat'));
         * console.log(util.versionCodes.search('jelly bean'));
         *
         * console.log(util.versionCodes.search('7.1.1'));
         * console.log(util.versionCodes.search('7.1'));
         * console.log(util.versionCodes.search(7.1));
         * console.log(util.versionCodes.search('8.0'));
         * console.log(util.versionCodes.search('8'));
         * console.log(util.versionCodes.search(8));
         *
         * console.log(util.versionCodes.search('2022'));
         * console.log(util.versionCodes.search(2022));
         * console.log(util.versionCodes.search(2011).map(o => [o.platformVersion, o.releaseName, o.releaseDate]));
         *
         * console.log(util.versionCodes.search('2022 Mar'));
         * console.log(util.versionCodes.search('2022 March'));
         * console.log(util.versionCodes.search('2022 3'));
         * console.log(util.versionCodes.search('2022/3'));
         * console.log(util.versionCodes.search('2022/3'));
         * console.log(util.versionCodes.search('2022/03'));
         * console.log(util.versionCodes.search('Mar 2022'));
         * console.log(util.versionCodes.search('March 2022'));
         * console.log(util.versionCodes.search('Mar, 2022'));
         * console.log(util.versionCodes.search('March, 2022'));
         *
         * console.log(util.versionCodes.search('2022 Mar 07'));
         * console.log(util.versionCodes.search('2022 Mar 7'));
         * console.log(util.versionCodes.search('2022 March 7'));
         * console.log(util.versionCodes.search('2022 3 7'));
         * console.log(util.versionCodes.search('2022/3/7'));
         * console.log(util.versionCodes.search('2022/3/07'));
         * console.log(util.versionCodes.search('2022/03/07'));
         * console.log(util.versionCodes.search('Mar 7 2022'));
         * console.log(util.versionCodes.search('March 7 2022'));
         * console.log(util.versionCodes.search('Mar 7, 2022'));
         * console.log(util.versionCodes.search('March 7, 2022'));
         *
         * console.log(util.versionCodes.search(new Date(2022, 3 - 1, 7)));
         */
        versionCodes: Util.VersionCodes;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * exports.__assignFunctions__ = function (src, target, functions) {
         *     for (let f of functions) {
         *         target[f] = src[f].bind(src);
         *     }
         * };
         */
        __assignFunctions__(src: object, target: object, functions: string[]): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * exports.isArray = isArray;
         * function isArray(ar) {
         *     return Array.isArray(ar);
         * }
         */
        isArray(arg: any): boolean;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * exports.isBoolean = isBoolean;
         * function isBoolean(arg) {
         *     return typeof arg === 'boolean';
         * }
         */
        isBoolean(arg: any): boolean;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * exports.isNull = isNull;
         * function isNull(arg) {
         *     return arg === null;
         * }
         */
        isNull(arg: any): boolean;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * exports.isNullOrUndefined = isNullOrUndefined;
         * function isNullOrUndefined(arg) {
         *     return arg == null;
         * }
         */
        isNullOrUndefined(arg: any): boolean;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * exports.isNumber = isNumber;
         * function isNumber(arg) {
         *     return typeof arg === 'number';
         * }
         */
        isNumber(arg: any): boolean;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * exports.isString = isString;
         * function isString(arg) {
         *     return typeof arg === 'string';
         * }
         */
        isString(arg: any): boolean;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * exports.isSymbol = isSymbol;
         * function isSymbol(arg) {
         *     return typeof arg === 'symbol';
         * }
         */
        isSymbol(arg: any): boolean;

        isBigInt(arg: any): boolean;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * exports.isUndefined = isUndefined;
         * function isUndefined(arg) {
         *     return arg === void 0;
         * }
         */
        isUndefined(arg: any): boolean;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * exports.isRegExp = isRegExp;
         * function isRegExp(re) {
         *     return isObject(re) && objectToString(re) === '[object RegExp]';
         * }
         * function isObject(arg) {
         *     return typeof arg === 'object' && arg !== null;
         * }
         * function objectToString(o) {
         *     return Object.prototype.toString.call(o);
         * }
         */
        isRegExp(arg: any): boolean;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * exports.isObject = isObject;
         * function isObject(arg) {
         *     return typeof arg === 'object' && arg !== null;
         * }
         */
        isObject(arg: any): boolean;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * exports.isDate = isDate;
         * function isDate(d) {
         *     return isObject(d) && objectToString(d) === '[object Date]';
         * }
         * function isObject(arg) {
         *     return typeof arg === 'object' && arg !== null;
         * }
         * function objectToString(o) {
         *     return Object.prototype.toString.call(o);
         * }
         */
        isDate(arg: any): boolean;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * exports.isError = isError;
         * function isError(e) {
         *     return isObject(e) &&
         *         (objectToString(e) === '[object Error]' || e instanceof Error);
         * }
         * function isObject(arg) {
         *     return typeof arg === 'object' && arg !== null;
         * }
         * function objectToString(o) {
         *     return Object.prototype.toString.call(o);
         * }
         */
        isError(arg: any): boolean;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * exports.isFunction = isFunction;
         * function isFunction(arg) {
         *     return typeof arg === 'function';
         * }
         */
        isFunction(arg: any): boolean;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * exports.isPrimitive = isPrimitive;
         * function isPrimitive(arg) {
         *     return arg === null ||
         *         typeof arg === 'boolean' ||
         *         typeof arg === 'number' ||
         *         typeof arg === 'string' ||
         *         typeof arg === 'symbol' ||  // ES6 symbol
         *         typeof arg === 'undefined';
         * }
         */
        isPrimitive(arg: any): boolean;

        isReference(arg: any): boolean;

        isEmptyObject(arg: any): boolean;

        isInteger(arg: any): boolean;

        isJavaArray(o: any): boolean;

        isJavaObject(o: any): boolean;

        unwrapJavaObject(o: { getClass(): java.lang.Class<any> }): any;

        /**
         * @example
         * function Animal(name) {
         *     this.name = name;
         *     this.sayName = () => toastLog(this.name);
         * }
         * let a = new Animal("Milo");
         * a.sayName();
         * function Dog() {}
         * util.extend(Dog, Animal);
         * let b = new Animal("Oscar");
         * b.sayName();
         * @example Source code summary (zh-CN: 源代码摘要)
         * exports.extend = (function () {
         *     let extendStatics = Object.setPrototypeOf
         *         || {__proto__: []} instanceof Array && function (d, b) {d.__proto__ = b}
         *         || function (d, b) {for (let p in b) if (b.hasOwnProperty(p)) d[p] = b[p]};
         *     return function (d, b) {
         *         extendStatics(d, b);
         *         function __() {this.constructor = d}
         *         d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
         *     };
         * })();
         */
        extend(subClass: Function | object, parentClass: Function | object | null): void;

        /**
         * @example
         * log(util.format("%s%s%d", "0x10", ": ", "0x10")); // "0x10: 16"
         * log(util.format("10%% is 0.1")); // "10% is 0.1"
         * log(util.format([1], 2, "3", {num: 4, age: 5})); // "[ 1 ] 2 '3' { num: 4, age: 5 }"
         * log(util.format({a: {b: {c: "d"}}})); // "{ a: { b: { c: 'd' } } }"
         * log(util.format({a: {b: {c: {d: {e: "f"}}}}})); // "{ a: { b: { c: [Object] } } }"
         * log(util.format('%s:%s', '22', '59', 'Go to bed')); // "22:59 Go to bed"
         * @example
         * let indices = [1, 2, 5];
         * let items = ["A", "B", "E"];
         * log(util.format("选项索引: %j, 选项内容 %j", indices, items));
         * @example
         * let colors = {
         *     'bold': [1, 22], 'italic': [3, 23], 'underline': [4, 24],
         *     'inverse': [7, 27], 'white': [37, 39], 'grey': [90, 39],
         *     'black': [30, 39], 'blue': [34, 39], 'cyan': [36, 39],
         *     'green': [32, 39], 'magenta': [35, 39], 'red': [31, 39], 'yellow': [33, 39],
         * };
         * let styles = {
         *     'special': 'cyan', 'number': 'yellow', 'boolean': 'yellow',
         *     'undefined': 'grey', 'null': 'bold', 'string': 'green',
         *     'date': 'magenta', 'regexp': 'red',
         * };
         * let stylizeWithColor = (str, styleType) => {
         *     let style = styles[styleType];
         *     return style ? '\u001b[' + colors[style][0] + 'm' + str + '\u001b[' + colors[style][1] + 'm' : str;
         * };
         * log(util.format({inspect: (recurseTimes, ctx) => (ctx.stylize = stylizeWithColor, stylizeWithColor)}));
         * log(util.format({inspect: (recurseTimes, ctx) => (ctx.stylize = stylizeWithColor, 1)}));
         * log(util.format({inspect: (recurseTimes, ctx) => (ctx.stylize = stylizeWithColor, /regex/)}));
         * log(util.format({inspect: (recurseTimes, ctx) => (ctx.stylize = stylizeWithColor, true)}));
         * log(util.format({inspect: (recurseTimes, ctx) => (ctx.stylize = stylizeWithColor, null)}));
         * log(util.format({inspect: (recurseTimes, ctx) => (ctx.stylize = stylizeWithColor, "hello")}));
         * log(util.format({inspect: (recurseTimes, ctx) => (ctx.stylize = stylizeWithColor, new Date())}));
         */
        format(...args: any[]): string;

        /**
         * @example
         * util.log("%d %s %d = %d", 1, "+", 3, 4); // e.g. "7 Aug 11:57:40 - 1 + 3 = 4"
         * @example Source code summary (zh-CN: 源代码摘要)
         * exports.log = function () {
         *     console.log('%s - %s', timestamp(), exports.format.apply(exports, arguments));
         * };
         * let months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
         * function timestamp() {
         *     let d = new Date();
         *     let time = [pad(d.getHours()),
         *         pad(d.getMinutes()),
         *         pad(d.getSeconds())].join(':');
         *     return [d.getDate(), months[d.getMonth()], time].join('\x20');
         * }
         * function pad(n) {
         *     return n < 10 ? '0' + n.toString(10) : n.toString(10);
         * }
         */
        log(...args: any[]): void;

        /**
         * @deprecated Not available for Auto.js 4.1.1 Alpha2 (marked by SuperMonster003 on Aug 7, 2020)
         * @example Source code summary (zh-CN: 源代码摘要)
         * exports.deprecate = function (fn, msg) {
         *     // Allow for deprecating things in the process of starting up.
         *     if (isUndefined(global.process)) {
         *         return function () {
         *             return exports.deprecate(fn, msg).apply(this, arguments);
         *         };
         *     }
         *     if (process.noDeprecation === true) {
         *         return fn;
         *     }
         *     let warned = false;
         *     function deprecated() {
         *         if (!warned) {
         *             if (process.throwDeprecation) {
         *                 throw new Error(msg);
         *             } else if (process.traceDeprecation) {
         *                 console.trace(msg);
         *             } else {
         *                 console.error(msg);
         *             }
         *             warned = true;
         *         }
         *         return fn.apply(this, arguments);
         *     }
         *     return deprecated;
         * };
         */
        deprecate(fn: Func, msg: string): Func;

        /**
         * @deprecated Not available for Auto.js 4.1.1 Alpha2 (marked by SuperMonster003 on Aug 7, 2020)
         * @example Source code summary (zh-CN: 源代码摘要)
         * let debugs = {};
         * let debugEnviron;
         * exports.debuglog = function (set) {
         *     if (isUndefined(debugEnviron))
         *         debugEnviron = process.env.NODE_DEBUG || '';
         *     set = set.toUpperCase();
         *     if (!debugs[set]) {
         *         if (new RegExp('\\b' + set + '\\b', 'i').test(debugEnviron)) {
         *             let pid = process.pid;
         *             debugs[set] = function () {
         *                 let msg = exports.format.apply(exports, arguments);
         *                 console.error('%s %d: %s', set, pid, msg);
         *             };
         *         } else {
         *             debugs[set] = function () {
         *             };
         *         }
         *     }
         *     return debugs[set];
         * };
         */
        debuglog(set): any;

        getClass(o: java.lang.Class<any> | object): java.lang.Class<any>;
        class(o: java.lang.Class<any> | object): java.lang.Class<any>;

        getClassName(o: java.lang.Class<any> | object): string;
        className(o: java.lang.Class<any> | object): string;

        /**
         * Check if a given argument matches the pattern.
         * @example
         * // A param needs a string alike 'disable' but not literally.
         * // Which means 'Disable', 'DISABLE', 'diSaBlE', even 'disable ' (a non-break space in the end) are all acceptable.
         * // That way, all samples below should be truthy.
         * checkStringParam('disable', 'disable'); // true
         * checkStringParam('Disable', 'disable'); // true
         * checkStringParam('DISABLE', 'disable'); // true
         * checkStringParam('diSaBlE', 'disable'); // true
         * checkStringParam('disable ', 'disable'); // true
         *
         * @example
         * function test(state) {
         *     // string pattern type
         *     if (util.checkStringParam(state, 'enable')) {
         *         enableSomething();
         *     }
         *     // RegExp pattern type
         *     if (util.checkStringParam(state, /disabled?/)) {
         *         disableSomething();
         *     }
         * }
         */
        checkStringParam(src: string | { toString(): string } | any, pattern: string | RegExp): boolean;

        /**
         * @example
         * util.assureStringStartsWith('123', '$'); // '$123'
         * util.assureStringStartsWith('$hello', '$'); // '$hello'
         */
        assureStringStartsWith(s: string, start: string): string;

        /**
         * @example
         * util.assureStringEndsWith('123', '$'); // '123$'
         * util.assureStringEndsWith('hello$', '$'); // 'hello$'
         */
        assureStringEndsWith(s: string, end: string): string;

        /**
         * @example
         * util.assureStringSurroundsWith('123', '+'); // '+123+'
         * util.assureStringSurroundsWith('hello=', '='); // '=hello='
         * util.assureStringSurroundsWith('test', '^', '$'); // '^test$'
         */
        assureStringSurroundsWith(s: string, start: string, end?: string): string;

        /**
         * @throws TypeError
         */
        ensureType(o: any, type: 'number' | 'boolean' | 'string' | 'undefined' | 'symbol' | 'bigint' | 'object' | 'function'): void;

        /**
         * @throws TypeError
         */
        ensureStringType(...o: any[]): void;

        /**
         * @throws TypeError
         */
        ensureNumberType(...o: any[]): void;

        /**
         * @throws TypeError
         */
        ensureUndefinedType(...o: any[]): void;

        /**
         * @throws TypeError
         */
        ensureBooleanType(...o: any[]): void;

        /**
         * @throws TypeError
         */
        ensureSymbolType(...o: any[]): void;

        /**
         * @throws TypeError
         */
        ensureBigintType(...o: any[]): void;

        /**
         * @throws TypeError
         */
        ensureObjectType(...o: any[]): void;

        /**
         * @throws TypeError
         */
        ensureFunctionType(...o: any[]): void;

        /**
         * @throws TypeError
         */
        ensureNonNullObjectType(...o: any[]): void;

        /**
         * @throws TypeError
         */
        ensureArrayType(...o: any[]): void;

        /**
         * @deprecated
         *
         * Converts a function into regular form
         * @example
         * let sum = (a, b, c) => a + b + c;
         * console.log(util.toRegular(sum)(1, 2, 5)); // 8
         * @example
         * let filter = () => true;
         * // Error: Cannot convert org.mozilla.javascript.ArrowFunction@xxx to...
         * console.log(textMatches(/.+/).filter(filter).exists());
         * // OK and returns boolean
         * console.log(textMatches(/.+/).filter(util.toRegular(filter)).exists());
         */
        toRegular<T extends Function>(f: T): T;

        /**
         * @deprecated
         *
         * @example
         * let sum = (a, b, c) => a + b + c;
         * console.log(util.toRegularAndCall(sum, 1, 2, 5)); // 8
         */
        toRegularAndCall<T>(f: (...args) => T, ...o: any[]): T;

        /**
         * @deprecated
         *
         * @example
         * let sum = (a, b, c) => a + b + c;
         * console.log(util.toRegularAndApply(sum, [1, 2, 5])); // 8
         */
        toRegularAndApply<T>(f: (...args) => T, args: any[]): T;

    }

}

declare namespace Util {

    interface Inspect {

        /**
         * @param [value]
         * @param [showHidden=false] - whether to show the names of not enumerable properties or methods
         * @param [depth=2] - recurse times
         * @param [colors=false] - stylize with color or not
         * @example
         * function Animal (name) {
         *     this.name = name;
         * }
         * let dog = new Animal("Oscar");
         * dog.age = 9;
         * Object.defineProperties(dog, {
         *     sex: {value: "Male"},
         *     hobby: {value: "Accompany", enumerable: true},
         *     isYoung: {get() {return () => this.age < 10}},
         * });
         *
         * // log(util.inspect(dog, false, 0, true));
         * log(util.inspect(dog, {
         *     showHidden: false,
         *     depth: 0,
         *     colors: true,
         * }));
         *
         * // log(util.inspect(dog, true, 0, true));
         * log(util.inspect(dog, {
         *     showHidden: true,
         *     depth: 0,
         *     colors: true,
         * }));
         * @example Source code summary (zh-CN: 源代码摘要)
         * exports.inspect = inspect;
         * // http://en.wikipedia.org/wiki/ANSI_escape_code#graphics
         * inspect.colors = {
         *     'bold': [1, 22],
         *     'italic': [3, 23],
         *     'underline': [4, 24],
         *     'inverse': [7, 27],
         *     'white': [37, 39],
         *     'grey': [90, 39],
         *     'black': [30, 39],
         *     'blue': [34, 39],
         *     'cyan': [36, 39],
         *     'green': [32, 39],
         *     'magenta': [35, 39],
         *     'red': [31, 39],
         *     'yellow': [33, 39]
         * };
         * // Don't use 'blue' not visible on cmd.exe
         * inspect.styles = {
         *     'special': 'cyan',
         *     'number': 'yellow',
         *     'boolean': 'yellow',
         *     'undefined': 'grey',
         *     'null': 'bold',
         *     'string': 'green',
         *     'date': 'magenta',
         *     // "name": intentionally not styling
         *     'regexp': 'red'
         * };
         */
        (value?: any, showHidden?: boolean, depth?: number, colors?: boolean): string;

        /**
         * Avoid using 'blue' on cmd.exe
         * @see http://en.wikipedia.org/wiki/ANSI_escape_code#graphics
         */
        colors: {
            bold: [1, 22];
            italic: [3, 23];
            underline: [4, 24];
            inverse: [7, 27];
            white: [37, 39];
            grey: [90, 39];
            black: [30, 39];
            blue: [34, 39];
            cyan: [36, 39];
            green: [32, 39];
            magenta: [35, 39];
            red: [31, 39];
            yellow: [33, 39];
        };

        styles: {
            special: 'cyan';
            number: 'yellow';
            boolean: 'yellow';
            undefined: 'grey';
            null: 'bold';
            string: 'green';
            date: 'magenta';
            regexp: 'red';
        };

    }

    interface Java {

        /**
         * @example
         * log(util.java.instanceOf(device, "java.lang.Object")); // true
         * log(util.java.instanceOf({getClass: () => files.getClass()}, "java.lang.Object")); // true
         * log(util.java.instanceOf(java.lang.Boolean.TRUE, "java.lang.Boolean")); // true
         * @example Source code summary (zh-CN: 源代码摘要)
         * let J = {};
         * J.instanceOf = function (obj, clazz) {
         *     return java.lang.Class.forName(clazz).isAssignableFrom(obj.getClass());
         * };
         */
        instanceOf(obj: java.lang.Class<any>, clazz: java.lang.Class<any>): boolean;

        /**
         * @example
         * images.readPixels = function (path) {
         *     let img = images.read(path);
         *     let bitmap = img.getBitmap();
         *     let w = bitmap.getWidth();
         *     let h = bitmap.getHeight();
         *     let pixels = util.java.array("int", w * h);
         *     bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
         *     img.recycle();
         *     return {data: pixels, width: w, height: h};
         * };
         * @example Source code summary (zh-CN: 源代码摘要)
         * let J = {};
         * J.array = array;
         * function array(type) {
         *     let clazz = typeToClass(type);
         *     let args = arguments;
         *     args[0] = clazz;
         *     return java.lang.reflect.Array.newInstance.apply(null, args);
         * }
         * function typeToClass(type) {
         *     if (typeof type !== 'string') {
         *         return type;
         *     }
         *     if (type === 'string') {
         *         return java.lang.String;
         *     }
         *     let types = {
         *         "int": "Integer",
         *         "long": "Long",
         *         "double": "Double",
         *         "char": "Character",
         *         "byte": "Byte",
         *         "float": "Float"
         *     };
         *     return types[type] ? Packages["java.lang." + types[type]].TYPE : Packages[type];
         * }
         */
        array(type: Java.LangAbbr | java.lang.Class<any> | any, length: number): Array<java.lang.Integer | java.lang.Long | java.lang.Double | java.lang.Character | java.lang.Byte | java.lang.Float | java.lang.String | any>;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * let J = {};
         * J.array = array;
         * function array(type) {
         *     let clazz = typeToClass(type);
         *     let args = arguments;
         *     args[0] = clazz;
         *     return java.lang.reflect.Array.newInstance.apply(null, args);
         * }
         * function typeToClass(type) {
         *     if (typeof type !== 'string') {
         *         return type;
         *     }
         *     if (type === 'string') {
         *         return java.lang.String;
         *     }
         *     let types = {
         *         "int": "Integer",
         *         "long": "Long",
         *         "double": "Double",
         *         "char": "Character",
         *         "byte": "Byte",
         *         "float": "Float"
         *     };
         *     return types[type] ? Packages["java.lang." + types[type]].TYPE : Packages[type];
         * }
         */
        array(type: Java.LangAbbr | java.lang.Class<any> | any, ...dimensions: number[]): Array<java.lang.Integer | java.lang.Long | java.lang.Double | java.lang.Character | java.lang.Byte | java.lang.Float | java.lang.String | any>;

        /**
         * @example
         * log(util.java.toJsArray(runtime.accessibilityBridge.windowRoots(), false).map((root) => {
         *     return org.autojs.autojs.core.automator.UiObject.Companion.createRoot(root);
         * }));
         * @example Source code summary (zh-CN: 源代码摘要)
         * let J = {};
         * J.toJsArray = function (list, nullListToEmptyArray) {
         *     if (list === null || list === undefined) {
         *         return nullListToEmptyArray ? [] : null;
         *     }
         *     let arr = Array(list.size());
         *     for (let i = 0; i < list.size(); i++) {
         *         arr[i] = list.get(i);
         *     }
         *     return arr;
         * };
         */
        toJsArray(list?: java.util.List<any>, nullListToEmptyArray?: boolean): any[];

        /**
         * @example
         * log(util.java.mapToObject(util.java.objectToMap({a: "A", b: "B"})));
         * @example Source code summary (zh-CN: 源代码摘要)
         * let J = {};
         * J.objectToMap = function (obj) {
         *     if (obj === null || obj === undefined) {
         *         return null;
         *     }
         *     let map = new java.util.HashMap();
         *     for (let key in obj) {
         *         if (obj.hasOwnProperty(key)) {
         *             map.put(key, obj[key]);
         *         }
         *     }
         *     return map;
         * };
         */
        objectToMap(obj?: object): java.util.HashMap<any, any> | null;

        /**
         * @example
         * log(util.java.mapToObject(util.java.objectToMap({a: "A", b: "B"})));
         * @example Source code summary (zh-CN: 源代码摘要)
         * let J = {};
         * J.mapToObject = function (map) {
         *     if (map === null || map === undefined) {
         *         return null;
         *     }
         *     let iter = map.entrySet().iterator();
         *     let obj = {};
         *     while (iter.hasNext()) {
         *         let entry = iter.next();
         *         obj[entry.key] = entry.value;
         *     }
         *     return obj;
         * };
         */
        mapToObject(map?: java.util.HashMap<any, any>): object | null;

    }

    namespace Java {

        type LangAbbr = 'int' | 'long' | 'double' | 'char' | 'byte' | 'float' | 'string';

    }

    interface MorseCode {

        (text: string, timeUnit?: number): {
            pattern: number[];
            code: string;
            getPattern(): number[];
            getCode(): string;
            vibrate(delay?: number): void;
            toString(): string;
        };

        getPattern(text: string): number[];

        getCode(text: string): string;

        vibrate(text: string, delay?: number): void;

    }

    type VersionCodes = {
        [prop in VersionCodes.CodeName]: VersionCodes.Info;
    } & {
        toString(isInDetail?: VersionCodes.IsInDetail): string;
        /**
         * @example
         * util.versionCodes.search('R');
         */
        search(versionCode: VersionCodes.CodeName | string): VersionCodes.SearchResult;
        /**
         * @example
         * util.versionCodes.search('Android Oreo');
         */
        search(releaseName: string): VersionCodes.SearchResult;
        /**
         * @example
         * util.versionCodes.search('New York Cheesecake');
         */
        search(internalCodename: string): VersionCodes.SearchResult;
        /**
         * @example
         * util.versionCodes.search('7.1');
         * util.versionCodes.search(7.1);
         */
        search(platformVersion: string | number): VersionCodes.SearchResult;
        /**
         * @example
         * util.versionCodes.search(23);
         */
        search(apiLevel: number): VersionCodes.SearchResult;
        /**
         * @example
         * util.versionCodes.search(new Date(2011, 5-1, 23));
         */
        search(releaseDate: Date): VersionCodes.SearchResult;
        /**
         * @example
         * util.versionCodes.search(2011);
         * util.versionCodes.search('2011');
         */
        search(releaseYear: number | string): VersionCodes.SearchResult;
        /**
         * @example
         * util.versionCodes.search('May 2011');
         * util.versionCodes.search('May, 2011');
         * util.versionCodes.search('2011/05');
         * util.versionCodes.search('2011/5');
         */
        search(releaseMonth: string): VersionCodes.SearchResult;
        /**
         * @example
         * util.versionCodes.search('May 23 2011');
         * util.versionCodes.search('May 23, 2011');
         * util.versionCodes.search('2011/05/23');
         * util.versionCodes.search('2011/5/23');
         */
        search(releaseDay: string): VersionCodes.SearchResult;
        /**
         * @example
         * util.versionCodes.search(1306080000000);
         * util.versionCodes.search(new Date(2011, 5-1, 23).getTime());
         */
        search(releaseTimestamp: number): VersionCodes.SearchResult;
    };

    namespace VersionCodes {

        type CodeName = 'UPSIDE_DOWN_CAKE' | 'TIRAMISU' | 'S_V2' | 'S' | 'R' | 'Q' | 'P' | 'O_MR1' | 'O' | 'N_MR1' | 'N' | 'M' | 'LOLLIPOP_MR1' | 'LOLLIPOP' | 'KITKAT_WATCH' | 'KITKAT' | 'JELLY_BEAN_MR2' | 'JELLY_BEAN_MR1' | 'JELLY_BEAN' | 'ICE_CREAM_SANDWICH_MR1' | 'ICE_CREAM_SANDWICH' | 'HONEYCOMB_MR2' | 'HONEYCOMB_MR1' | 'HONEYCOMB' | 'GINGERBREAD_MR1' | 'GINGERBREAD' | 'FROYO' | 'ECLAIR_MR1' | 'ECLAIR_0_1' | 'ECLAIR' | 'DONUT' | 'CUPCAKE' | 'BASE_1_1' | 'BASE';

        type Info = {
            versionCode: CodeName;
            releaseName: string;
            internalCodename: string;
            platformVersion: string;
            apiLevel: number;
            releaseDate: string;
            releaseTimestamp: number;
        };

        type IsInDetail = boolean | 'in_detail' | 'detail';

        type SearchResult = Info | Info[] | null;

    }

    interface Version {

        sdkInt: number;

        //// -=-= PENDING =-=- ////

    }

}