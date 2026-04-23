package com.seuoj.seuojbackend.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.seuoj.seuojbackend.client.dto.ProblemConfigDTO;
import com.seuoj.seuojbackend.model.JudgeResultDetailItem;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.apache.ibatis.type.JdbcType;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName(value = "submission_detail", autoResultMap = true)
public class SubmissionDetail implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "submission_id", type = IdType.INPUT)
    private Long submissionId;

    @TableField(value = "result_detail", typeHandler = JacksonTypeHandler.class, jdbcType = JdbcType.VARCHAR)
    private List<JudgeResultDetailItem> resultDetail;

    @TableField(value = "subtasks", typeHandler = JacksonTypeHandler.class, jdbcType = JdbcType.VARCHAR)
    private List<ProblemConfigDTO.Subtask> subtasks;

    @TableField("error_detail")
    private String errorDetail;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}
