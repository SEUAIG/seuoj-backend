package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.common.*;
import com.seuoj.seuojbackend.entity.Contest;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.mapper.*;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContestMemberAccessTest {
    private final ResourcePermissionMapper grants = mock(ResourcePermissionMapper.class);
    private final UserRoleService roles = mock(UserRoleService.class);
    private final ContestMapper contests = mock(ContestMapper.class);
    private final ContestProblemRelMapper problems = mock(ContestProblemRelMapper.class);
    private final ContestRegisterRelMapper registrations = mock(ContestRegisterRelMapper.class);
    private final PermissionService access = new PermissionService(grants, roles, null, null, null,
            null, contests, problems, registrations, null, null);

    private void setup(boolean registered, boolean manager, boolean started) {
        when(contests.selectById(13L)).thenReturn(new Contest().setId(13L).setIsPublic(false)
                .setStartTime(LocalDateTime.now().plusHours(started ? -1 : 1))
                .setEndTime(LocalDateTime.now().plusHours(2)));
        when(roles.getRoleCodesByUserId(10L)).thenReturn(List.of("STUDENT"));
        when(grants.hasPermission("CONTEST", 13L, 10L, "WRITE")).thenReturn(manager);
        when(grants.hasAnyPermission("CONTEST", 13L, 10L)).thenReturn(manager);
        when(registrations.selectCount(any())).thenReturn(registered ? 1L : 0L);
        when(problems.selectCount(any())).thenReturn(1L);
    }

    @Test void memberCanReadPrivateContestBeforeStartButNotProblem() {
        setup(true, false, false);
        assertTrue(access.hasPermission(10L, ResourceType.CONTEST, 13L, PermissionOp.READ));
        assertFalse(access.hasPermission(10L, ResourceType.CONTEST, 13L, PermissionOp.WRITE));
        assertThrows(ForbiddenException.class, () -> access.assertProblemAccessViaContest(10L, 99L, 13L));
    }
    @Test void ordinaryStudentWithContestWriteCanPreviewProblem() {
        setup(false, true, false);
        assertDoesNotThrow(() -> access.assertProblemAccessViaContest(10L, 99L, 13L));
    }
    @Test void memberCanReadProblemAfterStart() {
        setup(true, false, true);
        assertDoesNotThrow(() -> access.assertProblemAccessViaContest(10L, 99L, 13L));
    }
    @Test void removingMemberRevokesInheritedRead() {
        setup(false, false, true);
        assertFalse(access.hasPermission(10L, ResourceType.CONTEST, 13L, PermissionOp.READ));
        assertThrows(ForbiddenException.class, () -> access.assertProblemAccessViaContest(10L, 99L, 13L));
    }
}
