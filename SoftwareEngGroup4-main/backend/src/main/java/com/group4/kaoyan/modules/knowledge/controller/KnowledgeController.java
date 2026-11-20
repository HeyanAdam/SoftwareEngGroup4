package com.group4.kaoyan.modules.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.group4.kaoyan.common.R;
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

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public record KbCreateReq(String name, String description) {
    }

    @SaCheckPermission("kb:view")
    @GetMapping("/kb")
    public R<List<KbKnowledgeBase>> list() {
        return R.ok(knowledgeService.listKnowledgeBases());
    }

    @SaCheckPermission("kb:create")
    @PostMapping("/kb")
    public R<KbKnowledgeBase> create(@RequestBody KbCreateReq req) {
        return R.ok(knowledgeService.create(req.name(), req.description()));
    }

    @SaCheckPermission("kb:view")
    @GetMapping("/kb/{id}")
    public R<KbKnowledgeBase> detail(@PathVariable Long id) {
        return R.ok(knowledgeService.getKnowledgeBase(id));
    }

    @SaCheckPermission("kb:delete")
    @DeleteMapping("/kb/{id}")
    public R<Void> delete(@PathVariable Long id) {
        knowledgeService.deleteKnowledgeBase(id);
        return R.ok();
    }

    @SaCheckPermission("doc:upload")
    @PostMapping("/kb/{id}/documents")
    public R<KbDocument> upload(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return R.ok(knowledgeService.upload(id, file));
    }

    @SaCheckPermission("kb:view")
    @GetMapping("/kb/{id}/documents")
    public R<List<KbDocument>> documents(@PathVariable Long id) {
        return R.ok(knowledgeService.listDocuments(id));
    }

    @SaCheckPermission("kb:view")
    @GetMapping("/doc/{id}")
    public R<KbDocument> document(@PathVariable Long id) {
        return R.ok(knowledgeService.getDocument(id));
    }

    @SaCheckPermission("doc:delete")
    @DeleteMapping("/doc/{id}")
    public R<Void> deleteDocument(@PathVariable Long id) {
        knowledgeService.deleteDocument(id);
        return R.ok();
    }

    @SaCheckPermission("doc:upload")
    @PostMapping("/doc/{id}/reparse")
    public R<Void> reparse(@PathVariable Long id) {
        knowledgeService.reparse(id);
        return R.ok();
    }
}
