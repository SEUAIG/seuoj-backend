package com.seuoj.seuojbackend.model;

import com.seuoj.seuojbackend.common.SubmissionVerdict;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 描述单个判题测试用例结果的值对象。
 */
@Data
public class JudgeResultDetailItem {
    /**
     * 测试数据组号
     */
    @NotNull(message = "测试数据组号不能为空")
    private Integer id;

    /**
     * 程序输入文本（有截断）
     */
    @NotNull(message = "程序输入文本不能为空")
    private String in;

    /**
     * 用户输出文本（有截断）
     */
    private String out;

    /**
     * 答案文本（有截断）
     */
    private String ans;

    /**
     * 系统输出
     */
    @NotBlank(message = "系统输出不能为空")
    private String sys;

    /**
     * 耗时（ms）
     */
    @NotNull(message = "耗时不能为空")
    private Long time;

    /**
     * 内存占用（byte）
     */
    @NotNull(message = "内存占用不能为空")
    private Long mem;

    /**
     * 每一个测试点测试结果
     */
    @NotNull(message = "测试点测试结果不能为空")
    private SubmissionVerdict type;

    /**
     * 分数
     */
    @NotNull(message = "分数不能为空")
    private Integer score;
}
