package com.chillchat.controller;

import com.chillchat.entity.User;
import com.chillchat.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserMapper userMapper;

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestParam Long userId, @RequestBody User updates) {
        User user = userMapper.selectById(userId);
        if (user == null) return ResponseEntity.notFound().build();

        if (updates.getUsername() != null && !updates.getUsername().isEmpty()) {
            user.setUsername(updates.getUsername());
        }
        if (updates.getPassword() != null && !updates.getPassword().isEmpty()) {
            user.setPassword(updates.getPassword());
        }
        if (updates.getAvatar() != null && !updates.getAvatar().isEmpty()) {
            user.setAvatar(updates.getAvatar());
        }
        if (updates.getSignature() != null) {
            user.setSignature(updates.getSignature());
        }

        userMapper.updateById(user);
        return ResponseEntity.ok(user);
    }
    
    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) {
        return userMapper.selectById(id);
    }
}
