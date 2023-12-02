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

    public static void send(String eventName, Object[] args) {
        for (BroadcastEmitter emitter : sEventEmitters) {
            emitter.onBroadcast(eventName, args);
        }
    }

}
