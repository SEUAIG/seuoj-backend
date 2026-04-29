package com.seuoj.seuojbackend.client.dto;

import lombok.Data;

@Data
public class JudgeOnlineSubmissionResultDetailItem {
    private String ans;
    private Integer id;
    private String in;
    private Long mem;
    private String out;
    private String sys;
    private Long time;
    private String type;
}
