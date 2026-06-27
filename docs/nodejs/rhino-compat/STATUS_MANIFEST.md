# Rhino Compat Status Manifest

`status-manifest.json` 是 Phase 11A 的人工维护迁移状态表。它把
`rhino-augment-api-inventory.json` 中的每个 Rhino augment module 和 API
entry 显式标注为 Node.js 迁移状态。

## Schema

- `schema`: `autojs6-node-rhino-compat-status-manifest-v1`
- `schemaVersion`: `1`
- `policy.explicitApiEntryCoverageRequired`: 必须为 `true`
- `policy.wildcardStatusAllowed`: 必须为 `false`
- `policy.defaultStatusForbidden`: 必须为 `true`

manifest 必须显式列出每个 module id 和每个 API entry id。新增 Rhino API
时，如果没有同步补充 manifest，后续 verification gate 必须失败并指出缺失
的 API id。

## Status

允许状态与 inventory schema 保持一致：

- `supported`
- `supported_async`
- `renamed`
- `partial`
- `blocked_by_design`
- `unsupported`

初始 manifest 由当前 source scan 和 Node bridge/types/docs gap evidence
生成：

- exact Node bridge/type method match 标记为 `supported_async`。
- 有相关 Node surface 但 API 形态未对齐时标记为 `partial`。
- 违反 Safe Node Profile 边界的 Java/thread/root automation 标记为
  `blocked_by_design`。
- 没有可用 Node surface evidence 的 API 标记为 `unsupported`。

这些状态是初始迁移决策，不是最终承诺。实现 compat facade、bridge method
或类型声明后，应同步更新 manifest、inventory report、gap report 和对应
测试。

## Regeneration

需要重建初始 manifest 时可运行：

```powershell
node tools/nodejs/phase11/generate-rhino-compat-status-manifest.js
```

重建后必须人工审查 diff，尤其是从 `partial`/`unsupported` 变为
`supported_async` 或 `blocked_by_design` 的项。
