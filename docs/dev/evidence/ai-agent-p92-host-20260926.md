# P9.2 host image input, 2026-09-26

This implements the original roadmap's host item under the maintainer's accepted
V2.1 direction. Provider model mappings and the Agent `screen_capture` tool remain
separate, unfinished original P9.2 items. No new roadmap stage or public JS image
API was added. The pre-existing P9.1 Wi-Fi comparison failures remain failures.

## Implemented behavior

- Negotiate Provider 2.0-2.1. Preserve 2.0 text wire fixtures and AIDL transaction
  order. Optional vision capabilities can be ignored by old decoders; required
  image-content/tool-result fields cannot be silently ignored. Target catalogs
  strip `vision` when replying to 2.0 clients.
- Carry JPEG/PNG user-message images and native tool-result images as PFDs, with
  MIME, dimensions, encoded length and SHA-256. The exact selected target must
  advertise vision. The broker advertises `visionVersion=1`; the Agent base
  contract stays V1. Text-only clients and models continue to work.
- Validate exact descriptor references, per-image/batch/session bounds, actual
  bytes/digest/MIME/dimensions and decodability. Keep text and image quotas
  independent. Initial images count toward the native session limit.
- Preserve request/callback identity, pending tool IDs, original deadline,
  cancellation, call-rate and token budgets across image continuations. Reserve
  image estimates, count retained images on every continuation, and reconcile
  cumulative Provider usage. The estimate is not a model billing guarantee.
- Read image pipes without blocking cancellation. Defer opening outbound tool
  image descriptors until serialized dispatch; close them after dispatch or on
  failure. Clean up nested rejected Bundle descriptors iteratively, without a
  nesting cutoff or recursive traversal. Keep bytes, base64, hashes and paths
  out of ordinary diagnostics.
- Persistent `ai.session` remains text-only. No image generation, audio/video,
  automatic target fallback, real screenshots or real model requests were added
  to this acceptance run.

Normative details: [Provider V2](../ai-provider-protocol-v2.md) and
[Agent model image extension](../ai-agent-protocol-v1.md#model-image-extension).

## JVM and build checks

| Scope | Passed | Failed / skipped |
| --- | ---: | ---: |
| AI Provider API, including old golden wire fixtures | 84 | 0 / 0 |
| AI Agent contract API | 7 | 0 / 0 |
| Isolated fake Provider | 32 | 0 / 0 |
| Host AI plugin, model broker and `runtime.api.ai` tests | 228 | 0 / 0 |
| Total scoped JVM tests | 351 | 0 / 0 |

The host debug APKs, instrumentation APK and test-only fake Provider built
successfully. Native alignment checks passed at 16 KiB. The dedicated test
worktree contained the same changed Kotlin sources and no production signing
configuration. Host and fake Provider APKs used the standard test certificate
SHA-256 `2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8`.

The initial full host lint run completed in 9m 6s with zero errors, 2403 warnings
and 3 hints. Provider API lint had no findings; Agent API had 1 warning and the
fake Provider had 2 warnings. The final cleanup revision passed the same 228
host JVM tests and host lint again in 7m 21s, with the same lint counts. Its
instrumentation tests were rebuilt and passed on both AVDs. This is scoped JVM coverage and debug host build
evidence, not an entire-host JVM or host release/R8 certification.

## Android and compatibility checks

Only the existing private, disposable AVDs were used:

| Device | Model broker suite | Final deep-FD cleanup | Existing R8 Agent round trips | New/old Provider catalogs | Old Provider text/tools |
| --- | ---: | ---: | ---: | ---: | ---: |
| API 24 / x86, emulator-5590 | 20/20 | 1/1 | 4/4 | 2/2 | 2/2 |
| API 37.1 / x86_64 / 16 KiB, emulator-5592 | 20/20 | 1/1 | 4/4 | 2/2 | 2/2 |

The final cleanup case reruns one method from the base suite after adding the
deeply nested Bundle case; do not count it as a new independent test method.
Each method starts a fresh host process, retaining the production fault fuse.

The six vision methods cover PNG/JPEG initial upload; streamed/non-streamed
native image-result continuation in the original session; bad digest, wrong
decoded dimensions, non-image bytes, wrong length, missing/extra descriptors;
unsupported targets rejected before reading an open image pipe; cancellation and
deadline during a pipe read; and malformed arrays, deeply nested unknown fields,
null callbacks and foreign-UID cleanup. Fixtures use synthetic 32x24 bitmaps.

Compatibility uses a preserved 2.0 fake APK (SHA-256
`081ec21025622f241d159e80d09d47dd5008852962a8a80289c8f29fb648b46f`). The test binds
to its real Binder service and asserts the advertised maximum and negotiated
minor are 0, then runs text file/pipe and native tools through the new host.
The new fake advertises maximum minor 1; direct list calls verify a 2.0 client
receives no vision IDs and a 2.1 client receives them. These calls emulate the
old request contract, not installation of an old production host. Old-decoder
behavior is also covered by the frozen wire and unknown-required-field tests.

The unchanged, previously built R8 Agent 1.1.0 / build 82 completed the real-host
and separate fake-Provider UID tests: a batch of real read-only observations,
whole-batch invalid arguments, denied confirmation and cancellation while waiting
for confirmation. No device writes were approved by those tests.

## Intermediate failures and limits

- An initial Gradle task name was invalid. Early tests exposed old version/key
  assertions and fixtures that mixed fixed 2.0 requests with the new maximum
  version; fixtures now explicitly distinguish legacy 2.0 and negotiated 2.1.
  An initial new test also used incorrect helper names; corrected before the
  successful suite. No production version-validation assertion was weakened.
- The fake APK initially needed the explicit `adb install -t` flag. During final
  installation, the ADB daemon restarted and a private AVD briefly disappeared;
  reconnecting its existing loopback transport restored it without wiping data
  or rebooting real devices. These install attempts are not test passes.
- Private raw logs remain under ignored `build/p92-host-private`. No keys, real
  screens, prompts or account data are included in this receipt.
- No physical-device installation, connectivity change, real model request,
  order or payment occurred. No Rhino synchronization files were touched.
- Before real visual tasks can be accepted, implement the remaining Provider
  image mappings and Agent capture/prompt/budget item, then test the configured
  supported model. Ordinary networking is enough for this development; a SIM is
  only needed again for an online Wi-Fi-switching test without another network.
