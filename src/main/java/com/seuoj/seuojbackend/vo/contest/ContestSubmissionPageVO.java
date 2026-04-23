package com.seuoj.seuojbackend.vo.contest;

import java.util.List;
import lombok.Data;

@Data
public class ContestSubmissionPageVO {

    private Long current;

    private Long size;

    private Long total;

    private List<ContestSubmissionRecordVO> records;
}
