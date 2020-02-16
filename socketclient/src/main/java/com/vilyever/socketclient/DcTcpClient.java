package com.vilyever.socketclient;

import android.support.annotation.NonNull;
import android.util.Log;

import com.vilyever.socketclient.helper.ClientSendingEvent;
import com.vilyever.socketclient.helper.ClientStatusEvent;
import com.vilyever.socketclient.helper.SocketHeartBeatHelper;
import com.vilyever.socketclient.helper.SocketPacket;
import com.vilyever.socketclient.helper.SocketPacketHelper;
import com.vilyever.socketclient.helper.SocketResponsePacket;
import com.vilyever.socketclient.util.CharsetUtil;
import com.vilyever.socketclient.util.StringValidation;


public class DcTcpClient {
    private SocketClient mSocketClient;
    private String ip;
    private String port;

    private final static int MAX_SEGMENT_LENGTH = Integer.MAX_VALUE;

    //Debug专用
    private final boolean _isDebug = false;
    private final String TAG = this.getClass().toString();

    //非初始化可设置内容
    private boolean isManualDisconnect = false;
    private boolean isSocketConnected = false;

    //初始化可设置内容
    private int mConnectionTimeout = 10 * 1000;
    private int mSendTimeOut = 5 * 1000;
    private int mReceiveTimeOut = 15 * 1000;

    private byte[] magicNumber = null;
    private int bytesIndicateDataLength = 4;
    private int mHeartbeatInterval = 0;
    private int mSegmentLength = MAX_SEGMENT_LENGTH;

    private boolean isSendHeartBeatDynamic = false;
    private boolean isReceiveHeartBeatDynamic = false;
    private byte[] mHeartbeatDataForSend = null;
    private byte[] mHeartbeatDataForReceive = null;
    private HeartbeatBuilder mHeartbeatDynamicBuilder = null;
    private HeartbeatChecker mHeartBeatChecker = null;

    private ConnectStatusListener mConnectStatusListener;
    private SendStatusListener mSendStatusListener;
    private ClientStatusEvent mSocketClientConnectListener;
    private ClientSendingEvent mSocketClientSendingListener;

    public boolean isConnected() {
        return isSocketConnected;
    }

    public static class DcClientBuilder {
        private DcTcpClient dcTcpClient;
        private boolean buildAlready;

        public DcClientBuilder(String ip, String port) {
            if (!checkIpIllegal(ip)) {
                throw new IllegalArgumentException("ip param illegal");
            }

            if (!checkPortIllegal(port)) {
                throw new IllegalArgumentException("port param illegal");
            }

            dcTcpClient = new DcTcpClient(ip, port);
        }


        public DcClientBuilder setConnectTimeout(int timeout) {
            this.dcTcpClient.mConnectionTimeout = timeout;
            return this;
        }

        public DcClientBuilder setSendTimeout(int timeout) {
            this.dcTcpClient.mSendTimeOut = timeout;
            return this;
        }

        public DcClientBuilder setReceiveTimeout(int timeout) {
            this.dcTcpClient.mReceiveTimeOut = timeout;
            return this;
        }

        public DcClientBuilder setMagicNumber(byte[] magicNumber) {
            this.dcTcpClient.magicNumber = magicNumber;
            return this;
        }

        public DcClientBuilder setBytesThatIndicateDataLength(int bytes) {
            this.dcTcpClient.bytesIndicateDataLength = bytes;
            return this;
        }

        public DcClientBuilder setSegmentLength(int segmentLength) {
            if (segmentLength < MAX_SEGMENT_LENGTH) {
                this.dcTcpClient.mSegmentLength = segmentLength;
            } else this.dcTcpClient.mSegmentLength = MAX_SEGMENT_LENGTH;
            return this;
        }

