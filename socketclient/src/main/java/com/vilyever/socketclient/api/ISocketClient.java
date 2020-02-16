package com.vilyever.socketclient.api;

import com.vilyever.socketclient.SocketClient;
import com.vilyever.socketclient.helper.ClientReceivingEvent;
import com.vilyever.socketclient.helper.ClientSendingEvent;
import com.vilyever.socketclient.helper.ClientStatusEvent;
import com.vilyever.socketclient.helper.SocketClientAddress;
import com.vilyever.socketclient.helper.SocketHeartBeatHelper;
import com.vilyever.socketclient.helper.SocketPacketHelper;

@SuppressWarnings("UnusedReturnValue")
public interface ISocketClient {

    /**
     * 记录上次发送数据片段的时间
     * 仅在每个发送包开始发送时计时，结束后重置计时
     * NoSendingTime 表示当前没有在发送数据
     */
    long NoSendingTime = -1;


    SocketClientAddress getAddress();

    SocketPacketHelper getSocketPacketHelper();

    SocketHeartBeatHelper getHeartBeatHelper();

    void setCharsetName(String charsetName);

    String getCharsetName();

    boolean isConnected();

    boolean isDisconnected();

    boolean isConnecting();

    boolean isDisConnecting();

    void disconnect();

    SocketClient registerSocketStatusEvent(ClientStatusEvent delegate);

    SocketClient unregisterSocketStatusEvent(ClientStatusEvent delegate);

    SocketClient registerDataSendingEvent(ClientSendingEvent delegate);

    SocketClient unregisterDataSendingEvent(ClientSendingEvent delegate);

    SocketClient registerDataReceivingEvent(ClientReceivingEvent delegate);

    SocketClient unregisterDataReceivingEvent(ClientReceivingEvent delegate);
}
