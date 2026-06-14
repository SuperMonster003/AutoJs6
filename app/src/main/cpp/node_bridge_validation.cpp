#include "node_bridge_internal.h"

namespace autojs6::node_bridge::internal {

using namespace autojs6::node_bridge;

bool isInlineSystemServiceDeniedProbe(EmbeddedLifecycleJsProbeKind kind) {
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::InlineAccountDenied:
        case EmbeddedLifecycleJsProbeKind::InlinePackageManagerDenied:
        case EmbeddedLifecycleJsProbeKind::InlineIntentActivityDenied:
        case EmbeddedLifecycleJsProbeKind::InlineBroadcastDenied:
        case EmbeddedLifecycleJsProbeKind::InlineContentProviderDenied:
        case EmbeddedLifecycleJsProbeKind::InlineMediaStoreDenied:
        case EmbeddedLifecycleJsProbeKind::InlineDownloadManagerDenied:
        case EmbeddedLifecycleJsProbeKind::InlineInputMethodDenied:
        case EmbeddedLifecycleJsProbeKind::InlineAppOpsDenied:
        case EmbeddedLifecycleJsProbeKind::InlinePermissionManagerDenied:
        case EmbeddedLifecycleJsProbeKind::InlineSettingsDenied:
        case EmbeddedLifecycleJsProbeKind::InlinePowerManagerDenied:
        case EmbeddedLifecycleJsProbeKind::InlineKeyguardDenied:
        case EmbeddedLifecycleJsProbeKind::InlineWallpaperDenied:
        case EmbeddedLifecycleJsProbeKind::InlineShortcutManagerDenied:
        case EmbeddedLifecycleJsProbeKind::InlineAlarmManagerDenied:
        case EmbeddedLifecycleJsProbeKind::InlineJobSchedulerDenied:
        case EmbeddedLifecycleJsProbeKind::InlineWorkManagerDenied:
            return true;
        case EmbeddedLifecycleJsProbeKind::InlineClipboardListenerDenied:
        case EmbeddedLifecycleJsProbeKind::InlineNotificationListenerDenied:
        case EmbeddedLifecycleJsProbeKind::InlineAccessibilityControlDenied:
        case EmbeddedLifecycleJsProbeKind::InlineDevicePolicyDenied:
        case EmbeddedLifecycleJsProbeKind::InlineUsageStatsDenied:
        case EmbeddedLifecycleJsProbeKind::InlineVpnConnectivityDenied:
        case EmbeddedLifecycleJsProbeKind::InlineWifiManagerDenied:
        case EmbeddedLifecycleJsProbeKind::InlineTelephonySubscriptionDenied:
        case EmbeddedLifecycleJsProbeKind::InlineCameraManagerDenied:
        case EmbeddedLifecycleJsProbeKind::InlineAudioManagerDenied:
        case EmbeddedLifecycleJsProbeKind::InlineDisplayManagerDenied:
        case EmbeddedLifecycleJsProbeKind::InlinePrintManagerDenied:
            return true;
        default:
            return false;
    }
}

void putInlineSystemServiceDeniedSkippedPayload(
        std::vector<std::string>& payload,
        EmbeddedLifecycleJsProbeKind kind,
        const char* detail
) {
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::InlineAccountDenied:
            putInlineAccountDeniedSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::InlinePackageManagerDenied:
            putInlinePackageManagerDeniedSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineIntentActivityDenied:
            putInlineIntentActivityDeniedSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineBroadcastDenied:
            putInlineBroadcastDeniedSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineContentProviderDenied:
            putInlineContentProviderDeniedSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineMediaStoreDenied:
            putInlineMediaStoreDeniedSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineDownloadManagerDenied:
            putInlineDownloadManagerDeniedSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineInputMethodDenied:
            putInlineInputMethodDeniedSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineAppOpsDenied:
            putInlineAppOpsDeniedSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::InlinePermissionManagerDenied:
            putInlinePermissionManagerDeniedSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineSettingsDenied:
            putInlineSettingsDeniedSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::InlinePowerManagerDenied:
            putInlinePowerManagerDeniedSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineKeyguardDenied:
            putInlineKeyguardDeniedSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineWallpaperDenied:
            putInlineWallpaperDeniedSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineShortcutManagerDenied:
            putInlineShortcutManagerDeniedSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineAlarmManagerDenied:
            putInlineAlarmManagerDeniedSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineJobSchedulerDenied:
            putInlineJobSchedulerDeniedSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineWorkManagerDenied:
            putInlineWorkManagerDeniedSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineClipboardListenerDenied:
            putInlineClipboardListenerDeniedSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineNotificationListenerDenied:
            putInlineNotificationListenerDeniedSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineAccessibilityControlDenied:
            putInlineAccessibilityControlDeniedSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineDevicePolicyDenied:
            putInlineDevicePolicyDeniedSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineUsageStatsDenied:
            putInlineUsageStatsDeniedSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineVpnConnectivityDenied:
            putInlineVpnConnectivityDeniedSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineWifiManagerDenied:
            putInlineWifiManagerDeniedSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineTelephonySubscriptionDenied:
            putInlineTelephonySubscriptionDeniedSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineCameraManagerDenied:
            putInlineCameraManagerDeniedSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineAudioManagerDenied:
            putInlineAudioManagerDeniedSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineDisplayManagerDenied:
            putInlineDisplayManagerDeniedSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::InlinePrintManagerDenied:
            putInlinePrintManagerDeniedSkippedPayload(payload, detail);
            break;
        default:
            break;
    }
}

void putInlineSystemServiceDeniedFields(
        std::vector<std::string>& payload,
        EmbeddedLifecycleJsProbeKind kind,
        const std::string& text
) {
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::InlineAccountDenied:
            putInlineAccountDeniedFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::InlinePackageManagerDenied:
            putInlinePackageManagerDeniedFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineIntentActivityDenied:
            putInlineIntentActivityDeniedFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineBroadcastDenied:
            putInlineBroadcastDeniedFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineContentProviderDenied:
            putInlineContentProviderDeniedFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineMediaStoreDenied:
            putInlineMediaStoreDeniedFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineDownloadManagerDenied:
            putInlineDownloadManagerDeniedFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineInputMethodDenied:
            putInlineInputMethodDeniedFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineAppOpsDenied:
            putInlineAppOpsDeniedFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::InlinePermissionManagerDenied:
            putInlinePermissionManagerDeniedFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineSettingsDenied:
            putInlineSettingsDeniedFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::InlinePowerManagerDenied:
            putInlinePowerManagerDeniedFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineKeyguardDenied:
            putInlineKeyguardDeniedFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineWallpaperDenied:
            putInlineWallpaperDeniedFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineShortcutManagerDenied:
            putInlineShortcutManagerDeniedFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineAlarmManagerDenied:
            putInlineAlarmManagerDeniedFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineJobSchedulerDenied:
            putInlineJobSchedulerDeniedFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineWorkManagerDenied:
            putInlineWorkManagerDeniedFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineClipboardListenerDenied:
            putInlineClipboardListenerDeniedFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineNotificationListenerDenied:
            putInlineNotificationListenerDeniedFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineAccessibilityControlDenied:
            putInlineAccessibilityControlDeniedFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineDevicePolicyDenied:
            putInlineDevicePolicyDeniedFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineUsageStatsDenied:
            putInlineUsageStatsDeniedFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineVpnConnectivityDenied:
            putInlineVpnConnectivityDeniedFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineWifiManagerDenied:
            putInlineWifiManagerDeniedFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineTelephonySubscriptionDenied:
            putInlineTelephonySubscriptionDeniedFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineCameraManagerDenied:
            putInlineCameraManagerDeniedFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineAudioManagerDenied:
            putInlineAudioManagerDeniedFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::InlineDisplayManagerDenied:
            putInlineDisplayManagerDeniedFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::InlinePrintManagerDenied:
            putInlinePrintManagerDeniedFields(payload, text);
            break;
        default:
            break;
    }
}
bool isUserSourcePreflightProbe(EmbeddedLifecycleJsProbeKind kind) {
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::UserSourceDescriptor:
        case EmbeddedLifecycleJsProbeKind::UserSourceSize:
        case EmbeddedLifecycleJsProbeKind::UserSourceEncoding:
        case EmbeddedLifecycleJsProbeKind::UserSourceName:
        case EmbeddedLifecycleJsProbeKind::UserSourceWrapper:
        case EmbeddedLifecycleJsProbeKind::UserSourceStrictMode:
        case EmbeddedLifecycleJsProbeKind::UserSourceCapability:
        case EmbeddedLifecycleJsProbeKind::UserSourcePreflight:
            return true;
        default:
            return false;
    }
}

bool isControlledUserInlineProbe(EmbeddedLifecycleJsProbeKind kind) {
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineConstant:
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineStdout:
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineStderr:
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineReturnValue:
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineThrownError:
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlinePromise:
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineAsyncOrdering:
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineSummary:
            return true;
        default:
            return false;
    }
}

bool isUserFilePreflightProbe(EmbeddedLifecycleJsProbeKind kind) {
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::UserFileDescriptor:
        case EmbeddedLifecycleJsProbeKind::UserFileReadPolicy:
        case EmbeddedLifecycleJsProbeKind::UserFilePathNormalization:
        case EmbeddedLifecycleJsProbeKind::UserFileWorkingDirectory:
        case EmbeddedLifecycleJsProbeKind::UserFileSourceLoading:
        case EmbeddedLifecycleJsProbeKind::UserFileExecutionDryRun:
        case EmbeddedLifecycleJsProbeKind::UserFileErrorStackFilename:
        case EmbeddedLifecycleJsProbeKind::UserFileExecutionSummary:
            return true;
        default:
            return false;
    }
}

bool isEmbeddedScriptContractProbe(EmbeddedLifecycleJsProbeKind kind) {
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptRequest:
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptResult:
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptOutputEvent:
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptError:
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptTimeout:
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptCancellation:
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptProcessIsolation:
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptContract:
            return true;
        default:
            return false;
    }
}

bool isEmbeddedMvpReadinessProbe(EmbeddedLifecycleJsProbeKind kind) {
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpLifecycleReadiness:
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpSourceInputReadiness:
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpOutputReadiness:
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpErrorHandlingReadiness:
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpAsyncReadiness:
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpTimeoutIsolationReadiness:
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpSecurityPolicyReadiness:
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpGoNoGoReadiness:
            return true;
        default:
            return false;
    }
}

