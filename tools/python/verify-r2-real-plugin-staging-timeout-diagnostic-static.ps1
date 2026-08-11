#Requires -Version 7.0

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$runner = Join-Path $PSScriptRoot 'run-r2-real-plugin-staging-timeout-diagnostic.ps1'
$testSource = Join-Path `
    (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) `
    'app/src/androidTest/java/org/autojs/autojs/core/plugin/python/PythonRuntimeRealPluginStagingTimeoutDiagnosticTest.kt'
foreach ($path in @($runner, $testSource)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Missing staging-timeout diagnostic source: $path"
    }
}
$runnerSource = Get-Content -LiteralPath $runner -Raw
$test = Get-Content -LiteralPath $testSource -Raw

$tokens = $null
$errors = $null
$null = [Management.Automation.Language.Parser]::ParseInput($runnerSource, [ref] $tokens, [ref] $errors)
if (@($errors).Count -ne 0) {
    throw "Staging-timeout runner PowerShell parse failure:`n$(@($errors) -join "`n")"
}

$requiredRunner = @(
    '$expectedSerial = ''QV710AF65F''',
    '$expectedDeviceAbi = ''arm64-v8a''',
    '$expectedUserIds = @(''0'', ''10'')',
    '$expectedSignerSha256 = ''2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8''',
    '''org.autojs.autojs.core.plugin.python.PythonRuntimeRealPluginStagingTimeoutDiagnosticTest''',
    '''stagingDeadlineFailsBeforeOldBinderDeathThenExactRebindRunsFiniteReliablePipeOnce''',
    '''autojs.python.r2.stagingTimeout.enabled''',
    '@(''-s'', $script:ExactSerial) + $AdbArguments',
    '@(''install'', ''--user'', ''0'', ''-t'', $Artifact.Path)',
    '@(''shell'', ''pm'', ''list'', ''users'')',
    '@(''shell'', ''pm'', ''path'', ''--user'', $userId, $PackageName)',
    '@(''shell'', ''pm'', ''list'', ''packages'', ''--user'', $userId, ''-u'', $PackageName)',
    '@(''shell'', ''pm'', ''list'', ''packages'', ''-u'', $PackageName)',
    '@(''uninstall'', $Artifact.PackageName)',
    'function Get-CompleteUserInventory',
    'function Assert-UserInventoryUnchanged',
    '$frozenUserIds = @(Get-CompleteUserInventory)',
    '$rawUserIds.Count -ne $userIds.Count',
    '$records.Count -ne $recordMarkers',
    'if ($userIds -notcontains ''0'')',
    '''^(?:0|[1-9][0-9]*)$''',
    'Assert-InitiallyAbsent -State $state',
    '$malformedRetainedLines.Count -ne 0',
    '$malformedKnownLines.Count -ne 0',
    '''^package:[A-Za-z0-9_][A-Za-z0-9._]*$''',
    '[Guid]::NewGuid().ToString(''N'')',
    '$snapshotItem.IsReadOnly = $true',
    '''host immutable snapshot second verification''',
    '''plugin immutable snapshot second verification''',
    '''androidTest immutable snapshot second verification''',
    'Assert-UserInventoryUnchanged -FrozenUserIds $FrozenUserIds -Label ''pre-install inventory verification''',
    '$preInstallState = Get-PackageState -PackageName $Artifact.PackageName -UserIds $FrozenUserIds',
    'Assert-InitiallyAbsent -State $preInstallState -Label "pre-install $($Artifact.PackageName)"',
    'Assert-UserInventoryUnchanged -FrozenUserIds $FrozenUserIds -Label ''global-uninstall boundary''',
    '''PARTIAL_DIAGNOSTIC''',
    '''R2_REAL_CPYTHON_RELIABLE_PIPE_STAGING_TIMEOUT_AND_EXACT_REBIND_DIAGNOSTIC_ONLY''',
    'packageInstallationRestoration = ''VERIFIED_ABSENT_ALL_FROZEN_USERS_AND_GLOBAL_RECORD''',
    '''PROVIDER_4S_DEADLINE_DURING_SOURCE_STAGING''',
    '''TYPED_TERMINAL_BEFORE_OLD_BINDER_DEATH''',
    '''DIAGNOSTIC_HARNESS_DID_NOT_REPLAY_AFTER_DISPATCH''',
    '''PRODUCTION_HOST_AUTOMATIC_REPLAY_POLICY''',
    'frozenUserIds = @($frozenUserIds | ForEach-Object { [int] $_ })',
    'acceptanceReceiptWritten = $false',
    'r2DeviceAcceptance = $false',
    'OK \(1 test\)',
    'INSTRUMENTATION_STATUS_CODE:\s*-3'
)
$requiredTest = @(
    'class PythonRuntimeRealPluginStagingTimeoutDiagnosticTest',
    'fun stagingDeadlineFailsBeforeOldBinderDeathThenExactRebindRunsFiniteReliablePipeOnce()',
    'ARG_ENABLED = "autojs.python.r2.stagingTimeout.enabled"',
    'ParcelFileDescriptor.createReliablePipe()',
    'check(source.isNotEmpty())',
    'declaredLengthBytes = source.size.toLong()',
    'sha256 = PythonSha256.digest(source)',
    'timeoutMillis = PROVIDER_TIMEOUT_MILLIS',
    'const val PROVIDER_TIMEOUT_MILLIS = 4_000L',
    'assertEquals(PythonErrorCode.TIMEOUT, timedOut.error.code)',
    'assertEquals(PythonFailurePhase.EXECUTION, timedOut.error.phase)',
    'assertFalse("A dispatched staging timeout must not be retryable", timedOut.error.retryableBeforeDispatch)',
    'assertEquals(0, timedOut.startedCount)',
    'assertEquals(0, timedOut.stdoutCount)',
    'assertEquals(0, timedOut.stderrCount)',
    'assertEquals(0, timedOut.resultCount)',
    'assertEquals(0, timedOut.cancelledCount)',
    'bound.awaitTerminalBeforeDeath(terminal, TIMEOUT_TERMINAL_WAIT_MILLIS)',
    'bound.recordTypedTimeoutTerminal()',
    'bound.awaitExpectedBinderDeath(BINDER_DEATH_WAIT_MILLIS)',
    'oldBound.binderDeathEventOrder > oldBound.typedTimeoutTerminalOrder',
    'runCatching { sourceReader.close() }',
    'runCatching { sourceWriter.close() }',
    'received.forEach { descriptor -> runCatching { descriptor.close() } }',
    'val postDeathSnapshot = requireNotNull(transport.inspectExact(component))',
    'val newBound = bindProvider(postDeathSnapshot)',
    'newBound.provider.asBinder() != oldProviderBinder',
    'assertTrue("Recovered runtime generation must be positive", recovered.runtimeGeneration > 0L)',
    'assert sys.implementation.name == "cpython"',
    'assert sys.version_info[:3] == (3, 13, 9)',
    'assertEquals("Diagnostic must dispatch exactly two requests", 2, dispatched.size)',
    'assertEquals("Staging-timeout request was replayed", 1, dispatched.count { it == timedOut.requestId })'
)
$missingRunner = @($requiredRunner | Where-Object { -not $runnerSource.Contains($_, [StringComparison]::Ordinal) })
$missingTest = @($requiredTest | Where-Object { -not $test.Contains($_, [StringComparison]::Ordinal) })
if ($missingRunner.Count -gt 0 -or $missingTest.Count -gt 0) {
    throw "Missing staging-timeout contracts:`nRUNNER:`n$($missingRunner -join "`n")`nTEST:`n$($missingTest -join "`n")"
}

$violations = [Collections.Generic.List[string]]::new()
$forbiddenRunner = [ordered]@{
    'Gradle invocation' = '(?im)(?:^|[\\/])gradlew(?:\.bat)?\b'
    'connected task' = '(?i)connectedAndroidTest|managedDevice'
    'adb devices' = '(?im)\badb(?:\.exe)?\s+devices\b'
    'replace install' = "@\('install',\s*'-r'|--replace"
    'receipt write' = '(?im)\b(?:Set-Content|Out-File|Export-Clixml)\b'
    'formal receipt path' = 'r2-[^''"\r\n]*\.json'
    'acceptance PASS' = 'status\s*=\s*[''"]PASS[''"]'
    'user creation' = '(?i)create-user|createUser'
    'user deletion' = '(?i)remove-user|removeUser'
}
$forbiddenTest = [ordered]@{
    'JUnit skip' = '(?i)\b(?:Assume|assumeTrue|assumeFalse|assumeNotNull|assumeNoException)\b'
    'host cancel' = '\.\s*cancel\s*\(|\brequestCancel\s*\('
    'Thread sleep' = '\bThread\s*\.\s*sleep\s*\('
    'raw dup' = '(?:ParcelFileDescriptor|Os)\s*\.\s*dup\s*\('
    'raw detach or adoption' = 'detachFd\s*\(|(?:adoptFd|fromFd)\s*\('
    'regular-file PFD' = 'ParcelFileDescriptor\s*\.\s*open\s*\('
    'producer error close' = 'closeWithError\s*\('
}
foreach ($entry in $forbiddenRunner.GetEnumerator()) {
    if ($runnerSource -match $entry.Value) { $violations.Add($entry.Key) }
}
foreach ($entry in $forbiddenTest.GetEnumerator()) {
    if ($test -match $entry.Value) { $violations.Add($entry.Key) }
}

$exactSerialInjection = [regex]::Matches(
    $runnerSource,
    "@\('-s',\s*\`$script:ExactSerial\)\s*\+\s*\`$AdbArguments"
).Count
if ($exactSerialInjection -ne 1 -or $runnerSource -match '(?im)&\s*\$script:AdbExecutable') {
    $violations.Add("ADB exact-serial centralization failed: injection=$exactSerialInjection")
}
if ([regex]::Matches($runnerSource, "@\('shell',\s*'pm',\s*'list',\s*'users'\)").Count -ne 1) {
    $violations.Add('user inventory must have one centralized implementation')
}
if ([regex]::Matches($runnerSource, '(?m)^\$(?:sourceHost|sourcePlugin|sourceTest)Artifact\s*=\s*Assert-LocalArtifact').Count -ne 3) {
    $violations.Add('all three source APKs need initial exact verification')
}
if ([regex]::Matches($runnerSource, '(?m)^\s*\$(?:host|plugin|test)Artifact\s*=\s*New-ImmutableArtifactSnapshot').Count -ne 3) {
    $violations.Add('all three APKs need immutable GUID snapshots')
}
if ([regex]::Matches($runnerSource, "immutable snapshot second verification'").Count -ne 3) {
    $violations.Add('all three immutable snapshots need second verification')
}

$stagingBody = [regex]::Match(
    $test,
    '(?s)private fun executeStagingTimeout\(.*?\n\s*private fun executeFiniteSource\('
).Value
if ([string]::IsNullOrWhiteSpace($stagingBody)) {
    $violations.Add('could not isolate staging-timeout function')
} elseif ($stagingBody -match 'AutoCloseOutputStream|\.write\s*\(|\.flush\s*\(|closeWithError') {
    $violations.Add('staging writer is not provably open and unwritten')
}
if ([regex]::Matches($test, '(?m)^\s*@Test\s*$').Count -ne 1) {
    $violations.Add('diagnostic must contain exactly one test')
}
if ([regex]::Matches($test, 'ParcelFileDescriptor\.createReliablePipe\(\)').Count -ne 2) {
    $violations.Add('diagnostic must create one staging and one recovery reliable pipe')
}

foreach ($callback in @('onStarted','onStdout','onStderr','onResult','onFailed','onCancelled','onOpened','onRejected')) {
    $all = [regex]::Matches($test, "(?ms)override\s+fun\s+$callback\s*\([^)]*\)\s*\{").Count
    $uidFirst = [regex]::Matches(
        $test,
        "(?ms)override\s+fun\s+$callback\s*\([^)]*\)\s*\{\s*val\s+callingUid\s*=\s*Binder\.getCallingUid\(\)"
    ).Count
    if ($all -ne 2 -or $uidFirst -ne $all) {
        $violations.Add("$callback UID-first contract failed: all=$all first=$uidFirst")
    }
}

$firstInstall = $runnerSource.IndexOf('Install-RunOwnedArtifact -Artifact $hostArtifact', [StringComparison]::Ordinal)
$frozen = $runnerSource.IndexOf('$frozenUserIds = @(Get-CompleteUserInventory)', [StringComparison]::Ordinal)
if ($frozen -lt 0 -or $firstInstall -lt 0 -or $frozen -ge $firstInstall) {
    $violations.Add('user inventory must be frozen before installation')
}
foreach ($name in @('host','plugin','test')) {
    $flag = $runnerSource.IndexOf('$' + $name + 'Installed = $true', [StringComparison]::Ordinal)
    $install = $runnerSource.IndexOf('Install-RunOwnedArtifact -Artifact $' + $name + 'Artifact', [StringComparison]::Ordinal)
    if ($flag -lt 0 -or $install -lt 0 -or $flag -gt $install) {
        $violations.Add("$name ownership flag must precede install")
    }
}
$finally = $runnerSource.IndexOf('} finally {', [StringComparison]::Ordinal)
$restoreTest = $runnerSource.IndexOf('-Artifact $testArtifact', $finally, [StringComparison]::Ordinal)
$restorePlugin = $runnerSource.IndexOf('-Artifact $pluginArtifact', $restoreTest + 1, [StringComparison]::Ordinal)
$restoreHost = $runnerSource.IndexOf('-Artifact $hostArtifact', $restorePlugin + 1, [StringComparison]::Ordinal)
if ($restoreTest -lt 0 -or $restorePlugin -le $restoreTest -or $restoreHost -le $restorePlugin) {
    $violations.Add('reverse restoration order is not test,plugin,host')
}
if ($runnerSource.Contains("'NO_REPLAY_AFTER_DISPATCH'", [StringComparison]::Ordinal)) {
    $violations.Add('production no-replay is overclaimed')
}

if ($violations.Count -gt 0) {
    throw "R2 staging-timeout static validation failed:`n$($violations -join "`n")"
}

[ordered]@{
    schema = 1
    status = 'PASS'
    scope = 'FILESYSTEM_STATIC_ONLY'
    runner = $runner
    instrumentation = $testSource
    exactMethod = 'stagingDeadlineFailsBeforeOldBinderDeathThenExactRebindRunsFiniteReliablePipeOnce'
    contractTarget = 'STAGING_TIMEOUT_BEFORE_ON_STARTED_AND_OLD_BINDER_DEATH'
    adbInvocationsExecuted = 0
    gradleInvocationsExecuted = 0
    realPluginExecutionObserved = $false
    productionHostNoReplayProved = $false
    deviceAcceptance = $false
} | ConvertTo-Json -Depth 4
