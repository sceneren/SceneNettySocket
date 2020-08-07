package wiki.scene.socket.handler;

import android.text.TextUtils;
import android.util.Log;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import wiki.scene.socket.listener.NettyClientListener;
import wiki.scene.socket.status.ConnectState;


public class NettyClientHandler extends SimpleChannelInboundHandler<String> {

    private static final String TAG = "NettyClientHandler";
    private final boolean isSendHeartBeat;
    private NettyClientListener listener;
    private int index;
    private Object heartBeatData;
    private String packetSeparator;
    private String startPacketSeparator;

    //    private static final ByteBuf HEARTBEAT_SEQUENCE = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer("Heartbeat"+System.getProperty("line.separator"),
//            CharsetUtil.UTF_8));
    byte[] requestBody = {(byte) 0xFE, (byte) 0xED, (byte) 0xFE, 5, 4, (byte) 0xFF, 0x0a};


    public NettyClientHandler(NettyClientListener listener, int index, boolean isSendHeartBeat, Object heartBeatData) {
        this(listener, index, isSendHeartBeat, heartBeatData, null);
    }

    public NettyClientHandler(NettyClientListener listener, int index, boolean isSendHeartBeat, Object heartBeatData, String separator) {
        this(listener, index, isSendHeartBeat, heartBeatData, separator, null);
    }

    public NettyClientHandler(NettyClientListener listener, int index, boolean isSendHeartBeat, Object heartBeatData, String separator, String startPacketSeparator) {
        this.listener = listener;
        this.index = index;
        this.isSendHeartBeat = isSendHeartBeat;
        this.heartBeatData = heartBeatData;
        this.packetSeparator = TextUtils.isEmpty(separator) ? System.getProperty("line.separator") : separator;
        this.startPacketSeparator = TextUtils.isEmpty(startPacketSeparator) ? "" : startPacketSeparator;
    }

    /**
     * <p>设定IdleStateHandler心跳检测每x秒进行一次读检测，
     * 如果x秒内ChannelRead()方法未被调用则触发一次userEventTrigger()方法 </p>
     *
     * @param ctx ChannelHandlerContext
     * @param evt IdleStateEvent
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.WRITER_IDLE) {   //发送心跳
                if (isSendHeartBeat) {
                    if (heartBeatData == null) {
                        ctx.channel().writeAndFlush(startPacketSeparator + "Heartbeat" + packetSeparator);
                    } else {
                        if (heartBeatData instanceof String) {
                            ctx.channel().writeAndFlush(startPacketSeparator + heartBeatData + packetSeparator);
                        } else if (heartBeatData instanceof byte[]) {
                            ByteBuf buf = Unpooled.copiedBuffer((byte[]) heartBeatData);
                            ctx.channel().writeAndFlush(buf);
                        } else {
                            Log.e(TAG, "userEventTriggered: heartBeatData type error");
                        }
                    }
                } else {
                    Log.e(TAG, "不发送心跳");
                }
            }
        }
    }

    /**
     * <p>客户端上线</p>
     *
     * @param ctx ChannelHandlerContext
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        Log.e(TAG, "channelActive");
        ctx.channel().config().setWriteBufferHighWaterMark(10 * 1024 * 1024);
        if (listener != null) {
            listener.onClientStatusConnectChanged(ConnectState.STATUS_CONNECT_SUCCESS, index);
        }
    }

    /**
     * <p>客户端下线</p>
     *
     * @param ctx ChannelHandlerContext
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Log.e(TAG, "channelInactive");
    }

    /**
     * 客户端收到消息
     *
     * @param channelHandlerContext ChannelHandlerContext
     * @param msg                   消息
     */
    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, String msg) {
        Log.e(TAG, "channelRead0:" + msg);
        if (listener != null) {
            listener.onMessageResponseClient(msg, index);
        }
    }

    /**
     * @param ctx   ChannelHandlerContext
     * @param cause 异常
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Close the connection when an exception is raised.
//        NettyTcpClient.getInstance().setConnectStatus(false);
        Log.e(TAG, "exceptionCaught");
        if (listener != null) {
            listener.onClientStatusConnectChanged(ConnectState.STATUS_CONNECT_ERROR, index);
        }
        cause.printStackTrace();
        ctx.close();
    }
}
