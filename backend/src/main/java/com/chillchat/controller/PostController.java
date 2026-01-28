package com.chillchat.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chillchat.entity.Comment;
import com.chillchat.entity.Post;
import com.chillchat.entity.PostLike;
import com.chillchat.mapper.CommentMapper;
import com.chillchat.mapper.PostLikeMapper;
import com.chillchat.mapper.PostMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private PostLikeMapper postLikeMapper;

    // Get Feed
    @GetMapping
    public List<Post> getFeed(
            @RequestParam(required = false) Long currentUserId,
            @RequestParam(required = false) Long filterUserId // For My Posts
    ) {
        QueryWrapper<Post> postQuery = new QueryWrapper<>();
        if (filterUserId != null) {
            postQuery.eq("user_id", filterUserId);
        }
        postQuery.orderByDesc("create_time");
        
        List<Post> posts = postMapper.selectList(postQuery);
        // Note: selectPostWithUser (custom mapper) is better, but to support filter easily with MP wrapper:
        // We probably need to map User info manually or use the Join mapper if it supports wrapper.
        // Let's assume selectPostWithUser doesn't accept wrapper easily given the previous code didn't use it.
        // Wait, the previous code used: List<Post> posts = postMapper.selectPostWithUser();
        // That likely selects ALL posts. 
        // If I want to filter, I should probably stick to selectPostWithUser() but filter in memory (not efficient but easiest given I can't see Mapper XML)
        // OR, better, check if I can add a method to Mapper.
        // Let's try to filter in memory for now if selectPostWithUser() returns everything.
        // Actually, let's see if we can use the original method and just filter result.
        
        List<Post> allPosts = postMapper.selectPostWithUser();
        if (filterUserId != null) {
            posts = allPosts.stream().filter(p -> p.getUserId().equals(filterUserId)).collect(java.util.stream.Collectors.toList());
        } else {
            posts = allPosts;
        }

        if (currentUserId != null) {
            for (Post p : posts) {
                QueryWrapper<PostLike> query = new QueryWrapper<>();
                query.eq("post_id", p.getId()).eq("user_id", currentUserId);
                p.setIsLiked(postLikeMapper.selectCount(query) > 0);
            }
        }
        return posts;
    }

    // Create Post
    @PostMapping
    public Post createPost(@RequestBody Post post) {
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setCreateTime(new java.util.Date());
        postMapper.insert(post);
        return post;
    }

    // Delete Post
    @DeleteMapping("/{postId}")
    public ResponseEntity<?> deletePost(@PathVariable Long postId, @RequestParam Long userId) {
        Post post = postMapper.selectById(postId);
        if (post == null) return ResponseEntity.notFound().build();
        
        if (!post.getUserId().equals(userId)) {
            return ResponseEntity.status(403).body("Not authorized");
        }
        
        postMapper.deleteById(postId);
        // Also delete comments and likes ideally, but keeping it simple
        return ResponseEntity.ok().build();
    }

    // Toggle Like
    @PostMapping("/{postId}/like")
    public ResponseEntity<?> toggleLike(@PathVariable Long postId, @RequestParam Long userId) {
        QueryWrapper<PostLike> query = new QueryWrapper<>();
        query.eq("post_id", postId).eq("user_id", userId);
        
        Post post = postMapper.selectById(postId);
        if (post == null) return ResponseEntity.notFound().build();

        if (postLikeMapper.delete(query) > 0) {
            // Unliked
            post.setLikeCount(post.getLikeCount() > 0 ? post.getLikeCount() - 1 : 0);
        } else {
            // Like
            PostLike like = new PostLike();
            like.setPostId(postId);
            like.setUserId(userId);
            postLikeMapper.insert(like);
            post.setLikeCount(post.getLikeCount() + 1);
        }
        postMapper.updateById(post);
        return ResponseEntity.ok(post.getLikeCount());
    }

    // Get Comments
    @GetMapping("/{postId}/comments")
    public List<Comment> getComments(@PathVariable Long postId) {
        return commentMapper.selectCommentsByPostId(postId);
    }

    // Add Comment
    @PostMapping("/{postId}/comments")
    public Comment addComment(@PathVariable Long postId, @RequestBody Comment comment) {
        comment.setPostId(postId);
        commentMapper.insert(comment);
        
        Post post = postMapper.selectById(postId);
        post.setCommentCount(post.getCommentCount() + 1);
        postMapper.updateById(post);
        
        return comment; // Note: In real app return comment with user info
    }
}
