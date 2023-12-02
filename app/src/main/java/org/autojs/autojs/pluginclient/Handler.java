package org.autojs.autojs.pluginclient;

import com.google.gson.JsonObject;

/**
 * Created by Stardust on May 11, 2017.
 */
public interface Handler {

    boolean handle(JsonObject data);
}
