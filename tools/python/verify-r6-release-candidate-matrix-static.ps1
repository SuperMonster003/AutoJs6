#Requires -Version 7.0

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$files = [ordered]@{
    Runner = Join-Path $PSScriptRoot 'run-r6-release-candidate-matrix.ps1'
    RelayService = Join-Path $repoRoot 'test-apps/python-runtime-api-consumer/src/main/java/org/autojs/plugin/python/runtime/consumer/SessionRelayProbeService.kt'
    ConsumerTest = Join-Path $repoRoot 'test-apps/python-runtime-api-consumer/src/androidTest/java/org/autojs/plugin/python/runtime/consumer/PythonRuntimeSameSignerDifferentUidAndroidTest.kt'
    ConsumerManifest = Join-Path $repoRoot 'test-apps/python-runtime-api-consumer/src/main/AndroidManifest.xml'
    AvailabilityTest = Join-Path $repoRoot 'app/src/androidTest/java/org/autojs/autojs/core/plugin/python/PythonRuntimeR6ProviderAvailabilityInstrumentationTest.kt'
    RelayTest = Join-Path $repoRoot 'app/src/androidTest/java/org/autojs/autojs/core/plugin/python/PythonRuntimeR6SessionRelayInstrumentationTest.kt'
}
foreach ($entry in $files.GetEnumerator()) {
    if (-not (Test-Path -LiteralPath $entry.Value -PathType Leaf)) {
        throw "Missing R6-P3 source ($($entry.Key)): $($entry.Value)"
    }
}

