(function(global) {
    "use strict";

    var bridge = global.autojs || {};
    var editor = null;
    var session = null;
    var Range = null;
    var staticCompleter = null;
    var lspCompletionCompleter = null;
    var lspClient = null;
    var lastPublishedLspStateJson = "";
    var lspWarmUpHandle = null;
    var lspWarmUpUsesIdleCallback = false;
    var tooltipController = null;
    var signatureHelpController = null;
    var dirty = false;
    var suppressChange = false;
    var documentLongLineSafetyMode = false;
    var preferredWordWrapEnabled = false;
    var breakpoints = [];
    var lineNumbersEnabled = true;
    var breakpointMarkersEnabled = true;
    var foldMarkersEnabled = true;
    var gutterWidthMode = "dynamic";
    var debugMarker = null;
    var storedReplacement = "";
    var scrollNotifyTimer = null;
    var stateNotifyTimer = null;
    var stateNotifyEventName = "";
    var textNotifyTimer = null;
    var textNotifyIncludeText = false;
    var cursorNotifyTimer = null;
    var actionModeNotifyTimer = null;
    var memberCompletionRestartTimer = null;
    var fontMetricsRefreshTimer = null;
    var fontMetricsRefreshFrameScheduled = false;
    var fontMetricsReadySerial = 0;
    var bundledFontLoadPending = false;
    var bundledFontLoadSettled = false;
    var bundledFontLoadTimer = null;
    var lastActionModeSelectedText = "";
    var nativeActionModeActive = false;
    var selectionMenuSuppressed = false;
    var textMutationSelectionMenuSuppressUntil = 0;
    var lastStateNotifyAt = 0;
    var lastTextNotifyAt = 0;
    var lastCursorNotifyAt = 0;
    var resizeScheduled = false;
    var resizeFallbackTimer = null;
    var resizeReason = "";
    var cursorRevealSuppressUntil = 0;
    var firstPaintNotified = false;
    var firstPaintRequestStartedAt = 0;
    var firstPaintStableFrames = 0;
    var firstPaintLastSignature = "";
    var lastSelectionBrowseScrollAt = 0;
    var lastSelectionChangedAt = 0;
    var lastSelectionActionModeStartedAt = 0;
    var lastTouchClientX = -1;
    var lastTouchClientY = -1;
    var lastTouchAt = 0;
    var currentFontSizeSp = 14;
    var bridgePinchZoomActive = false;
    var bridgePinchZoomStartDistance = 0;
    var bridgePinchZoomBaseFontSizeSp = 14;
    var bridgePinchZoomCurrentFontSizeSp = 14;
    var bridgePinchZoomLastFocus = { x: 0, y: 0 };
    var bridgePinchZoomAnchor = null;
    var bridgePinchZoomSelectionRange = null;
    var bridgePinchPostTouchSuppressUntil = 0;
    var bridgePinchSelectionSuppressUntil = 0;
    var bridgePinchImeSuppressUntil = 0;
    var bridgePinchImeRestoreTimer = null;
    var bridgePinchImeElement = null;
    var bridgePinchImeElementWasReadOnly = false;
    var programmaticScrollUntil = 0;
    var foldTapCandidate = null;
    var gutterFoldClickSuppressUntil = 0;
    var imeTextInputSelectionWindow = {
        installed: false,
        element: null,
        value: "",
        start: -1,
        end: -1
    };
    var imeTextInputSelectionSyncing = false;
    var lastImeTextInputSelectionNotifyAt = 0;
    var imeTextInputSelectionSuppressUntil = 0;
    var imeTextInputSelectionSuppressReason = "";
    var BUNDLED_FONT_FAMILY = '"Iosevka", monospace';
    var SYSTEM_FONT_FAMILY = "monospace";
    var DEFAULT_FONT_FAMILY = BUNDLED_FONT_FAMILY;
    var fontDescriptorRequestSerial = 0;
    var installedFontLoads = Object.create(null);
    var activeInstalledFontFace = null;
    var pendingFontDescriptorLoad = null;
    var STATE_NOTIFY_THROTTLE_MS = 100;
    var TEXT_NOTIFY_THROTTLE_MS = 100;
    var CURSOR_NOTIFY_THROTTLE_MS = 50;
    var ACTION_MODE_NOTIFY_THROTTLE_MS = 0;
    var SELECTION_BROWSE_REVEAL_SUPPRESS_MS = 1600;
    var ACTION_MODE_SCROLL_SUPPRESS_GRACE_MS = 650;
    var TEXT_MUTATION_SELECTION_MENU_SUPPRESS_MS = 1200;
    var TOUCH_ANCHOR_MAX_AGE_MS = 2500;
    var FOLD_TAP_TOUCH_SLOP_SQUARED = 100;
    var IME_TEXT_INPUT_SELECTION_MAX_DELTA = 64;
    var IME_TEXT_INPUT_PROGRAMMATIC_SELECTION_SUPPRESS_MS = 800;
    var FIRST_PAINT_STABLE_FRAME_COUNT = 3;
    var FIRST_PAINT_MAX_WAIT_MS = 1800;
    var BUNDLED_FONT_LOAD_TIMEOUT_MS = 5000;
    var FONT_LOAD_TIMEOUT_MS = 30000;
    // ACE 1.4.12 does not apply Text.MAX_LINE_LENGTH to the first token in an
    // unwrapped line. Text mode emits the whole line as that first token, so a
    // wrongly decoded file can otherwise create hundreds of thousands of DOM
    // nodes and monopolize Android WebView during a whole-document Redo.
    var LONG_LINE_SAFETY_RENDER_TRIGGER = 16 * 1024;
    var LONG_LINE_SAFETY_RENDER_LIMIT = 4096;
    var BUNDLED_FONT_LOAD_PROBE = '14px "Iosevka"';
    var INSTALLED_FONT_URL_PREFIX = "https://appassets.androidplatform.net/autojs6-fonts/";
    var PINCH_CURSOR_REVEAL_SUPPRESS_MS = 3000;
    var PINCH_IME_SUPPRESS_MS = 1800;
    var PINCH_POST_TOUCH_SUPPRESS_MS = 650;
    var PINCH_CURSOR_RESTORE_MS = 700;
    var MIN_FONT_SIZE_SP = 1;
    var MAX_FONT_SIZE_SP = 96;
    var DARK_ACE_THEMES = {
        "ace/theme/ambiance": true,
        "ace/theme/chaos": true,
        "ace/theme/clouds_midnight": true,
        "ace/theme/cobalt": true,
        "ace/theme/dracula": true,
        "ace/theme/gob": true,
        "ace/theme/gruvbox": true,
        "ace/theme/idle_fingers": true,
        "ace/theme/kr_theme": true,
        "ace/theme/merbivore": true,
        "ace/theme/merbivore_soft": true,
        "ace/theme/mono_industrial": true,
        "ace/theme/monokai": true,
        "ace/theme/nord_dark": true,
        "ace/theme/pastel_on_dark": true,
        "ace/theme/solarized_dark": true,
        "ace/theme/terminal": true,
        "ace/theme/tomorrow_night": true,
        "ace/theme/tomorrow_night_blue": true,
        "ace/theme/tomorrow_night_bright": true,
        "ace/theme/tomorrow_night_eighties": true,
        "ace/theme/twilight": true,
        "ace/theme/vibrant_ink": true,
        "ace/theme/visual_studio_dark": true
    };

    function callBridge(name, args) {
        try {
            if (bridge && typeof bridge[name] === "function") {
                return bridge[name].apply(bridge, args || []);
            }
        } catch (error) {
            notifyError("Bridge call failed: " + name + ": " + error);
        }
        return null;
    }

    function callBridgeBoolean(name, defaultValue) {
        var value = callBridge(name);
        if (value === null || typeof value === "undefined") {
            return !!defaultValue;
        }
        return value === true || String(value).toLowerCase() === "true";
    }

    function callBridgeString(name, defaultValue) {
        var value = callBridge(name);
        if (value === null || typeof value === "undefined") {
            return String(defaultValue || "");
        }
        return String(value);
    }

    function notifyError(message) {
        try {
            if (bridge && typeof bridge.notifyError === "function") {
                bridge.notifyError(String(message || "Unknown ACE error"));
            }
        } catch (ignore) {
            // Keep the editor usable even if the native bridge is not ready.
        }
    }

    function notifyFatalError(payload) {
        payload = payload || {};
        if (!payload.message) {
            payload.message = "Unknown ACE fatal error";
        }
        try {
            callBridge("notifyEvent", ["fatalError", JSON.stringify(payload)]);
        } finally {
            notifyError(payload.message);
        }
    }

    function notifyCompletionError(message, error) {
        var payload = {
            message: String(message || "ACE completion error"),
            stack: error && error.stack ? String(error.stack) : ""
        };
        callBridge("notifyEvent", ["completionError", JSON.stringify(payload)]);
        notifyError(payload.message);
    }

    function notifyLspError(message, error) {
        var payload = {
            message: String(message || "ACE LSP error"),
            stack: error && error.stack ? String(error.stack) : ""
        };
        callBridge("notifyEvent", ["lspError", JSON.stringify(payload)]);
        notifyError(payload.message);
    }

    function notifyRecoverableError(source, error) {
        var payload = {
            message: String(source || "ACE recoverable error") + ": " + String(error && error.message ? error.message : error),
            source: String(source || ""),
            stack: error && error.stack ? String(error.stack) : ""
        };
        callBridge("notifyEvent", ["recoverableError", JSON.stringify(payload)]);
        notifyError(payload.message);
    }

    function applyThemeClass(theme) {
        var themeName = String(theme || "").toLowerCase();
        var dark = Object.prototype.hasOwnProperty.call(DARK_ACE_THEMES, themeName)
            ? DARK_ACE_THEMES[themeName]
            : themeName.indexOf("night") >= 0 || themeName.indexOf("dark") >= 0;
        document.documentElement.classList.toggle("ace-theme-dark", dark);
        document.body.classList.toggle("ace-theme-dark", dark);
    }

    function applyFontFamily(fontFamily) {
        var resolved = fontFamily || DEFAULT_FONT_FAMILY;
        if (document && document.documentElement && document.documentElement.style) {
            document.documentElement.style.setProperty("--autojs6-editor-font-family", resolved);
        }
        return resolved;
    }

    function normalizeFontDescriptor(value) {
        var descriptor = value;
        if (typeof descriptor === "string") {
            if (!descriptor.trim()) {
                throw new Error("Font descriptor is empty");
            }
            descriptor = JSON.parse(descriptor);
        }
        if (!descriptor || typeof descriptor !== "object" || Array.isArray(descriptor)) {
            throw new Error("Font descriptor must be a JSON object");
        }

        var source = String(descriptor.source || descriptor.type || "").trim().toLowerCase();
        if (source !== "system" && source !== "bundled" && source !== "installed") {
            throw new Error("Unsupported font descriptor source: " + source);
        }

        var normalized = {
            source: source,
            id: String(descriptor.id || "").trim(),
            family: String(descriptor.family || descriptor.fontFamily || "").trim(),
            url: String(descriptor.url || "").trim(),
            sha256: String(descriptor.sha256 || descriptor.digest || "").trim().toLowerCase(),
            format: String(descriptor.format || "woff2").trim().toLowerCase()
        };

        if (source === "system") {
            normalized.id = normalized.id || "system_monospace";
            normalized.family = "monospace";
            normalized.url = "";
            normalized.sha256 = "";
            return normalized;
        }

        if (source === "bundled") {
            normalized.id = normalized.id || "iosevka";
            if (normalized.id !== "iosevka") {
                throw new Error("Unsupported bundled font: " + normalized.id);
            }
            normalized.family = "Iosevka";
            normalized.url = "";
            normalized.sha256 = "";
            return normalized;
        }

        if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/.test(normalized.id)) {
            throw new Error("Installed font id is missing or invalid");
        }
        if (!normalized.url || normalized.url.length > 4096 || /[\u0000-\u001f\u007f]/.test(normalized.url)) {
            throw new Error("Installed font URL is missing or invalid");
        }
        if (normalized.format !== "woff2") {
            throw new Error("Unsupported installed font format: " + normalized.format);
        }
        if (!/^[a-f0-9]{64}$/.test(normalized.sha256)) {
            throw new Error("Installed font SHA-256 is invalid");
        }
        var expectedUrl = INSTALLED_FONT_URL_PREFIX + encodeURIComponent(normalized.id) + "/" + normalized.sha256 + ".woff2";
        if (normalized.url !== expectedUrl) {
            throw new Error("Installed font URL does not match its verified virtual asset route");
        }
        if (!normalized.family) {
            normalized.family = normalized.id;
        }
        return normalized;
    }

    function fontDescriptorInitialFamily(descriptor) {
        return descriptor && descriptor.source === "system" ? SYSTEM_FONT_FAMILY : DEFAULT_FONT_FAMILY;
    }

    function fontAliasHash(value) {
        var hash = 2166136261;
        var text = String(value || "");
        for (var i = 0; i < text.length; i += 1) {
            hash ^= text.charCodeAt(i);
            hash += (hash << 1) + (hash << 4) + (hash << 7) + (hash << 8) + (hash << 24);
        }
        return ("00000000" + (hash >>> 0).toString(16)).slice(-8);
    }

    function installedFontAlias(descriptor) {
        var safeId = descriptor.id.replace(/[^A-Za-z0-9_]/g, "_").slice(0, 48) || "font";
        var identity = [descriptor.id, descriptor.sha256, descriptor.url].join("\n");
        return "AutoJs6Font_" + safeId + "_" + fontAliasHash(identity);
    }

    function cssString(value) {
        return '"' + String(value || "")
            .replace(/\\/g, "\\\\")
            .replace(/"/g, '\\"') + '"';
    }

    function installedFontCacheKey(descriptor) {
        return [descriptor.id, descriptor.sha256, descriptor.url].join("\n");
    }

    function installedFontDecodeError(error) {
        var message = String(error && error.message ? error.message : error || "Installed font decoding failed");
        var wrapped = new Error(message);
        wrapped.autojs6InvalidateInstalledFontCache = true;
        return wrapped;
    }

    function loadInstalledFont(descriptor) {
        if (typeof global.FontFace !== "function" || !document.fonts || typeof document.fonts.add !== "function") {
            throw new Error("This WebView does not support dynamic FontFace loading");
        }

        var cacheKey = installedFontCacheKey(descriptor);
        var cached = installedFontLoads[cacheKey];
        if (cached) {
            return cached.promise;
        }

        var alias = installedFontAlias(descriptor);
        var source = "url(" + cssString(descriptor.url) + ') format("woff2")';
        var face = new global.FontFace(alias, source, {
            style: "normal",
            weight: "400"
        });
        var entry = {
            alias: alias,
            face: face,
            promise: null
        };
        installedFontLoads[cacheKey] = entry;
        try {
            var loadResult = face.load();
            entry.promise = new Promise(function(resolve, reject) {
                var settled = false;
                var timeout = global.setTimeout(function() {
                    if (settled) {
                        return;
                    }
                    settled = true;
                    if (installedFontLoads[cacheKey] === entry) {
                        delete installedFontLoads[cacheKey];
                    }
                    reject(new Error("Timed out while loading installed font"));
                }, FONT_LOAD_TIMEOUT_MS);

                Promise.resolve(loadResult).then(function() {
                    if (settled) {
                        return;
                    }
                    settled = true;
                    global.clearTimeout(timeout);
                    resolve(entry);
                }, function(error) {
                    if (settled) {
                        return;
                    }
                    settled = true;
                    global.clearTimeout(timeout);
                    if (installedFontLoads[cacheKey] === entry) {
                        delete installedFontLoads[cacheKey];
                    }
                    reject(installedFontDecodeError(error));
                });
            });
        } catch (error) {
            delete installedFontLoads[cacheKey];
            throw error;
        }
        return entry.promise;
    }

    function replaceActiveInstalledFontFace(nextFace) {
        if (
            activeInstalledFontFace &&
            activeInstalledFontFace !== nextFace &&
            document.fonts &&
            typeof document.fonts.delete === "function"
        ) {
            try {
                document.fonts.delete(activeInstalledFontFace);
            } catch (ignore) {
                // The loaded face can remain registered without affecting the selected family.
            }
        }
        activeInstalledFontFace = nextFace || null;
    }

    function ensureBundledFontLoaded() {
        if (bundledFontLoadSettled || bundledFontLoadPending) {
            return;
        }
        if (!document.fonts || typeof document.fonts.load !== "function") {
            bundledFontLoadSettled = true;
            return;
        }

        bundledFontLoadPending = true;
        var completed = false;
        var finish = function(error) {
            if (completed) {
                return;
            }
            completed = true;
            bundledFontLoadPending = false;
            bundledFontLoadSettled = true;
            if (bundledFontLoadTimer !== null) {
                global.clearTimeout(bundledFontLoadTimer);
                bundledFontLoadTimer = null;
            }
            if (error) {
                notifyRecoverableError("bundledFontLoad", error);
                return;
            }
            scheduleFontMetricsRefresh("bundled_font_ready");
        };

        var loadResult;
        try {
            loadResult = document.fonts.load(BUNDLED_FONT_LOAD_PROBE);
        } catch (error) {
            finish(error);
            return;
        }
        bundledFontLoadTimer = global.setTimeout(function() {
            finish(new Error("Timed out while loading bundled Iosevka font"));
        }, BUNDLED_FONT_LOAD_TIMEOUT_MS);
        Promise.resolve(loadResult).then(function() {
            finish(null);
        }, finish);
    }

    function applyFontFamilyAndRefresh(fontFamily, reason) {
        var resolved = applyFontFamily(fontFamily);
        if (editor && editor.setOption) {
            editor.setOption("fontFamily", resolved);
            forceRendererCoordinateRefresh(reason || "font_family_changed");
            scheduleFontMetricsRefresh(reason || "font_family_changed");
            scheduleResize(reason || "font_family_changed");
        }
        return resolved;
    }

    function notifyFontChanged(descriptor, cssFamily, alias) {
        callBridge("notifyEvent", [
            "fontChanged",
            JSON.stringify({
                id: descriptor.id,
                source: descriptor.source,
                family: descriptor.family,
                cssFamily: cssFamily,
                alias: alias || ""
            })
        ]);
    }

    function notifyFontLoadError(descriptor, error) {
        var message = String(error && error.message ? error.message : error || "Unknown font load error");
        callBridge("notifyEvent", [
            "fontLoadError",
            JSON.stringify({
                id: descriptor && descriptor.id ? descriptor.id : "",
                source: descriptor && descriptor.source ? descriptor.source : "",
                sha256: descriptor && descriptor.sha256 ? descriptor.sha256 : "",
                error: message,
                recoverable: true,
                invalidateInstalledCache: !!(
                    error && error.autojs6InvalidateInstalledFontCache === true
                )
            })
        ]);
        notifyRecoverableError("fontLoad", error || new Error(message));
    }

    function commitFontDescriptor(descriptor, entry) {
        var cssFamily;
        var alias = "";
        if (descriptor.source === "system") {
            replaceActiveInstalledFontFace(null);
            cssFamily = SYSTEM_FONT_FAMILY;
        } else if (descriptor.source === "bundled") {
            ensureBundledFontLoaded();
            replaceActiveInstalledFontFace(null);
            cssFamily = BUNDLED_FONT_FAMILY;
        } else {
            if (!entry || !entry.face || !entry.alias) {
                throw new Error("Installed font did not finish loading");
            }
            document.fonts.add(entry.face);
            replaceActiveInstalledFontFace(entry.face);
            alias = entry.alias;
            cssFamily = '"' + alias + '", ' + BUNDLED_FONT_FAMILY;
        }
        cssFamily = applyFontFamilyAndRefresh(cssFamily, "font_descriptor_changed");
        notifyStateChanged("fontFamilyChanged");
        notifyFontChanged(descriptor, cssFamily, alias);
        return true;
    }

    function applyFontLoadFallback(descriptor, error) {
        try {
            ensureBundledFontLoaded();
            replaceActiveInstalledFontFace(null);
            applyFontFamilyAndRefresh(BUNDLED_FONT_FAMILY, "font_load_fallback");
            notifyStateChanged("fontFamilyFallback");
        } catch (fallbackError) {
            notifyRecoverableError("fontFallback", fallbackError);
        }
        notifyFontLoadError(descriptor, error);
        return false;
    }

    function clearPendingFontDescriptorLoad(requestSerial) {
        if (pendingFontDescriptorLoad && pendingFontDescriptorLoad.requestSerial === requestSerial) {
            pendingFontDescriptorLoad = null;
        }
    }

    function fallbackWhilePendingFontLoadsForFirstPaint() {
        if (!pendingFontDescriptorLoad) {
            return false;
        }
        if (pendingFontDescriptorLoad.firstPaintFallbackApplied) {
            return false;
        }
        pendingFontDescriptorLoad.firstPaintFallbackApplied = true;
        try {
            ensureBundledFontLoaded();
            replaceActiveInstalledFontFace(null);
            applyFontFamilyAndRefresh(BUNDLED_FONT_FAMILY, "font_first_paint_fallback");
            notifyStateChanged("fontFamilyFirstPaintFallback");
        } catch (error) {
            notifyRecoverableError("fontFirstPaintFallback", error);
        }
        return true;
    }

    function setFontDescriptor(value) {
        var requestSerial = ++fontDescriptorRequestSerial;
        pendingFontDescriptorLoad = null;
        var descriptor;
        try {
            descriptor = normalizeFontDescriptor(value);
        } catch (error) {
            return Promise.resolve(applyFontLoadFallback(null, error));
        }

        if (descriptor.source !== "installed") {
            try {
                return Promise.resolve(commitFontDescriptor(descriptor, null));
            } catch (error) {
                return Promise.resolve(applyFontLoadFallback(descriptor, error));
            }
        }

        var loadPromise;
        try {
            loadPromise = loadInstalledFont(descriptor);
        } catch (error) {
            return Promise.resolve(applyFontLoadFallback(descriptor, error));
        }
        pendingFontDescriptorLoad = {
            requestSerial: requestSerial,
            descriptor: descriptor
        };
        return loadPromise.then(function(entry) {
            if (requestSerial !== fontDescriptorRequestSerial) {
                return false;
            }
            clearPendingFontDescriptorLoad(requestSerial);
            try {
                return commitFontDescriptor(descriptor, entry);
            } catch (error) {
                return applyFontLoadFallback(descriptor, error);
            }
        }, function(error) {
            if (requestSerial !== fontDescriptorRequestSerial) {
                return false;
            }
            clearPendingFontDescriptorLoad(requestSerial);
            return applyFontLoadFallback(descriptor, error);
        });
    }

    function applyFontLigaturesEnabled(enabled) {
        var enabledValue = enabled === true || String(enabled).toLowerCase() === "true";
        if (document && document.documentElement && document.documentElement.style) {
            document.documentElement.style.setProperty(
                "--autojs6-editor-font-ligatures",
                enabledValue ? "normal" : "none"
            );
            document.documentElement.style.setProperty(
                "--autojs6-editor-font-feature-settings",
                enabledValue ? "\"liga\" 1, \"calt\" 1" : "\"liga\" 0, \"calt\" 0"
            );
        }
        return enabledValue;
    }

    function applyFontStylesEnabled(enabled) {
        var enabledValue = enabled === true || String(enabled).toLowerCase() === "true";
        var disabled = !enabledValue;
        if (document && document.documentElement && document.documentElement.classList) {
            document.documentElement.classList.toggle("autojs6-font-styles-disabled", disabled);
        }
        if (document && document.body && document.body.classList) {
            document.body.classList.toggle("autojs6-font-styles-disabled", disabled);
        }
        setEditorContainerClass("autojs6-font-styles-disabled", disabled);
        return enabledValue;
    }

    function notifyFirstPaint() {
        if (firstPaintNotified) {
            return;
        }
        firstPaintNotified = true;
        document.body.classList.add("ace-ready");
        callBridge("notifyEvent", ["firstPaint", lightweightStateJson()]);
        warmUpLspAfterFirstPaint();
    }

    function warmUpLspAfterFirstPaint() {
        if (!lspClient || typeof lspClient.warmUp !== "function" || lspWarmUpHandle !== null) {
            return;
        }
        var run = function() {
            lspWarmUpHandle = null;
            lspWarmUpUsesIdleCallback = false;
            try {
                lspClient.warmUp(function(ok, state) {
                    publishLspState(state);
                });
            } catch (error) {
                notifyRecoverableError("lspWarmUp", error);
            }
        };
        if (typeof global.requestIdleCallback === "function") {
            lspWarmUpUsesIdleCallback = true;
            lspWarmUpHandle = global.requestIdleCallback(run, { timeout: 1500 });
        } else {
            lspWarmUpHandle = setTimeout(run, 250);
        }
    }

    function publishLspState(state) {
        if (!state && lspClient && typeof lspClient.getState === "function") {
            state = lspClient.getState();
        }
        if (state) {
            var json = JSON.stringify(state);
            if (json !== lastPublishedLspStateJson) {
                lastPublishedLspStateJson = json;
                callBridge("notifyEvent", ["lspStateChanged", json]);
            }
        }
        return state || null;
    }

    function refreshLspAndPublish() {
        return publishLspState(lspClient && lspClient.refresh ? lspClient.refresh() : null);
    }

    function getLspStateAndPublish() {
        return publishLspState(lspClient && lspClient.getState ? lspClient.getState() : null);
    }

    function destroyLspClient() {
        if (lspWarmUpHandle !== null) {
            if (lspWarmUpUsesIdleCallback && typeof global.cancelIdleCallback === "function") {
                global.cancelIdleCallback(lspWarmUpHandle);
            } else {
                clearTimeout(lspWarmUpHandle);
            }
            lspWarmUpHandle = null;
            lspWarmUpUsesIdleCallback = false;
        }
        if (lspClient && typeof lspClient.destroy === "function") {
            lspClient.destroy();
            publishLspState(lspClient.getState ? lspClient.getState() : null);
        }
    }

    function afterTwoFrames(callback) {
        var raf = global.requestAnimationFrame || function(run) { return setTimeout(run, 16); };
        raf(function() {
            raf(callback);
        });
    }

    function colorLuminance(color) {
        color = String(color || "").toLowerCase();
        if (!color || color === "transparent") {
            return 0;
        }
        var match = /rgba?\(\s*([0-9.]+)\s*,\s*([0-9.]+)\s*,\s*([0-9.]+)(?:\s*,\s*([0-9.]+))?\s*\)/.exec(color);
        if (!match) {
            return null;
        }
        var alpha = match[4] === undefined ? 1 : Number(match[4]);
        if (alpha === 0) {
            return 0;
        }
        var r = Number(match[1]) || 0;
        var g = Number(match[2]) || 0;
        var b = Number(match[3]) || 0;
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    function isDarkCssColor(color) {
        var luminance = colorLuminance(color);
        return luminance === null ? true : luminance < 150;
    }

    function computedStyleOf(node) {
        if (!node || !global.getComputedStyle) {
            return null;
        }
        try {
            return global.getComputedStyle(node);
        } catch (ignore) {
            return null;
        }
    }

    function isVisibleElement(node) {
        if (!node || !node.getBoundingClientRect) {
            return false;
        }
        var rect = node.getBoundingClientRect();
        return rect.width > 0 && rect.height > 0;
    }

    function isFirstPaintThemeReady(container, gutter) {
        if (!document || !document.body || !document.body.classList || !document.body.classList.contains("ace-theme-dark")) {
            return true;
        }
        var editorStyle = computedStyleOf(container);
        if (editorStyle && !isDarkCssColor(editorStyle.backgroundColor)) {
            return false;
        }
        if (!isVisibleElement(gutter)) {
            return true;
        }
        var gutterStyle = computedStyleOf(gutter);
        return !gutterStyle || isDarkCssColor(gutterStyle.backgroundColor);
    }

    function firstPaintReadinessSnapshot() {
        var container = editor && editor.container;
        var renderer = editor && editor.renderer;
        if (!container || !renderer || !container.querySelector || !container.getBoundingClientRect) {
            return { ready: false, signature: "missing-editor" };
        }
        var scroller = container.querySelector(".ace_scroller");
        var textLayer = container.querySelector(".ace_text-layer");
        var gutter = container.querySelector(".ace_gutter");
        var rect = container.getBoundingClientRect();
        var scrollerRect = scroller && scroller.getBoundingClientRect ? scroller.getBoundingClientRect() : { width: 0, height: 0, left: 0 };
        var gutterRect = gutter && gutter.getBoundingClientRect ? gutter.getBoundingClientRect() : { width: 0, height: 0 };
        var lineHeight = Number(renderer.lineHeight) || 0;
        var characterWidth = Number(renderer.characterWidth) || 0;
        var themeReady = isFirstPaintThemeReady(container, gutter);
        var ready = rect.width > 0 &&
            rect.height > 0 &&
            scrollerRect.width > 0 &&
            scrollerRect.height > 0 &&
            !!textLayer &&
            lineHeight > 0 &&
            characterWidth > 0 &&
            themeReady &&
            !pendingFontDescriptorLoad &&
            !bundledFontLoadPending;
        var containerStyle = computedStyleOf(container);
        var gutterStyle = computedStyleOf(gutter);
        return {
            ready: ready,
            signature: [
                Math.round(rect.width),
                Math.round(rect.height),
                Math.round(scrollerRect.left),
                Math.round(scrollerRect.width),
                Math.round(gutterRect.width),
                Math.round(lineHeight),
                Math.round(characterWidth),
                pendingFontDescriptorLoad ? "font-loading" : "font-ready",
                bundledFontLoadPending ? "bundled-font-loading" : "bundled-font-ready",
                container.className,
                containerStyle && containerStyle.backgroundColor || "",
                gutterStyle && gutterStyle.backgroundColor || ""
            ].join("|")
        };
    }

    function requestFirstPaintCheck(reason) {
        if (firstPaintNotified) {
            return;
        }
        var snapshot = firstPaintReadinessSnapshot();
        var now = Date.now();
        if (snapshot.ready && snapshot.signature === firstPaintLastSignature) {
            firstPaintStableFrames += 1;
        } else {
            firstPaintStableFrames = snapshot.ready ? 1 : 0;
            firstPaintLastSignature = snapshot.signature;
        }
        if (firstPaintStableFrames >= FIRST_PAINT_STABLE_FRAME_COUNT) {
            notifyFirstPaint();
            return;
        }
        if (firstPaintRequestStartedAt > 0 && now - firstPaintRequestStartedAt >= FIRST_PAINT_MAX_WAIT_MS) {
            fallbackWhilePendingFontLoadsForFirstPaint();
            if (!bundledFontLoadPending) {
                callBridge("notifyEvent", [
                    "firstPaintForced",
                    JSON.stringify({
                        reason: String(reason || ""),
                        signature: String(snapshot.signature || "")
                    })
                ]);
                notifyFirstPaint();
                return;
            }
        }
        var raf = global.requestAnimationFrame || function(run) { return setTimeout(run, 16); };
        raf(function() {
            requestFirstPaintCheck(reason);
        });
    }

    function requestFirstPaint(reason) {
        scheduleResize(reason || "first_paint");
        firstPaintRequestStartedAt = Date.now();
        firstPaintStableFrames = 0;
        firstPaintLastSignature = "";
        afterTwoFrames(function() {
            requestFirstPaintCheck(reason || "first_paint");
        });
    }

    function clampRow(row) {
        var length = session ? session.getLength() : 1;
        return Math.max(0, Math.min(Number(row) || 0, Math.max(0, length - 1)));
    }

    function getLine(row) {
        return session ? session.getLine(clampRow(row)) || "" : "";
    }

    function getBreakpoints() {
        return breakpoints.slice(0).sort(function(a, b) { return a - b; });
    }

    function getUndoManager() {
        return session ? session.getUndoManager() : null;
    }

    function isReadOnly() {
        return !!(editor && editor.getReadOnly && editor.getReadOnly());
    }

    function getScrollState() {
        var renderer = editor ? editor.renderer : null;
        return {
            firstVisibleLine: renderer && renderer.getFirstVisibleRow ? renderer.getFirstVisibleRow() : 0,
            firstVisibleColumn: renderer ? Math.max(0, renderer.scrollLeft || 0) : 0
        };
    }

    function buildState(includeText) {
        var position = editor ? editor.getCursorPosition() : { row: 0, column: 0 };
        var undoManager = getUndoManager();
        var selection = getSelectionState(includeText !== false);
        var readOnly = isReadOnly();
        var state = {
            dirty: dirty,
            readOnly: readOnly,
            canUndo: !readOnly && !!(undoManager && undoManager.hasUndo && undoManager.hasUndo()),
            canRedo: !readOnly && !!(undoManager && undoManager.hasRedo && undoManager.hasRedo()),
            lineCount: session ? session.getLength() : 1,
            cursor: {
                row: position.row || 0,
                column: position.column || 0
            },
            selection: selection,
            selectedText: selection.text,
            completionPopupOpen: isCompletionPopupOpen(),
            scroll: getScrollState(),
            breakpoints: getBreakpoints()
        };
        if (includeText !== false) {
            state.text = session ? session.getValue() : "";
        }
        return state;
    }

    function positionToIndex(position) {
        return session ? session.doc.positionToIndex(position, 0) : 0;
    }

    function indexToPosition(index) {
        if (!session) {
            return { row: 0, column: 0 };
        }
        index = Math.max(0, Math.min(Number(index) || 0, session.getValue().length));
        return session.doc.indexToPosition(index, 0);
    }

    function getSelectionState(includeText) {
        if (!editor || !session) {
            return {
                startOffset: 0,
                endOffset: 0,
                startLine: 0,
                startColumn: 0,
                endLine: 0,
                endColumn: 0,
                text: ""
            };
        }
        var range = editor.getSelectionRange();
        var startOffset = positionToIndex(range.start);
        var endOffset = positionToIndex(range.end);
        return {
            startOffset: startOffset,
            endOffset: endOffset,
            startLine: range.start.row,
            startColumn: range.start.column,
            endLine: range.end.row,
            endColumn: range.end.column,
            text: includeText === false ? "" : session.getTextRange(range)
        };
    }

    function cloneEditorSelectionRange() {
        if (!editor || !editor.getSelectionRange) {
            return null;
        }
        var range = editor.getSelectionRange();
        return range && range.clone ? range.clone() : range;
    }

    function restoreEditorSelectionRange(range, reason) {
        if (!range || !editor || !editor.selection || !editor.selection.setRange) {
            return false;
        }
        cursorRevealSuppressUntil = Math.max(cursorRevealSuppressUntil, Date.now() + PINCH_CURSOR_REVEAL_SUPPRESS_MS);
        try {
            editor.selection.setRange(range.clone ? range.clone() : range, false);
            resetImeTextInputSelectionWindow(reason || "selection_restore");
            notifyCursorChanged();
            notifySelectionChanged();
            return true;
        } catch (ignore) {
            return false;
        }
    }

    function hasActiveSelection() {
        return !!getSelectionState().text;
    }

    function setNativeActionModeActive(active) {
        active = !!active;
        if (nativeActionModeActive === active) {
            return;
        }
        nativeActionModeActive = active;
        if (document && document.body && document.body.classList) {
            document.body.classList.toggle("autojs6-native-action-mode", active);
        }
    }

    function setSelectionMenuSuppressed(suppressed) {
        suppressed = !!suppressed;
        if (selectionMenuSuppressed === suppressed) {
            return;
        }
        selectionMenuSuppressed = suppressed;
        if (document && document.body && document.body.classList) {
            document.body.classList.toggle("autojs6-selection-menu-suppressed", suppressed);
        }
    }

    function finishNativeActionMode(eventName) {
        var hadHostActionMode = nativeActionModeActive || !!lastActionModeSelectedText;
        if (actionModeNotifyTimer !== null) {
            clearTimeout(actionModeNotifyTimer);
            actionModeNotifyTimer = null;
        }
        setNativeActionModeActive(false);
        if (lastActionModeSelectedText) {
            lastActionModeSelectedText = "";
            dispatchSelectionActionModeState(eventName || "selectionActionModeFinished");
        }
        if (hadHostActionMode) {
            callBridge("finishActionMode");
        }
    }

    function suppressSelectionActionModeMenu(reason) {
        if (!hasActiveSelection()) {
            setSelectionMenuSuppressed(shouldSuppressSelectionActionModeAfterTextMutation());
            finishNativeActionMode("selectionActionModeFinished");
            return;
        }
        var alreadySuppressed = selectionMenuSuppressed &&
            !nativeActionModeActive &&
            !lastActionModeSelectedText;
        setSelectionMenuSuppressed(true);
        finishNativeActionMode("selectionActionModeSuppressed");
        if (!alreadySuppressed) {
            callBridge("notifyEvent", [
                "selectionActionModeSuppressed",
                JSON.stringify({ reason: String(reason || "") })
            ]);
        }
    }

    function markTextMutationSelectionMenuSuppressed(reason) {
        textMutationSelectionMenuSuppressUntil = Math.max(
            textMutationSelectionMenuSuppressUntil,
            Date.now() + TEXT_MUTATION_SELECTION_MENU_SUPPRESS_MS
        );
    }

    function suppressTextMutationSelectionMenu(reason) {
        markTextMutationSelectionMenuSuppressed(reason || "ime_text_mutation");
        suppressSelectionActionModeMenu(reason || "ime_text_mutation");
    }

    function shouldSuppressSelectionActionModeAfterTextMutation() {
        return Date.now() <= textMutationSelectionMenuSuppressUntil;
    }

    function markBridgePinchSelectionSuppressed(durationMs) {
        extendBridgePinchSelectionSuppression(durationMs);
        setSelectionMenuSuppressed(true);
        finishNativeActionMode("selectionActionModeSuppressed");
    }

    function extendBridgePinchSelectionSuppression(durationMs) {
        bridgePinchSelectionSuppressUntil = Math.max(
            bridgePinchSelectionSuppressUntil,
            Date.now() + (Number(durationMs) || 1200)
        );
    }

    function shouldSuppressSelectionActionModeForPinch() {
        return bridgePinchZoomActive || Date.now() <= bridgePinchSelectionSuppressUntil;
    }

    function extendBridgePinchImeSuppression(durationMs) {
        bridgePinchImeSuppressUntil = Math.max(
            bridgePinchImeSuppressUntil,
            Date.now() + (Number(durationMs) || PINCH_IME_SUPPRESS_MS)
        );
    }

    function shouldSuppressImeForPinch() {
        return Date.now() <= bridgePinchImeSuppressUntil;
    }

    function restoreBridgePinchImeElementIfReady() {
        if (shouldSuppressImeForPinch()) {
            scheduleBridgePinchImeRestore();
            return;
        }
        if (bridgePinchImeElement) {
            try {
                bridgePinchImeElement.readOnly = bridgePinchImeElementWasReadOnly;
                if (!bridgePinchImeElementWasReadOnly) {
                    bridgePinchImeElement.removeAttribute("readonly");
                }
            } catch (ignore) {
                // The textarea may have been replaced during editor teardown.
            }
        }
        bridgePinchImeElement = null;
        bridgePinchImeElementWasReadOnly = false;
        bridgePinchImeRestoreTimer = null;
    }

    function scheduleBridgePinchImeRestore() {
        if (bridgePinchImeRestoreTimer !== null) {
            clearTimeout(bridgePinchImeRestoreTimer);
        }
        bridgePinchImeRestoreTimer = setTimeout(restoreBridgePinchImeElementIfReady, Math.max(
            16,
            bridgePinchImeSuppressUntil - Date.now() + 16
        ));
    }

    function suppressBridgePinchIme(durationMs) {
        extendBridgePinchImeSuppression(durationMs);
        var element = getImeTextInputElement();
        if (element) {
            if (bridgePinchImeElement !== element) {
                if (bridgePinchImeElement) {
                    try {
                        bridgePinchImeElement.readOnly = bridgePinchImeElementWasReadOnly;
                        if (!bridgePinchImeElementWasReadOnly) {
                            bridgePinchImeElement.removeAttribute("readonly");
                        }
                    } catch (ignore0) {
                        // The previous textarea may have been detached.
                    }
                }
                bridgePinchImeElement = element;
                bridgePinchImeElementWasReadOnly = !!element.readOnly;
            }
            try {
                element.readOnly = true;
                element.setAttribute("readonly", "readonly");
                if (typeof element.blur === "function") {
                    element.blur();
                }
            } catch (ignore) {
                // Keep pinch handling non-fatal if WebView refuses textarea mutation.
            }
        }
        try {
            if (document && document.activeElement && document.activeElement !== document.body &&
                typeof document.activeElement.blur === "function"
            ) {
                document.activeElement.blur();
            }
        } catch (ignore2) {
            // Best-effort IME suppression.
        }
        scheduleBridgePinchImeRestore();
    }

    function releaseBridgePinchImeSuppression(reason) {
        if (bridgePinchImeRestoreTimer !== null) {
            clearTimeout(bridgePinchImeRestoreTimer);
            bridgePinchImeRestoreTimer = null;
        }
        bridgePinchImeSuppressUntil = 0;
        restoreBridgePinchImeElementIfReady();
        callBridge("notifyEvent", [
            "pinchImeSuppressionReleased",
            JSON.stringify({ reason: String(reason || "user_touch") })
        ]);
    }

    function releaseBridgePinchImeSuppressionForUserTouch(event) {
        if (bridgePinchZoomActive || !event || !event.touches || event.touches.length !== 1) {
            return;
        }
        releaseBridgePinchPostTouchSuppression("single_touch");
        if (shouldSuppressImeForPinch()) {
            releaseBridgePinchImeSuppression("single_touch");
        }
    }

    function markBridgePinchPostTouchSuppressed(durationMs) {
        bridgePinchPostTouchSuppressUntil = Math.max(
            bridgePinchPostTouchSuppressUntil,
            Date.now() + (Number(durationMs) || PINCH_POST_TOUCH_SUPPRESS_MS)
        );
    }

    function shouldSuppressPostPinchTouchEvent(event) {
        if (bridgePinchZoomActive || !event) {
            return false;
        }
        if (Date.now() > bridgePinchPostTouchSuppressUntil) {
            return false;
        }
        return event.type !== "touchstart";
    }

    function releaseBridgePinchPostTouchSuppression(reason) {
        var hadSuppression = Date.now() <= bridgePinchPostTouchSuppressUntil ||
            bridgePinchZoomAnchor !== null ||
            bridgePinchZoomSelectionRange !== null;
        bridgePinchPostTouchSuppressUntil = 0;
        bridgePinchZoomAnchor = null;
        bridgePinchZoomSelectionRange = null;
        if (!hadSuppression) {
            return;
        }
        callBridge("notifyEvent", [
            "pinchPostTouchSuppressionReleased",
            JSON.stringify({ reason: String(reason || "user_touch") })
        ]);
    }

    function restorePinchZoomSelection(reason) {
        return restoreEditorSelectionRange(
            bridgePinchZoomSelectionRange,
            reason || "pinch_selection_restore"
        );
    }

    function schedulePinchZoomSelectionRestore(reason) {
        var range = bridgePinchZoomSelectionRange;
        if (!range) {
            return;
        }
        [0, 32, 96, 192, 384].forEach(function(delay) {
            setTimeout(function() {
                if (bridgePinchZoomSelectionRange === range) {
                    restorePinchZoomSelection(reason || "pinch_selection_restore");
                }
            }, delay);
        });
        setTimeout(function() {
            if (bridgePinchZoomSelectionRange === range) {
                bridgePinchZoomSelectionRange = null;
            }
        }, PINCH_CURSOR_RESTORE_MS);
    }

    function suppressPostPinchTouchEvent(event) {
        if (!shouldSuppressPostPinchTouchEvent(event)) {
            return false;
        }
        restorePinchZoomSelection("post_pinch_touch");
        restorePinchZoomAnchor(bridgePinchZoomLastFocus);
        stopDomTouchEvent(event);
        callBridge("notifyEvent", [
            "postPinchTouchSuppressed",
            JSON.stringify({ type: String(event.type || "") })
        ]);
        return true;
    }

    function clearSelectionForPinch(reason) {
        markBridgePinchSelectionSuppressed(1400);
        try {
            if (editor && editor.clearSelection) {
                editor.clearSelection();
            }
        } catch (ignore) {
            // Selection cleanup is best-effort; native action mode remains suppressed.
        }
        callBridge("notifyEvent", [
            "selectionClearedForPinch",
            JSON.stringify({ reason: String(reason || "pinch_zoom") })
        ]);
        notifyCursorChanged();
        notifyStateChanged("pinchSelectionSuppressed");
    }

    function cancelAceMobileTouchInteraction(reason) {
        reason = String(reason || "touch_cancel");
        foldTapCandidate = null;
        try {
            if (editor && editor.$mouseHandler) {
                editor.$mouseHandler.isMousePressed = false;
            }
            if (editor && editor.renderer) {
                editor.renderer.$isMousePressed = false;
            }
        } catch (ignore) {
            // Best-effort reset for ACE mobile touch state held inside private handlers.
        }
        try {
            if (editor && editor.completer && editor.completer.detach) {
                editor.completer.detach();
            }
        } catch (ignore2) {
            // Completion is transient UI; keep touch cancellation non-fatal.
        }
        try {
            if (tooltipController) {
                tooltipController.hide();
            }
            if (signatureHelpController) {
                signatureHelpController.hide();
            }
        } catch (ignore3) {
            // Tooltip/signature helpers are optional.
        }
        suppressSelectionActionModeMenu(reason);
        if (reason.indexOf("pinch") >= 0) {
            markBridgePinchSelectionSuppressed(1400);
        }
        callBridge("notifyEvent", [
            "aceTouchInteractionCancelled",
            JSON.stringify({ reason: reason })
        ]);
    }

    function stopDomTouchEvent(event) {
        if (!event) {
            return;
        }
        if (event.cancelable !== false && event.preventDefault) {
            event.preventDefault();
        }
        if (event.stopPropagation) {
            event.stopPropagation();
        }
        if (event.stopImmediatePropagation) {
            event.stopImmediatePropagation();
        }
    }

    function touchFromEvent(event) {
        return (event && event.touches && event.touches[0]) ||
            (event && event.changedTouches && event.changedTouches[0]) ||
            null;
    }

    function clientPointFromDomEvent(event) {
        return touchFromEvent(event) || event || null;
    }

    var nonPassiveCaptureTouchOptions = null;

    function getNonPassiveCaptureTouchOptions() {
        if (nonPassiveCaptureTouchOptions !== null) {
            return nonPassiveCaptureTouchOptions;
        }
        nonPassiveCaptureTouchOptions = true;
        try {
            var options = { capture: true };
            Object.defineProperty(options, "passive", {
                get: function() {
                    nonPassiveCaptureTouchOptions = { capture: true, passive: false };
                    return false;
                }
            });
            var probe = function() {};
            global.addEventListener("autojs6-passive-probe", probe, options);
            global.removeEventListener("autojs6-passive-probe", probe, options);
        } catch (ignore) {
            nonPassiveCaptureTouchOptions = true;
        }
        return nonPassiveCaptureTouchOptions;
    }

    function installTouchDragScroller(element, scrollBy) {
        if (!element || element.$autojs6TouchDragScrollerInstalled || !element.addEventListener) {
            return;
        }
        element.$autojs6TouchDragScrollerInstalled = true;
        var state = null;
        var options = getNonPassiveCaptureTouchOptions();

        function onTouchStart(event) {
            if (!event || !event.touches || event.touches.length !== 1) {
                state = null;
                return;
            }
            var touch = touchFromEvent(event);
            if (!touch) {
                state = null;
                return;
            }
            state = {
                startX: Number(touch.clientX) || 0,
                startY: Number(touch.clientY) || 0,
                lastX: Number(touch.clientX) || 0,
                lastY: Number(touch.clientY) || 0,
                moved: false
            };
        }

        function onTouchMove(event) {
            if (!state) {
                return;
            }
            var touch = touchFromEvent(event);
            if (!touch) {
                return;
            }
            var x = Number(touch.clientX) || 0;
            var y = Number(touch.clientY) || 0;
            var totalDx = x - state.startX;
            var totalDy = y - state.startY;
            if (!state.moved && Math.max(Math.abs(totalDx), Math.abs(totalDy)) < 6) {
                return;
            }
            var dx = state.lastX - x;
            var dy = state.lastY - y;
            state.lastX = x;
            state.lastY = y;
            state.moved = true;
            if (scrollBy(dx, dy, event) !== false) {
                stopDomTouchEvent(event);
            }
        }

        function onTouchEnd(event) {
            if (state && state.moved) {
                stopDomTouchEvent(event);
            }
            state = null;
        }

        element.addEventListener("touchstart", onTouchStart, options);
        element.addEventListener("touchmove", onTouchMove, options);
        element.addEventListener("touchend", onTouchEnd, options);
        element.addEventListener("touchcancel", onTouchEnd, options);
    }

    function installAcePopupTouchScroller(popup) {
        if (!popup || !popup.container || !popup.renderer) {
            return;
        }
        popup.container.style.touchAction = "none";
        popup.container.style.webkitUserSelect = "none";
        popup.container.style.overflowX = "auto";
        popup.container.style.overflowY = "hidden";
        popup.container.style.maxWidth = "calc(100vw - 16px)";
        installTouchDragScroller(popup.container, function(dx, dy) {
            var scrolled = false;
            var beforeLeft = popup.container.scrollLeft;
            popup.container.scrollLeft += dx;
            scrolled = popup.container.scrollLeft !== beforeLeft;
            if (popup.renderer && typeof popup.renderer.scrollBy === "function") {
                popup.renderer.scrollBy(0, dy);
                scrolled = true;
            } else if (popup.container.scrollHeight > popup.container.clientHeight) {
                var beforeTop = popup.container.scrollTop;
                popup.container.scrollTop += dy;
                scrolled = scrolled || popup.container.scrollTop !== beforeTop;
            }
            if (popup.container.scrollWidth > popup.container.clientWidth) {
                return true;
            }
            return scrolled;
        });
    }

    function fitDocTooltipToViewport(node) {
        if (!node || !node.getBoundingClientRect) {
            return;
        }
        var doc = document && document.documentElement || {};
        var viewportWidth = Math.max(Number(global.innerWidth) || 0, Number(doc.clientWidth) || 0, 320);
        var viewportHeight = Math.max(Number(global.innerHeight) || 0, Number(doc.clientHeight) || 0, 240);
        var edge = 8;
        node.style.boxSizing = "border-box";
        node.style.maxWidth = Math.max(160, viewportWidth - edge * 2) + "px";
        node.style.maxHeight = Math.max(96, Math.round(viewportHeight * 0.45)) + "px";

        var rect = node.getBoundingClientRect();
        if (rect.left < edge || rect.right > viewportWidth - edge || node.scrollWidth > node.clientWidth) {
            node.style.left = edge + "px";
            node.style.right = edge + "px";
        }

        rect = node.getBoundingClientRect();
        if (rect.top < edge) {
            node.style.top = edge + "px";
            node.style.bottom = "";
        } else if (rect.bottom > viewportHeight - edge) {
            node.style.top = "";
            node.style.bottom = edge + "px";
        }
    }

    function installDocTooltipTouchScroller(node) {
        if (!node) {
            return;
        }
        node.style.boxSizing = "border-box";
        node.style.maxWidth = "calc(100vw - 16px)";
        node.style.maxHeight = "45vh";
        node.style.overflow = "auto";
        node.style.whiteSpace = "pre";
        node.style.touchAction = "pan-x pan-y";
        node.style.webkitOverflowScrolling = "touch";
        fitDocTooltipToViewport(node);
        installTouchDragScroller(node, function(dx, dy) {
            var beforeLeft = node.scrollLeft;
            var beforeTop = node.scrollTop;
            node.scrollLeft += dx;
            node.scrollTop += dy;
            return node.scrollLeft !== beforeLeft ||
                node.scrollTop !== beforeTop ||
                node.scrollWidth > node.clientWidth ||
                node.scrollHeight > node.clientHeight;
        });
    }

    function installDiagnosticsTooltipTouchScroller(node) {
        if (!node) {
            return;
        }
        node.style.boxSizing = "border-box";
        node.style.maxWidth = "calc(100vw - 16px)";
        node.style.maxHeight = "45vh";
        node.style.overflow = "auto";
        node.style.touchAction = "pan-x pan-y";
        node.style.webkitOverflowScrolling = "touch";
        installTouchDragScroller(node, function(dx, dy) {
            var beforeLeft = node.scrollLeft;
            var beforeTop = node.scrollTop;
            node.scrollLeft += dx;
            node.scrollTop += dy;
            return node.scrollLeft !== beforeLeft ||
                node.scrollTop !== beforeTop ||
                node.scrollWidth > node.clientWidth ||
                node.scrollHeight > node.clientHeight;
        });
    }

    function installDiagnosticsTooltipTouchScrollers() {
        if (!document || !document.querySelectorAll) {
            return;
        }
        var nodes = document.querySelectorAll(".error_widget");
        for (var i = 0; i < nodes.length; i += 1) {
            installDiagnosticsTooltipTouchScroller(nodes[i]);
        }
    }

    function scheduleDiagnosticsTooltipTouchScrollers() {
        var run = function() {
            installDiagnosticsTooltipTouchScrollers();
        };
        setTimeout(run, 0);
        setTimeout(run, 80);
    }

    function hideAceTooltipNodes() {
        if (!document || !document.querySelectorAll) {
            return;
        }
        var nodes = document.querySelectorAll(".ace_tooltip");
        for (var i = 0; i < nodes.length; i += 1) {
            nodes[i].style.display = "none";
        }
    }

    function scheduleAceTooltipHide() {
        hideAceTooltipNodes();
        setTimeout(hideAceTooltipNodes, 0);
        setTimeout(hideAceTooltipNodes, 80);
        setTimeout(hideAceTooltipNodes, 240);
    }

    function installCompletionPopupTouchScrollPatch() {
        try {
            if (!global.ace || typeof global.ace.require !== "function") {
                return;
            }
            var autocomplete = global.ace.require("ace/autocomplete");
            var Autocomplete = autocomplete && autocomplete.Autocomplete;
            var prototype = Autocomplete && Autocomplete.prototype;
            if (!prototype || prototype.$autojs6TouchScrollPatched) {
                return;
            }

            var originalInit = prototype.$init;
            if (typeof originalInit === "function") {
                prototype.$init = function() {
                    var popup = originalInit.apply(this, arguments);
                    installAcePopupTouchScroller(popup);
                    return popup;
                };
            }

            var originalShowDocTooltip = prototype.showDocTooltip;
            if (typeof originalShowDocTooltip === "function") {
                prototype.showDocTooltip = function() {
                    var result = originalShowDocTooltip.apply(this, arguments);
                    installDocTooltipTouchScroller(this.tooltipNode);
                    return result;
                };
            }

            prototype.$autojs6TouchScrollPatched = true;
        } catch (error) {
            notifyRecoverableError("completionTouchScroll", error);
        }
    }

    function resetAceBuiltInBodyZoom() {
        if (!document || !document.body || !document.body.style) {
            return;
        }
        if (document.body.style.zoom && document.body.style.zoom !== "1") {
            document.body.style.zoom = "1";
            if (editor && editor.renderer && editor.renderer.onResize) {
                editor.renderer.onResize(true);
            }
        }
    }

    function suppressAceBuiltInPinchZoom(event) {
        if (!event || !event.touches || event.touches.length < 2) {
            return false;
        }
        var distance = pinchDistanceFromTouches(event.touches);
        var focus = pinchFocusFromTouches(event.touches);
        if (!distance || distance <= 0) {
            return false;
        }
        resetAceBuiltInBodyZoom();
        if (!bridgePinchZoomActive && !isBridgePinchZoomEnabled()) {
            stopDomTouchEvent(event);
            return true;
        }
        if (!bridgePinchZoomActive || event.type === "touchstart") {
            startBridgePinchZoom(distance, focus);
        } else {
            bridgePinchZoomLastFocus = focus;
            applyBridgePinchZoom(distance / bridgePinchZoomStartDistance, focus);
        }
        stopDomTouchEvent(event);
        return true;
    }

    function suppressAceBuiltInGestureZoom(event) {
        resetAceBuiltInBodyZoom();
        stopDomTouchEvent(event);
        return true;
    }

    function pinchDistanceFromTouches(touches) {
        if (!touches || touches.length < 2) {
            return 0;
        }
        var dx = Number(touches[0].clientX) - Number(touches[1].clientX);
        var dy = Number(touches[0].clientY) - Number(touches[1].clientY);
        return Math.sqrt(dx * dx + dy * dy);
    }

    function pinchFocusFromTouches(touches) {
        if (!touches || touches.length < 2) {
            return bridgePinchZoomLastFocus;
        }
        return {
            x: (Number(touches[0].clientX) + Number(touches[1].clientX)) / 2,
            y: (Number(touches[0].clientY) + Number(touches[1].clientY)) / 2
        };
    }

    function isBridgePinchZoomEnabled() {
        return callBridgeString("getPinchToZoomStrategy", "change_text_size") !== "disabled";
    }

    function rendererViewportRect() {
        var renderer = editor && editor.renderer;
        var element = renderer && (renderer.scroller || renderer.container);
        return element && element.getBoundingClientRect ? element.getBoundingClientRect() : null;
    }

    function rendererCharacterWidth() {
        var renderer = editor && editor.renderer;
        var width = renderer && Number(renderer.characterWidth);
        if (!width && renderer && renderer.layerConfig) {
            width = Number(renderer.layerConfig.characterWidth);
        }
        return width > 0 ? width : 1;
    }

    function rendererLineHeight() {
        var renderer = editor && editor.renderer;
        var height = renderer && Number(renderer.lineHeight);
        if (!height && renderer && renderer.layerConfig) {
            height = Number(renderer.layerConfig.lineHeight);
        }
        return height > 0 ? height : 1;
    }

    function rendererTextPadding() {
        var renderer = editor && editor.renderer;
        return renderer ? Number(renderer.$padding) || 0 : 0;
    }

    function capturePinchZoomAnchor(focus) {
        var rect = rendererViewportRect();
        var x = Number(focus && focus.x);
        var y = Number(focus && focus.y);
        if (!rect || !isFinite(x) || !isFinite(y)) {
            return null;
        }
        return {
            screenColumn: (x + currentScrollLeft() - rect.left - rendererTextPadding()) / rendererCharacterWidth(),
            screenRow: (y + currentScrollTop() - rect.top) / rendererLineHeight()
        };
    }

    function restorePinchZoomAnchor(focus) {
        var rect = rendererViewportRect();
        var anchor = bridgePinchZoomAnchor;
        var x = Number(focus && focus.x);
        var y = Number(focus && focus.y);
        if (!rect || !anchor || !isFinite(x) || !isFinite(y)) {
            return false;
        }
        scrollToPosition(
            anchor.screenColumn * rendererCharacterWidth() + rendererTextPadding() + rect.left - x,
            anchor.screenRow * rendererLineHeight() + rect.top - y
        );
        return true;
    }

    function schedulePinchZoomAnchorRestore(focus) {
        var anchor = bridgePinchZoomAnchor;
        var focusCopy = focus ? { x: Number(focus.x), y: Number(focus.y) } : null;
        if (!anchor || !focusCopy || !isFinite(focusCopy.x) || !isFinite(focusCopy.y)) {
            return;
        }
        var restore = function() {
            if (bridgePinchZoomAnchor === anchor) {
                restorePinchZoomAnchor(focusCopy);
            }
        };
        if (global && global.requestAnimationFrame) {
            global.requestAnimationFrame(restore);
        } else {
            setTimeout(restore, 16);
        }
    }

    function startBridgePinchZoom(distance, focus) {
        bridgePinchZoomActive = true;
        bridgePinchZoomStartDistance = distance;
        bridgePinchZoomBaseFontSizeSp = currentFontSizeSp;
        bridgePinchZoomCurrentFontSizeSp = currentFontSizeSp;
        bridgePinchZoomLastFocus = focus;
        bridgePinchZoomAnchor = capturePinchZoomAnchor(focus);
        bridgePinchZoomSelectionRange = cloneEditorSelectionRange();
        bridgePinchPostTouchSuppressUntil = 0;
        cursorRevealSuppressUntil = Math.max(cursorRevealSuppressUntil, Date.now() + PINCH_CURSOR_REVEAL_SUPPRESS_MS);
        suppressBridgePinchIme(PINCH_IME_SUPPRESS_MS);
        cancelAceMobileTouchInteraction("pinch_zoom_begin");
        markBridgePinchSelectionSuppressed(1600);
        notifyBridgePinchZoom("begin", currentFontSizeSp, focus);
    }

    function applyBridgePinchZoom(scale, focus) {
        if (!bridgePinchZoomActive || !scale || scale <= 0) {
            return;
        }
        extendBridgePinchImeSuppression(PINCH_IME_SUPPRESS_MS);
        extendBridgePinchSelectionSuppression(1600);
        var targetSize = clampFontSizeSp(bridgePinchZoomBaseFontSizeSp * scale);
        if (Math.abs(targetSize - bridgePinchZoomCurrentFontSizeSp) < 0.025) {
            restorePinchZoomAnchor(focus);
            return;
        }
        bridgePinchZoomCurrentFontSizeSp = applyFontSizeSpKeepingFocusForPinch(targetSize, focus);
    }

    function notifyBridgePinchZoom(phase, fontSizeSp, focus) {
        focus = focus || bridgePinchZoomLastFocus || { x: 0, y: 0 };
        callBridge("handlePinchZoom", [
            Number(fontSizeSp) || currentFontSizeSp || 14,
            Number(focus.x) || 0,
            Number(focus.y) || 0,
            String(phase || "")
        ]);
    }

    function finishBridgePinchZoom(event) {
        if (bridgePinchZoomActive) {
            var finalSize = Math.round(bridgePinchZoomCurrentFontSizeSp);
            if (Math.abs(finalSize - bridgePinchZoomCurrentFontSizeSp) >= 0.001) {
                bridgePinchZoomCurrentFontSizeSp = applyFontSizeSpKeepingFocusForPinch(finalSize, bridgePinchZoomLastFocus);
            }
            notifyBridgePinchZoom(
                event && event.type === "touchcancel" ? "cancel" : "end",
                bridgePinchZoomCurrentFontSizeSp,
                bridgePinchZoomLastFocus
            );
            bridgePinchZoomActive = false;
            cursorRevealSuppressUntil = Math.max(cursorRevealSuppressUntil, Date.now() + PINCH_CURSOR_REVEAL_SUPPRESS_MS);
            suppressBridgePinchIme(PINCH_IME_SUPPRESS_MS);
            markBridgePinchPostTouchSuppressed(PINCH_POST_TOUCH_SUPPRESS_MS);
            markBridgePinchSelectionSuppressed(1600);
            restorePinchZoomSelection("pinch_zoom_end");
            stopDomTouchEvent(event);
            resetAceBuiltInBodyZoom();
            restorePinchZoomAnchor(bridgePinchZoomLastFocus);
            schedulePinchZoomAnchorRestore(bridgePinchZoomLastFocus);
            schedulePinchZoomSelectionRestore("pinch_zoom_end_deferred");
            setTimeout(function(anchor) {
                if (bridgePinchZoomAnchor === anchor) {
                    bridgePinchZoomAnchor = null;
                }
            }, 160, bridgePinchZoomAnchor);
            return;
        }
        resetAceBuiltInBodyZoom();
    }

    function installAceBuiltInPinchZoomSuppression() {
        if (!editor || !editor.container || !editor.container.addEventListener) {
            return;
        }
        resetAceBuiltInBodyZoom();
        ["touchstart", "touchmove"].forEach(function(type) {
            editor.container.addEventListener(type, suppressAceBuiltInPinchZoom, true);
        });
        editor.container.addEventListener("touchstart", releaseBridgePinchImeSuppressionForUserTouch, true);
        ["touchend", "touchcancel"].forEach(function(type) {
            editor.container.addEventListener(type, finishBridgePinchZoom, true);
        });
        ["touchend", "touchcancel", "mousedown", "mouseup", "click", "dblclick", "contextmenu"].forEach(function(type) {
            editor.container.addEventListener(type, suppressPostPinchTouchEvent, true);
        });
        if (document && document.addEventListener) {
            document.addEventListener("gesturestart", suppressAceBuiltInGestureZoom, true);
            document.addEventListener("gesturechange", suppressAceBuiltInGestureZoom, true);
            document.addEventListener("gestureend", finishBridgePinchZoom, true);
        }
    }

    function rememberTouchAnchor(event) {
        var touch = touchFromEvent(event);
        if (!touch) {
            return;
        }
        lastTouchClientX = Number(touch.clientX);
        lastTouchClientY = Number(touch.clientY);
        lastTouchAt = Date.now();
    }

    function isRecentTouchAnchor() {
        return lastTouchClientX >= 0 &&
            lastTouchClientY >= 0 &&
            Date.now() - lastTouchAt <= TOUCH_ANCHOR_MAX_AGE_MS;
    }

    function installAceMobileTouchCancellation() {
        if (!document || !document.addEventListener) {
            return;
        }
        document.addEventListener("touchcancel", function(event) {
            rememberTouchAnchor(event);
            cancelAceMobileTouchInteraction("dom_touchcancel");
        }, true);
        if (global && global.addEventListener) {
            global.addEventListener("blur", function() {
                cancelAceMobileTouchInteraction("window_blur");
            });
        }
    }

    function getActionModeFallbackRect() {
        var width = Math.max(
            1,
            Number(global.innerWidth) ||
                Number(document.documentElement && document.documentElement.clientWidth) ||
                Number(editor && editor.container && editor.container.clientWidth) ||
                1
        );
        var height = Math.max(
            1,
            Number(global.innerHeight) ||
                Number(document.documentElement && document.documentElement.clientHeight) ||
                Number(editor && editor.container && editor.container.clientHeight) ||
                1
        );
        var lineHeight = editor && editor.renderer && editor.renderer.lineHeight || 20;
        var centerX = Math.max(0, Math.round(width / 2));
        var centerY = Math.max(0, Math.round(height / 2));
        var left = Math.max(0, Math.min(width - 1, centerX - 1));
        var top = Math.max(0, Math.min(height - 1, centerY - 1));
        return {
            left: left,
            top: top,
            right: Math.max(left + 1, Math.min(width, left + 2)),
            bottom: Math.max(top + 1, Math.min(height, top + lineHeight))
        };
    }

    function getSelectionScreenRect() {
        var fallback = getActionModeFallbackRect();
        if (isRecentTouchAnchor()) {
            return {
                left: Math.max(0, lastTouchClientX),
                top: Math.max(0, lastTouchClientY),
                right: Math.max(1, lastTouchClientX + 1),
                bottom: Math.max(1, lastTouchClientY + (editor && editor.renderer && editor.renderer.lineHeight || 20))
            };
        }
        if (!editor || !editor.renderer || !editor.renderer.textToScreenCoordinates) {
            return fallback;
        }
        var range = editor.getSelectionRange();
        if (!range) {
            return fallback;
        }
        var renderer = editor.renderer;
        var start = renderer.textToScreenCoordinates(range.start.row, range.start.column);
        var end = renderer.textToScreenCoordinates(range.end.row, range.end.column);
        var lineHeight = renderer.lineHeight ||
            (renderer.layerConfig && renderer.layerConfig.lineHeight) ||
            20;
        var startX = Number(start && start.pageX);
        var startY = Number(start && start.pageY);
        var endX = Number(end && end.pageX);
        var endY = Number(end && end.pageY);
        if (!isFinite(startX) || !isFinite(startY)) {
            return fallback;
        }
        if (!isFinite(endX) || !isFinite(endY)) {
            endX = startX;
            endY = startY;
        }
        var left = Math.min(startX, endX);
        var top = Math.min(startY, endY);
        var right = Math.max(startX, endX);
        var bottom = Math.max(startY, endY) + lineHeight;
        if (right <= left) {
            right = left + 1;
        }
        if (bottom <= top) {
            bottom = top + lineHeight;
        }
        return {
            left: left,
            top: top,
            right: right,
            bottom: bottom
        };
    }

    function updateActionModeFromSelection() {
        actionModeNotifyTimer = null;
        var selection = getSelectionState();
        if (!selection.text) {
            setSelectionMenuSuppressed(false);
            finishNativeActionMode("selectionActionModeFinished");
            return;
        }
        if (shouldSuppressSelectionActionModeAfterTextMutation()) {
            suppressSelectionActionModeMenu("text_mutation_action_mode");
            return;
        }
        if (selectionMenuSuppressed) {
            finishNativeActionMode("selectionActionModeSuppressed");
            return;
        }
        var rect = getSelectionScreenRect();
        lastActionModeSelectedText = selection.text;
        lastSelectionActionModeStartedAt = Date.now();
        setNativeActionModeActive(true);
        dispatchSelectionActionModeState("selectionActionModeStarted");
        callBridge("startActionMode", [
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            true,
            false
        ]);
    }

    function scheduleActionModeSelectionUpdate() {
        if (actionModeNotifyTimer !== null) {
            clearTimeout(actionModeNotifyTimer);
            actionModeNotifyTimer = null;
        }
        if (ACTION_MODE_NOTIFY_THROTTLE_MS <= 0) {
            updateActionModeFromSelection();
            return;
        }
        actionModeNotifyTimer = setTimeout(updateActionModeFromSelection, ACTION_MODE_NOTIFY_THROTTLE_MS);
    }

    function stateJson(includeText) {
        return JSON.stringify(buildState(includeText));
    }

    function fullStateJson() {
        return stateJson(true);
    }

    function lightweightStateJson() {
        return stateJson(false);
    }

    function dispatchStateChanged(eventName) {
        var json = lightweightStateJson();
        callBridge("notifyStateChanged", [json]);
    }

    function flushStateChanged() {
        var eventName = stateNotifyEventName || "stateChanged";
        stateNotifyTimer = null;
        stateNotifyEventName = "";
        lastStateNotifyAt = Date.now();
        dispatchStateChanged(eventName);
    }

    function notifyStateChanged(eventName) {
        stateNotifyEventName = eventName || stateNotifyEventName || "stateChanged";
        var now = Date.now();
        var elapsed = now - lastStateNotifyAt;
        if (elapsed >= STATE_NOTIFY_THROTTLE_MS && stateNotifyTimer === null) {
            flushStateChanged();
            return;
        }
        if (stateNotifyTimer === null) {
            stateNotifyTimer = setTimeout(flushStateChanged, Math.max(0, STATE_NOTIFY_THROTTLE_MS - elapsed));
        }
    }

    function dispatchSelectionActionModeState(eventName) {
        if (stateNotifyTimer !== null) {
            clearTimeout(stateNotifyTimer);
            stateNotifyTimer = null;
        }
        stateNotifyEventName = "";
        lastStateNotifyAt = Date.now();
        dispatchStateChanged(eventName || "selectionActionMode");
    }

    function notifyScrollChanged() {
        var now = Date.now();
        if (
            hasActiveSelection() &&
            now > programmaticScrollUntil &&
            now - lastSelectionChangedAt > ACTION_MODE_SCROLL_SUPPRESS_GRACE_MS &&
            now - lastSelectionActionModeStartedAt > ACTION_MODE_SCROLL_SUPPRESS_GRACE_MS
        ) {
            lastSelectionBrowseScrollAt = Date.now();
            suppressSelectionActionModeMenu("browse_scroll");
        }
        if (scrollNotifyTimer !== null) {
            clearTimeout(scrollNotifyTimer);
        }
        scrollNotifyTimer = setTimeout(function() {
            scrollNotifyTimer = null;
            notifyStateChanged("scrollChanged");
        }, 80);
    }

    function wrapScrollMethod(owner, methodName) {
        if (!owner || typeof owner[methodName] !== "function" || owner[methodName].__autojs6ScrollHooked) {
            return;
        }
        var original = owner[methodName];
        var wrapped = function() {
            var result = original.apply(this, arguments);
            notifyScrollChanged();
            return result;
        };
        wrapped.__autojs6ScrollHooked = true;
        wrapped.__autojs6Original = original;
        owner[methodName] = wrapped;
    }

    function installScrollChangeHooks() {
        wrapScrollMethod(session, "setScrollTop");
        wrapScrollMethod(session, "setScrollLeft");
        if (editor && editor.renderer) {
            wrapScrollMethod(editor.renderer, "scrollToY");
            wrapScrollMethod(editor.renderer, "scrollToX");
        }
        wrapScrollMethod(editor, "scrollToLine");
    }

    function wrapCursorRevealMethod(owner, methodName) {
        if (!owner || typeof owner[methodName] !== "function" || owner[methodName].__autojs6CursorRevealGuarded) {
            return;
        }
        var original = owner[methodName];
        var wrapped = function() {
            if (Date.now() <= cursorRevealSuppressUntil) {
                return;
            }
            return original.apply(this, arguments);
        };
        wrapped.__autojs6CursorRevealGuarded = true;
        wrapped.__autojs6Original = original;
        owner[methodName] = wrapped;
    }

    function installCursorRevealSuppression() {
        var renderer = editor && editor.renderer;
        wrapCursorRevealMethod(renderer, "scrollCursorIntoView");
        wrapCursorRevealMethod(renderer, "scrollSelectionIntoView");
        wrapCursorRevealMethod(editor, "scrollToLine");
    }

    function dispatchTextChanged(includeText) {
        var json = stateJson(includeText === true);
        callBridge("notifyTextChanged", [json]);
    }

    function flushTextChanged() {
        var includeText = textNotifyIncludeText;
        textNotifyTimer = null;
        textNotifyIncludeText = false;
        lastTextNotifyAt = Date.now();
        dispatchTextChanged(includeText);
    }

    function notifyTextChanged(includeText) {
        if (includeText === true) {
            if (textNotifyTimer !== null) {
                clearTimeout(textNotifyTimer);
                textNotifyTimer = null;
            }
            textNotifyIncludeText = false;
            lastTextNotifyAt = Date.now();
            dispatchTextChanged(true);
            return;
        }
        textNotifyIncludeText = textNotifyIncludeText || includeText === true;
        var now = Date.now();
        var elapsed = now - lastTextNotifyAt;
        if (elapsed >= TEXT_NOTIFY_THROTTLE_MS && textNotifyTimer === null) {
            flushTextChanged();
            return;
        }
        if (textNotifyTimer === null) {
            textNotifyTimer = setTimeout(flushTextChanged, Math.max(0, TEXT_NOTIFY_THROTTLE_MS - elapsed));
        }
    }

    function notifyChangeDelta(delta) {
        if (!delta) {
            return false;
        }
        callBridge("notifyEvent", [
            "changeDelta",
            JSON.stringify({
                action: String(delta.action || ""),
                start: delta.start || { row: 0, column: 0 },
                end: delta.end || { row: 0, column: 0 },
                lines: delta.lines || []
            })
        ]);
        return true;
    }

    function dispatchCursorChanged() {
        if (!editor) {
            return;
        }
        var position = editor.getCursorPosition();
        var json = lightweightStateJson();
        callBridge("notifyCursorChanged", [
            "",
            position.row || 0,
            position.column || 0,
            json
        ]);
    }

    function flushCursorChanged() {
        cursorNotifyTimer = null;
        lastCursorNotifyAt = Date.now();
        dispatchCursorChanged();
    }

    function notifyCursorChanged() {
        var now = Date.now();
        var elapsed = now - lastCursorNotifyAt;
        if (elapsed >= CURSOR_NOTIFY_THROTTLE_MS && cursorNotifyTimer === null) {
            flushCursorChanged();
            return;
        }
        if (cursorNotifyTimer === null) {
            cursorNotifyTimer = setTimeout(flushCursorChanged, Math.max(0, CURSOR_NOTIFY_THROTTLE_MS - elapsed));
        }
    }

    function isAndroidUserAgent() {
        return typeof navigator !== "undefined" && /Android/i.test(String(navigator.userAgent || ""));
    }

    function getImeTextInputElement() {
        var textInput = editor && editor.textInput;
        if (!textInput || typeof textInput.getElement !== "function") {
            return null;
        }
        var element = textInput.getElement();
        if (
            !element ||
            typeof element.selectionStart !== "number" ||
            typeof element.selectionEnd !== "number"
        ) {
            return null;
        }
        return element;
    }

    function syncImeTextInputSelectionWindow(reason) {
        var element = getImeTextInputElement();
        if (!element) {
            imeTextInputSelectionWindow.element = null;
            imeTextInputSelectionWindow.value = "";
            imeTextInputSelectionWindow.start = -1;
            imeTextInputSelectionWindow.end = -1;
            return false;
        }
        var value = String(element.value || "");
        var start = Math.max(0, Math.min(Number(element.selectionStart) || 0, value.length));
        var end = Math.max(0, Math.min(Number(element.selectionEnd) || 0, value.length));
        imeTextInputSelectionWindow.element = element;
        imeTextInputSelectionWindow.value = value;
        imeTextInputSelectionWindow.start = start;
        imeTextInputSelectionWindow.end = end;
        if (reason === "editor_ready" || reason === "selectionchange_large_delta") {
            callBridge("notifyEvent", [
                "imeTextInputSelectionWindow",
                JSON.stringify({
                    reason: String(reason),
                    start: start,
                    end: end,
                    length: value.length
                })
            ]);
        }
        return true;
    }

    function shouldSuppressImeTextInputSelectionAfterReset(reason) {
        reason = String(reason || "");
        return reason === "move_cursor" ||
            reason === "select_range" ||
            reason === "jump_to" ||
            reason === "shortcut_insert" ||
            reason === "touch_cursor" ||
            reason === "init" ||
            reason.indexOf("focus") >= 0;
    }

    function clearImeTextInputSelectionSuppression() {
        imeTextInputSelectionSuppressUntil = 0;
        imeTextInputSelectionSuppressReason = "";
    }

    function suppressNextImeTextInputSelectionChange(reason) {
        if (!isAndroidUserAgent()) {
            return;
        }
        if (!shouldSuppressImeTextInputSelectionAfterReset(reason)) {
            return;
        }
        imeTextInputSelectionSuppressReason = String(reason || "");
        imeTextInputSelectionSuppressUntil =
            Date.now() + IME_TEXT_INPUT_PROGRAMMATIC_SELECTION_SUPPRESS_MS;
    }

    function resetImeTextInputSelectionWindow(reason) {
        if (!isAndroidUserAgent()) {
            return false;
        }
        var textInput = editor && editor.textInput;
        suppressNextImeTextInputSelectionChange(reason);
        imeTextInputSelectionSyncing = true;
        try {
            if (textInput && typeof textInput.resetSelection === "function") {
                textInput.resetSelection();
            }
        } catch (error) {
            notifyRecoverableError("imeTextInputSelectionWindow", error);
        } finally {
            syncImeTextInputSelectionWindow(reason || "reset");
            imeTextInputSelectionSyncing = false;
        }
        return imeTextInputSelectionWindow.element !== null;
    }

    function focusEditor(reason) {
        if (!editor) {
            return false;
        }
        if (shouldSuppressImeForPinch()) {
            suppressBridgePinchIme(PINCH_IME_SUPPRESS_MS);
            callBridge("notifyEvent", [
                "focusSuppressedForPinch",
                JSON.stringify({ reason: String(reason || "focus") })
            ]);
            return false;
        }
        editor.focus();
        resetImeTextInputSelectionWindow(reason || "focus");
        scrollCursorIntoViewSafely(reason || "focus");
        notifyCursorChanged();
        return typeof editor.isFocused === "function" ? editor.isFocused() : true;
    }

    function handleImeTextInputSelectionChange() {
        if (!isAndroidUserAgent() || imeTextInputSelectionSyncing) {
            return;
        }
        var element = getImeTextInputElement();
        if (!element || document.activeElement !== element) {
            return;
        }
        var value = String(element.value || "");
        var start = Math.max(0, Math.min(Number(element.selectionStart) || 0, value.length));
        var end = Math.max(0, Math.min(Number(element.selectionEnd) || 0, value.length));
        if (
            imeTextInputSelectionWindow.element !== element ||
            imeTextInputSelectionWindow.start < 0 ||
            imeTextInputSelectionWindow.value !== value ||
            start !== end
        ) {
            syncImeTextInputSelectionWindow("selectionchange_sync");
            return;
        }
        var previous = imeTextInputSelectionWindow.start;
        var delta = start - previous;
        if (delta === 0) {
            syncImeTextInputSelectionWindow("");
            return;
        }
        if (Date.now() <= imeTextInputSelectionSuppressUntil) {
            callBridge("notifyEvent", [
                "imeTextInputSelectionSuppressed",
                JSON.stringify({
                    reason: imeTextInputSelectionSuppressReason,
                    previous: previous,
                    start: start,
                    delta: delta,
                    length: value.length
                })
            ]);
            clearImeTextInputSelectionSuppression();
            syncImeTextInputSelectionWindow("selectionchange_suppressed");
            return;
        }
        if (Math.abs(delta) > IME_TEXT_INPUT_SELECTION_MAX_DELTA) {
            callBridge("notifyEvent", [
                "imeTextInputSelectionDropped",
                JSON.stringify({
                    previous: previous,
                    start: start,
                    delta: delta,
                    length: value.length
                })
            ]);
            syncImeTextInputSelectionWindow("selectionchange_large_delta");
            return;
        }
        var now = Date.now();
        if (now - lastImeTextInputSelectionNotifyAt >= 100) {
            lastImeTextInputSelectionNotifyAt = now;
            callBridge("notifyEvent", [
                "imeTextInputSelection",
                JSON.stringify({
                    previous: previous,
                    start: start,
                    delta: delta,
                    length: value.length
                })
            ]);
        }
        moveCursor(delta);
    }

    function installImeTextInputSelectionWindow() {
        if (!isAndroidUserAgent() || imeTextInputSelectionWindow.installed) {
            return;
        }
        imeTextInputSelectionWindow.installed = true;
        document.addEventListener("selectionchange", handleImeTextInputSelectionChange, true);
        if (editor && editor.on) {
            editor.on("destroy", function() {
                document.removeEventListener("selectionchange", handleImeTextInputSelectionChange, true);
                imeTextInputSelectionWindow.installed = false;
            });
        }
        resetImeTextInputSelectionWindow("editor_ready");
    }

    function notifySelectionChanged() {
        lastSelectionChangedAt = Date.now();
        var selection = getSelectionState();
        if (shouldSuppressSelectionActionModeForPinch()) {
            if (selection.text) {
                clearSelectionForPinch("pinch_selection_changed");
                return;
            }
            setSelectionMenuSuppressed(true);
            finishNativeActionMode("selectionActionModeSuppressed");
            notifyStateChanged("selectionChanged");
            return;
        }
        if (selection.text && shouldSuppressSelectionActionModeAfterTextMutation()) {
            suppressSelectionActionModeMenu("text_mutation_selection");
            notifyStateChanged("selectionChanged");
            return;
        }
        setSelectionMenuSuppressed(false);
        if (selection.text || lastActionModeSelectedText) {
            setNativeActionModeActive(true);
            notifyStateChanged("selectionChanged");
        } else {
            setNativeActionModeActive(false);
        }
        scheduleActionModeSelectionUpdate();
    }

    function handleDocumentVisibilityChanged() {
        if (document.hidden || document.visibilityState === "hidden") {
            suppressSelectionActionModeMenu("document_hidden");
            return;
        }
        scheduleResize("document_visible");
        notifyStateChanged("document_visible");
    }

    function notifyBreakpointChanged(row, enabled) {
        var json = lightweightStateJson();
        callBridge("notifyBreakpointChanged", [row, !!enabled, json]);
        callBridge("notifyEvent", ["breakpointChanged", json]);
    }

    function scrollCursorIntoViewSafely(reason) {
        if (!editor || !editor.renderer || !editor.renderer.scrollCursorIntoView) {
            return;
        }
        if (shouldSkipCursorReveal(reason)) {
            callBridge("notifyEvent", [
                "cursorRevealSkipped",
                JSON.stringify({ reason: String(reason || "") })
            ]);
            return;
        }
        try {
            programmaticScrollUntil = Date.now() + 300;
            editor.renderer.scrollCursorIntoView();
        } catch (error) {
            notifyRecoverableError(reason || "scrollCursorIntoView", error);
        }
    }

    function shouldSkipCursorReveal(reason) {
        reason = String(reason || "").toLowerCase();
        if (isFontFamilyMetricsRefreshReason(reason)) {
            return true;
        }
        if (!hasActiveSelection()) {
            return false;
        }
        if (isExplicitViewportRevealReason(reason)) {
            return false;
        }
        if (isActiveSelectionOffscreen()) {
            return true;
        }
        if (Date.now() - lastSelectionBrowseScrollAt > SELECTION_BROWSE_REVEAL_SUPPRESS_MS) {
            return false;
        }
        return !reason ||
            reason.indexOf("resize:") === 0 ||
            reason.indexOf("action_mode") >= 0 ||
            reason.indexOf("window_resize") >= 0;
    }

    function isFontFamilyMetricsRefreshReason(reason) {
        return reason.indexOf("font_descriptor") >= 0 ||
            reason.indexOf("font_family") >= 0 ||
            reason.indexOf("font_load_fallback") >= 0 ||
            reason.indexOf("font_first_paint_fallback") >= 0;
    }

    function isExplicitViewportRevealReason(reason) {
        return reason.indexOf("ime") >= 0 ||
            reason.indexOf("keyboard") >= 0 ||
            reason.indexOf("panel") >= 0 ||
            reason.indexOf("native_ready") >= 0 ||
            reason.indexOf("bridge_ready") >= 0 ||
            reason.indexOf("first_paint") >= 0 ||
            reason.indexOf("init") >= 0 ||
            reason.indexOf("jump") >= 0 ||
            reason.indexOf("restore") >= 0;
    }

    function isActiveSelectionOffscreen() {
        if (!editor || !editor.renderer || !editor.getSelectionRange) {
            return false;
        }
        var range = editor.getSelectionRange();
        if (!range || range.isEmpty && range.isEmpty()) {
            return false;
        }
        var renderer = editor.renderer;
        var first = renderer.getFirstVisibleRow ? renderer.getFirstVisibleRow() : 0;
        var last = renderer.getLastVisibleRow ?
            renderer.getLastVisibleRow() :
            first + Math.ceil((renderer.container && renderer.container.clientHeight || 0) / (renderer.lineHeight || 16));
        return range.end.row < first || range.start.row > last;
    }

    function runResize(reason) {
        if (!editor) {
            return;
        }
        try {
            editor.resize(true);
            scrollCursorIntoViewSafely("resize:" + (reason || ""));
            callBridge("notifyEvent", [
                "resizeDone",
                JSON.stringify({ reason: String(reason || "") })
            ]);
        } catch (error) {
            notifyRecoverableError("scheduleResize", error);
        }
    }

    function scheduleResize(reason) {
        resizeReason = reason || resizeReason || "scheduled";
        if (resizeFallbackTimer !== null) {
            clearTimeout(resizeFallbackTimer);
        }
        resizeFallbackTimer = setTimeout(function() {
            resizeFallbackTimer = null;
            runResize(resizeReason || "resize_fallback");
        }, 140);
        if (resizeScheduled) {
            return;
        }
        resizeScheduled = true;
        var raf = global.requestAnimationFrame || function(run) { return setTimeout(run, 16); };
        raf(function() {
            resizeScheduled = false;
            runResize(resizeReason || "resize_frame");
        });
    }

    function setBreakpoint(row, enabled, notify) {
        row = clampRow(row);
        var index = breakpoints.indexOf(row);
        if (enabled && index < 0) {
            breakpoints.push(row);
            session.setBreakpoint(row, "ace_breakpoint");
            if (notify !== false) {
                notifyBreakpointChanged(row, true);
            }
            return true;
        }
        if (!enabled && index >= 0) {
            breakpoints.splice(index, 1);
            session.clearBreakpoint(row);
            if (notify !== false) {
                notifyBreakpointChanged(row, false);
            }
            return true;
        }
        return false;
    }

    function markClean() {
        var undoManager = getUndoManager();
        if (undoManager && undoManager.markClean) {
            undoManager.markClean();
        }
        dirty = false;
        notifyStateChanged("cleanChanged");
        return true;
    }

    function markDirty() {
        var undoManager = getUndoManager();
        if (undoManager && undoManager.markClean && undoManager.getRevision &&
            undoManager.isClean && undoManager.isClean()) {
            undoManager.markClean(undoManager.getRevision() - 1);
        }
        dirty = true;
        notifyStateChanged("dirtyChanged");
        return true;
    }

    function refreshState() {
        dispatchStateChanged("hostRefresh");
        return true;
    }

    function notifyReadOnlyMutationBlocked(operation) {
        callBridge("notifyEvent", [
            "readOnlyMutationBlocked",
            JSON.stringify({ operation: String(operation || "") })
        ]);
        notifyStateChanged("readOnlyMutationBlocked");
    }

    function runTextMutation(operation, callback, defaultValue) {
        if (isReadOnly()) {
            notifyReadOnlyMutationBlocked(operation);
            return defaultValue;
        }
        markTextMutationSelectionMenuSuppressed(operation);
        try {
            return callback();
        } finally {
            markTextMutationSelectionMenuSuppressed(operation);
        }
    }

    function toggleBreakpoint(row) {
        row = clampRow(row);
        return setBreakpoint(row, breakpoints.indexOf(row) < 0, true);
    }

    function clearBreakpoints() {
        breakpoints.forEach(function(row) {
            session.clearBreakpoint(row);
        });
        breakpoints = [];
        notifyStateChanged("breakpointsCleared");
    }

    function setDebuggingLine(row) {
        if (!session || !Range) {
            return;
        }
        if (debugMarker !== null) {
            session.removeMarker(debugMarker);
            debugMarker = null;
        }
        if (row < 0) {
            notifyStateChanged("debugLineCleared");
            return;
        }
        row = clampRow(row);
        debugMarker = session.addMarker(new Range(row, 0, row, 1), "autojs6_debug_line", "fullLine");
        notifyStateChanged("debugLineChanged");
    }

    function installDocumentLongLineRenderGuard() {
        var textLayer = editor && editor.renderer && editor.renderer.$textLayer;
        if (!textLayer || textLayer.__autojs6LongLineRenderGuardInstalled ||
            typeof textLayer.$renderSimpleLine !== "function") {
            return;
        }
        textLayer.__autojs6LongLineRenderGuardInstalled = true;
        var originalRenderSimpleLine = textLayer.$renderSimpleLine;
        textLayer.$renderSimpleLine = function(parent, tokens) {
            if (!documentLongLineSafetyMode || !tokens || !tokens.length) {
                return originalRenderSimpleLine.call(this, parent, tokens);
            }

            var observedLength = 0;
            for (var index = 0; index < tokens.length; index++) {
                observedLength += String(tokens[index] && tokens[index].value || "").length;
                if (observedLength > LONG_LINE_SAFETY_RENDER_TRIGGER) {
                    break;
                }
            }
            if (observedLength <= LONG_LINE_SAFETY_RENDER_TRIGGER) {
                return originalRenderSimpleLine.call(this, parent, tokens);
            }

            var remaining = LONG_LINE_SAFETY_RENDER_LIMIT;
            var clippedTokens = [];
            for (var tokenIndex = 0; tokenIndex < tokens.length && remaining > 0; tokenIndex++) {
                var token = tokens[tokenIndex] || {};
                var value = String(token.value || "");
                var clippedValue = value.slice(0, remaining);
                if (clippedValue) {
                    clippedTokens.push({
                        type: token.type || "text",
                        value: clippedValue
                    });
                    remaining -= clippedValue.length;
                }
            }
            originalRenderSimpleLine.call(this, parent, clippedTokens);
            var indicator = this.dom.createElement("span");
            indicator.className = "ace_keyword autojs6_long_line_truncated";
            indicator.textContent = "...";
            parent.appendChild(indicator);
        };
    }

    function applyDocumentAccessibilityLightweightMode(enabled) {
        var textLayer = editor && editor.renderer && editor.renderer.$textLayer;
        var textLayerElement = textLayer && textLayer.element;
        if (!textLayerElement || !textLayerElement.setAttribute) {
            return;
        }
        // A pathological single line can contain hundreds of thousands of characters. Android
        // WebView otherwise attempts to expose every rendered character through Chromium's
        // accessibility tree whenever the document is replaced or history is navigated. That
        // tree rebuild can monopolize the renderer for tens of seconds even in ACE text mode.
        // ACE's separate textarea remains available for focus and keyboard input.
        if (enabled) {
            textLayerElement.setAttribute("aria-hidden", "true");
        } else {
            textLayerElement.removeAttribute("aria-hidden");
        }
    }

    function applyDocumentLongLineSafetyMode(enabled) {
        enabled = !!enabled;
        var changed = documentLongLineSafetyMode !== enabled;
        documentLongLineSafetyMode = enabled;
        if (!session || !editor) {
            return;
        }
        session.$autojs6LongLineSafetyMode = enabled;
        applyDocumentAccessibilityLightweightMode(enabled);
        if (!changed) {
            return;
        }
        session.setMode(enabled ? "ace/mode/text" : "ace/mode/javascript");
        session.setUseWrapMode(enabled ? false : preferredWordWrapEnabled);
        editor.setOption("wrap", enabled ? false : preferredWordWrapEnabled);
        editor.setOption("enableBasicAutocompletion", !enabled);
        editor.setOption("enableLiveAutocompletion", !enabled);
        if (enabled && typeof session.setAnnotations === "function") {
            session.setAnnotations([]);
        }
        applyGutterOptions("long_line_safety_mode_changed");
    }

    function setText(text, echoText, unsafeLine) {
        var targetUnsafeLine = !!unsafeLine;
        if (documentLongLineSafetyMode || targetUnsafeLine) {
            applyDocumentLongLineSafetyMode(true);
        }
        suppressChange = true;
        session.setValue(text || "");
        suppressChange = false;
        applyDocumentLongLineSafetyMode(targetUnsafeLine);
        dirty = false;
        var undoManager = getUndoManager();
        if (undoManager && undoManager.reset) {
            undoManager.reset();
        }
        editor.clearSelection();
        editor.moveCursorTo(0, 0);
        notifyTextChanged(echoText !== false);
        notifyCursorChanged();
    }

    function setTextDirty(text, echoText, unsafeLine) {
        var targetUnsafeLine = !!unsafeLine;
        if (documentLongLineSafetyMode || targetUnsafeLine) {
            applyDocumentLongLineSafetyMode(true);
        }
        suppressChange = true;
        session.setValue(text || "");
        suppressChange = false;
        applyDocumentLongLineSafetyMode(targetUnsafeLine);
        markDirty();
        notifyTextChanged(echoText !== false);
        notifyCursorChanged();
    }

    function cloneHistoryPosition(position) {
        return {
            row: Math.max(0, Number(position && position.row) || 0),
            column: Math.max(0, Number(position && position.column) || 0)
        };
    }

    function captureDocumentNewLineState() {
        var document = session && session.doc;
        if (!document || typeof document.getNewLineMode !== "function" ||
            typeof document.getNewLineCharacter !== "function") {
            return null;
        }
        return {
            mode: String(document.getNewLineMode() || "auto"),
            character: String(document.getNewLineCharacter() || "\n")
        };
    }

    function applyDocumentNewLineState(state) {
        var document = session && session.doc;
        if (!state || !document) {
            return;
        }
        var character = String(state.character || "\n");
        if (character !== "\r\n" && character !== "\r" && character !== "\n") {
            character = "\n";
        }
        var mode = String(state.mode || "auto");
        var autoNewLineChanged = document.$autoNewLine !== character;
        document.$autoNewLine = character;
        if (typeof document.setNewLineMode === "function" &&
            typeof document.getNewLineMode === "function" &&
            document.getNewLineMode() !== mode) {
            document.setNewLineMode(mode);
        } else if (autoNewLineChanged && typeof document._signal === "function") {
            document._signal("changeNewLineMode");
        }
    }

    function attachHistoryOperationToken(
        group,
        token,
        beforeUnsafeLine,
        afterUnsafeLine,
        beforeNewLineState,
        afterNewLineState
    ) {
        if (!group || !token) {
            return false;
        }
        group.autojs6HistoryToken = String(token);
        group.autojs6LongLineSafetyBefore = !!beforeUnsafeLine;
        group.autojs6LongLineSafetyAfter = !!afterUnsafeLine;
        group.autojs6NewLineBefore = beforeNewLineState || captureDocumentNewLineState();
        group.autojs6NewLineAfter = afterNewLineState || group.autojs6NewLineBefore;
        if (editor && editor.selection && editor.selection.toJSON) {
            var selection = editor.selection.toJSON();
            group.selectionBefore = selection;
            group.selectionAfter = selection;
        }
        return true;
    }

    function addUndoableHistoryMarker(token, beforeUnsafeLine, afterUnsafeLine) {
        token = String(token || "");
        var undoManager = getUndoManager();
        if (!token || !undoManager || !undoManager.add) {
            return false;
        }

        beforeUnsafeLine = typeof beforeUnsafeLine === "boolean" ? beforeUnsafeLine : documentLongLineSafetyMode;
        afterUnsafeLine = typeof afterUnsafeLine === "boolean" ? afterUnsafeLine : beforeUnsafeLine;
        var newLineState = captureDocumentNewLineState();
        if (beforeUnsafeLine || afterUnsafeLine) {
            applyDocumentLongLineSafetyMode(true);
        }
        session.markUndoGroup();
        var position = cloneHistoryPosition(editor && editor.getCursorPosition && editor.getCursorPosition());
        // ACE's Document.applyDelta() explicitly ignores this empty insertion. Its inverse is an
        // empty removal, which is ignored as well, while UndoManager still advances its revision.
        undoManager.add({
            action: "insert",
            start: cloneHistoryPosition(position),
            end: cloneHistoryPosition(position),
            lines: [""]
        }, false);
        var attached = attachHistoryOperationToken(
            undoManager.lastDeltas,
            token,
            beforeUnsafeLine,
            afterUnsafeLine,
            newLineState,
            newLineState
        );
        session.markUndoGroup();
        if (!attached) {
            applyDocumentLongLineSafetyMode(beforeUnsafeLine);
            return false;
        }
        applyDocumentLongLineSafetyMode(afterUnsafeLine);
        dirty = !(undoManager.isClean && undoManager.isClean());
        notifyStateChanged("historyMarkerAdded");
        return true;
    }

    function replaceAllTextUndoably(text, token, echoText, unsafeLine) {
        text = String(text || "");
        token = String(token || "");
        if (!token || !session || !Range) {
            return false;
        }
        var beforeUnsafeLine = documentLongLineSafetyMode;
        var afterUnsafeLine = !!unsafeLine;
        var beforeNewLineState = captureDocumentNewLineState();
        if (session.getValue() === text) {
            return addUndoableHistoryMarker(token, beforeUnsafeLine, afterUnsafeLine);
        }

        if (beforeUnsafeLine || afterUnsafeLine) {
            applyDocumentLongLineSafetyMode(true);
        }
        session.markUndoGroup();
        var lastRow = Math.max(0, session.getLength() - 1);
        var fullRange = new Range(0, 0, lastRow, session.getLine(lastRow).length);
        suppressChange = true;
        try {
            session.replace(fullRange, text);
        } finally {
            suppressChange = false;
        }
        var afterNewLineState = captureDocumentNewLineState();

        var undoManager = getUndoManager();
        var attached = attachHistoryOperationToken(
            undoManager && undoManager.lastDeltas,
            token,
            beforeUnsafeLine,
            afterUnsafeLine,
            beforeNewLineState,
            afterNewLineState
        );
        session.markUndoGroup();
        if (!attached) {
            return false;
        }
        applyDocumentLongLineSafetyMode(afterUnsafeLine);
        dirty = !(undoManager.isClean && undoManager.isClean());
        editor.clearSelection();
        editor.moveCursorTo(0, 0);
        notifyTextChanged(echoText !== false);
        notifyCursorChanged();
        return true;
    }

    function installHistoryOperationHooks() {
        if (!session || session.__autojs6HistoryOperationHooksInstalled) {
            return;
        }
        session.__autojs6HistoryOperationHooksInstalled = true;
        var originalUndoChanges = session.undoChanges;
        var originalRedoChanges = session.redoChanges;

        function historySafetyTarget(group, direction) {
            // If either side of a tagged group contains a pathological line, keep lightweight
            // text mode for the whole undo/redo branch. Re-enabling JavaScript tokenization for a
            // large restored document can monopolize Android WebView for tens of seconds before
            // the matching Redo; a fresh load will leave safety mode normally when appropriate.
            if (group && group.autojs6HistoryToken &&
                (group.autojs6LongLineSafetyBefore || group.autojs6LongLineSafetyAfter)) {
                return true;
            }
            var property = direction === "undo" ?
                "autojs6LongLineSafetyBefore" :
                "autojs6LongLineSafetyAfter";
            return group && typeof group[property] === "boolean" ?
                group[property] :
                documentLongLineSafetyMode;
        }

        function scheduleHistoryState(group, direction, targetUnsafeLine) {
            var token = group && group.autojs6HistoryToken ? String(group.autojs6HistoryToken) : "";
            setTimeout(function() {
                applyDocumentLongLineSafetyMode(targetUnsafeLine);
                var undoManager = getUndoManager();
                if (undoManager && undoManager.isClean) {
                    dirty = !undoManager.isClean();
                }
                // A document change schedules throttled text/state callbacks before this timer.
                // Flush them first so historyOperation is the final, authoritative notification
                // for the navigation. Otherwise a late text callback can mark an async toolbar
                // Undo/Redo as a direct edit and rewind only the encoding UI.
                if (textNotifyTimer !== null) {
                    clearTimeout(textNotifyTimer);
                    flushTextChanged();
                }
                if (stateNotifyTimer !== null) {
                    clearTimeout(stateNotifyTimer);
                    flushStateChanged();
                }
                callBridge("notifyEvent", [
                    "historyOperation",
                    JSON.stringify({
                        token: token,
                        direction: direction,
                        state: {
                            dirty: dirty,
                            readOnly: isReadOnly(),
                            canUndo: !!(undoManager && undoManager.hasUndo && undoManager.hasUndo()),
                            canRedo: !!(undoManager && undoManager.hasRedo && undoManager.hasRedo())
                        }
                    })
                ]);
            }, 0);
        }

        session.undoChanges = function(group, dontSelect) {
            var targetUnsafeLine = historySafetyTarget(group, "undo");
            if (documentLongLineSafetyMode || targetUnsafeLine) {
                applyDocumentLongLineSafetyMode(true);
            }
            var result = originalUndoChanges.call(this, group, dontSelect);
            applyDocumentNewLineState(group && group.autojs6NewLineBefore);
            scheduleHistoryState(group, "undo", targetUnsafeLine);
            return result;
        };
        session.redoChanges = function(group, dontSelect) {
            var targetUnsafeLine = historySafetyTarget(group, "redo");
            if (documentLongLineSafetyMode || targetUnsafeLine) {
                applyDocumentLongLineSafetyMode(true);
            }
            var result = originalRedoChanges.call(this, group, dontSelect);
            applyDocumentNewLineState(group && group.autojs6NewLineAfter);
            scheduleHistoryState(group, "redo", targetUnsafeLine);
            return result;
        };
    }

    function selectRange(startOffset, endOffset) {
        if (!editor || !session || !Range) {
            return;
        }
        var start = indexToPosition(startOffset);
        var end = indexToPosition(endOffset);
        editor.selection.setRange(new Range(start.row, start.column, end.row, end.column), false);
        resetImeTextInputSelectionWindow("select_range");
        scrollCursorIntoViewSafely("selectRange");
        notifyCursorChanged();
        notifySelectionChanged();
    }

    function restoreScroll(firstVisibleLine, firstVisibleColumn) {
        if (!editor || !editor.renderer) {
            return;
        }
        firstVisibleLine = clampRow(firstVisibleLine);
        firstVisibleColumn = Math.max(0, Number(firstVisibleColumn) || 0);
        programmaticScrollUntil = Date.now() + 300;
        if (editor.scrollToLine) {
            editor.scrollToLine(firstVisibleLine, false, false, null);
        }
        if (editor.renderer.scrollToX) {
            editor.renderer.scrollToX(firstVisibleColumn);
        }
        scheduleResize("scroll_restored");
        notifyStateChanged("scrollRestored");
    }

    function insert(text) {
        return runTextMutation("insert", function() {
            editor.insert(text || "");
            return true;
        }, false);
    }

    function insertTab() {
        return runTextMutation("insertTab", function() {
            if (editor.commands && editor.commands.exec) {
                editor.commands.exec("indent", editor);
            } else {
                editor.insert("\t");
            }
            notifyTextChanged();
            return true;
        }, false);
    }

    function isEditorSelectionEmpty() {
        if (!editor || !editor.selection) {
            return true;
        }
        if (typeof editor.selection.isEmpty === "function") {
            return editor.selection.isEmpty();
        }
        if (typeof editor.getSelectionRange === "function") {
            var range = editor.getSelectionRange();
            return !!(range && range.start && range.end &&
                range.start.row === range.end.row &&
                range.start.column === range.end.column);
        }
        return true;
    }

    function skipExistingClosingPair(text) {
        text = String(text || "");
        if (text !== ")" && text !== "]") {
            return false;
        }
        if (!editor || !session || !isEditorSelectionEmpty()) {
            return false;
        }
        var pos = editor.getCursorPosition && editor.getCursorPosition();
        if (!pos) {
            return false;
        }
        var line = String(session.getLine(pos.row) || "");
        if (line.charAt(pos.column) !== text) {
            return false;
        }
        if (editor.moveCursorTo) {
            editor.moveCursorTo(pos.row, pos.column + 1);
        } else if (editor.selection && editor.selection.moveTo) {
            editor.selection.moveTo(pos.row, pos.column + 1);
        }
        if (editor.renderer && editor.renderer.scrollCursorIntoView) {
            editor.renderer.scrollCursorIntoView();
        }
        resetImeTextInputSelectionWindow("shortcut_insert");
        notifyCursorChanged();
        notifyStateChanged("shortcutClosingSkipped");
        return true;
    }

    function insertShortcutText(text) {
        text = String(text || "");
        return runTextMutation("insertShortcutText", function() {
            if (skipExistingClosingPair(text)) {
                return true;
            }
            editor.insert(text);
            resetImeTextInputSelectionWindow("shortcut_insert");
            return true;
        }, false);
    }

    function isCompletionPopupOpen() {
        var completer = editor && editor.completer;
        var popup = completer && completer.popup;
        return !!(completer && completer.activated && popup && popup.isOpen);
    }

    function commandInsertedText(event) {
        var args = event && event.args;
        if (typeof args === "string") {
            return args;
        }
        if (args && typeof args.text === "string") {
            return args.text;
        }
        if (args && typeof args.value === "string") {
            return args.value;
        }
        return "";
    }

    function isCursorAfterMemberDot() {
        if (!editor || !session || !editor.getCursorPosition) {
            return false;
        }
        var pos = editor.getCursorPosition();
        var line = String(session.getLine(pos.row) || "").substring(0, pos.column);
        return /(?:[A-Za-z_$][A-Za-z0-9_$]*|\]|\)|\}|"|'|`)\.$/.test(line);
    }

    function isSingleInsertedText(delta, text) {
        if (!delta || delta.action !== "insert") {
            return false;
        }
        if (typeof delta.text === "string") {
            return delta.text === text;
        }
        return Array.isArray(delta.lines) &&
            delta.lines.length === 1 &&
            delta.lines[0] === text;
    }

    function restartMemberCompletionAfterDot() {
        memberCompletionRestartTimer = null;
        if (!editor || !isCursorAfterMemberDot()) {
            return;
        }
        if (editor.completer && typeof editor.completer.detach === "function") {
            editor.completer.detach();
        }
        if (editor.execCommand) {
            editor.execCommand("startAutocomplete");
            notifyStateChanged("memberCompletionRestarted");
        }
    }

    function scheduleMemberCompletionAfterDot() {
        if (memberCompletionRestartTimer !== null) {
            clearTimeout(memberCompletionRestartTimer);
        }
        memberCompletionRestartTimer = global.setTimeout(restartMemberCompletionAfterDot, 30);
    }

    function installMemberCompletionDotTrigger() {
        if (!editor || !editor.commands || !editor.commands.on || editor.$autojs6MemberCompletionDotTriggerInstalled) {
            return;
        }
        editor.commands.on("afterExec", function(event) {
            if (!event || !event.command || event.command.name !== "insertstring") {
                return;
            }
            if (commandInsertedText(event) !== ".") {
                return;
            }
            scheduleMemberCompletionAfterDot();
        });
        editor.$autojs6MemberCompletionDotTriggerInstalled = true;
    }

    function installTouchCursorSelectionGuard() {
        if (!editor || !editor.container || !editor.container.addEventListener || editor.$autojs6TouchCursorSelectionGuardInstalled) {
            return;
        }
        var touchCursorTimer = null;
        var options = getNonPassiveCaptureTouchOptions();

        function scheduleTouchCursorSelectionReset(reason) {
            if (!isAndroidUserAgent()) {
                return;
            }
            suppressNextImeTextInputSelectionChange("touch_cursor");
            if (touchCursorTimer !== null) {
                clearTimeout(touchCursorTimer);
            }
            touchCursorTimer = setTimeout(function() {
                touchCursorTimer = null;
                resetImeTextInputSelectionWindow(reason || "touch_cursor");
            }, 48);
        }

        editor.container.addEventListener("touchstart", function() {
            scheduleTouchCursorSelectionReset("touch_cursor_start");
        }, options);
        editor.container.addEventListener("touchend", function() {
            scheduleTouchCursorSelectionReset("touch_cursor_end");
        }, options);
        editor.container.addEventListener("touchcancel", function() {
            scheduleTouchCursorSelectionReset("touch_cursor_cancel");
        }, options);
        editor.container.addEventListener("mousedown", function() {
            scheduleTouchCursorSelectionReset("touch_cursor_mouse");
        }, true);
        editor.$autojs6TouchCursorSelectionGuardInstalled = true;
    }

    function moveCompletionSelectionOrCursor(delta) {
        delta = Number(delta) || 0;
        if (isCompletionPopupOpen() && editor.completer.goTo) {
            editor.completer.goTo(delta < 0 ? "up" : "down");
            notifyStateChanged("completionSelectionChanged");
            return true;
        }
        if (delta < 0) {
            jumpToPrevLine();
        } else if (delta > 0) {
            jumpToNextLine();
        }
        return false;
    }

    function acceptCompletionOrInsertTab() {
        if (isCompletionPopupOpen() && editor.completer.insertMatch) {
            return runTextMutation("acceptCompletion", function() {
                var accepted = editor.completer.insertMatch();
                notifyTextChanged();
                notifyCursorChanged();
                return accepted !== false;
            }, false);
        }
        return insertTab();
    }

    function languageToolsModule() {
        try {
            return global.ace && global.ace.require && global.ace.require("ace/ext/language_tools");
        } catch (error) {
            notifyCompletionError("ACE language tools unavailable: " + error, error);
            return null;
        }
    }

    function removeLanguageToolCompleter(completer) {
        var languageTools = languageToolsModule();
        var completers = languageTools && languageTools.completers;
        if (!completer || !Array.isArray(completers)) {
            return false;
        }
        var index = completers.indexOf(completer);
        if (index < 0) {
            return false;
        }
        completers.splice(index, 1);
        return true;
    }

    function insertLspCompletion(activeEditor, completion) {
        if (!completion || !completion.autojs6Ts || !completion.replaceRange) {
            return staticCompleter && typeof staticCompleter.insertMatch === "function" ?
                staticCompleter.insertMatch(activeEditor, completion) : false;
        }
        var activeSession = activeEditor && activeEditor.session;
        var replaceRange = completion.replaceRange;
        if (!activeSession || typeof activeSession.replace !== "function" ||
            !replaceRange.start || !replaceRange.end) {
            return false;
        }
        var range = Range ? new Range(
            replaceRange.start.row,
            replaceRange.start.column,
            replaceRange.end.row,
            replaceRange.end.column
        ) : replaceRange;
        if (completion.snippet) {
            activeSession.replace(range, "");
            if (activeEditor && typeof activeEditor.moveCursorTo === "function") {
                activeEditor.moveCursorTo(range.start.row, range.start.column);
            }
            try {
                var snippetManager = global.ace.require("ace/snippets").snippetManager;
                if (snippetManager && typeof snippetManager.insertSnippet === "function") {
                    snippetManager.insertSnippet(activeEditor, completion.snippet);
                    return true;
                }
            } catch (ignore) {
                // Fall through to plain insertion when the snippet module is unavailable.
            }
            var plainSnippet = String(completion.snippet || "")
                .replace(/\$\{\d+:([^}]*)\}/g, "$1")
                .replace(/\$\{\d+\}/g, "")
                .replace(/\$\d+/g, "");
            if (activeEditor && typeof activeEditor.insert === "function") {
                activeEditor.insert(plainSnippet);
                return true;
            }
            return false;
        }
        var end = activeSession.replace(range, String(completion.value || completion.caption || ""));
        if (end && activeEditor && typeof activeEditor.moveCursorTo === "function") {
            activeEditor.moveCursorTo(end.row, end.column);
        }
        return true;
    }

    function installLspCompletionCompleter() {
        if (lspCompletionCompleter || !lspClient) {
            return;
        }
        var languageTools = languageToolsModule();
        if (!languageTools || typeof languageTools.addCompleter !== "function") {
            return;
        }
        lspCompletionCompleter = {
            identifierRegexps: staticCompleter && staticCompleter.identifierRegexps,
            retrievePrecedingIdentifier: staticCompleter && staticCompleter.retrievePrecedingIdentifier,
            getCompletions: function(activeEditor, activeSession, pos, prefix, callback) {
                if (!lspClient || typeof lspClient.getCompletions !== "function") {
                    callback(null, []);
                    return;
                }
                lspClient.getCompletions(activeEditor, activeSession, pos, prefix, function(error, results) {
                    publishLspState();
                    callback(error, results);
                });
            },
            getDocTooltip: function(item) {
                if (item && item.docText) {
                    return { docText: item.docText };
                }
                if (staticCompleter && typeof staticCompleter.getDocTooltip === "function") {
                    return staticCompleter.getDocTooltip(item);
                }
                return null;
            },
            insertMatch: function(activeEditor, completion) {
                return insertLspCompletion(activeEditor, completion);
            }
        };
        languageTools.addCompleter(lspCompletionCompleter);
        removeLanguageToolCompleter(staticCompleter);
    }

    function handleEscapeKey() {
        if (!editor) {
            return false;
        }
        try {
            if (editor.completer && editor.completer.detach) {
                editor.completer.detach();
            }
            if (editor.commands && editor.commands.exec) {
                editor.commands.exec("esc", editor);
            }
            editor.focus();
            notifyStateChanged("escapeKey");
            return true;
        } catch (error) {
            notifyRecoverableError("escapeKey", error);
            return false;
        }
    }

    function insertAtLine(row, text) {
        return runTextMutation("insertAtLine", function() {
            session.insert({ row: clampRow(row), column: 0 }, text || "");
            return true;
        }, false);
    }

    function appendTextChunk(text) {
        session.insert(indexToPosition(session.getValue().length), text || "");
        return true;
    }

    function jumpTo(row, column) {
        row = clampRow(row);
        column = Math.max(0, Number(column) || 0);
        editor.focus();
        editor.gotoLine(row + 1, column, true);
        resetImeTextInputSelectionWindow("jump_to");
        scheduleResize("jump_to");
        notifyCursorChanged();
    }

    function jumpToStart() {
        jumpTo(0, 0);
    }

    function jumpToOffset(offset) {
        if (!editor || !session) {
            return false;
        }
        offset = Math.max(0, Math.min(Number(offset) || 0, session.getValue().length));
        editor.focus();
        editor.clearSelection();
        editor.moveCursorToPosition(indexToPosition(offset));
        notifyCursorChanged();
        notifySelectionChanged();
        return true;
    }

    function jumpToEnd() {
        var row = Math.max(0, session.getLength() - 1);
        jumpTo(row, getLine(row).length);
    }

    function jumpToLineStart() {
        var position = editor.getCursorPosition();
        jumpTo(position.row, 0);
    }

    function jumpToLineEnd() {
        var position = editor.getCursorPosition();
        jumpTo(position.row, getLine(position.row).length);
    }

    function jumpToNextLine() {
        var position = editor.getCursorPosition();
        jumpTo(Math.min(position.row + 1, session.getLength() - 1), position.column);
    }

    function jumpToPrevLine() {
        var position = editor.getCursorPosition();
        jumpTo(Math.max(position.row - 1, 0), position.column);
    }

    function moveCursor(delta) {
        delta = Number(delta) || 0;
        if (!delta || !editor || !session) {
            return;
        }
        if (Math.abs(delta) <= 64) {
            suppressNextImeTextInputSelectionChange("move_cursor");
            var range = editor.getSelectionRange();
            var position = !range.isEmpty() ?
                (delta < 0 ? range.start : range.end) :
                editor.getCursorPosition();
            var steps = Math.abs(delta);
            while (steps > 0) {
                if (delta < 0) {
                    if (position.column > 0) {
                        position = { row: position.row, column: position.column - 1 };
                    } else if (position.row > 0) {
                        position = { row: position.row - 1, column: getLine(position.row - 1).length };
                    }
                } else {
                    var lineLength = getLine(position.row).length;
                    if (position.column < lineLength) {
                        position = { row: position.row, column: position.column + 1 };
                    } else if (position.row + 1 < session.getLength()) {
                        position = { row: position.row + 1, column: 0 };
                    }
                }
                steps -= 1;
            }
            editor.clearSelection();
            editor.moveCursorToPosition(position);
            resetImeTextInputSelectionWindow("move_cursor");
            scrollCursorIntoViewSafely("moveCursor");
            notifyCursorChanged();
            notifySelectionChanged();
            return;
        }
        var selection = getSelectionState();
        var next = Math.max(0, Math.min(selection.startOffset + delta, session.getValue().length));
        suppressNextImeTextInputSelectionChange("move_cursor");
        selectRange(next, next);
        resetImeTextInputSelectionWindow("move_cursor");
    }

    function deleteLine() {
        return runTextMutation("deleteLine", function() {
            var row = editor.getCursorPosition().row;
            var lineCount = session.getLength();
            var start = { row: row, column: 0 };
            var end = row + 1 < lineCount ? { row: row + 1, column: 0 } : { row: row, column: getLine(row).length };
            session.remove(new Range(start.row, start.column, end.row, end.column));
            return true;
        }, false);
    }

    function clear() {
        return runTextMutation("clear", function() {
            if (!session.getValue()) {
                notifyStateChanged("clearNoop");
                return false;
            }
            var lastRow = Math.max(0, session.getLength() - 1);
            session.remove(new Range(0, 0, lastRow, getLine(lastRow).length));
            editor.clearSelection();
            editor.moveCursorTo(0, 0);
            notifyTextChanged(true);
            notifyCursorChanged();
            return true;
        }, false);
    }

    function toggleComment() {
        return runTextMutation("toggleComment", function() {
            editor.toggleCommentLines();
            notifyStateChanged("commentChanged");
            return true;
        }, false);
    }

    function beautify() {
        return runTextMutation("beautify", function() {
            try {
                var beautifyExtension = global.ace.require("ace/ext/beautify");
                beautifyExtension.beautify(session);
                notifyTextChanged(true);
                notifyStateChanged("beautified");
                return true;
            } catch (error) {
                notifyError("Beautify failed: " + error);
                return false;
            }
        }, false);
    }

    function replaceSelection(text) {
        return runTextMutation("replaceSelection", function() {
            editor.insert(text || "");
            notifyTextChanged();
            return true;
        }, false);
    }

    function replaceAll(query, replacement, usingRegex) {
        return runTextMutation("replaceAll", function() {
            var source = session.getValue();
            var next = source;
            if (usingRegex) {
                next = source.replace(new RegExp(query, "g"), replacement || "");
            } else if (query) {
                next = source.split(query).join(replacement || "");
            }
            if (next !== source) {
                session.setValue(next);
                dirty = true;
                notifyTextChanged(true);
                return true;
            }
            notifyStateChanged("replaceAllNoop");
            return false;
        }, false);
    }

    function setTheme(theme) {
        var nextTheme = theme || "ace/theme/textmate";
        applyThemeClass(nextTheme);
        editor.setTheme(nextTheme);
        notifyStateChanged("themeChanged");
    }

    function clampFontSizeSp(size) {
        return Math.max(MIN_FONT_SIZE_SP, Math.min(Number(size) || 14, MAX_FONT_SIZE_SP));
    }

    function applyFontSizeSp(size, options) {
        options = options || {};
        var sp = clampFontSizeSp(size);
        currentFontSizeSp = sp;
        editor.setFontSize(sp + "px");
        forceRendererCoordinateRefresh(options.reason || "font_size_apply");
        if (options.deferRefresh !== false) {
            scheduleFontMetricsRefresh(options.reason || "font_size_apply");
        }
        return sp;
    }

    function refreshFontMetricsForCoordinateMapping() {
        var renderer = editor && editor.renderer;
        if (renderer && renderer.updateFontSize) {
            renderer.updateFontSize();
        }
    }

    function forceRendererCoordinateRefresh(reason) {
        if (!editor || !editor.renderer) {
            return;
        }
        var renderer = editor.renderer;
        refreshFontMetricsForCoordinateMapping();
        if (editor.resize) {
            editor.resize(true);
        }
        if (renderer.updateFull) {
            renderer.updateFull(true);
        } else if (renderer.updateText) {
            renderer.updateText();
        }
        if (renderer.updateCursor) {
            renderer.updateCursor();
        }
    }

    function scheduleFontMetricsRefresh(reason) {
        var readySerial = ++fontMetricsReadySerial;
        var refresh = function() {
            fontMetricsRefreshFrameScheduled = false;
            forceRendererCoordinateRefresh(reason || "font_size_deferred");
        };
        if (!fontMetricsRefreshFrameScheduled && global && global.requestAnimationFrame) {
            fontMetricsRefreshFrameScheduled = true;
            global.requestAnimationFrame(refresh);
        } else if (!fontMetricsRefreshFrameScheduled) {
            fontMetricsRefreshFrameScheduled = true;
            setTimeout(refresh, 0);
        }
        if (fontMetricsRefreshTimer !== null) {
            clearTimeout(fontMetricsRefreshTimer);
        }
        fontMetricsRefreshTimer = setTimeout(function() {
            fontMetricsRefreshTimer = null;
            forceRendererCoordinateRefresh(reason || "font_size_deferred");
        }, 32);
        if (document.fonts && document.fonts.ready) {
            Promise.resolve(document.fonts.ready).then(function() {
                if (readySerial === fontMetricsReadySerial) {
                    forceRendererCoordinateRefresh(reason || "font_ready");
                }
            }, function() {
                // The immediate/frame/timer refreshes remain the compatibility fallback.
            });
        }
    }

    function setFontSizeSp(size) {
        applyFontSizeSp(size);
        scheduleResize("font_size_changed");
        notifyStateChanged("fontSizeChanged");
    }

    function currentScrollLeft() {
        if (session && session.getScrollLeft) {
            return Number(session.getScrollLeft()) || 0;
        }
        return editor && editor.renderer ? (Number(editor.renderer.scrollLeft) || 0) : 0;
    }

    function currentScrollTop() {
        if (session && session.getScrollTop) {
            return Number(session.getScrollTop()) || 0;
        }
        return editor && editor.renderer ? (Number(editor.renderer.scrollTop) || 0) : 0;
    }

    function scrollToPosition(scrollLeft, scrollTop) {
        if (!editor || !editor.renderer) {
            return;
        }
        var renderer = editor.renderer;
        var nextLeft = Math.max(0, Number(scrollLeft) || 0);
        var nextTop = Math.max(0, Number(scrollTop) || 0);
        programmaticScrollUntil = Date.now() + 300;
        if (session && session.setScrollLeft) {
            session.setScrollLeft(nextLeft);
        } else if (renderer.scrollToX) {
            renderer.scrollToX(nextLeft);
        }
        if (session && session.setScrollTop) {
            session.setScrollTop(nextTop);
        } else if (renderer.scrollToY) {
            renderer.scrollToY(nextTop);
        }
    }

    function scrollByDelta(deltaX, deltaY) {
        if (!editor || !editor.renderer) {
            return;
        }
        scrollToPosition(
            currentScrollLeft() + (Number(deltaX) || 0),
            currentScrollTop() + (Number(deltaY) || 0)
        );
    }

    function resizeForFocusPreservingFontSize(reason) {
        if (!editor || !editor.resize) {
            return;
        }
        forceRendererCoordinateRefresh(reason);
        callBridge("notifyEvent", [
            "resizeDone",
            JSON.stringify({ reason: String(reason || "font_size_focus_changed") })
        ]);
    }

    function setFontSizeSpKeepingFocus(size, focusX, focusY) {
        if (!editor || !editor.renderer || !editor.renderer.screenToTextCoordinates || !editor.renderer.textToScreenCoordinates) {
            setFontSizeSp(size);
            return;
        }
        var renderer = editor.renderer;
        var x = Number(focusX);
        var y = Number(focusY);
        if (!isFinite(x) || !isFinite(y)) {
            setFontSizeSp(size);
            return;
        }
        var anchor = renderer.screenToTextCoordinates(x, y);
        applyFontSizeSp(size);
        resizeForFocusPreservingFontSize("font_size_focus_before_scroll");
        var after = renderer.textToScreenCoordinates(anchor.row, anchor.column);
        scrollByDelta(after.pageX - x, after.pageY - y);
        resizeForFocusPreservingFontSize("font_size_focus_after_scroll");
        notifyStateChanged("fontSizeFocusChanged");
    }

    function applyFontSizeSpKeepingFocusForPinch(size, focus) {
        if (!editor || !editor.renderer || !editor.renderer.screenToTextCoordinates || !editor.renderer.textToScreenCoordinates) {
            return applyFontSizeSp(size, { reason: "pinch_font_size", deferRefresh: false });
        }
        var x = Number(focus && focus.x);
        var y = Number(focus && focus.y);
        if (!isFinite(x) || !isFinite(y)) {
            return applyFontSizeSp(size, { reason: "pinch_font_size", deferRefresh: false });
        }
        cursorRevealSuppressUntil = Math.max(cursorRevealSuppressUntil, Date.now() + PINCH_CURSOR_REVEAL_SUPPRESS_MS);
        if (!bridgePinchZoomAnchor) {
            bridgePinchZoomAnchor = capturePinchZoomAnchor(focus);
        }
        var appliedSize = applyFontSizeSp(size, { reason: "pinch_font_size", deferRefresh: false });
        restorePinchZoomAnchor(focus);
        schedulePinchZoomAnchorRestore(focus);
        return appliedSize;
    }

    function setFontFamily(fontFamily) {
        fontDescriptorRequestSerial += 1;
        pendingFontDescriptorLoad = null;
        replaceActiveInstalledFontFace(null);
        applyFontFamilyAndRefresh(fontFamily, "legacy_font_family_changed");
        notifyStateChanged("fontFamilyChanged");
    }

    function setFontLigaturesEnabled(enabled) {
        applyFontLigaturesEnabled(enabled);
        scheduleResize("font_ligatures_changed");
        notifyStateChanged("fontLigaturesChanged");
    }

    function setFontStylesEnabled(enabled) {
        applyFontStylesEnabled(enabled);
        scheduleResize("font_styles_changed");
        notifyStateChanged("fontStylesChanged");
    }

    function setWordWrapEnabled(enabled) {
        enabled = !!enabled;
        preferredWordWrapEnabled = enabled;
        session.setUseWrapMode(documentLongLineSafetyMode ? false : enabled);
        editor.setOption("wrap", documentLongLineSafetyMode ? false : enabled);
        applyGutterOptions("word_wrap_changed");
        notifyStateChanged("wordWrapChanged");
    }

    function normalizeWordWrapIndentStyle(style) {
        style = String(style || "").toLowerCase();
        if (style === "classic" || style === "default" || style === "continuation") {
            return style;
        }
        return "continuation";
    }

    function setWordWrapIndentStyle(style) {
        style = normalizeWordWrapIndentStyle(style);
        try {
            editor.setOption("indentedSoftWrap", style !== "classic");
        } catch (ignore) {
            // ACE 1.4.12 supports this option; keep the bridge tolerant for asset swaps.
        }
        scheduleResize("word_wrap_indent_style_changed");
        notifyStateChanged("wordWrapIndentStyleChanged");
    }

    function normalizeGutterWidthMode(mode) {
        mode = String(mode || "").toLowerCase();
        return mode === "fixed" ? "fixed" : "dynamic";
    }

    function installDynamicWrapGutterWidthPatch() {
        var renderer = editor && editor.renderer;
        var gutterLayer = renderer && renderer.$gutterLayer;
        if (!gutterLayer || !gutterLayer.$updateGutterWidth || gutterLayer.$autojs6DynamicWrapGutterWidthPatch) {
            return;
        }
        var originalUpdateGutterWidth = gutterLayer.$updateGutterWidth;
        gutterLayer.$updateGutterWidth = function(config) {
            var sessionForGutter = this.session;
            if (gutterWidthMode === "dynamic" && sessionForGutter && sessionForGutter.$useWrapMode) {
                var originalUseWrapMode = sessionForGutter.$useWrapMode;
                var originalFixedWidth = this.$fixedWidth;
                try {
                    sessionForGutter.$useWrapMode = false;
                    this.$fixedWidth = false;
                    return originalUpdateGutterWidth.call(this, config);
                } finally {
                    sessionForGutter.$useWrapMode = originalUseWrapMode;
                    this.$fixedWidth = originalFixedWidth;
                }
            }
            return originalUpdateGutterWidth.call(this, config);
        };
        gutterLayer.$autojs6DynamicWrapGutterWidthPatch = true;
    }

    function hasVisibleGutterContent() {
        return !!(lineNumbersEnabled || breakpointMarkersEnabled || foldMarkersEnabled);
    }

    function setLineNumbersEnabled(enabled) {
        lineNumbersEnabled = !!enabled;
        editor.setOption("showLineNumbers", lineNumbersEnabled);
        applyGutterOptions("line_numbers_changed");
        notifyStateChanged("lineNumbersChanged");
    }

    function setPrintMarginEnabled(enabled) {
        editor.setOption("showPrintMargin", !!enabled);
        notifyStateChanged("printMarginChanged");
    }

    function setIndentGuidesEnabled(enabled) {
        editor.setOption("displayIndentGuides", !!enabled);
        if (editor.renderer && editor.renderer.updateText) {
            editor.renderer.updateText();
        }
        notifyStateChanged("indentGuidesChanged");
    }

    function setEditorContainerClass(className, enabled) {
        if (editor && editor.container && editor.container.classList) {
            editor.container.classList.toggle(className, !!enabled);
        }
    }

    function applyGutterOptions(reason) {
        var showGutter = hasVisibleGutterContent();
        try {
            editor.setOption("fixedWidthGutter", gutterWidthMode === "fixed");
        } catch (ignore) {
            // ACE asset swaps may not expose fixedWidthGutter through setOption.
        }
        try {
            editor.renderer.setShowGutter(showGutter);
        } catch (ignore2) {
            try {
                editor.setOption("showGutter", showGutter);
            } catch (ignore3) {
                // CSS fallback below still removes the gutter visually.
            }
        }
        setEditorContainerClass("autojs6-hide-gutter", !showGutter);
        scheduleResize(reason || "gutter_changed");
    }

    function setGutterWidthMode(mode) {
        gutterWidthMode = normalizeGutterWidthMode(mode);
        applyGutterOptions("gutter_width_mode_changed");
        notifyStateChanged("gutterWidthModeChanged");
    }

    function setBreakpointMarkersEnabled(enabled) {
        breakpointMarkersEnabled = !!enabled;
        setEditorContainerClass("autojs6-hide-breakpoint-markers", !breakpointMarkersEnabled);
        applyGutterOptions("breakpoint_markers_changed");
        notifyStateChanged("breakpointMarkersChanged");
    }

    function setFoldMarkersEnabled(enabled) {
        foldMarkersEnabled = !!enabled;
        try {
            editor.setOption("showFoldWidgets", foldMarkersEnabled);
        } catch (ignore) {
            // ACE 1.4.x also honors the CSS fallback below.
        }
        setEditorContainerClass("autojs6-hide-fold-markers", !foldMarkersEnabled);
        applyGutterOptions("fold_markers_changed");
        notifyStateChanged("foldMarkersChanged");
    }

    function setReadOnlyMode(readOnly) {
        readOnly = !!readOnly;
        editor.setReadOnly(readOnly);
        editor.setOption("highlightActiveLine", !readOnly);
        editor.setOption("highlightGutterLine", !readOnly);
        editor.container.classList.toggle("autojs6-read-only", readOnly);
        notifyStateChanged("readOnlyChanged");
    }

    function undo() {
        return runTextMutation("undo", function() {
            editor.undo();
            notifyStateChanged("undo");
            return true;
        }, false);
    }

    function redo() {
        return runTextMutation("redo", function() {
            editor.redo();
            notifyStateChanged("redo");
            return true;
        }, false);
    }

    function runPreservingScroll(reason, callback) {
        var scrollLeft = currentScrollLeft();
        var scrollTop = currentScrollTop();
        var selectionRange = editor && editor.getSelectionRange && editor.getSelectionRange();
        if (selectionRange && selectionRange.clone) {
            selectionRange = selectionRange.clone();
        }
        cursorRevealSuppressUntil = Math.max(cursorRevealSuppressUntil, Date.now() + 900);
        var changed = !!callback();
        if (!changed) {
            return false;
        }
        if (selectionRange && editor && editor.selection && editor.selection.setRange) {
            try {
                editor.selection.setRange(selectionRange, false);
            } catch (ignore) {
                // Selection preservation is best-effort; scroll preservation is the primary goal.
            }
        }
        scrollToPosition(scrollLeft, scrollTop);
        setTimeout(function() {
            scrollToPosition(scrollLeft, scrollTop);
        }, 0);
        setTimeout(function() {
            scrollToPosition(scrollLeft, scrollTop);
        }, 32);
        setTimeout(function() {
            scrollToPosition(scrollLeft, scrollTop);
        }, 96);
        setTimeout(function() {
            scrollToPosition(scrollLeft, scrollTop);
        }, 192);
        setTimeout(function() {
            scrollToPosition(scrollLeft, scrollTop);
        }, 384);
        setTimeout(function() {
            scrollToPosition(scrollLeft, scrollTop);
        }, 768);
        notifyStateChanged(reason);
        return true;
    }

    function expandFoldAt(row, column) {
        if (!session) {
            return false;
        }
        return runPreservingScroll("foldExpanded", function() {
            try {
                var fold = session.getFoldAt &&
                    (session.getFoldAt(row, column, 1) ||
                        session.getFoldAt(row, column, -1) ||
                        session.getFoldAt(row, column, 0));
                if (fold && session.expandFold) {
                    session.expandFold(fold);
                    return true;
                }
                var foldLine = session.getFoldLine && session.getFoldLine(row);
                if (foldLine && foldLine.folds && foldLine.folds.length && session.expandFold) {
                    session.expandFold(foldLine.folds[0]);
                    return true;
                }
                var lineLength = getLine(row).length;
                if (session.getFoldAt && session.expandFold) {
                    for (var probeColumn = 0; probeColumn <= lineLength; probeColumn += 1) {
                        fold = session.getFoldAt(row, probeColumn, 1) ||
                            session.getFoldAt(row, probeColumn, -1) ||
                            session.getFoldAt(row, probeColumn, 0);
                        if (fold) {
                            session.expandFold(fold);
                            return true;
                        }
                    }
                }
                if (session.unfold) {
                    session.unfold(row, true);
                    return true;
                }
            } catch (error) {
                notifyRecoverableError("foldExpand", error);
            }
            return false;
        });
    }

    function foldWidgetOptionsFromDomEvent(domEvent) {
        domEvent = domEvent || {};
        return {
            children: !!domEvent.shiftKey,
            all: !!(domEvent.ctrlKey || domEvent.metaKey),
            siblings: !!domEvent.altKey
        };
    }

    function hasModifiedFoldClick(domEvent) {
        domEvent = domEvent || {};
        return !!(domEvent.shiftKey || domEvent.ctrlKey || domEvent.metaKey || domEvent.altKey);
    }

    function findFoldAtRow(row) {
        if (!session) {
            return null;
        }
        try {
            var foldLine = session.getFoldLine && session.getFoldLine(row);
            if (foldLine && foldLine.folds && foldLine.folds.length) {
                for (var i = 0; i < foldLine.folds.length; i += 1) {
                    var fold = foldLine.folds[i];
                    if (fold && fold.start && fold.end &&
                        fold.start.row <= row && fold.end.row >= row) {
                        return fold;
                    }
                }
                return foldLine.folds[0];
            }
            var lineLength = getLine(row).length;
            if (session.getFoldAt) {
                return session.getFoldAt(row, 0, 1) ||
                    session.getFoldAt(row, lineLength, -1) ||
                    session.getFoldAt(row, 0, 0) ||
                    session.getFoldAt(row, lineLength, 0);
            }
        } catch (error) {
            notifyRecoverableError("foldFind", error);
        }
        return null;
    }

    function toggleFoldWidgetDirectly(row) {
        try {
            var fold = findFoldAtRow(row);
            if (fold && session.expandFold) {
                session.expandFold(fold);
                return true;
            }
            if (!session.getFoldWidgetRange || !session.addFold) {
                return false;
            }
            var range = session.getFoldWidgetRange(row);
            if (!range || (range.isEmpty && range.isEmpty())) {
                return false;
            }
            session.addFold("...", range);
            return true;
        } catch (error) {
            notifyRecoverableError("foldToggleDirect", error);
            return false;
        }
    }

    function toggleFoldWidgetAt(row, domEvent) {
        if (!session) {
            return false;
        }
        if (session.$toggleFoldWidget && hasModifiedFoldClick(domEvent)) {
            return runPreservingScroll("foldToggled", function() {
                try {
                    return !!session.$toggleFoldWidget(row, foldWidgetOptionsFromDomEvent(domEvent));
                } catch (error) {
                    notifyRecoverableError("foldToggle", error);
                    return false;
                }
            });
        }
        return runPreservingScroll("foldToggled", function() {
            if (toggleFoldWidgetDirectly(row)) {
                return true;
            }
            if (session.$toggleFoldWidget) {
                try {
                    return !!session.$toggleFoldWidget(row, foldWidgetOptionsFromDomEvent(domEvent));
                } catch (error) {
                    notifyRecoverableError("foldToggle", error);
                }
            }
            return false;
        });
    }

    function hasClassToken(target, token) {
        return (" " + String(target && target.className || "") + " ").indexOf(" " + token + " ") >= 0;
    }

    function findAncestorWithClassToken(target, token, stopAt) {
        while (target && target !== stopAt) {
            if (hasClassToken(target, token)) {
                return target;
            }
            target = target.parentNode;
        }
        return null;
    }

    function isPointInsideElement(element, clientX, clientY) {
        if (!element || !element.getBoundingClientRect) {
            return false;
        }
        var rect = element.getBoundingClientRect();
        return clientX >= rect.left && clientX <= rect.right &&
            clientY >= rect.top && clientY <= rect.bottom;
    }

    function isGutterFoldWidgetEvent(target, domEvent) {
        var point = clientPointFromDomEvent(domEvent);
        var foldWidget = findAncestorWithClassToken(target, "ace_fold-widget", editor && editor.container);
        if (!foldWidget && target && target.querySelector) {
            foldWidget = target.querySelector(".ace_fold-widget");
        }
        return !!foldWidget && isPointInsideElement(
            foldWidget,
            point && point.clientX,
            point && point.clientY
        );
    }

    function isDiagnosticGutterCell(target) {
        return hasClassToken(target, "ace_error") ||
            hasClassToken(target, "ace_warning") ||
            hasClassToken(target, "ace_info");
    }

    function rowHasDiagnosticAnnotation(row) {
        if (!session || typeof session.getAnnotations !== "function" || row < 0) {
            return false;
        }
        var annotations = session.getAnnotations() || [];
        for (var i = 0; i < annotations.length; i += 1) {
            if (annotations[i] && annotations[i].row === row) {
                return true;
            }
        }
        return false;
    }

    function isDiagnosticGutterMarkerEvent(target, aceEvent) {
        if (!target || !target.getBoundingClientRect) {
            return false;
        }
        var position = aceEvent && typeof aceEvent.getDocumentPosition === "function" ?
            aceEvent.getDocumentPosition() :
            null;
        if (!isDiagnosticGutterCell(target) && !rowHasDiagnosticAnnotation(position && position.row)) {
            return false;
        }
        var point = clientPointFromDomEvent(aceEvent && aceEvent.domEvent || aceEvent);
        var clientX = Number(point && point.clientX);
        if (!isFinite(clientX)) {
            return true;
        }
        return clientX <= target.getBoundingClientRect().left + 24;
    }

    function getDiagnosticWidgetAtRow(row) {
        if (!session || !session.widgetManager || typeof session.widgetManager.getWidgetsAtRow !== "function") {
            return null;
        }
        var widgets = session.widgetManager.getWidgetsAtRow(row) || [];
        for (var i = 0; i < widgets.length; i += 1) {
            if (widgets[i] && widgets[i].type === "errorMarker") {
                return widgets[i];
            }
        }
        return null;
    }

    function destroyDiagnosticWidget(widget) {
        if (!widget) {
            return false;
        }
        var mouseHandler = editor && editor.$mouseHandler;
        var wasMousePressed = mouseHandler && mouseHandler.isMousePressed;
        try {
            if (mouseHandler) {
                mouseHandler.isMousePressed = false;
            }
            if (typeof widget.destroy === "function") {
                widget.destroy();
            } else if (session && session.widgetManager && typeof session.widgetManager.removeLineWidget === "function") {
                session.widgetManager.removeLineWidget(widget);
            }
            return true;
        } finally {
            if (mouseHandler) {
                mouseHandler.isMousePressed = wasMousePressed;
            }
        }
    }

    function closeDiagnosticWidgetAtRow(row) {
        return destroyDiagnosticWidget(getDiagnosticWidgetAtRow(row));
    }

    function showDiagnosticMarkerAtPosition(position) {
        if (!editor || !global.ace || typeof global.ace.require !== "function") {
            return false;
        }
        try {
            if (position && editor.selection && typeof editor.selection.moveToPosition === "function") {
                editor.selection.moveToPosition({
                    row: position.row,
                    column: Number(position.column) || 0
                });
            }
            var errorMarker = global.ace.require("ace/ext/error_marker");
            if (errorMarker && typeof errorMarker.showErrorMarker === "function") {
                errorMarker.showErrorMarker(editor, 1);
                scheduleDiagnosticsTooltipTouchScrollers();
                scheduleAceTooltipHide();
                return true;
            }
        } catch (error) {
            notifyRecoverableError("diagnosticGutter", error);
        }
        return false;
    }

    function toggleDiagnosticMarkerFromGutterEvent(aceEvent) {
        var position = aceEvent && typeof aceEvent.getDocumentPosition === "function" ?
            aceEvent.getDocumentPosition() :
            null;
        scheduleAceTooltipHide();
        if (position && closeDiagnosticWidgetAtRow(position.row)) {
            return true;
        }
        setTimeout(function() {
            showDiagnosticMarkerAtPosition(position);
        }, 80);
        return true;
    }

    function isDiagnosticGutterDomEvent(domEvent) {
        var target = domEvent && domEvent.target;
        var gutterCell = findAncestorWithClassToken(target, "ace_gutter-cell", editor && editor.container);
        return !!gutterCell && isDiagnosticGutterCell(gutterCell);
    }

    function installDiagnosticGutterTooltipSuppression() {
        if (!editor || !editor.container || !editor.container.addEventListener) {
            return;
        }
        var suppress = function(event) {
            if (!isDiagnosticGutterDomEvent(event)) {
                return;
            }
            scheduleAceTooltipHide();
            event.stopPropagation();
        };
        editor.container.addEventListener("mouseover", suppress, true);
        editor.container.addEventListener("mousemove", suppress, true);
    }

    function isFoldPlaceholderTarget(target) {
        while (target && target !== editor.container) {
            if (hasClassToken(target, "ace_gutter-cell") || hasClassToken(target, "ace_fold-widget")) {
                return false;
            }
            if (hasClassToken(target, "ace_fold")) {
                return true;
            }
            target = target.parentNode;
        }
        return false;
    }

    function beginFoldPlaceholderTouch(event) {
        if (!editor || !editor.renderer || !isFoldPlaceholderTarget(event && event.target)) {
            foldTapCandidate = null;
            return false;
        }
        var touch = touchFromEvent(event);
        if (!touch) {
            foldTapCandidate = null;
            return false;
        }
        cancelAceMobileTouchInteraction("fold_placeholder_touch_start");
        var position = editor.renderer.screenToTextCoordinates &&
            editor.renderer.screenToTextCoordinates(touch.clientX, touch.clientY);
        position = position || editor.getCursorPosition();
        foldTapCandidate = {
            startX: Number(touch.clientX),
            startY: Number(touch.clientY),
            row: position.row,
            column: position.column
        };
        return true;
    }

    function updateFoldPlaceholderTouch(event) {
        if (!foldTapCandidate) {
            return false;
        }
        var touch = touchFromEvent(event);
        if (!touch) {
            foldTapCandidate = null;
            return false;
        }
        var dx = Number(touch.clientX) - foldTapCandidate.startX;
        var dy = Number(touch.clientY) - foldTapCandidate.startY;
        if (dx * dx + dy * dy > FOLD_TAP_TOUCH_SLOP_SQUARED) {
            foldTapCandidate = null;
            return false;
        }
        return true;
    }

    function finishFoldPlaceholderTouch(event) {
        var candidate = foldTapCandidate;
        foldTapCandidate = null;
        if (!candidate) {
            return false;
        }
        if (expandFoldAt(candidate.row, candidate.column) || expandFoldAt(candidate.row, 0)) {
            cancelAceMobileTouchInteraction("fold_placeholder_touch");
            stopFoldPlaceholderClick(event);
            callBridge("notifyEvent", [
                "foldPlaceholderTouchExpanded",
                JSON.stringify({ row: candidate.row, column: candidate.column })
            ]);
            return true;
        }
        callBridge("notifyEvent", [
            "foldPlaceholderTouchMissed",
            JSON.stringify({ row: candidate.row, column: candidate.column })
        ]);
        return false;
    }

    function handleFoldPlaceholderTouch(event) {
        var type = event && event.type;
        if (type === "touchstart") {
            if (beginFoldPlaceholderTouch(event)) {
                stopFoldPlaceholderClick(event);
            }
        } else if (type === "touchmove") {
            if (updateFoldPlaceholderTouch(event)) {
                stopFoldPlaceholderClick(event);
            }
        } else if (type === "touchend") {
            if (finishFoldPlaceholderTouch(event)) {
                stopFoldPlaceholderClick(event);
            }
        } else if (type === "touchcancel") {
            foldTapCandidate = null;
        }
    }

    function stopFoldPlaceholderClick(event) {
        if (!event) {
            return;
        }
        if (event.preventDefault) {
            event.preventDefault();
        }
        if (event.stopPropagation) {
            event.stopPropagation();
        }
        if (event.stopImmediatePropagation) {
            event.stopImmediatePropagation();
        }
    }

    function expandFoldFromDomEvent(event) {
        if (!editor || !editor.renderer || !isFoldPlaceholderTarget(event && event.target)) {
            return false;
        }
        var position = editor.renderer.screenToTextCoordinates &&
            editor.renderer.screenToTextCoordinates(event.clientX, event.clientY);
        position = position || editor.getCursorPosition();
        if (expandFoldAt(position.row, position.column) || expandFoldAt(position.row, 0)) {
            cancelAceMobileTouchInteraction("fold_placeholder_click");
            stopFoldPlaceholderClick(event);
            return true;
        }
        return false;
    }

    function positionFromDomEvent(event) {
        if (!editor || !editor.renderer || !editor.renderer.screenToTextCoordinates) {
            return editor && editor.getCursorPosition && editor.getCursorPosition();
        }
        var point = clientPointFromDomEvent(event);
        return editor.renderer.screenToTextCoordinates(
            point && point.clientX,
            point && point.clientY
        );
    }

    function toggleGutterFoldFromDomEvent(event) {
        if (!foldMarkersEnabled || !editor || !session || !isGutterFoldWidgetEvent(event && event.target, event)) {
            return false;
        }
        var position = positionFromDomEvent(event);
        if (!position) {
            return false;
        }
        if (toggleFoldWidgetAt(position.row, event)) {
            gutterFoldClickSuppressUntil = Date.now() + 700;
            stopFoldPlaceholderClick(event);
            callBridge("notifyEvent", [
                "gutterFoldToggled",
                JSON.stringify({ row: position.row })
            ]);
            return true;
        }
        return false;
    }

    function stopGutterFoldClickAfterMouseDown(event) {
        if (Date.now() > gutterFoldClickSuppressUntil) {
            return false;
        }
        if (!isGutterFoldWidgetEvent(event && event.target, event)) {
            return false;
        }
        stopFoldPlaceholderClick(event);
        return true;
    }

    function installFoldPlaceholderClickCapture() {
        if (!editor || !editor.container || !editor.container.addEventListener) {
            return;
        }
        editor.container.addEventListener("mousedown", toggleGutterFoldFromDomEvent, true);
        editor.container.addEventListener("touchstart", toggleGutterFoldFromDomEvent, true);
        editor.container.addEventListener("click", stopGutterFoldClickAfterMouseDown, true);
        ["touchstart", "touchmove", "touchend", "touchcancel"].forEach(function(type) {
            editor.container.addEventListener(type, handleFoldPlaceholderTouch, true);
        });
        editor.container.addEventListener("click", expandFoldFromDomEvent, true);
    }

    function initEditor() {
        if (!global.ace) {
            notifyFatalError({
                message: "ACE core is not loaded",
                phase: "init"
            });
            return;
        }
        if (callBridge("shouldForceJsInitThrow")) {
            throw new Error("Forced ACE init throw");
        }

        global.ace.config.set("basePath", "./src-min-noconflict");
        global.ace.config.set("modePath", "./src-min-noconflict");
        global.ace.config.set("themePath", "./src-min-noconflict");
        global.ace.config.set("workerPath", "./src-min-noconflict");

        Range = global.ace.require("ace/range").Range;
        editor = global.ace.edit("editor");
        // ACE 1.4.12 ext-language_tools references a global `editor` in insertMatch().
        global.editor = editor;
        session = editor.getSession();
        installDocumentLongLineRenderGuard();
        installHistoryOperationHooks();
        installCompletionPopupTouchScrollPatch();
        installAceBuiltInPinchZoomSuppression();
        if (document && document.body && document.body.classList) {
            document.body.classList.add("autojs6-host-selection-action-mode");
        }
        installAceMobileTouchCancellation();
        installDiagnosticGutterTooltipSuppression();
        installFoldPlaceholderClickCapture();
        installDynamicWrapGutterWidthPatch();
        session.setMode("ace/mode/javascript");
        session.setUseWorker(false);
        session.setTabSize(4);
        session.setUseSoftTabs(true);

        var initialTheme = callBridge("getTheme") || "ace/theme/textmate";
        var wordWrapEnabled = callBridgeBoolean("isWordWrapEnabled", false);
        preferredWordWrapEnabled = wordWrapEnabled;
        var wordWrapIndentStyle = callBridgeString("getWordWrapIndentStyle", "continuation");
        var printMarginEnabled = callBridgeBoolean("isPrintMarginEnabled", false);
        var indentGuidesEnabled = callBridgeBoolean("isIndentGuidesEnabled", true);
        lineNumbersEnabled = callBridgeBoolean("isLineNumbersEnabled", true);
        breakpointMarkersEnabled = callBridgeBoolean("isBreakpointMarkersEnabled", true);
        foldMarkersEnabled = callBridgeBoolean("isFoldMarkersEnabled", true);
        gutterWidthMode = normalizeGutterWidthMode(callBridgeString("getGutterWidthMode", "dynamic"));
        var initialFontDescriptor = null;
        var initialFontDescriptorValue = callBridge("getFontDescriptor");
        var hasInitialFontDescriptor = initialFontDescriptorValue !== null &&
            typeof initialFontDescriptorValue !== "undefined" &&
            !!String(initialFontDescriptorValue).trim();
        if (hasInitialFontDescriptor) {
            try {
                initialFontDescriptor = normalizeFontDescriptor(initialFontDescriptorValue);
            } catch (error) {
                notifyRecoverableError("initialFontDescriptor", error);
            }
        }
        var initialFontFamily = initialFontDescriptor ?
            applyFontFamily(fontDescriptorInitialFamily(initialFontDescriptor)) :
            applyFontFamily(hasInitialFontDescriptor ? DEFAULT_FONT_FAMILY : callBridge("getFontFamily") || DEFAULT_FONT_FAMILY);
        applyFontLigaturesEnabled(callBridgeBoolean("isFontLigaturesEnabled", true));
        applyFontStylesEnabled(callBridgeBoolean("isFontStylesEnabled", true));
        applyThemeClass(initialTheme);
        editor.setTheme(initialTheme);
        editor.setOptions({
            enableBasicAutocompletion: true,
            enableLiveAutocompletion: true,
            enableSnippets: true,
            showPrintMargin: printMarginEnabled,
            displayIndentGuides: indentGuidesEnabled,
            showLineNumbers: lineNumbersEnabled,
            showFoldWidgets: foldMarkersEnabled,
            showGutter: hasVisibleGutterContent(),
            fixedWidthGutter: gutterWidthMode === "fixed",
            highlightActiveLine: true,
            indentedSoftWrap: normalizeWordWrapIndentStyle(wordWrapIndentStyle) !== "classic",
            wrap: wordWrapEnabled,
            fontFamily: initialFontFamily
        });
        installMemberCompletionDotTrigger();
        installTouchCursorSelectionGuard();
        session.setUseWrapMode(wordWrapEnabled);
        session.$autojs6LongLineSafetyMode = false;
        setEditorContainerClass("autojs6-hide-breakpoint-markers", !breakpointMarkersEnabled);
        setEditorContainerClass("autojs6-hide-fold-markers", !foldMarkersEnabled);
        setEditorContainerClass("autojs6-hide-gutter", !hasVisibleGutterContent());
        editor.setFontSize("14px");

        if (global.AutoJsAceCompleter) {
            try {
                staticCompleter = global.AutoJsAceCompleter.install(global.ace);
            } catch (error) {
                notifyCompletionError("ACE completion disabled: " + error, error);
            }
        }

        session.on("change", function(delta) {
            if (suppressChange) {
                return;
            }
            if (isSingleInsertedText(delta, ".")) {
                scheduleMemberCompletionAfterDot();
            }
            markTextMutationSelectionMenuSuppressed("session_change:" + String(delta && delta.action || ""));
            var undoManager = getUndoManager();
            dirty = !(undoManager && undoManager.isClean && undoManager.isClean());
            notifyTextChanged(!notifyChangeDelta(delta));
        });

        session.on("changeScrollTop", notifyScrollChanged);
        session.on("changeScrollLeft", notifyScrollChanged);
        installScrollChangeHooks();
        installCursorRevealSuppression();

        editor.selection.on("changeCursor", notifyCursorChanged);
        editor.selection.on("changeSelection", notifySelectionChanged);
        installImeTextInputSelectionWindow();
        global.addEventListener("resize", function() {
            scheduleResize("window_resize");
        });
        document.addEventListener("visibilitychange", handleDocumentVisibilityChanged);
        global.addEventListener("pagehide", function() {
            suppressSelectionActionModeMenu("page_hidden");
        });

        editor.on("guttermousedown", function(event) {
            var target = event.domEvent.target;
            if (!target || target.className.indexOf("ace_gutter-cell") === -1) {
                return;
            }
            if (foldMarkersEnabled && isGutterFoldWidgetEvent(target, event.domEvent)) {
                if (toggleFoldWidgetAt(event.getDocumentPosition().row, event.domEvent)) {
                    event.stop();
                }
                return;
            }
            if (isDiagnosticGutterMarkerEvent(target, event)) {
                toggleDiagnosticMarkerFromGutterEvent(event);
                event.stop();
                return;
            }
            if (!breakpointMarkersEnabled) {
                return;
            }
            if (event.clientX > target.getBoundingClientRect().left + 24) {
                return;
            }
            toggleBreakpoint(event.getDocumentPosition().row);
            event.stop();
        });

        editor.on("click", function(event) {
            var target = event.domEvent && event.domEvent.target;
            if (!isFoldPlaceholderTarget(target)) {
                return;
            }
            var position = event.getDocumentPosition();
            if (expandFoldAt(position.row, position.column)) {
                event.stop();
            }
        });

        global.AutoJsAce = {
            isReady: function() { return !!editor && !!session; },
            getText: function() { return session.getValue(); },
            setText: setText,
            setTextDirty: setTextDirty,
            markClean: markClean,
            markDirty: markDirty,
            refreshState: refreshState,
            replaceAllTextUndoably: replaceAllTextUndoably,
            addUndoableHistoryMarker: addUndoableHistoryMarker,
            insert: insert,
            insertShortcutText: insertShortcutText,
            insertTab: insertTab,
            acceptCompletionOrInsertTab: acceptCompletionOrInsertTab,
            moveCompletionSelectionOrCursor: moveCompletionSelectionOrCursor,
            handleEscapeKey: handleEscapeKey,
            suppressSelectionActionModeMenu: suppressSelectionActionModeMenu,
            suppressTextMutationSelectionMenu: suppressTextMutationSelectionMenu,
            cancelTouchInteraction: cancelAceMobileTouchInteraction,
            insertAtLine: insertAtLine,
            appendTextChunk: appendTextChunk,
            focus: focusEditor,
            jumpTo: jumpTo,
            jumpToOffset: jumpToOffset,
            jumpToStart: jumpToStart,
            jumpToEnd: jumpToEnd,
            jumpToLineStart: jumpToLineStart,
            jumpToLineEnd: jumpToLineEnd,
            jumpToNextLine: jumpToNextLine,
            jumpToPrevLine: jumpToPrevLine,
            moveCursor: moveCursor,
            selectRange: selectRange,
            deleteLine: deleteLine,
            clear: clear,
            toggleComment: toggleComment,
            beautify: beautify,
            replaceSelection: replaceSelection,
            replaceAll: replaceAll,
            setReplacement: function(value) { storedReplacement = value || ""; },
            replaceStoredSelection: function() { replaceSelection(storedReplacement); },
            undo: undo,
            redo: redo,
            setReadOnly: setReadOnlyMode,
            setTheme: setTheme,
            setFontSizeSp: setFontSizeSp,
            setFontSizeSpKeepingFocus: setFontSizeSpKeepingFocus,
            setFontFamily: setFontFamily,
            setFontDescriptor: setFontDescriptor,
            setFontLigaturesEnabled: setFontLigaturesEnabled,
            setFontStylesEnabled: setFontStylesEnabled,
            setWordWrapEnabled: setWordWrapEnabled,
            setWordWrapIndentStyle: setWordWrapIndentStyle,
            setLineNumbersEnabled: setLineNumbersEnabled,
            setPrintMarginEnabled: setPrintMarginEnabled,
            setIndentGuidesEnabled: setIndentGuidesEnabled,
            setBreakpointMarkersEnabled: setBreakpointMarkersEnabled,
            setFoldMarkersEnabled: setFoldMarkersEnabled,
            setGutterWidthMode: setGutterWidthMode,
            scheduleResize: scheduleResize,
            requestFirstPaint: requestFirstPaint,
            restoreScroll: restoreScroll,
            setBreakpoint: function(row, enabled) { return setBreakpoint(row, enabled, true); },
            toggleBreakpoint: toggleBreakpoint,
            clearBreakpoints: clearBreakpoints,
            setDebuggingLine: setDebuggingLine,
            getState: function() { return buildState(true); },
            read: function(uri) { return callBridge("read", [uri || ""]); },
            write: function(uri, text) { return callBridge("write", [uri || "", text || ""]); },
            getLspOptions: function() { return callBridge("getLspOptions") || "{}"; },
            refreshLsp: function() { return refreshLspAndPublish(); },
            getLspState: function() { return getLspStateAndPublish(); },
            getLspHover: function(row, column) {
                var result = lspClient ? lspClient.getHover({ row: row, column: column }) : null;
                publishLspState();
                return result;
            },
            getLspDiagnostics: function() {
                return lspClient && lspClient.getDiagnostics ? lspClient.getDiagnostics() : [];
            },
            validateLsp: function() {
                var results = lspClient && lspClient.validateNow ? lspClient.validateNow() : [];
                publishLspState();
                return results;
            },
            showLspTooltip: function(row, column) {
                return tooltipController ?
                    tooltipController.showAtPosition({ row: row, column: column }) :
                    false;
            },
            hideLspTooltip: function() {
                if (tooltipController) {
                    tooltipController.hide();
                }
            },
            getTooltipState: function() {
                return tooltipController ? tooltipController.getState() : null;
            },
            getSignatureHelp: function(row, column) {
                if (lspClient && lspClient.getSignatureHelp) {
                    var result = lspClient.getSignatureHelp({ row: row, column: column });
                    publishLspState();
                    return result;
                }
                return global.AutoJsAceSignatureHelp ?
                    global.AutoJsAceSignatureHelp.findSignatureHelp(session, { row: row, column: column }) :
                    null;
            },
            showSignatureHelp: function(row, column) {
                return signatureHelpController ?
                    signatureHelpController.showAtPosition({ row: row, column: column }) :
                    false;
            },
            hideSignatureHelp: function() {
                if (signatureHelpController) {
                    signatureHelpController.hide();
                }
            },
            getSignatureHelpState: function() {
                return signatureHelpController ? signatureHelpController.getState() : null;
            }
        };

        if (initialFontDescriptor) {
            setFontDescriptor(initialFontDescriptor);
        }

        if (global.AutoJsAceLspClient) {
            try {
                lspClient = global.AutoJsAceLspClient.install({
                    editor: editor,
                    session: session,
                    getOptions: function() { return callBridge("getLspOptions") || "{}"; },
                    getStaticCompleter: function() {
                        return staticCompleter ||
                            (global.AutoJsAceCompleter &&
                                global.AutoJsAceCompleter.getActiveCompleter &&
                                global.AutoJsAceCompleter.getActiveCompleter());
                    },
                    notifyError: notifyLspError
                });
                installLspCompletionCompleter();
                publishLspState();
                if (global.addEventListener) {
                    global.addEventListener("pagehide", destroyLspClient, { once: true });
                }
            } catch (error) {
                notifyLspError("ACE LSP client disabled: " + error, error);
            }
        }

        if (global.AutoJsAceTooltip) {
            try {
                tooltipController = global.AutoJsAceTooltip.install({
                    editor: editor,
                    session: session,
                    getHover: function(pos) {
                        var result = lspClient ? lspClient.getHover(pos) : null;
                        publishLspState();
                        return result;
                    },
                    notifyError: function(message, error) {
                        notifyRecoverableError("tooltip", error || message);
                    }
                });
            } catch (error) {
                notifyRecoverableError("tooltip", error);
            }
        }

        if (global.AutoJsAceSignatureHelp) {
            try {
                signatureHelpController = global.AutoJsAceSignatureHelp.install({
                    editor: editor,
                    session: session,
                    getSignatureHelp: function(pos) {
                        if (lspClient && lspClient.getSignatureHelp) {
                            var result = lspClient.getSignatureHelp(pos);
                            publishLspState();
                            return result;
                        }
                        return global.AutoJsAceSignatureHelp.findSignatureHelp(session, pos);
                    },
                    notifyError: function(message, error) {
                        notifyRecoverableError(message, error);
                    }
                });
            } catch (error) {
                notifyRecoverableError("signatureHelp", error);
            }
        }

        var json = fullStateJson();
        callBridge("notifyReady", [json]);
        callBridge("notifyEvent", ["ready", json]);
        scheduleResize("init");
        focusEditor("init");
    }

    function startEditor() {
        try {
            initEditor();
        } catch (error) {
            notifyFatalError({
                message: String(error && error.message ? error.message : error),
                source: "autojs6_ace_bridge.js",
                line: -1,
                column: -1,
                stack: error && error.stack ? String(error.stack) : "",
                phase: "init"
            });
            throw error;
        }
    }

    global.onerror = function(message, source, line, column, error) {
        notifyFatalError({
            message: String(message || ""),
            source: String(source || ""),
            line: line || -1,
            column: column || -1,
            stack: error && error.stack ? String(error.stack) : ""
        });
        return false;
    };

    global.onunhandledrejection = function(event) {
        notifyFatalError({
            message: "Unhandled promise rejection",
            reason: String(event && event.reason ? event.reason : "")
        });
        return false;
    };

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", startEditor);
    } else {
        startEditor();
    }
})(window);
