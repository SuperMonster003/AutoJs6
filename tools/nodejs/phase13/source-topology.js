"use strict";

const fs = require("fs");
const path = require("path");

const NATIVE_CPP_PREFIX = "app/src/main/cpp/";
const NATIVE_CPP_ROOT_ENV = "AUTOJS6_NODE_PHASE13_NATIVE_CPP_ROOT";

function resolvePhase13SourceFile(repoRoot, relative) {
  const normalized = String(relative).replace(/\\/g, "/");
  if (!normalized.startsWith(NATIVE_CPP_PREFIX)) {
    return path.join(repoRoot, relative);
  }
  const nativeCppRoot = resolveNativeCppRoot(repoRoot);
  return path.join(nativeCppRoot, normalized.slice(NATIVE_CPP_PREFIX.length));
}

function resolveNativeCppRoot(repoRoot) {
  const requested = process.env[NATIVE_CPP_ROOT_ENV];
  if (requested) {
    const requestedRoot = path.resolve(requested);
    requireNativeCppRoot(requestedRoot, `Environment ${NATIVE_CPP_ROOT_ENV}`);
    return requestedRoot;
  }
  const candidates = [
    path.resolve(repoRoot, "../AutoJs6-Plugin-NodeJs-Runtime/app/src/main/cpp"),
    path.join(repoRoot, "app/src/main/cpp"),
  ];
  const resolved = candidates.find((candidate) =>
    fs.existsSync(path.join(candidate, "node_bridge_sources.cpp")));
  if (!resolved) {
    throw new Error(
      "Unable to resolve Phase 13 native C++ source root. Checked: " + candidates.join(", "),
    );
  }
  return resolved;
}

function requireNativeCppRoot(root, source) {
  if (!fs.existsSync(path.join(root, "node_bridge_sources.cpp"))) {
    throw new Error(`${source} must contain node_bridge_sources.cpp: ${root}`);
  }
}

module.exports = {
  NATIVE_CPP_ROOT_ENV,
  resolveNativeCppRoot,
  resolvePhase13SourceFile,
};
