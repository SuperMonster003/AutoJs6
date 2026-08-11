param(
    [string]$Serial = "QV710AF65F",
    [string]$HostApkPath = "E:\AutoJs6-Worktree-Python\app\build\outputs\apk\app\debug\autojs6-v6.8.0-alpha7-arm64-v8a.apk",
    [string]$PluginApkPath = "D:\idea-projects\AutoJs6-Plugin-Python-Runtime\app\build\outputs\apk\debug\app-arm64-v8a-debug.apk",
    [string]$AndroidTestApkPath = "E:\AutoJs6-Worktree-Python\app\build\outputs\apk\androidTest\app\debug\app-app-debug-androidTest.apk",
    [Parameter(Mandatory = $true)][string]$HostApkSha256,
    [Parameter(Mandatory = $true)][string]$PluginApkSha256,
    [Parameter(Mandatory = $true)][string]$AndroidTestApkSha256,
    [string]$ExpectedSignerSha256 = "2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8",
    [string]$Selector = "org.autojs.autojs.engine.PythonR3PreviewSmokeInstrumentationTest#localPyUsesPythonEngineForOutputFailureForceStopAndRecoveryWithoutFallback",
    [string]$EnableArgument = "autojs.python.r3p.smoke.enabled",
    [string]$EvidenceLevel = "R3_DEVELOPER_PREVIEW_EXACT_DEVICE_SMOKE",
    [Parameter(Mandatory = $true)][switch]$ConfirmDeviceMutation
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$PSNativeCommandUseErrorActionPreference = $false

$sdkDir = "E:\.android\sdk"
$adb = Join-Path $sdkDir "platform-tools\adb.exe"
$aapt2 = Join-Path $sdkDir "build-tools\37.0.0\aapt2.exe"
$apkSigner = Join-Path $sdkDir "build-tools\37.0.0\apksigner.bat"
$runner = "org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner"
$newLine = [Environment]::NewLine
$temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) ("autojs6-python-r3p-" + [guid]::NewGuid().ToString("N"))
$createdFiles = [System.Collections.Generic.List[string]]::new()
$cleanupErrors = [System.Collections.Generic.List[string]]::new()
$runError = $null
$passRecord = $null
$initialUsers = @()
$deviceMutexName = "Global\AutoJs6-Codex-Device-$($Serial -replace '[^A-Za-z0-9_.-]', '_')"
$deviceMutex = [System.Threading.Mutex]::new($false, $deviceMutexName)
$deviceMutexOwned = $false

$artifacts = @(
    [pscustomobject]@{
        Role = "host"
        Package = "org.autojs.autojs6"
        Source = $HostApkPath
        SnapshotName = "host-arm64.apk"
        Sha256 = $HostApkSha256.ToLowerInvariant()
        ExpectedAbi = "arm64-v8a"
        Attempted = $false
    },
    [pscustomobject]@{
        Role = "plugin"
        Package = "io.github.supermonster003.autojs6.plugin.python.runtime"
        Source = $PluginApkPath
        SnapshotName = "plugin-arm64.apk"
        Sha256 = $PluginApkSha256.ToLowerInvariant()
        ExpectedAbi = "arm64-v8a"
        Attempted = $false
    },
    [pscustomobject]@{
        Role = "test"
        Package = "org.autojs.autojs6.test"
        Source = $AndroidTestApkPath
        SnapshotName = "host-androidTest.apk"
        Sha256 = $AndroidTestApkSha256.ToLowerInvariant()
        ExpectedAbi = ""
        Attempted = $false
    }
)

function Invoke-ExactAdb {
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [switch]$AllowNonZero
    )

    $output = & $adb "-s" $Serial @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0 -and -not $AllowNonZero) {
        throw "adb failed ($exitCode): adb -s $Serial $($Arguments -join ' ')$newLine$($output -join $newLine)"
    }
    return @($output | ForEach-Object { "$_" })
}

function Get-DeviceUsers {
    $lines = @(Invoke-ExactAdb @("shell", "pm", "list", "users"))
    $ids = [System.Collections.Generic.List[int]]::new()
    foreach ($line in $lines) {
        $match = [regex]::Match($line, "UserInfo\{(\d+):")
        if ($match.Success) {
            $ids.Add([int]$match.Groups[1].Value)
        } elseif ($line.Trim() -notin @("", "Users:")) {
            throw "Unparseable user inventory line: $line"
        }
    }
    $unique = @($ids | Sort-Object -Unique)
    if ($unique.Count -ne $ids.Count -or 0 -notin $unique) {
        throw "Invalid user inventory: $($lines -join ' | ')"
    }
    return $unique
}

