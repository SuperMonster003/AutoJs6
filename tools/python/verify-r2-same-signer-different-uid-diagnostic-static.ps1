#Requires -Version 7.0

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$runner = Join-Path $PSScriptRoot 'run-r2-same-signer-different-uid-diagnostic.ps1'
$consumerRoot = Join-Path $repositoryRoot 'test-apps/python-runtime-api-consumer'
$testSource = Join-Path $consumerRoot 'src/androidTest/java/org/autojs/plugin/python/runtime/consumer/PythonRuntimeSameSignerDifferentUidAndroidTest.kt'
$manifestSource = Join-Path $consumerRoot 'src/main/AndroidManifest.xml'
$buildSource = Join-Path $consumerRoot 'build.gradle.kts'
$providerAidl = Join-Path $repositoryRoot 'plugin-api/python-runtime-api/src/main/aidl/org/autojs/plugin/python/runtime/api/IPythonRuntimeProvider.aidl'

foreach ($requiredFile in @($runner, $testSource, $manifestSource, $buildSource, $providerAidl)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Missing same-signer different-UID diagnostic source: $requiredFile"
    }
}

$runnerText = Get-Content -LiteralPath $runner -Raw
$testText = Get-Content -LiteralPath $testSource -Raw
$manifestText = Get-Content -LiteralPath $manifestSource -Raw
$buildText = Get-Content -LiteralPath $buildSource -Raw
$aidlText = Get-Content -LiteralPath $providerAidl -Raw

$parserTokens = $null
$parserErrors = $null
$runnerAst = [Management.Automation.Language.Parser]::ParseInput(
    $runnerText,
    [ref] $parserTokens,
    [ref] $parserErrors
)
if (@($parserErrors).Count -ne 0) {
    throw (
        "Same-signer different-UID runner is not valid PowerShell:" +
            [Environment]::NewLine +
            [string]::Join([Environment]::NewLine, @($parserErrors))
    )
}
if ($null -eq $runnerAst) {
    throw 'PowerShell parser did not return a runner AST'
}

