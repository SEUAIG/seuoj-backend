package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.common.CommentTargetType;
import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.dto.discussion.CommentCreateDTO;
import com.seuoj.seuojbackend.dto.discussion.CommentEditDTO;
import com.seuoj.seuojbackend.dto.discussion.CommentModerationDTO;
import com.seuoj.seuojbackend.entity.Comment;
import com.seuoj.seuojbackend.entity.CommentRevision;
import com.seuoj.seuojbackend.common.PostKind;
import com.seuoj.seuojbackend.common.PostStatus;
import com.seuoj.seuojbackend.entity.Contest;
import com.seuoj.seuojbackend.entity.ContestProblemRel;
import com.seuoj.seuojbackend.entity.Post;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.entity.Vote;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ConflictException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 讨论 thread（题目讨论第 1b-1 期）：题目讨论、整场比赛讨论。
 *
 * <p>能看 thread 的人：
 * <ul>
 *   <li>整场比赛讨论：比赛 READ；非比赛管理者还要求开了「展示题解与讨论页面」且比赛已结束。</li>
 *   <li>题目讨论：题目 READ，或者通过某场包含这道题的比赛（按上一条判断）。</li>
 *   <li>题解评论区：能看到这篇题解（见 {@link #canSeePost}）且它所在的题目能看。</li>
 * </ul>
 * 能看的登录用户都能发言、投票。管理者：整场讨论看比赛 WRITE，题目讨论和题解评论区看题目 WRITE（全站管理员都有）；
 * 题解作者也能删除自己题解下的评论。
 */
@Service
public class CommentService {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 50;
    /** 一页里每棵树最多带多少条回复，更多的靠「继续这个讨论」 */
    static final int MAX_REPLIES_PER_ROOT = 200;
    static final int MAX_SUBTREE = 500;
    static final int MAX_PINNED = 10;
    static final int COMMENTS_PER_MINUTE = 5;

    private final CommentMapper commentMapper;
    private final VoteMapper voteMapper;
    private final ProblemMapper problemMapper;
    private final ContestMapper contestMapper;
    private final ContestProblemRelMapper contestProblemRelMapper;
    private final UserInfoMapper userInfoMapper;
    private final PermissionService permissionService;
    private final PostMapper postMapper;
    private final CommentRevisionMapper revisionMapper;

    public CommentService(CommentMapper commentMapper,
                          VoteMapper voteMapper,
                          ProblemMapper problemMapper,
                          ContestMapper contestMapper,
                          ContestProblemRelMapper contestProblemRelMapper,
                          UserInfoMapper userInfoMapper,
                          PermissionService permissionService,
                          PostMapper postMapper,
                          CommentRevisionMapper revisionMapper) {
        this.commentMapper = commentMapper;
        this.voteMapper = voteMapper;
        this.problemMapper = problemMapper;
        this.contestMapper = contestMapper;
        this.contestProblemRelMapper = contestProblemRelMapper;
        this.userInfoMapper = userInfoMapper;
        this.permissionService = permissionService;
        this.postMapper = postMapper;
        this.revisionMapper = revisionMapper;
    }

    /** 一个 thread：挂在哪、对当前用户来说能不能管理 */
    record Thread(CommentTargetType type, Long targetId, Problem problem, Contest contest, Post post, boolean canManage) {
    }

    // ---------------------------------------------------------------- 读

    public CommentThreadVO getThread(String targetType, String pid, Long contestId, Long postId,
                                     String sort, Long current, Long size) {
        Long userId = AuthContexts.userIdOrNull();
        Thread thread = resolve(userId, targetType, pid, contestId, postId);
        boolean newest = "new".equalsIgnoreCase(sort);
        long pageNo = current == null || current < 1 ? 1 : current;
        long pageSize = size == null || size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        LambdaQueryWrapper<Comment> top = new LambdaQueryWrapper<Comment>()
                .eq(Comment::getTargetType, thread.type().name())
                .eq(Comment::getTargetId, thread.targetId())
                .isNull(Comment::getParentId);
        if (newest) {
            top.orderByDesc(Comment::getId);
        } else {
            top.orderByDesc(Comment::getScore).orderByAsc(Comment::getId);
        }
        Page<Comment> page = commentMapper.selectPage(new Page<>(pageNo, pageSize), top);
        List<Comment> roots = page.getRecords() == null ? List.of() : page.getRecords();

        List<Comment> all = new ArrayList<>(roots);
        if (!roots.isEmpty()) {
            List<Comment> replies = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                    .in(Comment::getRootId, roots.stream().map(Comment::getId).toList())
                    .isNotNull(Comment::getParentId)
                    .orderByAsc(Comment::getId));
            // 按 id 升序截断：父发言的 id 总比回复小，截掉的只会是较新的一批，不会留下没有父节点的回复
            Map<Long, Integer> taken = new HashMap<>();
            for (Comment r : replies) {
                if (taken.merge(r.getRootId(), 1, Integer::sum) <= MAX_REPLIES_PER_ROOT) {
                    all.add(r);
                }
            }
        }

        List<Comment> pinned = pageNo == 1
                ? commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getTargetType, thread.type().name())
                        .eq(Comment::getTargetId, thread.targetId())
                        .isNotNull(Comment::getPinnedAt)
                        .orderByDesc(Comment::getPinnedAt)
                        .last("LIMIT " + MAX_PINNED))
                : List.of();

        Views views = new Views(userId, thread.canManage(), concat(all, pinned), postAuthorOf(thread));
        return new CommentThreadVO(thread.type().name(), pidOf(thread), titleOf(thread), contestIdOf(thread, contestId),
                postIdOf(thread), postTitleOf(thread), userId != null && canPostIn(thread), thread.canManage(),
                newest ? "new" : "hot", pageNo, pageSize, page.getTotal(), null, views.of(pinned), views.of(all));
    }

    /** 「继续这个讨论」和置顶跳转：以某条发言为根的子树 */
    public CommentThreadVO getSubtree(Long commentId, Long contestId) {
        Long userId = AuthContexts.userIdOrNull();
        Comment focus = loadComment(commentId);
        Thread thread = resolveOf(userId, focus);
        List<Comment> tree = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getRootId, focus.getRootId())
                .orderByAsc(Comment::getId));
        Map<Long, List<Comment>> children = tree.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(Comment::getParentId, LinkedHashMap::new, Collectors.toList()));
        List<Comment> subtree = new ArrayList<>();
        Deque<Comment> queue = new ArrayDeque<>(List.of(focus));
        while (!queue.isEmpty() && subtree.size() < MAX_SUBTREE) {
            Comment c = queue.poll();
            subtree.add(c);
            queue.addAll(children.getOrDefault(c.getId(), List.of()));
        }
        Views views = new Views(userId, thread.canManage(), subtree, postAuthorOf(thread));
        return new CommentThreadVO(thread.type().name(), pidOf(thread), titleOf(thread), contestIdOf(thread, contestId),
                postIdOf(thread), postTitleOf(thread), userId != null && canPostIn(thread), thread.canManage(),
                "hot", 1, subtree.size(), subtree.size(), focus.getId(), List.of(), views.of(subtree));
    }

    /** 题目页入口按钮用：不抛权限错误，看不了就返回 can_view = false；题目还带上公开题解数 */
    public CommentSummaryVO getSummary(String targetType, String pid, Long contestId) {
        Thread thread;
        try {
            thread = resolve(AuthContexts.userIdOrNull(), targetType, pid, contestId, null);
        } catch (ForbiddenException | NotFoundException | BadRequestException e) {
            return new CommentSummaryVO(false, 0, 0);
        }
        long count = countVisible(thread.type(), List.of(thread.targetId())).getOrDefault(thread.targetId(), 0L);
        long solutions = thread.type() == CommentTargetType.PROBLEM
                ? countPublicSolutions(List.of(thread.targetId())).getOrDefault(thread.targetId(), 0L)
                : 0;
        return new CommentSummaryVO(true, count, solutions);
    }

    /** 每道题公开可见的用户题解数（已发布、未隐藏） */
    public Map<Long, Long> countPublicSolutions(Collection<Long> problemIds) {
        List<Long> ids = problemIds == null ? List.of() : problemIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : postMapper.countPublicSolutions(ids)) {
            if (row.get("problem_id") instanceof Number n && row.get("cnt") instanceof Number m) {
                counts.put(n.longValue(), m.longValue());
            }
        }
        return counts;
    }

    /** 每个目标下可见的发言数（未删除、未隐藏，含回复） */
    public Map<Long, Long> countVisible(CommentTargetType type, Collection<Long> targetIds) {
        List<Long> ids = targetIds == null ? List.of() : targetIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : commentMapper.countVisibleByTargets(type.name(), ids)) {
            Object id = row.get("target_id");
            Object cnt = row.get("cnt");
            if (id instanceof Number n && cnt instanceof Number m) {
                counts.put(n.longValue(), m.longValue());
            }
        }
        return counts;
    }

    // ---------------------------------------------------------------- 写

    @Transactional(rollbackFor = Exception.class)
    public CommentVO create(CommentCreateDTO dto) {
        Long userId = AuthContexts.requiredUserId();
        String content = dto.content() == null ? "" : dto.content().strip();
        if (content.isEmpty()) {
            throw new BadRequestException("发言不能为空");
        }
        assertNotTooFast(userId);

        Comment comment = new Comment()
                .setAuthorUserId(userId)
                .setContent(content)
                .setScore(0)
                .setReplyCount(0);
        Thread thread;
        if (dto.parentId() != null) {
            Comment parent = commentMapper.selectById(dto.parentId());
            if (parent == null) {
                throw new NotFoundException("要回复的发言不存在");
            }
            if (parent.getDeletedAt() != null || parent.getHiddenAt() != null) {
                throw new BadRequestException("这条发言已删除或被隐藏，不能回复");
            }
            thread = resolveOf(userId, parent);
            assertCanPost(thread);
            comment.setTargetType(parent.getTargetType())
                    .setTargetId(parent.getTargetId())
                    .setParentId(parent.getId())
                    .setRootId(parent.getRootId())
                    .setDepth(parent.getDepth() == null ? 1 : parent.getDepth() + 1);
            commentMapper.insert(comment);
            commentMapper.addReplyCount(parent.getId(), 1);
        } else {
            thread = resolve(userId, dto.targetType(), dto.pid(), dto.contestId(), dto.postId());
            assertCanPost(thread);
            comment.setTargetType(thread.type().name())
                    .setTargetId(thread.targetId())
                    .setDepth(0);
            commentMapper.insert(comment);
            commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
                    .eq(Comment::getId, comment.getId())
                    .set(Comment::getRootId, comment.getId()));
            comment.setRootId(comment.getId());
        }
        comment.setCreatedAt(LocalDateTime.now());
        return new Views(userId, thread.canManage(), List.of(comment), postAuthorOf(thread)).of(comment);
    }

    /**
     * 修改自己的发言：旧正文存进 comment_revision（第 1 版是最初发的），正文没变就什么也不做。
     * 已删除的不能改；被隐藏的作者仍能改，改完仍是隐藏状态。修改和发言一起算每分钟 5 次的限流。
     */
    @Transactional(rollbackFor = Exception.class)
    public CommentVO edit(Long commentId, CommentEditDTO dto) {
        Long userId = AuthContexts.requiredUserId();
        Comment comment = loadComment(commentId);
        Thread thread = resolveOf(userId, comment);
        if (!userId.equals(comment.getAuthorUserId())) {
            throw new ForbiddenException("只能修改自己的发言");
        }
        if (comment.getDeletedAt() != null) {
            throw new BadRequestException("已删除的发言不能修改");
        }
        String content = dto.content() == null ? "" : dto.content().strip();
        if (content.isEmpty()) {
            throw new BadRequestException("发言不能为空");
        }
        if (!content.equals(comment.getContent())) {
            assertNotTooFast(userId);
            int old = comment.getRevisionCount() == null ? 0 : comment.getRevisionCount();
            try {
                revisionMapper.insert(new CommentRevision()
                        .setCommentId(comment.getId())
                        .setVersion(old + 1)
                        .setContent(comment.getContent()));
            } catch (DuplicateKeyException e) {
                throw new ConflictException("这条发言刚被改过，请刷新后再改");
            }
            commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
                    .eq(Comment::getId, comment.getId())
                    .set(Comment::getContent, content)
                    .set(Comment::getEditedAt, LocalDateTime.now())
                    .set(Comment::getRevisionCount, old + 1));
        }
        Comment fresh = loadComment(commentId);
        return new Views(userId, thread.canManage(), List.of(fresh), postAuthorOf(thread)).of(fresh);
    }

    /** 历史版本：能看到这条发言正文的人都能看（已删除的、对你隐藏的看不了） */
    public CommentRevisionsVO getRevisions(Long commentId) {
        Long userId = AuthContexts.userIdOrNull();
        Comment comment = loadComment(commentId);
        Thread thread = resolveOf(userId, comment);
        boolean mine = userId != null && userId.equals(comment.getAuthorUserId());
        if (comment.getDeletedAt() != null || (comment.getHiddenAt() != null && !mine && !thread.canManage())) {
            throw new NotFoundException("发言不存在");
        }
        List<CommentRevision> rows = revisionMapper.selectList(new LambdaQueryWrapper<CommentRevision>()
                .eq(CommentRevision::getCommentId, comment.getId())
                .orderByAsc(CommentRevision::getVersion));
        List<CommentRevisionsVO.Version> versions = new ArrayList<>();
        LocalDateTime started = comment.getCreatedAt();
        for (CommentRevision r : rows) {
            versions.add(new CommentRevisionsVO.Version(r.getVersion(), r.getContent(), started, false));
            started = r.getCreatedAt();
        }
        versions.add(new CommentRevisionsVO.Version(rows.size() + 1, comment.getContent(), started, true));
        return new CommentRevisionsVO(comment.getId(), versions);
    }

    /** 每人每分钟最多 5 次发言或修改 */
    private void assertNotTooFast(Long userId) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(1);
        Long posted = commentMapper.selectCount(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getAuthorUserId, userId)
                .gt(Comment::getCreatedAt, since));
        long edited = revisionMapper.countEditsSince(userId, since);
        if ((posted == null ? 0 : posted) + edited >= COMMENTS_PER_MINUTE) {
            throw new BadRequestException("发言太快了，请过一会儿再发");
        }
    }

    /**
     * 删除发言：作者、管理者，或者发言所在题解的作者。有回复时留「[已删除]」占位；没有回复就直接删掉，
     * 父发言因此变成没有回复的占位时一并删掉。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long commentId) {
        Long userId = AuthContexts.requiredUserId();
        Comment comment = loadComment(commentId);
        Thread thread = resolveOf(userId, comment);
        if (!userId.equals(comment.getAuthorUserId()) && !thread.canManage() && !userId.equals(postAuthorOf(thread))) {
            throw new ForbiddenException("只能删除自己的发言");
        }
        if (comment.getDeletedAt() != null) {
            return;
        }
        if (comment.getReplyCount() != null && comment.getReplyCount() > 0) {
            commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
                    .eq(Comment::getId, comment.getId())
                    .set(Comment::getDeletedAt, LocalDateTime.now())
                    .set(Comment::getPinnedAt, null)
                    .set(Comment::getPinnedBy, null));
            return;
        }
        removeLeaf(comment);
    }

    @Transactional(rollbackFor = Exception.class)
    public CommentVO moderate(Long commentId, CommentModerationDTO dto) {
        Long userId = AuthContexts.requiredUserId();
        Comment comment = loadComment(commentId);
        Thread thread = resolveOf(userId, comment);
        if (!thread.canManage()) {
            throw new ForbiddenException("只有管理者能置顶或隐藏发言");
        }
        if (comment.getDeletedAt() != null) {
            throw new BadRequestException("已删除的发言不能置顶或隐藏");
        }
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<Comment> update = new LambdaUpdateWrapper<Comment>().eq(Comment::getId, comment.getId());
        boolean changed = false;
        boolean hiding = Boolean.TRUE.equals(dto.hidden());
        if (dto.hidden() != null) {
            String reason = StringUtils.hasText(dto.reason()) ? dto.reason().strip() : null;
            update.set(Comment::getHiddenAt, hiding ? now : null)
                    .set(Comment::getHiddenBy, hiding ? userId : null)
                    .set(Comment::getHiddenReason, hiding ? reason : null);
            changed = true;
        }
        if (hiding) {
            // 隐藏的发言不再留在置顶区
            update.set(Comment::getPinnedAt, null).set(Comment::getPinnedBy, null);
        } else if (dto.pinned() != null) {
            if (Boolean.TRUE.equals(dto.pinned()) && comment.getHiddenAt() != null && dto.hidden() == null) {
                throw new BadRequestException("被隐藏的发言不能置顶");
            }
            boolean pin = Boolean.TRUE.equals(dto.pinned());
            update.set(Comment::getPinnedAt, pin ? now : null).set(Comment::getPinnedBy, pin ? userId : null);
            changed = true;
        }
        if (changed) {
            commentMapper.update(null, update);
        }
        Comment fresh = loadComment(commentId);
        return new Views(userId, true, List.of(fresh), postAuthorOf(thread)).of(fresh);
    }

    // ---------------------------------------------------------------- 给投票等调用

    /** 当前用户能看到这条发言所在的 thread，否则抛出权限错误 */
    void assertReadable(Long userId, Comment comment) {
        resolveOf(userId, comment);
    }

    // ---------------------------------------------------------------- 内部

    private void removeLeaf(Comment comment) {
        Comment current = comment;
        while (current != null) {
            commentMapper.deleteById(current.getId());
            if (current.getParentId() == null) {
                return;
            }
            commentMapper.addReplyCount(current.getParentId(), -1);
            Comment parent = commentMapper.selectById(current.getParentId());
            boolean orphanPlaceholder = parent != null && parent.getDeletedAt() != null
                    && (parent.getReplyCount() == null || parent.getReplyCount() <= 0);
            current = orphanPlaceholder ? parent : null;
        }
    }

    /** 按 target_type + pid / contest_id / post_id 定位 thread 并检查能否查看 */
    Thread resolve(Long userId, String rawType, String pid, Long contestId, Long postId) {
        CommentTargetType type = parseType(rawType);
        if (type == CommentTargetType.CONTEST) {
            Contest contest = loadContest(contestId);
            assertContestOpen(userId, contest);
            return new Thread(type, contest.getId(), null, contest, null, canManageContest(userId, contest.getId()));
        }
        if (type == CommentTargetType.POST) {
            return resolvePost(userId, postId);
        }
        Problem problem = loadProblem(pid);
        Contest contest = null;
        if (contestId != null) {
            contest = loadContest(contestId);
            assertContestOpen(userId, contest);
            Long inContest = contestProblemRelMapper.selectCount(new LambdaQueryWrapper<ContestProblemRel>()
                    .eq(ContestProblemRel::getContestId, contest.getId())
                    .eq(ContestProblemRel::getProblemId, problem.getId()));
            if (inContest == null || inContest == 0) {
                throw new NotFoundException("题目不在该比赛中");
            }
        } else {
            permissionService.assertPermission(userId, ResourceType.PROBLEM, problem.getId(), PermissionOp.READ);
        }
        return new Thread(type, problem.getId(), problem, contest, null, canManageProblem(userId, problem.getId()));
    }

    /** 已有发言所在的 thread；题目讨论不带比赛上下文，题库或任一场开放讨论的比赛能看到就行 */
    Thread resolveOf(Long userId, Comment comment) {
        CommentTargetType type = parseStoredType(comment.getTargetType());
        if (type == CommentTargetType.CONTEST) {
            Contest contest = loadContest(comment.getTargetId());
            assertContestOpen(userId, contest);
            return new Thread(type, contest.getId(), null, contest, null, canManageContest(userId, contest.getId()));
        }
        if (type == CommentTargetType.POST) {
            return resolvePost(userId, comment.getTargetId());
        }
        return resolveProblemById(userId, comment.getTargetId());
    }

    /** 题目（按 id）对当前用户能不能看：题目 READ，或通过任一场开放讨论的比赛 */
    Thread resolveProblemById(Long userId, Long problemId) {
        Problem problem = problemId == null ? null : problemMapper.selectById(problemId);
        if (problem == null) {
            throw new NotFoundException("题目不存在");
        }
        if (!permissionService.hasPermission(userId, ResourceType.PROBLEM, problem.getId(), PermissionOp.READ)
                && !readableViaSomeContest(userId, problem.getId())) {
            throw new ForbiddenException("无权查看这道题的讨论");
        }
        return new Thread(CommentTargetType.PROBLEM, problem.getId(), problem, null, null,
                canManageProblem(userId, problem.getId()));
    }

    /** 题解评论区：题解所在的题目能看，且这篇题解对当前用户可见 */
    private Thread resolvePost(Long userId, Long postId) {
        Post post = postId == null ? null : postMapper.selectById(postId);
        if (post == null || !PostKind.SOLUTION.name().equals(post.getKind())) {
            throw new NotFoundException("题解不存在");
        }
        Thread problemThread = resolveProblemById(userId, post.getProblemId());
        if (!canSeePost(userId, post, problemThread.canManage())) {
            throw new NotFoundException("题解不存在");
        }
        return new Thread(CommentTargetType.POST, post.getId(), problemThread.problem(), null, post,
                problemThread.canManage());
    }

    /** 用户题解对当前用户是否可见：已发布且未隐藏的人人可见；草稿只有作者；被隐藏的作者和管理者能看 */
    static boolean canSeePost(Long userId, Post post, boolean canManage) {
        boolean mine = userId != null && userId.equals(post.getAuthorUserId());
        if (!PostStatus.PUBLISHED.name().equals(post.getStatus())) {
            return mine;
        }
        return post.getHiddenAt() == null || mine || canManage;
    }

    /** 能不能在这个 thread 里发言：题解要已发布、未隐藏、允许评论 */
    private static boolean canPostIn(Thread thread) {
        Post post = thread.post();
        return post == null || (PostStatus.PUBLISHED.name().equals(post.getStatus())
                && post.getHiddenAt() == null && !Boolean.FALSE.equals(post.getAllowComment()));
    }

    private static void assertCanPost(Thread thread) {
        if (!canPostIn(thread)) {
            throw new BadRequestException("这篇题解现在不能评论");
        }
    }

    private boolean readableViaSomeContest(Long userId, Long problemId) {
        List<Long> contestIds = contestProblemRelMapper.selectList(new LambdaQueryWrapper<ContestProblemRel>()
                        .eq(ContestProblemRel::getProblemId, problemId))
                .stream().map(ContestProblemRel::getContestId).distinct().toList();
        if (contestIds.isEmpty()) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Contest contest : contestMapper.selectByIds(contestIds)) {
            if (!permissionService.hasPermission(userId, ResourceType.CONTEST, contest.getId(), PermissionOp.READ)) {
                continue;
            }
            if (canManageContest(userId, contest.getId())
                    || (Boolean.TRUE.equals(contest.getDiscussionEnabled()) && ContestDiscussionService.isEnded(contest, now))) {
                return true;
            }
        }
        return false;
    }

    /** 比赛 READ；非管理者还要开关打开、比赛已结束（与比赛「讨论」标签一致） */
    private void assertContestOpen(Long userId, Contest contest) {
        permissionService.assertPermission(userId, ResourceType.CONTEST, contest.getId(), PermissionOp.READ);
        if (canManageContest(userId, contest.getId())) {
            return;
        }
        if (!Boolean.TRUE.equals(contest.getDiscussionEnabled())) {
            throw new NotFoundException("本场比赛没有开放题解与讨论");
        }
        if (!ContestDiscussionService.isEnded(contest, LocalDateTime.now())) {
            throw new ForbiddenException("比赛结束后才开放题解与讨论");
        }
    }

    private boolean canManageContest(Long userId, Long contestId) {
        return userId != null
                && permissionService.hasPermission(userId, ResourceType.CONTEST, contestId, PermissionOp.WRITE);
    }

    private boolean canManageProblem(Long userId, Long problemId) {
        return userId != null
                && permissionService.hasPermission(userId, ResourceType.PROBLEM, problemId, PermissionOp.WRITE);
    }

    private static CommentTargetType parseType(String raw) {
        String s = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (CommentTargetType.PROBLEM.name().equals(s)) {
            return CommentTargetType.PROBLEM;
        }
        if (CommentTargetType.CONTEST.name().equals(s)) {
            return CommentTargetType.CONTEST;
        }
        if (CommentTargetType.POST.name().equals(s)) {
            return CommentTargetType.POST;
        }
        throw new BadRequestException("target_type 只能是 PROBLEM、CONTEST 或 POST");
    }

    private static CommentTargetType parseStoredType(String raw) {
        try {
            return CommentTargetType.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new NotFoundException("发言不存在");
        }
    }

    private Comment loadComment(Long id) {
        Comment comment = id == null ? null : commentMapper.selectById(id);
        if (comment == null) {
            throw new NotFoundException("发言不存在");
        }
        return comment;
    }

    private Problem loadProblem(String pid) {
        if (!StringUtils.hasText(pid)) {
            throw new BadRequestException("pid 不能为空");
        }
        Problem problem = problemMapper.selectOne(new LambdaQueryWrapper<Problem>()
                .eq(Problem::getPid, pid.trim())
                .last("LIMIT 1"));
        if (problem == null) {
            throw new NotFoundException("题目不存在");
        }
        return problem;
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

    private static String pidOf(Thread thread) {
        return thread.problem() == null ? null : thread.problem().getPid();
    }

    private static String titleOf(Thread thread) {
        return thread.problem() == null ? null : thread.problem().getTitle();
    }

    /** 题目讨论只回显请求带来的比赛（用于页面链回比赛）；整场讨论就是该比赛 */
    private static Long contestIdOf(Thread thread, Long requested) {
        if (thread.type() == CommentTargetType.CONTEST) {
            return thread.targetId();
        }
        return thread.contest() != null ? thread.contest().getId() : requested;
    }

    private static Long postIdOf(Thread thread) {
        return thread.post() == null ? null : thread.post().getId();
    }

    private static String postTitleOf(Thread thread) {
        return thread.post() == null ? null : thread.post().getTitle();
    }

    private static Long postAuthorOf(Thread thread) {
        return thread.post() == null ? null : thread.post().getAuthorUserId();
    }

    private static List<Comment> concat(List<Comment> a, List<Comment> b) {
        List<Comment> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    /** 批量查作者和「我的票」，再把发言转成对当前用户可见的样子 */
    private final class Views {
        private final Long userId;
        private final boolean canManage;
        private final Map<Long, UserInfo> authors;
        private final Map<Long, Integer> myVotes;
        /** 题解评论区里题解作者也能删评论 */
        private final Long postAuthorId;

        Views(Long userId, boolean canManage, List<Comment> comments, Long postAuthorId) {
            this.userId = userId;
            this.canManage = canManage;
            this.postAuthorId = postAuthorId;
            List<Long> authorIds = comments.stream().map(Comment::getAuthorUserId)
                    .filter(Objects::nonNull).distinct().toList();
            this.authors = authorIds.isEmpty() ? Map.of() : userInfoMapper.selectByIds(authorIds).stream()
                    .collect(Collectors.toMap(UserInfo::getId, Function.identity(), (x, y) -> x));
            List<Long> ids = comments.stream().map(Comment::getId).filter(Objects::nonNull).distinct().toList();
            this.myVotes = userId == null || ids.isEmpty() ? Map.of() : voteMapper.selectList(new LambdaQueryWrapper<Vote>()
                            .eq(Vote::getTargetType, "COMMENT")
                            .eq(Vote::getUserId, userId)
                            .in(Vote::getTargetId, ids))
                    .stream().collect(Collectors.toMap(Vote::getTargetId, Vote::getValue, (x, y) -> x));
        }

        List<CommentVO> of(List<Comment> comments) {
            return comments.stream().map(this::of).toList();
        }

        CommentVO of(Comment c) {
            boolean mine = userId != null && userId.equals(c.getAuthorUserId());
            boolean deleted = c.getDeletedAt() != null;
            boolean hidden = c.getHiddenAt() != null;
            boolean show = !deleted && (!hidden || mine || canManage);
            UserInfo u = show && c.getAuthorUserId() != null ? authors.get(c.getAuthorUserId()) : null;
            CommentVO.Author author = u == null ? null : new CommentVO.Author(u.getId(), u.getUsername(), u.getNickname());
            return new CommentVO(c.getId(), c.getParentId(), c.getRootId(), c.getDepth() == null ? 0 : c.getDepth(),
                    author, show ? c.getContent() : null, deleted, hidden,
                    hidden && (mine || canManage) ? c.getHiddenReason() : null,
                    c.getScore() == null ? 0 : c.getScore(), c.getId() == null ? 0 : myVotes.getOrDefault(c.getId(), 0),
                    c.getReplyCount() == null ? 0 : c.getReplyCount(), c.getPinnedAt() != null, mine,
                    !deleted && userId != null && (mine || canManage || userId.equals(postAuthorId)), c.getCreatedAt(),
                    show ? c.getEditedAt() : null,
                    show && c.getRevisionCount() != null ? c.getRevisionCount() + 1 : 1);
        }
    }
}
