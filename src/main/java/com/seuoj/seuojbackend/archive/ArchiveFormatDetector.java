package com.seuoj.seuojbackend.archive;

import com.seuoj.seuojbackend.exception.InternalServerException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
class ArchiveFormatDetector {

    public String detectArchiveFormat(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = inputStream.readNBytes(512);
            if (header.length >= 6) {
                if ((header[0] & 0xFF) == 0x37 && (header[1] & 0xFF) == 0x7A
                        && (header[2] & 0xFF) == 0xBC && (header[3] & 0xFF) == 0xAF
                        && (header[4] & 0xFF) == 0x27 && (header[5] & 0xFF) == 0x1C) {
                    return "7z";
                }
            }
            if (header.length >= 4) {
                if (header[0] == 'P' && header[1] == 'K'
                        && (header[2] == 3 || header[2] == 5 || header[2] == 7)
                        && (header[3] == 4 || header[3] == 6 || header[3] == 8)) {
                    return "zip";
                }
            }
            if (header.length >= 2) {
                if ((header[0] & 0xFF) == 0x1F && (header[1] & 0xFF) == 0x8B) {
                    return "gzip";
                }
            }
            if (hasUstar(header)) {
                return "tar";
            }
            return "unknown";
        } catch (IOException ex) {
            log.error("读取压缩包头信息失败", ex);
            throw new InternalServerException("读取测试数据压缩包失败");
        }
    }

    public boolean looksLikeTar(MultipartFile file) {
        try (TarArchiveInputStream tarInputStream = createTarInputStream(file.getInputStream())) {
            return tarInputStream.getNextEntry() != null;
        } catch (IOException ex) {
            log.debug("读取tar压缩包头失败: {}", ex.getMessage());
            return false;
        }
    }

    private TarArchiveInputStream createTarInputStream(InputStream inputStream) {
        return new TarArchiveInputStream(inputStream, StandardCharsets.UTF_8.name());
    }

    private boolean hasUstar(byte[] header) {
        if (header.length < 262) {
            return false;
        }
        return header[257] == 'u' && header[258] == 's'
                && header[259] == 't' && header[260] == 'a'
                && header[261] == 'r';
    }
}
