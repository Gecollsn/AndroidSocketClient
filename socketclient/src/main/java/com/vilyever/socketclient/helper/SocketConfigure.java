package com.vilyever.socketclient.helper;

/**
 * SocketConfigure
 * Created by vilyever on 2016/5/31.
 * Feature:
 */
@SuppressWarnings("UnusedReturnValue")
public class SocketConfigure {
    private String charsetName;
    private SocketPacketHelper socketPacketHelper = new SocketPacketHelper();
    private SocketHeartBeatHelper heartBeatHelper = new SocketHeartBeatHelper();
    private SocketClientAddress address;

    public SocketConfigure setCharsetName(String charsetName) {
        this.charsetName = charsetName;
        return this;
    }

    public String getCharsetName() {
        return this.charsetName;
    }

    public SocketConfigure setAddress(SocketClientAddress address) {
        this.address = address.copy();
        return this;
    }

    public SocketClientAddress getAddress() {
        return this.address;
    }

    public SocketPacketHelper getSocketPacketHelper() {
        return this.socketPacketHelper;
    }

    public SocketHeartBeatHelper getHeartBeatHelper() {
        return this.heartBeatHelper;
    }
}