$requiredRunnerContracts = @(
    '$expectedSerial = ''QV710AF65F''',
    '$expectedDeviceAbi = ''arm64-v8a''',
    '$expectedSignerSha256 = ''2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8''',
    '$hostPackage = ''org.autojs.autojs6''',
    '$pluginPackage = ''io.github.supermonster003.autojs6.plugin.python.runtime''',
    '$consumerPackage = ''org.autojs.plugin.python.runtime.consumer''',
    '$expectedConsumerTestPackage = ''org.autojs.plugin.python.runtime.consumer.test''',
    '$consumerTestPackageName = $ConsumerTestPackage',
    '$consumerTestPackageName -cne $expectedConsumerTestPackage',
    '[string] $HostSha256',
    '[string] $PluginSha256',
    '[string] $ConsumerSha256',
    '[string] $ConsumerTestSha256',
    '[string] $ConsumerTestPackage',
    '[switch] $ConfirmDeviceMutation',
    '$PSNativeCommandUseErrorActionPreference = $false',
    '$runnerComponent = "$consumerTestPackageName/androidx.test.runner.AndroidJUnitRunner"',
    '$testClass = ''org.autojs.plugin.python.runtime.consumer.PythonRuntimeSameSignerDifferentUidAndroidTest''',
    '$testMethod = ''sameSignerDifferentUidIsRejectedAtEveryProviderEntry''',
    '@(''-s'', $script:ExactSerial) + $AdbArguments',
    '@(''install'', ''--user'', ''0'', ''-t'', $Artifact.Path)',
    '@(''shell'', ''pm'', ''list'', ''users'')',
    '@(''shell'', ''pm'', ''path'', ''--user'', $userId.ToString(), $PackageName)',
    '''shell'', ''pm'', ''list'', ''packages'', ''--user'', $userId.ToString(), ''-u'', $PackageName',
    '@(''shell'', ''pm'', ''list'', ''packages'', ''-u'', $PackageName)',
    '@(''uninstall'', $Artifact.PackageName)',
    '$frozenUserIds = @(Get-DeviceUserIds)',
    '0 -notin $frozenUserIds -or 10 -notin $frozenUserIds',
    '$records.Count -eq 0 -or $records.Count -ne $recordMarkers',
    '$uniqueUserIds.Count -ne $userIds.Count -or 0 -notin $uniqueUserIds',
    'function Assert-DeviceUserSetUnchanged',
    'New-ImmutableArtifactSnapshot',
    'Copy-Item -LiteralPath $Artifact.Path -Destination $snapshotPath -ErrorAction Stop',
    '$snapshotItem.IsReadOnly = $true',
    'Immutable snapshot lost its read-only marker',
    '-Label "$($Artifact.Label) immediately before install"',
    'Assert-InitiallyAbsent -State $initialHost',
    'Assert-InitiallyAbsent -State $initialPlugin',
    'Assert-InitiallyAbsent -State $initialConsumer',
    'Assert-InitiallyAbsent -State $initialConsumerTest',
    '''PARTIAL_DIAGNOSTIC''',
    '''R2_SAME_SIGNER_DIFFERENT_UID_PROVIDER_ENTRY_REJECTION_DIAGNOSTIC_ONLY''',
    '''SAME_SIGNER_DIFFERENT_UID_REJECTED_AT_EVERY_PROVIDER_ENTRY''',
    '''DIFFERENT_SIGNER_CALLER_REJECTION''',
    '''ALTERNATE_UID_SESSION_HANDLE_RELAY''',
    '''PRODUCTION_HOST_ACCEPTANCE''',
    'exactTestCount = $observations.Count',
    'androidUserSetRestoration = ''VERIFIED_UNCHANGED''',
    'packageInstallationRestoration = ''VERIFIED_ABSENT_GLOBAL_AND_ALL_FROZEN_USERS''',
    'acceptanceReceiptWritten = $false',
    'r2DeviceAcceptance = $false',
    'OK \(1 test\)',
    'INSTRUMENTATION_STATUS_CODE:\s*-3',
    'frozenUserIds = @($frozenUserIds | ForEach-Object { [int] $_ })',
    'Get-ApkSignerDigests',
    'Get-ApkNativeAbis',
    'Assert-InstalledArtifact',
    'Remove-OwnedArtifact'
)
$missingRunnerContracts = @(
    $requiredRunnerContracts |
        Where-Object { -not $runnerText.Contains($_, [StringComparison]::Ordinal) }
)
if ($missingRunnerContracts.Count -gt 0) {
    throw (
        "Runner is missing required contracts:" +
            [Environment]::NewLine +
            [string]::Join([Environment]::NewLine, $missingRunnerContracts)
    )
}

$violations = [Collections.Generic.List[string]]::new()
$forbiddenRunnerRegexes = [ordered]@{
    'Gradle wrapper invocation' = '(?im)(?:^|[\\/])gradlew(?:\.bat)?\b'
    'connected or managed-device task' = '(?i)connectedAndroidTest|managedDevice'
    'implicit adb device enumeration' = '(?im)\badb(?:\.exe)?\s+devices\b'
    'replacing APK install' = "@\('install',\s*'-r'|@\('install',[^\r\n]*'--replace'"
    'formal R2 receipt path' = 'r2-(?:device|runtime)[^''"\r\n]*\.json'
    'acceptance PASS status' = 'status\s*=\s*[''"]PASS[''"]'
    'caller-controlled serial parameter' = '\[string\]\s+\$Serial\b'
    'receipt file write' = '(?im)\b(?:Set-Content|Out-File|Export-Clixml)\b'
    'implicit serial environment mutation' = '(?im)\$env:ANDROID_SERIAL\s*='
    'Android user mutation' = '(?i)(?:\bcreate-user\b|\bremove-user\b|[''"]user[''"]\s*,\s*[''"](?:create|remove)[''"])'
    'instrumentation opt-in argument' = 'autojs\.python\.'
}
foreach ($entry in $forbiddenRunnerRegexes.GetEnumerator()) {
    if ($runnerText -match $entry.Value) {
        $violations.Add($entry.Key)
    }
}

