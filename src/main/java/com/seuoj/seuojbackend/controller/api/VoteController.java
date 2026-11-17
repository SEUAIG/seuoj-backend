package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.dto.discussion.VoteDTO;
import com.seuoj.seuojbackend.service.VoteService;
import com.seuoj.seuojbackend.vo.discussion.VoteResultVO;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 赞踩：value 1 / -1 / 0（取消），幂等 */
@RestController
@Validated
@RequestMapping("/api/votes")
public class VoteController {

    private final VoteService voteService;

    public VoteController(VoteService voteService) {
        this.voteService = voteService;
    }

    @PutMapping
    public Result<VoteResultVO> vote(@Valid @RequestBody VoteDTO dto) {
        return Result.success(voteService.vote(dto));
    }
}
