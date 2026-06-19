"use strict";

module.exports = async function run() {
  const diagnostics = require("diagnostics_channel");
  const name = "autojs6.moderncore.diagnostics";
  const channel = diagnostics.channel(name);
  const seen = [];
  function listener(message, channelName) {
    seen.push(channelName + ":" + message.value);
  }
  const before = diagnostics.hasSubscribers(name);
  channel.subscribe(listener);
  const during = diagnostics.hasSubscribers(name) && channel.hasSubscribers === true;
  channel.publish({ value: "ok" });
  channel.unsubscribe(listener);
  const after = diagnostics.hasSubscribers(name);
  console.log("moderncore.diagnostics-channel=PASS|" + [
    before,
    during,
    seen.join(","),
    after,
  ].join("|"));
  return "PASS";
};
