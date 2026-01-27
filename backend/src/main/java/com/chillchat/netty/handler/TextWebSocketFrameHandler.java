package com.chillchat.netty.handler;

import com.alibaba.fastjson2.JSON;
import com.chillchat.model.ChatMessage;
import com.chillchat.service.KafkaConsumerService; // Used as Direct Message Service now
// import com.chillchat.service.KafkaProducerService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ChannelHandler.Sharable
public class TextWebSocketFrameHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    @Autowired
    private KafkaConsumerService messageService; // Was kafkaProducerService

    // Mapping: UserId -> Channel
    public static final Map<Long, Channel> userChannelMap = new ConcurrentHashMap<>();
    // Mapping: ChannelId -> UserId (for cleanup)
    private static final Map<String, Long> channelUserMap = new ConcurrentHashMap<>();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        try {
            String text = frame.text();
            // System.out.println("Received: " + text); 
            ChatMessage message = JSON.parseObject(text, ChatMessage.class);

            if (message == null) return;

            switch (message.getType()) {
                case CONNECT:
                    handleConnect(ctx, message);
                    break;
                case CHAT:
                    handleChat(message);
                    break;
                case PING:
                    ctx.channel().writeAndFlush(new TextWebSocketFrame("{\"type\":\"PONG\"}"));
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace(); // Print error to console
        }
    }

    private void handleConnect(ChannelHandlerContext ctx, ChatMessage message) {
        Long userId = message.getSenderId(); // Assume senderId is the connecting user
        if (userId != null) {
            userChannelMap.put(userId, ctx.channel());
            channelUserMap.put(ctx.channel().id().asLongText(), userId);
            System.out.println("User connected: " + userId);
        }
    }

    private void handleChat(ChatMessage message) {
        // Send directly to service, bypassing Kafka
        messageService.processMessage(message);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String channelId = ctx.channel().id().asLongText();
        Long userId = channelUserMap.remove(channelId);
        if (userId != null) {
            userChannelMap.remove(userId);
            System.out.println("User disconnected: " + userId);
        }
        super.channelInactive(ctx);
    }
    
    @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
