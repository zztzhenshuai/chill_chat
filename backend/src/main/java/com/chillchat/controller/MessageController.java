package com.chillchat.controller;

import com.chillchat.entity.Message;
import com.chillchat.mapper.MessageMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    @Autowired
    private MessageMapper messageMapper;

    @GetMapping("/history")
    public List<Message> getHistory(
            @RequestParam Long targetId,
            @RequestParam Boolean isGroup,
            @RequestParam(defaultValue = "-1") Long beforeId,
            @RequestParam(defaultValue = "30") int pageSize,
            HttpServletRequest request) {

        Long currentUserId = (Long) request.getAttribute("currentUserId");

        return messageMapper.selectPagedHistory(currentUserId, targetId, isGroup, beforeId, pageSize);
    }
}
