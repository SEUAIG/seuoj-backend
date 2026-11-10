package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.client.dto.JudgeConcurrencyStatus;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.mapper.SystemSettingMapper;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 判题端同时评测的提交数。
 * <p>
 * 设置存在 system_setting（judge.max_concurrent），修改时立即推给判题端；判题端重启后会回到 .env 的默认值，
 * 所以后端每 30 秒核对一次，不一致就再推一次。没有设置时不干预判题端。
 * 重测送评上限（RejudgeDispatcher）始终跟随判题端的实际并发。
 */
@Slf4j
@Service
public class JudgeConcurrencyService {
    static final String KEY = "judge.max_concurrent";
    /** 页面允许的上限。判题端本身接受更大的值，但并发越高计时越不稳 */
    public static final int MAX_LIMIT = 16;

    private final SystemSettingMapper settings;
    private final JudgeClient judge;
    private final RejudgeDispatcher dispatcher;
    private final UserRoleService roles;
    private volatile String lastSyncError;

    public JudgeConcurrencyService(SystemSettingMapper settings, JudgeClient judge, RejudgeDispatcher dispatcher,
                                   UserRoleService roles) {
        this.settings = settings;
        this.judge = judge;
        this.dispatcher = dispatcher;
        this.roles = roles;
    }

    /**
     * @param configured  保存的设置，null 表示沿用判题端默认值
     * @param judge       判题端实时状态（含计时探针样本），连不上时为 null
     * @param judgeError  连不上判题端、或推送失败的原因
     */
    public record Status(Integer configured, int maxLimit, boolean canEdit, int rejudgeQueue, int rejudgeMaxInFlight,
                         JudgeConcurrencyStatus judge, String judgeError) {
    }

    public Status status(Long actor) {
        if (!roles.isAdmin(actor)) throw new ForbiddenException("只有管理员可以查看评测并发");
        return snapshot(actor, null);
    }

    public Status update(Long actor, Integer limit) {
        if (!roles.isSuperAdmin(actor)) throw new ForbiddenException("只有超级管理员可以修改评测并发");
        if (limit == null || limit < 1 || limit > MAX_LIMIT) {
            throw new BadRequestException("评测并发应在 1 到 " + MAX_LIMIT + " 之间");
        }
        settings.put(KEY, String.valueOf(limit), actor);
        log.info("用户 {} 把评测并发设为 {}", actor, limit);
        String error = null;
        try {
            JudgeConcurrencyStatus applied = judge.updateConcurrency(limit);
            if (applied.getLimit() != null) dispatcher.updateMaxInFlight(applied.getLimit());
        } catch (RuntimeException e) {
            error = e.getMessage() + "。设置已保存，判题端可用后会自动同步";
        }
        return snapshot(actor, error);
    }

    private Status snapshot(Long actor, String pushError) {
        JudgeConcurrencyStatus current = null;
        String error = pushError;
        try {
            current = judge.fetchConcurrency();
        } catch (RuntimeException e) {
            if (error == null) error = e.getMessage();
        }
        return new Status(configured(), MAX_LIMIT, roles.isSuperAdmin(actor), dispatcher.pending(),
                dispatcher.maxInFlight(), current, error);
    }

    Integer configured() {
        String raw = settings.find(KEY);
        if (raw == null) return null;
        try {
            int value = Integer.parseInt(raw.trim());
            return value >= 1 && value <= MAX_LIMIT ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 让判题端回到保存的设置，并让重测送评上限跟上判题端 */
    @Scheduled(initialDelayString = "${seuoj.judge-concurrency.initial-delay-millis:5000}",
            fixedDelayString = "${seuoj.judge-concurrency.sync-millis:30000}")
    public void sync() {
        try {
            Integer wanted = configured();
            JudgeConcurrencyStatus current = judge.fetchConcurrency();
            Integer limit = current.getLimit();
            if (wanted != null && !wanted.equals(limit)) {
                log.info("判题端并发为 {}，与设置 {} 不一致，重新下发", limit, wanted);
                limit = judge.updateConcurrency(wanted).getLimit();
            }
            if (limit != null) dispatcher.updateMaxInFlight(limit);
            lastSyncError = null;
        } catch (RuntimeException e) {
            String message = e.getMessage();
            if (!Objects.equals(message, lastSyncError)) log.warn("同步判题并发失败：{}", message);
            lastSyncError = message;
        }
    }
}
