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
$providerComponent = "$providerPackage/.provider.YoloProviderService"
$runner = "androidx.test.runner.AndroidJUnitRunner"
$testClass = "org.autojs.autojs.runtime.api.modules.yolo.YoloRhinoR4AndroidTest"
$testMethod = "publicYoloApiImportsOfficialAndCustomModelsThroughExactRemoteProvider"
$protectedQv710 = "QV710AF65F"
$fixturePrivateDirectory = "files/yolo-r4-fixture"
$officialPrivateDirectory = "$fixturePrivateDirectory/official"
$officialModelPrivateDirectory = "$officialPrivateDirectory/model"
$customPrivateDirectory = "$fixturePrivateDirectory/custom"
$customModelPrivateDirectory = "$customPrivateDirectory/model"
$badShapePrivateDirectory = "$fixturePrivateDirectory/bad-shape"
$badShapeModelPrivateDirectory = "$badShapePrivateDirectory/model"
$resultPrivateFile = "files/yolo-r4-result.json"
$forbiddenFixtureLeafNames = @(
    "model.json",
    "model-manifest-v1.json",
    "model.ncnn.param",
    "model.ncnn.bin",
    "bus.jpg",
    "expected-bus-detections.json",
    "synthetic-heldout.png",
    "expected-synthetic-detection.json",
    "fixture.lock.json",
    "metadata.yaml",
    "yolo11n.pt"
)

function Assert-YoloR4Condition {
    param(
        [Parameter(Mandatory = $true)][bool] $Condition,
        [Parameter(Mandatory = $true)][string] $Message
    )
    if (-not $Condition) { throw $Message }
}

