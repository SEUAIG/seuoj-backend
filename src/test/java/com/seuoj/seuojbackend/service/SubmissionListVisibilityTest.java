package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.common.AuthStatus;
import com.seuoj.seuojbackend.exception.AuthorizationException;
import com.seuoj.seuojbackend.interceptor.UserContext;
import com.seuoj.seuojbackend.interceptor.UserContextHolder;
import com.seuoj.seuojbackend.mapper.SubmissionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubmissionListVisibilityTest {
    private final SubmissionMapper submissions = mock(SubmissionMapper.class);
    private final UserRoleService roles = mock(UserRoleService.class);
    private final SubmissionService service = new SubmissionService(submissions, null, null, null,
            null, null, null, roles, null, null, null, null, null, null);

    @AfterEach void cleanup() { UserContextHolder.clear(); }

    private void login(boolean teacher) {
        UserContextHolder.set(UserContext.of(10L, AuthStatus.AUTHENTICATED));
        when(roles.isTeacherOrAdmin(10L)).thenReturn(teacher);
        when(submissions.selectSubmissionPage(any(), nullable(Long.class), any(), any(), any(), any(), any()))
                .thenReturn(new Page<>(1, 20));
    }

    @Test void anonymousCannotEnumerateStudentsSubmissions() {
        UserContextHolder.set(UserContext.guest());
        assertThrows(AuthorizationException.class,
                () -> service.listSubmissions(1, 20, 99L, null, null, null, null, null));
        verifyNoInteractions(submissions);
    }

    @Test void studentCannotOverrideOwnerWithUserIdAndUsername() {
        login(false);
        service.listSubmissions(1, 20, 99L, null, 9L, "FN26101", null, "other-student");
        verify(submissions).selectSubmissionPage(any(), eq(10L), isNull(), eq(9L),
                eq("FN26101"), isNull(), eq("other-student"));
    }

    @Test void teacherCanFilterAnotherStudentsSubmissions() {
        login(true);
        service.listSubmissions(1, 20, 99L, null, 9L, null, null, null);
        verify(submissions).selectSubmissionPage(any(), eq(99L), isNull(), eq(9L), isNull(), isNull(), isNull());
    }

    @Test void teacherCanSeeAllStudents() {
        login(true);
        service.listSubmissions(1, 20, null, null, null, null, null, null);
        verify(submissions).selectSubmissionPage(any(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull());
    }
}
