package com.seuoj.seuojbackend.client.dto;

import java.util.List;
import lombok.Data;

/** 判题端 GET/PUT /judge/hosts 的返回 */
@Data
public class JudgeHostsStatus {
    private List<Host> hosts;
    /** 等待评测机的提交数 */
    private Integer waiting;
    /** 判题端接受的最多评测机数 */
    private Integer maxHosts;
    /** 判题端启动时的评测机数（.env 的 JUDGEND_MAX_CONCURRENT_REQUESTS） */
    private Integer defaultHosts;
    private Probe probe;

    /** 一台评测机：同一时间评一份提交，固定在一个 CPU 核上 */
    @Data
    public static class Host {
        private Integer id;
        private String name;
        /** 绑定的 CPU 核，判题端拿不到 CPU 信息时为 null */
        private Integer core;
        private Boolean enabled;
        /** 已删除，等手上这份评完就消失 */
        private Boolean removing;
        private Judging current;
        private Judging last;
        /** 判题端本次启动以来评过的份数 */
        private Long judged;
        /** 最近 5 分钟、15 分钟、1 小时、5 小时的忙碌比例（0–1） */
        private List<Double> load;
    }

    @Data
    public static class Judging {
        private String submissionId;
        /** Unix 毫秒 */
        private Long startedAt;
        private Long finishedAt;
    }

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
