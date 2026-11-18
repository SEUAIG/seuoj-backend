package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seuoj.seuojbackend.common.NotificationType;
import com.seuoj.seuojbackend.entity.Contest;
import com.seuoj.seuojbackend.entity.UserNotification;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.mapper.ContestMapper;
import com.seuoj.seuojbackend.mapper.UserNotificationMapper;
import com.seuoj.seuojbackend.vo.notification.PendingNotificationsVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 站内通知：事件发生时按收件人一人写一行，前端轮询还没弹过的，弹出前先 claim（标成弹过）。
 * 写通知是附带动作，失败只记日志，不影响发公告、回复等主操作。
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** 一次最多返回多少条待弹通知；前端同时只显示 3 条，其余只要总数 */
    static final int PAGE = 20;
    /** 超过这么多天还没弹的通知不再补弹 */
    static final int EXPIRE_DAYS = 7;

    private final UserNotificationMapper notificationMapper;
    private final ContestMapper contestMapper;

    public NotificationService(UserNotificationMapper notificationMapper, ContestMapper contestMapper) {
        this.notificationMapper = notificationMapper;
        this.contestMapper = contestMapper;
    }

    // ---------------------------------------------------------------- 写

    public void announcementPublished(Long contestId, Long announcementId, Long actor) {
        toRegistrants(contestId, NotificationType.CONTEST_ANNOUNCEMENT, announcementId, actor, null,
                NotificationType.CONTEST_ANNOUNCEMENT, NotificationType.CONTEST_ANNOUNCEMENT_UPDATED);
    }

    /** 还有「新公告」或「公告有更新」没弹的人不再多写一条 */
    public void announcementUpdated(Long contestId, Long announcementId, Long actor) {
        toRegistrants(contestId, NotificationType.CONTEST_ANNOUNCEMENT_UPDATED, announcementId, actor, null,
                NotificationType.CONTEST_ANNOUNCEMENT, NotificationType.CONTEST_ANNOUNCEMENT_UPDATED);
    }

    public void announcementDeleted(Long announcementId) {
        dropPending(announcementId, NotificationType.CONTEST_ANNOUNCEMENT, NotificationType.CONTEST_ANNOUNCEMENT_UPDATED);
    }

    /** 选手提了新问题：告诉这场比赛的管理者 */
    public void questionAsked(Long contestId, Long clarificationId, Long asker) {
        run(() -> notificationMapper.fanOutToContestManagers(contestId, NotificationType.CLARIFICATION_ASKED.name(),
                clarificationId, LocalDateTime.now(), asker));
    }

    /**
     * 回复或修改回复之后调用。
     * 提问人：第一次回复收到「有了回复」，之后改了回复内容收到「回复有更新」（上一条还没弹就不重复写）。
     * 其他报名的人：只在这条答疑从不公开变成公开时收到一次。
     */
    public void questionAnswered(Long contestId, Long clarificationId, Long asker, Long actor,
                                 boolean firstAnswer, boolean answerChanged, boolean becamePublic) {
        if (asker != null && !asker.equals(actor) && (firstAnswer || answerChanged)) {
            run(() -> {
                boolean waiting = notificationMapper.selectCount(new LambdaQueryWrapper<UserNotification>()
                        .eq(UserNotification::getUserId, asker)
                        .eq(UserNotification::getRefId, clarificationId)
                        .in(UserNotification::getType, NotificationType.CLARIFICATION_ANSWERED.name(),
                                NotificationType.CLARIFICATION_ANSWER_UPDATED.name())
                        .isNull(UserNotification::getPoppedAt)) > 0;
                if (waiting) {
                    return 0;
                }
                NotificationType type = firstAnswer
                        ? NotificationType.CLARIFICATION_ANSWERED
                        : NotificationType.CLARIFICATION_ANSWER_UPDATED;
                return notificationMapper.insert(new UserNotification().setUserId(asker).setType(type.name())
                        .setContestId(contestId).setRefId(clarificationId).setCreatedAt(LocalDateTime.now()));
            });
        }
        if (becamePublic) {
            toRegistrants(contestId, NotificationType.CLARIFICATION_PUBLIC, clarificationId, actor, asker,
                    NotificationType.CLARIFICATION_PUBLIC, NotificationType.CLARIFICATION_PUBLIC);
        }
    }

    public void clarificationDeleted(Long clarificationId) {
        dropPending(clarificationId, NotificationType.CLARIFICATION_ANSWERED,
                NotificationType.CLARIFICATION_ANSWER_UPDATED, NotificationType.CLARIFICATION_PUBLIC,
                NotificationType.CLARIFICATION_ASKED);
    }

    private void toRegistrants(Long contestId, NotificationType type, Long refId, Long actor, Long alsoSkip,
                               NotificationType sameRef1, NotificationType sameRef2) {
        // SQL 里用 <> 排除；传 NULL 会让条件变成 UNKNOWN、把所有人都排掉，所以没有第二个人时重复传操作者
        Long skip2 = alsoSkip != null ? alsoSkip : actor;
        run(() -> notificationMapper.fanOutToRegistrants(contestId, type.name(), refId, LocalDateTime.now(),
                actor, skip2, sameRef1.name(), sameRef2.name()));
    }

    /** 源头删了：还没弹的通知一起删掉，免得弹出来点进去找不到 */
    private void dropPending(Long refId, NotificationType... types) {
        List<String> names = Arrays.stream(types).map(Enum::name).toList();
        run(() -> notificationMapper.delete(new LambdaQueryWrapper<UserNotification>()
                .eq(UserNotification::getRefId, refId)
                .in(UserNotification::getType, names)
                .isNull(UserNotification::getPoppedAt)));
    }

    private void run(java.util.function.IntSupplier write) {
        try {
            write.getAsInt();
        } catch (RuntimeException e) {
            log.warn("写站内通知失败，主操作不受影响", e);
        }
    }

    // ---------------------------------------------------------------- 读

    public PendingNotificationsVO pending() {
        Long userId = AuthContexts.requiredUserId();
        LambdaQueryWrapper<UserNotification> q = new LambdaQueryWrapper<UserNotification>()
                .eq(UserNotification::getUserId, userId)
                .isNull(UserNotification::getPoppedAt)
                .ge(UserNotification::getCreatedAt, LocalDateTime.now().minusDays(EXPIRE_DAYS));
        long total = notificationMapper.selectCount(q);
        if (total == 0) {
            return new PendingNotificationsVO(List.of(), 0);
        }
        List<UserNotification> rows = notificationMapper.selectList(q
                .orderByAsc(UserNotification::getCreatedAt)
                .orderByAsc(UserNotification::getId)
                .last("LIMIT " + PAGE));
        Map<Long, Contest> contests = contestsOf(rows.stream().map(UserNotification::getContestId).toList());
        List<PendingNotificationsVO.Item> items = new ArrayList<>();
        for (UserNotification n : rows) {
            Contest c = n.getContestId() == null ? null : contests.get(n.getContestId());
            if (n.getContestId() != null && c == null) {
                continue; // 比赛已删除
            }
            items.add(new PendingNotificationsVO.Item(n.getId(), n.getType(), n.getContestId(),
                    c != null ? c.getTitle() : null, n.getRefId(), n.getCreatedAt()));
        }
        return new PendingNotificationsVO(items, total - (rows.size() - items.size()));
    }

    /** 标成弹过，返回这次真正抢到的 ID；同一个人开了两个窗口时，只有先 claim 的那个弹 */
    public List<Long> claim(Collection<Long> ids) {
        Long userId = AuthContexts.requiredUserId();
        LocalDateTime now = LocalDateTime.now();
        return ids.stream().filter(Objects::nonNull).distinct()
                .filter(id -> notificationMapper.claim(id, userId, now) == 1)
                .toList();
    }

    private Map<Long, Contest> contestsOf(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return contestMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(Contest::getId, Function.identity(), (a, b) -> a));
    }
}
