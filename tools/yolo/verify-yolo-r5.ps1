[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $DeviceSerial,

    [string] $SiblingPath = "D:\idea-projects\AutoJs6-Plugin-Yolo-NCNN",

    [switch] $Qv710SoakConfirmedIdle
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$hostPackage = "org.autojs.autojs6"
$providerPackage = "io.github.supermonster003.autojs6.plugin.yolo.ncnn"
$fakeProviderPackage = "org.autojs.plugin.yolo.fake"
$testPackage = "$hostPackage.test"
$providerComponent = "$providerPackage/$providerPackage.provider.YoloProviderService"
$runner = "androidx.test.runner.AndroidJUnitRunner"
$protectedQv710 = "QV710AF65F"

$hostileClass = "org.autojs.autojs.core.plugin.yolo.YoloProviderR5ConformanceAndroidTest"
$hostileMethods = @(
    "duplicateAndLateTerminalsFailClosedWithoutCompletingAnotherRequest",
    "busyAndBothTimeoutCancellationOutcomesAreDeterministic",
    "wrongCallbackUidAndProviderDeathInvalidateThePinnedSession"
)
$descriptorClass = "org.autojs.autojs.core.plugin.yolo.YoloRealProviderR5DescriptorAndroidTest"
$descriptorMethod = "hostileRegularDescriptorsFailVersionedThenProductionHostDetectsAndReopens"
$resourceClass = "org.autojs.autojs.core.plugin.yolo.YoloRealProviderR5ResourceAndroidTest"
$resourceMethod = "productionHostMaintainsBoundedResourcesAcrossTwoHundredFrames"

$descriptorFixtureDirectory = "files/yolo-r5-fixture"
$resourceFixtureDirectory = "files/yolo-r5-resource-fixture"
$descriptorResultFile = "files/yolo-r5-descriptor-result.json"
$resourceResultFile = "files/yolo-r5-resource-result.json"
$resourceProgressFile = "$resourceResultFile.tmp"

function Assert-YoloR5GateCondition {
    param(
        [Parameter(Mandatory = $true)][bool] $Condition,
        [Parameter(Mandatory = $true)][string] $Message
    )
    if (-not $Condition) { throw $Message }
}

function Get-YoloR5GateRequiredFile {
    param(
        [Parameter(Mandatory = $true)][string] $Path,
        [Parameter(Mandatory = $true)][string] $Label
    )
    $fullPath = [System.IO.Path]::GetFullPath($Path)
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
        throw "$Label is missing: $fullPath"
    }
    return $fullPath
}

function Get-YoloR5GateRequiredDirectory {
    param(
        [Parameter(Mandatory = $true)][string] $Path,
        [Parameter(Mandatory = $true)][string] $Label
    )
    $fullPath = [System.IO.Path]::GetFullPath($Path)
    if (-not (Test-Path -LiteralPath $fullPath -PathType Container)) {
        throw "$Label is missing: $fullPath"
    }
    return $fullPath
}

function Invoke-YoloR5GateExternal {
    param(
        [Parameter(Mandatory = $true)][string] $FilePath,
        [string[]] $Arguments = @(),
        [string] $WorkingDirectory,
        [switch] $Quiet
    )
    $display = "$FilePath $($Arguments -join ' ')".Trim()
    if (-not $Quiet) { Write-Host "> $display" }
    if ([string]::IsNullOrWhiteSpace($WorkingDirectory)) {
        $output = @(& $FilePath @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    } else {
        Push-Location -LiteralPath $WorkingDirectory
        try {
            $output = @(& $FilePath @Arguments 2>&1)
            $exitCode = $LASTEXITCODE
        } finally {
            Pop-Location
        }
    }
    if (-not $Quiet) { $output | ForEach-Object { Write-Host $_ } }
    if ($exitCode -ne 0) { throw "External command failed with exit code $exitCode`: $display" }
    return $output
}

function Invoke-YoloR5GateAdb {
    param(
        [Parameter(Mandatory = $true)][string] $AdbPath,
        [Parameter(Mandatory = $true)][string] $Serial,
        [string[]] $Arguments = @(),
        [switch] $Quiet
    )
    return Invoke-YoloR5GateExternal -FilePath $AdbPath -Arguments (@("-s", $Serial) + $Arguments) -Quiet:$Quiet
}

function Get-YoloR5GateGitText {
    param(
        [Parameter(Mandatory = $true)][string] $Repository,
        [Parameter(Mandatory = $true)][string[]] $Arguments
    )
    return ((Invoke-YoloR5GateExternal -FilePath "git" -Arguments (@("-C", $Repository) + $Arguments) -Quiet) -join "`n").Trim()
}

function Get-YoloR5GateSha256 {
    param([Parameter(Mandatory = $true)][string] $Path)
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Get-YoloR5GateTextSha256 {
    param([Parameter(Mandatory = $true)][string] $Value)
    $algorithm = [System.Security.Cryptography.SHA256]::Create()
    try {
        $digest = $algorithm.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($Value))
        return ([System.BitConverter]::ToString($digest) -replace '-', '').ToLowerInvariant()
    } finally {
        $algorithm.Dispose()
    }
}

function Resolve-YoloR5GateAndroidTool {
    param(
        [Parameter(Mandatory = $true)][string[]] $RepositoryRoots,
        [Parameter(Mandatory = $true)][ValidateSet("adb", "apksigner")][string] $ToolName
    )
    $sdkCandidates = [System.Collections.Generic.List[string]]::new()
    foreach ($root in $RepositoryRoots) {
        $localProperties = Join-Path $root "local.properties"
        if (Test-Path -LiteralPath $localProperties -PathType Leaf) {
            $sdkLine = Get-Content -LiteralPath $localProperties |
                Where-Object { $_ -match '^sdk\.dir=' } |
                Select-Object -First 1
            if ($null -ne $sdkLine) {
                $value = ($sdkLine -replace '^sdk\.dir=', '') -replace '\\:', ':' -replace '\\\\', '\'
                if (-not [string]::IsNullOrWhiteSpace($value)) { $sdkCandidates.Add($value.Trim()) }
            }
        }
    }
    foreach ($environmentName in @("ANDROID_SDK_ROOT", "ANDROID_HOME")) {
        $value = [Environment]::GetEnvironmentVariable($environmentName)
        if (-not [string]::IsNullOrWhiteSpace($value)) { $sdkCandidates.Add($value.Trim()) }
    }
    foreach ($sdk in $sdkCandidates | Select-Object -Unique) {
        $root = [System.IO.Path]::GetFullPath($sdk)
        if ($ToolName -eq "adb") {
            $candidate = Join-Path $root "platform-tools\adb.exe"
            if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
        } else {
            $buildToolsRoot = Join-Path $root "build-tools"
            if (-not (Test-Path -LiteralPath $buildToolsRoot -PathType Container)) { continue }
            $candidate = Get-ChildItem -LiteralPath $buildToolsRoot -Directory -ErrorAction SilentlyContinue |
                Sort-Object { [version]($_.Name -replace '-.*$', '') } -Descending |
                ForEach-Object { Join-Path $_.FullName "apksigner.bat" } |
                Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } |
                Select-Object -First 1
            if ($null -ne $candidate) { return $candidate }
        }
    }
    throw "Unable to resolve Android SDK tool '$ToolName'"
}

