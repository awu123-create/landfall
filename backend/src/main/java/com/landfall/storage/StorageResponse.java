package com.landfall.storage;

/**
 * 文件上传成功后的返回体
 */
public record StorageResponse(
        String assetId,     // 文件唯一标识
        String url,         // 文件访问路径（或本地相对路径）
        String contentType, // 文件类型（如 image/jpeg）
        long size           // 文件大小（字节）
) {
}