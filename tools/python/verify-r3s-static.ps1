[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path

function Read-RequiredText {
    param([Parameter(Mandatory = $true)][string]$RelativePath)

    $path = Join-Path $repoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Missing R3-S source: $RelativePath"
    }
    return Get-Content -LiteralPath $path -Raw
}

function Assert-Markers {
    param(
        [Parameter(Mandatory = $true)][string]$RelativePath,
        [Parameter(Mandatory = $true)][string[]]$Markers
    )

    $text = Read-RequiredText $RelativePath
    foreach ($marker in $Markers) {
        if (-not $text.Contains($marker)) {
            throw "Missing R3-S marker '$marker' in $RelativePath"
        }
    }
}

function Assert-Pattern {
    param(
        [Parameter(Mandatory = $true)][string]$RelativePath,
        [Parameter(Mandatory = $true)][string]$Pattern,
        [Parameter(Mandatory = $true)][string]$Description
    )

    $text = Read-RequiredText $RelativePath
    if ($text -notmatch $Pattern) {
        throw "Missing R3-S structure '$Description' in $RelativePath"
    }
}

function Assert-ForbiddenMarkers {
    param(
        [Parameter(Mandatory = $true)][string]$RelativePath,
        [Parameter(Mandatory = $true)][string[]]$Markers
    )

    $text = Read-RequiredText $RelativePath
    foreach ($marker in $Markers) {
        if ($text.Contains($marker)) {
            throw "Forbidden R3-S fallback marker '$marker' in $RelativePath"
        }
    }
}

Assert-Markers 'app/src/main/java/org/autojs/autojs/script/PythonLaunchBoundaryPolicy.kt' @(
    'PYTHON_RUNTIME_NOT_ACTIVATED',
    'PYTHON_COMPILED_SOURCE_UNSUPPORTED',
    'PYTHON_URI_UNSUPPORTED',
    'application/x-python-code',
    'text/x-python',
    'URLDecoder.decode'
)

Assert-Markers 'app/src/main/java/org/autojs/autojs/script/ScriptLaunchSourceFactory.kt' @(
    'fun enforcePythonLaunchBoundary(vararg locations: String?)',
    'fun enforcePythonMimeBoundary(mimeType: String?)',
    'fun enforcePythonExternalUriBoundary(',
    'fun createPersistedPathLaunch(',
    'PersistedScriptLaunchPolicy.PYTHON_FILE_V1',
    'PersistedScriptLaunchPolicy.PYTHON_PROJECT_V1',
    'throw PythonProjectLaunchException(PythonProjectLaunchError.CONTEXT_INVALID)',
    'fun capturePersistedLaunchContext(path: String): PersistedScriptLaunchContext',
    'PythonLaunchBoundaryPolicy.enforceDefaultOff(path)',
    'PythonLaunchBoundaryPolicy.enforceDefaultOff(fileSource.file.path)'
)

Assert-Markers 'app/src/main/java/org/autojs/autojs/script/PersistedScriptLaunchContext.kt' @(
    'data class PersistedScriptLaunchContext(',
    'const val PYTHON_FILE_V1 = "python_file_v1"',
    'const val PYTHON_PROJECT_V1 = "python_project_v1"',
    'fun capture(path: String): PersistedScriptLaunchContext',
    'PythonLaunchBoundaryPolicy.classify(file.path)'
)

# Legacy auto-file entry remains guarded before RootAutomator can see a Python path.
Assert-Markers 'app/src/main/java/org/autojs/autojs/runtime/api/Engines.kt' @(
    'val resolvedPath = mScriptRuntime.files.nonNullPath(path)',
    'ScriptLaunchSourceFactory.enforcePythonLaunchBoundary(resolvedPath)',
    'AutoFileSource(resolvedPath)'
)

# Content documents stay import-only; they cannot bypass the semantic Python boundary.
Assert-Markers 'app/src/main/java/org/autojs/autojs/external/open/RunIntentActivity.java' @(
    'ScriptIntents.enforceExternalUriRequestShape(intent)',
    'ScriptIntents.enforceContentPythonExecutionBoundary(',
    'getContentResolver().getType(uri)'
)

Assert-Markers 'app/src/main/java/org/autojs/autojs/external/ScriptIntents.kt' @(
    'const val EXTRA_KEY_SOURCE_KIND = "source_kind"',
    'const val EXTRA_KEY_PROJECT_ROOT = "project_root"',
    'enforceDefaultOffPythonIntentBoundary(context, intent, path)',
    'ScriptLaunchSourceFactory.createPersistedPathLaunch(',
    'fun putCapturedPythonContext(intent: Intent, path: String): Intent',
    'fun enforceContentPythonExecutionBoundary('
)

Assert-Markers 'app/src/main/java/org/autojs/autojs/script/PythonRuntimeChannelPolicy.kt' @(
    'Boolean = if (debuggable) previewEnabled else releaseCandidateEnabled',
    'debuggable = BuildConfig.DEBUG',
    'previewEnabled = BuildConfig.PYTHON_RUNTIME_PREVIEW_ENABLED',
    'releaseCandidateEnabled = BuildConfig.PYTHON_RUNTIME_RELEASE_CANDIDATE_ENABLED'
)

