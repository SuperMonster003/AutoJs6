type Label = string;
interface Payload {
  label: Label;
}

const payload: Payload = { label: await Promise.resolve("esm") };
export const marker: Label = payload.label;

console.log("sample.typescript-esm.value=" + marker);
console.log("sample.typescript-esm=PASS");
