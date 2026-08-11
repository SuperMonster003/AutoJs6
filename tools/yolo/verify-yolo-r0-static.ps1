[CmdletBinding()]
param(
    [string] $RepositoryRoot
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
    $RepositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
}

$script:CheckCount = 0

function Get-RequiredFile {
    param([Parameter(Mandatory = $true)][string] $RelativePath)

    $path = Join-Path $RepositoryRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Missing required file: $RelativePath"
    }
    $script:CheckCount++
    return $path
}

function Assert-Contains {
    param(
        [Parameter(Mandatory = $true)][string] $RelativePath,
        [Parameter(Mandatory = $true)][string] $Pattern
    )

    $path = Get-RequiredFile $RelativePath
    $text = Get-Content -Raw -LiteralPath $path
    if ($text -notmatch $Pattern) {
        throw "Required marker not found in ${RelativePath}: $Pattern"
    }
    $script:CheckCount++
}

function Assert-NotContains {
    param(
        [Parameter(Mandatory = $true)][string] $RelativePath,
        [Parameter(Mandatory = $true)][string] $Pattern
    )

    $path = Get-RequiredFile $RelativePath
    $text = Get-Content -Raw -LiteralPath $path
    if ($text -match $Pattern) {
        throw "Forbidden marker found in ${RelativePath}: $Pattern"
    }
    $script:CheckCount++
}

Assert-Contains "settings.gradle.kts" '"yolo-api"'
Assert-Contains "plugin-api/yolo-api/build.gradle.kts" 'project\(":plugin-api:protocol-wire-api"\)'
Assert-Contains "plugin-api/yolo-api/src/main/aidl/org/autojs/plugin/yolo/api/IYoloProvider.aidl" 'byte\[\]\s+getProviderInfo'
Assert-Contains "plugin-api/yolo-api/src/main/aidl/org/autojs/plugin/yolo/api/IYoloProvider.aidl" 'IYoloSession\s+openSession'
Assert-Contains "plugin-api/yolo-api/src/main/aidl/org/autojs/plugin/yolo/api/IYoloSession.aidl" 'void\s+detect'
Assert-Contains "plugin-api/yolo-api/src/main/aidl/org/autojs/plugin/yolo/api/IYoloSession.aidl" 'void\s+cancel'
Assert-Contains "plugin-api/yolo-api/src/main/aidl/org/autojs/plugin/yolo/api/IYoloSession.aidl" 'void\s+close'
Assert-Contains "plugin-api/yolo-api/src/main/aidl/org/autojs/plugin/yolo/api/IYoloCallback.aidl" 'void\s+onDetected'
Assert-Contains "plugin-api/yolo-api/src/main/aidl/org/autojs/plugin/yolo/api/IYoloCallback.aidl" 'void\s+onFailed'
Assert-Contains "plugin-api/yolo-api/src/main/aidl/org/autojs/plugin/yolo/api/IYoloCallback.aidl" 'void\s+onCancelled'
Assert-Contains "plugin-api/yolo-api/src/main/java/org/autojs/plugin/yolo/api/YoloContract.kt" 'SERVICE_ACTION\s*=\s*"org\.autojs\.plugin\.YOLO"'
Assert-Contains "plugin-api/yolo-api/src/main/java/org/autojs/plugin/yolo/api/YoloContract.kt" 'MAX_METADATA_BYTES'
Assert-Contains "plugin-api/yolo-api/src/main/java/org/autojs/plugin/yolo/api/YoloContract.kt" 'MAX_DETECTIONS\s*=\s*400'
Assert-Contains "plugin-api/yolo-api/src/main/java/org/autojs/plugin/yolo/api/YoloCodec.kt" 'TaggedWire'
Assert-Contains "plugin-api/yolo-api/src/main/java/org/autojs/plugin/yolo/api/YoloCodec.kt" 'requiredForReader\s*=\s*true'
Assert-Contains "plugin-api/yolo-api/src/main/java/org/autojs/plugin/yolo/api/YoloValidation.kt" 'descriptorCount'
Assert-Contains "plugin-api/yolo-api/src/main/java/org/autojs/plugin/yolo/api/YoloValidation.kt" 'sequence\s*>\s*0L'
Assert-Contains "plugin-api/yolo-api/src/main/java/org/autojs/plugin/yolo/api/YoloSessionLifecyclePolicy.kt" 'class\s+YoloSessionLifecyclePolicy'
Assert-Contains "plugin-api/yolo-api/src/main/java/org/autojs/plugin/yolo/api/YoloSessionLifecyclePolicy.kt" 'REPLAYED_OR_OUT_OF_ORDER'
Assert-NotContains "plugin-api/yolo-api/src/main/java/org/autojs/plugin/yolo/api/YoloContract.kt" 'System\.loadLibrary'
Assert-Contains "docs/dev/yolo-plugin-roadmap.md" 'default-off'
Assert-Contains "docs/dev/yolo-plugin-protocol-v1.md" 'retryOnRemoteException=false'

$appBuild = Get-Content -Raw -LiteralPath (Get-RequiredFile "app/build.gradle.kts")
$appDirectDependency = if ($appBuild -match 'plugin-api:yolo-api') { "ADDED" } else { "NOT_ADDED" }

Write-Output "YOLO_R0_STATIC=PASS CHECKS=$script:CheckCount SOURCE_ONLY=YES STATIC_MARKERS=PASS"
Write-Output "JVM_TESTS=NOT_RUN_BY_STATIC_SCRIPT ANDROID_BUILD=NOT_RUN_BY_STATIC_SCRIPT BINDER_PFD=NOT_RUN_BY_STATIC_SCRIPT NATIVE_RUNTIME=NOT_EVALUATED_BY_R0_GATE DEVICE_E2E=NOT_RUN_BY_STATIC_SCRIPT APP_DIRECT_DEPENDENCY=$appDirectDependency"
