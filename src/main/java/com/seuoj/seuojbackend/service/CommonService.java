package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.common.RoleType;
import com.seuoj.seuojbackend.exception.AuthorizationException;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.interceptor.UserContext;
import com.seuoj.seuojbackend.interceptor.UserContextHolder;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.mapper.UserRoleRelMapper;
import com.seuoj.seuojbackend.vo.common.UserPageItemVO;
import com.seuoj.seuojbackend.vo.common.UserPageVO;
import com.seuoj.seuojbackend.vo.common.UserRoleRowVO;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CommonService {

    private final UserInfoMapper userInfoMapper;
    private final UserRoleRelMapper userRoleRelMapper;
    private final UserRoleService userRoleService;

    public CommonService(UserInfoMapper userInfoMapper,
                         UserRoleRelMapper userRoleRelMapper,
                         UserRoleService userRoleService) {
        this.userInfoMapper = userInfoMapper;
        this.userRoleRelMapper = userRoleRelMapper;
        this.userRoleService = userRoleService;
    }

    public UserPageVO getUserPage(Integer current, Integer size, String username, String email, List<String> roles) {
        Long currentUserId = currentUserIdRequired();
        boolean isAdmin = userRoleService.isAdmin(currentUserId);

        List<String> roleFilterCodes = resolveAccessibleRoleCodes(roles, isAdmin);
        List<String> visibleRoleCodes = visibleRoleCodes(isAdmin);
        String usernameKeyword = normalizeKeyword(username);
        String emailKeyword = normalizeKeyword(email);

        IPage<UserPageItemVO> pageResult = userInfoMapper.selectUserPageBasic(
                new Page<>(current, size), usernameKeyword, emailKeyword, roleFilterCodes);

        List<UserPageItemVO> records = pageResult.getRecords() == null ? Collections.emptyList() : pageResult.getRecords();
        fillRoles(records, visibleRoleCodes);

        UserPageVO vo = new UserPageVO();
        vo.setCurrent(pageResult.getCurrent());
        vo.setSize(pageResult.getSize());
        vo.setTotal(pageResult.getTotal());
        vo.setRecords(records);
        return vo;
    }

    private void fillRoles(List<UserPageItemVO> records, List<String> visibleRoleCodes) {
        if (records == null || records.isEmpty()) {
            return;
        }

        List<Long> userIds = records.stream().map(UserPageItemVO::getUserId).collect(Collectors.toList());
        List<UserRoleRowVO> rows = userRoleRelMapper.selectRoleCodesByUserIds(userIds, visibleRoleCodes);

        Map<Long, List<String>> roleMap = rows.stream().collect(
                Collectors.groupingBy(
                        UserRoleRowVO::getUserId,
                        Collectors.mapping(UserRoleRowVO::getRoleCode, Collectors.toList())
                )
        );

        for (UserPageItemVO item : records) {
            item.setRoles(roleMap.getOrDefault(item.getUserId(), Collections.emptyList()));
        }
    }

    private List<String> visibleRoleCodes(boolean isAdmin) {
        if (isAdmin) {
            return null;
        }
        return Arrays.asList(RoleType.USER.getCode(), RoleType.TEACHER.getCode());
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> resolveAccessibleRoleCodes(List<String> roles, boolean isAdmin) {
        List<String> normalizedRoles = normalizeRoles(roles);

        if (isAdmin) {
            return normalizedRoles.isEmpty() ? null : normalizedRoles;
        }

        if (normalizedRoles.isEmpty()) {
            return Arrays.asList(RoleType.USER.getCode(), RoleType.TEACHER.getCode());
        }

        for (String roleCode : normalizedRoles) {
            if (!RoleType.USER.getCode().equals(roleCode) && !RoleType.TEACHER.getCode().equals(roleCode)) {
                throw new ForbiddenException("教师只可以查询 USER 或者 TEACHER");
            }
        }
        return normalizedRoles;
    }

    private List<String> normalizeRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String role : roles) {
            if (!StringUtils.hasText(role)) {
                continue;
            }
            String roleCode = role.trim().toUpperCase(Locale.ROOT);
            boolean validRole = Arrays.stream(RoleType.values())
                    .map(RoleType::getCode)
                    .anyMatch(roleCode::equals);
            if (!validRole) {
                throw new BadRequestException("无效的角色");
            }
            normalized.add(roleCode);
        }

        return normalized.isEmpty() ? Collections.emptyList() : new ArrayList<>(normalized);
    }

    private Long currentUserIdRequired() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.isGuest()) {
            throw new AuthorizationException("用户未登录");
        }
        return ctx.getUserId();
    }
}