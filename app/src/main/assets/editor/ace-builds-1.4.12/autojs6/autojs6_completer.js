(function(global) {
    "use strict";

    function toCompletion(item, score) {
        return {
            caption: item.name,
            value: item.value || item.name,
            meta: item.meta || item.type || "autojs",
            score: score,
            docText: item.doc || ""
        };
    }

    function createCompleter(indices) {
        return {
            getCompletions: function(editor, session, pos, prefix, callback) {
                var normalized = (prefix || "").toLowerCase();
                if (!normalized) {
                    callback(null, []);
                    return;
                }

                var completions = [];
                (indices.globals || []).forEach(function(item) {
                    if (item.name.toLowerCase().indexOf(normalized) === 0) {
                        completions.push(toCompletion(item, 900));
                    }
                });
                (indices.snippets || []).forEach(function(item) {
                    if (item.name.toLowerCase().indexOf(normalized) === 0) {
                        completions.push(toCompletion(item, 850));
                    }
                });
                callback(null, completions);
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
