package com.vilyever.socketclient.runner;

import com.vilyever.socketclient.api.IClientAssistant;
import com.vilyever.socketclient.api.ISocketClient;
import com.vilyever.socketclient.api.ITimeRecorder;
import com.vilyever.socketclient.helper.SocketPacket;

import static com.vilyever.socketclient.api.ISocketClient.NoSendingTime;

public class HeartbeatTask extends AbsSocketTask implements ITimeRecorder {

    private long lastTimeSendHeartbeat;
    private long lastTimeSendMessage;
    private long lastTimeReceiveMessage;

    public HeartbeatTask(ISocketClient socketClient, IClientAssistant clientAssistant) {
        super(socketClient, clientAssistant);
        lastTimeSendHeartbeat = System.currentTimeMillis();
        lastTimeReceiveMessage = System.currentTimeMillis();
        lastTimeSendMessage = NoSendingTime;
    }

    @Override
    public void updateLastTimeSend(long sendTime) {
        this.lastTimeSendMessage = sendTime;
    }

    @Override
    public void updateLastTimeReceive(long receiveTime) {
        this.lastTimeReceiveMessage = receiveTime;
    }

    @Override
    public void run() {
        boolean isHeartbeatEnable = mSocketClient.getHeartBeatHelper().isSendHeartBeatEnabled();
        long heartbeatInterval = mSocketClient.getHeartBeatHelper().getHeartBeatInterval();

        boolean isReceiveTimeoutEnable = mSocketClient.getSocketPacketHelper().isReceiveTimeoutEnabled();
        long receiveTimeout = mSocketClient.getSocketPacketHelper().getReceiveTimeout();

        boolean isSendTimeoutEnable = mSocketClient.getSocketPacketHelper().isSendTimeoutEnabled();
        long sendTimeout = mSocketClient.getSocketPacketHelper().getSendTimeout();

        while (isRunning()) {
            if (!mSocketClient.isConnected()) {
                return;
            }

            long currentTime = System.currentTimeMillis();

            if (isHeartbeatEnable) {
                if (currentTime - lastTimeSendHeartbeat >= heartbeatInterval) {

                    byte[] heartbeatData = mSocketClient.getHeartBeatHelper().getSendData();
                    SocketPacket packet = new SocketPacket(heartbeatData, true);
                    mClientAssistant.__i__enqueue(packet);

                    lastTimeSendHeartbeat = System.currentTimeMillis();
                }
            }

            if (isReceiveTimeoutEnable) {
                if (currentTime - lastTimeReceiveMessage >= receiveTimeout) {
                    mSocketClient.disconnect();
                }
            }

            if (isSendTimeoutEnable && lastTimeSendMessage != NoSendingTime) {
                if (currentTime - lastTimeSendMessage >= sendTimeout) {
                    mSocketClient.disconnect();
                }
            }

            try {
                Thread.sleep(heartbeatInterval);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
