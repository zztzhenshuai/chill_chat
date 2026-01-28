package com.chillchat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("friends")
public class Friend {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long friendId;

    @TableField(exist = false)
    private String friendName;
    @TableField(exist = false)
    private String friendAvatar;
    @TableField(exist = false)
    private String friendSignature;
    @TableField(exist = false)
    private Integer friendGender;
    @TableField(exist = false)
    private java.util.Date friendBirthday;
    @TableField(exist = false)
    private String friendLocation;
}
