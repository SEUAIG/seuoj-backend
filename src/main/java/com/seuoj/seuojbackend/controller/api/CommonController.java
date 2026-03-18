package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.annotation.RequireRole;
import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.common.RoleType;
import com.seuoj.seuojbackend.service.CommonService;
import com.seuoj.seuojbackend.vo.common.UserPageVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/common/user")
public class CommonController {

    private final CommonService commonService;

    public CommonController(CommonService commonService) {
        this.commonService = commonService;
    }

    @RequireRole({RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @GetMapping("/page")
    public Result<UserPageVO> getUserPage(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "current must be >= 1") Integer current,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size must be >= 1")
            @Max(value = 100, message = "size must be <= 100") Integer size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) List<String> roles) {
        return Result.success(commonService.getUserPage(current, size, username, email, roles));
    }
}