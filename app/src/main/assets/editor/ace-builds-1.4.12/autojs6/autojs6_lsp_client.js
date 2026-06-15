(function(global) {
    "use strict";

    var FALLBACK_STATIC_COMPLETION = "static-completion";
    var STATE_DISABLED = "disabled";
    var COMPLETION_PROVIDER_STATIC = "static-fallback";
    var HOVER_PROVIDER_STATIC = "static-fallback";

    function noop() {
    }

    function normalizePosition(pos) {
        pos = pos || {};
        return {
            row: Math.max(0, Number(pos.row) || 0),
            column: Math.max(0, Number(pos.column) || 0)
        };
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

    function hasServer(options) {
        return !!(
            options &&
            options.enabled === true &&
            options.serverAvailable === true &&
            typeof options.serverUri === "string" &&
            options.serverUri.length > 0
        );
    }

    function stateFromOptions(options) {
        var serverReady = hasServer(options);
        return {
            enabled: options && options.enabled === true,
            manager: options && options.manager || "",
            state: options && options.state || STATE_DISABLED,
            transport: options && options.transport || "",
            serverUri: options && options.serverUri || null,
            rootUri: options && options.rootUri || "",
            documentUri: options && options.documentUri || "",
            libraryUris: options && options.libraryUris || [],
            fallback: options && options.fallback || FALLBACK_STATIC_COMPLETION,
            startSupported: !!(options && options.startSupported),
            serverAvailable: !!(options && options.serverAvailable),
            serverReady: serverReady,
            reason: options && options.reason || "",
            completionProvider: serverReady ? "server" : COMPLETION_PROVIDER_STATIC,
            hoverProvider: serverReady ? "server" : HOVER_PROVIDER_STATIC
        };
    }

    function createClient(config) {
        config = config || {};
        var editor = config.editor || null;
        var session = config.session || null;
        var options = {};
        var state = stateFromOptions(options);

        function getStaticCompleter() {
            if (typeof config.getStaticCompleter === "function") {
                return config.getStaticCompleter();
            }
            if (global.AutoJsAceCompleter && global.AutoJsAceCompleter.getActiveCompleter) {
                return global.AutoJsAceCompleter.getActiveCompleter();
            }
            return null;
        }

        function refresh() {
            var rawOptions = typeof config.getOptions === "function" ? config.getOptions() : "{}";
            options = parseOptions(rawOptions, config);
            state = stateFromOptions(options);
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
                libraryUris: state.libraryUris.slice ? state.libraryUris.slice(0) : [],
                fallback: state.fallback,
                startSupported: state.startSupported,
                serverAvailable: state.serverAvailable,
                serverReady: state.serverReady,
                reason: state.reason,
                completionProvider: state.completionProvider,
                hoverProvider: state.hoverProvider
            };
        }

        function getCompletions(activeEditor, activeSession, pos, prefix, callback) {
            callback = typeof callback === "function" ? callback : noop;
            try {
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
            } catch (error) {
                notify(config, "ACE LSP completion fallback failed: " + error, error);
                callback(error, []);
            }
        }

        function getHover(pos) {
            try {
                var completer = getStaticCompleter();
                if (!completer || typeof completer.getHover !== "function") {
                    return null;
                }
                return completer.getHover(session, normalizePosition(pos));
            } catch (error) {
                notify(config, "ACE LSP hover fallback failed: " + error, error);
                return null;
            }
        }

        refresh();

        return {
            refresh: refresh,
            getState: getState,
            getCompletions: getCompletions,
            getHover: getHover
        };
    }

    global.AutoJsAceLspClient = {
        install: createClient,
        createClient: createClient
    };
})(window);
