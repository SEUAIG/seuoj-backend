package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.seuoj.seuojbackend.common.Capability;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 全站封禁。权限边界与编辑资料相同（{@link UserSettingsService#assertManageProfile}），
 * 一次请求要么全部生效要么全部不生效，便于管理员按报错修正名单后重试。
 */
@Slf4j @Service @RequiredArgsConstructor
public class UserBanService {
    public static final int MAX_BATCH = 500;
    private final UserInfoMapper users;
    private final UserSettingsService settings;
    private final UserRoleService roles;

    public record BanRequest(List<Long> userIds, List<String> usernames,
                             @NotBlank(message="请填写封禁原因") @Size(max=200, message="封禁原因不能超过200个字符") String reason,
                             LocalDateTime until) {}
    public record UnbanRequest(List<Long> userIds, List<String> usernames) {}
    public record Account(Long id, String username, String nickname) {}
    public record Outcome(int count, List<Account> accounts) {}

    @Transactional
    public Outcome ban(Long actor, BanRequest dto) {
        LocalDateTime now = LocalDateTime.now();
        if (dto.until() != null && !dto.until().isAfter(now)) throw new BadRequestException("解封时间必须晚于现在");
        List<UserInfo> targets = lockTargets(actor, dto.userIds(), dto.usernames());
        String reason = dto.reason().trim();
        List<Long> ids = targets.stream().map(UserInfo::getId).toList();
        users.update(null, new LambdaUpdateWrapper<UserInfo>().in(UserInfo::getId, ids)
                .set(UserInfo::getBannedAt, now)
                .set(UserInfo::getBannedUntil, dto.until())
                .set(UserInfo::getBanReason, reason)
                .set(UserInfo::getBannedBy, actor));
        log.info("用户 {} 封禁了用户 {}，截止 {}", actor, ids, dto.until() == null ? "永久" : dto.until());
        return outcome(targets);
    }

    @Transactional
    public Outcome unban(Long actor, UnbanRequest dto) {
        List<UserInfo> targets = lockTargets(actor, dto.userIds(), dto.usernames());
        List<Long> ids = targets.stream().map(UserInfo::getId).toList();
        users.update(null, new LambdaUpdateWrapper<UserInfo>().in(UserInfo::getId, ids)
                .set(UserInfo::getBannedAt, null)
                .set(UserInfo::getBannedUntil, null)
                .set(UserInfo::getBanReason, null)
                .set(UserInfo::getBannedBy, null));
        log.info("用户 {} 解封了用户 {}", actor, ids);
        return outcome(targets);
    }

    /** 按 id 和账号名找出目标并加行锁，逐个检查权限；有任何一个不合格就整体拒绝。 */
    private List<UserInfo> lockTargets(Long actor, List<Long> userIds, List<String> usernames) {
        Set<Long> ids = new LinkedHashSet<>();
        if (userIds != null) for (Long id : userIds) if (id != null) ids.add(id);
        Set<String> names = new LinkedHashSet<>();
        if (usernames != null) for (String name : usernames) if (name != null && !name.isBlank()) names.add(name.trim());
        if (!roles.hasCapability(actor, Capability.USER_MANAGE)) throw new ForbiddenException("没有用户管理权限");
        if (ids.isEmpty() && names.isEmpty()) throw new BadRequestException("请至少选择一个账号");
        if (ids.size() + names.size() > MAX_BATCH) throw new BadRequestException("一次最多处理 " + MAX_BATCH + " 个账号");

        Map<Long, UserInfo> found = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            for (UserInfo user : users.selectList(new LambdaQueryWrapper<UserInfo>().in(UserInfo::getId, ids).last("FOR UPDATE")))
                found.put(user.getId(), user);
            List<String> missing = ids.stream().filter(id -> !found.containsKey(id)).map(String::valueOf).toList();
            if (!missing.isEmpty()) throw new BadRequestException("找不到这些用户 ID：" + String.join("、", missing));
        }
        if (!names.isEmpty()) {
            // 库的排序规则不区分大小写，这里也按不区分大小写对应；比赛临时账号可能与普通账号重名
            Map<String, List<UserInfo>> byName = new HashMap<>();
            for (UserInfo user : users.selectList(new LambdaQueryWrapper<UserInfo>().in(UserInfo::getUsername, names).last("FOR UPDATE")))
                byName.computeIfAbsent(user.getUsername().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(user);
            List<String> missing = new ArrayList<>(), ambiguous = new ArrayList<>();
            for (String name : names) {
                List<UserInfo> matched = byName.getOrDefault(name.toLowerCase(Locale.ROOT), List.of());
                if (matched.isEmpty()) missing.add(name);
                else if (matched.size() > 1) ambiguous.add(name);
                else found.putIfAbsent(matched.get(0).getId(), matched.get(0));
            }
            if (!missing.isEmpty()) throw new BadRequestException("找不到这些账号：" + String.join("、", missing));
            if (!ambiguous.isEmpty()) throw new BadRequestException("这些账号名对应多个用户，请在用户列表中勾选：" + String.join("、", ambiguous));
        }

        List<String> denied = new ArrayList<>();
        for (UserInfo user : found.values()) {
            if (Objects.equals(actor, user.getId())) throw new ForbiddenException("不能封禁或解封自己");
            try {
                settings.assertManageProfile(actor, user.getId());
            } catch (ForbiddenException e) {
                denied.add(user.getUsername());
            }
        }
        if (!denied.isEmpty()) throw new ForbiddenException("无权管理这些账号：" + String.join("、", denied));
        return new ArrayList<>(found.values());
    }

    private static Outcome outcome(List<UserInfo> targets) {
        List<Account> accounts = targets.stream().map(u -> new Account(u.getId(), u.getUsername(), u.getNickname())).toList();
        return new Outcome(accounts.size(), accounts);
    }
}
