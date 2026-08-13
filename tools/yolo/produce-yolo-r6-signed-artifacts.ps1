[CmdletBinding(DefaultParameterSetName = "Build")]
param(
    [Parameter(Mandatory = $true, ParameterSetName = "Build")]
    [ValidateNotNullOrEmpty()]
    [string] $SignPropertiesSource,

    [Parameter(Mandatory = $true, ParameterSetName = "Build")]
    [ValidateNotNullOrEmpty()]
    [string] $KeyStoreSource,

    [Parameter(Mandatory = $true, ParameterSetName = "Build")]
    [ValidateNotNullOrEmpty()]
    [string] $ExpectedMainCommit,

    [Parameter(Mandatory = $true, ParameterSetName = "Build")]
    [ValidateNotNullOrEmpty()]
    [string] $ExpectedProviderCommit,

    [Parameter(Mandatory = $true, ParameterSetName = "Build")]
    [ValidateNotNullOrEmpty()]
    [string] $OfficialProductionSignerSha256,

    [Parameter(ParameterSetName = "Build")]
    [string] $SiblingPath = "D:\idea-projects\AutoJs6-Plugin-Yolo-NCNN",

    [Parameter(ParameterSetName = "Build")]
    [ValidateRange(0, 3600)]
    [int] $GradleClientWaitSeconds = 900,

    [Parameter(Mandatory = $true, ParameterSetName = "SelfTest")]
    [switch] $SelfTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
if (Test-Path variable:PSNativeCommandUseErrorActionPreference) {
    $PSNativeCommandUseErrorActionPreference = $false
}

$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$reportRoot = Join-Path $repositoryRoot "build\reports\yolo"
$receiptPath = Join-Path $reportRoot "r6-controlled-signed-build.generated.json"
$artifactRoot = Join-Path $reportRoot "r6-signed-artifacts"
$mainCloneStagingPath = Join-Path $reportRoot "r6-main-build-clone.staging"
$producerName = "yolo-r6-controlled-signed-build"
$expectedHostVersionCode = 5275L
$expectedHostVersionName = "6.8.0"
$expectedProviderVersionCode = 2L
$expectedProviderVersionName = "0.1.0"
$hostPackage = "org.autojs.autojs6"
$providerPackage = "io.github.supermonster003.autojs6.plugin.yolo.ncnn"
$expectedProviderRuntimeComponent = "$providerPackage/$providerPackage.provider.YoloProviderService"
$expectedProviderContract = [ordered]@{
    runtimeComponent = $expectedProviderRuntimeComponent
    protocolApiMin = "1.0"
    protocolApiMax = "1.0"
    backend = "ncnn"
    task = "detect"
    decoder = "ultralytics-detect"
    supportedAbis = @("arm64-v8a")
}
$officialSignerSource = "app/src/main/java/org/autojs/autojs/core/plugin/center/PluginTrustManager.kt"
$protocolPaths = @(
    "plugin-api/common-plugin-api",
    "plugin-api/protocol-wire-api",
    "plugin-api/yolo-api"
)

function Assert-R6ProducerCondition {
    param(
        [Parameter(Mandatory = $true)][bool] $Condition,
        [Parameter(Mandatory = $true)][string] $Message
    )
    if (-not $Condition) { throw $Message }
}

function Get-R6ProducerRequiredFile {
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

function Get-R6ProducerRequiredDirectory {
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

function Get-R6ProducerSha256 {
    param([Parameter(Mandatory = $true)][string] $Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Invoke-R6ProducerGitText {
    param(
        [Parameter(Mandatory = $true)][string] $Repository,
        [Parameter(Mandatory = $true)][string[]] $Arguments
    )
    $output = @(& git -C $Repository @Arguments 2>&1 | ForEach-Object { [string]$_ })
    if ($LASTEXITCODE -ne 0) { throw "Git command failed in $Repository`: git $($Arguments -join ' ')" }
    return ($output -join "`n").Trim()
}

function Get-R6ProducerGitSnapshot {
    param(
        [Parameter(Mandatory = $true)][string] $Repository,
        [Parameter(Mandatory = $true)][string] $ExpectedCommit,
        [Parameter(Mandatory = $true)][string] $Label
    )
    $expected = $ExpectedCommit.Trim().ToLowerInvariant()
    Assert-R6ProducerCondition ($expected -cmatch '^[0-9a-f]{40}$') "$Label expected commit must be a full Git object ID"
    $revision = Invoke-R6ProducerGitText -Repository $Repository -Arguments @("rev-parse", "HEAD")
    $status = Invoke-R6ProducerGitText -Repository $Repository -Arguments @("status", "--porcelain=v1", "--untracked-files=all")
    Assert-R6ProducerCondition ($revision -ceq $expected) "$Label HEAD differs from its expected commit"
    Assert-R6ProducerCondition ([string]::IsNullOrEmpty($status)) "$Label repository must be clean, including untracked files"
    return [pscustomobject][ordered]@{
        repository = [System.IO.Path]::GetFullPath($Repository)
        revision = $revision
        clean = $true
        status = @()
    }
}

function Read-R6ProducerProperties {
    param([Parameter(Mandatory = $true)][string] $Path)
    $properties = [ordered]@{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith("#") -or $line.TrimStart().StartsWith("!")) {
            continue
        }
        $separator = $line.IndexOf('=')
        Assert-R6ProducerCondition ($separator -gt 0) "Signing properties contains an unsupported line"
        $key = $line.Substring(0, $separator).Trim()
        $value = $line.Substring($separator + 1).Trim()
        Assert-R6ProducerCondition (-not $properties.Contains($key)) "Signing properties contains a duplicate key"
        Assert-R6ProducerCondition (-not [string]::IsNullOrWhiteSpace($value)) "Signing properties contains an empty value"
        $properties[$key] = $value
    }
    $expectedKeys = @("keyAlias", "keyPassword", "storeFile", "storePassword")
    $actualKeys = @($properties.Keys | Sort-Object)
    Assert-R6ProducerCondition (($actualKeys -join "`n") -ceq (($expectedKeys | Sort-Object) -join "`n")) (
        "Signing properties must contain exactly the four supported keys"
    )
    return $properties
}

function Get-R6ProducerPinnedOfficialSigner {
    $path = Get-R6ProducerRequiredFile -Path (Join-Path $repositoryRoot $officialSignerSource) -Label "official signer source"
    $text = Get-Content -Raw -LiteralPath $path
    $match = [regex]::Match($text, 'OFFICIAL_SHA_256\s*=\s*setOf\(\s*"([0-9a-f]{64})"\s*\)')
    Assert-R6ProducerCondition $match.Success "Host source must pin exactly one literal official signer"
    return [pscustomobject][ordered]@{
        path = $path
        sha256 = Get-R6ProducerSha256 -Path $path
        certificateSha256 = $match.Groups[1].Value.ToLowerInvariant()
    }
}

function Resolve-R6ProducerAndroidTools {
    param([Parameter(Mandatory = $true)][string[]] $RepositoryRoots)
    $sdkRoots = [System.Collections.Generic.List[string]]::new()
    foreach ($root in $RepositoryRoots) {
        $localProperties = Join-Path $root "local.properties"
        if (Test-Path -LiteralPath $localProperties -PathType Leaf) {
            $line = Get-Content -LiteralPath $localProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
            if ($null -ne $line) {
                $value = ($line -replace '^sdk\.dir=', '') -replace '\\:', ':' -replace '\\\\', '\'
                if (-not [string]::IsNullOrWhiteSpace($value)) { $sdkRoots.Add($value.Trim()) }
            }
        }
    }
    foreach ($name in @("ANDROID_SDK_ROOT", "ANDROID_HOME")) {
        $value = [Environment]::GetEnvironmentVariable($name)
        if (-not [string]::IsNullOrWhiteSpace($value)) { $sdkRoots.Add($value.Trim()) }
    }
    foreach ($sdkRoot in @($sdkRoots | Select-Object -Unique)) {
        $buildToolsRoot = Join-Path $sdkRoot "build-tools"
        if (-not (Test-Path -LiteralPath $buildToolsRoot -PathType Container)) { continue }
        $directories = @(Get-ChildItem -LiteralPath $buildToolsRoot | Where-Object { $_.PSIsContainer } |
            Sort-Object { try { [version]($_.Name -replace '-.*$', '') } catch { [version]'0.0' } } -Descending)
        foreach ($directory in $directories) {
            $apkSigner = Join-Path $directory.FullName "apksigner.bat"
            $aapt2 = Join-Path $directory.FullName "aapt2.exe"
            if ((Test-Path -LiteralPath $apkSigner -PathType Leaf) -and (Test-Path -LiteralPath $aapt2 -PathType Leaf)) {
                return [pscustomobject][ordered]@{ apkSigner = $apkSigner; aapt2 = $aapt2 }
            }
        }
    }
    throw "Unable to resolve apksigner and aapt2 from an Android SDK"
}

function Invoke-R6ProducerCapture {
    param(
        [Parameter(Mandatory = $true)][string] $FilePath,
        [Parameter(Mandatory = $true)][string[]] $Arguments,
        [Parameter(Mandatory = $true)][string] $Label
    )
    $output = @(& $FilePath @Arguments 2>&1 | ForEach-Object { [string]$_ })
    if ($LASTEXITCODE -ne 0) { throw "$Label failed"
    }
    return $output
}

function Get-R6ProducerApkSigner {
    param(
        [Parameter(Mandatory = $true)][string] $ApkSigner,
        [Parameter(Mandatory = $true)][string] $Apk,
        [Parameter(Mandatory = $true)][string] $Label
    )
    $output = Invoke-R6ProducerCapture -FilePath $ApkSigner -Arguments @("verify", "--verbose", "--print-certs", $Apk) -Label "$Label signature verification"
    $text = $output -join "`n"
    $count = [regex]::Match($text, '(?im)^Number of signers:\s*([0-9]+)\s*$')
    Assert-R6ProducerCondition ($count.Success -and [int]$count.Groups[1].Value -eq 1) "$Label must have exactly one signer"
    $digests = @([regex]::Matches($text, '(?im)certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$') |
        ForEach-Object { $_.Groups[1].Value.ToLowerInvariant() } | Select-Object -Unique)
    Assert-R6ProducerCondition ($digests.Count -eq 1) "$Label must expose exactly one signer certificate SHA-256"
    return $digests[0]
}

function Get-R6ProducerApkIdentity {
    param(
        [Parameter(Mandatory = $true)][string] $Aapt2,
        [Parameter(Mandatory = $true)][string] $Apk,
        [Parameter(Mandatory = $true)][string] $Label
    )
    $lines = Invoke-R6ProducerCapture -FilePath $Aapt2 -Arguments @("dump", "badging", $Apk) -Label "$Label badging inspection"
    $text = $lines -join "`n"
    $package = [regex]::Match($text, "(?m)^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']*)'")
    Assert-R6ProducerCondition $package.Success "$Label package identity is missing"
    $instrumentation = [regex]::Match($text, "(?m)^instrumentation: name='([^']+)' targetPackage='([^']+)'")
    return [pscustomobject][ordered]@{
        packageName = $package.Groups[1].Value
        versionCode = [long]$package.Groups[2].Value
        versionName = $package.Groups[3].Value
        debuggable = $text -match "(?m)^application-debuggable\s*$"
        instrumentation = if ($instrumentation.Success) {
            [ordered]@{ runner = $instrumentation.Groups[1].Value; targetPackage = $instrumentation.Groups[2].Value }
        } else { $null }
    }
}

function Get-R6ProducerApkVcsRevision {
    param(
        [Parameter(Mandatory = $true)][string] $Apk,
        [Parameter(Mandatory = $true)][string] $Label
    )
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($Apk)
    try {
        $entries = @($archive.Entries | Where-Object { $_.FullName -ceq "META-INF/version-control-info.textproto" })
        Assert-R6ProducerCondition ($entries.Count -eq 1) "$Label must contain one Gradle VCS metadata entry"
        $stream = $entries[0].Open()
        $reader = [System.IO.StreamReader]::new($stream)
        try { $text = $reader.ReadToEnd() } finally { $reader.Dispose(); $stream.Dispose() }
    } finally {
        $archive.Dispose()
    }
    $projectBlocks = @([regex]::Matches($text, '(?ms)repositories\s*\{(?<body>.*?)\}') | Where-Object {
        $_.Groups['body'].Value -match '(?m)^\s*local_root_path:\s*"\$PROJECT_DIR"\s*$'
    })
    Assert-R6ProducerCondition ($projectBlocks.Count -eq 1) "$Label VCS metadata must identify exactly one PROJECT_DIR repository"
    $revision = [regex]::Match($projectBlocks[0].Groups['body'].Value, '(?m)^\s*revision:\s*"([0-9a-f]{40})"\s*$')
    Assert-R6ProducerCondition $revision.Success "$Label PROJECT_DIR VCS metadata has no Git revision"
    return $revision.Groups[1].Value
}

function Get-R6ProducerApkPayloadBoundary {
    param(
        [Parameter(Mandatory = $true)][string] $Apk,
        [Parameter(Mandatory = $true)][ValidateSet("Host", "Provider", "Instrumentation")][string] $Kind
    )
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($Apk)
    try { $entries = @($archive.Entries | Where-Object { -not [string]::IsNullOrEmpty($_.Name) } | ForEach-Object { $_.FullName.Replace('\', '/') }) }
    finally { $archive.Dispose() }
    $modelEntries = @($entries | Where-Object { [IO.Path]::GetFileName($_).ToLowerInvariant() -in @(
        "model.json", "model-manifest-v1.json", "model.ncnn.param", "model.ncnn.bin", "yolo11n.pt", "bus.jpg", "synthetic-heldout.png"
    ) })
    Assert-R6ProducerCondition ($modelEntries.Count -eq 0) "$Kind APK contains a known YOLO model or fixture payload"
    $ncnnEntries = @($entries | Where-Object { $_ -match '(?i)(^|/)(libautojs_yolo|libncnn)[^/]*\.so$' })
    if ($Kind -ceq "Provider") {
        Assert-R6ProducerCondition ($ncnnEntries.Count -eq 1 -and $ncnnEntries[0] -ceq "lib/arm64-v8a/libautojs_yolo.so") (
            "Provider APK native payload must be exactly arm64-v8a/libautojs_yolo.so"
        )
    } else {
        Assert-R6ProducerCondition ($ncnnEntries.Count -eq 0) "$Kind APK contains YOLO/NCNN native code"
    }
    return [pscustomobject][ordered]@{ knownModelEntries = @(); yoloNcnnNativeEntries = $ncnnEntries }
}

function Get-R6ProducerArtifact {
    param(
        [Parameter(Mandatory = $true)][string] $Path,
        [Parameter(Mandatory = $true)][DateTime] $BuildStartedUtc,
        [Parameter(Mandatory = $true)][string] $Label
    )
    $fullPath = Get-R6ProducerRequiredFile -Path $Path -Label $Label
    $item = Get-Item -LiteralPath $fullPath
    Assert-R6ProducerCondition ($item.LastWriteTimeUtc -ge $BuildStartedUtc) "$Label is not fresh relative to its controlled build"
    return [pscustomobject][ordered]@{
        path = $fullPath
        length = $item.Length
        sha256 = Get-R6ProducerSha256 -Path $fullPath
        lastWriteUtc = $item.LastWriteTimeUtc.ToString("o")
        freshAfterBuildStart = $true
    }
}

function Resolve-R6ProducerApkFromMetadata {
    param(
        [Parameter(Mandatory = $true)][string] $MetadataPath,
        [Parameter(Mandatory = $true)][string] $Label,
        [string] $Abi = ""
    )
    $path = Get-R6ProducerRequiredFile -Path $MetadataPath -Label "$Label output metadata"
    $metadata = Get-Content -Raw -LiteralPath $path | ConvertFrom-Json
    $elements = @($metadata.elements)
    if ([string]::IsNullOrEmpty($Abi)) {
        Assert-R6ProducerCondition ($elements.Count -eq 1) "$Label output metadata must contain exactly one element"
        $selected = $elements[0]
    } else {
        $matches = @($elements | Where-Object {
            @($_.filters | Where-Object { [string]$_.filterType -ceq "ABI" -and [string]$_.value -ceq $Abi }).Count -eq 1
        })
        Assert-R6ProducerCondition ($matches.Count -eq 1) "$Label output metadata must contain exactly one $Abi element"
        $selected = $matches[0]
    }
    return Get-R6ProducerRequiredFile -Path (Join-Path (Split-Path -Parent $path) ([string]$selected.outputFile)) -Label "$Label APK"
}

function Assert-R6ProducerIgnoredSecretTarget {
    param(
        [Parameter(Mandatory = $true)][string] $Repository,
        [Parameter(Mandatory = $true)][string] $RelativePath
    )
    $target = [System.IO.Path]::GetFullPath((Join-Path $Repository $RelativePath))
    Assert-R6ProducerCondition (-not (Test-Path -LiteralPath $target)) "Refusing to overwrite an existing signing target: $target"
    & git -C $Repository check-ignore -q -- $RelativePath
    Assert-R6ProducerCondition ($LASTEXITCODE -eq 0) "Temporary signing target is not ignored by Git: $RelativePath"
    return $target
}

function Install-R6ProducerSigningMaterial {
    param(
        [Parameter(Mandatory = $true)][string] $Repository,
        [Parameter(Mandatory = $true)][string] $PropertiesSource,
        [Parameter(Mandatory = $true)][string] $KeyStoreSource,
        [Parameter(Mandatory = $true)][string] $KeyStoreLeaf
    )
    $propertiesTarget = Assert-R6ProducerIgnoredSecretTarget -Repository $Repository -RelativePath "sign.properties"
    $keyStoreRelative = "app/$KeyStoreLeaf"
    $keyStoreTarget = Assert-R6ProducerIgnoredSecretTarget -Repository $Repository -RelativePath $keyStoreRelative
    try {
        Copy-Item -LiteralPath $PropertiesSource -Destination $propertiesTarget
        Copy-Item -LiteralPath $KeyStoreSource -Destination $keyStoreTarget
        Assert-R6ProducerCondition ((Get-R6ProducerSha256 $keyStoreTarget) -ceq (Get-R6ProducerSha256 $KeyStoreSource)) (
            "Temporary keystore copy differs from its authorized source"
        )
    } catch {
        $copyError = $_.Exception.Message
        $cleanupErrors = [System.Collections.Generic.List[string]]::new()
        foreach ($path in @($propertiesTarget, $keyStoreTarget)) {
            try { if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Force } } catch { $cleanupErrors.Add($_.Exception.Message) }
            if (Test-Path -LiteralPath $path) { $cleanupErrors.Add("Temporary signing material remains after copy failure: $path") }
        }
        if ($cleanupErrors.Count -gt 0) { throw "Signing material copy failed: $copyError; cleanup failed: $($cleanupErrors -join '; ')" }
        throw "Signing material copy failed: $copyError"
    }
    return [pscustomobject][ordered]@{ properties = $propertiesTarget; keyStore = $keyStoreTarget }
}

function Remove-R6ProducerSigningMaterial {
    param([AllowNull()][object] $Targets)
    if ($null -eq $Targets) { return }
    $errors = [System.Collections.Generic.List[string]]::new()
    foreach ($path in @([string]$Targets.properties, [string]$Targets.keyStore)) {
        try {
            if (-not [string]::IsNullOrWhiteSpace($path) -and (Test-Path -LiteralPath $path)) { Remove-Item -LiteralPath $path -Force }
        } catch { $errors.Add($_.Exception.Message) }
        if (-not [string]::IsNullOrWhiteSpace($path) -and (Test-Path -LiteralPath $path)) { $errors.Add("Temporary signing material was not deleted: $path") }
    }
    if ($errors.Count -gt 0) { throw ($errors -join '; ') }
}

function Wait-R6ProducerGradleWindow {
    param([Parameter(Mandatory = $true)][int] $TimeoutSeconds)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $clients = @()
        try {
            $clients = @(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
                Where-Object {
                    $command = [string]$_.CommandLine
                    $command -match '(?i)(GradleWrapperMain|org\.gradle\.launcher\.GradleMain|gradle-wrapper\.jar)' -and
                        $command -notmatch '(?i)GradleDaemon'
                })
        } catch {
            throw "Unable to audit external Gradle clients before controlled build"
        }
        if ($clients.Count -eq 0) { return }
        if ([DateTime]::UtcNow -ge $deadline) { throw "Timed out waiting for $($clients.Count) external Gradle client(s) to exit" }
        Start-Sleep -Seconds 5
    } while ($true)
}

function Stop-R6ProducerTimedOutProcess {
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

function Invoke-R6ProducerGradle {
    param(
        [Parameter(Mandatory = $true)][string] $Repository,
        [Parameter(Mandatory = $true)][string[]] $Arguments,
        [Parameter(Mandatory = $true)][int] $WaitSeconds,
        [Parameter(Mandatory = $true)][ValidateRange(60, 14400)][int] $BuildTimeoutSeconds,
        [Parameter(Mandatory = $true)][string] $Label
    )
    Wait-R6ProducerGradleWindow -TimeoutSeconds $WaitSeconds
    $wrapper = Get-R6ProducerRequiredFile -Path (Join-Path $Repository "gradlew.bat") -Label "$Label Gradle wrapper"
    Write-Output "YOLO_R6_CONTROLLED_BUILD_STEP=$Label"
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $process.StartInfo.FileName = (Get-Command cmd.exe -ErrorAction Stop).Source
    $process.StartInfo.WorkingDirectory = $Repository
    $process.StartInfo.UseShellExecute = $false
    foreach ($argument in @("/d", "/s", "/c", $wrapper) + $Arguments) { [void]$process.StartInfo.ArgumentList.Add($argument) }
    try {
        Assert-R6ProducerCondition ($process.Start()) "$Label Gradle process did not start"
        if (-not $process.WaitForExit($BuildTimeoutSeconds * 1000)) {
            Stop-R6ProducerTimedOutProcess -Process $process -Label "$Label Gradle build"
            throw "$Label Gradle build exceeded its $BuildTimeoutSeconds-second wall-time limit"
        }
        Assert-R6ProducerCondition ($process.ExitCode -eq 0) "$Label Gradle build failed with exit code $($process.ExitCode)"
    } finally {
        $process.Dispose()
    }
}

function Invoke-R6ProducerBoundedPowerShell {
    param(
        [Parameter(Mandatory = $true)][string] $ScriptPath,
        [Parameter(Mandatory = $true)][string[]] $Arguments,
        [Parameter(Mandatory = $true)][ValidateRange(60, 14400)][int] $TimeoutSeconds,
        [Parameter(Mandatory = $true)][string] $WorkingDirectory,
        [Parameter(Mandatory = $true)][string] $Label
    )
    $pwsh = (Get-Command pwsh.exe -ErrorAction Stop).Source
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $process.StartInfo.FileName = $pwsh
    $process.StartInfo.WorkingDirectory = $WorkingDirectory
    $process.StartInfo.UseShellExecute = $false
    foreach ($argument in @("-NoLogo", "-NoProfile", "-NonInteractive", "-File", $ScriptPath) + $Arguments) {
        [void]$process.StartInfo.ArgumentList.Add($argument)
    }
    try {
        Assert-R6ProducerCondition ($process.Start()) "$Label did not start"
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
            Stop-R6ProducerTimedOutProcess -Process $process -Label $Label
            throw "$Label exceeded its $TimeoutSeconds-second wall-time limit"
        }
        Assert-R6ProducerCondition ($process.ExitCode -eq 0) "$Label failed with exit code $($process.ExitCode)"
    } finally {
        $process.Dispose()
    }
}

function Invoke-R6ProducerBoundedExternal {
    param(
        [Parameter(Mandatory = $true)][string] $FilePath,
        [Parameter(Mandatory = $true)][string[]] $Arguments,
        [Parameter(Mandatory = $true)][ValidateRange(5, 14400)][int] $TimeoutSeconds,
        [Parameter(Mandatory = $true)][string] $WorkingDirectory,
        [Parameter(Mandatory = $true)][string] $Label
    )
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $process.StartInfo.FileName = $FilePath
    $process.StartInfo.WorkingDirectory = $WorkingDirectory
    $process.StartInfo.UseShellExecute = $false
    foreach ($argument in $Arguments) { [void]$process.StartInfo.ArgumentList.Add($argument) }
    try {
        Assert-R6ProducerCondition ($process.Start()) "$Label did not start"
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
            Stop-R6ProducerTimedOutProcess -Process $process -Label $Label
            throw "$Label exceeded its $TimeoutSeconds-second wall-time limit"
        }
        Assert-R6ProducerCondition ($process.ExitCode -eq 0) "$Label failed with exit code $($process.ExitCode)"
    } finally { $process.Dispose() }
}

function New-R6ProducerOrdinaryMainClone {
    param(
        [Parameter(Mandatory = $true)][string] $ExpectedCommit
    )
    $cloneRoot = Assert-R6ProducerReportChildPath -Path (Join-Path $reportRoot "r6-main-build-clone.staging")
    if (Test-Path -LiteralPath $cloneRoot) { Remove-Item -LiteralPath $cloneRoot -Recurse -Force }
    try {
        Invoke-R6ProducerBoundedExternal `
            -FilePath (Get-Command git.exe -ErrorAction Stop).Source `
            -Arguments @("clone", "--no-hardlinks", "--no-checkout", $repositoryRoot, $cloneRoot) `
            -TimeoutSeconds 600 `
            -WorkingDirectory $repositoryRoot `
            -Label "local ordinary Host clone"
        Invoke-R6ProducerBoundedExternal `
            -FilePath (Get-Command git.exe -ErrorAction Stop).Source `
            -Arguments @("checkout", "--detach", $ExpectedCommit) `
            -TimeoutSeconds 300 `
            -WorkingDirectory $cloneRoot `
            -Label "ordinary Host clone checkout"
        $snapshot = Get-R6ProducerGitSnapshot -Repository $cloneRoot -ExpectedCommit $ExpectedCommit -Label "ordinary Host build clone"
        Assert-R6ProducerCondition (Test-Path -LiteralPath (Join-Path $cloneRoot ".git") -PathType Container) "Host build clone must contain an ordinary .git directory"
        $localProperties = Join-Path $repositoryRoot "local.properties"
        if (Test-Path -LiteralPath $localProperties -PathType Leaf) {
            $cloneLocalProperties = Join-Path $cloneRoot "local.properties"
            Copy-Item -LiteralPath $localProperties -Destination $cloneLocalProperties
            & git -C $cloneRoot check-ignore -q -- local.properties
            Assert-R6ProducerCondition ($LASTEXITCODE -eq 0) "Clone local.properties is not ignored"
        }
        return [pscustomobject][ordered]@{ path = $cloneRoot; snapshot = $snapshot; ordinaryGitDirectory = $true }
    } catch {
        $cloneError = $_.Exception.Message
        try { if (Test-Path -LiteralPath $cloneRoot) { Remove-Item -LiteralPath $cloneRoot -Recurse -Force } } catch { throw "Host clone setup failed: $cloneError; clone cleanup failed: $($_.Exception.Message)" }
        Assert-R6ProducerCondition (-not (Test-Path -LiteralPath $cloneRoot)) "Host clone setup failed and its partial clone remains"
        throw "Host clone setup failed: $cloneError"
    }
}

function Get-R6ProducerProtocolBoundary {
    param(
        [Parameter(Mandatory = $true)][string] $ProviderRoot,
        [Parameter(Mandatory = $true)][string] $MainCommit
    )
    $lockPath = Get-R6ProducerRequiredFile -Path (Join-Path $ProviderRoot "libs/protocol-aars.lock.json") -Label "Provider protocol AAR lock"
    $lock = Get-Content -Raw -LiteralPath $lockPath | ConvertFrom-Json
    Assert-R6ProducerCondition ([int]$lock.schemaVersion -eq 1) "Protocol AAR lock schema differs"
    $sourceRevision = [string]$lock.sourceRevision
    Assert-R6ProducerCondition ($sourceRevision -cmatch '^[0-9a-f]{40}$') "Protocol AAR lock source revision is invalid"
    [void](Invoke-R6ProducerGitText -Repository $repositoryRoot -Arguments @("merge-base", "--is-ancestor", $sourceRevision, $MainCommit))
    $changes = Invoke-R6ProducerGitText -Repository $repositoryRoot -Arguments (@("diff", "--name-only", $sourceRevision, $MainCommit, "--") + $protocolPaths)
    Assert-R6ProducerCondition ([string]::IsNullOrEmpty($changes)) "Protocol sources changed after the Provider AAR handoff"
    $artifacts = @()
    foreach ($entry in @($lock.artifacts)) {
        $path = Get-R6ProducerRequiredFile -Path (Join-Path $ProviderRoot "libs/$([string]$entry.name)") -Label "locked protocol AAR"
        Assert-R6ProducerCondition ((Get-R6ProducerSha256 $path) -ceq ([string]$entry.sha256).ToLowerInvariant()) "Protocol AAR SHA-256 differs from lock"
        Assert-R6ProducerCondition ((Get-Item -LiteralPath $path).Length -eq [long]$entry.length) "Protocol AAR length differs from lock"
        $artifacts += [ordered]@{ name = [string]$entry.name; length = [long]$entry.length; sha256 = ([string]$entry.sha256).ToLowerInvariant() }
    }
    Assert-R6ProducerCondition ($artifacts.Count -eq 3) "Protocol AAR lock must contain exactly three artifacts"
    return [pscustomobject][ordered]@{
        lockPath = $lockPath
        lockSha256 = Get-R6ProducerSha256 $lockPath
        sourceRevision = $sourceRevision
        currentMainRevision = $MainCommit
        sourceChangedSinceHandoff = $false
        handoffRequired = $false
        artifacts = $artifacts
    }
}

function Write-R6ProducerJson {
    param([Parameter(Mandatory = $true)][object] $Value)
    $null = New-Item -ItemType Directory -Path (Split-Path -Parent $receiptPath) -Force
    $json = $Value | ConvertTo-Json -Depth 20
    [System.IO.File]::WriteAllText($receiptPath, "$json`n", [System.Text.UTF8Encoding]::new($false))
}

function Assert-R6ProducerReportChildPath {
    param([Parameter(Mandatory = $true)][string] $Path)
    $reports = [System.IO.Path]::GetFullPath($reportRoot).TrimEnd('\') + '\'
    $candidate = [System.IO.Path]::GetFullPath($Path)
    Assert-R6ProducerCondition ($candidate.StartsWith($reports, [System.StringComparison]::OrdinalIgnoreCase)) (
        "Controlled producer output is outside the fixed report directory: $candidate"
    )
    return $candidate
}

function Remove-R6ProducerFixedOutput {
    param(
        [Parameter(Mandatory = $true)][string] $Path,
        [Parameter(Mandatory = $true)][string] $Label,
        [switch] $Recurse
    )
    $errors = [System.Collections.Generic.List[string]]::new()
    $safePath = $null
    try {
        $safePath = Assert-R6ProducerReportChildPath -Path $Path
        if (Test-Path -LiteralPath $safePath) {
            if ($Recurse) {
                Remove-Item -LiteralPath $safePath -Recurse -Force
            } else {
                Remove-Item -LiteralPath $safePath -Force
            }
        }
    } catch {
        $errors.Add("$Label removal failed: $($_.Exception.Message)")
    }
    if ($null -eq $safePath) {
        $errors.Add("$Label absence could not be verified because its fixed path was not admitted")
    } else {
        try {
            if (Test-Path -LiteralPath $safePath) { $errors.Add("$Label remains after cleanup") }
        } catch {
            $errors.Add("$Label absence check failed: $($_.Exception.Message)")
        }
    }
    return @($errors)
}

if ($SelfTest) {
    $temporary = Join-Path ([System.IO.Path]::GetTempPath()) ("yolo-r6-producer-selftest-{0}.properties" -f [Guid]::NewGuid().ToString("N"))
    try {
        [System.IO.File]::WriteAllText($temporary, "storeFile=release.jks`nkeyAlias=value`nkeyPassword=value`nstorePassword=value`n")
        $parsed = Read-R6ProducerProperties -Path $temporary
        Assert-R6ProducerCondition ([string]$parsed.storeFile -ceq "release.jks") "Signing properties parser self-test failed"
        $failed = $false
        [System.IO.File]::WriteAllText($temporary, "storeFile=release.jks`nkeyAlias=value`nkeyPassword=value`n")
        try { $null = Read-R6ProducerProperties -Path $temporary } catch { $failed = $true }
        Assert-R6ProducerCondition $failed "Signing properties parser accepted a missing required property"
        $pinned = Get-R6ProducerPinnedOfficialSigner
        Assert-R6ProducerCondition ($pinned.certificateSha256 -cmatch '^[0-9a-f]{64}$') "Official signer parser self-test failed"
        $temporaryBatch = Join-Path ([System.IO.Path]::GetTempPath()) ("yolo-r6-producer-selftest-{0}.cmd" -f [Guid]::NewGuid().ToString("N"))
        [System.IO.File]::WriteAllText($temporaryBatch, "@exit /b 0`r`n", [System.Text.Encoding]::ASCII)
        $cmd = (Get-Command cmd.exe -ErrorAction Stop).Source
        $probe = [System.Diagnostics.Process]::new()
        $probe.StartInfo = [System.Diagnostics.ProcessStartInfo]::new()
        $probe.StartInfo.FileName = $cmd
        $probe.StartInfo.UseShellExecute = $false
        foreach ($argument in @("/d", "/s", "/c", $temporaryBatch)) { [void]$probe.StartInfo.ArgumentList.Add($argument) }
        try {
            Assert-R6ProducerCondition ($probe.Start()) "Windows batch host process self-test did not start"
            Assert-R6ProducerCondition ($probe.WaitForExit(5000)) "Windows batch host process self-test timed out"
            Assert-R6ProducerCondition ($probe.ExitCode -eq 0) "Windows batch host process self-test failed"
        } finally {
            $probe.Dispose()
            if (Test-Path -LiteralPath $temporaryBatch) { Remove-Item -LiteralPath $temporaryBatch -Force }
        }
        $temporaryTimeoutBatch = Join-Path ([System.IO.Path]::GetTempPath()) ("yolo-r6-producer-selftest-{0}.cmd" -f [Guid]::NewGuid().ToString("N"))
        [System.IO.File]::WriteAllText($temporaryTimeoutBatch, "@ping.exe -n 30 127.0.0.1 >nul`r`n", [System.Text.Encoding]::ASCII)
        $timeoutProbe = [System.Diagnostics.Process]::new()
        $timeoutProbe.StartInfo = [System.Diagnostics.ProcessStartInfo]::new()
        $timeoutProbe.StartInfo.FileName = $cmd
        $timeoutProbe.StartInfo.UseShellExecute = $false
        foreach ($argument in @("/d", "/s", "/c", $temporaryTimeoutBatch)) { [void]$timeoutProbe.StartInfo.ArgumentList.Add($argument) }
        try {
            Assert-R6ProducerCondition ($timeoutProbe.Start()) "Windows timeout-process self-test did not start"
            Assert-R6ProducerCondition (-not $timeoutProbe.WaitForExit(100)) "Windows timeout-process self-test exited before timeout"
            Stop-R6ProducerTimedOutProcess -Process $timeoutProbe -Label "producer self-test timeout process"
            Assert-R6ProducerCondition $timeoutProbe.HasExited "Windows timeout-process self-test did not terminate"
        } finally {
            $timeoutProbe.Dispose()
            if (Test-Path -LiteralPath $temporaryTimeoutBatch) { Remove-Item -LiteralPath $temporaryTimeoutBatch -Force }
        }
    } finally {
        if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }
    }
    Write-Output "YOLO_R6_CONTROLLED_PRODUCER_SELF_TEST=PASS"
    return
}

$providerRoot = $null
$signPropertiesPath = $null
$keyStorePath = $null
$keyStoreLeaf = "sm003.jks"
$mainSignTargets = $null
$providerSignTargets = $null
$globalMutex = $null
$mutexOwned = $false
$runStarted = $false
$abandonedMutexRecovered = $false
$providerUnsignedOriginal = $null
$mainBuildClone = $null
$producerSucceeded = $false
$bodyCompleted = $false
$startedAtUtc = [DateTime]::UtcNow

try {
    $globalMutex = [System.Threading.Mutex]::new($false, "Global\AutoJs6ControlledGradleBuild")
    try {
        $mutexOwned = $globalMutex.WaitOne([TimeSpan]::FromSeconds($GradleClientWaitSeconds))
    } catch [System.Threading.AbandonedMutexException] {
        # WaitOne transfers ownership when it reports an abandoned mutex. Treat that as a
        # recoverable prior-process failure and clean the fixed staging paths while holding it.
        $mutexOwned = $true
        $abandonedMutexRecovered = $true
    }
    Assert-R6ProducerCondition $mutexOwned "Timed out waiting for the global controlled Gradle build mutex"
    $runStarted = $true

    $invalidationErrors = [System.Collections.Generic.List[string]]::new()
    foreach ($target in @(
        [pscustomobject]@{ path = $receiptPath; label = "stale controlled receipt"; recurse = $false },
        [pscustomobject]@{ path = $artifactRoot; label = "stale controlled artifact directory"; recurse = $true },
        [pscustomobject]@{ path = $mainCloneStagingPath; label = "stale ordinary Host build clone"; recurse = $true }
    )) {
        foreach ($message in @(Remove-R6ProducerFixedOutput `
            -Path $target.path `
            -Label $target.label `
            -Recurse:([bool]$target.recurse))) {
            $invalidationErrors.Add([string]$message)
        }
    }
    Assert-R6ProducerCondition ($invalidationErrors.Count -eq 0) (
        "Controlled producer could not invalidate every fixed prior output: $($invalidationErrors -join '; ')"
    )
    Wait-R6ProducerGradleWindow -TimeoutSeconds $GradleClientWaitSeconds

    # Only after the fixed previous outputs are invalidated may input validation begin. Thus an
    # early signing/source failure cannot leave a prior PASS-looking receipt attributed to this run.
    $providerRoot = Get-R6ProducerRequiredDirectory -Path $SiblingPath -Label "YOLO Provider repository"
    $signPropertiesPath = Get-R6ProducerRequiredFile -Path $SignPropertiesSource -Label "authorized signing properties"
    $keyStorePath = Get-R6ProducerRequiredFile -Path $KeyStoreSource -Label "authorized keystore"
    $properties = Read-R6ProducerProperties -Path $signPropertiesPath
    $keyStoreLeaf = [IO.Path]::GetFileName([string]$properties.storeFile)
    Assert-R6ProducerCondition (-not [IO.Path]::IsPathRooted([string]$properties.storeFile)) "Signing storeFile must be a relative leaf"
    Assert-R6ProducerCondition ([string]$properties.storeFile -ceq $keyStoreLeaf) "Signing storeFile must not contain a directory"
    Assert-R6ProducerCondition ($keyStoreLeaf -ceq "sm003.jks") "Controlled producer accepts only the explicitly authorized sm003.jks leaf"
    Assert-R6ProducerCondition ($keyStoreLeaf -ceq [IO.Path]::GetFileName($keyStorePath)) "Authorized keystore leaf differs from signing properties"
    $declaredSigner = $OfficialProductionSignerSha256.Trim().ToLowerInvariant()
    Assert-R6ProducerCondition ($declaredSigner -cmatch '^[0-9a-f]{64}$') "Official production signer must be a SHA-256 digest"
    $pinnedSigner = Get-R6ProducerPinnedOfficialSigner
    Assert-R6ProducerCondition ($declaredSigner -ceq $pinnedSigner.certificateSha256) "Declared signer differs from the Host-pinned official signer"

    $mainPre = Get-R6ProducerGitSnapshot -Repository $repositoryRoot -ExpectedCommit $ExpectedMainCommit -Label "Host"
    $providerPre = Get-R6ProducerGitSnapshot -Repository $providerRoot -ExpectedCommit $ExpectedProviderCommit -Label "Provider"
    $protocolBoundary = Get-R6ProducerProtocolBoundary -ProviderRoot $providerRoot -MainCommit $mainPre.revision
    $tools = Resolve-R6ProducerAndroidTools -RepositoryRoots @($repositoryRoot, $providerRoot)
    $producerScript = Get-R6ProducerRequiredFile -Path $PSCommandPath -Label "controlled producer source"

    try {
        $providerSignTargets = Install-R6ProducerSigningMaterial -Repository $providerRoot -PropertiesSource $signPropertiesPath -KeyStoreSource $keyStorePath -KeyStoreLeaf $keyStoreLeaf
        $providerBuildStartedUtc = [DateTime]::UtcNow
        $providerGradleArguments = @(
            "--no-daemon",
            "-Pautojs.gradle.build.number.auto.increment.enabled=false",
            "-Pautojs.gradle.build.time.update.enabled=false",
            ":app:clean",
            ":app:testDebugUnitTest",
            ":app:assembleRelease"
        )
        Invoke-R6ProducerGradle -Repository $providerRoot -Arguments $providerGradleArguments -WaitSeconds $GradleClientWaitSeconds -BuildTimeoutSeconds 1800 -Label "provider-signed-release"
        $providerSignedOutput = Resolve-R6ProducerApkFromMetadata -MetadataPath (Join-Path $providerRoot "app/build/outputs/apk/release/output-metadata.json") -Label "Provider release"
        $providerSignedOutputRecord = Get-R6ProducerArtifact -Path $providerSignedOutput -BuildStartedUtc $providerBuildStartedUtc -Label "Provider signed release"
        $null = New-Item -ItemType Directory -Path $artifactRoot -Force
        $providerArtifactPath = Join-Path $artifactRoot "AutoJs6-Plugin-Yolo-NCNN-v0.1.0-2-arm64-v8a-release.apk"
        if (Test-Path -LiteralPath $providerArtifactPath) { Remove-Item -LiteralPath $providerArtifactPath -Force }
        Copy-Item -LiteralPath $providerSignedOutput -Destination $providerArtifactPath
    } finally {
        Remove-R6ProducerSigningMaterial -Targets $providerSignTargets
        $providerSignTargets = $null
    }

    # Rebuild the complete unsigned admission bundle after preserving the signed output. This
    # keeps the source receipt, tests, mappings, resources and fixed Gradle output mutually fresh.
    $providerSourceGate = Get-R6ProducerRequiredFile -Path (Join-Path $providerRoot "tools/verify-r6-provider-source.ps1") -Label "Provider source gate"
    Wait-R6ProducerGradleWindow -TimeoutSeconds $GradleClientWaitSeconds
    Write-Output "YOLO_R6_CONTROLLED_BUILD_STEP=provider-unsigned-source-gate"
    Invoke-R6ProducerBoundedPowerShell `
        -ScriptPath $providerSourceGate `
        -Arguments @("-RequireClean") `
        -TimeoutSeconds 1800 `
        -WorkingDirectory $providerRoot `
        -Label "Provider unsigned source gate"
    $providerSourceReceiptPath = Get-R6ProducerRequiredFile -Path (Join-Path $providerRoot "build/reports/yolo/r6-provider-source.generated.json") -Label "Provider source receipt"
    $providerSourceReceipt = Get-Content -Raw -LiteralPath $providerSourceReceiptPath | ConvertFrom-Json
    Assert-R6ProducerCondition ([string]$providerSourceReceipt.result -ceq "PASS" -and [bool]$providerSourceReceipt.buildIdentityProven) (
        "Provider unsigned source receipt is not an admitted clean build"
    )
    foreach ($field in @("runtimeComponent", "protocolApiMin", "protocolApiMax", "backend", "task", "decoder")) {
        Assert-R6ProducerCondition (
            [string]$providerSourceReceipt.identity.$field -ceq [string]$expectedProviderContract[$field]
        ) "Provider unsigned source receipt contract field differs: $field"
    }
    $sourceSupportedAbis = @($providerSourceReceipt.identity.supportedAbis | ForEach-Object { [string]$_ })
    Assert-R6ProducerCondition (
        $sourceSupportedAbis.Count -eq 1 -and $sourceSupportedAbis[0] -ceq $expectedProviderContract.supportedAbis[0]
    ) "Provider unsigned source receipt supported ABI contract differs"
    $providerUnsignedOriginal = Get-R6ProducerRequiredFile -Path ([string]$providerSourceReceipt.release.artifact.path) -Label "Provider admitted unsigned APK"
    Assert-R6ProducerCondition ((Get-R6ProducerSha256 $providerUnsignedOriginal) -ceq [string]$providerSourceReceipt.release.artifact.sha256) (
        "Provider admitted unsigned APK differs from its source receipt"
    )

    $mainBuildClone = New-R6ProducerOrdinaryMainClone -ExpectedCommit $mainPre.revision
    $mainBuildRoot = [string]$mainBuildClone.path
    $mainSignTargets = Install-R6ProducerSigningMaterial -Repository $mainBuildRoot -PropertiesSource $signPropertiesPath -KeyStoreSource $keyStorePath -KeyStoreLeaf $keyStoreLeaf
    try {
        $mainBuildStartedUtc = [DateTime]::UtcNow
        $mainGradleArguments = @(
            "--no-daemon",
            "-Pautojs.gradle.build.number.auto.increment.enabled=false",
            "-Pautojs.gradle.build.time.update.enabled=false",
            "-PyoloR6ReleaseAndroidTest=true",
            ":app:clean",
            ":app:assembleAppRelease",
            ":app:assembleAppReleaseAndroidTest"
        )
        Invoke-R6ProducerGradle -Repository $mainBuildRoot -Arguments $mainGradleArguments -WaitSeconds $GradleClientWaitSeconds -BuildTimeoutSeconds 7200 -Label "host-and-release-instrumentation"
        $hostSignedOutput = Resolve-R6ProducerApkFromMetadata -MetadataPath (Join-Path $mainBuildRoot "app/build/outputs/apk/app/release/output-metadata.json") -Label "Host release" -Abi "arm64-v8a"
        $testSignedOutput = Resolve-R6ProducerApkFromMetadata -MetadataPath (Join-Path $mainBuildRoot "app/build/outputs/apk/androidTest/app/release/output-metadata.json") -Label "release instrumentation"
        $hostSignedOutputRecord = Get-R6ProducerArtifact -Path $hostSignedOutput -BuildStartedUtc $mainBuildStartedUtc -Label "Host signed release"
        $testSignedOutputRecord = Get-R6ProducerArtifact -Path $testSignedOutput -BuildStartedUtc $mainBuildStartedUtc -Label "release instrumentation"
        $hostArtifactPath = Join-Path $artifactRoot "AutoJs6-v6.8.0-5275-release.apk"
        $testArtifactPath = Join-Path $artifactRoot "AutoJs6-v6.8.0-5275-release-androidTest.apk"
        foreach ($path in @($hostArtifactPath, $testArtifactPath)) { if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Force } }
        Copy-Item -LiteralPath $hostSignedOutput -Destination $hostArtifactPath
        Copy-Item -LiteralPath $testSignedOutput -Destination $testArtifactPath
    } finally {
        Remove-R6ProducerSigningMaterial -Targets $mainSignTargets
        $mainSignTargets = $null
    }

    $mainClonePost = Get-R6ProducerGitSnapshot -Repository $mainBuildRoot -ExpectedCommit $mainPre.revision -Label "ordinary Host build clone post-build"
    $mainPost = Get-R6ProducerGitSnapshot -Repository $repositoryRoot -ExpectedCommit $mainPre.revision -Label "Host post-build"
    $providerPost = Get-R6ProducerGitSnapshot -Repository $providerRoot -ExpectedCommit $providerPre.revision -Label "Provider post-build"
    foreach ($secretTarget in @(
        (Join-Path $repositoryRoot "sign.properties"),
        (Join-Path $repositoryRoot "app/$keyStoreLeaf"),
        (Join-Path $providerRoot "sign.properties"),
        (Join-Path $providerRoot "app/$keyStoreLeaf")
    )) {
        Assert-R6ProducerCondition (-not (Test-Path -LiteralPath $secretTarget)) "Temporary signing material remains after build"
    }

    $hostArtifact = Get-R6ProducerArtifact -Path $hostArtifactPath -BuildStartedUtc $mainBuildStartedUtc -Label "copied Host release"
    $providerArtifact = Get-R6ProducerArtifact -Path $providerArtifactPath -BuildStartedUtc $providerBuildStartedUtc -Label "copied Provider release"
    $testArtifact = Get-R6ProducerArtifact -Path $testArtifactPath -BuildStartedUtc $mainBuildStartedUtc -Label "copied instrumentation APK"
    $hostIdentity = Get-R6ProducerApkIdentity -Aapt2 $tools.aapt2 -Apk $hostArtifact.path -Label "Host"
    $providerIdentity = Get-R6ProducerApkIdentity -Aapt2 $tools.aapt2 -Apk $providerArtifact.path -Label "Provider"
    $testIdentity = Get-R6ProducerApkIdentity -Aapt2 $tools.aapt2 -Apk $testArtifact.path -Label "Instrumentation"
    Assert-R6ProducerCondition ($hostIdentity.packageName -ceq $hostPackage -and $hostIdentity.versionCode -eq $expectedHostVersionCode -and $hostIdentity.versionName -ceq $expectedHostVersionName) "Host release identity differs"
    Assert-R6ProducerCondition (-not $hostIdentity.debuggable) "Host release is debuggable"
    Assert-R6ProducerCondition ($providerIdentity.packageName -ceq $providerPackage -and $providerIdentity.versionCode -eq $expectedProviderVersionCode -and $providerIdentity.versionName -ceq $expectedProviderVersionName) "Provider release identity differs"
    Assert-R6ProducerCondition (-not $providerIdentity.debuggable) "Provider release is debuggable"
    Assert-R6ProducerCondition ($testIdentity.packageName -ceq "$hostPackage.test") "Instrumentation package differs"
    Assert-R6ProducerCondition ($null -ne $testIdentity.instrumentation -and [string]$testIdentity.instrumentation.targetPackage -ceq $hostPackage) "Instrumentation does not target the production Host package"
    foreach ($pair in @(
        [pscustomobject]@{ path = $hostArtifact.path; label = "Host" },
        [pscustomobject]@{ path = $providerArtifact.path; label = "Provider" },
        [pscustomobject]@{ path = $testArtifact.path; label = "Instrumentation" }
    )) {
        $signer = Get-R6ProducerApkSigner -ApkSigner $tools.apkSigner -Apk $pair.path -Label $pair.label
        Assert-R6ProducerCondition ($signer -ceq $declaredSigner) "$($pair.label) signer differs from the approved official signer"
    }
    $hostVcs = Get-R6ProducerApkVcsRevision -Apk $hostArtifact.path -Label "Host"
    $providerVcs = Get-R6ProducerApkVcsRevision -Apk $providerArtifact.path -Label "Provider"
    Assert-R6ProducerCondition ($hostVcs -ceq $mainPre.revision) "Host signed APK VCS revision differs from clean source"
    Assert-R6ProducerCondition ($providerVcs -ceq $providerPre.revision) "Provider signed APK VCS revision differs from clean source"
    $hostBoundary = Get-R6ProducerApkPayloadBoundary -Apk $hostArtifact.path -Kind Host
    $providerBoundary = Get-R6ProducerApkPayloadBoundary -Apk $providerArtifact.path -Kind Provider
    $testBoundary = Get-R6ProducerApkPayloadBoundary -Apk $testArtifact.path -Kind Instrumentation

    $receipt = [ordered]@{
        schemaVersion = 1
        gate = $producerName
        mode = "CONTROLLED_CLEAN_SIGNED_BUILD"
        result = "PASS"
        generatedAtUtc = [DateTime]::UtcNow.ToString("o")
        startedAtUtc = $startedAtUtc.ToString("o")
        finishedAtUtc = [DateTime]::UtcNow.ToString("o")
        receiptSemantics = "CONTROLLED_BUILD_RECEIPT_NOT_CRYPTOGRAPHIC_ATTESTATION"
        producer = [ordered]@{
            path = $producerScript
            sha256 = Get-R6ProducerSha256 $producerScript
            sameProcessPreBuildPostBuildAndArtifactVerification = $true
            globalGradleMutex = "Global/AutoJs6ControlledGradleBuild"
            abandonedMutexRecovered = $abandonedMutexRecovered
            externalGradleClientChecks = "PASS"
        }
        source = [ordered]@{
            host = [ordered]@{ preBuild = $mainPre; postBuild = $mainPost; unchanged = $true }
            provider = [ordered]@{ preBuild = $providerPre; postBuild = $providerPost; unchanged = $true }
        }
        signing = [ordered]@{
            officialCertificateSha256 = $declaredSigner
            pinnedSource = $pinnedSigner
            propertiesValuesRecorded = $false
            propertiesDigestRecorded = $false
            aliasRecorded = $false
            secretValuesPassedOnCommandLine = $false
            temporaryCopiesDeleted = $true
            temporaryCopiesVerifiedAbsent = $true
        }
        protocolAarHandoff = $protocolBoundary
        hostOrdinaryBuildClone = [ordered]@{
            method = "LOCAL_GIT_CLONE_NO_HARDLINKS_DETACHED_EXACT_COMMIT"
            sourceRevision = $mainPre.revision
            ordinaryGitDirectory = $true
            removedAfterBuild = $true
        }
        providerUnsignedAdmission = [ordered]@{
            receiptPath = $providerSourceReceiptPath
            receiptSha256 = Get-R6ProducerSha256 $providerSourceReceiptPath
            artifactPath = $providerUnsignedOriginal
            artifactSha256 = [string]$providerSourceReceipt.release.artifact.sha256
            artifactLength = [long]$providerSourceReceipt.release.artifact.bytes
            fullUnsignedEvidenceRebuiltAfterSignedBuild = $true
            contract = $expectedProviderContract
        }
        builds = [ordered]@{
            provider = [ordered]@{ startedAtUtc = $providerBuildStartedUtc.ToString("o"); executable = ".\gradlew.bat"; arguments = $providerGradleArguments; status = "PASS" }
            hostAndInstrumentation = [ordered]@{
                startedAtUtc = $mainBuildStartedUtc.ToString("o")
                executable = ".\gradlew.bat"
                arguments = $mainGradleArguments
                status = "PASS"
                ordinaryClonePostBuild = $mainClonePost
            }
        }
        artifacts = [ordered]@{
            host = [ordered]@{ file = $hostArtifact; identity = $hostIdentity; signerSha256 = $declaredSigner; vcsRevision = $hostVcs; payloadBoundary = $hostBoundary }
            provider = [ordered]@{ file = $providerArtifact; identity = $providerIdentity; signerSha256 = $declaredSigner; vcsRevision = $providerVcs; payloadBoundary = $providerBoundary; minified = $true; resourcesShrunk = $true; abi = "arm64-v8a" }
            instrumentation = [ordered]@{
                file = $testArtifact
                identity = $testIdentity
                signerSha256 = $declaredSigner
                releaseVariantInvocation = "SAME_CONTROLLED_INVOCATION_AS_HOST"
                controlledInvocationHostVersionCode = $expectedHostVersionCode
                controlledInvocationHostApkSha256 = $hostArtifact.sha256
                targetVersionNotIndependentlyDerivedFromTestApk = $true
                payloadBoundary = $testBoundary
            }
        }
        releaseBoundary = [ordered]@{
            upgradeRuntimeEvidence = "NOT_RUN_BY_PRODUCT_DECISION"
            versionRollbackEvidence = "NOT_RUN_BY_PRODUCT_DECISION"
            predecessorArtifactApplicable = $false
            predecessorReason = "The product owner selected Provider 0.1.0 versionCode 2 as the first release and explicitly declined a versionCode 1 predecessor."
            sameVersionRecovery = "ARCHIVED_EXACT_V2_REINSTALL_ONLY"
            withdrawalMitigation = "DISABLE_PROVIDER_WITHDRAW_INDEX_PUBLISH_FORWARD_FIX_V3"
            deviceEvidence = "NOT_RUN"
            publication = "NOT_RUN"
            production = "PRODUCTION_NOT_COMPLETE"
        }
    }
    Write-R6ProducerJson -Value $receipt
    $bodyCompleted = $true
} finally {
    $cleanupErrors = [System.Collections.Generic.List[string]]::new()
    if ($runStarted) {
        try { Remove-R6ProducerSigningMaterial -Targets $mainSignTargets } catch { $cleanupErrors.Add($_.Exception.Message) }
        try { Remove-R6ProducerSigningMaterial -Targets $providerSignTargets } catch { $cleanupErrors.Add($_.Exception.Message) }
        $secretCleanupPaths = @(
            (Join-Path $repositoryRoot "sign.properties"),
            (Join-Path $repositoryRoot "app/$keyStoreLeaf")
        )
        if ($null -ne $providerRoot) {
            $secretCleanupPaths += @((Join-Path $providerRoot "sign.properties"), (Join-Path $providerRoot "app/$keyStoreLeaf"))
        }
        foreach ($message in @(Remove-R6ProducerFixedOutput `
            -Path $mainCloneStagingPath `
            -Label "temporary ordinary Host build clone" `
            -Recurse)) {
            $cleanupErrors.Add([string]$message)
        }
        foreach ($path in $secretCleanupPaths) {
            if (Test-Path -LiteralPath $path) { $cleanupErrors.Add("Temporary signing material remains: $path") }
        }
        if ($bodyCompleted -and $cleanupErrors.Count -eq 0 -and (Test-Path -LiteralPath $receiptPath -PathType Leaf)) {
            try {
                $retainedReceipt = Get-Content -Raw -LiteralPath $receiptPath | ConvertFrom-Json
                $producerSucceeded = [int]$retainedReceipt.schemaVersion -eq 1 -and
                    [string]$retainedReceipt.gate -ceq $producerName -and
                    [string]$retainedReceipt.result -ceq "PASS"
            } catch {
                $producerSucceeded = $false
            }
        }
        if (-not $producerSucceeded -or $cleanupErrors.Count -gt 0) {
            foreach ($target in @(
                [pscustomobject]@{ path = $receiptPath; label = "failed-run controlled receipt"; recurse = $false },
                [pscustomobject]@{ path = $artifactRoot; label = "failed-run controlled artifact directory"; recurse = $true }
            )) {
                foreach ($message in @(Remove-R6ProducerFixedOutput `
                    -Path $target.path `
                    -Label $target.label `
                    -Recurse:([bool]$target.recurse))) {
                    $cleanupErrors.Add([string]$message)
                }
            }
        }
    }
    if ($mutexOwned -and $null -ne $globalMutex) { $globalMutex.ReleaseMutex() }
    if ($null -ne $globalMutex) { $globalMutex.Dispose() }
    if ($cleanupErrors.Count -gt 0) { throw "Controlled producer cleanup failed: $($cleanupErrors -join '; ')" }
}
Assert-R6ProducerCondition $producerSucceeded "Controlled producer completed without a retained PASS receipt"
Write-Output "YOLO_R6_CONTROLLED_SIGNED_BUILD=PASS report=$receiptPath"
Write-Output "YOLO_R6_PRODUCTION=PRODUCTION_NOT_COMPLETE"
