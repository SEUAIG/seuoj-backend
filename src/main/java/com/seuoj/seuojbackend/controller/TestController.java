package com.seuoj.seuojbackend.controller;

import com.seuoj.seuojbackend.annotation.AllowAnonymous;
import com.seuoj.seuojbackend.annotation.RequireRole;
import com.seuoj.seuojbackend.common.RoleType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequireRole({RoleType.TEACHER})
@RestController
@RequestMapping("/test")
public class TestController {
    /**
     * 测试游客接口
     */
    @AllowAnonymous
    @RequestMapping("/anonymous")
    public String anonymous() {
        return "Anonymous Access";
    }

    /**
     * 测试学生权限
     */
    @RequireRole({RoleType.STUDENT})
    @RequestMapping("/student-auth")
    public String studentAuth() {
        return "Student Auth Required";
    }

    /**
     * 测试教师权限
     */
    @RequestMapping("/teacher-auth")
    public String teacherAuth() {
        return "Teacher Auth Required";
    }
}
