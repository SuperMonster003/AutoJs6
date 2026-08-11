#Requires -Version 7.0

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$runner = Join-Path $PSScriptRoot 'run-r2-real-plugin-device-diagnostic.ps1'
$testSource = Join-Path `
    (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) `
    'app/src/androidTest/java/org/autojs/autojs/core/plugin/python/PythonRuntimeRealPluginDiagnosticTest.kt'
foreach ($requiredFile in @($runner, $testSource)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Missing R2 real-plugin diagnostic source: $requiredFile"
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
    throw "R2 real-plugin runner is not valid PowerShell:`n$(@($parserErrors) -join "`n")"
}

$requiredRunnerContracts = @(
    '$expectedSerial = ''QV710AF65F''',
    '$expectedDeviceAbi = ''arm64-v8a''',
    '$expectedSignerSha256 = ''2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8''',
    '[switch] $ConfirmDeviceMutation',
    '$PSNativeCommandUseErrorActionPreference = $false',
    '''org.autojs.autojs6''',
    '''org.autojs.autojs6.test''',
    '''io.github.supermonster003.autojs6.plugin.python.runtime''',
    '''org.autojs.autojs.core.plugin.python.PythonRuntimeRealPluginDiagnosticTest''',
    '''realPluginMetadataActionNullBindAndCpythonPfdExecution''',
    '''autojs.python.r2.real.enabled''',
    '@(''-s'', $script:ExactSerial) + $AdbArguments',
    '@(''install'', ''--user'', ''0'', ''-t'', $Artifact.Path)',
    '@(''shell'', ''pm'', ''list'', ''users'')',
    '@(''shell'', ''pm'', ''list'', ''packages'', ''-u'', $PackageName)',
    '@(''uninstall'', $Artifact.PackageName)',
    '@(''shell'', ''pm'', ''path'', ''--user'', "$userId", $PackageName)',
    '@(''shell'', ''pm'', ''list'', ''packages'', ''--user'', "$userId", $PackageName)',
    'Get-ExactDeviceUserIds',
    '$frozenUserIds = @(Get-ExactDeviceUserIds)',
    'Assert-DeviceUserInventoryUnchanged -FrozenUserIds $frozenUserIds',
    'Assert-FullyAbsentPackageState',
    'Assert-FullyAbsentPackageState -State $preMutationState',
    'GlobalRecordCount = $knownCount',
    '$hostInstalledByThisRun = $true',
    '$pluginInstalledByThisRun = $true',
    '$testInstalledByThisRun = $true',
    'Install-MissingArtifact -Artifact $hostArtifact',
    'Install-MissingArtifact -Artifact $pluginArtifact',
    'Install-MissingArtifact -Artifact $testArtifact',
    '''PARTIAL_DIAGNOSTIC''',
    '''R2_REAL_CPYTHON_BINDER_PFD_SINGLE_CASE_DIAGNOSTIC_ONLY''',
    'packageInstallationRestoration = ''VERIFIED_ABSENT_ALL_FROZEN_USERS_AND_GLOBAL''',
    'appDataSnapshotTaken = $false',
    'acceptanceReceiptWritten = $false',
    'r2DeviceAcceptance = $false',
    '''CANCEL_AND_PROCESS_RESTART''',
    '''KILL_AND_REBIND''',
    '''NO_REPLAY_AFTER_DISPATCH''',
    'OK \(1 test\)',
    'INSTRUMENTATION_STATUS_CODE:\s*-3',
    'Get-ApkSignerDigests',
    'Get-ApkNativeAbis',
    '$signers.Count -ne 1 -or $signers[0] -cne $expectedSignerSha256',
    '$nativeAbis.Count -ne 1 -or $nativeAbis[0] -cne $expectedDeviceAbi',
    '$Label must be ABI-neutral',
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
    throw "R2 real-plugin runner is missing required contracts:`n$($missingRunnerContracts -join "`n")"
}

