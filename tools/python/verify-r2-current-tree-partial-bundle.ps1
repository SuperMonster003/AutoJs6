#Requires -Version 7.0

[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^[A-Za-z0-9._:-]+$')]
    [string] $Serial,

    [Parameter(Mandatory)]
    [string] $PositiveEvidence,

    [Parameter(Mandatory)]
    [string] $ExecutionMatrixEvidence,

    [Parameter(Mandatory)]
    [string] $CancelRebindEvidence,

    [Parameter(Mandatory)]
    [string] $TimeoutRebindEvidence,

    [Parameter(Mandatory)]
    [string] $ReliablePipeEvidence,

    [Parameter(Mandatory)]
    [string] $StagingTimeoutEvidence,

    [Parameter(Mandatory)]
    [string] $BlockingRetirementEvidence,

    [Parameter(Mandatory)]
    [string] $HostApk,

    [Parameter(Mandatory)]
    [ValidatePattern('^[0-9A-Fa-f]{64}$')]
    [string] $HostSha256,

    [Parameter(Mandatory)]
    [string] $TestApk,

    [Parameter(Mandatory)]
    [ValidatePattern('^[0-9A-Fa-f]{64}$')]
    [string] $TestSha256,

    [Parameter(Mandatory)]
    [string] $PluginApk,

    [Parameter(Mandatory)]
    [ValidatePattern('^[0-9A-Fa-f]{64}$')]
    [string] $PluginSha256,

    [Parameter(Mandatory)]
    [string] $Aapt2Path,

    [Parameter(Mandatory)]
    [string] $ApkSignerPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false

# This verifier is intentionally local and read-only. It consumes seven successful
# diagnostic transcripts, but it neither invokes their Gradle/ADB producers nor
# writes an R2 acceptance receipt. Its sole output is one JSON object on stdout.
$expectedSerial = 'QV710AF65F'
$expectedDeviceAbi = 'arm64-v8a'
$expectedSignerSha256 = '2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8'
$hostPackage = 'org.autojs.autojs6'
$testPackage = 'org.autojs.autojs6.test'
$pluginPackage = 'io.github.supermonster003.autojs6.plugin.python.runtime'
$maximumEvidenceBytes = 1MB

function Resolve-ExactFile {
    param(
        [Parameter(Mandatory)] [string] $Path,
        [Parameter(Mandatory)] [string] $Label
    )

    $resolved = @(Resolve-Path -LiteralPath $Path -ErrorAction Stop)
    if ($resolved.Count -ne 1) {
        throw "$Label must resolve to exactly one file"
    }

    $item = Get-Item -LiteralPath $resolved[0].ProviderPath -Force
    if ($item.PSIsContainer) {
        throw "$Label is not a file: $($item.FullName)"
    }
    return $item
}

function Invoke-NativeCapture {
    param(
        [Parameter(Mandatory)] [string] $Executable,
        [Parameter(Mandatory)] [string[]] $Arguments,
        [Parameter(Mandatory)] [string] $Label
    )

    $nativeLines = @(& $Executable @Arguments 2>&1 | ForEach-Object { $_.ToString() })
    $exitCode = $LASTEXITCODE
    $text = [string]::Join([Environment]::NewLine, $nativeLines)
    if ($exitCode -ne 0) {
        throw "$Label failed with exit code $exitCode`n$text"
    }
    return [pscustomobject]@{
        ExitCode = $exitCode
        Text = $text
    }
}

function Normalize-Sha256 {
    param(
        [Parameter(Mandatory)] $Value,
        [Parameter(Mandatory)] [string] $Label
    )

    if ($Value -isnot [string] -or $Value -notmatch '^[0-9A-Fa-f]{64}$') {
        throw "$Label is not one SHA-256 hex digest"
    }
    return $Value.ToLowerInvariant()
}

function Get-RequiredProperty {
    param(
        [Parameter(Mandatory)] [psobject] $InputObject,
        [Parameter(Mandatory)] [string] $Name,
        [Parameter(Mandatory)] [string] $Label
    )

    $property = $InputObject.PSObject.Properties[$Name]
    if ($null -eq $property) {
        throw "$Label is missing required property '$Name'"
    }
    return $property.Value
}

function Assert-ExactString {
    param(
        [Parameter(Mandatory)] $Actual,
        [Parameter(Mandatory)] [string] $Expected,
        [Parameter(Mandatory)] [string] $Label
    )

    if ($Actual -isnot [string] -or $Actual -cne $Expected) {
        throw "$Label must be exactly '$Expected'; actual='$Actual'"
    }
}

function Assert-FalseBoolean {
    param(
        [Parameter(Mandatory)] $Actual,
        [Parameter(Mandatory)] [string] $Label
    )

    if ($Actual -isnot [bool] -or $Actual) {
        throw "$Label must be the JSON boolean false"
    }
}

function Get-ExactStringSet {
    param(
        [Parameter(Mandatory)]
        [AllowEmptyCollection()]
        [object[]] $Values,
        [Parameter(Mandatory)] [string] $Label
    )

    $set = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($value in $Values) {
        if ($value -isnot [string] -or [string]::IsNullOrWhiteSpace($value)) {
            throw "$Label contains a non-string or empty value"
        }
        if (-not $set.Add($value)) {
            throw "$Label contains duplicate value '$value'"
        }
    }
    # HashSet is enumerable; prevent PowerShell from unrolling it into strings.
    return ,$set
}

function Assert-ExactStringSet {
    param(
        [Parameter(Mandatory)]
        [AllowEmptyCollection()]
        [object[]] $Actual,
        [Parameter(Mandatory)]
        [AllowEmptyCollection()]
        [string[]] $Expected,
        [Parameter(Mandatory)] [string] $Label
    )

    $actualSet = Get-ExactStringSet -Values $Actual -Label $Label
    $expectedSet = Get-ExactStringSet -Values $Expected -Label "expected $Label"
    if ($actualSet.Count -ne $expectedSet.Count) {
        throw "$Label count mismatch: expected $($expectedSet.Count), actual $($actualSet.Count)"
    }
    foreach ($value in $expectedSet) {
        if (-not $actualSet.Contains($value)) {
            throw "$Label is missing '$value'"
        }
    }
}

function Get-FrozenUserIds {
    param(
        [Parameter(Mandatory)] $Value,
        [Parameter(Mandatory)] [string] $Label
    )

    $values = @($Value)
    if ($values.Count -eq 0) {
        throw "$Label must contain at least Android user 0"
    }

    $ids = [Collections.Generic.List[int]]::new()
    $seen = [Collections.Generic.HashSet[int]]::new()
    foreach ($valueEntry in $values) {
        if ($null -eq $valueEntry -or $valueEntry -is [string] -or $valueEntry -is [bool]) {
            throw "$Label contains a non-integer JSON value"
        }
        $typeCode = [Convert]::GetTypeCode($valueEntry)
        if ($typeCode -notin @(
            [TypeCode]::Byte,
            [TypeCode]::SByte,
            [TypeCode]::Int16,
            [TypeCode]::UInt16,
            [TypeCode]::Int32,
            [TypeCode]::UInt32,
            [TypeCode]::Int64,
            [TypeCode]::UInt64
        )) {
            throw "$Label contains a non-integer JSON value"
        }

        $number = [int64] $valueEntry
        if ($number -lt 0 -or $number -gt [int]::MaxValue) {
            throw "$Label contains out-of-range Android user ID '$number'"
        }
        $id = [int] $number
        if (-not $seen.Add($id)) {
            throw "$Label contains duplicate Android user ID '$id'"
        }
        $ids.Add($id)
    }

    if (-not $seen.Contains(0)) {
        throw "$Label must include Android user 0"
    }
    return $ids.ToArray()
}

function Assert-ExactIntegerSequence {
    param(
        [Parameter(Mandatory)] [int[]] $Actual,
        [Parameter(Mandatory)] [int[]] $Expected,
        [Parameter(Mandatory)] [string] $Label
    )

    if ($Actual.Count -ne $Expected.Count) {
        throw "$Label count mismatch: expected $($Expected.Count), actual $($Actual.Count)"
    }
    for ($index = 0; $index -lt $Expected.Count; $index++) {
        if ($Actual[$index] -ne $Expected[$index]) {
            throw "$Label sequence mismatch at index $index`: expected $($Expected[$index]), actual $($Actual[$index])"
        }
    }
}

function Test-IsUnderBuildTree {
    param([Parameter(Mandatory)] [IO.FileInfo] $File)

    $directory = $File.Directory
    while ($null -ne $directory) {
        if ($directory.Name.Equals('build', [StringComparison]::OrdinalIgnoreCase)) {
            return $true
        }
        $directory = $directory.Parent
    }
    return $false
}

function Assert-NoHistoricalBuildMarker {
    param([Parameter(Mandatory)] [IO.FileInfo[]] $EvidenceFiles)

    $scannedDirectories = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($evidenceFile in $EvidenceFiles) {
        if (-not (Test-IsUnderBuildTree -File $evidenceFile)) {
            continue
        }
        $directory = $evidenceFile.Directory
        if (-not $scannedDirectories.Add($directory.FullName)) {
            continue
        }

        foreach ($candidate in @(Get-ChildItem -LiteralPath $directory.FullName -File -Force)) {
            if ($candidate.Name -match '(?i)(^|[-_.])(historical|stale|invalidated)([-_.]|$)') {
                throw "Historical build marker rejects this bundle: $($candidate.FullName)"
            }
            if ($candidate.Length -le 0 -or $candidate.Length -gt $maximumEvidenceBytes) {
                continue
            }
            if ($candidate.Extension -notin @('.json', '.txt', '.marker')) {
                continue
            }
            $candidateText = [IO.File]::ReadAllText($candidate.FullName)
            if ($candidateText -match '(?im)(?:^|[\"'']?)status[\"'']?(?:\s*[:=]\s*)[\"'']?HISTORICAL_ONLY\b') {
                throw "Historical build marker rejects this bundle: $($candidate.FullName)"
            }
        }
    }
}

function Assert-UniqueEvidenceFiles {
    param([Parameter(Mandatory)] [Collections.IDictionary] $EvidenceFiles)

    $paths = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $digests = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($entry in $EvidenceFiles.GetEnumerator()) {
        $file = [IO.FileInfo] $entry.Value
        if (-not $paths.Add($file.FullName)) {
            throw "Duplicate evidence path supplied for '$($entry.Key)': $($file.FullName)"
        }
        $digest = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        if (-not $digests.Add($digest)) {
            throw "Duplicate evidence content supplied for '$($entry.Key)': SHA-256=$digest"
        }
    }
}

function Read-EvidenceDocument {
    param(
        [Parameter(Mandatory)] [IO.FileInfo] $File,
        [Parameter(Mandatory)] [string] $Label
    )

    if ($File.Length -le 0 -or $File.Length -gt $maximumEvidenceBytes) {
        throw "$Label size must be in 1..$maximumEvidenceBytes bytes; actual=$($File.Length)"
    }

    try {
        $json = [IO.File]::ReadAllText($File.FullName, [Text.UTF8Encoding]::new($false, $true))
        $document = $json | ConvertFrom-Json -Depth 32 -NoEnumerate
    } catch {
        throw "$Label is not one valid UTF-8 JSON document: $($_.Exception.Message)"
    }

    if ($null -eq $document -or $document -is [Array] -or $document -isnot [pscustomobject]) {
        throw "$Label must contain one JSON object"
    }
    return $document
}

function Get-ApkBadging {
    param([Parameter(Mandatory)] [string] $Path)

    return Invoke-NativeCapture `
        -Executable $script:Aapt2Executable `
        -Arguments @('dump', 'badging', $Path) `
        -Label "aapt2 package inspection for $Path"
}

function Get-ApkPackageName {
    param([Parameter(Mandatory)] [string] $Path)

    $badging = Get-ApkBadging -Path $Path
    $match = [regex]::Match($badging.Text, "(?m)^package:\s+name='([^']+)'(?:\s|$)")
    if (-not $match.Success) {
        throw "Unable to read package name from APK: $Path"
    }
    return $match.Groups[1].Value
}

function Get-ApkNativeAbis {
    param([Parameter(Mandatory)] [string] $Path)

    $badging = Get-ApkBadging -Path $Path
    $line = [regex]::Match($badging.Text, '(?m)^native-code:\s+(.+)$')
    if (-not $line.Success) {
        return @()
    }
    return @(
        [regex]::Matches($line.Groups[1].Value, "'([^']+)'") |
            ForEach-Object { $_.Groups[1].Value } |
            Sort-Object -Unique
    )
}

function Get-ApkSignerDigests {
    param([Parameter(Mandatory)] [string] $Path)

    $result = Invoke-NativeCapture `
        -Executable $script:ApkSignerExecutable `
        -Arguments @('verify', '--print-certs', $Path) `
        -Label "apksigner verification for $Path"
    $matches = [regex]::Matches(
        $result.Text,
        '(?im)^Signer #\d+ certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$'
    )
    $digests = @(
        $matches |
            ForEach-Object { $_.Groups[1].Value.ToLowerInvariant() } |
            Sort-Object -Unique
    )
    if ($digests.Count -eq 0) {
        throw "Unable to read signer SHA-256 digest from APK: $Path"
    }
    return $digests
}

function Assert-CurrentArtifact {
    param(
        [Parameter(Mandatory)] [string] $Role,
        [Parameter(Mandatory)] [string] $PackageName,
        [Parameter(Mandatory)] [IO.FileInfo] $File,
        [Parameter(Mandatory)] [string] $ExpectedSha256,
        [Parameter(Mandatory)]
        [AllowEmptyCollection()]
        [string[]] $ExpectedNativeAbis
    )

    if (-not $File.Extension.Equals('.apk', [StringComparison]::OrdinalIgnoreCase)) {
        throw "$Role artifact is not an APK: $($File.FullName)"
    }
    $expected = Normalize-Sha256 -Value $ExpectedSha256 -Label "$Role expected SHA-256"
    $actual = (Get-FileHash -LiteralPath $File.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -cne $expected) {
        throw "$Role current APK SHA-256 mismatch: expected=$expected actual=$actual path=$($File.FullName)"
    }

    $actualPackageName = Get-ApkPackageName -Path $File.FullName
    if ($actualPackageName -cne $PackageName) {
        throw "$Role APK package mismatch: expected=$PackageName actual=$actualPackageName path=$($File.FullName)"
    }
    $signers = @(Get-ApkSignerDigests -Path $File.FullName)
    if ($signers.Count -ne 1 -or $signers[0] -cne $expectedSignerSha256) {
        throw "$Role APK signer is not the single pinned diagnostic signer: expected=$expectedSignerSha256 actual=$($signers -join ',')"
    }
    $nativeAbis = @(Get-ApkNativeAbis -Path $File.FullName)
    Assert-ExactStringSet `
        -Actual $nativeAbis `
        -Expected $ExpectedNativeAbis `
        -Label "$Role APK native ABIs"

    return [pscustomobject]@{
        Role = $Role
        PackageName = $actualPackageName
        Path = $File.FullName
        Sha256 = $actual
        Bytes = $File.Length
        SignerSha256 = $signers[0]
        NativeAbis = $nativeAbis
    }
}

function Assert-EvidenceDocument {
    param(
        [Parameter(Mandatory)] [pscustomobject] $Spec,
        [Parameter(Mandatory)] [Collections.IDictionary] $ExpectedArtifactShaByPackage
    )

    $label = "$($Spec.Name) evidence"
    $document = Read-EvidenceDocument -File $Spec.File -Label $label
    foreach ($property in $document.PSObject.Properties) {
        if ($property.Name -match '(?i)(historical|stale|invalidated)') {
            throw "$label contains forbidden historical-build marker property '$($property.Name)'"
        }
    }

    $schema = Get-RequiredProperty -InputObject $document -Name 'schema' -Label $label
    if ($schema -is [string] -or $schema -ne 1) {
        throw "$label schema must be numeric 1"
    }
    Assert-ExactString `
        -Actual (Get-RequiredProperty -InputObject $document -Name 'status' -Label $label) `
        -Expected 'PARTIAL_DIAGNOSTIC' `
        -Label "$label status"
    Assert-ExactString `
        -Actual (Get-RequiredProperty -InputObject $document -Name 'scope' -Label $label) `
        -Expected $Spec.Scope `
        -Label "$label scope"
    Assert-ExactString `
        -Actual (Get-RequiredProperty -InputObject $document -Name 'exactSerial' -Label $label) `
        -Expected $Serial `
        -Label "$label exactSerial"

    Assert-FalseBoolean `
        -Actual (Get-RequiredProperty -InputObject $document -Name 'acceptanceReceiptWritten' -Label $label) `
        -Label "$label acceptanceReceiptWritten"
    Assert-FalseBoolean `
        -Actual (Get-RequiredProperty -InputObject $document -Name 'r2DeviceAcceptance' -Label $label) `
        -Label "$label r2DeviceAcceptance"
    Assert-FalseBoolean `
        -Actual (Get-RequiredProperty -InputObject $document -Name 'appDataSnapshotTaken' -Label $label) `
        -Label "$label appDataSnapshotTaken"

    $pinnedSignerProperty = $document.PSObject.Properties['pinnedSignerSha256']
    if ($null -ne $pinnedSignerProperty) {
        $reportedPinnedSigner = Normalize-Sha256 -Value $pinnedSignerProperty.Value -Label "$label pinnedSignerSha256"
        if ($reportedPinnedSigner -cne $expectedSignerSha256) {
            throw "$label pinnedSignerSha256 does not match the pinned diagnostic signer"
        }
    }

    Assert-ExactString `
        -Actual (Get-RequiredProperty -InputObject $document -Name 'packageInstallationRestoration' -Label $label) `
        -Expected $Spec.Restoration `
        -Label "$label packageInstallationRestoration"
    if ($null -ne $Spec.UserRestoration) {
        Assert-ExactString `
            -Actual (Get-RequiredProperty -InputObject $document -Name 'androidUserSetRestoration' -Label $label) `
            -Expected $Spec.UserRestoration `
            -Label "$label androidUserSetRestoration"
    }

    $artifacts = @(Get-RequiredProperty -InputObject $document -Name 'artifacts' -Label $label)
    if ($artifacts.Count -ne $ExpectedArtifactShaByPackage.Count) {
        throw "$label artifacts must contain exactly $($ExpectedArtifactShaByPackage.Count) entries"
    }
    $artifactPackages = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($artifact in $artifacts) {
        if ($artifact -isnot [pscustomobject]) {
            throw "$label artifacts contains a non-object entry"
        }
        $packageName = Get-RequiredProperty -InputObject $artifact -Name 'packageName' -Label "$label artifact"
        if ($packageName -isnot [string] -or -not $ExpectedArtifactShaByPackage.Contains($packageName)) {
            throw "$label contains unexpected artifact package '$packageName'"
        }
        if (-not $artifactPackages.Add($packageName)) {
            throw "$label contains duplicate artifact package '$packageName'"
        }
        $artifactSha256 = Normalize-Sha256 `
            -Value (Get-RequiredProperty -InputObject $artifact -Name 'sha256' -Label "$label artifact $packageName") `
            -Label "$label artifact $packageName SHA-256"
        if ($artifactSha256 -cne $ExpectedArtifactShaByPackage[$packageName]) {
            throw "$label artifact $packageName is not the supplied current APK: expected=$($ExpectedArtifactShaByPackage[$packageName]) actual=$artifactSha256"
        }
        $artifactSignerProperty = $artifact.PSObject.Properties['signerSha256']
        if ($null -ne $artifactSignerProperty) {
            $artifactSigner = Normalize-Sha256 -Value $artifactSignerProperty.Value -Label "$label artifact $packageName signerSha256"
            if ($artifactSigner -cne $expectedSignerSha256) {
                throw "$label artifact $packageName signerSha256 is not the pinned diagnostic signer"
            }
        }
    }

    $device = Get-RequiredProperty -InputObject $document -Name 'device' -Label $label
    if ($device -isnot [pscustomobject]) {
        throw "$label device must be an object"
    }
    $api = Get-RequiredProperty -InputObject $device -Name 'api' -Label "$label device"
    $apiNumber = 0
    if (-not [int]::TryParse([string] $api, [ref] $apiNumber) -or $apiNumber -lt 1) {
        throw "$label device.api must be a positive integer"
    }
    $abi = Get-RequiredProperty -InputObject $device -Name 'abi' -Label "$label device"
    Assert-ExactString -Actual $abi -Expected 'arm64-v8a' -Label "$label device.abi"
    $fingerprint = Get-RequiredProperty -InputObject $device -Name 'fingerprint' -Label "$label device"
    if ($fingerprint -isnot [string] -or [string]::IsNullOrWhiteSpace($fingerprint)) {
        throw "$label device.fingerprint must be a non-empty string"
    }
    $frozenUserIds = @(Get-FrozenUserIds `
        -Value (Get-RequiredProperty -InputObject $device -Name 'frozenUserIds' -Label "$label device") `
        -Label "$label device.frozenUserIds")

    $observationValue = Get-RequiredProperty -InputObject $document -Name $Spec.ObservationProperty -Label $label
    $observations = @($observationValue)
    if ($observations.Count -ne $Spec.ExpectedIdentifiers.Count) {
        throw "$label observation count mismatch: expected $($Spec.ExpectedIdentifiers.Count), actual $($observations.Count)"
    }

    $identifiers = [Collections.Generic.List[string]]::new()
    foreach ($observation in $observations) {
        if ($observation -isnot [pscustomobject]) {
            throw "$label contains a non-object observation"
        }
        $identifier = Get-RequiredProperty -InputObject $observation -Name $Spec.IdentifierProperty -Label "$label observation"
        if ($identifier -isnot [string]) {
            throw "$label observation identifier must be a string"
        }
        Assert-ExactString `
            -Actual (Get-RequiredProperty -InputObject $observation -Name 'Observation' -Label "$label observation $identifier") `
            -Expected 'OK_1_TEST_ZERO_SKIP' `
            -Label "$label observation $identifier result"
        $identifiers.Add($identifier)
    }
    Assert-ExactStringSet `
        -Actual $identifiers.ToArray() `
        -Expected $Spec.ExpectedIdentifiers `
        -Label "$label case identifiers"

    $caseMethods = @($identifiers | ForEach-Object {
        if ($_.Contains('#')) { $_.Substring($_.LastIndexOf('#') + 1) } else { $_ }
    })
    return [pscustomobject]@{
        Name = $Spec.Name
        Path = $Spec.File.FullName
        Sha256 = (Get-FileHash -LiteralPath $Spec.File.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        Scope = $Spec.Scope
        CaseMethods = $caseMethods
        ZeroSkipCaseCount = $caseMethods.Count
        PackageInstallationRestoration = $Spec.Restoration
        DeviceApi = $apiNumber
        DeviceAbi = $abi
        DeviceFingerprint = $fingerprint
        FrozenUserIds = $frozenUserIds
    }
}

if ($Serial -cne $expectedSerial) {
    throw "This partial bundle is pinned to exact serial $expectedSerial"
}

$resolvedAapt2 = Resolve-ExactFile -Path $Aapt2Path -Label 'Aapt2Path'
$resolvedApkSigner = Resolve-ExactFile -Path $ApkSignerPath -Label 'ApkSignerPath'
$script:Aapt2Executable = $resolvedAapt2.FullName
$script:ApkSignerExecutable = $resolvedApkSigner.FullName
$resolvedHostApk = Resolve-ExactFile -Path $HostApk -Label 'HostApk'
$resolvedTestApk = Resolve-ExactFile -Path $TestApk -Label 'TestApk'
$resolvedPluginApk = Resolve-ExactFile -Path $PluginApk -Label 'PluginApk'
$currentArtifacts = @(
    Assert-CurrentArtifact `
        -Role 'host' `
        -PackageName $hostPackage `
        -File $resolvedHostApk `
        -ExpectedSha256 $HostSha256 `
        -ExpectedNativeAbis @($expectedDeviceAbi)
    Assert-CurrentArtifact `
        -Role 'androidTest' `
        -PackageName $testPackage `
        -File $resolvedTestApk `
        -ExpectedSha256 $TestSha256 `
        -ExpectedNativeAbis @()
    Assert-CurrentArtifact `
        -Role 'realPlugin' `
        -PackageName $pluginPackage `
        -File $resolvedPluginApk `
        -ExpectedSha256 $PluginSha256 `
        -ExpectedNativeAbis @($expectedDeviceAbi)
)
Assert-ExactStringSet `
    -Actual @($currentArtifacts | ForEach-Object { $_.Path }) `
    -Expected @($resolvedHostApk.FullName, $resolvedTestApk.FullName, $resolvedPluginApk.FullName) `
    -Label 'current artifact paths'

$expectedArtifactShaByPackage = [ordered]@{}
foreach ($artifact in $currentArtifacts) {
    $expectedArtifactShaByPackage[$artifact.PackageName] = $artifact.Sha256
}

$evidenceInputs = [ordered]@{
    positive = $PositiveEvidence
    executionMatrix = $ExecutionMatrixEvidence
    cancelRebind = $CancelRebindEvidence
    timeoutRebind = $TimeoutRebindEvidence
    reliablePipe = $ReliablePipeEvidence
    stagingTimeout = $StagingTimeoutEvidence
    blockingRetirement = $BlockingRetirementEvidence
}
$resolvedEvidence = [ordered]@{}
foreach ($entry in $evidenceInputs.GetEnumerator()) {
    $resolvedEvidence[$entry.Key] = Resolve-ExactFile -Path $entry.Value -Label "$($entry.Key) evidence"
}
Assert-UniqueEvidenceFiles -EvidenceFiles $resolvedEvidence
Assert-NoHistoricalBuildMarker -EvidenceFiles @($resolvedEvidence.Values)

$specs = @(
    [pscustomobject]@{
        Name = 'positive'
        File = $resolvedEvidence.positive
        Scope = 'R2_REAL_CPYTHON_BINDER_PFD_SINGLE_CASE_DIAGNOSTIC_ONLY'
        Restoration = 'VERIFIED_ABSENT_ALL_FROZEN_USERS_AND_GLOBAL'
        UserRestoration = $null
        ObservationProperty = 'observation'
        IdentifierProperty = 'Method'
        ExpectedIdentifiers = @('realPluginMetadataActionNullBindAndCpythonPfdExecution')
    },
    [pscustomobject]@{
        Name = 'executionMatrix'
        File = $resolvedEvidence.executionMatrix
        Scope = 'R2_REAL_CPYTHON_EXECUTION_MATRIX_FOUR_CASE_DIAGNOSTIC_ONLY'
        Restoration = 'VERIFIED_ABSENT_ALL_FROZEN_USERS_AND_GLOBAL'
        UserRestoration = $null
        ObservationProperty = 'observations'
        IdentifierProperty = 'Selector'
        ExpectedIdentifiers = @(
            'org.autojs.autojs.core.plugin.python.PythonRuntimeRealPluginExecutionMatrixDiagnosticTest#systemExitReturnsBoundedExitCode',
            'org.autojs.autojs.core.plugin.python.PythonRuntimeRealPluginExecutionMatrixDiagnosticTest#syntaxErrorReturnsStructuredProjectTraceback',
            'org.autojs.autojs.core.plugin.python.PythonRuntimeRealPluginExecutionMatrixDiagnosticTest#runtimeExceptionReturnsStructuredProjectTraceback',
            'org.autojs.autojs.core.plugin.python.PythonRuntimeRealPluginExecutionMatrixDiagnosticTest#outputLimitFailsAfterDeliveringOnlyBudgetedChunks'
        )
    },
    [pscustomobject]@{
        Name = 'cancelRebind'
        File = $resolvedEvidence.cancelRebind
        Scope = 'R2_REAL_CPYTHON_CANCEL_DEATH_REBIND_SINGLE_CASE_DIAGNOSTIC_ONLY'
        Restoration = 'VERIFIED_ABSENT_ALL_FROZEN_USERS_AND_GLOBAL'
        UserRestoration = $null
        ObservationProperty = 'observation'
        IdentifierProperty = 'Method'
        ExpectedIdentifiers = @('cancelAfterStartedKillsOldBinderThenExactRebindRunsFiniteRequestOnce')
    },
    [pscustomobject]@{
        Name = 'timeoutRebind'
        File = $resolvedEvidence.timeoutRebind
        Scope = 'R2_REAL_CPYTHON_TIMEOUT_DEATH_REBIND_SINGLE_CASE_DIAGNOSTIC_ONLY'
        Restoration = 'VERIFIED_ABSENT_ALL_FROZEN_USERS'
        UserRestoration = 'VERIFIED_UNCHANGED'
        ObservationProperty = 'observation'
        IdentifierProperty = 'Method'
        ExpectedIdentifiers = @('providerTimeoutFailsBeforeOldBinderDeathThenExactRebindRunsFiniteRequest')
    },
    [pscustomobject]@{
        Name = 'reliablePipe'
        File = $resolvedEvidence.reliablePipe
        Scope = 'R2_REAL_CPYTHON_RELIABLE_PIPE_ERROR_AND_EXACT_REBIND_DIAGNOSTIC_ONLY'
        Restoration = 'VERIFIED_ABSENT_ALL_FROZEN_USERS'
        UserRestoration = 'VERIFIED_UNCHANGED'
        ObservationProperty = 'observation'
        IdentifierProperty = 'Selector'
        ExpectedIdentifiers = @('org.autojs.autojs.core.plugin.python.PythonRuntimeRealPluginReliablePipeDiagnosticTest#producerErrorRejectsBeforeCpythonThenExactRebindExecutesFiniteSource')
    },
    [pscustomobject]@{
        Name = 'stagingTimeout'
        File = $resolvedEvidence.stagingTimeout
        Scope = 'R2_REAL_CPYTHON_RELIABLE_PIPE_STAGING_TIMEOUT_AND_EXACT_REBIND_DIAGNOSTIC_ONLY'
        Restoration = 'VERIFIED_ABSENT_ALL_FROZEN_USERS_AND_GLOBAL_RECORD'
        UserRestoration = $null
        ObservationProperty = 'observation'
        IdentifierProperty = 'Selector'
        ExpectedIdentifiers = @('org.autojs.autojs.core.plugin.python.PythonRuntimeRealPluginStagingTimeoutDiagnosticTest#stagingDeadlineFailsBeforeOldBinderDeathThenExactRebindRunsFiniteReliablePipeOnce')
    },
    [pscustomobject]@{
        Name = 'blockingRetirement'
        File = $resolvedEvidence.blockingRetirement
        Scope = 'R2_REAL_CPYTHON_BLOCKING_AND_NATIVE_TIMEOUT_RETIREMENT_TWO_CASE_DIAGNOSTIC_ONLY'
        Restoration = 'VERIFIED_ABSENT_ALL_FROZEN_USERS'
        UserRestoration = 'VERIFIED_UNCHANGED'
        ObservationProperty = 'observations'
        IdentifierProperty = 'Method'
        ExpectedIdentifiers = @(
            'blockingOsReadTimesOutBeforeBinderDeathThenExactRebindRunsFiniteRequestOnce',
            'nativePbkdf2TimesOutBeforeBinderDeathThenExactRebindRunsFiniteRequestOnce'
        )
    }
)

$verifiedEvidence = [Collections.Generic.List[pscustomobject]]::new()
foreach ($spec in $specs) {
    $verifiedEvidence.Add((Assert-EvidenceDocument -Spec $spec -ExpectedArtifactShaByPackage $expectedArtifactShaByPackage))
}

$deviceApis = @($verifiedEvidence | ForEach-Object { [string] $_.DeviceApi })
$deviceAbis = @($verifiedEvidence | ForEach-Object { $_.DeviceAbi })
$deviceFingerprints = @($verifiedEvidence | ForEach-Object { $_.DeviceFingerprint })
$frozenUserIds = @($verifiedEvidence[0].FrozenUserIds)
if (@($deviceApis | Where-Object { $_ -cne $deviceApis[0] }).Count -ne 0) {
    throw 'The seven evidence documents do not describe one device API level'
}
if (@($deviceAbis | Where-Object { $_ -cne $deviceAbis[0] }).Count -ne 0) {
    throw 'The seven evidence documents do not describe one device ABI'
}
if (@($deviceFingerprints | Where-Object { $_ -cne $deviceFingerprints[0] }).Count -ne 0) {
    throw 'The seven evidence documents do not describe one device fingerprint'
}
foreach ($evidence in $verifiedEvidence) {
    Assert-ExactIntegerSequence `
        -Actual @($evidence.FrozenUserIds) `
        -Expected $frozenUserIds `
        -Label "$($evidence.Name) evidence frozenUserIds"
}

$expectedCaseMethods = @(
    'realPluginMetadataActionNullBindAndCpythonPfdExecution',
    'systemExitReturnsBoundedExitCode',
    'syntaxErrorReturnsStructuredProjectTraceback',
    'runtimeExceptionReturnsStructuredProjectTraceback',
    'outputLimitFailsAfterDeliveringOnlyBudgetedChunks',
    'cancelAfterStartedKillsOldBinderThenExactRebindRunsFiniteRequestOnce',
    'providerTimeoutFailsBeforeOldBinderDeathThenExactRebindRunsFiniteRequest',
    'producerErrorRejectsBeforeCpythonThenExactRebindExecutesFiniteSource',
    'stagingDeadlineFailsBeforeOldBinderDeathThenExactRebindRunsFiniteReliablePipeOnce',
    'blockingOsReadTimesOutBeforeBinderDeathThenExactRebindRunsFiniteRequestOnce',
    'nativePbkdf2TimesOutBeforeBinderDeathThenExactRebindRunsFiniteRequestOnce'
)
$actualCaseMethods = @($verifiedEvidence | ForEach-Object { $_.CaseMethods })
Assert-ExactStringSet -Actual $actualCaseMethods -Expected $expectedCaseMethods -Label 'combined diagnostic case methods'

[ordered]@{
    schema = 1
    status = 'PARTIAL_DIAGNOSTIC'
    scope = 'R2_CURRENT_TREE_SEVEN_RUNNER_PARTIAL_BUNDLE_ONLY'
    exactSerial = $Serial
    device = [ordered]@{
        api = [int] $deviceApis[0]
        abi = $deviceAbis[0]
        fingerprint = $deviceFingerprints[0]
        frozenUserIds = $frozenUserIds
    }
    artifacts = @($currentArtifacts | ForEach-Object {
        [ordered]@{
            role = $_.Role
            packageName = $_.PackageName
            path = $_.Path
            sha256 = $_.Sha256
            bytes = $_.Bytes
            signerSha256 = $_.SignerSha256
            nativeAbis = $_.NativeAbis
        }
    })
    evidence = @($verifiedEvidence | ForEach-Object {
        [ordered]@{
            name = $_.Name
            path = $_.Path
            sha256 = $_.Sha256
            scope = $_.Scope
            caseMethods = $_.CaseMethods
            zeroSkipCaseCount = $_.ZeroSkipCaseCount
            packageInstallationRestoration = $_.PackageInstallationRestoration
        }
    })
    diagnosticCaseCount = $actualCaseMethods.Count
    diagnosticCaseMethods = $actualCaseMethods
    skippedCaseCount = 0
    packageRestorationMarkersVerified = $true
    currentArtifactHashesVerified = $true
    currentArtifactPackagesVerifiedByAapt2 = $true
    currentArtifactNativeAbisVerifiedByAapt2 = $true
    currentArtifactSignerVerifiedByApkSigner = $true
    pinnedSignerSha256 = $expectedSignerSha256
    frozenUserIdsIdenticalAndIncludeZero = $true
    duplicateEvidenceRejected = $true
    historicalBuildMarkerRejected = $true
    acceptanceReceiptWritten = $false
    canonicalGateReceiptWrittenByVerifier = $false
    r2DeviceAcceptance = $false
    evidenceBoundary = @(
        'ONE_EXACT_API_LEVEL_ARM64_PHYSICAL_DEVICE_PARTIAL_DIAGNOSTIC_ONLY',
        'NO_GRADLE_OR_ADB_EXECUTED_BY_THIS_VERIFIER',
        'NOT_AN_R2_RUNTIME_POC_ACCEPTANCE_GATE'
    )
} | ConvertTo-Json -Depth 8
