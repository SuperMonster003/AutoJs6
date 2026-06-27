#!/usr/bin/env node

import {
  GENERATED_INDEX_PATH,
  REPORT_PATH,
  buildRuntimeIndices,
  readSource,
  relativeToRoot,
  renderIndexJs,
  renderReport,
  writeIfChanged,
} from "./indices-utils.mjs";

const source = readSource();
const indices = buildRuntimeIndices(source);

const changed = [
  [GENERATED_INDEX_PATH, renderIndexJs(indices)],
  [REPORT_PATH, renderReport(source, indices)],
].map(([filePath, content]) => ({
  filePath,
  changed: writeIfChanged(filePath, content),
}));

for (const result of changed) {
  const status = result.changed ? "updated" : "unchanged";
  console.log(`${status}: ${relativeToRoot(result.filePath)}`);
}
