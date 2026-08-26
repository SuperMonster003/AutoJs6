#Requires -Version 7.0

[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $InputReport,
    [Parameter(Mandatory)] [string] $OutputPath,
    [switch] $Overwrite,
    [switch] $FailOnNotPromoted
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$expectedSchema = 'autojs.dex-compiler.r5-performance/v1'
$expectedClassification = 'R5_BASELINE'
$expectedCorpora = @('java8-1x8', 'java8-128x8', 'java8-2048x8')
$minimumWarmupSamplesPerCell = 2
$minimumMeasuredSamplesPerCell = 10
$maximumColdAddedP95Percent = 25.0
$minimumColdAddedP95AllowanceMillis = 1000.0
$maximumCacheHitAddedP95Millis = 100.0
$maximumCombinedPssRegressionPercent = 20.0
$requiredCacheStateVerifiedRate = 1.0
$policySource = 'docs/dev/dex-compiler-plugin-evaluation.md'

function Resolve-ExactFile {
    param([Parameter(Mandatory)] [string] $Path, [Parameter(Mandatory)] [string] $Label)

    $resolved = @(Resolve-Path -LiteralPath $Path -ErrorAction Stop)
    if ($resolved.Count -ne 1) { throw "$Label must resolve to exactly one file" }
    $item = Get-Item -LiteralPath $resolved[0].ProviderPath -Force
    if ($item.PSIsContainer) { throw "$Label is not a file: $($item.FullName)" }
    return $item.FullName
}

function Assert-Report {
    param([Parameter(Mandatory)] [bool] $Condition, [Parameter(Mandatory)] [string] $Message)
    if (-not $Condition) { throw "Invalid R5 performance report: $Message" }
}

function Get-RequiredProperty {
    param(
        [Parameter(Mandatory)] [object] $Object,
        [Parameter(Mandatory)] [string] $Property,
        [Parameter(Mandatory)] [string] $Label
    )

    $entry = $Object.PSObject.Properties[$Property]
    Assert-Report ($null -ne $entry) "$Label is missing $Property"
    return $entry.Value
}

function Get-OnlySummary {
    param(
        [Parameter(Mandatory)] [object[]] $Summaries,
        [Parameter(Mandatory)] [string] $CorpusId,
        [Parameter(Mandatory)] [string] $Route,
        [Parameter(Mandatory)] [string] $CacheState
    )

    $matches = @(
        $Summaries | Where-Object {
            $_.corpusId -ceq $CorpusId -and
            $_.route -ceq $Route -and
            $_.cacheState -ceq $CacheState
        }
    )
    Assert-Report ($matches.Count -eq 1) `
        "expected one summary for $CorpusId/$Route/$CacheState, found $($matches.Count)"
    return $matches[0]
}

function Get-OnlyArtifactValue {
    param(
        [Parameter(Mandatory)] [object] $Summary,
        [Parameter(Mandatory)] [string] $Property
    )

    $values = @($Summary.$Property)
    Assert-Report ($values.Count -eq 1) `
        "$($Summary.corpusId)/$($Summary.route)/$($Summary.cacheState) has non-deterministic $Property"
    return $values[0]
}

$inputPath = Resolve-ExactFile -Path $InputReport -Label 'input report'
$inputItem = Get-Item -LiteralPath $inputPath
$inputSha256 = (Get-FileHash -LiteralPath $inputPath -Algorithm SHA256).Hash.ToLowerInvariant()
$report = Get-Content -Raw -LiteralPath $inputPath | ConvertFrom-Json -Depth 100

Assert-Report ($report.schema -ceq $expectedSchema) "schema is $($report.schema)"
Assert-Report ($report.status -ceq 'PASS') "run status is $($report.status)"
Assert-Report ($report.benchmarkClassification -ceq $expectedClassification) `
    "benchmark classification is $($report.benchmarkClassification)"
Assert-Report ($report.contract.percentileMethod -ceq 'R7_LINEAR_INTERPOLATION') `
    "percentile method is $($report.contract.percentileMethod)"
Assert-Report ($report.contract.warmupSamplesPerCell -ge $minimumWarmupSamplesPerCell) `
    "requires at least $minimumWarmupSamplesPerCell warmups per cell"
Assert-Report ($report.contract.measuredSamplesPerCell -ge $minimumMeasuredSamplesPerCell) `
    "requires at least $minimumMeasuredSamplesPerCell measured samples per cell"
$builtInCacheHitInitialization = Get-RequiredProperty -Object $report.contract `
    -Property 'builtInCacheHitInitialization' -Label 'benchmark contract'
Assert-Report ($builtInCacheHitInitialization -ceq `
    'PREWARMED_LOADER_CACHE_PRESERVED_ON_CONSTRUCTION') `
    "built-in cache-hit initialization is $builtInCacheHitInitialization"
$builtInCacheHitProof = Get-RequiredProperty -Object $report.contract `
    -Property 'builtInCacheHitProof' -Label 'benchmark contract'
