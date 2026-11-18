package com.group4.kaoyan.platform.storage;

import java.io.InputStream;

/**
 * 文件存储抽象，当前实现为 MinIO，可替换成 OSS / 本地磁盘。
 */
public interface StorageService {

    void upload(String objectKey, InputStream in, long size, String contentType);

    InputStream download(String objectKey);

    void delete(String objectKey);
}
