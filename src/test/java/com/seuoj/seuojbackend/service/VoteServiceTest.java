package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.seuoj.seuojbackend.common.AuthStatus;
import com.seuoj.seuojbackend.dto.discussion.VoteDTO;
import com.seuoj.seuojbackend.entity.Comment;
import com.seuoj.seuojbackend.entity.Post;
import com.seuoj.seuojbackend.entity.Vote;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.interceptor.UserContext;
import com.seuoj.seuojbackend.interceptor.UserContextHolder;
import com.seuoj.seuojbackend.mapper.CommentMapper;
import com.seuoj.seuojbackend.mapper.PostMapper;
import com.seuoj.seuojbackend.mapper.VoteMapper;
import com.seuoj.seuojbackend.vo.discussion.VoteResultVO;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VoteServiceTest {
    private static final long ALICE = 2L, COMMENT_ID = 20L;

    private final VoteMapper votes = mock(VoteMapper.class);
    private final CommentMapper comments = mock(CommentMapper.class);
    private final CommentService commentService = mock(CommentService.class);
    private final PostMapper posts = mock(PostMapper.class);
    private final VoteService service = new VoteService(votes, comments, posts, commentService);

    private final Comment comment = new Comment().setId(COMMENT_ID).setTargetType("PROBLEM").setTargetId(11L).setScore(4);

    @BeforeAll
    static void initTableInfo() {
        var config = new MybatisConfiguration();
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(config, "vote");
        assistant.setCurrentNamespace("vote");
        TableInfoHelper.initTableInfo(assistant, Vote.class);
    }

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.of(ALICE, AuthStatus.AUTHENTICATED));
        when(comments.selectById(COMMENT_ID)).thenReturn(comment);
        when(votes.selectCount(any())).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void firstUpvoteInsertsAndAddsOne() {
        VoteResultVO vo = service.vote(new VoteDTO("COMMENT", COMMENT_ID, 1));

        ArgumentCaptor<Vote> saved = ArgumentCaptor.forClass(Vote.class);
        verify(votes).insert(saved.capture());
        assertEquals(1, saved.getValue().getValue());
        assertEquals(ALICE, saved.getValue().getUserId());
        verify(comments).addScore(COMMENT_ID, 1);
        assertEquals(1, vo.myVote());
    }

    @Test
    void switchingFromUpToDownMovesScoreByTwo() {
        when(votes.selectForUpdate("COMMENT", COMMENT_ID, ALICE)).thenReturn(new Vote().setId(7L).setValue(1));

        service.vote(new VoteDTO("COMMENT", COMMENT_ID, -1));

        verify(votes).updateById(any(Vote.class));
        verify(comments).addScore(COMMENT_ID, -2);
    }

    @Test
    void repeatingTheSameVoteChangesNothing() {
        when(votes.selectForUpdate("COMMENT", COMMENT_ID, ALICE)).thenReturn(new Vote().setId(7L).setValue(1));

        VoteResultVO vo = service.vote(new VoteDTO("COMMENT", COMMENT_ID, 1));

        verify(votes, never()).updateById(any(Vote.class));
        verify(votes, never()).insert(any(Vote.class));
        verify(comments, never()).addScore(anyLong(), anyInt());
        assertEquals(4, vo.score());
    }

    @Test
    void cancellingWithoutExistingVoteIsNoop() {
        service.vote(new VoteDTO("COMMENT", COMMENT_ID, 0));
        verify(votes, never()).insert(any(Vote.class));
        verify(comments, never()).addScore(anyLong(), anyInt());
    }

    @Test
    void votingTooFastIsRejected() {
        when(votes.selectCount(any())).thenReturn((long) VoteService.VOTES_PER_MINUTE);
        assertThrows(BadRequestException.class, () -> service.vote(new VoteDTO("COMMENT", COMMENT_ID, 1)));
        verify(comments, never()).addScore(anyLong(), anyInt());
    }

    @Test
    void hiddenCommentCannotBeVoted() {
        comment.setHiddenAt(LocalDateTime.now());
        assertThrows(BadRequestException.class, () -> service.vote(new VoteDTO("COMMENT", COMMENT_ID, 1)));
    }

    @Test
    void unreadableThreadIsRejected() {
        doThrow(new ForbiddenException("无权查看")).when(commentService).assertReadable(ALICE, comment);
        assertThrows(ForbiddenException.class, () -> service.vote(new VoteDTO("COMMENT", COMMENT_ID, 1)));
        verify(votes, never()).insert(any(Vote.class));
    }

    @Test
    void upvotingPublishedSolutionMovesPostScore() {
        when(posts.selectById(40L)).thenReturn(new Post().setId(40L).setKind("SOLUTION").setProblemId(11L)
                .setStatus("PUBLISHED").setScore(2));

        service.vote(new VoteDTO("POST", 40L, 1));

        verify(commentService).resolveProblemById(ALICE, 11L);
        verify(posts).addScore(40L, 1);
        verify(comments, never()).addScore(anyLong(), anyInt());
    }

    @Test
    void draftOrHiddenSolutionCannotBeVoted() {
        when(posts.selectById(40L)).thenReturn(new Post().setId(40L).setKind("SOLUTION").setProblemId(11L).setStatus("DRAFT"));
        assertThrows(BadRequestException.class, () -> service.vote(new VoteDTO("POST", 40L, 1)));
        when(posts.selectById(41L)).thenReturn(new Post().setId(41L).setKind("SOLUTION").setProblemId(11L)
                .setStatus("PUBLISHED").setHiddenAt(LocalDateTime.now()));
        assertThrows(BadRequestException.class, () -> service.vote(new VoteDTO("POST", 41L, 1)));
    }

    @Test
    void editorialCannotBeVoted() {
        when(posts.selectById(50L)).thenReturn(new Post().setId(50L).setKind("EDITORIAL").setStatus("PUBLISHED"));
        assertThrows(com.seuoj.seuojbackend.exception.NotFoundException.class,
                () -> service.vote(new VoteDTO("POST", 50L, 1)));
    }

    @Test
    void unknownTargetTypeIsRejected() {
        assertThrows(BadRequestException.class, () -> service.vote(new VoteDTO("BLOG", 1L, 1)));
    }
}