        public DcClientBuilder setHeartbeatInterval(int interval) {
            this.dcTcpClient.mHeartbeatInterval = interval;
            if (this.dcTcpClient.mHeartbeatInterval < 0) {
                this.dcTcpClient.mHeartbeatInterval = 0;
            }
            return this;
        }

        /**
         * 设置发收固定心跳包
         * @param send     固定发送心跳包
         * @param receive  固定收到心跳包
         */
        public DcClientBuilder setHeartbeatData(byte[] send, byte[] receive) {
            this.dcTcpClient.isSendHeartBeatDynamic = false;
            this.dcTcpClient.isReceiveHeartBeatDynamic = false;
            if (send != null) {
                this.dcTcpClient.mHeartbeatDataForSend = send.clone();
            }
            if (receive != null) {
                this.dcTcpClient.mHeartbeatDataForReceive = receive.clone();
            }
            return this;
        }

        /**
         * 设置发固定、收动态心跳包
         * @param send     固定发送心跳包
         * @param heartbeatChecker  动态检查接收的心跳包
         */
        public DcClientBuilder setHeartbeatData(byte[] send, HeartbeatChecker heartbeatChecker) {
            this.dcTcpClient.isSendHeartBeatDynamic = false;
            this.dcTcpClient.isReceiveHeartBeatDynamic = true;
            if (send != null) {
                this.dcTcpClient.mHeartbeatDataForSend = send.clone();
            }
            if (heartbeatChecker != null) {
                this.dcTcpClient.mHeartBeatChecker = heartbeatChecker;
            }

            return this;
        }


        /**
         * 设置发动态、收固定心跳包
         * @param heartBeatBuilder  动态生成发送心跳包
         * @param receiveHeartBeat  固定收到心跳包
         */
        public void setHeartBeat(HeartbeatBuilder heartBeatBuilder, byte[] receiveHeartBeat) {
            this.dcTcpClient.isSendHeartBeatDynamic = true;
            this.dcTcpClient.isReceiveHeartBeatDynamic = false;
            if (heartBeatBuilder != null) {
                this.dcTcpClient.mHeartbeatDynamicBuilder = heartBeatBuilder;
            }
            if (receiveHeartBeat != null) {
                this.dcTcpClient.mHeartbeatDataForReceive = receiveHeartBeat.clone();
            }
        }


        /**
         * 设置发收动态心跳包
         * @param heartBeatBuilder  动态生成发送心跳包
         * @param heartBeatChecker  动态检查接收的心跳包
         */
        public void setHeartBeat(HeartbeatBuilder heartBeatBuilder, HeartbeatChecker heartBeatChecker) {
            this.dcTcpClient.isSendHeartBeatDynamic = true;
            this.dcTcpClient.isReceiveHeartBeatDynamic = true;
            if (heartBeatBuilder != null) {
                this.dcTcpClient.mHeartbeatDynamicBuilder = heartBeatBuilder;
            }
            if (heartBeatChecker != null) {
                this.dcTcpClient.mHeartBeatChecker = heartBeatChecker;
            }
        }

        public synchronized DcTcpClient build() {
            if (buildAlready) {
                throw new RuntimeException("builder can build only once, please create new one");
            }

            buildAlready = true;

            //todo segment大小限制

            prepareSocketClient(dcTcpClient);
            return dcTcpClient;
        }

        private void prepareSocketClient(final DcTcpClient dcTcpClient) {
            if (dcTcpClient.mSocketClient == null) {
                dcTcpClient.mSocketClient = new SocketClient();

                dcTcpClient.__i__setupAddress(dcTcpClient.mSocketClient);

                dcTcpClient.__i__setupEncoding(dcTcpClient.mSocketClient);

                //设置心跳包
                dcTcpClient.__i__setupHeartBeat(dcTcpClient.mSocketClient);

                //发送添加包头
                dcTcpClient.__i__setupReadByLengthForSender(dcTcpClient.mSocketClient);
                //解析接收包头
                dcTcpClient.__i__setupReadByLengthForReceiver(dcTcpClient.mSocketClient);
            }
        }

