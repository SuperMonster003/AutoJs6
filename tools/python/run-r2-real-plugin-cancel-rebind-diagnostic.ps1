#Requires -Version 7.0

[CmdletBinding()]
param(
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
    [string] $AdbPath,

    [Parameter(Mandatory)]
    [string] $Aapt2Path,

    [Parameter(Mandatory)]
    [string] $ApkSignerPath,

    [Parameter(Mandatory)]
    [switch] $ConfirmDeviceMutation
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false

# Narrow, reversible lifecycle diagnostic. It never writes a formal acceptance receipt.
$expectedSerial = 'QV710AF65F'
$expectedDeviceAbi = 'arm64-v8a'
$expectedSignerSha256 = '2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8'
$hostPackage = 'org.autojs.autojs6'
$testPackage = 'org.autojs.autojs6.test'
$pluginPackage = 'io.github.supermonster003.autojs6.plugin.python.runtime'
$runnerComponent = "$testPackage/androidx.test.runner.AndroidJUnitRunner"
$testClass = 'org.autojs.autojs.core.plugin.python.PythonRuntimeRealPluginCancelRebindDiagnosticTest'
$testMethod = 'cancelAfterStartedKillsOldBinderThenExactRebindRunsFiniteRequestOnce'
$optInArgument = 'autojs.python.r2.cancelRebind.enabled'

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
    return $item.FullName
}

function Invoke-NativeCapture {
    param(
        [Parameter(Mandatory)] [string] $Executable,
        [Parameter(Mandatory)] [string[]] $Arguments,
        [Parameter(Mandatory)] [string] $Label,
        [switch] $AllowNonZero
    )

    $nativeLines = @(& $Executable @Arguments 2>&1 | ForEach-Object { $_.ToString() })
    $exitCode = $LASTEXITCODE
    $text = [string]::Join([Environment]::NewLine, $nativeLines)
    if (-not $AllowNonZero -and $exitCode -ne 0) {
        throw "$Label failed with exit code $exitCode`n$text"
    }
    return [pscustomobject]@{
        ExitCode = $exitCode
        Lines = $nativeLines
        Text = $text
    }
}

$resolvedAdb = Resolve-ExactFile -Path $AdbPath -Label 'AdbPath'
$resolvedAapt2 = Resolve-ExactFile -Path $Aapt2Path -Label 'Aapt2Path'
$resolvedApkSigner = Resolve-ExactFile -Path $ApkSignerPath -Label 'ApkSignerPath'
$resolvedHostApk = Resolve-ExactFile -Path $HostApk -Label 'HostApk'
$resolvedTestApk = Resolve-ExactFile -Path $TestApk -Label 'TestApk'
$resolvedPluginApk = Resolve-ExactFile -Path $PluginApk -Label 'PluginApk'

$script:ExactSerial = $expectedSerial
$script:AdbExecutable = $resolvedAdb
$script:Aapt2Executable = $resolvedAapt2
$script:ApkSignerExecutable = $resolvedApkSigner

function Invoke-ExactAdb {
    param(
        [Parameter(Mandatory)] [string[]] $AdbArguments,
        [Parameter(Mandatory)] [string] $Label,
        [switch] $AllowNonZero
    )

    return Invoke-NativeCapture `
        -Executable $script:AdbExecutable `
        -Arguments (@('-s', $script:ExactSerial) + $AdbArguments) `
        -Label $Label `
        -AllowNonZero:$AllowNonZero
}

function Get-FileSha256 {
    param([Parameter(Mandatory)] [string] $Path)

    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
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

    $result = Get-ApkBadging -Path $Path
    $match = [regex]::Match($result.Text, "(?m)^package:\s+name='([^']+)'(?:\s|$)")
    if (-not $match.Success) {
        throw "Unable to read package name from APK: $Path"
    }
    return $match.Groups[1].Value
}

