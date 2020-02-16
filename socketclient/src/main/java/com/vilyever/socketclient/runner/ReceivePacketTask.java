package com.vilyever.socketclient.runner;

import com.vilyever.socketclient.api.IClientAssistant;
import com.vilyever.socketclient.api.ISocketClient;
import com.vilyever.socketclient.api.ITimeRecorder;
import com.vilyever.socketclient.helper.SocketInputReader;
import com.vilyever.socketclient.helper.SocketPacketHelper;
import com.vilyever.socketclient.helper.SocketResponsePacket;
import com.vilyever.socketclient.manager.DataPacketReceiveManager;
import com.vilyever.socketclient.manager.DataPacketSendManager;
import com.vilyever.socketclient.manager.SocketEventManager;

import java.net.Socket;

public class ReceivePacketTask extends AbsSocketTask {
    public ReceivePacketTask(ISocketClient socketClient, IClientAssistant clientAssistant) {
        super(socketClient, clientAssistant);
    }

    @Override
    public void run() {
        if (mSocketClient.getSocketPacketHelper().getReadStrategy()
                == SocketPacketHelper.ReadStrategy.Manually) {
            return;
        }

        ITimeRecorder timeRecorder = mClientAssistant.getTimeRecorder();
        Socket activeSocket = mClientAssistant.getActiveSocket();
        SocketEventManager socketEventManager = mClientAssistant.getSocketEventManager();
        DataPacketReceiveManager dataPacketReceiveManager = mClientAssistant.getReceiveDataPacketManager();

        try {
            SocketInputReader socketInputReader = new SocketInputReader(activeSocket.getInputStream());

            while (mSocketClient.isConnected()
                    && isRunning()) {
                SocketResponsePacket packet = new SocketResponsePacket();
                dataPacketReceiveManager.updateReceivingPacket(packet);

                byte[] headerData = mSocketClient.getSocketPacketHelper().getReceiveHeaderData();
                int headerDataLength = headerData == null ? 0 : headerData.length;

                byte[] trailerData = mSocketClient.getSocketPacketHelper().getReceiveTrailerData();
                int trailerDataLength = trailerData == null ? 0 : trailerData.length;

                int packetLengthDataLength = mSocketClient.getSocketPacketHelper().getReceivePacketLengthDataLength();

                int dataLength = 0;
                int receivedPacketLength = 0;

                socketEventManager.noticeReceiveBeginEvent(packet);

                if (headerDataLength > 0) {
                    byte[] data = socketInputReader.readToData(headerData, true);
                    timeRecorder.updateLastTimeReceive(System.currentTimeMillis());
                    packet.setHeaderData(data);

                    receivedPacketLength += headerDataLength;
                }

                if (mSocketClient.getSocketPacketHelper().getReadStrategy() == SocketPacketHelper.ReadStrategy.AutoReadByLength) {
                    if (packetLengthDataLength < 0) {
                        socketEventManager.noticeReceiveCancelEvent(packet);
                        dataPacketReceiveManager.clearReceivingPacket();
                    } else if (packetLengthDataLength == 0) {
                        socketEventManager.noticeReceiveEndEvent(packet);
                        dataPacketReceiveManager.clearReceivingPacket();
                    }

                    byte[] data = socketInputReader.readToLength(packetLengthDataLength);
                    timeRecorder.updateLastTimeReceive(System.currentTimeMillis());
                    packet.setPacketLengthData(data);

                    receivedPacketLength += packetLengthDataLength;

                    int bodyTrailerLength = mSocketClient.getSocketPacketHelper().getReceivePacketDataLength(data);

                    dataLength = bodyTrailerLength - trailerDataLength;

                    if (dataLength > 0) {
                        int segmentLength = activeSocket.getReceiveBufferSize();
                        if (mSocketClient.getSocketPacketHelper().isReceiveSegmentEnabled()) {
                            segmentLength = Math.min(segmentLength, mSocketClient.getSocketPacketHelper().getReceiveSegmentLength());
                        }
                        int offset = 0;
                        while (offset < dataLength) {
                            int end = offset + segmentLength;
                            end = Math.min(end, dataLength);
                            data = socketInputReader.readToLength(end - offset);
                            timeRecorder.updateLastTimeReceive(System.currentTimeMillis());

                            if (packet.getData() == null) {
                                packet.setData(data);
                            } else {
                                byte[] mergedData = new byte[packet.getData().length + data.length];

                                System.arraycopy(packet.getData(), 0, mergedData, 0, packet.getData().length);
                                System.arraycopy(data, 0, mergedData, packet.getData().length, data.length);

                                packet.setData(mergedData);
                            }

                            receivedPacketLength += end - offset;

                            socketEventManager.noticeReceiveProgressEvent(packet, receivedPacketLength, headerDataLength, packetLengthDataLength, dataLength, trailerDataLength);

                            offset = end;
                        }
                    } else if (dataLength < 0) {
                        socketEventManager.noticeReceiveCancelEvent(packet);
                        dataPacketReceiveManager.clearReceivingPacket();
                    }

                    if (trailerDataLength > 0) {
                        data = socketInputReader.readToLength(trailerDataLength);
                        timeRecorder.updateLastTimeReceive(System.currentTimeMillis());
                        packet.setTrailerData(data);

                        receivedPacketLength += trailerDataLength;

                        socketEventManager.noticeReceiveProgressEvent(packet, receivedPacketLength, headerDataLength, packetLengthDataLength, dataLength, trailerDataLength);
                    }
                } else if (mSocketClient.getSocketPacketHelper().getReadStrategy() == SocketPacketHelper.ReadStrategy.AutoReadToTrailer) {
                    if (trailerDataLength > 0) {
                        byte[] data = socketInputReader.readToData(trailerData, false);
                        timeRecorder.updateLastTimeReceive(System.currentTimeMillis());
                        packet.setData(data);
                        packet.setTrailerData(trailerData);

                        receivedPacketLength += data.length;
                    } else {
                        socketEventManager.noticeReceiveCancelEvent(packet);
                        dataPacketReceiveManager.clearReceivingPacket();
                    }
                }

                packet.setHeartBeat(mSocketClient.getHeartBeatHelper().isReceiveHeartBeatPacket(packet));

                if (mSocketClient.getCharsetName() != null) {
                    packet.buildStringWithCharsetName(mSocketClient.getCharsetName());
                }

                socketEventManager.noticeReceiveEndEvent(packet);
                socketEventManager.noticeSocketRespond(packet);
                timeRecorder.updateLastTimeReceive(System.currentTimeMillis());
                dataPacketReceiveManager.clearReceivingPacket();
            }
        } catch (Exception e) {
//                e.printStackTrace();
            mSocketClient.disconnect();

            if (dataPacketReceiveManager.isExistReceivingPacket()) {
                socketEventManager.noticeReceiveCancelEvent(dataPacketReceiveManager.getReceivingPacket());
                dataPacketReceiveManager.clearReceivingPacket();
            }
        }
    }

    @Override
    public void kill() {
        super.kill();
        DataPacketReceiveManager dataPacketReceiveManager = mClientAssistant.getReceiveDataPacketManager();
        SocketEventManager socketEventManager = mClientAssistant.getSocketEventManager();

        if (dataPacketReceiveManager.isExistReceivingPacket()) {
            socketEventManager.noticeReceiveCancelEvent(dataPacketReceiveManager.getReceivingPacket());
            dataPacketReceiveManager.clearReceivingPacket();
        }
    }
}
