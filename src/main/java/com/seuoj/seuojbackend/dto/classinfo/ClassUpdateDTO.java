package com.seuoj.seuojbackend.dto.classinfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.seuoj.seuojbackend.dto.announcement.AttachmentDTO;
import jakarta.validation.Valid;
import java.util.List;
import lombok.Data;

@Data
public class ClassUpdateDTO {

    /**
     * 班级名称
     */
    private String name;

    /**
     * 班级描述
     */
    private String description;

    /**
     * 班级介绍（Markdown 富文本）
     */
    private String introduction;

    /**
     * 是否公开班级
     */
    @JsonProperty("is_public")
    private Boolean isPublic;

    @Valid
    @JsonProperty("add_intro_attachments")
    private List<AttachmentDTO> addIntroAttachments;

    @JsonProperty("remove_intro_attachment_ids")
    private List<Long> removeIntroAttachmentIds;
}