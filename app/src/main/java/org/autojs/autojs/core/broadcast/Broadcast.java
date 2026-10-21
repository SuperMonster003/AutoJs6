package org.autojs.autojs.core.broadcast;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by Stardust on Apr 1, 2018.
 */
public class Broadcast {

    private static final CopyOnWriteArrayList<BroadcastEmitter> sEventEmitters = new CopyOnWriteArrayList<>();

    public static void registerListener(BroadcastEmitter eventEmitter) {
        sEventEmitters.add(eventEmitter);
    }

    public static boolean unregisterListener(BroadcastEmitter eventEmitter) {
        return sEventEmitters.remove(eventEmitter);
    }

    /**
     * Registered emitters (one per running script, removed by events.recycle), for the memory gate (docs/memory/ROADMAP.md P4.2).
     * zh-CN: 已登记的发射器数 (每个运行中的脚本一个, 由 events.recycle 移除), 供内存门禁使用 (docs/memory/ROADMAP.md P4.2).
     */
    public static int listenerCount() {
        return sEventEmitters.size();
    }

    public static void send(String eventName, Object[] args) {
        for (BroadcastEmitter emitter : sEventEmitters) {
            emitter.onBroadcast(eventName, args);
        }
    }

}
