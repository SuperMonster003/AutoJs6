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

# One narrow real-runtime reliable-pipe/rebind diagnostic. No acceptance receipt is written.
$expectedSerial = 'QV710AF65F'
$expectedDeviceAbi = 'arm64-v8a'
$expectedSignerSha256 = '2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8'
$hostPackage = 'org.autojs.autojs6'
$testPackage = 'org.autojs.autojs6.test'
$pluginPackage = 'io.github.supermonster003.autojs6.plugin.python.runtime'
$runnerComponent = "$testPackage/androidx.test.runner.AndroidJUnitRunner"
$testClass = 'org.autojs.autojs.core.plugin.python.PythonRuntimeRealPluginReliablePipeDiagnosticTest'
$testMethod = 'producerErrorRejectsBeforeCpythonThenExactRebindExecutesFiniteSource'
$optInArgument = 'autojs.python.r2.reliablePipe.enabled'

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

function Get-SignerKey {
    param([Parameter(Mandatory)] [string[]] $Digests)

    return [string]::Join(',', @($Digests | Sort-Object -Unique))
}

function Get-AbiKey {
    param([Parameter()] [AllowEmptyCollection()] [string[]] $Abis = @())

    return [string]::Join(',', @($Abis | Sort-Object -Unique))
}

function Assert-LocalArtifact {
    param(
        [Parameter(Mandatory)] [string] $Label,
        [Parameter(Mandatory)] [string] $Path,
        [Parameter(Mandatory)] [string] $ExpectedSha256,
        [Parameter(Mandatory)] [string] $ExpectedPackage,
        [Parameter()] [AllowEmptyCollection()] [string[]] $ExpectedAbis = @()
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
    $signerKey = Get-SignerKey -Digests $signers
    if ($signerKey -cne $expectedSignerSha256) {
        throw "$Label signer mismatch: expected $expectedSignerSha256, actual $signerKey"
    }
    $actualAbis = @(Get-ApkNativeAbis -Path $Path)
    if ((Get-AbiKey -Abis $actualAbis) -cne (Get-AbiKey -Abis $ExpectedAbis)) {
        throw "$Label ABI mismatch: expected=$($ExpectedAbis -join ','), actual=$($actualAbis -join ',')"
    }
    return [pscustomobject]@{
        Label = $Label
        Path = $Path
        Sha256 = $actualSha256
        PackageName = $actualPackage
        SignerKey = $signerKey
        Abis = $actualAbis
        AbiKey = Get-AbiKey -Abis $actualAbis
    }
}

function New-ImmutableArtifactSnapshot {
    param(
        [Parameter(Mandatory)] [pscustomobject] $Artifact,
        [Parameter(Mandatory)] [string] $TemporaryDirectory,
        [Parameter(Mandatory)] [string] $FileName,
        [Parameter(Mandatory)] [string] $ExpectedSha256,
        [Parameter(Mandatory)] [string] $ExpectedPackage,
        [Parameter()] [AllowEmptyCollection()] [string[]] $ExpectedAbis = @()
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
        -ExpectedAbis $ExpectedAbis
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

function Get-DeviceUserIds {
    $result = Invoke-ExactAdb `
        -AdbArguments @('shell', 'pm', 'list', 'users') `
        -Label 'device user inventory'
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

function Assert-DeviceUserSetUnchanged {
    param([Parameter(Mandatory)] [int[]] $ExpectedUserIds)

    $currentUserIds = @(Get-DeviceUserIds)
    if (
        [string]::Join(',', $currentUserIds) -cne
        [string]::Join(',', @($ExpectedUserIds | Sort-Object -Unique))
    ) {
        throw "Android user set changed during diagnostic: expected=$($ExpectedUserIds -join ',') actual=$($currentUserIds -join ',')"
    }
}

function Get-PackageState {
    param(
        [Parameter(Mandatory)] [string] $PackageName,
        [Parameter(Mandatory)] [int[]] $UserIds
    )

    $userStates = @(
        foreach ($userId in $UserIds) {
            $pathResult = Invoke-ExactAdb `
                -AdbArguments @('shell', 'pm', 'path', '--user', $userId.ToString(), $PackageName) `
                -Label "package path query for $PackageName user $userId" `
                -AllowNonZero
            $missingWithEmptyOutput = $pathResult.ExitCode -eq 1 -and [string]::IsNullOrWhiteSpace($pathResult.Text)
            if ($pathResult.ExitCode -ne 0 -and -not $missingWithEmptyOutput) {
                throw "Unable to query package path for $PackageName user $userId`n$($pathResult.Text)"
            }
            $pathLines = @(
                $pathResult.Lines |
                    ForEach-Object { $_.Trim() } |
                    Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
            )
            $paths = @(
                $pathLines |
                    Where-Object { $_.StartsWith('package:', [StringComparison]::Ordinal) } |
                    ForEach-Object { $_.Substring('package:'.Length) }
            )
            if ($pathLines.Count -ne $paths.Count) {
                throw "Unexpected package path output for $PackageName user $userId`n$($pathResult.Text)"
            }
            $knownResult = Invoke-ExactAdb `
                -AdbArguments @(
                    'shell', 'pm', 'list', 'packages', '--user', $userId.ToString(), '-u', $PackageName
                ) `
                -Label "exact retained package query for $PackageName user $userId" `
                -AllowNonZero
            if ($knownResult.ExitCode -ne 0) {
                throw "Unable to query retained package record for $PackageName user $userId`n$($knownResult.Text)"
            }
            $knownLines = @(
                $knownResult.Lines |
                    ForEach-Object { $_.Trim() } |
                    Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
            )
            $malformedKnownLines = @(
                $knownLines |
                    Where-Object { $_ -notmatch '^package:[A-Za-z0-9_][A-Za-z0-9._]*$' }
            )
            $knownCount = @($knownLines | Where-Object { $_ -ceq "package:$PackageName" }).Count
            if (
                $malformedKnownLines.Count -ne 0 -or
                $knownCount -gt 1 -or
                ($paths.Count -gt 0 -and $knownCount -ne 1)
            ) {
                throw "Package state is internally inconsistent for $PackageName user $userId"
            }
            [pscustomobject]@{
                UserId = $userId
                Installed = $paths.Count -gt 0
                Paths = $paths
                RetainedOrGlobalRecord = $knownCount -eq 1
            }
        }
    )
    return [pscustomobject]@{
        PackageName = $PackageName
        UserStates = $userStates
        Installed = @($userStates | Where-Object { $_.Installed }).Count -gt 0
        InstalledUserIds = @($userStates | Where-Object { $_.Installed } | ForEach-Object { $_.UserId })
        Paths = @($userStates | ForEach-Object { $_.Paths } | Sort-Object -Unique)
        KnownToPackageManager = @($userStates | Where-Object { $_.RetainedOrGlobalRecord }).Count -gt 0
    }
}

function Assert-InitiallyAbsent {
    param(
        [Parameter(Mandatory)] [pscustomobject] $State,
        [Parameter(Mandatory)] [string] $Label
    )

    if ($State.Installed) {
        throw "$Label must initially be absent; refusing to overwrite its APK or app data"
    }
    if ($State.KnownToPackageManager) {
        throw "$Label retains a package-manager record; refusing a non-reversible install"
    }
}

function Assert-InstalledArtifact {
    param(
        [Parameter(Mandatory)] [pscustomobject] $Artifact,
        [Parameter(Mandatory)] [string] $TemporaryDirectory,
        [Parameter(Mandatory)] [int[]] $FrozenUserIds
    )

    Assert-DeviceUserSetUnchanged -ExpectedUserIds $FrozenUserIds
    $state = Get-PackageState -PackageName $Artifact.PackageName -UserIds $FrozenUserIds
    if (-not $state.Installed -or $state.Paths.Count -ne 1) {
        throw "Installed artifact must have one exact base APK: $($Artifact.PackageName)"
    }
    $pulledPath = Join-Path $TemporaryDirectory "$($Artifact.PackageName)-installed.apk"
    if (Test-Path -LiteralPath $pulledPath) {
        Remove-Item -LiteralPath $pulledPath -Force
    }
    $null = Invoke-ExactAdb `
        -AdbArguments @('pull', $state.Paths[0], $pulledPath) `
        -Label "pull exact installed artifact $($Artifact.PackageName)"
    if ((Get-FileSha256 -Path $pulledPath) -cne $Artifact.Sha256) {
        throw "Installed APK hash differs from run-owned artifact: $($Artifact.PackageName)"
    }
    if ((Get-ApkPackageName -Path $pulledPath) -cne $Artifact.PackageName) {
        throw "Installed package identity changed: $($Artifact.PackageName)"
    }
    $actualSignerKey = Get-SignerKey -Digests @(Get-ApkSignerDigests -Path $pulledPath)
    if ($actualSignerKey -cne $Artifact.SignerKey) {
        throw "Installed signer differs from run-owned artifact: $($Artifact.PackageName)"
    }
    $actualAbiKey = Get-AbiKey -Abis @(Get-ApkNativeAbis -Path $pulledPath)
    if ($actualAbiKey -cne $Artifact.AbiKey) {
        throw "Installed ABI set differs from run-owned artifact: $($Artifact.PackageName)"
    }
}

function Install-RunOwnedArtifact {
    param(
        [Parameter(Mandatory)] [pscustomobject] $Artifact,
        [Parameter(Mandatory)] [int[]] $FrozenUserIds
    )

    Assert-DeviceUserSetUnchanged -ExpectedUserIds $FrozenUserIds
    $preInstallState = Get-PackageState -PackageName $Artifact.PackageName -UserIds $FrozenUserIds
    Assert-InitiallyAbsent -State $preInstallState -Label "pre-install $($Artifact.PackageName)"
    $result = Invoke-ExactAdb `
        -AdbArguments @('install', '--user', '0', '-t', $Artifact.Path) `
        -Label "temporary non-replacing install for $($Artifact.PackageName)" `
        -AllowNonZero
    if ($result.ExitCode -ne 0 -or $result.Text -notmatch '(?im)^Success\s*$') {
        throw "Temporary install failed for $($Artifact.PackageName)`n$($result.Text)"
    }
}

function Remove-RunOwnedArtifact {
    param(
        [Parameter(Mandatory)] [pscustomobject] $Artifact,
        [Parameter(Mandatory)] [string] $TemporaryDirectory,
        [Parameter(Mandatory)] [int[]] $FrozenUserIds
    )

    Assert-DeviceUserSetUnchanged -ExpectedUserIds $FrozenUserIds
    $current = Get-PackageState -PackageName $Artifact.PackageName -UserIds $FrozenUserIds
    if (-not $current.Installed) {
        if ($current.KnownToPackageManager) {
            throw "Run-owned package is absent but retains a package-manager record: $($Artifact.PackageName)"
        }
        return
    }
    Assert-InstalledArtifact `
        -Artifact $Artifact `
        -TemporaryDirectory $TemporaryDirectory `
        -FrozenUserIds $FrozenUserIds
    Assert-DeviceUserSetUnchanged -ExpectedUserIds $FrozenUserIds
    $result = Invoke-ExactAdb `
        -AdbArguments @('uninstall', $Artifact.PackageName) `
        -Label "restore absent state for $($Artifact.PackageName)" `
        -AllowNonZero
    if ($result.ExitCode -ne 0 -or $result.Text -notmatch '(?im)^Success\s*$') {
        throw "Unable to remove run-owned package $($Artifact.PackageName)`n$($result.Text)"
    }
    Assert-DeviceUserSetUnchanged -ExpectedUserIds $FrozenUserIds
    $restored = Get-PackageState -PackageName $Artifact.PackageName -UserIds $FrozenUserIds
    if ($restored.Installed -or $restored.KnownToPackageManager) {
        throw "Package state was not restored to absent: $($Artifact.PackageName)"
    }
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
        Selector = $selector
        Observation = 'OK_1_TEST_ZERO_SKIP'
    }
}

