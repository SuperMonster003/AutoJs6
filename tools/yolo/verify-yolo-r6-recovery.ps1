[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $DeviceSerial,

    [Parameter(Mandatory = $true)]
    [ValidateSet("representative-arm64-physical", "current-api-arm64-emulator", "current-api-arm64-physical")]
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
    [string] $ControlledBuildReceipt,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $OfficialProductionSignerSha256,

    [Parameter(Mandatory = $true)]
    [ValidateRange(1, [long]::MaxValue)]
    [long] $ExpectedProviderVersionCode,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $ExpectedMainCommit,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $ExpectedProviderCommit,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $ProviderBuildReceipt,

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
$reportPath = Join-Path $reportDirectory ("r6-recovery-{0}.generated.json" -f ($EnvironmentKind -replace '[^a-z0-9-]', '-'))
$null = New-Item -ItemType Directory -Path $reportDirectory -Force
$reportPrefix = [System.IO.Path]::GetFullPath($reportDirectory).TrimEnd('\') + '\'
$reportPath = [System.IO.Path]::GetFullPath($reportPath)
if (-not $reportPath.StartsWith($reportPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "R6 recovery report escaped its fixed report directory"
}
if (Test-Path -LiteralPath $reportPath -PathType Leaf) { Remove-Item -LiteralPath $reportPath -Force }

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

function Stop-YoloR6RecoveryTimedOutProcess {
    param(
        [Parameter(Mandatory = $true)][System.Diagnostics.Process] $Process,
        [Parameter(Mandatory = $true)][string] $Label
    )
    try {
        if (-not $Process.HasExited) { $Process.Kill($true) }
    } catch {
        if (-not $Process.HasExited) { throw "$Label timed out and process-tree termination failed: $($_.Exception.Message)" }
    }
    if (-not $Process.HasExited -and -not $Process.WaitForExit(10000)) {
        throw "$Label timed out and its process tree did not exit within the 10-second termination limit"
    }
}

function Invoke-YoloR6RecoveryProcess {
    param(
        [Parameter(Mandatory = $true)][string] $FilePath,
        [string[]] $Arguments = @(),
        [ValidateRange(1, 3600)][int] $TimeoutSeconds = 120,
        [switch] $Quiet
    )
    if (-not $Quiet) { Write-Host "> $FilePath $($Arguments -join ' ')" }
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $isBatch = [IO.Path]::GetExtension($FilePath) -in @(".bat", ".cmd")
    $process.StartInfo.FileName = if ($isBatch) { (Get-Command cmd.exe -ErrorAction Stop).Source } else { $FilePath }
    $process.StartInfo.UseShellExecute = $false
    $process.StartInfo.RedirectStandardOutput = $true
    $process.StartInfo.RedirectStandardError = $true
    $effectiveArguments = if ($isBatch) { @("/d", "/s", "/c", $FilePath) + $Arguments } else { $Arguments }
    foreach ($argument in $effectiveArguments) { [void]$process.StartInfo.ArgumentList.Add($argument) }
    try {
        Assert-YoloR6RecoveryCondition ($process.Start()) "External command did not start: $FilePath"
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
            Stop-YoloR6RecoveryTimedOutProcess -Process $process -Label $FilePath
            throw "External command exceeded its $TimeoutSeconds-second wall-time limit: $FilePath"
        }
        $stdout = $stdoutTask.GetAwaiter().GetResult()
        $stderr = $stderrTask.GetAwaiter().GetResult()
        $lines = @(($stdout, $stderr) | Where-Object { -not [string]::IsNullOrEmpty($_) } | ForEach-Object { $_ -split '\r?\n' } | Where-Object { $_ -ne "" })
        if (-not $Quiet) { $lines | ForEach-Object { Write-Host $_ } }
        return [pscustomobject][ordered]@{ exitCode = $process.ExitCode; output = $lines }
    } finally { $process.Dispose() }
}

function Invoke-YoloR6RecoveryExternal {
    param(
        [Parameter(Mandatory = $true)][string] $FilePath,
        [string[]] $Arguments = @(),
        [ValidateRange(1, 3600)][int] $TimeoutSeconds = 120,
        [switch] $Quiet
    )
    $result = Invoke-YoloR6RecoveryProcess -FilePath $FilePath -Arguments $Arguments -TimeoutSeconds $TimeoutSeconds -Quiet:$Quiet
    if ($result.exitCode -ne 0) { throw "External command failed with exit code $($result.exitCode)`: $FilePath" }
    return @($result.output)
}

function Invoke-YoloR6RecoveryAdb {
    param([string] $Adb, [string[]] $Arguments = @(), [switch] $Quiet)
    return Invoke-YoloR6RecoveryExternal -FilePath $Adb -Arguments (@("-s", $DeviceSerial) + $Arguments) -TimeoutSeconds 180 -Quiet:$Quiet
}

function Test-YoloR6RecoveryRemotePathAbsent {
    param([string] $Adb, [string] $RemotePath)
    $result = Invoke-YoloR6RecoveryProcess -FilePath $Adb -Arguments @("-s", $DeviceSerial, "shell", "ls", "-ld", $RemotePath) -TimeoutSeconds 30 -Quiet
    $code = $result.exitCode
    if ($code -eq 1) { return $true }
    if ($code -eq 0) { return $false }
    throw "Unable to verify cleanup of the exact R6 staging path on $DeviceSerial"
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
            Stop-YoloR6RecoveryTimedOutProcess -Process $process -Label "$testClass#$testMethod instrumentation"
            foreach ($packageName in @($testPackage, $hostPackage, $providerPackage)) {
                try {
                    $null = Invoke-YoloR6RecoveryAdb -Adb $Adb -Arguments @(
                        "shell", "am", "force-stop", $packageName
                    ) -Quiet
                } catch { }
            }
            throw "$testClass#$testMethod exceeded its $timeoutMillis ms wall-clock limit"
        }
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
    $result = Invoke-YoloR6RecoveryProcess -FilePath $Adb -Arguments @("-s", $DeviceSerial, "shell", "pm", "path", $PackageName) -TimeoutSeconds 30 -Quiet
    $output = @($result.output)
    $code = $result.exitCode
    if ($code -eq 0) { return (($output -join "`n") -match '(?m)^package:') }
    if ($code -eq 1 -and -not (($output -join "`n") -match '(?m)^package:')) { return $false }
    throw "Unable to query package $PackageName on $DeviceSerial"
}

function Get-YoloR6RecoveryInstalledApkEvidence {
    param(
        [Parameter(Mandatory = $true)][string] $Adb,
        [Parameter(Mandatory = $true)][string] $PackageName,
        [Parameter(Mandatory = $true)][string] $InputApk
    )
    $pathOutput = (Invoke-YoloR6RecoveryAdb -Adb $Adb -Arguments @("shell", "pm", "path", $PackageName) -Quiet) -join "`n"
    $paths = @([regex]::Matches($pathOutput, '(?m)^package:(/[^\r\n]+)\s*$') | ForEach-Object { $_.Groups[1].Value.Trim() })
    Assert-YoloR6RecoveryCondition ($paths.Count -eq 1) "Installed package $PackageName must expose exactly one APK path"
    $digestOutput = (Invoke-YoloR6RecoveryAdb -Adb $Adb -Arguments @("shell", "sha256sum", $paths[0]) -Quiet) -join "`n"
    $digest = [regex]::Match($digestOutput, '(?im)^([0-9a-f]{64})\s+')
    Assert-YoloR6RecoveryCondition $digest.Success "Device sha256sum did not return an APK digest for $PackageName"
    $inputSha = Get-YoloR6RecoverySha256 -Path $InputApk
    Assert-YoloR6RecoveryCondition ($digest.Groups[1].Value.ToLowerInvariant() -ceq $inputSha) "Installed APK digest differs for $PackageName"
    return [pscustomobject][ordered]@{ packageName = $PackageName; installedPath = $paths[0]; inputSha256 = $inputSha; installedSha256 = $inputSha; exactBytesMatch = $true }
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
$pwsh = (Get-Command pwsh.exe -ErrorAction Stop).Source
$publishableArguments = @(
    "-NoLogo", "-NoProfile", "-NonInteractive", "-File", $publishableVerifier,
    "-ProviderApk", $providerApkPath,
    "-HostApk", $hostApkPath,
    "-InstrumentationApk", $testApkPath,
    "-OfficialProductionSignerSha256", $expectedSigner,
    "-ExpectedProviderVersionCode", ([string]$ExpectedProviderVersionCode),
    "-ExpectedMainCommit", $ExpectedMainCommit,
    "-ExpectedProviderCommit", $ExpectedProviderCommit,
    "-ProviderBuildReceipt", $ProviderBuildReceipt,
    "-ControlledBuildReceipt", $ControlledBuildReceipt,
    "-SiblingPath", $siblingRoot
)
Invoke-YoloR6RecoveryExternal -FilePath $pwsh -Arguments $publishableArguments -TimeoutSeconds 900 | Out-Null
$publishableReportPath = Get-YoloR6RecoveryRequiredFile -Path (
    Join-Path $repositoryRoot "build\reports\yolo\r6-publishable.generated.json"
) -Label "R6 publishable artifact preflight report"
$publishableReport = Get-Content -Raw -LiteralPath $publishableReportPath | ConvertFrom-Json
Assert-YoloR6RecoveryCondition (
    [string]$publishableReport.claims.PRODUCTION_ARTIFACT_PREFLIGHT -ceq "PASS_CONTROLLED_SIGNED_BUILD" -and
    [bool]$publishableReport.evidenceBoundary.artifactBuildIdentityProven -and
    [bool]$publishableReport.evidenceBoundary.providerSignedApkBuildIdentityProven -and
    [bool]$publishableReport.evidenceBoundary.hostSignedApkBuildIdentityProven -and
    [bool]$publishableReport.evidenceBoundary.instrumentationSignedApkBuildIdentityProven -and
    -not [bool]$publishableReport.evidenceBoundary.cryptographicSourceAttestation
) (
    "R6 recovery refuses production APKs without a reverified controlled signed-build receipt; " +
    "current artifact preflight=$($publishableReport.claims.PRODUCTION_ARTIFACT_PREFLIGHT)"
)

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
} elseif ($EnvironmentKind -ceq "current-api-arm64-emulator") {
    Assert-YoloR6RecoveryCondition $isEmulator "The current API environment must be an emulator"
    Assert-YoloR6RecoveryCondition ($apiLevel -eq 36) "The current API emulator must run the pinned compile/target API 36"
} else {
    Assert-YoloR6RecoveryCondition (-not $isEmulator) "The current API physical environment must be a physical device"
    Assert-YoloR6RecoveryCondition ($apiLevel -ge 35) "The current API physical environment must run API 35 or newer"
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
$installedApkEvidence = $null
$stagingAbsentAfterCleanup = $false
try {
    Invoke-YoloR6RecoveryAdb -Adb $tools.adb -Arguments @("shell", "mkdir", "-p", $staging) | Out-Null
    foreach ($name in $fixtureNames) {
        Invoke-YoloR6RecoveryAdb -Adb $tools.adb -Arguments @("push", (Join-Path $fixtureRootPath $name), "$staging/$name") | Out-Null
    }
    Invoke-YoloR6RecoveryAdb -Adb $tools.adb -Arguments @("install", "-r", $hostApkPath) | Out-Null
    Invoke-YoloR6RecoveryAdb -Adb $tools.adb -Arguments @("install", "-r", $providerApkPath) | Out-Null
    Invoke-YoloR6RecoveryAdb -Adb $tools.adb -Arguments @("install", "-r", "-t", $testApkPath) | Out-Null

    $installedApkEvidence = [ordered]@{
        host = Get-YoloR6RecoveryInstalledApkEvidence -Adb $tools.adb -PackageName $hostPackage -InputApk $hostApkPath
        provider = Get-YoloR6RecoveryInstalledApkEvidence -Adb $tools.adb -PackageName $providerPackage -InputApk $providerApkPath
        instrumentation = Get-YoloR6RecoveryInstalledApkEvidence -Adb $tools.adb -PackageName $testPackage -InputApk $testApkPath
    }

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
        try { $stagingAbsentAfterCleanup = Test-YoloR6RecoveryRemotePathAbsent -Adb $tools.adb -RemotePath $staging }
        catch { $cleanupErrors.Add($_.Exception.Message) }
        if (-not $stagingAbsentAfterCleanup) { $cleanupErrors.Add("R6 staging path still exists after cleanup") }
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
Assert-YoloR6RecoveryCondition $stagingAbsentAfterCleanup "R6 staging cleanup was not independently confirmed"
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
    installedApks = $installedApkEvidence
    fixture = $fixtureEvidence
    instrumentation = [ordered]@{
        wallClockTimeoutMillis = $instrumentationTimeoutMillis
        result = $resultJson
    }
    cleanup = [ordered]@{ packagesAbsent = $true; stagingPath = $staging; stagingAbsent = $stagingAbsentAfterCleanup; exactPostCheck = "PASS" }
    claims = [ordered]@{
        DEVICE_E2E = "PASS_FOR_THIS_EXPLICIT_ENVIRONMENT_ONLY"
        REAL_PROVIDER_DEATH_EXPLICIT_REOPEN = "PASS"
        DISPATCH_RETRY_AFTER_DEATH = "ABSENT"
        UPGRADE_RUNTIME = "NOT_RUN_BY_PRODUCT_DECISION"
        VERSION_ROLLBACK = "NOT_RUN_BY_PRODUCT_DECISION"
        PRODUCTION = "PRODUCTION_NOT_COMPLETE_PENDING_SECOND_ENVIRONMENT_INDEX_AND_PUBLICATION"
    }
}
[System.IO.File]::WriteAllText($reportPath, "$(ConvertTo-Json $report -Depth 14)`n", [System.Text.UTF8Encoding]::new($false))
Write-Host "YOLO_R6_RECOVERY=PASS report=$reportPath"
Write-Host "YOLO_R6_PRODUCTION=PRODUCTION_NOT_COMPLETE"
