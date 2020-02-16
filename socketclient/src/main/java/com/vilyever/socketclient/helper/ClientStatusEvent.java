package com.vilyever.socketclient.helper;

import android.support.annotation.NonNull;

import com.vilyever.socketclient.SocketClient;

/**
 * ClientStatusEvent
 * Created by vilyever on 2016/5/30.
 * Feature:
 */
public interface ClientStatusEvent {
    void onConnected(SocketClient client);

    void onDisconnected(SocketClient client);

    void onResponse(SocketClient client, @NonNull SocketResponsePacket responsePacket);
}
