#!/usr/bin/env node
"use strict";

const assert = require("assert");
const childProcess = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { scan } = require("./scan-rhino-augment-inventory.js");
const { buildInventory } = require("./generate-rhino-augment-inventory.js");
const { candidateModuleNames } = require("./generate-rhino-node-compat-gap.js");
const { inventoryKeyViolations } = require("./verify-rhino-compat-inventory.js");

const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), "autojs6-phase11-inventory-"));

try {
  writeFixture(
    "app/src/main/java/org/autojs/autojs/runtime/api/augment/Augmentable.kt",
    [
      "package org.autojs.autojs.runtime.api.augment",
      "",
      "data class AugmentableKey(val name: String, val withDollarPrefix: Boolean = true)",
      "abstract class Augmentable",
      "",
    ].join("\n"),
  );
  writeFixture(
    "app/src/main/java/org/autojs/autojs/runtime/api/augment/fixture/FixtureModules.kt",
    [
      "package org.autojs.autojs.runtime.api.augment.fixture",
      "",
      "import org.autojs.autojs.runtime.api.augment.Augmentable",
      "import org.autojs.autojs.runtime.api.augment.AugmentableKey",
      "",
      "class MultiName : Augmentable() {",
      "    override val keys: List<AugmentableKey>",
      "        get() = listOf<AugmentableKey>(",
      "        AugmentableKey(\"primary\"),",
      "        AugmentableKey(name = \"legacy\", withDollarPrefix = false),",
      "    )",
      "    override val selfAssignmentFunctions = listOf(\"ping\")",
      "}",
      "",
      "object OcrAlias : Augmentable() {",
      "    override val keys = listOf(",
      "        AugmentableKey(OcrMode.MLKIT.value, withDollarPrefix = false),",
      "    )",
      "}",
      "",
      "class DefaultName : Augmentable()",
      "",
      "class Assigned : Augmentable() {",
      "    /** override val keys = listOf(AugmentableKey(\"documentedOnly\")) */",
      "    override val keys = listOf(",
      "        AugmentableKey(\"assigned\"),",
      "        AugmentableKey(\"ignoredAlias\", withDollarPrefix = false),",
      "    )",
      "}",
      "",
      "class LegacyName : Augmentable() {",
      "    override val key = \"oldName\"",
      "}",
      "",
    ].join("\n"),
  );
  writeFixture(
    "app/src/main/java/org/autojs/autojs/runtime/ScriptRuntime.kt",
    [
      "package org.autojs.autojs.runtime",
      "",
      "import org.autojs.autojs.runtime.api.augment.fixture.DefaultName",
      "import org.autojs.autojs.runtime.api.augment.fixture.Assigned",
      "import org.autojs.autojs.runtime.api.augment.fixture.LegacyName",
      "import org.autojs.autojs.runtime.api.augment.fixture.MultiName",
      "import org.autojs.autojs.runtime.api.augment.fixture.OcrAlias",
      "",
      "class ScriptRuntime {",
      "    private fun augment(target: ScriptableObject) {",
      "        MultiName().augment(target = target, proto = SomeProto, additionalAttributes = READONLY)",
      "        OcrAlias.augmentWithRuntime(target, this)",
      "        DefaultName().augment(target)",
      "        Assigned().assignWithRuntime(target, this)",
      "        LegacyName().augment(target, false)",
      "    }",
      "}",
      "",
    ].join("\n"),
  );

  const scanReport = scan(tempRoot);
  assert.strictEqual(scanReport.totals.registeredModuleCount, 5);
  assert.strictEqual(scanReport.totals.unresolvedCount, 0);

  const multiName = moduleByReceiver(scanReport, "MultiName");
  assert.deepStrictEqual(multiName.keys, [
    { name: "primary", withDollarPrefix: true },
    { name: "legacy", withDollarPrefix: false },
  ]);
  assert.deepStrictEqual(multiName.mountNames, ["primary", "$primary", "legacy"]);
  assert.strictEqual(multiName.key, "primary");
  assert.strictEqual(multiName.registration.withDollarPrefix, true);
  assert.deepStrictEqual(multiName.registration.protoExpressions, ["SomeProto"]);
  assert.deepStrictEqual(multiName.registration.additionalAttributes, ["READONLY"]);

  const ocrAlias = moduleByReceiver(scanReport, "OcrAlias");
  assert.deepStrictEqual(ocrAlias.keys, [
    { name: "mlkit", withDollarPrefix: false },
  ]);
  assert.deepStrictEqual(ocrAlias.mountNames, ["mlkit"]);

  const defaultName = moduleByReceiver(scanReport, "DefaultName");
  assert.deepStrictEqual(defaultName.keys, [
    { name: "defaultName", withDollarPrefix: true },
  ]);
  assert.deepStrictEqual(defaultName.mountNames, ["defaultName", "$defaultName"]);

  const assigned = moduleByReceiver(scanReport, "Assigned");
  assert.deepStrictEqual(assigned.keys, [
    { name: "assigned", withDollarPrefix: false },
  ]);
  assert.deepStrictEqual(assigned.mountNames, ["assigned"]);
  assert.strictEqual(assigned.registration.withDollarPrefix, false);

  const legacyName = moduleByReceiver(scanReport, "LegacyName");
  assert.deepStrictEqual(legacyName.keys, [
    { name: "oldName", withDollarPrefix: false },
  ]);
  assert.deepStrictEqual(legacyName.mountNames, ["oldName"]);

  const inventory = buildInventory(scanReport);
  const inventoryMultiName = inventory.modules.find((module) => module.displayName === "MultiName");
  assert(inventoryMultiName, "Missing MultiName inventory module");
  assert.deepStrictEqual(inventoryMultiName.keys, multiName.keys);
  assert.deepStrictEqual(inventoryMultiName.mountNames, multiName.mountNames);
  assert.deepStrictEqual(inventoryMultiName.exports.moduleKeys, multiName.mountNames);
  assert.deepStrictEqual(
    inventoryMultiName.exports.entries[0].aliases,
    ["$primary", "legacy"],
  );
  assert.deepStrictEqual(inventoryKeyViolations(inventory), []);
  assert(candidateModuleNames(inventoryMultiName).includes("legacy"));

  const legacyScanReport = structuredClone(scanReport);
  legacyScanReport.modules.forEach((module) => {
    delete module.keys;
    delete module.mountNames;
  });
  const inventoryFromLegacyScan = buildInventory(legacyScanReport);
  const legacyMultiName = inventoryFromLegacyScan.modules.find(
    (module) => module.displayName === "MultiName",
  );
  assert.deepStrictEqual(legacyMultiName.exports.moduleKeys, ["primary", "$primary"]);

  const inconsistentInventory = structuredClone(inventory);
  inconsistentInventory.modules[0].mountNames.push("unexpected");
  assert(inventoryKeyViolations(inconsistentInventory).length > 0);

  const blankKeyInventory = structuredClone(inventory);
  blankKeyInventory.modules[0].keys[0].name = " ";
  assert(inventoryKeyViolations(blankKeyInventory).some(
    (violation) => violation.includes("blank or invalid"),
  ));

  const fixtureModules = path.join(
    tempRoot,
    "app/src/main/java/org/autojs/autojs/runtime/api/augment/fixture/FixtureModules.kt",
  );
  const invalidSource = fs.readFileSync(fixtureModules, "utf8")
    .replace(
      "AugmentableKey(name = \"legacy\", withDollarPrefix = false)",
      "AugmentableKey(\"\\$primary\", withDollarPrefix = false)",
    )
    .replace(
      "class DefaultName : Augmentable()",
      "class DefaultName : Augmentable() { override val keys = configuredKeys }",
    )
    .replace(
      "AugmentableKey(OcrMode.MLKIT.value, withDollarPrefix = false)",
      "AugmentableKey(\"${prefix}mlkit\", withDollarPrefix = false)",
    )
    .replace(
      "AugmentableKey(\"ignoredAlias\", withDollarPrefix = false)",
      "AugmentableKey(\"   \", withDollarPrefix = false)",
    )
    .replace(
      "override val key = \"oldName\"",
      "override var keys = listOf(AugmentableKey(\"oldName\"))",
    );
  fs.writeFileSync(fixtureModules, invalidSource);
  const invalidScanReport = scan(tempRoot);
  assert.strictEqual(invalidScanReport.totals.unresolvedKeyCount, 5);
  assert(invalidScanReport.totals.unresolvedKeys.some(
    (entry) => entry.reason.includes("duplicate mount names"),
  ));
  assert(invalidScanReport.totals.unresolvedKeys.some(
    (entry) => entry.reason.includes("unsupported keys property initializer"),
  ));
  assert(invalidScanReport.totals.unresolvedKeys.some(
    (entry) => entry.reason.includes("unsupported key name expression"),
  ));
  assert(invalidScanReport.totals.unresolvedKeys.some(
    (entry) => entry.reason.includes("must not be blank"),
  ));
  assert(invalidScanReport.totals.unresolvedKeys.some(
    (entry) => entry.reason.includes("must be declared as val"),
  ));
  const invalidCli = childProcess.spawnSync(
    process.execPath,
    [
      path.join(__dirname, "scan-rhino-augment-inventory.js"),
      "--repo-root",
      tempRoot,
      "--fail-on-unresolved",
    ],
    { encoding: "utf8" },
  );
  assert.strictEqual(invalidCli.status, 1);

  console.log("Rhino augment inventory declarative-key tests: passed");
} finally {
  assert(tempRoot.startsWith(path.resolve(os.tmpdir()) + path.sep));
  fs.rmSync(tempRoot, { recursive: true, force: true });
}

function moduleByReceiver(report, receiver) {
  const module = report.modules.find((candidate) => candidate.receiver === receiver);
  assert(module, `Missing scanner module for ${receiver}`);
  return module;
}

function writeFixture(relativeFile, contents) {
  const file = path.join(tempRoot, relativeFile);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, contents);
}
