package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.common.CommentTargetType;
import com.seuoj.seuojbackend.common.PostKind;
import com.seuoj.seuojbackend.common.PostStatus;
import com.seuoj.seuojbackend.common.SubmissionVerdict;
import com.seuoj.seuojbackend.dto.discussion.PostModerationDTO;
import com.seuoj.seuojbackend.dto.discussion.SolutionSaveDTO;
import com.seuoj.seuojbackend.entity.Post;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.entity.Vote;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.mapper.PostMapper;
import com.seuoj.seuojbackend.mapper.SubmissionMapper;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.mapper.VoteMapper;
import com.seuoj.seuojbackend.vo.discussion.CommentVO;
import com.seuoj.seuojbackend.vo.discussion.PostDetailVO;
import com.seuoj.seuojbackend.vo.discussion.SolutionPageVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 用户题解（题目讨论第 1b-2 期，post.kind = SOLUTION）。
 *
 * <ul>
 *   <li>能看：和这道题的讨论一样（题目 READ，或通过开放讨论的比赛）；草稿只有作者看得到，被隐藏的作者和管理者看得到。</li>
 *   <li>能写：登录且 AC 过这道题（submission.verdict = Accepted 且没被跳过，题库、比赛、作业里交的都算），每人每题最多 3 篇。</li>
 *   <li>能改：作者；能删：作者或管理者；精选、隐藏：管理者（题目 WRITE，全站管理员都有）。</li>
 * </ul>
 */
@Service
public class SolutionService {

    static final int MAX_PER_PROBLEM = 3;
    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 50;

    private final PostMapper postMapper;
    private final SubmissionMapper submissionMapper;
    private final UserInfoMapper userInfoMapper;
    private final VoteMapper voteMapper;
    private final CommentService commentService;

    public SolutionService(PostMapper postMapper,
                           SubmissionMapper submissionMapper,
                           UserInfoMapper userInfoMapper,
                           VoteMapper voteMapper,
                           CommentService commentService) {
        this.postMapper = postMapper;
        this.submissionMapper = submissionMapper;
        this.userInfoMapper = userInfoMapper;
        this.voteMapper = voteMapper;
        this.commentService = commentService;
    }

    // ---------------------------------------------------------------- 读

    public SolutionPageVO list(String pid, Long contestId, Long current, Long size) {
        Long userId = AuthContexts.userIdOrNull();
        CommentService.Thread thread = commentService.resolve(userId, "PROBLEM", pid, contestId, null);
        Long problemId = thread.targetId();
        boolean canManage = thread.canManage();
        long pageNo = current == null || current < 1 ? 1 : current;
        long pageSize = size == null || size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        // 公开的题解：已发布；被隐藏的只有管理者在列表里看得到
        LambdaQueryWrapper<Post> visible = new LambdaQueryWrapper<Post>()
                .eq(Post::getKind, PostKind.SOLUTION.name())
                .eq(Post::getProblemId, problemId)
                .eq(Post::getStatus, PostStatus.PUBLISHED.name());
        if (!canManage) {
            visible.isNull(Post::getHiddenAt);
        }
        visible.orderByDesc(Post::getFeatured).orderByDesc(Post::getScore).orderByAsc(Post::getId);
        Page<Post> page = postMapper.selectPage(new Page<>(pageNo, pageSize), visible);
        List<Post> items = page.getRecords() == null ? List.of() : page.getRecords();

        // 我自己还没公开的：草稿，以及被隐藏的（管理者已经在上面的列表里看到了）
        List<Post> minePending = List.of();
        long myCount = 0;
        if (userId != null) {
            myCount = postMapper.selectCount(new LambdaQueryWrapper<Post>()
                    .eq(Post::getKind, PostKind.SOLUTION.name())
                    .eq(Post::getProblemId, problemId)
                    .eq(Post::getAuthorUserId, userId));
            if (pageNo == 1 && myCount > 0) {
                minePending = postMapper.selectList(new LambdaQueryWrapper<Post>()
                                .eq(Post::getKind, PostKind.SOLUTION.name())
                                .eq(Post::getProblemId, problemId)
                                .eq(Post::getAuthorUserId, userId)
                                .orderByDesc(Post::getUpdatedAt))
                        .stream()
                        .filter(p -> !PostStatus.PUBLISHED.name().equals(p.getStatus())
                                || (p.getHiddenAt() != null && !canManage))
                        .toList();
            }
        }

        boolean accepted = userId != null && hasAccepted(userId, problemId);
        List<Post> all = new ArrayList<>(minePending);
        all.addAll(items);
        Map<Long, CommentVO.Author> authors = authorsOf(all);
        Map<Long, Long> comments = commentService.countVisible(CommentTargetType.POST,
                all.stream().map(Post::getId).toList());
        return new SolutionPageVO(thread.problem().getPid(), thread.problem().getTitle(),
                accepted && myCount < MAX_PER_PROBLEM, accepted, canManage, myCount, MAX_PER_PROBLEM,
                pageNo, pageSize, page.getTotal(),
                minePending.stream().map(p -> item(p, userId, authors, comments)).toList(),
                items.stream().map(p -> item(p, userId, authors, comments)).toList());
    }

