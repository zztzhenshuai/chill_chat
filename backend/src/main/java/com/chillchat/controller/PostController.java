package com.chillchat.controller;

import com.chillchat.entity.Comment;
import com.chillchat.entity.Post;
import com.chillchat.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    @Autowired
    private PostService postService;

    // Get Feed（R-5 修复：支持分页，默认第1页每页20条）
    @GetMapping
    public List<Post> getFeed(
            @RequestParam(required = false) Long currentUserId,
            @RequestParam(required = false) Long filterUserId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return postService.getFeed(currentUserId, filterUserId, page, size);
    }

    // Create Post
    @PostMapping
    public Post createPost(@RequestBody Post post) {
        return postService.createPost(post);
    }

    // Delete Post
    @DeleteMapping("/{postId}")
    public ResponseEntity<?> deletePost(@PathVariable Long postId, @RequestParam Long userId) {
        return postService.deletePost(postId, userId);
    }

    // Toggle Like
    @PostMapping("/{postId}/like")
    public ResponseEntity<?> toggleLike(@PathVariable Long postId, @RequestParam Long userId) {
        return postService.toggleLike(postId, userId);
    }

    // Get Comments
    @GetMapping("/{postId}/comments")
    public List<Comment> getComments(@PathVariable Long postId) {
        return postService.getComments(postId);
    }

    // Add Comment
    @PostMapping("/{postId}/comments")
    public Comment addComment(@PathVariable Long postId, @RequestBody Comment comment) {
        return postService.addComment(postId, comment);
    }
}
