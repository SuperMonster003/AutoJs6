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
    var DEFAULT_MAX_DOCUMENT_LENGTH = 512 * 1024;
    var MAX_COMPLETION_ITEMS = 300;
    var TS_LOAD_MAX_ATTEMPTS = 2;
    var TS_LOAD_RETRY_DELAY_MS = 750;
    var TS_LOAD_TIMEOUT_MS = 5000;
    var TS_SERVICE_INIT_MAX_ATTEMPTS = 2;
    var SEMANTIC_SLOW_OPERATION_LIMIT_MS = 1000;
    var SEMANTIC_SLOW_OPERATION_MAX_COUNT = 1;
    var JSHINT_SCRIPT_SRC = "./src-min-noconflict/worker-javascript.js";
    var TS_RUNTIME_SCRIPT_SRC = "./autojs6/typescript/typescriptServices.js";
    var TS_SERVICE_SCRIPT_SRC = "./autojs6/autojs6_ts_language_service.js";

    var jshintLoadState = "idle";
    var jshintCallbacks = [];
    var tsLoadState = "idle";
    var tsLoadCallbacks = [];
    var tsLoadAttempts = 0;

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

    function positiveInteger(value, fallback) {
        value = Number(value);
        return isFinite(value) && value > 0 ? Math.floor(value) : fallback;
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
            localServiceReady: false,
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
            features: copyArray(options && options.features),
            maxDocumentLength: positiveInteger(
                options && options.maxDocumentLength,
                DEFAULT_MAX_DOCUMENT_LENGTH
            ),
            documentLength: 0,
            semanticServiceSuppressed: false,
            semanticServiceReason: "",
            lastSemanticOperation: "",
            lastSemanticDurationMs: 0
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

    function hasTsRuntime() {
        return !!(global.ts && typeof global.ts.createLanguageService === "function");
    }

    function appendScript(src, onload, onerror) {
        if (!global.document || !global.document.createElement) {
            onerror("document unavailable");
            return;
        }
        var script = null;
        var settled = false;
        var timeoutHandle = null;

        function finish(callback, value) {
            if (settled) {
                return;
            }
            settled = true;
            if (timeoutHandle !== null) {
                timerClear(timeoutHandle);
                timeoutHandle = null;
            }
            if (script) {
                script.onload = null;
                script.onerror = null;
            }
            callback(value);
        }

        try {
            script = global.document.createElement("script");
            var parent = global.document.head || global.document.body || global.document.documentElement;
            if (!parent || !parent.appendChild) {
                finish(onerror, "document head unavailable");
                return;
            }
            script.src = src;
            script.async = true;
            script.onload = function() {
                finish(onload);
            };
            script.onerror = function(error) {
                finish(onerror, error || (src + " unavailable"));
            };
            var timeoutFn = global.setTimeout || (typeof setTimeout === "function" ? setTimeout : null);
            if (timeoutFn) {
                timeoutHandle = timeoutFn(function() {
                    if (script.parentNode && typeof script.parentNode.removeChild === "function") {
                        script.parentNode.removeChild(script);
                    }
                    finish(onerror, src + " timed out after " + TS_LOAD_TIMEOUT_MS + " ms");
                }, TS_LOAD_TIMEOUT_MS);
            }
            parent.appendChild(script);
        } catch (error) {
            finish(onerror, error);
        }
    }

    function ensureTsLanguageServiceLoaded(config, callback) {
        callback = typeof callback === "function" ? callback : noop;
        if (hasTsLanguageService()) {
            tsLoadState = "loaded";
            callback(true);
            return true;
        }
        if (tsLoadState === "failed" && tsLoadAttempts >= TS_LOAD_MAX_ATTEMPTS) {
            callback(false);
            return false;
        }
        if (tsLoadState === "failed") {
            tsLoadState = "idle";
        }
        tsLoadCallbacks.push(callback);
        if (tsLoadState === "loading") {
            return false;
        }
        tsLoadState = "loading";
        tsLoadAttempts++;

        function loadServiceScript() {
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
        }

        if (hasTsRuntime()) {
            loadServiceScript();
        } else {
            appendScript(TS_RUNTIME_SCRIPT_SRC, loadServiceScript, function(error) {
                tsLoadState = "failed";
                notify(config, "ACE TypeScript runtime script unavailable", error);
                flushTsLoadCallbacks(false);
            });
        }
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
        }).slice(0, MAX_COMPLETION_ITEMS);
    }

    function createClient(config) {
        config = config || {};
        var editor = config.editor || null;
        var session = config.session || null;
        var options = {};
        var state = stateFromOptions(options);
        var attached = false;
        var destroyed = false;
        var hasRefreshed = false;
        var configurationRevision = 0;
        var validationTimer = null;
        var tsRetryTimer = null;
        var lastAnnotations = [];
        var tsService = null;
        var tsServiceInitAttempts = 0;
        var documentLengthKnown = false;
        var semanticCircuitOpen = false;
        var consecutiveSlowSemanticOperations = 0;

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
            if (destroyed || !state.enabled) {
                return null;
            }
            if (!tsService && hasTsLanguageService()) {
                tsServiceInitAttempts++;
                tsService = global.AutoJsAceTsLanguageService.create({
                    notifyError: config.notifyError,
                    checkJs: options && options.checkJs === true,
                    completionLimit: MAX_COMPLETION_ITEMS,
                    documentUri: state.documentUri
                });
            }
            return tsService;
        }

        function disposeTsService() {
            var service = tsService;
            tsService = null;
            if (service && typeof service.dispose === "function") {
                try {
                    service.dispose();
                } catch (error) {
                    notify(config, "ACE TS language service disposal failed: " + error, error);
                }
            }
        }

        function cancelTsRetry() {
            if (tsRetryTimer !== null) {
                timerClear(tsRetryTimer);
                tsRetryTimer = null;
            }
        }

        function resetProviderState() {
            state.localServiceReady = false;
            if (!state.enabled) {
                state.completionProvider = COMPLETION_PROVIDER_STATIC;
                state.hoverProvider = HOVER_PROVIDER_STATIC;
                state.diagnosticProvider = DIAGNOSTIC_PROVIDER_DISABLED;
                state.signatureProvider = SIGNATURE_PROVIDER_STATIC_LOCAL;
                return;
            }
            state.completionProvider = options.completionProvider || COMPLETION_PROVIDER_LOCAL_INDEX;
            state.hoverProvider = options.hoverProvider || HOVER_PROVIDER_LOCAL_INDEX;
            state.diagnosticProvider = options.diagnosticProvider || DIAGNOSTIC_PROVIDER_ACE_JSHINT;
            state.signatureProvider = options.signatureProvider || SIGNATURE_PROVIDER_STATIC_LOCAL;
        }

        function documentLengthFromSession(activeSession) {
            var target = activeSession || session;
            if (target === session && documentLengthKnown) {
                return state.documentLength;
            }
            try {
                if (target && typeof target.getValue === "function") {
                    var length = String(target.getValue() || "").length;
                    if (target === session) {
                        state.documentLength = length;
                        documentLengthKnown = true;
                    }
                    return length;
                }
                return textFromSession(target).length;
            } catch (ignore) {
                return 0;
            }
        }

        function updateSemanticState(activeSession, knownLength) {
            var target = activeSession || session;
            var length = typeof knownLength === "number" ? knownLength : documentLengthFromSession(target);
            if (target === session && typeof knownLength === "number") {
                state.documentLength = knownLength;
                documentLengthKnown = true;
            }
            var tooLarge = state.enabled && state.maxDocumentLength > 0 && length > state.maxDocumentLength;
            var unsafeLine = !!(target && target.$autojs6LongLineSafetyMode);
            state.documentLength = length;
            state.semanticServiceSuppressed = !!(unsafeLine || tooLarge || semanticCircuitOpen);
            state.semanticServiceReason = unsafeLine ?
                "line-too-long" :
                tooLarge ?
                "document-too-large" :
                semanticCircuitOpen ? "slow-operation-circuit-open" : "";
            if (state.semanticServiceSuppressed) {
                resetProviderState();
                clearDiagnostics();
            }
            return state.enabled && !state.semanticServiceSuppressed;
        }

        function getTsServiceState() {
            if (!tsService || typeof tsService.getState !== "function") {
                return {
                    ready: false,
                    reason: state.semanticServiceReason || "typescript language service unavailable"
                };
            }
            return tsService.getState();
        }

        function tsServiceReady() {
            var tsState = getTsServiceState();
            return !!(tsState && tsState.ready);
        }

        function applyTsProviderState() {
            if (!state.enabled || state.semanticServiceSuppressed || !tsServiceReady()) {
                return false;
            }
            state.completionProvider = COMPLETION_PROVIDER_TYPESCRIPT;
            state.hoverProvider = HOVER_PROVIDER_TYPESCRIPT;
            state.diagnosticProvider = DIAGNOSTIC_PROVIDER_TYPESCRIPT;
            state.signatureProvider = SIGNATURE_PROVIDER_TYPESCRIPT;
            state.localServiceReady = true;
            return true;
        }

        function openSemanticCircuit(operation, durationMs) {
            if (semanticCircuitOpen) {
                return;
            }
            semanticCircuitOpen = true;
            updateSemanticState(session);
            disposeTsService();
            notify(
                config,
                "ACE TypeScript language service switched to static fallback after slow " +
                    operation + " (" + durationMs + " ms)"
            );
        }

        function recordSemanticOperation(startedAt, operation, circuitEligible) {
            var durationMs = Math.max(0, Date.now() - startedAt);
            state.lastSemanticOperation = operation;
            state.lastSemanticDurationMs = durationMs;
            if (circuitEligible !== false && durationMs >= SEMANTIC_SLOW_OPERATION_LIMIT_MS) {
                consecutiveSlowSemanticOperations++;
                if (consecutiveSlowSemanticOperations >= SEMANTIC_SLOW_OPERATION_MAX_COUNT) {
                    openSemanticCircuit(operation, durationMs);
                }
            } else if (circuitEligible !== false) {
                consecutiveSlowSemanticOperations = 0;
            }
            return durationMs;
        }

        function scheduleTsLoadRetry(revision) {
            if (destroyed || !state.enabled || revision !== configurationRevision ||
                tsLoadAttempts >= TS_LOAD_MAX_ATTEMPTS) {
                return;
            }
            cancelTsRetry();
            tsRetryTimer = timerSet(function() {
                tsRetryTimer = null;
                if (!destroyed && state.enabled && revision === configurationRevision) {
                    warmUp();
                }
            }, TS_LOAD_RETRY_DELAY_MS);
        }

        function scheduleTsServiceRetry(revision) {
            if (destroyed || !state.enabled || revision !== configurationRevision ||
                tsServiceInitAttempts >= TS_SERVICE_INIT_MAX_ATTEMPTS) {
                return;
            }
            cancelTsRetry();
            tsRetryTimer = timerSet(function() {
                tsRetryTimer = null;
                if (!destroyed && state.enabled && revision === configurationRevision) {
                    disposeTsService();
                    warmUp();
                }
            }, TS_LOAD_RETRY_DELAY_MS);
        }

        function warmUp(callback) {
            callback = typeof callback === "function" ? callback : noop;
            if (destroyed || !state.enabled || !updateSemanticState(session)) {
                callback(false, getState());
                return false;
            }
            var revision = configurationRevision;
            ensureTsLanguageServiceLoaded(config, function(ok) {
                if (destroyed || !state.enabled || revision !== configurationRevision) {
                    callback(false, getState());
                    return;
                }
                if (!ok) {
                    scheduleTsLoadRetry(revision);
                    callback(false, getState());
                    return;
                }
                var startedAt = Date.now();
                getTsService();
                var ready = applyTsProviderState();
                recordSemanticOperation(startedAt, "initialize", false);
                if (ready && !semanticCircuitOpen) {
                    scheduleDiagnostics();
                } else if (!semanticCircuitOpen) {
                    scheduleTsServiceRetry(revision);
                }
                callback(ready && !semanticCircuitOpen, getState());
            });
            return true;
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
            if (destroyed || !state.enabled) {
                clearDiagnostics();
                return [];
            }
            var text = textFromSession(session);
            if (!updateSemanticState(session, text.length)) {
                clearDiagnostics();
                return [];
            }
            if (isJsonDocumentUri(state.documentUri)) {
                setSessionAnnotations(jsonSyntaxDiagnostics(text));
                return getDiagnostics();
            }
            var startedAt = Date.now();
            var service = getTsService();
            if (service && typeof service.getDiagnostics === "function") {
                var tsAnnotations = service.getDiagnostics(session, text);
                recordSemanticOperation(startedAt, "diagnostics");
                if (tsAnnotations && !semanticCircuitOpen) {
                    applyTsProviderState();
                    setSessionAnnotations(tsAnnotations);
                    return getDiagnostics();
                }
            }
            var annotations = runJshintDiagnostics(text, getStaticCompleter, config);
            if (annotations === null) {
                ensureJshintLoaded(config, function(lint) {
                    if (lint && state.enabled && !destroyed) {
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
            if (destroyed || !state.enabled || !updateSemanticState(session)) {
                clearDiagnostics();
                return;
            }
            validationTimer = timerSet(function() {
                validationTimer = null;
                if (!tsService && tsLoadState !== "loading") {
                    warmUp();
                }
                validateNow();
            }, VALIDATION_DELAY_MS);
        }

        function changedTextLength(delta) {
            if (!delta || !Array.isArray(delta.lines) || delta.lines.length === 0) {
                return null;
            }
            var newLineLength = 1;
            try {
                var document = session && typeof session.getDocument === "function" ?
                    session.getDocument() : session && session.doc;
                if (document && typeof document.getNewLineCharacter === "function") {
                    newLineLength = Math.max(1, String(document.getNewLineCharacter() || "\n").length);
                }
            } catch (ignore) {
                newLineLength = 1;
            }
            return delta.lines.reduce(function(total, line) {
                return total + String(line || "").length;
            }, Math.max(0, delta.lines.length - 1) * newLineLength);
        }

        function onSessionChange(delta) {
            var changedLength = changedTextLength(delta);
            if (documentLengthKnown && changedLength !== null) {
                if (delta.action === "insert") {
                    state.documentLength += changedLength;
                } else if (delta.action === "remove") {
                    state.documentLength = Math.max(0, state.documentLength - changedLength);
                } else {
                    documentLengthKnown = false;
                }
            } else {
                documentLengthKnown = false;
            }
            if (documentLengthKnown) {
                updateSemanticState(session, state.documentLength);
            }
            scheduleDiagnostics();
        }

        function attachDiagnostics() {
            if (attached || !session || typeof session.on !== "function") {
                scheduleDiagnostics();
                return;
            }
            session.on("change", onSessionChange);
            attached = true;
            scheduleDiagnostics();
        }

        function detachDiagnostics() {
            if (validationTimer !== null) {
                timerClear(validationTimer);
                validationTimer = null;
            }
            if (attached && session && typeof session.off === "function") {
                session.off("change", onSessionChange);
            }
            attached = false;
        }

        function refresh() {
            if (destroyed) {
                return getState();
            }
            var shouldWarmUp = hasRefreshed;
            configurationRevision++;
            cancelTsRetry();
            semanticCircuitOpen = false;
            consecutiveSlowSemanticOperations = 0;
            var rawOptions = typeof config.getOptions === "function" ? config.getOptions() : "{}";
            options = parseOptions(rawOptions, config);
            state = stateFromOptions(options);
            documentLengthKnown = false;
            updateSemanticState(session);
            if (state.enabled) {
                if (tsService && typeof tsService.setDocumentUri === "function") {
                    tsService.setDocumentUri(state.documentUri);
                }
                applyTsProviderState();
                attachDiagnostics();
                if (shouldWarmUp && !state.semanticServiceSuppressed && !tsServiceReady()) {
                    var revision = configurationRevision;
                    tsRetryTimer = timerSet(function() {
                        tsRetryTimer = null;
                        if (!destroyed && state.enabled && revision === configurationRevision) {
                            warmUp();
                        }
                    }, 0);
                }
            } else {
                detachDiagnostics();
                clearDiagnostics();
                disposeTsService();
                tsServiceInitAttempts = 0;
            }
            hasRefreshed = true;
            return getState();
        }

        function getState() {
            if (!destroyed && state.enabled) {
                updateSemanticState(session);
                applyTsProviderState();
            }
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
                tsLoader: {
                    state: tsLoadState,
                    attempts: tsLoadAttempts,
                    maxAttempts: TS_LOAD_MAX_ATTEMPTS
                },
                tsService: getTsServiceState(),
                maxDocumentLength: state.maxDocumentLength,
                documentLength: state.documentLength,
                semanticServiceSuppressed: state.semanticServiceSuppressed,
                semanticServiceReason: state.semanticServiceReason,
                lastSemanticOperation: state.lastSemanticOperation,
                lastSemanticDurationMs: state.lastSemanticDurationMs,
                serviceStatus: !state.enabled ? "disabled" :
                    state.semanticServiceSuppressed ? "degraded" :
                    state.localServiceReady ? "ready" : "configured",
                diagnosticsReady: state.enabled && !state.semanticServiceSuppressed &&
                    (isJsonDocumentUri(state.documentUri) || state.localServiceReady ||
                        getJshint() !== null || lastAnnotations.length > 0),
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
            var targetSession = activeSession || session;
            try {
                var text = textFromSession(targetSession);
                var semanticAllowed = updateSemanticState(targetSession, text.length);
                if (isJsonDocumentUri(state.documentUri)) {
                    callback(null, []);
                    return;
                }
                if (!semanticAllowed) {
                    getStaticCompletions(activeEditor, targetSession, pos, prefix, callback);
                    return;
                }
                var service = getTsService();
                if (service && typeof service.getCompletions === "function") {
                    var tsCompletions = [];
                    var tsError = null;
                    var startedAt = Date.now();
                    service.getCompletions(targetSession, normalizePosition(pos), prefix || "", function(error, result) {
                        tsError = error || null;
                        tsCompletions = result || [];
                    }, text);
                    recordSemanticOperation(startedAt, "completion");
                    if (tsError) {
                        notify(config, "ACE TS completion failed: " + tsError, tsError);
                    } else if (tsCompletions.length > 0 && !semanticCircuitOpen) {
                        applyTsProviderState();
                        callback(null, dedupeCompletions(tsCompletions));
                        return;
                    }
                } else if (state.enabled && tsLoadState !== "loading") {
                    warmUp();
                }
                getStaticCompletions(activeEditor, targetSession, pos, prefix, callback);
            } catch (error) {
                notify(config, "ACE LSP completion failed: " + error, error);
                getStaticCompletions(activeEditor, targetSession, pos, prefix, callback);
            }
        }

        function getHover(pos) {
            try {
                var text = textFromSession(session);
                if (isJsonDocumentUri(state.documentUri)) {
                    updateSemanticState(session, text.length);
                    return null;
                }
                if (updateSemanticState(session, text.length)) {
                    var service = getTsService();
                    if (service && typeof service.getHover === "function") {
                        var startedAt = Date.now();
                        var tsHover = service.getHover(session, normalizePosition(pos), text);
                        recordSemanticOperation(startedAt, "hover");
                        if (tsHover && !semanticCircuitOpen) {
                            applyTsProviderState();
                            return tsHover;
                        }
                    } else if (state.enabled && tsLoadState !== "loading") {
                        warmUp();
                    }
                }
                var completer = getStaticCompleter();
                if (completer && typeof completer.getHover === "function") {
                    return completer.getHover(session, normalizePosition(pos));
                }
                return null;
            } catch (error) {
                notify(config, "ACE LSP hover failed: " + error, error);
                return null;
            }
        }

        function getSignatureHelp(pos) {
            try {
                var text = textFromSession(session);
                if (isJsonDocumentUri(state.documentUri)) {
                    updateSemanticState(session, text.length);
                    return null;
                }
                if (updateSemanticState(session, text.length)) {
                    var service = getTsService();
                    if (service && typeof service.getSignatureHelp === "function") {
                        var startedAt = Date.now();
                        var tsHelp = service.getSignatureHelp(session, normalizePosition(pos), text);
                        recordSemanticOperation(startedAt, "signatureHelp");
                        if (tsHelp && !semanticCircuitOpen) {
                            applyTsProviderState();
                            return tsHelp;
                        }
                    } else if (state.enabled && tsLoadState !== "loading") {
                        warmUp();
                    }
                }
                var completer = getStaticCompleter();
                if (completer && typeof completer.getSignatureHelp === "function") {
                    var indexedHelp = completer.getSignatureHelp(session, normalizePosition(pos));
                    if (indexedHelp) {
                        return indexedHelp;
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
            if (destroyed) {
                return;
            }
            detachDiagnostics();
            clearDiagnostics();
            cancelTsRetry();
            configurationRevision++;
            destroyed = true;
            disposeTsService();
            state.enabled = false;
            state.state = STATE_DISABLED;
            state.semanticServiceSuppressed = false;
            state.semanticServiceReason = "destroyed";
            resetProviderState();
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