        private boolean checkIpIllegal(String ip) {
            return StringValidation.validateRegex(ip, StringValidation.RegexIP);
        }

        private boolean checkPortIllegal(String port) {
            return StringValidation.validateRegex(port, StringValidation.RegexPort);
        }
    }


    /**
     * 构造器
     * @param remoteIp      对端IP
     * @param remotePort    对端端口
     */
    private DcTcpClient(String remoteIp, String remotePort) {
        this.ip = remoteIp;
        this.port = remotePort;
    }

    public interface HeartbeatBuilder {
        byte[] build();
    }

    public interface HeartbeatChecker {
        boolean check(byte[] data);
    }

    public static class DisconnectedEvent {
        boolean isManualDisconnect = false;
        String reason = "default";

        DisconnectedEvent() {

        }

        DisconnectedEvent(boolean manualDisconnect, String why) {
            isManualDisconnect = manualDisconnect;
            reason = why;
        }
    }

    /**
     * 连接状态回调
     */
    public interface ConnectStatusListener {
        void onConnected();

        void onDisconnected(DisconnectedEvent de);

        void onResponse(byte[] data);
    }

    /**
     * 发送数据包状态回调
     */
    public interface SendStatusListener {
        void onSendBegin();

        void onSendInProgress();

        void onSendEnd();

        void onSendCancel();
    }

    /**
     * 初始化后或断开连接后，可以连接
     * @param listener    连接状态回调
     */
    public synchronized void connect(ConnectStatusListener listener) {
        if (!isSocketConnected) {
            try {
                getDcProtoClient(listener).connect();
            } catch (Exception e) {
                listener.onDisconnected(new DisconnectedEvent(false, "Exception in connecting: " + e.toString()));
            }
        }
    }

    /**
     * 手动断开连接
     */
    public synchronized void disconnect() {
        if (this.mSocketClient != null && isSocketConnected) {
            isManualDisconnect = true;
            isSocketConnected = false;

            if (mConnectStatusListener != null) {
                mConnectStatusListener.onDisconnected(new DisconnectedEvent(true, "disconnect by user"));
            }

            this.mConnectStatusListener = null;
            this.mSocketClient.unregisterSocketStatusEvent(mSocketClientConnectListener);
            this.mSocketClient.unregisterDataSendingEvent(mSocketClientSendingListener);
            this.mSocketClient.disconnect();
        }
    }


    /**
     * 发送字节序列
     * @param bodyData   字节序列
     */
    public synchronized void send(byte[] bodyData) {
        this.send(bodyData, null);
    }

    /**
     * 发送带回调的字节序列
     * @param bodyData   字节序列
     * @param listener    发送状态回调
     */
    public synchronized void send(byte[] bodyData, final SendStatusListener listener) {
        this.mSendStatusListener = listener;

        if (bodyData == null || this.mSocketClient == null || !isSocketConnected) {
            if (this.mSendStatusListener != null)
                this.mSendStatusListener.onSendCancel();
        } else {
            if (this.mSendStatusListener != null) {
                this.mSocketClient.registerDataSendingEvent(getSocketClientSendingListener());
            }
            this.mSocketClient.sendData(bodyData);
        }
    }

    private SocketClient getDcProtoClient(final ConnectStatusListener listener) {
        this.mConnectStatusListener = listener;

        //注册状态回调
        if (mConnectStatusListener != null) {
            mSocketClient.registerSocketStatusEvent(getSocketClientConnectListener());
        }

        return this.mSocketClient;
    }

