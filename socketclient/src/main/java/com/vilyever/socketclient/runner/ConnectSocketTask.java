package com.vilyever.socketclient.runner;

import com.vilyever.socketclient.api.IClientAssistant;
import com.vilyever.socketclient.api.ISocketClient;
import com.vilyever.socketclient.helper.SocketClientAddress;

import java.io.IOException;

public class ConnectSocketTask extends AbsSocketTask {

    public ConnectSocketTask(ISocketClient socketClient, IClientAssistant clientAssistant) {
        super(socketClient, clientAssistant);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        this.mSocketClient = null;
        this.mClientAssistant = null;
    }

    @Override
    public void run() {
        try {
            SocketClientAddress address = mSocketClient.getAddress();

            mClientAssistant
                    .newSocket()
                    .connect(address.getInetSocketAddress(), address.getConnectionTimeout());

            if (isRunning()) mClientAssistant.__i__onConnected();
        } catch (IOException e) {
            e.printStackTrace();

            mSocketClient.disconnect();
        }
    }
}