function Assert-UsersUnchanged {
    $current = @(Get-DeviceUsers)
    if (($current -join ",") -ne ($initialUsers -join ",")) {
        throw "Device users changed: initial=$($initialUsers -join ',') current=$($current -join ',')"
    }
}

function Get-NonEmptyLines {
    param([object[]]$Lines)
    return @($Lines | ForEach-Object { "$($_)".Trim() } | Where-Object { $_.Length -gt 0 })
}

function Get-ExactPackageRecords {
    param(
        [object[]]$Lines,
        [Parameter(Mandatory = $true)][string]$PackageName
    )

    $matches = [System.Collections.Generic.List[string]]::new()
    foreach ($line in @(Get-NonEmptyLines $Lines)) {
        $parsed = [regex]::Match($line, "^package:(\S+)$")
        if (-not $parsed.Success) {
            throw "Unparseable package record: $line"
        }
        if ($parsed.Groups[1].Value -eq $PackageName) {
            $matches.Add($line)
        }
    }
    return $matches.ToArray()
}

function Assert-PackageAbsent {
    param([Parameter(Mandatory = $true)][string]$PackageName)

    Assert-UsersUnchanged
    foreach ($userId in $initialUsers) {
        $paths = @(Get-NonEmptyLines (Invoke-ExactAdb -Arguments @("shell", "pm", "path", "--user", "$userId", $PackageName) -AllowNonZero))
        $records = @(Get-ExactPackageRecords -Lines (Invoke-ExactAdb @("shell", "pm", "list", "packages", "--user", "$userId", "-u", $PackageName)) -PackageName $PackageName)
        if ($paths.Count -ne 0 -or $records.Count -ne 0) {
            throw "Package is not absent for user ${userId}: $PackageName"
        }
    }
    $globalRecords = @(Get-ExactPackageRecords -Lines (Invoke-ExactAdb @("shell", "pm", "list", "packages", "-u", $PackageName)) -PackageName $PackageName)
    if ($globalRecords.Count -ne 0) {
        throw "Package has a retained/global record: $PackageName"
    }
}

function Assert-Artifact {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$ExpectedPackage,
        [Parameter(Mandatory = $true)][string]$ExpectedSha,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$ExpectedAbi
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Artifact missing: $Path"
    }
    $actualSha = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualSha -ne $ExpectedSha) {
        throw "Artifact SHA mismatch: $Path expected=$ExpectedSha actual=$actualSha"
    }

    $badging = & $aapt2 dump badging $Path 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "aapt2 failed for $Path$newLine$($badging -join $newLine)"
    }
    $badgingText = $badging -join $newLine
    $packageMatch = [regex]::Match($badgingText, "package: name='([^']+)'")
    if (-not $packageMatch.Success -or $packageMatch.Groups[1].Value -ne $ExpectedPackage) {
        throw "Package mismatch: $Path expected=$ExpectedPackage actual=$($packageMatch.Groups[1].Value)"
    }
    $abiMatch = [regex]::Match($badgingText, "native-code: '([^']+)'")
    $actualAbi = if ($abiMatch.Success) { $abiMatch.Groups[1].Value } else { "" }
    if ($actualAbi -ne $ExpectedAbi) {
        throw "ABI mismatch: $Path expected=$ExpectedAbi actual=$actualAbi"
    }

    $certificate = & $apkSigner verify --print-certs $Path 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "apksigner failed for $Path$newLine$($certificate -join $newLine)"
    }
    $certificateMatch = [regex]::Match(($certificate -join $newLine), "certificate SHA-256 digest:\s*([0-9a-fA-F]+)")
    $actualSigner = if ($certificateMatch.Success) { $certificateMatch.Groups[1].Value.ToLowerInvariant() } else { "" }
    if ($actualSigner -ne $ExpectedSignerSha256.ToLowerInvariant()) {
        throw "Signer mismatch: $Path expected=$ExpectedSignerSha256 actual=$actualSigner"
    }
}

