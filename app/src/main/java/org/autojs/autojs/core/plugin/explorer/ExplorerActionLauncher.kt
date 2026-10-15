package org.autojs.autojs.core.plugin.explorer

import android.app.Activity
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import org.autojs.autojs.core.plugin.explorer.output.ExplorerActionOutputCoordinator
import org.autojs.autojs.core.plugin.explorer.session.ExplorerActionHostSession
import org.autojs.autojs.core.plugin.explorer.session.ExplorerActionHostSessionTarget
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.explorer.api.ExplorerActionIntentExtras
import org.autojs.plugin.explorer.api.ExplorerActionPluginActions
import org.autojs.plugin.explorer.api.ExplorerActionPluginPermissions
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionKeys
import org.autojs.plugin.explorer.api.ExplorerActionTargetKeys
import org.autojs.plugin.explorer.api.ExplorerActionValues
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.text.Charsets.UTF_8

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
        items: List<ExplorerItem>,
        action: ResolvedExplorerAction,
    ): Result<Unit> {
        if (ExplorerActionOutputCoordinator.supports(action)) {
            require(items.size == 1) { "Explorer Action output transactions require one target" }
            return ExplorerActionOutputCoordinator.launch(context, surface, items.single(), action)
        }
        return launchReadOnly(context, surface, items, action)
    }

    private fun launchReadOnly(
        context: Context,
        surface: ExplorerSurface,
        items: List<ExplorerItem>,
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

        require(items.isNotEmpty() && items.size <= ExplorerActionProtocol.MAX_TARGETS_PER_REQUEST) {
            "Explorer Action target count is invalid"
        }
        val targets = items.map { item -> resolveTarget(context, item, action.packageName) }
        val parent = targets.first().file.parentFile
            ?: error("Explorer Action target has no parent directory: ${targets.first().item.name}")
        val canonicalParent = parent.canonicalFile
        require(targets.all { target -> target.file.parentFile?.canonicalFile == canonicalParent }) {
            "Explorer Action targets do not share one parent directory"
        }
        require(
            action.descriptor.cardinality == ExplorerActionValues.CARDINALITY_MULTIPLE || targets.size == 1,
        ) {
            "Explorer Action descriptor does not accept multiple targets"
        }
        val targetUri = targets.first().uri
        val parentUri = AppFileProvider.getUriForFile(context, parent)
        val mimeType = if (targets.size == 1) targets.single().mimeType else Mime.WILDCARD
        // v23: the canonical path of the one selected directory replaces the host file session,
        // which such an action never needs; nothing else about the file system is disclosed.
        // zh-CN: v23: 所选单个目录的规范路径取代宿主文件会话 (此类动作不需要), 不披露文件系统的其它信息.
        val filesystemPath = if (action.descriptor.filesystemPath) {
            val directory = targets.single().file
            require(directory.isDirectory && directory.path.length <= ExplorerActionProtocol.MAX_FILESYSTEM_PATH_LENGTH) {
                "Explorer Action filesystem path is unavailable for ${targets.single().item.name}"
            }
            directory.path
        } else {
            null
        }
        val hostSession = if (
            filesystemPath == null &&
            action.descriptor.protocolVersion >= MULTI_TARGET_PROTOCOL_VERSION &&
            (
                action.descriptor.accessMode == ExplorerActionValues.ACCESS_CREATE_IN_PARENT ||
                    action.descriptor.accessMode == ExplorerActionValues.ACCESS_REPLACE_TARGET ||
                    action.descriptor.accessMode == ExplorerActionValues.ACCESS_MANAGE_TARGET ||
                    action.descriptor.readSiblings ||
                    action.descriptor.playbackProgress ||
                    action.descriptor.relatedFileSuffixes.isNotEmpty() ||
                    action.descriptor.targetTrash ||
                    action.descriptor.targetRename ||
                    targets.size > 1 ||
                    targets.any { target -> target.kind == ExplorerActionValues.TARGET_DIRECTORY }
                )
        ) {
            ExplorerActionHostSession.create(
                context = context,
                pluginPackageName = action.packageName,
                commonParent = canonicalParent,
                targets = targets.map { target ->
                    ExplorerActionHostSessionTarget(target.id, target.file)
                },
                allowCreateOutput =
                    action.descriptor.accessMode == ExplorerActionValues.ACCESS_CREATE_IN_PARENT ||
                        action.descriptor.accessMode == ExplorerActionValues.ACCESS_MANAGE_TARGET,
                allowReplaceTarget =
                    action.descriptor.accessMode == ExplorerActionValues.ACCESS_REPLACE_TARGET ||
                        action.descriptor.accessMode == ExplorerActionValues.ACCESS_MANAGE_TARGET,
                allowReplacementUndo =
                    action.descriptor.protocolVersion >= REPLACEMENT_UNDO_PROTOCOL_VERSION &&
                        (
                            action.descriptor.accessMode == ExplorerActionValues.ACCESS_REPLACE_TARGET ||
                                action.descriptor.accessMode == ExplorerActionValues.ACCESS_MANAGE_TARGET
                            ),
                allowReadSiblings = action.descriptor.readSiblings,
                allowPlaybackProgress = action.descriptor.playbackProgress,
                relatedFileSuffixes = action.descriptor.relatedFileSuffixes,
                allowTargetTrash = action.descriptor.targetTrash,
                allowTargetRename = action.descriptor.targetRename,
            )
        } else {
            null
        }
        val clipData = ClipData.newRawUri(targets.first().item.name, targetUri).apply {
            targets.drop(1).forEach { target -> addItem(ClipData.Item(target.uri)) }
            if (action.descriptor.protocolVersion < MULTI_TARGET_PROTOCOL_VERSION) {
                addItem(ClipData.Item(parentUri))
            }
        }
        val intent = Intent(ExplorerActionPluginActions.EXECUTE).apply {
            setComponent(component)
            setDataAndType(targetUri, mimeType)
            this.clipData = clipData
            putExtra(ExplorerActionIntentExtras.ACTION_ID, action.descriptor.id)
            putExtra(ExplorerActionIntentExtras.PROTOCOL_VERSION, action.descriptor.protocolVersion)
            putExtra(ExplorerActionIntentExtras.DISPLAY_NAME, targets.first().item.name)
            putExtra(ExplorerActionIntentExtras.SIZE, targets.first().size)
            putExtra(ExplorerActionIntentExtras.PARENT_URI, parentUri)
            putExtra(ExplorerActionIntentExtras.HOST_PACKAGE_NAME, context.packageName)
            putExtra(ExplorerActionIntentExtras.HOST_VERSION_NAME, BuildConfig.VERSION_NAME)
            putExtra(ExplorerActionIntentExtras.HOST_VERSION_CODE, BuildConfig.VERSION_CODE.toLong())
            putExtra(ExplorerActionIntentExtras.SOURCE_SURFACE, sourceSurface)
            if (action.descriptor.protocolVersion >= MULTI_TARGET_PROTOCOL_VERSION) {
                putExtra(ExplorerActionIntentExtras.REQUEST_ID, UUID.randomUUID().toString())
                putExtra(
                    ExplorerActionIntentExtras.PARENT_DISPLAY_PATH,
                    canonicalParent.path.take(ExplorerActionProtocol.MAX_PARENT_DISPLAY_PATH_LENGTH),
                )
                putParcelableArrayListExtra(
                    ExplorerActionIntentExtras.TARGETS,
                    ArrayList(
                        targets.map { target ->
                            target.toBundle().apply {
                                filesystemPath?.let { putString(ExplorerActionTargetKeys.FILESYSTEM_PATH, it) }
                            }
                        },
                    ),
                )
                hostSession?.let { session ->
                    putExtra(
                        ExplorerActionIntentExtras.HOST_SESSION,
                        Bundle().apply {
                            putBinder(ExplorerActionHostSessionKeys.BINDER, session)
                        },
                    )
                }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        var activityStarted = false
        try {
            ExplorerActionIntentSizePolicy.requireSafe(intent)
            context.startActivity(intent)
            activityStarted = true
        } finally {
            if (!activityStarted) {
                runCatching { hostSession?.close() }
            }
        }
    }

    private fun resolveTarget(context: Context, item: ExplorerItem, packageName: String): ResolvedTarget {
        val file = File(item.path).canonicalFile
        require(file.canRead() && (file.isFile || file.isDirectory)) {
            "Explorer Action target is not readable: ${item.name}"
        }
        val kind = if (file.isDirectory) {
            ExplorerActionValues.TARGET_DIRECTORY
        } else {
            ExplorerActionValues.TARGET_FILE
        }
        val mimeType = if (file.isDirectory) {
            DIRECTORY_MIME_TYPE
        } else {
            val targetType = ExplorerActionTargetTypePolicy.resolve(
                type = item.type,
                fallbackMimeType = Mime.fromFileOrWildcard(file.path),
                rawExtension = file.extension,
            )
            ExplorerM3uPlaylistPolicy.resolveMimeType(packageName, targetType.extension, targetType.mimeType)
        }
        return ResolvedTarget(
            id = stableTargetId(file),
            item = item,
            file = file,
            uri = AppFileProvider.getUriForFile(context, file),
            kind = kind,
            mimeType = mimeType,
            size = if (file.isFile) file.length() else SIZE_UNKNOWN,
        )
    }

    private data class ResolvedTarget(
        val id: String,
        val item: ExplorerItem,
        val file: File,
        val uri: android.net.Uri,
        val kind: Int,
        val mimeType: String,
        val size: Long,
    ) {
        fun toBundle(): Bundle = Bundle().apply {
            putString(ExplorerActionTargetKeys.ID, id)
            putParcelable(ExplorerActionTargetKeys.URI, uri)
            putString(
                ExplorerActionTargetKeys.DISPLAY_NAME,
                item.name.take(ExplorerActionProtocol.MAX_TARGET_DISPLAY_NAME_LENGTH),
            )
            putInt(ExplorerActionTargetKeys.KIND, kind)
            putString(ExplorerActionTargetKeys.MIME_TYPE, mimeType)
            putLong(ExplorerActionTargetKeys.SIZE, size)
            putLong(ExplorerActionTargetKeys.LAST_MODIFIED, item.lastModified())
        }
    }

    private fun stableTargetId(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.path.toByteArray(UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }

    private const val MULTI_TARGET_PROTOCOL_VERSION = 4
    private const val REPLACEMENT_UNDO_PROTOCOL_VERSION = 18
    private const val DIRECTORY_MIME_TYPE = "inode/directory"
    private const val SIZE_UNKNOWN = -1L

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
