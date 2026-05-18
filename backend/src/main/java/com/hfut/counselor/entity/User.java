package com.hfut.counselor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("counselor_user")
public class User {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String username;
    private String displayName;
    private String email;
    private String phone;
    private String avatar;
    private String userType;
    private String portalUserId;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
