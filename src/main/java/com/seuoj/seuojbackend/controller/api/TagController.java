package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.annotation.AllowAnonymous;
import com.seuoj.seuojbackend.annotation.RequireRole;
import com.seuoj.seuojbackend.common.RoleType;
import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.dto.tag.TagCreateDTO;
import com.seuoj.seuojbackend.dto.tag.TagUpdateDTO;
import com.seuoj.seuojbackend.service.TagService;
import jakarta.validation.Valid;
import com.seuoj.seuojbackend.vo.tag.TagListVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api")
public class TagController {
    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    /**
     * 获取所有标签
     * @return 所有标签
     */
    @AllowAnonymous
    @GetMapping("/problem/tag")
    public Result<TagListVO> listTags() {
        return Result.success(tagService.listTags());
    }

    @RequireRole({RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @PostMapping("/problem/tag")
    public Result<Void> createTag(@Valid @RequestBody TagCreateDTO dto) {
        tagService.createTag(dto);
        return Result.success();
    }

    @RequireRole({RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @PutMapping("/problem/tag")
    public Result<Void> updateTag(@Valid @RequestBody TagUpdateDTO dto) {
        tagService.updateTag(dto);
        return Result.success();
    }
}
