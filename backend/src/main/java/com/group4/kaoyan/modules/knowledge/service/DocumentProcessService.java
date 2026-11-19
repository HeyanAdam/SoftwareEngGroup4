package com.group4.kaoyan.modules.knowledge.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.config.AiProperties;
import com.group4.kaoyan.modules.knowledge.entity.KbDocument;
import com.group4.kaoyan.modules.knowledge.entity.KbDocumentChunk;
import com.group4.kaoyan.modules.knowledge.mapper.ChunkMapper;
import com.group4.kaoyan.modules.knowledge.mapper.DocumentMapper;
import com.group4.kaoyan.modules.knowledge.parse.DocumentParserFactory;
import com.group4.kaoyan.modules.knowledge.parse.TextSplitter;
import com.group4.kaoyan.platform.ai.embedding.EmbeddingClient;
import com.group4.kaoyan.platform.ai.vectorstore.VectorStore;
import com.group4.kaoyan.platform.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档处理流水线：解析 → 全文落库 → 切片落库 → 向量化 → 写 Milvus → 置 READY。
 *
 * <p>两个入口：</p>
 * <ul>
 *   <li>{@link #process(Long)}：本地/兼容路径，异步执行，失败自行置 FAILED；</li>
 *   <li>{@link #processInTransaction(Long)}：MQ 消费路径，与任务状态更新同一事务，
 *       失败直接抛出由消费者回滚 + 重试。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessService {

    private final DocumentMapper documentMapper;
    private final ChunkMapper chunkMapper;
    private final StorageService storageService;
    private final DocumentParserFactory parserFactory;
    private final TextSplitter textSplitter;
    private final EmbeddingClient embeddingClient;
    private final VectorStore vectorStore;
    private final AiProperties props;

    @Async("documentExecutor")
    public void process(Long docId) {
        try {
            runPipeline(docId);
        } catch (Exception e) {
            log.error("文档处理失败 docId={}", docId, e);
            updateStatus(docId, "FAILED", brief(e.getMessage()));
        }
    }

    /** MQ 消费入口：整个流水线与任务 SUCCEEDED 在同一事务里，失败整体回滚。 */
    @Transactional(rollbackFor = Exception.class)
    public void processInTransaction(Long docId) {
        runPipeline(docId);
    }

    private void runPipeline(Long docId) {
        KbDocument doc = documentMapper.selectById(docId);
        if (doc == null) {
            throw new BizException(404, "文档不存在：" + docId);
        }
        updateStatus(docId, "PARSING", null);
        String text;
        try (InputStream in = storageService.download(doc.getObjectKey())) {
            text = parserFactory.parse(in, doc.getFileExt());
        } catch (Exception e) {
            throw new BizException("下载或解析文档失败：" + e.getMessage());
        }
        if (text == null || text.isBlank()) {
            throw new BizException("未解析出文本内容，扫描件请先做 OCR");
        }

        KbDocument meta = new KbDocument();
        meta.setId(docId);
        meta.setRawText(text);
        meta.setContentHash(DigestUtil.md5Hex(text));
        meta.setUpdateTime(LocalDateTime.now());
        documentMapper.updateById(meta);

        updateStatus(docId, "SPLITTING", null);
        List<String> pieces = textSplitter.split(text);
        if (pieces.isEmpty()) {
            throw new BizException("切片结果为空");
        }
        chunkMapper.deleteByDocId(docId);
        List<KbDocumentChunk> chunks = new ArrayList<>();
        for (int i = 0; i < pieces.size(); i++) {
            KbDocumentChunk c = new KbDocumentChunk();
            c.setDocId(docId);
            c.setKbId(doc.getKbId());
            c.setUserId(doc.getUserId());
            c.setChunkIndex(i);
            c.setContent(pieces.get(i));
            c.setCharCount(pieces.get(i).length());
            c.setCreateTime(LocalDateTime.now());
            chunkMapper.insert(c);
            c.setMilvusId(c.getId());
            chunkMapper.updateById(c);
            chunks.add(c);
        }

        updateStatus(docId, "EMBEDDING", null);
        embedAndIndex(docId, doc, chunks);

        KbDocument ok = new KbDocument();
        ok.setId(docId);
        ok.setChunkCount(chunks.size());
        ok.setStatus("READY");
        ok.setErrorMsg(null);
        ok.setUpdateTime(LocalDateTime.now());
        documentMapper.updateById(ok);
        log.info("文档处理完成 docId={}，切片数={}", docId, chunks.size());
    }

    private void embedAndIndex(Long docId, KbDocument doc, List<KbDocumentChunk> chunks) {
        int batchSize = Math.max(1, props.getEmbedding().getBatchSize());
        for (int from = 0; from < chunks.size(); from += batchSize) {
            List<KbDocumentChunk> part = chunks.subList(from, Math.min(chunks.size(), from + batchSize));
            List<String> inputs = new ArrayList<>();
            for (KbDocumentChunk c : part) {
                inputs.add(c.getContent());
            }
            List<float[]> vectors = embeddingClient.embed(inputs);
            if (vectors.size() != part.size()) {
                throw new BizException("向量数量与切片数量不一致");
            }
            List<VectorStore.VectorRecord> records = new ArrayList<>();
            for (int i = 0; i < part.size(); i++) {
                KbDocumentChunk c = part.get(i);
                records.add(new VectorStore.VectorRecord(
                        c.getId(), vectors.get(i), c.getId(), docId,
                        c.getKbId(), c.getUserId(), c.getContent(), doc.getOriginalFilename()));
            }
            vectorStore.upsert(records);
            log.debug("已写入 Milvus：docId={}，batch={}-{}", docId, from, from + part.size() - 1);
        }
    }

    private void updateStatus(Long docId, String status, String errorMsg) {
        KbDocument doc = new KbDocument();
        doc.setId(docId);
        doc.setStatus(status);
        doc.setErrorMsg(errorMsg);
        doc.setUpdateTime(LocalDateTime.now());
        documentMapper.updateById(doc);
    }

    private static String brief(String msg) {
        if (msg == null) {
            return "未知错误";
        }
        return msg.length() <= 900 ? msg : msg.substring(0, 900);
    }
}