function Get-YoloR4RequiredFile {
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

function Get-YoloR4RequiredDirectory {
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

function Invoke-YoloR4External {
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

function Invoke-YoloR4Adb {
    param(
        [Parameter(Mandatory = $true)][string] $AdbPath,
        [Parameter(Mandatory = $true)][string] $Serial,
        [string[]] $Arguments = @(),
        [switch] $Quiet
    )
    return Invoke-YoloR4External -FilePath $AdbPath -Arguments (@("-s", $Serial) + $Arguments) -Quiet:$Quiet
}

function Get-YoloR4Sha256 {
    param([Parameter(Mandatory = $true)][string] $Path)
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Get-YoloR4GitText {
    param(
        [Parameter(Mandatory = $true)][string] $Repository,
        [Parameter(Mandatory = $true)][string[]] $Arguments
    )
    return ((Invoke-YoloR4External -FilePath "git" -Arguments (@("-C", $Repository) + $Arguments) -Quiet) -join "`n").Trim()
}

function Resolve-YoloR4AndroidTool {
    param(
        [Parameter(Mandatory = $true)][string] $RepositoryRoot,
        [Parameter(Mandatory = $true)][string] $ToolName
    )
    $sdkCandidates = [System.Collections.Generic.List[string]]::new()
    $localProperties = Join-Path $RepositoryRoot "local.properties"
    if (Test-Path -LiteralPath $localProperties -PathType Leaf) {
        $sdkLine = Get-Content -LiteralPath $localProperties |
            Where-Object { $_ -match '^sdk\.dir=' } |
            Select-Object -First 1
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
        }
        if ($ToolName -eq "apksigner") {
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

function Resolve-YoloR4ApkFromMetadata {
    param(
        [Parameter(Mandatory = $true)][string] $MetadataPath,
        [Parameter(Mandatory = $true)][string] $Label,
        [string] $Abi
    )
    $metadataFile = Get-YoloR4RequiredFile -Path $MetadataPath -Label "$Label output metadata"
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
    Assert-YoloR4Condition ($selected.Count -eq 1) "$Label must have exactly $expectation"
    $outputFile = [string]$selected[0].outputFile
    Assert-YoloR4Condition (-not [string]::IsNullOrWhiteSpace($outputFile)) "$Label output metadata has no outputFile"
    return Get-YoloR4RequiredFile -Path (Join-Path (Split-Path -Parent $metadataFile) $outputFile) -Label "$Label APK"
}

function Get-YoloR4ApkSignerDigest {
    param(
        [Parameter(Mandatory = $true)][string] $ApkSigner,
        [Parameter(Mandatory = $true)][string] $ApkPath,
        [Parameter(Mandatory = $true)][string] $Label
    )
    $output = Invoke-YoloR4External -FilePath $ApkSigner -Arguments @("verify", "--verbose", "--print-certs", $ApkPath) -Quiet
    $text = $output -join "`n"
    $signerCountMatches = [regex]::Matches($text, '(?im)^Number of signers:\s*([0-9]+)\s*$')
    Assert-YoloR4Condition (
        $signerCountMatches.Count -eq 1 -and [int]$signerCountMatches[0].Groups[1].Value -eq 1
    ) "$Label must have exactly one APK signer"
    $certificateMatches = [regex]::Matches(
        $text,
        '(?im)^(?:Signer #[0-9]+|V[0-9.]+ Signer(?: #[0-9]+)?): certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$'
    )
    $digests = @($certificateMatches | ForEach-Object {
        $_.Groups[1].Value.ToLowerInvariant()
    } | Sort-Object -Unique)
    Assert-YoloR4Condition ($digests.Count -eq 1) "$Label must expose exactly one signer certificate digest"
    return $digests[0]
}

function Assert-YoloR4ApkClean {
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
        Assert-YoloR4Condition ($forbidden.Count -eq 0) "$Label embeds local YOLO fixture/model bytes: $($forbidden -join ', ')"
        $native = @($entries | Where-Object { $_ -eq 'lib/arm64-v8a/libautojs_yolo.so' })
        if ($RequireNative) {
            Assert-YoloR4Condition ($native.Count -eq 1) "Provider APK must contain exactly one arm64 libautojs_yolo.so"
        } else {
            Assert-YoloR4Condition ($native.Count -eq 0) "$Label must not contain plugin native code"
        }
    } finally {
        $archive.Dispose()
    }
}

function Test-YoloR4PackageInstalled {
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

function Assert-YoloR4Qv710Available {
    param(
        [Parameter(Mandatory = $true)][string] $Serial,
        [Parameter(Mandatory = $true)][bool] $ConfirmedIdle
    )
    if (-not $Serial.Equals($protectedQv710, [System.StringComparison]::OrdinalIgnoreCase)) {
        Assert-YoloR4Condition (-not $ConfirmedIdle) "-Qv710SoakConfirmedIdle is valid only for $protectedQv710"
        return
    }
    Assert-YoloR4Condition $ConfirmedIdle "Using $protectedQv710 requires explicit -Qv710SoakConfirmedIdle"
    $serialPattern = [regex]::Escape($protectedQv710)
    $soakPattern = '(?i)(soak|endurance|long[-_ ]?run|stress[-_ ]?run|verify[-_a-z0-9]*r7|r7[-_a-z0-9]*verify)'
    $matches = @(Get-CimInstance Win32_Process -ErrorAction Stop | Where-Object {
        if ($_.ProcessId -eq $PID -or [string]::IsNullOrWhiteSpace($_.CommandLine)) { return $false }
        $inspected = $_.CommandLine -replace '(?i)-Qv710SoakConfirmedIdle\b', ''
        return $inspected -match $serialPattern -and $inspected -match $soakPattern
    })
    Assert-YoloR4Condition ($matches.Count -eq 0) (
        "Refusing to use $protectedQv710 because matching soak runner process IDs are active: " +
        (($matches | ForEach-Object { $_.ProcessId }) -join ', ')
    )
}

Assert-YoloR4Qv710Available -Serial $DeviceSerial -ConfirmedIdle $Qv710SoakConfirmedIdle.IsPresent

$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$siblingRoot = Get-YoloR4RequiredDirectory -Path $SiblingPath -Label "YOLO NCNN sibling"
$mainStatus = Get-YoloR4GitText -Repository $repositoryRoot -Arguments @("status", "--porcelain=v1", "--untracked-files=all")
$providerStatus = Get-YoloR4GitText -Repository $siblingRoot -Arguments @("status", "--porcelain=v1", "--untracked-files=all")
Assert-YoloR4Condition ([string]::IsNullOrEmpty($mainStatus)) "Host repository must be clean before generating R4 evidence"
Assert-YoloR4Condition ([string]::IsNullOrEmpty($providerStatus)) "Provider repository must be clean before generating R4 evidence"
$mainRevision = Get-YoloR4GitText -Repository $repositoryRoot -Arguments @("rev-parse", "HEAD")
$providerRevision = Get-YoloR4GitText -Repository $siblingRoot -Arguments @("rev-parse", "HEAD")
$mainGradle = Get-YoloR4RequiredFile -Path (Join-Path $repositoryRoot "gradlew.bat") -Label "Host Gradle wrapper"
$providerGradle = Get-YoloR4RequiredFile -Path (Join-Path $siblingRoot "gradlew.bat") -Label "Provider Gradle wrapper"
$adb = Resolve-YoloR4AndroidTool -RepositoryRoot $repositoryRoot -ToolName "adb"
$apkSigner = Resolve-YoloR4AndroidTool -RepositoryRoot $repositoryRoot -ToolName "apksigner"

$officialTrackedRoot = Get-YoloR4RequiredDirectory -Path (Join-Path $siblingRoot "fixtures\yolo11n") -Label "tracked official YOLO11n fixture metadata"
$officialLocalRoot = Get-YoloR4RequiredDirectory -Path (Join-Path $siblingRoot "fixtures\local\yolo11n") -Label "local-only official YOLO11n fixture"
$officialLockFile = Get-YoloR4RequiredFile -Path (Join-Path $officialTrackedRoot "fixture.lock.json") -Label "official fixture lock"
$officialLock = Get-Content -Raw -LiteralPath $officialLockFile | ConvertFrom-Json
$officialFiles = [ordered]@{
    "model-manifest-v1.json" = Get-YoloR4RequiredFile -Path (Join-Path $officialTrackedRoot "model-manifest-v1.json") -Label "official model manifest"
    "expected-bus-detections.json" = Get-YoloR4RequiredFile -Path (Join-Path $officialTrackedRoot "expected-bus-detections.json") -Label "official expected detections"
    "model.ncnn.param" = Get-YoloR4RequiredFile -Path (Join-Path $officialLocalRoot "model.ncnn.param") -Label "official NCNN param"
    "model.ncnn.bin" = Get-YoloR4RequiredFile -Path (Join-Path $officialLocalRoot "model.ncnn.bin") -Label "official NCNN weights"
    "bus.jpg" = Get-YoloR4RequiredFile -Path (Join-Path $officialLocalRoot "bus.jpg") -Label "official bus image"
}
$officialEvidence = [ordered]@{}
foreach ($entry in $officialFiles.GetEnumerator()) {
    $locked = @($officialLock.artifacts | Where-Object { $_.name -eq $entry.Key })
    Assert-YoloR4Condition ($locked.Count -eq 1) "Official fixture lock must contain exactly one $($entry.Key) entry"
    $actualLength = (Get-Item -LiteralPath $entry.Value).Length
    $actualSha256 = Get-YoloR4Sha256 -Path $entry.Value
    Assert-YoloR4Condition ($actualLength -eq [long]$locked[0].length) "$($entry.Key) length differs from official fixture lock"
    Assert-YoloR4Condition ($actualSha256 -eq ([string]$locked[0].sha256).ToLowerInvariant()) "$($entry.Key) digest differs from official fixture lock"
    $officialEvidence[$entry.Key] = [ordered]@{ length = $actualLength; sha256 = $actualSha256 }
}
$officialEvidence["fixture.lock.json"] = [ordered]@{
    length = (Get-Item -LiteralPath $officialLockFile).Length
    sha256 = Get-YoloR4Sha256 -Path $officialLockFile
}

$customTrackedRoot = Get-YoloR4RequiredDirectory -Path (Join-Path $siblingRoot "fixtures\yolo11n-synthetic") -Label "tracked custom YOLO11n fixture metadata"
$customLocalRoot = Get-YoloR4RequiredDirectory -Path (Join-Path $siblingRoot "fixtures\local\yolo11n-synthetic") -Label "local-only custom YOLO11n fixture"
$customLockFile = Get-YoloR4RequiredFile -Path (Join-Path $customTrackedRoot "fixture.lock.json") -Label "custom fixture lock"
$customLock = Get-Content -Raw -LiteralPath $customLockFile | ConvertFrom-Json
$customFiles = [ordered]@{
    "model-manifest-v1.json" = Get-YoloR4RequiredFile -Path (Join-Path $customTrackedRoot "model-manifest-v1.json") -Label "custom model manifest"
    "expected-synthetic-detection.json" = Get-YoloR4RequiredFile -Path (Join-Path $customTrackedRoot "expected-synthetic-detection.json") -Label "custom expected detection"
    "model.ncnn.param" = Get-YoloR4RequiredFile -Path (Join-Path $customLocalRoot "model.ncnn.param") -Label "custom NCNN param"
    "model.ncnn.bin" = Get-YoloR4RequiredFile -Path (Join-Path $customLocalRoot "model.ncnn.bin") -Label "custom NCNN weights"
    "synthetic-heldout.png" = Get-YoloR4RequiredFile -Path (Join-Path $customLocalRoot "heldout\synthetic-heldout.png") -Label "custom held-out image"
}
$customEvidence = [ordered]@{}
foreach ($entry in $customFiles.GetEnumerator()) {
    $locked = @($customLock.artifacts | Where-Object { $_.name -eq $entry.Key })
    Assert-YoloR4Condition ($locked.Count -eq 1) "Custom fixture lock must contain exactly one $($entry.Key) entry"
    $actualLength = (Get-Item -LiteralPath $entry.Value).Length
    $actualSha256 = Get-YoloR4Sha256 -Path $entry.Value
    Assert-YoloR4Condition ($actualLength -eq [long]$locked[0].length) "$($entry.Key) length differs from custom fixture lock"
    Assert-YoloR4Condition ($actualSha256 -eq ([string]$locked[0].sha256).ToLowerInvariant()) "$($entry.Key) digest differs from custom fixture lock"
    $customEvidence[$entry.Key] = [ordered]@{ length = $actualLength; sha256 = $actualSha256 }
}
$customEvidence["fixture.lock.json"] = [ordered]@{
    length = (Get-Item -LiteralPath $customLockFile).Length
    sha256 = Get-YoloR4Sha256 -Path $customLockFile
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
Invoke-YoloR4External -FilePath $providerGradle -Arguments @($gradleArguments + ":app:assembleDebug") -WorkingDirectory $siblingRoot
Invoke-YoloR4External -FilePath $mainGradle -Arguments @($gradleArguments + ":app:assembleAppDebug") -WorkingDirectory $repositoryRoot
Invoke-YoloR4External -FilePath $mainGradle -Arguments @($gradleArguments + ":app:assembleAppDebugAndroidTest") -WorkingDirectory $repositoryRoot

$providerApk = Resolve-YoloR4ApkFromMetadata -MetadataPath (Join-Path $siblingRoot "app\build\outputs\apk\debug\output-metadata.json") -Label "Provider"
$hostApk = Resolve-YoloR4ApkFromMetadata -MetadataPath (Join-Path $repositoryRoot "app\build\outputs\apk\app\debug\output-metadata.json") -Label "Host" -Abi "arm64-v8a"
$testApk = Resolve-YoloR4ApkFromMetadata -MetadataPath (Join-Path $repositoryRoot "app\build\outputs\apk\androidTest\app\debug\output-metadata.json") -Label "Instrumentation"
Assert-YoloR4ApkClean -ApkPath $providerApk -Label "Provider" -RequireNative
Assert-YoloR4ApkClean -ApkPath $hostApk -Label "Host"
Assert-YoloR4ApkClean -ApkPath $testApk -Label "Instrumentation"

$providerSigner = Get-YoloR4ApkSignerDigest -ApkSigner $apkSigner -ApkPath $providerApk -Label "Provider"
$hostSigner = Get-YoloR4ApkSignerDigest -ApkSigner $apkSigner -ApkPath $hostApk -Label "Host"
$testSigner = Get-YoloR4ApkSignerDigest -ApkSigner $apkSigner -ApkPath $testApk -Label "Instrumentation"
Assert-YoloR4Condition ($providerSigner -eq $hostSigner -and $hostSigner -eq $testSigner) "Host, provider, and instrumentation APK signers differ"

$deviceRows = @(Invoke-YoloR4External -FilePath $adb -Arguments @("devices", "-l") -Quiet)
$onlineRows = @($deviceRows | Where-Object { $_ -match "^$([regex]::Escape($DeviceSerial))\s+device(?:\s|$)" })
Assert-YoloR4Condition ($onlineRows.Count -eq 1) "Device $DeviceSerial is not uniquely online"
$deviceApi = [int]((Invoke-YoloR4Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "getprop", "ro.build.version.sdk") -Quiet) -join '').Trim()
$deviceAbi = ((Invoke-YoloR4Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "getprop", "ro.product.cpu.abi") -Quiet) -join '').Trim()
$deviceModel = ((Invoke-YoloR4Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "getprop", "ro.product.model") -Quiet) -join '').Trim()
$deviceFingerprint = ((Invoke-YoloR4Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "getprop", "ro.build.fingerprint") -Quiet) -join '').Trim()
Assert-YoloR4Condition ($deviceApi -ge 24) "R4 requires Android API 24 or newer"
Assert-YoloR4Condition ($deviceAbi -eq "arm64-v8a") "R4 provider is arm64-only; device ABI is $deviceAbi"
foreach ($packageName in @($hostPackage, $providerPackage, $testPackage)) {
    Assert-YoloR4Condition (-not (Test-YoloR4PackageInstalled -AdbPath $adb -Serial $DeviceSerial -PackageName $packageName)) (
        "R4 refuses to replace pre-existing package $packageName on $DeviceSerial"
    )
}

$deviceStaging = "/data/local/tmp/autojs6-yolo-r4-$([guid]::NewGuid().ToString('N'))"
Assert-YoloR4Condition (
    $deviceStaging -match '^/data/local/tmp/autojs6-yolo-r4-[0-9a-f]{32}$'
) "Unsafe R4 device staging path"
$deviceResult = $null
$instrumentationText = ""
$cleanupComplete = $false
try {
    Invoke-YoloR4Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "mkdir", "-p", $deviceStaging) | Out-Null
    $stagingFiles = [ordered]@{
        "official-model.json" = $officialFiles["model-manifest-v1.json"]
        "official-model.ncnn.param" = $officialFiles["model.ncnn.param"]
        "official-model.ncnn.bin" = $officialFiles["model.ncnn.bin"]
        "official-bus.jpg" = $officialFiles["bus.jpg"]
        "official-expected.json" = $officialFiles["expected-bus-detections.json"]
        "custom-model.json" = $customFiles["model-manifest-v1.json"]
        "custom-model.ncnn.param" = $customFiles["model.ncnn.param"]
        "custom-model.ncnn.bin" = $customFiles["model.ncnn.bin"]
        "custom-heldout.png" = $customFiles["synthetic-heldout.png"]
        "custom-expected.json" = $customFiles["expected-synthetic-detection.json"]
    }
    foreach ($entry in $stagingFiles.GetEnumerator()) {
        Invoke-YoloR4Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("push", $entry.Value, "$deviceStaging/$($entry.Key)") | Out-Null
        Invoke-YoloR4Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "chmod", "0644", "$deviceStaging/$($entry.Key)") | Out-Null
    }

    Invoke-YoloR4Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("install", "-r", $hostApk) | Out-Null
    Invoke-YoloR4Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("install", "-r", $providerApk) | Out-Null
    Invoke-YoloR4Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("install", "-r", "-t", $testApk) | Out-Null

    Invoke-YoloR4Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "run-as", $hostPackage, "rm", "-rf", $fixturePrivateDirectory) | Out-Null
    foreach ($directory in @($officialModelPrivateDirectory, $customModelPrivateDirectory, $badShapeModelPrivateDirectory)) {
        Invoke-YoloR4Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "run-as", $hostPackage, "mkdir", "-p", $directory) | Out-Null
    }
    $privateCopies = @(
        [ordered]@{ source = "official-model.json"; destination = "$officialModelPrivateDirectory/model.json"; mode = "0400" }
        [ordered]@{ source = "official-model.ncnn.param"; destination = "$officialModelPrivateDirectory/model.ncnn.param"; mode = "0400" }
        [ordered]@{ source = "official-model.ncnn.bin"; destination = "$officialModelPrivateDirectory/model.ncnn.bin"; mode = "0400" }
        [ordered]@{ source = "official-bus.jpg"; destination = "$officialPrivateDirectory/bus.jpg"; mode = "0400" }
        [ordered]@{ source = "official-expected.json"; destination = "$officialPrivateDirectory/expected-bus-detections.json"; mode = "0400" }
        [ordered]@{ source = "custom-model.json"; destination = "$customModelPrivateDirectory/model.json"; mode = "0400" }
        [ordered]@{ source = "custom-model.ncnn.param"; destination = "$customModelPrivateDirectory/model.ncnn.param"; mode = "0400" }
        [ordered]@{ source = "custom-model.ncnn.bin"; destination = "$customModelPrivateDirectory/model.ncnn.bin"; mode = "0400" }
        [ordered]@{ source = "custom-heldout.png"; destination = "$customPrivateDirectory/synthetic-heldout.png"; mode = "0400" }
        [ordered]@{ source = "custom-expected.json"; destination = "$customPrivateDirectory/expected-synthetic-detection.json"; mode = "0400" }
        [ordered]@{ source = "custom-model.json"; destination = "$badShapeModelPrivateDirectory/model.json"; mode = "0600" }
        [ordered]@{ source = "custom-model.ncnn.param"; destination = "$badShapeModelPrivateDirectory/model.ncnn.param"; mode = "0400" }
        [ordered]@{ source = "custom-model.ncnn.bin"; destination = "$badShapeModelPrivateDirectory/model.ncnn.bin"; mode = "0400" }
    )
    foreach ($copy in $privateCopies) {
        Invoke-YoloR4Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "run-as", $hostPackage, "cp", "$deviceStaging/$($copy.source)", $copy.destination) | Out-Null
        Invoke-YoloR4Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "run-as", $hostPackage, "chmod", $copy.mode, $copy.destination) | Out-Null
    }
    Invoke-YoloR4Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "run-as", $hostPackage, "rm", "-f", $resultPrivateFile) | Out-Null

    $instrumentationOutput = @(Invoke-YoloR4Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @(
        "shell", "am", "instrument", "-w", "-r",
        "-e", "yoloR4Enabled", "true",
        "-e", "class", "$testClass#$testMethod",
        "$testPackage/$runner"
    ))
    $instrumentationText = $instrumentationOutput -join "`n"
    Assert-YoloR4Condition ($instrumentationText -match '(?m)^OK \(1 test\)\s*$') "R4 instrumentation did not report one passing test"
    Assert-YoloR4Condition ($instrumentationText -notmatch '(?m)^(FAILURES!!!|INSTRUMENTATION_FAILED:)') "R4 instrumentation reported a failure"

    $resultText = (Invoke-YoloR4Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("exec-out", "run-as", $hostPackage, "cat", $resultPrivateFile) -Quiet) -join "`n"
    try { $deviceResult = $resultText | ConvertFrom-Json } catch { throw "R4 device result is not valid JSON`n$($_.Exception.Message)" }
    Assert-YoloR4Condition ([int]$deviceResult.schemaVersion -eq 1 -and $deviceResult.status -eq "PASS") "R4 device result did not report PASS"
    Assert-YoloR4Condition ($deviceResult.component -eq $providerComponent) "R4 used an unexpected provider component"
    Assert-YoloR4Condition ([bool]$deviceResult.productionRhino) "R4 did not run the production Rhino engine"
    Assert-YoloR4Condition ($deviceResult.engineClass -eq "org.autojs.autojs.engine.LoopBasedJavaScriptEngine") "R4 started an unexpected script engine"
    Assert-YoloR4Condition ([bool]$deviceResult.sameSigner -and [bool]$deviceResult.separateProviderUid) "R4 provider identity isolation failed"
    Assert-YoloR4Condition ([bool]$deviceResult.defaultOffObserved) "R4 did not observe default-off before explicit opt-in"
    Assert-YoloR4Condition ([bool]$deviceResult.publicApiPresent) "R4 public yolo API was absent"
    Assert-YoloR4Condition ([bool]$deviceResult.idempotentClose) "R4 repeated detector.close failed"
    Assert-YoloR4Condition ([string]$deviceResult.scriptSha256 -match '^[0-9a-f]{64}$') "R4 script digest is invalid"
    Assert-YoloR4Condition ($deviceResult.badShapeErrorCode -eq "YOLO_MODEL_REJECTED") "R4 bad-shape manifest did not map to YOLO_MODEL_REJECTED"
    $officialResult = $deviceResult.models.official
    $officialDetections = @($officialResult.detections)
    Assert-YoloR4Condition ([bool]$officialResult.rectCentersUsable) "R4 official RectF result was not directly usable"
    Assert-YoloR4Condition ($officialDetections.Count -eq 5) "R4 official model expected exactly five detections"
    Assert-YoloR4Condition (@($officialDetections | Where-Object { [int]$_.classId -eq 5 -and $_.label -eq "bus" }).Count -eq 1) "R4 official model expected one bus"
    Assert-YoloR4Condition (@($officialDetections | Where-Object { [int]$_.classId -eq 0 -and $_.label -eq "person" }).Count -eq 4) "R4 official model expected four people"
    Assert-YoloR4Condition (@($officialResult.comparisons).Count -eq 5) "R4 official comparison evidence is incomplete"
    $customResult = $deviceResult.models.custom
    $customDetections = @($customResult.detections)
    Assert-YoloR4Condition ([bool]$customResult.rectCentersUsable) "R4 custom RectF result was not directly usable"
    Assert-YoloR4Condition ($customDetections.Count -eq 1) "R4 custom model expected exactly one detection"
    Assert-YoloR4Condition ([int]$customDetections[0].classId -eq 0 -and $customDetections[0].label -eq "synthetic-target") "R4 custom model expected one synthetic-target"
    Assert-YoloR4Condition ([double]$customResult.comparison.groundTruthIou -ge 0.90) "R4 custom ground-truth IoU is below 0.90"
    Assert-YoloR4Condition ([double]$customResult.comparison.referenceIou -ge 0.999) "R4 custom local NCNN reference IoU is below 0.999"
    Assert-YoloR4Condition ([double]$customResult.comparison.confidenceDelta -le 0.001) "R4 custom confidence delta exceeds 0.001"
} finally {
    $cleanupErrors = [System.Collections.Generic.List[string]]::new()
    $hostPresentForCleanup = $false
    try {
        $hostPresentForCleanup = Test-YoloR4PackageInstalled -AdbPath $adb -Serial $DeviceSerial -PackageName $hostPackage
    } catch { $cleanupErrors.Add($_.Exception.Message) }
    if ($hostPresentForCleanup) {
        try { Invoke-YoloR4Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "run-as", $hostPackage, "rm", "-rf", $fixturePrivateDirectory) -Quiet | Out-Null } catch { $cleanupErrors.Add($_.Exception.Message) }
        try { Invoke-YoloR4Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "run-as", $hostPackage, "rm", "-f", $resultPrivateFile) -Quiet | Out-Null } catch { $cleanupErrors.Add($_.Exception.Message) }
    }
    try { Invoke-YoloR4Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "rm", "-rf", $deviceStaging) -Quiet | Out-Null } catch { $cleanupErrors.Add($_.Exception.Message) }
    foreach ($packageName in @($testPackage, $providerPackage, $hostPackage)) {
        try {
            if (Test-YoloR4PackageInstalled -AdbPath $adb -Serial $DeviceSerial -PackageName $packageName) {
                Invoke-YoloR4Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("uninstall", $packageName) -Quiet | Out-Null
            }
        } catch { $cleanupErrors.Add($_.Exception.Message) }
    }
    foreach ($packageName in @($hostPackage, $providerPackage, $testPackage)) {
        try {
            if (Test-YoloR4PackageInstalled -AdbPath $adb -Serial $DeviceSerial -PackageName $packageName) {
                $cleanupErrors.Add("Package remains installed after cleanup: $packageName")
            }
        } catch { $cleanupErrors.Add($_.Exception.Message) }
    }
    $cleanupComplete = $cleanupErrors.Count -eq 0
    if (-not $cleanupComplete) { throw "R4 cleanup failed: $($cleanupErrors -join '; ')" }
}

