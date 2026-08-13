param(
    [Parameter(Mandatory = $true)]
    [string] $Serial,

    [Parameter(Mandatory = $true)]
    [string] $HostApk,

    [Parameter(Mandatory = $true)]
    [string] $TestApk,

    [Parameter(Mandatory = $true)]
    [string] $ProviderApk,

    [string] $Adb = "E:\.android\sdk\platform-tools\adb.exe",

    [ValidateRange(10000, 300000)]
    [int] $CommandTimeoutMillis = 120000
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$hostPackage = "org.autojs.autojs6"
$testPackage = "org.autojs.autojs6.test"
$providerPackage = "io.github.supermonster003.autojs6.plugin.lua.runtime"
$runner = "$testPackage/androidx.test.runner.AndroidJUnitRunner"
$selector = "org.autojs.autojs.core.plugin.lua.LuaOfficialRuntimeSmokeTest#officialProviderExecutesAndReleasesSequentialSessions"
$packages = @($hostPackage, $testPackage, $providerPackage)

function Invoke-AdbBounded {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $Arguments,

        [int] $TimeoutMillis = $CommandTimeoutMillis
    )

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $Adb
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    foreach ($argument in $Arguments) {
        $startInfo.ArgumentList.Add($argument)
    }

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) {
        throw "Unable to start adb"
    }
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEndAsync()
    if (-not $process.WaitForExit($TimeoutMillis)) {
        $process.Kill($true)
        $process.WaitForExit()
        $null = $stdout.GetAwaiter().GetResult()
        $null = $stderr.GetAwaiter().GetResult()
        return [pscustomobject]@{
            ExitCode = -1
            TimedOut = $true
            Lines = @("ADB_CLIENT_TIMEOUT")
        }
    }

    $output = $stdout.GetAwaiter().GetResult()
    $errorOutput = $stderr.GetAwaiter().GetResult()
    $lines = @(($output + "`n" + $errorOutput) -split "\r?\n" | Where-Object { $_.Length -gt 0 })
    return [pscustomobject]@{
        ExitCode = $process.ExitCode
        TimedOut = $false
        Lines = $lines
    }
}

function Require-AdbSuccess {
    param(
        [Parameter(Mandatory = $true)]
        $Result,

        [Parameter(Mandatory = $true)]
        [string] $Label
    )

    $successCount = @($Result.Lines | Where-Object { $_.Trim() -eq "Success" }).Count
    if ($Result.TimedOut -or $Result.ExitCode -ne 0 -or $successCount -ne 1) {
        throw "$Label failed: exit=$($Result.ExitCode), timeout=$($Result.TimedOut), output=$($Result.Lines -join ' | ')"
    }
    Write-Output "$Label=Success"
}

function Invoke-AdbBinaryToFile {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $Arguments,

        [Parameter(Mandatory = $true)]
        [string] $Destination
    )

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $Adb
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    foreach ($argument in $Arguments) {
        $startInfo.ArgumentList.Add($argument)
    }

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    $destinationStream = [IO.File]::Open(
        $Destination,
        [IO.FileMode]::Create,
        [IO.FileAccess]::Write,
        [IO.FileShare]::None
    )
    try {
        if (-not $process.Start()) {
            throw "Unable to start adb"
        }
        $copyTask = $process.StandardOutput.BaseStream.CopyToAsync($destinationStream)
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $timedOut = -not $process.WaitForExit($CommandTimeoutMillis)
        if ($timedOut) {
            $process.Kill($true)
            $process.WaitForExit()
        }
        $copyTask.GetAwaiter().GetResult()
        $errorOutput = $stderrTask.GetAwaiter().GetResult()
        $lines = @($errorOutput -split "\r?\n" | Where-Object { $_.Length -gt 0 })
        return [pscustomobject]@{
            ExitCode = if ($timedOut) { -1 } else { $process.ExitCode }
            TimedOut = $timedOut
            Lines = $lines
        }
    } finally {
        $destinationStream.Dispose()
        $process.Dispose()
    }
}

function Invoke-DeviceLines {
    param([Parameter(Mandatory = $true)][string[]] $Arguments)

    $result = Invoke-AdbBounded -Arguments (@("-s", $Serial) + $Arguments)
    if ($result.TimedOut -or $result.ExitCode -ne 0) {
        throw "adb command failed: $($Arguments -join ' '): $($result.Lines -join ' | ')"
    }
    return @($result.Lines)
}

function Get-AndroidUserIds {
    $ids = @(Invoke-DeviceLines -Arguments @("shell", "pm", "list", "users") | ForEach-Object {
        if ($_ -match "UserInfo\{(\d+):") {
            [int] $Matches[1]
        }
    })
    if ($ids.Count -eq 0) {
        throw "No Android users were reported"
    }
    return $ids
}

function Get-DeviceProcesses {
    if ($deviceSdk -ge 26) {
        return @(Invoke-DeviceLines -Arguments @("shell", "ps", "-A", "-o", "PID,NAME"))
    }
    # Android 7.0 toolbox ps rejects both -A and -o; its default output still ends in NAME.
    return @(Invoke-DeviceLines -Arguments @("shell", "ps"))
}

