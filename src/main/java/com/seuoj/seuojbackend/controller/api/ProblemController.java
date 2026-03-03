package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.annotation.AllowAnonymous;
import com.seuoj.seuojbackend.annotation.RequireRole;
import com.seuoj.seuojbackend.client.dto.JudgeProblemDataResponse;
import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.common.RoleType;
import com.seuoj.seuojbackend.dto.problem.ProblemEditDTO;
import com.seuoj.seuojbackend.service.ProblemService;
import com.seuoj.seuojbackend.service.ProblemTestcaseService;
import com.seuoj.seuojbackend.vo.problem.ProblemDetailVO;
import com.seuoj.seuojbackend.vo.problem.ProblemPageVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/problem")
public class ProblemController {
    private final ProblemService problemService;
    private final ProblemTestcaseService problemTestcaseService;

    public ProblemController(ProblemService problemService, ProblemTestcaseService problemTestcaseService) {
        this.problemService = problemService;
        this.problemTestcaseService = problemTestcaseService;
    }

    /**
     * 分页查询题目列表
     */
    @AllowAnonymous
    @GetMapping("/page")
    public Result<ProblemPageVO> getProblemPage(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为1") Integer current,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "每页条数最小为1")
            @Max(value = 100, message = "每页条数最大为100") Integer size,
            @RequestParam(required = false) @Size(max = 100, message = "标题长度不能超过100") String title,
            @RequestParam(value = "tag_ids", required = false)
            @Size(max = 50, message = "标签数量不能超过50") List<Long> tagIds) {
        return Result.success(problemService.getProblemPage(current, size, title, tagIds));
    }

    @AllowAnonymous
    @GetMapping("/{pid}")
    public Result<ProblemDetailVO> getProblemDetail(@PathVariable String pid) {
        return Result.success(problemService.getProblemDetail(pid));
    }

    @RequireRole({RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @PatchMapping("/edit")
    public Result<Void> editProblem(@Valid @RequestBody ProblemEditDTO dto) {
        problemService.editProblem(dto);
        return Result.success();
    }

    /**
     * 上传题目测试数据
     * 后端仅负责鉴权，通过 nginx 重定向到评测端
     */
    @RequireRole({RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @PostMapping("/testcases/{pid}")
    public void uploadProblemTestcases(@PathVariable String pid,
            HttpServletResponse response) {
        problemTestcaseService.redirectTestcaseUpload(pid, response);
    }

    /**
     * 获取题目配置
     * 后端仅负责鉴权，通过 nginx 重定向到评测端获取配置
     *
     * @param pid  题目编号
     * @param type 配置类型：META（元数据）或 CASE（测试点配置）
     */
    @RequireRole({RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @GetMapping("/config/{pid}")
    public void getProblemConfig(@PathVariable String pid,
                                @RequestParam("type") String type,
                                HttpServletResponse response) {
        problemTestcaseService.redirectProblemConfig(pid, type, response);
    }

    /**
     * 直接修改题目配置文件
     * 后端不做内容处理，内容校验由评测端完成，上传内容采用全量覆盖
     *
     * @param pid  题目编号
     * @param type 配置类型：META（元数据）或 CASE（测试点配置）
     */
    @RequireRole({RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @PutMapping("/config/{pid}")
    public void updateProblemConfig(@PathVariable String pid,
                                   @RequestParam("type") String type,
                                   HttpServletResponse response) {
        problemTestcaseService.redirectProblemConfig(pid, type, response);
    }

    @RequireRole({RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @GetMapping("/data/{pid}")
    public Result<JudgeProblemDataResponse> getProblemData(@PathVariable String pid) {
        return Result.success(problemTestcaseService.getProblemTestcaseMeta(pid));
    }

    /**
     * 获取题目文件（代理到评测端）
     * <p>
     * 开发环境：后端直接代理请求到评测端，通过 RestTemplate 获取字节流后写入响应。
     * 生产环境：可改用 Nginx X-Accel-Redirect 由 Nginx 内部转发，减轻后端 IO 负担。
     * <p>
     * file_name 支持子目录路径（如 subtask1/1.in），但不允许向外遍历
     *
     * @param pid      题目编号
     * @param fileName 文件名（可含子目录）
     */
    @RequireRole({RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @GetMapping("/file/{pid}/{*file_name}")
    public void getProblemFile(@PathVariable String pid,
            @PathVariable("file_name") String fileName,
            HttpServletResponse response) {
        problemTestcaseService.proxyProblemFile(pid, fileName, response);
    }

    /**
     * 获取题目文件树（代理到评测端）
     * <p>
     * 开发环境：后端直接代理请求到评测端，通过 RestTemplate 获取 JSON 后返回。
     * 生产环境：可改用 Nginx X-Accel-Redirect 由 Nginx 内部转发。
     *
     * @param pid 题目编号
     */
    @RequireRole({RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @GetMapping("/tree/{pid}")
    public Result<Object> getProblemTree(@PathVariable String pid) {
        return Result.success(problemTestcaseService.getProblemTree(pid));
    }
}