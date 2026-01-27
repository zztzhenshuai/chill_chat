package com.chillchat.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chillchat.entity.Message;
import com.chillchat.mapper.MessageMapper;
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
            @RequestParam Long currentId,
            @RequestParam Long targetId,
            @RequestParam Boolean isGroup) {

        QueryWrapper<Message> query = new QueryWrapper<>();
        
        if (isGroup) {
            // Get group messages
            query.eq("target_id", targetId)
                 .eq("is_group", true);
        } else {
            // Get private messages (A->B or B->A)
            query.eq("is_group", false)
                 .and(wrapper -> wrapper
                     .nested(w -> w.eq("sender_id", currentId).eq("target_id", targetId))
                     .or()
                     .nested(w -> w.eq("sender_id", targetId).eq("target_id", currentId))
                 );
        }
        
        // Limit to last 50
        query.orderByAsc("create_time");
        // To do paging properly, we probably want .last("LIMIT 50") but let's return all for now or 100
        query.last("LIMIT 100");

        return messageMapper.selectList(query);
    }
}
