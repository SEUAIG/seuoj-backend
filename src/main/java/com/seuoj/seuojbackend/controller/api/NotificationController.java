package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.dto.notification.NotificationClaimDTO;
import com.seuoj.seuojbackend.service.NotificationService;
import com.seuoj.seuojbackend.vo.notification.PendingNotificationsVO;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 右上角弹窗通知：拉取还没弹过的，弹出前 claim */
@RestController
@Validated
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/pending")
    public Result<PendingNotificationsVO> pending() {
        return Result.success(notificationService.pending());
    }

    @PostMapping("/claim")
    public Result<Map<String, List<Long>>> claim(@Valid @RequestBody NotificationClaimDTO dto) {
        return Result.success(Map.of("claimed", notificationService.claim(dto.ids())));
    }
}
