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
@RequestMapping("/api/class/{classId}/assignment")
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @PostMapping
    public Result<Map<String, Long>> createAssignment(
            @PathVariable("classId") Long classId,
            @Valid @RequestBody AssignmentCreateDTO dto) {
        Long assignmentId = assignmentService.createAssignment(classId, dto);
        Map<String, Long> result = new HashMap<>();
        result.put("assignment_id", assignmentId);
        return Result.success(result);
    }

    @GetMapping("/page")
    public Result<IPage<Assignment>> getAssignmentPage(
            @PathVariable("classId") Long classId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") Integer current,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数最小为 1")
            @Max(value = 100, message = "每页条数最大为 100") Integer size) {
        return Result.success(assignmentService.getAssignmentPage(classId, current, size));
    }

    @GetMapping("/{assignmentId}")
    public Result<Assignment> getAssignmentDetail(
            @PathVariable("classId") Long classId,
            @PathVariable("assignmentId") Long assignmentId) {
        return Result.success(assignmentService.getAssignmentDetail(classId, assignmentId));
    }

    @PutMapping("/{assignmentId}")
    public Result<Void> updateAssignment(
            @PathVariable("classId") Long classId,
            @PathVariable("assignmentId") Long assignmentId,
            @RequestBody AssignmentUpdateDTO dto) {
        assignmentService.updateAssignment(classId, assignmentId, dto);
        return Result.success();
    }

    @DeleteMapping("/{assignmentId}")
    public Result<Void> deleteAssignment(
            @PathVariable("classId") Long classId,
            @PathVariable("assignmentId") Long assignmentId) {
        assignmentService.deleteAssignment(classId, assignmentId);
        return Result.success();
    }
}
