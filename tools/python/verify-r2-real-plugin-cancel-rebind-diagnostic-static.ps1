#Requires -Version 7.0

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$runner = Join-Path $PSScriptRoot 'run-r2-real-plugin-cancel-rebind-diagnostic.ps1'
$testSource = Join-Path `
    $repositoryRoot `
    'app/src/androidTest/java/org/autojs/autojs/core/plugin/python/PythonRuntimeRealPluginCancelRebindDiagnosticTest.kt'
foreach ($requiredFile in @($runner, $testSource)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Missing R2 cancel/rebind diagnostic source: $requiredFile"
    }
}

$runnerSource = Get-Content -LiteralPath $runner -Raw
$test = Get-Content -LiteralPath $testSource -Raw

$parserTokens = $null
$parserErrors = $null
$null = [Management.Automation.Language.Parser]::ParseInput(
    $runnerSource,
    [ref] $parserTokens,
    [ref] $parserErrors
)
if (@($parserErrors).Count -ne 0) {
    throw "R2 cancel/rebind runner is not valid PowerShell:`n$(@($parserErrors) -join "`n")"
}

$requiredRunnerContracts = @(
    '$expectedSerial = ''QV710AF65F''',
    '$expectedDeviceAbi = ''arm64-v8a''',
    '$expectedSignerSha256 = ''2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8''',
    '[string] $HostSha256',
    '[string] $TestSha256',
    '[string] $PluginSha256',
    '[switch] $ConfirmDeviceMutation',
    '$PSNativeCommandUseErrorActionPreference = $false',
    '''org.autojs.autojs6''',
    '''org.autojs.autojs6.test''',
    '''io.github.supermonster003.autojs6.plugin.python.runtime''',
    '''org.autojs.autojs.core.plugin.python.PythonRuntimeRealPluginCancelRebindDiagnosticTest''',
    '''cancelAfterStartedKillsOldBinderThenExactRebindRunsFiniteRequestOnce''',
    '''autojs.python.r2.cancelRebind.enabled''',
    '@(''-s'', $script:ExactSerial) + $AdbArguments',
    '@(''install'', ''--user'', ''0'', ''-t'', $Artifact.Path)',
    '@(''shell'', ''pm'', ''list'', ''users'')',
    '@(''shell'', ''pm'', ''path'', ''--user'', "$userId", $PackageName)',
    '@(''shell'', ''pm'', ''list'', ''packages'', ''--user'', "$userId", $PackageName)',
    '@(''shell'', ''pm'', ''list'', ''packages'', ''-u'', $PackageName)',
    '@(''uninstall'', $Artifact.PackageName)',
    'Get-ExactDeviceUserIds',
    '$frozenUserIds = @(Get-ExactDeviceUserIds)',
    'Assert-DeviceUserInventoryUnchanged -FrozenUserIds $frozenUserIds',
    'Assert-FullyAbsentPackageState',
    'Assert-FullyAbsentPackageState -State $preMutationState',
    'GlobalRecordCount = $knownCount',
    'Install-ExactArtifact -Artifact $hostArtifact',
    'Install-ExactArtifact -Artifact $pluginArtifact',
    'Install-ExactArtifact -Artifact $testArtifact',
    '''PARTIAL_DIAGNOSTIC''',
    '''R2_REAL_CPYTHON_CANCEL_DEATH_REBIND_SINGLE_CASE_DIAGNOSTIC_ONLY''',
    '''HARNESS_DOES_NOT_REPLAY_DISPATCHED_REQUEST_ID''',
    '''PRODUCTION_HOST_AUTOMATIC_REPLAY_POLICY''',
    'packageInstallationRestoration = ''VERIFIED_ABSENT_ALL_FROZEN_USERS_AND_GLOBAL''',
    'appDataSnapshotTaken = $false',
    'acceptanceReceiptWritten = $false',
    'r2DeviceAcceptance = $false',
    'OK \(1 test\)',
    'INSTRUMENTATION_STATUS_CODE:\s*-3',
    'Get-ApkSignerDigests',
    'Get-ApkNativeAbis',
    'New-ImmutableArtifactSnapshot',
    'Copy-Item -LiteralPath $Artifact.Path -Destination $snapshotPath -ErrorAction Stop',
    '$snapshotItem.IsReadOnly = $true',
    '$sourceHostArtifact = Assert-LocalArtifact',
    '$sourcePluginArtifact = Assert-LocalArtifact',
    '$sourceTestArtifact = Assert-LocalArtifact',
    '$hostArtifact = New-ImmutableArtifactSnapshot',
    '$pluginArtifact = New-ImmutableArtifactSnapshot',
    '$testArtifact = New-ImmutableArtifactSnapshot',
    'Assert-InstalledArtifact',
    'Remove-OwnedArtifact'
)
$missingRunnerContracts = @(
    $requiredRunnerContracts |
        Where-Object { -not $runnerSource.Contains($_, [StringComparison]::Ordinal) }
)
if ($missingRunnerContracts.Count -gt 0) {
    throw "R2 cancel/rebind runner is missing required contracts:`n$($missingRunnerContracts -join "`n")"
}

