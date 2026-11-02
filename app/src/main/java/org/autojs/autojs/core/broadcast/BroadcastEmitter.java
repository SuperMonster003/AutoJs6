package org.autojs.autojs.core.broadcast;

import org.autojs.autojs.core.eventloop.EventEmitter;
import org.autojs.autojs.core.looper.Timer;
import org.autojs.autojs.runtime.ScriptBridges;

/**
 * Created by Stardust on Apr 1, 2018.
 */
public class BroadcastEmitter extends EventEmitter {

    /**
     * An object or array broadcast by a Node.js or Bun script, as JSON text: each Rhino script that listens parses a native copy in its own scope.
     * zh-CN: Node.js 或 Bun 脚本广播的对象或数组, 以 JSON 文本形式传递: 每个监听的 Rhino 脚本在自己的作用域里解析出一份原生副本.
     */
    public static final class Json {
        public final String text;

        public Json(String text) {
            this.text = text;
        }
    }

    public BroadcastEmitter(ScriptBridges bridges, Timer timer) {
        super(bridges, timer);
        Broadcast.registerListener(this);
    }

    public boolean onBroadcast(String eventName, Object... args) {
        if (listenerCount(eventName) == 0) {
            return false;
        }
        Object[] values = args.clone();
        for (int i = 0; i < values.length; i++) {
            if (values[i] instanceof Json) {
                values[i] = mBridges.parseJson(((Json) values[i]).text);
            }
        }
        return super.emit(eventName, values);
    }

    public void unregister() {
        Broadcast.unregisterListener(this);
    }

    @Override
    public boolean emit(String eventName, Object... args) {
        Broadcast.send(eventName, args);
        return true;
    }
}
