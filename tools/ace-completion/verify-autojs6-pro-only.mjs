#!/usr/bin/env node

import {
  PRO_ONLY_COMPLETION_NAMES,
  buildRuntimeIndices,
  findProOnlyEntriesInRuntimeIndices,
  findProOnlyEntriesInSource,
  readSource,
} from "./indices-utils.mjs";
import {
  assertValidApiModel,
  buildAutoJs6ApiModel,
  buildAutoJs6CompletionSource,
} from "./autojs6-api-model.mjs";

assertDetectorSelfTest();

const apiModel = buildAutoJs6ApiModel();
assertValidApiModel(apiModel);

const baseSource = readSource();
const modelBackedSource = buildAutoJs6CompletionSource(baseSource, apiModel);
const runtimeIndices = buildRuntimeIndices(modelBackedSource);

assertNoProOnlyEntries("completion source", findProOnlyEntriesInSource(baseSource));
assertNoProOnlyEntries("model-backed completion source", findProOnlyEntriesInSource(modelBackedSource));
assertNoProOnlyEntries("runtime completion indices", findProOnlyEntriesInRuntimeIndices(runtimeIndices));

const knownNames = new Set(apiModel.filters && apiModel.filters.proOnlyNames || []);
for (const name of PRO_ONLY_COMPLETION_NAMES) {
  if (!knownNames.has(name)) {
    console.error(`API model is missing Pro-only filter name: ${name}`);
    process.exit(1);
  }
}

console.log(
  `AutoJs6 Pro-only API detection passed (${PRO_ONLY_COMPLETION_NAMES.length} reserved names, ` +
    `${apiModel.summary.proOnlyFilteredCount} filtered declaration entries).`,
);

function assertDetectorSelfTest() {
  const syntheticSource = {
    version: 1,
    modules: {
      app: {
        doc: "Synthetic app API",
        members: [
          {
            name: "uiObject",
            kind: "variable",
            doc: "Synthetic Pro-only member",
          },
        ],
      },
      autojsPro: {
        doc: "Synthetic Pro-only module",
        members: ["launch"],
      },
    },
    globals: ["pro"],
    variables: ["autojspro"],
    globalMembers: {
      app: ["uiObject"],
    },
    snippets: [
      {
        name: "autojsPro",
        snippet: "autojsPro.${1:call}()",
        meta: "snippet",
      },
    ],
  };
  const syntheticSourceEntries = findProOnlyEntriesInSource(syntheticSource);
  const sourceEntries = syntheticSourceEntries.map((entry) => entry.name);
  for (const name of ["autojsPro", "uiObject", "pro", "autojspro"]) {
    if (!sourceEntries.includes(name)) {
      console.error(`Pro-only source detector self-test missed: ${name}`);
      process.exit(1);
    }
  }
  if (!syntheticSourceEntries.some((entry) => entry.path === "snippets.autojsPro")) {
    console.error("Pro-only source detector self-test missed snippet names.");
    process.exit(1);
  }

  const runtimeEntries = findProOnlyEntriesInRuntimeIndices({
    globals: [{ name: "autojsPro" }],
    modules: {
      app: [{ name: "uiObject" }],
    },
    snippets: [{ name: "pro" }],
  });
  const runtimeNames = runtimeEntries.map((entry) => entry.name);
  for (const name of ["autojsPro", "uiObject"]) {
    if (!runtimeNames.includes(name)) {
      console.error(`Pro-only runtime detector self-test missed: ${name}`);
      process.exit(1);
    }
  }
  if (!runtimeEntries.some((entry) => entry.path === "snippets.pro")) {
    console.error("Pro-only runtime detector self-test missed snippet names.");
    process.exit(1);
  }
}

function assertNoProOnlyEntries(label, entries) {
  if (entries.length === 0) {
    return;
  }
  for (const entry of entries) {
    console.error(`${label} contains Pro-only completion: ${entry.path} -> ${entry.name}`);
  }
  process.exit(1);
}
