[CmdletBinding(DefaultParameterSetName = "Source")]
param(
    [Parameter(ParameterSetName = "Source")]
    [switch] $SourceOnly,

    [Parameter(ParameterSetName = "Source")]
    [string] $SourceSiblingPath = "D:\idea-projects\AutoJs6-Plugin-Yolo-NCNN",

    [Parameter(Mandatory = $true, ParameterSetName = "Artifact")]
    [ValidateNotNullOrEmpty()]
    [string] $ProviderApk,

    [Parameter(Mandatory = $true, ParameterSetName = "Artifact")]
    [ValidateNotNullOrEmpty()]
    [string] $HostApk,

    [Parameter(Mandatory = $true, ParameterSetName = "Artifact")]
    [ValidateNotNullOrEmpty()]
    [string] $InstrumentationApk,

    [Parameter(Mandatory = $true, ParameterSetName = "Artifact")]
    [ValidateNotNullOrEmpty()]
    [string] $OfficialProductionSignerSha256,

    [Parameter(Mandatory = $true, ParameterSetName = "Artifact")]
    [ValidateRange(1, [long]::MaxValue)]
    [long] $ExpectedProviderVersionCode,

    [Parameter(Mandatory = $true, ParameterSetName = "Artifact")]
    [ValidateNotNullOrEmpty()]
    [string] $ExpectedMainCommit,

    [Parameter(Mandatory = $true, ParameterSetName = "Artifact")]
    [ValidateNotNullOrEmpty()]
    [string] $ExpectedProviderCommit,

    [Parameter(Mandatory = $true, ParameterSetName = "Artifact")]
    [ValidateNotNullOrEmpty()]
    [string] $ProviderBuildReceipt,

    [Parameter(Mandatory = $true, ParameterSetName = "Artifact")]
    [ValidateNotNullOrEmpty()]
    [string] $ControlledBuildReceipt,

    [Parameter(Mandatory = $true, ParameterSetName = "Artifact")]
    [ValidateNotNullOrEmpty()]
    [string] $SiblingPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$knownR5TestSigner = "2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8"
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$reportDirectory = Join-Path $repositoryRoot "build\reports\yolo"
$sourceReportPath = Join-Path $reportDirectory "r6-source.generated.json"
$artifactReportPath = Join-Path $reportDirectory "r6-publishable.generated.json"
$trustSourcePath = Join-Path $repositoryRoot "app\src\main\java\org\autojs\autojs\core\plugin\center\PluginTrustManager.kt"
$modelWeightsNotDistributedPattern = '(?is)model weights.*not\s+distributed'
$providerAllowedAssets = @(
    "assets/THIRD_PARTY_NOTICES.md",
    "assets/licenses/MPL-2.0.txt",
    "assets/licenses/ncnn-20260526.txt",
    "assets/third_party/ncnn/provenance.lock.json"
)
$providerRequiredMetadata = @(
    "META-INF/com/android/build/gradle/app-metadata.properties",
    "META-INF/version-control-info.textproto"
)
$providerRequiredKotlinMetadata = @(
    "kotlin/annotation/annotation.kotlin_builtins",
    "kotlin/collections/collections.kotlin_builtins",
    "kotlin/concurrent/atomics/atomics.kotlin_builtins",
    "kotlin/coroutines/coroutines.kotlin_builtins",
    "kotlin/internal/internal.kotlin_builtins",
    "kotlin/kotlin.kotlin_builtins",
    "kotlin/ranges/ranges.kotlin_builtins",
    "kotlin/reflect/reflect.kotlin_builtins"
)

function Assert-YoloR6Condition {
    param(
        [Parameter(Mandatory = $true)][bool] $Condition,
        [Parameter(Mandatory = $true)][string] $Message
    )
    if (-not $Condition) { throw $Message }
}

function Get-YoloR6RequiredFile {
    param(
        [Parameter(Mandatory = $true)][string] $Path,
        [Parameter(Mandatory = $true)][string] $Label
    )
    $fullPath = [System.IO.Path]::GetFullPath($Path)
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
        throw "$Label is missing: $fullPath"
    }
    return $fullPath
}

function Get-YoloR6RequiredDirectory {
    param(
        [Parameter(Mandatory = $true)][string] $Path,
        [Parameter(Mandatory = $true)][string] $Label
    )
    $fullPath = [System.IO.Path]::GetFullPath($Path)
    if (-not (Test-Path -LiteralPath $fullPath -PathType Container)) {
        throw "$Label is missing: $fullPath"
    }
    return $fullPath
}

function Invoke-YoloR6GitText {
    param(
        [Parameter(Mandatory = $true)][string] $Repository,
        [Parameter(Mandatory = $true)][string[]] $Arguments
    )
    $output = @(& git -C $Repository @Arguments 2>&1 | ForEach-Object { [string]$_ })
    if ($LASTEXITCODE -ne 0) { throw "Git command failed in $Repository`: git $($Arguments -join ' ')" }
    return ($output -join "`n").Trim()
}

function Get-YoloR6CleanRepositoryIdentity {
    param(
        [Parameter(Mandatory = $true)][string] $Repository,
        [Parameter(Mandatory = $true)][string] $ExpectedCommit,
        [Parameter(Mandatory = $true)][string] $Label
    )
    $root = Get-YoloR6RequiredDirectory -Path $Repository -Label "$Label repository"
    $expected = $ExpectedCommit.Trim().ToLowerInvariant()
    Assert-YoloR6Condition ($expected -cmatch '^[0-9a-f]{40}$') "$Label expected commit must be one full 40-character Git object ID"
    $head = Invoke-YoloR6GitText -Repository $root -Arguments @("rev-parse", "HEAD")
    Assert-YoloR6Condition ($head -ceq $expected) "$Label HEAD differs from its explicit expected commit"
    $status = Invoke-YoloR6GitText -Repository $root -Arguments @("status", "--porcelain=v1", "--untracked-files=all")
    Assert-YoloR6Condition ([string]::IsNullOrEmpty($status)) "$Label repository must be clean, including untracked files"
    return [pscustomobject][ordered]@{ path = $root; commit = $head; clean = $true }
}

