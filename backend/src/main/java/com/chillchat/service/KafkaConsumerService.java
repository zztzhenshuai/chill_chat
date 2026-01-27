package com.chillchat.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chillchat.entity.GroupMember;
import com.chillchat.entity.Message;
import com.chillchat.mapper.GroupMemberMapper;
import com.chillchat.mapper.MessageMapper;
import com.chillchat.model.ChatMessage;
import com.chillchat.netty.handler.TextWebSocketFrameHandler;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class KafkaConsumerService {

    @Autowired
    private MessageMapper messageMapper;
    
    @Autowired
    private GroupMemberMapper groupMemberMapper;

    // @KafkaListener(topics = "chat-msg-topic", groupId = "chill-chat-group")
    // public void consume(List<ConsumerRecord<String, String>> records) { ... }

    // Direct processing method to bypass Kafka
    public void processMessage(ChatMessage chatMessage) {
        if (chatMessage == null) return;

        try {
            // 1. Persist to MySQL
            Message entity = new Message();
            entity.setSenderId(chatMessage.getSenderId());
            entity.setTargetId(chatMessage.getTargetId());
            entity.setIsGroup(chatMessage.getIsGroup());
            entity.setContent(chatMessage.getContent());
            entity.setCreateTime(new Date(chatMessage.getTimestamp()));
            
            // System.out.println("Saving message: " + JSON.toJSONString(entity));
            messageMapper.insert(entity);

            // 2. Push to WebSocket if local
            String jsonMsg = JSON.toJSONString(chatMessage);
            
            if (chatMessage.getIsGroup()) {
                // Group Logic: Broadcast to all members except sender
                QueryWrapper<GroupMember> query = new QueryWrapper<>();
                query.eq("group_id", chatMessage.getTargetId());
                query.select("user_id"); // optimize query
                List<GroupMember> members = groupMemberMapper.selectList(query);
                
                for (GroupMember member : members) {
                    Long memberId = member.getUserId();
                    if (memberId.equals(chatMessage.getSenderId())) {
                        continue; // Skip sender
                    }
                    
                    Channel channel = TextWebSocketFrameHandler.userChannelMap.get(memberId);
                    if (channel != null && channel.isActive()) {
                        channel.writeAndFlush(new TextWebSocketFrame(jsonMsg));
                    }
                }
            } else {
                // Private Chat
                Channel targetChannel = TextWebSocketFrameHandler.userChannelMap.get(chatMessage.getTargetId());
                if (targetChannel != null && targetChannel.isActive()) {
                    targetChannel.writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(chatMessage)));
                } else {
                     // User offline
                     // System.out.println("User offline: " + chatMessage.getTargetId());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
