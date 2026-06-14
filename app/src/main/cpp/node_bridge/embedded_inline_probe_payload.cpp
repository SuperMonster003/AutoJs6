#include "embedded_inline_probe_payload.h"
#include "embedded_probe_payload_utils.h"

#include <cstddef>

namespace autojs6::node_bridge {
namespace {

struct InlineDeniedFieldMapping {
    const char* payloadSuffix;
    const char* jsonKey;
};

enum class ControlledUserInlineFieldType { Boolean, String, Number };

struct ControlledUserInlineFieldMapping {
    const char* payloadSuffix;
    const char* jsonKey;
    ControlledUserInlineFieldType type;
};

std::string inlineDeniedPayloadKey(const char* prefix, const char* suffix) {
    return std::string(prefix) + "." + suffix;
}

template <std::size_t N>
void putControlledUserInlineSkippedPayload(std::vector<std::string>& payload, const char* prefix, const char* timingKey, const ControlledUserInlineFieldMapping (&fields)[N], const char* detail) {
    putPayload(payload, inlineDeniedPayloadKey(prefix, "status"), "skipped");
    putPayload(payload, inlineDeniedPayloadKey(prefix, "detail"), detail);
    putPayload(payload, inlineDeniedPayloadKey(prefix, "result_text"), "");
    putPayload(payload, inlineDeniedPayloadKey(prefix, "execution_mode"), "");
    putPayload(payload, inlineDeniedPayloadKey(prefix, "source_kind"), "");
    putPayload(payload, inlineDeniedPayloadKey(prefix, "user_source_used"), false);
    putPayload(payload, inlineDeniedPayloadKey(prefix, "user_file_read"), false);
    putPayload(payload, inlineDeniedPayloadKey(prefix, "preflight_only"), false);
    putPayload(payload, inlineDeniedPayloadKey(prefix, "dynamic_source_allowed"), false);
    putPayload(payload, inlineDeniedPayloadKey(prefix, "require_allowed"), false);
    putPayload(payload, inlineDeniedPayloadKey(prefix, "import_allowed"), false);
    putPayload(payload, inlineDeniedPayloadKey(prefix, "autojs_api_allowed"), false);
    putPayload(payload, inlineDeniedPayloadKey(prefix, "android_bridge_allowed"), false);
    for (const auto& field : fields) {
        const auto key = inlineDeniedPayloadKey(prefix, field.payloadSuffix);
        if (field.type == ControlledUserInlineFieldType::String) putPayload(payload, key, "");
        else if (field.type == ControlledUserInlineFieldType::Number) putPayload(payload, key, static_cast<long long>(0));
        else putPayload(payload, key, false);
    }
    putPayload(payload, inlineDeniedPayloadKey(prefix, "sequence_monotonic"), false);
    putPayload(payload, inlineDeniedPayloadKey(prefix, "event_count"), static_cast<long long>(0));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "response_count"), static_cast<long long>(0));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "node_version"), "");
    putPayload(payload, inlineDeniedPayloadKey(prefix, "source.length"), static_cast<long long>(0));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "bootstrap_source.length"), static_cast<long long>(0));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "script_source.length"), static_cast<long long>(0));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "load_environment.result"), "skipped");
    putPayload(payload, inlineDeniedPayloadKey(prefix, "spin_event_loop.result"), "skipped");
    putPayload(payload, timingKey, static_cast<long long>(0));
}

template <std::size_t N>
void putControlledUserInlineFields(std::vector<std::string>& payload, const char* prefix, const ControlledUserInlineFieldMapping (&fields)[N], const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, inlineDeniedPayloadKey(prefix, "result_text"), jsonStringField(text, "resultText"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "execution_mode"), jsonStringField(text, "executionMode"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "source_kind"), jsonStringField(text, "sourceKind"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "user_source_used"), jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "user_file_read"), jsonBooleanField(text, "userFileRead"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "preflight_only"), jsonBooleanField(text, "preflightOnly"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "dynamic_source_allowed"), jsonBooleanField(text, "dynamicSourceAllowed"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "require_allowed"), jsonBooleanField(text, "requireAllowed"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "import_allowed"), jsonBooleanField(text, "importAllowed"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "autojs_api_allowed"), jsonBooleanField(text, "autojsApiAllowed"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "android_bridge_allowed"), jsonBooleanField(text, "androidBridgeAllowed"));
    for (const auto& field : fields) {
        const auto key = inlineDeniedPayloadKey(prefix, field.payloadSuffix);
        if (field.type == ControlledUserInlineFieldType::String) putPayload(payload, key, jsonStringField(text, field.jsonKey));
        else if (field.type == ControlledUserInlineFieldType::Number) putPayload(payload, key, jsonNumberField(text, field.jsonKey));
        else putPayload(payload, key, jsonBooleanField(text, field.jsonKey));
    }
    putPayload(payload, inlineDeniedPayloadKey(prefix, "sequence_monotonic"), jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "event_count"), jsonNumberField(text, "eventCount"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "response_count"), jsonNumberField(text, "responseCount"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "node_version"), jsonStringField(text, "nodeVersion"));
    if (!error.empty()) putPayload(payload, inlineDeniedPayloadKey(prefix, "detail"), error);
}

enum class UserSourcePreflightFieldType { Boolean, String, Number };

struct UserSourcePreflightFieldMapping {
    const char* payloadSuffix;
    const char* jsonKey;
    UserSourcePreflightFieldType type;
};

template <std::size_t N>
void putUserSourcePreflightSkippedPayload(std::vector<std::string>& payload, const char* prefix, const char* timingKey, const UserSourcePreflightFieldMapping (&fields)[N], const char* detail) {
    putPayload(payload, inlineDeniedPayloadKey(prefix, "status"), "skipped");
    putPayload(payload, inlineDeniedPayloadKey(prefix, "detail"), detail);
    putPayload(payload, inlineDeniedPayloadKey(prefix, "result_text"), "");
    putPayload(payload, inlineDeniedPayloadKey(prefix, "execution_mode"), "");
    putPayload(payload, inlineDeniedPayloadKey(prefix, "source_kind"), "");
    putPayload(payload, inlineDeniedPayloadKey(prefix, "user_source_used"), false);
    putPayload(payload, inlineDeniedPayloadKey(prefix, "user_file_read"), false);
    putPayload(payload, inlineDeniedPayloadKey(prefix, "preflight_only"), false);
    putPayload(payload, inlineDeniedPayloadKey(prefix, "dynamic_source_allowed"), false);
    for (const auto& field : fields) {
        const auto key = inlineDeniedPayloadKey(prefix, field.payloadSuffix);
        if (field.type == UserSourcePreflightFieldType::String) putPayload(payload, key, "");
        else if (field.type == UserSourcePreflightFieldType::Number) putPayload(payload, key, static_cast<long long>(0));
        else putPayload(payload, key, false);
    }
    putPayload(payload, inlineDeniedPayloadKey(prefix, "sequence_monotonic"), false);
    putPayload(payload, inlineDeniedPayloadKey(prefix, "event_count"), static_cast<long long>(0));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "response_count"), static_cast<long long>(0));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "node_version"), "");
    putPayload(payload, inlineDeniedPayloadKey(prefix, "source.length"), static_cast<long long>(0));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "bootstrap_source.length"), static_cast<long long>(0));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "script_source.length"), static_cast<long long>(0));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "load_environment.result"), "skipped");
    putPayload(payload, inlineDeniedPayloadKey(prefix, "spin_event_loop.result"), "skipped");
    putPayload(payload, timingKey, static_cast<long long>(0));
}

template <std::size_t N>
void putUserSourcePreflightFields(std::vector<std::string>& payload, const char* prefix, const UserSourcePreflightFieldMapping (&fields)[N], const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, inlineDeniedPayloadKey(prefix, "result_text"), text);
    putPayload(payload, inlineDeniedPayloadKey(prefix, "execution_mode"), jsonStringField(text, "executionMode"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "source_kind"), jsonStringField(text, "sourceKind"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "user_source_used"), jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "user_file_read"), jsonBooleanField(text, "userFileRead"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "preflight_only"), jsonBooleanField(text, "preflightOnly"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "dynamic_source_allowed"), jsonBooleanField(text, "dynamicSourceAllowed"));
    for (const auto& field : fields) {
        const auto key = inlineDeniedPayloadKey(prefix, field.payloadSuffix);
        if (field.type == UserSourcePreflightFieldType::String) putPayload(payload, key, jsonStringField(text, field.jsonKey));
        else if (field.type == UserSourcePreflightFieldType::Number) putPayload(payload, key, jsonNumberField(text, field.jsonKey));
        else putPayload(payload, key, jsonBooleanField(text, field.jsonKey));
    }
    putPayload(payload, inlineDeniedPayloadKey(prefix, "sequence_monotonic"), jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "event_count"), jsonNumberField(text, "eventCount"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "response_count"), jsonNumberField(text, "responseCount"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "node_version"), jsonStringField(text, "nodeVersion"));
    if (!error.empty()) putPayload(payload, inlineDeniedPayloadKey(prefix, "detail"), error);
}

template <std::size_t N>
void putInlineDeniedSkippedPayload(
        std::vector<std::string>& payload,
        const char* prefix,
        const char* timingKey,
        const InlineDeniedFieldMapping (&fields)[N],
        const char* detail
) {
    putPayload(payload, inlineDeniedPayloadKey(prefix, "status"), "skipped");
    putPayload(payload, inlineDeniedPayloadKey(prefix, "detail"), detail);
    putPayload(payload, inlineDeniedPayloadKey(prefix, "result_text"), "");
    putPayload(payload, inlineDeniedPayloadKey(prefix, "execution_mode"), "");
    putPayload(payload, inlineDeniedPayloadKey(prefix, "source_kind"), "");
    putPayload(payload, inlineDeniedPayloadKey(prefix, "user_source_used"), false);
    putPayload(payload, inlineDeniedPayloadKey(prefix, "user_file_read"), false);
    for (const auto& field : fields) {
        putPayload(payload, inlineDeniedPayloadKey(prefix, field.payloadSuffix), false);
    }
    putPayload(payload, inlineDeniedPayloadKey(prefix, "policy"), "");
    putPayload(payload, inlineDeniedPayloadKey(prefix, "denied_error_code"), "");
    putPayload(payload, inlineDeniedPayloadKey(prefix, "denied_error_message"), "");
    putPayload(payload, inlineDeniedPayloadKey(prefix, "final_state"), "");
    putPayload(payload, inlineDeniedPayloadKey(prefix, "sequence_monotonic"), false);
    putPayload(payload, inlineDeniedPayloadKey(prefix, "event_count"), static_cast<long long>(0));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "response_count"), static_cast<long long>(0));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "node_version"), "");
    putPayload(payload, inlineDeniedPayloadKey(prefix, "source.length"), static_cast<long long>(0));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "bootstrap_source.length"), static_cast<long long>(0));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "script_source.length"), static_cast<long long>(0));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "load_environment.result"), "skipped");
    putPayload(payload, inlineDeniedPayloadKey(prefix, "spin_event_loop.result"), "skipped");
    putPayload(payload, timingKey, static_cast<long long>(0));
}

template <std::size_t N>
void putInlineDeniedFields(
        std::vector<std::string>& payload,
        const char* prefix,
        const InlineDeniedFieldMapping (&fields)[N],
        const std::string& text
) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, inlineDeniedPayloadKey(prefix, "result_text"), text);
    putPayload(payload, inlineDeniedPayloadKey(prefix, "execution_mode"), jsonStringField(text, "executionMode"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "source_kind"), jsonStringField(text, "sourceKind"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "user_source_used"), jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "user_file_read"), jsonBooleanField(text, "userFileRead"));
    for (const auto& field : fields) {
        putPayload(payload, inlineDeniedPayloadKey(prefix, field.payloadSuffix), jsonBooleanField(text, field.jsonKey));
    }
    putPayload(payload, inlineDeniedPayloadKey(prefix, "policy"), jsonStringField(text, "policy"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "denied_error_code"), jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "denied_error_message"), jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "final_state"), jsonStringField(text, "finalState"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "sequence_monotonic"), jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "event_count"), jsonNumberField(text, "eventCount"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "response_count"), jsonNumberField(text, "responseCount"));
    putPayload(payload, inlineDeniedPayloadKey(prefix, "node_version"), jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, inlineDeniedPayloadKey(prefix, "detail"), error);
    }
}

