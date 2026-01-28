package com.chillchat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("users")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String avatar;
    private String signature;
    private Integer gender; // 1: Male, 2: Female
    private Date birthday;
    private String location;
    private Date createTime;
}
