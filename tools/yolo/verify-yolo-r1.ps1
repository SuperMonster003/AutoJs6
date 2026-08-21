[CmdletBinding()]
param(
    [string] $SiblingPath = "D:\idea-projects\AutoJs6-Plugin-Yolo-NCNN",

    [string] $DeviceSerial,

    [switch] $BuildOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$protectedDeviceSerial = "QV710AF65F"
$providerApplicationId = "io.github.supermonster003.autojs6.plugin.yolo.ncnn"
$testApplicationId = "$providerApplicationId.test"
$testRunner = "androidx.test.runner.AndroidJUnitRunner"
$smokeTestClass = "$providerApplicationId.provider.NativeYoloSmokeTest"
$requiredNativeEntry = "lib/arm64-v8a/libautojs_yolo.so"
$forbiddenPackagedFiles = @("model.ncnn.bin", "model.ncnn.param", "bus.jpg")

function Assert-YoloR1Condition {
    param(
        [Parameter(Mandatory = $true)]
        [bool] $Condition,

        [Parameter(Mandatory = $true)]
        [string] $Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function Get-YoloR1RequiredFile {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Path,

        [Parameter(Mandatory = $true)]
        [string] $Label
    )

    $fullPath = [System.IO.Path]::GetFullPath($Path)
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
        throw "$Label is missing: $fullPath"
    }
    return $fullPath
}

function Get-YoloR1RequiredDirectory {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Path,

        [Parameter(Mandatory = $true)]
        [string] $Label
    )

    $fullPath = [System.IO.Path]::GetFullPath($Path)
    if (-not (Test-Path -LiteralPath $fullPath -PathType Container)) {
        throw "$Label is missing: $fullPath"
    }
    return $fullPath
}

function Read-YoloR1Json {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Path,

        [Parameter(Mandatory = $true)]
        [string] $Label
    )

    $requiredPath = Get-YoloR1RequiredFile -Path $Path -Label $Label
    try {
        return Get-Content -Raw -LiteralPath $requiredPath | ConvertFrom-Json
    } catch {
        throw "$Label is not valid JSON: $requiredPath`n$($_.Exception.Message)"
    }
}

function Get-YoloR1JsonProperty {
    param(
        [Parameter(Mandatory = $true)]
        [object] $Object,

        [Parameter(Mandatory = $true)]
        [string] $Name,

        [Parameter(Mandatory = $true)]
        [string] $Label,

        [switch] $AllowNull
    )

    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) {
        throw "$Label is missing property '$Name'"
    }
    if (-not $AllowNull -and $null -eq $property.Value) {
        throw "$Label property '$Name' must not be null"
    }
    return $property.Value
}

function Get-YoloR1Sha256 {
    param([Parameter(Mandatory = $true)][string] $Path)

    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Assert-YoloR1Sha256Text {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Value,

        [Parameter(Mandatory = $true)]
        [string] $Label
    )

    if ($Value -notmatch '^[0-9a-fA-F]{64}$') {
        throw "$Label must be a 64-character SHA-256 value"
    }
}

