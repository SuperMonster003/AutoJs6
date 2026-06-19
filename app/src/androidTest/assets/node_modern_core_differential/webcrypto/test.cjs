"use strict";

module.exports = async function run() {
  const { Buffer } = require("buffer");
  const crypto = require("crypto");
  const webCrypto = globalThis.crypto || crypto.webcrypto;
  const data = new TextEncoder().encode("abc");
  const digest = Buffer.from(await webCrypto.subtle.digest("SHA-256", data)).toString("hex");
  const random = new Uint8Array(8);
  const randomOk = webCrypto.getRandomValues(random) === random && random.some((value) => value !== 0);
  const uuidOk = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(webCrypto.randomUUID());
  const key = await webCrypto.subtle.importKey(
    "raw",
    new TextEncoder().encode("key"),
    { name: "HMAC", hash: "SHA-256" },
    true,
    ["sign", "verify"],
  );
  const signature = await webCrypto.subtle.sign("HMAC", key, data);
  const verified = await webCrypto.subtle.verify("HMAC", key, signature, data);
  console.log("moderncore.webcrypto=PASS|" + [
    digest,
    randomOk,
    uuidOk,
    verified,
    typeof webCrypto.subtle.generateKey,
    typeof webCrypto.subtle.deriveBits,
  ].join("|"));
  return "PASS";
};
