package com.seuoj.seuojbackend.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class JudgeClientLogAspect {

    @Around("execution(public * com.seuoj.seuojbackend.client.JudgeClient.*(..))")
    public Object aroundJudgeClient(ProceedingJoinPoint pjp) throws Throwable {
        return withMethodMdc(pjp);
    }

    @Around("execution(public * com.seuoj.seuojbackend.service.ProblemTestcaseService.proxyJudgeProblemApi(..))")
    public Object aroundJudgeProxy(ProceedingJoinPoint pjp) throws Throwable {
        return withMethodMdc(pjp);
    }

    private Object withMethodMdc(ProceedingJoinPoint pjp) throws Throwable {
        String oldClass = MDC.get("judgeCallerClass");
        String oldMethod = MDC.get("judgeCallerMethod");
        String className = pjp.getTarget() == null ? "UnknownClass" : pjp.getTarget().getClass().getSimpleName();
        String methodName = pjp.getSignature().getName();
        MDC.put("judgeCallerClass", className);
        MDC.put("judgeCallerMethod", methodName);
        try {
            return pjp.proceed();
        } finally {
            restore("judgeCallerClass", oldClass);
            restore("judgeCallerMethod", oldMethod);
        }
    }

    private void restore(String key, String value) {
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }
}
