package org.autojs.autojs.pluginclient;

import android.annotation.SuppressLint;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.WorkerThread;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.autojs.autojs.pref.Pref;
import org.autojs.autojs6.R;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.subjects.PublishSubject;

@SuppressWarnings({"UnusedReturnValue", "resource"})
public class JsonSocketServer extends JsonSocket {

    private final static String prefKeyServerSocketNormallyClosed = "key_$_server_socket_normally_closed";

    private static final String TAG = JsonSocketServer.class.getSimpleName();

    public static final PublishSubject<DevPluginService.State> cxnState = PublishSubject.create();

    private final PublishSubject<JsonElement> mJsonElementPublishSubject = PublishSubject.create();
    private final PublishSubject<Bytes> mBytesPublishSubject = PublishSubject.create();

    private final HashMap<String, Bytes> mBytes = new HashMap<>();
    private final HashMap<String, JsonObject> mRequiredBytesCommands = new HashMap<>();

    private Socket mSocket;
    private ServerSocket mServerSocket;

    public JsonSocketServer(DevPluginService service, int port) {
        super(service);
        try {
            setStateConnecting();
            mServerSocket = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
        }
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

    public boolean isSocketReady() {
        return mSocket != null && !mSocket.isClosed();
    }

    public boolean isServerSocketReady() {
        return mServerSocket != null && !mServerSocket.isClosed();
    }

    public static boolean isServerSocketNormallyClosed() {
        return Pref.getBoolean(prefKeyServerSocketNormallyClosed, true);
    }

    public static void setServerSocketNormallyClosed(boolean state) {
        Pref.putBoolean(prefKeyServerSocketNormallyClosed, state);
    }

    @Override
    public Socket getSocket() {
        return mSocket;
    }

    public ServerSocket getServerSocket() {
        return mServerSocket;
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
        if (isServerSocketReady()) {
            mServerSocket.close();
            mServerSocket = null;
        }
        close();
        setStateDisconnected();
        setServerSocketNormallyClosed(true);
    }

    public void close() throws IOException {
        if (isSocketReady()) {
            mSocket.close();
            mSocket = null;
        }
    }

    @Override
    public void sayHello() {
        super.sayHello();
        mHandler.postDelayed(() -> {
            if (!isServerSocketReady()) {
                try {
                    onHandshakeTimeout();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, HANDSHAKE_TIMEOUT);
    }

    public JsonSocket monitorMessage() {
        super.monitorMessage(mSocket, this);
        return this;
    }

    @MainThread
    private void onSocketData(JsonElement element) {
        Log.d(TAG, "onSocketData...");

        try {
            if (!element.isJsonObject()) {
                onSocketError(new Exception("Not a JSON object"));
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
                case TYPE_HELLO -> setStateConnected();
                case TYPE_BYTES_COMMAND -> {
                    String md5 = obj.get("md5").getAsString();
                    Bytes bytes = mBytes.remove(md5);
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
    private void onSocketData(Bytes bytes) {
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
        e.printStackTrace();
        if (isServerSocketReady()) {
            setStateDisconnected(e);
            switchOff();
        }
    }

    @MainThread
    public void onHandshakeTimeout() throws IOException {
        Log.i(TAG, "onHandshakeTimeout");
        setStateDisconnected(new SocketTimeoutException(getContext().getString(R.string.error_handshake_timed_out, HANDSHAKE_TIMEOUT)));
        switchOff();
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
