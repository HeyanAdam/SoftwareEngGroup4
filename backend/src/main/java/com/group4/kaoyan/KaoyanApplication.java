package com.group4.kaoyan;

import com.group4.kaoyan.platform.storage.MinioProperties;
import org.apache.ibatis.annotations.Mapper;
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
// 被当成 Mapper 注册会与真实 Bean 冲突，因此只精确扫描各 mapper 包。
// platform 两个包内的 mapper 未标注 @Mapper（基线如此），仍按包扫描。
@MapperScan("com.group4.kaoyan.platform.messaging.mapper")
@MapperScan("com.group4.kaoyan.platform.idempotency.mapper")
// modules 包必须按注解扫描：包内混有业务接口（如 EmailSender），
// 按包扫会被注册成 MyBatis 代理，启动后调用报 Invalid bound statement。
@MapperScan(value = "com.group4.kaoyan.modules", annotationClass = Mapper.class)
@EnableConfigurationProperties(MinioProperties.class)
public class KaoyanApplication {

    public static void main(String[] args) {
        SpringApplication.run(KaoyanApplication.class, args);
    }
}
