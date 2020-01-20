package com.vilyever.socketclient;

import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;

import com.vilyever.socketclient.helper.SocketClientAddress;
import com.vilyever.socketclient.helper.SocketClientDelegate;
import com.vilyever.socketclient.helper.SocketClientReceivingDelegate;
import com.vilyever.socketclient.helper.SocketClientSendingDelegate;
import com.vilyever.socketclient.helper.SocketConfigure;
import com.vilyever.socketclient.helper.SocketHeartBeatHelper;
import com.vilyever.socketclient.helper.SocketInputReader;
import com.vilyever.socketclient.helper.SocketPacket;
import com.vilyever.socketclient.helper.SocketPacketHelper;
import com.vilyever.socketclient.helper.SocketResponsePacket;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * SocketClient
 * AndroidSocketClient <com.vilyever.socketclient>
 * Created by vilyever on 2016/3/18.
 * Feature:
 */
@SuppressWarnings("UnusedReturnValue")
public class SocketClient {
    public static final String TAG = SocketClient.class.getSimpleName();

    private SocketConfigure socketConfigure = new SocketConfigure();
    /**
     * 当前连接状态
     * 当设置状态为{@link State#Connected}, 收发线程等初始操作均未启动
     * 此状态仅为一个标识
     */
    private State state;

    private ThreadPoolExecutor executor;


    /* Constructors */
    public SocketClient() {
        newThreadPool();
    }

    private synchronized void newThreadPool() {
        if (executor == null || executor.isShutdown()) {
            executor = new ThreadPoolExecutor(3, 10, 60, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
        }
    }

    private synchronized ThreadPoolExecutor getThreadPool() {
        if (executor == null) {
            newThreadPool();
        }

        return executor;
    }

    /* Public Methods */
    public synchronized void connect() {
        if (!isDisconnected()) {
            return;
        }

        if (getAddress() == null) {
            throw new IllegalArgumentException("we need a SocketClientAddress to connect");
        }

        getAddress().checkValidation();
        getSocketPacketHelper().checkValidation();

        updateState(State.Connecting);
        getConnectionThread().start();
    }

    public synchronized void disconnect() {
        if (isDisconnected() || isDisconnecting()) {
            return;
        }

        setDisconnecting(true);

        getDisconnectionThread().start();
    }

    public boolean isConnected() {
        return getState() == State.Connected;
    }

    public boolean isDisconnected() {
        return getState() == State.Disconnected;
    }

    public boolean isConnecting() {
        return getState() == State.Connecting;
    }

    /**
     * 发送byte数组
     * @param data
     * @return 打包后的数据包
     */
    public SocketPacket sendData(byte[] data) {
        if (!isConnected()) {
            return null;
        }
        SocketPacket packet = new SocketPacket(data);
        sendPacket(packet);
        return packet;
    }

    /**
     * 发送字符串，将以{charsetName}编码为byte数组
     * @param message
     * @return 打包后的数据包
     */
    public SocketPacket sendString(String message) {
        if (!isConnected()) {
            return null;
        }
        SocketPacket packet = new SocketPacket(message);
        sendPacket(packet);
        return packet;
    }

    public SocketPacket sendPacket(final SocketPacket packet) {
        if (!isConnected()) {
            return null;
        }

        if (packet == null) {
            return null;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                __i__enqueueNewPacket(packet);
            }
        }).start();

