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

    function buildState() {
        var position = editor ? editor.getCursorPosition() : { row: 0, column: 0 };
        var undoManager = getUndoManager();
        return {
            text: session ? session.getValue() : "",
            dirty: dirty,
            canUndo: !!(undoManager && undoManager.hasUndo && undoManager.hasUndo()),
            canRedo: !!(undoManager && undoManager.hasRedo && undoManager.hasRedo()),
            lineCount: session ? session.getLength() : 1,
            cursor: {
                row: position.row || 0,
                column: position.column || 0,
                lineText: getLine(position.row || 0)
            },
            breakpoints: getBreakpoints()
        };
    }

    function stateJson() {
        return JSON.stringify(buildState());
    }

    function notifyStateChanged(eventName) {
        var json = stateJson();
        callBridge("notifyStateChanged", [json]);
        callBridge("notifyEvent", [eventName || "stateChanged", json]);
    }

    function notifyTextChanged() {
        var json = stateJson();
        callBridge("notifyTextChanged", [json]);
        callBridge("notifyEvent", ["textChanged", json]);
    }

    function notifyCursorChanged() {
        if (!editor) {
            return;
        }
        var position = editor.getCursorPosition();
        var json = stateJson();
        callBridge("notifyCursorChanged", [
            getLine(position.row),
            position.row || 0,
            position.column || 0,
            json
        ]);
        callBridge("notifyEvent", ["cursorChanged", json]);
    }

    function notifyBreakpointChanged(row, enabled) {
        var json = stateJson();
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
        notifyTextChanged();
        notifyCursorChanged();
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
            notifyError("ACE core is not loaded");
            return;
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
            wrap: false
        });
        editor.setFontSize("14px");

        if (global.AutoJsAceCompleter) {
            global.AutoJsAceCompleter.install(global.ace);
        }

        session.on("change", function() {
            if (suppressChange) {
                return;
            }
            dirty = true;
            notifyTextChanged();
        });

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
            getText: function() { return session.getValue(); },
            setText: setText,
            insert: insert,
            insertAtLine: insertAtLine,
            jumpTo: jumpTo,
            undo: function() { editor.undo(); notifyStateChanged("undo"); },
            redo: function() { editor.redo(); notifyStateChanged("redo"); },
            setReadOnly: function(readOnly) { editor.setReadOnly(!!readOnly); notifyStateChanged("readOnlyChanged"); },
            setTheme: setTheme,
            setFontSizeSp: setFontSizeSp,
            toggleBreakpoint: toggleBreakpoint,
            clearBreakpoints: clearBreakpoints,
            setDebuggingLine: setDebuggingLine,
            getState: buildState,
            read: function(uri) { return callBridge("read", [uri || ""]); },
            write: function(uri, text) { return callBridge("write", [uri || "", text || ""]); },
            getLspOptions: function() { return callBridge("getLspOptions") || "{}"; }
        };

        var json = stateJson();
        callBridge("notifyReady", [json]);
        callBridge("notifyEvent", ["ready", json]);
        editor.focus();
    }

    global.onerror = function(message, source, line, column) {
        notifyError([message, source, line, column].join(":"));
        return false;
    };

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initEditor);
    } else {
        initEditor();
    }
})(window);
