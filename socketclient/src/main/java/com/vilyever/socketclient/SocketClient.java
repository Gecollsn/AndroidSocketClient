package com.vilyever.socketclient;

import android.util.Log;

import com.vilyever.socketclient.api.IClientAssistant;
import com.vilyever.socketclient.api.ISocketClient;
import com.vilyever.socketclient.api.ITimeRecorder;
import com.vilyever.socketclient.helper.ClientReceivingEvent;
import com.vilyever.socketclient.helper.ClientSendingEvent;
import com.vilyever.socketclient.helper.ClientStatusEvent;
import com.vilyever.socketclient.helper.SocketClientAddress;
import com.vilyever.socketclient.helper.SocketConfigure;
import com.vilyever.socketclient.helper.SocketHeartBeatHelper;
import com.vilyever.socketclient.helper.SocketPacket;
import com.vilyever.socketclient.helper.SocketPacketHelper;
import com.vilyever.socketclient.manager.DataPacketReceiveManager;
import com.vilyever.socketclient.manager.DataPacketSendManager;
import com.vilyever.socketclient.manager.SocketEventManager;
import com.vilyever.socketclient.runner.ConnectSocketTask;
import com.vilyever.socketclient.runner.DisconnectSocketTask;
import com.vilyever.socketclient.runner.HeartbeatTask;
import com.vilyever.socketclient.runner.ISocketTask;
import com.vilyever.socketclient.runner.ReceivePacketTask;
import com.vilyever.socketclient.runner.SendPacketTask;

import java.io.IOException;
import java.net.Socket;
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
public class SocketClient implements ISocketClient {
    private static final String TAG = SocketClient.class.getSimpleName();
    private SocketConfigure socketConfigure = new SocketConfigure();
    private State state = State.Disconnected;
    private Socket mActiveSocket;

    private ThreadPoolExecutor executor;
    private ISocketTask mConnectTask;
    private ISocketTask mHeartbeatTask;
    private ISocketTask mSendTask;
    private ISocketTask mReceiveTask;
    private ISocketTask mDisconnectTask;
    private final DataPacketSendManager sendDataPacketManager = new DataPacketSendManager(this);
    private final DataPacketReceiveManager receiveDataPacketManager = new DataPacketReceiveManager();
    private final SocketEventManager mSocketEventManager = new SocketEventManager(this);

    private IClientAssistant mClientAssistant = new IClientAssistant() {
        @Override
        public void updateState(State state) {
            SocketClient.this.updateState(state);
        }

        @Override
        public void __i__enqueue(SocketPacket packet) {
            SocketClient.this.__i__enqueue(packet);
        }

        @Override
        public void __i__onConnected() {
            SocketClient.this.__i__onConnected();
        }

        @Override
        public DataPacketSendManager getSendDataPacketManager() {
            return sendDataPacketManager;
        }

        @Override
        public DataPacketReceiveManager getReceiveDataPacketManager() {
            return receiveDataPacketManager;
        }

        @Override
        public ITimeRecorder getTimeRecorder() {
            return (ITimeRecorder) mHeartbeatTask;
        }

        @Override
        public SocketEventManager getSocketEventManager() {
            return mSocketEventManager;
        }

        @Override
        public Socket getActiveSocket() {
            return mActiveSocket;
        }

        @Override
        public void killAllTask() {
            clearTaskState(mConnectTask);
            clearTaskState(mSendTask);
            clearTaskState(mReceiveTask);
            clearTaskState(mHeartbeatTask);

            release();
        }

        @Override
        public Socket newSocket() {
            stopSocket();
            mActiveSocket = new Socket();
            return mActiveSocket;
        }

        @Override
        public void stopSocket() {
            if (mActiveSocket != null) {
                try {
                    mActiveSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            mActiveSocket = null;
        }

        @Override
        public void executeTask(Runnable runnable) {
            SocketClient.this.executeTask(runnable);
        }
    };

    private void release() {
        mSocketEventManager.clearAll();
        sendDataPacketManager.reset();
        shutdownPool();

        Log.d(TAG, this.hashCode() + " socket client released!");
    }

    private void shutdownPool() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }

        executor = null;
    }

