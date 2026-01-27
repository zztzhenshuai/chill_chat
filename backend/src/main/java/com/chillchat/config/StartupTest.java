package com.chillchat.config;

import com.chillchat.entity.Message;
import com.chillchat.mapper.MessageMapper;
import com.chillchat.model.ChatMessage;
import com.chillchat.model.MessageType;
import com.chillchat.service.KafkaConsumerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class StartupTest implements CommandLineRunner {

    @Autowired
    private MessageMapper messageMapper;
    
    @Autowired
    private KafkaConsumerService kafkaConsumerService;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("--- Startup DB & Service Test ---");
        try {
            long count = messageMapper.selectCount(null);
            System.out.println("Current message count: " + count);
            
            // Test Service Process Message
            ChatMessage chatMsg = new ChatMessage();
            chatMsg.setType(MessageType.CHAT);
            chatMsg.setSenderId(1001L);
            chatMsg.setTargetId(1002L);
            chatMsg.setIsGroup(false);
            chatMsg.setContent("Service Test Message " + new Date());
            chatMsg.setTimestamp(System.currentTimeMillis());
            
            kafkaConsumerService.processMessage(chatMsg);
            
            System.out.println("KafkaConsumerService.processMessage executed successfully.");
        } catch (Exception e) {
            System.err.println("Startup Test Failed!");
            e.printStackTrace();
        }
        System.out.println("---------------------------------");
    }
}
