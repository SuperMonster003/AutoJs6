package org.autojs.autojs.compat

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.StrictMode
import android.provider.Settings
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import org.autojs.autojs.AutoJs
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.StringScriptSource
import org.autojs.autojs.core.plugin.installer.PackageInstallRouter
import org.autojs.autojs.util.ActivityLaunchOptions
import org.json.JSONObject
import org.json.JSONArray
import java.io.File

/** Run in the real application UID, without instrumentation or adopted shell permissions. */
class ActivityLaunchCompatibilityProbeActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var caseId: String
    private lateinit var route: String
    private lateinit var result: JSONObject
    private var priorPolicy: StrictMode.VmPolicy? = null
    private var waitingForBelowAttempt = false
    private val launcher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        result.put("resultCode", it.resultCode).put("resultCase", it.data?.getStringExtra("case"))
        save()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        caseId = requireNotNull(intent.getStringExtra("case"))
        require(caseId.matches(Regex("[A-Za-z0-9_-]{1,80}")))
        route = requireNotNull(intent.getStringExtra("route"))
        result = JSONObject().put("case", caseId).put("route", route).put("sdk", Build.VERSION.SDK_INT)
            .put("startedAt", System.currentTimeMillis())
            .put("targetSdk", applicationInfo.targetSdkVersion).put("uid", Process.myUid())
            .put("pid", Process.myPid()).put("taskId", taskId).put("overlayPermission", Settings.canDrawOverlays(this))
            .put("background", intent.getBooleanExtra("background", false))
            .put("senderMode", intent.getStringExtra("senderMode") ?: "visible")
        setContentView(TextView(this).apply { text = "SDK37 BAL: $caseId"; textSize = 24f })
        if (Build.VERSION.SDK_INT >= 36) {
            priorPolicy = StrictMode.getVmPolicy()
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder(priorPolicy!!)
                .detectBlockedBackgroundActivityLaunch().penaltyLog()
                .penaltyListener(mainExecutor) { violation ->
                    result.put("strictMode", violation.toString())
                    save()
                }.build())
        }
        save()
        handler.postDelayed({
            if (intent.getBooleanExtra("background", false)) {
                moveTaskToBack(true)
                handler.postDelayed(::runProbe, 12_000)
            } else runProbe()
        }, 800)
    }

    private fun senderOptions(): ActivityOptionsCompat? = when (intent.getStringExtra("senderMode") ?: "visible") {
        "none" -> null
        "visible" -> ActivityLaunchOptions.forVisibleSender()
        "denied" -> if (Build.VERSION.SDK_INT < 34) null else object : ActivityOptionsCompat() {
            override fun toBundle(): Bundle = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_DENIED).toBundle()
        }
        else -> error("Unknown sender mode")
    }

    private fun runProbe() {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        result.put("sendImportance", info.importance).put("windowFocusAtSend", hasWindowFocus())
        try {
            when (route) {
                "result", "sender", "sender-legacy", "pending", "broadcast" -> {
                    val response = requireNotNull(contentResolver.call(FIXTURE_URI,
                        if (route == "broadcast") "broadcast" else "activity", caseId, null))
                    @Suppress("DEPRECATION")
                    val token = requireNotNull(response.getParcelable<PendingIntent>("token"))
                    // Keep the legacy overload separate: it has no options parameter.
                    val options = if (route == "sender-legacy") null else senderOptions()
                    result.put("creatorPackage", token.creatorPackage).put("creatorUid", token.creatorUid)
                        .put("creatorTarget", response.getInt("targetSdk"))
                    val optionBundle = options?.toBundle()
                    result.put("senderOptions", JSONObject().apply {
                        optionBundle?.keySet()?.forEach { key ->
                            @Suppress("DEPRECATION")
                            put(key, optionBundle.get(key))
                        }
                    })
                    when (route) {
                        "result" -> launcher.launch(IntentSenderRequest.Builder(token.intentSender).build(), options)
                        "sender-legacy" -> token.intentSender.sendIntent(this, 0, null, null, null)
                        "sender" -> {
                            // The options overload is public in API 37.
                            if (Build.VERSION.SDK_INT >= 37) token.intentSender.sendIntent(this, 0, null, null, optionBundle, mainExecutor, null)
                            else token.intentSender.sendIntent(this, 0, null, null, null)
                        }
                        else -> token.send(this, 0, null, null, null, null, options?.toBundle())
                    }
                    result.put("sendReturned", true)
                }
                "script-launch", "script-start" -> {
                    val script = if (route == "script-launch") "app.launch('$FIXTURE_PACKAGE');" else
                        "app.startActivity({packageName:'$FIXTURE_PACKAGE',className:'$FIXTURE_PACKAGE.TargetActivity',extras:{'case':'$caseId'}});"
                    AutoJs.instance.scriptEngineService.execute(StringScriptSource("sdk37-bal-$caseId", script),
                        object : SimpleScriptExecutionListener() {
                            override fun onSuccess(execution: ScriptExecution, value: Any?) {
                                handler.post {
                                    result.put("scriptCompleted", true).put("scriptResult", value?.toString())
                                    save()
                                    handler.postDelayed(::complete, 6_000)
                                }
                            }
                            override fun onException(execution: ScriptExecution, error: Throwable) {
                                handler.post { result.put("scriptError", error.toString()); complete() }
                            }
                        }, ExecutionConfig())
                }
                "asm" -> {
                    waitingForBelowAttempt = true
                    startActivity(Intent().setClassName(FIXTURE_PACKAGE, "$FIXTURE_PACKAGE.TargetActivity")
                        .putExtra("case", caseId).putExtra("hold", true).apply {
                            if (intent.hasExtra("allowBelow")) putExtra("allowBelow", intent.getBooleanExtra("allowBelow", false))
                        })
                }
                "install", "install-recovery" -> {
                    // The session-based host installer and its status coordinator moved to the
                    // 3-Setup Installer plugin (plugin roadmap P1.4); only the routed UI entry remains.
                    error("Route $route was removed with the host installer")
                }
                "install-ui" -> {
                    val apkName = intent.getStringExtra("apkName") ?: "sdk37-bal-install.apk"
                    require(apkName.matches(Regex("[A-Za-z0-9_.-]+\\.apk")))
                    result.put("installUiStarted", PackageInstallRouter.install(this, File(filesDir, apkName)))
                }
                else -> error("Unknown route: $route")
            }
        } catch (error: Throwable) {
            result.put("error", error.toString())
        }
        save()
        if (result.has("error") || route !in setOf("asm", "script-launch", "script-start")) {
            handler.postDelayed(::complete, if (route == "install") 12_000 else 6_000)
        }
    }

    override fun onStop() {
        super.onStop()
        if (waitingForBelowAttempt) {
            waitingForBelowAttempt = false
            result.put("probeStoppedAt", System.currentTimeMillis())
            save()
            handler.postDelayed({
                val info = ActivityManager.RunningAppProcessInfo()
                ActivityManager.getMyMemoryState(info)
                result.put("belowImportance", info.importance).put("belowAttemptedAt", System.currentTimeMillis())
                runCatching {
                    startActivity(Intent(this, ActivityLaunchProbeLandingActivity::class.java).putExtra("case", caseId))
                }.onFailure { result.put("belowError", it.toString()) }
                result.put("belowAttempted", true)
                save()
                handler.postDelayed(::complete, 6_000)
            }, 12_000)
        }
    }

    private fun complete() {
        if (route !in setOf("install", "install-recovery", "install-ui")) runCatching {
            val key = if (route == "script-launch") "launcher" else caseId
            val event = contentResolver.call(FIXTURE_URI, "read", key, null)?.getString("event") ?: "{}"
            result.put("fixtureEvent", JSONObject(event))
        }.onFailure { result.put("fixtureReadError", it.toString()) }
        File(filesDir, "sdk37-bal-$caseId-landing.json").takeIf { it.exists() }?.let {
            result.put("landing", JSONObject(it.readText()))
        }
        result.put("completed", true)
        save()
    }

    private fun save() {
        if (::result.isInitialized) File(filesDir, "sdk37-bal-$caseId.json").writeText(result.toString(2))
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        priorPolicy?.let(StrictMode::setVmPolicy)
        super.onDestroy()
    }

    companion object {
        const val FIXTURE_PACKAGE = "org.autojs.sdk37.balfixture"
        val FIXTURE_URI: Uri = Uri.parse("content://$FIXTURE_PACKAGE")
    }
}

class ActivityLaunchProbeLandingActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = requireNotNull(intent.getStringExtra("case"))
        require(id.matches(Regex("[A-Za-z0-9_-]{1,80}")))
        File(filesDir, "sdk37-bal-$id-landing.json").writeText(JSONObject().put("case", id)
            .put("uid", Process.myUid()).put("pid", Process.myPid()).put("taskId", taskId).toString())
        setContentView(TextView(this).apply { text = "SDK37 BAL landing: $id" })
    }
}
