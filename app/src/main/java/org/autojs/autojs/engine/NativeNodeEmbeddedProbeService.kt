package org.autojs.autojs.engine

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.ResultReceiver
import android.os.SystemClock
import android.util.Log
import org.autojs.autojs.App
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.tool.ForegroundServiceCreator
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.util.ForegroundServiceUtils.FOREGROUND_SERVICE_TYPE_UNKNOWN
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.exitProcess

class NativeNodeEmbeddedProbeService : Service() {

    @Volatile
    private var exitScheduled = false
    private var foregroundServiceCreator: ForegroundServiceCreator? = null

    private val foregroundServiceType = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        else -> FOREGROUND_SERVICE_TYPE_UNKNOWN
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startProbeForeground()
        Log.i(TAG, "PROCESS STARTED processName=${currentProcessName()} pid=${Process.myPid()}")
    }

    override fun onDestroy() {
        foregroundServiceCreator?.stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundServiceCreator = null
        scheduleProcessExit(0)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_PROBE -> {
                Thread {
                    runProbe(intent, startId)
                }.apply {
                    name = "NativeNodeEmbeddedProbeService-$startId"
                    start()
                }
                START_NOT_STICKY
            }
            else -> {
                stopSelf(startId)
                START_NOT_STICKY
            }
        }
    }

    private fun runProbe(intent: Intent, startId: Int) {
        val receiver = intent.resultReceiverExtra(EXTRA_RESULT_RECEIVER)
        val probeLevel = intent.getIntExtra(EXTRA_PROBE_LEVEL, NativeNodeEmbeddedRuntimeBridge.currentProbeLevel()).coerceIn(0, 209)
        intent.applyRequestedProbeProperties()
        val startedAt = SystemClock.elapsedRealtime()
        val processName = currentProcessName()
        val pid = Process.myPid()
        Log.i(TAG, "probe.enter processName=$processName pid=$pid level=$probeLevel")
        try {
            val probeResult = if (probeLevel == NativeNodeEmbeddedRuntimeBridge.PROBE_LEVEL_KOTLIN_ROUND_TRIP) {
                runKotlinRoundTripProbe(probeLevel, processName, pid, startedAt)
            } else {
                NativeNodeEmbeddedRuntimeBridge.probeEmbeddedRuntimeInCurrentProcess(
                    context = this,
                    probeLevel = probeLevel,
                )
            }
            receiver?.send(
                RESULT_PROBE,
                Bundle().apply {
                    putBoolean(EXTRA_SUCCEEDED, probeResult.succeeded)
                    putString(EXTRA_FORMATTED_LOG, probeResult.formatForLog())
                    putString(EXTRA_ERROR_MESSAGE, probeResult.errorMessage)
                },
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Native Node embedded probe service failed.", e)
            receiver?.send(
                RESULT_PROBE,
                Bundle().apply {
                    putBoolean(EXTRA_SUCCEEDED, false)
                    putString(EXTRA_FORMATTED_LOG, "Native Node embedded probe service failed.\n${e.stackTraceToString()}")
                    putString(EXTRA_ERROR_MESSAGE, e.message ?: e.javaClass.name)
                },
            )
        } finally {
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            Log.i(TAG, "probe.exit processName=$processName pid=$pid level=$probeLevel elapsed=${elapsed}ms")
            Log.i(TAG, "Native Node embedded probe service finished in ${elapsed}ms.")
            stopSelf(startId)
            scheduleProcessExit(0)
        }
    }

    private fun runKotlinRoundTripProbe(
        probeLevel: Int,
        processName: String,
        pid: Int,
        startedAt: Long,
    ): NativeNodeEmbeddedRuntimeBridge.ProbeResult {
        val stageEvents = mutableListOf<String>()
        logStage(stageEvents, processName, pid, probeLevel, "service.roundTrip", "start")
        val errorMessage = if (NativeNodeEmbeddedRuntimeBridge.isEmbeddedProbeProcess(processName)) {
            null
        } else {
            "Embedded probe service started in unexpected process; expected suffix $EMBEDDED_PROBE_PROCESS_SUFFIX, currentProcess=$processName, pid=$pid."
        }
        logStage(stageEvents, processName, pid, probeLevel, "service.roundTrip", if (errorMessage == null) "done" else "failed")
        return NativeNodeEmbeddedRuntimeBridge.kotlinRoundTripProbeResult(
            context = this,
            probeLevel = probeLevel,
            processName = processName,
            pid = pid,
            totalDurationMs = SystemClock.elapsedRealtime() - startedAt,
            stageEvents = stageEvents,
            errorMessage = errorMessage,
        ).also { result ->
            if (result.succeeded) {
                Log.i(TAG, result.formatForLog())
            } else {
                Log.e(TAG, result.formatForLog())
            }
        }
    }

    private fun scheduleProcessExit(status: Int) {
        if (exitScheduled) {
            return
        }
        exitScheduled = true
        Handler(Looper.getMainLooper()).postDelayed({
            exitProcess(status)
        }, PROCESS_EXIT_DELAY_MS)
    }

    private fun startProbeForeground() {
        foregroundServiceCreator = ForegroundServiceCreator.Builder(this)
            .setClassName(NativeNodeEmbeddedProbeService::class.java)
            .setIntent(Intent(this, MainActivity::class.java))
            .setNotificationId(NOTIFICATION_ID)
            .setServiceName("AutoJs6 embedded Node probe")
            .setServiceDescription("Runs isolated embedded Node diagnostics")
            .setNotificationTitle("AutoJs6 embedded Node probe")
            .setNotificationContent("Running isolated embedded Node diagnostics")
            .create()
            .apply { startForeground(foregroundServiceType) }
    }

    private fun currentProcessName(): String = runCatching { App.getProcessNameCompat() }
        .getOrElse { "unknown" }

    private fun logStage(
        stageEvents: MutableList<String>,
        processName: String,
        pid: Int,
        probeLevel: Int,
        stage: String,
        state: String,
    ) {
        val event = "stage.$state=$stage"
        stageEvents += event
        Log.i(TAG, "processName=$processName pid=$pid level=$probeLevel $event")
    }

    private fun Intent.applyRequestedProbeProperties() {
        PROBE_REQUEST_PROPERTIES.forEach { (extraName, propertyName) ->
            if (getBooleanExtra(extraName, false)) {
                System.setProperty(propertyName, "true")
            }
        }
    }

    companion object {

        private const val TAG = "NativeNodeEmbeddedProbeService"
        private const val ACTION_PROBE = "org.autojs.autojs.engine.action.PROBE_NATIVE_NODE_EMBEDDED"
        private const val EXTRA_RESULT_RECEIVER = "result_receiver"
        private const val EXTRA_PROBE_LEVEL = "probe_level"
        private const val EXTRA_INITIALIZE_PROBE = "initialize_probe"
        private const val EXTRA_ISOLATE_PROBE = "isolate_probe"
        private const val EXTRA_UVLOOP_PROBE = "uvloop_probe"
        private const val EXTRA_V8_PROBE = "v8_probe"
        private const val EXTRA_UVDIAGNOSTICS_PROBE = "uvdiagnostics_probe"
        private const val EXTRA_UVCLOSE_PROBE = "uvclose_probe"
        private const val EXTRA_UVRUN_PROBE = "uvrun_probe"
        private const val EXTRA_ISOLATEDATA_PROBE = "isolatedata_probe"
        private const val EXTRA_ENVIRONMENT_PROBE = "environment_probe"
        private const val EXTRA_LOAD_ENVIRONMENT_PROBE = "loadenvironment_probe"
        private const val EXTRA_SPIN_EVENT_LOOP_PROBE = "spineventloop_probe"
        private const val EXTRA_INLINE_JS_PROBE = "inlinejs_probe"
        private const val EXTRA_PROCESS_JS_PROBE = "processjs_probe"
        private const val EXTRA_CONSOLE_JS_PROBE = "consolejs_probe"
        private const val EXTRA_STDOUT_CAPTURE_PROBE = "stdout_capture_probe"
        private const val EXTRA_JS_RESULT_PROBE = "jsresult_probe"
        private const val EXTRA_STDOUT_WRITE_PROBE = "stdoutwrite_probe"
        private const val EXTRA_CONSOLE_DIAGNOSTICS_PROBE = "consolediagnostics_probe"
        private const val EXTRA_CONSOLE_STREAM_PROBE = "consolestream_probe"
        private const val EXTRA_CONSOLE_SHAPE_PROBE = "consoleshape_probe"
        private const val EXTRA_CONSOLE_REPLACE_PROBE = "consolereplace_probe"
        private const val EXTRA_CONSOLE_FAMILY_PROBE = "consolefamily_probe"
        private const val EXTRA_CONSOLE_FORMAT_PROBE = "consoleformat_probe"
        private const val EXTRA_CONSOLE_REJECTION_PROBE = "consolerejection_probe"
        private const val EXTRA_CONSOLE_UNCAUGHT_PROBE = "consoleuncaught_probe"
        private const val EXTRA_SCHEDULING_PROBE = "scheduling_probe"
        private const val EXTRA_SCHEDULING_ORDER_PROBE = "schedulingorder_probe"
        private const val EXTRA_ASYNC_CONSOLE_PROBE = "asyncconsole_probe"
        private const val EXTRA_ASYNC_ERROR_PROBE = "asyncerror_probe"
        private const val EXTRA_BOOTSTRAP_PROBE = "bootstrap_probe"
        private const val EXTRA_BOOTSTRAP_SCRIPT_PROBE = "bootstrapscript_probe"
        private const val EXTRA_SCRIPT_COMPLETION_PROBE = "scriptcompletion_probe"
        private const val EXTRA_SCRIPT_FAILURE_PROBE = "scriptfailure_probe"
        private const val EXTRA_COMPLETION_DRAIN_PROBE = "completiondrain_probe"
        private const val EXTRA_POST_COMPLETION_ERROR_PROBE = "postcompletionerror_probe"
        private const val EXTRA_OUTPUT_EVENTS_PROBE = "outputevents_probe"
        private const val EXTRA_OUTPUT_ENVELOPE_PROBE = "outputenvelope_probe"
        private const val EXTRA_SCRIPT_CANCEL_PROBE = "scriptcancel_probe"
        private const val EXTRA_CAPABILITY_DESCRIPTOR_PROBE = "capabilitydescriptor_probe"
        private const val EXTRA_PLUGIN_CONTRACT_PROBE = "plugincontract_probe"
        private const val EXTRA_PLUGIN_REQUEST_PROBE = "pluginrequest_probe"
        private const val EXTRA_PLUGIN_EVENTS_PROBE = "pluginevents_probe"
        private const val EXTRA_PLUGIN_FAILURE_PROBE = "pluginfailure_probe"
        private const val EXTRA_PLUGIN_CANCEL_PROBE = "plugincancel_probe"
        private const val EXTRA_PLUGIN_DISPOSE_PROBE = "plugindispose_probe"
        private const val EXTRA_PLUGIN_LIFECYCLE_PROBE = "pluginlifecycle_probe"
        private const val EXTRA_PLUGIN_INVALID_REQUEST_PROBE = "plugininvalidrequest_probe"
        private const val EXTRA_PLUGIN_STATE_MACHINE_PROBE = "pluginstatemachine_probe"
        private const val EXTRA_PLUGIN_TIMEOUT_PROBE = "plugintimeout_probe"
        private const val EXTRA_PLUGIN_CRASH_PROBE = "plugincrash_probe"
        private const val EXTRA_PLUGIN_BACKPRESSURE_PROBE = "pluginbackpressure_probe"
        private const val EXTRA_PLUGIN_NEGOTIATION_PROBE = "pluginnegotiation_probe"
        private const val EXTRA_PLUGIN_PERMISSION_PROBE = "pluginpermission_probe"
        private const val EXTRA_PLUGIN_MANIFEST_PROBE = "pluginmanifest_probe"
        private const val EXTRA_PLUGIN_ABI_PROBE = "pluginabi_probe"
        private const val EXTRA_PLUGIN_LIBRARY_PROBE = "pluginlibrary_probe"
        private const val EXTRA_PLUGIN_INSTANCE_PROBE = "plugininstance_probe"
        private const val EXTRA_PLUGIN_SESSION_PROBE = "pluginsession_probe"
        private const val EXTRA_PLUGIN_QUEUE_PROBE = "pluginqueue_probe"
        private const val EXTRA_PLUGIN_CONCURRENCY_PROBE = "pluginconcurrency_probe"
        private const val EXTRA_PLUGIN_RACE_PROBE = "pluginrace_probe"
        private const val EXTRA_PLUGIN_SHUTDOWN_PROBE = "pluginshutdown_probe"
        private const val EXTRA_PLUGIN_HEARTBEAT_PROBE = "pluginheartbeat_probe"
        private const val EXTRA_PLUGIN_HEALTH_PROBE = "pluginhealth_probe"
        private const val EXTRA_PLUGIN_TELEMETRY_PROBE = "plugintelemetry_probe"
        private const val EXTRA_PLUGIN_DIAGNOSTICS_PROBE = "plugindiagnostics_probe"
        private const val EXTRA_PLUGIN_RECOVERY_PROBE = "pluginrecovery_probe"
        private const val EXTRA_PLUGIN_RESTART_PROBE = "pluginrestart_probe"
        private const val EXTRA_PLUGIN_RESTART_BUDGET_PROBE = "pluginrestartbudget_probe"
        private const val EXTRA_PLUGIN_QUARANTINE_PROBE = "pluginquarantine_probe"
        private const val EXTRA_SCRIPT_DESCRIPTOR_PROBE = "scriptdescriptor_probe"
        private const val EXTRA_SCRIPT_NORMALIZATION_PROBE = "scriptnormalization_probe"
        private const val EXTRA_SCRIPT_CONTEXT_PROBE = "scriptcontext_probe"
        private const val EXTRA_SCRIPT_RESULT_V2_PROBE = "scriptresultv2_probe"
        private const val EXTRA_SCRIPT_CANCELLATION_TOKEN_PROBE = "scriptcancellationtoken_probe"
        private const val EXTRA_SCRIPT_TIMEOUT_POLICY_PROBE = "scripttimeoutpolicy_probe"
        private const val EXTRA_SCRIPT_MEMORY_POLICY_PROBE = "scriptmemorypolicy_probe"
        private const val EXTRA_SCRIPT_PREFLIGHT_SUMMARY_PROBE = "scriptpreflightsummary_probe"
        private const val EXTRA_INLINE_CONSTANT_PROBE = "inlineconstant_probe"
        private const val EXTRA_INLINE_RETURN_VALUE_PROBE = "inlinereturnvalue_probe"
        private const val EXTRA_INLINE_ERROR_PROBE = "inlineerror_probe"
        private const val EXTRA_INLINE_OUTPUT_PROBE = "inlineoutput_probe"
        private const val EXTRA_INLINE_ASYNC_PROBE = "inlineasync_probe"
        private const val EXTRA_INLINE_CANCEL_PROBE = "inlinecancel_probe"
        private const val EXTRA_INLINE_TIMEOUT_PROBE = "inlinetimeout_probe"
        private const val EXTRA_INLINE_REPEATED_PROCESS_PROBE = "inlinerepeatedprocess_probe"
        private const val EXTRA_INLINE_BUILTIN_POLICY_PROBE = "inlinebuiltinpolicy_probe"
        private const val EXTRA_INLINE_REQUIRE_DENIED_PROBE = "inlinerequiredenied_probe"
        private const val EXTRA_INLINE_NPM_DENIED_PROBE = "inlinenpmdenied_probe"
        private const val EXTRA_INLINE_PACKAGE_JSON_DENIED_PROBE = "inlinepackagejsondenied_probe"
        private const val EXTRA_INLINE_NODE_MODULES_DENIED_PROBE = "inlinenodemodulesdenied_probe"
        private const val EXTRA_INLINE_NATIVE_ADDON_DENIED_PROBE = "inlinenativeaddondenied_probe"
        private const val EXTRA_INLINE_ESM_DENIED_PROBE = "inlineesmdenied_probe"
        private const val EXTRA_INLINE_DYNAMIC_IMPORT_DENIED_PROBE = "inlinedynamicimportdenied_probe"
        private const val EXTRA_INLINE_WORKER_DENIED_PROBE = "inlineworkerdenied_probe"
        private const val EXTRA_INLINE_CAPABILITY_SUMMARY_PROBE = "inlinecapabilitysummary_probe"
        private const val EXTRA_INLINE_FILESYSTEM_DENIED_PROBE = "inlinefilesystemdenied_probe"
        private const val EXTRA_INLINE_CHILD_PROCESS_DENIED_PROBE = "inlinechildprocessdenied_probe"
        private const val EXTRA_INLINE_NETWORK_DENIED_PROBE = "inlinenetworkdenied_probe"
        private const val EXTRA_INLINE_PERMISSIONS_DENIED_PROBE = "inlinepermissionsdenied_probe"
        private const val EXTRA_INLINE_ANDROID_BRIDGE_DENIED_PROBE = "inlineandroidbridgedenied_probe"
        private const val EXTRA_INLINE_AUTOJS_API_DENIED_PROBE = "inlineautojsapidenied_probe"
        private const val EXTRA_INLINE_CONSOLE_BRIDGE_DENIED_PROBE = "inlineconsolebridgedenied_probe"
        private const val EXTRA_INLINE_JSON_SOCKET_DENIED_PROBE = "inlinejsonsocketdenied_probe"
        private const val EXTRA_INLINE_BINDER_DENIED_PROBE = "inlinebinderdenied_probe"
        private const val EXTRA_INLINE_UI_BRIDGE_DENIED_PROBE = "inlineuibridgedenied_probe"
        private const val EXTRA_INLINE_ACCESSIBILITY_DENIED_PROBE = "inlineaccessibilitydenied_probe"
        private const val EXTRA_INLINE_IMAGES_DENIED_PROBE = "inlineimagesdenied_probe"
        private const val EXTRA_INLINE_DIALOGS_DENIED_PROBE = "inlinedialogsdenied_probe"
        private const val EXTRA_INLINE_SENSORS_DENIED_PROBE = "inlinesensorsdenied_probe"
        private const val EXTRA_INLINE_MEDIA_CAMERA_DENIED_PROBE = "inlinemediacameradenied_probe"
        private const val EXTRA_INLINE_STORAGE_DENIED_PROBE = "inlinestoragedenied_probe"
        private const val EXTRA_INLINE_SHELL_DENIED_PROBE = "inlineshelldenied_probe"
        private const val EXTRA_INLINE_NOTIFICATION_DENIED_PROBE = "inlinenotificationdenied_probe"
        private const val EXTRA_INLINE_CLIPBOARD_DENIED_PROBE = "inlineclipboarddenied_probe"
        private const val EXTRA_INLINE_DEVICE_INFO_DENIED_PROBE = "inlinedeviceinfodenied_probe"
        private const val EXTRA_INLINE_VIBRATION_DENIED_PROBE = "inlinevibrationdenied_probe"
        private const val EXTRA_INLINE_TOAST_DENIED_PROBE = "inlinetoastdenied_probe"
        private const val EXTRA_INLINE_FLOATY_DENIED_PROBE = "inlinefloatydenied_probe"
        private const val EXTRA_INLINE_EVENTS_DENIED_PROBE = "inlineeventsdenied_probe"
        private const val EXTRA_INLINE_THREADS_DENIED_PROBE = "inlinethreadsdenied_probe"
        private const val EXTRA_INLINE_TIMERS_DENIED_PROBE = "inlinetimersdenied_probe"
        private const val EXTRA_INLINE_WEBVIEW_DENIED_PROBE = "inlinewebviewdenied_probe"
        private const val EXTRA_INLINE_HTTP_DENIED_PROBE = "inlinehttpdenied_probe"
        private const val EXTRA_INLINE_CRYPTO_DENIED_PROBE = "inlinecryptodenied_probe"
        private const val EXTRA_INLINE_OCR_DENIED_PROBE = "inlineocrdenied_probe"
        private const val EXTRA_INLINE_ML_AI_DENIED_PROBE = "inlinemlaidenied_probe"
        private const val EXTRA_INLINE_WEBSOCKET_DENIED_PROBE = "inlinewebsocketdenied_probe"
        private const val EXTRA_INLINE_BLUETOOTH_DENIED_PROBE = "inlinebluetoothdenied_probe"
        private const val EXTRA_INLINE_NFC_DENIED_PROBE = "inlinenfcdenied_probe"
        private const val EXTRA_INLINE_USB_DENIED_PROBE = "inlineusbdenied_probe"
        private const val EXTRA_INLINE_LOCATION_DENIED_PROBE = "inlinelocationdenied_probe"
        private const val EXTRA_INLINE_CONTACTS_DENIED_PROBE = "inlinecontactsdenied_probe"
        private const val EXTRA_INLINE_CALENDAR_DENIED_PROBE = "inlinecalendardenied_probe"
        private const val EXTRA_INLINE_SMS_TELEPHONY_DENIED_PROBE = "inlinesmstelephonydenied_probe"
        private const val EXTRA_INLINE_ACCOUNT_DENIED_PROBE = "inlineaccountdenied_probe"
        private const val EXTRA_INLINE_PACKAGE_MANAGER_DENIED_PROBE = "inlinepackagemanagerdenied_probe"
        private const val EXTRA_INLINE_INTENT_ACTIVITY_DENIED_PROBE = "inlineintentactivitydenied_probe"
        private const val EXTRA_INLINE_BROADCAST_DENIED_PROBE = "inlinebroadcastdenied_probe"
        private const val EXTRA_INLINE_CONTENT_PROVIDER_DENIED_PROBE = "inlinecontentproviderdenied_probe"
        private const val EXTRA_INLINE_MEDIA_STORE_DENIED_PROBE = "inlinemediastoredenied_probe"
        private const val EXTRA_INLINE_DOWNLOAD_MANAGER_DENIED_PROBE = "inlinedownloadmanagerdenied_probe"
        private const val EXTRA_INLINE_INPUT_METHOD_DENIED_PROBE = "inlineinputmethoddenied_probe"
        private const val EXTRA_INLINE_APP_OPS_DENIED_PROBE = "inlineappopsdenied_probe"
        private const val EXTRA_INLINE_PERMISSION_MANAGER_DENIED_PROBE = "inlinepermissionmanagerdenied_probe"
        private const val EXTRA_INLINE_SETTINGS_DENIED_PROBE = "inlinesettingsdenied_probe"
        private const val EXTRA_INLINE_POWER_MANAGER_DENIED_PROBE = "inlinepowermanagerdenied_probe"
        private const val EXTRA_INLINE_KEYGUARD_DENIED_PROBE = "inlinekeyguarddenied_probe"
        private const val EXTRA_INLINE_WALLPAPER_DENIED_PROBE = "inlinewallpaperdenied_probe"
        private const val EXTRA_INLINE_SHORTCUT_MANAGER_DENIED_PROBE = "inlineshortcutmanagerdenied_probe"
        private const val EXTRA_INLINE_ALARM_MANAGER_DENIED_PROBE = "inlinealarmmanagerdenied_probe"
        private const val EXTRA_INLINE_JOB_SCHEDULER_DENIED_PROBE = "inlinejobschedulerdenied_probe"
        private const val EXTRA_INLINE_WORK_MANAGER_DENIED_PROBE = "inlineworkmanagerdenied_probe"
        private const val EXTRA_INLINE_CLIPBOARD_LISTENER_DENIED_PROBE = "inlineclipboardlistenerdenied_probe"
        private const val EXTRA_INLINE_NOTIFICATION_LISTENER_DENIED_PROBE = "inlinenotificationlistenerdenied_probe"
        private const val EXTRA_INLINE_ACCESSIBILITY_CONTROL_DENIED_PROBE = "inlineaccessibilitycontroldenied_probe"
        private const val EXTRA_INLINE_DEVICE_POLICY_DENIED_PROBE = "inlinedevicepolicydenied_probe"
        private const val EXTRA_INLINE_USAGE_STATS_DENIED_PROBE = "inlineusagestatsdenied_probe"
        private const val EXTRA_INLINE_VPN_CONNECTIVITY_DENIED_PROBE = "inlinevpnconnectivitydenied_probe"
        private const val EXTRA_INLINE_WIFI_MANAGER_DENIED_PROBE = "inlinewifimanagerdenied_probe"
        private const val EXTRA_INLINE_TELEPHONY_SUBSCRIPTION_DENIED_PROBE = "inlinetelephonysubscriptiondenied_probe"
        private const val EXTRA_INLINE_CAMERA_MANAGER_DENIED_PROBE = "inlinecameramanagerdenied_probe"
        private const val EXTRA_INLINE_AUDIO_MANAGER_DENIED_PROBE = "inlineaudiomanagerdenied_probe"
        private const val EXTRA_INLINE_DISPLAY_MANAGER_DENIED_PROBE = "inlinedisplaymanagerdenied_probe"
        private const val EXTRA_INLINE_PRINT_MANAGER_DENIED_PROBE = "inlineprintmanagerdenied_probe"
        private const val EXTRA_USER_SOURCE_DESCRIPTOR_PROBE = "usersourcedescriptor_probe"
        private const val EXTRA_USER_SOURCE_SIZE_PROBE = "usersourcesize_probe"
        private const val EXTRA_USER_SOURCE_ENCODING_PROBE = "usersourceencoding_probe"
        private const val EXTRA_USER_SOURCE_NAME_PROBE = "usersourcename_probe"
        private const val EXTRA_USER_SOURCE_WRAPPER_PROBE = "usersourcewrapper_probe"
        private const val EXTRA_USER_SOURCE_STRICT_MODE_PROBE = "usersourcestrictmode_probe"
        private const val EXTRA_USER_SOURCE_CAPABILITY_PROBE = "usersourcecapability_probe"
        private const val EXTRA_USER_SOURCE_PREFLIGHT_PROBE = "usersourcepreflight_probe"
        private const val EXTRA_CONTROLLED_USER_INLINE_CONSTANT_PROBE = "controlleduserinlineconstant_probe"
        private const val EXTRA_CONTROLLED_USER_INLINE_STDOUT_PROBE = "controlleduserinlinestdout_probe"
        private const val EXTRA_CONTROLLED_USER_INLINE_STDERR_PROBE = "controlleduserinlinestderr_probe"
        private const val EXTRA_CONTROLLED_USER_INLINE_RETURN_VALUE_PROBE = "controlleduserinlinereturnvalue_probe"
        private const val EXTRA_CONTROLLED_USER_INLINE_THROWN_ERROR_PROBE = "controlleduserinlinethrownerror_probe"
        private const val EXTRA_CONTROLLED_USER_INLINE_PROMISE_PROBE = "controlleduserinlinepromise_probe"
        private const val EXTRA_CONTROLLED_USER_INLINE_ASYNC_ORDERING_PROBE = "controlleduserinlineasyncordering_probe"
        private const val EXTRA_CONTROLLED_USER_INLINE_SUMMARY_PROBE = "controlleduserinlinesummary_probe"
        private const val EXTRA_USER_FILE_DESCRIPTOR_PROBE = "userfiledescriptor_probe"
        private const val EXTRA_USER_FILE_READ_POLICY_PROBE = "userfilereadpolicy_probe"
        private const val EXTRA_USER_FILE_PATH_NORMALIZATION_PROBE = "userfilepathnormalization_probe"
        private const val EXTRA_USER_FILE_WORKING_DIRECTORY_PROBE = "userfileworkingdirectory_probe"
        private const val EXTRA_USER_FILE_SOURCE_LOADING_PROBE = "userfilesourceloading_probe"
        private const val EXTRA_USER_FILE_EXECUTION_DRY_RUN_PROBE = "userfileexecutiondryrun_probe"
        private const val EXTRA_USER_FILE_ERROR_STACK_FILENAME_PROBE = "userfileerrorstackfilename_probe"
        private const val EXTRA_USER_FILE_EXECUTION_SUMMARY_PROBE = "userfileexecutionsummary_probe"
        private const val EXTRA_EMBEDDED_SCRIPT_REQUEST_PROBE = "embeddedscriptrequest_probe"
        private const val EXTRA_EMBEDDED_SCRIPT_RESULT_PROBE = "embeddedscriptresult_probe"
        private const val EXTRA_EMBEDDED_SCRIPT_OUTPUT_EVENT_PROBE = "embeddedscriptoutputevent_probe"
        private const val EXTRA_EMBEDDED_SCRIPT_ERROR_PROBE = "embeddedscripterror_probe"
        private const val EXTRA_EMBEDDED_SCRIPT_TIMEOUT_PROBE = "embeddedscripttimeout_probe"
        private const val EXTRA_EMBEDDED_SCRIPT_CANCELLATION_PROBE = "embeddedscriptcancellation_probe"
        private const val EXTRA_EMBEDDED_SCRIPT_PROCESS_ISOLATION_PROBE = "embeddedscriptprocessisolation_probe"
        private const val EXTRA_EMBEDDED_SCRIPT_CONTRACT_PROBE = "embeddedscriptcontract_probe"
        private const val EXTRA_EMBEDDED_MVP_LIFECYCLE_PROBE = "embeddedmvplifecycle_probe"
        private const val EXTRA_EMBEDDED_MVP_SOURCE_INPUT_PROBE = "embeddedmvpsourceinput_probe"
        private const val EXTRA_EMBEDDED_MVP_OUTPUT_PROBE = "embeddedmvpoutput_probe"
        private const val EXTRA_EMBEDDED_MVP_ERROR_HANDLING_PROBE = "embeddedmvperrorhandling_probe"
        private const val EXTRA_EMBEDDED_MVP_ASYNC_PROBE = "embeddedmvpasync_probe"
        private const val EXTRA_EMBEDDED_MVP_TIMEOUT_ISOLATION_PROBE = "embeddedmvptimeoutisolation_probe"
        private const val EXTRA_EMBEDDED_MVP_SECURITY_POLICY_PROBE = "embeddedmvpsecuritypolicy_probe"
        private const val EXTRA_EMBEDDED_MVP_GO_NO_GO_PROBE = "embeddedmvpgonogo_probe"
        private const val EXTRA_SUCCEEDED = "succeeded"
        private const val EXTRA_FORMATTED_LOG = "formatted_log"
        private const val EXTRA_ERROR_MESSAGE = "error_message"
        private const val INITIALIZE_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.initialize"
        private const val ISOLATE_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.isolate"
        private const val UVLOOP_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.uvloop"
        private const val V8_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.v8"
        private const val UVDIAGNOSTICS_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.uvdiagnostics"
        private const val UVCLOSE_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.uvclose"
        private const val UVRUN_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.uvrun"
        private const val ISOLATEDATA_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.isolatedata"
        private const val ENVIRONMENT_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.environment"
        private const val LOAD_ENVIRONMENT_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.loadenvironment"
        private const val SPIN_EVENT_LOOP_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.spineventloop"
        private const val INLINE_JS_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinejs"
        private const val PROCESS_JS_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.process"
        private const val CONSOLE_JS_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.console"
        private const val STDOUT_CAPTURE_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.stdout"
        private const val JS_RESULT_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.jsresult"
        private const val STDOUT_WRITE_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.stdoutwrite"
        private const val CONSOLE_DIAGNOSTICS_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.consolediagnostics"
        private const val CONSOLE_STREAM_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.consolestream"
        private const val CONSOLE_SHAPE_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.consoleshape"
        private const val CONSOLE_REPLACE_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.consolereplace"
        private const val CONSOLE_FAMILY_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.consolefamily"
        private const val CONSOLE_FORMAT_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.consoleformat"
        private const val CONSOLE_REJECTION_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.consolerejection"
        private const val CONSOLE_UNCAUGHT_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.consoleuncaught"
        private const val SCHEDULING_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.scheduling"
        private const val SCHEDULING_ORDER_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.schedulingorder"
        private const val ASYNC_CONSOLE_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.asyncconsole"
        private const val ASYNC_ERROR_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.asyncerror"
        private const val BOOTSTRAP_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.bootstrap"
        private const val BOOTSTRAP_SCRIPT_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.bootstrapscript"
        private const val SCRIPT_COMPLETION_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.scriptcompletion"
        private const val SCRIPT_FAILURE_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.scriptfailure"
        private const val COMPLETION_DRAIN_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.completiondrain"
        private const val POST_COMPLETION_ERROR_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.postcompletionerror"
        private const val OUTPUT_EVENTS_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.outputevents"
        private const val OUTPUT_ENVELOPE_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.outputenvelope"
        private const val SCRIPT_CANCEL_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.scriptcancel"
        private const val CAPABILITY_DESCRIPTOR_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.capabilitydescriptor"
        private const val PLUGIN_CONTRACT_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.plugincontract"
        private const val PLUGIN_REQUEST_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.pluginrequest"
        private const val PLUGIN_EVENTS_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.pluginevents"
        private const val PLUGIN_FAILURE_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.pluginfailure"
        private const val PLUGIN_CANCEL_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.plugincancel"
        private const val PLUGIN_DISPOSE_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.plugindispose"
        private const val PLUGIN_LIFECYCLE_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.pluginlifecycle"
        private const val PLUGIN_INVALID_REQUEST_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.plugininvalidrequest"
        private const val PLUGIN_STATE_MACHINE_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.pluginstatemachine"
        private const val PLUGIN_TIMEOUT_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.plugintimeout"
        private const val PLUGIN_CRASH_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.plugincrash"
        private const val PLUGIN_BACKPRESSURE_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.pluginbackpressure"
        private const val PLUGIN_NEGOTIATION_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.pluginnegotiation"
        private const val PLUGIN_PERMISSION_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.pluginpermission"
        private const val PLUGIN_MANIFEST_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.pluginmanifest"
        private const val PLUGIN_ABI_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.pluginabi"
        private const val PLUGIN_LIBRARY_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.pluginlibrary"
        private const val PLUGIN_INSTANCE_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.plugininstance"
        private const val PLUGIN_SESSION_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.pluginsession"
        private const val PLUGIN_QUEUE_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.pluginqueue"
        private const val PLUGIN_CONCURRENCY_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.pluginconcurrency"
        private const val PLUGIN_RACE_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.pluginrace"
        private const val PLUGIN_SHUTDOWN_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.pluginshutdown"
        private const val PLUGIN_HEARTBEAT_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.pluginheartbeat"
        private const val PLUGIN_HEALTH_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.pluginhealth"
        private const val PLUGIN_TELEMETRY_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.plugintelemetry"
        private const val PLUGIN_DIAGNOSTICS_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.plugindiagnostics"
        private const val PLUGIN_RECOVERY_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.pluginrecovery"
        private const val PLUGIN_RESTART_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.pluginrestart"
        private const val PLUGIN_RESTART_BUDGET_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.pluginrestartbudget"
        private const val PLUGIN_QUARANTINE_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.pluginquarantine"
        private const val SCRIPT_DESCRIPTOR_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.scriptdescriptor"
        private const val SCRIPT_NORMALIZATION_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.scriptnormalization"
        private const val SCRIPT_CONTEXT_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.scriptcontext"
        private const val SCRIPT_RESULT_V2_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.scriptresultv2"
        private const val SCRIPT_CANCELLATION_TOKEN_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.scriptcancellationtoken"
        private const val SCRIPT_TIMEOUT_POLICY_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.scripttimeoutpolicy"
        private const val SCRIPT_MEMORY_POLICY_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.scriptmemorypolicy"
        private const val SCRIPT_PREFLIGHT_SUMMARY_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.scriptpreflightsummary"
        private const val INLINE_CONSTANT_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlineconstant"
        private const val INLINE_RETURN_VALUE_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinereturnvalue"
        private const val INLINE_ERROR_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlineerror"
        private const val INLINE_OUTPUT_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlineoutput"
        private const val INLINE_ASYNC_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlineasync"
        private const val INLINE_CANCEL_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinecancel"
        private const val INLINE_TIMEOUT_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinetimeout"
        private const val INLINE_REPEATED_PROCESS_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinerepeatedprocess"
        private const val INLINE_BUILTIN_POLICY_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinebuiltinpolicy"
        private const val INLINE_REQUIRE_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinerequiredenied"
        private const val INLINE_NPM_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinenpmdenied"
        private const val INLINE_PACKAGE_JSON_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinepackagejsondenied"
        private const val INLINE_NODE_MODULES_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinenodemodulesdenied"
        private const val INLINE_NATIVE_ADDON_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinenativeaddondenied"
        private const val INLINE_ESM_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlineesmdenied"
        private const val INLINE_DYNAMIC_IMPORT_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinedynamicimportdenied"
        private const val INLINE_WORKER_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlineworkerdenied"
        private const val INLINE_CAPABILITY_SUMMARY_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinecapabilitysummary"
        private const val INLINE_FILESYSTEM_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinefilesystemdenied"
        private const val INLINE_CHILD_PROCESS_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinechildprocessdenied"
        private const val INLINE_NETWORK_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinenetworkdenied"
        private const val INLINE_PERMISSIONS_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinepermissionsdenied"
        private const val INLINE_ANDROID_BRIDGE_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlineandroidbridgedenied"
        private const val INLINE_AUTOJS_API_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlineautojsapidenied"
        private const val INLINE_CONSOLE_BRIDGE_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlineconsolebridgedenied"
        private const val INLINE_JSON_SOCKET_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinejsonsocketdenied"
        private const val INLINE_BINDER_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinebinderdenied"
        private const val INLINE_UI_BRIDGE_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlineuibridgedenied"
        private const val INLINE_ACCESSIBILITY_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlineaccessibilitydenied"
        private const val INLINE_IMAGES_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlineimagesdenied"
        private const val INLINE_DIALOGS_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinedialogsdenied"
        private const val INLINE_SENSORS_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinesensorsdenied"
        private const val INLINE_MEDIA_CAMERA_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinemediacameradenied"
        private const val INLINE_STORAGE_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinestoragedenied"
        private const val INLINE_SHELL_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlineshelldenied"
        private const val INLINE_NOTIFICATION_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinenotificationdenied"
        private const val INLINE_CLIPBOARD_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlineclipboarddenied"
        private const val INLINE_DEVICE_INFO_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinedeviceinfodenied"
        private const val INLINE_VIBRATION_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinevibrationdenied"
        private const val INLINE_TOAST_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinetoastdenied"
        private const val INLINE_FLOATY_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinefloatydenied"
        private const val INLINE_EVENTS_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlineeventsdenied"
        private const val INLINE_THREADS_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinethreadsdenied"
        private const val INLINE_TIMERS_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinetimersdenied"
        private const val INLINE_WEBVIEW_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinewebviewdenied"
        private const val INLINE_HTTP_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinehttpdenied"
        private const val INLINE_CRYPTO_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinecryptodenied"
        private const val INLINE_OCR_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlineocrdenied"
        private const val INLINE_ML_AI_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinemlaidenied"
        private const val INLINE_WEBSOCKET_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinewebsocketdenied"
        private const val INLINE_BLUETOOTH_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinebluetoothdenied"
        private const val INLINE_NFC_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinenfcdenied"
        private const val INLINE_USB_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlineusbdenied"
        private const val INLINE_LOCATION_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinelocationdenied"
        private const val INLINE_CONTACTS_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinecontactsdenied"
        private const val INLINE_CALENDAR_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinecalendardenied"
        private const val INLINE_SMS_TELEPHONY_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinesmstelephonydenied"
        private const val INLINE_ACCOUNT_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlineaccountdenied"
        private const val INLINE_PACKAGE_MANAGER_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinepackagemanagerdenied"
        private const val INLINE_INTENT_ACTIVITY_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlineintentactivitydenied"
        private const val INLINE_BROADCAST_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinebroadcastdenied"
        private const val INLINE_CONTENT_PROVIDER_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinecontentproviderdenied"
        private const val INLINE_MEDIA_STORE_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinemediastoredenied"
        private const val INLINE_DOWNLOAD_MANAGER_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinedownloadmanagerdenied"
        private const val INLINE_INPUT_METHOD_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlineinputmethoddenied"
        private const val INLINE_APP_OPS_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlineappopsdenied"
        private const val INLINE_PERMISSION_MANAGER_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinepermissionmanagerdenied"
        private const val INLINE_SETTINGS_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinesettingsdenied"
        private const val INLINE_POWER_MANAGER_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinepowermanagerdenied"
        private const val INLINE_KEYGUARD_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinekeyguarddenied"
        private const val INLINE_WALLPAPER_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinewallpaperdenied"
        private const val INLINE_SHORTCUT_MANAGER_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlineshortcutmanagerdenied"
        private const val INLINE_ALARM_MANAGER_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinealarmmanagerdenied"
        private const val INLINE_JOB_SCHEDULER_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinejobschedulerdenied"
        private const val INLINE_WORK_MANAGER_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlineworkmanagerdenied"
        private const val INLINE_CLIPBOARD_LISTENER_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlineclipboardlistenerdenied"
        private const val INLINE_NOTIFICATION_LISTENER_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinenotificationlistenerdenied"
        private const val INLINE_ACCESSIBILITY_CONTROL_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlineaccessibilitycontroldenied"
        private const val INLINE_DEVICE_POLICY_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinedevicepolicydenied"
        private const val INLINE_USAGE_STATS_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlineusagestatsdenied"
        private const val INLINE_VPN_CONNECTIVITY_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinevpnconnectivitydenied"
        private const val INLINE_WIFI_MANAGER_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinewifimanagerdenied"
        private const val INLINE_TELEPHONY_SUBSCRIPTION_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinetelephonysubscriptiondenied"
        private const val INLINE_CAMERA_MANAGER_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinecameramanagerdenied"
        private const val INLINE_AUDIO_MANAGER_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlineaudiomanagerdenied"
        private const val INLINE_DISPLAY_MANAGER_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlinedisplaymanagerdenied"
        private const val INLINE_PRINT_MANAGER_DENIED_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.inlineprintmanagerdenied"
        private const val USER_SOURCE_DESCRIPTOR_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.usersourcedescriptor"
        private const val USER_SOURCE_SIZE_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.usersourcesize"
        private const val USER_SOURCE_ENCODING_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.usersourceencoding"
        private const val USER_SOURCE_NAME_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.usersourcename"
        private const val USER_SOURCE_WRAPPER_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.usersourcewrapper"
        private const val USER_SOURCE_STRICT_MODE_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.usersourcestrictmode"
        private const val USER_SOURCE_CAPABILITY_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.usersourcecapability"
        private const val USER_SOURCE_PREFLIGHT_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.usersourcepreflight"
        private const val CONTROLLED_USER_INLINE_CONSTANT_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.controlleduserinlineconstant"
        private const val CONTROLLED_USER_INLINE_STDOUT_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.controlleduserinlinestdout"
        private const val CONTROLLED_USER_INLINE_STDERR_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.controlleduserinlinestderr"
        private const val CONTROLLED_USER_INLINE_RETURN_VALUE_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.controlleduserinlinereturnvalue"
        private const val CONTROLLED_USER_INLINE_THROWN_ERROR_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.controlleduserinlinethrownerror"
        private const val CONTROLLED_USER_INLINE_PROMISE_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.controlleduserinlinepromise"
        private const val CONTROLLED_USER_INLINE_ASYNC_ORDERING_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.controlleduserinlineasyncordering"
        private const val CONTROLLED_USER_INLINE_SUMMARY_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.controlleduserinlinesummary"
        private const val USER_FILE_DESCRIPTOR_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.userfiledescriptor"
        private const val USER_FILE_READ_POLICY_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.userfilereadpolicy"
        private const val USER_FILE_PATH_NORMALIZATION_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.userfilepathnormalization"
        private const val USER_FILE_WORKING_DIRECTORY_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.userfileworkingdirectory"
        private const val USER_FILE_SOURCE_LOADING_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.userfilesourceloading"
        private const val USER_FILE_EXECUTION_DRY_RUN_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.userfileexecutiondryrun"
        private const val USER_FILE_ERROR_STACK_FILENAME_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.userfileerrorstackfilename"
        private const val USER_FILE_EXECUTION_SUMMARY_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.userfileexecutionsummary"
        private const val EMBEDDED_SCRIPT_REQUEST_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.embeddedscriptrequest"
        private const val EMBEDDED_SCRIPT_RESULT_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.embeddedscriptresult"
        private const val EMBEDDED_SCRIPT_OUTPUT_EVENT_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.embeddedscriptoutputevent"
        private const val EMBEDDED_SCRIPT_ERROR_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.embeddedscripterror"
        private const val EMBEDDED_SCRIPT_TIMEOUT_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.embeddedscripttimeout"
        private const val EMBEDDED_SCRIPT_CANCELLATION_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.embeddedscriptcancellation"
        private const val EMBEDDED_SCRIPT_PROCESS_ISOLATION_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.embeddedscriptprocessisolation"
        private const val EMBEDDED_SCRIPT_CONTRACT_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.embeddedscriptcontract"
        private const val EMBEDDED_MVP_LIFECYCLE_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.embeddedmvplifecycle"
        private const val EMBEDDED_MVP_SOURCE_INPUT_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.embeddedmvpsourceinput"
        private const val EMBEDDED_MVP_OUTPUT_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.embeddedmvpoutput"
        private const val EMBEDDED_MVP_ERROR_HANDLING_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.embeddedmvperrorhandling"
        private const val EMBEDDED_MVP_ASYNC_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.embeddedmvpasync"
        private const val EMBEDDED_MVP_TIMEOUT_ISOLATION_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.embeddedmvptimeoutisolation"
        private const val EMBEDDED_MVP_SECURITY_POLICY_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.embeddedmvpsecuritypolicy"
        private const val EMBEDDED_MVP_GO_NO_GO_PROBE_PROPERTY = "autojs.nodejs.embedded.probe.embeddedmvpgonogo"
        private const val RESULT_PROBE = 1
        private const val NOTIFICATION_ID = 0xA6E3
        private const val EMBEDDED_PROBE_PROCESS_SUFFIX = ":nodejs_embedded"
        private const val PROCESS_EXIT_DELAY_MS = 250L

        private data class InlineProbeRequest(
            val extraName: String,
            val propertyName: String,
            val requested: () -> Boolean,
        )

        private val INLINE_PROBE_REQUESTS = arrayOf(
            InlineProbeRequest(EXTRA_INLINE_CONSTANT_PROBE, INLINE_CONSTANT_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineConstantProbe),
            InlineProbeRequest(EXTRA_INLINE_RETURN_VALUE_PROBE, INLINE_RETURN_VALUE_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineReturnValueProbe),
            InlineProbeRequest(EXTRA_INLINE_ERROR_PROBE, INLINE_ERROR_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineErrorProbe),
            InlineProbeRequest(EXTRA_INLINE_OUTPUT_PROBE, INLINE_OUTPUT_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineOutputProbe),
            InlineProbeRequest(EXTRA_INLINE_ASYNC_PROBE, INLINE_ASYNC_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineAsyncProbe),
            InlineProbeRequest(EXTRA_INLINE_CANCEL_PROBE, INLINE_CANCEL_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineCancelProbe),
            InlineProbeRequest(EXTRA_INLINE_TIMEOUT_PROBE, INLINE_TIMEOUT_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineTimeoutProbe),
            InlineProbeRequest(EXTRA_INLINE_REPEATED_PROCESS_PROBE, INLINE_REPEATED_PROCESS_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineRepeatedProcessProbe),
            InlineProbeRequest(EXTRA_INLINE_BUILTIN_POLICY_PROBE, INLINE_BUILTIN_POLICY_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineBuiltinPolicyProbe),
            InlineProbeRequest(EXTRA_INLINE_REQUIRE_DENIED_PROBE, INLINE_REQUIRE_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineRequireDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_NPM_DENIED_PROBE, INLINE_NPM_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineNpmDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_PACKAGE_JSON_DENIED_PROBE, INLINE_PACKAGE_JSON_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlinePackageJsonDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_NODE_MODULES_DENIED_PROBE, INLINE_NODE_MODULES_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineNodeModulesDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_NATIVE_ADDON_DENIED_PROBE, INLINE_NATIVE_ADDON_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineNativeAddonDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_ESM_DENIED_PROBE, INLINE_ESM_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineEsmDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_DYNAMIC_IMPORT_DENIED_PROBE, INLINE_DYNAMIC_IMPORT_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineDynamicImportDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_WORKER_DENIED_PROBE, INLINE_WORKER_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineWorkerDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_CAPABILITY_SUMMARY_PROBE, INLINE_CAPABILITY_SUMMARY_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineCapabilitySummaryProbe),
            InlineProbeRequest(EXTRA_INLINE_FILESYSTEM_DENIED_PROBE, INLINE_FILESYSTEM_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineFilesystemDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_CHILD_PROCESS_DENIED_PROBE, INLINE_CHILD_PROCESS_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineChildProcessDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_NETWORK_DENIED_PROBE, INLINE_NETWORK_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineNetworkDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_PERMISSIONS_DENIED_PROBE, INLINE_PERMISSIONS_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlinePermissionsDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_ANDROID_BRIDGE_DENIED_PROBE, INLINE_ANDROID_BRIDGE_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineAndroidBridgeDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_AUTOJS_API_DENIED_PROBE, INLINE_AUTOJS_API_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineAutoJsApiDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_CONSOLE_BRIDGE_DENIED_PROBE, INLINE_CONSOLE_BRIDGE_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineConsoleBridgeDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_JSON_SOCKET_DENIED_PROBE, INLINE_JSON_SOCKET_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineJsonSocketDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_BINDER_DENIED_PROBE, INLINE_BINDER_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineBinderDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_UI_BRIDGE_DENIED_PROBE, INLINE_UI_BRIDGE_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineUiBridgeDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_ACCESSIBILITY_DENIED_PROBE, INLINE_ACCESSIBILITY_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineAccessibilityDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_IMAGES_DENIED_PROBE, INLINE_IMAGES_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineImagesDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_DIALOGS_DENIED_PROBE, INLINE_DIALOGS_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineDialogsDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_SENSORS_DENIED_PROBE, INLINE_SENSORS_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineSensorsDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_MEDIA_CAMERA_DENIED_PROBE, INLINE_MEDIA_CAMERA_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineMediaCameraDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_STORAGE_DENIED_PROBE, INLINE_STORAGE_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineStorageDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_SHELL_DENIED_PROBE, INLINE_SHELL_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineShellDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_NOTIFICATION_DENIED_PROBE, INLINE_NOTIFICATION_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineNotificationDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_CLIPBOARD_DENIED_PROBE, INLINE_CLIPBOARD_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineClipboardDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_DEVICE_INFO_DENIED_PROBE, INLINE_DEVICE_INFO_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineDeviceInfoDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_VIBRATION_DENIED_PROBE, INLINE_VIBRATION_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineVibrationDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_TOAST_DENIED_PROBE, INLINE_TOAST_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineToastDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_FLOATY_DENIED_PROBE, INLINE_FLOATY_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineFloatyDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_EVENTS_DENIED_PROBE, INLINE_EVENTS_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineEventsDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_THREADS_DENIED_PROBE, INLINE_THREADS_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineThreadsDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_TIMERS_DENIED_PROBE, INLINE_TIMERS_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineTimersDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_WEBVIEW_DENIED_PROBE, INLINE_WEBVIEW_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineWebViewDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_HTTP_DENIED_PROBE, INLINE_HTTP_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineHttpDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_CRYPTO_DENIED_PROBE, INLINE_CRYPTO_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineCryptoDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_OCR_DENIED_PROBE, INLINE_OCR_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineOcrDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_ML_AI_DENIED_PROBE, INLINE_ML_AI_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineMlAiDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_WEBSOCKET_DENIED_PROBE, INLINE_WEBSOCKET_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineWebSocketDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_BLUETOOTH_DENIED_PROBE, INLINE_BLUETOOTH_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineBluetoothDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_NFC_DENIED_PROBE, INLINE_NFC_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineNfcDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_USB_DENIED_PROBE, INLINE_USB_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineUsbDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_LOCATION_DENIED_PROBE, INLINE_LOCATION_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineLocationDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_CONTACTS_DENIED_PROBE, INLINE_CONTACTS_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineContactsDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_CALENDAR_DENIED_PROBE, INLINE_CALENDAR_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineCalendarDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_SMS_TELEPHONY_DENIED_PROBE, INLINE_SMS_TELEPHONY_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineSmsTelephonyDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_ACCOUNT_DENIED_PROBE, INLINE_ACCOUNT_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineAccountDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_PACKAGE_MANAGER_DENIED_PROBE, INLINE_PACKAGE_MANAGER_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlinePackageManagerDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_INTENT_ACTIVITY_DENIED_PROBE, INLINE_INTENT_ACTIVITY_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineIntentActivityDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_BROADCAST_DENIED_PROBE, INLINE_BROADCAST_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineBroadcastDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_CONTENT_PROVIDER_DENIED_PROBE, INLINE_CONTENT_PROVIDER_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineContentProviderDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_MEDIA_STORE_DENIED_PROBE, INLINE_MEDIA_STORE_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineMediaStoreDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_DOWNLOAD_MANAGER_DENIED_PROBE, INLINE_DOWNLOAD_MANAGER_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineDownloadManagerDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_INPUT_METHOD_DENIED_PROBE, INLINE_INPUT_METHOD_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineInputMethodDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_APP_OPS_DENIED_PROBE, INLINE_APP_OPS_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineAppOpsDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_PERMISSION_MANAGER_DENIED_PROBE, INLINE_PERMISSION_MANAGER_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlinePermissionManagerDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_SETTINGS_DENIED_PROBE, INLINE_SETTINGS_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineSettingsDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_POWER_MANAGER_DENIED_PROBE, INLINE_POWER_MANAGER_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlinePowerManagerDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_KEYGUARD_DENIED_PROBE, INLINE_KEYGUARD_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineKeyguardDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_WALLPAPER_DENIED_PROBE, INLINE_WALLPAPER_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineWallpaperDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_SHORTCUT_MANAGER_DENIED_PROBE, INLINE_SHORTCUT_MANAGER_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineShortcutManagerDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_ALARM_MANAGER_DENIED_PROBE, INLINE_ALARM_MANAGER_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineAlarmManagerDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_JOB_SCHEDULER_DENIED_PROBE, INLINE_JOB_SCHEDULER_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineJobSchedulerDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_WORK_MANAGER_DENIED_PROBE, INLINE_WORK_MANAGER_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineWorkManagerDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_CLIPBOARD_LISTENER_DENIED_PROBE, INLINE_CLIPBOARD_LISTENER_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineClipboardListenerDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_NOTIFICATION_LISTENER_DENIED_PROBE, INLINE_NOTIFICATION_LISTENER_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineNotificationListenerDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_ACCESSIBILITY_CONTROL_DENIED_PROBE, INLINE_ACCESSIBILITY_CONTROL_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineAccessibilityControlDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_DEVICE_POLICY_DENIED_PROBE, INLINE_DEVICE_POLICY_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineDevicePolicyDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_USAGE_STATS_DENIED_PROBE, INLINE_USAGE_STATS_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineUsageStatsDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_VPN_CONNECTIVITY_DENIED_PROBE, INLINE_VPN_CONNECTIVITY_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineVpnConnectivityDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_WIFI_MANAGER_DENIED_PROBE, INLINE_WIFI_MANAGER_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineWifiManagerDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_TELEPHONY_SUBSCRIPTION_DENIED_PROBE, INLINE_TELEPHONY_SUBSCRIPTION_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineTelephonySubscriptionDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_CAMERA_MANAGER_DENIED_PROBE, INLINE_CAMERA_MANAGER_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineCameraManagerDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_AUDIO_MANAGER_DENIED_PROBE, INLINE_AUDIO_MANAGER_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineAudioManagerDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_DISPLAY_MANAGER_DENIED_PROBE, INLINE_DISPLAY_MANAGER_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlineDisplayManagerDeniedProbe),
            InlineProbeRequest(EXTRA_INLINE_PRINT_MANAGER_DENIED_PROBE, INLINE_PRINT_MANAGER_DENIED_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallInlinePrintManagerDeniedProbe),
            InlineProbeRequest(EXTRA_USER_SOURCE_DESCRIPTOR_PROBE, USER_SOURCE_DESCRIPTOR_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallUserSourceDescriptorProbe),
            InlineProbeRequest(EXTRA_USER_SOURCE_SIZE_PROBE, USER_SOURCE_SIZE_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallUserSourceSizeProbe),
            InlineProbeRequest(EXTRA_USER_SOURCE_ENCODING_PROBE, USER_SOURCE_ENCODING_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallUserSourceEncodingProbe),
            InlineProbeRequest(EXTRA_USER_SOURCE_NAME_PROBE, USER_SOURCE_NAME_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallUserSourceNameProbe),
            InlineProbeRequest(EXTRA_USER_SOURCE_WRAPPER_PROBE, USER_SOURCE_WRAPPER_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallUserSourceWrapperProbe),
            InlineProbeRequest(EXTRA_USER_SOURCE_STRICT_MODE_PROBE, USER_SOURCE_STRICT_MODE_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallUserSourceStrictModeProbe),
            InlineProbeRequest(EXTRA_USER_SOURCE_CAPABILITY_PROBE, USER_SOURCE_CAPABILITY_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallUserSourceCapabilityProbe),
            InlineProbeRequest(EXTRA_USER_SOURCE_PREFLIGHT_PROBE, USER_SOURCE_PREFLIGHT_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallUserSourcePreflightProbe),
            InlineProbeRequest(EXTRA_CONTROLLED_USER_INLINE_CONSTANT_PROBE, CONTROLLED_USER_INLINE_CONSTANT_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallControlledUserInlineConstantProbe),
            InlineProbeRequest(EXTRA_CONTROLLED_USER_INLINE_STDOUT_PROBE, CONTROLLED_USER_INLINE_STDOUT_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallControlledUserInlineStdoutProbe),
            InlineProbeRequest(EXTRA_CONTROLLED_USER_INLINE_STDERR_PROBE, CONTROLLED_USER_INLINE_STDERR_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallControlledUserInlineStderrProbe),
            InlineProbeRequest(EXTRA_CONTROLLED_USER_INLINE_RETURN_VALUE_PROBE, CONTROLLED_USER_INLINE_RETURN_VALUE_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallControlledUserInlineReturnValueProbe),
            InlineProbeRequest(EXTRA_CONTROLLED_USER_INLINE_THROWN_ERROR_PROBE, CONTROLLED_USER_INLINE_THROWN_ERROR_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallControlledUserInlineThrownErrorProbe),
            InlineProbeRequest(EXTRA_CONTROLLED_USER_INLINE_PROMISE_PROBE, CONTROLLED_USER_INLINE_PROMISE_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallControlledUserInlinePromiseProbe),
            InlineProbeRequest(EXTRA_CONTROLLED_USER_INLINE_ASYNC_ORDERING_PROBE, CONTROLLED_USER_INLINE_ASYNC_ORDERING_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallControlledUserInlineAsyncOrderingProbe),
            InlineProbeRequest(EXTRA_CONTROLLED_USER_INLINE_SUMMARY_PROBE, CONTROLLED_USER_INLINE_SUMMARY_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallControlledUserInlineSummaryProbe),
            InlineProbeRequest(EXTRA_USER_FILE_DESCRIPTOR_PROBE, USER_FILE_DESCRIPTOR_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallUserFileDescriptorProbe),
            InlineProbeRequest(EXTRA_USER_FILE_READ_POLICY_PROBE, USER_FILE_READ_POLICY_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallUserFileReadPolicyProbe),
            InlineProbeRequest(EXTRA_USER_FILE_PATH_NORMALIZATION_PROBE, USER_FILE_PATH_NORMALIZATION_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallUserFilePathNormalizationProbe),
            InlineProbeRequest(EXTRA_USER_FILE_WORKING_DIRECTORY_PROBE, USER_FILE_WORKING_DIRECTORY_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallUserFileWorkingDirectoryProbe),
            InlineProbeRequest(EXTRA_USER_FILE_SOURCE_LOADING_PROBE, USER_FILE_SOURCE_LOADING_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallUserFileSourceLoadingProbe),
            InlineProbeRequest(EXTRA_USER_FILE_EXECUTION_DRY_RUN_PROBE, USER_FILE_EXECUTION_DRY_RUN_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallUserFileExecutionDryRunProbe),
            InlineProbeRequest(EXTRA_USER_FILE_ERROR_STACK_FILENAME_PROBE, USER_FILE_ERROR_STACK_FILENAME_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallUserFileErrorStackFilenameProbe),
            InlineProbeRequest(EXTRA_USER_FILE_EXECUTION_SUMMARY_PROBE, USER_FILE_EXECUTION_SUMMARY_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallUserFileExecutionSummaryProbe),
            InlineProbeRequest(EXTRA_EMBEDDED_SCRIPT_REQUEST_PROBE, EMBEDDED_SCRIPT_REQUEST_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallEmbeddedScriptRequestProbe),
            InlineProbeRequest(EXTRA_EMBEDDED_SCRIPT_RESULT_PROBE, EMBEDDED_SCRIPT_RESULT_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallEmbeddedScriptResultProbe),
            InlineProbeRequest(EXTRA_EMBEDDED_SCRIPT_OUTPUT_EVENT_PROBE, EMBEDDED_SCRIPT_OUTPUT_EVENT_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallEmbeddedScriptOutputEventProbe),
            InlineProbeRequest(EXTRA_EMBEDDED_SCRIPT_ERROR_PROBE, EMBEDDED_SCRIPT_ERROR_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallEmbeddedScriptErrorProbe),
            InlineProbeRequest(EXTRA_EMBEDDED_SCRIPT_TIMEOUT_PROBE, EMBEDDED_SCRIPT_TIMEOUT_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallEmbeddedScriptTimeoutProbe),
            InlineProbeRequest(EXTRA_EMBEDDED_SCRIPT_CANCELLATION_PROBE, EMBEDDED_SCRIPT_CANCELLATION_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallEmbeddedScriptCancellationProbe),
            InlineProbeRequest(EXTRA_EMBEDDED_SCRIPT_PROCESS_ISOLATION_PROBE, EMBEDDED_SCRIPT_PROCESS_ISOLATION_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallEmbeddedScriptProcessIsolationProbe),
            InlineProbeRequest(EXTRA_EMBEDDED_SCRIPT_CONTRACT_PROBE, EMBEDDED_SCRIPT_CONTRACT_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallEmbeddedScriptContractProbe),
            InlineProbeRequest(EXTRA_EMBEDDED_MVP_LIFECYCLE_PROBE, EMBEDDED_MVP_LIFECYCLE_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallEmbeddedMvpLifecycleProbe),
            InlineProbeRequest(EXTRA_EMBEDDED_MVP_SOURCE_INPUT_PROBE, EMBEDDED_MVP_SOURCE_INPUT_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallEmbeddedMvpSourceInputProbe),
            InlineProbeRequest(EXTRA_EMBEDDED_MVP_OUTPUT_PROBE, EMBEDDED_MVP_OUTPUT_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallEmbeddedMvpOutputProbe),
            InlineProbeRequest(EXTRA_EMBEDDED_MVP_ERROR_HANDLING_PROBE, EMBEDDED_MVP_ERROR_HANDLING_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallEmbeddedMvpErrorHandlingProbe),
            InlineProbeRequest(EXTRA_EMBEDDED_MVP_ASYNC_PROBE, EMBEDDED_MVP_ASYNC_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallEmbeddedMvpAsyncProbe),
            InlineProbeRequest(EXTRA_EMBEDDED_MVP_TIMEOUT_ISOLATION_PROBE, EMBEDDED_MVP_TIMEOUT_ISOLATION_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallEmbeddedMvpTimeoutIsolationProbe),
            InlineProbeRequest(EXTRA_EMBEDDED_MVP_SECURITY_POLICY_PROBE, EMBEDDED_MVP_SECURITY_POLICY_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallEmbeddedMvpSecurityPolicyProbe),
            InlineProbeRequest(EXTRA_EMBEDDED_MVP_GO_NO_GO_PROBE, EMBEDDED_MVP_GO_NO_GO_PROBE_PROPERTY, NativeNodeEmbeddedRuntimeBridge::shouldCallEmbeddedMvpGoNoGoProbe),
        )

        private val INLINE_PROBE_REQUEST_PROPERTIES = INLINE_PROBE_REQUESTS
            .map { it.extraName to it.propertyName }
            .toTypedArray()

        private val PROBE_REQUEST_PROPERTIES = arrayOf(
            EXTRA_INITIALIZE_PROBE to INITIALIZE_PROBE_PROPERTY,
            EXTRA_ISOLATE_PROBE to ISOLATE_PROBE_PROPERTY,
            EXTRA_UVLOOP_PROBE to UVLOOP_PROBE_PROPERTY,
            EXTRA_V8_PROBE to V8_PROBE_PROPERTY,
            EXTRA_UVDIAGNOSTICS_PROBE to UVDIAGNOSTICS_PROBE_PROPERTY,
            EXTRA_UVCLOSE_PROBE to UVCLOSE_PROBE_PROPERTY,
            EXTRA_UVRUN_PROBE to UVRUN_PROBE_PROPERTY,
            EXTRA_ISOLATEDATA_PROBE to ISOLATEDATA_PROBE_PROPERTY,
            EXTRA_ENVIRONMENT_PROBE to ENVIRONMENT_PROBE_PROPERTY,
            EXTRA_LOAD_ENVIRONMENT_PROBE to LOAD_ENVIRONMENT_PROBE_PROPERTY,
            EXTRA_SPIN_EVENT_LOOP_PROBE to SPIN_EVENT_LOOP_PROBE_PROPERTY,
            EXTRA_INLINE_JS_PROBE to INLINE_JS_PROBE_PROPERTY,
            EXTRA_PROCESS_JS_PROBE to PROCESS_JS_PROBE_PROPERTY,
            EXTRA_CONSOLE_JS_PROBE to CONSOLE_JS_PROBE_PROPERTY,
            EXTRA_STDOUT_CAPTURE_PROBE to STDOUT_CAPTURE_PROBE_PROPERTY,
            EXTRA_JS_RESULT_PROBE to JS_RESULT_PROBE_PROPERTY,
            EXTRA_STDOUT_WRITE_PROBE to STDOUT_WRITE_PROBE_PROPERTY,
            EXTRA_CONSOLE_DIAGNOSTICS_PROBE to CONSOLE_DIAGNOSTICS_PROBE_PROPERTY,
            EXTRA_CONSOLE_STREAM_PROBE to CONSOLE_STREAM_PROBE_PROPERTY,
            EXTRA_CONSOLE_SHAPE_PROBE to CONSOLE_SHAPE_PROBE_PROPERTY,
            EXTRA_CONSOLE_REPLACE_PROBE to CONSOLE_REPLACE_PROBE_PROPERTY,
            EXTRA_CONSOLE_FAMILY_PROBE to CONSOLE_FAMILY_PROBE_PROPERTY,
            EXTRA_CONSOLE_FORMAT_PROBE to CONSOLE_FORMAT_PROBE_PROPERTY,
            EXTRA_CONSOLE_REJECTION_PROBE to CONSOLE_REJECTION_PROBE_PROPERTY,
            EXTRA_CONSOLE_UNCAUGHT_PROBE to CONSOLE_UNCAUGHT_PROBE_PROPERTY,
            EXTRA_SCHEDULING_PROBE to SCHEDULING_PROBE_PROPERTY,
            EXTRA_SCHEDULING_ORDER_PROBE to SCHEDULING_ORDER_PROBE_PROPERTY,
            EXTRA_ASYNC_CONSOLE_PROBE to ASYNC_CONSOLE_PROBE_PROPERTY,
            EXTRA_ASYNC_ERROR_PROBE to ASYNC_ERROR_PROBE_PROPERTY,
            EXTRA_BOOTSTRAP_PROBE to BOOTSTRAP_PROBE_PROPERTY,
            EXTRA_BOOTSTRAP_SCRIPT_PROBE to BOOTSTRAP_SCRIPT_PROBE_PROPERTY,
            EXTRA_SCRIPT_COMPLETION_PROBE to SCRIPT_COMPLETION_PROBE_PROPERTY,
            EXTRA_SCRIPT_FAILURE_PROBE to SCRIPT_FAILURE_PROBE_PROPERTY,
            EXTRA_COMPLETION_DRAIN_PROBE to COMPLETION_DRAIN_PROBE_PROPERTY,
            EXTRA_POST_COMPLETION_ERROR_PROBE to POST_COMPLETION_ERROR_PROBE_PROPERTY,
            EXTRA_OUTPUT_EVENTS_PROBE to OUTPUT_EVENTS_PROBE_PROPERTY,
            EXTRA_OUTPUT_ENVELOPE_PROBE to OUTPUT_ENVELOPE_PROBE_PROPERTY,
            EXTRA_SCRIPT_CANCEL_PROBE to SCRIPT_CANCEL_PROBE_PROPERTY,
            EXTRA_CAPABILITY_DESCRIPTOR_PROBE to CAPABILITY_DESCRIPTOR_PROBE_PROPERTY,
            EXTRA_PLUGIN_CONTRACT_PROBE to PLUGIN_CONTRACT_PROBE_PROPERTY,
            EXTRA_PLUGIN_REQUEST_PROBE to PLUGIN_REQUEST_PROBE_PROPERTY,
            EXTRA_PLUGIN_EVENTS_PROBE to PLUGIN_EVENTS_PROBE_PROPERTY,
            EXTRA_PLUGIN_FAILURE_PROBE to PLUGIN_FAILURE_PROBE_PROPERTY,
            EXTRA_PLUGIN_CANCEL_PROBE to PLUGIN_CANCEL_PROBE_PROPERTY,
            EXTRA_PLUGIN_DISPOSE_PROBE to PLUGIN_DISPOSE_PROBE_PROPERTY,
            EXTRA_PLUGIN_LIFECYCLE_PROBE to PLUGIN_LIFECYCLE_PROBE_PROPERTY,
            EXTRA_PLUGIN_INVALID_REQUEST_PROBE to PLUGIN_INVALID_REQUEST_PROBE_PROPERTY,
            EXTRA_PLUGIN_STATE_MACHINE_PROBE to PLUGIN_STATE_MACHINE_PROBE_PROPERTY,
            EXTRA_PLUGIN_TIMEOUT_PROBE to PLUGIN_TIMEOUT_PROBE_PROPERTY,
            EXTRA_PLUGIN_CRASH_PROBE to PLUGIN_CRASH_PROBE_PROPERTY,
            EXTRA_PLUGIN_BACKPRESSURE_PROBE to PLUGIN_BACKPRESSURE_PROBE_PROPERTY,
            EXTRA_PLUGIN_NEGOTIATION_PROBE to PLUGIN_NEGOTIATION_PROBE_PROPERTY,
            EXTRA_PLUGIN_PERMISSION_PROBE to PLUGIN_PERMISSION_PROBE_PROPERTY,
            EXTRA_PLUGIN_MANIFEST_PROBE to PLUGIN_MANIFEST_PROBE_PROPERTY,
            EXTRA_PLUGIN_ABI_PROBE to PLUGIN_ABI_PROBE_PROPERTY,
            EXTRA_PLUGIN_LIBRARY_PROBE to PLUGIN_LIBRARY_PROBE_PROPERTY,
            EXTRA_PLUGIN_INSTANCE_PROBE to PLUGIN_INSTANCE_PROBE_PROPERTY,
            EXTRA_PLUGIN_SESSION_PROBE to PLUGIN_SESSION_PROBE_PROPERTY,
            EXTRA_PLUGIN_QUEUE_PROBE to PLUGIN_QUEUE_PROBE_PROPERTY,
            EXTRA_PLUGIN_CONCURRENCY_PROBE to PLUGIN_CONCURRENCY_PROBE_PROPERTY,
            EXTRA_PLUGIN_RACE_PROBE to PLUGIN_RACE_PROBE_PROPERTY,
            EXTRA_PLUGIN_SHUTDOWN_PROBE to PLUGIN_SHUTDOWN_PROBE_PROPERTY,
            EXTRA_PLUGIN_HEARTBEAT_PROBE to PLUGIN_HEARTBEAT_PROBE_PROPERTY,
            EXTRA_PLUGIN_HEALTH_PROBE to PLUGIN_HEALTH_PROBE_PROPERTY,
            EXTRA_PLUGIN_TELEMETRY_PROBE to PLUGIN_TELEMETRY_PROBE_PROPERTY,
            EXTRA_PLUGIN_DIAGNOSTICS_PROBE to PLUGIN_DIAGNOSTICS_PROBE_PROPERTY,
            EXTRA_PLUGIN_RECOVERY_PROBE to PLUGIN_RECOVERY_PROBE_PROPERTY,
            EXTRA_PLUGIN_RESTART_PROBE to PLUGIN_RESTART_PROBE_PROPERTY,
            EXTRA_PLUGIN_RESTART_BUDGET_PROBE to PLUGIN_RESTART_BUDGET_PROBE_PROPERTY,
            EXTRA_PLUGIN_QUARANTINE_PROBE to PLUGIN_QUARANTINE_PROBE_PROPERTY,
            EXTRA_SCRIPT_DESCRIPTOR_PROBE to SCRIPT_DESCRIPTOR_PROBE_PROPERTY,
            EXTRA_SCRIPT_NORMALIZATION_PROBE to SCRIPT_NORMALIZATION_PROBE_PROPERTY,
            EXTRA_SCRIPT_CONTEXT_PROBE to SCRIPT_CONTEXT_PROBE_PROPERTY,
            EXTRA_SCRIPT_RESULT_V2_PROBE to SCRIPT_RESULT_V2_PROBE_PROPERTY,
            EXTRA_SCRIPT_CANCELLATION_TOKEN_PROBE to SCRIPT_CANCELLATION_TOKEN_PROBE_PROPERTY,
            EXTRA_SCRIPT_TIMEOUT_POLICY_PROBE to SCRIPT_TIMEOUT_POLICY_PROBE_PROPERTY,
            EXTRA_SCRIPT_MEMORY_POLICY_PROBE to SCRIPT_MEMORY_POLICY_PROBE_PROPERTY,
            EXTRA_SCRIPT_PREFLIGHT_SUMMARY_PROBE to SCRIPT_PREFLIGHT_SUMMARY_PROBE_PROPERTY,
            *INLINE_PROBE_REQUEST_PROPERTIES,
        )

        private fun Intent.putInlineProbeExtras() {
            INLINE_PROBE_REQUESTS.forEach { request ->
                putExtra(request.extraName, request.requested())
            }
        }

        fun runProbe(
            context: Context,
            timeoutMs: Long = NativeNodeEmbeddedRuntimeBridge.currentProbeTimeoutMs(),
        ): ProbeIpcResult {
            val appContext = context.applicationContext
            val probeLevel = NativeNodeEmbeddedRuntimeBridge.currentProbeLevel()
            val lifecycleProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallLifecycleProbe()
            val isolateProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallIsolateProbe()
            val uvLoopProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallUvLoopProbe()
            val v8ProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallV8Probe()
            val uvDiagnosticsProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallUvDiagnosticsProbe()
            val uvCloseProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallUvCloseProbe()
            val uvRunProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallUvRunProbe()
            val isolateDataProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallIsolateDataProbe()
            val environmentProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallEnvironmentProbe()
            val loadEnvironmentProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallLoadEnvironmentProbe()
            val spinEventLoopProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallSpinEventLoopProbe()
            val inlineJsProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallInlineJsProbe()
            val processJsProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallProcessJsProbe()
            val consoleJsProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallConsoleJsProbe()
            val stdoutCaptureProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallStdoutCaptureProbe()
            val jsResultProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallJsResultProbe()
            val stdoutWriteProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallStdoutWriteProbe()
            val consoleDiagnosticsProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallConsoleDiagnosticsProbe()
            val consoleStreamProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallConsoleStreamProbe()
            val consoleShapeProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallConsoleShapeProbe()
            val consoleReplaceProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallConsoleReplaceProbe()
            val consoleFamilyProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallConsoleFamilyProbe()
            val consoleFormatProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallConsoleFormatProbe()
            val consoleRejectionProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallConsoleRejectionProbe()
            val consoleUncaughtProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallConsoleUncaughtProbe()
            val schedulingProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallSchedulingProbe()
            val schedulingOrderProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallSchedulingOrderProbe()
            val asyncConsoleProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallAsyncConsoleProbe()
            val asyncErrorProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallAsyncErrorProbe()
            val bootstrapProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallBootstrapProbe()
            val bootstrapScriptProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallBootstrapScriptProbe()
            val scriptCompletionProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallScriptCompletionProbe()
            val scriptFailureProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallScriptFailureProbe()
            val completionDrainProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallCompletionDrainProbe()
            val postCompletionErrorProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPostCompletionErrorProbe()
            val outputEventsProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallOutputEventsProbe()
            val outputEnvelopeProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallOutputEnvelopeProbe()
            val scriptCancelProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallScriptCancelProbe()
            val capabilityDescriptorProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallCapabilityDescriptorProbe()
            val pluginContractProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginContractProbe()
            val pluginRequestProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginRequestProbe()
            val pluginEventsProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginEventsProbe()
            val pluginFailureProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginFailureProbe()
            val pluginCancelProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginCancelProbe()
            val pluginDisposeProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginDisposeProbe()
            val pluginLifecycleProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginLifecycleProbe()
            val pluginInvalidRequestProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginInvalidRequestProbe()
            val pluginStateMachineProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginStateMachineProbe()
            val pluginTimeoutProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginTimeoutProbe()
            val pluginCrashProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginCrashProbe()
            val pluginBackpressureProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginBackpressureProbe()
            val pluginNegotiationProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginNegotiationProbe()
            val pluginPermissionProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginPermissionProbe()
            val pluginManifestProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginManifestProbe()
            val pluginAbiProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginAbiProbe()
            val pluginLibraryProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginLibraryProbe()
            val pluginInstanceProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginInstanceProbe()
            val pluginSessionProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginSessionProbe()
            val pluginQueueProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginQueueProbe()
            val pluginConcurrencyProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginConcurrencyProbe()
            val pluginRaceProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginRaceProbe()
            val pluginShutdownProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginShutdownProbe()
            val pluginHeartbeatProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginHeartbeatProbe()
            val pluginHealthProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginHealthProbe()
            val pluginTelemetryProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginTelemetryProbe()
            val pluginDiagnosticsProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginDiagnosticsProbe()
            val pluginRecoveryProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginRecoveryProbe()
            val pluginRestartProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginRestartProbe()
            val pluginRestartBudgetProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginRestartBudgetProbe()
            val pluginQuarantineProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallPluginQuarantineProbe()
            val scriptDescriptorProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallScriptDescriptorProbe()
            val scriptNormalizationProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallScriptNormalizationProbe()
            val scriptContextProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallScriptContextProbe()
            val scriptResultV2ProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallScriptResultV2Probe()
            val scriptCancellationTokenProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallScriptCancellationTokenProbe()
            val scriptTimeoutPolicyProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallScriptTimeoutPolicyProbe()
            val scriptMemoryPolicyProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallScriptMemoryPolicyProbe()
            val scriptPreflightSummaryProbeRequested = NativeNodeEmbeddedRuntimeBridge.shouldCallScriptPreflightSummaryProbe()
            val completion = CountDownLatch(1)
            val result = AtomicReference<ProbeIpcResult>()
            val receiver = object : ResultReceiver(null) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    if (resultCode != RESULT_PROBE) {
                        return
                    }
                    result.set(
                        ProbeIpcResult(
                            succeeded = resultData?.getBoolean(EXTRA_SUCCEEDED) ?: false,
                            formattedLog = resultData?.getString(EXTRA_FORMATTED_LOG).orEmpty(),
                            errorMessage = resultData?.getString(EXTRA_ERROR_MESSAGE),
                        )
                    )
                    completion.countDown()
                }
            }
            val intent = Intent(appContext, NativeNodeEmbeddedProbeService::class.java).apply {
                action = ACTION_PROBE
                putExtra(EXTRA_RESULT_RECEIVER, receiver)
                putExtra(EXTRA_PROBE_LEVEL, probeLevel)
                putExtra(EXTRA_INITIALIZE_PROBE, lifecycleProbeRequested)
                putExtra(EXTRA_ISOLATE_PROBE, isolateProbeRequested)
                putExtra(EXTRA_UVLOOP_PROBE, uvLoopProbeRequested)
                putExtra(EXTRA_V8_PROBE, v8ProbeRequested)
                putExtra(EXTRA_UVDIAGNOSTICS_PROBE, uvDiagnosticsProbeRequested)
                putExtra(EXTRA_UVCLOSE_PROBE, uvCloseProbeRequested)
                putExtra(EXTRA_UVRUN_PROBE, uvRunProbeRequested)
                putExtra(EXTRA_ISOLATEDATA_PROBE, isolateDataProbeRequested)
                putExtra(EXTRA_ENVIRONMENT_PROBE, environmentProbeRequested)
                putExtra(EXTRA_LOAD_ENVIRONMENT_PROBE, loadEnvironmentProbeRequested)
                putExtra(EXTRA_SPIN_EVENT_LOOP_PROBE, spinEventLoopProbeRequested)
                putExtra(EXTRA_INLINE_JS_PROBE, inlineJsProbeRequested)
                putExtra(EXTRA_PROCESS_JS_PROBE, processJsProbeRequested)
                putExtra(EXTRA_CONSOLE_JS_PROBE, consoleJsProbeRequested)
                putExtra(EXTRA_STDOUT_CAPTURE_PROBE, stdoutCaptureProbeRequested)
                putExtra(EXTRA_JS_RESULT_PROBE, jsResultProbeRequested)
                putExtra(EXTRA_STDOUT_WRITE_PROBE, stdoutWriteProbeRequested)
                putExtra(EXTRA_CONSOLE_DIAGNOSTICS_PROBE, consoleDiagnosticsProbeRequested)
                putExtra(EXTRA_CONSOLE_STREAM_PROBE, consoleStreamProbeRequested)
                putExtra(EXTRA_CONSOLE_SHAPE_PROBE, consoleShapeProbeRequested)
                putExtra(EXTRA_CONSOLE_REPLACE_PROBE, consoleReplaceProbeRequested)
                putExtra(EXTRA_CONSOLE_FAMILY_PROBE, consoleFamilyProbeRequested)
                putExtra(EXTRA_CONSOLE_FORMAT_PROBE, consoleFormatProbeRequested)
                putExtra(EXTRA_CONSOLE_REJECTION_PROBE, consoleRejectionProbeRequested)
                putExtra(EXTRA_CONSOLE_UNCAUGHT_PROBE, consoleUncaughtProbeRequested)
                putExtra(EXTRA_SCHEDULING_PROBE, schedulingProbeRequested)
                putExtra(EXTRA_SCHEDULING_ORDER_PROBE, schedulingOrderProbeRequested)
                putExtra(EXTRA_ASYNC_CONSOLE_PROBE, asyncConsoleProbeRequested)
                putExtra(EXTRA_ASYNC_ERROR_PROBE, asyncErrorProbeRequested)
                putExtra(EXTRA_BOOTSTRAP_PROBE, bootstrapProbeRequested)
                putExtra(EXTRA_BOOTSTRAP_SCRIPT_PROBE, bootstrapScriptProbeRequested)
                putExtra(EXTRA_SCRIPT_COMPLETION_PROBE, scriptCompletionProbeRequested)
                putExtra(EXTRA_SCRIPT_FAILURE_PROBE, scriptFailureProbeRequested)
                putExtra(EXTRA_COMPLETION_DRAIN_PROBE, completionDrainProbeRequested)
                putExtra(EXTRA_POST_COMPLETION_ERROR_PROBE, postCompletionErrorProbeRequested)
                putExtra(EXTRA_OUTPUT_EVENTS_PROBE, outputEventsProbeRequested)
                putExtra(EXTRA_OUTPUT_ENVELOPE_PROBE, outputEnvelopeProbeRequested)
                putExtra(EXTRA_SCRIPT_CANCEL_PROBE, scriptCancelProbeRequested)
                putExtra(EXTRA_CAPABILITY_DESCRIPTOR_PROBE, capabilityDescriptorProbeRequested)
                putExtra(EXTRA_PLUGIN_CONTRACT_PROBE, pluginContractProbeRequested)
                putExtra(EXTRA_PLUGIN_REQUEST_PROBE, pluginRequestProbeRequested)
                putExtra(EXTRA_PLUGIN_EVENTS_PROBE, pluginEventsProbeRequested)
                putExtra(EXTRA_PLUGIN_FAILURE_PROBE, pluginFailureProbeRequested)
                putExtra(EXTRA_PLUGIN_CANCEL_PROBE, pluginCancelProbeRequested)
                putExtra(EXTRA_PLUGIN_DISPOSE_PROBE, pluginDisposeProbeRequested)
                putExtra(EXTRA_PLUGIN_LIFECYCLE_PROBE, pluginLifecycleProbeRequested)
                putExtra(EXTRA_PLUGIN_INVALID_REQUEST_PROBE, pluginInvalidRequestProbeRequested)
                putExtra(EXTRA_PLUGIN_STATE_MACHINE_PROBE, pluginStateMachineProbeRequested)
                putExtra(EXTRA_PLUGIN_TIMEOUT_PROBE, pluginTimeoutProbeRequested)
                putExtra(EXTRA_PLUGIN_CRASH_PROBE, pluginCrashProbeRequested)
                putExtra(EXTRA_PLUGIN_BACKPRESSURE_PROBE, pluginBackpressureProbeRequested)
                putExtra(EXTRA_PLUGIN_NEGOTIATION_PROBE, pluginNegotiationProbeRequested)
                putExtra(EXTRA_PLUGIN_PERMISSION_PROBE, pluginPermissionProbeRequested)
                putExtra(EXTRA_PLUGIN_MANIFEST_PROBE, pluginManifestProbeRequested)
                putExtra(EXTRA_PLUGIN_ABI_PROBE, pluginAbiProbeRequested)
                putExtra(EXTRA_PLUGIN_LIBRARY_PROBE, pluginLibraryProbeRequested)
                putExtra(EXTRA_PLUGIN_INSTANCE_PROBE, pluginInstanceProbeRequested)
                putExtra(EXTRA_PLUGIN_SESSION_PROBE, pluginSessionProbeRequested)
                putExtra(EXTRA_PLUGIN_QUEUE_PROBE, pluginQueueProbeRequested)
                putExtra(EXTRA_PLUGIN_CONCURRENCY_PROBE, pluginConcurrencyProbeRequested)
                putExtra(EXTRA_PLUGIN_RACE_PROBE, pluginRaceProbeRequested)
                putExtra(EXTRA_PLUGIN_SHUTDOWN_PROBE, pluginShutdownProbeRequested)
                putExtra(EXTRA_PLUGIN_HEARTBEAT_PROBE, pluginHeartbeatProbeRequested)
                putExtra(EXTRA_PLUGIN_HEALTH_PROBE, pluginHealthProbeRequested)
                putExtra(EXTRA_PLUGIN_TELEMETRY_PROBE, pluginTelemetryProbeRequested)
                putExtra(EXTRA_PLUGIN_DIAGNOSTICS_PROBE, pluginDiagnosticsProbeRequested)
                putExtra(EXTRA_PLUGIN_RECOVERY_PROBE, pluginRecoveryProbeRequested)
                putExtra(EXTRA_PLUGIN_RESTART_PROBE, pluginRestartProbeRequested)
                putExtra(EXTRA_PLUGIN_RESTART_BUDGET_PROBE, pluginRestartBudgetProbeRequested)
                putExtra(EXTRA_PLUGIN_QUARANTINE_PROBE, pluginQuarantineProbeRequested)
                putExtra(EXTRA_SCRIPT_DESCRIPTOR_PROBE, scriptDescriptorProbeRequested)
                putExtra(EXTRA_SCRIPT_NORMALIZATION_PROBE, scriptNormalizationProbeRequested)
                putExtra(EXTRA_SCRIPT_CONTEXT_PROBE, scriptContextProbeRequested)
                putExtra(EXTRA_SCRIPT_RESULT_V2_PROBE, scriptResultV2ProbeRequested)
                putExtra(EXTRA_SCRIPT_CANCELLATION_TOKEN_PROBE, scriptCancellationTokenProbeRequested)
                putExtra(EXTRA_SCRIPT_TIMEOUT_POLICY_PROBE, scriptTimeoutPolicyProbeRequested)
                putExtra(EXTRA_SCRIPT_MEMORY_POLICY_PROBE, scriptMemoryPolicyProbeRequested)
                putExtra(EXTRA_SCRIPT_PREFLIGHT_SUMMARY_PROBE, scriptPreflightSummaryProbeRequested)
                putInlineProbeExtras()
            }
            var component: android.content.ComponentName? = null
            try {
                component = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
                    ?: return processCrashedOrTimedOut(probeLevel, "Failed to start embedded probe service at level $probeLevel.")
                val completed = completion.await(timeoutMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
                if (!completed) {
                    appContext.stopService(Intent().setComponent(component))
                    return processCrashedOrTimedOut(probeLevel, "Embedded probe process crashed or timed out at level $probeLevel.")
                }
            } catch (e: InterruptedException) {
                component?.let { appContext.stopService(Intent().setComponent(it)) }
                Thread.currentThread().interrupt()
                throw ScriptInterruptedException(e)
            } catch (e: Throwable) {
                component?.let { appContext.stopService(Intent().setComponent(it)) }
                return processCrashedOrTimedOut(
                    probeLevel,
                    "Embedded probe process crashed or timed out at level $probeLevel.\n${e.message ?: e.javaClass.name}"
                )
            }
            return result.get()
                ?: processCrashedOrTimedOut(probeLevel, "Embedded probe process crashed or timed out at level $probeLevel.")
        }

        private fun processCrashedOrTimedOut(probeLevel: Int, message: String): ProbeIpcResult {
            return ProbeIpcResult(
                succeeded = false,
                formattedLog = buildString {
                    appendLine("Native Node embedded probe started")
                    appendLine("  backend=embedded")
                    appendLine("  probe.level=$probeLevel")
                    appendLine()
                    appendLine("Embedded probe error:")
                    append(message)
                }.trimEnd(),
                errorMessage = message,
            )
        }

        @Suppress("DEPRECATION")
        private fun Intent.resultReceiverExtra(name: String): ResultReceiver? {
            return getParcelableExtra(name)
        }
    }

    data class ProbeIpcResult(
        val succeeded: Boolean,
        val formattedLog: String,
        val errorMessage: String?,
    )
}

