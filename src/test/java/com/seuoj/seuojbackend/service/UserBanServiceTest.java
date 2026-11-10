package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.common.Capability;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.exception.*;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.util.UserBans;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserBanServiceTest {
    final UserInfoMapper users = mock(UserInfoMapper.class);
    final UserSettingsService settings = mock(UserSettingsService.class);
    final UserRoleService roles = mock(UserRoleService.class);
    final UserBanService bans = new UserBanService(users, settings, roles);
    final UserInfo alice = new UserInfo().setId(2L).setUsername("alice");
    final UserInfo bob = new UserInfo().setId(3L).setUsername("Bob");

    @BeforeEach void setup() {
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, "ban-test");
        assistant.setCurrentNamespace("ban-test");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, UserInfo.class);
        when(roles.hasCapability(1L, Capability.USER_MANAGE)).thenReturn(true);
    }

    private static UserBanService.BanRequest ban(List<Long> ids, List<String> names) {
        return new UserBanService.BanRequest(ids, names, "抄袭", null);
    }

    @Test void bansSelectedAndNamedAccountsTogether() {
        when(users.selectList(any())).thenReturn(List.of(alice)).thenReturn(List.of(bob));
        var outcome = bans.ban(1L, ban(List.of(2L), List.of("bob")));
        assertEquals(2, outcome.count());
        verify(users).update(isNull(), any());
    }

    @Test void nonManagerIsRejectedBeforeLookingUpAccounts() {
        assertThrows(ForbiddenException.class, () -> bans.ban(5L, ban(List.of(2L), null)));
        verify(users, never()).selectList(any());
        verify(users, never()).update(any(), any());
    }

    @Test void unknownUsernameRejectsWholeBatch() {
        when(users.selectList(any())).thenReturn(List.of(alice));
        var e = assertThrows(BadRequestException.class, () -> bans.ban(1L, ban(null, List.of("alice", "ghost"))));
        assertTrue(e.getMessage().contains("ghost"));
        verify(users, never()).update(any(), any());
    }

    @Test void duplicateUsernameMustBePickedFromList() {
        UserInfo contestAlice = new UserInfo().setId(9L).setUsername("alice");
        when(users.selectList(any())).thenReturn(List.of(alice, contestAlice));
        assertThrows(BadRequestException.class, () -> bans.ban(1L, ban(null, List.of("alice"))));
        verify(users, never()).update(any(), any());
    }

    @Test void oneUnmanageableAccountRejectsWholeBatch() {
        when(users.selectList(any())).thenReturn(List.of(alice, bob));
        doThrow(new ForbiddenException("x")).when(settings).assertManageProfile(1L, 3L);
        var e = assertThrows(ForbiddenException.class, () -> bans.ban(1L, ban(List.of(2L, 3L), null)));
        assertTrue(e.getMessage().contains("Bob"));
        verify(users, never()).update(any(), any());
    }

    @Test void cannotBanSelf() {
        when(users.selectList(any())).thenReturn(List.of(new UserInfo().setId(1L).setUsername("me")));
        assertThrows(ForbiddenException.class, () -> bans.ban(1L, ban(List.of(1L), null)));
        verify(users, never()).update(any(), any());
    }

    @Test void untilMustBeInFuture() {
        var dto = new UserBanService.BanRequest(List.of(2L), null, "抄袭", LocalDateTime.now().minusMinutes(1));
        assertThrows(BadRequestException.class, () -> bans.ban(1L, dto));
        verify(users, never()).update(any(), any());
    }

    @Test void emptyAndOversizedBatchesAreRejected() {
        assertThrows(BadRequestException.class, () -> bans.ban(1L, ban(List.of(), List.of(" "))));
        List<Long> many = new ArrayList<>();
        for (long i = 0; i <= UserBanService.MAX_BATCH; i++) many.add(i + 10);
        assertThrows(BadRequestException.class, () -> bans.unban(1L, new UserBanService.UnbanRequest(many, null)));
    }

    @Test void unbanClearsFields() {
        when(users.selectList(any())).thenReturn(List.of(alice));
        assertEquals(1, bans.unban(1L, new UserBanService.UnbanRequest(List.of(2L), null)).count());
        verify(users).update(isNull(), any());
    }

    @Test void banExpiresByTime() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 5, 12, 0);
        UserInfo user = new UserInfo().setBannedAt(now.minusDays(1));
        assertTrue(UserBans.isBanned(user, now), "permanent");
        user.setBannedUntil(now.plusMinutes(1));
        assertTrue(UserBans.isBanned(user, now));
        user.setBannedUntil(now);
        assertFalse(UserBans.isBanned(user, now));
        assertFalse(UserBans.isBanned(new UserInfo(), now));
    }

    @Test void loginShowsBanReason() {
        var passwords = mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        when(passwords.matches(any(), any())).thenReturn(true);
        AuthService auth = new AuthService(users, null, null, roles, passwords, null, null, null, null);
        when(users.selectOne(any())).thenReturn(new UserInfo().setId(2L).setUsername("alice").setPassword("hash")
                .setIsEnabled(true).setBannedAt(LocalDateTime.now()).setBanReason("比赛作弊"));
        var dto = new com.seuoj.seuojbackend.dto.auth.LoginDTO();
        dto.setIdentifier("alice");
        dto.setPassword("password");
        var e = assertThrows(AuthorizationException.class, () -> auth.login(dto));
        assertTrue(e.getMessage().contains("比赛作弊"));
        assertTrue(e.getMessage().contains("永久"));
    }
}
