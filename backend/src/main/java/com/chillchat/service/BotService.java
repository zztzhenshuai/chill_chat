package com.chillchat.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chillchat.entity.*;
import com.chillchat.mapper.*;
import com.chillchat.model.ChatMessage;
import com.chillchat.model.MessageType;
import com.chillchat.netty.handler.TextWebSocketFrameHandler;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class BotService implements InitializingBean {

    // ─── Dependencies ────────────────────────────────────────────────────────

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FriendMapper friendMapper;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private MessageEmbeddingMapper messageEmbeddingMapper;

    // ─── Config ──────────────────────────────────────────────────────────────

    @Value("${dashscope.api-key}")
    private String apiKey;

    // ─── Constants ───────────────────────────────────────────────────────────

    private static final String API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final String MODEL_NAME = "qwen-turbo";

    private static final String SYSTEM_PROMPT =
            "你是一个知心好友，名字叫ChillBot。通过用户的聊天内容，理解用户的情绪，" +
            "并给出温暖、贴心的回应。请不要长篇大论，像正常朋友聊天一样回复。\n\n" +
            "你拥有以下工具函数，通过 function calling 机制调用：\n" +
            "- get_friend_list：查看用户好友列表\n" +
            "- send_message：代用户给好友发私信\n" +
            "- create_post：代用户在广场发帖\n\n" +
            "【强制规则】\n" +
            "1. 当用户请求帮他发帖、发消息或查看好友时，你必须发出 function call（工具调用），" +
            "不得用文字描述「已成功...」等模拟执行的回复。\n" +
            "2. 工具调用由后端系统处理，你只负责发出调用请求，不要在文字中描述执行过程或结果。\n" +
            "3. 如果没有用户明确指示的操作，正常聊天回复即可，无需调用工具。";

    private static final String TOOLS_JSON = "["
            + "{\"type\":\"function\",\"function\":{"
            +   "\"name\":\"get_friend_list\","
            +   "\"description\":\"查看当前用户的好友列表，返回好友用户名列表。\","
            +   "\"parameters\":{\"type\":\"object\",\"properties\":{},\"required\":[]}"
            + "}},"
            + "{\"type\":\"function\",\"function\":{"
            +   "\"name\":\"send_message\","
            +   "\"description\":\"以用户自己的名义给某个好友发送私信。执行前系统会请求用户确认。\","
            +   "\"parameters\":{\"type\":\"object\","
            +     "\"properties\":{"
            +       "\"friend_name\":{\"type\":\"string\",\"description\":\"好友的用户名\"},"
            +       "\"content\":{\"type\":\"string\",\"description\":\"要发送的消息内容\"}"
            +     "},"
            +     "\"required\":[\"friend_name\",\"content\"]"
            +   "}"
            + "}},"
            + "{\"type\":\"function\",\"function\":{"
            +   "\"name\":\"create_post\","
            +   "\"description\":\"以用户自己的名义在广场发布帖子。执行前系统会请求用户确认。\","
            +   "\"parameters\":{\"type\":\"object\","
            +     "\"properties\":{"
            +       "\"content\":{\"type\":\"string\",\"description\":\"帖子的文字内容\"}"
            +     "},"
            +     "\"required\":[\"content\"]"
            +   "}"
            + "}}"
            + "]";

    private Long botId;

    // ─── Redis key helpers ───────────────────────────────────────────────────

    private String pendingKey(Long userId) {
        return "bot:pending_action:" + userId;
    }

    private String postLikeCountKey(Long postId) {
        return "post:like:cnt:" + postId;
    }

    private String postCommentCountKey(Long postId) {
        return "post:comment:cnt:" + postId;
    }

    // ─── Initialisation ──────────────────────────────────────────────────────

    @Override
    public void afterPropertiesSet() throws Exception {
        init();
    }

    public void init() {
        QueryWrapper<User> query = new QueryWrapper<>();
        query.eq("username", "ChillBot");
        User bot = userMapper.selectOne(query);

        if (bot == null) {
            bot = new User();
            bot.setUsername("ChillBot");
            bot.setPassword("123456");
            bot.setAvatar("https://api.dicebear.com/9.x/bottts/svg?seed=ChillBot&backgroundColor=b6e3f4");
            bot.setSignature("我是你的AI知心好友，随时倾听你的心声。");
            bot.setGender(0);
            bot.setCreateTime(new Date());
            userMapper.insert(bot);
            System.out.println("[BotService] Initialized ChillBot with ID: " + bot.getId());
        } else {
            System.out.println("[BotService] Found existing ChillBot with ID: " + bot.getId());
        }
        this.botId = bot.getId();
        ensureAllUsersAreFriendsWithBot();
    }

    private void ensureAllUsersAreFriendsWithBot() {
        if (botId == null) return;
        List<User> allUsers = userMapper.selectList(null);
        int count = 0;
        for (User user : allUsers) {
            if (user.getId().equals(botId)) continue;
            QueryWrapper<Friend> q = new QueryWrapper<>();
            q.eq("user_id", user.getId()).eq("friend_id", botId);
            if (friendMapper.selectCount(q) == 0) {
                Friend f1 = new Friend();
                f1.setUserId(user.getId());
                f1.setFriendId(botId);
                friendMapper.insert(f1);

                Friend f2 = new Friend();
                f2.setUserId(botId);
                f2.setFriendId(user.getId());
                friendMapper.insert(f2);
                count++;
            }
        }
        if (count > 0) {
            System.out.println("[BotService] Added ChillBot as friend for " + count + " users.");
        }
    }

    public boolean isBot(Long userId) {
        return botId != null && botId.equals(userId);
    }

    // ─── Main handler ────────────────────────────────────────────────────────

    @Async
    public void handleBotResponse(ChatMessage userMsg) {
        if (!isBot(userMsg.getTargetId())) return;

        Long userId = userMsg.getSenderId();

        try {
            // ── STEP 1: Check for a pending confirmation ──────────────────────
            String pendingJson = stringRedisTemplate.opsForValue().get(pendingKey(userId));
            if (pendingJson != null) {
                String incoming = userMsg.getContent().trim();
                if (incoming.contains("确认") || incoming.equalsIgnoreCase("ok")
                        || incoming.equalsIgnoreCase("yes")) {
                    stringRedisTemplate.delete(pendingKey(userId));
                    JSONObject action = JSON.parseObject(pendingJson);
                    String result = executeToolAction(userId, action);
                    sendBotMessage(userId, result);
                    return;
                } else if (incoming.contains("取消") || incoming.equalsIgnoreCase("cancel")
                        || incoming.equalsIgnoreCase("no")) {
                    stringRedisTemplate.delete(pendingKey(userId));
                    sendBotMessage(userId, "好的，操作已取消。");
                    return;
                } else {
                    stringRedisTemplate.delete(pendingKey(userId));
                }
            }

            // ── STEP 2: Find the user message ID saved by KafkaConsumerService ─
            Long userMsgId = findRecentUserMessage(userId, userMsg.getContent());

            // ── STEP 3: Build hybrid context ──────────────────────────────────
            List<Message> recentHistory = fetchRecentHistory(userId, 10);
            List<Message> semanticHistory = retrieveSemanticHistory(userId, userMsg.getContent(), 8);
            List<Message> context = mergeAndDeduplicate(semanticHistory, recentHistory);

            // ── STEP 4: Agent loop (max 5 iterations) ─────────────────────────
            JSONArray messages = buildMessages(context, userMsg.getContent());
            JSONArray tools = JSON.parseArray(TOOLS_JSON);

            for (int iter = 0; iter < 5; iter++) {
                JSONObject llmResponse = callLLM(messages, tools);
                if (llmResponse == null) break;

                String finishReason = llmResponse.getString("finish_reason");
                JSONObject message = llmResponse.getJSONObject("message");

                // 检测幻觉执行：模型在文本中模拟了工具结果，强制重试
                if (!"tool_calls".equals(finishReason) && iter == 0) {
                    String preliminary = message.getString("content");
                    if (looksLikeFakeToolExecution(preliminary)) {
                        System.out.println("[BotService] Detected hallucinated tool execution, retrying with tool_choice=required");
                        llmResponse = callLLM(messages, tools, "required");
                        if (llmResponse == null) break;
                        finishReason = llmResponse.getString("finish_reason");
                        message = llmResponse.getJSONObject("message");
                    }
                }

                if ("tool_calls".equals(finishReason)) {
                    JSONArray toolCalls = message.getJSONArray("tool_calls");
                    if (toolCalls == null || toolCalls.isEmpty()) break;

                    JSONObject toolCall = toolCalls.getJSONObject(0);
                    String toolName = toolCall.getJSONObject("function").getString("name");

                    if ("get_friend_list".equals(toolName)) {
                        String friendList = executeFriendList(userId);

                        JSONObject assistantTurn = new JSONObject();
                        assistantTurn.put("role", "assistant");
                        assistantTurn.put("tool_calls", toolCalls);
                        messages.add(assistantTurn);

                        JSONObject toolResult = new JSONObject();
                        toolResult.put("role", "tool");
                        toolResult.put("tool_call_id", toolCall.getString("id"));
                        toolResult.put("content", friendList);
                        messages.add(toolResult);

                        continue;
                    } else {
                        stringRedisTemplate.opsForValue().set(
                                pendingKey(userId),
                                toolCall.toString(),
                                5, TimeUnit.MINUTES
                        );
                        String confirmMsg = buildConfirmMessage(toolName, toolCall);
                        sendBotMessage(userId, confirmMsg);
                        return;
                    }
                } else {
                    String replyContent = message.getString("content");
                    if (replyContent != null && !replyContent.isBlank()) {
                        saveAndPush(userId, replyContent);
                        if (userMsgId != null) {
                            embeddingService.asyncEmbedAndStore(userMsgId, userId, userMsg.getContent());
                        }
                    }
                    return;
                }
            }

        } catch (Exception e) {
            System.err.println("[BotService] handleBotResponse error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ─── LLM call ────────────────────────────────────────────────────────────

    private JSONObject callLLM(JSONArray messages, JSONArray tools) throws Exception {
        return callLLM(messages, tools, "auto");
    }

    private JSONObject callLLM(JSONArray messages, JSONArray tools, String toolChoice) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("model", MODEL_NAME);
        payload.put("messages", messages);
        payload.put("tools", tools);
        payload.put("tool_choice", toolChoice);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("[BotService] LLM raw response (" + response.statusCode() + "): " + response.body());
        if (response.statusCode() == 200) {
            JSONObject json = JSON.parseObject(response.body());
            JSONArray choices = json.getJSONArray("choices");
            if (choices != null && !choices.isEmpty()) {
                JSONObject choice = choices.getJSONObject(0);
                System.out.println("[BotService] finish_reason=" + choice.getString("finish_reason"));
                return choice;
            }
        } else {
            System.err.println("[BotService] LLM API error: " + response.statusCode() + " " + response.body());
        }
        return null;
    }

    // ─── Message helpers ─────────────────────────────────────────────────────

    private JSONArray buildMessages(List<Message> context, String userContent) {
        JSONArray messages = new JSONArray();

        JSONObject system = new JSONObject();
        system.put("role", "system");
        system.put("content", SYSTEM_PROMPT);
        messages.add(system);

        for (Message m : context) {
            JSONObject item = new JSONObject();
            item.put("role", isBot(m.getSenderId()) ? "assistant" : "user");
            item.put("content", m.getContent());
            messages.add(item);
        }

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userContent);
        messages.add(userMsg);

        return messages;
    }

    private List<Message> fetchRecentHistory(Long userId, int limit) {
        QueryWrapper<Message> q = new QueryWrapper<>();
        q.and(wrapper -> wrapper
                .nested(i -> i.eq("sender_id", userId).eq("target_id", botId))
                .or()
                .nested(i -> i.eq("sender_id", botId).eq("target_id", userId))
        ).orderByDesc("create_time").last("limit " + limit);
        List<Message> history = messageMapper.selectList(q);
        Collections.reverse(history);
        return history;
    }

    private void saveAndPush(Long userId, String content) {
        Message replyMsg = new Message();
        replyMsg.setSenderId(botId);
        replyMsg.setTargetId(userId);
        replyMsg.setIsGroup(false);
        replyMsg.setContent(content);
        replyMsg.setCreateTime(new Date());
        messageMapper.insert(replyMsg);

        ChatMessage wsMsg = new ChatMessage();
        wsMsg.setType(MessageType.CHAT);
        wsMsg.setSenderId(botId);
        wsMsg.setTargetId(userId);
        wsMsg.setContent(content);
        wsMsg.setTimestamp(System.currentTimeMillis());

        Channel channel = TextWebSocketFrameHandler.userChannelMap.get(userId);
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(wsMsg)));
        }
    }

    private void sendBotMessage(Long userId, String content) {
        saveAndPush(userId, content);
    }

    private Long findRecentUserMessage(Long userId, String content) {
        try {
            QueryWrapper<Message> q = new QueryWrapper<>();
            q.eq("sender_id", userId)
             .eq("target_id", botId)
             .eq("content", content)
             .orderByDesc("create_time")
             .last("limit 1");
            Message m = messageMapper.selectOne(q);
            return m != null ? m.getId() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ─── Semantic retrieval ──────────────────────────────────────────────────

    private List<Message> retrieveSemanticHistory(Long userId, String queryText, int topK) {
        List<MessageEmbedding> embeddings = messageEmbeddingMapper.selectLatestByUser(userId);
        if (embeddings.isEmpty()) return Collections.emptyList();

        float[] queryVec = embeddingService.getEmbedding(queryText);
        if (queryVec == null) return Collections.emptyList();

        List<long[]> scored = new ArrayList<>();
        for (MessageEmbedding me : embeddings) {
            try {
                float[] vec = embeddingService.deserializeEmbedding(me.getEmbedding());
                double sim = embeddingService.cosineSimilarity(queryVec, vec);
                scored.add(new long[]{me.getMessageId(), Double.doubleToLongBits(sim)});
            } catch (Exception ignored) {
            }
        }
        scored.sort((a, b) -> Double.compare(
                Double.longBitsToDouble(b[1]),
                Double.longBitsToDouble(a[1])
        ));

        List<Long> topIds = scored.stream()
                .limit(topK)
                .map(a -> a[0])
                .collect(Collectors.toList());

        if (topIds.isEmpty()) return Collections.emptyList();

        QueryWrapper<Message> q = new QueryWrapper<>();
        q.in("id", topIds);
        List<Message> userMessages = messageMapper.selectList(q);

        List<Message> result = new ArrayList<>();
        for (Message um : userMessages) {
            result.add(um);
            QueryWrapper<Message> botQ = new QueryWrapper<>();
            botQ.eq("sender_id", botId)
                .eq("target_id", userId)
                .gt("create_time", um.getCreateTime())
                .orderByAsc("create_time")
                .last("limit 1");
            Message botReply = messageMapper.selectOne(botQ);
            if (botReply != null) result.add(botReply);
        }

        result.sort(Comparator.comparing(Message::getCreateTime));
        return result;
    }

    private List<Message> mergeAndDeduplicate(List<Message> a, List<Message> b) {
        Map<Long, Message> map = new LinkedHashMap<>();
        for (Message m : a) map.put(m.getId(), m);
        for (Message m : b) map.put(m.getId(), m);
        List<Message> merged = new ArrayList<>(map.values());
        merged.sort(Comparator.comparing(Message::getCreateTime));
        return merged;
    }

    // ─── Tool execution ──────────────────────────────────────────────────────

    private String executeToolAction(Long userId, JSONObject toolCall) {
        try {
            String name = toolCall.getJSONObject("function").getString("name");
            JSONObject args = JSON.parseObject(
                    toolCall.getJSONObject("function").getString("arguments"));

            return switch (name) {
                case "send_message" -> executeSendMessage(
                        userId,
                        args.getString("friend_name"),
                        args.getString("content"));
                case "create_post" -> executeCreatePost(
                        userId,
                        args.getString("content"));
                default -> "未知操作，已忽略。";
            };
        } catch (Exception e) {
            System.err.println("[BotService] executeToolAction error: " + e.getMessage());
            return "操作执行失败，请稍后再试。";
        }
    }

    private String executeFriendList(Long userId) {
        try {
            List<Friend> friends = friendMapper.selectFriendsWithInfo(userId);
            List<String> names = friends.stream()
                    .filter(f -> !f.getFriendId().equals(botId))
                    .map(Friend::getFriendName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (names.isEmpty()) return "你目前还没有好友。";
            return "你的好友列表：" + String.join("、", names);
        } catch (Exception e) {
            return "查询好友列表失败：" + e.getMessage();
        }
    }

    private String executeSendMessage(Long userId, String friendName, String content) {
        try {
            QueryWrapper<User> uq = new QueryWrapper<>();
            uq.eq("username", friendName);
            User targetUser = userMapper.selectOne(uq);
            if (targetUser == null) return "找不到用户名为「" + friendName + "」的好友。";

            Long targetId = targetUser.getId();

            QueryWrapper<Friend> fq = new QueryWrapper<>();
            fq.eq("user_id", userId).eq("friend_id", targetId);
            if (friendMapper.selectCount(fq) == 0) {
                return "「" + friendName + "」不在你的好友列表中，无法发送消息。";
            }

            Message msg = new Message();
            msg.setSenderId(userId);
            msg.setTargetId(targetId);
            msg.setIsGroup(false);
            msg.setContent(content);
            msg.setCreateTime(new Date());
            messageMapper.insert(msg);

            Channel targetChannel = TextWebSocketFrameHandler.userChannelMap.get(targetId);
            if (targetChannel != null && targetChannel.isActive()) {
                ChatMessage wsMsg = new ChatMessage();
                wsMsg.setType(MessageType.CHAT);
                wsMsg.setSenderId(userId);
                wsMsg.setTargetId(targetId);
                wsMsg.setIsGroup(false);
                wsMsg.setContent(content);
                wsMsg.setTimestamp(System.currentTimeMillis());
                wsMsg.setId(msg.getId());
                targetChannel.writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(wsMsg)));
            }

            return "已成功以你的名义向「" + friendName + "」发送消息：「" + content + "」";
        } catch (Exception e) {
            return "发送消息失败：" + e.getMessage();
        }
    }

    private String executeCreatePost(Long userId, String content) {
        try {
            Post post = new Post();
            post.setUserId(userId);
            post.setContent(content);
            post.setLikeCount(0);
            post.setCommentCount(0);
            post.setCreateTime(new Date());
            postMapper.insert(post);

            stringRedisTemplate.opsForValue().set(postLikeCountKey(post.getId()), "0");
            stringRedisTemplate.opsForValue().set(postCommentCountKey(post.getId()), "0");

            return "已成功以你的名义在广场发布帖子：「" + content + "」";
        } catch (Exception e) {
            return "发帖失败：" + e.getMessage();
        }
    }

    private String buildConfirmMessage(String toolName, JSONObject toolCall) {
        JSONObject args = JSON.parseObject(
                toolCall.getJSONObject("function").getString("arguments"));
        return switch (toolName) {
            case "send_message" -> "我将以你的名义给**" + args.getString("friend_name")
                    + "**发送消息：「" + args.getString("content")
                    + "」\n请回复「确认」执行，或「取消」放弃。";
            case "create_post" -> "我将以你的名义在广场发布帖子：「" + args.getString("content")
                    + "」\n请回复「确认」执行，或「取消」放弃。";
            default -> "即将执行操作，请回复「确认」或「取消」。";
        };
    }

    /**
     * 检测模型是否在文本中伪装了工具执行结果（幻觉执行）。
     * 典型特征：直接说"已成功发布帖子/发送消息"而没有经过 function call。
     */
    private boolean looksLikeFakeToolExecution(String content) {
        if (content == null) return false;
        return (content.contains("发布帖子") || content.contains("发送消息") || content.contains("发帖"))
                && (content.contains("已成功") || content.contains("帮你") || content.contains("已经"));
    }
}