void putUserSourcePreflightProbeSkippedPayload(std::vector<std::string>& payload, EmbeddedLifecycleJsProbeKind kind, const char* detail) {
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::UserSourceDescriptor:
            putUserSourceDescriptorSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::UserSourceSize:
            putUserSourceSizeSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::UserSourceEncoding:
            putUserSourceEncodingSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::UserSourceName:
            putUserSourceNameSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::UserSourceWrapper:
            putUserSourceWrapperSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::UserSourceStrictMode:
            putUserSourceStrictModeSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::UserSourceCapability:
            putUserSourceCapabilitySkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::UserSourcePreflight:
            putUserSourcePreflightSkippedPayload(payload, detail);
            break;
        default:
            break;
    }
}

void putControlledUserInlineProbeSkippedPayload(std::vector<std::string>& payload, EmbeddedLifecycleJsProbeKind kind, const char* detail) {
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineConstant:
            putControlledUserInlineConstantSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineStdout:
            putControlledUserInlineStdoutSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineStderr:
            putControlledUserInlineStderrSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineReturnValue:
            putControlledUserInlineReturnValueSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineThrownError:
            putControlledUserInlineThrownErrorSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlinePromise:
            putControlledUserInlinePromiseSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineAsyncOrdering:
            putControlledUserInlineAsyncOrderingSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineSummary:
            putControlledUserInlineSummarySkippedPayload(payload, detail);
            break;
        default:
            break;
    }
}

void putUserFilePreflightProbeSkippedPayload(std::vector<std::string>& payload, EmbeddedLifecycleJsProbeKind kind, const char* detail) {
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::UserFileDescriptor:
            putUserFileDescriptorSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::UserFileReadPolicy:
            putUserFileReadPolicySkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::UserFilePathNormalization:
            putUserFilePathNormalizationSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::UserFileWorkingDirectory:
            putUserFileWorkingDirectorySkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::UserFileSourceLoading:
            putUserFileSourceLoadingSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::UserFileExecutionDryRun:
            putUserFileExecutionDryRunSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::UserFileErrorStackFilename:
            putUserFileErrorStackFilenameSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::UserFileExecutionSummary:
            putUserFileExecutionSummarySkippedPayload(payload, detail);
            break;
        default:
            break;
    }
}

void putEmbeddedScriptContractProbeSkippedPayload(std::vector<std::string>& payload, EmbeddedLifecycleJsProbeKind kind, const char* detail) {
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptRequest:
            putEmbeddedScriptRequestSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptResult:
            putEmbeddedScriptResultSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptOutputEvent:
            putEmbeddedScriptOutputEventSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptError:
            putEmbeddedScriptErrorSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptTimeout:
            putEmbeddedScriptTimeoutSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptCancellation:
            putEmbeddedScriptCancellationSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptProcessIsolation:
            putEmbeddedScriptProcessIsolationSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptContract:
            putEmbeddedScriptContractSkippedPayload(payload, detail);
            break;
        default:
            break;
    }
}

void putEmbeddedMvpReadinessProbeSkippedPayload(std::vector<std::string>& payload, EmbeddedLifecycleJsProbeKind kind, const char* detail) {
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpLifecycleReadiness:
            putEmbeddedMvpLifecycleReadinessSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpSourceInputReadiness:
            putEmbeddedMvpSourceInputReadinessSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpOutputReadiness:
            putEmbeddedMvpOutputReadinessSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpErrorHandlingReadiness:
            putEmbeddedMvpErrorHandlingReadinessSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpAsyncReadiness:
            putEmbeddedMvpAsyncReadinessSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpTimeoutIsolationReadiness:
            putEmbeddedMvpTimeoutIsolationReadinessSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpSecurityPolicyReadiness:
            putEmbeddedMvpSecurityPolicyReadinessSkippedPayload(payload, detail);
            break;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpGoNoGoReadiness:
            putEmbeddedMvpGoNoGoReadinessSkippedPayload(payload, detail);
            break;
        default:
            break;
    }
}

void putUserSourcePreflightProbeFields(std::vector<std::string>& payload, EmbeddedLifecycleJsProbeKind kind, const std::string& text) {
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::UserSourceDescriptor:
            putUserSourceDescriptorFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::UserSourceSize:
            putUserSourceSizeFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::UserSourceEncoding:
            putUserSourceEncodingFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::UserSourceName:
            putUserSourceNameFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::UserSourceWrapper:
            putUserSourceWrapperFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::UserSourceStrictMode:
            putUserSourceStrictModeFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::UserSourceCapability:
            putUserSourceCapabilityFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::UserSourcePreflight:
            putUserSourcePreflightFields(payload, text);
            break;
        default:
            break;
    }
}

void putControlledUserInlineProbeFields(std::vector<std::string>& payload, EmbeddedLifecycleJsProbeKind kind, const std::string& text) {
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineConstant:
            putControlledUserInlineConstantFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineStdout:
            putControlledUserInlineStdoutFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineStderr:
            putControlledUserInlineStderrFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineReturnValue:
            putControlledUserInlineReturnValueFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineThrownError:
            putControlledUserInlineThrownErrorFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlinePromise:
            putControlledUserInlinePromiseFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineAsyncOrdering:
            putControlledUserInlineAsyncOrderingFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineSummary:
            putControlledUserInlineSummaryFields(payload, text);
            break;
        default:
            break;
    }
}

void putUserFilePreflightProbeFields(std::vector<std::string>& payload, EmbeddedLifecycleJsProbeKind kind, const std::string& text) {
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::UserFileDescriptor:
            putUserFileDescriptorFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::UserFileReadPolicy:
            putUserFileReadPolicyFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::UserFilePathNormalization:
            putUserFilePathNormalizationFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::UserFileWorkingDirectory:
            putUserFileWorkingDirectoryFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::UserFileSourceLoading:
            putUserFileSourceLoadingFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::UserFileExecutionDryRun:
            putUserFileExecutionDryRunFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::UserFileErrorStackFilename:
            putUserFileErrorStackFilenameFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::UserFileExecutionSummary:
            putUserFileExecutionSummaryFields(payload, text);
            break;
        default:
            break;
    }
}

void putEmbeddedScriptContractProbeFields(std::vector<std::string>& payload, EmbeddedLifecycleJsProbeKind kind, const std::string& text) {
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptRequest:
            putEmbeddedScriptRequestFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptResult:
            putEmbeddedScriptResultFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptOutputEvent:
            putEmbeddedScriptOutputEventFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptError:
            putEmbeddedScriptErrorFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptTimeout:
            putEmbeddedScriptTimeoutFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptCancellation:
            putEmbeddedScriptCancellationFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptProcessIsolation:
            putEmbeddedScriptProcessIsolationFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptContract:
            putEmbeddedScriptContractFields(payload, text);
            break;
        default:
            break;
    }
}

void putEmbeddedMvpReadinessProbeFields(std::vector<std::string>& payload, EmbeddedLifecycleJsProbeKind kind, const std::string& text) {
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpLifecycleReadiness:
            putEmbeddedMvpLifecycleReadinessFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpSourceInputReadiness:
            putEmbeddedMvpSourceInputReadinessFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpOutputReadiness:
            putEmbeddedMvpOutputReadinessFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpErrorHandlingReadiness:
            putEmbeddedMvpErrorHandlingReadinessFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpAsyncReadiness:
            putEmbeddedMvpAsyncReadinessFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpTimeoutIsolationReadiness:
            putEmbeddedMvpTimeoutIsolationReadinessFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpSecurityPolicyReadiness:
            putEmbeddedMvpSecurityPolicyReadinessFields(payload, text);
            break;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpGoNoGoReadiness:
            putEmbeddedMvpGoNoGoReadinessFields(payload, text);
            break;
        default:
            break;
    }
}

void putMetadataMappedInlineProbeSkippedPayload(std::vector<std::string>& payload, EmbeddedLifecycleJsProbeKind kind, const char* detail) {
    if (isInlineSystemServiceDeniedProbe(kind)) {
        putInlineSystemServiceDeniedSkippedPayload(payload, kind, detail);
        return;
    }
    if (isUserSourcePreflightProbe(kind)) {
        putUserSourcePreflightProbeSkippedPayload(payload, kind, detail);
        return;
    }
    if (isControlledUserInlineProbe(kind)) {
        putControlledUserInlineProbeSkippedPayload(payload, kind, detail);
        return;
    }
    if (isUserFilePreflightProbe(kind)) {
        putUserFilePreflightProbeSkippedPayload(payload, kind, detail);
        return;
    }
    if (isEmbeddedScriptContractProbe(kind)) {
        putEmbeddedScriptContractProbeSkippedPayload(payload, kind, detail);
        return;
    }
    if (isEmbeddedMvpReadinessProbe(kind)) {
        putEmbeddedMvpReadinessProbeSkippedPayload(payload, kind, detail);
    }
}

void putMetadataMappedInlineProbeFields(std::vector<std::string>& payload, EmbeddedLifecycleJsProbeKind kind, const std::string& text) {
    if (isInlineSystemServiceDeniedProbe(kind)) {
        putInlineSystemServiceDeniedFields(payload, kind, text);
        return;
    }
    if (isUserSourcePreflightProbe(kind)) {
        putUserSourcePreflightProbeFields(payload, kind, text);
        return;
    }
    if (isControlledUserInlineProbe(kind)) {
        putControlledUserInlineProbeFields(payload, kind, text);
        return;
    }
    if (isUserFilePreflightProbe(kind)) {
        putUserFilePreflightProbeFields(payload, kind, text);
        return;
    }
    if (isEmbeddedScriptContractProbe(kind)) {
        putEmbeddedScriptContractProbeFields(payload, kind, text);
        return;
    }
    if (isEmbeddedMvpReadinessProbe(kind)) {
        putEmbeddedMvpReadinessProbeFields(payload, kind, text);
    }
}

