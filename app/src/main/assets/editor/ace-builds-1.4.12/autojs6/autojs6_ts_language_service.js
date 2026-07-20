(function(global) {
    "use strict";

    var CURRENT_FILE = "file:///autojs6/editor/current.js";
    var CURRENT_DIR = "file:///autojs6/editor";
    var AUTOJS6_LIB = "file:///autojs6/types/lib.autojs6.d.ts";
    var AUTOJS6_LIB_ASSET = "./autojs6/types/lib.autojs6.d.ts";
    var AUTOJS6_EXTRA_LIB = "file:///autojs6/types/lib.autojs6.extra.d.ts";
    var AUTOJS6_EXTRA_LIB_ASSET = "./autojs6/types/lib.autojs6.extra.d.ts";
    var TS_LIB_ROOT = "file:///autojs6/typescript/";
    var TS_LIB_ASSET_ROOT = "./autojs6/typescript/";
    var DEFAULT_LIB = "lib.es2020.d.ts";
    var TS_DIAGNOSTIC_SOURCE = "autojs6-ts";
    var SINGLE_FILE_UNRELIABLE_DIAGNOSTIC_CODES = {
        2307: true,
        2792: true,
        7016: true
    };

    var TS_LIB_NAMES = [
        "lib.d.ts",
        "lib.es2015.collection.d.ts",
        "lib.es2015.core.d.ts",
        "lib.es2015.d.ts",
        "lib.es2015.generator.d.ts",
        "lib.es2015.iterable.d.ts",
        "lib.es2015.promise.d.ts",
        "lib.es2015.proxy.d.ts",
        "lib.es2015.reflect.d.ts",
        "lib.es2015.symbol.d.ts",
        "lib.es2015.symbol.wellknown.d.ts",
        "lib.es2016.array.include.d.ts",
        "lib.es2016.d.ts",
        "lib.es2016.full.d.ts",
        "lib.es2017.d.ts",
        "lib.es2017.full.d.ts",
        "lib.es2017.intl.d.ts",
        "lib.es2017.object.d.ts",
        "lib.es2017.sharedmemory.d.ts",
        "lib.es2017.string.d.ts",
        "lib.es2017.typedarrays.d.ts",
        "lib.es2018.asyncgenerator.d.ts",
        "lib.es2018.asynciterable.d.ts",
        "lib.es2018.d.ts",
        "lib.es2018.full.d.ts",
        "lib.es2018.intl.d.ts",
        "lib.es2018.promise.d.ts",
        "lib.es2018.regexp.d.ts",
        "lib.es2019.array.d.ts",
        "lib.es2019.d.ts",
        "lib.es2019.full.d.ts",
        "lib.es2019.object.d.ts",
        "lib.es2019.string.d.ts",
        "lib.es2019.symbol.d.ts",
        "lib.es2020.bigint.d.ts",
        "lib.es2020.d.ts",
        "lib.es2020.intl.d.ts",
        "lib.es2020.promise.d.ts",
        "lib.es2020.sharedmemory.d.ts",
        "lib.es2020.string.d.ts",
        "lib.es2020.symbol.wellknown.d.ts",
        "lib.es5.d.ts",
        "lib.es6.d.ts",
        "lib.esnext.d.ts",
        "lib.esnext.full.d.ts",
        "lib.esnext.intl.d.ts",
        "lib.esnext.promise.d.ts",
        "lib.esnext.string.d.ts",
        "lib.esnext.weakref.d.ts",
        "lib.scripthost.d.ts"
    ];
    var TS_LIB_NAME_SET = Object.create(null);
    TS_LIB_NAMES.forEach(function(name) {
        TS_LIB_NAME_SET[name] = true;
    });

    function noop() {
    }

    function notify(config, message, error) {
        if (config && typeof config.notifyError === "function") {
            config.notifyError(String(message || "ACE TS language service error"), error);
        }
    }

    function copyArray(value) {
        return Array.isArray(value) ? value.slice(0) : [];
    }

    function hasOwn(object, key) {
        return Object.prototype.hasOwnProperty.call(object, key);
    }

    function normalizeFileName(fileName) {
        fileName = String(fileName || "").replace(/\\/g, "/");
        if (fileName.indexOf(TS_LIB_ROOT) === 0) {
            return fileName;
        }
        if (fileName.indexOf("/autojs6/typescript/") >= 0) {
            return TS_LIB_ROOT + fileName.substring(fileName.lastIndexOf("/") + 1);
        }
        if (fileName.indexOf("/typescript/") >= 0) {
            return TS_LIB_ROOT + fileName.substring(fileName.lastIndexOf("/") + 1);
        }
        if (fileName.indexOf("lib.") === 0 && /\.d\.ts$/.test(fileName)) {
            return TS_LIB_ROOT + fileName;
        }
        if (fileName === "typescriptServices.d.ts" || fileName === "typescript.d.ts" || fileName === "tsserverlibrary.d.ts") {
            return TS_LIB_ROOT + fileName;
        }
        if (fileName.indexOf("/autojs6/types/lib.autojs6.d.ts") >= 0 || /(^|\/)lib\.autojs6\.d\.ts$/.test(fileName)) {
            return AUTOJS6_LIB;
        }
        return fileName || CURRENT_FILE;
    }

    function normalizeDocumentUri(uri) {
        uri = normalizeFileName(uri || CURRENT_FILE);
        return uri || CURRENT_FILE;
    }

    function cleanFileNameForKind(fileName) {
        return String(fileName || "").replace(/[?#].*$/, "").toLowerCase();
    }

    function scriptKindForFileName(ts, fileName) {
        var clean = cleanFileNameForKind(fileName);
        if (/\.json$/.test(clean) && ts.ScriptKind && typeof ts.ScriptKind.JSON !== "undefined") {
            return ts.ScriptKind.JSON;
        }
        if (/\.tsx$/.test(clean) && ts.ScriptKind && typeof ts.ScriptKind.TSX !== "undefined") {
            return ts.ScriptKind.TSX;
        }
        if (/\.jsx$/.test(clean) && ts.ScriptKind && typeof ts.ScriptKind.JSX !== "undefined") {
            return ts.ScriptKind.JSX;
        }
        if ((/\.(?:ts|mts|cts)$/.test(clean) || /\.d\.(?:ts|mts|cts)$/.test(clean)) &&
            ts.ScriptKind && typeof ts.ScriptKind.TS !== "undefined") {
            return ts.ScriptKind.TS;
        }
        return ts.ScriptKind && typeof ts.ScriptKind.JS !== "undefined" ? ts.ScriptKind.JS : undefined;
    }

    function loadTextFromBridge(url) {
        var bridge = global.autojs;
        if (!bridge || typeof bridge.read !== "function") {
            return null;
        }
        try {
            var response = JSON.parse(String(bridge.read(url) || "{}"));
            if (response && response.ok === true && typeof response.text === "string") {
                return response.text;
            }
        } catch (ignore) {
            return null;
        }
        return null;
    }

    function loadText(url) {
        var bridged = loadTextFromBridge(url);
        if (bridged !== null) {
            return bridged;
        }
        if (!global.XMLHttpRequest) {
            return null;
        }
        var xhr = new global.XMLHttpRequest();
        xhr.open("GET", url, false);
        xhr.send(null);
        if ((xhr.status >= 200 && xhr.status < 300) || xhr.status === 0) {
            return String(xhr.responseText || "");
        }
        return null;
    }

    function positionToIndex(session, pos) {
        pos = pos || {};
        if (session && session.doc && typeof session.doc.positionToIndex === "function") {
            return session.doc.positionToIndex(pos, 0);
        }
        var row = Math.max(0, Number(pos.row) || 0);
        var column = Math.max(0, Number(pos.column) || 0);
        var index = 0;
        for (var i = 0; i < row; i++) {
            index += String(session.getLine(i) || "").length + 1;
        }
        return index + column;
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

    function partsToString(parts) {
        if (typeof parts === "string") {
            return parts;
        }
        if (!Array.isArray(parts)) {
            return parts && typeof parts.text === "string" ? parts.text : "";
        }
        return (parts || []).map(function(part) {
            return part && part.text || "";
        }).join("");
    }

    function flattenDiagnostic(ts, diagnostic) {
        if (!diagnostic) {
            return "";
        }
        return ts.flattenDiagnosticMessageText(diagnostic.messageText || "", "\n");
    }

    function diagnosticType(ts, diagnostic) {
        if (!diagnostic || diagnostic.category === ts.DiagnosticCategory.Message) {
            return "info";
        }
        if (diagnostic.category === ts.DiagnosticCategory.Warning) {
            return "warning";
        }
        if (diagnostic.category === ts.DiagnosticCategory.Suggestion) {
            return "info";
        }
        return "error";
    }

    function parameterTextFromSignature(signature, name) {
        signature = String(signature || "");
        name = String(name || "");
        var openIndex = -1;
        if (name) {
            openIndex = signature.indexOf(name + "(");
            if (openIndex >= 0) {
                openIndex += name.length;
            }
        }
        if (openIndex < 0) {
            openIndex = signature.indexOf("(");
        }
        if (openIndex < 0) {
            return null;
        }
        var depth = 0;
        for (var i = openIndex; i < signature.length; i++) {
            var ch = signature.charAt(i);
            if (ch === "(") {
                depth++;
            } else if (ch === ")") {
                depth--;
                if (depth === 0) {
                    return signature.substring(openIndex + 1, i);
                }
            }
        }
        return null;
    }

    function signatureHasParameters(signature, name) {
        var parameters = parameterTextFromSignature(signature, name);
        return parameters === null || parameters.trim().length > 0;
    }

    function literalMemberContextFromLine(line) {
        var match = /((?:"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`|\[[^\]\r\n]*\]|\([^()\r\n]*\)|\{[^{}\r\n]*\}))\.([A-Za-z_$][A-Za-z0-9_$]*)?$/.exec(line);
        return match ? match[1] : "";
    }

    function memberContextFromText(text, offset) {
        var lineStart = Math.max(text.lastIndexOf("\n", offset - 1), text.lastIndexOf("\r", offset - 1)) + 1;
        var line = text.substring(lineStart, offset);
        var match = /((?:[A-Za-z_$][A-Za-z0-9_$]*\.)*[A-Za-z_$][A-Za-z0-9_$]*)\.([A-Za-z_$][A-Za-z0-9_$]*)?$/.exec(line);
        return match ? match[1] : literalMemberContextFromLine(line);
    }

    function isFunctionLikeKind(kind) {
        kind = String(kind || "");
        return kind === "function" || kind === "method" || kind === "constructor";
    }

    function isNoisyCompletionEntry(entry) {
        return String(entry && entry.kind || "").toLowerCase() === "warning";
    }

    function completionSnippet(name, kind, signature) {
        if (!isFunctionLikeKind(kind) || !/^[A-Za-z_$][A-Za-z0-9_$]*$/.test(name || "")) {
            return "";
        }
        return name + (signatureHasParameters(signature, name) ? "(${1})" : "()");
    }

    function matchesCompletionFilter(value, prefix) {
        value = String(value || "").toLowerCase();
        prefix = String(prefix || "").toLowerCase();
        if (!prefix) {
            return true;
        }
        var directIndex = value.indexOf(prefix);
        if (directIndex >= 0) {
            return true;
        }
        var valueIndex = -1;
        for (var i = 0; i < prefix.length; i++) {
            valueIndex = value.indexOf(prefix.charAt(i), valueIndex + 1);
            if (valueIndex < 0) {
                return false;
            }
        }
        return true;
    }

    function annotationFromDiagnostic(ts, text, diagnostic) {
        var start = Number(diagnostic && diagnostic.start) || 0;
        var pos = positionFromIndex(text, start);
        return {
            row: pos.row,
            column: pos.column,
            text: flattenDiagnostic(ts, diagnostic),
            type: diagnosticType(ts, diagnostic),
            raw: String(diagnostic && diagnostic.code || TS_DIAGNOSTIC_SOURCE),
            source: TS_DIAGNOSTIC_SOURCE
        };
    }

    function createLanguageService(config) {
        config = config || {};
        var ts = config.ts || global.ts;
        var files = Object.create(null);
        var versions = Object.create(null);
        var ready = false;
        var reason = "";
        var service = null;
        var currentText = "";
        var currentFile = normalizeDocumentUri(config.documentUri);
        var libraryUris = [];
        var disposed = false;
        var diagnosticsLimit = Math.max(1, Number(config.diagnosticsLimit) || 100);
        var completionLimit = Math.max(1, Number(config.completionLimit) || 300);

        function compilerOptions() {
            return {
                allowJs: true,
                checkJs: !!config.checkJs,
                noEmit: true,
                allowNonTsExtensions: true,
                target: ts.ScriptTarget.ES2020 || ts.ScriptTarget.ES2019 || ts.ScriptTarget.Latest,
                module: ts.ModuleKind.CommonJS,
                moduleResolution: ts.ModuleResolutionKind.NodeJs,
                jsx: ts.JsxEmit && ts.JsxEmit.Preserve,
                lib: [DEFAULT_LIB],
                skipLibCheck: true,
                skipDefaultLibCheck: true
            };
        }

        function addFile(fileName, text) {
            fileName = normalizeFileName(fileName);
            files[fileName] = String(text || "");
            versions[fileName] = versions[fileName] || "0";
            return fileName;
        }

        function bump(fileName) {
            fileName = normalizeFileName(fileName);
            versions[fileName] = String((Number(versions[fileName]) || 0) + 1);
        }

        function readConfiguredText(uri, assetUrl) {
            var libraryTextByUri = config.libraryTextByUri || {};
            if (hasOwn(libraryTextByUri, uri)) {
                return libraryTextByUri[uri];
            }
            return loadText(assetUrl);
        }

        function ensureTsLibrary(fileName) {
            fileName = normalizeFileName(fileName);
            if (hasOwn(files, fileName)) {
                return true;
            }
            if (fileName.indexOf(TS_LIB_ROOT) !== 0) {
                return false;
            }
            var name = fileName.substring(TS_LIB_ROOT.length);
            if (!TS_LIB_NAME_SET[name]) {
                return false;
            }
            var text = readConfiguredText(fileName, TS_LIB_ASSET_ROOT + name);
            if (text === null || text === undefined) {
                return false;
            }
            libraryUris.push(addFile(fileName, text));
            return true;
        }

        function initialize() {
            if (disposed) {
                reason = "typescript language service disposed";
                return false;
            }
            if (ready || reason) {
                return ready;
            }
            if (!ts || typeof ts.createLanguageService !== "function") {
                reason = "typescriptServices.js unavailable";
                return false;
            }
            try {
                ensureTsLibrary(TS_LIB_ROOT + DEFAULT_LIB);
                var autojsText = readConfiguredText(AUTOJS6_LIB, AUTOJS6_LIB_ASSET);
                if (autojsText !== null && autojsText !== undefined) {
                    libraryUris.push(addFile(AUTOJS6_LIB, autojsText));
                }
                var autojsExtraText = readConfiguredText(AUTOJS6_EXTRA_LIB, AUTOJS6_EXTRA_LIB_ASSET);
                if (autojsExtraText !== null && autojsExtraText !== undefined) {
                    libraryUris.push(addFile(AUTOJS6_EXTRA_LIB, autojsExtraText));
                }
                addFile(currentFile, "");
                if (!hasOwn(files, TS_LIB_ROOT + DEFAULT_LIB)) {
                    reason = "TypeScript default lib missing: " + DEFAULT_LIB;
                    return false;
                }
                if (!hasOwn(files, AUTOJS6_LIB)) {
                    reason = "AutoJs6 declaration lib missing";
                    return false;
                }
                service = ts.createLanguageService({
                    getCompilationSettings: compilerOptions,
                    getScriptFileNames: function() {
                        return [
                            currentFile,
                            TS_LIB_ROOT + DEFAULT_LIB,
                            AUTOJS6_LIB,
                            AUTOJS6_EXTRA_LIB
                        ].filter(function(fileName) {
                            return hasOwn(files, fileName);
                        });
                    },
                    getScriptVersion: function(fileName) {
                        return versions[normalizeFileName(fileName)] || "0";
                    },
                    getScriptSnapshot: function(fileName) {
                        fileName = normalizeFileName(fileName);
                        ensureTsLibrary(fileName);
                        return hasOwn(files, fileName) ? ts.ScriptSnapshot.fromString(files[fileName]) : undefined;
                    },
                    getScriptKind: function(fileName) {
                        return scriptKindForFileName(ts, normalizeFileName(fileName));
                    },
                    getCurrentDirectory: function() {
                        return CURRENT_DIR;
                    },
                    getDefaultLibFileName: function() {
                        return TS_LIB_ROOT + DEFAULT_LIB;
                    },
                    readFile: function(fileName) {
                        fileName = normalizeFileName(fileName);
                        ensureTsLibrary(fileName);
                        return hasOwn(files, fileName) ? files[fileName] : undefined;
                    },
                    fileExists: function(fileName) {
                        fileName = normalizeFileName(fileName);
                        return hasOwn(files, fileName) || ensureTsLibrary(fileName);
                    },
                    directoryExists: function() {
                        return true;
                    },
                    getDirectories: function() {
                        return [];
                    },
                    useCaseSensitiveFileNames: function() {
                        return true;
                    },
                    getNewLine: function() {
                        return "\n";
                    }
                });
                ready = true;
                return true;
            } catch (error) {
                reason = String(error && error.message ? error.message : error);
                notify(config, "ACE TS language service initialization failed: " + reason, error);
                return false;
            }
        }

        function setDocumentUri(documentUri) {
            if (disposed) {
                return false;
            }
            var nextFile = normalizeDocumentUri(documentUri);
            if (nextFile === currentFile) {
                return false;
            }
            var previousFile = currentFile;
            currentFile = nextFile;
            if (ready) {
                delete files[previousFile];
                delete versions[previousFile];
                addFile(currentFile, currentText);
                bump(currentFile);
            }
            return true;
        }

        function updateDocument(text, documentUri) {
            if (disposed) {
                return false;
            }
            if (documentUri) {
                setDocumentUri(documentUri);
            }
            if (!initialize()) {
                return false;
            }
            text = String(text || "");
            if (text !== currentText) {
                currentText = text;
                files[currentFile] = currentText;
                bump(currentFile);
            }
            return true;
        }

        function syncSession(session, documentText) {
            return updateDocument(typeof documentText === "string" ? documentText : textFromSession(session));
        }

        function signatureForCompletionEntry(entry, offset) {
            if (!entry || !isFunctionLikeKind(entry.kind) || typeof service.getCompletionEntryDetails !== "function") {
                return "";
            }
            try {
                var details = service.getCompletionEntryDetails(
                    currentFile,
                    offset,
                    entry.name || "",
                    {},
                    entry.source,
                    {}
                );
                return details ? partsToString(details.displayParts) : "";
            } catch (ignore) {
                return "";
            }
        }

        function getCompletions(session, pos, prefix, callback, documentText) {
            callback = typeof callback === "function" ? callback : noop;
            try {
                if (!syncSession(session, documentText)) {
                    callback(null, []);
                    return false;
                }
                var offset = positionToIndex(session, pos);
                var memberContext = memberContextFromText(currentText, offset);
                var result = service.getCompletionsAtPosition(currentFile, offset, {
                    includeExternalModuleExports: false,
                    includeInsertTextCompletions: true,
                    triggerCharacter: memberContext ? "." : undefined
                });
                var completions = (result && result.entries || [])
                    .filter(function(entry) {
                        return !isNoisyCompletionEntry(entry) &&
                            matchesCompletionFilter(entry && entry.name, prefix);
                    })
                    .slice(0, completionLimit)
                    .map(function(entry, index) {
                        var name = entry.name || "";
                        var caption = name;
                        var insertText = entry.insertText || name;
                        var completion = {
                            caption: caption,
                            value: insertText,
                            meta: entry.kind || "ts",
                            score: 1200 - Math.min(index, 300),
                            docText: entry.kindModifiers || "",
                            sortText: entry.sortText || "",
                            autojs6Ts: true
                        };
                        var replacementSpan = entry.replacementSpan;
                        if (replacementSpan && typeof replacementSpan.start === "number") {
                            var replacementStart = Math.max(0, replacementSpan.start);
                            var replacementEnd = replacementStart + Math.max(0, Number(replacementSpan.length) || 0);
                            completion.replaceRange = {
                                start: positionFromIndex(currentText, replacementStart),
                                end: positionFromIndex(currentText, replacementEnd)
                            };
                        }
                        var shouldResolveSignature = memberContext ||
                            (prefix && (
                                matchesCompletionFilter(name, prefix) ||
                                matchesCompletionFilter(caption, prefix)
                            ));
                        var signature = shouldResolveSignature ? signatureForCompletionEntry(entry, offset) : "";
                        var replacementStartsInString = replacementSpan &&
                            /["'`]$/.test(currentText.substring(0, replacementSpan.start));
                        var snippet = !entry.insertText && !replacementStartsInString ?
                            completionSnippet(name, entry.kind, signature) : "";
                        if (snippet) {
                            completion.snippet = snippet;
                        } else if (entry.isSnippet && entry.insertText) {
                            completion.snippet = entry.insertText;
                        }
                        if (signature) {
                            completion.docText = signature;
                        }
                        return completion;
                    });
                callback(null, completions);
                return true;
            } catch (error) {
                notify(config, "ACE TS completion failed: " + error, error);
                callback(error, []);
                return false;
            }
        }

        function getHover(session, pos, documentText) {
            try {
                if (!syncSession(session, documentText)) {
                    return null;
                }
                var offset = positionToIndex(session, pos);
                var info = service.getQuickInfoAtPosition(currentFile, offset);
                if (!info) {
                    return null;
                }
                var display = partsToString(info.displayParts);
                var documentation = partsToString(info.documentation);
                var tags = (info.tags || []).map(function(tag) {
                    return "@" + tag.name + " " + partsToString(tag.text);
                }).join("\n");
                var docText = [display, documentation, tags].filter(Boolean).join("\n");
                if (!docText) {
                    return null;
                }
                var range = null;
                if (info.textSpan) {
                    var start = positionFromIndex(currentText, info.textSpan.start);
                    var end = positionFromIndex(currentText, info.textSpan.start + info.textSpan.length);
                    range = { start: start, end: end };
                }
                return {
                    caption: display || "TypeScript",
                    value: display,
                    meta: "ts",
                    docText: docText,
                    signature: display,
                    range: range
                };
            } catch (error) {
                notify(config, "ACE TS hover failed: " + error, error);
                return null;
            }
        }

        function getSignatureHelp(session, pos, documentText) {
            try {
                if (!syncSession(session, documentText)) {
                    return null;
                }
                var offset = positionToIndex(session, pos);
                var help = service.getSignatureHelpItems(currentFile, offset, {
                    triggerReason: { kind: "invoked" }
                });
                if (!help || !help.items || !help.items.length) {
                    return null;
                }
                var item = help.items[Math.max(0, Number(help.selectedItemIndex) || 0)] || help.items[0];
                var separator = partsToString(item.separatorDisplayParts) || ", ";
                var parameters = (item.parameters || []).map(function(parameter) {
                    return partsToString(parameter.displayParts);
                });
                var signature = partsToString(item.prefixDisplayParts) +
                    parameters.join(separator) +
                    partsToString(item.suffixDisplayParts);
                if (!signature) {
                    return null;
                }
                return {
                    caption: signature.replace(/\(.*/, ""),
                    signature: signature,
                    parameters: parameters,
                    activeParameter: Math.max(0, Number(help.argumentIndex) || 0),
                    docText: partsToString(item.documentation)
                };
            } catch (error) {
                notify(config, "ACE TS signature help failed: " + error, error);
                return null;
            }
        }

        function getDiagnostics(session, documentText) {
            try {
                if (!syncSession(session, documentText)) {
                    return null;
                }
                var diagnostics = []
                    .concat(service.getSyntacticDiagnostics(currentFile) || [])
                    .concat((service.getSemanticDiagnostics(currentFile) || []).filter(function(diagnostic) {
                        return !SINGLE_FILE_UNRELIABLE_DIAGNOSTIC_CODES[Number(diagnostic && diagnostic.code)];
                    }));
                return diagnostics.slice(0, diagnosticsLimit).map(function(diagnostic) {
                    return annotationFromDiagnostic(ts, currentText, diagnostic);
                });
            } catch (error) {
                notify(config, "ACE TS diagnostics failed: " + error, error);
                return null;
            }
        }

        function getState() {
            if (!disposed) {
                initialize();
            }
            return {
                ready: ready,
                reason: reason,
                version: ts && ts.version || "",
                defaultLib: DEFAULT_LIB,
                libraryCount: libraryUris.length,
                currentFile: currentFile,
                diagnosticSource: TS_DIAGNOSTIC_SOURCE
            };
        }

        function dispose() {
            if (disposed) {
                return;
            }
            disposed = true;
            ready = false;
            if (service && typeof service.dispose === "function") {
                try {
                    service.dispose();
                } catch (error) {
                    notify(config, "ACE TS language service disposal failed: " + error, error);
                }
            }
            service = null;
            files = Object.create(null);
            versions = Object.create(null);
            libraryUris = [];
            currentText = "";
            reason = "typescript language service disposed";
        }

        return {
            initialize: initialize,
            setDocumentUri: setDocumentUri,
            updateDocument: updateDocument,
            getCompletions: getCompletions,
            getHover: getHover,
            getSignatureHelp: getSignatureHelp,
            getDiagnostics: getDiagnostics,
            getState: getState,
            dispose: dispose,
            isReady: function() {
                return initialize();
            }
        };
    }

    global.AutoJsAceTsLanguageService = {
        create: createLanguageService,
        constants: {
            currentFile: CURRENT_FILE,
            autojs6Lib: AUTOJS6_LIB,
            autojs6ExtraLib: AUTOJS6_EXTRA_LIB,
            defaultLib: DEFAULT_LIB,
            libraryNames: copyArray(TS_LIB_NAMES),
            diagnosticSource: TS_DIAGNOSTIC_SOURCE
        }
    };
})(window);