$requiredTestContracts = @(
    'class PythonRuntimeRealPluginCancelRebindDiagnosticTest',
    'fun cancelAfterStartedKillsOldBinderThenExactRebindRunsFiniteRequestOnce()',
    'REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.python.runtime"',
    'PythonCancellationMode.PROCESS_RESTART_ONLY',
    'policy.requestCancel()',
    'openedSession.get()) { "Cancellation session disappeared before cancel" }.cancel()',
    'PythonRuntimeCodec.decodeCancellation(',
    'decoded.reason == PythonCancellationReason.REQUESTED',
    'decoded.phase == PythonFailurePhase.CANCELLATION',
    'oldBound.awaitExpectedBinderDeath(',
    'assertFalse("Old provider Binder still reports alive after death callback"',
    'assertEquals("Real plugin package identity changed across process retirement"',
    'assertTrue("Exact rebind returned the dead provider Binder"',
    'assertNotEquals(',
    '"Exact rebind reused the retired CPython runtime generation"',
    'assertEquals("Cancelled request was replayed after Binder death"',
    'assertEquals("Rebound finite request was dispatched more than once"',
    'ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use(action)',
    'receivedDescriptors.forEach { descriptor -> runCatching { descriptor.close() } }',
    'source.fill(0)',
    'while True:',
    'sys.stdout.write("__EXPECTED_TOKEN__\n")'
)
$missingTestContracts = @(
    $requiredTestContracts |
        Where-Object { -not $test.Contains($_, [StringComparison]::Ordinal) }
)
if ($missingTestContracts.Count -gt 0) {
    throw "R2 cancel/rebind instrumentation is missing required contracts:`n$($missingTestContracts -join "`n")"
}

$violations = [Collections.Generic.List[string]]::new()
$forbiddenRunnerRegexes = [ordered]@{
    'Gradle wrapper invocation' = '(?im)(?:^|[\\/])gradlew(?:\.bat)?\b'
    'connected or managed-device task' = '(?i)connectedAndroidTest|managedDevice'
    'implicit adb device enumeration' = '(?im)\badb(?:\.exe)?\s+devices\b'
    'replacing APK install' = "@\('install',\s*'-r'|@\('install',[^\r\n]*'--replace'"
    'formal R2 receipt' = 'r2-(?:device|runtime)[^''"\r\n]*\.json'
    'acceptance PASS status' = 'status\s*=\s*[''"]PASS[''"]'
    'caller-controlled serial parameter' = '\[string\]\s+\$Serial\b'
    'Android user deletion or creation' = '(?i)\b(?:remove-user|delete-user|create-user)\b'
}
foreach ($entry in $forbiddenRunnerRegexes.GetEnumerator()) {
    if ($runnerSource -match $entry.Value) {
        $violations.Add($entry.Key)
    }
}
if ($test.Contains('org.autojs.plugin.python.runtime.fake', [StringComparison]::Ordinal)) {
    $violations.Add('real-plugin lifecycle instrumentation references the fake provider')
}

$exactSerialCallCount = [regex]::Matches(
    $runnerSource,
    "@\('-s',\s*\`$script:ExactSerial\)\s*\+\s*\`$AdbArguments"
).Count
if ($exactSerialCallCount -ne 1) {
    $violations.Add("expected one centralized exact-serial ADB injection, found $exactSerialCallCount")
}
$adbBypasses = [regex]::Matches($runnerSource, '(?im)&\s*\$script:AdbExecutable\b').Count
if ($adbBypasses -ne 0) {
    $violations.Add('ADB executable bypasses centralized exact-serial invocation')
}

$userInventoryCalls = [regex]::Matches(
    $runnerSource,
    "@\('shell',\s*'pm',\s*'list',\s*'users'\)"
).Count
$perUserPathQueryCount = [regex]::Matches(
    $runnerSource,
    '@\(''shell'',\s*''pm'',\s*''path'',\s*''--user'',\s*"\$userId",\s*\$PackageName\)'
).Count
$perUserListingCount = [regex]::Matches(
    $runnerSource,
    '@\(''shell'',\s*''pm'',\s*''list'',\s*''packages'',\s*''--user'',\s*"\$userId",\s*\$PackageName\)'
).Count
$globalRetainedQueryCount = [regex]::Matches(
    $runnerSource,
    "@\('shell',\s*'pm',\s*'list',\s*'packages',\s*'-u',\s*\`$PackageName\)"
).Count
$frozenInventoryMatch = [regex]::Match($runnerSource, '(?m)^\$frozenUserIds\s*=\s*@\(Get-ExactDeviceUserIds\)\s*$')
$uniqueNonNegativeInventoryAssertions = [regex]::Matches(
    $runnerSource,
    'userIdMatches\.Count\s+-eq\s+0|uniqueUserIds\.Count\s+-ne\s+\$userIds\.Count|0\s+-notin\s+\$uniqueUserIds'
).Count
if (
    $userInventoryCalls -ne 1 -or
    $perUserPathQueryCount -ne 1 -or
    $perUserListingCount -ne 1 -or
    $globalRetainedQueryCount -ne 1 -or
    -not $frozenInventoryMatch.Success -or
    $uniqueNonNegativeInventoryAssertions -ne 3
) {
    $violations.Add(
        "runner must freeze every unique non-negative Android user ID including 0 and inspect per-user/global package state; " +
            "inventory=$userInventoryCalls path=$perUserPathQueryCount list=$perUserListingCount " +
            "global=$globalRetainedQueryCount frozen=$($frozenInventoryMatch.Success) assertions=$uniqueNonNegativeInventoryAssertions"
    )
}

$sourcePreflightCount = [regex]::Matches(
    $runnerSource,
    '(?m)^\$(?:sourceHost|sourcePlugin|sourceTest)Artifact\s*=\s*Assert-LocalArtifact\s*`\s*$'
).Count
$immutableSnapshotCount = [regex]::Matches(
    $runnerSource,
    '(?m)^\s*\$(?:host|plugin|test)Artifact\s*=\s*New-ImmutableArtifactSnapshot\s*`\s*$'
).Count
$immutableCopyCount = [regex]::Matches(
    $runnerSource,
    '(?m)^\s*Copy-Item\s+-LiteralPath\s+\$Artifact\.Path\s+-Destination\s+\$snapshotPath\s+-ErrorAction\s+Stop\s*$'
).Count
$readOnlySnapshotCount = [regex]::Matches(
    $runnerSource,
    '(?m)^\s*\$snapshotItem\.IsReadOnly\s*=\s*\$true\s*$'
).Count
if (
    $sourcePreflightCount -ne 3 -or
    $immutableSnapshotCount -ne 3 -or
    $immutableCopyCount -ne 1 -or
    $readOnlySnapshotCount -ne 1
) {
    $violations.Add(
        "runner must preflight three inputs and install only revalidated read-only snapshots; " +
            "preflight=$sourcePreflightCount snapshots=$immutableSnapshotCount " +
            "copy=$immutableCopyCount readonly=$readOnlySnapshotCount"
    )
}

