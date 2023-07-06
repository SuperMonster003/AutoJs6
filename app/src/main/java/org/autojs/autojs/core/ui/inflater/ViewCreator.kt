package org.autojs.autojs.core.ui.inflater

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * Created by Stardust on 2017/11/29.
 * Transformed by SuperMonster003 on Jun 8, 2023.
 */
interface ViewCreator<V : View> {

    fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): V

}