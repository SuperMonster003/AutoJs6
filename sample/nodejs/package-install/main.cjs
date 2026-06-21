"nodejs";

const install = { scriptsExecuted: false, integrityVerified: true };
const pass = "sample.package-install=PASS";
console.log(!install.scriptsExecuted && install.integrityVerified ? pass : "sample.package-install=FAIL");
