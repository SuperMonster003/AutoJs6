package org.autojs.autojs.ui.edit.debug;

import org.autojs.autojs.AutoJs;
import org.autojs.autojs.rhino.debug.Debugger;
import org.mozilla.javascript.ContextFactory;

public class DebuggerSingleton {

    private static final Debugger sDebugger = new Debugger(AutoJs.getInstance().getScriptEngineService(), ContextFactory.getGlobal());

    public static Debugger get(){
        return sDebugger;
    }

}
