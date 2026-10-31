package org.autojs.autojs.core.plugin.gallery

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The launch of a session Activity (api-coverage Q197): the token travels in the intent the Activity is created with, so a
 * launch must never reach the Activity of an earlier session, which reads no new intent.
 * zh-CN: 会话 Activity 的启动 (api-coverage Q197): token 随创建 Activity 的 intent 传递, 因此启动绝不能落到先前会话的 Activity 上, 它不读取新的 intent.
 */
class GalleryActivityLauncherTest {

    @Test
    fun aLaunchFromTheApplicationAlwaysCreatesATaskOfItsOwn() {
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK,
            GalleryActivityLauncher.launchFlags(fromActivity = false),
        )
    }

    @Test
    fun noLaunchAsksToReuseTheActivityOnTop() {
        listOf(true, false).forEach { fromActivity ->
            assertEquals(0, GalleryActivityLauncher.launchFlags(fromActivity) and Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
    }
}
