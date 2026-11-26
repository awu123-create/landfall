package com.landfall.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 存储适配器接口：负责将文件落盘并返回访问信息
 */
public interface StorageAdapter {

    /**
     * 上传文件
     * @param file Spring 的 MultipartFile
     * @param folder 存储的业务目录（如 "images", "videos"）
     * @return 上传结果
     */
    StorageResponse upload(MultipartFile file, String folder);
}
