#Requires -Version 7.0

[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^[A-Za-z0-9._:-]+$')]
    [string] $Serial,

    [Parameter(Mandatory)]
    [ValidateNotNullOrEmpty()]
    [string] $DiagnosticName,

    [Parameter(Mandatory)]
    [ValidateNotNullOrEmpty()]
    [string] $Selector,

    [Parameter(Mandatory)]
    [int] $RunnerExitCode,

    [Parameter(Mandatory)]
    [ValidateNotNullOrEmpty()]
    [string] $FailureSummary,

    [Parameter(Mandatory)]
    [string] $RawTranscript,

    [Parameter(Mandatory)]
    [ValidateNotNullOrEmpty()]
    [string[]] $Artifact,

    [Parameter(Mandatory)]
    [ValidateNotNullOrEmpty()]
    [string[]] $Source,

    [switch] $MutationAttempted,

    [switch] $MutationObserved,

    [switch] $RestorationAttempted,

    [switch] $PackageStateRestorationVerified,

    [switch] $AndroidUserSetRestorationVerified,

    [switch] $RestorationVerified,

    [ValidateRange(1, 1073741824)]
    [long] $MaximumTranscriptBytes = 67108864,

    [string] $OutputDirectory = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# This source-only helper consumes an already-finished diagnostic transcript.
# It does not invoke a runner, Gradle, ADB, or any device operation, and it can
# only emit non-acceptance failure evidence under its selected output directory.
$evidenceKind = 'AUTOJS6_PYTHON_R2_DEVICE_DIAGNOSTIC_FAILURE'
$manifestKind = 'AUTOJS6_PYTHON_R2_FAILURE_HASH_MANIFEST_ENTRY'
$zeroSha256 = '0' * 64
$utf8 = [Text.UTF8Encoding]::new($false, $true)

function Get-Sha256Hex {
    param(
        [Parameter(Mandatory)]
        [byte[]] $Bytes
    )

    $digest = [Security.Cryptography.SHA256]::HashData($Bytes)
    return [Convert]::ToHexString($digest).ToLowerInvariant()
}

function Resolve-ExactFile {
    param(
        [Parameter(Mandatory)] [string] $Path,
        [Parameter(Mandatory)] [string] $Label
    )

    $resolved = @(Resolve-Path -LiteralPath $Path -ErrorAction Stop)
    if ($resolved.Count -ne 1) {
        throw "$Label must resolve to exactly one filesystem path"
    }

    $item = Get-Item -LiteralPath $resolved[0].ProviderPath -Force
    if ($item.PSIsContainer) {
        throw "$Label must be a file: $($item.FullName)"
    }
    return $item
}

function Get-LockedFileRecord {
    param(
        [Parameter(Mandatory)] [IO.FileInfo] $File,
        [Parameter(Mandatory)] [string] $Role
    )

    $stream = [IO.FileStream]::new(
        $File.FullName,
        [IO.FileMode]::Open,
        [IO.FileAccess]::Read,
        [IO.FileShare]::Read,
        1048576,
        [IO.FileOptions]::SequentialScan
    )
    try {
        $length = $stream.Length
        $sha256 = [Security.Cryptography.SHA256]::Create()
        try {
            $digest = $sha256.ComputeHash($stream)
        } finally {
            $sha256.Dispose()
        }

        return [ordered]@{
            role = $Role
            path = $File.FullName
            bytes = [long] $length
            sha256 = [Convert]::ToHexString($digest).ToLowerInvariant()
        }
    } finally {
        $stream.Dispose()
    }
}

function Convert-FileSpecs {
    param(
        [Parameter(Mandatory)] [string[]] $Specs,
        [Parameter(Mandatory)] [string] $CollectionLabel
    )

    $roles = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $paths = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $records = [Collections.Generic.List[object]]::new()

    foreach ($spec in $Specs) {
        if ([string]::IsNullOrWhiteSpace($spec)) {
            throw "$CollectionLabel contains an empty role=path entry"
        }

        $separator = $spec.IndexOf('=')
        if ($separator -le 0 -or $separator -eq ($spec.Length - 1)) {
            throw "$CollectionLabel entry must use role=path syntax: '$spec'"
        }

        $role = $spec.Substring(0, $separator)
        $path = $spec.Substring($separator + 1)
        if ($role -cnotmatch '^[a-z][a-z0-9._-]{0,63}$') {
            throw "$CollectionLabel role must be lowercase ASCII and 1..64 characters: '$role'"
        }
        if (-not $roles.Add($role)) {
            throw "$CollectionLabel contains duplicate role '$role'"
        }

        $file = Resolve-ExactFile -Path $path -Label "$CollectionLabel '$role'"
        if (-not $paths.Add($file.FullName)) {
            throw "$CollectionLabel contains the same file more than once: '$($file.FullName)'"
        }
        $records.Add((Get-LockedFileRecord -File $file -Role $role))
    }

    if ($records.Count -eq 0) {
        throw "$CollectionLabel must contain at least one role=path entry"
    }
    return @($records)
}

function Copy-LockedTranscript {
    param(
        [Parameter(Mandatory)] [IO.FileInfo] $SourceFile,
        [Parameter(Mandatory)] [string] $Destination,
        [Parameter(Mandatory)] [long] $MaximumBytes
    )

    $input = [IO.FileStream]::new(
        $SourceFile.FullName,
        [IO.FileMode]::Open,
        [IO.FileAccess]::Read,
        [IO.FileShare]::Read,
        1048576,
        [IO.FileOptions]::SequentialScan
    )
    try {
        if ($input.Length -gt $MaximumBytes) {
            throw "raw transcript exceeds MaximumTranscriptBytes: bytes=$($input.Length) maximum=$MaximumBytes"
        }

        $output = [IO.FileStream]::new(
            $Destination,
            [IO.FileMode]::CreateNew,
            [IO.FileAccess]::Write,
            [IO.FileShare]::None,
            1048576,
            [IO.FileOptions]::WriteThrough
        )
        $incrementalHash = [Security.Cryptography.IncrementalHash]::CreateHash(
            [Security.Cryptography.HashAlgorithmName]::SHA256
        )
        try {
            $buffer = [byte[]]::new(1048576)
            $total = 0L
            while (($read = $input.Read($buffer, 0, $buffer.Length)) -gt 0) {
                $total += $read
                if ($total -gt $MaximumBytes) {
                    throw "raw transcript changed beyond MaximumTranscriptBytes while being copied"
                }
                $output.Write($buffer, 0, $read)
                $incrementalHash.AppendData($buffer, 0, $read)
            }
            $output.Flush($true)
            $digest = $incrementalHash.GetHashAndReset()
        } finally {
            $incrementalHash.Dispose()
            $output.Dispose()
        }

        return [ordered]@{
            file = 'raw-transcript.bin'
            originalPath = $SourceFile.FullName
            bytes = [long] $total
            sha256 = [Convert]::ToHexString($digest).ToLowerInvariant()
        }
    } finally {
        $input.Dispose()
    }
}

function Write-NewUtf8File {
    param(
        [Parameter(Mandatory)] [string] $Path,
        [Parameter(Mandatory)] [string] $Text
    )

    $bytes = $utf8.GetBytes($Text)
    $stream = [IO.FileStream]::new(
        $Path,
        [IO.FileMode]::CreateNew,
        [IO.FileAccess]::Write,
        [IO.FileShare]::None,
        65536,
        [IO.FileOptions]::WriteThrough
    )
    try {
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Flush($true)
    } finally {
        $stream.Dispose()
    }
    return Get-Sha256Hex -Bytes $bytes
}

function Get-RequiredProperty {
    param(
        [Parameter(Mandatory)] [psobject] $InputObject,
        [Parameter(Mandatory)] [string] $Name,
        [Parameter(Mandatory)] [string] $Label
    )

    $property = $InputObject.PSObject.Properties[$Name]
    if ($null -eq $property) {
        throw "$Label is missing required property '$Name'"
    }
    return $property.Value
}

function ConvertTo-CanonicalManifestPayloadJson {
    param(
        [Parameter(Mandatory)] [psobject] $Payload,
        [Parameter(Mandatory)] [string] $Label
    )

    $sequenceValue = Get-RequiredProperty -InputObject $Payload -Name 'sequence' -Label $Label
    if ($sequenceValue -is [string]) {
        throw "$Label sequence must be numeric"
    }
    try {
        $sequence = [long] $sequenceValue
    } catch {
        throw "$Label sequence must fit in an Int64"
    }
    if ($sequence -le 0) {
        throw "$Label sequence must be positive"
    }

    $recordedValue = Get-RequiredProperty -InputObject $Payload -Name 'recordedAtUtc' -Label $Label
    if ($recordedValue -is [DateTime]) {
        $recordedAtUtc = $recordedValue.ToUniversalTime().ToString(
            'yyyy-MM-ddTHH:mm:ss.fffffffZ',
            [Globalization.CultureInfo]::InvariantCulture
        )
    } elseif ($recordedValue -is [string] -and $recordedValue -cmatch '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{7}Z$') {
        $recordedAtUtc = $recordedValue
    } else {
        throw "$Label recordedAtUtc must be a seven-digit UTC timestamp"
    }

    $previousEntrySha256 = Get-RequiredProperty -InputObject $Payload -Name 'previousEntrySha256' -Label $Label
    $evidenceSha256 = Get-RequiredProperty -InputObject $Payload -Name 'evidenceSha256' -Label $Label
    $transcriptSha256 = Get-RequiredProperty -InputObject $Payload -Name 'transcriptSha256' -Label $Label
    foreach ($hashField in @(
        [pscustomobject]@{ Name = 'previousEntrySha256'; Value = $previousEntrySha256 },
        [pscustomobject]@{ Name = 'evidenceSha256'; Value = $evidenceSha256 },
        [pscustomobject]@{ Name = 'transcriptSha256'; Value = $transcriptSha256 }
    )) {
        if ($hashField.Value -isnot [string] -or $hashField.Value -cnotmatch '^[0-9a-f]{64}$') {
            throw "$Label $($hashField.Name) must be lowercase SHA-256"
        }
    }

    $failureId = Get-RequiredProperty -InputObject $Payload -Name 'failureId' -Label $Label
    if ($failureId -isnot [string] -or $failureId -cnotmatch '^r2-failure-\d{8}T\d{9}Z-[0-9a-f]{32}$') {
        throw "$Label failureId is not a UTC+GUID R2 failure identifier"
    }

    $evidencePath = Get-RequiredProperty -InputObject $Payload -Name 'evidencePath' -Label $Label
    $transcriptPath = Get-RequiredProperty -InputObject $Payload -Name 'transcriptPath' -Label $Label
    if ($evidencePath -isnot [string] -or $evidencePath -cne "$failureId/failure.json") {
        throw "$Label evidencePath must be the failure bundle's relative JSON path"
    }
    if ($transcriptPath -isnot [string] -or $transcriptPath -cne "$failureId/raw-transcript.bin") {
        throw "$Label transcriptPath must be the failure bundle's relative transcript path"
    }

    $evidenceBytesValue = Get-RequiredProperty -InputObject $Payload -Name 'evidenceBytes' -Label $Label
    $transcriptBytesValue = Get-RequiredProperty -InputObject $Payload -Name 'transcriptBytes' -Label $Label
    if ($evidenceBytesValue -is [string] -or $transcriptBytesValue -is [string]) {
        throw "$Label byte lengths must be numeric"
    }
    try {
        $evidenceBytes = [long] $evidenceBytesValue
        $transcriptBytes = [long] $transcriptBytesValue
    } catch {
        throw "$Label byte lengths must fit in Int64"
    }
    if ($evidenceBytes -le 0 -or $transcriptBytes -lt 0) {
        throw "$Label byte lengths are out of range"
    }

    return [ordered]@{
        sequence = $sequence
        previousEntrySha256 = $previousEntrySha256
        recordedAtUtc = $recordedAtUtc
        failureId = $failureId
        evidencePath = $evidencePath
        evidenceBytes = $evidenceBytes
        evidenceSha256 = $evidenceSha256
        transcriptPath = $transcriptPath
        transcriptBytes = $transcriptBytes
        transcriptSha256 = $transcriptSha256
    } | ConvertTo-Json -Depth 8 -Compress
}

function Read-ManifestState {
    param(
        [Parameter(Mandatory)] [IO.FileStream] $Stream,
        [Parameter(Mandatory)] [string] $OutputRoot
    )

    if ($Stream.Length -gt 67108864) {
        throw "failure hash manifest exceeds the 64 MiB parser bound"
    }
    if ($Stream.Length -eq 0) {
        return [pscustomobject]@{ Count = 0L; LastEntrySha256 = $zeroSha256 }
    }

    if ($Stream.Length -gt [int]::MaxValue) {
        throw "failure hash manifest is too large to parse"
    }
    $bytes = [byte[]]::new([int] $Stream.Length)
    $Stream.Position = 0
    $offset = 0
    while ($offset -lt $bytes.Length) {
        $read = $Stream.Read($bytes, $offset, $bytes.Length - $offset)
        if ($read -le 0) {
            throw "failure hash manifest ended before its declared file length"
        }
        $offset += $read
    }

    $text = $utf8.GetString($bytes)
    if (-not $text.EndsWith("`n", [StringComparison]::Ordinal)) {
        throw "failure hash manifest has a partial final line; refusing to rewrite append-only evidence"
    }

    $lines = $text.Split("`n", [StringSplitOptions]::None)
    $expectedSequence = 1L
    $previous = $zeroSha256
    for ($index = 0; $index -lt ($lines.Length - 1); $index++) {
        $line = $lines[$index]
        if ([string]::IsNullOrWhiteSpace($line)) {
            throw "failure hash manifest contains an empty line at sequence $expectedSequence"
        }

        try {
            $entry = $line | ConvertFrom-Json -Depth 16 -NoEnumerate
        } catch {
            throw "failure hash manifest sequence $expectedSequence is invalid JSON: $($_.Exception.Message)"
        }
        if ($null -eq $entry -or $entry -isnot [pscustomobject]) {
            throw "failure hash manifest sequence $expectedSequence must be one JSON object"
        }

        $schema = Get-RequiredProperty -InputObject $entry -Name 'schema' -Label "manifest sequence $expectedSequence"
        if ($schema -is [string] -or $schema -ne 1) {
            throw "manifest sequence $expectedSequence has an unsupported schema"
        }
        $kind = Get-RequiredProperty -InputObject $entry -Name 'kind' -Label "manifest sequence $expectedSequence"
        if ($kind -isnot [string] -or $kind -cne $manifestKind) {
            throw "manifest sequence $expectedSequence has an unexpected kind"
        }

        $payload = Get-RequiredProperty -InputObject $entry -Name 'payload' -Label "manifest sequence $expectedSequence"
        if ($null -eq $payload -or $payload -isnot [pscustomobject]) {
            throw "manifest sequence $expectedSequence payload must be one JSON object"
        }
        $sequence = Get-RequiredProperty -InputObject $payload -Name 'sequence' -Label "manifest sequence $expectedSequence payload"
        if ($sequence -is [string] -or [long] $sequence -ne $expectedSequence) {
            throw "manifest sequence is not contiguous: expected=$expectedSequence actual=$sequence"
        }
        $payloadPrevious = Get-RequiredProperty -InputObject $payload -Name 'previousEntrySha256' -Label "manifest sequence $expectedSequence payload"
        if ($payloadPrevious -isnot [string] -or $payloadPrevious -cne $previous) {
            throw "manifest sequence $expectedSequence breaks the previous-entry hash chain"
        }

        $declaredEntrySha = Get-RequiredProperty -InputObject $entry -Name 'entrySha256' -Label "manifest sequence $expectedSequence"
        if ($declaredEntrySha -isnot [string] -or $declaredEntrySha -cnotmatch '^[0-9a-f]{64}$') {
            throw "manifest sequence $expectedSequence entrySha256 is not lowercase SHA-256"
        }
        $payloadJson = ConvertTo-CanonicalManifestPayloadJson `
            -Payload $payload `
            -Label "manifest sequence $expectedSequence payload"
        $actualEntrySha = Get-Sha256Hex -Bytes $utf8.GetBytes($payloadJson)
        if ($actualEntrySha -cne $declaredEntrySha) {
            throw "manifest sequence $expectedSequence entry hash mismatch"
        }

        $evidenceFile = Resolve-ExactFile `
            -Path (Join-Path $OutputRoot $payload.evidencePath) `
            -Label "manifest sequence $expectedSequence evidence"
        $transcriptFile = Resolve-ExactFile `
            -Path (Join-Path $OutputRoot $payload.transcriptPath) `
            -Label "manifest sequence $expectedSequence transcript"
        $evidenceRecord = Get-LockedFileRecord -File $evidenceFile -Role 'manifest-evidence'
        $transcriptRecord = Get-LockedFileRecord -File $transcriptFile -Role 'manifest-transcript'
        if ($evidenceRecord.bytes -ne [long] $payload.evidenceBytes -or $evidenceRecord.sha256 -cne $payload.evidenceSha256) {
            throw "manifest sequence $expectedSequence referenced evidence bytes or SHA-256 changed"
        }
        if ($transcriptRecord.bytes -ne [long] $payload.transcriptBytes -or $transcriptRecord.sha256 -cne $payload.transcriptSha256) {
            throw "manifest sequence $expectedSequence referenced transcript bytes or SHA-256 changed"
        }

        $previous = $declaredEntrySha
        $expectedSequence++
    }

    return [pscustomobject]@{
        Count = $expectedSequence - 1
        LastEntrySha256 = $previous
    }
}

function Add-ManifestEntry {
    param(
        [Parameter(Mandatory)] [string] $ManifestPath,
        [Parameter(Mandatory)] [string] $RecordedAtUtc,
        [Parameter(Mandatory)] [string] $FailureId,
        [Parameter(Mandatory)] [string] $EvidenceRelativePath,
        [Parameter(Mandatory)] [string] $EvidenceSha256,
        [Parameter(Mandatory)] [long] $EvidenceBytes,
        [Parameter(Mandatory)] [string] $TranscriptRelativePath,
        [Parameter(Mandatory)] [string] $TranscriptSha256,
        [Parameter(Mandatory)] [long] $TranscriptBytes
    )

    $deadline = [DateTime]::UtcNow.AddSeconds(5)
    $stream = $null
    do {
        try {
            $stream = [IO.FileStream]::new(
                $ManifestPath,
                [IO.FileMode]::OpenOrCreate,
                [IO.FileAccess]::ReadWrite,
                [IO.FileShare]::Read,
                65536,
                [IO.FileOptions]::WriteThrough
            )
        } catch [IO.IOException] {
            if ([DateTime]::UtcNow -ge $deadline) {
                throw "could not acquire the append-only failure manifest within 5 seconds: $($_.Exception.Message)"
            }
            Start-Sleep -Milliseconds 100
        }
    } while ($null -eq $stream)

    try {
        $state = Read-ManifestState `
            -Stream $stream `
            -OutputRoot ([IO.Directory]::GetParent($ManifestPath).FullName)
        $payload = [ordered]@{
            sequence = [long] ($state.Count + 1)
            previousEntrySha256 = $state.LastEntrySha256
            recordedAtUtc = $RecordedAtUtc
            failureId = $FailureId
            evidencePath = $EvidenceRelativePath
            evidenceBytes = $EvidenceBytes
            evidenceSha256 = $EvidenceSha256
            transcriptPath = $TranscriptRelativePath
            transcriptBytes = $TranscriptBytes
            transcriptSha256 = $TranscriptSha256
        }
        $payloadObject = [pscustomobject] $payload
        $payloadJson = ConvertTo-CanonicalManifestPayloadJson `
            -Payload $payloadObject `
            -Label "new manifest sequence $($payload.sequence) payload"
        $entrySha256 = Get-Sha256Hex -Bytes $utf8.GetBytes($payloadJson)
        $entry = [ordered]@{
            schema = 1
            kind = $manifestKind
            payload = $payloadObject
            entrySha256 = $entrySha256
        }
        $line = ($entry | ConvertTo-Json -Depth 10 -Compress) + "`n"
        $lineBytes = $utf8.GetBytes($line)

        $stream.Position = $stream.Length
        $stream.Write($lineBytes, 0, $lineBytes.Length)
        $stream.Flush($true)

        return [pscustomobject]@{
            Sequence = [long] $payload.sequence
            EntrySha256 = $entrySha256
        }
    } finally {
        $stream.Dispose()
    }
}

if ($RunnerExitCode -eq 0) {
    throw 'RunnerExitCode must be non-zero; this helper cannot record successful evidence'
}
if ([string]::IsNullOrWhiteSpace($DiagnosticName)) {
    throw 'DiagnosticName must not be blank'
}
if ([string]::IsNullOrWhiteSpace($Selector)) {
    throw 'Selector must not be blank'
}
if ([string]::IsNullOrWhiteSpace($FailureSummary)) {
    throw 'FailureSummary must not be blank'
}
if ($DiagnosticName.Length -gt 256) {
    throw 'DiagnosticName must not exceed 256 characters'
}
if ($Selector.Length -gt 1024) {
    throw 'Selector must not exceed 1024 characters'
}
if ($FailureSummary.Length -gt 4096) {
    throw 'FailureSummary must not exceed 4096 characters'
}
if ($MutationObserved -and -not $MutationAttempted) {
    throw 'MutationObserved requires MutationAttempted'
}
if (($PackageStateRestorationVerified -or $AndroidUserSetRestorationVerified -or $RestorationVerified) -and -not $RestorationAttempted) {
    throw 'restoration verification flags require RestorationAttempted'
}
if ($RestorationVerified -and (-not $PackageStateRestorationVerified -or -not $AndroidUserSetRestorationVerified)) {
    throw 'RestorationVerified requires both package-state and Android-user-set restoration verification'
}

$transcriptFile = Resolve-ExactFile -Path $RawTranscript -Label 'RawTranscript'
$artifactRecords = @(Convert-FileSpecs -Specs $Artifact -CollectionLabel 'Artifact')
$sourceRecords = @(Convert-FileSpecs -Specs $Source -CollectionLabel 'Source')

$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $outputRoot = Join-Path $repositoryRoot 'build\reports\python\r2-failures'
} else {
    $outputRoot = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputDirectory)
}
$outputRoot = [IO.Path]::GetFullPath($outputRoot)
$null = New-Item -ItemType Directory -Path $outputRoot -Force

