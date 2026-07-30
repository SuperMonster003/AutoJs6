package org.autojs.autojs.ui.main.scripts

import android.content.Context
import android.content.Intent
import android.util.Log
import io.noties.prism4j.GrammarLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs6.R
import java.io.File
import java.util.regex.Pattern

class DisplayManifestActivity : BaseDisplayContentActivity() {

    override var internalMenuResource = R.menu.menu_display_manifest_fab
    override val deferTextRelayoutDuringScale = true

    override var highlightGrammarLocator: GrammarLocator = MarkupGrammarLocator()
    override var highlightGrammarName = MarkupGrammarLocator.GRAMMAR_NAME
    override var highlightThemeLanguage = "markup"

    private var mPermissionsContent: CharSequence = ""
    private var mOriginalText: CharSequence = ""
    private var mOriginalTextSelectionEnabled = true
    private var mContentReady = false

    init {
        popMenuActionMap += listOf(
            R.id.action_permissions to { showPermissionList() },
            R.id.action_back to { restoreOriginalContent() },
        )
    }

    override suspend fun loadAndDisplayContent() {
        setContentActionsEnabled(false)
        val packagePath = intent.getStringExtra(PATH_IDENTIFIER_PACKAGE)
        val permissions = intent.getStringArrayExtra(INTENT_IDENTIFIER_PERMISSIONS)
        val deviceSpec = PackageDeviceSpec.from(this)

        val manifestContentRaw = withContext(Dispatchers.IO) {
            packagePath?.let { path ->
                runCatching {
                    AndroidPackageArchiveInspector.inspect(File(path), deviceSpec).decodeDisplayManifest()
                }.onFailure { error ->
                    Log.w(TAG, "Failed to decode Android package manifest: $path", error)
                }
                    .getOrNull()
            }
        }
        val manifestContent = manifestContentRaw ?: getString(R.string.text_no_content)
        mPermissionsContent = withContext(Dispatchers.Default) {
            permissions
                ?.let(::handlePermissionsText)
                ?: manifestContentRaw
                    ?.let(::parsePermissionsFromManifest)
                    ?.let(::handlePermissionsText)
                ?: getString(R.string.text_no_content)
        }

        // Syntax highlighting turns the whole document into spans and delays first paint. The
        // formatted plain XML remains readable and keeps scrolling and pinch-to-zoom responsive.
        mOriginalText = manifestContent
        mOriginalTextSelectionEnabled = manifestContent.length <= SELECTABLE_TEXT_LIMIT
        setTextSelectionEnabled(mOriginalTextSelectionEnabled)
        setTextWithLock(manifestContent)
        mContentReady = true
        setContentActionsEnabled(true)
    }

    private fun showPermissionList() {
        if (!mContentReady) return
        internalMenuResource = R.menu.menu_display_permissions_fab
        setTextSelectionEnabled(true)
        setText(mPermissionsContent)
    }

    private fun restoreOriginalContent() {
        if (!mContentReady) return
        internalMenuResource = R.menu.menu_display_manifest_fab
        setTextSelectionEnabled(mOriginalTextSelectionEnabled)
        setText(mOriginalText)
    }

    companion object {

        private const val TAG = "DisplayManifest"
        private const val PATH_IDENTIFIER_PACKAGE = "PACKAGE_PATH"
        private const val INTENT_IDENTIFIER_PERMISSIONS = "permissions"
        private const val SELECTABLE_TEXT_LIMIT = 256 * 1024

        @JvmStatic
        @JvmOverloads
        fun launch(context: Context, packageFile: File, usesPermissions: List<String>? = null) {
            Intent(context, DisplayManifestActivity::class.java).apply {
                putExtra(PATH_IDENTIFIER_PACKAGE, packageFile.absolutePath)
                usesPermissions?.let {
                    putExtra(INTENT_IDENTIFIER_PERMISSIONS, it.toTypedArray())
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }.startSafely(context)
        }

        private fun parsePermissionsFromManifest(manifestContent: CharSequence): Array<String> {
            val permissionList = mutableListOf<String>()
            val pattern = Pattern.compile("""<uses-permission\b[^>]*\bandroid:name="([^"]+)"[^>]*?/?>""")
            val matcher = pattern.matcher(manifestContent)

            while (matcher.find()) {
                matcher.group(1)?.let { permissionList.add(it) }
            }

            return permissionList.toTypedArray()
        }

        private fun handlePermissionsText(usesPermissions: Array<String>): String {
            val androidPrefix = "android.permission."
            val android = mutableSetOf<String>()
            val others = mutableSetOf<String>()
            usesPermissions.forEach { permission ->
                when (permission.startsWith(androidPrefix, ignoreCase = false)) {
                    true -> android.add(permission)
                    else -> others.add(permission)
                }
            }
            val contentList = when (others.isEmpty()) {
                true -> android.sorted()
                else -> android.sorted() + "-".repeat((android + others).maxOf { it.length }) + others.sorted()
            }
            return contentList.joinToString("\n")
        }

    }

}
