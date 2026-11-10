package com.seuoj.seuojbackend.client.dto;

import java.util.List;
import lombok.Data;

/** 判题端 GET/PUT /judge/concurrency 的返回 */
@Data
public class JudgeConcurrencyStatus {
    /** 当前同时评测上限 */
    private Integer limit;
    /** 判题端 .env 里的默认值（判题端重启后回到它，直到后端再次同步） */
    private Integer defaultLimit;
    /** 判题端接受的最大值 */
    private Integer maxLimit;
    /** 正在评测 */
    private Integer running;
    /** 等待名额 */
    private Integer waiting;
    private Probe probe;

    /** 计时探针：每隔 intervalSeconds 在沙箱里跑同一个固定程序，记下测得用时 */
    @Data
    public static class Probe {
        private Integer intervalSeconds;
        /** 探针不可用时的原因 */
        private String error;
        private List<Sample> samples;
    }

    @Data
    public static class Sample {
        /** 开始时间，Unix 毫秒 */
        private Long at;
        /** 这次运行期间正在评测的提交数 */
        private Integer running;
        private Integer cpuMs;
        private Integer realMs;
    }
}
