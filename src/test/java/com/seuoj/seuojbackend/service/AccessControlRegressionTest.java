package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.seuoj.seuojbackend.common.AuthStatus;
import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.dto.image.ImageBindDTO;
import com.seuoj.seuojbackend.entity.Assignment;
import com.seuoj.seuojbackend.entity.AssignmentProblemRel;
import com.seuoj.seuojbackend.entity.ClassInfo;
import com.seuoj.seuojbackend.entity.ImageAsset;
import com.seuoj.seuojbackend.entity.ImageBinding;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.interceptor.UserContext;
import com.seuoj.seuojbackend.interceptor.UserContextHolder;
import com.seuoj.seuojbackend.mapper.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 越权修复回归：图片绑定、加入私有班级、作业提交列表。
 */
class AccessControlRegressionTest {

    @Mock ImageAssetMapper imageAssetMapper;
    @Mock ImageBindingMapper imageBindingMapper;
    @Mock PermissionService permissionService;
    @Mock ClassInfoMapper classInfoMapper;
    @Mock ClassStudentRelMapper classStudentRelMapper;
    @Mock AssignmentMapper assignmentMapper;
    @Mock AssignmentProblemRelMapper assignmentProblemRelMapper;
    @Mock AssignmentIntroAttachmentMapper assignmentIntroAttachmentMapper;
    @Mock ProblemMapper problemMapper;
    @Mock ProblemSetMapper problemSetMapper;
    @Mock ProblemSetProblemRelMapper problemSetProblemRelMapper;
    @Mock SubmissionMapper submissionMapper;
    @Mock FileAccessService fileAccess;
    @Mock ClassIntroAttachmentMapper classIntroAttachmentMapper;
    @Mock ClassContestRelMapper classContestRelMapper;
    @Mock ContestMapper contestMapper;
    @Mock UserInfoMapper userInfoMapper;
    @Mock UserRoleMapper userRoleMapper;
    @Mock UserRoleRelMapper userRoleRelMapper;

    @InjectMocks ImageService imageService;
    @InjectMocks ClassService classService;
    @InjectMocks AssignmentService assignmentService;

    AutoCloseable mocks;

    @BeforeAll
    static void initTableInfo() {
        var config = new MybatisConfiguration();
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(config, "access-regression");
        assistant.setCurrentNamespace("access-regression");
        TableInfoHelper.initTableInfo(assistant, ImageBinding.class);
        TableInfoHelper.initTableInfo(assistant, ImageAsset.class);
        TableInfoHelper.initTableInfo(assistant, AssignmentProblemRel.class);
        TableInfoHelper.initTableInfo(assistant, ClassInfo.class);
        TableInfoHelper.initTableInfo(assistant, Assignment.class);
    }

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        UserContextHolder.set(UserContext.of(10L, AuthStatus.AUTHENTICATED));
        lenient().when(classInfoMapper.selectById(20L)).thenReturn(new ClassInfo().setId(20L));
        // assertPermission 的默认语义：无权限即抛 Forbidden（与真实实现一致）
        lenient().doThrow(new ForbiddenException("无权操作")).when(permissionService)
                .assertPermission(any(), any(), any(), any());
    }

    @AfterEach
    void cleanup() throws Exception {
        UserContextHolder.clear();
        mocks.close();
    }

    // ───────── 图片绑定：改绑/解绑必须有目标资源 WRITE 权限 ─────────

    @Test
    void imageBindingWithoutWriteOnProblemIsForbidden() {
        when(imageBindingMapper.selectList(any())).thenReturn(java.util.List.of());
        assertThrows(ForbiddenException.class, () -> imageService.replaceBindings(
                ResourceType.PROBLEM, 1L, new ImageBindDTO()));
        assertThrows(ForbiddenException.class, () -> imageService.unbindResource(ResourceType.PROBLEM, 1L));
    }

    @Test
    void imageBindingWithWriteOnProblemSucceeds() {
        Mockito.doNothing().when(permissionService)
                .assertPermission(10L, ResourceType.PROBLEM, 1L, PermissionOp.WRITE);
        when(permissionService.hasPermission(10L, ResourceType.PROBLEM, 1L, PermissionOp.WRITE)).thenReturn(true);
        when(imageBindingMapper.selectList(any())).thenReturn(java.util.List.of());
        ImageBindDTO dto = new ImageBindDTO();
        dto.setImageKeys(java.util.List.of());
        assertDoesNotThrow(() -> imageService.replaceBindings(ResourceType.PROBLEM, 1L, dto));
        assertDoesNotThrow(() -> imageService.unbindResource(ResourceType.PROBLEM, 1L));
    }

    // ───────── joinClass：私有班级不能凭 id 自行加入 ─────────

    @Test
    void privateClassCannotBeJoinedByPlainTextId() {
        when(classInfoMapper.selectById(21L)).thenReturn(new ClassInfo().setId(21L).setIsPublic(false));
        assertThrows(NotFoundException.class, () -> classService.joinClass(21L));
    }

    @Test
    void publicClassCanBeJoined() {
        when(classInfoMapper.selectById(22L)).thenReturn(new ClassInfo().setId(22L).setIsPublic(true));
        when(classStudentRelMapper.restoreDeletedStudent(22L, 10L)).thenReturn(0);
        assertDoesNotThrow(() -> classService.joinClass(22L));
    }

    @Test
    void classManagerCanJoinOwnPrivateClass() {
        when(classInfoMapper.selectById(21L)).thenReturn(new ClassInfo().setId(21L).setIsPublic(false));
        when(permissionService.hasPermission(10L, ResourceType.CLASS, 21L, PermissionOp.WRITE)).thenReturn(true);
        when(classStudentRelMapper.restoreDeletedStudent(21L, 10L)).thenReturn(0);
        assertDoesNotThrow(() -> classService.joinClass(21L));
    }

    // 作业提交列表：线上版本改为“无班级 WRITE 只能查到自己的提交”，见 AssignmentSubmissionScopeTest。
}
