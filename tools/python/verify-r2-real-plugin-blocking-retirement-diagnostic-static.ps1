#Requires -Version 7.0

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$runner = Join-Path $PSScriptRoot 'run-r2-real-plugin-blocking-retirement-diagnostic.ps1'
$testSource = Join-Path `
    $repositoryRoot `
    'app/src/androidTest/java/org/autojs/autojs/core/plugin/python/PythonRuntimeRealPluginBlockingRetirementDiagnosticTest.kt'
foreach ($requiredFile in @($runner, $testSource)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Missing R2 blocking-retirement diagnostic source: $requiredFile"
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
    throw "R2 blocking-retirement runner is not valid PowerShell:`n$(@($parserErrors) -join "`n")"
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
    '''org.autojs.autojs.core.plugin.python.PythonRuntimeRealPluginBlockingRetirementDiagnosticTest''',
    '''blockingOsReadTimesOutBeforeBinderDeathThenExactRebindRunsFiniteRequestOnce''',
    '''nativePbkdf2TimesOutBeforeBinderDeathThenExactRebindRunsFiniteRequestOnce''',
    '''autojs.python.r2.blockingRetirement.enabled''',
    '$testMethods.Count -ne 2 -or @($testMethods | Select-Object -Unique).Count -ne 2',
    '@(''-s'', $script:ExactSerial) + $AdbArguments',
    '@(''install'', ''--user'', ''0'', ''-t'', $Artifact.Path)',
    '@(''shell'', ''pm'', ''list'', ''users'')',
    '@(''shell'', ''pm'', ''path'', ''--user'', $userId.ToString(), $PackageName)',
    '''shell'', ''pm'', ''list'', ''packages'', ''--user'', $userId.ToString(), ''-u'', $PackageName',
    '@(''uninstall'', $Artifact.PackageName)',
    'Assert-InitiallyAbsent -State $initialHost',
    'Assert-InitiallyAbsent -State $initialPlugin',
    'Assert-InitiallyAbsent -State $initialTest',
    'Install-ExactArtifact -Artifact $hostArtifact -FrozenUserIds $frozenUserIds',
    'Install-ExactArtifact -Artifact $pluginArtifact -FrozenUserIds $frozenUserIds',
    'Install-ExactArtifact -Artifact $testArtifact -FrozenUserIds $frozenUserIds',
    '$preInstallState = Get-PackageState -PackageName $Artifact.PackageName -UserIds $FrozenUserIds',
    'Assert-InitiallyAbsent -State $preInstallState -Label "pre-install $($Artifact.PackageName)"',
    '''PARTIAL_DIAGNOSTIC''',
    '''R2_REAL_CPYTHON_BLOCKING_AND_NATIVE_TIMEOUT_RETIREMENT_TWO_CASE_DIAGNOSTIC_ONLY''',
    '''OS_PIPE_READ_BLOCKING_IO_TIMEOUT''',
    '''HASHLIB_PBKDF2_HMAC_NATIVE_WORK_TIMEOUT''',
    '$observations.Count -ne 2',
    'exactTestCount = $observations.Count',
    '''HOST_DID_NOT_CANCEL_OR_REPLAY_TIMED_OUT_REQUEST''',
    '''PRODUCTION_HOST_AUTOMATIC_REPLAY_POLICY''',
    'androidUserSetRestoration = ''VERIFIED_UNCHANGED''',
    'packageInstallationRestoration = ''VERIFIED_ABSENT_ALL_FROZEN_USERS''',
    'appDataSnapshotTaken = $false',
    'acceptanceReceiptWritten = $false',
    'r2DeviceAcceptance = $false',
    'OK \(1 test\)',
    'INSTRUMENTATION_STATUS_CODE:\s*-3',
    'Get-ApkSignerDigests',
    'Get-ApkNativeAbis',
    'function Get-DeviceUserIds',
    '$frozenUserIds = @(Get-DeviceUserIds)',
    '0 -notin $frozenUserIds -or 10 -notin $frozenUserIds',
    '$records.Count -ne $recordMarkers',
    '0 -notin $uniqueUserIds',
    'function Assert-DeviceUserSetUnchanged',
    'frozenUserIds = @($frozenUserIds | ForEach-Object { [int] $_ })',
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
    'Assert-InstalledArtifact',
    'Remove-OwnedArtifact'
)
$missingRunnerContracts = @(
    $requiredRunnerContracts |
        Where-Object { -not $runnerSource.Contains($_, [StringComparison]::Ordinal) }
)
if ($missingRunnerContracts.Count -gt 0) {
    throw "R2 blocking-retirement runner is missing required contracts:`n$($missingRunnerContracts -join "`n")"
}

