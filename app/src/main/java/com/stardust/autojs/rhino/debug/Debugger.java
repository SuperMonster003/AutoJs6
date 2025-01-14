package com.stardust.autojs.rhino.debug;

import org.autojs.autojs.engine.ScriptEngineService;
import org.mozilla.javascript.ContextFactory;

public class Debugger extends org.autojs.autojs.rhino.debug.Debugger {
    public Debugger(ScriptEngineService scriptEngineService, ContextFactory contextFactory) {
        super(scriptEngineService, contextFactory);
    }
}
