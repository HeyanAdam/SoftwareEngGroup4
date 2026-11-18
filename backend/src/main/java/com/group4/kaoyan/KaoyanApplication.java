package com.group4.kaoyan;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import com.group4.kaoyan.platform.storage.MinioProperties;

@SpringBootApplication
//@EnableAsync
@EnableConfigurationProperties(MinioProperties.class)
public class KaoyanApplication {
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@MapperScan("com.group4.kaoyan.modules")
@SpringBootApplication
public class KaoyanApplication {
    public static void main(String[] args) {
        SpringApplication.run(KaoyanApplication.class, args);
    }
}
