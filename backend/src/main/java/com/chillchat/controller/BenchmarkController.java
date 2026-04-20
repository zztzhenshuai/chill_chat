package com.chillchat.controller;

import com.chillchat.entity.Post;
import com.chillchat.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * 压力测试专用接口（已在 WebConfig 中排除 JWT 认证）
 *
 * GET /api/benchmark/mysql   — 每次直接查 MySQL，无任何缓存
 * GET /api/benchmark/redis   — Cache-Aside：命中 Redis 直接返回，否则查 MySQL 后写缓存（TTL 30s）
 * DELETE /api/benchmark/cache — 手动清空 bench:feed:* 缓存键，方便重复测试
 */
@RestController
@RequestMapping("/api/benchmark")
public class BenchmarkController {

    @Autowired
    private PostService postService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 纯 MySQL 基准，不经过 Redis */
    @GetMapping("/mysql")
    public List<Post> mysqlFeed(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return postService.getFeedDirectMySQL(page, size);
    }

    /** Redis Cache-Aside 版本 */
    @GetMapping("/redis")
    public List<Post> redisFeed(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return postService.getFeedRedisCache(page, size);
    }

    /** 清除 bench 缓存，让 Redis 测试从 cache-miss 开始 */
    @DeleteMapping("/cache")
    public String clearCache() {
        try {
            Set<String> keys = stringRedisTemplate.keys("bench:feed:*");
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
                return "Cleared " + keys.size() + " cache key(s)";
            }
            return "No cache keys found";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
