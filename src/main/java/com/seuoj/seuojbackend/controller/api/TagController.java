package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.annotation.AllowAnonymous;
import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.service.TagService;
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
}
