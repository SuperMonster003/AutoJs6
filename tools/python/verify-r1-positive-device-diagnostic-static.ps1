#Requires -Version 7.0

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$runner = Join-Path $PSScriptRoot 'run-r1-positive-device-diagnostic.ps1'
if (-not (Test-Path -LiteralPath $runner -PathType Leaf)) {
    throw "Missing R1 positive diagnostic runner: $runner"
}

$source = Get-Content -LiteralPath $runner -Raw
$required = @(
    "[ValidatePattern('^[A-Za-z0-9._:-]+$')]",
    '[switch] $ConfirmDeviceMutation',
    '[switch] $AllowTemporaryHostInstall',
    'elseif (-not $AllowTemporaryHostInstall.IsPresent)',
    '$hostInstalledByThisRun = $false',
    '$PSNativeCommandUseErrorActionPreference = $false',
    "'org.autojs.autojs6'",
    "'org.autojs.autojs6.test'",
    "'org.autojs.plugin.python.runtime.fake'",
    "'exactActionDiscoveryBindsWithNullActionAndReturnsValidatedMetadata'",
    "'realPfdNormalSessionEnforcesCreditsOutputResultAndCallbackUid'",
    "'reliablePipeProducerErrorRejectsByteExactSourceBeforeExecution'",
    "'autojs.python.r1.conformance.enabled'",
    "@('-s', `$script:ExactSerial) + `$AdbArguments",
    "@('install', '--user', '0', '-t', `$Artifact.Path)",
    "@('shell', 'pm', 'list', 'packages', '-u', `$PackageName)",
    "@('uninstall', `$PackageName)",
    'Install-MissingArtifact -Artifact $hostArtifact',
    "'PARTIAL_DIAGNOSTIC'",
    'acceptanceReceiptWritten = $false',
    'r1DeviceAcceptance = $false',
    'OK \(1 test\)',
    'INSTRUMENTATION_STATUS_CODE:\s*-3',
    'Assert-InstalledArtifact',
    'Assert-SafeMissingStateForTemporaryInstall',
    'Remove-OwnedArtifact'
)

$missing = @($required | Where-Object { -not $source.Contains($_, [StringComparison]::Ordinal) })
if ($missing.Count -gt 0) {
    throw "R1 positive diagnostic runner is missing required source contracts:`n$($missing -join "`n")"
}

$forbiddenRegexes = [ordered]@{
    'Gradle wrapper invocation' = '(?im)(?:^|[\\/])gradlew(?:\.bat)?\b'
    'connected or managed-device task' = '(?i)connectedAndroidTest|managedDevice'
    'implicit adb device enumeration' = '(?im)\badb(?:\.exe)?\s+devices\b'
    'replacing APK install' = "@\('install',\s*'-r'|@\('install',[^\r\n]*'--replace'"
    'formal R1 device receipt' = 'r1-device-conformance-gate\.json'
    'acceptance PASS status' = 'status\s*=\s*[''"]PASS[''"]'
}

$violations = [Collections.Generic.List[string]]::new()
foreach ($entry in $forbiddenRegexes.GetEnumerator()) {
    if ($source -match $entry.Value) {
        $violations.Add($entry.Key)
    }
}

$nativeAdbReferences = @(
    [regex]::Matches($source, '(?im)&\s*\$script:AdbExecutable\b') |
        ForEach-Object { $_.Value }
)
if ($nativeAdbReferences.Count -ne 0) {
    $violations.Add('ADB executable bypasses the exact-serial Invoke-NativeCapture wrapper')
}

$exactSerialCallCount = [regex]::Matches(
    $source,
    "@\('-s',\s*\`$script:ExactSerial\)\s*\+\s*\`$AdbArguments"
).Count
if ($exactSerialCallCount -ne 1) {
    $violations.Add("expected exactly one centralized -s serial injection, found $exactSerialCallCount")
}

$installOrder = @(
    [regex]::Matches(
        $source,
        'Install-MissingArtifact\s+-Artifact\s+\$(host|fake|test)Artifact'
    ) | ForEach-Object { $_.Groups[1].Value }
)
if ([string]::Join(',', $installOrder) -cne 'host,fake,test') {
    $violations.Add("temporary install order must be exactly host,fake,test; actual=$($installOrder -join ',')")
}

$restoreOrder = @(
    [regex]::Matches(
        $source,
        'Remove-OwnedArtifact\s+-Artifact\s+\$(host|fake|test)Artifact'
    ) | ForEach-Object { $_.Groups[1].Value }
)
if ([string]::Join(',', $restoreOrder) -cne 'test,fake,host') {
    $violations.Add("owned restore order must be exactly test,fake,host; actual=$($restoreOrder -join ',')")
}

if ($violations.Count -gt 0) {
    throw "R1 positive diagnostic static validation failed:`n$($violations -join "`n")"
}

[ordered]@{
    schema = 1
    status = 'PASS'
    scope = 'FILESYSTEM_STATIC_ONLY'
    runner = $runner
    adbInvocationsExecuted = 0
    gradleInvocationsExecuted = 0
    deviceAcceptance = $false
} | ConvertTo-Json -Depth 3
