package com.seuoj.seuojbackend.controller.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.dto.announcement.AnnouncementCreateDTO;
import com.seuoj.seuojbackend.dto.announcement.AnnouncementUpdateDTO;
import com.seuoj.seuojbackend.service.AnnouncementService;
import com.seuoj.seuojbackend.vo.announcement.AnnouncementVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/class/{classId}")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping("/announcement/page")
    public Result<IPage<AnnouncementVO>> getClassAnnouncementPage(
            @PathVariable("classId") Long classId,
            @RequestParam(defaultValue = "1") @Min(1) Integer current,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size) {
        return Result.success(announcementService.getClassAnnouncementPage(classId, current, size));
    }

    @PostMapping("/announcement")
    public Result<Map<String, Long>> createClassAnnouncement(
            @PathVariable("classId") Long classId,
            @Valid @RequestBody AnnouncementCreateDTO dto) {
        Long announcementId = announcementService.createClassAnnouncement(classId, dto);
        return Result.success(Map.of("announcement_id", announcementId));
    }

    @PutMapping("/announcement/{announcementId}")
    public Result<Void> updateAnnouncement(
            @PathVariable("classId") Long classId,
            @PathVariable("announcementId") Long announcementId,
            @Valid @RequestBody AnnouncementUpdateDTO dto) {
        announcementService.updateAnnouncement(classId, announcementId, dto);
        return Result.success();
    }

    @DeleteMapping("/announcement/{announcementId}")
    public Result<Void> deleteAnnouncement(
            @PathVariable("classId") Long classId,
            @PathVariable("announcementId") Long announcementId) {
        announcementService.deleteAnnouncement(classId, announcementId);
        return Result.success();
    }

    @GetMapping("/assignment/{assignmentId}/announcement/page")
    public Result<IPage<AnnouncementVO>> getAssignmentAnnouncementPage(
            @PathVariable("classId") Long classId,
            @PathVariable("assignmentId") Long assignmentId,
            @RequestParam(defaultValue = "1") @Min(1) Integer current,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size) {
        return Result.success(announcementService.getAssignmentAnnouncementPage(
                classId, assignmentId, current, size));
    }

    @PostMapping("/assignment/{assignmentId}/announcement")
    public Result<Map<String, Long>> createAssignmentAnnouncement(
            @PathVariable("classId") Long classId,
            @PathVariable("assignmentId") Long assignmentId,
            @Valid @RequestBody AnnouncementCreateDTO dto) {
        Long announcementId = announcementService.createAssignmentAnnouncement(
                classId, assignmentId, dto);
        return Result.success(Map.of("announcement_id", announcementId));
    }
}
