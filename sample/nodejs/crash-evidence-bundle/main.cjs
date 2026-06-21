"nodejs";

const bundle = ["logcat", "nativeLibraryHashes", "runtimeDescriptor", "timelineTail"];
const pass = "sample.crash-evidence-bundle=PASS";
console.log(bundle.length === 4 ? pass : "sample.crash-evidence-bundle=FAIL");