if (-not $ConfirmDeviceMutation.IsPresent) {
    throw 'Pass -ConfirmDeviceMutation explicitly; temporary package installation is otherwise forbidden'
}
if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SERIAL) -and $env:ANDROID_SERIAL -cne $expectedSerial) {
    throw 'ANDROID_SERIAL differs from exact serial QV710AF65F; refusing ambiguous device scope'
}

$sourceHostArtifact = Assert-LocalArtifact `
    -Label 'host arm64 target APK' `
    -Path $resolvedHostApk `
    -ExpectedSha256 $HostSha256 `
    -ExpectedPackage $hostPackage `
    -ExpectedAbis @($expectedDeviceAbi)
$sourcePluginArtifact = Assert-LocalArtifact `
    -Label 'real Python Runtime arm64 plugin APK' `
    -Path $resolvedPluginApk `
    -ExpectedSha256 $PluginSha256 `
    -ExpectedPackage $pluginPackage `
    -ExpectedAbis @($expectedDeviceAbi)
$sourceTestArtifact = Assert-LocalArtifact `
    -Label 'host ABI-independent androidTest APK' `
    -Path $resolvedTestApk `
    -ExpectedSha256 $TestSha256 `
    -ExpectedPackage $testPackage

$signerKeys = @(
    @($sourceHostArtifact.SignerKey, $sourcePluginArtifact.SignerKey, $sourceTestArtifact.SignerKey) |
        Sort-Object -Unique
)
if ($signerKeys.Count -ne 1 -or $signerKeys[0] -cne $expectedSignerSha256) {
    throw 'Host, real plugin, and androidTest APKs do not share the pinned signer'
}

