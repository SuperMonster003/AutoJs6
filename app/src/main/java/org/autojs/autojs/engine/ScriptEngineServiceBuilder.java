package org.autojs.autojs.engine;

import org.autojs.autojs.runtime.api.Console;
import org.autojs.autojs.tool.UiHandler;

/**
 * Created by Stardust on Apr 2, 2017.
 */
public class ScriptEngineServiceBuilder {

    ScriptEngineManager scriptEngineManager;
    Console globalConsole;
    UiHandler uiHandler;

    public ScriptEngineServiceBuilder() {
        /* Empty body. */
    }

    public ScriptEngineServiceBuilder uiHandler(UiHandler uiHandler) {
        this.uiHandler = uiHandler;
        return this;
    }

    public ScriptEngineServiceBuilder engineManger(ScriptEngineManager manager) {
        scriptEngineManager = manager;
        return this;
    }

    public ScriptEngineServiceBuilder globalConsole(Console console) {
        globalConsole = console;
        return this;
    }

    public ScriptEngineService build() {
        return new ScriptEngineService(this);
    }

}
