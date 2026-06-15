// Type definitions for AutoJs6 internal module console
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 4.3.5
//
// Last modified: Oct 21, 2021

/// <reference path="./index.d.ts" />
/// <reference lib="es2022" />

/**
 * @Source %AutoJs6Assets%/modules/__console__.js
 */

declare namespace Internal {

    interface Continuation {

        /**
         * @param [scope=global]
         * @example Source code summary (zh-CN: 源代码摘要)
         * // To use continuation, make sure that codes are running
         * // in a project with {"useFeatures": ['continuation']} in project.json file
         * // zh-CN: 携程功能需在项目中 (而非单个JS文件中) 运行
         * // 并需要在 project.json 文件中增加 {"useFeatures": ['continuation']} 配置参数
         * continuation.create = function (scope) {
         *     scope = scope || global;
         *     const cont = Object.create(runtime.createContinuation(scope));
         *     cont.await = function () {
         *         let result = cont.suspend();
         *         if (result.error != null) {
         *             throw result.error;
         *         }
         *         return result.result;
         *     };
         *     cont.resumeError = function (error) {
         *         if (error === null || error === undefined) {
         *             throw TypeError('error is null or undefined');
         *         }
         *         cont.resumeWith(Continuation.Result.Companion.failure(error));
         *     };
         *     cont.resume = function (result) {
         *         cont.resumeWith(Continuation.Result.Companion.success(result));
         *     };
         *     return cont;
         * };
         */
        create(scope?: org.mozilla.javascript.Scriptable): Continuation.Creator;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * continuation.await = function (any) {
         *     if (Object.getPrototypeOf(any).constructor === Promise) {
         *         return awaitPromise(global, any);
         *     }
         *     throw new TypeError('cannot await ' + any);
         * };
         * function awaitPromise(scope, promise) {
         *     const cont = continuation.create(scope);
         *     promise.then(result => {
         *         cont.resume(result);
         *     }).catch(error => {
         *         cont.resumeError(error);
         *     });
         *     return cont.await();
         * }
         */
        await(promise: Promise<unknown> | any): any;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * continuation.delay = function (millis) {
         *     const cont = continuation.create();
         *     setTimeout(() => {
         *         cont.resume();
         *     }, millis);
         *     cont.await();
         * };
         */
        delay(millis: number): void;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * continuation.__defineGetter__('enabled', function () {
         *     return engines.myEngine().hasFeature('continuation');
         * });
         */
        get enabled(): boolean;

    }

    namespace Continuation {

        class Creator {

            /**
             * @example Source code summary (zh-CN: 源代码摘要)
             * const cont = Object.create(runtime.createContinuation(scope));
             * cont.await = function () {
             *     let result = cont.suspend();
             *
             *     if (result.error != null) {
             *         throw result.error;
             *     }
             *     return result.result;
             * };
             * @see continuation.create
             */
            await(): any;

            /**
             * @example Source code summary (zh-CN: 源代码摘要)
             * const cont = Object.create(runtime.createContinuation(scope));
             * cont.resumeError = function (error) {
             *     if (error === null || error === undefined) {
             *         throw TypeError('error is null or undefined');
             *     }
             *     cont.resumeWith(Continuation.Result.Companion.failure(error));
             * };
             */
            resumeError(error?: any): void;

            /**
             * @example Source code summary (zh-CN: 源代码摘要)
             * const cont = Object.create(runtime.createContinuation(scope));
             * cont.resume = function (result) {
             *     cont.resumeWith(Continuation.Result.Companion.success(result));
             * };
             */
            resume(result?: any): void;

        }

        interface PromiseExtension extends PromiseConstructor {

            /**
             * @example Source code summary (zh-CN: 源代码摘要)
             * Promise.prototype.await = function () {
             *     return continuation.await(this);
             * };
             */
            await(): any;

        }

    }

}