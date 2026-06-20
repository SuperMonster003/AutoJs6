import jsonData from "./data.json" with { type: "json" };
import requiredJson from "./json-require.cjs";
import customCondition from "custom-condition-pkg";
import tsMixed from "./ts-esm.mts";

const dynamicJson = await import("./dynamic.json", { with: { type: "json" } });
const localResolved = import.meta.resolve("./meta-target.mjs");
const packageResolved = import.meta.resolve("custom-condition-pkg");
const builtinResolved = import.meta.resolve("node:path");
let deniedResolve = false;

try {
  import.meta.resolve("node:child_process");
} catch (error) {
  deniedResolve = error && error.code === "ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED";
}

jsonData.seenFromEsm = true;

export const summary = {
  json: jsonData.name + "/" + dynamicJson.default.name,
  jsonCacheShared: requiredJson.data === jsonData && requiredJson.data.seenFromEsm === true,
  customCondition: customCondition.value,
  tsMixed,
  metaResolve: localResolved.endsWith("/meta-target.mjs") &&
    packageResolved.endsWith("/auto.mjs") &&
    builtinResolved === "node:path" &&
    deniedResolve,
};

export default summary;
