#Requires -Version 7.0

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$runner = Join-Path $PSScriptRoot 'run-r2-real-plugin-reliable-pipe-diagnostic.ps1'
$testSource = Join-Path `
    (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) `
    'app/src/androidTest/java/org/autojs/autojs/core/plugin/python/PythonRuntimeRealPluginReliablePipeDiagnosticTest.kt'
foreach ($requiredFile in @($runner, $testSource)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Missing R2 reliable-pipe diagnostic source: $requiredFile"
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
    throw "R2 reliable-pipe runner is not valid PowerShell:`n$(@($parserErrors) -join "`n")"
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
    '''org.autojs.autojs.core.plugin.python.PythonRuntimeRealPluginReliablePipeDiagnosticTest''',
    '''producerErrorRejectsBeforeCpythonThenExactRebindExecutesFiniteSource''',
    '''autojs.python.r2.reliablePipe.enabled''',
    '@(''-s'', $script:ExactSerial) + $AdbArguments',
    '@(''install'', ''--user'', ''0'', ''-t'', $Artifact.Path)',
    '@(''shell'', ''pm'', ''list'', ''users'')',
    '@(''shell'', ''pm'', ''path'', ''--user'', $userId.ToString(), $PackageName)',
    '''shell'', ''pm'', ''list'', ''packages'', ''--user'', $userId.ToString(), ''-u'', $PackageName',
    '@(''uninstall'', $Artifact.PackageName)',
    'Assert-InitiallyAbsent -State $state',
    'foreach ($artifact in @($hostArtifact, $pluginArtifact, $testArtifact))',
    'Install-RunOwnedArtifact -Artifact $hostArtifact -FrozenUserIds $frozenUserIds',
    'Install-RunOwnedArtifact -Artifact $pluginArtifact -FrozenUserIds $frozenUserIds',
    'Install-RunOwnedArtifact -Artifact $testArtifact -FrozenUserIds $frozenUserIds',
    '$preInstallState = Get-PackageState -PackageName $Artifact.PackageName -UserIds $FrozenUserIds',
    'Assert-InitiallyAbsent -State $preInstallState -Label "pre-install $($Artifact.PackageName)"',
    '$current = Get-PackageState -PackageName $Artifact.PackageName',
    'if (-not $current.Installed)',
    'if ($current.KnownToPackageManager)',
    '''PARTIAL_DIAGNOSTIC''',
    '''R2_REAL_CPYTHON_RELIABLE_PIPE_ERROR_AND_EXACT_REBIND_DIAGNOSTIC_ONLY''',
    'androidUserSetRestoration = ''VERIFIED_UNCHANGED''',
    'packageInstallationRestoration = ''VERIFIED_ABSENT_ALL_FROZEN_USERS''',
    'appDataSnapshotTaken = $false',
    'acceptanceReceiptWritten = $false',
    'r2DeviceAcceptance = $false',
    '''SOURCE_REJECTED_BEFORE_CPYTHON_STARTED_CALLBACK''',
    '''EXACT_COMPONENT_REBIND_FINITE_CPYTHON_RECOVERY''',
    'OK \(1 test\)',
    'INSTRUMENTATION_STATUS_CODE:\s*-3',
    'Get-ApkSignerDigests',
    'Get-ApkNativeAbis',
    'function Get-DeviceUserIds',
    '$frozenUserIds = @(Get-DeviceUserIds)',
    '$records.Count -ne $recordMarkers',
    '0 -notin $uniqueUserIds',
    'function Assert-DeviceUserSetUnchanged',
    'frozenUserIds = $frozenUserIds',
    'RetainedOrGlobalRecord = $knownCount -eq 1',
    '$pathLines.Count -ne $paths.Count',
    '$malformedKnownLines.Count -ne 0',
    '''^package:[A-Za-z0-9_][A-Za-z0-9._]*$''',
    'New-ImmutableArtifactSnapshot',
    'Copy-Item -LiteralPath $Artifact.Path -Destination $snapshotPath -ErrorAction Stop',
    '$snapshotItem.IsReadOnly = $true',
    'return Assert-LocalArtifact',
    '-Path $snapshotItem.FullName',
    '$hostArtifact = New-ImmutableArtifactSnapshot',
    '$pluginArtifact = New-ImmutableArtifactSnapshot',
    '$testArtifact = New-ImmutableArtifactSnapshot',
    'Assert-InstalledArtifact'
)
$missingRunnerContracts = @(
    $requiredRunnerContracts |
        Where-Object { -not $runnerSource.Contains($_, [StringComparison]::Ordinal) }
)
if ($missingRunnerContracts.Count -gt 0) {
    throw "R2 reliable-pipe runner is missing required contracts:`n$($missingRunnerContracts -join "`n")"
}