    private synchronized void newThreadPool() {
        shutdownPool();
        if (executor == null || executor.isShutdown()) {
            executor = new ThreadPoolExecutor(3, 10, 60, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
        }
    }

    private synchronized void executeTask(Runnable runnable) {
        if (executor != null && !executor.isShutdown()) {
            executor.execute(runnable);
        }
    }

    private synchronized void removeTask(Runnable runnable) {
        if (executor != null && !executor.isShutdown()) {
            executor.remove(runnable);
        }
    }

    public synchronized void connect() {
        if (!isDisconnected()) {
            return;
        }

        if (getAddress() == null) {
            throw new IllegalArgumentException("we need a SocketClientAddress to connect");
        }

        newThreadPool();

        getAddress().checkValidation();
        getSocketPacketHelper().checkValidation();

        updateState(State.Connecting);

        clearTaskState(mConnectTask);

        mConnectTask = new ConnectSocketTask(this, mClientAssistant);

        executeTask(mConnectTask);
    }

    private void clearTaskState(ISocketTask task) {
        if (task != null) {
            task.kill();
            removeTask(task);
        }
    }

    public synchronized void disconnect() {
        if (isDisconnected() || isDisConnecting()) {
            return;
        }

        updateState(State.Disconnecting);

        clearTaskState(mDisconnectTask);
        mDisconnectTask = new DisconnectSocketTask(this, mClientAssistant);
        executeTask(mDisconnectTask);
    }

    @Override
    public boolean isConnected() {
        return getState() == State.Connected;
    }

    @Override
    public boolean isDisconnected() {
        return getState() == State.Disconnected;
    }

    @Override
    public boolean isConnecting() {
        return getState() == State.Connecting;
    }

    @Override
    public boolean isDisConnecting() {
        return getState() == State.Disconnecting;
    }

    /**
     * 发送byte数组
     *
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
     *
     * @param message
     * @return 打包后的数据包
     */
    public SocketPacket sendMessage(String message) {
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

        __i__enqueue(packet);

        return packet;
    }

    /**
     * 注册监听回调
     *
     * @param delegate 回调接收者
     */
    public SocketClient registerSocketStatusEvent(ClientStatusEvent delegate) {
        mSocketEventManager.registerStatusEvent(delegate);
        return this;
    }

    /**
     * 取消注册监听回调
     *
     * @param delegate 回调接收者
     */
    public SocketClient unregisterSocketStatusEvent(ClientStatusEvent delegate) {
        mSocketEventManager.unregisterStatusEvent(delegate);
        return this;
    }

    /**
     * 注册信息发送回调
     *
     * @param delegate 回调接收者
     */
    public SocketClient registerDataSendingEvent(ClientSendingEvent delegate) {
        mSocketEventManager.registerSendingEvent(delegate);
        return this;
    }

    /**
     * 取消注册信息发送回调
     *
     * @param delegate 回调接收者
     */
    public SocketClient unregisterDataSendingEvent(ClientSendingEvent delegate) {
        mSocketEventManager.unregisterSendingEvent(delegate);
        return this;
    }

    /**
     * 注册信息接收回调
     *
     * @param delegate 回调接收者
     */
    public SocketClient registerDataReceivingEvent(ClientReceivingEvent delegate) {
        mSocketEventManager.registerReceivingEvent(delegate);
        return this;
    }

    /**
     * 取消注册信息接收回调
     *
     * @param delegate 回调接收者
     */
    public SocketClient unregisterDataReceivingEvent(ClientReceivingEvent delegate) {
        mSocketEventManager.unregisterReceivingEvent(delegate);
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

    @Override
    public String getCharsetName() {
        return getSocketConfigure().getCharsetName();
    }

    public SocketPacketHelper getSocketPacketHelper() {
        return getSocketConfigure().getSocketPacketHelper();
    }


    public SocketHeartBeatHelper getHeartBeatHelper() {
        return getSocketConfigure().getHeartBeatHelper();
    }

    protected SocketClient setActiveSocket(Socket socket) {
        this.mActiveSocket = socket;
        return this;
    }

    protected SocketConfigure getSocketConfigure() {
        return this.socketConfigure;
    }


    private synchronized SocketClient updateState(State state) {
        this.state = state;
        return this;
    }

    private State getState() {
        return this.state;
    }

    public enum State {
        Disconnecting, Disconnected, Connecting, Connected
    }

    private void __i__enqueue(final SocketPacket packet) {
        sendDataPacketManager.enqueue(mClientAssistant, packet);
    }

    private synchronized void __i__onConnected() {
        if (mSocketEventManager.shouldConvertToMainThread(this::__i__onConnected)) {
            return;
        }

        if (isConnected()) return;

        updateState(State.Connected);

        sendDataPacketManager.reset();
        receiveDataPacketManager.reset();


        mSocketEventManager.noticeSocketConnected();

        clearTaskState(mHeartbeatTask);
        mHeartbeatTask = new HeartbeatTask(this, mClientAssistant);
        executeTask(mHeartbeatTask);


        clearTaskState(mSendTask);
        mSendTask = new SendPacketTask(this, mClientAssistant);
        executeTask(mSendTask);

        clearTaskState(mReceiveTask);
        mReceiveTask = new ReceivePacketTask(this, mClientAssistant);
        executeTask(mReceiveTask);
    }
}