    public PostDetailVO get(Long postId, Long contestId) {
        Long userId = AuthContexts.userIdOrNull();
        Post post = loadSolution(postId);
        CommentService.Thread thread = commentService.resolveProblemById(userId, post.getProblemId());
        if (!CommentService.canSeePost(userId, post, thread.canManage())) {
            throw new NotFoundException("题解不存在");
        }
        return detail(post, userId, thread, contestId);
    }

    // ---------------------------------------------------------------- 写

    @Transactional(rollbackFor = Exception.class)
    public PostDetailVO create(SolutionSaveDTO dto) {
        Long userId = AuthContexts.requiredUserId();
        CommentService.Thread thread = commentService.resolve(userId, "PROBLEM", dto.pid(), dto.contestId(), null);
        Long problemId = thread.targetId();
        if (!hasAccepted(userId, problemId)) {
            throw new ForbiddenException("AC 这道题之后才能发题解");
        }
        Long mine = postMapper.selectCount(new LambdaQueryWrapper<Post>()
                .eq(Post::getKind, PostKind.SOLUTION.name())
                .eq(Post::getProblemId, problemId)
                .eq(Post::getAuthorUserId, userId));
        if (mine != null && mine >= MAX_PER_PROBLEM) {
            throw new BadRequestException("每道题最多写 " + MAX_PER_PROBLEM + " 篇题解");
        }
        String status = parseStatus(dto.status());
        String content = checkedContent(dto.content(), status);
        Post post = new Post()
                .setKind(PostKind.SOLUTION.name())
                .setAuthorUserId(userId)
                .setProblemId(problemId)
                .setContestId(thread.contest() == null ? null : thread.contest().getId())
                .setTitle(dto.title().strip())
                .setContent(content)
                .setStatus(status)
                .setPublishedAt(PostStatus.PUBLISHED.name().equals(status) ? LocalDateTime.now() : null)
                .setFeatured(false)
                .setAllowComment(true)
                .setScore(0)
                .setCommentCount(0);
        postMapper.insert(post);
        return detail(postMapper.selectById(post.getId()), userId, thread, dto.contestId());
    }

