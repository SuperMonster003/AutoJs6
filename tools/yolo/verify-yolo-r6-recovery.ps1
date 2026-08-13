[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $DeviceSerial,

    [Parameter(Mandatory = $true)]
    [ValidateSet("representative-arm64-physical", "current-api-arm64-emulator")]
    [string] $EnvironmentKind,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $ProviderApk,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $HostApk,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $InstrumentationApk,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $OfficialProductionSignerSha256,

    [Parameter(Mandatory = $true)]
    [ValidateRange(1, [long]::MaxValue)]
    [long] $ExpectedProviderVersionCode,

    [string] $SiblingPath = "D:\idea-projects\AutoJs6-Plugin-Yolo-NCNN",

    [string] $FixtureRoot,

    [switch] $Qv710SoakConfirmedIdle
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$protectedSerial = "QV710AF65F"
$hostPackage = "org.autojs.autojs6"
$providerPackage = "io.github.supermonster003.autojs6.plugin.yolo.ncnn"
$testPackage = "$hostPackage.test"
$runner = "androidx.test.runner.AndroidJUnitRunner"
$testClass = "org.autojs.autojs.core.plugin.yolo.YoloRealProviderR6RecoveryAndroidTest"
$testMethod = "providerDeathFailsPinnedSessionWithoutRetryThenExplicitReopenSucceeds"
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$reportDirectory = Join-Path $repositoryRoot "build\reports\yolo"

function Assert-YoloR6RecoveryCondition {
    param(
        [Parameter(Mandatory = $true)][bool] $Condition,
        [Parameter(Mandatory = $true)][string] $Message
    )
    if (-not $Condition) { throw $Message }
}

function Assert-YoloR6RecoveryDeviceAccess {
    if ($DeviceSerial.Equals($protectedSerial, [System.StringComparison]::OrdinalIgnoreCase)) {
        Assert-YoloR6RecoveryCondition ([bool]$Qv710SoakConfirmedIdle) (
            "Using $protectedSerial requires explicit -Qv710SoakConfirmedIdle after confirming no soak or competing device work"
        )
    } else {
        Assert-YoloR6RecoveryCondition (-not [bool]$Qv710SoakConfirmedIdle) (
            "-Qv710SoakConfirmedIdle is valid only for $protectedSerial"
        )
    }

    $processes = @(Get-CimInstance Win32_Process -ErrorAction Stop)
    $byPid = @{}
    foreach ($process in $processes) { $byPid[[int]$process.ProcessId] = $process }
    $excluded = [System.Collections.Generic.HashSet[int]]::new()
    $cursor = $PID
    while ($cursor -gt 0 -and $excluded.Add([int]$cursor)) {
        $row = $byPid[[int]$cursor]
        if ($null -eq $row) { break }
        $cursor = [int]$row.ParentProcessId
    }
    $serialPattern = [regex]::Escape($DeviceSerial)
    $runnerPattern = '(?i)(soak|endurance|long[-_ ]?run|stress[-_ ]?run|am\s+instrument|connected[a-z0-9_-]*androidtest|verify[-_a-z0-9]*r[0-9]+|adb(?:\.exe)?\s+-s)'
    $matches = @($processes | Where-Object {
        -not $excluded.Contains([int]$_.ProcessId) -and
        -not [string]::IsNullOrWhiteSpace($_.CommandLine) -and
        $_.CommandLine -match $serialPattern -and
        $_.CommandLine -match $runnerPattern
    })
    Assert-YoloR6RecoveryCondition ($matches.Count -eq 0) (
        "Refusing device work because competing runner process IDs reference $DeviceSerial`: " +
        (($matches | ForEach-Object { $_.ProcessId }) -join ', ')
    )
}

function Get-YoloR6RecoveryRequiredFile {
    param([string] $Path, [string] $Label)
    $fullPath = [System.IO.Path]::GetFullPath($Path)
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) { throw "$Label is missing: $fullPath" }
    return $fullPath
}

function Get-YoloR6RecoveryRequiredDirectory {
    param([string] $Path, [string] $Label)
    $fullPath = [System.IO.Path]::GetFullPath($Path)
    if (-not (Test-Path -LiteralPath $fullPath -PathType Container)) { throw "$Label is missing: $fullPath" }
    return $fullPath
}

