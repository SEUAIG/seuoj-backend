package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.dto.contest.ContestBalloonColorDTO;
import com.seuoj.seuojbackend.service.ContestBalloonService;
import com.seuoj.seuojbackend.vo.contest.ContestBalloonItemVO;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 比赛气球：队列查看、送达、题目配色。权限复用比赛的 WRITE 权限。 */
@RestController
@Validated
@RequestMapping("/api/contest")
public class ContestBalloonController {

    private final ContestBalloonService balloonService;

    public ContestBalloonController(ContestBalloonService balloonService) {
        this.balloonService = balloonService;
    }

    @GetMapping("/{id}/balloon/list")
    public Result<List<ContestBalloonItemVO>> listBalloons(@PathVariable("id") Long id) {
        return Result.success(balloonService.listQueue(id));
    }

    @PostMapping("/{id}/balloon/{balloonId}/deliver")
    public Result<Void> deliverBalloon(@PathVariable("id") Long id,
                                       @PathVariable("balloonId") Long balloonId) {
        balloonService.deliver(id, balloonId);
        return Result.success();
    }

    @PutMapping("/{id}/balloon/color")
    public Result<Void> setBalloonColor(@PathVariable("id") Long id,
                                        @RequestBody(required = false) ContestBalloonColorDTO dto) {
        balloonService.setProblemColor(id, dto == null ? null : dto.pid(), dto == null ? null : dto.color());
        return Result.success();
    }
}
