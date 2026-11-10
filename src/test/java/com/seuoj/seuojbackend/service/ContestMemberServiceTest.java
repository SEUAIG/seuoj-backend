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
    private final ContestMemberService service = new ContestMemberService(contests, registrations, users, permissions, access, mock(UserRoleMapper.class), mock(UserRoleRelMapper.class), mock(UserRoleService.class), mock(org.springframework.security.crypto.password.PasswordEncoder.class), mock(AccountCreationService.class));

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
        when(users.selectOne(any(Wrapper.class))).thenReturn(new UserInfo().setId(20L).setUsername("a"));
        when(registrations.selectOne(any(Wrapper.class))).thenReturn(new ContestRegisterRel().setId(30L).setUserId(20L).setContestId(13L));
        service.upsertParticipants(13L, new ContestMembersDTO.Batch(List.of(entry("a"))));
        var capture = ArgumentCaptor.forClass(ContestRegisterRel.class);
        verify(registrations).updateById(capture.capture());
        assertEquals("张三", capture.getValue().getDisplayName());
        assertTrue(capture.getValue().getIsStarred());
        assertTrue(capture.getValue().getIsManaged());
        verify(users, never()).updateById(any(UserInfo.class));
        verifyNoInteractions(permissions);
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
