(function(global) {
    "use strict";

    global.AutoJsAceIndices = {
        globals: [
            { name: "app", type: "module", doc: "Application and intent helpers" },
            { name: "console", type: "module", doc: "Console logging API" },
            { name: "device", type: "module", doc: "Device information and controls" },
            { name: "dialogs", type: "module", doc: "Material dialog helpers" },
            { name: "engines", type: "module", doc: "Script engine management" },
            { name: "files", type: "module", doc: "File system helpers" },
            { name: "floaty", type: "module", doc: "Floating window helpers" },
            { name: "http", type: "module", doc: "HTTP client API" },
            { name: "images", type: "module", doc: "Image processing helpers" },
            { name: "threads", type: "module", doc: "Thread helpers" },
            { name: "ui", type: "module", doc: "UI DSL namespace" }
        ],
        snippets: [
            { name: "toast", value: "toast(${1:message});", meta: "autojs" },
            { name: "log", value: "console.log(${1:value});", meta: "autojs" },
            { name: "sleep", value: "sleep(${1:1000});", meta: "autojs" },
            { name: "click", value: "click(${1:x}, ${2:y});", meta: "autojs" },
            { name: "requestScreenCapture", value: "requestScreenCapture();", meta: "autojs" }
        ]
    };
})(window);
