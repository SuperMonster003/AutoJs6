#Requires -Version 7.0

[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^\S+$')]
    [string] $Serial,

    [Parameter(Mandatory)] [string] $HostApk,
    [Parameter(Mandatory)] [string] $HostTestApk,
    [Parameter(Mandatory)] [string] $PluginApk,

    [string] $AdbPath = 'adb',
    [string] $Aapt2Path = '',
    [string] $ApkSignerPath = '',
    [string] $OutputPath = '',

    [ValidateSet('java8-1x8', 'java8-128x8', 'java8-2048x8')]
    [string[]] $Corpora = @('java8-1x8', 'java8-128x8', 'java8-2048x8'),

    [ValidateRange(0, 20)] [int] $WarmupSamples = 2,
    [ValidateRange(1, 50)] [int] $MeasuredSamples = 10,
    [ValidateRange(20, 1000)] [int] $PssPollMilliseconds = 100,
    [ValidateRange(0, 30)] [int] $BetweenSamplesSeconds = 1,
    [ValidateRange(30, 600)] [int] $SampleTimeoutSeconds = 240,

    [Parameter(Mandatory)] [switch] $ConfirmDeviceMutation,
    [switch] $Overwrite
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false

$hostPackage = 'org.autojs.autojs6'
$hostTestPackage = 'org.autojs.autojs6.test'
$pluginPackage = 'io.github.supermonster003.autojs6.plugin.dexcompiler'
$pluginProcessName = "$pluginPackage`:compiler"
$testRunner = "$hostTestPackage/androidx.test.runner.AndroidJUnitRunner"
$testClass = 'org.autojs.autojs.core.plugin.dex.DexCompilerR5PerformanceAndroidTest'
$enabledArgument = 'autojs.dexCompiler.r5.performance.enabled'
$corpusArgument = 'autojs.dexCompiler.r5.performance.corpus'
$routeArgument = 'autojs.dexCompiler.r5.performance.route'
$cacheStateArgument = 'autojs.dexCompiler.r5.performance.cacheState'
$sampleIndexArgument = 'autojs.dexCompiler.r5.performance.sampleIndex'
$corpusStatusKey = 'autojs.dex.r5.performance.corpus'
$managementStatusKey = 'autojs.dex.r5.performance.management'
$sampleStatusKey = 'autojs.dex.r5.performance.sample'
$processQuiescenceTimeoutSeconds = 10
$processQuiescencePollMilliseconds = 100
$processQuiescenceRequiredAbsentPolls = 3

if (-not $ConfirmDeviceMutation) {
    throw 'Pass -ConfirmDeviceMutation explicitly; APK installation and process mutation are otherwise forbidden'
}

function Resolve-ExactFile {
    param([Parameter(Mandatory)] [string] $Path, [Parameter(Mandatory)] [string] $Label)

    $resolved = @(Resolve-Path -LiteralPath $Path -ErrorAction Stop)
    if ($resolved.Count -ne 1) { throw "$Label must resolve to exactly one file" }
    $item = Get-Item -LiteralPath $resolved[0].ProviderPath -Force
    if ($item.PSIsContainer) { throw "$Label is not a file: $($item.FullName)" }
    return $item.FullName
}

function Resolve-Executable {
    param([Parameter(Mandatory)] [string] $Value, [Parameter(Mandatory)] [string] $Label)

    if (Test-Path -LiteralPath $Value -PathType Leaf) {
        return Resolve-ExactFile -Path $Value -Label $Label
    }
    $commands = @(Get-Command -Name $Value -CommandType Application -ErrorAction Stop)
    if ($commands.Count -eq 0) { throw "$Label did not resolve to an executable" }
    return $commands[0].Source
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

function Invoke-AdbCapture {
    param(
        [Parameter(Mandatory)] [string[]] $Arguments,
        [Parameter(Mandatory)] [string] $Label,
        [switch] $AllowNonZero
    )

    return Invoke-NativeCapture -Executable $script:Adb -Arguments (@('-s', $Serial) + $Arguments) `
        -Label $Label -AllowNonZero:$AllowNonZero
}

function Get-Sha256 {
    param([Parameter(Mandatory)] [string] $Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-TextSha256 {
    param([Parameter(Mandatory)] [AllowEmptyString()] [string] $Value)
    $bytes = [Text.Encoding]::UTF8.GetBytes($Value)
    return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
}

function Resolve-SdkBuildTool {
    param([Parameter(Mandatory)] [string] $Name)

    $platformTools = Split-Path -Parent $script:Adb
    $sdkRoot = Split-Path -Parent $platformTools
    $buildToolsRoot = Join-Path $sdkRoot 'build-tools'
    $candidates = @(
        Get-ChildItem -LiteralPath $buildToolsRoot -Directory -ErrorAction Stop |
            Sort-Object {
                try { [version] $_.Name } catch { [version] '0.0' }
            } -Descending |
            ForEach-Object { Join-Path $_.FullName $Name } |
            Where-Object { Test-Path -LiteralPath $_ -PathType Leaf }
    )
    if ($candidates.Count -eq 0) { throw "Unable to find Android SDK build tool: $Name" }
    return (Resolve-Path -LiteralPath $candidates[0]).ProviderPath
}

function Get-ApkMetadata {
    param(
        [Parameter(Mandatory)] [string] $Path,
        [Parameter(Mandatory)] [string] $ExpectedPackage,
        [Parameter(Mandatory)] [string] $Label
    )

    $badging = Invoke-NativeCapture -Executable $script:Aapt2 -Arguments @('dump', 'badging', $Path) `
        -Label "aapt2 inspection for $Label"
    $packageMatch = [regex]::Match(
        $badging.Text,
        "(?m)^package:\s+name='([^']+)'\s+versionCode='([^']*)'\s+versionName='([^']*)'"
    )
    if (-not $packageMatch.Success) { throw "Unable to parse APK identity for $Label" }
    if ($packageMatch.Groups[1].Value -cne $ExpectedPackage) {
        throw "$Label package is $($packageMatch.Groups[1].Value), expected $ExpectedPackage"
    }
    $signing = Invoke-NativeCapture -Executable $script:ApkSigner `
        -Arguments @('verify', '--print-certs', $Path) -Label "apksigner verification for $Label"
    $signers = @(
        [regex]::Matches(
            $signing.Text,
            '(?im)^(?:Signer #\d+|V\d+ Signer:)\s+certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$'
        ) | ForEach-Object { $_.Groups[1].Value.ToLowerInvariant() } | Sort-Object -Unique
    )
    if ($signers.Count -ne 1) { throw "$Label must have exactly one signer certificate" }
    $nativeMatch = [regex]::Match($badging.Text, '(?m)^native-code:\s+(.+)$')
    $abis = if ($nativeMatch.Success) {
        @([regex]::Matches($nativeMatch.Groups[1].Value, "'([^']+)'") | ForEach-Object { $_.Groups[1].Value })
    } else {
        @()
    }
    $item = Get-Item -LiteralPath $Path
    return [pscustomobject][ordered]@{
        label = $Label
        path = $item.FullName
        packageName = $ExpectedPackage
        versionCode = $(if ([string]::IsNullOrEmpty($packageMatch.Groups[2].Value)) {
            $null
        } else {
            [long] $packageMatch.Groups[2].Value
        })
        versionName = $packageMatch.Groups[3].Value
        sizeBytes = $item.Length
        sha256 = Get-Sha256 -Path $item.FullName
        signerCertificateSha256 = $signers[0]
        nativeAbis = $abis
        debuggable = $badging.Text -match '(?m)^application-debuggable:'
    }
}

function Get-DeviceProperty {
    param([Parameter(Mandatory)] [string] $Name)
    $value = (Invoke-AdbCapture -Arguments @('shell', 'getprop', $Name) -Label "read device property $Name").Text.Trim()
    if ([string]::IsNullOrWhiteSpace($value)) { throw "Device property is empty: $Name" }
    return $value
}

function Get-PackagePaths {
    param([Parameter(Mandatory)] [string] $Package)

    $result = Invoke-AdbCapture -Arguments @('shell', 'pm', 'path', '--user', '0', $Package) `
        -Label "query package paths for $Package" -AllowNonZero
    if ($result.ExitCode -ne 0) { return @() }
    return @(
        $result.Lines |
            Where-Object { $_ -match '^package:.+' } |
            ForEach-Object { $_.Substring('package:'.Length).Trim() }
    )
}

function Test-GlobalPackageKnown {
    param([Parameter(Mandatory)] [string] $Package)
    $result = Invoke-AdbCapture -Arguments @('shell', 'pm', 'list', 'packages', $Package) `
        -Label "query global package record for $Package"
    return @($result.Lines | Where-Object { $_ -ceq "package:$Package" }).Count -eq 1
}

function Get-RemoteSha256 {
    param([Parameter(Mandatory)] [string] $Path)
    $result = Invoke-AdbCapture -Arguments @('shell', 'sha256sum', $Path) `
        -Label "hash installed APK $Path" -AllowNonZero
    if ($result.ExitCode -ne 0) { throw "Device cannot SHA-256 hash installed APK: $Path`n$($result.Text)" }
    $match = [regex]::Match($result.Text, '(?im)^([0-9a-f]{64})\s+')
    if (-not $match.Success) { throw "Unable to parse installed APK SHA-256: $Path" }
    return $match.Groups[1].Value.ToLowerInvariant()
}

function New-PackageSnapshot {
    param(
        [Parameter(Mandatory)] [string] $Package,
        [Parameter(Mandatory)] [string] $SnapshotRoot
    )

    $paths = @(Get-PackagePaths -Package $Package)
    $globalKnown = Test-GlobalPackageKnown -Package $Package
    if ($paths.Count -eq 0 -and $globalKnown) {
        throw "$Package has a retained/global record but is absent for user 0; this runner refuses to mutate ambiguous multi-user state"
    }
    $localApks = @()
    if ($paths.Count -gt 0) {
        $packageRoot = Join-Path $SnapshotRoot ($Package -replace '[^A-Za-z0-9._-]', '_')
        New-Item -ItemType Directory -Path $packageRoot -Force | Out-Null
        for ($index = 0; $index -lt $paths.Count; $index++) {
            $local = Join-Path $packageRoot ("{0:D2}.apk" -f $index)
            $pull = Invoke-AdbCapture -Arguments @('pull', $paths[$index], $local) `
                -Label "snapshot installed APK $Package #$index"
            if ($pull.Text -notmatch '(?i)(pulled|file pulled)') {
                throw "ADB did not confirm snapshot pull for $Package #$index`n$($pull.Text)"
            }
            $localApks += $local
        }
    }
    return [pscustomobject]@{
        Package = $Package
        InitiallyInstalledForUser0 = $paths.Count -gt 0
        InitiallyGlobalKnown = $globalKnown
        RemotePaths = $paths
        RestoreApks = $localApks
        RestoreSha256 = @($localApks | ForEach-Object { Get-Sha256 -Path $_ } | Sort-Object)
    }
}

function Get-PackageSnapshotEvidence {
    param([Parameter(Mandatory)] [pscustomobject] $Snapshot)
    return [ordered]@{
        packageName = $Snapshot.Package
        initiallyInstalledForUser0 = $Snapshot.InitiallyInstalledForUser0
        initiallyGlobalKnown = $Snapshot.InitiallyGlobalKnown
        apkCount = @($Snapshot.RestoreApks).Count
        apkSha256 = @($Snapshot.RestoreSha256)
    }
}

function Install-BenchmarkApk {
    param([Parameter(Mandatory)] [pscustomobject] $Artifact)
    $result = Invoke-AdbCapture -Arguments @(
        'install', '-r', '-t', '-d', '--user', '0', $Artifact.path
    ) -Label "install $($Artifact.label)"
    if ($result.Text -notmatch '(?m)^Success\s*$') {
        throw "Package manager did not confirm installation of $($Artifact.label)`n$($result.Text)"
    }
    if (@(Get-PackagePaths -Package $Artifact.packageName).Count -eq 0) {
        throw "Installed package is unavailable for user 0: $($Artifact.packageName)"
    }
}

function Restore-PackageSnapshot {
    param([Parameter(Mandatory)] [pscustomobject] $Snapshot)

    if ($Snapshot.InitiallyInstalledForUser0) {
        $command = if (@($Snapshot.RestoreApks).Count -eq 1) { 'install' } else { 'install-multiple' }
        $arguments = @($command, '-r', '-t', '-d', '--user', '0') + @($Snapshot.RestoreApks)
        $result = Invoke-AdbCapture -Arguments $arguments -Label "restore package $($Snapshot.Package)"
        if ($result.Text -notmatch '(?m)^Success\s*$') {
            throw "Package manager did not confirm restoration of $($Snapshot.Package)`n$($result.Text)"
        }
        $restoredPaths = @(Get-PackagePaths -Package $Snapshot.Package)
        if ($restoredPaths.Count -ne @($Snapshot.RestoreApks).Count) {
            throw "Restored APK count differs for $($Snapshot.Package)"
        }
        $restoredHashes = @($restoredPaths | ForEach-Object { Get-RemoteSha256 -Path $_ } | Sort-Object)
        if ([string]::Join(',', $restoredHashes) -cne [string]::Join(',', @($Snapshot.RestoreSha256))) {
            throw "Restored APK bytes differ for $($Snapshot.Package)"
        }
    } else {
        if (@(Get-PackagePaths -Package $Snapshot.Package).Count -eq 0 -and
            -not (Test-GlobalPackageKnown -Package $Snapshot.Package)) {
            return
        }
        $result = Invoke-AdbCapture -Arguments @('uninstall', $Snapshot.Package) `
            -Label "remove run-owned package $($Snapshot.Package)" -AllowNonZero
        if ($result.ExitCode -ne 0 -or $result.Text -notmatch '(?m)^Success\s*$') {
            throw "Unable to remove run-owned package $($Snapshot.Package)`n$($result.Text)"
        }
        if (@(Get-PackagePaths -Package $Snapshot.Package).Count -ne 0 -or
            (Test-GlobalPackageKnown -Package $Snapshot.Package)) {
            throw "Run-owned package remains after cleanup: $($Snapshot.Package)"
        }
    }
}

