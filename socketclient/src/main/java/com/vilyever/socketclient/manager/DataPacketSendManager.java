package com.vilyever.socketclient.manager;

import com.vilyever.socketclient.api.IClientAssistant;
import com.vilyever.socketclient.api.ISocketClient;
import com.vilyever.socketclient.helper.SocketPacket;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

public class DataPacketSendManager implements IDataPacketManager {
    private final BlockingQueue<SocketPacket> sendingPacketQueue = new LinkedBlockingQueue<>();

    private SocketPacket mSendingPacket;

    private ISocketClient mSocketClient;

    public DataPacketSendManager(ISocketClient socketClient) {
        this.mSocketClient = socketClient;
    }

    public SocketPacket takePacket() throws InterruptedException {
        return sendingPacketQueue.take();
    }

    public void updateSendingPacket(SocketPacket packet) {
        this.mSendingPacket = packet;
    }

    public void clearSendingPacket() {
        this.mSendingPacket = null;
    }

    public void reset() {
        clearSendingPacket();
        sendingPacketQueue.clear();
    }

    public boolean isExistSendingPacket() {
        return mSendingPacket != null;
    }

    public void enqueue(IClientAssistant clientAssistant, SocketPacket packet) {
        if (!mSocketClient.isConnected()) {
            return;
        }

        clientAssistant.executeTask(() -> {
            synchronized (sendingPacketQueue) {
                try {
                    sendingPacketQueue.put(packet);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

    }

    public SocketPacket getSendingPacket() {
        return mSendingPacket;
    }
}
