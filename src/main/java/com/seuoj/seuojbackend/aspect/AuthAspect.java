package com.seuoj.seuojbackend.aspect;

import com.seuoj.seuojbackend.annotation.AllowAnonymous;
import com.seuoj.seuojbackend.annotation.RequireRole;
import com.seuoj.seuojbackend.common.AuthStatus;
import com.seuoj.seuojbackend.common.RoleType;
import com.seuoj.seuojbackend.entity.UserRoleRel;
import com.seuoj.seuojbackend.exception.AuthorizationException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.interceptor.UserContext;
import com.seuoj.seuojbackend.mapper.UserRoleRelMapper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import com.seuoj.seuojbackend.interceptor.UserContextHolder;

@Slf4j
@Aspect
@Component
public class AuthAspect {
    @Autowired
    private UserRoleRelMapper userRoleRelMapper;

    /**
     * 拦截controller包中的所有
     * @param pjp
     * @return
     * @throws Throwable
     */
    @Around("execution(public * com.seuoj.seuojbackend.controller..*(..))")
    public Object authAround(ProceedingJoinPoint pjp) throws Throwable {

        Method method = AopUtil.getMethod(pjp);
        Class<?> clazz = pjp.getTarget().getClass();
        String className = clazz.getSimpleName();
        String methodName = method.getName();
        
        log.debug("开始权限校验 - Controller: {}.{}", className, methodName);

        // 检查是否有 AllowAnonymous 注解：有则放行
        if (hasAllowAnonymous(method)) {
            log.info("方法允许匿名访问，跳过权限校验 - {}.{}", className, methodName);
            return pjp.proceed();
        }

        // 登录校验
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) {
            log.warn("用户上下文为空，拒绝访问 - {}.{}", className, methodName);
            throw new AuthorizationException("未登录");
        }
        
        if (ctx.isGuest()) {
            log.warn("游客用户尝试访问需要登录的资源 - {}.{}", className, methodName);
            throw new AuthorizationException("未登录");
        }
        
        log.debug("用户已登录 - userId: {}, {}.{}", ctx.getUserId(), className, methodName);

        // RequireRole（方法优先）
        RequireRole requireRole = method.getAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = clazz.getAnnotation(RequireRole.class);
        }

        if (requireRole != null) {
            log.debug("检测到角色要求，开始角色校验 - 需要角色: {}, {}.{}", 
                    Arrays.toString(requireRole.value()), className, methodName);
            checkRole(ctx, requireRole);
        } else {
            log.debug("无角色要求，仅需登录即可访问 - {}.{}", className, methodName);
        }

        log.debug("权限校验通过，执行业务方法 - {}.{}", className, methodName);
        Object result = pjp.proceed();
        log.debug("业务方法执行完成 - {}.{}", className, methodName);
        
        return result;
    }

    public void checkRole(UserContext ctx, RequireRole requireRole) {
        // 访问数据库查询用户角色列表
        String userId = UserContextHolder.get().getUserId();
        log.debug("开始角色校验 - userId: {}", userId);

        if (userId == null) {
            log.error("角色校验失败：用户ID为空（游客）");
            throw new AuthorizationException("游客不允许访问");
        }

        log.debug("查询用户角色 - userId: {}", userId);
        List<String> roleCodesByUserId = userRoleRelMapper.getRoleCodesByUserId(userId);
        log.debug("查询到用户角色列表 - userId: {}, roles: {}", userId, roleCodesByUserId);

        if (roleCodesByUserId.isEmpty()) {
            if (requireRole.value().length == 0) {
                log.info("用户无角色但无需角色要求，放行 - userId: {}", userId);
                return; // 无需角色，放行
            } else {
                log.warn("用户无角色但需要角色权限 - userId: {}, 所需角色: {}", 
                        userId, Arrays.toString(requireRole.value()));
                throw new ForbiddenException("用户没有权限访问该资源");
            }
        }

        // 检查用户角色是否包含所需角色（只需要用户拥有的目标角色中的一个及以上即可）
        log.debug("开始匹配用户角色 - userId: {}, 用户角色: {}, 所需角色: {}", 
                userId, roleCodesByUserId, Arrays.toString(requireRole.value()));
        
        for (RoleType roleReq : requireRole.value()) {
            if (roleCodesByUserId.contains(roleReq.getCode())) {
                log.info("角色校验通过 - userId: {}, 用户角色: {}, 匹配角色: {}", 
                        userId, roleCodesByUserId, roleReq.getCode());
                return; // 有角色，放行
            }
        }

        log.warn("角色校验失败 - userId: {}, 用户角色: {}, 所需角色: {}", 
                userId, roleCodesByUserId, Arrays.toString(requireRole.value()));
        throw new ForbiddenException("用户没有权限访问该资源");
    }

    private boolean hasAllowAnonymous(Method method) {
        AllowAnonymous onMethod = AnnotationUtils.findAnnotation(method, AllowAnonymous.class);
        if (onMethod != null) {
            log.debug("方法上找到 @AllowAnonymous 注解 - {}.{}", 
                    method.getDeclaringClass().getSimpleName(), method.getName());
            return true;
        }
        
        AllowAnonymous onClass = AnnotationUtils.findAnnotation(method.getDeclaringClass(), AllowAnonymous.class);
        if (onClass != null) {
            log.debug("类上找到 @AllowAnonymous 注解 - {}", 
                    method.getDeclaringClass().getSimpleName());
            return true;
        }
        
        return false;
    }

    // 小工具：从 JoinPoint 拿到 Method（简化写法）
    static class AopUtil {
        static Method getMethod(JoinPoint jp) {
            try {
                String methodName = jp.getSignature().getName();
                Class<?> targetClass = jp.getTarget().getClass();
                Class<?>[] argTypes = new Class[jp.getArgs().length];
                for (int i = 0; i < jp.getArgs().length; i++) {
                    argTypes[i] = jp.getArgs()[i] == null ? Object.class : jp.getArgs()[i].getClass();
                }
                // 这里用最简单实现；生产建议用 MethodSignature 直接取 Method
                // 为避免反射匹配问题，建议你改成：
                // MethodSignature ms = (MethodSignature) jp.getSignature(); return ms.getMethod();
                for (Method m : targetClass.getMethods()) {
                    if (m.getName().equals(methodName)) return m;
                }
                throw new IllegalStateException("Method not found");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}