package com.seuoj.seuojbackend.dto.announcement;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import java.util.List;
import lombok.Data;

@Data
public class AnnouncementUpdateDTO {

    private String title;

    private String content;

    @JsonProperty("is_pinned")
    private Boolean isPinned;

    @Valid
    @JsonProperty("add_attachments")
    private List<AttachmentDTO> addAttachments;

    @JsonProperty("remove_attachment_ids")
    private List<Long> removeAttachmentIds;
}
