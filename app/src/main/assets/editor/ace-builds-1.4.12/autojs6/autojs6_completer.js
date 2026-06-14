(function(global) {
    "use strict";

    var IDENTIFIER_REGEX = /[a-zA-Z_0-9$]/;

    function nameOf(item) {
        return item && (item.name || item.key || item.value);
    }

    function startsWithIgnoreCase(value, prefix) {
        value = String(value || "").toLowerCase();
        prefix = String(prefix || "").toLowerCase();
        return value.indexOf(prefix) === 0;
    }

    function toCompletion(item, score, options) {
        var name = nameOf(item);
        var completion = {
            caption: options && options.caption || item.caption || name,
            value: options && options.value || item.value || name,
            meta: options && options.meta || item.meta || item.type || "autojs",
            score: score,
            docText: item.doc || item.summary || ""
        };
        if (item.snippet) {
            completion.snippet = item.snippet;
        } else if (item.value && item.value.indexOf("${") >= 0) {
            completion.snippet = item.value;
        }
        return completion;
    }

    function normalizeProperty(property) {
        return {
            name: property.name || property.key,
            value: property.value,
            type: property.type || (property.variable ? "variable" : "function"),
            doc: property.doc || property.summary || "",
            meta: property.meta
        };
    }

    function normalizeIndices(indices) {
        var normalized = {
            globals: [],
            modules: {},
            snippets: indices && indices.snippets || []
        };

        if (Array.isArray(indices)) {
            indices.forEach(function(moduleJson) {
                var moduleName = moduleJson.name;
                var properties = moduleJson.properties || [];
                if (moduleName !== "globals" && moduleName !== "global") {
                    normalized.globals.push({
                        name: moduleName,
                        type: "module",
                        doc: moduleJson.summary || ""
                    });
                    normalized.modules[moduleName] = properties.map(normalizeProperty);
                }
                properties.forEach(function(property) {
                    if (property.global) {
                        normalized.globals.push(normalizeProperty(property));
                    }
                });
            });
            return normalized;
        }

        indices = indices || {};
        normalized.globals = indices.globals || [];
        if (Array.isArray(indices.modules)) {
            indices.modules.forEach(function(moduleJson) {
                normalized.modules[moduleJson.name] = (moduleJson.properties || []).map(normalizeProperty);
            });
        } else {
            normalized.modules = indices.modules || {};
        }
        return normalized;
    }

    function findMemberContext(session, pos) {
        var line = session.getLine(pos.row).substring(0, pos.column);
        var match = /([A-Za-z_$][A-Za-z0-9_$]*)\.([A-Za-z_$][A-Za-z0-9_$]*)?$/.exec(line);
        if (!match) {
            return null;
        }
        return {
            moduleName: match[1],
            memberPrefix: match[2] || ""
        };
    }

    function retrievePrecedingIdentifier(text, pos) {
        var buf = [];
        for (var i = pos - 1; i >= 0; i--) {
            if (IDENTIFIER_REGEX.test(text[i]) || (i === pos - 1 && text[i] === ".")) {
                buf.push(text[i]);
            } else {
                break;
            }
        }
        return buf.reverse().join("");
    }

    function collect(items, prefix, score, mapper) {
        var completions = [];
        (items || []).forEach(function(item) {
            var name = nameOf(item);
            if (name && startsWithIgnoreCase(name, prefix)) {
                completions.push(mapper ? mapper(item, name) : toCompletion(item, score));
            }
        });
        return completions;
    }

    function dedupe(completions) {
        var seen = Object.create(null);
        return completions.filter(function(item) {
            var key = item.caption || item.value || item.snippet || "";
            if (seen[key]) {
                return false;
            }
            seen[key] = true;
            return true;
        });
    }

    function createCompleter(indices) {
        var source = normalizeIndices(indices);
        return {
            retrievePrecedingIdentifier: retrievePrecedingIdentifier,

            getCompletions: function(editor, session, pos, prefix, callback) {
                var memberContext = findMemberContext(session, pos);
                if (memberContext && source.modules[memberContext.moduleName]) {
                    var replaceWithQualifiedName = (prefix || "").indexOf(".") >= 0;
                    callback(null, dedupe(collect(
                        source.modules[memberContext.moduleName],
                        memberContext.memberPrefix,
                        1000,
                        function(item, name) {
                            var qualifiedName = memberContext.moduleName + "." + name;
                            return toCompletion(item, 1000, {
                                caption: replaceWithQualifiedName ? qualifiedName : name,
                                value: replaceWithQualifiedName ? qualifiedName : name,
                                meta: memberContext.moduleName
                            });
                        }
                    )));
                    return;
                }

                if (!prefix) {
                    callback(null, []);
                    return;
                }

                callback(null, dedupe(
                    collect(source.snippets, prefix, 950)
                        .concat(collect(source.globals, prefix, 900))
                ));
            },

            getDocTooltip: function(item) {
                if (!item || !item.docText) {
                    return null;
                }
                return { docText: item.docText };
            }
        };
    }

    global.AutoJsAceCompleter = {
        install: function(ace) {
            var languageTools = ace.require("ace/ext/language_tools");
            languageTools.addCompleter(createCompleter(global.AutoJsAceIndices || {}));
        }
    };
})(window);
