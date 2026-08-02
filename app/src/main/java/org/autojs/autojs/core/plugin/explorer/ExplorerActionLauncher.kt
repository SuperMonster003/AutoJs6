package org.autojs.autojs.core.plugin.explorer

import android.app.Activity
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.explorer.api.ExplorerActionIntentExtras
import org.autojs.plugin.explorer.api.ExplorerActionPluginActions
import org.autojs.plugin.explorer.api.ExplorerActionPluginPermissions
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import java.io.File

internal object ExplorerActionLauncher {

    fun resolveAndValidateActivity(
        context: Context,
        packageName: String,
        activityClassName: String,
    ): ComponentName? {
        val normalizedClassName = ExplorerActionCatalogPolicy.normalizeActivityClassName(
            packageName,
            activityClassName,
        ) ?: return null
        val component = ComponentName(packageName, normalizedClassName)
        val activityInfo = getActivityInfo(context.packageManager, component) ?: return null
        if (!isActivityInfoValid(activityInfo, packageName)) return null
        if (!declaresExecuteAction(context.packageManager, packageName, component)) return null
        return component
    }

    fun launch(
        context: Context,
        surface: ExplorerSurface,
        item: ExplorerItem,
        action: ResolvedExplorerAction,
    ): Result<Unit> = runCatching {
        val sourceSurface = ExplorerActionPolicy.sourceSurfaceValue(surface)
            ?: error("Explorer Action surface is not allowed: $surface")
        val component = resolveAndValidateActivity(
            context = context,
            packageName = action.packageName,
            activityClassName = action.descriptor.activityClassName,
        ) ?: error("Explorer Action activity is unavailable: ${action.packageName}/${action.descriptor.activityClassName}")
        check(component == action.component) {
            "Explorer Action activity changed since discovery: ${action.packageName}/${action.descriptor.activityClassName}"
        }

        val file = File(item.path)
        require(file.isFile && file.canRead()) { "Explorer Action target is not a readable file: ${item.name}" }
        val parent = file.parentFile ?: error("Explorer Action target has no parent directory: ${item.name}")
        val targetUri = AppFileProvider.getUriForFile(context, file)
        val parentUri = AppFileProvider.getUriForFile(context, parent)
        val mimeType = Mime.fromFileOrWildcard(file.path)
        val clipData = ClipData.newRawUri(item.name, targetUri).apply {
            addItem(ClipData.Item(parentUri))
        }
        val intent = Intent(ExplorerActionPluginActions.EXECUTE).apply {
            setComponent(component)
            setDataAndType(targetUri, mimeType)
            this.clipData = clipData
            putExtra(ExplorerActionIntentExtras.ACTION_ID, action.descriptor.id)
            putExtra(ExplorerActionIntentExtras.PROTOCOL_VERSION, action.descriptor.protocolVersion)
            putExtra(ExplorerActionIntentExtras.DISPLAY_NAME, item.name)
            putExtra(ExplorerActionIntentExtras.SIZE, item.size)
            putExtra(ExplorerActionIntentExtras.PARENT_URI, parentUri)
            putExtra(ExplorerActionIntentExtras.HOST_PACKAGE_NAME, context.packageName)
            putExtra(ExplorerActionIntentExtras.HOST_VERSION_NAME, BuildConfig.VERSION_NAME)
            putExtra(ExplorerActionIntentExtras.HOST_VERSION_CODE, BuildConfig.VERSION_CODE.toLong())
            putExtra(ExplorerActionIntentExtras.SOURCE_SURFACE, sourceSurface)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    }

    private fun isActivityInfoValid(activityInfo: ActivityInfo, expectedPackageName: String): Boolean {
        return ExplorerActionAvailabilityPolicy.isActivityDeclarationValid(
            expectedPackageName = expectedPackageName,
            actualPackageName = activityInfo.packageName,
            exported = activityInfo.exported,
            activityEnabled = activityInfo.enabled,
            applicationEnabled = activityInfo.applicationInfo.enabled,
            requiredPermission = ExplorerActionPluginPermissions.PLUGIN,
            actualPermission = activityInfo.permission,
        )
    }

    private fun declaresExecuteAction(
        packageManager: PackageManager,
        packageName: String,
        expectedComponent: ComponentName,
    ): Boolean {
        val intent = Intent(ExplorerActionPluginActions.EXECUTE).apply {
            setPackage(packageName)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
        return resolveInfos.any { info ->
            info.activityInfo?.let { activityInfo ->
                ComponentName(activityInfo.packageName, activityInfo.name) == expectedComponent
            } == true
        }
    }

    private fun getActivityInfo(packageManager: PackageManager, component: ComponentName): ActivityInfo? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getActivityInfo(component, 0)
            }
        }.getOrNull()
    }
}
