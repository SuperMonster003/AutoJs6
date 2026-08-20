# on-device AI provider conformance test app

This Android application is a test-only Binder conformance fixture for the On-Device AI protocol V1. It is not a production provider, a host fallback, or source shared with the independent `AutoJs6-Plugin-On-Device-AI` project.

The fixture intentionally keeps a distinct fake package and hostile scenarios for reasoning, tools, structured JSON, usage, pagination, malformed callbacks, descriptor ownership, and caller authentication. Those behaviors must not be exposed by a production provider merely to test the host.

Only a debug variant is available. The manifest sets `android:testOnly="true"`, and the app uses the Android default debug/test signing key. Run same-signer Android compatibility tests only from an isolated checkout where the fixture and host debug build intentionally use the same default or dedicated test key. Never copy, reference, or use a production signing key for this matrix.
