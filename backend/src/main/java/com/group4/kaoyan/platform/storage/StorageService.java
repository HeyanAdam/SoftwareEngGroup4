package com.group4.kaoyan.platform.storage;

import java.io.InputStream;
import java.util.List;

/**
 * 文件存储抽象，当前实现为 MinIO，可替换成 OSS / 本地磁盘。
 */
public interface StorageService {

    void upload(String objectKey, InputStream in, long size, String contentType);

    /** 上传一个分片对象（与普通 upload 同语义，单独命名便于语义清晰）。 */
    void uploadPart(String objectKey, InputStream in, long size, String contentType);

    /** 把若干分片对象合成为一个完整对象（MinIO compose）。 */
    void compose(String targetKey, List<String> sourceKeys, String contentType);

    InputStream download(String objectKey);

    /** 生成一次性下载地址，expireMinutes 内有效。 */
    String presignedGet(String objectKey, int expireMinutes);

    void delete(String objectKey);

    /** 当前使用的桶名，写库时需要落 file_object.bucket_name。 */
    String bucketName();
}