bool userSourcePreflightCommonValidationFailed(const std::string& resultText) {
    return resultText.rfind("error=", 0) == 0 ||
            jsonStringField(resultText, "executionMode") != "preflight_only" ||
            jsonStringField(resultText, "sourceKind") != "user_inline" ||
            jsonBooleanField(resultText, "userSourceUsed") ||
            jsonBooleanField(resultText, "userFileRead") ||
            !jsonBooleanField(resultText, "preflightOnly") ||
            jsonBooleanField(resultText, "dynamicSourceAllowed") ||
            !jsonBooleanField(resultText, "sequenceMonotonic") ||
            jsonNumberField(resultText, "eventCount") != "1" ||
            jsonNumberField(resultText, "responseCount") != "1" ||
            jsonStringField(resultText, "nodeVersion").find(kJsResultExpectedText) == std::string::npos;
}

bool userSourcePreflightValidationFailed(EmbeddedLifecycleJsProbeKind kind, const std::string& resultText) {
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::UserSourceDescriptor:
            return userSourcePreflightCommonValidationFailed(resultText) || jsonBooleanField(resultText, "requireAllowed") || jsonBooleanField(resultText, "importAllowed") || jsonBooleanField(resultText, "nodeModulesAllowed");
        case EmbeddedLifecycleJsProbeKind::UserSourceSize:
            return userSourcePreflightCommonValidationFailed(resultText) || jsonNumberField(resultText, "maxBytes") != "262144" || jsonNumberField(resultText, "actualBytes") != "0" || !jsonBooleanField(resultText, "withinLimit") || jsonBooleanField(resultText, "rejected") || !jsonStringField(resultText, "rejectReason").empty();
        case EmbeddedLifecycleJsProbeKind::UserSourceEncoding:
            return userSourcePreflightCommonValidationFailed(resultText) || jsonStringField(resultText, "inputEncoding") != "utf-8" || jsonStringField(resultText, "normalizedEncoding") != "utf-8" || jsonBooleanField(resultText, "bomDetected") || jsonBooleanField(resultText, "invalidSequenceDetected") || !jsonBooleanField(resultText, "normalizationSucceeded");
        case EmbeddedLifecycleJsProbeKind::UserSourceName:
            return userSourcePreflightCommonValidationFailed(resultText) || jsonStringField(resultText, "sourceName") != "<embedded-user-script>" || jsonBooleanField(resultText, "filenameUsed") || !jsonBooleanField(resultText, "displayNameSanitized") || !jsonBooleanField(resultText, "stackTraceNameAvailable");
        case EmbeddedLifecycleJsProbeKind::UserSourceWrapper:
            return userSourcePreflightCommonValidationFailed(resultText) || !jsonBooleanField(resultText, "wrapperEnabled") || jsonStringField(resultText, "wrapperKind") != "async_iife" || !jsonBooleanField(resultText, "returnValueCaptureEnabled") || !jsonBooleanField(resultText, "errorCaptureEnabled") || jsonBooleanField(resultText, "sourceMapUsed");
        case EmbeddedLifecycleJsProbeKind::UserSourceStrictMode:
            return userSourcePreflightCommonValidationFailed(resultText) || !jsonBooleanField(resultText, "strictModeEnabled") || !jsonBooleanField(resultText, "userStrictDirectivePreserved") || !jsonBooleanField(resultText, "wrapperStrictDirectiveAdded");
        case EmbeddedLifecycleJsProbeKind::UserSourceCapability:
            return userSourcePreflightCommonValidationFailed(resultText) || jsonBooleanField(resultText, "requireAllowed") || jsonBooleanField(resultText, "importAllowed") || jsonBooleanField(resultText, "npmAllowed") || jsonBooleanField(resultText, "nodeModulesAllowed") || jsonBooleanField(resultText, "fsAllowed") || jsonBooleanField(resultText, "networkAllowed") || jsonBooleanField(resultText, "autojsApiAllowed") || jsonBooleanField(resultText, "androidBridgeAllowed");
        case EmbeddedLifecycleJsProbeKind::UserSourcePreflight:
            return userSourcePreflightCommonValidationFailed(resultText) || !jsonBooleanField(resultText, "readyForControlledExecution") || jsonBooleanField(resultText, "userSourceExecutionEnabled") || jsonStringField(resultText, "nextStage") != "controlled_user_inline_execution";
        default:
            return false;
    }
}

bool controlledUserInlineCommonValidationFailed(const std::string& resultText) {
    return resultText.rfind("error=", 0) == 0 ||
            jsonStringField(resultText, "executionMode") != "controlled_user_inline" ||
            jsonStringField(resultText, "sourceKind") != "hardcoded_user_like" ||
            jsonBooleanField(resultText, "userSourceUsed") ||
            jsonBooleanField(resultText, "userFileRead") ||
            jsonBooleanField(resultText, "preflightOnly") ||
            jsonBooleanField(resultText, "dynamicSourceAllowed") ||
            jsonBooleanField(resultText, "requireAllowed") ||
            jsonBooleanField(resultText, "importAllowed") ||
            jsonBooleanField(resultText, "autojsApiAllowed") ||
            jsonBooleanField(resultText, "androidBridgeAllowed") ||
            !jsonBooleanField(resultText, "sequenceMonotonic") ||
            jsonNumberField(resultText, "eventCount") != "1" ||
            jsonNumberField(resultText, "responseCount") != "1" ||
            jsonStringField(resultText, "nodeVersion").find(kJsResultExpectedText) == std::string::npos;
}

bool controlledUserInlineValidationFailed(EmbeddedLifecycleJsProbeKind kind, const std::string& resultText) {
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineConstant:
            return controlledUserInlineCommonValidationFailed(resultText) ||
                    jsonStringField(resultText, "expression") != "1 + 1" ||
                    !jsonBooleanField(resultText, "resultCaptured") ||
                    jsonStringField(resultText, "resultType") != "number" ||
                    jsonStringField(resultText, "resultText") != "2";
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineStdout:
            return controlledUserInlineCommonValidationFailed(resultText) ||
                    !jsonBooleanField(resultText, "stdoutCaptureEnabled") ||
                    !jsonBooleanField(resultText, "stdoutWritten") ||
                    jsonStringField(resultText, "stdoutText") != "hello stdout" ||
                    jsonBooleanField(resultText, "stderrWritten") ||
                    jsonStringField(resultText, "resultText") != "hello stdout";
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineStderr:
            return controlledUserInlineCommonValidationFailed(resultText) ||
                    !jsonBooleanField(resultText, "stderrCaptureEnabled") ||
                    !jsonBooleanField(resultText, "stderrWritten") ||
                    jsonStringField(resultText, "stderrText") != "hello stderr" ||
                    jsonBooleanField(resultText, "stdoutWritten") ||
                    jsonStringField(resultText, "resultText") != "hello stderr";
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineReturnValue:
            return controlledUserInlineCommonValidationFailed(resultText) ||
                    !jsonBooleanField(resultText, "returnValueCaptureEnabled") ||
                    !jsonBooleanField(resultText, "returnValueCaptured") ||
                    jsonStringField(resultText, "returnValueType") != "object" ||
                    jsonStringField(resultText, "returnValueJson").find("\"ok\":true") == std::string::npos ||
                    jsonStringField(resultText, "returnValueJson").find("\"value\":42") == std::string::npos;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineThrownError:
            return controlledUserInlineCommonValidationFailed(resultText) ||
                    !jsonBooleanField(resultText, "errorCaptureEnabled") ||
                    !jsonBooleanField(resultText, "errorCaptured") ||
                    jsonStringField(resultText, "errorName") != "Error" ||
                    jsonStringField(resultText, "errorMessage") != "boom from controlled user inline" ||
                    !jsonBooleanField(resultText, "stackAvailable") ||
                    jsonBooleanField(resultText, "mainProcessCrashed") ||
                    !jsonBooleanField(resultText, "embeddedProcessIsolated") ||
                    jsonStringField(resultText, "resultText") != "error_captured";
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlinePromise:
            return controlledUserInlineCommonValidationFailed(resultText) ||
                    !jsonBooleanField(resultText, "promiseCreated") ||
                    !jsonBooleanField(resultText, "promiseCompleted") ||
                    jsonNumberField(resultText, "promiseValue") != "42" ||
                    jsonStringField(resultText, "stdoutText") != "42" ||
                    !jsonBooleanField(resultText, "spinEventLoopCompleted") ||
                    jsonStringField(resultText, "resultText") != "42";
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineAsyncOrdering:
            return controlledUserInlineCommonValidationFailed(resultText) ||
                    !jsonBooleanField(resultText, "nextTickAvailable") ||
                    !jsonBooleanField(resultText, "promiseAvailable") ||
                    !jsonBooleanField(resultText, "setImmediateAvailable") ||
                    jsonBooleanField(resultText, "setTimeoutAvailable") ||
                    jsonStringField(resultText, "ordering") != "sync:start -> sync:end -> nextTick -> promise -> setImmediate" ||
                    !jsonBooleanField(resultText, "orderingMatchesExpected") ||
                    jsonStringField(resultText, "resultText") != "sync:start -> sync:end -> nextTick -> promise -> setImmediate";
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineSummary:
            return controlledUserInlineCommonValidationFailed(resultText) ||
                    !jsonBooleanField(resultText, "inlineUserExecutionReady") ||
                    !jsonBooleanField(resultText, "constantExecutionReady") ||
                    !jsonBooleanField(resultText, "stdoutReady") ||
                    !jsonBooleanField(resultText, "stderrReady") ||
                    !jsonBooleanField(resultText, "returnValueReady") ||
                    !jsonBooleanField(resultText, "errorCaptureReady") ||
                    !jsonBooleanField(resultText, "promiseCompletionReady") ||
                    !jsonBooleanField(resultText, "asyncOrderingReady") ||
                    !jsonBooleanField(resultText, "readyForUserFilePreflight") ||
                    jsonStringField(resultText, "resultText") != "ready";
        default:
            return false;
    }
}

bool userFilePreflightCommonValidationFailed(const std::string& resultText) {
    return resultText.rfind("error=", 0) == 0 ||
            jsonStringField(resultText, "sourceKind") != "hardcoded_file_like" ||
            jsonBooleanField(resultText, "userSourceUsed") ||
            jsonBooleanField(resultText, "userFileRead") ||
            jsonBooleanField(resultText, "dynamicSourceAllowed") ||
            jsonBooleanField(resultText, "requireAllowed") ||
            jsonBooleanField(resultText, "importAllowed") ||
            jsonBooleanField(resultText, "autojsApiAllowed") ||
            jsonBooleanField(resultText, "androidBridgeAllowed") ||
            !jsonBooleanField(resultText, "sequenceMonotonic") ||
            jsonNumberField(resultText, "eventCount") != "1" ||
            jsonNumberField(resultText, "responseCount") != "1" ||
            jsonStringField(resultText, "nodeVersion").find(kJsResultExpectedText) == std::string::npos;
}