$exactSerialInjectionCount = [regex]::Matches(
    $runnerText,
    '@\(''-s'',\s*\$script:ExactSerial\)\s*\+\s*\$AdbArguments'
).Count
if ($exactSerialInjectionCount -ne 1) {
    $violations.Add("expected one centralized exact-serial ADB injection; actual=$exactSerialInjectionCount")
}
if ([regex]::Matches($runnerText, '(?im)&\s*\$script:AdbExecutable\b').Count -ne 0) {
    $violations.Add('ADB executable bypasses centralized exact-serial invocation')
}

$providerMethods = @(
    [regex]::Matches(
        $aidlText,
        '(?m)^\s*(?:byte\[\]|void)\s+(getRuntimeInfo|getCapabilities|openSession)\s*\('
    ) | ForEach-Object { $_.Groups[1].Value }
)
if (
    $providerMethods.Count -ne 3 -or
    [string]::Join(',', @($providerMethods | Sort-Object)) -cne
        'getCapabilities,getRuntimeInfo,openSession'
) {
    $violations.Add("provider AIDL entry set drifted; actual=$($providerMethods -join ',')")
}

$userInventoryCalls = [regex]::Matches(
    $runnerText,
    '@\(''shell'',\s*''pm'',\s*''list'',\s*''users''\)'
).Count
$globalPackageQueries = [regex]::Matches(
    $runnerText,
    '@\(''shell'',\s*''pm'',\s*''list'',\s*''packages'',\s*''-u'',\s*\$PackageName\)'
).Count
$perUserPathQueries = [regex]::Matches(
    $runnerText,
    '@\(''shell'',\s*''pm'',\s*''path'',\s*''--user'',\s*\$userId\.ToString\(\),\s*\$PackageName\)'
).Count
$perUserRetainedQueries = [regex]::Matches(
    $runnerText,
    '''shell'',\s*''pm'',\s*''list'',\s*''packages'',\s*''--user'',\s*\$userId\.ToString\(\),\s*''-u'',\s*\$PackageName'
).Count
if (
    $userInventoryCalls -ne 1 -or
    $globalPackageQueries -ne 1 -or
    $perUserPathQueries -ne 1 -or
    $perUserRetainedQueries -ne 1
) {
    $violations.Add(
        "global/all-user inventory contract drifted; users=$userInventoryCalls global=$globalPackageQueries " +
            "paths=$perUserPathQueries retained=$perUserRetainedQueries"
    )
}

$sourcePreflightCount = [regex]::Matches(
    $runnerText,
    '(?m)^\$(?:sourceHost|sourcePlugin|sourceConsumer|sourceConsumerTest)Artifact\s*=\s*Assert-LocalArtifact\s*\x60\s*$'
).Count
$immutableSnapshotCount = [regex]::Matches(
    $runnerText,
    '(?m)^\s*\$(?:host|plugin|consumer|consumerTest)Artifact\s*=\s*New-ImmutableArtifactSnapshot\s*\x60\s*$'
).Count
$initialAbsenceCount = [regex]::Matches(
    $runnerText,
    '(?m)^\s*Assert-InitiallyAbsent\s+-State\s+\$initial(?:Host|Plugin|Consumer|ConsumerTest)\b'
).Count
if ($sourcePreflightCount -ne 4 -or $immutableSnapshotCount -ne 4 -or $initialAbsenceCount -ne 4) {
    $violations.Add(
        "expected four preflights, immutable snapshots, and initial absence checks; " +
            "preflight=$sourcePreflightCount snapshots=$immutableSnapshotCount absent=$initialAbsenceCount"
    )
}

$sourceAbiMatrix = @(
    [regex]::Matches(
        $runnerText,
        '(?ms)^\$(sourceHost|sourcePlugin|sourceConsumer|sourceConsumerTest)Artifact\s*=\s*Assert-LocalArtifact\s*\x60.*?' +
            '^\s*-RequiresExactNativeAbi\s+\$(true|false)\s*$'
    ) | ForEach-Object { "$($_.Groups[1].Value):$($_.Groups[2].Value)" }
)
if (
    [string]::Join(',', $sourceAbiMatrix) -cne
        'sourceHost:true,sourcePlugin:true,sourceConsumer:false,sourceConsumerTest:false'
) {
    $violations.Add("artifact ABI admission matrix drifted: $($sourceAbiMatrix -join ',')")
}

$installOrder = @(
    [regex]::Matches(
        $runnerText,
        'Install-ExactArtifact\s+-Artifact\s+\$(host|plugin|consumer|consumerTest)Artifact\s+-FrozenUserIds\s+\$frozenUserIds'
    ) | ForEach-Object { $_.Groups[1].Value }
)
if ([string]::Join(',', $installOrder) -cne 'host,plugin,consumer,consumerTest') {
    $violations.Add("install order must be host,plugin,consumer,consumerTest; actual=$($installOrder -join ',')")
}

$restoreOrder = @(
    [regex]::Matches(
        $runnerText,
        '(?ms)^\s*Remove-OwnedArtifact\s*\x60\s*\r?\n' +
            '\s*-Artifact\s+\$(consumerTest|consumer|plugin|host)Artifact\s*\x60\s*\r?\n' +
            '\s*-TemporaryDirectory\s+\$temporaryRoot\s*\x60\s*\r?\n' +
            '\s*-FrozenUserIds\s+\$frozenUserIds\s*$'
    ) | ForEach-Object { $_.Groups[1].Value }
)
if ([string]::Join(',', $restoreOrder) -cne 'consumerTest,consumer,plugin,host') {
    $violations.Add("restore order must be consumerTest,consumer,plugin,host; actual=$($restoreOrder -join ',')")
}

$installFunction = [regex]::Match(
    $runnerText,
    '(?s)function Install-ExactArtifact\s*\{(?<body>.*?)\r?\n\}\r?\n\r?\nfunction Remove-OwnedArtifact'
)
if (-not $installFunction.Success) {
    $violations.Add('unable to inspect install mutation contract')
} else {
    $body = $installFunction.Groups['body'].Value
    $userGuardIndex = $body.IndexOf('Assert-DeviceUserSetUnchanged', [StringComparison]::Ordinal)
    $snapshotCheckIndex = $body.IndexOf('Assert-LocalArtifact', [StringComparison]::Ordinal)
    $absenceIndex = $body.IndexOf('Assert-InitiallyAbsent', [StringComparison]::Ordinal)
    $installIndex = $body.IndexOf("@('install', '--user', '0', '-t', `$Artifact.Path)", [StringComparison]::Ordinal)
    if (
        $userGuardIndex -lt 0 -or
        $snapshotCheckIndex -le $userGuardIndex -or
        $absenceIndex -le $snapshotCheckIndex -or
        $installIndex -le $absenceIndex
    ) {
        $violations.Add('each install must follow frozen-user, immutable artifact, and exact absence revalidation')
    }
}

$removeFunction = [regex]::Match(
    $runnerText,
    '(?s)function Remove-OwnedArtifact\s*\{(?<body>.*?)\r?\n\}\r?\n\r?\nfunction Invoke-ExactInstrumentation'
)
if (-not $removeFunction.Success) {
    $violations.Add('unable to inspect uninstall mutation contract')
} else {
    $body = $removeFunction.Groups['body'].Value
    $identityIndex = $body.IndexOf('Assert-InstalledArtifact', [StringComparison]::Ordinal)
    $uninstallIndex = $body.IndexOf("@('uninstall', `$Artifact.PackageName)", [StringComparison]::Ordinal)
    $guardIndexes = @(
        [regex]::Matches($body, 'Assert-DeviceUserSetUnchanged\s+-ExpectedUserIds\s+\$FrozenUserIds') |
            ForEach-Object { $_.Index }
    )
    $guardBeforeUninstall = @(
        $guardIndexes | Where-Object { $_ -gt $identityIndex -and $_ -lt $uninstallIndex }
    ).Count
    $guardAfterUninstall = @($guardIndexes | Where-Object { $_ -gt $uninstallIndex }).Count
    if (
        $identityIndex -lt 0 -or
        $uninstallIndex -le $identityIndex -or
        $guardBeforeUninstall -lt 1 -or
        $guardAfterUninstall -lt 1
    ) {
        $violations.Add('each uninstall must follow exact installed identity and frozen-user revalidation')
    }
}

$dispatchRevalidation = [regex]::Match(
    $runnerText,
    '(?s)# Revalidate every installed artifact and the frozen user set immediately before code dispatch\..*?' +
        '\$observations\.Add\(\(Invoke-ExactInstrumentation\)\)'
)
if (-not $dispatchRevalidation.Success) {
    $violations.Add('all four installed artifacts are not revalidated immediately before instrumentation')
} else {
    $dispatchArtifactNames = @(
        [regex]::Matches(
            $dispatchRevalidation.Value,
            '\$(host|plugin|consumer|consumerTest)Artifact'
        ) | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique
    )
    if ([string]::Join(',', $dispatchArtifactNames) -cne 'host,plugin,consumer,consumerTest') {
        $violations.Add("pre-dispatch artifact revalidation set drifted: $($dispatchArtifactNames -join ',')")
    }
}

$artifactHashPins = [regex]::Matches(
    $runnerText,
    'sha256 = \$(?:host|plugin|consumer|consumerTest)Artifact\.Sha256'
).Count
$artifactSignerPins = [regex]::Matches(
    $runnerText,
    'signerSha256 = \$expectedSignerSha256'
).Count
if ($artifactHashPins -ne 4 -or $artifactSignerPins -ne 4) {
    $violations.Add("output must pin four APK hashes and signers; hashes=$artifactHashPins signers=$artifactSignerPins")
}

$requiredTestContracts = @(
    'class PythonRuntimeSameSignerDifferentUidAndroidTest',
    'fun sameSignerDifferentUidIsRejectedAtEveryProviderEntry()',
    'TARGET_PACKAGE = "org.autojs.plugin.python.runtime.consumer"',
    'HOST_PACKAGE = "org.autojs.autojs6"',
    'REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.python.runtime"',
    'assertEquals("Instrumentation must execute as the consumer target UID", targetUid, Process.myUid())',
    'val targetUidPackages = packageManager.getPackagesForUid(targetUid)?.toSet().orEmpty()',
    'assertTrue("Consumer UID does not own its target package", targetPackage in targetUidPackages)',
    'HOST_PACKAGE in targetUidPackages',
    'assertNotEquals("Fixture must not run as the canonical host UID", hostUid, targetUid)',
    'packageManager.checkSignatures(targetPackage, HOST_PACKAGE)',
    'packageManager.checkSignatures(targetPackage, REAL_PLUGIN_PACKAGE)',
    'ComponentName(REAL_PLUGIN_PACKAGE, REAL_PLUGIN_SERVICE)',
    'Intent().setComponent(expectedComponent)',
    'assertNull("Pinned hostile-caller bind must be action-null", intent.action)',
    'assertSynchronousSecurityException("getRuntimeInfo")',
    'assertSynchronousSecurityException("getCapabilities")',
    'assertSynchronousSecurityException("openSession")',
    'PythonRuntimeValidation.validateRequest(request)',
    'PythonRuntimeValidation.validateDescriptorReferences(request, 1)',
    'val encodedRequest = PythonRuntimeCodec.encodeExecutionRequest(request)',
    'arrayOf(sourceDescriptor)',
    'assertTrue("Hostile source PFD was not valid before dispatch", callerFileDescriptor.valid())',
    '"Security rejection closed the caller-owned source PFD"',
    'sourceDescriptor.close()',
    'assertFalse("Caller-owned source PFD remained valid after close", callerFileDescriptor.valid())',
    '"Unable to remove the hostile-caller source snapshot"',
    'assertEquals("Rejected openSession emitted an asynchronous callback", 0, callbackCount.get())',
    '"Rejected openSession returned a result file descriptor"',
    '0,',
    'receivedResultDescriptorCount.get()',
    'if (bound) context.unbindService(connection)'
)
$missingTestContracts = @(
    $requiredTestContracts |
        Where-Object { -not $testText.Contains($_, [StringComparison]::Ordinal) }
)
if ($missingTestContracts.Count -gt 0) {
    $violations.Add("instrumentation is missing required contracts: $($missingTestContracts -join '; ')")
}
$testMarkers = [regex]::Matches($testText, '(?m)^\s*@Test\s*$')
$testMethods = @(
    [regex]::Matches(
        $testText,
        '(?m)^\s*@Test\s*\r?\n\s*fun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\('
    ) | ForEach-Object { $_.Groups[1].Value }
)
$expectedTestMethods = @(
    'sameSignerDifferentUidIsRejectedAtEveryProviderEntry',
    'differentSignerCannotAcquirePluginPermissionOrBindProvider'
)
if (
    $testMarkers.Count -ne 2 -or
    $testMethods.Count -ne $testMarkers.Count -or
    [string]::Join(',', @($testMethods | Sort-Object)) -cne
        [string]::Join(',', @($expectedTestMethods | Sort-Object))
) {
    $violations.Add("instrumentation @Test allowlist drifted: $($testMethods -join ',')")
}
if ($testText -match '(?i)\b(?:Assume|assumeTrue|assumeFalse|assumeNotNull|assumeNoException)\b') {
    $violations.Add('instrumentation contains a skip or JUnit assumption')
}
$securityEntryLabels = @(
    [regex]::Matches(
        $testText,
        'assertSynchronousSecurityException\("(getRuntimeInfo|getCapabilities|openSession)"\)'
    ) | ForEach-Object { $_.Groups[1].Value }
)
if (
    $securityEntryLabels.Count -ne 3 -or
    [string]::Join(',', @($securityEntryLabels | Sort-Object)) -cne
        'getCapabilities,getRuntimeInfo,openSession'
) {
    $violations.Add("instrumentation does not reject every provider AIDL entry: $($securityEntryLabels -join ',')")
}

$manifestQueryPackages = @(
    [regex]::Matches(
        $manifestText,
        '<package\s+android:name="([^"]+)"\s*/>'
    ) | ForEach-Object { $_.Groups[1].Value }
)
$expectedManifestQueries = @(
    'org.autojs.autojs6',
    'io.github.supermonster003.autojs6.plugin.python.runtime'
)
if (
    [string]::Join(',', @($manifestQueryPackages | Sort-Object)) -cne
    [string]::Join(',', @($expectedManifestQueries | Sort-Object))
) {
    $violations.Add("consumer manifest query allowlist drifted: $($manifestQueryPackages -join ',')")
}
foreach ($manifestContract in @(
    '<uses-permission android:name="org.autojs.permission.PLUGIN" />',
    'android:testOnly="true"',
    'android:allowBackup="false"',
    'android:usesCleartextTraffic="false"'
)) {
    if (-not $manifestText.Contains($manifestContract, [StringComparison]::Ordinal)) {
        $violations.Add("consumer manifest is missing: $manifestContract")
    }
}

foreach ($buildContract in @(
    'namespace = "org.autojs.plugin.python.runtime.consumer"',
    'applicationId = "org.autojs.plugin.python.runtime.consumer"',
    'testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"',
    'assembleDebugAndroidTest',
    'python-runtime-api-debug.aar',
    'protocol-wire-api-debug.aar'
)) {
    if (-not $buildText.Contains($buildContract, [StringComparison]::Ordinal)) {
        $violations.Add("consumer build is missing: $buildContract")
    }
}

if ($violations.Count -gt 0) {
    throw (
        "R2 same-signer different-UID diagnostic static validation failed:" +
            [Environment]::NewLine +
            [string]::Join([Environment]::NewLine, $violations)
    )
}

[ordered]@{
    schema = 1
    status = 'PASS'
    scope = 'FILESYSTEM_STATIC_ONLY'
    runner = $runner
    instrumentation = $testSource
    exactProviderEntryCount = $providerMethods.Count
    frozenGlobalAndAllUserAbsenceContract = $true
    immutableArtifactCount = $immutableSnapshotCount
    exactArtifactCount = $artifactHashPins
    adbInvocationsExecuted = 0
    gradleInvocationsExecuted = 0
    sameSignerDifferentUidObserved = $false
    deviceAcceptance = $false
    formalReceiptWritten = $false
} | ConvertTo-Json -Depth 3
