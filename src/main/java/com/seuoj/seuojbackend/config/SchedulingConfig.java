package com.seuoj.seuojbackend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 开启 @Scheduled（目前只有判题并发同步） */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
