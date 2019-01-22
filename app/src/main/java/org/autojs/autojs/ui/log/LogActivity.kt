package org.autojs.autojs.ui.log

import android.os.Bundle
import androidx.annotation.Nullable
import android.view.View

import com.stardust.autojs.core.console.ConsoleView
import com.stardust.autojs.core.console.ConsoleImpl

import butterknife.BindView
import org.autojs.autojs.R
import org.autojs.autojs.autojs.AutoJs
import org.autojs.autojs.ui.BaseActivity

@EActivity(R.layout.activity_log)
class LogActivity : BaseActivity() {

    @BindView(R.id.console)
    internal var mConsoleView: ConsoleView? = null

    private var mConsoleImpl: ConsoleImpl? = null

    override fun onCreate(@Nullable savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDayNightMode()
    }

    @AfterViews
    internal fun setupViews() {
        setToolbarAsBack(getString(R.string.text_log))
        mConsoleImpl = AutoJs.getInstance().globalConsole
        mConsoleView!!.setConsole(mConsoleImpl)
        mConsoleView!!.findViewById<View>(R.id.input_container).visibility = View.GONE
    }

    @Click(R.id.fab)
    internal fun clearConsole() {
        mConsoleImpl!!.clear()
    }
}
