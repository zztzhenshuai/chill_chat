package com.chillchat.service;

import com.chillchat.entity.Post;
import com.chillchat.mapper.PostMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class PostSyncService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private PostMapper postMapper;

    // Run every minute
    @Scheduled(fixedDelay = 60000)
    public void syncPostStats() {
        // Sync Likes
        syncLikes();
        // Sync Comments
        syncComments();
    }

    private void syncLikes() {
        try {
            Set<String> keys = stringRedisTemplate.keys("post:like:cnt:*");
            if (keys == null || keys.isEmpty()) return;

            for (String key : keys) {
                try {
                    String val = stringRedisTemplate.opsForValue().get(key);
                    if (val == null) continue;

                    String postIdStr = key.substring("post:like:cnt:".length());
                    Long postId = Long.parseLong(postIdStr);
                    Integer count = Integer.parseInt(val);

                    Post update = new Post();
                    update.setId(postId);
                    update.setLikeCount(count);
                    postMapper.updateById(update);
                } catch (Exception e) {
                    // Ignore parsing errors
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void syncComments() {
        try {
            Set<String> keys = stringRedisTemplate.keys("post:comment:cnt:*");
            if (keys == null || keys.isEmpty()) return;

            for (String key : keys) {
                try {
                    String val = stringRedisTemplate.opsForValue().get(key);
                    if (val == null) continue;

                    String postIdStr = key.substring("post:comment:cnt:".length());
                    Long postId = Long.parseLong(postIdStr);
                    Integer count = Integer.parseInt(val);

                    Post update = new Post();
                    update.setId(postId);
                    update.setCommentCount(count);
                    postMapper.updateById(update);
                } catch (Exception e) {
                    // Ignore parsing errors
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
