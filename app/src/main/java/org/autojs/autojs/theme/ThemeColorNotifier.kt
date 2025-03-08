package org.autojs.autojs.theme

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

object ThemeChangeNotifier {

    private val _themeChanged = MutableLiveData<Unit>()

    val themeChanged: LiveData<Unit> get() = _themeChanged

    fun notifyThemeChanged() {
        _themeChanged.postValue(Unit)
    }

}
