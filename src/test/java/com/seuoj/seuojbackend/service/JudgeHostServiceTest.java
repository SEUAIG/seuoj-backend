package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.client.JudgeClient.HostSetting;
import com.seuoj.seuojbackend.client.dto.JudgeHostsStatus;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.exception.JudgeRemoteException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.mapper.JudgeHostMapper;
import com.seuoj.seuojbackend.mapper.SystemSettingMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JudgeHostServiceTest {
    final SystemSettingMapper settings = mock(SystemSettingMapper.class);
    final JudgeHostMapper judgings = mock(JudgeHostMapper.class);
    final JudgeClient judge = mock(JudgeClient.class);
    final RejudgeDispatcher dispatcher = mock(RejudgeDispatcher.class);
    final RejudgeService rejudge = mock(RejudgeService.class);
    final UserRoleService roles = mock(UserRoleService.class);
    final JudgeHostService service = new JudgeHostService(settings, judgings, judge, dispatcher, rejudge, roles);

    static final long SUPER = 1L, ADMIN = 2L, STUDENT = 3L;

    {
        when(roles.isSuperAdmin(SUPER)).thenReturn(true);
        when(roles.isAdmin(SUPER)).thenReturn(true);
        when(roles.isAdmin(ADMIN)).thenReturn(true);
        when(judge.updateHosts(anyList())).thenAnswer(inv -> judgeWith(inv.getArgument(0)));
    }

    private static JudgeHostsStatus judgeWith(List<HostSetting> hosts) {
        JudgeHostsStatus s = new JudgeHostsStatus();
        List<JudgeHostsStatus.Host> list = new ArrayList<>();
        for (HostSetting h : hosts) {
            JudgeHostsStatus.Host host = new JudgeHostsStatus.Host();
            host.setId(h.id());
            host.setName("judge-" + h.id());
            host.setEnabled(h.enabled());
            host.setRemoving(false);
            list.add(host);
        }
        s.setHosts(list);
        s.setWaiting(0);
        return s;
    }

    private static List<HostSetting> on(int... ids) {
        List<HostSetting> out = new ArrayList<>();
        for (int id : ids) out.add(new HostSetting(id, true));
        return out;
    }

    @Test void adminsCanViewStudentsCannot() {
        when(judge.fetchHosts()).thenReturn(judgeWith(on(0, 1)));
        var status = service.status(ADMIN);
        assertFalse(status.canEdit());
        assertEquals(2, status.judge().getHosts().size());
        assertThrows(ForbiddenException.class, () -> service.status(STUDENT));
        assertThrows(ForbiddenException.class, () -> service.judgings(STUDENT, 0));
    }

    @Test void onlySuperAdminCanChange() {
        assertThrows(ForbiddenException.class, () -> service.add(ADMIN));
        assertThrows(ForbiddenException.class, () -> service.setEnabled(ADMIN, 0, false));
        assertThrows(ForbiddenException.class, () -> service.rejudge(ADMIN, 0, null));
        verify(settings, never()).put(any(), any(), any());
        verify(judge, never()).updateHosts(anyList());
    }

    @Test void firstChangeStartsFromWhatTheJudgeRuns() {
        when(judge.fetchHosts()).thenReturn(judgeWith(on(0, 1)));
        service.add(SUPER);
        verify(settings).put(JudgeHostService.KEY, "0:1,1:1,2:1", SUPER);
        verify(judge).updateHosts(on(0, 1, 2));
        verify(dispatcher).updateMaxInFlight(3);
    }

    @Test void addUsesTheNextUnusedIdEvenAfterDeletes() {
        when(settings.find(JudgeHostService.KEY)).thenReturn("0:1,4:0");
        service.add(SUPER);
        verify(settings).put(JudgeHostService.KEY, "0:1,4:0,5:1", SUPER);
    }

    @Test void pauseAndResumeOneOrAll() {
        when(settings.find(JudgeHostService.KEY)).thenReturn("0:1,1:1");
        service.setEnabled(SUPER, 1, false);
        verify(settings).put(JudgeHostService.KEY, "0:1,1:0", SUPER);
        verify(dispatcher).updateMaxInFlight(1);
        service.setAllEnabled(SUPER, false);
        verify(settings).put(JudgeHostService.KEY, "0:0,1:0", SUPER);
        assertThrows(NotFoundException.class, () -> service.setEnabled(SUPER, 7, true));
    }

    @Test void removeKeepsAtLeastOneHost() {
        when(settings.find(JudgeHostService.KEY)).thenReturn("3:1");
        assertThrows(BadRequestException.class, () -> service.remove(SUPER, 3));
        when(settings.find(JudgeHostService.KEY)).thenReturn("3:1,4:1");
        service.remove(SUPER, 3);
        verify(settings).put(JudgeHostService.KEY, "4:1", SUPER);
    }

    @Test void addStopsAtTheMaximum() {
        StringBuilder raw = new StringBuilder();
        for (int i = 0; i < JudgeHostService.MAX_HOSTS; i++) raw.append(i == 0 ? "" : ",").append(i).append(":1");
        when(settings.find(JudgeHostService.KEY)).thenReturn(raw.toString());
        assertThrows(BadRequestException.class, () -> service.add(SUPER));
    }

    @Test void judgeDownStillSavesTheSetting() {
        when(settings.find(JudgeHostService.KEY)).thenReturn("0:1");
        reset(judge);
        when(judge.updateHosts(anyList())).thenThrow(new JudgeRemoteException("无法连接判题端"));
        when(judge.fetchHosts()).thenThrow(new JudgeRemoteException("无法连接判题端"));
        var status = service.add(SUPER);
        verify(settings).put(JudgeHostService.KEY, "0:1,1:1", SUPER);
        assertNull(status.judge());
        assertTrue(status.judgeError().contains("自动同步"));
    }

    @Test void cannotChangeWithoutSettingOrJudge() {
        reset(judge);
        when(judge.fetchHosts()).thenThrow(new JudgeRemoteException("无法连接判题端"));
        assertThrows(BadRequestException.class, () -> service.add(SUPER));
        verify(settings, never()).put(any(), any(), any());
    }

    @Test void syncPushesSavedListAfterJudgeRestart() {
        when(settings.find(JudgeHostService.KEY)).thenReturn("0:1,1:0,2:1");
        when(judge.fetchHosts()).thenReturn(judgeWith(on(0, 1)));
        service.sync();
        verify(judge).updateHosts(List.of(new HostSetting(0, true), new HostSetting(1, false), new HostSetting(2, true)));
        verify(dispatcher).updateMaxInFlight(2);
    }

    @Test void syncLeavesJudgeAloneWithoutSettingOrWhenEqual() {
        when(judge.fetchHosts()).thenReturn(judgeWith(on(0, 1)));
        service.sync();
        when(settings.find(JudgeHostService.KEY)).thenReturn("1:1,0:1");
        service.sync();
        verify(judge, never()).updateHosts(anyList());
        verify(dispatcher, times(2)).updateMaxInFlight(2);
    }

    @Test void brokenSettingIsIgnored() {
        for (String raw : new String[]{"x", "0:1,0:1", "-1:1", "0"}) {
            when(settings.find(JudgeHostService.KEY)).thenReturn(raw);
            assertNull(service.configured(), raw);
        }
    }

    @Test void rejudgeByHostPassesTheTimeWindow() {
        when(rejudge.rejudgeJudgeHost(eq(SUPER), eq("judge-2"), any())).thenReturn(new RejudgeService.Outcome(3, 0, 3));
        assertEquals(3, service.rejudge(SUPER, 2, 24).queued());
        verify(rejudge).rejudgeJudgeHost(eq(SUPER), eq("judge-2"), notNull());
        service.rejudge(SUPER, 2, null);
        verify(rejudge).rejudgeJudgeHost(eq(SUPER), eq("judge-2"), isNull());
        assertThrows(BadRequestException.class, () -> service.rejudge(SUPER, 2, 0));
    }
}