bool userFilePreflightValidationFailed(EmbeddedLifecycleJsProbeKind kind, const std::string& resultText) {
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::UserFileDescriptor:
            return userFilePreflightCommonValidationFailed(resultText) ||
                    jsonStringField(resultText, "executionMode") != "controlled_file_preflight" ||
                    !jsonBooleanField(resultText, "preflightOnly") ||
                    jsonStringField(resultText, "describedSourceKind") != "user_file" ||
                    !jsonBooleanField(resultText, "filePathPresent") ||
                    jsonStringField(resultText, "fileExists") != "diagnostic_only" ||
                    !jsonBooleanField(resultText, "fileExtensionSupported") ||
                    jsonStringField(resultText, "supportedExtensions") != ".js,.mjs,.cjs" ||
                    jsonBooleanField(resultText, "packageJsonRead") ||
                    jsonBooleanField(resultText, "nodeModulesRead") ||
                    jsonStringField(resultText, "resultText") != "descriptor_ready";
        case EmbeddedLifecycleJsProbeKind::UserFileReadPolicy:
            return userFilePreflightCommonValidationFailed(resultText) ||
                    jsonStringField(resultText, "executionMode") != "controlled_file_preflight" ||
                    !jsonBooleanField(resultText, "preflightOnly") ||
                    !jsonBooleanField(resultText, "targetFileReadAllowed") ||
                    jsonBooleanField(resultText, "arbitraryFsAccessAllowed") ||
                    jsonBooleanField(resultText, "packageJsonReadAllowed") ||
                    jsonBooleanField(resultText, "nodeModulesReadAllowed") ||
                    jsonBooleanField(resultText, "directoryListingAllowed") ||
                    jsonStringField(resultText, "symlinkFollowPolicy") != "restricted" ||
                    jsonNumberField(resultText, "maxFileBytes") != "262144" ||
                    jsonStringField(resultText, "resultText") != "read_policy_ready";
        case EmbeddedLifecycleJsProbeKind::UserFilePathNormalization:
            return userFilePreflightCommonValidationFailed(resultText) ||
                    jsonStringField(resultText, "executionMode") != "controlled_file_preflight" ||
                    !jsonBooleanField(resultText, "preflightOnly") ||
                    !jsonBooleanField(resultText, "absolutePathReady") ||
                    !jsonBooleanField(resultText, "canonicalPathReady") ||
                    !jsonBooleanField(resultText, "displayPathReady") ||
                    !jsonBooleanField(resultText, "pathTraversalChecked") ||
                    !jsonBooleanField(resultText, "allowedScopeChecked") ||
                    !jsonBooleanField(resultText, "pathNormalizationSucceeded") ||
                    jsonStringField(resultText, "resultText") != "path_normalization_ready";
        case EmbeddedLifecycleJsProbeKind::UserFileWorkingDirectory:
            return userFilePreflightCommonValidationFailed(resultText) ||
                    jsonStringField(resultText, "executionMode") != "controlled_file_preflight" ||
                    !jsonBooleanField(resultText, "preflightOnly") ||
                    !jsonBooleanField(resultText, "workingDirectoryReady") ||
                    !jsonBooleanField(resultText, "cwdMatchesScriptParent") ||
                    !jsonBooleanField(resultText, "cwdPassedToEnvironment") ||
                    !jsonBooleanField(resultText, "relativePathBaseDefined") ||
                    !jsonBooleanField(resultText, "processCwdExpected") ||
                    jsonStringField(resultText, "resultText") != "working_directory_ready";
        case EmbeddedLifecycleJsProbeKind::UserFileSourceLoading:
            return userFilePreflightCommonValidationFailed(resultText) ||
                    jsonStringField(resultText, "executionMode") != "controlled_file_preflight" ||
                    !jsonBooleanField(resultText, "preflightOnly") ||
                    !jsonBooleanField(resultText, "sourceLoadingReady") ||
                    !jsonBooleanField(resultText, "simulatedFileSourceUsed") ||
                    jsonStringField(resultText, "encoding") != "utf-8" ||
                    !jsonBooleanField(resultText, "bomHandled") ||
                    !jsonBooleanField(resultText, "lineCountAvailable") ||
                    !jsonBooleanField(resultText, "sourceBytesAvailable") ||
                    jsonStringField(resultText, "resultText") != "source_loading_ready";
        case EmbeddedLifecycleJsProbeKind::UserFileExecutionDryRun:
            return userFilePreflightCommonValidationFailed(resultText) ||
                    jsonStringField(resultText, "executionMode") != "controlled_file_dry_run" ||
                    jsonBooleanField(resultText, "preflightOnly") ||
                    !jsonBooleanField(resultText, "simulatedFileSourceUsed") ||
                    !jsonBooleanField(resultText, "stdoutCaptured") ||
                    !jsonBooleanField(resultText, "stdoutContainsHello") ||
                    !jsonBooleanField(resultText, "stdoutContainsNodeVersion") ||
                    jsonStringField(resultText, "resultText") != "file_dry_run_completed";
        case EmbeddedLifecycleJsProbeKind::UserFileErrorStackFilename:
            return userFilePreflightCommonValidationFailed(resultText) ||
                    jsonStringField(resultText, "executionMode") != "controlled_file_error_dry_run" ||
                    jsonBooleanField(resultText, "preflightOnly") ||
                    !jsonBooleanField(resultText, "errorCaptured") ||
                    jsonStringField(resultText, "errorName") != "Error" ||
                    jsonStringField(resultText, "errorMessage") != "file dry-run boom" ||
                    !jsonBooleanField(resultText, "stackAvailable") ||
                    !jsonBooleanField(resultText, "stackContainsSourceName") ||
                    jsonStringField(resultText, "sourceName") != "<embedded-user-file.js>" ||
                    jsonStringField(resultText, "resultText") != "error_captured";
        case EmbeddedLifecycleJsProbeKind::UserFileExecutionSummary:
            return userFilePreflightCommonValidationFailed(resultText) ||
                    jsonStringField(resultText, "executionMode") != "controlled_file_preflight" ||
                    !jsonBooleanField(resultText, "preflightOnly") ||
                    !jsonBooleanField(resultText, "fileExecutionPreflightReady") ||
                    !jsonBooleanField(resultText, "fileDescriptorReady") ||
                    !jsonBooleanField(resultText, "readPolicyReady") ||
                    !jsonBooleanField(resultText, "pathNormalizationReady") ||
                    !jsonBooleanField(resultText, "workingDirectoryReady") ||
                    !jsonBooleanField(resultText, "sourceLoadingReady") ||
                    !jsonBooleanField(resultText, "fileDryRunReady") ||
                    !jsonBooleanField(resultText, "errorStackFilenameReady") ||
                    !jsonBooleanField(resultText, "readyForRequestResultEnvelope") ||
                    jsonStringField(resultText, "resultText") != "ready_for_request_result_envelope";
        default:
            return false;
    }
}

bool embeddedScriptContractCommonValidationFailed(const std::string& resultText) {
    return resultText.rfind("error=", 0) == 0 ||
            jsonStringField(resultText, "executionMode") != "embedded_script_contract_diagnostics" ||
            jsonStringField(resultText, "sourceKind") != "hardcoded_contract" ||
            jsonBooleanField(resultText, "userSourceUsed") ||
            jsonBooleanField(resultText, "userFileRead") ||
            !jsonBooleanField(resultText, "preflightOnly") ||
            jsonBooleanField(resultText, "dynamicSourceAllowed") ||
            jsonBooleanField(resultText, "requireAllowed") ||
            jsonBooleanField(resultText, "importAllowed") ||
            jsonBooleanField(resultText, "autojsApiAllowed") ||
            jsonBooleanField(resultText, "androidBridgeAllowed") ||
            !jsonBooleanField(resultText, "sequenceMonotonic") ||
            jsonNumberField(resultText, "eventCount") != "1" ||
            jsonNumberField(resultText, "responseCount") != "1" ||
            jsonStringField(resultText, "nodeVersion").find(kJsResultExpectedText) == std::string::npos;
}

