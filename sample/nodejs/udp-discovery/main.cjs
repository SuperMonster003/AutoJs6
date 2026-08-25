"nodejs";
"use strict";

const dgram = require("node:dgram");
const server = dgram.createSocket("udp4");
const client = dgram.createSocket("udp4");
let finished = false;

const timeout = setTimeout(() => finishError(new Error("UDP loopback timed out")), 10000);

function closeSockets() {
  try { client.close(); } catch (_) {}
  try { server.close(); } catch (_) {}
}

function finishError(error) {
  if (finished) return;
  finished = true;
  clearTimeout(timeout);
  console.error("sample.udp-discovery.error=" + error.message);
  process.exitCode = 1;
  closeSockets();
}

server.on("error", finishError);
client.on("error", finishError);

server.on("message", (message, remote) => {
  if (message.toString() !== "discover:autojs6") {
    finishError(new Error("unexpected discovery payload"));
    return;
  }
  server.send("autojs6:available", remote.port, remote.address);
});

client.on("message", (message) => {
  if (message.toString() !== "autojs6:available") {
    finishError(new Error("unexpected discovery reply"));
    return;
  }
  if (finished) return;
  finished = true;
  clearTimeout(timeout);
  console.log("sample.udp-discovery.reply=" + message.toString());
  console.log("sample.udp-discovery=PASS");
  closeSockets();
});

server.bind(0, "127.0.0.1", () => {
  client.send("discover:autojs6", server.address().port, "127.0.0.1");
});