function Test-ProcessPresent {
    param([Parameter(Mandatory)] [string] $ProcessName)

    $result = Invoke-AdbCapture -Arguments @('shell', 'pidof', $ProcessName) `
        -Label "find process $ProcessName" -AllowNonZero
    return $result.ExitCode -eq 0 -and -not [string]::IsNullOrWhiteSpace($result.Text)
}

function Stop-BenchmarkProcesses {
    Invoke-AdbCapture -Arguments @('shell', 'am', 'force-stop', $hostPackage) `
        -Label 'force-stop AutoJs6' | Out-Null
    Invoke-AdbCapture -Arguments @('shell', 'am', 'force-stop', $pluginPackage) `
        -Label 'force-stop DEX compiler plugin' | Out-Null

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($processQuiescenceTimeoutSeconds)
    $consecutiveAbsentPolls = 0
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        $hostPresent = Test-ProcessPresent -ProcessName $hostPackage
        $pluginPresent = Test-ProcessPresent -ProcessName $pluginProcessName
        if (-not $hostPresent -and -not $pluginPresent) {
            $consecutiveAbsentPolls++
            if ($consecutiveAbsentPolls -ge $processQuiescenceRequiredAbsentPolls) { return }
        } else {
            $consecutiveAbsentPolls = 0
            Invoke-AdbCapture -Arguments @('shell', 'am', 'force-stop', $hostPackage) `
                -Label 'repeat force-stop AutoJs6 during process quiescence' | Out-Null
            Invoke-AdbCapture -Arguments @('shell', 'am', 'force-stop', $pluginPackage) `
                -Label 'repeat force-stop DEX compiler plugin during process quiescence' | Out-Null
        }
        Start-Sleep -Milliseconds $processQuiescencePollMilliseconds
    }
    throw "Benchmark processes did not remain absent for $processQuiescenceRequiredAbsentPolls consecutive polls within $processQuiescenceTimeoutSeconds seconds"
}

function Assert-InstrumentationSucceeded {
    param(
        [Parameter(Mandatory)] [int] $ExitCode,
        [Parameter(Mandatory)] [string] $Output,
        [Parameter(Mandatory)] [string] $Label
    )
    if ($ExitCode -ne 0 -or $Output -notmatch '(?m)^OK \(1 test\)\s*$' -or
        $Output -notmatch '(?m)^INSTRUMENTATION_CODE: -1\s*$' -or $Output -match '(?m)^FAILURES!!!') {
        throw "$Label did not complete exactly one passing test (exit=$ExitCode)`n$Output"
    }
}

