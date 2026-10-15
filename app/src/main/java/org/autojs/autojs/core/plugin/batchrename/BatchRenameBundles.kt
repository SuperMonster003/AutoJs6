package org.autojs.autojs.core.plugin.batchrename

import android.os.Bundle
import androidx.core.os.BundleCompat
import org.autojs.plugin.batchrename.api.BatchRenameContract
import org.autojs.plugin.batchrename.api.BatchRenameItemKeys
import org.autojs.plugin.batchrename.api.BatchRenameParentKeys
import org.autojs.plugin.batchrename.api.BatchRenameRequestKeys
import org.autojs.plugin.batchrename.api.BatchRenameResultKeys
import org.autojs.plugin.batchrename.api.BatchRenameValues

/**
 * Contract v1 Bundles of a script plan (docs/dev/batch-rename-plugin-protocol-v1.md): the request the host sends and
 * the answer it reads back.
 * zh-CN: 脚本方案的契约 v1 Bundle: 宿主发送的请求与读回的应答.
 */
internal object BatchRenameBundles {

    fun request(request: BatchRenamePlanRequest): Bundle = Bundle().apply {
        putInt(BatchRenameRequestKeys.CONTRACT_VERSION, BatchRenameContract.CONTRACT_VERSION)
        putBundle(BatchRenameRequestKeys.OPTIONS, Bundle().apply {
            request.options.forEach { (key, value) ->
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    else -> putString(key, value.toString())
                }
            }
        })
        putParcelableArrayList(BatchRenameRequestKeys.PARENTS, ArrayList(request.parents.map { parent ->
            Bundle().apply {
                putString(BatchRenameParentKeys.PARENT_ID, parent.id)
                putStringArrayList(BatchRenameParentKeys.EXISTING_NAMES, ArrayList(parent.existingNames))
                putBoolean(BatchRenameParentKeys.CASE_INSENSITIVE, parent.traits.caseInsensitive)
                putBoolean(BatchRenameParentKeys.RESTRICTED_CHARACTERS, parent.traits.restrictedCharacters)
            }
        }))
        putParcelableArrayList(BatchRenameRequestKeys.ITEMS, ArrayList(request.items.mapIndexed { index, item ->
            Bundle().apply {
                putInt(BatchRenameItemKeys.INDEX, index)
                putString(BatchRenameItemKeys.PARENT_ID, item.parentId)
                putString(BatchRenameItemKeys.NAME, item.file.name)
                putInt(BatchRenameItemKeys.KIND, if (item.directory) BatchRenameValues.KIND_DIRECTORY else BatchRenameValues.KIND_FILE)
                putInt(BatchRenameItemKeys.DEPTH, item.depth)
                putLong(BatchRenameItemKeys.LAST_MODIFIED, item.lastModified)
                putLong(BatchRenameItemKeys.LENGTH, item.length)
                putBoolean(BatchRenameItemKeys.EXCLUDED, request.excluded[index])
            }
        }))
        putString(BatchRenameRequestKeys.LOCALE, request.locale)
        putString(BatchRenameRequestKeys.TIME_ZONE, request.timeZone)
        putLong(BatchRenameRequestKeys.NOW, request.now)
    }

    /** The answer, or the script error its error code stands for. zh-CN: 应答, 或其错误码对应的脚本错误. */
    fun answer(bundle: Bundle): BatchRenamePlanAnswer {
        bundle.getString(BatchRenameResultKeys.ERROR_CODE)?.let { code ->
            throw BatchRenameScriptException(
                BatchRenameScriptCodes.fromPluginErrorCode(code),
                bundle.getString(BatchRenameResultKeys.ERROR_MESSAGE)?.takeIf { it.isNotBlank() } ?: "the Batch Rename plugin answered $code",
            )
        }
        val items = BundleCompat.getParcelableArrayList(bundle, BatchRenameResultKeys.ITEMS, Bundle::class.java).orEmpty().map { item ->
            BatchRenamePlanAnswerItem(
                newName = item.getString(BatchRenameItemKeys.NEW_NAME),
                status = item.getString(BatchRenameItemKeys.STATUS).orEmpty(),
                reason = item.getString(BatchRenameItemKeys.REASON),
                overwrites = item.getBoolean(BatchRenameItemKeys.OVERWRITES, false),
            )
        }
        val counts = bundle.getBundle(BatchRenameResultKeys.COUNTS)?.let { counts ->
            counts.keySet().associateWith { counts.getInt(it, 0) }
        }.orEmpty()
        return BatchRenamePlanAnswer(bundle.getBoolean(BatchRenameResultKeys.REJECTED, false), items, counts)
    }
}
