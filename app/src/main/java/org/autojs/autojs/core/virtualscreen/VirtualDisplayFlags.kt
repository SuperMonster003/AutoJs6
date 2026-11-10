package org.autojs.autojs.core.virtualscreen

/**
 * Hidden flags of DisplayManager.createVirtualDisplay used by a background automation display.
 *
 * The values are the AOSP bit positions (kept as literals because several of them are @hide).
 * Ported from the mechanism of Mika (wangzan101/Mika, Apache-2.0): a trusted virtual display
 * owns its display group and, on Android 14+, its focus without stealing the main screen focus.
 * This object is deliberately free of any `android.*` reference so that it can be unit tested on
 * a plain JVM.
 *
 * zh-CN: 后台自动化虚拟屏使用的 DisplayManager 隐藏标志位. 数值取自 AOSP 位定义 (部分为 @hide 常量, 故以字面量保存),
 * 机制参考 Mika (wangzan101/Mika, Apache-2.0): 可信虚拟屏自成一个显示组, Android 14+ 起还能拥有独立焦点且不抢主屏焦点.
 * 本对象刻意不引用任何 android.* 类, 以便纯 JVM 单元测试.
 */
object VirtualDisplayFlags {

    /** The first Android version whose shell may be allowed to add a trusted display. */
    const val TRUSTED_SDK = 33 // TIRAMISU

    /** The first Android version on which a virtual display can own its focus. */
    const val OWN_FOCUS_SDK = 34 // UPSIDE_DOWN_CAKE

    const val PUBLIC = 1 shl 0
    const val OWN_CONTENT_ONLY = 1 shl 3
    const val SUPPORTS_TOUCH = 1 shl 6
    const val ROTATES_WITH_CONTENT = 1 shl 7
    const val DESTROY_CONTENT_ON_REMOVAL = 1 shl 8
    const val TRUSTED = 1 shl 10
    const val OWN_DISPLAY_GROUP = 1 shl 11
    const val ALWAYS_UNLOCKED = 1 shl 12
    const val TOUCH_FEEDBACK_DISABLED = 1 shl 13
    const val OWN_FOCUS = 1 shl 14
    const val STEAL_TOP_FOCUS_DISABLED = 1 shl 16

    /** The flags a plain app is allowed to use on every supported Android version. */
    const val BASE = PUBLIC or OWN_CONTENT_ONLY or SUPPORTS_TOUCH or
        ROTATES_WITH_CONTENT or DESTROY_CONTENT_ON_REMOVAL

    /**
     * Flags for a display created as the shell uid.
     *
     * [trusted] must be false when the shell lacks `ADD_TRUSTED_DISPLAY`: the trusted-only bits
     * are then dropped, and the created display shares the main screen's focus. On Android 14+
     * `OWN_FOCUS` alone would make the virtual display the top focus display and clear the main
     * screen's focus window, so it is always paired with `STEAL_TOP_FOCUS_DISABLED`.
     *
     * zh-CN: 以 shell 身份建屏时使用的标志位. 当 shell 缺少 ADD_TRUSTED_DISPLAY 时 [trusted] 传 false, 去掉仅可信屏可用的位,
     * 该屏会与主屏共享焦点. Android 14+ 上单独的 OWN_FOCUS 会让虚拟屏成为最上层焦点屏并清空主屏焦点窗口,
     * 因此必须与 STEAL_TOP_FOCUS_DISABLED 成对出现.
     */
    fun forSdk(sdk: Int, trusted: Boolean): Int {
        var flags = BASE
        if (sdk >= TRUSTED_SDK) {
            flags = flags or TOUCH_FEEDBACK_DISABLED
            if (trusted) {
                flags = flags or TRUSTED or OWN_DISPLAY_GROUP or ALWAYS_UNLOCKED
            }
        }
        if (sdk >= OWN_FOCUS_SDK && trusted) {
            flags = flags or OWN_FOCUS or STEAL_TOP_FOCUS_DISABLED
        }
        return flags
    }

    /** Whether a [SecurityException] from createVirtualDisplay is worth a retry without trusted flags. */
    fun isTrusted(flags: Int) = flags and TRUSTED != 0

    /** Human-readable names of the set bits, for `virtualScreen.state()` and diagnostics. */
    fun describe(flags: Int): List<String> = buildList {
        fun bit(value: Int, name: String) {
            if (flags and value != 0) add(name)
        }
        bit(PUBLIC, "public")
        bit(OWN_CONTENT_ONLY, "ownContentOnly")
        bit(SUPPORTS_TOUCH, "supportsTouch")
        bit(ROTATES_WITH_CONTENT, "rotatesWithContent")
        bit(DESTROY_CONTENT_ON_REMOVAL, "destroyContentOnRemoval")
        bit(TRUSTED, "trusted")
        bit(OWN_DISPLAY_GROUP, "ownDisplayGroup")
        bit(ALWAYS_UNLOCKED, "alwaysUnlocked")
        bit(TOUCH_FEEDBACK_DISABLED, "touchFeedbackDisabled")
        bit(OWN_FOCUS, "ownFocus")
        bit(STEAL_TOP_FOCUS_DISABLED, "stealTopFocusDisabled")
    }

}