$requiredTestContracts = @(
    'class PythonRuntimeRealPluginBlockingRetirementDiagnosticTest',
    'fun blockingOsReadTimesOutBeforeBinderDeathThenExactRebindRunsFiniteRequestOnce()',
    'fun nativePbkdf2TimesOutBeforeBinderDeathThenExactRebindRunsFiniteRequestOnce()',
    'ARG_ENABLED = "autojs.python.r2.blockingRetirement.enabled"',
    '"true",',
    'REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.python.runtime"',
    'PythonCancellationMode.PROCESS_RESTART_ONLY',
    'fun executeUntilProviderTimeout(',
    'timeoutMillis = PROVIDER_TIMEOUT_MILLIS',
    'const val PROVIDER_TIMEOUT_MILLIS = 4_000L',
    'policy.onFailed(decoded)',
    'decoded.code == PythonErrorCode.TIMEOUT',
    'decoded.phase == PythonFailurePhase.EXECUTION',
    '!decoded.retryableBeforeDispatch',
    'PythonExecutionSessionPolicy.Terminal.FAILED',
    'assertEquals(0, timedOut.hostCancelInvocationCount)',
    'bound.recordTypedTimeoutTerminal()',
    'oldBound.binderDeathEventOrder > oldBound.typedTimeoutTerminalOrder',
    'val eventOrder = eventSequence.incrementAndGet()',
    'bound.awaitTerminalBeforeDeath(terminal, TIMEOUT_TERMINAL_WAIT_MILLIS)',
    'oldBound.awaitExpectedBinderDeath(',
    'assertFalse("Old provider Binder still reports alive after death callback"',
    'assertEquals("Real plugin package identity changed across process retirement"',
    'assertNull("New exact bind reused the discovery action"',
    'assertTrue("Exact rebind returned the dead provider Binder"',
    'assertNotEquals(',
    '"Exact rebind reused the retired CPython runtime generation"',
    'assertEquals("Timed-out request was replayed after Binder death"',
    'assertEquals("Rebound finite request was dispatched more than once"',
    'timedOut.callbackUids.all { uid -> uid == originalSnapshot.packageUid }',
    'completed.callbackUids.all { uid -> uid == originalSnapshot.packageUid }',
    'val sourceDescriptor = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)',
    'val sourceFileDescriptor = sourceDescriptor.fileDescriptor',
    'sourceDescriptor.use(action)',
    'sourceFileDescriptor.valid()',
    'receivedDescriptors.forEach { descriptor -> runCatching { descriptor.close() } }',
    'source.fill(0)',
    'os.pipe()',
    'os.read(read_fd, 1)',
    'os.close(read_fd)',
    'os.close(write_fd)',
    'hashlib.pbkdf2_hmac("sha256", b"password", b"salt", 500_000_000, dklen=32)',
    'sys.stdout.write("__EXPECTED_TOKEN__\n")'
)
$missingTestContracts = @(
    $requiredTestContracts |
        Where-Object { -not $test.Contains($_, [StringComparison]::Ordinal) }
)
if ($missingTestContracts.Count -gt 0) {
    throw "R2 blocking-retirement instrumentation is missing required contracts:`n$($missingTestContracts -join "`n")"
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
    'receipt file write' = '(?im)\b(?:Set-Content|Out-File|Export-Clixml)\b'
    'implicit serial environment mutation' = '(?im)\$env:ANDROID_SERIAL\s*='
    'Android user creation or deletion' = '(?i)(?:\bcreate-user\b|\bremove-user\b|[''"]user[''"]\s*,\s*[''"](?:create|remove)[''"])'
}
foreach ($entry in $forbiddenRunnerRegexes.GetEnumerator()) {
    if ($runnerSource -match $entry.Value) {
        $violations.Add($entry.Key)
    }
}
if ($test.Contains('org.autojs.plugin.python.runtime.fake', [StringComparison]::Ordinal)) {
    $violations.Add('real-plugin blocking-retirement instrumentation references the fake provider')
}

$forbiddenTestRegexes = [ordered]@{
    'host session cancel call' = '(?i)\.cancel\s*\('
    'host policy cancellation request' = '\brequestCancel\s*\('
    'requested cancellation model' = 'PythonCancellationReason|PythonFailurePhase\.CANCELLATION|Terminal\.CANCELLED'
    'copied cancellation constants' = '\bCANCEL_[A-Z0-9_]+'
    'host timeout sleep trigger' = '\bThread\.sleep\s*\('
    'JUnit assumption or skip' = '(?i)\b(?:Assume|assumeTrue|assumeFalse|assumeNotNull|assumeNoException)\b'
}
foreach ($entry in $forbiddenTestRegexes.GetEnumerator()) {
    if ($test -match $entry.Value) {
        $violations.Add($entry.Key)
    }
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
$immutableSnapshotAssignments = [regex]::Matches(
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
    $immutableSnapshotAssignments -ne 3 -or
    $immutableCopyCount -ne 1 -or
    $readOnlySnapshotCount -ne 1
) {
    $violations.Add(
        "runner must preflight three inputs and install only revalidated immutable snapshots; " +
            "preflight=$sourcePreflightCount snapshots=$immutableSnapshotAssignments " +
            "copy=$immutableCopyCount readonly=$readOnlySnapshotCount"
    )
}

$installOrder = @(
    [regex]::Matches(
        $runnerSource,
        'Install-ExactArtifact\s+-Artifact\s+\$(host|plugin|test)Artifact\s+-FrozenUserIds\s+\$frozenUserIds'
    ) | ForEach-Object { $_.Groups[1].Value }
)
if ([string]::Join(',', $installOrder) -cne 'host,plugin,test') {
    $violations.Add("install order must be host,plugin,test; actual=$($installOrder -join ',')")
}
$firstSnapshotMatch = [regex]::Match(
    $runnerSource,
    '(?m)^\s*\$hostArtifact\s*=\s*New-ImmutableArtifactSnapshot\s*`\s*$'
)
$firstInstallMatch = [regex]::Match(
    $runnerSource,
    '(?m)^\s*Install-ExactArtifact\s+-Artifact\s+\$hostArtifact\s+-FrozenUserIds\s+\$frozenUserIds\s*$'
)
$initialAbsenceMatches = [regex]::Matches(
    $runnerSource,
    '(?m)^\s*Assert-InitiallyAbsent\s+-State\s+\$initial(?:Host|Plugin|Test)\b.*$'
)
$mainUserSetGuards = [regex]::Matches(
    $runnerSource,
    '(?m)^\s*Assert-DeviceUserSetUnchanged\s+-ExpectedUserIds\s+\$frozenUserIds\s*$'
)
$lastInitialAbsenceIndex = if ($initialAbsenceMatches.Count -eq 0) {
    -1
} else {
    $initialAbsenceMatches[$initialAbsenceMatches.Count - 1].Index
}
$preMutationGuardCount = @(
    $mainUserSetGuards |
        Where-Object { $_.Index -gt $lastInitialAbsenceIndex -and $_.Index -lt $firstInstallMatch.Index }
).Count
if (
    -not $frozenUserSetMatch.Success -or
    -not $firstSnapshotMatch.Success -or
    -not $firstInstallMatch.Success -or
    $frozenUserSetMatch.Index -ge $firstInstallMatch.Index -or
    $firstSnapshotMatch.Index -ge $firstInstallMatch.Index -or
    $initialAbsenceMatches.Count -ne 3 -or
    $preMutationGuardCount -ne 1
) {
    $violations.Add('complete per-user absence, frozen-user recheck, and immutable staging must precede the first install')
}
$restoreOrder = @(
    [regex]::Matches(
        $runnerSource,
        '(?ms)^\s*Remove-OwnedArtifact\s*`\s*\r?\n' +
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
    '(?s)function Remove-OwnedArtifact\s*\{(?<body>.*?)\r?\n\}\r?\n\r?\nfunction Invoke-ExactInstrumentationMethod'
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

$callbackNames = 'onStarted|onStdout|onStderr|onResult|onFailed|onCancelled|onOpened|onRejected'
$callbackDeclarations = [regex]::Matches($test, "override\s+fun\s+($callbackNames)\s*\(").Count
$uidFirstStatements = [regex]::Matches(
    $test,
    "(?s)override\s+fun\s+($callbackNames)\s*\([^{}]*?\)\s*\{\s*val\s+callingUid\s*=\s*Binder\.getCallingUid\(\)"
).Count
if ($callbackDeclarations -ne 16) {
    $violations.Add("expected exactly 16 shared AIDL callback entries, found $callbackDeclarations")
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

$annotatedTests = [regex]::Matches($test, '(?m)^\s*@Test\s*$').Count
if ($annotatedTests -ne 2) {
    $violations.Add("expected exactly two @Test methods, found $annotatedTests")
}
foreach ($method in @(
    'blockingOsReadTimesOutBeforeBinderDeathThenExactRebindRunsFiniteRequestOnce',
    'nativePbkdf2TimesOutBeforeBinderDeathThenExactRebindRunsFiniteRequestOnce'
)) {
    $methodOccurrences = [regex]::Matches($runnerSource, [regex]::Escape("'$method'" )).Count
    if ($methodOccurrences -ne 1) {
        $violations.Add("runner must pin exact method $method once; found $methodOccurrences")
    }
}
$timeoutMatch = [regex]::Match($test, 'const val PROVIDER_TIMEOUT_MILLIS = ([0-9_]+)L')
if (-not $timeoutMatch.Success) {
    $violations.Add('provider timeout constant is missing')
} else {
    $timeoutMillis = [long] ($timeoutMatch.Groups[1].Value.Replace('_', ''))
    if ($timeoutMillis -lt [long] 1000 -or $timeoutMillis -gt [long] 5000) {
        $violations.Add("provider timeout must remain short but reasonable (1000..5000 ms); actual=$timeoutMillis")
    }
}

$timeoutDispatch = $test.IndexOf('timedOut = executeUntilProviderTimeout(', [StringComparison]::Ordinal)
$expectedDeath = $test.IndexOf('oldBound.awaitExpectedBinderDeath(', [StringComparison]::Ordinal)
$exactRebind = $test.IndexOf('val newBound = bindProvider(', [StringComparison]::Ordinal)
if ($timeoutDispatch -lt 0 -or $expectedDeath -le $timeoutDispatch -or $exactRebind -le $expectedDeath) {
    $violations.Add('typed timeout, old Binder death, and exact rebind are not asserted in order')
}

$artifactHashPins = [regex]::Matches($runnerSource, 'sha256 = \$(?:host|test|plugin)Artifact\.Sha256').Count
$artifactSignerPins = [regex]::Matches($runnerSource, 'signerSha256 = \$expectedSignerSha256').Count
if ($artifactHashPins -ne 3 -or $artifactSignerPins -ne 3) {
    $violations.Add("runner must report three pinned APK hashes/signers; hashes=$artifactHashPins signers=$artifactSignerPins")
}

if ($violations.Count -gt 0) {
    throw "R2 blocking-retirement diagnostic static validation failed:`n$($violations -join "`n")"
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
