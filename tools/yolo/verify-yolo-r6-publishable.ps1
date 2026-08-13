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

function Assert-YoloR6Rejects {
    param(
        [Parameter(Mandatory = $true)][scriptblock] $Action,
        [Parameter(Mandatory = $true)][string] $Label
    )
    $rejected = $false
    try { $null = & $Action } catch { $rejected = $true }
    Assert-YoloR6Condition $rejected "Self-test expected rejection: $Label"
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

function Get-YoloR6XmlTreeInstrumentationNodes {
    param(
        [Parameter(Mandatory = $true)][string] $XmlTreeText,
        [Parameter(Mandatory = $true)][string] $Label
    )
    $lines = @($XmlTreeText -split "`r?`n")
    $elementStack = [System.Collections.Generic.List[object]]::new()
    $manifestElements = [System.Collections.Generic.List[object]]::new()
    $elements = [System.Collections.Generic.List[object]]::new()
    for ($index = 0; $index -lt $lines.Count; $index++) {
        $match = [regex]::Match($lines[$index], '^(?<indent>[ \t]*)E:\s+(?<name>[^\s(]+)(?:\s+\(line=[0-9]+\))?\s*$')
        if (-not $match.Success) { continue }
        $indent = $match.Groups['indent'].Value
        while ($elementStack.Count -gt 0 -and ([string]$elementStack[$elementStack.Count - 1].indent).Length -ge $indent.Length) {
            $elementStack.RemoveAt($elementStack.Count - 1)
        }
        $parentName = if ($elementStack.Count -gt 0) { [string]$elementStack[$elementStack.Count - 1].name } else { "" }
        $record = [pscustomobject]@{
            index = $index
            indent = $indent
            name = $match.Groups['name'].Value
            parentName = $parentName
        }
        if ($record.name -ceq 'manifest') { $manifestElements.Add($record) }
        if ($record.name -ceq 'instrumentation') {
            Assert-YoloR6Condition ($record.parentName -ceq 'manifest') "$Label instrumentation node must be a direct child of the manifest"
            $elements.Add($record)
        }
        $elementStack.Add($record)
    }
    Assert-YoloR6Condition ($manifestElements.Count -eq 1) "$Label manifest tree must contain exactly one manifest element"
    $nodes = [System.Collections.Generic.List[object]]::new()
    foreach ($element in $elements) {
        $elementIndent = ([string]$element.indent).Length
        $directAttributeIndent = $elementIndent + 2
        $values = @{}
        $enteredChildElement = $false
        for ($index = [int]$element.index + 1; $index -lt $lines.Count; $index++) {
            $child = [regex]::Match($lines[$index], '^(?<indent>[ \t]*)E:\s+')
            if ($child.Success -and $child.Groups['indent'].Value.Length -le $elementIndent) { break }
            if ($child.Success) { $enteredChildElement = $true; continue }
            $attribute = [regex]::Match(
                $lines[$index],
                '^(?<indent>[ \t]*)A:\s+(?<key>[^\s(=]+)(?:\(0x[0-9a-fA-F]+\))?=(?<compiled>"[^"]*"|[^\s]+)(?:\s+\(Raw:\s+"(?<raw>[^"]*)"\))?\s*$'
            )
            if ($enteredChildElement -or -not $attribute.Success -or $attribute.Groups['indent'].Value.Length -ne $directAttributeIndent) { continue }
            $qualifiedKey = $attribute.Groups['key'].Value
            $androidPrefix = 'http://schemas.android.com/apk/res/android:'
            if (-not $qualifiedKey.StartsWith($androidPrefix, [System.StringComparison]::Ordinal)) { continue }
            $key = $qualifiedKey.Substring($androidPrefix.Length)
            if ($key -notin @('name', 'targetPackage')) { continue }
            if (-not $values.ContainsKey($key)) { $values[$key] = [System.Collections.Generic.List[string]]::new() }
            Assert-YoloR6Condition $attribute.Groups['raw'].Success "$Label instrumentation $key has no Raw string value"
            $value = $attribute.Groups['raw'].Value
            $compiled = $attribute.Groups['compiled'].Value
            if ($compiled.StartsWith('"') -and $compiled.EndsWith('"')) {
                Assert-YoloR6Condition ($compiled.Substring(1, $compiled.Length - 2) -ceq $value) "$Label instrumentation $key compiled and raw values differ"
            }
            $values[$key].Add($value)
        }
        $names = @(if ($values.ContainsKey('name')) { @($values['name']) })
        $targets = @(if ($values.ContainsKey('targetPackage')) { @($values['targetPackage']) })
        Assert-YoloR6Condition ($names.Count -eq 1) "$Label instrumentation node must contain exactly one direct android:name attribute"
        Assert-YoloR6Condition ($targets.Count -eq 1) "$Label instrumentation node must contain exactly one direct android:targetPackage attribute"
        $nodes.Add([pscustomobject][ordered]@{ runner = $names[0]; targetPackage = $targets[0] })
    }
    return @($nodes)
}

function Get-YoloR6ApkIdentityFromText {
    param(
        [Parameter(Mandatory = $true)][string] $BadgingText,
        [Parameter(Mandatory = $true)][string] $XmlTreeText,
        [Parameter(Mandatory = $true)][ValidateSet("Ordinary", "Instrumentation")][string] $Kind,
        [Parameter(Mandatory = $true)][string] $Label
    )
    $packageLines = @([regex]::Matches($BadgingText, '(?m)^package:[^\r\n]*$'))
    Assert-YoloR6Condition ($packageLines.Count -eq 1) "$Label badging must contain exactly one package line"
    $package = [regex]::Match(
        $packageLines[0].Value,
        "^package:\s+name='([^'\r\n]*)'\s+versionCode='([^'\r\n]*)'\s+versionName='([^'\r\n]*)'(?:\s|$)"
    )
    Assert-YoloR6Condition $package.Success "$Label badging package identity is malformed"
    $badgingInstrumentationLines = @([regex]::Matches($BadgingText, '(?m)^instrumentation:[^\r\n]*$'))
    Assert-YoloR6Condition ($badgingInstrumentationLines.Count -le 1) "$Label badging contains multiple instrumentation claims"
    $badgingInstrumentation = $null
    if ($badgingInstrumentationLines.Count -eq 1) {
        $match = [regex]::Match($badgingInstrumentationLines[0].Value, "^instrumentation:\s+name='([^'\r\n]+)'\s+targetPackage='([^'\r\n]+)'(?:\s|$)")
        Assert-YoloR6Condition $match.Success "$Label badging instrumentation claim is malformed"
        $badgingInstrumentation = [pscustomobject]@{ runner = $match.Groups[1].Value; targetPackage = $match.Groups[2].Value }
    }
    $packageName = $package.Groups[1].Value
    $rawVersionCode = $package.Groups[2].Value
    $rawVersionName = $package.Groups[3].Value
    Assert-YoloR6Condition (-not [string]::IsNullOrEmpty($packageName)) "$Label package name is empty"
    $nodes = @(Get-YoloR6XmlTreeInstrumentationNodes -XmlTreeText $XmlTreeText -Label $Label)
    if ($Kind -ceq "Ordinary") {
        $parsedVersionCode = 0L
        Assert-YoloR6Condition (
            $rawVersionCode -cmatch '^[0-9]+$' -and
            [long]::TryParse($rawVersionCode, [Globalization.NumberStyles]::None, [Globalization.CultureInfo]::InvariantCulture, [ref]$parsedVersionCode) -and
            $parsedVersionCode -gt 0L
        ) "$Label versionCode must be a positive 64-bit integer"
        Assert-YoloR6Condition ($nodes.Count -eq 0) "$Label ordinary APK must not declare an instrumentation node"
        Assert-YoloR6Condition ($null -eq $badgingInstrumentation) "$Label ordinary APK badging must not claim instrumentation"
        $versionCode = $parsedVersionCode
    } else {
        Assert-YoloR6Condition ($packageName -ceq "org.autojs.autojs6.test") "$Label instrumentation package differs"
        Assert-YoloR6Condition ([string]::IsNullOrEmpty($rawVersionCode)) "$Label instrumentation versionCode must be empty"
        Assert-YoloR6Condition ([string]::IsNullOrEmpty($rawVersionName)) "$Label instrumentation versionName must be empty"
        Assert-YoloR6Condition ($nodes.Count -eq 1) "$Label must declare exactly one instrumentation node"
        Assert-YoloR6Condition ([string]$nodes[0].runner -ceq "androidx.test.runner.AndroidJUnitRunner") "$Label instrumentation runner differs"
        Assert-YoloR6Condition ([string]$nodes[0].targetPackage -ceq "org.autojs.autojs6") "$Label instrumentation target package differs"
        if ($null -ne $badgingInstrumentation) {
            Assert-YoloR6Condition (
                [string]$badgingInstrumentation.runner -ceq [string]$nodes[0].runner -and
                [string]$badgingInstrumentation.targetPackage -ceq [string]$nodes[0].targetPackage
            ) "$Label badging instrumentation claim differs from the manifest tree"
        }
        $versionCode = ""
    }
    return [pscustomobject][ordered]@{
        packageName = $packageName
        versionCode = $versionCode
        versionName = $rawVersionName
        debuggable = $BadgingText -match "(?m)^application-debuggable\s*$"
        instrumentation = if ($Kind -ceq "Instrumentation") { $nodes[0] } else { $null }
    }
}

function Get-YoloR6ApkIdentity {
    param(
        [Parameter(Mandatory = $true)][string] $Aapt2,
        [Parameter(Mandatory = $true)][string] $ApkPath,
        [Parameter(Mandatory = $true)][ValidateSet("Ordinary", "Instrumentation")][string] $Kind,
        [Parameter(Mandatory = $true)][string] $Label
    )
    $badging = Invoke-YoloR6ExternalText -FilePath $Aapt2 -Arguments @("dump", "badging", $ApkPath) -Label "$Label badging inspection"
    $xmlTree = Invoke-YoloR6ExternalText -FilePath $Aapt2 -Arguments @("dump", "xmltree", "--file", "AndroidManifest.xml", $ApkPath) -Label "$Label manifest inspection"
    return Get-YoloR6ApkIdentityFromText -BadgingText $badging -XmlTreeText $xmlTree -Kind $Kind -Label $Label
}

function Get-YoloR6InstrumentationMetadataIdentity {
    param(
        [Parameter(Mandatory = $true)][string] $JsonText,
        [Parameter(Mandatory = $true)][string] $Label
    )
    try { $metadata = $JsonText | ConvertFrom-Json } catch { throw "$Label is invalid JSON" }
    Assert-YoloR6Condition ($null -ne $metadata) "$Label is null"
    Assert-YoloR6Condition ([int]$metadata.version -eq 3) "$Label schema must be version 3"
    Assert-YoloR6Condition ([string]$metadata.artifactType.type -ceq "APK" -and [string]$metadata.artifactType.kind -ceq "Directory") "$Label artifactType differs"
    Assert-YoloR6Condition ([string]$metadata.applicationId -ceq "org.autojs.autojs6.test") "$Label applicationId differs"
    Assert-YoloR6Condition ([string]$metadata.variantName -ceq "appReleaseAndroidTest") "$Label variantName differs"
    $elements = @($metadata.elements)
    Assert-YoloR6Condition ($elements.Count -eq 1 -and $null -ne $elements[0]) "$Label must contain exactly one non-null element"
    $element = $elements[0]
    Assert-YoloR6Condition ([string]$element.type -ceq "SINGLE") "$Label element type differs"
    Assert-YoloR6Condition ($null -ne $element.filters -and @($element.filters).Count -eq 0) "$Label filters must be empty"
    Assert-YoloR6Condition ($null -ne $element.attributes -and @($element.attributes).Count -eq 0) "$Label attributes must be empty"
    Assert-YoloR6Condition ($null -ne $element.versionCode -and [long]$element.versionCode -eq 0L) "$Label versionCode must be zero"
    Assert-YoloR6Condition ($null -ne $element.versionName -and [string]$element.versionName -ceq "") "$Label versionName must be empty"
    $outputFile = [string]$element.outputFile
    Assert-YoloR6Condition ($outputFile -ceq "app-app-release-androidTest.apk") "$Label outputFile differs"
    Assert-YoloR6Condition (-not [System.IO.Path]::IsPathRooted($outputFile) -and [System.IO.Path]::GetFileName($outputFile) -ceq $outputFile) "$Label outputFile must be a leaf name"
    return [pscustomobject][ordered]@{
        schemaVersion = 3
        artifactType = [ordered]@{ type = "APK"; kind = "Directory" }
        applicationId = "org.autojs.autojs6.test"
        variantName = "appReleaseAndroidTest"
        element = [ordered]@{ type = "SINGLE"; filters = @(); attributes = @(); versionCode = 0L; versionName = ""; outputFile = $outputFile }
    }
}

function Get-YoloR6SingleSignerDigestFromText {
    param(
        [Parameter(Mandatory = $true)][string] $Text,
        [Parameter(Mandatory = $true)][string] $Label
    )
    $counts = @([regex]::Matches($Text, '(?im)^Number of signers:\s*([0-9]+)\s*$'))
    Assert-YoloR6Condition ($counts.Count -eq 1 -and [int]$counts[0].Groups[1].Value -eq 1) "$Label must have exactly one unambiguous signer-count claim"
    $digests = @([regex]::Matches($Text, '(?im)certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$') | ForEach-Object { $_.Groups[1].Value.ToLowerInvariant() })
    Assert-YoloR6Condition ($digests.Count -eq 1) "$Label must expose exactly one signer certificate SHA-256 claim"
    return $digests[0]
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

    $metadataReceipt = $receipt.artifacts.instrumentation.outputMetadata
    $expectedMetadataPath = [System.IO.Path]::GetFullPath((Join-Path $reportDirectory "r6-signed-artifacts/AutoJs6-v6.8.0-5275-release-androidTest.output-metadata.json"))
    $metadataPath = Get-YoloR6RequiredFile -Path ([string]$metadataReceipt.file.path) -Label "controlled instrumentation output metadata"
    Assert-YoloR6Condition ($metadataPath -ceq $expectedMetadataPath) "Controlled instrumentation output metadata path differs"
    Assert-YoloR6Condition ([string]$metadataReceipt.file.sha256 -ceq (Get-YoloR6Sha256 $metadataPath)) "Controlled instrumentation output metadata SHA-256 is stale"
    Assert-YoloR6Condition ([long]$metadataReceipt.file.length -eq (Get-Item -LiteralPath $metadataPath).Length) "Controlled instrumentation output metadata length is stale"
    Assert-YoloR6Condition ([bool]$metadataReceipt.file.freshAfterBuildStart) "Controlled instrumentation output metadata was not fresh after build start"
    $metadataIdentity = Get-YoloR6InstrumentationMetadataIdentity -JsonText (Get-Content -Raw -LiteralPath $metadataPath) -Label "controlled instrumentation output metadata"
    $metadataReceiptIdentity = $metadataReceipt.identity
    Assert-YoloR6Condition ([int]$metadataReceiptIdentity.schemaVersion -eq $metadataIdentity.schemaVersion) "Instrumentation metadata receipt schema differs"
    Assert-YoloR6Condition (
        [string]$metadataReceiptIdentity.artifactType.type -ceq [string]$metadataIdentity.artifactType.type -and
        [string]$metadataReceiptIdentity.artifactType.kind -ceq [string]$metadataIdentity.artifactType.kind -and
        [string]$metadataReceiptIdentity.applicationId -ceq [string]$metadataIdentity.applicationId -and
        [string]$metadataReceiptIdentity.variantName -ceq [string]$metadataIdentity.variantName
    ) "Instrumentation metadata receipt root identity differs"
    Assert-YoloR6Condition (
        [string]$metadataReceiptIdentity.element.type -ceq [string]$metadataIdentity.element.type -and
        @($metadataReceiptIdentity.element.filters).Count -eq 0 -and
        @($metadataReceiptIdentity.element.attributes).Count -eq 0 -and
        [long]$metadataReceiptIdentity.element.versionCode -eq [long]$metadataIdentity.element.versionCode -and
        [string]$metadataReceiptIdentity.element.versionName -ceq [string]$metadataIdentity.element.versionName -and
        [string]$metadataReceiptIdentity.element.outputFile -ceq [string]$metadataIdentity.element.outputFile
    ) "Instrumentation metadata receipt element identity differs"

    $apkSigner = Get-YoloR6RequiredFile -Path ([string]$AndroidTools.apkSigner) -Label "R5-selected apksigner"
    $aapt2 = Get-YoloR6RequiredFile -Path ([string]$AndroidTools.aapt2) -Label "R5-selected aapt2"
    $testSignatureText = Invoke-YoloR6ExternalText -FilePath $apkSigner -Arguments @("verify", "--verbose", "--print-certs", $InstrumentationApkPath) -Label "Instrumentation signature verification"
    $testSignerDigest = Get-YoloR6SingleSignerDigestFromText -Text $testSignatureText -Label "Instrumentation"
    Assert-YoloR6Condition ($testSignerDigest -ceq $ExpectedSigner) "Instrumentation is not signed by the official production certificate"
    $hostIdentity = Get-YoloR6ApkIdentity -Aapt2 $aapt2 -ApkPath $HostApkPath -Kind Ordinary -Label "Host"
    $providerIdentity = Get-YoloR6ApkIdentity -Aapt2 $aapt2 -ApkPath $ProviderApkPath -Kind Ordinary -Label "Provider"
    $testIdentity = Get-YoloR6ApkIdentity -Aapt2 $aapt2 -ApkPath $InstrumentationApkPath -Kind Instrumentation -Label "Instrumentation"
    foreach ($comparison in @(
        [pscustomobject]@{ actual = $hostIdentity; expected = $receipt.artifacts.host.identity; label = "Host" },
        [pscustomobject]@{ actual = $providerIdentity; expected = $receipt.artifacts.provider.identity; label = "Provider" },
        [pscustomobject]@{ actual = $testIdentity; expected = $receipt.artifacts.instrumentation.identity; label = "Instrumentation" }
    )) {
        Assert-YoloR6Condition ([string]$comparison.actual.packageName -ceq [string]$comparison.expected.packageName) "$($comparison.label) package differs from controlled receipt"
        Assert-YoloR6Condition ([string]$comparison.actual.versionCode -ceq [string]$comparison.expected.versionCode) "$($comparison.label) versionCode differs from controlled receipt"
        Assert-YoloR6Condition ([string]$comparison.actual.versionName -ceq [string]$comparison.expected.versionName) "$($comparison.label) versionName differs from controlled receipt"
        Assert-YoloR6Condition ([bool]$comparison.actual.debuggable -eq [bool]$comparison.expected.debuggable) "$($comparison.label) debuggable state differs from controlled receipt"
    }
    Assert-YoloR6Condition (-not $hostIdentity.debuggable -and -not $providerIdentity.debuggable -and -not $testIdentity.debuggable) "A controlled release APK is debuggable"
    Assert-YoloR6Condition (
        [string]$testIdentity.instrumentation.runner -ceq [string]$receipt.artifacts.instrumentation.identity.instrumentation.runner -and
        [string]$testIdentity.instrumentation.targetPackage -ceq [string]$receipt.artifacts.instrumentation.identity.instrumentation.targetPackage
    ) "Instrumentation manifest identity differs from controlled receipt"

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
    $signerText = "Number of signers: 1`nSigner #1 certificate SHA-256 digest: $('b' * 64)"
    Assert-YoloR6Condition ((Get-YoloR6SingleSignerDigestFromText -Text $signerText -Label "consumer signer self-test") -ceq ('b' * 64)) "Consumer signer identity parser self-test failed"
    Assert-YoloR6Rejects -Label "consumer duplicate signer-count claim" -Action {
        Get-YoloR6SingleSignerDigestFromText -Text "$signerText`nNumber of signers: 1" -Label "consumer duplicate signer-count self-test"
    }
    $ordinaryBadging = "package: name='org.autojs.autojs6' versionCode='5275' versionName='6.8.0' platformBuildVersionName='16'"
    $ordinaryIdentity = Get-YoloR6ApkIdentityFromText -BadgingText $ordinaryBadging -XmlTreeText "  E: manifest" -Kind Ordinary -Label "consumer ordinary self-test"
    Assert-YoloR6Condition ($ordinaryIdentity.versionCode -eq 5275L -and $null -eq $ordinaryIdentity.instrumentation) "Consumer ordinary APK parser self-test failed"
    foreach ($invalidVersion in @("", "0", "9223372036854775808")) {
        Assert-YoloR6Rejects -Label "consumer invalid ordinary versionCode $invalidVersion" -Action {
            Get-YoloR6ApkIdentityFromText -BadgingText "package: name='org.autojs.autojs6' versionCode='$invalidVersion' versionName='6.8.0'" -XmlTreeText "  E: manifest" -Kind Ordinary -Label "consumer invalid ordinary version"
        }
    }
    $testBadging = "package: name='org.autojs.autojs6.test' versionCode='' versionName='' platformBuildVersionName='16'"
    $testXml = @'
  E: manifest (line=2)
    E: instrumentation (line=9)
      A: http://schemas.android.com/apk/res/android:name(0x01010003)=0x7f010001 (Raw: "androidx.test.runner.AndroidJUnitRunner")
      A: http://schemas.android.com/apk/res/android:targetPackage(0x01010021)="org.autojs.autojs6" (Raw: "org.autojs.autojs6")
      E: meta-data (line=10)
        A: http://schemas.android.com/apk/res/android:name="nested.decoy" (Raw: "nested.decoy")
        A: http://schemas.android.com/apk/res/android:targetPackage="nested.target" (Raw: "nested.target")
    A: http://schemas.android.com/apk/res/android:name="wrong.scope" (Raw: "wrong.scope")
'@
    $testIdentity = Get-YoloR6ApkIdentityFromText -BadgingText "$testBadging`ninstrumentation: name='androidx.test.runner.AndroidJUnitRunner' targetPackage='org.autojs.autojs6'" -XmlTreeText $testXml -Kind Instrumentation -Label "consumer instrumentation self-test"
    Assert-YoloR6Condition (
        $testIdentity.versionCode -ceq "" -and $testIdentity.versionName -ceq "" -and
        [string]$testIdentity.instrumentation.runner -ceq "androidx.test.runner.AndroidJUnitRunner" -and
        [string]$testIdentity.instrumentation.targetPackage -ceq "org.autojs.autojs6"
    ) "Consumer instrumentation APK parser self-test failed"
    $secondTestNode = @'
    E: instrumentation (line=40)
      A: http://schemas.android.com/apk/res/android:name="androidx.test.runner.AndroidJUnitRunner" (Raw: "androidx.test.runner.AndroidJUnitRunner")
      A: http://schemas.android.com/apk/res/android:targetPackage="org.autojs.autojs6" (Raw: "org.autojs.autojs6")
'@
    $nestedTestNode = @'
  E: manifest (line=2)
    E: application (line=8)
      E: instrumentation (line=9)
        A: http://schemas.android.com/apk/res/android:name="androidx.test.runner.AndroidJUnitRunner" (Raw: "androidx.test.runner.AndroidJUnitRunner")
        A: http://schemas.android.com/apk/res/android:targetPackage="org.autojs.autojs6" (Raw: "org.autojs.autojs6")
'@
    foreach ($sample in @(
        [pscustomobject]@{ label = "consumer missing instrumentation"; badging = $testBadging; xml = "  E: manifest" },
        [pscustomobject]@{ label = "consumer duplicate instrumentation"; badging = $testBadging; xml = "$testXml`n$secondTestNode" },
        [pscustomobject]@{ label = "consumer wrong runner"; badging = $testBadging; xml = $testXml.Replace('androidx.test.runner.AndroidJUnitRunner', 'wrong.runner') },
        [pscustomobject]@{ label = "consumer wrong target"; badging = $testBadging; xml = $testXml.Replace('org.autojs.autojs6', 'wrong.target') },
        [pscustomobject]@{ label = "consumer forged badging"; badging = "$testBadging`ninstrumentation: name='androidx.test.runner.AndroidJUnitRunner' targetPackage='forged.target'"; xml = $testXml },
        [pscustomobject]@{ label = "consumer duplicate package"; badging = "$testBadging`n$testBadging"; xml = $testXml },
        [pscustomobject]@{ label = "consumer compiled without raw"; badging = $testBadging; xml = $testXml.Replace('0x7f010001 (Raw: "androidx.test.runner.AndroidJUnitRunner")', '0x7f010001') },
        [pscustomobject]@{ label = "consumer missing direct runner"; badging = $testBadging; xml = $testXml.Replace('      A: http://schemas.android.com/apk/res/android:name(0x01010003)=0x7f010001 (Raw: "androidx.test.runner.AndroidJUnitRunner")' + "`n", '') },
        [pscustomobject]@{ label = "consumer missing direct target"; badging = $testBadging; xml = $testXml.Replace('      A: http://schemas.android.com/apk/res/android:targetPackage(0x01010021)="org.autojs.autojs6" (Raw: "org.autojs.autojs6")' + "`n", '') },
        [pscustomobject]@{ label = "consumer nested instrumentation"; badging = $testBadging; xml = $nestedTestNode },
        [pscustomobject]@{ label = "consumer evil runner namespace"; badging = $testBadging; xml = $testXml.Replace('http://schemas.android.com/apk/res/android:name', 'evil:name') },
        [pscustomobject]@{ label = "consumer evil target namespace"; badging = $testBadging; xml = $testXml.Replace('http://schemas.android.com/apk/res/android:targetPackage', 'evil:targetPackage') }
    )) {
        Assert-YoloR6Rejects -Label $sample.label -Action {
            Get-YoloR6ApkIdentityFromText -BadgingText $sample.badging -XmlTreeText $sample.xml -Kind Instrumentation -Label $sample.label
        }
    }
    $validMetadata = [ordered]@{
        version = 3
        artifactType = [ordered]@{ type = "APK"; kind = "Directory" }
        applicationId = "org.autojs.autojs6.test"
        variantName = "appReleaseAndroidTest"
        elements = @([ordered]@{ type = "SINGLE"; filters = @(); attributes = @(); versionCode = 0; versionName = ""; outputFile = "app-app-release-androidTest.apk" })
    }
    $metadataIdentity = Get-YoloR6InstrumentationMetadataIdentity -JsonText ($validMetadata | ConvertTo-Json -Depth 8) -Label "consumer metadata self-test"
    Assert-YoloR6Condition ($metadataIdentity.schemaVersion -eq 3 -and $metadataIdentity.element.outputFile -ceq "app-app-release-androidTest.apk") "Consumer instrumentation metadata parser self-test failed"
    foreach ($mutation in @(
        [pscustomobject]@{ label = "consumer metadata missing schema"; apply = { param($value) $value.Remove('version') } },
        [pscustomobject]@{ label = "consumer metadata null element"; apply = { param($value) $value.elements = @($null) } },
        [pscustomobject]@{ label = "consumer metadata ambiguous elements"; apply = { param($value) $value.elements = @($value.elements[0], $value.elements[0]) } },
        [pscustomobject]@{ label = "consumer metadata rooted output"; apply = { param($value) $value.elements[0].outputFile = 'C:\invalid.apk' } },
        [pscustomobject]@{ label = "consumer metadata nested output"; apply = { param($value) $value.elements[0].outputFile = 'nested/invalid.apk' } }
    )) {
        $candidate = $validMetadata | ConvertTo-Json -Depth 8 | ConvertFrom-Json -AsHashtable
        & $mutation.apply $candidate
        Assert-YoloR6Rejects -Label $mutation.label -Action {
            Get-YoloR6InstrumentationMetadataIdentity -JsonText ($candidate | ConvertTo-Json -Depth 8) -Label $mutation.label
        }
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
