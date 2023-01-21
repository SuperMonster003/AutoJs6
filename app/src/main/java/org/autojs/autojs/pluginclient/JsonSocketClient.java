package org.autojs.autojs.pluginclient;

import android.annotation.SuppressLint;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.WorkerThread;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.autojs.autojs6.R;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.subjects.PublishSubject;

@SuppressWarnings({"resource", "UnusedReturnValue"})
public class JsonSocketClient extends JsonSocket {

    private static final String TAG = JsonSocketClient.class.getSimpleName();

    private final ExecutorService jsonSocketExecutor = Executors.newSingleThreadExecutor();

    public static final PublishSubject<DevPluginService.State> cxnState = PublishSubject.create();

    private final PublishSubject<JsonElement> mJsonElementPublishSubject = PublishSubject.create();
    private final PublishSubject<Bytes> mBytesPublishSubject = PublishSubject.create();

    private final HashMap<String, Bytes> mBytes = new HashMap<>();
    private final HashMap<String, JsonObject> mRequiredBytesCommands = new HashMap<>();

    private Socket mSocket;

    public JsonSocketClient(DevPluginService service, String host, int port) {
        super(service);
        jsonSocketExecutor.submit(() -> {
            try {
                setStateConnecting();
                mSocket = new Socket(host, port);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public boolean isSocketReady() {
        return mSocket != null && mSocket.isConnected();
    }

    @Override
    public Socket getSocket() {
        return mSocket;
    }

    @Override
    public JsonSocket setSocket(Socket socket) {
        mSocket = socket;
        return this;
    }

    @Override
    public PublishSubject<JsonElement> getJsonElementPublishSubject() {
        return mJsonElementPublishSubject;
    }

    @Override
    public PublishSubject<Bytes> getBytesPublishSubject() {
        return mBytesPublishSubject;
    }

    @Override
    public void switchOff() throws IOException {
        close();
        setStateDisconnected();
    }

    public void close() throws IOException {
        Log.w(TAG, "closing socket...");
        mJsonElementPublishSubject.onComplete();
        if (mSocket != null) {
            mSocket.close();
            mSocket = null;
        }
        jsonSocketExecutor.shutdown();
    }

    @Override
    public void sayHello() {
        super.sayHello();
        mHandler.postDelayed(() -> {
            if (!isSocketReady()) {
                try {
                    onHandshakeTimeout();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, HANDSHAKE_TIMEOUT);
    }

    private void onHello(JsonObject message) {
        Log.i(TAG, "onHello: " + message);
        setStateConnected();
    }

    @MainThread
    private void onSocketData(JsonElement element) {
        Log.d(TAG, "onSocketData...");

        try {
            if (!element.isJsonObject()) {
                onSocketError(new Error("Not a JSON object"));
                return;
            }
            JsonObject obj = element.getAsJsonObject();
            JsonElement typeElement = obj.get("type");
            if (typeElement == null || !typeElement.isJsonPrimitive()) {
                return;
            }
            String type = typeElement.getAsString();
            Log.d(TAG, "json type: " + type);
            switch (type) {
                case TYPE_HELLO -> onHello(obj);
                case TYPE_BYTES_COMMAND -> {
                    String md5 = obj.get("md5").getAsString();
                    JsonSocket.Bytes bytes = mBytes.remove(md5);
                    if (bytes != null) {
                        handleBytes(obj, bytes);
                    } else {
                        mRequiredBytesCommands.put(md5, obj);
                    }
                }
                default -> getService().getResponseHandler().handle(obj);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @WorkerThread
    private void onSocketData(JsonSocket.Bytes bytes) {
        Log.d(TAG, "onSocketData bytes");
        JsonObject command = mRequiredBytesCommands.remove(bytes.md5);
        if (command != null) {
            handleBytes(command, bytes);
        } else {
            mBytes.put(bytes.md5, bytes);
        }
    }

    @MainThread
    public void onSocketError(Throwable e) throws IOException {
        Log.w(TAG, "onSocketError");
        e.printStackTrace();
        setStateDisconnected(e);
        close();
    }

    @MainThread
    public void onHandshakeTimeout() throws IOException {
        Log.i(TAG, "onHandshakeTimeout");
        setStateDisconnected(new SocketTimeoutException(getContext().getString(R.string.error_handshake_timed_out, HANDSHAKE_TIMEOUT)));
        close();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("CheckResult")
    @Override
    public JsonSocket subscribeMessage() {
        mJsonElementPublishSubject
                .observeOn(AndroidSchedulers.mainThread())
                .doOnComplete(this::setStateDisconnected)
                .subscribe(this::onSocketData, this::onSocketError);
        mBytesPublishSubject
                .doOnComplete(this::setStateDisconnected)
                .subscribe(this::onSocketData, this::onSocketError);

        return this;
    }

    public JsonSocket monitorMessage() {
        super.monitorMessage(mSocket, this);
        return this;
    }

    public JsonSocket setStateConnected() {
        setState(cxnState, DevPluginService.State.CONNECTED);
        return this;
    }

    public JsonSocket setStateConnecting() {
        setState(cxnState, DevPluginService.State.CONNECTING);
        return this;
    }

    public JsonSocket setStateDisconnected() {
        setState(cxnState, DevPluginService.State.DISCONNECTED);
        return this;
    }

    public JsonSocket setStateDisconnected(Throwable e) {
        setState(cxnState, DevPluginService.State.DISCONNECTED, e);
        return this;
    }

}
