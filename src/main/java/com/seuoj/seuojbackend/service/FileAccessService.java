package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seuoj.seuojbackend.common.*;
import com.seuoj.seuojbackend.entity.*;
import com.seuoj.seuojbackend.exception.*;
import com.seuoj.seuojbackend.mapper.*;
import java.nio.file.Path;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class FileAccessService {
    private final ClassIntroAttachmentMapper classFiles;
    private final AssignmentIntroAttachmentMapper assignmentFiles;
    private final AnnouncementAttachmentMapper announcementFiles;
    private final ClassInfoMapper classes;
    private final AssignmentMapper assignments;
    private final AnnouncementMapper announcements;
    private final PermissionService permissions;
    private final UserRoleService roles;

    public void assertDownload(Long userId, String path) { assertAccess(userId,path,PermissionOp.READ); }
    public void assertAttach(Long userId, String path) { assertAccess(userId,path,PermissionOp.WRITE); }

    private void assertAccess(Long userId, String path, PermissionOp op) {
        if (path==null || path.isBlank() || path.startsWith("/") || path.contains("\\") || path.contains(":")
                || path.contains("\0") || !Path.of(path).normalize().toString().replace('\\','/').equals(path))
            throw new BadRequestException("非法文件路径");
        if (userId==null) throw new ForbiddenException("无权访问附件");
        // New uploads are stored under a server-chosen owner directory. Never trust a client-supplied owner.
        if (path.startsWith("u"+userId+"/") || roles.isAdmin(userId)) return;
        for (var link:classFiles.selectList(new LambdaQueryWrapper<ClassIntroAttachment>().eq(ClassIntroAttachment::getFilePath,path)))
            if (canAccessClass(userId,link.getClassId(),op)) return;
        for (var link:assignmentFiles.selectList(new LambdaQueryWrapper<AssignmentIntroAttachment>().eq(AssignmentIntroAttachment::getFilePath,path)))
            if (canAccessAssignment(userId,link.getAssignmentId(),op)) return;
        for (var link:announcementFiles.selectList(new LambdaQueryWrapper<AnnouncementAttachment>().eq(AnnouncementAttachment::getFilePath,path))) {
            var announcement=announcements.selectById(link.getAnnouncementId());
            if (announcement==null) continue;
            if ("CLASS".equals(announcement.getTargetType()) && canAccessClass(userId,announcement.getTargetId(),op)) return;
            if ("ASSIGNMENT".equals(announcement.getTargetType()) && canAccessAssignment(userId,announcement.getTargetId(),op)) return;
        }
        throw new ForbiddenException("无权访问附件");
    }

    private boolean canAccessClass(Long userId,Long classId,PermissionOp op) {
        return classes.selectById(classId)!=null && permissions.hasPermission(userId,ResourceType.CLASS,classId,op);
    }
    private boolean canAccessAssignment(Long userId,Long assignmentId,PermissionOp op) {
        var assignment=assignments.selectById(assignmentId);
        if (assignment==null || classes.selectById(assignment.getClassId())==null) return false;
        if (permissions.hasPermission(userId,ResourceType.CLASS,assignment.getClassId(),PermissionOp.WRITE)) return true;
        if (op==PermissionOp.WRITE || !"PUBLISHED".equals(assignment.getStatus())) return false;
        var now=LocalDateTime.now();
        if (assignment.getVisibleFrom()!=null && now.isBefore(assignment.getVisibleFrom())) return false;
        if (assignment.getVisibleTo()!=null && now.isAfter(assignment.getVisibleTo())) return false;
        return permissions.hasPermission(userId,ResourceType.CLASS,assignment.getClassId(),PermissionOp.READ);
    }
}
