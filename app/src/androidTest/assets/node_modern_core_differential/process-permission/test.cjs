"use strict";

module.exports = async function run() {
  const permission = process.permission;
  if (!permission) {
    console.log("moderncore.process-permission=DESKTOP_ABSENT|undefined");
    return "PASS";
  }
  const query = permission.query();
  const summary = [
    typeof permission,
    typeof permission.has,
    typeof permission.query,
    permission.model,
    permission.exactNodePermissionModel,
    permission.has("fs.read"),
    permission.has("fs.write"),
    permission.has("network"),
    permission.has("child_process"),
    Array.isArray(query.scopeNames),
    query.manifest && query.manifest.enforced,
  ].join("|");
  console.log("moderncore.process-permission=AUTOJS6_FACADE|" + summary);
  return "PASS";
};
