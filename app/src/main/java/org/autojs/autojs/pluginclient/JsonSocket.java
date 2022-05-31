package org.autojs.autojs.pluginclient;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.stardust.app.GlobalAppContext;
import com.stardust.autojs.runtime.api.Device;
import com.stardust.util.MapBuilder;

import org.autojs.autojs6.BuildConfig;
import org.autojs.autojs6.R;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.subjects.PublishSubject;
import okio.ByteString;

abstract public class JsonSocket extends Socket {

    private final String TAG = "JsonSocket";

    public static final int HEADER_SIZE = 8;
    public static final int HANDSHAKE_TIMEOUT = 5 * 1000;

    public static final String TYPE_HELLO = DevPluginService.TYPE_HELLO;
    public static final String TYPE_BYTES_COMMAND = DevPluginService.TYPE_BYTES_COMMAND;

    public static class Bytes {
        public final String md5;
        public final ByteString byteString;
        public final long timestamp;

        public Bytes(String md5, ByteString byteString) {
            this.md5 = md5;
            this.byteString = byteString;
            this.timestamp = System.currentTimeMillis();
        }
    }

    @SuppressWarnings("unused")
    public static class Type {
        public static int TEXT = 1;
        public static int BINARY = 2;
        public static int GZIP_TEXT = 3;
        public static int GZIP_BINARY = 4;
    }

    public final android.os.Handler mHandler = new Handler(Looper.getMainLooper());
    public final DevPluginService devPlugin = DevPluginService.getInstance();

    public abstract void switchOff() throws IOException;

    public abstract boolean isSocketReady();

    public abstract Socket getSocket();

    public abstract JsonSocket setSocket(Socket socket);

    public abstract JsonSocket monitorMessage();

    public abstract JsonSocket subscribeMessage();

    public abstract JsonSocket setStateConnected();

    public abstract PublishSubject<JsonElement> getJsonElementPublishSubject();

    public abstract PublishSubject<Bytes> getBytesPublishSubject();

    public void sayHello() {
        writeMap(TYPE_HELLO, new MapBuilder<String, Object>()
                .put("device_name", Build.BRAND + " " + Build.MODEL)
                .put("app_version", BuildConfig.VERSION_NAME)
                .put("app_version_code", BuildConfig.VERSION_CODE)
                .put("server_version", DevPluginService.Version.SERVER)
                .put("device_id", new Device(GlobalAppContext.get()).getAndroidId())
                .build());
    }

    public void onMessage(JsonSocket jsonSocket, String text) {
        Log.d(TAG, "onMessage: text = " + text);
        dispatchJson(jsonSocket, text);
    }

    public void onMessage(JsonSocket jsonSocket, ByteString bytes) {
        Log.d(TAG, "onMessage: ByteString = " + bytes.toString());
        jsonSocket.getBytesPublishSubject().onNext(new Bytes(bytes.md5().hex(), bytes));
    }

    private void onMessageDispatch(JsonSocket jsonSocket, String str) throws IOException {
        Log.d(TAG, "Input total str: " + str);
        Log.d(TAG, "Input total length: " + str.length());

        if (str.length() < HEADER_SIZE) {
            Log.w(TAG, "Message with length less than HEADER_SIZE has been adopted");
            return;
        }

        int idxDataStart = str.indexOf("{");

        if (idxDataStart < 0) {
            Log.w(TAG, "Message without data has been adopted");
            return;
        }

        String header = str.substring(0, idxDataStart);

        if (header.length() == 8) {
            Log.d(TAG, "Input data length: " + new Buffer(header.getBytes()).readInt32BE(0));
            Log.d(TAG, "Input data type: " + new Buffer(header.getBytes()).readInt32BE(4));
        } else if (header.length() >= 4) {
            Log.d(TAG, "Input data type: " + new Buffer(header.getBytes()).readInt32BE(header.length() - 4));
        }

        String message = str.substring(idxDataStart);

        Log.d(TAG, "Input message length: " + message.length());
        Log.d(TAG, "Input message: " + message);

        // Log.d(TAG, "Input message gunzip: " + gunzip(str));

        onMessage(jsonSocket, message);
    }

