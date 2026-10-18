(function(action, key, url) {
    "use strict";
    var root = globalThis, state = root[key];
    if (action === "start") {
        if (state) throw new Error("A page download with this identity already exists");
        state = {busy: true, reader: null, data: null, offset: 0, done: false, error: null, mimeType: "", totalBytes: -1};
        Object.defineProperty(root, key, {value: state, configurable: true});
        fetch(url).then(function(response) {
            if (!response.ok) throw new Error("Page download HTTP " + response.status);
            if (!response.body || !response.body.getReader) throw new Error("This WebView does not support streamed page downloads");
            state.mimeType = response.headers.get("Content-Type") || "application/octet-stream";
            var length = response.headers.get("Content-Length");
            if (length !== null && /^\d+$/.test(length)) state.totalBytes = Number(length);
            state.reader = response.body.getReader();
            if (root[key] !== state) return state.reader.cancel();
            state.busy = false;
        }).catch(function(error) {state.error = String(error); state.busy = false;});
        return true;
    }
    if (action === "cancel") {
        delete root[key];
        if (state && state.reader) Promise.resolve(state.reader.cancel()).catch(function() {});
        return true;
    }
    if (!state) return {error: "The source page changed"};
    if (state.error) return {error: state.error};
    var result = {mimeType: state.mimeType, totalBytes: state.totalBytes};
    if (state.data) {
        var end = Math.min(state.offset + 32768, state.data.length);
        result.base64 = btoa(String.fromCharCode.apply(null, state.data.subarray(state.offset, end)));
        state.offset = end;
        if (end === state.data.length) {state.data = null; state.offset = 0;}
        return result;
    }
    if (state.done) {result.done = true; return result;}
    if (!state.busy && state.reader) {
        state.busy = true;
        state.reader.read().then(function(chunk) {
            state.done = chunk.done;
            if (!chunk.done && chunk.value.length) state.data = chunk.value;
            state.busy = false;
        }).catch(function(error) {state.error = String(error); state.busy = false;});
    }
    result.pending = true;
    return result;
})