function Read-InstrumentationStatus {
    param(
        [Parameter(Mandatory)] [string] $Output,
        [Parameter(Mandatory)] [string] $StatusKey,
        [Parameter(Mandatory)] [string] $Label
    )
    $pattern = '(?m)^INSTRUMENTATION_STATUS: ' + [regex]::Escape($StatusKey) + '=(.+)\r?$'
    $matches = @([regex]::Matches($Output, $pattern))
    if ($matches.Count -ne 1) {
        throw "$Label emitted $($matches.Count) instances of $StatusKey, expected exactly one`n$Output"
    }
    try {
        return $matches[0].Groups[1].Value | ConvertFrom-Json -Depth 50
    } catch {
        throw "$Label emitted invalid JSON for $StatusKey`: $($_.Exception.Message)"
    }
}

function New-InstrumentationArguments {
    param(
        [Parameter(Mandatory)] [string] $Method,
        [Parameter(Mandatory)] [string] $Corpus,
        [AllowEmptyString()] [string] $Route = '',
        [AllowEmptyString()] [string] $CacheState = '',
        [int] $SampleIndex = -1
    )
    $arguments = @(
        'shell', 'am', 'instrument', '-w', '-r',
        '-e', $enabledArgument, 'true',
        '-e', $corpusArgument, $Corpus
    )
    if (-not [string]::IsNullOrEmpty($Route)) { $arguments += @('-e', $routeArgument, $Route) }
    if (-not [string]::IsNullOrEmpty($CacheState)) { $arguments += @('-e', $cacheStateArgument, $CacheState) }
    if ($SampleIndex -ge 0) { $arguments += @('-e', $sampleIndexArgument, $SampleIndex.ToString()) }
    $arguments += @('-e', 'class', "$testClass#$Method", $testRunner)
    return $arguments
}