    private ClientStatusEvent getSocketClientConnectListener() {
        if (mSocketClientConnectListener == null) {
            mSocketClientConnectListener = new ClientStatusEvent() {
                @Override
                public void onConnected(SocketClient client) {
                    isSocketConnected = true;
                    if (_isDebug) {
                        Log.d(TAG, "onConnected: ");
                    }
                    mConnectStatusListener.onConnected();
                }

                @Override
                public void onDisconnected(SocketClient client) {
                    isSocketConnected = false;
                    if (_isDebug) {
                        Log.d(TAG, "onDisconnected: ");
                    }
                    mConnectStatusListener.onDisconnected(new DisconnectedEvent(isManualDisconnect, isManualDisconnect ? "Manual disconnect" : "default"));
                    isManualDisconnect = false;
                }

                @Override
                public void onResponse(SocketClient client, @NonNull SocketResponsePacket responsePacket) {
                    if (responsePacket.isHeartBeat()) {
                        if (_isDebug) {
                            Log.d(TAG, "onResponse: [ ~ Heart Beat ~ ]");
                        }
                    } else {
                        if (_isDebug) {
                            Log.d(TAG, "onResponse: [" + responsePacket.getMessage() + "]");
                        }
                        mConnectStatusListener.onResponse(responsePacket.getData());
                    }
                }
            };
        }

        return mSocketClientConnectListener;
    }

    private ClientSendingEvent getSocketClientSendingListener() {
        if (mSocketClientSendingListener == null) {
            mSocketClientSendingListener = new ClientSendingEvent() {
                @Override
                public void onSendPacketBegin(SocketClient client, SocketPacket packet) {
                    if (_isDebug) {
                        Log.d(TAG, "onSendPacketBegin: ");
                    }
                    mSendStatusListener.onSendBegin();
                }

                @Override
                public void onSendPacketEnd(SocketClient client, SocketPacket packet) {
                    if (_isDebug) {
                        Log.d(TAG, "onSendPacketEnd: ");
                    }
                    mSendStatusListener.onSendEnd();
                    mSocketClient.unregisterDataSendingEvent(this);
                }

                @Override
                public void onSendPacketCancel(SocketClient client, SocketPacket packet) {
                    if (_isDebug) {
                        Log.d(TAG, "onSendPacketCancel: ");
                    }
                    mSendStatusListener.onSendCancel();
                    mSocketClient.unregisterDataSendingEvent(this);
                }

                @Override
                public void onSendingPacketInProgress(SocketClient client, SocketPacket packet, float progress, int sendLength) {
                    if (_isDebug) {
                        Log.d(TAG, "onSendingPacketInProgress: ");
                    }
                    mSendStatusListener.onSendInProgress();
                }
            };
        }

        return mSocketClientSendingListener;
    }

    private void __i__setupAddress(SocketClient socketClient) {
        socketClient.getAddress()
                .setRemoteIP(this.ip)
                .setRemotePort(this.port)
                .setConnectionTimeout(this.mConnectionTimeout);
    }

    private void __i__setupEncoding(SocketClient socketClient) {
        socketClient.setCharsetName(CharsetUtil.UTF_8); // 设置编码为UTF-8
    }

    private void __i__setupHeartBeat(SocketClient socketClient) {
        if (mHeartbeatInterval > 0) {
            if (isSendHeartBeatDynamic) {
                socketClient.getHeartBeatHelper().setSendDataBuilder(new SocketHeartBeatHelper.SendDataBuilder() {
                    @Override
                    public byte[] obtainSendHeartBeatData(SocketHeartBeatHelper helper) {
                        return mHeartbeatDynamicBuilder.build();
                    }
                });
            } else {
                socketClient.getHeartBeatHelper().setDefaultSendData(mHeartbeatDataForSend);
            }
            if (isReceiveHeartBeatDynamic) {
                socketClient.getHeartBeatHelper().setReceiveHeartBeatPacketChecker(new SocketHeartBeatHelper.ReceiveHeartBeatPacketChecker() {
                    @Override
                    public boolean isReceiveHeartBeatPacket(SocketHeartBeatHelper helper, SocketResponsePacket packet) {
                        return mHeartBeatChecker.check(packet.getData());
                    }
                });
            } else {
                socketClient.getHeartBeatHelper().setDefaultReceiveData(mHeartbeatDataForReceive);
            }

            socketClient.getHeartBeatHelper().setHeartBeatInterval(mHeartbeatInterval); // 设置自动发送心跳包的间隔时长，单位毫秒
            socketClient.getHeartBeatHelper().setSendHeartBeatEnabled(true); // 设置允许自动发送心跳包，此值默认为false
        }
    }

