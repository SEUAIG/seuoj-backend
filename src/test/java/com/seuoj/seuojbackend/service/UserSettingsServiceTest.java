package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.common.Capability;
import com.seuoj.seuojbackend.entity.*;
import com.seuoj.seuojbackend.exception.*;
import com.seuoj.seuojbackend.mapper.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserSettingsServiceTest {
    final UserInfoMapper users=mock(UserInfoMapper.class);
    final UserRoleMapper definitions=mock(UserRoleMapper.class);
    final UserRoleRelMapper links=mock(UserRoleRelMapper.class);
    final UserCapabilityMapper grants=mock(UserCapabilityMapper.class);
    final UserRoleService roles=mock(UserRoleService.class);
    final PasswordEncoder passwords=mock(PasswordEncoder.class);
    final VerificationCodeService codes=mock(VerificationCodeService.class);
    final UserSettingsService settings=new UserSettingsService(users,definitions,links,grants,roles,passwords,codes);
    UserInfo target;
    @BeforeEach void setup() {
        var configuration=new com.baomidou.mybatisplus.core.MybatisConfiguration();
        var assistant=new org.apache.ibatis.builder.MapperBuilderAssistant(configuration,"settings-test");
        assistant.setCurrentNamespace("settings-test");
        for (Class<?> type:List.of(UserInfo.class,UserRoleRel.class,UserRole.class))
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant,type);
        target=new UserInfo().setId(2L).setUsername("student").setNickname("Real Name").setEmail("old@example.invalid").setAllowNicknameChange(false).setPassword("hash");
        when(users.selectById(2L)).thenReturn(target);
        when(roles.getHighestRoleLabel(2L)).thenReturn("student");
        when(roles.hasCapability(1L,Capability.USER_MANAGE)).thenReturn(true);
        when(roles.isAdmin(1L)).thenReturn(true);
        when(definitions.selectOne(any())).thenReturn(new UserRole().setId(4L).setRoleCode("TEACHER"));
    }
    @AfterEach void cleanup() {
        if(TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }
    @Test void selfCannotUnlockNickname() {
        assertThrows(ForbiddenException.class,()->settings.updateProfile(2L,2L,new UserSettingsService.ProfileUpdate("New",null,true)));
        verify(users,never()).update(any(),any());
    }
    @Test void lockedSelfCannotChangeNickname() {
        assertThrows(ForbiddenException.class,()->settings.updateProfile(2L,2L,new UserSettingsService.ProfileUpdate("New",null,null)));
        verify(users,never()).update(any(),any());
    }
    @Test void legacyProfileEndpointAlsoRespectsNicknameLock() {
        AuthService auth=new AuthService(users,definitions,links,roles,passwords,null,codes,null,null);
        var dto=new com.seuoj.seuojbackend.dto.user.UpdateProfileDTO();dto.setNickname("Changed");
        assertThrows(ForbiddenException.class,()->auth.updateProfile(2L,dto));
        verify(users,never()).update(any(),any());
    }
    @Test void lockedSelfMaySubmitUnchangedNickname() {
        assertDoesNotThrow(()->settings.updateProfile(2L,2L,new UserSettingsService.ProfileUpdate("Real Name",null,null)));
    }
    @Test void managerCanCorrectLockedNickname() {
        assertDoesNotThrow(()->settings.updateProfile(1L,2L,new UserSettingsService.ProfileUpdate("Correct Name",null,false)));
        verify(users,times(2)).update(isNull(),any());
    }
    @Test void nonManagerCannotReadPrivateSettings() {
        assertThrows(ForbiddenException.class,()->settings.get(3L,2L));
    }
    @Test void userManagerCannotTakeOverPrivilegedAccount() {
        when(roles.hasCapability(3L,Capability.USER_MANAGE)).thenReturn(true);
        when(roles.getCapabilities(2L)).thenReturn(Set.of(Capability.CONTEST_CREATE));
        assertThrows(ForbiddenException.class,()->settings.updateProfile(3L,2L,new UserSettingsService.ProfileUpdate(null,"new@example.invalid",null)));
        verify(users,never()).update(any(),any());
        assertFalse(settings.canManageProfile(3L,2L));
    }
    @Test void editAvailabilityMatchesAccountBoundaries() {
        assertTrue(settings.canManageProfile(2L,2L));
        assertTrue(settings.canManageProfile(1L,2L));
        assertFalse(settings.canManageProfile(3L,2L));
        when(roles.isAdmin(2L)).thenReturn(true);
        assertFalse(settings.canManageProfile(1L,2L));
        when(roles.isSuperAdmin(1L)).thenReturn(true);
        assertTrue(settings.canManageProfile(1L,2L));
        when(roles.isSuperAdmin(2L)).thenReturn(true);
        assertFalse(settings.canManageProfile(1L,2L));
    }
    @Test void emailValidationMatchesDatabaseColumnLength() {
        try (var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            String email="a".repeat(60)+"@"+"b".repeat(60)+".invalid";
            assertTrue(validator.validate(new UserSettingsService.ProfileUpdate(null,email,null)).stream()
                    .anyMatch(v->v.getPropertyPath().toString().equals("email")));
            assertTrue(validator.validate(new UserSettingsService.EmailUpdate(email,"password","id","code")).stream()
                    .anyMatch(v->v.getPropertyPath().toString().equals("email")));
        }
    }
    @Test void administratorCannotModifyAnotherAdministrator() {
        when(roles.isAdmin(2L)).thenReturn(true);
        assertThrows(ForbiddenException.class,()->settings.updatePermissions(1L,2L,Set.of()));
    }
    @Test void superAdministratorCanGrantAdministrator() {
        when(roles.isSuperAdmin(1L)).thenReturn(true);
        assertDoesNotThrow(()->settings.updatePermissions(1L,2L,Set.of(Capability.ACCOUNT_ADMIN)));
        verify(links).insert(any(UserRoleRel.class));
        verify(grants,never()).grant(anyLong(),eq("ACCOUNT_ADMIN"));
    }
    @Test void ordinaryAdministratorCannotGrantAdministrator() {
        assertThrows(ForbiddenException.class,()->settings.updatePermissions(1L,2L,Set.of(Capability.ACCOUNT_ADMIN)));
        verifyNoInteractions(grants,links);
    }
    @Test void userManagerCannotGrantCapabilities() {
        when(roles.hasCapability(3L,Capability.USER_MANAGE)).thenReturn(true);
        assertThrows(ForbiddenException.class,()->settings.updatePermissions(3L,2L,Set.of(Capability.PROBLEM_CREATE)));
    }
    @Test void nobodyCanEditOwnPermissions() {
        assertThrows(ForbiddenException.class,()->settings.updatePermissions(2L,2L,Set.of(Capability.ACCOUNT_ADMIN)));
    }
    @Test void superAdministratorCannotDemoteAnotherSuperAdministrator() {
        when(roles.isSuperAdmin(1L)).thenReturn(true);
        when(roles.isSuperAdmin(2L)).thenReturn(true);
        assertThrows(ForbiddenException.class,()->settings.updatePermissions(1L,2L,Set.of()));
    }
    @Test void savingPermissionsReplacesDirectGrants() {
        settings.updatePermissions(1L,2L,Set.of(Capability.TEACHER,Capability.USER_MANAGE));
        verify(grants).deleteByUserId(2L);
        verify(grants).grant(2L,"USER_MANAGE");
        verify(grants,never()).grant(2L,"TEACHER");
        verify(links).insert(any(UserRoleRel.class));
    }
    @Test void selfCannotChangeEmailWithoutVerification() {
        assertThrows(ForbiddenException.class,()->settings.updateProfile(2L,2L,new UserSettingsService.ProfileUpdate(null,"new@example.invalid",null)));
    }
    @Test void emailChangeNeedsCurrentPassword() {
        assertThrows(ForbiddenException.class,()->settings.updateEmail(2L,new UserSettingsService.EmailUpdate("new@example.invalid","wrong","id","code")));
        verifyNoInteractions(codes);
    }
    @Test void emailCodeMustMatchNewMailboxAndIsReleasedOnRollback() {
        when(passwords.matches("current","hash")).thenReturn(true);
        when(codes.preVerifyCode("id","code")).thenReturn("different@example.invalid");
        TransactionSynchronizationManager.initSynchronization();
        assertThrows(BadRequestException.class,()->settings.updateEmail(2L,new UserSettingsService.EmailUpdate("new@example.invalid","current","id","code")));
        verify(users,never()).update(any(),any());
        TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(codes).releaseReservation("id");
        verify(codes,never()).consumeVerification(anyString());
    }
    @Test void invalidOrExpiredEmailCodeCannotChangeMailbox() {
        when(passwords.matches("current","hash")).thenReturn(true);
        when(codes.preVerifyCode("id","code")).thenReturn(null);
        assertThrows(BadRequestException.class,()->settings.updateEmail(2L,new UserSettingsService.EmailUpdate("new@example.invalid","current","id","code")));
        verify(users,never()).update(any(),any());
        verify(codes,never()).consumeVerification(anyString());
    }
    @Test void successfulEmailChangeConsumesCodeOnlyAfterCommit() {
        when(passwords.matches("current","hash")).thenReturn(true);
        when(codes.preVerifyCode("id","code")).thenReturn("new@example.invalid");
        TransactionSynchronizationManager.initSynchronization();
        settings.updateEmail(2L,new UserSettingsService.EmailUpdate("new@example.invalid","current","id","code"));
        verify(codes,never()).consumeVerification(anyString());
        verify(users).lockAccountCreation();
        verify(users).update(isNull(),any());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(codes).consumeVerification("id");
    }
    @Test void duplicateMailboxIsRejected() {
        when(users.selectList(any())).thenReturn(List.of(new UserInfo().setId(9L)));
        assertThrows(ConflictException.class,()->settings.updateProfile(1L,2L,new UserSettingsService.ProfileUpdate(null,"taken@example.invalid",null)));
        verify(users,never()).update(any(),any());
    }
    @Test void managerResetsPasswordWithRandomValue() {
        when(passwords.encode(anyString())).thenReturn("new-hash");
        var reset=settings.resetPassword(1L,2L);
        assertTrue(reset.password().matches("[A-HJ-NP-Za-km-z2-9]{12}"));
        verify(passwords).encode(reset.password());
        verify(users).update(isNull(),any());
        assertNotEquals(reset.password(),settings.resetPassword(1L,2L).password());
    }
    @Test void passwordResetCannotTargetSelf() {
        assertThrows(ForbiddenException.class,()->settings.resetPassword(2L,2L));
        verify(users,never()).update(any(),any());
    }
    @Test void passwordResetNeedsUserManagement() {
        assertThrows(ForbiddenException.class,()->settings.resetPassword(3L,2L));
        verify(users,never()).update(any(),any());
        verifyNoInteractions(passwords);
    }
    @Test void passwordResetRespectsAdministratorBoundary() {
        when(roles.isAdmin(2L)).thenReturn(true);
        assertThrows(ForbiddenException.class,()->settings.resetPassword(1L,2L));
        when(roles.isSuperAdmin(1L)).thenReturn(true);
        when(passwords.encode(anyString())).thenReturn("new-hash");
        assertDoesNotThrow(()->settings.resetPassword(1L,2L));
        when(roles.isSuperAdmin(2L)).thenReturn(true);
        assertThrows(ForbiddenException.class,()->settings.resetPassword(1L,2L));
    }
    @Test void passwordResetOfMissingUserIsNotFound() {
        assertThrows(NotFoundException.class,()->settings.resetPassword(1L,99L));
    }
}
