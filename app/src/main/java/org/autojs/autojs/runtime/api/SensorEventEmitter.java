package org.autojs.autojs.runtime.api;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

import org.autojs.autojs.core.eventloop.EventEmitter;
import org.autojs.autojs.runtime.ScriptBridges;

/**
 * Created by SuperMonster003 on Dec 19, 2023.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on Dec 19, 2023.
public class SensorEventEmitter extends EventEmitter implements SensorEventListener {

    private final Sensors sensors;

    public SensorEventEmitter(Sensors sensors, ScriptBridges bridges) {
        super(bridges);
        this.sensors = sensors;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Object[] args = new Object[event.values.length + 1];
        args[0] = event;
        for (int i = 1; i < args.length; i++) {
            args[i] = event.values[i - 1];
        }
        emit("change", args);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        emit("accuracy_change", accuracy);
    }

    public void unregister() {
        sensors.unregister(this);
    }
}
