package com.vilyever.socketclient.manager;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.vilyever.socketclient.SocketClient;
import com.vilyever.socketclient.api.IClientAssistant;
import com.vilyever.socketclient.helper.ClientReceivingEvent;
import com.vilyever.socketclient.helper.ClientSendingEvent;
import com.vilyever.socketclient.helper.ClientStatusEvent;
import com.vilyever.socketclient.helper.SocketPacket;
import com.vilyever.socketclient.helper.SocketResponsePacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("WeakerAccess")
public class SocketEventManager {
    private List<ClientSendingEvent> mSendingEventList = new ArrayList<>();
    private List<ClientReceivingEvent> mReceivingEventList = new ArrayList<>();
    private List<ClientStatusEvent> mStatusEventList = new ArrayList<>();
    private SocketClient mSocketClient;
    private final UIHandler mUiHandler = new UIHandler();

    private long mLastTimeCallReceiveProgressEvent;


    public SocketEventManager(SocketClient client) {
        this.mSocketClient = client;
    }

    public void registerSendingEvent(ClientSendingEvent event) {
        if (!mSendingEventList.contains(event)) {
            this.mSendingEventList.add(event);
        }
    }

    public void unregisterSendingEvent(ClientSendingEvent event) {
        mSendingEventList.remove(event);
    }

    public void clearSendingEvent() {
        mSendingEventList.clear();
    }

    public void registerReceivingEvent(ClientReceivingEvent event) {
        if (!mReceivingEventList.contains(event)) {
            this.mReceivingEventList.add(event);
        }
    }

    public void unregisterReceivingEvent(ClientReceivingEvent event) {
        mReceivingEventList.remove(event);
    }

    public void clearReceivingEvent() {
        mReceivingEventList.clear();
    }

    public void registerStatusEvent(ClientStatusEvent event) {
        if (!mStatusEventList.contains(event)) {
            this.mStatusEventList.add(event);
        }
    }

    public void unregisterStatusEvent(ClientStatusEvent event) {
        mStatusEventList.remove(event);
    }

    public void clearStatusEvent() {
        mStatusEventList.clear();
    }

    public void clearAll() {
        clearSendingEvent();
        clearReceivingEvent();
        clearStatusEvent();
    }

    public void noticeSendBeginEvent(SocketPacket packet) {
        if (shouldConvertToMainThread(() -> noticeSendBeginEvent(packet))) {
            return;
        }

        for (ClientSendingEvent event : getUnmodifiableSendEventList()) {
            runWithTry(() -> event.onSendPacketBegin(mSocketClient, packet));
        }
    }

    public void noticeSendCancelEvent(SocketPacket packet) {
        if (shouldConvertToMainThread(() -> noticeSendCancelEvent(packet))) {
            return;
        }

        for (ClientSendingEvent event : getUnmodifiableSendEventList()) {
            runWithTry(() -> event.onSendPacketCancel(mSocketClient, packet));
        }
    }

    public void noticeSendProgressEvent(SocketPacket packet,
                                        int sendLength,
                                        int headerLength,
                                        int packetLengthDataLength,
                                        int dataLength,
                                        int trailerLength) {
        if (shouldConvertToMainThread(() ->
                noticeSendProgressEvent(
                        packet,
                        sendLength,
                        headerLength,
                        packetLengthDataLength,
                        dataLength,
                        trailerLength))) {
            return;
        }

        float progress = sendLength / (float) (headerLength + packetLengthDataLength + dataLength + trailerLength);


        for (ClientSendingEvent event : getUnmodifiableSendEventList()) {
            runWithTry(() -> event.onSendingPacketInProgress(mSocketClient, packet, progress, sendLength));
        }
    }

    public void noticeSendEndEvent(SocketPacket packet) {
        if (shouldConvertToMainThread(() -> noticeSendEndEvent(packet))) {
            return;
        }

        for (ClientSendingEvent event : getUnmodifiableSendEventList()) {
            runWithTry(() -> event.onSendPacketEnd(mSocketClient, packet));
        }
    }

    private List<ClientSendingEvent> getUnmodifiableSendEventList() {
        return Collections.unmodifiableList(mSendingEventList);
    }

    public void noticeReceiveBeginEvent(SocketResponsePacket packet) {
        if (shouldConvertToMainThread(() -> noticeReceiveBeginEvent(packet))) {
            return;
        }

        for (ClientReceivingEvent event : getUnmodifiableReceiveEventList()) {
            runWithTry(() -> event.onReceivePacketBegin(mSocketClient, packet));
        }
    }

    public void noticeReceiveCancelEvent(SocketResponsePacket packet) {
        if (shouldConvertToMainThread(() -> noticeReceiveCancelEvent(packet))) {
            return;
        }

        for (ClientReceivingEvent event : getUnmodifiableReceiveEventList()) {
            runWithTry(() -> event.onReceivePacketCancel(mSocketClient, packet));
        }
    }

    public void noticeReceiveEndEvent(SocketResponsePacket packet) {
        if (shouldConvertToMainThread(() -> noticeReceiveEndEvent(packet))) {
            return;
        }

        for (ClientReceivingEvent event : getUnmodifiableReceiveEventList()) {
            runWithTry(() -> event.onReceivePacketEnd(mSocketClient, packet));
        }
    }

    public void noticeReceiveProgressEvent(SocketResponsePacket packet,
                                           int receivedLength,
                                           int headerLength,
                                           int packetLengthDataLength,
                                           int dataLength,
                                           int trailerLength) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - mLastTimeCallReceiveProgressEvent < (1000 / 24)) {
            return;
        }

        if (shouldConvertToMainThread(() ->
                noticeReceiveProgressEvent(
                        packet,
                        receivedLength,
                        headerLength,
                        packetLengthDataLength,
                        dataLength,
                        trailerLength))) {
            return;
        }

        float progress = receivedLength / (float) (headerLength + packetLengthDataLength + dataLength + trailerLength);

        for (ClientReceivingEvent event : getUnmodifiableReceiveEventList()) {
            runWithTry(() -> event.onReceivingPacketInProgress(mSocketClient, packet, progress, receivedLength));
        }

        this.mLastTimeCallReceiveProgressEvent = System.currentTimeMillis();
    }

    private List<ClientReceivingEvent> getUnmodifiableReceiveEventList() {
        return Collections.unmodifiableList(mReceivingEventList);
    }

    public void noticeSocketConnected() {
        if (shouldConvertToMainThread(this::noticeSocketConnected)) {
            return;
        }

        for (ClientStatusEvent event : getUnmodifiableStatusEventList()) {
            runWithTry(() -> event.onConnected(mSocketClient));
        }
    }

    public void noticeSocketDisconnected() {
        if (shouldConvertToMainThread(this::noticeSocketDisconnected)) {
            return;
        }

        for (ClientStatusEvent event : getUnmodifiableStatusEventList()) {
            runWithTry(() -> event.onDisconnected(mSocketClient));
        }
    }

    public void noticeSocketRespond(SocketResponsePacket packet) {
        if (shouldConvertToMainThread(() -> noticeSocketRespond(packet))) {
            return;
        }

        for (ClientStatusEvent event : getUnmodifiableStatusEventList()) {
            runWithTry(() -> event.onResponse(mSocketClient, packet));
        }
    }

    private List<ClientStatusEvent> getUnmodifiableStatusEventList() {
        return Collections.unmodifiableList(mStatusEventList);
    }

    public boolean shouldConvertToMainThread(Runnable action) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mUiHandler.post(action);

            return true;
        }

        return false;
    }

    private static class UIHandler extends Handler {
        private UIHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    }

    private void runWithTry(Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
