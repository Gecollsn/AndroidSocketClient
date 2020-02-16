package com.vilyever.socketclient.runner;

import com.vilyever.socketclient.api.IClientAssistant;
import com.vilyever.socketclient.api.ISocketClient;
import com.vilyever.socketclient.api.ITimeRecorder;
import com.vilyever.socketclient.helper.SocketPacket;
import com.vilyever.socketclient.manager.DataPacketSendManager;
import com.vilyever.socketclient.manager.SocketEventManager;

import java.net.Socket;

public class SendPacketTask extends AbsSocketTask {
    public SendPacketTask(ISocketClient socketClient, IClientAssistant clientAssistant) {
        super(socketClient, clientAssistant);
    }

    @Override
    public void run() {
        SocketPacket packet;
        DataPacketSendManager dataPacketSendManager = mClientAssistant.getSendDataPacketManager();
        ITimeRecorder timeRecorder = mClientAssistant.getTimeRecorder();
        SocketEventManager socketEventManager = mClientAssistant.getSocketEventManager();
        Socket activeSocket = mClientAssistant.getActiveSocket();
        try {
            while (mSocketClient.isConnected()
                    && isRunning()
                    && (packet = dataPacketSendManager.takePacket()) != null) {

                dataPacketSendManager.updateSendingPacket(packet);
                timeRecorder.updateLastTimeSend(System.currentTimeMillis());

                if (packet.getData() == null
                        && packet.getMessage() != null) {
                    if (mSocketClient.getCharsetName() == null) {
                        throw new IllegalArgumentException("we need string charset to send string type message");
                    } else {
                        packet.buildDataWithCharsetName(mSocketClient.getCharsetName());
                    }
                }

                if (packet.getData() == null) {
                    socketEventManager.noticeSendCancelEvent(packet);
                    dataPacketSendManager.clearSendingPacket();
                    continue;
                }

                byte[] headerData = mSocketClient.getSocketPacketHelper().getSendHeaderData();
                int headerDataLength = headerData == null ? 0 : headerData.length;

                byte[] trailerData = mSocketClient.getSocketPacketHelper().getSendTrailerData();
                int trailerDataLength = trailerData == null ? 0 : trailerData.length;

                byte[] packetLengthData = mSocketClient.getSocketPacketHelper().getSendPacketLengthData(packet.getData().length + trailerDataLength);
                int packetLengthDataLength = packetLengthData == null ? 0 : packetLengthData.length;

                int sendPacketLength = 0;

                packet.setHeaderData(headerData);
                packet.setTrailerData(trailerData);
                packet.setPacketLengthData(packetLengthData);

                if (headerDataLength + packetLengthDataLength + packet.getData().length + trailerDataLength <= 0) {
                    socketEventManager.noticeSendCancelEvent(packet);
                    dataPacketSendManager.clearSendingPacket();
                    continue;
                }

                socketEventManager.noticeSendBeginEvent(packet);
                socketEventManager.noticeSendProgressEvent(packet, sendPacketLength, headerDataLength, packetLengthDataLength, packet.getData().length, trailerDataLength);

                if (headerDataLength > 0) {
                    activeSocket.getOutputStream().write(headerData);
                    activeSocket.getOutputStream().flush();
                    timeRecorder.updateLastTimeSend(System.currentTimeMillis());

                    sendPacketLength += headerDataLength;
                    socketEventManager.noticeSendProgressEvent(packet, sendPacketLength, headerDataLength, packetLengthDataLength, packet.getData().length, trailerDataLength);
                }

                if (packetLengthDataLength > 0) {
                    activeSocket.getOutputStream().write(packetLengthData);
                    activeSocket.getOutputStream().flush();
                    timeRecorder.updateLastTimeSend(System.currentTimeMillis());

                    sendPacketLength += packetLengthDataLength;
                    socketEventManager.noticeSendProgressEvent(packet, sendPacketLength, headerDataLength, packetLengthDataLength, packet.getData().length, trailerDataLength);
                }

                if (packet.getData().length > 0) {
                    int segmentLength = activeSocket.getSendBufferSize();
                    if (mSocketClient.getSocketPacketHelper().isSendSegmentEnabled()) {
                        segmentLength = Math.min(segmentLength, mSocketClient.getSocketPacketHelper().getSendSegmentLength());
                    }

                    int offset = 0;

                    while (offset < packet.getData().length) {
                        int end = offset + segmentLength;
                        end = Math.min(end, packet.getData().length);
                        activeSocket.getOutputStream().write(packet.getData(), offset, end - offset);
                        activeSocket.getOutputStream().flush();
                        timeRecorder.updateLastTimeSend(System.currentTimeMillis());

                        sendPacketLength += end - offset;

                        socketEventManager.noticeSendProgressEvent(packet, sendPacketLength, headerDataLength, packetLengthDataLength, packet.getData().length, trailerDataLength);

                        offset = end;
                    }
                }

                if (trailerDataLength > 0) {
                    activeSocket.getOutputStream().write(trailerData);
                    activeSocket.getOutputStream().flush();
                    timeRecorder.updateLastTimeSend(System.currentTimeMillis());

                    sendPacketLength += trailerDataLength;

                    socketEventManager.noticeSendProgressEvent(packet, sendPacketLength, headerDataLength, packetLengthDataLength, packet.getData().length, trailerDataLength);
                }

                socketEventManager.noticeSendEndEvent(packet);
                dataPacketSendManager.clearSendingPacket();

                timeRecorder.updateLastTimeSend(ISocketClient.NoSendingTime);
            }
        } catch (Throwable e) {
//                e.printStackTrace();
            mSocketClient.disconnect();
            if (dataPacketSendManager.isExistSendingPacket()) {
                socketEventManager.noticeSendCancelEvent(dataPacketSendManager.getSendingPacket());
                dataPacketSendManager.clearSendingPacket();
            }
        }
    }

    @Override
    public void kill() {
        super.kill();
        DataPacketSendManager dataPacketSendManager = mClientAssistant.getSendDataPacketManager();
        SocketEventManager socketEventManager = mClientAssistant.getSocketEventManager();
        if (dataPacketSendManager.isExistSendingPacket()) {
            socketEventManager.noticeSendCancelEvent(dataPacketSendManager.getSendingPacket());
            dataPacketSendManager.clearSendingPacket();
        }
    }
}
