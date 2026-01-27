package com.chillchat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("posts")
public class Post {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String content;
    private String imageUrl;
    private Integer likeCount;
    private Integer commentCount;
    private Date createTime;

    @TableField(exist = false)
    private String username; // For UI display
    @TableField(exist = false)
    private String avatar;   // For UI display
    @TableField(exist = false)
    private Boolean isLiked; // Current user liked?
}
