# Phase 10 Security Corpus

S10-53 converts the Phase 10 attack-surface list into a host gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase10SecurityCorpus
```

The gate writes:

- `build/reports/nodejs/phase10-security-corpus.json`
- `build/reports/nodejs/phase10-security-corpus.md`

The corpus covers packaged ESM and dynamic-import escape, provider capability
bypass, package tar path escape, integrity mismatch, malicious package scripts,
long-running quota exhaustion, multi-instance cross-slot access, Worker bridge
access, WASI preopen escape, plugin/package namespace escape, and malformed
adapter ABI input.

Covered attack surfaces must point at executable code or an existing focused
gate. Policy-blocked surfaces stay explicit: multi-instance remains primary
slot only, `worker_threads` stays disabled, raw/controlled WASI is not promoted,
and `adapter_v1` remains blocked by the surface-only Node 24.5 adapter.