function Resolve-YoloR5GateApkFromMetadata {
    param(
        [Parameter(Mandatory = $true)][string] $MetadataPath,
        [Parameter(Mandatory = $true)][string] $Label,
        [string] $Abi
    )
    $metadataFile = Get-YoloR5GateRequiredFile -Path $MetadataPath -Label "$Label output metadata"
    $metadata = Get-Content -Raw -LiteralPath $metadataFile | ConvertFrom-Json
    $elements = @($metadata.elements)
    $selected = if ([string]::IsNullOrWhiteSpace($Abi)) {
        $elements
    } else {
        @($elements | Where-Object {
            @($_.filters | Where-Object {
                $_.filterType -eq "ABI" -and $_.value -eq $Abi
            }).Count -eq 1
        })
    }
    $expectation = if ([string]::IsNullOrWhiteSpace($Abi)) { "one APK output" } else { "one $Abi APK output" }
    Assert-YoloR5GateCondition ($selected.Count -eq 1) "$Label must have exactly $expectation"
    $outputFile = [string]$selected[0].outputFile
    Assert-YoloR5GateCondition (-not [string]::IsNullOrWhiteSpace($outputFile)) "$Label output metadata has no outputFile"
    return Get-YoloR5GateRequiredFile -Path (Join-Path (Split-Path -Parent $metadataFile) $outputFile) -Label "$Label APK"
}

function Get-YoloR5GateApkSignerDigest {
    param(
        [Parameter(Mandatory = $true)][string] $ApkSigner,
        [Parameter(Mandatory = $true)][string] $ApkPath,
        [Parameter(Mandatory = $true)][string] $Label
    )
    $output = Invoke-YoloR5GateExternal -FilePath $ApkSigner -Arguments @("verify", "--verbose", "--print-certs", $ApkPath) -Quiet
    $text = $output -join "`n"
    $countMatches = [regex]::Matches($text, '(?im)^Number of signers:\s*([0-9]+)\s*$')
    Assert-YoloR5GateCondition (
        $countMatches.Count -eq 1 -and [int]$countMatches[0].Groups[1].Value -eq 1
    ) "$Label must have exactly one APK signer"
    $certificateMatches = [regex]::Matches(
        $text,
        '(?im)^(?:Signer #[0-9]+|V[0-9.]+ Signer(?: #[0-9]+)?)\s*:?\s*certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$'
    )
    $digests = @($certificateMatches | ForEach-Object {
        $_.Groups[1].Value.ToLowerInvariant()
    } | Sort-Object -Unique)
    Assert-YoloR5GateCondition ($digests.Count -eq 1) "$Label must expose exactly one signer certificate digest"
    return $digests[0]
}

function Get-YoloR5GateApkApplicationId {
    param(
        [Parameter(Mandatory = $true)][string] $Aapt2,
        [Parameter(Mandatory = $true)][string] $ApkPath,
        [Parameter(Mandatory = $true)][string] $Label
    )
    $output = Invoke-YoloR5GateExternal -FilePath $Aapt2 -Arguments @("dump", "badging", $ApkPath) -Quiet
    $text = $output -join "`n"
    $matches = [regex]::Matches($text, "(?m)^package: name='([^']+)'")
    Assert-YoloR5GateCondition ($matches.Count -eq 1) "$Label must expose exactly one applicationId"
    return $matches[0].Groups[1].Value
}

function Test-YoloR5GatePackageInstalled {
    param(
        [Parameter(Mandatory = $true)][string] $AdbPath,
        [Parameter(Mandatory = $true)][string] $Serial,
        [Parameter(Mandatory = $true)][string] $PackageName
    )
    $output = @(& $AdbPath -s $Serial shell pm path $PackageName 2>&1)
    $exitCode = $LASTEXITCODE
    if ($exitCode -eq 0) { return (($output -join "`n") -match '(?m)^package:') }
    if ($exitCode -eq 1) { return $false }
    throw "Cannot query package $PackageName on device $Serial (exit $exitCode)"
}

