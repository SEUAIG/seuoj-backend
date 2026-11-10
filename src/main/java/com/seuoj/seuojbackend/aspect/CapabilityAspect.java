package com.seuoj.seuojbackend.aspect;

import com.seuoj.seuojbackend.annotation.RequireCapability;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.service.UserRoleService;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

@Aspect @Component @RequiredArgsConstructor
public class CapabilityAspect {
    private final UserRoleService roles;
    @Before("@annotation(required)")
    public void check(RequireCapability required) {
        Long uid = AuthContexts.requiredUserId();
        if (Arrays.stream(required.value()).noneMatch(c -> roles.hasCapability(uid, c)))
            throw new ForbiddenException("没有此操作权限");
    }
}
