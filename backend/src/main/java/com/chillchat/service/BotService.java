package com.chillchat.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chillchat.entity.Message;
import com.chillchat.entity.User;
import com.chillchat.mapper.MessageMapper;
import com.chillchat.mapper.UserMapper;
import com.chillchat.model.ChatMessage;
import com.chillchat.model.MessageType;
import com.chillchat.netty.handler.TextWebSocketFrameHandler;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Service
public class BotService implements InitializingBean {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private com.chillchat.mapper.FriendMapper friendMapper;

    @Autowired
    private MessageMapper messageMapper;

    private static final String API_KEY = "sk-dfe51496b73a4dab81bc5d268f2aba0c";
    // Use standard compatible endpoint
    private static final String API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    // User requested specific model
    private static final String MODEL_NAME = "qwen-flash"; // Fallback to safe name if needed, usually 'qwen-turbo' or 'qwen-plus'. Checking availability of 'character' suffix. Assuming user knows. 
    // Actually, allowing user input:
    private String modelName = "qwen-flash"; 

    private Long botId;

    @Override
    public void afterPropertiesSet() throws Exception {
        init();
    }

    public void init() {
        // Ensure ChillBot user exists
        QueryWrapper<User> query = new QueryWrapper<>();
        query.eq("username", "ChillBot");
        User bot = userMapper.selectOne(query);
        
        if (bot == null) {
            bot = new User();
            bot.setUsername("ChillBot");
            // Dummy password
            bot.setPassword("123456");
            // A nice avatar
            bot.setAvatar("https://api.dicebear.com/9.x/bottts/svg?seed=ChillBot&backgroundColor=b6e3f4");
            bot.setSignature("我是你的AI知心好友，随时倾听你的心声。");
            bot.setGender(0);
            bot.setCreateTime(new Date());
            
            userMapper.insert(bot);
            System.out.println("Initialized ChillBot with ID: " + bot.getId());
        } else {
            System.out.println("Found existing ChillBot with ID: " + bot.getId());
        }
        this.botId = bot.getId();
        
        // Ensure ALL users are friends with the bot
        ensureAllUsersAreFriendsWithBot();
    }

    private void ensureAllUsersAreFriendsWithBot() {
        if (botId == null) return;
        
        // 1. Get all users
        List<User> allUsers = userMapper.selectList(null);
        
        int count = 0;
        for (User user : allUsers) {
            if (user.getId().equals(botId)) continue;
            
            // Check if friend exists (User -> Bot)
            QueryWrapper<com.chillchat.entity.Friend> query = new QueryWrapper<>();
            query.eq("user_id", user.getId()).eq("friend_id", botId);
            if (friendMapper.selectCount(query) == 0) {
                // Add friend
                com.chillchat.entity.Friend f1 = new com.chillchat.entity.Friend();
                f1.setUserId(user.getId());
                f1.setFriendId(botId);
                friendMapper.insert(f1);
                
                // Add reverse friend (Bot -> User) usually required for logic, though bot doesn't initiate
                com.chillchat.entity.Friend f2 = new com.chillchat.entity.Friend();
                f2.setUserId(botId);
                f2.setFriendId(user.getId());
                friendMapper.insert(f2);
                
                count++;
            }
        }
        if (count > 0) {
            System.out.println("Added ChillBot as friend for " + count + " users.");
        }
    }

    public boolean isBot(Long userId) {
        return botId != null && botId.equals(userId);
    }

    @Async
    public void handleBotResponse(ChatMessage userMsg) {
        // Determine if message is TO the bot
        if (!isBot(userMsg.getTargetId())) {
            return;
        }

        Long userId = userMsg.getSenderId();

        try {
            // 1. Fetch Chat History (Last 20 messages between User and Bot)
            QueryWrapper<Message> query = new QueryWrapper<>();
            query.and(wrapper -> wrapper
                    .nested(i -> i.eq("sender_id", userId).eq("target_id", botId))
                    .or()
                    .nested(i -> i.eq("sender_id", botId).eq("target_id", userId))
            ).orderByDesc("create_time").last("limit 20");

            List<Message> history = messageMapper.selectList(query);
            // Revert to chronological order
            Collections.reverse(history);

            // 2. Build Request Body
            JSONObject payload = new JSONObject();
            // User requested "qwen-flash-character"
            payload.put("model", "qwen-flash-character"); 
            
            JSONArray messages = new JSONArray();
            
            // System Prompt
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", "你是一个知心好友，名字叫ChillBot。通过用户的聊天内容，理解用户的情绪，并给出温暖、贴心的回应。请不要长篇大论，像正常朋友聊天一样回复。");
            messages.add(systemMsg);

            // History
            for (Message m : history) {
                JSONObject item = new JSONObject();
                if (isBot(m.getSenderId())) {
                    item.put("role", "assistant");
                } else {
                    item.put("role", "user");
                }
                item.put("content", m.getContent());
                messages.add(item);
            }
            
            payload.put("messages", messages);

            // 3. Send Request
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(API_URL))
                    .header("Authorization", "Bearer " + API_KEY)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String responseBody = response.body();
                JSONObject jsonResponse = JSON.parseObject(responseBody);
                
                // Parse Content
                String botReplyContent = "";
                if (jsonResponse.containsKey("choices")) {
                    JSONArray choices = jsonResponse.getJSONArray("choices");
                    if (!choices.isEmpty()) {
                        botReplyContent = choices.getJSONObject(0).getJSONObject("message").getString("content");
                    }
                }

                if (!botReplyContent.isEmpty()) {
                    // 4. Save to DB
                    Message replyMsg = new Message();
                    replyMsg.setSenderId(botId);
                    replyMsg.setTargetId(userId);
                    replyMsg.setIsGroup(false);
                    replyMsg.setContent(botReplyContent);
                    replyMsg.setCreateTime(new Date());
                    messageMapper.insert(replyMsg);

                    // 5. Send via WebSocket
                    ChatMessage wsMsg = new ChatMessage();
                    wsMsg.setType(MessageType.CHAT);
                    wsMsg.setSenderId(botId);
                    wsMsg.setTargetId(userId);
                    wsMsg.setContent(botReplyContent);
                    wsMsg.setTimestamp(System.currentTimeMillis());
                    
                    Channel userChannel = TextWebSocketFrameHandler.userChannelMap.get(userId);
                    if (userChannel != null && userChannel.isActive()) {
                        userChannel.writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(wsMsg)));
                    }
                }
            } else {
                System.err.println("Bot API Error: " + response.statusCode() + " " + response.body());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