function Assert-YoloR5GateDeviceAccess {
    param(
        [Parameter(Mandatory = $true)][string] $Serial,
        [Parameter(Mandatory = $true)][bool] $Qv710ConfirmedIdle
    )
    if ($Serial.Equals($protectedQv710, [System.StringComparison]::OrdinalIgnoreCase)) {
        Assert-YoloR5GateCondition $Qv710ConfirmedIdle "Using $protectedQv710 requires explicit -Qv710SoakConfirmedIdle"
    } else {
        Assert-YoloR5GateCondition (-not $Qv710ConfirmedIdle) "-Qv710SoakConfirmedIdle is valid only for $protectedQv710"
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
    $serialPattern = [regex]::Escape($Serial)
    $runnerPattern = '(?i)(soak|endurance|long[-_ ]?run|stress[-_ ]?run|am\s+instrument|connected[a-z0-9_-]*androidtest|verify[-_a-z0-9]*r[0-9]+|adb(?:\.exe)?\s+-s)'
    $matches = @($processes | Where-Object {
        -not $excluded.Contains([int]$_.ProcessId) -and
        -not [string]::IsNullOrWhiteSpace($_.CommandLine) -and
        $_.CommandLine -match $serialPattern -and
        $_.CommandLine -match $runnerPattern
    })
    Assert-YoloR5GateCondition ($matches.Count -eq 0) (
        "Refusing device work because competing runner process IDs reference $Serial`: " +
        (($matches | ForEach-Object { $_.ProcessId }) -join ', ')
    )
}

function Invoke-YoloR5GateInstrumentation {
    param(
        [Parameter(Mandatory = $true)][string] $AdbPath,
        [Parameter(Mandatory = $true)][string] $Serial,
        [Parameter(Mandatory = $true)][string] $ArgumentName,
        [Parameter(Mandatory = $true)][string] $ClassName,
        [Parameter(Mandatory = $true)][string] $MethodName
    )
    $output = @(Invoke-YoloR5GateAdb -AdbPath $AdbPath -Serial $Serial -Arguments @(
        "shell", "am", "instrument", "-w", "-r",
        "-e", $ArgumentName, "true",
        "-e", "class", "$ClassName#$MethodName",
        "$testPackage/$runner"
    ))
    $text = $output -join "`n"
    Assert-YoloR5GateCondition ($text -match '(?m)^OK \(1 test\)\s*$') "$ClassName#$MethodName did not report one passing test"
    Assert-YoloR5GateCondition ($text -notmatch '(?im)^(FAILURES!!!|INSTRUMENTATION_FAILED:)|Process crashed|AssumptionViolated|INSTRUMENTATION_STATUS_CODE:\s*-3\s*$') (
        "$ClassName#$MethodName reported a failure, crash, or skipped assumption"
    )
    return [pscustomobject][ordered]@{
        class = $ClassName
        method = $MethodName
        status = "PASS"
        outputSha256 = Get-YoloR5GateTextSha256 -Value $text
        output = @($output | ForEach-Object { [string]$_ })
    }
}

function Get-YoloR5GateDeviceJson {
    param(
        [Parameter(Mandatory = $true)][string] $AdbPath,
        [Parameter(Mandatory = $true)][string] $Serial,
        [Parameter(Mandatory = $true)][string] $PrivatePath,
        [Parameter(Mandatory = $true)][string] $Label
    )
    $text = (Invoke-YoloR5GateAdb -AdbPath $AdbPath -Serial $Serial -Arguments @(
        "exec-out", "run-as", $hostPackage, "cat", $PrivatePath
    ) -Quiet) -join "`n"
    try { return $text | ConvertFrom-Json } catch { throw "$Label is not valid JSON`n$($_.Exception.Message)" }
}

function Write-YoloR5GateJson {
    param(
        [Parameter(Mandatory = $true)][string] $Path,
        [Parameter(Mandatory = $true)] $Value,
        [int] $Depth = 16
    )
    $json = $Value | ConvertTo-Json -Depth $Depth
    [System.IO.File]::WriteAllText($Path, "$json`n", [System.Text.UTF8Encoding]::new($false))
}

if ($DeviceSerial.Equals($protectedQv710, [System.StringComparison]::OrdinalIgnoreCase)) {
    Assert-YoloR5GateCondition $Qv710SoakConfirmedIdle.IsPresent "Using $protectedQv710 requires explicit -Qv710SoakConfirmedIdle"
} else {
    Assert-YoloR5GateCondition (-not $Qv710SoakConfirmedIdle.IsPresent) "-Qv710SoakConfirmedIdle is valid only for $protectedQv710"
}

$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$siblingRoot = Get-YoloR5GateRequiredDirectory -Path $SiblingPath -Label "YOLO NCNN sibling"
$mainGradle = Get-YoloR5GateRequiredFile -Path (Join-Path $repositoryRoot "gradlew.bat") -Label "Host Gradle wrapper"
$providerGradle = Get-YoloR5GateRequiredFile -Path (Join-Path $siblingRoot "gradlew.bat") -Label "Provider Gradle wrapper"
$packagingScript = Get-YoloR5GateRequiredFile -Path (Join-Path $repositoryRoot "tools\yolo\verify-yolo-r5-packaging.ps1") -Label "R5 packaging verifier"
$mainStatus = Get-YoloR5GateGitText -Repository $repositoryRoot -Arguments @("status", "--porcelain=v1", "--untracked-files=all")
$providerStatus = Get-YoloR5GateGitText -Repository $siblingRoot -Arguments @("status", "--porcelain=v1", "--untracked-files=all")
Assert-YoloR5GateCondition ([string]::IsNullOrEmpty($mainStatus)) "Host repository must be clean before generating R5 evidence"
Assert-YoloR5GateCondition ([string]::IsNullOrEmpty($providerStatus)) "Provider repository must be clean before generating R5 evidence"
$mainRevision = Get-YoloR5GateGitText -Repository $repositoryRoot -Arguments @("rev-parse", "HEAD")
$providerRevision = Get-YoloR5GateGitText -Repository $siblingRoot -Arguments @("rev-parse", "HEAD")

$reportDirectory = Join-Path $repositoryRoot "build\reports\yolo"
$summaryPath = Join-Path $reportDirectory "r5-summary.generated.json"
$hostileReportPath = Join-Path $reportDirectory "r5-hostile-device.generated.json"
$descriptorReportPath = Join-Path $reportDirectory "r5-descriptor-device.generated.json"
$resourceReportPath = Join-Path $reportDirectory "r5-resource-device.generated.json"
$packagingReportPath = Join-Path $reportDirectory "r5-packaging.generated.json"
[void](New-Item -ItemType Directory -Force -Path $reportDirectory)
foreach ($stale in @($summaryPath, $hostileReportPath, $descriptorReportPath, $resourceReportPath, $packagingReportPath)) {
    if (Test-Path -LiteralPath $stale -PathType Leaf) { Remove-Item -LiteralPath $stale -Force }
}

$officialTrackedRoot = Get-YoloR5GateRequiredDirectory -Path (Join-Path $siblingRoot "fixtures\yolo11n") -Label "tracked official YOLO11n fixture metadata"
$officialLocalRoot = Get-YoloR5GateRequiredDirectory -Path (Join-Path $siblingRoot "fixtures\local\yolo11n") -Label "local-only official YOLO11n fixture"
$officialLockFile = Get-YoloR5GateRequiredFile -Path (Join-Path $officialTrackedRoot "fixture.lock.json") -Label "official fixture lock"
$officialLock = Get-Content -Raw -LiteralPath $officialLockFile | ConvertFrom-Json
$officialFiles = [ordered]@{
    "model-manifest-v1.json" = Get-YoloR5GateRequiredFile -Path (Join-Path $officialTrackedRoot "model-manifest-v1.json") -Label "official model manifest"
    "expected-bus-detections.json" = Get-YoloR5GateRequiredFile -Path (Join-Path $officialTrackedRoot "expected-bus-detections.json") -Label "official expected detections"
    "model.ncnn.param" = Get-YoloR5GateRequiredFile -Path (Join-Path $officialLocalRoot "model.ncnn.param") -Label "official NCNN param"
    "model.ncnn.bin" = Get-YoloR5GateRequiredFile -Path (Join-Path $officialLocalRoot "model.ncnn.bin") -Label "official NCNN weights"
    "bus.jpg" = Get-YoloR5GateRequiredFile -Path (Join-Path $officialLocalRoot "bus.jpg") -Label "official bus image"
}
$officialEvidence = [ordered]@{}
foreach ($entry in $officialFiles.GetEnumerator()) {
    $locked = @($officialLock.artifacts | Where-Object { $_.name -eq $entry.Key })
    Assert-YoloR5GateCondition ($locked.Count -eq 1) "Official fixture lock must contain exactly one $($entry.Key) entry"
    $length = (Get-Item -LiteralPath $entry.Value).Length
    $sha256 = Get-YoloR5GateSha256 -Path $entry.Value
    Assert-YoloR5GateCondition ($length -eq [long]$locked[0].length) "$($entry.Key) length differs from official fixture lock"
    Assert-YoloR5GateCondition ($sha256 -eq ([string]$locked[0].sha256).ToLowerInvariant()) "$($entry.Key) digest differs from official fixture lock"
    $officialEvidence[$entry.Key] = [ordered]@{ length = $length; sha256 = $sha256 }
}
$officialEvidence["fixture.lock.json"] = [ordered]@{
    length = (Get-Item -LiteralPath $officialLockFile).Length
    sha256 = Get-YoloR5GateSha256 -Path $officialLockFile
}

$gradleArguments = @(
    "--console=plain",
    "--no-daemon",
    "--no-parallel",
    "--max-workers=2",
    "-Pkotlin.compiler.execution.strategy=in-process",
    "-Pautojs.gradle.build.number.auto.increment.enabled=false",
    "-Pautojs.gradle.build.time.update.enabled=false"
)
$providerTasks = @(
    ":app:testDebugUnitTest",
    ":app:assembleRc",
    ":app:compileDebugAndroidTestKotlin"
)
$hostTasks = @(
    ":app:assembleAppDebug",
    ":app:assembleAppDebugAndroidTest",
    ":test-apps:yolo-provider-conformance:assembleDebug"
)
Invoke-YoloR5GateExternal -FilePath $providerGradle -Arguments @($gradleArguments + $providerTasks) -WorkingDirectory $siblingRoot
Invoke-YoloR5GateExternal -FilePath $mainGradle -Arguments @($gradleArguments + $hostTasks) -WorkingDirectory $repositoryRoot

$providerApk = Resolve-YoloR5GateApkFromMetadata -MetadataPath (Join-Path $siblingRoot "app\build\outputs\apk\rc\output-metadata.json") -Label "Provider RC"
$hostApk = Resolve-YoloR5GateApkFromMetadata -MetadataPath (Join-Path $repositoryRoot "app\build\outputs\apk\app\debug\output-metadata.json") -Label "Host" -Abi "arm64-v8a"
$testApk = Resolve-YoloR5GateApkFromMetadata -MetadataPath (Join-Path $repositoryRoot "app\build\outputs\apk\androidTest\app\debug\output-metadata.json") -Label "Instrumentation"
$fakeProviderApk = Resolve-YoloR5GateApkFromMetadata -MetadataPath (Join-Path $repositoryRoot "test-apps\yolo-provider-conformance\build\outputs\apk\debug\output-metadata.json") -Label "Fake provider"

$packagingOutput = @(& $packagingScript -SiblingPath $siblingRoot -ProviderApk $providerApk -HostApk $hostApk 2>&1)
$packagingOutput | ForEach-Object { Write-Host $_ }
Assert-YoloR5GateCondition (Test-Path -LiteralPath $packagingReportPath -PathType Leaf) "R5 packaging verifier produced no report"
$packaging = Get-Content -Raw -LiteralPath $packagingReportPath | ConvertFrom-Json
Assert-YoloR5GateCondition ([int]$packaging.schemaVersion -eq 1 -and $packaging.status -eq "PASS") "R5 packaging report did not report PASS"
Assert-YoloR5GateCondition (-not [bool]$packaging.providerApk.debuggable.value) "Provider RC is debuggable"
Assert-YoloR5GateCondition ($packaging.claims.APK_PACKAGING -eq "PASS") "R5 APK packaging sub-gate did not pass"
Assert-YoloR5GateCondition ($packaging.claims.ELF_16K_COMPATIBLE -eq "PASS") "R5 static ELF 16K sub-gate did not pass"
Assert-YoloR5GateCondition ($packaging.claims.NATIVE_LOAD_ARM64_DEVICE -eq "NOT_RUN") "Packaging-only gate unexpectedly claimed device native load"
Assert-YoloR5GateCondition ($packaging.claims.NATIVE_LOAD_16K_DEVICE -eq "NOT_RUN") "Packaging-only gate unexpectedly claimed 16K device native load"
$aapt2 = Get-YoloR5GateRequiredFile -Path ([string]$packaging.tools.aapt2) -Label "aapt2 selected by packaging verifier"
$apkApplicationIds = [ordered]@{
    host = Get-YoloR5GateApkApplicationId -Aapt2 $aapt2 -ApkPath $hostApk -Label "Host"
    instrumentation = Get-YoloR5GateApkApplicationId -Aapt2 $aapt2 -ApkPath $testApk -Label "Instrumentation"
    provider = Get-YoloR5GateApkApplicationId -Aapt2 $aapt2 -ApkPath $providerApk -Label "Provider RC"
    fakeProvider = Get-YoloR5GateApkApplicationId -Aapt2 $aapt2 -ApkPath $fakeProviderApk -Label "Fake provider"
}
Assert-YoloR5GateCondition ($apkApplicationIds.host -ceq $hostPackage) "Host APK applicationId is unexpected"
Assert-YoloR5GateCondition ($apkApplicationIds.instrumentation -ceq $testPackage) "Instrumentation APK applicationId is unexpected"
Assert-YoloR5GateCondition ($apkApplicationIds.provider -ceq $providerPackage) "Provider RC APK applicationId is unexpected"
Assert-YoloR5GateCondition ($apkApplicationIds.fakeProvider -ceq $fakeProviderPackage) "Fake provider APK applicationId is unexpected"

$apkSigner = Resolve-YoloR5GateAndroidTool -RepositoryRoots @($repositoryRoot, $siblingRoot) -ToolName "apksigner"
$signers = [ordered]@{
    host = Get-YoloR5GateApkSignerDigest -ApkSigner $apkSigner -ApkPath $hostApk -Label "Host"
    instrumentation = Get-YoloR5GateApkSignerDigest -ApkSigner $apkSigner -ApkPath $testApk -Label "Instrumentation"
    provider = Get-YoloR5GateApkSignerDigest -ApkSigner $apkSigner -ApkPath $providerApk -Label "Provider RC"
    fakeProvider = Get-YoloR5GateApkSignerDigest -ApkSigner $apkSigner -ApkPath $fakeProviderApk -Label "Fake provider"
}
Assert-YoloR5GateCondition (@($signers.Values | Select-Object -Unique).Count -eq 1) "Host, instrumentation, real provider, and fake provider APK signers differ"

$postBuildMainStatus = Get-YoloR5GateGitText -Repository $repositoryRoot -Arguments @("status", "--porcelain=v1", "--untracked-files=all")
$postBuildProviderStatus = Get-YoloR5GateGitText -Repository $siblingRoot -Arguments @("status", "--porcelain=v1", "--untracked-files=all")
Assert-YoloR5GateCondition ([string]::IsNullOrEmpty($postBuildMainStatus)) "Host repository became dirty during R5 build/packaging"
Assert-YoloR5GateCondition ([string]::IsNullOrEmpty($postBuildProviderStatus)) "Provider repository became dirty during R5 build/packaging"
Assert-YoloR5GateCondition ((Get-YoloR5GateGitText -Repository $repositoryRoot -Arguments @("rev-parse", "HEAD")) -eq $mainRevision) "Host revision changed during R5 gate"
Assert-YoloR5GateCondition ((Get-YoloR5GateGitText -Repository $siblingRoot -Arguments @("rev-parse", "HEAD")) -eq $providerRevision) "Provider revision changed during R5 gate"

# This is intentionally the last host-side check before the first ADB command.
Assert-YoloR5GateDeviceAccess -Serial $DeviceSerial -Qv710ConfirmedIdle $Qv710SoakConfirmedIdle.IsPresent
$adb = Resolve-YoloR5GateAndroidTool -RepositoryRoots @($repositoryRoot, $siblingRoot) -ToolName "adb"
$deviceRows = @(Invoke-YoloR5GateExternal -FilePath $adb -Arguments @("devices", "-l") -Quiet)
$onlineRows = @($deviceRows | Where-Object { $_ -match "^$([regex]::Escape($DeviceSerial))\s+device(?:\s|$)" })
Assert-YoloR5GateCondition ($onlineRows.Count -eq 1) "Device $DeviceSerial is not uniquely online"
$deviceApi = [int]((Invoke-YoloR5GateAdb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "getprop", "ro.build.version.sdk") -Quiet) -join '').Trim()
$deviceAbi = ((Invoke-YoloR5GateAdb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "getprop", "ro.product.cpu.abi") -Quiet) -join '').Trim()
$deviceModel = ((Invoke-YoloR5GateAdb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "getprop", "ro.product.model") -Quiet) -join '').Trim()
$deviceFingerprint = ((Invoke-YoloR5GateAdb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "getprop", "ro.build.fingerprint") -Quiet) -join '').Trim()
Assert-YoloR5GateCondition ($deviceApi -ge 24) "R5 requires Android API 24 or newer"
Assert-YoloR5GateCondition ($deviceAbi -eq "arm64-v8a") "R5 provider is arm64-only; device ABI is $deviceAbi"
$pageSizeOutput = @(& $adb -s $DeviceSerial shell getconf PAGESIZE 2>&1)
$pageSizeExitCode = $LASTEXITCODE
$devicePageSize = if ($pageSizeExitCode -eq 0) { [long](($pageSizeOutput -join '').Trim()) } else { $null }

