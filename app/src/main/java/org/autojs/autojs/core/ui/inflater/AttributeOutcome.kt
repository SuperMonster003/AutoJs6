package org.autojs.autojs.core.ui.inflater

/**
 * How applying one attribute ended (host roadmap `docs/dev/android-xml-attributes-roadmap.md` A2 / A4).
 *
 * zh-CN: 应用一个属性的结果 (宿主路线图 A2 / A4).
 */
enum class AttributeOutcome {
    /** A handler of the inflater or the metadata fallback set it. */
    APPLIED,

    /** Its namespace is not `android`, `app` or none (`tools:`, `xmlns:`), so it is not meant for the view. */
    IGNORED_NAMESPACE,

    /** The attribute exists for this view, but neither a handler nor a public API of this device can set it. */
    UNSUPPORTED,

    /** Neither the handlers nor the attribute metadata know it for this view (a typo, or another library's attribute). */
    UNKNOWN,
}
