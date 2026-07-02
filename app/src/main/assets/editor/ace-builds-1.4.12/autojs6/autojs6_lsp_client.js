(function(global) {
    "use strict";

    var FALLBACK_STATIC_COMPLETION = "static-completion";
    var STATE_DISABLED = "disabled";
    var STATE_LOCAL_LANGUAGE_SERVICE = "local-language-service";
    var TRANSPORT_IN_PROCESS = "in-process";
    var COMPLETION_PROVIDER_STATIC = "static-fallback";
    var HOVER_PROVIDER_STATIC = "static-fallback";
    var COMPLETION_PROVIDER_LOCAL_INDEX = "local-index";
    var HOVER_PROVIDER_LOCAL_INDEX = "local-index";
    var COMPLETION_PROVIDER_TYPESCRIPT = "typescript-language-service";
    var HOVER_PROVIDER_TYPESCRIPT = "typescript-language-service";
    var DIAGNOSTIC_PROVIDER_DISABLED = "disabled";
    var DIAGNOSTIC_PROVIDER_ACE_JSHINT = "ace-jshint";
    var DIAGNOSTIC_PROVIDER_LOCAL_SYNTAX = "local-syntax";
    var DIAGNOSTIC_PROVIDER_TYPESCRIPT = "typescript-language-service";
    var SIGNATURE_PROVIDER_STATIC_LOCAL = "static-local";
    var SIGNATURE_PROVIDER_TYPESCRIPT = "typescript-language-service";
    var DIAGNOSTIC_SOURCE = "autojs6-lsp";
    var VALIDATION_DELAY_MS = 450;
    var JSHINT_SCRIPT_SRC = "./src-min-noconflict/worker-javascript.js";
    var TS_RUNTIME_SCRIPT_SRC = "./autojs6/typescript/typescriptServices.js";
    var TS_SERVICE_SCRIPT_SRC = "./autojs6/autojs6_ts_language_service.js";

    var jshintLoadState = "idle";
    var jshintCallbacks = [];
    var tsLoadState = "idle";
    var tsLoadCallbacks = [];

    function noop() {
    }

    function normalizePosition(pos) {
        pos = pos || {};
        return {
            row: Math.max(0, Number(pos.row) || 0),
            column: Math.max(0, Number(pos.column) || 0)
        };
    }

    function copyArray(value) {
        return Array.isArray(value) ? value.slice(0) : [];
    }

    function timerSet(callback, delayMs) {
        var fn = global.setTimeout || (typeof setTimeout === "function" ? setTimeout : null);
        if (!fn) {
            callback();
            return null;
        }
        return fn(callback, delayMs);
    }

    function timerClear(timer) {
        var fn = global.clearTimeout || (typeof clearTimeout === "function" ? clearTimeout : null);
        if (timer !== null && fn) {
            fn(timer);
        }
    }

    function notify(config, message, error) {
        if (config && typeof config.notifyError === "function") {
            config.notifyError(String(message || "ACE LSP error"), error);
        }
    }

    function parseOptions(rawOptions, config) {
        if (!rawOptions) {
            return {};
        }
        if (typeof rawOptions === "object") {
            return rawOptions;
        }
        try {
            return JSON.parse(String(rawOptions));
        } catch (error) {
            notify(config, "Invalid ACE LSP options: " + error, error);
            return {};
        }
    }

    function stateFromOptions(options) {
        var enabled = !!(options && options.enabled === true);
        return {
            enabled: enabled,
            manager: options && options.manager || "",
            state: enabled ? (options && options.state || STATE_LOCAL_LANGUAGE_SERVICE) : STATE_DISABLED,
            transport: enabled ? (options && options.transport || TRANSPORT_IN_PROCESS) : "",
            serverUri: options && options.serverUri || null,
            rootUri: options && options.rootUri || "",
            documentUri: options && options.documentUri || "",
            libraryUris: copyArray(options && options.libraryUris),
            fallback: options && options.fallback || FALLBACK_STATIC_COMPLETION,
            startSupported: !!(options && options.startSupported),
            serverAvailable: !!(options && options.serverAvailable),
            serverReady: false,
            localServiceReady: enabled,
            reason: options && options.reason || "",
            completionProvider: enabled ?
                (options && options.completionProvider || COMPLETION_PROVIDER_LOCAL_INDEX) :
                COMPLETION_PROVIDER_STATIC,
            hoverProvider: enabled ?
                (options && options.hoverProvider || HOVER_PROVIDER_LOCAL_INDEX) :
                HOVER_PROVIDER_STATIC,
            diagnosticProvider: enabled ?
                (options && options.diagnosticProvider || DIAGNOSTIC_PROVIDER_ACE_JSHINT) :
                DIAGNOSTIC_PROVIDER_DISABLED,
            signatureProvider: options && options.signatureProvider || SIGNATURE_PROVIDER_STATIC_LOCAL,
            features: copyArray(options && options.features)
        };
    }

    function textFromSession(session) {
        if (!session) {
            return "";
        }
        if (typeof session.getValue === "function") {
            return String(session.getValue() || "");
        }
        var lines = [];
        var length = typeof session.getLength === "function" ? session.getLength() : 1;
        for (var row = 0; row < length; row++) {
            lines.push(session.getLine(row) || "");
        }
        return lines.join("\n");
    }

    function positionFromIndex(text, index) {
        text = String(text || "");
        index = Math.max(0, Math.min(Number(index) || 0, text.length));
        var row = 0;
        var column = 0;
        for (var i = 0; i < index; i++) {
            var ch = text.charAt(i);
            if (ch === "\n") {
                row++;
                column = 0;
            } else if (ch !== "\r") {
                column++;
            }
        }
        return { row: row, column: column };
    }

    function annotation(row, column, text, type, raw) {
        return {
            row: Math.max(0, Number(row) || 0),
            column: Math.max(0, Number(column) || 0),
            text: String(text || ""),
            type: type || "error",
            raw: raw || ""
        };
    }

    function isJsonDocumentUri(uri) {
        return /\.json(?:[?#].*)?$/i.test(String(uri || ""));
    }

    function jsonSyntaxDiagnostics(text) {
        text = String(text || "");
        try {
            JSON.parse(text);
            return [];
        } catch (error) {
            var message = String(error && error.message || "Invalid JSON");
            var match = /position\s+(\d+)/i.exec(message);
            var index = match ? Number(match[1]) : text.length;
            var pos = positionFromIndex(text, index);
            return [annotation(pos.row, pos.column, message, "error", "json-syntax")];
        }
    }

    function getJshint() {
        try {
            if (!global.ace || typeof global.ace.require !== "function") {
                return null;
            }
            var module = global.ace.require("ace/mode/javascript/jshint");
            return module && module.JSHINT || null;
        } catch (ignore) {
            return null;
        }
    }

    function flushJshintCallbacks(lint) {
        var callbacks = jshintCallbacks.slice(0);
        jshintCallbacks = [];
        callbacks.forEach(function(callback) {
            try {
                callback(lint || null);
            } catch (ignore) {
                // Ignore callback failures; the validator will report its own errors.
            }
        });
    }

    function flushTsLoadCallbacks(ok) {
        var callbacks = tsLoadCallbacks.slice(0);
        tsLoadCallbacks = [];
        callbacks.forEach(function(callback) {
            try {
                callback(!!ok);
            } catch (ignore) {
                // Ignore callback failures; callers own their reporting.
            }
        });
    }

    function hasTsLanguageService() {
        return !!(global.AutoJsAceTsLanguageService &&
            typeof global.AutoJsAceTsLanguageService.create === "function");
    }

    function appendScript(src, onload, onerror) {
        if (!global.document || !global.document.createElement) {
            onerror("document unavailable");
            return;
        }
        try {
            var script = global.document.createElement("script");
            script.src = src;
            script.async = true;
            script.onload = function() {
                onload();
            };
            script.onerror = function(error) {
                onerror(error);
            };
            var parent = global.document.head || global.document.body || global.document.documentElement;
            if (!parent || !parent.appendChild) {
                onerror("document head unavailable");
                return;
            }
            parent.appendChild(script);
        } catch (error) {
            onerror(error);
        }
    }

    function ensureTsLanguageServiceLoaded(config, callback) {
        callback = typeof callback === "function" ? callback : noop;
        if (hasTsLanguageService()) {
            tsLoadState = "loaded";
            callback(true);
            return true;
        }
        if (tsLoadState === "failed") {
            callback(false);
            return false;
        }
        tsLoadCallbacks.push(callback);
        if (tsLoadState === "loading") {
            return false;
        }
        tsLoadState = "loading";
        appendScript(TS_RUNTIME_SCRIPT_SRC, function() {
            appendScript(TS_SERVICE_SCRIPT_SRC, function() {
                tsLoadState = hasTsLanguageService() ? "loaded" : "failed";
                if (tsLoadState === "failed") {
                    notify(config, "ACE TypeScript language service did not register after loading");
                }
                flushTsLoadCallbacks(tsLoadState === "loaded");
            }, function(error) {
                tsLoadState = "failed";
                notify(config, "ACE TypeScript language service script unavailable", error);
                flushTsLoadCallbacks(false);
            });
        }, function(error) {
            tsLoadState = "failed";
            notify(config, "ACE TypeScript runtime script unavailable", error);
            flushTsLoadCallbacks(false);
        });
        return false;
    }

    function ensureJshintLoaded(config, callback) {
        var lint = getJshint();
        if (lint) {
            callback(lint);
            return true;
        }
        if (!global.document || !global.document.createElement) {
            return false;
        }
        if (jshintLoadState === "failed") {
            return false;
        }
        jshintCallbacks.push(callback);
        if (jshintLoadState === "loading") {
            return false;
        }

        jshintLoadState = "loading";
        try {
            var script = global.document.createElement("script");
            script.src = JSHINT_SCRIPT_SRC;
            script.async = true;
            script.onload = function() {
                jshintLoadState = getJshint() ? "loaded" : "failed";
                flushJshintCallbacks(getJshint());
            };
            script.onerror = function(error) {
                jshintLoadState = "failed";
                notify(config, "ACE JSHint diagnostics unavailable", error);
                flushJshintCallbacks(null);
            };
            var parent = global.document.head || global.document.body || global.document.documentElement;
            if (!parent || !parent.appendChild) {
                jshintLoadState = "failed";
                flushJshintCallbacks(null);
                return false;
            }
            parent.appendChild(script);
        } catch (error) {
            jshintLoadState = "failed";
            notify(config, "ACE JSHint diagnostics unavailable: " + error, error);
            flushJshintCallbacks(null);
        }
        return false;
    }

    function addGlobal(globals, name) {
        name = String(name || "");
        if (/^[A-Za-z_$][A-Za-z0-9_$]*$/.test(name)) {
            globals[name] = false;
        }
    }

    function defaultGlobalNames() {
        return [
            "console", "require", "module", "exports", "global", "java", "Packages",
            "android", "org", "com", "runtime", "engines", "events", "ui",
            "$ui", "$settings", "$app", "$crypto", "$debug", "$engines", "$files",
            "$http", "$images", "$shell", "$threads", "$timers", "$zip",
            "importClass", "importPackage", "toast", "log", "sleep"
        ];
    }

    function buildJshintGlobals(getStaticCompleter) {
        var globals = Object.create(null);
        defaultGlobalNames().forEach(function(name) {
            addGlobal(globals, name);
        });
        try {
            var completer = getStaticCompleter();
            if (completer && typeof completer.getGlobalNames === "function") {
                completer.getGlobalNames().forEach(function(name) {
                    addGlobal(globals, name);
                });
            }
        } catch (ignore) {
            // Static completion is optional for diagnostics.
        }
        return globals;
    }

    function startRegex(parts) {
        return RegExp("^(" + parts.join("|") + ")");
    }

    var disabledWarningsRe = startRegex(["Bad for in variable '(.+)'.", 'Missing "use strict"']);
    var errorsRe = startRegex([
        "Unexpected", "Expected ", "Confusing (plus|minus)",
        "\\{a\\} unterminated regular expression", "Unclosed ", "Unmatched ",
        "Unbegun comment", "Bad invocation", "Missing space after", "Missing operator at"
    ]);
    var infoRe = startRegex([
        "Expected an assignment", "Bad escapement of EOL", "Unexpected comma",
        "Unexpected space", "Missing radix parameter.", "A leading decimal point can",
        "\\['{a}'\\] is better written in dot notation.", "'{a}' used out of scope"
    ]);

    function jshintAnnotationType(error) {
        var raw = String(error && error.raw || "");
        if (raw === "Missing semicolon.") {
            return "info";
        }
        if (disabledWarningsRe.test(raw)) {
            return "";
        }
        if (infoRe.test(raw) || raw === "'{a}' is defined but never used.") {
            return "info";
        }
        if (errorsRe.test(raw)) {
            return "error";
        }
        return "warning";
    }

    function runJshintDiagnostics(text, getStaticCompleter, config) {
        var lint = getJshint();
        if (!lint) {
            return null;
        }
        text = String(text || "").replace(/^#!.*\n/, "\n");
        if (!text) {
            return [];
        }
        var globals = buildJshintGlobals(getStaticCompleter);
        var options = {
            esnext: true,
            moz: true,
            devel: true,
            browser: true,
            node: true,
            laxcomma: true,
            laxbreak: true,
            lastsemic: true,
            onevar: false,
            passfail: false,
            maxerr: 100,
            expr: true,
            multistr: true,
            globalstrict: true,
            asi: true,
            undef: false,
            globals: globals
        };

        try {
            lint(text, options, globals);
            var result = [];
            (lint.errors || []).forEach(function(error) {
                if (!error) {
                    return;
                }
                var type = jshintAnnotationType(error);
                if (!type) {
                    return;
                }
                result.push(annotation(
                    (Number(error.line) || 1) - 1,
                    (Number(error.character) || 1) - 1,
                    error.reason || error.raw || "JavaScript diagnostic",
                    type,
                    error.raw || ""
                ));
            });
            return result;
        } catch (error) {
            notify(config, "ACE JSHint diagnostics failed: " + error, error);
            return null;
        }
    }

    function advancePosition(pos, ch) {
        if (ch === "\n") {
            pos.row++;
            pos.column = 0;
        } else if (ch !== "\r") {
            pos.column++;
        }
    }

    function localSyntaxDiagnostics(text) {
        text = String(text || "");
        var annotations = [];
        var stack = [];
        var pos = { row: 0, column: 0 };
        var quote = "";
        var quoteStart = null;
        var escaped = false;
        var blockCommentStart = null;
        var lineComment = false;
        var pairs = { "(": ")", "[": "]", "{": "}" };
        var closers = { ")": "(", "]": "[", "}": "{" };

        for (var i = 0; i < text.length; i++) {
            var ch = text.charAt(i);
            var next = text.charAt(i + 1);

            if (lineComment) {
                if (ch === "\n" || ch === "\r") {
                    lineComment = false;
                }
                advancePosition(pos, ch);
                continue;
            }

            if (blockCommentStart) {
                if (ch === "*" && next === "/") {
                    advancePosition(pos, ch);
                    i++;
                    advancePosition(pos, next);
                    blockCommentStart = null;
                    continue;
                }
                advancePosition(pos, ch);
                continue;
            }

            if (quote) {
                if (escaped) {
                    escaped = false;
                } else if (ch === "\\") {
                    escaped = true;
                } else if (ch === quote) {
                    quote = "";
                    quoteStart = null;
                } else if (quote !== "`" && (ch === "\n" || ch === "\r")) {
                    annotations.push(annotation(
                        quoteStart.row,
                        quoteStart.column,
                        "Unterminated string literal",
                        "error",
                        "local-syntax"
                    ));
                    quote = "";
                    quoteStart = null;
                }
                advancePosition(pos, ch);
                continue;
            }

            if (ch === "/" && next === "/") {
                lineComment = true;
                advancePosition(pos, ch);
                i++;
                advancePosition(pos, next);
                continue;
            }
            if (ch === "/" && next === "*") {
                blockCommentStart = { row: pos.row, column: pos.column };
                advancePosition(pos, ch);
                i++;
                advancePosition(pos, next);
                continue;
            }
            if (ch === "\"" || ch === "'" || ch === "`") {
                quote = ch;
                quoteStart = { row: pos.row, column: pos.column };
                advancePosition(pos, ch);
                continue;
            }
            if (pairs[ch]) {
                stack.push({ ch: ch, row: pos.row, column: pos.column });
            } else if (closers[ch]) {
                var opener = stack.pop();
                if (!opener || opener.ch !== closers[ch]) {
                    annotations.push(annotation(
                        pos.row,
                        pos.column,
                        "Unmatched closing " + ch,
                        "error",
                        "local-syntax"
                    ));
                }
            }
            advancePosition(pos, ch);
        }

        if (quote && quoteStart) {
            annotations.push(annotation(
                quoteStart.row,
                quoteStart.column,
                "Unterminated string literal",
                "error",
                "local-syntax"
            ));
        }
        if (blockCommentStart) {
            annotations.push(annotation(
                blockCommentStart.row,
                blockCommentStart.column,
                "Unclosed block comment",
                "error",
                "local-syntax"
            ));
        }
        stack.reverse().forEach(function(opener) {
            annotations.push(annotation(
                opener.row,
                opener.column,
                "Unclosed " + opener.ch,
                "error",
                "local-syntax"
            ));
        });
        return annotations;
    }

    function annotationToDiagnostic(item) {
        var start = {
            line: Math.max(0, Number(item && item.row) || 0),
            character: Math.max(0, Number(item && item.column) || 0)
        };
        var severity = item && item.type === "error" ? 1 : item && item.type === "warning" ? 2 : 3;
        return {
            range: {
                start: start,
                end: {
                    line: start.line,
                    character: start.character + 1
                }
            },
            severity: severity,
            source: item && item.source || DIAGNOSTIC_SOURCE,
            message: String(item && item.text || ""),
            code: item && item.raw || item && item.type || ""
        };
    }

    function dedupeCompletions(completions) {
        var seen = Object.create(null);
        return (completions || []).filter(function(item) {
            var key = item && (item.caption || item.value || item.snippet) || "";
            if (!key || seen[key]) {
                return false;
            }
            seen[key] = true;
            return true;
        });
    }

    function createClient(config) {
        config = config || {};
        var editor = config.editor || null;
        var session = config.session || null;
        var options = {};
        var state = stateFromOptions(options);
        var attached = false;
        var validationTimer = null;
        var lastAnnotations = [];
        var tsService = null;

        function getStaticCompleter() {
            if (typeof config.getStaticCompleter === "function") {
                return config.getStaticCompleter();
            }
            if (global.AutoJsAceCompleter && global.AutoJsAceCompleter.getActiveCompleter) {
                return global.AutoJsAceCompleter.getActiveCompleter();
            }
            return null;
        }

        function getTsService() {
            if (!tsService && hasTsLanguageService()) {
                tsService = global.AutoJsAceTsLanguageService.create({
                    notifyError: config.notifyError,
                    checkJs: options && options.checkJs === true,
                    documentUri: state.documentUri
                });
            }
            return tsService;
        }

        function warmUp(callback) {
            callback = typeof callback === "function" ? callback : noop;
            if (!state.enabled) {
                callback(false, getState());
                return false;
            }
            ensureTsLanguageServiceLoaded(config, function(ok) {
                if (ok) {
                    getTsService();
                    applyTsProviderState();
                    scheduleDiagnostics();
                }
                callback(!!ok, getState());
            });
            return true;
        }

        function getTsServiceState(allowCreate) {
            var service = allowCreate ? getTsService() : tsService;
            if (!service || typeof service.getState !== "function") {
                return { ready: false, reason: "typescript language service unavailable" };
            }
            return service.getState();
        }

        function tsServiceReady() {
            var tsState = getTsServiceState(true);
            return !!(tsState && tsState.ready);
        }

        function applyTsProviderState() {
            if (!state.enabled || !tsServiceReady()) {
                return;
            }
            state.completionProvider = COMPLETION_PROVIDER_TYPESCRIPT;
            state.hoverProvider = HOVER_PROVIDER_TYPESCRIPT;
            state.diagnosticProvider = DIAGNOSTIC_PROVIDER_TYPESCRIPT;
            state.signatureProvider = SIGNATURE_PROVIDER_TYPESCRIPT;
            state.localServiceReady = true;
        }

        function setSessionAnnotations(annotations) {
            lastAnnotations = copyArray(annotations);
            if (session && typeof session.setAnnotations === "function") {
                session.setAnnotations(lastAnnotations);
            }
            return lastAnnotations;
        }

        function clearDiagnostics() {
            if (lastAnnotations.length > 0) {
                setSessionAnnotations([]);
            }
        }

        function getDiagnostics() {
            return lastAnnotations.map(annotationToDiagnostic);
        }

        function validateNow() {
            if (!state.enabled) {
                clearDiagnostics();
                return [];
            }
            var text = textFromSession(session);
            if (isJsonDocumentUri(state.documentUri)) {
                setSessionAnnotations(jsonSyntaxDiagnostics(text));
                return getDiagnostics();
            }
            var service = getTsService();
            if (service && typeof service.getDiagnostics === "function") {
                var tsAnnotations = service.getDiagnostics(session);
                if (tsAnnotations) {
                    setSessionAnnotations(tsAnnotations);
                    return getDiagnostics();
                }
            }
            var annotations = runJshintDiagnostics(text, getStaticCompleter, config);
            if (annotations === null) {
                ensureJshintLoaded(config, function(lint) {
                    if (lint && state.enabled) {
                        validateNow();
                    }
                });
                annotations = localSyntaxDiagnostics(text);
            }
            setSessionAnnotations(annotations);
            return getDiagnostics();
        }

        function scheduleDiagnostics() {
            if (validationTimer !== null) {
                timerClear(validationTimer);
                validationTimer = null;
            }
            if (!state.enabled) {
                clearDiagnostics();
                return;
            }
            validationTimer = timerSet(function() {
                validationTimer = null;
                validateNow();
            }, VALIDATION_DELAY_MS);
        }

        function attachDiagnostics() {
            if (attached || !session || typeof session.on !== "function") {
                scheduleDiagnostics();
                return;
            }
            session.on("change", scheduleDiagnostics);
            attached = true;
            scheduleDiagnostics();
        }

        function detachDiagnostics() {
            if (validationTimer !== null) {
                timerClear(validationTimer);
                validationTimer = null;
            }
            if (attached && session && typeof session.off === "function") {
                session.off("change", scheduleDiagnostics);
            }
            attached = false;
        }

        function refresh() {
            var rawOptions = typeof config.getOptions === "function" ? config.getOptions() : "{}";
            options = parseOptions(rawOptions, config);
            state = stateFromOptions(options);
            if (tsService && typeof tsService.setDocumentUri === "function") {
                tsService.setDocumentUri(state.documentUri);
            }
            applyTsProviderState();
            if (state.enabled) {
                attachDiagnostics();
            } else {
                detachDiagnostics();
                clearDiagnostics();
            }
            return getState();
        }

        function getState() {
            return {
                enabled: state.enabled,
                manager: state.manager,
                state: state.state,
                transport: state.transport,
                serverUri: state.serverUri,
                rootUri: state.rootUri,
                documentUri: state.documentUri,
                libraryUris: copyArray(state.libraryUris),
                fallback: state.fallback,
                startSupported: state.startSupported,
                serverAvailable: state.serverAvailable,
                serverReady: state.serverReady,
                localServiceReady: state.localServiceReady,
                reason: state.reason,
                completionProvider: state.completionProvider,
                hoverProvider: state.hoverProvider,
                diagnosticProvider: state.diagnosticProvider,
                signatureProvider: state.signatureProvider,
                tsService: getTsServiceState(state.enabled || !!tsService),
                diagnosticsReady: state.enabled && (isJsonDocumentUri(state.documentUri) || getJshint() !== null || lastAnnotations.length > 0),
                diagnosticsCount: lastAnnotations.length,
                features: copyArray(state.features)
            };
        }

        function getStaticCompletions(activeEditor, activeSession, pos, prefix, callback) {
            var completer = getStaticCompleter();
            if (!completer || typeof completer.getCompletions !== "function") {
                callback(null, []);
                return;
            }
            completer.getCompletions(
                activeEditor || editor,
                activeSession || session,
                normalizePosition(pos),
                prefix || "",
                callback
            );
        }

        function getCompletions(activeEditor, activeSession, pos, prefix, callback) {
            callback = typeof callback === "function" ? callback : noop;
            try {
                var service = state.enabled && getTsService();
                if (service && typeof service.getCompletions === "function") {
                    var tsCompletions = [];
                    var tsError = null;
                    service.getCompletions(activeSession || session, normalizePosition(pos), prefix || "", function(error, result) {
                        tsError = error || null;
                        tsCompletions = result || [];
                    });
                    if (tsError) {
                        notify(config, "ACE TS completion failed: " + tsError, tsError);
                    } else if (tsCompletions.length > 0) {
                        getStaticCompletions(activeEditor, activeSession, pos, prefix, function(error, staticCompletions) {
                            callback(error, dedupeCompletions(tsCompletions.concat(staticCompletions || [])));
                        });
                        return;
                    }
                }
                getStaticCompletions(activeEditor, activeSession, pos, prefix, callback);
            } catch (error) {
                notify(config, "ACE LSP completion failed: " + error, error);
                callback(error, []);
            }
        }

        function getHover(pos) {
            try {
                var completer = getStaticCompleter();
                if (completer && typeof completer.getHover === "function") {
                    var indexedHover = completer.getHover(session, normalizePosition(pos));
                    if (indexedHover) {
                        return indexedHover;
                    }
                }
                var service = state.enabled && getTsService();
                if (service && typeof service.getHover === "function") {
                    var tsHover = service.getHover(session, normalizePosition(pos));
                    if (tsHover) {
                        return tsHover;
                    }
                }
                return null;
            } catch (error) {
                notify(config, "ACE LSP hover failed: " + error, error);
                return null;
            }
        }

        function getSignatureHelp(pos) {
            try {
                var completer = getStaticCompleter();
                if (completer && typeof completer.getSignatureHelp === "function") {
                    var indexedHelp = completer.getSignatureHelp(session, normalizePosition(pos));
                    if (indexedHelp) {
                        return indexedHelp;
                    }
                }
                var service = state.enabled && getTsService();
                if (service && typeof service.getSignatureHelp === "function") {
                    var tsHelp = service.getSignatureHelp(session, normalizePosition(pos));
                    if (tsHelp) {
                        return tsHelp;
                    }
                }
                if (!global.AutoJsAceSignatureHelp ||
                    typeof global.AutoJsAceSignatureHelp.findSignatureHelp !== "function") {
                    return null;
                }
                return global.AutoJsAceSignatureHelp.findSignatureHelp(session, normalizePosition(pos));
            } catch (error) {
                notify(config, "ACE LSP signature help failed: " + error, error);
                return null;
            }
        }

        function destroy() {
            detachDiagnostics();
            clearDiagnostics();
        }

        refresh();

        return {
            refresh: refresh,
            warmUp: warmUp,
            getState: getState,
            getCompletions: getCompletions,
            getHover: getHover,
            getSignatureHelp: getSignatureHelp,
            getDiagnostics: getDiagnostics,
            validateNow: validateNow,
            destroy: destroy,
            detach: destroy,
            positionFromIndex: positionFromIndex
        };
    }

    global.AutoJsAceLspClient = {
        install: createClient,
        createClient: createClient
    };
})(window);
