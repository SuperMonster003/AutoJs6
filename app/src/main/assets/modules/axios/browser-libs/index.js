(function() {
    let XMLHttpRequest = require("modules/axios/browser-libs/XMLHttpRequest.js");
    let EventTarget = require("modules/axios/browser-libs/EventTarget.js");
    let Event = require("modules/axios/browser-libs/Event.js");
    let FormData = require("modules/axios/browser-libs/FormData.js");
    let Blob = require("modules/axios/browser-libs/Blob.js");

    let window = {}
    let _Object = Object.create(Object)
    _Object.getOwnPropertyDescriptors = function(obj) {
        var descriptors = {};
        Object.getOwnPropertyNames(obj).forEach(function(key) {
            descriptors[key] = Object.getOwnPropertyDescriptor(obj, key);
        });
        return descriptors;
    }

    let _setTimeout = (fn, time) => setTimeout(fn, time || 0)
    module.exports = {
        Object:_Object,
        window,
        FormData,
        XMLHttpRequest,
        Blob,
        EventTarget,
        Event,
        setTimeout:_setTimeout,
    }
})()