function Get-ApkNativeAbis {
    param([Parameter(Mandatory)] [string] $Path)

    $result = Get-ApkBadging -Path $Path
    $line = [regex]::Match($result.Text, '(?m)^native-code:\s+(.+)$')
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

function Assert-LocalArtifact {
    param(
        [Parameter(Mandatory)] [string] $Label,
        [Parameter(Mandatory)] [string] $Path,
        [Parameter(Mandatory)] [string] $ExpectedSha256,
        [Parameter(Mandatory)] [string] $ExpectedPackage,
        [Parameter(Mandatory)] [bool] $RequiresExactNativeAbi
    )

    if ([IO.Path]::GetExtension($Path) -ine '.apk') {
        throw "$Label must be an exact APK path"
    }
    $actualSha256 = Get-FileSha256 -Path $Path
    if ($actualSha256 -cne $ExpectedSha256.ToLowerInvariant()) {
        throw "$Label SHA-256 mismatch: expected $ExpectedSha256, actual $actualSha256"
    }
    $actualPackage = Get-ApkPackageName -Path $Path
    if ($actualPackage -cne $ExpectedPackage) {
        throw "$Label package mismatch: expected $ExpectedPackage, actual $actualPackage"
    }
    $signers = @(Get-ApkSignerDigests -Path $Path)
    if ($signers.Count -ne 1 -or $signers[0] -cne $expectedSignerSha256) {
        throw "$Label signer is not the pinned diagnostic signer"
    }
    $nativeAbis = @(Get-ApkNativeAbis -Path $Path)
    if ($RequiresExactNativeAbi) {
        if ($nativeAbis.Count -ne 1 -or $nativeAbis[0] -cne $expectedDeviceAbi) {
            throw "$Label must contain only exact ABI $expectedDeviceAbi; actual=$($nativeAbis -join ',')"
        }
    } elseif ($nativeAbis.Count -ne 0) {
        throw "$Label must be ABI-neutral; actual=$($nativeAbis -join ',')"
    }
    return [pscustomobject]@{
        Label = $Label
        Path = $Path
        Sha256 = $actualSha256
        PackageName = $actualPackage
        SignerSha256 = $signers[0]
        NativeAbis = $nativeAbis
    }
}

function New-ImmutableArtifactSnapshot {
    param(
        [Parameter(Mandatory)] [pscustomobject] $Artifact,
        [Parameter(Mandatory)] [string] $TemporaryDirectory,
        [Parameter(Mandatory)] [string] $FileName,
        [Parameter(Mandatory)] [string] $ExpectedSha256,
        [Parameter(Mandatory)] [string] $ExpectedPackage,
        [Parameter(Mandatory)] [bool] $RequiresExactNativeAbi
    )

    $snapshotPath = Join-Path $TemporaryDirectory $FileName
    Copy-Item -LiteralPath $Artifact.Path -Destination $snapshotPath -ErrorAction Stop
    $snapshotItem = Get-Item -LiteralPath $snapshotPath -Force
    $snapshotItem.IsReadOnly = $true
    return Assert-LocalArtifact `
        -Label "$($Artifact.Label) immutable snapshot" `
        -Path $snapshotItem.FullName `
        -ExpectedSha256 $ExpectedSha256 `
        -ExpectedPackage $ExpectedPackage `
        -RequiresExactNativeAbi $RequiresExactNativeAbi
}

function Get-DeviceProperty {
    param(
        [Parameter(Mandatory)] [string] $Property,
        [Parameter(Mandatory)] [string] $Label
    )

    $result = Invoke-ExactAdb -AdbArguments @('shell', 'getprop', $Property) -Label $Label
    $value = $result.Text.Trim()
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "$Label returned an empty value"
    }
    return $value
}

function Get-ExactDeviceUserIds {
    $result = Invoke-ExactAdb `
        -AdbArguments @('shell', 'pm', 'list', 'users') `
        -Label 'device user inventory'
    $userRecords = [regex]::Matches($result.Text, 'UserInfo\{')
    $userIdMatches = [regex]::Matches($result.Text, 'UserInfo\{([0-9]+):')
    if ($userIdMatches.Count -eq 0 -or $userIdMatches.Count -ne $userRecords.Count) {
        throw 'Unable to parse every non-negative Android user ID'
    }
    $userIds = @($userIdMatches | ForEach-Object { [int] $_.Groups[1].Value })
    $uniqueUserIds = @($userIds | Sort-Object -Unique)
    if ($uniqueUserIds.Count -ne $userIds.Count -or 0 -notin $uniqueUserIds) {
        throw "Android user inventory must contain unique non-negative IDs including user 0; actual=$($userIds -join ',')"
    }
    return @($uniqueUserIds)
}

function Get-UserInventoryKey {
    param([Parameter(Mandatory)] [int[]] $UserIds)

    return [string]::Join(',', @($UserIds | Sort-Object -Unique))
}

function Assert-DeviceUserInventoryUnchanged {
    param([Parameter(Mandatory)] [int[]] $FrozenUserIds)

    $currentUserIds = @(Get-ExactDeviceUserIds)
    if ((Get-UserInventoryKey -UserIds $currentUserIds) -cne (Get-UserInventoryKey -UserIds $FrozenUserIds)) {
        throw "Android user inventory changed during diagnostic; frozen=$($FrozenUserIds -join ',') current=$($currentUserIds -join ',')"
    }
}

function Get-PackageState {
    param(
        [Parameter(Mandatory)] [string] $PackageName,
        [Parameter(Mandatory)] [int[]] $UserIds
    )
    if ($UserIds.Count -eq 0 -or 0 -notin $UserIds) {
        throw 'Package-state query requires the frozen Android user inventory including user 0'
    }

    $userStates = [Collections.Generic.List[object]]::new()
    foreach ($userId in @($UserIds | Sort-Object -Unique)) {
        $pathResult = Invoke-ExactAdb `
            -AdbArguments @('shell', 'pm', 'path', '--user', "$userId", $PackageName) `
            -Label "package path query for $PackageName user $userId" `
            -AllowNonZero
        $missingWithEmptyOutput = $pathResult.ExitCode -eq 1 -and [string]::IsNullOrWhiteSpace($pathResult.Text)
        if ($pathResult.ExitCode -ne 0 -and -not $missingWithEmptyOutput) {
            throw "Unable to query package path for $PackageName user $userId`n$($pathResult.Text)"
        }
        $paths = @(
            $pathResult.Lines |
                ForEach-Object { $_.Trim() } |
                Where-Object { $_.StartsWith('package:', [StringComparison]::Ordinal) } |
                ForEach-Object { $_.Substring('package:'.Length) }
        )
        $listingResult = Invoke-ExactAdb `
            -AdbArguments @('shell', 'pm', 'list', 'packages', '--user', "$userId", $PackageName) `
            -Label "exact installed-package listing for $PackageName user $userId" `
            -AllowNonZero
        if ($listingResult.ExitCode -ne 0) {
            throw "Unable to query exact installed-package listing for $PackageName user $userId`n$($listingResult.Text)"
        }
        $listedCount = @(
            $listingResult.Lines |
                ForEach-Object { $_.Trim() } |
                Where-Object { $_ -ceq "package:$PackageName" }
        ).Count
        if ($listedCount -gt 1 -or (($paths.Count -gt 0) -ne ($listedCount -eq 1))) {
            throw "Per-user package state is inconsistent for $PackageName user $userId"
        }
        $userStates.Add([pscustomobject]@{
            UserId = [int] $userId
            Paths = $paths
            Listed = $listedCount -eq 1
        })
    }

    $knownResult = Invoke-ExactAdb `
        -AdbArguments @('shell', 'pm', 'list', 'packages', '-u', $PackageName) `
        -Label "global package-record query for $PackageName" `
        -AllowNonZero
    if ($knownResult.ExitCode -ne 0) {
        throw "Unable to query global package record for $PackageName`n$($knownResult.Text)"
    }
    $knownCount = @(
        $knownResult.Lines |
            ForEach-Object { $_.Trim() } |
            Where-Object { $_ -ceq "package:$PackageName" }
    ).Count
    if ($knownCount -gt 1) {
        throw "Duplicate package-manager records returned for $PackageName"
    }
    $installedForAnyUser = @($userStates | Where-Object { $_.Listed -or $_.Paths.Count -gt 0 }).Count -gt 0
    if ($installedForAnyUser -and $knownCount -ne 1) {
        throw "Package state is internally inconsistent for $PackageName"
    }
    return [pscustomobject]@{
        PackageName = $PackageName
        UserStates = $userStates.ToArray()
        InstalledForAnyUser = $installedForAnyUser
        GlobalRecordCount = $knownCount
        KnownToPackageManager = $knownCount -eq 1
    }
}