function Assert-PackagesAbsent {
    param([Parameter(Mandatory = $true)][int[]] $UserIds)

    foreach ($userId in $UserIds) {
        $inventory = @(Invoke-DeviceLines -Arguments @(
            "shell", "cmd", "package", "list", "packages", "-u", "--user", "$userId"
        ))
        foreach ($packageName in $packages) {
            if (@($inventory | Where-Object { $_.Trim() -eq "package:$packageName" }).Count -ne 0) {
                throw "Package remains visible for Android user ${userId}: $packageName"
            }
        }
    }
}

foreach ($path in @($Adb, $HostApk, $TestApk, $ProviderApk)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required file is missing: $path"
    }
}

$state = (Invoke-DeviceLines -Arguments @("get-state") | Select-Object -First 1).Trim()
if ($state -ne "device") {
    throw "Device is not ready: $state"
}
$deviceSdkText = (Invoke-DeviceLines -Arguments @(
    "shell", "getprop", "ro.build.version.sdk"
) | Select-Object -First 1).Trim()
$deviceSdk = 0
if (-not [int]::TryParse($deviceSdkText, [ref] $deviceSdk) -or $deviceSdk -lt 24) {
    throw "Unsupported or malformed device SDK: $deviceSdkText"
}
$userIds = @(Get-AndroidUserIds)
Assert-PackagesAbsent -UserIds $userIds

$activity = @(Invoke-DeviceLines -Arguments @("shell", "dumpsys", "activity", "processes"))
if (@($activity | Select-String "mInstr=ActiveInstrumentation").Count -ne 0) {
    throw "Another instrumentation is active"
}
$processes = @(Get-DeviceProcesses)
foreach ($packageName in $packages) {
    if (@($processes | Where-Object { $_ -match "\s$([regex]::Escape($packageName))(?::\S+)?\s*$" }).Count) {
        throw "A target process already exists: $packageName"
    }
}

$expectedHashes = @{
    $hostPackage = (Get-FileHash -LiteralPath $HostApk -Algorithm SHA256).Hash.ToLowerInvariant()
    $testPackage = (Get-FileHash -LiteralPath $TestApk -Algorithm SHA256).Hash.ToLowerInvariant()
    $providerPackage = (Get-FileHash -LiteralPath $ProviderApk -Algorithm SHA256).Hash.ToLowerInvariant()
}
$installAttempted = @{
    $hostPackage = $false
    $testPackage = $false
    $providerPackage = $false
}
$runFailure = $null
$cleanupFailures = [System.Collections.Generic.List[string]]::new()

