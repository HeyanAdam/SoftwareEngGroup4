package com.group4.kaoyan.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 全部可配置项集中在此，对应 application.yml 的 kaoyan.* 节点。
 * 所有值都可用环境变量覆盖，代码里不出现地址、密钥、模型名。
 */
@Data
@Component
@ConfigurationProperties(prefix = "kaoyan")
public class AiProperties {

    private Llm llm = new Llm();
    private Embedding embedding = new Embedding();
    private Milvus milvus = new Milvus();
    private Storage storage = new Storage();
    private Rag rag = new Rag();

    /**
     * 对话生成模型配置：DeepSeek（OpenAI 兼容 /chat/completions）。
     * 只负责把 Prompt 生成回答，不负责向量化。
     */
    @Data
    public static class Llm {
        private String baseUrl = "https://api.deepseek.com/v1";
        private String apiKey = "";
        private String model = "deepseek-chat";
        private double temperature = 0.3;
        private int maxTokens = 2048;
        /** 兼容旧配置：未显式配置读取超时时，回落到该值 */
        private int timeoutSeconds = 60;
        /** 建连超时（秒），独立于读取超时 */
        private int connectTimeoutSeconds = 10;
        /** 流式读取超时（秒）：两段增量之间允许的最长静默 */
        private int readTimeoutSeconds = 60;
    }

    /**
     * 向量化模型配置：千问 Embedding（DashScope 的 OpenAI 兼容 /embeddings）。
     * 只输出向量、不面对用户；dimension 必须与 Milvus collection 维度一致。
     * 这里的默认值仅在配置缺失时生效，实际部署以 application.yml / .env 为准。
     */
    @Data
    public static class Embedding {
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        private String apiKey = "";
        private String model = "text-embedding-v3";
        private int dimension = 1024;
        private int batchSize = 16;
        private int timeoutSeconds = 30;
    }

    @Data
    public static class Milvus {
        private String host = "localhost";
        private int port = 19530;
        private String collection = "doc_chunk";
        private int dimension = 1024;
        private int topK = 6;
        private double scoreThreshold = 0.3;
    }

    @Data
    public static class Storage {
        private String endpoint = "http://localhost:9000";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private String bucket = "kaoyan";
    }

    @Data
    public static class Rag {
        private int chunkSize = 400;
        private int chunkOverlap = 80;
        private int historyRounds = 6;
    }
}
