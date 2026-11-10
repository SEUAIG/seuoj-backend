package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.annotation.AllowAnonymous;
import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.dto.contest.ContestNoticeDTO;
import com.seuoj.seuojbackend.service.ContestNoticeService;
import com.seuoj.seuojbackend.vo.contest.ContestNoticesVO;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 比赛公告与答疑 */
@RestController
@Validated
@RequestMapping("/api/contest/{id}")
public class ContestNoticeController {

    private final ContestNoticeService noticeService;

    public ContestNoticeController(ContestNoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @AllowAnonymous
    @GetMapping("/notices")
    public Result<ContestNoticesVO> getNotices(@PathVariable("id") Long id) {
        return Result.success(noticeService.getNotices(id));
    }

    @PostMapping("/announcement")
    public Result<Map<String, Long>> createAnnouncement(@PathVariable("id") Long id,
                                                        @Valid @RequestBody ContestNoticeDTO.Announcement dto) {
        return Result.success(Map.of("announcement_id", noticeService.createAnnouncement(id, dto)));
    }

    @PutMapping("/announcement/{announcementId}")
    public Result<Void> updateAnnouncement(@PathVariable("id") Long id,
                                           @PathVariable("announcementId") Long announcementId,
                                           @Valid @RequestBody ContestNoticeDTO.Announcement dto) {
        noticeService.updateAnnouncement(id, announcementId, dto);
        return Result.success();
    }

    @DeleteMapping("/announcement/{announcementId}")
    public Result<Void> deleteAnnouncement(@PathVariable("id") Long id,
                                           @PathVariable("announcementId") Long announcementId) {
        noticeService.deleteAnnouncement(id, announcementId);
        return Result.success();
    }

    @PostMapping("/clarification")
    public Result<Map<String, Long>> ask(@PathVariable("id") Long id,
                                         @Valid @RequestBody ContestNoticeDTO.Ask dto) {
        return Result.success(Map.of("clarification_id", noticeService.ask(id, dto)));
    }

    @PutMapping("/clarification/{clarificationId}/answer")
    public Result<Void> answer(@PathVariable("id") Long id,
                               @PathVariable("clarificationId") Long clarificationId,
                               @Valid @RequestBody ContestNoticeDTO.Answer dto) {
        noticeService.answer(id, clarificationId, dto);
        return Result.success();
    }

    @DeleteMapping("/clarification/{clarificationId}")
    public Result<Void> deleteClarification(@PathVariable("id") Long id,
                                            @PathVariable("clarificationId") Long clarificationId) {
        noticeService.deleteClarification(id, clarificationId);
        return Result.success();
    }
}