function Invoke-InstrumentationManagement {
    param(
        [Parameter(Mandatory)] [ValidateSet(
            'prepareDeterministicCorpus',
            'primePersistentCache',
            'cleanupPersistentCache',
            'cleanupBenchmarkWorkspace'
        )]
        [string] $Method,
        [Parameter(Mandatory)] [string] $Corpus,
        [AllowEmptyString()] [string] $Route = ''
    )
    Stop-BenchmarkProcesses
    $arguments = New-InstrumentationArguments -Method $Method -Corpus $Corpus -Route $Route
    $result = Invoke-AdbCapture -Arguments $arguments -Label "$Method $Corpus $Route"
    Assert-InstrumentationSucceeded -ExitCode $result.ExitCode -Output $result.Text -Label "$Method $Corpus $Route"
    $statusKey = if ($Method -ceq 'prepareDeterministicCorpus') { $corpusStatusKey } else { $managementStatusKey }
    return Read-InstrumentationStatus -Output $result.Text -StatusKey $statusKey -Label "$Method $Corpus $Route"
}

function Get-ProcessPssKb {
    param([Parameter(Mandatory)] [string] $ProcessName)
    $pidResult = Invoke-AdbCapture -Arguments @('shell', 'pidof', $ProcessName) `
        -Label "find process $ProcessName" -AllowNonZero
    if ($pidResult.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($pidResult.Text)) { return $null }
    $processIds = @(
        $pidResult.Text.Trim() -split '\s+' |
            Where-Object { $_ -match '^\d+$' } |
            Sort-Object -Unique
    )
    if ($processIds.Count -eq 0) { return $null }
    [long] $totalPssKb = 0
    foreach ($processId in $processIds) {
        $result = Invoke-AdbCapture -Arguments @('shell', 'dumpsys', 'meminfo', $processId) `
            -Label "sample PSS for $ProcessName pid $processId" -AllowNonZero
        if ($result.ExitCode -ne 0 -or $result.Text -match '(?i)No process found') { continue }
        $match = [regex]::Match($result.Text, '(?m)^\s*TOTAL\s+([\d,]+)\s+')
        if (-not $match.Success) {
            $match = [regex]::Match($result.Text, '(?m)^\s*TOTAL PSS:\s*([\d,]+)')
        }
        if ($match.Success) { $totalPssKb += [long] ($match.Groups[1].Value -replace ',', '') }
    }
    if ($totalPssKb -eq 0) { return $null }
    return $totalPssKb
}

function Start-AdbProcess {
    param([Parameter(Mandatory)] [string[]] $Arguments)
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $script:Adb
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    foreach ($argument in @('-s', $Serial) + $Arguments) { $startInfo.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) { throw 'Unable to start asynchronous ADB instrumentation process' }
    return [pscustomobject]@{
        Process = $process
        StandardOutput = $process.StandardOutput.ReadToEndAsync()
        StandardError = $process.StandardError.ReadToEndAsync()
    }
}

