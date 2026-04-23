package com.seuoj.seuojbackend.vo.announcement;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class AnnouncementVO {

    @JsonProperty("announcement_id")
    private Long announcementId;

    private String title;

    private String content;

    @JsonProperty("is_pinned")
    private Boolean isPinned;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    @JsonProperty("created_by_user_id")
    private Long createdByUserId;

    @JsonProperty("created_by_username")
    private String createdByUsername;

    @JsonProperty("created_by_nickname")
    private String createdByNickname;

    private List<AttachmentVO> attachments;
}
