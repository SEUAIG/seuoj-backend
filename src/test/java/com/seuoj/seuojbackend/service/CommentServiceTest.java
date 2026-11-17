package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.common.AuthStatus;
import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.dto.discussion.CommentCreateDTO;
import com.seuoj.seuojbackend.dto.discussion.CommentModerationDTO;
import com.seuoj.seuojbackend.entity.Comment;
import com.seuoj.seuojbackend.entity.Contest;
import com.seuoj.seuojbackend.entity.ContestProblemRel;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.entity.Vote;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.UserContext;
import com.seuoj.seuojbackend.interceptor.UserContextHolder;
import com.seuoj.seuojbackend.mapper.CommentMapper;
import com.seuoj.seuojbackend.mapper.ContestMapper;
import com.seuoj.seuojbackend.mapper.ContestProblemRelMapper;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.mapper.VoteMapper;
import com.seuoj.seuojbackend.vo.discussion.CommentSummaryVO;
import com.seuoj.seuojbackend.vo.discussion.CommentThreadVO;
import com.seuoj.seuojbackend.vo.discussion.CommentVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommentServiceTest {
    private static final long PROBLEM = 11L, CONTEST = 5L, ADMIN = 1L, ALICE = 2L, BOB = 3L;

    private final CommentMapper comments = mock(CommentMapper.class);
    private final VoteMapper votes = mock(VoteMapper.class);
    private final ProblemMapper problems = mock(ProblemMapper.class);
    private final ContestMapper contests = mock(ContestMapper.class);
    private final ContestProblemRelMapper rels = mock(ContestProblemRelMapper.class);
    private final UserInfoMapper users = mock(UserInfoMapper.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final CommentService service = new CommentService(comments, votes, problems, contests, rels, users, permissions);

    private final Problem problem = new Problem().setId(PROBLEM).setPid("P1001").setTitle("两数之和");
    private final Contest contest = new Contest();

    @BeforeAll
    static void initTableInfo() {
        var config = new MybatisConfiguration();
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(config, "comment");
        assistant.setCurrentNamespace("comment");
        TableInfoHelper.initTableInfo(assistant, Comment.class);
        TableInfoHelper.initTableInfo(assistant, Vote.class);
        TableInfoHelper.initTableInfo(assistant, Problem.class);
        TableInfoHelper.initTableInfo(assistant, ContestProblemRel.class);
    }

    @BeforeEach
    void setUp() {
        contest.setId(CONTEST);
        contest.setStartTime(LocalDateTime.now().minusHours(5));
        contest.setEndTime(LocalDateTime.now().minusHours(1));
        contest.setDiscussionEnabled(true);
        when(contests.selectById(CONTEST)).thenReturn(contest);
        when(contests.selectByIds(any())).thenReturn(List.of(contest));
        when(problems.selectOne(any())).thenReturn(problem);
        when(problems.selectById(PROBLEM)).thenReturn(problem);
        when(rels.selectCount(any())).thenReturn(1L);
        when(rels.selectList(any())).thenReturn(List.of(new ContestProblemRel().setContestId(CONTEST).setProblemId(PROBLEM)));
        when(permissions.hasPermission(any(), eq(ResourceType.PROBLEM), eq(PROBLEM), eq(PermissionOp.READ))).thenReturn(true);
        when(permissions.hasPermission(eq(ADMIN), any(), any(), any())).thenReturn(true);
        when(permissions.hasPermission(any(), eq(ResourceType.CONTEST), eq(CONTEST), eq(PermissionOp.READ))).thenReturn(true);
        doAnswer(inv -> {
            if (!permissions.hasPermission(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3))) {
                throw new ForbiddenException("无权操作");
            }
            return null;
        }).when(permissions).assertPermission(any(), any(), any(), any());
        when(users.selectByIds(any())).thenReturn(List.of(user(ALICE, "alice"), user(BOB, "bob")));
        when(votes.selectList(any())).thenReturn(List.of());
        when(comments.selectCount(any())).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    // ---------------------------------------------------------------- 能不能看

    @Test
    void problemThreadNeedsProblemReadFromLibrary() {
        when(permissions.hasPermission(any(), eq(ResourceType.PROBLEM), eq(PROBLEM), eq(PermissionOp.READ))).thenReturn(false);
        assertThrows(ForbiddenException.class, () -> service.getThread("PROBLEM", "P1001", null, null, null, null));
    }

    @Test
    void problemThreadFromContestFollowsContestToggle() {
        when(permissions.hasPermission(any(), eq(ResourceType.PROBLEM), eq(PROBLEM), eq(PermissionOp.READ))).thenReturn(false);
        stubPage(List.of());
        login(ALICE);

        CommentThreadVO vo = service.getThread("PROBLEM", "P1001", CONTEST, null, null, null);
        assertEquals("P1001", vo.pid());
        assertEquals(CONTEST, vo.contestId());
        assertTrue(vo.canPost());
        assertFalse(vo.canManage());

        contest.setDiscussionEnabled(false);
        assertThrows(NotFoundException.class, () -> service.getThread("PROBLEM", "P1001", CONTEST, null, null, null));
        contest.setDiscussionEnabled(true);
        contest.setEndTime(LocalDateTime.now().plusHours(1));
        assertThrows(ForbiddenException.class, () -> service.getThread("PROBLEM", "P1001", CONTEST, null, null, null));
    }

    @Test
    void problemFromAnotherContestIsRejected() {
        when(rels.selectCount(any())).thenReturn(0L);
        assertThrows(NotFoundException.class, () -> service.getThread("PROBLEM", "P1001", CONTEST, null, null, null));
    }

    @Test
    void contestManagerSeesContestThreadBeforeItEnds() {
        contest.setEndTime(LocalDateTime.now().plusHours(1));
        contest.setDiscussionEnabled(false);
        stubPage(List.of());
        login(ADMIN);

        CommentThreadVO vo = service.getThread("CONTEST", null, CONTEST, null, null, null);

        assertTrue(vo.canManage());
        assertEquals(CONTEST, vo.contestId());
    }

    @Test
    void summaryReportsNoAccessInsteadOfThrowing() {
        when(permissions.hasPermission(any(), eq(ResourceType.PROBLEM), eq(PROBLEM), eq(PermissionOp.READ))).thenReturn(false);
        CommentSummaryVO denied = service.getSummary("PROBLEM", "P1001", null);
        assertFalse(denied.canView());

        when(permissions.hasPermission(any(), eq(ResourceType.PROBLEM), eq(PROBLEM), eq(PermissionOp.READ))).thenReturn(true);
        when(comments.countVisibleByTargets(eq("PROBLEM"), any())).thenReturn(List.of(Map.of("target_id", PROBLEM, "cnt", 4L)));
        CommentSummaryVO ok = service.getSummary("PROBLEM", "P1001", null);
        assertTrue(ok.canView());
        assertEquals(4L, ok.commentCount());
    }

    @Test
    void existingProblemCommentIsReadableThroughAnOpenContest() {
        when(permissions.hasPermission(any(), eq(ResourceType.PROBLEM), eq(PROBLEM), eq(PermissionOp.READ))).thenReturn(false);
        Comment c = comment(20L, null, 20L, 0, ALICE);
        assertDoesNotThrow(() -> service.assertReadable(BOB, c));

        contest.setEndTime(LocalDateTime.now().plusHours(1));
        assertThrows(ForbiddenException.class, () -> service.assertReadable(BOB, c));
    }

    @Test
    void unknownTargetTypeIsRejected() {
        assertThrows(BadRequestException.class, () -> service.getThread("POST", null, null, null, null, null));
    }

    // ---------------------------------------------------------------- 拼树与可见性

    @Test
    void threadReturnsRootsWithTheirRepliesAndPinnedOnFirstPage() {
        Comment root = comment(20L, null, 20L, 0, ALICE);
        Comment reply = comment(21L, 20L, 20L, 1, BOB);
        Comment pinned = comment(21L, 20L, 20L, 1, BOB).setPinnedAt(LocalDateTime.now());
        stubPage(List.of(root));
        when(comments.selectList(any())).thenReturn(List.of(reply), List.of(pinned));
        login(BOB);

        CommentThreadVO vo = service.getThread("PROBLEM", "P1001", null, "hot", 1L, 20L);

        assertEquals(List.of(20L, 21L), vo.comments().stream().map(CommentVO::id).toList());
        assertEquals(1, vo.pinned().size());
        assertTrue(vo.comments().get(1).mine());
        assertEquals("alice", vo.comments().get(0).author().username());
    }

    @Test
    void secondPageHasNoPinnedSection() {
        stubPage(List.of());
        CommentThreadVO vo = service.getThread("PROBLEM", "P1001", null, "new", 2L, 20L);
        assertTrue(vo.pinned().isEmpty());
        assertEquals("new", vo.sort());
        verify(comments, never()).selectList(any());
    }

    @Test
    void deletedAndHiddenCommentsShowPlaceholdersToOthers() {
        Comment deleted = comment(20L, null, 20L, 0, ALICE).setDeletedAt(LocalDateTime.now()).setReplyCount(1);
        Comment hidden = comment(21L, 20L, 20L, 1, BOB).setHiddenAt(LocalDateTime.now()).setHiddenReason("剧透");
        stubPage(List.of(deleted));
        when(comments.selectList(any())).thenReturn(List.of(hidden), List.of());

        login(ALICE);
        List<CommentVO> forAlice = service.getThread("PROBLEM", "P1001", null, null, null, null).comments();
        assertNull(forAlice.get(0).content());
        assertNull(forAlice.get(0).author());
        assertTrue(forAlice.get(0).deleted());
        assertFalse(forAlice.get(0).canDelete());
        assertNull(forAlice.get(1).content());
        assertNull(forAlice.get(1).hiddenReason());

        when(comments.selectList(any())).thenReturn(List.of(hidden), List.of());
        login(BOB);
        CommentVO forAuthor = service.getThread("PROBLEM", "P1001", null, null, null, null).comments().get(1);
        assertEquals("内容", forAuthor.content());
        assertEquals("剧透", forAuthor.hiddenReason());
    }

    @Test
    void subtreeContainsOnlyTheFocusAndItsDescendants() {
        Comment root = comment(20L, null, 20L, 0, ALICE);
        Comment a = comment(21L, 20L, 20L, 1, BOB);
        Comment b = comment(22L, 20L, 20L, 1, ALICE);
        Comment a1 = comment(23L, 21L, 20L, 2, ALICE);
        when(comments.selectById(21L)).thenReturn(a);
        when(comments.selectList(any())).thenReturn(List.of(root, a, b, a1));

        CommentThreadVO vo = service.getSubtree(21L, null);

        assertEquals(21L, vo.focusId());
        assertEquals(List.of(21L, 23L), vo.comments().stream().map(CommentVO::id).toList());
    }

    // ---------------------------------------------------------------- 发言

    @Test
    void topLevelCommentPointsRootAtItself() {
        login(ALICE);
        doAnswer(inv -> {
            ((Comment) inv.getArgument(0)).setId(30L);
            return 1;
        }).when(comments).insert(any(Comment.class));

        CommentVO vo = service.create(new CommentCreateDTO("PROBLEM", "P1001", null, null, "  样例过不了  "));

        ArgumentCaptor<Comment> saved = ArgumentCaptor.forClass(Comment.class);
        verify(comments).insert(saved.capture());
        assertEquals("PROBLEM", saved.getValue().getTargetType());
        assertEquals(PROBLEM, saved.getValue().getTargetId());
        assertEquals("样例过不了", saved.getValue().getContent());
        assertEquals(0, saved.getValue().getDepth());
        verify(comments).update(isNull(), any());
        assertEquals(30L, vo.rootId());
        assertTrue(vo.mine());
    }

    @Test
    void replyInheritsThreadAndBumpsParentReplyCount() {
        login(BOB);
        Comment parent = comment(20L, null, 20L, 0, ALICE);
        when(comments.selectById(20L)).thenReturn(parent);

        service.create(new CommentCreateDTO(null, null, null, 20L, "同意"));

        ArgumentCaptor<Comment> saved = ArgumentCaptor.forClass(Comment.class);
        verify(comments).insert(saved.capture());
        assertEquals(20L, saved.getValue().getParentId());
        assertEquals(20L, saved.getValue().getRootId());
        assertEquals(1, saved.getValue().getDepth());
        assertEquals(PROBLEM, saved.getValue().getTargetId());
        verify(comments).addReplyCount(20L, 1);
    }

    @Test
    void cannotReplyToDeletedOrHiddenComment() {
        login(BOB);
        when(comments.selectById(20L)).thenReturn(comment(20L, null, 20L, 0, ALICE).setHiddenAt(LocalDateTime.now()));
        assertThrows(BadRequestException.class, () -> service.create(new CommentCreateDTO(null, null, null, 20L, "x")));
    }

    @Test
    void postingTooFastIsRejected() {
        login(ALICE);
        when(comments.selectCount(any())).thenReturn((long) CommentService.COMMENTS_PER_MINUTE);
        assertThrows(BadRequestException.class,
                () -> service.create(new CommentCreateDTO("PROBLEM", "P1001", null, null, "hi")));
        verify(comments, never()).insert(any(Comment.class));
    }

    @Test
    void blankContentIsRejected() {
        login(ALICE);
        assertThrows(BadRequestException.class,
                () -> service.create(new CommentCreateDTO("PROBLEM", "P1001", null, null, "   ")));
    }

    // ---------------------------------------------------------------- 删除

    @Test
    void deletingCommentWithRepliesLeavesPlaceholder() {
        login(ALICE);
        when(comments.selectById(20L)).thenReturn(comment(20L, null, 20L, 0, ALICE).setReplyCount(2));

        service.delete(20L);

        verify(comments).update(isNull(), any());
        verify(comments, never()).deleteById(anyLong());
    }

    @Test
    void deletingLastReplyAlsoRemovesDeletedParentPlaceholder() {
        login(BOB);
        Comment placeholder = comment(20L, null, 20L, 0, ALICE).setDeletedAt(LocalDateTime.now()).setReplyCount(0);
        when(comments.selectById(21L)).thenReturn(comment(21L, 20L, 20L, 1, BOB));
        when(comments.selectById(20L)).thenReturn(placeholder);

        service.delete(21L);

        verify(comments).deleteById(21L);
        verify(comments).addReplyCount(20L, -1);
        verify(comments).deleteById(20L);
    }

    @Test
    void othersCannotDeleteButManagerCan() {
        when(comments.selectById(20L)).thenReturn(comment(20L, null, 20L, 0, ALICE));
        login(BOB);
        assertThrows(ForbiddenException.class, () -> service.delete(20L));
        login(ADMIN);
        service.delete(20L);
        verify(comments).deleteById(20L);
    }

    // ---------------------------------------------------------------- 置顶、隐藏

    @Test
    void onlyManagersModerate() {
        when(comments.selectById(20L)).thenReturn(comment(20L, null, 20L, 0, ALICE));
        login(ALICE);
        assertThrows(ForbiddenException.class, () -> service.moderate(20L, new CommentModerationDTO(true, null, null)));
    }

    @Test
    void hidingAlsoUnpins() {
        Comment c = comment(20L, null, 20L, 0, ALICE).setPinnedAt(LocalDateTime.now());
        when(comments.selectById(20L)).thenReturn(c);
        login(ADMIN);

        service.moderate(20L, new CommentModerationDTO(true, true, "剧透"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<Comment>> w =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(comments).update(isNull(), w.capture());
        String sql = w.getValue().getSqlSet();
        assertTrue(sql.contains("hidden_at"), sql);
        assertTrue(sql.contains("pinned_at"), sql);
    }

    @Test
    void deletedCommentCannotBePinned() {
        when(comments.selectById(20L)).thenReturn(comment(20L, null, 20L, 0, ALICE).setDeletedAt(LocalDateTime.now()));
        login(ADMIN);
        assertThrows(BadRequestException.class, () -> service.moderate(20L, new CommentModerationDTO(true, null, null)));
    }

    // ---------------------------------------------------------------- 工具

    private void stubPage(List<Comment> roots) {
        when(comments.selectPage(any(), any())).thenAnswer(inv -> {
            Page<Comment> page = inv.getArgument(0);
            page.setRecords(roots);
            page.setTotal(roots.size());
            return page;
        });
    }

    private static Comment comment(long id, Long parent, long root, int depth, long author) {
        return new Comment().setId(id).setParentId(parent).setRootId(root).setDepth(depth)
                .setTargetType("PROBLEM").setTargetId(PROBLEM).setAuthorUserId(author)
                .setContent("内容").setScore(0).setReplyCount(0).setCreatedAt(LocalDateTime.now());
    }

    private static UserInfo user(long id, String username) {
        UserInfo u = new UserInfo();
        u.setId(id);
        u.setUsername(username);
        return u;
    }

    private static void login(long userId) {
        UserContextHolder.set(UserContext.of(userId, AuthStatus.AUTHENTICATED));
    }
}
