# Test applications

This directory contains installable Android applications used only for cross-process and hostile conformance testing. They are part of the repository test surface, not production plugins, host fallbacks, or release distributions.

Production plugin implementations live in independent sibling repositories. Test applications keep separate package identities and debug-only signing so malformed callbacks, invalid output, timeouts, and other adversarial behavior never need to be included in a production plugin.

`codegen-fixture/` is a separate class of test application for collecting layout-code-generation training data. It provides eight deterministic View-based screen families, controlled mutations, private truth tags, and observable action receipts. It has a separate application ID, debug-only build variants, no network permissions, and is never included in the host APK or published. Its oracle is read locally using `adb exec-out run-as`; private truth keys are never copied into accessibility text, descriptions, or resource IDs. See [the fixture protocol](codegen-fixture/README.md) and [the model roadmap](../docs/dev/layout-code-generation-model-roadmap.md).
