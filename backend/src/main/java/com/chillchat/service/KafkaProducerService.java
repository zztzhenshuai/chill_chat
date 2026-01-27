package com.chillchat.service;

import com.alibaba.fastjson2.JSON;
import com.chillchat.model.ChatMessage;
import org.springframework.stereotype.Service;

@Service
public class KafkaProducerService {

    // @Autowired
    // private KafkaTemplate<String, String> kafkaTemplate;

    private static final String TOPIC = "chat-msg-topic";

    public void sendMessage(ChatMessage message) {
        // Disabled for direct mode
        /*
        String key = message.getIsGroup() ? "GROUP_" + message.getTargetId() 
                                          : "USER_" + Math.min(message.getSenderId(), message.getTargetId()) + "_" + Math.max(message.getSenderId(), message.getTargetId());
        
        kafkaTemplate.send(TOPIC, key, JSON.toJSONString(message));
        */
    }
}