    @SuppressWarnings("SameParameterValue")
    private void writeMap(String type, Map<String, ?> map) {
        JsonObject data = new JsonObject();
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            addProperty(entry, data);
        }
        writeData(type, data);
    }

    private void addProperty(Map.Entry<String, ?> entry, JsonObject data) {
        Object value = entry.getValue();
        String key = entry.getKey();
        if (value instanceof String) {
            data.addProperty(key, (String) value);
        } else if (value instanceof Character) {
            data.addProperty(key, (Character) value);
        } else if (value instanceof Number) {
            data.addProperty(key, (Number) value);
        } else if (value instanceof Boolean) {
            data.addProperty(key, (Boolean) value);
        } else if (value instanceof JsonElement) {
            data.add(key, (JsonElement) value);
        } else {
            throw new IllegalArgumentException(GlobalAppContext.getString(R.string.error_put_value_into_json, value));
        }
    }

    @SuppressWarnings("SameParameterValue")
    @AnyThread
    public void writePair(String type, Pair<String, String> pair) {
        JsonObject data = new JsonObject();
        data.addProperty(pair.first, pair.second);
        writeData(type, data);
    }

    public void writeLog(String log) {
        if (isSocketReady()) {
            writePair("log", new Pair<>("log", log));
        }
    }

    private void writeData(String type, JsonObject data) {
        JsonObject json = new JsonObject();

        json.addProperty("type", type);
        json.add("data", data);

        writeMessage(json);
    }

    private void writeMessage(JsonElement element) {
        String json = element.toString();
        Log.d(TAG, "writeMessage: length = " + json.length() + ", json = " + element);
        try {
            writeMessageWithType(getSocket(), json, Type.TEXT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeMessageWithType(Socket socket, String message, int messageType) throws IOException {
        if (socket != null) {
            byte[] jsonBytes = getJsonBytes(message);
            byte[] headerBytes = getHeaderBytes(new int[]{jsonBytes.length, messageType});

            OutputStream os = socket.getOutputStream();
            BufferedOutputStream writer = new BufferedOutputStream(os);

            writer.write(headerBytes);
            writer.write(jsonBytes);
            writer.flush();
        }
    }

    public void monitorMessage(Socket socket, JsonSocket jsonSocket) {
        new Thread(() -> {
            // try (AutoCloseable) { ... }
            // @Thank to Zen2H
            try (InputStream inputStream = socket.getInputStream();
                 InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
                 BufferedReader bufferedReader = new BufferedReader(inputStreamReader)
            ) {
                final StringBuilder stringBuilder = new StringBuilder();
                String readLine;
                Log.d(TAG, "bufferedReader is reading lines...");
                while ((readLine = bufferedReader.readLine()) != null && !socket.isClosed()) {
                    Log.d(TAG, "Reading line...");
                    stringBuilder.append(readLine);
                    Log.d(TAG, "read line length: " + stringBuilder.toString().length());
                    onMessageDispatch(jsonSocket, stringBuilder.toString());
                    stringBuilder.setLength(0);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    jsonSocket.switchOff();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void setState(PublishSubject<DevPluginService.State> cxn, int state) {
        cxn.onNext(new DevPluginService.State(state));
    }

    public void setState(PublishSubject<DevPluginService.State> cxn, int state, Throwable e) {
        cxn.onNext(new DevPluginService.State(state, e));
    }

    private void dispatchJson(@NonNull JsonSocket jsonSocket, String json) {
        try {
            Log.d(TAG, "JSON to parse: " + json);
            JsonReader reader = new JsonReader(new StringReader(json));
            reader.setLenient(true);
            JsonElement element = JsonParser.parseReader(reader);
            jsonSocket.getJsonElementPublishSubject().onNext(element);
        } catch (JsonParseException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("CheckResult")
    public void handleBytes(JsonObject jsonObject, JsonSocket.Bytes bytes) {
        devPlugin.mResponseHandler
                .handleBytes(jsonObject, bytes)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(dir -> {
                    jsonObject
                            .get("data")
                            .getAsJsonObject()
                            .add("dir", new JsonPrimitive(dir.getPath()));
                    devPlugin.mResponseHandler.handle(jsonObject);
                });

    }

    private byte[] getJsonBytes(@NonNull String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    @NonNull
    private byte[] getHeaderBytes(@NonNull int[] data) {
        // byte order is big endian
        // use Buffer#readInt32BE for a socket server in Node.js
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES * data.length);
        for (int i : data) {
            // int, 4 bytes
            buffer.putInt(i);
        }
        return buffer.array();
    }

}