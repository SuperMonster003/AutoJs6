package org.autojs.autojs.pluginclient;

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
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import okio.ByteString;
import org.autojs.autojs.runtime.api.Device;
import org.autojs.autojs.tool.MapBuilder;
import org.autojs.autojs6.BuildConfig;
import org.autojs.autojs6.R;
import org.mozilla.javascript.NativeObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.nio.charset.StandardCharsets.UTF_8;

abstract public class JsonSocket extends Socket {

    public static final int HEADER_SIZE = 8;
    public static final int HANDSHAKE_TIMEOUT = 6400;

    public static final String TYPE_HELLO = DevPluginService.TYPE_HELLO;
    public static final String TYPE_COMMAND = DevPluginService.TYPE_COMMAND;
    public static final String TYPE_BYTES_COMMAND = DevPluginService.TYPE_BYTES_COMMAND;

    protected static final HashMap<String, Bytes> sBytes = new HashMap<>();
    protected static final HashMap<String, JsonObject> sRequiredBytesCommands = new HashMap<>();

    private static final String TAG = "JsonSocket";

    public final android.os.Handler mHandler = new Handler(Looper.getMainLooper());

    // Serialize writes to prevent interleaving between threads.
    // zh-CN: 串行化写入, 避免多线程导致帧交错.
    private final Object mWriteLock = new Object();

    private final Context mContext;
    private final DevPluginService mService;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private PublishSubject<JsonElement> mJsonElementPublishSubject;
    private PublishSubject<Bytes> mBytesPublishSubject;

    public JsonSocket(DevPluginService service) {
        mService = service;
        mContext = service.getContext();
    }

    private static void readFully(InputStream in, byte[] buffer, int offset, int length) throws IOException {
        int read;
        int total = 0;
        while (total < length) {
            read = in.read(buffer, offset + total, length - total);
            if (read < 0) {
                throw new IOException("Stream ended unexpectedly");
            }
            total += read;
        }
    }

    public <E> E getLast(Collection<E> c) {
        E last = null;
        for (E e : c) last = e;
        return last;
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
        mJsonElementPublishSubject
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onSocketData, this::onSocketError);

        mBytesPublishSubject = PublishSubject.create();
        mBytesPublishSubject
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onSocketData, this::onSocketError);

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
            // Fail fast on write errors to avoid half-dead connections.
            // zh-CN: 写入出错时快速失败, 避免连接处于"半死不活"状态.
            onSocketError(e);
        }
    }

    private void writeMessageWithType(Socket socket, String message, int messageType) throws IOException {
        if (socket != null) {
            byte[] jsonBytes = getJsonBytes(message);
            byte[] headerBytes = getHeaderBytes(new int[]{jsonBytes.length, messageType});

            synchronized (mWriteLock) {
                OutputStream os = socket.getOutputStream();
                BufferedOutputStream writer = new BufferedOutputStream(os);

                writer.write(headerBytes);
                writer.write(jsonBytes);
                writer.flush();
            }
        }
    }

    public void monitorMessage(Socket socket, JsonSocket jsonSocket) {

        // @Caution by SuperMonster003 on May 30, 2023.
        //  ! DO NOT use BufferedReader#readLine as it doesn't distinguish U+000A (\n) and U+000D (\r),
        //  ! which makes MD5 not matching the one from Node.js .
        //  ! zh-CN:
        //  ! 不要使用 BufferedReader 的 readLine 方法,
        //  ! 因为它不能区分换行符 \n (U+000A) 和回车符 \r (U+000D),
        //  ! 这将导致产生的 MD5 散列值与在 Node.js 环境中得到的 MD5 散列值不匹配.

        executorService.execute(() -> {
            // Use byte stream to read framed binary protocol reliably.
            // zh-CN: 使用字节流读取分帧二进制协议, 提升可靠性.
            try (InputStream inputStream = socket.getInputStream();
                 BufferedInputStream bis = new BufferedInputStream(inputStream)
            ) {
                Log.d(TAG, "bufferedReader is reading...");

                byte[] header = new byte[HEADER_SIZE];

                while (!socket.isClosed()) {
                    readFully(bis, header, 0, HEADER_SIZE);

                    ByteBuffer hb = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);
                    int dataSize = hb.getInt();
                    int dataType = hb.getInt();

                    Log.d(TAG, "Data length from header: " + dataSize);
                    Log.d(TAG, "Data type from header: " + dataType);

                    if (dataSize < 0) {
                        throw new IOException("Invalid data length: " + dataSize);
                    }

                    byte[] payload = new byte[dataSize];
                    readFully(bis, payload, 0, dataSize);

                    if (dataType == Type.JSON) {
                        onMessage(new String(payload, UTF_8));
                    } else if (dataType == Type.BYTES) {
                        onMessage(ByteString.of(payload));
                    } else {
                        throw new IOException("Unknown data type: " + dataType);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                // Treat "Socket closed" as a normal shutdown path.
                // zh-CN: 将 "Socket closed" 视为正常关闭流程.
                String message = e.getMessage();
                if (message != null && message.toLowerCase().contains("socket closed")) {
                    return;
                }

                // Treat "Stream ended unexpectedly" as error unless user requested normal close.
                // zh-CN: 除非用户主动正常关闭, 否则将 "Stream ended unexpectedly" 视为错误.
                if (message != null && message.toLowerCase().contains("stream ended unexpectedly")) {
                    boolean isNormallyClosed = false;
                    try {
                        if (jsonSocket instanceof JsonSocketClient) {
                            isNormallyClosed = JsonSocketClient.Companion.isClientSocketNormallyClosed();
                        }
                    } catch (Throwable ignored) {
                        /* Ignored. */
                    }
                    if (isNormallyClosed) {
                        return;
                    }
                }

                onSocketError(e);
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

    public void setState(Subject<DevPluginService.State> cxn, int state) {
        cxn.onNext(new DevPluginService.State(state));
    }

    public void setState(Subject<DevPluginService.State> cxn, int state, Throwable e) {
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

}