function Test-FullyAbsentPackageState {
    param([Parameter(Mandatory)] [pscustomobject] $State)

    return $State.GlobalRecordCount -eq 0 -and @(
        $State.UserStates | Where-Object { $_.Listed -or $_.Paths.Count -gt 0 }
    ).Count -eq 0
}

function Assert-FullyAbsentPackageState {
    param(
        [Parameter(Mandatory)] [pscustomobject] $State,
        [Parameter(Mandatory)] [string] $Label
    )

    if (-not (Test-FullyAbsentPackageState -State $State)) {
        $users = @(
            $State.UserStates |
                Where-Object { $_.Listed -or $_.Paths.Count -gt 0 } |
                ForEach-Object { $_.UserId }
        )
        throw "$Label must be absent for every frozen user and have no retained global record; users=$($users -join ',') global=$($State.GlobalRecordCount)"
    }
}

function Assert-InstalledArtifact {
    param(
        [Parameter(Mandatory)] [pscustomobject] $Artifact,
        [Parameter(Mandatory)] [string] $TemporaryDirectory,
        [Parameter(Mandatory)] [int[]] $FrozenUserIds
    )

    Assert-DeviceUserInventoryUnchanged -FrozenUserIds $FrozenUserIds
    $state = Get-PackageState -PackageName $Artifact.PackageName -UserIds $FrozenUserIds
    $userZeroStates = @($state.UserStates | Where-Object { $_.UserId -eq 0 })
    $otherInstalledStates = @(
        $state.UserStates |
            Where-Object { $_.UserId -ne 0 -and ($_.Listed -or $_.Paths.Count -gt 0) }
    )
    if (
        $userZeroStates.Count -ne 1 -or
        -not $userZeroStates[0].Listed -or
        $userZeroStates[0].Paths.Count -ne 1 -or
        $otherInstalledStates.Count -ne 0 -or
        $state.GlobalRecordCount -ne 1
    ) {
        throw "Installed $($Artifact.Label) must be one exact user-0 APK with no installation in another frozen user"
    }
    $pulledPath = Join-Path $TemporaryDirectory "$($Artifact.PackageName)-installed.apk"
    if (Test-Path -LiteralPath $pulledPath) {
        Remove-Item -LiteralPath $pulledPath -Force
    }
    $null = Invoke-ExactAdb `
        -AdbArguments @('pull', $userZeroStates[0].Paths[0], $pulledPath) `
        -Label "read installed APK for $($Artifact.Label)"
    if ((Get-FileSha256 -Path $pulledPath) -cne $Artifact.Sha256) {
        throw "Installed $($Artifact.Label) differs from the exact local APK"
    }
    if ((Get-ApkPackageName -Path $pulledPath) -cne $Artifact.PackageName) {
        throw "Installed $($Artifact.Label) package identity changed"
    }
    $installedSigners = @(Get-ApkSignerDigests -Path $pulledPath)
    if ($installedSigners.Count -ne 1 -or $installedSigners[0] -cne $expectedSignerSha256) {
        throw "Installed $($Artifact.Label) signer changed"
    }
}