$runnerSource = Get-Content -LiteralPath $files.Runner -Raw
$tokens = $null
$parserErrors = $null
$runnerAst = [Management.Automation.Language.Parser]::ParseInput(
    $runnerSource,
    [ref] $tokens,
    [ref] $parserErrors
)
if (@($parserErrors).Count -ne 0) {
    throw "R6-P3 runner is not valid PowerShell:`n$(@($parserErrors) -join "`n")"
}

$violations = [Collections.Generic.List[string]]::new()
function Require-Text {
    param([Parameter(Mandatory)] [string] $Text, [Parameter(Mandatory)] [string] $Needle, [Parameter(Mandatory)] [string] $Label)
    if (-not $Text.Contains($Needle, [StringComparison]::Ordinal)) { $violations.Add($Label) }
}
function Get-FunctionSource {
    param([Parameter(Mandatory)] [string] $Name)
    $functions = @(
        $runnerAst.FindAll(
            { param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq $Name },
            $true
        )
    )
    if ($functions.Count -ne 1) {
        $violations.Add("runner must define exactly one $Name function")
        return ''
    }
    return $functions[0].Extent.Text
}

foreach ($contract in @(
    '[string] $Serial',
    '[int] $ExpectedApi',
    '[string] $ExpectedAbi',
    '[int[]] $ExpectedUserIds',
    '[string] $BaselinePluginApk',
    '[string] $CandidatePluginApk',
    '[string] $BaselinePluginSha256',
    '[string] $CandidatePluginSha256',
    '[switch] $ConfirmDeviceMutation',
    'Global\AutoJs6PythonR6P3_',
    ').IsReadOnly = $true',
    "'PARTIAL_DIAGNOSTIC'",
    "'BLOCKED'",
    'finalReceiptWritten = $false',
    'stableReleaseAuthorized = $false',
    "'INDEPENDENT_FINAL_RECEIPT_VERIFIER'",
    "'DECLARED_ABI_PACKAGING_AND_ACTUAL_DEVICE_COVERAGE_AGGREGATION'"
)) {
    Require-Text -Text $runnerSource -Needle $contract -Label "runner contract missing: $contract"
}
if ($runnerSource -match '(?i)QV710AF65F|QV770340J7|968e9f18') {
    $violations.Add('runner must not hard-code a device serial')
}
if ($runnerSource -match '(?i)storepass|keypass|keystore|\.jks|sign\s+--') {
    $violations.Add('runner must not hold signing credentials or perform APK signing')
}
if ($runnerSource -match '(?im)\b(?:Set-Content|Out-File|Export-Clixml|Add-Content)\b') {
    $violations.Add('diagnostic runner must not write a final or intermediate receipt')
}

$usersSource = Get-FunctionSource -Name 'Get-DeviceUsers'
foreach ($contract in @(
    '$records.Count -ne $recordMarkers',
    '[int]::TryParse',
    '$uniqueUserIds.Count -ne $userIds.Count',
    '0 -notin $uniqueUserIds'
)) {
    Require-Text -Text $usersSource -Needle $contract -Label "complete user parser missing: $contract"
}
$knownSource = Get-FunctionSource -Name 'Test-PackageKnown'
foreach ($contract in @(
    '$result.ExitCode -ne 0',
    "'^package:[A-Za-z0-9_][A-Za-z0-9._]*$'",
    '$malformed.Count -ne 0',
    '$exactCount -gt 1'
)) {
    Require-Text -Text $knownSource -Needle $contract -Label "fail-closed package-list parser missing: $contract"
}
$stateSource = Get-FunctionSource -Name 'Get-PackageState'
foreach ($contract in @(
    '$result.ExitCode -eq 1 -and [string]::IsNullOrWhiteSpace($result.Text)',
    '$lines.Count -ne $paths.Count',
    'Test-PackageKnown -Package $Package -UserId $userId',
    'Test-PackageKnown -Package $Package'
)) {
    Require-Text -Text $stateSource -Needle $contract -Label "fail-closed package-state parser missing: $contract"
}

$installSource = Get-FunctionSource -Name 'Install-ExactApk'
foreach ($contract in @(
    'Assert-UserInventory',
    'Assert-InstalledArtifact -Artifact $ExpectedCurrentArtifact',
    'Assert-PackageAbsent -Package $Artifact.Package',
    '$script:PackageOwnership[$Artifact.Package] = [pscustomobject]@{',
    "State = 'INSTALL_ATTEMPT'",
    'CurrentArtifact = $ExpectedCurrentArtifact',
    'AttemptArtifact = $Artifact',
    'Assert-InstalledArtifact -Artifact $Artifact',
    "State = 'OWNED'"
)) {
    Require-Text -Text $installSource -Needle $contract -Label "install ownership guard missing: $contract"
}
$ownershipIndex = $installSource.IndexOf("State = 'INSTALL_ATTEMPT'", [StringComparison]::Ordinal)
$installInvokeIndex = $installSource.IndexOf('Invoke-ExactAdb -Arguments $arguments', [StringComparison]::Ordinal)
if ($ownershipIndex -lt 0 -or $installInvokeIndex -lt 0 -or $ownershipIndex -ge $installInvokeIndex) {
    $violations.Add('install-attempt ownership must be established before invoking adb install')
}

$removeSource = Get-FunctionSource -Name 'Remove-RunOwnedPackage'
foreach ($contract in @(
    '$script:PackageOwnership.ContainsKey($Package)',
    'Assert-InstalledArtifactSet -Artifacts @($ownership.AllowedArtifacts)',
    'Assert-UserInventory -Label "immediately before uninstall $Package"',
    '@(''uninstall'', $Package)',
    '$result.ExitCode -ne 0',
    '$result.Text -notmatch',
    'Assert-PackageAbsent -Package $Package',
    '$script:PackageOwnership.Remove($Package)'
)) {
    Require-Text -Text $removeSource -Needle $contract -Label "guarded uninstall contract missing: $contract"
}
$allUninstalls = [regex]::Matches($runnerSource, "@\('uninstall',").Count
if ($allUninstalls -ne 1) {
    $violations.Add("all global uninstalls must use the one guarded path; found $allUninstalls")
}
foreach ($contract in @(
    '[string]::Join('','', $state.InstalledUserIds) -cne ''0''',
    'Get-Sha256 -Path $pulled',
    'Assert-Apk -Label "$Label installed artifact"',
    '-Package $matched.Package -Signer $matched.Signer -AbiMode $matched.AbiMode'
)) {
    Require-Text -Text (Get-FunctionSource -Name 'Assert-InstalledArtifactSet') -Needle $contract `
        -Label "exact installed ownership proof missing: $contract"
}

