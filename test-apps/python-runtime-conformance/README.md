# Python runtime conformance test app

This debug-only Android application is a deterministic Binder and PFD fixture
for Python Runtime protocol V1. It contains no CPython, Chaquopy, native code,
network permission, launcher activity, production fallback, or host route.

The fake service validates admission, snapshots finite descriptor payloads, and
emits normal or deliberately hostile callback sequences selected by stable test
case IDs. Hostile behavior belongs only in this test application.

The manifest marks the application `testOnly`, disables release variants, and
protects its exported runtime service with the host signature permission. Real
cross-process evidence requires an explicitly enabled host instrumentation run
with host and fixture built using the same isolated debug/test signer. JVM tests
cover only deterministic policies and simulations; they are not Binder, PFD,
deadline, process-death, or device evidence.