function Assert-InstalledArtifact {
    param([Parameter(Mandatory = $true)]$Artifact)

    Assert-UsersUnchanged
    $userZeroPaths = @(Get-NonEmptyLines (Invoke-ExactAdb @("shell", "pm", "path", "--user", "0", $Artifact.Package)))
    if ($userZeroPaths.Count -ne 1 -or $userZeroPaths[0] -notmatch "^package:(/.+)$") {
        throw "Expected exactly one user-0 APK path for $($Artifact.Package)"
    }
    foreach ($userId in @($initialUsers | Where-Object { $_ -ne 0 })) {
        $otherPaths = @(Get-NonEmptyLines (Invoke-ExactAdb -Arguments @("shell", "pm", "path", "--user", "$userId", $Artifact.Package) -AllowNonZero))
        if ($otherPaths.Count -ne 0) {
            throw "Package unexpectedly installed for user ${userId}: $($Artifact.Package)"
        }
    }

    $remotePath = $userZeroPaths[0].Substring(8)
    $pulledPath = Join-Path $temporaryRoot ("installed-$($Artifact.Role).apk")
    $null = Invoke-ExactAdb @("pull", $remotePath, $pulledPath)
    $createdFiles.Add($pulledPath)
    Assert-Artifact -Path $pulledPath -ExpectedPackage $Artifact.Package -ExpectedSha $Artifact.Sha256 -ExpectedAbi $Artifact.ExpectedAbi
}

function Remove-OwnedArtifact {
    param([Parameter(Mandatory = $true)]$Artifact)

    if (-not $Artifact.Attempted) {
        return
    }
    Assert-UsersUnchanged
    $globalRecords = @(Get-ExactPackageRecords -Lines (Invoke-ExactAdb @("shell", "pm", "list", "packages", "-u", $Artifact.Package)) -PackageName $Artifact.Package)
    if ($globalRecords.Count -eq 0) {
        return
    }
    Assert-InstalledArtifact -Artifact $Artifact
    $uninstall = @(Invoke-ExactAdb @("uninstall", $Artifact.Package))
    if (($uninstall -join $newLine) -notmatch "(?m)^Success\s*$") {
        throw "Uninstall did not report Success for $($Artifact.Package)"
    }
    Assert-PackageAbsent -PackageName $Artifact.Package
}

