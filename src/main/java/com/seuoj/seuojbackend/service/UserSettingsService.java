package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.seuoj.seuojbackend.common.Capability;
import com.seuoj.seuojbackend.entity.*;
import com.seuoj.seuojbackend.exception.*;
import com.seuoj.seuojbackend.mapper.*;
import jakarta.validation.constraints.*;
import java.security.SecureRandom;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j @Service @RequiredArgsConstructor
public class UserSettingsService {
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int RESET_PASSWORD_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserInfoMapper users;
    private final UserRoleMapper roleDefinitions;
    private final UserRoleRelMapper roleLinks;
    private final UserCapabilityMapper grants;
    private final UserRoleService roles;
    private final PasswordEncoder passwords;
    private final VerificationCodeService codes;

    public record Settings(Long id, String username, String nickname, String email, String role,
                           boolean allowNicknameChange, Set<Capability> assigned, Set<Capability> effective,
                           boolean canManageProfile, boolean canManagePermissions, boolean canGrantAdmin) {}
    public record ProfileUpdate(@Size(max=64) String nickname, @Email @Size(max=128, message="邮箱长度不能超过128个字符") String email,
                                Boolean allowNicknameChange) {}
    public record PermissionsUpdate(@NotNull Set<@NotNull Capability> assigned) {}
    public record EmailUpdate(@NotBlank @Email @Size(max=128, message="邮箱长度不能超过128个字符") String email, @NotBlank String password,
                              @NotBlank String verificationId, @NotBlank String code) {}
    public record PasswordReset(String password) {}

    private UserInfo requireUser(Long id) {
        UserInfo user = users.selectById(id);
        if (user == null) throw new NotFoundException("用户不存在");
        return user;
    }

    public void assertManageProfile(Long actor, Long target) {
        if (!roles.hasCapability(actor, Capability.USER_MANAGE)) throw new ForbiddenException("没有用户管理权限");
        if (roles.isSuperAdmin(target) && !Objects.equals(actor,target)) throw new ForbiddenException("不能修改其他超级管理员");
        if (roles.isAdmin(target) && !roles.isSuperAdmin(actor)) throw new ForbiddenException("仅超级管理员可管理管理员账号");
        if (!roles.getCapabilities(target).isEmpty() && !roles.isAdmin(actor)) throw new ForbiddenException("仅账号管理员可管理有特殊权限的账号");
    }

    public boolean canManageProfile(Long actor, Long target) {
        if (Objects.equals(actor, target)) return true;
        try {
            assertManageProfile(actor, target);
            return true;
        } catch (ForbiddenException denied) {
            return false;
        }
    }

    public Settings get(Long actor, Long target) {
        UserInfo user = requireUser(target);
        boolean self = Objects.equals(actor,target);
        if (!self) assertManageProfile(actor,target);
        Set<Capability> assigned = EnumSet.noneOf(Capability.class);
        for (String code : grants.findByUserId(target)) assigned.add(Capability.valueOf(code));
        if (roles.isTeacher(target)) assigned.add(Capability.TEACHER);
        if (roles.isAdmin(target)) assigned.add(Capability.ACCOUNT_ADMIN);
        boolean manage = !self && roles.hasCapability(actor,Capability.USER_MANAGE);
        return new Settings(target,user.getUsername(),user.getNickname(),user.getEmail(),roles.getHighestRoleLabel(target),
                !Boolean.FALSE.equals(user.getAllowNicknameChange()),assigned,roles.getCapabilities(target),
                manage,manage && roles.isAdmin(actor) && !roles.isSuperAdmin(target),roles.isSuperAdmin(actor));
    }