    private void __i__setupReadByLengthForSender(SocketClient socketClient) {
        if (bytesIndicateDataLength > 0) {
            socketClient.getSocketPacketHelper().setSendPacketLengthDataConverter(new SocketPacketHelper.SendPacketLengthDataConvertor() {
                @Override
                public byte[] obtainSendPacketLengthDataForPacketLength(SocketPacketHelper helper, int packetLength) {
                    return intToBytes(packetLength);
                }
            });
        }

        if (magicNumber != null) {
            socketClient.getSocketPacketHelper().setSendHeaderData(magicNumber);
        }

        if (mSegmentLength > 0) {
            socketClient.getSocketPacketHelper().setSendSegmentLength(mSegmentLength); // 设置发送分段长度，单位byte
            socketClient.getSocketPacketHelper().setSendSegmentEnabled(true); // 设置允许使用分段发送，此值默认为false
        }

        if (mSendTimeOut > 0) {
            socketClient.getSocketPacketHelper().setSendTimeout(mSendTimeOut); // 设置发送超时时长，单位毫秒
            socketClient.getSocketPacketHelper().setSendTimeoutEnabled(true); // 设置允许使用发送超时时长，此值默认为false
        }
    }

    private void __i__setupReadByLengthForReceiver(SocketClient socketClient) {
        if (bytesIndicateDataLength > 0) {
            socketClient.getSocketPacketHelper().setReadStrategy(SocketPacketHelper.ReadStrategy.AutoReadByLength);

            socketClient.getSocketPacketHelper().setReceivePacketLengthDataLength(bytesIndicateDataLength);
            socketClient.getSocketPacketHelper().setReceivePacketDataLengthConvertor(new SocketPacketHelper.ReceivePacketDataLengthConvertor() {
                @Override
                public int obtainReceivePacketDataLength(SocketPacketHelper helper, byte[] packetLengthData) {
                    return byteToInt(packetLengthData);
                }
            });
        }

        if (magicNumber != null) {
            socketClient.getSocketPacketHelper().setReceiveHeaderData(magicNumber);
        }

        if (mReceiveTimeOut > 0) {
            socketClient.getSocketPacketHelper().setReceiveTimeout(mReceiveTimeOut); // 设置接收超时时长，单位毫秒
            socketClient.getSocketPacketHelper().setReceiveTimeoutEnabled(true); // 设置允许使用接收超时时长，此值默认为false
        }
    }

    private byte[] intToBytes(int packetLength) {
        byte[] lengthBytes = new byte[bytesIndicateDataLength];

        //大端
        for (int i = 0; i < bytesIndicateDataLength; i++) {
            lengthBytes[i] = (byte) ((packetLength >> 8 * (bytesIndicateDataLength - 1 - i)) & 0xff);
        }
        return lengthBytes;
    }

    private int byteToInt(byte[] lengthBytes) {
        int length = 0;
        for (int i = 0; i < bytesIndicateDataLength; i++) {
            length += (lengthBytes[i] & 0xff) << (8 * (bytesIndicateDataLength - i - 1));
        }
        return length;
    }

    private byte[] bytesJoint(byte[] frontBytes, byte[] rearBytes) {
        byte[] bytes = new byte[frontBytes.length + rearBytes.length];
        System.arraycopy(frontBytes, 0, bytes, 0, frontBytes.length);
        System.arraycopy(rearBytes, 0, bytes, frontBytes.length, rearBytes.length);
        return bytes;
    }
}
