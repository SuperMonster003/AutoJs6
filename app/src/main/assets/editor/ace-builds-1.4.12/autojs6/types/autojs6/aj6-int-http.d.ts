// Type definitions for AutoJs6 internal module http
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 5.7.3
//
// Last modified: Jun 14, 2026

/// <reference path="./index.d.ts" />

/**
 * @Source /src/main/java/org/autojs/autojs/runtime/api/augment/http/Http.kt
 * @Source /src/main/java/org/autojs/autojs/runtime/api/augment/http/RequestBuilder.kt
 * @Source /src/main/java/org/autojs/autojs/runtime/api/augment/http/RequestBuilderHelper.kt
 * @Source /src/main/java/org/autojs/autojs/runtime/api/augment/http/ResponseWrapper.kt
 * @Source /src/main/java/org/autojs/autojs/runtime/api/augment/http/ResponseBodyNativeObject.kt
 * @Source /src/main/java/org/autojs/autojs/runtime/api/augment/http/HttpSaveResult.kt
 * @Source /src/main/java/org/autojs/autojs/runtime/api/Http.kt
 */

declare namespace Internal {

    interface Http {

        readonly __okhttp__: MutableOkHttp;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * http.client = function () {
         *     return http.__okhttp__.client();
         * };
         * @see org.autojs.autojs.core.http.MutableOkHttp.client
         */
        client(): okhttp3.OkHttpClient;

