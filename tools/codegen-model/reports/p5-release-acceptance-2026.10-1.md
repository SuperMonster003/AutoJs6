# P5.4 published-release acceptance (2026-10-04, Asia/Shanghai)

The maintainer completed the first real test-repository release workflow. This report is
remote E2 evidence for P5.4, followed by an independent desktop readback. It does not claim
publication of the production ranker or Android-device acceptance.

## Reviewed run and release

- Repository: `SuperMonster003/AutoJs6-Codegen-Distribution-Test` (public test repository).
- [Workflow run 37138706446](https://github.com/SuperMonster003/AutoJs6-Codegen-Distribution-Test/actions/runs/37138706446), attempt 1, result `success`.
- Event: `workflow_dispatch`; actor and triggering actor: `SuperMonster003`.
- Reviewed commit: `8a0ae0128913f40b7fca306c76efc7c6209ac477`, branch `main`.
- Created: `2026-10-04 00:56:44 +08:00`; completed: `2026-10-04 00:57:03 +08:00`.
- [Release codegen-models-v1](https://github.com/SuperMonster003/AutoJs6-Codegen-Distribution-Test/releases/tag/codegen-models-v1), ID `402604433`, published at `2026-10-04 00:56:59 +08:00`.
- Release state: published prerelease, not a draft. Its lightweight tag resolves to the
  same reviewed commit as the workflow.
- Test key ID: `p256-fc44588d1cb789dc`.
- Test public SPKI SHA-256: `fc44588d1cb789dc3ac0002821b8f698b663b44529606a2937910c0a80ccb7c3`.

## Assets downloaded and independently verified

| Asset | Bytes | SHA-256 |
| --- | ---: | --- |
| `autojs6-codegen-autojs6.codegen.tiny-test-v1-9ab137a6.ajcgm` | 23,633 | `9ab137a6e184f52246768a905ea98a09a03cf4da97f221526d70e7b6f32afc17` |
| `catalog-v1.json` | 2,094 | `7a96afa530738232f521ee53d76dce628e960947cb8f97d7aa879ebd19796b43` |
| `catalog-v1.sig.json` | 222 | `04cd4032ae1f8342f8289924c17f0e942832f69d37a0a7e0b922d5a8d829dda4` |

The downloaded catalog/signature matched the locally reviewed files byte for byte. Package
size, hash, ZIP/binary structure and manifest metadata matched the signed catalog. GitHub's
reported asset digests also matched the independently computed values.

## Verification performed

1. The actual Linux CI job ran all **63 distribution tests**, with `OK`, no skipped tests,
   and no failures. Its current/historical signature checks and exact catalog reproduction
   succeeded before remote mutation.
2. Authenticated preflight confirmed absence of the release and tag. The workflow created
   the tag, uploaded assets to a draft release, published it, then downloaded every asset
   and verified the reviewed bytes and signature. Every workflow step succeeded.
3. A fresh desktop download through `gh release download` independently passed
   `verify_release.py --downloaded-release` and `verify_catalog.py` using the pinned test key.
4. Separate HTTPS requests with no authentication headers fetched both current catalog files
   from `raw.githubusercontent.com` and the package from its signed GitHub Release URL.
   All three responses matched the verified release bytes. This confirms public endpoint
   availability for later host testing; it is not a phone/network-device test.
5. A read-only post-publication preflight rejected the already existing release, as expected.
   No second publication or workflow dispatch was attempted.

The source and downloaded materials are preserved locally under
`D:/idea-projects/AutoJs6-Codegen-Distribution-Test/build/acceptance-run-37138706446/`.
Raw logs and downloaded artifacts remain ignored; this report contains only public aggregate
evidence and public integrity metadata.

## Phase boundaries

P5.4 now has the required real workflow-run link and successful release readback. The formal
P5.5 bootstrap already uses the separate production key `p256-f1a540e052cf0865`; this test
release does not replace that key or become a production model source.

The two requested maintainer inputs, signed trust material and the test workflow run, are
complete. Production `current` catalog publication remains aligned with P8.3 so an empty
remote v1 does not occupy the first official model's catalog identity. P5.1/P5.6 therefore
retain their recorded production-current-catalog boundary. P6.1 still needs its release/debug
configuration factory, followed by model storage, download, management and generation UI work.
No additional maintainer operation is required for this test release acceptance.
