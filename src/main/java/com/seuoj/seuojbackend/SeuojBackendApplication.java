package com.seuoj.seuojbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.security.Security;
import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SeuojBackendApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        // 校园网 DNS 偶尔解析不了境外域名（如 smtp.resend.com，TTL 只有 60 秒）。
        // 解析失败时沿用上次成功的地址，最多 1 小时；须在任何域名解析之前设置。
        Security.setProperty("networkaddress.cache.stale.ttl", "3600");
        SpringApplication.run(SeuojBackendApplication.class, args);
    }

}
