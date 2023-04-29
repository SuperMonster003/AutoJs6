package org.autojs.autojs.ui.log;

import android.view.View;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.ViewById;
import org.autojs.autojs.AutoJs;
import org.autojs.autojs.core.console.ConsoleView;
import org.autojs.autojs.core.console.GlobalConsole;
import org.autojs.autojs.ui.BaseActivity;
import org.autojs.autojs6.R;

@EActivity(R.layout.activity_log)
public class LogActivity extends BaseActivity {

    @ViewById(R.id.console)
    ConsoleView mConsoleView;

    private GlobalConsole mConsoleImpl;

    @AfterViews
    void setupViews() {
        setToolbarAsBack(R.string.text_log);
        mConsoleImpl = AutoJs.getInstance().getGlobalConsole();
        mConsoleView.setConsole(mConsoleImpl);
        mConsoleView.findViewById(R.id.input_container).setVisibility(View.GONE);
    }

    @Click(R.id.fab)
    public void clearConsole() {
        mConsoleImpl.clear();
    }

}
