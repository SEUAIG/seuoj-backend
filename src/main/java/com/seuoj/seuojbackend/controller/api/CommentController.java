package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.annotation.AllowAnonymous;
import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.dto.discussion.CommentCreateDTO;
import com.seuoj.seuojbackend.dto.discussion.CommentEditDTO;
import com.seuoj.seuojbackend.dto.discussion.CommentModerationDTO;
import com.seuoj.seuojbackend.service.CommentService;
import com.seuoj.seuojbackend.vo.discussion.CommentRevisionsVO;
import com.seuoj.seuojbackend.vo.discussion.CommentSummaryVO;
import com.seuoj.seuojbackend.vo.discussion.CommentThreadVO;
import com.seuoj.seuojbackend.vo.discussion.CommentVO;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 讨论 thread：题目讨论（PROBLEM，pid）、整场比赛讨论（CONTEST，contest_id）、题解评论区（POST，post_id） */
@RestController
@Validated
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @AllowAnonymous
    @GetMapping
    public Result<CommentThreadVO> getThread(@RequestParam("target_type") String targetType,
                                             @RequestParam(value = "pid", required = false) String pid,
                                             @RequestParam(value = "contest_id", required = false) Long contestId,
                                             @RequestParam(value = "post_id", required = false) Long postId,
                                             @RequestParam(value = "sort", required = false) String sort,
                                             @RequestParam(value = "current", required = false) Long current,
                                             @RequestParam(value = "size", required = false) Long size) {
        return Result.success(commentService.getThread(targetType, pid, contestId, postId, sort, current, size));
    }

    /** 题目页「💭 讨论」入口：能不能进、有几条 */
    @AllowAnonymous
    @GetMapping("/summary")
    public Result<CommentSummaryVO> getSummary(@RequestParam("target_type") String targetType,
                                               @RequestParam(value = "pid", required = false) String pid,
                                               @RequestParam(value = "contest_id", required = false) Long contestId) {
        return Result.success(commentService.getSummary(targetType, pid, contestId));
    }

    @AllowAnonymous
    @GetMapping("/{id}/subtree")
    public Result<CommentThreadVO> getSubtree(@PathVariable("id") Long id,
                                              @RequestParam(value = "contest_id", required = false) Long contestId) {
        return Result.success(commentService.getSubtree(id, contestId));
    }

    @PostMapping
    public Result<CommentVO> create(@Valid @RequestBody CommentCreateDTO dto) {
        return Result.success(commentService.create(dto));
    }

    /** 修改自己的发言，旧正文进历史 */
    @PutMapping("/{id}")
    public Result<CommentVO> edit(@PathVariable("id") Long id, @Valid @RequestBody CommentEditDTO dto) {
        return Result.success(commentService.edit(id, dto));
    }

    /** 历史版本：能看这条发言的人都能看 */
    @AllowAnonymous
    @GetMapping("/{id}/revisions")
    public Result<CommentRevisionsVO> revisions(@PathVariable("id") Long id) {
        return Result.success(commentService.getRevisions(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        commentService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/moderation")
    public Result<CommentVO> moderate(@PathVariable("id") Long id, @Valid @RequestBody CommentModerationDTO dto) {
        return Result.success(commentService.moderate(id, dto));
    }
}
