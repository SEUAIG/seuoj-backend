package com.seuoj.seuojbackend.controller.api;

import com.seuoj.seuojbackend.annotation.RequireRole;
import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.common.RoleType;
import com.seuoj.seuojbackend.storage.FileUploadStorage;
import com.seuoj.seuojbackend.storage.FileUploadStorage.FileUploadResult;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/file")
public class FileController {

    private static final Pattern INVALID_FILENAME_CHARS = Pattern.compile("[\\\\/\\r\\n\\t\\x00]");

    private final FileUploadStorage fileUploadStorage;

    public FileController(FileUploadStorage fileUploadStorage) {
        this.fileUploadStorage = fileUploadStorage;
    }

    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        FileUploadResult result = fileUploadStorage.store(file);
        return Result.success(Map.of(
                "path", result.path(),
                "name", result.name(),
                "size", result.size()
        ));
    }

    @RequireRole({RoleType.STUDENT, RoleType.TEACHER, RoleType.ADMIN, RoleType.SUPER_ADMIN})
    @GetMapping("/download/**")
    public ResponseEntity<Resource> download(HttpServletRequest request,
                                             @RequestParam(value = "name", required = false) String name) {
        String fullPath = request.getRequestURI();
        String prefix = "/api/file/download/";
        String relativePath = fullPath.substring(fullPath.indexOf(prefix) + prefix.length());

        Resource resource = fileUploadStorage.loadAsResource(relativePath);
        String fallbackFilename = resource.getFilename();
        if (fallbackFilename == null || fallbackFilename.isBlank()) {
            fallbackFilename = "download";
        }

        String downloadFilename = sanitizeDownloadFilename(name, fallbackFilename);
        String encodedFilename = URLEncoder.encode(downloadFilename, StandardCharsets.UTF_8).replace("+", "%20");
        String asciiFilename = toAsciiFallback(downloadFilename);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + asciiFilename + "\"; filename*=UTF-8''" + encodedFilename)
                .body(resource);
    }

    private String sanitizeDownloadFilename(String name, String fallbackFilename) {
        if (name == null || name.isBlank()) {
            return fallbackFilename;
        }
        String cleaned = INVALID_FILENAME_CHARS.matcher(name.trim()).replaceAll("_");
        if (cleaned.isBlank()) {
            return fallbackFilename;
        }
        return cleaned;
    }

    private String toAsciiFallback(String filename) {
        StringBuilder sb = new StringBuilder(filename.length());
        for (char c : filename.toCharArray()) {
            if (c >= 32 && c <= 126 && c != '"') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        String value = sb.toString();
        return value.isBlank() ? "download" : value;
    }
}
