(function(global) {
    "use strict";

    var bridge = global.autojs || {};
    var editor = null;
    var session = null;
    var Range = null;
    var staticCompleter = null;
    var lspClient = null;
    var tooltipController = null;
    var signatureHelpController = null;
    var dirty = false;
    var suppressChange = false;
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
    var fontMetricsRefreshTimer = null;
    var fontMetricsRefreshFrameScheduled = false;
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
    var DEFAULT_FONT_FAMILY = '"JetBrains Mono", "Source Code Pro", monospace';
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
    var MIN_FONT_SIZE_SP = 4;
    var MAX_FONT_SIZE_SP = 96;

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
        var dark = String(theme || "").toLowerCase().indexOf("night") >= 0 ||
            String(theme || "").toLowerCase().indexOf("dark") >= 0;
        document.documentElement.classList.toggle("ace-theme-dark", dark);
        document.body.classList.toggle("ace-theme-dark", dark);
    }

    function notifyFirstPaint() {
        if (firstPaintNotified) {
            return;
        }
        firstPaintNotified = true;
        document.body.classList.add("ace-ready");
        callBridge("notifyEvent", ["firstPaint", lightweightStateJson()]);
    }

    function afterTwoFrames(callback) {
        var raf = global.requestAnimationFrame || function(run) { return setTimeout(run, 16); };
        raf(function() {
            raf(callback);
        });
    }

    function requestFirstPaint(reason) {
        scheduleResize(reason || "first_paint");
        afterTwoFrames(notifyFirstPaint);
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
        var selection = getSelectionState();
        var readOnly = isReadOnly();
        var state = {
            dirty: dirty,
            readOnly: readOnly,
            canUndo: !readOnly && !!(undoManager && undoManager.hasUndo && undoManager.hasUndo()),
            canRedo: !readOnly && !!(undoManager && undoManager.hasRedo && undoManager.hasRedo()),
            lineCount: session ? session.getLength() : 1,
            cursor: {
                row: position.row || 0,
                column: position.column || 0,
                lineText: getLine(position.row || 0)
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

    function getSelectionState() {
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
            text: session.getTextRange(range)
        };
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

    function startBridgePinchZoom(distance, focus) {
        bridgePinchZoomActive = true;
        bridgePinchZoomStartDistance = distance;
        bridgePinchZoomBaseFontSizeSp = currentFontSizeSp;
        bridgePinchZoomCurrentFontSizeSp = currentFontSizeSp;
        bridgePinchZoomLastFocus = focus;
        cursorRevealSuppressUntil = Math.max(cursorRevealSuppressUntil, Date.now() + 1200);
        cancelAceMobileTouchInteraction("pinch_zoom_begin");
        notifyBridgePinchZoom("begin", currentFontSizeSp, focus);
    }

    function applyBridgePinchZoom(scale, focus) {
        if (!bridgePinchZoomActive || !scale || scale <= 0) {
            return;
        }
        var targetSize = clampFontSizeSp(bridgePinchZoomBaseFontSizeSp * scale);
        if (Math.abs(targetSize - bridgePinchZoomCurrentFontSizeSp) < 0.025) {
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
            cursorRevealSuppressUntil = Math.max(cursorRevealSuppressUntil, Date.now() + 1200);
            cancelAceMobileTouchInteraction("pinch_zoom_end");
            stopDomTouchEvent(event);
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
        ["touchend", "touchcancel"].forEach(function(type) {
            editor.container.addEventListener(type, finishBridgePinchZoom, true);
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
        callBridge("notifyEvent", [eventName || "stateChanged", json]);
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
        callBridge("notifyEvent", ["textChanged", json]);
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
            getLine(position.row),
            position.row || 0,
            position.column || 0,
            json
        ]);
        callBridge("notifyEvent", ["cursorChanged", json]);
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
        if (!hasActiveSelection()) {
            return false;
        }
        reason = String(reason || "").toLowerCase();
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
        dirty = false;
        notifyStateChanged("cleanChanged");
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

    function setText(text) {
        suppressChange = true;
        session.setValue(text || "");
        suppressChange = false;
        dirty = false;
        var undoManager = getUndoManager();
        if (undoManager && undoManager.reset) {
            undoManager.reset();
        }
        editor.clearSelection();
        editor.moveCursorTo(0, 0);
        notifyTextChanged(true);
        notifyCursorChanged();
    }

    function setTextDirty(text) {
        suppressChange = true;
        session.setValue(text || "");
        suppressChange = false;
        dirty = true;
        notifyTextChanged(true);
        notifyCursorChanged();
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

    function isCompletionPopupOpen() {
        var completer = editor && editor.completer;
        var popup = completer && completer.popup;
        return !!(completer && completer.activated && popup && popup.isOpen);
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
        var renderer = editor.renderer;
        var x = Number(focus && focus.x);
        var y = Number(focus && focus.y);
        if (!isFinite(x) || !isFinite(y)) {
            return applyFontSizeSp(size, { reason: "pinch_font_size", deferRefresh: false });
        }
        cursorRevealSuppressUntil = Math.max(cursorRevealSuppressUntil, Date.now() + 1200);
        var anchor = renderer.screenToTextCoordinates(x, y);
        var appliedSize = applyFontSizeSp(size, { reason: "pinch_font_size", deferRefresh: false });
        var after = renderer.textToScreenCoordinates(anchor.row, anchor.column);
        scrollByDelta(after.pageX - x, after.pageY - y);
        return appliedSize;
    }

    function setFontFamily(fontFamily) {
        editor.setOption("fontFamily", fontFamily || DEFAULT_FONT_FAMILY);
        notifyStateChanged("fontFamilyChanged");
    }

    function setWordWrapEnabled(enabled) {
        enabled = !!enabled;
        session.setUseWrapMode(enabled);
        editor.setOption("wrap", enabled);
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
        installAceBuiltInPinchZoomSuppression();
        if (document && document.body && document.body.classList) {
            document.body.classList.add("autojs6-host-selection-action-mode");
        }
        installAceMobileTouchCancellation();
        installFoldPlaceholderClickCapture();
        installDynamicWrapGutterWidthPatch();
        session.setMode("ace/mode/javascript");
        session.setUseWorker(false);
        session.setTabSize(4);
        session.setUseSoftTabs(true);

        var initialTheme = callBridge("getTheme") || "ace/theme/textmate";
        var wordWrapEnabled = callBridgeBoolean("isWordWrapEnabled", false);
        var wordWrapIndentStyle = callBridgeString("getWordWrapIndentStyle", "continuation");
        lineNumbersEnabled = callBridgeBoolean("isLineNumbersEnabled", true);
        breakpointMarkersEnabled = callBridgeBoolean("isBreakpointMarkersEnabled", true);
        foldMarkersEnabled = callBridgeBoolean("isFoldMarkersEnabled", true);
        gutterWidthMode = normalizeGutterWidthMode(callBridgeString("getGutterWidthMode", "dynamic"));
        applyThemeClass(initialTheme);
        editor.setTheme(initialTheme);
        editor.setOptions({
            enableBasicAutocompletion: true,
            enableLiveAutocompletion: true,
            enableSnippets: true,
            showPrintMargin: false,
            showLineNumbers: lineNumbersEnabled,
            showFoldWidgets: foldMarkersEnabled,
            showGutter: hasVisibleGutterContent(),
            fixedWidthGutter: gutterWidthMode === "fixed",
            highlightActiveLine: true,
            indentedSoftWrap: normalizeWordWrapIndentStyle(wordWrapIndentStyle) !== "classic",
            wrap: wordWrapEnabled,
            fontFamily: callBridge("getFontFamily") || DEFAULT_FONT_FAMILY
        });
        session.setUseWrapMode(wordWrapEnabled);
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
            markTextMutationSelectionMenuSuppressed("session_change:" + String(delta && delta.action || ""));
            dirty = true;
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
            insert: insert,
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
            setWordWrapEnabled: setWordWrapEnabled,
            setWordWrapIndentStyle: setWordWrapIndentStyle,
            setLineNumbersEnabled: setLineNumbersEnabled,
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
            refreshLsp: function() { return lspClient ? lspClient.refresh() : null; },
            getLspState: function() { return lspClient ? lspClient.getState() : null; },
            getLspHover: function(row, column) {
                return lspClient ? lspClient.getHover({ row: row, column: column }) : null;
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
                        return lspClient ? lspClient.getHover(pos) : null;
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
