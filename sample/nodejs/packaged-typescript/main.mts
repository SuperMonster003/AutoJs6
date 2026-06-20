import { message } from "./src/message.ts";

type Label = string;

const dynamic = await import("./src/dynamic.mts");
const marker: Label = message + ":" + dynamic.suffix;

console.log("sample.packaged-typescript.value=" + marker);
console.log("sample.packaged-typescript=PASS");
