package com.landfall.storage;

import com.landfall.common.api.BusinessException;
import com.landfall.common.api.ErrorCode;
import com.landfall.common.id.UuidV7Generator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

@Component
public class LocalDiskStorageAdapter implements StorageAdapter {

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024L; // 50MB 大小限制
    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "mp4");

    private final Path rootLocation;

    // 使用构造器注入，从配置文件读取上传路径，默认是 ./uploads
    public LocalDiskStorageAdapter(@Value("${landfall.storage.local-path:./uploads}") String uploadPath) {
        this.rootLocation = Paths.get(uploadPath).toAbsolutePath().normalize();
    }

    @Override
    public StorageResponse upload(MultipartFile file, String folder) {
        // 1. 空文件校验
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }

        // 2. 大小校验
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }

        // 3. 扩展名校验
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new BusinessException(ErrorCode.INVALID_FILE_EXTENSION);
        }
        String extension = getExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_EXTENSION);
        }

        // 4. 魔数校验（防伪造扩展名）
        if (!isValidMagicNumber(file, extension)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_HEADER);
        }

        // 5. 生成 UUIDv7 并落盘
        String assetId = UuidV7Generator.generate().toString();
        String finalFileName = assetId + "." + extension;
        Path targetDir = this.rootLocation.resolve(folder).normalize();
        
        // 安全检查：防止路径穿越攻击
        if (!targetDir.startsWith(this.rootLocation)) {
             throw new BusinessException(ErrorCode.BAD_REQUEST, "非法路径");
        }

        Path targetFile = targetDir.resolve(finalFileName);

        try {
            Files.createDirectories(targetDir);
            file.transferTo(targetFile);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件落盘失败: " + e.getMessage());
        }

        // 6. 返回结果
        String url = "/" + folder + "/" + finalFileName; 
        return new StorageResponse(assetId, url, file.getContentType(), file.getSize());
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0) {
            throw new BusinessException(ErrorCode.INVALID_FILE_EXTENSION);
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    // 核心：文件头魔数校验
    private boolean isValidMagicNumber(MultipartFile file, String extension) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[12];
            if (is.read(header) < 4) {
                return false;
            }

            return switch (extension) {
                // JPG 开头是 FF D8 FF
                case "jpg", "jpeg" -> (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF);
                // PNG 开头是 89 50 4E 47
                case "png" -> (header[0] == (byte) 0x89 && header[1] == (byte) 0x50 && header[2] == (byte) 0x4E && header[3] == (byte) 0x47);
                // MP4 第 5-8 个字节是 'f' 't' 'y' 'p' (66 74 79 70)
                case "mp4" -> (header[4] == (byte) 0x66 && header[5] == (byte) 0x74 && header[6] == (byte) 0x79 && header[7] == (byte) 0x70);
                default -> false;
            };
        } catch (IOException e) {
            return false;
        }
    }
}