# Python-to-Kotlin catalog signature fixture

These two files were produced on 2026-10-03 by the Shared-Assets P5 distribution CLIs,
using the existing `autojs6.codegen.tiny-test/1` package. They belong only to JVM tests.
The synthetic repository `example/AutoJs6-Codegen-Test` has not been published or queried.

- Public SPKI SHA-256: `0971601a168da7ee9db0f4e23499454a69b9925e39ec1ce8fa353ab8ab839fa7`.
- Test key ID: `p256-0971601a168da7ee`.
- Signed catalog SHA-256: `07af51c5ed5f512dcb902066aec53e2a0495e58c8df60f8643f9011ca0f89bb5`.
- Package SHA-256: `9ab137a6e184f52246768a905ea98a09a03cf4da97f221526d70e7b6f32afc17`.

The disposable private key was generated outside Git and removed after signing. It is not
the future production key and cannot be used to publish an official model. The bootstrap
envelope preserves the exact signed catalog bytes in `catalogBase64`; its signature uses
the existing `ECDSA_P256_SHA256` four-field envelope and an OpenSSL DER ECDSA signature.

Reproduction uses `prepare_catalog.py --allow-test-models` with repository above, version 1
and `--generated-at 2026-10-03T11:12:51Z`, then `generate_keys.py`, `sign_catalog.py`,
`verify_catalog.py` and `prepare_bootstrap.py --allow-test-models` in Shared-Assets.
New signing keys and ECDSA signatures naturally differ; the catalog bytes are reproducible.
No corresponding production resource is installed in `src/main/res/raw` by these tests.