Assert-Report ($builtInCacheHitProof -ceq `
    'ARTIFACT_NAME_SIZE_SHA256_AND_LAST_MODIFIED_UNCHANGED') `
    "built-in cache-hit proof is $builtInCacheHitProof"
Assert-Report ($report.packageRestoration -ceq 'EXACT_INITIAL_APK_BYTES_RESTORED_OR_RUN_OWNED_PACKAGE_REMOVED') `
    "package restoration is $($report.packageRestoration)"
Assert-Report (@($report.cleanupErrors).Count -eq 0) 'cleanup errors are present'
Assert-Report (@($report.restorationErrors).Count -eq 0) 'restoration errors are present'
Assert-Report ($null -eq $report.error) 'the source run contains an error'
Assert-Report ($null -eq $report.recoverySnapshotPath) 'a recovery snapshot was retained'
Assert-Report (-not [bool] $report.claims.performanceAdvantageClaimed) `
    'the raw descriptive report claims a performance advantage'
Assert-Report (-not [bool] $report.claims.defaultEnableAuthorized) `
    'the raw descriptive report authorizes default enablement'
Assert-Report (-not [bool] $report.claims.statisticalInferenceClaimed) `
    'the raw descriptive report claims statistical inference'

$artifacts = @($report.artifacts)
Assert-Report ($artifacts.Count -eq 3) "artifact array has $($artifacts.Count) entries"
$expectedPackages = @(
    'io.github.supermonster003.autojs6.plugin.dexcompiler',
    'org.autojs.autojs6',
    'org.autojs.autojs6.test'
)
$actualPackages = @($artifacts | ForEach-Object packageName | Sort-Object)
Assert-Report (($actualPackages -join "`n") -ceq (($expectedPackages | Sort-Object) -join "`n")) `
    "artifact package set is $($actualPackages -join ', ')"
$signers = @($artifacts | ForEach-Object signerCertificateSha256 | Sort-Object -Unique)
Assert-Report ($signers.Count -eq 1 -and $signers[0] -cmatch '^[0-9a-f]{64}$') `
    'the three benchmark APKs do not have one shared signer certificate'
$pluginArtifact = @(
    $artifacts | Where-Object packageName -ceq 'io.github.supermonster003.autojs6.plugin.dexcompiler'
)
Assert-Report ($pluginArtifact.Count -eq 1 -and -not [bool] $pluginArtifact[0].debuggable) `
    'the benchmark plugin must be a non-debuggable release APK'
Assert-Report ($report.environment.deviceBefore.exactSerial -ceq $report.environment.deviceAfter.exactSerial) `
    'the device serial changed during the campaign'
Assert-Report (
    $report.environment.deviceBefore.buildFingerprintSha256 -ceq
        $report.environment.deviceAfter.buildFingerprintSha256
) 'the device build fingerprint changed during the campaign'

$actualCorpora = @($report.corpora | ForEach-Object { $_.corpusId } | Sort-Object)
$expectedSortedCorpora = @($expectedCorpora | Sort-Object)
Assert-Report (($actualCorpora -join "`n") -ceq ($expectedSortedCorpora -join "`n")) `
    "corpus set is $($actualCorpora -join ', ')"
foreach ($corpus in @($report.corpora)) {
    Assert-Report ([bool] $corpus.deterministicRegeneration) "$($corpus.corpusId) did not regenerate deterministically"
    Assert-Report ($corpus.inputSha256 -cmatch '^[0-9a-f]{64}$') "$($corpus.corpusId) has an invalid SHA-256"
}

$samples = @($report.samples)
$summaries = @($report.summaries)
$expectedTimedSamples = $expectedCorpora.Count * 4 *
    ($report.contract.warmupSamplesPerCell + $report.contract.measuredSamplesPerCell)
Assert-Report ($report.expectedTimedSamples -eq $expectedTimedSamples) `
    "expectedTimedSamples is $($report.expectedTimedSamples), calculated $expectedTimedSamples"
Assert-Report ($report.completedTimedSamples -eq $expectedTimedSamples) `
    "completedTimedSamples is $($report.completedTimedSamples), expected $expectedTimedSamples"
Assert-Report ($samples.Count -eq $expectedTimedSamples) `
    "sample array has $($samples.Count) entries, expected $expectedTimedSamples"
Assert-Report ($summaries.Count -eq ($expectedCorpora.Count * 4)) `
    "summary array has $($summaries.Count) entries"

$measuredSamples = @($samples | Where-Object phase -ceq 'MEASURED')
$expectedMeasuredSamples = $expectedCorpora.Count * 4 * $report.contract.measuredSamplesPerCell
Assert-Report ($measuredSamples.Count -eq $expectedMeasuredSamples) `
    "measured sample count is $($measuredSamples.Count), expected $expectedMeasuredSamples"

$routeVerifiedCount = 0
$cacheStateVerifiedCount = 0
foreach ($sample in $measuredSamples) {
    $expectedAdoptedPath = if ($sample.route -ceq 'PLUGIN' -and $sample.cacheState -ceq 'CACHE_HIT') {
        'CACHE_HIT'
    } else {
        $sample.route
    }
    if ($sample.device.adoptedPath -ceq $expectedAdoptedPath) { $routeVerifiedCount++ }
    if ([bool] $sample.device.cacheStateVerified) { $cacheStateVerifiedCount++ }

    $sampleLabel = "$($sample.corpusId)/$($sample.route)/$($sample.cacheState)"
    $expectedHit = $sample.cacheState -ceq 'CACHE_HIT'
    $actualExpectedHit = Get-RequiredProperty -Object $sample.device -Property 'cacheExpectedHit' `
        -Label $sampleLabel
    Assert-Report ([bool] $actualExpectedHit -eq $expectedHit) `
        "$sampleLabel has a mismatched cache-hit expectation"

    $expectedVerification = switch ("$($sample.route)/$($sample.cacheState)") {
        'BUILTIN/COLD' { 'EMPTY_FRESH_LOADER_CACHE_AND_BUILTIN_ROUTE' }
        'BUILTIN/CACHE_HIT' { 'PREEXISTING_ARTIFACT_NAME_SIZE_AND_SHA256_UNCHANGED' }
        'PLUGIN/COLD' { 'PLUGIN_ROUTE_AND_EXACT_GENERATION_INVALIDATED' }
        'PLUGIN/CACHE_HIT' { 'DIAGNOSTIC_CACHE_HIT_ROUTE' }
        default { throw "Invalid R5 performance report: unexpected cell $sampleLabel" }
    }
    $actualVerification = Get-RequiredProperty -Object $sample.device -Property 'cacheVerification' `
        -Label $sampleLabel
    Assert-Report ($actualVerification -ceq $expectedVerification) `
        "$sampleLabel cache proof is $actualVerification"

    $expectedInitialization = if ($sample.route -ceq 'BUILTIN' -and $sample.cacheState -ceq 'CACHE_HIT') {
        'PRESERVED_FOR_PREEXISTING_ARTIFACT'
    } else {
        'CLEARED_ON_CONSTRUCTION'
    }
    $actualInitialization = Get-RequiredProperty -Object $sample.device `
        -Property 'loaderCacheInitialization' -Label $sampleLabel
    Assert-Report ($actualInitialization -ceq $expectedInitialization) `
        "$sampleLabel loader-cache initialization is $actualInitialization"
    if ($sample.route -ceq 'BUILTIN' -and $sample.cacheState -ceq 'CACHE_HIT') {
        $modifiedDelta = Get-RequiredProperty -Object $sample.device `
            -Property 'cacheArtifactLastModifiedDeltaMillis' -Label $sampleLabel
        Assert-Report ([long] $modifiedDelta -eq 0L) `
            "$sampleLabel changed the pre-existing artifact modification time by $modifiedDelta ms"
    }

    Assert-Report ([bool] $sample.externalMemory.hostObserved) `
        "host process was not observed for $($sample.corpusId)/$($sample.route)/$($sample.cacheState)"
    Assert-Report ($sample.externalMemory.hostPeakPssKb -gt 0) `
        "host PSS was not positive for $($sample.corpusId)/$($sample.route)/$($sample.cacheState)"
    $providerExpected = $sample.route -ceq 'PLUGIN'
    Assert-Report ($sample.externalMemory.providerObserved -eq $providerExpected) `
        "provider observation mismatch for $($sample.corpusId)/$($sample.route)/$($sample.cacheState)"
    if ($providerExpected) {
        Assert-Report ($sample.externalMemory.providerPeakPssKb -gt 0) `
            "provider PSS was not positive for $($sample.corpusId)/$($sample.cacheState)"
    } else {
        Assert-Report ($sample.externalMemory.providerPeakPssKb -eq 0) `
            "provider PSS was non-zero for built-in $($sample.corpusId)/$($sample.cacheState)"
    }
}
Assert-Report ($routeVerifiedCount -eq $measuredSamples.Count) `
    "route adoption verified for $routeVerifiedCount/$($measuredSamples.Count) measured samples"
Assert-Report ($cacheStateVerifiedCount -eq $measuredSamples.Count) `
    "cache state verified for $cacheStateVerifiedCount/$($measuredSamples.Count) measured samples"

$cells = [System.Collections.Generic.List[object]]::new()
$failedGates = [System.Collections.Generic.List[object]]::new()
$latencyPassCount = 0
$memoryPassCount = 0
$cachePassCount = 0
$outputPassCount = 0

foreach ($corpusId in $expectedCorpora) {
    foreach ($cacheState in @('COLD', 'CACHE_HIT')) {
        $builtIn = Get-OnlySummary -Summaries $summaries -CorpusId $corpusId -Route 'BUILTIN' `
            -CacheState $cacheState
        $plugin = Get-OnlySummary -Summaries $summaries -CorpusId $corpusId -Route 'PLUGIN' `
            -CacheState $cacheState

        foreach ($summary in @($builtIn, $plugin)) {
            Assert-Report ($summary.sampleCount -eq $report.contract.measuredSamplesPerCell) `
                "$($summary.corpusId)/$($summary.route)/$($summary.cacheState) sample count is $($summary.sampleCount)"
        }

        $builtInP95Millis = [double] $builtIn.elapsedMillis.p95
        $pluginP95Millis = [double] $plugin.elapsedMillis.p95
        $addedP95Millis = $pluginP95Millis - $builtInP95Millis
        $latencyAllowanceMillis = if ($cacheState -ceq 'COLD') {
            [Math]::Max(
                $minimumColdAddedP95AllowanceMillis,
                $builtInP95Millis * ($maximumColdAddedP95Percent / 100.0)
            )
        } else {
            $maximumCacheHitAddedP95Millis
        }
        $latencyPassed = $addedP95Millis -le $latencyAllowanceMillis
        if ($latencyPassed) { $latencyPassCount++ }

        $builtInP95CombinedPssKb = [double] $builtIn.combinedPeakPssKb.p95
        $pluginP95CombinedPssKb = [double] $plugin.combinedPeakPssKb.p95
        $maximumPluginP95CombinedPssKb = $builtInP95CombinedPssKb *
            (1.0 + ($maximumCombinedPssRegressionPercent / 100.0))
        $combinedPssRegressionPercent = if ($builtInP95CombinedPssKb -eq 0.0) {
            $null
        } else {
            (($pluginP95CombinedPssKb - $builtInP95CombinedPssKb) / $builtInP95CombinedPssKb) * 100.0
        }
        $memoryPassed = $pluginP95CombinedPssKb -le $maximumPluginP95CombinedPssKb
        if ($memoryPassed) { $memoryPassCount++ }

        $builtInCachePassed = [double] $builtIn.cacheStateVerifiedRate -eq $requiredCacheStateVerifiedRate
        $pluginCachePassed = [double] $plugin.cacheStateVerifiedRate -eq $requiredCacheStateVerifiedRate
        $cachePassed = $builtInCachePassed -and $pluginCachePassed
        if ($cachePassed) { $cachePassCount++ }

        $builtInSize = Get-OnlyArtifactValue -Summary $builtIn -Property 'outputSizeBytes'
        $builtInSha = Get-OnlyArtifactValue -Summary $builtIn -Property 'outputSha256'
        $pluginSize = Get-OnlyArtifactValue -Summary $plugin -Property 'outputSizeBytes'
        $pluginSha = Get-OnlyArtifactValue -Summary $plugin -Property 'outputSha256'
        $outputPassed =
            $builtInSha -cmatch '^[0-9a-f]{64}$' -and
            $pluginSha -cmatch '^[0-9a-f]{64}$' -and
            [long] $builtInSize -gt 0 -and
            [long] $pluginSize -gt 0
        if ($outputPassed) { $outputPassCount++ }

        $cellPassed = $latencyPassed -and $memoryPassed -and $cachePassed -and $outputPassed
        if (-not $latencyPassed) {
            $failedGates.Add([pscustomobject][ordered]@{
                corpusId = $corpusId
                cacheState = $cacheState
                gate = 'P95_LATENCY'
                actual = $addedP95Millis
                maximum = $latencyAllowanceMillis
                unit = 'ms_added_over_builtin'
            })
        }
        if (-not $memoryPassed) {
            $failedGates.Add([pscustomobject][ordered]@{
                corpusId = $corpusId
                cacheState = $cacheState
                gate = 'P95_COMBINED_PSS'
                actual = $combinedPssRegressionPercent
                maximum = $maximumCombinedPssRegressionPercent
                unit = 'percent_regression_over_builtin'
            })
        }
        if (-not $cachePassed) {
            $failedGates.Add([pscustomobject][ordered]@{
                corpusId = $corpusId
                cacheState = $cacheState
                gate = 'CACHE_STATE_VERIFICATION_RATE'
                actual = [Math]::Min(
                    [double] $builtIn.cacheStateVerifiedRate,
                    [double] $plugin.cacheStateVerifiedRate
                )
                minimum = $requiredCacheStateVerifiedRate
                unit = 'ratio'
            })
        }
        if (-not $outputPassed) {
            $failedGates.Add([pscustomobject][ordered]@{
                corpusId = $corpusId
                cacheState = $cacheState
                gate = 'OUTPUT_DETERMINISM'
                actual = $false
                required = $true
                unit = 'boolean'
            })
        }

        $cells.Add([pscustomobject][ordered]@{
            corpusId = $corpusId
            cacheState = $cacheState
            measuredSamplesPerRoute = [int] $builtIn.sampleCount
            latency = [pscustomobject][ordered]@{
                builtInP95Millis = $builtInP95Millis
                pluginP95Millis = $pluginP95Millis
                pluginAddedP95Millis = $addedP95Millis
                maximumAddedP95Millis = $latencyAllowanceMillis
                passed = $latencyPassed
            }
            combinedPss = [pscustomobject][ordered]@{
                builtInP95Kb = $builtInP95CombinedPssKb
                pluginP95Kb = $pluginP95CombinedPssKb
                pluginRegressionPercent = $combinedPssRegressionPercent
                maximumRegressionPercent = $maximumCombinedPssRegressionPercent
                passed = $memoryPassed
            }
            cacheStateVerification = [pscustomobject][ordered]@{
                builtInRate = [double] $builtIn.cacheStateVerifiedRate
                pluginRate = [double] $plugin.cacheStateVerifiedRate
                requiredRate = $requiredCacheStateVerifiedRate
                passed = $cachePassed
            }
            deterministicOutput = [pscustomobject][ordered]@{
                builtInSizeBytes = [long] $builtInSize
                builtInSha256 = $builtInSha
                pluginSizeBytes = [long] $pluginSize
                pluginSha256 = $pluginSha
                passed = $outputPassed
            }
            passed = $cellPassed
        })
    }
}

