package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.seuoj.seuojbackend.common.ImageAssetStatus;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.dto.image.ImageBindDTO;
import com.seuoj.seuojbackend.entity.ImageAsset;
import com.seuoj.seuojbackend.entity.ImageBinding;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ConflictException;
import com.seuoj.seuojbackend.exception.InternalServerException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.mapper.ImageAssetMapper;
import com.seuoj.seuojbackend.mapper.ImageBindingMapper;
import com.seuoj.seuojbackend.vo.image.ImageUploadVO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class ImageService {

    private static final Pattern IMAGE_KEY_PATTERN = Pattern.compile("^\\d{4}/\\d{2}/[A-Za-z0-9._-]+$");

    private final ImageAssetMapper imageAssetMapper;
    private final ImageBindingMapper imageBindingMapper;

    @Value("${storage.image-path:./data/images}")
    private String imagePath;

    @Value("${storage.image-max-file-size:10485760}")
    private long maxImageFileSize;

    @Value("${storage.image-allowed-mime-types:image/jpeg,image/png,image/webp,image/gif}")
    private String allowedMimeTypesRaw;

    public ImageService(ImageAssetMapper imageAssetMapper, ImageBindingMapper imageBindingMapper) {
        this.imageAssetMapper = imageAssetMapper;
        this.imageBindingMapper = imageBindingMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public ImageUploadVO upload(MultipartFile file) {
        Long userId = AuthContexts.requiredUserId();

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("图片不能为空");
        }
        if (file.getSize() > maxImageFileSize) {
            throw new BadRequestException("图片大小超过限制（最大 " + (maxImageFileSize / 1024 / 1024) + " MB）");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new InternalServerException("读取图片失败");
        }

        String mimeType = analyzeImage(bytes);
        Set<String> allowedMimeTypes = parseAllowedMimeTypes();
        if (!allowedMimeTypes.contains(mimeType.toLowerCase(Locale.ROOT))) {
            throw new BadRequestException("不支持的图片类型");
        }

        String extension = extensionByMime(mimeType);
        LocalDate now = LocalDate.now();
        String imageKey = now.getYear() + "/" + String.format("%02d", now.getMonthValue())
                + "/" + UUID.randomUUID() + "." + extension;

        Path fullPath = resolveStoragePath(imageKey);
        try {
            Files.createDirectories(fullPath.getParent());
            Files.write(fullPath, bytes);
        } catch (IOException e) {
            log.error("写入图片文件失败, imageKey={}", imageKey, e);
            throw new InternalServerException("图片上传失败");
        }

        ImageAsset asset = new ImageAsset();
        asset.setImageKey(imageKey);
        asset.setStoragePath(imageKey);
        asset.setMimeType(mimeType);
        asset.setFileSize((long) bytes.length);
        asset.setSha256(sha256Hex(bytes));
        asset.setUploaderUserId(userId);
        asset.setStatus(ImageAssetStatus.ACTIVE.name());
        try {
            imageAssetMapper.insert(asset);
        } catch (DuplicateKeyException ex) {
            throw new ConflictException("图片 key 冲突，请重试");
        }

        ImageUploadVO vo = new ImageUploadVO();
        vo.setImageKey(imageKey);
        vo.setMimeType(mimeType);
        vo.setSize((long) bytes.length);
        return vo;
    }

    public ImageReadPayload read(String imageKey) {
        String normalizedKey = normalizeImageKey(imageKey);

        ImageAsset asset = imageAssetMapper.selectOne(new LambdaQueryWrapper<ImageAsset>()
                .eq(ImageAsset::getImageKey, normalizedKey));
        if (asset == null || !ImageAssetStatus.ACTIVE.name().equals(asset.getStatus())) {
            throw new NotFoundException("图片不存在");
        }

        Path imageFile = resolveStoragePath(asset.getStoragePath());
        if (!Files.exists(imageFile) || !Files.isReadable(imageFile)) {
            throw new NotFoundException("图片不存在");
        }

        try {
            Resource resource = new UrlResource(imageFile.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new NotFoundException("图片不存在");
            }
            return new ImageReadPayload(resource, MediaType.parseMediaType(asset.getMimeType()));
        } catch (IOException ex) {
            throw new NotFoundException("图片不存在");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void replaceBindings(ResourceType resourceType, Long resourceId, ImageBindDTO dto) {
        validateBindingTarget(resourceType, resourceId);

        Set<String> normalizedKeys = new LinkedHashSet<>();
        if (dto != null && dto.getImageKeys() != null) {
            for (String rawKey : dto.getImageKeys()) {
                if (!StringUtils.hasText(rawKey)) {
                    continue;
                }
                normalizedKeys.add(normalizeImageKey(rawKey));
            }
        }

        List<ImageBinding> oldBindings = imageBindingMapper.selectList(new LambdaQueryWrapper<ImageBinding>()
                .eq(ImageBinding::getResourceType, resourceType.name())
                .eq(ImageBinding::getResourceId, resourceId));
        Set<Long> oldImageIds = new LinkedHashSet<>();
        for (ImageBinding binding : oldBindings) {
            oldImageIds.add(binding.getImageId());
        }

        List<ImageAsset> assets = new ArrayList<>();
        if (!normalizedKeys.isEmpty()) {
            assets = imageAssetMapper.selectList(new LambdaQueryWrapper<ImageAsset>()
                    .in(ImageAsset::getImageKey, normalizedKeys)
                    .eq(ImageAsset::getStatus, ImageAssetStatus.ACTIVE.name()));
            if (assets.size() != normalizedKeys.size()) {
                throw new BadRequestException("部分图片不存在或已失效");
            }
        }

        Set<Long> newImageIds = new LinkedHashSet<>();
        for (ImageAsset asset : assets) {
            newImageIds.add(asset.getId());
        }

        Set<Long> toRemove = new LinkedHashSet<>(oldImageIds);
        toRemove.removeAll(newImageIds);
        if (!toRemove.isEmpty()) {
            imageBindingMapper.update(null, new LambdaUpdateWrapper<ImageBinding>()
                    .set(ImageBinding::getIsDel, 1)
                    .in(ImageBinding::getImageId, toRemove)
                    .eq(ImageBinding::getResourceType, resourceType.name())
                    .eq(ImageBinding::getResourceId, resourceId));
        }

        for (Long imageId : newImageIds) {
            int restored = imageBindingMapper.update(null, new LambdaUpdateWrapper<ImageBinding>()
                    .set(ImageBinding::getIsDel, 0)
                    .eq(ImageBinding::getImageId, imageId)
                    .eq(ImageBinding::getResourceType, resourceType.name())
                    .eq(ImageBinding::getResourceId, resourceId));
            if (restored == 0) {
                ImageBinding binding = new ImageBinding();
                binding.setImageId(imageId);
                binding.setResourceType(resourceType.name());
                binding.setResourceId(resourceId);
                try {
                    imageBindingMapper.insert(binding);
                } catch (DuplicateKeyException ignore) {
                    imageBindingMapper.update(null, new LambdaUpdateWrapper<ImageBinding>()
                            .set(ImageBinding::getIsDel, 0)
                            .eq(ImageBinding::getImageId, imageId)
                            .eq(ImageBinding::getResourceType, resourceType.name())
                            .eq(ImageBinding::getResourceId, resourceId));
                }
            }
        }

        recycleUnreferencedImages(toRemove);
    }

    @Transactional(rollbackFor = Exception.class)
    public void unbindResource(ResourceType resourceType, Long resourceId) {
        validateBindingTarget(resourceType, resourceId);

        List<ImageBinding> oldBindings = imageBindingMapper.selectList(new LambdaQueryWrapper<ImageBinding>()
                .eq(ImageBinding::getResourceType, resourceType.name())
                .eq(ImageBinding::getResourceId, resourceId));
        if (oldBindings.isEmpty()) {
            return;
        }
        Set<Long> imageIds = new LinkedHashSet<>();
        for (ImageBinding binding : oldBindings) {
            imageIds.add(binding.getImageId());
        }

        imageBindingMapper.update(null, new LambdaUpdateWrapper<ImageBinding>()
                .set(ImageBinding::getIsDel, 1)
                .eq(ImageBinding::getResourceType, resourceType.name())
                .eq(ImageBinding::getResourceId, resourceId));

        recycleUnreferencedImages(imageIds);
    }

    private void recycleUnreferencedImages(Set<Long> candidateImageIds) {
        for (Long imageId : candidateImageIds) {
            if (imageId == null) {
                continue;
            }
            Long refCount = imageBindingMapper.selectCount(new LambdaQueryWrapper<ImageBinding>()
                    .eq(ImageBinding::getImageId, imageId));
            if (refCount != null && refCount > 0) {
                continue;
            }

            ImageAsset asset = imageAssetMapper.selectById(imageId);
            if (asset == null || ImageAssetStatus.DELETED.name().equals(asset.getStatus())) {
                continue;
            }
            boolean deleted = deleteImageFile(asset.getStoragePath());
            if (deleted) {
                imageAssetMapper.update(null, new LambdaUpdateWrapper<ImageAsset>()
                        .set(ImageAsset::getStatus, ImageAssetStatus.DELETED.name())
                        .set(ImageAsset::getDeletedAt, LocalDateTime.now())
                        .eq(ImageAsset::getId, imageId));
            }
        }
    }

    private boolean deleteImageFile(String storagePath) {
        Path fullPath = resolveStoragePath(storagePath);
        try {
            Files.deleteIfExists(fullPath);
            return true;
        } catch (IOException ex) {
            log.error("删除图片文件失败, path={}", storagePath, ex);
            return false;
        }
    }

    private void validateBindingTarget(ResourceType resourceType, Long resourceId) {
        if (resourceType == null) {
            throw new BadRequestException("resourceType 非法");
        }
        if (resourceId == null || resourceId <= 0) {
            throw new BadRequestException("resourceId 非法");
        }
    }

    private String normalizeImageKey(String imageKey) {
        if (!StringUtils.hasText(imageKey)) {
            throw new BadRequestException("图片 key 不能为空");
        }
        String trimmed = imageKey.trim();
        if (!IMAGE_KEY_PATTERN.matcher(trimmed).matches()) {
            throw new BadRequestException("非法图片 key");
        }
        return trimmed;
    }

    private Path resolveStoragePath(String relativePath) {
        Path basePath = Paths.get(imagePath).toAbsolutePath().normalize();
        Path resolved = basePath.resolve(relativePath).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new BadRequestException("非法图片路径");
        }
        return resolved;
    }

    private Set<String> parseAllowedMimeTypes() {
        Set<String> result = new LinkedHashSet<>();
        for (String part : allowedMimeTypesRaw.split(",")) {
            String value = part.trim().toLowerCase(Locale.ROOT);
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return result;
    }

    private String analyzeImage(byte[] bytes) {
        String mime = detectMimeType(bytes);
        if (mime == null) {
            throw new BadRequestException("不支持的图片类型");
        }

        if ("image/webp".equals(mime)) {
            int[] size = parseWebpSize(bytes);
            if (size == null || size[0] <= 0 || size[1] <= 0) {
                throw new BadRequestException("图片内容无法解析");
            }
            return mime;
        }

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw new BadRequestException("图片内容无法解析");
            }
            return mime;
        } catch (IOException ex) {
            throw new BadRequestException("图片内容无法解析");
        }
    }

    private String detectMimeType(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return null;
        }
        if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 8
                && bytes[0] == (byte) 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A) {
            return "image/png";
        }
        if (bytes.length >= 6
                && bytes[0] == 0x47
                && bytes[1] == 0x49
                && bytes[2] == 0x46
                && bytes[3] == 0x38
                && (bytes[4] == 0x37 || bytes[4] == 0x39)
                && bytes[5] == 0x61) {
            return "image/gif";
        }
        if (bytes.length >= 12
                && bytes[0] == 0x52
                && bytes[1] == 0x49
                && bytes[2] == 0x46
                && bytes[3] == 0x46
                && bytes[8] == 0x57
                && bytes[9] == 0x45
                && bytes[10] == 0x42
                && bytes[11] == 0x50) {
            return "image/webp";
        }
        return null;
    }

    private int[] parseWebpSize(byte[] bytes) {
        if (bytes.length < 30) {
            return null;
        }
        int offset = 12;
        while (offset + 8 <= bytes.length) {
            String chunkType = new String(bytes, offset, 4);
            int chunkSize = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            int chunkDataOffset = offset + 8;
            if (chunkDataOffset + chunkSize > bytes.length) {
                return null;
            }

            if ("VP8X".equals(chunkType)) {
                if (chunkSize < 10) {
                    return null;
                }
                int width = 1 + ((bytes[chunkDataOffset + 4] & 0xFF)
                        | ((bytes[chunkDataOffset + 5] & 0xFF) << 8)
                        | ((bytes[chunkDataOffset + 6] & 0xFF) << 16));
                int height = 1 + ((bytes[chunkDataOffset + 7] & 0xFF)
                        | ((bytes[chunkDataOffset + 8] & 0xFF) << 8)
                        | ((bytes[chunkDataOffset + 9] & 0xFF) << 16));
                return new int[] {width, height};
            }
            if ("VP8 ".equals(chunkType)) {
                if (chunkSize < 10) {
                    return null;
                }
                int frameHeaderOffset = chunkDataOffset + 3;
                if (frameHeaderOffset + 7 > bytes.length) {
                    return null;
                }
                if ((bytes[frameHeaderOffset] & 0xFF) != 0x9D
                        || (bytes[frameHeaderOffset + 1] & 0xFF) != 0x01
                        || (bytes[frameHeaderOffset + 2] & 0xFF) != 0x2A) {
                    return null;
                }
                int width = ((bytes[frameHeaderOffset + 3] & 0xFF) | ((bytes[frameHeaderOffset + 4] & 0x3F) << 8));
                int height = ((bytes[frameHeaderOffset + 5] & 0xFF) | ((bytes[frameHeaderOffset + 6] & 0x3F) << 8));
                return new int[] {width, height};
            }
            if ("VP8L".equals(chunkType)) {
                if (chunkSize < 5) {
                    return null;
                }
                int sig = bytes[chunkDataOffset] & 0xFF;
                if (sig != 0x2F) {
                    return null;
                }
                int b1 = bytes[chunkDataOffset + 1] & 0xFF;
                int b2 = bytes[chunkDataOffset + 2] & 0xFF;
                int b3 = bytes[chunkDataOffset + 3] & 0xFF;
                int b4 = bytes[chunkDataOffset + 4] & 0xFF;
                int width = 1 + (b1 | ((b2 & 0x3F) << 8));
                int height = 1 + (((b2 >> 6) & 0x03) | (b3 << 2) | ((b4 & 0x0F) << 10));
                return new int[] {width, height};
            }
            offset = chunkDataOffset + chunkSize + (chunkSize % 2);
        }
        return null;
    }

    private String extensionByMime(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> throw new BadRequestException("不支持的图片类型");
        };
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new InternalServerException("计算文件摘要失败");
        }
    }

    public record ImageReadPayload(Resource resource, MediaType mediaType) {
    }
}
