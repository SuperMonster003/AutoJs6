( /* @ModuleIIFE */ () => {

    // @Reference to module __ui__.js from Auto.js Pro 9.3.11 on May 24, 2025.

    let JsBridge = (function () {
        let ResultAdapter = require('result-adapter');

        let EVENT_REQUEST = '$autojs:internal:request';
        let EVENT_RESPONSE = '$autojs:internal:response';

        function JavaScriptBridgeImpl(webview) {
            let self = this;
            events.__asEmitter__(self);
            self.nextId = 1;
            self.requestHandlers = new Map();
            self.webview = webview;
            webview.setJavascriptEventCallback({
                onWebJavaScriptEvent(event, args) {
                    let obj = unwrapJson(args) || [];
                    self.emit.apply(self, [ event, { name: event } ].concat(obj));
                },
            });
            self.on(EVENT_REQUEST, function (e, request) {
                let handler = self.requestHandlers.get(request.channel) ?? self.requestHandlers.get('');
                if (!handler) {
                    self.sendResponseError(request, new Error('no handler for action: ' + request.channel));
                    return;
                }
                let event = {
                    channel: request.channel,
                    arguments: request.args,
                };
                let result;
                try {
                    result = handler.apply(void 0, [ event ].concat(event.arguments));
                } catch (e) {
                    self.sendResponseError(request, e);
                    return;
                }
                if (isPromise(result)) {
                    result.then(function (r) {
                        self.sendResponse(request, r);
                    }).catch(function (err) {
                        self.sendResponseError(request, err);
                    });
                } else {
                    self.sendResponse(request, result);
                }
            });
            return self;
        }

        JavaScriptBridgeImpl.prototype.sendResponse = function (request, result, error) {
            this.send(EVENT_RESPONSE + ':' + request.id, {
                result: result,
                error: error,
            });
        };
        JavaScriptBridgeImpl.prototype.sendResponseError = function (request, error) {
            this.sendResponse(request, undefined, error.toString());
        };
        JavaScriptBridgeImpl.prototype.invoke = function (channel) {
            let self = this;
            let args = [];
            for (let _i = 1; _i < arguments.length; _i++) {
                args[_i - 1] = arguments[_i];
            }
            let id = this.nextId++;
            return new Promise(function (resolve, reject) {
                self.once(EVENT_RESPONSE + ':' + id, function (event, result) {
                    if (result.error) {
                        reject(new Error('Error occurred while handling invoke: channel = ' + channel + ', error = ' + result.error));
                    } else {
                        resolve(result.result);
                    }
                });
                self.send(EVENT_REQUEST, {
                    id: id,
                    channel: channel,
                    args: args,
                });
            });
        };
        JavaScriptBridgeImpl.prototype.send = function (event) {
            let args = [];
            for (let i = 1; i < arguments.length; i++) {
                args[i - 1] = arguments[i];
            }
            this.webview.sendEventToWebJavaScript(event, wrapJson(args));
        };
        JavaScriptBridgeImpl.prototype.handle = function (channel, handler) {
            this.requestHandlers.set(channel !== null && channel !== void 0 ? channel : '', handler);
            return this;
        };
        JavaScriptBridgeImpl.prototype.eval = function (code) {
            let self = this;
            return new Promise(function (resolve, reject) {
                ResultAdapter.promise(self.webview.__eval(code))
                    .then(result => resolve(JSON.parse(String(result))))
                    .catch(err => reject(err));
            });
        };

        function unwrapJson(maybeJson) {
            if (!maybeJson) {
                return undefined;
            }
            return JSON.parse(maybeJson);
        }

        function wrapJson(obj) {
            if (typeof obj === 'undefined') {
                return undefined;
            }
            return JSON.stringify(obj);
        }

        function isPromise(obj) {
            return !!obj && (typeof obj === 'object' || typeof obj === 'function') && typeof obj.then === 'function';
        }

        return JavaScriptBridgeImpl;
    })();

    /**
     * @param {org.autojs.autojs.core.ui.widget.JsWebView} webview
     */
    function initWebView(webview) {
        webview.jsBridge = new JsBridge(webview);
        let emitter = events.emitter();
        webview.events = emitter;
        webview.setSyncWebViewEventCallback({
            onSyncWebViewEvent(event) {
                dispatchJavaEvent(event, emitter);
            },
        });
        webview.setSyncEventEnabled('', true);

        function dispatchJavaEvent(event, emitter) {
            let eventName = event.getName();
            let args = Array.from(event.getArguments());
            let _returnValue;
            let returnValueSet = false;
            let e = {
                name: eventName,
                arguments: args,
                consumed: false,
            };
            Object.defineProperty(e, 'returnValue', {
                get: function () {
                    return _returnValue;
                },
                set: function (value) {
                    _returnValue = value;
                    returnValueSet = true;
                },
            });
            emitter.emit.apply(emitter, [ eventName, e ].concat(args));
        }
    }

    module.exports = initWebView;
})();