$requiredTestContracts = @(
    'class PythonRuntimeRealPluginReliablePipeDiagnosticTest',
    'fun producerErrorRejectsBeforeCpythonThenExactRebindExecutesFiniteSource()',
    'ARG_ENABLED = "autojs.python.r2.reliablePipe.enabled"',
    'REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.python.runtime"',
    'REAL_PROVIDER_ID = "org.autojs.python.runtime.cpython"',
    'EXPECTED_PYTHON_VERSION = "3.13.9"',
    'PythonImplementation.CPYTHON',
    'PythonIsolationMode.DEDICATED_PLUGIN_PROCESS',
    'ParcelFileDescriptor.createReliablePipe()',
    'sourceWriter.closeWithError(RELIABLE_PIPE_PRODUCER_ERROR)',
    'declaredLengthBytes = source.size.toLong()',
    'sha256 = PythonSha256.digest(source)',
    'assertEquals(PythonErrorCode.SOURCE_REJECTED, rejected.error.code)',
    'assertEquals(PythonFailurePhase.INPUT_VALIDATION, rejected.error.phase)',
    'assertFalse("Source rejection must not be retryable after start dispatch", rejected.error.retryableBeforeDispatch)',
    'assertEquals(0, rejected.startedCount)',
    'assertEquals(0, rejected.stdoutCount)',
    'assertEquals(0, rejected.stderrCount)',
    'assertEquals(0, rejected.resultCount)',
    'assertEquals(0, rejected.cancelledCount)',
    'unexpected(callingUid, startedCount, "onStarted")',
    'Keep it open but unread until the',
    'Retain the original reliable-pipe reader until staging publishes onStarted',
    'source.fill(0)',
    'runCatching { output?.close() }',
    'runCatching { sourceReader.close() }',
    'runCatching { sourceWriter.close() }',
    'received.forEach { descriptor -> runCatching { descriptor.close() } }',
    'oldBound.awaitExpectedBinderDeath(BINDER_DEATH_WAIT_MILLIS)',
    'val postDeathSnapshot = requireNotNull(transport.inspectExact(component))',
    'val newBound = bindProvider(postDeathSnapshot)',
    'newBound.provider.asBinder() != oldProviderBinder',
    'assert sys.implementation.name == "cpython"',
    'assert sys.version_info[:3] == (3, 13, 9)',
    'assertEquals(0, recovered.result.exitCode)',
    'dispatchLedger += request.requestId',
    'assertEquals("Diagnostic must dispatch exactly two requests", 2, dispatched.size)',
    'assertEquals("Reliable-pipe rejected request was replayed", 1, dispatched.count { it == rejected.requestId })'
)
$missingTestContracts = @(
    $requiredTestContracts |
        Where-Object { -not $test.Contains($_, [StringComparison]::Ordinal) }
)
if ($missingTestContracts.Count -gt 0) {
    throw "R2 reliable-pipe instrumentation is missing required contracts:`n$($missingTestContracts -join "`n")"
}
if ([regex]::Matches($test, 'sourceReader\.close\(\)').Count -ne 2) {
    throw 'Reliable-pipe readers must only close in the two common finally blocks'
}

$violations = [Collections.Generic.List[string]]::new()
$forbiddenRunnerRegexes = [ordered]@{
    'Gradle wrapper invocation' = '(?im)(?:^|[\\/])gradlew(?:\.bat)?\b'
    'connected or managed-device task' = '(?i)connectedAndroidTest|managedDevice'
    'implicit adb device enumeration' = '(?im)\badb(?:\.exe)?\s+devices\b'
    'replacing APK install' = "@\('install',\s*'-r'|@\('install',(?:[^\r\n])*'--replace'"
    'formal R2 receipt' = 'r2-(?:device|runtime|reliable)[^''"\r\n]*\.json'
    'acceptance PASS status' = 'status\s*=\s*[''"]PASS[''"]'
    'receipt file write' = '(?im)\b(?:Set-Content|Out-File|Export-Clixml)\b'
    'mutable serial parameter' = '(?im)\[string\]\s*\$Serial\b'
    'implicit device selector assignment' = '(?im)\$env:ANDROID_SERIAL\s*='
    'Android user creation or deletion' = '(?i)(?:\bcreate-user\b|\bremove-user\b|[''"]user[''"]\s*,\s*[''"](?:create|remove)[''"])'
}
foreach ($entry in $forbiddenRunnerRegexes.GetEnumerator()) {
    if ($runnerSource -match $entry.Value) {
        $violations.Add($entry.Key)
    }
}
$forbiddenTestRegexes = [ordered]@{
    'JUnit assumption or skip' = '(?i)\b(?:Assume|assumeTrue|assumeFalse|assumeNotNull|assumeNoException)\b'
    'fake provider reference' = 'org\.autojs\.plugin\.python\.runtime\.fake'
    'raw ParcelFileDescriptor dup' = 'ParcelFileDescriptor\s*\.\s*dup\s*\('
    'raw Os dup' = '(?:android\.system\.)?Os\s*\.\s*dup\s*\('
    'raw file-descriptor adoption' = 'ParcelFileDescriptor\s*\.\s*adoptFd\s*\('
    'regular-file source descriptor' = 'ParcelFileDescriptor\s*\.\s*open\s*\('
}
foreach ($entry in $forbiddenTestRegexes.GetEnumerator()) {
    if ($test -match $entry.Value) {
        $violations.Add($entry.Key)
    }
}

$nativeAdbReferences = @(
    [regex]::Matches($runnerSource, '(?im)&\s*\$script:AdbExecutable\b') |
        ForEach-Object { $_.Value }
)
if ($nativeAdbReferences.Count -ne 0) {
    $violations.Add('ADB executable bypasses centralized exact-serial invocation')
}
$exactSerialCallCount = [regex]::Matches(
    $runnerSource,
    "@\('-s',\s*\`$script:ExactSerial\)\s*\+\s*\`$AdbArguments"
).Count
if ($exactSerialCallCount -ne 1) {
    $violations.Add("expected one centralized -s injection, found $exactSerialCallCount")
}

$userInventoryCalls = [regex]::Matches(
    $runnerSource,
    "@\('shell',\s*'pm',\s*'list',\s*'users'\)"
).Count
$frozenUserSetMatch = [regex]::Match(
    $runnerSource,
    '(?m)^\$frozenUserIds\s*=\s*@\(Get-DeviceUserIds\)\s*$'
)
$completeInventoryChecks = [regex]::Matches(
    $runnerSource,
    '\$records\.Count\s+-eq\s+0\s+-or\s+\$records\.Count\s+-ne\s+\$recordMarkers'
).Count
$uniqueIncludingZeroChecks = [regex]::Matches(
    $runnerSource,
    '\$uniqueUserIds\.Count\s+-ne\s+\$userIds\.Count\s+-or\s+0\s+-notin\s+\$uniqueUserIds'
).Count
$perUserPathQueries = [regex]::Matches(
    $runnerSource,
    "@\('shell',\s*'pm',\s*'path',\s*'--user',\s*\`$userId\.ToString\(\),\s*\`$PackageName\)"
).Count
$perUserRetainedQueries = [regex]::Matches(
    $runnerSource,
    "'shell',\s*'pm',\s*'list',\s*'packages',\s*'--user',\s*\`$userId\.ToString\(\),\s*'-u',\s*\`$PackageName"
).Count
$userSetGuardCalls = [regex]::Matches(
    $runnerSource,
    'Assert-DeviceUserSetUnchanged\s+-ExpectedUserIds\s+\$(?:FrozenUserIds|frozenUserIds)'
).Count
if (
    $userInventoryCalls -ne 1 -or
    -not $frozenUserSetMatch.Success -or
    $completeInventoryChecks -ne 1 -or
    $uniqueIncludingZeroChecks -ne 1 -or
    $perUserPathQueries -ne 1 -or
    $perUserRetainedQueries -ne 1 -or
    $userSetGuardCalls -ne 7
) {
    $violations.Add(
        "runner must freeze and repeatedly verify a complete multi-user inventory; inventory=$userInventoryCalls " +
            "freeze=$($frozenUserSetMatch.Success) complete=$completeInventoryChecks unique0=$uniqueIncludingZeroChecks " +
            "paths=$perUserPathQueries retained=$perUserRetainedQueries guards=$userSetGuardCalls"
    )
}

$sourcePreflightCount = [regex]::Matches(
    $runnerSource,
    '(?m)^\$(?:sourceHost|sourcePlugin|sourceTest)Artifact\s*=\s*Assert-LocalArtifact\s*`\s*$'
).Count
$immutableSnapshotMatches = [regex]::Matches(
    $runnerSource,
    '(?m)^\s*\$(?:host|plugin|test)Artifact\s*=\s*New-ImmutableArtifactSnapshot\s*`\s*$'
)
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
    $immutableSnapshotMatches.Count -ne 3 -or
    $immutableCopyCount -ne 1 -or
    $readOnlySnapshotCount -ne 1
) {
    $violations.Add(
        "runner must preflight three inputs and install only revalidated immutable snapshots; " +
            "preflight=$sourcePreflightCount snapshots=$($immutableSnapshotMatches.Count) " +
            "copy=$immutableCopyCount readonly=$readOnlySnapshotCount"
    )
}

