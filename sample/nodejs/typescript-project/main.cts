"nodejs";

const project: { language: string; cache: boolean } = { language: "typescript", cache: true };
const pass = "sample.typescript-project=PASS";
console.log(project.cache ? pass : "sample.typescript-project=FAIL");
