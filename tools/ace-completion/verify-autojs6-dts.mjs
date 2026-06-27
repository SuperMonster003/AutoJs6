#!/usr/bin/env node

import fs from "node:fs";
import {
  GENERATED_DTS_PATH,
  readSource,
  relativeToRoot,
  renderDts,
} from "./indices-utils.mjs";

const source = readSource();
const expected = renderDts(source);
const actual = fs.existsSync(GENERATED_DTS_PATH)
  ? fs.readFileSync(GENERATED_DTS_PATH, "utf8")
  : "";

if (actual !== expected) {
  console.error(`${relativeToRoot(GENERATED_DTS_PATH)} is stale. Run node tools/ace-completion/generate-dts.mjs.`);
  process.exit(1);
}

const requiredSignatures = [
  "launch(app: App | App.Alias | App.PackageName): boolean;",
  "read(path: string, encoding?: StandardCharsets): string;",
  "findImage(img: ImageWrapper, template: ImageWrapper, options?: { threshold?: number, weakThreshold?: number, level?: number, region?: OmniRegion, }): OpenCV.Point | null;",
  "declare function launch(app: App | App.Alias | App.PackageName): boolean;",
];

for (const signature of requiredSignatures) {
  if (!actual.includes(signature)) {
    console.error(`Generated AutoJs6 d.ts is missing signature: ${signature}`);
    process.exit(1);
  }
}

console.log("AutoJs6 generated d.ts verified.");
