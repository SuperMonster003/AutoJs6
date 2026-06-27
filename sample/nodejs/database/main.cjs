"nodejs";

function codeOf(error) {
  return String(error && (error.code || error.autojs6Code || error.name || "ERROR"));
}

(async function main() {
  const database = require("database");
  console.log("sample.database.provider=sqlite-bridge");

  try {
    const db = await database.open("sample_phase7", { timeoutMs: 1000 });
    await db.exec("CREATE TABLE IF NOT EXISTS items (name TEXT, value INTEGER)", [], { timeoutMs: 1000 });
    await db.run("INSERT INTO items (name, value) VALUES (?, ?)", ["alpha", 1], { timeoutMs: 1000 });
    const rows = await db.all("SELECT name, value FROM items WHERE value >= ?", [1], { timeoutMs: 1000 });
    await db.close({ timeoutMs: 1000 });
    console.log("sample.database.rows=" + rows.length);
  } catch (error) {
    console.log("sample.database.unavailable=" + codeOf(error));
  }

  console.log("sample.database=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
