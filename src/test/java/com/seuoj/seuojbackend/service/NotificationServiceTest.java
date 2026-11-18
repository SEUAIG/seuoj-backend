package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.seuoj.seuojbackend.common.AuthStatus;
import com.seuoj.seuojbackend.common.NotificationType;
import com.seuoj.seuojbackend.entity.Contest;
import com.seuoj.seuojbackend.entity.UserNotification;
import com.seuoj.seuojbackend.interceptor.UserContext;
import com.seuoj.seuojbackend.interceptor.UserContextHolder;
import com.seuoj.seuojbackend.mapper.ContestMapper;
import com.seuoj.seuojbackend.mapper.UserNotificationMapper;
import com.seuoj.seuojbackend.vo.notification.PendingNotificationsVO;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    private static final long CONTEST = 5L, ADMIN = 1L, ALICE = 2L, BOB = 3L;

    private final UserNotificationMapper mapper = mock(UserNotificationMapper.class);
    private final ContestMapper contests = mock(ContestMapper.class);
    private final NotificationService service = new NotificationService(mapper, contests);

    @BeforeAll
    static void initTableInfo() {
        var config = new MybatisConfiguration();
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(config, "notification");
        assistant.setCurrentNamespace("notification");
        TableInfoHelper.initTableInfo(assistant, UserNotification.class);
        TableInfoHelper.initTableInfo(assistant, Contest.class);
    }

    @AfterEach
    void cleanup() {
        UserContextHolder.clear();
    }

    private void login(long userId) {
        UserContextHolder.set(UserContext.of(userId, AuthStatus.AUTHENTICATED));
    }

    // ---------------------------------------------------------------- 写

    @Test
    void newAnnouncementGoesToRegistrantsExceptAuthor() {
        service.announcementPublished(CONTEST, 7L, ADMIN);
        // 没有第二个要跳过的人时重复传操作者，避免 SQL 里 <> NULL 把所有人排掉
        verify(mapper).fanOutToRegistrants(eq(CONTEST), eq("CONTEST_ANNOUNCEMENT"), eq(7L), any(),
                eq(ADMIN), eq(ADMIN), eq("CONTEST_ANNOUNCEMENT"), eq("CONTEST_ANNOUNCEMENT_UPDATED"));
    }

    @Test
    void announcementUpdateSkipsPeopleWithAPendingOne() {
        service.announcementUpdated(CONTEST, 7L, ADMIN);
        verify(mapper).fanOutToRegistrants(eq(CONTEST), eq("CONTEST_ANNOUNCEMENT_UPDATED"), eq(7L), any(),
                eq(ADMIN), eq(ADMIN), eq("CONTEST_ANNOUNCEMENT"), eq("CONTEST_ANNOUNCEMENT_UPDATED"));
    }

    @Test
    void newQuestionGoesToContestManagers() {
        service.questionAsked(CONTEST, 9L, ALICE);
        verify(mapper).fanOutToContestManagers(eq(CONTEST), eq("CLARIFICATION_ASKED"), eq(9L), any(), eq(ALICE));
    }

    @Test
    void firstAnswerNotifiesAsker() {
        when(mapper.selectCount(any())).thenReturn(0L);
        service.questionAnswered(CONTEST, 9L, ALICE, ADMIN, true, false, false);
        ArgumentCaptor<UserNotification> saved = ArgumentCaptor.forClass(UserNotification.class);
        verify(mapper).insert(saved.capture());
        assertEquals(ALICE, saved.getValue().getUserId());
        assertEquals("CLARIFICATION_ANSWERED", saved.getValue().getType());
        assertEquals(CONTEST, saved.getValue().getContestId());
        assertEquals(9L, saved.getValue().getRefId());
        verify(mapper, never()).fanOutToRegistrants(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void editedAnswerNotifiesAskerOnceUntilPopped() {
        when(mapper.selectCount(any())).thenReturn(0L);
        service.questionAnswered(CONTEST, 9L, ALICE, ADMIN, false, true, false);
        ArgumentCaptor<UserNotification> saved = ArgumentCaptor.forClass(UserNotification.class);
        verify(mapper).insert(saved.capture());
        assertEquals("CLARIFICATION_ANSWER_UPDATED", saved.getValue().getType());

        // 上一条还没弹，就不再多写
        reset(mapper);
        when(mapper.selectCount(any())).thenReturn(1L);
        service.questionAnswered(CONTEST, 9L, ALICE, ADMIN, false, true, false);
        verify(mapper, never()).insert(any(UserNotification.class));
    }

    @Test
    void unchangedAnswerNotifiesNobody() {
        service.questionAnswered(CONTEST, 9L, ALICE, ADMIN, false, false, false);
        verifyNoInteractions(mapper);
    }

    @Test
    void managerAnsweringOwnQuestionDoesNotNotifyThemselves() {
        service.questionAnswered(CONTEST, 9L, ADMIN, ADMIN, true, false, false);
        verify(mapper, never()).insert(any(UserNotification.class));
    }

    @Test
    void goingPublicNotifiesOtherRegistrantsButNotAskerTwice() {
        when(mapper.selectCount(any())).thenReturn(0L);
        service.questionAnswered(CONTEST, 9L, ALICE, ADMIN, true, false, true);
        verify(mapper).insert(any(UserNotification.class));
        verify(mapper).fanOutToRegistrants(eq(CONTEST), eq("CLARIFICATION_PUBLIC"), eq(9L), any(),
                eq(ADMIN), eq(ALICE), eq("CLARIFICATION_PUBLIC"), eq("CLARIFICATION_PUBLIC"));
    }

    @Test
    void deletingSourceDropsPendingNotifications() {
        service.announcementDeleted(7L);
        service.clarificationDeleted(9L);
        verify(mapper, times(2)).delete(any());
    }

    @Test
    void writeFailureDoesNotPropagate() {
        when(mapper.fanOutToRegistrants(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("db down"));
        assertDoesNotThrow(() -> service.announcementPublished(CONTEST, 7L, ADMIN));
    }

    @Test
    void everyTypeIsKnown() {
        assertEquals(6, NotificationType.values().length);
    }

    // ---------------------------------------------------------------- 读

    @Test
    void nothingPendingSkipsTheListQuery() {
        login(ALICE);
        when(mapper.selectCount(any())).thenReturn(0L);
        PendingNotificationsVO vo = service.pending();
        assertEquals(0, vo.total());
        assertTrue(vo.items().isEmpty());
        verify(mapper, never()).selectList(any());
    }

    @Test
    void pendingCarriesContestTitleAndDropsDeletedContests() {
        login(ALICE);
        LocalDateTime t = LocalDateTime.now().minusMinutes(5);
        when(mapper.selectCount(any())).thenReturn(25L);
        when(mapper.selectList(any())).thenReturn(List.of(
                new UserNotification().setId(1L).setUserId(ALICE).setType("CONTEST_ANNOUNCEMENT")
                        .setContestId(CONTEST).setRefId(7L).setCreatedAt(t),
                new UserNotification().setId(2L).setUserId(ALICE).setType("CONTEST_ANNOUNCEMENT")
                        .setContestId(99L).setRefId(8L).setCreatedAt(t)));
        Contest c = new Contest();
        c.setId(CONTEST);
        c.setTitle("2026 新生赛");
        when(contests.selectBatchIds(any())).thenReturn(List.of(c));

        PendingNotificationsVO vo = service.pending();
        assertEquals(1, vo.items().size());
        assertEquals("2026 新生赛", vo.items().get(0).contestTitle());
        assertEquals(7L, vo.items().get(0).refId());
        assertEquals(24, vo.total());
    }

    @Test
    void claimReturnsOnlyRowsThisCallMarked() {
        login(BOB);
        when(mapper.claim(eq(1L), eq(BOB), any())).thenReturn(1);
        when(mapper.claim(eq(2L), eq(BOB), any())).thenReturn(0); // 另一个窗口先弹了，或者不是他的
        assertEquals(List.of(1L), service.claim(java.util.Arrays.asList(1L, 2L, 1L, null)));
        verify(mapper, times(1)).claim(eq(1L), eq(BOB), any());
    }
}