function Get-YoloR6RecoverySha256 {
    param([Parameter(Mandatory = $true)][string] $Path)
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Invoke-YoloR6RecoveryExternal {
    param(
        [Parameter(Mandatory = $true)][string] $FilePath,
        [string[]] $Arguments = @(),
        [switch] $Quiet
    )
    if (-not $Quiet) { Write-Host "> $FilePath $($Arguments -join ' ')" }
    $output = @(& $FilePath @Arguments 2>&1)
    $exitCode = $LASTEXITCODE
    if (-not $Quiet) { $output | ForEach-Object { Write-Host $_ } }
    if ($exitCode -ne 0) { throw "External command failed with exit code $exitCode`: $FilePath" }
    return @($output | ForEach-Object { [string]$_ })
}

function Invoke-YoloR6RecoveryAdb {
    param([string] $Adb, [string[]] $Arguments = @(), [switch] $Quiet)
    return Invoke-YoloR6RecoveryExternal -FilePath $Adb -Arguments (@("-s", $DeviceSerial) + $Arguments) -Quiet:$Quiet
}

function Invoke-YoloR6RecoveryInstrumentation {
    param(
        [Parameter(Mandatory = $true)][string] $Adb,
        [Parameter(Mandatory = $true)][string] $FixtureRoot
    )
    $arguments = @(
        "-s", $DeviceSerial,
        "shell", "am", "instrument", "-w", "-r",
        "-e", "yoloR6RecoveryEnabled", "true",
        "-e", "yoloR6FixtureRoot", $FixtureRoot,
        "-e", "class", "$testClass#$testMethod",
        "$testPackage/$runner"
    )
    $timeoutMillis = 10 * 60 * 1000
    $display = "$Adb $($arguments -join ' ')"
    Write-Host "> $display"
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $Adb
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    foreach ($argument in $arguments) { $null = $startInfo.ArgumentList.Add($argument) }
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    try {
        Assert-YoloR6RecoveryCondition ($process.Start()) "Could not start R6 instrumentation: $display"
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($timeoutMillis)) {
            try { $process.Kill($true) } catch { }
            $process.WaitForExit()
            foreach ($packageName in @($testPackage, $hostPackage, $providerPackage)) {
                try {
                    $null = Invoke-YoloR6RecoveryAdb -Adb $Adb -Arguments @(
                        "shell", "am", "force-stop", $packageName
                    ) -Quiet
                } catch { }
            }
            throw "$testClass#$testMethod exceeded its $timeoutMillis ms wall-clock limit"
        }
        $process.WaitForExit()
        $stdout = $stdoutTask.GetAwaiter().GetResult()
        $stderr = $stderrTask.GetAwaiter().GetResult()
        $text = @($stdout, $stderr) |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
            ForEach-Object { $_.TrimEnd() } |
            Join-String -Separator "`n"
        if (-not [string]::IsNullOrWhiteSpace($text)) { Write-Host $text }
        Assert-YoloR6RecoveryCondition ($process.ExitCode -eq 0) (
            "$testClass#$testMethod instrumentation exited with $($process.ExitCode)"
        )
        Assert-YoloR6RecoveryCondition ($text -match '(?m)^OK \(1 test\)\s*$') (
            "R6 recovery instrumentation did not pass exactly one test"
        )
        Assert-YoloR6RecoveryCondition (
            $text -notmatch '(?im)^(FAILURES!!!|INSTRUMENTATION_FAILED:)|Process crashed|AssumptionViolated|INSTRUMENTATION_STATUS_CODE:\s*-3\s*$'
        ) "R6 recovery instrumentation reported failure, crash, or skipped assumption"
        return [pscustomobject][ordered]@{
            text = $text
            wallClockTimeoutMillis = $timeoutMillis
        }
    } finally {
        $process.Dispose()
    }
}

