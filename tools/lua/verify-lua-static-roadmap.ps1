[CmdletBinding()]
param(
    [string] $RepositoryRoot = (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)),
    [string] $PluginRoot = (Join-Path (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))) "AutoJs6-Plugin-Lua-Runtime"),
    [switch] $RequireR3
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$failures = [System.Collections.Generic.List[string]]::new()
$passes = [System.Collections.Generic.List[string]]::new()

function Add-Pass([string] $Message) {
    $passes.Add($Message)
}

function Add-Failure([string] $Message) {
    $failures.Add($Message)
}

function Resolve-RequiredDirectory([string] $Path, [string] $Label) {
    if (-not (Test-Path -LiteralPath $Path -PathType Container)) {
        throw "$Label does not exist: $Path"
    }
    return (Resolve-Path -LiteralPath $Path).Path
}

function Require-File([string] $Root, [string] $RelativePath) {
    $path = Join-Path $Root $RelativePath
    if (Test-Path -LiteralPath $path -PathType Leaf) {
        Add-Pass $RelativePath
        return $path
    }
    Add-Failure "Missing file: $RelativePath"
    return $null
}

function Read-RequiredText([string] $Root, [string] $RelativePath) {
    $path = Require-File $Root $RelativePath
    if ($null -eq $path) {
        return ""
    }
    return Get-Content -Raw -LiteralPath $path
}

function Require-Pattern(
    [string] $Text,
    [string] $Pattern,
    [string] $Description
) {
    if ($Text -match $Pattern) {
        Add-Pass $Description
    } else {
        Add-Failure "Missing or changed invariant: $Description"
    }
}

function Require-Xml([string] $Path, [string] $Description) {
    try {
        $document = [xml](Get-Content -Raw -LiteralPath $Path)
        Add-Pass "$Description parses as XML"
        return $document
    } catch {
        Add-Failure "$Description is not valid XML: $($_.Exception.Message)"
        return $null
    }
}

function Android-Attribute($Node, [string] $Name) {
    return $Node.GetAttribute($Name, "http://schemas.android.com/apk/res/android")
}

$root = Resolve-RequiredDirectory $RepositoryRoot "AutoJs6 repository"

$requiredHostFiles = @(
    "docs/lua/ROADMAP.md",
    "docs/dev/lua-runtime-protocol-v1.md",
    "docs/dev/lua-runtime-conformance.md",
    "plugin-api/lua-runtime-api/src/main/aidl/org/autojs/plugin/lua/runtime/api/ILuaRuntimeProvider.aidl",
    "app/src/main/java/org/autojs/autojs/core/plugin/lua/LuaBoundedBinderCallLane.kt",
    "app/src/main/java/org/autojs/autojs/engine/LuaPluginScriptEngine.kt",
    "app/src/main/java/org/autojs/autojs/engine/LuaSourceSnapshot.kt",
    "app/src/main/java/org/autojs/autojs/script/LuaScriptSource.kt",
    "app/src/main/java/org/autojs/autojs/script/LuaFileSource.kt",
    "app/src/test/java/org/autojs/autojs/core/plugin/lua/LuaServiceEnabledStateTest.kt",
    "app/src/test/java/org/autojs/autojs/util/LuaRestartRoutingTest.kt",
    "app/src/androidTest/java/org/autojs/autojs/core/plugin/lua/RawLuaRuntimeProviderClient.kt"
)
$requiredHostFiles | ForEach-Object { [void](Require-File $root $_) }

$settings = Read-RequiredText $root "settings.gradle.kts"
Require-Pattern $settings '"lua-runtime-api"' "lua-runtime-api is registered"
Require-Pattern $settings '"lua-runtime-conformance"' "lua-runtime-conformance is registered"

$appBuild = Read-RequiredText $root "app/build.gradle.kts"
Require-Pattern $appBuild 'project\(":plugin-api:lua-runtime-api"\)' "App depends on lua-runtime-api"
Require-Pattern $appBuild '(?s)buildFeatures\s*\{.*?aidl\s*=\s*true' "App AIDL generation is enabled"

$androidTransport = Read-RequiredText $root "app/src/main/java/org/autojs/autojs/core/plugin/lua/AndroidLuaRuntimeProviderTransport.kt"
Require-Pattern $androidTransport 'getComponentEnabledSetting' "Lua discovery reads component enabled overrides"
Require-Pattern $androidTransport '(?s)queryServices\(Intent\(LuaPluginActions\.INFO\)\)\s*\.filter\(::isServiceEffectivelyEnabled\)' "Lua INFO discovery admits only effectively enabled services"
Require-Pattern $androidTransport '(?s)queryServices\(runtimeIntent\)\s*\.filter\(::isServiceEffectivelyEnabled\)' "Lua runtime discovery admits only effectively enabled services"

$deviceEvidence = Read-RequiredText $root "docs/dev/lua-runtime-device-evidence-2026-08-10.md"
Require-Pattern $deviceEvidence 'all 19 methods passed' "R2 report records the complete method outcome"
Require-Pattern $deviceEvidence 'returned to the audited package/process\s+state' "R2 report records audited device restoration"

$nativeDeviceEvidence = Read-RequiredText $root "docs/dev/lua-runtime-native-device-evidence-2026-08-10.md"
Require-Pattern $nativeDeviceEvidence 'Every method was selected explicitly' "R3 native report records isolated method execution"
Require-Pattern $nativeDeviceEvidence 'Final state: both packages absent, no target PID, and no active\s+instrumentation' "R3 native report records audited device restoration"

