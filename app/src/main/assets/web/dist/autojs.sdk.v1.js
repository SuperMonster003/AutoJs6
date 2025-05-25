'use strict';

( /* @IIFE */ () => {

    // @Reference to .../asset/web/dist/autojs.sdk.v1.js from Auto.js Pro 9.3.11 on May 24, 2025.

    const EVENT_RESPONSE = '$autojs:internal:response';
    const EVENT_REQUEST = '$autojs:internal:request';

    // noinspection JSValidateTypes
    /** @type {import('events')} */
    let nodejsEvents = events;

    let nextId = 1;

    let helper = {
        isPromise: o => o && typeof o.then === 'function',
        unwrapJson: o => o ? JSON.parse(o) : undefined,
        wrapJson: o => o === undefined ? o : JSON.stringify(o),
    };

    Object.setPrototypeOf($autojs, new nodejsEvents.EventEmitter());

    /** @type {Internal.AutoJsBridge} */
    let AutoJsBridge = {
        requestHandlers: {},
        onEventInternal(event, args) {
            this.emit(event, ...helper.unwrapJson(args));
        },
        send(event, ...args) {
            this.sendEventInternal(event, helper.wrapJson(args));
        },
        invoke(channel, ...args) {
            let id = nextId++;
            return new Promise((resolve, reject) => {
                this.once(EVENT_RESPONSE + ':' + id, (result) => {
                    if (result.error) {
                        reject(new Error('Error occurred while handling invoke: channel = ' + channel + ', error = ' + result.error));
                    } else {
                        resolve(result.result);
                    }
                });
                this.send(EVENT_REQUEST, {
                    id, channel, args,
                });
            });
        },
        /** @this {Internal.AutoJsBridge} */
        handle(channel, handler) {
            this.requestHandlers[channel || ''] = handler;
            return this;
        },
        removeHandler(channel) {
            delete this.requestHandlers[channel];
        },
        handleRequest(request) {
            let handler = this.requestHandlers[request.channel] || this.requestHandlers[''];
            if (!handler) {
                return;
            }
            let event = {
                channel: request.channel,
                arguments: request.args,
            };
            let result;
            try {
                result = handler(event, ...event.arguments);
            } catch (e) {
                this.sendResponse(request, undefined, e);
                return;
            }
            if (helper.isPromise(request)) {
                result.then((r) => {
                    this.sendResponse(request, r);
                }).catch((err) => {
                    this.sendResponse(request, undefined, err);
                });
            } else {
                this.sendResponse(request, result);
            }
        },
        sendResponse(request, result, error) {
            this.send(EVENT_RESPONSE + ':' + request.id, { result, error });
        },
    };

    $autojs = Object.assign($autojs, AutoJsBridge);
    $autojs.on(EVENT_REQUEST, AutoJsBridge.handleRequest.bind($autojs));

})();
