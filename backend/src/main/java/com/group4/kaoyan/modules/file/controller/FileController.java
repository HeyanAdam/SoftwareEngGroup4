package com.group4.kaoyan.modules.file.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.group4.kaoyan.common.R;
import com.group4.kaoyan.common.web.RequestContext;
import com.group4.kaoyan.modules.audit.annotation.AuditLog;
import com.group4.kaoyan.modules.file.entity.FileObject;
import com.group4.kaoyan.modules.file.entity.FileUploadSession;
import com.group4.kaoyan.modules.file.service.FileService;
import com.group4.kaoyan.platform.idempotency.Idempotent;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 文件接口，对齐《接口设计文档 V1.1》第 10 章。
 * 归属用户一律取登录态，不接受前端传 userId。
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    public record InitSessionReq(String filename, Long fileSize, String contentType,
                                 Integer partSize, String fileHash, String scene) {
    }

    public record IdsReq(List<Long> fileIds) {
    }

    // ============ 小文件 ============

    @AuditLog(module = "file", action = "UPLOAD", resourceType = "FILE")
    @PostMapping
    public R<FileObject> upload(@RequestParam("file") MultipartFile file,
                                @RequestParam(defaultValue = "STUDY_MATERIAL") String scene) {
        return R.ok(fileService.upload(userId(), scene, file));
    }

    @AuditLog(module = "file", action = "UPLOAD_BATCH", resourceType = "FILE")
    @PostMapping("/upload")
    public R<List<FileObject>> uploadBatch(@RequestPart("files") MultipartFile[] files,
                                           @RequestParam(defaultValue = "STUDY_MATERIAL") String scene) {
        return R.ok(fileService.uploadBatch(userId(), scene, files));
    }

    @PostMapping("/batch")
    public R<List<FileObject>> batch(@RequestBody IdsReq req) {
        return R.ok(fileService.batch(userId(), req.fileIds()));
    }

    // ============ 分片 ============

    @Idempotent
    @AuditLog(module = "file", action = "INIT_UPLOAD_SESSION", resourceType = "FILE")
    @PostMapping("/upload-sessions")
    public R<FileUploadSession> initSession(@RequestBody InitSessionReq req) {
        return R.ok(fileService.initSession(userId(), new FileService.InitSessionCommand(
                req.filename(), req.fileSize() == null ? 0 : req.fileSize(),
                req.contentType(), req.partSize(), req.fileHash(), req.scene())));
    }

    @GetMapping("/upload-sessions/{sessionId}")
    public R<FileUploadSession> session(@PathVariable Long sessionId) {
        return R.ok(fileService.getSession(userId(), sessionId));
    }

    @AuditLog(module = "file", action = "UPLOAD_PART", resourceType = "FILE")
    @PutMapping("/upload-sessions/{sessionId}/parts/{partNumber}")
    public R<FileUploadSession> uploadPart(@PathVariable Long sessionId,
                                           @PathVariable Integer partNumber,
                                           @RequestPart("part") MultipartFile part,
                                           @RequestHeader(value = "X-Part-Hash", required = false) String partHash) {
        return R.ok(fileService.uploadPart(userId(), sessionId, partNumber, part,
                RequestContext.truncate(partHash, 64)));
    }

    @AuditLog(module = "file", action = "COMPLETE_UPLOAD_SESSION", resourceType = "FILE")
    @PostMapping("/upload-sessions/{sessionId}/complete")
    public R<FileObject> complete(@PathVariable Long sessionId,
                                  @RequestParam(defaultValue = "STUDY_MATERIAL") String scene) {
        return R.ok(fileService.complete(userId(), sessionId, scene));
    }

    @AuditLog(module = "file", action = "CANCEL_UPLOAD_SESSION", resourceType = "FILE")
    @DeleteMapping("/upload-sessions/{sessionId}")
    public R<Void> cancel(@PathVariable Long sessionId) {
        fileService.cancelSession(userId(), sessionId);
        return R.ok();
    }

    // ============ 列表 / 下载 / 删除 ============

    @GetMapping
    public R<R.PageData<FileObject>> list(@RequestParam(required = false) String scene,
                                          @RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "10") long size) {
        return R.ok(fileService.list(userId(), scene, page, size));
    }

    @GetMapping("/{fileId}")
    public R<FileObject> detail(@PathVariable Long fileId) {
        return R.ok(fileService.getOwned(userId(), fileId));
    }

    /** 返回 60 分钟有效的预签名下载地址，不暴露 MinIO 内部地址与错误。 */
    @GetMapping("/{fileId}/content")
    public R<Map<String, Object>> content(@PathVariable Long fileId) {
        return R.ok(Map.of(
                "url", fileService.downloadUrl(userId(), fileId),
                "expiresInMinutes", 60));
    }

    @AuditLog(module = "file", action = "DELETE", resourceType = "FILE")
    @DeleteMapping("/{fileId}")
    public R<Void> delete(@PathVariable Long fileId) {
        fileService.delete(userId(), fileId);
        return R.ok();
    }

    private Long userId() {
        return StpUtil.getLoginIdAsLong();
    }
}
