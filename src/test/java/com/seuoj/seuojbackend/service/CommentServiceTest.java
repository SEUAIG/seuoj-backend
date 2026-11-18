package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.common.AuthStatus;
import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.dto.discussion.CommentCreateDTO;
import com.seuoj.seuojbackend.dto.discussion.CommentEditDTO;
import com.seuoj.seuojbackend.dto.discussion.CommentModerationDTO;
import com.seuoj.seuojbackend.entity.Comment;
import com.seuoj.seuojbackend.entity.CommentRevision;
import com.seuoj.seuojbackend.entity.Contest;
import com.seuoj.seuojbackend.entity.ContestProblemRel;
import com.seuoj.seuojbackend.entity.Post;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.entity.Vote;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.UserContext;
import com.seuoj.seuojbackend.interceptor.UserContextHolder;
import com.seuoj.seuojbackend.mapper.CommentMapper;
import com.seuoj.seuojbackend.mapper.CommentRevisionMapper;
import com.seuoj.seuojbackend.mapper.ContestMapper;
import com.seuoj.seuojbackend.mapper.ContestProblemRelMapper;
import com.seuoj.seuojbackend.mapper.PostMapper;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.mapper.VoteMapper;
import com.seuoj.seuojbackend.vo.discussion.CommentRevisionsVO;
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
    private final PostMapper posts = mock(PostMapper.class);
    private final CommentRevisionMapper revisions = mock(CommentRevisionMapper.class);
    private final CommentService service = new CommentService(comments, votes, problems, contests, rels, users, permissions, posts, revisions);

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
        TableInfoHelper.initTableInfo(assistant, Post.class);
        TableInfoHelper.initTableInfo(assistant, CommentRevision.class);
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
        assertThrows(ForbiddenException.class, () -> service.getThread("PROBLEM", "P1001", null, null, null, null, null));
    }

    @Test
    void problemThreadFromContestFollowsContestToggle() {
        when(permissions.hasPermission(any(), eq(ResourceType.PROBLEM), eq(PROBLEM), eq(PermissionOp.READ))).thenReturn(false);
        stubPage(List.of());
        login(ALICE);

        CommentThreadVO vo = service.getThread("PROBLEM", "P1001", CONTEST, null, null, null, null);
        assertEquals("P1001", vo.pid());
        assertEquals(CONTEST, vo.contestId());
        assertTrue(vo.canPost());
        assertFalse(vo.canManage());

        contest.setDiscussionEnabled(false);
        assertThrows(NotFoundException.class, () -> service.getThread("PROBLEM", "P1001", CONTEST, null, null, null, null));
        contest.setDiscussionEnabled(true);
        contest.setEndTime(LocalDateTime.now().plusHours(1));
        assertThrows(ForbiddenException.class, () -> service.getThread("PROBLEM", "P1001", CONTEST, null, null, null, null));
    }

    @Test
    void problemFromAnotherContestIsRejected() {
        when(rels.selectCount(any())).thenReturn(0L);
        assertThrows(NotFoundException.class, () -> service.getThread("PROBLEM", "P1001", CONTEST, null, null, null, null));
    }

    @Test
    void contestManagerSeesContestThreadBeforeItEnds() {
        contest.setEndTime(LocalDateTime.now().plusHours(1));
        contest.setDiscussionEnabled(false);
        stubPage(List.of());
        login(ADMIN);

        CommentThreadVO vo = service.getThread("CONTEST", null, CONTEST, null, null, null, null);

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
        when(posts.countPublicSolutions(any())).thenReturn(List.of(Map.of("problem_id", PROBLEM, "cnt", 2L)));
        CommentSummaryVO ok = service.getSummary("PROBLEM", "P1001", null);
        assertTrue(ok.canView());
        assertEquals(4L, ok.commentCount());
        assertEquals(2L, ok.solutionCount());
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
        assertThrows(BadRequestException.class, () -> service.getThread("BLOG", null, null, null, null, null, null));
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

        CommentThreadVO vo = service.getThread("PROBLEM", "P1001", null, null, "hot", 1L, 20L);

        assertEquals(List.of(20L, 21L), vo.comments().stream().map(CommentVO::id).toList());
        assertEquals(1, vo.pinned().size());
        assertTrue(vo.comments().get(1).mine());
        assertEquals("alice", vo.comments().get(0).author().username());
    }

    @Test
    void secondPageHasNoPinnedSection() {
        stubPage(List.of());
        CommentThreadVO vo = service.getThread("PROBLEM", "P1001", null, null, "new", 2L, 20L);
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
        List<CommentVO> forAlice = service.getThread("PROBLEM", "P1001", null, null, null, null, null).comments();
        assertNull(forAlice.get(0).content());
        assertNull(forAlice.get(0).author());
        assertTrue(forAlice.get(0).deleted());
        assertFalse(forAlice.get(0).canDelete());
        assertNull(forAlice.get(1).content());
        assertNull(forAlice.get(1).hiddenReason());

        when(comments.selectList(any())).thenReturn(List.of(hidden), List.of());
        login(BOB);
        CommentVO forAuthor = service.getThread("PROBLEM", "P1001", null, null, null, null, null).comments().get(1);
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

        CommentVO vo = service.create(new CommentCreateDTO("PROBLEM", "P1001", null, null, null, "  样例过不了  "));

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

        service.create(new CommentCreateDTO(null, null, null, null, 20L, "同意"));

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
        assertThrows(BadRequestException.class, () -> service.create(new CommentCreateDTO(null, null, null, null, 20L, "x")));
    }

    @Test
    void postingTooFastIsRejected() {
        login(ALICE);
        when(comments.selectCount(any())).thenReturn((long) CommentService.COMMENTS_PER_MINUTE);
        assertThrows(BadRequestException.class,
                () -> service.create(new CommentCreateDTO("PROBLEM", "P1001", null, null, null, "hi")));
        verify(comments, never()).insert(any(Comment.class));
    }

    @Test
    void blankContentIsRejected() {
        login(ALICE);
        assertThrows(BadRequestException.class,
                () -> service.create(new CommentCreateDTO("PROBLEM", "P1001", null, null, null, "   ")));
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

    // ---------------------------------------------------------------- 题解评论区

    @Test
    void draftSolutionThreadIsOnlyForItsAuthor() {
        when(posts.selectById(40L)).thenReturn(solution(40L, ALICE, "DRAFT"));
        stubPage(List.of());
        login(BOB);
        assertThrows(NotFoundException.class, () -> service.getThread("POST", null, null, 40L, null, null, null));
        login(ALICE);
        CommentThreadVO vo = service.getThread("POST", null, null, 40L, null, null, null);
        assertEquals(40L, vo.postId());
        assertEquals("P1001", vo.pid());
        assertFalse(vo.canPost(), "草稿不能评论");
    }

    @Test
    void cannotCommentOnHiddenSolution() {
        when(posts.selectById(40L)).thenReturn(solution(40L, ALICE, "PUBLISHED").setHiddenAt(LocalDateTime.now()));
        login(ALICE);
        assertThrows(BadRequestException.class,
                () -> service.create(new CommentCreateDTO("POST", null, null, 40L, null, "hi")));
    }

    @Test
    void commentOnPublishedSolutionTargetsThePost() {
        when(posts.selectById(40L)).thenReturn(solution(40L, ALICE, "PUBLISHED"));
        login(BOB);

        service.create(new CommentCreateDTO("POST", null, null, 40L, null, "写得清楚"));

        ArgumentCaptor<Comment> saved = ArgumentCaptor.forClass(Comment.class);
        verify(comments).insert(saved.capture());
        assertEquals("POST", saved.getValue().getTargetType());
        assertEquals(40L, saved.getValue().getTargetId());
    }

    @Test
    void solutionAuthorCanDeleteCommentsUnderIt() {
        when(posts.selectById(40L)).thenReturn(solution(40L, ALICE, "PUBLISHED"));
        Comment c = comment(50L, null, 50L, 0, BOB).setTargetType("POST").setTargetId(40L);
        when(comments.selectById(50L)).thenReturn(c);
        login(ALICE);

        service.delete(50L);

        verify(comments).deleteById(50L);
    }

    // ---------------------------------------------------------------- 修改与历史

    @Test
    void editStoresOldContentAsRevision() {
        Comment c = comment(20L, null, 20L, 0, ALICE);
        when(comments.selectById(20L)).thenReturn(c);
        login(ALICE);

        service.edit(20L, new CommentEditDTO("  改过的内容  "));

        ArgumentCaptor<CommentRevision> saved = ArgumentCaptor.forClass(CommentRevision.class);
        verify(revisions).insert(saved.capture());
        assertEquals(20L, saved.getValue().getCommentId());
        assertEquals(1, saved.getValue().getVersion());
        assertEquals("内容", saved.getValue().getContent());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<Comment>> w =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(comments).update(isNull(), w.capture());
        String set = w.getValue().getSqlSet();
        assertTrue(set.contains("edited_at") && set.contains("revision_count") && set.contains("content"), set);
    }

    @Test
    void unchangedContentMakesNoRevision() {
        when(comments.selectById(20L)).thenReturn(comment(20L, null, 20L, 0, ALICE));
        login(ALICE);
        service.edit(20L, new CommentEditDTO("内容"));
        verify(revisions, never()).insert(any(CommentRevision.class));
        verify(comments, never()).update(isNull(), any());
    }

    @Test
    void onlyTheAuthorCanEditEvenManagersCannot() {
        when(comments.selectById(20L)).thenReturn(comment(20L, null, 20L, 0, ALICE));
        login(ADMIN);
        assertThrows(ForbiddenException.class, () -> service.edit(20L, new CommentEditDTO("x")));
        login(BOB);
        assertThrows(ForbiddenException.class, () -> service.edit(20L, new CommentEditDTO("x")));
    }

    @Test
    void deletedCommentCannotBeEdited() {
        when(comments.selectById(20L)).thenReturn(comment(20L, null, 20L, 0, ALICE).setDeletedAt(LocalDateTime.now()));
        login(ALICE);
        assertThrows(BadRequestException.class, () -> service.edit(20L, new CommentEditDTO("x")));
    }

    @Test
    void editsCountTowardTheRateLimit() {
        when(comments.selectById(20L)).thenReturn(comment(20L, null, 20L, 0, ALICE));
        when(comments.selectCount(any())).thenReturn((long) CommentService.COMMENTS_PER_MINUTE - 1);
        when(revisions.countEditsSince(eq(ALICE), any())).thenReturn(1L);
        login(ALICE);
        assertThrows(BadRequestException.class, () -> service.edit(20L, new CommentEditDTO("新的")));
    }

    @Test
    void revisionsRunFromFirstToCurrentWithStartTimes() {
        LocalDateTime t0 = LocalDateTime.of(2026, 11, 18, 9, 0), t1 = t0.plusMinutes(5), t2 = t0.plusMinutes(9);
        Comment c = comment(20L, null, 20L, 0, ALICE).setContent("第三版").setRevisionCount(2).setEditedAt(t2);
        c.setCreatedAt(t0);
        when(comments.selectById(20L)).thenReturn(c);
        when(revisions.selectList(any())).thenReturn(List.of(
                new CommentRevision().setCommentId(20L).setVersion(1).setContent("第一版").setCreatedAt(t1),
                new CommentRevision().setCommentId(20L).setVersion(2).setContent("第二版").setCreatedAt(t2)));

        CommentRevisionsVO vo = service.getRevisions(20L);

        assertEquals(List.of(1, 2, 3), vo.versions().stream().map(CommentRevisionsVO.Version::version).toList());
        assertEquals(List.of("第一版", "第二版", "第三版"), vo.versions().stream().map(CommentRevisionsVO.Version::content).toList());
        assertEquals(List.of(t0, t1, t2), vo.versions().stream().map(CommentRevisionsVO.Version::startedAt).toList());
        assertTrue(vo.versions().get(2).current());
    }

    @Test
    void historyOfHiddenOrDeletedCommentIsNotShownToOthers() {
        when(comments.selectById(20L)).thenReturn(comment(20L, null, 20L, 0, ALICE).setHiddenAt(LocalDateTime.now()));
        login(BOB);
        assertThrows(NotFoundException.class, () -> service.getRevisions(20L));
        login(ALICE);
        assertDoesNotThrow(() -> service.getRevisions(20L));
        when(comments.selectById(21L)).thenReturn(comment(21L, null, 21L, 0, ALICE).setDeletedAt(LocalDateTime.now()));
        assertThrows(NotFoundException.class, () -> service.getRevisions(21L));
    }

    @Test
    void editedCommentShowsRevisionNumber() {
        Comment c = comment(20L, null, 20L, 0, ALICE).setRevisionCount(2).setEditedAt(LocalDateTime.now());
        stubPage(List.of(c));
        when(comments.selectList(any())).thenReturn(List.of(), List.of());
        CommentVO vo = service.getThread("PROBLEM", "P1001", null, null, null, null, null).comments().get(0);
        assertEquals(3, vo.revision());
        assertNotNull(vo.editedAt());
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
                .setContent("内容").setScore(0).setReplyCount(0).setRevisionCount(0).setCreatedAt(LocalDateTime.now());
    }

    private static Post solution(long id, long author, String status) {
        return new Post().setId(id).setKind("SOLUTION").setProblemId(PROBLEM).setAuthorUserId(author)
                .setTitle("前缀和").setContent("x").setStatus(status).setAllowComment(true).setScore(0);
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
