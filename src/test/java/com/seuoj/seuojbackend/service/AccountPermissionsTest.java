package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.common.*;
import com.seuoj.seuojbackend.entity.*;
import com.seuoj.seuojbackend.mapper.*;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountPermissionsTest {
    final UserRoleRelMapper links=mock(UserRoleRelMapper.class);
    final UserCapabilityMapper caps=mock(UserCapabilityMapper.class);
    final UserRoleService roles=new UserRoleService(links,caps);
    final ResourcePermissionMapper grants=mock(ResourcePermissionMapper.class);
    final ProblemMapper problems=mock(ProblemMapper.class);
    final ContestMapper contests=mock(ContestMapper.class);
    final PermissionService access=new PermissionService(grants,roles,problems,null,null,null,contests,null,null,null,null);

    @BeforeEach void setup() {
        when(links.getRoleCodesByUserId(10L)).thenReturn(List.of("STUDENT"));
        when(caps.findByUserId(10L)).thenReturn(List.of());
        when(problems.selectById(20L)).thenReturn(new Problem().setId(20L).setIsPublic(false));
        when(contests.selectById(30L)).thenReturn(new Contest().setId(30L).setIsPublic(false));
    }
    @Test void legacyTeacherAutomaticallyGetsCreatePermissionsOnly() {
        when(links.getRoleCodesByUserId(10L)).thenReturn(List.of("TEACHER"));
        assertEquals(Set.of(Capability.TEACHER,Capability.PROBLEM_CREATE,Capability.CONTEST_CREATE),roles.getCapabilities(10L));
        assertTrue(access.canCreate(10L,ResourceType.PROBLEM));
        assertTrue(access.canCreate(10L,ResourceType.CONTEST));
        assertFalse(access.hasPermission(10L,ResourceType.CONTEST,30L,PermissionOp.WRITE));
    }
    @ParameterizedTest @ValueSource(strings={"ADMIN","SUPER_ADMIN"})
    void legacyAdministratorsRetainAllCapabilities(String role) {
        when(links.getRoleCodesByUserId(10L)).thenReturn(List.of(role));
        assertEquals(EnumSet.allOf(Capability.class),roles.getCapabilities(10L));
    }
    @Test void independentCreationDoesNotGrantOtherResources() {
        when(caps.findByUserId(10L)).thenReturn(List.of("PROBLEM_CREATE","CONTEST_CREATE"));
        assertTrue(access.canCreate(10L,ResourceType.PROBLEM));
        assertTrue(access.canCreate(10L,ResourceType.CONTEST));
        assertFalse(access.hasPermission(10L,ResourceType.PROBLEM,20L,PermissionOp.WRITE));
        assertFalse(access.hasPermission(10L,ResourceType.CONTEST,30L,PermissionOp.WRITE));
    }
    @Test void problemManagerDoesNotBecomeContestManagerOrCreator() {
        when(caps.findByUserId(10L)).thenReturn(List.of("PROBLEM_MANAGE"));
        assertTrue(access.hasPermission(10L,ResourceType.PROBLEM,20L,PermissionOp.READ));
        assertTrue(access.hasPermission(10L,ResourceType.PROBLEM,20L,PermissionOp.WRITE));
        assertFalse(access.canCreate(10L,ResourceType.PROBLEM));
        assertFalse(access.hasPermission(10L,ResourceType.CONTEST,30L,PermissionOp.WRITE));
    }
    @Test void contestManagerDoesNotBecomeProblemManager() {
        when(caps.findByUserId(10L)).thenReturn(List.of("CONTEST_MANAGE"));
        assertTrue(access.hasPermission(10L,ResourceType.CONTEST,30L,PermissionOp.READ));
        assertTrue(access.hasPermission(10L,ResourceType.CONTEST,30L,PermissionOp.WRITE));
        assertFalse(access.hasPermission(10L,ResourceType.PROBLEM,20L,PermissionOp.WRITE));
    }
    @Test void creatorGrantStillWorksAfterGlobalCreationRevoked() {
        when(grants.hasPermission("CONTEST",30L,10L,"WRITE")).thenReturn(true);
        assertFalse(access.canCreate(10L,ResourceType.CONTEST));
        assertTrue(access.hasPermission(10L,ResourceType.CONTEST,30L,PermissionOp.WRITE));
    }
    @Test void grantRevocationIsObservedWithoutRelogin() {
        when(caps.findByUserId(10L)).thenReturn(List.of("CONTEST_MANAGE"),List.of());
        assertTrue(access.hasPermission(10L,ResourceType.CONTEST,30L,PermissionOp.WRITE));
        assertFalse(access.hasPermission(10L,ResourceType.CONTEST,30L,PermissionOp.WRITE));
    }
    @Test void directCapabilityRowsCannotCreateAnAdministrator() {
        when(caps.findByUserId(10L)).thenReturn(List.of("ACCOUNT_ADMIN","TEACHER"));
        assertTrue(roles.getCapabilities(10L).isEmpty());
        assertFalse(roles.isAdmin(10L));
    }
    @Test void anonymousCannotCreateOrManage() {
        assertFalse(access.canCreate(null,ResourceType.PROBLEM));
        assertFalse(access.hasPermission(null,ResourceType.PROBLEM,20L,PermissionOp.WRITE));
    }
    @Test void globalContestManagerCanReadOthersFullFeedback() {
        when(caps.findByUserId(10L)).thenReturn(List.of("CONTEST_MANAGE"));
        Contest contest=new Contest().setId(30L).setRuleType("ACM");
        Submission submission=new Submission().setUserId(11L).setProblemId(20L).setStatus("Finished");
        ContestSubmissionPolicy policy=new ContestSubmissionPolicy(access,roles);
        assertDoesNotThrow(() -> policy.assertCanView(10L,contest,submission));
        assertTrue(policy.project(10L,contest,submission,null).testDataVisible());
    }
    @Test void problemManagerCannotBypassContestSubmissionAccess() {
        when(caps.findByUserId(10L)).thenReturn(List.of("PROBLEM_MANAGE"));
        when(contests.selectById(30L)).thenReturn(new Contest().setId(30L).setIsPublic(true));
        ContestSubmissionPolicy policy=new ContestSubmissionPolicy(access,roles);
        assertThrows(ForbiddenException.class,()->policy.assertCanView(10L,new Contest().setId(30L),new Submission().setUserId(11L)));
    }
}
