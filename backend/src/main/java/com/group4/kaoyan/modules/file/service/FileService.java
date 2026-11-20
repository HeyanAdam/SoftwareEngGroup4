package com.group4.kaoyan.modules.file.service;

import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.R;
import com.group4.kaoyan.common.mybatis.EntityTimestamps;
import com.group4.kaoyan.common.mybatis.PageQuery;
import com.group4.kaoyan.modules.file.entity.FileObject;
import com.group4.kaoyan.modules.file.entity.FileUploadPart;
import com.group4.kaoyan.modules.file.entity.FileUploadSession;
import com.group4.kaoyan.modules.file.mapper.FileObjectMapper;
import com.group4.kaoyan.modules.file.mapper.UploadPartMapper;
import com.group4.kaoyan.modules.file.mapper.UploadSessionMapper;
import com.group4.kaoyan.platform.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 文件持久化：小文件直传、秒传、分片上传、下载与删除。
 *
 * <p>一致性顺序统一为「先落对象再写库」，写库失败时删对象，绝不出现"有记录没文件"。
 * 分片会话尚未完成时，{@code object_key} 列临时存分片前缀（tmp/{scene}/{userId}/{uuid}），
 * 合并成功后再替换为正式对象键。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    public static final Set<String> SCENES = Set.of(
            "AVATAR", "QUESTION_IMAGE", "STUDY_MATERIAL", "KNOWLEDGE_DOCUMENT", "CHAT_ATTACHMENT");

    private static final long MAX_DIRECT_SIZE = 50L * 1024 * 1024;
    private static final int DEFAULT_PART_SIZE = 5 * 1024 * 1024;
    private static final int MIN_PART_SIZE = 1024 * 1024;
    private static final int MAX_PART_SIZE = 100 * 1024 * 1024;
    private static final int MAX_PART_COUNT = 10000;
    private static final int DOWNLOAD_EXPIRE_MINUTES = 60;

    private final FileObjectMapper fileObjectMapper;
    private final UploadSessionMapper sessionMapper;
    private final UploadPartMapper partMapper;
    private final StorageService storageService;

    // ============ 小文件直传 / 秒传 ============

    public FileObject upload(Long userId, String scene, MultipartFile file) {
        requireScene(scene);
        if (file == null || file.isEmpty()) {
            throw new BizException("上传文件为空");
        }
        if (file.getSize() > MAX_DIRECT_SIZE) {
            throw new BizException(413, "文件超过 50MB，请使用分片上传");
        }
        String filename = safeFilename(file.getOriginalFilename());
        String hash = sha256Of(file);

        // 秒传：同用户同内容已有 READY 文件，直接复用元数据，不再写对象
        if (hash != null) {
            FileObject existing = fileObjectMapper.selectByOwnerAndHash(userId, hash);
            if (existing != null) {
                log.info("秒传命中 userId={} fileId={}", userId, existing.getId());
                return existing;
            }
        }

        String ext = extensionOf(filename);
        String objectKey = buildObjectKey(scene, userId, ext);
        String contentType = file.getContentType() == null
                ? "application/octet-stream" : file.getContentType();

        // 先对象后记录：顺序不能反，否则会出现"有记录没文件"
        try (InputStream in = file.getInputStream()) {
            storageService.upload(objectKey, in, file.getSize(), contentType);
        } catch (IOException e) {
            throw new BizException(500, "读取上传文件失败：" + e.getMessage());
        } catch (RuntimeException e) {
            throw new BizException(500, "文件存储失败：" + e.getMessage());
        }

        FileObject record = new FileObject();
        record.setOwnerUserId(userId);
        record.setScene(scene);
        record.setBucketName(bucketName(objectKey));
        record.setObjectKey(objectKey);
        record.setOriginalFilename(filename);
        record.setFileExt(ext);
        record.setContentType(contentType);
        record.setFileSize(file.getSize());
        record.setSha256(hash);
        record.setStatus("READY");
        record.setDeleted(0);
        EntityTimestamps.fillOnInsert(record);
        try {
            fileObjectMapper.insert(record);
        } catch (RuntimeException e) {
            storageService.delete(objectKey);
            throw new BizException(500, "文件元数据写入失败：" + e.getMessage());
        }
        return record;
    }

    public List<FileObject> uploadBatch(Long userId, String scene, MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new BizException("上传文件为空");
        }
        List<FileObject> result = new ArrayList<>();
        for (MultipartFile file : files) {
            result.add(upload(userId, scene, file));
        }
        return result;
    }

    // ============ 分片上传 ============

    public record InitSessionCommand(String filename, long fileSize, String contentType,
                                     Integer partSize, String fileHash, String scene) {
    }

    public FileUploadSession initSession(Long userId, InitSessionCommand command) {
        if (command == null) {
            throw new BizException("初始化参数不能为空");
        }
        requireScene(command.scene());
        if (command.fileSize() <= 0) {
            throw new BizException("文件大小不合法");
        }
        int partSize = command.partSize() == null ? DEFAULT_PART_SIZE : command.partSize();
        if (partSize < MIN_PART_SIZE || partSize > MAX_PART_SIZE) {
            throw new BizException("分片大小需在 1MB–100MB 之间");
        }
        int partCount = (int) ((command.fileSize() + partSize - 1) / partSize);
        if (partCount > MAX_PART_COUNT) {
            throw new BizException("分片数量超限，请增大分片大小");
        }
        String filename = safeFilename(command.filename());
        String ext = extensionOf(filename);
        // 分片前缀写在 object_key：完成前它是临时前缀，完成后替换为正式对象键
        String prefix = "tmp/" + command.scene() + "/" + userId + "/" + UUID.randomUUID();

        FileUploadSession session = new FileUploadSession();
        session.setUserId(userId);
        session.setOriginalFilename(filename);
        session.setFileSize(command.fileSize());
        session.setContentType(command.contentType() == null
                ? "application/octet-stream" : command.contentType());
        session.setFileHash(command.fileHash());
        session.setPartSize(partSize);
        session.setPartCount(partCount);
        session.setUploadedCount(0);
        session.setBucketName(bucketName(prefix));
        session.setObjectKey(prefix);
        session.setStatus(FileUploadSession.STATUS_INIT);
        session.setExpiresAt(LocalDateTime.now().plusHours(24));
        EntityTimestamps.fillOnInsert(session);
        sessionMapper.insert(session);
        log.info("初始化上传会话 sessionId={} fileSize={} partCount={}", session.getId(),
                command.fileSize(), partCount);
        return session;
    }

    public FileUploadSession getSession(Long userId, Long sessionId) {
        return requireSession(userId, sessionId);
    }

    public FileUploadSession uploadPart(Long userId, Long sessionId, Integer partNumber,
                                        MultipartFile part, String partHash) {
        FileUploadSession session = requireSession(userId, sessionId);
        if (!FileUploadSession.STATUS_INIT.equals(session.getStatus())
                && !FileUploadSession.STATUS_UPLOADING.equals(session.getStatus())) {
            throw new BizException(409, "当前会话状态不允许上传分片：" + session.getStatus());
        }
        if (partNumber == null || partNumber < 1 || partNumber > session.getPartCount()) {
            throw new BizException(400, "分片序号越界");
        }
        if (part == null || part.isEmpty()) {
            throw new BizException("分片内容为空");
        }
        long expected = expectedPartSize(session, partNumber);
        if (part.getSize() != expected) {
            throw new BizException(400, "分片大小不符，期望 " + expected + " 字节");
        }

        String objectKey = session.getObjectKey() + "/p" + partNumber;
        try (InputStream in = part.getInputStream()) {
            storageService.uploadPart(objectKey, in, part.getSize(),
                    part.getContentType() == null ? "application/octet-stream" : part.getContentType());
        } catch (IOException e) {
            throw new BizException(500, "读取分片失败：" + e.getMessage());
        }

        FileUploadPart record = new FileUploadPart();
        record.setSessionId(sessionId);
        record.setPartNumber(partNumber);
        record.setPartSize((int) part.getSize());
        record.setPartHash(partHash);
        record.setBucketName(session.getBucketName());
        record.setObjectKey(objectKey);
        record.setStatus(FileUploadPart.STATUS_UPLOADED);
        EntityTimestamps.fillOnInsert(record);
        try {
            partMapper.upsertPart(record);
        } catch (RuntimeException e) {
            // 写库失败时删掉刚上传的分片，允许前端重传
            storageService.delete(objectKey);
            throw new BizException(500, "分片记录写入失败：" + e.getMessage());
        }

        int uploaded = partMapper.countUploaded(sessionId);
        sessionMapper.updateProgress(sessionId, uploaded, FileUploadSession.STATUS_UPLOADING);
        session.setUploadedCount(uploaded);
        session.setStatus(FileUploadSession.STATUS_UPLOADING);
        return session;
    }

    public FileObject complete(Long userId, Long sessionId, String scene) {
        FileUploadSession session = requireSession(userId, sessionId);
        requireScene(scene);
        if (FileUploadSession.STATUS_COMPLETED.equals(session.getStatus())
                && session.getObjectKey() != null && !session.getObjectKey().startsWith("tmp/")) {
            // 重复 complete：按已完成的对象键找回记录，不重复合并
            FileObject done = fileObjectMapper.selectByOwnerAndKey(userId, session.getObjectKey());
            if (done != null) {
                return done;
            }
        }
        int uploaded = partMapper.countUploaded(sessionId);
        if (uploaded != session.getPartCount()) {
            throw new BizException(409, "分片未上传完整：" + uploaded + "/" + session.getPartCount());
        }
        // 只有一个请求能把会话推进到 MERGING，避免并发下重复合并
        if (sessionMapper.tryStartMerging(sessionId) == 0) {
            throw new BizException(409, "会话正在合并或已完成，请勿重复提交");
        }

        String ext = extensionOf(session.getOriginalFilename());
        String finalKey = buildObjectKey(scene, userId, ext);
        List<String> partKeys = new ArrayList<>();
        for (FileUploadPart part : partMapper.selectBySession(sessionId)) {
            if (FileUploadPart.STATUS_UPLOADED.equals(part.getStatus())) {
                partKeys.add(part.getObjectKey());
            }
        }
        try {
            storageService.compose(finalKey, partKeys, session.getContentType());
        } catch (RuntimeException e) {
            sessionMapper.updateStatus(sessionId, FileUploadSession.STATUS_FAILED);
            // 分片保留，前端可重试合并
            throw new BizException(500, "分片合并失败：" + e.getMessage());
        }

        FileObject file = new FileObject();
        file.setOwnerUserId(userId);
        file.setScene(scene);
        file.setBucketName(bucketName(finalKey));
        file.setObjectKey(finalKey);
        file.setOriginalFilename(session.getOriginalFilename());
        file.setFileExt(ext);
        file.setContentType(session.getContentType());
        file.setFileSize(session.getFileSize());
        file.setSha256(session.getFileHash());
        file.setStatus("READY");
        file.setDeleted(0);
        EntityTimestamps.fillOnInsert(file);
        try {
            fileObjectMapper.insert(file);
            sessionMapper.markCompleted(sessionId, finalKey);
        } catch (RuntimeException e) {
            storageService.delete(finalKey);
            sessionMapper.updateStatus(sessionId, FileUploadSession.STATUS_FAILED);
            throw new BizException(500, "文件记录写入失败：" + e.getMessage());
        }
        log.info("分片合并完成 sessionId={} fileId={} size={}", sessionId, file.getId(), file.getFileSize());
        return file;
    }

    public void cancelSession(Long userId, Long sessionId) {
        FileUploadSession session = requireSession(userId, sessionId);
        if (sessionMapper.cancel(sessionId) == 0) {
            throw new BizException(409, "当前会话状态不允许取消：" + session.getStatus());
        }
        partMapper.deleteBySession(sessionId);
    }

    // ============ 查询 / 下载 / 删除 ============

    public R.PageData<FileObject> list(Long userId, String scene, long pageNum, long pageSize) {
        PageQuery page = PageQuery.of(pageNum, pageSize);
        long total = fileObjectMapper.countPage(userId, scene);
        List<FileObject> records = fileObjectMapper.selectPage(userId, scene, page.getOffset(), page.getLimit());
        return R.PageData.of(total, records, pageNum, pageSize);
    }

    public List<FileObject> batch(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> safe = ids.size() > 100 ? ids.subList(0, 100) : ids;
        return fileObjectMapper.selectByIds(userId, safe);
    }

    public String downloadUrl(Long userId, Long fileId) {
        FileObject file = requireReadyFile(userId, fileId);
        return storageService.presignedGet(file.getObjectKey(), DOWNLOAD_EXPIRE_MINUTES);
    }

    public void delete(Long userId, Long fileId) {
        FileObject file = requireReadyFile(userId, fileId);
        if (fileObjectMapper.logicDelete(fileId, userId) == 0) {
            throw new BizException(404, "文件不存在");
        }
        storageService.delete(file.getObjectKey());
    }

    public FileObject getOwned(Long userId, Long fileId) {
        FileObject file = fileObjectMapper.selectByIdForOwner(fileId, userId);
        if (file == null) {
            throw new BizException(404, "文件不存在");
        }
        return file;
    }

    /**
     * 清理过期上传会话：取消会话并删除分片对象。
     * 按 id 逐条处理，重复执行无副作用（已取消/已完成的不再被选中）。
     */
    public int cleanupExpiredSessions(int limit) {
        List<FileUploadSession> expired = sessionMapper.selectExpired(limit);
        int handled = 0;
        for (FileUploadSession session : expired) {
            try {
                for (FileUploadPart part : partMapper.selectBySession(session.getId())) {
                    storageService.delete(part.getObjectKey());
                }
                partMapper.deleteBySession(session.getId());
                if (sessionMapper.cancel(session.getId()) > 0) {
                    handled++;
                }
            } catch (Exception e) {
                // 单条失败不影响其他会话，下一轮继续
                log.warn("清理上传会话 {} 失败：{}", session.getId(), e.getMessage());
            }
        }
        return handled;
    }

    private FileObject requireReadyFile(Long userId, Long fileId) {
        FileObject file = getOwned(userId, fileId);
        if (!"READY".equals(file.getStatus())) {
            throw new BizException(409, "文件不可用：" + file.getStatus());
        }
        return file;
    }

    private FileUploadSession requireSession(Long userId, Long sessionId) {
        FileUploadSession session = sessionMapper.selectByIdForUser(sessionId, userId);
        if (session == null) {
            throw new BizException(404, "上传会话不存在");
        }
        return session;
    }

    // ============ 内部工具 ============

    private void requireScene(String scene) {
        if (scene == null || !SCENES.contains(scene)) {
            throw new BizException(400, "文件场景非法：" + scene);
        }
    }

    private long expectedPartSize(FileUploadSession session, int partNumber) {
        if (partNumber < session.getPartCount()) {
            return session.getPartSize();
        }
        return session.getFileSize() - (long) session.getPartSize() * (session.getPartCount() - 1);
    }

    private String buildObjectKey(String scene, Long userId, String ext) {
        LocalDate today = LocalDate.now();
        String datePath = today.format(DateTimeFormatter.ofPattern("yyyy/MM"));
        return scene + "/" + userId + "/" + datePath + "/" + UUID.randomUUID()
                + (ext == null ? "" : "." + ext);
    }

    private String bucketName(String objectKey) {
        return storageService.bucketName();
    }

    private static String safeFilename(String filename) {
        String name = filename == null || filename.isBlank() ? "unnamed" : filename.trim();
        name = name.replace('\\', '/');
        if (name.contains("/")) {
            name = name.substring(name.lastIndexOf('/') + 1);
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    private static String extensionOf(String filename) {
        String ext = StringUtils.getFilenameExtension(filename);
        if (ext == null || ext.isBlank()) {
            return null;
        }
        ext = ext.toLowerCase();
        return ext.length() > 16 ? ext.substring(0, 16) : ext;
    }

    private static String sha256Of(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream dis = new DigestInputStream(in, digest)) {
                byte[] buffer = new byte[8192];
                while (dis.read(buffer) != -1) {
                    // 读完即可，摘要由 DigestInputStream 累积
                }
            }
            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            log.warn("计算文件摘要失败：{}", e.getMessage());
            return null;
        }
    }
}
