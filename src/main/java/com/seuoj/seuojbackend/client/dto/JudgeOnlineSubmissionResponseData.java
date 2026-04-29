package com.seuoj.seuojbackend.client.dto;

import java.util.List;
import lombok.Data;

@Data
public class JudgeOnlineSubmissionResponseData {
    private List<JudgeOnlineSubmissionResultDetailItem> resultDetail;
    private String status;
}
