"nodejs";

try {
  const inspector = require("inspector");
  const session = new inspector.Session();
  session.connect();
  const post = (method, params) => new Promise((resolve, reject) => {
    session.post(method, params || {}, (error, result) => error ? reject(error) : resolve(result || {}));
  });
  (async () => {
    await post("Profiler.enable");
    await post("Profiler.start");
    let total = 0;
    for (let index = 0; index < 100000; index += 1) total += index;
    const stopped = await post("Profiler.stop");
    session.disconnect();
    console.log("sample.cpu-profile.nodes=" + !!(stopped.profile && stopped.profile.nodes.length));
    console.log("sample.cpu-profile=PASS");
  })().catch((error) => setImmediate(() => { throw error; }));
} catch (error) {
  console.log("sample.cpu-profile.debug-request-required=" + (error && (error.autojs6Code || error.code || error.name)));
  console.log("sample.cpu-profile=PASS");
}
