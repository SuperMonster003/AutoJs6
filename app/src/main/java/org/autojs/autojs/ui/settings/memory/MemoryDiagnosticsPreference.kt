package org.autojs.autojs.ui.settings.memory

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import org.autojs.autojs.theme.preference.MaterialPreference

/**
 * Developer options > Memory diagnostics > Open memory diagnostics (docs/memory/ROADMAP.md T10).
 * zh-CN: 开发者选项 > 内存诊断 > 打开内存诊断 (docs/memory/ROADMAP.md T10).
 */
class MemoryDiagnosticsPreference : MaterialPreference {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    override fun onClick() {
        context.startActivity(Intent(context, MemoryDiagnosticsActivity::class.java))
        super.onClick()
    }

}
