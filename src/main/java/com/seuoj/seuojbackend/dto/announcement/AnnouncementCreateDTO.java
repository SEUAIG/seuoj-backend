package com.seuoj.seuojbackend.dto.announcement;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;

@Data
public class AnnouncementCreateDTO {

    @NotBlank(message = "title 不能为空")
    private String title;

    private String content;

    @JsonProperty("is_pinned")
    private Boolean isPinned;

    @Valid
    private List<AttachmentDTO> attachments;
}
