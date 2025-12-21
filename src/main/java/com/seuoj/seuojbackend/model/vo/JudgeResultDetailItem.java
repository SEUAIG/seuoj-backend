package com.seuoj.seuojbackend.model.vo;

import lombok.Data;

/**
 * 描述单个判题测试用例结果的值对象。
 */
@Data
public class JudgeResultDetailItem {
    /** 测试数据组号 */
    private Integer cnt;

    /** 程序输入文本（有截断） */
    private String in;

    /** 用户输出文本（有截断） */
    private String out;

    /** 答案文本（有截断） */
    private String ans;

    /** 系统输出 */
    private String sys;

    /** 耗时（ms） */
    private Long time;

    /** 内存占用（byte） */
    private Long mem;
}