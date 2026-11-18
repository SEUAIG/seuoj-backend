package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.seuoj.seuojbackend.common.AuthStatus;
import com.seuoj.seuojbackend.common.CommentTargetType;
import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.dto.contest.ContestEditorialDTO;
import com.seuoj.seuojbackend.entity.Contest;
import com.seuoj.seuojbackend.entity.Post;
import com.seuoj.seuojbackend.entity.PostAttachment;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ConflictException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.UserContext;
import com.seuoj.seuojbackend.interceptor.UserContextHolder;
import com.seuoj.seuojbackend.mapper.ContestMapper;
import com.seuoj.seuojbackend.mapper.PostAttachmentMapper;
import com.seuoj.seuojbackend.mapper.PostMapper;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.storage.FileUploadStorage;
import com.seuoj.seuojbackend.vo.contest.ContestDiscussionVO;
import com.seuoj.seuojbackend.vo.contest.ContestProblemOverviewVO;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DuplicateKeyException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContestDiscussionServiceTest {
    private static final long CONTEST = 5L, ADMIN = 1L, ALICE = 2L;

    private final ContestMapper contests = mock(ContestMapper.class);
    private final PostMapper posts = mock(PostMapper.class);
    private final PostAttachmentMapper attachments = mock(PostAttachmentMapper.class);
    private final UserInfoMapper users = mock(UserInfoMapper.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final FileAccessService fileAccess = mock(FileAccessService.class);
    private final FileUploadStorage storage = mock(FileUploadStorage.class);
    private final ProblemMapper problemMapper = mock(ProblemMapper.class);
    private final CommentService comments = mock(CommentService.class);
    private final ContestDiscussionService service = new ContestDiscussionService(
            contests, posts, attachments, users, permissions, fileAccess, storage, problemMapper, comments);

    private final Contest contest = new Contest();

    @BeforeAll
    static void initTableInfo() {
        var config = new MybatisConfiguration();
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(config, "contest-discussion");
        assistant.setCurrentNamespace("contest-discussion");
        TableInfoHelper.initTableInfo(assistant, Post.class);
        TableInfoHelper.initTableInfo(assistant, PostAttachment.class);
    }

    @BeforeEach
    void setUp() {
        contest.setId(CONTEST);
        contest.setStartTime(LocalDateTime.now().minusHours(5));
        contest.setEndTime(LocalDateTime.now().minusHours(1));
        contest.setDiscussionEnabled(true);
        when(contests.selectById(CONTEST)).thenReturn(contest);
        when(permissions.hasPermission(any(), eq(ResourceType.CONTEST), eq(CONTEST), eq(PermissionOp.READ))).thenReturn(true);
        when(permissions.hasPermission(eq(ADMIN), eq(ResourceType.CONTEST), eq(CONTEST), eq(PermissionOp.WRITE))).thenReturn(true);
        doAnswer(inv -> {
            if (!permissions.hasPermission(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3))) {
                throw new ForbiddenException("无权操作");
            }
            return null;
        }).when(permissions).assertPermission(any(), any(), any(), any());

        ContestProblemOverviewVO b = problem(2, "P0002", "B 题");
        ContestProblemOverviewVO a = problem(1, "P0001", "A 题");
        when(contests.selectContestProblems(CONTEST)).thenReturn(List.of(b, a));
        when(attachments.selectList(any())).thenReturn(List.of());
        when(problemMapper.selectList(any())).thenReturn(List.of(
                new Problem().setId(101L).setPid("P0001"), new Problem().setId(102L).setPid("P0002")));
        when(comments.countVisible(eq(CommentTargetType.PROBLEM), any())).thenReturn(Map.of(102L, 7L));
        when(comments.countVisible(eq(CommentTargetType.CONTEST), any())).thenReturn(Map.of(CONTEST, 3L));
        when(comments.countPublicSolutions(any())).thenReturn(Map.of(101L, 2L));
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    // ---------------------------------------------------------------- 读

    @Test
    void nonManagerGetsNotFoundWhenDisabled() {
        contest.setDiscussionEnabled(false);
        login(ALICE);
        assertThrows(NotFoundException.class, () -> service.getDiscussion(CONTEST));
    }

    @Test
    void nonManagerCannotOpenBeforeContestEnds() {
        contest.setEndTime(LocalDateTime.now().plusHours(1));
        login(ALICE);
        assertThrows(ForbiddenException.class, () -> service.getDiscussion(CONTEST));
    }

    @Test
    void draftEditorialIsHiddenFromParticipantsButProblemsAreListedInOrder() {
        when(posts.selectOne(any())).thenReturn(editorial("DRAFT", null));
        login(ALICE);

        ContestDiscussionVO vo = service.getDiscussion(CONTEST);

        assertNull(vo.editorial());
        assertFalse(vo.canManage());
        assertEquals(List.of("P0001", "P0002"), vo.problems().stream().map(ContestDiscussionVO.ProblemEntry::pid).toList());
    }

    @Test
    void problemIndexCarriesCommentCounts() {
        login(ALICE);

        ContestDiscussionVO vo = service.getDiscussion(CONTEST);

        assertEquals(List.of(0L, 7L), vo.problems().stream().map(ContestDiscussionVO.ProblemEntry::commentCount).toList());
        assertEquals(3L, vo.commentCount());
        assertEquals(List.of(2L, 0L), vo.problems().stream().map(ContestDiscussionVO.ProblemEntry::solutionCount).toList());
    }

    @Test
    void publishedEditorialWaitsForVisibleAfter() {
        when(posts.selectOne(any())).thenReturn(editorial("PUBLISHED", LocalDateTime.now().plusHours(2)));
        login(ALICE);
        assertNull(service.getDiscussion(CONTEST).editorial());

        when(posts.selectOne(any())).thenReturn(editorial("PUBLISHED", LocalDateTime.now().minusMinutes(1)));
        ContestDiscussionVO.Editorial e = service.getDiscussion(CONTEST).editorial();
        assertNotNull(e);
        assertTrue(e.visible());
    }

    @Test
    void anonymousCanReadPublishedEditorialOfReadableContest() {
        when(posts.selectOne(any())).thenReturn(editorial("PUBLISHED", null));
        UserContextHolder.set(UserContext.guest());
        assertNotNull(service.getDiscussion(CONTEST).editorial());
    }

    @Test
    void managerSeesDraftBeforeEndEvenWhenDisabled() {
        contest.setDiscussionEnabled(false);
        contest.setEndTime(LocalDateTime.now().plusHours(1));
        when(posts.selectOne(any())).thenReturn(editorial("PUBLISHED", null));
        login(ADMIN);

        ContestDiscussionVO vo = service.getDiscussion(CONTEST);

        assertTrue(vo.canManage());
        assertFalse(vo.ended());
        assertNotNull(vo.editorial());
        assertFalse(vo.editorial().visible(), "比赛没结束，对选手还不可见");
    }

    @Test
    void attachmentOfHiddenEditorialIsNotFoundForParticipants() {
        Post draft = editorial("DRAFT", null);
        when(attachments.selectById(9L)).thenReturn(new PostAttachment().setId(9L).setPostId(draft.getId()).setFilePath("u1/a.pdf"));
        when(posts.selectById(draft.getId())).thenReturn(draft);
        login(ALICE);
        assertThrows(NotFoundException.class, () -> service.loadAttachment(CONTEST, 9L));

        login(ADMIN);
        when(storage.loadAsResource("u1/a.pdf")).thenReturn(new ByteArrayResource(pdfBytes()));
        assertNotNull(service.loadAttachment(CONTEST, 9L).resource());
    }

    @Test
    void attachmentFromAnotherContestIsNotFound() {
        Post other = editorial("PUBLISHED", null).setContestId(99L);
        when(attachments.selectById(9L)).thenReturn(new PostAttachment().setId(9L).setPostId(other.getId()).setFilePath("u1/a.pdf"));
        when(posts.selectById(other.getId())).thenReturn(other);
        login(ADMIN);
        assertThrows(NotFoundException.class, () -> service.loadAttachment(CONTEST, 9L));
    }

    // ---------------------------------------------------------------- 写

    @Test
    void participantCannotSaveEditorial() {
        login(ALICE);
        assertThrows(ForbiddenException.class, () -> service.saveEditorial(CONTEST, dto("PUBLISHED", null, List.of())));
        verify(posts, never()).insert(any(Post.class));
    }

    @Test
    void firstSaveCreatesEditorialWithoutOwnComments() {
        when(posts.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            inv.<Post>getArgument(0).setId(77L);
            return 1;
        }).when(posts).insert(any(Post.class));
        login(ADMIN);

        Long id = service.saveEditorial(CONTEST, dto("published", null, List.of()));

        assertEquals(77L, id);
        ArgumentCaptor<Post> saved = ArgumentCaptor.forClass(Post.class);
        verify(posts).insert(saved.capture());
        assertEquals("EDITORIAL", saved.getValue().getKind());
        assertEquals("PUBLISHED", saved.getValue().getStatus());
        assertEquals(CONTEST, saved.getValue().getContestId());
        assertEquals(ADMIN, saved.getValue().getAuthorUserId());
        assertNotNull(saved.getValue().getPublishedAt());
        assertFalse(saved.getValue().getAllowComment());
    }

    @Test
    void concurrentCreateBecomesConflict() {
        when(posts.selectOne(any())).thenReturn(null);
        when(posts.insert(any(Post.class))).thenThrow(new DuplicateKeyException("uk_post_contest_editorial"));
        login(ADMIN);
        assertThrows(ConflictException.class, () -> service.saveEditorial(CONTEST, dto("DRAFT", null, List.of())));
    }

    @Test
    void updateExplicitlyClearsVisibleAfter() {
        when(posts.selectOne(any())).thenReturn(editorial("PUBLISHED", LocalDateTime.now().plusDays(1)));
        login(ADMIN);

        service.saveEditorial(CONTEST, dto("PUBLISHED", null, List.of()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Post>> update = ArgumentCaptor.forClass(Wrapper.class);
        verify(posts).update(isNull(), update.capture());
        assertTrue(update.getValue().getSqlSet().contains("visible_after"), update.getValue().getSqlSet());
        verify(posts, never()).insert(any(Post.class));
    }

    @Test
    void rejectsUnknownStatus() {
        login(ADMIN);
        assertThrows(BadRequestException.class, () -> service.saveEditorial(CONTEST, dto("HIDDEN", null, List.of())));
    }

    @Test
    void newAttachmentMustBeOwnedPdf() {
        when(posts.selectOne(any())).thenReturn(editorial("DRAFT", null));
        login(ADMIN);

        // 扩展名不是 pdf
        assertThrows(BadRequestException.class, () -> service.saveEditorial(CONTEST,
                dto("DRAFT", null, List.of(new ContestEditorialDTO.Attachment(null, "u1/x.zip", "x.zip")))));

        // 扩展名是 pdf 但内容不是
        when(storage.loadAsResource("u1/fake.pdf")).thenReturn(new ByteArrayResource("PK\3\4".getBytes(StandardCharsets.US_ASCII)));
        assertThrows(BadRequestException.class, () -> service.saveEditorial(CONTEST,
                dto("DRAFT", null, List.of(new ContestEditorialDTO.Attachment(null, "u1/fake.pdf", "fake.pdf")))));

        // 别人的文件：交给 FileAccessService 拒绝
        doThrow(new ForbiddenException("无权访问附件")).when(fileAccess).assertAttach(ADMIN, "u9/other.pdf");
        assertThrows(ForbiddenException.class, () -> service.saveEditorial(CONTEST,
                dto("DRAFT", null, List.of(new ContestEditorialDTO.Attachment(null, "u9/other.pdf", "other.pdf")))));

        verify(attachments, never()).insert(any(PostAttachment.class));
    }

    @Test
    void attachmentsAreSyncedToTheGivenList() {
        Post existing = editorial("DRAFT", null);
        when(posts.selectOne(any())).thenReturn(existing);
        when(attachments.selectList(any())).thenReturn(List.of(
                new PostAttachment().setId(1L).setPostId(existing.getId()),
                new PostAttachment().setId(2L).setPostId(existing.getId())));
        when(storage.loadAsResource("u1/new.pdf")).thenReturn(new ByteArrayResource(pdfBytes()));
        login(ADMIN);

        service.saveEditorial(CONTEST, dto("DRAFT", null, List.of(
                new ContestEditorialDTO.Attachment(2L, null, null),
                new ContestEditorialDTO.Attachment(null, "u1/new.pdf", "题解.pdf"))));

        verify(attachments).deleteById(1L);
        verify(attachments, never()).deleteById(2L);
        ArgumentCaptor<PostAttachment> added = ArgumentCaptor.forClass(PostAttachment.class);
        verify(attachments).insert(added.capture());
        assertEquals("题解.pdf", added.getValue().getFileName());
        assertEquals(pdfBytes().length, added.getValue().getFileSize());
        verify(fileAccess).assertAttach(ADMIN, "u1/new.pdf");
    }

    @Test
    void keepingAnAttachmentOfAnotherPostIsRejected() {
        when(posts.selectOne(any())).thenReturn(editorial("DRAFT", null));
        login(ADMIN);
        assertThrows(BadRequestException.class, () -> service.saveEditorial(CONTEST,
                dto("DRAFT", null, List.of(new ContestEditorialDTO.Attachment(123L, null, null)))));
    }

    // ---------------------------------------------------------------- 工具

    private static ContestProblemOverviewVO problem(int order, String pid, String title) {
        ContestProblemOverviewVO p = new ContestProblemOverviewVO();
        p.setSortOrder(order);
        p.setPid(pid);
        p.setTitle(title);
        return p;
    }

    private static Post editorial(String status, LocalDateTime visibleAfter) {
        return new Post().setId(50L).setKind("EDITORIAL").setContestId(CONTEST).setAuthorUserId(ADMIN)
                .setTitle("题解").setContent("# 题解").setStatus(status).setVisibleAfter(visibleAfter);
    }

    private static ContestEditorialDTO dto(String status, LocalDateTime visibleAfter,
                                           List<ContestEditorialDTO.Attachment> atts) {
        return new ContestEditorialDTO(" 题解 ", "正文", status, visibleAfter, atts);
    }

    private static byte[] pdfBytes() {
        return "%PDF-1.7\n%test".getBytes(StandardCharsets.US_ASCII);
    }

    private static void login(long userId) {
        UserContextHolder.set(UserContext.of(userId, AuthStatus.AUTHENTICATED));
    }
}
