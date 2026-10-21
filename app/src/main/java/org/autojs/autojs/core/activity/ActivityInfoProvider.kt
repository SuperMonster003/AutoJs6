package org.autojs.autojs.core.activity

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import org.autojs.autojs.app.AppOps.isUsageStatsPermissionGranted
import org.autojs.autojs.core.accessibility.event.AccessibilityDelegate
import org.autojs.autojs.runtime.api.Shell
import java.util.regex.Pattern

/**
 * Created by Stardust on Mar 9, 2017.
 */
class ActivityInfoProvider(private val context: Context) : AccessibilityDelegate {

    private val mPackageManager: PackageManager = context.packageManager

    @Volatile
    internal var accessibilityIdentity = ActivityWindowIdentity()
        private set

    @Volatile
    private var mLatestPackage: String = ""

    @Volatile
    private var mLatestActivity: String = ""

    @Volatile
    private var mLatestExternalPackage: String = ""

    @Volatile
    private var mLatestExternalActivity: String = ""

    private var mLatestComponentFromShell: ComponentName? = null

    private var mShell: Shell? = null
    private var mUseShell = false

    val latestPackage: String
        get() {
            val compFromShell = mLatestComponentFromShell
            if (useShell && compFromShell != null) {
                return compFromShell.packageName
            }
            if (useUsageStats) {
                mLatestPackage = getLatestPackageByUsageStats()
            }
            return mLatestPackage
        }

    val latestActivity: String
        get() {
            val compFromShell = mLatestComponentFromShell
            if (useShell && compFromShell != null) {
                return compFromShell.className
            }
            return mLatestActivity
        }

    val latestExternalComponent: ComponentName?
        get() {
            val compFromShell = mLatestComponentFromShell
            if (useShell && compFromShell != null && isExternalPackage(compFromShell.packageName)) {
                return compFromShell
            }
            return mLatestExternalPackage.takeIf { it.isNotEmpty() }?.let {
                ComponentName(it, mLatestExternalActivity)
            }
        }

    var useUsageStats: Boolean = false

    var useShell: Boolean
        get() = mUseShell
        set(value) {
            if (value) {
                mShell.let {
                    if (it == null) {
                        mShell = createShell(200)
                    }
                }
            } else {
                mShell?.exit()
                mShell = null
            }
            mUseShell = value
        }

    override val eventTypes: Set<Int>?
        get() = AccessibilityDelegate.ALL_EVENT_TYPES

