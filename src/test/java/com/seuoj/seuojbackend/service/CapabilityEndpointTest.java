package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.aspect.*;
import com.seuoj.seuojbackend.common.*;
import com.seuoj.seuojbackend.controller.api.ProblemController;
import com.seuoj.seuojbackend.controller.api.CommonController;
import com.seuoj.seuojbackend.exception.*;
import com.seuoj.seuojbackend.interceptor.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CapabilityEndpointTest {
    final UserRoleService roles=mock(UserRoleService.class);
    final ProblemService problems=mock(ProblemService.class);
    final ProblemTestcaseService data=mock(ProblemTestcaseService.class);
    final ProblemImportService imports=mock(ProblemImportService.class);
    final AuthService accounts=mock(AuthService.class);
    <T> T proxy(T target) {
        var factory=new AspectJProxyFactory(target);
        factory.addAspect(new AuthAspect(roles));
        factory.addAspect(new CapabilityAspect(roles));
        return factory.getProxy();
    }
    @BeforeEach void setup() {
        UserContextHolder.set(UserContext.of(10L,AuthStatus.AUTHENTICATED));
        when(roles.getRoleCodesByUserId(10L)).thenReturn(List.of("STUDENT"));
    }
    @AfterEach void cleanup() { UserContextHolder.clear(); }
    @Test void studentWithCreationCapabilityCanCreateAndImport() {
        when(roles.hasCapability(10L,Capability.PROBLEM_CREATE)).thenReturn(true);
        ProblemController controller=proxy(new ProblemController(problems,data,imports));
        assertDoesNotThrow(()->controller.createProblem(null));
        assertDoesNotThrow(()->controller.importProblemPack(null,"P1",false,null));
        verify(problems).createProblem(null);
        verify(imports).importPack(null,null,"P1",false);
    }
    @Test void studentWithoutCreationCannotCreateOrImport() {
        ProblemController controller=proxy(new ProblemController(problems,data,imports));
        assertThrows(ForbiddenException.class,()->controller.createProblem(null));
        assertThrows(ForbiddenException.class,()->controller.importProblemPack(null,"P1",false,null));
        verifyNoInteractions(problems,imports);
    }
    @Test void userManagementCapabilityAllowsImportWithoutAdminRole() {
        when(roles.hasCapability(10L,Capability.USER_MANAGE)).thenReturn(true);
        CommonController controller=proxy(new CommonController(mock(CommonService.class),accounts));
        controller.batchImportUsers(null);
        verify(accounts).batchImportUsers(null);
    }
    @Test void teacherWithoutUserManagementCannotImportAccounts() {
        when(roles.getRoleCodesByUserId(10L)).thenReturn(List.of("TEACHER"));
        CommonController controller=proxy(new CommonController(mock(CommonService.class),accounts));
        assertThrows(ForbiddenException.class,()->controller.batchImportUsers(null));
        verifyNoInteractions(accounts);
    }
    @Test void anonymousCannotUseCapabilityEndpoints() {
        UserContextHolder.set(UserContext.guest());
        ProblemController controller=proxy(new ProblemController(problems,data,imports));
        assertThrows(AuthorizationException.class,()->controller.createProblem(null));
    }
    @Test void testcaseAccessRequiresSpecificProblemEditPermission() {
        doThrow(new ForbiddenException("Denied")).when(problems).assertCanManageProblem("P1");
        var judge=mock(com.seuoj.seuojbackend.client.JudgeClient.class);
        ProblemTestcaseService service=new ProblemTestcaseService(problems,judge,null);
        assertThrows(ForbiddenException.class,()->service.getProblemTree("P1"));
        assertThrows(ForbiddenException.class,()->service.getProblemTestcaseMeta("P1"));
        verifyNoInteractions(judge);
    }
}
