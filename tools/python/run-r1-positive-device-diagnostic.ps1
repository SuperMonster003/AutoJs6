#Requires -Version 7.0

[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^[A-Za-z0-9._:-]+$')]
    [string] $Serial,

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
    [string] $FakeApk,

    [Parameter(Mandatory)]
    [ValidatePattern('^[0-9A-Fa-f]{64}$')]
    [string] $FakeSha256,

    [Parameter(Mandatory)]
    [string] $AdbPath,

    [Parameter(Mandatory)]
    [string] $Aapt2Path,

    [Parameter(Mandatory)]
    [string] $ApkSignerPath,

    [Parameter(Mandatory)]
    [switch] $ConfirmDeviceMutation,

    [Parameter()]
    [switch] $AllowTemporaryHostInstall
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
# Keep non-zero native exits observable through $LASTEXITCODE so AllowNonZero callers can parse
# instrumentation/install output before raising a scoped error. A caller preference must not turn
# redirected native stderr into an early terminating PowerShell error.
$PSNativeCommandUseErrorActionPreference = $false

# This runner is deliberately diagnostic-only. It exercises only the enumerated R1
# instrumentation cases and never creates the named R1 device acceptance receipt.
$hostPackage = 'org.autojs.autojs6'
$testPackage = 'org.autojs.autojs6.test'
$fakePackage = 'org.autojs.plugin.python.runtime.fake'
$runnerComponent = "$testPackage/androidx.test.runner.AndroidJUnitRunner"
$testClass = 'org.autojs.autojs.core.plugin.python.PythonRuntimeAndroidConformanceTest'
$optInArgument = 'autojs.python.r1.conformance.enabled'
$methods = @(
    'exactActionDiscoveryBindsWithNullActionAndReturnsValidatedMetadata',
    'realPfdNormalSessionEnforcesCreditsOutputResultAndCallbackUid',
    'reliablePipeProducerErrorRejectsByteExactSourceBeforeExecution'
)

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
$resolvedFakeApk = Resolve-ExactFile -Path $FakeApk -Label 'FakeApk'

$script:ExactSerial = $Serial
$script:AdbExecutable = $resolvedAdb
$script:Aapt2Executable = $resolvedAapt2
$script:ApkSignerExecutable = $resolvedApkSigner

function Invoke-ExactAdb {
    param(
        [Parameter(Mandatory)] [string[]] $AdbArguments,
        [Parameter(Mandatory)] [string] $Label,
        [switch] $AllowNonZero
    )

    # Every device operation passes through this one argument-array invocation. There is no
    # ANDROID_SERIAL fallback, implicit device selection, connected task, or shell string.
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

function Get-ApkPackageName {
    param([Parameter(Mandatory)] [string] $Path)

    $result = Invoke-NativeCapture `
        -Executable $script:Aapt2Executable `
        -Arguments @('dump', 'badging', $Path) `
        -Label "aapt2 package inspection for $Path"
    $match = [regex]::Match($result.Text, "(?m)^package:\s+name='([^']+)'(?:\s|$)")
    if (-not $match.Success) {
        throw "Unable to read package name from APK: $Path"
    }
    return $match.Groups[1].Value
}

function Get-ApkSignerDigests {
    param([Parameter(Mandatory)] [string] $Path)

    $result = Invoke-NativeCapture `
        -Executable $script:ApkSignerExecutable `
        -Arguments @('verify', '--print-certs', $Path) `
        -Label "apksigner verification for $Path"
    $matches = [regex]::Matches(
        $result.Text,
        '(?im)^Signer #\d+ certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$',
        [System.Text.RegularExpressions.RegexOptions]::None
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

function Assert-LocalArtifact {
    param(
        [Parameter(Mandatory)] [string] $Label,
        [Parameter(Mandatory)] [string] $Path,
        [Parameter(Mandatory)] [string] $ExpectedSha256,
        [Parameter(Mandatory)] [string] $ExpectedPackage
    )

    if ([IO.Path]::GetExtension($Path) -ine '.apk') {
        throw "$Label must be an exact APK path"
    }
    $actualSha256 = Get-FileSha256 -Path $Path
    if ($actualSha256 -ne $ExpectedSha256.ToLowerInvariant()) {
        throw "$Label SHA-256 mismatch: expected $ExpectedSha256, actual $actualSha256"
    }
    $actualPackage = Get-ApkPackageName -Path $Path
    if ($actualPackage -cne $ExpectedPackage) {
        throw "$Label package mismatch: expected $ExpectedPackage, actual $actualPackage"
    }
    $signers = @(Get-ApkSignerDigests -Path $Path)
    return [pscustomobject]@{
        Label = $Label
        Path = $Path
        Sha256 = $actualSha256
        PackageName = $actualPackage
        Signers = $signers
        SignerKey = Get-SignerKey -Digests $signers
    }
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

function Get-PackageState {
    param([Parameter(Mandatory)] [string] $PackageName)

    $result = Invoke-ExactAdb `
        -AdbArguments @('shell', 'pm', 'path', '--user', '0', $PackageName) `
        -Label "package-state query for $PackageName" `
        -AllowNonZero
    $missingWithEmptyOutput = $result.ExitCode -eq 1 -and [string]::IsNullOrWhiteSpace($result.Text)
    if ($result.ExitCode -ne 0 -and -not $missingWithEmptyOutput) {
        throw "Unable to query package state for $PackageName`n$($result.Text)"
    }
    $paths = @(
        $result.Lines |
            ForEach-Object { $_.Trim() } |
            Where-Object { $_.StartsWith('package:', [StringComparison]::Ordinal) } |
            ForEach-Object { $_.Substring('package:'.Length) }
    )
    if ($missingWithEmptyOutput -and $paths.Count -ne 0) {
        throw "Absent package query unexpectedly returned APK paths for $PackageName"
    }
    $knownResult = Invoke-ExactAdb `
        -AdbArguments @('shell', 'pm', 'list', 'packages', '-u', $PackageName) `
        -Label "global package-record query for $PackageName" `
        -AllowNonZero
    if ($knownResult.ExitCode -ne 0) {
        throw "Unable to query global package record for $PackageName`n$($knownResult.Text)"
    }
    $knownToPackageManager = @(
        $knownResult.Lines |
            ForEach-Object { $_.Trim() } |
            Where-Object { $_ -ceq "package:$PackageName" }
    ).Count -eq 1
    if ($paths.Count -gt 0 -and -not $knownToPackageManager) {
        throw "Package state is internally inconsistent for $PackageName"
    }
    return [pscustomobject]@{
        PackageName = $PackageName
        Installed = $paths.Count -gt 0
        Paths = $paths
        KnownToPackageManager = $knownToPackageManager
    }
}

function Assert-SafeMissingStateForTemporaryInstall {
    param(
        [Parameter(Mandatory)] [pscustomobject] $State,
        [Parameter(Mandatory)] [string] $Label
    )

    if ($State.Installed) {
        return
    }
    if ($State.KnownToPackageManager) {
        throw "$Label is absent for user 0 but still has a package-manager record; refusing a non-reversible temporary install"
    }
}

function Assert-InstalledArtifact {
    param(
        [Parameter(Mandatory)] [string] $Label,
        [Parameter(Mandatory)] [pscustomobject] $ExpectedArtifact,
        [Parameter(Mandatory)] [string] $TemporaryDirectory
    )

    $state = Get-PackageState -PackageName $ExpectedArtifact.PackageName
    if (-not $state.Installed) {
        throw "$Label is not installed for user 0"
    }
    if ($state.Paths.Count -ne 1) {
        throw "$Label must be a single exact APK; installed paths=$($state.Paths.Count)"
    }
    $pulledPath = Join-Path $TemporaryDirectory "$($ExpectedArtifact.PackageName)-installed.apk"
    if (Test-Path -LiteralPath $pulledPath) {
        Remove-Item -LiteralPath $pulledPath -Force
    }
    $pull = Invoke-ExactAdb `
        -AdbArguments @('pull', $state.Paths[0], $pulledPath) `
        -Label "read installed APK for $Label"
    if (-not (Test-Path -LiteralPath $pulledPath -PathType Leaf)) {
        throw "ADB did not materialize the installed APK for $Label`n$($pull.Text)"
    }
    $actualHash = Get-FileSha256 -Path $pulledPath
    if ($actualHash -ne $ExpectedArtifact.Sha256) {
        throw "$Label is installed but differs from the exact prebuilt artifact; refusing overwrite"
    }
    $actualPackage = Get-ApkPackageName -Path $pulledPath
    if ($actualPackage -cne $ExpectedArtifact.PackageName) {
        throw "$Label installed package identity changed"
    }
    $actualSigners = @(Get-ApkSignerDigests -Path $pulledPath)
    if ((Get-SignerKey -Digests $actualSigners) -cne $ExpectedArtifact.SignerKey) {
        throw "$Label installed signer differs from the exact prebuilt artifact"
    }
    return [pscustomobject]@{
        State = $state
        Sha256 = $actualHash
        SignerKey = Get-SignerKey -Digests $actualSigners
    }
}

function Install-MissingArtifact {
    param([Parameter(Mandatory)] [pscustomobject] $Artifact)

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
        [Parameter(Mandatory)] [string] $TemporaryDirectory
    )

    $PackageName = $Artifact.PackageName
    $currentState = Get-PackageState -PackageName $PackageName
    if (-not $currentState.Installed) {
        return
    }
    # A concurrent actor may have changed the package after our install. In that case the runner
    # must fail restoration visibly, but must not remove an artifact it can no longer prove it owns.
    $null = Assert-InstalledArtifact `
        -Label "package owned for restoration: $PackageName" `
        -ExpectedArtifact $Artifact `
        -TemporaryDirectory $TemporaryDirectory

    $result = Invoke-ExactAdb `
        -AdbArguments @('uninstall', $PackageName) `
        -Label "restore package state for $PackageName" `
        -AllowNonZero
    if ($result.ExitCode -ne 0 -or $result.Text -notmatch '(?im)^Success\s*$') {
        throw "Unable to remove the package installed by this run: $PackageName`n$($result.Text)"
    }
    $restored = Get-PackageState -PackageName $PackageName
    if ($restored.Installed -or $restored.KnownToPackageManager) {
        throw "Package or retained package-manager record remains after restoration: $PackageName"
    }
}

function Invoke-ExactInstrumentationMethod {
    param([Parameter(Mandatory)] [string] $Method)

    $selector = "$testClass#$Method"
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
    $terminalStatusCount = [regex]::Matches(
        $result.Text,
        '(?m)^INSTRUMENTATION_STATUS_CODE:\s*0\s*$'
    ).Count
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
        throw "Instrumentation did not prove exactly OK (1 test) with zero skips: $selector`n$($result.Text)"
    }
    return [pscustomobject]@{
        Method = $Method
        Observation = 'OK_1_TEST_ZERO_SKIP'
    }
}

if (-not $ConfirmDeviceMutation.IsPresent) {
    throw 'Pass -ConfirmDeviceMutation explicitly; temporary package installation is otherwise forbidden'
}
if ($Serial.StartsWith('-', [StringComparison]::Ordinal)) {
    throw 'Serial must not begin with a dash'
}
if (
    -not [string]::IsNullOrWhiteSpace($env:ANDROID_SERIAL) -and
    $env:ANDROID_SERIAL -cne $Serial
) {
    throw 'ANDROID_SERIAL differs from -Serial; refusing ambiguous device scope'
}

$hostArtifact = Assert-LocalArtifact `
    -Label 'host target APK' `
    -Path $resolvedHostApk `
    -ExpectedSha256 $HostSha256 `
    -ExpectedPackage $hostPackage
$testArtifact = Assert-LocalArtifact `
    -Label 'host androidTest APK' `
    -Path $resolvedTestApk `
    -ExpectedSha256 $TestSha256 `
    -ExpectedPackage $testPackage
$fakeArtifact = Assert-LocalArtifact `
    -Label 'fake provider APK' `
    -Path $resolvedFakeApk `
    -ExpectedSha256 $FakeSha256 `
    -ExpectedPackage $fakePackage

$signerKeys = @(
    @($hostArtifact.SignerKey, $testArtifact.SignerKey, $fakeArtifact.SignerKey) |
        Sort-Object -Unique
)
if ($signerKeys.Count -ne 1) {
    throw 'The host target, androidTest, and fake provider APKs do not share one exact signer set'
}

$deviceState = Invoke-ExactAdb -AdbArguments @('get-state') -Label 'device state'
if ($deviceState.Text.Trim() -cne 'device') {
    throw "Exact serial is not in device state: $($deviceState.Text.Trim())"
}
$deviceApiText = Get-DeviceProperty -Property 'ro.build.version.sdk' -Label 'device API query'
$deviceApi = 0
if (-not [int]::TryParse($deviceApiText, [ref] $deviceApi) -or $deviceApi -lt 24) {
    throw "Unsupported device API: $deviceApiText"
}
$deviceAbi = Get-DeviceProperty -Property 'ro.product.cpu.abi' -Label 'device ABI query'
$deviceFingerprint = Get-DeviceProperty -Property 'ro.build.fingerprint' -Label 'device fingerprint query'

$temporaryRoot = Join-Path `
    ([IO.Path]::GetTempPath()) `
    ("autojs6-python-r1-diagnostic-" + [Guid]::NewGuid().ToString('N'))
[void] (New-Item -ItemType Directory -Path $temporaryRoot)

$hostInstalledByThisRun = $false
$testInstalledByThisRun = $false
$fakeInstalledByThisRun = $false
$primaryFailure = $null
$restoreFailures = [Collections.Generic.List[string]]::new()
$observations = [Collections.Generic.List[object]]::new()
$initialHostState = $null
$initialTestState = $null
$initialFakeState = $null

try {
    # Snapshot and validate every pre-existing package before the first mutation. A present host
    # must always be byte-for-byte exact and is never replaced, regardless of the optional switch.
    $initialHostState = Get-PackageState -PackageName $hostPackage
    $initialTestState = Get-PackageState -PackageName $testPackage
    $initialFakeState = Get-PackageState -PackageName $fakePackage

    if ($initialHostState.Installed) {
        $null = Assert-InstalledArtifact `
            -Label 'pre-existing host target APK' `
            -ExpectedArtifact $hostArtifact `
            -TemporaryDirectory $temporaryRoot
    } elseif (-not $AllowTemporaryHostInstall.IsPresent) {
        throw 'Host target is absent; pass -AllowTemporaryHostInstall explicitly to permit a reversible exact host install'
    }
    if (-not $initialHostState.Installed) {
        Assert-SafeMissingStateForTemporaryInstall `
            -State $initialHostState `
            -Label 'host target'
    }
    if (-not $initialFakeState.Installed) {
        Assert-SafeMissingStateForTemporaryInstall `
            -State $initialFakeState `
            -Label 'fake provider'
    }
    if (-not $initialTestState.Installed) {
        Assert-SafeMissingStateForTemporaryInstall `
            -State $initialTestState `
            -Label 'host androidTest'
    }
    if ($initialTestState.Installed) {
        $null = Assert-InstalledArtifact `
            -Label 'pre-existing host androidTest APK' `
            -ExpectedArtifact $testArtifact `
            -TemporaryDirectory $temporaryRoot
    }
    if ($initialFakeState.Installed) {
        $null = Assert-InstalledArtifact `
            -Label 'pre-existing fake provider APK' `
            -ExpectedArtifact $fakeArtifact `
            -TemporaryDirectory $temporaryRoot
    }

    # Installation order is intentional: target host, fake provider, then instrumentation APK.
    if (-not $initialHostState.Installed) {
        Install-MissingArtifact -Artifact $hostArtifact
        $hostInstalledByThisRun = $true
        $null = Assert-InstalledArtifact `
            -Label 'temporary host target APK' `
            -ExpectedArtifact $hostArtifact `
            -TemporaryDirectory $temporaryRoot
    }
    if (-not $initialFakeState.Installed) {
        Install-MissingArtifact -Artifact $fakeArtifact
        $fakeInstalledByThisRun = $true
        $null = Assert-InstalledArtifact `
            -Label 'temporary fake provider APK' `
            -ExpectedArtifact $fakeArtifact `
            -TemporaryDirectory $temporaryRoot
    }
    if (-not $initialTestState.Installed) {
        Install-MissingArtifact -Artifact $testArtifact
        $testInstalledByThisRun = $true
        $null = Assert-InstalledArtifact `
            -Label 'temporary host androidTest APK' `
            -ExpectedArtifact $testArtifact `
            -TemporaryDirectory $temporaryRoot
    }

    foreach ($method in $methods) {
        $observations.Add((Invoke-ExactInstrumentationMethod -Method $method))
    }
} catch {
    $primaryFailure = $_.Exception
} finally {
    # Restore only packages whose successful installation is owned by this run. Never remove or
    # replace a package which existed at the initial snapshot. Reverse install order is mandatory.
    if ($testInstalledByThisRun) {
        try {
            Remove-OwnedArtifact -Artifact $testArtifact -TemporaryDirectory $temporaryRoot
        } catch {
            $restoreFailures.Add($_.Exception.Message)
        }
    }
    if ($fakeInstalledByThisRun) {
        try {
            Remove-OwnedArtifact -Artifact $fakeArtifact -TemporaryDirectory $temporaryRoot
        } catch {
            $restoreFailures.Add($_.Exception.Message)
        }
    }
    if ($hostInstalledByThisRun) {
        try {
            Remove-OwnedArtifact -Artifact $hostArtifact -TemporaryDirectory $temporaryRoot
        } catch {
            $restoreFailures.Add($_.Exception.Message)
        }
    }

    try {
        if ($null -ne $initialHostState) {
            $currentHostState = Get-PackageState -PackageName $hostPackage
            if ($currentHostState.Installed -ne $initialHostState.Installed) {
                throw 'host target package state was not restored'
            }
            if ($currentHostState.KnownToPackageManager -ne $initialHostState.KnownToPackageManager) {
                throw 'host target global package record was not restored'
            }
            if ($initialHostState.Installed) {
                $null = Assert-InstalledArtifact `
                    -Label 'restored host target APK' `
                    -ExpectedArtifact $hostArtifact `
                    -TemporaryDirectory $temporaryRoot
            }
        }
        if ($null -ne $initialTestState) {
            $currentTestState = Get-PackageState -PackageName $testPackage
            if ($currentTestState.Installed -ne $initialTestState.Installed) {
                throw 'host androidTest package state was not restored'
            }
            if ($currentTestState.KnownToPackageManager -ne $initialTestState.KnownToPackageManager) {
                throw 'host androidTest global package record was not restored'
            }
            if ($initialTestState.Installed) {
                $null = Assert-InstalledArtifact `
                    -Label 'restored host androidTest APK' `
                    -ExpectedArtifact $testArtifact `
                    -TemporaryDirectory $temporaryRoot
            }
        }
        if ($null -ne $initialFakeState) {
            $currentFakeState = Get-PackageState -PackageName $fakePackage
            if ($currentFakeState.Installed -ne $initialFakeState.Installed) {
                throw 'fake provider package state was not restored'
            }
            if ($currentFakeState.KnownToPackageManager -ne $initialFakeState.KnownToPackageManager) {
                throw 'fake provider global package record was not restored'
            }
            if ($initialFakeState.Installed) {
                $null = Assert-InstalledArtifact `
                    -Label 'restored fake provider APK' `
                    -ExpectedArtifact $fakeArtifact `
                    -TemporaryDirectory $temporaryRoot
            }
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
        # Local cleanup must not mask the primary diagnostic or device-restoration result.
        $restoreFailures.Add("Local diagnostic temporary-file cleanup failed: $($_.Exception.Message)")
    }
}

if ($restoreFailures.Count -gt 0) {
    $message = "Device restoration verification failed:`n" +
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
    scope = 'R1_BINDER_PFD_THREE_CASE_DIAGNOSTIC_ONLY'
    exactSerial = $Serial
    device = [ordered]@{
        api = $deviceApi
        abi = $deviceAbi
        fingerprint = $deviceFingerprint
    }
    artifacts = @(
        [ordered]@{ packageName = $hostPackage; sha256 = $hostArtifact.Sha256 },
        [ordered]@{ packageName = $testPackage; sha256 = $testArtifact.Sha256 },
        [ordered]@{ packageName = $fakePackage; sha256 = $fakeArtifact.Sha256 }
    )
    initialPackageState = [ordered]@{
        host = if ($initialHostState.Installed) { 'PREEXISTING_EXACT' } else { 'ABSENT' }
        androidTest = if ($initialTestState.Installed) { 'PREEXISTING_EXACT' } else { 'ABSENT' }
        fakeProvider = if ($initialFakeState.Installed) { 'PREEXISTING_EXACT' } else { 'ABSENT' }
    }
    temporaryInstalls = [ordered]@{
        host = $hostInstalledByThisRun
        fakeProvider = $fakeInstalledByThisRun
        androidTest = $testInstalledByThisRun
    }
    methods = @($observations)
    restoration = 'VERIFIED'
    acceptanceReceiptWritten = $false
    r1DeviceAcceptance = $false
} | ConvertTo-Json -Depth 6