$cacheHitMeasuredSamples = @($measuredSamples | Where-Object cacheState -ceq 'CACHE_HIT')
$pluginCacheHitSamples = @($cacheHitMeasuredSamples | Where-Object route -ceq 'PLUGIN')
$verifiedCacheHitCount = @($cacheHitMeasuredSamples | Where-Object { $_.device.cacheStateVerified }).Count
$adoptedPluginCacheHitCount = @(
    $pluginCacheHitSamples | Where-Object { $_.device.adoptedPath -ceq 'CACHE_HIT' }
).Count
$cellCount = $cells.Count
$promoted = $failedGates.Count -eq 0
$decision = if ($promoted) { 'PROMOTED' } else { 'NOT_PROMOTED' }

$evaluation = [pscustomobject][ordered]@{
    schema = 'autojs.dex-compiler.r5-performance-evaluation/v1'
    evaluatedAtUtc = [DateTimeOffset]::UtcNow.ToString('o')
    decision = $decision
    sourceReport = [pscustomobject][ordered]@{
        path = $inputPath
        schema = $report.schema
        campaignId = $report.campaignId
        benchmarkClassification = $report.benchmarkClassification
        sizeBytes = $inputItem.Length
        sha256 = $inputSha256
    }
    environment = [pscustomobject][ordered]@{
        deviceSerial = $report.environment.deviceBefore.exactSerial
        deviceModel = $report.environment.deviceBefore.model
        deviceApiLevel = $report.environment.deviceBefore.apiLevel
        devicePrimaryAbi = $report.environment.deviceBefore.primaryAbi
        deviceBuildFingerprintSha256 = $report.environment.deviceBefore.buildFingerprintSha256
        javaVersion = $report.environment.javaVersion
        sharedSignerCertificateSha256 = $signers[0]
    }
    policy = [pscustomobject][ordered]@{
        source = $policySource
        percentileMethod = 'R7_LINEAR_INTERPOLATION'
        minimumWarmupSamplesPerCell = $minimumWarmupSamplesPerCell
        minimumMeasuredSamplesPerCell = $minimumMeasuredSamplesPerCell
        coldLatency = [pscustomobject][ordered]@{
            maximumAddedP95Percent = $maximumColdAddedP95Percent
            minimumAddedP95AllowanceMillis = $minimumColdAddedP95AllowanceMillis
            rule = 'pluginP95 <= builtInP95 + max(25_percent_of_builtInP95, 1000_ms)'
        }
        cacheHitLatency = [pscustomobject][ordered]@{
            maximumAddedP95Millis = $maximumCacheHitAddedP95Millis
            rule = 'pluginP95 <= builtInP95 + 100_ms'
        }
        combinedPss = [pscustomobject][ordered]@{
            maximumP95RegressionPercent = $maximumCombinedPssRegressionPercent
            rule = 'pluginCombinedPssP95 <= builtInCombinedPssP95 * 1.20'
        }
        cacheStateVerifiedRate = [pscustomobject][ordered]@{
            required = $requiredCacheStateVerifiedRate
            rule = 'all_measured_samples_must_prove_requested_cache_state_and_adopted_route'
        }
    }
    integrity = [pscustomobject][ordered]@{
        sourceRunStatus = $report.status
        exactPackageRestoration = $true
        measuredSamples = $measuredSamples.Count
        routeVerifiedSamples = $routeVerifiedCount
        routeVerifiedRate = $routeVerifiedCount / [double] $measuredSamples.Count
        cacheStateVerifiedSamples = $cacheStateVerifiedCount
        cacheStateVerifiedRate = $cacheStateVerifiedCount / [double] $measuredSamples.Count
        cacheHitMeasuredSamples = $cacheHitMeasuredSamples.Count
        cacheHitVerifiedSamples = $verifiedCacheHitCount
        cacheHitVerifiedRate = $verifiedCacheHitCount / [double] $cacheHitMeasuredSamples.Count
        pluginCacheHitMeasuredSamples = $pluginCacheHitSamples.Count
        pluginCacheHitAdoptedSamples = $adoptedPluginCacheHitCount
        pluginCacheHitAdoptedRate = $adoptedPluginCacheHitCount / [double] $pluginCacheHitSamples.Count
    }
    gateSummary = [pscustomobject][ordered]@{
        cellCount = $cellCount
        latencyPassed = $latencyPassCount
        combinedPssPassed = $memoryPassCount
        cacheStatePassed = $cachePassCount
        deterministicOutputPassed = $outputPassCount
        failedGateCount = $failedGates.Count
    }
    cells = @($cells)
    failedGates = @($failedGates)
    claims = [pscustomobject][ordered]@{
        performancePromotionPassed = $promoted
        performanceAdvantageClaimAuthorized = $false
        defaultEnableAuthorized = $false
        note = if ($promoted) {
            'The R5.2 performance gate passed, but performance advantage and default enablement require separate authorization.'
        } else {
            'At least one R5.2 promotion gate failed; performance advantage and default enablement remain unauthorized.'
        }
    }
}

