package com.vilyever.socketclient.manager;

import com.vilyever.socketclient.helper.SocketResponsePacket;

public class DataPacketReceiveManager implements IDataPacketManager {

    private SocketResponsePacket mReceivingPacket;

    public void updateReceivingPacket(SocketResponsePacket packet) {
        this.mReceivingPacket = packet;
    }

    public boolean isExistReceivingPacket() {
        return mReceivingPacket != null;
    }

    public void clearReceivingPacket() {
        mReceivingPacket = null;
    }

    public SocketResponsePacket getReceivingPacket() {
        return mReceivingPacket;
    }

    @Override
    public void reset() {
        clearReceivingPacket();
    }
}
