package org.autojs.autojs.theme.preference

import android.content.Context
import android.util.AttributeSet

abstract class ThemeColorPermissionSwitchPreference : ThemeColorSwitchPreference {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    fun sync() {
        isChecked = has()
    }

    abstract fun has(): Boolean

    abstract fun request(): Boolean

    abstract fun revoke(): Boolean

    override fun onClick() = toggle()

    private fun toggle() {
        if (isChecked) revoke() else request()
    }

}