Assert-YoloR4Condition ($null -ne $deviceResult) "R4 produced no device result"
$reportDirectory = Join-Path $repositoryRoot "build\reports\yolo"
[void](New-Item -ItemType Directory -Force -Path $reportDirectory)
$reportPath = Join-Path $reportDirectory "r4-summary.generated.json"
$report = [ordered]@{
    schemaVersion = 1
    roadmapStage = "R4"
    status = "PASS"
    evidenceLevel = "real-device-production-rhino-two-model-public-import-exact-provider-ncnn"
    generatedAtUtc = [DateTime]::UtcNow.ToString("o")
    mainRevision = $mainRevision
    providerRevision = $providerRevision
    sourceRepositoriesClean = $true
    evidenceStatus = [ordered]@{
        source = "PASS"
        static = "NOT_RUN_BY_R4_GATE"
        jvm = "NOT_RUN_BY_R4_GATE"
        androidBuild = "PASS"
        binderPfd = "PASS_VIA_PRODUCTION_HOST_CHAIN"
        nativeRuntime = "PASS"
        deviceE2e = "PASS"
        publicModelImport = "PASS"
        production = "NOT_CLAIMED_R4_PUBLIC_MODEL_IMPORT_ONLY"
    }
    invocation = [ordered]@{
        script = "tools/yolo/verify-yolo-r4.ps1"
        explicitDeviceSerial = $DeviceSerial
        gradleTasks = @(
            ":app:assembleDebug",
            ":app:assembleAppDebug",
            ":app:assembleAppDebugAndroidTest"
        )
    }
    device = [ordered]@{
        serial = $DeviceSerial
        model = $deviceModel
        api = $deviceApi
        abi = $deviceAbi
        fingerprint = $deviceFingerprint
        qv710SoakConfirmedIdle = [bool]($DeviceSerial -eq $protectedQv710 -and $Qv710SoakConfirmedIdle.IsPresent)
        avdStartedByGate = $false
    }
    provider = [ordered]@{
        component = $providerComponent
        separateUid = [bool]$deviceResult.separateProviderUid
        sameSigner = [bool]$deviceResult.sameSigner
    }
    script = [ordered]@{
        engineClass = [string]$deviceResult.engineClass
        sha256 = [string]$deviceResult.scriptSha256
        publicApiPresent = [bool]$deviceResult.publicApiPresent
        idempotentClose = [bool]$deviceResult.idempotentClose
        officialRectCentersUsable = [bool]$deviceResult.models.official.rectCentersUsable
        customRectCentersUsable = [bool]$deviceResult.models.custom.rectCentersUsable
    }
    apks = [ordered]@{
        host = [ordered]@{ path = $hostApk; sha256 = Get-YoloR4Sha256 -Path $hostApk; signerSha256 = $hostSigner; modelFree = $true; pluginNativeAbsent = $true }
        provider = [ordered]@{ path = $providerApk; sha256 = Get-YoloR4Sha256 -Path $providerApk; signerSha256 = $providerSigner; modelFree = $true; arm64NativePresent = $true }
        instrumentation = [ordered]@{ path = $testApk; sha256 = Get-YoloR4Sha256 -Path $testApk; signerSha256 = $testSigner; modelFree = $true; pluginNativeAbsent = $true }
    }
    models = [ordered]@{
        official = [ordered]@{
            name = "yolo11n"
            family = "ultralytics-yolo11"
            decoderId = "ultralytics-detect"
            fixtureHashes = $officialEvidence
            result = $deviceResult.models.official
        }
        custom = [ordered]@{
            name = "yolo11n-synthetic"
            family = "ultralytics-yolo11"
            decoderId = "ultralytics-detect"
            localValidationOnly = $true
            fixtureHashes = $customEvidence
            result = $deviceResult.models.custom
        }
    }
    negative = [ordered]@{
        case = "output-shape-row-count-mismatch"
        manifestShape = @(1, 6, 8400)
        publicApi = "yolo.load"
        stableErrorCode = [string]$deviceResult.badShapeErrorCode
    }
    fixtureDeployment = [ordered]@{
        manifestSourceName = "model-manifest-v1.json"
        manifestStagedName = "model.json"
        officialAndCustomDirectoriesSeparate = $true
        modelAndImageExternalOnly = $true
    }
    instrumentation = [ordered]@{
        runner = $runner
        class = $testClass
        method = $testMethod
        singleMethodOnly = $true
        passedTests = 1
    }
    test = $deviceResult
    chain = [ordered]@{
        productionRhino = $true
        publicYoloApi = $true
        exactComponent = $true
        externalProviderUid = $true
        binderPfdHostBoundary = $true
        providerJniNcnn = $true
    }
    cleanup = [ordered]@{
        packagesAbsentBefore = $true
        packagesAbsentAfter = $cleanupComplete
        uniqueStagingRemoved = $cleanupComplete
    }
    claims = [ordered]@{
        R4 = "PASS"
        SOURCE = "PASS"
        ANDROID_BUILD = "PASS"
        DEVICE_E2E = "PASS"
        PUBLIC_MODEL_IMPORT = "PASS"
        PRODUCTION = "NOT_CLAIMED"
    }
}
$report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $reportPath -Encoding utf8
Write-Host "YOLO_R4=PASS"
Write-Host "YOLO_R4_REPORT=$reportPath"
