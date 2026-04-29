package com.seuoj.seuojbackend.vo.submission;

import com.seuoj.seuojbackend.client.dto.JudgeOnlineSubmissionResultDetailItem;
import java.util.List;
import lombok.Data;

@Data
public class OnlineJudgeResultVO {
    private List<JudgeOnlineSubmissionResultDetailItem> resultDetail;
    private String status;
}
