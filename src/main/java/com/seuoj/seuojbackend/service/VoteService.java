package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seuoj.seuojbackend.common.PostKind;
import com.seuoj.seuojbackend.common.PostStatus;
import com.seuoj.seuojbackend.dto.discussion.VoteDTO;
import com.seuoj.seuojbackend.entity.Comment;
import com.seuoj.seuojbackend.entity.Post;
import com.seuoj.seuojbackend.entity.Vote;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ConflictException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.mapper.CommentMapper;
import com.seuoj.seuojbackend.mapper.PostMapper;
import com.seuoj.seuojbackend.mapper.VoteMapper;
import com.seuoj.seuojbackend.vo.discussion.VoteResultVO;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 赞踩：发言（COMMENT）和用户题解（POST）。 */
@Service
public class VoteService {

    static final String COMMENT = "COMMENT";
    static final String POST = "POST";
    static final int VOTES_PER_MINUTE = 30;

    private final VoteMapper voteMapper;
    private final CommentMapper commentMapper;
    private final PostMapper postMapper;
    private final CommentService commentService;

    public VoteService(VoteMapper voteMapper, CommentMapper commentMapper, PostMapper postMapper,
                       CommentService commentService) {
        this.voteMapper = voteMapper;
        this.commentMapper = commentMapper;
        this.postMapper = postMapper;
        this.commentService = commentService;
    }

    @Transactional(rollbackFor = Exception.class)
    public VoteResultVO vote(VoteDTO dto) {
        Long userId = AuthContexts.requiredUserId();
        String type = dto.targetType() == null ? "" : dto.targetType().trim().toUpperCase(Locale.ROOT);
        int value = dto.value() == null ? 0 : dto.value();
        if (value < -1 || value > 1) {
            throw new BadRequestException("value 只能是 1、-1 或 0");
        }
        Long targetId = dto.targetId();
        if (COMMENT.equals(type)) {
            Comment comment = targetId == null ? null : commentMapper.selectById(targetId);
            if (comment == null) {
                throw new NotFoundException("发言不存在");
            }
            if (comment.getDeletedAt() != null || comment.getHiddenAt() != null) {
                throw new BadRequestException("这条发言已删除或被隐藏");
            }
            commentService.assertReadable(userId, comment);
        } else if (POST.equals(type)) {
            Post post = targetId == null ? null : postMapper.selectById(targetId);
            if (post == null || !PostKind.SOLUTION.name().equals(post.getKind())) {
                throw new NotFoundException("题解不存在");
            }
            if (!PostStatus.PUBLISHED.name().equals(post.getStatus()) || post.getHiddenAt() != null) {
                throw new BadRequestException("这篇题解还没公开或被隐藏，不能投票");
            }
            commentService.resolveProblemById(userId, post.getProblemId());
        } else {
            throw new BadRequestException("target_type 只能是 COMMENT 或 POST");
        }

        // 锁住这一行再改：同一个人连点时按顺序处理，净分不会算重
        Vote existing = voteMapper.selectForUpdate(type, targetId, userId);
        int old = existing == null || existing.getValue() == null ? 0 : existing.getValue();
        if (old != value) {
            Long recent = voteMapper.selectCount(new LambdaQueryWrapper<Vote>()
                    .eq(Vote::getUserId, userId)
                    .gt(Vote::getUpdatedAt, LocalDateTime.now().minusMinutes(1)));
            if (recent != null && recent >= VOTES_PER_MINUTE) {
                throw new BadRequestException("投票太频繁了，请过一会儿再试");
            }
            if (existing == null) {
                try {
                    voteMapper.insert(new Vote().setTargetType(type).setTargetId(targetId)
                            .setUserId(userId).setValue(value));
                } catch (DuplicateKeyException e) {
                    throw new ConflictException("投票冲突，请刷新后再试");
                }
            } else {
                voteMapper.updateById(new Vote().setId(existing.getId()).setValue(value));
            }
            if (COMMENT.equals(type)) {
                commentMapper.addScore(targetId, value - old);
            } else {
                postMapper.addScore(targetId, value - old);
            }
        }
        Integer score;
        if (COMMENT.equals(type)) {
            Comment fresh = commentMapper.selectById(targetId);
            score = fresh == null ? null : fresh.getScore();
        } else {
            Post fresh = postMapper.selectById(targetId);
            score = fresh == null ? null : fresh.getScore();
        }
        return new VoteResultVO(score == null ? 0 : score, value);
    }
}
