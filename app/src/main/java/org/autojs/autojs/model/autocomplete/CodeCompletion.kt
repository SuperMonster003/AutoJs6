package org.autojs.autojs.model.autocomplete

import androidx.annotation.DrawableRes

/**
 * Created by Stardust on Feb 3, 2018.
 * Transformed by 抠脚本人 (https://github.com/little-alei) on Jul 11, 2023.
 */
class CodeCompletion {
    val hint: String
    val url: String?
    @DrawableRes
    val iconResId: Int
    private val mInsertText: String?
    private val mInsertPos: Int

    constructor(hint: String, url: String?, insertPos: Int) : this(hint, url, insertPos, 0)

    constructor(hint: String, url: String?, insertPos: Int, @DrawableRes iconResId: Int) {
        this.hint = hint
        this.url = url
        this.iconResId = iconResId
        mInsertPos = insertPos
        mInsertText = null
    }

    constructor(hint: String, url: String?, insertText: String?) : this(hint, url, insertText, 0)

    constructor(hint: String, url: String?, insertText: String?, @DrawableRes iconResId: Int) {
        this.hint = hint
        this.url = url
        this.iconResId = iconResId
        mInsertText = insertText
        mInsertPos = -1
    }

    val insertText: String
        get() {
            if (mInsertText != null) return mInsertText
            return if (mInsertPos == 0) {
                hint
            } else hint.substring(mInsertPos)
        }
}
