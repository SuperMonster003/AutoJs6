"nodejs";

function finish(label) {
  console.log("sample.http-client-compat.result=" + label);
  console.log("sample.http-client-compat=PASS");
}

let http;
try {
  http = require("http");
} catch (error) {
  finish("builtin-denied:" + (error && (error.autojs6Code || error.code || error.name)));
  return;
}

const agent = new http.Agent({ keepAlive: false, maxSockets: 1 });
const req = http.request(
  "http://127.0.0.1:9/",
  { agent, timeout: 750 },
  (res) => {
    res.resume();
    finish("response:" + res.statusCode);
  },
);
req.once("error", (error) => {
  finish("request-error:" + (error && (error.autojs6Code || error.code || error.name)));
});
req.once("timeout", () => {
  req.destroy();
  finish("timeout");
});
req.end();