$deviceState = Invoke-ExactAdb -AdbArguments @('get-state') -Label 'device state'
if ($deviceState.Text.Trim() -cne 'device') {
    throw "Exact serial is not in device state: $($deviceState.Text.Trim())"
}
$frozenUserIds = @(Get-DeviceUserIds)
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

$temporaryRoot = Join-Path `
    ([IO.Path]::GetTempPath()) `
    ("autojs6-python-r2-reliable-pipe-" + [Guid]::NewGuid().ToString('N'))
[void] (New-Item -ItemType Directory -Path $temporaryRoot)

$hostInstalled = $false
$pluginInstalled = $false
$testInstalled = $false
$primaryFailure = $null
$restorationFailures = [Collections.Generic.List[string]]::new()
$initialStates = @{}
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
        -ExpectedAbis @($expectedDeviceAbi)
    $pluginArtifact = New-ImmutableArtifactSnapshot `
        -Artifact $sourcePluginArtifact `
        -TemporaryDirectory $temporaryRoot `
        -FileName 'python-runtime-plugin.apk' `
        -ExpectedSha256 $PluginSha256 `
        -ExpectedPackage $pluginPackage `
        -ExpectedAbis @($expectedDeviceAbi)
    $testArtifact = New-ImmutableArtifactSnapshot `
        -Artifact $sourceTestArtifact `
        -TemporaryDirectory $temporaryRoot `
        -FileName 'host-android-test.apk' `
        -ExpectedSha256 $TestSha256 `
        -ExpectedPackage $testPackage

    foreach ($artifact in @($hostArtifact, $pluginArtifact, $testArtifact)) {
        $state = Get-PackageState -PackageName $artifact.PackageName -UserIds $frozenUserIds
        $initialStates[$artifact.PackageName] = $state
        Assert-InitiallyAbsent -State $state -Label $artifact.Label
    }
    Assert-DeviceUserSetUnchanged -ExpectedUserIds $frozenUserIds

    $hostInstalled = $true
    Install-RunOwnedArtifact -Artifact $hostArtifact -FrozenUserIds $frozenUserIds
    Assert-InstalledArtifact `
        -Artifact $hostArtifact `
        -TemporaryDirectory $temporaryRoot `
        -FrozenUserIds $frozenUserIds

    $pluginInstalled = $true
    Install-RunOwnedArtifact -Artifact $pluginArtifact -FrozenUserIds $frozenUserIds
    Assert-InstalledArtifact `
        -Artifact $pluginArtifact `
        -TemporaryDirectory $temporaryRoot `
        -FrozenUserIds $frozenUserIds

    $testInstalled = $true
    Install-RunOwnedArtifact -Artifact $testArtifact -FrozenUserIds $frozenUserIds
    Assert-InstalledArtifact `
        -Artifact $testArtifact `
        -TemporaryDirectory $temporaryRoot `
        -FrozenUserIds $frozenUserIds

    $observation = Invoke-ExactInstrumentationMethod
} catch {
    $primaryFailure = $_.Exception
} finally {
    if ($testInstalled) {
        try {
            Remove-RunOwnedArtifact `
                -Artifact $testArtifact `
                -TemporaryDirectory $temporaryRoot `
                -FrozenUserIds $frozenUserIds
        } catch {
            $restorationFailures.Add($_.Exception.Message)
        }
    }
    if ($pluginInstalled) {
        try {
            Remove-RunOwnedArtifact `
                -Artifact $pluginArtifact `
                -TemporaryDirectory $temporaryRoot `
                -FrozenUserIds $frozenUserIds
        } catch {
            $restorationFailures.Add($_.Exception.Message)
        }
    }
    if ($hostInstalled) {
        try {
            Remove-RunOwnedArtifact `
                -Artifact $hostArtifact `
                -TemporaryDirectory $temporaryRoot `
                -FrozenUserIds $frozenUserIds
        } catch {
            $restorationFailures.Add($_.Exception.Message)
        }
    }

    try {
        foreach ($artifact in @($hostArtifact, $pluginArtifact, $testArtifact)) {
            $initial = $initialStates[$artifact.PackageName]
            if ($null -ne $initial) {
                if ($initial.Installed -or $initial.KnownToPackageManager) {
                    throw "Initial state invariant was not absent: $($artifact.PackageName)"
                }
                Assert-DeviceUserSetUnchanged -ExpectedUserIds $frozenUserIds
                $current = Get-PackageState -PackageName $artifact.PackageName -UserIds $frozenUserIds
                if ($current.Installed -or $current.KnownToPackageManager) {
                    throw "Package state was not restored to absent: $($artifact.PackageName)"
                }
            }
        }
    } catch {
        $restorationFailures.Add($_.Exception.Message)
    }

    try {
        if (Test-Path -LiteralPath $temporaryRoot -PathType Container) {
            $resolvedTemporaryRoot = (Resolve-Path -LiteralPath $temporaryRoot).ProviderPath
            $resolvedSystemTemp = (Resolve-Path -LiteralPath ([IO.Path]::GetTempPath())).ProviderPath
            if (-not $resolvedTemporaryRoot.StartsWith($resolvedSystemTemp, [StringComparison]::OrdinalIgnoreCase)) {
                throw "Refusing cleanup outside resolved system temp: $resolvedTemporaryRoot"
            }
            Get-ChildItem -LiteralPath $resolvedTemporaryRoot -File -Force |
                ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force }
            Remove-Item -LiteralPath $resolvedTemporaryRoot -Force
        }
    } catch {
        $restorationFailures.Add("Local temporary-file cleanup failed: $($_.Exception.Message)")
    }
}

if ($restorationFailures.Count -gt 0) {
    $message = "Device package restoration verification failed:`n" +
        [string]::Join("`n", @($restorationFailures | ForEach-Object { "- $_" }))
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
    scope = 'R2_REAL_CPYTHON_RELIABLE_PIPE_ERROR_AND_EXACT_REBIND_DIAGNOSTIC_ONLY'
    exactSerial = $expectedSerial
    device = [ordered]@{
        api = $deviceApi
        abi = $deviceAbi
        fingerprint = $deviceFingerprint
        frozenUserIds = $frozenUserIds
    }
    pinnedSignerSha256 = $expectedSignerSha256
    artifacts = @(
        [ordered]@{ packageName = $hostPackage; sha256 = $hostArtifact.Sha256; abis = $hostArtifact.Abis },
        [ordered]@{ packageName = $pluginPackage; sha256 = $pluginArtifact.Sha256; abis = $pluginArtifact.Abis },
        [ordered]@{ packageName = $testPackage; sha256 = $testArtifact.Sha256; abis = $testArtifact.Abis }
    )
    initialPackageState = [ordered]@{
        host = 'ABSENT_ALL_FROZEN_USERS_REQUIRED'
        realPlugin = 'ABSENT_ALL_FROZEN_USERS_REQUIRED'
        androidTest = 'ABSENT_ALL_FROZEN_USERS_REQUIRED'
    }
    temporaryInstallOrder = @($hostPackage, $pluginPackage, $testPackage)
    restorationOrder = @($testPackage, $pluginPackage, $hostPackage)
    observation = $observation
    covered = @(
        'RELIABLE_PIPE_RECEIVER_COMM_ERROR',
        'BYTE_EXACT_DECLARED_LENGTH_AND_SHA256',
        'SOURCE_REJECTED_BEFORE_CPYTHON_STARTED_CALLBACK',
        'CALLBACK_UID_AT_EVERY_PROTOCOL_ENTRY',
        'TERMINAL_CLOSE_PROCESS_RETIREMENT',
        'EXACT_COMPONENT_REBIND_FINITE_CPYTHON_RECOVERY',
        'DIAGNOSTIC_HARNESS_DID_NOT_REPLAY_AFTER_DISPATCH',
        'PFD_FINALLY_CLOSURE'
    )
    notCovered = @(
        'SAME_BINDING_RECOVERY',
        'TIMEOUT',
        'HOSTILE_CALLER',
        'UPDATE_OR_UNINSTALL_RACE',
        'PRODUCTION_HOST_AUTOMATIC_REPLAY_POLICY',
        'FULL_PFD_HOSTILE_MATRIX'
    )
    androidUserSetRestoration = 'VERIFIED_UNCHANGED'
    packageInstallationRestoration = 'VERIFIED_ABSENT_ALL_FROZEN_USERS'
    appDataSnapshotTaken = $false
    acceptanceReceiptWritten = $false
    r2DeviceAcceptance = $false
} | ConvertTo-Json -Depth 7