        /**
         * @example
         * let r = http.get("www.baidu.com");
         * log("code = " + r.statusCode);
         * log("html length = " + r.body.string().length);
         * @example
         * http.get("www.baidu.com", {}, function (res, err) {
         *     if (err) {
         *         throw err;
         *     }
         *     log("code = " + res.statusCode);
         *     log("html length = " + res.body.string().length);
         * });
         * @example
         * let r = http.get("www.baidu.com", {
         *     headers: {
         *         'Accept-Language': 'zh-cn,zh;q=0.5',
         *         'User-Agent': 'Mozilla/5.0(Macintosh;IntelMacOSX10_7_0)AppleWebKit/535.11(KHTML,likeGecko)Chrome/17.0.963.56Safari/535.11'
         *     }
         * });
         * log("code = " + r.statusCode);
         * log("html length = " + r.body.string().length);
         * @example
         * let res = http.get("http://t.weather.sojson.com/api/weather/city/101010100");
         * if (res.statusCode !== 200) {
         *     throw Error("请求失败: " + res.statusCode + " " + res.statusMessage);
         * }
         * let weather = res.body.json();
         * toastLog(util.format(
         *     "城市: %s\n温度: %s°C\n湿度: %s\n空气质量: %s",
         *     weather.cityInfo.city,
         *     weather.data.wendu,
         *     weather.data.shidu,
         *     weather.data.quality
         * ));
         * @example Source code summary (zh-CN: 源代码摘要)
         * http.get = function (url, options, callback) {
         *     options = options || {};
         *     options.method = "GET";
         *     return http.request(url, options, callback);
         * };
         * @see request
         */
        get(url: string, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Http.WrappedResponse | void;

        getAsync(url: string, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Promise<Http.WrappedResponse>;

        head(url: string, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Http.WrappedResponse | void;

        headAsync(url: string, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Promise<Http.WrappedResponse>;

        /**
         * @example
         * let url = "https://login.taobao.com/member/login.jhtml";
         * let username = "你的用户名";
         * let password = "你的密码";
         * let res = http.post(url, {
         *     "TPL_username": username,
         *     "TPL_password": password
         * });
         * let html = res.body.string();
         * if (~html.indexOf("页面跳转中")) {
         *     toast('登录成功');
         * } else {
         *     toast('登录失败');
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * http.post = function (url, data, options, callback) {
         *     options = options || {};
         *     options.method = "POST";
         *     options.contentType = options.contentType || "application/x-www-form-urlencoded";
         *     if (data) {
         *         fillPostData(options, data);
         *     }
         *     return http.request(url, options, callback);
         * };
         * function fillPostData(options, data) {
         *     if (options.contentType === "application/x-www-form-urlencoded") {
         *         let b = new FormBody.Builder();
         *         for (let key in data) {
         *             if (data.hasOwnProperty(key)) {
         *                 b.add(key, data[key]);
         *             }
         *         }
         *         options.body = b.build();
         *     } else if (options.contentType === "application/json") {
         *         options.body = JSON.stringify(data);
         *     } else {
         *         options.body = data;
         *     }
         * }
         */
        post(url: string, data?: Http.RequestData, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Http.WrappedResponse | void;

        postAsync(url: string, data?: Http.RequestData, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Promise<Http.WrappedResponse>;

        /**
         * @example
         * let r = http.postJson("http://www.tuling123.com/openapi/api", {
         *     key: "65458a5df537443b89b31f1c03202a80",
         *     info: "你好",
         *     userid: "1",
         * });
         * toastLog(r.body.json().text);
         * @example Source code summary (zh-CN: 源代码摘要)
         * http.postJson = function (url, data, options, callback) {
         *     options = options || {};
         *     options.contentType = "application/json";
         *     return http.post(url, data, options, callback);
         * };
         */
        postJson(url: string, data?: any, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Http.WrappedResponse | void;

        postJsonAsync(url: string, data?: any, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Promise<Http.WrappedResponse>;

        /**
         * @example
         * let res = http.postMultipart(url, {
         *     file: open('/sdcard/1.txt'), // PReadableTextFile | PWritableTextFile
         * });
         * log(res.body.string());
         * @example
         * let res = http.postMultipart(url, {
         *     file: ["1.txt", "/sdcard/1.txt"], // [fileName, filePath]
         * });
         * log(res.body.string());
         * @example
         * let res = http.postMultipart(url, {
         *     file: ["1.txt", "text/plain", "/sdcard/1.txt"], // [fileName, mimeType, filePath]
         * });
         * log(res.body.string());
         * @example
         * let res = http.postMultipart(url, {
         *     appId: "abc",
         *     file: open('/sdcard/1.txt')
         * });
         * log(res.body.string());
         * @example Source code summary (zh-CN: 源代码摘要)
         * http.postMultipart = function (url, files, options, callback) {
         *     options = options || {};
         *     options.method = "POST";
         *     options.contentType = "multipart/form-data";
         *     options.files = files;
         *     return http.request(url, options, callback);
         * };
         */
        postMultipart(url: string, files: Http.MultipartFiles, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Http.WrappedResponse | void;

        postMultipartAsync(url: string, files: Http.MultipartFiles, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Promise<Http.WrappedResponse>;

        /**
         * @example
         * let data = {wd: 'time'};
         * let b = new FormBody.Builder();
         * for (let key in data) {
         *     if (data.hasOwnProperty(key)) {
         *         b.add(key, data[key]);
         *     }
         * }
         * let body = b.build();
         * let res = http.request("www.baidu.com", {
         *     method: "POST",
         *     body: body,
         * });
         * log(res.statusCode);
         * log(res.body.string().length);
         * @example
         * let data = {wd: 'time'};
         * let b = new FormBody.Builder();
         * for (let key in data) {
         *     if (data.hasOwnProperty(key)) {
         *         b.add(key, data[key]);
         *     }
         * }
         * let body = b.build();
         * http.request("www.baidu.com", {
         *     method: "POST",
         *     body: body,
         * }, function (res, ex) {
         *     log(res.statusCode);
         *     log(res.body.string().length);
         * });
         * @example Source code summary (zh-CN: 源代码摘要)
         * http.request = function (url, options, callback) {
         *     let cont;
         *     if (!callback && ui.isUiThread() && continuation.enabled) {
         *         cont = continuation.create();
         *     }
         *     let call = http.client().newCall(http.buildRequest(url, options));
         *     if (!callback && !cont) {
         *         return wrapResponse(call.execute());
         *     }
         *     call.enqueue(new Callback({
         *         onResponse: function (call, res) {
         *             res = wrapResponse(res);
         *             cont && cont.resume(res);
         *             callback && callback(res);
         *         },
         *         onFailure: function (call, ex) {
         *             cont && cont.resumeError(ex);
         *             callback && callback(null, ex);
         *         }
         *     }));
         *     if (cont) {
         *         return cont.await();
         *     }
         * };
         * function wrapResponse(res) {
         *     let r = {};
         *     r.statusCode = res.code();
         *     r.statusMessage = res.message();
         *     let headers = res.headers();
         *     r.headers = {};
         *     for (let i = 0; i < headers.size(); i++) {
         *         let name = headers.name(i);
         *         let value = headers.value(i);
         *         if (r.headers.hasOwnProperty(name)) {
         *             let origin = r.headers[name];
         *             if (!Array.isArray(origin)) {
         *                 r.headers[name] = [origin];
         *             }
         *             r.headers[name].push(value);
         *         } else {
         *             r.headers[name] = value;
         *         }
         *     }
         *     r.body = {};
         *     let body = res.body();
         *     r.body.string = body.string.bind(body);
         *     r.body.bytes = body.bytes.bind(body);
         *     r.body.json = function () {
         *         return JSON.parse(r.body.string());
         *     };
         *     r.body.contentType = body.contentType();
         *     r.request = res.request();
         *     r.url = r.request.url();
         *     r.method = r.request.method();
         *     return r;
         * }
         */
        request(url: string, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Http.WrappedResponse | void;

        requestAsync(url: string, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Promise<Http.WrappedResponse>;

        put(url: string, data?: Http.RequestData, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Http.WrappedResponse | void;

        putAsync(url: string, data?: Http.RequestData, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Promise<Http.WrappedResponse>;

        delete(url: string, data?: Http.RequestData, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Http.WrappedResponse | void;

        del(url: string, data?: Http.RequestData, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Http.WrappedResponse | void;

        deleteAsync(url: string, data?: Http.RequestData, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Promise<Http.WrappedResponse>;

        delAsync(url: string, data?: Http.RequestData, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Promise<Http.WrappedResponse>;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * http.buildRequest = function (url, options) {
         *     let r = new Request.Builder();
         *     if (!url.startsWith('http://') && !url.startsWith('https://')) {
         *         url = "http://" + url;
         *     }
         *     r.url(url);
         *     if (options.headers) {
         *         setHeaders(r, options.headers);
         *     }
         *     if (options.body) {
         *         r.method(options.method, parseBody(options, options.body));
         *     } else if (options.files) {
         *         r.method(options.method, parseMultipart(options.files));
         *     } else {
         *         r.method(options.method, null);
         *     }
         *     return r.build();
         * };
         * function parseMimeType(ext) {
         *     if (ext.length === 0) {
         *         return "application/octet-stream";
         *     }
         *     return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
         *         || "application/octet-stream";
         * }
         * function parseMultipart(files) {
         *     let builder = new MultipartBody.Builder()
         *         .setType(MultipartBody.FORM);
         *     for (let key in files) {
         *         if (!files.hasOwnProperty(key)) {
         *             continue;
         *         }
         *         let value = files[key];
         *         if (typeof (value) == 'string') {
         *             builder.addFormDataPart(key, value);
         *             continue;
         *         }
         *         let path, mimeType, fileName;
         *         if (typeof (value.getPath) == 'function') {
         *             path = value.getPath();
         *         } else if (value.length === 2) {
         *             fileName = value[0];
         *             path = value[1];
         *         } else if (value.length >= 3) {
         *             fileName = value[0];
         *             mimeType = value[1];
         *             path = value[2];
         *         }
         *         const file = new org.autojs.autojs.pio.PFile(path);
         *         fileName = fileName || file.getName();
         *         mimeType = mimeType || parseMimeType(file.getExtension());
         *         builder.addFormDataPart(key, fileName, RequestBody.create(MediaType.parse(mimeType), file));
         *     }
         *     return builder.build();
         * }
         * function setHeaders(r, headers) {
         *     for (let key in headers) {
         *         if (headers.hasOwnProperty(key)) {
         *             let value = headers[key];
         *             if (Array.isArray(value)) {
         *                 value.forEach(v => {
         *                     r.header(key, v);
         *                 });
         *             } else {
         *                 r.header(key, value);
         *             }
         *         }
         *     }
         * }
         * function parseBody(options, body) {
         *     if (typeof (body) == "string") {
         *         body = RequestBody.create(MediaType.parse(options.contentType), body);
         *     } else if (body instanceof RequestBody) {
         *         return body;
         *     } else {
         *         body = new RequestBody({
         *             contentType: function () {
         *                 return MediaType.parse(options.contentType);
         *             },
         *             writeTo: body
         *         });
         *     }
         *     return body;
         * }
         */
        buildRequest(url: string, options?: Http.RequestBuilderOptions): okhttp3.Request;

    }

}

declare namespace Http {

    type Callback = (response: Http.WrappedResponse | null, ex?: java.io.IOException | null) => void;

    type Method = 'GET' | 'POST' | 'HEAD' | 'PUT' | 'DELETE' | 'CONNECT' | 'OPTIONS' | 'TRACE' | 'PATCH';

    type RequestData = any;

    type BodyWriter = (bufferedSink: okio.BufferedSink) => void;

    type MultipartFileValue =
        | string
        | number
        | [fileName: string, path: string | java.net.URI]
        | [fileName: string, mimeType: string, path: string | java.net.URI]
        | org.autojs.autojs.pio.PFileInterface
        | org.autojs.autojs.pio.PReadableTextFile
        | org.autojs.autojs.pio.PWritableTextFile;

    interface MultipartFiles {
        [name: string]: MultipartFileValue;
    }

    interface ClientBuilderOptions {
        [methodName: string]: any;
    }

    // noinspection SpellCheckingInspection
    type ContentType = 'application/atom+xml' | 'application/fractals' | 'application/futuresplash' | 'application/hta' | 'application/json' | 'application/mac-binhex40' | 'application/msaccess' | 'application/msword' | 'application/octet-stream' | 'application/pdf' | 'application/pics-rules' | 'application/pkcs10' | 'application/pkcs7-mime' | 'application/pkcs7-signature' | 'application/pkix-crl' | 'application/postscript' | 'application/rat-file' | 'application/sdp' | 'application/smil' | 'application/streamingmedia' | 'application/vnd.adobe.edn' | 'application/vnd.adobe.pdx' | 'application/vnd.adobe.rmf' | 'application/vnd.adobe.workflow' | 'application/vnd.adobe.xdp' | 'application/vnd.adobe.xfd' | 'application/vnd.adobe.xfdf' | 'application/vnd.android.package-archive' | 'application/vnd.fdf' | 'application/vnd.iphone' | 'application/vnd.ms-excel' | 'application/vnd.ms-pki.certstore' | 'application/vnd.ms-pki.pko' | 'application/vnd.ms-pki.seccat' | 'application/vnd.ms-pki.stl' | 'application/vnd.ms-powerpoint' | 'application/vnd.ms-project' | 'application/vnd.ms-wpl' | 'application/vnd.rn-realmedia' | 'application/vnd.rn-realmedia-secure' | 'application/vnd.rn-realmedia-vbr' | 'application/vnd.rn-realplayer' | 'application/vnd.rn-realsystem-rjs' | 'application/vnd.rn-realsystem-rjt' | 'application/vnd.rn-realsystem-rmj' | 'application/vnd.rn-realsystem-rmx' | 'application/vnd.rn-recording' | 'application/vnd.rn-rn_music_package' | 'application/vnd.rn-rsml' | 'application/vnd.symbian.install' | 'application/vnd.visio' | 'application/x-' | 'application/x-001' | 'application/x-301' | 'application/x-906' | 'application/x-a11' | 'application/x-anv' | 'application/x-bittorrent' | 'application/x-bmp' | 'application/x-bot' | 'application/x-c4t' | 'application/x-c90' | 'application/x-cals' | 'application/x-cdr' | 'application/x-cel' | 'application/x-cgm' | 'application/x-cit' | 'application/x-cmp' | 'application/x-cmx' | 'application/x-cot' | 'application/x-csi' | 'application/x-cut' | 'application/x-dbf' | 'application/x-dbm' | 'application/x-dbx' | 'application/x-dcx' | 'application/x-dgn' | 'application/x-dib' | 'application/x-drw' | 'application/x-dwf' | 'application/x-dwg' | 'application/x-dxb' | 'application/x-dxf' | 'application/x-ebx' | 'application/x-emf' | 'application/x-epi' | 'application/x-frm' | 'application/x-g4' | 'application/x-gbr' | 'application/x-gl2' | 'application/x-gp4' | 'application/x-hgl' | 'application/x-hmr' | 'application/x-hpgl' | 'application/x-hpl' | 'application/x-hrf' | 'application/x-icb' | 'application/x-ico' | 'application/x-icq' | 'application/x-iff' | 'application/x-igs' | 'application/x-img' | 'application/x-internet-signup' | 'application/x-iphone' | 'application/x-javascript' | 'application/x-jpe' | 'application/x-jpg' | 'application/x-laplayer-reg' | 'application/x-latex' | 'application/x-lbm' | 'application/x-ltr' | 'application/x-mac' | 'application/x-mdb' | 'application/x-mi' | 'application/x-mil' | 'application/x-mmxp' | 'application/x-ms-wmd' | 'application/x-ms-wmz' | 'application/x-msdownload' | 'application/x-netcdf' | 'application/x-nrf' | 'application/x-out' | 'application/x-pc5' | 'application/x-pci' | 'application/x-pcl' | 'application/x-pcx' | 'application/x-perl' | 'application/x-pgl' | 'application/x-pic' | 'application/x-pkcs12' | 'application/x-pkcs7-certificates' | 'application/x-pkcs7-certreqresp' | 'application/x-plt' | 'application/x-png' | 'application/x-ppm' | 'application/x-ppt' | 'application/x-pr' | 'application/x-prn' | 'application/x-prt' | 'application/x-ps' | 'application/x-ptn' | 'application/x-ras' | 'application/x-red' | 'application/x-rgb' | 'application/x-rlc' | 'application/x-rle' | 'application/x-rtf' | 'application/x-sam' | 'application/x-sat' | 'application/x-sdw' | 'application/x-shockwave-flash' | 'application/x-silverlight-app' | 'application/x-slb' | 'application/x-sld' | 'application/x-smk' | 'application/x-stuffit' | 'application/x-sty' | 'application/x-tdf' | 'application/x-tg4' | 'application/x-tga' | 'application/x-tif' | 'application/x-troff-man' | 'application/x-vda' | 'application/x-vpeg005' | 'application/x-vsd' | 'application/x-vst' | 'application/x-wb1' | 'application/x-wb2' | 'application/x-wb3' | 'application/x-wk3' | 'application/x-wk4' | 'application/x-wkq' | 'application/x-wks' | 'application/x-wmf' | 'application/x-wp6' | 'application/x-wpd' | 'application/x-wpg' | 'application/x-wq1' | 'application/x-wr1' | 'application/x-wri' | 'application/x-wrk' | 'application/x-ws' | 'application/x-www-form-urlencoded' | 'application/x-x509-ca-cert' | 'application/x-xls' | 'application/x-xlw' | 'application/x-xwd' | 'application/x-x_b' | 'application/x-x_t' | 'application/xhtml+xml' | 'application/xml' | 'audio/aiff' | 'audio/basic' | 'audio/mid' | 'audio/mp1' | 'audio/mp2' | 'audio/mp3' | 'audio/mpegurl' | 'audio/rn-mpeg' | 'audio/scpls' | 'audio/vnd.rn-realaudio' | 'audio/wav' | 'audio/x-la-lms' | 'audio/x-liquid-file' | 'audio/x-liquid-secure' | 'audio/x-mei-aac' | 'audio/x-ms-wax' | 'audio/x-ms-wma' | 'audio/x-musicnet-download' | 'audio/x-musicnet-stream' | 'audio/x-pn-realaudio' | 'audio/x-pn-realaudio-plugin' | 'drawing/907' | 'drawing/x-slk' | 'drawing/x-top' | 'image/fax' | 'image/gif' | 'image/jpeg' | 'image/pnetvue' | 'image/png' | 'image/tiff' | 'image/vnd.rn-realpix' | 'image/vnd.wap.wbmp' | 'image/x-icon' | 'java/*' | 'message/rfc822' | 'Model/vnd.dwf' | 'multipart/form-data' | 'text/asa' | 'text/asp' | 'text/css' | 'text/h323' | 'text/html' | 'text/iuls' | 'text/plain' | 'text/scriptlet' | 'text/vnd.rn-realtext' | 'text/vnd.rn-realtext3d' | 'text/vnd.wap.wml' | 'text/webviewhtml' | 'text/x-component' | 'text/x-ms-odc' | 'text/x-vcard' | 'text/xml' | 'video/avi' | 'video/mpeg' | 'video/mpeg4' | 'video/mpg' | 'video/vnd.rn-realvideo' | 'video/x-ivf' | 'video/x-mpeg' | 'video/x-mpg' | 'video/x-ms-asf' | 'video/x-ms-wm' | 'video/x-ms-wmv' | 'video/x-ms-wmx' | 'video/x-ms-wvx' | 'video/x-sgi-movie';

    interface RequestBuilderOptions {

        /**
         * @see https://blog.csdn.net/xiaochengyihe/article/details/80910913
         */
        headers?: {
            [prop: string]: any;

            'Accept'?: '*/*' | 'text/html' | 'image/*' | 'text/html, application/xhtml+xml, application/xml;q=0.9, */*;q=0.8' | string;
            'Accept-Charset'?: string;
            'Accept-Encoding'?: string;
            'Accept-Language'?: string;
            'Accept-Ranges'?: string;
            'Authorization'?: string;
            'Cache-Control'?: string;
            'Connection'?: string;
            'Cookie'?: string;
            'Content-Language'?: string;
            'Content-Length'?: string | number;
            'Content-Type'?: Http.ContentType;
            'Date'?: string;
            'Expect'?: string;
            'From'?: string;
            'Host'?: string;
            'If-Match'?: string;
            'If-Modified-Since'?: string;
            'If-None-Match'?: string;
            'If-Range'?: string;
            'If-Unmodified-Since'?: string;
            'Max-Forwards'?: string | number;
            'Pragma'?: string;
            'Proxy-Authorization'?: string;
            'Range'?: string;
            'Referer'?: string;
            'TE'?: string;
            'Upgrade'?: string;
            'User-Agent'?: string;
            'Via'?: string;
            'Warning'?: string;
        };
        body?: string | okhttp3.RequestBody | Http.BodyWriter;
        method?: Http.Method;
        files?: Http.MultipartFiles;
        contentType?: Http.ContentType | string;
        client?: Http.ClientBuilderOptions;
        isInsecure?: boolean;
        insecure?: boolean;
        /**
         * Timeout (milliseconds) for readTimeout/writeTimeout/connectTimeout.
         * @default 30e3
         */
        timeout?: number;
        /**
         * Max retries for making the request.
         * @default 3
         */
        maxRetries?: number;
        /**
         * Cache response body content after reading string() or bytes().
         * @default false
         */
        cacheBody?: boolean;
        /**
         * Max body size allowed for response body cache.
         * @default 8 * 1024 * 1024
         */
        bodyCacheThresholdBytes?: number;
    }

    interface WrappedResponse {

        statusCode: number;
        statusMessage: string;
        headers: {
            [prop: string]: string | string[];
            'Cache-Control'?: string | string[];
            'Connection'?: string | string[];
            'Content-Type'?: string | string[];
            'Date'?: string | string[];
            'Last-Modified'?: string | string[];
            'Pragma'?: string | string[];
            'Server'?: string | string[];
            'Set-Cookie'?: string | string[];
            'Transfer-Encoding'?: string | string[];
            'Etag'?: string | string[];
        };
        body: {
            /**
             * @see okhttp3.ResponseBody.string
             */
            string(): string;
            /**
             * @see okhttp3.ResponseBody.bytes
             */
            bytes(): number[];
            /**
             * @example Source code summary (zh-CN: 源代码摘要)
             * r.body = {};
             * let body = res.body();
             * r.body.string = body.string.bind(body);
             * r.body.json = function () {
             *     return JSON.parse(r.body.string());
             * };
             */
            json(): any;
            /**
             * Returns the response body stream. Caller owns closing the stream/body.
             */
            stream(): java.io.InputStream;
            /**
             * Saves the response body directly to a file path.
             */
            saveToFile(path: string, bufferSize?: number): Http.SaveResult;
            /**
             * Explicitly closes the response body.
             */
            close(): void;
            /**
             * @example Source code summary (zh-CN: 源代码摘要)
             * r.body = {};
             * let body = res.body();
             * r.body.contentType = body.contentType();
             * @see okhttp3.ResponseBody.contentType
             */
            contentType: okhttp3.MediaType | null;
        };
        request: okhttp3.Request;
        url: okhttp3.HttpUrl;
        method: string;
    }

    interface SaveResult extends org.mozilla.javascript.NativeObject {

        readonly code: number;

        readonly path: string | null;

        readonly bytesCopied: number;

        readonly success: boolean;

        readonly error: java.lang.Throwable | null;

        isSuccess(): boolean;

    }

}