$testCount = [regex]::Matches($test, '(?m)^\s*@Test\s*$').Count
if ($testCount -ne 1) {
    $violations.Add("expected exactly one @Test method, found $testCount")
}
$reliablePipeCount = [regex]::Matches(
    $test,
    'ParcelFileDescriptor\.createReliablePipe\(\)'
).Count
if ($reliablePipeCount -ne 2) {
    $violations.Add("expected two complete reliable pipes, found $reliablePipeCount")
}
$closeWithErrorCount = [regex]::Matches(
    $test,
    'sourceWriter\.closeWithError\(RELIABLE_PIPE_PRODUCER_ERROR\)'
).Count
if ($closeWithErrorCount -ne 1) {
    $violations.Add("expected one producer closeWithError, found $closeWithErrorCount")
}

$protocolCallbacks = @(
    'onStarted',
    'onStdout',
    'onStderr',
    'onResult',
    'onFailed',
    'onCancelled',
    'onOpened',
    'onRejected'
)
foreach ($callback in $protocolCallbacks) {
    $allCount = [regex]::Matches(
        $test,
        "(?ms)override\s+fun\s+$callback\s*\([^)]*\)\s*\{"
    ).Count
    $uidFirstCount = [regex]::Matches(
        $test,
        "(?ms)override\s+fun\s+$callback\s*\([^)]*\)\s*\{\s*val\s+callingUid\s*=\s*Binder\.getCallingUid\(\)"
    ).Count
    if ($allCount -ne 2 -or $uidFirstCount -ne $allCount) {
        $violations.Add("$callback must occur twice with Binder UID capture as its first statement; all=$allCount first=$uidFirstCount")
    }
}