$faultRecoveryEvidence = Read-RequiredText $root "docs/dev/lua-runtime-fault-recovery-device-evidence-2026-08-10.md"
Require-Pattern $faultRecoveryEvidence 'The two fault methods passed independently' "R3 fault report records both isolated method outcomes"
Require-Pattern $faultRecoveryEvidence 'returned to the audited package/process state' "R3 fault report records audited all-user restoration"
Require-Pattern $faultRecoveryEvidence 'per-user package\s+inventory for users `0` and `10`' "R3 fault report names every audited Android user"
Require-Pattern $faultRecoveryEvidence 'no current or uninstalled record for either package in either Android user' "R3 fault report records all-user package-setting removal"
Require-Pattern $faultRecoveryEvidence 'LuaRuntimeFaultRecoveryInstrumentationTest#nativeCrashCausesBinderDeathAndRecoversInANewProcess' "R3 fault report pins the crash selector"
Require-Pattern $faultRecoveryEvidence 'LuaRuntimeFaultRecoveryInstrumentationTest#nativeWedgeIsKilledByWatchdogAndRecoversInANewProcess' "R3 fault report pins the wedge selector"
Require-Pattern $faultRecoveryEvidence 'e8ed2bdc4d72d06314354aefb1e1a5f36b8343d5' "R3 fault report pins the canonical sibling revision"

$restartRouting = Read-RequiredText $root "app/src/main/java/org/autojs/autojs/util/IntentUtils.kt"
Require-Pattern $restartRouting 'resolveRestartScriptFile' "Restart recovery uses one source resolver"
Require-Pattern $restartRouting 'LuaScriptSource\.isLuaFileName\(script\)' "Restart recovery preserves Lua extensions"

$fileUtils = Read-RequiredText $root "app/src/main/java/org/autojs/autojs/util/FileUtils.kt"
Require-Pattern $fileUtils 'val LUA = TypeData\(IconData\("L"\), TYPE\.IDENTITY_EXECUTABLE or TYPE\.IDENTITY_TEXT_EDITABLE\)' ".lua has an executable and editable identity after the R1 proxy gate"
Require-Pattern $fileUtils 'LUA\("lua",\s*TypeDataHolder\.LUA\)' ".lua uses the gated Lua executable identity"

$appManifestPath = Require-File $root "app/src/main/AndroidManifest.xml"
if ($null -ne $appManifestPath) {
    $appManifest = Require-Xml $appManifestPath "App manifest"
    if ($null -ne $appManifest) {
        $namespace = [System.Xml.XmlNamespaceManager]::new($appManifest.NameTable)
        $namespace.AddNamespace("android", "http://schemas.android.com/apk/res/android")
        foreach ($action in @("org.autojs.plugin.INFO", "org.autojs.plugin.lua.RUNTIME")) {
            $escaped = $action.Replace("'", "&apos;")
            $node = $appManifest.SelectSingleNode("/manifest/queries/intent/action[@android:name='$escaped']", $namespace)
            if ($null -eq $node) {
                Add-Failure "App package visibility is missing $action"
            } else {
                Add-Pass "App package visibility includes $action"
            }
        }
    }
}

$fixtureFiles = @(
    "test-apps/lua-runtime-conformance/build.gradle.kts",
    "test-apps/lua-runtime-conformance/README.md",
    "test-apps/lua-runtime-conformance/src/main/AndroidManifest.xml",
    "test-apps/lua-runtime-conformance/src/main/aidl/org/autojs/plugin/lua/runtime/fake/control/IFakeLuaConformanceControl.aidl",
    "test-apps/lua-runtime-conformance/src/main/java/org/autojs/plugin/lua/runtime/fake/FakeLuaConformanceState.kt",
    "test-apps/lua-runtime-conformance/src/main/java/org/autojs/plugin/lua/runtime/fake/FakeLuaRuntimeScenario.kt",
    "test-apps/lua-runtime-conformance/src/main/java/org/autojs/plugin/lua/runtime/fake/service/FakeLuaConformanceControlService.kt",
    "test-apps/lua-runtime-conformance/src/main/java/org/autojs/plugin/lua/runtime/fake/service/FakeLuaPluginInfoService.kt",
    "test-apps/lua-runtime-conformance/src/main/java/org/autojs/plugin/lua/runtime/fake/service/FakeLuaRuntimeService.kt"
)
$fixtureFiles | ForEach-Object { [void](Require-File $root $_) }

$fixtureBuild = Read-RequiredText $root "test-apps/lua-runtime-conformance/build.gradle.kts"
Require-Pattern $fixtureBuild '(?s)beforeVariants\(selector\(\)\.withBuildType\("release"\)\).*?enable\s*=\s*false' "Fixture release variant is disabled"
Require-Pattern $fixtureBuild '(?s)buildFeatures\s*\{.*?aidl\s*=\s*true' "Fixture AIDL generation is enabled"
Require-Pattern $fixtureBuild 'project\(":plugin-api:lua-runtime-api"\)' "Fixture depends on lua-runtime-api"