function Get-YoloR6RecoverySdkRoots {
    param([string[]] $RepositoryRoots)
    $candidates = [System.Collections.Generic.List[string]]::new()
    foreach ($root in $RepositoryRoots) {
        $properties = Join-Path $root "local.properties"
        if (Test-Path -LiteralPath $properties -PathType Leaf) {
            $line = Get-Content -LiteralPath $properties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
            if ($null -ne $line) {
                $value = ($line -replace '^sdk\.dir=', '') -replace '\\:', ':' -replace '\\\\', '\'
                if (-not [string]::IsNullOrWhiteSpace($value)) { $candidates.Add($value.Trim()) }
            }
        }
    }
    foreach ($name in @("ANDROID_SDK_ROOT", "ANDROID_HOME")) {
        $value = [Environment]::GetEnvironmentVariable($name)
        if (-not [string]::IsNullOrWhiteSpace($value)) { $candidates.Add($value.Trim()) }
    }
    return @($candidates | ForEach-Object { [System.IO.Path]::GetFullPath($_) } |
        Where-Object { Test-Path -LiteralPath $_ -PathType Container } | Select-Object -Unique)
}

function Resolve-YoloR6RecoveryTools {
    param([string[]] $SdkRoots)
    foreach ($sdk in $SdkRoots) {
        $adb = Join-Path $sdk "platform-tools\adb.exe"
        if (-not (Test-Path -LiteralPath $adb -PathType Leaf)) { continue }
        $buildTools = Get-ChildItem -LiteralPath (Join-Path $sdk "build-tools") -Directory -ErrorAction SilentlyContinue |
            Sort-Object { [version]($_.Name -replace '-.*$', '') } -Descending
        foreach ($directory in $buildTools) {
            $apkSigner = Join-Path $directory.FullName "apksigner.bat"
            $aapt2 = Join-Path $directory.FullName "aapt2.exe"
            if ((Test-Path -LiteralPath $apkSigner -PathType Leaf) -and (Test-Path -LiteralPath $aapt2 -PathType Leaf)) {
                return [pscustomobject]@{ adb = $adb; apkSigner = $apkSigner; aapt2 = $aapt2; sdkRoot = $sdk }
            }
        }
    }
    throw "Unable to resolve adb, apksigner, and aapt2 from one Android SDK"
}

function Get-YoloR6RecoverySigner {
    param([string] $ApkSigner, [string] $Apk, [string] $Label)
    $text = (Invoke-YoloR6RecoveryExternal -FilePath $ApkSigner -Arguments @("verify", "--verbose", "--print-certs", $Apk) -Quiet) -join "`n"
    $count = [regex]::Match($text, '(?im)^Number of signers:\s*([0-9]+)\s*$')
    Assert-YoloR6RecoveryCondition ($count.Success -and [int]$count.Groups[1].Value -eq 1) "$Label must have exactly one signer"
    $digests = @([regex]::Matches($text, '(?im)certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$') |
        ForEach-Object { $_.Groups[1].Value.ToLowerInvariant() } | Select-Object -Unique)
    Assert-YoloR6RecoveryCondition ($digests.Count -eq 1) "$Label must expose exactly one signer SHA-256"
    return $digests[0]
}

function Get-YoloR6RecoveryApplicationId {
    param([string] $Aapt2, [string] $Apk, [string] $Label)
    $text = (Invoke-YoloR6RecoveryExternal -FilePath $Aapt2 -Arguments @("dump", "badging", $Apk) -Quiet) -join "`n"
    $match = [regex]::Match($text, "(?m)^package: name='([^']+)'")
    Assert-YoloR6RecoveryCondition $match.Success "$Label has no package identity"
    return $match.Groups[1].Value
}

function Test-YoloR6RecoveryPackageInstalled {
    param([string] $Adb, [string] $PackageName)
    $output = @(& $Adb -s $DeviceSerial shell pm path $PackageName 2>&1)
    $code = $LASTEXITCODE
    if ($code -eq 0) { return (($output -join "`n") -match '(?m)^package:') }
    throw "Unable to query package $PackageName on $DeviceSerial"
}

Assert-YoloR6RecoveryCondition ($DeviceSerial -cmatch '^[A-Za-z0-9._:-]+$') "Unsafe Android serial"
Assert-YoloR6RecoveryDeviceAccess

