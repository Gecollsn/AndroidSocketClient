package com.vilyever.socketclient.api;

import com.vilyever.socketclient.SocketClient;
import com.vilyever.socketclient.helper.SocketPacket;
import com.vilyever.socketclient.manager.DataPacketReceiveManager;
import com.vilyever.socketclient.manager.DataPacketSendManager;
import com.vilyever.socketclient.manager.SocketEventManager;

import java.net.Socket;

public interface IClientAssistant {
    void updateState(SocketClient.State state);

    void __i__enqueue(SocketPacket packet);

    void __i__onConnected();

    DataPacketSendManager getSendDataPacketManager();

    DataPacketReceiveManager getReceiveDataPacketManager();

    SocketEventManager getSocketEventManager();

    Socket getActiveSocket();

    ITimeRecorder getTimeRecorder();

    void killAllTask();

    Socket newSocket();

    void stopSocket();
}
