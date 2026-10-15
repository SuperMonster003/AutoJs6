package org.autojs.autojs.core.ui.preview

/**
 * The script a layout-preview runtime executes (3-Block Compose roadmap P10.2, D47 level 1).
 *
 * It is a constant: the XML and the binding values of a request are applied from Kotlin on the running
 * runtime (`ui.layout` is invoked directly, the binding context is parsed with the JSON parser), so no
 * request content is ever spliced into source text and no user code runs in a layout preview.
 *
 * zh-CN: 布局预览运行时执行的脚本 (3-Block Compose 路线图 P10.2, D47 第一级). 它是常量: 请求中的 XML 与绑定值由 Kotlin 直接作用于
 * 运行中的运行时 (直接调用 `ui.layout`, 绑定上下文经 JSON 解析器得到), 请求内容从不拼接进源码, 布局预览也不运行用户代码.
 */
object UiPreviewScript {

    /** The whole source: the `"ui"` directive selects the ui execution mode and nothing else happens. */
    const val SOURCE = "\"ui\";"

    /** Prefix of the engine source name when the client sends no label. */
    const val SOURCE_NAME_PREFIX = "ui-preview"
}
