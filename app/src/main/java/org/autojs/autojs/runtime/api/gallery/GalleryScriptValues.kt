package org.autojs.autojs.runtime.api.gallery

import org.autojs.autojs.core.plugin.gallery.GalleryError
import org.autojs.autojs.core.plugin.gallery.GalleryPickedImage
import org.autojs.autojs.core.plugin.gallery.GalleryWrittenImage
import org.autojs.plugin.gallery.api.GalleryContract
import org.autojs.plugin.gallery.api.GalleryErrorCodes
import java.io.File

/**
 * The plain JVM values the `gallery` script API hands to Rhino (3-Maple Image roadmap M6, 10.1): a
 * picked image becomes `{ path, name, mimeType, width, height, size, dateTaken }`, a written output
 * `{ path, name, mimeType, width, height, size }`. Integral numbers that fit a script integer stay
 * `Int`, larger ones (sizes beyond 2 GiB, millisecond times) become `Double`, so `typeof` and `===`
 * behave like for any script number. Nothing here touches Rhino, so the JVM tests cover it.
 * zh-CN: `gallery` 脚本 API 交给 Rhino 的普通 JVM 值 (3-Maple Image 路线图 M6, 10.1): 选中的图片为 `{ path, name, mimeType, width, height, size,
 * dateTaken }`, 写出的输出为 `{ path, name, mimeType, width, height, size }`. 能放进脚本整数的整值保持 `Int`, 更大的 (超过 2 GiB 的大小, 毫秒时间)
 * 为 `Double`, 因此 `typeof` 与 `===` 的行为与普通脚本数字相同. 这里不触碰 Rhino, 因此由 JVM 测试覆盖.
 */
internal object GalleryScriptValues {

    fun picked(items: List<GalleryPickedImage>): List<Map<String, Any?>> = items.map { item ->
        LinkedHashMap<String, Any?>().apply {
            put(GalleryContract.FIELD_PATH, item.path)
            put(GalleryContract.FIELD_NAME, item.name)
            put(GalleryContract.FIELD_MIME_TYPE, item.mimeType)
            put(GalleryContract.FIELD_WIDTH, item.width)
            put(GalleryContract.FIELD_HEIGHT, item.height)
            put(GalleryContract.FIELD_SIZE, number(item.size))
            put(GalleryContract.FIELD_DATE_TAKEN, number(item.dateTaken))
        }
    }

    fun written(image: GalleryWrittenImage): Map<String, Any?> = LinkedHashMap<String, Any?>().apply {
        put(GalleryContract.FIELD_PATH, image.path)
        put(GalleryContract.FIELD_NAME, File(image.path).name)
        put(GalleryContract.FIELD_MIME_TYPE, image.mimeType)
        put(GalleryContract.FIELD_WIDTH, image.width)
        put(GalleryContract.FIELD_HEIGHT, image.height)
        put(GalleryContract.FIELD_SIZE, number(image.size))
    }

    fun number(value: Long): Number = if (value in Int.MIN_VALUE..Int.MAX_VALUE) value.toInt() else value.toDouble()

    /**
     * How a picker or editor session that closed without a `result` answers the script: null (the
     * user cancelled, or the host ended it) or the failure. A reported `error` event wins over the
     * reason.
     * zh-CN: 未收到 `result` 就关闭的选择器或编辑器会话如何应答脚本: null (用户取消, 或宿主结束了会话) 或失败. 已报告的 `error` 事件优先于原因.
     */
    fun closedWithoutResult(reason: String?, reported: GalleryError?): GalleryError? = reported ?: when (reason) {
        GalleryContract.REASON_USER, GalleryContract.REASON_DONE, GalleryContract.REASON_HOST -> null
        GalleryContract.REASON_TIMEOUT -> GalleryError(GalleryErrorCodes.TIMEOUT, "the gallery plugin did not show the session in time")
        GalleryContract.REASON_REPLACED -> GalleryError(GalleryErrorCodes.SESSION_REPLACED, "another gallery session replaced this one")
        else -> GalleryError(GalleryErrorCodes.SESSION_CLOSED, "the gallery plugin ended the session (${reason ?: "no reason"})")
    }
}
