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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class MessageDispatchService {

    private static final long PENDING_RETRY_INTERVAL_MS = 15000L;
    private static final int MAX_PENDING_RETRY = 5;

    @Autowired
    private MessageMapper messageMapper;
    
    @Autowired
    private GroupMemberMapper groupMemberMapper;

    @Autowired
    private BotService botService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private String dedupKey(String dedupToken) {
        return "msg:dedup:" + dedupToken;
    }

    private String pendingKey(Long userId, Long msgId) {
        return "msg:pending:" + userId + ":" + msgId;
    }

    private String groupMembersKey(Long groupId) {
        return "group:members:" + groupId;
    }

    private String buildDedupToken(ChatMessage msg) {
        if (msg == null) return null;
        if (msg.getId() != null) return String.valueOf(msg.getId());
        Long senderId = msg.getSenderId() == null ? 0L : msg.getSenderId();
        Long targetId = msg.getTargetId() == null ? 0L : msg.getTargetId();
        Boolean isGroup = msg.getIsGroup() != null && msg.getIsGroup();
        Long timestamp = msg.getTimestamp() == null ? 0L : msg.getTimestamp();
        String content = msg.getContent() == null ? "" : msg.getContent();
        return senderId + ":" + targetId + ":" + (isGroup ? 1 : 0) + ":" + timestamp + ":" + content.hashCode();
    }

    private boolean firstSeen(String dedupToken) {
        if (dedupToken == null || dedupToken.isEmpty()) return true;
        try {
            Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(dedupKey(dedupToken), "1", 24, TimeUnit.HOURS);
            return Boolean.TRUE.equals(ok);
        } catch (Exception ignored) {
            return true;
        }
    }

    private void savePending(Long userId, ChatMessage chatMessage, int retries, long nextRetryAt) {
        if (userId == null || chatMessage == null || chatMessage.getId() == null) return;
        try {
            PendingEnvelope envelope = new PendingEnvelope();
            envelope.setMessage(chatMessage);
            envelope.setRetries(retries);
            envelope.setNextRetryAt(nextRetryAt);
            stringRedisTemplate.opsForValue().set(
                    pendingKey(userId, chatMessage.getId()),
                    JSON.toJSONString(envelope),
                    3,
                    TimeUnit.DAYS
            );
        } catch (Exception ignored) {
        }
    }

    public void handleAck(ChatMessage ackMessage) {
        if (ackMessage == null || ackMessage.getSenderId() == null || ackMessage.getId() == null) return;
        try {
            stringRedisTemplate.delete(pendingKey(ackMessage.getSenderId(), ackMessage.getId()));
        } catch (Exception ignored) {
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void retryPendingMessages() {
        try {
            Set<String> keys = stringRedisTemplate.keys("msg:pending:*");
            if (keys == null || keys.isEmpty()) {
                return;
            }

            long now = System.currentTimeMillis();
            for (String key : keys) {
                String value = stringRedisTemplate.opsForValue().get(key);
                if (value == null || value.isEmpty()) {
                    continue;
                }

                PendingEnvelope envelope;
                try {
                    envelope = JSON.parseObject(value, PendingEnvelope.class);
                } catch (Exception ex) {
                    continue;
                }

                if (envelope == null || envelope.getMessage() == null) {
                    continue;
                }

                if (envelope.getRetries() >= MAX_PENDING_RETRY) {
                    continue;
                }

                if (envelope.getNextRetryAt() > now) {
                    continue;
                }

                ChatMessage msg = envelope.getMessage();
                Long targetUserId = extractTargetUserId(key, msg);
                if (targetUserId == null) {
                    continue;
                }

                Channel channel = TextWebSocketFrameHandler.userChannelMap.get(targetUserId);
                if (channel != null && channel.isActive()) {
                    channel.writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(msg)));
                }

                envelope.setRetries(envelope.getRetries() + 1);
                envelope.setNextRetryAt(now + PENDING_RETRY_INTERVAL_MS);
                stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(envelope), 3, TimeUnit.DAYS);
            }
        } catch (Exception ignored) {
        }
    }

    private Long extractTargetUserId(String pendingKey, ChatMessage message) {
        if (pendingKey != null) {
            String[] arr = pendingKey.split(":");
            if (arr.length >= 4) {
                try {
                    return Long.parseLong(arr[2]);
                } catch (Exception ignored) {
                }
            }
        }
        if (message != null && Boolean.FALSE.equals(message.getIsGroup())) {
            return message.getTargetId();
        }
        return null;
    }

    private List<Long> getGroupMemberIds(Long groupId) {
        if (groupId == null) return java.util.Collections.emptyList();

        try {
            Set<String> cached = stringRedisTemplate.opsForSet().members(groupMembersKey(groupId));
            if (cached != null && !cached.isEmpty()) {
                return cached.stream().map(Long::parseLong).toList();
            }
        } catch (Exception ignored) {
        }

        QueryWrapper<GroupMember> query = new QueryWrapper<>();
        query.eq("group_id", groupId);
        query.select("user_id");
        List<GroupMember> members = groupMemberMapper.selectList(query);
        List<Long> memberIds = members.stream().map(GroupMember::getUserId).toList();

        if (!memberIds.isEmpty()) {
            try {
                String key = groupMembersKey(groupId);
                String[] payload = memberIds.stream().map(String::valueOf).toArray(String[]::new);
                stringRedisTemplate.opsForSet().add(key, payload);
                stringRedisTemplate.expire(key, 1, TimeUnit.HOURS);
            } catch (Exception ignored) {
            }
        }
        return memberIds;
    }

    public void processMessage(ChatMessage chatMessage) {
        if (chatMessage == null) return;

        try {
            String dedupToken = buildDedupToken(chatMessage);
            if (!firstSeen(dedupToken)) {
                return;
            }

            if (chatMessage.getTimestamp() == null) {
                chatMessage.setTimestamp(System.currentTimeMillis());
            }

            // 1. Persist to MySQL
            Message entity = new Message();
            entity.setSenderId(chatMessage.getSenderId());
            entity.setTargetId(chatMessage.getTargetId());
            entity.setIsGroup(chatMessage.getIsGroup());
            entity.setContent(chatMessage.getContent());
            entity.setCreateTime(new Date(chatMessage.getTimestamp()));
            messageMapper.insert(entity);
            chatMessage.setId(entity.getId());

            // 2. Push to WebSocket
            String jsonMsg = JSON.toJSONString(chatMessage);
            
            if (chatMessage.getIsGroup()) {
                // Group: broadcast to all members except sender
                List<Long> members = getGroupMemberIds(chatMessage.getTargetId());
                for (Long memberId : members) {
                    if (memberId.equals(chatMessage.getSenderId())) continue;
                    savePending(memberId, chatMessage, 0, System.currentTimeMillis() + PENDING_RETRY_INTERVAL_MS);
                    Channel channel = TextWebSocketFrameHandler.userChannelMap.get(memberId);
                    if (channel != null && channel.isActive()) {
                        channel.writeAndFlush(new TextWebSocketFrame(jsonMsg));
                    }
                }
            } else {
                // Private chat
                savePending(chatMessage.getTargetId(), chatMessage, 0, System.currentTimeMillis() + PENDING_RETRY_INTERVAL_MS);
                Channel targetChannel = TextWebSocketFrameHandler.userChannelMap.get(chatMessage.getTargetId());
                if (targetChannel != null && targetChannel.isActive()) {
                    targetChannel.writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(chatMessage)));
                }
            }

            // 3. Trigger Bot Response (Async)
            botService.handleBotResponse(chatMessage);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @lombok.Data
    public static class PendingEnvelope {
        private ChatMessage message;
        private int retries;
        private long nextRetryAt;
    }
}
