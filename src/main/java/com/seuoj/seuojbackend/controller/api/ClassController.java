package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.annotation.AllowAnonymous;
import com.seuoj.seuojbackend.annotation.RequireRole;
import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.common.RoleType;
import com.seuoj.seuojbackend.dto.classinfo.ClassBatchImportDTO;
import com.seuoj.seuojbackend.dto.classinfo.ClassCreateDTO;
import com.seuoj.seuojbackend.dto.classinfo.ClassUpdateDTO;
import com.seuoj.seuojbackend.service.ClassService;
import com.seuoj.seuojbackend.vo.classinfo.AssignmentOverviewVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassBatchImportResultVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassCreateVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassItemVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassMemberPageVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassOverviewVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassPageVO;
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
import java.util.List;
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

    @RequireRole({RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @PostMapping
    public Result<ClassCreateVO> createClass(@Valid @RequestBody ClassCreateDTO dto) {
        return Result.success(classService.createClass(dto));
    }

    @AllowAnonymous
    @GetMapping("/page")
    public Result<ClassPageVO> getClassPage(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") Integer current,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数最小为 1")
            @Max(value = 100, message = "每页条数最大为 100") Integer size) {
        return Result.success(classService.getClassPage(current, size));
    }

    @GetMapping("/{classId}")
    public Result<ClassItemVO> getClassDetail(@PathVariable("classId") Long classId) {
        return Result.success(classService.getClassDetail(classId));
    }

    @PutMapping("/{classId}")
    public Result<ClassItemVO> updateClass(@PathVariable("classId") Long classId,
                                           @RequestBody ClassUpdateDTO dto) {
        return Result.success(classService.updateClass(classId, dto));
    }

    @DeleteMapping("/{classId}")
    public Result<Void> deleteClass(@PathVariable("classId") Long classId) {
        classService.deleteClass(classId);
        return Result.success();
    }

    @PostMapping("/{classId}/join")
    public Result<Void> joinClass(@PathVariable("classId") Long classId) {
        classService.joinClass(classId);
        return Result.success();
    }

    @GetMapping("/{classId}/member/page")
    public Result<ClassMemberPageVO> getClassMemberPage(
            @PathVariable("classId") Long classId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") Integer current,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数最小为 1")
            @Max(value = 100, message = "每页条数最大为 100") Integer size) {
        return Result.success(classService.getClassMemberPage(classId, current, size));
    }

    @DeleteMapping("/{classId}/member/{userId}")
    public Result<Void> removeMember(@PathVariable("classId") Long classId,
                                     @PathVariable("userId") Long userId) {
        classService.removeMember(classId, userId);
        return Result.success();
    }

    @PostMapping("/{classId}/member/{userId}")
    public Result<Void> addMember(@PathVariable("classId") Long classId,
                                  @PathVariable("userId") Long userId) {
        classService.addMember(classId, userId);
        return Result.success();
    }

    @RequireRole({RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @PostMapping("/{classId}/batch-import")
    public Result<ClassBatchImportResultVO> batchImportMembers(
            @PathVariable("classId") Long classId,
            @Valid @RequestBody ClassBatchImportDTO dto) {
        return Result.success(classService.batchImportMembers(classId, dto));
    }

    @GetMapping("/{classId}/contest/page")
    public Result<LinkPageVO> getClassContestPage(
            @PathVariable("classId") Long classId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") Integer current,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数最小为 1")
            @Max(value = 100, message = "每页条数最大为 100") Integer size) {
        return Result.success(classService.getClassContestPage(classId, current, size));
    }

    @PutMapping("/{classId}/contest/{contestId}")
    public Result<Void> linkContest(@PathVariable("classId") Long classId,
                                    @PathVariable("contestId") Long contestId) {
        classService.linkContest(classId, contestId);
        return Result.success();
    }

    @DeleteMapping("/{classId}/contest/{contestId}")
    public Result<Void> unlinkContest(@PathVariable("classId") Long classId,
                                      @PathVariable("contestId") Long contestId) {
        classService.unlinkContest(classId, contestId);
        return Result.success();
    }

    @GetMapping("/{classId}/problem_set/page")
    public Result<LinkPageVO> getClassProblemSetPage(
            @PathVariable("classId") Long classId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") Integer current,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数最小为 1")
            @Max(value = 100, message = "每页条数最大为 100") Integer size) {
        return Result.success(classService.getClassProblemSetPage(classId, current, size));
    }

    @PutMapping("/{classId}/problem_set/{problemSetId}")
    public Result<Void> linkProblemSet(@PathVariable("classId") Long classId,
                                       @PathVariable("problemSetId") Long problemSetId) {
        classService.linkProblemSet(classId, problemSetId);
        return Result.success();
    }

    @DeleteMapping("/{classId}/problem_set/{problemSetId}")
    public Result<Void> unlinkProblemSet(@PathVariable("classId") Long classId,
                                         @PathVariable("problemSetId") Long problemSetId) {
        classService.unlinkProblemSet(classId, problemSetId);
        return Result.success();
    }

    @GetMapping("/{classId}/overview")
    public Result<ClassOverviewVO> getClassOverview(
            @PathVariable("classId") Long classId) {
        return Result.success(classService.getClassOverview(classId));
    }

    @GetMapping("/{classId}/assignment/progress")
    public Result<List<ClassOverviewVO.AssignmentProgressItem>> getAssignmentProgress(
            @PathVariable("classId") Long classId) {
        return Result.success(classService.getAssignmentProgress(classId));
    }

    @GetMapping("/{classId}/overview/assignment/{assignmentId}")
    public Result<AssignmentOverviewVO> getAssignmentOverview(
            @PathVariable("classId") Long classId,
            @PathVariable("assignmentId") Long assignmentId) {
        return Result.success(classService.getAssignmentOverview(classId, assignmentId));
    }
}
