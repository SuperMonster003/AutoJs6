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
import java.net.SocketTimeoutException;
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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.charset.StandardCharsets.UTF_8;

abstract public class JsonSocket extends Socket {

    public static final int HEADER_SIZE = 8;
    public static final int HANDSHAKE_TIMEOUT = 6400;

    /**
     * Protocol version spoken by this app. v2 adds ping/pong heartbeat and request/response messages.
     * zh-CN: 应用支持的协议版本. v2 增加 ping/pong 心跳与 request/response 消息.
     */
    public static final int PROTOCOL_VERSION = 2;

    /**
     * Max size of a single frame, used to detect desync early and prevent huge allocations.
     * zh-CN: 单帧最大长度, 用于尽早发现失步并避免超大内存分配.
     */
    public static final int MAX_FRAME_SIZE = 64 * 1024 * 1024;

    /**
     * Heartbeat: ping when idle for HEARTBEAT_INTERVAL, treat as dead when idle for HEARTBEAT_TIMEOUT.
     * zh-CN: 心跳: 空闲超过 HEARTBEAT_INTERVAL 发送 ping, 空闲超过 HEARTBEAT_TIMEOUT 判定连接失效.
     */
    public static final long HEARTBEAT_INTERVAL = 20_000L;
    public static final long HEARTBEAT_TIMEOUT = 60_000L;

    /**
     * Max pending outbound messages per connection; exceeding it means the peer is not reading.
     * zh-CN: 每个连接的最大待发送消息数, 超出即认为对端已停止读取.
     */
    private static final int MAX_PENDING_WRITES = 4096;

    public static final String TYPE_HELLO = DevPluginService.TYPE_HELLO;
    public static final String TYPE_COMMAND = DevPluginService.TYPE_COMMAND;
    public static final String TYPE_BYTES_COMMAND = DevPluginService.TYPE_BYTES_COMMAND;
    public static final String TYPE_PING = DevPluginService.TYPE_PING;
    public static final String TYPE_PONG = DevPluginService.TYPE_PONG;
    public static final String TYPE_REQUEST = DevPluginService.TYPE_REQUEST;
    public static final String TYPE_RESPONSE = DevPluginService.TYPE_RESPONSE;

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