$phaseContracts = @(
    'Install-ExactApk -Artifact $artifactByLabel[''baseline plugin'']',
    'Install-ExactApk -Artifact $artifactByLabel[''candidate plugin''] -Replace',
    'Remove-RunOwnedPackage -Package $pluginPackage',
    "Invoke-Availability -Expected 'MISSING'",
    'Install-ExactApk -Artifact $artifactByLabel[''candidate plugin'']',
    'Install-ExactApk -Artifact $artifactByLabel[''baseline plugin'']'
)
$lastIndex = -1
foreach ($contract in $phaseContracts) {
    $nextIndex = $runnerSource.IndexOf($contract, $lastIndex + 1, [StringComparison]::Ordinal)
    if ($nextIndex -lt 0) {
        $violations.Add("phase-wise baseline/upgrade/missing/recovery/rollback contract missing: $contract")
    } else {
        $lastIndex = $nextIndex
    }
}

[xml] $manifest = Get-Content -LiteralPath $files.ConsumerManifest -Raw
$namespace = [Xml.XmlNamespaceManager]::new($manifest.NameTable)
$namespace.AddNamespace('android', 'http://schemas.android.com/apk/res/android')
$relayServiceNode = $manifest.SelectSingleNode(
    "/manifest/application/service[@android:name='.SessionRelayProbeService']",
    $namespace
)
if (
    $null -eq $relayServiceNode -or
    $relayServiceNode.GetAttribute('exported', $namespace.LookupNamespace('android')) -cne 'true' -or
    $relayServiceNode.GetAttribute('permission', $namespace.LookupNamespace('android')) -cne 'org.autojs.permission.PLUGIN' -or
    $relayServiceNode.GetAttribute('process', $namespace.LookupNamespace('android')) -cne ':session_relay_probe'
) {
    $violations.Add('consumer manifest does not expose the exact signature-protected isolated relay fixture')
}

$relayService = Get-Content -LiteralPath $files.RelayService -Raw
foreach ($contract in @(
    'if (intent?.action != null) return null',
    'data.enforceInterface(DESCRIPTOR)',
    'session.start()',
    'session.grantOutputCredits(Int.MAX_VALUE)',
    'session.cancel()',
    'session.close()'
)) {
    Require-Text -Text $relayService -Needle $contract -Label "relay service contract missing: $contract"
}
$consumerTest = Get-Content -LiteralPath $files.ConsumerTest -Raw
foreach ($contract in @(
    'fun differentSignerCannotAcquirePluginPermissionOrBindProvider()',
    'PackageManager.PERMISSION_DENIED',
    'context.checkSelfPermission(PLUGIN_PERMISSION)',
    'assertNull("Different-signer probe must use an action-null exact component", intent.action)',
    'fail("Different-signer caller unexpectedly passed the signature-permission bind")'
)) {
    Require-Text -Text $consumerTest -Needle $contract -Label "different-signer test contract missing: $contract"
}
if ($consumerTest -match '(?i)storepass|keypass|keystore|\.jks') {
    $violations.Add('different-signer test source must not contain signing material')
}
$availabilityTest = Get-Content -LiteralPath $files.AvailabilityTest -Raw
foreach ($contract in @(
    'productionClientMatchesExplicitProviderAvailabilityPhase()',
    'Expected.MISSING',
    'PythonRuntimeHostError.PLUGIN_MISSING',
    'Expected.DISABLED',
    'PythonRuntimeHostError.PLUGIN_DISABLED',
    'Expected.AVAILABLE'
)) {
    Require-Text -Text $availabilityTest -Needle $contract -Label "availability test contract missing: $contract"
}
$relayTest = Get-Content -LiteralPath $files.RelayTest -Raw
foreach ($contract in @(
    'alternateUidCannotMutateRelayedSessionAndOwnerStillCompletesExactlyOnce()',
    'relay.transact(RELAY_TRANSACTION, data, reply, 0)',
    'terminal.await(RELAY_SETTLE_MILLIS',
    'requireNotNull(session).start()',
    'callbackUids.all { it == installed.packageUid }'
)) {
    Require-Text -Text $relayTest -Needle $contract -Label "session relay test contract missing: $contract"
}

if ($violations.Count -ne 0) {
    throw "R6-P3 static validation failed:`n$($violations -join "`n")"
}

[ordered]@{
    schema = 1
    status = 'PASS'
    scope = 'R6_P3_SOURCE_AND_RUNNER_STATIC_ONLY'
    runnerPowerShellAst = 'PASS'
    exactSerialRequired = $true
    sharedDeviceOwnershipGuards = 'PASS'
    finalReceiptWritten = $false
    gradleExecuted = $false
    adbExecuted = $false
} | ConvertTo-Json -Depth 4
