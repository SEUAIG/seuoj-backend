package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Serialize all application account creation without modifying legacy duplicate usernames. */
@Service
@RequiredArgsConstructor
public class AccountCreationService {
    private final UserInfoMapper users;

    @Transactional(noRollbackFor = DuplicateKeyException.class)
    public void insert(UserInfo user) {
        if (users.lockAccountCreation() == null) throw new IllegalStateException("STUDENT role is missing");
        // A locking read sees the latest committed usernames, even inside an older RR transaction.
        if (!users.selectList(new LambdaQueryWrapper<UserInfo>().select(UserInfo::getId)
                .eq(UserInfo::getUsername, user.getUsername()).last("LIMIT 1 FOR UPDATE")).isEmpty()) {
            throw new DuplicateKeyException("Username already exists");
        }
        users.insert(user);
    }
}