function Install-ExactArtifact {
    param(
        [Parameter(Mandatory)] [pscustomobject] $Artifact,
        [Parameter(Mandatory)] [int[]] $FrozenUserIds,
        [Parameter(Mandatory)] [pscustomobject] $InitialState
    )

    Assert-DeviceUserInventoryUnchanged -FrozenUserIds $FrozenUserIds
    Assert-FullyAbsentPackageState -State $InitialState -Label "initial package state for $($Artifact.PackageName)"
    $currentState = Get-PackageState -PackageName $Artifact.PackageName -UserIds $FrozenUserIds
    Assert-FullyAbsentPackageState -State $currentState -Label "pre-install package state for $($Artifact.PackageName)"
    Assert-DeviceUserInventoryUnchanged -FrozenUserIds $FrozenUserIds
    $result = Invoke-ExactAdb `
        -AdbArguments @('install', '--user', '0', '-t', $Artifact.Path) `
        -Label "temporary non-replacing install for $($Artifact.PackageName)" `
        -AllowNonZero
    if ($result.ExitCode -ne 0 -or $result.Text -notmatch '(?im)^Success\s*$') {
        throw "Temporary install failed for $($Artifact.PackageName)`n$($result.Text)"
    }
}

function Remove-OwnedArtifact {
    param(
        [Parameter(Mandatory)] [pscustomobject] $Artifact,
        [Parameter(Mandatory)] [string] $TemporaryDirectory,
        [Parameter(Mandatory)] [int[]] $FrozenUserIds,
        [Parameter(Mandatory)] [pscustomobject] $InitialState
    )

    Assert-DeviceUserInventoryUnchanged -FrozenUserIds $FrozenUserIds
    Assert-FullyAbsentPackageState -State $InitialState -Label "frozen initial package state for $($Artifact.PackageName)"
    $state = Get-PackageState -PackageName $Artifact.PackageName -UserIds $FrozenUserIds
    if (Test-FullyAbsentPackageState -State $state) {
        return
    }
    Assert-InstalledArtifact `
        -Artifact $Artifact `
        -TemporaryDirectory $TemporaryDirectory `
        -FrozenUserIds $FrozenUserIds
    Assert-DeviceUserInventoryUnchanged -FrozenUserIds $FrozenUserIds
    $result = Invoke-ExactAdb `
        -AdbArguments @('uninstall', $Artifact.PackageName) `
        -Label "restore absent package state for $($Artifact.PackageName)" `
        -AllowNonZero
    if ($result.ExitCode -ne 0 -or $result.Text -notmatch '(?im)^Success\s*$') {
        throw "Unable to remove run-owned package $($Artifact.PackageName)`n$($result.Text)"
    }
    $restored = Get-PackageState -PackageName $Artifact.PackageName -UserIds $FrozenUserIds
    Assert-FullyAbsentPackageState -State $restored -Label "restored package state for $($Artifact.PackageName)"
}

