(function(global) {
    "use strict";

    var bridge = global.autojs || {};
    var editor = null;
    var session = null;
    var Range = null;
    var dirty = false;
    var suppressChange = false;
    var breakpoints = [];
    var debugMarker = null;
    var storedReplacement = "";
    var scrollNotifyTimer = null;
    var DEFAULT_FONT_FAMILY = '"JetBrains Mono", monospace';

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
        var state = {
            dirty: dirty,
            canUndo: !!(undoManager && undoManager.hasUndo && undoManager.hasUndo()),
            canRedo: !!(undoManager && undoManager.hasRedo && undoManager.hasRedo()),
            lineCount: session ? session.getLength() : 1,
            cursor: {
                row: position.row || 0,
                column: position.column || 0,
                lineText: getLine(position.row || 0)
            },
            selection: selection,
            selectedText: selection.text,
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

    function stateJson(includeText) {
        return JSON.stringify(buildState(includeText));
    }

    function fullStateJson() {
        return stateJson(true);
    }

    function lightweightStateJson() {
        return stateJson(false);
    }

    function notifyStateChanged(eventName) {
        var json = lightweightStateJson();
        callBridge("notifyStateChanged", [json]);
        callBridge("notifyEvent", [eventName || "stateChanged", json]);
    }

    function notifyScrollChanged() {
        if (scrollNotifyTimer !== null) {
            clearTimeout(scrollNotifyTimer);
        }
        scrollNotifyTimer = setTimeout(function() {
            scrollNotifyTimer = null;
            notifyStateChanged("scrollChanged");
        }, 80);
    }

    function notifyTextChanged(includeText) {
        var json = stateJson(includeText === true);
        callBridge("notifyTextChanged", [json]);
        callBridge("notifyEvent", ["textChanged", json]);
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

    function notifyCursorChanged() {
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

    function notifyBreakpointChanged(row, enabled) {
        var json = lightweightStateJson();
        callBridge("notifyBreakpointChanged", [row, !!enabled, json]);
        callBridge("notifyEvent", ["breakpointChanged", json]);
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
        editor.renderer.scrollCursorIntoView();
        notifyCursorChanged();
        notifyStateChanged("selectionChanged");
    }

    function restoreScroll(firstVisibleLine, firstVisibleColumn) {
        if (!editor || !editor.renderer) {
            return;
        }
        firstVisibleLine = clampRow(firstVisibleLine);
        firstVisibleColumn = Math.max(0, Number(firstVisibleColumn) || 0);
        if (editor.scrollToLine) {
            editor.scrollToLine(firstVisibleLine, false, false, null);
        }
        if (editor.renderer.scrollToX) {
            editor.renderer.scrollToX(firstVisibleColumn);
        }
        notifyStateChanged("scrollRestored");
    }

    function insert(text) {
        editor.insert(text || "");
    }

    function insertAtLine(row, text) {
        session.insert({ row: clampRow(row), column: 0 }, text || "");
    }

    function jumpTo(row, column) {
        row = clampRow(row);
        column = Math.max(0, Number(column) || 0);
        editor.focus();
        editor.gotoLine(row + 1, column, true);
        notifyCursorChanged();
    }

    function jumpToStart() {
        jumpTo(0, 0);
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
        var selection = getSelectionState();
        var next = Math.max(0, Math.min(selection.startOffset + (Number(delta) || 0), session.getValue().length));
        selectRange(next, next);
    }

    function deleteLine() {
        var row = editor.getCursorPosition().row;
        var lineCount = session.getLength();
        var start = { row: row, column: 0 };
        var end = row + 1 < lineCount ? { row: row + 1, column: 0 } : { row: row, column: getLine(row).length };
        session.remove(new Range(start.row, start.column, end.row, end.column));
    }

    function clear() {
        setText("");
    }

    function toggleComment() {
        editor.toggleCommentLines();
        notifyStateChanged("commentChanged");
    }

    function beautify() {
        try {
            var beautifyExtension = global.ace.require("ace/ext/beautify");
            beautifyExtension.beautify(session);
            notifyTextChanged(true);
            notifyStateChanged("beautified");
        } catch (error) {
            notifyError("Beautify failed: " + error);
        }
    }

    function replaceSelection(text) {
        editor.insert(text || "");
        notifyTextChanged();
    }

    function replaceAll(query, replacement, usingRegex) {
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
    }

    function setTheme(theme) {
        editor.setTheme(theme || "ace/theme/textmate");
        notifyStateChanged("themeChanged");
    }

    function setFontSizeSp(size) {
        var sp = Math.max(8, Math.min(Number(size) || 14, 40));
        editor.setFontSize(sp + "px");
        notifyStateChanged("fontSizeChanged");
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
        session = editor.getSession();
        session.setMode("ace/mode/javascript");
        session.setUseWorker(false);
        session.setTabSize(4);
        session.setUseSoftTabs(true);

        editor.setTheme(callBridge("getTheme") || "ace/theme/textmate");
        editor.setOptions({
            enableBasicAutocompletion: true,
            enableLiveAutocompletion: true,
            enableSnippets: true,
            showPrintMargin: false,
            highlightActiveLine: true,
            wrap: false,
            fontFamily: DEFAULT_FONT_FAMILY
        });
        editor.setFontSize("14px");

        if (global.AutoJsAceCompleter) {
            try {
                global.AutoJsAceCompleter.install(global.ace);
            } catch (error) {
                notifyCompletionError("ACE completion disabled: " + error, error);
            }
        }

        session.on("change", function(delta) {
            if (suppressChange) {
                return;
            }
            dirty = true;
            notifyTextChanged(!notifyChangeDelta(delta));
        });

        session.on("changeScrollTop", notifyScrollChanged);
        session.on("changeScrollLeft", notifyScrollChanged);

        editor.selection.on("changeCursor", notifyCursorChanged);

        editor.on("guttermousedown", function(event) {
            var target = event.domEvent.target;
            if (!target || target.className.indexOf("ace_gutter-cell") === -1) {
                return;
            }
            if (event.clientX > target.getBoundingClientRect().left + 24) {
                return;
            }
            toggleBreakpoint(event.getDocumentPosition().row);
            event.stop();
        });

        global.AutoJsAce = {
            isReady: function() { return !!editor && !!session; },
            getText: function() { return session.getValue(); },
            setText: setText,
            setTextDirty: setTextDirty,
            markClean: markClean,
            insert: insert,
            insertAtLine: insertAtLine,
            jumpTo: jumpTo,
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
            undo: function() { editor.undo(); notifyStateChanged("undo"); },
            redo: function() { editor.redo(); notifyStateChanged("redo"); },
            setReadOnly: function(readOnly) { editor.setReadOnly(!!readOnly); notifyStateChanged("readOnlyChanged"); },
            setTheme: setTheme,
            setFontSizeSp: setFontSizeSp,
            restoreScroll: restoreScroll,
            setBreakpoint: function(row, enabled) { return setBreakpoint(row, enabled, true); },
            toggleBreakpoint: toggleBreakpoint,
            clearBreakpoints: clearBreakpoints,
            setDebuggingLine: setDebuggingLine,
            getState: function() { return buildState(true); },
            read: function(uri) { return callBridge("read", [uri || ""]); },
            write: function(uri, text) { return callBridge("write", [uri || "", text || ""]); },
            getLspOptions: function() { return callBridge("getLspOptions") || "{}"; }
        };

        var json = fullStateJson();
        callBridge("notifyReady", [json]);
        callBridge("notifyEvent", ["ready", json]);
        editor.focus();
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
