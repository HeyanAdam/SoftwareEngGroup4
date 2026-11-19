package com.group4.kaoyan.platform.storage;

import com.group4.kaoyan.common.BizException;
import io.minio.BucketExistsArgs;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 实现。配置统一走 {@link MinioProperties}（application.yml 的 minio.* 节点），
 * 桶名 default kaoyan-files。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioStorageService implements StorageService, InitializingBean {

    private final MinioClient minioClient;
    private final MinioProperties properties;

    @Override
    public void afterPropertiesSet() {
        try {
            ensureBucket();
        } catch (Exception e) {
            // 中间件未就绪时不要让整个应用起不来，文件功能降级
            log.warn("MinIO 初始化失败，文件功能暂不可用：{}", e.getMessage());
        }
    }

    private void ensureBucket() throws Exception {
        String bucket = bucket();
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("已创建 MinIO bucket：{}", bucket);
        }
    }

    @Override
    public void upload(String objectKey, InputStream in, long size, String contentType) {
        put(objectKey, in, size, contentType);
    }

    @Override
    public void uploadPart(String objectKey, InputStream in, long size, String contentType) {
        put(objectKey, in, size, contentType);
    }

    private void put(String objectKey, InputStream in, long size, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .stream(in, size, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
        } catch (Exception e) {
            throw new BizException(500, "文件存储失败：" + e.getMessage());
        }
    }

    @Override
    public void compose(String targetKey, List<String> sourceKeys, String contentType) {
        try {
            List<ComposeSource> sources = new ArrayList<>();
            for (String key : sourceKeys) {
                sources.add(ComposeSource.builder().bucket(bucket()).object(key).build());
            }
            minioClient.composeObject(ComposeObjectArgs.builder()
                    .bucket(bucket())
                    .object(targetKey)
                    .sources(sources)
                    .build());
        } catch (Exception e) {
            throw new BizException(500, "分片合并失败：" + e.getMessage());
        }
    }

    @Override
    public InputStream download(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new BizException(500, "文件读取失败：" + e.getMessage());
        }
    }

    @Override
    public String presignedGet(String objectKey, int expireMinutes) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket())
                    .object(objectKey)
                    .expiry(Math.max(1, expireMinutes), TimeUnit.MINUTES)
                    .build());
        } catch (Exception e) {
            throw new BizException(500, "生成下载地址失败：" + e.getMessage());
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            // 删除是尽力而为：对象可能已不存在
            log.warn("删除对象失败：{}，{}", objectKey, e.getMessage());
        }
    }

    @Override
    public String bucketName() {
        return bucket();
    }

    private String bucket() {
        return properties.getBucket() == null || properties.getBucket().isBlank()
                ? "kaoyan-files" : properties.getBucket();
    }
}
