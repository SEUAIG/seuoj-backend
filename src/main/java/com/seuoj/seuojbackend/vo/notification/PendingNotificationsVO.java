package com.seuoj.seuojbackend.vo.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

/** 还没弹过的通知：items 是最早的若干条，total 是一共积压了多少条 */
public record PendingNotificationsVO(List<Item> items, long total) {

    public record Item(
            Long id,
            String type,
            @JsonProperty("contest_id") Long contestId,
            @JsonProperty("contest_title") String contestTitle,
            @JsonProperty("ref_id") Long refId,
            @JsonProperty("created_at") LocalDateTime createdAt) {
    }
}
