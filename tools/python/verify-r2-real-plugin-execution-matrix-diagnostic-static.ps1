#Requires -Version 7.0

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$runner = Join-Path $PSScriptRoot 'run-r2-real-plugin-execution-matrix-diagnostic.ps1'
$testSource = Join-Path `
    (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) `
    'app/src/androidTest/java/org/autojs/autojs/core/plugin/python/PythonRuntimeRealPluginExecutionMatrixDiagnosticTest.kt'
foreach ($requiredFile in @($runner, $testSource)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Missing R2 real-plugin execution-matrix diagnostic source: $requiredFile"
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
    throw "R2 real-plugin execution-matrix runner is not valid PowerShell:`n$(@($parserErrors) -join "`n")"
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
    '''org.autojs.autojs.core.plugin.python.PythonRuntimeRealPluginExecutionMatrixDiagnosticTest''',
    '''systemExitReturnsBoundedExitCode''',
    '''syntaxErrorReturnsStructuredProjectTraceback''',
    '''runtimeExceptionReturnsStructuredProjectTraceback''',
    '''outputLimitFailsAfterDeliveringOnlyBudgetedChunks''',
    '''autojs.python.r2.execution.matrix.enabled''',
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
    'foreach ($testMethod in $testMethods)',
    'Invoke-ExactInstrumentationMethod -TestMethod $testMethod',
    '''PARTIAL_DIAGNOSTIC''',
    '''R2_REAL_CPYTHON_EXECUTION_MATRIX_FOUR_CASE_DIAGNOSTIC_ONLY''',
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
    throw "R2 execution-matrix runner is missing required contracts:`n$($missingRunnerContracts -join "`n")"
}

