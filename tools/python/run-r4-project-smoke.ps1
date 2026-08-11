param(
    [string]$Serial = "QV710AF65F",
    [string]$HostApkPath = "E:\AutoJs6-Worktree-Python\app\build\outputs\apk\app\debug\autojs6-v6.8.0-alpha7-arm64-v8a.apk",
    [string]$PluginApkPath = "D:\idea-projects\AutoJs6-Plugin-Python-Runtime\app\build\outputs\apk\debug\app-arm64-v8a-debug.apk",
    [string]$AndroidTestApkPath = "E:\AutoJs6-Worktree-Python\app\build\outputs\apk\androidTest\app\debug\app-app-debug-androidTest.apk",
    [Parameter(Mandatory = $true)][string]$HostApkSha256,
    [Parameter(Mandatory = $true)][string]$PluginApkSha256,
    [Parameter(Mandatory = $true)][string]$AndroidTestApkSha256,
    [string]$ExpectedSignerSha256 = "2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8",
    [Parameter(Mandatory = $true)][switch]$ConfirmDeviceMutation
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$sharedRunner = Join-Path $PSScriptRoot "run-r3-preview-smoke.ps1"
if (-not (Test-Path -LiteralPath $sharedRunner -PathType Leaf)) {
    throw "Shared exact-serial Python smoke runner is missing: $sharedRunner"
}

& $sharedRunner `
    -Serial $Serial `
    -HostApkPath $HostApkPath `
    -PluginApkPath $PluginApkPath `
    -AndroidTestApkPath $AndroidTestApkPath `
    -HostApkSha256 $HostApkSha256 `
    -PluginApkSha256 $PluginApkSha256 `
    -AndroidTestApkSha256 $AndroidTestApkSha256 `
    -ExpectedSignerSha256 $ExpectedSignerSha256 `
    -Selector "org.autojs.autojs.engine.PythonR4ProjectSmokeInstrumentationTest#projectLauncherRunsImportedModuleAndRelativeDataInPythonWorkspaceWithoutFallback" `
    -EnableArgument "autojs.python.r4p2.smoke.enabled" `
    -EvidenceLevel "R4_PROJECT_WORKSPACE_EXACT_DEVICE_SMOKE" `
    -ConfirmDeviceMutation:$ConfirmDeviceMutation
