// noinspection JSUnusedGlobalSymbols,JSUnusedLocalSymbols,UnnecessaryLocalVariableJS

/* Overwritten protection. */

let { ui } = global;

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Http}
 */
module.exports = function (scriptRuntime, scope) {

    const PFile = org.autojs.autojs.pio.PFile;
    const Request = okhttp3.Request;
    const RequestBody = okhttp3.RequestBody;
    const MultipartBody = okhttp3.MultipartBody;
    const MediaType = okhttp3.MediaType;
    const FormBody = okhttp3.FormBody;
    const Callback = okhttp3.Callback;
    const MimeTypeMap = android.webkit.MimeTypeMap;

    let _ = {
        Http: (/* @IIFE */ () => {
            /**
             * @implements Internal.Http
             */
            const Http = function () {
                /* Empty body. */
            };

            Http.prototype = {
                constructor: Http,
                __okhttp__: new MutableOkHttp(),
                /**
                 * @example
                 * http.client() === http.client(); // true
                 */
                client() {
                    return this.__okhttp__.client();
                },
                /**
                 * @param {string} url
                 * @param {Http.RequestBuilderOptions} [options]
                 * @return {okhttp3.Request}
                 */
                buildRequest(url, options) {
                    let __ = {
                        options: options || {},
                        getUrl(url) {
                            if (typeof url !== 'string') {
                                throw TypeError('Param url must be a string');
                            }
                            // noinspection HttpUrlsUsage
                            return url.match(/^https?:\/\//) ? url : `http://${url}`;
                        },
                        /**
                         * @param {okhttp3.Request.Builder} request
                         */
                        setHeaders(request) {
                            Object.entries(this.options.headers || {}).forEach((entries) => {
                                let [ key, value ] = entries;
                                if (Array.isArray(value)) {
                                    value.forEach(v => request.header(key, v));
                                } else {
                                    request.header(key, value);
                                }
                            });
                        },
                        setMethod(request) {
                            if (this.options.body) {
                                this.ensureMethodInOptions();
                                request.method(this.options.method, this.parseBody());
                            } else if (this.options.files) {
                                this.ensureMethodInOptions();
                                request.method(this.options.method, this.parseMultipart());
                            } else {
                                this.ensureMethodInOptions();
                                request.method(this.options.method, null);
                            }
                        },
                        ensureMethodInOptions() {
                            if (typeof this.options.method !== 'string') {
                                throw Error('Property method is required for header options');
                            }
                        },
                        parseBody() {
                            let body = this.options.body;

                            if (body instanceof RequestBody) {
                                return body;
                            }
                            if (typeof body === 'string') {
                                // noinspection JSDeprecatedSymbols
                                return RequestBody.create(MediaType.parse(this.options.contentType), body);
                            }
                            if (typeof body === 'function') {
                                // noinspection JSValidateTypes
                                return new RequestBody({
                                    contentType() {
                                        return MediaType.parse(this.options.contentType);
                                    },
                                    writeTo: body,
                                });
                            }
                            throw TypeError('Unknown type of body for header options');
                        },
                        parseMultipart() {
                            let files = this.options.files;

                            let builder = new MultipartBody.Builder()
                                .setType(MultipartBody.FORM);

                            Object.entries(files).forEach((entries) => {
                                let [ key, value ] = entries;
                                if (typeof value === 'string') {
                                    builder.addFormDataPart(key, value);
                                    return;
                                }
                                let path, mimeType, fileName;
                                if (typeof value.getPath === 'function') {
                                    path = value.getPath();
                                } else if (value.length === 2) {
                                    [ fileName, path ] = value;
                                } else if (value.length > 2) {
                                    [ fileName, mimeType, path ] = value;
                                }
                                let file = new PFile(path);
                                fileName = fileName || file.getName();
                                mimeType = mimeType || this.parseMimeType(file.getExtension());
                                // noinspection JSDeprecatedSymbols
                                let requestBody = RequestBody.create(MediaType.parse(mimeType), file);
                                builder.addFormDataPart(key, fileName, requestBody);
                            });

                            return builder.build();
                        },
                        parseMimeType(ext) {
                            if (ext.length > 0) {
                                let type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                                if (type) {
                                    return type;
                                }
                            }
                            return 'application/octet-stream';
                        },
                    };

                    let $$ = {
                        /**
                         * @type {okhttp3.Request.Builder}
                         */
                        request: new Request.Builder(),
                        build() {
                            this.setUrl();
                            this.setHeader();
                            this.setMethod();

                            return this.request.build();
                        },
                        setUrl() {
                            this.request.url(__.getUrl(url));
                        },
                        setHeader() {
                            __.setHeaders(this.request);
                        },
                        setMethod() {
                            __.setMethod(this.request);
                        },
                    };

                    return $$.build();
                },
                /**
                 * @param {string} url
                 * @param {Http.RequestBuilderOptions} [options]
                 * @param {(response: Http.WrappedResponse, ex?: java.io.IOException) => void} [callback]
                 * @return {Http.WrappedResponse | void}
                 */
                request(url, options, callback) {
                    let cont = !callback && ui.isUiThread() && continuation.enabled
                        ? continuation.create() : null;

                    let opt = options || {};

                    this.__okhttp__.setTimeout((/* milliseconds = */ (opt.timeout || _.constants.DEFAULT_TIMEOUT)));

                    /**
                     * @type {okhttp3.Call}
                     */
                    let call = this.__okhttp__.client().newCall(this.buildRequest(url, opt));

                    if (!callback && !cont) {
                        return _.wrapResponse(call.execute());
                    }

                    call.enqueue(new Callback({
                        onResponse(call, response) {
                            let wrappedResponse = _.wrapResponse(response);
                            cont && cont.resume(wrappedResponse);
                            callback && callback(wrappedResponse);
                        },
                        onFailure(call, ex) {
                            cont && cont.resumeError(ex);
                            callback && callback(null, ex);
                        },
                    }));

                    if (cont) {
                        return cont.await();
                    }
                },
                get(url, options, callback) {
                    return this.request(url, Object.assign(options || {}, {
                        method: 'GET',
                    }), callback);
                },
                post(url, data, options, callback) {
                    let opt = Object.assign({
                        contentType: _.constants.DEFAULT_CONTENT_TYPE,
                    }, options, {
                        method: 'POST',
                    });
                    _.fillPostData(opt, data);
                    return this.request(url, opt, callback);
                },
                postJson(url, data, options, callback) {
                    return this.post(url, data, Object.assign(options || {}, {
                        contentType: 'application/json',
                    }), callback);
                },
                postMultipart(url, files, options, callback) {
                    return this.request(url, Object.assign(options || {}, {
                        method: 'POST',
                        contentType: 'multipart/form-data',
                        files: files,
                    }), callback);
                },
            };

            return Http;
        })(),
        constants: {
            DEFAULT_CONTENT_TYPE: 'application/x-www-form-urlencoded',
            DEFAULT_TIMEOUT: 30e3,
        },
        /**
         * @param {okhttp3.Response} res
         * @return {Http.WrappedResponse}
         */
        wrapResponse: (res) => /* @AXR */ ({
            request: res.request(),
            getResponse() {
                /** @type {Http.WrappedResponse} */
                return {
                    request: this.request,
                    statusMessage: res.message(),
                    statusCode: res.code(),
                    body: this.getBody(),
                    headers: this.getHeaders(),
                    url: this.request.url(),
                    method: this.request.method(),
                };
            },
            getHeaders() {
                let result = {};
                let headers = res.headers();
                for (let i = 0; i < headers.size(); i += 1) {
                    let name = headers.name(i).toLowerCase();
                    let value = headers.value(i);
                    if (!(name in result)) {
                        result[name] = value;
                        continue;
                    }
                    let origin = result[name];
                    if (!Array.isArray(origin)) {
                        result[name] = [ origin ];
                    }
                    result[name].push(value);
                }
                return result;
            },
            getBody() {
                let resBody = res.body();
                let resBodyString;
                let resBodyBytes;
                return Object.setPrototypeOf({
                    string() {
                        if (typeof resBodyString !== 'undefined') {
                            return resBodyString;
                        }
                        return resBodyString = resBody.string();
                    },
                    bytes() {
                        if (typeof resBodyBytes !== 'undefined') {
                            return resBodyBytes;
                        }
                        return resBodyBytes = resBody.bytes();
                    },
                    json() {
                        /* "java.lang.IllegalStateException: closed" may happen. */
                        let str = this.string();
                        try {
                            return JSON.parse(str);
                        } catch (e) {
                            throw Error('Failed to parse JSON. Body string may be not in JSON format');
                        }
                    },
                    get contentType() {
                        return resBody.contentType();
                    },
                }, resBody);
            },
        }.getResponse()),
        /**
         * @param {Http.RequestBuilderOptions} options
         * @param {?Object.<string, string> | string} data
         */
        fillPostData(options, data) {
            data = data || {};
            if (options.contentType === _.constants.DEFAULT_CONTENT_TYPE) {
                let b = new FormBody.Builder();
                Object.entries(data).forEach((entries) => {
                    let [ key, value ] = entries;
                    b.add(key, value);
                });
                options.body = b.build();
            } else if (options.contentType === 'application/json') {
                options.body = JSON.stringify(data);
            } else {
                options.body = data;
            }
        },
    };

    /**
     * @type {Internal.Http}
     */
    const http = new _.Http();

    return http;
};