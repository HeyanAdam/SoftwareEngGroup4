package com.group4.kaoyan.platform.storage;

import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.config.AiProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinioStorageService implements StorageService, InitializingBean {

    private final AiProperties props;
    private MinioClient client;

    @Override
    public void afterPropertiesSet() {
        AiProperties.Storage cfg = props.getStorage();
        client = MinioClient.builder()
                .endpoint(cfg.getEndpoint())
                .credentials(cfg.getAccessKey(), cfg.getSecretKey())
                .build();
        try {
            ensureBucket();
        } catch (Exception e) {
            log.warn("MinIO 初始化失败，文件上传功能不可用：{}", e.getMessage());
        }
    }

    private void ensureBucket() throws Exception {
        String bucket = props.getStorage().getBucket();
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("已创建 MinIO bucket：{}", bucket);
        }
    }

    @Override
    public void upload(String objectKey, InputStream in, long size, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(props.getStorage().getBucket())
                    .object(objectKey)
                    .stream(in, size, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
        } catch (Exception e) {
            throw new BizException(500, "文件存储失败：" + e.getMessage());
        }
    }

    @Override
    public InputStream download(String objectKey) {
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(props.getStorage().getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new BizException(500, "文件读取失败：" + e.getMessage());
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(props.getStorage().getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.warn("删除对象失败：{}，{}", objectKey, e.getMessage());
        }
    }
}