$installOrder = @(
    [regex]::Matches(
        $runnerSource,
        'Install-ExactArtifact\s+-Artifact\s+\$(host|plugin|test)Artifact'
    ) | ForEach-Object { $_.Groups[1].Value }
)
if ([string]::Join(',', $installOrder) -cne 'host,plugin,test') {
    $violations.Add("install order must be host,plugin,test; actual=$($installOrder -join ',')")
}
$firstInstallMatch = [regex]::Match(
    $runnerSource,
    '(?m)^\s*Install-ExactArtifact\s+-Artifact\s+\$hostArtifact\s*`\s*$'
)
$firstSnapshotMatch = [regex]::Match(
    $runnerSource,
    '(?m)^\s*\$hostArtifact\s*=\s*New-ImmutableArtifactSnapshot\s*`\s*$'
)
$initialAbsentAssertionCount = [regex]::Matches(
    $runnerSource,
    '(?m)^Assert-FullyAbsentPackageState\s+-State\s+\$initial(?:Host|Plugin|Test)\b'
).Count
if (
    -not $firstInstallMatch.Success -or
    -not $firstSnapshotMatch.Success -or
    -not $frozenInventoryMatch.Success -or
    $initialAbsentAssertionCount -ne 3 -or
    $frozenInventoryMatch.Index -ge $firstInstallMatch.Index -or
    $firstSnapshotMatch.Index -ge $firstInstallMatch.Index
) {
    $violations.Add('frozen multi-user/global absence preflight and immutable snapshot staging must precede the first install')
}
foreach ($name in @('host', 'plugin', 'test')) {
    $flag = '$installedByThisRun.' + $name + ' = $true'
    $install = 'Install-ExactArtifact -Artifact $' + $name + 'Artifact'
    $flagIndex = $runnerSource.IndexOf($flag, [StringComparison]::Ordinal)
    $installIndex = $runnerSource.IndexOf($install, [StringComparison]::Ordinal)
    if ($flagIndex -lt 0 -or $installIndex -lt 0 -or $flagIndex -gt $installIndex) {
        $violations.Add("$name cleanup ownership flag must be set before its install attempt")
    }
}
$installFunctionMatch = [regex]::Match(
    $runnerSource,
    '(?s)function Install-ExactArtifact\s*\{.*?Assert-DeviceUserInventoryUnchanged.*?Assert-FullyAbsentPackageState\s+-State\s+\$InitialState.*?Get-PackageState.*?-UserIds\s+\$FrozenUserIds.*?Assert-FullyAbsentPackageState.*?Invoke-ExactAdb.*?@\(''install'''
)
$uninstallGuardMatch = [regex]::Match(
    $runnerSource,
    '(?s)function Remove-OwnedArtifact\s*\{.*?Assert-DeviceUserInventoryUnchanged.*?Assert-FullyAbsentPackageState\s+-State\s+\$InitialState.*?Assert-InstalledArtifact.*?Assert-DeviceUserInventoryUnchanged.*?Invoke-ExactAdb.*?@\(''uninstall'''
)
if (-not $installFunctionMatch.Success -or -not $uninstallGuardMatch.Success) {
    $violations.Add('install and global uninstall must recheck frozen users, initial absence, and exact run-owned artifact state')
}
$restoreOrder = @(
    [regex]::Matches(
        $runnerSource,
        'Remove-OwnedArtifact\s+-Artifact\s+\$(host|plugin|test)Artifact'
    ) | ForEach-Object { $_.Groups[1].Value }
)
if ([string]::Join(',', $restoreOrder) -cne 'test,plugin,host') {
    $violations.Add("restore order must be test,plugin,host; actual=$($restoreOrder -join ',')")
}

