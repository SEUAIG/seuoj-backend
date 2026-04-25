package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.common.AnnouncementTargetType;
import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.dto.announcement.AnnouncementCreateDTO;
import com.seuoj.seuojbackend.dto.announcement.AnnouncementUpdateDTO;
import com.seuoj.seuojbackend.dto.announcement.AttachmentDTO;
import com.seuoj.seuojbackend.entity.Announcement;
import com.seuoj.seuojbackend.entity.AnnouncementAttachment;
import com.seuoj.seuojbackend.entity.Assignment;
import com.seuoj.seuojbackend.entity.ClassInfo;
import com.seuoj.seuojbackend.exception.BadRequestException;
import java.time.LocalDateTime;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.mapper.AnnouncementAttachmentMapper;
import com.seuoj.seuojbackend.mapper.AnnouncementMapper;
import com.seuoj.seuojbackend.mapper.AssignmentMapper;
import com.seuoj.seuojbackend.mapper.ClassInfoMapper;
import com.seuoj.seuojbackend.vo.announcement.AnnouncementVO;
import com.seuoj.seuojbackend.vo.announcement.AttachmentVO;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AnnouncementService {

    private final AnnouncementMapper announcementMapper;
    private final AnnouncementAttachmentMapper attachmentMapper;
    private final ClassInfoMapper classInfoMapper;
    private final AssignmentMapper assignmentMapper;
    private final PermissionService permissionService;

    public AnnouncementService(AnnouncementMapper announcementMapper,
                               AnnouncementAttachmentMapper attachmentMapper,
                               ClassInfoMapper classInfoMapper,
                               AssignmentMapper assignmentMapper,
                               PermissionService permissionService) {
        this.announcementMapper = announcementMapper;
        this.attachmentMapper = attachmentMapper;
        this.classInfoMapper = classInfoMapper;
        this.assignmentMapper = assignmentMapper;
        this.permissionService = permissionService;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createClassAnnouncement(Long classId, AnnouncementCreateDTO dto) {
        Long userId = AuthContexts.requiredUserId();
        if (classId == null) {
            throw new BadRequestException("class_id 不能为空");
        }
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null) {
            throw new NotFoundException("班级不存在");
        }
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);

        return doCreate(AnnouncementTargetType.CLASS.name(), classInfo.getId(), userId, dto);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createAssignmentAnnouncement(Long classId, Long assignmentId,
                                             AnnouncementCreateDTO dto) {
        Long userId = AuthContexts.requiredUserId();
        if (classId == null) {
            throw new BadRequestException("class_id 不能为空");
        }
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null) {
            throw new NotFoundException("班级不存在");
        }
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);

        if (assignmentId == null) {
            throw new BadRequestException("assignment_id 不能为空");
        }
        Assignment assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new NotFoundException("作业不存在");
        }
        if (!assignment.getClassId().equals(classInfo.getId())) {
            throw new NotFoundException("作业不存在");
        }

        return doCreate(AnnouncementTargetType.ASSIGNMENT.name(), assignment.getId(), userId, dto);
    }

    public IPage<AnnouncementVO> getClassAnnouncementPage(Long classId, Integer current, Integer size) {
        Long userId = AuthContexts.requiredUserId();
        if (classId == null) {
            throw new BadRequestException("class_id 不能为空");
        }
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null) {
            throw new NotFoundException("班级不存在");
        }
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.READ);

        IPage<AnnouncementVO> page = announcementMapper.selectAnnouncementPage(
                new Page<>(current, size),
                AnnouncementTargetType.CLASS.name(),
                classInfo.getId());

        fillAttachments(page.getRecords());
        return page;
    }

    public IPage<AnnouncementVO> getAssignmentAnnouncementPage(Long classId, Long assignmentId,
                                                                Integer current, Integer size) {
        Long userId = AuthContexts.requiredUserId();
        if (classId == null) {
            throw new BadRequestException("class_id 不能为空");
        }
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null) {
            throw new NotFoundException("班级不存在");
        }
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.READ);

        if (assignmentId == null) {
            throw new BadRequestException("assignment_id 不能为空");
        }
        Assignment assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new NotFoundException("作业不存在");
        }
        if (!assignment.getClassId().equals(classInfo.getId())) {
            throw new NotFoundException("作业不存在");
        }

        boolean canWrite = permissionService.hasPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);
        if (!canWrite) {
            if (!"PUBLISHED".equals(assignment.getStatus())) {
                throw new NotFoundException("作业不存在");
            }
            LocalDateTime now = LocalDateTime.now();
            if (assignment.getVisibleFrom() != null && now.isBefore(assignment.getVisibleFrom())) {
                throw new BadRequestException("作业尚未开放");
            }
            if (assignment.getVisibleTo() != null && now.isAfter(assignment.getVisibleTo())) {
                throw new BadRequestException("作业已关闭");
            }
        }

        IPage<AnnouncementVO> page = announcementMapper.selectAnnouncementPage(
                new Page<>(current, size),
                AnnouncementTargetType.ASSIGNMENT.name(),
                assignment.getId());

        fillAttachments(page.getRecords());
        return page;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateAnnouncement(Long classId, Long announcementId, AnnouncementUpdateDTO dto) {
        Long userId = AuthContexts.requiredUserId();
        if (classId == null) {
            throw new BadRequestException("class_id 不能为空");
        }
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null) {
            throw new NotFoundException("班级不存在");
        }
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);

        if (announcementId == null) {
            throw new BadRequestException("announcement_id 不能为空");
        }
        Announcement announcement = announcementMapper.selectById(announcementId);
        if (announcement == null) {
            throw new NotFoundException("公告不存在");
        }
        assertAnnouncementBelongsToClass(announcement, classInfo);

        Announcement update = new Announcement();
        update.setId(announcement.getId());

        boolean changed = false;
        if (dto.getTitle() != null) {
            if (!StringUtils.hasText(dto.getTitle())) {
                throw new BadRequestException("title 不能为空");
            }
            update.setTitle(dto.getTitle().trim());
            changed = true;
        }
        if (dto.getContent() != null) {
            update.setContent(dto.getContent());
            changed = true;
        }
        if (dto.getIsPinned() != null) {
            update.setIsPinned(dto.getIsPinned());
            changed = true;
        }

        if (changed) {
            announcementMapper.updateById(update);
        }

        if (dto.getRemoveAttachmentIds() != null && !dto.getRemoveAttachmentIds().isEmpty()) {
            for (Long attachId : dto.getRemoveAttachmentIds()) {
                AnnouncementAttachment att = attachmentMapper.selectById(attachId);
                if (att != null && att.getAnnouncementId().equals(announcement.getId())) {
                    attachmentMapper.deleteById(attachId);
                }
            }
        }

        if (dto.getAddAttachments() != null) {
            insertAttachments(announcement.getId(), dto.getAddAttachments());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAnnouncement(Long classId, Long announcementId) {
        Long userId = AuthContexts.requiredUserId();
        if (classId == null) {
            throw new BadRequestException("class_id 不能为空");
        }
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null) {
            throw new NotFoundException("班级不存在");
        }
        permissionService.assertPermission(userId, ResourceType.CLASS, classInfo.getId(), PermissionOp.WRITE);

        if (announcementId == null) {
            throw new BadRequestException("announcement_id 不能为空");
        }
        Announcement announcement = announcementMapper.selectById(announcementId);
        if (announcement == null) {
            throw new NotFoundException("公告不存在");
        }
        assertAnnouncementBelongsToClass(announcement, classInfo);

        List<AnnouncementAttachment> attachments = attachmentMapper.selectList(
                new LambdaQueryWrapper<AnnouncementAttachment>()
                        .eq(AnnouncementAttachment::getAnnouncementId, announcement.getId()));
        for (AnnouncementAttachment att : attachments) {
            attachmentMapper.deleteById(att.getId());
        }

        announcementMapper.deleteById(announcement.getId());
    }

    private Long doCreate(String targetType, Long targetId, Long userId, AnnouncementCreateDTO dto) {
        Announcement announcement = new Announcement();
        announcement.setTargetType(targetType);
        announcement.setTargetId(targetId);
        announcement.setTitle(dto.getTitle().trim());
        announcement.setContent(dto.getContent());
        announcement.setIsPinned(dto.getIsPinned() != null && dto.getIsPinned());
        announcement.setCreatedByUserId(userId);
        announcementMapper.insert(announcement);

        if (dto.getAttachments() != null) {
            insertAttachments(announcement.getId(), dto.getAttachments());
        }

        return announcement.getId();
    }

    private void insertAttachments(Long announcementId, List<AttachmentDTO> attachments) {
        for (AttachmentDTO att : attachments) {
            AnnouncementAttachment entity = new AnnouncementAttachment();
            entity.setAnnouncementId(announcementId);
            entity.setFilePath(att.getFilePath());
            entity.setFileName(att.getFileName());
            entity.setFileSize(att.getFileSize());
            attachmentMapper.insert(entity);
        }
    }

    private void fillAttachments(List<AnnouncementVO> records) {
        for (AnnouncementVO vo : records) {
            if (vo.getAnnouncementId() == null) continue;

            List<AnnouncementAttachment> atts = attachmentMapper.selectList(
                    new LambdaQueryWrapper<AnnouncementAttachment>()
                            .eq(AnnouncementAttachment::getAnnouncementId, vo.getAnnouncementId())
                            .orderByAsc(AnnouncementAttachment::getId));

            List<AttachmentVO> attVOs = atts.stream().map(a -> {
                AttachmentVO avo = new AttachmentVO();
                avo.setId(a.getId());
                avo.setFilePath(a.getFilePath());
                avo.setFileName(a.getFileName());
                avo.setFileSize(a.getFileSize());
                return avo;
            }).toList();

            vo.setAttachments(attVOs);
        }
    }

    private void assertAnnouncementBelongsToClass(Announcement announcement, ClassInfo classInfo) {
        if (AnnouncementTargetType.CLASS.name().equals(announcement.getTargetType())) {
            if (!announcement.getTargetId().equals(classInfo.getId())) {
                throw new NotFoundException("公告不存在");
            }
        } else if (AnnouncementTargetType.ASSIGNMENT.name().equals(announcement.getTargetType())) {
            Assignment assignment = assignmentMapper.selectById(announcement.getTargetId());
            if (assignment == null || !assignment.getClassId().equals(classInfo.getId())) {
                throw new NotFoundException("公告不存在");
            }
        } else {
            throw new NotFoundException("公告不存在");
        }
    }

}
