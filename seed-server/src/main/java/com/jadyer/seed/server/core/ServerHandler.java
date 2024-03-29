package com.jadyer.seed.server.core;

import com.jadyer.seed.comm.util.ByteUtil;
import com.jadyer.seed.comm.util.LogUtil;
import com.jadyer.seed.server.helper.MessageBuilder;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

/**
 * 业务分发类
 * <p>
 *     用于将收到的请求报文解码后的数据分发到具体的业务处理类
 * </p>
 * Created by 玄玉<https://jadyer.cn/> on 2012/12/22 19:23.
 */
public class ServerHandler extends IoHandlerAdapter {
    //装载业务码和与之对应的接口业务实现类
    private Map<String, GenericAction> busiProcessMap = new HashMap<>();

    public void setBusiProcessMap(Map<String, GenericAction> busiProcessMap) {
        this.busiProcessMap = busiProcessMap;
    }

    @Override
    public void messageReceived(IoSession session, Object message) {
        String respData;
        Token token = (Token)message;
        if("10005".equals(token.getBusiCode())){
            LogUtil.getWapLogger();
        }else if("/notify_".startsWith(token.getBusiCode())){
            LogUtil.getWebLogger();
        }
        byte[] msgbytes;
        try {
            msgbytes = token.getFullMessage().getBytes(token.getBusiCharset());
        } catch (UnsupportedEncodingException e) {
            LogUtil.getLogger().warn("将接收到的完整报文转为byte[]时发生异常：Unsupported Encoding-->[" + token.getBusiCharset() + "]，转为使用默认字符集转码");
            msgbytes = token.getFullMessage().getBytes();
        }
        LogUtil.getLogger().info("渠道:"+token.getBusiType()+"  交易码:"+token.getBusiCode()+"  完整报文(HEX):"+ ByteUtil.buildHexStringWithASCII(msgbytes));
        StringBuilder sb = new StringBuilder();
        sb.append("\r\n------------------------------------------------------------------------------------------");
        sb.append("\r\n【通信双方】").append(session);
        sb.append("\r\n【收发标识】Receive");
        sb.append("\r\n【报文内容】").append(token.getFullMessage());
        sb.append("\r\n------------------------------------------------------------------------------------------");
        LogUtil.getLogger().info(sb.toString());
        if("/".equals(token.getBusiCode())){
            respData = MessageBuilder.buildHTTPResponseMessage(MessageBuilder.getServerStatus());
        }else if("/favicon.ico".equals(token.getBusiCode())){
            respData = MessageBuilder.buildHTTPResponseMessage("<link rel=\"icon\" href=\"https://raw.githubusercontent.com/jadyer/seed/master/seed-scs/src/main/webapp/favicon.ico\" type=\"image/x-icon\"/>\n<link rel=\"shortcut icon\" href=\"https://raw.githubusercontent.com/jadyer/seed/master/seed-scs/src/main/webapp/favicon.ico\" type=\"image/x-icon\"/>");
        }else if(this.busiProcessMap.keySet().contains(token.getBusiCode())){
            respData = this.busiProcessMap.get(token.getBusiCode()).execute(token.getBusiMessage());
        }else{
            switch (token.getBusiType()) {
                case Token.BUSI_TYPE_TCP:
                    respData = "ILLEGAL_REQUEST";
                    break;
                case Token.BUSI_TYPE_HTTP:
                    respData = MessageBuilder.buildHTTPResponseMessage(501, null);
                    break;
                default:
                    respData = "UNKNOWN_REQUEST";
            }
        }
        sb.setLength(0);
        sb.append("\r\n------------------------------------------------------------------------------------------");
        sb.append("\r\n【通信双方】").append(session);
        sb.append("\r\n【收发标识】Response");
        sb.append("\r\n【报文内容】").append(respData);
        sb.append("\r\n------------------------------------------------------------------------------------------");
        LogUtil.getLogger().info(sb.toString());
        session.write(respData);
    }

    @Override
    public void messageSent(IoSession session, Object message) throws Exception {
        LogUtil.getLogger().info("已回应给Client...");
        if(session != null){
            //session.close(true);
            session.closeOnFlush();
        }
    }

    @Override
    public void sessionIdle(IoSession session, IdleStatus status){
        LogUtil.getLogger().info("请求进入闲置状态...回路即将关闭...");
        //session.close(true);
        session.closeNow();
    }

    @Override
    public void exceptionCaught(IoSession session, Throwable cause){
        LogUtil.getLogger().error("请求处理遇到异常...回路即将关闭...", cause);
        //session.close(true);
        session.closeOnFlush();
    }
}