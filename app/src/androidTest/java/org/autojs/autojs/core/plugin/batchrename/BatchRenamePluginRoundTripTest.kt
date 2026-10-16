package org.autojs.autojs.core.plugin.batchrename

import android.content.Context
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.plugin.batchrename.api.BatchRenameContract
import org.autojs.plugin.batchrename.api.BatchRenameErrorCodes
import org.autojs.plugin.batchrename.api.BatchRenameItemKeys
import org.autojs.plugin.batchrename.api.BatchRenameOptionKeys
import org.autojs.plugin.batchrename.api.BatchRenameParentKeys
import org.autojs.plugin.batchrename.api.BatchRenameRequestKeys
import org.autojs.plugin.batchrename.api.BatchRenameResultKeys
import org.autojs.plugin.batchrename.api.BatchRenameValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Real cross-process round trip with the installed Batch Rename plugin (plugin roadmap P2.5 / P3): requests built
 * exactly as the host sends them, answers read with the contract keys, and the error codes of refused requests. Run it
 * against a signed release build of the plugin to cover R8 as well.
 *
 * Skipped when no Batch Rename plugin is installed. The plugin's services require this host's signature permission.
 *
 * ```
 * adb shell am instrument -w -e class org.autojs.autojs.core.plugin.batchrename.BatchRenamePluginRoundTripTest org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * zh-CN: 与已安装的 Batch Rename 插件进行真实的跨进程往返 (插件路线图 P2.5 / P3): 请求与宿主实际发送的完全一致, 用契约键读取
 * 应答, 并检查被拒请求的错误码. 对插件的签名 release 构建运行即可同时覆盖 R8. 未安装 Batch Rename 插件时跳过.
 */
class BatchRenamePluginRoundTripTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun plansComeBackFromTheInstalledPlugin(): Unit = runBlocking {
        requirePlugin()
        val answer = BatchRenamePluginHost.plan(
            context,
            request(
                options = Bundle().apply {
                    putString(BatchRenameOptionKeys.SEARCH, "IMG_(?<n>\\d+)")
                    putString(BatchRenameOptionKeys.REPLACE, "\$YYYY-\$MM_\${padding=3}_\$<n>")
                    putBoolean(BatchRenameOptionKeys.REGEX, true)
                    putBoolean(BatchRenameOptionKeys.ENUMERATE, true)
                    putString(BatchRenameOptionKeys.FORMAT, BatchRenameValues.FORMAT_UPPER)
                },
                names = listOf("IMG_7.jpg", "IMG_8.jpg", "notes.txt"),
            ),
        )
        assertFalse("error: ${answer.getString(BatchRenameResultKeys.ERROR_MESSAGE)}", answer.containsKey(BatchRenameResultKeys.ERROR_CODE))
        assertEquals(BatchRenameContract.CONTRACT_VERSION, answer.getInt(BatchRenameResultKeys.CONTRACT_VERSION))
        assertFalse(answer.getBoolean(BatchRenameResultKeys.REJECTED, true))
        // 2026-10-15 08:30 UTC is the modification time of every item.
        assertEquals(listOf("2026-10_000_7.JPG", "2026-10_001_8.JPG", "notes.txt"), items(answer).map { it.getString(BatchRenameItemKeys.NEW_NAME) })
        assertEquals(listOf("rename", "rename", "unchanged"), items(answer).map { it.getString(BatchRenameItemKeys.STATUS) })
        val counts = requireNotNull(answer.getBundle(BatchRenameResultKeys.COUNTS))
        assertEquals(BatchRenameValues.STATUSES.toSet(), counts.keySet())
        assertEquals(2, counts.getInt(BatchRenameValues.STATUS_RENAME))
    }

    @Test
    fun conflictPoliciesAndLookbehindCrossTheBinder(): Unit = runBlocking {
        requirePlugin()
        fun options(policy: String) = Bundle().apply {
            putString(BatchRenameOptionKeys.SEARCH, "(?<=_)[a-z]+")
            putString(BatchRenameOptionKeys.REPLACE, "same")
            putBoolean(BatchRenameOptionKeys.REGEX, true)
            putString(BatchRenameOptionKeys.ON_CONFLICT, policy)
        }
        val names = listOf("p_one.txt", "p_two.txt")
        val failed = BatchRenamePluginHost.plan(context, request(options(BatchRenameValues.CONFLICT_FAIL), names))
        assertTrue(failed.getBoolean(BatchRenameResultKeys.REJECTED))
        assertEquals(listOf("duplicateInBatch", "duplicateInBatch"), items(failed).map { it.getString(BatchRenameItemKeys.REASON) })
        val numbered = BatchRenamePluginHost.plan(context, request(options(BatchRenameValues.CONFLICT_NUMBER), names))
        assertFalse(numbered.getBoolean(BatchRenameResultKeys.REJECTED, true))
        assertEquals(listOf("p_same.txt", "p_same (1).txt"), items(numbered).map { it.getString(BatchRenameItemKeys.NEW_NAME) })
    }

    /** The dictionary of plugin 1.1.0 loads in the plugin process, also after R8 (roadmap P6.1). zh-CN: 插件 1.1.0 的词典在插件进程中加载. */
    @Test
    fun pinyinComesFromTheBuiltInDictionary(): Unit = runBlocking {
        requirePlugin()
        val features = BatchRenamePluginHost.discover(context).flatMap { BatchRenamePluginHost.featuresOf(it.pluginInfo) }
        assumeTrue("The installed plugin predates pinyin", BatchRenameValues.FEATURE_PINYIN in features)
        val names = listOf("重慶銀行.jpg", "我的照片.jpg", "notes.txt")
        val cleanup = BatchRenamePluginHost.plan(context, request(Bundle().apply {
            putString(BatchRenameOptionKeys.SEARCH, "")
            putBoolean(BatchRenameOptionKeys.PINYIN, true)
            putString(BatchRenameOptionKeys.PINYIN_SEPARATOR, BatchRenameValues.PINYIN_SEPARATOR_UNDERSCORE)
        }, names))
        assertFalse("error: ${cleanup.getString(BatchRenameResultKeys.ERROR_MESSAGE)}", cleanup.containsKey(BatchRenameResultKeys.ERROR_CODE))
        assertEquals(listOf("chong_qing_yin_hang.jpg", "wo_de_zhao_pian.jpg", "notes.txt"), items(cleanup).map { it.getString(BatchRenameItemKeys.NEW_NAME) })
        val variable = BatchRenamePluginHost.plan(context, request(Bundle().apply {
            putString(BatchRenameOptionKeys.SEARCH, "^")
            putString(BatchRenameOptionKeys.REPLACE, "\${pinyin}-")
            putBoolean(BatchRenameOptionKeys.REGEX, true)
            putString(BatchRenameOptionKeys.TARGET, BatchRenameValues.TARGET_NAME)
            putString(BatchRenameOptionKeys.PINYIN_TONE, BatchRenameValues.PINYIN_TONE_NUMBER)
            putString(BatchRenameOptionKeys.PINYIN_CASE, BatchRenameValues.PINYIN_CASE_CAPITALIZE)
        }, names.take(1)))
        assertEquals(listOf("Chong2Qing4Yin2Hang2-重慶銀行.jpg"), items(variable).map { it.getString(BatchRenameItemKeys.NEW_NAME) })
    }

    @Test
    fun refusedRequestsComeBackAsContractErrors(): Unit = runBlocking {
        requirePlugin()
        val invalidPattern = BatchRenamePluginHost.plan(
            context,
            request(Bundle().apply {
                putString(BatchRenameOptionKeys.SEARCH, "(")
                putBoolean(BatchRenameOptionKeys.REGEX, true)
            }, listOf("a")),
        )
        assertEquals(BatchRenameErrorCodes.INVALID_OPTIONS, invalidPattern.getString(BatchRenameResultKeys.ERROR_CODE))
        assertFalse(invalidPattern.containsKey(BatchRenameResultKeys.ITEMS))
        val tooMany = BatchRenamePluginHost.plan(context, request(Bundle().apply { putString(BatchRenameOptionKeys.SEARCH, "f") }, List(2001) { "f$it" }))
        assertEquals(BatchRenameErrorCodes.LIMIT_EXCEEDED, tooMany.getString(BatchRenameResultKeys.ERROR_CODE))
        val unsupported = BatchRenamePluginHost.plan(context, Bundle().apply { putInt(BatchRenameRequestKeys.CONTRACT_VERSION, 99) })
        assertEquals(BatchRenameErrorCodes.UNSUPPORTED_CONTRACT_VERSION, unsupported.getString(BatchRenameResultKeys.ERROR_CODE))
    }

    private fun requirePlugin() {
        assumeTrue("No Batch Rename plugin is installed", BatchRenamePluginHost.queryServiceCount(context) > 0)
    }

    private fun request(options: Bundle, names: List<String>): Bundle = Bundle().apply {
        putInt(BatchRenameRequestKeys.CONTRACT_VERSION, BatchRenameContract.CONTRACT_VERSION)
        putBundle(BatchRenameRequestKeys.OPTIONS, options)
        putParcelableArrayList(BatchRenameRequestKeys.PARENTS, arrayListOf(Bundle().apply {
            putString(BatchRenameParentKeys.PARENT_ID, "parent")
            putStringArrayList(BatchRenameParentKeys.EXISTING_NAMES, ArrayList(names))
            putBoolean(BatchRenameParentKeys.CASE_INSENSITIVE, false)
            putBoolean(BatchRenameParentKeys.RESTRICTED_CHARACTERS, false)
        }))
        putParcelableArrayList(BatchRenameRequestKeys.ITEMS, ArrayList(names.mapIndexed { index, name ->
            Bundle().apply {
                putInt(BatchRenameItemKeys.INDEX, index)
                putString(BatchRenameItemKeys.PARENT_ID, "parent")
                putString(BatchRenameItemKeys.NAME, name)
                putInt(BatchRenameItemKeys.KIND, BatchRenameValues.KIND_FILE)
                putInt(BatchRenameItemKeys.DEPTH, 0)
                putLong(BatchRenameItemKeys.LAST_MODIFIED, MODIFIED)
                putLong(BatchRenameItemKeys.LENGTH, 1L)
                putBoolean(BatchRenameItemKeys.EXCLUDED, false)
            }
        }))
        putString(BatchRenameRequestKeys.LOCALE, "en")
        putString(BatchRenameRequestKeys.TIME_ZONE, "UTC")
        putLong(BatchRenameRequestKeys.NOW, MODIFIED)
    }

    @Suppress("DEPRECATION")
    private fun items(answer: Bundle): List<Bundle> = requireNotNull(answer.getParcelableArrayList<Bundle>(BatchRenameResultKeys.ITEMS))

    private companion object {
        /** 2026-10-15 08:30:00 UTC. */
        const val MODIFIED = 1_792_053_000_000L
    }
}
