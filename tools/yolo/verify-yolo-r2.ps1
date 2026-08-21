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
$testPackage = "$hostPackage.test"
$providerComponent = "$providerPackage/$providerPackage.provider.YoloProviderService"
$runner = "androidx.test.runner.AndroidJUnitRunner"
$testClass = "org.autojs.autojs.core.plugin.yolo.YoloRealProviderR2AndroidTest"
$testMethod = "productionHostUsesRemoteBinderOwnsPfdAndReopensAfterIdempotentClose"
$protectedQv710 = "QV710AF65F"
$fixturePrivateDirectory = "files/yolo-r2-fixture"
$resultPrivateFile = "files/yolo-r2-result.json"
$forbiddenFixtureLeafNames = @(
    "model.ncnn.param",
    "model.ncnn.bin",
    "bus.jpg",
    "model-manifest-v1.json",
    "expected-bus-detections.json",
    "fixture.lock.json",
    "metadata.yaml",
    "yolo11n.pt"
)

function Assert-YoloR2Condition {
    param(
        [Parameter(Mandatory = $true)][bool] $Condition,
        [Parameter(Mandatory = $true)][string] $Message
    )
    if (-not $Condition) { throw $Message }
}

function Get-YoloR2RequiredFile {
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

function Get-YoloR2RequiredDirectory {
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

function Invoke-YoloR2External {
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

function Invoke-YoloR2Adb {
    param(
        [Parameter(Mandatory = $true)][string] $AdbPath,
        [Parameter(Mandatory = $true)][string] $Serial,
        [string[]] $Arguments = @(),
        [switch] $Quiet
    )
    return Invoke-YoloR2External -FilePath $AdbPath -Arguments (@("-s", $Serial) + $Arguments) -Quiet:$Quiet
}

function Get-YoloR2Sha256 {
    param([Parameter(Mandatory = $true)][string] $Path)
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Get-YoloR2GitText {
    param(
        [Parameter(Mandatory = $true)][string] $Repository,
        [Parameter(Mandatory = $true)][string[]] $Arguments
    )
    return ((Invoke-YoloR2External -FilePath "git" -Arguments (@("-C", $Repository) + $Arguments) -Quiet) -join "`n").Trim()
}

function Resolve-YoloR2AndroidTool {
    param(
        [Parameter(Mandatory = $true)][string] $RepositoryRoot,
        [Parameter(Mandatory = $true)][string] $ToolName
    )
    $sdkCandidates = [System.Collections.Generic.List[string]]::new()
    $localProperties = Join-Path $RepositoryRoot "local.properties"
    if (Test-Path -LiteralPath $localProperties -PathType Leaf) {
        $sdkLine = Get-Content -LiteralPath $localProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($null -ne $sdkLine) {
            $value = ($sdkLine -replace '^sdk\.dir=', '') -replace '\\:', ':' -replace '\\\\', '\'
            if (-not [string]::IsNullOrWhiteSpace($value)) { $sdkCandidates.Add($value.Trim()) }
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
        } elseif ($ToolName -eq "apksigner") {
            $candidate = Get-ChildItem -LiteralPath (Join-Path $root "build-tools") -Directory -ErrorAction SilentlyContinue |
                Sort-Object { [version]($_.Name -replace '-.*$', '') } -Descending |
                ForEach-Object { Join-Path $_.FullName "apksigner.bat" } |
                Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } |
                Select-Object -First 1
            if ($null -ne $candidate) { return $candidate }
        }
    }
    throw "Unable to resolve Android SDK tool '$ToolName'"
}

function Resolve-YoloR2ApkFromMetadata {
    param(
        [Parameter(Mandatory = $true)][string] $MetadataPath,
        [Parameter(Mandatory = $true)][string] $Label,
        [string] $Abi
    )
    $metadataFile = Get-YoloR2RequiredFile -Path $MetadataPath -Label "$Label output metadata"
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
    Assert-YoloR2Condition ($selected.Count -eq 1) "$Label must have exactly $expectation"
    $outputFile = [string]$selected[0].outputFile
    Assert-YoloR2Condition (-not [string]::IsNullOrWhiteSpace($outputFile)) "$Label output metadata has no outputFile"
    return Get-YoloR2RequiredFile -Path (Join-Path (Split-Path -Parent $metadataFile) $outputFile) -Label "$Label APK"
}

function Get-YoloR2ApkSignerDigest {
    param(
        [Parameter(Mandatory = $true)][string] $ApkSigner,
        [Parameter(Mandatory = $true)][string] $ApkPath,
        [Parameter(Mandatory = $true)][string] $Label
    )
    $output = Invoke-YoloR2External -FilePath $ApkSigner -Arguments @("verify", "--verbose", "--print-certs", $ApkPath) -Quiet
    $text = $output -join "`n"
    $signerCountMatches = [regex]::Matches($text, '(?im)^Number of signers:\s*([0-9]+)\s*$')
    Assert-YoloR2Condition (
        $signerCountMatches.Count -eq 1 -and [int]$signerCountMatches[0].Groups[1].Value -eq 1
    ) "$Label must have exactly one APK signer"
    # build-tools 36 prints "Signer #1", while build-tools 37 prints "V2 Signer".
    $certificateMatches = [regex]::Matches(
        $text,
        '(?im)^(?:Signer #[0-9]+|V[0-9.]+ Signer(?: #[0-9]+)?): certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$'
    )
    $certificateDigests = @($certificateMatches | ForEach-Object {
        $_.Groups[1].Value.ToLowerInvariant()
    } | Sort-Object -Unique)
    Assert-YoloR2Condition ($certificateDigests.Count -eq 1) "$Label must expose exactly one signer certificate digest"
    return $certificateDigests[0]
}

function Assert-YoloR2ApkClean {
    param(
        [Parameter(Mandatory = $true)][string] $ApkPath,
        [Parameter(Mandatory = $true)][string] $Label,
        [switch] $RequireNative
    )
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($ApkPath)
    try {
        $entries = @($archive.Entries | ForEach-Object { $_.FullName.Replace('\', '/') })
        $forbidden = @($entries | Where-Object {
            $forbiddenFixtureLeafNames -contains [System.IO.Path]::GetFileName($_).ToLowerInvariant()
        })
        Assert-YoloR2Condition ($forbidden.Count -eq 0) "$Label embeds local YOLO fixture/model bytes: $($forbidden -join ', ')"
        if ($RequireNative) {
            $native = @($entries | Where-Object { $_ -eq 'lib/arm64-v8a/libautojs_yolo.so' })
            Assert-YoloR2Condition ($native.Count -eq 1) "Provider APK must contain exactly one arm64 libautojs_yolo.so"
        }
    } finally {
        $archive.Dispose()
    }
}

function Test-YoloR2PackageInstalled {
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

function Assert-YoloR2Qv710Available {
    param(
        [Parameter(Mandatory = $true)][string] $Serial,
        [Parameter(Mandatory = $true)][bool] $ConfirmedIdle
    )
    if (-not $Serial.Equals($protectedQv710, [System.StringComparison]::OrdinalIgnoreCase)) {
        Assert-YoloR2Condition (-not $ConfirmedIdle) "-Qv710SoakConfirmedIdle is valid only for $protectedQv710"
        return
    }
    Assert-YoloR2Condition $ConfirmedIdle "Using $protectedQv710 requires explicit -Qv710SoakConfirmedIdle"
    $serialPattern = [regex]::Escape($protectedQv710)
    $soakPattern = '(?i)(soak|endurance|long[-_ ]?run|stress[-_ ]?run|verify[-_a-z0-9]*r7|r7[-_a-z0-9]*verify)'
    $matches = @(Get-CimInstance Win32_Process -ErrorAction Stop | Where-Object {
        if ($_.ProcessId -eq $PID -or [string]::IsNullOrWhiteSpace($_.CommandLine)) {
            return $false
        }
        # The explicit approval switch contains the word "Soak" and is propagated by
        # some command wrappers. Remove only that token before looking for a real runner.
        $commandForInspection = $_.CommandLine -replace '(?i)-Qv710SoakConfirmedIdle\b', ''
        return $commandForInspection -match $serialPattern -and $commandForInspection -match $soakPattern
    })
    Assert-YoloR2Condition ($matches.Count -eq 0) (
        "Refusing to use $protectedQv710 because matching soak runner process IDs are active: " +
        (($matches | ForEach-Object { $_.ProcessId }) -join ', ')
    )
}

$DeviceSerial = $DeviceSerial.Trim()
Assert-YoloR2Condition ($DeviceSerial -match '^[A-Za-z0-9._:-]+$') "DeviceSerial contains unsupported characters"
Assert-YoloR2Qv710Available -Serial $DeviceSerial -ConfirmedIdle $Qv710SoakConfirmedIdle.IsPresent

$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$siblingRoot = Get-YoloR2RequiredDirectory -Path $SiblingPath -Label "YOLO NCNN sibling"
$mainStatus = Get-YoloR2GitText -Repository $repositoryRoot -Arguments @("status", "--porcelain=v1", "--untracked-files=all")
$providerStatus = Get-YoloR2GitText -Repository $siblingRoot -Arguments @("status", "--porcelain=v1", "--untracked-files=all")
Assert-YoloR2Condition ([string]::IsNullOrEmpty($mainStatus)) "Host repository must be clean before generating R2 evidence"
Assert-YoloR2Condition ([string]::IsNullOrEmpty($providerStatus)) "Provider repository must be clean before generating R2 evidence"
$mainRevision = Get-YoloR2GitText -Repository $repositoryRoot -Arguments @("rev-parse", "HEAD")
$providerRevision = Get-YoloR2GitText -Repository $siblingRoot -Arguments @("rev-parse", "HEAD")
$mainGradle = Get-YoloR2RequiredFile -Path (Join-Path $repositoryRoot "gradlew.bat") -Label "Host Gradle wrapper"
$providerGradle = Get-YoloR2RequiredFile -Path (Join-Path $siblingRoot "gradlew.bat") -Label "Provider Gradle wrapper"
$adb = Resolve-YoloR2AndroidTool -RepositoryRoot $repositoryRoot -ToolName "adb"
$apkSigner = Resolve-YoloR2AndroidTool -RepositoryRoot $repositoryRoot -ToolName "apksigner"

$trackedFixtureRoot = Get-YoloR2RequiredDirectory -Path (Join-Path $siblingRoot "fixtures\yolo11n") -Label "tracked YOLO11n fixture metadata"
$localFixtureRoot = Get-YoloR2RequiredDirectory -Path (Join-Path $siblingRoot "fixtures\local\yolo11n") -Label "local-only YOLO11n fixture"
$fixtureLock = Get-Content -Raw -LiteralPath (Get-YoloR2RequiredFile -Path (Join-Path $trackedFixtureRoot "fixture.lock.json") -Label "fixture lock") | ConvertFrom-Json
$fixtureFiles = [ordered]@{
    "model-manifest-v1.json" = Get-YoloR2RequiredFile -Path (Join-Path $trackedFixtureRoot "model-manifest-v1.json") -Label "model manifest"
    "expected-bus-detections.json" = Get-YoloR2RequiredFile -Path (Join-Path $trackedFixtureRoot "expected-bus-detections.json") -Label "expected detections"
    "model.ncnn.param" = Get-YoloR2RequiredFile -Path (Join-Path $localFixtureRoot "model.ncnn.param") -Label "NCNN param"
    "model.ncnn.bin" = Get-YoloR2RequiredFile -Path (Join-Path $localFixtureRoot "model.ncnn.bin") -Label "NCNN weights"
    "bus.jpg" = Get-YoloR2RequiredFile -Path (Join-Path $localFixtureRoot "bus.jpg") -Label "bus image"
}
$fixtureEvidence = [ordered]@{}
foreach ($entry in $fixtureFiles.GetEnumerator()) {
    $locked = @($fixtureLock.artifacts | Where-Object { $_.name -eq $entry.Key })
    Assert-YoloR2Condition ($locked.Count -eq 1) "Fixture lock must contain exactly one $($entry.Key) entry"
    $actualLength = (Get-Item -LiteralPath $entry.Value).Length
    $actualSha256 = Get-YoloR2Sha256 -Path $entry.Value
    Assert-YoloR2Condition ($actualLength -eq [long]$locked[0].length) "$($entry.Key) length differs from fixture lock"
    Assert-YoloR2Condition ($actualSha256 -eq ([string]$locked[0].sha256).ToLowerInvariant()) "$($entry.Key) digest differs from fixture lock"
    $fixtureEvidence[$entry.Key] = [ordered]@{ length = $actualLength; sha256 = $actualSha256 }
}

# Build serially with one native PowerShell argument per Gradle token: provider, production Host,
# then the single Host instrumentation APK. The properties suppress version/build-time drift.
$gradleDeterministicArguments = @(
    "--console=plain",
    "--no-daemon",
    "--no-parallel",
    "--max-workers=2",
    "-Pkotlin.compiler.execution.strategy=in-process",
    "-Pautojs.gradle.build.number.auto.increment.enabled=false",
    "-Pautojs.gradle.build.time.update.enabled=false"
)
Invoke-YoloR2External -FilePath $providerGradle -Arguments @($gradleDeterministicArguments + ":app:assembleDebug") -WorkingDirectory $siblingRoot
Invoke-YoloR2External -FilePath $mainGradle -Arguments @($gradleDeterministicArguments + ":app:assembleAppDebug") -WorkingDirectory $repositoryRoot
Invoke-YoloR2External -FilePath $mainGradle -Arguments @($gradleDeterministicArguments + ":app:assembleAppDebugAndroidTest") -WorkingDirectory $repositoryRoot

$providerApk = Resolve-YoloR2ApkFromMetadata -MetadataPath (Join-Path $siblingRoot "app\build\outputs\apk\debug\output-metadata.json") -Label "Provider"
$hostApk = Resolve-YoloR2ApkFromMetadata -MetadataPath (Join-Path $repositoryRoot "app\build\outputs\apk\app\debug\output-metadata.json") -Label "Host" -Abi "arm64-v8a"
$testApk = Resolve-YoloR2ApkFromMetadata -MetadataPath (Join-Path $repositoryRoot "app\build\outputs\apk\androidTest\app\debug\output-metadata.json") -Label "Instrumentation"
Assert-YoloR2ApkClean -ApkPath $providerApk -Label "Provider" -RequireNative
Assert-YoloR2ApkClean -ApkPath $hostApk -Label "Host"
Assert-YoloR2ApkClean -ApkPath $testApk -Label "Instrumentation"

$providerSigner = Get-YoloR2ApkSignerDigest -ApkSigner $apkSigner -ApkPath $providerApk -Label "Provider APK"
$hostSigner = Get-YoloR2ApkSignerDigest -ApkSigner $apkSigner -ApkPath $hostApk -Label "Host APK"
$testSigner = Get-YoloR2ApkSignerDigest -ApkSigner $apkSigner -ApkPath $testApk -Label "Instrumentation APK"
Assert-YoloR2Condition ($providerSigner -eq $hostSigner -and $hostSigner -eq $testSigner) "Host, Provider, and instrumentation APKs must have the same signer"

$deviceRows = Invoke-YoloR2External -FilePath $adb -Arguments @("devices") -Quiet
$onlineRows = @($deviceRows | Where-Object { $_ -match "^$([regex]::Escape($DeviceSerial))\s+device(?:\s|$)" })
Assert-YoloR2Condition ($onlineRows.Count -eq 1) "Device $DeviceSerial is not present exactly once in adb device state"
$deviceState = ((Invoke-YoloR2Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("get-state") -Quiet) -join "`n").Trim()
Assert-YoloR2Condition ($deviceState -eq "device") "Device $DeviceSerial is not online"
$deviceAbi = ((Invoke-YoloR2Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "getprop", "ro.product.cpu.abi") -Quiet) -join "`n").Trim()
Assert-YoloR2Condition ($deviceAbi -eq "arm64-v8a") "R2 provider requires arm64-v8a; device reports $deviceAbi"
$deviceApi = [int](((Invoke-YoloR2Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "getprop", "ro.build.version.sdk") -Quiet) -join "`n").Trim())
Assert-YoloR2Condition ($deviceApi -ge 24) "R2 requires Android API 24 or newer; device reports API $deviceApi"
$deviceFingerprint = ((Invoke-YoloR2Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "getprop", "ro.build.fingerprint") -Quiet) -join "`n").Trim()

foreach ($packageName in @($hostPackage, $providerPackage, $testPackage)) {
    Assert-YoloR2Condition (-not (Test-YoloR2PackageInstalled -AdbPath $adb -Serial $DeviceSerial -PackageName $packageName)) `
        "R2 requires packages to be absent before the run; already installed: $packageName"
}

$stagingToken = [Guid]::NewGuid().ToString("N")
$deviceStaging = "/data/local/tmp/autojs6-yolo-r2-$stagingToken"
Assert-YoloR2Condition ($deviceStaging -match '^/data/local/tmp/autojs6-yolo-r2-[0-9a-f]{32}$') "Unsafe device staging path"
$hostInstalled = $false
$providerInstalled = $false
$testInstalled = $false
$instrumentationOutput = @()
$deviceResult = $null
$cleanupComplete = $false

try {
    Invoke-YoloR2Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "mkdir", "-p", $deviceStaging) | Out-Null
    foreach ($entry in $fixtureFiles.GetEnumerator()) {
        Invoke-YoloR2Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("push", $entry.Value, "$deviceStaging/$($entry.Key)") | Out-Null
        Invoke-YoloR2Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "chmod", "0644", "$deviceStaging/$($entry.Key)") | Out-Null
    }

    Invoke-YoloR2Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("install", "-r", $hostApk) | Out-Null
    $hostInstalled = $true
    Invoke-YoloR2Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("install", "-r", $providerApk) | Out-Null
    $providerInstalled = $true
    Invoke-YoloR2Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("install", "-r", "-t", $testApk) | Out-Null
    $testInstalled = $true

    Invoke-YoloR2Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "run-as", $hostPackage, "rm", "-rf", $fixturePrivateDirectory) | Out-Null
    Invoke-YoloR2Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "run-as", $hostPackage, "mkdir", "-p", $fixturePrivateDirectory) | Out-Null
    foreach ($entry in $fixtureFiles.GetEnumerator()) {
        $privatePath = "$fixturePrivateDirectory/$($entry.Key)"
        Invoke-YoloR2Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "run-as", $hostPackage, "cp", "$deviceStaging/$($entry.Key)", $privatePath) | Out-Null
        Invoke-YoloR2Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "run-as", $hostPackage, "chmod", "0400", $privatePath) | Out-Null
    }
    Invoke-YoloR2Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "run-as", $hostPackage, "rm", "-f", $resultPrivateFile) | Out-Null

    $instrumentationOutput = @(Invoke-YoloR2Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @(
        "shell", "am", "instrument", "-w", "-r",
        "-e", "yoloR2Enabled", "true",
        "-e", "class", "$testClass#$testMethod",
        "$testPackage/$runner"
    ))
    $instrumentationText = $instrumentationOutput -join "`n"
    Assert-YoloR2Condition ($instrumentationText -match '(?m)^OK \(1 test\)\s*$') "The single R2 instrumentation test did not pass"
    Assert-YoloR2Condition ($instrumentationText -notmatch '(?m)^(FAILURES!!!|INSTRUMENTATION_FAILED:)') "R2 instrumentation reported a failure"

    $resultText = (Invoke-YoloR2Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("exec-out", "run-as", $hostPackage, "cat", $resultPrivateFile) -Quiet) -join "`n"
    try { $deviceResult = $resultText | ConvertFrom-Json } catch { throw "R2 device result is not valid JSON`n$($_.Exception.Message)" }
    Assert-YoloR2Condition ([int]$deviceResult.schemaVersion -eq 1 -and $deviceResult.status -eq "PASS") "R2 device result did not report PASS"
    Assert-YoloR2Condition ($deviceResult.component -eq $providerComponent) "R2 used an unexpected provider component"
    Assert-YoloR2Condition ([bool]$deviceResult.remoteBinder) "R2 did not traverse a remote session Binder"
    Assert-YoloR2Condition ([bool]$deviceResult.modelDescriptorsClosedAfterOpen) "Host model descriptors were not closed after open"
    Assert-YoloR2Condition ([bool]$deviceResult.imageDescriptorClosedAfterDispatch) "Host image descriptor was not closed after dispatch"
    Assert-YoloR2Condition ([int]$deviceResult.terminalCount -eq 1) "R2 did not observe exactly one terminal callback"
    Assert-YoloR2Condition ([int]$deviceResult.callbackUid -eq [int]$deviceResult.providerUid) "Callback UID is not the provider UID"
    Assert-YoloR2Condition ([int]$deviceResult.hostUid -ne [int]$deviceResult.providerUid) "Host and provider UIDs are not isolated"
    Assert-YoloR2Condition ([bool]$deviceResult.idempotentClose -and [bool]$deviceResult.reopenSucceeded) "Close/reopen lifecycle evidence is incomplete"
    $detections = @($deviceResult.detections)
    Assert-YoloR2Condition ($detections.Count -eq 5) "R2 must return exactly five locked detections"
    Assert-YoloR2Condition (@($detections | Where-Object { $_.classId -eq 5 -and $_.label -eq 'bus' }).Count -eq 1) "R2 must return exactly one bus"
    Assert-YoloR2Condition (@($detections | Where-Object { $_.classId -eq 0 -and $_.label -eq 'person' }).Count -eq 4) "R2 must return exactly four people"
} finally {
    $cleanupErrors = [System.Collections.Generic.List[string]]::new()
    $hostPresentForCleanup = $false
    try {
        $hostPresentForCleanup = Test-YoloR2PackageInstalled -AdbPath $adb -Serial $DeviceSerial -PackageName $hostPackage
    } catch { $cleanupErrors.Add($_.Exception.Message) }
    if ($hostPresentForCleanup) {
        try { Invoke-YoloR2Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "run-as", $hostPackage, "rm", "-rf", $fixturePrivateDirectory) -Quiet | Out-Null } catch { $cleanupErrors.Add($_.Exception.Message) }
        try { Invoke-YoloR2Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "run-as", $hostPackage, "rm", "-f", $resultPrivateFile) -Quiet | Out-Null } catch { $cleanupErrors.Add($_.Exception.Message) }
    }
    try { Invoke-YoloR2Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "rm", "-rf", $deviceStaging) -Quiet | Out-Null } catch { $cleanupErrors.Add($_.Exception.Message) }
    # All three packages were proven absent before the run. Query and remove them
    # regardless of install-command bookkeeping so a partially successful install
    # cannot survive an adb error return.
    foreach ($packageName in @($testPackage, $providerPackage, $hostPackage)) {
        try {
            if (Test-YoloR2PackageInstalled -AdbPath $adb -Serial $DeviceSerial -PackageName $packageName) {
                Invoke-YoloR2Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("uninstall", $packageName) -Quiet | Out-Null
            }
        } catch { $cleanupErrors.Add($_.Exception.Message) }
    }
    foreach ($packageName in @($testPackage, $providerPackage, $hostPackage)) {
        try {
            if (Test-YoloR2PackageInstalled -AdbPath $adb -Serial $DeviceSerial -PackageName $packageName) {
                $cleanupErrors.Add("Package remained installed after cleanup: $packageName")
            }
        } catch { $cleanupErrors.Add($_.Exception.Message) }
    }
    if ($cleanupErrors.Count -gt 0) { throw "R2 cleanup failed:`n$($cleanupErrors -join "`n")" }
    $cleanupComplete = $true
}

Assert-YoloR2Condition ($null -ne $deviceResult) "R2 produced no device result"
Assert-YoloR2Condition $cleanupComplete "R2 cleanup did not complete"
$reportDirectory = Join-Path $repositoryRoot "build\reports\yolo"
[void](New-Item -ItemType Directory -Force -Path $reportDirectory)
$reportPath = Join-Path $reportDirectory "r2-summary.generated.json"
$report = [ordered]@{
    schemaVersion = 1
    roadmapStage = "R2"
    status = "PASS"
    evidenceLevel = "real-device-production-host-remote-binder-pfd"
    generatedAtUtc = [DateTime]::UtcNow.ToString("o")
    mainRevision = $mainRevision
    providerRevision = $providerRevision
    sourceRepositoriesClean = $true
    invocation = [ordered]@{
        script = "tools/yolo/verify-yolo-r2.ps1"
        explicitDeviceSerial = $DeviceSerial
        gradleTasks = @(
            ":app:assembleDebug",
            ":app:assembleAppDebug",
            ":app:assembleAppDebugAndroidTest"
        )
    }
    device = [ordered]@{
        serial = $DeviceSerial
        api = $deviceApi
        abi = $deviceAbi
        fingerprint = $deviceFingerprint
        qv710SoakConfirmedIdle = $DeviceSerial.Equals($protectedQv710, [System.StringComparison]::OrdinalIgnoreCase) -and $Qv710SoakConfirmedIdle.IsPresent
    }
    apks = [ordered]@{
        host = [ordered]@{ path = $hostApk; sha256 = Get-YoloR2Sha256 $hostApk; signerSha256 = $hostSigner; modelFree = $true }
        provider = [ordered]@{ path = $providerApk; sha256 = Get-YoloR2Sha256 $providerApk; signerSha256 = $providerSigner; modelFree = $true; nativeArm64 = $true }
        instrumentation = [ordered]@{ path = $testApk; sha256 = Get-YoloR2Sha256 $testApk; signerSha256 = $testSigner; modelFree = $true }
    }
    fixture = $fixtureEvidence
    instrumentation = [ordered]@{
        runner = $runner
        class = $testClass
        method = $testMethod
        singleMethodOnly = $true
        passedTests = 1
    }
    test = $deviceResult
    cleanup = [ordered]@{
        reverseUninstall = $true
        packagesAbsent = $true
        temporaryStagingRemoved = $true
        temporaryAvdStarted = $false
    }
}
$report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $reportPath -Encoding utf8
Write-Host "YOLO_R2=PASS"
Write-Host "YOLO_R2_REPORT=$reportPath"
