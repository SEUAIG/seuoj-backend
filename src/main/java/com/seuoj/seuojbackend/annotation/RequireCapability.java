package com.seuoj.seuojbackend.annotation;

import com.seuoj.seuojbackend.common.Capability;
import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireCapability {
    Capability[] value();
}
