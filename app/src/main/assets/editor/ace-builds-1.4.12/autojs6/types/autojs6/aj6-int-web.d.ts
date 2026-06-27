// Type definitions for AutoJs6 internal module web
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 5.7.3
//
// Last modified: Jun 14, 2026

/// <reference path="./index.d.ts" />

/**
 * @Source /src/main/java/org/autojs/autojs/runtime/api/augment/web/Web.kt
 * @Source /src/main/java/org/autojs/autojs/runtime/api/augment/web/WebSocket.kt
 * @Source /src/main/java/org/autojs/autojs/runtime/api/augment/web/WebSocketFields.kt
 */

declare namespace Internal {

    interface Web {

        newInjectableWebView(context?: android.content.Context | string, url?: string): org.autojs.autojs.core.web.InjectableWebView;

        newInjectableWebClient(): org.autojs.autojs.core.web.InjectableWebClient;

        newWebSocket(url: string): org.autojs.autojs.core.web.WebSocket;

    }

}

/**
 * @example Source code summary (zh-CN: 源代码摘要)
 * scope.newInjectableWebClient = function () {
 *     return new org.autojs.autojs.core.web.InjectableWebClient(org.mozilla.javascript.Context.getCurrentContext(), scope);
 * };
 */
declare function newInjectableWebClient(): org.autojs.autojs.core.web.InjectableWebClient;

/**
 * @example Source code summary (zh-CN: 源代码摘要)
 * scope.newInjectableWebView = function () {
 *     return new org.autojs.autojs.core.web.InjectableWebView(scope.activity, org.mozilla.javascript.Context.getCurrentContext(), scope);
 * };
 */
declare function newInjectableWebView(context?: android.content.Context | string, url?: string): org.autojs.autojs.core.web.InjectableWebView;

declare function newWebSocket(url: string): org.autojs.autojs.core.web.WebSocket;

declare class WebSocket extends org.autojs.autojs.core.web.WebSocket {

    constructor(client: MutableOkHttp, url: string);
    constructor(url: string);

    /**
     * Constant: 1000
     *
     * Successful operation / regular socket shutdown.
     * @const
     */
    static CODE_CLOSE_NORMAL: number

    /**
     * Constant: 1001
     *
     * Client is leaving (browser tab closing).
     * @const
     */
    static CODE_CLOSE_GOING_AWAY: number

    /**
     * Constant: 1002
     *
     * Endpoint received a malformed frame.
     * @const
     */
    static CODE_CLOSE_PROTOCOL_ERROR: number

    /**
     * Constant: 1003
     *
     * Endpoint received an unsupported frame (e.g. binary-only endpoint received text frame).
     * @const
     */
    static CODE_CLOSE_UNSUPPORTED: number

    /**
     * Constant: 1005
     *
     * Expected close status, received none.
     * @const
     */
    static CODE_CLOSED_NO_STATUS: number

    /**
     * Constant: 1006
     *
     * No close code frame has been receieved.
     * @const
     */
    static CODE_CLOSE_ABNORMAL: number

    /**
     * Constant: 1007
     *
     * Endpoint received inconsistent message (e.g. malformed UTF-8).
     * @const
     */
    static CODE_UNSUPPORTED_PAYLOAD: number

    /**
     * Constant: 1008
     *
     * Generic code used for situations other than 1003 and 1009.
     * @const
     */
    static CODE_POLICY_VIOLATION: number

    /**
     * Constant: 1009
     *
     * Endpoint won't process large frame.
     * @const
     */
    static CODE_CLOSE_TOO_LARGE: number

    /**
     * Constant: 1010
     *
     * Client wanted an extension which server did not negotiate.
     * @const
     */
    static CODE_MANDATORY_EXTENSION: number

    /**
     * Constant: 1011
     *
     * Internal server error while operating.
     * @const
     */
    static CODE_SERVER_ERROR: number

    /**
     * Constant: 1012
     *
     * Server/service is restarting.
     * @const
     */
    static CODE_SERVICE_RESTART: number

    /**
     * Constant: 1013
     *
     * Temporary server condition forced blocking client's request.
     * @const
     */
    static CODE_TRY_AGAIN_LATER: number

    /**
     * Constant: 1014
     *
     * Server acting as gateway received an invalid response.
     * @const
     */
    static CODE_BAD_GATEWAY: number

    /**
     * Constant: 1015
     *
     * Transport Layer Security handshake failure.
     * @const
     */
    static CODE_TLS_HANDSHAKE_FAIL: number

    static EVENT_CLOSED: string

    static EVENT_CLOSING: string

    static EVENT_FAILURE: string

    static EVENT_TEXT: string

    static EVENT_MESSAGE: string

    static EVENT_BYTES: string

    static EVENT_OPEN: string

    static EVENT_MAX_REBUILDS: string

}