$fixtureManifestPath = Join-Path $root "test-apps/lua-runtime-conformance/src/main/AndroidManifest.xml"
if (Test-Path -LiteralPath $fixtureManifestPath -PathType Leaf) {
    $fixtureManifest = Require-Xml $fixtureManifestPath "Lua conformance manifest"
    if ($null -ne $fixtureManifest) {
        $application = $fixtureManifest.manifest.application
        if ((Android-Attribute $application "testOnly") -eq "true") {
            Add-Pass "Fixture application is testOnly"
        } else {
            Add-Failure "Fixture application must be android:testOnly=true"
        }

        $services = @($application.service)
        if ($services.Count -eq 17) {
            Add-Pass "Fixture declares 17 services"
        } else {
            Add-Failure "Fixture must declare 17 services, found $($services.Count)"
        }
        foreach ($service in $services) {
            $name = Android-Attribute $service "name"
            if ((Android-Attribute $service "process") -ne ":lua_runtime") {
                Add-Failure "$name is not isolated in :lua_runtime"
            }
            if ((Android-Attribute $service "exported") -ne "true") {
                Add-Failure "$name is not explicitly exported"
            }
            if ((Android-Attribute $service "permission") -ne "org.autojs.permission.PLUGIN") {
                Add-Failure "$name is not protected by the plugin permission"
            }
        }

        $infoServices = @($services | Where-Object {
            @($_.SelectNodes("intent-filter/action")) | Where-Object {
                (Android-Attribute $_ "name") -eq "org.autojs.plugin.INFO"
            }
        })
        $runtimeServices = @($services | Where-Object {
            @($_.SelectNodes("intent-filter/action")) | Where-Object {
                (Android-Attribute $_ "name") -eq "org.autojs.plugin.lua.RUNTIME"
            }
        })
        $controlServices = @($services | Where-Object {
            @($_.SelectNodes("intent-filter/action")) | Where-Object {
                (Android-Attribute $_ "name") -eq "org.autojs.plugin.lua.runtime.fake.CONTROL"
            }
        })
        if ($infoServices.Count -eq 2 -and $runtimeServices.Count -eq 14 -and $controlServices.Count -eq 1) {
            Add-Pass "Fixture service roles are exactly 2 INFO, 1 control, and 14 runtime"
        } else {
            Add-Failure "Fixture service-role drift: INFO=$($infoServices.Count), control=$($controlServices.Count), runtime=$($runtimeServices.Count)"
        }

        $enabledInfo = @($services | Where-Object {
            (Android-Attribute $_ "name") -like "*PluginInfoService" -and
                (Android-Attribute $_ "enabled") -eq "true"
        })
        $enabledRuntime = @($services | Where-Object {
            (Android-Attribute $_ "name") -like "*LuaRuntimeService" -and
                (Android-Attribute $_ "enabled") -eq "true"
        })
        $normalInfoEnabled = $enabledInfo.Count -eq 1 -and
            (Android-Attribute $enabledInfo[0] "name") -eq ".service.FakeLuaPluginInfoService"
        $normalRuntimeEnabled = $enabledRuntime.Count -eq 1 -and
            (Android-Attribute $enabledRuntime[0] "name") -eq ".service.NormalLuaRuntimeService"
        $hostileRuntimeDisabled = @($runtimeServices | Where-Object {
            (Android-Attribute $_ "name") -ne ".service.NormalLuaRuntimeService" -and
                (Android-Attribute $_ "enabled") -ne "false"
        }).Count -eq 0
        $controlEnabled = $controlServices.Count -eq 1 -and
            (Android-Attribute $controlServices[0] "enabled") -eq "true"
        if ($normalInfoEnabled -and $normalRuntimeEnabled -and $hostileRuntimeDisabled -and $controlEnabled) {
            Add-Pass "Fixture defaults to one INFO/runtime and explicitly disables every hostile runtime"
        } else {
            Add-Failure "Fixture default topology must contain normal INFO/runtime plus the control service"
        }

        $expectedScenarios = @(
            "normal",
            "malformed-runtime-info",
            "null-binding",
            "stall-runtime-info",
            "stall-create",
            "late-create-return-session",
            "callback-before-start",
            "duplicate-terminal",
            "ignore-cancel",
            "zero-credit-output",
            "sequence-gap",
            "sequence-replay",
            "wrong-request-id",
            "busy-second-create"
        )
        $scenarioValues = @($services | ForEach-Object {
            @($_.SelectNodes("meta-data")) | Where-Object {
                (Android-Attribute $_ "name") -eq "org.autojs.plugin.lua.runtime.fake.SCENARIO"
            } | ForEach-Object { Android-Attribute $_ "value" }
        } | Sort-Object -Unique)
        $missingScenarios = @($expectedScenarios | Where-Object { $_ -notin $scenarioValues })
        $unexpectedScenarios = @($scenarioValues | Where-Object { $_ -notin $expectedScenarios })
        if ($missingScenarios.Count -eq 0 -and $unexpectedScenarios.Count -eq 0) {
            Add-Pass "Fixture runtime scenario allowlist is exact"
        } else {
            Add-Failure "Fixture scenario drift; missing=[$($missingScenarios -join ', ')], unexpected=[$($unexpectedScenarios -join ', ')]"
        }

        $metadataIsUnique = $runtimeServices.Count -eq 14
        foreach ($service in $runtimeServices) {
            $scenarioNodes = @($service.SelectNodes("meta-data") | Where-Object {
                (Android-Attribute $_ "name") -eq "org.autojs.plugin.lua.runtime.fake.SCENARIO"
            })
            if ($scenarioNodes.Count -ne 1) {
                $metadataIsUnique = $false
            }
        }
        if ($metadataIsUnique -and $scenarioValues.Count -eq 14) {
            Add-Pass "Every runtime service has one unique scenario metadata value"
        } else {
            Add-Failure "Runtime scenario metadata is missing, duplicated, or shared"
        }

        $scenarioSource = Get-Content -Raw -LiteralPath (
            Join-Path $root "test-apps/lua-runtime-conformance/src/main/java/org/autojs/plugin/lua/runtime/fake/FakeLuaRuntimeScenario.kt"
        )
        $selectorSource = Get-Content -Raw -LiteralPath (
            Join-Path $root "test-apps/lua-runtime-conformance/src/main/java/org/autojs/plugin/lua/runtime/fake/service/FakeLuaConformanceControlService.kt"
        )
        $enumNames = @([regex]::Matches(
            $scenarioSource,
            '(?m)^\s*([A-Z][A-Z0-9_]*)\("[^"]+"\),?\s*$'
        ) | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
        $selectorNames = @([regex]::Matches(
            $selectorSource,
            'FakeLuaRuntimeScenario\.([A-Z][A-Z0-9_]*)\s+to\s+'
        ) | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
        if ($enumNames.Count -eq 14 -and @(Compare-Object $enumNames $selectorNames).Count -eq 0) {
            Add-Pass "Fixture enum and component selector cover the same 14 scenarios"
        } else {
            Add-Failure "Fixture enum and component selector scenario mappings differ"
        }
    }
}

$fixtureControl = Read-RequiredText $root "test-apps/lua-runtime-conformance/src/main/aidl/org/autojs/plugin/lua/runtime/fake/control/IFakeLuaConformanceControl.aidl"
$hostControlPath = "app/src/androidTest/aidl/org/autojs/plugin/lua/runtime/fake/control/IFakeLuaConformanceControl.aidl"
$hostControl = Read-RequiredText $root $hostControlPath
$hostControlClient = Read-RequiredText $root "app/src/androidTest/java/org/autojs/autojs/core/plugin/lua/FakeLuaConformanceControlClient.kt"
$hostConformance = Read-RequiredText $root "app/src/androidTest/java/org/autojs/autojs/core/plugin/lua/LuaRuntimeAndroidConformanceTest.kt"
$rawSourceClient = Get-Content -Raw -LiteralPath (
    Join-Path $root "app/src/androidTest/java/org/autojs/autojs/core/plugin/lua/RawLuaRuntimeProviderClient.kt"
)
foreach ($method in @(
    "reset",
    "selectScenario",
    "selectRuntimeAmbiguity",
    "releasePluginInfoStall",
    "releaseRuntimeInfoStall",
    "releaseCreateExecutionStall",
    "releaseAllStalls",
    "getCounters"
)) {
    Require-Pattern $fixtureControl "\b$method\s*\(" "Fixture control exposes $method"
    Require-Pattern $hostControl "\b$method\s*\(" "Host control copy exposes $method"
}
if ($fixtureControl -ceq $hostControl) {
    Add-Pass "Fixture and host control AIDL files are byte-for-byte identical"
} else {
    Add-Failure "Fixture and host control AIDL files differ"
}
Require-Pattern $hostControlClient 'Intent\(\)\.setComponent\(CONTROL_COMPONENT\)' "Host binds the fixture control by exact component"
Require-Pattern $hostConformance 'autojs\.luaRuntime\.conformance\.enabled' "Android conformance is opt-in"
Require-Pattern $hostConformance 'autojs\.luaRuntime\.conformance\.freshProcess' "Hostile Android cases require fresh-process admission"
if ($hostConformance -match 'executeShellCommand|UiAutomation|killProcess|forceStopPackage') {
    Add-Failure "Lua Android conformance must not mutate or kill the fixture through shell/process control"
} else {
    Add-Pass "Lua Android conformance uses no shell or process-kill control"
}

$fixtureRuntime = Get-Content -Raw -LiteralPath (
    Join-Path $root "test-apps/lua-runtime-conformance/src/main/java/org/autojs/plugin/lua/runtime/fake/service/FakeLuaRuntimeService.kt"
)
$androidTestCount = [regex]::Matches($hostConformance, '(?m)^\s*@Test\s*$').Count
$rawSourceTestMethods = @(
    "truncatedRawSourcePfdFailsBeforeSessionCreation",
    "trailingRawSourcePfdFailsBeforeSessionCreation",
    "digestMismatchedRawSourcePfdFailsBeforeSessionCreation"
)
$rawSourceTestsPresent = @($rawSourceTestMethods | Where-Object {
    $hostConformance -notmatch "fun\s+$([regex]::Escape($_))\s*\("
}).Count -eq 0
$rawSourceClientIsExactReadOnly =
    $rawSourceClient -match 'ParcelFileDescriptor\.MODE_READ_ONLY' -and
    $rawSourceClient -match '\.bindExact\('
$nullBindingCoverage =
    $hostConformance -match 'fun\s+runtimeNullBindingFailsClosedWithoutCreatingSession\s*\(' -and
    $hostConformance -match '(?s)fun\s+runtimeNullBindingFailsClosedWithoutCreatingSession\s*\(\).*?requiresFreshProcess\s*=\s*true' -and
    $hostConformance -match 'LuaRuntimeProviderRejection\.BIND_FAILED' -and
    $hostConformance -match 'const\s+val\s+NULL_BINDING\s*=\s*"null-binding"' -and
    $fixtureRuntime -match 'class\s+NullBindingLuaRuntimeService\s*:\s*Service\(\)' -and
    $fixtureRuntime -match 'intent\?\.action\s*==\s*null' -and
    $fixtureRuntime -match 'intent\?\.component\s*==\s*ComponentName\(this,\s*javaClass\)' -and
    $fixtureRuntime -match 'FakeLuaConformanceCounterContract\.NULL_BINDING_CALLS' -and
    $fixtureRuntime -match '(?s)class\s+NullBindingLuaRuntimeService.*?return\s+null'
if ($androidTestCount -eq 19 -and $rawSourceTestsPresent -and $rawSourceClientIsExactReadOnly -and $nullBindingCoverage) {
    Add-Pass "Host Android conformance declares 19 opt-in tests including raw source rejection and exact null binding"
} else {
    Add-Failure "Host Android conformance raw-source/null-binding coverage or test-count drift: $androidTestCount"
}

$fixtureState = Get-Content -Raw -LiteralPath (
    Join-Path $root "test-apps/lua-runtime-conformance/src/main/java/org/autojs/plugin/lua/runtime/fake/FakeLuaConformanceState.kt"
)
$counterConstants = @(
    "PLUGIN_INFO_CALLS", "PLUGIN_INFO_EXITS", "RUNTIME_INFO_CALLS", "RUNTIME_INFO_EXITS",
    "CREATE_CALLS", "CREATE_EXITS", "START_CALLS", "CANCEL_CALLS", "CLOSE_CALLS",
    "SOURCE_FDS_OPENED", "SOURCE_FDS_CLOSED", "SESSIONS_CREATED", "SESSIONS_FINISHED",
    "SOURCE_VALIDATIONS", "STARTED_CALLBACKS_SENT", "OUTPUT_CALLBACKS_SENT",
    "TERMINAL_CALLBACKS_SENT", "BUSY_REJECTIONS", "SOURCE_READ_ONLY_CHECKS",
    "SOURCE_READ_ONLY_CONFIRMED", "SOURCE_EARLY_EOF_FAILURES",
    "SOURCE_TRAILING_DATA_FAILURES", "SOURCE_DIGEST_MISMATCH_FAILURES", "NULL_BINDING_CALLS"
)
$counterMappingMatches = $true
for ($index = 0; $index -lt $counterConstants.Count; $index++) {
    $constant = [regex]::Escape($counterConstants[$index])
    $pattern = "const\s+val\s+$constant\s*=\s*$index\b"
    if ($fixtureState -notmatch $pattern -or $hostControlClient -notmatch $pattern) {
        $counterMappingMatches = $false
    }
}
$missingRawSourceCounterUses = @(
    foreach ($counter in @(
        "SOURCE_READ_ONLY_CHECKS", "SOURCE_READ_ONLY_CONFIRMED", "SOURCE_EARLY_EOF_FAILURES",
        "SOURCE_TRAILING_DATA_FAILURES", "SOURCE_DIGEST_MISMATCH_FAILURES"
    )) {
        $constant = [regex]::Escape($counter)
        if ($fixtureRuntime -notmatch "generation\.record\(FakeLuaConformanceCounterContract\.$constant\)") {
            $counter
        }
    }
)
if (
    $counterMappingMatches -and
    $missingRawSourceCounterUses.Count -eq 0 -and
    $fixtureState -match 'val\s+adopted\s*=\s*adoptIncoming\(incoming\)' -and
    $fixtureState -match 'fun\s+hasReadOnlyAccessMode\(\)' -and
    $fixtureState -match 'Os\.pwrite\(' -and
    $fixtureState -match 'ZERO_LENGTH_WRITE_PROBE\s*=\s*ByteArray\(0\)' -and
    $fixtureState -match 'OsConstants\.EBADF' -and
    $fixtureState -match 'const\s+val\s+SIZE\s*=\s*24\b' -and
    $hostControlClient -match 'const\s+val\s+SIZE\s*=\s*24\b'
) {
    Add-Pass "Fixture and host counter indexes match 24 entries, null binding, read-only flags, and incoming source ownership"
} else {
    Add-Failure "Fixture and host counter index mappings differ"
}

$ci = Read-RequiredText $root ".github/workflows/android.yml"
foreach ($task in @(
    ":plugin-api:lua-runtime-api:testDebugUnitTest",
    ":plugin-api:lua-runtime-api:assembleDebug",
    ":test-apps:lua-runtime-conformance:testDebugUnitTest",
    ":test-apps:lua-runtime-conformance:assembleDebug"
)) {
    Require-Pattern $ci ([regex]::Escape($task)) "CI names $task"
}

if (Test-Path -LiteralPath $PluginRoot -PathType Container) {
    $resolvedPlugin = (Resolve-Path -LiteralPath $PluginRoot).Path
    foreach ($file in @(
        "README.md",
        "ROADMAP.md",
        "settings.gradle.kts",
        "build.gradle.kts",
        "gradle.properties",
        ".github/workflows/ci.yml",
        "app/build.gradle.kts",
        "app/src/main/AndroidManifest.xml",
        "app/src/debug/AndroidManifest.xml",
        "app/src/debug/java/io/github/supermonster003/autojs6/plugin/lua/runtime/debug/LuaRuntimeFaultService.kt",
        "app/src/debug/java/io/github/supermonster003/autojs6/plugin/lua/runtime/debug/NativeLuaFaults.kt",
        "app/src/androidTest/java/io/github/supermonster003/autojs6/plugin/lua/runtime/LuaRuntimeFaultRecoveryInstrumentationTest.kt",
        "app/src/main/cpp/vendor/vendor-lock.json",
        "app/src/main/cpp/lua_runtime_jni.cpp",
        "docs/native-execution-core.md",
        "app/src/main/java/io/github/supermonster003/autojs6/plugin/lua/runtime/NativeLuaRuntime.kt",
        "app/src/main/java/io/github/supermonster003/autojs6/plugin/lua/runtime/execution/LuaCallbackDeliveryGate.kt",
        "app/src/main/java/io/github/supermonster003/autojs6/plugin/lua/runtime/execution/LuaExecutionRunner.kt",
        "app/src/main/java/io/github/supermonster003/autojs6/plugin/lua/runtime/execution/LuaExecutionSessionController.kt",
        "app/src/main/java/io/github/supermonster003/autojs6/plugin/lua/runtime/execution/LuaSerialExecutionWorker.kt",
        "app/src/main/java/io/github/supermonster003/autojs6/plugin/lua/runtime/execution/LuaSourceVerifier.kt",
        "app/src/main/java/io/github/supermonster003/autojs6/plugin/lua/runtime/execution/RetainedExecutionGate.kt",
        "app/src/main/java/io/github/supermonster003/autojs6/plugin/lua/runtime/execution/SingleActiveExecutionGate.kt",
        "app/src/main/java/io/github/supermonster003/autojs6/plugin/lua/runtime/service/LuaRuntimeExecutionManager.kt",
        "app/src/main/java/io/github/supermonster003/autojs6/plugin/lua/runtime/service/LuaRuntimeService.kt",
        "app/src/test/java/io/github/supermonster003/autojs6/plugin/lua/runtime/NativeLuaRuntimeBoundaryTest.kt",
        "app/src/test/java/io/github/supermonster003/autojs6/plugin/lua/runtime/execution/LuaCallbackDeliveryGateTest.kt",
        "app/src/test/java/io/github/supermonster003/autojs6/plugin/lua/runtime/execution/LuaExecutionSessionControllerTest.kt",
        "app/src/test/java/io/github/supermonster003/autojs6/plugin/lua/runtime/execution/LuaSourceVerifierTest.kt",
        "tools/verify_repository.py",
        "tools/verify_fault_harness_artifacts.ps1"
    )) {
        [void](Require-File $resolvedPlugin $file)
    }
    $pluginProperties = Read-RequiredText $resolvedPlugin "gradle.properties"
    Require-Pattern $pluginProperties '(?m)^autojs\.lua\.native\.enabled=false\s*$' "R3 native build remains default-off"
    Require-Pattern $pluginProperties '(?m)^autojs\.lua\.provider\.enabled=false\s*$' "R3 provider discovery remains default-off"
    Require-Pattern $pluginProperties '(?m)^autojs\.lua\.faultHarness\.enabled=false\s*$' "R3 fault harness remains default-off"

    $pluginBuild = Read-RequiredText $resolvedPlugin "app/build.gradle.kts"
    Require-Pattern $pluginBuild 'luaProviderEnabled\.get\(\)\s*&&\s*!luaNativeEnabled\.get\(\)' "R3 rejects provider enablement without native enablement"
    Require-Pattern $pluginBuild 'luaFaultHarnessEnabled\.get\(\)\s*&&\s*\(!luaNativeEnabled\.get\(\)\s*\|\|\s*luaProviderEnabled\.get\(\)\)' "R3 admits the fault harness only with native=true and provider=false"
    Require-Pattern $pluginBuild 'resValue\("bool",\s*"lua_runtime_provider_enabled",\s*luaProviderEnabled\.get\(\)\.toString\(\)\)' "R3 manifest enablement is generated from the provider gate"
    Require-Pattern $pluginBuild 'buildConfigField\("boolean",\s*"LUA_FAULT_HARNESS_ENABLED",\s*"false"\)' "R3 release BuildConfig hard-disables the fault harness"
    Require-Pattern $pluginBuild 'arguments\s*\+=\s*"-DAUTOJS_LUA_DEBUG_FAULT_HARNESS=OFF"' "R3 release native build hard-excludes fault JNI"
    Require-Pattern $pluginBuild 'testInstrumentationRunner\s*=\s*"androidx\.test\.runner\.AndroidJUnitRunner"' "R3 declares the native Android test runner"

    $pluginManifestPath = Join-Path $resolvedPlugin "app/src/main/AndroidManifest.xml"
    $pluginManifest = Require-Xml $pluginManifestPath "R3 plugin manifest"
    if ($null -ne $pluginManifest) {
        $pluginServices = @($pluginManifest.manifest.application.service)
        if ($pluginServices.Count -eq 2) {
            Add-Pass "R3 declares exactly INFO and runtime services"
        } else {
            Add-Failure "R3 must declare exactly two plugin services, found $($pluginServices.Count)"
        }
        foreach ($service in $pluginServices) {
            $name = Android-Attribute $service "name"
            if ((Android-Attribute $service "enabled") -ne "@bool/lua_runtime_provider_enabled") {
                Add-Failure "$name is not controlled by the R3 provider gate"
            }
            if ((Android-Attribute $service "process") -ne ":lua_runtime") {
                Add-Failure "$name is not isolated in the R3 :lua_runtime process"
            }
            if ((Android-Attribute $service "permission") -ne "org.autojs.permission.PLUGIN") {
                Add-Failure "$name is not protected by the plugin permission"
            }
        }
        if ($pluginServices.Count -eq 2) {
            Add-Pass "R3 services share the gated process and permission boundary"
        }
    }

    $faultManifestPath = Join-Path $resolvedPlugin "app/src/debug/AndroidManifest.xml"
    $faultManifest = Require-Xml $faultManifestPath "R3 debug fault manifest"
    if ($null -ne $faultManifest) {
        $faultServices = @($faultManifest.manifest.application.service)
        if (
            $faultServices.Count -eq 1 -and
            (Android-Attribute $faultServices[0] "name") -eq ".debug.LuaRuntimeFaultService" -and
            (Android-Attribute $faultServices[0] "enabled") -eq "@bool/lua_runtime_fault_harness_enabled" -and
            (Android-Attribute $faultServices[0] "exported") -eq "false" -and
            (Android-Attribute $faultServices[0] "process") -eq ":lua_runtime" -and
            @($faultServices[0].SelectNodes("intent-filter")).Count -eq 0
        ) {
            Add-Pass "R3 debug fault service is resource-gated, non-exported, actionless, and process-isolated"
        } else {
            Add-Failure "R3 debug fault service isolation boundary drifted"
        }
    }

    $runtimeService = Read-RequiredText $resolvedPlugin "app/src/main/java/io/github/supermonster003/autojs6/plugin/lua/runtime/service/LuaRuntimeService.kt"
    Require-Pattern $runtimeService 'override\s+fun\s+onBind\(intent:\s*Intent\?\):\s*IBinder\s*=\s*binder' "R3 runtime accepts actionless explicit binding"
    Require-Pattern $runtimeService 'NativeLuaRuntime\.requireReady\(\)' "R3 runtime rechecks the native/provider gate inside Binder entrypoints"
    Require-Pattern $runtimeService 'runner\s*=\s*selectLuaExecutionRunner\(BuildConfig\.LUA_NATIVE_ENABLED\)' "R3 service selects native execution only through the native build gate"

    $executionWorker = Read-RequiredText $resolvedPlugin "app/src/main/java/io/github/supermonster003/autojs6/plugin/lua/runtime/execution/LuaSerialExecutionWorker.kt"
    Require-Pattern $executionWorker 'ThreadPoolExecutor\(\s*1,\s*1,' "R3 execution worker has exactly one thread"
    Require-Pattern $executionWorker 'SynchronousQueue\(\)' "R3 execution worker has no backlog"
    Require-Pattern $executionWorker '(?s)^(?!.*prestartCoreThread).*executor\.execute\(task\)' "R3 first task starts the zero-backlog worker without a prestart race"

    $sourceVerifier = Read-RequiredText $resolvedPlugin "app/src/main/java/io/github/supermonster003/autojs6/plugin/lua/runtime/execution/LuaSourceVerifier.kt"
    Require-Pattern $sourceVerifier 'sourceLengthBytes' "R3 source verifier consumes the declared byte length"
    Require-Pattern $sourceVerifier 'input\.read\(\)\s*!=\s*-1' "R3 source verifier rejects trailing bytes"
    Require-Pattern $sourceVerifier 'LuaSha256\.digest\(source\)\s*!=\s*request\.sourceSha256' "R3 source verifier checks SHA-256"
    Require-Pattern $sourceVerifier 'CodingErrorAction\.REPORT' "R3 source verifier requires strict UTF-8"

    $executionManager = Read-RequiredText $resolvedPlugin "app/src/main/java/io/github/supermonster003/autojs6/plugin/lua/runtime/service/LuaRuntimeExecutionManager.kt"
    Require-Pattern $executionManager 'private\s+object\s+ProcessExecutionResources' "R3 process owns one shared execution admission boundary"
    Require-Pattern $executionManager 'SingleActiveExecutionGate<Any>\(\)' "R3 process admits one active execution"
    Require-Pattern $executionManager 'RetainedExecutionGate\(MAX_RETAINED_SESSIONS\)' "R3 process bounds retained sessions"
    Require-Pattern $executionManager 'MAX_RETAINED_SESSIONS\s*=\s*2\b' "R3 retained-session ceiling is two"
    Require-Pattern $executionManager 'minOf\(request\.timeoutMillis,\s*MAX_START_LEASE_MILLIS\)' "R3 unstarted sessions have a bounded lease"

    $sessionController = Read-RequiredText $resolvedPlugin "app/src/main/java/io/github/supermonster003/autojs6/plugin/lua/runtime/execution/LuaExecutionSessionController.kt"
    Require-Pattern $sessionController 'cleanupStarted\.compareAndSet\(false,\s*true\)' "R3 session cleanup has one owner"
    Require-Pattern $sessionController 'runnerTimeoutMillis\s*=\s*remainingTimeoutMillis\(\)' "R3 runner receives only its remaining deadline"
    Require-Pattern $sessionController 'LuaRuntimeContract\.MAX_OUTSTANDING_OUTPUT_CREDITS' "R3 output-credit accounting is bounded"
    Require-Pattern $sessionController 'terminalClaimed' "R3 session records one terminal owner"
    Require-Pattern $sessionController 'LuaCallbackDeliveryGate' "R3 callbacks use a close-aware delivery gate"
    if ($sessionController -match 'callbackLock') {
        Add-Failure "R3 session must not hold a callback monitor across Binder"
    } else {
        Add-Pass "R3 session holds no callback monitor across Binder"
    }

    $callbackGate = Read-RequiredText $resolvedPlugin "app/src/main/java/io/github/supermonster003/autojs6/plugin/lua/runtime/execution/LuaCallbackDeliveryGate.kt"
    Require-Pattern $callbackGate 'AtomicReference\(State\.OPEN\)' "R3 callback delivery is atomically claimed"
    Require-Pattern $callbackGate 'State\.CLOSE_PENDING' "R3 callback close waits for the in-flight delivery owner"

    $executionWatchdog = Read-RequiredText $resolvedPlugin "app/src/main/java/io/github/supermonster003/autojs6/plugin/lua/runtime/execution/LuaExecutionWatchdog.kt"
    Require-Pattern $executionWatchdog 'execution\.token\s*!==\s*token' "R3 watchdog actions are bound to the active execution token"
    Require-Pattern $executionWatchdog '(?s)immediateTermination\s*=\s*when\s*\{.*?poisonIfOwned\(execution\).*?return@synchronized\s+false' "R3 watchdog rejects dispatch when its deadline or stop grace already expired"

    $processWatchdog = Read-RequiredText $resolvedPlugin "app/src/main/java/io/github/supermonster003/autojs6/plugin/lua/runtime/service/LuaRuntimeProcessWatchdog.kt"
    Require-Pattern $processWatchdog '(?s)Process\.killProcess\(pid\).*?Runtime\.getRuntime\(\)\.halt' "R3 process watchdog has kill and fail-stop fallback paths"

    $nativeAndroidTest = Read-RequiredText $resolvedPlugin "app/src/androidTest/java/io/github/supermonster003/autojs6/plugin/lua/runtime/NativeLuaRuntimeInstrumentationTest.kt"
    Require-Pattern $nativeAndroidTest '(?s)\A(?!.*bindService\().*assertTrue\(BuildConfig\.LUA_NATIVE_ENABLED\).*assertFalse\(BuildConfig\.LUA_PROVIDER_ENABLED\)' "R3 native Android gate keeps the provider disabled and never binds it"
    Require-Pattern $nativeAndroidTest 'fun\s+infiniteLoopIsCancelledByHook\(\)' "R3 native Android gate exercises hook cancellation"
    Require-Pattern $nativeAndroidTest 'fun\s+allocatorLimitFailsClosedAndTheProcessRemainsReusable\(\)' "R3 native Android gate exercises allocator failure and same-process reuse"

    $faultService = Read-RequiredText $resolvedPlugin "app/src/debug/java/io/github/supermonster003/autojs6/plugin/lua/runtime/debug/LuaRuntimeFaultService.kt"
    Require-Pattern $faultService 'BuildConfig\.DEBUG\s*&&\s*BuildConfig\.LUA_FAULT_HARNESS_ENABLED' "R3 fault service rechecks its debug opt-in"
    Require-Pattern $faultService 'BuildConfig\.LUA_NATIVE_ENABLED\s*&&\s*!BuildConfig\.LUA_PROVIDER_ENABLED' "R3 fault service keeps production discovery disabled"
    Require-Pattern $faultService 'LuaRuntimeExecutionManager\(' "R3 fault service uses the production execution manager"
    Require-Pattern $faultService 'writeStrongBinder\(runtimeProvider\)' "R3 fault control exposes only a remote provider Binder"
    Require-Pattern $faultService 'executionManager\.create\(' "R3 fault provider uses the production session creation path"
    Require-Pattern $faultService 'NativeLuaExecutionRunner\.execute\(request\)' "R3 fault runner recovers through the real native adapter"

    $faultAndroidTest = Read-RequiredText $resolvedPlugin "app/src/androidTest/java/io/github/supermonster003/autojs6/plugin/lua/runtime/LuaRuntimeFaultRecoveryInstrumentationTest.kt"
    Require-Pattern $faultAndroidTest 'ParcelFileDescriptor\.open\(snapshot,\s*ParcelFileDescriptor\.MODE_READ_ONLY\)' "R3 fault client sends a read-only source PFD"
    Require-Pattern $faultAndroidTest 'provider\.createExecution\(' "R3 fault client creates a session through remote AIDL"
    Require-Pattern $faultAndroidTest 'session\.start\(\)' "R3 fault client explicitly starts the remote session"
    Require-Pattern $faultAndroidTest 'assertStartedWithoutTerminal\(\)' "R3 fault client requires started without a false terminal"
    Require-Pattern $faultAndroidTest 'assertNotEquals\("The Lua runtime PID was reused without recovery"' "R3 fault client requires a new process PID"
    Require-Pattern $faultAndroidTest 'assertEquals\(7L,\s*executeReturnSeven' "R3 fault client proves post-recovery execution"

    $faultArtifactGate = Read-RequiredText $resolvedPlugin "tools/verify_fault_harness_artifacts.ps1"
    Require-Pattern $faultArtifactGate 'InvocationStartedAtUtc' "R3 fault artifact gate binds outputs to one invocation"
    Require-Pattern $faultArtifactGate 'merged_manifest/release' "R3 fault artifact gate checks the release main manifest"
    Require-Pattern $faultArtifactGate 'CmakeDirectory\s*=\s*''RelWithDebInfo''' "R3 fault artifact gate maps the release CMake output exactly"
    Require-Pattern $faultArtifactGate 'LuaRuntimeService\.class' "R3 release exclusion has a fresh known-class positive control"
    Require-Pattern $faultArtifactGate 'NativeLuaFaults_nativeCrash' "R3 artifact gate checks crash JNI admission and exclusion"
    Require-Pattern $faultArtifactGate 'RELEASE_VARIANT_FAULT_HARNESS_EXCLUSION_PASS' "R3 fault gate labels unsigned release-variant evidence"

    $vendorLock = Read-RequiredText $resolvedPlugin "app/src/main/cpp/vendor/vendor-lock.json"
    Require-Pattern $vendorLock 'https://www\.lua\.org/ftp/lua-5\.4\.8\.tar\.gz' "R3 pins the official Lua 5.4.8 source"
    Require-Pattern $vendorLock '4f18ddae154e793e46eeab727c59ef1c0c0c2b744e7b94219710d76f530629ae' "R3 pins the official Lua 5.4.8 SHA-256"
} elseif ($RequireR3) {
    Add-Failure "Required R3 plugin repository does not exist: $PluginRoot"
} else {
    Write-Host "[DEFERRED] R3 plugin repository is not present: $PluginRoot"
}

$passes | ForEach-Object { Write-Host "[PASS] $_" }
if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Host "[FAIL] $_" -ForegroundColor Red }
    Write-Host "Lua static roadmap gate failed with $($failures.Count) error(s)."
    exit 1
}

Write-Host "Lua static roadmap gate passed with $($passes.Count) checks."
Write-Host "This is source/configuration evidence only; it does not compile AIDL/Kotlin/C++, run Binder, or touch a device."
