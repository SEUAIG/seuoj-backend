package com.seuoj.seuojbackend.vo.assignment;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class AssignmentDetailVO {

    @JsonProperty("assignment_id")
    private Long assignmentId;

    @JsonProperty("class_id")
    private Long classId;

    private String title;

    private String description;

    private String introduction;

    private String status;

    private LocalDateTime deadline;

    @JsonProperty("visible_from")
    private LocalDateTime visibleFrom;

    @JsonProperty("visible_to")
    private LocalDateTime visibleTo;

    @JsonProperty("problem_count")
    private Integer problemCount;

    @JsonProperty("member_count")
    private Integer memberCount;

    @JsonProperty("avg_completion_rate")
    private Double avgCompletionRate;

    @JsonProperty("can_write")
    private Boolean canWrite;

    private List<ProblemItem> problems;

    @JsonProperty("intro_attachments")
    private List<IntroAttachmentItem> introAttachments;

    @Data
    public static class ProblemItem {
        @JsonProperty("problem_id")
        private Long problemId;
        private String pid;
        private String title;
        @JsonProperty("sort_order")
        private Integer sortOrder;
        private Integer weight;
    }

    @Data
    public static class IntroAttachmentItem {
        private Long id;
        @JsonProperty("file_path")
        private String filePath;
        @JsonProperty("file_name")
        private String fileName;
        @JsonProperty("file_size")
        private Long fileSize;
    }
}
