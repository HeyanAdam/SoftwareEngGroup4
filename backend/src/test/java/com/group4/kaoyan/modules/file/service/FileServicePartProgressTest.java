package com.group4.kaoyan.modules.file.service;

import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.modules.file.entity.FileUploadSession;
import com.group4.kaoyan.modules.file.mapper.FileObjectMapper;
import com.group4.kaoyan.modules.file.mapper.UploadPartMapper;
import com.group4.kaoyan.modules.file.mapper.UploadSessionMapper;
import com.group4.kaoyan.platform.storage.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("分片上传进度与合并前查漏")
class FileServicePartProgressTest {

    private final FileObjectMapper fileObjectMapper = mock(FileObjectMapper.class);
    private final UploadSessionMapper sessionMapper = mock(UploadSessionMapper.class);
    private final UploadPartMapper partMapper = mock(UploadPartMapper.class);
    private final StorageService storageService = mock(StorageService.class);
    private final FileService service = new FileService(
            fileObjectMapper, sessionMapper, partMapper, storageService);

    @Test
    @DisplayName("查询会话返回已传序号和缺失序号")
    void getSessionReturnsExactPartIndexes() {
        FileUploadSession session = session(72);
        List<Integer> uploaded = IntStream.rangeClosed(1, 71).boxed().toList();
        when(sessionMapper.selectByIdForUser(7L, 9L)).thenReturn(session);
        when(partMapper.selectUploadedNumbers(7L)).thenReturn(uploaded);

        FileUploadSession result = service.getSession(9L, 7L);

        assertEquals(uploaded, result.getUploadedParts());
        assertEquals(List.of(72), result.getMissingParts());
        assertEquals(71, result.getUploadedCount());
    }

    @Test
    @DisplayName("71/72 时拒绝合并并明确指出缺少第 72 片")
    void completeRejectsMissingPartWithExactIndex() {
        FileUploadSession session = session(72);
        when(sessionMapper.selectByIdForUser(7L, 9L)).thenReturn(session);
        when(partMapper.selectUploadedNumbers(7L))
                .thenReturn(IntStream.rangeClosed(1, 71).boxed().toList());

        BizException error = assertThrows(BizException.class,
                () -> service.complete(9L, 7L, "STUDY_MATERIAL"));

        assertTrue(error.getMessage().contains("缺少分片：72"));
        verifyNoInteractions(storageService);
    }

    private FileUploadSession session(int partCount) {
        FileUploadSession session = new FileUploadSession();
        session.setId(7L);
        session.setUserId(9L);
        session.setOriginalFilename("large.pdf");
        session.setFileSize(357L * 1024 * 1024);
        session.setContentType("application/pdf");
        session.setPartSize(5 * 1024 * 1024);
        session.setPartCount(partCount);
        session.setUploadedCount(0);
        session.setBucketName("kaoyan-files");
        session.setObjectKey("tmp/STUDY_MATERIAL/9/test");
        session.setStatus(FileUploadSession.STATUS_UPLOADING);
        return session;
    }
}
