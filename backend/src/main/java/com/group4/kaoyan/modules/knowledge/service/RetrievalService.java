package com.group4.kaoyan.modules.knowledge.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.config.AiProperties;
import com.group4.kaoyan.modules.knowledge.entity.KbKnowledgeBase;
import com.group4.kaoyan.modules.knowledge.mapper.KbMapper;
import com.group4.kaoyan.platform.ai.embedding.EmbeddingClient;
import com.group4.kaoyan.platform.ai.vectorstore.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 检索：问题向量化 → Milvus TopK → 按“知识库可见性”过滤 → 阈值过滤。
 *
 * 数据隔离口径：从“按上传者 user_id”改为“按知识库 kb_id 集合”。
 * 原因：普通用户只读，管理员建的公开库必须能被检索到，user_id 过滤会把人挡在外面。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private final EmbeddingClient embeddingClient;
    private final VectorStore vectorStore;
    private final KbMapper kbMapper;
    private final AiProperties props;

    public record Retrieved(String docName, long chunkId, long docId, String content, float score) {
    }

    public List<Retrieved> retrieve(Long kbId, String query) {
        String expr = buildExpr(kbId);
        if (expr == null) {
            return List.of();
        }
        float[] vector = embeddingClient.embedOne(query);
        double threshold = props.getMilvus().getScoreThreshold();
        return vectorStore.search(vector, expr, props.getMilvus().getTopK()).stream()
                .filter(h -> h.score() >= threshold)
                .map(h -> new Retrieved(h.sourceFilename(), h.chunkId(), h.docId(), h.content(), h.score()))
                .toList();
    }

    /** 返回 null 表示“当前用户没有可见知识库”，直接跳过检索 */
    private String buildExpr(Long kbId) {
        if (StpUtil.hasRole("ADMIN")) {
            if (kbId == null) {
                return "id >= 0";
            }
            if (kbMapper.selectById(kbId) == null) {
                throw new BizException(404, "知识库不存在");
            }
            return "kb_id == " + kbId;
        }
        // 普通用户：不认 kbId（无权知道库结构，前端传了也忽略），统一检索公开库
        List<KbKnowledgeBase> publicKbs = kbMapper.selectList(Wrappers.<KbKnowledgeBase>lambdaQuery()
                .eq(KbKnowledgeBase::getVisibility, KnowledgeService.VISIBILITY_PUBLIC));
        if (publicKbs.isEmpty()) {
            return null;
        }
        return "kb_id in [" + publicKbs.stream()
                .map(k -> k.getId().toString())
                .collect(Collectors.joining(", ")) + "]";
    }

    /** 把命中片段拼成带编号的上下文，供 Prompt 使用 */
    public String buildContext(List<Retrieved> items) {
        if (items == null || items.isEmpty()) {
            return "（未检索到相关片段）";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            Retrieved r = items.get(i);
            sb.append('[').append(i + 1).append("] 来源《").append(r.docName()).append("》\n")
                    .append(r.content()).append("\n\n");
        }
        return sb.toString();
    }
}
