package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.annotation.AllowAnonymous;
import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.dto.contest.ContestEditorialDTO;
import com.seuoj.seuojbackend.service.ContestDiscussionService;
import com.seuoj.seuojbackend.vo.contest.ContestDiscussionVO;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 比赛「讨论」标签：官方题解 + 题目索引 */
@RestController
@Validated
@RequestMapping("/api/contest/{id}")
public class ContestDiscussionController {

    private final ContestDiscussionService discussionService;

    public ContestDiscussionController(ContestDiscussionService discussionService) {
        this.discussionService = discussionService;
    }

    @AllowAnonymous
    @GetMapping("/discussion")
    public Result<ContestDiscussionVO> getDiscussion(@PathVariable("id") Long id) {
        return Result.success(discussionService.getDiscussion(id));
    }

    @PutMapping("/editorial")
    public Result<Map<String, Long>> saveEditorial(@PathVariable("id") Long id,
                                                   @Valid @RequestBody ContestEditorialDTO dto) {
        return Result.success(Map.of("post_id", discussionService.saveEditorial(id, dto)));
    }

    @DeleteMapping("/editorial")
    public Result<Void> deleteEditorial(@PathVariable("id") Long id) {
        discussionService.deleteEditorial(id);
        return Result.success();
    }

    /** 题解 PDF：inline 返回，浏览器直接打开；权限同官方题解 */
    @AllowAnonymous
    @GetMapping("/editorial/attachments/{attachmentId}")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable("id") Long id,
                                                       @PathVariable("attachmentId") Long attachmentId) {
        ContestDiscussionService.AttachmentFile file = discussionService.loadAttachment(id, attachmentId);
        String name = file.fileName() == null || file.fileName().isBlank() ? "editorial.pdf" : file.fileName();
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"editorial.pdf\"; filename*=UTF-8''" + encoded)
                .header("X-Content-Type-Options", "nosniff")
                .body(file.resource());
    }
}
