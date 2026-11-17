package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.seuoj.seuojbackend.common.CommentTargetType;
import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.PostKind;
import com.seuoj.seuojbackend.common.PostStatus;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.dto.contest.ContestEditorialDTO;
import com.seuoj.seuojbackend.entity.Contest;
import com.seuoj.seuojbackend.entity.Post;
import com.seuoj.seuojbackend.entity.PostAttachment;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ConflictException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.mapper.ContestMapper;
import com.seuoj.seuojbackend.mapper.PostAttachmentMapper;
import com.seuoj.seuojbackend.mapper.PostMapper;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.storage.FileUploadStorage;
import com.seuoj.seuojbackend.vo.contest.ContestDiscussionVO;
import com.seuoj.seuojbackend.vo.contest.ContestProblemOverviewVO;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 比赛「讨论」标签（题目讨论第 1a 期）：比赛官方题解 + 题目索引。
 *
 * <p>可见性：先要能看比赛（比赛 READ）。比赛管理者（比赛 WRITE）随时可见、可编辑。
 * 其他人要求比赛打开了「展示题解与讨论页面」且比赛已结束；官方题解还要已发布、到了 visible_after。
 */
@Service
public class ContestDiscussionService {

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final ContestMapper contestMapper;
    private final PostMapper postMapper;
    private final PostAttachmentMapper attachmentMapper;
    private final UserInfoMapper userInfoMapper;
    private final PermissionService permissionService;
    private final FileAccessService fileAccessService;
    private final FileUploadStorage fileUploadStorage;
    private final ProblemMapper problemMapper;
    private final CommentService commentService;

    public ContestDiscussionService(ContestMapper contestMapper,
                                    PostMapper postMapper,
                                    PostAttachmentMapper attachmentMapper,
                                    UserInfoMapper userInfoMapper,
                                    PermissionService permissionService,
                                    FileAccessService fileAccessService,
                                    FileUploadStorage fileUploadStorage,
                                    ProblemMapper problemMapper,
                                    CommentService commentService) {
        this.contestMapper = contestMapper;
        this.postMapper = postMapper;
        this.attachmentMapper = attachmentMapper;
        this.userInfoMapper = userInfoMapper;
        this.permissionService = permissionService;
        this.fileAccessService = fileAccessService;
        this.fileUploadStorage = fileUploadStorage;
        this.problemMapper = problemMapper;
        this.commentService = commentService;
    }

    /** 下载附件时交给 controller 的文件和展示名 */
    public record AttachmentFile(Resource resource, String fileName) {
    }

    // ---------------------------------------------------------------- 读

