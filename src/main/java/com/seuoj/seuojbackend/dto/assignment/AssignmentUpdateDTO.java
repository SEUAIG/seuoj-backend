package com.seuoj.seuojbackend.dto.assignment;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.seuoj.seuojbackend.dto.announcement.AttachmentDTO;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class AssignmentUpdateDTO {
    private String title;
    private String description;
    private String introduction;
    private String status;
    private LocalDateTime deadline;
    @JsonProperty("visible_from")
    private LocalDateTime visibleFrom;
    @JsonProperty("visible_to")
    private LocalDateTime visibleTo;
    @JsonProperty("add_intro_attachments")
    private List<AttachmentDTO> addIntroAttachments;
    @JsonProperty("remove_intro_attachment_ids")
    private List<Long> removeIntroAttachmentIds;
}
