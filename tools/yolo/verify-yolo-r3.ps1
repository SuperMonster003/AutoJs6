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
$testClass = "org.autojs.autojs.runtime.api.modules.yolo.YoloRhinoR3AndroidTest"
$testMethod = "publicYoloApiRunsProductionRhinoThroughExactRemoteProvider"
$protectedQv710 = "QV710AF65F"
$fixturePrivateDirectory = "files/yolo-r3-fixture"
$modelPrivateDirectory = "$fixturePrivateDirectory/model"
$resultPrivateFile = "files/yolo-r3-result.json"
$forbiddenFixtureLeafNames = @(
    "model.json",
    "model-manifest-v1.json",
    "model.ncnn.param",
    "model.ncnn.bin",
    "bus.jpg",
    "expected-bus-detections.json",
    "fixture.lock.json",
    "metadata.yaml",
    "yolo11n.pt"
)

function Assert-YoloR3Condition {
    param(
        [Parameter(Mandatory = $true)][bool] $Condition,
        [Parameter(Mandatory = $true)][string] $Message
    )
    if (-not $Condition) { throw $Message }
}

function Get-YoloR3RequiredFile {
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

function Get-YoloR3RequiredDirectory {
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

function Invoke-YoloR3External {
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

function Invoke-YoloR3Adb {
    param(
        [Parameter(Mandatory = $true)][string] $AdbPath,
        [Parameter(Mandatory = $true)][string] $Serial,
        [string[]] $Arguments = @(),
        [switch] $Quiet
    )
    return Invoke-YoloR3External -FilePath $AdbPath -Arguments (@("-s", $Serial) + $Arguments) -Quiet:$Quiet
}

function Get-YoloR3Sha256 {
    param([Parameter(Mandatory = $true)][string] $Path)
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Get-YoloR3GitText {
    param(
        [Parameter(Mandatory = $true)][string] $Repository,
        [Parameter(Mandatory = $true)][string[]] $Arguments
    )
    return ((Invoke-YoloR3External -FilePath "git" -Arguments (@("-C", $Repository) + $Arguments) -Quiet) -join "`n").Trim()
}

function Resolve-YoloR3AndroidTool {
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

function Resolve-YoloR3ApkFromMetadata {
    param(
        [Parameter(Mandatory = $true)][string] $MetadataPath,
        [Parameter(Mandatory = $true)][string] $Label,
        [string] $Abi
    )
    $metadataFile = Get-YoloR3RequiredFile -Path $MetadataPath -Label "$Label output metadata"
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
    Assert-YoloR3Condition ($selected.Count -eq 1) "$Label must have exactly $expectation"
    $outputFile = [string]$selected[0].outputFile
    Assert-YoloR3Condition (-not [string]::IsNullOrWhiteSpace($outputFile)) "$Label output metadata has no outputFile"
    return Get-YoloR3RequiredFile -Path (Join-Path (Split-Path -Parent $metadataFile) $outputFile) -Label "$Label APK"
}

function Get-YoloR3ApkSignerDigest {
    param(
        [Parameter(Mandatory = $true)][string] $ApkSigner,
        [Parameter(Mandatory = $true)][string] $ApkPath,
        [Parameter(Mandatory = $true)][string] $Label
    )
    $output = Invoke-YoloR3External -FilePath $ApkSigner -Arguments @("verify", "--verbose", "--print-certs", $ApkPath) -Quiet
    $text = $output -join "`n"
    $signerCountMatches = [regex]::Matches($text, '(?im)^Number of signers:\s*([0-9]+)\s*$')
    Assert-YoloR3Condition (
        $signerCountMatches.Count -eq 1 -and [int]$signerCountMatches[0].Groups[1].Value -eq 1
    ) "$Label must have exactly one APK signer"
    $certificateMatches = [regex]::Matches(
        $text,
        '(?im)^(?:Signer #[0-9]+|V[0-9.]+ Signer(?: #[0-9]+)?): certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$'
    )
    $digests = @($certificateMatches | ForEach-Object {
        $_.Groups[1].Value.ToLowerInvariant()
    } | Sort-Object -Unique)
    Assert-YoloR3Condition ($digests.Count -eq 1) "$Label must expose exactly one signer certificate digest"
    return $digests[0]
}

function Assert-YoloR3ApkClean {
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
        Assert-YoloR3Condition ($forbidden.Count -eq 0) "$Label embeds local YOLO fixture/model bytes: $($forbidden -join ', ')"
        $native = @($entries | Where-Object { $_ -eq 'lib/arm64-v8a/libautojs_yolo.so' })
        if ($RequireNative) {
            Assert-YoloR3Condition ($native.Count -eq 1) "Provider APK must contain exactly one arm64 libautojs_yolo.so"
        } else {
            Assert-YoloR3Condition ($native.Count -eq 0) "$Label must not contain plugin native code"
        }
    } finally {
        $archive.Dispose()
    }
}

function Test-YoloR3PackageInstalled {
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

function Assert-YoloR3Qv710Available {
    param(
        [Parameter(Mandatory = $true)][string] $Serial,
        [Parameter(Mandatory = $true)][bool] $ConfirmedIdle
    )
    if (-not $Serial.Equals($protectedQv710, [System.StringComparison]::OrdinalIgnoreCase)) {
        Assert-YoloR3Condition (-not $ConfirmedIdle) "-Qv710SoakConfirmedIdle is valid only for $protectedQv710"
        return
    }
    Assert-YoloR3Condition $ConfirmedIdle "Using $protectedQv710 requires explicit -Qv710SoakConfirmedIdle"
    $serialPattern = [regex]::Escape($protectedQv710)
    $soakPattern = '(?i)(soak|endurance|long[-_ ]?run|stress[-_ ]?run|verify[-_a-z0-9]*r7|r7[-_a-z0-9]*verify)'
    $matches = @(Get-CimInstance Win32_Process -ErrorAction Stop | Where-Object {
        if ($_.ProcessId -eq $PID -or [string]::IsNullOrWhiteSpace($_.CommandLine)) { return $false }
        $inspected = $_.CommandLine -replace '(?i)-Qv710SoakConfirmedIdle\b', ''
        return $inspected -match $serialPattern -and $inspected -match $soakPattern
    })
    Assert-YoloR3Condition ($matches.Count -eq 0) (
        "Refusing to use $protectedQv710 because matching soak runner process IDs are active: " +
        (($matches | ForEach-Object { $_.ProcessId }) -join ', ')
    )
}

Assert-YoloR3Qv710Available -Serial $DeviceSerial -ConfirmedIdle $Qv710SoakConfirmedIdle.IsPresent

$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$siblingRoot = Get-YoloR3RequiredDirectory -Path $SiblingPath -Label "YOLO NCNN sibling"
$mainStatus = Get-YoloR3GitText -Repository $repositoryRoot -Arguments @("status", "--porcelain=v1", "--untracked-files=all")
$providerStatus = Get-YoloR3GitText -Repository $siblingRoot -Arguments @("status", "--porcelain=v1", "--untracked-files=all")
Assert-YoloR3Condition ([string]::IsNullOrEmpty($mainStatus)) "Host repository must be clean before generating R3 evidence"
Assert-YoloR3Condition ([string]::IsNullOrEmpty($providerStatus)) "Provider repository must be clean before generating R3 evidence"
$mainRevision = Get-YoloR3GitText -Repository $repositoryRoot -Arguments @("rev-parse", "HEAD")
$providerRevision = Get-YoloR3GitText -Repository $siblingRoot -Arguments @("rev-parse", "HEAD")
$mainGradle = Get-YoloR3RequiredFile -Path (Join-Path $repositoryRoot "gradlew.bat") -Label "Host Gradle wrapper"
$providerGradle = Get-YoloR3RequiredFile -Path (Join-Path $siblingRoot "gradlew.bat") -Label "Provider Gradle wrapper"
$adb = Resolve-YoloR3AndroidTool -RepositoryRoot $repositoryRoot -ToolName "adb"
$apkSigner = Resolve-YoloR3AndroidTool -RepositoryRoot $repositoryRoot -ToolName "apksigner"

$trackedFixtureRoot = Get-YoloR3RequiredDirectory -Path (Join-Path $siblingRoot "fixtures\yolo11n") -Label "tracked YOLO11n fixture metadata"
$localFixtureRoot = Get-YoloR3RequiredDirectory -Path (Join-Path $siblingRoot "fixtures\local\yolo11n") -Label "local-only YOLO11n fixture"
$fixtureLockFile = Get-YoloR3RequiredFile -Path (Join-Path $trackedFixtureRoot "fixture.lock.json") -Label "fixture lock"
$fixtureLock = Get-Content -Raw -LiteralPath $fixtureLockFile | ConvertFrom-Json
$fixtureFiles = [ordered]@{
    "model-manifest-v1.json" = Get-YoloR3RequiredFile -Path (Join-Path $trackedFixtureRoot "model-manifest-v1.json") -Label "model manifest"
    "expected-bus-detections.json" = Get-YoloR3RequiredFile -Path (Join-Path $trackedFixtureRoot "expected-bus-detections.json") -Label "expected detections"
    "model.ncnn.param" = Get-YoloR3RequiredFile -Path (Join-Path $localFixtureRoot "model.ncnn.param") -Label "NCNN param"
    "model.ncnn.bin" = Get-YoloR3RequiredFile -Path (Join-Path $localFixtureRoot "model.ncnn.bin") -Label "NCNN weights"
    "bus.jpg" = Get-YoloR3RequiredFile -Path (Join-Path $localFixtureRoot "bus.jpg") -Label "bus image"
}
$fixtureEvidence = [ordered]@{}
foreach ($entry in $fixtureFiles.GetEnumerator()) {
    $locked = @($fixtureLock.artifacts | Where-Object { $_.name -eq $entry.Key })
    Assert-YoloR3Condition ($locked.Count -eq 1) "Fixture lock must contain exactly one $($entry.Key) entry"
    $actualLength = (Get-Item -LiteralPath $entry.Value).Length
    $actualSha256 = Get-YoloR3Sha256 -Path $entry.Value
    Assert-YoloR3Condition ($actualLength -eq [long]$locked[0].length) "$($entry.Key) length differs from fixture lock"
    Assert-YoloR3Condition ($actualSha256 -eq ([string]$locked[0].sha256).ToLowerInvariant()) "$($entry.Key) digest differs from fixture lock"
    $fixtureEvidence[$entry.Key] = [ordered]@{ length = $actualLength; sha256 = $actualSha256 }
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
Invoke-YoloR3External -FilePath $providerGradle -Arguments @($gradleArguments + ":app:assembleDebug") -WorkingDirectory $siblingRoot
Invoke-YoloR3External -FilePath $mainGradle -Arguments @($gradleArguments + ":app:assembleAppDebug") -WorkingDirectory $repositoryRoot
Invoke-YoloR3External -FilePath $mainGradle -Arguments @($gradleArguments + ":app:assembleAppDebugAndroidTest") -WorkingDirectory $repositoryRoot

$providerApk = Resolve-YoloR3ApkFromMetadata -MetadataPath (Join-Path $siblingRoot "app\build\outputs\apk\debug\output-metadata.json") -Label "Provider"
$hostApk = Resolve-YoloR3ApkFromMetadata -MetadataPath (Join-Path $repositoryRoot "app\build\outputs\apk\app\debug\output-metadata.json") -Label "Host" -Abi "arm64-v8a"
$testApk = Resolve-YoloR3ApkFromMetadata -MetadataPath (Join-Path $repositoryRoot "app\build\outputs\apk\androidTest\app\debug\output-metadata.json") -Label "Instrumentation"
Assert-YoloR3ApkClean -ApkPath $providerApk -Label "Provider" -RequireNative
Assert-YoloR3ApkClean -ApkPath $hostApk -Label "Host"
Assert-YoloR3ApkClean -ApkPath $testApk -Label "Instrumentation"

$providerSigner = Get-YoloR3ApkSignerDigest -ApkSigner $apkSigner -ApkPath $providerApk -Label "Provider"
$hostSigner = Get-YoloR3ApkSignerDigest -ApkSigner $apkSigner -ApkPath $hostApk -Label "Host"
$testSigner = Get-YoloR3ApkSignerDigest -ApkSigner $apkSigner -ApkPath $testApk -Label "Instrumentation"
Assert-YoloR3Condition ($providerSigner -eq $hostSigner -and $hostSigner -eq $testSigner) "Host, provider, and instrumentation APK signers differ"

$deviceRows = @(Invoke-YoloR3External -FilePath $adb -Arguments @("devices", "-l") -Quiet)
$onlineRows = @($deviceRows | Where-Object { $_ -match "^$([regex]::Escape($DeviceSerial))\s+device(?:\s|$)" })
Assert-YoloR3Condition ($onlineRows.Count -eq 1) "Device $DeviceSerial is not uniquely online"
$deviceApi = [int]((Invoke-YoloR3Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "getprop", "ro.build.version.sdk") -Quiet) -join '').Trim()
$deviceAbi = ((Invoke-YoloR3Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "getprop", "ro.product.cpu.abi") -Quiet) -join '').Trim()
$deviceModel = ((Invoke-YoloR3Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "getprop", "ro.product.model") -Quiet) -join '').Trim()
$deviceFingerprint = ((Invoke-YoloR3Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "getprop", "ro.build.fingerprint") -Quiet) -join '').Trim()
Assert-YoloR3Condition ($deviceApi -ge 24) "R3 requires Android API 24 or newer"
Assert-YoloR3Condition ($deviceAbi -eq "arm64-v8a") "R3 provider is arm64-only; device ABI is $deviceAbi"
foreach ($packageName in @($hostPackage, $providerPackage, $testPackage)) {
    Assert-YoloR3Condition (-not (Test-YoloR3PackageInstalled -AdbPath $adb -Serial $DeviceSerial -PackageName $packageName)) (
        "R3 refuses to replace pre-existing package $packageName on $DeviceSerial"
    )
}

$deviceStaging = "/data/local/tmp/autojs6-yolo-r3-$([guid]::NewGuid().ToString('N'))"
Assert-YoloR3Condition (
    $deviceStaging -match '^/data/local/tmp/autojs6-yolo-r3-[0-9a-f]{32}$'
) "Unsafe R3 device staging path"
$deviceResult = $null
$instrumentationText = ""
$cleanupComplete = $false
try {
    Invoke-YoloR3Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "mkdir", "-p", $deviceStaging) | Out-Null
    $stagingFiles = [ordered]@{
        "model.json" = $fixtureFiles["model-manifest-v1.json"]
        "model.ncnn.param" = $fixtureFiles["model.ncnn.param"]
        "model.ncnn.bin" = $fixtureFiles["model.ncnn.bin"]
        "bus.jpg" = $fixtureFiles["bus.jpg"]
        "expected-bus-detections.json" = $fixtureFiles["expected-bus-detections.json"]
    }
    foreach ($entry in $stagingFiles.GetEnumerator()) {
        Invoke-YoloR3Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("push", $entry.Value, "$deviceStaging/$($entry.Key)") | Out-Null
        Invoke-YoloR3Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "chmod", "0644", "$deviceStaging/$($entry.Key)") | Out-Null
    }

    Invoke-YoloR3Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("install", "-r", $hostApk) | Out-Null
    Invoke-YoloR3Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("install", "-r", $providerApk) | Out-Null
    Invoke-YoloR3Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("install", "-r", "-t", $testApk) | Out-Null

    Invoke-YoloR3Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "run-as", $hostPackage, "rm", "-rf", $fixturePrivateDirectory) | Out-Null
    Invoke-YoloR3Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "run-as", $hostPackage, "mkdir", "-p", $modelPrivateDirectory) | Out-Null
    foreach ($name in @("model.json", "model.ncnn.param", "model.ncnn.bin")) {
        Invoke-YoloR3Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "run-as", $hostPackage, "cp", "$deviceStaging/$name", "$modelPrivateDirectory/$name") | Out-Null
        Invoke-YoloR3Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "run-as", $hostPackage, "chmod", "0400", "$modelPrivateDirectory/$name") | Out-Null
    }
    foreach ($name in @("bus.jpg", "expected-bus-detections.json")) {
        Invoke-YoloR3Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "run-as", $hostPackage, "cp", "$deviceStaging/$name", "$fixturePrivateDirectory/$name") | Out-Null
        Invoke-YoloR3Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "run-as", $hostPackage, "chmod", "0400", "$fixturePrivateDirectory/$name") | Out-Null
    }
    Invoke-YoloR3Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "run-as", $hostPackage, "rm", "-f", $resultPrivateFile) | Out-Null

    $instrumentationOutput = @(Invoke-YoloR3Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @(
        "shell", "am", "instrument", "-w", "-r",
        "-e", "yoloR3Enabled", "true",
        "-e", "class", "$testClass#$testMethod",
        "$testPackage/$runner"
    ))
    $instrumentationText = $instrumentationOutput -join "`n"
    Assert-YoloR3Condition ($instrumentationText -match '(?m)^OK \(1 test\)\s*$') "R3 instrumentation did not report one passing test"
    Assert-YoloR3Condition ($instrumentationText -notmatch '(?m)^(FAILURES!!!|INSTRUMENTATION_FAILED:)') "R3 instrumentation reported a failure"

    $resultText = (Invoke-YoloR3Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("exec-out", "run-as", $hostPackage, "cat", $resultPrivateFile) -Quiet) -join "`n"
    try { $deviceResult = $resultText | ConvertFrom-Json } catch { throw "R3 device result is not valid JSON`n$($_.Exception.Message)" }
    Assert-YoloR3Condition ([int]$deviceResult.schemaVersion -eq 1 -and $deviceResult.status -eq "PASS") "R3 device result did not report PASS"
    Assert-YoloR3Condition ($deviceResult.component -eq $providerComponent) "R3 used an unexpected provider component"
    Assert-YoloR3Condition ([bool]$deviceResult.productionRhino) "R3 did not run the production Rhino engine"
    Assert-YoloR3Condition ($deviceResult.engineClass -eq "org.autojs.autojs.engine.LoopBasedJavaScriptEngine") "R3 started an unexpected script engine"
    Assert-YoloR3Condition ([bool]$deviceResult.sameSigner -and [bool]$deviceResult.separateProviderUid) "R3 provider identity isolation failed"
    Assert-YoloR3Condition ([bool]$deviceResult.defaultOffObserved) "R3 did not observe default-off before explicit opt-in"
    Assert-YoloR3Condition ([bool]$deviceResult.publicApiPresent) "R3 public yolo API was absent"
    Assert-YoloR3Condition ([bool]$deviceResult.idempotentClose) "R3 repeated detector.close failed"
    Assert-YoloR3Condition ([bool]$deviceResult.rectCentersUsable) "R3 RectF result was not directly usable"
    Assert-YoloR3Condition ([string]$deviceResult.scriptSha256 -match '^[0-9a-f]{64}$') "R3 script digest is invalid"
    $detections = @($deviceResult.detections)
    Assert-YoloR3Condition ($detections.Count -eq 5) "R3 expected exactly five detections"
    Assert-YoloR3Condition (@($detections | Where-Object { [int]$_.classId -eq 5 -and $_.label -eq "bus" }).Count -eq 1) "R3 expected one bus"
    Assert-YoloR3Condition (@($detections | Where-Object { [int]$_.classId -eq 0 -and $_.label -eq "person" }).Count -eq 4) "R3 expected four people"
    Assert-YoloR3Condition (@($deviceResult.comparisons).Count -eq 5) "R3 comparison evidence is incomplete"
} finally {
    $cleanupErrors = [System.Collections.Generic.List[string]]::new()
    $hostPresentForCleanup = $false
    try {
        $hostPresentForCleanup = Test-YoloR3PackageInstalled -AdbPath $adb -Serial $DeviceSerial -PackageName $hostPackage
    } catch { $cleanupErrors.Add($_.Exception.Message) }
    if ($hostPresentForCleanup) {
        try { Invoke-YoloR3Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "run-as", $hostPackage, "rm", "-rf", $fixturePrivateDirectory) -Quiet | Out-Null } catch { $cleanupErrors.Add($_.Exception.Message) }
        try { Invoke-YoloR3Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "run-as", $hostPackage, "rm", "-f", $resultPrivateFile) -Quiet | Out-Null } catch { $cleanupErrors.Add($_.Exception.Message) }
    }
    try { Invoke-YoloR3Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("shell", "rm", "-rf", $deviceStaging) -Quiet | Out-Null } catch { $cleanupErrors.Add($_.Exception.Message) }
    foreach ($packageName in @($testPackage, $providerPackage, $hostPackage)) {
        try {
            if (Test-YoloR3PackageInstalled -AdbPath $adb -Serial $DeviceSerial -PackageName $packageName) {
                Invoke-YoloR3Adb -AdbPath $adb -Serial $DeviceSerial -Arguments @("uninstall", $packageName) -Quiet | Out-Null
            }
        } catch { $cleanupErrors.Add($_.Exception.Message) }
    }
    foreach ($packageName in @($hostPackage, $providerPackage, $testPackage)) {
        try {
            if (Test-YoloR3PackageInstalled -AdbPath $adb -Serial $DeviceSerial -PackageName $packageName) {
                $cleanupErrors.Add("Package remains installed after cleanup: $packageName")
            }
        } catch { $cleanupErrors.Add($_.Exception.Message) }
    }
    $cleanupComplete = $cleanupErrors.Count -eq 0
    if (-not $cleanupComplete) { throw "R3 cleanup failed: $($cleanupErrors -join '; ')" }
}

