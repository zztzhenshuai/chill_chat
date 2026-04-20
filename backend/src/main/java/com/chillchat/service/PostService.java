package com.chillchat.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chillchat.entity.Comment;
import com.chillchat.entity.Post;
import com.chillchat.entity.PostLike;
import com.chillchat.mapper.CommentMapper;
import com.chillchat.mapper.PostLikeMapper;
import com.chillchat.mapper.PostMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class PostService {

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private PostLikeMapper postLikeMapper;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private String postLikeCountKey(Long postId) {
        return "post:like:cnt:" + postId;
    }

    private String postCommentCountKey(Long postId) {
        return "post:comment:cnt:" + postId;
    }

    public List<Post> getFeed(Long currentUserId, Long filterUserId, int page, int size) {
        int offset = (page - 1) * size;
        List<Post> posts;
        if (filterUserId != null) {
            posts = postMapper.selectUserPostsPaged(filterUserId, offset, size);
        } else {
            posts = postMapper.selectAllPostsPaged(offset, size);
        }

        if (currentUserId != null) {
            for (Post p : posts) {
                QueryWrapper<PostLike> query = new QueryWrapper<>();
                query.eq("post_id", p.getId()).eq("user_id", currentUserId);
                p.setIsLiked(postLikeMapper.selectCount(query) > 0);
                try {
                    String likeCnt = stringRedisTemplate.opsForValue().get(postLikeCountKey(p.getId()));
                    String commentCnt = stringRedisTemplate.opsForValue().get(postCommentCountKey(p.getId()));
                    if (likeCnt != null) p.setLikeCount(Integer.parseInt(likeCnt));
                    if (commentCnt != null) p.setCommentCount(Integer.parseInt(commentCnt));
                } catch (Exception ignored) {
                }
            }
        }
        return posts;
    }

    public Post createPost(Post post) {
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setCreateTime(new Date());
        postMapper.insert(post);
        try {
            stringRedisTemplate.opsForValue().set(postLikeCountKey(post.getId()), "0");
            stringRedisTemplate.opsForValue().set(postCommentCountKey(post.getId()), "0");
        } catch (Exception ignored) {
        }
        return post;
    }

    public ResponseEntity<?> deletePost(Long postId, Long userId) {
        Post post = postMapper.selectById(postId);
        if (post == null) return ResponseEntity.notFound().build();
        if (!post.getUserId().equals(userId)) {
            return ResponseEntity.status(403).body("Not authorized");
        }
        postLikeMapper.delete(new QueryWrapper<PostLike>().eq("post_id", postId));
        commentMapper.delete(new QueryWrapper<Comment>().eq("post_id", postId));
        postMapper.deleteById(postId);
        return ResponseEntity.ok().build();
    }

    public ResponseEntity<?> toggleLike(Long postId, Long userId) {
        Post post = postMapper.selectById(postId);
        if (post == null) return ResponseEntity.notFound().build();

        try {
            stringRedisTemplate.opsForValue().setIfAbsent(postLikeCountKey(postId), String.valueOf(post.getLikeCount()));
        } catch (Exception ignored) {
        }

        QueryWrapper<PostLike> query = new QueryWrapper<>();
        query.eq("post_id", postId).eq("user_id", userId);
        Long newLikeCount = null;

        if (postLikeMapper.delete(query) > 0) {
            try {
                newLikeCount = stringRedisTemplate.opsForValue().decrement(postLikeCountKey(postId));
                if (newLikeCount != null && newLikeCount < 0) {
                    stringRedisTemplate.opsForValue().set(postLikeCountKey(postId), "0");
                    newLikeCount = 0L;
                }
            } catch (Exception ignored) {
            }
            post.setLikeCount(newLikeCount != null ? newLikeCount.intValue() : Math.max(0, post.getLikeCount() - 1));
        } else {
            PostLike like = new PostLike();
            like.setPostId(postId);
            like.setUserId(userId);
            postLikeMapper.insert(like);
            try {
                newLikeCount = stringRedisTemplate.opsForValue().increment(postLikeCountKey(postId));
            } catch (Exception ignored) {
            }
            post.setLikeCount(newLikeCount != null ? newLikeCount.intValue() : post.getLikeCount() + 1);
        }

        postMapper.updateById(post);
        return ResponseEntity.ok(post.getLikeCount());
    }

    public Comment addComment(Long postId, Comment comment) {
        comment.setPostId(postId);
        commentMapper.insert(comment);

        Post post = postMapper.selectById(postId);
        if (post != null) {
            Long newCommentCount = null;
            try {
                stringRedisTemplate.opsForValue().setIfAbsent(postCommentCountKey(postId), String.valueOf(post.getCommentCount()));
                newCommentCount = stringRedisTemplate.opsForValue().increment(postCommentCountKey(postId));
            } catch (Exception ignored) {
            }
            post.setCommentCount(newCommentCount != null ? newCommentCount.intValue() : post.getCommentCount() + 1);
            postMapper.updateById(post);
        }
        return comment;
    }

    public List<Comment> getComments(Long postId) {
        return commentMapper.selectCommentsByPostId(postId);
    }

    // ── Benchmark helpers ────────────────────────────────────────────────────

    /** 直接查 MySQL，不经过任何缓存（benchmark 基准线） */
    public List<Post> getFeedDirectMySQL(int page, int size) {
        int offset = (page - 1) * size;
        return postMapper.selectAllPostsPaged(offset, size);
    }

    /** Cache-Aside 模式：命中 Redis 则直接返回，否则查 MySQL 后写入缓存 */
    public List<Post> getFeedRedisCache(int page, int size) {
        String key = "bench:feed:p" + page + ":s" + size;
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null) {
                return JSON.parseObject(json, new TypeReference<List<Post>>() {});
            }
        } catch (Exception ignored) {
        }
        int offset = (page - 1) * size;
        List<Post> posts = postMapper.selectAllPostsPaged(offset, size);
        try {
            stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(posts), 30, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
        return posts;
    }
}
