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
    public List<Post> getFeed(@RequestParam(required = false) Long currentUserId) {
        List<Post> posts = postMapper.selectPostWithUser();
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
        postMapper.insert(post);
        return post;
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
