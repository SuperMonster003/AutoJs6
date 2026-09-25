# AI Provider conformance test app

This Android application is a test-only Binder conformance fixture for the AI Provider protocol V2 unified target catalog. It is not a production provider, a host fallback, or source shared with the independent `AutoJs6-Plugin-Three-Stone-AI` project.

The fixture intentionally keeps a distinct fake package and hostile scenarios for reasoning, tools, structured JSON, usage, pagination, malformed callbacks, descriptor ownership, and caller authentication. Those behaviors must not be exposed by a production provider merely to test the host.

Only a debug variant is available. The manifest sets `android:testOnly="true"`, and the app uses the Android default debug/test signing key. Run same-signer Android compatibility tests only from an isolated checkout where the fixture and host debug build intentionally use the same default or dedicated test key. Never copy, reference, or use a production signing key for this matrix.

The `profile:agent-tools` catalog target has simulated remote locality but performs
no network access. It lets the real Agent use the normal online context budget.
Reserved `agent-conformance:native-{batch,invalid,deny,cancel}` goals drive the
combined P9.1 host/Agent/Provider matrix. The descriptor allows 16 tool rounds;
existing session-machine tests still exercise smaller explicit round limits.
The older `local:tools` target and its synthetic argument/result behavior remain
available for direct model-broker tests. Neither fixture requires credentials.