    public ContestDiscussionVO getDiscussion(Long contestId) {
        Contest contest = loadContest(contestId);
        Long userId = AuthContexts.userIdOrNull();
        permissionService.assertPermission(userId, ResourceType.CONTEST, contest.getId(), PermissionOp.READ);
        boolean canManage = canManage(userId, contest.getId());
        LocalDateTime now = LocalDateTime.now();
        boolean enabled = Boolean.TRUE.equals(contest.getDiscussionEnabled());
        boolean ended = isEnded(contest, now);
        if (!canManage) {
            if (!enabled) {
                throw new NotFoundException("本场比赛没有开放题解与讨论");
            }
            if (!ended) {
                throw new ForbiddenException("比赛结束后才开放题解与讨论");
            }
        }

        Post editorial = findEditorial(contest.getId());
        ContestDiscussionVO.Editorial editorialVO = null;
        if (editorial != null) {
            boolean visible = isEditorialVisible(editorial, contest, now);
            if (canManage || visible) {
                editorialVO = toEditorialVO(editorial, visible);
            }
        }

        List<ContestProblemOverviewVO> problemRows = contestMapper.selectContestProblems(contest.getId());
        List<ContestProblemOverviewVO> rows = problemRows == null ? List.of() : problemRows;
        List<String> pids = rows.stream().map(ContestProblemOverviewVO::getPid).toList();
        Map<String, Long> problemIds = pids.isEmpty() ? Map.of() : problemMapper.selectList(
                        new LambdaQueryWrapper<Problem>().in(Problem::getPid, pids))
                .stream().collect(Collectors.toMap(Problem::getPid, Problem::getId, (a, b) -> a));
        Map<Long, Long> problemCounts = commentService.countVisible(CommentTargetType.PROBLEM, problemIds.values());
        List<ContestDiscussionVO.ProblemEntry> problems = rows.stream()
                .sorted(Comparator.comparing(ContestProblemOverviewVO::getSortOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(p -> new ContestDiscussionVO.ProblemEntry(p.getSortOrder(), p.getPid(), p.getTitle(),
                        countOf(problemCounts, problemIds.get(p.getPid()))))
                .toList();
        long contestCount = commentService.countVisible(CommentTargetType.CONTEST, List.of(contest.getId()))
                .getOrDefault(contest.getId(), 0L);

        return new ContestDiscussionVO(enabled, ended, canManage, editorialVO, problems, contestCount);
    }

    public AttachmentFile loadAttachment(Long contestId, Long attachmentId) {
        Contest contest = loadContest(contestId);
        Long userId = AuthContexts.userIdOrNull();
        permissionService.assertPermission(userId, ResourceType.CONTEST, contest.getId(), PermissionOp.READ);
        PostAttachment attachment = attachmentId == null ? null : attachmentMapper.selectById(attachmentId);
        Post post = attachment == null ? null : postMapper.selectById(attachment.getPostId());
        if (post == null || !PostKind.EDITORIAL.name().equals(post.getKind())
                || !contest.getId().equals(post.getContestId())) {
            throw new NotFoundException("附件不存在");
        }
        if (!canManage(userId, contest.getId())) {
            LocalDateTime now = LocalDateTime.now();
            boolean open = Boolean.TRUE.equals(contest.getDiscussionEnabled()) && isEditorialVisible(post, contest, now);
            if (!open) {
                throw new NotFoundException("附件不存在");
            }
        }
        return new AttachmentFile(fileUploadStorage.loadAsResource(attachment.getFilePath()), attachment.getFileName());
    }

    // ---------------------------------------------------------------- 写

    @Transactional(rollbackFor = Exception.class)
    public Long saveEditorial(Long contestId, ContestEditorialDTO dto) {
        Contest contest = loadContest(contestId);
        Long userId = AuthContexts.requiredUserId();
        assertManage(userId, contest.getId());

        String status = parseStatus(dto.status());
        String title = dto.title().trim();
        String content = dto.content() == null ? "" : dto.content();
        LocalDateTime now = LocalDateTime.now();
        boolean publish = PostStatus.PUBLISHED.name().equals(status);

        Post existing = findEditorial(contest.getId());
        Long postId;
        if (existing == null) {
            Post post = new Post()
                    .setKind(PostKind.EDITORIAL.name())
                    .setAuthorUserId(userId)
                    .setContestId(contest.getId())
                    .setTitle(title)
                    .setContent(content)
                    .setStatus(status)
                    .setVisibleAfter(dto.visibleAfter())
                    .setPublishedAt(publish ? now : null)
                    .setFeatured(false)
                    // 对官方题解的讨论都在整场比赛的 thread 里，题解本身不开评论区
                    .setAllowComment(false)
                    .setScore(0)
                    .setCommentCount(0);
            try {
                postMapper.insert(post);
            } catch (DuplicateKeyException e) {
                throw new ConflictException("其他管理者刚刚创建了本场的官方题解，请刷新后再保存");
            }
            postId = post.getId();
        } else {
            LambdaUpdateWrapper<Post> update = new LambdaUpdateWrapper<Post>()
                    .eq(Post::getId, existing.getId())
                    .set(Post::getTitle, title)
                    .set(Post::getContent, content)
                    .set(Post::getStatus, status)
                    // visible_after 可以被清空，必须显式 set，updateById 会跳过 null
                    .set(Post::getVisibleAfter, dto.visibleAfter());
            if (publish && existing.getPublishedAt() == null) {
                update.set(Post::getPublishedAt, now);
            }
            postMapper.update(null, update);
            postId = existing.getId();
        }

        syncAttachments(postId, userId, dto.attachments());
        return postId;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteEditorial(Long contestId) {
        Contest contest = loadContest(contestId);
        Long userId = AuthContexts.requiredUserId();
        assertManage(userId, contest.getId());
        Post existing = findEditorial(contest.getId());
        if (existing == null) {
            throw new NotFoundException("本场比赛还没有官方题解");
        }
        attachmentMapper.delete(new LambdaQueryWrapper<PostAttachment>().eq(PostAttachment::getPostId, existing.getId()));
        postMapper.deleteById(existing.getId());
    }

    // ---------------------------------------------------------------- 内部

    private void syncAttachments(Long postId, Long userId, List<ContestEditorialDTO.Attachment> desired) {
        List<ContestEditorialDTO.Attachment> wanted = desired == null ? List.of() : desired;
        List<PostAttachment> current = attachmentMapper.selectList(
                new LambdaQueryWrapper<PostAttachment>().eq(PostAttachment::getPostId, postId));
        Set<Long> currentIds = new HashSet<>();
        current.forEach(a -> currentIds.add(a.getId()));

        Set<Long> keep = new HashSet<>();
        for (ContestEditorialDTO.Attachment a : wanted) {
            if (a == null) {
                throw new BadRequestException("附件不能为空");
            }
            if (a.id() != null) {
                if (!currentIds.contains(a.id())) {
                    throw new BadRequestException("附件不存在或不属于这篇题解");
                }
                keep.add(a.id());
            }
        }
        for (PostAttachment a : current) {
            if (!keep.contains(a.getId())) {
                attachmentMapper.deleteById(a.getId());
            }
        }
        for (ContestEditorialDTO.Attachment a : wanted) {
            if (a.id() == null) {
                attachmentMapper.insert(newPdfAttachment(postId, userId, a));
            }
        }
    }

    private PostAttachment newPdfAttachment(Long postId, Long userId, ContestEditorialDTO.Attachment a) {
        if (!StringUtils.hasText(a.filePath())) {
            throw new BadRequestException("附件路径不能为空");
        }
        String path = a.filePath().trim();
        // 只能挂自己刚上传的文件（路径在 u{userId}/ 下）或有权访问的文件，校验规则与其他附件一致
        fileAccessService.assertAttach(userId, path);
        String fileName = StringUtils.hasText(a.fileName()) ? a.fileName().trim() : path.substring(path.lastIndexOf('/') + 1);
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new BadRequestException("附件只能是 PDF：" + fileName);
        }
        Resource resource = fileUploadStorage.loadAsResource(path);
        long size;
        try (InputStream in = resource.getInputStream()) {
            byte[] head = in.readNBytes(PDF_MAGIC.length);
            if (!Arrays.equals(head, PDF_MAGIC)) {
                throw new BadRequestException("附件不是有效的 PDF 文件：" + fileName);
            }
            size = resource.contentLength();
        } catch (IOException e) {
            throw new BadRequestException("附件读取失败：" + fileName);
        }
        return new PostAttachment()
                .setPostId(postId)
                .setFilePath(path)
                .setFileName(fileName)
                .setFileSize(size);
    }

    private ContestDiscussionVO.Editorial toEditorialVO(Post post, boolean visible) {
        List<ContestDiscussionVO.Attachment> attachments = attachmentMapper.selectList(
                        new LambdaQueryWrapper<PostAttachment>()
                                .eq(PostAttachment::getPostId, post.getId())
                                .orderByAsc(PostAttachment::getId))
                .stream()
                .map(a -> new ContestDiscussionVO.Attachment(a.getId(), a.getFileName(), a.getFileSize()))
                .toList();
        UserInfo author = post.getAuthorUserId() == null ? null : userInfoMapper.selectById(post.getAuthorUserId());
        String authorName = author == null ? null
                : StringUtils.hasText(author.getNickname()) ? author.getNickname() : author.getUsername();
        return new ContestDiscussionVO.Editorial(post.getId(), post.getTitle(), post.getContent(), post.getStatus(),
                post.getVisibleAfter(), visible, authorName, post.getPublishedAt(), post.getUpdatedAt(), attachments);
    }

    private static long countOf(Map<Long, Long> counts, Long id) {
        Long n = id == null ? null : counts.get(id);
        return n == null ? 0 : n;
    }

    /** 非管理者此刻能否看到官方题解：已发布、比赛已结束、到了 visible_after */
    static boolean isEditorialVisible(Post post, Contest contest, LocalDateTime now) {
        return PostStatus.PUBLISHED.name().equals(post.getStatus())
                && isEnded(contest, now)
                && (post.getVisibleAfter() == null || !now.isBefore(post.getVisibleAfter()));
    }

    /** 与 PermissionService 的赛后判定一致：严格晚于 end_time 才算结束 */
    static boolean isEnded(Contest contest, LocalDateTime now) {
        return contest.getEndTime() != null && now.isAfter(contest.getEndTime());
    }

    private Post findEditorial(Long contestId) {
        return postMapper.selectOne(new LambdaQueryWrapper<Post>()
                .eq(Post::getKind, PostKind.EDITORIAL.name())
                .eq(Post::getContestId, contestId)
                .last("LIMIT 1"));
    }

    private static String parseStatus(String raw) {
        String s = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        for (PostStatus status : PostStatus.values()) {
            if (status.name().equals(s)) {
                return s;
            }
        }
        throw new BadRequestException("status 只能是 DRAFT 或 PUBLISHED");
    }

    private Contest loadContest(Long contestId) {
        if (contestId == null) {
            throw new BadRequestException("contest_id 不能为空");
        }
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null) {
            throw new NotFoundException("比赛不存在");
        }
        return contest;
    }

    private boolean canManage(Long userId, Long contestId) {
        return userId != null
                && permissionService.hasPermission(userId, ResourceType.CONTEST, contestId, PermissionOp.WRITE);
    }

    private void assertManage(Long userId, Long contestId) {
        permissionService.assertPermission(userId, ResourceType.CONTEST, contestId, PermissionOp.WRITE);
    }
}
