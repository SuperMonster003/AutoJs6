param(
    [string] $LockFile = "tools/nodejs/runtime-build/runtime-build.lock.json",
    [string] $OutputDir = "build/node-runtime",
    [switch] $Execute
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$lockPath = Resolve-Path (Join-Path $repoRoot $LockFile)
$lock = Get-Content -Encoding UTF8 $lockPath | ConvertFrom-Json

Write-Host "AutoJs6 Node runtime build plan"
Write-Host "  target Node: $($lock.node.targetVersion)"
Write-Host "  current Node: $($lock.node.currentVersion)"
Write-Host "  NDK: $($lock.toolchain.ndkVersion)"
Write-Host "  output: $OutputDir"
Write-Host "  ABIs: $([string]::Join(', ', $lock.abis.PSObject.Properties.Name))"

if (-not $Execute) {
    Write-Host "Plan-only mode. Re-run with -Execute after source checksums, patches, and artifact signing are finalized."
    exit 0
}

Write-Error "Executable runtime build is intentionally blocked until S9-04 artifact sources and signing inputs are finalized."
