package com.group4.kaoyan.modules.knowledge.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.security.RoleConstants;
import com.group4.kaoyan.modules.knowledge.entity.KbDocument;
import com.group4.kaoyan.modules.knowledge.entity.KbKnowledgeBase;
import com.group4.kaoyan.modules.knowledge.mapper.ChunkMapper;
import com.group4.kaoyan.modules.knowledge.mapper.DocumentMapper;
import com.group4.kaoyan.modules.knowledge.mapper.KbMapper;
import com.group4.kaoyan.platform.ai.vectorstore.VectorStore;
import com.group4.kaoyan.platform.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 知识库与文档。
 *
 * 权限模型：
 * - 写（建库、上传、删除、重解析、重建向量）：SYSTEM_ADMIN / CONTENT_OPERATOR，
 *   接口层 @SaCheckPermission + 此处二次校验；
 * - 读（列表、详情、文档列表、原文）：SYSTEM_ADMIN / CONTENT_OPERATOR / DATA_AUDITOR。
 * 这样普通用户永远不会触达 Milvus 的写操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    public static final int VISIBILITY_PUBLIC = 1;
    public static final int VISIBILITY_PRIVATE = 0;

    private final KbMapper kbMapper;
    private final DocumentMapper documentMapper;
    private final ChunkMapper chunkMapper;
    private final StorageService storageService;
    private final VectorStore vectorStore;
    private final DocumentProcessService documentProcessService;

    // ============ 知识库 ============

    /** 建库：管理员操作，且默认设为公开，普通用户才能检索到 */
    @Transactional
    public KbKnowledgeBase create(String name, String description) {
        requireContentWrite("只有系统管理员或内容运营可以创建知识库");
        if (name == null || name.isBlank()) {
            throw new BizException("知识库名称不能为空");
        }
        KbKnowledgeBase kb = new KbKnowledgeBase();
        kb.setName(name.trim());
        kb.setDescription(description);
        kb.setUserId(currentUserId());
        kb.setVisibility(VISIBILITY_PUBLIC);
        kb.setDocCount(0);
        kb.setStatus(1);
        kb.setCreateTime(LocalDateTime.now());
        kb.setUpdateTime(LocalDateTime.now());
        kbMapper.insert(kb);
        return kb;
    }

    /** 列表：后台管理能力，仅管理员；普通用户只能通过问答使用数据，看不到库结构 */
    public List<KbKnowledgeBase> listKnowledgeBases() {
        requireContentRead("当前角色不可浏览知识库");
        return kbMapper.selectList(Wrappers.<KbKnowledgeBase>lambdaQuery()
                .orderByDesc(KbKnowledgeBase::getId));
    }

    public KbKnowledgeBase getKnowledgeBase(Long id) {
        requireContentRead("当前角色不可访问知识库");
        return requireKb(id);
    }

    @Transactional
    public void deleteKnowledgeBase(Long id) {
        requireContentWrite("只有系统管理员或内容运营可以删除知识库");
        KbKnowledgeBase kb = requireKb(id);
        List<KbDocument> docs = documentMapper.selectList(Wrappers.<KbDocument>lambdaQuery()
                .eq(KbDocument::getKbId, kb.getId()));
        for (KbDocument doc : docs) {
            vectorStore.deleteByDocId(doc.getId());
            if (doc.getObjectKey() != null) {
                storageService.delete(doc.getObjectKey());
            }
            chunkMapper.deleteByDocId(doc.getId());
        }
        documentMapper.delete(Wrappers.<KbDocument>lambdaQuery().eq(KbDocument::getKbId, kb.getId()));
        vectorStore.deleteByKbId(kb.getId());
        kbMapper.deleteById(kb.getId());
    }

    // ============ 文档 ============

    public KbDocument upload(Long kbId, MultipartFile file) {
        requireContentWrite("只有系统管理员或内容运营可以上传文档");
        KbKnowledgeBase kb = requireKb(kbId);
        if (file == null || file.isEmpty()) {
            throw new BizException("上传文件为空");
        }
        String original = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
        String ext = StringUtils.getFilenameExtension(original);
        String objectKey = kb.getUserId() + "/" + kbId + "/" + UUID.randomUUID()
                + (ext == null ? "" : "." + ext);
        try (InputStream in = file.getInputStream()) {
            storageService.upload(objectKey, in, file.getSize(), file.getContentType());
        } catch (IOException e) {
            throw new BizException(500, "读取上传文件失败：" + e.getMessage());
        }

        KbDocument doc = new KbDocument();
        doc.setKbId(kbId);
        doc.setUserId(currentUserId());
        doc.setOriginalFilename(original);
        doc.setFileExt(ext);
        doc.setFileSize(file.getSize());
        doc.setObjectKey(objectKey);
        doc.setChunkCount(0);
        doc.setStatus("UPLOADED");
        doc.setCreateTime(LocalDateTime.now());
        doc.setUpdateTime(LocalDateTime.now());
        documentMapper.insert(doc);

        kbMapper.updateById(incDocCount(kbId, 1));
        documentProcessService.process(doc.getId());
        return doc;
    }

    public List<KbDocument> listDocuments(Long kbId) {
        requireContentRead("当前角色不可浏览文档列表");
        requireKb(kbId);
        return documentMapper.selectList(Wrappers.<KbDocument>lambdaQuery()
                .select(KbDocument::getId, KbDocument::getKbId, KbDocument::getOriginalFilename,
                        KbDocument::getFileExt, KbDocument::getFileSize, KbDocument::getChunkCount,
                        KbDocument::getStatus, KbDocument::getErrorMsg, KbDocument::getCreateTime)
                .eq(KbDocument::getKbId, kbId)
                .orderByDesc(KbDocument::getId));
    }

    /** 详情含原文全文：仅管理员可见（普通用户拿不到库里的原文） */
    public KbDocument getDocument(Long docId) {
        requireContentRead("当前角色不可查看文档原文");
        KbDocument doc = documentMapper.selectById(docId);
        if (doc == null) {
            throw new BizException(404, "文档不存在");
        }
        return doc;
    }

    @Transactional
    public void deleteDocument(Long docId) {
        requireContentWrite("只有系统管理员或内容运营可以删除文档");
        KbDocument doc = getDocument(docId);
        vectorStore.deleteByDocId(docId);
        if (doc.getObjectKey() != null) {
            storageService.delete(doc.getObjectKey());
        }
        chunkMapper.deleteByDocId(docId);
        documentMapper.deleteById(docId);
        kbMapper.updateById(incDocCount(doc.getKbId(), -1));
    }

    /** 重新解析单个文档：清掉旧切片与向量，回到 UPLOADED 再跑一次流水线 */
    public void reparse(Long docId) {
        requireContentWrite("只有系统管理员或内容运营可以重建文档向量");
        KbDocument doc = getDocument(docId);
        resetDoc(docId);
        documentProcessService.process(docId);
        log.info("文档已提交重建：docId={}, kbId={}", docId, doc.getKbId());
    }

    /**
     * 重建整个知识库的向量（管理员修复用）：逐文档清空后重新入队。
     * 不加 @Transactional：清理要先于异步任务可见，否则异步线程可能读到未提交状态。
     */
    public int reindexKnowledgeBase(Long kbId) {
        requireContentWrite("只有系统管理员或内容运营可以重建知识库向量");
        requireKb(kbId);
        List<KbDocument> docs = documentMapper.selectList(Wrappers.<KbDocument>lambdaQuery()
                .eq(KbDocument::getKbId, kbId));
        for (KbDocument doc : docs) {
            resetDoc(doc.getId());
            documentProcessService.process(doc.getId());
        }
        log.info("知识库重建已提交：kbId={}，文档数={}", kbId, docs.size());
        return docs.size();
    }

    // ============ 内部 ============

    private void resetDoc(Long docId) {
        vectorStore.deleteByDocId(docId);
        chunkMapper.deleteByDocId(docId);
        KbDocument upd = new KbDocument();
        upd.setId(docId);
        upd.setStatus("UPLOADED");
        upd.setErrorMsg(null);
        upd.setChunkCount(0);
        upd.setUpdateTime(LocalDateTime.now());
        documentMapper.updateById(upd);
    }

    /** 知识库读取：仅管理员；普通用户没有任何浏览入口 */
    private KbKnowledgeBase requireKb(Long kbId) {
        KbKnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null) {
            throw new BizException(404, "知识库不存在");
        }
        return kb;
    }

    private void requireContentRead(String message) {
        if (!hasAnyContentRole()) {
            throw new BizException(403, message);
        }
    }

    private void requireContentWrite(String message) {
        if (!StpUtil.hasRole(RoleConstants.SYSTEM_ADMIN)
                && !StpUtil.hasRole(RoleConstants.CONTENT_OPERATOR)) {
            throw new BizException(403, message);
        }
    }

    private KbKnowledgeBase incDocCount(Long kbId, int delta) {
        KbKnowledgeBase kb = kbMapper.selectById(kbId);
        int count = kb == null || kb.getDocCount() == null ? 0 : kb.getDocCount();
        KbKnowledgeBase upd = new KbKnowledgeBase();
        upd.setId(kbId);
        upd.setDocCount(Math.max(0, count + delta));
        upd.setUpdateTime(LocalDateTime.now());
        return upd;
    }

    private Long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    private boolean hasAnyContentRole() {
        return StpUtil.hasRole(RoleConstants.SYSTEM_ADMIN)
                || StpUtil.hasRole(RoleConstants.CONTENT_OPERATOR)
                || StpUtil.hasRole(RoleConstants.DATA_AUDITOR);
    }
}
