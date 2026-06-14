// Type definitions for AutoJs6 internal module jvm-npm
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 4.3.5
//
// Last modified: Oct 21, 2021

/// <reference path="../index.d.ts" />

/**
 * @Source %AutoJs6Assets%/modules/jvm-npm.js
 */

declare namespace Internal {

    interface Require {

        (id: Require.ID, parent?: { id?: string }): Require.Result;

        debug: boolean;
        cache: { [prop: string]: object | any };
        extensions: {};
        root: string;
        NODE_PATH: any;

        // noinspection HttpUrlsUsage
        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * Require.resolve = function (id, parent) {
         *     var roots = findRoots(parent);
         *     for (var i = 0; i < roots.length; ++i) {
         *         var root = roots[i];
         *         var result = resolveCoreModule(id, root) ||
         *             resolveAsFile(id, root, '.js') ||
         *             resolveAsFile(id, root, '.json') ||
         *             resolveAsDirectory(id, root) ||
         *             resolveAsNodeModule(id, root);
         *         if (result) {
         *             return result;
         *         }
         *     }
         *     return false;
         * };
         */
        resolve(id: Require.ID, parent?: { id?: string }): { path: string, core: boolean } | string | false;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * Require.paths = function () {
         *     var r = [];
         *     r.push(java.lang.System.getProperty('user.home') + '/.node_modules');
         *     r.push(java.lang.System.getProperty('user.home') + '/.node_libraries');
         *     if (Require.NODE_PATH) {
         *         r = r.concat(parsePaths(Require.NODE_PATH));
         *     } else {
         *         var NODE_PATH = java.lang.System.getenv().NODE_PATH;
         *         if (NODE_PATH) {
         *             r = r.concat(parsePaths(NODE_PATH));
         *         }
         *     }
         *     // r.push( $PREFIX + "/node/library" )
         *     return r;
         * };
         */
        paths(): string[];

    }

    namespace Require {

        // noinspection HttpUrlsUsage
        type ID = 'events' | 'http://.*' | 'https://.*' | InternalModule | string;

        type Result = Internal.Events | any;

        type InternalModuleWithDoubleUnderlines =
            'Arrayx' |
            'Mathx' |
            'Numberx' |
            'RootAutomator' |
            'app' |
            'autojs' |
            'automator' |
            'base64' |
            'bridges' |
            'colors' |
            'console' |
            'continuation' |
            'crypto' |
            'device' |
            'dialogs' |
            'engines' |
            'events' |
            'files' |
            'floaty' |
            'globals' |
            'http' |
            'i18n' |
            'images' |
            'media' |
            'notice' |
            'plugins' |
            'recorder' |
            's13n' |
            'selector' |
            'sensors' |
            'shell' |
            'storages' |
            'tasks' |
            'threads' |
            'timers' |
            'toast' |
            'ui' |
            'util' |
            'web';

        type InternalModuleWithoutDoubleUnderlines =
            'array-observe.min' |
            'banana-i18n' |
            'cheerio' |
            'jvm-npm' |
            'lodash' |
            'ocr-mlkit' |
            'ocr-paddle' |
            'object-observe-lite.min' |
            'paddle-ocr' |
            'polyfill' |
            'promise' |
            'redirect' |
            'result-adapter';

        type InternalModule =
            `__${InternalModuleWithDoubleUnderlines}__${'.js' | ''}` |
            `${InternalModuleWithoutDoubleUnderlines}${'.js' | ''}`;

        class Module {

            constructor(id?: Require.ID, parent?, core?: boolean);

            public _exports: any;
            public id: Require.ID;
            public core: boolean;
            public parent: { children?: any[] };
            public children: [];
            public filename: Require.ID;
            public loaded: boolean;

            public static require(id: Require.ID, parent?: { id?: string }): Require.Result;

            /**
             * @example Source code summary (zh-CN: 源代码摘要)
             * Module._load = function _load(file, parent, core, main) {
             *     return NativeRequire.require(file);
             * };
             */
            public static _load(file: string): any;

            /**
             * @example Source code summary (zh-CN: 源代码摘要)
             * Module.runMain = function runMain(main) {
             *     let file = Require.resolve(main);
             *     Module._load(file, undefined, false, true);
             * };
             */
            public static runMain(main): any;

            /**
             * @example Source code summary (zh-CN: 源代码摘要)
             * Object.defineProperty(this, 'exports', {
             *     get: function () {
             *         return this._exports;
             *     }.bind(this),
             *     set: function (val) {
             *         Require.cache[this.filename] = val;
             *         this._exports = val;
             *     }.bind(this)
             * });
             */
            public get exports(): any;

            /**
             * @example Source code summary (zh-CN: 源代码摘要)
             * Object.defineProperty(this, 'exports', {
             *     get: function () {
             *         return this._exports;
             *     }.bind(this),
             *     set: function (val) {
             *         Require.cache[this.filename] = val;
             *         this._exports = val;
             *     }.bind(this)
             * });
             */
            public set exports(val: any);

            /**
             * @example
             * this.require = function (id) {
             *     return Require(id, this);
             * }.bind(this);
             */
            public require(id: Require.ID): Require.Result;

        }

    }

}