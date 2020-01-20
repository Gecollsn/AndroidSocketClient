package com.vilyever.androidsocketclient;

import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.widget.Toast;

import com.vilyever.contextholder.ContextHolder;
import com.vilyever.logger.Logger;
import com.vilyever.socketclient.SocketClient;
import com.vilyever.socketclient.helper.SocketClientDelegate;
import com.vilyever.socketclient.helper.SocketClientReceivingDelegate;
import com.vilyever.socketclient.helper.SocketClientSendingDelegate;
import com.vilyever.socketclient.helper.SocketHeartBeatHelper;
import com.vilyever.socketclient.helper.SocketPacket;
import com.vilyever.socketclient.helper.SocketPacketHelper;
import com.vilyever.socketclient.helper.SocketResponsePacket;
import com.vilyever.socketclient.server.SocketServer;
import com.vilyever.socketclient.server.SocketServerClient;
import com.vilyever.socketclient.server.SocketServerDelegate;
import com.vilyever.socketclient.util.CharsetUtil;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

/**
 * TestServer
 * Created by vilyever on 2016/7/26.
 * Feature:
 */
public class TestServer {
    final TestServer self = this;
    
    
    /* Constructors */
    public TestServer() {

    }
    
    /* Public Methods */
    public void beginListen() {
        int port = getSocketServer().beginListenFromPort(21998);
        Toast.makeText(ContextHolder.getContext(), "port " + port, Toast.LENGTH_LONG).show();
    }
    
    /* Properties */
    private SocketServer socketServer;
    protected SocketServer getSocketServer() {
        if (this.socketServer == null) {
            this.socketServer = new SocketServer();

            __i__setupEncoding(this.socketServer);

            __i__setupConstantHeartBeat(this.socketServer);

//            __i__setupVariableHeartBeat(this.socketServer);

//            __i__setupReadToTrailerForSender(this.socketServer);
//            __i__setupReadToTrailerForReceiver(this.socketServer);

            __i__setupReadByLengthForSender(this.socketServer);
            __i__setupReadByLengthForReceiver(this.socketServer);

//            __i__setupReadManuallyForSender(this.socketServer);
//            __i__setupReadManuallyForReceiver(this.socketServer);

            this.socketServer.registerSocketServerDelegate(new SocketServerDelegate() {
                @Override
                public void onServerBeginListen(SocketServer socketServer, int port) {
                    Logger.log("onServer", "SocketServer: begin listen " + port);

                    getTestClient().connect();
                }

                @Override
                public void onServerStopListen(SocketServer socketServer, int port) {
                    Logger.log("onServer", "SocketServer: stop listen " + port);
                }

                @Override
                public void onClientConnected(SocketServer socketServer, SocketServerClient socketServerClient) {
                    Logger.log("onServer", "SocketServer: onClientConnected");

                    self.setServerListeningSocketServerClient(socketServerClient);
                    socketServerClient.sendString("Server accepted");
                }

                @Override
                public void onClientDisconnected(SocketServer socketServer, SocketServerClient socketServerClient) {
                    Logger.log("onServer", "SocketServer: onClientDisconnected");

                    self.setServerListeningSocketServerClient(null);
                }
            });
        }
        return this.socketServer;
    }

