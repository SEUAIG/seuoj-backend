package com.seuoj.seuojbackend.integration.contest;

import com.seuoj.seuojbackend.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContestScriptInputProfileIntegrationTest extends BaseIntegrationTest {

    @Test
    void teacherShouldGetAndUpdateCustomContestScriptInputProfile() throws Exception {
        String createBody = """
                {
                  "title": "Custom Contest",
                  "subtitle": "s",
                  "description": "d",
                  "start_time": "2026-05-20T10:00:00",
                  "end_time": "2026-05-20T12:00:00",
                  "rule_type": "CUSTOM",
                  "is_public": true,
                  "scoring_script": "function computeStandings(input){return [];}"
                }
                """;

        String createResp = mockMvc.perform(post("/api/contest")
                        .header("Authorization", bearerToken(10003L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long contestId = objectMapper.readTree(createResp).path("data").path("contest_id").asLong();

        mockMvc.perform(get("/api/contest/{id}/script-input-profile", contestId)
                        .header("Authorization", bearerToken(10003L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.available_fields[0].key").exists())
                .andExpect(jsonPath("$.data.enabled_fields").isArray());

        mockMvc.perform(put("/api/contest/{id}/script-input-profile", contestId)
                        .header("Authorization", bearerToken(10003L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled_fields\":[\"id\",\"score\",\"error_length\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/contest/{id}/script-input-profile", contestId)
                        .header("Authorization", bearerToken(10003L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled_fields[0]").value("id"))
                .andExpect(jsonPath("$.data.enabled_fields[1]").value("score"))
                .andExpect(jsonPath("$.data.enabled_fields[2]").value("error_length"));
    }

    @Test
    void shouldRejectInvalidFieldKey() throws Exception {
        String createBody = """
                {
                  "title": "Custom Contest 2",
                  "start_time": "2026-05-20T10:00:00",
                  "end_time": "2026-05-20T12:00:00",
                  "rule_type": "CUSTOM",
                  "is_public": true,
                  "scoring_script": "function computeStandings(input){return [];}"
                }
                """;

        String createResp = mockMvc.perform(post("/api/contest")
                        .header("Authorization", bearerToken(10003L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long contestId = objectMapper.readTree(createResp).path("data").path("contest_id").asLong();

        mockMvc.perform(put("/api/contest/{id}/script-input-profile", contestId)
                        .header("Authorization", bearerToken(10003L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled_fields\":[\"id\",\"bad\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }
}
