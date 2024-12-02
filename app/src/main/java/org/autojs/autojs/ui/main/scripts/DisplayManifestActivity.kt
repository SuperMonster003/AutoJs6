package org.autojs.autojs.ui.main.scripts

import android.content.Context
import android.content.Intent
import io.noties.prism4j.GrammarLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs6.R
import java.io.File
import java.util.regex.Pattern

class DisplayManifestActivity : BaseDisplayContentActivity() {

    override var internalMenuResource = R.menu.menu_display_manifest_fab

    override var highlightGrammarLocator: GrammarLocator = MarkupGrammarLocator()
    override var highlightGrammarName = MarkupGrammarLocator.GRAMMAR_NAME
    override var highlightThemeLanguage = "markup"

    private lateinit var mPermissionsContent: CharSequence
    private lateinit var mOriginalText: CharSequence

    init {
        popMenuActionMap += listOf(
            R.id.action_permissions to { showPermissionList() },
            R.id.action_back to { restoreOriginalContent() },
        )
    }

    override suspend fun loadAndDisplayContent() {
        val manifestPath = intent.getStringExtra(PATH_IDENTIFIER_MANIFEST)
        val permissions = intent.getStringArrayExtra(INTENT_IDENTIFIER_PERMISSIONS)

        val manifestContentRaw = withContext(Dispatchers.IO) {
            manifestPath?.let { File(it).readText() }
        }
        val manifestContent = manifestContentRaw ?: getString(R.string.text_no_content)
        mPermissionsContent = permissions?.let {
            withContext(Dispatchers.Default) {
                highlightTextOrNull(handlePermissionsText(it))
            }
        } ?: manifestContentRaw?.let {
            withContext(Dispatchers.Default) {
                highlightTextOrNull(handlePermissionsText(parsePermissionsFromManifest(it)))
            }
        } ?: getString(R.string.text_no_content)

        setTextWithLock(withContext(Dispatchers.Default) {
            highlightTextOrSelf(manifestContent).also { mOriginalText = it }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        deleteCacheFiles()
    }

    private fun showPermissionList() {
        internalMenuResource = R.menu.menu_display_permissions_fab
        setText(mPermissionsContent)
    }

    private fun restoreOriginalContent() {
        internalMenuResource = R.menu.menu_display_manifest_fab
        setText(mOriginalText)
    }

    private fun deleteCacheFiles() {
        File(cacheDir, FILE_NAME_TMP_MANIFEST).takeIf { it.exists() }?.delete()
    }

    companion object {

        private const val PATH_IDENTIFIER_MANIFEST = "MANIFEST_PATH"
        private const val FILE_NAME_TMP_MANIFEST = "tmp-manifest-for-display-manifest-activity.xml"
        private const val INTENT_IDENTIFIER_PERMISSIONS = "permissions"

        @JvmStatic
        fun launch(context: Context, manifest: String, usesPermissions: List<String>) {
            val manifestFile = File(context.cacheDir, FILE_NAME_TMP_MANIFEST).apply {
                writeText(manifest)
            }
            Intent(context, DisplayManifestActivity::class.java).apply {
                putExtra(PATH_IDENTIFIER_MANIFEST, manifestFile.path)
                putExtra(INTENT_IDENTIFIER_PERMISSIONS, usesPermissions.toTypedArray())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }.let { context.startActivity(it) }
        }

        private fun parsePermissionsFromManifest(manifestContent: CharSequence): Array<String> {
            val permissionList = mutableListOf<String>()
            val pattern = Pattern.compile("""<uses-permission[\s\S]*?android:name="([^"]+)"[\s\S]*?/>""")
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