$siblingRoot = Get-YoloR6RecoveryRequiredDirectory -Path $SiblingPath -Label "YOLO Provider sibling"
if ([string]::IsNullOrWhiteSpace($FixtureRoot)) { $FixtureRoot = Join-Path $siblingRoot "fixtures\local\yolo11n" }
$fixtureRootPath = Get-YoloR6RecoveryRequiredDirectory -Path $FixtureRoot -Label "local-only YOLO11n fixture"
$providerApkPath = Get-YoloR6RecoveryRequiredFile -Path $ProviderApk -Label "Provider production APK"
$hostApkPath = Get-YoloR6RecoveryRequiredFile -Path $HostApk -Label "Host production APK"
$testApkPath = Get-YoloR6RecoveryRequiredFile -Path $InstrumentationApk -Label "production-signed instrumentation APK"
$expectedSigner = $OfficialProductionSignerSha256.Trim().ToLowerInvariant()
Assert-YoloR6RecoveryCondition ($expectedSigner -cmatch '^[0-9a-f]{64}$') "Official production signer must be one SHA-256 digest"

$publishableVerifier = Get-YoloR6RecoveryRequiredFile -Path (Join-Path $PSScriptRoot "verify-yolo-r6-publishable.ps1") -Label "R6 publishable verifier"
& $publishableVerifier -ProviderApk $providerApkPath -HostApk $hostApkPath -OfficialProductionSignerSha256 $expectedSigner -ExpectedProviderVersionCode $ExpectedProviderVersionCode -SiblingPath $siblingRoot

$tools = Resolve-YoloR6RecoveryTools -SdkRoots @(Get-YoloR6RecoverySdkRoots -RepositoryRoots @($repositoryRoot, $siblingRoot))
Assert-YoloR6RecoveryCondition ((Get-YoloR6RecoveryApplicationId -Aapt2 $tools.aapt2 -Apk $testApkPath -Label "Instrumentation") -ceq $testPackage) (
    "Instrumentation APK package identity is unexpected"
)
Assert-YoloR6RecoveryCondition ((Get-YoloR6RecoverySigner -ApkSigner $tools.apkSigner -Apk $testApkPath -Label "Instrumentation") -ceq $expectedSigner) (
    "Instrumentation APK is not signed by the approved production certificate"
)

$lockPath = Get-YoloR6RecoveryRequiredFile -Path (Join-Path $siblingRoot "fixtures\yolo11n\fixture.lock.json") -Label "YOLO11n fixture lock"
$lock = Get-Content -Raw -LiteralPath $lockPath | ConvertFrom-Json
$fixtureNames = @("model-manifest-v1.json", "model.ncnn.param", "model.ncnn.bin", "bus.jpg")
$fixtureEvidence = [ordered]@{}
foreach ($name in $fixtureNames) {
    $file = Get-YoloR6RecoveryRequiredFile -Path (Join-Path $fixtureRootPath $name) -Label "R6 fixture $name"
    $entry = @($lock.artifacts | Where-Object { [string]$_.name -ceq $name })
    Assert-YoloR6RecoveryCondition ($entry.Count -eq 1) "Fixture lock must contain exactly one $name"
    $length = (Get-Item -LiteralPath $file).Length
    $sha = Get-YoloR6RecoverySha256 -Path $file
    Assert-YoloR6RecoveryCondition ($length -eq [long]$entry[0].length) "$name length differs from its lock"
    Assert-YoloR6RecoveryCondition ($sha -ceq ([string]$entry[0].sha256).ToLowerInvariant()) "$name SHA-256 differs from its lock"
    $fixtureEvidence[$name] = [ordered]@{ length = $length; sha256 = $sha }
}

