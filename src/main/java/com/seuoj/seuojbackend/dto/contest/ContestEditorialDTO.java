package com.seuoj.seuojbackend.dto.contest;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 保存比赛官方题解（不存在就新建）。
 * attachments 是保存后的完整附件列表：带 id 的保留原附件，不带 id 的是刚上传的新文件，列表里没有的旧附件被删除。
 */
public record ContestEditorialDTO(
        @NotBlank(message = "标题不能为空") @Size(max = 120, message = "标题最多 120 字") String title,
        @Size(max = 100000, message = "正文最多 100000 字") String content,
        @NotBlank(message = "状态不能为空") String status,
        @JsonProperty("visible_after") LocalDateTime visibleAfter,
        @Valid @Size(max = 5, message = "最多 5 个附件") List<Attachment> attachments) {

    public record Attachment(
            Long id,
            @JsonProperty("file_path") @Size(max = 500) String filePath,
            @JsonProperty("file_name") @Size(max = 200, message = "文件名最多 200 字") String fileName) {
    }
}
