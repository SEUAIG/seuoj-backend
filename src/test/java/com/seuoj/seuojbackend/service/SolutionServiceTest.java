package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.common.AuthStatus;
import com.seuoj.seuojbackend.common.CommentTargetType;
import com.seuoj.seuojbackend.dto.discussion.PostModerationDTO;
import com.seuoj.seuojbackend.dto.discussion.SolutionSaveDTO;
import com.seuoj.seuojbackend.entity.Contest;
import com.seuoj.seuojbackend.entity.Post;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.entity.Vote;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.UserContext;
import com.seuoj.seuojbackend.interceptor.UserContextHolder;
import com.seuoj.seuojbackend.mapper.PostMapper;
import com.seuoj.seuojbackend.mapper.SubmissionMapper;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.mapper.VoteMapper;
import com.seuoj.seuojbackend.vo.discussion.PostDetailVO;
import com.seuoj.seuojbackend.vo.discussion.SolutionPageVO;
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

class SolutionServiceTest {
    private static final long PROBLEM = 11L, CONTEST = 5L, ALICE = 2L, BOB = 3L;

    private final PostMapper posts = mock(PostMapper.class);
    private final SubmissionMapper submissions = mock(SubmissionMapper.class);
    private final UserInfoMapper users = mock(UserInfoMapper.class);
    private final VoteMapper votes = mock(VoteMapper.class);
    private final CommentService comments = mock(CommentService.class);
    private final SolutionService service = new SolutionService(posts, submissions, users, votes, comments);

    private final Problem problem = new Problem().setId(PROBLEM).setPid("P1001").setTitle("两数之和");
    private boolean managing = false;

    @BeforeAll
    static void initTableInfo() {
        var config = new MybatisConfiguration();
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(config, "solution");
        assistant.setCurrentNamespace("solution");
        TableInfoHelper.initTableInfo(assistant, Post.class);
        TableInfoHelper.initTableInfo(assistant, Submission.class);
        TableInfoHelper.initTableInfo(assistant, Vote.class);
    }