$requiredTestContracts = @(
    'class PythonRuntimeRealPluginExecutionMatrixDiagnosticTest',
    'fun systemExitReturnsBoundedExitCode()',
    'fun syntaxErrorReturnsStructuredProjectTraceback()',
    'fun runtimeExceptionReturnsStructuredProjectTraceback()',
    'fun outputLimitFailsAfterDeliveringOnlyBudgetedChunks()',
    'ARG_ENABLED = "autojs.python.r2.execution.matrix.enabled"',
    'REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.python.runtime"',
    'REAL_PROVIDER_ID = "org.autojs.python.runtime.cpython"',
    'EXPECTED_PYTHON_VERSION = "3.13.9"',
    'sys.exit(23)',
    'assertEquals(23, result.exitCode)',
    'assertPythonException(observation, "SyntaxError")',
    'assertPythonException(observation, "ValueError")',
    'PythonTracebackOrigin.PROJECT',
    'frame.fileName == ENTRY_POINT',
    'PythonErrorCode.OUTPUT_LIMIT_EXCEEDED',
    'PythonFailurePhase.OUTPUT',
    'Binder.getCallingUid()',
    'callbackUids.all { uid -> uid == observation.pinnedProviderUid }',
    'ParcelFileDescriptor.open',
    'sourceDescriptorClosed = !sourceFileDescriptor.valid()',
    'received.forEach { descriptor -> runCatching { descriptor.close() } }',
    'receivedFileDescriptors.any { descriptor -> descriptor.valid() }',
    'resultDescriptorCount',
    'resultDescriptorsClosed',
    'PythonSha256.digest(source)',
    'openedSession.get()?.let { session -> runCatching { session.close() } }',
    '!sourceFile.exists() || sourceFile.delete()'
)
$missingTestContracts = @(
    $requiredTestContracts |
        Where-Object { -not $test.Contains($_, [StringComparison]::Ordinal) }
)
if ($missingTestContracts.Count -gt 0) {
    throw "R2 execution-matrix instrumentation is missing required contracts:`n$($missingTestContracts -join "`n")"
}

$violations = [Collections.Generic.List[string]]::new()
$forbiddenRunnerRegexes = [ordered]@{
    'Gradle wrapper invocation' = '(?im)(?:^|[\\/])gradlew(?:\.bat)?\b'
    'connected or managed-device task' = '(?i)connectedAndroidTest|managedDevice'
    'implicit adb device enumeration' = '(?im)\badb(?:\.exe)?\s+devices\b'
    'replacing APK install' = "@\('install',\s*'-r'|@\('install',[^\r\n]*'--replace'"
    'formal R2 receipt' = 'r2-(?:device|runtime|execution)[^''"\r\n]*\.json'
    'acceptance PASS status' = 'status\s*=\s*[''"]PASS[''"]'
    'receipt file write' = '(?im)\b(?:Set-Content|Out-File|Export-Clixml)\b'
    'mutable serial parameter' = '(?im)\[string\]\s*\$Serial\b'
    'implicit device selector assignment' = '(?im)\$env:ANDROID_SERIAL\s*='
    'Android user deletion or creation' = '(?i)\b(?:remove-user|delete-user|create-user)\b'
}
foreach ($entry in $forbiddenRunnerRegexes.GetEnumerator()) {
    if ($runnerSource -match $entry.Value) {
        $violations.Add($entry.Key)
    }
}
$forbiddenTestRegexes = [ordered]@{
    'JUnit assumption or skip' = '(?i)\b(?:Assume|assumeTrue|assumeFalse|assumeNotNull|assumeNoException)\b'
    'fake provider reference' = 'org\.autojs\.plugin\.python\.runtime\.fake'
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

$declaredMethods = @(
    [regex]::Matches($test, '(?m)^\s*fun\s+([A-Za-z0-9_]+)\(\)\s*\{') |
        ForEach-Object { $_.Groups[1].Value }
)
$expectedMethods = @(
    'systemExitReturnsBoundedExitCode',
    'syntaxErrorReturnsStructuredProjectTraceback',
    'runtimeExceptionReturnsStructuredProjectTraceback',
    'outputLimitFailsAfterDeliveringOnlyBudgetedChunks'
)
$annotatedTests = [regex]::Matches($test, '(?m)^\s*@Test\s*$').Count
if ($annotatedTests -ne 4) {
    $violations.Add("expected exactly four @Test methods, found $annotatedTests")
}
foreach ($method in $expectedMethods) {
    if ($method -notin $declaredMethods) {
        $violations.Add("missing exact test method $method")
    }
    $runnerOccurrences = [regex]::Matches($runnerSource, "'$([regex]::Escape($method))'").Count
    if ($runnerOccurrences -ne 1) {
        $violations.Add("runner must list exact method $method once; found $runnerOccurrences")
    }
}

$installOrder = @(
    [regex]::Matches(
        $runnerSource,
        'Install-MissingArtifact\s+-Artifact\s+\$(host|plugin|test)Artifact'
    ) | ForEach-Object { $_.Groups[1].Value }
)
if ([string]::Join(',', $installOrder) -cne 'host,plugin,test') {
    $violations.Add("install order must be host,plugin,test; actual=$($installOrder -join ',')")
}
$firstInstallMatch = [regex]::Match(
    $runnerSource,
    '(?m)^\s*Install-MissingArtifact\s+-Artifact\s+\$hostArtifact\s*`\s*$'
)
$firstSnapshotMatch = [regex]::Match(
    $runnerSource,
    '(?m)^\s*\$hostArtifact\s*=\s*New-ImmutableArtifactSnapshot\s*`\s*$'
)
$initialAbsentAssertionCount = [regex]::Matches(
    $runnerSource,
    '(?m)^\s*Assert-FullyAbsentPackageState\s+-State\s+\$state\s+-Label\s+"initial\s+\$\(\$sourceArtifact\.Label\)"\s*$'
).Count
if (
    -not $firstInstallMatch.Success -or
    -not $firstSnapshotMatch.Success -or
    -not $frozenInventoryMatch.Success -or
    $initialAbsentAssertionCount -ne 1 -or
    $frozenInventoryMatch.Index -ge $firstInstallMatch.Index -or
    $firstSnapshotMatch.Index -ge $firstInstallMatch.Index
) {
    $violations.Add('frozen multi-user/global absence preflight and immutable snapshot staging must precede the first install')
}
foreach ($name in @('host', 'plugin', 'test')) {
    $flag = '$' + $name + 'InstalledByThisRun = $true'
    $install = 'Install-MissingArtifact -Artifact $' + $name + 'Artifact'
    $flagIndex = $runnerSource.IndexOf($flag, [StringComparison]::Ordinal)
    $installIndex = $runnerSource.IndexOf($install, [StringComparison]::Ordinal)
    if ($flagIndex -lt 0 -or $installIndex -lt 0 -or $flagIndex -gt $installIndex) {
        $violations.Add("$name cleanup ownership flag must be set before its install attempt")
    }
}
$installFunctionMatch = [regex]::Match(
    $runnerSource,
    '(?s)function Install-MissingArtifact\s*\{.*?Assert-DeviceUserInventoryUnchanged.*?Assert-FullyAbsentPackageState\s+-State\s+\$InitialState.*?Get-PackageState.*?-UserIds\s+\$FrozenUserIds.*?Assert-FullyAbsentPackageState.*?Invoke-ExactAdb.*?@\(''install'''
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

$initialStateLoopPresent = $runnerSource.Contains(
    'foreach ($sourceArtifact in @($sourceHostArtifact, $sourcePluginArtifact, $sourceTestArtifact))',
    [StringComparison]::Ordinal
)
if (-not $initialStateLoopPresent -or $initialAbsentAssertionCount -ne 1) {
    $violations.Add('all three APKs must share the all-user/global initial-absent fail-closed loop')
}

if ($violations.Count -gt 0) {
    throw "R2 real-plugin execution-matrix static validation failed:`n$($violations -join "`n")"
}

[ordered]@{
    schema = 1
    status = 'PASS'
    scope = 'FILESYSTEM_STATIC_ONLY'
    runner = $runner
    instrumentation = $testSource
    exactMethods = $expectedMethods
    adbInvocationsExecuted = 0
    gradleInvocationsExecuted = 0
    realPluginExecutionObserved = $false
    deviceAcceptance = $false
} | ConvertTo-Json -Depth 4