constexpr InlineDeniedFieldMapping kInlineAccountDeniedFields[] = {
        {"account_bridge_allowed", "accountBridgeAllowed"},
        {"account_global_injected", "accountGlobalInjected"},
        {"account_manager_accessed", "accountManagerAccessed"},
        {"accounts_queried", "accountsQueried"},
        {"auth_token_requested", "authTokenRequested"},
        {"account_added", "accountAdded"},
        {"account_removed", "accountRemoved"},
        {"permission_requested", "permissionRequested"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr InlineDeniedFieldMapping kInlinePackageManagerDeniedFields[] = {
        {"package_manager_bridge_allowed", "packageManagerBridgeAllowed"},
        {"package_manager_global_injected", "packageManagerGlobalInjected"},
        {"package_manager_accessed", "packageManagerAccessed"},
        {"installed_packages_queried", "installedPackagesQueried"},
        {"application_info_queried", "applicationInfoQueried"},
        {"package_info_queried", "packageInfoQueried"},
        {"launch_intent_queried", "launchIntentQueried"},
        {"permission_info_queried", "permissionInfoQueried"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr InlineDeniedFieldMapping kInlineIntentActivityDeniedFields[] = {
        {"intent_activity_bridge_allowed", "intentActivityBridgeAllowed"},
        {"intent_global_injected", "intentGlobalInjected"},
        {"activity_global_injected", "activityGlobalInjected"},
        {"intent_created", "intentCreated"},
        {"activity_started", "activityStarted"},
        {"activity_result_requested", "activityResultRequested"},
        {"service_started", "serviceStarted"},
        {"uri_parsed", "uriParsed"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr InlineDeniedFieldMapping kInlineBroadcastDeniedFields[] = {
        {"broadcast_bridge_allowed", "broadcastBridgeAllowed"},
        {"broadcast_global_injected", "broadcastGlobalInjected"},
        {"broadcast_sent", "broadcastSent"},
        {"ordered_broadcast_sent", "orderedBroadcastSent"},
        {"broadcast_receiver_registered", "broadcastReceiverRegistered"},
        {"intent_filter_created", "intentFilterCreated"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr InlineDeniedFieldMapping kInlineContentProviderDeniedFields[] = {
        {"content_provider_bridge_allowed", "contentProviderBridgeAllowed"},
        {"content_provider_global_injected", "contentProviderGlobalInjected"},
        {"content_resolver_accessed", "contentResolverAccessed"},
        {"provider_query_attempted", "providerQueryAttempted"},
        {"provider_insert_attempted", "providerInsertAttempted"},
        {"provider_update_attempted", "providerUpdateAttempted"},
        {"provider_delete_attempted", "providerDeleteAttempted"},
        {"cursor_opened", "cursorOpened"},
        {"uri_parsed", "uriParsed"},
        {"permission_requested", "permissionRequested"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr InlineDeniedFieldMapping kInlineMediaStoreDeniedFields[] = {
        {"media_store_bridge_allowed", "mediaStoreBridgeAllowed"},
        {"media_store_global_injected", "mediaStoreGlobalInjected"},
        {"content_resolver_accessed", "contentResolverAccessed"},
        {"media_store_queried", "mediaStoreQueried"},
        {"image_media_queried", "imageMediaQueried"},
        {"video_media_queried", "videoMediaQueried"},
        {"audio_media_queried", "audioMediaQueried"},
        {"media_insert_attempted", "mediaInsertAttempted"},
        {"media_delete_attempted", "mediaDeleteAttempted"},
        {"permission_requested", "permissionRequested"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr InlineDeniedFieldMapping kInlineDownloadManagerDeniedFields[] = {
        {"download_manager_bridge_allowed", "downloadManagerBridgeAllowed"},
        {"download_manager_global_injected", "downloadManagerGlobalInjected"},
        {"download_manager_accessed", "downloadManagerAccessed"},
        {"download_request_created", "downloadRequestCreated"},
        {"download_enqueued", "downloadEnqueued"},
        {"download_query_attempted", "downloadQueryAttempted"},
        {"download_removed", "downloadRemoved"},
        {"uri_parsed", "uriParsed"},
        {"network_permission_requested", "networkPermissionRequested"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr InlineDeniedFieldMapping kInlineInputMethodDeniedFields[] = {
        {"input_method_bridge_allowed", "inputMethodBridgeAllowed"},
        {"input_method_global_injected", "inputMethodGlobalInjected"},
        {"input_method_manager_accessed", "inputMethodManagerAccessed"},
        {"soft_keyboard_shown", "softKeyboardShown"},
        {"soft_keyboard_hidden", "softKeyboardHidden"},
        {"input_connection_accessed", "inputConnectionAccessed"},
        {"text_committed", "textCommitted"},
        {"editor_action_sent", "editorActionSent"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr InlineDeniedFieldMapping kInlineAppOpsDeniedFields[] = {
        {"app_ops_bridge_allowed", "appOpsBridgeAllowed"},
        {"app_ops_global_injected", "appOpsGlobalInjected"},
        {"app_ops_manager_accessed", "appOpsManagerAccessed"},
        {"op_checked", "opChecked"},
        {"op_noted", "opNoted"},
        {"mode_queried", "modeQueried"},
        {"package_ops_queried", "packageOpsQueried"},
        {"permission_requested", "permissionRequested"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr InlineDeniedFieldMapping kInlinePermissionManagerDeniedFields[] = {
        {"permission_manager_bridge_allowed", "permissionManagerBridgeAllowed"},
        {"permission_manager_global_injected", "permissionManagerGlobalInjected"},
        {"permission_manager_accessed", "permissionManagerAccessed"},
        {"permission_checked", "permissionChecked"},
        {"permission_requested", "permissionRequested"},
        {"runtime_permission_requested", "runtimePermissionRequested"},
        {"permission_grant_attempted", "permissionGrantAttempted"},
        {"permission_revoke_attempted", "permissionRevokeAttempted"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr InlineDeniedFieldMapping kInlineSettingsDeniedFields[] = {
        {"settings_bridge_allowed", "settingsBridgeAllowed"},
        {"settings_global_injected", "settingsGlobalInjected"},
        {"settings_provider_accessed", "settingsProviderAccessed"},
        {"settings_system_queried", "settingsSystemQueried"},
        {"settings_secure_queried", "settingsSecureQueried"},
        {"settings_global_queried", "settingsGlobalQueried"},
        {"settings_write_attempted", "settingsWriteAttempted"},
        {"can_write_settings_checked", "canWriteSettingsChecked"},
        {"permission_requested", "permissionRequested"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr InlineDeniedFieldMapping kInlinePowerManagerDeniedFields[] = {
        {"power_manager_bridge_allowed", "powerManagerBridgeAllowed"},
        {"power_manager_global_injected", "powerManagerGlobalInjected"},
        {"power_manager_accessed", "powerManagerAccessed"},
        {"wake_lock_created", "wakeLockCreated"},
        {"wake_lock_acquired", "wakeLockAcquired"},
        {"wake_lock_released", "wakeLockReleased"},
        {"battery_optimization_checked", "batteryOptimizationChecked"},
        {"interactive_state_queried", "interactiveStateQueried"},
        {"permission_requested", "permissionRequested"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr InlineDeniedFieldMapping kInlineKeyguardDeniedFields[] = {
        {"keyguard_bridge_allowed", "keyguardBridgeAllowed"},
        {"keyguard_global_injected", "keyguardGlobalInjected"},
        {"keyguard_manager_accessed", "keyguardManagerAccessed"},
        {"device_locked_queried", "deviceLockedQueried"},
        {"keyguard_locked_queried", "keyguardLockedQueried"},
        {"keyguard_dismiss_attempted", "keyguardDismissAttempted"},
        {"credential_confirmation_requested", "credentialConfirmationRequested"},
        {"permission_requested", "permissionRequested"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr InlineDeniedFieldMapping kInlineWallpaperDeniedFields[] = {
        {"wallpaper_bridge_allowed", "wallpaperBridgeAllowed"},
        {"wallpaper_global_injected", "wallpaperGlobalInjected"},
        {"wallpaper_manager_accessed", "wallpaperManagerAccessed"},
        {"wallpaper_read_attempted", "wallpaperReadAttempted"},
        {"wallpaper_set_attempted", "wallpaperSetAttempted"},
        {"wallpaper_clear_attempted", "wallpaperClearAttempted"},
        {"bitmap_required", "bitmapRequired"},
        {"image_stream_opened", "imageStreamOpened"},
        {"permission_requested", "permissionRequested"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr InlineDeniedFieldMapping kInlineShortcutManagerDeniedFields[] = {
        {"shortcut_manager_bridge_allowed", "shortcutManagerBridgeAllowed"},
        {"shortcut_manager_global_injected", "shortcutManagerGlobalInjected"},
        {"shortcut_manager_accessed", "shortcutManagerAccessed"},
        {"dynamic_shortcut_pushed", "dynamicShortcutPushed"},
        {"pinned_shortcut_requested", "pinnedShortcutRequested"},
        {"shortcut_removed", "shortcutRemoved"},
        {"shortcut_updated", "shortcutUpdated"},
        {"intent_created", "intentCreated"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr InlineDeniedFieldMapping kInlineAlarmManagerDeniedFields[] = {
        {"alarm_manager_bridge_allowed", "alarmManagerBridgeAllowed"},
        {"alarm_manager_global_injected", "alarmManagerGlobalInjected"},
        {"alarm_manager_accessed", "alarmManagerAccessed"},
        {"alarm_scheduled", "alarmScheduled"},
        {"exact_alarm_scheduled", "exactAlarmScheduled"},
        {"alarm_cancelled", "alarmCancelled"},
        {"pending_intent_created", "pendingIntentCreated"},
        {"permission_requested", "permissionRequested"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr InlineDeniedFieldMapping kInlineJobSchedulerDeniedFields[] = {
        {"job_scheduler_bridge_allowed", "jobSchedulerBridgeAllowed"},
        {"job_scheduler_global_injected", "jobSchedulerGlobalInjected"},
        {"job_scheduler_accessed", "jobSchedulerAccessed"},
        {"job_info_created", "jobInfoCreated"},
        {"job_scheduled", "jobScheduled"},
        {"job_cancelled", "jobCancelled"},
        {"job_service_referenced", "jobServiceReferenced"},
        {"permission_requested", "permissionRequested"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr InlineDeniedFieldMapping kInlineWorkManagerDeniedFields[] = {
        {"work_manager_bridge_allowed", "workManagerBridgeAllowed"},
        {"work_manager_global_injected", "workManagerGlobalInjected"},
        {"work_manager_accessed", "workManagerAccessed"},
        {"work_request_created", "workRequestCreated"},
        {"work_enqueued", "workEnqueued"},
        {"work_cancelled", "workCancelled"},
        {"worker_referenced", "workerReferenced"},
        {"constraint_created", "constraintCreated"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr InlineDeniedFieldMapping kInlineClipboardListenerDeniedFields[] = {
        {"clipboard_listener_bridge_allowed", "clipboardListenerBridgeAllowed"},
        {"clipboard_listener_global_injected", "clipboardListenerGlobalInjected"},
        {"clipboard_manager_accessed", "clipboardManagerAccessed"},
        {"primary_clip_listener_registered", "primaryClipListenerRegistered"},
        {"primary_clip_listener_removed", "primaryClipListenerRemoved"},
        {"clip_change_observed", "clipChangeObserved"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr InlineDeniedFieldMapping kInlineNotificationListenerDeniedFields[] = {
        {"notification_listener_bridge_allowed", "notificationListenerBridgeAllowed"},
        {"notification_listener_global_injected", "notificationListenerGlobalInjected"},
        {"notification_listener_service_accessed", "notificationListenerServiceAccessed"},
        {"notification_listener_registered", "notificationListenerRegistered"},
        {"notifications_queried", "notificationsQueried"},
        {"notification_event_observed", "notificationEventObserved"},
        {"permission_requested", "permissionRequested"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr InlineDeniedFieldMapping kInlineAccessibilityControlDeniedFields[] = {
        {"accessibility_control_bridge_allowed", "accessibilityControlBridgeAllowed"},
        {"accessibility_control_global_injected", "accessibilityControlGlobalInjected"},
        {"accessibility_service_accessed", "accessibilityServiceAccessed"},
        {"service_enabled_checked", "serviceEnabledChecked"},
        {"service_start_attempted", "serviceStartAttempted"},
        {"service_stop_attempted", "serviceStopAttempted"},
        {"accessibility_settings_opened", "accessibilitySettingsOpened"},
        {"gesture_dispatch_attempted", "gestureDispatchAttempted"},
        {"node_action_attempted", "nodeActionAttempted"},
        {"permission_requested", "permissionRequested"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr InlineDeniedFieldMapping kInlineDevicePolicyDeniedFields[] = {
        {"device_policy_bridge_allowed", "devicePolicyBridgeAllowed"},
        {"device_policy_global_injected", "devicePolicyGlobalInjected"},
        {"device_policy_manager_accessed", "devicePolicyManagerAccessed"},
        {"admin_active_checked", "adminActiveChecked"},
        {"lock_now_attempted", "lockNowAttempted"},
        {"wipe_data_attempted", "wipeDataAttempted"},
        {"password_policy_queried", "passwordPolicyQueried"},
        {"permission_requested", "permissionRequested"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr InlineDeniedFieldMapping kInlineUsageStatsDeniedFields[] = {
        {"usage_stats_bridge_allowed", "usageStatsBridgeAllowed"},
        {"usage_stats_global_injected", "usageStatsGlobalInjected"},
        {"usage_stats_manager_accessed", "usageStatsManagerAccessed"},
        {"usage_events_queried", "usageEventsQueried"},
        {"usage_stats_queried", "usageStatsQueried"},
        {"app_standby_bucket_queried", "appStandbyBucketQueried"},
        {"permission_requested", "permissionRequested"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr InlineDeniedFieldMapping kInlineVpnConnectivityDeniedFields[] = {
        {"vpn_connectivity_bridge_allowed", "vpnConnectivityBridgeAllowed"},
        {"vpn_connectivity_global_injected", "vpnConnectivityGlobalInjected"},
        {"connectivity_manager_accessed", "connectivityManagerAccessed"},
        {"vpn_service_accessed", "vpnServiceAccessed"},
        {"network_capabilities_queried", "networkCapabilitiesQueried"},
        {"active_network_queried", "activeNetworkQueried"},
        {"vpn_prepare_attempted", "vpnPrepareAttempted"},
        {"network_request_created", "networkRequestCreated"},
        {"permission_requested", "permissionRequested"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr InlineDeniedFieldMapping kInlineWifiManagerDeniedFields[] = {
        {"wifi_manager_bridge_allowed", "wifiManagerBridgeAllowed"},
        {"wifi_manager_global_injected", "wifiManagerGlobalInjected"},
        {"wifi_manager_accessed", "wifiManagerAccessed"},
        {"wifi_info_queried", "wifiInfoQueried"},
        {"scan_results_queried", "scanResultsQueried"},
        {"scan_started", "scanStarted"},
        {"wifi_network_suggested", "wifiNetworkSuggested"},
        {"permission_requested", "permissionRequested"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr InlineDeniedFieldMapping kInlineTelephonySubscriptionDeniedFields[] = {
        {"telephony_subscription_bridge_allowed", "telephonySubscriptionBridgeAllowed"},
        {"telephony_subscription_global_injected", "telephonySubscriptionGlobalInjected"},
        {"subscription_manager_accessed", "subscriptionManagerAccessed"},
        {"active_subscription_info_queried", "activeSubscriptionInfoQueried"},
        {"sim_state_queried", "simStateQueried"},
        {"carrier_info_queried", "carrierInfoQueried"},
        {"permission_requested", "permissionRequested"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr InlineDeniedFieldMapping kInlineCameraManagerDeniedFields[] = {
        {"camera_manager_bridge_allowed", "cameraManagerBridgeAllowed"},
        {"camera_manager_global_injected", "cameraManagerGlobalInjected"},
        {"camera_manager_accessed", "cameraManagerAccessed"},
        {"camera_id_list_queried", "cameraIdListQueried"},
        {"camera_characteristics_queried", "cameraCharacteristicsQueried"},
        {"camera_open_attempted", "cameraOpenAttempted"},
        {"availability_callback_registered", "availabilityCallbackRegistered"},
        {"permission_requested", "permissionRequested"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr InlineDeniedFieldMapping kInlineAudioManagerDeniedFields[] = {
        {"audio_manager_bridge_allowed", "audioManagerBridgeAllowed"},
        {"audio_manager_global_injected", "audioManagerGlobalInjected"},
        {"audio_manager_accessed", "audioManagerAccessed"},
        {"volume_queried", "volumeQueried"},
        {"volume_changed", "volumeChanged"},
        {"ringer_mode_changed", "ringerModeChanged"},
        {"audio_focus_requested", "audioFocusRequested"},
        {"microphone_mute_changed", "microphoneMuteChanged"},
        {"permission_requested", "permissionRequested"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr InlineDeniedFieldMapping kInlineDisplayManagerDeniedFields[] = {
        {"display_manager_bridge_allowed", "displayManagerBridgeAllowed"},
        {"display_manager_global_injected", "displayManagerGlobalInjected"},
        {"display_manager_accessed", "displayManagerAccessed"},
        {"displays_queried", "displaysQueried"},
        {"display_listener_registered", "displayListenerRegistered"},
        {"virtual_display_created", "virtualDisplayCreated"},
        {"display_metrics_queried", "displayMetricsQueried"},
        {"permission_requested", "permissionRequested"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr InlineDeniedFieldMapping kInlinePrintManagerDeniedFields[] = {
        {"print_manager_bridge_allowed", "printManagerBridgeAllowed"},
        {"print_manager_global_injected", "printManagerGlobalInjected"},
        {"print_manager_accessed", "printManagerAccessed"},
        {"print_job_created", "printJobCreated"},
        {"print_adapter_created", "printAdapterCreated"},
        {"print_document_requested", "printDocumentRequested"},
        {"print_job_state_queried", "printJobStateQueried"},
        {"permission_requested", "permissionRequested"},
        {"android_context_accessed", "androidContextAccessed"},
        {"android_api_called", "androidApiCalled"},
        {"host_object_injected", "hostObjectInjected"},
        {"binder_used", "binderUsed"},
};

constexpr ControlledUserInlineFieldMapping kControlledUserInlineConstantFields[] = {
        {"expression", "expression", ControlledUserInlineFieldType::String},
        {"result_captured", "resultCaptured", ControlledUserInlineFieldType::Boolean},
        {"result_type", "resultType", ControlledUserInlineFieldType::String},
};

constexpr ControlledUserInlineFieldMapping kControlledUserInlineStdoutFields[] = {
        {"stdout_capture_enabled", "stdoutCaptureEnabled", ControlledUserInlineFieldType::Boolean},
        {"stdout_written", "stdoutWritten", ControlledUserInlineFieldType::Boolean},
        {"stdout_text", "stdoutText", ControlledUserInlineFieldType::String},
        {"stderr_written", "stderrWritten", ControlledUserInlineFieldType::Boolean},
};

constexpr ControlledUserInlineFieldMapping kControlledUserInlineStderrFields[] = {
        {"stderr_capture_enabled", "stderrCaptureEnabled", ControlledUserInlineFieldType::Boolean},
        {"stderr_written", "stderrWritten", ControlledUserInlineFieldType::Boolean},
        {"stderr_text", "stderrText", ControlledUserInlineFieldType::String},
        {"stdout_written", "stdoutWritten", ControlledUserInlineFieldType::Boolean},
};

constexpr ControlledUserInlineFieldMapping kControlledUserInlineReturnValueFields[] = {
        {"return_value_capture_enabled", "returnValueCaptureEnabled", ControlledUserInlineFieldType::Boolean},
        {"return_value_captured", "returnValueCaptured", ControlledUserInlineFieldType::Boolean},
        {"return_value_type", "returnValueType", ControlledUserInlineFieldType::String},
        {"return_value_json", "returnValueJson", ControlledUserInlineFieldType::String},
};

constexpr ControlledUserInlineFieldMapping kControlledUserInlineThrownErrorFields[] = {
        {"error_capture_enabled", "errorCaptureEnabled", ControlledUserInlineFieldType::Boolean},
        {"error_captured", "errorCaptured", ControlledUserInlineFieldType::Boolean},
        {"error_name", "errorName", ControlledUserInlineFieldType::String},
        {"error_message", "errorMessage", ControlledUserInlineFieldType::String},
        {"stack_available", "stackAvailable", ControlledUserInlineFieldType::Boolean},
        {"main_process_crashed", "mainProcessCrashed", ControlledUserInlineFieldType::Boolean},
        {"embedded_process_isolated", "embeddedProcessIsolated", ControlledUserInlineFieldType::Boolean},
};

constexpr ControlledUserInlineFieldMapping kControlledUserInlinePromiseFields[] = {
        {"promise_created", "promiseCreated", ControlledUserInlineFieldType::Boolean},
        {"promise_completed", "promiseCompleted", ControlledUserInlineFieldType::Boolean},
        {"promise_value", "promiseValue", ControlledUserInlineFieldType::Number},
        {"stdout_text", "stdoutText", ControlledUserInlineFieldType::String},
        {"spin_event_loop_completed", "spinEventLoopCompleted", ControlledUserInlineFieldType::Boolean},
};

constexpr ControlledUserInlineFieldMapping kControlledUserInlineAsyncOrderingFields[] = {
        {"next_tick_available", "nextTickAvailable", ControlledUserInlineFieldType::Boolean},
        {"promise_available", "promiseAvailable", ControlledUserInlineFieldType::Boolean},
        {"set_immediate_available", "setImmediateAvailable", ControlledUserInlineFieldType::Boolean},
        {"set_timeout_available", "setTimeoutAvailable", ControlledUserInlineFieldType::Boolean},
        {"ordering", "ordering", ControlledUserInlineFieldType::String},
        {"ordering_matches_expected", "orderingMatchesExpected", ControlledUserInlineFieldType::Boolean},
};

constexpr ControlledUserInlineFieldMapping kControlledUserInlineSummaryFields[] = {
        {"inline_user_execution_ready", "inlineUserExecutionReady", ControlledUserInlineFieldType::Boolean},
        {"constant_execution_ready", "constantExecutionReady", ControlledUserInlineFieldType::Boolean},
        {"stdout_ready", "stdoutReady", ControlledUserInlineFieldType::Boolean},
        {"stderr_ready", "stderrReady", ControlledUserInlineFieldType::Boolean},
        {"return_value_ready", "returnValueReady", ControlledUserInlineFieldType::Boolean},
        {"error_capture_ready", "errorCaptureReady", ControlledUserInlineFieldType::Boolean},
        {"promise_completion_ready", "promiseCompletionReady", ControlledUserInlineFieldType::Boolean},
        {"async_ordering_ready", "asyncOrderingReady", ControlledUserInlineFieldType::Boolean},
        {"ready_for_user_file_preflight", "readyForUserFilePreflight", ControlledUserInlineFieldType::Boolean},
};

constexpr ControlledUserInlineFieldMapping kUserFileDescriptorFields[] = {
        {"described_source_kind", "describedSourceKind", ControlledUserInlineFieldType::String},
        {"file_path_present", "filePathPresent", ControlledUserInlineFieldType::Boolean},
        {"file_exists", "fileExists", ControlledUserInlineFieldType::String},
        {"file_extension_supported", "fileExtensionSupported", ControlledUserInlineFieldType::Boolean},
        {"supported_extensions", "supportedExtensions", ControlledUserInlineFieldType::String},
        {"package_json_read", "packageJsonRead", ControlledUserInlineFieldType::Boolean},
        {"node_modules_read", "nodeModulesRead", ControlledUserInlineFieldType::Boolean},
};

constexpr ControlledUserInlineFieldMapping kUserFileReadPolicyFields[] = {
        {"target_file_read_allowed", "targetFileReadAllowed", ControlledUserInlineFieldType::Boolean},
        {"arbitrary_fs_access_allowed", "arbitraryFsAccessAllowed", ControlledUserInlineFieldType::Boolean},
        {"package_json_read_allowed", "packageJsonReadAllowed", ControlledUserInlineFieldType::Boolean},
        {"node_modules_read_allowed", "nodeModulesReadAllowed", ControlledUserInlineFieldType::Boolean},
        {"directory_listing_allowed", "directoryListingAllowed", ControlledUserInlineFieldType::Boolean},
        {"symlink_follow_policy", "symlinkFollowPolicy", ControlledUserInlineFieldType::String},
        {"max_file_bytes", "maxFileBytes", ControlledUserInlineFieldType::Number},
};

constexpr ControlledUserInlineFieldMapping kUserFilePathNormalizationFields[] = {
        {"absolute_path_ready", "absolutePathReady", ControlledUserInlineFieldType::Boolean},
        {"canonical_path_ready", "canonicalPathReady", ControlledUserInlineFieldType::Boolean},
        {"display_path_ready", "displayPathReady", ControlledUserInlineFieldType::Boolean},
        {"path_traversal_checked", "pathTraversalChecked", ControlledUserInlineFieldType::Boolean},
        {"allowed_scope_checked", "allowedScopeChecked", ControlledUserInlineFieldType::Boolean},
        {"path_normalization_succeeded", "pathNormalizationSucceeded", ControlledUserInlineFieldType::Boolean},
};

constexpr ControlledUserInlineFieldMapping kUserFileWorkingDirectoryFields[] = {
        {"working_directory_ready", "workingDirectoryReady", ControlledUserInlineFieldType::Boolean},
        {"cwd_matches_script_parent", "cwdMatchesScriptParent", ControlledUserInlineFieldType::Boolean},
        {"cwd_passed_to_environment", "cwdPassedToEnvironment", ControlledUserInlineFieldType::Boolean},
        {"relative_path_base_defined", "relativePathBaseDefined", ControlledUserInlineFieldType::Boolean},
        {"process_cwd_expected", "processCwdExpected", ControlledUserInlineFieldType::Boolean},
};

constexpr ControlledUserInlineFieldMapping kUserFileSourceLoadingFields[] = {
        {"source_loading_ready", "sourceLoadingReady", ControlledUserInlineFieldType::Boolean},
        {"simulated_file_source_used", "simulatedFileSourceUsed", ControlledUserInlineFieldType::Boolean},
        {"encoding", "encoding", ControlledUserInlineFieldType::String},
        {"bom_handled", "bomHandled", ControlledUserInlineFieldType::Boolean},
        {"line_count_available", "lineCountAvailable", ControlledUserInlineFieldType::Boolean},
        {"source_bytes_available", "sourceBytesAvailable", ControlledUserInlineFieldType::Boolean},
};

constexpr ControlledUserInlineFieldMapping kUserFileExecutionDryRunFields[] = {
        {"simulated_file_source_used", "simulatedFileSourceUsed", ControlledUserInlineFieldType::Boolean},
        {"stdout_captured", "stdoutCaptured", ControlledUserInlineFieldType::Boolean},
        {"stdout_contains_hello", "stdoutContainsHello", ControlledUserInlineFieldType::Boolean},
        {"stdout_contains_node_version", "stdoutContainsNodeVersion", ControlledUserInlineFieldType::Boolean},
};

constexpr ControlledUserInlineFieldMapping kUserFileErrorStackFilenameFields[] = {
        {"error_captured", "errorCaptured", ControlledUserInlineFieldType::Boolean},
        {"error_name", "errorName", ControlledUserInlineFieldType::String},
        {"error_message", "errorMessage", ControlledUserInlineFieldType::String},
        {"stack_available", "stackAvailable", ControlledUserInlineFieldType::Boolean},
        {"stack_contains_source_name", "stackContainsSourceName", ControlledUserInlineFieldType::Boolean},
        {"source_name", "sourceName", ControlledUserInlineFieldType::String},
};

constexpr ControlledUserInlineFieldMapping kUserFileExecutionSummaryFields[] = {
        {"file_execution_preflight_ready", "fileExecutionPreflightReady", ControlledUserInlineFieldType::Boolean},
        {"file_descriptor_ready", "fileDescriptorReady", ControlledUserInlineFieldType::Boolean},
        {"read_policy_ready", "readPolicyReady", ControlledUserInlineFieldType::Boolean},
        {"path_normalization_ready", "pathNormalizationReady", ControlledUserInlineFieldType::Boolean},
        {"working_directory_ready", "workingDirectoryReady", ControlledUserInlineFieldType::Boolean},
        {"source_loading_ready", "sourceLoadingReady", ControlledUserInlineFieldType::Boolean},
        {"file_dry_run_ready", "fileDryRunReady", ControlledUserInlineFieldType::Boolean},
        {"error_stack_filename_ready", "errorStackFilenameReady", ControlledUserInlineFieldType::Boolean},
        {"ready_for_request_result_envelope", "readyForRequestResultEnvelope", ControlledUserInlineFieldType::Boolean},
};

constexpr ControlledUserInlineFieldMapping kEmbeddedScriptRequestFields[] = {
        {"source_present", "sourcePresent", ControlledUserInlineFieldType::Boolean},
        {"source_kind_descriptor", "sourceKindDescriptor", ControlledUserInlineFieldType::String},
        {"source_name_present", "sourceNamePresent", ControlledUserInlineFieldType::Boolean},
        {"working_directory_present", "workingDirectoryPresent", ControlledUserInlineFieldType::Boolean},
        {"argv_supported", "argvSupported", ControlledUserInlineFieldType::Boolean},
        {"env_supported", "envSupported", ControlledUserInlineFieldType::Boolean},
        {"timeout_ms_present", "timeoutMsPresent", ControlledUserInlineFieldType::Boolean},
        {"capture_stdout", "captureStdout", ControlledUserInlineFieldType::Boolean},
        {"capture_stderr", "captureStderr", ControlledUserInlineFieldType::Boolean},
};

constexpr ControlledUserInlineFieldMapping kEmbeddedScriptResultFields[] = {
        {"succeeded_present", "succeededPresent", ControlledUserInlineFieldType::Boolean},
        {"exit_code_present", "exitCodePresent", ControlledUserInlineFieldType::Boolean},
        {"result_text_present", "resultTextPresent", ControlledUserInlineFieldType::Boolean},
        {"stdout_present", "stdoutPresent", ControlledUserInlineFieldType::Boolean},
        {"stderr_present", "stderrPresent", ControlledUserInlineFieldType::Boolean},
        {"error_name_present", "errorNamePresent", ControlledUserInlineFieldType::Boolean},
        {"error_message_present", "errorMessagePresent", ControlledUserInlineFieldType::Boolean},
        {"error_stack_present", "errorStackPresent", ControlledUserInlineFieldType::Boolean},
        {"elapsed_ms_present", "elapsedMsPresent", ControlledUserInlineFieldType::Boolean},
        {"process_exit_scheduled", "processExitScheduled", ControlledUserInlineFieldType::Boolean},
};

constexpr ControlledUserInlineFieldMapping kEmbeddedScriptOutputEventFields[] = {
        {"event_envelope_ready", "eventEnvelopeReady", ControlledUserInlineFieldType::Boolean},
        {"stdout_event_supported", "stdoutEventSupported", ControlledUserInlineFieldType::Boolean},
        {"stderr_event_supported", "stderrEventSupported", ControlledUserInlineFieldType::Boolean},
        {"sequence_supported", "sequenceSupported", ControlledUserInlineFieldType::Boolean},
        {"timestamp_supported", "timestampSupported", ControlledUserInlineFieldType::Boolean},
        {"chunk_text_supported", "chunkTextSupported", ControlledUserInlineFieldType::Boolean},
        {"truncation_flag_supported", "truncationFlagSupported", ControlledUserInlineFieldType::Boolean},
        {"backpressure_policy", "backpressurePolicy", ControlledUserInlineFieldType::String},
};

constexpr ControlledUserInlineFieldMapping kEmbeddedScriptErrorFields[] = {
        {"error_envelope_ready", "errorEnvelopeReady", ControlledUserInlineFieldType::Boolean},
        {"syntax_error_supported", "syntaxErrorSupported", ControlledUserInlineFieldType::Boolean},
        {"runtime_error_supported", "runtimeErrorSupported", ControlledUserInlineFieldType::Boolean},
        {"native_error_supported", "nativeErrorSupported", ControlledUserInlineFieldType::Boolean},
        {"timeout_error_supported", "timeoutErrorSupported", ControlledUserInlineFieldType::Boolean},
        {"process_crash_supported", "processCrashSupported", ControlledUserInlineFieldType::Boolean},
        {"error_name_present", "errorNamePresent", ControlledUserInlineFieldType::Boolean},
        {"error_message_present", "errorMessagePresent", ControlledUserInlineFieldType::Boolean},
        {"error_stack_present", "errorStackPresent", ControlledUserInlineFieldType::Boolean},
};

constexpr ControlledUserInlineFieldMapping kEmbeddedScriptTimeoutFields[] = {
        {"timeout_policy_ready", "timeoutPolicyReady", ControlledUserInlineFieldType::Boolean},
        {"timeout_ms_present", "timeoutMsPresent", ControlledUserInlineFieldType::Boolean},
        {"timeout_triggered", "timeoutTriggered", ControlledUserInlineFieldType::Boolean},
        {"timeout_error_code", "timeoutErrorCode", ControlledUserInlineFieldType::String},
        {"embedded_process_terminated_on_timeout", "embeddedProcessTerminatedOnTimeout", ControlledUserInlineFieldType::Boolean},
        {"main_process_survives_timeout", "mainProcessSurvivesTimeout", ControlledUserInlineFieldType::Boolean},
};

constexpr ControlledUserInlineFieldMapping kEmbeddedScriptCancellationFields[] = {
        {"cancellation_envelope_ready", "cancellationEnvelopeReady", ControlledUserInlineFieldType::Boolean},
        {"cancel_requested_supported", "cancelRequestedSupported", ControlledUserInlineFieldType::Boolean},
        {"cancel_observed_supported", "cancelObservedSupported", ControlledUserInlineFieldType::Boolean},
        {"cancel_error_code", "cancelErrorCode", ControlledUserInlineFieldType::String},
        {"best_effort_process_termination", "bestEffortProcessTermination", ControlledUserInlineFieldType::Boolean},
        {"main_process_survives_cancel", "mainProcessSurvivesCancel", ControlledUserInlineFieldType::Boolean},
};

constexpr ControlledUserInlineFieldMapping kEmbeddedScriptProcessIsolationFields[] = {
        {"embedded_process_name", "embeddedProcessName", ControlledUserInlineFieldType::String},
        {"main_process_isolated", "mainProcessIsolated", ControlledUserInlineFieldType::Boolean},
        {"single_lifecycle_per_process", "singleLifecyclePerProcess", ControlledUserInlineFieldType::Boolean},
        {"process_exit_scheduled", "processExitScheduled", ControlledUserInlineFieldType::Boolean},
        {"crash_containment_ready", "crashContainmentReady", ControlledUserInlineFieldType::Boolean},
        {"reuse_runtime", "reuseRuntime", ControlledUserInlineFieldType::Boolean},
};

constexpr ControlledUserInlineFieldMapping kEmbeddedScriptContractFields[] = {
        {"request_envelope_ready", "requestEnvelopeReady", ControlledUserInlineFieldType::Boolean},
        {"result_envelope_ready", "resultEnvelopeReady", ControlledUserInlineFieldType::Boolean},
        {"output_event_envelope_ready", "outputEventEnvelopeReady", ControlledUserInlineFieldType::Boolean},
        {"error_envelope_ready", "errorEnvelopeReady", ControlledUserInlineFieldType::Boolean},
        {"timeout_envelope_ready", "timeoutEnvelopeReady", ControlledUserInlineFieldType::Boolean},
        {"cancellation_envelope_ready", "cancellationEnvelopeReady", ControlledUserInlineFieldType::Boolean},
        {"process_isolation_ready", "processIsolationReady", ControlledUserInlineFieldType::Boolean},
        {"ready_for_mvp_readiness", "readyForMvpReadiness", ControlledUserInlineFieldType::Boolean},
};

constexpr ControlledUserInlineFieldMapping kEmbeddedMvpLifecycleReadinessFields[] = {
        {"lifecycle_ready", "lifecycleReady", ControlledUserInlineFieldType::Boolean},
        {"initialize_ready", "initializeReady", ControlledUserInlineFieldType::Boolean},
        {"isolate_ready", "isolateReady", ControlledUserInlineFieldType::Boolean},
        {"environment_ready", "environmentReady", ControlledUserInlineFieldType::Boolean},
        {"load_environment_ready", "loadEnvironmentReady", ControlledUserInlineFieldType::Boolean},
        {"spin_event_loop_ready", "spinEventLoopReady", ControlledUserInlineFieldType::Boolean},
        {"dispose_ready", "disposeReady", ControlledUserInlineFieldType::Boolean},
        {"single_process_lifecycle_ready", "singleProcessLifecycleReady", ControlledUserInlineFieldType::Boolean},
};

constexpr ControlledUserInlineFieldMapping kEmbeddedMvpSourceInputReadinessFields[] = {
        {"source_input_ready", "sourceInputReady", ControlledUserInlineFieldType::Boolean},
        {"inline_source_ready", "inlineSourceReady", ControlledUserInlineFieldType::Boolean},
        {"file_source_ready", "fileSourceReady", ControlledUserInlineFieldType::Boolean},
        {"source_size_limit_ready", "sourceSizeLimitReady", ControlledUserInlineFieldType::Boolean},
        {"source_encoding_ready", "sourceEncodingReady", ControlledUserInlineFieldType::Boolean},
        {"source_name_ready", "sourceNameReady", ControlledUserInlineFieldType::Boolean},
        {"working_directory_ready", "workingDirectoryReady", ControlledUserInlineFieldType::Boolean},
        {"user_file_read_scope_limited", "userFileReadScopeLimited", ControlledUserInlineFieldType::Boolean},
};

constexpr ControlledUserInlineFieldMapping kEmbeddedMvpOutputReadinessFields[] = {
        {"output_ready", "outputReady", ControlledUserInlineFieldType::Boolean},
        {"stdout_capture_ready", "stdoutCaptureReady", ControlledUserInlineFieldType::Boolean},
        {"stderr_capture_ready", "stderrCaptureReady", ControlledUserInlineFieldType::Boolean},
        {"stdout_event_envelope_ready", "stdoutEventEnvelopeReady", ControlledUserInlineFieldType::Boolean},
        {"stderr_event_envelope_ready", "stderrEventEnvelopeReady", ControlledUserInlineFieldType::Boolean},
        {"console_log_minimal_ready", "consoleLogMinimalReady", ControlledUserInlineFieldType::Boolean},
        {"output_backpressure_policy_ready", "outputBackpressurePolicyReady", ControlledUserInlineFieldType::Boolean},
};

constexpr ControlledUserInlineFieldMapping kEmbeddedMvpErrorHandlingReadinessFields[] = {
        {"error_handling_ready", "errorHandlingReady", ControlledUserInlineFieldType::Boolean},
        {"syntax_error_ready", "syntaxErrorReady", ControlledUserInlineFieldType::Boolean},
        {"runtime_error_ready", "runtimeErrorReady", ControlledUserInlineFieldType::Boolean},
        {"thrown_error_ready", "thrownErrorReady", ControlledUserInlineFieldType::Boolean},
        {"native_error_ready", "nativeErrorReady", ControlledUserInlineFieldType::Boolean},
        {"error_stack_ready", "errorStackReady", ControlledUserInlineFieldType::Boolean},
        {"error_envelope_ready", "errorEnvelopeReady", ControlledUserInlineFieldType::Boolean},
};

constexpr ControlledUserInlineFieldMapping kEmbeddedMvpAsyncReadinessFields[] = {
        {"async_completion_ready", "asyncCompletionReady", ControlledUserInlineFieldType::Boolean},
        {"promise_ready", "promiseReady", ControlledUserInlineFieldType::Boolean},
        {"next_tick_ready", "nextTickReady", ControlledUserInlineFieldType::Boolean},
        {"set_immediate_ready", "setImmediateReady", ControlledUserInlineFieldType::Boolean},
        {"async_ordering_ready", "asyncOrderingReady", ControlledUserInlineFieldType::Boolean},
        {"set_timeout_available", "setTimeoutAvailable", ControlledUserInlineFieldType::Boolean},
        {"timer_absence_documented", "timerAbsenceDocumented", ControlledUserInlineFieldType::Boolean},
};

constexpr ControlledUserInlineFieldMapping kEmbeddedMvpTimeoutIsolationReadinessFields[] = {
        {"timeout_isolation_ready", "timeoutIsolationReady", ControlledUserInlineFieldType::Boolean},
        {"timeout_envelope_ready", "timeoutEnvelopeReady", ControlledUserInlineFieldType::Boolean},
        {"cancellation_envelope_ready", "cancellationEnvelopeReady", ControlledUserInlineFieldType::Boolean},
        {"process_isolation_ready", "processIsolationReady", ControlledUserInlineFieldType::Boolean},
        {"main_process_survives_timeout", "mainProcessSurvivesTimeout", ControlledUserInlineFieldType::Boolean},
        {"main_process_survives_crash", "mainProcessSurvivesCrash", ControlledUserInlineFieldType::Boolean},
        {"single_lifecycle_per_process", "singleLifecyclePerProcess", ControlledUserInlineFieldType::Boolean},
        {"reusable_runtime", "reusableRuntime", ControlledUserInlineFieldType::Boolean},
};

constexpr ControlledUserInlineFieldMapping kEmbeddedMvpSecurityPolicyReadinessFields[] = {
        {"security_policy_ready", "securityPolicyReady", ControlledUserInlineFieldType::Boolean},
        {"npm_allowed", "npmAllowed", ControlledUserInlineFieldType::Boolean},
        {"node_modules_allowed", "nodeModulesAllowed", ControlledUserInlineFieldType::Boolean},
        {"package_json_allowed", "packageJsonAllowed", ControlledUserInlineFieldType::Boolean},
        {"fs_allowed", "fsAllowed", ControlledUserInlineFieldType::Boolean},
        {"network_allowed", "networkAllowed", ControlledUserInlineFieldType::Boolean},
        {"child_process_allowed", "childProcessAllowed", ControlledUserInlineFieldType::Boolean},
        {"worker_allowed", "workerAllowed", ControlledUserInlineFieldType::Boolean},
        {"binder_allowed", "binderAllowed", ControlledUserInlineFieldType::Boolean},
};

constexpr ControlledUserInlineFieldMapping kEmbeddedMvpGoNoGoReadinessFields[] = {
        {"embedded_mvp_ready", "embeddedMvpReady", ControlledUserInlineFieldType::Boolean},
        {"lifecycle_ready", "lifecycleReady", ControlledUserInlineFieldType::Boolean},
        {"source_input_ready", "sourceInputReady", ControlledUserInlineFieldType::Boolean},
        {"output_ready", "outputReady", ControlledUserInlineFieldType::Boolean},
        {"error_handling_ready", "errorHandlingReady", ControlledUserInlineFieldType::Boolean},
        {"async_completion_ready", "asyncCompletionReady", ControlledUserInlineFieldType::Boolean},
        {"timeout_isolation_ready", "timeoutIsolationReady", ControlledUserInlineFieldType::Boolean},
        {"security_policy_ready", "securityPolicyReady", ControlledUserInlineFieldType::Boolean},
        {"next_step", "nextStep", ControlledUserInlineFieldType::String},
};

constexpr UserSourcePreflightFieldMapping kUserSourceDescriptorFields[] = {
        {"require_allowed", "requireAllowed", UserSourcePreflightFieldType::Boolean},
        {"import_allowed", "importAllowed", UserSourcePreflightFieldType::Boolean},
        {"node_modules_allowed", "nodeModulesAllowed", UserSourcePreflightFieldType::Boolean},
};

constexpr UserSourcePreflightFieldMapping kUserSourceSizeFields[] = {
        {"max_bytes", "maxBytes", UserSourcePreflightFieldType::Number},
        {"actual_bytes", "actualBytes", UserSourcePreflightFieldType::Number},
        {"within_limit", "withinLimit", UserSourcePreflightFieldType::Boolean},
        {"rejected", "rejected", UserSourcePreflightFieldType::Boolean},
        {"reject_reason", "rejectReason", UserSourcePreflightFieldType::String},
};

constexpr UserSourcePreflightFieldMapping kUserSourceEncodingFields[] = {
        {"input_encoding", "inputEncoding", UserSourcePreflightFieldType::String},
        {"normalized_encoding", "normalizedEncoding", UserSourcePreflightFieldType::String},
        {"bom_detected", "bomDetected", UserSourcePreflightFieldType::Boolean},
        {"invalid_sequence_detected", "invalidSequenceDetected", UserSourcePreflightFieldType::Boolean},
        {"normalization_succeeded", "normalizationSucceeded", UserSourcePreflightFieldType::Boolean},
};

constexpr UserSourcePreflightFieldMapping kUserSourceNameFields[] = {
        {"source_name", "sourceName", UserSourcePreflightFieldType::String},
        {"filename_used", "filenameUsed", UserSourcePreflightFieldType::Boolean},
        {"display_name_sanitized", "displayNameSanitized", UserSourcePreflightFieldType::Boolean},
        {"stack_trace_name_available", "stackTraceNameAvailable", UserSourcePreflightFieldType::Boolean},
};

constexpr UserSourcePreflightFieldMapping kUserSourceWrapperFields[] = {
        {"wrapper_enabled", "wrapperEnabled", UserSourcePreflightFieldType::Boolean},
        {"wrapper_kind", "wrapperKind", UserSourcePreflightFieldType::String},
        {"return_value_capture_enabled", "returnValueCaptureEnabled", UserSourcePreflightFieldType::Boolean},
        {"error_capture_enabled", "errorCaptureEnabled", UserSourcePreflightFieldType::Boolean},
        {"source_map_used", "sourceMapUsed", UserSourcePreflightFieldType::Boolean},
};

constexpr UserSourcePreflightFieldMapping kUserSourceStrictModeFields[] = {
        {"strict_mode_enabled", "strictModeEnabled", UserSourcePreflightFieldType::Boolean},
        {"user_strict_directive_preserved", "userStrictDirectivePreserved", UserSourcePreflightFieldType::Boolean},
        {"wrapper_strict_directive_added", "wrapperStrictDirectiveAdded", UserSourcePreflightFieldType::Boolean},
};

constexpr UserSourcePreflightFieldMapping kUserSourceCapabilityFields[] = {
        {"require_allowed", "requireAllowed", UserSourcePreflightFieldType::Boolean},
        {"import_allowed", "importAllowed", UserSourcePreflightFieldType::Boolean},
        {"npm_allowed", "npmAllowed", UserSourcePreflightFieldType::Boolean},
        {"node_modules_allowed", "nodeModulesAllowed", UserSourcePreflightFieldType::Boolean},
        {"fs_allowed", "fsAllowed", UserSourcePreflightFieldType::Boolean},
        {"network_allowed", "networkAllowed", UserSourcePreflightFieldType::Boolean},
        {"autojs_api_allowed", "autojsApiAllowed", UserSourcePreflightFieldType::Boolean},
        {"android_bridge_allowed", "androidBridgeAllowed", UserSourcePreflightFieldType::Boolean},
};

constexpr UserSourcePreflightFieldMapping kUserSourcePreflightFields[] = {
        {"ready_for_controlled_execution", "readyForControlledExecution", UserSourcePreflightFieldType::Boolean},
        {"user_source_execution_enabled", "userSourceExecutionEnabled", UserSourcePreflightFieldType::Boolean},
        {"next_stage", "nextStage", UserSourcePreflightFieldType::String},
};

}  // namespace

void putInlineConstantSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_constant.status", "skipped");
    putPayload(payload, "inline_constant.detail", detail);
    putPayload(payload, "inline_constant.result_text", "");
    putPayload(payload, "inline_constant.execution_mode", "");
    putPayload(payload, "inline_constant.source_kind", "");
    putPayload(payload, "inline_constant.user_source_used", false);
    putPayload(payload, "inline_constant.user_file_read", false);
    putPayload(payload, "inline_constant.script_started", false);
    putPayload(payload, "inline_constant.script_completed", false);
    putPayload(payload, "inline_constant.has_value", false);
    putPayload(payload, "inline_constant.value_type", "");
    putPayload(payload, "inline_constant.value_preview", "");
    putPayload(payload, "inline_constant.require_available", false);
    putPayload(payload, "inline_constant.npm_available", false);
    putPayload(payload, "inline_constant.android_bridge_available", false);
    putPayload(payload, "inline_constant.autojs_api_available", false);
    putPayload(payload, "inline_constant.exit_code", static_cast<long long>(0));
    putPayload(payload, "inline_constant.final_state", "");
    putPayload(payload, "inline_constant.sequence_monotonic", false);
    putPayload(payload, "inline_constant.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_constant.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_constant.node_version", "");
    putPayload(payload, "inline_constant.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_constant.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_constant.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_constant.load_environment.result", "skipped");
    putPayload(payload, "inline_constant.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_constant.ms", static_cast<long long>(0));
}

void putInlineReturnValueSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_return_value.status", "skipped");
    putPayload(payload, "inline_return_value.detail", detail);
    putPayload(payload, "inline_return_value.result_text", "");
    putPayload(payload, "inline_return_value.execution_mode", "");
    putPayload(payload, "inline_return_value.source_kind", "");
    putPayload(payload, "inline_return_value.user_source_used", false);
    putPayload(payload, "inline_return_value.user_file_read", false);
    putPayload(payload, "inline_return_value.return_captured", false);
    putPayload(payload, "inline_return_value.return_serializable", false);
    putPayload(payload, "inline_return_value.value_type", "");
    putPayload(payload, "inline_return_value.value_preview", "");
    putPayload(payload, "inline_return_value.value_json", "");
    putPayload(payload, "inline_return_value.result_envelope_ready", false);
    putPayload(payload, "inline_return_value.exit_code", static_cast<long long>(0));
    putPayload(payload, "inline_return_value.error_present", false);
    putPayload(payload, "inline_return_value.require_available", false);
    putPayload(payload, "inline_return_value.npm_available", false);
    putPayload(payload, "inline_return_value.android_bridge_available", false);
    putPayload(payload, "inline_return_value.autojs_api_available", false);
    putPayload(payload, "inline_return_value.final_state", "");
    putPayload(payload, "inline_return_value.sequence_monotonic", false);
    putPayload(payload, "inline_return_value.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_return_value.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_return_value.node_version", "");
    putPayload(payload, "inline_return_value.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_return_value.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_return_value.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_return_value.load_environment.result", "skipped");
    putPayload(payload, "inline_return_value.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_return_value.ms", static_cast<long long>(0));
}

void putInlineErrorSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_error.status", "skipped");
    putPayload(payload, "inline_error.detail", detail);
    putPayload(payload, "inline_error.result_text", "");
    putPayload(payload, "inline_error.execution_mode", "");
    putPayload(payload, "inline_error.source_kind", "");
    putPayload(payload, "inline_error.user_source_used", false);
    putPayload(payload, "inline_error.user_file_read", false);
    putPayload(payload, "inline_error.error_thrown", false);
    putPayload(payload, "inline_error.error_captured", false);
    putPayload(payload, "inline_error.error_name", "");
    putPayload(payload, "inline_error.error_message", "");
    putPayload(payload, "inline_error.stack_available", false);
    putPayload(payload, "inline_error.result_envelope_ready", false);
    putPayload(payload, "inline_error.exit_code", static_cast<long long>(0));
    putPayload(payload, "inline_error.final_state", "");
    putPayload(payload, "inline_error.sequence_monotonic", false);
    putPayload(payload, "inline_error.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_error.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_error.require_available", false);
    putPayload(payload, "inline_error.npm_available", false);
    putPayload(payload, "inline_error.android_bridge_available", false);
    putPayload(payload, "inline_error.autojs_api_available", false);
    putPayload(payload, "inline_error.node_version", "");
    putPayload(payload, "inline_error.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_error.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_error.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_error.load_environment.result", "skipped");
    putPayload(payload, "inline_error.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_error.ms", static_cast<long long>(0));
}

void putInlineOutputSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_output.status", "skipped");
    putPayload(payload, "inline_output.detail", detail);
    putPayload(payload, "inline_output.result_text", "");
    putPayload(payload, "inline_output.execution_mode", "");
    putPayload(payload, "inline_output.source_kind", "");
    putPayload(payload, "inline_output.user_source_used", false);
    putPayload(payload, "inline_output.user_file_read", false);
    putPayload(payload, "inline_output.stdout_written", false);
    putPayload(payload, "inline_output.stderr_written", false);
    putPayload(payload, "inline_output.stdout_captured", false);
    putPayload(payload, "inline_output.stderr_captured", false);
    putPayload(payload, "inline_output.stdout_contains_probe", false);
    putPayload(payload, "inline_output.stderr_contains_probe", false);
    putPayload(payload, "inline_output.stdout_event_count", static_cast<long long>(0));
    putPayload(payload, "inline_output.stderr_event_count", static_cast<long long>(0));
    putPayload(payload, "inline_output.output_dropped_count", static_cast<long long>(0));
    putPayload(payload, "inline_output.exit_code", static_cast<long long>(0));
    putPayload(payload, "inline_output.final_state", "");
    putPayload(payload, "inline_output.sequence_monotonic", false);
    putPayload(payload, "inline_output.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_output.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_output.require_available", false);
    putPayload(payload, "inline_output.npm_available", false);
    putPayload(payload, "inline_output.android_bridge_available", false);
    putPayload(payload, "inline_output.autojs_api_available", false);
    putPayload(payload, "inline_output.node_version", "");
    putPayload(payload, "inline_output.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_output.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_output.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_output.load_environment.result", "skipped");
    putPayload(payload, "inline_output.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_output.ms", static_cast<long long>(0));
}

void putInlineAsyncSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_async.status", "skipped");
    putPayload(payload, "inline_async.detail", detail);
    putPayload(payload, "inline_async.result_text", "");
    putPayload(payload, "inline_async.execution_mode", "");
    putPayload(payload, "inline_async.source_kind", "");
    putPayload(payload, "inline_async.user_source_used", false);
    putPayload(payload, "inline_async.user_file_read", false);
    putPayload(payload, "inline_async.async_started", false);
    putPayload(payload, "inline_async.async_completed", false);
    putPayload(payload, "inline_async.has_next_tick", false);
    putPayload(payload, "inline_async.has_promise", false);
    putPayload(payload, "inline_async.has_set_immediate", false);
    putPayload(payload, "inline_async.has_set_timeout", false);
    putPayload(payload, "inline_async.has_queue_microtask", false);
    putPayload(payload, "inline_async.order", "");
    putPayload(payload, "inline_async.order_matches_expected", false);
    putPayload(payload, "inline_async.result_written_after_async", false);
    putPayload(payload, "inline_async.exit_code", static_cast<long long>(0));
    putPayload(payload, "inline_async.final_state", "");
    putPayload(payload, "inline_async.sequence_monotonic", false);
    putPayload(payload, "inline_async.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_async.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_async.require_available", false);
    putPayload(payload, "inline_async.npm_available", false);
    putPayload(payload, "inline_async.android_bridge_available", false);
    putPayload(payload, "inline_async.autojs_api_available", false);
    putPayload(payload, "inline_async.node_version", "");
    putPayload(payload, "inline_async.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_async.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_async.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_async.load_environment.result", "skipped");
    putPayload(payload, "inline_async.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_async.ms", static_cast<long long>(0));
}

void putInlineCancelSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_cancel.status", "skipped");
    putPayload(payload, "inline_cancel.detail", detail);
    putPayload(payload, "inline_cancel.result_text", "");
    putPayload(payload, "inline_cancel.execution_mode", "");
    putPayload(payload, "inline_cancel.source_kind", "");
    putPayload(payload, "inline_cancel.user_source_used", false);
    putPayload(payload, "inline_cancel.user_file_read", false);
    putPayload(payload, "inline_cancel.real_v8_interrupt", false);
    putPayload(payload, "inline_cancel.cancel_token_created", false);
    putPayload(payload, "inline_cancel.cancel_requested", false);
    putPayload(payload, "inline_cancel.cancel_observed", false);
    putPayload(payload, "inline_cancel.cancel_reason", "");
    putPayload(payload, "inline_cancel.cancel_exit_code", static_cast<long long>(0));
    putPayload(payload, "inline_cancel.cancelled_event_emitted", false);
    putPayload(payload, "inline_cancel.script_started", false);
    putPayload(payload, "inline_cancel.script_cancelled", false);
    putPayload(payload, "inline_cancel.script_completed", false);
    putPayload(payload, "inline_cancel.result_envelope_ready", false);
    putPayload(payload, "inline_cancel.require_available", false);
    putPayload(payload, "inline_cancel.npm_available", false);
    putPayload(payload, "inline_cancel.android_bridge_available", false);
    putPayload(payload, "inline_cancel.autojs_api_available", false);
    putPayload(payload, "inline_cancel.final_state", "");
    putPayload(payload, "inline_cancel.sequence_monotonic", false);
    putPayload(payload, "inline_cancel.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_cancel.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_cancel.node_version", "");
    putPayload(payload, "inline_cancel.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_cancel.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_cancel.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_cancel.load_environment.result", "skipped");
    putPayload(payload, "inline_cancel.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_cancel.ms", static_cast<long long>(0));
}

void putInlineTimeoutSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_timeout.status", "skipped");
    putPayload(payload, "inline_timeout.detail", detail);
    putPayload(payload, "inline_timeout.result_text", "");
    putPayload(payload, "inline_timeout.execution_mode", "");
    putPayload(payload, "inline_timeout.source_kind", "");
    putPayload(payload, "inline_timeout.user_source_used", false);
    putPayload(payload, "inline_timeout.user_file_read", false);
    putPayload(payload, "inline_timeout.real_hard_kill", false);
    putPayload(payload, "inline_timeout.timeout_policy_created", false);
    putPayload(payload, "inline_timeout.timeout_ms", static_cast<long long>(0));
    putPayload(payload, "inline_timeout.timeout_detected", false);
    putPayload(payload, "inline_timeout.timeout_reason", "");
    putPayload(payload, "inline_timeout.timeout_exit_code", static_cast<long long>(0));
    putPayload(payload, "inline_timeout.timeout_event_emitted", false);
    putPayload(payload, "inline_timeout.script_started", false);
    putPayload(payload, "inline_timeout.script_timed_out", false);
    putPayload(payload, "inline_timeout.script_completed", false);
    putPayload(payload, "inline_timeout.result_error_code", "");
    putPayload(payload, "inline_timeout.result_envelope_ready", false);
    putPayload(payload, "inline_timeout.dispose_after_timeout", false);
    putPayload(payload, "inline_timeout.restart_required", false);
    putPayload(payload, "inline_timeout.diagnostics_preserved", false);
    putPayload(payload, "inline_timeout.require_available", false);
    putPayload(payload, "inline_timeout.npm_available", false);
    putPayload(payload, "inline_timeout.android_bridge_available", false);
    putPayload(payload, "inline_timeout.autojs_api_available", false);
    putPayload(payload, "inline_timeout.final_state", "");
    putPayload(payload, "inline_timeout.sequence_monotonic", false);
    putPayload(payload, "inline_timeout.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_timeout.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_timeout.node_version", "");
    putPayload(payload, "inline_timeout.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_timeout.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_timeout.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_timeout.load_environment.result", "skipped");
    putPayload(payload, "inline_timeout.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_timeout.ms", static_cast<long long>(0));
}

void putInlineRepeatedProcessSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_repeated_process.status", "skipped");
    putPayload(payload, "inline_repeated_process.detail", detail);
    putPayload(payload, "inline_repeated_process.result_text", "");
    putPayload(payload, "inline_repeated_process.execution_mode", "");
    putPayload(payload, "inline_repeated_process.source_kind", "");
    putPayload(payload, "inline_repeated_process.user_source_used", false);
    putPayload(payload, "inline_repeated_process.user_file_read", false);
    putPayload(payload, "inline_repeated_process.same_process_second_lifecycle", false);
    putPayload(payload, "inline_repeated_process.single_process_reuse_allowed", false);
    putPayload(payload, "inline_repeated_process.requires_fresh_process", false);
    putPayload(payload, "inline_repeated_process.force_stop_required_between_full_probes", false);
    putPayload(payload, "inline_repeated_process.second_lifecycle_in_same_process_allowed", false);
    putPayload(payload, "inline_repeated_process.first_process_policy", "");
    putPayload(payload, "inline_repeated_process.second_process_policy", "");
    putPayload(payload, "inline_repeated_process.double_initialization_risk_detected", false);
    putPayload(payload, "inline_repeated_process.double_initialization_error_code", "");
    putPayload(payload, "inline_repeated_process.script_started", false);
    putPayload(payload, "inline_repeated_process.script_completed", false);
    putPayload(payload, "inline_repeated_process.exit_code", static_cast<long long>(0));
    putPayload(payload, "inline_repeated_process.final_state", "");
    putPayload(payload, "inline_repeated_process.sequence_monotonic", false);
    putPayload(payload, "inline_repeated_process.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_repeated_process.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_repeated_process.node_version", "");
    putPayload(payload, "inline_repeated_process.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_repeated_process.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_repeated_process.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_repeated_process.load_environment.result", "skipped");
    putPayload(payload, "inline_repeated_process.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_repeated_process.ms", static_cast<long long>(0));
}

void putInlineBuiltinPolicySkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_builtin_policy.status", "skipped");
    putPayload(payload, "inline_builtin_policy.detail", detail);
    putPayload(payload, "inline_builtin_policy.result_text", "");
    putPayload(payload, "inline_builtin_policy.execution_mode", "");
    putPayload(payload, "inline_builtin_policy.source_kind", "");
    putPayload(payload, "inline_builtin_policy.user_source_used", false);
    putPayload(payload, "inline_builtin_policy.user_file_read", false);
    putPayload(payload, "inline_builtin_policy.builtin_module_policy", "");
    putPayload(payload, "inline_builtin_policy.allowlist_enabled", false);
    putPayload(payload, "inline_builtin_policy.allowed_builtin_count", static_cast<long long>(0));
    putPayload(payload, "inline_builtin_policy.denied_builtin_count", static_cast<long long>(0));
    putPayload(payload, "inline_builtin_policy.fs_allowed", false);
    putPayload(payload, "inline_builtin_policy.path_allowed", false);
    putPayload(payload, "inline_builtin_policy.child_process_allowed", false);
    putPayload(payload, "inline_builtin_policy.worker_threads_allowed", false);
    putPayload(payload, "inline_builtin_policy.http_allowed", false);
    putPayload(payload, "inline_builtin_policy.denied_error_code", "");
    putPayload(payload, "inline_builtin_policy.require_called", false);
    putPayload(payload, "inline_builtin_policy.real_module_resolution", false);
    putPayload(payload, "inline_builtin_policy.final_state", "");
    putPayload(payload, "inline_builtin_policy.sequence_monotonic", false);
    putPayload(payload, "inline_builtin_policy.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_builtin_policy.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_builtin_policy.node_version", "");
    putPayload(payload, "inline_builtin_policy.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_builtin_policy.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_builtin_policy.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_builtin_policy.load_environment.result", "skipped");
    putPayload(payload, "inline_builtin_policy.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_builtin_policy.ms", static_cast<long long>(0));
}

void putInlineRequireDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_require_denied.status", "skipped");
    putPayload(payload, "inline_require_denied.detail", detail);
    putPayload(payload, "inline_require_denied.result_text", "");
    putPayload(payload, "inline_require_denied.execution_mode", "");
    putPayload(payload, "inline_require_denied.source_kind", "");
    putPayload(payload, "inline_require_denied.user_source_used", false);
    putPayload(payload, "inline_require_denied.user_file_read", false);
    putPayload(payload, "inline_require_denied.require_available", false);
    putPayload(payload, "inline_require_denied.require_called", false);
    putPayload(payload, "inline_require_denied.require_denied", false);
    putPayload(payload, "inline_require_denied.denied_error_code", "");
    putPayload(payload, "inline_require_denied.denied_error_message", "");
    putPayload(payload, "inline_require_denied.commonjs_resolution_attempted", false);
    putPayload(payload, "inline_require_denied.node_modules_resolution_attempted", false);
    putPayload(payload, "inline_require_denied.package_json_read", false);
    putPayload(payload, "inline_require_denied.real_module_resolution", false);
    putPayload(payload, "inline_require_denied.final_state", "");
    putPayload(payload, "inline_require_denied.sequence_monotonic", false);
    putPayload(payload, "inline_require_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_require_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_require_denied.node_version", "");
    putPayload(payload, "inline_require_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_require_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_require_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_require_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_require_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_require_denied.ms", static_cast<long long>(0));
}

void putInlineNpmDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_npm_denied.status", "skipped");
    putPayload(payload, "inline_npm_denied.detail", detail);
    putPayload(payload, "inline_npm_denied.result_text", "");
    putPayload(payload, "inline_npm_denied.execution_mode", "");
    putPayload(payload, "inline_npm_denied.source_kind", "");
    putPayload(payload, "inline_npm_denied.user_source_used", false);
    putPayload(payload, "inline_npm_denied.user_file_read", false);
    putPayload(payload, "inline_npm_denied.npm_available", false);
    putPayload(payload, "inline_npm_denied.npm_command_allowed", false);
    putPayload(payload, "inline_npm_denied.npm_install_allowed", false);
    putPayload(payload, "inline_npm_denied.package_manager_invoked", false);
    putPayload(payload, "inline_npm_denied.network_used", false);
    putPayload(payload, "inline_npm_denied.disk_write_attempted", false);
    putPayload(payload, "inline_npm_denied.denied_error_code", "");
    putPayload(payload, "inline_npm_denied.denied_error_message", "");
    putPayload(payload, "inline_npm_denied.package_json_read", false);
    putPayload(payload, "inline_npm_denied.node_modules_resolution_attempted", false);
    putPayload(payload, "inline_npm_denied.lifecycle_scripts_allowed", false);
    putPayload(payload, "inline_npm_denied.native_addon_build_allowed", false);
    putPayload(payload, "inline_npm_denied.final_state", "");
    putPayload(payload, "inline_npm_denied.sequence_monotonic", false);
    putPayload(payload, "inline_npm_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_npm_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_npm_denied.node_version", "");
    putPayload(payload, "inline_npm_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_npm_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_npm_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_npm_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_npm_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_npm_denied.ms", static_cast<long long>(0));
}

void putInlinePackageJsonDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_package_json_denied.status", "skipped");
    putPayload(payload, "inline_package_json_denied.detail", detail);
    putPayload(payload, "inline_package_json_denied.result_text", "");
    putPayload(payload, "inline_package_json_denied.execution_mode", "");
    putPayload(payload, "inline_package_json_denied.source_kind", "");
    putPayload(payload, "inline_package_json_denied.user_source_used", false);
    putPayload(payload, "inline_package_json_denied.user_file_read", false);
    putPayload(payload, "inline_package_json_denied.package_json_read", false);
    putPayload(payload, "inline_package_json_denied.package_json_parse_attempted", false);
    putPayload(payload, "inline_package_json_denied.package_main_resolution_attempted", false);
    putPayload(payload, "inline_package_json_denied.package_exports_resolution_attempted", false);
    putPayload(payload, "inline_package_json_denied.package_imports_resolution_attempted", false);
    putPayload(payload, "inline_package_json_denied.denied_error_code", "");
    putPayload(payload, "inline_package_json_denied.denied_error_message", "");
    putPayload(payload, "inline_package_json_denied.disk_read_attempted", false);
    putPayload(payload, "inline_package_json_denied.real_module_resolution", false);
    putPayload(payload, "inline_package_json_denied.node_modules_resolution_attempted", false);
    putPayload(payload, "inline_package_json_denied.final_state", "");
    putPayload(payload, "inline_package_json_denied.sequence_monotonic", false);
    putPayload(payload, "inline_package_json_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_package_json_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_package_json_denied.node_version", "");
    putPayload(payload, "inline_package_json_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_package_json_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_package_json_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_package_json_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_package_json_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_package_json_denied.ms", static_cast<long long>(0));
}

void putInlineNodeModulesDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_node_modules_denied.status", "skipped");
    putPayload(payload, "inline_node_modules_denied.detail", detail);
    putPayload(payload, "inline_node_modules_denied.result_text", "");
    putPayload(payload, "inline_node_modules_denied.execution_mode", "");
    putPayload(payload, "inline_node_modules_denied.source_kind", "");
    putPayload(payload, "inline_node_modules_denied.user_source_used", false);
    putPayload(payload, "inline_node_modules_denied.user_file_read", false);
    putPayload(payload, "inline_node_modules_denied.node_modules_resolution_attempted", false);
    putPayload(payload, "inline_node_modules_denied.node_modules_scan_attempted", false);
    putPayload(payload, "inline_node_modules_denied.package_json_read", false);
    putPayload(payload, "inline_node_modules_denied.disk_read_attempted", false);
    putPayload(payload, "inline_node_modules_denied.real_module_resolution", false);
    putPayload(payload, "inline_node_modules_denied.lookup_paths_count", static_cast<long long>(0));
    putPayload(payload, "inline_node_modules_denied.lookup_paths_generated", false);
    putPayload(payload, "inline_node_modules_denied.lookup_cache_used", false);
    putPayload(payload, "inline_node_modules_denied.denied_error_code", "");
    putPayload(payload, "inline_node_modules_denied.denied_error_message", "");
    putPayload(payload, "inline_node_modules_denied.final_state", "");
    putPayload(payload, "inline_node_modules_denied.sequence_monotonic", false);
    putPayload(payload, "inline_node_modules_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_node_modules_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_node_modules_denied.node_version", "");
    putPayload(payload, "inline_node_modules_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_node_modules_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_node_modules_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_node_modules_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_node_modules_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_node_modules_denied.ms", static_cast<long long>(0));
}

void putInlineNativeAddonDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_native_addon_denied.status", "skipped");
    putPayload(payload, "inline_native_addon_denied.detail", detail);
    putPayload(payload, "inline_native_addon_denied.result_text", "");
    putPayload(payload, "inline_native_addon_denied.execution_mode", "");
    putPayload(payload, "inline_native_addon_denied.source_kind", "");
    putPayload(payload, "inline_native_addon_denied.user_source_used", false);
    putPayload(payload, "inline_native_addon_denied.user_file_read", false);
    putPayload(payload, "inline_native_addon_denied.native_addon_allowed", false);
    putPayload(payload, "inline_native_addon_denied.node_file_load_attempted", false);
    putPayload(payload, "inline_native_addon_denied.dlopen_attempted", false);
    putPayload(payload, "inline_native_addon_denied.symbol_resolution_attempted", false);
    putPayload(payload, "inline_native_addon_denied.native_addon_build_allowed", false);
    putPayload(payload, "inline_native_addon_denied.disk_read_attempted", false);
    putPayload(payload, "inline_native_addon_denied.disk_write_attempted", false);
    putPayload(payload, "inline_native_addon_denied.real_module_resolution", false);
    putPayload(payload, "inline_native_addon_denied.denied_error_code", "");
    putPayload(payload, "inline_native_addon_denied.denied_error_message", "");
    putPayload(payload, "inline_native_addon_denied.final_state", "");
    putPayload(payload, "inline_native_addon_denied.sequence_monotonic", false);
    putPayload(payload, "inline_native_addon_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_native_addon_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_native_addon_denied.node_version", "");
    putPayload(payload, "inline_native_addon_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_native_addon_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_native_addon_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_native_addon_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_native_addon_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_native_addon_denied.ms", static_cast<long long>(0));
}

void putInlineEsmDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_esm_denied.status", "skipped");
    putPayload(payload, "inline_esm_denied.detail", detail);
    putPayload(payload, "inline_esm_denied.result_text", "");
    putPayload(payload, "inline_esm_denied.execution_mode", "");
    putPayload(payload, "inline_esm_denied.source_kind", "");
    putPayload(payload, "inline_esm_denied.user_source_used", false);
    putPayload(payload, "inline_esm_denied.user_file_read", false);
    putPayload(payload, "inline_esm_denied.esm_loader_enabled", false);
    putPayload(payload, "inline_esm_denied.static_import_allowed", false);
    putPayload(payload, "inline_esm_denied.import_map_used", false);
    putPayload(payload, "inline_esm_denied.package_type_module_read", false);
    putPayload(payload, "inline_esm_denied.package_json_read", false);
    putPayload(payload, "inline_esm_denied.module_graph_created", false);
    putPayload(payload, "inline_esm_denied.real_module_resolution", false);
    putPayload(payload, "inline_esm_denied.disk_read_attempted", false);
    putPayload(payload, "inline_esm_denied.network_used", false);
    putPayload(payload, "inline_esm_denied.denied_error_code", "");
    putPayload(payload, "inline_esm_denied.denied_error_message", "");
    putPayload(payload, "inline_esm_denied.final_state", "");
    putPayload(payload, "inline_esm_denied.sequence_monotonic", false);
    putPayload(payload, "inline_esm_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_esm_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_esm_denied.node_version", "");
    putPayload(payload, "inline_esm_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_esm_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_esm_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_esm_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_esm_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_esm_denied.ms", static_cast<long long>(0));
}

void putInlineDynamicImportDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_dynamic_import_denied.status", "skipped");
    putPayload(payload, "inline_dynamic_import_denied.detail", detail);
    putPayload(payload, "inline_dynamic_import_denied.result_text", "");
    putPayload(payload, "inline_dynamic_import_denied.execution_mode", "");
    putPayload(payload, "inline_dynamic_import_denied.source_kind", "");
    putPayload(payload, "inline_dynamic_import_denied.user_source_used", false);
    putPayload(payload, "inline_dynamic_import_denied.user_file_read", false);
    putPayload(payload, "inline_dynamic_import_denied.dynamic_import_allowed", false);
    putPayload(payload, "inline_dynamic_import_denied.import_called", false);
    putPayload(payload, "inline_dynamic_import_denied.import_promise_created", false);
    putPayload(payload, "inline_dynamic_import_denied.module_resolution_attempted", false);
    putPayload(payload, "inline_dynamic_import_denied.module_graph_created", false);
    putPayload(payload, "inline_dynamic_import_denied.package_json_read", false);
    putPayload(payload, "inline_dynamic_import_denied.node_modules_resolution_attempted", false);
    putPayload(payload, "inline_dynamic_import_denied.disk_read_attempted", false);
    putPayload(payload, "inline_dynamic_import_denied.network_used", false);
    putPayload(payload, "inline_dynamic_import_denied.denied_error_code", "");
    putPayload(payload, "inline_dynamic_import_denied.denied_error_message", "");
    putPayload(payload, "inline_dynamic_import_denied.final_state", "");
    putPayload(payload, "inline_dynamic_import_denied.sequence_monotonic", false);
    putPayload(payload, "inline_dynamic_import_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_dynamic_import_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_dynamic_import_denied.node_version", "");
    putPayload(payload, "inline_dynamic_import_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_dynamic_import_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_dynamic_import_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_dynamic_import_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_dynamic_import_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_dynamic_import_denied.ms", static_cast<long long>(0));
}

void putInlineWorkerDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_worker_denied.status", "skipped");
    putPayload(payload, "inline_worker_denied.detail", detail);
    putPayload(payload, "inline_worker_denied.result_text", "");
    putPayload(payload, "inline_worker_denied.execution_mode", "");
    putPayload(payload, "inline_worker_denied.source_kind", "");
    putPayload(payload, "inline_worker_denied.user_source_used", false);
    putPayload(payload, "inline_worker_denied.user_file_read", false);
    putPayload(payload, "inline_worker_denied.worker_threads_allowed", false);
    putPayload(payload, "inline_worker_denied.worker_created", false);
    putPayload(payload, "inline_worker_denied.worker_bootstrap_attempted", false);
    putPayload(payload, "inline_worker_denied.thread_created", false);
    putPayload(payload, "inline_worker_denied.message_port_created", false);
    putPayload(payload, "inline_worker_denied.shared_array_buffer_allowed", false);
    putPayload(payload, "inline_worker_denied.native_thread_spawned", false);
    putPayload(payload, "inline_worker_denied.real_module_resolution", false);
    putPayload(payload, "inline_worker_denied.disk_read_attempted", false);
    putPayload(payload, "inline_worker_denied.denied_error_code", "");
    putPayload(payload, "inline_worker_denied.denied_error_message", "");
    putPayload(payload, "inline_worker_denied.final_state", "");
    putPayload(payload, "inline_worker_denied.sequence_monotonic", false);
    putPayload(payload, "inline_worker_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_worker_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_worker_denied.node_version", "");
    putPayload(payload, "inline_worker_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_worker_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_worker_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_worker_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_worker_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_worker_denied.ms", static_cast<long long>(0));
}

void putInlineCapabilitySummarySkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_capability_summary.status", "skipped");
    putPayload(payload, "inline_capability_summary.detail", detail);
    putPayload(payload, "inline_capability_summary.result_text", "");
    putPayload(payload, "inline_capability_summary.execution_mode", "");
    putPayload(payload, "inline_capability_summary.source_kind", "");
    putPayload(payload, "inline_capability_summary.user_source_used", false);
    putPayload(payload, "inline_capability_summary.user_file_read", false);
    putPayload(payload, "inline_capability_summary.constant_execution_supported", false);
    putPayload(payload, "inline_capability_summary.return_value_supported", false);
    putPayload(payload, "inline_capability_summary.error_capture_supported", false);
    putPayload(payload, "inline_capability_summary.stdout_capture_supported", false);
    putPayload(payload, "inline_capability_summary.stderr_capture_supported", false);
    putPayload(payload, "inline_capability_summary.async_completion_supported", false);
    putPayload(payload, "inline_capability_summary.cancel_envelope_supported", false);
    putPayload(payload, "inline_capability_summary.timeout_envelope_supported", false);
    putPayload(payload, "inline_capability_summary.fresh_process_required", false);
    putPayload(payload, "inline_capability_summary.require_supported", false);
    putPayload(payload, "inline_capability_summary.npm_supported", false);
    putPayload(payload, "inline_capability_summary.package_json_resolution_supported", false);
    putPayload(payload, "inline_capability_summary.node_modules_resolution_supported", false);
    putPayload(payload, "inline_capability_summary.native_addon_supported", false);
    putPayload(payload, "inline_capability_summary.esm_supported", false);
    putPayload(payload, "inline_capability_summary.dynamic_import_supported", false);
    putPayload(payload, "inline_capability_summary.worker_threads_supported", false);
    putPayload(payload, "inline_capability_summary.user_file_execution_supported", false);
    putPayload(payload, "inline_capability_summary.dynamic_source_supported", false);
    putPayload(payload, "inline_capability_summary.autojs_api_supported", false);
    putPayload(payload, "inline_capability_summary.android_bridge_supported", false);
    putPayload(payload, "inline_capability_summary.auto_backend_supported", false);
    putPayload(payload, "inline_capability_summary.ready_for_controlled_inline_execution", false);
    putPayload(payload, "inline_capability_summary.ready_for_user_script_execution", false);
    putPayload(payload, "inline_capability_summary.ready_for_module_loading", false);
    putPayload(payload, "inline_capability_summary.ready_for_autojs_bridge", false);
    putPayload(payload, "inline_capability_summary.final_state", "");
    putPayload(payload, "inline_capability_summary.sequence_monotonic", false);
    putPayload(payload, "inline_capability_summary.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_capability_summary.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_capability_summary.node_version", "");
    putPayload(payload, "inline_capability_summary.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_capability_summary.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_capability_summary.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_capability_summary.load_environment.result", "skipped");
    putPayload(payload, "inline_capability_summary.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_capability_summary.ms", static_cast<long long>(0));
}

void putInlineFilesystemDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_filesystem_denied.status", "skipped");
    putPayload(payload, "inline_filesystem_denied.detail", detail);
    putPayload(payload, "inline_filesystem_denied.result_text", "");
    putPayload(payload, "inline_filesystem_denied.execution_mode", "");
    putPayload(payload, "inline_filesystem_denied.source_kind", "");
    putPayload(payload, "inline_filesystem_denied.user_source_used", false);
    putPayload(payload, "inline_filesystem_denied.user_file_read", false);
    putPayload(payload, "inline_filesystem_denied.fs_access_allowed", false);
    putPayload(payload, "inline_filesystem_denied.fs_module_loaded", false);
    putPayload(payload, "inline_filesystem_denied.file_read_attempted", false);
    putPayload(payload, "inline_filesystem_denied.file_write_attempted", false);
    putPayload(payload, "inline_filesystem_denied.directory_scan_attempted", false);
    putPayload(payload, "inline_filesystem_denied.realpath_attempted", false);
    putPayload(payload, "inline_filesystem_denied.disk_read_attempted", false);
    putPayload(payload, "inline_filesystem_denied.disk_write_attempted", false);
    putPayload(payload, "inline_filesystem_denied.real_module_resolution", false);
    putPayload(payload, "inline_filesystem_denied.denied_error_code", "");
    putPayload(payload, "inline_filesystem_denied.denied_error_message", "");
    putPayload(payload, "inline_filesystem_denied.final_state", "");
    putPayload(payload, "inline_filesystem_denied.sequence_monotonic", false);
    putPayload(payload, "inline_filesystem_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_filesystem_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_filesystem_denied.node_version", "");
    putPayload(payload, "inline_filesystem_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_filesystem_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_filesystem_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_filesystem_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_filesystem_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_filesystem_denied.ms", static_cast<long long>(0));
}

void putInlineChildProcessDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_child_process_denied.status", "skipped");
    putPayload(payload, "inline_child_process_denied.detail", detail);
    putPayload(payload, "inline_child_process_denied.result_text", "");
    putPayload(payload, "inline_child_process_denied.execution_mode", "");
    putPayload(payload, "inline_child_process_denied.source_kind", "");
    putPayload(payload, "inline_child_process_denied.user_source_used", false);
    putPayload(payload, "inline_child_process_denied.user_file_read", false);
    putPayload(payload, "inline_child_process_denied.child_process_allowed", false);
    putPayload(payload, "inline_child_process_denied.child_process_module_loaded", false);
    putPayload(payload, "inline_child_process_denied.spawn_attempted", false);
    putPayload(payload, "inline_child_process_denied.exec_attempted", false);
    putPayload(payload, "inline_child_process_denied.fork_attempted", false);
    putPayload(payload, "inline_child_process_denied.process_created", false);
    putPayload(payload, "inline_child_process_denied.shell_invoked", false);
    putPayload(payload, "inline_child_process_denied.native_process_started", false);
    putPayload(payload, "inline_child_process_denied.real_module_resolution", false);
    putPayload(payload, "inline_child_process_denied.denied_error_code", "");
    putPayload(payload, "inline_child_process_denied.denied_error_message", "");
    putPayload(payload, "inline_child_process_denied.final_state", "");
    putPayload(payload, "inline_child_process_denied.sequence_monotonic", false);
    putPayload(payload, "inline_child_process_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_child_process_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_child_process_denied.node_version", "");
    putPayload(payload, "inline_child_process_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_child_process_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_child_process_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_child_process_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_child_process_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_child_process_denied.ms", static_cast<long long>(0));
}

void putInlineNetworkDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_network_denied.status", "skipped");
    putPayload(payload, "inline_network_denied.detail", detail);
    putPayload(payload, "inline_network_denied.result_text", "");
    putPayload(payload, "inline_network_denied.execution_mode", "");
    putPayload(payload, "inline_network_denied.source_kind", "");
    putPayload(payload, "inline_network_denied.user_source_used", false);
    putPayload(payload, "inline_network_denied.user_file_read", false);
    putPayload(payload, "inline_network_denied.network_allowed", false);
    putPayload(payload, "inline_network_denied.http_module_loaded", false);
    putPayload(payload, "inline_network_denied.https_module_loaded", false);
    putPayload(payload, "inline_network_denied.net_module_loaded", false);
    putPayload(payload, "inline_network_denied.dns_module_loaded", false);
    putPayload(payload, "inline_network_denied.socket_created", false);
    putPayload(payload, "inline_network_denied.connection_attempted", false);
    putPayload(payload, "inline_network_denied.dns_lookup_attempted", false);
    putPayload(payload, "inline_network_denied.request_sent", false);
    putPayload(payload, "inline_network_denied.network_used", false);
    putPayload(payload, "inline_network_denied.real_module_resolution", false);
    putPayload(payload, "inline_network_denied.denied_error_code", "");
    putPayload(payload, "inline_network_denied.denied_error_message", "");
    putPayload(payload, "inline_network_denied.final_state", "");
    putPayload(payload, "inline_network_denied.sequence_monotonic", false);
    putPayload(payload, "inline_network_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_network_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_network_denied.node_version", "");
    putPayload(payload, "inline_network_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_network_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_network_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_network_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_network_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_network_denied.ms", static_cast<long long>(0));
}

void putInlinePermissionsDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_permissions_denied.status", "skipped");
    putPayload(payload, "inline_permissions_denied.detail", detail);
    putPayload(payload, "inline_permissions_denied.result_text", "");
    putPayload(payload, "inline_permissions_denied.execution_mode", "");
    putPayload(payload, "inline_permissions_denied.source_kind", "");
    putPayload(payload, "inline_permissions_denied.user_source_used", false);
    putPayload(payload, "inline_permissions_denied.user_file_read", false);
    putPayload(payload, "inline_permissions_denied.permission_bridge_allowed", false);
    putPayload(payload, "inline_permissions_denied.android_permission_query_allowed", false);
    putPayload(payload, "inline_permissions_denied.runtime_permission_request_allowed", false);
    putPayload(payload, "inline_permissions_denied.special_permission_request_allowed", false);
    putPayload(payload, "inline_permissions_denied.accessibility_permission_bridge_allowed", false);
    putPayload(payload, "inline_permissions_denied.notification_permission_bridge_allowed", false);
    putPayload(payload, "inline_permissions_denied.android_api_called", false);
    putPayload(payload, "inline_permissions_denied.binder_called", false);
    putPayload(payload, "inline_permissions_denied.activity_started", false);
    putPayload(payload, "inline_permissions_denied.permission_dialog_shown", false);
    putPayload(payload, "inline_permissions_denied.denied_error_code", "");
    putPayload(payload, "inline_permissions_denied.denied_error_message", "");
    putPayload(payload, "inline_permissions_denied.final_state", "");
    putPayload(payload, "inline_permissions_denied.sequence_monotonic", false);
    putPayload(payload, "inline_permissions_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_permissions_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_permissions_denied.node_version", "");
    putPayload(payload, "inline_permissions_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_permissions_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_permissions_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_permissions_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_permissions_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_permissions_denied.ms", static_cast<long long>(0));
}

void putInlineAndroidBridgeDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_android_bridge_denied.status", "skipped");
    putPayload(payload, "inline_android_bridge_denied.detail", detail);
    putPayload(payload, "inline_android_bridge_denied.result_text", "");
    putPayload(payload, "inline_android_bridge_denied.execution_mode", "");
    putPayload(payload, "inline_android_bridge_denied.source_kind", "");
    putPayload(payload, "inline_android_bridge_denied.user_source_used", false);
    putPayload(payload, "inline_android_bridge_denied.user_file_read", false);
    putPayload(payload, "inline_android_bridge_denied.android_bridge_allowed", false);
    putPayload(payload, "inline_android_bridge_denied.context_injected", false);
    putPayload(payload, "inline_android_bridge_denied.activity_injected", false);
    putPayload(payload, "inline_android_bridge_denied.application_injected", false);
    putPayload(payload, "inline_android_bridge_denied.jvm_bridge_attached", false);
    putPayload(payload, "inline_android_bridge_denied.jni_bridge_attached", false);
    putPayload(payload, "inline_android_bridge_denied.android_api_called", false);
    putPayload(payload, "inline_android_bridge_denied.binder_called", false);
    putPayload(payload, "inline_android_bridge_denied.looper_used", false);
    putPayload(payload, "inline_android_bridge_denied.ui_thread_dispatch_attempted", false);
    putPayload(payload, "inline_android_bridge_denied.denied_error_code", "");
    putPayload(payload, "inline_android_bridge_denied.denied_error_message", "");
    putPayload(payload, "inline_android_bridge_denied.final_state", "");
    putPayload(payload, "inline_android_bridge_denied.sequence_monotonic", false);
    putPayload(payload, "inline_android_bridge_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_android_bridge_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_android_bridge_denied.node_version", "");
    putPayload(payload, "inline_android_bridge_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_android_bridge_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_android_bridge_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_android_bridge_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_android_bridge_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_android_bridge_denied.ms", static_cast<long long>(0));
}

void putInlineAutoJsApiDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_autojs_api_denied.status", "skipped");
    putPayload(payload, "inline_autojs_api_denied.detail", detail);
    putPayload(payload, "inline_autojs_api_denied.result_text", "");
    putPayload(payload, "inline_autojs_api_denied.execution_mode", "");
    putPayload(payload, "inline_autojs_api_denied.source_kind", "");
    putPayload(payload, "inline_autojs_api_denied.user_source_used", false);
    putPayload(payload, "inline_autojs_api_denied.user_file_read", false);
    putPayload(payload, "inline_autojs_api_denied.autojs_api_allowed", false);
    putPayload(payload, "inline_autojs_api_denied.global_auto_injected", false);
    putPayload(payload, "inline_autojs_api_denied.global_files_injected", false);
    putPayload(payload, "inline_autojs_api_denied.global_device_injected", false);
    putPayload(payload, "inline_autojs_api_denied.global_app_injected", false);
    putPayload(payload, "inline_autojs_api_denied.global_console_injected", false);
    putPayload(payload, "inline_autojs_api_denied.global_images_injected", false);
    putPayload(payload, "inline_autojs_api_denied.rhino_bridge_used", false);
    putPayload(payload, "inline_autojs_api_denied.android_bridge_used", false);
    putPayload(payload, "inline_autojs_api_denied.host_object_injected", false);
    putPayload(payload, "inline_autojs_api_denied.denied_error_code", "");
    putPayload(payload, "inline_autojs_api_denied.denied_error_message", "");
    putPayload(payload, "inline_autojs_api_denied.final_state", "");
    putPayload(payload, "inline_autojs_api_denied.sequence_monotonic", false);
    putPayload(payload, "inline_autojs_api_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_autojs_api_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_autojs_api_denied.node_version", "");
    putPayload(payload, "inline_autojs_api_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_autojs_api_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_autojs_api_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_autojs_api_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_autojs_api_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_autojs_api_denied.ms", static_cast<long long>(0));
}

void putInlineConsoleBridgeDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_console_bridge_denied.status", "skipped");
    putPayload(payload, "inline_console_bridge_denied.detail", detail);
    putPayload(payload, "inline_console_bridge_denied.result_text", "");
    putPayload(payload, "inline_console_bridge_denied.execution_mode", "");
    putPayload(payload, "inline_console_bridge_denied.source_kind", "");
    putPayload(payload, "inline_console_bridge_denied.user_source_used", false);
    putPayload(payload, "inline_console_bridge_denied.user_file_read", false);
    putPayload(payload, "inline_console_bridge_denied.console_bridge_allowed", false);
    putPayload(payload, "inline_console_bridge_denied.autojs_console_connected", false);
    putPayload(payload, "inline_console_bridge_denied.host_console_sink_attached", false);
    putPayload(payload, "inline_console_bridge_denied.log_event_forwarded", false);
    putPayload(payload, "inline_console_bridge_denied.stdout_forwarded_to_autojs", false);
    putPayload(payload, "inline_console_bridge_denied.stderr_forwarded_to_autojs", false);
    putPayload(payload, "inline_console_bridge_denied.console_global_replaced", false);
    putPayload(payload, "inline_console_bridge_denied.console_proxy_installed", false);
    putPayload(payload, "inline_console_bridge_denied.json_socket_used", false);
    putPayload(payload, "inline_console_bridge_denied.binder_used", false);
    putPayload(payload, "inline_console_bridge_denied.denied_error_code", "");
    putPayload(payload, "inline_console_bridge_denied.denied_error_message", "");
    putPayload(payload, "inline_console_bridge_denied.final_state", "");
    putPayload(payload, "inline_console_bridge_denied.sequence_monotonic", false);
    putPayload(payload, "inline_console_bridge_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_console_bridge_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_console_bridge_denied.node_version", "");
    putPayload(payload, "inline_console_bridge_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_console_bridge_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_console_bridge_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_console_bridge_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_console_bridge_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_console_bridge_denied.ms", static_cast<long long>(0));
}

void putInlineJsonSocketDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_json_socket_denied.status", "skipped");
    putPayload(payload, "inline_json_socket_denied.detail", detail);
    putPayload(payload, "inline_json_socket_denied.result_text", "");
    putPayload(payload, "inline_json_socket_denied.execution_mode", "");
    putPayload(payload, "inline_json_socket_denied.source_kind", "");
    putPayload(payload, "inline_json_socket_denied.user_source_used", false);
    putPayload(payload, "inline_json_socket_denied.user_file_read", false);
    putPayload(payload, "inline_json_socket_denied.json_socket_allowed", false);
    putPayload(payload, "inline_json_socket_denied.socket_created", false);
    putPayload(payload, "inline_json_socket_denied.host_connected", false);
    putPayload(payload, "inline_json_socket_denied.message_sent", false);
    putPayload(payload, "inline_json_socket_denied.message_received", false);
    putPayload(payload, "inline_json_socket_denied.debug_protocol_enabled", false);
    putPayload(payload, "inline_json_socket_denied.console_protocol_enabled", false);
    putPayload(payload, "inline_json_socket_denied.remote_command_enabled", false);
    putPayload(payload, "inline_json_socket_denied.network_used", false);
    putPayload(payload, "inline_json_socket_denied.binder_used", false);
    putPayload(payload, "inline_json_socket_denied.denied_error_code", "");
    putPayload(payload, "inline_json_socket_denied.denied_error_message", "");
    putPayload(payload, "inline_json_socket_denied.final_state", "");
    putPayload(payload, "inline_json_socket_denied.sequence_monotonic", false);
    putPayload(payload, "inline_json_socket_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_json_socket_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_json_socket_denied.node_version", "");
    putPayload(payload, "inline_json_socket_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_json_socket_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_json_socket_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_json_socket_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_json_socket_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_json_socket_denied.ms", static_cast<long long>(0));
}

void putInlineBinderDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_binder_denied.status", "skipped");
    putPayload(payload, "inline_binder_denied.detail", detail);
    putPayload(payload, "inline_binder_denied.result_text", "");
    putPayload(payload, "inline_binder_denied.execution_mode", "");
    putPayload(payload, "inline_binder_denied.source_kind", "");
    putPayload(payload, "inline_binder_denied.user_source_used", false);
    putPayload(payload, "inline_binder_denied.user_file_read", false);
    putPayload(payload, "inline_binder_denied.binder_bridge_allowed", false);
    putPayload(payload, "inline_binder_denied.binder_used", false);
    putPayload(payload, "inline_binder_denied.aidl_interface_bound", false);
    putPayload(payload, "inline_binder_denied.service_bound", false);
    putPayload(payload, "inline_binder_denied.ipc_transaction_attempted", false);
    putPayload(payload, "inline_binder_denied.main_process_connected", false);
    putPayload(payload, "inline_binder_denied.result_receiver_used", false);
    putPayload(payload, "inline_binder_denied.parcel_created", false);
    putPayload(payload, "inline_binder_denied.remote_exception_observed", false);
    putPayload(payload, "inline_binder_denied.denied_error_code", "");
    putPayload(payload, "inline_binder_denied.denied_error_message", "");
    putPayload(payload, "inline_binder_denied.final_state", "");
    putPayload(payload, "inline_binder_denied.sequence_monotonic", false);
    putPayload(payload, "inline_binder_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_binder_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_binder_denied.node_version", "");
    putPayload(payload, "inline_binder_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_binder_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_binder_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_binder_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_binder_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_binder_denied.ms", static_cast<long long>(0));
}

void putInlineUiBridgeDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_ui_bridge_denied.status", "skipped");
    putPayload(payload, "inline_ui_bridge_denied.detail", detail);
    putPayload(payload, "inline_ui_bridge_denied.result_text", "");
    putPayload(payload, "inline_ui_bridge_denied.execution_mode", "");
    putPayload(payload, "inline_ui_bridge_denied.source_kind", "");
    putPayload(payload, "inline_ui_bridge_denied.user_source_used", false);
    putPayload(payload, "inline_ui_bridge_denied.user_file_read", false);
    putPayload(payload, "inline_ui_bridge_denied.ui_bridge_allowed", false);
    putPayload(payload, "inline_ui_bridge_denied.ui_global_injected", false);
    putPayload(payload, "inline_ui_bridge_denied.activity_required", false);
    putPayload(payload, "inline_ui_bridge_denied.activity_accessed", false);
    putPayload(payload, "inline_ui_bridge_denied.view_created", false);
    putPayload(payload, "inline_ui_bridge_denied.layout_inflated", false);
    putPayload(payload, "inline_ui_bridge_denied.ui_thread_dispatch_attempted", false);
    putPayload(payload, "inline_ui_bridge_denied.looper_used", false);
    putPayload(payload, "inline_ui_bridge_denied.android_bridge_used", false);
    putPayload(payload, "inline_ui_bridge_denied.host_object_injected", false);
    putPayload(payload, "inline_ui_bridge_denied.binder_used", false);
    putPayload(payload, "inline_ui_bridge_denied.denied_error_code", "");
    putPayload(payload, "inline_ui_bridge_denied.denied_error_message", "");
    putPayload(payload, "inline_ui_bridge_denied.final_state", "");
    putPayload(payload, "inline_ui_bridge_denied.sequence_monotonic", false);
    putPayload(payload, "inline_ui_bridge_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_ui_bridge_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_ui_bridge_denied.node_version", "");
    putPayload(payload, "inline_ui_bridge_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_ui_bridge_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_ui_bridge_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_ui_bridge_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_ui_bridge_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_ui_bridge_denied.ms", static_cast<long long>(0));
}

void putInlineAccessibilityDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_accessibility_denied.status", "skipped");
    putPayload(payload, "inline_accessibility_denied.detail", detail);
    putPayload(payload, "inline_accessibility_denied.result_text", "");
    putPayload(payload, "inline_accessibility_denied.execution_mode", "");
    putPayload(payload, "inline_accessibility_denied.source_kind", "");
    putPayload(payload, "inline_accessibility_denied.user_source_used", false);
    putPayload(payload, "inline_accessibility_denied.user_file_read", false);
    putPayload(payload, "inline_accessibility_denied.accessibility_bridge_allowed", false);
    putPayload(payload, "inline_accessibility_denied.accessibility_service_accessed", false);
    putPayload(payload, "inline_accessibility_denied.accessibility_state_queried", false);
    putPayload(payload, "inline_accessibility_denied.node_query_attempted", false);
    putPayload(payload, "inline_accessibility_denied.selector_engine_used", false);
    putPayload(payload, "inline_accessibility_denied.gesture_dispatch_attempted", false);
    putPayload(payload, "inline_accessibility_denied.ui_automation_used", false);
    putPayload(payload, "inline_accessibility_denied.android_api_called", false);
    putPayload(payload, "inline_accessibility_denied.binder_used", false);
    putPayload(payload, "inline_accessibility_denied.host_object_injected", false);
    putPayload(payload, "inline_accessibility_denied.denied_error_code", "");
    putPayload(payload, "inline_accessibility_denied.denied_error_message", "");
    putPayload(payload, "inline_accessibility_denied.final_state", "");
    putPayload(payload, "inline_accessibility_denied.sequence_monotonic", false);
    putPayload(payload, "inline_accessibility_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_accessibility_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_accessibility_denied.node_version", "");
    putPayload(payload, "inline_accessibility_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_accessibility_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_accessibility_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_accessibility_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_accessibility_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_accessibility_denied.ms", static_cast<long long>(0));
}

void putInlineImagesDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_images_denied.status", "skipped");
    putPayload(payload, "inline_images_denied.detail", detail);
    putPayload(payload, "inline_images_denied.result_text", "");
    putPayload(payload, "inline_images_denied.execution_mode", "");
    putPayload(payload, "inline_images_denied.source_kind", "");
    putPayload(payload, "inline_images_denied.user_source_used", false);
    putPayload(payload, "inline_images_denied.user_file_read", false);
    putPayload(payload, "inline_images_denied.images_bridge_allowed", false);
    putPayload(payload, "inline_images_denied.images_global_injected", false);
    putPayload(payload, "inline_images_denied.bitmap_created", false);
    putPayload(payload, "inline_images_denied.image_wrapper_created", false);
    putPayload(payload, "inline_images_denied.screen_capture_attempted", false);
    putPayload(payload, "inline_images_denied.image_io_attempted", false);
    putPayload(payload, "inline_images_denied.opencv_used", false);
    putPayload(payload, "inline_images_denied.mlkit_used", false);
    putPayload(payload, "inline_images_denied.android_api_called", false);
    putPayload(payload, "inline_images_denied.native_image_library_loaded", false);
    putPayload(payload, "inline_images_denied.host_object_injected", false);
    putPayload(payload, "inline_images_denied.denied_error_code", "");
    putPayload(payload, "inline_images_denied.denied_error_message", "");
    putPayload(payload, "inline_images_denied.final_state", "");
    putPayload(payload, "inline_images_denied.sequence_monotonic", false);
    putPayload(payload, "inline_images_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_images_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_images_denied.node_version", "");
    putPayload(payload, "inline_images_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_images_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_images_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_images_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_images_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_images_denied.ms", static_cast<long long>(0));
}
void putInlineDialogsDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_dialogs_denied.status", "skipped");
    putPayload(payload, "inline_dialogs_denied.detail", detail);
    putPayload(payload, "inline_dialogs_denied.result_text", "");
    putPayload(payload, "inline_dialogs_denied.execution_mode", "");
    putPayload(payload, "inline_dialogs_denied.source_kind", "");
    putPayload(payload, "inline_dialogs_denied.user_source_used", false);
    putPayload(payload, "inline_dialogs_denied.user_file_read", false);
    putPayload(payload, "inline_dialogs_denied.dialogs_bridge_allowed", false);
    putPayload(payload, "inline_dialogs_denied.dialogs_global_injected", false);
    putPayload(payload, "inline_dialogs_denied.alert_attempted", false);
    putPayload(payload, "inline_dialogs_denied.confirm_attempted", false);
    putPayload(payload, "inline_dialogs_denied.prompt_attempted", false);
    putPayload(payload, "inline_dialogs_denied.dialog_created", false);
    putPayload(payload, "inline_dialogs_denied.activity_required", false);
    putPayload(payload, "inline_dialogs_denied.activity_accessed", false);
    putPayload(payload, "inline_dialogs_denied.ui_thread_dispatch_attempted", false);
    putPayload(payload, "inline_dialogs_denied.android_bridge_used", false);
    putPayload(payload, "inline_dialogs_denied.host_object_injected", false);
    putPayload(payload, "inline_dialogs_denied.binder_used", false);
    putPayload(payload, "inline_dialogs_denied.policy", "");
    putPayload(payload, "inline_dialogs_denied.denied_error_code", "");
    putPayload(payload, "inline_dialogs_denied.denied_error_message", "");
    putPayload(payload, "inline_dialogs_denied.final_state", "");
    putPayload(payload, "inline_dialogs_denied.sequence_monotonic", false);
    putPayload(payload, "inline_dialogs_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_dialogs_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_dialogs_denied.node_version", "");
    putPayload(payload, "inline_dialogs_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_dialogs_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_dialogs_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_dialogs_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_dialogs_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_dialogs_denied.ms", static_cast<long long>(0));
}

void putInlineSensorsDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_sensors_denied.status", "skipped");
    putPayload(payload, "inline_sensors_denied.detail", detail);
    putPayload(payload, "inline_sensors_denied.result_text", "");
    putPayload(payload, "inline_sensors_denied.execution_mode", "");
    putPayload(payload, "inline_sensors_denied.source_kind", "");
    putPayload(payload, "inline_sensors_denied.user_source_used", false);
    putPayload(payload, "inline_sensors_denied.user_file_read", false);
    putPayload(payload, "inline_sensors_denied.sensors_bridge_allowed", false);
    putPayload(payload, "inline_sensors_denied.sensors_global_injected", false);
    putPayload(payload, "inline_sensors_denied.sensor_manager_accessed", false);
    putPayload(payload, "inline_sensors_denied.sensor_listener_registered", false);
    putPayload(payload, "inline_sensors_denied.accelerometer_accessed", false);
    putPayload(payload, "inline_sensors_denied.gyroscope_accessed", false);
    putPayload(payload, "inline_sensors_denied.orientation_accessed", false);
    putPayload(payload, "inline_sensors_denied.location_accessed", false);
    putPayload(payload, "inline_sensors_denied.android_api_called", false);
    putPayload(payload, "inline_sensors_denied.permission_requested", false);
    putPayload(payload, "inline_sensors_denied.host_object_injected", false);
    putPayload(payload, "inline_sensors_denied.binder_used", false);
    putPayload(payload, "inline_sensors_denied.policy", "");
    putPayload(payload, "inline_sensors_denied.denied_error_code", "");
    putPayload(payload, "inline_sensors_denied.denied_error_message", "");
    putPayload(payload, "inline_sensors_denied.final_state", "");
    putPayload(payload, "inline_sensors_denied.sequence_monotonic", false);
    putPayload(payload, "inline_sensors_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_sensors_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_sensors_denied.node_version", "");
    putPayload(payload, "inline_sensors_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_sensors_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_sensors_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_sensors_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_sensors_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_sensors_denied.ms", static_cast<long long>(0));
}

void putInlineMediaCameraDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_media_camera_denied.status", "skipped");
    putPayload(payload, "inline_media_camera_denied.detail", detail);
    putPayload(payload, "inline_media_camera_denied.result_text", "");
    putPayload(payload, "inline_media_camera_denied.execution_mode", "");
    putPayload(payload, "inline_media_camera_denied.source_kind", "");
    putPayload(payload, "inline_media_camera_denied.user_source_used", false);
    putPayload(payload, "inline_media_camera_denied.user_file_read", false);
    putPayload(payload, "inline_media_camera_denied.media_camera_bridge_allowed", false);
    putPayload(payload, "inline_media_camera_denied.media_global_injected", false);
    putPayload(payload, "inline_media_camera_denied.camera_global_injected", false);
    putPayload(payload, "inline_media_camera_denied.camera_open_attempted", false);
    putPayload(payload, "inline_media_camera_denied.camera_device_created", false);
    putPayload(payload, "inline_media_camera_denied.media_recorder_created", false);
    putPayload(payload, "inline_media_camera_denied.audio_record_created", false);
    putPayload(payload, "inline_media_camera_denied.microphone_accessed", false);
    putPayload(payload, "inline_media_camera_denied.camera_permission_requested", false);
    putPayload(payload, "inline_media_camera_denied.record_audio_permission_requested", false);
    putPayload(payload, "inline_media_camera_denied.android_api_called", false);
    putPayload(payload, "inline_media_camera_denied.native_media_library_loaded", false);
    putPayload(payload, "inline_media_camera_denied.host_object_injected", false);
    putPayload(payload, "inline_media_camera_denied.binder_used", false);
    putPayload(payload, "inline_media_camera_denied.policy", "");
    putPayload(payload, "inline_media_camera_denied.denied_error_code", "");
    putPayload(payload, "inline_media_camera_denied.denied_error_message", "");
    putPayload(payload, "inline_media_camera_denied.final_state", "");
    putPayload(payload, "inline_media_camera_denied.sequence_monotonic", false);
    putPayload(payload, "inline_media_camera_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_media_camera_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_media_camera_denied.node_version", "");
    putPayload(payload, "inline_media_camera_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_media_camera_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_media_camera_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_media_camera_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_media_camera_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_media_camera_denied.ms", static_cast<long long>(0));
}

void putInlineStorageDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_storage_denied.status", "skipped");
    putPayload(payload, "inline_storage_denied.detail", detail);
    putPayload(payload, "inline_storage_denied.result_text", "");
    putPayload(payload, "inline_storage_denied.execution_mode", "");
    putPayload(payload, "inline_storage_denied.source_kind", "");
    putPayload(payload, "inline_storage_denied.user_source_used", false);
    putPayload(payload, "inline_storage_denied.user_file_read", false);
    putPayload(payload, "inline_storage_denied.storage_bridge_allowed", false);
    putPayload(payload, "inline_storage_denied.storage_global_injected", false);
    putPayload(payload, "inline_storage_denied.shared_preferences_accessed", false);
    putPayload(payload, "inline_storage_denied.sqlite_accessed", false);
    putPayload(payload, "inline_storage_denied.file_storage_accessed", false);
    putPayload(payload, "inline_storage_denied.kv_storage_accessed", false);
    putPayload(payload, "inline_storage_denied.database_opened", false);
    putPayload(payload, "inline_storage_denied.transaction_started", false);
    putPayload(payload, "inline_storage_denied.android_context_accessed", false);
    putPayload(payload, "inline_storage_denied.android_api_called", false);
    putPayload(payload, "inline_storage_denied.host_object_injected", false);
    putPayload(payload, "inline_storage_denied.binder_used", false);
    putPayload(payload, "inline_storage_denied.policy", "");
    putPayload(payload, "inline_storage_denied.denied_error_code", "");
    putPayload(payload, "inline_storage_denied.denied_error_message", "");
    putPayload(payload, "inline_storage_denied.final_state", "");
    putPayload(payload, "inline_storage_denied.sequence_monotonic", false);
    putPayload(payload, "inline_storage_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_storage_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_storage_denied.node_version", "");
    putPayload(payload, "inline_storage_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_storage_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_storage_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_storage_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_storage_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_storage_denied.ms", static_cast<long long>(0));
}

void putInlineShellDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_shell_denied.status", "skipped");
    putPayload(payload, "inline_shell_denied.detail", detail);
    putPayload(payload, "inline_shell_denied.result_text", "");
    putPayload(payload, "inline_shell_denied.execution_mode", "");
    putPayload(payload, "inline_shell_denied.source_kind", "");
    putPayload(payload, "inline_shell_denied.user_source_used", false);
    putPayload(payload, "inline_shell_denied.user_file_read", false);
    putPayload(payload, "inline_shell_denied.shell_bridge_allowed", false);
    putPayload(payload, "inline_shell_denied.shell_global_injected", false);
    putPayload(payload, "inline_shell_denied.shell_command_attempted", false);
    putPayload(payload, "inline_shell_denied.root_command_attempted", false);
    putPayload(payload, "inline_shell_denied.su_requested", false);
    putPayload(payload, "inline_shell_denied.process_spawn_attempted", false);
    putPayload(payload, "inline_shell_denied.runtime_exec_attempted", false);
    putPayload(payload, "inline_shell_denied.pty_created", false);
    putPayload(payload, "inline_shell_denied.stdin_written", false);
    putPayload(payload, "inline_shell_denied.stdout_read", false);
    putPayload(payload, "inline_shell_denied.stderr_read", false);
    putPayload(payload, "inline_shell_denied.android_api_called", false);
    putPayload(payload, "inline_shell_denied.host_object_injected", false);
    putPayload(payload, "inline_shell_denied.binder_used", false);
    putPayload(payload, "inline_shell_denied.policy", "");
    putPayload(payload, "inline_shell_denied.denied_error_code", "");
    putPayload(payload, "inline_shell_denied.denied_error_message", "");
    putPayload(payload, "inline_shell_denied.final_state", "");
    putPayload(payload, "inline_shell_denied.sequence_monotonic", false);
    putPayload(payload, "inline_shell_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_shell_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_shell_denied.node_version", "");
    putPayload(payload, "inline_shell_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_shell_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_shell_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_shell_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_shell_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_shell_denied.ms", static_cast<long long>(0));
}

void putInlineNotificationDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_notification_denied.status", "skipped");
    putPayload(payload, "inline_notification_denied.detail", detail);
    putPayload(payload, "inline_notification_denied.result_text", "");
    putPayload(payload, "inline_notification_denied.execution_mode", "");
    putPayload(payload, "inline_notification_denied.source_kind", "");
    putPayload(payload, "inline_notification_denied.user_source_used", false);
    putPayload(payload, "inline_notification_denied.user_file_read", false);
    putPayload(payload, "inline_notification_denied.notification_bridge_allowed", false);
    putPayload(payload, "inline_notification_denied.notification_global_injected", false);
    putPayload(payload, "inline_notification_denied.notification_manager_accessed", false);
    putPayload(payload, "inline_notification_denied.notification_channel_created", false);
    putPayload(payload, "inline_notification_denied.notification_built", false);
    putPayload(payload, "inline_notification_denied.notification_posted", false);
    putPayload(payload, "inline_notification_denied.notification_cancelled", false);
    putPayload(payload, "inline_notification_denied.pending_intent_created", false);
    putPayload(payload, "inline_notification_denied.android_context_accessed", false);
    putPayload(payload, "inline_notification_denied.permission_requested", false);
    putPayload(payload, "inline_notification_denied.android_api_called", false);
    putPayload(payload, "inline_notification_denied.host_object_injected", false);
    putPayload(payload, "inline_notification_denied.binder_used", false);
    putPayload(payload, "inline_notification_denied.policy", "");
    putPayload(payload, "inline_notification_denied.denied_error_code", "");
    putPayload(payload, "inline_notification_denied.denied_error_message", "");
    putPayload(payload, "inline_notification_denied.final_state", "");
    putPayload(payload, "inline_notification_denied.sequence_monotonic", false);
    putPayload(payload, "inline_notification_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_notification_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_notification_denied.node_version", "");
    putPayload(payload, "inline_notification_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_notification_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_notification_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_notification_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_notification_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_notification_denied.ms", static_cast<long long>(0));
}

void putInlineClipboardDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_clipboard_denied.status", "skipped");
    putPayload(payload, "inline_clipboard_denied.detail", detail);
    putPayload(payload, "inline_clipboard_denied.result_text", "");
    putPayload(payload, "inline_clipboard_denied.execution_mode", "");
    putPayload(payload, "inline_clipboard_denied.source_kind", "");
    putPayload(payload, "inline_clipboard_denied.user_source_used", false);
    putPayload(payload, "inline_clipboard_denied.user_file_read", false);
    putPayload(payload, "inline_clipboard_denied.clipboard_bridge_allowed", false);
    putPayload(payload, "inline_clipboard_denied.clipboard_global_injected", false);
    putPayload(payload, "inline_clipboard_denied.clipboard_manager_accessed", false);
    putPayload(payload, "inline_clipboard_denied.clip_read_attempted", false);
    putPayload(payload, "inline_clipboard_denied.clip_write_attempted", false);
    putPayload(payload, "inline_clipboard_denied.primary_clip_read", false);
    putPayload(payload, "inline_clipboard_denied.primary_clip_set", false);
    putPayload(payload, "inline_clipboard_denied.android_context_accessed", false);
    putPayload(payload, "inline_clipboard_denied.android_api_called", false);
    putPayload(payload, "inline_clipboard_denied.permission_requested", false);
    putPayload(payload, "inline_clipboard_denied.host_object_injected", false);
    putPayload(payload, "inline_clipboard_denied.binder_used", false);
    putPayload(payload, "inline_clipboard_denied.policy", "");
    putPayload(payload, "inline_clipboard_denied.denied_error_code", "");
    putPayload(payload, "inline_clipboard_denied.denied_error_message", "");
    putPayload(payload, "inline_clipboard_denied.final_state", "");
    putPayload(payload, "inline_clipboard_denied.sequence_monotonic", false);
    putPayload(payload, "inline_clipboard_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_clipboard_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_clipboard_denied.node_version", "");
    putPayload(payload, "inline_clipboard_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_clipboard_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_clipboard_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_clipboard_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_clipboard_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_clipboard_denied.ms", static_cast<long long>(0));
}

void putInlineDeviceInfoDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_device_info_denied.status", "skipped");
    putPayload(payload, "inline_device_info_denied.detail", detail);
    putPayload(payload, "inline_device_info_denied.result_text", "");
    putPayload(payload, "inline_device_info_denied.execution_mode", "");
    putPayload(payload, "inline_device_info_denied.source_kind", "");
    putPayload(payload, "inline_device_info_denied.user_source_used", false);
    putPayload(payload, "inline_device_info_denied.user_file_read", false);
    putPayload(payload, "inline_device_info_denied.device_info_bridge_allowed", false);
    putPayload(payload, "inline_device_info_denied.device_global_injected", false);
    putPayload(payload, "inline_device_info_denied.system_service_accessed", false);
    putPayload(payload, "inline_device_info_denied.build_info_accessed", false);
    putPayload(payload, "inline_device_info_denied.display_metrics_accessed", false);
    putPayload(payload, "inline_device_info_denied.battery_state_queried", false);
    putPayload(payload, "inline_device_info_denied.network_state_queried", false);
    putPayload(payload, "inline_device_info_denied.telephony_info_queried", false);
    putPayload(payload, "inline_device_info_denied.settings_secure_accessed", false);
    putPayload(payload, "inline_device_info_denied.android_context_accessed", false);
    putPayload(payload, "inline_device_info_denied.android_api_called", false);
    putPayload(payload, "inline_device_info_denied.permission_requested", false);
    putPayload(payload, "inline_device_info_denied.host_object_injected", false);
    putPayload(payload, "inline_device_info_denied.binder_used", false);
    putPayload(payload, "inline_device_info_denied.policy", "");
    putPayload(payload, "inline_device_info_denied.denied_error_code", "");
    putPayload(payload, "inline_device_info_denied.denied_error_message", "");
    putPayload(payload, "inline_device_info_denied.final_state", "");
    putPayload(payload, "inline_device_info_denied.sequence_monotonic", false);
    putPayload(payload, "inline_device_info_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_device_info_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_device_info_denied.node_version", "");
    putPayload(payload, "inline_device_info_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_device_info_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_device_info_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_device_info_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_device_info_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_device_info_denied.ms", static_cast<long long>(0));
}

void putInlineVibrationDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_vibration_denied.status", "skipped");
    putPayload(payload, "inline_vibration_denied.detail", detail);
    putPayload(payload, "inline_vibration_denied.result_text", "");
    putPayload(payload, "inline_vibration_denied.execution_mode", "");
    putPayload(payload, "inline_vibration_denied.source_kind", "");
    putPayload(payload, "inline_vibration_denied.user_source_used", false);
    putPayload(payload, "inline_vibration_denied.user_file_read", false);
    putPayload(payload, "inline_vibration_denied.vibration_bridge_allowed", false);
    putPayload(payload, "inline_vibration_denied.vibrator_global_injected", false);
    putPayload(payload, "inline_vibration_denied.vibrator_service_accessed", false);
    putPayload(payload, "inline_vibration_denied.vibrate_attempted", false);
    putPayload(payload, "inline_vibration_denied.vibration_effect_created", false);
    putPayload(payload, "inline_vibration_denied.cancel_attempted", false);
    putPayload(payload, "inline_vibration_denied.android_context_accessed", false);
    putPayload(payload, "inline_vibration_denied.android_api_called", false);
    putPayload(payload, "inline_vibration_denied.permission_requested", false);
    putPayload(payload, "inline_vibration_denied.host_object_injected", false);
    putPayload(payload, "inline_vibration_denied.binder_used", false);
    putPayload(payload, "inline_vibration_denied.policy", "");
    putPayload(payload, "inline_vibration_denied.denied_error_code", "");
    putPayload(payload, "inline_vibration_denied.denied_error_message", "");
    putPayload(payload, "inline_vibration_denied.final_state", "");
    putPayload(payload, "inline_vibration_denied.sequence_monotonic", false);
    putPayload(payload, "inline_vibration_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_vibration_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_vibration_denied.node_version", "");
    putPayload(payload, "inline_vibration_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_vibration_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_vibration_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_vibration_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_vibration_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_vibration_denied.ms", static_cast<long long>(0));
}

void putInlineToastDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_toast_denied.status", "skipped");
    putPayload(payload, "inline_toast_denied.detail", detail);
    putPayload(payload, "inline_toast_denied.result_text", "");
    putPayload(payload, "inline_toast_denied.execution_mode", "");
    putPayload(payload, "inline_toast_denied.source_kind", "");
    putPayload(payload, "inline_toast_denied.user_source_used", false);
    putPayload(payload, "inline_toast_denied.user_file_read", false);
    putPayload(payload, "inline_toast_denied.toast_bridge_allowed", false);
    putPayload(payload, "inline_toast_denied.toast_global_injected", false);
    putPayload(payload, "inline_toast_denied.toast_attempted", false);
    putPayload(payload, "inline_toast_denied.toast_object_created", false);
    putPayload(payload, "inline_toast_denied.toast_shown", false);
    putPayload(payload, "inline_toast_denied.activity_required", false);
    putPayload(payload, "inline_toast_denied.android_context_accessed", false);
    putPayload(payload, "inline_toast_denied.ui_thread_dispatch_attempted", false);
    putPayload(payload, "inline_toast_denied.looper_used", false);
    putPayload(payload, "inline_toast_denied.android_api_called", false);
    putPayload(payload, "inline_toast_denied.host_object_injected", false);
    putPayload(payload, "inline_toast_denied.binder_used", false);
    putPayload(payload, "inline_toast_denied.policy", "");
    putPayload(payload, "inline_toast_denied.denied_error_code", "");
    putPayload(payload, "inline_toast_denied.denied_error_message", "");
    putPayload(payload, "inline_toast_denied.final_state", "");
    putPayload(payload, "inline_toast_denied.sequence_monotonic", false);
    putPayload(payload, "inline_toast_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_toast_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_toast_denied.node_version", "");
    putPayload(payload, "inline_toast_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_toast_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_toast_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_toast_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_toast_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_toast_denied.ms", static_cast<long long>(0));
}

void putInlineFloatyDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_floaty_denied.status", "skipped");
    putPayload(payload, "inline_floaty_denied.detail", detail);
    putPayload(payload, "inline_floaty_denied.result_text", "");
    putPayload(payload, "inline_floaty_denied.execution_mode", "");
    putPayload(payload, "inline_floaty_denied.source_kind", "");
    putPayload(payload, "inline_floaty_denied.user_source_used", false);
    putPayload(payload, "inline_floaty_denied.user_file_read", false);
    putPayload(payload, "inline_floaty_denied.floaty_bridge_allowed", false);
    putPayload(payload, "inline_floaty_denied.floaty_global_injected", false);
    putPayload(payload, "inline_floaty_denied.window_manager_accessed", false);
    putPayload(payload, "inline_floaty_denied.overlay_permission_checked", false);
    putPayload(payload, "inline_floaty_denied.overlay_permission_requested", false);
    putPayload(payload, "inline_floaty_denied.float_window_created", false);
    putPayload(payload, "inline_floaty_denied.view_created", false);
    putPayload(payload, "inline_floaty_denied.layout_inflated", false);
    putPayload(payload, "inline_floaty_denied.window_added", false);
    putPayload(payload, "inline_floaty_denied.window_removed", false);
    putPayload(payload, "inline_floaty_denied.ui_thread_dispatch_attempted", false);
    putPayload(payload, "inline_floaty_denied.android_context_accessed", false);
    putPayload(payload, "inline_floaty_denied.android_api_called", false);
    putPayload(payload, "inline_floaty_denied.host_object_injected", false);
    putPayload(payload, "inline_floaty_denied.binder_used", false);
    putPayload(payload, "inline_floaty_denied.policy", "");
    putPayload(payload, "inline_floaty_denied.denied_error_code", "");
    putPayload(payload, "inline_floaty_denied.denied_error_message", "");
    putPayload(payload, "inline_floaty_denied.final_state", "");
    putPayload(payload, "inline_floaty_denied.sequence_monotonic", false);
    putPayload(payload, "inline_floaty_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_floaty_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_floaty_denied.node_version", "");
    putPayload(payload, "inline_floaty_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_floaty_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_floaty_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_floaty_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_floaty_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_floaty_denied.ms", static_cast<long long>(0));
}

void putInlineEventsDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_events_denied.status", "skipped");
    putPayload(payload, "inline_events_denied.detail", detail);
    putPayload(payload, "inline_events_denied.result_text", "");
    putPayload(payload, "inline_events_denied.execution_mode", "");
    putPayload(payload, "inline_events_denied.source_kind", "");
    putPayload(payload, "inline_events_denied.user_source_used", false);
    putPayload(payload, "inline_events_denied.user_file_read", false);
    putPayload(payload, "inline_events_denied.events_bridge_allowed", false);
    putPayload(payload, "inline_events_denied.events_global_injected", false);
    putPayload(payload, "inline_events_denied.event_emitter_injected", false);
    putPayload(payload, "inline_events_denied.listener_registered", false);
    putPayload(payload, "inline_events_denied.broadcast_receiver_registered", false);
    putPayload(payload, "inline_events_denied.key_observer_registered", false);
    putPayload(payload, "inline_events_denied.touch_observer_registered", false);
    putPayload(payload, "inline_events_denied.notification_listener_accessed", false);
    putPayload(payload, "inline_events_denied.accessibility_event_observed", false);
    putPayload(payload, "inline_events_denied.sensor_listener_registered", false);
    putPayload(payload, "inline_events_denied.android_api_called", false);
    putPayload(payload, "inline_events_denied.host_object_injected", false);
    putPayload(payload, "inline_events_denied.binder_used", false);
    putPayload(payload, "inline_events_denied.policy", "");
    putPayload(payload, "inline_events_denied.denied_error_code", "");
    putPayload(payload, "inline_events_denied.denied_error_message", "");
    putPayload(payload, "inline_events_denied.final_state", "");
    putPayload(payload, "inline_events_denied.sequence_monotonic", false);
    putPayload(payload, "inline_events_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_events_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_events_denied.node_version", "");
    putPayload(payload, "inline_events_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_events_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_events_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_events_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_events_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_events_denied.ms", static_cast<long long>(0));
}

void putInlineThreadsDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_threads_denied.status", "skipped");
    putPayload(payload, "inline_threads_denied.detail", detail);
    putPayload(payload, "inline_threads_denied.result_text", "");
    putPayload(payload, "inline_threads_denied.execution_mode", "");
    putPayload(payload, "inline_threads_denied.source_kind", "");
    putPayload(payload, "inline_threads_denied.user_source_used", false);
    putPayload(payload, "inline_threads_denied.user_file_read", false);
    putPayload(payload, "inline_threads_denied.threads_bridge_allowed", false);
    putPayload(payload, "inline_threads_denied.threads_global_injected", false);
    putPayload(payload, "inline_threads_denied.thread_started", false);
    putPayload(payload, "inline_threads_denied.thread_join_attempted", false);
    putPayload(payload, "inline_threads_denied.thread_interrupted", false);
    putPayload(payload, "inline_threads_denied.thread_pool_created", false);
    putPayload(payload, "inline_threads_denied.handler_thread_created", false);
    putPayload(payload, "inline_threads_denied.java_thread_created", false);
    putPayload(payload, "inline_threads_denied.android_looper_used", false);
    putPayload(payload, "inline_threads_denied.android_handler_created", false);
    putPayload(payload, "inline_threads_denied.host_object_injected", false);
    putPayload(payload, "inline_threads_denied.android_api_called", false);
    putPayload(payload, "inline_threads_denied.binder_used", false);
    putPayload(payload, "inline_threads_denied.policy", "");
    putPayload(payload, "inline_threads_denied.denied_error_code", "");
    putPayload(payload, "inline_threads_denied.denied_error_message", "");
    putPayload(payload, "inline_threads_denied.final_state", "");
    putPayload(payload, "inline_threads_denied.sequence_monotonic", false);
    putPayload(payload, "inline_threads_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_threads_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_threads_denied.node_version", "");
    putPayload(payload, "inline_threads_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_threads_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_threads_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_threads_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_threads_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_threads_denied.ms", static_cast<long long>(0));
}

void putInlineTimersDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_timers_denied.status", "skipped");
    putPayload(payload, "inline_timers_denied.detail", detail);
    putPayload(payload, "inline_timers_denied.result_text", "");
    putPayload(payload, "inline_timers_denied.execution_mode", "");
    putPayload(payload, "inline_timers_denied.source_kind", "");
    putPayload(payload, "inline_timers_denied.user_source_used", false);
    putPayload(payload, "inline_timers_denied.user_file_read", false);
    putPayload(payload, "inline_timers_denied.timers_bridge_allowed", false);
    putPayload(payload, "inline_timers_denied.timers_global_injected", false);
    putPayload(payload, "inline_timers_denied.set_timeout_available", false);
    putPayload(payload, "inline_timers_denied.clear_timeout_available", false);
    putPayload(payload, "inline_timers_denied.set_interval_available", false);
    putPayload(payload, "inline_timers_denied.clear_interval_available", false);
    putPayload(payload, "inline_timers_denied.timeout_scheduled", false);
    putPayload(payload, "inline_timers_denied.interval_scheduled", false);
    putPayload(payload, "inline_timers_denied.timer_callback_invoked", false);
    putPayload(payload, "inline_timers_denied.android_handler_used", false);
    putPayload(payload, "inline_timers_denied.libuv_timer_created", false);
    putPayload(payload, "inline_timers_denied.host_object_injected", false);
    putPayload(payload, "inline_timers_denied.android_api_called", false);
    putPayload(payload, "inline_timers_denied.binder_used", false);
    putPayload(payload, "inline_timers_denied.policy", "");
    putPayload(payload, "inline_timers_denied.denied_error_code", "");
    putPayload(payload, "inline_timers_denied.denied_error_message", "");
    putPayload(payload, "inline_timers_denied.final_state", "");
    putPayload(payload, "inline_timers_denied.sequence_monotonic", false);
    putPayload(payload, "inline_timers_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_timers_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_timers_denied.node_version", "");
    putPayload(payload, "inline_timers_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_timers_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_timers_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_timers_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_timers_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_timers_denied.ms", static_cast<long long>(0));
}

void putInlineHttpDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_http_denied.status", "skipped");
    putPayload(payload, "inline_http_denied.detail", detail);
    putPayload(payload, "inline_http_denied.result_text", "");
    putPayload(payload, "inline_http_denied.execution_mode", "");
    putPayload(payload, "inline_http_denied.source_kind", "");
    putPayload(payload, "inline_http_denied.user_source_used", false);
    putPayload(payload, "inline_http_denied.user_file_read", false);
    putPayload(payload, "inline_http_denied.http_bridge_allowed", false);
    putPayload(payload, "inline_http_denied.http_global_injected", false);
    putPayload(payload, "inline_http_denied.http_request_attempted", false);
    putPayload(payload, "inline_http_denied.http_get_attempted", false);
    putPayload(payload, "inline_http_denied.http_post_attempted", false);
    putPayload(payload, "inline_http_denied.http_client_created", false);
    putPayload(payload, "inline_http_denied.connection_opened", false);
    putPayload(payload, "inline_http_denied.socket_created", false);
    putPayload(payload, "inline_http_denied.dns_lookup_attempted", false);
    putPayload(payload, "inline_http_denied.request_body_written", false);
    putPayload(payload, "inline_http_denied.response_read", false);
    putPayload(payload, "inline_http_denied.network_permission_requested", false);
    putPayload(payload, "inline_http_denied.android_api_called", false);
    putPayload(payload, "inline_http_denied.host_object_injected", false);
    putPayload(payload, "inline_http_denied.binder_used", false);
    putPayload(payload, "inline_http_denied.policy", "");
    putPayload(payload, "inline_http_denied.denied_error_code", "");
    putPayload(payload, "inline_http_denied.denied_error_message", "");
    putPayload(payload, "inline_http_denied.final_state", "");
    putPayload(payload, "inline_http_denied.sequence_monotonic", false);
    putPayload(payload, "inline_http_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_http_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_http_denied.node_version", "");
    putPayload(payload, "inline_http_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_http_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_http_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_http_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_http_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_http_denied.ms", static_cast<long long>(0));
}

void putInlineCryptoDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_crypto_denied.status", "skipped");
    putPayload(payload, "inline_crypto_denied.detail", detail);
    putPayload(payload, "inline_crypto_denied.result_text", "");
    putPayload(payload, "inline_crypto_denied.execution_mode", "");
    putPayload(payload, "inline_crypto_denied.source_kind", "");
    putPayload(payload, "inline_crypto_denied.user_source_used", false);
    putPayload(payload, "inline_crypto_denied.user_file_read", false);
    putPayload(payload, "inline_crypto_denied.crypto_bridge_allowed", false);
    putPayload(payload, "inline_crypto_denied.crypto_global_injected", false);
    putPayload(payload, "inline_crypto_denied.java_crypto_accessed", false);
    putPayload(payload, "inline_crypto_denied.keystore_accessed", false);
    putPayload(payload, "inline_crypto_denied.message_digest_created", false);
    putPayload(payload, "inline_crypto_denied.cipher_created", false);
    putPayload(payload, "inline_crypto_denied.mac_created", false);
    putPayload(payload, "inline_crypto_denied.key_generated", false);
    putPayload(payload, "inline_crypto_denied.secure_random_created", false);
    putPayload(payload, "inline_crypto_denied.native_crypto_library_loaded", false);
    putPayload(payload, "inline_crypto_denied.android_api_called", false);
    putPayload(payload, "inline_crypto_denied.host_object_injected", false);
    putPayload(payload, "inline_crypto_denied.binder_used", false);
    putPayload(payload, "inline_crypto_denied.policy", "");
    putPayload(payload, "inline_crypto_denied.denied_error_code", "");
    putPayload(payload, "inline_crypto_denied.denied_error_message", "");
    putPayload(payload, "inline_crypto_denied.final_state", "");
    putPayload(payload, "inline_crypto_denied.sequence_monotonic", false);
    putPayload(payload, "inline_crypto_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_crypto_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_crypto_denied.node_version", "");
    putPayload(payload, "inline_crypto_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_crypto_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_crypto_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_crypto_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_crypto_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_crypto_denied.ms", static_cast<long long>(0));
}

void putInlineOcrDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_ocr_denied.status", "skipped");
    putPayload(payload, "inline_ocr_denied.detail", detail);
    putPayload(payload, "inline_ocr_denied.result_text", "");
    putPayload(payload, "inline_ocr_denied.execution_mode", "");
    putPayload(payload, "inline_ocr_denied.source_kind", "");
    putPayload(payload, "inline_ocr_denied.user_source_used", false);
    putPayload(payload, "inline_ocr_denied.user_file_read", false);
    putPayload(payload, "inline_ocr_denied.ocr_bridge_allowed", false);
    putPayload(payload, "inline_ocr_denied.ocr_global_injected", false);
    putPayload(payload, "inline_ocr_denied.ocr_engine_created", false);
    putPayload(payload, "inline_ocr_denied.ocr_model_loaded", false);
    putPayload(payload, "inline_ocr_denied.bitmap_required", false);
    putPayload(payload, "inline_ocr_denied.bitmap_created", false);
    putPayload(payload, "inline_ocr_denied.screen_capture_attempted", false);
    putPayload(payload, "inline_ocr_denied.predictor_initialized", false);
    putPayload(payload, "inline_ocr_denied.recognize_text_attempted", false);
    putPayload(payload, "inline_ocr_denied.detect_attempted", false);
    putPayload(payload, "inline_ocr_denied.native_ocr_invoked", false);
    putPayload(payload, "inline_ocr_denied.native_library_loaded", false);
    putPayload(payload, "inline_ocr_denied.android_api_called", false);
    putPayload(payload, "inline_ocr_denied.host_object_injected", false);
    putPayload(payload, "inline_ocr_denied.binder_used", false);
    putPayload(payload, "inline_ocr_denied.policy", "");
    putPayload(payload, "inline_ocr_denied.denied_error_code", "");
    putPayload(payload, "inline_ocr_denied.denied_error_message", "");
    putPayload(payload, "inline_ocr_denied.final_state", "");
    putPayload(payload, "inline_ocr_denied.sequence_monotonic", false);
    putPayload(payload, "inline_ocr_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_ocr_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_ocr_denied.node_version", "");
    putPayload(payload, "inline_ocr_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_ocr_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_ocr_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_ocr_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_ocr_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_ocr_denied.ms", static_cast<long long>(0));
}

void putInlineMlAiDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_ml_ai_denied.status", "skipped");
    putPayload(payload, "inline_ml_ai_denied.detail", detail);
    putPayload(payload, "inline_ml_ai_denied.result_text", "");
    putPayload(payload, "inline_ml_ai_denied.execution_mode", "");
    putPayload(payload, "inline_ml_ai_denied.source_kind", "");
    putPayload(payload, "inline_ml_ai_denied.user_source_used", false);
    putPayload(payload, "inline_ml_ai_denied.user_file_read", false);
    putPayload(payload, "inline_ml_ai_denied.ml_ai_bridge_allowed", false);
    putPayload(payload, "inline_ml_ai_denied.ml_ai_global_injected", false);
    putPayload(payload, "inline_ml_ai_denied.model_loaded", false);
    putPayload(payload, "inline_ml_ai_denied.inference_engine_created", false);
    putPayload(payload, "inline_ml_ai_denied.inference_attempted", false);
    putPayload(payload, "inline_ml_ai_denied.tensor_created", false);
    putPayload(payload, "inline_ml_ai_denied.accelerator_used", false);
    putPayload(payload, "inline_ml_ai_denied.gpu_delegate_used", false);
    putPayload(payload, "inline_ml_ai_denied.nnapi_used", false);
    putPayload(payload, "inline_ml_ai_denied.native_ml_library_loaded", false);
    putPayload(payload, "inline_ml_ai_denied.android_api_called", false);
    putPayload(payload, "inline_ml_ai_denied.host_object_injected", false);
    putPayload(payload, "inline_ml_ai_denied.binder_used", false);
    putPayload(payload, "inline_ml_ai_denied.policy", "");
    putPayload(payload, "inline_ml_ai_denied.denied_error_code", "");
    putPayload(payload, "inline_ml_ai_denied.denied_error_message", "");
    putPayload(payload, "inline_ml_ai_denied.final_state", "");
    putPayload(payload, "inline_ml_ai_denied.sequence_monotonic", false);
    putPayload(payload, "inline_ml_ai_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_ml_ai_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_ml_ai_denied.node_version", "");
    putPayload(payload, "inline_ml_ai_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_ml_ai_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_ml_ai_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_ml_ai_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_ml_ai_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_ml_ai_denied.ms", static_cast<long long>(0));
}

void putInlineWebSocketDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_websocket_denied.status", "skipped");
    putPayload(payload, "inline_websocket_denied.detail", detail);
    putPayload(payload, "inline_websocket_denied.result_text", "");
    putPayload(payload, "inline_websocket_denied.execution_mode", "");
    putPayload(payload, "inline_websocket_denied.source_kind", "");
    putPayload(payload, "inline_websocket_denied.user_source_used", false);
    putPayload(payload, "inline_websocket_denied.user_file_read", false);
    putPayload(payload, "inline_websocket_denied.websocket_bridge_allowed", false);
    putPayload(payload, "inline_websocket_denied.websocket_global_injected", false);
    putPayload(payload, "inline_websocket_denied.websocket_created", false);
    putPayload(payload, "inline_websocket_denied.connection_attempted", false);
    putPayload(payload, "inline_websocket_denied.handshake_attempted", false);
    putPayload(payload, "inline_websocket_denied.socket_created", false);
    putPayload(payload, "inline_websocket_denied.dns_lookup_attempted", false);
    putPayload(payload, "inline_websocket_denied.message_sent", false);
    putPayload(payload, "inline_websocket_denied.message_received", false);
    putPayload(payload, "inline_websocket_denied.connection_closed", false);
    putPayload(payload, "inline_websocket_denied.network_permission_requested", false);
    putPayload(payload, "inline_websocket_denied.android_api_called", false);
    putPayload(payload, "inline_websocket_denied.host_object_injected", false);
    putPayload(payload, "inline_websocket_denied.binder_used", false);
    putPayload(payload, "inline_websocket_denied.policy", "");
    putPayload(payload, "inline_websocket_denied.denied_error_code", "");
    putPayload(payload, "inline_websocket_denied.denied_error_message", "");
    putPayload(payload, "inline_websocket_denied.final_state", "");
    putPayload(payload, "inline_websocket_denied.sequence_monotonic", false);
    putPayload(payload, "inline_websocket_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_websocket_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_websocket_denied.node_version", "");
    putPayload(payload, "inline_websocket_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_websocket_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_websocket_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_websocket_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_websocket_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_websocket_denied.ms", static_cast<long long>(0));
}

void putInlineBluetoothDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_bluetooth_denied.status", "skipped");
    putPayload(payload, "inline_bluetooth_denied.detail", detail);
    putPayload(payload, "inline_bluetooth_denied.result_text", "");
    putPayload(payload, "inline_bluetooth_denied.execution_mode", "");
    putPayload(payload, "inline_bluetooth_denied.source_kind", "");
    putPayload(payload, "inline_bluetooth_denied.user_source_used", false);
    putPayload(payload, "inline_bluetooth_denied.user_file_read", false);
    putPayload(payload, "inline_bluetooth_denied.bluetooth_bridge_allowed", false);
    putPayload(payload, "inline_bluetooth_denied.bluetooth_global_injected", false);
    putPayload(payload, "inline_bluetooth_denied.bluetooth_adapter_accessed", false);
    putPayload(payload, "inline_bluetooth_denied.bluetooth_manager_accessed", false);
    putPayload(payload, "inline_bluetooth_denied.scan_attempted", false);
    putPayload(payload, "inline_bluetooth_denied.device_pairing_attempted", false);
    putPayload(payload, "inline_bluetooth_denied.gatt_connection_attempted", false);
    putPayload(payload, "inline_bluetooth_denied.socket_connection_attempted", false);
    putPayload(payload, "inline_bluetooth_denied.permission_requested", false);
    putPayload(payload, "inline_bluetooth_denied.android_context_accessed", false);
    putPayload(payload, "inline_bluetooth_denied.android_api_called", false);
    putPayload(payload, "inline_bluetooth_denied.host_object_injected", false);
    putPayload(payload, "inline_bluetooth_denied.binder_used", false);
    putPayload(payload, "inline_bluetooth_denied.policy", "");
    putPayload(payload, "inline_bluetooth_denied.denied_error_code", "");
    putPayload(payload, "inline_bluetooth_denied.denied_error_message", "");
    putPayload(payload, "inline_bluetooth_denied.final_state", "");
    putPayload(payload, "inline_bluetooth_denied.sequence_monotonic", false);
    putPayload(payload, "inline_bluetooth_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_bluetooth_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_bluetooth_denied.node_version", "");
    putPayload(payload, "inline_bluetooth_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_bluetooth_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_bluetooth_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_bluetooth_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_bluetooth_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_bluetooth_denied.ms", static_cast<long long>(0));
}

void putInlineNfcDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_nfc_denied.status", "skipped");
    putPayload(payload, "inline_nfc_denied.detail", detail);
    putPayload(payload, "inline_nfc_denied.result_text", "");
    putPayload(payload, "inline_nfc_denied.execution_mode", "");
    putPayload(payload, "inline_nfc_denied.source_kind", "");
    putPayload(payload, "inline_nfc_denied.user_source_used", false);
    putPayload(payload, "inline_nfc_denied.user_file_read", false);
    putPayload(payload, "inline_nfc_denied.nfc_bridge_allowed", false);
    putPayload(payload, "inline_nfc_denied.nfc_global_injected", false);
    putPayload(payload, "inline_nfc_denied.nfc_adapter_accessed", false);
    putPayload(payload, "inline_nfc_denied.nfc_manager_accessed", false);
    putPayload(payload, "inline_nfc_denied.tag_scan_attempted", false);
    putPayload(payload, "inline_nfc_denied.ndef_read_attempted", false);
    putPayload(payload, "inline_nfc_denied.ndef_write_attempted", false);
    putPayload(payload, "inline_nfc_denied.foreground_dispatch_enabled", false);
    putPayload(payload, "inline_nfc_denied.permission_requested", false);
    putPayload(payload, "inline_nfc_denied.android_context_accessed", false);
    putPayload(payload, "inline_nfc_denied.android_api_called", false);
    putPayload(payload, "inline_nfc_denied.host_object_injected", false);
    putPayload(payload, "inline_nfc_denied.binder_used", false);
    putPayload(payload, "inline_nfc_denied.policy", "");
    putPayload(payload, "inline_nfc_denied.denied_error_code", "");
    putPayload(payload, "inline_nfc_denied.denied_error_message", "");
    putPayload(payload, "inline_nfc_denied.final_state", "");
    putPayload(payload, "inline_nfc_denied.sequence_monotonic", false);
    putPayload(payload, "inline_nfc_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_nfc_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_nfc_denied.node_version", "");
    putPayload(payload, "inline_nfc_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_nfc_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_nfc_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_nfc_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_nfc_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_nfc_denied.ms", static_cast<long long>(0));
}

void putInlineUsbDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_usb_denied.status", "skipped");
    putPayload(payload, "inline_usb_denied.detail", detail);
    putPayload(payload, "inline_usb_denied.result_text", "");
    putPayload(payload, "inline_usb_denied.execution_mode", "");
    putPayload(payload, "inline_usb_denied.source_kind", "");
    putPayload(payload, "inline_usb_denied.user_source_used", false);
    putPayload(payload, "inline_usb_denied.user_file_read", false);
    putPayload(payload, "inline_usb_denied.usb_bridge_allowed", false);
    putPayload(payload, "inline_usb_denied.usb_global_injected", false);
    putPayload(payload, "inline_usb_denied.usb_manager_accessed", false);
    putPayload(payload, "inline_usb_denied.device_list_queried", false);
    putPayload(payload, "inline_usb_denied.device_open_attempted", false);
    putPayload(payload, "inline_usb_denied.interface_claim_attempted", false);
    putPayload(payload, "inline_usb_denied.endpoint_accessed", false);
    putPayload(payload, "inline_usb_denied.bulk_transfer_attempted", false);
    putPayload(payload, "inline_usb_denied.permission_requested", false);
    putPayload(payload, "inline_usb_denied.android_context_accessed", false);
    putPayload(payload, "inline_usb_denied.android_api_called", false);
    putPayload(payload, "inline_usb_denied.host_object_injected", false);
    putPayload(payload, "inline_usb_denied.binder_used", false);
    putPayload(payload, "inline_usb_denied.policy", "");
    putPayload(payload, "inline_usb_denied.denied_error_code", "");
    putPayload(payload, "inline_usb_denied.denied_error_message", "");
    putPayload(payload, "inline_usb_denied.final_state", "");
    putPayload(payload, "inline_usb_denied.sequence_monotonic", false);
    putPayload(payload, "inline_usb_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_usb_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_usb_denied.node_version", "");
    putPayload(payload, "inline_usb_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_usb_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_usb_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_usb_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_usb_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_usb_denied.ms", static_cast<long long>(0));
}

void putInlineLocationDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_location_denied.status", "skipped");
    putPayload(payload, "inline_location_denied.detail", detail);
    putPayload(payload, "inline_location_denied.result_text", "");
    putPayload(payload, "inline_location_denied.execution_mode", "");
    putPayload(payload, "inline_location_denied.source_kind", "");
    putPayload(payload, "inline_location_denied.user_source_used", false);
    putPayload(payload, "inline_location_denied.user_file_read", false);
    putPayload(payload, "inline_location_denied.location_bridge_allowed", false);
    putPayload(payload, "inline_location_denied.location_global_injected", false);
    putPayload(payload, "inline_location_denied.location_manager_accessed", false);
    putPayload(payload, "inline_location_denied.fused_location_accessed", false);
    putPayload(payload, "inline_location_denied.last_location_queried", false);
    putPayload(payload, "inline_location_denied.location_updates_requested", false);
    putPayload(payload, "inline_location_denied.gps_provider_used", false);
    putPayload(payload, "inline_location_denied.network_provider_used", false);
    putPayload(payload, "inline_location_denied.permission_requested", false);
    putPayload(payload, "inline_location_denied.android_context_accessed", false);
    putPayload(payload, "inline_location_denied.android_api_called", false);
    putPayload(payload, "inline_location_denied.host_object_injected", false);
    putPayload(payload, "inline_location_denied.binder_used", false);
    putPayload(payload, "inline_location_denied.policy", "");
    putPayload(payload, "inline_location_denied.denied_error_code", "");
    putPayload(payload, "inline_location_denied.denied_error_message", "");
    putPayload(payload, "inline_location_denied.final_state", "");
    putPayload(payload, "inline_location_denied.sequence_monotonic", false);
    putPayload(payload, "inline_location_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_location_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_location_denied.node_version", "");
    putPayload(payload, "inline_location_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_location_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_location_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_location_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_location_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_location_denied.ms", static_cast<long long>(0));
}

void putInlineContactsDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_contacts_denied.status", "skipped");
    putPayload(payload, "inline_contacts_denied.detail", detail);
    putPayload(payload, "inline_contacts_denied.result_text", "");
    putPayload(payload, "inline_contacts_denied.execution_mode", "");
    putPayload(payload, "inline_contacts_denied.source_kind", "");
    putPayload(payload, "inline_contacts_denied.user_source_used", false);
    putPayload(payload, "inline_contacts_denied.user_file_read", false);
    putPayload(payload, "inline_contacts_denied.contacts_bridge_allowed", false);
    putPayload(payload, "inline_contacts_denied.contacts_global_injected", false);
    putPayload(payload, "inline_contacts_denied.content_resolver_accessed", false);
    putPayload(payload, "inline_contacts_denied.contacts_provider_queried", false);
    putPayload(payload, "inline_contacts_denied.contact_read_attempted", false);
    putPayload(payload, "inline_contacts_denied.contact_write_attempted", false);
    putPayload(payload, "inline_contacts_denied.cursor_opened", false);
    putPayload(payload, "inline_contacts_denied.permission_requested", false);
    putPayload(payload, "inline_contacts_denied.android_context_accessed", false);
    putPayload(payload, "inline_contacts_denied.android_api_called", false);
    putPayload(payload, "inline_contacts_denied.host_object_injected", false);
    putPayload(payload, "inline_contacts_denied.binder_used", false);
    putPayload(payload, "inline_contacts_denied.policy", "");
    putPayload(payload, "inline_contacts_denied.denied_error_code", "");
    putPayload(payload, "inline_contacts_denied.denied_error_message", "");
    putPayload(payload, "inline_contacts_denied.final_state", "");
    putPayload(payload, "inline_contacts_denied.sequence_monotonic", false);
    putPayload(payload, "inline_contacts_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_contacts_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_contacts_denied.node_version", "");
    putPayload(payload, "inline_contacts_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_contacts_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_contacts_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_contacts_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_contacts_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_contacts_denied.ms", static_cast<long long>(0));
}

void putInlineCalendarDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_calendar_denied.status", "skipped");
    putPayload(payload, "inline_calendar_denied.detail", detail);
    putPayload(payload, "inline_calendar_denied.result_text", "");
    putPayload(payload, "inline_calendar_denied.execution_mode", "");
    putPayload(payload, "inline_calendar_denied.source_kind", "");
    putPayload(payload, "inline_calendar_denied.user_source_used", false);
    putPayload(payload, "inline_calendar_denied.user_file_read", false);
    putPayload(payload, "inline_calendar_denied.calendar_bridge_allowed", false);
    putPayload(payload, "inline_calendar_denied.calendar_global_injected", false);
    putPayload(payload, "inline_calendar_denied.content_resolver_accessed", false);
    putPayload(payload, "inline_calendar_denied.calendar_provider_queried", false);
    putPayload(payload, "inline_calendar_denied.event_read_attempted", false);
    putPayload(payload, "inline_calendar_denied.event_write_attempted", false);
    putPayload(payload, "inline_calendar_denied.reminder_accessed", false);
    putPayload(payload, "inline_calendar_denied.cursor_opened", false);
    putPayload(payload, "inline_calendar_denied.permission_requested", false);
    putPayload(payload, "inline_calendar_denied.android_context_accessed", false);
    putPayload(payload, "inline_calendar_denied.android_api_called", false);
    putPayload(payload, "inline_calendar_denied.host_object_injected", false);
    putPayload(payload, "inline_calendar_denied.binder_used", false);
    putPayload(payload, "inline_calendar_denied.policy", "");
    putPayload(payload, "inline_calendar_denied.denied_error_code", "");
    putPayload(payload, "inline_calendar_denied.denied_error_message", "");
    putPayload(payload, "inline_calendar_denied.final_state", "");
    putPayload(payload, "inline_calendar_denied.sequence_monotonic", false);
    putPayload(payload, "inline_calendar_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_calendar_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_calendar_denied.node_version", "");
    putPayload(payload, "inline_calendar_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_calendar_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_calendar_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_calendar_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_calendar_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_calendar_denied.ms", static_cast<long long>(0));
}

void putInlineSmsTelephonyDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_sms_telephony_denied.status", "skipped");
    putPayload(payload, "inline_sms_telephony_denied.detail", detail);
    putPayload(payload, "inline_sms_telephony_denied.result_text", "");
    putPayload(payload, "inline_sms_telephony_denied.execution_mode", "");
    putPayload(payload, "inline_sms_telephony_denied.source_kind", "");
    putPayload(payload, "inline_sms_telephony_denied.user_source_used", false);
    putPayload(payload, "inline_sms_telephony_denied.user_file_read", false);
    putPayload(payload, "inline_sms_telephony_denied.sms_telephony_bridge_allowed", false);
    putPayload(payload, "inline_sms_telephony_denied.sms_global_injected", false);
    putPayload(payload, "inline_sms_telephony_denied.telephony_global_injected", false);
    putPayload(payload, "inline_sms_telephony_denied.sms_manager_accessed", false);
    putPayload(payload, "inline_sms_telephony_denied.telephony_manager_accessed", false);
    putPayload(payload, "inline_sms_telephony_denied.sms_send_attempted", false);
    putPayload(payload, "inline_sms_telephony_denied.sms_read_attempted", false);
    putPayload(payload, "inline_sms_telephony_denied.call_state_queried", false);
    putPayload(payload, "inline_sms_telephony_denied.phone_number_queried", false);
    putPayload(payload, "inline_sms_telephony_denied.permission_requested", false);
    putPayload(payload, "inline_sms_telephony_denied.android_context_accessed", false);
    putPayload(payload, "inline_sms_telephony_denied.android_api_called", false);
    putPayload(payload, "inline_sms_telephony_denied.host_object_injected", false);
    putPayload(payload, "inline_sms_telephony_denied.binder_used", false);
    putPayload(payload, "inline_sms_telephony_denied.policy", "");
    putPayload(payload, "inline_sms_telephony_denied.denied_error_code", "");
    putPayload(payload, "inline_sms_telephony_denied.denied_error_message", "");
    putPayload(payload, "inline_sms_telephony_denied.final_state", "");
    putPayload(payload, "inline_sms_telephony_denied.sequence_monotonic", false);
    putPayload(payload, "inline_sms_telephony_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_sms_telephony_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_sms_telephony_denied.node_version", "");
    putPayload(payload, "inline_sms_telephony_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_sms_telephony_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_sms_telephony_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_sms_telephony_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_sms_telephony_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_sms_telephony_denied.ms", static_cast<long long>(0));
}

void putInlineAccountDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_account_denied", "timing.inline_account_denied.ms", kInlineAccountDeniedFields, detail);
}

void putInlinePackageManagerDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_package_manager_denied", "timing.inline_package_manager_denied.ms", kInlinePackageManagerDeniedFields, detail);
}

void putInlineIntentActivityDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_intent_activity_denied", "timing.inline_intent_activity_denied.ms", kInlineIntentActivityDeniedFields, detail);
}

void putInlineBroadcastDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_broadcast_denied", "timing.inline_broadcast_denied.ms", kInlineBroadcastDeniedFields, detail);
}

void putInlineContentProviderDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_content_provider_denied", "timing.inline_content_provider_denied.ms", kInlineContentProviderDeniedFields, detail);
}

void putInlineMediaStoreDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_media_store_denied", "timing.inline_media_store_denied.ms", kInlineMediaStoreDeniedFields, detail);
}

void putInlineDownloadManagerDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_download_manager_denied", "timing.inline_download_manager_denied.ms", kInlineDownloadManagerDeniedFields, detail);
}

void putInlineInputMethodDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_input_method_denied", "timing.inline_input_method_denied.ms", kInlineInputMethodDeniedFields, detail);
}

void putInlineAppOpsDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_app_ops_denied", "timing.inline_app_ops_denied.ms", kInlineAppOpsDeniedFields, detail);
}

void putInlinePermissionManagerDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_permission_manager_denied", "timing.inline_permission_manager_denied.ms", kInlinePermissionManagerDeniedFields, detail);
}

void putInlineSettingsDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_settings_denied", "timing.inline_settings_denied.ms", kInlineSettingsDeniedFields, detail);
}

void putInlinePowerManagerDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_power_manager_denied", "timing.inline_power_manager_denied.ms", kInlinePowerManagerDeniedFields, detail);
}

void putInlineKeyguardDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_keyguard_denied", "timing.inline_keyguard_denied.ms", kInlineKeyguardDeniedFields, detail);
}

void putInlineWallpaperDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_wallpaper_denied", "timing.inline_wallpaper_denied.ms", kInlineWallpaperDeniedFields, detail);
}

void putInlineShortcutManagerDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_shortcut_manager_denied", "timing.inline_shortcut_manager_denied.ms", kInlineShortcutManagerDeniedFields, detail);
}

void putInlineAlarmManagerDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_alarm_manager_denied", "timing.inline_alarm_manager_denied.ms", kInlineAlarmManagerDeniedFields, detail);
}

void putInlineJobSchedulerDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_job_scheduler_denied", "timing.inline_job_scheduler_denied.ms", kInlineJobSchedulerDeniedFields, detail);
}

void putInlineWorkManagerDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_work_manager_denied", "timing.inline_work_manager_denied.ms", kInlineWorkManagerDeniedFields, detail);
}

void putInlineClipboardListenerDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_clipboard_listener_denied", "timing.inline_clipboard_listener_denied.ms", kInlineClipboardListenerDeniedFields, detail);
}
void putInlineNotificationListenerDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_notification_listener_denied", "timing.inline_notification_listener_denied.ms", kInlineNotificationListenerDeniedFields, detail);
}
void putInlineAccessibilityControlDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_accessibility_control_denied", "timing.inline_accessibility_control_denied.ms", kInlineAccessibilityControlDeniedFields, detail);
}
void putInlineDevicePolicyDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_device_policy_denied", "timing.inline_device_policy_denied.ms", kInlineDevicePolicyDeniedFields, detail);
}
void putInlineUsageStatsDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_usage_stats_denied", "timing.inline_usage_stats_denied.ms", kInlineUsageStatsDeniedFields, detail);
}
void putInlineVpnConnectivityDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_vpn_connectivity_denied", "timing.inline_vpn_connectivity_denied.ms", kInlineVpnConnectivityDeniedFields, detail);
}
void putInlineWifiManagerDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_wifi_manager_denied", "timing.inline_wifi_manager_denied.ms", kInlineWifiManagerDeniedFields, detail);
}
void putInlineTelephonySubscriptionDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_telephony_subscription_denied", "timing.inline_telephony_subscription_denied.ms", kInlineTelephonySubscriptionDeniedFields, detail);
}
void putInlineCameraManagerDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_camera_manager_denied", "timing.inline_camera_manager_denied.ms", kInlineCameraManagerDeniedFields, detail);
}
void putInlineAudioManagerDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_audio_manager_denied", "timing.inline_audio_manager_denied.ms", kInlineAudioManagerDeniedFields, detail);
}
void putInlineDisplayManagerDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_display_manager_denied", "timing.inline_display_manager_denied.ms", kInlineDisplayManagerDeniedFields, detail);
}
void putInlinePrintManagerDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putInlineDeniedSkippedPayload(payload, "inline_print_manager_denied", "timing.inline_print_manager_denied.ms", kInlinePrintManagerDeniedFields, detail);
}
void putControlledUserInlineConstantSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "controlled_user_inline_constant", "timing.controlled_user_inline_constant.ms", kControlledUserInlineConstantFields, detail);
}

void putControlledUserInlineStdoutSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "controlled_user_inline_stdout", "timing.controlled_user_inline_stdout.ms", kControlledUserInlineStdoutFields, detail);
}

void putControlledUserInlineStderrSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "controlled_user_inline_stderr", "timing.controlled_user_inline_stderr.ms", kControlledUserInlineStderrFields, detail);
}

void putControlledUserInlineReturnValueSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "controlled_user_inline_return_value", "timing.controlled_user_inline_return_value.ms", kControlledUserInlineReturnValueFields, detail);
}

void putControlledUserInlineThrownErrorSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "controlled_user_inline_thrown_error", "timing.controlled_user_inline_thrown_error.ms", kControlledUserInlineThrownErrorFields, detail);
}

void putControlledUserInlinePromiseSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "controlled_user_inline_promise", "timing.controlled_user_inline_promise.ms", kControlledUserInlinePromiseFields, detail);
}

void putControlledUserInlineAsyncOrderingSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "controlled_user_inline_async_ordering", "timing.controlled_user_inline_async_ordering.ms", kControlledUserInlineAsyncOrderingFields, detail);
}

void putControlledUserInlineSummarySkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "controlled_user_inline_summary", "timing.controlled_user_inline_summary.ms", kControlledUserInlineSummaryFields, detail);
}

void putUserFileDescriptorSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "user_file_descriptor", "timing.user_file_descriptor.ms", kUserFileDescriptorFields, detail);
}
void putUserFileReadPolicySkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "user_file_read_policy", "timing.user_file_read_policy.ms", kUserFileReadPolicyFields, detail);
}
void putUserFilePathNormalizationSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "user_file_path_normalization", "timing.user_file_path_normalization.ms", kUserFilePathNormalizationFields, detail);
}
void putUserFileWorkingDirectorySkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "user_file_working_directory", "timing.user_file_working_directory.ms", kUserFileWorkingDirectoryFields, detail);
}
void putUserFileSourceLoadingSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "user_file_source_loading", "timing.user_file_source_loading.ms", kUserFileSourceLoadingFields, detail);
}
void putUserFileExecutionDryRunSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "user_file_execution_dry_run", "timing.user_file_execution_dry_run.ms", kUserFileExecutionDryRunFields, detail);
}
void putUserFileErrorStackFilenameSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "user_file_error_stack_filename", "timing.user_file_error_stack_filename.ms", kUserFileErrorStackFilenameFields, detail);
}
void putUserFileExecutionSummarySkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "user_file_execution_summary", "timing.user_file_execution_summary.ms", kUserFileExecutionSummaryFields, detail);
}
void putEmbeddedScriptRequestSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "embedded_script_request", "timing.embedded_script_request.ms", kEmbeddedScriptRequestFields, detail);
}
void putEmbeddedScriptResultSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "embedded_script_result", "timing.embedded_script_result.ms", kEmbeddedScriptResultFields, detail);
}
void putEmbeddedScriptOutputEventSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "embedded_script_output_event", "timing.embedded_script_output_event.ms", kEmbeddedScriptOutputEventFields, detail);
}
void putEmbeddedScriptErrorSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "embedded_script_error", "timing.embedded_script_error.ms", kEmbeddedScriptErrorFields, detail);
}
void putEmbeddedScriptTimeoutSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "embedded_script_timeout", "timing.embedded_script_timeout.ms", kEmbeddedScriptTimeoutFields, detail);
}
void putEmbeddedScriptCancellationSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "embedded_script_cancellation", "timing.embedded_script_cancellation.ms", kEmbeddedScriptCancellationFields, detail);
}
void putEmbeddedScriptProcessIsolationSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "embedded_script_process_isolation", "timing.embedded_script_process_isolation.ms", kEmbeddedScriptProcessIsolationFields, detail);
}
void putEmbeddedScriptContractSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "embedded_script_contract", "timing.embedded_script_contract.ms", kEmbeddedScriptContractFields, detail);
}
void putEmbeddedMvpLifecycleReadinessSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "embedded_mvp_lifecycle", "timing.embedded_mvp_lifecycle.ms", kEmbeddedMvpLifecycleReadinessFields, detail);
}
void putEmbeddedMvpSourceInputReadinessSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "embedded_mvp_source_input", "timing.embedded_mvp_source_input.ms", kEmbeddedMvpSourceInputReadinessFields, detail);
}
void putEmbeddedMvpOutputReadinessSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "embedded_mvp_output", "timing.embedded_mvp_output.ms", kEmbeddedMvpOutputReadinessFields, detail);
}
void putEmbeddedMvpErrorHandlingReadinessSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "embedded_mvp_error_handling", "timing.embedded_mvp_error_handling.ms", kEmbeddedMvpErrorHandlingReadinessFields, detail);
}
void putEmbeddedMvpAsyncReadinessSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "embedded_mvp_async", "timing.embedded_mvp_async.ms", kEmbeddedMvpAsyncReadinessFields, detail);
}
void putEmbeddedMvpTimeoutIsolationReadinessSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "embedded_mvp_timeout_isolation", "timing.embedded_mvp_timeout_isolation.ms", kEmbeddedMvpTimeoutIsolationReadinessFields, detail);
}
void putEmbeddedMvpSecurityPolicyReadinessSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "embedded_mvp_security_policy", "timing.embedded_mvp_security_policy.ms", kEmbeddedMvpSecurityPolicyReadinessFields, detail);
}
void putEmbeddedMvpGoNoGoReadinessSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putControlledUserInlineSkippedPayload(payload, "embedded_mvp_go_no_go", "timing.embedded_mvp_go_no_go.ms", kEmbeddedMvpGoNoGoReadinessFields, detail);
}

void putUserSourceDescriptorSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putUserSourcePreflightSkippedPayload(payload, "user_source_descriptor", "timing.user_source_descriptor.ms", kUserSourceDescriptorFields, detail);
}
void putUserSourceSizeSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putUserSourcePreflightSkippedPayload(payload, "user_source_size", "timing.user_source_size.ms", kUserSourceSizeFields, detail);
}
void putUserSourceEncodingSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putUserSourcePreflightSkippedPayload(payload, "user_source_encoding", "timing.user_source_encoding.ms", kUserSourceEncodingFields, detail);
}
void putUserSourceNameSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putUserSourcePreflightSkippedPayload(payload, "user_source_name", "timing.user_source_name.ms", kUserSourceNameFields, detail);
}
void putUserSourceWrapperSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putUserSourcePreflightSkippedPayload(payload, "user_source_wrapper", "timing.user_source_wrapper.ms", kUserSourceWrapperFields, detail);
}
void putUserSourceStrictModeSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putUserSourcePreflightSkippedPayload(payload, "user_source_strict_mode", "timing.user_source_strict_mode.ms", kUserSourceStrictModeFields, detail);
}
void putUserSourceCapabilitySkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putUserSourcePreflightSkippedPayload(payload, "user_source_capability", "timing.user_source_capability.ms", kUserSourceCapabilityFields, detail);
}
void putUserSourcePreflightSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putUserSourcePreflightSkippedPayload(payload, "user_source_preflight", "timing.user_source_preflight.ms", kUserSourcePreflightFields, detail);
}

void putInlineWebViewDeniedSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_webview_denied.status", "skipped");
    putPayload(payload, "inline_webview_denied.detail", detail);
    putPayload(payload, "inline_webview_denied.result_text", "");
    putPayload(payload, "inline_webview_denied.execution_mode", "");
    putPayload(payload, "inline_webview_denied.source_kind", "");
    putPayload(payload, "inline_webview_denied.user_source_used", false);
    putPayload(payload, "inline_webview_denied.user_file_read", false);
    putPayload(payload, "inline_webview_denied.webview_bridge_allowed", false);
    putPayload(payload, "inline_webview_denied.webview_global_injected", false);
    putPayload(payload, "inline_webview_denied.webview_created", false);
    putPayload(payload, "inline_webview_denied.webview_context_required", false);
    putPayload(payload, "inline_webview_denied.webview_settings_accessed", false);
    putPayload(payload, "inline_webview_denied.javascript_interface_added", false);
    putPayload(payload, "inline_webview_denied.url_loaded", false);
    putPayload(payload, "inline_webview_denied.html_loaded", false);
    putPayload(payload, "inline_webview_denied.webview_client_set", false);
    putPayload(payload, "inline_webview_denied.chrome_client_set", false);
    putPayload(payload, "inline_webview_denied.activity_accessed", false);
    putPayload(payload, "inline_webview_denied.ui_thread_dispatch_attempted", false);
    putPayload(payload, "inline_webview_denied.android_api_called", false);
    putPayload(payload, "inline_webview_denied.host_object_injected", false);
    putPayload(payload, "inline_webview_denied.binder_used", false);
    putPayload(payload, "inline_webview_denied.policy", "");
    putPayload(payload, "inline_webview_denied.denied_error_code", "");
    putPayload(payload, "inline_webview_denied.denied_error_message", "");
    putPayload(payload, "inline_webview_denied.final_state", "");
    putPayload(payload, "inline_webview_denied.sequence_monotonic", false);
    putPayload(payload, "inline_webview_denied.event_count", static_cast<long long>(0));
    putPayload(payload, "inline_webview_denied.response_count", static_cast<long long>(0));
    putPayload(payload, "inline_webview_denied.node_version", "");
    putPayload(payload, "inline_webview_denied.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_webview_denied.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_webview_denied.script_source.length", static_cast<long long>(0));
    putPayload(payload, "inline_webview_denied.load_environment.result", "skipped");
    putPayload(payload, "inline_webview_denied.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.inline_webview_denied.ms", static_cast<long long>(0));
}

void putInlineConstantFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_constant.result_text", text);
    putPayload(payload, "inline_constant.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_constant.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_constant.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_constant.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_constant.script_started", jsonBooleanField(text, "scriptStarted"));
    putPayload(payload, "inline_constant.script_completed", jsonBooleanField(text, "scriptCompleted"));
    putPayload(payload, "inline_constant.has_value", jsonBooleanField(text, "hasValue"));
    putPayload(payload, "inline_constant.value_type", jsonStringField(text, "valueType"));
    putPayload(payload, "inline_constant.value_preview", jsonStringField(text, "valuePreview"));
    putPayload(payload, "inline_constant.require_available", jsonBooleanField(text, "requireAvailable"));
    putPayload(payload, "inline_constant.npm_available", jsonBooleanField(text, "npmAvailable"));
    putPayload(payload, "inline_constant.android_bridge_available", jsonBooleanField(text, "androidBridgeAvailable"));
    putPayload(payload, "inline_constant.autojs_api_available", jsonBooleanField(text, "autojsApiAvailable"));
    putPayload(payload, "inline_constant.exit_code", jsonNumberField(text, "exitCode"));
    putPayload(payload, "inline_constant.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_constant.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_constant.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_constant.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_constant.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_constant.detail", error);
    }
}

void putInlineReturnValueFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_return_value.result_text", text);
    putPayload(payload, "inline_return_value.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_return_value.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_return_value.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_return_value.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_return_value.return_captured", jsonBooleanField(text, "returnCaptured"));
    putPayload(payload, "inline_return_value.return_serializable", jsonBooleanField(text, "returnSerializable"));
    putPayload(payload, "inline_return_value.value_type", jsonStringField(text, "valueType"));
    putPayload(payload, "inline_return_value.value_preview", jsonStringField(text, "valuePreview"));
    putPayload(payload, "inline_return_value.value_json", jsonStringField(text, "valueJson"));
    putPayload(payload, "inline_return_value.result_envelope_ready", jsonBooleanField(text, "resultEnvelopeReady"));
    putPayload(payload, "inline_return_value.exit_code", jsonNumberField(text, "exitCode"));
    putPayload(payload, "inline_return_value.error_present", jsonBooleanField(text, "errorPresent"));
    putPayload(payload, "inline_return_value.require_available", jsonBooleanField(text, "requireAvailable"));
    putPayload(payload, "inline_return_value.npm_available", jsonBooleanField(text, "npmAvailable"));
    putPayload(payload, "inline_return_value.android_bridge_available", jsonBooleanField(text, "androidBridgeAvailable"));
    putPayload(payload, "inline_return_value.autojs_api_available", jsonBooleanField(text, "autojsApiAvailable"));
    putPayload(payload, "inline_return_value.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_return_value.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_return_value.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_return_value.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_return_value.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_return_value.detail", error);
    }
}

void putInlineErrorFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_error.result_text", text);
    putPayload(payload, "inline_error.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_error.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_error.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_error.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_error.error_thrown", jsonBooleanField(text, "errorThrown"));
    putPayload(payload, "inline_error.error_captured", jsonBooleanField(text, "errorCaptured"));
    putPayload(payload, "inline_error.error_name", jsonStringField(text, "errorName"));
    putPayload(payload, "inline_error.error_message", jsonStringField(text, "errorMessage"));
    putPayload(payload, "inline_error.stack_available", jsonBooleanField(text, "stackAvailable"));
    putPayload(payload, "inline_error.result_envelope_ready", jsonBooleanField(text, "resultEnvelopeReady"));
    putPayload(payload, "inline_error.exit_code", jsonNumberField(text, "exitCode"));
    putPayload(payload, "inline_error.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_error.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_error.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_error.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_error.require_available", jsonBooleanField(text, "requireAvailable"));
    putPayload(payload, "inline_error.npm_available", jsonBooleanField(text, "npmAvailable"));
    putPayload(payload, "inline_error.android_bridge_available", jsonBooleanField(text, "androidBridgeAvailable"));
    putPayload(payload, "inline_error.autojs_api_available", jsonBooleanField(text, "autojsApiAvailable"));
    putPayload(payload, "inline_error.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_error.detail", error);
    }
}

void putInlineOutputFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_output.result_text", text);
    putPayload(payload, "inline_output.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_output.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_output.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_output.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_output.stdout_written", jsonBooleanField(text, "stdoutWritten"));
    putPayload(payload, "inline_output.stderr_written", jsonBooleanField(text, "stderrWritten"));
    putPayload(payload, "inline_output.stdout_captured", jsonBooleanField(text, "stdoutCaptured"));
    putPayload(payload, "inline_output.stderr_captured", jsonBooleanField(text, "stderrCaptured"));
    putPayload(payload, "inline_output.stdout_contains_probe", jsonBooleanField(text, "stdoutContainsProbe"));
    putPayload(payload, "inline_output.stderr_contains_probe", jsonBooleanField(text, "stderrContainsProbe"));
    putPayload(payload, "inline_output.stdout_event_count", jsonNumberField(text, "stdoutEventCount"));
    putPayload(payload, "inline_output.stderr_event_count", jsonNumberField(text, "stderrEventCount"));
    putPayload(payload, "inline_output.output_dropped_count", jsonNumberField(text, "outputDroppedCount"));
    putPayload(payload, "inline_output.exit_code", jsonNumberField(text, "exitCode"));
    putPayload(payload, "inline_output.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_output.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_output.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_output.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_output.require_available", jsonBooleanField(text, "requireAvailable"));
    putPayload(payload, "inline_output.npm_available", jsonBooleanField(text, "npmAvailable"));
    putPayload(payload, "inline_output.android_bridge_available", jsonBooleanField(text, "androidBridgeAvailable"));
    putPayload(payload, "inline_output.autojs_api_available", jsonBooleanField(text, "autojsApiAvailable"));
    putPayload(payload, "inline_output.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_output.detail", error);
    }
}

void putInlineAsyncFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_async.result_text", text);
    putPayload(payload, "inline_async.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_async.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_async.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_async.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_async.async_started", jsonBooleanField(text, "asyncStarted"));
    putPayload(payload, "inline_async.async_completed", jsonBooleanField(text, "asyncCompleted"));
    putPayload(payload, "inline_async.has_next_tick", jsonBooleanField(text, "hasNextTick"));
    putPayload(payload, "inline_async.has_promise", jsonBooleanField(text, "hasPromise"));
    putPayload(payload, "inline_async.has_set_immediate", jsonBooleanField(text, "hasSetImmediate"));
    putPayload(payload, "inline_async.has_set_timeout", jsonBooleanField(text, "hasSetTimeout"));
    putPayload(payload, "inline_async.has_queue_microtask", jsonBooleanField(text, "hasQueueMicrotask"));
    putPayload(payload, "inline_async.order", jsonStringField(text, "order"));
    putPayload(payload, "inline_async.order_matches_expected", jsonBooleanField(text, "orderMatchesExpected"));
    putPayload(payload, "inline_async.result_written_after_async", jsonBooleanField(text, "resultWrittenAfterAsync"));
    putPayload(payload, "inline_async.exit_code", jsonNumberField(text, "exitCode"));
    putPayload(payload, "inline_async.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_async.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_async.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_async.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_async.require_available", jsonBooleanField(text, "requireAvailable"));
    putPayload(payload, "inline_async.npm_available", jsonBooleanField(text, "npmAvailable"));
    putPayload(payload, "inline_async.android_bridge_available", jsonBooleanField(text, "androidBridgeAvailable"));
    putPayload(payload, "inline_async.autojs_api_available", jsonBooleanField(text, "autojsApiAvailable"));
    putPayload(payload, "inline_async.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_async.detail", error);
    }
}

void putInlineCancelFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_cancel.result_text", text);
    putPayload(payload, "inline_cancel.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_cancel.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_cancel.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_cancel.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_cancel.real_v8_interrupt", jsonBooleanField(text, "realV8Interrupt"));
    putPayload(payload, "inline_cancel.cancel_token_created", jsonBooleanField(text, "cancelTokenCreated"));
    putPayload(payload, "inline_cancel.cancel_requested", jsonBooleanField(text, "cancelRequested"));
    putPayload(payload, "inline_cancel.cancel_observed", jsonBooleanField(text, "cancelObserved"));
    putPayload(payload, "inline_cancel.cancel_reason", jsonStringField(text, "cancelReason"));
    putPayload(payload, "inline_cancel.cancel_exit_code", jsonNumberField(text, "cancelExitCode"));
    putPayload(payload, "inline_cancel.cancelled_event_emitted", jsonBooleanField(text, "cancelledEventEmitted"));
    putPayload(payload, "inline_cancel.script_started", jsonBooleanField(text, "scriptStarted"));
    putPayload(payload, "inline_cancel.script_cancelled", jsonBooleanField(text, "scriptCancelled"));
    putPayload(payload, "inline_cancel.script_completed", jsonBooleanField(text, "scriptCompleted"));
    putPayload(payload, "inline_cancel.result_envelope_ready", jsonBooleanField(text, "resultEnvelopeReady"));
    putPayload(payload, "inline_cancel.require_available", jsonBooleanField(text, "requireAvailable"));
    putPayload(payload, "inline_cancel.npm_available", jsonBooleanField(text, "npmAvailable"));
    putPayload(payload, "inline_cancel.android_bridge_available", jsonBooleanField(text, "androidBridgeAvailable"));
    putPayload(payload, "inline_cancel.autojs_api_available", jsonBooleanField(text, "autojsApiAvailable"));
    putPayload(payload, "inline_cancel.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_cancel.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_cancel.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_cancel.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_cancel.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_cancel.detail", error);
    }
}

void putInlineTimeoutFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_timeout.result_text", text);
    putPayload(payload, "inline_timeout.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_timeout.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_timeout.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_timeout.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_timeout.real_hard_kill", jsonBooleanField(text, "realHardKill"));
    putPayload(payload, "inline_timeout.timeout_policy_created", jsonBooleanField(text, "timeoutPolicyCreated"));
    putPayload(payload, "inline_timeout.timeout_ms", jsonNumberField(text, "timeoutMs"));
    putPayload(payload, "inline_timeout.timeout_detected", jsonBooleanField(text, "timeoutDetected"));
    putPayload(payload, "inline_timeout.timeout_reason", jsonStringField(text, "timeoutReason"));
    putPayload(payload, "inline_timeout.timeout_exit_code", jsonNumberField(text, "timeoutExitCode"));
    putPayload(payload, "inline_timeout.timeout_event_emitted", jsonBooleanField(text, "timeoutEventEmitted"));
    putPayload(payload, "inline_timeout.script_started", jsonBooleanField(text, "scriptStarted"));
    putPayload(payload, "inline_timeout.script_timed_out", jsonBooleanField(text, "scriptTimedOut"));
    putPayload(payload, "inline_timeout.script_completed", jsonBooleanField(text, "scriptCompleted"));
    putPayload(payload, "inline_timeout.result_error_code", jsonStringField(text, "resultErrorCode"));
    putPayload(payload, "inline_timeout.result_envelope_ready", jsonBooleanField(text, "resultEnvelopeReady"));
    putPayload(payload, "inline_timeout.dispose_after_timeout", jsonBooleanField(text, "disposeAfterTimeout"));
    putPayload(payload, "inline_timeout.restart_required", jsonBooleanField(text, "restartRequired"));
    putPayload(payload, "inline_timeout.diagnostics_preserved", jsonBooleanField(text, "diagnosticsPreserved"));
    putPayload(payload, "inline_timeout.require_available", jsonBooleanField(text, "requireAvailable"));
    putPayload(payload, "inline_timeout.npm_available", jsonBooleanField(text, "npmAvailable"));
    putPayload(payload, "inline_timeout.android_bridge_available", jsonBooleanField(text, "androidBridgeAvailable"));
    putPayload(payload, "inline_timeout.autojs_api_available", jsonBooleanField(text, "autojsApiAvailable"));
    putPayload(payload, "inline_timeout.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_timeout.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_timeout.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_timeout.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_timeout.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_timeout.detail", error);
    }
}

void putInlineRepeatedProcessFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_repeated_process.result_text", text);
    putPayload(payload, "inline_repeated_process.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_repeated_process.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_repeated_process.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_repeated_process.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_repeated_process.same_process_second_lifecycle", jsonBooleanField(text, "sameProcessSecondLifecycle"));
    putPayload(payload, "inline_repeated_process.single_process_reuse_allowed", jsonBooleanField(text, "singleProcessReuseAllowed"));
    putPayload(payload, "inline_repeated_process.requires_fresh_process", jsonBooleanField(text, "requiresFreshProcess"));
    putPayload(payload, "inline_repeated_process.force_stop_required_between_full_probes", jsonBooleanField(text, "forceStopRequiredBetweenFullProbes"));
    putPayload(payload, "inline_repeated_process.second_lifecycle_in_same_process_allowed", jsonBooleanField(text, "secondLifecycleInSameProcessAllowed"));
    putPayload(payload, "inline_repeated_process.first_process_policy", jsonStringField(text, "firstProcessPolicy"));
    putPayload(payload, "inline_repeated_process.second_process_policy", jsonStringField(text, "secondProcessPolicy"));
    putPayload(payload, "inline_repeated_process.double_initialization_risk_detected", jsonBooleanField(text, "doubleInitializationRiskDetected"));
    putPayload(payload, "inline_repeated_process.double_initialization_error_code", jsonStringField(text, "doubleInitializationErrorCode"));
    putPayload(payload, "inline_repeated_process.script_started", jsonBooleanField(text, "scriptStarted"));
    putPayload(payload, "inline_repeated_process.script_completed", jsonBooleanField(text, "scriptCompleted"));
    putPayload(payload, "inline_repeated_process.exit_code", jsonNumberField(text, "exitCode"));
    putPayload(payload, "inline_repeated_process.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_repeated_process.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_repeated_process.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_repeated_process.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_repeated_process.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_repeated_process.detail", error);
    }
}

void putInlineBuiltinPolicyFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_builtin_policy.result_text", text);
    putPayload(payload, "inline_builtin_policy.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_builtin_policy.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_builtin_policy.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_builtin_policy.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_builtin_policy.builtin_module_policy", jsonStringField(text, "builtinModulePolicy"));
    putPayload(payload, "inline_builtin_policy.allowlist_enabled", jsonBooleanField(text, "allowlistEnabled"));
    putPayload(payload, "inline_builtin_policy.allowed_builtin_count", jsonNumberField(text, "allowedBuiltinCount"));
    putPayload(payload, "inline_builtin_policy.denied_builtin_count", jsonNumberField(text, "deniedBuiltinCount"));
    putPayload(payload, "inline_builtin_policy.fs_allowed", jsonBooleanField(text, "fsAllowed"));
    putPayload(payload, "inline_builtin_policy.path_allowed", jsonBooleanField(text, "pathAllowed"));
    putPayload(payload, "inline_builtin_policy.child_process_allowed", jsonBooleanField(text, "childProcessAllowed"));
    putPayload(payload, "inline_builtin_policy.worker_threads_allowed", jsonBooleanField(text, "workerThreadsAllowed"));
    putPayload(payload, "inline_builtin_policy.http_allowed", jsonBooleanField(text, "httpAllowed"));
    putPayload(payload, "inline_builtin_policy.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_builtin_policy.require_called", jsonBooleanField(text, "requireCalled"));
    putPayload(payload, "inline_builtin_policy.real_module_resolution", jsonBooleanField(text, "realModuleResolution"));
    putPayload(payload, "inline_builtin_policy.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_builtin_policy.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_builtin_policy.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_builtin_policy.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_builtin_policy.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_builtin_policy.detail", error);
    }
}

void putInlineRequireDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_require_denied.result_text", text);
    putPayload(payload, "inline_require_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_require_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_require_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_require_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_require_denied.require_available", jsonBooleanField(text, "requireAvailable"));
    putPayload(payload, "inline_require_denied.require_called", jsonBooleanField(text, "requireCalled"));
    putPayload(payload, "inline_require_denied.require_denied", jsonBooleanField(text, "requireDenied"));
    putPayload(payload, "inline_require_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_require_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_require_denied.commonjs_resolution_attempted", jsonBooleanField(text, "commonjsResolutionAttempted"));
    putPayload(payload, "inline_require_denied.node_modules_resolution_attempted", jsonBooleanField(text, "nodeModulesResolutionAttempted"));
    putPayload(payload, "inline_require_denied.package_json_read", jsonBooleanField(text, "packageJsonRead"));
    putPayload(payload, "inline_require_denied.real_module_resolution", jsonBooleanField(text, "realModuleResolution"));
    putPayload(payload, "inline_require_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_require_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_require_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_require_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_require_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_require_denied.detail", error);
    }
}

void putInlineNpmDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_npm_denied.result_text", text);
    putPayload(payload, "inline_npm_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_npm_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_npm_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_npm_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_npm_denied.npm_available", jsonBooleanField(text, "npmAvailable"));
    putPayload(payload, "inline_npm_denied.npm_command_allowed", jsonBooleanField(text, "npmCommandAllowed"));
    putPayload(payload, "inline_npm_denied.npm_install_allowed", jsonBooleanField(text, "npmInstallAllowed"));
    putPayload(payload, "inline_npm_denied.package_manager_invoked", jsonBooleanField(text, "packageManagerInvoked"));
    putPayload(payload, "inline_npm_denied.network_used", jsonBooleanField(text, "networkUsed"));
    putPayload(payload, "inline_npm_denied.disk_write_attempted", jsonBooleanField(text, "diskWriteAttempted"));
    putPayload(payload, "inline_npm_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_npm_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_npm_denied.package_json_read", jsonBooleanField(text, "packageJsonRead"));
    putPayload(payload, "inline_npm_denied.node_modules_resolution_attempted", jsonBooleanField(text, "nodeModulesResolutionAttempted"));
    putPayload(payload, "inline_npm_denied.lifecycle_scripts_allowed", jsonBooleanField(text, "lifecycleScriptsAllowed"));
    putPayload(payload, "inline_npm_denied.native_addon_build_allowed", jsonBooleanField(text, "nativeAddonBuildAllowed"));
    putPayload(payload, "inline_npm_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_npm_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_npm_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_npm_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_npm_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_npm_denied.detail", error);
    }
}

void putInlinePackageJsonDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_package_json_denied.result_text", text);
    putPayload(payload, "inline_package_json_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_package_json_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_package_json_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_package_json_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_package_json_denied.package_json_read", jsonBooleanField(text, "packageJsonRead"));
    putPayload(payload, "inline_package_json_denied.package_json_parse_attempted", jsonBooleanField(text, "packageJsonParseAttempted"));
    putPayload(payload, "inline_package_json_denied.package_main_resolution_attempted", jsonBooleanField(text, "packageMainResolutionAttempted"));
    putPayload(payload, "inline_package_json_denied.package_exports_resolution_attempted", jsonBooleanField(text, "packageExportsResolutionAttempted"));
    putPayload(payload, "inline_package_json_denied.package_imports_resolution_attempted", jsonBooleanField(text, "packageImportsResolutionAttempted"));
    putPayload(payload, "inline_package_json_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_package_json_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_package_json_denied.disk_read_attempted", jsonBooleanField(text, "diskReadAttempted"));
    putPayload(payload, "inline_package_json_denied.real_module_resolution", jsonBooleanField(text, "realModuleResolution"));
    putPayload(payload, "inline_package_json_denied.node_modules_resolution_attempted", jsonBooleanField(text, "nodeModulesResolutionAttempted"));
    putPayload(payload, "inline_package_json_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_package_json_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_package_json_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_package_json_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_package_json_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_package_json_denied.detail", error);
    }
}

void putInlineNodeModulesDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_node_modules_denied.result_text", text);
    putPayload(payload, "inline_node_modules_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_node_modules_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_node_modules_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_node_modules_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_node_modules_denied.node_modules_resolution_attempted", jsonBooleanField(text, "nodeModulesResolutionAttempted"));
    putPayload(payload, "inline_node_modules_denied.node_modules_scan_attempted", jsonBooleanField(text, "nodeModulesScanAttempted"));
    putPayload(payload, "inline_node_modules_denied.package_json_read", jsonBooleanField(text, "packageJsonRead"));
    putPayload(payload, "inline_node_modules_denied.disk_read_attempted", jsonBooleanField(text, "diskReadAttempted"));
    putPayload(payload, "inline_node_modules_denied.real_module_resolution", jsonBooleanField(text, "realModuleResolution"));
    putPayload(payload, "inline_node_modules_denied.lookup_paths_count", jsonNumberField(text, "lookupPathsCount"));
    putPayload(payload, "inline_node_modules_denied.lookup_paths_generated", jsonBooleanField(text, "lookupPathsGenerated"));
    putPayload(payload, "inline_node_modules_denied.lookup_cache_used", jsonBooleanField(text, "lookupCacheUsed"));
    putPayload(payload, "inline_node_modules_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_node_modules_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_node_modules_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_node_modules_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_node_modules_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_node_modules_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_node_modules_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_node_modules_denied.detail", error);
    }
}

void putInlineNativeAddonDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_native_addon_denied.result_text", text);
    putPayload(payload, "inline_native_addon_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_native_addon_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_native_addon_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_native_addon_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_native_addon_denied.native_addon_allowed", jsonBooleanField(text, "nativeAddonAllowed"));
    putPayload(payload, "inline_native_addon_denied.node_file_load_attempted", jsonBooleanField(text, "nodeFileLoadAttempted"));
    putPayload(payload, "inline_native_addon_denied.dlopen_attempted", jsonBooleanField(text, "dlopenAttempted"));
    putPayload(payload, "inline_native_addon_denied.symbol_resolution_attempted", jsonBooleanField(text, "symbolResolutionAttempted"));
    putPayload(payload, "inline_native_addon_denied.native_addon_build_allowed", jsonBooleanField(text, "nativeAddonBuildAllowed"));
    putPayload(payload, "inline_native_addon_denied.disk_read_attempted", jsonBooleanField(text, "diskReadAttempted"));
    putPayload(payload, "inline_native_addon_denied.disk_write_attempted", jsonBooleanField(text, "diskWriteAttempted"));
    putPayload(payload, "inline_native_addon_denied.real_module_resolution", jsonBooleanField(text, "realModuleResolution"));
    putPayload(payload, "inline_native_addon_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_native_addon_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_native_addon_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_native_addon_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_native_addon_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_native_addon_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_native_addon_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_native_addon_denied.detail", error);
    }
}

void putInlineEsmDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_esm_denied.result_text", text);
    putPayload(payload, "inline_esm_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_esm_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_esm_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_esm_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_esm_denied.esm_loader_enabled", jsonBooleanField(text, "esmLoaderEnabled"));
    putPayload(payload, "inline_esm_denied.static_import_allowed", jsonBooleanField(text, "staticImportAllowed"));
    putPayload(payload, "inline_esm_denied.import_map_used", jsonBooleanField(text, "importMapUsed"));
    putPayload(payload, "inline_esm_denied.package_type_module_read", jsonBooleanField(text, "packageTypeModuleRead"));
    putPayload(payload, "inline_esm_denied.package_json_read", jsonBooleanField(text, "packageJsonRead"));
    putPayload(payload, "inline_esm_denied.module_graph_created", jsonBooleanField(text, "moduleGraphCreated"));
    putPayload(payload, "inline_esm_denied.real_module_resolution", jsonBooleanField(text, "realModuleResolution"));
    putPayload(payload, "inline_esm_denied.disk_read_attempted", jsonBooleanField(text, "diskReadAttempted"));
    putPayload(payload, "inline_esm_denied.network_used", jsonBooleanField(text, "networkUsed"));
    putPayload(payload, "inline_esm_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_esm_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_esm_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_esm_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_esm_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_esm_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_esm_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_esm_denied.detail", error);
    }
}

void putInlineDynamicImportDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_dynamic_import_denied.result_text", text);
    putPayload(payload, "inline_dynamic_import_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_dynamic_import_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_dynamic_import_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_dynamic_import_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_dynamic_import_denied.dynamic_import_allowed", jsonBooleanField(text, "dynamicImportAllowed"));
    putPayload(payload, "inline_dynamic_import_denied.import_called", jsonBooleanField(text, "importCalled"));
    putPayload(payload, "inline_dynamic_import_denied.import_promise_created", jsonBooleanField(text, "importPromiseCreated"));
    putPayload(payload, "inline_dynamic_import_denied.module_resolution_attempted", jsonBooleanField(text, "moduleResolutionAttempted"));
    putPayload(payload, "inline_dynamic_import_denied.module_graph_created", jsonBooleanField(text, "moduleGraphCreated"));
    putPayload(payload, "inline_dynamic_import_denied.package_json_read", jsonBooleanField(text, "packageJsonRead"));
    putPayload(payload, "inline_dynamic_import_denied.node_modules_resolution_attempted", jsonBooleanField(text, "nodeModulesResolutionAttempted"));
    putPayload(payload, "inline_dynamic_import_denied.disk_read_attempted", jsonBooleanField(text, "diskReadAttempted"));
    putPayload(payload, "inline_dynamic_import_denied.network_used", jsonBooleanField(text, "networkUsed"));
    putPayload(payload, "inline_dynamic_import_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_dynamic_import_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_dynamic_import_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_dynamic_import_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_dynamic_import_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_dynamic_import_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_dynamic_import_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_dynamic_import_denied.detail", error);
    }
}

void putInlineWorkerDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_worker_denied.result_text", text);
    putPayload(payload, "inline_worker_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_worker_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_worker_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_worker_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_worker_denied.worker_threads_allowed", jsonBooleanField(text, "workerThreadsAllowed"));
    putPayload(payload, "inline_worker_denied.worker_created", jsonBooleanField(text, "workerCreated"));
    putPayload(payload, "inline_worker_denied.worker_bootstrap_attempted", jsonBooleanField(text, "workerBootstrapAttempted"));
    putPayload(payload, "inline_worker_denied.thread_created", jsonBooleanField(text, "threadCreated"));
    putPayload(payload, "inline_worker_denied.message_port_created", jsonBooleanField(text, "messagePortCreated"));
    putPayload(payload, "inline_worker_denied.shared_array_buffer_allowed", jsonBooleanField(text, "sharedArrayBufferAllowed"));
    putPayload(payload, "inline_worker_denied.native_thread_spawned", jsonBooleanField(text, "nativeThreadSpawned"));
    putPayload(payload, "inline_worker_denied.real_module_resolution", jsonBooleanField(text, "realModuleResolution"));
    putPayload(payload, "inline_worker_denied.disk_read_attempted", jsonBooleanField(text, "diskReadAttempted"));
    putPayload(payload, "inline_worker_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_worker_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_worker_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_worker_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_worker_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_worker_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_worker_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_worker_denied.detail", error);
    }
}

void putInlineCapabilitySummaryFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_capability_summary.result_text", text);
    putPayload(payload, "inline_capability_summary.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_capability_summary.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_capability_summary.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_capability_summary.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_capability_summary.constant_execution_supported", jsonBooleanField(text, "constantExecutionSupported"));
    putPayload(payload, "inline_capability_summary.return_value_supported", jsonBooleanField(text, "returnValueSupported"));
    putPayload(payload, "inline_capability_summary.error_capture_supported", jsonBooleanField(text, "errorCaptureSupported"));
    putPayload(payload, "inline_capability_summary.stdout_capture_supported", jsonBooleanField(text, "stdoutCaptureSupported"));
    putPayload(payload, "inline_capability_summary.stderr_capture_supported", jsonBooleanField(text, "stderrCaptureSupported"));
    putPayload(payload, "inline_capability_summary.async_completion_supported", jsonBooleanField(text, "asyncCompletionSupported"));
    putPayload(payload, "inline_capability_summary.cancel_envelope_supported", jsonBooleanField(text, "cancelEnvelopeSupported"));
    putPayload(payload, "inline_capability_summary.timeout_envelope_supported", jsonBooleanField(text, "timeoutEnvelopeSupported"));
    putPayload(payload, "inline_capability_summary.fresh_process_required", jsonBooleanField(text, "freshProcessRequired"));
    putPayload(payload, "inline_capability_summary.require_supported", jsonBooleanField(text, "requireSupported"));
    putPayload(payload, "inline_capability_summary.npm_supported", jsonBooleanField(text, "npmSupported"));
    putPayload(payload, "inline_capability_summary.package_json_resolution_supported", jsonBooleanField(text, "packageJsonResolutionSupported"));
    putPayload(payload, "inline_capability_summary.node_modules_resolution_supported", jsonBooleanField(text, "nodeModulesResolutionSupported"));
    putPayload(payload, "inline_capability_summary.native_addon_supported", jsonBooleanField(text, "nativeAddonSupported"));
    putPayload(payload, "inline_capability_summary.esm_supported", jsonBooleanField(text, "esmSupported"));
    putPayload(payload, "inline_capability_summary.dynamic_import_supported", jsonBooleanField(text, "dynamicImportSupported"));
    putPayload(payload, "inline_capability_summary.worker_threads_supported", jsonBooleanField(text, "workerThreadsSupported"));
    putPayload(payload, "inline_capability_summary.user_file_execution_supported", jsonBooleanField(text, "userFileExecutionSupported"));
    putPayload(payload, "inline_capability_summary.dynamic_source_supported", jsonBooleanField(text, "dynamicSourceSupported"));
    putPayload(payload, "inline_capability_summary.autojs_api_supported", jsonBooleanField(text, "autojsApiSupported"));
    putPayload(payload, "inline_capability_summary.android_bridge_supported", jsonBooleanField(text, "androidBridgeSupported"));
    putPayload(payload, "inline_capability_summary.auto_backend_supported", jsonBooleanField(text, "autoBackendSupported"));
    putPayload(payload, "inline_capability_summary.ready_for_controlled_inline_execution", jsonBooleanField(text, "readyForControlledInlineExecution"));
    putPayload(payload, "inline_capability_summary.ready_for_user_script_execution", jsonBooleanField(text, "readyForUserScriptExecution"));
    putPayload(payload, "inline_capability_summary.ready_for_module_loading", jsonBooleanField(text, "readyForModuleLoading"));
    putPayload(payload, "inline_capability_summary.ready_for_autojs_bridge", jsonBooleanField(text, "readyForAutojsBridge"));
    putPayload(payload, "inline_capability_summary.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_capability_summary.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_capability_summary.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_capability_summary.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_capability_summary.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_capability_summary.detail", error);
    }
}

void putInlineFilesystemDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_filesystem_denied.result_text", text);
    putPayload(payload, "inline_filesystem_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_filesystem_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_filesystem_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_filesystem_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_filesystem_denied.fs_access_allowed", jsonBooleanField(text, "fsAccessAllowed"));
    putPayload(payload, "inline_filesystem_denied.fs_module_loaded", jsonBooleanField(text, "fsModuleLoaded"));
    putPayload(payload, "inline_filesystem_denied.file_read_attempted", jsonBooleanField(text, "fileReadAttempted"));
    putPayload(payload, "inline_filesystem_denied.file_write_attempted", jsonBooleanField(text, "fileWriteAttempted"));
    putPayload(payload, "inline_filesystem_denied.directory_scan_attempted", jsonBooleanField(text, "directoryScanAttempted"));
    putPayload(payload, "inline_filesystem_denied.realpath_attempted", jsonBooleanField(text, "realpathAttempted"));
    putPayload(payload, "inline_filesystem_denied.disk_read_attempted", jsonBooleanField(text, "diskReadAttempted"));
    putPayload(payload, "inline_filesystem_denied.disk_write_attempted", jsonBooleanField(text, "diskWriteAttempted"));
    putPayload(payload, "inline_filesystem_denied.real_module_resolution", jsonBooleanField(text, "realModuleResolution"));
    putPayload(payload, "inline_filesystem_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_filesystem_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_filesystem_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_filesystem_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_filesystem_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_filesystem_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_filesystem_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_filesystem_denied.detail", error);
    }
}

void putInlineChildProcessDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_child_process_denied.result_text", text);
    putPayload(payload, "inline_child_process_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_child_process_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_child_process_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_child_process_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_child_process_denied.child_process_allowed", jsonBooleanField(text, "childProcessAllowed"));
    putPayload(payload, "inline_child_process_denied.child_process_module_loaded", jsonBooleanField(text, "childProcessModuleLoaded"));
    putPayload(payload, "inline_child_process_denied.spawn_attempted", jsonBooleanField(text, "spawnAttempted"));
    putPayload(payload, "inline_child_process_denied.exec_attempted", jsonBooleanField(text, "execAttempted"));
    putPayload(payload, "inline_child_process_denied.fork_attempted", jsonBooleanField(text, "forkAttempted"));
    putPayload(payload, "inline_child_process_denied.process_created", jsonBooleanField(text, "processCreated"));
    putPayload(payload, "inline_child_process_denied.shell_invoked", jsonBooleanField(text, "shellInvoked"));
    putPayload(payload, "inline_child_process_denied.native_process_started", jsonBooleanField(text, "nativeProcessStarted"));
    putPayload(payload, "inline_child_process_denied.real_module_resolution", jsonBooleanField(text, "realModuleResolution"));
    putPayload(payload, "inline_child_process_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_child_process_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_child_process_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_child_process_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_child_process_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_child_process_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_child_process_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_child_process_denied.detail", error);
    }
}

void putInlineNetworkDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_network_denied.result_text", text);
    putPayload(payload, "inline_network_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_network_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_network_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_network_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_network_denied.network_allowed", jsonBooleanField(text, "networkAllowed"));
    putPayload(payload, "inline_network_denied.http_module_loaded", jsonBooleanField(text, "httpModuleLoaded"));
    putPayload(payload, "inline_network_denied.https_module_loaded", jsonBooleanField(text, "httpsModuleLoaded"));
    putPayload(payload, "inline_network_denied.net_module_loaded", jsonBooleanField(text, "netModuleLoaded"));
    putPayload(payload, "inline_network_denied.dns_module_loaded", jsonBooleanField(text, "dnsModuleLoaded"));
    putPayload(payload, "inline_network_denied.socket_created", jsonBooleanField(text, "socketCreated"));
    putPayload(payload, "inline_network_denied.connection_attempted", jsonBooleanField(text, "connectionAttempted"));
    putPayload(payload, "inline_network_denied.dns_lookup_attempted", jsonBooleanField(text, "dnsLookupAttempted"));
    putPayload(payload, "inline_network_denied.request_sent", jsonBooleanField(text, "requestSent"));
    putPayload(payload, "inline_network_denied.network_used", jsonBooleanField(text, "networkUsed"));
    putPayload(payload, "inline_network_denied.real_module_resolution", jsonBooleanField(text, "realModuleResolution"));
    putPayload(payload, "inline_network_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_network_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_network_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_network_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_network_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_network_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_network_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_network_denied.detail", error);
    }
}

void putInlinePermissionsDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_permissions_denied.result_text", text);
    putPayload(payload, "inline_permissions_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_permissions_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_permissions_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_permissions_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_permissions_denied.permission_bridge_allowed", jsonBooleanField(text, "permissionBridgeAllowed"));
    putPayload(payload, "inline_permissions_denied.android_permission_query_allowed", jsonBooleanField(text, "androidPermissionQueryAllowed"));
    putPayload(payload, "inline_permissions_denied.runtime_permission_request_allowed", jsonBooleanField(text, "runtimePermissionRequestAllowed"));
    putPayload(payload, "inline_permissions_denied.special_permission_request_allowed", jsonBooleanField(text, "specialPermissionRequestAllowed"));
    putPayload(payload, "inline_permissions_denied.accessibility_permission_bridge_allowed", jsonBooleanField(text, "accessibilityPermissionBridgeAllowed"));
    putPayload(payload, "inline_permissions_denied.notification_permission_bridge_allowed", jsonBooleanField(text, "notificationPermissionBridgeAllowed"));
    putPayload(payload, "inline_permissions_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_permissions_denied.binder_called", jsonBooleanField(text, "binderCalled"));
    putPayload(payload, "inline_permissions_denied.activity_started", jsonBooleanField(text, "activityStarted"));
    putPayload(payload, "inline_permissions_denied.permission_dialog_shown", jsonBooleanField(text, "permissionDialogShown"));
    putPayload(payload, "inline_permissions_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_permissions_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_permissions_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_permissions_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_permissions_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_permissions_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_permissions_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_permissions_denied.detail", error);
    }
}

void putInlineAndroidBridgeDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_android_bridge_denied.result_text", text);
    putPayload(payload, "inline_android_bridge_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_android_bridge_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_android_bridge_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_android_bridge_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_android_bridge_denied.android_bridge_allowed", jsonBooleanField(text, "androidBridgeAllowed"));
    putPayload(payload, "inline_android_bridge_denied.context_injected", jsonBooleanField(text, "contextInjected"));
    putPayload(payload, "inline_android_bridge_denied.activity_injected", jsonBooleanField(text, "activityInjected"));
    putPayload(payload, "inline_android_bridge_denied.application_injected", jsonBooleanField(text, "applicationInjected"));
    putPayload(payload, "inline_android_bridge_denied.jvm_bridge_attached", jsonBooleanField(text, "jvmBridgeAttached"));
    putPayload(payload, "inline_android_bridge_denied.jni_bridge_attached", jsonBooleanField(text, "jniBridgeAttached"));
    putPayload(payload, "inline_android_bridge_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_android_bridge_denied.binder_called", jsonBooleanField(text, "binderCalled"));
    putPayload(payload, "inline_android_bridge_denied.looper_used", jsonBooleanField(text, "looperUsed"));
    putPayload(payload, "inline_android_bridge_denied.ui_thread_dispatch_attempted", jsonBooleanField(text, "uiThreadDispatchAttempted"));
    putPayload(payload, "inline_android_bridge_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_android_bridge_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_android_bridge_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_android_bridge_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_android_bridge_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_android_bridge_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_android_bridge_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_android_bridge_denied.detail", error);
    }
}

void putInlineAutoJsApiDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_autojs_api_denied.result_text", text);
    putPayload(payload, "inline_autojs_api_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_autojs_api_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_autojs_api_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_autojs_api_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_autojs_api_denied.autojs_api_allowed", jsonBooleanField(text, "autojsApiAllowed"));
    putPayload(payload, "inline_autojs_api_denied.global_auto_injected", jsonBooleanField(text, "globalAutoInjected"));
    putPayload(payload, "inline_autojs_api_denied.global_files_injected", jsonBooleanField(text, "globalFilesInjected"));
    putPayload(payload, "inline_autojs_api_denied.global_device_injected", jsonBooleanField(text, "globalDeviceInjected"));
    putPayload(payload, "inline_autojs_api_denied.global_app_injected", jsonBooleanField(text, "globalAppInjected"));
    putPayload(payload, "inline_autojs_api_denied.global_console_injected", jsonBooleanField(text, "globalConsoleInjected"));
    putPayload(payload, "inline_autojs_api_denied.global_images_injected", jsonBooleanField(text, "globalImagesInjected"));
    putPayload(payload, "inline_autojs_api_denied.rhino_bridge_used", jsonBooleanField(text, "rhinoBridgeUsed"));
    putPayload(payload, "inline_autojs_api_denied.android_bridge_used", jsonBooleanField(text, "androidBridgeUsed"));
    putPayload(payload, "inline_autojs_api_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_autojs_api_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_autojs_api_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_autojs_api_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_autojs_api_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_autojs_api_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_autojs_api_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_autojs_api_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_autojs_api_denied.detail", error);
    }
}

void putInlineConsoleBridgeDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_console_bridge_denied.result_text", text);
    putPayload(payload, "inline_console_bridge_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_console_bridge_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_console_bridge_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_console_bridge_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_console_bridge_denied.console_bridge_allowed", jsonBooleanField(text, "consoleBridgeAllowed"));
    putPayload(payload, "inline_console_bridge_denied.autojs_console_connected", jsonBooleanField(text, "autojsConsoleConnected"));
    putPayload(payload, "inline_console_bridge_denied.host_console_sink_attached", jsonBooleanField(text, "hostConsoleSinkAttached"));
    putPayload(payload, "inline_console_bridge_denied.log_event_forwarded", jsonBooleanField(text, "logEventForwarded"));
    putPayload(payload, "inline_console_bridge_denied.stdout_forwarded_to_autojs", jsonBooleanField(text, "stdoutForwardedToAutoJs"));
    putPayload(payload, "inline_console_bridge_denied.stderr_forwarded_to_autojs", jsonBooleanField(text, "stderrForwardedToAutoJs"));
    putPayload(payload, "inline_console_bridge_denied.console_global_replaced", jsonBooleanField(text, "consoleGlobalReplaced"));
    putPayload(payload, "inline_console_bridge_denied.console_proxy_installed", jsonBooleanField(text, "consoleProxyInstalled"));
    putPayload(payload, "inline_console_bridge_denied.json_socket_used", jsonBooleanField(text, "jsonSocketUsed"));
    putPayload(payload, "inline_console_bridge_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_console_bridge_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_console_bridge_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_console_bridge_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_console_bridge_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_console_bridge_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_console_bridge_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_console_bridge_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_console_bridge_denied.detail", error);
    }
}

void putInlineJsonSocketDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_json_socket_denied.result_text", text);
    putPayload(payload, "inline_json_socket_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_json_socket_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_json_socket_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_json_socket_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_json_socket_denied.json_socket_allowed", jsonBooleanField(text, "jsonSocketAllowed"));
    putPayload(payload, "inline_json_socket_denied.socket_created", jsonBooleanField(text, "socketCreated"));
    putPayload(payload, "inline_json_socket_denied.host_connected", jsonBooleanField(text, "hostConnected"));
    putPayload(payload, "inline_json_socket_denied.message_sent", jsonBooleanField(text, "messageSent"));
    putPayload(payload, "inline_json_socket_denied.message_received", jsonBooleanField(text, "messageReceived"));
    putPayload(payload, "inline_json_socket_denied.debug_protocol_enabled", jsonBooleanField(text, "debugProtocolEnabled"));
    putPayload(payload, "inline_json_socket_denied.console_protocol_enabled", jsonBooleanField(text, "consoleProtocolEnabled"));
    putPayload(payload, "inline_json_socket_denied.remote_command_enabled", jsonBooleanField(text, "remoteCommandEnabled"));
    putPayload(payload, "inline_json_socket_denied.network_used", jsonBooleanField(text, "networkUsed"));
    putPayload(payload, "inline_json_socket_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_json_socket_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_json_socket_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_json_socket_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_json_socket_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_json_socket_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_json_socket_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_json_socket_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_json_socket_denied.detail", error);
    }
}

void putInlineBinderDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_binder_denied.result_text", text);
    putPayload(payload, "inline_binder_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_binder_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_binder_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_binder_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_binder_denied.binder_bridge_allowed", jsonBooleanField(text, "binderBridgeAllowed"));
    putPayload(payload, "inline_binder_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_binder_denied.aidl_interface_bound", jsonBooleanField(text, "aidlInterfaceBound"));
    putPayload(payload, "inline_binder_denied.service_bound", jsonBooleanField(text, "serviceBound"));
    putPayload(payload, "inline_binder_denied.ipc_transaction_attempted", jsonBooleanField(text, "ipcTransactionAttempted"));
    putPayload(payload, "inline_binder_denied.main_process_connected", jsonBooleanField(text, "mainProcessConnected"));
    putPayload(payload, "inline_binder_denied.result_receiver_used", jsonBooleanField(text, "resultReceiverUsed"));
    putPayload(payload, "inline_binder_denied.parcel_created", jsonBooleanField(text, "parcelCreated"));
    putPayload(payload, "inline_binder_denied.remote_exception_observed", jsonBooleanField(text, "remoteExceptionObserved"));
    putPayload(payload, "inline_binder_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_binder_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_binder_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_binder_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_binder_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_binder_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_binder_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_binder_denied.detail", error);
    }
}

void putInlineUiBridgeDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_ui_bridge_denied.result_text", text);
    putPayload(payload, "inline_ui_bridge_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_ui_bridge_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_ui_bridge_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_ui_bridge_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_ui_bridge_denied.ui_bridge_allowed", jsonBooleanField(text, "uiBridgeAllowed"));
    putPayload(payload, "inline_ui_bridge_denied.ui_global_injected", jsonBooleanField(text, "uiGlobalInjected"));
    putPayload(payload, "inline_ui_bridge_denied.activity_required", jsonBooleanField(text, "activityRequired"));
    putPayload(payload, "inline_ui_bridge_denied.activity_accessed", jsonBooleanField(text, "activityAccessed"));
    putPayload(payload, "inline_ui_bridge_denied.view_created", jsonBooleanField(text, "viewCreated"));
    putPayload(payload, "inline_ui_bridge_denied.layout_inflated", jsonBooleanField(text, "layoutInflated"));
    putPayload(payload, "inline_ui_bridge_denied.ui_thread_dispatch_attempted", jsonBooleanField(text, "uiThreadDispatchAttempted"));
    putPayload(payload, "inline_ui_bridge_denied.looper_used", jsonBooleanField(text, "looperUsed"));
    putPayload(payload, "inline_ui_bridge_denied.android_bridge_used", jsonBooleanField(text, "androidBridgeUsed"));
    putPayload(payload, "inline_ui_bridge_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_ui_bridge_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_ui_bridge_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_ui_bridge_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_ui_bridge_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_ui_bridge_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_ui_bridge_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_ui_bridge_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_ui_bridge_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_ui_bridge_denied.detail", error);
    }
}

void putInlineAccessibilityDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_accessibility_denied.result_text", text);
    putPayload(payload, "inline_accessibility_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_accessibility_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_accessibility_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_accessibility_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_accessibility_denied.accessibility_bridge_allowed", jsonBooleanField(text, "accessibilityBridgeAllowed"));
    putPayload(payload, "inline_accessibility_denied.accessibility_service_accessed", jsonBooleanField(text, "accessibilityServiceAccessed"));
    putPayload(payload, "inline_accessibility_denied.accessibility_state_queried", jsonBooleanField(text, "accessibilityStateQueried"));
    putPayload(payload, "inline_accessibility_denied.node_query_attempted", jsonBooleanField(text, "nodeQueryAttempted"));
    putPayload(payload, "inline_accessibility_denied.selector_engine_used", jsonBooleanField(text, "selectorEngineUsed"));
    putPayload(payload, "inline_accessibility_denied.gesture_dispatch_attempted", jsonBooleanField(text, "gestureDispatchAttempted"));
    putPayload(payload, "inline_accessibility_denied.ui_automation_used", jsonBooleanField(text, "uiAutomationUsed"));
    putPayload(payload, "inline_accessibility_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_accessibility_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_accessibility_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_accessibility_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_accessibility_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_accessibility_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_accessibility_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_accessibility_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_accessibility_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_accessibility_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_accessibility_denied.detail", error);
    }
}

void putInlineImagesDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_images_denied.result_text", text);
    putPayload(payload, "inline_images_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_images_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_images_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_images_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_images_denied.images_bridge_allowed", jsonBooleanField(text, "imagesBridgeAllowed"));
    putPayload(payload, "inline_images_denied.images_global_injected", jsonBooleanField(text, "imagesGlobalInjected"));
    putPayload(payload, "inline_images_denied.bitmap_created", jsonBooleanField(text, "bitmapCreated"));
    putPayload(payload, "inline_images_denied.image_wrapper_created", jsonBooleanField(text, "imageWrapperCreated"));
    putPayload(payload, "inline_images_denied.screen_capture_attempted", jsonBooleanField(text, "screenCaptureAttempted"));
    putPayload(payload, "inline_images_denied.image_io_attempted", jsonBooleanField(text, "imageIoAttempted"));
    putPayload(payload, "inline_images_denied.opencv_used", jsonBooleanField(text, "opencvUsed"));
    putPayload(payload, "inline_images_denied.mlkit_used", jsonBooleanField(text, "mlkitUsed"));
    putPayload(payload, "inline_images_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_images_denied.native_image_library_loaded", jsonBooleanField(text, "nativeImageLibraryLoaded"));
    putPayload(payload, "inline_images_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_images_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_images_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_images_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_images_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_images_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_images_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_images_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_images_denied.detail", error);
    }
}
void putInlineDialogsDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_dialogs_denied.result_text", text);
    putPayload(payload, "inline_dialogs_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_dialogs_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_dialogs_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_dialogs_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_dialogs_denied.dialogs_bridge_allowed", jsonBooleanField(text, "dialogsBridgeAllowed"));
    putPayload(payload, "inline_dialogs_denied.dialogs_global_injected", jsonBooleanField(text, "dialogsGlobalInjected"));
    putPayload(payload, "inline_dialogs_denied.alert_attempted", jsonBooleanField(text, "alertAttempted"));
    putPayload(payload, "inline_dialogs_denied.confirm_attempted", jsonBooleanField(text, "confirmAttempted"));
    putPayload(payload, "inline_dialogs_denied.prompt_attempted", jsonBooleanField(text, "promptAttempted"));
    putPayload(payload, "inline_dialogs_denied.dialog_created", jsonBooleanField(text, "dialogCreated"));
    putPayload(payload, "inline_dialogs_denied.activity_required", jsonBooleanField(text, "activityRequired"));
    putPayload(payload, "inline_dialogs_denied.activity_accessed", jsonBooleanField(text, "activityAccessed"));
    putPayload(payload, "inline_dialogs_denied.ui_thread_dispatch_attempted", jsonBooleanField(text, "uiThreadDispatchAttempted"));
    putPayload(payload, "inline_dialogs_denied.android_bridge_used", jsonBooleanField(text, "androidBridgeUsed"));
    putPayload(payload, "inline_dialogs_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_dialogs_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_dialogs_denied.policy", jsonStringField(text, "policy"));
    putPayload(payload, "inline_dialogs_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_dialogs_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_dialogs_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_dialogs_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_dialogs_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_dialogs_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_dialogs_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_dialogs_denied.detail", error);
    }
}

void putInlineSensorsDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_sensors_denied.result_text", text);
    putPayload(payload, "inline_sensors_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_sensors_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_sensors_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_sensors_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_sensors_denied.sensors_bridge_allowed", jsonBooleanField(text, "sensorsBridgeAllowed"));
    putPayload(payload, "inline_sensors_denied.sensors_global_injected", jsonBooleanField(text, "sensorsGlobalInjected"));
    putPayload(payload, "inline_sensors_denied.sensor_manager_accessed", jsonBooleanField(text, "sensorManagerAccessed"));
    putPayload(payload, "inline_sensors_denied.sensor_listener_registered", jsonBooleanField(text, "sensorListenerRegistered"));
    putPayload(payload, "inline_sensors_denied.accelerometer_accessed", jsonBooleanField(text, "accelerometerAccessed"));
    putPayload(payload, "inline_sensors_denied.gyroscope_accessed", jsonBooleanField(text, "gyroscopeAccessed"));
    putPayload(payload, "inline_sensors_denied.orientation_accessed", jsonBooleanField(text, "orientationAccessed"));
    putPayload(payload, "inline_sensors_denied.location_accessed", jsonBooleanField(text, "locationAccessed"));
    putPayload(payload, "inline_sensors_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_sensors_denied.permission_requested", jsonBooleanField(text, "permissionRequested"));
    putPayload(payload, "inline_sensors_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_sensors_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_sensors_denied.policy", jsonStringField(text, "policy"));
    putPayload(payload, "inline_sensors_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_sensors_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_sensors_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_sensors_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_sensors_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_sensors_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_sensors_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_sensors_denied.detail", error);
    }
}

void putInlineMediaCameraDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_media_camera_denied.result_text", text);
    putPayload(payload, "inline_media_camera_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_media_camera_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_media_camera_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_media_camera_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_media_camera_denied.media_camera_bridge_allowed", jsonBooleanField(text, "mediaCameraBridgeAllowed"));
    putPayload(payload, "inline_media_camera_denied.media_global_injected", jsonBooleanField(text, "mediaGlobalInjected"));
    putPayload(payload, "inline_media_camera_denied.camera_global_injected", jsonBooleanField(text, "cameraGlobalInjected"));
    putPayload(payload, "inline_media_camera_denied.camera_open_attempted", jsonBooleanField(text, "cameraOpenAttempted"));
    putPayload(payload, "inline_media_camera_denied.camera_device_created", jsonBooleanField(text, "cameraDeviceCreated"));
    putPayload(payload, "inline_media_camera_denied.media_recorder_created", jsonBooleanField(text, "mediaRecorderCreated"));
    putPayload(payload, "inline_media_camera_denied.audio_record_created", jsonBooleanField(text, "audioRecordCreated"));
    putPayload(payload, "inline_media_camera_denied.microphone_accessed", jsonBooleanField(text, "microphoneAccessed"));
    putPayload(payload, "inline_media_camera_denied.camera_permission_requested", jsonBooleanField(text, "cameraPermissionRequested"));
    putPayload(payload, "inline_media_camera_denied.record_audio_permission_requested", jsonBooleanField(text, "recordAudioPermissionRequested"));
    putPayload(payload, "inline_media_camera_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_media_camera_denied.native_media_library_loaded", jsonBooleanField(text, "nativeMediaLibraryLoaded"));
    putPayload(payload, "inline_media_camera_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_media_camera_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_media_camera_denied.policy", jsonStringField(text, "policy"));
    putPayload(payload, "inline_media_camera_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_media_camera_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_media_camera_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_media_camera_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_media_camera_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_media_camera_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_media_camera_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_media_camera_denied.detail", error);
    }
}

void putInlineStorageDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_storage_denied.result_text", text);
    putPayload(payload, "inline_storage_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_storage_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_storage_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_storage_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_storage_denied.storage_bridge_allowed", jsonBooleanField(text, "storageBridgeAllowed"));
    putPayload(payload, "inline_storage_denied.storage_global_injected", jsonBooleanField(text, "storageGlobalInjected"));
    putPayload(payload, "inline_storage_denied.shared_preferences_accessed", jsonBooleanField(text, "sharedPreferencesAccessed"));
    putPayload(payload, "inline_storage_denied.sqlite_accessed", jsonBooleanField(text, "sqliteAccessed"));
    putPayload(payload, "inline_storage_denied.file_storage_accessed", jsonBooleanField(text, "fileStorageAccessed"));
    putPayload(payload, "inline_storage_denied.kv_storage_accessed", jsonBooleanField(text, "kvStorageAccessed"));
    putPayload(payload, "inline_storage_denied.database_opened", jsonBooleanField(text, "databaseOpened"));
    putPayload(payload, "inline_storage_denied.transaction_started", jsonBooleanField(text, "transactionStarted"));
    putPayload(payload, "inline_storage_denied.android_context_accessed", jsonBooleanField(text, "androidContextAccessed"));
    putPayload(payload, "inline_storage_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_storage_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_storage_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_storage_denied.policy", jsonStringField(text, "policy"));
    putPayload(payload, "inline_storage_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_storage_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_storage_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_storage_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_storage_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_storage_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_storage_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_storage_denied.detail", error);
    }
}

void putInlineShellDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_shell_denied.result_text", text);
    putPayload(payload, "inline_shell_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_shell_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_shell_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_shell_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_shell_denied.shell_bridge_allowed", jsonBooleanField(text, "shellBridgeAllowed"));
    putPayload(payload, "inline_shell_denied.shell_global_injected", jsonBooleanField(text, "shellGlobalInjected"));
    putPayload(payload, "inline_shell_denied.shell_command_attempted", jsonBooleanField(text, "shellCommandAttempted"));
    putPayload(payload, "inline_shell_denied.root_command_attempted", jsonBooleanField(text, "rootCommandAttempted"));
    putPayload(payload, "inline_shell_denied.su_requested", jsonBooleanField(text, "suRequested"));
    putPayload(payload, "inline_shell_denied.process_spawn_attempted", jsonBooleanField(text, "processSpawnAttempted"));
    putPayload(payload, "inline_shell_denied.runtime_exec_attempted", jsonBooleanField(text, "runtimeExecAttempted"));
    putPayload(payload, "inline_shell_denied.pty_created", jsonBooleanField(text, "ptyCreated"));
    putPayload(payload, "inline_shell_denied.stdin_written", jsonBooleanField(text, "stdinWritten"));
    putPayload(payload, "inline_shell_denied.stdout_read", jsonBooleanField(text, "stdoutRead"));
    putPayload(payload, "inline_shell_denied.stderr_read", jsonBooleanField(text, "stderrRead"));
    putPayload(payload, "inline_shell_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_shell_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_shell_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_shell_denied.policy", jsonStringField(text, "policy"));
    putPayload(payload, "inline_shell_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_shell_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_shell_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_shell_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_shell_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_shell_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_shell_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_shell_denied.detail", error);
    }
}

void putInlineNotificationDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_notification_denied.result_text", text);
    putPayload(payload, "inline_notification_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_notification_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_notification_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_notification_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_notification_denied.notification_bridge_allowed", jsonBooleanField(text, "notificationBridgeAllowed"));
    putPayload(payload, "inline_notification_denied.notification_global_injected", jsonBooleanField(text, "notificationGlobalInjected"));
    putPayload(payload, "inline_notification_denied.notification_manager_accessed", jsonBooleanField(text, "notificationManagerAccessed"));
    putPayload(payload, "inline_notification_denied.notification_channel_created", jsonBooleanField(text, "notificationChannelCreated"));
    putPayload(payload, "inline_notification_denied.notification_built", jsonBooleanField(text, "notificationBuilt"));
    putPayload(payload, "inline_notification_denied.notification_posted", jsonBooleanField(text, "notificationPosted"));
    putPayload(payload, "inline_notification_denied.notification_cancelled", jsonBooleanField(text, "notificationCancelled"));
    putPayload(payload, "inline_notification_denied.pending_intent_created", jsonBooleanField(text, "pendingIntentCreated"));
    putPayload(payload, "inline_notification_denied.android_context_accessed", jsonBooleanField(text, "androidContextAccessed"));
    putPayload(payload, "inline_notification_denied.permission_requested", jsonBooleanField(text, "permissionRequested"));
    putPayload(payload, "inline_notification_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_notification_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_notification_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_notification_denied.policy", jsonStringField(text, "policy"));
    putPayload(payload, "inline_notification_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_notification_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_notification_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_notification_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_notification_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_notification_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_notification_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_notification_denied.detail", error);
    }
}

void putInlineClipboardDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_clipboard_denied.result_text", text);
    putPayload(payload, "inline_clipboard_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_clipboard_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_clipboard_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_clipboard_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_clipboard_denied.clipboard_bridge_allowed", jsonBooleanField(text, "clipboardBridgeAllowed"));
    putPayload(payload, "inline_clipboard_denied.clipboard_global_injected", jsonBooleanField(text, "clipboardGlobalInjected"));
    putPayload(payload, "inline_clipboard_denied.clipboard_manager_accessed", jsonBooleanField(text, "clipboardManagerAccessed"));
    putPayload(payload, "inline_clipboard_denied.clip_read_attempted", jsonBooleanField(text, "clipReadAttempted"));
    putPayload(payload, "inline_clipboard_denied.clip_write_attempted", jsonBooleanField(text, "clipWriteAttempted"));
    putPayload(payload, "inline_clipboard_denied.primary_clip_read", jsonBooleanField(text, "primaryClipRead"));
    putPayload(payload, "inline_clipboard_denied.primary_clip_set", jsonBooleanField(text, "primaryClipSet"));
    putPayload(payload, "inline_clipboard_denied.android_context_accessed", jsonBooleanField(text, "androidContextAccessed"));
    putPayload(payload, "inline_clipboard_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_clipboard_denied.permission_requested", jsonBooleanField(text, "permissionRequested"));
    putPayload(payload, "inline_clipboard_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_clipboard_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_clipboard_denied.policy", jsonStringField(text, "policy"));
    putPayload(payload, "inline_clipboard_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_clipboard_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_clipboard_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_clipboard_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_clipboard_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_clipboard_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_clipboard_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_clipboard_denied.detail", error);
    }
}

void putInlineDeviceInfoDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_device_info_denied.result_text", text);
    putPayload(payload, "inline_device_info_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_device_info_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_device_info_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_device_info_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_device_info_denied.device_info_bridge_allowed", jsonBooleanField(text, "deviceInfoBridgeAllowed"));
    putPayload(payload, "inline_device_info_denied.device_global_injected", jsonBooleanField(text, "deviceGlobalInjected"));
    putPayload(payload, "inline_device_info_denied.system_service_accessed", jsonBooleanField(text, "systemServiceAccessed"));
    putPayload(payload, "inline_device_info_denied.build_info_accessed", jsonBooleanField(text, "buildInfoAccessed"));
    putPayload(payload, "inline_device_info_denied.display_metrics_accessed", jsonBooleanField(text, "displayMetricsAccessed"));
    putPayload(payload, "inline_device_info_denied.battery_state_queried", jsonBooleanField(text, "batteryStateQueried"));
    putPayload(payload, "inline_device_info_denied.network_state_queried", jsonBooleanField(text, "networkStateQueried"));
    putPayload(payload, "inline_device_info_denied.telephony_info_queried", jsonBooleanField(text, "telephonyInfoQueried"));
    putPayload(payload, "inline_device_info_denied.settings_secure_accessed", jsonBooleanField(text, "settingsSecureAccessed"));
    putPayload(payload, "inline_device_info_denied.android_context_accessed", jsonBooleanField(text, "androidContextAccessed"));
    putPayload(payload, "inline_device_info_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_device_info_denied.permission_requested", jsonBooleanField(text, "permissionRequested"));
    putPayload(payload, "inline_device_info_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_device_info_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_device_info_denied.policy", jsonStringField(text, "policy"));
    putPayload(payload, "inline_device_info_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_device_info_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_device_info_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_device_info_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_device_info_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_device_info_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_device_info_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_device_info_denied.detail", error);
    }
}

void putInlineVibrationDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_vibration_denied.result_text", text);
    putPayload(payload, "inline_vibration_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_vibration_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_vibration_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_vibration_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_vibration_denied.vibration_bridge_allowed", jsonBooleanField(text, "vibrationBridgeAllowed"));
    putPayload(payload, "inline_vibration_denied.vibrator_global_injected", jsonBooleanField(text, "vibratorGlobalInjected"));
    putPayload(payload, "inline_vibration_denied.vibrator_service_accessed", jsonBooleanField(text, "vibratorServiceAccessed"));
    putPayload(payload, "inline_vibration_denied.vibrate_attempted", jsonBooleanField(text, "vibrateAttempted"));
    putPayload(payload, "inline_vibration_denied.vibration_effect_created", jsonBooleanField(text, "vibrationEffectCreated"));
    putPayload(payload, "inline_vibration_denied.cancel_attempted", jsonBooleanField(text, "cancelAttempted"));
    putPayload(payload, "inline_vibration_denied.android_context_accessed", jsonBooleanField(text, "androidContextAccessed"));
    putPayload(payload, "inline_vibration_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_vibration_denied.permission_requested", jsonBooleanField(text, "permissionRequested"));
    putPayload(payload, "inline_vibration_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_vibration_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_vibration_denied.policy", jsonStringField(text, "policy"));
    putPayload(payload, "inline_vibration_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_vibration_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_vibration_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_vibration_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_vibration_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_vibration_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_vibration_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_vibration_denied.detail", error);
    }
}

void putInlineToastDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_toast_denied.result_text", text);
    putPayload(payload, "inline_toast_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_toast_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_toast_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_toast_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_toast_denied.toast_bridge_allowed", jsonBooleanField(text, "toastBridgeAllowed"));
    putPayload(payload, "inline_toast_denied.toast_global_injected", jsonBooleanField(text, "toastGlobalInjected"));
    putPayload(payload, "inline_toast_denied.toast_attempted", jsonBooleanField(text, "toastAttempted"));
    putPayload(payload, "inline_toast_denied.toast_object_created", jsonBooleanField(text, "toastObjectCreated"));
    putPayload(payload, "inline_toast_denied.toast_shown", jsonBooleanField(text, "toastShown"));
    putPayload(payload, "inline_toast_denied.activity_required", jsonBooleanField(text, "activityRequired"));
    putPayload(payload, "inline_toast_denied.android_context_accessed", jsonBooleanField(text, "androidContextAccessed"));
    putPayload(payload, "inline_toast_denied.ui_thread_dispatch_attempted", jsonBooleanField(text, "uiThreadDispatchAttempted"));
    putPayload(payload, "inline_toast_denied.looper_used", jsonBooleanField(text, "looperUsed"));
    putPayload(payload, "inline_toast_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_toast_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_toast_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_toast_denied.policy", jsonStringField(text, "policy"));
    putPayload(payload, "inline_toast_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_toast_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_toast_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_toast_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_toast_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_toast_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_toast_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_toast_denied.detail", error);
    }
}

void putInlineFloatyDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_floaty_denied.result_text", text);
    putPayload(payload, "inline_floaty_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_floaty_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_floaty_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_floaty_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_floaty_denied.floaty_bridge_allowed", jsonBooleanField(text, "floatyBridgeAllowed"));
    putPayload(payload, "inline_floaty_denied.floaty_global_injected", jsonBooleanField(text, "floatyGlobalInjected"));
    putPayload(payload, "inline_floaty_denied.window_manager_accessed", jsonBooleanField(text, "windowManagerAccessed"));
    putPayload(payload, "inline_floaty_denied.overlay_permission_checked", jsonBooleanField(text, "overlayPermissionChecked"));
    putPayload(payload, "inline_floaty_denied.overlay_permission_requested", jsonBooleanField(text, "overlayPermissionRequested"));
    putPayload(payload, "inline_floaty_denied.float_window_created", jsonBooleanField(text, "floatWindowCreated"));
    putPayload(payload, "inline_floaty_denied.view_created", jsonBooleanField(text, "viewCreated"));
    putPayload(payload, "inline_floaty_denied.layout_inflated", jsonBooleanField(text, "layoutInflated"));
    putPayload(payload, "inline_floaty_denied.window_added", jsonBooleanField(text, "windowAdded"));
    putPayload(payload, "inline_floaty_denied.window_removed", jsonBooleanField(text, "windowRemoved"));
    putPayload(payload, "inline_floaty_denied.ui_thread_dispatch_attempted", jsonBooleanField(text, "uiThreadDispatchAttempted"));
    putPayload(payload, "inline_floaty_denied.android_context_accessed", jsonBooleanField(text, "androidContextAccessed"));
    putPayload(payload, "inline_floaty_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_floaty_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_floaty_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_floaty_denied.policy", jsonStringField(text, "policy"));
    putPayload(payload, "inline_floaty_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_floaty_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_floaty_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_floaty_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_floaty_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_floaty_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_floaty_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_floaty_denied.detail", error);
    }
}

void putInlineEventsDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_events_denied.result_text", text);
    putPayload(payload, "inline_events_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_events_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_events_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_events_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_events_denied.events_bridge_allowed", jsonBooleanField(text, "eventsBridgeAllowed"));
    putPayload(payload, "inline_events_denied.events_global_injected", jsonBooleanField(text, "eventsGlobalInjected"));
    putPayload(payload, "inline_events_denied.event_emitter_injected", jsonBooleanField(text, "eventEmitterInjected"));
    putPayload(payload, "inline_events_denied.listener_registered", jsonBooleanField(text, "listenerRegistered"));
    putPayload(payload, "inline_events_denied.broadcast_receiver_registered", jsonBooleanField(text, "broadcastReceiverRegistered"));
    putPayload(payload, "inline_events_denied.key_observer_registered", jsonBooleanField(text, "keyObserverRegistered"));
    putPayload(payload, "inline_events_denied.touch_observer_registered", jsonBooleanField(text, "touchObserverRegistered"));
    putPayload(payload, "inline_events_denied.notification_listener_accessed", jsonBooleanField(text, "notificationListenerAccessed"));
    putPayload(payload, "inline_events_denied.accessibility_event_observed", jsonBooleanField(text, "accessibilityEventObserved"));
    putPayload(payload, "inline_events_denied.sensor_listener_registered", jsonBooleanField(text, "sensorListenerRegistered"));
    putPayload(payload, "inline_events_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_events_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_events_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_events_denied.policy", jsonStringField(text, "policy"));
    putPayload(payload, "inline_events_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_events_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_events_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_events_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_events_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_events_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_events_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_events_denied.detail", error);
    }
}

void putInlineThreadsDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_threads_denied.result_text", text);
    putPayload(payload, "inline_threads_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_threads_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_threads_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_threads_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_threads_denied.threads_bridge_allowed", jsonBooleanField(text, "threadsBridgeAllowed"));
    putPayload(payload, "inline_threads_denied.threads_global_injected", jsonBooleanField(text, "threadsGlobalInjected"));
    putPayload(payload, "inline_threads_denied.thread_started", jsonBooleanField(text, "threadStarted"));
    putPayload(payload, "inline_threads_denied.thread_join_attempted", jsonBooleanField(text, "threadJoinAttempted"));
    putPayload(payload, "inline_threads_denied.thread_interrupted", jsonBooleanField(text, "threadInterrupted"));
    putPayload(payload, "inline_threads_denied.thread_pool_created", jsonBooleanField(text, "threadPoolCreated"));
    putPayload(payload, "inline_threads_denied.handler_thread_created", jsonBooleanField(text, "handlerThreadCreated"));
    putPayload(payload, "inline_threads_denied.java_thread_created", jsonBooleanField(text, "javaThreadCreated"));
    putPayload(payload, "inline_threads_denied.android_looper_used", jsonBooleanField(text, "androidLooperUsed"));
    putPayload(payload, "inline_threads_denied.android_handler_created", jsonBooleanField(text, "androidHandlerCreated"));
    putPayload(payload, "inline_threads_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_threads_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_threads_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_threads_denied.policy", jsonStringField(text, "policy"));
    putPayload(payload, "inline_threads_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_threads_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_threads_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_threads_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_threads_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_threads_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_threads_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_threads_denied.detail", error);
    }
}

void putInlineTimersDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_timers_denied.result_text", text);
    putPayload(payload, "inline_timers_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_timers_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_timers_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_timers_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_timers_denied.timers_bridge_allowed", jsonBooleanField(text, "timersBridgeAllowed"));
    putPayload(payload, "inline_timers_denied.timers_global_injected", jsonBooleanField(text, "timersGlobalInjected"));
    putPayload(payload, "inline_timers_denied.set_timeout_available", jsonBooleanField(text, "setTimeoutAvailable"));
    putPayload(payload, "inline_timers_denied.clear_timeout_available", jsonBooleanField(text, "clearTimeoutAvailable"));
    putPayload(payload, "inline_timers_denied.set_interval_available", jsonBooleanField(text, "setIntervalAvailable"));
    putPayload(payload, "inline_timers_denied.clear_interval_available", jsonBooleanField(text, "clearIntervalAvailable"));
    putPayload(payload, "inline_timers_denied.timeout_scheduled", jsonBooleanField(text, "timeoutScheduled"));
    putPayload(payload, "inline_timers_denied.interval_scheduled", jsonBooleanField(text, "intervalScheduled"));
    putPayload(payload, "inline_timers_denied.timer_callback_invoked", jsonBooleanField(text, "timerCallbackInvoked"));
    putPayload(payload, "inline_timers_denied.android_handler_used", jsonBooleanField(text, "androidHandlerUsed"));
    putPayload(payload, "inline_timers_denied.libuv_timer_created", jsonBooleanField(text, "libuvTimerCreated"));
    putPayload(payload, "inline_timers_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_timers_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_timers_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_timers_denied.policy", jsonStringField(text, "policy"));
    putPayload(payload, "inline_timers_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_timers_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_timers_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_timers_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_timers_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_timers_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_timers_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_timers_denied.detail", error);
    }
}

void putInlineWebViewDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_webview_denied.result_text", text);
    putPayload(payload, "inline_webview_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_webview_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_webview_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_webview_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_webview_denied.webview_bridge_allowed", jsonBooleanField(text, "webviewBridgeAllowed"));
    putPayload(payload, "inline_webview_denied.webview_global_injected", jsonBooleanField(text, "webviewGlobalInjected"));
    putPayload(payload, "inline_webview_denied.webview_created", jsonBooleanField(text, "webviewCreated"));
    putPayload(payload, "inline_webview_denied.webview_context_required", jsonBooleanField(text, "webviewContextRequired"));
    putPayload(payload, "inline_webview_denied.webview_settings_accessed", jsonBooleanField(text, "webviewSettingsAccessed"));
    putPayload(payload, "inline_webview_denied.javascript_interface_added", jsonBooleanField(text, "javascriptInterfaceAdded"));
    putPayload(payload, "inline_webview_denied.url_loaded", jsonBooleanField(text, "urlLoaded"));
    putPayload(payload, "inline_webview_denied.html_loaded", jsonBooleanField(text, "htmlLoaded"));
    putPayload(payload, "inline_webview_denied.webview_client_set", jsonBooleanField(text, "webviewClientSet"));
    putPayload(payload, "inline_webview_denied.chrome_client_set", jsonBooleanField(text, "chromeClientSet"));
    putPayload(payload, "inline_webview_denied.activity_accessed", jsonBooleanField(text, "activityAccessed"));
    putPayload(payload, "inline_webview_denied.ui_thread_dispatch_attempted", jsonBooleanField(text, "uiThreadDispatchAttempted"));
    putPayload(payload, "inline_webview_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_webview_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_webview_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_webview_denied.policy", jsonStringField(text, "policy"));
    putPayload(payload, "inline_webview_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_webview_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_webview_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_webview_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_webview_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_webview_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_webview_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_webview_denied.detail", error);
    }
}

void putInlineHttpDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_http_denied.result_text", text);
    putPayload(payload, "inline_http_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_http_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_http_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_http_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_http_denied.http_bridge_allowed", jsonBooleanField(text, "httpBridgeAllowed"));
    putPayload(payload, "inline_http_denied.http_global_injected", jsonBooleanField(text, "httpGlobalInjected"));
    putPayload(payload, "inline_http_denied.http_request_attempted", jsonBooleanField(text, "httpRequestAttempted"));
    putPayload(payload, "inline_http_denied.http_get_attempted", jsonBooleanField(text, "httpGetAttempted"));
    putPayload(payload, "inline_http_denied.http_post_attempted", jsonBooleanField(text, "httpPostAttempted"));
    putPayload(payload, "inline_http_denied.http_client_created", jsonBooleanField(text, "httpClientCreated"));
    putPayload(payload, "inline_http_denied.connection_opened", jsonBooleanField(text, "connectionOpened"));
    putPayload(payload, "inline_http_denied.socket_created", jsonBooleanField(text, "socketCreated"));
    putPayload(payload, "inline_http_denied.dns_lookup_attempted", jsonBooleanField(text, "dnsLookupAttempted"));
    putPayload(payload, "inline_http_denied.request_body_written", jsonBooleanField(text, "requestBodyWritten"));
    putPayload(payload, "inline_http_denied.response_read", jsonBooleanField(text, "responseRead"));
    putPayload(payload, "inline_http_denied.network_permission_requested", jsonBooleanField(text, "networkPermissionRequested"));
    putPayload(payload, "inline_http_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_http_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_http_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_http_denied.policy", jsonStringField(text, "policy"));
    putPayload(payload, "inline_http_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_http_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_http_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_http_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_http_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_http_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_http_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_http_denied.detail", error);
    }
}

void putInlineCryptoDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_crypto_denied.result_text", text);
    putPayload(payload, "inline_crypto_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_crypto_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_crypto_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_crypto_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_crypto_denied.crypto_bridge_allowed", jsonBooleanField(text, "cryptoBridgeAllowed"));
    putPayload(payload, "inline_crypto_denied.crypto_global_injected", jsonBooleanField(text, "cryptoGlobalInjected"));
    putPayload(payload, "inline_crypto_denied.java_crypto_accessed", jsonBooleanField(text, "javaCryptoAccessed"));
    putPayload(payload, "inline_crypto_denied.keystore_accessed", jsonBooleanField(text, "keystoreAccessed"));
    putPayload(payload, "inline_crypto_denied.message_digest_created", jsonBooleanField(text, "messageDigestCreated"));
    putPayload(payload, "inline_crypto_denied.cipher_created", jsonBooleanField(text, "cipherCreated"));
    putPayload(payload, "inline_crypto_denied.mac_created", jsonBooleanField(text, "macCreated"));
    putPayload(payload, "inline_crypto_denied.key_generated", jsonBooleanField(text, "keyGenerated"));
    putPayload(payload, "inline_crypto_denied.secure_random_created", jsonBooleanField(text, "secureRandomCreated"));
    putPayload(payload, "inline_crypto_denied.native_crypto_library_loaded", jsonBooleanField(text, "nativeCryptoLibraryLoaded"));
    putPayload(payload, "inline_crypto_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_crypto_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_crypto_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_crypto_denied.policy", jsonStringField(text, "policy"));
    putPayload(payload, "inline_crypto_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_crypto_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_crypto_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_crypto_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_crypto_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_crypto_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_crypto_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_crypto_denied.detail", error);
    }
}

void putInlineOcrDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_ocr_denied.result_text", text);
    putPayload(payload, "inline_ocr_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_ocr_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_ocr_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_ocr_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_ocr_denied.ocr_bridge_allowed", jsonBooleanField(text, "ocrBridgeAllowed"));
    putPayload(payload, "inline_ocr_denied.ocr_global_injected", jsonBooleanField(text, "ocrGlobalInjected"));
    putPayload(payload, "inline_ocr_denied.ocr_engine_created", jsonBooleanField(text, "ocrEngineCreated"));
    putPayload(payload, "inline_ocr_denied.ocr_model_loaded", jsonBooleanField(text, "ocrModelLoaded"));
    putPayload(payload, "inline_ocr_denied.bitmap_required", jsonBooleanField(text, "bitmapRequired"));
    putPayload(payload, "inline_ocr_denied.bitmap_created", jsonBooleanField(text, "bitmapCreated"));
    putPayload(payload, "inline_ocr_denied.screen_capture_attempted", jsonBooleanField(text, "screenCaptureAttempted"));
    putPayload(payload, "inline_ocr_denied.predictor_initialized", jsonBooleanField(text, "predictorInitialized"));
    putPayload(payload, "inline_ocr_denied.recognize_text_attempted", jsonBooleanField(text, "recognizeTextAttempted"));
    putPayload(payload, "inline_ocr_denied.detect_attempted", jsonBooleanField(text, "detectAttempted"));
    putPayload(payload, "inline_ocr_denied.native_ocr_invoked", jsonBooleanField(text, "nativeOcrInvoked"));
    putPayload(payload, "inline_ocr_denied.native_library_loaded", jsonBooleanField(text, "nativeLibraryLoaded"));
    putPayload(payload, "inline_ocr_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_ocr_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_ocr_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_ocr_denied.policy", jsonStringField(text, "policy"));
    putPayload(payload, "inline_ocr_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_ocr_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_ocr_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_ocr_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_ocr_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_ocr_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_ocr_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_ocr_denied.detail", error);
    }
}

void putInlineMlAiDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_ml_ai_denied.result_text", text);
    putPayload(payload, "inline_ml_ai_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_ml_ai_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_ml_ai_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_ml_ai_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_ml_ai_denied.ml_ai_bridge_allowed", jsonBooleanField(text, "mlAiBridgeAllowed"));
    putPayload(payload, "inline_ml_ai_denied.ml_ai_global_injected", jsonBooleanField(text, "mlAiGlobalInjected"));
    putPayload(payload, "inline_ml_ai_denied.model_loaded", jsonBooleanField(text, "modelLoaded"));
    putPayload(payload, "inline_ml_ai_denied.inference_engine_created", jsonBooleanField(text, "inferenceEngineCreated"));
    putPayload(payload, "inline_ml_ai_denied.inference_attempted", jsonBooleanField(text, "inferenceAttempted"));
    putPayload(payload, "inline_ml_ai_denied.tensor_created", jsonBooleanField(text, "tensorCreated"));
    putPayload(payload, "inline_ml_ai_denied.accelerator_used", jsonBooleanField(text, "acceleratorUsed"));
    putPayload(payload, "inline_ml_ai_denied.gpu_delegate_used", jsonBooleanField(text, "gpuDelegateUsed"));
    putPayload(payload, "inline_ml_ai_denied.nnapi_used", jsonBooleanField(text, "nnapiUsed"));
    putPayload(payload, "inline_ml_ai_denied.native_ml_library_loaded", jsonBooleanField(text, "nativeMlLibraryLoaded"));
    putPayload(payload, "inline_ml_ai_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_ml_ai_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_ml_ai_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_ml_ai_denied.policy", jsonStringField(text, "policy"));
    putPayload(payload, "inline_ml_ai_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_ml_ai_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_ml_ai_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_ml_ai_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_ml_ai_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_ml_ai_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_ml_ai_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_ml_ai_denied.detail", error);
    }
}

void putInlineWebSocketDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_websocket_denied.result_text", text);
    putPayload(payload, "inline_websocket_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_websocket_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_websocket_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_websocket_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_websocket_denied.websocket_bridge_allowed", jsonBooleanField(text, "websocketBridgeAllowed"));
    putPayload(payload, "inline_websocket_denied.websocket_global_injected", jsonBooleanField(text, "websocketGlobalInjected"));
    putPayload(payload, "inline_websocket_denied.websocket_created", jsonBooleanField(text, "websocketCreated"));
    putPayload(payload, "inline_websocket_denied.connection_attempted", jsonBooleanField(text, "connectionAttempted"));
    putPayload(payload, "inline_websocket_denied.handshake_attempted", jsonBooleanField(text, "handshakeAttempted"));
    putPayload(payload, "inline_websocket_denied.socket_created", jsonBooleanField(text, "socketCreated"));
    putPayload(payload, "inline_websocket_denied.dns_lookup_attempted", jsonBooleanField(text, "dnsLookupAttempted"));
    putPayload(payload, "inline_websocket_denied.message_sent", jsonBooleanField(text, "messageSent"));
    putPayload(payload, "inline_websocket_denied.message_received", jsonBooleanField(text, "messageReceived"));
    putPayload(payload, "inline_websocket_denied.connection_closed", jsonBooleanField(text, "connectionClosed"));
    putPayload(payload, "inline_websocket_denied.network_permission_requested", jsonBooleanField(text, "networkPermissionRequested"));
    putPayload(payload, "inline_websocket_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_websocket_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_websocket_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_websocket_denied.policy", jsonStringField(text, "policy"));
    putPayload(payload, "inline_websocket_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_websocket_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_websocket_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_websocket_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_websocket_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_websocket_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_websocket_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_websocket_denied.detail", error);
    }
}

void putInlineBluetoothDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_bluetooth_denied.result_text", text);
    putPayload(payload, "inline_bluetooth_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_bluetooth_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_bluetooth_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_bluetooth_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_bluetooth_denied.bluetooth_bridge_allowed", jsonBooleanField(text, "bluetoothBridgeAllowed"));
    putPayload(payload, "inline_bluetooth_denied.bluetooth_global_injected", jsonBooleanField(text, "bluetoothGlobalInjected"));
    putPayload(payload, "inline_bluetooth_denied.bluetooth_adapter_accessed", jsonBooleanField(text, "bluetoothAdapterAccessed"));
    putPayload(payload, "inline_bluetooth_denied.bluetooth_manager_accessed", jsonBooleanField(text, "bluetoothManagerAccessed"));
    putPayload(payload, "inline_bluetooth_denied.scan_attempted", jsonBooleanField(text, "scanAttempted"));
    putPayload(payload, "inline_bluetooth_denied.device_pairing_attempted", jsonBooleanField(text, "devicePairingAttempted"));
    putPayload(payload, "inline_bluetooth_denied.gatt_connection_attempted", jsonBooleanField(text, "gattConnectionAttempted"));
    putPayload(payload, "inline_bluetooth_denied.socket_connection_attempted", jsonBooleanField(text, "socketConnectionAttempted"));
    putPayload(payload, "inline_bluetooth_denied.permission_requested", jsonBooleanField(text, "permissionRequested"));
    putPayload(payload, "inline_bluetooth_denied.android_context_accessed", jsonBooleanField(text, "androidContextAccessed"));
    putPayload(payload, "inline_bluetooth_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_bluetooth_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_bluetooth_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_bluetooth_denied.policy", jsonStringField(text, "policy"));
    putPayload(payload, "inline_bluetooth_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_bluetooth_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_bluetooth_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_bluetooth_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_bluetooth_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_bluetooth_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_bluetooth_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_bluetooth_denied.detail", error);
    }
}

void putInlineNfcDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_nfc_denied.result_text", text);
    putPayload(payload, "inline_nfc_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_nfc_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_nfc_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_nfc_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_nfc_denied.nfc_bridge_allowed", jsonBooleanField(text, "nfcBridgeAllowed"));
    putPayload(payload, "inline_nfc_denied.nfc_global_injected", jsonBooleanField(text, "nfcGlobalInjected"));
    putPayload(payload, "inline_nfc_denied.nfc_adapter_accessed", jsonBooleanField(text, "nfcAdapterAccessed"));
    putPayload(payload, "inline_nfc_denied.nfc_manager_accessed", jsonBooleanField(text, "nfcManagerAccessed"));
    putPayload(payload, "inline_nfc_denied.tag_scan_attempted", jsonBooleanField(text, "tagScanAttempted"));
    putPayload(payload, "inline_nfc_denied.ndef_read_attempted", jsonBooleanField(text, "ndefReadAttempted"));
    putPayload(payload, "inline_nfc_denied.ndef_write_attempted", jsonBooleanField(text, "ndefWriteAttempted"));
    putPayload(payload, "inline_nfc_denied.foreground_dispatch_enabled", jsonBooleanField(text, "foregroundDispatchEnabled"));
    putPayload(payload, "inline_nfc_denied.permission_requested", jsonBooleanField(text, "permissionRequested"));
    putPayload(payload, "inline_nfc_denied.android_context_accessed", jsonBooleanField(text, "androidContextAccessed"));
    putPayload(payload, "inline_nfc_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_nfc_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_nfc_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_nfc_denied.policy", jsonStringField(text, "policy"));
    putPayload(payload, "inline_nfc_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_nfc_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_nfc_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_nfc_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_nfc_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_nfc_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_nfc_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_nfc_denied.detail", error);
    }
}

void putInlineUsbDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_usb_denied.result_text", text);
    putPayload(payload, "inline_usb_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_usb_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_usb_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_usb_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_usb_denied.usb_bridge_allowed", jsonBooleanField(text, "usbBridgeAllowed"));
    putPayload(payload, "inline_usb_denied.usb_global_injected", jsonBooleanField(text, "usbGlobalInjected"));
    putPayload(payload, "inline_usb_denied.usb_manager_accessed", jsonBooleanField(text, "usbManagerAccessed"));
    putPayload(payload, "inline_usb_denied.device_list_queried", jsonBooleanField(text, "deviceListQueried"));
    putPayload(payload, "inline_usb_denied.device_open_attempted", jsonBooleanField(text, "deviceOpenAttempted"));
    putPayload(payload, "inline_usb_denied.interface_claim_attempted", jsonBooleanField(text, "interfaceClaimAttempted"));
    putPayload(payload, "inline_usb_denied.endpoint_accessed", jsonBooleanField(text, "endpointAccessed"));
    putPayload(payload, "inline_usb_denied.bulk_transfer_attempted", jsonBooleanField(text, "bulkTransferAttempted"));
    putPayload(payload, "inline_usb_denied.permission_requested", jsonBooleanField(text, "permissionRequested"));
    putPayload(payload, "inline_usb_denied.android_context_accessed", jsonBooleanField(text, "androidContextAccessed"));
    putPayload(payload, "inline_usb_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_usb_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_usb_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_usb_denied.policy", jsonStringField(text, "policy"));
    putPayload(payload, "inline_usb_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_usb_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_usb_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_usb_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_usb_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_usb_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_usb_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_usb_denied.detail", error);
    }
}

void putInlineLocationDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_location_denied.result_text", text);
    putPayload(payload, "inline_location_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_location_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_location_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_location_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_location_denied.location_bridge_allowed", jsonBooleanField(text, "locationBridgeAllowed"));
    putPayload(payload, "inline_location_denied.location_global_injected", jsonBooleanField(text, "locationGlobalInjected"));
    putPayload(payload, "inline_location_denied.location_manager_accessed", jsonBooleanField(text, "locationManagerAccessed"));
    putPayload(payload, "inline_location_denied.fused_location_accessed", jsonBooleanField(text, "fusedLocationAccessed"));
    putPayload(payload, "inline_location_denied.last_location_queried", jsonBooleanField(text, "lastLocationQueried"));
    putPayload(payload, "inline_location_denied.location_updates_requested", jsonBooleanField(text, "locationUpdatesRequested"));
    putPayload(payload, "inline_location_denied.gps_provider_used", jsonBooleanField(text, "gpsProviderUsed"));
    putPayload(payload, "inline_location_denied.network_provider_used", jsonBooleanField(text, "networkProviderUsed"));
    putPayload(payload, "inline_location_denied.permission_requested", jsonBooleanField(text, "permissionRequested"));
    putPayload(payload, "inline_location_denied.android_context_accessed", jsonBooleanField(text, "androidContextAccessed"));
    putPayload(payload, "inline_location_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_location_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_location_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_location_denied.policy", jsonStringField(text, "policy"));
    putPayload(payload, "inline_location_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_location_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_location_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_location_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_location_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_location_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_location_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_location_denied.detail", error);
    }
}

void putInlineContactsDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_contacts_denied.result_text", text);
    putPayload(payload, "inline_contacts_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_contacts_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_contacts_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_contacts_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_contacts_denied.contacts_bridge_allowed", jsonBooleanField(text, "contactsBridgeAllowed"));
    putPayload(payload, "inline_contacts_denied.contacts_global_injected", jsonBooleanField(text, "contactsGlobalInjected"));
    putPayload(payload, "inline_contacts_denied.content_resolver_accessed", jsonBooleanField(text, "contentResolverAccessed"));
    putPayload(payload, "inline_contacts_denied.contacts_provider_queried", jsonBooleanField(text, "contactsProviderQueried"));
    putPayload(payload, "inline_contacts_denied.contact_read_attempted", jsonBooleanField(text, "contactReadAttempted"));
    putPayload(payload, "inline_contacts_denied.contact_write_attempted", jsonBooleanField(text, "contactWriteAttempted"));
    putPayload(payload, "inline_contacts_denied.cursor_opened", jsonBooleanField(text, "cursorOpened"));
    putPayload(payload, "inline_contacts_denied.permission_requested", jsonBooleanField(text, "permissionRequested"));
    putPayload(payload, "inline_contacts_denied.android_context_accessed", jsonBooleanField(text, "androidContextAccessed"));
    putPayload(payload, "inline_contacts_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_contacts_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_contacts_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_contacts_denied.policy", jsonStringField(text, "policy"));
    putPayload(payload, "inline_contacts_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_contacts_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_contacts_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_contacts_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_contacts_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_contacts_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_contacts_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_contacts_denied.detail", error);
    }
}

void putInlineCalendarDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_calendar_denied.result_text", text);
    putPayload(payload, "inline_calendar_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_calendar_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_calendar_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_calendar_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_calendar_denied.calendar_bridge_allowed", jsonBooleanField(text, "calendarBridgeAllowed"));
    putPayload(payload, "inline_calendar_denied.calendar_global_injected", jsonBooleanField(text, "calendarGlobalInjected"));
    putPayload(payload, "inline_calendar_denied.content_resolver_accessed", jsonBooleanField(text, "contentResolverAccessed"));
    putPayload(payload, "inline_calendar_denied.calendar_provider_queried", jsonBooleanField(text, "calendarProviderQueried"));
    putPayload(payload, "inline_calendar_denied.event_read_attempted", jsonBooleanField(text, "eventReadAttempted"));
    putPayload(payload, "inline_calendar_denied.event_write_attempted", jsonBooleanField(text, "eventWriteAttempted"));
    putPayload(payload, "inline_calendar_denied.reminder_accessed", jsonBooleanField(text, "reminderAccessed"));
    putPayload(payload, "inline_calendar_denied.cursor_opened", jsonBooleanField(text, "cursorOpened"));
    putPayload(payload, "inline_calendar_denied.permission_requested", jsonBooleanField(text, "permissionRequested"));
    putPayload(payload, "inline_calendar_denied.android_context_accessed", jsonBooleanField(text, "androidContextAccessed"));
    putPayload(payload, "inline_calendar_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_calendar_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_calendar_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_calendar_denied.policy", jsonStringField(text, "policy"));
    putPayload(payload, "inline_calendar_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_calendar_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_calendar_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_calendar_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_calendar_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_calendar_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_calendar_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_calendar_denied.detail", error);
    }
}

void putInlineSmsTelephonyDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "inline_sms_telephony_denied.result_text", text);
    putPayload(payload, "inline_sms_telephony_denied.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "inline_sms_telephony_denied.source_kind", jsonStringField(text, "sourceKind"));
    putPayload(payload, "inline_sms_telephony_denied.user_source_used", jsonBooleanField(text, "userSourceUsed"));
    putPayload(payload, "inline_sms_telephony_denied.user_file_read", jsonBooleanField(text, "userFileRead"));
    putPayload(payload, "inline_sms_telephony_denied.sms_telephony_bridge_allowed", jsonBooleanField(text, "smsTelephonyBridgeAllowed"));
    putPayload(payload, "inline_sms_telephony_denied.sms_global_injected", jsonBooleanField(text, "smsGlobalInjected"));
    putPayload(payload, "inline_sms_telephony_denied.telephony_global_injected", jsonBooleanField(text, "telephonyGlobalInjected"));
    putPayload(payload, "inline_sms_telephony_denied.sms_manager_accessed", jsonBooleanField(text, "smsManagerAccessed"));
    putPayload(payload, "inline_sms_telephony_denied.telephony_manager_accessed", jsonBooleanField(text, "telephonyManagerAccessed"));
    putPayload(payload, "inline_sms_telephony_denied.sms_send_attempted", jsonBooleanField(text, "smsSendAttempted"));
    putPayload(payload, "inline_sms_telephony_denied.sms_read_attempted", jsonBooleanField(text, "smsReadAttempted"));
    putPayload(payload, "inline_sms_telephony_denied.call_state_queried", jsonBooleanField(text, "callStateQueried"));
    putPayload(payload, "inline_sms_telephony_denied.phone_number_queried", jsonBooleanField(text, "phoneNumberQueried"));
    putPayload(payload, "inline_sms_telephony_denied.permission_requested", jsonBooleanField(text, "permissionRequested"));
    putPayload(payload, "inline_sms_telephony_denied.android_context_accessed", jsonBooleanField(text, "androidContextAccessed"));
    putPayload(payload, "inline_sms_telephony_denied.android_api_called", jsonBooleanField(text, "androidApiCalled"));
    putPayload(payload, "inline_sms_telephony_denied.host_object_injected", jsonBooleanField(text, "hostObjectInjected"));
    putPayload(payload, "inline_sms_telephony_denied.binder_used", jsonBooleanField(text, "binderUsed"));
    putPayload(payload, "inline_sms_telephony_denied.policy", jsonStringField(text, "policy"));
    putPayload(payload, "inline_sms_telephony_denied.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "inline_sms_telephony_denied.denied_error_message", jsonStringField(text, "deniedErrorMessage"));
    putPayload(payload, "inline_sms_telephony_denied.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "inline_sms_telephony_denied.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "inline_sms_telephony_denied.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "inline_sms_telephony_denied.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "inline_sms_telephony_denied.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "inline_sms_telephony_denied.detail", error);
    }
}

void putInlineAccountDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_account_denied", kInlineAccountDeniedFields, text);
}

void putInlinePackageManagerDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_package_manager_denied", kInlinePackageManagerDeniedFields, text);
}

void putInlineIntentActivityDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_intent_activity_denied", kInlineIntentActivityDeniedFields, text);
}

void putInlineBroadcastDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_broadcast_denied", kInlineBroadcastDeniedFields, text);
}

void putInlineContentProviderDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_content_provider_denied", kInlineContentProviderDeniedFields, text);
}

void putInlineMediaStoreDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_media_store_denied", kInlineMediaStoreDeniedFields, text);
}

void putInlineDownloadManagerDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_download_manager_denied", kInlineDownloadManagerDeniedFields, text);
}

void putInlineInputMethodDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_input_method_denied", kInlineInputMethodDeniedFields, text);
}

void putInlineAppOpsDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_app_ops_denied", kInlineAppOpsDeniedFields, text);
}

void putInlinePermissionManagerDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_permission_manager_denied", kInlinePermissionManagerDeniedFields, text);
}

void putInlineSettingsDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_settings_denied", kInlineSettingsDeniedFields, text);
}

void putInlinePowerManagerDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_power_manager_denied", kInlinePowerManagerDeniedFields, text);
}

void putInlineKeyguardDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_keyguard_denied", kInlineKeyguardDeniedFields, text);
}

void putInlineWallpaperDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_wallpaper_denied", kInlineWallpaperDeniedFields, text);
}

void putInlineShortcutManagerDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_shortcut_manager_denied", kInlineShortcutManagerDeniedFields, text);
}

void putInlineAlarmManagerDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_alarm_manager_denied", kInlineAlarmManagerDeniedFields, text);
}

void putInlineJobSchedulerDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_job_scheduler_denied", kInlineJobSchedulerDeniedFields, text);
}

void putInlineWorkManagerDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_work_manager_denied", kInlineWorkManagerDeniedFields, text);
}

void putInlineClipboardListenerDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_clipboard_listener_denied", kInlineClipboardListenerDeniedFields, text);
}
void putInlineNotificationListenerDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_notification_listener_denied", kInlineNotificationListenerDeniedFields, text);
}
void putInlineAccessibilityControlDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_accessibility_control_denied", kInlineAccessibilityControlDeniedFields, text);
}
void putInlineDevicePolicyDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_device_policy_denied", kInlineDevicePolicyDeniedFields, text);
}
void putInlineUsageStatsDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_usage_stats_denied", kInlineUsageStatsDeniedFields, text);
}
void putInlineVpnConnectivityDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_vpn_connectivity_denied", kInlineVpnConnectivityDeniedFields, text);
}
void putInlineWifiManagerDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_wifi_manager_denied", kInlineWifiManagerDeniedFields, text);
}
void putInlineTelephonySubscriptionDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_telephony_subscription_denied", kInlineTelephonySubscriptionDeniedFields, text);
}
void putInlineCameraManagerDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_camera_manager_denied", kInlineCameraManagerDeniedFields, text);
}
void putInlineAudioManagerDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_audio_manager_denied", kInlineAudioManagerDeniedFields, text);
}
void putInlineDisplayManagerDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_display_manager_denied", kInlineDisplayManagerDeniedFields, text);
}
void putInlinePrintManagerDeniedFields(std::vector<std::string>& payload, const std::string& text) {
    putInlineDeniedFields(payload, "inline_print_manager_denied", kInlinePrintManagerDeniedFields, text);
}
void putControlledUserInlineConstantFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "controlled_user_inline_constant", kControlledUserInlineConstantFields, text);
}

void putControlledUserInlineStdoutFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "controlled_user_inline_stdout", kControlledUserInlineStdoutFields, text);
}

void putControlledUserInlineStderrFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "controlled_user_inline_stderr", kControlledUserInlineStderrFields, text);
}

void putControlledUserInlineReturnValueFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "controlled_user_inline_return_value", kControlledUserInlineReturnValueFields, text);
}

void putControlledUserInlineThrownErrorFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "controlled_user_inline_thrown_error", kControlledUserInlineThrownErrorFields, text);
}

void putControlledUserInlinePromiseFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "controlled_user_inline_promise", kControlledUserInlinePromiseFields, text);
}

void putControlledUserInlineAsyncOrderingFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "controlled_user_inline_async_ordering", kControlledUserInlineAsyncOrderingFields, text);
}

void putControlledUserInlineSummaryFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "controlled_user_inline_summary", kControlledUserInlineSummaryFields, text);
}

void putUserFileDescriptorFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "user_file_descriptor", kUserFileDescriptorFields, text);
}
void putUserFileReadPolicyFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "user_file_read_policy", kUserFileReadPolicyFields, text);
}
void putUserFilePathNormalizationFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "user_file_path_normalization", kUserFilePathNormalizationFields, text);
}
void putUserFileWorkingDirectoryFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "user_file_working_directory", kUserFileWorkingDirectoryFields, text);
}
void putUserFileSourceLoadingFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "user_file_source_loading", kUserFileSourceLoadingFields, text);
}
void putUserFileExecutionDryRunFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "user_file_execution_dry_run", kUserFileExecutionDryRunFields, text);
}
void putUserFileErrorStackFilenameFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "user_file_error_stack_filename", kUserFileErrorStackFilenameFields, text);
}
void putUserFileExecutionSummaryFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "user_file_execution_summary", kUserFileExecutionSummaryFields, text);
}
void putEmbeddedScriptRequestFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "embedded_script_request", kEmbeddedScriptRequestFields, text);
}
void putEmbeddedScriptResultFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "embedded_script_result", kEmbeddedScriptResultFields, text);
}
void putEmbeddedScriptOutputEventFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "embedded_script_output_event", kEmbeddedScriptOutputEventFields, text);
}
void putEmbeddedScriptErrorFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "embedded_script_error", kEmbeddedScriptErrorFields, text);
}
void putEmbeddedScriptTimeoutFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "embedded_script_timeout", kEmbeddedScriptTimeoutFields, text);
}
void putEmbeddedScriptCancellationFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "embedded_script_cancellation", kEmbeddedScriptCancellationFields, text);
}
void putEmbeddedScriptProcessIsolationFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "embedded_script_process_isolation", kEmbeddedScriptProcessIsolationFields, text);
}
void putEmbeddedScriptContractFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "embedded_script_contract", kEmbeddedScriptContractFields, text);
}
void putEmbeddedMvpLifecycleReadinessFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "embedded_mvp_lifecycle", kEmbeddedMvpLifecycleReadinessFields, text);
}
void putEmbeddedMvpSourceInputReadinessFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "embedded_mvp_source_input", kEmbeddedMvpSourceInputReadinessFields, text);
}
void putEmbeddedMvpOutputReadinessFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "embedded_mvp_output", kEmbeddedMvpOutputReadinessFields, text);
}
void putEmbeddedMvpErrorHandlingReadinessFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "embedded_mvp_error_handling", kEmbeddedMvpErrorHandlingReadinessFields, text);
}
void putEmbeddedMvpAsyncReadinessFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "embedded_mvp_async", kEmbeddedMvpAsyncReadinessFields, text);
}
void putEmbeddedMvpTimeoutIsolationReadinessFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "embedded_mvp_timeout_isolation", kEmbeddedMvpTimeoutIsolationReadinessFields, text);
}
void putEmbeddedMvpSecurityPolicyReadinessFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "embedded_mvp_security_policy", kEmbeddedMvpSecurityPolicyReadinessFields, text);
}
void putEmbeddedMvpGoNoGoReadinessFields(std::vector<std::string>& payload, const std::string& text) {
    putControlledUserInlineFields(payload, "embedded_mvp_go_no_go", kEmbeddedMvpGoNoGoReadinessFields, text);
}

void putUserSourceDescriptorFields(std::vector<std::string>& payload, const std::string& text) {
    putUserSourcePreflightFields(payload, "user_source_descriptor", kUserSourceDescriptorFields, text);
}
void putUserSourceSizeFields(std::vector<std::string>& payload, const std::string& text) {
    putUserSourcePreflightFields(payload, "user_source_size", kUserSourceSizeFields, text);
}
void putUserSourceEncodingFields(std::vector<std::string>& payload, const std::string& text) {
    putUserSourcePreflightFields(payload, "user_source_encoding", kUserSourceEncodingFields, text);
}
void putUserSourceNameFields(std::vector<std::string>& payload, const std::string& text) {
    putUserSourcePreflightFields(payload, "user_source_name", kUserSourceNameFields, text);
}
void putUserSourceWrapperFields(std::vector<std::string>& payload, const std::string& text) {
    putUserSourcePreflightFields(payload, "user_source_wrapper", kUserSourceWrapperFields, text);
}
void putUserSourceStrictModeFields(std::vector<std::string>& payload, const std::string& text) {
    putUserSourcePreflightFields(payload, "user_source_strict_mode", kUserSourceStrictModeFields, text);
}
void putUserSourceCapabilityFields(std::vector<std::string>& payload, const std::string& text) {
    putUserSourcePreflightFields(payload, "user_source_capability", kUserSourceCapabilityFields, text);
}
void putUserSourcePreflightFields(std::vector<std::string>& payload, const std::string& text) {
    putUserSourcePreflightFields(payload, "user_source_preflight", kUserSourcePreflightFields, text);
}

}  // namespace autojs6::node_bridge
