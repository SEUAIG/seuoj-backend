package com.seuoj.seuojbackend.controller.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.dto.assignment.AssignmentCreateDTO;
import com.seuoj.seuojbackend.dto.assignment.AssignmentUpdateDTO;
import com.seuoj.seuojbackend.entity.Assignment;
import com.seuoj.seuojbackend.service.AssignmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.HashMap;
import java.util.Map;
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
@RequestMapping("/api/class/{class_public_id}/assignment")
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @PostMapping
    public Result<Map<String, String>> createAssignment(
            @PathVariable("class_public_id") String classPublicId,
            @Valid @RequestBody AssignmentCreateDTO dto) {
        String publicId = assignmentService.createAssignment(classPublicId, dto);
        Map<String, String> result = new HashMap<>();
        result.put("assignmentPublicId", publicId);
        return Result.success(result);
    }

    @GetMapping("/page")
    public Result<IPage<Assignment>> getAssignmentPage(
            @PathVariable("class_public_id") String classPublicId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") Integer current,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数最小为 1")
            @Max(value = 100, message = "每页条数最大为 100") Integer size) {
        return Result.success(assignmentService.getAssignmentPage(classPublicId, current, size));
    }

    @GetMapping("/{assignment_public_id}")
    public Result<Assignment> getAssignmentDetail(
            @PathVariable("class_public_id") String classPublicId,
            @PathVariable("assignment_public_id") String assignmentPublicId) {
        return Result.success(assignmentService.getAssignmentDetail(classPublicId, assignmentPublicId));
    }

    @PutMapping("/{assignment_public_id}")
    public Result<Void> updateAssignment(
            @PathVariable("class_public_id") String classPublicId,
            @PathVariable("assignment_public_id") String assignmentPublicId,
            @RequestBody AssignmentUpdateDTO dto) {
        assignmentService.updateAssignment(classPublicId, assignmentPublicId, dto);
        return Result.success();
    }

    @DeleteMapping("/{assignment_public_id}")
    public Result<Void> deleteAssignment(
            @PathVariable("class_public_id") String classPublicId,
            @PathVariable("assignment_public_id") String assignmentPublicId) {
        assignmentService.deleteAssignment(classPublicId, assignmentPublicId);
        return Result.success();
    }
}