function Invoke-ExactInstrumentationMethod {
    $selector = "$testClass#$testMethod"
    $result = Invoke-ExactAdb `
        -AdbArguments @(
            'shell', 'am', 'instrument', '--user', '0', '-w', '-r',
            '-e', 'class', $selector,
            '-e', $optInArgument, 'true',
            $runnerComponent
        ) `
        -Label "instrumentation $selector" `
        -AllowNonZero

    $skipMarkers = @(
        'INSTRUMENTATION_STATUS_CODE:\s*-3(?:\s|$)',
        'AssumptionViolatedException',
        '(?im)^INSTRUMENTATION_STATUS:\s+(?:ignored|skipped)=',
        '(?im)\b(?:ignored|skipped)\s*[:=]\s*[1-9][0-9]*\b'
    )
    $hasSkip = @($skipMarkers | Where-Object { $result.Text -match $_ })
    $okCount = [regex]::Matches($result.Text, '(?m)^\s*OK \(1 test\)\s*$').Count
    $numTests = @(
        [regex]::Matches($result.Text, '(?m)^INSTRUMENTATION_STATUS:\s*numtests=(\d+)\s*$') |
            ForEach-Object { [int] $_.Groups[1].Value }
    )
    $terminalStatusCount = [regex]::Matches($result.Text, '(?m)^INSTRUMENTATION_STATUS_CODE:\s*0\s*$').Count
    $hasFailure = $result.Text -match '(?m)^(?:FAILURES!!!|INSTRUMENTATION_FAILED:)' -or
        $result.Text -match '(?m)^INSTRUMENTATION_STATUS_CODE:\s*-[124-9][0-9]*\s*$'
    $hasSuccessCode = $result.Text -match '(?m)^INSTRUMENTATION_CODE:\s*-1\s*$'
    if (
        $result.ExitCode -ne 0 -or
        $okCount -ne 1 -or
        $numTests.Count -eq 0 -or
        @($numTests | Where-Object { $_ -ne 1 }).Count -ne 0 -or
        $terminalStatusCount -ne 1 -or
        $hasSkip.Count -ne 0 -or
        $hasFailure -or
        -not $hasSuccessCode
    ) {
        throw "Instrumentation did not prove exactly OK (1 test), zero skips: $selector`n$($result.Text)"
    }
    return [pscustomobject]@{
        Method = $testMethod
        Observation = 'OK_1_TEST_ZERO_SKIP'
    }
}

