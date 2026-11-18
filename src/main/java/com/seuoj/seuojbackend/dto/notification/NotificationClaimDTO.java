package com.seuoj.seuojbackend.dto.notification;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 前端准备弹出的通知 ID；一次最多 20 条 */
public record NotificationClaimDTO(@NotNull @Size(max = 20) List<Long> ids) {
}