    // One bounded single-thread writer per connection so that a stalled peer cannot block
    // the global log fan-out or other connections.
    // zh-CN: 每个连接独立的有界单线程写入器, 使停滞的对端无法阻塞全局日志分发或其他连接.
    private final ThreadPoolExecutor mWriteExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(MAX_PENDING_WRITES));

    private final AtomicBoolean mWriteFailureReported = new AtomicBoolean(false);

    private PublishSubject<JsonElement> mJsonElementPublishSubject;
    private PublishSubject<Bytes> mBytesPublishSubject;

    private volatile int mPeerProtocolVersion = 1;
    private volatile long mLastInboundAt = System.currentTimeMillis();
    private volatile boolean mHeartbeatStarted = false;

    private final Runnable mHeartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isSocketReady()) {
                return;
            }
            long idle = System.currentTimeMillis() - mLastInboundAt;
            if (idle >= HEARTBEAT_TIMEOUT) {
                Log.w(TAG, "heartbeat timed out after " + idle + " ms");
                onSocketError(new SocketTimeoutException("Heartbeat timed out after " + idle + " ms"));
                return;
            }
            if (idle >= HEARTBEAT_INTERVAL) {
                JsonObject data = new JsonObject();
                data.addProperty("ts", System.currentTimeMillis());
                writeData(TYPE_PING, data);
            }
            mHandler.postDelayed(this, HEARTBEAT_INTERVAL);
        }
    };

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

    /** Protocol version announced by the peer in its hello (1 for legacy extensions). zh-CN: 对端在 hello 中声明的协议版本. */
    public int getPeerProtocolVersion() {
        return mPeerProtocolVersion;
    }

    public boolean isPeerProtocolV2() {
        return mPeerProtocolVersion >= 2;
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
                .put("protocol_version", PROTOCOL_VERSION)
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
        mLastInboundAt = System.currentTimeMillis();
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onMessage: text = " + abbreviate(text));
        }
        dispatchJson(text);
    }

    public void onMessage(ByteString bytes) {
        mLastInboundAt = System.currentTimeMillis();
        // Never stringify the payload here: a project archive can be tens of megabytes (OOM).
        // zh-CN: 切勿在此将载荷转为字符串: 项目归档可能达数十 MB (会导致 OOM).
        Log.d(TAG, "onMessage: bytes length = " + bytes.size() + ", md5 hex = " + bytes.md5().hex());

        /* private void onSocketData(Bytes bytes) */
        if (mBytesPublishSubject != null) {
            mBytesPublishSubject.onNext(new Bytes(bytes.md5().hex(), bytes));
        }
    }

    private static String abbreviate(String s) {
        return s.length() > 512 ? s.substring(0, 512) + "... (" + s.length() + " chars)" : s;
    }

    /**
     * Record the peer's protocol version from its hello and start the heartbeat when supported.
     * zh-CN: 从对端 hello 记录协议版本, 若支持则启动心跳.
     */
    protected void onPeerHello(@NonNull JsonObject message) {
        JsonElement data = message.get("data");
        if (data != null && data.isJsonObject()) {
            JsonElement version = data.getAsJsonObject().get("protocolVersion");
            if (version != null && version.isJsonPrimitive()) {
                try {
                    mPeerProtocolVersion = version.getAsInt();
                } catch (Exception e) {
                    mPeerProtocolVersion = 1;
                }
            }
        }
        Log.i(TAG, "peer protocol version: " + mPeerProtocolVersion);
        if (isPeerProtocolV2()) {
            startHeartbeat();
        }
    }

    private void startHeartbeat() {
        if (mHeartbeatStarted) {
            return;
        }
        mHeartbeatStarted = true;
        mLastInboundAt = System.currentTimeMillis();
        mHandler.removeCallbacks(mHeartbeatRunnable);
        mHandler.postDelayed(mHeartbeatRunnable, HEARTBEAT_INTERVAL);
    }

    private void stopHeartbeat() {
        mHeartbeatStarted = false;
        mHandler.removeCallbacks(mHeartbeatRunnable);
    }

    /**
     * Handle protocol-level messages shared by client and server sockets (ping, pong, request).
     * Returns true when the message was consumed.
     * zh-CN: 处理客户端与服务端共用的协议级消息 (ping, pong, request). 已消费时返回 true.
     */
    protected boolean handleProtocolMessage(@NonNull String type, @NonNull JsonObject message) {
        switch (type) {
            case TYPE_PING: {
                JsonElement data = message.get("data");
                writeData(TYPE_PONG, data != null && data.isJsonObject() ? data.getAsJsonObject() : new JsonObject());
                return true;
            }
            case TYPE_PONG:
                return true;
            case TYPE_REQUEST:
                mService.getRequestHandler().handle(this, message);
                return true;
            case TYPE_RESPONSE:
                return true;
            default:
                return false;
        }
    }

    /** Write a successful response to a request (protocol v2). zh-CN: 写入请求的成功响应 (协议 v2). */
    @AnyThread
    public void writeResponse(@Nullable JsonElement id, @Nullable JsonElement result) {
        JsonObject data = new JsonObject();
        data.addProperty("ok", true);
        data.add("result", result == null ? new JsonObject() : result);
        writeEnvelope(TYPE_RESPONSE, id, data);
    }

    /** Write an error response to a request (protocol v2). zh-CN: 写入请求的错误响应 (协议 v2). */
    @AnyThread
    public void writeErrorResponse(@Nullable JsonElement id, @NonNull String error) {
        JsonObject data = new JsonObject();
        data.addProperty("ok", false);
        data.addProperty("error", error);
        writeEnvelope(TYPE_RESPONSE, id, data);
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

    @AnyThread
    public void writeLog(String log) {
        if (isSocketReady()) {
            writePair("log", new Pair<>("log", log));
        }
    }

    private void writeData(String type, JsonObject data) {
        writeEnvelope(type, null, data);
    }

    private void writeEnvelope(String type, @Nullable JsonElement id, JsonObject data) {
        JsonObject json = new JsonObject();
        if (id != null) {
            json.add("id", id);
        }
        json.addProperty("type", type);
        json.add("data", data);

        writeMessage(json);
    }

    /**
     * Enqueue a JSON frame on this connection's writer. Never blocks the caller.
     * zh-CN: 将 JSON 帧放入本连接的写入队列, 不阻塞调用方.
     */
    private void writeMessage(JsonElement element) {
        String json = element.toString();
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "writeMessage: length = " + json.length() + ", json = " + abbreviate(json));
        }
        byte[] payload = getJsonBytes(json);
        try {
            mWriteExecutor.execute(() -> {
                try {
                    writeFrame(getSocket(), payload, Type.JSON);
                } catch (IOException e) {
                    e.printStackTrace();
                    // Fail fast on write errors to avoid half-dead connections.
                    // zh-CN: 写入出错时快速失败, 避免连接处于"半死不活"状态.
                    reportWriteFailure(e);
                }
            });
        } catch (RejectedExecutionException e) {
            // Queue full or writer shut down: the peer stopped reading, drop the connection.
            // zh-CN: 队列已满或写入器已关闭: 对端已停止读取, 断开连接.
            if (!mWriteExecutor.isShutdown()) {
                reportWriteFailure(new IOException("Outbound queue overflow (" + MAX_PENDING_WRITES + " pending frames)"));
            }
        }
    }

    private void reportWriteFailure(IOException e) {
        if (mWriteFailureReported.compareAndSet(false, true)) {
            mHandler.post(() -> onSocketError(e));
        }
    }

    private void writeFrame(Socket socket, byte[] payload, int messageType) throws IOException {
        if (socket != null && !socket.isClosed()) {
            byte[] headerBytes = getHeaderBytes(new int[]{payload.length, messageType});

            synchronized (mWriteLock) {
                OutputStream os = socket.getOutputStream();
                BufferedOutputStream writer = new BufferedOutputStream(os);

                writer.write(headerBytes);
                writer.write(payload);
                writer.flush();
            }
        }
    }

    /**
     * Release per-connection resources (writer, heartbeat). Safe to call multiple times.
     * zh-CN: 释放连接级资源 (写入器, 心跳). 可重复调用.
     */
    protected void releaseResources() {
        stopHeartbeat();
        mWriteExecutor.shutdownNow();
    }

    public void monitorMessage(Socket socket, JsonSocket jsonSocket) {

        // @Caution by SuperMonster003 on May 30, 2023.
        //  ! DO NOT use BufferedReader#readLine as it doesn't distinguish U+000A (\n) and U+000D (\r),
        //  ! which makes MD5 not matching the one from Node.js .
        //  ! zh-CN:
        //  ! 不要使用 BufferedReader 的 readLine 方法,
        //  ! 因为它不能区分换行符 \n (U+000A) 和回车符 \r (U+000D),
        //  ! 这将导致产生的 MD5 散列值与在 Node.js 环境中得到的 MD5 散列值不匹配.

        try {
            // Detect dead peers at the TCP level and avoid Nagle delays for small frames.
            // zh-CN: 在 TCP 层检测失效对端, 并避免小帧的 Nagle 延迟.
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);
        } catch (Exception e) {
            e.printStackTrace();
        }

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

                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Data length from header: " + dataSize + ", type: " + dataType);
                    }

                    if (dataSize < 0 || dataSize > MAX_FRAME_SIZE) {
                        throw new IOException("Invalid data length: " + dataSize);
                    }
                    if (dataType != Type.JSON && dataType != Type.BYTES) {
                        throw new IOException("Unknown data type: " + dataType);
                    }

                    byte[] payload = new byte[dataSize];
                    readFully(bis, payload, 0, dataSize);

                    if (dataType == Type.JSON) {
                        onMessage(new String(payload, UTF_8));
                    } else {
                        onMessage(ByteString.of(payload));
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

                mHandler.post(() -> onSocketError(e));
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
                }, e -> {
                    e.printStackTrace();
                    Log.e(TAG, "failed to handle bytes command: " + e.getMessage());
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
