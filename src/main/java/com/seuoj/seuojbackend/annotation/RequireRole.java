package com.seuoj.seuojbackend.annotation;

import com.seuoj.seuojbackend.common.RoleType;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {
    RoleType[] value();
}