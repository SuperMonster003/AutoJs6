# DEX compiler conformance test app

This Android application is a test-only Binder conformance fixture for the DEX Compiler protocol V1. It is not a production compiler, a host fallback, or source shared with the independent `AutoJs6-Plugin-DEX-Compiler` project.

The fixture intentionally keeps a distinct fake package and deterministic hostile services for invalid callback order, duplicate terminals, invalid output, unnegotiated DEX versions, and stalls. Those behaviors must not be added to the production D8 provider merely to test the host.

Only a debug variant is available. The manifest sets `android:testOnly="true"`, and the app uses the Android default debug/test signing key. Run same-signer Android compatibility tests only from an isolated checkout where the fixture and host debug build intentionally use the same default or dedicated test key. Never copy, reference, or use a production signing key for this matrix.