if (-not $ConfirmDeviceMutation.IsPresent) {
    throw 'Pass -ConfirmDeviceMutation explicitly; temporary package installation is otherwise forbidden'
}
if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SERIAL) -and $env:ANDROID_SERIAL -cne $expectedSerial) {
    throw 'ANDROID_SERIAL differs from the fixed exact serial; refusing ambiguous device scope'
}

$sourceHostArtifact = Assert-LocalArtifact `
    -Label 'host target APK' `
    -Path $resolvedHostApk `
    -ExpectedSha256 $HostSha256 `
    -ExpectedPackage $hostPackage `
    -RequiresExactNativeAbi $true
$sourceTestArtifact = Assert-LocalArtifact `
    -Label 'host androidTest APK' `
    -Path $resolvedTestApk `
    -ExpectedSha256 $TestSha256 `
    -ExpectedPackage $testPackage `
    -RequiresExactNativeAbi $false
$sourcePluginArtifact = Assert-LocalArtifact `
    -Label 'real Python Runtime plugin APK' `
    -Path $resolvedPluginApk `
    -ExpectedSha256 $PluginSha256 `
    -ExpectedPackage $pluginPackage `
    -RequiresExactNativeAbi $true

$deviceState = Invoke-ExactAdb -AdbArguments @('get-state') -Label 'exact device state'
if ($deviceState.Text.Trim() -cne 'device') {
    throw "Exact serial is not in device state: $($deviceState.Text.Trim())"
}
$frozenUserIds = @(Get-ExactDeviceUserIds)
$deviceApiText = Get-DeviceProperty -Property 'ro.build.version.sdk' -Label 'device API query'
$deviceApi = 0
if (-not [int]::TryParse($deviceApiText, [ref] $deviceApi) -or $deviceApi -lt 24) {
    throw "Unsupported device API: $deviceApiText"
}
$deviceAbi = Get-DeviceProperty -Property 'ro.product.cpu.abi' -Label 'device ABI query'
if ($deviceAbi -cne $expectedDeviceAbi) {
    throw "Exact device ABI mismatch: expected $expectedDeviceAbi, actual $deviceAbi"
}
$deviceFingerprint = Get-DeviceProperty -Property 'ro.build.fingerprint' -Label 'device fingerprint query'

$initialHost = Get-PackageState -PackageName $hostPackage -UserIds $frozenUserIds
$initialPlugin = Get-PackageState -PackageName $pluginPackage -UserIds $frozenUserIds
$initialTest = Get-PackageState -PackageName $testPackage -UserIds $frozenUserIds
Assert-FullyAbsentPackageState -State $initialHost -Label 'initial host target package'
Assert-FullyAbsentPackageState -State $initialPlugin -Label 'initial real Python Runtime plugin package'
Assert-FullyAbsentPackageState -State $initialTest -Label 'initial host androidTest package'
Assert-DeviceUserInventoryUnchanged -FrozenUserIds $frozenUserIds

$temporaryRoot = Join-Path `
    ([IO.Path]::GetTempPath()) `
    ("autojs6-python-r2-cancel-rebind-" + [Guid]::NewGuid().ToString('N'))
[void] (New-Item -ItemType Directory -Path $temporaryRoot)

$installedByThisRun = [ordered]@{
    host = $false
    plugin = $false
    test = $false
}
$primaryFailure = $null
$restoreFailures = [Collections.Generic.List[string]]::new()
$observation = $null
$hostArtifact = $null
$pluginArtifact = $null
$testArtifact = $null

try {
    $hostArtifact = New-ImmutableArtifactSnapshot `
        -Artifact $sourceHostArtifact `
        -TemporaryDirectory $temporaryRoot `
        -FileName 'host-target.apk' `
        -ExpectedSha256 $HostSha256 `
        -ExpectedPackage $hostPackage `
        -RequiresExactNativeAbi $true
    $pluginArtifact = New-ImmutableArtifactSnapshot `
        -Artifact $sourcePluginArtifact `
        -TemporaryDirectory $temporaryRoot `
        -FileName 'python-runtime-plugin.apk' `
        -ExpectedSha256 $PluginSha256 `
        -ExpectedPackage $pluginPackage `
        -RequiresExactNativeAbi $true
    $testArtifact = New-ImmutableArtifactSnapshot `
        -Artifact $sourceTestArtifact `
        -TemporaryDirectory $temporaryRoot `
        -FileName 'host-android-test.apk' `
        -ExpectedSha256 $TestSha256 `
        -ExpectedPackage $testPackage `
        -RequiresExactNativeAbi $false

    foreach ($artifact in @($hostArtifact, $pluginArtifact, $testArtifact)) {
        $preMutationState = Get-PackageState -PackageName $artifact.PackageName -UserIds $frozenUserIds
        Assert-FullyAbsentPackageState -State $preMutationState -Label "pre-mutation package state for $($artifact.PackageName)"
    }
    Assert-DeviceUserInventoryUnchanged -FrozenUserIds $frozenUserIds

    $installedByThisRun.host = $true
    Install-ExactArtifact -Artifact $hostArtifact `
        -FrozenUserIds $frozenUserIds `
        -InitialState $initialHost
    Assert-InstalledArtifact `
        -Artifact $hostArtifact `
        -TemporaryDirectory $temporaryRoot `
        -FrozenUserIds $frozenUserIds

    $installedByThisRun.plugin = $true
    Install-ExactArtifact -Artifact $pluginArtifact `
        -FrozenUserIds $frozenUserIds `
        -InitialState $initialPlugin
    Assert-InstalledArtifact `
        -Artifact $pluginArtifact `
        -TemporaryDirectory $temporaryRoot `
        -FrozenUserIds $frozenUserIds

    $installedByThisRun.test = $true
    Install-ExactArtifact -Artifact $testArtifact `
        -FrozenUserIds $frozenUserIds `
        -InitialState $initialTest
    Assert-InstalledArtifact `
        -Artifact $testArtifact `
        -TemporaryDirectory $temporaryRoot `
        -FrozenUserIds $frozenUserIds

    $observation = Invoke-ExactInstrumentationMethod
} catch {
    $primaryFailure = $_.Exception
} finally {
    if ($installedByThisRun.test) {
        try {
            Remove-OwnedArtifact -Artifact $testArtifact `
                -TemporaryDirectory $temporaryRoot `
                -FrozenUserIds $frozenUserIds `
                -InitialState $initialTest
        } catch {
            $restoreFailures.Add($_.Exception.Message)
        }
    }
    if ($installedByThisRun.plugin) {
        try {
            Remove-OwnedArtifact -Artifact $pluginArtifact `
                -TemporaryDirectory $temporaryRoot `
                -FrozenUserIds $frozenUserIds `
                -InitialState $initialPlugin
        } catch {
            $restoreFailures.Add($_.Exception.Message)
        }
    }
    if ($installedByThisRun.host) {
        try {
            Remove-OwnedArtifact -Artifact $hostArtifact `
                -TemporaryDirectory $temporaryRoot `
                -FrozenUserIds $frozenUserIds `
                -InitialState $initialHost
        } catch {
            $restoreFailures.Add($_.Exception.Message)
        }
    }

    try {
        Assert-DeviceUserInventoryUnchanged -FrozenUserIds $frozenUserIds
        foreach ($packageName in @($hostPackage, $pluginPackage, $testPackage)) {
            $restored = Get-PackageState -PackageName $packageName -UserIds $frozenUserIds
            Assert-FullyAbsentPackageState -State $restored -Label "final restored state for $packageName"
        }
    } catch {
        $restoreFailures.Add($_.Exception.Message)
    }

    try {
        if (Test-Path -LiteralPath $temporaryRoot -PathType Container) {
            Get-ChildItem -LiteralPath $temporaryRoot -File -Force |
                ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force }
            Remove-Item -LiteralPath $temporaryRoot -Force
        }
    } catch {
        $restoreFailures.Add("Local diagnostic temporary-file cleanup failed: $($_.Exception.Message)")
    }
}

if ($restoreFailures.Count -gt 0) {
    $message = "Device package restoration verification failed:`n" +
        [string]::Join("`n", @($restoreFailures | ForEach-Object { "- $_" }))
    if ($null -ne $primaryFailure) {
        $message += "`nOriginal diagnostic failure:`n$($primaryFailure.Message)"
    }
    throw $message
}
if ($null -ne $primaryFailure) {
    throw $primaryFailure
}

[ordered]@{
    schema = 1
    status = 'PARTIAL_DIAGNOSTIC'
    scope = 'R2_REAL_CPYTHON_CANCEL_DEATH_REBIND_SINGLE_CASE_DIAGNOSTIC_ONLY'
    exactSerial = $expectedSerial
    device = [ordered]@{
        api = $deviceApi
        abi = $deviceAbi
        fingerprint = $deviceFingerprint
        frozenUserIds = $frozenUserIds
    }
    artifacts = @(
        [ordered]@{ packageName = $hostPackage; sha256 = $hostArtifact.Sha256; signerSha256 = $expectedSignerSha256 },
        [ordered]@{ packageName = $testPackage; sha256 = $testArtifact.Sha256; signerSha256 = $expectedSignerSha256 },
        [ordered]@{ packageName = $pluginPackage; sha256 = $pluginArtifact.Sha256; signerSha256 = $expectedSignerSha256 }
    )
    initialPackageState = [ordered]@{
        host = 'ABSENT_REQUIRED_ALL_USERS_AND_GLOBAL'
        androidTest = 'ABSENT_REQUIRED_ALL_USERS_AND_GLOBAL'
        realPlugin = 'ABSENT_REQUIRED_ALL_USERS_AND_GLOBAL'
    }
    observation = $observation
    covered = @(
        'TYPED_REQUESTED_CANCELLATION_AFTER_STARTED',
        'OLD_PROVIDER_BINDER_DEATH',
        'ACTION_NULL_EXACT_REBIND',
        'RUNTIME_GENERATION_CHANGED',
        'DISTINCT_FINITE_REQUEST_SINGLE_OPEN_START_OUTPUT_RESULT',
        'HARNESS_DOES_NOT_REPLAY_DISPATCHED_REQUEST_ID',
        'CALLBACK_UID_AT_EVERY_AIDL_ENTRY',
        'HOST_PFD_CLOSURE'
    )
    notCovered = @(
        'PRODUCTION_HOST_AUTOMATIC_REPLAY_POLICY',
        'REPEATED_CANCEL_DEATH_REBIND_STRESS',
        'TIMEOUT',
        'HOSTILE_CALLER',
        'UPDATE_OR_UNINSTALL_RACE',
        'FULL_FD_LEAK_INSTRUMENTATION'
    )
    packageInstallationRestoration = 'VERIFIED_ABSENT_ALL_FROZEN_USERS_AND_GLOBAL'
    appDataSnapshotTaken = $false
    acceptanceReceiptWritten = $false
    r2DeviceAcceptance = $false
} | ConvertTo-Json -Depth 7
