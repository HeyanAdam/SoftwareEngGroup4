package com.group4.kaoyan.common.config;

import com.group4.kaoyan.common.R;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@RestController("legacyHealthController")
@RequestMapping("/health")
public class LegacyHealthController {

    @Resource
    private MinioClient minioClient;

    @GetMapping
    public R<Map<String,Object>> healthCheck(){
        Map<String,Object> result = new HashMap<>();
        result.put("server", "UP");
        try {
            minioClient.listBuckets();
            result.put("minio", "UP");
        } catch (MinioException e) {
            result.put("minio", "DOWN");
            result.put("minioError", e.getMessage());
        } catch (Exception e){
            result.put("minio", "DOWN");
            result.put("minioError", e.getMessage());
        }
        return R.ok(result);
    }
}
