# Parameter and return types: script-modules.json against the d.ts

Date: 2026-10-30. Batch: api-coverage P2.4, the second step of decision D9 (argument counts were P0.3 / P2.2). Static; no device.

## What is compared

The KSP processor of the script module layer writes the Kotlin type of every parameter, return value and accessor to `script-modules.json`. In the host HEAD of this batch:

- 1057 `@ScriptFunction` functions with 1083 overloads and 1502 parameters, 97 accessors, the invoke / construct overloads of 39 callable modules (37 callable, 4 constructable);
- 1335 of the 1502 parameters are `kotlin.Any?` (the function checks the raw value itself) and carry no type; the typed ones are String (58), NativeObject? (30), Double (18), Any (18), List (14), Callable (13), Boolean (10) and a few classes;
- return values are richer: Boolean 187, Unit 137, String 92, Double 76, Int 56, classes of `returned-objects.json` (ImageWrapper 24 and others), other Java classes (OpenCV points, Uri, Intent ...), and 319 `Any` / `Any?`;
- the 625 names of `@ScriptInstall` hooks and the 4 Java classes have no types.

`tools/api-coverage/type-map.json` maps the Kotlin types (README "Types"). For parameters it separates the type the d.ts is expected to say from what the `ScriptArgs` converter takes (numbers reach a String parameter through toString, numeric strings a Double one); a nullable type adds null (and undefined for parameters); types without detail (`object`, `Function`, `{}`, arrays, classes) are open, so an option interface or a callback signature in the d.ts is a refinement. Classes of `returned-objects.json` are compared with the d.ts type of the object's probe, other classes with the d.ts type of their qualified name. `kotlin.ByteArray` return values are `number[]`, the d.ts convention for Java byte arrays (Q188 (a)).

Levels per parameter position and return value: match, refined, coerced (parameters only) are consistent; narrow, wide, error differ; unmapped is not compared (no Kotlin type, an undeclared class, a d.ts `any` / `unknown` / unresolved type parameter). The unsafe directions are wide parameters (the d.ts allows values the implementation rejects) and narrow return values (the d.ts omits values the implementation returns); `check.py --strict-types` fails on those and on error (Q187 (a), in CI).

## Results (Rhino d.ts)

1318 rows compared (functions with their aliases and global placements, accessors, invoke / construct).

| | before (TS 973963b) | after (TS aa62341) |
|---|---|---|
| rows | typed 1013 / differs 44 / unmapped 257 / no-dts 4 | typed 1034 / differs 23 / unmapped 257 / no-dts 4 |
| parameter positions | match 79 / refined 46 / coerced 9 / narrow 39 / wide 3 / unmapped 1534 | match 81 / refined 63 / coerced 9 / narrow 22 / wide 1 / unmapped 1534 |
| return values and accessors | match 783 / refined 249 / error 2 / unmapped 280 | match 785 / refined 249 / unmapped 280 |
| strict failures | 5 (7 before Q188) | 0 (23 differing positions reviewed in type-map.json) |

Unmapped reasons: `kotlin.Any?` 1765 positions, ProxyObject / ProxyJavaObject 39, d.ts any or generic 8, Set 1, Throwable 1. The 4 no-dts rows are `toString`, `ui.__autojs6Tsx`, `ui.__autojs6TsxFragment` (internal names without declarations) and `WebSocket.()` (calling it is a TypeError, as in P2.2).

In MATRIX.md the d.ts column counts `typed` for the consistent rows whose arity matches (accessors have no arity): none 533 / name 1774 / arity 1163 / typed 0 -> none 533 / name 1690 / arity 319 / typed 928; the type column of the module table gives typed / compared rows per module, and the summary line counts the matrix rows by comparison status (typed 993 / differs 23 / unmapped 252; the 41 invoke / construct rows of the comparison are not matrix rows).

### Fixed in the declarations (TS aa62341, Ace 0a97709, host completion source 00d36fb4f)

