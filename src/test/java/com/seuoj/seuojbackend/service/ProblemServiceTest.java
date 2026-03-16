package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.client.dto.JudgeProblemEditRequest;
import com.seuoj.seuojbackend.dto.problem.ProblemCreateDTO;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.ProblemTagRelMapper;
import com.seuoj.seuojbackend.mapper.TagMapper;
import com.seuoj.seuojbackend.vo.problem.ProblemCreateVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProblemServiceTest {

    @Mock
    private ProblemMapper problemMapper;
    @Mock
    private JudgeClient judgeClient;
    @Mock
    private TagMapper tagMapper;
    @Mock
    private ProblemTagRelMapper problemTagRelMapper;

    private ProblemService problemService;

    @BeforeEach
    void setUp() {
        problemService = new ProblemService(
                problemMapper,
                judgeClient,
                tagMapper,
                problemTagRelMapper,
                new ProblemPidGenerator()
        );
    }

    @Test
    void createProblem_shouldGeneratePidFromInsertedId() {
        doAnswer(invocation -> {
            Problem problem = invocation.getArgument(0);
            problem.setId(12L);
            return 1;
        }).when(problemMapper).insert(any(Problem.class));

        ProblemCreateDTO dto = new ProblemCreateDTO();
        dto.setTitle("Two Sum");
        dto.setIsPublic(true);

        ProblemCreateVO result = problemService.createProblem(dto);

        assertThat(result.getPid()).isEqualTo("p12");

        ArgumentCaptor<Problem> updateCaptor = ArgumentCaptor.forClass(Problem.class);
        verify(problemMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getId()).isEqualTo(12L);
        assertThat(updateCaptor.getValue().getPid()).isEqualTo("p12");

        ArgumentCaptor<JudgeProblemEditRequest> judgeCaptor = ArgumentCaptor.forClass(JudgeProblemEditRequest.class);
        verify(judgeClient).updateProblem(judgeCaptor.capture());
        assertThat(judgeCaptor.getValue().getPid()).isEqualTo("p12");
    }
}
