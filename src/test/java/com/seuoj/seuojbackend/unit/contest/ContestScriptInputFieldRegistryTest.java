package com.seuoj.seuojbackend.unit.contest;

import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.entity.SubmissionDetail;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.service.contest.custom.ContestScriptInputFieldRegistry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContestScriptInputFieldRegistryTest {

    private final ContestScriptInputFieldRegistry registry = new ContestScriptInputFieldRegistry();

    @Test
    void shouldRejectIllegalFieldKey() {
        assertThatThrownBy(() -> registry.normalizeEnabledFields(List.of("id", "bad_key")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("非法输入字段 key");
    }

    @Test
    void shouldBuildDefaultFieldsWhenNoProfileConfigured() {
        Submission submission = new Submission()
                .setId(7L)
                .setLanguage("cpp")
                .setScore(100)
                .setVerdict("Accepted")
                .setSubmitTime(LocalDateTime.of(2026, 5, 20, 10, 0, 0));
        SubmissionDetail detail = new SubmissionDetail().setErrorDetail("compile error");

        Map<String, Object> input = registry.buildSubmissionInput(submission, detail, List.of());
        assertThat(input).containsAllEntriesOf(Map.of(
            "id", 7L,
            "language", "cpp",
            "score", 100,
            "verdict", "Accepted",
            "submit_time", "2026-05-20T10:00",
            "error_detail", "compile error",
            "error_length", 13
        ));
    }

    @Test
    void shouldFillDefaultValueWhenFieldMissing() {
        Submission submission = new Submission();

        Map<String, Object> input = registry.buildSubmissionInput(submission, null, List.of("id", "error_length", "language"));
        assertThat(input.get("id")).isEqualTo(0L);
        assertThat(input.get("error_length")).isEqualTo(0);
        assertThat(input.get("language")).isEqualTo("");
    }
}