        return packet;
    }

    public void cancelSend(final SocketPacket packet) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (getSendingPacketQueue()) {
                    if (getSendingPacketQueue().contains(packet)) {
                        getSendingPacketQueue().remove(packet);

                        __i__onSendPacketCancel(packet);
                    }
                }
            }
        }).start();
    }

    public SocketResponsePacket readDataToLength(final int length) {
        if (!isConnected()) {
            return null;
        }

        if (getSocketConfigure().getSocketPacketHelper().getReadStrategy() != SocketPacketHelper.ReadStrategy.Manually) {
            return null;
        }

        if (getReceivingResponsePacket() != null) {
            return null;
        }

        setReceivingResponsePacket(new SocketResponsePacket());

        new Thread(new Runnable() {
            @Override
            public void run() {
                if (!isConnected()) {
                    return;
                }

                __i__onReceivePacketBegin(getReceivingResponsePacket());
                try {
                    byte[] data = getSocketInputReader().readToLength(length);
                    getReceivingResponsePacket().setData(data);
                    if (getSocketConfigure().getCharsetName() != null) {
                        getReceivingResponsePacket().buildStringWithCharsetName(getSocketConfigure().getCharsetName());
                    }
                    __i__onReceivePacketEnd(getReceivingResponsePacket());
                    __i__onReceiveResponse(getReceivingResponsePacket());
                } catch (IOException e) {
                    e.printStackTrace();

                    if (getReceivingResponsePacket() != null) {
                        __i__onReceivePacketCancel(getReceivingResponsePacket());
                        setReceivingResponsePacket(null);
                    }
                }
            }
        }).start();

        return getReceivingResponsePacket();
    }

    public SocketResponsePacket readDataToData(final byte[] data) {
        return readDataToData(data, true);
    }

    /**
     * 读取到与指定字节相同的字节序列后回调数据包
     *
     * @param data 指定字节序列
     * @param includeData 是否在回调的数据包中包含指定的字节序列
     * @return 将要读取的数据包实例
     */
    public SocketResponsePacket readDataToData(final byte[] data, final boolean includeData) {
        if (!isConnected()) {
            return null;
        }

        if (getSocketConfigure().getSocketPacketHelper().getReadStrategy() != SocketPacketHelper.ReadStrategy.Manually) {
            return null;
        }

        if (getReceivingResponsePacket() != null) {
            return null;
        }

        setReceivingResponsePacket(new SocketResponsePacket());

        new Thread(new Runnable() {
            @Override
            public void run() {
                __i__onReceivePacketBegin(getReceivingResponsePacket());
                try {
                    byte[] result = getSocketInputReader().readToData(data, includeData);
                    getReceivingResponsePacket().setData(result);
                    if (getSocketConfigure().getCharsetName() != null) {
                        getReceivingResponsePacket().buildStringWithCharsetName(getSocketConfigure().getCharsetName());
                    }
                    __i__onReceivePacketEnd(getReceivingResponsePacket());
                    __i__onReceiveResponse(getReceivingResponsePacket());
                } catch (IOException e) {
                    e.printStackTrace();

                    if (getReceivingResponsePacket() != null) {
                        __i__onReceivePacketCancel(getReceivingResponsePacket());
                        setReceivingResponsePacket(null);
                    }
                }
            }
        }).start();

        return getReceivingResponsePacket();
    }

    /**
     * 注册监听回调
     * @param delegate 回调接收者
     */
    public SocketClient registerSocketClientDelegate(SocketClientDelegate delegate) {
        if (!getSocketClientDelegates().contains(delegate)) {
            getSocketClientDelegates().add(delegate);
        }
        return this;
    }

    /**
     * 取消注册监听回调
     * @param delegate 回调接收者
     */
    public SocketClient removeSocketClientDelegate(SocketClientDelegate delegate) {
        getSocketClientDelegates().remove(delegate);
        return this;
    }

    /**
     * 注册信息发送回调
     * @param delegate 回调接收者
     */
    public SocketClient registerSocketClientSendingDelegate(SocketClientSendingDelegate delegate) {
        if (!getSocketClientSendingDelegates().contains(delegate)) {
            getSocketClientSendingDelegates().add(delegate);
        }
        return this;
    }

    /**
     * 取消注册信息发送回调
     * @param delegate 回调接收者
     */
    public SocketClient removeSocketClientSendingDelegate(SocketClientSendingDelegate delegate) {
        getSocketClientSendingDelegates().remove(delegate);
        return this;
    }

    /**
     * 注册信息接收回调
     * @param delegate 回调接收者
     */
    public SocketClient registerSocketClientReceiveDelegate(SocketClientReceivingDelegate delegate) {
        if (!getSocketClientReceivingDelegates().contains(delegate)) {
            getSocketClientReceivingDelegates().add(delegate);
        }
        return this;
    }

    /**
     * 取消注册信息接收回调
     * @param delegate 回调接收者
     */
    public SocketClient removeSocketClientReceiveDelegate(SocketClientReceivingDelegate delegate) {
        getSocketClientReceivingDelegates().remove(delegate);
        return this;
    }

    public SocketClientAddress getAddress() {
        return getSocketConfigure().getAddress();
    }

    /**
     * 设置默认的编码格式
     * 为null表示不自动转换data到string
     */
    public void setCharsetName(String charsetName) {
        getSocketConfigure().setCharsetName(charsetName);
    }


    public SocketPacketHelper getSocketPacketHelper() {
        return getSocketConfigure().getSocketPacketHelper();
    }


    public SocketHeartBeatHelper getHeartBeatHelper() {
        return getSocketConfigure().getHeartBeatHelper();
    }

    private Socket runningSocket;

    public Socket getRunningSocket() {
        if (this.runningSocket == null) {
            this.runningSocket = new Socket();
        }
        return this.runningSocket;
    }

    protected SocketClient setRunningSocket(Socket socket) {
        this.runningSocket = socket;
        return this;
    }

    private SocketInputReader socketInputReader;

    protected SocketClient setSocketInputReader(SocketInputReader socketInputReader) {
        this.socketInputReader = socketInputReader;
        return this;
    }

    protected SocketInputReader getSocketInputReader() throws IOException {
        if (this.socketInputReader == null) {
            this.socketInputReader = new SocketInputReader(getRunningSocket().getInputStream());
        }
        return this.socketInputReader;
    }

    protected synchronized SocketConfigure getSocketConfigure() {
        return this.socketConfigure;
    }


    private synchronized SocketClient updateState(State state) {
        this.state = state;
        return this;
    }

    public State getState() {
        if (this.state == null) {
            return State.Disconnected;
        }
        return this.state;
    }

    public enum State {
        Disconnected, Connecting, Connected
    }

    private boolean disconnecting;

    protected SocketClient setDisconnecting(boolean disconnecting) {
        this.disconnecting = disconnecting;
        return this;
    }

    public boolean isDisconnecting() {
        return this.disconnecting;
    }

    private LinkedBlockingQueue<SocketPacket> sendingPacketQueue;

    protected LinkedBlockingQueue<SocketPacket> getSendingPacketQueue() {
        if (sendingPacketQueue == null) {
            sendingPacketQueue = new LinkedBlockingQueue<SocketPacket>();
        }
        return sendingPacketQueue;
    }

    private synchronized SocketPacket getSendPacket() throws InterruptedException {
        return getSendingPacketQueue().take();
    }

    /**
     * 计时器
     */
    private CountDownTimer hearBeatCountDownTimer;

    protected CountDownTimer getHearBeatCountDownTimer() {
        if (this.hearBeatCountDownTimer == null) {
            this.hearBeatCountDownTimer = new CountDownTimer(Long.MAX_VALUE, 1000L) {
                @Override
                public void onTick(long millisUntilFinished) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            __i__onTimeTick();
                        }
                    }).start();
                }

                @Override
                public void onFinish() {
                    if (isConnected()) {
                        this.start();
                    }
                }
            };

        }
        return this.hearBeatCountDownTimer;
    }

    /**
     * 记录上次发送心跳包的时间
     */
    private long lastSendHeartBeatMessageTime;

    private SocketClient setLastSendHeartBeatMessageTime(long lastSendHeartBeatMessageTime) {
        this.lastSendHeartBeatMessageTime = lastSendHeartBeatMessageTime;
        return this;
    }

    private long getLastSendHeartBeatMessageTime() {
        return this.lastSendHeartBeatMessageTime;
    }

    /**
     * 记录上次接收到消息的时间
     */
    private long lastReceiveMessageTime;

    private SocketClient setLastReceiveMessageTime(long lastReceiveMessageTime) {
        this.lastReceiveMessageTime = lastReceiveMessageTime;
        return this;
    }

    private long getLastReceiveMessageTime() {
        return this.lastReceiveMessageTime;
    }

    /**
     * 记录上次发送数据片段的时间
     * 仅在每个发送包开始发送时计时，结束后重置计时
     * NoSendingTime 表示当前没有在发送数据
     */
    private final static long NoSendingTime = -1;
    private long lastSendMessageTime = NoSendingTime;

    private SocketClient setLastSendMessageTime(long lastSendMessageTime) {
        this.lastSendMessageTime = lastSendMessageTime;
        return this;
    }

    private long getLastSendMessageTime() {
        return this.lastSendMessageTime;
    }

    private SocketPacket sendingPacket;

    private SocketClient setSendingPacket(SocketPacket sendingPacket) {
        this.sendingPacket = sendingPacket;
        return this;
    }

    private SocketPacket getSendingPacket() {
        return this.sendingPacket;
    }

    private SocketResponsePacket receivingResponsePacket;

    protected SocketClient setReceivingResponsePacket(SocketResponsePacket receivingResponsePacket) {
        this.receivingResponsePacket = receivingResponsePacket;
        return this;
    }

    protected SocketResponsePacket getReceivingResponsePacket() {
        return this.receivingResponsePacket;
    }

    private long lastReceiveProgressCallbackTime;

    protected SocketClient setLastReceiveProgressCallbackTime(long lastReceiveProgressCallbackTime) {
        this.lastReceiveProgressCallbackTime = lastReceiveProgressCallbackTime;
        return this;
    }

    protected long getLastReceiveProgressCallbackTime() {
        return this.lastReceiveProgressCallbackTime;
    }

    private ConnectionThread connectionThread;

    protected SocketClient setConnectionThread(ConnectionThread connectionThread) {
        this.connectionThread = connectionThread;
        return this;
    }

    protected ConnectionThread getConnectionThread() {
        if (this.connectionThread == null) {
            this.connectionThread = new ConnectionThread();
        }
        return this.connectionThread;
    }

    private DisconnectionThread disconnectionThread;

    protected SocketClient setDisconnectionThread(DisconnectionThread disconnectionThread) {
        this.disconnectionThread = disconnectionThread;
        return this;
    }

    private DisconnectionThread getDisconnectionThread() {
        if (this.disconnectionThread == null) {
            this.disconnectionThread = new DisconnectionThread();
        }
        return this.disconnectionThread;
    }

    private SendThread sendThread;

    private SocketClient setSendThread(SendThread sendThread) {
        this.sendThread = sendThread;
        return this;
    }

    private SendThread getSendThread() {
        if (this.sendThread == null) {
            this.sendThread = new SendThread();
        }
        return this.sendThread;
    }

    private ReceiveThread receiveThread;

    private SocketClient setReceiveThread(ReceiveThread receiveThread) {
        this.receiveThread = receiveThread;
        return this;
    }

    private ReceiveThread getReceiveThread() {
        if (this.receiveThread == null) {
            this.receiveThread = new ReceiveThread();
        }
        return this.receiveThread;
    }

    private ArrayList<SocketClientDelegate> socketClientDelegates;

    private ArrayList<SocketClientDelegate> getSocketClientDelegates() {
        if (this.socketClientDelegates == null) {
            this.socketClientDelegates = new ArrayList<SocketClientDelegate>();
        }
        return this.socketClientDelegates;
    }

    private ArrayList<SocketClientSendingDelegate> socketClientSendingDelegates;

    private ArrayList<SocketClientSendingDelegate> getSocketClientSendingDelegates() {
        if (this.socketClientSendingDelegates == null) {
            this.socketClientSendingDelegates = new ArrayList<>();
        }
        return this.socketClientSendingDelegates;
    }

    private ArrayList<SocketClientReceivingDelegate> socketClientReceivingDelegates;

    private ArrayList<SocketClientReceivingDelegate> getSocketClientReceivingDelegates() {
        if (this.socketClientReceivingDelegates == null) {
            this.socketClientReceivingDelegates = new ArrayList<>();
        }
        return this.socketClientReceivingDelegates;
    }

    private UIHandler uiHandler = new UIHandler();

    private UIHandler getUiHandler() {
        return this.uiHandler;
    }

    private static class UIHandler extends Handler {
        private UIHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    }

    /* Overrides */


    /* Delegates */


    /* Protected Methods */
    @CallSuper
    protected void internalOnConnected() {
        updateState(SocketClient.State.Connected);

        setLastSendHeartBeatMessageTime(System.currentTimeMillis());
        setLastReceiveMessageTime(System.currentTimeMillis());
        setLastSendMessageTime(NoSendingTime);

        setSendingPacket(null);
        setReceivingResponsePacket(null);

        __i__onConnected();
    }

    /* Private Methods */
    private void __i__enqueueNewPacket(final SocketPacket packet) {
        if (!isConnected()) {
            return;
        }

        synchronized (getSendingPacketQueue()) {
            try {
                getSendingPacketQueue().put(packet);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void __i__sendHeartBeat() {
        if (!isConnected()) {
            return;
        }

        if (getSocketConfigure() == null
                || getSocketConfigure().getHeartBeatHelper() == null
                || !getSocketConfigure().getHeartBeatHelper().isSendHeartBeatEnabled()) {
            return;
        }

        final SocketPacket packet = new SocketPacket(getSocketConfigure().getHeartBeatHelper().getSendData(), true);
        new Thread(new Runnable() {
            @Override
            public void run() {
                __i__enqueueNewPacket(packet);
            }
        }).start();
    }

    private void __i__onConnected() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            getUiHandler().post(new Runnable() {
                @Override
                public void run() {
                    __i__onConnected();
                }
            });
            return;
        }

        ArrayList<SocketClientDelegate> delegatesCopy =
                (ArrayList<SocketClientDelegate>) getSocketClientDelegates().clone();
        int count = delegatesCopy.size();
        for (int i = 0; i < count; ++i) {
            delegatesCopy.get(i).onConnected(this);
        }

        getSendThread().start();
        getReceiveThread().start();
        getHearBeatCountDownTimer().start();
    }

    private void __i__onDisconnected() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            getUiHandler().post(new Runnable() {
                @Override
                public void run() {
                    __i__onDisconnected();
                }
            });
            return;
        }

        ArrayList<SocketClientDelegate> delegatesCopy =
                (ArrayList<SocketClientDelegate>) getSocketClientDelegates().clone();
        int count = delegatesCopy.size();
        for (int i = 0; i < count; ++i) {
            delegatesCopy.get(i).onDisconnected(this);
        }
    }

    private void __i__onReceiveResponse(@NonNull final SocketResponsePacket responsePacket) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            getUiHandler().post(new Runnable() {
                @Override
                public void run() {
                    __i__onReceiveResponse(responsePacket);
                }
            });
            return;
        }

        setLastReceiveMessageTime(System.currentTimeMillis());

        if (getSocketClientDelegates().size() > 0) {
            ArrayList<SocketClientDelegate> delegatesCopy =
                    (ArrayList<SocketClientDelegate>) getSocketClientDelegates().clone();
            int count = delegatesCopy.size();
            for (int i = 0; i < count; ++i) {
                delegatesCopy.get(i).onResponse(this, responsePacket);
            }
        }
    }

    private void __i__onSendPacketBegin(final SocketPacket packet) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            getUiHandler().post(new Runnable() {
                @Override
                public void run() {
                    __i__onSendPacketBegin(packet);
                }
            });
            return;
        }

        if (getSocketClientDelegates().size() > 0) {
            ArrayList<SocketClientSendingDelegate> delegatesCopy =
                    (ArrayList<SocketClientSendingDelegate>) getSocketClientSendingDelegates().clone();
            int count = delegatesCopy.size();
            for (int i = 0; i < count; ++i) {
                delegatesCopy.get(i).onSendPacketBegin(this, packet);
            }
        }
    }

    private void __i__onSendPacketEnd(final SocketPacket packet) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            getUiHandler().post(new Runnable() {
                @Override
                public void run() {
                    __i__onSendPacketEnd(packet);
                }
            });
            return;
        }

        if (getSocketClientDelegates().size() > 0) {
            ArrayList<SocketClientSendingDelegate> delegatesCopy =
                    (ArrayList<SocketClientSendingDelegate>) getSocketClientSendingDelegates().clone();
            int count = delegatesCopy.size();
            for (int i = 0; i < count; ++i) {
                delegatesCopy.get(i).onSendPacketEnd(this, packet);
            }
        }
    }

    private void __i__onSendPacketCancel(final SocketPacket packet) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            getUiHandler().post(new Runnable() {
                @Override
                public void run() {
                    __i__onSendPacketCancel(packet);
                }
            });
            return;
        }

        if (getSocketClientDelegates().size() > 0) {
            ArrayList<SocketClientSendingDelegate> delegatesCopy =
                    (ArrayList<SocketClientSendingDelegate>) getSocketClientSendingDelegates().clone();
            int count = delegatesCopy.size();
            for (int i = 0; i < count; ++i) {
                delegatesCopy.get(i).onSendPacketCancel(this, packet);
            }
        }
    }

    private void __i__onSendingPacketInProgress(final SocketPacket packet, final int sendedLength, final int headerLength, final int packetLengthDataLength, final int dataLength, final int trailerLength) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            getUiHandler().post(new Runnable() {
                @Override
                public void run() {
                    __i__onSendingPacketInProgress(packet, sendedLength, headerLength, packetLengthDataLength, dataLength, trailerLength);
                }
            });
            return;
        }

        float progress = sendedLength / (float) (headerLength + packetLengthDataLength + dataLength + trailerLength);

        if (getSocketClientDelegates().size() > 0) {
            ArrayList<SocketClientSendingDelegate> delegatesCopy =
                    (ArrayList<SocketClientSendingDelegate>) getSocketClientSendingDelegates().clone();
            int count = delegatesCopy.size();
            for (int i = 0; i < count; ++i) {
                delegatesCopy.get(i).onSendingPacketInProgress(this, packet, progress, sendedLength);
            }
        }
    }

    private void __i__onReceivePacketBegin(final SocketResponsePacket packet) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            getUiHandler().post(new Runnable() {
                @Override
                public void run() {
                    __i__onReceivePacketBegin(packet);
                }
            });
            return;
        }

        if (getSocketClientReceivingDelegates().size() > 0) {
            ArrayList<SocketClientReceivingDelegate> delegatesCopy =
                    (ArrayList<SocketClientReceivingDelegate>) getSocketClientReceivingDelegates().clone();
            int count = delegatesCopy.size();
            for (int i = 0; i < count; ++i) {
                delegatesCopy.get(i).onReceivePacketBegin(this, packet);
            }
        }
    }

    private void __i__onReceivePacketEnd(final SocketResponsePacket packet) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            getUiHandler().post(new Runnable() {
                @Override
                public void run() {
                    __i__onReceivePacketEnd(packet);
                }
            });
            return;
        }

        if (getSocketClientReceivingDelegates().size() > 0) {
            ArrayList<SocketClientReceivingDelegate> delegatesCopy =
                    (ArrayList<SocketClientReceivingDelegate>) getSocketClientReceivingDelegates().clone();
            int count = delegatesCopy.size();
            for (int i = 0; i < count; ++i) {
                delegatesCopy.get(i).onReceivePacketEnd(this, packet);
            }
        }
    }

    private void __i__onReceivePacketCancel(final SocketResponsePacket packet) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            getUiHandler().post(new Runnable() {
                @Override
                public void run() {
                    __i__onReceivePacketCancel(packet);
                }
            });
            return;
        }

        if (getSocketClientReceivingDelegates().size() > 0) {
            ArrayList<SocketClientReceivingDelegate> delegatesCopy =
                    (ArrayList<SocketClientReceivingDelegate>) getSocketClientReceivingDelegates().clone();
            int count = delegatesCopy.size();
            for (int i = 0; i < count; ++i) {
                delegatesCopy.get(i).onReceivePacketCancel(this, packet);
            }
        }
    }

    private void __i__onReceivingPacketInProgress(final SocketResponsePacket packet, final int receivedLength, final int headerLength, final int packetLengthDataLength, final int dataLength, final int trailerLength) {

        long currentTime = System.currentTimeMillis();
        if (currentTime - getLastReceiveProgressCallbackTime() < (1000 / 24)) {
            return;
        }

        if (Looper.myLooper() != Looper.getMainLooper()) {
            getUiHandler().post(new Runnable() {
                @Override
                public void run() {
                    __i__onReceivingPacketInProgress(packet, receivedLength, headerLength, packetLengthDataLength, dataLength, trailerLength);
                }
            });
            return;
        }

        float progress = receivedLength / (float) (headerLength + packetLengthDataLength + dataLength + trailerLength);

        if (getSocketClientReceivingDelegates().size() > 0) {
            ArrayList<SocketClientReceivingDelegate> delegatesCopy =
                    (ArrayList<SocketClientReceivingDelegate>) getSocketClientReceivingDelegates().clone();
            int count = delegatesCopy.size();
            for (int i = 0; i < count; ++i) {
                delegatesCopy.get(i).onReceivingPacketInProgress(this, packet, progress, receivedLength);
            }
        }

        setLastReceiveProgressCallbackTime(System.currentTimeMillis());
    }

    private void __i__onTimeTick() {
        if (!isConnected()) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        if (getSocketConfigure().getHeartBeatHelper().isSendHeartBeatEnabled()) {
            if (currentTime - getLastSendHeartBeatMessageTime() >= getSocketConfigure().getHeartBeatHelper().getHeartBeatInterval()) {
                __i__sendHeartBeat();
                setLastSendHeartBeatMessageTime(currentTime);
            }
        }

        if (getSocketConfigure().getSocketPacketHelper().isReceiveTimeoutEnabled()) {
            if (currentTime - getLastReceiveMessageTime() >= getSocketConfigure().getSocketPacketHelper().getReceiveTimeout()) {
                disconnect();
            }
        }

        if (getSocketConfigure().getSocketPacketHelper().isSendTimeoutEnabled()
                && getLastSendMessageTime() != NoSendingTime) {
            if (currentTime - getLastSendMessageTime() >= getSocketConfigure().getSocketPacketHelper().getSendTimeout()) {
                disconnect();
            }
        }
    }

    /* Enums */

    /* Inner Classes */
    private class ConnectionThread extends Thread {
        @Override
        public void run() {
            super.run();

            try {
                SocketClientAddress address = getAddress();

                if (Thread.interrupted()) {
                    return;
                }

                getRunningSocket().connect(address.getInetSocketAddress(), address.getConnectionTimeout());

                if (Thread.interrupted()) {
                    return;
                }

                updateState(SocketClient.State.Connected);

                setLastSendHeartBeatMessageTime(System.currentTimeMillis());
                setLastReceiveMessageTime(System.currentTimeMillis());
                setLastSendMessageTime(NoSendingTime);

                setSendingPacket(null);
                setReceivingResponsePacket(null);

                setConnectionThread(null);

                __i__onConnected();
            } catch (IOException e) {
                e.printStackTrace();

                disconnect();
            }
        }
    }

    private class DisconnectionThread extends Thread {
        @Override
        public void run() {
            super.run();

            if (connectionThread != null) {
                getConnectionThread().interrupt();
                setConnectionThread(null);
            }

            if (!getRunningSocket().isClosed()
                    || isConnecting()) {
                try {
                    getRunningSocket().getOutputStream().close();
                    getRunningSocket().getInputStream().close();
                } catch (IOException e) {
//                e.printStackTrace();
                } finally {
                    try {
                        getRunningSocket().close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    setRunningSocket(null);
                }
            }

            if (sendThread != null) {
                getSendThread().interrupt();
                setSendThread(null);
            }
            if (receiveThread != null) {
                getReceiveThread().interrupt();
                setReceiveThread(null);
            }

            setDisconnecting(false);
            updateState(SocketClient.State.Disconnected);
            setSocketInputReader(null);

            if (hearBeatCountDownTimer != null) {
                hearBeatCountDownTimer.cancel();
            }

            if (getSendingPacket() != null) {
                __i__onSendPacketCancel(getSendingPacket());
                setSendingPacket(null);
            }

            SocketPacket packet;
            while ((packet = getSendingPacketQueue().poll()) != null) {
                __i__onSendPacketCancel(packet);
            }

            if (getReceivingResponsePacket() != null) {
                __i__onReceivePacketCancel(getReceivingResponsePacket());
                setReceivingResponsePacket(null);
            }

            setDisconnectionThread(null);

            __i__onDisconnected();
        }
    }

    private class SendThread extends Thread {
        public SendThread() {
        }

        @Override
        public void run() {
            super.run();

            SocketPacket packet;
            try {
                while (isConnected()
                        && !Thread.interrupted()
                        && (packet = getSendPacket()) != null) {
                    setSendingPacket(packet);
                    setLastSendMessageTime(System.currentTimeMillis());

                    if (packet.getData() == null
                            && packet.getMessage() != null) {
                        if (getSocketConfigure().getCharsetName() == null) {
                            throw new IllegalArgumentException("we need string charset to send string type message");
                        } else {
                            packet.buildDataWithCharsetName(getSocketConfigure().getCharsetName());
                        }
                    }

                    if (packet.getData() == null) {
                        __i__onSendPacketCancel(packet);
                        setSendingPacket(null);
                        continue;
                    }

                    byte[] headerData = getSocketConfigure().getSocketPacketHelper().getSendHeaderData();
                    int headerDataLength = headerData == null ? 0 : headerData.length;

                    byte[] trailerData = getSocketConfigure().getSocketPacketHelper().getSendTrailerData();
                    int trailerDataLength = trailerData == null ? 0 : trailerData.length;

                    byte[] packetLengthData = getSocketConfigure().getSocketPacketHelper().getSendPacketLengthData(packet.getData().length + trailerDataLength);
                    int packetLengthDataLength = packetLengthData == null ? 0 : packetLengthData.length;

                    int sendedPacketLength = 0;

                    packet.setHeaderData(headerData);
                    packet.setTrailerData(trailerData);
                    packet.setPacketLengthData(packetLengthData);

                    if (headerDataLength + packetLengthDataLength + packet.getData().length + trailerDataLength <= 0) {
                        __i__onSendPacketCancel(packet);
                        setSendingPacket(null);
                        continue;
                    }

                    __i__onSendPacketBegin(packet);
                    __i__onSendingPacketInProgress(packet, sendedPacketLength, headerDataLength, packetLengthDataLength, packet.getData().length, trailerDataLength);

                    try {
                        if (headerDataLength > 0) {
                            getRunningSocket().getOutputStream().write(headerData);
                            getRunningSocket().getOutputStream().flush();
                            setLastSendMessageTime(System.currentTimeMillis());

                            sendedPacketLength += headerDataLength;
                            __i__onSendingPacketInProgress(packet, sendedPacketLength, headerDataLength, packetLengthDataLength, packet.getData().length, trailerDataLength);
                        }

                        if (packetLengthDataLength > 0) {
                            getRunningSocket().getOutputStream().write(packetLengthData);
                            getRunningSocket().getOutputStream().flush();
                            setLastSendMessageTime(System.currentTimeMillis());

                            sendedPacketLength += packetLengthDataLength;
                            __i__onSendingPacketInProgress(packet, sendedPacketLength, headerDataLength, packetLengthDataLength, packet.getData().length, trailerDataLength);
                        }

                        if (packet.getData().length > 0) {
                            int segmentLength = getRunningSocket().getSendBufferSize();
                            if (getSocketConfigure().getSocketPacketHelper().isSendSegmentEnabled()) {
                                segmentLength = Math.min(segmentLength, getSocketConfigure().getSocketPacketHelper().getSendSegmentLength());
                            }

                            int offset = 0;

                            while (offset < packet.getData().length) {
                                int end = offset + segmentLength;
                                end = Math.min(end, packet.getData().length);
                                getRunningSocket().getOutputStream().write(packet.getData(), offset, end - offset);
                                getRunningSocket().getOutputStream().flush();
                                setLastSendMessageTime(System.currentTimeMillis());

                                sendedPacketLength += end - offset;

                                __i__onSendingPacketInProgress(packet, sendedPacketLength, headerDataLength, packetLengthDataLength, packet.getData().length, trailerDataLength);

                                offset = end;
                            }
                        }

                        if (trailerDataLength > 0) {
                            getRunningSocket().getOutputStream().write(trailerData);
                            getRunningSocket().getOutputStream().flush();
                            setLastSendMessageTime(System.currentTimeMillis());

                            sendedPacketLength += trailerDataLength;

                            __i__onSendingPacketInProgress(packet, sendedPacketLength, headerDataLength, packetLengthDataLength, packet.getData().length, trailerDataLength);
                        }

                        __i__onSendPacketEnd(packet);
                        setSendingPacket(null);

                        setLastSendMessageTime(NoSendingTime);
                    } catch (IOException e) {
                        e.printStackTrace();

                        if (getSendingPacket() != null) {
                            __i__onSendPacketCancel(getSendingPacket());
                            setSendingPacket(null);
                        }
                    }
                }
            } catch (InterruptedException e) {
//                e.printStackTrace();
                if (getSendingPacket() != null) {
                    __i__onSendPacketCancel(getSendingPacket());
                    setSendingPacket(null);
                }
            }
        }
    }

    private class ReceiveThread extends Thread {
        @Override
        public void run() {
            super.run();

            if (getSocketConfigure().getSocketPacketHelper().getReadStrategy() == SocketPacketHelper.ReadStrategy.Manually) {
                return;
            }

            try {
                while (isConnected()
                        && getSocketInputReader() != null
                        && !Thread.interrupted()) {
                    SocketResponsePacket packet = new SocketResponsePacket();
                    setReceivingResponsePacket(packet);

                    byte[] headerData = getSocketConfigure().getSocketPacketHelper().getReceiveHeaderData();
                    int headerDataLength = headerData == null ? 0 : headerData.length;

                    byte[] trailerData = getSocketConfigure().getSocketPacketHelper().getReceiveTrailerData();
                    int trailerDataLength = trailerData == null ? 0 : trailerData.length;

                    int packetLengthDataLength = getSocketConfigure().getSocketPacketHelper().getReceivePacketLengthDataLength();

                    int dataLength = 0;
                    int receivedPacketLength = 0;

                    __i__onReceivePacketBegin(packet);

                    if (headerDataLength > 0) {
                        byte[] data = getSocketInputReader().readToData(headerData, true);
                        setLastReceiveMessageTime(System.currentTimeMillis());
                        packet.setHeaderData(data);

                        receivedPacketLength += headerDataLength;
                    }

                    if (getSocketConfigure().getSocketPacketHelper().getReadStrategy() == SocketPacketHelper.ReadStrategy.AutoReadByLength) {
                        if (packetLengthDataLength < 0) {
                            __i__onReceivePacketCancel(packet);
                            setReceivingResponsePacket(null);
                        } else if (packetLengthDataLength == 0) {
                            __i__onReceivePacketEnd(packet);
                            setReceivingResponsePacket(null);
                        }

                        byte[] data = getSocketInputReader().readToLength(packetLengthDataLength);
                        setLastReceiveMessageTime(System.currentTimeMillis());
                        packet.setPacketLengthData(data);

                        receivedPacketLength += packetLengthDataLength;

                        int bodyTrailerLength = getSocketConfigure().getSocketPacketHelper().getReceivePacketDataLength(data);

                        dataLength = bodyTrailerLength - trailerDataLength;

                        if (dataLength > 0) {
                            int segmentLength = getRunningSocket().getReceiveBufferSize();
                            if (getSocketConfigure().getSocketPacketHelper().isReceiveSegmentEnabled()) {
                                segmentLength = Math.min(segmentLength, getSocketConfigure().getSocketPacketHelper().getReceiveSegmentLength());
                            }
                            int offset = 0;
                            while (offset < dataLength) {
                                int end = offset + segmentLength;
                                end = Math.min(end, dataLength);
                                data = getSocketInputReader().readToLength(end - offset);
                                setLastReceiveMessageTime(System.currentTimeMillis());

                                if (packet.getData() == null) {
                                    packet.setData(data);
                                } else {
                                    byte[] mergedData = new byte[packet.getData().length + data.length];

                                    System.arraycopy(packet.getData(), 0, mergedData, 0, packet.getData().length);
                                    System.arraycopy(data, 0, mergedData, packet.getData().length, data.length);

                                    packet.setData(mergedData);
                                }

                                receivedPacketLength += end - offset;

                                __i__onReceivingPacketInProgress(packet, receivedPacketLength, headerDataLength, packetLengthDataLength, dataLength, trailerDataLength);

                                offset = end;
                            }
                        } else if (dataLength < 0) {
                            __i__onReceivePacketCancel(packet);
                            setReceivingResponsePacket(null);
                        }

                        if (trailerDataLength > 0) {
                            data = getSocketInputReader().readToLength(trailerDataLength);
                            setLastReceiveMessageTime(System.currentTimeMillis());
                            packet.setTrailerData(data);

                            receivedPacketLength += trailerDataLength;

                            __i__onReceivingPacketInProgress(packet, receivedPacketLength, headerDataLength, packetLengthDataLength, dataLength, trailerDataLength);
                        }
                    } else if (getSocketConfigure().getSocketPacketHelper().getReadStrategy() == SocketPacketHelper.ReadStrategy.AutoReadToTrailer) {
                        if (trailerDataLength > 0) {
                            byte[] data = getSocketInputReader().readToData(trailerData, false);
                            setLastReceiveMessageTime(System.currentTimeMillis());
                            packet.setData(data);
                            packet.setTrailerData(trailerData);

                            receivedPacketLength += data.length;
                        } else {
                            __i__onReceivePacketCancel(packet);
                            setReceivingResponsePacket(null);
                        }
                    }

                    packet.setHeartBeat(getSocketConfigure().getHeartBeatHelper().isReceiveHeartBeatPacket(packet));

                    if (getSocketConfigure().getCharsetName() != null) {
                        packet.buildStringWithCharsetName(getSocketConfigure().getCharsetName());
                    }

                    __i__onReceivePacketEnd(packet);
                    __i__onReceiveResponse(packet);
                    setReceivingResponsePacket(null);
                }
            } catch (Exception e) {
//                e.printStackTrace();
                disconnect();

                if (getReceivingResponsePacket() != null) {
                    __i__onReceivePacketCancel(getReceivingResponsePacket());
                    setReceivingResponsePacket(null);
                }
            }
        }
    }

}