    override fun onAccessibilityEvent(service: AccessibilityService, event: AccessibilityEvent): Boolean {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val window = service.getWindow(event.windowId)
            if (window?.isFocused != false) {
                setLatestComponent(event.packageName, event.className)
                return false
            }
        }
        return false
    }

    fun getLatestPackageByUsageStatsIfGranted(): String {
        if (isUsageStatsPermissionGranted(context)) {
            return getLatestPackageByUsageStats()
        }
        return mLatestPackage
    }

    fun getLatestExternalPackageByUsageStatsIfGranted(): String {
        if (isUsageStatsPermissionGranted(context)) {
            return getLatestExternalPackageByUsageStats()
        }
        return mLatestExternalPackage
    }

    private fun setLatestComponentFromShellOutput(output: String) {
        val matcher = WINDOW_PATTERN.matcher(output)
        if (!matcher.find() || matcher.groupCount() < 1) {
            Log.w(LOG_TAG, "invalid format: $output")
            return
        }
        val latestPackage = matcher.group(1)
        if (latestPackage != null && latestPackage.contains(":")) {
            return
        }
        val latestActivity = if (matcher.groupCount() >= 2) {
            matcher.group(2).orEmpty()
        } else {
            ""
        }
        Log.d(LOG_TAG, "setLatestComponent: output = $output, comp = $latestPackage/$latestActivity")
        if (latestPackage != null) {
            mLatestComponentFromShell = ComponentName(latestPackage, latestActivity)
            setLatestExternalComponent(latestPackage, latestActivity)
        }
    }

    private fun createShell(@Suppress("SameParameterValue") dumpInterval: Int): Shell {
        val shell = Shell(true)
        shell.setCallback(object : Shell.Callback {
            override fun onOutput(str: String) {
                /* Empty body. */
            }

            override fun onNewLine(line: String) {
                setLatestComponentFromShellOutput(line)
            }

            override fun onInitialized() {
                /* Empty body. */
            }

            override fun onInterrupted(e: InterruptedException) {
                /* Empty body. */
            }
        })
        shell.exec(DUMP_WINDOW_COMMAND.format(dumpInterval))
        return shell
    }

    private fun getLatestPackageByUsageStats(): String {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val current = System.currentTimeMillis()
        val beginTime = current - 60 * 60 * 1000
        val usageStats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, beginTime, current).orEmpty()
        return usageStats
            .filter { it.packageName?.let(::isPackageExists) == true }
            .maxByOrNull { it.lastTimeUsed }
            ?.packageName
            ?.also { mLatestPackage = it }
            ?: mLatestPackage
    }

    private fun getLatestExternalPackageByUsageStats(): String {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val current = System.currentTimeMillis()
        val beginTime = current - 60 * 60 * 1000
        val usageStats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, beginTime, current).orEmpty()
        return usageStats
            .filter { it.packageName?.let { packageName -> isExternalPackage(packageName) && isPackageExists(packageName) } == true }
            .maxByOrNull { it.lastTimeUsed }
            ?.packageName
            ?: mLatestExternalPackage
    }

    private fun setLatestComponent(latestPackage: CharSequence?, latestClass: CharSequence?) {
        if (latestPackage == null)
            return
        val latestPackageStr = latestPackage.toString()
        val latestClassStr = (latestClass ?: "").toString()
        if (isPackageExists(latestPackageStr)) {
            mLatestPackage = latestPackage.toString()
            mLatestActivity = latestClassStr
            accessibilityIdentity = accessibilityIdentity.observe(latestPackageStr, latestClassStr) { pkg, name ->
                try {
                    val component = ComponentName(pkg, name)
                    if (SDK_INT >= TIRAMISU) mPackageManager.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0))
                    else @Suppress("DEPRECATION") mPackageManager.getActivityInfo(component, 0)
                    true
                } catch (_: PackageManager.NameNotFoundException) { false }
            }
            setLatestExternalComponent(latestPackageStr, latestClassStr)
        }
        Log.d(LOG_TAG, "setLatestComponent: $latestPackage/$latestClassStr $mLatestPackage/$mLatestActivity")
    }

    private fun setLatestExternalComponent(packageName: String, className: String) {
        if (isExternalPackage(packageName) && isPackageExists(packageName)) {
            mLatestExternalPackage = packageName
            mLatestExternalActivity = className
        }
    }

    private fun isExternalPackage(packageName: String): Boolean {
        return packageName.isNotEmpty() && packageName != context.packageName && !packageName.contains(":")
    }

    private fun isPackageExists(packageName: String) = try {
        if (SDK_INT >= TIRAMISU) {
            mPackageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            mPackageManager.getPackageInfo(packageName, 0)
        }
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    companion object {
        @Suppress("RegExpRedundantEscape")
        private val WINDOW_PATTERN = Pattern.compile("""Window\{\S+\s\S+\s([^/]+)/?([^}]+)?\}""")
        private val DUMP_WINDOW_COMMAND = """
            oldActivity=""
            currentActivity=`dumpsys window windows | grep -E 'mCurrentFocus'`
            while true
            do
                if [[ ${'$'}oldActivity != ${'$'}currentActivity && ${'$'}currentActivity != *"=null"* ]]; then
                    echo ${'$'}currentActivity
                    oldActivity=${'$'}currentActivity
                fi
                currentActivity=`dumpsys window windows | grep -E 'mCurrentFocus'`
            done
        """.trimIndent()

        private const val LOG_TAG = "ActivityInfoProvider"
    }
}

private fun AccessibilityService.getWindow(windowId: Int): AccessibilityWindowInfo? {
    val windows = runCatching {
        this.windows
    }.onFailure {
        Log.w("ActivityInfoProvider", "Unable to get accessibility windows", it)
    }.getOrDefault(emptyList())
    windows.forEach {
        if (it.id == windowId) {
            return it
        }
    }
    return null
}
