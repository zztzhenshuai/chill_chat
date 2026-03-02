package com.chillchat.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RedisRateLimiterService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public boolean allow(String key, long limit, long windowSeconds) {
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count == null) {
                return true;
            }
            if (count == 1) {
                stringRedisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
            }
            return count <= limit;
        } catch (Exception ignored) {
            return true;
        }
    }
}