Assert-YoloR3Condition ($null -ne $deviceResult) "R3 produced no device result"
$reportDirectory = Join-Path $repositoryRoot "build\reports\yolo"
[void](New-Item -ItemType Directory -Force -Path $reportDirectory)
$reportPath = Join-Path $reportDirectory "r3-summary.generated.json"
$report = [ordered]@{
    schemaVersion = 1
    roadmapStage = "R3"
    status = "PASS"
    evidenceLevel = "real-device-production-rhino-exact-provider-ncnn"
    generatedAtUtc = [DateTime]::UtcNow.ToString("o")
    mainRevision = $mainRevision
    providerRevision = $providerRevision
    sourceRepositoriesClean = $true
    evidenceStatus = [ordered]@{
        source = "PASS"
        static = "NOT_RUN_BY_R3_GATE"
        jvm = "NOT_RUN_BY_R3_GATE"
        androidBuild = "PASS"
        binderPfd = "PASS_VIA_PRODUCTION_HOST_CHAIN"
        nativeRuntime = "PASS"
        deviceE2e = "PASS"
        production = "NOT_CLAIMED_PUBLIC_PREVIEW_ONLY"
    }
    invocation = [ordered]@{
        script = "tools/yolo/verify-yolo-r3.ps1"
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
        rectCentersUsable = [bool]$deviceResult.rectCentersUsable
    }
    apks = [ordered]@{
        host = [ordered]@{ path = $hostApk; sha256 = Get-YoloR3Sha256 -Path $hostApk; signerSha256 = $hostSigner; modelFree = $true; pluginNativeAbsent = $true }
        provider = [ordered]@{ path = $providerApk; sha256 = Get-YoloR3Sha256 -Path $providerApk; signerSha256 = $providerSigner; modelFree = $true; arm64NativePresent = $true }
        instrumentation = [ordered]@{ path = $testApk; sha256 = Get-YoloR3Sha256 -Path $testApk; signerSha256 = $testSigner; modelFree = $true; pluginNativeAbsent = $true }
    }
    fixture = $fixtureEvidence
    fixtureDeployment = [ordered]@{
        manifestSourceName = "model-manifest-v1.json"
        manifestStagedName = "model.json"
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
    detections = @($deviceResult.detections)
    comparisons = @($deviceResult.comparisons)
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
        SOURCE = "PASS"
        ANDROID_BUILD = "PASS"
        DEVICE_E2E = "PASS"
        PUBLIC_PREVIEW = "PASS"
        PRODUCTION = "NOT_CLAIMED"
    }
}
$report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $reportPath -Encoding utf8
Write-Host "YOLO_R3=PASS"
Write-Host "YOLO_R3_REPORT=$reportPath"