bool embeddedScriptContractValidationFailed(EmbeddedLifecycleJsProbeKind kind, const std::string& resultText) {
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptRequest:
            return embeddedScriptContractCommonValidationFailed(resultText) ||
                    !jsonBooleanField(resultText, "sourcePresent") ||
                    jsonStringField(resultText, "sourceKindDescriptor") != "inline_or_file" ||
                    !jsonBooleanField(resultText, "sourceNamePresent") ||
                    !jsonBooleanField(resultText, "workingDirectoryPresent") ||
                    !jsonBooleanField(resultText, "argvSupported") ||
                    jsonBooleanField(resultText, "envSupported") ||
                    !jsonBooleanField(resultText, "timeoutMsPresent") ||
                    !jsonBooleanField(resultText, "captureStdout") ||
                    !jsonBooleanField(resultText, "captureStderr") ||
                    jsonStringField(resultText, "resultText") != "request_envelope_ready";
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptResult:
            return embeddedScriptContractCommonValidationFailed(resultText) ||
                    !jsonBooleanField(resultText, "succeededPresent") ||
                    !jsonBooleanField(resultText, "exitCodePresent") ||
                    !jsonBooleanField(resultText, "resultTextPresent") ||
                    !jsonBooleanField(resultText, "stdoutPresent") ||
                    !jsonBooleanField(resultText, "stderrPresent") ||
                    !jsonBooleanField(resultText, "errorNamePresent") ||
                    !jsonBooleanField(resultText, "errorMessagePresent") ||
                    !jsonBooleanField(resultText, "errorStackPresent") ||
                    !jsonBooleanField(resultText, "elapsedMsPresent") ||
                    !jsonBooleanField(resultText, "processExitScheduled") ||
                    jsonStringField(resultText, "resultText") != "result_envelope_ready";
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptOutputEvent:
            return embeddedScriptContractCommonValidationFailed(resultText) ||
                    !jsonBooleanField(resultText, "eventEnvelopeReady") ||
                    !jsonBooleanField(resultText, "stdoutEventSupported") ||
                    !jsonBooleanField(resultText, "stderrEventSupported") ||
                    !jsonBooleanField(resultText, "sequenceSupported") ||
                    !jsonBooleanField(resultText, "timestampSupported") ||
                    !jsonBooleanField(resultText, "chunkTextSupported") ||
                    !jsonBooleanField(resultText, "truncationFlagSupported") ||
                    jsonStringField(resultText, "backpressurePolicy") != "buffered" ||
                    jsonStringField(resultText, "resultText") != "output_event_envelope_ready";
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptError:
            return embeddedScriptContractCommonValidationFailed(resultText) ||
                    !jsonBooleanField(resultText, "errorEnvelopeReady") ||
                    !jsonBooleanField(resultText, "syntaxErrorSupported") ||
                    !jsonBooleanField(resultText, "runtimeErrorSupported") ||
                    !jsonBooleanField(resultText, "nativeErrorSupported") ||
                    !jsonBooleanField(resultText, "timeoutErrorSupported") ||
                    !jsonBooleanField(resultText, "processCrashSupported") ||
                    !jsonBooleanField(resultText, "errorNamePresent") ||
                    !jsonBooleanField(resultText, "errorMessagePresent") ||
                    !jsonBooleanField(resultText, "errorStackPresent") ||
                    jsonStringField(resultText, "resultText") != "error_envelope_ready";
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptTimeout:
            return embeddedScriptContractCommonValidationFailed(resultText) ||
                    !jsonBooleanField(resultText, "timeoutPolicyReady") ||
                    !jsonBooleanField(resultText, "timeoutMsPresent") ||
                    jsonBooleanField(resultText, "timeoutTriggered") ||
                    jsonStringField(resultText, "timeoutErrorCode") != "EMBEDDED_SCRIPT_TIMEOUT" ||
                    !jsonBooleanField(resultText, "embeddedProcessTerminatedOnTimeout") ||
                    !jsonBooleanField(resultText, "mainProcessSurvivesTimeout") ||
                    jsonStringField(resultText, "resultText") != "timeout_envelope_ready";
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptCancellation:
            return embeddedScriptContractCommonValidationFailed(resultText) ||
                    !jsonBooleanField(resultText, "cancellationEnvelopeReady") ||
                    !jsonBooleanField(resultText, "cancelRequestedSupported") ||
                    jsonBooleanField(resultText, "cancelObservedSupported") ||
                    jsonStringField(resultText, "cancelErrorCode") != "EMBEDDED_SCRIPT_CANCELLED" ||
                    !jsonBooleanField(resultText, "bestEffortProcessTermination") ||
                    !jsonBooleanField(resultText, "mainProcessSurvivesCancel") ||
                    jsonStringField(resultText, "resultText") != "cancellation_envelope_ready";
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptProcessIsolation:
            return embeddedScriptContractCommonValidationFailed(resultText) ||
                    jsonStringField(resultText, "embeddedProcessName") != ":nodejs_embedded" ||
                    !jsonBooleanField(resultText, "mainProcessIsolated") ||
                    !jsonBooleanField(resultText, "singleLifecyclePerProcess") ||
                    !jsonBooleanField(resultText, "processExitScheduled") ||
                    !jsonBooleanField(resultText, "crashContainmentReady") ||
                    jsonBooleanField(resultText, "reuseRuntime") ||
                    jsonStringField(resultText, "resultText") != "process_isolation_ready";
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptContract:
            return embeddedScriptContractCommonValidationFailed(resultText) ||
                    !jsonBooleanField(resultText, "requestEnvelopeReady") ||
                    !jsonBooleanField(resultText, "resultEnvelopeReady") ||
                    !jsonBooleanField(resultText, "outputEventEnvelopeReady") ||
                    !jsonBooleanField(resultText, "errorEnvelopeReady") ||
                    !jsonBooleanField(resultText, "timeoutEnvelopeReady") ||
                    !jsonBooleanField(resultText, "cancellationEnvelopeReady") ||
                    !jsonBooleanField(resultText, "processIsolationReady") ||
                    !jsonBooleanField(resultText, "readyForMvpReadiness") ||
                    jsonStringField(resultText, "resultText") != "ready_for_mvp_readiness";
        default:
            return false;
    }
}

bool embeddedMvpReadinessCommonValidationFailed(const std::string& resultText) {
    return resultText.rfind("error=", 0) == 0 ||
            jsonStringField(resultText, "executionMode") != "embedded_mvp_readiness_diagnostics" ||
            jsonStringField(resultText, "sourceKind") != "hardcoded_mvp_readiness" ||
            jsonBooleanField(resultText, "userSourceUsed") ||
            jsonBooleanField(resultText, "userFileRead") ||
            !jsonBooleanField(resultText, "preflightOnly") ||
            jsonBooleanField(resultText, "dynamicSourceAllowed") ||
            jsonBooleanField(resultText, "requireAllowed") ||
            jsonBooleanField(resultText, "importAllowed") ||
            jsonBooleanField(resultText, "autojsApiAllowed") ||
            jsonBooleanField(resultText, "androidBridgeAllowed") ||
            !jsonBooleanField(resultText, "sequenceMonotonic") ||
            jsonNumberField(resultText, "eventCount") != "1" ||
            jsonNumberField(resultText, "responseCount") != "1" ||
            jsonStringField(resultText, "nodeVersion").find(kJsResultExpectedText) == std::string::npos;
}

bool embeddedMvpReadinessValidationFailed(EmbeddedLifecycleJsProbeKind kind, const std::string& resultText) {
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpLifecycleReadiness:
            return embeddedMvpReadinessCommonValidationFailed(resultText) ||
                    !jsonBooleanField(resultText, "lifecycleReady") ||
                    !jsonBooleanField(resultText, "initializeReady") ||
                    !jsonBooleanField(resultText, "isolateReady") ||
                    !jsonBooleanField(resultText, "environmentReady") ||
                    !jsonBooleanField(resultText, "loadEnvironmentReady") ||
                    !jsonBooleanField(resultText, "spinEventLoopReady") ||
                    !jsonBooleanField(resultText, "disposeReady") ||
                    !jsonBooleanField(resultText, "singleProcessLifecycleReady") ||
                    jsonStringField(resultText, "resultText") != "lifecycle_ready";
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpSourceInputReadiness:
            return embeddedMvpReadinessCommonValidationFailed(resultText) ||
                    !jsonBooleanField(resultText, "sourceInputReady") ||
                    !jsonBooleanField(resultText, "inlineSourceReady") ||
                    !jsonBooleanField(resultText, "fileSourceReady") ||
                    !jsonBooleanField(resultText, "sourceSizeLimitReady") ||
                    !jsonBooleanField(resultText, "sourceEncodingReady") ||
                    !jsonBooleanField(resultText, "sourceNameReady") ||
                    !jsonBooleanField(resultText, "workingDirectoryReady") ||
                    !jsonBooleanField(resultText, "userFileReadScopeLimited") ||
                    jsonStringField(resultText, "resultText") != "source_input_ready";
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpOutputReadiness:
            return embeddedMvpReadinessCommonValidationFailed(resultText) ||
                    !jsonBooleanField(resultText, "outputReady") ||
                    !jsonBooleanField(resultText, "stdoutCaptureReady") ||
                    !jsonBooleanField(resultText, "stderrCaptureReady") ||
                    !jsonBooleanField(resultText, "stdoutEventEnvelopeReady") ||
                    !jsonBooleanField(resultText, "stderrEventEnvelopeReady") ||
                    !jsonBooleanField(resultText, "consoleLogMinimalReady") ||
                    !jsonBooleanField(resultText, "outputBackpressurePolicyReady") ||
                    jsonStringField(resultText, "resultText") != "output_ready";
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpErrorHandlingReadiness:
            return embeddedMvpReadinessCommonValidationFailed(resultText) ||
                    !jsonBooleanField(resultText, "errorHandlingReady") ||
                    !jsonBooleanField(resultText, "syntaxErrorReady") ||
                    !jsonBooleanField(resultText, "runtimeErrorReady") ||
                    !jsonBooleanField(resultText, "thrownErrorReady") ||
                    !jsonBooleanField(resultText, "nativeErrorReady") ||
                    !jsonBooleanField(resultText, "errorStackReady") ||
                    !jsonBooleanField(resultText, "errorEnvelopeReady") ||
                    jsonStringField(resultText, "resultText") != "error_handling_ready";
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpAsyncReadiness:
            return embeddedMvpReadinessCommonValidationFailed(resultText) ||
                    !jsonBooleanField(resultText, "asyncCompletionReady") ||
                    !jsonBooleanField(resultText, "promiseReady") ||
                    !jsonBooleanField(resultText, "nextTickReady") ||
                    !jsonBooleanField(resultText, "setImmediateReady") ||
                    !jsonBooleanField(resultText, "asyncOrderingReady") ||
                    jsonBooleanField(resultText, "setTimeoutAvailable") ||
                    !jsonBooleanField(resultText, "timerAbsenceDocumented") ||
                    jsonStringField(resultText, "resultText") != "async_completion_ready";
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpTimeoutIsolationReadiness:
            return embeddedMvpReadinessCommonValidationFailed(resultText) ||
                    !jsonBooleanField(resultText, "timeoutIsolationReady") ||
                    !jsonBooleanField(resultText, "timeoutEnvelopeReady") ||
                    !jsonBooleanField(resultText, "cancellationEnvelopeReady") ||
                    !jsonBooleanField(resultText, "processIsolationReady") ||
                    !jsonBooleanField(resultText, "mainProcessSurvivesTimeout") ||
                    !jsonBooleanField(resultText, "mainProcessSurvivesCrash") ||
                    !jsonBooleanField(resultText, "singleLifecyclePerProcess") ||
                    jsonBooleanField(resultText, "reusableRuntime") ||
                    jsonStringField(resultText, "resultText") != "timeout_isolation_ready";
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpSecurityPolicyReadiness:
            return embeddedMvpReadinessCommonValidationFailed(resultText) ||
                    !jsonBooleanField(resultText, "securityPolicyReady") ||
                    jsonBooleanField(resultText, "npmAllowed") ||
                    jsonBooleanField(resultText, "nodeModulesAllowed") ||
                    jsonBooleanField(resultText, "packageJsonAllowed") ||
                    jsonBooleanField(resultText, "fsAllowed") ||
                    jsonBooleanField(resultText, "networkAllowed") ||
                    jsonBooleanField(resultText, "childProcessAllowed") ||
                    jsonBooleanField(resultText, "workerAllowed") ||
                    jsonBooleanField(resultText, "binderAllowed") ||
                    jsonStringField(resultText, "resultText") != "security_policy_ready";
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpGoNoGoReadiness:
            return embeddedMvpReadinessCommonValidationFailed(resultText) ||
                    !jsonBooleanField(resultText, "embeddedMvpReady") ||
                    !jsonBooleanField(resultText, "lifecycleReady") ||
                    !jsonBooleanField(resultText, "sourceInputReady") ||
                    !jsonBooleanField(resultText, "outputReady") ||
                    !jsonBooleanField(resultText, "errorHandlingReady") ||
                    !jsonBooleanField(resultText, "asyncCompletionReady") ||
                    !jsonBooleanField(resultText, "timeoutIsolationReady") ||
                    !jsonBooleanField(resultText, "securityPolicyReady") ||
                    jsonStringField(resultText, "nextStep") != "implement_embedded_user_script_execution_mvp" ||
                    jsonStringField(resultText, "resultText") != "go";
        default:
            return false;
    }
}

