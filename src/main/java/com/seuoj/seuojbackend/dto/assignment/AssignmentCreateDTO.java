package com.seuoj.seuojbackend.dto.assignment;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.seuoj.seuojbackend.dto.announcement.AttachmentDTO;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class AssignmentCreateDTO {
    private String title;
    private String description;
    private String introduction;
    private LocalDateTime deadline;
    @JsonProperty("visible_from")
    private LocalDateTime visibleFrom;
    @JsonProperty("visible_to")
    private LocalDateTime visibleTo;
    @JsonProperty("problem_ids")
    private List<Long> problemIds;
    @JsonProperty("intro_attachments")
    private List<AttachmentDTO> introAttachments;
}
