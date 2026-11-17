package com.group4.kaoyan.platform.ai.vectorstore;

import com.group4.kaoyan.common.config.AiProperties;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.DescribeCollectionResponse;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.CollectionSchemaParam;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.DescribeCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.FlushParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MilvusVectorStore implements VectorStore, InitializingBean, DisposableBean {

    private final AiProperties props;
    private MilvusServiceClient client;

    @Override
    public void afterPropertiesSet() {
        // 跳过Milvus连接初始化，避免启动卡住
    /*
    AiProperties.Milvus cfg = props.getMilvus();
    try {
        client = new MilvusServiceClient(ConnectParam.newBuilder()
                .withHost(cfg.getHost())
                .withPort(cfg.getPort())
                .build());
        ensureCollection();
        log.info("Milvus 已连接 {}:{}，collection={}，维度={}",
                cfg.getHost(), cfg.getPort(), cfg.getCollection(), cfg.getDimension());
    } catch (Exception e) {
        log.warn("Milvus 初始化失败，向量检索暂不可用：{}", e.getMessage());
    }
    */
    }


    @Override
    public void destroy() {
        if (client != null) {
            client.close();
        }
    }

    @Override
    public void ensureCollection() {
        AiProperties.Milvus cfg = props.getMilvus();
        if (exists(cfg.getCollection())) {
            load(cfg.getCollection());
            return;
        }
        CollectionSchemaParam schema = CollectionSchemaParam.newBuilder()
                .addFieldType(field("id", DataType.Int64, true))
                .addFieldType(FieldType.newBuilder().withName("vector")
                        .withDataType(DataType.FloatVector).withDimension(cfg.getDimension()).build())
                .addFieldType(field("chunk_id", DataType.Int64, false))
                .addFieldType(field("doc_id", DataType.Int64, false))
                .addFieldType(field("kb_id", DataType.Int64, false))
                .addFieldType(field("user_id", DataType.Int64, false))
                .addFieldType(FieldType.newBuilder().withName("content")
                        .withDataType(DataType.VarChar).withMaxLength(4096).build())
                .addFieldType(FieldType.newBuilder().withName("source_filename")
                        .withDataType(DataType.VarChar).withMaxLength(512).build())
                .build();
        client.createCollection(CreateCollectionParam.newBuilder()
                .withCollectionName(cfg.getCollection())
                .withSchema(schema)
                .withShardsNum(1)
                .build());
        client.createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(cfg.getCollection())
                .withFieldName("vector")
                .withIndexType(IndexType.HNSW)
                .withMetricType(MetricType.COSINE)
                .withExtraParam("{\"M\":16,\"efConstruction\":200}")
                .build());
        load(cfg.getCollection());
        log.info("已创建 Milvus collection {}（维度 {}）", cfg.getCollection(), cfg.getDimension());
    }

    private FieldType field(String name, DataType type, boolean pk) {
        return FieldType.newBuilder().withName(name).withDataType(type).withPrimaryKey(pk).build();
    }

    private boolean exists(String collection) {
        try {
            R<DescribeCollectionResponse> resp = client.describeCollection(
                    DescribeCollectionParam.newBuilder().withCollectionName(collection).build());
            return resp.getData() != null && resp.getData().getSchema() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private void load(String collection) {
        client.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(collection).build());
    }

    @Override
    public void upsert(List<VectorRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        String collection = props.getMilvus().getCollection();
        List<Long> ids = new ArrayList<>();
        List<List<Float>> vectors = new ArrayList<>();
        List<Long> chunkIds = new ArrayList<>();
        List<Long> docIds = new ArrayList<>();
        List<Long> kbIds = new ArrayList<>();
        List<Long> userIds = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        List<String> filenames = new ArrayList<>();

        for (VectorRecord r : records) {
            ids.add(r.id());
            vectors.add(toList(r.vector()));
            chunkIds.add(r.chunkId());
            docIds.add(r.docId());
            kbIds.add(r.kbId());
            userIds.add(r.userId());
            contents.add(truncate(r.content(), 4000));
            filenames.add(truncate(r.sourceFilename(), 500));
        }

        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field("id", ids));
        fields.add(new InsertParam.Field("vector", vectors));
        fields.add(new InsertParam.Field("chunk_id", chunkIds));
        fields.add(new InsertParam.Field("doc_id", docIds));
        fields.add(new InsertParam.Field("kb_id", kbIds));
        fields.add(new InsertParam.Field("user_id", userIds));
        fields.add(new InsertParam.Field("content", contents));
        fields.add(new InsertParam.Field("source_filename", filenames));

        client.insert(InsertParam.newBuilder()
                .withCollectionName(collection)
                .withFields(fields)
                .build());
        client.flush(FlushParam.newBuilder()
                .withCollectionNames(List.of(collection))
                .build());
    }

    @Override
    public List<Hit> search(float[] query, String expr, int topK) {
        if (client == null || query == null || query.length == 0) {
            return List.of();
        }
        try {
            SearchParam param = SearchParam.newBuilder()
                    .withCollectionName(props.getMilvus().getCollection())
                    .withMetricType(MetricType.COSINE)
                    .withOutFields(Arrays.asList("chunk_id", "doc_id", "kb_id", "content", "source_filename"))
                    .withTopK(topK)
                    .withVectors(List.of(toList(query)))
                    .withVectorFieldName("vector")
                    .withExpr(expr)
                    .build();
            R<io.milvus.grpc.SearchResults> resp = client.search(param);
            if (resp.getData() == null) {
                return List.of();
            }
            return parseHits(new SearchResultsWrapper(resp.getData().getResults()));
        } catch (Exception e) {
            log.warn("Milvus 检索失败：{}", e.getMessage());
            return List.of();
        }
    }

    private List<Hit> parseHits(SearchResultsWrapper wrapper) {
        List<Hit> hits = new ArrayList<>();
        List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);
        List<?> chunkIdCol = wrapper.getFieldData("chunk_id", 0);
        List<?> docIdCol = wrapper.getFieldData("doc_id", 0);
        List<?> kbIdCol = wrapper.getFieldData("kb_id", 0);
        List<?> contentCol = wrapper.getFieldData("content", 0);
        List<?> nameCol = wrapper.getFieldData("source_filename", 0);
        for (int i = 0; i < scores.size(); i++) {
            hits.add(new Hit(
                    toLong(chunkIdCol, i),
                    toLong(docIdCol, i),
                    toLong(kbIdCol, i),
                    toStringValue(contentCol, i),
                    toStringValue(nameCol, i),
                    scores.get(i).getScore()));
        }
        return hits;
    }

    @Override
    public void deleteByDocId(long docId) {
        deleteByExpr("doc_id == " + docId);
    }

    @Override
    public void deleteByKbId(long kbId) {
        deleteByExpr("kb_id == " + kbId);
    }

    private void deleteByExpr(String expr) {
        if (client == null) {
            return;
        }
        try {
            client.delete(DeleteParam.newBuilder()
                    .withCollectionName(props.getMilvus().getCollection())
                    .withExpr(expr)
                    .build());
        } catch (Exception e) {
            log.warn("Milvus 删除失败（{}）：{}", expr, e.getMessage());
        }
    }

    private static List<Float> toList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float v : vector) {
            list.add(v);
        }
        return list;
    }

    private static long toLong(List<?> col, int i) {
        if (col == null || i >= col.size() || col.get(i) == null) {
            return 0L;
        }
        Object v = col.get(i);
        return v instanceof Number n ? n.longValue() : Long.parseLong(v.toString());
    }

    private static String toStringValue(List<?> col, int i) {
        if (col == null || i >= col.size() || col.get(i) == null) {
            return "";
        }
        return col.get(i).toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
