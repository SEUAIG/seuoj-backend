package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.annotation.AllowAnonymous;
import com.seuoj.seuojbackend.annotation.RequireRole;
import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.common.RoleType;
import com.seuoj.seuojbackend.dto.classinfo.ClassBatchImportDTO;
import com.seuoj.seuojbackend.dto.classinfo.ClassCreateDTO;
import com.seuoj.seuojbackend.dto.classinfo.ClassUpdateDTO;
import com.seuoj.seuojbackend.service.ClassService;
import com.seuoj.seuojbackend.vo.classinfo.ClassBatchImportResultVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassCreateVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassItemVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassMemberPageVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassOverviewVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassPageVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassProblemSetMatrixVO;
import com.seuoj.seuojbackend.vo.classinfo.LinkPageVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

@RestController
@Validated
@RequestMapping("/api/class")
public class ClassController {

    private final ClassService classService;

    public ClassController(ClassService classService) {
        this.classService = classService;
    }

    /**
     * 创建班级
     */
    @RequireRole({ RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN })
    @PostMapping
    public Result<ClassCreateVO> createClass(@Valid @RequestBody ClassCreateDTO dto) {
        return Result.success(classService.createClass(dto));
    }

    /**
     * 分页查询班级列表
     */
    @AllowAnonymous
    @GetMapping("/page")
    public Result<ClassPageVO> getClassPage(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") Integer current,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数最小为 1") @Max(value = 100, message = "每页条数最大为 100") Integer size) {
        return Result.success(classService.getClassPage(current, size));
    }

    /**
     * 更新班级基础信息
     */
    @RequireRole({ RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN })
    @PutMapping("/{class_public_id}")
    public Result<ClassItemVO> updateClass(@PathVariable("class_public_id") String classPublicId,
            @RequestBody ClassUpdateDTO dto) {
        return Result.success(classService.updateClass(classPublicId, dto));
    }

    /**
     * 删除班级
     */
    @RequireRole({ RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN })
    @DeleteMapping("/{class_public_id}")
    public Result<Void> deleteClass(@PathVariable("class_public_id") String classPublicId) {
        classService.deleteClass(classPublicId);
        return Result.success();
    }

    /**
     * 当前用户加入班级
     */
    @RequireRole({ RoleType.USER, RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN })
    @PostMapping("/{class_public_id}/join")
    public Result<Void> joinClass(@PathVariable("class_public_id") String classPublicId) {
        classService.joinClass(classPublicId);
        return Result.success();
    }

    /**
     * 分页查询班级成员
     */
    @RequireRole({ RoleType.USER, RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN })
    @GetMapping("/{class_public_id}/member/page")
    public Result<ClassMemberPageVO> getClassMemberPage(
            @PathVariable("class_public_id") String classPublicId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") Integer current,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数最小为 1") @Max(value = 100, message = "每页条数最大为 100") Integer size) {
        return Result.success(classService.getClassMemberPage(classPublicId, current, size));
    }

    /**
     * 移除班级成员
     */
    @RequireRole({ RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN })
    @DeleteMapping("/{class_public_id}/member/{user_public_id}")
    public Result<Void> removeMember(@PathVariable("class_public_id") String classPublicId,
            @PathVariable("user_public_id") String userPublicId) {
        classService.removeMember(classPublicId, userPublicId);
        return Result.success();
    }

    /**
     * 添加班级成员
     */
    @RequireRole({ RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN })
    @PostMapping("/{class_public_id}/member/{user_public_id}")
    public Result<Void> addMember(@PathVariable("class_public_id") String classPublicId,
            @PathVariable("user_public_id") String userPublicId) {
        classService.addMember(classPublicId, userPublicId);
        return Result.success();
    }

    /**
     * 批量导入学生到班级
     */
    @RequireRole({ RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN })
    @PostMapping("/{class_public_id}/batch-import")
    public Result<ClassBatchImportResultVO> batchImportStudents(
            @PathVariable("class_public_id") String classPublicId,
            @Valid @RequestBody ClassBatchImportDTO dto) {
        return Result.success(classService.batchImportStudents(classPublicId, dto));
    }

    /**
     * 分页查询班级关联题单
     */
    @RequireRole({ RoleType.USER, RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN })
    @GetMapping("/{class_public_id}/problem_set/page")
    public Result<LinkPageVO> getClassProblemSetPage(
            @PathVariable("class_public_id") String classPublicId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") Integer current,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数最小为 1") @Max(value = 100, message = "每页条数最大为 100") Integer size) {
        return Result.success(classService.getClassProblemSetPage(classPublicId, current, size));
    }

    /**
     * 关联题单到班级
     */
    @RequireRole({ RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN })
    @PutMapping("/{class_public_id}/problem_set/{problem_set_public_id}")
    public Result<Void> linkProblemSet(@PathVariable("class_public_id") String classPublicId,
            @PathVariable("problem_set_public_id") String problemSetPublicId) {
        classService.linkProblemSet(classPublicId, problemSetPublicId);
        return Result.success();
    }

    /**
     * 取消班级与题单关联
     */
    @RequireRole({ RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN })
    @DeleteMapping("/{class_public_id}/problem_set/{problem_set_public_id}")
    public Result<Void> unlinkProblemSet(@PathVariable("class_public_id") String classPublicId,
            @PathVariable("problem_set_public_id") String problemSetPublicId) {
        classService.unlinkProblemSet(classPublicId, problemSetPublicId);
        return Result.success();
    }

    /**
     * 分页查询班级关联比赛
     */
    @RequireRole({ RoleType.USER, RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN })
    @GetMapping("/{class_public_id}/contest/page")
    public Result<LinkPageVO> getClassContestPage(
            @PathVariable("class_public_id") String classPublicId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") Integer current,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数最小为 1") @Max(value = 100, message = "每页条数最大为 100") Integer size) {
        return Result.success(classService.getClassContestPage(classPublicId, current, size));
    }

    /**
     * 关联比赛到班级
     */
    @RequireRole({ RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN })
    @PutMapping("/{class_public_id}/contest/{contest_public_id}")
    public Result<Void> linkContest(@PathVariable("class_public_id") String classPublicId,
            @PathVariable("contest_public_id") String contestPublicId) {
        classService.linkContest(classPublicId, contestPublicId);
        return Result.success();
    }

    /**
     * 取消班级与比赛关联
     */
    @RequireRole({ RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN })
    @DeleteMapping("/{class_public_id}/contest/{contest_public_id}")
    public Result<Void> unlinkContest(@PathVariable("class_public_id") String classPublicId,
            @PathVariable("contest_public_id") String contestPublicId) {
        classService.unlinkContest(classPublicId, contestPublicId);
        return Result.success();
    }

    /**
     * 班级学情概览
     */
    @RequireRole({ RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN })
    @GetMapping("/{class_public_id}/overview")
    public Result<ClassOverviewVO> getClassOverview(
            @PathVariable("class_public_id") String classPublicId) {
        return Result.success(classService.getClassOverview(classPublicId));
    }

    /**
     * 班级题单做题矩阵
     */
    @RequireRole({ RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN })
    @GetMapping("/{class_public_id}/problem_set/{problem_set_public_id}/matrix")
    public Result<ClassProblemSetMatrixVO> getClassProblemSetMatrix(
            @PathVariable("class_public_id") String classPublicId,
            @PathVariable("problem_set_public_id") String problemSetPublicId) {
        return Result.success(classService.getClassProblemSetMatrix(classPublicId, problemSetPublicId));
    }
}
