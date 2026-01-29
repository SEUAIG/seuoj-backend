package com.seuoj.seuojbackend.vo.submission;

import java.util.List;

import lombok.Data;

@Data
public class SubmissionPageVO {
    private long current;
    private long size;
    private long total;
    private List<SubmissionListItemVO> records;
}
