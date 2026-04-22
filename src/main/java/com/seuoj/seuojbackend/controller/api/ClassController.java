package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.annotation.AllowAnonymous;
import com.seuoj.seuojbackend.annotation.RequireRole;
import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.common.RoleType;
import com.seuoj.seuojbackend.dto.classinfo.ClassCreateDTO;
import com.seuoj.seuojbackend.dto.classinfo.ClassUpdateDTO;
import com.seuoj.seuojbackend.service.ClassService;
import com.seuoj.seuojbackend.vo.classinfo.ClassCreateVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassItemVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassMemberPageVO;
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

    @PutMapping("/{class_public_id}")
    public Result<ClassItemVO> updateClass(@PathVariable("class_public_id") String classPublicId,
                                           @RequestBody ClassUpdateDTO dto) {
        return Result.success(classService.updateClass(classPublicId, dto));
    }

    @DeleteMapping("/{class_public_id}")
    public Result<Void> deleteClass(@PathVariable("class_public_id") String classPublicId) {
        classService.deleteClass(classPublicId);
        return Result.success();
    }

    @PostMapping("/{class_public_id}/join")
    public Result<Void> joinClass(@PathVariable("class_public_id") String classPublicId) {
        classService.joinClass(classPublicId);
        return Result.success();
    }

    @GetMapping("/{class_public_id}/member/page")
    public Result<ClassMemberPageVO> getClassMemberPage(
            @PathVariable("class_public_id") String classPublicId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") Integer current,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数最小为 1")
            @Max(value = 100, message = "每页条数最大为 100") Integer size) {
        return Result.success(classService.getClassMemberPage(classPublicId, current, size));
    }

    @DeleteMapping("/{class_public_id}/member/{user_public_id}")
    public Result<Void> removeMember(@PathVariable("class_public_id") String classPublicId,
                                     @PathVariable("user_public_id") String userPublicId) {
        classService.removeMember(classPublicId, userPublicId);
        return Result.success();
    }

    @PostMapping("/{class_public_id}/member/{user_public_id}")
    public Result<Void> addMember(@PathVariable("class_public_id") String classPublicId,
                                  @PathVariable("user_public_id") String userPublicId) {
        classService.addMember(classPublicId, userPublicId);
        return Result.success();
    }

    @GetMapping("/{class_public_id}/contest/page")
    public Result<LinkPageVO> getClassContestPage(
            @PathVariable("class_public_id") String classPublicId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") Integer current,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数最小为 1")
            @Max(value = 100, message = "每页条数最大为 100") Integer size) {
        return Result.success(classService.getClassContestPage(classPublicId, current, size));
    }

    @PutMapping("/{class_public_id}/contest/{contest_public_id}")
    public Result<Void> linkContest(@PathVariable("class_public_id") String classPublicId,
                                    @PathVariable("contest_public_id") String contestPublicId) {
        classService.linkContest(classPublicId, contestPublicId);
        return Result.success();
    }

    @DeleteMapping("/{class_public_id}/contest/{contest_public_id}")
    public Result<Void> unlinkContest(@PathVariable("class_public_id") String classPublicId,
                                      @PathVariable("contest_public_id") String contestPublicId) {
        classService.unlinkContest(classPublicId, contestPublicId);
        return Result.success();
    }
}
