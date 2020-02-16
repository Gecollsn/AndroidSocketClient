package com.vilyever.socketclient.runner;

public interface ISocketTask extends Runnable {
    void kill();

    boolean isRunning();
}
