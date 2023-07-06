package org.autojs.autojs.pluginclient;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;

import org.autojs.autojs.runtime.ScriptRuntime;
import org.autojs.autojs.runtime.api.Device;
import org.autojs.autojs.tool.MapBuilder;
import org.autojs.autojs6.BuildConfig;
import org.autojs.autojs6.R;
import org.mozilla.javascript.NativeObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.subjects.PublishSubject;
import okio.ByteString;

abstract public class JsonSocket extends Socket {

    private static final String TAG = "JsonSocket";

    protected static final HashMap<String, Bytes> sBytes = new HashMap<>();

    protected static final HashMap<String, JsonObject> sRequiredBytesCommands = new HashMap<>();
    private PublishSubject<JsonElement> mJsonElementPublishSubject;
    private PublishSubject<Bytes> mBytesPublishSubject;

    public <E> E getLast(Collection<E> c) {
        E last = null;
        for (E e : c) last = e;
        return last;
    }

    private Fragment mFragment = null;

    private final Context mContext;
    private final DevPluginService mService;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public static final int HEADER_SIZE = 8;
    public static final int HANDSHAKE_TIMEOUT = 5 * 1000;

    public static final String TYPE_HELLO = DevPluginService.TYPE_HELLO;
    public static final String TYPE_COMMAND = DevPluginService.TYPE_COMMAND;
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

    public static class Type {
        public static int JSON = 1;
        public static int BYTES = 2;
    }

    public final android.os.Handler mHandler = new Handler(Looper.getMainLooper());

    public JsonSocket(DevPluginService service) {
        mService = service;
        mContext = service.getContext();
    }

    protected Context getContext() {
        return mContext;
    }

    protected DevPluginService getService() {
        return mService;
    }

    public abstract void switchOff() throws IOException;

    public abstract boolean isSocketReady();

    public abstract Socket getSocket();

    public abstract JsonSocket setSocket(Socket socket);

    public abstract JsonSocket monitorMessage();

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("CheckResult")
    public JsonSocket subscribeMessage() {
        mJsonElementPublishSubject = PublishSubject.create();
        mJsonElementPublishSubject.observeOn(AndroidSchedulers.mainThread());
        mJsonElementPublishSubject.subscribe(this::onSocketData, this::onSocketError);

        mBytesPublishSubject = PublishSubject.create();
        mBytesPublishSubject.observeOn(AndroidSchedulers.mainThread());
        mBytesPublishSubject.subscribe(this::onSocketData, this::onSocketError);

        return this;
    }

    protected abstract void onSocketError(Throwable e);

    protected abstract void onSocketData(Bytes bytes);

    protected abstract void onSocketData(JsonElement element);

    public abstract JsonSocket setStateConnected();

    public void sayHello() {
        writeMap(TYPE_HELLO, new MapBuilder<String, Object>()
                .put("device_name", Build.BRAND + " " + Build.MODEL)
                .put("app_version", BuildConfig.VERSION_NAME)
                .put("app_version_code", BuildConfig.VERSION_CODE)
                .put("device_id", new Device(mContext).getAndroidId())
                .build());
    }

    @SuppressWarnings("unused")
    public void sendCommand(String commandName) {
        sendCommand(commandName, null);
    }