try {
    $installPlan = @(
        [pscustomobject]@{ Package = $hostPackage; Apk = $HostApk; Label = "INSTALL_HOST" }
        [pscustomobject]@{ Package = $providerPackage; Apk = $ProviderApk; Label = "INSTALL_PROVIDER" }
        [pscustomobject]@{ Package = $testPackage; Apk = $TestApk; Label = "INSTALL_TEST" }
    )
    foreach ($item in $installPlan) {
        # The device can finish an install after the adb client times out or loses its reply.
        # Mark the attempt before dispatch so finally always tries to remove any late package.
        $installAttempted[$item.Package] = $true
        $result = Invoke-AdbBounded -Arguments @(
            "-s", $Serial, "install", "--no-streaming", "-t", $item.Apk
        )
        Require-AdbSuccess -Result $result -Label $item.Label
    }

    foreach ($packageName in $packages) {
        $paths = @(Invoke-DeviceLines -Arguments @("shell", "pm", "path", $packageName) |
            Where-Object { $_.Trim().StartsWith("package:") })
        if ($paths.Count -ne 1) {
            throw "Expected one installed base APK for $packageName, found $($paths.Count)"
        }
        $remotePath = $paths[0].Trim().Substring("package:".Length)
        $auditCopy = [System.IO.Path]::GetTempFileName()
        try {
            $pull = Invoke-AdbBounded -Arguments @("-s", $Serial, "pull", $remotePath, $auditCopy)
            if ($pull.TimedOut -or $pull.ExitCode -ne 0) {
                if ($deviceSdk -ge 26) {
                    throw "Unable to audit installed APK for ${packageName}: exit=$($pull.ExitCode), timeout=$($pull.TimedOut), output=$($pull.Lines -join ' | ')"
                }
                # Android 7 adbd sync can report a PackageManager path as missing even though
                # the shell can read the world-readable base APK. Preserve the byte-exact audit
                # through exec-out instead of weakening the installed-artifact hash check.
                $fallback = @(Invoke-AdbBinaryToFile -Arguments @(
                    "-s", $Serial, "exec-out", "cat", $remotePath
                ) -Destination $auditCopy | Where-Object { $_ -is [pscustomobject] }) | Select-Object -Last 1
                if ($null -eq $fallback) {
                    throw "Unable to audit installed APK for ${packageName}: exec-out returned no status"
                }
                if (
                    $fallback.TimedOut -or
                    $fallback.ExitCode -ne 0 -or
                    (Get-Item -LiteralPath $auditCopy).Length -eq 0
                ) {
                    throw "Unable to audit installed APK for ${packageName}: pull=$($pull.Lines -join ' | '), exec-out=$($fallback.Lines -join ' | ')"
                }
                Write-Output "APK_AUDIT_FALLBACK_${packageName}=exec-out"
            }
            $actualHash = (Get-FileHash -LiteralPath $auditCopy -Algorithm SHA256).Hash.ToLowerInvariant()
            if ($actualHash -ne $expectedHashes[$packageName]) {
                throw "Installed APK hash mismatch for $packageName"
            }
            Write-Output "INSTALLED_SHA256_$packageName=$actualHash"
        } finally {
            Remove-Item -LiteralPath $auditCopy -Force -ErrorAction SilentlyContinue
        }
    }

    $testResult = Invoke-AdbBounded -Arguments @(
        "-s", $Serial,
        "shell", "am", "instrument", "-w", "-r",
        "-e", "class", $selector,
        "-e", "autojs.luaRuntime.officialProvider.enabled", "true",
        $runner
    )
    $testResult.Lines | ForEach-Object { Write-Output "TEST> $_" }
    $receipt = $testResult.Lines -join "`n"
    $forbidden = "FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|shortMsg=Process crashed|Process crashed|AssumptionViolated|ignored"
    if (
        $testResult.TimedOut -or
        $testResult.ExitCode -ne 0 -or
        $receipt -notmatch "(?m)^OK \(1 test\)\s*$" -or
        $receipt -notmatch "(?m)^INSTRUMENTATION_CODE: -1\s*$" -or
        $receipt -match $forbidden
    ) {
        throw "Instrumentation receipt was rejected"
    }
    Write-Output "OFFICIAL_PROVIDER_SMOKE=PASS"
    # Retain the historical label so earlier evidence parsers remain compatible.
    Write-Output "M3_OFFICIAL_PROVIDER_SMOKE=PASS"

    Get-DeviceProcesses |
        Where-Object { $_ -match "\s$([regex]::Escape($providerPackage)):lua_runtime\s*$" } |
        ForEach-Object { Write-Output "REMOTE_PROCESS=$($_.Trim())" }
} catch {
    $runFailure = $_
} finally {
    foreach ($packageName in @($testPackage, $providerPackage, $hostPackage)) {
        $null = Invoke-AdbBounded -Arguments @(
            "-s", $Serial, "shell", "am", "force-stop", $packageName
        )
    }
    foreach ($packageName in @($testPackage, $providerPackage, $hostPackage)) {
        if ($installAttempted[$packageName]) {
            $result = Invoke-AdbBounded -Arguments @("-s", $Serial, "uninstall", $packageName)
            $successCount = @($result.Lines | Where-Object { $_.Trim() -eq "Success" }).Count
            if (-not $result.TimedOut -and $result.ExitCode -eq 0 -and $successCount -eq 1) {
                Write-Output "UNINSTALL_$packageName=Success"
            } else {
                # A package that never landed is already clean. The authoritative all-user and
                # pm-path audit below decides whether a failed/unknown-package reply is harmful.
                Write-Output "UNINSTALL_ATTEMPT_$packageName=$($result.Lines -join ' | ')"
            }
        }
    }
}

try {
    Assert-PackagesAbsent -UserIds $userIds
    foreach ($packageName in $packages) {
        $pathResult = Invoke-AdbBounded -Arguments @(
            "-s", $Serial, "shell", "pm", "path", $packageName
        )
        if ($pathResult.TimedOut) {
            $cleanupFailures.Add("pm path timed out for $packageName")
            continue
        }
        $paths = @($pathResult.Lines |
            Where-Object { $_.Trim().StartsWith("package:") })
        if ($paths.Count -ne 0) {
            $cleanupFailures.Add("pm path remains for $packageName")
        }
    }
    $processes = @(Get-DeviceProcesses)
    foreach ($packageName in $packages) {
        if (@($processes | Where-Object { $_ -match "\s$([regex]::Escape($packageName))(?::\S+)?\s*$" }).Count) {
            $cleanupFailures.Add("process remains for $packageName")
        }
    }
    $activity = @(Invoke-DeviceLines -Arguments @("shell", "dumpsys", "activity", "processes"))
    if (@($activity | Select-String "mInstr=ActiveInstrumentation").Count -ne 0) {
        $cleanupFailures.Add("instrumentation remains active")
    }
} catch {
    $cleanupFailures.Add("post-state audit failed: $($_.Exception.Message)")
}

if ($cleanupFailures.Count -ne 0) {
    throw "DEVICE_DIRTY: $($cleanupFailures -join '; ')"
}
Write-Output "DEVICE_RESTORED_USERS=$($userIds -join ',')"
if ($null -ne $runFailure) {
    throw $runFailure
}
