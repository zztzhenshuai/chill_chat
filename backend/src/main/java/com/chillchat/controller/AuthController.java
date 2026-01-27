package com.chillchat.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chillchat.entity.User;
import com.chillchat.mapper.UserMapper;
import com.chillchat.model.LoginRequest;
import com.chillchat.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody LoginRequest req) {
        QueryWrapper<User> query = new QueryWrapper<>();
        query.eq("username", req.getUsername());
        if (userMapper.selectCount(query) > 0) {
            return ResponseEntity.badRequest().body("Username already exists");
        }

        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(req.getPassword()); // In real world, use BCrypt
        user.setAvatar("https://api.dicebear.com/7.x/avataaars/svg?seed=" + req.getUsername());
        userMapper.insert(user);

        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        QueryWrapper<User> query = new QueryWrapper<>();
        query.eq("username", req.getUsername())
             .eq("password", req.getPassword());
        
        User user = userMapper.selectOne(query);
        if (user == null) {
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