Assert-Markers 'app/src/main/java/org/autojs/autojs/script/PythonScriptSource.kt' @(
    'final override val engineName: String',
    'const val ENGINE = "python"',
    'PythonRuntimeChannelPolicy.isEnabledForCurrentBuild'
)

Assert-Markers 'app/src/main/java/org/autojs/autojs/AbstractAutoJs.kt' @(
    'if (PythonRuntimeChannelPolicy.isEnabledForCurrentBuild)',
    'scriptEngineManager.registerEngine(PythonScriptSource.ENGINE)',
    'PythonPluginScriptEngine(applicationContext, globalConsole)'
)

Assert-Markers 'app/src/main/java/org/autojs/autojs/engine/PythonPluginScriptEngine.kt' @(
    'if (!PythonRuntimeChannelPolicy.isEnabledForCurrentBuild)',
    'PythonRuntimeHostError.PREVIEW_DISABLED',
    'private val client = PythonRuntimeClient(context, globalConsole)',
    'client.createExecution(fileSource)'
)

Assert-Markers 'app/src/main/java/org/autojs/autojs/core/plugin/python/PythonRuntimeClient.kt' @(
    'private val transport = PythonRuntimeOfficialTransport(applicationContext)',
    'requireRuntimeChannelEnabled()',
    'transport.bind('
)

Assert-Markers 'app/src/main/java/org/autojs/autojs/core/plugin/python/PythonRuntimeOfficialTransport.kt' @(
    'Intent().setComponent(OFFICIAL_COMPONENT)',
    'applicationContext.bindService(',
    'PluginTrustManager.resolveTrustInfo('
)

# The Python engine and source must not redirect to a legacy engine when admission fails.
Assert-ForbiddenMarkers 'app/src/main/java/org/autojs/autojs/engine/PythonPluginScriptEngine.kt' @(
    'RhinoJavaScriptEngine',
    'RootAutomatorEngine',
    'AutoFileSource',
    'JavaScriptSource',
    'ENGINE_NODEJS'
)

Assert-ForbiddenMarkers 'app/src/main/java/org/autojs/autojs/script/PythonScriptSource.kt' @(
    'RhinoJavaScriptEngine',
    'RootAutomatorEngine',
    'AutoFileSource',
    'JavaScriptSource',
    'ENGINE_NODEJS'
)

$appBuild = Read-RequiredText 'app/build.gradle.kts'
Assert-Pattern 'app/build.gradle.kts' '(?s)val\s+pythonRuntimePreviewEnabled\s*=\s*providers\.gradleProperty\("autojs\.python\.preview\.enabled"\).*?\.orElse\(false\)' 'preview channel defaults off'
Assert-Pattern 'app/build.gradle.kts' '(?s)val\s+pythonRuntimeReleaseCandidateEnabled\s*=\s*providers\.gradleProperty\("autojs\.python\.release\.candidate\.enabled"\).*?\.orElse\(false\)' 'release-candidate channel defaults off'
Assert-Markers 'app/build.gradle.kts' @(
    '"PYTHON_RUNTIME_PREVIEW_ENABLED"',
    '"PYTHON_RUNTIME_RELEASE_CANDIDATE_ENABLED"'
)
if ($appBuild -match '(?i)chaquopy|com\.chaquo\.python') {
    throw 'Host app must not embed the Python runtime'
}

$pythonNative = @(Get-ChildItem -LiteralPath (Join-Path $repoRoot 'app/src') -Recurse -File -Filter '*.so' |
    Where-Object { $_.Name -match '(?i)python|chaquopy' })
if ($pythonNative.Count -ne 0) {
    throw 'Host app contains a Python runtime native library'
}

Write-Output 'R3S_STATIC_GATE=PASS'
Write-Output 'SOURCE_MARKERS_PRESENT=YES_UNCOMPILED'
Write-Output 'ROUTING_EVIDENCE=UNCOMPILED_MARKERS_ONLY'
Write-Output 'PRODUCTION_PYTHON_EXECUTION_ROUTE=DEFAULT_OFF_BY_SOURCE_SENTINELS'
Write-Output 'PERSISTED_CONTEXT_ROUTING=PRESENT_BY_SOURCE_SENTINELS'
Write-Output 'ENGINE_ACTIVATION=SHARED_CHANNEL_POLICY_BY_SOURCE_SENTINELS'
Write-Output 'BINDER_TRANSPORT=EXPLICIT_OFFICIAL_COMPONENT_BY_SOURCE_SENTINELS'
Write-Output 'LEGACY_ENGINE_FALLBACK=ABSENT_IN_PYTHON_ENGINE_BY_SENTINEL'
Write-Output 'HOST_EMBEDDED_CPYTHON=ABSENT_BY_SENTINEL'
Write-Output 'CPYTHON_EXECUTION=NOT_PROVEN'
Write-Output 'DEVICE_EVIDENCE=NO'
Write-Output 'THIS_GATE_GRADLE_EXECUTED=NO'
Write-Output 'THIS_GATE_ADB_EXECUTED=NO'
Write-Output 'ACCEPTANCE_RECEIPT_WRITTEN=NO'