    @Transactional(rollbackFor = Exception.class)
    public PostDetailVO update(Long postId, SolutionSaveDTO dto) {
        Long userId = AuthContexts.requiredUserId();
        Post post = loadSolution(postId);
        if (!userId.equals(post.getAuthorUserId())) {
            throw new ForbiddenException("只能修改自己的题解");
        }
        CommentService.Thread thread = commentService.resolveProblemById(userId, post.getProblemId());
        String status = parseStatus(dto.status());
        String content = checkedContent(dto.content(), status);
        LambdaUpdateWrapper<Post> update = new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, post.getId())
                .set(Post::getTitle, dto.title().strip())
                .set(Post::getContent, content)
                .set(Post::getStatus, status);
        if (PostStatus.PUBLISHED.name().equals(status) && post.getPublishedAt() == null) {
            update.set(Post::getPublishedAt, LocalDateTime.now());
        }
        postMapper.update(null, update);
        return detail(postMapper.selectById(post.getId()), userId, thread, dto.contestId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long postId) {
        Long userId = AuthContexts.requiredUserId();
        Post post = loadSolution(postId);
        CommentService.Thread thread = commentService.resolveProblemById(userId, post.getProblemId());
        if (!userId.equals(post.getAuthorUserId()) && !thread.canManage()) {
            throw new ForbiddenException("只能删除自己的题解");
        }
        postMapper.deleteById(post.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public PostDetailVO moderate(Long postId, PostModerationDTO dto, Long contestId) {
        Long userId = AuthContexts.requiredUserId();
        Post post = loadSolution(postId);
        CommentService.Thread thread = commentService.resolveProblemById(userId, post.getProblemId());
        if (!thread.canManage()) {
            throw new ForbiddenException("只有管理者能精选或隐藏题解");
        }
        if (!PostStatus.PUBLISHED.name().equals(post.getStatus())) {
            throw new BadRequestException("草稿不能精选或隐藏");
        }
        LambdaUpdateWrapper<Post> update = new LambdaUpdateWrapper<Post>().eq(Post::getId, post.getId());
        boolean changed = false;
        boolean hiding = Boolean.TRUE.equals(dto.hidden());
        if (dto.hidden() != null) {
            String reason = StringUtils.hasText(dto.reason()) ? dto.reason().strip() : null;
            update.set(Post::getHiddenAt, hiding ? LocalDateTime.now() : null)
                    .set(Post::getHiddenBy, hiding ? userId : null)
                    .set(Post::getHiddenReason, hiding ? reason : null);
            changed = true;
        }
        if (hiding) {
            // 隐藏的题解不再算精选
            update.set(Post::getFeatured, false);
        } else if (dto.featured() != null) {
            if (Boolean.TRUE.equals(dto.featured()) && post.getHiddenAt() != null && dto.hidden() == null) {
                throw new BadRequestException("被隐藏的题解不能精选");
            }
            update.set(Post::getFeatured, Boolean.TRUE.equals(dto.featured()));
            changed = true;
        }
        if (changed) {
            postMapper.update(null, update);
        }
        return detail(postMapper.selectById(post.getId()), userId, thread, contestId);
    }

    // ---------------------------------------------------------------- 内部

    /** AC 过这道题：有一条 Accepted 且没被跳过的提交（比赛、作业、题库都算；全部测试点通过，IOI 即满分） */
    boolean hasAccepted(Long userId, Long problemId) {
        Long n = submissionMapper.selectCount(new LambdaQueryWrapper<Submission>()
                .eq(Submission::getUserId, userId)
                .eq(Submission::getProblemId, problemId)
                .eq(Submission::getVerdict, SubmissionVerdict.ACCEPTED.getVerdict())
                .and(w -> w.isNull(Submission::getIsSkipped).or().eq(Submission::getIsSkipped, false)));
        return n != null && n > 0;
    }

    private Post loadSolution(Long postId) {
        Post post = postId == null ? null : postMapper.selectById(postId);
        if (post == null || !PostKind.SOLUTION.name().equals(post.getKind())) {
            throw new NotFoundException("题解不存在");
        }
        return post;
    }

    private static String parseStatus(String raw) {
        String s = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (PostStatus.DRAFT.name().equals(s) || PostStatus.PUBLISHED.name().equals(s)) {
            return s;
        }
        throw new BadRequestException("status 只能是 DRAFT 或 PUBLISHED");
    }

    private static String checkedContent(String raw, String status) {
        String content = raw == null ? "" : raw;
        if (PostStatus.PUBLISHED.name().equals(status) && content.isBlank()) {
            throw new BadRequestException("正文不能为空");
        }
        return content;
    }

    private PostDetailVO detail(Post post, Long userId, CommentService.Thread thread, Long contestId) {
        boolean mine = userId != null && userId.equals(post.getAuthorUserId());
        boolean canManage = thread.canManage();
        Vote vote = userId == null ? null : voteMapper.selectOne(new LambdaQueryWrapper<Vote>()
                .eq(Vote::getTargetType, "POST")
                .eq(Vote::getTargetId, post.getId())
                .eq(Vote::getUserId, userId));
        long comments = commentService.countVisible(CommentTargetType.POST, List.of(post.getId()))
                .getOrDefault(post.getId(), 0L);
        CommentVO.Author author = authorsOf(List.of(post)).get(post.getAuthorUserId());
        boolean hidden = post.getHiddenAt() != null;
        return new PostDetailVO(post.getId(), post.getKind(), post.getTitle(), post.getContent(), author,
                post.getStatus(), Boolean.TRUE.equals(post.getFeatured()), hidden,
                hidden && (mine || canManage) ? post.getHiddenReason() : null,
                post.getScore() == null ? 0 : post.getScore(),
                vote == null || vote.getValue() == null ? 0 : vote.getValue(), comments,
                !Boolean.FALSE.equals(post.getAllowComment()), mine, canManage,
                thread.problem() == null ? null : thread.problem().getPid(),
                thread.problem() == null ? null : thread.problem().getTitle(),
                contestId, post.getPublishedAt(), post.getUpdatedAt());
    }

    private SolutionPageVO.Item item(Post p, Long userId, Map<Long, CommentVO.Author> authors, Map<Long, Long> comments) {
        Long n = comments.get(p.getId());
        return new SolutionPageVO.Item(p.getId(), p.getTitle(), authors.get(p.getAuthorUserId()),
                p.getScore() == null ? 0 : p.getScore(), n == null ? 0 : n, Boolean.TRUE.equals(p.getFeatured()),
                p.getStatus(), p.getHiddenAt() != null, userId != null && userId.equals(p.getAuthorUserId()),
                p.getPublishedAt(), p.getUpdatedAt());
    }

    private Map<Long, CommentVO.Author> authorsOf(List<Post> posts) {
        List<Long> ids = posts.stream().map(Post::getAuthorUserId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userInfoMapper.selectByIds(ids).stream().collect(Collectors.toMap(UserInfo::getId,
                u -> new CommentVO.Author(u.getId(), u.getUsername(), u.getNickname()), (a, b) -> a));
    }
}
