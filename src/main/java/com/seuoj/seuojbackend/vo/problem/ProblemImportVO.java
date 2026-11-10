package com.seuoj.seuojbackend.vo.problem;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/**
 * 题目包导入结果
 */
@Data
public class ProblemImportVO {
    private String pid;

    /**
     * 题目包 testdata 目录中实际上传到评测端的文件数量
     */
    @JsonProperty("data_file_count")
    private Integer dataFileCount;

    /**
     * 评测配置中的测试点数量
     */
    @JsonProperty("testcase_count")
    private Integer testcaseCount;

    /**
     * 是否为特判题目（评测端已成功编译 checker）
     */
    @JsonProperty("checker_compiled")
    private Boolean checkerCompiled;

    /**
     * 不阻断导入、但需要出题人关注的问题（如题面缺少某个章节）
     */
    private List<String> warnings;
}
