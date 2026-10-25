// Calls of the modules of roadmap P3.5, second group (http, web, WebSocket), appended to bench/calls_prelude.js by
// device_bench.py --calls http. They reach the modules of the script module layer (with its switch off they reached the
// old augment ones until P5.2). "ok" cases make no connection (a request is only built, a constant is read); the "fail"
// cases fail with an argument error before any connection, compared with the "ref" cases of the prelude.
time("ok http.buildRequest", () => { try { http.buildRequest("http://example.com/", { method: "GET" }); } catch (e) { } });
time("ok http.client", () => { try { http.client(); } catch (e) { } });
time("ok WebSocket.CODE_CLOSE_NORMAL", () => { try { WebSocket.CODE_CLOSE_NORMAL; } catch (e) { } });
time("fail http.get-arity", () => { try { http.get(); } catch (e) { } });
time("fail http.get-type", () => { try { http.get("http://127.0.0.1:9/", 5); } catch (e) { } });
time("fail http.request-type", () => { try { http.request("http://127.0.0.1:9/", {}); } catch (e) { } });
time("fail http.post-callback", () => { try { http.post("http://127.0.0.1:9/", {}, null, 1); } catch (e) { } });
time("fail newWebSocket-arity", () => { try { newWebSocket(); } catch (e) { } });
time("fail web.newWebSocket-type", () => { try { web.newWebSocket("ws://127.0.0.1:9/", 5); } catch (e) { } });
