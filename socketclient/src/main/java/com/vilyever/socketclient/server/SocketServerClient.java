package com.vilyever.socketclient.server;

import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;

import com.vilyever.socketclient.SocketClient;
import com.vilyever.socketclient.helper.SocketConfigure;

import java.net.Socket;

/**
 * SocketServerClient
 * AndroidSocketClient <com.vilyever.socketclient.server>
 * Created by vilyever on 2016/3/23.
 * Feature:
 */
public class SocketServerClient extends SocketClient {
    final SocketServerClient self = this;


    /* Constructors */
    public SocketServerClient(@NonNull Socket socket, SocketConfigure configure) {
        super();

        setActiveSocket(socket);
        getSocketConfigure()
                .setCharsetName(configure.getCharsetName())
                .setAddress(getAddress());

        internalOnConnected();
    }


    @CallSuper
    protected void internalOnConnected() {

//        setLastSendHeartBeatMessageTime(System.currentTimeMillis());
//        setLastReceiveMessageTime(System.currentTimeMillis());
//        setLastSendMessageTime(ISocketClient.NoSendingTime);
//
//        setSendingPacket(null);
//        setReceivingResponsePacket(null);
//
//        __i__onConnected();
    }
    
}