package com.seuoj.seuojbackend.controller.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seuoj.seuojbackend.annotation.AllowAnonymous;
import com.seuoj.seuojbackend.annotation.RequireRole;
import com.seuoj.seuojbackend.client.dto.JudgeProblemDataResponse;
import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.common.RoleType;
import com.seuoj.seuojbackend.dto.problem.ProblemCreateDTO;
import com.seuoj.seuojbackend.dto.problem.ProblemDetailQuery;
import com.seuoj.seuojbackend.dto.problem.ProblemEditDTO;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.service.ProblemService;
import com.seuoj.seuojbackend.service.ProblemTestcaseService;
import com.seuoj.seuojbackend.vo.problem.ProblemCreateVO;
import com.seuoj.seuojbackend.vo.problem.ProblemDetailVO;
import com.seuoj.seuojbackend.vo.problem.ProblemPageVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/problem")
public class ProblemController {
    private final ProblemService problemService;
    private final ProblemTestcaseService problemTestcaseService;
    private final ProblemMapper problemMapper;

    public ProblemController(ProblemService problemService, ProblemTestcaseService problemTestcaseService,
                             ProblemMapper problemMapper) {
        this.problemService = problemService;
        this.problemTestcaseService = problemTestcaseService;
        this.problemMapper = problemMapper;
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

    /**
     * 查看某题详情
     */
    @AllowAnonymous
    @GetMapping("/{pid}")
    public Result<ProblemDetailVO> getProblemDetail(@PathVariable String pid,
                                                    @RequestParam(value = "contest_public_id", required = false) String contestPublicId,
                                                    @RequestParam(value = "problem_set_public_id", required = false) String problemSetPublicId) {
        return Result.success(problemService.getProblemDetail(
                ProblemDetailQuery.fromRequest(pid, contestPublicId, problemSetPublicId)
        ));
    }

    /**
     * 新建题面
     */
    @RequireRole({RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @PostMapping
    public Result<ProblemCreateVO> createProblem(@Valid @RequestBody ProblemCreateDTO dto) {
        return Result.success(problemService.createProblem(dto));
    }

    /**
     * 编辑题目信息
     */
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
     * 获取题目数据配置
     * 后端仅负责鉴权，通过 nginx 重定向到评测端获取配置
     *
     * @param pid 题目编号
     */
    @RequireRole({RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @GetMapping("/config/{pid}")
    public void getProblemConfig(@PathVariable String pid,
                                 HttpServletResponse response) {
        validateProblemExists(pid);
        response.setHeader("X-Accel-Redirect", "/internal/judgend/judge/problem/config/" + pid);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * 直接修改题目配置文件
     * 后端不做内容处理，内容校验由评测端完成，上传内容采用全量覆盖
     *
     * @param pid 题目编号
     */
    @RequireRole({RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @PutMapping("/config/{pid}")
    public void updateProblemConfig(@PathVariable String pid,
                                    HttpServletResponse response) {
        validateProblemExists(pid);
        response.setHeader("X-Accel-Redirect", "/internal/judgend/judge/problem/config/" + pid);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * 获取题目测试点元数据（已废弃）
     */
    @Deprecated
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

    /**
     * 删除题目
     */
    @RequireRole({RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @DeleteMapping("/{pid}")
    public Result<Void> deleteProblem(@PathVariable String pid) {
        problemService.deleteProblem(pid);
        return Result.success();
    }

    /**
     * 删除题目文件
     */
    @RequireRole({RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @DeleteMapping("/file/{pid}/{*file_name}")
    public void deleteProblemFile(@PathVariable String pid, @PathVariable("file_name") String fileName,
                                  HttpServletResponse response) {
        validateProblemExists(pid);
        response.setHeader("X-Accel-Redirect", "/internal/judgend/judge/problem/file/" + pid + "/" + fileName);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void validateProblemExists(String pid) {
        Problem problem = problemMapper.selectOne(new LambdaQueryWrapper<Problem>()
                .eq(Problem::getPid, pid));
        if (problem == null) {
            throw new NotFoundException("题目不存在");
        }
    }
}
