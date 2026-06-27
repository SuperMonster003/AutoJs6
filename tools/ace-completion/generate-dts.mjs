#!/usr/bin/env node

import {
  GENERATED_DTS_PATH,
  readSource,
  relativeToRoot,
  renderDts,
  writeIfChanged,
} from "./indices-utils.mjs";

const source = readSource();
const changed = writeIfChanged(GENERATED_DTS_PATH, renderDts(source));
console.log(`${changed ? "updated" : "unchanged"}: ${relativeToRoot(GENERATED_DTS_PATH)}`);
