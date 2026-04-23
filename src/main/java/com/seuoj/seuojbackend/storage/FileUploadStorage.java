package com.seuoj.seuojbackend.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;

import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.InternalServerException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
public class FileUploadStorage {

    @Value("${storage.upload-path:/app/data/uploads}")
    private String uploadPath;

    @Value("${storage.upload-max-file-size:20971520}")
    private long maxFileSize;

    public FileUploadResult store(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("文件不能为空");
        }
        if (file.getSize() > maxFileSize) {
            throw new BadRequestException("文件大小超过限制（最大 " + (maxFileSize / 1024 / 1024) + " MB）");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            originalName = "unnamed";
        }

        String ext = "";
        int dotIdx = originalName.lastIndexOf('.');
        if (dotIdx >= 0) {
            ext = originalName.substring(dotIdx);
        }

        LocalDate now = LocalDate.now();
        String relativePath = now.getYear() + "/" + String.format("%02d", now.getMonthValue())
                + "/" + UUID.randomUUID() + ext;
        Path fullPath = Paths.get(uploadPath, relativePath);

        try {
            Files.createDirectories(fullPath.getParent());
            file.transferTo(fullPath);
        } catch (IOException e) {
            log.error("文件上传失败: {}", originalName, e);
            throw new InternalServerException("文件上传失败");
        }

        return new FileUploadResult(relativePath, originalName, file.getSize());
    }

    public Resource loadAsResource(String relativePath) {
        try {
            Path filePath = Paths.get(uploadPath, relativePath).normalize();
            if (!filePath.startsWith(Paths.get(uploadPath))) {
                throw new BadRequestException("非法文件路径");
            }
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new NotFoundException("文件不存在");
            }
            return resource;
        } catch (IOException e) {
            throw new NotFoundException("文件不存在");
        }
    }

    public record FileUploadResult(String path, String name, long size) {}
}
