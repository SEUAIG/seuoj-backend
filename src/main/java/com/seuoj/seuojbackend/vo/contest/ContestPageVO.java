package com.seuoj.seuojbackend.vo.contest;

import java.util.List;
import lombok.Data;

@Data
public class ContestPageVO {

    private Long total;

    private Long current;

    private Long size;

    private List<ContestPageItemVO> records;
}