$now = [DateTime]::UtcNow
$recordedAtUtc = $now.ToString('yyyy-MM-ddTHH:mm:ss.fffffffZ', [Globalization.CultureInfo]::InvariantCulture)
$failureId = 'r2-failure-{0}-{1}' -f @(
    $now.ToString('yyyyMMddTHHmmssfffZ', [Globalization.CultureInfo]::InvariantCulture),
    [Guid]::NewGuid().ToString('N')
)
$stagingDirectory = Join-Path $outputRoot ('.$failureId.staging')
$finalDirectory = Join-Path $outputRoot $failureId
$stagingCreated = $false

try {
    $null = New-Item -ItemType Directory -Path $stagingDirectory -ErrorAction Stop
    $stagingCreated = $true

    $transcriptRecord = Copy-LockedTranscript `
        -SourceFile $transcriptFile `
        -Destination (Join-Path $stagingDirectory 'raw-transcript.bin') `
        -MaximumBytes $MaximumTranscriptBytes

    $evidence = [ordered]@{
        schema = 1
        kind = $evidenceKind
        status = 'FAILURE_CAPTURED'
        scope = 'R2_DEVICE_DIAGNOSTIC_FAILURE_ONLY'
        failureId = $failureId
        recordedAtUtc = $recordedAtUtc
        diagnostic = [ordered]@{
            name = $DiagnosticName
            selector = $Selector
            exactSerial = $Serial
            runnerExitCode = $RunnerExitCode
            summary = $FailureSummary
        }
        artifacts = @($artifactRecords)
        sources = @($sourceRecords)
        rawTranscript = $transcriptRecord
        deviceMutation = [ordered]@{
            attempted = [bool] $MutationAttempted
            observed = [bool] $MutationObserved
        }
        restoration = [ordered]@{
            attempted = [bool] $RestorationAttempted
            packageStateVerified = [bool] $PackageStateRestorationVerified
            androidUserSetVerified = [bool] $AndroidUserSetRestorationVerified
            overallVerified = [bool] $RestorationVerified
        }
        formalReceiptWritten = $false
        r2DeviceAcceptance = $false
        runtimeRoutingAuthorized = $false
    }
    $evidenceJson = ($evidence | ConvertTo-Json -Depth 12) + "`n"
    $evidenceSha256 = Write-NewUtf8File `
        -Path (Join-Path $stagingDirectory 'failure.json') `
        -Text $evidenceJson

    [IO.Directory]::Move($stagingDirectory, $finalDirectory)
    $stagingCreated = $false

    $evidencePath = Join-Path $finalDirectory 'failure.json'
    $transcriptPath = Join-Path $finalDirectory 'raw-transcript.bin'
    $evidenceFile = Get-Item -LiteralPath $evidencePath -Force
    $publishedTranscript = Get-Item -LiteralPath $transcriptPath -Force
    $publishedEvidenceSha = (Get-FileHash -LiteralPath $evidencePath -Algorithm SHA256).Hash.ToLowerInvariant()
    $publishedTranscriptSha = (Get-FileHash -LiteralPath $transcriptPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($publishedEvidenceSha -cne $evidenceSha256) {
        throw 'published failure evidence hash changed after the atomic directory move'
    }
    if ($publishedTranscriptSha -cne $transcriptRecord.sha256) {
        throw 'published raw transcript hash changed after the atomic directory move'
    }

    $manifestPath = Join-Path $outputRoot 'manifest.jsonl'
    $manifestEntry = Add-ManifestEntry `
        -ManifestPath $manifestPath `
        -RecordedAtUtc $recordedAtUtc `
        -FailureId $failureId `
        -EvidenceRelativePath "$failureId/failure.json" `
        -EvidenceSha256 $publishedEvidenceSha `
        -EvidenceBytes $evidenceFile.Length `
        -TranscriptRelativePath "$failureId/raw-transcript.bin" `
        -TranscriptSha256 $publishedTranscriptSha `
        -TranscriptBytes $publishedTranscript.Length

    [ordered]@{
        schema = 1
        status = 'FAILURE_CAPTURED'
        failureId = $failureId
        evidencePath = $evidencePath
        evidenceSha256 = $publishedEvidenceSha
        transcriptPath = $transcriptPath
        transcriptSha256 = $publishedTranscriptSha
        manifestPath = $manifestPath
        manifestSequence = $manifestEntry.Sequence
        manifestEntrySha256 = $manifestEntry.EntrySha256
        formalReceiptWritten = $false
        r2DeviceAcceptance = $false
    } | ConvertTo-Json -Depth 5
} catch {
    if ($stagingCreated -and [IO.Directory]::Exists($stagingDirectory)) {
        $resolvedStagingParent = [IO.Path]::GetFullPath([IO.Directory]::GetParent($stagingDirectory).FullName)
        if ($resolvedStagingParent -ceq $outputRoot -and [IO.Path]::GetFileName($stagingDirectory).StartsWith('.r2-failure-', [StringComparison]::Ordinal)) {
            [IO.Directory]::Delete($stagingDirectory, $true)
        }
    }
    throw
}
