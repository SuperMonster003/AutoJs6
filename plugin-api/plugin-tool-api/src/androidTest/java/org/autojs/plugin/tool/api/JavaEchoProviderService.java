package org.autojs.plugin.tool.api;

import android.os.Bundle;

import org.autojs.plugin.common.api.PluginInfo;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A provider written in Java on top of PluginToolProviderService, so that the base class, PluginToolNames and the result
 * builder stay usable without Kotlin (MCP roadmap D59 / D62).
 */
public class JavaEchoProviderService extends PluginToolProviderService {

    @Override
    protected PluginInfo createPluginInfo() {
        PluginInfo info = new PluginInfo();
        info.setName("Java echo");
        info.setAuthor("SuperMonster003");
        info.setVersionName("1.0.0");
        info.setVersionCode(1L);
        info.setCapabilities(new Bundle());
        return info;
    }

    @Override
    protected String createCatalogJson() {
        try {
            JSONObject schema = new JSONObject().put("type", "object").put("additionalProperties", false);
            JSONObject echo = new JSONObject().put("name", "echo").put("description", "Returns its text.")
                    .put("risk", PluginToolValues.RISK_READONLY).put("inputSchema", schema);
            if (!PluginToolNames.isValidShortName("javaecho")) throw new IllegalStateException("bad short name");
            return new JSONObject().put("shortName", "javaecho").put("tools", new JSONArray().put(echo)).toString();
        } catch (org.json.JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    protected PluginToolResult onCall(PluginToolCall call) throws Exception {
        call.throwIfCancelled();
        String text = call.getArguments().getString("text");
        return new PluginToolResult(new JSONObject().put("text", text)).withText(text);
    }

    @Override
    protected int getWorkerThreads() {
        return 1;
    }
}
