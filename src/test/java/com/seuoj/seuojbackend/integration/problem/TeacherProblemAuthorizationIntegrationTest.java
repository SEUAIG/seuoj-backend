package com.seuoj.seuojbackend.integration.problem;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 教师题目权限集成测试：
 * 可创建；仅可编辑自己创建/被授权题目。
 */
class TeacherProblemAuthorizationIntegrationTest extends BaseIntegrationTest {

    @MockBean
    private JudgeClient judgeClient;

    @Autowired
    private ProblemMapper problemMapper;

    @Test
    void teacherShouldCreateProblem() throws Exception {
        String pid = "TTEACHERCREATE1";
        String requestBody = """
                {
                  "pid": "TTEACHERCREATE1",
                  "title": "Teacher Created Problem",
                  "is_public": false,
                  "example": [{"in":"1","ans":"1"}]
                }
                """;

        mockMvc.perform(post("/api/problem")
                        .header("Authorization", bearerToken(10003L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.pid").value(pid));

        Problem created = problemMapper.selectOne(new LambdaQueryWrapper<Problem>().eq(Problem::getPid, pid));
        assertThat(created).isNotNull();
        assertThat(created.getCreatedByUserId()).isEqualTo(10003L);
    }

    @Test
    void teacherShouldEditOwnProblem() throws Exception {
        String pid = "TTEACHEROWN1";
        mockMvc.perform(post("/api/problem")
                        .header("Authorization", bearerToken(10003L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pid": "TTEACHEROWN1",
                                  "title": "Before Edit",
                                  "is_public": true,
                                  "example": [{"in":"1","ans":"1"}]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(patch("/api/problem/edit")
                        .header("Authorization", bearerToken(10003L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pid": "TTEACHEROWN1",
                                  "title": "After Edit By Teacher"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Problem edited = problemMapper.selectOne(new LambdaQueryWrapper<Problem>().eq(Problem::getPid, pid));
        assertThat(edited).isNotNull();
        assertThat(edited.getTitle()).isEqualTo("After Edit By Teacher");
    }

    @Test
    void teacherShouldNotEditProblemWithoutWritePermission() throws Exception {
        Problem adminOwned = new Problem()
                .setPid("TADMINOWNED1")
                .setTitle("Admin Owned")
                .setTotalSubmit(0)
                .setTotalAccept(0)
                .setIsPublic(true)
                .setCreatedByUserId(10001L);
        problemMapper.insert(adminOwned);

        mockMvc.perform(patch("/api/problem/edit")
                        .header("Authorization", bearerToken(10003L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pid": "TADMINOWNED1",
                                  "title": "Teacher Should Not Edit"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }
}
