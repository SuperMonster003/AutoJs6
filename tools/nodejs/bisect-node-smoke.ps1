[CmdletBinding()]
param(
    [string] $IdentityProfile = "production",

    [switch] $RunDeviceSmoke,

    [string] $AndroidSerial = $env:ANDROID_SERIAL,

    [string] $DeviceTestFilter = "org.autojs.autojs.engine.EmbeddedScriptMvpInstrumentationTest#helloUnicodeAndStderrAreCaptured,org.autojs.autojs.engine.EmbeddedScriptMvpInstrumentationTest#commonJsLocalModulesSupportExportsJsonAndCache,org.autojs.autojs.engine.EmbeddedScriptMvpInstrumentationTest#scopedFsRejectsAbsoluteEscapeAndNulPaths",

    [string] $PackagedApk = "",

    [string] $ExpectedAbis = "arm64-v8a",

    [switch] $RunPackagedHostVerify
)

$ErrorActionPreference = "Stop"

function Get-GitOutput {
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

function Invoke-BisectCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string] $FilePath,

        [string[]] $Arguments = @()
    )

    Write-Host "> $FilePath $($Arguments -join ' ')"
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $FilePath $($Arguments -join ' ')"
    }
}

try {
    $repoRoot = Get-GitOutput @("rev-parse", "--show-toplevel")
    Set-Location $repoRoot

    $hostTasks = @(
        ":app:compileAppDebugKotlin",
        ":app:compileAppDebugAndroidTestKotlin",
        ":app:embeddedNodePackagingPreflightAppDebug",
        ":app:verifyNodeCompatibilityProfile",
        ":app:verifyNodeErrorCatalog",
        ":app:verifyNodeResolverVisualizerFixtures",
        ":app:verifyNodeDependencyBundlerFixtures",
        ":app:verifyNodeTemplateReproducibility",
        ":app:embeddedNodePackagedAppIntegrationDryRunAppDebug"
    )

    Invoke-BisectCommand ".\gradlew.bat" (
        @("--console=plain") +
        $hostTasks +
        @("-Pautojs.nodejs.identity=$IdentityProfile")
    )

    if (-not [string]::IsNullOrWhiteSpace($PackagedApk)) {
        Invoke-BisectCommand ".\gradlew.bat" @(
            "--console=plain",
            ":app:embeddedNodePackagedApkOutputVerify",
            "-Pautojs.nodejs.identity=$IdentityProfile",
            "-Pautojs.embeddedNode.packagedApk=$PackagedApk",
            "-Pautojs.embeddedNode.expectedAbis=$ExpectedAbis"
        )
    }

    if ($RunPackagedHostVerify) {
        Invoke-BisectCommand ".\gradlew.bat" @(
            "--console=plain",
            ":app:embeddedNodePackagedSmokeHostVerify",
            "-Pautojs.nodejs.identity=$IdentityProfile",
            "-Pautojs.embeddedNode.expectedAbis=$ExpectedAbis"
        )
    }

    if ($RunDeviceSmoke) {
        if ([string]::IsNullOrWhiteSpace($AndroidSerial)) {
            Write-Host "RunDeviceSmoke requested without AndroidSerial or ANDROID_SERIAL; skipping this bisect step."
            exit 125
        }
        $env:ANDROID_SERIAL = $AndroidSerial
        Invoke-BisectCommand ".\gradlew.bat" @(
            "--console=plain",
            ":app:connectedAppDebugAndroidTest",
            "-Pautojs.nodejs.identity=$IdentityProfile",
            "-Pautojs.nodejs.test.deviceProfile=smoke",
            "-Pautojs.nodejs.fuzzerBudget=quick",
            "-Pandroid.testInstrumentationRunnerArguments.class=$DeviceTestFilter"
        )
    }

    Write-Host "Node bisect smoke passed."
    exit 0
} catch {
    Write-Host $_
    exit 1
}
