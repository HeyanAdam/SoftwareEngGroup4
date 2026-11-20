package com.group4.kaoyan.modules.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.group4.kaoyan.common.R;
import com.group4.kaoyan.modules.audit.annotation.AuditLog;
import com.group4.kaoyan.modules.knowledge.entity.KbDocument;
import com.group4.kaoyan.modules.knowledge.entity.KbKnowledgeBase;
import com.group4.kaoyan.modules.knowledge.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库与文档（对齐《接口设计文档 V1.1 适配版》第 11 章）。
 *
 * <p>路径口径按文档统一为 <b>/api/v1/knowledge-bases</b> 与 <b>/api/v1/knowledge-documents</b>，
 * 此前的 /api/kb、/api/doc 已废弃（P2 改造清单 D 项：/api → /api/v1 统一迁移）。</p>
 *
 * <p>权限码：kb:view / kb:create / kb:delete / doc:upload / doc:delete；
 * 写接口同时挂 {@code @AuditLog} 留痕。</p>
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public record KbCreateReq(String name, String description) {
    }

    @SaCheckPermission("kb:view")
    @GetMapping("/knowledge-bases")
    public R<List<KbKnowledgeBase>> list() {
        return R.ok(knowledgeService.listKnowledgeBases());
    }

    @SaCheckPermission("kb:create")
    @AuditLog(module = "kb", action = "CREATE", resourceType = "KNOWLEDGE_BASE")
    @PostMapping("/knowledge-bases")
    public R<KbKnowledgeBase> create(@RequestBody KbCreateReq req) {
        return R.ok(knowledgeService.create(req.name(), req.description()));
    }

    @SaCheckPermission("kb:view")
    @GetMapping("/knowledge-bases/{id}")
    public R<KbKnowledgeBase> detail(@PathVariable Long id) {
        return R.ok(knowledgeService.getKnowledgeBase(id));
    }

    @SaCheckPermission("kb:delete")
    @AuditLog(module = "kb", action = "DELETE", resourceType = "KNOWLEDGE_BASE")
    @DeleteMapping("/knowledge-bases/{id}")
    public R<Void> delete(@PathVariable Long id) {
        knowledgeService.deleteKnowledgeBase(id);
        return R.ok();
    }

    @SaCheckPermission("doc:upload")
    @AuditLog(module = "kb", action = "UPLOAD_DOCUMENT", resourceType = "DOCUMENT")
    @PostMapping("/knowledge-bases/{id}/documents")
    public R<KbDocument> upload(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return R.ok(knowledgeService.upload(id, file));
    }

    @SaCheckPermission("kb:view")
    @GetMapping("/knowledge-bases/{id}/documents")
    public R<List<KbDocument>> documents(@PathVariable Long id) {
        return R.ok(knowledgeService.listDocuments(id));
    }

    @SaCheckPermission("kb:view")
    @GetMapping("/knowledge-documents/{id}")
    public R<KbDocument> document(@PathVariable Long id) {
        return R.ok(knowledgeService.getDocument(id));
    }

    @SaCheckPermission("doc:delete")
    @AuditLog(module = "kb", action = "DELETE_DOCUMENT", resourceType = "DOCUMENT")
    @DeleteMapping("/knowledge-documents/{id}")
    public R<Void> deleteDocument(@PathVariable Long id) {
        knowledgeService.deleteDocument(id);
        return R.ok();
    }

    @SaCheckPermission("doc:upload")
    @AuditLog(module = "kb", action = "REPARSE_DOCUMENT", resourceType = "DOCUMENT")
    @PostMapping("/knowledge-documents/{id}/reparse")
    public R<Void> reparse(@PathVariable Long id) {
        knowledgeService.reparse(id);
        return R.ok();
    }
}
