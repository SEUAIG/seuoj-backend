package com.seuoj.seuojbackend.vo.common;

import java.util.List;
import lombok.Data;

@Data
public class UserPageVO {

    private Long current;

    private Long size;

    private Long total;

    private List<UserPageItemVO> records;
}