Unsafe directions:

- `engines.stopAllAndToast()` returned `void`; the module returns the number of engines asked to stop (Int), as the documentation says: `number`.
- `util.deprecate(fn, msg): Func`; the module only writes a warning and returns nothing (the documentation says `(...args): void`): `deprecate(...args: any[]): void`.
- `flow.whenPresent` / `flow.repeatUntil` took null as `cond` (`Pickup.SingleSelector` includes null); the module rejects null and undefined, and so does every flow wait (`FlowArguments.wait`): `Flow.SelectorCond` is `NonNullable<Pickup.Selector> | number | UiObject`.

Narrow parameters, fixed after Q189 (b) (null where the nullable parameter takes it): the options of `app.getInstalledApps` / `getInstalledPackages` / `getApkInfo`, `autojs.version.isAtLeast` ignoreSuffix, `colors.isEqual` alphaMatters, `continuation.create` scope, the mode of `files.open` / `open` (the `'r'` overload), `powerManager.requestIgnoreBatteryOptimizations` forceRequest, the default values of `sysprops()` / `sysprops.get` / `sysprops.getBoolean`, the `nanoid()` size, the options of `tasks.queryTimedTasks` / `queryIntentTasks`, and `tasks.removeTimedTask` / `removeIntentTask` without options (`options?: null` on the overload without callbacks). 17 positions.

The documentation already said what the module does for all of them (no signature lists null), so the documentation and the offline documents are unchanged.

### Kept or left (type-map.json `reviewed`)

- `tasks.add*Task` (9), `timers.add*Task` (4), `workManager.add*Task` (5) options: the module defaults null to an empty object, which then fails for the missing path / component, so the d.ts keeps them required without null, as for their argument counts (BASELINE section 7; Q190 (a)).
- `timers.queryTimedTasks` / `queryIntentTasks` and the same two of `workManager` (one declaration, `Internal.WorkManager`): null missing; timers belongs to the automation cards of batch 10, so they or the closing batch fix it.
- `auto.explain` target (wide: `Automator.Target` takes null through `Pickup.SingleSelector`, the module rejects it): the auto / automator declarations belong to the automation cards of batch 10 (P3.4 / P4.4) or the closing batch.

## Node runtime declarations (Q186 (a), reported only)

`check.py --matrix` also compares `docs/nodejs/types/autojs6-node` for the 1744 rows the matrix covers in Node main (bridged / local-shim with a module and member; Node plugin b77c28cb with the automation family of P3.4): 1101 of them are type rows. Return values and accessors are compared awaited (the bridge answers with promises, getters included); classes have no Node declaration and stay unmapped.

- rows: typed 735 / differs 70 / unmapped 296;
- parameters: match 57 / refined 48 / coerced 2 / narrow 35 / wide 4 / unmapped 1265;
- return values: match 559 / refined 173 / narrow 12 / wide 3 / error 18 / unmapped 336.

Most differences are the Node shape of an API rather than mistakes: `images.toBytes` / `compressToBytes` resolve a `Uint8Array`, the settings launchers (`app.launchSettings` and 11 more) resolve nothing where Rhino returns whether it started, `util.dpToPx` and its relatives also take strings, task options without null as above. They are listed in `runtime-types.json` for the Node cards; `node-types.gradle.kts` does not generate declarations (it checks that the 73 handwritten ones exist and are referenced), so the comparison runs in the host gate instead (augment appendix C Q3, second item).

## Observations

- TS d473c8e (3-Macaw speech `ImportOptions.kind`) has not been reimported into Ace; this batch reimported its own files only and left `aj6-int-speech.d.ts` at the bundled version.
- `util.toRegularAndCall` / `toRegularAndApply` always throw and return `Unit` in Kotlin while the d.ts returns the generic `T`; `util.debuglog` / `app.sendLocalBroadcastSync` return `any`. These are unmapped (d.ts any or generic), not differences.