$packages = @($hostPackage, $providerPackage, $fakeProviderPackage, $testPackage)
foreach ($packageName in $packages) {
    Assert-YoloR5GateCondition (-not (Test-YoloR5GatePackageInstalled -AdbPath $adb -Serial $DeviceSerial -PackageName $packageName)) (
        "R5 refuses to replace pre-existing package $packageName on $DeviceSerial"
    )
}

$deviceStaging = "/data/local/tmp/autojs6-yolo-r5-$([guid]::NewGuid().ToString('N'))"
Assert-YoloR5GateCondition ($deviceStaging -match '^/data/local/tmp/autojs6-yolo-r5-[0-9a-f]{32}$') "Unsafe R5 device staging path"
$hostileEvidence = $null
$descriptorResult = $null
$resourceResult = $null
$cleanupComplete = $false
try {
    Invoke-YoloR5GateAdb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "mkdir", "-p", $deviceStaging) | Out-Null
    $stagingFiles = [ordered]@{
        "model-manifest-v1.json" = $officialFiles["model-manifest-v1.json"]
        "model.ncnn.param" = $officialFiles["model.ncnn.param"]
        "model.ncnn.bin" = $officialFiles["model.ncnn.bin"]
        "bus.jpg" = $officialFiles["bus.jpg"]
        "expected-bus-detections.json" = $officialFiles["expected-bus-detections.json"]
    }
    foreach ($entry in $stagingFiles.GetEnumerator()) {
        Invoke-YoloR5GateAdb -AdbPath $adb -Serial $DeviceSerial -Arguments @("push", $entry.Value, "$deviceStaging/$($entry.Key)") | Out-Null
        Invoke-YoloR5GateAdb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "chmod", "0644", "$deviceStaging/$($entry.Key)") | Out-Null
    }

    # Host defines the signature permission; install it before either provider and the test package.
    Invoke-YoloR5GateAdb -AdbPath $adb -Serial $DeviceSerial -Arguments @("install", "-r", $hostApk) | Out-Null
    Invoke-YoloR5GateAdb -AdbPath $adb -Serial $DeviceSerial -Arguments @("install", "-r", $providerApk) | Out-Null
    Invoke-YoloR5GateAdb -AdbPath $adb -Serial $DeviceSerial -Arguments @("install", "-r", "-t", $fakeProviderApk) | Out-Null
    Invoke-YoloR5GateAdb -AdbPath $adb -Serial $DeviceSerial -Arguments @("install", "-r", "-t", $testApk) | Out-Null
    foreach ($packageName in $packages) {
        Assert-YoloR5GateCondition (Test-YoloR5GatePackageInstalled -AdbPath $adb -Serial $DeviceSerial -PackageName $packageName) (
            "Expected R5 package was not installed: $packageName"
        )
    }

    foreach ($directory in @($descriptorFixtureDirectory, $resourceFixtureDirectory)) {
        Invoke-YoloR5GateAdb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "run-as", $hostPackage, "rm", "-rf", $directory) | Out-Null
        Invoke-YoloR5GateAdb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "run-as", $hostPackage, "mkdir", "-p", $directory) | Out-Null
        foreach ($leafName in $stagingFiles.Keys) {
            Invoke-YoloR5GateAdb -AdbPath $adb -Serial $DeviceSerial -Arguments @(
                "shell", "run-as", $hostPackage, "cp", "$deviceStaging/$leafName", "$directory/$leafName"
            ) | Out-Null
            Invoke-YoloR5GateAdb -AdbPath $adb -Serial $DeviceSerial -Arguments @(
                "shell", "run-as", $hostPackage, "chmod", "0400", "$directory/$leafName"
            ) | Out-Null
        }
    }
    foreach ($privateResult in @($descriptorResultFile, $resourceResultFile, $resourceProgressFile)) {
        Invoke-YoloR5GateAdb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "run-as", $hostPackage, "rm", "-f", $privateResult) | Out-Null
    }

    $hostileRuns = [System.Collections.Generic.List[object]]::new()
    foreach ($method in $hostileMethods) {
        $hostileRuns.Add((Invoke-YoloR5GateInstrumentation -AdbPath $adb -Serial $DeviceSerial -ArgumentName "yoloR5ConformanceEnabled" -ClassName $hostileClass -MethodName $method))
    }
    $hostileEvidence = [ordered]@{
        schemaVersion = 1
        roadmapStage = "R5-HOSTILE"
        status = "PASS"
        productionHost = $true
        fakeProviderIsSeparatePackage = $true
        explicitExactProviderSelection = $true
        tests = @($hostileRuns)
        cases = @(
            "duplicate-terminal",
            "late-old-terminal",
            "host-busy",
            "timeout-cancel-ack",
            "timeout-cancel-no-ack",
            "wrong-callback-uid",
            "provider-death"
        )
        identityNegative = "wrong-callback-uid"
        temporaryWrongSignerPackage = "NOT_CREATED_UID_NEGATIVE_USED"
        claims = [ordered]@{
            hostileBinder = "PASS"
            retryAfterDispatch = "NOT_PERFORMED"
            production = "NOT_CLAIMED"
        }
    }
    Write-YoloR5GateJson -Path $hostileReportPath -Value $hostileEvidence

    # The resource test requires a cold real-provider process, so it precedes the descriptor test.
    $resourceRun = Invoke-YoloR5GateInstrumentation -AdbPath $adb -Serial $DeviceSerial -ArgumentName "yoloR5ResourceEnabled" -ClassName $resourceClass -MethodName $resourceMethod
    $resourceResult = Get-YoloR5GateDeviceJson -AdbPath $adb -Serial $DeviceSerial -PrivatePath $resourceResultFile -Label "R5 resource result"
    Assert-YoloR5GateCondition ([int]$resourceResult.schemaVersion -eq 1 -and $resourceResult.status -eq "PASS") "R5 resource result did not report PASS"
    Assert-YoloR5GateCondition ($resourceResult.component -eq $providerComponent) "R5 resource test used an unexpected provider component"
    Assert-YoloR5GateCondition ([bool]$resourceResult.productionHost -and [bool]$resourceResult.sameSigner) "R5 resource test did not use the production same-signer Host path"
    Assert-YoloR5GateCondition ([int]$resourceResult.warmupFrames -eq 20 -and [int]$resourceResult.measuredFrames -eq 200) "R5 resource frame counts are incomplete"
    Assert-YoloR5GateCondition ([int]$resourceResult.resourceSampleCount -eq 11) "R5 resource sample count is incomplete"
    Assert-YoloR5GateCondition ([bool]$resourceResult.uniqueTerminalEveryFrame -and [bool]$resourceResult.imagePfdClosedEveryFrame -and [bool]$resourceResult.providerPidStableEveryFrame) "R5 per-frame transport/lifecycle checks failed"
    Assert-YoloR5GateCondition (-not [bool]$resourceResult.performanceThresholdApplied) "R5 must record performance without applying a performance threshold"
    Assert-YoloR5GateCondition ($resourceResult.claims.resource200Frame -eq "PASS" -and $resourceResult.claims.hostFd -eq "PASS" -and $resourceResult.claims.providerFd -eq "PASS" -and $resourceResult.claims.sessionHandleLifecycle -eq "PASS") "R5 resource bounds did not pass"
    Assert-YoloR5GateCondition ($resourceResult.claims.rssRunaway -eq "NOT_OBSERVED") "R5 resource run observed sustained RSS runaway"
    Assert-YoloR5GateCondition ([bool]$resourceResult.resources.providerFd.withinAllowance -and [bool]$resourceResult.resources.hostFd.withinAllowance) "R5 FD medians exceeded the bounded-growth allowance"
    Assert-YoloR5GateCondition (-not [bool]$resourceResult.resources.providerRss.runaway -and -not [bool]$resourceResult.resources.hostRss.runaway) "R5 resource samples show sustained RSS runaway"
    foreach ($closedSnapshot in @($resourceResult.resources.afterPrimaryClose, $resourceResult.resources.afterReopenedClose)) {
        Assert-YoloR5GateCondition ([int]$closedSnapshot.activeSessions -eq 0 -and -not [bool]$closedSnapshot.sessionGate -and [int]$closedSnapshot.activeNativeHandles -eq 0 -and [int]$closedSnapshot.modelDirCount -eq 0) "R5 provider retained a session, gate, native handle, or model directory after close"
    }
    Assert-YoloR5GateCondition ($resourceResult.claims.performance -eq "RECORDED_NO_THRESHOLD") "R5 performance evidence boundary changed"
    Assert-YoloR5GateCondition ($resourceResult.claims.soak -eq "NOT_RUN" -and $resourceResult.claims.production -eq "NOT_CLAIMED") "R5 resource result over-claimed soak or production"
    Write-YoloR5GateJson -Path $resourceReportPath -Value ([ordered]@{ instrumentation = $resourceRun; result = $resourceResult })

    $descriptorRun = Invoke-YoloR5GateInstrumentation -AdbPath $adb -Serial $DeviceSerial -ArgumentName "yoloR5DescriptorEnabled" -ClassName $descriptorClass -MethodName $descriptorMethod
    $descriptorResult = Get-YoloR5GateDeviceJson -AdbPath $adb -Serial $DeviceSerial -PrivatePath $descriptorResultFile -Label "R5 descriptor result"
    Assert-YoloR5GateCondition ([int]$descriptorResult.schemaVersion -eq 1 -and $descriptorResult.status -eq "PASS") "R5 descriptor result did not report PASS"
    Assert-YoloR5GateCondition ($descriptorResult.roadmapStage -eq "R5-DESCRIPTOR" -and $descriptorResult.component -eq $providerComponent) "R5 descriptor result identity is unexpected"
    Assert-YoloR5GateCondition ([bool]$descriptorResult.actionlessExactComponentBind -and [bool]$descriptorResult.remoteProviderBinder) "R5 descriptor negative did not use the exact remote provider"
    $descriptorCases = @($descriptorResult.hostileDescriptors)
    Assert-YoloR5GateCondition ($descriptorCases.Count -eq 3) "R5 descriptor result does not contain exactly three hostile cases"
    $expectedDescriptorCases = @("descriptor-shorter-than-declared", "descriptor-longer-than-declared", "same-length-bad-sha256")
    Assert-YoloR5GateCondition (@($descriptorCases | Where-Object { $_.case -notin $expectedDescriptorCases }).Count -eq 0) "R5 descriptor result contains an unexpected hostile case"
    Assert-YoloR5GateCondition (@($descriptorCases | Where-Object { $_.code -ne "INVALID_REQUEST" -or -not [bool]$_.versionedFailure -or -not [bool]$_.hostDescriptorsClosed }).Count -eq 0) "R5 descriptor failures were not versioned, stable, and descriptor-clean"
    Assert-YoloR5GateCondition ([int]$descriptorResult.idleAfterHostileDescriptors.activeSessions -eq 0 -and -not [bool]$descriptorResult.idleAfterHostileDescriptors.sessionGate -and [int]$descriptorResult.idleAfterHostileDescriptors.activeNativeHandles -eq 0 -and [int]$descriptorResult.idleAfterHostileDescriptors.modelDirCount -eq 0) "R5 provider was not idle after hostile descriptors"
    Assert-YoloR5GateCondition ([bool]$descriptorResult.production.remoteSessionBinder -and [bool]$descriptorResult.production.modelDescriptorsClosedAfterOpen -and [bool]$descriptorResult.production.imageDescriptorClosedAfterDispatch) "R5 production descriptor lifecycle failed"
    Assert-YoloR5GateCondition ([bool]$descriptorResult.production.idempotentClose -and [bool]$descriptorResult.production.reopenSucceeded) "R5 provider did not close idempotently and reopen after hostile descriptors"
    Write-YoloR5GateJson -Path $descriptorReportPath -Value ([ordered]@{ instrumentation = $descriptorRun; result = $descriptorResult })
} finally {
    $cleanupErrors = [System.Collections.Generic.List[string]]::new()
    $hostPresent = $false
    try { $hostPresent = Test-YoloR5GatePackageInstalled -AdbPath $adb -Serial $DeviceSerial -PackageName $hostPackage } catch { $cleanupErrors.Add($_.Exception.Message) }
    if ($hostPresent) {
        foreach ($privatePath in @($descriptorFixtureDirectory, $resourceFixtureDirectory)) {
            try { Invoke-YoloR5GateAdb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "run-as", $hostPackage, "rm", "-rf", $privatePath) -Quiet | Out-Null } catch { $cleanupErrors.Add($_.Exception.Message) }
        }
        foreach ($privatePath in @($descriptorResultFile, $resourceResultFile, $resourceProgressFile)) {
            try { Invoke-YoloR5GateAdb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "run-as", $hostPackage, "rm", "-f", $privatePath) -Quiet | Out-Null } catch { $cleanupErrors.Add($_.Exception.Message) }
        }
    }
    try { Invoke-YoloR5GateAdb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "rm", "-rf", $deviceStaging) -Quiet | Out-Null } catch { $cleanupErrors.Add($_.Exception.Message) }
    try { Invoke-YoloR5GateAdb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "test", "!", "-e", $deviceStaging) -Quiet | Out-Null } catch { $cleanupErrors.Add("Device staging remains after cleanup: $deviceStaging") }
    foreach ($packageName in @($testPackage, $fakeProviderPackage, $providerPackage, $hostPackage)) {
        try {
            if (Test-YoloR5GatePackageInstalled -AdbPath $adb -Serial $DeviceSerial -PackageName $packageName) {
                Invoke-YoloR5GateAdb -AdbPath $adb -Serial $DeviceSerial -Arguments @("uninstall", $packageName) -Quiet | Out-Null
            }
        } catch { $cleanupErrors.Add($_.Exception.Message) }
    }
    foreach ($packageName in $packages) {
        try {
            if (Test-YoloR5GatePackageInstalled -AdbPath $adb -Serial $DeviceSerial -PackageName $packageName) {
                $cleanupErrors.Add("Package remains installed after cleanup: $packageName")
            }
        } catch { $cleanupErrors.Add($_.Exception.Message) }
    }
    $cleanupComplete = $cleanupErrors.Count -eq 0
    if (-not $cleanupComplete) { throw "R5 cleanup failed: $($cleanupErrors -join '; ')" }
}

