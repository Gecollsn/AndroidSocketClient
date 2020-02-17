package com.vilyever.socketclient.runner;

import com.vilyever.socketclient.SocketClient;
import com.vilyever.socketclient.api.IClientAssistant;
import com.vilyever.socketclient.api.ISocketClient;

import java.io.IOException;
import java.net.Socket;

public class DisconnectSocketTask extends AbsSocketTask {
    public DisconnectSocketTask(ISocketClient socketClient, IClientAssistant clientAssistant) {
        super(socketClient, clientAssistant);
    }

    @Override
    public void run() {

        Socket activeSocket = mClientAssistant.getActiveSocket();

        if (!activeSocket.isClosed() || mSocketClient.isConnecting()) {
            try {
                activeSocket.getOutputStream().close();
                activeSocket.getInputStream().close();
            } catch (IOException e) {
//                e.printStackTrace();
            } finally {
                mClientAssistant.stopSocket();
            }
        }

        mClientAssistant.updateState(SocketClient.State.Disconnected);
        mClientAssistant.getSocketEventManager().noticeSocketDisconnected();
        mClientAssistant.killAllTask();
    }
}
