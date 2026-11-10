package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.common.*;
import com.seuoj.seuojbackend.entity.*;
import com.seuoj.seuojbackend.exception.*;
import com.seuoj.seuojbackend.mapper.*;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.*;
import org.mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FileAccessServiceTest {
    @Mock ClassIntroAttachmentMapper classFiles;
    @Mock AssignmentIntroAttachmentMapper assignmentFiles;
    @Mock AnnouncementAttachmentMapper announcementFiles;
    @Mock ClassInfoMapper classes;
    @Mock AssignmentMapper assignments;
    @Mock AnnouncementMapper announcements;
    @Mock PermissionService permissions;
    @Mock UserRoleService roles;
    @InjectMocks FileAccessService access;
    AutoCloseable mocks;
    String path="2026/11/00000000-0000-0000-0000-000000000000.txt";
    @BeforeEach void setup() {
        mocks=MockitoAnnotations.openMocks(this);
        var config=new com.baomidou.mybatisplus.core.MybatisConfiguration();
        var assistant=new org.apache.ibatis.builder.MapperBuilderAssistant(config,"file-access");
        assistant.setCurrentNamespace("file-access");
        for(var type:List.of(ClassIntroAttachment.class,AssignmentIntroAttachment.class,AnnouncementAttachment.class))
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant,type);
        when(classes.selectById(20L)).thenReturn(new ClassInfo().setId(20L));
    }
    @AfterEach void close() throws Exception {mocks.close();}
    @Test void privateClassFileIsNotReadableByUnrelatedAccount() {
        when(classFiles.selectList(any())).thenReturn(List.of(new ClassIntroAttachment().setClassId(20L)));
        assertThrows(ForbiddenException.class,()->access.assertDownload(10L,path));
    }
    @Test void classReaderCanDownloadButCannotRepublishAttachment() {
        when(classFiles.selectList(any())).thenReturn(List.of(new ClassIntroAttachment().setClassId(20L)));
        when(permissions.hasPermission(10L,ResourceType.CLASS,20L,PermissionOp.READ)).thenReturn(true);
        assertDoesNotThrow(()->access.assertDownload(10L,path));
        assertThrows(ForbiddenException.class,()->access.assertAttach(10L,path));
    }
    @Test void classWriterCanAttachExistingFile() {
        when(classFiles.selectList(any())).thenReturn(List.of(new ClassIntroAttachment().setClassId(20L)));
        when(permissions.hasPermission(10L,ResourceType.CLASS,20L,PermissionOp.WRITE)).thenReturn(true);
        assertDoesNotThrow(()->access.assertAttach(10L,path));
    }
    @Test void freshUploadOnlyAccessibleToOwner() {
        assertDoesNotThrow(()->access.assertDownload(10L,"u10/"+path));
        assertDoesNotThrow(()->access.assertAttach(10L,"u10/"+path));
        assertThrows(ForbiddenException.class,()->access.assertDownload(11L,"u10/"+path));
    }
    @Test void pathAliasCannotForgeOwnershipOrSkipAttachmentLookup() {
        for(String p:List.of("u10/../"+path,"u10/../../"+path,"u10//"+path,"/"+path,"u10\\"+path))
            assertThrows(BadRequestException.class,()->access.assertDownload(10L,p));
    }
    @Test void removedAndUnboundLegacyFilesAreNotPublic() {
        assertThrows(ForbiddenException.class,()->access.assertDownload(10L,path));
    }
    @Test void deletedClassCannotAuthorizeItsAttachment() {
        when(classFiles.selectList(any())).thenReturn(List.of(new ClassIntroAttachment().setClassId(21L)));
        when(permissions.hasPermission(10L,ResourceType.CLASS,21L,PermissionOp.READ)).thenReturn(true);
        assertThrows(ForbiddenException.class,()->access.assertDownload(10L,path));
    }
    @Test void assignmentAttachmentsRespectDraftAndVisibilityWindow() {
        when(assignmentFiles.selectList(any())).thenReturn(List.of(new AssignmentIntroAttachment().setAssignmentId(30L)));
        var assignment=new Assignment().setId(30L).setClassId(20L).setStatus("DRAFT");
        when(assignments.selectById(30L)).thenReturn(assignment);
        when(permissions.hasPermission(10L,ResourceType.CLASS,20L,PermissionOp.READ)).thenReturn(true);
        assertThrows(ForbiddenException.class,()->access.assertDownload(10L,path));
        assignment.setStatus("PUBLISHED").setVisibleFrom(LocalDateTime.now().plusHours(1));
        assertThrows(ForbiddenException.class,()->access.assertDownload(10L,path));
        assignment.setVisibleFrom(null).setVisibleTo(LocalDateTime.now().minusHours(1));
        assertThrows(ForbiddenException.class,()->access.assertDownload(10L,path));
        assignment.setVisibleTo(null);
        assertDoesNotThrow(()->access.assertDownload(10L,path));
        assertThrows(ForbiddenException.class,()->access.assertAttach(10L,path));
    }
    @Test void assignmentOwnerCanDownloadDraftAttachment() {
        when(assignmentFiles.selectList(any())).thenReturn(List.of(new AssignmentIntroAttachment().setAssignmentId(30L)));
        when(assignments.selectById(30L)).thenReturn(new Assignment().setId(30L).setClassId(20L).setStatus("DRAFT"));
        when(permissions.hasPermission(10L,ResourceType.CLASS,20L,PermissionOp.WRITE)).thenReturn(true);
        assertDoesNotThrow(()->access.assertDownload(10L,path));
    }
    @Test void announcementAttachmentUsesItsTargetPermissions() {
        when(announcementFiles.selectList(any())).thenReturn(List.of(new AnnouncementAttachment().setAnnouncementId(40L)));
        when(announcements.selectById(40L)).thenReturn(new Announcement().setId(40L).setTargetType("CLASS").setTargetId(20L));
        assertThrows(ForbiddenException.class,()->access.assertDownload(10L,path));
        when(permissions.hasPermission(10L,ResourceType.CLASS,20L,PermissionOp.READ)).thenReturn(true);
        assertDoesNotThrow(()->access.assertDownload(10L,path));
    }
}
