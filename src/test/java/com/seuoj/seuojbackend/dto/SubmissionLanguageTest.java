package com.seuoj.seuojbackend.dto;

import com.seuoj.seuojbackend.dto.contest.ContestSubmitDTO;
import com.seuoj.seuojbackend.dto.submission.OnlineJudgeSubmitDTO;
import com.seuoj.seuojbackend.dto.submission.SubmitDTO;
import jakarta.validation.Validation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubmissionLanguageTest {
    @ParameterizedTest
    @ValueSource(strings = {"C", "Cpp", "Cpp20", "Python", "Nodejs", "Go", "Java", "Rust"})
    void acceptsSupportedLanguagesInEverySubmissionEntry(String language) {
        validateLanguage(language, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"rust", "Rust;sh", "Unknown", ""})
    void rejectsUnsupportedLanguagesInEverySubmissionEntry(String language) {
        validateLanguage(language, false);
    }

    private void validateLanguage(String language, boolean accepted) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (var type : new Class<?>[]{SubmitDTO.class, ContestSubmitDTO.class, OnlineJudgeSubmitDTO.class}) {
                assertEquals(accepted, validator.validateValue(type, "language", language).isEmpty(),
                        type.getSimpleName() + ": " + language);
            }
        }
    }
}