try {
    try {
        $deviceMutexOwned = $deviceMutex.WaitOne([TimeSpan]::FromMinutes(9))
    } catch [System.Threading.AbandonedMutexException] {
        $deviceMutexOwned = $true
    }
    if (-not $deviceMutexOwned) {
        throw "Timed out waiting for the exact-device transaction mutex: $deviceMutexName"
    }
    if (-not $ConfirmDeviceMutation) {
        throw "-ConfirmDeviceMutation is required"
    }
    if ([string]::IsNullOrWhiteSpace($Selector) -or $Selector -match "[\x00-\x20]") {
        throw "-Selector must be one non-empty instrumentation class or class#method token"
    }
    if ($EnableArgument -notmatch "^[A-Za-z0-9._-]+$") {
        throw "-EnableArgument contains an unsupported character"
    }
    if ($EvidenceLevel -notmatch "^[A-Z0-9_]+$") {
        throw "-EvidenceLevel contains an unsupported character"
    }
    foreach ($tool in @($adb, $aapt2, $apkSigner)) {
        if (-not (Test-Path -LiteralPath $tool -PathType Leaf)) {
            throw "Required tool missing: $tool"
        }
    }

    $gradleClients = @(Get-CimInstance Win32_Process | Where-Object {
        ($_.Name -in @("java.exe", "javaw.exe")) -and $_.CommandLine -match "gradle-wrapper\.jar"
    })
    $otherAdbClients = @(Get-CimInstance Win32_Process | Where-Object {
        $_.Name -eq "adb.exe" -and $_.CommandLine -notmatch "fork-server server"
    })
    if ($gradleClients.Count -ne 0 -or $otherAdbClients.Count -ne 0) {
        throw "Concurrent client detected: gradle=$($gradleClients.Count) adb=$($otherAdbClients.Count)"
    }

    $state = (@(Invoke-ExactAdb @("get-state")) -join "").Trim()
    $api = (@(Invoke-ExactAdb @("shell", "getprop", "ro.build.version.sdk")) -join "").Trim()
    $abi = (@(Invoke-ExactAdb @("shell", "getprop", "ro.product.cpu.abi")) -join "").Trim()
    if ($state -ne "device" -or $api -ne "31" -or $abi -ne "arm64-v8a") {
        throw "Unexpected device: state=$state api=$api abi=$abi"
    }

    $initialUsers = @(Get-DeviceUsers)
    if (($initialUsers -join ",") -ne "0,10") {
        throw "Unexpected user inventory: $($initialUsers -join ',')"
    }

    foreach ($artifact in $artifacts) {
        Assert-PackageAbsent -PackageName $artifact.Package
        Assert-Artifact -Path $artifact.Source -ExpectedPackage $artifact.Package -ExpectedSha $artifact.Sha256 -ExpectedAbi $artifact.ExpectedAbi
    }

    $null = New-Item -ItemType Directory -Path $temporaryRoot
    foreach ($artifact in $artifacts) {
        $snapshot = Join-Path $temporaryRoot $artifact.SnapshotName
        $artifact | Add-Member -NotePropertyName Snapshot -NotePropertyValue $snapshot
        Copy-Item -LiteralPath $artifact.Source -Destination $snapshot
        (Get-Item -LiteralPath $snapshot).IsReadOnly = $true
        $createdFiles.Add($snapshot)
        Assert-Artifact -Path $snapshot -ExpectedPackage $artifact.Package -ExpectedSha $artifact.Sha256 -ExpectedAbi $artifact.ExpectedAbi
    }

    foreach ($artifact in $artifacts) {
        Assert-PackageAbsent -PackageName $artifact.Package
        $artifact.Attempted = $true
        $install = @(Invoke-ExactAdb @("install", "--user", "0", $artifact.Snapshot))
        if (($install -join $newLine) -notmatch "(?m)^Success\s*$") {
            throw "Install did not report Success for $($artifact.Package)"
        }
        Assert-InstalledArtifact -Artifact $artifact
    }

    $instrumentation = @(Invoke-ExactAdb @(
        "shell", "am", "instrument", "--user", "0", "-w", "-r",
        "-e", "class", $selector,
        "-e", $EnableArgument, "true",
        $runner
    ))
    $instrumentationText = $instrumentation -join $newLine
    if ($instrumentationText -notmatch "(?m)^OK \(1 test\)\s*$") {
        throw "Instrumentation did not report OK (1 test):$newLine$instrumentationText"
    }
    if ($instrumentationText -match "FAILURES!!!|INSTRUMENTATION_FAILED|AssumptionViolated|Tests run:\s*0") {
        throw "Instrumentation contained a failure or skip marker:$newLine$instrumentationText"
    }

    $passRecord = [pscustomobject]@{
        status = "PASS"
        evidenceLevel = $EvidenceLevel
        serial = $Serial
        api = [int]$api
        abi = $abi
        frozenUserIds = @($initialUsers)
        selector = $selector
        tests = 1
        failures = 0
        errors = 0
        skipped = 0
        hostSha256 = $artifacts[0].Sha256
        pluginSha256 = $artifacts[1].Sha256
        androidTestSha256 = $artifacts[2].Sha256
        signerSha256 = $ExpectedSignerSha256.ToLowerInvariant()
        instrumentationTail = @($instrumentation | Select-Object -Last 12)
    }
} catch {
    $runError = $_
} finally {
    foreach ($artifact in @($artifacts[2], $artifacts[1], $artifacts[0])) {
        try {
            Remove-OwnedArtifact -Artifact $artifact
        } catch {
            $cleanupErrors.Add("$($artifact.Package): $($_.Exception.Message)")
        }
    }

    foreach ($createdFile in @($createdFiles | Select-Object -Unique)) {
        try {
            if (Test-Path -LiteralPath $createdFile -PathType Leaf) {
                (Get-Item -LiteralPath $createdFile).IsReadOnly = $false
                Remove-Item -LiteralPath $createdFile -Force
            }
        } catch {
            $cleanupErrors.Add("temp-file: ${createdFile}: $($_.Exception.Message)")
        }
    }
    if (Test-Path -LiteralPath $temporaryRoot) {
        try {
            $resolvedTemp = [IO.Path]::GetFullPath($temporaryRoot)
            $resolvedBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
            $leaf = [IO.Path]::GetFileName($resolvedTemp)
            if (-not $resolvedTemp.StartsWith($resolvedBase, [StringComparison]::OrdinalIgnoreCase) -or
                -not $leaf.StartsWith("autojs6-python-r3p-", [StringComparison]::Ordinal)) {
                throw "Refusing unsafe temp cleanup: $resolvedTemp"
            }
            Remove-Item -LiteralPath $resolvedTemp -Force
        } catch {
            $cleanupErrors.Add("temp-dir: $($_.Exception.Message)")
        }
    }
    if ($deviceMutexOwned) {
        try {
            $deviceMutex.ReleaseMutex()
            $deviceMutexOwned = $false
        } catch {
            $cleanupErrors.Add("device-mutex: $($_.Exception.Message)")
        }
    }
    $deviceMutex.Dispose()
}

if ($cleanupErrors.Count -ne 0) {
    throw "Cleanup failed: $($cleanupErrors -join ' || ')"
}
if ($null -ne $runError) {
    throw $runError
}
if ($null -eq $passRecord) {
    throw "Smoke ended without a PASS record"
}
$passRecord | Add-Member -NotePropertyName restorationVerified -NotePropertyValue $true
$passRecord | ConvertTo-Json -Depth 6
