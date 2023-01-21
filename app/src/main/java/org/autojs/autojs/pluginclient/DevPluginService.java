package org.autojs.autojs.pluginclient;

import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import org.autojs.autojs.util.ThreadUtils;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

/**
 * Created by Stardust on 2017/5/11.
 */
public class DevPluginService {

    private final Context mContext;

    public static class State {

        public static final int DISCONNECTED = 0;
        public static final int CONNECTING = 1;
        public static final int CONNECTED = 2;

        private final int mState;
        private final Throwable mException;

        public State(int state, Throwable exception) {
            mState = state;
            mException = exception;
        }

        public State(int state) {
            this(state, null);
        }

        public int getState() {
            return mState;
        }

        public Throwable getException() {
            return mException;
        }
    }

    @SuppressWarnings("unused")
    public static class Port {
        static int PC_CLIENT = 27139;
        static int PC_SERVER = 6347;
        static int AJ_CLIENT = -1;
        static int AJ_SERVER = 9317;
    }

    @SuppressWarnings("unused")
    public static class Version {
        static int CLIENT = 2;
        static int SERVER = 3;
    }

    public static final String TYPE_HELLO = "hello";
    public static final String TYPE_COMMAND = "command";
    public static final String TYPE_BYTES_COMMAND = "bytes_command";
    public static final int HANDSHAKE_TIMEOUT = JsonSocket.HANDSHAKE_TIMEOUT;

    private final DevPluginResponseHandler mResponseHandler;

    private volatile JsonSocketClient mJsonSocketClient;
    private volatile JsonSocketServer mJsonSocketServer;
    private volatile ServerSocket mServerSocket;

    public DevPluginService(@NonNull Context context) {
        mContext = context;
        File cache = new File(context.getCacheDir(), "remote_project");
        mResponseHandler = new DevPluginResponseHandler(context, cache);
    }

    public DevPluginResponseHandler getResponseHandler() {
        return mResponseHandler;
    }

    public Context getContext() {
        return mContext;
    }

    public void disconnectJsonSocketClient() {
        try {
            mJsonSocketClient.switchOff();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isJsonSocketClientConnected() {
        return mJsonSocketClient != null && mJsonSocketClient.isSocketReady();
    }

    public boolean isServerSocketConnected() {
        return mServerSocket != null && !mServerSocket.isClosed();
    }

    public void disconnectJsonSocketServer() {
        try {
            mJsonSocketServer.switchOff();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @AnyThread
    public Observable<JsonSocketClient> connectToRemoteServer(String host) {
        int port = Port.PC_SERVER;
        String ip = host;
        int i = host.lastIndexOf(':');
        if (i > 0 && i < host.length() - 1) {
            port = Integer.parseInt(host.substring(i + 1));
            ip = host.substring(0, i);
        }

        return Observable
                .just(new JsonSocketClient(this, ip, port))
                .observeOn(Schedulers.newThread())
                .doOnNext(jsonSocketClient -> {
                    try {
                        mJsonSocketClient = jsonSocketClient;
                        if (ThreadUtils.wait(jsonSocketClient::isSocketReady, HANDSHAKE_TIMEOUT)) {
                            jsonSocketClient
                                    .subscribeMessage()
                                    .monitorMessage()
                                    .sayHello();
                        } else {
                            jsonSocketClient.onHandshakeTimeout();
                        }
                    } catch (IOException e) {
                        jsonSocketClient.onSocketError(e);
                    }
                });
    }

    @AnyThread
    public Observable<JsonSocketServer> enableLocalServer() {
        return Observable
                .just(new JsonSocketServer(this, Port.AJ_SERVER))
                .observeOn(Schedulers.newThread())
                .doOnNext(jsonSocketServer -> {
                    try {
                        mJsonSocketServer = jsonSocketServer;
                        mServerSocket = jsonSocketServer.getServerSocket();
                        if (mServerSocket != null) {
                            jsonSocketServer
                                    .setStateConnected()
                                    .setSocket(mServerSocket.accept())
                                    .subscribeMessage()
                                    .monitorMessage()
                                    .sayHello();
                        } else {
                            jsonSocketServer.onHandshakeTimeout();
                        }
                    } catch (IOException e) {
                        jsonSocketServer.onSocketError(e);
                    }
                });
    }

    public static void setState(PublishSubject<State> cxn, int state) {
        cxn.onNext(new State(state));
    }

    public static void setState(PublishSubject<State> cxn, int state, Throwable e) {
        cxn.onNext(new State(state, e));
    }

    @AnyThread
    // FIXME by SuperMonster003 as of Dec 29, 2021.
    //  ! Would print double (may be even more times) the amount of
    //  ! messages on VSCode when multi connection were established.
    public void print(String log) {
        if (mJsonSocketClient != null) {
            mJsonSocketClient.writeLog(log);
        }
        if (mJsonSocketServer != null) {
            mJsonSocketServer.writeLog(log);
        }
    }

}