$requiredTestContracts = @(
    'class PythonRuntimeRealPluginDiagnosticTest',
    'fun realPluginMetadataActionNullBindAndCpythonPfdExecution()',
    'REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.python.runtime"',
    'REAL_PROVIDER_ID = "org.autojs.python.runtime.cpython"',
    'EXPECTED_PYTHON_VERSION = "3.13.9"',
    'assertNull("Exact real-plugin bind reused the discovery action"',
    'PythonImplementation.CPYTHON',
    'PythonIsolationMode.DEDICATED_PLUGIN_PROCESS',
    'ParcelFileDescriptor.open',
    'PythonSha256.digest(source)',
    'Binder.getCallingUid()',
    'policy.onOutput(decoded, PythonOutputStream.STDOUT)',
    'policy.onOutput(decoded, PythonOutputStream.STDERR)',
    'policy.onResult(decoded, descriptorArray.size)',
    'assert sys.implementation.name == "cpython"',
    'assert sys.version_info[:3] == (3, 13, 9)',
    'CPYTHON=3.13.9;STREAM=STDOUT',
    'CPYTHON=3.13.9;STREAM=STDERR',
    'openedSession.get()?.let { session -> runCatching { session.close() } }',
    '!sourceFile.exists() || sourceFile.delete()'
)
$missingTestContracts = @(
    $requiredTestContracts |
        Where-Object { -not $test.Contains($_, [StringComparison]::Ordinal) }
)
if ($missingTestContracts.Count -gt 0) {
    throw "R2 real-plugin instrumentation is missing required contracts:`n$($missingTestContracts -join "`n")"
}

$violations = [Collections.Generic.List[string]]::new()
$forbiddenRunnerRegexes = [ordered]@{
    'Gradle wrapper invocation' = '(?im)(?:^|[\\/])gradlew(?:\.bat)?\b'
    'connected or managed-device task' = '(?i)connectedAndroidTest|managedDevice'
    'implicit adb device enumeration' = '(?im)\badb(?:\.exe)?\s+devices\b'
    'replacing APK install' = "@\('install',\s*'-r'|@\('install',[^\r\n]*'--replace'"
    'formal R2 receipt' = 'r2-(?:device|runtime)[^''"\r\n]*\.json'
    'acceptance PASS status' = 'status\s*=\s*[''"]PASS[''"]'
    'implicit device selector' = '(?i)ANDROID_SERIAL\s*='
    'Android user deletion or creation' = '(?i)\b(?:remove-user|delete-user|create-user)\b'
    'pre-existing package allowance' = '(?i)AllowTemporaryHostInstall|PREEXISTING_EXACT'
}
foreach ($entry in $forbiddenRunnerRegexes.GetEnumerator()) {
    if ($runnerSource -match $entry.Value) {
        $violations.Add($entry.Key)
    }
}
if ($test.Contains('org.autojs.plugin.python.runtime.fake', [StringComparison]::Ordinal)) {
    $violations.Add('real-plugin instrumentation references the fake provider')
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
$frozenInventoryAssignmentCount = [regex]::Matches(
    $runnerSource,
    '(?m)^\$frozenUserIds\s*=\s*@\(Get-ExactDeviceUserIds\)\s*$'
).Count
$uniqueNonNegativeInventoryAssertions = [regex]::Matches(
    $runnerSource,
    'userIdMatches\.Count\s+-eq\s+0|uniqueUserIds\.Count\s+-ne\s+\$userIds\.Count|0\s+-notin\s+\$uniqueUserIds'
).Count
if (
    $userInventoryCalls -ne 1 -or
    $perUserPathQueryCount -ne 1 -or
    $perUserListingCount -ne 1 -or
    $globalRetainedQueryCount -ne 1 -or
    $frozenInventoryAssignmentCount -ne 1 -or
    $uniqueNonNegativeInventoryAssertions -ne 3
) {
    $violations.Add(
        "runner must freeze every unique non-negative Android user ID including 0 and inspect per-user/global package state; " +
            "inventory=$userInventoryCalls path=$perUserPathQueryCount list=$perUserListingCount " +
            "global=$globalRetainedQueryCount frozen=$frozenInventoryAssignmentCount assertions=$uniqueNonNegativeInventoryAssertions"
    )
}

$sourcePreflightMatches = [regex]::Matches(
    $runnerSource,
    '(?m)^\$(?:sourceHost|sourcePlugin|sourceTest)Artifact\s*=\s*Assert-LocalArtifact\s*`\s*$'
)
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
    $sourcePreflightMatches.Count -ne 3 -or
    $immutableSnapshotMatches.Count -ne 3 -or
    $immutableCopyCount -ne 1 -or
    $readOnlySnapshotCount -ne 1
) {
    $violations.Add(
        "runner must preflight three inputs and install only revalidated immutable snapshots; " +
            "preflight=$($sourcePreflightMatches.Count) snapshots=$($immutableSnapshotMatches.Count) " +
            "copy=$immutableCopyCount readonly=$readOnlySnapshotCount"
    )
}

$sourceAbiPolicies = [regex]::Matches(
    $runnerSource,
    '(?ms)^\$(sourceHost|sourceTest|sourcePlugin)Artifact\s*=\s*Assert-LocalArtifact\s*`.*?-RequiresExactNativeAbi\s+\$(true|false)\s*$'
)
$sourceAbiPolicyMap = @{}
foreach ($match in $sourceAbiPolicies) {
    $sourceAbiPolicyMap[$match.Groups[1].Value] = $match.Groups[2].Value
}
if (
    $sourceAbiPolicyMap['sourceHost'] -cne 'true' -or
    $sourceAbiPolicyMap['sourcePlugin'] -cne 'true' -or
    $sourceAbiPolicyMap['sourceTest'] -cne 'false'
) {
    $violations.Add('host/plugin must be exact single arm64-v8a APKs and androidTest must be ABI-neutral')
}