$fullOutputPath = [IO.Path]::GetFullPath($OutputPath)
if ([StringComparer]::OrdinalIgnoreCase.Equals($inputPath, $fullOutputPath)) {
    throw 'OutputPath must not overwrite the raw input report'
}
if ((Test-Path -LiteralPath $fullOutputPath) -and -not $Overwrite) {
    throw "Output already exists; pass -Overwrite explicitly: $fullOutputPath"
}
$outputDirectory = Split-Path -Parent $fullOutputPath
if ([string]::IsNullOrWhiteSpace($outputDirectory)) { $outputDirectory = (Get-Location).Path }
[IO.Directory]::CreateDirectory($outputDirectory) | Out-Null
$json = $evaluation | ConvertTo-Json -Depth 20
[IO.File]::WriteAllText($fullOutputPath, $json + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))

Write-Output "R5.2 evaluation: $fullOutputPath"
Write-Output "Decision: $decision; latency: $latencyPassCount/$cellCount; combined PSS: $memoryPassCount/$cellCount; cache: $cachePassCount/$cellCount"
foreach ($failedGate in $failedGates) {
    $boundaryLabel = if ($null -ne $failedGate.PSObject.Properties['maximum']) {
        'maximum'
    } elseif ($null -ne $failedGate.PSObject.Properties['minimum']) {
        'minimum'
    } else {
        'required'
    }
    $boundaryValue = $failedGate.$boundaryLabel
    Write-Output ("Failed: {0}/{1} {2}: actual={3}, {4}={5} {6}" -f
        $failedGate.corpusId,
        $failedGate.cacheState,
        $failedGate.gate,
        $failedGate.actual,
        $boundaryLabel,
        $boundaryValue,
        $failedGate.unit)
}
if (-not $promoted -and $FailOnNotPromoted) { exit 2 }