Assert-YoloR5GateCondition ($null -ne $hostileEvidence -and $null -ne $descriptorResult -and $null -ne $resourceResult) "R5 device evidence is incomplete"
$finalMainStatus = Get-YoloR5GateGitText -Repository $repositoryRoot -Arguments @("status", "--porcelain=v1", "--untracked-files=all")
$finalProviderStatus = Get-YoloR5GateGitText -Repository $siblingRoot -Arguments @("status", "--porcelain=v1", "--untracked-files=all")
Assert-YoloR5GateCondition ([string]::IsNullOrEmpty($finalMainStatus)) "Host repository is not clean after R5 device verification"
Assert-YoloR5GateCondition ([string]::IsNullOrEmpty($finalProviderStatus)) "Provider repository is not clean after R5 device verification"
Assert-YoloR5GateCondition ((Get-YoloR5GateGitText -Repository $repositoryRoot -Arguments @("rev-parse", "HEAD")) -eq $mainRevision) "Host revision changed during R5 device verification"
Assert-YoloR5GateCondition ((Get-YoloR5GateGitText -Repository $siblingRoot -Arguments @("rev-parse", "HEAD")) -eq $providerRevision) "Provider revision changed during R5 device verification"

$native16kClaim = if ($null -ne $devicePageSize -and $devicePageSize -eq 16384L) { "PASS" } else { "NOT_RUN_NO_16K_DEVICE" }
$summary = [ordered]@{
    schemaVersion = 1
    roadmapStage = "R5"
    status = "PASS"
    evidenceLevel = "clean-source-serial-build-test-signed-nondebuggable-arm64-device-hostile-descriptor-resource"
    generatedAtUtc = [DateTime]::UtcNow.ToString("o")
    repositories = [ordered]@{
        host = [ordered]@{ revision = $mainRevision; cleanBefore = $true; cleanAfter = $true }
        provider = [ordered]@{ revision = $providerRevision; cleanBefore = $true; cleanAfter = $true }
    }
    invocation = [ordered]@{
        script = "tools/yolo/verify-yolo-r5.ps1"
        explicitDeviceSerial = $DeviceSerial
        parallelGradle = $false
        connectedGradleTasks = @()
        providerGradleTasks = $providerTasks
        hostGradleTasks = $hostTasks
        avdStartImplemented = $false
    }
    device = [ordered]@{
        serial = $DeviceSerial
        model = $deviceModel
        api = $deviceApi
        abi = $deviceAbi
        fingerprint = $deviceFingerprint
        pageSizeBytes = $devicePageSize
        qv710SoakConfirmedIdle = [bool]($DeviceSerial -eq $protectedQv710 -and $Qv710SoakConfirmedIdle.IsPresent)
        competingRunnerScanImmediatelyBeforeAdb = "PASS"
        avdStartedByGate = $false
    }
    apks = [ordered]@{
        host = [ordered]@{ path = $hostApk; applicationId = $apkApplicationIds.host; sha256 = Get-YoloR5GateSha256 -Path $hostApk; signerSha256 = $signers.host }
        instrumentation = [ordered]@{ path = $testApk; applicationId = $apkApplicationIds.instrumentation; sha256 = Get-YoloR5GateSha256 -Path $testApk; signerSha256 = $signers.instrumentation }
        providerRc = [ordered]@{ path = $providerApk; applicationId = $apkApplicationIds.provider; sha256 = Get-YoloR5GateSha256 -Path $providerApk; signerSha256 = $signers.provider; debuggable = $false; abi = "arm64-v8a"; testSigned = $true }
        fakeProvider = [ordered]@{ path = $fakeProviderApk; applicationId = $apkApplicationIds.fakeProvider; sha256 = Get-YoloR5GateSha256 -Path $fakeProviderApk; signerSha256 = $signers.fakeProvider; testOnly = $true }
        allFourSameSigner = $true
    }
    fixture = [ordered]@{
        name = "official-yolo11n-bus"
        modelAndImageExternalOnly = $true
        hashes = $officialEvidence
    }
    hostile = $hostileEvidence
    resource = $resourceResult
    descriptor = $descriptorResult
    packaging = $packaging
    resultArtifacts = [ordered]@{
        hostile = $hostileReportPath
        descriptor = $descriptorReportPath
        resource = $resourceReportPath
        packaging = $packagingReportPath
    }
    evidenceBoundary = [ordered]@{
        performanceSamplesRecorded = $true
        performanceThresholdApplied = $false
        longSoakRun = $false
        fullApiMatrixRun = $false
        staticElf16kKeptSeparateFromDeviceLoad = $true
        productionReleaseClaimed = $false
    }
    cleanup = [ordered]@{
        packagesAbsentBefore = $true
        packagesAbsentAfter = $cleanupComplete
        uniqueStagingRemoved = $cleanupComplete
        avdStoppedByGate = $false
    }
    claims = [ordered]@{
        R5 = "PASS"
        SOURCE = "PASS"
        ANDROID_BUILD = "PASS"
        HOSTILE_BINDER = "PASS"
        WRONG_CALLBACK_UID = "PASS"
        DESCRIPTOR_INTEGRITY = "PASS"
        RESOURCE_200_FRAME = "PASS"
        APK_PACKAGING = "PASS"
        ELF_16K_COMPATIBLE = "PASS_STATIC_ONLY"
        NATIVE_LOAD_ARM64_DEVICE = "PASS"
        NATIVE_LOAD_16K_DEVICE = $native16kClaim
        PERFORMANCE = "RECORDED_NO_THRESHOLD"
        SOAK = "NOT_RUN"
        RC = "TEST_SIGNED_NONDEBUGGABLE_DEVICE_VALIDATED"
        PRODUCTION = "NOT_CLAIMED"
    }
}
Write-YoloR5GateJson -Path $summaryPath -Value $summary -Depth 20
Write-Host "YOLO_R5=PASS"
Write-Host "YOLO_R5_REPORT=$summaryPath"
