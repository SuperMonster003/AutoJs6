#!/usr/bin/env node

import {
  SOURCE_PATH,
  assertValidSource,
  readSource,
  relativeToRoot,
} from "./indices-utils.mjs";
import {
  assertValidApiModel,
  buildAutoJs6ApiModel,
  buildAutoJs6CompletionSource,
} from "./autojs6-api-model.mjs";

const apiModel = buildAutoJs6ApiModel();
assertValidApiModel(apiModel);

const persistedSource = readSource();
const source = buildAutoJs6CompletionSource(persistedSource, apiModel);
assertValidSource(source);
if (JSON.stringify(source, null, 2) + "\n" !== JSON.stringify(persistedSource, null, 2) + "\n") {
  console.error(`${relativeToRoot(SOURCE_PATH)} is stale. Run node tools/ace-completion/generate-autojs6-indices-from-dts.mjs.`);
  process.exit(1);
}

const moduleMap = new Map(apiModel.modules.map((moduleSpec) => [moduleSpec.name, moduleSpec]));
const requiredDeclarationMembers = {
  app: "launch",
  files: "read",
  images: "findImage",
  device: "getBattery",
  console: "log",
  threads: "start",
  engines: "myEngine",
  toast: "dismissAll",
};
const requiredDeclarationSignatures = {
  "app.launch": "launch(app: App | App.Alias | App.PackageName): boolean;",
  "files.read": "read(path: string, encoding?: StandardCharsets): string;",
  "device.getBattery": "getBattery(): number;",
};

for (const [moduleName, memberName] of Object.entries(requiredDeclarationMembers)) {
  const moduleSpec = moduleMap.get(moduleName);
  if (!moduleSpec) {
    console.error(`Missing declaration-backed API model module: ${moduleName}`);
    process.exit(1);
  }
  if (!moduleSpec.members.some((member) => member.name === memberName)) {
    console.error(`Missing declaration-backed API model member: ${moduleName}.${memberName}`);
    process.exit(1);
  }
}

for (const [qualifiedName, signature] of Object.entries(requiredDeclarationSignatures)) {
  const [moduleName, memberName] = qualifiedName.split(".");
  const moduleSpec = moduleMap.get(moduleName);
  const member = moduleSpec && moduleSpec.members.find((entry) => entry.name === memberName);
  if (!member || member.signature !== signature) {
    console.error(`Missing declaration-backed API model signature: ${qualifiedName} -> ${signature}`);
    process.exit(1);
  }
}

const globalFunctions = new Set(apiModel.globals.functions.map((entry) => entry.name));
for (const name of ["sleep", "setTimeout"]) {
  if (!globalFunctions.has(name)) {
    console.error(`Missing declaration-backed API model global function: ${name}`);
    process.exit(1);
  }
}

if (apiModel.summary.declarationBackedModuleCount < Object.keys(requiredDeclarationMembers).length) {
  console.error("AutoJs6 API model has too few declaration-backed modules.");
  process.exit(1);
}

if (apiModel.summary.declarationBackedMemberCount < 100) {
  console.error("AutoJs6 API model has too few declaration-backed members.");
  process.exit(1);
}

console.log(
  `AutoJs6 API model verified: ${apiModel.summary.declarationBackedModuleCount} modules, ` +
    `${apiModel.summary.declarationBackedMemberCount} members.`,
);
