package com.seuoj.seuojbackend.dto.assignment;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AssignmentUpdateDTO {
    private String title;
    private String description;
    private String status;
    private LocalDateTime deadline;
}
