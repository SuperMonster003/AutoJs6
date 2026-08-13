[CmdletBinding()]
param(
    [string] $SiblingPath = "D:\idea-projects\AutoJs6-Plugin-Yolo-NCNN",

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $ProviderApk,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $HostApk
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$providerApplicationId = "io.github.supermonster003.autojs6.plugin.yolo.ncnn"
$hostApplicationId = "org.autojs.autojs6"
$providerNativeEntry = "lib/arm64-v8a/libautojs_yolo.so"
$supportedAbi = "arm64-v8a"
$apkSignerDigestPattern = '(?im)^(?:Signer #[0-9]+|V[0-9.]+ Signer(?: #[0-9]+)?)\s*:?\s*certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$'
$expectedNcnn = [ordered]@{
    version = "20260526"
    sourceUrl = "https://github.com/Tencent/ncnn/releases/download/20260526/ncnn-20260526-android.zip"
    sourceRevision = "e54f7b1f88434e1d844ea0551b880a1cfb079ce1"
    archiveLength = 21045913L
    archiveSha256 = "85b18b875488585c2d21360430e0e54abb6c04aa88094b471c20208ab55ff796"
    staticLibraryPath = "20260526/arm64-v8a/lib/libncnn.a"
    staticLibraryLength = 7275272L
    staticLibrarySha256 = "505e484ae18b59e51cb35343506146ab101e608a86f8c1d0834e761b99b2a490"
    licenseFile = "LICENSE-20260526.txt"
    licenseSha256 = "7c974bac98848df46be1af5bdaa3c3c9c01f6082a90f55caeb7f60c6208aa255"
    ndkVersion = "29.0.14206865"
    cmakeVersion = "3.22.1"
}
$requiredAssetEntries = [ordered]@{
    "assets/licenses/MPL-2.0.txt" = "app/src/main/assets/licenses/MPL-2.0.txt"
    "assets/licenses/ncnn-20260526.txt" = "app/src/main/assets/licenses/ncnn-20260526.txt"
    "assets/third_party/ncnn/provenance.lock.json" = "app/src/main/assets/third_party/ncnn/provenance.lock.json"
}
$knownModelLeafNames = @(
    "model.json",
    "model-manifest-v1.json",
    "model.ncnn.param",
    "model.ncnn.bin",
    "bus.jpg",
    "synthetic-heldout.png",
    "expected-bus-detections.json",
    "expected-synthetic-detection.json",
    "fixture.lock.json",
    "metadata.yaml",
    "yolo11n.pt"
)
$modelExtensions = @(".bin", ".onnx", ".param", ".pt", ".pth", ".tflite", ".weights")

function Assert-YoloR5PackagingCondition {
    param(
        [Parameter(Mandatory = $true)][bool] $Condition,
        [Parameter(Mandatory = $true)][string] $Message
    )
    if (-not $Condition) { throw $Message }
}

function Get-YoloR5RequiredFile {
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

function Get-YoloR5RequiredDirectory {
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

function Invoke-YoloR5PackagingExternal {
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
    return @($output | ForEach-Object { [string]$_ })
}

function Get-YoloR5GitText {
    param(
        [Parameter(Mandatory = $true)][string] $Repository,
        [Parameter(Mandatory = $true)][string[]] $Arguments
    )
    return ((Invoke-YoloR5PackagingExternal -FilePath "git" -Arguments (@("-C", $Repository) + $Arguments) -Quiet) -join "`n").Trim()
}

function Get-YoloR5Sha256 {
    param([Parameter(Mandatory = $true)][string] $Path)
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Get-YoloR5BytesSha256 {
    param([Parameter(Mandatory = $true)][byte[]] $Bytes)
    $algorithm = [System.Security.Cryptography.SHA256]::Create()
    try {
        return (($algorithm.ComputeHash($Bytes) | ForEach-Object { $_.ToString("x2") }) -join "")
    } finally {
        $algorithm.Dispose()
    }
}

function Test-YoloR5BytesEqual {
    param(
        [Parameter(Mandatory = $true)][byte[]] $First,
        [Parameter(Mandatory = $true)][byte[]] $Second
    )
    if ($First.Length -ne $Second.Length) { return $false }
    for ($index = 0; $index -lt $First.Length; $index += 1) {
        if ($First[$index] -ne $Second[$index]) { return $false }
    }
    return $true
}

function Get-YoloR5NormalizedUtf8Text {
    param([Parameter(Mandatory = $true)][byte[]] $Bytes)
    $utf8 = [System.Text.UTF8Encoding]::new($false, $true)
    return $utf8.GetString($Bytes).Replace("`r`n", "`n")
}

function Get-YoloR5SdkRoots {
    param([Parameter(Mandatory = $true)][string[]] $RepositoryRoots)
    $candidates = [System.Collections.Generic.List[string]]::new()
    foreach ($repository in $RepositoryRoots) {
        $localProperties = Join-Path $repository "local.properties"
        if (Test-Path -LiteralPath $localProperties -PathType Leaf) {
            $sdkLine = Get-Content -LiteralPath $localProperties |
                Where-Object { $_ -match '^sdk\.dir=' } |
                Select-Object -First 1
            if ($null -ne $sdkLine) {
                $value = ($sdkLine -replace '^sdk\.dir=', '') -replace '\\:', ':' -replace '\\\\', '\'
                if (-not [string]::IsNullOrWhiteSpace($value)) { $candidates.Add($value.Trim()) }
            }
        }
    }
    foreach ($environmentName in @("ANDROID_SDK_ROOT", "ANDROID_HOME")) {
        $value = [Environment]::GetEnvironmentVariable($environmentName)
        if (-not [string]::IsNullOrWhiteSpace($value)) { $candidates.Add($value.Trim()) }
    }
    return @($candidates | ForEach-Object {
        [System.IO.Path]::GetFullPath($_)
    } | Where-Object {
        Test-Path -LiteralPath $_ -PathType Container
    } | Select-Object -Unique)
}

function Resolve-YoloR5BuildTools {
    param([Parameter(Mandatory = $true)][string[]] $SdkRoots)
    foreach ($sdkRoot in $SdkRoots) {
        $buildToolsRoot = Join-Path $sdkRoot "build-tools"
        $directories = @(Get-ChildItem -LiteralPath $buildToolsRoot -Directory -ErrorAction SilentlyContinue |
            Sort-Object { [version]($_.Name -replace '-.*$', '') } -Descending)
        foreach ($directory in $directories) {
            $aapt2 = Join-Path $directory.FullName "aapt2.exe"
            $apkSigner = Join-Path $directory.FullName "apksigner.bat"
            $zipAlign = Join-Path $directory.FullName "zipalign.exe"
            if (
                (Test-Path -LiteralPath $aapt2 -PathType Leaf) -and
                (Test-Path -LiteralPath $apkSigner -PathType Leaf) -and
                (Test-Path -LiteralPath $zipAlign -PathType Leaf)
            ) {
                return [pscustomobject][ordered]@{
                    sdkRoot = $sdkRoot
                    version = $directory.Name
                    aapt2 = $aapt2
                    apkSigner = $apkSigner
                    zipAlign = $zipAlign
                }
            }
        }
    }
    throw "Unable to resolve one Android build-tools directory containing aapt2, apksigner, and zipalign"
}

function Resolve-YoloR5ReadElf {
    param(
        [Parameter(Mandatory = $true)][string[]] $SdkRoots,
        [Parameter(Mandatory = $true)][string] $NdkVersion
    )
    foreach ($sdkRoot in $SdkRoots) {
        $prebuiltRoot = Join-Path $sdkRoot "ndk\$NdkVersion\toolchains\llvm\prebuilt"
        foreach ($prebuilt in @(Get-ChildItem -LiteralPath $prebuiltRoot -Directory -ErrorAction SilentlyContinue)) {
            foreach ($leaf in @("llvm-readelf.exe", "llvm-readelf")) {
                $candidate = Join-Path $prebuilt.FullName "bin\$leaf"
                if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
            }
        }
    }
    throw "Unable to resolve llvm-readelf from pinned NDK $NdkVersion"
}

function Get-YoloR5ApkSigner {
    param(
        [Parameter(Mandatory = $true)][string] $ApkSigner,
        [Parameter(Mandatory = $true)][string] $ApkPath,
        [Parameter(Mandatory = $true)][string] $Label
    )
    $output = Invoke-YoloR5PackagingExternal -FilePath $ApkSigner -Arguments @(
        "verify", "--verbose", "--print-certs", $ApkPath
    ) -Quiet
    $text = $output -join "`n"
    $countMatches = [regex]::Matches($text, '(?im)^Number of signers:\s*([0-9]+)\s*$')
    Assert-YoloR5PackagingCondition (
        $countMatches.Count -eq 1 -and [int]$countMatches[0].Groups[1].Value -eq 1
    ) "$Label must have exactly one APK signer"
    $digestMatches = [regex]::Matches(
        $text,
        $apkSignerDigestPattern
    )
    $digests = @($digestMatches | ForEach-Object {
        $_.Groups[1].Value.ToLowerInvariant()
    } | Sort-Object -Unique)
    Assert-YoloR5PackagingCondition ($digests.Count -eq 1) "$Label must expose exactly one signer certificate SHA-256"
    return [pscustomobject][ordered]@{
        count = 1
        certificateSha256 = $digests[0]
        verified = $true
    }
}

function Get-YoloR5ManifestBoolean {
    param(
        [Parameter(Mandatory = $true)][string] $XmlTree,
        [Parameter(Mandatory = $true)][string] $Name,
        [Parameter(Mandatory = $true)][bool] $DefaultValue,
        [Parameter(Mandatory = $true)][string] $Label
    )
    $escaped = [regex]::Escape($Name)
    $matches = [regex]::Matches(
        $XmlTree,
        "(?im)^\s*A:\s+http://schemas\.android\.com/apk/res/android:$escaped(?:\(0x[0-9a-f]+\))?=(?:\(type\s+0x[0-9a-f]+\))?([^\s]+)\s*$"
    )
    Assert-YoloR5PackagingCondition ($matches.Count -le 1) "$Label manifest repeats android:$Name"
    if ($matches.Count -eq 0) {
        return [pscustomobject][ordered]@{ present = $false; value = $DefaultValue; raw = $null }
    }
    $raw = $matches[0].Groups[1].Value.Trim().ToLowerInvariant()
    $value = switch ($raw) {
        { $_ -in @("true", "1", "0x1", "0xffffffff") } { $true; break }
        { $_ -in @("false", "0", "0x0") } { $false; break }
        default { throw "$Label manifest has an unrecognized android:$Name value: $raw" }
    }
    return [pscustomobject][ordered]@{ present = $true; value = $value; raw = $raw }
}

function Assert-YoloR5ParserSamples {
    $xmlSamples = @(
        @{ text = 'A: http://schemas.android.com/apk/res/android:debuggable(0x0101000f)=(type 0x12)0xffffffff'; expected = $true },
        @{ text = 'A: http://schemas.android.com/apk/res/android:debuggable(0x0101000f)=(type 0x12)0x0'; expected = $false },
        @{ text = 'A: http://schemas.android.com/apk/res/android:debuggable(0x0101000f)=true'; expected = $true }
    )
    foreach ($sample in $xmlSamples) {
        $parsed = Get-YoloR5ManifestBoolean -XmlTree $sample.text -Name "debuggable" -DefaultValue $false -Label "internal sample"
        Assert-YoloR5PackagingCondition (
            $parsed.present -and $parsed.value -eq $sample.expected
        ) "Internal aapt2 boolean parser self-check failed"
    }

    $signerSamples = @(
        'Signer #1 certificate SHA-256 digest: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        'Signer #1: certificate SHA-256 digest: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        'V2 Signer: certificate SHA-256 digest: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
    )
    foreach ($sample in $signerSamples) {
        $matches = [regex]::Matches($sample, $apkSignerDigestPattern)
        Assert-YoloR5PackagingCondition (
            $matches.Count -eq 1 -and $matches[0].Groups[1].Value.Length -eq 64
        ) "Internal apksigner parser self-check failed"
    }
}

function Get-YoloR5ApkManifest {
    param(
        [Parameter(Mandatory = $true)][string] $Aapt2,
        [Parameter(Mandatory = $true)][string] $ApkPath,
        [Parameter(Mandatory = $true)][string] $Label
    )
    $badging = (Invoke-YoloR5PackagingExternal -FilePath $Aapt2 -Arguments @("dump", "badging", $ApkPath) -Quiet) -join "`n"
    $xmlTree = (Invoke-YoloR5PackagingExternal -FilePath $Aapt2 -Arguments @(
        "dump", "xmltree", "--file", "AndroidManifest.xml", $ApkPath
    ) -Quiet) -join "`n"

    $packageMatches = [regex]::Matches(
        $badging,
        "(?m)^package:\s+name='([^']+)'\s+versionCode='([^']+)'\s+versionName='([^']*)'"
    )
    Assert-YoloR5PackagingCondition ($packageMatches.Count -eq 1) "$Label aapt badging must contain exactly one package identity"
    $packageMatch = $packageMatches[0]
    $minimumMatches = [regex]::Matches($badging, "(?m)^(?:sdkVersion|minSdkVersion):'([0-9]+)'\s*$")
    $targetMatches = [regex]::Matches($badging, "(?m)^targetSdkVersion:'([0-9]+)'\s*$")
    Assert-YoloR5PackagingCondition ($minimumMatches.Count -eq 1) "$Label aapt badging must contain exactly one numeric minSdk"
    Assert-YoloR5PackagingCondition ($targetMatches.Count -eq 1) "$Label aapt badging must contain exactly one numeric targetSdk"
    $minimumMatch = $minimumMatches[0]
    $targetMatch = $targetMatches[0]

    $nativeMatch = [regex]::Match($badging, '(?m)^native-code:\s*(.+)\s*$')
    $abis = @()
    if ($nativeMatch.Success) {
        $abis = @([regex]::Matches($nativeMatch.Groups[1].Value, "'([^']+)'") | ForEach-Object {
            $_.Groups[1].Value
        })
        Assert-YoloR5PackagingCondition ($abis.Count -gt 0) "$Label native-code line has no ABI values"
    }

    return [pscustomobject][ordered]@{
        applicationId = $packageMatch.Groups[1].Value
        versionCode = $packageMatch.Groups[2].Value
        versionName = $packageMatch.Groups[3].Value
        minSdk = [int]$minimumMatch.Groups[1].Value
        targetSdk = [int]$targetMatch.Groups[1].Value
        abis = @($abis)
        debuggable = Get-YoloR5ManifestBoolean -XmlTree $xmlTree -Name "debuggable" -DefaultValue $false -Label $Label
        extractNativeLibs = Get-YoloR5ManifestBoolean -XmlTree $xmlTree -Name "extractNativeLibs" -DefaultValue $true -Label $Label
    }
}

function Test-YoloR5ProviderModelEntry {
    param([Parameter(Mandatory = $true)][string] $EntryName)
    $normalized = $EntryName.Replace('\', '/')
    $leaf = [System.IO.Path]::GetFileName($normalized).ToLowerInvariant()
    $extension = [System.IO.Path]::GetExtension($leaf).ToLowerInvariant()
    return ($knownModelLeafNames -contains $leaf) -or ($modelExtensions -contains $extension)
}

function Test-YoloR5HostYoloModelEntry {
    param([Parameter(Mandatory = $true)][string] $EntryName)
    $normalized = $EntryName.Replace('\', '/')
    $leaf = [System.IO.Path]::GetFileName($normalized).ToLowerInvariant()
    $extension = [System.IO.Path]::GetExtension($leaf).ToLowerInvariant()
    $looksYoloSpecific = $normalized -match '(?i)(yolo|ncnn)'
    return $looksYoloSpecific -and (
        ($knownModelLeafNames -contains $leaf) -or ($modelExtensions -contains $extension)
    )
}

function Get-YoloR5ZipEntryBytes {
    param([Parameter(Mandatory = $true)] $Entry)
    $inputStream = $Entry.Open()
    $memory = [System.IO.MemoryStream]::new()
    try {
        $inputStream.CopyTo($memory)
        return $memory.ToArray()
    } finally {
        $memory.Dispose()
        $inputStream.Dispose()
    }
}

function Get-YoloR5ProviderZipEvidence {
    param(
        [Parameter(Mandatory = $true)][string] $ApkPath,
        [Parameter(Mandatory = $true)][string] $SiblingRoot,
        [Parameter(Mandatory = $true)][string] $ExtractedSoPath
    )
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($ApkPath)
    try {
        $entries = @($archive.Entries)
        $entryNames = @($entries | ForEach-Object { $_.FullName.Replace('\', '/') })
        $nativeEntries = @($entryNames | Where-Object {
            [System.IO.Path]::GetExtension($_) -ieq ".so"
        })
        Assert-YoloR5PackagingCondition (
            $nativeEntries.Count -eq 1 -and $nativeEntries[0] -ceq $providerNativeEntry
        ) "Provider APK must contain exactly $providerNativeEntry and no other native library"
        $modelEntries = @($entryNames | Where-Object { Test-YoloR5ProviderModelEntry -EntryName $_ })
        Assert-YoloR5PackagingCondition ($modelEntries.Count -eq 0) (
            "Provider APK contains model or fixture artifacts: $($modelEntries -join ', ')"
        )

        $assetEvidence = [ordered]@{}
        foreach ($assetSpec in $requiredAssetEntries.GetEnumerator()) {
            $matches = @($entries | Where-Object { $_.FullName.Replace('\', '/') -ceq $assetSpec.Key })
            Assert-YoloR5PackagingCondition ($matches.Count -eq 1) (
                "Provider APK must contain exactly one $($assetSpec.Key)"
            )
            $sourcePath = Get-YoloR5RequiredFile -Path (Join-Path $SiblingRoot $assetSpec.Value) -Label "source asset $($assetSpec.Key)"
            $sourceBytes = [System.IO.File]::ReadAllBytes($sourcePath)
            $packagedBytes = Get-YoloR5ZipEntryBytes -Entry $matches[0]
            Assert-YoloR5PackagingCondition (
                (Test-YoloR5BytesEqual -First $sourceBytes -Second $packagedBytes)
            ) "Packaged asset $($assetSpec.Key) differs byte-for-byte from sibling source"
            $assetEvidence[$assetSpec.Key] = [ordered]@{
                sourcePath = $assetSpec.Value.Replace('\', '/')
                length = $packagedBytes.Length
                sha256 = Get-YoloR5BytesSha256 -Bytes $packagedBytes
                sourceBytesMatch = $true
            }
        }

        $nativeMatches = @($entries | Where-Object { $_.FullName.Replace('\', '/') -ceq $providerNativeEntry })
        Assert-YoloR5PackagingCondition ($nativeMatches.Count -eq 1) "Provider native ZIP entry is not unique"
        $nativeBytes = Get-YoloR5ZipEntryBytes -Entry $nativeMatches[0]
        [System.IO.File]::WriteAllBytes($ExtractedSoPath, $nativeBytes)
        return [pscustomobject][ordered]@{
            entryCount = $entryNames.Count
            nativeEntries = @($nativeEntries)
            modelEntries = @()
            assets = $assetEvidence
            nativeEntryLength = $nativeBytes.Length
            nativeEntrySha256 = Get-YoloR5BytesSha256 -Bytes $nativeBytes
        }
    } finally {
        $archive.Dispose()
    }
}

function Get-YoloR5HostZipEvidence {
    param([Parameter(Mandatory = $true)][string] $ApkPath)
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($ApkPath)
    try {
        $entryNames = @($archive.Entries | ForEach-Object { $_.FullName.Replace('\', '/') })
        $nativeEntries = @($entryNames | Where-Object {
            [System.IO.Path]::GetExtension($_) -ieq ".so"
        })
        $forbiddenNative = @($nativeEntries | Where-Object { $_ -match '(?i)(yolo|ncnn)' })
        $forbiddenModels = @($entryNames | Where-Object { Test-YoloR5HostYoloModelEntry -EntryName $_ })
        Assert-YoloR5PackagingCondition ($forbiddenNative.Count -eq 0) (
            "Host APK contains YOLO/NCNN native code: $($forbiddenNative -join ', ')"
        )
        Assert-YoloR5PackagingCondition ($forbiddenModels.Count -eq 0) (
            "Host APK contains YOLO/NCNN model artifacts: $($forbiddenModels -join ', ')"
        )
        return [pscustomobject][ordered]@{
            entryCount = $entryNames.Count
            nativeEntries = @($nativeEntries)
            yoloNcnnNativeEntries = @()
            yoloNcnnModelEntries = @()
        }
    } finally {
        $archive.Dispose()
    }
}

function ConvertFrom-YoloR5HexUInt64 {
    param(
        [Parameter(Mandatory = $true)][string] $Value,
        [Parameter(Mandatory = $true)][string] $Label
    )
    Assert-YoloR5PackagingCondition ($Value -match '^0x[0-9a-fA-F]+$') "$Label is not hexadecimal: $Value"
    return [Convert]::ToUInt64($Value.Substring(2), 16)
}

function Get-YoloR5ElfEvidence {
    param(
        [Parameter(Mandatory = $true)][string] $ReadElf,
        [Parameter(Mandatory = $true)][string] $SoPath
    )
    $output = Invoke-YoloR5PackagingExternal -FilePath $ReadElf -Arguments @("-h", "-l", "-d", $SoPath) -Quiet
    $text = $output -join "`n"
    Assert-YoloR5PackagingCondition ($text -match '(?m)^\s*Class:\s+ELF64\s*$') "Provider native library is not ELF64"
    Assert-YoloR5PackagingCondition ($text -match '(?m)^\s*Type:\s+DYN\b') "Provider native library is not ELF DYN"
    Assert-YoloR5PackagingCondition ($text -match '(?m)^\s*Machine:\s+AArch64\s*$') "Provider native library is not AArch64"

    $loads = [System.Collections.Generic.List[object]]::new()
    foreach ($match in [regex]::Matches($text, '(?m)^\s*LOAD\s+([^\r\n]+)$')) {
        $tokens = @($match.Groups[1].Value.Trim() -split '\s+')
        Assert-YoloR5PackagingCondition ($tokens.Count -ge 7) "llvm-readelf emitted an unrecognized LOAD row"
        $offset = ConvertFrom-YoloR5HexUInt64 -Value $tokens[0] -Label "LOAD offset"
        $virtualAddress = ConvertFrom-YoloR5HexUInt64 -Value $tokens[1] -Label "LOAD virtual address"
        $alignment = ConvertFrom-YoloR5HexUInt64 -Value $tokens[$tokens.Count - 1] -Label "LOAD alignment"
        Assert-YoloR5PackagingCondition ($alignment -ge 0x4000) (
            "Provider LOAD alignment $($tokens[$tokens.Count - 1]) is below 0x4000"
        )
        Assert-YoloR5PackagingCondition (
            ($offset % $alignment) -eq ($virtualAddress % $alignment)
        ) "Provider LOAD offset and virtual address are not congruent for alignment"
        $loads.Add([pscustomobject][ordered]@{
            offset = $tokens[0].ToLowerInvariant()
            virtualAddress = $tokens[1].ToLowerInvariant()
            alignment = $tokens[$tokens.Count - 1].ToLowerInvariant()
            alignmentBytes = [long]$alignment
            congruent = $true
        })
    }
    Assert-YoloR5PackagingCondition ($loads.Count -gt 0) "Provider ELF contains no LOAD program header"

    $needed = @([regex]::Matches($text, '\(NEEDED\)\s+Shared library:\s+\[([^\]]+)\]') | ForEach-Object {
        $_.Groups[1].Value
    })
    Assert-YoloR5PackagingCondition ($needed.Count -gt 0) "Provider ELF contains no DT_NEEDED entries"
    Assert-YoloR5PackagingCondition (($needed | Select-Object -Unique).Count -eq $needed.Count) "Provider ELF repeats a DT_NEEDED entry"
    $unexpected = @($needed | Where-Object {
        $_ -notmatch '^lib(?:android|log|jnigraphics|m|dl|c)\.so$'
    })
    Assert-YoloR5PackagingCondition ($unexpected.Count -eq 0) (
        "Provider ELF has unexpected DT_NEEDED libraries: $($unexpected -join ', ')"
    )
    Assert-YoloR5PackagingCondition ('libc++_shared.so' -notin $needed) "Provider ELF must not depend on libc++_shared.so"
    Assert-YoloR5PackagingCondition ('libncnn.so' -notin $needed) "Provider ELF must not depend on a separate libncnn.so"

    return [pscustomobject][ordered]@{
        class = "ELF64"
        type = "DYN"
        machine = "AArch64"
        loadSegments = @($loads)
        allLoadAlignmentsAtLeast16384 = $true
        allLoadOffsetsCongruent = $true
        needed = @($needed)
    }
}

function Assert-YoloR5HexDigest {
    param(
        [Parameter(Mandatory = $true)][string] $Value,
        [Parameter(Mandatory = $true)][string] $Label,
        [int] $Length = 64
    )
    Assert-YoloR5PackagingCondition ($Value -match "^[0-9a-fA-F]{$Length}$") "$Label is not a $Length-character hexadecimal digest"
}

function Get-YoloR5NcnnEvidence {
    param(
        [Parameter(Mandatory = $true)][string] $SiblingRoot,
        [Parameter(Mandatory = $true)] $PackagedAssets
    )
    $lockPath = Get-YoloR5RequiredFile -Path (Join-Path $SiblingRoot "third_party\ncnn\provenance.lock.json") -Label "NCNN provenance lock"
    $lock = Get-Content -Raw -LiteralPath $lockPath | ConvertFrom-Json
    Assert-YoloR5PackagingCondition ([int]$lock.schemaVersion -eq 1) "NCNN provenance schemaVersion must be 1"
    Assert-YoloR5PackagingCondition ([string]$lock.state -ceq "staged") "NCNN provenance state must be staged"
    Assert-YoloR5PackagingCondition ([string]$lock.name -ceq "ncnn") "NCNN provenance name must be ncnn"
    Assert-YoloR5PackagingCondition ([string]$lock.version -ceq $expectedNcnn.version) "NCNN provenance version is not pinned"
    Assert-YoloR5PackagingCondition (
        [string]$lock.sourceUrl -ceq $expectedNcnn.sourceUrl
    ) "NCNN provenance source URL is not the pinned official release"
    Assert-YoloR5PackagingCondition (
        [string]$lock.sourceRevision -ceq $expectedNcnn.sourceRevision
    ) "NCNN source revision is not pinned"
    Assert-YoloR5PackagingCondition (
        [long]$lock.archiveLength -eq $expectedNcnn.archiveLength
    ) "NCNN archive length is not pinned"
    Assert-YoloR5PackagingCondition (
        [string]$lock.archiveSha256 -ceq $expectedNcnn.archiveSha256
    ) "NCNN archive SHA-256 is not pinned"
    Assert-YoloR5PackagingCondition ([string]$lock.license -ceq "BSD-3-Clause") "NCNN license ID must be BSD-3-Clause"
    Assert-YoloR5PackagingCondition ([string]$lock.licenseFile -ceq $expectedNcnn.licenseFile) "NCNN license file is not pinned"
    Assert-YoloR5PackagingCondition ([string]$lock.licenseSha256 -ceq $expectedNcnn.licenseSha256) "NCNN license SHA-256 is not pinned"
    Assert-YoloR5PackagingCondition ([string]$lock.ndkVersion -ceq $expectedNcnn.ndkVersion) "NCNN NDK version is not pinned"
    Assert-YoloR5PackagingCondition ([string]$lock.cmakeVersion -ceq $expectedNcnn.cmakeVersion) "NCNN CMake version is not pinned"
    Assert-YoloR5PackagingCondition ([string]$lock.stl -ceq "c++_static") "NCNN STL must be c++_static"
    Assert-YoloR5PackagingCondition (@($lock.abis).Count -eq 1 -and [string]$lock.abis[0] -ceq $supportedAbi) "NCNN provenance ABI must be arm64-v8a only"
    Assert-YoloR5PackagingCondition ($lock.vulkan -eq $false) "NCNN provenance must declare Vulkan disabled"

    $staticRelativePath = [string]$lock.arm64StaticLibrary.path
    Assert-YoloR5PackagingCondition ($staticRelativePath -ceq $expectedNcnn.staticLibraryPath) "NCNN static-library path is not pinned"
    Assert-YoloR5PackagingCondition ([long]$lock.arm64StaticLibrary.length -eq $expectedNcnn.staticLibraryLength) "NCNN static-library length is not pinned"
    Assert-YoloR5PackagingCondition ([string]$lock.arm64StaticLibrary.sha256 -ceq $expectedNcnn.staticLibrarySha256) "NCNN static-library SHA-256 is not pinned"
    $staticPath = Get-YoloR5RequiredFile -Path (Join-Path (Join-Path $SiblingRoot "third_party\ncnn") $staticRelativePath) -Label "NCNN arm64 static library"
    $staticFile = Get-Item -LiteralPath $staticPath
    $staticSha = Get-YoloR5Sha256 -Path $staticPath
    Assert-YoloR5PackagingCondition ($staticFile.Length -eq [long]$lock.arm64StaticLibrary.length) "NCNN static-library length differs from provenance"
    Assert-YoloR5PackagingCondition ($staticSha -eq ([string]$lock.arm64StaticLibrary.sha256).ToLowerInvariant()) "NCNN static-library SHA-256 differs from provenance"

    $licensePath = Get-YoloR5RequiredFile -Path (Join-Path $SiblingRoot "third_party\ncnn\$($lock.licenseFile)") -Label "NCNN license"
    $licenseSha = Get-YoloR5Sha256 -Path $licensePath
    Assert-YoloR5PackagingCondition ($licenseSha -eq ([string]$lock.licenseSha256).ToLowerInvariant()) "NCNN license SHA-256 differs from provenance"
    $packagedNcnnLicense = $PackagedAssets["assets/licenses/ncnn-20260526.txt"]
    $packagedNcnnProvenance = $PackagedAssets["assets/third_party/ncnn/provenance.lock.json"]
    Assert-YoloR5PackagingCondition (
        [string]$packagedNcnnLicense.sha256 -eq $licenseSha
    ) "Packaged NCNN license SHA-256 differs from provenance"
    Assert-YoloR5PackagingCondition (
        [string]$packagedNcnnProvenance.sha256 -eq (Get-YoloR5Sha256 -Path $lockPath)
    ) "Packaged NCNN provenance bytes differ from the source lock"

    $rootLicensePath = Get-YoloR5RequiredFile -Path (Join-Path $SiblingRoot "LICENSE") -Label "plugin MPL-2.0 license"
    $rootLicenseBytes = [System.IO.File]::ReadAllBytes($rootLicensePath)
    $assetLicensePath = Get-YoloR5RequiredFile -Path (Join-Path $SiblingRoot $requiredAssetEntries["assets/licenses/MPL-2.0.txt"]) -Label "packaged-source MPL-2.0 asset"
    $assetLicenseBytes = [System.IO.File]::ReadAllBytes($assetLicensePath)
    Assert-YoloR5PackagingCondition (
        (Get-YoloR5NormalizedUtf8Text -Bytes $rootLicenseBytes) -ceq (Get-YoloR5NormalizedUtf8Text -Bytes $assetLicenseBytes)
    ) "MPL-2.0 asset content differs from the root license after line-ending normalization"

    return [pscustomobject][ordered]@{
        schemaVersion = [int]$lock.schemaVersion
        state = [string]$lock.state
        name = [string]$lock.name
        version = [string]$lock.version
        sourceUrl = [string]$lock.sourceUrl
        sourceRevision = ([string]$lock.sourceRevision).ToLowerInvariant()
        archiveLength = [long]$lock.archiveLength
        archiveSha256 = ([string]$lock.archiveSha256).ToLowerInvariant()
        arm64StaticLibrary = [ordered]@{
            path = $staticRelativePath
            length = $staticFile.Length
            sha256 = $staticSha
            provenanceMatch = $true
        }
        license = [string]$lock.license
        licenseFile = [string]$lock.licenseFile
        licenseSha256 = $licenseSha
        packagedLicenseMatch = $true
        packagedProvenanceMatch = $true
        pluginLicense = [ordered]@{
            rootSha256 = Get-YoloR5Sha256 -Path $rootLicensePath
            sourceAssetSha256 = Get-YoloR5Sha256 -Path $assetLicensePath
            normalizedContentMatch = $true
        }
        ndkVersion = [string]$lock.ndkVersion
        cmakeVersion = [string]$lock.cmakeVersion
        stl = [string]$lock.stl
        abis = @($lock.abis)
        vulkan = [bool]$lock.vulkan
    }
}

$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
Assert-YoloR5ParserSamples
$reportDirectory = Join-Path $repositoryRoot "build\reports\yolo"
$reportPath = Join-Path $reportDirectory "r5-packaging.generated.json"
if (Test-Path -LiteralPath $reportPath -PathType Leaf) {
    Remove-Item -LiteralPath $reportPath -Force
}
$siblingRoot = Get-YoloR5RequiredDirectory -Path $SiblingPath -Label "YOLO NCNN sibling"
$providerApkPath = Get-YoloR5RequiredFile -Path $ProviderApk -Label "Provider APK"
$hostApkPath = Get-YoloR5RequiredFile -Path $HostApk -Label "Host APK"
Assert-YoloR5PackagingCondition ($providerApkPath -cne $hostApkPath) "Provider APK and Host APK must be different files"

$mainStatus = Get-YoloR5GitText -Repository $repositoryRoot -Arguments @("status", "--porcelain=v1", "--untracked-files=all")
$providerStatus = Get-YoloR5GitText -Repository $siblingRoot -Arguments @("status", "--porcelain=v1", "--untracked-files=all")
Assert-YoloR5PackagingCondition ([string]::IsNullOrEmpty($mainStatus)) "Host repository must be clean before R5 packaging verification"
Assert-YoloR5PackagingCondition ([string]::IsNullOrEmpty($providerStatus)) "Provider repository must be clean before R5 packaging verification"
$mainRevision = Get-YoloR5GitText -Repository $repositoryRoot -Arguments @("rev-parse", "HEAD")
$providerRevision = Get-YoloR5GitText -Repository $siblingRoot -Arguments @("rev-parse", "HEAD")

$ncnnLockPath = Get-YoloR5RequiredFile -Path (Join-Path $siblingRoot "third_party\ncnn\provenance.lock.json") -Label "NCNN provenance lock"
$ncnnLockForTools = Get-Content -Raw -LiteralPath $ncnnLockPath | ConvertFrom-Json
$sdkRoots = @(Get-YoloR5SdkRoots -RepositoryRoots @($repositoryRoot, $siblingRoot))
Assert-YoloR5PackagingCondition ($sdkRoots.Count -gt 0) "No Android SDK root is available"
$buildTools = Resolve-YoloR5BuildTools -SdkRoots $sdkRoots
$readElf = Resolve-YoloR5ReadElf -SdkRoots $sdkRoots -NdkVersion ([string]$ncnnLockForTools.ndkVersion)

$providerManifest = Get-YoloR5ApkManifest -Aapt2 $buildTools.aapt2 -ApkPath $providerApkPath -Label "Provider"
$hostManifest = Get-YoloR5ApkManifest -Aapt2 $buildTools.aapt2 -ApkPath $hostApkPath -Label "Host"
Assert-YoloR5PackagingCondition ($providerManifest.applicationId -ceq $providerApplicationId) "Provider APK applicationId is unexpected"
Assert-YoloR5PackagingCondition ($hostManifest.applicationId -ceq $hostApplicationId) "Host APK applicationId is unexpected"
Assert-YoloR5PackagingCondition (-not [bool]$providerManifest.debuggable.value) "Provider APK must be non-debuggable"
Assert-YoloR5PackagingCondition (
    [bool]$providerManifest.extractNativeLibs.present -and [bool]$providerManifest.extractNativeLibs.value
) "Provider APK must explicitly set extractNativeLibs=true for the extracted-native packaging strategy"
Assert-YoloR5PackagingCondition (
    $providerManifest.abis.Count -eq 1 -and $providerManifest.abis[0] -ceq $supportedAbi
) "Provider APK aapt native-code must contain arm64-v8a only"

$providerSigner = Get-YoloR5ApkSigner -ApkSigner $buildTools.apkSigner -ApkPath $providerApkPath -Label "Provider"
$hostSigner = Get-YoloR5ApkSigner -ApkSigner $buildTools.apkSigner -ApkPath $hostApkPath -Label "Host"
Assert-YoloR5PackagingCondition (
    $providerSigner.certificateSha256 -ceq $hostSigner.certificateSha256
) "Provider and Host APK signer certificates differ"

$tempBase = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot "build\tmp\yolo-r5-packaging"))
$tempRoot = [System.IO.Path]::GetFullPath((Join-Path $tempBase ([guid]::NewGuid().ToString("N"))))
Assert-YoloR5PackagingCondition (
    [System.IO.Path]::GetFullPath((Split-Path -Parent $tempRoot)) -ceq $tempBase
) "R5 packaging temporary directory escaped its fixed build directory"
$null = New-Item -ItemType Directory -Path $tempRoot -Force
$extractedSo = Join-Path $tempRoot "libautojs_yolo.so"
$temporaryDirectoryRemoved = $false
try {
    $providerZip = Get-YoloR5ProviderZipEvidence -ApkPath $providerApkPath -SiblingRoot $siblingRoot -ExtractedSoPath $extractedSo
    $hostZip = Get-YoloR5HostZipEvidence -ApkPath $hostApkPath
    $ncnnEvidence = Get-YoloR5NcnnEvidence -SiblingRoot $siblingRoot -PackagedAssets $providerZip.assets
    $elfEvidence = Get-YoloR5ElfEvidence -ReadElf $readElf -SoPath $extractedSo
    Assert-YoloR5PackagingCondition (
        (Get-Item -LiteralPath $extractedSo).Length -eq [long]$providerZip.nativeEntryLength
    ) "Extracted provider native length differs from its APK entry"
    Assert-YoloR5PackagingCondition (
        (Get-YoloR5Sha256 -Path $extractedSo) -ceq [string]$providerZip.nativeEntrySha256
    ) "Extracted provider native SHA-256 differs from its APK entry"

    $zipAlignOutput = Invoke-YoloR5PackagingExternal -FilePath $buildTools.zipAlign -Arguments @(
        "-c", "-P", "16", "4", $providerApkPath
    ) -Quiet

    $providerFile = Get-Item -LiteralPath $providerApkPath
    $hostFile = Get-Item -LiteralPath $hostApkPath
    $report = [ordered]@{
        schemaVersion = 1
        status = "PASS"
        evidenceLevel = "clean-repository-apk-packaging-and-static-elf-only"
        generatedAtUtc = [DateTime]::UtcNow.ToString("o")
        repositories = [ordered]@{
            host = [ordered]@{ revision = $mainRevision; clean = $true }
            provider = [ordered]@{ revision = $providerRevision; clean = $true }
        }
        tools = [ordered]@{
            androidSdkRoot = $buildTools.sdkRoot
            buildToolsVersion = $buildTools.version
            aapt2 = $buildTools.aapt2
            apkSigner = $buildTools.apkSigner
            zipAlign = $buildTools.zipAlign
            llvmReadElf = $readElf
        }
        providerApk = [ordered]@{
            path = $providerApkPath
            length = $providerFile.Length
            sha256 = Get-YoloR5Sha256 -Path $providerApkPath
            applicationId = $providerManifest.applicationId
            versionCode = $providerManifest.versionCode
            versionName = $providerManifest.versionName
            minSdk = $providerManifest.minSdk
            targetSdk = $providerManifest.targetSdk
            abis = @($providerManifest.abis)
            debuggable = $providerManifest.debuggable
            extractNativeLibs = $providerManifest.extractNativeLibs
            signer = $providerSigner
            modelFree = $true
            zip = $providerZip
        }
        hostApk = [ordered]@{
            path = $hostApkPath
            length = $hostFile.Length
            sha256 = Get-YoloR5Sha256 -Path $hostApkPath
            applicationId = $hostManifest.applicationId
            versionCode = $hostManifest.versionCode
            versionName = $hostManifest.versionName
            minSdk = $hostManifest.minSdk
            targetSdk = $hostManifest.targetSdk
            abis = @($hostManifest.abis)
            debuggable = $hostManifest.debuggable
            extractNativeLibs = $hostManifest.extractNativeLibs
            signer = $hostSigner
            signerMatchesProvider = $true
            zip = $hostZip
        }
        ncnn = $ncnnEvidence
        native = [ordered]@{
            entry = $providerNativeEntry
            length = $providerZip.nativeEntryLength
            sha256 = $providerZip.nativeEntrySha256
            elf = $elfEvidence
        }
        zipAlignment = [ordered]@{
            command = "zipalign -c -P 16 4 <provider-apk>"
            status = "PASS"
            output = @($zipAlignOutput)
        }
        evidenceBoundary = [ordered]@{
            packagingStrategy = "extracted-native"
            artifactBuildIdentityProven = $false
            staticElfAlignmentIsNotRuntimeLoadEvidence = $true
            deviceCommandsExecuted = $false
            buildExecuted = $false
        }
        cleanup = [ordered]@{
            temporaryDirectory = $tempRoot
            temporaryDirectoryRemoved = $false
        }
        claims = [ordered]@{
            APK_PACKAGING = "PASS"
            ELF_16K_COMPATIBLE = "PASS"
            NATIVE_LOAD_ARM64_DEVICE = "NOT_RUN"
            NATIVE_LOAD_16K_DEVICE = "NOT_RUN"
            RC = "NOT_CLAIMED_PACKAGING_ONLY"
            PRODUCTION = "NOT_CLAIMED"
        }
    }
} finally {
    if (Test-Path -LiteralPath $tempRoot -PathType Container) {
        $resolvedParent = [System.IO.Path]::GetFullPath((Split-Path -Parent $tempRoot))
        if ($resolvedParent -cne $tempBase) {
            throw "Refusing to remove unexpected R5 packaging temporary directory: $tempRoot"
        }
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
    $temporaryDirectoryRemoved = -not (Test-Path -LiteralPath $tempRoot)
}

Assert-YoloR5PackagingCondition $temporaryDirectoryRemoved "R5 packaging temporary directory was not removed"
$report.cleanup.temporaryDirectoryRemoved = $true
$null = New-Item -ItemType Directory -Path $reportDirectory -Force
$json = $report | ConvertTo-Json -Depth 14
[System.IO.File]::WriteAllText($reportPath, "$json`n", [System.Text.UTF8Encoding]::new($false))
Write-Host "YOLO_R5_PACKAGING=PASS report=$reportPath"
