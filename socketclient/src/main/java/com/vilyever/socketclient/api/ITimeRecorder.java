package com.vilyever.socketclient.api;

public interface ITimeRecorder {
    void updateLastTimeSend(long sendTime);

    void updateLastTimeReceive(long receiveTime);
}
