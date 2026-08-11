#Requires -Version 7.0

[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^\S+$')]
    [string] $Serial,

    [Parameter(Mandatory)]
    [ValidateRange(24, 99)]
    [int] $ExpectedApi,

    [Parameter(Mandatory)]
    [ValidateSet('arm64-v8a', 'x86_64', 'armeabi-v7a', 'x86')]
    [string] $ExpectedAbi,

    [Parameter(Mandatory)]
    [int[]] $ExpectedUserIds,

    [Parameter(Mandatory)] [string] $Provenance,
    [Parameter(Mandatory)] [ValidatePattern('^[0-9A-Fa-f]{64}$')] [string] $ProvenanceSha256,
    [Parameter(Mandatory)] [ValidatePattern('^[0-9A-Fa-f]{64}$')] [string] $CandidateSignerSha256,
    [Parameter(Mandatory)] [ValidatePattern('^[0-9A-Fa-f]{64}$')] [string] $DifferentSignerSha256,

    [Parameter(Mandatory)] [string] $HostApk,
    [Parameter(Mandatory)] [ValidatePattern('^[0-9A-Fa-f]{64}$')] [string] $HostSha256,
    [Parameter(Mandatory)] [string] $HostTestApk,
    [Parameter(Mandatory)] [ValidatePattern('^[0-9A-Fa-f]{64}$')] [string] $HostTestSha256,
    [Parameter(Mandatory)] [string] $BaselinePluginApk,
    [Parameter(Mandatory)] [ValidatePattern('^[0-9A-Fa-f]{64}$')] [string] $BaselinePluginSha256,
    [Parameter(Mandatory)] [string] $CandidatePluginApk,
    [Parameter(Mandatory)] [ValidatePattern('^[0-9A-Fa-f]{64}$')] [string] $CandidatePluginSha256,
    [Parameter(Mandatory)] [string] $ConsumerApk,
    [Parameter(Mandatory)] [ValidatePattern('^[0-9A-Fa-f]{64}$')] [string] $ConsumerSha256,
    [Parameter(Mandatory)] [string] $ConsumerTestApk,
    [Parameter(Mandatory)] [ValidatePattern('^[0-9A-Fa-f]{64}$')] [string] $ConsumerTestSha256,
    [Parameter(Mandatory)] [string] $DifferentSignerConsumerApk,
    [Parameter(Mandatory)] [ValidatePattern('^[0-9A-Fa-f]{64}$')] [string] $DifferentSignerConsumerSha256,
    [Parameter(Mandatory)] [string] $DifferentSignerConsumerTestApk,
    [Parameter(Mandatory)] [ValidatePattern('^[0-9A-Fa-f]{64}$')] [string] $DifferentSignerConsumerTestSha256,

    [Parameter(Mandatory)] [string] $AdbPath,
    [Parameter(Mandatory)] [string] $Aapt2Path,
    [Parameter(Mandatory)] [string] $ApkSignerPath,
    [Parameter(Mandatory)] [switch] $ConfirmDeviceMutation
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false

# This is the single R6-P3 concentrated diagnostic runner. It deliberately emits only
# PARTIAL_DIAGNOSTIC or BLOCKED. It cannot write the final release receipt: the independent final
# verifier and declared-ABI coverage aggregation remain separate, explicit prerequisites.
$hostPackage = 'org.autojs.autojs6'
$hostTestPackage = 'org.autojs.autojs6.test'
$pluginPackage = 'io.github.supermonster003.autojs6.plugin.python.runtime'
$consumerPackage = 'org.autojs.plugin.python.runtime.consumer'
$consumerTestPackage = 'org.autojs.plugin.python.runtime.consumer.test'
$hostRunner = "$hostTestPackage/androidx.test.runner.AndroidJUnitRunner"
$consumerRunner = "$consumerTestPackage/androidx.test.runner.AndroidJUnitRunner"
$availabilityTest =
    'org.autojs.autojs.core.plugin.python.PythonRuntimeR6ProviderAvailabilityInstrumentationTest#' +
    'productionClientMatchesExplicitProviderAvailabilityPhase'
$relayTest =
    'org.autojs.autojs.core.plugin.python.PythonRuntimeR6SessionRelayInstrumentationTest#' +
    'alternateUidCannotMutateRelayedSessionAndOwnerStillCompletesExactlyOnce'
$cancelRebindTest =
    'org.autojs.autojs.core.plugin.python.PythonRuntimeRealPluginCancelRebindDiagnosticTest#' +
    'cancelAfterStartedKillsOldBinderThenExactRebindRunsFiniteRequestOnce'
$timeoutRebindTest =
    'org.autojs.autojs.core.plugin.python.PythonRuntimeRealPluginTimeoutRebindDiagnosticTest#' +
    'providerTimeoutFailsBeforeOldBinderDeathThenExactRebindRunsFiniteRequest'
$sameSignerTest =
    'org.autojs.plugin.python.runtime.consumer.PythonRuntimeSameSignerDifferentUidAndroidTest#' +
    'sameSignerDifferentUidIsRejectedAtEveryProviderEntry'
$differentSignerTest =
    'org.autojs.plugin.python.runtime.consumer.PythonRuntimeSameSignerDifferentUidAndroidTest#' +
    'differentSignerCannotAcquirePluginPermissionOrBindProvider'

if (-not $ConfirmDeviceMutation) {
    throw 'Pass -ConfirmDeviceMutation explicitly; package lifecycle mutation is otherwise forbidden'
}

function Resolve-ExactFile {
    param([Parameter(Mandatory)] [string] $Path, [Parameter(Mandatory)] [string] $Label)

    $resolved = @(Resolve-Path -LiteralPath $Path -ErrorAction Stop)
    if ($resolved.Count -ne 1) { throw "$Label must resolve to exactly one file" }
    $item = Get-Item -LiteralPath $resolved[0].ProviderPath -Force
    if ($item.PSIsContainer) { throw "$Label is not a file: $($item.FullName)" }
    return $item.FullName
}

function Invoke-NativeCapture {
    param(
        [Parameter(Mandatory)] [string] $Executable,
        [Parameter(Mandatory)] [string[]] $Arguments,
        [Parameter(Mandatory)] [string] $Label,
        [switch] $AllowNonZero
    )

    $lines = @(& $Executable @Arguments 2>&1 | ForEach-Object { $_.ToString() })
    $exitCode = $LASTEXITCODE
    $captured = [string]::Join([Environment]::NewLine, $lines)
    if (-not $AllowNonZero -and $exitCode -ne 0) {
        throw "$Label failed with exit code $exitCode`n$captured"
    }
    return [pscustomobject]@{ ExitCode = $exitCode; Lines = $lines; Text = $captured }
}

function Get-Sha256 {
    param([Parameter(Mandatory)] [string] $Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-ApkBadging {
    param([Parameter(Mandatory)] [string] $Path)
    return Invoke-NativeCapture -Executable $script:Aapt2 -Arguments @('dump', 'badging', $Path) `
        -Label "aapt2 inspection for $Path"
}

function Get-ApkPackage {
    param([Parameter(Mandatory)] [string] $Path)
    $match = [regex]::Match((Get-ApkBadging -Path $Path).Text, "(?m)^package:\s+name='([^']+)'(?:\s|$)")
    if (-not $match.Success) { throw "Unable to read APK package: $Path" }
    return $match.Groups[1].Value
}

function Get-ApkAbis {
    param([Parameter(Mandatory)] [string] $Path)
    $match = [regex]::Match((Get-ApkBadging -Path $Path).Text, '(?m)^native-code:\s+(.+)$')
    if (-not $match.Success) { return @() }
    return @(
        [regex]::Matches($match.Groups[1].Value, "'([^']+)'") |
            ForEach-Object { $_.Groups[1].Value } |
            Sort-Object -Unique
    )
}

function Get-ApkSigners {
    param([Parameter(Mandatory)] [string] $Path)
    $result = Invoke-NativeCapture -Executable $script:ApkSigner `
        -Arguments @('verify', '--print-certs', $Path) -Label "apksigner verification for $Path"
    return @(
        [regex]::Matches(
            $result.Text,
            '(?im)^Signer #\d+ certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$'
        ) |
            ForEach-Object { $_.Groups[1].Value.ToLowerInvariant() } |
            Sort-Object -Unique
    )
}

function Get-ApkVersionCode {
    param([Parameter(Mandatory)] [string] $Path)
    $match = [regex]::Match(
        (Get-ApkBadging -Path $Path).Text,
        "(?m)^package:\s+name='[^']+'\s+versionCode='(\d+)'(?:\s|$)"
    )
    if (-not $match.Success) { throw "Unable to read APK versionCode: $Path" }
    return [long] $match.Groups[1].Value
}

function Assert-Apk {
    param(
        [Parameter(Mandatory)] [string] $Label,
        [Parameter(Mandatory)] [string] $Path,
        [Parameter(Mandatory)] [string] $Sha256,
        [Parameter(Mandatory)] [string] $Package,
        [Parameter(Mandatory)] [string] $Signer,
        [Parameter(Mandatory)] [ValidateSet('EXACT', 'NONE')] [string] $AbiMode
    )

    if ((Get-Sha256 -Path $Path) -cne $Sha256.ToLowerInvariant()) {
        throw "$Label SHA-256 differs from the exact argument"
    }
    if ((Get-ApkPackage -Path $Path) -cne $Package) { throw "$Label package identity changed" }
    $signers = @(Get-ApkSigners -Path $Path)
    if ($signers.Count -ne 1 -or $signers[0] -cne $Signer.ToLowerInvariant()) {
        throw "$Label signer differs from its exact pinned signer"
    }
    $abis = @(Get-ApkAbis -Path $Path)
    if ($AbiMode -ceq 'EXACT' -and ($abis.Count -ne 1 -or $abis[0] -cne $ExpectedAbi)) {
        throw "$Label must contain only $ExpectedAbi; actual=$($abis -join ',')"
    }
    if ($AbiMode -ceq 'NONE' -and $abis.Count -ne 0) {
        throw "$Label must be ABI-neutral; actual=$($abis -join ',')"
    }
}

function New-ReadonlySnapshot {
    param(
        [Parameter(Mandatory)] [pscustomobject] $Artifact,
        [Parameter(Mandatory)] [string] $Directory
    )

    $target = Join-Path $Directory $Artifact.FileName
    Copy-Item -LiteralPath $Artifact.Path -Destination $target -ErrorAction Stop
    (Get-Item -LiteralPath $target -Force).IsReadOnly = $true
    Assert-Apk -Label "$($Artifact.Label) readonly snapshot" -Path $target `
        -Sha256 $Artifact.Sha256 -Package $Artifact.Package -Signer $Artifact.Signer -AbiMode $Artifact.AbiMode
    return [pscustomobject]@{
        Label = $Artifact.Label
        Path = $target
        Sha256 = $Artifact.Sha256.ToLowerInvariant()
        Package = $Artifact.Package
        Signer = $Artifact.Signer.ToLowerInvariant()
        AbiMode = $Artifact.AbiMode
    }
}

function Invoke-ExactAdb {
    param([Parameter(Mandatory)] [string[]] $Arguments, [Parameter(Mandatory)] [string] $Label, [switch] $AllowNonZero)
    return Invoke-NativeCapture -Executable $script:Adb -Arguments (@('-s', $Serial) + $Arguments) `
        -Label $Label -AllowNonZero:$AllowNonZero
}

function Get-DeviceUsers {
    $result = Invoke-ExactAdb -Arguments @('shell', 'pm', 'list', 'users') -Label 'frozen Android user inventory'
    $records = [regex]::Matches($result.Text, 'UserInfo\{([0-9]+):')
    $recordMarkers = [regex]::Matches($result.Text, 'UserInfo\{').Count
    if ($records.Count -eq 0 -or $records.Count -ne $recordMarkers) {
        throw 'Unable to parse the complete non-negative Android user inventory'
    }
    $userIds = @(
        $records | ForEach-Object {
            $userId = 0
            if (-not [int]::TryParse($_.Groups[1].Value, [ref] $userId) -or $userId -lt 0) {
                throw "Invalid Android user ID: $($_.Groups[1].Value)"
            }
            $userId
        }
    )
    $uniqueUserIds = @($userIds | Sort-Object -Unique)
    if ($uniqueUserIds.Count -ne $userIds.Count -or 0 -notin $uniqueUserIds) {
        throw "Android user inventory must contain unique non-negative IDs including 0: $($userIds -join ',')"
    }
    return $uniqueUserIds
}

function Assert-UserInventory {
    param([Parameter(Mandatory)] [string] $Label)
    $actual = @(Get-DeviceUsers)
    if ([string]::Join(',', $actual) -cne [string]::Join(',', $script:FrozenUsers)) {
        throw "$Label user inventory changed: expected=$($script:FrozenUsers -join ',') actual=$($actual -join ',')"
    }
}

function Test-PackageKnown {
    param(
        [Parameter(Mandatory)] [string] $Package,
        [AllowNull()] [Nullable[int]] $UserId = $null
    )

    $arguments = @('shell', 'pm', 'list', 'packages')
    $scope = 'global'
    if ($null -ne $UserId) {
        $arguments += @('--user', $UserId.ToString())
        $scope = "user $UserId"
    }
    $arguments += @('-u', $Package)
    $result = Invoke-ExactAdb -Arguments $arguments -Label "$scope package record for $Package" -AllowNonZero
    if ($result.ExitCode -ne 0) {
        throw "Unable to query $scope retained package record for $Package`n$($result.Text)"
    }
    $lines = @(
        $result.Lines |
            ForEach-Object { $_.Trim() } |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    )
    $malformed = @($lines | Where-Object { $_ -notmatch '^package:[A-Za-z0-9_][A-Za-z0-9._]*$' })
    $exactCount = @($lines | Where-Object { $_ -ceq "package:$Package" }).Count
    if ($malformed.Count -ne 0 -or $exactCount -gt 1) {
        throw "Unexpected $scope package-list output for $Package`n$($result.Text)"
    }
    return $exactCount -eq 1
}

function Get-PackageState {
    param([Parameter(Mandatory)] [string] $Package)

    $userStates = @(
        foreach ($userId in $script:FrozenUsers) {
            $result = Invoke-ExactAdb -Arguments @(
                'shell', 'pm', 'path', '--user', $userId.ToString(), $Package
            ) -Label "package path for $Package user $userId" -AllowNonZero
            $missingWithEmptyOutput = $result.ExitCode -eq 1 -and [string]::IsNullOrWhiteSpace($result.Text)
            if ($result.ExitCode -ne 0 -and -not $missingWithEmptyOutput) {
                throw "Unable to query package path for $Package user $userId`n$($result.Text)"
            }
            $lines = @(
                $result.Lines |
                    ForEach-Object { $_.Trim() } |
                    Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
            )
            $paths = @(
                $lines |
                    Where-Object { $_.StartsWith('package:', [StringComparison]::Ordinal) } |
                    ForEach-Object { $_.Substring('package:'.Length) }
            )
            if ($lines.Count -ne $paths.Count -or @($paths | Where-Object { [string]::IsNullOrWhiteSpace($_) }).Count -ne 0) {
                throw "Unexpected package-path output for $Package user $userId`n$($result.Text)"
            }
            $known = Test-PackageKnown -Package $Package -UserId $userId
            if ($paths.Count -gt 0 -and -not $known) {
                throw "Package state is internally inconsistent for $Package user $userId"
            }
            [pscustomobject]@{
                UserId = $userId
                Installed = $paths.Count -gt 0
                Paths = $paths
                RetainedRecord = $known
            }
        }
    )
    $globalKnown = Test-PackageKnown -Package $Package
    if (@($userStates | Where-Object { $_.Installed }).Count -gt 0 -and -not $globalKnown) {
        throw "Global package state is internally inconsistent for $Package"
    }
    return [pscustomobject]@{
        Package = $Package
        UserStates = $userStates
        Installed = @($userStates | Where-Object { $_.Installed }).Count -gt 0
        InstalledUserIds = @($userStates | Where-Object { $_.Installed } | ForEach-Object { $_.UserId })
        Paths = @($userStates | ForEach-Object { $_.Paths } | Sort-Object -Unique)
        RetainedForAnyUser = @($userStates | Where-Object { $_.RetainedRecord }).Count -gt 0
        GlobalKnown = $globalKnown
    }
}

function Assert-PackageAbsent {
    param([Parameter(Mandatory)] [string] $Package, [Parameter(Mandatory)] [string] $Label)
    Assert-UserInventory -Label $Label
    $state = Get-PackageState -Package $Package
    if ($state.Installed -or $state.RetainedForAnyUser -or $state.GlobalKnown) {
        throw "$Label requires $Package absent from all frozen users and global records; installedUsers=$($state.InstalledUserIds -join ',')"
    }
}

function Install-ExactApk {
    param(
        [Parameter(Mandatory)] [pscustomobject] $Artifact,
        [switch] $Replace,
        [AllowNull()] [pscustomobject] $ExpectedCurrentArtifact = $null
    )

    Assert-UserInventory -Label "before install $($Artifact.Label)"
    $allowedDuringAttempt = @($Artifact)
    if ($Replace) {
        if ($null -eq $ExpectedCurrentArtifact -or $ExpectedCurrentArtifact.Package -cne $Artifact.Package) {
            throw "Replacing $($Artifact.Package) requires its exact current run-owned artifact"
        }
        if (-not $script:PackageOwnership.ContainsKey($Artifact.Package)) {
            throw "Replacing $($Artifact.Package) requires existing run ownership"
        }
        $existingOwnership = $script:PackageOwnership[$Artifact.Package]
        if (
            $existingOwnership.State -cne 'OWNED' -or
            @($existingOwnership.AllowedArtifacts).Count -ne 1 -or
            $existingOwnership.CurrentArtifact.Sha256 -cne $ExpectedCurrentArtifact.Sha256
        ) {
            throw "Replacing $($Artifact.Package) does not match the exact current run-owned artifact"
        }
        Assert-InstalledArtifact -Artifact $ExpectedCurrentArtifact
        $allowedDuringAttempt = @($ExpectedCurrentArtifact, $Artifact)
    } else {
        if ($script:PackageOwnership.ContainsKey($Artifact.Package)) {
            throw "Non-replacing install still has run-owned state: $($Artifact.Package)"
        }
        Assert-PackageAbsent -Package $Artifact.Package -Label "pre-install $($Artifact.Label)"
    }
    # Establish cleanup ownership before the install subprocess. A failed install can still have
    # committed package-manager state; cleanup may remove only one of these exact immutable bytes.
    $script:PackageOwnership[$Artifact.Package] = [pscustomobject]@{
        State = 'INSTALL_ATTEMPT'
        CurrentArtifact = $ExpectedCurrentArtifact
        AttemptArtifact = $Artifact
        AllowedArtifacts = $allowedDuringAttempt
    }
    $arguments = @('install', '--user', '0', '-t')
    if ($Replace) { $arguments += '-r' }
    $arguments += $Artifact.Path
    $result = Invoke-ExactAdb -Arguments $arguments -Label "install $($Artifact.Label)" -AllowNonZero
    if ($result.ExitCode -ne 0 -or $result.Text -notmatch '(?m)^Success\s*$') {
        throw "Unable to install $($Artifact.Label)`n$($result.Text)"
    }
    Assert-InstalledArtifact -Artifact $Artifact
    $script:PackageOwnership[$Artifact.Package] = [pscustomobject]@{
        State = 'OWNED'
        CurrentArtifact = $Artifact
        AttemptArtifact = $null
        AllowedArtifacts = @($Artifact)
    }
}

function Assert-InstalledArtifact {
    param([Parameter(Mandatory)] [pscustomobject] $Artifact)

    $null = Assert-InstalledArtifactSet -Artifacts @($Artifact) -Label $Artifact.Label
}

function Assert-InstalledArtifactSet {
    param(
        [Parameter(Mandatory)] [pscustomobject[]] $Artifacts,
        [Parameter(Mandatory)] [string] $Label
    )

    if ($Artifacts.Count -eq 0) { throw "$Label has no allowed run-owned artifact" }
    $package = $Artifacts[0].Package
    if (@($Artifacts | Where-Object { $_.Package -cne $package }).Count -ne 0) {
        throw "$Label allowed-artifact set spans more than one package"
    }
    Assert-UserInventory -Label "installed artifact $Label"
    $state = Get-PackageState -Package $package
    if (
        [string]::Join(',', $state.InstalledUserIds) -cne '0' -or
        $state.Paths.Count -ne 1 -or
        -not $state.GlobalKnown
    ) {
        throw "$Label must be installed only for user 0 with one exact base APK"
    }
    $pulled = Join-Path $script:TemporaryRoot ("installed-" + [Guid]::NewGuid().ToString('N') + '.apk')
    $null = Invoke-ExactAdb -Arguments @('pull', $state.Paths[0], $pulled) -Label "pull $Label"
    try {
        $pulledSha256 = Get-Sha256 -Path $pulled
        $matching = @($Artifacts | Where-Object { $_.Sha256 -ceq $pulledSha256 })
        if ($matching.Count -ne 1) {
            throw "$Label installed bytes are not one exact allowed readonly snapshot"
        }
        $matched = $matching[0]
        Assert-Apk -Label "$Label installed artifact" -Path $pulled -Sha256 $matched.Sha256 `
            -Package $matched.Package -Signer $matched.Signer -AbiMode $matched.AbiMode
        return $matched
    } finally {
        Remove-Item -LiteralPath $pulled -Force -ErrorAction SilentlyContinue
    }
}

function Remove-RunOwnedPackage {
    param([Parameter(Mandatory)] [string] $Package)

    if (-not $script:PackageOwnership.ContainsKey($Package)) {
        throw "Refusing to uninstall package without run-owned state: $Package"
    }
    Assert-UserInventory -Label "before removing run-owned $Package"
    $state = Get-PackageState -Package $Package
    if (-not $state.Installed) {
        if ($state.RetainedForAnyUser -or $state.GlobalKnown) {
            throw "Run-owned package is absent but retains a package-manager record: $Package"
        }
        $script:PackageOwnership.Remove($Package)
        return
    }
    $ownership = $script:PackageOwnership[$Package]
    $null = Assert-InstalledArtifactSet -Artifacts @($ownership.AllowedArtifacts) -Label "run-owned $Package"
    Assert-UserInventory -Label "immediately before uninstall $Package"
    $result = Invoke-ExactAdb -Arguments @('uninstall', $Package) -Label "uninstall run-owned $Package" -AllowNonZero
    if ($result.ExitCode -ne 0 -or $result.Text -notmatch '(?m)^Success\s*$') {
        throw "Unable to remove run-owned package $Package`n$($result.Text)"
    }
    Assert-PackageAbsent -Package $Package -Label "post-uninstall $Package"
    $script:PackageOwnership.Remove($Package)
}

function Invoke-OneInstrumentation {
    param(
        [Parameter(Mandatory)] [string] $Runner,
        [Parameter(Mandatory)] [string] $Test,
        [hashtable] $Extras = @{}
    )

    Assert-UserInventory -Label "before $Test"
    $arguments = @('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', $Test)
    foreach ($key in @($Extras.Keys | Sort-Object)) {
        $arguments += @('-e', $key, [string] $Extras[$key])
    }
    $arguments += $Runner
    $result = Invoke-ExactAdb -Arguments $arguments -Label "instrumentation $Test" -AllowNonZero
    if (
        $result.ExitCode -ne 0 -or
        $result.Text -notmatch 'OK \(1 test\)' -or
        $result.Text -notmatch 'INSTRUMENTATION_CODE:\s*-1' -or
        $result.Text -match '(?i)FAILURES!!!|Process crashed|INSTRUMENTATION_FAILED'
    ) {
        throw "Instrumentation did not pass exactly one test: $Test`n$($result.Text)"
    }
    Assert-UserInventory -Label "after $Test"
    $script:Observations.Add([ordered]@{ test = $Test; result = 'OK_1_TEST_ZERO_SKIP' })
}

function Invoke-Availability {
    param([Parameter(Mandatory)] [ValidateSet('MISSING', 'DISABLED', 'AVAILABLE')] [string] $Expected)
    Invoke-OneInstrumentation -Runner $hostRunner -Test $availabilityTest -Extras @{
        'autojs.python.r6.providerAvailability.expected' = $Expected
    }
}

$resolvedAdb = Resolve-ExactFile -Path $AdbPath -Label 'AdbPath'
$resolvedAapt2 = Resolve-ExactFile -Path $Aapt2Path -Label 'Aapt2Path'
$resolvedApkSigner = Resolve-ExactFile -Path $ApkSignerPath -Label 'ApkSignerPath'
$resolvedProvenance = Resolve-ExactFile -Path $Provenance -Label 'Provenance'
if ((Get-Sha256 -Path $resolvedProvenance) -cne $ProvenanceSha256.ToLowerInvariant()) {
    throw 'P2 provenance SHA-256 differs from the exact argument'
}

$script:Adb = $resolvedAdb
$script:Aapt2 = $resolvedAapt2
$script:ApkSigner = $resolvedApkSigner
$script:FrozenUsers = @($ExpectedUserIds | Sort-Object -Unique)
if ($script:FrozenUsers.Count -ne $ExpectedUserIds.Count -or 0 -notin $script:FrozenUsers) {
    throw 'ExpectedUserIds must be duplicate-free and include user 0'
}
if ($CandidateSignerSha256 -ieq $DifferentSignerSha256) {
    throw 'DifferentSignerSha256 must differ from CandidateSignerSha256'
}

$artifactInputs = @(
    [pscustomobject]@{ Label='host'; Path=(Resolve-ExactFile $HostApk 'HostApk'); Sha256=$HostSha256; Package=$hostPackage; Signer=$CandidateSignerSha256; AbiMode='EXACT'; FileName='host.apk' },
    [pscustomobject]@{ Label='host androidTest'; Path=(Resolve-ExactFile $HostTestApk 'HostTestApk'); Sha256=$HostTestSha256; Package=$hostTestPackage; Signer=$CandidateSignerSha256; AbiMode='NONE'; FileName='host-test.apk' },
    [pscustomobject]@{ Label='baseline plugin'; Path=(Resolve-ExactFile $BaselinePluginApk 'BaselinePluginApk'); Sha256=$BaselinePluginSha256; Package=$pluginPackage; Signer=$CandidateSignerSha256; AbiMode='EXACT'; FileName='baseline-plugin.apk' },
    [pscustomobject]@{ Label='candidate plugin'; Path=(Resolve-ExactFile $CandidatePluginApk 'CandidatePluginApk'); Sha256=$CandidatePluginSha256; Package=$pluginPackage; Signer=$CandidateSignerSha256; AbiMode='EXACT'; FileName='candidate-plugin.apk' },
    [pscustomobject]@{ Label='same-signer consumer'; Path=(Resolve-ExactFile $ConsumerApk 'ConsumerApk'); Sha256=$ConsumerSha256; Package=$consumerPackage; Signer=$CandidateSignerSha256; AbiMode='NONE'; FileName='consumer.apk' },
    [pscustomobject]@{ Label='same-signer consumer androidTest'; Path=(Resolve-ExactFile $ConsumerTestApk 'ConsumerTestApk'); Sha256=$ConsumerTestSha256; Package=$consumerTestPackage; Signer=$CandidateSignerSha256; AbiMode='NONE'; FileName='consumer-test.apk' },
    [pscustomobject]@{ Label='different-signer consumer'; Path=(Resolve-ExactFile $DifferentSignerConsumerApk 'DifferentSignerConsumerApk'); Sha256=$DifferentSignerConsumerSha256; Package=$consumerPackage; Signer=$DifferentSignerSha256; AbiMode='NONE'; FileName='different-consumer.apk' },
    [pscustomobject]@{ Label='different-signer consumer androidTest'; Path=(Resolve-ExactFile $DifferentSignerConsumerTestApk 'DifferentSignerConsumerTestApk'); Sha256=$DifferentSignerConsumerTestSha256; Package=$consumerTestPackage; Signer=$DifferentSignerSha256; AbiMode='NONE'; FileName='different-consumer-test.apk' }
)

$temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) ("autojs6-python-r6-p3-" + [Guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $temporaryRoot -ErrorAction Stop
$script:TemporaryRoot = (Resolve-Path -LiteralPath $temporaryRoot).ProviderPath
$serialDigest = [Convert]::ToHexString(
    [Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($Serial))
).ToLowerInvariant()
$mutex = [Threading.Mutex]::new($false, "Global\AutoJs6PythonR6P3_$serialDigest")
$mutexOwned = $false
$initialAbsenceVerified = $false
$runError = $null
$restorationErrors = [Collections.Generic.List[string]]::new()
$snapshots = @()
$script:Observations = [Collections.Generic.List[object]]::new()
$script:PackageOwnership = @{}
$packages = @($hostPackage, $pluginPackage, $hostTestPackage, $consumerPackage, $consumerTestPackage)

try {
    try {
        $mutexOwned = $mutex.WaitOne(0)
    } catch [Threading.AbandonedMutexException] {
        $mutexOwned = $true
    }
    if (-not $mutexOwned) { throw "Another R6-P3 runner owns serial $Serial" }

    $snapshots = @($artifactInputs | ForEach-Object { New-ReadonlySnapshot -Artifact $_ -Directory $temporaryRoot })
    $artifactByLabel = @{}
    foreach ($artifact in $snapshots) { $artifactByLabel[$artifact.Label] = $artifact }
    $baselineVersionCode = Get-ApkVersionCode -Path $artifactByLabel['baseline plugin'].Path
    $candidateVersionCode = Get-ApkVersionCode -Path $artifactByLabel['candidate plugin'].Path
    if ($candidateVersionCode -le $baselineVersionCode) {
        throw "Candidate plugin versionCode must exceed baseline: baseline=$baselineVersionCode candidate=$candidateVersionCode"
    }

    $serialState = Invoke-ExactAdb -Arguments @('get-state') -Label 'exact device state'
    if ($serialState.Text.Trim() -cne 'device') { throw "Exact serial $Serial is not in device state" }
    $actualApi = [int](Invoke-ExactAdb -Arguments @('shell', 'getprop', 'ro.build.version.sdk') -Label 'device API').Text.Trim()
    $actualAbi = (Invoke-ExactAdb -Arguments @('shell', 'getprop', 'ro.product.cpu.abi') -Label 'device ABI').Text.Trim()
    if ($actualApi -ne $ExpectedApi) { throw "Device API mismatch: expected=$ExpectedApi actual=$actualApi" }
    if ($actualAbi -cne $ExpectedAbi) { throw "Device ABI mismatch: expected=$ExpectedAbi actual=$actualAbi" }
    Assert-UserInventory -Label 'initial device preflight'
    foreach ($package in $packages) { Assert-PackageAbsent -Package $package -Label 'initial package preflight' }
    $initialAbsenceVerified = $true

    Install-ExactApk -Artifact $artifactByLabel['host']
    Install-ExactApk -Artifact $artifactByLabel['baseline plugin']
    Assert-InstalledArtifact -Artifact $artifactByLabel['baseline plugin']
    Install-ExactApk -Artifact $artifactByLabel['host androidTest']
    Install-ExactApk -Artifact $artifactByLabel['same-signer consumer']
    Install-ExactApk -Artifact $artifactByLabel['same-signer consumer androidTest']

    Invoke-Availability -Expected 'AVAILABLE'
    $script:Observations.Add([ordered]@{
        phase = 'BASELINE_INSTALL'; versionCode = $baselineVersionCode; result = 'AVAILABLE'
    })
    Install-ExactApk -Artifact $artifactByLabel['candidate plugin'] -Replace `
        -ExpectedCurrentArtifact $artifactByLabel['baseline plugin']
    Assert-InstalledArtifact -Artifact $artifactByLabel['candidate plugin']
    Invoke-Availability -Expected 'AVAILABLE'
    $script:Observations.Add([ordered]@{
        phase = 'BASELINE_TO_CANDIDATE_UPGRADE'; versionCode = $candidateVersionCode; result = 'AVAILABLE'
    })

    Assert-InstalledArtifact -Artifact $artifactByLabel['candidate plugin']
    $disable = Invoke-ExactAdb -Arguments @('shell', 'pm', 'disable-user', '--user', '0', $pluginPackage) `
        -Label 'disable exact plugin' -AllowNonZero
    if ($disable.ExitCode -ne 0) { throw "Unable to disable exact plugin`n$($disable.Text)" }
    Invoke-Availability -Expected 'DISABLED'
    Assert-InstalledArtifact -Artifact $artifactByLabel['candidate plugin']
    $enable = Invoke-ExactAdb -Arguments @('shell', 'pm', 'enable', '--user', '0', $pluginPackage) `
        -Label 'enable exact plugin' -AllowNonZero
    if ($enable.ExitCode -ne 0) { throw "Unable to re-enable exact plugin`n$($enable.Text)" }
    Invoke-Availability -Expected 'AVAILABLE'

    Remove-RunOwnedPackage -Package $pluginPackage
    Invoke-Availability -Expected 'MISSING'
    Install-ExactApk -Artifact $artifactByLabel['candidate plugin']
    Assert-InstalledArtifact -Artifact $artifactByLabel['candidate plugin']
    Invoke-Availability -Expected 'AVAILABLE'

    Invoke-OneInstrumentation -Runner $hostRunner -Test $cancelRebindTest -Extras @{
        'autojs.python.r2.cancelRebind.enabled' = 'true'
    }
    Invoke-OneInstrumentation -Runner $hostRunner -Test $timeoutRebindTest -Extras @{
        'autojs.python.r2.timeoutRebind.enabled' = 'true'
    }
    Invoke-OneInstrumentation -Runner $hostRunner -Test $relayTest -Extras @{
        'autojs.python.r6.sessionRelay.enabled' = 'true'
    }
    Invoke-OneInstrumentation -Runner $consumerRunner -Test $sameSignerTest

    Remove-RunOwnedPackage -Package $consumerTestPackage
    Remove-RunOwnedPackage -Package $consumerPackage
    Install-ExactApk -Artifact $artifactByLabel['different-signer consumer']
    Install-ExactApk -Artifact $artifactByLabel['different-signer consumer androidTest']
    Invoke-OneInstrumentation -Runner $consumerRunner -Test $differentSignerTest

    Remove-RunOwnedPackage -Package $pluginPackage
    Install-ExactApk -Artifact $artifactByLabel['baseline plugin']
    Assert-InstalledArtifact -Artifact $artifactByLabel['baseline plugin']
    Invoke-Availability -Expected 'AVAILABLE'
    $script:Observations.Add([ordered]@{
        phase = 'CANDIDATE_UNINSTALL_BASELINE_REINSTALL_ROLLBACK'
        versionCode = $baselineVersionCode
        result = 'AVAILABLE_WITH_FRESH_APP_DATA'
    })
} catch {
    $runError = $_
} finally {
    try {
        if ($initialAbsenceVerified) {
            foreach ($package in @($consumerTestPackage, $consumerPackage, $hostTestPackage, $pluginPackage, $hostPackage)) {
                if ($script:PackageOwnership.ContainsKey($package)) {
                    try {
                        Remove-RunOwnedPackage -Package $package
                    } catch {
                        $restorationErrors.Add($_.Exception.Message)
                    }
                }
            }
            try { Assert-UserInventory -Label 'final restoration' } catch {
                $restorationErrors.Add($_.Exception.Message)
            }
            foreach ($package in $packages) {
                try { Assert-PackageAbsent -Package $package -Label 'final restoration' } catch {
                    $restorationErrors.Add($_.Exception.Message)
                }
            }
        }
    } finally {
        if ($mutexOwned) { $mutex.ReleaseMutex() }
        $mutex.Dispose()
        if (Test-Path -LiteralPath $temporaryRoot) {
            $resolvedTemporaryRoot = (Resolve-Path -LiteralPath $temporaryRoot).ProviderPath
            $expectedTempParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
            if (-not $resolvedTemporaryRoot.StartsWith($expectedTempParent, [StringComparison]::OrdinalIgnoreCase)) {
                throw "Refusing to remove temporary directory outside the system temp root: $resolvedTemporaryRoot"
            }
            Remove-Item -LiteralPath $resolvedTemporaryRoot -Recurse -Force
        }
    }
}

$status = if ($null -eq $runError -and $restorationErrors.Count -eq 0) {
    'PARTIAL_DIAGNOSTIC'
} else {
    'BLOCKED'
}
[ordered]@{
    schema = 1
    status = $status
    scope = 'R6_RELEASE_CANDIDATE_CONCENTRATED_MATRIX_WITH_FRESH_DATA_ROLLBACK_NO_FINAL_RECEIPT'
    exactSerial = $Serial
    expectedDevice = [ordered]@{ api = $ExpectedApi; abi = $ExpectedAbi; frozenUserIds = $script:FrozenUsers }
    provenance = [ordered]@{ path = $resolvedProvenance; sha256 = $ProvenanceSha256.ToLowerInvariant() }
    candidateSignerSha256 = $CandidateSignerSha256.ToLowerInvariant()
    differentSignerSha256 = $DifferentSignerSha256.ToLowerInvariant()
    pluginVersions = [ordered]@{
        baseline = if (Get-Variable -Name baselineVersionCode -ErrorAction SilentlyContinue) { $baselineVersionCode } else { $null }
        candidate = if (Get-Variable -Name candidateVersionCode -ErrorAction SilentlyContinue) { $candidateVersionCode } else { $null }
    }
    observations = @($script:Observations)
    packageRestoration = if ($restorationErrors.Count -eq 0 -and $initialAbsenceVerified) {
        'VERIFIED_ABSENT_ALL_FROZEN_USERS_AND_GLOBAL'
    } else {
        'NOT_VERIFIED'
    }
    blockedRequirements = @(
        'INDEPENDENT_FINAL_RECEIPT_VERIFIER',
        'DECLARED_ABI_PACKAGING_AND_ACTUAL_DEVICE_COVERAGE_AGGREGATION'
    )
    error = if ($null -ne $runError) { $runError.Exception.Message } else { $null }
    restorationErrors = @($restorationErrors)
    finalReceiptWritten = $false
    stableReleaseAuthorized = $false
} | ConvertTo-Json -Depth 8

if ($status -ceq 'BLOCKED') { exit 1 }
