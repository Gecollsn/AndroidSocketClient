package com.vilyever.socketclient.runner;

import com.vilyever.socketclient.api.IClientAssistant;
import com.vilyever.socketclient.api.ISocketClient;

public abstract class AbsSocketTask implements ISocketTask {
    private boolean flag = true;
    protected ISocketClient mSocketClient;
    protected IClientAssistant mClientAssistant;


    public AbsSocketTask(ISocketClient socketClient, IClientAssistant clientAssistant) {
        this.mSocketClient = socketClient;
        this.mClientAssistant = clientAssistant;
    }


    @Override
    public void kill() {
        flag = false;
    }

    @Override
    public boolean isRunning() {
        return flag;
    }
}