    private SocketServerClient serverListeningSocketServerClient;
    protected TestServer setServerListeningSocketServerClient(SocketServerClient serverListeningSocketServerClient) {
        this.serverListeningSocketServerClient = serverListeningSocketServerClient;
        if (serverListeningSocketServerClient == null) {
            return this;
        }

        this.serverListeningSocketServerClient.registerSocketClientDelegate(new SocketClientDelegate() {
            @Override
            public void onConnected(SocketClient client) {
                Logger.log("onConnected", "SocketServerClient: onConnected");
                /**
                 * 此处永不回调
                 * 在{@link SocketServerDelegate#onClientConnected(SocketServer, SocketServerClient)} 处处理新client接入时的操作
                 */
            }

            @Override
            public void onDisconnected(SocketClient client) {
                Logger.log("onDisconnected", "SocketServerClient: onDisconnected");
            }

            @Override
            public void onResponse(final SocketClient client, @NonNull SocketResponsePacket responsePacket) {
                Logger.log("onResponse", "SocketServerClient: onResponse: " + responsePacket.hashCode() + " 【" + responsePacket.getMessage() + "】 " + " isHeartBeat: " + responsePacket.isHeartBeat() + " " + Arrays.toString(responsePacket.getData()));
                if (responsePacket.isHeartBeat()) {
                    return;
                }
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {
                        try {
                            Thread.sleep(3 * 1000);
                        }
                        catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        client.sendString("server on " + System.currentTimeMillis());

                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void aVoid) {
                        super.onPostExecute(aVoid);

                    }
                }.execute();
            }
        });
        this.serverListeningSocketServerClient.registerSocketClientSendingDelegate(new SocketClientSendingDelegate() {

            @Override
            public void onSendPacketBegin(SocketClient client, SocketPacket packet) {
                Logger.log("onSend", "SocketServerClient: onSendPacketBegin: " + packet.hashCode() + "   " + Arrays.toString(packet.getData()));
            }

            @Override
            public void onSendPacketCancel(SocketClient client, SocketPacket packet) {
                Logger.log("onSend", "SocketServerClient: onSendPacketCancel: " + packet.hashCode());
            }

            @Override
            public void onSendingPacketInProgress(SocketClient client, SocketPacket packet, float progress, int sendedLength) {
                Logger.log("onSend", "SocketServerClient: onSendingPacketInProgress: " + packet.hashCode() + " : " + progress + " : " + sendedLength);
            }

            @Override
            public void onSendPacketEnd(SocketClient client, SocketPacket packet) {
                Logger.log("onSend", "SocketServerClient: onSendPacketEnd: " + packet.hashCode());
            }

        });
        this.serverListeningSocketServerClient.registerSocketClientReceiveDelegate(new SocketClientReceivingDelegate() {
            @Override
            public void onReceivePacketBegin(SocketClient client, SocketResponsePacket packet) {
                Logger.log("onReceive", "SocketServerClient: onReceivePacketBegin: " + packet.hashCode());
            }

            @Override
            public void onReceivePacketEnd(SocketClient client, SocketResponsePacket packet) {
                Logger.log("onReceive", "SocketServerClient: onReceivePacketEnd: " + packet.hashCode());
            }

            @Override
            public void onReceivePacketCancel(SocketClient client, SocketResponsePacket packet) {
                Logger.log("onReceive", "SocketServerClient: onReceivePacketCancel: " + packet.hashCode());
            }

            @Override
            public void onReceivingPacketInProgress(SocketClient client, SocketResponsePacket packet, float progress, int receivedLength) {
                Logger.log("onReceive", "SocketServerClient: onReceivingPacketInProgress: " + packet.hashCode() + " : " + progress + " : " + receivedLength);
            }
        });
        return this;
    }
    protected SocketClient getServerListeningSocketServerClient() {
        return this.serverListeningSocketServerClient;
    }

    private TestClient testClient;
    protected TestClient getTestClient() {
        if (this.testClient == null) {
            this.testClient = new TestClient();
        }
        return this.testClient;
    }
    
    /* Overrides */
    
    
    /* Delegates */
    
    
    /* Private Methods */
    /**
     * 设置自动转换String类型到byte[]类型的编码
     * 如未设置（默认为null），将不能使用{@link SocketClient#sendString(String)}发送消息
     * 如设置为非null（如UTF-8），在接受消息时会自动尝试在接收线程（非主线程）将接收的byte[]数据依照编码转换为String，在{@link SocketResponsePacket#getMessage()}读取
     */
    private void __i__setupEncoding(SocketServer socketServer) {
        socketServer.setCharsetName(CharsetUtil.UTF_8); // 设置编码为UTF-8
    }
    
    private void __i__setupConstantHeartBeat(SocketServer socketServer) {
        /**
         * 设置自动发送的心跳包信息
         */
        socketServer.getHeartBeatHelper().setDefaultSendData(CharsetUtil.stringToData("HeartBeat", CharsetUtil.UTF_8));
        
        /**
         * 设置远程端发送到本地的心跳包信息内容，用于判断接收到的数据包是否是心跳包
         * 通过{@link SocketResponsePacket#isHeartBeat()} 查看数据包是否是心跳包
         */
        socketServer.getHeartBeatHelper().setDefaultReceiveData(CharsetUtil.stringToData("HeartBeat", CharsetUtil.UTF_8));
        socketServer.getHeartBeatHelper().setHeartBeatInterval(10 * 1000); // 设置自动发送心跳包的间隔时长，单位毫秒
        socketServer.getHeartBeatHelper().setSendHeartBeatEnabled(true); // 设置允许自动发送心跳包，此值默认为false
    }
    
    private void __i__setupVariableHeartBeat(SocketServer socketServer) {
        /**
         * 设置自动发送的心跳包信息
         * 此信息动态生成
         *
         * 每次发送心跳包时自动调用
         */
        socketServer.getHeartBeatHelper().setSendDataBuilder(new SocketHeartBeatHelper.SendDataBuilder() {
            @Override
            public byte[] obtainSendHeartBeatData(SocketHeartBeatHelper helper) {
                /**
                 * 使用当前日期作为心跳包
                 */
                byte[] heartBeatPrefix = new byte[]{0x1F, 0x1F};
                byte[] heartBeatSuffix = new byte[]{0x1F, 0x1F};
                
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                byte[] heartBeatInfo = CharsetUtil.stringToData(sdf.format(new Date()), CharsetUtil.UTF_8);
                
                byte[] data = new byte[heartBeatPrefix.length + heartBeatSuffix.length + heartBeatInfo.length];
                System.arraycopy(heartBeatPrefix, 0, data, 0, heartBeatPrefix.length);
                System.arraycopy(heartBeatInfo, 0, data, heartBeatPrefix.length, heartBeatInfo.length);
                System.arraycopy(heartBeatSuffix, 0, data, heartBeatPrefix.length + heartBeatInfo.length, heartBeatSuffix.length);
                
                return data;
            }
        });
        
        /**
         * 设置远程端发送到本地的心跳包信息的检测器，用于判断接收到的数据包是否是心跳包
         * 通过{@link SocketResponsePacket#isHeartBeat()} 查看数据包是否是心跳包
         */
        socketServer.getHeartBeatHelper().setReceiveHeartBeatPacketChecker(new SocketHeartBeatHelper.ReceiveHeartBeatPacketChecker() {
            @Override
            public boolean isReceiveHeartBeatPacket(SocketHeartBeatHelper helper, SocketResponsePacket packet) {
                /**
                 * 判断数据包信息是否含有指定的心跳包前缀和后缀
                 */
                byte[] heartBeatPrefix = new byte[]{0x1F, 0x1F};
                byte[] heartBeatSuffix = new byte[]{0x1F, 0x1F};
                
                if (Arrays.equals(heartBeatPrefix, Arrays.copyOfRange(packet.getData(), 0, heartBeatPrefix.length))
                    && Arrays.equals(heartBeatSuffix, Arrays.copyOfRange(packet.getData(), packet.getData().length - heartBeatSuffix.length, packet.getData().length))) {
                    return true;
                }
                
                return false;
            }
        });
        
        socketServer.getHeartBeatHelper().setHeartBeatInterval(10 * 1000); // 设置自动发送心跳包的间隔时长，单位毫秒
        socketServer.getHeartBeatHelper().setSendHeartBeatEnabled(true); // 设置允许自动发送心跳包，此值默认为false
    }
    
    private void __i__setupReadToTrailerForSender(SocketServer socketServer) {
        /**
         * 根据连接双方协议设置自动发送的包尾数据
         * 每次发送数据包（包括心跳包）都会在发送包内容前自动发送此包尾
         *
         * 例：socketClient.sendData(new byte[]{0x01, 0x02})的步骤为
         * 1. socketClient向远程端发送包头（如果设置了包头信息）
         * 2. socketClient向远程端发送正文数据{0x01, 0x02}
         * 3. socketClient向远程端发送包尾
         *
         * 使用{@link com.vilyever.socketclient.helper.SocketPacketHelper.ReadStrategy.AutoReadToTrailer}必须设置此项
         * 用于分隔多条消息
         */
        socketServer.getSocketPacketHelper().setSendTrailerData(new byte[]{0x13, 0x10});
        
        /**
         * 根据连接双方协议设置自动发送的包头数据
         * 每次发送数据包（包括心跳包）都会在发送包内容前自动发送此包头
         *
         * 若无需包头可删除此行
         */
        socketServer.getSocketPacketHelper().setSendHeaderData(CharsetUtil.stringToData("SocketClient:", CharsetUtil.UTF_8));
        
        /**
         * 设置分段发送数据长度
         * 即在发送指定长度后通过 {@link SocketClientSendingDelegate#onSendingPacketInProgress(SocketClient, SocketPacket, float, int)}回调当前发送进度
         * 注意：无论设置的分段长度为多小，回调的频率最高为1秒30次，防止因此产生主线程的卡顿
         *
         * 若无需进度回调可删除此二行，删除后仍有【发送开始】【发送结束】的回调
         */
        socketServer.getSocketPacketHelper().setSendSegmentLength(8); // 设置发送分段长度，单位byte
        socketServer.getSocketPacketHelper().setSendSegmentEnabled(true); // 设置允许使用分段发送，此值默认为false
        
        /**
         * 设置发送超时时长
         * 在发送每个数据包时，发送每段数据的最长时间，超过后自动断开socket连接
         * 通过设置分段发送{@link SocketPacketHelper#setSendSegmentEnabled(boolean)} 可避免发送大数据包时因超时断开，
         *
         * 若无需限制发送时长可删除此二行
         */
        socketServer.getSocketPacketHelper().setSendTimeout(30 * 1000); // 设置发送超时时长，单位毫秒
        socketServer.getSocketPacketHelper().setSendTimeoutEnabled(true); // 设置允许使用发送超时时长，此值默认为false
    }
    
    private void __i__setupReadToTrailerForReceiver(SocketServer socketServer) {
        /**
         * 设置读取策略为自动读取到指定的包尾
         */
        socketServer.getSocketPacketHelper().setReadStrategy(SocketPacketHelper.ReadStrategy.AutoReadToTrailer);
        
        /**
         * 根据连接双方协议设置的包尾数据
         * 每次接收数据包（包括心跳包）都会在检测接收到与包尾数据相同的byte[]时回调一个数据包
         *
         * 例：自动接收远程端所发送的socketClient.sendData(new byte[]{0x01, 0x02})【{0x01, 0x02}为将要接收的数据】的步骤为
         * 1. socketClient接收包头（如果设置了包头信息）（接收方式为一直读取到与包头相同的byte[],即可能过滤掉包头前的多余信息）
         * 2. socketClient接收正文和包尾（接收方式为一直读取到与尾相同的byte[]）
         * 3. socketClient回调数据包
         *
         * 使用{@link com.vilyever.socketclient.helper.SocketPacketHelper.ReadStrategy.AutoReadToTrailer}必须设置此项
         * 用于分隔多条消息
         */
        socketServer.getSocketPacketHelper().setReceiveTrailerData(new byte[]{0x13, 0x10});
        
        /**
         * 根据连接双方协议设置的包头数据
         * 每次接收数据包（包括心跳包）都会在先接收此包头
         *
         * 若无需包头可删除此行
         */
        socketServer.getSocketPacketHelper().setReceiveHeaderData(CharsetUtil.stringToData("SocketClient:", CharsetUtil.UTF_8));
        
        /**
         * 设置接收超时时长
         * 在指定时长内没有数据到达本地自动断开
         *
         * 若无需限制接收时长可删除此二行
         */
        socketServer.getSocketPacketHelper().setReceiveTimeout(120 * 1000); // 设置接收超时时长，单位毫秒
        socketServer.getSocketPacketHelper().setReceiveTimeoutEnabled(true); // 设置允许使用接收超时时长，此值默认为false
    }
    
    private void __i__setupReadByLengthForSender(SocketServer socketServer) {
        /**
         * 设置包长度转换器
         * 即每次发送数据时，将包头以外的数据长度转换为特定的byte[]发送个远程端用于解析还需要读取多少长度的数据
         *
         * 例：socketClient.sendData(new byte[]{0x01, 0x02})的步骤为
         * 1. socketClient向远程端发送包头（如果设置了包头信息）
         * 2. socketClient要发送的数据为{0x01, 0x02}，长度为2（若设置了包尾，还需加上包尾的字节长度），通过此转换器将int类型的2转换为4字节的byte[]，远程端也照此算法将4字节的byte[]转换为int值
         * 3. socketClient向远程端发送转换后的长度信息byte[]
         * 4. socketClient向远程端发送正文数据{0x01, 0x02}
         * 5. socketClient向远程端发送包尾（如果设置了包尾信息）
         *
         * 此转换器用于第二步
         *
         * 使用{@link com.vilyever.socketclient.helper.SocketPacketHelper.ReadStrategy.AutoReadByLength}必须设置此项
         * 用于分隔多条消息
         */
        socketServer.getSocketPacketHelper().setSendPacketLengthDataConverter(new SocketPacketHelper.SendPacketLengthDataConvertor() {
            @Override
            public byte[] obtainSendPacketLengthDataForPacketLength(SocketPacketHelper helper, int packetLength) {
                /**
                 * 简单将int转换为byte[]
                 */
                byte[] data = new byte[4];
                data[3] = (byte) (packetLength & 0xFF);
                data[2] = (byte) ((packetLength >> 8) & 0xFF);
                data[1] = (byte) ((packetLength >> 16) & 0xFF);
                data[0] = (byte) ((packetLength >> 24) & 0xFF);
                return data;
            }
        });
        
        /**
         * 根据连接双方协议设置自动发送的包头数据
         * 每次发送数据包（包括心跳包）都会在发送包内容前自动发送此包头
         *
         * 若无需包头可删除此行
         */
        socketServer.getSocketPacketHelper().setSendHeaderData(CharsetUtil.stringToData("SocketClient:", CharsetUtil.UTF_8));
        
        /**
         * 根据连接双方协议设置自动发送的包尾数据
         * 每次发送数据包（包括心跳包）都会在发送包内容前自动发送此包尾
         *
         * 若无需包尾可删除此行
         * 注意：
         * 使用{@link com.vilyever.socketclient.helper.SocketPacketHelper.ReadStrategy.AutoReadByLength}时不依赖包尾读取数据
         */
        socketServer.getSocketPacketHelper().setSendTrailerData(new byte[]{0x13, 0x10});
        
        /**
         * 设置分段发送数据长度
         * 即在发送指定长度后通过 {@link SocketClientSendingDelegate#onSendingPacketInProgress(SocketClient, SocketPacket, float, int)}回调当前发送进度
         * 注意：无论设置的分段长度为多小，回调的频率最高为1秒30次，防止因此产生主线程的卡顿
         *
         * 若无需进度回调可删除此二行，删除后仍有【发送开始】【发送结束】的回调
         */
        socketServer.getSocketPacketHelper().setSendSegmentLength(8); // 设置发送分段长度，单位byte
        socketServer.getSocketPacketHelper().setSendSegmentEnabled(true); // 设置允许使用分段发送，此值默认为false
        
        /**
         * 设置发送超时时长
         * 在发送每个数据包时，发送每段数据的最长时间，超过后自动断开socket连接
         * 通过设置分段发送{@link SocketPacketHelper#setSendSegmentEnabled(boolean)} 可避免发送大数据包时因超时断开，
         *
         * 若无需限制发送时长可删除此二行
         */
        socketServer.getSocketPacketHelper().setSendTimeout(30 * 1000); // 设置发送超时时长，单位毫秒
        socketServer.getSocketPacketHelper().setSendTimeoutEnabled(true); // 设置允许使用发送超时时长，此值默认为false
    }
    
    private void __i__setupReadByLengthForReceiver(SocketServer socketServer) {
        /**
         * 设置读取策略为自动读取指定长度
         */
        socketServer.getSocketPacketHelper().setReadStrategy(SocketPacketHelper.ReadStrategy.AutoReadByLength);
        
        /**
         * 设置包长度转换器
         * 即每次接收数据时，将远程端发送到本地的长度信息byte[]转换为int，然后读取相应长度的值
         *
         * 例：自动接收远程端所发送的socketClient.sendData(new byte[]{0x01, 0x02})【{0x01, 0x02}为将要接收的数据】的步骤为
         * 1. socketClient接收包头（如果设置了包头信息）（接收方式为一直读取到与包头相同的byte[],即可能过滤掉包头前的多余信息）
         * 2. socketClient接收长度为{@link SocketPacketHelper#getReceivePacketLengthDataLength()}（此处设置为4）的byte[]，通过下面设置的转换器，将byte[]转换为int值，此int值暂时称为X
         * 3. socketClient接收长度为X的byte[]
         * 4. socketClient接收包尾（如果设置了包尾信息）（接收方式为一直读取到与包尾相同的byte[],如无意外情况，此处不会读取到多余的信息）
         * 5. socketClient回调数据包
         *
         * 此转换器用于第二步
         *
         * 使用{@link com.vilyever.socketclient.helper.SocketPacketHelper.ReadStrategy.AutoReadByLength}必须设置此项
         * 用于分隔多条消息
         */
        socketServer.getSocketPacketHelper().setReceivePacketLengthDataLength(4);
        socketServer.getSocketPacketHelper().setReceivePacketDataLengthConvertor(new SocketPacketHelper.ReceivePacketDataLengthConvertor() {
            @Override
            public int obtainReceivePacketDataLength(SocketPacketHelper helper, byte[] packetLengthData) {
                /**
                 * 简单将byte[]转换为int
                 */
                int length =  (packetLengthData[3] & 0xFF) + ((packetLengthData[2] & 0xFF) << 8) + ((packetLengthData[1] & 0xFF) << 16) + ((packetLengthData[0] & 0xFF) << 24);
                
                return length;
            }
        });
        
        /**
         * 根据连接双方协议设置的包头数据
         * 每次接收数据包（包括心跳包）都会在先接收此包头
         *
         * 若无需包头可删除此行
         */
        socketServer.getSocketPacketHelper().setReceiveHeaderData(CharsetUtil.stringToData("SocketClient:", CharsetUtil.UTF_8));
        
        /**
         * 根据连接双方协议设置的包尾数据
         * 每次接收数据包（包括心跳包）都会在检测接收到与包尾数据相同的byte[]时回调一个数据包
         *
         * 使用{@link com.vilyever.socketclient.helper.SocketPacketHelper.ReadStrategy.AutoReadToTrailer}必须设置此项
         * 用于分隔多条消息
         */
        socketServer.getSocketPacketHelper().setReceiveTrailerData(new byte[]{0x13, 0x10});
        
        /**
         * 设置接收超时时长
         * 在指定时长内没有数据到达本地自动断开
         *
         * 若无需限制接收时长可删除此二行
         */
        socketServer.getSocketPacketHelper().setReceiveTimeout(120 * 1000); // 设置接收超时时长，单位毫秒
        socketServer.getSocketPacketHelper().setReceiveTimeoutEnabled(true); // 设置允许使用接收超时时长，此值默认为false
    }
    
    private void __i__setupReadManuallyForSender(SocketServer socketServer) {
        /**
         * 设置分段发送数据长度
         * 即在发送指定长度后通过 {@link SocketClientSendingDelegate#onSendingPacketInProgress(SocketClient, SocketPacket, float, int)}回调当前发送进度
         * 注意：无论设置的分段长度为多小，回调的频率最高为1秒30次，防止因此产生主线程的卡顿
         *
         * 若无需进度回调可删除此二行，删除后仍有【发送开始】【发送结束】的回调
         */
        socketServer.getSocketPacketHelper().setSendSegmentLength(8); // 设置发送分段长度，单位byte
        socketServer.getSocketPacketHelper().setSendSegmentEnabled(true); // 设置允许使用分段发送，此值默认为false
        
        /**
         * 设置发送超时时长
         * 在发送每个数据包时，发送每段数据的最长时间，超过后自动断开socket连接
         * 通过设置分段发送{@link SocketPacketHelper#setSendSegmentEnabled(boolean)} 可避免发送大数据包时因超时断开，
         *
         * 若无需限制发送时长可删除此二行
         */
        socketServer.getSocketPacketHelper().setSendTimeout(30 * 1000); // 设置发送超时时长，单位毫秒
        socketServer.getSocketPacketHelper().setSendTimeoutEnabled(true); // 设置允许使用发送超时时长，此值默认为false
    }
    
    private void __i__setupReadManuallyForReceiver(SocketServer socketServer) {
        /**
         * 设置读取策略为手动读取
         * 手动读取有两种方法
         * 1. {@link SocketClient#readDataToData(byte[], boolean)} )} 读取到与指定字节相同的字节序列后回调数据包
         * 2. {@link SocketClient#readDataToLength(int)} 读取指定长度的字节后回调数据包
         *
         * 此时SocketPacketHelper中其他读取相关设置将会无效化
         */
        socketServer.getSocketPacketHelper().setReadStrategy(SocketPacketHelper.ReadStrategy.Manually);
    }
}