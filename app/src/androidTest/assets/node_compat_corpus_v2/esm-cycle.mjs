import cjs from "./cjs-cycle.cjs";

cjs.note("esm-cycle");

export const name = "esm-cycle";
export const sawCjs = cjs.name;
export const sawDone = cjs.done === true;
