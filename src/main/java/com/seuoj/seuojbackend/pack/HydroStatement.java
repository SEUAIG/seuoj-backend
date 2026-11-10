package com.seuoj.seuojbackend.pack;

import com.seuoj.seuojbackend.model.ProblemCommon;

import java.util.List;

/**
 * 从 Hydro 题面 Markdown（problem_zh.md）解析出的 SEUOJ 题面字段
 *
 * @param description 题目描述，含题目背景与首个标题之前的正文
 * @param input       输入格式
 * @param output      输出格式
 * @param hint        数据范围与提示：依次拼接数据范围、提示、未识别的章节
 * @param examples    样例，来自 input1 / output1 之类的围栏代码块
 */
public record HydroStatement(String description,
                             String input,
                             String output,
                             String hint,
                             List<ProblemCommon.Example> examples) {
}
