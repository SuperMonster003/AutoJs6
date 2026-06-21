"nodejs";

const deniedDirectAccess = ["shell", "java", "native-addon"];
console.log("sample.sandboxed-script.denied=" + deniedDirectAccess.join(","));
console.log("sample.sandboxed-script.mode=design-gated");
console.log("sample.sandboxed-script=PASS");
