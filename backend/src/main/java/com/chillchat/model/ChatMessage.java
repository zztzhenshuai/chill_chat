package com.chillchat.model;

import lombok.Data;

@Data
public class ChatMessage {
    private MessageType type;
    private Long id; // msgId
    private Long senderId;
    private Long targetId; // userId or groupId
    private Boolean isGroup;
    private String content; // text or image url
    private Long timestamp;
}
