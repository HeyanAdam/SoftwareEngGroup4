package com.group4.kaoyan.platform.ai.vectorstore;

import java.util.List;

/**
 * 向量库抽象。业务侧只依赖这个接口，未来换 Qdrant/PGVector 不影响上层。
 */
public interface VectorStore {

    record VectorRecord(long id, float[] vector, long chunkId, long docId,
                        long kbId, long userId, String content, String sourceFilename) {
    }

    record Hit(long chunkId, long docId, long kbId, String content,
               String sourceFilename, float score) {
    }

    void ensureCollection();

    void upsert(List<VectorRecord> records);

    /** expr 为 Milvus 布尔表达式，例如 user_id == 1 && kb_id == 2 */
    List<Hit> search(float[] query, String expr, int topK);

    void deleteByDocId(long docId);

    void deleteByKbId(long kbId);
}
