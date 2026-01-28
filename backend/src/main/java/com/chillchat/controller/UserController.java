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
    public ResponseEntity<?> updateProfile(@RequestParam Long userId, @RequestBody UpdateRequest updates) {
        User user = userMapper.selectById(userId);
        if (user == null) return ResponseEntity.notFound().build();

        if (updates.getUsername() != null && !updates.getUsername().isEmpty()) {
            user.setUsername(updates.getUsername());
        }
        
        // Password Change
        if (updates.getNewPassword() != null && !updates.getNewPassword().isEmpty()) {
            if (updates.getOldPassword() == null || !updates.getOldPassword().equals(user.getPassword())) {
                return ResponseEntity.badRequest().body("旧密码错误");
            }
            user.setPassword(updates.getNewPassword());
        }

        if (updates.getAvatar() != null && !updates.getAvatar().isEmpty()) {
            user.setAvatar(updates.getAvatar());
        }
        if (updates.getSignature() != null) {
            user.setSignature(updates.getSignature());
        }
        if (updates.getGender() != null) {
            user.setGender(updates.getGender());
        }
        if (updates.getBirthday() != null) {
            user.setBirthday(updates.getBirthday());
        }
        if (updates.getLocation() != null) {
            user.setLocation(updates.getLocation());
        }

        userMapper.updateById(user);
        return ResponseEntity.ok(user);
    }

    @lombok.Data
    static class UpdateRequest {
        private String username;
        private String oldPassword;
        private String newPassword;
        private String avatar;
        private String signature;
        private Integer gender;
        private java.util.Date birthday;
        private String location;
    }
    
    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) {
        return userMapper.selectById(id);
    }
}