$snapshotAbiPolicies = [regex]::Matches(
    $runnerSource,
    '(?ms)^\s*\$(host|test|plugin)Artifact\s*=\s*New-ImmutableArtifactSnapshot\s*`.*?-RequiresExactNativeAbi\s+\$(true|false)\s*$'
)
$snapshotAbiPolicyMap = @{}
foreach ($match in $snapshotAbiPolicies) {
    $snapshotAbiPolicyMap[$match.Groups[1].Value] = $match.Groups[2].Value
}
if (
    $snapshotAbiPolicyMap['host'] -cne 'true' -or
    $snapshotAbiPolicyMap['plugin'] -cne 'true' -or
    $snapshotAbiPolicyMap['test'] -cne 'false'
) {
    $violations.Add('immutable host/plugin snapshots must be exact arm64-v8a and androidTest snapshot must be ABI-neutral')
}

$firstSnapshotMatch = [regex]::Match(
    $runnerSource,
    '(?m)^\s*\$hostArtifact\s*=\s*New-ImmutableArtifactSnapshot\s*`\s*$'
)
$firstInstallMatch = [regex]::Match(
    $runnerSource,
    '(?m)^\s*Install-MissingArtifact\s+-Artifact\s+\$hostArtifact\s*`\s*$'
)
$frozenInventoryMatch = [regex]::Match(
    $runnerSource,
    '(?m)^\$frozenUserIds\s*=\s*@\(Get-ExactDeviceUserIds\)\s*$'
)
$initialAbsentAssertions = [regex]::Matches(
    $runnerSource,
    '(?m)^Assert-FullyAbsentPackageState\s+-State\s+\$initial(?:HostState|PluginState|TestState)\b'
)
$lastSourcePreflightIndex = if ($sourcePreflightMatches.Count -eq 0) {
    -1
} else {
    ($sourcePreflightMatches | Measure-Object -Property Index -Maximum).Maximum
}
$lastSnapshotIndex = if ($immutableSnapshotMatches.Count -eq 0) {
    -1
} else {
    ($immutableSnapshotMatches | Measure-Object -Property Index -Maximum).Maximum
}
if (
    -not $frozenInventoryMatch.Success -or
    $initialAbsentAssertions.Count -ne 3 -or
    -not $firstSnapshotMatch.Success -or
    -not $firstInstallMatch.Success -or
    $lastSourcePreflightIndex -ge $firstSnapshotMatch.Index -or
    $lastSnapshotIndex -ge $firstInstallMatch.Index -or
    $firstSnapshotMatch.Index -ge $firstInstallMatch.Index -or
    $frozenInventoryMatch.Index -ge $firstInstallMatch.Index -or
    ($initialAbsentAssertions | Measure-Object -Property Index -Maximum).Maximum -ge $firstInstallMatch.Index
) {
    $violations.Add('frozen multi-user/global absence preflight and all immutable snapshot staging must precede the first install')
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
$globalUninstallCount = [regex]::Matches(
    $runnerSource,
    "@\('uninstall',\s*\`$Artifact\.PackageName\)"
).Count
if ($globalUninstallCount -ne 1) {
    $violations.Add("all global uninstall calls must use the one guarded removal path; found $globalUninstallCount")
}

$ownershipBeforeInstall = [regex]::Matches(
    $runnerSource,
    '(?ms)^\s*\$(host|plugin|test)InstalledByThisRun\s*=\s*\$true\s*\r?\n\s*Install-MissingArtifact\s+-Artifact\s+\$\1Artifact\s*`\s*$'
)
if ($ownershipBeforeInstall.Count -ne 3) {
    $violations.Add("all three install-attempt ownership flags must be set before install; found $($ownershipBeforeInstall.Count)")
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
$restoreOrder = @(
    [regex]::Matches(
        $runnerSource,
        'Remove-OwnedArtifact\s+-Artifact\s+\$(host|plugin|test)Artifact'
    ) | ForEach-Object { $_.Groups[1].Value }
)
if ([string]::Join(',', $restoreOrder) -cne 'test,plugin,host') {
    $violations.Add("restore order must be test,plugin,host; actual=$($restoreOrder -join ',')")
}

$artifactHashPins = [regex]::Matches($runnerSource, 'sha256 = \$(?:host|test|plugin)Artifact\.Sha256').Count
$artifactSignerPins = [regex]::Matches($runnerSource, 'signerSha256 = \$expectedSignerSha256').Count
if ($artifactHashPins -ne 3 -or $artifactSignerPins -ne 3) {
    $violations.Add("runner must report three pinned APK hashes/signers; hashes=$artifactHashPins signers=$artifactSignerPins")
}

if ($violations.Count -gt 0) {
    throw "R2 real-plugin diagnostic static validation failed:`n$($violations -join "`n")"
}

[ordered]@{
    schema = 1
    status = 'PASS'
    scope = 'FILESYSTEM_STATIC_ONLY'
    runner = $runner
    instrumentation = $testSource
    adbInvocationsExecuted = 0
    gradleInvocationsExecuted = 0
    realPluginExecutionObserved = $false
    deviceAcceptance = $false
} | ConvertTo-Json -Depth 3