function Invoke-TimedSample {
    param(
        [Parameter(Mandatory)] [string] $Corpus,
        [Parameter(Mandatory)] [ValidateSet('BUILTIN', 'PLUGIN')] [string] $Route,
        [Parameter(Mandatory)] [ValidateSet('COLD', 'CACHE_HIT')] [string] $CacheState,
        [Parameter(Mandatory)] [int] $SampleIndex,
        [Parameter(Mandatory)] [ValidateSet('WARMUP', 'MEASURED')] [string] $Phase
    )

    Stop-BenchmarkProcesses
    $arguments = New-InstrumentationArguments -Method 'measureSingleProcessColdSample' -Corpus $Corpus `
        -Route $Route -CacheState $CacheState -SampleIndex $SampleIndex
    $startedAt = [DateTimeOffset]::UtcNow
    $started = Start-AdbProcess -Arguments $arguments
    $stopwatch = [Diagnostics.Stopwatch]::StartNew()
    [long] $hostPeak = 0
    [long] $pluginPeak = 0
    [long] $combinedPeak = 0
    [int] $pollCount = 0
    [bool] $hostObserved = $false
    [bool] $pluginObserved = $false
    try {
        while (-not $started.Process.HasExited) {
            if ($stopwatch.Elapsed.TotalSeconds -gt $SampleTimeoutSeconds) {
                $started.Process.Kill($true)
                throw "Timed sample exceeded $SampleTimeoutSeconds seconds: $Corpus $Route $CacheState #$SampleIndex"
            }
            $hostPss = Get-ProcessPssKb -ProcessName $hostPackage
            $pluginPss = Get-ProcessPssKb -ProcessName $pluginProcessName
            $hostValue = if ($null -eq $hostPss) { 0L } else { [long] $hostPss }
            $pluginValue = if ($null -eq $pluginPss) { 0L } else { [long] $pluginPss }
            if ($hostValue -gt 0) { $hostObserved = $true }
            if ($pluginValue -gt 0) { $pluginObserved = $true }
            $hostPeak = [math]::Max($hostPeak, $hostValue)
            $pluginPeak = [math]::Max($pluginPeak, $pluginValue)
            $combinedPeak = [math]::Max($combinedPeak, $hostValue + $pluginValue)
            $pollCount++
            Start-Sleep -Milliseconds $PssPollMilliseconds
        }
        $started.Process.WaitForExit()
        $stdout = $started.StandardOutput.GetAwaiter().GetResult()
        $stderr = $started.StandardError.GetAwaiter().GetResult()
        $output = @($stdout, $stderr | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) -join [Environment]::NewLine
        Assert-InstrumentationSucceeded -ExitCode $started.Process.ExitCode -Output $output `
            -Label "timed sample $Corpus $Route $CacheState #$SampleIndex"
        $deviceStatus = Read-InstrumentationStatus -Output $output -StatusKey $sampleStatusKey `
            -Label "timed sample $Corpus $Route $CacheState #$SampleIndex"
        if (-not $deviceStatus.cacheStateVerified) { throw 'Device fixture did not verify the requested cache state' }
        if (-not $hostObserved) { throw 'External PSS sampler never observed the host process' }
        $providerExpected = $Route -ceq 'PLUGIN'
        if ($pluginObserved -ne $providerExpected) {
            throw "Provider-process observation differs from route expectation: expected=$providerExpected observed=$pluginObserved"
        }
        return [pscustomobject][ordered]@{
            corpusId = $Corpus
            route = $Route
            cacheState = $CacheState
            phase = $Phase
            sampleIndex = $SampleIndex
            startedAtUtc = $startedAt.ToString('o')
            instrumentationWallMillis = $stopwatch.Elapsed.TotalMilliseconds
            instrumentationOutputSha256 = Get-TextSha256 -Value $output
            externalMemory = [ordered]@{
                requestedInterPollDelayMillis = $PssPollMilliseconds
                pollCount = $pollCount
                hostObserved = $hostObserved
                providerObserved = $pluginObserved
                hostPeakPssKb = $hostPeak
                providerPeakPssKb = $pluginPeak
                combinedPeakPssKb = $combinedPeak
            }
            device = $deviceStatus
        }
    } finally {
        if (-not $started.Process.HasExited) {
            $started.Process.Kill($true)
            $started.Process.WaitForExit()
        }
        $started.Process.Dispose()
        Stop-BenchmarkProcesses
    }
}

function Get-Percentile {
    param([Parameter(Mandatory)] [double[]] $Values, [Parameter(Mandatory)] [double] $Probability)
    if ($Values.Count -eq 0) { return $null }
    $sorted = @($Values | Sort-Object)
    if ($sorted.Count -eq 1) { return [double] $sorted[0] }
    $rank = ($sorted.Count - 1) * $Probability
    $lower = [math]::Floor($rank)
    $upper = [math]::Ceiling($rank)
    if ($lower -eq $upper) { return [double] $sorted[$lower] }
    return [double] $sorted[$lower] + ($rank - $lower) * ([double] $sorted[$upper] - [double] $sorted[$lower])
}

function Get-Statistics {
    param([Parameter(Mandatory)] [double[]] $Values)
    if ($Values.Count -eq 0) { return $null }
    $mean = ($Values | Measure-Object -Average).Average
    $sumSquared = 0.0
    foreach ($value in $Values) { $sumSquared += [math]::Pow($value - $mean, 2) }
    return [ordered]@{
        count = $Values.Count
        minimum = ($Values | Measure-Object -Minimum).Minimum
        median = Get-Percentile -Values $Values -Probability 0.5
        p95 = Get-Percentile -Values $Values -Probability 0.95
        maximum = ($Values | Measure-Object -Maximum).Maximum
        mean = $mean
        populationStandardDeviation = [math]::Sqrt($sumSquared / $Values.Count)
        percentileMethod = 'R7_LINEAR_INTERPOLATION'
    }
}

function Get-Summaries {
    param([Parameter(Mandatory)] [AllowEmptyCollection()] [object[]] $Samples)
    $measured = @($Samples | Where-Object { $_.phase -ceq 'MEASURED' })
    $summaries = [Collections.Generic.List[object]]::new()
    foreach ($corpus in $Corpora) {
        foreach ($route in @('BUILTIN', 'PLUGIN')) {
            foreach ($cacheState in @('COLD', 'CACHE_HIT')) {
                $group = @($measured | Where-Object {
                    $_.corpusId -ceq $corpus -and $_.route -ceq $route -and $_.cacheState -ceq $cacheState
                })
                if ($group.Count -eq 0) { continue }
                $verified = @($group | Where-Object { $_.device.cacheStateVerified }).Count
                $summaries.Add([pscustomobject][ordered]@{
                    corpusId = $corpus
                    route = $route
                    cacheState = $cacheState
                    sampleCount = $group.Count
                    cacheStateVerifiedCount = $verified
                    cacheStateVerifiedRate = $verified / [double] $group.Count
                    elapsedMillis = Get-Statistics -Values @($group | ForEach-Object { [double] $_.device.elapsedMillis })
                    hostPeakPssKb = Get-Statistics -Values @($group | ForEach-Object { [double] $_.externalMemory.hostPeakPssKb })
                    providerPeakPssKb = Get-Statistics -Values @($group | ForEach-Object { [double] $_.externalMemory.providerPeakPssKb })
                    combinedPeakPssKb = Get-Statistics -Values @($group | ForEach-Object { [double] $_.externalMemory.combinedPeakPssKb })
                    outputSizeBytes = @($group | ForEach-Object { [long] $_.device.artifact.outputSizeBytes } | Sort-Object -Unique)
                    outputSha256 = @($group | ForEach-Object { $_.device.artifact.outputSha256 } |
                        Where-Object { $null -ne $_ } | Sort-Object -Unique)
                })
            }
        }
    }
    return @($summaries)
}

function Get-Comparisons {
    param([Parameter(Mandatory)] [AllowEmptyCollection()] [object[]] $Summaries)
    $comparisons = [Collections.Generic.List[object]]::new()
    foreach ($corpus in $Corpora) {
        foreach ($cacheState in @('COLD', 'CACHE_HIT')) {
            $builtIn = @($Summaries | Where-Object {
                $_.corpusId -ceq $corpus -and $_.route -ceq 'BUILTIN' -and $_.cacheState -ceq $cacheState
            })
            $plugin = @($Summaries | Where-Object {
                $_.corpusId -ceq $corpus -and $_.route -ceq 'PLUGIN' -and $_.cacheState -ceq $cacheState
            })
            if ($builtIn.Count -ne 1 -or $plugin.Count -ne 1) { continue }
            $latencyBaseline = [double] $builtIn[0].elapsedMillis.p95
            $memoryBaseline = [double] $builtIn[0].combinedPeakPssKb.p95
            $comparisons.Add([pscustomobject][ordered]@{
                corpusId = $corpus
                cacheState = $cacheState
                pluginMinusBuiltInP95Millis = [double] $plugin[0].elapsedMillis.p95 - $latencyBaseline
                pluginVsBuiltInP95LatencyPercent = $(if ($latencyBaseline -eq 0) { $null } else {
                    100.0 * ([double] $plugin[0].elapsedMillis.p95 - $latencyBaseline) / $latencyBaseline
                })
                pluginMinusBuiltInP95CombinedPssKb = [double] $plugin[0].combinedPeakPssKb.p95 - $memoryBaseline
                pluginVsBuiltInP95CombinedPssPercent = $(if ($memoryBaseline -eq 0) { $null } else {
                    100.0 * ([double] $plugin[0].combinedPeakPssKb.p95 - $memoryBaseline) / $memoryBaseline
                })
            })
        }
    }
    return @($comparisons)
}

function Get-BatteryEvidence {
    $result = Invoke-AdbCapture -Arguments @('shell', 'dumpsys', 'battery') -Label 'read battery state'
    function Read-BatteryField([string] $Name) {
        $match = [regex]::Match($result.Text, "(?m)^\s*$([regex]::Escape($Name)):\s*(.+?)\s*$")
        if ($match.Success) { return $match.Groups[1].Value }
        return $null
    }
    $temperature = Read-BatteryField -Name 'temperature'
    return [ordered]@{
        levelPercent = Read-BatteryField -Name 'level'
        status = Read-BatteryField -Name 'status'
        plugged = Read-BatteryField -Name 'plugged'
        temperatureCelsius = $(if ($null -eq $temperature) { $null } else { [double] $temperature / 10.0 })
    }
}

function Get-DeviceEvidence {
    $thermal = Invoke-AdbCapture -Arguments @('shell', 'cmd', 'thermalservice', 'get-current-thermal-status') `
        -Label 'read thermal status' -AllowNonZero
    return [ordered]@{
        exactSerial = $Serial
        manufacturer = Get-DeviceProperty -Name 'ro.product.manufacturer'
        model = Get-DeviceProperty -Name 'ro.product.model'
        apiLevel = [int] (Get-DeviceProperty -Name 'ro.build.version.sdk')
        primaryAbi = Get-DeviceProperty -Name 'ro.product.cpu.abi'
        buildId = Get-DeviceProperty -Name 'ro.build.id'
        securityPatch = Get-DeviceProperty -Name 'ro.build.version.security_patch'
        buildFingerprintSha256 = Get-TextSha256 -Value (Get-DeviceProperty -Name 'ro.build.fingerprint')
        battery = Get-BatteryEvidence
        thermalStatusRaw = $(if ($thermal.ExitCode -eq 0) { $thermal.Text.Trim() } else { $null })
    }
}

