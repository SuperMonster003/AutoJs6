"nodejs";

const provider = { statusCode: "available", activeResources: [] };
const pass = "sample.provider-health=PASS";
console.log(provider.statusCode === "available" ? pass : "sample.provider-health=FAIL");
