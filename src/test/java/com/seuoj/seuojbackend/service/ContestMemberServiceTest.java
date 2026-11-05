package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.seuoj.seuojbackend.common.*;
import com.seuoj.seuojbackend.dto.contest.ContestMembersDTO;
import com.seuoj.seuojbackend.entity.*;
import com.seuoj.seuojbackend.exception.*;
import com.seuoj.seuojbackend.interceptor.*;
import com.seuoj.seuojbackend.mapper.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContestMemberServiceTest {
    private final ContestMapper contests = mock(ContestMapper.class);
    private final ContestRegisterRelMapper registrations = mock(ContestRegisterRelMapper.class);
    private final UserInfoMapper users = mock(UserInfoMapper.class);
    private final ResourcePermissionMapper permissions = mock(ResourcePermissionMapper.class);
    private final PermissionService access = mock(PermissionService.class);
    private final UserRoleMapper roles = mock(UserRoleMapper.class);
    private final AccountCreationService accounts = mock(AccountCreationService.class);
    private final ContestMemberService service = new ContestMemberService(contests, registrations, users, permissions, access, roles, mock(UserRoleRelMapper.class), mock(UserRoleService.class), mock(org.springframework.security.crypto.password.PasswordEncoder.class), accounts);

    @BeforeEach void setup() {
        UserContextHolder.set(UserContext.of(10L, AuthStatus.AUTHENTICATED));
        when(contests.selectById(13L)).thenReturn(new Contest().setId(13L).setCreatedByUserId(1L));
    }
    @AfterEach void cleanup() { UserContextHolder.clear(); }
    private ContestMembersDTO.Participant entry(String username) {
        return new ContestMembersDTO.Participant(username, " 张三 ", true);
    }

    @Test void readOnlyUserCannotListOrWriteMembers() {
        doThrow(new ForbiddenException("无权操作")).when(access).assertPermission(10L, ResourceType.CONTEST, 13L, PermissionOp.WRITE);
        assertThrows(ForbiddenException.class, () -> service.list(13L));
        assertThrows(ForbiddenException.class, () -> service.addAdministrator(13L, "a"));
        assertThrows(ForbiddenException.class, () -> service.upsertParticipants(13L, new ContestMembersDTO.Batch(List.of(entry("a")))));
        assertThrows(ForbiddenException.class, () -> service.removeParticipant(13L, 20L));
        verifyNoInteractions(users, registrations, permissions);
    }

    @Test void missingSecondAccountDoesNotImportFirstAccount() {
        when(users.selectOne(any(Wrapper.class))).thenReturn(new UserInfo().setId(20L).setUsername("a"), null);
        var error = assertThrows(BadRequestException.class, () -> service.upsertParticipants(13L,
                new ContestMembersDTO.Batch(List.of(entry("a"), entry("missing")))));
        assertTrue(error.getMessage().contains("第 2 行"));
        verify(registrations, never()).insert(any(ContestRegisterRel.class));
        verify(registrations, never()).updateById(any(ContestRegisterRel.class));
    }

    @Test void duplicateResolvedUserIdRejectedBeforeWrites() {
        when(users.selectOne(any(Wrapper.class))).thenReturn(new UserInfo().setId(20L).setUsername("a"));
        assertThrows(BadRequestException.class, () -> service.upsertParticipants(13L,
                new ContestMembersDTO.Batch(List.of(entry("a"), entry("A")))));
        verify(registrations, never()).insert(any(ContestRegisterRel.class));
        verify(registrations, never()).updateById(any(ContestRegisterRel.class));
    }

    @Test void upsertUpdatesContestNameAndStarWithoutChangingGlobalAccount() {
        var user = new UserInfo().setId(20L).setUsername("a").setNickname("OJ昵称");
        when(users.selectOne(any(Wrapper.class))).thenReturn(user);
        when(registrations.selectOne(any(Wrapper.class))).thenReturn(new ContestRegisterRel().setId(30L).setUserId(20L).setContestId(13L));
        service.upsertParticipants(13L, new ContestMembersDTO.Batch(List.of(entry("a"))));
        var capture = ArgumentCaptor.forClass(ContestRegisterRel.class);
        verify(registrations).updateById(capture.capture());
        assertEquals("张三", capture.getValue().getDisplayName());
        assertTrue(capture.getValue().getIsStarred());
        assertTrue(capture.getValue().getIsManaged());
        verify(users, never()).updateById(any(UserInfo.class));
        assertEquals("OJ昵称", user.getNickname());
        verifyNoInteractions(permissions);
    }

    @Test void generatedAccountDoesNotPublishRosterNameAsNickname() {
        var member = new ContestRegisterRel().setId(30L).setContestId(13L).setDisplayName("验题者真实姓名");
        when(registrations.selectList(any(Wrapper.class))).thenReturn(List.of(member));
        when(roles.selectOne(any(Wrapper.class))).thenReturn(new UserRole().setId(2L).setRoleCode("STUDENT"));
        doAnswer(invocation -> {
            ((UserInfo) invocation.getArgument(0)).setId(20L);
            return null;
        }).when(accounts).insert(any(UserInfo.class));

        var credentials = service.generateAccounts(13L, new ContestMembersDTO.Generate(List.of(30L), "FRtest", 1, 2));

        var capture = ArgumentCaptor.forClass(UserInfo.class);
        verify(accounts).insert(capture.capture());
        assertEquals("FRtest01", capture.getValue().getUsername());
        assertNull(capture.getValue().getNickname());
        assertEquals("验题者真实姓名", member.getDisplayName());
        assertEquals(20L, member.getUserId());
        assertEquals("验题者真实姓名", credentials.getFirst().displayName());
    }

    @Test void cannotRemoveCreatorOrSelf() {
        assertThrows(BadRequestException.class, () -> service.removeAdministrator(13L, 1L));
        assertThrows(BadRequestException.class, () -> service.removeAdministrator(13L, 10L));
        verify(access, never()).revokePermission(any(), any(), any(), any(), any());
    }

    @Test void administratorGrantIsScopedToContestOnly() {
        when(users.selectOne(any(Wrapper.class))).thenReturn(new UserInfo().setId(20L).setUsername("a"));
        service.addAdministrator(13L, "a");
        verify(access).grantPermission(10L, ResourceType.CONTEST, 13L, 20L, PermissionOp.WRITE);
    }
}
