"nodejs";

const compat = require("autojs6:compat");

function expectBlocked(apiName, reason) {
  const error = compat.blockedByDesign(apiName, reason);
  if (
    !error ||
    error.code !== compat.errorCodes.blockedByDesign ||
    error.code !== "ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN"
  ) {
    throw new Error(`${apiName} did not return a blocked-by-design marker`);
  }
  return error;
}

async function main() {
  const windowError = expectBlocked(
    "floaty.window",
    "Node does not expose Rhino XML overlay windows or live floaty wrappers.",
  );
  const rawWindowError = expectBlocked(
    "floaty.rawWindow",
    "Node does not expose raw WindowManager overlay flags or live raw floaty wrappers.",
  );
  console.log(windowError.code);
  console.log(rawWindowError.code);
}

main().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exitCode = 1;
});
