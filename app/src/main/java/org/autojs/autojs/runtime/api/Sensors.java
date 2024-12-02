package org.autojs.autojs.runtime.api;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;

import androidx.annotation.NonNull;

import org.autojs.autojs.core.eventloop.EventEmitter;
import org.autojs.autojs.core.looper.Loopers;
import org.autojs.autojs.core.pref.Language;
import org.autojs.autojs.runtime.ScriptBridges;
import org.autojs.autojs.runtime.ScriptRuntime;
import org.autojs.autojs.tool.MapBuilder;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by Stardust on Feb 5, 2018.
 * Modified by SuperMonster003 as of Dec 5, 2021.
 */
public class Sensors extends EventEmitter implements Loopers.LooperQuitHandler {

    public boolean ignoresUnsupportedSensor = false;

    @SuppressWarnings("InstantiationOfUtilityClass")
    public final Delay delay = new Delay();

    private static final Map<String, Integer> SENSORS = new MapBuilder<String, Integer>()
            .put("ACCELEROMETER", Sensor.TYPE_ACCELEROMETER)
            .put("AMBIENT_TEMPERATURE", Sensor.TYPE_AMBIENT_TEMPERATURE)
            .put("GRAVITY", Sensor.TYPE_GRAVITY)
            .put("GYROSCOPE", Sensor.TYPE_GYROSCOPE)
            .put("LIGHT", Sensor.TYPE_LIGHT)
            .put("LINEAR_ACCELERATION", Sensor.TYPE_LINEAR_ACCELERATION)
            .put("MAGNETIC_FIELD", Sensor.TYPE_MAGNETIC_FIELD)
            .put("ORIENTATION", Sensor.TYPE_ORIENTATION)
            .put("PRESSURE", Sensor.TYPE_PRESSURE)
            .put("PROXIMITY", Sensor.TYPE_PROXIMITY)
            .put("RELATIVE_HUMIDITY", Sensor.TYPE_RELATIVE_HUMIDITY)
            .put("TEMPERATURE", Sensor.TYPE_AMBIENT_TEMPERATURE)
            .build();

    private final Set<SensorEventEmitter> mSensorEventEmitters = new HashSet<>();
    private final SensorManager mSensorManager;
    private final ScriptBridges mScriptBridges;
    private final SensorEventEmitter mNoOpSensorEventEmitter;
    private final ScriptRuntime mScriptRuntime;

    public Sensors(Context context, ScriptRuntime scriptRuntime) {
        super(scriptRuntime.bridges);
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mScriptBridges = scriptRuntime.bridges;
        mNoOpSensorEventEmitter = new SensorEventEmitter(this, scriptRuntime.bridges);
        mScriptRuntime = scriptRuntime;
        scriptRuntime.loopers.addLooperQuitHandler(this);
    }

    public static class Delay {
        public static final int normal = SensorManager.SENSOR_DELAY_NORMAL;
        public static final int ui = SensorManager.SENSOR_DELAY_UI;
        public static final int game = SensorManager.SENSOR_DELAY_GAME;
        public static final int fastest = SensorManager.SENSOR_DELAY_FASTEST;
    }

    public SensorEventEmitter register(String sensorName) {
        return register(sensorName, Delay.normal);
    }

    public SensorEventEmitter register(String sensorName, int delay) {
        if (sensorName == null) {
            throw new NullPointerException(Sensors.class.getSimpleName() + ".register");
        }
        Sensor sensor = getSensor(sensorName);
        if (sensor == null) {
            if (ignoresUnsupportedSensor) {
                emit("unsupported_sensor", sensorName);
                return mNoOpSensorEventEmitter;
            } else {
                return null;
            }
        }
        return register(sensor, delay);
    }

    private SensorEventEmitter register(@NonNull Sensor sensor, int delay) {
        SensorEventEmitter emitter = new SensorEventEmitter(this, mScriptBridges);
        mSensorManager.registerListener(emitter, sensor, delay);
        synchronized (mSensorEventEmitters) {
            mSensorEventEmitters.add(emitter);
            return emitter;
        }
    }

    public void unregister(SensorEventEmitter emitter) {
        if (emitter != null) {
            synchronized (mSensorEventEmitters) {
                mSensorEventEmitters.remove(emitter);
            }
            mSensorManager.unregisterListener(emitter);
        }
    }

    public void unregisterAll() {
        synchronized (mSensorEventEmitters) {
            for (SensorEventEmitter sensorEventEmitter : mSensorEventEmitters) {
                mSensorManager.unregisterListener(sensorEventEmitter);
            }
            mSensorEventEmitters.clear();
            mScriptRuntime.loopers.removeLooperQuitHandler(this);
        }
    }

    public Sensor getSensor(String sensorName) {
        sensorName = sensorName.toUpperCase();
        Integer type = SENSORS.get(sensorName);
        type = type == null ? getSensorTypeByReflect(sensorName) : type;
        return type == null ? null : mSensorManager.getDefaultSensor(type);
    }

    private Integer getSensorTypeByReflect(String sensorName) {
        try {
            Field field = Sensor.class.getField("TYPE_" + sensorName.toUpperCase(Language.getPrefLanguage().getLocale()));
            return (Integer) field.get(null);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean shouldQuit() {
        return mSensorEventEmitters.isEmpty();
    }

}