$callbackNames = 'onStarted|onStdout|onStderr|onResult|onFailed|onCancelled|onOpened|onRejected'
$callbackDeclarations = [regex]::Matches($test, "override\s+fun\s+($callbackNames)\s*\(").Count
$uidFirstStatements = [regex]::Matches(
    $test,
    "(?s)override\s+fun\s+($callbackNames)\s*\([^{}]*?\)\s*\{\s*val\s+callingUid\s*=\s*Binder\.getCallingUid\(\)"
).Count
if ($callbackDeclarations -ne 16) {
    $violations.Add("expected exactly 16 AIDL callback entries, found $callbackDeclarations")
}
if ($uidFirstStatements -ne $callbackDeclarations) {
    $violations.Add("Binder UID is not the first statement in every AIDL callback: $uidFirstStatements/$callbackDeclarations")
}
$resultDescriptorClosures = [regex]::Matches(
    $test,
    'receivedDescriptors\.forEach\s*\{\s*descriptor\s*->\s*runCatching\s*\{\s*descriptor\.close\(\)\s*\}\s*\}'
).Count
if ($resultDescriptorClosures -ne 2) {
    $violations.Add("expected result-descriptor closure in both result callbacks, found $resultDescriptorClosures")
}

if ($violations.Count -gt 0) {
    throw "R2 cancel/rebind diagnostic static validation failed:`n$($violations -join "`n")"
}

[ordered]@{
    schema = 1
    status = 'PASS'
    scope = 'FILESYSTEM_STATIC_ONLY'
    runner = $runner
    instrumentation = $testSource
    aidlCallbackUidFirstStatementCount = $uidFirstStatements
    adbInvocationsExecuted = 0
    gradleInvocationsExecuted = 0
    realPluginExecutionObserved = $false
    productionHostNoReplayProved = $false
    deviceAcceptance = $false
} | ConvertTo-Json -Depth 3
