package com.jadyer.seed.server.core;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.apache.mina.filter.codec.demux.MessageEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Server端协议编码器
 * <p>
 *     用于编码响应给Client的报文
 * </p>
 * Created by 玄玉<https://jadyer.cn/> on 2012/12/21 13:28.
 */
@Component
public class ServerProtocolEncoder implements MessageEncoder<String> {
    @Override
    public void encode(IoSession session, String message, ProtocolEncoderOutput out) throws Exception {
        IoBuffer buffer = IoBuffer.allocate(100).setAutoExpand(true);
        buffer.putString(message, StandardCharsets.UTF_8.newEncoder());
        buffer.flip();
        out.write(buffer);
    }
}