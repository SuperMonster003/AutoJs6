package org.autojs.autojs.theme.preference

import android.content.Context
import android.util.AttributeSet

abstract class ThemeColorServiceSwitchPreference : ThemeColorSwitchPreference, Syncable {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    override fun sync() {
        isChecked = isRunning()
    }

    abstract fun isRunning(): Boolean

    abstract fun start(): Boolean

    abstract fun stop(): Boolean

    override fun onClick() {
        toggle()
        super.onClick()
    }

    private fun toggle() {
        if (isChecked) stop() else start()
    }

}