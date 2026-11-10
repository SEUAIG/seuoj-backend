package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.client.dto.JudgeConcurrencyStatus;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.exception.JudgeRemoteException;
import com.seuoj.seuojbackend.mapper.SystemSettingMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JudgeConcurrencyServiceTest {
    final SystemSettingMapper settings = mock(SystemSettingMapper.class);
    final JudgeClient judge = mock(JudgeClient.class);
    final RejudgeDispatcher dispatcher = mock(RejudgeDispatcher.class);
    final UserRoleService roles = mock(UserRoleService.class);
    final JudgeConcurrencyService service = new JudgeConcurrencyService(settings, judge, dispatcher, roles);

    static final long SUPER = 1L, ADMIN = 2L, STUDENT = 3L;

    {
        when(roles.isSuperAdmin(SUPER)).thenReturn(true);
        when(roles.isAdmin(SUPER)).thenReturn(true);
        when(roles.isAdmin(ADMIN)).thenReturn(true);
    }

    private static JudgeConcurrencyStatus judgeAt(int limit) {
        JudgeConcurrencyStatus s = new JudgeConcurrencyStatus();
        s.setLimit(limit);
        s.setDefaultLimit(2);
        s.setRunning(0);
        s.setWaiting(0);
        return s;
    }

    @Test void adminsCanViewStudentsCannot() {
        when(judge.fetchConcurrency()).thenReturn(judgeAt(2));
        var status = service.status(ADMIN);
        assertFalse(status.canEdit());
        assertEquals(2, status.judge().getLimit());
        assertThrows(ForbiddenException.class, () -> service.status(STUDENT));
    }

    @Test void onlySuperAdminCanChange() {
        assertThrows(ForbiddenException.class, () -> service.update(ADMIN, 4));
        verify(settings, never()).put(any(), any(), any());
        verify(judge, never()).updateConcurrency(anyInt());
    }

    @Test void outOfRangeIsRejected() {
        assertThrows(BadRequestException.class, () -> service.update(SUPER, 0));
        assertThrows(BadRequestException.class, () -> service.update(SUPER, JudgeConcurrencyService.MAX_LIMIT + 1));
        assertThrows(BadRequestException.class, () -> service.update(SUPER, null));
        verify(settings, never()).put(any(), any(), any());
    }

    @Test void changeIsSavedPushedAndFollowedByRejudge() {
        when(judge.updateConcurrency(4)).thenReturn(judgeAt(4));
        when(judge.fetchConcurrency()).thenReturn(judgeAt(4));
        when(settings.find(JudgeConcurrencyService.KEY)).thenReturn("4");

        var status = service.update(SUPER, 4);

        verify(settings).put(JudgeConcurrencyService.KEY, "4", SUPER);
        verify(dispatcher).updateMaxInFlight(4);
        assertEquals(4, status.configured());
        assertNull(status.judgeError());
        assertTrue(status.canEdit());
    }

    @Test void unreachableJudgeStillKeepsTheSetting() {
        when(judge.updateConcurrency(4)).thenThrow(new JudgeRemoteException("无法连接判题端"));
        when(judge.fetchConcurrency()).thenThrow(new JudgeRemoteException("无法连接判题端"));

        var status = service.update(SUPER, 4);

        verify(settings).put(JudgeConcurrencyService.KEY, "4", SUPER);
        verify(dispatcher, never()).updateMaxInFlight(anyInt());
        assertNull(status.judge());
        assertTrue(status.judgeError().contains("设置已保存"));
    }

    @Test void syncRepushesAfterJudgeRestart() {
        when(settings.find(JudgeConcurrencyService.KEY)).thenReturn("6");
        when(judge.fetchConcurrency()).thenReturn(judgeAt(2)); // 判题端重启后回到 .env 默认
        when(judge.updateConcurrency(6)).thenReturn(judgeAt(6));

        service.sync();

        verify(judge).updateConcurrency(6);
        verify(dispatcher).updateMaxInFlight(6);
    }

    @Test void syncWithoutSettingOnlyFollowsJudge() {
        when(settings.find(JudgeConcurrencyService.KEY)).thenReturn(null);
        when(judge.fetchConcurrency()).thenReturn(judgeAt(3));

        service.sync();

        verify(judge, never()).updateConcurrency(anyInt());
        verify(dispatcher).updateMaxInFlight(3);
    }

    @Test void syncSurvivesOldJudgeWithoutTheEndpoint() {
        when(judge.fetchConcurrency()).thenThrow(new JudgeRemoteException("判题端版本过旧，不支持在线调整并发"));
        assertDoesNotThrow(service::sync);
        assertDoesNotThrow(service::sync);
        verify(dispatcher, never()).updateMaxInFlight(anyInt());
    }

    @Test void garbageSettingIsIgnored() {
        when(settings.find(JudgeConcurrencyService.KEY)).thenReturn("lots");
        assertNull(service.configured());
        when(settings.find(JudgeConcurrencyService.KEY)).thenReturn("99");
        assertNull(service.configured());
    }
}
