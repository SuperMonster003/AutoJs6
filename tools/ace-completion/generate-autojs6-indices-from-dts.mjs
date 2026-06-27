#!/usr/bin/env node

import path from "node:path";
import {
  API_MODEL_JSON_PATH,
  API_MODEL_REPORT_PATH,
  BUNDLED_AUTOJS6_DTS_ROOT,
  SOURCE_PATH,
  assertValidSource,
  readSource,
  relativeToRoot,
  writeIfChanged,
} from "./indices-utils.mjs";
import {
  assertValidApiModel,
  buildAutoJs6ApiModel,
  buildAutoJs6CompletionSource,
  renderApiModelReport,
} from "./autojs6-api-model.mjs";

const dtsRoot = path.resolve(process.argv[2] || BUNDLED_AUTOJS6_DTS_ROOT);
const baseSource = readSource();
const apiModel = buildAutoJs6ApiModel(dtsRoot);
assertValidApiModel(apiModel);

const source = buildAutoJs6CompletionSource(baseSource, apiModel);
assertValidSource(source);

const changed = [
  [API_MODEL_JSON_PATH, JSON.stringify(apiModel, null, 2) + "\n"],
  [API_MODEL_REPORT_PATH, renderApiModelReport(apiModel)],
  [SOURCE_PATH, JSON.stringify(source, null, 2) + "\n"],
].map(([filePath, content]) => ({
  filePath,
  changed: writeIfChanged(filePath, content),
}));

for (const result of changed) {
  const status = result.changed ? "updated" : "unchanged";
  console.log(`${status}: ${relativeToRoot(result.filePath)}`);
}

console.log(
  `Parsed ${apiModel.summary.declarationFileCount} d.ts files into ` +
    `${apiModel.summary.declarationBackedModuleCount} declaration-backed modules and ` +
    `${apiModel.summary.declarationBackedMemberCount} declaration-backed members.`,
);