const InlineSystemServiceDeniedValidationSpec* inlineSystemServiceDeniedValidationSpec(EmbeddedLifecycleJsProbeKind kind) {
    static constexpr const char* kAccountFalseFields[] = {
            "accountBridgeAllowed", "accountGlobalInjected", "accountManagerAccessed", "accountsQueried",
            "authTokenRequested", "accountAdded", "accountRemoved", "permissionRequested",
            "androidContextAccessed", "androidApiCalled", "hostObjectInjected", "binderUsed",
    };
    static constexpr const char* kPackageManagerFalseFields[] = {
            "packageManagerBridgeAllowed", "packageManagerGlobalInjected", "packageManagerAccessed",
            "installedPackagesQueried", "applicationInfoQueried", "packageInfoQueried", "launchIntentQueried",
            "permissionInfoQueried", "androidContextAccessed", "androidApiCalled", "hostObjectInjected", "binderUsed",
    };
    static constexpr const char* kIntentActivityFalseFields[] = {
            "intentActivityBridgeAllowed", "intentGlobalInjected", "activityGlobalInjected", "intentCreated",
            "activityStarted", "activityResultRequested", "serviceStarted", "uriParsed",
            "androidContextAccessed", "androidApiCalled", "hostObjectInjected", "binderUsed",
    };
    static constexpr const char* kBroadcastFalseFields[] = {
            "broadcastBridgeAllowed", "broadcastGlobalInjected", "broadcastSent", "orderedBroadcastSent",
            "broadcastReceiverRegistered", "intentFilterCreated", "androidContextAccessed", "androidApiCalled",
            "hostObjectInjected", "binderUsed",
    };
    static constexpr const char* kContentProviderFalseFields[] = {
            "contentProviderBridgeAllowed", "contentProviderGlobalInjected", "contentResolverAccessed",
            "providerQueryAttempted", "providerInsertAttempted", "providerUpdateAttempted", "providerDeleteAttempted",
            "cursorOpened", "uriParsed", "permissionRequested", "androidContextAccessed", "androidApiCalled",
            "hostObjectInjected", "binderUsed",
    };
    static constexpr const char* kMediaStoreFalseFields[] = {
            "mediaStoreBridgeAllowed", "mediaStoreGlobalInjected", "contentResolverAccessed", "mediaStoreQueried",
            "imageMediaQueried", "videoMediaQueried", "audioMediaQueried", "mediaInsertAttempted",
            "mediaDeleteAttempted", "permissionRequested", "androidContextAccessed", "androidApiCalled",
            "hostObjectInjected", "binderUsed",
    };
    static constexpr const char* kDownloadManagerFalseFields[] = {
            "downloadManagerBridgeAllowed", "downloadManagerGlobalInjected", "downloadManagerAccessed",
            "downloadRequestCreated", "downloadEnqueued", "downloadQueryAttempted", "downloadRemoved", "uriParsed",
            "networkPermissionRequested", "androidContextAccessed", "androidApiCalled", "hostObjectInjected", "binderUsed",
    };
    static constexpr const char* kInputMethodFalseFields[] = {
            "inputMethodBridgeAllowed", "inputMethodGlobalInjected", "inputMethodManagerAccessed",
            "softKeyboardShown", "softKeyboardHidden", "inputConnectionAccessed", "textCommitted",
            "editorActionSent", "androidContextAccessed", "androidApiCalled", "hostObjectInjected", "binderUsed",
    };
    static constexpr const char* kAppOpsFalseFields[] = {
            "appOpsBridgeAllowed", "appOpsGlobalInjected", "appOpsManagerAccessed", "opChecked", "opNoted", "modeQueried", "packageOpsQueried", "permissionRequested", "androidContextAccessed", "androidApiCalled", "hostObjectInjected", "binderUsed",
    };
    static constexpr const char* kPermissionManagerFalseFields[] = {
            "permissionManagerBridgeAllowed", "permissionManagerGlobalInjected", "permissionManagerAccessed", "permissionChecked", "permissionRequested", "runtimePermissionRequested", "permissionGrantAttempted", "permissionRevokeAttempted", "androidContextAccessed", "androidApiCalled", "hostObjectInjected", "binderUsed",
    };
    static constexpr const char* kSettingsFalseFields[] = {
            "settingsBridgeAllowed", "settingsGlobalInjected", "settingsProviderAccessed", "settingsSystemQueried", "settingsSecureQueried", "settingsGlobalQueried", "settingsWriteAttempted", "canWriteSettingsChecked", "permissionRequested", "androidContextAccessed", "androidApiCalled", "hostObjectInjected", "binderUsed",
    };
    static constexpr const char* kPowerManagerFalseFields[] = {
            "powerManagerBridgeAllowed", "powerManagerGlobalInjected", "powerManagerAccessed", "wakeLockCreated", "wakeLockAcquired", "wakeLockReleased", "batteryOptimizationChecked", "interactiveStateQueried", "permissionRequested", "androidContextAccessed", "androidApiCalled", "hostObjectInjected", "binderUsed",
    };
    static constexpr const char* kKeyguardFalseFields[] = {
            "keyguardBridgeAllowed", "keyguardGlobalInjected", "keyguardManagerAccessed", "deviceLockedQueried", "keyguardLockedQueried", "keyguardDismissAttempted", "credentialConfirmationRequested", "permissionRequested", "androidContextAccessed", "androidApiCalled", "hostObjectInjected", "binderUsed",
    };
    static constexpr const char* kWallpaperFalseFields[] = {
            "wallpaperBridgeAllowed", "wallpaperGlobalInjected", "wallpaperManagerAccessed", "wallpaperReadAttempted", "wallpaperSetAttempted", "wallpaperClearAttempted", "bitmapRequired", "imageStreamOpened", "permissionRequested", "androidContextAccessed", "androidApiCalled", "hostObjectInjected", "binderUsed",
    };
    static constexpr const char* kShortcutManagerFalseFields[] = {
            "shortcutManagerBridgeAllowed", "shortcutManagerGlobalInjected", "shortcutManagerAccessed", "dynamicShortcutPushed", "pinnedShortcutRequested", "shortcutRemoved", "shortcutUpdated", "intentCreated", "androidContextAccessed", "androidApiCalled", "hostObjectInjected", "binderUsed",
    };
    static constexpr const char* kAlarmManagerFalseFields[] = {
            "alarmManagerBridgeAllowed", "alarmManagerGlobalInjected", "alarmManagerAccessed", "alarmScheduled", "exactAlarmScheduled", "alarmCancelled", "pendingIntentCreated", "permissionRequested", "androidContextAccessed", "androidApiCalled", "hostObjectInjected", "binderUsed",
    };
    static constexpr const char* kJobSchedulerFalseFields[] = {
            "jobSchedulerBridgeAllowed", "jobSchedulerGlobalInjected", "jobSchedulerAccessed", "jobInfoCreated", "jobScheduled", "jobCancelled", "jobServiceReferenced", "permissionRequested", "androidContextAccessed", "androidApiCalled", "hostObjectInjected", "binderUsed",
    };
    static constexpr const char* kWorkManagerFalseFields[] = {
            "workManagerBridgeAllowed", "workManagerGlobalInjected", "workManagerAccessed", "workRequestCreated", "workEnqueued", "workCancelled", "workerReferenced", "constraintCreated", "androidContextAccessed", "androidApiCalled", "hostObjectInjected", "binderUsed",
    };

    static constexpr InlineSystemServiceDeniedValidationSpec kAccountSpec = {
            "ACCOUNT_BRIDGE_DISABLED",
            "Account bridge is disabled in embedded probe runtime",
            "account_denied",
            kAccountFalseFields,
            sizeof(kAccountFalseFields) / sizeof(kAccountFalseFields[0]),
    };
    static constexpr InlineSystemServiceDeniedValidationSpec kPackageManagerSpec = {
            "PACKAGE_MANAGER_BRIDGE_DISABLED",
            "Package manager bridge is disabled in embedded probe runtime",
            "package_manager_denied",
            kPackageManagerFalseFields,
            sizeof(kPackageManagerFalseFields) / sizeof(kPackageManagerFalseFields[0]),
    };
    static constexpr InlineSystemServiceDeniedValidationSpec kIntentActivitySpec = {
            "INTENT_ACTIVITY_BRIDGE_DISABLED",
            "Intent/activity bridge is disabled in embedded probe runtime",
            "intent_activity_denied",
            kIntentActivityFalseFields,
            sizeof(kIntentActivityFalseFields) / sizeof(kIntentActivityFalseFields[0]),
    };
    static constexpr InlineSystemServiceDeniedValidationSpec kBroadcastSpec = {
            "BROADCAST_BRIDGE_DISABLED",
            "Broadcast bridge is disabled in embedded probe runtime",
            "broadcast_denied",
            kBroadcastFalseFields,
            sizeof(kBroadcastFalseFields) / sizeof(kBroadcastFalseFields[0]),
    };
    static constexpr InlineSystemServiceDeniedValidationSpec kContentProviderSpec = {
            "CONTENT_PROVIDER_BRIDGE_DISABLED",
            "Content provider bridge is disabled in embedded probe runtime",
            "content_provider_denied",
            kContentProviderFalseFields,
            sizeof(kContentProviderFalseFields) / sizeof(kContentProviderFalseFields[0]),
    };
    static constexpr InlineSystemServiceDeniedValidationSpec kMediaStoreSpec = {
            "MEDIA_STORE_BRIDGE_DISABLED",
            "Media store bridge is disabled in embedded probe runtime",
            "media_store_denied",
            kMediaStoreFalseFields,
            sizeof(kMediaStoreFalseFields) / sizeof(kMediaStoreFalseFields[0]),
    };
    static constexpr InlineSystemServiceDeniedValidationSpec kDownloadManagerSpec = {
            "DOWNLOAD_MANAGER_BRIDGE_DISABLED",
            "Download manager bridge is disabled in embedded probe runtime",
            "download_manager_denied",
            kDownloadManagerFalseFields,
            sizeof(kDownloadManagerFalseFields) / sizeof(kDownloadManagerFalseFields[0]),
    };
    static constexpr InlineSystemServiceDeniedValidationSpec kInputMethodSpec = {
            "INPUT_METHOD_BRIDGE_DISABLED",
            "Input method bridge is disabled in embedded probe runtime",
            "input_method_denied",
            kInputMethodFalseFields,
            sizeof(kInputMethodFalseFields) / sizeof(kInputMethodFalseFields[0]),
    };
    static constexpr InlineSystemServiceDeniedValidationSpec kAppOpsSpec = {
            "APP_OPS_BRIDGE_DISABLED",
            "App ops bridge is disabled in embedded probe runtime",
            "app_ops_denied",
            kAppOpsFalseFields,
            sizeof(kAppOpsFalseFields) / sizeof(kAppOpsFalseFields[0]),
    };
    static constexpr InlineSystemServiceDeniedValidationSpec kPermissionManagerSpec = {
            "PERMISSION_MANAGER_BRIDGE_DISABLED",
            "Permission manager bridge is disabled in embedded probe runtime",
            "permission_manager_denied",
            kPermissionManagerFalseFields,
            sizeof(kPermissionManagerFalseFields) / sizeof(kPermissionManagerFalseFields[0]),
    };
    static constexpr InlineSystemServiceDeniedValidationSpec kSettingsSpec = {
            "SETTINGS_BRIDGE_DISABLED",
            "Settings bridge is disabled in embedded probe runtime",
            "settings_denied",
            kSettingsFalseFields,
            sizeof(kSettingsFalseFields) / sizeof(kSettingsFalseFields[0]),
    };
    static constexpr InlineSystemServiceDeniedValidationSpec kPowerManagerSpec = {
            "POWER_MANAGER_BRIDGE_DISABLED",
            "Power manager bridge is disabled in embedded probe runtime",
            "power_manager_denied",
            kPowerManagerFalseFields,
            sizeof(kPowerManagerFalseFields) / sizeof(kPowerManagerFalseFields[0]),
    };
    static constexpr InlineSystemServiceDeniedValidationSpec kKeyguardSpec = {
            "KEYGUARD_BRIDGE_DISABLED",
            "Keyguard bridge is disabled in embedded probe runtime",
            "keyguard_denied",
            kKeyguardFalseFields,
            sizeof(kKeyguardFalseFields) / sizeof(kKeyguardFalseFields[0]),
    };
    static constexpr InlineSystemServiceDeniedValidationSpec kWallpaperSpec = {
            "WALLPAPER_BRIDGE_DISABLED",
            "Wallpaper bridge is disabled in embedded probe runtime",
            "wallpaper_denied",
            kWallpaperFalseFields,
            sizeof(kWallpaperFalseFields) / sizeof(kWallpaperFalseFields[0]),
    };
    static constexpr InlineSystemServiceDeniedValidationSpec kShortcutManagerSpec = {
            "SHORTCUT_MANAGER_BRIDGE_DISABLED",
            "Shortcut manager bridge is disabled in embedded probe runtime",
            "shortcut_manager_denied",
            kShortcutManagerFalseFields,
            sizeof(kShortcutManagerFalseFields) / sizeof(kShortcutManagerFalseFields[0]),
    };
    static constexpr InlineSystemServiceDeniedValidationSpec kAlarmManagerSpec = {
            "ALARM_MANAGER_BRIDGE_DISABLED",
            "Alarm manager bridge is disabled in embedded probe runtime",
            "alarm_manager_denied",
            kAlarmManagerFalseFields,
            sizeof(kAlarmManagerFalseFields) / sizeof(kAlarmManagerFalseFields[0]),
    };
    static constexpr InlineSystemServiceDeniedValidationSpec kJobSchedulerSpec = {
            "JOB_SCHEDULER_BRIDGE_DISABLED",
            "Job scheduler bridge is disabled in embedded probe runtime",
            "job_scheduler_denied",
            kJobSchedulerFalseFields,
            sizeof(kJobSchedulerFalseFields) / sizeof(kJobSchedulerFalseFields[0]),
    };
    static constexpr InlineSystemServiceDeniedValidationSpec kWorkManagerSpec = {
            "WORK_MANAGER_BRIDGE_DISABLED",
            "Work manager bridge is disabled in embedded probe runtime",
            "work_manager_denied",
            kWorkManagerFalseFields,
            sizeof(kWorkManagerFalseFields) / sizeof(kWorkManagerFalseFields[0]),
    };
    static constexpr const char* kClipboardListenerDeniedFalseFields[] = {
            "clipboardListenerBridgeAllowed", "clipboardListenerGlobalInjected", "clipboardManagerAccessed", "primaryClipListenerRegistered", "primaryClipListenerRemoved", "clipChangeObserved", "androidContextAccessed", "androidApiCalled", "hostObjectInjected", "binderUsed",
    };
    static constexpr const char* kNotificationListenerDeniedFalseFields[] = {
            "notificationListenerBridgeAllowed", "notificationListenerGlobalInjected", "notificationListenerServiceAccessed", "notificationListenerRegistered", "notificationsQueried", "notificationEventObserved", "permissionRequested", "androidContextAccessed", "androidApiCalled", "hostObjectInjected", "binderUsed",
    };
    static constexpr const char* kAccessibilityControlDeniedFalseFields[] = {
            "accessibilityControlBridgeAllowed", "accessibilityControlGlobalInjected", "accessibilityServiceAccessed", "serviceEnabledChecked", "serviceStartAttempted", "serviceStopAttempted", "accessibilitySettingsOpened", "gestureDispatchAttempted", "nodeActionAttempted", "permissionRequested", "androidContextAccessed", "androidApiCalled", "hostObjectInjected", "binderUsed",
    };
    static constexpr const char* kDevicePolicyDeniedFalseFields[] = {
            "devicePolicyBridgeAllowed", "devicePolicyGlobalInjected", "devicePolicyManagerAccessed", "adminActiveChecked", "lockNowAttempted", "wipeDataAttempted", "passwordPolicyQueried", "permissionRequested", "androidContextAccessed", "androidApiCalled", "hostObjectInjected", "binderUsed",
    };
    static constexpr const char* kUsageStatsDeniedFalseFields[] = {
            "usageStatsBridgeAllowed", "usageStatsGlobalInjected", "usageStatsManagerAccessed", "usageEventsQueried", "usageStatsQueried", "appStandbyBucketQueried", "permissionRequested", "androidContextAccessed", "androidApiCalled", "hostObjectInjected", "binderUsed",
    };
    static constexpr const char* kVpnConnectivityDeniedFalseFields[] = {
            "vpnConnectivityBridgeAllowed", "vpnConnectivityGlobalInjected", "connectivityManagerAccessed", "vpnServiceAccessed", "networkCapabilitiesQueried", "activeNetworkQueried", "vpnPrepareAttempted", "networkRequestCreated", "permissionRequested", "androidContextAccessed", "androidApiCalled", "hostObjectInjected", "binderUsed",
    };
    static constexpr const char* kWifiManagerDeniedFalseFields[] = {
            "wifiManagerBridgeAllowed", "wifiManagerGlobalInjected", "wifiManagerAccessed", "wifiInfoQueried", "scanResultsQueried", "scanStarted", "wifiNetworkSuggested", "permissionRequested", "androidContextAccessed", "androidApiCalled", "hostObjectInjected", "binderUsed",
    };
    static constexpr const char* kTelephonySubscriptionDeniedFalseFields[] = {
            "telephonySubscriptionBridgeAllowed", "telephonySubscriptionGlobalInjected", "subscriptionManagerAccessed", "activeSubscriptionInfoQueried", "simStateQueried", "carrierInfoQueried", "permissionRequested", "androidContextAccessed", "androidApiCalled", "hostObjectInjected", "binderUsed",
    };
    static constexpr const char* kCameraManagerDeniedFalseFields[] = {
            "cameraManagerBridgeAllowed", "cameraManagerGlobalInjected", "cameraManagerAccessed", "cameraIdListQueried", "cameraCharacteristicsQueried", "cameraOpenAttempted", "availabilityCallbackRegistered", "permissionRequested", "androidContextAccessed", "androidApiCalled", "hostObjectInjected", "binderUsed",
    };
    static constexpr const char* kAudioManagerDeniedFalseFields[] = {
            "audioManagerBridgeAllowed", "audioManagerGlobalInjected", "audioManagerAccessed", "volumeQueried", "volumeChanged", "ringerModeChanged", "audioFocusRequested", "microphoneMuteChanged", "permissionRequested", "androidContextAccessed", "androidApiCalled", "hostObjectInjected", "binderUsed",
    };
    static constexpr const char* kDisplayManagerDeniedFalseFields[] = {
            "displayManagerBridgeAllowed", "displayManagerGlobalInjected", "displayManagerAccessed", "displaysQueried", "displayListenerRegistered", "virtualDisplayCreated", "displayMetricsQueried", "permissionRequested", "androidContextAccessed", "androidApiCalled", "hostObjectInjected", "binderUsed",
    };
    static constexpr const char* kPrintManagerDeniedFalseFields[] = {
            "printManagerBridgeAllowed", "printManagerGlobalInjected", "printManagerAccessed", "printJobCreated", "printAdapterCreated", "printDocumentRequested", "printJobStateQueried", "permissionRequested", "androidContextAccessed", "androidApiCalled", "hostObjectInjected", "binderUsed",
    };
    static constexpr InlineSystemServiceDeniedValidationSpec kClipboardListenerDeniedSpec = {
            "CLIPBOARD_LISTENER_BRIDGE_DISABLED",
            "Clipboard listener bridge is disabled in embedded probe runtime",
            "clipboard_listener_denied",
            kClipboardListenerDeniedFalseFields,
            sizeof(kClipboardListenerDeniedFalseFields) / sizeof(kClipboardListenerDeniedFalseFields[0]),
    };
    static constexpr InlineSystemServiceDeniedValidationSpec kNotificationListenerDeniedSpec = {
            "NOTIFICATION_LISTENER_BRIDGE_DISABLED",
            "Notification listener bridge is disabled in embedded probe runtime",
            "notification_listener_denied",
            kNotificationListenerDeniedFalseFields,
            sizeof(kNotificationListenerDeniedFalseFields) / sizeof(kNotificationListenerDeniedFalseFields[0]),
    };
    static constexpr InlineSystemServiceDeniedValidationSpec kAccessibilityControlDeniedSpec = {
            "ACCESSIBILITY_CONTROL_BRIDGE_DISABLED",
            "Accessibility control bridge is disabled in embedded probe runtime",
            "accessibility_control_denied",
            kAccessibilityControlDeniedFalseFields,
            sizeof(kAccessibilityControlDeniedFalseFields) / sizeof(kAccessibilityControlDeniedFalseFields[0]),
    };
    static constexpr InlineSystemServiceDeniedValidationSpec kDevicePolicyDeniedSpec = {
            "DEVICE_POLICY_BRIDGE_DISABLED",
            "Device policy bridge is disabled in embedded probe runtime",
            "device_policy_denied",
            kDevicePolicyDeniedFalseFields,
            sizeof(kDevicePolicyDeniedFalseFields) / sizeof(kDevicePolicyDeniedFalseFields[0]),
    };
    static constexpr InlineSystemServiceDeniedValidationSpec kUsageStatsDeniedSpec = {
            "USAGE_STATS_BRIDGE_DISABLED",
            "Usage stats bridge is disabled in embedded probe runtime",
            "usage_stats_denied",
            kUsageStatsDeniedFalseFields,
            sizeof(kUsageStatsDeniedFalseFields) / sizeof(kUsageStatsDeniedFalseFields[0]),
    };
    static constexpr InlineSystemServiceDeniedValidationSpec kVpnConnectivityDeniedSpec = {
            "VPN_CONNECTIVITY_BRIDGE_DISABLED",
            "VPN/connectivity bridge is disabled in embedded probe runtime",
            "vpn_connectivity_denied",
            kVpnConnectivityDeniedFalseFields,
            sizeof(kVpnConnectivityDeniedFalseFields) / sizeof(kVpnConnectivityDeniedFalseFields[0]),
    };
    static constexpr InlineSystemServiceDeniedValidationSpec kWifiManagerDeniedSpec = {
            "WIFI_MANAGER_BRIDGE_DISABLED",
            "Wifi manager bridge is disabled in embedded probe runtime",
            "wifi_manager_denied",
            kWifiManagerDeniedFalseFields,
            sizeof(kWifiManagerDeniedFalseFields) / sizeof(kWifiManagerDeniedFalseFields[0]),
    };
    static constexpr InlineSystemServiceDeniedValidationSpec kTelephonySubscriptionDeniedSpec = {
            "TELEPHONY_SUBSCRIPTION_BRIDGE_DISABLED",
            "Telephony subscription bridge is disabled in embedded probe runtime",
            "telephony_subscription_denied",
            kTelephonySubscriptionDeniedFalseFields,
            sizeof(kTelephonySubscriptionDeniedFalseFields) / sizeof(kTelephonySubscriptionDeniedFalseFields[0]),
    };
    static constexpr InlineSystemServiceDeniedValidationSpec kCameraManagerDeniedSpec = {
            "CAMERA_MANAGER_BRIDGE_DISABLED",
            "Camera manager bridge is disabled in embedded probe runtime",
            "camera_manager_denied",
            kCameraManagerDeniedFalseFields,
            sizeof(kCameraManagerDeniedFalseFields) / sizeof(kCameraManagerDeniedFalseFields[0]),
    };
    static constexpr InlineSystemServiceDeniedValidationSpec kAudioManagerDeniedSpec = {
            "AUDIO_MANAGER_BRIDGE_DISABLED",
            "Audio manager bridge is disabled in embedded probe runtime",
            "audio_manager_denied",
            kAudioManagerDeniedFalseFields,
            sizeof(kAudioManagerDeniedFalseFields) / sizeof(kAudioManagerDeniedFalseFields[0]),
    };
    static constexpr InlineSystemServiceDeniedValidationSpec kDisplayManagerDeniedSpec = {
            "DISPLAY_MANAGER_BRIDGE_DISABLED",
            "Display manager bridge is disabled in embedded probe runtime",
            "display_manager_denied",
            kDisplayManagerDeniedFalseFields,
            sizeof(kDisplayManagerDeniedFalseFields) / sizeof(kDisplayManagerDeniedFalseFields[0]),
    };
    static constexpr InlineSystemServiceDeniedValidationSpec kPrintManagerDeniedSpec = {
            "PRINT_MANAGER_BRIDGE_DISABLED",
            "Print manager bridge is disabled in embedded probe runtime",
            "print_manager_denied",
            kPrintManagerDeniedFalseFields,
            sizeof(kPrintManagerDeniedFalseFields) / sizeof(kPrintManagerDeniedFalseFields[0]),
    };

    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::InlineAccountDenied:
            return &kAccountSpec;
        case EmbeddedLifecycleJsProbeKind::InlinePackageManagerDenied:
            return &kPackageManagerSpec;
        case EmbeddedLifecycleJsProbeKind::InlineIntentActivityDenied:
            return &kIntentActivitySpec;
        case EmbeddedLifecycleJsProbeKind::InlineBroadcastDenied:
            return &kBroadcastSpec;
        case EmbeddedLifecycleJsProbeKind::InlineContentProviderDenied:
            return &kContentProviderSpec;
        case EmbeddedLifecycleJsProbeKind::InlineMediaStoreDenied:
            return &kMediaStoreSpec;
        case EmbeddedLifecycleJsProbeKind::InlineDownloadManagerDenied:
            return &kDownloadManagerSpec;
        case EmbeddedLifecycleJsProbeKind::InlineInputMethodDenied:
            return &kInputMethodSpec;
        case EmbeddedLifecycleJsProbeKind::InlineAppOpsDenied:
            return &kAppOpsSpec;
        case EmbeddedLifecycleJsProbeKind::InlinePermissionManagerDenied:
            return &kPermissionManagerSpec;
        case EmbeddedLifecycleJsProbeKind::InlineSettingsDenied:
            return &kSettingsSpec;
        case EmbeddedLifecycleJsProbeKind::InlinePowerManagerDenied:
            return &kPowerManagerSpec;
        case EmbeddedLifecycleJsProbeKind::InlineKeyguardDenied:
            return &kKeyguardSpec;
        case EmbeddedLifecycleJsProbeKind::InlineWallpaperDenied:
            return &kWallpaperSpec;
        case EmbeddedLifecycleJsProbeKind::InlineShortcutManagerDenied:
            return &kShortcutManagerSpec;
        case EmbeddedLifecycleJsProbeKind::InlineAlarmManagerDenied:
            return &kAlarmManagerSpec;
        case EmbeddedLifecycleJsProbeKind::InlineJobSchedulerDenied:
            return &kJobSchedulerSpec;
        case EmbeddedLifecycleJsProbeKind::InlineWorkManagerDenied:
            return &kWorkManagerSpec;
        case EmbeddedLifecycleJsProbeKind::InlineClipboardListenerDenied:
            return &kClipboardListenerDeniedSpec;
        case EmbeddedLifecycleJsProbeKind::InlineNotificationListenerDenied:
            return &kNotificationListenerDeniedSpec;
        case EmbeddedLifecycleJsProbeKind::InlineAccessibilityControlDenied:
            return &kAccessibilityControlDeniedSpec;
        case EmbeddedLifecycleJsProbeKind::InlineDevicePolicyDenied:
            return &kDevicePolicyDeniedSpec;
        case EmbeddedLifecycleJsProbeKind::InlineUsageStatsDenied:
            return &kUsageStatsDeniedSpec;
        case EmbeddedLifecycleJsProbeKind::InlineVpnConnectivityDenied:
            return &kVpnConnectivityDeniedSpec;
        case EmbeddedLifecycleJsProbeKind::InlineWifiManagerDenied:
            return &kWifiManagerDeniedSpec;
        case EmbeddedLifecycleJsProbeKind::InlineTelephonySubscriptionDenied:
            return &kTelephonySubscriptionDeniedSpec;
        case EmbeddedLifecycleJsProbeKind::InlineCameraManagerDenied:
            return &kCameraManagerDeniedSpec;
        case EmbeddedLifecycleJsProbeKind::InlineAudioManagerDenied:
            return &kAudioManagerDeniedSpec;
        case EmbeddedLifecycleJsProbeKind::InlineDisplayManagerDenied:
            return &kDisplayManagerDeniedSpec;
        case EmbeddedLifecycleJsProbeKind::InlinePrintManagerDenied:
            return &kPrintManagerDeniedSpec;
        default:
            return nullptr;
    }
}