$script:Adb = Resolve-Executable -Value $AdbPath -Label 'adb'
$script:Aapt2 = if ([string]::IsNullOrWhiteSpace($Aapt2Path)) {
    Resolve-SdkBuildTool -Name 'aapt2.exe'
} else {
    Resolve-ExactFile -Path $Aapt2Path -Label 'aapt2'
}
$script:ApkSigner = if ([string]::IsNullOrWhiteSpace($ApkSignerPath)) {
    Resolve-SdkBuildTool -Name 'apksigner.bat'
} else {
    Resolve-ExactFile -Path $ApkSignerPath -Label 'apksigner'
}

$resolvedHostApk = Resolve-ExactFile -Path $HostApk -Label 'host APK'
$resolvedHostTestApk = Resolve-ExactFile -Path $HostTestApk -Label 'host androidTest APK'
$resolvedPluginApk = Resolve-ExactFile -Path $PluginApk -Label 'plugin APK'
$hostArtifact = Get-ApkMetadata -Path $resolvedHostApk -ExpectedPackage $hostPackage -Label 'AutoJs6 host'
$hostTestArtifact = Get-ApkMetadata -Path $resolvedHostTestApk -ExpectedPackage $hostTestPackage -Label 'AutoJs6 androidTest'
$pluginArtifact = Get-ApkMetadata -Path $resolvedPluginApk -ExpectedPackage $pluginPackage -Label 'DEX compiler plugin'
$signers = @(@(
    $hostArtifact.signerCertificateSha256,
    $hostTestArtifact.signerCertificateSha256,
    $pluginArtifact.signerCertificateSha256
) | Sort-Object -Unique)
if ($signers.Count -ne 1) { throw 'Host, androidTest, and plugin APK signer certificates differ' }
if ($pluginArtifact.debuggable) { throw 'The R5.2 benchmark requires a non-debuggable plugin APK' }

$deviceList = Invoke-NativeCapture -Executable $script:Adb -Arguments @('devices') -Label 'enumerate ADB devices'
$deviceMatches = @($deviceList.Lines | Where-Object { $_ -match ('^' + [regex]::Escape($Serial) + '\s+device\s*$') })
if ($deviceMatches.Count -ne 1) { throw "Exact ADB device is not online: $Serial`n$($deviceList.Text)" }
$currentUser = (Invoke-AdbCapture -Arguments @('shell', 'am', 'get-current-user') -Label 'read current Android user').Text.Trim()
if ($currentUser -cne '0') { throw "R5.2 benchmark requires current Android user 0; observed $currentUser" }
$initialDevice = Get-DeviceEvidence
if ($initialDevice.apiLevel -lt 24 -or $initialDevice.apiLevel -gt 36) {
    throw "Device API is outside the R5.2 contract: $($initialDevice.apiLevel)"
}
if (@($hostArtifact.nativeAbis).Count -gt 0 -and $initialDevice.primaryAbi -notin @($hostArtifact.nativeAbis)) {
    throw "Host APK does not contain device ABI $($initialDevice.primaryAbi)"
}

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $timestamp = [DateTimeOffset]::UtcNow.ToString('yyyyMMddTHHmmssZ')
    $OutputPath = Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..\..\docs\dev')).ProviderPath `
        "dex-compiler-r5-performance-$timestamp.json"
}
$resolvedOutput = [IO.Path]::GetFullPath($OutputPath)
if ([IO.File]::Exists($resolvedOutput) -and -not $Overwrite) {
    throw "Output report already exists; pass -Overwrite explicitly: $resolvedOutput"
}
$outputDirectory = Split-Path -Parent $resolvedOutput
if (-not [IO.Directory]::Exists($outputDirectory)) {
    [IO.Directory]::CreateDirectory($outputDirectory) | Out-Null
}

$tempBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$snapshotRoot = [IO.Path]::GetFullPath((Join-Path $tempBase "autojs-dex-r5-$([guid]::NewGuid())"))
if (-not $snapshotRoot.StartsWith($tempBase, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Generated package-snapshot path escaped the system temporary directory'
}
[IO.Directory]::CreateDirectory($snapshotRoot) | Out-Null

$campaignId = [guid]::NewGuid().ToString()
$startedAtUtc = [DateTimeOffset]::UtcNow
$samples = [Collections.Generic.List[object]]::new()
$corpusEvidence = [Collections.Generic.List[object]]::new()
$managementEvidence = [Collections.Generic.List[object]]::new()
$packageSnapshots = [Collections.Generic.List[object]]::new()
$preparedCorpora = [Collections.Generic.List[string]]::new()
$cleanupErrors = [Collections.Generic.List[string]]::new()
$restorationErrors = [Collections.Generic.List[string]]::new()
$runError = $null
$finalDevice = $null
$benchmarkPackagesInstalled = $false
$recoverySnapshotPath = $null
$status = 'RUNNING'
$expectedSamples = $Corpora.Count * 4 * ($WarmupSamples + $MeasuredSamples)
$completedSamples = 0

try {
    foreach ($package in @($hostPackage, $hostTestPackage, $pluginPackage)) {
        $packageSnapshots.Add((New-PackageSnapshot -Package $package -SnapshotRoot $snapshotRoot))
    }
    Install-BenchmarkApk -Artifact $hostArtifact
    Install-BenchmarkApk -Artifact $hostTestArtifact
    Install-BenchmarkApk -Artifact $pluginArtifact
    $benchmarkPackagesInstalled = $true

    foreach ($corpus in $Corpora) {
        Write-Host "Preparing deterministic corpus $corpus"
        $corpusEvidence.Add((Invoke-InstrumentationManagement -Method prepareDeterministicCorpus -Corpus $corpus))
        $preparedCorpora.Add($corpus)
        foreach ($route in @('BUILTIN', 'PLUGIN')) {
            $managementEvidence.Add((Invoke-InstrumentationManagement -Method cleanupPersistentCache `
                -Corpus $corpus -Route $route))
        }

        $sampleCountPerMode = $WarmupSamples + $MeasuredSamples
        for ($index = 0; $index -lt $sampleCountPerMode; $index++) {
            $phase = if ($index -lt $WarmupSamples) { 'WARMUP' } else { 'MEASURED' }
            $routes = if ($index % 2 -eq 0) { @('BUILTIN', 'PLUGIN') } else { @('PLUGIN', 'BUILTIN') }
            foreach ($route in $routes) {
                $completedSamples++
                Write-Host "[$completedSamples/$expectedSamples] $corpus $route COLD $phase #$index"
                $samples.Add((Invoke-TimedSample -Corpus $corpus -Route $route -CacheState COLD `
                    -SampleIndex $index -Phase $phase))
                if ($BetweenSamplesSeconds -gt 0) { Start-Sleep -Seconds $BetweenSamplesSeconds }
            }
        }

        foreach ($route in @('BUILTIN', 'PLUGIN')) {
            $managementEvidence.Add((Invoke-InstrumentationManagement -Method cleanupPersistentCache `
                -Corpus $corpus -Route $route))
            $managementEvidence.Add((Invoke-InstrumentationManagement -Method primePersistentCache `
                -Corpus $corpus -Route $route))
        }

        for ($index = 0; $index -lt $sampleCountPerMode; $index++) {
            $phase = if ($index -lt $WarmupSamples) { 'WARMUP' } else { 'MEASURED' }
            $routes = if ($index % 2 -eq 0) { @('PLUGIN', 'BUILTIN') } else { @('BUILTIN', 'PLUGIN') }
            foreach ($route in $routes) {
                $completedSamples++
                Write-Host "[$completedSamples/$expectedSamples] $corpus $route CACHE_HIT $phase #$index"
                $samples.Add((Invoke-TimedSample -Corpus $corpus -Route $route -CacheState CACHE_HIT `
                    -SampleIndex $index -Phase $phase))
                if ($BetweenSamplesSeconds -gt 0) { Start-Sleep -Seconds $BetweenSamplesSeconds }
            }
        }

        foreach ($route in @('BUILTIN', 'PLUGIN')) {
            $managementEvidence.Add((Invoke-InstrumentationManagement -Method cleanupPersistentCache `
                -Corpus $corpus -Route $route))
        }
    }
    if ($samples.Count -ne $expectedSamples) {
        throw "Completed sample count differs: $($samples.Count) != $expectedSamples"
    }
    $status = 'PASS'
} catch {
    $runError = $_
    $status = 'FAIL'
    Write-Warning "Benchmark run failed: $($_.Exception.Message)"
} finally {
    foreach ($corpus in @($preparedCorpora)) {
        foreach ($route in @('BUILTIN', 'PLUGIN')) {
            try {
                $managementEvidence.Add((Invoke-InstrumentationManagement -Method cleanupPersistentCache `
                    -Corpus $corpus -Route $route))
            } catch {
                $cleanupErrors.Add("$corpus/$route`: $($_.Exception.Message)")
            }
        }
    }
    if ($benchmarkPackagesInstalled) {
        try {
            $managementEvidence.Add((Invoke-InstrumentationManagement -Method cleanupBenchmarkWorkspace `
                -Corpus $Corpora[0]))
        } catch {
            $cleanupErrors.Add("workspace: $($_.Exception.Message)")
        }
    }
    try { Stop-BenchmarkProcesses } catch { $cleanupErrors.Add("force-stop: $($_.Exception.Message)") }
    $restorationOrder = @($packageSnapshots)
    [array]::Reverse($restorationOrder)
    foreach ($snapshot in $restorationOrder) {
        try {
            Write-Host "Restoring initial package state for $($snapshot.Package)"
            Restore-PackageSnapshot -Snapshot $snapshot
        } catch {
            $restorationMessage = "$($snapshot.Package): $($_.Exception.Message)"
            $restorationErrors.Add($restorationMessage)
            Write-Warning $restorationMessage
        }
    }
    try { $finalDevice = Get-DeviceEvidence } catch { $cleanupErrors.Add("final-device: $($_.Exception.Message)") }
    if ([IO.Directory]::Exists($snapshotRoot)) {
        $verifiedSnapshotRoot = [IO.Path]::GetFullPath($snapshotRoot)
        if (-not $verifiedSnapshotRoot.StartsWith($tempBase, [StringComparison]::OrdinalIgnoreCase)) {
            $restorationErrors.Add('Refused to remove package snapshot outside the system temporary directory')
        } elseif ($restorationErrors.Count -gt 0) {
            $recoverySnapshotPath = $verifiedSnapshotRoot
        } else {
            Remove-Item -LiteralPath $verifiedSnapshotRoot -Recurse -Force
        }
    }
}

$summaries = @(Get-Summaries -Samples @($samples))
$comparisons = @(Get-Comparisons -Summaries $summaries)
$javaVersion = Invoke-NativeCapture -Executable (Resolve-Executable -Value 'java' -Label 'java') `
    -Arguments @('-version') -Label 'read Java version'
$adbVersion = Invoke-NativeCapture -Executable $script:Adb -Arguments @('version') -Label 'read ADB version'
$restorationStatus = if ($restorationErrors.Count -eq 0 -and $packageSnapshots.Count -eq 3) {
    'EXACT_INITIAL_APK_BYTES_RESTORED_OR_RUN_OWNED_PACKAGE_REMOVED'
} else {
    'NOT_VERIFIED'
}
if ($restorationErrors.Count -gt 0) { $status = 'BLOCKED' }
elseif ($cleanupErrors.Count -gt 0 -and $status -ceq 'PASS') { $status = 'FAIL' }

$report = [ordered]@{
    schema = 'autojs.dex-compiler.r5-performance/v1'
    campaignId = $campaignId
    status = $status
    benchmarkClassification = $(if ($MeasuredSamples -ge 10) { 'R5_BASELINE' } else { 'EXPLORATORY_SMOKE' })
    startedAtUtc = $startedAtUtc.ToString('o')
    finishedAtUtc = [DateTimeOffset]::UtcNow.ToString('o')
    contract = [ordered]@{
        timingBoundary = 'LOAD_JAR_THROUGH_CLASS_RESOLUTION_AND_STATIC_INVOCATION'
        processState = 'HOST_AND_PROVIDER_FORCE_STOPPED_BEFORE_EVERY_TIMED_SAMPLE'
        processQuiescence = 'THREE_CONSECUTIVE_ABSENT_PID_POLLS_AFTER_FORCE_STOP'
        processQuiescencePollMillis = $processQuiescencePollMilliseconds
        processQuiescenceTimeoutSeconds = $processQuiescenceTimeoutSeconds
        corpusGenerator = 'ON_DEVICE_DETERMINISTIC_JAVA8_CLASSFILE_V1'
        routes = @('BUILTIN', 'PLUGIN')
        cacheStates = @('COLD', 'CACHE_HIT')
        warmupSamplesPerCell = $WarmupSamples
        measuredSamplesPerCell = $MeasuredSamples
        requestedInterPssPollDelayMillis = $PssPollMilliseconds
        betweenSamplesSeconds = $BetweenSamplesSeconds
        sampleOrder = 'PAIRWISE_ALTERNATING_ROUTE_ORDER_PER_CORPUS_AND_CACHE_STATE'
        coldPluginCleanup = 'EXACT_TRACKED_GENERATION_INVALIDATED_AFTER_TIMED_LOAD_WITHOUT_UNTIMED_RECOMPILATION'
        providerProcessExpectation = 'OBSERVED_FOR_PROCESS_COLD_PLUGIN_ROUTE_IN_BOTH_CACHE_STATES_BECAUSE_CACHE_KEY_FINALIZATION_IS_CAPABILITY_BOUND'
        percentileMethod = 'R7_LINEAR_INTERPOLATION'
    }
    environment = [ordered]@{
        hostOs = [Runtime.InteropServices.RuntimeInformation]::OSDescription
        powershellVersion = $PSVersionTable.PSVersion.ToString()
        javaVersion = $javaVersion.Text
        adbVersion = $adbVersion.Text
        adbPath = $script:Adb
        aapt2Path = $script:Aapt2
        apkSignerPath = $script:ApkSigner
        deviceBefore = $initialDevice
        deviceAfter = $finalDevice
    }
    artifacts = @($hostArtifact, $hostTestArtifact, $pluginArtifact)
    initialPackageState = @($packageSnapshots | ForEach-Object { Get-PackageSnapshotEvidence -Snapshot $_ })
    packageRestoration = $restorationStatus
    recoverySnapshotPath = $recoverySnapshotPath
    corpora = @($corpusEvidence)
    management = @($managementEvidence)
    expectedTimedSamples = $expectedSamples
    completedTimedSamples = $samples.Count
    samples = @($samples)
    summaries = $summaries
    comparisons = $comparisons
    claims = [ordered]@{
        performanceAdvantageClaimed = $false
        defaultEnableAuthorized = $false
        statisticalInferenceClaimed = $false
        note = 'This report records repeated descriptive measurements; promotion thresholds are evaluated separately.'
    }
    error = $(if ($null -eq $runError) { $null } else { $runError.Exception.Message })
    cleanupErrors = @($cleanupErrors)
    restorationErrors = @($restorationErrors)
}

$json = $report | ConvertTo-Json -Depth 30
$temporaryReport = "$resolvedOutput.$([guid]::NewGuid()).partial"
try {
    [IO.File]::WriteAllText($temporaryReport, $json, [Text.UTF8Encoding]::new($false))
    if ([IO.File]::Exists($resolvedOutput)) {
        if (-not $Overwrite) { throw "Output report appeared during the run: $resolvedOutput" }
        [IO.File]::Delete($resolvedOutput)
    }
    [IO.File]::Move($temporaryReport, $resolvedOutput)
} finally {
    if ([IO.File]::Exists($temporaryReport)) { [IO.File]::Delete($temporaryReport) }
}

Write-Host "R5.2 performance report: $resolvedOutput"
Write-Host "Status: $status; samples: $($samples.Count)/$expectedSamples; restoration: $restorationStatus"
if ($status -cne 'PASS') {
    if ($null -ne $runError) { Write-Error $runError.Exception.Message }
    exit 1
}
