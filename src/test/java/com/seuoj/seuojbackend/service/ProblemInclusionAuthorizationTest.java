package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.common.*;
import com.seuoj.seuojbackend.dto.assignment.*;
import com.seuoj.seuojbackend.dto.contest.ContestProblemEditDTO;
import com.seuoj.seuojbackend.dto.problemset.ProblemSetProblemEditDTO;
import com.seuoj.seuojbackend.entity.*;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.interceptor.*;
import com.seuoj.seuojbackend.mapper.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProblemInclusionAuthorizationTest {
    @Mock ProblemMapper problems;
    @Mock ProblemSetMapper sets;
    @Mock ProblemSetProblemRelMapper setLinks;
    @Mock ContestMapper contests;
    @Mock ContestProblemRelMapper contestLinks;
    @Mock AssignmentMapper assignments;
    @Mock AssignmentProblemRelMapper assignmentLinks;
    @Mock ClassInfoMapper classes;
    @Mock ClassContestRelMapper classContests;
    @Mock PermissionService access;
    @Mock UserRoleService roles;
    @InjectMocks ProblemSetService setService;
    @InjectMocks ContestService contestService;
    @InjectMocks AssignmentService assignmentService;
    @InjectMocks ClassService classService;
    AutoCloseable mocks;
    Problem problem;
    PermissionService policy;
    ResourcePermissionMapper grants;

    @BeforeEach void setup() {
        mocks=MockitoAnnotations.openMocks(this);
        UserContextHolder.set(UserContext.of(10L,AuthStatus.AUTHENTICATED));
        var config=new com.baomidou.mybatisplus.core.MybatisConfiguration();
        var assistant=new org.apache.ibatis.builder.MapperBuilderAssistant(config,"inclusion");
        assistant.setCurrentNamespace("inclusion");
        for(var type:List.of(Problem.class,ProblemSet.class,ProblemSetProblemRel.class,Contest.class,ContestProblemRel.class,Assignment.class,AssignmentProblemRel.class))
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant,type);
        grants=mock(ResourcePermissionMapper.class);
        policy=new PermissionService(grants,roles,problems,null,null,null,null,null,null,null,null);
        problem=new Problem().setId(20L).setPid("PRIVATE").setIsPublic(false);
        when(problems.selectById(20L)).thenReturn(problem);
        when(problems.selectList(any())).thenReturn(List.of(problem));
        when(sets.selectById(30L)).thenReturn(new ProblemSet().setId(30L));
        when(contests.selectById(40L)).thenReturn(new Contest().setId(40L));
        when(classes.selectById(50L)).thenReturn(new ClassInfo().setId(50L));
        when(assignments.selectById(60L)).thenReturn(new Assignment().setId(60L).setClassId(50L));
        doAnswer(call->{policy.assertCanReferenceProblem(call.getArgument(0),call.getArgument(1));return null;})
                .when(access).assertCanReferenceProblem(anyLong(),any());
    }
    @AfterEach void cleanup() throws Exception {UserContextHolder.clear();mocks.close();}
    ProblemSetProblemEditDTO setDto() {
        var item=new ProblemSetProblemEditDTO.ProblemItemDTO();item.setPid("PRIVATE");item.setOrderId(1);
        var dto=new ProblemSetProblemEditDTO();dto.setProblemList(List.of(item));return dto;
    }
    @Test void publicProblemCanBeIncluded() {
        problem.setIsPublic(true);assertDoesNotThrow(()->policy.assertCanReferenceProblem(10L,problem));
    }
    @Test void readGrantCannotRedistributePrivateProblem() {
        when(grants.hasAnyPermission("PROBLEM",20L,10L)).thenReturn(true);
        assertThrows(ForbiddenException.class,()->policy.assertCanReferenceProblem(10L,problem));
    }
    @Test void teacherReadDefaultDoesNotAuthorizeRedistribution() {
        when(roles.getRoleCodesByUserId(10L)).thenReturn(List.of("TEACHER"));
        assertThrows(ForbiddenException.class,()->policy.assertCanReferenceProblem(10L,problem));
    }
    @Test void problemWriterCanIncludePrivateProblem() {
        when(grants.hasPermission("PROBLEM",20L,10L,"WRITE")).thenReturn(true);
        assertDoesNotThrow(()->policy.assertCanReferenceProblem(10L,problem));
    }
    @Test void globalProblemManagerCanIncludePrivateProblem() {
        when(roles.hasCapability(10L,Capability.PROBLEM_MANAGE)).thenReturn(true);
        assertDoesNotThrow(()->policy.assertCanReferenceProblem(10L,problem));
    }
    @Test void ownProblemSetCannotImportUnknownPrivateProblem() {
        assertThrows(ForbiddenException.class,()->setService.replaceProblemSetProblems(30L,setDto()));
        verify(setLinks,never()).insertBatch(anyList());
        verify(setLinks,never()).markDeletedByIds(anyLong(),anyList());
    }
    @Test void deletedProblemSetLinkMustBeReauthorized() {
        when(setLinks.selectByProblemSetId(30L)).thenReturn(List.of(new ProblemSetProblemRel().setId(70L).setProblemId(20L).setIsDel(1)));
        assertThrows(ForbiddenException.class,()->setService.replaceProblemSetProblems(30L,setDto()));
        verify(setLinks,never()).restoreByIdsWithSortOrders(anyLong(),anyList());
    }
    @Test void collaboratorCanKeepAlreadyIncludedPrivateProblem() {
        when(setLinks.selectByProblemSetId(30L)).thenReturn(List.of(new ProblemSetProblemRel().setId(70L).setProblemId(20L).setSortOrder(1).setIsDel(0)));
        assertDoesNotThrow(()->setService.replaceProblemSetProblems(30L,setDto()));
        verify(access,never()).assertCanReferenceProblem(anyLong(),any());
    }
    @Test void contestWriteDoesNotAuthorizeImportingPrivateProblem() {
        var item=new ContestProblemEditDTO.ProblemItemDTO();item.setPid("PRIVATE");item.setSortOrder(0);
        var dto=new ContestProblemEditDTO();dto.setProblemList(List.of(item));
        assertThrows(ForbiddenException.class,()->contestService.replaceContestProblems(40L,dto));
        verify(contestLinks,never()).insertBatch(anyList());
    }
    @Test void classWriteDoesNotAuthorizeAssignmentPrivateProblem() {
        var item=new AssignmentProblemEditDTO.ProblemItem();item.setProblemId(20L);
        var dto=new AssignmentProblemEditDTO();dto.setProblems(List.of(item));
        assertThrows(ForbiddenException.class,()->assignmentService.replaceAssignmentProblems(50L,60L,dto));
        verify(assignmentLinks,never()).update(any(),any());
        verify(assignmentLinks,never()).insert(any(AssignmentProblemRel.class));
    }
    @Test void importingProblemSetChecksItsReadPermission() {
        var dto=new AssignmentImportFromProblemSetDTO();dto.setProblemSetId(30L);
        doThrow(new ForbiddenException("denied")).when(access).assertPermission(10L,ResourceType.PROBLEM_SET,30L,PermissionOp.READ);
        assertThrows(ForbiddenException.class,()->assignmentService.importFromProblemSet(50L,60L,dto));
        verifyNoInteractions(setLinks,assignmentLinks);
    }
    @Test void classWriterCannotAttachAnotherPrivateContest() {
        doThrow(new ForbiddenException("denied")).when(access).assertPermission(10L,ResourceType.CONTEST,40L,PermissionOp.WRITE);
        assertThrows(ForbiddenException.class,()->classService.linkContest(50L,40L));
        verifyNoInteractions(classContests);
    }
    @Test void legacyClassProblemSetImportChecksSourceAccess() {
        doThrow(new ForbiddenException("denied")).when(access).assertPermission(10L,ResourceType.PROBLEM_SET,30L,PermissionOp.READ);
        assertThrows(ForbiddenException.class,()->classService.linkProblemSet(50L,30L));
        verifyNoInteractions(setLinks,assignmentLinks);
    }
}
