package com.chillchat.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chillchat.entity.User;
import com.chillchat.mapper.UserMapper;
import com.chillchat.model.LoginRequest;
import com.chillchat.service.RedisRateLimiterService;
import com.chillchat.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private com.chillchat.mapper.FriendMapper friendMapper;

    @Autowired
    private RedisRateLimiterService rateLimiterService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private void addBotFriend(Long userId) {
        QueryWrapper<User> qBot = new QueryWrapper<>();
        qBot.eq("username", "ChillBot");
        User bot = userMapper.selectOne(qBot);
        if (bot != null) {
             com.chillchat.entity.Friend f1 = new com.chillchat.entity.Friend();
             f1.setUserId(userId);
             f1.setFriendId(bot.getId());
             friendMapper.insert(f1);

             com.chillchat.entity.Friend f2 = new com.chillchat.entity.Friend();
             f2.setUserId(bot.getId());
             f2.setFriendId(userId);
             friendMapper.insert(f2);
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody LoginRequest req) {
        QueryWrapper<User> query = new QueryWrapper<>();
        query.eq("username", req.getUsername());
        if (userMapper.selectCount(query) > 0) {
            return ResponseEntity.badRequest().body("Username already exists");
        }

        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setAvatar("https://api.dicebear.com/7.x/avataaars/svg?seed=" + req.getUsername());
        userMapper.insert(user);
        
        // Auto add Bot friend
        addBotFriend(user.getId());

        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpServletRequest request) {
        String username = req.getUsername() == null ? "unknown" : req.getUsername().trim().toLowerCase();
        String clientIp = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        boolean userAllowed = rateLimiterService.allow("rl:login:user:" + username, 10, 60);
        boolean ipAllowed = rateLimiterService.allow("rl:login:ip:" + clientIp, 30, 60);
        if (!userAllowed || !ipAllowed) {
            return ResponseEntity.status(429).body("Too many login attempts, try again later");
        }

        QueryWrapper<User> query = new QueryWrapper<>();
        query.eq("username", req.getUsername());
        User user = userMapper.selectOne(query);
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            return ResponseEntity.status(401).body("Invalid credentials");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        
        Map<String, Object> resp = new HashMap<>();
        resp.put("token", token);
        resp.put("userId", user.getId());
        resp.put("username", user.getUsername());
        resp.put("avatar", user.getAvatar());
        
        return ResponseEntity.ok(resp);
    }
}
