"use strict";

function codeOf(error) {
  return String(error && (error.autojs6Code || error.code || error.name || ""));
}

module.exports = async function run() {
  let database;
  try {
    database = require("database");
  } catch (error) {
    const moduleBuiltin = require("module");
    console.log("moderncore.database=DESKTOP_SQLITE_BUILTIN|" + [
      moduleBuiltin.isBuiltin("node:sqlite"),
      "database:" + codeOf(error),
    ].join("|"));
    return "PASS";
  }

  const bridge = require("autojs6:bridge");
  const requests = [];
  bridge.__test.setTransport({
    postMessage(message) {
      const request = JSON.parse(message);
      requests.push(request.module + "." + request.method);
      let result = true;
      if (request.method === "open") {
        result = { id: "modern-core-db", name: request.args[0].name };
      } else if (request.method === "get") {
        result = { value: 1 };
      }
      setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
        id: request.id,
        ok: true,
        result,
      })));
    },
  });

  let nodeSqliteDenied = "";
  try {
    require("node:sqlite");
    nodeSqliteDenied = "allowed";
  } catch (error) {
    nodeSqliteDenied = codeOf(error);
  }

  const db = await database.open("modern_core_differential", { timeoutMs: 1000 });
  await db.exec("CREATE TABLE items(value INTEGER)", [], { timeoutMs: 1000 });
  const row = await db.get("SELECT 1 AS value", [], { timeoutMs: 1000 });
  const closed = await db.close({ timeoutMs: 1000 });
  console.log("moderncore.database=AUTOJS6_CONTROLLED|" + [
    typeof database.open,
    row.value,
    closed,
    nodeSqliteDenied,
    requests.join(","),
    bridge.__test.pendingCount(),
  ].join("|"));
  return "PASS";
};