function Resolve-YoloR1ChildFile {
    param(
        [Parameter(Mandatory = $true)]
        [string] $BasePath,

        [Parameter(Mandatory = $true)]
        [string] $RelativePath,

        [Parameter(Mandatory = $true)]
        [string] $Label
    )

    $baseFullPath = [System.IO.Path]::GetFullPath($BasePath).TrimEnd('\', '/')
    $candidate = [System.IO.Path]::GetFullPath((Join-Path $baseFullPath $RelativePath))
    $basePrefix = $baseFullPath + [System.IO.Path]::DirectorySeparatorChar
    if (-not $candidate.StartsWith($basePrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "$Label escapes its allowed root: $RelativePath"
    }
    return Get-YoloR1RequiredFile -Path $candidate -Label $Label
}

function Assert-YoloR1LockedFile {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Path,

        [Parameter(Mandatory = $true)]
        [object] $LockEntry,

        [Parameter(Mandatory = $true)]
        [string] $Label
    )

    $requiredPath = Get-YoloR1RequiredFile -Path $Path -Label $Label
    $expectedLength = [long](Get-YoloR1JsonProperty -Object $LockEntry -Name "length" -Label $Label)
    $expectedSha256 = [string](Get-YoloR1JsonProperty -Object $LockEntry -Name "sha256" -Label $Label)
    Assert-YoloR1Sha256Text -Value $expectedSha256 -Label "$Label sha256"

    $actualLength = (Get-Item -LiteralPath $requiredPath).Length
    $actualSha256 = Get-YoloR1Sha256 -Path $requiredPath
    Assert-YoloR1Condition ($actualLength -eq $expectedLength) `
        "$Label length mismatch: expected $expectedLength, found $actualLength"
    Assert-YoloR1Condition ($actualSha256 -eq $expectedSha256.ToLowerInvariant()) `
        "$Label SHA-256 mismatch: expected $expectedSha256, found $actualSha256"

    return [ordered]@{
        path = $requiredPath
        length = $actualLength
        sha256 = $actualSha256
    }
}

function Invoke-YoloR1External {
    param(
        [Parameter(Mandatory = $true)]
        [string] $FilePath,

        [string[]] $Arguments = @(),

        [string] $WorkingDirectory,

        [switch] $Quiet
    )

    $displayCommand = "$FilePath $($Arguments -join ' ')".Trim()
    if (-not $Quiet) {
        Write-Host "> $displayCommand"
    }
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
    if (-not $Quiet) {
        $output | ForEach-Object { Write-Host $_ }
    }
    if ($exitCode -ne 0) {
        throw "External command failed with exit code ${exitCode}: $displayCommand"
    }
    return $output
}

function Get-YoloR1GitText {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Repository,

        [Parameter(Mandatory = $true)]
        [string[]] $Arguments
    )

    $output = Invoke-YoloR1External `
        -FilePath "git" -Arguments (@("-C", $Repository) + $Arguments) -Quiet
    return ($output -join "`n").Trim()
}

function Test-YoloR1PackageInstalled {
    param(
        [Parameter(Mandatory = $true)][string] $AdbPath,
        [Parameter(Mandatory = $true)][string] $Serial,
        [Parameter(Mandatory = $true)][string] $PackageName
    )

    $output = @(& $AdbPath -s $Serial shell pm path $PackageName 2>&1)
    $exitCode = $LASTEXITCODE
    if ($exitCode -eq 0) {
        return (($output -join "`n") -match '(?m)^package:')
    }
    if ($exitCode -eq 1) {
        return $false
    }
    throw "Cannot query package $PackageName on device $Serial (exit $exitCode)"
}

function Get-YoloR1SourceSnapshotSha256 {
    param([Parameter(Mandatory = $true)][string] $RepositoryRoot)

    $moduleRoot = Get-YoloR1RequiredDirectory `
        -Path (Join-Path $RepositoryRoot "plugin-api\yolo-api") `
        -Label "YOLO API module"
    $sourceRoot = Get-YoloR1RequiredDirectory `
        -Path (Join-Path $moduleRoot "src\main") `
        -Label "YOLO API main source"
    $modulePrefixLength = $moduleRoot.TrimEnd('\', '/').Length + 1
    $lines = Get-ChildItem -LiteralPath $sourceRoot -Recurse -File |
        Sort-Object FullName |
        ForEach-Object {
        $relativePath = $_.FullName.Substring($modulePrefixLength).Replace('\', '/')
        "$relativePath=$(Get-YoloR1Sha256 -Path $_.FullName)"
    }
    $manifest = ($lines -join "`n") + "`n"
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $digest = $sha256.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($manifest))
    } finally {
        $sha256.Dispose()
    }
    return ([System.BitConverter]::ToString($digest)).Replace("-", "").ToLowerInvariant()
}

function Resolve-YoloR1SingleApk {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Directory,

        [Parameter(Mandatory = $true)]
        [string] $Label
    )

    $requiredDirectory = Get-YoloR1RequiredDirectory -Path $Directory -Label "$Label output directory"
    $apks = @(Get-ChildItem -LiteralPath $requiredDirectory -File -Filter "*.apk")
    if ($apks.Count -ne 1) {
        $found = if ($apks.Count -eq 0) { "none" } else { ($apks.Name -join ", ") }
        throw "$Label output must contain exactly one APK; found $found in $requiredDirectory"
    }
    return $apks[0].FullName
}