    @Transactional
    public Settings updateProfile(Long actor, Long target, ProfileUpdate dto) {
        users.selectList(new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getId,target).last("FOR UPDATE"));
        boolean self = Objects.equals(actor,target);
        if (!self) assertManageProfile(actor,target);
        UserInfo user = requireUser(target);
        if (self && (dto.email()!=null || dto.allowNicknameChange()!=null)) throw new ForbiddenException("邮箱需单独验证，昵称开关由账号管理者设置");
        if (dto.nickname()!=null) {
            String nickname=dto.nickname().trim();
            if (self && Boolean.FALSE.equals(user.getAllowNicknameChange()) && !Objects.equals(nickname,Objects.toString(user.getNickname(),"")))
                throw new ForbiddenException("此账号不允许修改昵称");
            users.update(null,new LambdaUpdateWrapper<UserInfo>().eq(UserInfo::getId,target)
                    .set(UserInfo::getNickname,nickname.isEmpty()?null:nickname));
        }
        if (dto.email()!=null) updateEmailValue(target,dto.email());
        if (dto.allowNicknameChange()!=null) users.update(null,new LambdaUpdateWrapper<UserInfo>()
                .eq(UserInfo::getId,target).set(UserInfo::getAllowNicknameChange,dto.allowNicknameChange()));
        return get(actor,target);
    }

    private void updateEmailValue(Long target, String input) {
        String email=input.trim().toLowerCase(Locale.ROOT);
        if (email.isBlank()) throw new BadRequestException("邮箱不能为空");
        // Share account creation's lock so an email change cannot race registration/import.
        users.lockAccountCreation();
        if (!users.selectList(new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getEmail,email)
                .ne(UserInfo::getId,target).last("LIMIT 1 FOR UPDATE")).isEmpty()) throw new ConflictException("邮箱已被使用");
        users.update(null,new LambdaUpdateWrapper<UserInfo>().eq(UserInfo::getId,target).set(UserInfo::getEmail,email));
    }

    @Transactional
    public Settings updateEmail(Long actor, EmailUpdate dto) {
        users.selectList(new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getId,actor).last("FOR UPDATE"));
        UserInfo user=requireUser(actor);
        if (!passwords.matches(dto.password(),user.getPassword())) throw new ForbiddenException("当前密码不正确");
        String verified=codes.preVerifyCode(dto.verificationId(),dto.code());
        if (verified==null) throw new BadRequestException("验证码无效或已过期");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { codes.consumeVerification(dto.verificationId()); }
            @Override public void afterCompletion(int status) {
                if (status!=STATUS_COMMITTED) codes.releaseReservation(dto.verificationId());
            }
        });
        if (!verified.equalsIgnoreCase(dto.email().trim())) throw new BadRequestException("验证码与新邮箱不匹配");
        updateEmailValue(actor,dto.email());
        return get(actor,actor);
    }

    /** 账号管理者为他人重设密码：生成随机密码，只在本次响应中返回明文。 */
    @Transactional
    public PasswordReset resetPassword(Long actor, Long target) {
        users.selectList(new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getId,target).last("FOR UPDATE"));
        requireUser(target);
        if (Objects.equals(actor,target)) throw new ForbiddenException("修改自己的密码请使用修改密码功能");
        assertManageProfile(actor,target);
        StringBuilder password=new StringBuilder(RESET_PASSWORD_LENGTH);
        for (int i=0;i<RESET_PASSWORD_LENGTH;i++) password.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        users.update(null,new LambdaUpdateWrapper<UserInfo>().eq(UserInfo::getId,target)
                .set(UserInfo::getPassword,passwords.encode(password.toString())));
        log.info("用户 {} 重设了用户 {} 的密码", actor, target);
        return new PasswordReset(password.toString());
    }

    @Transactional
    public Settings updatePermissions(Long actor, Long target, Set<Capability> assigned) {
        users.selectList(new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getId,target).last("FOR UPDATE"));
        requireUser(target);
        if (Objects.equals(actor,target)) throw new ForbiddenException("不能修改自己的权限");
        if (!roles.isAdmin(actor)) throw new ForbiddenException("仅账号管理员可分配权限");
        assertManageProfile(actor,target);
        if (roles.isSuperAdmin(target)) throw new ForbiddenException("不能修改超级管理员权限");
        if (assigned.contains(Capability.ACCOUNT_ADMIN) && !roles.isSuperAdmin(actor)) throw new ForbiddenException("仅超级管理员可授予账号管理员权限");
        grants.deleteByUserId(target);
        for (Capability c: assigned) if (c!=Capability.TEACHER && c!=Capability.ACCOUNT_ADMIN) grants.grant(target,c.name());
        String role=assigned.contains(Capability.ACCOUNT_ADMIN)?"ADMIN":assigned.contains(Capability.TEACHER)?"TEACHER":"STUDENT";
        UserRole definition=roleDefinitions.selectOne(new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleCode,role));
        if (definition==null) throw new IllegalStateException("Missing role: "+role);
        roleLinks.update(null,new LambdaUpdateWrapper<UserRoleRel>().eq(UserRoleRel::getUserId,target).set(UserRoleRel::getIsDel,1));
        roleLinks.insert(new UserRoleRel().setUserId(target).setRoleId(definition.getId()));
        return get(actor,target);
    }
}