    public void sendCommand(String commandName, @Nullable NativeObject jsObject) {
        MapBuilder<String, Object> builder = new MapBuilder<String, Object>()
                .put("\u00a0cmd\u00a0", commandName);
        if (jsObject != null) {
            for (Map.Entry<Object, Object> entry : jsObject.entrySet()) {
                builder.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        writeMap(TYPE_COMMAND, builder.build());
    }

    public void onMessage(String text) {
        Log.d(TAG, "onMessage: text = " + text);
        dispatchJson(text);
    }

    public void onMessage(ByteString bytes) {
        Log.d(TAG, "onMessage: ByteArray = " + Arrays.toString(bytes.toByteArray()));
        Log.d(TAG, "bytes md5 hex: " + bytes.md5().hex());

        /* private void onSocketData(Bytes bytes) */
        if (mBytesPublishSubject != null) {
            mBytesPublishSubject.onNext(new Bytes(bytes.md5().hex(), bytes));
        }
    }

    private void onMessageDispatch(char[] bytes) {
        Log.d(TAG, "Received message total bytes: " + Arrays.toString(bytes));
        Log.d(TAG, "Received message total length: " + bytes.length);

        char[] overload;

        if (mFragment != null) {
            overload = mFragment.splice(bytes);
        } else {
            String header = new String(Arrays.copyOfRange(bytes, 0, HEADER_SIZE));

            int dataSize = parseHeaderInt(header, 0);
            Log.d(TAG, "Data length from header: " + dataSize);

            int dataType = parseHeaderInt(header, 4);
            Log.d(TAG, "Data type from header: " + dataType);

            mFragment = new Fragment(dataSize, dataType);
            overload = mFragment.splice(Arrays.copyOfRange(bytes, HEADER_SIZE, bytes.length));
        }

        if (!mFragment.isRestored()) {
            return;
        }
        char[] restored = mFragment.getRestoredBytes();
        int type = mFragment.getAimDataType();
        mFragment = null;

        if (type == Type.JSON) {
            onMessage(new String(charsToBytes(restored, UTF_8), UTF_8));
        } else if (type == Type.BYTES) {
            onMessage(ByteString.of(charsToBytes(restored, ISO_8859_1)));
        } else {
            ScriptRuntime.popException("Unknown data type (" + type + ") for message dispatching");
        }

        if (overload != null) {
            onMessageDispatch(overload);
        }
    }

    private static int parseHeaderInt(String header, int offset) {
        try {
            return Integer.parseInt(new String(header.getBytes(UTF_8), offset, 4).replaceAll("\\D", ""));
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
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
            throw new IllegalArgumentException(mContext.getString(R.string.error_put_value_into_json, value));
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
            writeMessageWithType(getSocket(), json, Type.JSON);
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

        // CAUTION by SuperMonster003 on May 30, 2023.
        //  ! DO NOT use BufferedReader#readLine as it doesn't distinguish U+000A (\n) and U+000D (\r),
        //  ! which makes MD5 not matching the one from Node.js.

        executorService.execute(() -> {
            // try (AutoCloseable) { ... }
            // @Thank to Zen2H
            try (InputStream inputStream = socket.getInputStream();
                 InputStreamReader inputStreamReader = new InputStreamReader(inputStream, UTF_8);
                 BufferedReader bufferedReader = new BufferedReader(inputStreamReader)
            ) {
                char[] buff = new char[2048];
                int k;

                Log.d(TAG, "bufferedReader is reading...");
                while ((k = bufferedReader.read(buff, 0, buff.length)) > -1 && !socket.isClosed()) {
                    Log.d(TAG, "read length: " + k);
                    onMessageDispatch(Arrays.copyOfRange(buff, 0, k));
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (jsonSocket instanceof JsonSocketClient) {
                    try {
                        jsonSocket.switchOff();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    protected byte[] charsToBytes(char[] chars, Charset charset) {
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        ByteBuffer byteBuffer = charset.encode(charBuffer);
        byte[] bytes = Arrays.copyOfRange(byteBuffer.array(),
                byteBuffer.position(), byteBuffer.limit());
        Arrays.fill(byteBuffer.array(), (byte) 0); // clear sensitive data
        return bytes;
    }

    public void setState(PublishSubject<DevPluginService.State> cxn, int state) {
        cxn.onNext(new DevPluginService.State(state));
    }

    public void setState(PublishSubject<DevPluginService.State> cxn, int state, Throwable e) {
        cxn.onNext(new DevPluginService.State(state, e));
    }

    private void dispatchJson(String json) {
        try {
            Log.d(TAG, "JSON to parse: " + json);
            JsonReader reader = new JsonReader(new StringReader(json));
            reader.setLenient(true);
            JsonElement element = JsonParser.parseReader(reader);
            if (mJsonElementPublishSubject != null) {
                mJsonElementPublishSubject.onNext(element);
            }
        } catch (JsonParseException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("CheckResult")
    public void handleBytes(JsonObject jsonObject, JsonSocket.Bytes bytes) {
        mService.getResponseHandler()
                .handleBytes(jsonObject, bytes)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(dir -> {
                    jsonObject
                            .get("data")
                            .getAsJsonObject()
                            .add("dir", new JsonPrimitive(dir.getPath()));
                    mService.getResponseHandler().handle(jsonObject);
                });

    }

    private byte[] getJsonBytes(@NonNull String json) {
        return json.getBytes(UTF_8);
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

    private static class Fragment {

        private final int aimLength;
        private final int aimDataType;

        private char[] bytes = new char[0];
        private int currentLength = 0;

        public Fragment(int aimLength, int aimDataType) {
            this.aimLength = aimLength;
            this.aimDataType = aimDataType;
        }

        public boolean isRestored() {
            return currentLength >= aimLength;
        }

        public char[] splice(char[] charBytes) {
            int tempLength = currentLength + charBytes.length;
            if (tempLength <= aimLength) {
                this.bytes = joinBytes(this.bytes, charBytes);
                currentLength = currentLength + charBytes.length;
                Log.d(TAG, "currentLength: " + currentLength + "/" + aimLength);
                return null;
            }
            int overloadLength = tempLength - aimLength;
            Log.w(TAG, "charBytes overloaded: " + overloadLength);
            char[] leftPart = Arrays.copyOfRange(charBytes, 0, charBytes.length - overloadLength);
            char[] rightPart = Arrays.copyOfRange(charBytes, charBytes.length - overloadLength, charBytes.length);

            splice(leftPart);
            return rightPart;
        }

        public char[] getRestoredBytes() {
            return bytes;
        }

        public int getAimDataType() {
            return aimDataType;
        }

        private static char[] joinBytes(final char[] array1, char[] array2) {
            char[] joinedArray = Arrays.copyOf(array1, array1.length + array2.length);
            System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);
            return joinedArray;
        }

    }

}