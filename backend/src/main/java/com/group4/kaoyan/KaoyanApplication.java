package com.group4.kaoyan;

import com.group4.kaoyan.platform.storage.MinioProperties;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import com.group4.kaoyan.platform.storage.MinioProperties;

@SpringBootApplication
@EnableAsync
// 只扫带 @Mapper 的接口：modules 包下还有业务接口（如 EmailSender），
// 按包扫描会把它们一起注册成 MyBatis 代理，启动后调用报 Invalid bound statement。
@MapperScan(value = "com.group4.kaoyan.modules", annotationClass = Mapper.class)
@EnableConfigurationProperties(MinioProperties.class)
public class KaoyanApplication {

    public static void main(String[] args) {
        SpringApplication.run(KaoyanApplication.class, args);
    }
}
