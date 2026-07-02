# Phase 14 Next Branch Handoff

Suggested next branch: `feature/nodejs-runtime-v1.3`

Status: handoff ready.

Start the next branch from the committed Phase 14 closeout state after
`:app:verifyNodePhase14CloseoutHandoff` reports `ready`.

## Carry Forward

- Use `docs/nodejs/reports/phase14/PHASE14_FUTURE_BACKLOG.json` as the v1.3 future promotion backlog.
- Keep Safe profile as the default profile.
- Keep `provider_poc_not_promoted` reports out of release notes as promoted
  runtime authority until their provider gates are reopened and closed with
  device, packaged, rollback, disclosure, and security evidence.
- Keep `worker_intentionally_unsupported_v1_2` until native worker ABI evidence
  becomes available.
- Keep `host_contract_ready` packaged aggregate smoke separate from production
  packaged install-run approval.
- do not promote raw Android object parity.

## First v1.3 Deployment Candidates

1. Reopen the Pro UI lane from `P14-07` through `P14-15`, starting with the
   packaged live UI and overlay evidence gaps.
2. Reopen the Pro task/device/media lane from `P14-17` through `P14-26`,
   starting with task database ownership and schema rollback.
3. Revisit native `worker_threads` only if target ABI evidence changes.
4. Convert any provider POC to opt-in promotion only after docs, types, release
   notes, security corpus, packaged evidence, and rollback behavior all agree.