$deviceOutput = (Invoke-YoloR6RecoveryExternal -FilePath $tools.adb -Arguments @("devices") -Quiet) -join "`n"
$deviceMatch = [regex]::Match($deviceOutput, "(?m)^$([regex]::Escape($DeviceSerial))\s+(\S+)\s*$")
Assert-YoloR6RecoveryCondition ($deviceMatch.Success -and $deviceMatch.Groups[1].Value -ceq "device") "Requested device is not uniquely online"
$abiList = ((Invoke-YoloR6RecoveryAdb -Adb $tools.adb -Arguments @("shell", "getprop", "ro.product.cpu.abilist") -Quiet) -join "").Trim()
Assert-YoloR6RecoveryCondition (@($abiList.Split(',')) -contains "arm64-v8a") "R6 recovery requires an arm64-v8a-capable Android environment"
$apiLevel = [int](((Invoke-YoloR6RecoveryAdb -Adb $tools.adb -Arguments @("shell", "getprop", "ro.build.version.sdk") -Quiet) -join "").Trim())
$model = ((Invoke-YoloR6RecoveryAdb -Adb $tools.adb -Arguments @("shell", "getprop", "ro.product.model") -Quiet) -join "").Trim()
$pageSize = ((Invoke-YoloR6RecoveryAdb -Adb $tools.adb -Arguments @("shell", "getconf", "PAGESIZE") -Quiet) -join "").Trim()
$isEmulator = ((Invoke-YoloR6RecoveryAdb -Adb $tools.adb -Arguments @("shell", "getprop", "ro.kernel.qemu") -Quiet) -join "").Trim() -ceq "1"
if ($EnvironmentKind -ceq "representative-arm64-physical") {
    Assert-YoloR6RecoveryCondition (-not $isEmulator) "The representative arm64 environment must be a physical device"
} else {
    Assert-YoloR6RecoveryCondition $isEmulator "The current API environment must be an emulator"
    Assert-YoloR6RecoveryCondition ($apiLevel -eq 36) "The current API emulator must run the pinned compile/target API 36"
}
$activeInstrumentation = (Invoke-YoloR6RecoveryAdb -Adb $tools.adb -Arguments @("shell", "dumpsys", "activity", "instrumentation") -Quiet) -join "`n"
Assert-YoloR6RecoveryCondition ($activeInstrumentation -notmatch '(?m)^\s*Active instrumentation') "Another instrumentation runner is active on $DeviceSerial"

foreach ($packageName in @($testPackage, $providerPackage, $hostPackage)) {
    Assert-YoloR6RecoveryCondition (-not (Test-YoloR6RecoveryPackageInstalled -Adb $tools.adb -PackageName $packageName)) (
        "R6 refuses to replace a pre-existing package: $packageName"
    )
}

$staging = "/data/local/tmp/autojs6-yolo-r6-$([guid]::NewGuid().ToString('N'))"
$cleanupErrors = [System.Collections.Generic.List[string]]::new()
$instrumentationText = ""
$instrumentationTimeoutMillis = 0
$resultJson = $null
try {
    Invoke-YoloR6RecoveryAdb -Adb $tools.adb -Arguments @("shell", "mkdir", "-p", $staging) | Out-Null
    foreach ($name in $fixtureNames) {
        Invoke-YoloR6RecoveryAdb -Adb $tools.adb -Arguments @("push", (Join-Path $fixtureRootPath $name), "$staging/$name") | Out-Null
    }
    Invoke-YoloR6RecoveryAdb -Adb $tools.adb -Arguments @("install", "-r", $hostApkPath) | Out-Null
    Invoke-YoloR6RecoveryAdb -Adb $tools.adb -Arguments @("install", "-r", $providerApkPath) | Out-Null
    Invoke-YoloR6RecoveryAdb -Adb $tools.adb -Arguments @("install", "-r", "-t", $testApkPath) | Out-Null

    $instrumentationRun = Invoke-YoloR6RecoveryInstrumentation -Adb $tools.adb -FixtureRoot $staging
    $instrumentationText = $instrumentationRun.text
    $instrumentationTimeoutMillis = $instrumentationRun.wallClockTimeoutMillis
    $resultMatch = [regex]::Matches($instrumentationText, '(?m)^INSTRUMENTATION_STATUS: yoloR6RecoveryResult=(\{.*\})\s*$')
    Assert-YoloR6RecoveryCondition ($resultMatch.Count -eq 1) "R6 recovery instrumentation emitted no unique structured result"
    $resultJson = $resultMatch[0].Groups[1].Value | ConvertFrom-Json
    Assert-YoloR6RecoveryCondition ([string]$resultJson.status -ceq "PASS") "R6 recovery structured result did not pass"
    Assert-YoloR6RecoveryCondition ([bool]$resultJson.oldSessionClosed) "Old session did not close after Provider death"
    Assert-YoloR6RecoveryCondition (-not [bool]$resultJson.dispatchRetryAfterDeath) "Host retried after Provider death"
    Assert-YoloR6RecoveryCondition ([bool]$resultJson.explicitReopenSucceeded) "Explicit reopen failed"
} finally {
    if ($staging -cmatch '^/data/local/tmp/autojs6-yolo-r6-[a-f0-9]{32}$') {
        try { Invoke-YoloR6RecoveryAdb -Adb $tools.adb -Arguments @("shell", "rm", "-rf", $staging) -Quiet | Out-Null } catch { $cleanupErrors.Add($_.Exception.Message) }
    } else {
        $cleanupErrors.Add("Refused cleanup of unexpected staging path: $staging")
    }
    # All three packages were proven absent before install. Query every target unconditionally so
    # even a partially failing `adb install` cannot escape cleanup merely because bookkeeping did
    # not reach the next line.
    foreach ($packageName in @($testPackage, $providerPackage, $hostPackage)) {
        try {
            if (Test-YoloR6RecoveryPackageInstalled -Adb $tools.adb -PackageName $packageName) {
                Invoke-YoloR6RecoveryAdb -Adb $tools.adb -Arguments @("uninstall", $packageName) -Quiet | Out-Null
            }
        } catch { $cleanupErrors.Add($_.Exception.Message) }
    }
}

