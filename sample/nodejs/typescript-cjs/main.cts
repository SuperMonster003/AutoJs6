"nodejs";

type Label = string;
interface Payload {
  label: Label;
  count: number;
}

const payload: Payload = { label: "cjs", count: 1 };

function format<T>(input: T): string {
  return String(input);
}

console.log("sample.typescript-cjs.value=" + format(payload.label) + ":" + payload.count);
console.log("sample.typescript-cjs=PASS");
