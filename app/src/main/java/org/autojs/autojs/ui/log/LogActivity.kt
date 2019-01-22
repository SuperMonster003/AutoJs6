package org.autojs.autojs.ui.log

import android.os.Bundle
import android.view.View
import androidx.annotation.Nullable
import com.stardust.autojs.core.console.ConsoleImpl
import kotlinx.android.synthetic.main.activity_log.*
import org.autojs.autojs.R
import org.autojs.autojs.autojs.AutoJs
import org.autojs.autojs.ui.BaseActivity

class LogActivity : BaseActivity(R.layout.activity_log) {

    private lateinit var mConsoleImpl: ConsoleImpl

    override fun onCreate(@Nullable savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDayNightMode()
    }

    override fun setUpViews() {
        setToolbarAsBack(getString(R.string.text_log))
        mConsoleImpl = AutoJs.getInstance().globalConsole
        fab.setOnClickListener {
            mConsoleImpl.clear()
        }
        console.setConsole(mConsoleImpl)
        console.findViewById<View>(R.id.input_container).visibility = View.GONE
    }

}
