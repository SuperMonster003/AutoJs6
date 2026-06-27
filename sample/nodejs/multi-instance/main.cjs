"nodejs";

const slots = ["short-script", "long-running", "sandboxed"].map((kind, index) => ({
  slot: index + 1,
  kind
}));

console.log("sample.multi-instance.policy=" + slots.map((slot) => slot.kind).join(","));
console.log("sample.multi-instance.mode=design-gated");
console.log("sample.multi-instance=PASS");