Assert-YoloR6RecoveryCondition ($cleanupErrors.Count -eq 0) "R6 recovery cleanup failed: $($cleanupErrors -join '; ')"
foreach ($packageName in @($testPackage, $providerPackage, $hostPackage)) {
    Assert-YoloR6RecoveryCondition (-not (Test-YoloR6RecoveryPackageInstalled -Adb $tools.adb -PackageName $packageName)) "Package remains after R6 cleanup: $packageName"
}

$report = [ordered]@{
    schemaVersion = 1
    roadmapStage = "R6-D-RECOVERY"
    status = "PASS"
    generatedAtUtc = [DateTime]::UtcNow.ToString("o")
    environmentKind = $EnvironmentKind
    device = [ordered]@{ serial = $DeviceSerial; model = $model; apiLevel = $apiLevel; abiList = $abiList; pageSize = $pageSize; emulator = $isEmulator }
    signerSha256 = $expectedSigner
    apks = [ordered]@{
        host = [ordered]@{ path = $hostApkPath; sha256 = Get-YoloR6RecoverySha256 -Path $hostApkPath }
        provider = [ordered]@{ path = $providerApkPath; sha256 = Get-YoloR6RecoverySha256 -Path $providerApkPath }
        instrumentation = [ordered]@{ path = $testApkPath; sha256 = Get-YoloR6RecoverySha256 -Path $testApkPath }
    }
    fixture = $fixtureEvidence
    instrumentation = [ordered]@{
        wallClockTimeoutMillis = $instrumentationTimeoutMillis
        result = $resultJson
    }
    cleanup = [ordered]@{ packagesAbsent = $true; stagingAbsent = $true }
    claims = [ordered]@{
        DEVICE_E2E = "PASS_FOR_THIS_EXPLICIT_ENVIRONMENT_ONLY"
        REAL_PROVIDER_DEATH_EXPLICIT_REOPEN = "PASS"
        DISPATCH_RETRY_AFTER_DEATH = "ABSENT"
        PRODUCTION = "PRODUCTION_NOT_COMPLETE_PENDING_SECOND_ENVIRONMENT_INDEX_UPGRADE_ROLLBACK_AND_PUBLICATION"
    }
}
$null = New-Item -ItemType Directory -Path $reportDirectory -Force
$reportPath = Join-Path $reportDirectory ("r6-recovery-{0}.generated.json" -f ($EnvironmentKind -replace '[^a-z0-9-]', '-'))
[System.IO.File]::WriteAllText($reportPath, "$(ConvertTo-Json $report -Depth 14)`n", [System.Text.UTF8Encoding]::new($false))
Write-Host "YOLO_R6_RECOVERY=PASS report=$reportPath"
Write-Host "YOLO_R6_PRODUCTION=PRODUCTION_NOT_COMPLETE"