bool inlineSystemServiceDeniedValidationFailed(EmbeddedLifecycleJsProbeKind kind, const std::string& resultText) {
    const auto* spec = inlineSystemServiceDeniedValidationSpec(kind);
    if (spec == nullptr) {
        return false;
    }
    if (resultText.rfind("error=", 0) == 0 ||
        jsonStringField(resultText, "executionMode") != "controlled_inline" ||
        jsonStringField(resultText, "sourceKind") != "hardcoded" ||
        jsonBooleanField(resultText, "userSourceUsed") ||
        jsonBooleanField(resultText, "userFileRead") ||
        jsonStringField(resultText, "policy") != "denied" ||
        jsonStringField(resultText, "deniedErrorCode") != spec->deniedErrorCode ||
        jsonStringField(resultText, "deniedErrorMessage") != spec->deniedErrorMessage ||
        jsonStringField(resultText, "finalState") != spec->finalState ||
        !jsonBooleanField(resultText, "sequenceMonotonic") ||
        jsonNumberField(resultText, "eventCount") != "1" ||
        jsonNumberField(resultText, "responseCount") != "1" ||
        jsonStringField(resultText, "nodeVersion").find(kJsResultExpectedText) == std::string::npos) {
        return true;
    }
    for (size_t i = 0; i < spec->falseJsonFieldCount; ++i) {
        if (jsonBooleanField(resultText, spec->falseJsonFields[i])) {
            return true;
        }
    }
    return false;
}

}  // namespace autojs6::node_bridge::internal
