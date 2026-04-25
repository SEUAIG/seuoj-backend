package com.seuoj.seuojbackend.vo.problem;

import lombok.Data;

import java.util.List;

@Data
public class ProblemStatisticsVO {
    private Integer totalSubmit;
    private Integer totalAccept;
    private Double acceptRate;
    private List<ScoreDistributionItem> scoreDistribution;
    private List<SubmissionTrendItem> submissionTrend;

    @Data
    public static class ScoreDistributionItem {
        private String range;
        private Integer count;
    }

    @Data
    public static class SubmissionTrendItem {
        private String date;
        private Integer count;
    }
}