    @BeforeEach
    void setUp() {
        Contest contest = new Contest();
        contest.setId(CONTEST);
        when(comments.resolve(any(), eq("PROBLEM"), eq("P1001"), any(), isNull())).thenAnswer(inv ->
                new CommentService.Thread(CommentTargetType.PROBLEM, PROBLEM, problem,
                        inv.getArgument(3) == null ? null : contest, null, managing));
        when(comments.resolveProblemById(any(), eq(PROBLEM))).thenAnswer(inv ->
                new CommentService.Thread(CommentTargetType.PROBLEM, PROBLEM, problem, null, null, managing));
        when(comments.countVisible(any(), any())).thenReturn(Map.of());
        when(users.selectByIds(any())).thenReturn(List.of(user(ALICE, "alice"), user(BOB, "bob")));
        when(submissions.selectCount(any())).thenReturn(1L);
        when(posts.selectCount(any())).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    // ---------------------------------------------------------------- 列表

    @Test
    void listSeparatesMyUnpublishedPostsFromPublicOnes() {
        Post published = post(40L, BOB, "PUBLISHED");
        Post myDraft = post(41L, ALICE, "DRAFT");
        stubPage(List.of(published));
        when(posts.selectList(any())).thenReturn(List.of(myDraft));
        login(ALICE);

        SolutionPageVO vo = service.list("P1001", null, null, null);

        assertEquals(List.of(41L), vo.minePending().stream().map(SolutionPageVO.Item::id).toList());
        assertEquals(List.of(40L), vo.items().stream().map(SolutionPageVO.Item::id).toList());
        assertTrue(vo.hasAccepted());
        assertFalse(vo.canPost(), "写过一篇（哪怕是草稿）就不能再写");
        assertEquals(1L, vo.myCount());
        assertEquals(41L, vo.myPostId());
        assertEquals("bob", vo.items().get(0).author().username());
    }

    @Test
    void cannotPostWithoutAcOrBeyondTheLimit() {
        stubPage(List.of());
        login(ALICE);
        when(submissions.selectCount(any())).thenReturn(0L);
        SolutionPageVO noAc = service.list("P1001", null, null, null);
        assertFalse(noAc.canPost());
        assertFalse(noAc.hasAccepted());
        assertNull(noAc.myPostId());

        when(submissions.selectCount(any())).thenReturn(1L);
        assertTrue(service.list("P1001", null, null, null).canPost(), "AC 过、还没写过就能写");
    }

    @Test
    void acCheckIgnoresSkippedSubmissions() {
        login(ALICE);
        service.hasAccepted(ALICE, PROBLEM);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Submission>> w = ArgumentCaptor.forClass(Wrapper.class);
        verify(submissions).selectCount(w.capture());
        String sql = w.getValue().getSqlSegment();
        assertTrue(sql.contains("verdict"), sql);
        assertTrue(sql.contains("is_skipped"), sql);
    }

    // ---------------------------------------------------------------- 详情

    @Test
    void draftIsOnlyVisibleToItsAuthor() {
        when(posts.selectById(41L)).thenReturn(post(41L, ALICE, "DRAFT"));
        login(BOB);
        assertThrows(NotFoundException.class, () -> service.get(41L, null));
        login(ALICE);
        assertTrue(service.get(41L, null).mine());
    }

    @Test
    void hiddenSolutionShowsReasonToAuthorAndIsGoneForOthers() {
        when(posts.selectById(40L)).thenReturn(post(40L, ALICE, "PUBLISHED").setHiddenAt(LocalDateTime.now()).setHiddenReason("抄袭"));
        login(BOB);
        assertThrows(NotFoundException.class, () -> service.get(40L, null));
        login(ALICE);
        PostDetailVO vo = service.get(40L, CONTEST);
        assertTrue(vo.hidden());
        assertEquals("抄袭", vo.hiddenReason());
        assertEquals(CONTEST, vo.contestId());
    }

    @Test
    void editorialIsNotASolution() {
        when(posts.selectById(50L)).thenReturn(post(50L, ALICE, "PUBLISHED").setKind("EDITORIAL"));
        assertThrows(NotFoundException.class, () -> service.get(50L, null));
    }

    // ---------------------------------------------------------------- 新建、修改、删除

    @Test
    void creatingRequiresAc() {
        login(ALICE);
        when(submissions.selectCount(any())).thenReturn(0L);
        assertThrows(ForbiddenException.class, () -> service.create(dto("PUBLISHED", "正文")));
        verify(posts, never()).insert(any(Post.class));
    }

    @Test
    void onlyOneSolutionPerProblem() {
        login(ALICE);
        when(posts.selectCount(any())).thenReturn((long) SolutionService.MAX_PER_PROBLEM);
        assertThrows(BadRequestException.class, () -> service.create(dto("DRAFT", "")));
    }

    @Test
    void publishingRequiresContentButDraftsMayBeEmpty() {
        login(ALICE);
        assertThrows(BadRequestException.class, () -> service.create(dto("PUBLISHED", "   ")));
        when(posts.selectById(any())).thenAnswer(inv -> post(60L, ALICE, "DRAFT"));
        assertDoesNotThrow(() -> service.create(dto("DRAFT", "")));
    }

    @Test
    void createdSolutionRecordsProblemContestAndPublishTime() {
        login(ALICE);
        doAnswer(inv -> {
            ((Post) inv.getArgument(0)).setId(60L);
            return 1;
        }).when(posts).insert(any(Post.class));
        when(posts.selectById(60L)).thenReturn(post(60L, ALICE, "PUBLISHED"));

        service.create(new SolutionSaveDTO("P1001", CONTEST, "  前缀和  ", "正文", "published"));

        ArgumentCaptor<Post> saved = ArgumentCaptor.forClass(Post.class);
        verify(posts).insert(saved.capture());
        Post p = saved.getValue();
        assertEquals("SOLUTION", p.getKind());
        assertEquals(PROBLEM, p.getProblemId());
        assertEquals(CONTEST, p.getContestId());
        assertEquals("前缀和", p.getTitle());
        assertEquals("PUBLISHED", p.getStatus());
        assertNotNull(p.getPublishedAt());
        assertTrue(p.getAllowComment());
    }

    @Test
    void onlyAuthorCanEdit() {
        when(posts.selectById(40L)).thenReturn(post(40L, ALICE, "PUBLISHED"));
        login(BOB);
        assertThrows(ForbiddenException.class, () -> service.update(40L, dto("PUBLISHED", "x")));
        login(ALICE);
        service.update(40L, dto("PUBLISHED", "新正文"));
        verify(posts).update(isNull(), any());
    }

    @Test
    void othersCannotDeleteButManagersCan() {
        when(posts.selectById(40L)).thenReturn(post(40L, ALICE, "PUBLISHED"));
        login(BOB);
        assertThrows(ForbiddenException.class, () -> service.delete(40L));
        managing = true;
        service.delete(40L);
        verify(posts).deleteById(40L);
    }

    // ---------------------------------------------------------------- 精选、隐藏

    @Test
    void onlyManagersModerateAndDraftsCannotBeModerated() {
        when(posts.selectById(40L)).thenReturn(post(40L, ALICE, "PUBLISHED"));
        login(ALICE);
        assertThrows(ForbiddenException.class, () -> service.moderate(40L, new PostModerationDTO(true, null, null), null));

        managing = true;
        when(posts.selectById(41L)).thenReturn(post(41L, ALICE, "DRAFT"));
        assertThrows(BadRequestException.class, () -> service.moderate(41L, new PostModerationDTO(true, null, null), null));
    }

    @Test
    void hidingAlsoUnfeatures() {
        when(posts.selectById(40L)).thenReturn(post(40L, ALICE, "PUBLISHED").setFeatured(true));
        managing = true;
        login(BOB);

        service.moderate(40L, new PostModerationDTO(true, true, "剧透"), null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Post>> w = ArgumentCaptor.forClass(Wrapper.class);
        verify(posts).update(isNull(), w.capture());
        String set = w.getValue().getSqlSet();
        assertTrue(set.contains("hidden_at"), set);
        assertTrue(set.contains("featured"), set);
    }

    // ---------------------------------------------------------------- 工具

    private void stubPage(List<Post> records) {
        when(posts.selectPage(any(), any())).thenAnswer(inv -> {
            Page<Post> page = inv.getArgument(0);
            page.setRecords(records);
            page.setTotal(records.size());
            return page;
        });
    }

    private static SolutionSaveDTO dto(String status, String content) {
        return new SolutionSaveDTO("P1001", null, "前缀和", content, status);
    }

    private static Post post(long id, long author, String status) {
        return new Post().setId(id).setKind("SOLUTION").setProblemId(PROBLEM).setAuthorUserId(author)
                .setTitle("题解 " + id).setContent("正文").setStatus(status).setAllowComment(true)
                .setFeatured(false).setScore(0);
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
