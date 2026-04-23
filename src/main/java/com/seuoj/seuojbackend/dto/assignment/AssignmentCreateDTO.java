package com.seuoj.seuojbackend.dto.assignment;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AssignmentCreateDTO {
    private Long problemSetId;
    private String title;
    private String description;
    private LocalDateTime deadline;
}
