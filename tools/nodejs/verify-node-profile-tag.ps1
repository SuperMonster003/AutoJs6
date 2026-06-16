[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $Tag,

    [string] $ExpectedCommit = "",

    [switch] $RunStaticChecks,

    [switch] $RunDeviceSmoke,

    [string] $AndroidSerial = $env:ANDROID_SERIAL
)

$ErrorActionPreference = "Stop"

function Invoke-NodeProfileCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string] $FilePath,

        [string[]] $Arguments = @()
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $FilePath $($Arguments -join ' ')"
    }
}

function Get-NodeProfileGitOutput {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $Arguments
    )

    $output = & git @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed."
    }
    return ($output -join "`n").Trim()
}

$repoRoot = Get-NodeProfileGitOutput @("rev-parse", "--show-toplevel")
Set-Location $repoRoot

$status = Get-NodeProfileGitOutput @("status", "--short")
if (-not [string]::IsNullOrWhiteSpace($status)) {
    throw "Worktree must be clean before verifying a Node Profile tag.`n$status"
}

$tagCommit = Get-NodeProfileGitOutput @("rev-list", "-n", "1", $Tag)
$headCommit = Get-NodeProfileGitOutput @("rev-parse", "HEAD")
if ($headCommit -ne $tagCommit) {
    throw "Current HEAD $headCommit does not match tag $Tag commit $tagCommit. Checkout the tag or its commit before verification."
}

if (-not [string]::IsNullOrWhiteSpace($ExpectedCommit) -and $tagCommit -ne $ExpectedCommit) {
    throw "Tag $Tag commit $tagCommit does not match expected commit $ExpectedCommit."
}

$manifestPath = Join-Path $repoRoot "app/build/reports/nodejs/release-manifest.json"
if (-not (Test-Path -LiteralPath $manifestPath)) {
    throw "Release manifest is missing: $manifestPath. Regenerate it before post-tag verification."
}

$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$manifestCommit = [string] $manifest.git.commit
if ($manifestCommit -ne $headCommit) {
    throw "Release manifest commit $manifestCommit does not match HEAD $headCommit."
}

$manifestTag = [string] $manifest.release.tag
if ($manifestTag -ne $Tag) {
    throw "Release manifest tag '$manifestTag' does not match requested tag '$Tag'."
}

if ([string]::IsNullOrWhiteSpace([string] $manifest.app.applicationId)) {
    throw "Release manifest app.applicationId is missing."
}

if ([string]::IsNullOrWhiteSpace([string] $manifest.app.label)) {
    throw "Release manifest app.label is missing."
}

if ([string]::IsNullOrWhiteSpace([string] $manifest.release.identityStatus)) {
    throw "Release manifest release.identityStatus is missing."
}

Write-Host "Node Profile tag manifest checks passed for $Tag at $headCommit."
Write-Host "Application ID: $($manifest.app.applicationId)"
Write-Host "App label: $($manifest.app.label)"
Write-Host "Identity status: $($manifest.release.identityStatus)"

if ($RunStaticChecks) {
    Invoke-NodeProfileCommand ".\gradlew.bat" @(
        "--console=plain",
        ":app:verifyNodeCompatibilityProfile",
        ":app:verifyNodeErrorCatalog",
        ":app:verifyNodeTemplateReproducibility",
        ":app:embeddedNodePackagingPreflightAppDebug",
        ":app:collectNodeProfileGaArtifacts"
    )
}

if ($RunDeviceSmoke) {
    if ([string]::IsNullOrWhiteSpace($AndroidSerial)) {
        throw "RunDeviceSmoke requires -AndroidSerial or ANDROID_SERIAL."
    }
    $env:ANDROID_SERIAL = $AndroidSerial
    Invoke-NodeProfileCommand ".\gradlew.bat" @(
        "--console=plain",
        ":app:connectedAppDebugAndroidTest",
        "-Pautojs.nodejs.test.deviceProfile=smoke",
        "-Pautojs.nodejs.fuzzerBudget=quick"
    )
}

Write-Host "Node Profile tag verification completed."
