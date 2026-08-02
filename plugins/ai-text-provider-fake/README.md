# AutoJs6 AI text provider fake

This module is a test-only Binder fixture. It exposes only a debug variant, uses the Android default debug/test signing key, and must never use the host production signing configuration.

Run same-signer Android compatibility tests only from an isolated checkout where both the fixture and the host debug build intentionally use the dedicated test key or the same default debug key. Never copy, reference, or use a production signing key for this matrix.
