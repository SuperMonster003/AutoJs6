"nodejs";

const compat = require("autojs6:compat");

function expectBlocked(apiName, reason) {
  const error = compat.blockedByDesign(apiName, reason);
  if (!error || error.code !== compat.errorCodes.blockedByDesign) {
    throw new Error(`${apiName} did not return a blocked-by-design marker`);
  }
  return error;
}

async function main() {
  const error = expectBlocked(
    "threads.start",
    "Shared Rhino runtime threads are replaced by async flow, execution slots, or work_manager.",
  );
  console.log(error.code);
}

main().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exitCode = 1;
});