function Assert-YoloR1ProviderApk {
    param([Parameter(Mandatory = $true)][string] $ApkPath)

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($ApkPath)
    try {
        $entryNames = @($archive.Entries | ForEach-Object { $_.FullName.Replace('\', '/') })
        Assert-YoloR1Condition ($entryNames -ccontains $requiredNativeEntry) `
            "Provider APK does not contain $requiredNativeEntry"

        $nativeEntries = @($entryNames | Where-Object {
            $_ -match '^lib/[^/]+/libautojs_yolo\.so$'
        })
        Assert-YoloR1Condition ($nativeEntries.Count -eq 1) `
            "Provider APK must contain exactly one libautojs_yolo.so entry"

        $forbiddenEntries = @($entryNames | Where-Object {
            $leafName = [System.IO.Path]::GetFileName($_).ToLowerInvariant()
            $forbiddenPackagedFiles -contains $leafName
        })
        Assert-YoloR1Condition ($forbiddenEntries.Count -eq 0) `
            "Provider APK contains local-only fixture/model files: $($forbiddenEntries -join ', ')"
    } finally {
        $archive.Dispose()
    }
}

if (-not [string]::IsNullOrWhiteSpace($DeviceSerial)) {
    $DeviceSerial = $DeviceSerial.Trim()
    if ($DeviceSerial.Equals($protectedDeviceSerial, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "DeviceSerial $protectedDeviceSerial is protected and must not be used by the YOLO R1 gate"
    }
    if ($DeviceSerial -notmatch '^[A-Za-z0-9._:-]+$') {
        throw "DeviceSerial contains unsupported characters"
    }
}
if ($BuildOnly -and -not [string]::IsNullOrWhiteSpace($DeviceSerial)) {
    throw "-BuildOnly cannot be combined with -DeviceSerial"
}

$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$siblingRoot = Get-YoloR1RequiredDirectory -Path $SiblingPath -Label "YOLO NCNN sibling"
$gradleWrapper = Get-YoloR1RequiredFile -Path (Join-Path $siblingRoot "gradlew.bat") -Label "Sibling Gradle wrapper"

$mainTopLevel = Get-YoloR1GitText -Repository $repositoryRoot -Arguments @("rev-parse", "--show-toplevel")
Assert-YoloR1Condition (
    [System.IO.Path]::GetFullPath($mainTopLevel).TrimEnd('\', '/') -eq $repositoryRoot.TrimEnd('\', '/')
) "R1 gate is not running from the expected main repository"
$mainHead = Get-YoloR1GitText -Repository $repositoryRoot -Arguments @("rev-parse", "HEAD")

$r0StaticGate = Get-YoloR1RequiredFile `
    -Path (Join-Path $repositoryRoot "tools\yolo\verify-yolo-r0-static.ps1") `
    -Label "R0 static gate"
$r0StaticOutput = @(& $r0StaticGate -RepositoryRoot $repositoryRoot)
Assert-YoloR1Condition (($r0StaticOutput -join "`n") -match 'YOLO_R0_STATIC=PASS') `
    "R0 static gate did not report PASS"

$protocolLockPath = Join-Path $siblingRoot "libs\protocol-aars.lock.json"
$protocolLock = Read-YoloR1Json -Path $protocolLockPath -Label "Protocol AAR lock"
Assert-YoloR1Condition (
    [int](Get-YoloR1JsonProperty -Object $protocolLock -Name "schemaVersion" -Label "Protocol AAR lock") -eq 1
) "Protocol AAR lock schemaVersion must be 1"
$sourceRevision = [string](Get-YoloR1JsonProperty `
    -Object $protocolLock -Name "sourceRevision" -Label "Protocol AAR lock" -AllowNull)
$sourceSnapshotSha256 = [string](Get-YoloR1JsonProperty `
    -Object $protocolLock -Name "sourceSnapshotSha256" -Label "Protocol AAR lock" -AllowNull)
Assert-YoloR1Condition (
    -not [string]::IsNullOrWhiteSpace($sourceRevision) -or
    -not [string]::IsNullOrWhiteSpace($sourceSnapshotSha256)
) "Protocol AAR lock must identify an R0 sourceRevision or sourceSnapshotSha256"

if (-not [string]::IsNullOrWhiteSpace($sourceRevision)) {
    Assert-YoloR1Condition ($sourceRevision -match '^[0-9a-fA-F]{40}$') `
        "Protocol AAR sourceRevision must be a full Git commit"
    [void](Get-YoloR1GitText -Repository $repositoryRoot -Arguments @("cat-file", "-e", "$sourceRevision^{commit}"))
    foreach ($sourcePath in @(
        "plugin-api/common-plugin-api/build.gradle.kts",
        "plugin-api/protocol-wire-api/build.gradle.kts",
        "plugin-api/yolo-api/build.gradle.kts"
    )) {
        [void](Get-YoloR1GitText -Repository $repositoryRoot -Arguments @("cat-file", "-e", "${sourceRevision}:$sourcePath"))
    }
    [void](Get-YoloR1GitText -Repository $repositoryRoot -Arguments @(
        "merge-base", "--is-ancestor", $sourceRevision, $mainHead
    ))
}
if (-not [string]::IsNullOrWhiteSpace($sourceSnapshotSha256)) {
    Assert-YoloR1Sha256Text -Value $sourceSnapshotSha256 -Label "Protocol AAR sourceSnapshotSha256"
    $actualSourceSnapshotSha256 = Get-YoloR1SourceSnapshotSha256 -RepositoryRoot $repositoryRoot
    Assert-YoloR1Condition (
        $actualSourceSnapshotSha256 -eq $sourceSnapshotSha256.ToLowerInvariant()
    ) "Main R0 source snapshot SHA-256 does not match the protocol AAR lock"
}

$expectedAarNames = @("common-plugin-api.aar", "protocol-wire-api.aar", "yolo-api.aar")
$protocolArtifacts = @(Get-YoloR1JsonProperty `
    -Object $protocolLock -Name "artifacts" -Label "Protocol AAR lock")
Assert-YoloR1Condition ($protocolArtifacts.Count -eq $expectedAarNames.Count) `
    "Protocol AAR lock must contain exactly the three required AARs"
$aarEvidence = @()
foreach ($aarName in $expectedAarNames) {
    $matches = @($protocolArtifacts | Where-Object { [string]$_.name -eq $aarName })
    Assert-YoloR1Condition ($matches.Count -eq 1) `
        "Protocol AAR lock must contain exactly one $aarName entry"
    $aarEvidence += Assert-YoloR1LockedFile `
        -Path (Join-Path $siblingRoot "libs\$aarName") `
        -LockEntry $matches[0] `
        -Label "Protocol AAR $aarName"
}

$ncnnRoot = Join-Path $siblingRoot "third_party\ncnn"
$ncnnLockPath = Join-Path $ncnnRoot "provenance.lock.json"
$ncnnLock = Read-YoloR1Json -Path $ncnnLockPath -Label "NCNN provenance lock"
Assert-YoloR1Condition (
    [int](Get-YoloR1JsonProperty -Object $ncnnLock -Name "schemaVersion" -Label "NCNN provenance lock") -eq 1
) "NCNN provenance lock schemaVersion must be 1"
Assert-YoloR1Condition ([string]$ncnnLock.name -eq "ncnn") "NCNN provenance lock name must be ncnn"
Assert-YoloR1Condition ([string]$ncnnLock.stl -eq "c++_static") "R1 NCNN STL must be c++_static"
Assert-YoloR1Condition (-not [bool]$ncnnLock.vulkan) "R1 Vulkan must remain disabled"
$ncnnAbis = @($ncnnLock.abis | ForEach-Object { [string]$_ })
Assert-YoloR1Condition ($ncnnAbis.Count -eq 1 -and $ncnnAbis[0] -eq "arm64-v8a") `
    "R1 NCNN lock must contain only arm64-v8a"
Assert-YoloR1Sha256Text -Value ([string]$ncnnLock.archiveSha256) -Label "NCNN archiveSha256"
$ncnnLibraryEntry = Get-YoloR1JsonProperty `
    -Object $ncnnLock -Name "arm64StaticLibrary" -Label "NCNN provenance lock"
$ncnnLibraryPath = Resolve-YoloR1ChildFile `
    -BasePath $ncnnRoot -RelativePath ([string]$ncnnLibraryEntry.path) -Label "NCNN arm64 static library"
$ncnnLibraryEvidence = Assert-YoloR1LockedFile `
    -Path $ncnnLibraryPath -LockEntry $ncnnLibraryEntry -Label "NCNN arm64 static library"
$ncnnLicensePath = Resolve-YoloR1ChildFile `
    -BasePath $ncnnRoot -RelativePath ([string]$ncnnLock.licenseFile) -Label "NCNN license"
$ncnnLicenseEntry = [pscustomobject]@{
    length = (Get-Item -LiteralPath $ncnnLicensePath).Length
    sha256 = [string]$ncnnLock.licenseSha256
}
$ncnnLicenseEvidence = Assert-YoloR1LockedFile `
    -Path $ncnnLicensePath -LockEntry $ncnnLicenseEntry -Label "NCNN license"

$fixtureMetadataRoot = Join-Path $siblingRoot "fixtures\yolo11n"
$fixtureLocalRoot = Join-Path $siblingRoot "fixtures\local\yolo11n"
$fixtureLockPath = Join-Path $fixtureMetadataRoot "fixture.lock.json"
$fixtureLock = Read-YoloR1Json -Path $fixtureLockPath -Label "YOLO fixture lock"
Assert-YoloR1Condition (
    [int](Get-YoloR1JsonProperty -Object $fixtureLock -Name "schemaVersion" -Label "YOLO fixture lock") -eq 1
) "YOLO fixture lock schemaVersion must be 1"
Assert-YoloR1Condition ([string]$fixtureLock.state -eq "local-only") `
    "YOLO fixture lock must remain local-only"
Assert-YoloR1Condition ([string]$fixtureLock.task -eq "detect") "YOLO fixture task must be detect"
Assert-YoloR1Condition ([string]$fixtureLock.backend -eq "ncnn") "YOLO fixture backend must be ncnn"

$fixtureArtifacts = @(Get-YoloR1JsonProperty `
    -Object $fixtureLock -Name "artifacts" -Label "YOLO fixture lock")
$fixtureEvidence = @()
foreach ($fixtureName in @("metadata.yaml", "model.ncnn.param", "model.ncnn.bin", "bus.jpg")) {
    $matches = @($fixtureArtifacts | Where-Object { [string]$_.name -eq $fixtureName })
    Assert-YoloR1Condition ($matches.Count -eq 1) `
        "YOLO fixture lock must contain exactly one $fixtureName entry"
    $fixtureEvidence += Assert-YoloR1LockedFile `
        -Path (Join-Path $fixtureLocalRoot $fixtureName) `
        -LockEntry $matches[0] `
        -Label "Local YOLO fixture $fixtureName"
}

$modelManifestName = "model-manifest-v1.json"
$expectedDetectionsName = "expected-bus-detections.json"
$modelManifestEntry = @($fixtureArtifacts | Where-Object { [string]$_.name -eq $modelManifestName })
$expectedDetectionsEntry = @($fixtureArtifacts | Where-Object { [string]$_.name -eq $expectedDetectionsName })
Assert-YoloR1Condition ($modelManifestEntry.Count -eq 1) `
    "YOLO fixture lock must contain exactly one $modelManifestName entry"
Assert-YoloR1Condition ($expectedDetectionsEntry.Count -eq 1) `
    "YOLO fixture lock must contain exactly one $expectedDetectionsName entry"
$modelManifestPath = Join-Path $fixtureMetadataRoot $modelManifestName
$expectedDetectionsPath = Join-Path $fixtureMetadataRoot $expectedDetectionsName
$modelManifestEvidence = Assert-YoloR1LockedFile `
    -Path $modelManifestPath -LockEntry $modelManifestEntry[0] -Label "YOLO model manifest"
$expectedDetectionsEvidence = Assert-YoloR1LockedFile `
    -Path $expectedDetectionsPath -LockEntry $expectedDetectionsEntry[0] -Label "YOLO expected detections"

$modelManifest = Read-YoloR1Json -Path $modelManifestPath -Label "YOLO model manifest"
Assert-YoloR1Condition ([int]$modelManifest.schemaVersion -eq 1) "YOLO model manifest schemaVersion must be 1"
Assert-YoloR1Condition ([string]$modelManifest.engine -eq "yolo") "YOLO model manifest engine must be yolo"
Assert-YoloR1Condition ([string]$modelManifest.backend -eq "ncnn") "YOLO model manifest backend must be ncnn"
Assert-YoloR1Condition ([string]$modelManifest.task -eq "detect") "YOLO model manifest task must be detect"
Assert-YoloR1Condition ([string]$modelManifest.input.name -eq [string]$fixtureLock.graph.inputName) `
    "YOLO manifest input node does not match the fixture lock"
Assert-YoloR1Condition ([int]$modelManifest.input.width -eq [int]$fixtureLock.inputWidth) `
    "YOLO manifest input width does not match the fixture lock"
Assert-YoloR1Condition ([int]$modelManifest.input.height -eq [int]$fixtureLock.inputHeight) `
    "YOLO manifest input height does not match the fixture lock"
Assert-YoloR1Condition ([string]$modelManifest.output.name -eq [string]$fixtureLock.graph.outputName) `
    "YOLO manifest output node does not match the fixture lock"
Assert-YoloR1Condition ([string]$modelManifest.output.decoder -eq [string]$fixtureLock.decoderId) `
    "YOLO manifest decoder does not match the fixture lock"
$manifestLabels = @($modelManifest.labels)
Assert-YoloR1Condition ($manifestLabels.Count -eq [int]$fixtureLock.graph.classes) `
    "YOLO manifest label count does not match the fixture lock"

$expectedDetections = Read-YoloR1Json -Path $expectedDetectionsPath -Label "YOLO expected detections"
Assert-YoloR1Condition ([int]$expectedDetections.schemaVersion -eq 1) `
    "YOLO expected detections schemaVersion must be 1"
Assert-YoloR1Condition ([string]$expectedDetections.image.name -eq "bus.jpg") `
    "YOLO expected detections must use bus.jpg"
$busEvidence = @($fixtureEvidence | Where-Object {
    [System.IO.Path]::GetFileName([string]$_.path) -eq "bus.jpg"
})
Assert-YoloR1Condition ($busEvidence.Count -eq 1) "Local bus.jpg evidence is missing"
Assert-YoloR1Condition ([string]$expectedDetections.image.sha256 -eq [string]$busEvidence[0].sha256) `
    "Expected-detections image SHA-256 does not match local bus.jpg"
Assert-YoloR1Condition (@($expectedDetections.detections).Count -gt 0) `
    "YOLO expected detections must not be empty"

$siblingStatusWithBranch = Get-YoloR1GitText `
    -Repository $siblingRoot -Arguments @("status", "--short", "--branch", "--untracked-files=all")
$siblingRevision = if ($siblingStatusWithBranch -match '(?m)^## No commits yet on (?<branch>[^\r\n]+)$') {
    "UNBORN:$($Matches.branch)"
} else {
    Get-YoloR1GitText -Repository $siblingRoot -Arguments @("rev-parse", "HEAD")
}
$siblingDirty = (Get-YoloR1GitText `
    -Repository $siblingRoot -Arguments @("status", "--short", "--untracked-files=all")) -ne ""

$gradleArguments = @(
    "--console=plain",
    ":app:assembleDebug",
    ":app:assembleDebugAndroidTest"
)
[void](Invoke-YoloR1External `
    -FilePath $gradleWrapper -Arguments $gradleArguments -WorkingDirectory $siblingRoot)

$providerApkPath = Resolve-YoloR1SingleApk `
    -Directory (Join-Path $siblingRoot "app\build\outputs\apk\debug") `
    -Label "Provider debug APK"
$testApkPath = Resolve-YoloR1SingleApk `
    -Directory (Join-Path $siblingRoot "app\build\outputs\apk\androidTest\debug") `
    -Label "Instrumentation debug APK"
Assert-YoloR1ProviderApk -ApkPath $providerApkPath
$providerApkEvidence = [ordered]@{
    path = $providerApkPath
    length = (Get-Item -LiteralPath $providerApkPath).Length
    sha256 = Get-YoloR1Sha256 -Path $providerApkPath
}
$testApkEvidence = [ordered]@{
    path = $testApkPath
    length = (Get-Item -LiteralPath $testApkPath).Length
    sha256 = Get-YoloR1Sha256 -Path $testApkPath
}

$nativeStatus = if ($BuildOnly) { "NOT_RUN_BUILD_ONLY" } else { "NOT_RUN_NO_SERIAL" }
$deviceEnvironment = $null
$nativeResult = $null
if (-not $BuildOnly -and -not [string]::IsNullOrWhiteSpace($DeviceSerial)) {
    $adbCommand = Get-Command "adb.exe" -ErrorAction Stop
    $adbPath = $adbCommand.Source
    $deviceState = (Invoke-YoloR1External -FilePath $adbPath -Arguments @(
        "-s", $DeviceSerial, "get-state"
    ) | Select-Object -Last 1).ToString().Trim()
    Assert-YoloR1Condition ($deviceState -eq "device") "Device $DeviceSerial is not online"
    $deviceEnvironment = [ordered]@{
        model = ((Invoke-YoloR1External -FilePath $adbPath -Arguments @(
            "-s", $DeviceSerial, "shell", "getprop", "ro.product.model"
        )) -join "`n").Trim()
        abiList = ((Invoke-YoloR1External -FilePath $adbPath -Arguments @(
            "-s", $DeviceSerial, "shell", "getprop", "ro.product.cpu.abilist"
        )) -join "`n").Trim()
        apiLevel = [int](((Invoke-YoloR1External -FilePath $adbPath -Arguments @(
            "-s", $DeviceSerial, "shell", "getprop", "ro.build.version.sdk"
        )) -join "`n").Trim())
        fingerprint = ((Invoke-YoloR1External -FilePath $adbPath -Arguments @(
            "-s", $DeviceSerial, "shell", "getprop", "ro.build.fingerprint"
        )) -join "`n").Trim()
    }
    Assert-YoloR1Condition ([string]$deviceEnvironment.abiList -match '(^|,)arm64-v8a(,|$)') `
        "Device $DeviceSerial is not an arm64-v8a target"
    Assert-YoloR1Condition (
        -not (Test-YoloR1PackageInstalled -AdbPath $adbPath -Serial $DeviceSerial `
            -PackageName $providerApplicationId)
    ) "Provider package already exists; refusing to overwrite an unsnapshotted installation"
    Assert-YoloR1Condition (
        -not (Test-YoloR1PackageInstalled -AdbPath $adbPath -Serial $DeviceSerial `
            -PackageName $testApplicationId)
    ) "Test package already exists; refusing to overwrite an unsnapshotted installation"

    $providerInstalledByGate = $false
    $testInstalledByGate = $false
    try {
        [void](Invoke-YoloR1External -FilePath $adbPath -Arguments @(
            "-s", $DeviceSerial, "install", $providerApkPath
        ))
        $providerInstalledByGate = $true
        [void](Invoke-YoloR1External -FilePath $adbPath -Arguments @(
            "-s", $DeviceSerial, "install", "-t", $testApkPath
        ))
        $testInstalledByGate = $true
        $instrumentationOutput = Invoke-YoloR1External -FilePath $adbPath -Arguments @(
            "-s", $DeviceSerial,
            "shell", "am", "instrument", "-w", "-r",
            "-e", "class", $smokeTestClass,
            "$testApplicationId/$testRunner"
        )
        $instrumentationText = $instrumentationOutput -join "`n"
        Assert-YoloR1Condition ($instrumentationText -match 'OK\s+\(1\s+test\)') `
            "Instrumentation did not report exactly one passing smoke test"
        Assert-YoloR1Condition (
            $instrumentationText -notmatch 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed'
        ) "Instrumentation output contains a failure marker"

        $nativeResultText = (Invoke-YoloR1External -FilePath $adbPath -Arguments @(
            "-s", $DeviceSerial, "exec-out", "run-as", $providerApplicationId,
            "cat", "files/r1-native-result.json"
        )) -join "`n"
        try {
            $nativeResult = $nativeResultText | ConvertFrom-Json
        } catch {
            throw "Native smoke result is not valid JSON"
        }
        Assert-YoloR1Condition ([string]$nativeResult.backendVersion -eq "ncnn/1.0.20260526") `
            "Native smoke reported an unexpected NCNN version"
        Assert-YoloR1Condition (@($nativeResult.detections).Count -gt 0) `
            "Native smoke returned no detections"
        $nativeStatus = "PASS"
    } finally {
        $cleanupErrors = New-Object System.Collections.Generic.List[string]
        if ($testInstalledByGate) {
            try {
                [void](Invoke-YoloR1External -FilePath $adbPath -Arguments @(
                    "-s", $DeviceSerial, "uninstall", $testApplicationId
                ))
            } catch {
                $cleanupErrors.Add($_.Exception.Message)
            }
        }
        if ($providerInstalledByGate) {
            try {
                [void](Invoke-YoloR1External -FilePath $adbPath -Arguments @(
                    "-s", $DeviceSerial, "uninstall", $providerApplicationId
                ))
            } catch {
                $cleanupErrors.Add($_.Exception.Message)
            }
        }
        if ($cleanupErrors.Count -gt 0) {
            throw "R1 device cleanup failed: $($cleanupErrors -join '; ')"
        }
    }
    Assert-YoloR1Condition (
        -not (Test-YoloR1PackageInstalled -AdbPath $adbPath -Serial $DeviceSerial `
            -PackageName $providerApplicationId)
    ) "Provider package remained installed after the R1 gate"
    Assert-YoloR1Condition (
        -not (Test-YoloR1PackageInstalled -AdbPath $adbPath -Serial $DeviceSerial `
            -PackageName $testApplicationId)
    ) "Test package remained installed after the R1 gate"
}

$generatedReportPath = Join-Path $repositoryRoot "build\reports\yolo\r1-summary.generated.json"
$generatedReportDirectory = Split-Path -Parent $generatedReportPath
[void](New-Item -ItemType Directory -Force -Path $generatedReportDirectory)
$summary = [ordered]@{
    schemaVersion = 1
    generatedAtUtc = [DateTime]::UtcNow.ToString("o")
    gate = "YOLO_R1"
    r1Build = "PASS"
    r1Native = $nativeStatus
    main = [ordered]@{
        path = $repositoryRoot
        headRevision = $mainHead
        lockedSourceRevision = $sourceRevision
        lockedSourceSnapshotSha256 = $sourceSnapshotSha256
        r0Static = "PASS"
    }
    sibling = [ordered]@{
        path = $siblingRoot
        revision = $siblingRevision
        dirty = $siblingDirty
    }
    locks = [ordered]@{
        protocolAars = [ordered]@{
            path = $protocolLockPath
            sha256 = Get-YoloR1Sha256 -Path $protocolLockPath
        }
        ncnn = [ordered]@{
            path = $ncnnLockPath
            sha256 = Get-YoloR1Sha256 -Path $ncnnLockPath
        }
        fixture = [ordered]@{
            path = $fixtureLockPath
            sha256 = Get-YoloR1Sha256 -Path $fixtureLockPath
        }
    }
    verifiedInputs = [ordered]@{
        protocolAars = $aarEvidence
        ncnnLibrary = $ncnnLibraryEvidence
        ncnnLicense = $ncnnLicenseEvidence
        localFixtures = $fixtureEvidence
        modelManifest = $modelManifestEvidence
        expectedDetections = $expectedDetectionsEvidence
    }
    build = [ordered]@{
        tasks = @($gradleArguments | Where-Object { $_ -like ':*' })
        providerApk = $providerApkEvidence
        instrumentationApk = $testApkEvidence
        requiredNativeEntry = $requiredNativeEntry
        forbiddenPackagedFiles = $forbiddenPackagedFiles
    }
    device = [ordered]@{
        serial = if ([string]::IsNullOrWhiteSpace($DeviceSerial)) { $null } else { $DeviceSerial }
        smokeTestClass = $smokeTestClass
        status = $nativeStatus
        environment = $deviceEnvironment
        result = $nativeResult
    }
}
$summaryJson = $summary | ConvertTo-Json -Depth 10
$utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($generatedReportPath, $summaryJson + "`n", $utf8WithoutBom)

Write-Output "YOLO_R1_BUILD=PASS"
Write-Output "YOLO_R1_NATIVE=$nativeStatus"
Write-Output "YOLO_R1_GENERATED_SUMMARY=$generatedReportPath"