$installOrder = @(
    [regex]::Matches(
        $runnerSource,
        'Install-RunOwnedArtifact\s+-Artifact\s+\$(host|plugin|test)Artifact\s+-FrozenUserIds\s+\$frozenUserIds'
    ) | ForEach-Object { $_.Groups[1].Value }
)
if ([string]::Join(',', $installOrder) -cne 'host,plugin,test') {
    $violations.Add("install order must be host,plugin,test; actual=$($installOrder -join ',')")
}
$firstInstallMatch = [regex]::Match(
    $runnerSource,
    '(?m)^\s*Install-RunOwnedArtifact\s+-Artifact\s+\$hostArtifact\s+-FrozenUserIds\s+\$frozenUserIds\s*$'
)
$initialAbsenceMatches = [regex]::Matches(
    $runnerSource,
    '(?m)^\s*Assert-InitiallyAbsent\s+-State\s+\$state\s+-Label\s+\$artifact\.Label\s*$'
)
$mainUserSetGuards = [regex]::Matches(
    $runnerSource,
    '(?m)^\s*Assert-DeviceUserSetUnchanged\s+-ExpectedUserIds\s+\$frozenUserIds\s*$'
)
$preMutationGuardCount = @(
    $mainUserSetGuards | Where-Object { $_.Index -lt $firstInstallMatch.Index }
).Count
if (
    -not $frozenUserSetMatch.Success -or
    $immutableSnapshotMatches.Count -ne 3 -or
    -not $firstInstallMatch.Success -or
    $frozenUserSetMatch.Index -ge $firstInstallMatch.Index -or
    $immutableSnapshotMatches[0].Index -ge $firstInstallMatch.Index -or
    $initialAbsenceMatches.Count -ne 1 -or
    $preMutationGuardCount -ne 1
) {
    $violations.Add('complete per-user absence, frozen-user recheck, and immutable staging must precede the first install')
}
foreach ($name in @('host', 'plugin', 'test')) {
    $flag = '$' + $name + 'Installed = $true'
    $install = 'Install-RunOwnedArtifact -Artifact $' + $name + 'Artifact'
    $flagIndex = $runnerSource.IndexOf($flag, [StringComparison]::Ordinal)
    $installIndex = $runnerSource.IndexOf($install, [StringComparison]::Ordinal)
    if ($flagIndex -lt 0 -or $installIndex -lt 0 -or $flagIndex -gt $installIndex) {
        $violations.Add("$name cleanup ownership flag must be set before its install attempt")
    }
}
$restoreOrder = @(
    [regex]::Matches(
        $runnerSource,
        '(?ms)^\s*Remove-RunOwnedArtifact\s*`\s*\r?\n' +
            '\s*-Artifact\s+\$(test|plugin|host)Artifact\s*`\s*\r?\n' +
            '\s*-TemporaryDirectory\s+\$temporaryRoot\s*`\s*\r?\n' +
            '\s*-FrozenUserIds\s+\$frozenUserIds\s*$'
    ) | ForEach-Object { $_.Groups[1].Value }
)
if ([string]::Join(',', $restoreOrder) -cne 'test,plugin,host') {
    $violations.Add("restore order must be test,plugin,host; actual=$($restoreOrder -join ',')")
}