function Get-YoloR6Sha256 {
    param([Parameter(Mandatory = $true)][string] $Path)
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Get-YoloR6BytesSha256 {
    param([Parameter(Mandatory = $true)][byte[]] $Bytes)
    $algorithm = [System.Security.Cryptography.SHA256]::Create()
    try {
        return (($algorithm.ComputeHash($Bytes) | ForEach-Object { $_.ToString("x2") }) -join "")
    } finally {
        $algorithm.Dispose()
    }
}

function Get-YoloR6ProviderZipPolicyEvidence {
    param(
        [Parameter(Mandatory = $true)][string] $ProviderApkPath,
        [Parameter(Mandatory = $true)][string] $SiblingRoot
    )
    $allowedAssets = @($providerAllowedAssets)
    $requiredMetadata = @($providerRequiredMetadata)
    $requiredKotlinMetadata = @($providerRequiredKotlinMetadata)
    $requiredCore = @("classes.dex", "AndroidManifest.xml", "resources.arsc")
    $expectedNative = "lib/arm64-v8a/libautojs_yolo.so"
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($ProviderApkPath)
    try {
        $fileEntries = @($archive.Entries | Where-Object { -not [string]::IsNullOrEmpty($_.Name) })
        $entries = @($fileEntries | ForEach-Object { $_.FullName })
        $instructionMatches = @($fileEntries | Where-Object { $_.FullName -cmatch '^res/[^/]+\.md$' })
        Assert-YoloR6Condition ($instructionMatches.Count -eq 1) "Provider APK must contain exactly one compiled markdown raw resource"
        $instructionMemory = [System.IO.MemoryStream]::new()
        try {
            $instructionStream = $instructionMatches[0].Open()
            try { $instructionStream.CopyTo($instructionMemory) } finally { $instructionStream.Dispose() }
            $packagedInstructionBytes = $instructionMemory.ToArray()
        } finally {
            $instructionMemory.Dispose()
        }
        $compiledInstructionEntry = $instructionMatches[0].FullName
    } finally {
        $archive.Dispose()
    }
    $instructionSourcePath = Get-YoloR6RequiredFile -Path (
        Join-Path $SiblingRoot "app\src\main\res\raw\plugin_instruction.md"
    ) -Label "Provider plugin instruction source"
    $instructionSourceBytes = [System.IO.File]::ReadAllBytes($instructionSourcePath)
    $instructionSourceSha = Get-YoloR6BytesSha256 -Bytes $instructionSourceBytes
    $packagedInstructionSha = Get-YoloR6BytesSha256 -Bytes $packagedInstructionBytes
    Assert-YoloR6Condition (
        $instructionSourceBytes.Length -eq $packagedInstructionBytes.Length -and
        $instructionSourceSha -ceq $packagedInstructionSha
    ) "Provider compiled markdown raw resource differs from plugin_instruction.md"
    $assets = @($entries | Where-Object { $_ -cmatch '^assets/' })
    $unexpectedAssets = @($assets | Where-Object { $allowedAssets -cnotcontains $_ })
    $missingAssets = @($allowedAssets | Where-Object { $assets -cnotcontains $_ })
    Assert-YoloR6Condition ($unexpectedAssets.Count -eq 0) (
        "Provider APK contains an asset outside the strict R6 allowlist: $($unexpectedAssets -join ', ')"
    )
    Assert-YoloR6Condition ($missingAssets.Count -eq 0) (
        "Provider APK is missing an allowlisted release asset: $($missingAssets -join ', ')"
    )
    $native = @($entries | Where-Object { $_ -cmatch '^lib/' })
    Assert-YoloR6Condition ($native.Count -eq 1 -and $native[0] -ceq $expectedNative) (
        "Provider APK native entries must equal only $expectedNative"
    )
    foreach ($requiredSet in @($requiredMetadata, $requiredKotlinMetadata, $requiredCore)) {
        $missing = @($requiredSet | Where-Object { $entries -cnotcontains $_ })
        Assert-YoloR6Condition ($missing.Count -eq 0) (
            "Provider APK is missing a required release entry: $($missing -join ', ')"
        )
    }
    $unknownTopLevelPayloads = @($entries | Where-Object {
        $_ -cnotin $allowedAssets -and
        $_ -cnotin $requiredMetadata -and
        $_ -cnotin $requiredKotlinMetadata -and
        $_ -cnotin $requiredCore -and
        $_ -cne $expectedNative -and
        $_ -cne $compiledInstructionEntry -and
        $_ -cnotmatch '^META-INF/(MANIFEST\.MF|[^/]+\.(SF|RSA|DSA|EC))$'
    })
    Assert-YoloR6Condition ($unknownTopLevelPayloads.Count -eq 0) (
        "Provider APK contains an entry outside the strict R6 release allowlist: $($unknownTopLevelPayloads -join ', ')"
    )
    return [pscustomobject][ordered]@{
        policy = "strict-entry-allowlist-v1"
        allowedAssets = @($allowedAssets)
        assetsExactlyMatch = $true
        requiredAndroidMetadata = @($requiredMetadata)
        requiredKotlinMetadata = @($requiredKotlinMetadata)
        requiredCoreEntries = @($requiredCore)
        nativeEntries = @($native)
        compiledInstruction = [ordered]@{
            sourcePath = $instructionSourcePath
            apkEntry = $compiledInstructionEntry
            length = $packagedInstructionBytes.Length
            sha256 = $packagedInstructionSha
            sourceAndPackagedBytesMatch = $true
        }
        allFileEntriesAllowed = $true
        entryCount = $entries.Count
    }
}

function Get-YoloR6ProviderBuildReceiptEvidence {
    param(
        [Parameter(Mandatory = $true)][string] $ReceiptPath,
        [Parameter(Mandatory = $true)][string] $SiblingRoot,
        [Parameter(Mandatory = $true)][string] $ExpectedProviderCommit,
        [Parameter(Mandatory = $true)][long] $ExpectedHostVersionCode,
        [Parameter(Mandatory = $true)][string] $ProductionApkSha256,
        [Parameter(Mandatory = $true)][string] $ExpectedNoticeSha256,
        [Parameter(Mandatory = $true)][string] $ExpectedModelPolicySha256,
        [Parameter(Mandatory = $true)][string] $ExpectedReleaseNoteSha256
    )
    $path = Get-YoloR6RequiredFile -Path $ReceiptPath -Label "Provider build receipt"
    $expectedReceiptPath = [System.IO.Path]::GetFullPath((Join-Path $SiblingRoot "build\reports\yolo\r6-provider-source.generated.json"))
    Assert-YoloR6Condition ($path -ceq $expectedReceiptPath) "Provider build receipt must use the fixed sibling report path"
    try { $receipt = Get-Content -Raw -LiteralPath $path | ConvertFrom-Json } catch { throw "Provider build receipt is invalid JSON" }
    Assert-YoloR6Condition ([int]$receipt.schemaVersion -eq 2) "Provider build receipt schemaVersion must be 2"
    Assert-YoloR6Condition ([string]$receipt.gate -ceq "yolo-r6-provider-source") "Provider build receipt gate identity differs"
    Assert-YoloR6Condition ([string]$receipt.mode -ceq "FULL_CLEAN_BUILD") "Provider build receipt is not a full clean build"
    Assert-YoloR6Condition ([string]$receipt.result -ceq "PASS") "Provider build receipt did not pass"
    Assert-YoloR6Condition ([bool]$receipt.buildIdentityProven) "Provider unsigned source-build identity was not proven"
    Assert-YoloR6Condition ([string]$receipt.source.preBuild.revision -ceq $ExpectedProviderCommit) "Provider build receipt pre-build revision differs"
    Assert-YoloR6Condition ([string]$receipt.source.postBuild.revision -ceq $ExpectedProviderCommit) "Provider build receipt post-build revision differs"
    Assert-YoloR6Condition ([bool]$receipt.source.preBuild.clean -and [bool]$receipt.source.postBuild.clean) "Provider build receipt was not clean before and after build"
    Assert-YoloR6Condition (@($receipt.source.preBuild.status).Count -eq 0 -and @($receipt.source.postBuild.status).Count -eq 0) "Provider build receipt contains dirty status rows"
    Assert-YoloR6Condition ([bool]$receipt.source.unchangedAcrossBuild) "Provider source changed across its build"
    Assert-YoloR6Condition ([string]$receipt.source.repository -ceq $SiblingRoot) "Provider build receipt repository path differs"
    $expectedBuildArguments = @(
        "--no-daemon",
        ":app:clean",
        ":app:testDebugUnitTest",
        ":app:assembleRelease",
        ":app:assembleRc"
    )
    Assert-YoloR6Condition ([string]$receipt.build.status -ceq "PASS") "Provider build receipt build status did not pass"
    Assert-YoloR6Condition ([string]$receipt.build.executable -ceq ".\gradlew.bat") "Provider build receipt executable differs"
    Assert-YoloR6Condition ((@($receipt.build.arguments) -join "`n") -ceq ($expectedBuildArguments -join "`n")) "Provider build receipt arguments differ"
    Assert-YoloR6Condition ([bool]$receipt.build.cleanTaskIncluded) "Provider build receipt omitted the clean task"
    Assert-YoloR6Condition (-not [string]::IsNullOrWhiteSpace([string]$receipt.build.startedAtUtc)) "Provider build receipt has no build start"
    Assert-YoloR6Condition (-not [string]::IsNullOrWhiteSpace([string]$receipt.build.finishedAtUtc)) "Provider build receipt has no build finish"
    Assert-YoloR6Condition (
        [string]$receipt.tests.status -ceq "PASS" -and
        [int]$receipt.tests.totalTests -gt 0 -and
        [int]$receipt.tests.totalFailures -eq 0 -and
        [int]$receipt.tests.totalErrors -eq 0 -and
        [int]$receipt.tests.totalSkipped -eq 0
    ) "Provider build receipt focused JVM tests are not an exact clean pass"
    Assert-YoloR6Condition (-not [bool]$receipt.release.signed) "Provider source-build release is expected to remain unsigned"
    Assert-YoloR6Condition ([bool]$receipt.release.minified -and [bool]$receipt.release.resourcesShrunk) "Provider build receipt lacks shrinker admission"
    Assert-YoloR6Condition ([string]$receipt.release.packageVerification -ceq "PASS") "Provider build receipt package verification did not pass"
    Assert-YoloR6Condition ([string]$receipt.release.abi -ceq "arm64-v8a") "Provider build receipt ABI differs"
    Assert-YoloR6Condition (-not [bool]$receipt.release.modelOrImagePayloads) "Provider build receipt reports a model/image payload"
    $receiptAssets = @($receipt.release.assetEntries | ForEach-Object { [string]$_ })
    $unexpectedReceiptAssets = @($receiptAssets | Where-Object { $providerAllowedAssets -cnotcontains $_ })
    $missingReceiptAssets = @($providerAllowedAssets | Where-Object { $receiptAssets -cnotcontains $_ })
    Assert-YoloR6Condition (
        $receiptAssets.Count -eq $providerAllowedAssets.Count -and
        (@($receiptAssets | Select-Object -Unique).Count -eq $receiptAssets.Count) -and
        $unexpectedReceiptAssets.Count -eq 0 -and
        $missingReceiptAssets.Count -eq 0
    ) "Provider build receipt assets differ from the strict release allowlist"
    Assert-YoloR6Condition ([long]$receipt.identity.requiresHostVersion -eq $ExpectedHostVersionCode) "Provider build receipt Host boundary differs"
    $expectedProviderContract = [ordered]@{
        runtimeComponent = "io.github.supermonster003.autojs6.plugin.yolo.ncnn/io.github.supermonster003.autojs6.plugin.yolo.ncnn.provider.YoloProviderService"
        protocolApiMin = "1.0"
        protocolApiMax = "1.0"
        backend = "ncnn"
        task = "detect"
        decoder = "ultralytics-detect"
        supportedAbis = @("arm64-v8a")
    }
    foreach ($field in @("runtimeComponent", "protocolApiMin", "protocolApiMax", "backend", "task", "decoder")) {
        Assert-YoloR6Condition (
            [string]$receipt.identity.$field -ceq [string]$expectedProviderContract[$field]
        ) "Provider build receipt release contract field differs: $field"
    }
    $receiptSupportedAbis = @($receipt.identity.supportedAbis | ForEach-Object { [string]$_ })
    Assert-YoloR6Condition (
        $receiptSupportedAbis.Count -eq 1 -and $receiptSupportedAbis[0] -ceq $expectedProviderContract.supportedAbis[0]
    ) "Provider build receipt supported ABI contract differs"
    Assert-YoloR6Condition ([string]$receipt.compliance.thirdPartyNoticesSha256 -ceq $ExpectedNoticeSha256) "Provider build receipt notice SHA-256 differs"
    Assert-YoloR6Condition ([string]$receipt.compliance.modelLicensePolicySha256 -ceq $ExpectedModelPolicySha256) "Provider build receipt model-policy SHA-256 differs"
    Assert-YoloR6Condition ([string]$receipt.compliance.releaseNotesDraftSha256 -ceq $ExpectedReleaseNoteSha256) "Provider build receipt release-note SHA-256 differs"
    $unsignedReleasePath = Get-YoloR6RequiredFile -Path ([string]$receipt.release.artifact.path) -Label "Provider receipt unsigned release APK"
    $expectedUnsignedRoot = [System.IO.Path]::GetFullPath((Join-Path $SiblingRoot "app\build\outputs\apk\release"))
    Assert-YoloR6Condition ([System.IO.Path]::GetFullPath((Split-Path -Parent $unsignedReleasePath)) -ceq $expectedUnsignedRoot) (
        "Provider receipt unsigned release APK is outside the fixed Gradle output directory"
    )
    Assert-YoloR6Condition ((Get-YoloR6Sha256 -Path $unsignedReleasePath) -ceq [string]$receipt.release.artifact.sha256) (
        "Provider receipt unsigned release APK SHA-256 is stale or mismatched"
    )
    Assert-YoloR6Condition ((Get-Item -LiteralPath $unsignedReleasePath).Length -eq [long]$receipt.release.artifact.bytes) (
        "Provider receipt unsigned release APK length is stale or mismatched"
    )
    Assert-YoloR6Condition ([bool]$receipt.release.artifact.freshAfterBuildStart) "Provider receipt release APK was not freshly built"
    $instructionSourcePath = Get-YoloR6RequiredFile -Path (
        Join-Path $SiblingRoot "app\src\main\res\raw\plugin_instruction.md"
    ) -Label "Provider plugin instruction source"
    Assert-YoloR6Condition (
        [string]$receipt.release.pluginInstructionSha256 -ceq (Get-YoloR6Sha256 -Path $instructionSourcePath)
    ) "Provider build receipt plugin instruction SHA-256 differs from source"
    $producerScriptPath = Get-YoloR6RequiredFile -Path (
        Join-Path $SiblingRoot "tools\verify-r6-provider-source.ps1"
    ) -Label "Provider build receipt producer source"
    # A JSON property is not a cryptographic source attestation. Even if a hand-edited receipt
    # claims a signed APK, this verifier never promotes it. A future controlled build producer must
    # define and verify its own authenticated receipt contract before this boundary can become PASS.
    return [pscustomobject][ordered]@{
        path = $path
        sha256 = Get-YoloR6Sha256 -Path $path
        schemaVersion = 2
        sourceRevision = [string]$receipt.source.preBuild.revision
        producerScriptPath = $producerScriptPath
        producerScriptSha256 = Get-YoloR6Sha256 -Path $producerScriptPath
        clean = $true
        unsignedReleaseSha256 = [string]$receipt.release.artifact.sha256
        unsignedReleaseSigned = $false
        minified = $true
        resourcesShrunk = $true
        contract = $expectedProviderContract
        inspectedProductionApkSha256 = $ProductionApkSha256
        controlledSignedBuildReceipt = "NOT_AVAILABLE"
        sourceBuildLinkage = "CONTROLLED_PRODUCER_DECLARED_UNSIGNED_CLEAN_BUILD"
        cryptographicSourceAttestation = $false
    }
}

function Invoke-YoloR6ExternalText {
    param(
        [Parameter(Mandatory = $true)][string] $FilePath,
        [Parameter(Mandatory = $true)][string[]] $Arguments,
        [Parameter(Mandatory = $true)][string] $Label
    )
    $output = @(& $FilePath @Arguments 2>&1 | ForEach-Object { [string]$_ })
    if ($LASTEXITCODE -ne 0) { throw "$Label failed" }
    return ($output -join "`n")
}

function Get-YoloR6ApkProjectRevision {
    param(
        [Parameter(Mandatory = $true)][string] $ApkPath,
        [Parameter(Mandatory = $true)][string] $Label
    )
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($ApkPath)
    try {
        $entries = @($archive.Entries | Where-Object { $_.FullName -ceq "META-INF/version-control-info.textproto" })
        Assert-YoloR6Condition ($entries.Count -eq 1) "$Label must contain one Gradle VCS metadata entry"
        $stream = $entries[0].Open()
        $reader = [System.IO.StreamReader]::new($stream)
        try { $text = $reader.ReadToEnd() } finally { $reader.Dispose(); $stream.Dispose() }
    } finally {
        $archive.Dispose()
    }
    $projectBlocks = @([regex]::Matches($text, '(?ms)repositories\s*\{(?<body>.*?)\}') | Where-Object {
        $_.Groups['body'].Value -match '(?m)^\s*local_root_path:\s*"\$PROJECT_DIR"\s*$'
    })
    Assert-YoloR6Condition ($projectBlocks.Count -eq 1) "$Label VCS metadata must contain one PROJECT_DIR repository"
    $revision = [regex]::Match($projectBlocks[0].Groups['body'].Value, '(?m)^\s*revision:\s*"([0-9a-f]{40})"\s*$')
    Assert-YoloR6Condition $revision.Success "$Label PROJECT_DIR VCS metadata has no Git revision"
    return $revision.Groups[1].Value
}

function Get-YoloR6ControlledBuildReceiptEvidence {
    param(
        [Parameter(Mandatory = $true)][string] $ReceiptPath,
        [Parameter(Mandatory = $true)][string] $HostApkPath,
        [Parameter(Mandatory = $true)][string] $ProviderApkPath,
        [Parameter(Mandatory = $true)][string] $InstrumentationApkPath,
        [Parameter(Mandatory = $true)][string] $ExpectedMainCommit,
        [Parameter(Mandatory = $true)][string] $ExpectedProviderCommit,
        [Parameter(Mandatory = $true)][string] $ExpectedSigner,
        [Parameter(Mandatory = $true)][long] $ExpectedHostVersionCode,
        [Parameter(Mandatory = $true)][long] $ExpectedProviderVersionCode,
        [Parameter(Mandatory = $true)][object] $ProviderBuildEvidence,
        [Parameter(Mandatory = $true)][object] $AndroidTools,
        [Parameter(Mandatory = $true)][string] $SiblingRoot
    )
    $path = Get-YoloR6RequiredFile -Path $ReceiptPath -Label "controlled signed-build receipt"
    $expectedPath = [System.IO.Path]::GetFullPath((Join-Path $reportDirectory "r6-controlled-signed-build.generated.json"))
    Assert-YoloR6Condition ($path -ceq $expectedPath) "Controlled signed-build receipt must use its fixed report path"
    try { $receipt = Get-Content -Raw -LiteralPath $path | ConvertFrom-Json } catch { throw "Controlled signed-build receipt is invalid JSON" }
    Assert-YoloR6Condition ([int]$receipt.schemaVersion -eq 1) "Controlled signed-build receipt schema differs"
    Assert-YoloR6Condition ([string]$receipt.gate -ceq "yolo-r6-controlled-signed-build") "Controlled signed-build gate identity differs"
    Assert-YoloR6Condition ([string]$receipt.mode -ceq "CONTROLLED_CLEAN_SIGNED_BUILD") "Controlled signed-build mode differs"
    Assert-YoloR6Condition ([string]$receipt.result -ceq "PASS") "Controlled signed-build receipt did not pass"
    Assert-YoloR6Condition ([string]$receipt.receiptSemantics -ceq "CONTROLLED_BUILD_RECEIPT_NOT_CRYPTOGRAPHIC_ATTESTATION") (
        "Controlled receipt must not claim cryptographic attestation"
    )
    $producerPath = Get-YoloR6RequiredFile -Path (Join-Path $PSScriptRoot "produce-yolo-r6-signed-artifacts.ps1") -Label "controlled producer source"
    Assert-YoloR6Condition ([System.IO.Path]::GetFullPath([string]$receipt.producer.path) -ceq $producerPath) "Controlled receipt producer path differs"
    Assert-YoloR6Condition ([string]$receipt.producer.sha256 -ceq (Get-YoloR6Sha256 $producerPath)) "Controlled receipt producer source SHA-256 is stale"
    Assert-YoloR6Condition ([bool]$receipt.producer.sameProcessPreBuildPostBuildAndArtifactVerification) "Controlled producer did not bind its checks in one process"
    Assert-YoloR6Condition ([string]$receipt.producer.externalGradleClientChecks -ceq "PASS") "Controlled producer did not prove an exclusive Gradle window"

    foreach ($source in @(
        [pscustomobject]@{ value = $receipt.source.host; revision = $ExpectedMainCommit; label = "Host" },
        [pscustomobject]@{ value = $receipt.source.provider; revision = $ExpectedProviderCommit; label = "Provider" }
    )) {
        Assert-YoloR6Condition ([bool]$source.value.unchanged) "$($source.label) source was not stable across controlled build"
        Assert-YoloR6Condition ([string]$source.value.preBuild.revision -ceq $source.revision -and [string]$source.value.postBuild.revision -ceq $source.revision) "$($source.label) controlled source revision differs"
        Assert-YoloR6Condition ([bool]$source.value.preBuild.clean -and [bool]$source.value.postBuild.clean) "$($source.label) controlled source was not clean"
        Assert-YoloR6Condition (@($source.value.preBuild.status).Count -eq 0 -and @($source.value.postBuild.status).Count -eq 0) "$($source.label) controlled source status rows are nonempty"
    }

    Assert-YoloR6Condition ([string]$receipt.signing.officialCertificateSha256 -ceq $ExpectedSigner) "Controlled receipt signer differs"
    Assert-YoloR6Condition ([string]$receipt.signing.pinnedSource.certificateSha256 -ceq $ExpectedSigner) "Controlled receipt signer is not Host-pinned"
    Assert-YoloR6Condition (-not [bool]$receipt.signing.propertiesValuesRecorded -and -not [bool]$receipt.signing.propertiesDigestRecorded -and -not [bool]$receipt.signing.aliasRecorded) "Controlled receipt exposed signing-property material"
    Assert-YoloR6Condition (-not [bool]$receipt.signing.secretValuesPassedOnCommandLine) "Controlled producer passed a signing secret on a command line"
    Assert-YoloR6Condition ([bool]$receipt.signing.temporaryCopiesDeleted -and [bool]$receipt.signing.temporaryCopiesVerifiedAbsent) "Controlled producer did not prove signing-copy cleanup"
    foreach ($secretPath in @(
        (Join-Path $repositoryRoot "sign.properties"),
        (Join-Path $repositoryRoot "app/sm003.jks"),
        (Join-Path $SiblingRoot "sign.properties"),
        (Join-Path $SiblingRoot "app/sm003.jks")
    )) {
        Assert-YoloR6Condition (-not (Test-Path -LiteralPath $secretPath)) "A temporary signing copy remains after the controlled build"
    }

    Assert-YoloR6Condition ([string]$receipt.providerUnsignedAdmission.receiptSha256 -ceq [string]$ProviderBuildEvidence.sha256) "Controlled receipt does not bind the admitted Provider source receipt"
    Assert-YoloR6Condition ([string]$receipt.providerUnsignedAdmission.artifactSha256 -ceq [string]$ProviderBuildEvidence.unsignedReleaseSha256) "Controlled receipt does not bind the admitted unsigned Provider APK"
    Assert-YoloR6Condition ([bool]$receipt.providerUnsignedAdmission.fullUnsignedEvidenceRebuiltAfterSignedBuild) "Controlled producer did not rebuild the complete unsigned Provider admission bundle"
    foreach ($field in @("runtimeComponent", "protocolApiMin", "protocolApiMax", "backend", "task", "decoder")) {
        Assert-YoloR6Condition (
            [string]$receipt.providerUnsignedAdmission.contract.$field -ceq [string]$ProviderBuildEvidence.contract.$field
        ) "Controlled receipt Provider contract differs from the fresh source receipt: $field"
    }
    $controlledSupportedAbis = @($receipt.providerUnsignedAdmission.contract.supportedAbis | ForEach-Object { [string]$_ })
    $sourceSupportedAbis = @($ProviderBuildEvidence.contract.supportedAbis | ForEach-Object { [string]$_ })
    Assert-YoloR6Condition (
        $controlledSupportedAbis.Count -eq 1 -and
        $sourceSupportedAbis.Count -eq 1 -and
        $controlledSupportedAbis[0] -ceq $sourceSupportedAbis[0]
    ) "Controlled receipt Provider supported ABI contract differs from the fresh source receipt"

    $expectedProviderArguments = @(
        "--no-daemon",
        "-Pautojs.gradle.build.number.auto.increment.enabled=false",
        "-Pautojs.gradle.build.time.update.enabled=false",
        ":app:clean",
        ":app:testDebugUnitTest",
        ":app:assembleRelease"
    )
    $expectedHostArguments = @(
        "--no-daemon",
        "-Pautojs.gradle.build.number.auto.increment.enabled=false",
        "-Pautojs.gradle.build.time.update.enabled=false",
        "-PyoloR6ReleaseAndroidTest=true",
        ":app:clean",
        ":app:assembleAppRelease",
        ":app:assembleAppReleaseAndroidTest"
    )
    Assert-YoloR6Condition ([string]$receipt.builds.provider.status -ceq "PASS" -and (@($receipt.builds.provider.arguments) -join "`n") -ceq ($expectedProviderArguments -join "`n")) "Controlled Provider build invocation differs"
    Assert-YoloR6Condition ([string]$receipt.builds.hostAndInstrumentation.status -ceq "PASS" -and (@($receipt.builds.hostAndInstrumentation.arguments) -join "`n") -ceq ($expectedHostArguments -join "`n")) "Controlled Host build invocation differs"
    Assert-YoloR6Condition ([string]$receipt.hostOrdinaryBuildClone.method -ceq "LOCAL_GIT_CLONE_NO_HARDLINKS_DETACHED_EXACT_COMMIT" -and [bool]$receipt.hostOrdinaryBuildClone.ordinaryGitDirectory -and [bool]$receipt.hostOrdinaryBuildClone.removedAfterBuild) "Controlled Host was not built from the required removed ordinary local clone"
    Assert-YoloR6Condition ([string]$receipt.hostOrdinaryBuildClone.sourceRevision -ceq $ExpectedMainCommit) "Controlled Host ordinary clone revision differs"
    Assert-YoloR6Condition ([string]$receipt.builds.hostAndInstrumentation.ordinaryClonePostBuild.revision -ceq $ExpectedMainCommit -and [bool]$receipt.builds.hostAndInstrumentation.ordinaryClonePostBuild.clean) "Controlled Host ordinary clone changed during build"
    Assert-YoloR6Condition (-not (Test-Path -LiteralPath (Join-Path $reportDirectory "r6-main-build-clone.staging"))) "Temporary ordinary Host build clone remains"

    foreach ($artifact in @(
        [pscustomobject]@{ receipt = $receipt.artifacts.host; path = $HostApkPath; label = "Host" },
        [pscustomobject]@{ receipt = $receipt.artifacts.provider; path = $ProviderApkPath; label = "Provider" },
        [pscustomobject]@{ receipt = $receipt.artifacts.instrumentation; path = $InstrumentationApkPath; label = "Instrumentation" }
    )) {
        $actualPath = Get-YoloR6RequiredFile -Path $artifact.path -Label "$($artifact.label) controlled artifact"
        Assert-YoloR6Condition ([System.IO.Path]::GetFullPath([string]$artifact.receipt.file.path) -ceq $actualPath) "$($artifact.label) receipt path differs"
        Assert-YoloR6Condition ([string]$artifact.receipt.file.sha256 -ceq (Get-YoloR6Sha256 $actualPath)) "$($artifact.label) receipt SHA-256 is stale"
        Assert-YoloR6Condition ([long]$artifact.receipt.file.length -eq (Get-Item -LiteralPath $actualPath).Length) "$($artifact.label) receipt length is stale"
        Assert-YoloR6Condition ([bool]$artifact.receipt.file.freshAfterBuildStart) "$($artifact.label) was not fresh after controlled build start"
        Assert-YoloR6Condition ([string]$artifact.receipt.signerSha256 -ceq $ExpectedSigner) "$($artifact.label) receipt signer differs"
    }
    Assert-YoloR6Condition ([long]$receipt.artifacts.host.identity.versionCode -eq $ExpectedHostVersionCode) "Controlled Host version differs"
    Assert-YoloR6Condition ([long]$receipt.artifacts.provider.identity.versionCode -eq $ExpectedProviderVersionCode) "Controlled Provider version differs"
    Assert-YoloR6Condition ([bool]$receipt.artifacts.provider.minified -and [bool]$receipt.artifacts.provider.resourcesShrunk) "Controlled Provider lacks shrinker evidence"
    Assert-YoloR6Condition ([string]$receipt.artifacts.provider.abi -ceq "arm64-v8a") "Controlled Provider ABI differs"
    Assert-YoloR6Condition ([string]$receipt.artifacts.instrumentation.identity.packageName -ceq "org.autojs.autojs6.test") "Controlled instrumentation package differs"
    Assert-YoloR6Condition ([string]$receipt.artifacts.instrumentation.identity.instrumentation.targetPackage -ceq "org.autojs.autojs6") "Controlled instrumentation target differs"
    Assert-YoloR6Condition ([string]$receipt.artifacts.instrumentation.releaseVariantInvocation -ceq "SAME_CONTROLLED_INVOCATION_AS_HOST") "Instrumentation was not linked to the controlled Host release invocation"
    Assert-YoloR6Condition ([long]$receipt.artifacts.instrumentation.controlledInvocationHostVersionCode -eq $ExpectedHostVersionCode) "Controlled invocation Host version differs"
    Assert-YoloR6Condition ([string]$receipt.artifacts.instrumentation.controlledInvocationHostApkSha256 -ceq (Get-YoloR6Sha256 $HostApkPath)) "Controlled invocation Host hash differs"
    Assert-YoloR6Condition ([bool]$receipt.artifacts.instrumentation.targetVersionNotIndependentlyDerivedFromTestApk) "Instrumentation receipt overstates target-version derivation"

    $apkSigner = Get-YoloR6RequiredFile -Path ([string]$AndroidTools.apkSigner) -Label "R5-selected apksigner"
    $aapt2 = Get-YoloR6RequiredFile -Path ([string]$AndroidTools.aapt2) -Label "R5-selected aapt2"
    $testSignatureText = Invoke-YoloR6ExternalText -FilePath $apkSigner -Arguments @("verify", "--verbose", "--print-certs", $InstrumentationApkPath) -Label "Instrumentation signature verification"
    $testDigests = @([regex]::Matches($testSignatureText, '(?im)certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$') | ForEach-Object { $_.Groups[1].Value.ToLowerInvariant() } | Select-Object -Unique)
    Assert-YoloR6Condition ($testDigests.Count -eq 1 -and $testDigests[0] -ceq $ExpectedSigner) "Instrumentation is not signed by the official production certificate"
    $testBadging = Invoke-YoloR6ExternalText -FilePath $aapt2 -Arguments @("dump", "badging", $InstrumentationApkPath) -Label "Instrumentation identity inspection"
    Assert-YoloR6Condition ($testBadging -match "(?m)^package: name='org\.autojs\.autojs6\.test'") "Instrumentation package identity differs"
    Assert-YoloR6Condition ($testBadging -match "(?m)^instrumentation: name='androidx\.test\.runner\.AndroidJUnitRunner' targetPackage='org\.autojs\.autojs6'") "Instrumentation does not target the production Host"

    $hostVcs = Get-YoloR6ApkProjectRevision -ApkPath $HostApkPath -Label "Host"
    $providerVcs = Get-YoloR6ApkProjectRevision -ApkPath $ProviderApkPath -Label "Provider"
    Assert-YoloR6Condition ($hostVcs -ceq $ExpectedMainCommit -and [string]$receipt.artifacts.host.vcsRevision -ceq $ExpectedMainCommit) "Host signed artifact is not bound to the expected source revision"
    Assert-YoloR6Condition ($providerVcs -ceq $ExpectedProviderCommit -and [string]$receipt.artifacts.provider.vcsRevision -ceq $ExpectedProviderCommit) "Provider signed artifact is not bound to the expected source revision"

    Assert-YoloR6Condition ([string]$receipt.releaseBoundary.upgradeRuntimeEvidence -ceq "NOT_RUN_BY_PRODUCT_DECISION") "Upgrade evidence boundary differs from product decision"
    Assert-YoloR6Condition ([string]$receipt.releaseBoundary.versionRollbackEvidence -ceq "NOT_RUN_BY_PRODUCT_DECISION") "Rollback evidence boundary differs from product decision"
    Assert-YoloR6Condition (-not [bool]$receipt.releaseBoundary.predecessorArtifactApplicable) "Controlled receipt unexpectedly claims a predecessor artifact"
    return [pscustomobject][ordered]@{
        path = $path
        sha256 = Get-YoloR6Sha256 $path
        schemaVersion = 1
        exactArtifactsReverified = $true
        signedArtifactSourceLinkageVerified = $true
        cleanSourceBuildIdentityVerified = $true
        secretCleanupReverified = $true
        receiptIsCryptographicAttestation = $false
        instrumentationApk = [ordered]@{ path = $InstrumentationApkPath; sha256 = Get-YoloR6Sha256 $InstrumentationApkPath; signerSha256 = $ExpectedSigner }
    }
}

function Get-YoloR6PackagedNoticeEvidence {
    param(
        [Parameter(Mandatory = $true)][string] $ProviderApkPath,
        [Parameter(Mandatory = $true)][string] $SiblingRoot,
        [Parameter(Mandatory = $true)][long] $ExpectedMinHostVersionCode
    )
    $rootNoticePath = Get-YoloR6RequiredFile -Path (Join-Path $SiblingRoot "THIRD_PARTY_NOTICES.md") -Label "Provider third-party notice"
    $assetNoticePath = Get-YoloR6RequiredFile -Path (Join-Path $SiblingRoot "app\src\main\assets\THIRD_PARTY_NOTICES.md") -Label "Provider notice asset source"
    $modelPolicyPath = Get-YoloR6RequiredFile -Path (Join-Path $SiblingRoot "docs\model-license-policy.md") -Label "Provider model license policy"
    $releaseNotePath = Get-YoloR6RequiredFile -Path (Join-Path $SiblingRoot "docs\release-notes\0.1.0.md") -Label "Provider release note draft"
    $rootNoticeBytes = [System.IO.File]::ReadAllBytes($rootNoticePath)
    $assetNoticeBytes = [System.IO.File]::ReadAllBytes($assetNoticePath)
    $rootNoticeSha = Get-YoloR6BytesSha256 -Bytes $rootNoticeBytes
    $assetNoticeSha = Get-YoloR6BytesSha256 -Bytes $assetNoticeBytes
    Assert-YoloR6Condition ($rootNoticeBytes.Length -gt 0) "Provider THIRD_PARTY_NOTICES.md is empty"
    Assert-YoloR6Condition (
        $rootNoticeBytes.Length -eq $assetNoticeBytes.Length -and $rootNoticeSha -ceq $assetNoticeSha
    ) "Provider repository and Android asset THIRD_PARTY_NOTICES.md differ"
    $noticeText = [System.Text.UTF8Encoding]::new($false, $true).GetString($rootNoticeBytes)
    foreach ($pattern in @(
        'MPL-2\.0',
        'NCNN',
        'BSD 3-Clause|BSD-3-Clause',
        $modelWeightsNotDistributedPattern,
        'original licenses and usage conditions continue to apply'
    )) {
        Assert-YoloR6Condition ($noticeText -cmatch $pattern) "Provider third-party notice is missing required marker: $pattern"
    }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($ProviderApkPath)
    try {
        $matches = @($archive.Entries | Where-Object { $_.FullName -ceq "assets/THIRD_PARTY_NOTICES.md" })
        Assert-YoloR6Condition ($matches.Count -eq 1) "Provider APK must contain exactly one assets/THIRD_PARTY_NOTICES.md"
        $memory = [System.IO.MemoryStream]::new()
        try {
            $stream = $matches[0].Open()
            try { $stream.CopyTo($memory) } finally { $stream.Dispose() }
            $packagedBytes = $memory.ToArray()
        } finally {
            $memory.Dispose()
        }
    } finally {
        $archive.Dispose()
    }
    $packagedSha = Get-YoloR6BytesSha256 -Bytes $packagedBytes
    Assert-YoloR6Condition (
        $packagedBytes.Length -eq $assetNoticeBytes.Length -and $packagedSha -ceq $assetNoticeSha
    ) "Packaged THIRD_PARTY_NOTICES.md differs from its source asset"

    $modelPolicyText = Get-Content -Raw -LiteralPath $modelPolicyPath
    Assert-YoloR6Condition ($modelPolicyText -cmatch 'contains no model weights') "Model license policy does not state the model-free release boundary"
    Assert-YoloR6Condition ($modelPolicyText -cmatch 'converting an Ultralytics checkpoint to NCNN does\s+not change') "Model license policy omits conversion-license continuity"
    Assert-YoloR6Condition ($modelPolicyText -cmatch 'local validation\s+only') "Model license policy omits local-only fixture scope"
    $releaseNoteText = Get-Content -Raw -LiteralPath $releaseNotePath
    foreach ($pattern in @(
        'Draft only',
        "minimum version code: ``$ExpectedMinHostVersionCode``",
        'ABI: `arm64-v8a` only',
        'Backend: NCNN `20260526`',
        'CPU only',
        'Decoder: `ultralytics-detect`',
        'includes no model'
    )) {
        Assert-YoloR6Condition ($releaseNoteText -cmatch $pattern) "Provider release note draft is missing scoped claim: $pattern"
    }
    return [pscustomobject][ordered]@{
        thirdPartyNotice = [ordered]@{
            rootPath = $rootNoticePath
            sourceAssetPath = $assetNoticePath
            apkEntry = "assets/THIRD_PARTY_NOTICES.md"
            length = $packagedBytes.Length
            sha256 = $packagedSha
            sourceAndPackagedBytesMatch = $true
            requiredMarkersPresent = $true
        }
        modelLicensePolicy = [ordered]@{
            path = $modelPolicyPath
            sha256 = Get-YoloR6Sha256 -Path $modelPolicyPath
            repoOnly = $true
        }
        releaseNoteDraft = [ordered]@{
            path = $releaseNotePath
            sha256 = Get-YoloR6Sha256 -Path $releaseNotePath
            repoOnly = $true
            productionClaim = $false
        }
    }
}

function Get-YoloR6SignerDigest {
    param([Parameter(Mandatory = $true)][string] $Value)
    $digest = $Value.Trim().ToLowerInvariant()
    Assert-YoloR6Condition ($digest -cmatch '^[0-9a-f]{64}$') (
        "OfficialProductionSignerSha256 must be exactly 64 hexadecimal characters"
    )
    return $digest
}

function Get-YoloR6OfficialSignerSource {
    $path = Get-YoloR6RequiredFile -Path $trustSourcePath -Label "PluginTrustManager source"
    $text = Get-Content -Raw -LiteralPath $path
    $matches = [regex]::Matches(
        $text,
        '(?s)\bval\s+OFFICIAL_SHA_256\s*=\s*setOf\s*\((.*?)\)'
    )
    Assert-YoloR6Condition ($matches.Count -eq 1) (
        "PluginTrustManager must expose exactly one parseable OFFICIAL_SHA_256 set"
    )
    $body = $matches[0].Groups[1].Value
    $fingerprintMatches = [regex]::Matches($body, '"([0-9a-f]{64})"')
    $fingerprints = @($fingerprintMatches | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique)
    $unparsed = [regex]::Replace($body, '"[0-9a-f]{64}"', '') -replace '[\s,]', ''
    Assert-YoloR6Condition ([string]::IsNullOrEmpty($unparsed)) (
        "PluginTrustManager OFFICIAL_SHA_256 contains a non-literal or unparseable entry"
    )
    Assert-YoloR6Condition ($fingerprints.Count -eq 1) (
        "R6 requires exactly one unambiguous official signer fingerprint in PluginTrustManager"
    )
    return [pscustomobject][ordered]@{
        path = $path
        sha256 = Get-YoloR6Sha256 -Path $path
        fingerprints = @($fingerprints)
        singleFingerprint = $fingerprints[0]
    }
}

function Get-YoloR6HostSourceVersionCode {
    $path = Get-YoloR6RequiredFile -Path (Join-Path $repositoryRoot "version.properties") -Label "Host version.properties"
    $lines = @(Get-Content -LiteralPath $path | Where-Object { $_ -cmatch '^VERSION_BUILD=[0-9]+$' })
    Assert-YoloR6Condition ($lines.Count -eq 1) "Host version.properties must contain exactly one numeric VERSION_BUILD"
    return [pscustomobject][ordered]@{
        path = $path
        sha256 = Get-YoloR6Sha256 -Path $path
        versionCode = [long]($lines[0].Substring("VERSION_BUILD=".Length))
    }
}

function Get-YoloR6ProviderHostBoundary {
    param([Parameter(Mandatory = $true)][string] $SiblingRoot)
    $path = Get-YoloR6RequiredFile -Path (
        Join-Path $SiblingRoot "app\src\main\java\io\github\supermonster003\autojs6\plugin\yolo\ncnn\YoloPlugin.kt"
    ) -Label "Provider YoloPlugin source"
    $text = Get-Content -Raw -LiteralPath $path
    $matches = [regex]::Matches($text, '(?m)^\s*const\s+val\s+REQUIRED_HOST_VERSION\s*=\s*([0-9_]+)L\s*$')
    Assert-YoloR6Condition ($matches.Count -eq 1) "Provider source must declare one literal REQUIRED_HOST_VERSION"
    return [pscustomobject][ordered]@{
        path = $path
        sha256 = Get-YoloR6Sha256 -Path $path
        minHostVersionCode = [long]($matches[0].Groups[1].Value -replace '_', '')
    }
}

function Get-YoloR6ProviderSourceVersionCode {
    param([Parameter(Mandatory = $true)][string] $SiblingRoot)
    $path = Get-YoloR6RequiredFile -Path (Join-Path $SiblingRoot "version.properties") -Label "Provider version.properties"
    $lines = @(Get-Content -LiteralPath $path | Where-Object { $_ -cmatch '^VERSION_BUILD=[0-9]+$' })
    Assert-YoloR6Condition ($lines.Count -eq 1) "Provider version.properties must contain exactly one numeric VERSION_BUILD"
    return [pscustomobject][ordered]@{
        path = $path
        sha256 = Get-YoloR6Sha256 -Path $path
        versionCode = [long]($lines[0].Substring("VERSION_BUILD=".Length))
    }
}

function Assert-YoloR6SourceSentinel {
    param(
        [Parameter(Mandatory = $true)][string] $RelativePath,
        [Parameter(Mandatory = $true)][string[]] $Patterns
    )
    $path = Get-YoloR6RequiredFile -Path (Join-Path $repositoryRoot $RelativePath) -Label $RelativePath
    $text = Get-Content -Raw -LiteralPath $path
    foreach ($pattern in $Patterns) {
        Assert-YoloR6Condition ($text -cmatch $pattern) "$RelativePath is missing the required R6 source marker: $pattern"
    }
}

function Write-YoloR6Json {
    param(
        [Parameter(Mandatory = $true)][string] $Path,
        [Parameter(Mandatory = $true)] $Value
    )
    $null = New-Item -ItemType Directory -Path (Split-Path -Parent $Path) -Force
    $json = $Value | ConvertTo-Json -Depth 14
    [System.IO.File]::WriteAllText($Path, "$json`n", [System.Text.UTF8Encoding]::new($false))
}

function Invoke-YoloR6SourcePreflight {
    # These checks prove only that the fail-closed R6 source surfaces remain present. They do not
    # inspect an APK, use a signing key, contact a device, publish an index, or create a release.
    Assert-YoloR6SourceSentinel -RelativePath "docs/dev/yolo-plugin-roadmap.md" -Patterns @(
        '## R6-A：发布源码与预检',
        'SOURCE_STATIC_PASS',
        'PRODUCTION_NOT_RUN'
    )
    Assert-YoloR6SourceSentinel -RelativePath "docs/dev/yolo-r6-release-playbook.md" -Patterns @(
        'NOT_RUN_BY_PRODUCT_DECISION',
        'ARCHIVED_EXACT_V2_REINSTALL_ONLY',
        'PROVIDER_KILL_EXPLICIT_REOPEN',
        '不得原位降级'
    )
    Assert-YoloR6SourceSentinel -RelativePath "tools/yolo/verify-yolo-r6-recovery.ps1" -Patterns @(
        'OfficialProductionSignerSha256',
        'Qv710SoakConfirmedIdle',
        'YoloRealProviderR6RecoveryAndroidTest'
    )
    Assert-YoloR6SourceSentinel -RelativePath "tools/yolo/verify-yolo-r6-publishable.ps1" -Patterns @(
        'ExpectedMainCommit',
        'strict-entry-allowlist-v1',
        'Provider APK must contain exactly one compiled markdown raw resource',
        'PASS_CONTROLLED_SIGNED_BUILD',
        'CONTROLLED_BUILD_RECEIPT_NOT_CRYPTOGRAPHIC_ATTESTATION',
        'Remove-Item -LiteralPath \$artifactReportPath'
    )
    Assert-YoloR6SourceSentinel -RelativePath "tools/yolo/produce-yolo-r6-signed-artifacts.ps1" -Patterns @(
        'CONTROLLED_CLEAN_SIGNED_BUILD',
        'CONTROLLED_BUILD_RECEIPT_NOT_CRYPTOGRAPHIC_ATTESTATION',
        'temporaryCopiesVerifiedAbsent',
        'yoloR6ReleaseAndroidTest=true'
    )
    Assert-YoloR6SourceSentinel -RelativePath "app/src/androidTest/java/org/autojs/autojs/core/plugin/yolo/YoloRealProviderR6RecoveryAndroidTest.kt" -Patterns @(
        'yoloR6RecoveryEnabled',
        'am force-stop',
        'explicitReopenSucceeded'
    )

    $parserSamples = @(
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "ABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCD"
    )
    foreach ($sample in $parserSamples) {
        $parsed = Get-YoloR6SignerDigest -Value $sample
        Assert-YoloR6Condition ($parsed -ceq $sample.ToLowerInvariant()) "R6 signer parser self-check failed"
    }
    foreach ($invalid in @("", "00", ("g" * 64), (("0" * 63) + ":"))) {
        $failed = $false
        try { $null = Get-YoloR6SignerDigest -Value $invalid } catch { $failed = $true }
        Assert-YoloR6Condition $failed "R6 signer parser accepted an invalid fingerprint sample"
    }
    Assert-YoloR6Condition (
        "Model weights, training data, and images are not distributed in the Provider APK." -cmatch
            $modelWeightsNotDistributedPattern
    ) "R6 notice marker rejected the Provider's supported model-distribution wording"
    Assert-YoloR6Condition (-not (
        "Model weights may be distributed separately." -cmatch $modelWeightsNotDistributedPattern
    )) "R6 notice marker accepted wording that does not prohibit model distribution"

    $officialSignerSource = Get-YoloR6OfficialSignerSource
    $hostSourceVersion = Get-YoloR6HostSourceVersionCode
    $mainCommit = Invoke-YoloR6GitText -Repository $repositoryRoot -Arguments @("rev-parse", "HEAD")
    $mainStatus = Invoke-YoloR6GitText -Repository $repositoryRoot -Arguments @("status", "--porcelain=v1", "--untracked-files=all")
    $sourceSiblingRoot = [System.IO.Path]::GetFullPath($SourceSiblingPath)
    $providerSnapshot = if (Test-Path -LiteralPath $sourceSiblingRoot -PathType Container) {
        $providerCommit = Invoke-YoloR6GitText -Repository $sourceSiblingRoot -Arguments @("rev-parse", "HEAD")
        $providerStatus = Invoke-YoloR6GitText -Repository $sourceSiblingRoot -Arguments @("status", "--porcelain=v1", "--untracked-files=all")
        [ordered]@{
            repository = $sourceSiblingRoot
            revision = $providerCommit
            dirty = -not [string]::IsNullOrEmpty($providerStatus)
            cleanRequiredForSourceClaim = $false
        }
    } else {
        [ordered]@{
            repository = $sourceSiblingRoot
            state = "NOT_PRESENT"
            cleanRequiredForSourceClaim = $false
        }
    }

    $report = [ordered]@{
        schemaVersion = 1
        roadmapStage = "R6-A-SOURCE"
        status = "SOURCE_STATIC_PASS"
        generatedAtUtc = [DateTime]::UtcNow.ToString("o")
        deviceCommandsExecuted = $false
        apkInspected = $false
        signingSecretsRead = $false
        externalPublicationExecuted = $false
        officialSignerSource = $officialSignerSource
        hostSourceVersion = $hostSourceVersion
        sourceSnapshot = [ordered]@{
            repository = $repositoryRoot
            revision = $mainCommit
            dirty = -not [string]::IsNullOrEmpty($mainStatus)
            cleanRequiredForSourceClaim = $false
            providerSnapshot = $providerSnapshot
        }
        claims = [ordered]@{
            SOURCE = "SOURCE_STATIC_PASS"
            ANDROID_BUILD = "NOT_RUN"
            PRODUCTION_ARTIFACT_PREFLIGHT = "NOT_RUN"
            DEVICE_RECOVERY = "NOT_RUN"
            PLUGIN_INDEX = "NOT_RUN"
            PRODUCTION = "PRODUCTION_NOT_RUN"
        }
    }
    Write-YoloR6Json -Path $sourceReportPath -Value $report
    Write-Output "SOURCE_STATIC_PASS"
    Write-Output "PRODUCTION_NOT_RUN"
}

if ($PSCmdlet.ParameterSetName -ceq "Source") {
    $null = New-Item -ItemType Directory -Path $reportDirectory -Force
    if (Test-Path -LiteralPath $sourceReportPath -PathType Leaf) { Remove-Item -LiteralPath $sourceReportPath -Force }
    Invoke-YoloR6SourcePreflight
    return
}

$null = New-Item -ItemType Directory -Path $reportDirectory -Force
# An artifact run must never leave an older PASS-looking report behind if this invocation fails.
# The source receipt remains independent and is not removed here.
if (Test-Path -LiteralPath $artifactReportPath -PathType Leaf) {
    Remove-Item -LiteralPath $artifactReportPath -Force
}

$mainIdentity = Get-YoloR6CleanRepositoryIdentity -Repository $repositoryRoot -ExpectedCommit $ExpectedMainCommit -Label "Host"
$siblingRoot = Get-YoloR6RequiredDirectory -Path $SiblingPath -Label "Provider sibling"
$providerIdentity = Get-YoloR6CleanRepositoryIdentity -Repository $siblingRoot -ExpectedCommit $ExpectedProviderCommit -Label "Provider"
$expectedSigner = Get-YoloR6SignerDigest -Value $OfficialProductionSignerSha256
$officialSignerSource = Get-YoloR6OfficialSignerSource
Assert-YoloR6Condition ($expectedSigner -ceq [string]$officialSignerSource.singleFingerprint) (
    "The declared production signer is not the single fingerprint pinned by PluginTrustManager.OFFICIAL_SHA_256"
)
Assert-YoloR6Condition ($expectedSigner -cne $knownR5TestSigner) (
    "The declared production signer is the known R5 TEST-SIGNED certificate; refusing promotion"
)
$providerApkPath = Get-YoloR6RequiredFile -Path $ProviderApk -Label "Provider production APK"
$hostApkPath = Get-YoloR6RequiredFile -Path $HostApk -Label "Host production APK"
Assert-YoloR6Condition ($providerApkPath -cne $hostApkPath) "Host and Provider APK paths must differ"
$hostSourceVersion = Get-YoloR6HostSourceVersionCode
$providerHostBoundary = Get-YoloR6ProviderHostBoundary -SiblingRoot $siblingRoot
$providerSourceVersion = Get-YoloR6ProviderSourceVersionCode -SiblingRoot $siblingRoot
Assert-YoloR6Condition (
    [long]$providerHostBoundary.minHostVersionCode -eq [long]$hostSourceVersion.versionCode
) "Provider REQUIRED_HOST_VERSION does not equal the current Host VERSION_BUILD"
Assert-YoloR6Condition (
    [long]$providerSourceVersion.versionCode -eq $ExpectedProviderVersionCode
) "ExpectedProviderVersionCode differs from the checked Provider source VERSION_BUILD"

$r5Verifier = Get-YoloR6RequiredFile -Path (Join-Path $PSScriptRoot "verify-yolo-r5-packaging.ps1") -Label "R5 packaging verifier"
& $r5Verifier -SiblingPath $SiblingPath -ProviderApk $providerApkPath -HostApk $hostApkPath
$r5ReportPath = Get-YoloR6RequiredFile -Path (Join-Path $reportDirectory "r5-packaging.generated.json") -Label "R5 packaging report"
$r5 = Get-Content -Raw -LiteralPath $r5ReportPath | ConvertFrom-Json

Assert-YoloR6Condition ([string]$r5.status -ceq "PASS") "The prerequisite APK packaging verifier did not pass"
Assert-YoloR6Condition ([string]$r5.claims.APK_PACKAGING -ceq "PASS") "APK packaging evidence is absent"
Assert-YoloR6Condition ([string]$r5.claims.ELF_16K_COMPATIBLE -ceq "PASS") "Provider ELF static alignment evidence is absent"
Assert-YoloR6Condition ([long]$r5.hostApk.versionCode -eq [long]$hostSourceVersion.versionCode) (
    "Host APK versionCode differs from the checked source VERSION_BUILD"
)
Assert-YoloR6Condition ([long]$r5.providerApk.versionCode -eq $ExpectedProviderVersionCode) (
    "Provider APK versionCode differs from ExpectedProviderVersionCode"
)
Assert-YoloR6Condition (-not [bool]$r5.providerApk.debuggable.value) "Provider production APK is debuggable"
Assert-YoloR6Condition (-not [bool]$r5.hostApk.debuggable.value) "Host production APK is debuggable"
Assert-YoloR6Condition (
    @($r5.providerApk.abis).Count -eq 1 -and [string]$r5.providerApk.abis[0] -ceq "arm64-v8a"
) "Provider production APK must remain arm64-v8a only"
Assert-YoloR6Condition ([bool]$r5.providerApk.modelFree) "Provider production APK is not model-free"
Assert-YoloR6Condition (@($r5.providerApk.zip.modelEntries).Count -eq 0) "Provider APK contains a model artifact"
Assert-YoloR6Condition (@($r5.hostApk.zip.yoloNcnnModelEntries).Count -eq 0) "Host APK contains a YOLO model artifact"
Assert-YoloR6Condition (@($r5.hostApk.zip.yoloNcnnNativeEntries).Count -eq 0) "Host APK contains YOLO/NCNN native code"
Assert-YoloR6Condition ([string]$r5.providerApk.signer.certificateSha256 -ceq $expectedSigner) (
    "Provider signer does not match the explicitly approved production certificate fingerprint"
)
Assert-YoloR6Condition ([string]$r5.hostApk.signer.certificateSha256 -ceq $expectedSigner) (
    "Host signer does not match the explicitly approved production certificate fingerprint"
)
Assert-YoloR6Condition ([bool]$r5.hostApk.signerMatchesProvider) "Host and Provider APK signers differ"
Assert-YoloR6Condition ([string]$r5.providerApk.versionName -cnotmatch '(?i)(test[-_. ]?signed|debug)') (
    "Provider versionName still declares a test/debug candidate"
)
Assert-YoloR6Condition ([string]$r5.hostApk.versionName -cnotmatch '(?i)(test[-_. ]?signed|debug)') (
    "Host versionName still declares a test/debug candidate"
)
Assert-YoloR6Condition ([string]$r5.ncnn.license -ceq "BSD-3-Clause") "NCNN license declaration differs"
Assert-YoloR6Condition ([bool]$r5.ncnn.arm64StaticLibrary.provenanceMatch) "NCNN static-library provenance did not match"
Assert-YoloR6Condition ([bool]$r5.ncnn.packagedProvenanceMatch) "Packaged NCNN provenance did not match"
Assert-YoloR6Condition ([bool]$r5.ncnn.packagedLicenseMatch) "Packaged NCNN notice did not match"
Assert-YoloR6Condition ([bool]$r5.ncnn.pluginLicense.normalizedContentMatch) "Packaged MPL-2.0 notice did not match"
$releaseCompliance = Get-YoloR6PackagedNoticeEvidence `
    -ProviderApkPath $providerApkPath `
    -SiblingRoot $siblingRoot `
    -ExpectedMinHostVersionCode ([long]$providerHostBoundary.minHostVersionCode)
$providerZipPolicy = Get-YoloR6ProviderZipPolicyEvidence -ProviderApkPath $providerApkPath -SiblingRoot $siblingRoot
$providerApkSha256 = Get-YoloR6Sha256 -Path $providerApkPath
$providerBuildEvidence = Get-YoloR6ProviderBuildReceiptEvidence `
    -ReceiptPath $ProviderBuildReceipt `
    -SiblingRoot $siblingRoot `
    -ExpectedProviderCommit $providerIdentity.commit `
    -ExpectedHostVersionCode ([long]$hostSourceVersion.versionCode) `
    -ProductionApkSha256 $providerApkSha256 `
    -ExpectedNoticeSha256 $releaseCompliance.thirdPartyNotice.sha256 `
    -ExpectedModelPolicySha256 $releaseCompliance.modelLicensePolicy.sha256 `
    -ExpectedReleaseNoteSha256 $releaseCompliance.releaseNoteDraft.sha256
$instrumentationApkPath = Get-YoloR6RequiredFile -Path $InstrumentationApk -Label "production-signed instrumentation APK"
$controlledBuildEvidence = Get-YoloR6ControlledBuildReceiptEvidence `
    -ReceiptPath $ControlledBuildReceipt `
    -HostApkPath $hostApkPath `
    -ProviderApkPath $providerApkPath `
    -InstrumentationApkPath $instrumentationApkPath `
    -ExpectedMainCommit $mainIdentity.commit `
    -ExpectedProviderCommit $providerIdentity.commit `
    -ExpectedSigner $expectedSigner `
    -ExpectedHostVersionCode ([long]$hostSourceVersion.versionCode) `
    -ExpectedProviderVersionCode $ExpectedProviderVersionCode `
    -ProviderBuildEvidence $providerBuildEvidence `
    -AndroidTools $r5.tools `
    -SiblingRoot $siblingRoot
$artifactBuildIdentityProven = $true
$artifactPreflightClaim = "PASS_CONTROLLED_SIGNED_BUILD"

$report = [ordered]@{
    schemaVersion = 2
    roadmapStage = "R6-B-ARTIFACT-PREFLIGHT"
    status = $artifactPreflightClaim
    generatedAtUtc = [DateTime]::UtcNow.ToString("o")
    declaredOfficialProductionSignerSha256 = $expectedSigner
    signerDeclarationSource = "explicit-operator-input-not-derived-from-a-secret"
    repositories = [ordered]@{
        host = $mainIdentity
        provider = $providerIdentity
        officialIndex = "NOT_PART_OF_ARTIFACT_PREFLIGHT"
    }
    officialSignerSource = [ordered]@{
        path = $officialSignerSource.path
        sha256 = $officialSignerSource.sha256
        fingerprintCount = @($officialSignerSource.fingerprints).Count
        inputMatchesSingleOfficialFingerprint = $true
    }
    compatibilityBoundary = [ordered]@{
        hostSourcePath = $hostSourceVersion.path
        hostSourceSha256 = $hostSourceVersion.sha256
        hostVersionCode = $hostSourceVersion.versionCode
        providerSourcePath = $providerHostBoundary.path
        providerSourceSha256 = $providerHostBoundary.sha256
        providerRequiresHostVersionCode = $providerHostBoundary.minHostVersionCode
        providerVersionSourcePath = $providerSourceVersion.path
        providerVersionSourceSha256 = $providerSourceVersion.sha256
        expectedProviderVersionCode = $ExpectedProviderVersionCode
        providerApkAndSourceVersionMatch = $true
        predecessorArtifactApplicable = $false
        predecessorReason = "Provider 0.1.0 versionCode 2 is the first release by explicit product decision."
        upgradeRuntimeEvidence = "NOT_RUN_BY_PRODUCT_DECISION"
        versionRollbackEvidence = "NOT_RUN_BY_PRODUCT_DECISION"
        sameVersionRecovery = "ARCHIVED_EXACT_V2_REINSTALL_ONLY"
        withdrawalMitigation = "DISABLE_PROVIDER_WITHDRAW_INDEX_PUBLISH_FORWARD_FIX_V3"
    }
    hostApk = [ordered]@{
        path = $hostApkPath
        length = (Get-Item -LiteralPath $hostApkPath).Length
        sha256 = Get-YoloR6Sha256 -Path $hostApkPath
        applicationId = [string]$r5.hostApk.applicationId
        versionCode = [long]$r5.hostApk.versionCode
        versionName = [string]$r5.hostApk.versionName
        debuggable = $false
        signerSha256 = $expectedSigner
    }
    providerApk = [ordered]@{
        path = $providerApkPath
        length = (Get-Item -LiteralPath $providerApkPath).Length
        sha256 = $providerApkSha256
        applicationId = [string]$r5.providerApk.applicationId
        versionCode = [long]$r5.providerApk.versionCode
        versionName = [string]$r5.providerApk.versionName
        debuggable = $false
        abis = @($r5.providerApk.abis)
        strictProviderAssetAllowlist = $true
        signerSha256 = $expectedSigner
    }
    prerequisitePackagingReport = [ordered]@{
        path = $r5ReportPath
        sha256 = Get-YoloR6Sha256 -Path $r5ReportPath
    }
    releaseCompliance = $releaseCompliance
    providerZipPolicy = $providerZipPolicy
    providerBuildReceipt = $providerBuildEvidence
    controlledSignedBuildReceipt = $controlledBuildEvidence
    evidenceBoundary = [ordered]@{
        signingSecretReadByConsumer = $false
        artifactBuildIdentityProven = $artifactBuildIdentityProven
        providerSignedApkBuildIdentityProven = $true
        hostSignedApkBuildIdentityProven = $true
        instrumentationSignedApkBuildIdentityProven = $true
        cryptographicSourceAttestation = $false
        controlledBuildReceiptIsNotCryptographicAttestation = $true
        staticElfAlignmentIsNotRuntimeLoadEvidence = $true
        deviceCommandsExecuted = $false
        pluginIndexChanged = $false
        externalPublicationExecuted = $false
    }
    claims = [ordered]@{
        PRODUCTION_ARTIFACT_PREFLIGHT = $artifactPreflightClaim
        OFFICIAL_SIGNER_MATCH = "PASS_HOST_PINNED_SINGLE_FINGERPRINT_AND_EXPLICIT_INPUT"
        APK_SHA256_RECEIPT = "PASS"
        ARM64_ONLY_PROVIDER = "PASS"
        STRICT_PROVIDER_RELEASE_ENTRY_ALLOWLIST = "PASS"
        KNOWN_YOLO_NCNN_MODEL_ARTIFACTS_ABSENT_FROM_HOST = "PASS"
        LICENSE_AND_PROVENANCE = "PASS"
        DEVICE_RECOVERY = "NOT_RUN"
        PLUGIN_INDEX = "NOT_RUN"
        UPGRADE_ROLLBACK = "NOT_RUN_BY_PRODUCT_DECISION"
        PUBLICATION = "NOT_RUN"
        PRODUCTION = "PRODUCTION_NOT_RUN"
    }
}
Write-YoloR6Json -Path $artifactReportPath -Value $report
Write-Output "YOLO_R6_PUBLISHABLE_PREFLIGHT=$artifactPreflightClaim report=$artifactReportPath"
Write-Output "YOLO_R6_PRODUCTION=PRODUCTION_NOT_RUN"
