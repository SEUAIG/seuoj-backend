package com.seuoj.seuojbackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 题目测试数据上传安全阈值配置
 */
@Data
@ConfigurationProperties(prefix = "problem.testcase")
public class ProblemTestcaseProperties {
    /**
     * 压缩包大小上限（字节）
     */
    private long maxArchiveSize = 50L * 1024 * 1024;

    /**
     * 最大文件数量
     */
    private int maxEntryCount = 200;

    /**
     * 单文件解包后大小上限（字节）
     */
    private long maxEntrySize = 5L * 1024 * 1024;

    /**
     * 解包后总大小上限（字节）
     */
    private long maxTotalSize = 200L * 1024 * 1024;

    /**
     * 最大压缩比（解包总大小 / 压缩包大小）
     */
    private int maxCompressionRatio = 50;

    /**
     * 单文件解码为文本后的最大字符数
     */
    private long maxTextSize = 5L * 1024 * 1024;

    /**
     * 单行最大长度（字符数）
     */
    private int maxLineLength = 200000;
}
