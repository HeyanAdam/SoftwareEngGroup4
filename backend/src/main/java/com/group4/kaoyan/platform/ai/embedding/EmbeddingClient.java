package com.group4.kaoyan.platform.ai.embedding;

import java.util.List;

/**
 * 文本向量化客户端（OpenAI 兼容 /embeddings）。
 */
public interface EmbeddingClient {

    List<float[]> embed(List<String> texts);

    float[] embedOne(String text);
}
