package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.common.*;
import com.seuoj.seuojbackend.entity.*;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.interceptor.*;
import com.seuoj.seuojbackend.mapper.*;
import java.util.List;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssignmentSubmissionScopeTest {
    private final AssignmentMapper assignments=mock(AssignmentMapper.class);
    private final ClassInfoMapper classes=mock(ClassInfoMapper.class);
    private final AssignmentProblemRelMapper relations=mock(AssignmentProblemRelMapper.class);
    private final SubmissionMapper submissions=mock(SubmissionMapper.class);
    private final PermissionService permissions=mock(PermissionService.class);
    private final AssignmentService service=new AssignmentService(assignments,classes,relations,
        mock(AssignmentIntroAttachmentMapper.class),mock(ClassStudentRelMapper.class),mock(ProblemMapper.class),
        mock(ProblemSetMapper.class),mock(ProblemSetProblemRelMapper.class),submissions,permissions);
    @BeforeEach void setup(){
        UserContextHolder.set(UserContext.of(10L,AuthStatus.AUTHENTICATED));
        var c=new ClassInfo();c.setId(1L);when(classes.selectById(1L)).thenReturn(c);
        var a=new Assignment();a.setId(2L);a.setClassId(1L);a.setStatus("PUBLISHED");when(assignments.selectById(2L)).thenReturn(a);
        var r=new AssignmentProblemRel();r.setProblemId(4L);when(relations.selectList(any())).thenReturn(List.of(r));
        when(submissions.selectAssignmentSubmissionPage(any(),anyList(),anyLong(),nullable(Long.class),nullable(Long.class),nullable(String.class),nullable(String.class),nullable(String.class),nullable(String.class))).thenReturn(new Page<>(1,20));
    }
    @AfterEach void cleanup(){UserContextHolder.clear();}
    @Test void managerLearningScopeIncludesUnlinkedHistoryAndPreservesFilters(){
        when(permissions.hasPermission(10L,ResourceType.CLASS,1L,PermissionOp.WRITE)).thenReturn(true);
        service.getAssignmentSubmissionPage(1L,2L,1,20,"learning","P0001","Accepted","Cpp","student");
        verify(submissions).selectAssignmentSubmissionPage(any(),eq(List.of(4L)),eq(1L),isNull(),isNull(),eq("P0001"),eq("Accepted"),eq("Cpp"),eq("student"));
    }
    @Test void linkedScopeRequiresExplicitAssignmentAssociation(){
        when(permissions.hasPermission(10L,ResourceType.CLASS,1L,PermissionOp.WRITE)).thenReturn(true);
        service.getAssignmentSubmissionPage(1L,2L,1,20,"linked",null,null,null,null);
        verify(submissions).selectAssignmentSubmissionPage(any(),eq(List.of(4L)),eq(1L),eq(2L),isNull(),isNull(),isNull(),isNull(),isNull());
    }
    @Test void studentCannotBrowseAnotherStudentsHistoryByUsername(){
        service.getAssignmentSubmissionPage(1L,2L,1,20,"learning",null,null,null,"other-student");
        verify(submissions).selectAssignmentSubmissionPage(any(),eq(List.of(4L)),eq(1L),isNull(),eq(10L),isNull(),isNull(),isNull(),isNull());
    }
    @Test void invalidScopeAndPageAreRejected(){
        assertThrows(BadRequestException.class,()->service.getAssignmentSubmissionPage(1L,2L,1,20,"all-users",null,null,null,null));
        assertThrows(BadRequestException.class,()->service.getAssignmentSubmissionPage(1L,2L,0,20,"learning",null,null,null,null));
        verifyNoInteractions(submissions);
    }
}
