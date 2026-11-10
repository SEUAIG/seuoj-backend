package com.seuoj.seuojbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seuoj.seuojbackend.client.dto.ProblemConfigDTO;
import com.seuoj.seuojbackend.controller.api.ContestController;
import com.seuoj.seuojbackend.controller.api.SubmissionController;
import com.seuoj.seuojbackend.common.AuthStatus;
import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.common.SubmissionVerdict;
import com.seuoj.seuojbackend.entity.*;
import com.seuoj.seuojbackend.exception.AuthorizationException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.UserContext;
import com.seuoj.seuojbackend.interceptor.UserContextHolder;
import com.seuoj.seuojbackend.mapper.*;
import com.seuoj.seuojbackend.model.JudgeResultDetailItem;
import com.seuoj.seuojbackend.storage.CodeStorage;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ContestSubmissionVisibilityTest {
    private final SubmissionMapper submissions = mock(SubmissionMapper.class);
    private final SubmissionDetailMapper details = mock(SubmissionDetailMapper.class);
    private final ContestMapper contests = mock(ContestMapper.class);
    private final ContestSubmissionMapper links = mock(ContestSubmissionMapper.class);
    private final ProblemMapper problems = mock(ProblemMapper.class);
    private final UserInfoMapper users = mock(UserInfoMapper.class);
    private final UserRoleService roles = mock(UserRoleService.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final CodeStorage code = mock(CodeStorage.class);
    private final ContestSubmissionPolicy policy = new ContestSubmissionPolicy(permissions, roles);
    private final SubmissionService generic = new SubmissionService(submissions, details, problems, null,
            null, code, users, roles, permissions, null, null, contests, links, policy);
    private final ContestService contestService = new ContestService(contests, null, null, links,
            submissions, details, problems, users, permissions, roles, null, null, code, null, null, policy);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private Contest contest;
    private Submission submission;
    private SubmissionDetail detail;

    @BeforeEach void setup() {
        UserContextHolder.set(UserContext.of(10L, AuthStatus.AUTHENTICATED));
        contest = new Contest().setId(7L).setRuleType("ICPC")
                .setStartTime(LocalDateTime.now().minusHours(1)).setEndTime(LocalDateTime.now().plusHours(1));
        submission = new Submission().setId(90L).setSubmissionNo("submission-fixture")
                .setUserId(10L).setProblemId(30L).setLanguage("Cpp")
                .setStatus("Finished").setVerdict("WrongAnswer").setScore(50)
                .setSubmitTime(LocalDateTime.now().minusMinutes(1)).setFinishTime(LocalDateTime.now());
        Problem problem = new Problem();
        problem.setId(30L);
        problem.setPid("P1000");
        problem.setTitle("Fixture");
        UserInfo user = new UserInfo();
        user.setUsername("participant");
        ProblemConfigDTO.Subtask subtask = new ProblemConfigDTO.Subtask();
        subtask.setId(1);
        subtask.setCases(List.of(1, 2));
        subtask.setPreSubtasks(List.of());
        subtask.setScore(100);
        subtask.setType("min");
        detail = new SubmissionDetail().setSubmissionId(90L)
                .setResultDetail(List.of(testcase(1, 70L, 4096L), testcase(2, 30L, 8192L)))
                .setSubtasks(List.of(subtask)).setErrorDetail("SECRET test 2 failed");
        when(submissions.selectOne(any())).thenReturn(submission);
        when(details.selectById(90L)).thenReturn(detail);
        when(problems.selectById(30L)).thenReturn(problem);
        when(users.selectById(10L)).thenReturn(user);
        when(contests.selectById(7L)).thenReturn(contest);
        when(links.selectCount(any())).thenReturn(1L);
        when(links.findContestIdBySubmissionId(90L)).thenReturn(7L);
        when(code.getCode("submission-fixture")).thenReturn("int main() { return 0; }");
    }

    private JudgeResultDetailItem testcase(int id, Long time, Long memory) {
        JudgeResultDetailItem item = new JudgeResultDetailItem();
        item.setId(id);
        item.setIn("SECRET input " + id);
        item.setOut("SECRET echoed input " + id);
        item.setAns("SECRET answer " + id);
        item.setSys("SECRET checker says case " + id);
        item.setTime(time);
        item.setMem(memory);
        item.setType(SubmissionVerdict.fromString(id == 1 ? "Accepted" : "WrongAnswer"));
        item.setScore(id == 1 ? 100 : 0);
        return item;
    }

    @AfterEach void cleanup() { UserContextHolder.clear(); }

    private List<JsonNode> bothEndpoints() {
        return List.of(json.valueToTree(generic.getResult("submission-fixture")),
                json.valueToTree(contestService.getContestSubmissionDetail(7L, "submission-fixture")));
    }

    private void assertSummary(JsonNode node, boolean genericResponse) {
        assertFalse(node.has(genericResponse ? "resultDetail" : "result_detail"));
        assertFalse(node.has("subtasks"));
        assertFalse(node.has("score"));
        assertFalse(node.has(genericResponse ? "errorDetail" : "error_detail"));
        assertFalse(node.toString().contains("SECRET"));
        assertEquals(70L, node.path(genericResponse ? "maxTime" : "max_time").asLong());
        assertEquals(8192L, node.path(genericResponse ? "maxMemory" : "max_memory").asLong());
        assertEquals("int main() { return 0; }", node.path("code").asText());
        assertFalse(node.path(genericResponse ? "testDataVisible" : "test_data_visible").asBoolean());
        if (genericResponse) assertFalse(node.has("finishTime"));
    }

    @ParameterizedTest @ValueSource(strings = {"ICPC", "NOI", "CUSTOM", "unknown"})
    void nonIoiOwnerReceivesOnlySummaryFromEitherEndpoint(String rule) {
        contest.setRuleType(rule);
        List<JsonNode> responses = bothEndpoints();
        assertSummary(responses.get(0), true);
        assertSummary(responses.get(1), false);
        assertEquals("SECRET input 1", detail.getResultDetail().getFirst().getIn());
    }

    @Test void endedContestDoesNotExposeCases() {
        contest.setEndTime(LocalDateTime.now().minusMinutes(1));
        List<JsonNode> responses = bothEndpoints();
        assertSummary(responses.get(0), true);
        assertSummary(responses.get(1), false);
    }

    @Test void ioiOwnerGetsScoresButNoInputOutputAnswerOrMessages() {
        contest.setRuleType("IOI");
        List<JsonNode> responses = bothEndpoints();
        for (int i = 0; i < responses.size(); i++) {
            JsonNode response = responses.get(i);
            assertEquals(50, response.path("score").asInt());
            assertEquals(1, response.path("subtasks").size());
            JsonNode cases = response.path(i == 0 ? "resultDetail" : "result_detail");
            assertEquals(2, cases.size());
            assertEquals("WrongAnswer", cases.get(1).path("type").asText());
            for (JsonNode item : cases) for (String field : List.of("in", "out", "ans", "sys")) {
                assertFalse(item.has(field), field);
            }
            assertFalse(response.toString().contains("SECRET"));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"admin", "contest"})
    void authorizedManagersKeepDiagnosticData(String permission) {
        if (permission.equals("admin")) when(roles.isAdmin(10L)).thenReturn(true);
        else when(permissions.hasPermission(10L, ResourceType.CONTEST, 7L, PermissionOp.WRITE)).thenReturn(true);
        for (JsonNode response : bothEndpoints()) assertTrue(response.toString().contains("SECRET input 1"));
    }

    @Test void problemEditorStillReceivesOnlyOwnContestFeedback() {
        when(permissions.hasPermission(10L, ResourceType.PROBLEM, 30L, PermissionOp.WRITE)).thenReturn(true);
        List<JsonNode> responses = bothEndpoints();
        assertSummary(responses.get(0), true);
        assertSummary(responses.get(1), false);
    }

    @Test void problemEditorCannotReadAnotherContestantsSubmission() {
        UserContextHolder.set(UserContext.of(11L, AuthStatus.AUTHENTICATED));
        when(permissions.hasPermission(11L, ResourceType.PROBLEM, 30L, PermissionOp.WRITE)).thenReturn(true);
        assertThrows(ForbiddenException.class, () -> generic.getResult("submission-fixture"));
        assertThrows(ForbiddenException.class, () -> contestService.getContestSubmissionDetail(7L, "submission-fixture"));
        verifyNoInteractions(code);
    }

    @ParameterizedTest @ValueSource(strings = {"Pending", "Running"})
    void inProgressOwnerCannotInferProgressByPolling(String status) {
        submission.setStatus(status);
        for (String rule : List.of("ICPC", "IOI")) {
            contest.setRuleType(rule);
            for (JsonNode response : bothEndpoints()) {
                assertFalse(response.has("resultDetail"));
                assertFalse(response.has("result_detail"));
                assertFalse(response.has("subtasks"));
                assertFalse(response.has("score"));
                assertTrue(response.path("maxTime").isMissingNode() || response.path("maxTime").isNull());
                assertTrue(response.path("max_time").isMissingNode() || response.path("max_time").isNull());
                assertEquals("int main() { return 0; }", response.path("code").asText());
            }
        }
    }

    @Test void compileErrorsAreRetainedAndBounded() {
        submission.setVerdict("CompileError");
        detail.setErrorDetail("compiler diagnostic ".repeat(200));
        List<JsonNode> responses = bothEndpoints();
        assertEquals(2000, responses.get(0).path("errorDetail").asText().length());
        assertEquals(2000, responses.get(1).path("error_detail").asText().length());
    }

    @ParameterizedTest @ValueSource(strings = {"RuntimeError", "JudgeError", "SystemError"})
    void runtimeAndJudgeMessagesCannotExposeCases(String verdict) {
        submission.setVerdict(verdict).setStatus("Failed");
        for (JsonNode response : bothEndpoints()) assertFalse(response.toString().contains("SECRET"));
    }

    @Test void anotherParticipantCannotReadCodeThroughEitherEndpoint() {
        UserContextHolder.set(UserContext.of(11L, AuthStatus.AUTHENTICATED));
        assertThrows(ForbiddenException.class, () -> generic.getResult("submission-fixture"));
        assertThrows(ForbiddenException.class, () -> contestService.getContestSubmissionDetail(7L, "submission-fixture"));
        verifyNoInteractions(code);
    }

    @Test void teacherCannotUseGenericEndpointToReadOtherContestantsCode() {
        UserContextHolder.set(UserContext.of(11L, AuthStatus.AUTHENTICATED));
        when(roles.isTeacherOrAdmin(11L)).thenReturn(true);
        assertThrows(ForbiddenException.class, () -> generic.getResult("submission-fixture"));
        assertThrows(ForbiddenException.class, () -> contestService.getContestSubmissionDetail(7L, "submission-fixture"));
    }

    @Test void contestManagerCanReadAnotherParticipantsCode() {
        UserContextHolder.set(UserContext.of(11L, AuthStatus.AUTHENTICATED));
        when(permissions.hasPermission(11L, ResourceType.CONTEST, 7L, PermissionOp.WRITE)).thenReturn(true);
        for (JsonNode response : bothEndpoints()) assertEquals("int main() { return 0; }", response.path("code").asText());
    }

    @Test void contestReadPermissionIsRequiredOnGenericEndpointToo() {
        doThrow(new ForbiddenException("无权操作")).when(permissions)
                .assertPermission(10L, ResourceType.CONTEST, 7L, PermissionOp.READ);
        assertThrows(ForbiddenException.class, () -> generic.getResult("submission-fixture"));
        assertThrows(ForbiddenException.class, () -> contestService.getContestSubmissionDetail(7L, "submission-fixture"));
    }

    @Test void deletedContestFailsClosedInsteadOfFallingBackToPractice() {
        when(contests.selectById(7L)).thenReturn(null);
        assertThrows(NotFoundException.class, () -> generic.getResult("submission-fixture"));
        verifyNoInteractions(code);
    }

    @Test void mismatchedContestCannotBeUsedToSelectAnotherVisibilityPolicy() {
        when(links.selectCount(any())).thenReturn(0L);
        assertThrows(NotFoundException.class, () -> contestService.getContestSubmissionDetail(7L, "submission-fixture"));
    }

    @Test void ordinaryPracticeKeepsExistingFeedback() {
        when(links.findContestIdBySubmissionId(90L)).thenReturn(null);
        JsonNode response = json.valueToTree(generic.getResult("submission-fixture"));
        assertEquals("SECRET input 1", response.path("resultDetail").get(0).path("in").asText());
        assertFalse(response.has("contestId"));
    }

    @Test void missingAndNullCaseMetricsDoNotInventZeroMeasurements() {
        detail.setResultDetail(Arrays.asList(null, testcase(1, null, null)));
        for (JsonNode response : bothEndpoints()) {
            assertTrue(response.path("maxTime").isMissingNode() || response.path("maxTime").isNull());
            assertTrue(response.path("max_time").isMissingNode() || response.path("max_time").isNull());
        }
        when(details.selectById(90L)).thenReturn(null);
        for (JsonNode response : bothEndpoints()) assertFalse(response.toString().contains("SECRET"));
    }

    @Test void anonymousCannotReadEitherEndpoint() {
        UserContextHolder.set(UserContext.guest());
        assertThrows(AuthorizationException.class, () -> generic.getResult("submission-fixture"));
        assertThrows(AuthorizationException.class, () -> contestService.getContestSubmissionDetail(7L, "submission-fixture"));
    }

    @Test void httpResponsesContainOnlyThePermittedFields() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new SubmissionController(generic, mock(SubmissionSkipService.class)),
                new ContestController(contestService, mock(SubmissionSkipService.class))).build();
        for (String path : List.of("/api/submission/submission-fixture", "/api/contest/7/submission/submission-fixture")) {
            mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.code").value("int main() { return 0; }"))
                    .andExpect(jsonPath("$.data.resultDetail").doesNotExist())
                    .andExpect(jsonPath("$.data.result_detail").doesNotExist())
                    .andExpect(jsonPath("$.data.subtasks").doesNotExist())
                    .andExpect(jsonPath("$.data.errorDetail").doesNotExist())
                    .andExpect(jsonPath("$.data.error_detail").doesNotExist())
                    .andExpect(jsonPath("$.data.score").doesNotExist());
        }
    }
}
