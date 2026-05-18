package com.hfut.counselor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hfut.counselor.entity.User;
import com.hfut.counselor.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class UserService extends ServiceImpl<UserMapper, User> {
    public User findById(String id) {
        return StringUtils.hasText(id) ? getById(id) : null;
    }

    public User findByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username), false);
    }

    public User upsertPortalUser(String username, String displayName, String email, String phone,
                                 String avatar, String portalUserId) {
        User user = findByUsername(username);
        if (user == null && StringUtils.hasText(portalUserId)) {
            user = getOne(new LambdaQueryWrapper<User>().eq(User::getPortalUserId, portalUserId), false);
        }
        if (user == null) {
            user = new User();
            user.setUsername(username);
            user.setCreateTime(LocalDateTime.now());
        }
        user.setDisplayName(StringUtils.hasText(displayName) ? displayName : username);
        user.setEmail(email);
        user.setPhone(phone);
        user.setAvatar(avatar);
        user.setUserType("PORTAL_USER");
        user.setPortalUserId(StringUtils.hasText(portalUserId) ? portalUserId : username);
        user.setLastLoginTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        saveOrUpdate(user);
        return user;
    }

    public User createOrUpdateDevUser(String username, String displayName) {
        String actualUsername = StringUtils.hasText(username) ? username : "dev_user";
        User user = findByUsername(actualUsername);
        if (user == null) {
            user = new User();
            user.setUsername(actualUsername);
            user.setCreateTime(LocalDateTime.now());
        }
        user.setDisplayName(StringUtils.hasText(displayName) ? displayName : "开发测试用户");
        user.setEmail(actualUsername + "@example.com");
        user.setPhone("18800000000");
        user.setUserType("DEV_USER");
        user.setPortalUserId(actualUsername);
        user.setLastLoginTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        saveOrUpdate(user);
        return user;
    }
}
