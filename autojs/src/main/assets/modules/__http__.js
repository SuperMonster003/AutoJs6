// noinspection JSUnusedGlobalSymbols

/* Here, importClass() is not recommended for intelligent code completion in IDE like WebStorm. */
/* The same is true of destructuring assignment syntax (like `let {Uri} = android.net`). */

const PFile = com.stardust.pio.PFile;
const Request = okhttp3.Request;
const RequestBody = okhttp3.RequestBody;
const MultipartBody = okhttp3.MultipartBody;
const MediaType = okhttp3.MediaType;
const FormBody = okhttp3.FormBody;
const Callback = okhttp3.Callback;
const MimeTypeMap = android.webkit.MimeTypeMap;

let _ = {
    constants: {
        DEF_CONTENT_TYPE: 'application/x-www-form-urlencoded',
    },
    init(__runtime__, scope) {
        this.runtime = __runtime__;
        this.scope = scope;
        this.http = {};
    },
    getModule() {
        return this.http;
    },
    selfAugment() {
        Object.assign(this.http, {
            __okhttp__: new MutableOkHttp(),
            client() {
                return this.__okhttp__.client();
            },
            /**
             * @param {string} url
             * @param {Http.RequestOptions} [options]
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
                    setHeaders(request) {
                        Object.entries(this.options.headers || {}).forEach((entries) => {
                            let [key, value] = entries;
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
                            let [key, value] = entries;
                            if (typeof value === 'string') {
                                builder.addFormDataPart(key, value);
                                return;
                            }
                            let path, mimeType, fileName;
                            if (typeof value.getPath === 'function') {
                                path = value.getPath();
                            } else if (value.length === 2) {
                                [fileName, path] = value;
                            } else if (value.length > 2) {
                                [fileName, mimeType, path] = value;
                            }
                            let file = new PFile(path);
                            fileName = fileName || file.getName();
                            mimeType = mimeType || this.parseMimeType(file.getExtension());
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
             * @param {Http.RequestOptions} [options]
             * @param {(response: Http.WrappedResponse, ex?: java.io.IOException) => void} [callback]
             * @return {Http.WrappedResponse | void}
             */
            request(url, options, callback) {
                let cont = !callback && ui.isUiThread() && continuation.enabled
                    ? continuation.create() : null;
                let call = this.client().newCall(this.buildRequest(url, options));

                if (!callback && !cont) {
                    return _.wrapResponse(call.execute());
                }

                call.enqueue(new Callback({
                    onResponse(call, res) {
                        res = _.wrapResponse(res);
                        cont && cont.resume(res);
                        callback && callback(res);
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
                    contentType: _.constants.DEF_CONTENT_TYPE,
                }, options, {
                    method: 'POST',
                });
                if (data) {
                    _.fillPostData(opt, data);
                }
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
        });
    },
    /**
     * @param {okhttp3.Response} res
     * @return {Http.WrappedResponse}
     */
    wrapResponse: (res) => /* @AXR */ ({
        request: res.request(),
        getResponse() {
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
                let name = headers.name(i);
                let value = headers.value(i);
                if (!(name in result)) {
                    result[name] = value;
                    continue;
                }
                let origin = result[name];
                if (!Array.isArray(origin)) {
                    result[name] = [origin];
                }
                result[name].push(value);
            }
            return result;
        },
        getBody() {
            let body = res.body();
            return {
                string: body.string.bind(body),
                bytes: body.bytes.bind(body),
                contentType: body.contentType(),
                json() {
                    try {
                        return JSON.parse(this.string());
                    } catch (e) {
                        console.warn(`${e.message}\n${e.stack}`);
                        throw Error('JSON parsed failed. Body string may be not in JSON format');
                    }
                },
            };
        },
    }.getResponse()),
    /**
     * @param {Http.RequestOptions} options
     * @param {Object.<string, string>} data
     */
    fillPostData(options, data) {
        if (options.contentType === _.constants.DEF_CONTENT_TYPE) {
            let b = new FormBody.Builder();
            Object.entries(data).forEach((entries) => {
                let [key, value] = entries;
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

let $ = {
    getModule(__runtime__, scope) {
        _.init(__runtime__, scope);

        _.selfAugment();

        return _.getModule();
    },
};

/**
 * @param {com.stardust.autojs.runtime.ScriptRuntime} __runtime__
 * @param {org.mozilla.javascript.Scriptable} scope
 * @return {Internal.Http}
 */
module.exports = function (__runtime__, scope) {
    return $.getModule(__runtime__, scope);
};