$removeFunction = [regex]::Match(
    $runnerSource,
    '(?s)function Remove-RunOwnedArtifact\s*\{(?<body>.*?)\r?\n\}\r?\n\r?\nfunction Invoke-ExactInstrumentationMethod'
)
if (-not $removeFunction.Success) {
    $violations.Add('unable to inspect multi-user removal contract')
} else {
    $removeBody = $removeFunction.Groups['body'].Value
    $artifactIdentityIndex = $removeBody.IndexOf('Assert-InstalledArtifact', [StringComparison]::Ordinal)
    $uninstallIndex = $removeBody.IndexOf("@('uninstall', `$Artifact.PackageName)", [StringComparison]::Ordinal)
    $guardIndexes = @(
        [regex]::Matches($removeBody, 'Assert-DeviceUserSetUnchanged\s+-ExpectedUserIds\s+\$FrozenUserIds') |
            ForEach-Object { $_.Index }
    )
    $guardAfterIdentity = @(
        $guardIndexes | Where-Object { $_ -gt $artifactIdentityIndex -and $_ -lt $uninstallIndex }
    ).Count
    $guardAfterUninstall = @($guardIndexes | Where-Object { $_ -gt $uninstallIndex }).Count
    if (
        $artifactIdentityIndex -lt 0 -or
        $uninstallIndex -lt 0 -or
        $artifactIdentityIndex -ge $uninstallIndex -or
        $guardAfterIdentity -lt 1 -or
        $guardAfterUninstall -lt 1
    ) {
        $violations.Add('global uninstall must follow exact artifact proof and an unchanged-user guard, then recheck users')
    }
}

if ($runnerSource.Contains("'NO_REPLAY_AFTER_DISPATCH'", [StringComparison]::Ordinal)) {
    $violations.Add('runner overclaims production no-replay evidence')
}
if (-not $runnerSource.Contains("'DIAGNOSTIC_HARNESS_DID_NOT_REPLAY_AFTER_DISPATCH'", [StringComparison]::Ordinal)) {
    $violations.Add('runner is missing the harness-scoped no-replay observation')
}
$productionNoReplayNotCovered = [regex]::IsMatch(
    $runnerSource,
    "(?s)notCovered\s*=\s*@\(.*?'PRODUCTION_HOST_AUTOMATIC_REPLAY_POLICY'.*?\)"
)
if (-not $productionNoReplayNotCovered) {
    $violations.Add('production host automatic replay policy must remain explicitly not covered')
}

if ($violations.Count -gt 0) {
    throw "R2 reliable-pipe diagnostic static validation failed:`n$($violations -join "`n")"
}

[ordered]@{
    schema = 1
    status = 'PASS'
    scope = 'FILESYSTEM_STATIC_ONLY'
    runner = $runner
    instrumentation = $testSource
    exactMethod = 'producerErrorRejectsBeforeCpythonThenExactRebindExecutesFiniteSource'
    contractTarget = 'SOURCE_REJECTED_BEFORE_ON_STARTED'
    runtimeEvidencePending = 'UPDATED_PLUGIN_REQUIRES_GRADLE_COMPILE_AND_EXACT_DEVICE_PROOF'
    adbInvocationsExecuted = 0
    gradleInvocationsExecuted = 0
    realPluginExecutionObserved = $false
    productionHostNoReplayProved = $false
    deviceAcceptance = $false
} | ConvertTo-Json -Depth 4
