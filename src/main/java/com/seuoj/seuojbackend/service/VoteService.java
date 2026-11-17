package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seuoj.seuojbackend.dto.discussion.VoteDTO;
import com.seuoj.seuojbackend.entity.Comment;
import com.seuoj.seuojbackend.entity.Vote;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ConflictException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.mapper.CommentMapper;
import com.seuoj.seuojbackend.mapper.VoteMapper;
import com.seuoj.seuojbackend.vo.discussion.VoteResultVO;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 赞踩。1b-1 只对发言开放；帖子（题解）在 1b-2 接入。 */
@Service
public class VoteService {

    static final String COMMENT = "COMMENT";
    static final int VOTES_PER_MINUTE = 30;

    private final VoteMapper voteMapper;
    private final CommentMapper commentMapper;
    private final CommentService commentService;

    public VoteService(VoteMapper voteMapper, CommentMapper commentMapper, CommentService commentService) {
        this.voteMapper = voteMapper;
        this.commentMapper = commentMapper;
        this.commentService = commentService;
    }

    @Transactional(rollbackFor = Exception.class)
    public VoteResultVO vote(VoteDTO dto) {
        Long userId = AuthContexts.requiredUserId();
        String type = dto.targetType() == null ? "" : dto.targetType().trim().toUpperCase(Locale.ROOT);
        if (!COMMENT.equals(type)) {
            throw new BadRequestException("目前只能给发言投票");
        }
        int value = dto.value() == null ? 0 : dto.value();
        if (value < -1 || value > 1) {
            throw new BadRequestException("value 只能是 1、-1 或 0");
        }
        Comment comment = dto.targetId() == null ? null : commentMapper.selectById(dto.targetId());
        if (comment == null) {
            throw new NotFoundException("发言不存在");
        }
        if (comment.getDeletedAt() != null || comment.getHiddenAt() != null) {
            throw new BadRequestException("这条发言已删除或被隐藏");
        }
        commentService.assertReadable(userId, comment);

        // 锁住这一行再改：同一个人连点时按顺序处理，净分不会算重
        Vote existing = voteMapper.selectForUpdate(COMMENT, comment.getId(), userId);
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
                    voteMapper.insert(new Vote().setTargetType(COMMENT).setTargetId(comment.getId())
                            .setUserId(userId).setValue(value));
                } catch (DuplicateKeyException e) {
                    throw new ConflictException("投票冲突，请刷新后再试");
                }
            } else {
                voteMapper.updateById(new Vote().setId(existing.getId()).setValue(value));
            }
            commentMapper.addScore(comment.getId(), value - old);
        }
        Comment fresh = commentMapper.selectById(comment.getId());
        return new VoteResultVO(fresh == null || fresh.getScore() == null ? 0 : fresh.getScore(), value);
    }
}
