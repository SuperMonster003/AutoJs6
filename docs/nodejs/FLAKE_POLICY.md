# AutoJs6 Node.js Flake Policy

Last reviewed: 2026-08-21.

## Classification

- **Product failure**: deterministic assertion, crash, contract mismatch, permission
  regression, lost execution, leaked resource, or incorrect result.
- **Environment failure**: device offline, package installation failure, unavailable
  Android service, exhausted device storage, or unrelated Gradle/SDK failure.
- **Skipped**: an Assume precondition rejected the test. A skipped test is neither a
  pass nor release evidence.

## Rules

1. Always record host and plugin versions before a device run.
2. Use raw instrumentation output when plugin-dependent tests finish implausibly fast.
3. Retry an environment failure once only after correcting the stated condition.
4. Do not retry deterministic product failures into a green result.
5. Permission-sensitive tests must record accessibility, screen-capture, notification,
   and plugin authorization state as applicable.
6. Close a failure only with a focused regression or a documented external cause.

Baseline comparison is appropriate when a device-specific failure may predate a
change, but identical baseline failure does not turn the affected test into positive
release evidence.
