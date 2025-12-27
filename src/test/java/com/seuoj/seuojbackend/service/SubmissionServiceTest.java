package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.client.dto.JudgeSubmissionRequest;
import com.seuoj.seuojbackend.common.AuthStatus;
import com.seuoj.seuojbackend.common.SubmissionStatus;
import com.seuoj.seuojbackend.dto.submission.SubmitDTO;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.CodeStorageException;
import com.seuoj.seuojbackend.exception.JudgeRemoteException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.UserContext;
import com.seuoj.seuojbackend.interceptor.UserContextHolder;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.SubmissionMapper;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.storage.CodeStorage;
import com.seuoj.seuojbackend.vo.submission.SubmitVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubmissionServiceTest {

    @Mock
    private SubmissionMapper submissionMapper;
    @Mock
    private ProblemMapper problemMapper;
    @Mock
    private JudgeClient judgeClient;
    @Mock
    private CodeStorage codeStorage;
    @Mock
    private UserInfoMapper userInfoMapper;
    @Mock
    private TransactionTemplate transactionTemplate;

    private SubmissionService submissionService;

    @BeforeEach
    void setUp() {
        submissionService = new SubmissionService(
                submissionMapper, problemMapper, judgeClient, codeStorage, userInfoMapper, transactionTemplate);
        // 初始化 MyBatis-Plus 元数据以便 LambdaUpdateWrapper 在单元测试中正常工作
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "mapper");
        assistant.setCurrentNamespace("dummy");
        TableInfoHelper.initTableInfo(assistant, Submission.class);
        // Make transactionTemplate.execute run the callback immediately without a real transaction manager
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void submit_shouldFailWhenUserNotLoggedIn() {
        UserContextHolder.clear();
        SubmitDTO dto = baseDto("p01");
        assertThrows(NotFoundException.class, () -> submissionService.submit(dto));
    }

    @Test
    void submit_shouldFailWhenProblemNotFound() {
        mockUser();
        SubmitDTO dto = baseDto("p01");
        when(problemMapper.selectOne(any())).thenReturn(null);
        assertThrows(NotFoundException.class, () -> submissionService.submit(dto));
    }

    @Test
    void submit_shouldFailWhenCodeBlank() {
        mockUser();
        when(problemMapper.selectOne(any())).thenReturn(problem(1L, "p01"));
        SubmitDTO dto = baseDto("p01");
        dto.setCode("   ");
        assertThrows(BadRequestException.class, () -> submissionService.submit(dto));
    }

    @Test
    void submit_shouldFailWhenCodeTooLong() {
        mockUser();
        when(problemMapper.selectOne(any())).thenReturn(problem(1L, "p01"));
        SubmitDTO dto = baseDto("p01");
        dto.setCode("a".repeat(65536)); // 65536 bytes in UTF-8 exceeds the 65535-byte limit
        assertThrows(BadRequestException.class, () -> submissionService.submit(dto));
    }

    @Test
    void submit_shouldPropagateCodeStorageException() {
        mockUser();
        when(problemMapper.selectOne(any())).thenReturn(problem(1L, "p01"));
        SubmitDTO dto = baseDto("p01");
        doThrow(new CodeStorageException("io fail")).when(codeStorage).save(any(), any());
        assertThrows(CodeStorageException.class, () -> submissionService.submit(dto));
    }

    @Test
    void submit_shouldMarkFailedWhenJudgeRemoteThrows() {
        mockUser();
        when(problemMapper.selectOne(any())).thenReturn(problem(1L, "p01"));
        SubmitDTO dto = baseDto("p01");
        doThrow(new JudgeRemoteException("remote error")).when(judgeClient).submit(any(JudgeSubmissionRequest.class));

        SubmitVO result = submissionService.submit(dto);

        assertThat(result.getSubmissionNo()).isNotBlank();
        // judge failure should trigger a status update to FAILED
        verify(submissionMapper).update(any(), any());
    }

    @Test
    void getResult_shouldFailWhenUserNotLoggedIn() {
        UserContextHolder.clear();
        assertThrows(NotFoundException.class, () -> submissionService.getResult("nope"));
    }

    @Test
    void getResult_shouldFailWhenNotOwner() {
        mockUser();
        when(submissionMapper.selectOne(any())).thenReturn(submission("s1", 2L, 10L));
        when(problemMapper.selectById(any())).thenReturn(problem(10L, "p01"));
        assertThrows(BadRequestException.class, () -> submissionService.getResult("s1"));
    }

    @Test
    void getResult_shouldPropagateCodeStorageExceptionWhenFileMissing() {
        mockUser();
        when(submissionMapper.selectOne(any())).thenReturn(submission("s1", 1L, 10L));
        when(problemMapper.selectById(any())).thenReturn(problem(10L, "p01"));
        doThrow(new CodeStorageException("file missing")).when(codeStorage).getCode("s1");
        assertThrows(CodeStorageException.class, () -> submissionService.getResult("s1"));
    }

    private void mockUser() {
        UserContextHolder.set(UserContext.of(1L, AuthStatus.AUTHENTICATED));
    }

    private SubmitDTO baseDto(String pid) {
        SubmitDTO dto = new SubmitDTO();
        dto.setPid(pid);
        dto.setLanguage("java");
        dto.setCode("print(1)");
        return dto;
    }

    private Problem problem(long id, String pid) {
        Problem p = new Problem();
        p.setId(id);
        p.setPid(pid);
        return p;
    }

    private Submission submission(String submissionNo, Long userId, Long problemId) {
        Submission s = new Submission();
        s.setSubmissionNo(submissionNo);
        s.setUserId(userId);
        s.setProblemId(problemId);
        s.setStatus(SubmissionStatus.PENDING.getStatus());
        return s;
    }
}
