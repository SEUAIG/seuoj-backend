package com.seuoj.seuojbackend.unit.pack;

import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.pack.HydroStatement;
import com.seuoj.seuojbackend.pack.HydroStatementParser;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HydroStatementParserTest {

    private final HydroStatementParser parser = new HydroStatementParser();

    private final List<String> warnings = new ArrayList<>();

    @Test
    void shouldParseCreateHydroStatement() {
        HydroStatement statement = parser.parse("""
                ## 题目背景

                背景一段。

                ## 题目描述

                描述一段。

                ## 输入格式

                一行一个整数 $n$。

                ## 输出格式

                一行一个整数。

                ## 样例

                ```input1
                1 2
                ```

                ```output1
                3
                ```

                ```input2
                -5 5
                ```

                ```output2
                0
                ```

                ### 样例 1 解释

                第一组解释。

                ### 样例 2 解释

                第二组解释。

                ## 数据范围

                $1 \\le n \\le 10$。
                """, "problem_zh.md", warnings);

        assertThat(statement.description()).isEqualTo("背景一段。\n\n描述一段。");
        assertThat(statement.input()).isEqualTo("一行一个整数 $n$。");
        assertThat(statement.output()).isEqualTo("一行一个整数。");
        assertThat(statement.hint()).isEqualTo("$1 \\le n \\le 10$。");
        assertThat(statement.examples()).hasSize(2);
        assertThat(statement.examples().get(0).getIn()).isEqualTo("1 2");
        assertThat(statement.examples().get(0).getAns()).isEqualTo("3");
        assertThat(statement.examples().get(0).getDescription()).isEqualTo("第一组解释。");
        assertThat(statement.examples().get(1).getIn()).isEqualTo("-5 5");
        assertThat(statement.examples().get(1).getDescription()).isEqualTo("第二组解释。");
        assertThat(warnings).isEmpty();
    }

    @Test
    void shouldPairSamplesByNumberRegardlessOfOrder() {
        HydroStatement statement = parser.parse("""
                ## 题目描述

                d

                ## 样例

                ```input2
                second
                ```

                ```output2
                2
                ```

                ```input1
                first
                ```

                ```output1
                1
                ```
                """, "problem_zh.md", warnings);

        assertThat(statement.examples()).hasSize(2);
        assertThat(statement.examples().get(0).getIn()).isEqualTo("first");
        assertThat(statement.examples().get(0).getAns()).isEqualTo("1");
        assertThat(statement.examples().get(1).getIn()).isEqualTo("second");
        assertThat(statement.examples().get(1).getAns()).isEqualTo("2");
    }

    @Test
    void shouldKeepInnerWhitespaceOfSamples() {
        HydroStatement statement = parser.parse("""
                ## 题目描述

                d

                ## 样例

                ```input1
                3
                  1   2
                ```

                ```output1
                6
                ```
                """, "problem_zh.md", warnings);

        assertThat(statement.examples().get(0).getIn()).isEqualTo("3\n  1   2");
    }

    @Test
    void shouldWarnAboutMissingSectionsAndFoldUnknownOnesIntoHint() {
        HydroStatement statement = parser.parse("""
                ## 题目描述

                d

                ## 样例

                ```input1
                1
                ```

                ```output1
                1
                ```

                ## 子任务

                子任务说明。
                """, "problem_zh.md", warnings);

        assertThat(statement.hint()).isEqualTo("子任务说明。");
        assertThat(warnings).anyMatch(warning -> warning.contains("输入格式"))
                .anyMatch(warning -> warning.contains("输出格式"))
                .anyMatch(warning -> warning.contains("数据范围"))
                .anyMatch(warning -> warning.contains("子任务"));
    }

    @Test
    void shouldSeparateLimitsAndHintsWithSubHeadings() {
        HydroStatement statement = parser.parse("""
                ## 题目描述

                d

                ## 样例

                ```input1
                1
                ```

                ```output1
                1
                ```

                ## 数据范围

                $n \\le 10$。

                ## 提示

                可以用前缀和。
                """, "problem_zh.md", warnings);

        assertThat(statement.hint()).isEqualTo("""
                ### 数据范围

                $n \\le 10$。

                ### 提示

                可以用前缀和。""");
    }

    @Test
    void shouldNotSplitSectionsInsideFence() {
        HydroStatement statement = parser.parse("""
                ## 题目描述

                d

                ## 样例

                ```input1
                ## 输入格式
                ```

                ```output1
                ok
                ```
                """, "problem_zh.md", warnings);

        assertThat(statement.examples().get(0).getIn()).isEqualTo("## 输入格式");
        assertThat(statement.input()).isEmpty();
    }

    @Test
    void shouldRejectStatementWithoutSample() {
        assertThatThrownBy(() -> parser.parse("""
                ## 题目描述

                d
                """, "problem_zh.md", warnings))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("没有解析到样例");
    }

    @Test
    void shouldRejectStatementWithoutDescription() {
        assertThatThrownBy(() -> parser.parse("""
                ## 样例

                ```input1
                1
                ```

                ```output1
                1
                ```
                """, "problem_zh.md", warnings))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("题目描述");
    }
}
