package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.annotation.RequireRole;
import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.common.RoleType;
import com.seuoj.seuojbackend.service.SubmissionService;
import com.seuoj.seuojbackend.vo.me.MeHeatmapVO;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final SubmissionService submissionService;

    public MeController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @RequireRole({RoleType.USER})
    @GetMapping("/heatmap")
    public Result<MeHeatmapVO> heatmap(@RequestParam
                                       @Pattern(regexp = "^\\d{4}$", message = "年份格式必须为4位数字") String year) {
        return Result.success(submissionService.getHeatmap(year));
    }
}
