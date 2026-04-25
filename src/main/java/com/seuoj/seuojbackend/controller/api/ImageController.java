package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.annotation.AllowAnonymous;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.dto.image.ImageBindDTO;
import com.seuoj.seuojbackend.service.ImageService;
import com.seuoj.seuojbackend.service.ImageService.ImageReadPayload;
import com.seuoj.seuojbackend.vo.image.ImageUploadVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@Validated
@RequestMapping("/api/image")
public class ImageController {

    private final ImageService imageService;

    @Value("${storage.image-public-base-url:}")
    private String imagePublicBaseUrl;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping("/upload")
    public Result<ImageUploadVO> upload(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        String baseUrl = resolveBaseUrl(request);
        ImageUploadVO vo = imageService.upload(file);
        URI uri = imageService.buildImageUri(baseUrl, vo.getImageKey());
        vo.setUrl(uri.toString());
        return Result.success(vo);
    }

    @AllowAnonymous
    @GetMapping("/{year:\\d{4}}/{month:\\d{2}}/{filename:.+}")
    public ResponseEntity<Resource> getImage(@PathVariable String year,
                                             @PathVariable String month,
                                             @PathVariable String filename) {
        String imageKey = year + "/" + month + "/" + filename;
        ImageReadPayload payload = imageService.read(imageKey);
        return ResponseEntity.ok()
                .contentType(payload.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .cacheControl(CacheControl.maxAge(31536000, java.util.concurrent.TimeUnit.SECONDS)
                        .cachePublic()
                        .immutable())
                .body(payload.resource());
    }

    @PutMapping("/bindings/{resourceType}/{resourceId}")
    public Result<Void> replaceBindings(@PathVariable String resourceType,
                                        @PathVariable Long resourceId,
                                        @Valid @RequestBody(required = false) ImageBindDTO dto) {
        imageService.replaceBindings(parseResourceType(resourceType), resourceId, dto);
        return Result.success();
    }

    @DeleteMapping("/bindings/{resourceType}/{resourceId}")
    public Result<Void> clearBindings(@PathVariable String resourceType,
                                      @PathVariable Long resourceId) {
        imageService.unbindResource(parseResourceType(resourceType), resourceId);
        return Result.success();
    }

    private ResourceType parseResourceType(String resourceType) {
        try {
            return ResourceType.valueOf(resourceType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new com.seuoj.seuojbackend.exception.BadRequestException("resourceType 非法");
        }
    }

    private String resolveBaseUrl(HttpServletRequest request) {
        if (imagePublicBaseUrl != null && !imagePublicBaseUrl.isBlank()) {
            return imagePublicBaseUrl.trim();
        }
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
        if (defaultPort) {
            return scheme + "://" + host;
        }
        return scheme + "://" + host + ":" + port;
    }
}
