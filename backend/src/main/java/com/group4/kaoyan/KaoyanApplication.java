package com.group4.kaoyan;

import com.group4.kaoyan.platform.storage.MinioProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
// 不扫根包：platform/storage、platform/ai 下的 StorageService、VectorStore 等是业务接口，
// 被当成 Mapper 注册会与真实 Bean 冲突，因此只精确扫描各 mapper 包
@MapperScan({
        "com.group4.kaoyan.modules",
        "com.group4.kaoyan.platform.messaging.mapper",
        "com.group4.kaoyan.platform.idempotency.mapper"
})
@EnableConfigurationProperties(MinioProperties.class)
public class KaoyanApplication {

    public static void main(String[] args) {
        SpringApplication.run(KaoyanApplication.class, args);
    }
}
